package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCallSession
import com.example.data.repository.SmartChatRepository
import com.example.ui.auth.OnboardingPreferences
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class SmartChatViewModel(
    private val repository: SmartChatRepository,
    val currentUserId: String
) : ViewModel() {

    private var hasRunInitialCleanup = false

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    val isSuperAdmin: Boolean
        get() = repository.isCurrentUserAdmin()

    val currentUserProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { e ->
            Log.w(TAG, "Error observing current user profile", e)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val membersState: StateFlow<UiState<List<UserProfile>>> = repository.observeGroupMembers()
        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing group members", error)
            emit(UiState.Error(error.message ?: "মেম্বার লিস্ট লোড করা যায়নি"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<ChatMessage>>> = repository.observeGroupMessages()
        .onEach { list ->
            if (!hasRunInitialCleanup && list.isNotEmpty()) {
                hasRunInitialCleanup = true
                viewModelScope.launch {
                    val deleted = repository.cleanupExpiredMessages(list, forceDeleteAllForAdmin = false)
                    if (deleted > 0) {
                        _statusBanner.value = "ফ্রি স্টোরেজ সেভার: $deleted টি পুরনো মেসেজ অটো ডিলিট হয়েছে।"
                    }
                }
            }
        }
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing group messages", error)
            emit(UiState.Error(error.message ?: "চ্যাট মেসেজ লোড করা যায়নি"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activeCallsState: StateFlow<UiState<List<VoiceCallSession>>> = repository.observeActiveCalls()
        .map<List<VoiceCallSession>, UiState<List<VoiceCallSession>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing active calls", error)
            emit(UiState.Error(error.message ?: "কল স্ট্যাটাস লোড করা যায়নি"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun initializeProfile(prefs: OnboardingPreferences?) {
        viewModelScope.launch {
            val result = repository.ensureUserProfile(
                customUsername = prefs?.username?.takeIf { it.isNotBlank() },
                customAvatar = prefs?.avatarData?.takeIf { it.isNotBlank() },
                customStatus = prefs?.statusText?.takeIf { it.isNotBlank() },
                autoDeleteHours = prefs?.autoDeleteHours ?: 24L
            )
            result.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "প্রোফাইল সিঙ্ক করতে সমস্যা হয়েছে"
            }
        }
    }

    fun updateMyProfile(
        username: String,
        avatarData: String,
        statusText: String,
        autoDeleteHours: Long
    ) {
        viewModelScope.launch {
            val res = repository.updateProfile(
                username = username,
                avatarData = avatarData,
                statusText = statusText,
                autoDeleteHours = autoDeleteHours,
                isOnline = true
            )
            if (res.isSuccess) {
                _statusBanner.value = "প্রোফাইল সফলভাবে আপডেট হয়েছে!"
            } else {
                _statusBanner.value = "প্রোফাইল আপডেট ব্যর্থ হয়েছে।"
            }
        }
    }

    fun setReplyTarget(message: ChatMessage?) {
        _replyingTo.value = message
    }

    fun setPlayingMessageId(messageId: String?) {
        _playingMessageId.value = messageId
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun sendTextMessage(text: String, isAdminNotice: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val profile = currentUserProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "Member",
            isAdminBadge = isSuperAdmin
        )
        if (profile.isBlocked) {
            _statusBanner.value = "অ্যাডমিন আপনাকে ব্লক করেছেন। আপনি মেসেজ পাঠাতে পারবেন না।"
            return
        }
        val replyTarget = _replyingTo.value
        _replyingTo.value = null

        viewModelScope.launch {
            val result = repository.sendTextMessage(
                senderProfile = profile,
                text = trimmed,
                replyTo = replyTarget,
                isNotice = isAdminNotice
            )
            result.onFailure {
                _statusBanner.value = "মেসেজ পাঠানো যায়নি (আপনি ব্লকড হতে পারেন বা নেটওয়ার্ক সমস্যা)।"
            }
        }
    }

    fun sendVoiceMessage(audioBase64: String, durationSec: Int) {
        val profile = currentUserProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "Member",
            isAdminBadge = isSuperAdmin
        )
        if (profile.isBlocked) {
            _statusBanner.value = "অ্যাডমিন আপনাকে ব্লক করেছেন। আপনি ভয়েস মেসেজ পাঠাতে পারবেন না।"
            return
        }
        viewModelScope.launch {
            val result = repository.sendVoiceMessage(
                senderProfile = profile,
                audioBase64 = audioBase64,
                durationSec = durationSec
            )
            result.onSuccess {
                _statusBanner.value = "ভয়েস মেসেজ পাঠানো হয়েছে ($durationSec সেকেন্ড)"
            }.onFailure {
                _statusBanner.value = "ভয়েস মেসেজ পাঠানো যায়নি।"
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            val res = repository.deleteMessage(messageId)
            if (res.isSuccess) {
                _statusBanner.value = "মেসেজ ডিলিট করা হয়েছে।"
            } else {
                _statusBanner.value = "মেসেজ ডিলিট করার অনুমতি নেই।"
            }
        }
    }

    fun toggleBlockUser(targetUser: UserProfile) {
        if (!isSuperAdmin) {
            _statusBanner.value = "শুধুমাত্র অ্যাডমিন ব্লক বা আনব্লক করতে পারবেন!"
            return
        }
        val newBlockedState = !targetUser.isBlocked
        viewModelScope.launch {
            val res = repository.setUserBlockStatus(targetUser.userId, newBlockedState)
            if (res.isSuccess) {
                val actionLabel = if (newBlockedState) "ব্লক (Blocked)" else "আনব্লক (Unblocked)"
                _statusBanner.value = "${targetUser.username}-কে $actionLabel করা হয়েছে।"
            } else {
                _statusBanner.value = "ব্লক স্ট্যাটাস পরিবর্তন করা যায়নি।"
            }
        }
    }

    fun runManualStorageCleanup(deleteAllAsAdmin: Boolean = false) {
        val currentList = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            val count = repository.cleanupExpiredMessages(
                messages = currentList,
                forceDeleteAllForAdmin = deleteAllAsAdmin
            )
            _statusBanner.value = if (deleteAllAsAdmin) {
                "অ্যাডমিন ক্লিনআপ: মোট $count টি মেসেজ মুছে ফ্রি ডাটাবেস খালি করা হয়েছে!"
            } else {
                "অটো-ক্লিনআপ সম্পন্ন: $count টি পুরনো মেসেজ ডিলিট করা হয়েছে।"
            }
        }
    }

    fun startVoiceCall(targetUserId: String = "all", targetUserName: String = "SMART CHAT গ্রুপ কল") {
        val profile = currentUserProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "Member"
        )
        if (profile.isBlocked) {
            _statusBanner.value = "অ্যাডমিন আপনাকে ব্লক করেছেন। আপনি ভয়েস কল করতে পারবেন না।"
            return
        }
        viewModelScope.launch {
            val res = repository.startVoiceCall(profile, targetUserId, targetUserName)
            if (res.isSuccess) {
                _statusBanner.value = "$targetUserName শুরু হয়েছে..."
            } else {
                _statusBanner.value = "ভয়েস কল শুরু করা যায়নি।"
            }
        }
    }

    fun joinVoiceCall(call: VoiceCallSession) {
        viewModelScope.launch {
            repository.joinVoiceCall(call.id, call.participantCount)
        }
    }

    fun transmitLiveVoiceChunk(
        callId: String,
        audioBase64: String,
        isCaller: Boolean = false,
        showBanner: Boolean = false
    ) {
        viewModelScope.launch {
            repository.transmitAudioChunk(callId, audioBase64, isCaller)
                .onSuccess {
                    if (showBanner) {
                        _statusBanner.value = "লাইভ ভয়েস অডিও কলের সবাইকে পাঠানো হয়েছে!"
                    }
                }
        }
    }

    fun endVoiceCall(callId: String) {
        viewModelScope.launch {
            repository.endVoiceCall(callId)
            _statusBanner.value = "ভয়েস কল সমাপ্ত হয়েছে।"
        }
    }

    companion object {
        private const val TAG = "SmartChatVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun factory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                SmartChatViewModel(SmartChatRepository(app), currentUserId)
            }
        }
    }
}
