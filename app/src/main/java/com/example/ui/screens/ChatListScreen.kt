package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
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
import com.example.tdlib.StoryItem
import com.example.tdlib.TelegramChatModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassCapsuleButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*

@Composable
fun ChatListScreen(
    repository: TelegramRepository,
    onChatClick: (TelegramChatModel) -> Unit,
    onCreateNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val chats by repository.chats.collectAsState()
    val stories by repository.stories.collectAsState()
    val messages by repository.messages.collectAsState()
    val connectionStatus by repository.connectionStatus.collectAsState()
    val connectionIndicatorColor = when {
        connectionStatus.contains("ready", ignoreCase = true) -> Color(0xFF30D158)
        connectionStatus.contains("connect", ignoreCase = true) ||
            connectionStatus.contains("updat", ignoreCase = true) ||
            connectionStatus.contains("waiting", ignoreCase = true) -> Color(0xFFFF9F0A)
        else -> MaterialTheme.colorScheme.error
    }
    val publicSearchChats by repository.publicSearchChats.collectAsState()

    var selectedCategory by remember { mutableStateOf(ChatCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isEditMode by remember { mutableStateOf(false) }
    var showArchivedView by remember { mutableStateOf(false) }
    val selectedChatIds = remember { mutableStateListOf<Long>() }
    var activeStory by remember { mutableStateOf<StoryItem?>(null) }
    var showAddStoryDialog by remember { mutableStateOf(false) }

    val globalSearchHits = remember(searchQuery, chats, messages, publicSearchChats) {
        if (searchQuery.isBlank()) emptyList() else repository.searchGlobal(searchQuery, publicSearchChats)
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            repository.searchPublicChats("")
        } else {
            delay(350)
            repository.searchPublicChats(searchQuery)
        }
    }

    val archivedCount = remember(chats) { chats.count { it.isArchived } }

    val filteredChats = remember(chats, selectedCategory, showArchivedView) {
        val pool = if (showArchivedView) chats.filter { it.isArchived } else chats.filter { !it.isArchived }
        if (showArchivedView) {
            pool
        } else {
            pool.filter { chat ->
                when (selectedCategory) {
                    ChatCategory.ALL -> true
                    ChatCategory.PERSONAL -> chat.type == "private" && !chat.isSecret
                    ChatCategory.CHANNELS -> chat.type == "channel"
                    ChatCategory.GROUPS -> chat.type == "group" || chat.type == "supergroup"
                    ChatCategory.BOTS -> chat.type == "bot"
                    ChatCategory.UNREAD -> chat.unreadCount > 0
                    ChatCategory.SECRET -> chat.isSecret || chat.type == "secret"
                }
            }
        }
    }

    LiquidGlassBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Floating iPhone Liquid Glass Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showArchivedView) {
                    IconButton(
                        onClick = { showArchivedView = false },
                        modifier = Modifier.testTag("archive_back_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    TextButton(
                        onClick = {
                            isEditMode = !isEditMode
                            if (!isEditMode) selectedChatIds.clear()
                        },
                        modifier = Modifier.testTag("chats_edit_button")
                    ) {
                        Text(
                            text = if (isEditMode) strings.done else strings.edit,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            isEditMode && selectedChatIds.isNotEmpty() -> "${selectedChatIds.size} ${strings.selectedCountSuffix}"
                            showArchivedView -> strings.archivedChats
                            else -> strings.tabChats
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(connectionIndicatorColor)
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

                if (isEditMode && selectedChatIds.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            selectedChatIds.forEach { repository.pinChat(it) }
                            isEditMode = false
                            selectedChatIds.clear()
                        }) {
                            Icon(Icons.Default.PushPin, contentDescription = strings.pin, tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            selectedChatIds.forEach { repository.toggleArchiveChat(it) }
                            isEditMode = false
                            selectedChatIds.clear()
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = strings.archive, tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            selectedChatIds.forEach { repository.deleteChat(it) }
                            isEditMode = false
                            selectedChatIds.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = TelegramIosRed)
                        }
                    }
                } else {
                    IconButton(
                        onClick = onCreateNewChat,
                        modifier = Modifier.testTag("new_chat_compose_btn")
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = strings.newMessageTitle,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Floating Liquid Glass Search Capsule
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .height(42.dp)
                    .liquidGlass(shape = RoundedCornerShape(50), strong = false, elevation = 3.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = strings.searchChatsHint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                            contentDescription = strings.close,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Stories Carousel + Folder Pills (when not searching and not inside Archive)
            if (searchQuery.isBlank() && !showArchivedView) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Add Own Story Pill
                    item {
                        StoryBubbleItem(
                            title = strings.myStory,
                            initials = "+",
                            isOwnAdd = true,
                            isViewed = false,
                            avatarColorIndex = 0,
                            onClick = { showAddStoryDialog = true }
                        )
                    }
                    items(stories, key = { it.id }) { story ->
                        StoryBubbleItem(
                            title = story.authorName.substringBefore(" "),
                            initials = story.authorName.take(1).uppercase(),
                            isOwnAdd = false,
                            isViewed = story.isViewed,
                            avatarColorIndex = story.avatarColorIndex,
                            onClick = { activeStory = story }
                        )
                    }
                }

                // Folder Pills Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ChatCategory.entries) { category ->
                        val selected = selectedCategory == category
                        val unreadCount = if (category == ChatCategory.UNREAD) chats.count { !it.isArchived && it.unreadCount > 0 } else 0

                        LiquidGlassCapsuleButton(
                            onClick = { selectedCategory = category },
                            selected = selected
                        ) {
                            Text(
                                text = strings.categoryName(category),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (unreadCount > 0) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Content: Global Search Results OR Chat List
            if (searchQuery.isNotBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (globalSearchHits.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${strings.noResultsFor} \"$searchQuery\"",
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
                            LiquidGlassCard(strong = false) {
                                IosChatRow(
                                    chat = chatForRow,
                                    isEditMode = false,
                                    isSelected = false,
                                    onClick = { onChatClick(hit.chat) },
                                    onLongClick = {}
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Collapsible Archived Chats Banner
                    if (!showArchivedView && archivedCount > 0 && selectedCategory == ChatCategory.ALL) {
                        item {
                            LiquidGlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showArchivedView = true }
                                    .testTag("archived_chats_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Archive,
                                            contentDescription = strings.archivedChats,
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = strings.archivedChats,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = archivedCount.toString(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredChats.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
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
                                        text = strings.noChatsInFolder,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredChats, key = { it.id }) { chat ->
                            val isSelected = selectedChatIds.contains(chat.id)
                            LiquidGlassCard(
                                strong = chat.isPinned || isSelected,
                                accentTint = if (chat.isSecret) TelegramSecretGreen else null
                            ) {
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
                            }
                        }
                    }
                }
            }
        }
    }

    activeStory?.let { story ->
        StoryViewerModal(
            story = story,
            onViewed = { repository.markStoryViewed(it) },
            onDismiss = { activeStory = null }
        )
    }

    if (showAddStoryDialog) {
        AddStoryDialog(
            repository = repository,
            onDismiss = { showAddStoryDialog = false }
        )
    }
}

@Composable
private fun StoryBubbleItem(
    title: String,
    initials: String,
    isOwnAdd: Boolean,
    isViewed: Boolean,
    avatarColorIndex: Int,
    onClick: () -> Unit
) {
    val palettes = listOf(
        listOf(Color(0xFF64D2FF), Color(0xFF007AFF)),
        listOf(Color(0xFFFF6482), Color(0xFFFF3B30)),
        listOf(Color(0xFFBF5AF2), Color(0xFF5E5CE6)),
        listOf(Color(0xFF30D158), Color(0xFF00C7BE))
    )
    val grad = palettes[avatarColorIndex.coerceIn(0, palettes.lastIndex)]
    val ringBrush = when {
        isOwnAdd -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, TelegramIosMint))
        isViewed -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
        else -> Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFAF52DE)))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(62.dp)
            .clickable(onClick = onClick)
            .testTag(if (isOwnAdd) "add_story_btn" else "story_bubble_$title")
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(2.2.dp, ringBrush, CircleShape)
                .padding(3.5.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(grad)),
            contentAlignment = Alignment.Center
        ) {
            if (isOwnAdd) {
                Icon(Icons.Default.Add, contentDescription = title, tint = Color.White, modifier = Modifier.size(26.dp))
            } else {
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    val avatarGradient = remember(chat.id, chat.isSecret) {
        if (chat.isSecret) {
            listOf(Color(0xFF30D158), Color(0xFF0B8A42))
        } else {
            val palettes = listOf(
                listOf(Color(0xFF64D2FF), Color(0xFF007AFF)),
                listOf(Color(0xFFFF6482), Color(0xFFFF3B30)),
                listOf(Color(0xFFBF5AF2), Color(0xFF5E5CE6)),
                listOf(Color(0xFF30D158), Color(0xFF00C7BE)),
                listOf(Color(0xFFFFB340), Color(0xFFFF9500))
            )
            palettes[(kotlin.math.abs(chat.id) % palettes.size).toInt()]
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
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

        // 54.dp Glass Avatar
        Box(modifier = Modifier.size(54.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(avatarGradient))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (chat.title == "Saved Messages") {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                } else {
                    Text(
                        text = chat.title.take(1).uppercase(),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
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

        Spacer(Modifier.width(13.dp))

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
                    if (chat.isSecret) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secret",
                            tint = TelegramSecretGreen,
                            modifier = Modifier
                                .size(15.dp)
                                .padding(end = 3.dp)
                        )
                    } else if (chat.type == "channel") {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 3.dp)
                        )
                    } else if (chat.type == "group" || chat.type == "supergroup") {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 3.dp)
                        )
                    }
                    Text(
                        text = chat.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (chat.isSecret) TelegramSecretGreen else MaterialTheme.colorScheme.onBackground
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
                            Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                Text(
                    text = chat.lastMessageTime,
                    fontSize = 12.sp,
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
