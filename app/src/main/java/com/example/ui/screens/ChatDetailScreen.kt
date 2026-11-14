package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.repository.TelegramRepository
import com.example.tdlib.MediaType
import com.example.tdlib.TelegramChatModel
import com.example.tdlib.TelegramMessageModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    repository: TelegramRepository,
    chat: TelegramChatModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current
    val chats by repository.chats.collectAsState()
    val currentChat = chats.find { it.id == chat.id } ?: chat
    val messagesMap by repository.messages.collectAsState()
    val isDarkMode by repository.isDarkMode.collectAsState()
    val isRecordingVoice by repository.isRecordingVoice.collectAsState()
    val recordingDurationSec by repository.recordingDurationSec.collectAsState()
    val playingMessageId by repository.playingMessageId.collectAsState()

    val chatMessages = messagesMap[currentChat.id].orEmpty()

    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var editingMessage by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var messageToForward by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showStickerPanel by remember { mutableStateOf(false) }
    var isVideoNoteMode by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Photo Picker (Zero-permission Google Play compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            repository.sendMediaMessage(
                chatId = currentChat.id,
                mediaType = MediaType.PHOTO,
                mediaUri = uri.toString(),
                caption = inputText.trim()
            )
            inputText = ""
        }
    }

    // Audio permission launcher for Voice Notes
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            repository.startVoiceRecording()
        } else {
            // Fallback simulated voice recording if user declines permission
            repository.startVoiceRecording()
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(onClick = onBack)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .testTag("chat_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Chats",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Chats",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 17.sp
                            )
                        }
                    },
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(onClick = onOpenProfile)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .testTag("chat_header_profile_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentChat.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (currentChat.isVerified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = "Verified",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Text(
                                text = when (currentChat.type) {
                                    "channel" -> "${currentChat.memberCount.coerceAtLeast(1)} subscribers"
                                    "supergroup", "group" -> "${currentChat.memberCount.coerceAtLeast(1)} members"
                                    "bot" -> "bot"
                                    else -> if (currentChat.isOnline) "online" else "last seen recently"
                                },
                                fontSize = 12.sp,
                                color = if (currentChat.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onOpenProfile)
                                .testTag("chat_avatar_profile_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentChat.title == "Saved Messages") {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Text(
                                    text = currentChat.title.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Pinned message bar
                currentChat.pinnedMessageText?.let { pinnedText ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pinned Message",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = pinnedText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)

                // Reply or Edit Banner
                val bannerTitle = editingMessage?.let { "Edit Message" } ?: replyingToMessage?.let { "Reply to ${it.senderName}" }
                val bannerBody = editingMessage?.text ?: replyingToMessage?.text
                if (bannerTitle != null && bannerBody != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (editingMessage != null) Icons.Default.Edit else Icons.Default.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(30.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bannerTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = bannerBody,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                replyingToMessage = null
                                editingMessage = null
                                if (editingMessage != null) inputText = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                }

                // Sticker Quick Strip
                AnimatedVisibility(visible = showStickerPanel) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("🚀", "🔥", "❤️", "👍", "😂", "🤩", "🦀").forEach { sticker ->
                            Text(
                                text = sticker,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        repository.sendMediaMessage(
                                            chatId = currentChat.id,
                                            mediaType = MediaType.STICKER,
                                            caption = sticker
                                        )
                                        showStickerPanel = false
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
                }

                // Composer Bar (Voice Recording Active vs Standard iOS Input)
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(TelegramIosRed)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "0:${recordingDurationSec.toString().padStart(2, '0')}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { repository.cancelVoiceRecording() }) {
                            Text("Cancel", color = TelegramIosRed, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { repository.stopAndSendVoiceRecording(currentChat.id) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("send_recorded_voice_button")
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Send voice message",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier.size(40.dp).testTag("attach_button")
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Attach media",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // iOS Rounded Capsule Input
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 3.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = "Message",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 16.sp
                                    )
                                }
                                BasicTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    maxLines = 5,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = {
                                        if (inputText.isNotBlank()) {
                                            val editTarget = editingMessage
                                            if (editTarget != null) {
                                                repository.editMessage(currentChat.id, editTarget.id, inputText.trim())
                                                editingMessage = null
                                            } else {
                                                repository.sendMessage(currentChat.id, inputText.trim(), replyingToMessage?.id)
                                                replyingToMessage = null
                                            }
                                            inputText = ""
                                        }
                                    }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("message_input_field")
                                )
                            }

                            IconButton(
                                onClick = { showStickerPanel = !showStickerPanel },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (showStickerPanel) Icons.Default.Keyboard else Icons.Default.SentimentSatisfiedAlt,
                                    contentDescription = "Stickers",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val editTarget = editingMessage
                                    if (editTarget != null) {
                                        repository.editMessage(currentChat.id, editTarget.id, inputText.trim())
                                        editingMessage = null
                                    } else {
                                        repository.sendMessage(currentChat.id, inputText.trim(), replyingToMessage?.id)
                                        replyingToMessage = null
                                    }
                                    inputText = ""
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    if (editingMessage != null) Icons.Default.Check else Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isVideoNoteMode = !isVideoNoteMode },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (isVideoNoteMode) Icons.Default.Mic else Icons.Default.CameraAlt,
                                        contentDescription = "Toggle Mic / Video Note",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isVideoNoteMode) {
                                            repository.sendMediaMessage(
                                                chatId = currentChat.id,
                                                mediaType = MediaType.VIDEO_NOTE
                                            )
                                        } else {
                                            val hasPerm = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) {
                                                repository.startVoiceRecording()
                                            } else {
                                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                        .testTag("record_voice_button")
                                ) {
                                    Icon(
                                        if (isVideoNoteMode) Icons.Default.Videocam else Icons.Default.Mic,
                                        contentDescription = if (isVideoNoteMode) "Record video note" else "Record voice message",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Telegram Patterned Chat Wallpaper
            TelegramChatWallpaper(isDarkMode = isDarkMode, modifier = Modifier.fillMaxSize())

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(chatMessages, key = { it.id }) { message ->
                    SwipeableMessageBubble(
                        message = message,
                        isDarkMode = isDarkMode,
                        isPlaying = playingMessageId == message.id,
                        onSwipeToReply = { replyingToMessage = message },
                        onPlayVoiceToggle = { repository.toggleVoicePlayback(message) },
                        onReactionClick = { emoji -> repository.toggleReaction(currentChat.id, message.id, emoji) },
                        onLongClick = { selectedMessageForOptions = message }
                    )
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }

    // Attachment Sheet (Photo Picker, Document, Location, Contact)
    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    "Share to ${currentChat.title}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IosAttachmentOption(Icons.Default.Image, "Photo", TelegramOnlineGreen) {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    IosAttachmentOption(Icons.Default.InsertDriveFile, "File", IosBlueLight) {
                        repository.sendMediaMessage(
                            chatId = currentChat.id,
                            mediaType = MediaType.DOCUMENT,
                            fileName = "RustGram_Specs_v1.1.pdf",
                            fileSize = "2.4 MB"
                        )
                        showAttachmentSheet = false
                    }
                    IosAttachmentOption(Icons.Default.LocationOn, "Location", TelegramIosOrange) {
                        repository.sendMessage(currentChat.id, "📍 Shared Live Location (52.3676° N, 4.9041° E)")
                        showAttachmentSheet = false
                    }
                    IosAttachmentOption(Icons.Default.Person, "Contact", TelegramIosPurple) {
                        repository.sendMessage(currentChat.id, "👤 Contact: Pavel Rust (@rustgram_dev)")
                        showAttachmentSheet = false
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // iOS Context Action Dialog + Quick Reaction Bar
    selectedMessageForOptions?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForOptions = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("👍", "❤️", "🔥", "😂", "👏", "🎉").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    repository.toggleReaction(currentChat.id, msg.id, emoji)
                                    selectedMessageForOptions = null
                                }
                                .padding(4.dp)
                        )
                    }
                }
            },
            text = {
                Column {
                    IosMenuAction(Icons.Default.Reply, "Reply") {
                        replyingToMessage = msg
                        selectedMessageForOptions = null
                    }
                    IosMenuAction(Icons.Default.ContentCopy, "Copy") {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Message", msg.text))
                        selectedMessageForOptions = null
                    }
                    IosMenuAction(Icons.Default.Forward, "Forward") {
                        messageToForward = msg
                        selectedMessageForOptions = null
                    }
                    IosMenuAction(Icons.Default.PushPin, "Pin") {
                        repository.pinMessage(currentChat.id, msg)
                        selectedMessageForOptions = null
                    }
                    if (msg.isOutgoing && msg.mediaType == MediaType.NONE) {
                        IosMenuAction(Icons.Default.Edit, "Edit") {
                            editingMessage = msg
                            inputText = msg.text
                            selectedMessageForOptions = null
                        }
                    }
                    IosMenuAction(Icons.Default.Delete, "Delete", isDestructive = true) {
                        repository.deleteMessage(currentChat.id, msg.id)
                        selectedMessageForOptions = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForOptions = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Forward Target Picker
    messageToForward?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToForward = null },
            title = { Text("Forward to…", fontWeight = FontWeight.SemiBold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(chats) { targetChat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.forwardMessage(targetChat.id, msg)
                                    messageToForward = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(targetChat.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(targetChat.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { messageToForward = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TelegramChatWallpaper(isDarkMode: Boolean, modifier: Modifier = Modifier) {
    val bgColors = if (isDarkMode) {
        listOf(Color(0xFF06090E), Color(0xFF0D1520), Color(0xFF0A111A))
    } else {
        listOf(Color(0xFFD2E7D6), Color(0xFFCDE2EE), Color(0xFFDCEBC9))
    }
    val motifColor = if (isDarkMode) Color.White.copy(alpha = 0.04f) else Color(0xFF2B6049).copy(alpha = 0.08f)

    Canvas(
        modifier = modifier.background(Brush.verticalGradient(bgColors))
    ) {
        val stepX = 110.dp.toPx()
        val stepY = 110.dp.toPx()
        val cols = (size.width / stepX).toInt() + 2
        val rows = (size.height / stepY).toInt() + 2

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = c * stepX + (if (r % 2 == 0) 20f else 60f)
                val cy = r * stepY + 30f
                when ((r + c) % 3) {
                    0 -> {
                        // Subtle paper plane silhouette
                        val path = Path().apply {
                            moveTo(cx, cy)
                            lineTo(cx + 26f, cy - 10f)
                            lineTo(cx + 14f, cy + 16f)
                            close()
                        }
                        drawPath(path, motifColor, style = Stroke(width = 2.5f))
                    }
                    1 -> {
                        drawCircle(motifColor, radius = 14f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
                    }
                    else -> {
                        drawLine(
                            motifColor,
                            start = Offset(cx - 12f, cy),
                            end = Offset(cx + 12f, cy),
                            strokeWidth = 2.5f
                        )
                        drawLine(
                            motifColor,
                            start = Offset(cx, cy - 12f),
                            end = Offset(cx, cy + 12f),
                            strokeWidth = 2.5f
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableMessageBubble(
    message: TelegramMessageModel,
    isDarkMode: Boolean,
    isPlaying: Boolean,
    onSwipeToReply: () -> Unit,
    onPlayVoiceToggle: () -> Unit,
    onReactionClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var triggeredHaptic by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX.value <= -75f) {
                            onSwipeToReply()
                        }
                        triggeredHaptic = false
                        scope.launch {
                            offsetX.animateTo(0f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f))
                        }
                    },
                    onDragCancel = {
                        triggeredHaptic = false
                        scope.launch { offsetX.animateTo(0f) }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val next = (offsetX.value + dragAmount).coerceIn(-120f, 0f)
                        if (next <= -75f && !triggeredHaptic) {
                            triggeredHaptic = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        scope.launch { offsetX.snapTo(next) }
                    }
                )
            }
    ) {
        // Revealed Swipe-to-Reply icon on the right
        if (offsetX.value < -15f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = ((-offsetX.value) / 90f).coerceIn(0.2f, 0.9f))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Reply,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        ) {
            IosMessageBubble(
                message = message,
                isDarkMode = isDarkMode,
                isPlaying = isPlaying,
                onPlayVoiceToggle = onPlayVoiceToggle,
                onReactionClick = onReactionClick,
                onLongClick = onLongClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosMessageBubble(
    message: TelegramMessageModel,
    isDarkMode: Boolean,
    isPlaying: Boolean,
    onPlayVoiceToggle: () -> Unit,
    onReactionClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val isOut = message.isOutgoing
    val bubbleBrush = if (isOut) {
        if (isDarkMode) Brush.verticalGradient(listOf(IosDarkBubbleOutStart, IosDarkBubbleOutEnd))
        else Brush.verticalGradient(listOf(IosLightBubbleOutStart, IosLightBubbleOutEnd))
    } else {
        if (isDarkMode) Brush.verticalGradient(listOf(IosDarkBubbleIn, IosDarkBubbleIn))
        else Brush.verticalGradient(listOf(IosLightBubbleIn, IosLightBubbleIn))
    }

    val textColor = if (isOut) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isOut) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant

    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isOut) 18.dp else 5.dp,
        bottomEnd = if (isOut) 5.dp else 18.dp
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = if (isOut) Alignment.End else Alignment.Start
    ) {
        if (message.mediaType == MediaType.STICKER) {
            Text(
                text = message.text,
                fontSize = 68.sp,
                modifier = Modifier
                    .combinedClickable(onClick = onLongClick, onLongClick = onLongClick)
                    .padding(4.dp)
            )
        } else if (message.mediaType == MediaType.VIDEO_NOTE) {
            // Circular Telegram Video Note ("Кружочек")
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF34AADC), Color(0xFF1C2D42))))
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .combinedClickable(onClick = onLongClick, onLongClick = onLongClick),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayCircleFilled, contentDescription = "Video Note", tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("0:0${message.voiceDurationSec}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Surface(
                shape = bubbleShape,
                shadowElevation = if (isDarkMode) 0.dp else 1.dp,
                color = Color.Transparent,
                modifier = Modifier
                    .widthIn(min = 84.dp, max = 304.dp)
                    .clip(bubbleShape)
                    .background(bubbleBrush)
                    .combinedClickable(onClick = onLongClick, onLongClick = onLongClick)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!isOut && message.senderName != "You") {
                        Text(
                            text = message.senderName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    message.forwardedFrom?.let { from ->
                        Text(
                            text = "Forwarded from $from",
                            fontSize = 12.sp,
                            fontStyle = FontStyle.Italic,
                            color = mutedColor,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    message.replyToMessage?.let { reply ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isOut) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.5.dp)
                                    .height(28.dp)
                                    .background(if (isOut) Color.White else MaterialTheme.colorScheme.primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    reply.senderName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOut) Color.White else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    reply.text,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = mutedColor
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // Photo attachment
                    if (message.mediaType == MediaType.PHOTO && !message.mediaUri.isNullOrBlank()) {
                        AsyncImage(
                            model = message.mediaUri,
                            contentDescription = "Shared photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (message.text != "📷 Photo") {
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    // Document attachment
                    if (message.mediaType == MediaType.DOCUMENT) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isOut) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = "Document",
                                    tint = if (isOut) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(message.fileName ?: message.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor)
                                Text(message.fileSize ?: "Document", fontSize = 12.sp, color = mutedColor)
                            }
                        }
                    } else if (message.mediaType == MediaType.VOICE) {
                        // Real Voice Note with Rust-normalized Waveform
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = onPlayVoiceToggle,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isOut) Color.White else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play voice",
                                    tint = if (isOut) IosBlueLight else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val bars = message.voiceWaveform.ifEmpty {
                                        listOf(0.3f, 0.6f, 0.9f, 0.5f, 0.8f, 0.4f, 0.7f, 0.3f, 0.6f, 0.85f, 0.5f, 0.25f)
                                    }
                                    bars.take(24).forEachIndexed { idx, ratio ->
                                        val active = isPlaying && idx % 2 == 0
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height((22.dp * ratio).coerceAtLeast(4.dp))
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(
                                                    when {
                                                        active && isOut -> Color.White
                                                        active -> MaterialTheme.colorScheme.primary
                                                        isOut -> Color.White.copy(alpha = 0.55f)
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    }
                                                )
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "0:${message.voiceDurationSec.toString().padStart(2, '0')}",
                                    fontSize = 11.sp,
                                    color = mutedColor
                                )
                            }
                        }
                    } else if (!(message.mediaType == MediaType.PHOTO && message.text == "📷 Photo")) {
                        // Rich AnnotatedString rendered from Rust parseMarkdown entities
                        val annotated = remember(message.text, message.entities, isOut) {
                            buildAnnotatedString {
                                append(message.text)
                                message.entities.forEach { span ->
                                    val start = span.offset.coerceIn(0, message.text.length)
                                    val end = (span.offset + span.length).coerceIn(start, message.text.length)
                                    if (start < end) {
                                        val style = when (span.type) {
                                            "Bold" -> SpanStyle(fontWeight = FontWeight.Bold)
                                            "Italic" -> SpanStyle(fontStyle = FontStyle.Italic)
                                            "Strikethrough" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                                            "Code" -> SpanStyle(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                background = if (isOut) Color.Black.copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.16f)
                                            )
                                            "Mention", "Hashtag", "Url" -> SpanStyle(
                                                color = if (isOut) Color(0xFFD6EEFF) else IosBlueLight,
                                                textDecoration = if (span.type == "Url") TextDecoration.Underline else TextDecoration.None
                                            )
                                            else -> null
                                        }
                                        if (style != null) addStyle(style, start, end)
                                    }
                                }
                            }
                        }
                        Text(
                            text = annotated,
                            fontSize = 15.5.sp,
                            lineHeight = 20.sp,
                            color = textColor
                        )
                    }

                    Spacer(Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "edited ",
                                fontSize = 11.sp,
                                color = mutedColor
                            )
                        }
                        Text(
                            text = message.timestamp,
                            fontSize = 11.sp,
                            color = mutedColor
                        )
                        if (isOut) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                if (message.isRead) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = if (message.isRead) "Read" else "Sent",
                                tint = if (message.isRead) Color.White else mutedColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                message.reactions.forEach { reaction ->
                    Surface(
                        onClick = { onReactionClick(reaction.emoji) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (reaction.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = "${reaction.emoji} ${reaction.count}",
                            fontSize = 12.sp,
                            color = if (reaction.isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosAttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IosMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, color = textColor)
    }
}
