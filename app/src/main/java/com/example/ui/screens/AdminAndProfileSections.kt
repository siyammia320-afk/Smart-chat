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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.GroupSettings
import com.example.data.model.UserProfile
import com.example.data.model.buildDmRoomKey
import com.example.ui.auth.PRESET_AVATARS
import com.example.ui.auth.UserAvatarView
import com.example.ui.theme.AdminGold
import com.example.ui.theme.DangerRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.viewmodel.UiState
import com.example.util.AudioHelper
import kotlinx.coroutines.delay

@Composable
fun InboxAndSearchSection(
    currentUserId: String,
    groupSettings: GroupSettings,
    membersState: UiState<List<UserProfile>>,
    messagesState: UiState<List<ChatMessage>>,
    onOpenMainGroup: () -> Unit,
    onOpenUserDm: (UserProfile) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var nowEpochSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000L)
            nowEpochSec = System.currentTimeMillis() / 1000L
        }
    }

    val members = (membersState as? UiState.Success)?.data ?: emptyList()
    val allMessages = (messagesState as? UiState.Success)?.data ?: emptyList()
    val memberMap = remember(members) { members.associateBy { it.userId } }

    // Find users who have active 1-to-1 DM history with currentUserId
    val dmConversations = remember(allMessages, currentUserId, memberMap) {
        val latestByPeer = LinkedHashMap<String, ChatMessage>()
        allMessages.filter { it.isDirectMessage() }.forEach { msg ->
            val otherId = msg.otherDmParticipantId(currentUserId)
            if (!otherId.isNullOrBlank() && otherId != currentUserId) {
                latestByPeer[otherId] = msg
            }
        }
        latestByPeer.entries
            .sortedByDescending { it.value.createdAt?.seconds ?: 0L }
            .map { (peerId, lastMsg) ->
                val profile = memberMap[peerId] ?: UserProfile(
                    id = peerId,
                    userId = peerId,
                    username = if (lastMsg.senderId == peerId) lastMsg.senderName else "User",
                    avatarData = if (lastMsg.senderId == peerId) lastMsg.senderAvatar else "avatar_indigo",
                    isOnline = false
                )
                Pair(profile, lastMsg)
            }
    }

    // Search results ONLY when user types in the search box
    val searchResults = remember(searchQuery, members, currentUserId) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            members.filter {
                it.userId != currentUserId &&
                    it.username.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Search Bar by Username
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "ইউজারনেম দিয়ে সার্চ করুন...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inbox_username_search_input")
                )
            }
            if (searchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { searchQuery = "" },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Admin's Official Group is always joined for all users
            item(key = "official_admin_group") {
                val onlineCount = members.count { it.isOnline }
                val groupTypingUsers = members.filter {
                    it.userId != currentUserId && it.isTypingInRoom("group", nowEpochSec)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenMainGroup() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = "Group",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = groupSettings.groupName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (groupSettings.isGroupOff) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Off",
                                            tint = AdminGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (groupTypingUsers.isNotEmpty()) {
                                        "${groupTypingUsers.first().username} typing..."
                                    } else {
                                        "Online: $onlineCount"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (groupTypingUsers.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        OnlineGreen
                                    }
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "গ্রুপ চ্যাট",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // If user typed a username in the Search Bar, show matching users
            if (searchQuery.trim().isNotEmpty()) {
                items(searchResults, key = { "search_${it.userId}" }) { user ->
                    val vip = user.computeVipInfo()
                    val dmRoom = buildDmRoomKey(currentUserId, user.userId)
                    val isTyping = user.isTypingInRoom(dmRoom, nowEpochSec)

                    UserInboxRowCard(
                        user = user,
                        vipInfo = vip,
                        subtitle = when {
                            isTyping -> "Typing..."
                            user.isOnline -> "Online"
                            else -> "Offline"
                        },
                        subtitleColor = when {
                            isTyping -> MaterialTheme.colorScheme.primary
                            user.isOnline -> OnlineGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = { onOpenUserDm(user) }
                    )
                }
            } else {
                // Otherwise show ONLY existing 1-to-1 DM conversations (nobody pre-added!)
                items(dmConversations, key = { "dm_${it.first.userId}" }) { (peer, lastMsg) ->
                    val vip = peer.computeVipInfo()
                    val dmRoom = buildDmRoomKey(currentUserId, peer.userId)
                    val isTyping = peer.isTypingInRoom(dmRoom, nowEpochSec)

                    UserInboxRowCard(
                        user = peer,
                        vipInfo = vip,
                        subtitle = when {
                            isTyping -> "Typing..."
                            lastMsg.type == "voice" -> "🎤 ভয়েস মেসেজ"
                            else -> lastMsg.text
                        },
                        subtitleColor = if (isTyping) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = { onOpenUserDm(peer) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserInboxRowCard(
    user: UserProfile,
    vipInfo: com.example.data.model.VipInfo,
    subtitle: String,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                UserAvatarView(
                    avatarData = user.avatarData,
                    username = user.username,
                    sizeDp = 40,
                    isOnline = user.isOnline
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        VipBadgeChip(vipInfo = vipInfo)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "মেসেজ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MembersAndAdminSection(
    currentUserId: String,
    isSuperAdmin: Boolean,
    groupSettings: GroupSettings,
    membersState: UiState<List<UserProfile>>,
    onUpdateGroupSettings: (String, Boolean) -> Unit,
    onToggleBlockUser: (UserProfile) -> Unit,
    onOpenUserDm: (UserProfile) -> Unit,
    onClearExpiredMessages: (Boolean) -> Unit
) {
    val members = (membersState as? UiState.Success)?.data ?: emptyList()
    var groupNameInput by remember(groupSettings.groupName) {
        mutableStateOf(groupSettings.groupName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (isSuperAdmin) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, AdminGold)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Group Name Input + Save Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = groupNameInput,
                                onValueChange = { groupNameInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    onUpdateGroupSettings(groupNameInput, groupSettings.isGroupOff)
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save name",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "নাম সেভ",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Uniform 38.dp Action Buttons Row: Group ON/OFF + Clear All SMS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (groupSettings.isGroupOff) DangerRed
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    if (groupSettings.isGroupOff) DangerRed else AdminGold,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onUpdateGroupSettings(groupNameInput, !groupSettings.isGroupOff)
                                }
                                .testTag("admin_toggle_group_lock_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (groupSettings.isGroupOff) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = "Toggle group",
                                    tint = if (groupSettings.isGroupOff) Color.White else AdminGold,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (groupSettings.isGroupOff) "গ্রুপ অন করুন" else "গ্রুপ অফ করুন",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (groupSettings.isGroupOff) Color.White else AdminGold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DangerRed)
                                .clickable { onClearExpiredMessages(true) }
                                .testTag("admin_clear_all_messages_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Clear all",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "সব মেসেজ ডিলিট",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("members_list"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(members, key = { it.userId.ifBlank { it.id } }) { member ->
                val vip = member.computeVipInfo()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = member.userId != currentUserId) {
                            onOpenUserDm(member)
                        },
                    shape = RoundedCornerShape(10.dp),
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
                            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                sizeDp = 38,
                                isOnline = member.isOnline
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = member.username,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    VipBadgeChip(vipInfo = vip)
                                }
                                Text(
                                    text = when {
                                        member.isBlocked -> "Blocked"
                                        member.isOnline -> "Online"
                                        else -> "Offline"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        member.isBlocked -> DangerRed
                                        member.isOnline -> OnlineGreen
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        if (isSuperAdmin && member.userId != currentUserId) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (member.isBlocked) MaterialTheme.colorScheme.primary
                                        else DangerRed
                                    )
                                    .clickable { onToggleBlockUser(member) }
                                    .padding(horizontal = 10.dp)
                                    .testTag("block_toggle_${member.userId}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (member.isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (member.isBlocked) "Unblock" else "Block",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
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
    var avatarData by remember(currentUserProfile?.avatarData) {
        mutableStateOf(currentUserProfile?.avatarData ?: "avatar_emerald")
    }
    val autoDeleteHours = currentUserProfile?.autoDeleteHours ?: 24L

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = AudioHelper.compressGalleryImageToBase64(context, uri)
            if (encoded != null) {
                avatarData = encoded
            }
        }
    }

    val vipInfo = remember(currentUserProfile, isSuperAdmin) {
        (currentUserProfile ?: UserProfile()).computeVipInfo(isSuperAdmin)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatarView(
                    avatarData = avatarData,
                    username = username.ifBlank { "S" },
                    sizeDp = 76,
                    isOnline = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // VIP Rank & Level Card in Profile
                val vipColor = Color(vipInfo.colorHex)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = vipColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, vipColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "VIP",
                            tint = vipColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${vipInfo.title} • Level ${vipInfo.level} (${vipInfo.daysOld}d)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = vipColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gallery Photo Button (Uniform 38.dp)
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(horizontal = 14.dp)
                        .testTag("profile_pick_photo_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ছবি পরিবর্তন",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Preset Avatar Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PRESET_AVATARS.forEach { (presetId, presetColor) ->
                        val selected = avatarData == presetId
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { avatarData = presetId }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Username Input Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (username.isEmpty()) {
                        Text(
                            text = "ইউজারনেম",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_username_input")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Save Profile Button (Uniform 42.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            onUpdateProfile(username, avatarData, "Online", autoDeleteHours)
                        }
                        .testTag("save_profile_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "সেভ করুন",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(10.dp))

                // Sign Out Button (Uniform 42.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DangerRed.copy(alpha = 0.14f))
                        .border(1.dp, DangerRed, RoundedCornerShape(10.dp))
                        .clickable { onSignOut() }
                        .testTag("profile_sign_out_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Logout",
                            tint = DangerRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "লগআউট",
                            color = DangerRed,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
