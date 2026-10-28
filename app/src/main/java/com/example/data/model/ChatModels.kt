package com.example.data.model

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val GROUP_ID = "smart_chat_main"
const val LIVE_EMAIL_GROUP_ID = "smart_chat_live"
const val ADMIN_EMAIL = "siyammia320@gmail.com"
const val GROUP_CONFIG_DOC_ID = "usr_000000000000000000000001"

fun emailToUserId(email: String): String {
    val normalized = email.trim().lowercase()
    val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    val hex = bytes.take(12).joinToString("") { "%02x".format(it) }
    return "usr_$hex"
}

fun hashEmailPassword(email: String, password: String): String {
    val raw = "${email.trim().lowercase()}::smart_chat_v1::$password"
    val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

val ADMIN_USER_ID: String = emailToUserId(ADMIN_EMAIL)

fun buildDmRoomKey(uidA: String, uidB: String): String {
    val a = uidA.trim()
    val b = uidB.trim()
    return if (a <= b) "${a}_${b}" else "${b}_${a}"
}

data class GroupSettings(
    val groupName: String = "SMART CHAT",
    val isGroupOff: Boolean = false
)

data class VipInfo(
    val title: String,
    val level: Int,
    val daysOld: Long,
    val colorHex: Long
) {
    val shortBadge: String
        get() = "$title • Lv.$level"
}

data class AccountSession(
    val userId: String,
    val email: String,
    val fullName: String,
    val username: String,
    val avatarData: String,
    val autoDeleteHours: Long = 24L
)

object AccountSessionManager {
    private const val PREF_NAME = "smart_chat_account_prefs"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"
    private const val KEY_EMAIL = "email"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_AVATAR = "avatar_data"
    private const val KEY_AUTO_DELETE = "auto_delete_hours"

    private val _sessionFlow = MutableStateFlow<AccountSession?>(null)
    val sessionFlow: StateFlow<AccountSession?> = _sessionFlow.asStateFlow()

    val currentSession: AccountSession?
        get() = _sessionFlow.value

    fun init(context: Context) {
        if (_sessionFlow.value != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val activeUid = prefs.getString(KEY_ACTIVE_USER_ID, null)
        val email = prefs.getString(KEY_EMAIL, null)
        if (!activeUid.isNullOrBlank() && !email.isNullOrBlank()) {
            _sessionFlow.value = AccountSession(
                userId = activeUid,
                email = email,
                fullName = prefs.getString(KEY_FULL_NAME, "") ?: "",
                username = prefs.getString(KEY_USERNAME, "") ?: "",
                avatarData = prefs.getString(KEY_AVATAR, "avatar_indigo") ?: "avatar_indigo",
                autoDeleteHours = prefs.getLong(KEY_AUTO_DELETE, 24L)
            )
        }
    }

    fun saveSession(context: Context, session: AccountSession, passwordHash: String? = null) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString(KEY_ACTIVE_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email.trim().lowercase())
            .putString(KEY_FULL_NAME, session.fullName)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_AVATAR, session.avatarData)
            .putLong(KEY_AUTO_DELETE, session.autoDeleteHours)
        if (!passwordHash.isNullOrBlank()) {
            editor.putString("password_hash", passwordHash)
        }
        editor.apply()
        _sessionFlow.value = session
    }

    fun clearSession(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ACTIVE_USER_ID).apply()
        _sessionFlow.value = null
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val groupId: String = GROUP_ID,
    val username: String = "",
    val avatarData: String = "avatar_emerald",
    val statusText: String = "Online",
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

    fun cleanBio(): String {
        val idx = statusText.indexOf("||TYPING:")
        val raw = if (idx >= 0) statusText.substring(0, idx) else statusText
        return raw.ifBlank { if (isOnline) "Online" else "Offline" }
    }

    fun isTypingInRoom(roomKey: String, nowEpochSec: Long = System.currentTimeMillis() / 1000L): Boolean {
        val idx = statusText.indexOf("||TYPING:")
        if (idx < 0) return false
        val meta = statusText.substring(idx + 9)
        val parts = meta.split(":")
        if (parts.size < 2) return false
        val targetRoom = parts[0]
        val ts = parts[1].toLongOrNull() ?: return false
        return targetRoom == roomKey && (nowEpochSec - ts) in -5L..8L
    }

    fun computeVipInfo(isSuperAdmin: Boolean = isAdminBadge || userId == ADMIN_USER_ID): VipInfo {
        val nowSec = System.currentTimeMillis() / 1000L
        val createdSec = createdAt?.seconds ?: nowSec
        val days = ((nowSec - createdSec) / 86400L).coerceAtLeast(0L)
        return when {
            isSuperAdmin -> VipInfo("ROYAL VIP", 10, days, 0xFFF59E0B)
            days >= 30L -> VipInfo("LEGEND VIP", 6, days, 0xFFEC4899)
            days >= 15L -> VipInfo("DIAMOND VIP", 5, days, 0xFF38BDF8)
            days >= 7L -> VipInfo("GOLD VIP", 4, days, 0xFFFBBF24)
            days >= 3L -> VipInfo("SILVER VIP", 3, days, 0xFFCBD5E1)
            days >= 1L -> VipInfo("CRYSTAL VIP", 2, days, 0xFFA78BFA)
            else -> VipInfo("BRONZE VIP", 1, days, 0xFFD97706)
        }
    }
}

fun buildStatusWithTyping(cleanBio: String, roomKey: String?): String {
    val safeBio = cleanBio.replace("||TYPING:", "").take(55)
    return if (roomKey.isNullOrBlank()) {
        safeBio
    } else {
        val nowSec = System.currentTimeMillis() / 1000L
        "$safeBio||TYPING:$roomKey:$nowSec".take(120)
    }
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
        "replyToPreview" to replyToPreview.take(600),
        "ttlHours" to if (ttlHours == 48L) 48L else 24L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun isDirectMessage(): Boolean = replyToId.startsWith("DM:")

    fun dmRoomKeyOrNull(): String? {
        if (!isDirectMessage()) return null
        val withoutPrefix = replyToId.removePrefix("DM:")
        return withoutPrefix.substringBefore("|").takeIf { it.isNotBlank() }
    }

    fun actualReplyMessageId(): String {
        return if (isDirectMessage()) {
            replyToId.substringAfter("|", "")
        } else {
            replyToId
        }
    }

    fun otherDmParticipantId(myUserId: String): String? {
        val key = dmRoomKeyOrNull() ?: return null
        // Format: usr_<24hex>_usr_<24hex>
        val idx = key.indexOf("_usr_")
        if (idx <= 0) return null
        val firstUid = key.substring(0, idx)
        val secondUid = key.substring(idx + 1)
        return when (myUserId) {
            firstUid -> secondUid
            secondUid -> firstUid
            else -> null
        }
    }

    fun cleanReplyText(): String {
        return replyToPreview
            .substringBefore("||EDITED:")
            .substringBefore("||REACT:")
    }

    fun isEditedMessage(): Boolean {
        return replyToPreview.contains("||EDITED:1")
    }

    fun reactionsMap(): Map<String, List<String>> {
        val reactPart = replyToPreview.substringAfter("||REACT:", "")
        if (reactPart.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, List<String>>()
        reactPart.split(";").forEach { entry ->
            val pieces = entry.split("=", limit = 2)
            if (pieces.size == 2) {
                val emoji = pieces[0].trim()
                val users = pieces[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (emoji.isNotEmpty() && users.isNotEmpty()) {
                    result[emoji] = users
                }
            }
        }
        return result
    }
}

fun encodeReplyPreviewMetadata(
    cleanReplyText: String,
    isEdited: Boolean,
    reactions: Map<String, List<String>>
): String {
    val base = cleanReplyText
        .replace("||EDITED:", "")
        .replace("||REACT:", "")
        .take(140)
    val editedTag = if (isEdited) "||EDITED:1" else ""
    val reactEntries = reactions.entries
        .filter { it.key.isNotBlank() && it.value.isNotEmpty() }
        .joinToString(";") { (emoji, uids) ->
            "$emoji=${uids.distinct().take(15).joinToString(",")}"
        }
    val reactTag = if (reactEntries.isNotBlank()) "||REACT:$reactEntries" else ""
    return "$base$editedTag$reactTag".take(600)
}
