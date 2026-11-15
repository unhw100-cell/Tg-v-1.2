package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.example.ui.components.TelegramChatWallpaper
import com.example.ui.components.liquidGlass
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    repository: TelegramRepository,
    chat: TelegramChatModel,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val strings = LocalAppStrings.current
    val glass = LocalLiquidGlassPalette.current
    val context = LocalContext.current
    val chats by repository.chats.collectAsState()
    val currentChat = chats.find { it.id == chat.id } ?: chat
    val messagesMap by repository.messages.collectAsState()
    val wallpaperStyle by repository.wallpaperStyle.collectAsState()
    val isRecordingVoice by repository.isRecordingVoice.collectAsState()
    val recordingDurationSec by repository.recordingDurationSec.collectAsState()
    val playingMessageId by repository.playingMessageId.collectAsState()

    val chatMessages = messagesMap[currentChat.id].orEmpty()

    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var editingMessage by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var messageToForward by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var viewingMediaMessage by remember { mutableStateOf<TelegramMessageModel?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showPollCreator by remember { mutableStateOf(false) }
    var showSendOptionsMenu by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showStickerPanel by remember { mutableStateOf(false) }
    var isVideoNoteMode by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

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

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        repository.startVoiceRecording()
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Wallpaper Layer beneath floating Liquid Glass chrome
        TelegramChatWallpaper(
            isDarkMode = glass.isDark,
            wallpaperStyle = wallpaperStyle,
            accentColor = glass.accent,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Floating Liquid Glass Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(50),
                            strong = true,
                            accentTint = if (currentChat.isSecret) TelegramSecretGreen else null
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("chat_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = strings.tabChats,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpenProfile)
                            .padding(horizontal = 6.dp)
                            .testTag("chat_header_profile_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentChat.isSecret) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Secret",
                                    tint = TelegramSecretGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = currentChat.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (currentChat.isSecret) TelegramSecretGreen else MaterialTheme.colorScheme.onBackground
                            )
                            if (currentChat.isVerified) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = when {
                                currentChat.isSecret -> "${strings.selfDestructTimer}: ${if (currentChat.selfDestructSec > 0) "${currentChat.selfDestructSec}s" else strings.timerOff}"
                                currentChat.type == "channel" -> "${currentChat.memberCount.coerceAtLeast(1)} ${strings.subscribersSuffix}"
                                currentChat.type == "supergroup" || currentChat.type == "group" -> "${currentChat.memberCount.coerceAtLeast(1)} ${strings.membersSuffix}"
                                currentChat.type == "bot" -> "bot"
                                currentChat.isOnline -> strings.onlineStatus
                                else -> strings.lastSeenRecently
                            },
                            fontSize = 11.sp,
                            color = if (currentChat.isSecret) TelegramSecretGreen
                            else if (currentChat.isOnline) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (currentChat.isSecret) {
                        IconButton(
                            onClick = { showTimerDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("secret_timer_btn")
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = strings.selfDestructTimer,
                                tint = TelegramSecretGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (currentChat.isSecret) TelegramSecretGreen else MaterialTheme.colorScheme.primary)
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
                }

                // Floating Pinned Message Pill
                currentChat.pinnedMessageText?.let { pinnedText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(shape = RoundedCornerShape(16.dp), strong = false, elevation = 3.dp)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(26.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.pinnedMessage,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
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
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Messages Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(chatMessages, key = { it.id }) { message ->
                    SwipeableMessageBubble(
                        message = message,
                        strings = strings,
                        isPlaying = playingMessageId == message.id,
                        onSwipeToReply = { replyingToMessage = message },
                        onPlayVoiceToggle = { repository.toggleVoicePlayback(message) },
                        onVotePoll = { optIdx -> repository.votePoll(currentChat.id, message.id, optIdx) },
                        onPhotoClick = { viewingMediaMessage = message },
                        onReactionClick = { emoji -> repository.toggleReaction(currentChat.id, message.id, emoji) },
                        onLongClick = { selectedMessageForOptions = message }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Bottom Floating Liquid Glass Composer Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Reply or Edit Banner
                val bannerTitle = editingMessage?.let { strings.editMessage }
                    ?: replyingToMessage?.let { "${strings.replyTo} ${it.senderName}" }
                val bannerBody = editingMessage?.text ?: replyingToMessage?.text
                if (bannerTitle != null && bannerBody != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(shape = RoundedCornerShape(18.dp), strong = true)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bannerTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(bannerBody, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = {
                                if (editingMessage != null) inputText = ""
                                replyingToMessage = null
                                editingMessage = null
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = strings.cancel, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Sticker Quick Strip
                AnimatedVisibility(visible = showStickerPanel) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(shape = RoundedCornerShape(22.dp), strong = true)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("🚀", "🔥", "❤️", "👍", "😂", "🤩", "🦀", "💎").forEach { sticker ->
                            Text(
                                text = sticker,
                                fontSize = 27.sp,
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
                                    .padding(4.dp)
                            )
                        }
                    }
                }

                // Floating Liquid Glass Composer Capsule
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(shape = RoundedCornerShape(50), strong = true)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                            Text(strings.cancel, color = TelegramIosRed, fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { repository.stopAndSendVoiceRecording(currentChat.id) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("send_recorded_voice_button")
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = strings.voiceMessageLabel,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlass(shape = RoundedCornerShape(28.dp), strong = true)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("attach_button")
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Attach",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = strings.messagePlaceholder,
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
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                if (showStickerPanel) Icons.Default.Keyboard else Icons.Default.SentimentSatisfiedAlt,
                                contentDescription = "Stickers",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(2.dp))

                        if (inputText.isNotBlank()) {
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .combinedClickable(
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
                                            onLongClick = { showSendOptionsMenu = true }
                                        )
                                        .testTag("send_message_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (editingMessage != null) Icons.Default.Check else Icons.Default.ArrowUpward,
                                        contentDescription = "Send",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSendOptionsMenu,
                                    onDismissRequest = { showSendOptionsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strings.sendSilently) },
                                        leadingIcon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                                        onClick = {
                                            showSendOptionsMenu = false
                                            repository.sendMessage(
                                                chatId = currentChat.id,
                                                rawText = inputText.trim(),
                                                replyToId = replyingToMessage?.id,
                                                isSilent = true
                                            )
                                            replyingToMessage = null
                                            inputText = ""
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("${strings.scheduleMessage}: ${strings.scheduleIn10Sec}") },
                                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                        onClick = {
                                            showSendOptionsMenu = false
                                            repository.sendMessage(
                                                chatId = currentChat.id,
                                                rawText = inputText.trim(),
                                                replyToId = replyingToMessage?.id,
                                                scheduleDelaySec = 10
                                            )
                                            replyingToMessage = null
                                            inputText = ""
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("${strings.scheduleMessage}: ${strings.scheduleIn1Min}") },
                                        leadingIcon = { Icon(Icons.Default.ScheduleSend, contentDescription = null) },
                                        onClick = {
                                            showSendOptionsMenu = false
                                            repository.sendMessage(
                                                chatId = currentChat.id,
                                                rawText = inputText.trim(),
                                                replyToId = replyingToMessage?.id,
                                                scheduleDelaySec = 60
                                            )
                                            replyingToMessage = null
                                            inputText = ""
                                        }
                                    )
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { isVideoNoteMode = !isVideoNoteMode },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isVideoNoteMode) Icons.Default.Mic else Icons.Default.CameraAlt,
                                        contentDescription = "Switch Mic / Video Note",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (isVideoNoteMode) {
                                            repository.sendMediaMessage(currentChat.id, MediaType.VIDEO_NOTE)
                                        } else {
                                            val hasPerm = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) repository.startVoiceRecording()
                                            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                        .testTag("record_voice_button")
                                ) {
                                    Icon(
                                        if (isVideoNoteMode) Icons.Default.Videocam else Icons.Default.Mic,
                                        contentDescription = strings.voiceMessageLabel,
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
    }

    // Attachment Sheet (Photo, File, Poll, Location, Contact)
    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(
                    "${strings.shareToPrefix} ${currentChat.title}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    IosAttachmentOption(Icons.Default.Image, strings.photoLabel, TelegramOnlineGreen) {
                        showAttachmentSheet = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    IosAttachmentOption(Icons.Default.Poll, strings.pollLabel, TelegramIosPurple) {
                        showAttachmentSheet = false
                        showPollCreator = true
                    }
                    IosAttachmentOption(Icons.AutoMirrored.Filled.InsertDriveFile, strings.fileLabel, IosBlueLight) {
                        repository.sendMediaMessage(
                            chatId = currentChat.id,
                            mediaType = MediaType.DOCUMENT,
                            fileName = "RustGram_Specs_v2.pdf",
                            fileSize = "2.4 MB"
                        )
                        showAttachmentSheet = false
                    }
                    IosAttachmentOption(Icons.Default.LocationOn, strings.locationLabel, TelegramIosOrange) {
                        repository.sendMessage(currentChat.id, "📍 Shared Live Location (52.3676° N, 4.9041° E)")
                        showAttachmentSheet = false
                    }
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (showPollCreator) {
        PollCreatorDialog(
            onCreatePoll = { q, opts, isQuiz, correctIdx, multi ->
                repository.sendPollMessage(currentChat.id, q, opts, isQuiz, correctIdx, multi)
                showPollCreator = false
            },
            onDismiss = { showPollCreator = false }
        )
    }

    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text(strings.selfDestructTimer, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to strings.timerOff, 5 to "5s", 15 to "15s", 60 to "60s").forEach { (sec, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    repository.setSecretSelfDestructTimer(currentChat.id, sec)
                                    showTimerDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentChat.selfDestructSec == sec,
                                onClick = {
                                    repository.setSecretSelfDestructTimer(currentChat.id, sec)
                                    showTimerDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text(strings.close) }
            }
        )
    }

    viewingMediaMessage?.let { mediaMsg ->
        MediaViewerModal(
            message = mediaMsg,
            onDismiss = { viewingMediaMessage = null }
        )
    }

    // Message Options Dialog
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
                    IosMenuAction(Icons.AutoMirrored.Filled.Reply, strings.reply) {
                        replyingToMessage = msg
                        selectedMessageForOptions = null
                    }
                    IosMenuAction(Icons.Default.ContentCopy, strings.copy) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Message", msg.text))
                        selectedMessageForOptions = null
                    }
                    IosMenuAction(Icons.AutoMirrored.Filled.Forward, strings.forward) {
                        messageToForward = msg
                        selectedMessageForOptions = null
                    }
                    IosMenuAction(Icons.Default.PushPin, strings.pin) {
                        repository.pinMessage(currentChat.id, msg)
                        selectedMessageForOptions = null
                    }
                    if (msg.isOutgoing && msg.mediaType == MediaType.NONE) {
                        IosMenuAction(Icons.Default.Edit, strings.edit) {
                            editingMessage = msg
                            inputText = msg.text
                            selectedMessageForOptions = null
                        }
                    }
                    IosMenuAction(Icons.Default.Delete, strings.delete, isDestructive = true) {
                        repository.deleteMessage(currentChat.id, msg.id)
                        selectedMessageForOptions = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForOptions = null }) {
                    Text(strings.close)
                }
            }
        )
    }

    // Forward Target Picker
    messageToForward?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToForward = null },
            title = { Text(strings.forwardTo, fontWeight = FontWeight.SemiBold) },
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
                TextButton(onClick = { messageToForward = null }) { Text(strings.cancel) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableMessageBubble(
    message: TelegramMessageModel,
    strings: AppStrings,
    isPlaying: Boolean,
    onSwipeToReply: () -> Unit,
    onPlayVoiceToggle: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onPhotoClick: () -> Unit,
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
                    Icons.AutoMirrored.Filled.Reply,
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
                strings = strings,
                isPlaying = isPlaying,
                onPlayVoiceToggle = onPlayVoiceToggle,
                onVotePoll = onVotePoll,
                onPhotoClick = onPhotoClick,
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
    strings: AppStrings,
    isPlaying: Boolean,
    onPlayVoiceToggle: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onPhotoClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val glass = LocalLiquidGlassPalette.current
    val isOut = message.isOutgoing
    val bubbleBrush = if (isOut) {
        Brush.verticalGradient(listOf(glass.bubbleOutStart, glass.bubbleOutEnd))
    } else {
        Brush.verticalGradient(listOf(glass.bubbleIn, glass.glassSurfaceStrong))
    }
    val rimColor = if (isOut) Color.White.copy(alpha = 0.42f) else glass.glassSpecularTop

    val textColor = if (isOut) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isOut) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isOut) 20.dp else 6.dp,
        bottomEnd = if (isOut) 6.dp else 20.dp
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
            Box(
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 312.dp)
                    .clip(bubbleShape)
                    .background(bubbleBrush)
                    .border(0.9.dp, rimColor, bubbleShape)
                    .combinedClickable(onClick = onLongClick, onLongClick = onLongClick)
            ) {
                Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)) {
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
                            text = "${strings.forward}: $from",
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
                                .clip(RoundedCornerShape(8.dp))
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

                    // Interactive Poll Card
                    if (message.mediaType == MediaType.POLL && message.pollData != null) {
                        val poll = message.pollData
                        Text(
                            text = poll.question,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (poll.isQuiz) strings.quizMode else strings.pollLabel,
                            fontSize = 11.sp,
                            color = mutedColor
                        )
                        Spacer(Modifier.height(8.dp))

                        val totalVotes = poll.totalVotes.coerceAtLeast(1)
                        val hasVoted = poll.selectedIndices.isNotEmpty()

                        poll.options.forEachIndexed { idx, opt ->
                            val isSelected = poll.selectedIndices.contains(idx)
                            val pct = if (hasVoted) (opt.votes * 100) / totalVotes else 0
                            val fraction = if (hasVoted) (opt.votes.toFloat() / totalVotes.toFloat()).coerceIn(0.05f, 1f) else 0f
                            val isCorrectQuiz = poll.isQuiz && poll.correctOptionIndex == idx

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isOut) Color.White.copy(alpha = 0.14f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.6.dp,
                                        color = when {
                                            hasVoted && isCorrectQuiz -> TelegramOnlineGreen
                                            isSelected && isOut -> Color.White
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            else -> Color.Transparent
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onVotePoll(idx) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isSelected) "✓ ${opt.text}" else opt.text,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (hasVoted) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "$pct%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCorrectQuiz) TelegramOnlineGreen else mutedColor
                                        )
                                    }
                                }
                                if (hasVoted) {
                                    Spacer(Modifier.height(5.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(textColor.copy(alpha = 0.15f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(
                                                    when {
                                                        isCorrectQuiz -> TelegramOnlineGreen
                                                        isOut -> Color.White
                                                        else -> MaterialTheme.colorScheme.primary
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${poll.totalVotes} ${strings.votesSuffix}",
                            fontSize = 11.sp,
                            color = mutedColor
                        )
                    } else if (message.mediaType == MediaType.PHOTO && !message.mediaUri.isNullOrBlank()) {
                        AsyncImage(
                            model = message.mediaUri,
                            contentDescription = strings.tapToViewPhoto,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 240.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onPhotoClick)
                        )
                        if (message.text != "📷 Photo") {
                            Spacer(Modifier.height(6.dp))
                            Text(text = message.text, fontSize = 15.sp, color = textColor)
                        }
                    } else if (message.mediaType == MediaType.DOCUMENT) {
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
                                    Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = strings.fileLabel,
                                    tint = if (isOut) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(message.fileName ?: message.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor)
                                Text(message.fileSize ?: strings.fileLabel, fontSize = 12.sp, color = mutedColor)
                            }
                        }
                    } else if (message.mediaType == MediaType.VOICE) {
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
                                    contentDescription = strings.voiceMessageLabel,
                                    tint = if (isOut) glass.accent else Color.White,
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
                    } else {
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
                        if (message.scheduledForEpochMs != null) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = strings.scheduledBadge,
                                tint = mutedColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        if (message.isSilent) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = strings.silentBadge,
                                tint = mutedColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        if (message.expiresAtEpochMs != null) {
                            val remainSec = ((message.expiresAtEpochMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                            Text(
                                text = "⏱${remainSec}s ",
                                fontSize = 10.sp,
                                color = mutedColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (message.isEdited) {
                            Text(
                                text = strings.editedLabel,
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
                        color = if (reaction.isSelected) MaterialTheme.colorScheme.primary else glass.glassSurfaceStrong,
                        shadowElevation = 2.dp
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
