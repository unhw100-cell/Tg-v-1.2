package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Message
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
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassSectionDivider
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.TelegramOnlineGreen
import com.example.ui.theme.TelegramSecretGreen

@Composable
fun ProfileScreen(
    repository: TelegramRepository,
    chat: TelegramChatModel,
    onBack: () -> Unit,
    onStartCall: (Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    val chats by repository.chats.collectAsState()
    val currentChat = chats.find { it.id == chat.id } ?: chat
    val messagesMap by repository.messages.collectAsState()
    val chatMessages = messagesMap[chat.id].orEmpty()
    val contacts by repository.contacts.collectAsState()

    var selectedSegment by remember { mutableIntStateOf(0) }
    val segments = remember(currentChat.type, strings.langCode) {
        if (currentChat.type == "group" || currentChat.type == "supergroup") {
            listOf(strings.sharedMembers, strings.sharedMedia, strings.sharedFiles, strings.sharedLinks)
        } else {
            listOf(strings.sharedMedia, strings.sharedFiles, strings.sharedVoice, strings.sharedLinks)
        }
    }

    val avatarGradient = remember(currentChat.id, currentChat.isSecret) {
        if (currentChat.isSecret) {
            listOf(Color(0xFF30D158), Color(0xFF0B8A42))
        } else {
            val pairs = listOf(
                listOf(Color(0xFF5AC8FA), Color(0xFF007AFF)),
                listOf(Color(0xFFAF52DE), Color(0xFF5856D6)),
                listOf(Color(0xFFFF9500), Color(0xFFFF3B30)),
                listOf(Color(0xFF34C759), Color(0xFF00C7BE))
            )
            pairs[(kotlin.math.abs(currentChat.id) % pairs.size).toInt()]
        }
    }

    LiquidGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Floating Liquid Glass Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("profile_back_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.back,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        strings.back,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { repository.toggleArchiveChat(currentChat.id) }) {
                        Text(
                            if (currentChat.isArchived) strings.unarchive else strings.archive,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                    TextButton(onClick = { repository.pinChat(currentChat.id) }) {
                        Text(
                            if (currentChat.isPinned) strings.unpin else strings.pin,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
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
                                .background(Brush.verticalGradient(avatarGradient))
                                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentChat.title == "Saved Messages") {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
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
                            if (currentChat.isSecret) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = TelegramSecretGreen, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = currentChat.title,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (currentChat.isSecret) TelegramSecretGreen else MaterialTheme.colorScheme.onBackground
                            )
                            if (currentChat.isVerified) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = when (currentChat.type) {
                                "channel" -> "${currentChat.memberCount.coerceAtLeast(1)} ${strings.subscribersSuffix}"
                                "supergroup", "group" -> "${currentChat.memberCount.coerceAtLeast(1)} ${strings.membersSuffix}"
                                "bot" -> "bot"
                                "secret" -> strings.secretChatBanner
                                else -> if (currentChat.isOnline) strings.onlineStatus else strings.lastSeenRecently
                            },
                            fontSize = 14.sp,
                            color = when {
                                currentChat.isSecret -> TelegramSecretGreen
                                currentChat.isOnline -> TelegramOnlineGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IosProfileActionCard(
                                icon = Icons.Default.Call,
                                label = strings.callAction,
                                modifier = Modifier.weight(1f),
                                onClick = { onStartCall(false) }
                            )
                            IosProfileActionCard(
                                icon = Icons.Default.Videocam,
                                label = strings.videoAction,
                                modifier = Modifier.weight(1f),
                                onClick = { onStartCall(true) }
                            )
                            IosProfileActionCard(
                                icon = if (currentChat.isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                label = if (currentChat.isMuted) strings.unmute else strings.mute,
                                modifier = Modifier.weight(1f),
                                onClick = { repository.toggleMuteChat(currentChat.id) }
                            )
                            IosProfileActionCard(
                                icon = Icons.AutoMirrored.Filled.Message,
                                label = strings.messageAction,
                                modifier = Modifier.weight(1f),
                                onClick = onBack
                            )
                        }
                    }
                }

                // Secret Chat Encryption Fingerprint Card
                if (currentChat.isSecret) {
                    item {
                        Spacer(Modifier.height(14.dp))
                        LiquidGlassCard(
                            accentTint = TelegramSecretGreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = strings.secretChatBanner,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TelegramSecretGreen
                                    )
                                    Text(
                                        text = "${strings.selfDestructTimer}: ${if (currentChat.selfDestructSec > 0) "${currentChat.selfDestructSec}s" else strings.timerOff}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = currentChat.encryptionFingerprint,
                                    fontSize = 20.sp,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    }
                }

                // Grouped Glass Info Card
                item {
                    Spacer(Modifier.height(14.dp))
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!currentChat.phoneNumber.isNullOrBlank()) {
                                ProfileInfoRow(label = "mobile", value = currentChat.phoneNumber!!)
                                LiquidGlassSectionDivider()
                            }
                            if (!currentChat.username.isNullOrBlank()) {
                                ProfileInfoRow(
                                    label = "username",
                                    value = currentChat.username!!,
                                    isAccent = true
                                )
                                LiquidGlassSectionDivider()
                            }
                            ProfileInfoRow(
                                label = strings.bioLabel,
                                value = currentChat.bio.ifBlank { "RustGram encrypted Telegram peer" }
                            )
                        }
                    }
                }

                // Shared Content Segmented Tabs
                item {
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .liquidGlass(shape = RoundedCornerShape(50), strong = false)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        segments.forEachIndexed { idx, title ->
                            val selected = selectedSegment == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable { selectedSegment = idx }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                val activeLabel = segments[selectedSegment]
                if (activeLabel == strings.sharedMembers) {
                    items(contacts) { member ->
                        LiquidGlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(member.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(
                                        if (member.isOnline) strings.onlineStatus else member.lastSeen,
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
                            strings.sharedMedia -> msg.mediaType == MediaType.PHOTO || msg.mediaType == MediaType.VIDEO || msg.mediaType == MediaType.VIDEO_NOTE
                            strings.sharedFiles -> msg.mediaType == MediaType.DOCUMENT
                            strings.sharedVoice -> msg.mediaType == MediaType.VOICE
                            else -> msg.text.contains("http") || msg.text.contains("@")
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            LiquidGlassCard(
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
                                        text = "${strings.noSharedPrefix} ${activeLabel.lowercase()}",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered) { item ->
                            LiquidGlassCard(
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
                                            MediaType.DOCUMENT -> Icons.AutoMirrored.Filled.InsertDriveFile
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
}

@Composable
private fun IosProfileActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(66.dp)
            .liquidGlass(shape = RoundedCornerShape(16.dp), strong = true)
            .clickable(onClick = onClick),
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
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            color = if (isAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
