package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

const val GROUP_ID = "smart_chat_main"
const val ADMIN_EMAIL = "siyammia320@gmail.com"

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val groupId: String = GROUP_ID,
    val username: String = "",
    val avatarData: String = "avatar_emerald",
    val statusText: String = "SMART CHAT মেম্বার",
    @get:PropertyName("isBlocked") @set:PropertyName("isBlocked")
    var isBlocked: Boolean = false,
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = true,
    @get:PropertyName("isAdminBadge") @set:PropertyName("isAdminBadge")
    var isAdminBadge: Boolean = false,
    val autoDeleteHours: Long = 24L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "groupId" to groupId,
        "username" to username.take(40).ifBlank { "Member" },
        "avatarData" to avatarData.take(25000),
        "statusText" to statusText.take(120),
        "isBlocked" to isBlocked,
        "isOnline" to isOnline,
        "isAdminBadge" to isAdminBadge,
        "autoDeleteHours" to if (autoDeleteHours == 48L) 48L else 24L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val groupId: String = GROUP_ID,
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatar: String = "avatar_emerald",
    val senderIsAdmin: Boolean = false,
    val type: String = "text", // "text", "voice", "notice"
    val text: String = "",
    val audioBase64: String = "",
    val audioDurationSec: Long = 0L,
    val replyToId: String = "",
    val replyToSender: String = "",
    val replyToPreview: String = "",
    val ttlHours: Long = 24L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "senderId" to senderId,
        "senderName" to senderName.take(40).ifBlank { "Member" },
        "senderAvatar" to senderAvatar.take(25000),
        "senderIsAdmin" to senderIsAdmin,
        "type" to type,
        "text" to text.take(2000),
        "audioBase64" to audioBase64.take(300000),
        "audioDurationSec" to audioDurationSec.coerceIn(0L, 120L),
        "replyToId" to replyToId.take(128),
        "replyToSender" to replyToSender.take(40),
        "replyToPreview" to replyToPreview.take(200),
        "ttlHours" to if (ttlHours == 48L) 48L else 24L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class VoiceCallSession(
    @DocumentId val id: String = "",
    val groupId: String = GROUP_ID,
    val callerId: String = "",
    val callerName: String = "",
    val targetUserId: String = "all",
    val targetUserName: String = "গ্রুপ ভয়েস কল",
    val status: String = "ringing", // "ringing", "connected", "ended"
    val sdpOffer: String = "",
    val sdpAnswer: String = "",
    val latestAudioChunk: String = "",
    val latestAudioSpeakerId: String = "",
    val participantCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "groupId" to groupId,
        "callerId" to callerId,
        "callerName" to callerName.take(40).ifBlank { "Member" },
        "targetUserId" to targetUserId.take(128).ifBlank { "all" },
        "targetUserName" to targetUserName.take(40).ifBlank { "গ্রুপ ভয়েস কল" },
        "status" to status,
        "sdpOffer" to sdpOffer.take(20000),
        "sdpAnswer" to sdpAnswer.take(20000),
        "latestAudioChunk" to latestAudioChunk.take(60000),
        "latestAudioSpeakerId" to latestAudioSpeakerId.take(128),
        "participantCount" to participantCount.coerceIn(0L, 50L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
