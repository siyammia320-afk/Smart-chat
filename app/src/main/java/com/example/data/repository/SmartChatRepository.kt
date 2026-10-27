package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.ChatMessage
import com.example.data.model.GROUP_ID
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCallSession
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
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Convenience constructor using custom database ID from firebase_applet_config.xml
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    fun isCurrentUserAdmin(): Boolean {
        val email = auth.currentUser?.email?.lowercase()?.trim()
        return email == ADMIN_EMAIL
    }

    suspend fun ensureUserProfile(
        customUsername: String? = null,
        customAvatar: String? = null,
        customStatus: String? = null,
        autoDeleteHours: Long = 24L
    ): Result<UserProfile> {
        val uid = requireUserId()
        val user = auth.currentUser
        val isAdmin = isCurrentUserAdmin()
        val docRef = db.collection("users").document(uid)

        return try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val existing = snapshot.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = uid, userId = uid)

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
                    ?: user?.displayName?.takeIf { it.isNotBlank() }
                    ?: user?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                    ?: "Member_${uid.take(5)}"

                val newProfile = UserProfile(
                    id = uid,
                    userId = uid,
                    groupId = GROUP_ID,
                    username = fallbackName.take(40),
                    avatarData = (customAvatar ?: "avatar_emerald").take(25000),
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
            .whereEqualTo("groupId", GROUP_ID)
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
            .whereEqualTo("groupId", GROUP_ID)
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
            groupId = GROUP_ID,
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
            groupId = GROUP_ID,
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

    /**
     * Automatically deletes messages older than their configured TTL (24h or 48h)
     * or deletes all messages when triggered by the Admin to keep Firebase Free Plan clean.
     */
    suspend fun cleanupExpiredMessages(
        messages: List<ChatMessage>,
        forceDeleteAllForAdmin: Boolean = false
    ): Int {
        val uid = auth.currentUser?.uid ?: return 0
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
            .whereEqualTo("groupId", GROUP_ID)
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
            groupId = GROUP_ID,
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
