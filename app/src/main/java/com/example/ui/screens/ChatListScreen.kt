package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.repository.TelegramRepository
import com.example.tdlib.ChatCategory
import com.example.tdlib.TelegramChatModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    repository: TelegramRepository,
    onChatClick: (TelegramChatModel) -> Unit,
    onCreateNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chats by repository.chats.collectAsState()
    val connectionStatus by repository.connectionStatus.collectAsState()

    var selectedCategory by remember { mutableStateOf(ChatCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isEditMode by remember { mutableStateOf(false) }
    val selectedChatIds = remember { mutableStateListOf<Long>() }

    val globalSearchHits = remember(searchQuery, chats) {
        if (searchQuery.isBlank()) emptyList() else repository.searchGlobal(searchQuery)
    }

    val filteredChats = remember(chats, selectedCategory) {
        chats.filter { chat ->
            when (selectedCategory) {
                ChatCategory.ALL -> true
                ChatCategory.PERSONAL -> chat.type == "private"
                ChatCategory.CHANNELS -> chat.type == "channel"
                ChatCategory.GROUPS -> chat.type == "group" || chat.type == "supergroup"
                ChatCategory.BOTS -> chat.type == "bot"
                ChatCategory.UNREAD -> chat.unreadCount > 0
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Telegram iOS Centered Header
        CenterAlignedTopAppBar(
            navigationIcon = {
                TextButton(
                    onClick = {
                        isEditMode = !isEditMode
                        if (!isEditMode) selectedChatIds.clear()
                    },
                    modifier = Modifier.testTag("chats_edit_button")
                ) {
                    Text(
                        text = if (isEditMode) "Done" else "Edit",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isEditMode && selectedChatIds.isNotEmpty()) "${selectedChatIds.size} Selected" else "Chats",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TelegramOnlineGreen)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = connectionStatus,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            actions = {
                if (isEditMode && selectedChatIds.isNotEmpty()) {
                    IconButton(onClick = {
                        selectedChatIds.forEach { repository.pinChat(it) }
                        isEditMode = false
                        selectedChatIds.clear()
                    }) {
                        Icon(Icons.Default.PushPin, contentDescription = "Pin selected", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        selectedChatIds.forEach { repository.deleteChat(it) }
                        isEditMode = false
                        selectedChatIds.clear()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(
                        onClick = onCreateNewChat,
                        modifier = Modifier.testTag("new_chat_compose_btn")
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "New Message or Group",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // iOS Rounded Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search for messages or users",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_search_input")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Folder Pills Row (iOS Telegram style)
        if (searchQuery.isBlank()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ChatCategory.values()) { category ->
                    val selected = selectedCategory == category
                    val unreadCount = if (category == ChatCategory.UNREAD) chats.count { it.unreadCount > 0 } else 0

                    Surface(
                        onClick = { selectedCategory = category },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (unreadCount > 0) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            thickness = 0.5.dp
        )

        // Content: Global Search Results OR Chat List
        if (searchQuery.isNotBlank()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (globalSearchHits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results for \"$searchQuery\"",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    items(globalSearchHits) { hit ->
                        val previewSubtitle = hit.matchedMessage?.let {
                            "${it.senderName}: ${it.text}"
                        } ?: (hit.chat.username ?: hit.chat.lastMessageText)
                        val chatForRow = hit.chat.copy(
                            lastMessageText = previewSubtitle,
                            lastMessageTime = hit.matchedMessage?.timestamp ?: hit.chat.lastMessageTime
                        )
                        IosChatRow(
                            chat = chatForRow,
                            isEditMode = false,
                            isSelected = false,
                            onClick = { onChatClick(hit.chat) },
                            onLongClick = {}
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 84.dp)
                        )
                    }
                }
            }
        } else if (filteredChats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No chats in ${selectedCategory.label}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredChats, key = { it.id }) { chat ->
                    val isSelected = selectedChatIds.contains(chat.id)
                    IosChatRow(
                        chat = chat,
                        isEditMode = isEditMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isEditMode) {
                                if (isSelected) selectedChatIds.remove(chat.id)
                                else selectedChatIds.add(chat.id)
                            } else {
                                onChatClick(chat)
                            }
                        },
                        onLongClick = {
                            if (!isEditMode) {
                                isEditMode = true
                                selectedChatIds.add(chat.id)
                            }
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 84.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosChatRow(
    chat: TelegramChatModel,
    isEditMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val avatarGradient = remember(chat.id) {
        val palettes = listOf(
            listOf(Color(0xFF64D2FF), Color(0xFF007AFF)),
            listOf(Color(0xFFFF6482), Color(0xFFFF3B30)),
            listOf(Color(0xFFBF5AF2), Color(0xFF5E5CE6)),
            listOf(Color(0xFF30D158), Color(0xFF00C7BE)),
            listOf(Color(0xFFFFB340), Color(0xFFFF9500))
        )
        palettes[(kotlin.math.abs(chat.id) % palettes.size).toInt()]
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag("chat_item_${chat.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = isEditMode) {
            Row {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Select chat",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
        }

        // 56.dp iOS Avatar
        Box(modifier = Modifier.size(56.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(avatarGradient)),
                contentAlignment = Alignment.Center
            ) {
                if (chat.title == "Saved Messages") {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Text(
                        text = chat.title.take(1).uppercase(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(TelegramOnlineGreen)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (chat.type == "channel") {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).padding(end = 3.dp)
                        )
                    } else if (chat.type == "group" || chat.type == "supergroup") {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).padding(end = 3.dp)
                        )
                    }
                    Text(
                        text = chat.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (chat.isVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.isMuted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Text(
                    text = chat.lastMessageTime,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessageText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chat.isPinned && chat.unreadCount == 0) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (chat.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (chat.isMuted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.primary
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.unreadCount.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
