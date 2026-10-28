package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.GroupSettings
import com.example.data.model.UserProfile
import com.example.data.model.VipInfo
import com.example.data.model.buildDmRoomKey
import com.example.ui.auth.UserAvatarView
import com.example.ui.theme.AdminGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.UiState
import com.example.util.AudioHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "🔥", "😮", "😢")
private val URL_REGEX = Regex("""(https?://[^\s]+|www\.[^\s]+)""", RegexOption.IGNORE_CASE)
private val MENTION_REGEX = Regex("""@[a-zA-Z0-9_\u0980-\u09FF.]+""")

@Composable
fun GroupChatSection(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    isSuperAdmin: Boolean,
    groupSettings: GroupSettings,
    activeDmPeer: UserProfile?,
    members: List<UserProfile>,
    messagesState: UiState<List<ChatMessage>>,
    replyingTo: ChatMessage?,
    editingMessage: ChatMessage?,
    playingMessageId: String?,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    audioHelper: AudioHelper,
    onSendText: (String, Boolean) -> Unit,
    onSendVoice: (String, Int) -> Unit,
    onSelectReply: (ChatMessage?) -> Unit,
    onStartEdit: (ChatMessage?) -> Unit,
    onToggleReaction: (ChatMessage, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSetPlayingId: (String?) -> Unit,
    onOpenUserDm: (UserProfile) -> Unit,
    onCloseDm: () -> Unit,
    onTypingChanged: (String?) -> Unit
) {
    var messageInput by rememberSaveable { mutableStateOf("") }
    var sendAsAdminNotice by rememberSaveable { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var nowEpochSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    val listState = rememberLazyListState()

    val memberMap = remember(members) {
        members.associateBy { it.userId }
    }

    val currentRoomKey = remember(currentUserId, activeDmPeer?.userId) {
        if (activeDmPeer != null) {
            buildDmRoomKey(currentUserId, activeDmPeer.userId)
        } else {
            "group"
        }
    }

    // Periodic tick for real-time typing expiration check
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000L)
            nowEpochSec = System.currentTimeMillis() / 1000L
        }
    }

    // Populate input box when user taps Edit on their message
    LaunchedEffect(editingMessage?.id) {
        if (editingMessage != null) {
            messageInput = editingMessage.text
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingSeconds = 0
            while (isRecordingVoice && recordingSeconds < 60) {
                delay(1000L)
                recordingSeconds++
            }
            if (isRecordingVoice && recordingSeconds >= 60) {
                isRecordingVoice = false
                val encoded = audioHelper.stopRecordingAndEncode()
                if (encoded != null) {
                    onSendVoice(encoded.first, encoded.second)
                }
            }
        }
    }

    val allMessages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val visibleMessages = remember(allMessages, activeDmPeer?.userId, currentUserId) {
        if (activeDmPeer != null) {
            val dmKey = buildDmRoomKey(currentUserId, activeDmPeer.userId)
            allMessages.filter { it.dmRoomKeyOrNull() == dmKey }
        } else {
            allMessages.filter { !it.isDirectMessage() }
        }
    }

    val latestNotice = remember(visibleMessages, activeDmPeer) {
        if (activeDmPeer == null) visibleMessages.lastOrNull { it.type == "notice" } else null
    }

    val typingUsers = remember(members, currentRoomKey, currentUserId, nowEpochSec) {
        members.filter {
            it.userId != currentUserId && it.isTypingInRoom(currentRoomKey, nowEpochSec)
        }
    }

    // Mention suggestions when user types '@'
    val currentMentionQuery = remember(messageInput) {
        val lastAt = messageInput.lastIndexOf('@')
        if (lastAt >= 0 && (lastAt == 0 || messageInput[lastAt - 1] == ' ')) {
            val sub = messageInput.substring(lastAt + 1)
            if (!sub.contains(' ')) sub else null
        } else {
            null
        }
    }

    val mentionSuggestions = remember(currentMentionQuery, members, currentUserId) {
        if (currentMentionQuery == null) {
            emptyList()
        } else {
            members.filter {
                it.userId != currentUserId &&
                    it.username.contains(currentMentionQuery, ignoreCase = true)
            }.take(8)
        }
    }

    LaunchedEffect(visibleMessages.size, activeDmPeer?.userId) {
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.size - 1)
        }
    }

    val isInputLocked = activeDmPeer == null && groupSettings.isGroupOff && !isSuperAdmin

    Column(modifier = Modifier.fillMaxSize()) {
        // 1-to-1 Private Inbox Header Bar when chatting privately
        if (activeDmPeer != null) {
            val livePeer = memberMap[activeDmPeer.userId] ?: activeDmPeer
            val peerVip = livePeer.computeVipInfo()
            val peerTyping = livePeer.isTypingInRoom(currentRoomKey, nowEpochSec)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCloseDm,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("close_dm_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        UserAvatarView(
                            avatarData = livePeer.avatarData,
                            username = livePeer.username,
                            sizeDp = 36,
                            isOnline = livePeer.isOnline
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = livePeer.username,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                VipBadgeChip(vipInfo = peerVip)
                            }
                            Text(
                                text = when {
                                    peerTyping -> "Typing..."
                                    livePeer.isOnline -> "Online"
                                    else -> "Offline"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (peerTyping) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    peerTyping -> MaterialTheme.colorScheme.primary
                                    livePeer.isOnline -> OnlineGreen
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else if (typingUsers.isNotEmpty()) {
            // Real-time typing bar in Group Chat
            val names = typingUsers.joinToString(", ") { it.username }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Text(
                    text = "$names typing...",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Pinned Notice in Group Chat
        if (latestNotice != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = AdminGold.copy(alpha = 0.14f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AdminGold)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "Notice",
                        tint = AdminGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = latestNotice.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Messages Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = messagesState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("chat_messages_list"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            visibleMessages,
                            key = { it.id.ifBlank { it.hashCode().toString() } }
                        ) { msg ->
                            val senderProfile = memberMap[msg.senderId]
                            val vipInfo = senderProfile?.computeVipInfo(msg.senderIsAdmin)
                                ?: VipInfo(
                                    title = if (msg.senderIsAdmin) "ROYAL VIP" else "BRONZE VIP",
                                    level = if (msg.senderIsAdmin) 10 else 1,
                                    daysOld = 0L,
                                    colorHex = if (msg.senderIsAdmin) 0xFFF59E0B else 0xFFD97706
                                )

                            ChatMessageBubble(
                                message = msg,
                                currentUserId = currentUserId,
                                senderOnline = senderProfile?.isOnline == true,
                                senderTyping = senderProfile?.isTypingInRoom(currentRoomKey, nowEpochSec) == true,
                                vipInfo = vipInfo,
                                isMine = msg.senderId == currentUserId,
                                canDelete = msg.senderId == currentUserId || isSuperAdmin,
                                canEdit = msg.senderId == currentUserId && msg.type != "voice",
                                isPlaying = playingMessageId == msg.id,
                                onReply = { onSelectReply(msg) },
                                onEdit = { onStartEdit(msg) },
                                onMentionSender = {
                                    val tag = "@${msg.senderName.replace(" ", "_")} "
                                    if (!messageInput.contains(tag)) {
                                        messageInput = "$messageInput$tag"
                                    }
                                },
                                onClickSender = {
                                    if (msg.senderId != currentUserId) {
                                        val targetProfile = senderProfile ?: UserProfile(
                                            id = msg.senderId,
                                            userId = msg.senderId,
                                            username = msg.senderName,
                                            avatarData = msg.senderAvatar,
                                            isAdminBadge = msg.senderIsAdmin
                                        )
                                        onOpenUserDm(targetProfile)
                                    }
                                },
                                onReactionClick = { emoji ->
                                    onToggleReaction(msg, emoji)
                                },
                                onDelete = { onDeleteMessage(msg.id) },
                                onTogglePlayVoice = {
                                    if (playingMessageId == msg.id) {
                                        audioHelper.stopPlayback()
                                        onSetPlayingId(null)
                                    } else {
                                        onSetPlayingId(msg.id)
                                        audioHelper.playBase64Audio(
                                            audioBase64 = msg.audioBase64,
                                            onCompletion = { onSetPlayingId(null) },
                                            onError = { onSetPlayingId(null) }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Composer Area
        ChatComposerBar(
            messageInput = messageInput,
            onMessageInputChange = { newValue ->
                messageInput = newValue
                onTypingChanged(if (newValue.isNotBlank()) currentRoomKey else null)
            },
            isSuperAdmin = isSuperAdmin && activeDmPeer == null,
            isBlocked = currentUserProfile?.isBlocked == true,
            isGroupLockedForUser = isInputLocked,
            sendAsAdminNotice = sendAsAdminNotice,
            onToggleAdminNotice = { sendAsAdminNotice = !sendAsAdminNotice },
            replyingTo = replyingTo,
            onClearReply = { onSelectReply(null) },
            editingMessage = editingMessage,
            onCancelEdit = {
                onStartEdit(null)
                messageInput = ""
            },
            mentionSuggestions = mentionSuggestions,
            onSelectMention = { member ->
                val lastAt = messageInput.lastIndexOf('@')
                if (lastAt >= 0) {
                    val cleanName = member.username.replace(" ", "_")
                    messageInput = messageInput.substring(0, lastAt) + "@$cleanName "
                }
            },
            isRecordingVoice = isRecordingVoice,
            recordingSeconds = recordingSeconds,
            onToggleVoiceRecording = {
                if (!hasMicPermission) {
                    onRequestMicPermission()
                } else if (!isRecordingVoice) {
                    if (audioHelper.startRecording()) {
                        isRecordingVoice = true
                    }
                } else {
                    isRecordingVoice = false
                    val encoded = audioHelper.stopRecordingAndEncode()
                    if (encoded != null) {
                        onSendVoice(encoded.first, encoded.second)
                    }
                }
            },
            onCancelVoiceRecording = {
                isRecordingVoice = false
                audioHelper.cancelRecording()
            },
            onSendClicked = {
                if (messageInput.isNotBlank()) {
                    onSendText(messageInput, sendAsAdminNotice)
                    messageInput = ""
                    sendAsAdminNotice = false
                }
            }
        )
    }
}

@Composable
fun VipBadgeChip(vipInfo: VipInfo) {
    val badgeColor = Color(vipInfo.colorHex)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor.copy(alpha = 0.16f))
            .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = vipInfo.shortBadge,
            color = badgeColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    currentUserId: String,
    senderOnline: Boolean,
    senderTyping: Boolean,
    vipInfo: VipInfo,
    isMine: Boolean,
    canDelete: Boolean,
    canEdit: Boolean,
    isPlaying: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onMentionSender: () -> Unit,
    onClickSender: () -> Unit,
    onReactionClick: (String) -> Unit,
    onDelete: () -> Unit,
    onTogglePlayVoice: () -> Unit
) {
    var showReactionBar by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val isNotice = message.type == "notice"
    val containerColor = when {
        isNotice -> AdminGold.copy(alpha = 0.16f)
        isMine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isNotice -> AdminGold
        isMine -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val cleanReply = remember(message.replyToPreview) { message.cleanReplyText() }
    val isEdited = remember(message.replyToPreview) { message.isEditedMessage() }
    val reactions = remember(message.replyToPreview) { message.reactionsMap() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 120.dp, max = 315.dp),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isMine) 12.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 12.dp
            ),
            color = containerColor,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                // Header: Avatar + Clickable Name + Online/Typing + VIP Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !isMine) { onClickSender() }
                    ) {
                        UserAvatarView(
                            avatarData = message.senderAvatar,
                            username = message.senderName,
                            sizeDp = 24,
                            isOnline = senderOnline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = message.senderName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (message.senderIsAdmin) AdminGold else MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                VipBadgeChip(vipInfo = vipInfo)
                            }
                            if (senderTyping) {
                                Text(
                                    text = "Typing...",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Compact Uniform Action Icons Row (26.dp each so they always fit on mobile)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { showReactionBar = !showReactionBar },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SentimentSatisfiedAlt,
                                contentDescription = "React",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isMine) {
                            IconButton(
                                onClick = onMentionSender,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AlternateEmail,
                                    contentDescription = "Mention",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = onReply,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Reply",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canEdit) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (canDelete) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(15.dp),
                                    tint = DangerRed
                                )
                            }
                        }
                    }
                }

                // Quick Emoji Picker Row when toggled
                if (showReactionBar) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QUICK_REACTIONS.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        showReactionBar = false
                                        onReactionClick(emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // Reply Preview Box
                if (message.actualReplyMessageId().isNotBlank() && cleanReply.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = message.replyToSender,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = cleanReply,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Message Body (Voice or Clickable Text with Links & @Mentions)
                if (message.type == "voice" && message.audioBase64.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTogglePlayVoice() }
                            .testTag("voice_message_play_${message.id}"),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Play voice",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPlaying) "Playing..." else "Voice (${message.audioDurationSec}s)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    LinkAndMentionText(
                        text = message.text,
                        onOpenUrl = { rawUrl ->
                            val fullUrl = if (rawUrl.startsWith("http://", ignoreCase = true) ||
                                rawUrl.startsWith("https://", ignoreCase = true)
                            ) {
                                rawUrl
                            } else {
                                "https://$rawUrl"
                            }
                            try {
                                uriHandler.openUri(fullUrl)
                            } catch (_: Exception) {
                            }
                        }
                    )
                }

                // Active Reactions Chips Row
                if (reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        reactions.forEach { (emoji, userIds) ->
                            val iReacted = userIds.contains(currentUserId)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (iReacted) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (iReacted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onReactionClick(emoji) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$emoji ${userIds.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEdited) {
                        Text(
                            text = "edited • ",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = formatTimestampShort(message.createdAt?.toDate()),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkAndMentionText(
    text: String,
    onOpenUrl: (String) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val linkColor = Color(0xFF38BDF8)

    val annotated = remember(text, primaryColor, textColor, linkColor) {
        buildAnnotatedString {
            append(text)
            addStyle(
                style = SpanStyle(color = textColor, fontSize = 14.sp),
                start = 0,
                end = text.length
            )
            // Style @mentions
            MENTION_REGEX.findAll(text).forEach { match ->
                addStyle(
                    style = SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
            // Style and tag URLs
            URL_REGEX.findAll(text).forEach { match ->
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = match.range.first,
                    end = match.range.last + 1
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = match.value,
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onOpenUrl(annotation.item)
                }
        }
    )
}

@Composable
private fun ChatComposerBar(
    messageInput: String,
    onMessageInputChange: (String) -> Unit,
    isSuperAdmin: Boolean,
    isBlocked: Boolean,
    isGroupLockedForUser: Boolean,
    sendAsAdminNotice: Boolean,
    onToggleAdminNotice: () -> Unit,
    replyingTo: ChatMessage?,
    onClearReply: () -> Unit,
    editingMessage: ChatMessage?,
    onCancelEdit: () -> Unit,
    mentionSuggestions: List<UserProfile>,
    onSelectMention: (UserProfile) -> Unit,
    isRecordingVoice: Boolean,
    recordingSeconds: Int,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    onSendClicked: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

            // Mention Autocomplete Popup Strip
            if (mentionSuggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mentionSuggestions.forEach { member ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectMention(member) },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "@${member.username}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Editing Message Banner
            if (editingMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = editingMessage.text,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onCancelEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel edit",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Replying To Banner
            if (replyingTo != null && editingMessage == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "↩ ${replyingTo.senderName}: ${replyingTo.text.take(45)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onClearReply,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear reply",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // If Group Chat is OFF for regular users
            if (isGroupLockedForUser) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = AdminGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "অ্যাডমিন গ্রুপ চ্যাট অফ রেখেছেন",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AdminGold
                    )
                }
                return@Column
            }

            // Compact Mobile Input Row (Uniform 40.dp buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DangerRed.copy(alpha = 0.14f))
                            .border(1.dp, DangerRed, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "● Rec ${recordingSeconds}s",
                            color = DangerRed,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onCancelVoiceRecording() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "বাতিল",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DangerRed)
                                    .clickable { onToggleVoiceRecording() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "পাঠান",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    // Admin Notice Toggle Icon (Compact 40.dp button)
                    if (isSuperAdmin && editingMessage == null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (sendAsAdminNotice) AdminGold.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (sendAsAdminNotice) AdminGold else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggleAdminNotice() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "Notice toggle",
                                tint = if (sendAsAdminNotice) AdminGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Voice Note Button (Uniform 40.dp)
                    if (editingMessage == null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                .clickable(enabled = !isBlocked) { onToggleVoiceRecording() }
                                .testTag("record_voice_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Compact Flat Text Input Box (40.dp height)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                if (editingMessage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (messageInput.isEmpty()) {
                            Text(
                                text = if (sendAsAdminNotice) "নোটিশ লিখুন..." else "মেসেজ লিখুন...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = messageInput,
                            onValueChange = onMessageInputChange,
                            enabled = !isBlocked,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_message_input")
                        )
                    }

                    // Send Button (Uniform 40.dp)
                    val canSend = !isBlocked && messageInput.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (canSend) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(enabled = canSend) { onSendClicked() }
                            .testTag("send_message_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestampShort(date: Date?): String {
    if (date == null) return "Now"
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(date)
}
