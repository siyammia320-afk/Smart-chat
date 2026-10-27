package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ADMIN_EMAIL
import com.example.data.model.UserProfile
import com.example.ui.auth.OnboardingPreferences
import com.example.ui.auth.UserAvatarView
import com.example.ui.theme.AdminGold
import com.example.ui.theme.DangerRed
import com.example.ui.viewmodel.SmartChatViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.AudioHelper

enum class SmartChatTab(val titleBn: String) {
    CHAT("গ্রুপ চ্যাট"),
    CALL("ভয়েস কল"),
    MEMBERS("মেম্বার ও অ্যাডমিন"),
    PROFILE("প্রোফাইল")
}

@Composable
fun SmartChatMainScreen(
    viewModel: SmartChatViewModel,
    initialPrefs: OnboardingPreferences?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val audioHelper = remember { AudioHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            audioHelper.releaseAll()
        }
    }

    LaunchedEffect(viewModel.currentUserId) {
        viewModel.initializeProfile(initialPrefs)
    }

    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val membersState by viewModel.membersState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val activeCallsState by viewModel.activeCallsState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(SmartChatTab.CHAT) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    BackHandler(enabled = currentTab != SmartChatTab.CHAT) {
        currentTab = SmartChatTab.CHAT
    }

    var lastPlayedCallTimestamp by remember { mutableLongStateOf(0L) }
    val activeCalls = (activeCallsState as? UiState.Success)?.data ?: emptyList()
    val primaryCall = activeCalls.firstOrNull()

    LaunchedEffect(primaryCall?.updatedAt?.seconds, primaryCall?.latestAudioChunk) {
        val call = primaryCall ?: return@LaunchedEffect
        val sec = call.updatedAt?.seconds ?: 0L
        if (call.latestAudioChunk.isNotBlank() &&
            call.latestAudioSpeakerId != viewModel.currentUserId &&
            sec > lastPlayedCallTimestamp
        ) {
            lastPlayedCallTimestamp = sec
            audioHelper.playBase64Audio(
                audioBase64 = call.latestAudioChunk,
                onCompletion = {},
                onError = {}
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                FlatTopHeaderBar(
                    currentUserProfile = currentUserProfile,
                    isSuperAdmin = viewModel.isSuperAdmin,
                    activeCallCount = activeCalls.size,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onOpenCallTab = { currentTab = SmartChatTab.CALL },
                    onSignOut = onSignOut
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = currentTab == SmartChatTab.CHAT,
                            onClick = { currentTab = SmartChatTab.CHAT },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "গ্রুপ চ্যাট") },
                            label = { Text("চ্যাট") },
                            modifier = Modifier.testTag("nav_tab_chat")
                        )
                        NavigationBarItem(
                            selected = currentTab == SmartChatTab.CALL,
                            onClick = { currentTab = SmartChatTab.CALL },
                            icon = { Icon(Icons.Default.Call, contentDescription = "ভয়েস কল") },
                            label = {
                                Text(
                                    if (activeCalls.isNotEmpty()) "কল (${activeCalls.size})" else "ভয়েস কল"
                                )
                            },
                            modifier = Modifier.testTag("nav_tab_call")
                        )
                        NavigationBarItem(
                            selected = currentTab == SmartChatTab.MEMBERS,
                            onClick = { currentTab = SmartChatTab.MEMBERS },
                            icon = { Icon(Icons.Default.Group, contentDescription = "মেম্বার ও অ্যাডমিন") },
                            label = { Text(if (viewModel.isSuperAdmin) "অ্যাডমিন" else "মেম্বার") },
                            modifier = Modifier.testTag("nav_tab_members")
                        )
                        NavigationBarItem(
                            selected = currentTab == SmartChatTab.PROFILE,
                            onClick = { currentTab = SmartChatTab.PROFILE },
                            icon = { Icon(Icons.Default.Person, contentDescription = "প্রোফাইল") },
                            label = { Text("প্রোফাইল") },
                            modifier = Modifier.testTag("nav_tab_profile")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        NavigationRailItem(
                            selected = currentTab == SmartChatTab.CHAT,
                            onClick = { currentTab = SmartChatTab.CHAT },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "গ্রুপ চ্যাট") },
                            label = { Text("চ্যাট") }
                        )
                        NavigationRailItem(
                            selected = currentTab == SmartChatTab.CALL,
                            onClick = { currentTab = SmartChatTab.CALL },
                            icon = { Icon(Icons.Default.Call, contentDescription = "ভয়েস কল") },
                            label = { Text("ভয়েস কল") }
                        )
                        NavigationRailItem(
                            selected = currentTab == SmartChatTab.MEMBERS,
                            onClick = { currentTab = SmartChatTab.MEMBERS },
                            icon = { Icon(Icons.Default.Group, contentDescription = "মেম্বার") },
                            label = { Text("অ্যাডমিন/মেম্বার") }
                        )
                        NavigationRailItem(
                            selected = currentTab == SmartChatTab.PROFILE,
                            onClick = { currentTab = SmartChatTab.PROFILE },
                            icon = { Icon(Icons.Default.Person, contentDescription = "প্রোফাইল") },
                            label = { Text("প্রোফাইল") }
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    if (currentUserProfile?.isBlocked == true) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = DangerRed.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DangerRed)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = "Blocked icon",
                                    tint = DangerRed
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "অ্যাডমিন ($ADMIN_EMAIL) আপনাকে চ্যাট এবং ভয়েস কল থেকে ব্লক করেছেন।",
                                    color = DangerRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (primaryCall != null && currentTab != SmartChatTab.CALL) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { currentTab = SmartChatTab.CALL }
                                .testTag("active_call_banner"),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Active Call",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "লাইভ কল চলছে: ${primaryCall.callerName} (${primaryCall.participantCount} জন) — জয়েন করতে ট্যাপ করুন",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    if (statusBanner != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusBanner!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearBanner() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss notification",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    when (currentTab) {
                        SmartChatTab.CHAT -> GroupChatSection(
                            currentUserId = viewModel.currentUserId,
                            currentUserProfile = currentUserProfile,
                            isSuperAdmin = viewModel.isSuperAdmin,
                            messagesState = messagesState,
                            replyingTo = replyingTo,
                            playingMessageId = playingMessageId,
                            hasMicPermission = hasMicPermission,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            audioHelper = audioHelper,
                            onSendText = { text, isNotice ->
                                viewModel.sendTextMessage(text, isNotice)
                            },
                            onSendVoice = { base64, durationSec ->
                                viewModel.sendVoiceMessage(base64, durationSec)
                            },
                            onSelectReply = { viewModel.setReplyTarget(it) },
                            onDeleteMessage = { viewModel.deleteMessage(it) },
                            onSetPlayingId = { viewModel.setPlayingMessageId(it) },
                            onStartGroupCall = {
                                viewModel.startVoiceCall()
                                currentTab = SmartChatTab.CALL
                            }
                        )

                        SmartChatTab.CALL -> VoiceCallLoungeSection(
                            currentUserId = viewModel.currentUserId,
                            currentUserProfile = currentUserProfile,
                            activeCallsState = activeCallsState,
                            membersState = membersState,
                            hasMicPermission = hasMicPermission,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            audioHelper = audioHelper,
                            onStartCall = { targetId, targetName ->
                                viewModel.startVoiceCall(targetId, targetName)
                            },
                            onJoinCall = { viewModel.joinVoiceCall(it) },
                            onTransmitVoiceChunk = { callId, base64 ->
                                viewModel.transmitLiveVoiceChunk(callId, base64)
                            },
                            onEndCall = { viewModel.endVoiceCall(it) }
                        )

                        SmartChatTab.MEMBERS -> MembersAndAdminSection(
                            currentUserId = viewModel.currentUserId,
                            isSuperAdmin = viewModel.isSuperAdmin,
                            membersState = membersState,
                            onToggleBlockUser = { viewModel.toggleBlockUser(it) },
                            onCallMember = { member ->
                                viewModel.startVoiceCall(member.userId, "${member.username}-এর সাথে কল")
                                currentTab = SmartChatTab.CALL
                            },
                            onClearExpiredMessages = { deleteAll ->
                                viewModel.runManualStorageCleanup(deleteAllAsAdmin = deleteAll)
                            }
                        )

                        SmartChatTab.PROFILE -> ProfileAndStorageSection(
                            currentUserProfile = currentUserProfile,
                            isSuperAdmin = viewModel.isSuperAdmin,
                            onUpdateProfile = { name, avatar, status, ttl ->
                                viewModel.updateMyProfile(name, avatar, status, ttl)
                            },
                            onRunCleanup = { deleteAll ->
                                viewModel.runManualStorageCleanup(deleteAllAsAdmin = deleteAll)
                            },
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlatTopHeaderBar(
    currentUserProfile: UserProfile?,
    isSuperAdmin: Boolean,
    activeCallCount: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenCallTab: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    UserAvatarView(
                        avatarData = currentUserProfile?.avatarData ?: "avatar_emerald",
                        username = currentUserProfile?.username ?: "S",
                        sizeDp = 40,
                        isOnline = true
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SMART CHAT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (isSuperAdmin) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AdminGold.copy(alpha = 0.2f))
                                        .border(1.dp, AdminGold, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ADMIN",
                                        color = AdminGold,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${currentUserProfile?.username ?: "লোড হচ্ছে..."} • ২৪-৪৮ঘঃ অটো ক্লিন",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenCallTab,
                        modifier = Modifier.testTag("top_bar_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "ভয়েস কল ট্যাব",
                            tint = if (activeCallCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "থিম পরিবর্তন করুন"
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "লগআউট করুন",
                            tint = DangerRed
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}
