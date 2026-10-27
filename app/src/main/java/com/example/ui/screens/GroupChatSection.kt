package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.ui.auth.UserAvatarView
import com.example.ui.theme.AdminGold
import com.example.ui.theme.DangerRed
import com.example.ui.viewmodel.UiState
import com.example.util.AudioHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun GroupChatSection(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    isSuperAdmin: Boolean,
    messagesState: UiState<List<ChatMessage>>,
    replyingTo: ChatMessage?,
    playingMessageId: String?,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    audioHelper: AudioHelper,
    onSendText: (String, Boolean) -> Unit,
    onSendVoice: (String, Int) -> Unit,
    onSelectReply: (ChatMessage?) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onSetPlayingId: (String?) -> Unit,
    onStartGroupCall: () -> Unit
) {
    var messageInput by rememberSaveable { mutableStateOf("") }
    var sendAsAdminNotice by rememberSaveable { mutableStateOf(false) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

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

    val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val latestNotice = messages.lastOrNull { it.type == "notice" }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (latestNotice != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = AdminGold.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AdminGold)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = "অ্যাডমিন নোটিশ",
                        tint = AdminGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "অ্যাডমিন নোটিশ (${latestNotice.senderName})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AdminGold
                        )
                        Text(
                            text = latestNotice.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "গ্রুপ চ্যাট লোড হচ্ছে...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = messagesState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Empty chat",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "SMART CHAT গ্রুপে স্বাগতম!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "প্রথম টেক্সট মেসেজ, ভয়েস মেসেজ পাঠান অথবা ভয়েস কল শুরু করুন। সব মেসেজ ২৪-৪৮ ঘণ্টা পর স্বয়ংক্রিয়ভাবে ডিলিট হয়ে যাবে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = onStartGroupCall,
                                modifier = Modifier.testTag("empty_state_start_call_button")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Start call")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("গ্রুপ ভয়েস কল শুরু করুন")
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("chat_messages_list"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id.ifBlank { it.hashCode().toString() } }) { msg ->
                                ChatMessageBubble(
                                    message = msg,
                                    isMine = msg.senderId == currentUserId,
                                    canDelete = msg.senderId == currentUserId || isSuperAdmin,
                                    isPlaying = playingMessageId == msg.id,
                                    onReply = { onSelectReply(msg) },
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
        }

        if (replyingTo != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "রিপ্লাই দিচ্ছেন: ${replyingTo.senderName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = replyingTo.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onSelectReply(null) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                    }
                }
            }
        }

        if (isRecordingVoice) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DangerRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, DangerRed)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Recording",
                            tint = DangerRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ভয়েস রেকর্ড হচ্ছে... ${recordingSeconds}s",
                            fontWeight = FontWeight.Bold,
                            color = DangerRed
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                isRecordingVoice = false
                                audioHelper.cancelRecording()
                            },
                            modifier = Modifier.testTag("cancel_voice_recording_button")
                        ) {
                            Text("বাতিল")
                        }
                        Button(
                            onClick = {
                                isRecordingVoice = false
                                val result = audioHelper.stopRecordingAndEncode()
                                if (result != null) {
                                    onSendVoice(result.first, result.second)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("send_voice_recording_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send voice")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পাঠান")
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                if (isSuperAdmin) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = sendAsAdminNotice,
                            onClick = { sendAsAdminNotice = !sendAsAdminNotice },
                            label = { Text("অ্যাডমিন পিন নোটিশ হিসেবে পাঠান") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Campaign,
                                    contentDescription = "Notice mode",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("admin_notice_toggle_chip")
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (!hasMicPermission) {
                                onRequestMicPermission()
                            } else if (!isRecordingVoice) {
                                if (audioHelper.startRecording()) {
                                    isRecordingVoice = true
                                }
                            } else {
                                isRecordingVoice = false
                                val result = audioHelper.stopRecordingAndEncode()
                                if (result != null) {
                                    onSendVoice(result.first, result.second)
                                }
                            }
                        },
                        enabled = currentUserProfile?.isBlocked != true,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecordingVoice) DangerRed else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .testTag("voice_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "ভয়েস মেসেজ রেকর্ড করুন",
                            tint = if (isRecordingVoice) Color.White else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it.take(2000) },
                        placeholder = {
                            Text(
                                if (currentUserProfile?.isBlocked == true) {
                                    "আপনি ব্লকড আছেন"
                                } else {
                                    "মেসেজ লিখুন..."
                                }
                            )
                        },
                        enabled = currentUserProfile?.isBlocked != true,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank()) {
                                onSendText(messageInput, sendAsAdminNotice)
                                messageInput = ""
                                sendAsAdminNotice = false
                            }
                        },
                        enabled = messageInput.isNotBlank() && currentUserProfile?.isBlocked != true,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "মেসেজ পাঠান",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    canDelete: Boolean,
    isPlaying: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onTogglePlayVoice: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeText = remember(message.createdAt) {
        val date = message.createdAt?.toDate() ?: Date()
        timeFormat.format(date)
    }

    val bubbleBg = when {
        message.type == "notice" -> AdminGold.copy(alpha = 0.16f)
        isMine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        message.type == "notice" -> AdminGold
        message.senderIsAdmin -> AdminGold.copy(alpha = 0.7f)
        isMine -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isMine) {
            UserAvatarView(
                avatarData = message.senderAvatar,
                username = message.senderName,
                sizeDp = 34
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier.widthIn(min = 140.dp, max = 320.dp),
            shape = RoundedCornerShape(
                topStart = if (isMine) 14.dp else 4.dp,
                topEnd = if (isMine) 4.dp else 14.dp,
                bottomStart = 14.dp,
                bottomEnd = 14.dp
            ),
            color = bubbleBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (message.senderIsAdmin) AdminGold else MaterialTheme.colorScheme.primary
                        )
                        if (message.senderIsAdmin) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "★ অ্যাডমিন",
                                style = MaterialTheme.typography.labelSmall,
                                color = AdminGold,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Text(
                        text = "${message.ttlHours}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (message.replyToId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        Column {
                            Text(
                                text = message.replyToSender,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = message.replyToPreview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (message.type == "voice") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            .clickable { onTogglePlayVoice() }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("play_voice_msg_${message.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop voice" else "Play voice",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPlaying) "বাজছে (Playing...)" else "ভয়েস মেসেজ শুনুন",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "দৈর্ঘ্য: ${message.audioDurationSec} সেকেন্ড",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onReply,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "রিপ্লাই দিন",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (canDelete) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("delete_msg_${message.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "মেসেজ ডিলিট করুন",
                                    modifier = Modifier.size(15.dp),
                                    tint = DangerRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
