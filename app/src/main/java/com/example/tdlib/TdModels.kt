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

enum class AppLanguage(val code: String, val displayName: String) {
    RU("ru", "Русский"),
    EN("en", "English")
}

enum class TelegramThemePreset {
    DAY, CLASSIC, TINTED_DARK, OLED_NIGHT
}

enum class WallpaperStyle {
    TELEGRAM_PATTERN, ARCTIC_ICE, MIDNIGHT_AURORA, LAVENDER_MIST, SOLID_MINIMAL
}

enum class ChatCategory {
    ALL, PERSONAL, GROUPS, CHANNELS, BOTS, UNREAD, SECRET
}

enum class MainTab {
    CONTACTS, CALLS, CHATS, SETTINGS
}

enum class MediaType {
    NONE, PHOTO, VIDEO, VOICE, VIDEO_NOTE, DOCUMENT, STICKER, POLL
}

data class TextEntitySpan(
    val offset: Int,
    val length: Int,
    val type: String
)

data class PollOption(
    val text: String,
    val votes: Int = 0
)

data class PollData(
    val question: String,
    val options: List<PollOption>,
    val isQuiz: Boolean = false,
    val correctOptionIndex: Int? = null,
    val allowMultiple: Boolean = false,
    val selectedIndices: Set<Int> = emptySet(),
    val isAnonymous: Boolean = true
) {
    val totalVotes: Int
        get() = options.sumOf { it.votes }
}

data class TelegramChatModel(
    val id: Long,
    val title: String,
    val type: String, // "private", "group", "supergroup", "channel", "bot", "secret"
    val lastMessageText: String,
    val lastMessageTime: String,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val isSecret: Boolean = false,
    val selfDestructSec: Int = 0,
    val encryptionFingerprint: String = "🔐 🦀 🚀 ⚡",
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
    val isSilent: Boolean = false,
    val scheduledForEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val forwardedFrom: String? = null,
    val replyToMessage: TelegramMessageModel? = null,
    val mediaType: MediaType = MediaType.NONE,
    val mediaUri: String? = null,
    val voiceDurationSec: Int = 0,
    val voiceFilePath: String? = null,
    val voiceWaveform: List<Float> = emptyList(),
    val fileName: String? = null,
    val fileSize: String? = null,
    val pollData: PollData? = null,
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

data class AccountProfile(
    val id: Int,
    val name: String,
    val username: String,
    val phone: String,
    val bio: String,
    val avatarColorIndex: Int = 0,
    val isActive: Boolean = false
)

data class StorySlide(
    val id: Long,
    val text: String,
    val startColorHex: Long,
    val endColorHex: Long,
    val timestamp: String
)

data class StoryItem(
    val id: Long,
    val authorName: String,
    val chatId: Long,
    val avatarColorIndex: Int,
    val isViewed: Boolean = false,
    val isOwnStory: Boolean = false,
    val slides: List<StorySlide>
)

data class TdLogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val isError: Boolean = false
)
