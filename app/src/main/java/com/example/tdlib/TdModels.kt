package com.example.tdlib

sealed class TdAuthState {
    object Initializing : TdAuthState()
    object WaitTdlibParameters : TdAuthState()
    object WaitPhoneNumber : TdAuthState()
    data class WaitCode(val phoneNumber: String, val codeLength: Int = 5, val timeoutSec: Int = 60) : TdAuthState()
    data class WaitPassword(val hint: String?, val hasRecoveryEmail: Boolean = false) : TdAuthState()
    data class WaitOtherDeviceConfirmation(val link: String) : TdAuthState()
    object Ready : TdAuthState()
    object LoggingOut : TdAuthState()
    object Closed : TdAuthState()
}

enum class ChatCategory(val label: String) {
    ALL("All Chats"),
    PERSONAL("Personal"),
    GROUPS("Groups"),
    CHANNELS("Channels"),
    BOTS("Bots"),
    UNREAD("Unread")
}

enum class MainTab {
    CONTACTS, CALLS, CHATS, SETTINGS
}

enum class MediaType {
    NONE, PHOTO, VIDEO, VOICE, VIDEO_NOTE, DOCUMENT, STICKER
}

data class TextEntitySpan(
    val offset: Int,
    val length: Int,
    val type: String // Bold, Italic, Code, Strikethrough, Spoiler, Mention, Hashtag, Url
)

data class TelegramChatModel(
    val id: Long,
    val title: String,
    val type: String, // "private", "group", "supergroup", "channel", "bot"
    val lastMessageText: String,
    val lastMessageTime: String,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val avatarUrl: String? = null,
    val isOnline: Boolean = false,
    val isVerified: Boolean = false,
    val username: String? = null,
    val bio: String = "",
    val phoneNumber: String? = null,
    val memberCount: Int = 0,
    val pinnedMessageText: String? = null,
    val category: ChatCategory = ChatCategory.PERSONAL
)

data class ReactionItem(
    val emoji: String,
    var count: Int,
    var isSelected: Boolean = false
)

data class TelegramMessageModel(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val isOutgoing: Boolean,
    val text: String,
    val timestamp: String,
    val epochSec: Long = System.currentTimeMillis() / 1000L,
    val isRead: Boolean = true,
    val isEdited: Boolean = false,
    val forwardedFrom: String? = null,
    val replyToMessage: TelegramMessageModel? = null,
    val mediaType: MediaType = MediaType.NONE,
    val mediaUri: String? = null,
    val voiceDurationSec: Int = 0,
    val voiceFilePath: String? = null,
    val voiceWaveform: List<Float> = emptyList(),
    val fileName: String? = null,
    val fileSize: String? = null,
    val entities: List<TextEntitySpan> = emptyList(),
    val reactions: List<ReactionItem> = emptyList()
)

data class CountryItem(
    val name: String,
    val code: String,
    val dialCode: String,
    val flag: String
)

data class ContactModel(
    val id: Long,
    val name: String,
    val username: String,
    val phone: String,
    val bio: String,
    val isOnline: Boolean,
    val lastSeen: String
)

data class CallHistoryItem(
    val id: Long,
    val callerName: String,
    val isVideo: Boolean,
    val isOutgoing: Boolean,
    val isMissed: Boolean,
    val durationText: String,
    val timestamp: String
)

data class SearchHitItem(
    val chat: TelegramChatModel,
    val matchedMessage: TelegramMessageModel? = null
)
