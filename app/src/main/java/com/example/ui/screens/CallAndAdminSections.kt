package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCallSession
import com.example.ui.auth.PRESET_AVATARS
import com.example.ui.auth.UserAvatarView
import com.example.ui.theme.AdminGold
import com.example.ui.theme.DangerRed
import com.example.ui.viewmodel.UiState
import com.example.util.AudioHelper
import kotlinx.coroutines.delay

@Composable
fun VoiceCallLoungeSection(
    currentUserId: String,
    currentUserProfile: UserProfile?,
    activeCallsState: UiState<List<VoiceCallSession>>,
    membersState: UiState<List<UserProfile>>,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    audioHelper: AudioHelper,
    onStartCall: (String, String) -> Unit,
    onJoinCall: (VoiceCallSession) -> Unit,
    onTransmitVoiceChunk: (String, String) -> Unit,
    onEndCall: (String) -> Unit
) {
    var isSpeakerOn by rememberSaveable { mutableStateOf(true) }
    var isMicMuted by rememberSaveable { mutableStateOf(false) }
    var isTransmittingPtt by remember { mutableStateOf(false) }
    var callSeconds by remember { mutableIntStateOf(0) }

    val activeCalls = (activeCallsState as? UiState.Success)?.data ?: emptyList()
    val currentCall = activeCalls.firstOrNull()
    val members = (membersState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(currentCall?.id, isSpeakerOn, isMicMuted) {
        if (currentCall != null) {
            audioHelper.configureCallAudio(speakerOn = isSpeakerOn, micMuted = isMicMuted)
        } else {
            audioHelper.resetCallAudio()
        }
    }

    LaunchedEffect(currentCall?.id) {
        callSeconds = 0
        while (currentCall != null) {
            delay(1000L)
            callSeconds++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentCall != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Live Call",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentCall.targetUserName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "কলার: ${currentCall.callerName} • যুক্ত আছেন: ${currentCall.participantCount} জন",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (currentCall.status == "ringing") {
                            "রিং হচ্ছে (Ringing...) • %02d:%02d".format(callSeconds / 60, callSeconds % 60)
                        } else {
                            "কল সংযুক্ত (Connected) • %02d:%02d".format(callSeconds / 60, callSeconds % 60)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentCall.status == "ringing" && currentCall.callerId != currentUserId) {
                        Button(
                            onClick = { onJoinCall(currentCall) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("accept_call_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Accept call")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("কল রিসিভ / জয়েন করুন", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "লাইভ ভয়েস ট্রান্সমিটার (Live Voice Relay)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "বাটনে ট্যাপ করে কথা বলুন এবং আবার ট্যাপ করে পাঠান—কলে থাকা সবার ফোনে সাথে সাথে বাজবে!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (!hasMicPermission) {
                                        onRequestMicPermission()
                                    } else if (!isTransmittingPtt) {
                                        if (audioHelper.startRecording()) {
                                            isTransmittingPtt = true
                                        }
                                    } else {
                                        isTransmittingPtt = false
                                        val encoded = audioHelper.stopRecordingAndEncode()
                                        if (encoded != null) {
                                            onTransmitVoiceChunk(currentCall.id, encoded.first)
                                        }
                                    }
                                },
                                enabled = !isMicMuted,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isTransmittingPtt) DangerRed else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("live_call_talk_button")
                            ) {
                                Icon(
                                    imageVector = if (isTransmittingPtt) Icons.Default.Stop else Icons.Default.RecordVoiceOver,
                                    contentDescription = "Push to talk"
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTransmittingPtt) "কথা বলা শেষ হলে পাঠান" else "কলে কথা বলুন (Tap to Speak)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = { isMicMuted = !isMicMuted },
                            modifier = Modifier.testTag("call_mute_button")
                        ) {
                            Icon(
                                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute toggle"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMicMuted) "আনমিউট" else "মিউট")
                        }

                        OutlinedButton(
                            onClick = { isSpeakerOn = !isSpeakerOn },
                            modifier = Modifier.testTag("call_speaker_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speaker toggle",
                                tint = if (isSpeakerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isSpeakerOn) "স্পিকার ON" else "স্পিকার OFF")
                        }

                        Button(
                            onClick = { onEndCall(currentCall.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            modifier = Modifier.testTag("end_call_button")
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("কল কাটুন", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Voice call icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "SMART CHAT ফ্রি ভয়েস কল",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "যেকোনো মেম্বার বা পুরো গ্রুপের সাথে ১০০% ফ্রি অডিও কল শুরু করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onStartCall("all", "SMART CHAT গ্রুপ কল") },
                        enabled = currentUserProfile?.isBlocked != true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_group_call_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Start group call")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("গ্রুপ ভয়েস কল শুরু করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "মেম্বারদের সরাসরি কল করুন (${members.size} জন)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                members.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatarView(
                                avatarData = member.avatarData,
                                username = member.username,
                                sizeDp = 38,
                                isOnline = member.isOnline
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = member.username,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (member.isBlocked) "ব্লকড" else member.statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (member.isBlocked) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (member.userId != currentUserId && !member.isBlocked) {
                            OutlinedButton(
                                onClick = {
                                    onStartCall(member.userId, "${member.username}-এর সাথে কল")
                                },
                                modifier = Modifier.testTag("call_member_${member.userId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call ${member.username}",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("কল দিন")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MembersAndAdminSection(
    currentUserId: String,
    isSuperAdmin: Boolean,
    membersState: UiState<List<UserProfile>>,
    onToggleBlockUser: (UserProfile) -> Unit,
    onCallMember: (UserProfile) -> Unit,
    onClearExpiredMessages: (Boolean) -> Unit
) {
    val members = (membersState as? UiState.Success)?.data ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(
                1.dp,
                if (isSuperAdmin) AdminGold else MaterialTheme.colorScheme.outline
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin status",
                        tint = AdminGold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSuperAdmin) {
                            "সুপার অ্যাডমিন প্যানেল সক্রিয় ($ADMIN_EMAIL)"
                        } else {
                            "গ্রুপ অ্যাডমিন: $ADMIN_EMAIL"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSuperAdmin) AdminGold else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isSuperAdmin) {
                        "আপনি যেকোনো মেম্বারকে ব্লক/আনব্লক করতে পারবেন এবং এক ক্লিকে মেসেজ ক্লিয়ার করে Firebase স্টোরেজ খালি রাখতে পারবেন।"
                    } else {
                        "অ্যাডমিন চাইলে যেকোনো মেম্বারকে ব্লক বা আনব্লক করতে পারেন।"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isSuperAdmin) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onClearExpiredMessages(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_clean_expired_button")
                        ) {
                            Icon(Icons.Default.AutoDelete, contentDescription = "Clean expired", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("পুরনো মেসেজ ক্লিন")
                        }
                        Button(
                            onClick = { onClearExpiredMessages(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_clear_all_messages_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("সব SMS ডিলিট", color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "গ্রুপ মেম্বার লিস্ট (${members.size} জন)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("members_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(members, key = { it.userId.ifBlank { it.id } }) { member ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(
                        1.dp,
                        if (member.isBlocked) DangerRed else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            UserAvatarView(
                                avatarData = member.avatarData,
                                username = member.username,
                                sizeDp = 44,
                                isOnline = member.isOnline
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = member.username,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (member.isAdminBadge) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = "Verified Admin",
                                            tint = AdminGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (member.isBlocked) {
                                        "স্ট্যাটাস: ব্লকড (Blocked by Admin)"
                                    } else {
                                        member.statusText
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (member.isBlocked) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (member.userId != currentUserId && !member.isBlocked) {
                                IconButton(
                                    onClick = { onCallMember(member) },
                                    modifier = Modifier.testTag("member_call_${member.userId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call ${member.username}",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (isSuperAdmin && member.userId != currentUserId) {
                                Button(
                                    onClick = { onToggleBlockUser(member) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (member.isBlocked) MaterialTheme.colorScheme.primary else DangerRed
                                    ),
                                    modifier = Modifier.testTag("block_toggle_${member.userId}")
                                ) {
                                    Icon(
                                        imageVector = if (member.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                        contentDescription = if (member.isBlocked) "Unblock" else "Block",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (member.isBlocked) "আনব্লক" else "ব্লক করুন",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileAndStorageSection(
    currentUserProfile: UserProfile?,
    isSuperAdmin: Boolean,
    onUpdateProfile: (String, String, String, Long) -> Unit,
    onRunCleanup: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var username by remember(currentUserProfile?.username) {
        mutableStateOf(currentUserProfile?.username ?: "")
    }
    var statusText by remember(currentUserProfile?.statusText) {
        mutableStateOf(currentUserProfile?.statusText ?: "SMART CHAT মেম্বার")
    }
    var avatarData by remember(currentUserProfile?.avatarData) {
        mutableStateOf(currentUserProfile?.avatarData ?: "avatar_emerald")
    }
    var autoDeleteHours by remember(currentUserProfile?.autoDeleteHours) {
        mutableLongStateOf(currentUserProfile?.autoDeleteHours ?: 24L)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val compressed = AudioHelper.compressGalleryImageToBase64(context, uri)
            if (compressed != null) {
                avatarData = compressed
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatarView(
                    avatarData = avatarData,
                    username = username.ifBlank { "S" },
                    sizeDp = 76,
                    isOnline = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("profile_change_photo_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("প্রোফাইল ছবি পরিবর্তন করুন")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PRESET_AVATARS.forEach { (key, color) ->
                        val selected = avatarData == key
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { avatarData = key }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(40) },
                    label = { Text("আপনার ইউজারনেম (Username)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_username_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = statusText,
                    onValueChange = { statusText = it.take(120) },
                    label = { Text("স্ট্যাটাস মেসেজ (Status)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_status_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("মেসেজ অটো-ডিলিট সময়:", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = autoDeleteHours == 24L,
                            onClick = { autoDeleteHours = 24L },
                            label = { Text("১ দিন (24h)") }
                        )
                        FilterChip(
                            selected = autoDeleteHours == 48L,
                            onClick = { autoDeleteHours = 48L },
                            label = { Text("২ দিন (48h)") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onUpdateProfile(username, avatarData, statusText, autoDeleteHours)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_profile_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("প্রোফাইল সেভ করুন", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoDelete,
                        contentDescription = "Auto delete info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Firebase Free Plan স্টোরেজ সেভার",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "আপনার Firebase ফ্রি প্ল্যান যেন কখনোই ফুল না হয়, সেজন্য ১-২ দিন (২৪ থেকে ৪৮ ঘণ্টা) পুরনো সব চ্যাট ও ভয়েস মেসেজ স্বয়ংক্রিয়ভাবে ডিলিট হয়ে যায়।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onRunCleanup(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_cleanup_button")
                ) {
                    Icon(Icons.Default.AutoDelete, contentDescription = "Clean expired")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("এখনই পুরনো মেসেজ ক্লিন করুন")
                }

                if (isSuperAdmin) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onRunCleanup(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_admin_clear_all_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Admin clear all", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("অ্যাডমিন: সব SMS এক ক্লিকে মুছে ফেলুন", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_sign_out_button"),
                    border = BorderStroke(1.dp, DangerRed)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", tint = DangerRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লগআউট (Sign Out)", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
