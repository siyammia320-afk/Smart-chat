package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model. ADMIN_EMAIL
import com.example.data.model.ADMIN_USER_ID
import com.example.data.model.AccountSession
import com.example.data.model.AccountSessionManager
import com.example.data.model.ChatMessage
import com.example.data.model.GROUP_ID
import com.example.data.model.LIVE_EMAIL_GROUP_ID
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCallSession
import com.example.data.model.emailToUserId
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SmartChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val activeGroupId: String = GROUP_ID
) {
    // Convenience constructor using custom database ID from firebase_applet_config.xml
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
                .takeIf { it.isNotBlank() && !it.startsWith("remixed") }
                ?: "ai-studio-android-840e1286-d8b0-4357-8b06-707f1248dfd6"
        ),
        auth = Firebase.auth,
        activeGroupId = LIVE_EMAIL_GROUP_ID
    )

    private fun currentUidOrNull(): String? {
        return AccountSessionManager.currentSession?.userId ?: auth.currentUser?.uid
    }

    private fun requireUserId(): String {
        return currentUidOrNull()
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    fun isCurrentUserAdmin(): Boolean {
        val email = (AccountSessionManager.currentSession?.email ?: auth.currentUser?.email)
            ?.lowercase()?.trim()
        val uid = currentUidOrNull()
        return email == ADMIN_EMAIL || uid == ADMIN_USER_ID
    }

    suspend fun registerEmailAccount(
        fullName: String,
        usernameHandle: String,
        email: String,
        passwordHash: String,
        avatarData: String,
        autoDeleteHours: Long = 24L
    ): Result<AccountSession> {
        val normalizedEmail = email.trim().lowercase()
        val uid = emailToUserId(normalizedEmail)
        val cleanHandle = usernameHandle.trim().removePrefix("@").take(24)
        val cleanName = fullName.trim().take(40).ifBlank { cleanHandle }
        val isAdmin = normalizedEmail == ADMIN_EMAIL || uid == ADMIN_USER_ID

        val accountRef = db.collection("accounts").document(uid)
        val userRef = db.collection("users").document(uid)

        return try {
            val existingAcc = accountRef.get().await()
            if (existingAcc.exists()) {
                val existingHash = existingAcc.getString("passwordHash") ?: ""
                if (existingHash.isNotBlank() && existingHash != passwordHash) {
                    return Result.failure(
                        IllegalArgumentException("An account with this email already exists. Please login instead.")
                    )
                }
            } else {
                val accPayload = mapOf(
                    "userId" to uid,
                    "fullName" to cleanName,
                    "username" to cleanHandle,
                    "passwordHash" to passwordHash,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                accountRef.set(accPayload).await()
            }

            val finalAvatar = avatarData.take(25000).ifBlank { "avatar_indigo" }
            val finalTtl = if (autoDeleteHours == 48L) 48L else 24L
            val existingUser = userRef.get().await()
            if (existingUser.exists()) {
                userRef.update(
                    mapOf(
                        "username" to cleanName,
                        "avatarData" to finalAvatar,
                        "statusText" to "@$cleanHandle",
                        "isOnline" to true,
                        "isAdminBadge" to isAdmin,
                        "autoDeleteHours" to finalTtl,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val newProfile = UserProfile(
                    id = uid,
                    userId = uid,
                    groupId = activeGroupId,
                    username = cleanName,
                    avatarData = finalAvatar,
                    statusText = "@$cleanHandle",
                    isBlocked = false,
                    isOnline = true,
                    isAdminBadge = isAdmin,
                    autoDeleteHours = finalTtl
                )
                userRef.set(newProfile.toCreateMap()).await()
            }

            Result.success(
                AccountSession(
                    userId = uid,
                    email = normalizedEmail,
                    fullName = cleanName,
                    username = cleanHandle,
                    avatarData = finalAvatar,
                    autoDeleteHours = finalTtl
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, accountRef.path)
            Result.failure(e)
        }
    }

    suspend fun loginEmailAccount(
        email: String,
        passwordHash: String
    ): Result<AccountSession> {
        val normalizedEmail = email.trim().lowercase()
        val uid = emailToUserId(normalizedEmail)
        val accountRef = db.collection("accounts").document(uid)
        val userRef = db.collection("users").document(uid)

        return try {
            val accSnap = accountRef.get().await()
            if (!accSnap.exists()) {
                return Result.failure(
                    IllegalArgumentException("No account found with this email. Please Sign Up first.")
                )
            }
            val storedHash = accSnap.getString("passwordHash") ?: ""
            if (storedHash != passwordHash) {
                return Result.failure(
                    IllegalArgumentException("Incorrect password. Please try again.")
                )
            }

            val fullName = accSnap.getString("fullName") ?: normalizedEmail.substringBefore("@")
            val handle = accSnap.getString("username") ?: normalizedEmail.substringBefore("@")

            val userSnap = userRef.get().await()
            val avatarData = if (userSnap.exists()) {
                userSnap.getString("avatarData") ?: "avatar_indigo"
            } else {
                "avatar_indigo"
            }
            val autoDeleteHours = if (userSnap.exists()) {
                userSnap.getLong("autoDeleteHours") ?: 24L
            } else {
                24L
            }

            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "isOnline" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val isAdmin = normalizedEmail == ADMIN_EMAIL || uid == ADMIN_USER_ID
                val profile = UserProfile(
                    id = uid,
                    userId = uid,
                    groupId = activeGroupId,
                    username = fullName.take(40),
                    avatarData = avatarData,
                    statusText = "@$handle",
                    isBlocked = false,
                    isOnline = true,
                    isAdminBadge = isAdmin,
                    autoDeleteHours = if (autoDeleteHours == 48L) 48L else 24L
                )
                userRef.set(profile.toCreateMap()).await()
            }

            Result.success(
                AccountSession(
                    userId = uid,
                    email = normalizedEmail,
                    fullName = fullName,
                    username = handle,
                    avatarData = avatarData,
                    autoDeleteHours = autoDeleteHours
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, accountRef.path)
            Result.failure(e)
        }
    }

    suspend fun ensureUserProfile(
        customUsername: String? = null,
        customAvatar: String? = null,
        customStatus: String? = null,
        autoDeleteHours: Long = 24L
    ): Result<UserProfile> {
        val uid = requireUserId()
        val user = auth.currentUser
        val session = AccountSessionManager.currentSession
        val isAdmin = isCurrentUserAdmin()
        val docRef = db.collection("users").document(uid)

        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = uid, userId = uid, groupId = activeGroupId)

                val updatedUsername = customUsername?.takeIf { it.isNotBlank() } ?: existing.username
                val updatedAvatar = customAvatar?.takeIf { it.isNotBlank() } ?: existing.avatarData
                val updatedStatus = customStatus?.takeIf { it.isNotBlank() } ?: existing.statusText
                val updatedBadge = if (isAdmin) true else existing.isAdminBadge

                val updatePayload = mapOf(
                    "username" to updatedUsername.take(40).ifBlank { "Member" },
                    "avatarData" to updatedAvatar.take(25000),
                    "statusText" to updatedStatus.take(120),
                    "isOnline" to true,
                    "isAdminBadge" to updatedBadge,
                    "autoDeleteHours" to if (autoDeleteHours == 48L) 48L else existing.autoDeleteHours,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
                Result.success(
                    existing.copy(
                        username = updatedUsername,
                        avatarData = updatedAvatar,
                        statusText = updatedStatus,
                        isOnline = true,
                        isAdminBadge = updatedBadge
                    )
                )
            } else {
                val fallbackName = customUsername?.takeIf { it.isNotBlank() }
                    ?: session?.fullName?.takeIf { it.isNotBlank() }
                    ?: user?.displayName?.takeIf { it.isNotBlank() }
                    ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Member_${uid.take(5)}"

                val newProfile = UserProfile(
                    id = uid,
                    userId = uid,
                    groupId = activeGroupId,
                    username = fallbackName.take(40),
                    avatarData = (customAvatar ?: session?.avatarData ?: "avatar_emerald").take(25000),
                    statusText = (customStatus ?: if (isAdmin) "SMART CHAT অ্যাডমিন" else "SMART CHAT মেম্বার").take(120),
                    isBlocked = false,
                    isOnline = true,
                    isAdminBadge = isAdmin,
                    autoDeleteHours = if (autoDeleteHours == 48L) 48L else 24L
                )
                docRef.set(newProfile.toCreateMap()).await()
                Result.success(newProfile)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        username: String,
        avatarData: String,
        statusText: String,
        autoDeleteHours: Long,
        isOnline: Boolean = true
    ): Result<Unit> {
        val uid = requireUserId()
        val isAdmin = isCurrentUserAdmin()
        val docRef = db.collection("users").document(uid)
        return try {
            val payload = mapOf(
                "username" to username.trim().take(40).ifBlank { "Member" },
                "avatarData" to avatarData.take(25000),
                "statusText" to statusText.trim().take(120),
                "isOnline" to isOnline,
                "isAdminBadge" to isAdmin,
                "autoDeleteHours" to if (autoDeleteHours == 48L) 48L else 24L,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProfileById(targetUserId: String): Result<UserProfile?> {
        val docRef = db.collection("users").document(targetUserId)
        return try {
            val snap = docRef.get().await()
            val profile = snap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(uid)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    fun observeGroupMembers(): Flow<List<UserProfile>> {
        val path = "users"
        return db.collection(path)
            .whereEqualTo("groupId", activeGroupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<UserProfile> { it.isAdminBadge }
                        .thenByDescending { it.isOnline }
                        .thenBy { it.username.lowercase() }
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun setUserBlockStatus(targetUserId: String, blocked: Boolean): Result<Unit> {
        requireUserId()
        val docRef = db.collection("users").document(targetUserId)
        return try {
            val payload = mapOf(
                "isBlocked" to blocked,
                "statusText" to if (blocked) "অ্যাডমিন কর্তৃক ব্লকড (Blocked)" else "SMART CHAT মেম্বার",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeGroupMessages(): Flow<List<ChatMessage>> {
        val path = "messages"
        return db.collection(path)
            .whereEqualTo("groupId", activeGroupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { (it.createdAt ?: Timestamp(0, 0)).seconds }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun sendTextMessage(
        senderProfile: UserProfile,
        text: String,
        replyTo: ChatMessage? = null,
        isNotice: Boolean = false
    ): Result<String> {
        val uid = requireUserId()
        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("messages").document(messageId)

        val message = ChatMessage(
            id = messageId,
            groupId = activeGroupId,
            senderId = uid,
            senderName = senderProfile.username.ifBlank { "Member" },
            senderAvatar = senderProfile.avatarData,
            senderIsAdmin = isCurrentUserAdmin() && senderProfile.isAdminBadge,
            type = if (isNotice && isCurrentUserAdmin()) "notice" else "text",
            text = text.trim().take(2000),
            audioBase64 = "",
            audioDurationSec = 0L,
            replyToId = replyTo?.id ?: "",
            replyToSender = replyTo?.senderName ?: "",
            replyToPreview = replyTo?.text?.take(120) ?: "",
            ttlHours = if (senderProfile.autoDeleteHours == 48L) 48L else 24L
        )

        return try {
            docRef.set(message.toCreateMap()).await()
            Result.success(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendVoiceMessage(
        senderProfile: UserProfile,
        audioBase64: String,
        durationSec: Int,
        caption: String = "ভয়েস মেসেজ (Voice Note)"
    ): Result<String> {
        val uid = requireUserId()
        val messageId = "vmsg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("messages").document(messageId)

        val message = ChatMessage(
            id = messageId,
            groupId = activeGroupId,
            senderId = uid,
            senderName = senderProfile.username.ifBlank { "Member" },
            senderAvatar = senderProfile.avatarData,
            senderIsAdmin = isCurrentUserAdmin() && senderProfile.isAdminBadge,
            type = "voice",
            text = caption.take(200),
            audioBase64 = audioBase64.take(300000),
            audioDurationSec = durationSec.toLong().coerceIn(1L, 120L),
            ttlHours = if (senderProfile.autoDeleteHours == 48L) 48L else 24L
        )

        return try {
            docRef.set(message.toCreateMap()).await()
            Result.success(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("messages").document(messageId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun cleanupExpiredMessages(
        messages: List<ChatMessage>,
        forceDeleteAllForAdmin: Boolean = false
    ): Int {
        val uid = currentUidOrNull() ?: return 0
        val isAdmin = isCurrentUserAdmin()
        val nowSeconds = Timestamp.now().seconds
        var deletedCount = 0

        for (msg in messages) {
            if (msg.id.isBlank()) continue
            val createdSec = msg.createdAt?.seconds ?: continue
            val ttlSeconds = (if (msg.ttlHours == 48L) 48L else 24L) * 3600L
            val isExpired = (nowSeconds - createdSec) >= ttlSeconds
            val canDelete = (forceDeleteAllForAdmin && isAdmin) ||
                (isExpired && (msg.senderId == uid || isAdmin || (nowSeconds - createdSec) > 24L * 3600L))

            if (canDelete) {
                try {
                    db.collection("messages").document(msg.id).delete().await()
                    deletedCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    handleFirestoreError(e, OperationType.DELETE, "messages/${msg.id}")
                }
            }
        }
        return deletedCount
    }

    fun observeActiveCalls(): Flow<List<VoiceCallSession>> {
        val path = "calls"
        return db.collection(path)
            .whereEqualTo("groupId", activeGroupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VoiceCallSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.status != "ended" }
                    .sortedByDescending { (it.createdAt ?: Timestamp(0, 0)).seconds }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun startVoiceCall(
        callerProfile: UserProfile,
        targetUserId: String = "all",
        targetUserName: String = "গ্রুপ ভয়েস কল"
    ): Result<String> {
        val uid = requireUserId()
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val docRef = db.collection("calls").document(callId)
        val session = VoiceCallSession(
            id = callId,
            groupId = activeGroupId,
            callerId = uid,
            callerName = callerProfile.username.ifBlank { "Member" },
            targetUserId = targetUserId.ifBlank { "all" },
            targetUserName = targetUserName.ifBlank { "গ্রুপ ভয়েস কল" },
            status = "ringing",
            sdpOffer = "SMARTCHAT_AUDIO_SESSION_V1",
            sdpAnswer = "",
            latestAudioChunk = "",
            latestAudioSpeakerId = uid,
            participantCount = 1L
        )
        return try {
            docRef.set(session.toCreateMap()).await()
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinVoiceCall(callId: String, currentParticipants: Long): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("calls").document(callId)
        return try {
            val payload = mapOf(
                "status" to "connected",
                "sdpAnswer" to "JOINED_BY_$uid",
                "participantCount" to (currentParticipants + 1L).coerceAtMost(50L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun transmitAudioChunk(callId: String, audioBase64: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("calls").document(callId)
        return try {
            val payload = mapOf(
                "status" to "connected",
                "latestAudioChunk" to audioBase64.take(60000),
                "latestAudioSpeakerId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun endVoiceCall(callId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        return try {
            val payload = mapOf(
                "status" to "ended",
                "latestAudioChunk" to "",
                "participantCount" to 0L,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
