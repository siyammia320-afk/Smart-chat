package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.ChatMessage
import com.example.data.model.GroupSettings
import com.example.data.model.UserProfile
import com.example.data.repository.SmartChatRepository
import com.example.ui.auth.OnboardingPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var typingClearJob: Job? = null
    private var lastTypingSentMs: Long = 0L

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo: StateFlow<ChatMessage?> = _replyingTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    private val _activeDmPeer = MutableStateFlow<UserProfile?>(null)
    val activeDmPeer: StateFlow<UserProfile?> = _activeDmPeer.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    val isSuperAdmin: Boolean
        get() = repository.isCurrentUserAdmin()

    val groupSettings: StateFlow<GroupSettings> = repository.observeGroupSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = GroupSettings()
        )

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
            emit(UiState.Error(error.message ?: "Error"))
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
                    repository.cleanupExpiredMessages(list, forceDeleteAllForAdmin = false)
                }
            }
        }
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { error ->
            Log.w(TAG, "Error observing group messages", error)
            emit(UiState.Error(error.message ?: "Error"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun initializeProfile(prefs: OnboardingPreferences?) {
        viewModelScope.launch {
            repository.ensureUserProfile(
                customUsername = prefs?.username?.takeIf { it.isNotBlank() },
                customAvatar = prefs?.avatarData?.takeIf { it.isNotBlank() },
                customStatus = prefs?.statusText?.takeIf { it.isNotBlank() },
                autoDeleteHours = prefs?.autoDeleteHours ?: 24L
            )
        }
    }

    fun setOnlineState(isOnline: Boolean) {
        viewModelScope.launch {
            repository.setOnlinePresence(currentUserProfile.value, isOnline)
        }
    }

    fun notifyTyping(roomKey: String?) {
        val profile = currentUserProfile.value ?: return
        if (roomKey.isNullOrBlank()) {
            typingClearJob?.cancel()
            viewModelScope.launch {
                repository.setTypingStatus(profile, null)
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingSentMs >= 2500L) {
            lastTypingSentMs = now
            viewModelScope.launch {
                repository.setTypingStatus(profile, roomKey)
            }
        }
        typingClearJob?.cancel()
        typingClearJob = viewModelScope.launch {
            delay(4500L)
            repository.setTypingStatus(profile, null)
        }
    }

    fun openDirectMessage(peer: UserProfile?) {
        _replyingTo.value = null
        _editingMessage.value = null
        _activeDmPeer.value = peer
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
                _statusBanner.value = "প্রোফাইল সেভ হয়েছে"
            }
        }
    }

    fun updateAdminGroupSettings(groupName: String, isGroupOff: Boolean) {
        if (!isSuperAdmin) return
        viewModelScope.launch {
            repository.updateGroupSettings(groupName, isGroupOff)
        }
    }

    fun setReplyTarget(message: ChatMessage?) {
        _editingMessage.value = null
        _replyingTo.value = message
    }

    fun startEditingMessage(message: ChatMessage?) {
        _replyingTo.value = null
        _editingMessage.value = message
    }

    fun saveEditedMessage(newText: String) {
        val target = _editingMessage.value ?: return
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        _editingMessage.value = null
        viewModelScope.launch {
            repository.editMessage(target, trimmed)
        }
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            repository.toggleReaction(message, emoji)
        }
    }

    fun setPlayingMessageId(messageId: String?) {
        _playingMessageId.value = messageId
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun sendTextMessage(
        text: String,
        isAdminNotice: Boolean = false,
        dmTargetUserId: String? = null
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // If user is currently editing a message, save the edit instead
        if (_editingMessage.value != null) {
            saveEditedMessage(trimmed)
            return
        }

        val profile = currentUserProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "Member",
            isAdminBadge = isSuperAdmin
        )
        if (profile.isBlocked) {
            _statusBanner.value = "আপনি ব্লকড"
            return
        }
        if (dmTargetUserId.isNullOrBlank() && groupSettings.value.isGroupOff && !isSuperAdmin) {
            _statusBanner.value = "গ্রুপ চ্যাট অফ আছে"
            return
        }

        val replyTarget = _replyingTo.value
        _replyingTo.value = null
        typingClearJob?.cancel()

        viewModelScope.launch {
            repository.sendTextMessage(
                senderProfile = profile,
                text = trimmed,
                replyTo = replyTarget,
                isNotice = isAdminNotice,
                dmTargetUserId = dmTargetUserId
            )
            repository.setTypingStatus(profile, null)
        }
    }

    fun sendVoiceMessage(
        audioBase64: String,
        durationSec: Int,
        dmTargetUserId: String? = null
    ) {
        val profile = currentUserProfile.value ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            username = "Member",
            isAdminBadge = isSuperAdmin
        )
        if (profile.isBlocked) {
            _statusBanner.value = "আপনি ব্লকড"
            return
        }
        if (dmTargetUserId.isNullOrBlank() && groupSettings.value.isGroupOff && !isSuperAdmin) {
            _statusBanner.value = "গ্রুপ চ্যাট অফ আছে"
            return
        }
        viewModelScope.launch {
            repository.sendVoiceMessage(
                senderProfile = profile,
                audioBase64 = audioBase64,
                durationSec = durationSec,
                dmTargetUserId = dmTargetUserId
            )
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
        }
    }

    fun toggleBlockUser(targetUser: UserProfile) {
        if (!isSuperAdmin) return
        val newBlockedState = !targetUser.isBlocked
        viewModelScope.launch {
            repository.setUserBlockStatus(targetUser.userId, newBlockedState)
        }
    }

    fun runManualStorageCleanup(deleteAllAsAdmin: Boolean = false) {
        val currentList = (messagesState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            val count = repository.cleanupExpiredMessages(
                messages = currentList,
                forceDeleteAllForAdmin = deleteAllAsAdmin
            )
            _statusBanner.value = "$count টি মেসেজ ক্লিন হয়েছে"
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
