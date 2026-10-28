package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GroupSettings
import com.example.data.model.UserProfile
import com.example.ui.auth.OnboardingPreferences
import com.example.ui.auth.UserAvatarView
import com.example.ui.theme.AdminGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.SmartChatViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.AudioHelper

enum class SmartChatTab {
    CHAT,
    INBOX,
    MEMBERS,
    PROFILE
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

    LaunchedEffect(viewModel.currentUserId) {
        viewModel.initializeProfile(initialPrefs)
        viewModel.setOnlineState(true)
    }

    DisposableEffect(viewModel.currentUserId) {
        onDispose {
            viewModel.setOnlineState(false)
            audioHelper.releaseAll()
        }
    }

    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val groupSettings by viewModel.groupSettings.collectAsStateWithLifecycle()
    val membersState by viewModel.membersState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingTo.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val activeDmPeer by viewModel.activeDmPeer.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()

    val members = (membersState as? UiState.Success)?.data ?: emptyList()

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

    BackHandler(enabled = activeDmPeer != null || currentTab != SmartChatTab.CHAT) {
        if (activeDmPeer != null) {
            viewModel.openDirectMessage(null)
        } else {
            currentTab = SmartChatTab.CHAT
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
                    groupSettings = groupSettings,
                    onlineCount = members.count { it.isOnline },
                    isSuperAdmin = viewModel.isSuperAdmin,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onSignOut = {
                        viewModel.setOnlineState(false)
                        onSignOut()
                    }
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
                            selected = currentTab == SmartChatTab.CHAT && activeDmPeer == null,
                            onClick = {
                                viewModel.openDirectMessage(null)
                                currentTab = SmartChatTab.CHAT
                            },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "গ্রুপ") },
                            label = { Text("গ্রুপ", maxLines = 1) },
                            modifier = Modifier.testTag("nav_tab_chat")
                        )
                        NavigationBarItem(
                            selected = currentTab == SmartChatTab.INBOX || activeDmPeer != null,
                            onClick = {
                                viewModel.openDirectMessage(null)
                                currentTab = SmartChatTab.INBOX
                            },
                            icon = { Icon(Icons.Default.Forum, contentDescription = "ইনবক্স") },
                            label = { Text("ইনবক্স", maxLines = 1) },
                            modifier = Modifier.testTag("nav_tab_inbox")
                        )
                        if (viewModel.isSuperAdmin) {
                            NavigationBarItem(
                                selected = currentTab == SmartChatTab.MEMBERS,
                                onClick = {
                                    viewModel.openDirectMessage(null)
                                    currentTab = SmartChatTab.MEMBERS
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.AdminPanelSettings,
                                        contentDescription = "অ্যাডমিন"
                                    )
                                },
                                label = { Text("অ্যাডমিন", maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_members")
                            )
                        }
                        NavigationBarItem(
                            selected = currentTab == SmartChatTab.PROFILE,
                            onClick = {
                                viewModel.openDirectMessage(null)
                                currentTab = SmartChatTab.PROFILE
                            },
                            icon = { Icon(Icons.Default.Person, contentDescription = "প্রোফাইল") },
                            label = { Text("প্রোফাইল", maxLines = 1) },
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
                            selected = currentTab == SmartChatTab.CHAT && activeDmPeer == null,
                            onClick = {
                                viewModel.openDirectMessage(null)
                                currentTab = SmartChatTab.CHAT
                            },
                            icon = { Icon(Icons.Default.Chat, contentDescription = "গ্রুপ") },
                            label = { Text("গ্রুপ") }
                        )
                        NavigationRailItem(
                            selected = currentTab == SmartChatTab.INBOX || activeDmPeer != null,
                            onClick = {
                                viewModel.openDirectMessage(null)
                                currentTab = SmartChatTab.INBOX
                            },
                            icon = { Icon(Icons.Default.Forum, contentDescription = "ইনবক্স") },
                            label = { Text("ইনবক্স") }
                        )
                        if (viewModel.isSuperAdmin) {
                            NavigationRailItem(
                                selected = currentTab == SmartChatTab.MEMBERS,
                                onClick = {
                                    viewModel.openDirectMessage(null)
                                    currentTab = SmartChatTab.MEMBERS
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.AdminPanelSettings,
                                        contentDescription = "অ্যাডমিন"
                                    )
                                },
                                label = { Text("অ্যাডমিন") }
                            )
                        }
                        NavigationRailItem(
                            selected = currentTab == SmartChatTab.PROFILE,
                            onClick = {
                                viewModel.openDirectMessage(null)
                                currentTab = SmartChatTab.PROFILE
                            },
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
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color = DangerRed.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DangerRed)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = "Blocked",
                                    tint = DangerRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "আপনি ব্লকড",
                                    color = DangerRed,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (statusBanner != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusBanner!!,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.clearBanner() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        modifier = Modifier.size(14.dp)
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
                            groupSettings = groupSettings,
                            activeDmPeer = activeDmPeer,
                            members = members,
                            messagesState = messagesState,
                            replyingTo = replyingTo,
                            editingMessage = editingMessage,
                            playingMessageId = playingMessageId,
                            hasMicPermission = hasMicPermission,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            audioHelper = audioHelper,
                            onSendText = { text, isNotice ->
                                viewModel.sendTextMessage(
                                    text = text,
                                    isAdminNotice = isNotice,
                                    dmTargetUserId = activeDmPeer?.userId
                                )
                            },
                            onSendVoice = { base64, durationSec ->
                                viewModel.sendVoiceMessage(
                                    audioBase64 = base64,
                                    durationSec = durationSec,
                                    dmTargetUserId = activeDmPeer?.userId
                                )
                            },
                            onSelectReply = { viewModel.setReplyTarget(it) },
                            onStartEdit = { viewModel.startEditingMessage(it) },
                            onToggleReaction = { msg, emoji ->
                                viewModel.toggleReaction(msg, emoji)
                            },
                            onDeleteMessage = { viewModel.deleteMessage(it) },
                            onSetPlayingId = { viewModel.setPlayingMessageId(it) },
                            onOpenUserDm = { peer ->
                                viewModel.openDirectMessage(peer)
                                currentTab = SmartChatTab.CHAT
                            },
                            onCloseDm = {
                                viewModel.openDirectMessage(null)
                            },
                            onTypingChanged = { roomKey ->
                                viewModel.notifyTyping(roomKey)
                            }
                        )

                        SmartChatTab.INBOX -> InboxAndSearchSection(
                            currentUserId = viewModel.currentUserId,
                            groupSettings = groupSettings,
                            membersState = membersState,
                            messagesState = messagesState,
                            onOpenMainGroup = {
                                viewModel.openDirectMessage(null)
                                currentTab = SmartChatTab.CHAT
                            },
                            onOpenUserDm = { peer ->
                                viewModel.openDirectMessage(peer)
                                currentTab = SmartChatTab.CHAT
                            }
                        )

                        SmartChatTab.MEMBERS -> MembersAndAdminSection(
                            currentUserId = viewModel.currentUserId,
                            isSuperAdmin = viewModel.isSuperAdmin,
                            groupSettings = groupSettings,
                            membersState = membersState,
                            onUpdateGroupSettings = { name, isOff ->
                                viewModel.updateAdminGroupSettings(name, isOff)
                            },
                            onToggleBlockUser = { viewModel.toggleBlockUser(it) },
                            onOpenUserDm = { peer ->
                                viewModel.openDirectMessage(peer)
                                currentTab = SmartChatTab.CHAT
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
                            onSignOut = {
                                viewModel.setOnlineState(false)
                                onSignOut()
                            }
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
    groupSettings: GroupSettings,
    onlineCount: Int,
    isSuperAdmin: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    val vipInfo = remember(currentUserProfile, isSuperAdmin) {
        (currentUserProfile ?: UserProfile()).computeVipInfo(isSuperAdmin)
    }

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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        sizeDp = 38,
                        isOnline = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = groupSettings.groupName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSuperAdmin) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AdminGold.copy(alpha = 0.2f))
                                        .border(1.dp, AdminGold, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "ADMIN",
                                        color = AdminGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentUserProfile?.username ?: "...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            VipBadgeChip(vipInfo = vipInfo)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Online $onlineCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = OnlineGreen,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Uniform 36.dp Top Bar Icon Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Theme",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        }
    }
}
