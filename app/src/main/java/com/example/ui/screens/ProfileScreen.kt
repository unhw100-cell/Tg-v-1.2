package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.TelegramRepository
import com.example.tdlib.MediaType
import com.example.tdlib.TelegramChatModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: TelegramRepository,
    chat: TelegramChatModel,
    onBack: () -> Unit,
    onStartCall: (Boolean) -> Unit
) {
    val chats by repository.chats.collectAsState()
    val currentChat = chats.find { it.id == chat.id } ?: chat
    val messagesMap by repository.messages.collectAsState()
    val chatMessages = messagesMap[chat.id].orEmpty()
    val contacts by repository.contacts.collectAsState()

    var selectedSegment by remember { mutableIntStateOf(0) }
    val segments = remember(currentChat.type) {
        if (currentChat.type == "group" || currentChat.type == "supergroup") {
            listOf("Members", "Media", "Files", "Links")
        } else {
            listOf("Media", "Files", "Voice", "Links")
        }
    }

    val avatarGradient = remember(currentChat.id) {
        val pairs = listOf(
            listOf(Color(0xFF5AC8FA), Color(0xFF007AFF)),
            listOf(Color(0xFFAF52DE), Color(0xFF5856D6)),
            listOf(Color(0xFFFF9500), Color(0xFFFF3B30)),
            listOf(Color(0xFF34C759), Color(0xFF00C7BE))
        )
        pairs[(kotlin.math.abs(currentChat.id) % pairs.size).toInt()]
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("profile_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Back",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                title = {},
                actions = {
                    TextButton(onClick = { repository.pinChat(currentChat.id) }) {
                        Text(
                            if (currentChat.isPinned) "Unpin" else "Pin",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 16.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Avatar + Title Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(avatarGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentChat.title == "Saved Messages") {
                            Icon(
                                Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            Text(
                                text = currentChat.title.take(1).uppercase(),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentChat.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (currentChat.isVerified) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = when (currentChat.type) {
                            "channel" -> "${currentChat.memberCount.coerceAtLeast(1)} subscribers"
                            "supergroup", "group" -> "${currentChat.memberCount.coerceAtLeast(1)} members"
                            "bot" -> "bot"
                            else -> if (currentChat.isOnline) "online" else "last seen recently"
                        },
                        fontSize = 14.sp,
                        color = if (currentChat.isOnline) TelegramOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))

                    // iOS Action Pills Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IosProfileActionCard(
                            icon = Icons.Default.Call,
                            label = "Call",
                            modifier = Modifier.weight(1f),
                            onClick = { onStartCall(false) }
                        )
                        IosProfileActionCard(
                            icon = Icons.Default.Videocam,
                            label = "Video",
                            modifier = Modifier.weight(1f),
                            onClick = { onStartCall(true) }
                        )
                        IosProfileActionCard(
                            icon = if (currentChat.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            label = if (currentChat.isMuted) "Unmute" else "Mute",
                            modifier = Modifier.weight(1f),
                            onClick = { repository.toggleMuteChat(currentChat.id) }
                        )
                        IosProfileActionCard(
                            icon = Icons.Default.Message,
                            label = "Message",
                            modifier = Modifier.weight(1f),
                            onClick = onBack
                        )
                    }
                }
            }

            // Grouped iOS Info Card
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (!currentChat.phoneNumber.isNullOrBlank()) {
                            ProfileInfoRow(label = "mobile", value = currentChat.phoneNumber!!)
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                        if (!currentChat.username.isNullOrBlank()) {
                            ProfileInfoRow(
                                label = "username",
                                value = currentChat.username!!,
                                isAccent = true
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                        ProfileInfoRow(
                            label = "bio",
                            value = currentChat.bio.ifBlank { "RustGram encrypted Telegram peer" }
                        )
                    }
                }
            }

            // Shared Content Segmented Tabs
            item {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    segments.forEachIndexed { idx, title ->
                        val selected = selectedSegment == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedSegment = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Tab Content
            val activeLabel = segments[selectedSegment]
            if (activeLabel == "Members") {
                items(contacts) { member ->
                    Card(
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    member.name.take(1),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (member.isOnline) "online" else member.lastSeen,
                                    fontSize = 12.sp,
                                    color = if (member.isOnline) TelegramOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                val filtered = chatMessages.filter { msg ->
                    when (activeLabel) {
                        "Media" -> msg.mediaType == MediaType.PHOTO || msg.mediaType == MediaType.VIDEO || msg.mediaType == MediaType.VIDEO_NOTE
                        "Files" -> msg.mediaType == MediaType.DOCUMENT
                        "Voice" -> msg.mediaType == MediaType.VOICE
                        else -> msg.text.contains("http") || msg.text.contains("@")
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No shared ${activeLabel.lowercase()} yet",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (item.mediaType) {
                                        MediaType.PHOTO -> Icons.Default.Image
                                        MediaType.VOICE -> Icons.Default.Mic
                                        MediaType.DOCUMENT -> Icons.Default.InsertDriveFile
                                        else -> Icons.Default.Link
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.fileName ?: item.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${item.senderName} • ${item.timestamp}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun IosProfileActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(
    label: String,
    value: String,
    isAccent: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = if (isAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
