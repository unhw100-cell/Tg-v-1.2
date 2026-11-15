package com.example.repository

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.rustgram.NativeBridge
import com.example.security.KeystoreSecretStore
import com.example.tdlib.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TelegramRepository(private val context: Context) {
    companion object {
        private const val TAG = "RustGram_Repo"
        private const val PREFS_NAME = "rustgram_prefs"
        private const val KEY_API_ID = "telegram_api_id"
        private const val KEY_API_HASH = "telegram_api_hash"
        private const val KEY_TDLIB_DATABASE_KEY = "tdlib_database_encryption_key"
        private const val KEY_PHONE = "telegram_phone"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_ACCENT_INDEX = "accent_index"
        private const val KEY_WALLPAPER_STYLE = "wallpaper_style"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_PASSCODE_HASH = "passcode_hash"
        private const val PASSCODE_KDF = "pbkdf2-sha256"
        private const val PASSCODE_ITERATIONS = 310_000
        private const val PASSCODE_KEY_BITS = 256
        private const val PASSCODE_SALT_BYTES = 16
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secretStore = KeystoreSecretStore(prefs)

    val tdClient = TdClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    // Language & Theme Constructor State
    private val _language = MutableStateFlow(
        if (prefs.getString(KEY_LANGUAGE, "ru") == "en") AppLanguage.EN else AppLanguage.RU
    )
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _themePreset = MutableStateFlow(
        runCatching {
            TelegramThemePreset.valueOf(
                prefs.getString(KEY_THEME_PRESET, TelegramThemePreset.DAY.name) ?: TelegramThemePreset.DAY.name
            )
        }.getOrDefault(TelegramThemePreset.DAY)
    )
    val themePreset: StateFlow<TelegramThemePreset> = _themePreset.asStateFlow()

    private val _isDarkMode = MutableStateFlow(
        _themePreset.value == TelegramThemePreset.TINTED_DARK || _themePreset.value == TelegramThemePreset.OLED_NIGHT
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _accentIndex = MutableStateFlow(prefs.getInt(KEY_ACCENT_INDEX, 0))
    val accentIndex: StateFlow<Int> = _accentIndex.asStateFlow()

    private val _wallpaperStyle = MutableStateFlow(
        runCatching {
            WallpaperStyle.valueOf(
                prefs.getString(KEY_WALLPAPER_STYLE, WallpaperStyle.TELEGRAM_PATTERN.name)
                    ?: WallpaperStyle.TELEGRAM_PATTERN.name
            )
        }.getOrDefault(WallpaperStyle.TELEGRAM_PATTERN)
    )
    val wallpaperStyle: StateFlow<WallpaperStyle> = _wallpaperStyle.asStateFlow()

    // Passcode Lock State
    private val _hasPasscode = MutableStateFlow(!prefs.getString(KEY_PASSCODE_HASH, null).isNullOrEmpty())
    val hasPasscode: StateFlow<Boolean> = _hasPasscode.asStateFlow()

    private val _isAppLocked = MutableStateFlow(_hasPasscode.value)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Auth & Real TDLib Diagnostics State
    private val _authState = MutableStateFlow<TdAuthState>(TdAuthState.Initializing)
    val authState: StateFlow<TdAuthState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthBusy = MutableStateFlow(false)
    val isAuthBusy: StateFlow<Boolean> = _isAuthBusy.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _tdLogEntries = MutableStateFlow<List<TdLogEntry>>(emptyList())
    val tdLogEntries: StateFlow<List<TdLogEntry>> = _tdLogEntries.asStateFlow()

    // Core Messaging State
    private val _chats = MutableStateFlow<List<TelegramChatModel>>(emptyList())
    val chats: StateFlow<List<TelegramChatModel>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<Map<Long, List<TelegramMessageModel>>>(emptyMap())
    val messages: StateFlow<Map<Long, List<TelegramMessageModel>>> = _messages.asStateFlow()

    private val _publicSearchChats = MutableStateFlow<List<TelegramChatModel>>(emptyList())
    val publicSearchChats: StateFlow<List<TelegramChatModel>> = _publicSearchChats.asStateFlow()
    @Volatile private var activePublicSearchToken: String? = null

    private val _contacts = MutableStateFlow<List<ContactModel>>(emptyList())
    val contacts: StateFlow<List<ContactModel>> = _contacts.asStateFlow()

    private val _calls = MutableStateFlow<List<CallHistoryItem>>(emptyList())
    val calls: StateFlow<List<CallHistoryItem>> = _calls.asStateFlow()

    private val _stories = MutableStateFlow<List<StoryItem>>(emptyList())
    val stories: StateFlow<List<StoryItem>> = _stories.asStateFlow()

    private val _activeChat = MutableStateFlow<TelegramChatModel?>(null)
    val activeChat: StateFlow<TelegramChatModel?> = _activeChat.asStateFlow()

    // Multi-Account & Profile State
    private val _accounts = MutableStateFlow<List<AccountProfile>>(emptyList())
    val accounts: StateFlow<List<AccountProfile>> = _accounts.asStateFlow()

    private val _currentUserName = MutableStateFlow("Telegram User")
    val currentUserName: StateFlow<String> = _currentUserName.asStateFlow()

    private val _currentUserPhone = MutableStateFlow("")
    val currentUserPhone: StateFlow<String> = _currentUserPhone.asStateFlow()

    private val _currentUserUsername = MutableStateFlow("")
    val currentUserUsername: StateFlow<String> = _currentUserUsername.asStateFlow()

    private val _currentUserBio = MutableStateFlow("")
    val currentUserBio: StateFlow<String> = _currentUserBio.asStateFlow()
    private var currentTelegramUserId: Long? = null
    private val pendingGroupMembers = ConcurrentHashMap<String, List<Long>>()

    private val _connectionStatus = MutableStateFlow("Connecting…")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _rustEngineVersion = MutableStateFlow("RustGram Core")
    val rustEngineVersion: StateFlow<String> = _rustEngineVersion.asStateFlow()

    // Voice Recording & Playback State
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    private var recordingJob: Job? = null
    private val recordedPeaks = mutableListOf<Float>()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _playingMessageId = MutableStateFlow<Long?>(null)
    val playingMessageId: StateFlow<Long?> = _playingMessageId.asStateFlow()

    init {
        initRustCore()
        listenToUpdates()
        loadInitialState()
        startSecretMessageExpiryTicker()
    }

    private fun appendTdLog(tag: String, message: String, isError: Boolean = false) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = TdLogEntry(ts, tag, message, isError)
        _tdLogEntries.value = (_tdLogEntries.value + entry).takeLast(40)
    }

    private fun initRustCore() {
        if (NativeBridge.isRustCoreLoaded) {
            try {
                NativeBridge.initCore(getApiId(), tdClient.getRustDbFilePath())
                _rustEngineVersion.value = NativeBridge.getCoreVersion()
                _connectionStatus.value = if (tdClient.isNativeTdLibLinked) {
                    "TDLib + Rust Core"
                } else {
                    "Local Rust Engine"
                }
                appendTdLog("RUST", "Core initialized • TDLib linked=${tdClient.isNativeTdLibLinked}")
            } catch (e: Throwable) {
                appendTdLog("RUST", "Init failed: ${e.message}", isError = true)
            }
        } else {
            appendTdLog("JNI", "librustgram_integration.so unavailable, fallback mode", isError = true)
        }
    }

    // ========================================================================
    // Language, Theme Constructor & Passcode Lock
    // ========================================================================

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        prefs.edit().putString(KEY_LANGUAGE, lang.code).apply()
    }

    fun setThemePreset(preset: TelegramThemePreset) {
        _themePreset.value = preset
        _isDarkMode.value = (preset == TelegramThemePreset.TINTED_DARK || preset == TelegramThemePreset.OLED_NIGHT)
        prefs.edit().putString(KEY_THEME_PRESET, preset.name).apply()
    }

    fun toggleDarkMode() {
        val next = if (_isDarkMode.value) TelegramThemePreset.DAY else TelegramThemePreset.TINTED_DARK
        setThemePreset(next)
    }

    fun setAccentIndex(index: Int) {
        _accentIndex.value = index
        prefs.edit().putInt(KEY_ACCENT_INDEX, index).apply()
    }

    fun setWallpaperStyle(style: WallpaperStyle) {
        _wallpaperStyle.value = style
        prefs.edit().putString(KEY_WALLPAPER_STYLE, style.name).apply()
    }

    private fun derivePasscodeKey(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, PASSCODE_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodePasscodeHash(pin: String): String {
        val salt = ByteArray(PASSCODE_SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derivePasscodeKey(pin, salt, PASSCODE_ITERATIONS)
        val encoder = Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        return listOf(
            PASSCODE_KDF,
            PASSCODE_ITERATIONS.toString(),
            Base64.encodeToString(salt, encoder),
            Base64.encodeToString(derived, encoder)
        ).joinToString("$")
    }

    // Compatibility only: verify old installations, then upgrade to PBKDF2 after a valid unlock.
    private fun legacyPasscodeHash(pin: String): String {
        if (NativeBridge.isRustCoreLoaded) {
            runCatching {
                NativeBridge.computePasswordHash(pin, "rustgram_pin_s1", "rustgram_pin_s2")
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("rustgram_pin_$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun setPasscode(pin: String) {
        require(pin.length in 4..128) { "Passcode must contain 4 to 128 characters" }
        prefs.edit().putString(KEY_PASSCODE_HASH, encodePasscodeHash(pin)).apply()
        _hasPasscode.value = true
    }

    fun verifyPasscode(pin: String): Boolean {
        val saved = prefs.getString(KEY_PASSCODE_HASH, null) ?: return true
        val parts = saved.split('$')
        val matches = if (parts.size == 4 && parts[0] == PASSCODE_KDF) {
            runCatching {
                val iterations = parts[1].toInt().coerceIn(100_000, 1_000_000)
                val decoder = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                val salt = Base64.decode(parts[2], decoder)
                val expected = Base64.decode(parts[3], decoder)
                val actual = derivePasscodeKey(pin, salt, iterations)
                salt.size == PASSCODE_SALT_BYTES && MessageDigest.isEqual(expected, actual)
            }.getOrDefault(false)
        } else {
            MessageDigest.isEqual(
                saved.toByteArray(Charsets.UTF_8),
                legacyPasscodeHash(pin).toByteArray(Charsets.UTF_8)
            )
        }
        if (matches) {
            if (parts.size != 4 || parts[0] != PASSCODE_KDF) setPasscode(pin)
            _isAppLocked.value = false
        }
        return matches
    }

    fun clearPasscode() {
        prefs.edit().remove(KEY_PASSCODE_HASH).apply()
        _hasPasscode.value = false
        _isAppLocked.value = false
    }

    fun lockApp() {
        if (_hasPasscode.value) {
            _isAppLocked.value = true
        }
    }

    // ========================================================================
    // Multi-Account & Profile Editing
    // ========================================================================

    fun switchAccount(accountId: Int) {
        val target = _accounts.value.find { it.id == accountId } ?: return
        _accounts.value = _accounts.value.map { it.copy(isActive = it.id == accountId) }
        _currentUserName.value = target.name
        _currentUserPhone.value = target.phone
        _currentUserUsername.value = target.username
        _currentUserBio.value = target.bio
    }

    fun addAccount(name: String, phone: String, username: String) {
        if (_accounts.value.size >= 3) return
        val newId = (_accounts.value.maxOfOrNull { it.id } ?: 0) + 1
        val handle = if (username.startsWith("@")) username else "@$username"
        val newAcc = AccountProfile(
            id = newId,
            name = name.ifBlank { "Telegram User" },
            username = handle,
            phone = phone.ifBlank { "+1 202 555 0100" },
            bio = "RustGram Account #$newId",
            avatarColorIndex = newId % 5,
            isActive = true
        )
        _accounts.value = _accounts.value.map { it.copy(isActive = false) } + newAcc
        switchAccount(newId)
    }

    fun updateActiveProfile(name: String, username: String, bio: String, avatarColorIndex: Int) {
        val handle = if (username.isBlank()) "" else if (username.startsWith("@")) username else "@$username"
        _currentUserName.value = name
        _currentUserUsername.value = handle
        _currentUserBio.value = bio
        _accounts.value = _accounts.value.map { acc ->
            if (acc.isActive) acc.copy(name = name, username = handle, bio = bio, avatarColorIndex = avatarColorIndex)
            else acc
        }
        if (tdClient.isNativeTdLibLinked && !_isDemoMode.value) {
            val parts = name.trim().split(" ", limit = 2)
            tdClient.send(JSONObject().apply {
                put("@type", "setName")
                put("first_name", parts.firstOrNull() ?: name)
                put("last_name", parts.getOrNull(1) ?: "")
            })
            tdClient.send(JSONObject().apply {
                put("@type", "setBio")
                put("bio", bio)
            })
        }
    }

    // ========================================================================
    // API Credentials & TDLib Lifecycle
    // ========================================================================

    fun getApiId(): Int = prefs.getInt(KEY_API_ID, 0)

    fun getApiHash(): String = try {
        secretStore.get(KEY_API_HASH).orEmpty()
    } catch (error: Exception) {
        Log.e(TAG, "Could not decrypt the Telegram API Hash", error)
        ""
    }

    fun saveApiCredentials(apiId: Int, apiHash: String) {
        require(apiId > 0) { "Telegram API ID must be a positive integer" }
        require(apiHash.matches(Regex("[0-9a-fA-F]{32}"))) { "Telegram API Hash must be 32 hexadecimal characters" }
        // API Hash is encrypted with an AES key held by Android Keystore.
        try {
            secretStore.put(KEY_API_HASH, apiHash)
        } catch (error: Exception) {
            Log.e(TAG, "Could not encrypt Telegram API credentials", error)
            _authError.value = "Не удалось сохранить API Hash в Android Keystore. Проверь защиту устройства и повтори попытку."
            appendTdLog("SECURITY", "API credential encryption failed; TDLib was not initialized", isError = true)
            return
        }
        // Keep the encrypted value written by KeystoreSecretStore. Removing this key
        // here used to erase the API Hash immediately after encrypting it.
        prefs.edit()
            .putInt(KEY_API_ID, apiId)
            .apply()
        _authError.value = null
        sendTdlibParameters()
    }

    private fun loadInitialState() {
        val savedPhone = prefs.getString(KEY_PHONE, null)
        if (!savedPhone.isNullOrEmpty()) _currentUserPhone.value = savedPhone

        // Never infer a valid Telegram session from a SharedPreferences flag. TDLib's
        // authorizationState updates are the source of truth for the real account.
        _authState.value = TdAuthState.Initializing
        if (!tdClient.isNativeTdLibLinked) {
            _authState.value = TdAuthState.WaitPhoneNumber
            _connectionStatus.value = "TDLib native library unavailable"
            _authError.value = "Нативная TDLib не загружена. Реальный вход недоступен; установи сборку с рабочими libtdjson.so и Rust JNI."
        } else {
            appendTdLog("AUTH", "Waiting for TDLib authorization state")
        }
    }

    private fun sendTdlibParameters() {
        val apiId = getApiId()
        val apiHash = getApiHash()
        if (apiId <= 0 || !apiHash.matches(Regex("[0-9a-fA-F]{32}"))) {
            _authError.value = "Сначала укажи Telegram API ID и API Hash в настройках подключения."
            return
        }
        val databaseDirectory = tdClient.getDatabaseDirectory()
        val databaseEncryptionKey = try {
            val existingKey = secretStore.get(KEY_TDLIB_DATABASE_KEY)
            val hasExistingDatabase = File(databaseDirectory).listFiles()?.isNotEmpty() == true
            when {
                existingKey != null -> existingKey
                hasExistingDatabase -> null // Preserve access to an older unencrypted TDLib database.
                else -> secretStore.getOrCreateBase64(KEY_TDLIB_DATABASE_KEY, 32)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not access the TDLib database encryption key", error)
            _authError.value = "Не удалось открыть защищённое хранилище. Не удаляй данные приложения; проверь резервную копию и доступ к Android Keystore."
            appendTdLog("SECURITY", "Keystore error; TDLib initialization stopped", isError = true)
            return
        }
        if (databaseEncryptionKey == null) {
            appendTdLog(
                "SECURITY",
                "Legacy TDLib database detected; keeping compatibility. New local database encryption requires a clean database reset after backup.",
                isError = true
            )
        }
        val params = JSONObject().apply {
            put("@type", "setTdlibParameters")
            put("use_test_dc", false)
            put("database_directory", databaseDirectory)
            put("files_directory", tdClient.getFilesDirectory())
            put("use_file_database", true)
            if (databaseEncryptionKey != null) put("database_encryption_key", databaseEncryptionKey)
            put("use_chat_info_database", true)
            put("use_message_database", true)
            put("use_secret_chats", true)
            put("enable_storage_optimizer", true)
            put("api_id", apiId)
            put("api_hash", apiHash)
            put("system_language_code", _language.value.code)
            put("device_model", Build.MODEL ?: "Honor 600 Lite")
            put("system_version", "Android ${Build.VERSION.RELEASE}")
            put("application_version", "2.0.0-RustGram")
        }
        appendTdLog("TDLIB", "→ setTdlibParameters (api_id=$apiId, device=${Build.MODEL})")
        tdClient.send(params)
    }

    private fun listenToUpdates() {
        scope.launch {
            tdClient.updates.collect { update ->
                handleUpdate(update)
            }
        }
    }

    private fun handleUpdate(update: JSONObject) {
        val type = update.optString("@type")
        if (type.isBlank()) return
        Log.d(TAG, "TDLib update: $type")

        when (type) {
            "ok" -> {
                val extra = update.optString("@extra")
                if (extra.startsWith("rustgram-delete-chat-")) {
                    val chatId = extra.removePrefix("rustgram-delete-chat-").toLongOrNull()
                    if (chatId != null) {
                        _chats.value = _chats.value.filterNot { it.id == chatId }
                        _messages.value = _messages.value - chatId
                        if (_activeChat.value?.id == chatId) _activeChat.value = null
                        appendTdLog("CHATS", "Chat history deleted in TDLib")
                    }
                }
            }
            "error" -> {
                val extra = update.optString("@extra")
                if (extra.startsWith("rustgram-proxy-")) return
                if (extra.startsWith("rustgram-public-search-") || extra.startsWith("rustgram-search-chat-")) {
                    appendTdLog("SEARCH", "Public chat search failed: ${update.optString("message", "TDLib error")}", isError = true)
                    return
                }
                if (extra.startsWith("rustgram-delete-chat-")) {
                    appendTdLog("CHATS", "Chat history was not deleted: ${update.optString("message", "TDLib error")}", isError = true)
                    return
                }
                val code = update.optInt("code", 0)
                val msg = update.optString("message", "Unknown TDLib error")
                _isAuthBusy.value = false
                _authError.value = "[$code] $msg"
                appendTdLog("ERROR", "← [$code] $msg", isError = true)
            }
            "updateAuthorizationState" -> {
                val stateObj = update.optJSONObject("authorization_state")
                val stateType = stateObj?.optString("@type") ?: ""
                appendTdLog("AUTH", "← $stateType")
                _isAuthBusy.value = false
                _authError.value = null

                when (stateType) {
                    "authorizationStateWaitTdlibParameters" -> sendTdlibParameters()
                    "authorizationStateWaitPhoneNumber" -> _authState.value = TdAuthState.WaitPhoneNumber
                    "authorizationStateWaitCode" -> {
                        val phone = prefs.getString(KEY_PHONE, "") ?: ""
                        val codeInfo = stateObj?.optJSONObject("code_info")
                        val len = codeInfo?.optJSONObject("type")?.optInt("length", 5) ?: 5
                        val timeout = codeInfo?.optInt("timeout", 60) ?: 60
                        _authState.value = TdAuthState.WaitCode(phoneNumber = phone, codeLength = len, timeoutSec = timeout)
                    }
                    "authorizationStateWaitPassword" -> {
                        val hint = stateObj?.optString("password_hint")?.takeIf { it.isNotBlank() }
                        _authState.value = TdAuthState.WaitPassword(hint = hint)
                    }
                    "authorizationStateWaitOtherDeviceConfirmation" -> {
                        val link = stateObj?.optString("link") ?: ""
                        _authState.value = TdAuthState.WaitOtherDeviceConfirmation(link)
                    }
                    "authorizationStateReady" -> {
                        _isDemoMode.value = false
                        _chats.value = emptyList()
                        _messages.value = emptyMap()
                        _contacts.value = emptyList()
                        _calls.value = emptyList()
                        _stories.value = emptyList()
                        _accounts.value = emptyList()
                        _authState.value = TdAuthState.Ready
                        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
                        tdClient.send(JSONObject().put("@type", "getMe").put("@extra", "rustgram-me"))
                        tdClient.send(JSONObject().put("@type", "getContacts").put("@extra", "rustgram-contacts"))
                        loadTelegramChats()
                    }
                    "authorizationStateLoggingOut" -> _authState.value = TdAuthState.LoggingOut
                    "authorizationStateClosed" -> _authState.value = TdAuthState.Closed
                }
            }
            "updateConnectionState" -> {
                val connState = update.optJSONObject("state")?.optString("@type") ?: ""
                appendTdLog("NET", "← $connState")
                _connectionStatus.value = when (connState) {
                    "connectionStateReady" -> "MTProto Ready"
                    "connectionStateConnectingToProxy" -> "Connecting to proxy…"
                    "connectionStateConnecting" -> "Connecting…"
                    "connectionStateUpdating" -> "Updating…"
                    "connectionStateWaitingForNetwork" -> "Waiting for network…"
                    else -> connState.ifBlank { "TDLib connection state unknown" }
                }
            }
            "user" -> {
                val extra = update.optString("@extra")
                val userId = update.optLong("id")
                if (extra == "rustgram-me") {
                    currentTelegramUserId = userId.takeIf { it != 0L }
                    val first = update.optString("first_name")
                    val last = update.optString("last_name")
                    val full = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
                    if (full.isNotBlank()) _currentUserName.value = full
                    val phone = update.optString("phone_number")
                    if (phone.isNotBlank()) _currentUserPhone.value = "+${phone.removePrefix("+")}"
                    val username = update.optString("username")
                    if (username.isNotBlank()) _currentUserUsername.value = "@$username"
                } else if (extra.startsWith("rustgram-contact-") && userId != 0L) {
                    val name = listOf(update.optString("first_name"), update.optString("last_name"))
                        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Telegram user" }
                    val statusType = update.optJSONObject("status")?.optString("@type").orEmpty()
                    val usernames = update.optJSONObject("usernames")
                    val rawUsername = update.optString("username").takeIf { it.isNotBlank() }
                        ?: usernames?.optString("editable_username")?.takeIf { it.isNotBlank() }.orEmpty()
                    val contact = ContactModel(
                        id = userId,
                        name = name,
                        username = if (rawUsername.isBlank() || rawUsername.startsWith("@")) rawUsername else "@$rawUsername",
                        phone = update.optString("phone_number"),
                        bio = "",
                        isOnline = statusType == "userStatusOnline",
                        lastSeen = if (statusType == "userStatusOnline") "online" else "offline"
                    )
                    _contacts.value = (_contacts.value.filterNot { it.id == userId } + contact).sortedBy { it.name.lowercase() }
                }
            }
            "messages" -> {
                val values = update.optJSONArray("messages") ?: return
                for (index in values.length() - 1 downTo 0) {
                    val message = values.optJSONObject(index) ?: continue
                    parseAndAppendMessage(message, replaceExisting = true)
                }
            }
            "users" -> {
                val ids = update.optJSONArray("user_ids") ?: return
                for (index in 0 until ids.length()) {
                    val userId = ids.optLong(index)
                    if (userId <= 0L) continue
                    tdClient.send(
                        JSONObject().put("@type", "getUser")
                            .put("user_id", userId)
                            .put("@extra", "rustgram-contact-$userId")
                    )
                }
                appendTdLog("CONTACTS", "← Requested ${ids.length()} Telegram contact profiles")
            }
            "updateUser" -> {
                val userId = update.optLong("user_id")
                if (userId > 0L && userId != currentTelegramUserId) {
                    tdClient.send(
                        JSONObject().put("@type", "getUser")
                            .put("user_id", userId)
                            .put("@extra", "rustgram-contact-$userId")
                    )
                }
            }
            "chats" -> {
                val ids = update.optJSONArray("chat_ids") ?: return
                val responseExtra = update.optString("@extra")
                val searchToken = responseExtra.removePrefix("rustgram-public-search-")
                    .takeIf { responseExtra.startsWith("rustgram-public-search-") }
                if (searchToken == null) appendTdLog("CHATS", "← Synced ${ids.length()} chats")
                for (i in 0 until ids.length()) {
                    val cid = ids.optLong(i)
                    if (cid == 0L) continue
                    tdClient.send(JSONObject().apply {
                        put("@type", "getChat")
                        put("chat_id", cid)
                        if (searchToken != null) put("@extra", "rustgram-search-chat-$searchToken:$cid")
                    })
                }
            }
            "updateNewChat", "chat" -> {
                val chatObj = if (type == "chat") update else update.optJSONObject("chat") ?: return
                val id = chatObj.optLong("id")
                val title = chatObj.optString("title", "Telegram Chat")
                val typeObj = chatObj.optJSONObject("type")
                val isSecret = typeObj?.optString("@type") == "chatTypeSecret"
                val typeStr = when (typeObj?.optString("@type")) {
                    "chatTypeBasicGroup" -> "group"
                    "chatTypeSupergroup" -> if (typeObj.optBoolean("is_channel")) "channel" else "supergroup"
                    "chatTypeSecret" -> "secret"
                    else -> "private"
                }
                val unread = chatObj.optInt("unread_count", 0)
                val lastMsgText = chatObj.optJSONObject("last_message")
                    ?.optJSONObject("content")
                    ?.optJSONObject("text")
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() } ?: "Synced from Telegram"
                val model = TelegramChatModel(
                    id = id,
                    title = title,
                    type = typeStr,
                    lastMessageText = lastMsgText,
                    lastMessageTime = formatCurrentTime(),
                    unreadCount = unread,
                    isSecret = isSecret,
                    selfDestructSec = chatObj.optInt("message_auto_delete_time", 0),
                    category = when (typeStr) {
                        "channel" -> ChatCategory.CHANNELS
                        "group", "supergroup" -> ChatCategory.GROUPS
                        "secret" -> ChatCategory.SECRET
                        else -> ChatCategory.PERSONAL
                    }
                )
                val responseExtra = chatObj.optString("@extra")
                if (responseExtra.startsWith("rustgram-search-chat-")) {
                    val queryToken = responseExtra.removePrefix("rustgram-search-chat-").substringBefore(':')
                    if (queryToken == activePublicSearchToken) {
                        _publicSearchChats.value = (_publicSearchChats.value.filterNot { it.id == model.id } + model)
                            .sortedBy { it.title.lowercase() }
                    }
                } else {
                    upsertChat(model)
                }
                val members = pendingGroupMembers.remove(responseExtra).orEmpty()
                if (id != 0L && members.isNotEmpty()) {
                    val memberPayload = JSONObject().put("chat_id", id).put("user_ids", JSONArray(members))
                    val memberRequest = buildRustMessagingRequest("add_chat_members", memberPayload)
                        ?: JSONObject().put("@type", "addChatMembers").put("chat_id", id)
                            .put("user_ids", JSONArray(members)).put("forward_limit", 0)
                    tdClient.send(memberRequest)
                }
            }
            "updateChatLastMessage" -> {
                val chatId = update.optLong("chat_id")
                val lastObj = update.optJSONObject("last_message") ?: return
                val text = lastObj.optJSONObject("content")?.optJSONObject("text")?.optString("text") ?: return
                _chats.value = _chats.value.map {
                    if (it.id == chatId) it.copy(lastMessageText = text, lastMessageTime = formatCurrentTime()) else it
                }
            }
            "updateNewMessage" -> {
                val msgObj = update.optJSONObject("message") ?: return
                // TDLib can also return the same message as a sendMessage response.
                // Upsert by (chat_id, message_id) so the UI never shows duplicates.
                parseAndAppendMessage(msgObj, replaceExisting = true)
            }
            "updateMessageSendSucceeded" -> {
                val msgObj = update.optJSONObject("message") ?: return
                val chatId = msgObj.optLong("chat_id")
                val oldMessageId = update.optLong("old_message_id")
                if (chatId != 0L && oldMessageId != 0L) {
                    val current = _messages.value.toMutableMap()
                    current[chatId] = current[chatId].orEmpty().filterNot { it.id == oldMessageId }
                    _messages.value = current
                }
                parseAndAppendMessage(msgObj, replaceExisting = true)
            }
            "updateChatPosition" -> {
                val chatId = update.optLong("chat_id")
                val position = update.optJSONObject("position") ?: return
                val listType = position.optJSONObject("list")?.optString("@type").orEmpty()
                val isPinned = position.optBoolean("is_pinned", false)
                val order = position.optString("order", "0")
                _chats.value = _chats.value.map { chat ->
                    if (chat.id != chatId) chat else {
                        val archiveState = if (listType == "chatListArchive") order != "0" else chat.isArchived
                        chat.copy(isPinned = isPinned, isArchived = archiveState)
                    }
                }.sortedByDescending { it.isPinned }
            }
            "updateChatNotificationSettings" -> {
                val chatId = update.optLong("chat_id")
                val settings = update.optJSONObject("notification_settings") ?: return
                val muted = !settings.optBoolean("use_default_mute_for", true) && settings.optInt("mute_for", 0) > 0
                _chats.value = _chats.value.map { if (it.id == chatId) it.copy(isMuted = muted) else it }
                if (_activeChat.value?.id == chatId) _activeChat.value = _activeChat.value?.copy(isMuted = muted)
            }
            "updateChatPinnedMessage" -> {
                val chatId = update.optLong("chat_id")
                val pinnedId = update.optLong("pinned_message_id").takeIf { it > 0L }
                val pinnedText = pinnedId?.let { id -> _messages.value[chatId]?.firstOrNull { it.id == id }?.text }
                _chats.value = _chats.value.map { if (it.id == chatId) it.copy(pinnedMessageText = pinnedText) else it }
                if (_activeChat.value?.id == chatId) _activeChat.value = _activeChat.value?.copy(pinnedMessageText = pinnedText)
            }
            "updateDeleteMessages" -> {
                val chatId = update.optLong("chat_id")
                val ids = update.optJSONArray("message_ids") ?: return
                val deleted = buildSet { for (i in 0 until ids.length()) add(ids.optLong(i)) }
                val current = _messages.value.toMutableMap()
                current[chatId] = current[chatId].orEmpty().filterNot { it.id in deleted }
                _messages.value = current
                if (NativeBridge.isRustCoreLoaded) deleted.forEach { runCatching { NativeBridge.deleteMessageFromDb(chatId, it) } }
            }
            "updateMessageIsPinned" -> {
                val chatId = update.optLong("chat_id")
                val messageId = update.optLong("message_id")
                val isPinned = update.optBoolean("is_pinned", false)
                if (isPinned) {
                    val text = _messages.value[chatId]?.firstOrNull { it.id == messageId }?.text
                    _chats.value = _chats.value.map { if (it.id == chatId) it.copy(pinnedMessageText = text) else it }
                    if (_activeChat.value?.id == chatId) _activeChat.value = _activeChat.value?.copy(pinnedMessageText = text)
                }
            }
            "updateChatMessageAutoDeleteTime" -> {
                val chatId = update.optLong("chat_id")
                val ttl = update.optInt("message_auto_delete_time", 0)
                _chats.value = _chats.value.map { if (it.id == chatId) it.copy(selfDestructSec = ttl) else it }
                if (_activeChat.value?.id == chatId) _activeChat.value = _activeChat.value?.copy(selfDestructSec = ttl)
            }
            "message" -> {
                // Direct response to sendMessage/forwardMessages.
                if (update.has("chat_id") && update.has("id")) {
                    parseAndAppendMessage(update, replaceExisting = true)
                }
            }
            "updateMessageContent" -> {
                val chatId = update.optLong("chat_id")
                val messageId = update.optLong("message_id")
                val existing = _messages.value[chatId]?.firstOrNull { it.id == messageId } ?: return
                val newContent = update.optJSONObject("new_content") ?: return
                val synthetic = JSONObject().apply {
                    put("chat_id", chatId)
                    put("id", messageId)
                    put("is_outgoing", existing.isOutgoing)
                    put("date", existing.epochSec)
                    put("content", newContent)
                }
                parseAndAppendMessage(synthetic, replaceExisting = true)
            }
        }
    }

    private fun upsertChat(chat: TelegramChatModel) {
        val current = _chats.value.toMutableList()
        val idx = current.indexOfFirst { it.id == chat.id }
        if (idx >= 0) {
            current[idx] = chat
        } else {
            current.add(0, chat)
        }
        _chats.value = current.sortedByDescending { it.isPinned }
        // RustGram's secondary SQLite cache is not encrypted yet; TDLib remains the canonical store.
    }

    fun getWorldCountries(): List<CountryItem> {
        if (NativeBridge.isRustCoreLoaded) {
            try {
                val json = NativeBridge.getCountriesJson()
                val arr = JSONArray(json)
                if (arr.length() > 0) {
                    val result = ArrayList<CountryItem>(arr.length())
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        result.add(
                            CountryItem(
                                name = obj.optString("name"),
                                code = obj.optString("country_code"),
                                dialCode = obj.optString("calling_code"),
                                flag = obj.optString("flag_emoji")
                            )
                        )
                    }
                    return result
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Rust getCountriesJson fallback: ${e.message}")
            }
        }
        return WorldCountries.all
    }

    fun matchCountryByDialCode(dialCode: String): CountryItem? {
        if (NativeBridge.isRustCoreLoaded) {
            try {
                val json = NativeBridge.matchCountryByDialCode(dialCode)
                if (json.isNotBlank()) {
                    val obj = JSONObject(json)
                    return CountryItem(
                        name = obj.optString("name"),
                        code = obj.optString("country_code"),
                        dialCode = obj.optString("calling_code"),
                        flag = obj.optString("flag_emoji")
                    )
                }
            } catch (_: Throwable) {}
        }
        return WorldCountries.matchByDialCode(dialCode)
    }

    fun submitPhoneNumber(phoneNumber: String) {
        prefs.edit().putString(KEY_PHONE, phoneNumber).apply()
        _currentUserPhone.value = phoneNumber
        _authError.value = null

        val req = JSONObject().apply {
            put("@type", "setAuthenticationPhoneNumber")
            put("phone_number", phoneNumber)
        }
        appendTdLog("AUTH", "→ setAuthenticationPhoneNumber (phone hidden)")

        if (tdClient.isNativeTdLibLinked && !_isDemoMode.value) {
            _isAuthBusy.value = true
            tdClient.send(req)
        } else {
            _authError.value = "TDLib не подключена. Номер не отправлен, код подтверждения не запрашивался."
            _authState.value = TdAuthState.WaitPhoneNumber
        }
    }

    fun submitVerificationCode(code: String) {
        _authError.value = null
        val req = JSONObject().apply {
            put("@type", "checkAuthenticationCode")
            put("code", code)
        }
        appendTdLog("AUTH", "→ checkAuthenticationCode (***)")

        if (tdClient.isNativeTdLibLinked && !_isDemoMode.value) {
            _isAuthBusy.value = true
            tdClient.send(req)
        } else {
            _authError.value = "TDLib не подключена. Код не проверялся. Используй отдельный демо-вход только для просмотра интерфейса."
            _authState.value = TdAuthState.WaitPhoneNumber
        }
    }

    fun submit2FaPassword(password: String) {
        _authError.value = null
        val req = JSONObject().apply {
            put("@type", "checkAuthenticationPassword")
            put("password", password)
        }
        appendTdLog("AUTH", "→ checkAuthenticationPassword")

        if (tdClient.isNativeTdLibLinked && !_isDemoMode.value) {
            _isAuthBusy.value = true
            tdClient.send(req)
        } else {
            _authError.value = "TDLib не подключена. Пароль 2FA не проверялся."
            _authState.value = TdAuthState.WaitPhoneNumber
        }
    }

    fun requestQrCodeAuth() {
        _authError.value = null
        appendTdLog("AUTH", "→ requestQrCodeAuthentication")
        if (tdClient.isNativeTdLibLinked) {
            tdClient.send(JSONObject().put("@type", "requestQrCodeAuthentication"))
        }
    }

    fun enterDemoMode() {
        _isDemoMode.value = true
        _isAuthBusy.value = false
        _authError.value = null
        _currentUserName.value = "Pavel Rust (Demo)"
        _currentUserPhone.value = ""
        _currentUserUsername.value = "@rustgram_demo"
        _currentUserBio.value = "Демонстрационный профиль — не аккаунт Telegram"
        _accounts.value = defaultAccounts
        _contacts.value = defaultContacts
        _calls.value = defaultCallHistory
        _stories.value = defaultStories
        appendTdLog("DEMO", "Offline demo mode activated; no Telegram account was authenticated")
        _authState.value = TdAuthState.Ready
        seedDefaultChatsIfEmpty()
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun backToPhoneStep() {
        _isAuthBusy.value = false
        _authError.value = null
        _authState.value = TdAuthState.WaitPhoneNumber
    }

    fun logOut() {
        if (tdClient.isNativeTdLibLinked && !_isDemoMode.value) {
            tdClient.send(JSONObject().put("@type", "logOut"))
        }
        _isDemoMode.value = false
        _isAuthBusy.value = false
        _authError.value = null
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
        _authState.value = TdAuthState.WaitPhoneNumber
        _chats.value = emptyList()
        _messages.value = emptyMap()
        _contacts.value = emptyList()
        _calls.value = emptyList()
        _stories.value = emptyList()
        _accounts.value = emptyList()
        currentTelegramUserId = null
    }

    private fun loadTelegramChats() {
        val req = JSONObject().apply {
            put("@type", "getChats")
            put("limit", 50)
        }
        tdClient.send(req)
    }

    private fun parseAndAppendMessage(msgObj: JSONObject, replaceExisting: Boolean = false) {
        val chatId = msgObj.optLong("chat_id")
        val id = msgObj.optLong("id")
        if (chatId == 0L || id == 0L) return
        val isOut = msgObj.optBoolean("is_outgoing")
        val contentObj = msgObj.optJSONObject("content") ?: return
        val contentType = contentObj.optString("@type")
        val textObject = contentObj.optJSONObject("text")
        val rawText = textObject?.optString("text").orEmpty()
        var mediaType = MediaType.NONE
        var pollData: PollData? = null
        val displayText = when (contentType) {
            "messageText" -> rawText
            "messagePoll" -> {
                pollData = parsePollData(contentObj.optJSONObject("poll"))
                mediaType = MediaType.POLL
                pollData?.question?.let { "📊 $it" } ?: "📊 Poll"
            }
            "messagePhoto" -> { mediaType = MediaType.PHOTO; "📷 Photo" }
            "messageVideo" -> { mediaType = MediaType.VIDEO; "📹 Video" }
            "messageDocument" -> {
                mediaType = MediaType.DOCUMENT
                contentObj.optJSONObject("document")?.optString("file_name")?.takeIf { it.isNotBlank() }?.let { "📎 $it" } ?: "📎 Document"
            }
            "messageVoiceNote", "messageAudio" -> { mediaType = MediaType.VOICE; "🎤 Voice message" }
            "messageVideoNote" -> { mediaType = MediaType.VIDEO_NOTE; "⭕ Video message" }
            "messageSticker" -> { mediaType = MediaType.STICKER; "Sticker" }
            else -> rawText
        }
        if (displayText.isBlank()) return
        val (cleanText, entities) = parseTextWithRust(displayText)
        val date = msgObj.optLong("date", System.currentTimeMillis() / 1000L)
        val newMsg = TelegramMessageModel(
            id = id,
            chatId = chatId,
            senderName = if (isOut) "You" else "Telegram",
            isOutgoing = isOut,
            text = cleanText,
            timestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date * 1000L)),
            epochSec = date,
            isRead = true,
            mediaType = mediaType,
            pollData = pollData,
            entities = entities
        )
        if (replaceExisting) upsertMessageLocal(chatId, newMsg) else appendMessageLocal(chatId, newMsg)
    }

    private fun parsePollData(poll: JSONObject?): PollData? {
        poll ?: return null
        val question = poll.optJSONObject("question")?.optString("text").orEmpty()
        val optionArray = poll.optJSONArray("options") ?: return null
        val selected = linkedSetOf<Int>()
        val options = buildList {
            for (index in 0 until optionArray.length()) {
                val option = optionArray.optJSONObject(index) ?: continue
                if (option.optBoolean("is_chosen")) selected += index
                add(PollOption(
                    text = option.optJSONObject("text")?.optString("text") ?: option.optString("text"),
                    votes = option.optInt("voter_count", 0)
                ))
            }
        }
        if (question.isBlank() || options.size < 2) return null
        val type = poll.optJSONObject("type")
        val typeName = type?.optString("@type").orEmpty()
        return PollData(
            question = question,
            options = options,
            isQuiz = typeName == "pollTypeQuiz",
            correctOptionIndex = type?.optInt("correct_option_id")?.takeIf { typeName == "pollTypeQuiz" && it >= 0 },
            allowMultiple = typeName == "pollTypeRegular" && (type?.optBoolean("allow_multiple_answers") == true),
            selectedIndices = selected,
            isAnonymous = poll.optBoolean("is_anonymous", true)
        )
    }

    private fun upsertMessageLocal(chatId: Long, msg: TelegramMessageModel) {
        val currentMap = _messages.value.toMutableMap()
        val list = currentMap[chatId]?.toMutableList() ?: mutableListOf()
        val existingIndex = list.indexOfFirst { it.id == msg.id }
        if (existingIndex >= 0) list[existingIndex] = msg else list.add(msg)
        currentMap[chatId] = list.sortedBy { it.epochSec }
        _messages.value = currentMap
        val preview = when (msg.mediaType) {
            MediaType.POLL -> "📊 ${msg.pollData?.question ?: msg.text}"
            else -> msg.text
        }
        _chats.value = _chats.value.map { chat ->
            if (chat.id == chatId) chat.copy(lastMessageText = preview, lastMessageTime = msg.timestamp) else chat
        }
    }

    fun parseTextWithRust(rawInput: String): Pair<String, List<TextEntitySpan>> {
        if (!NativeBridge.isRustCoreLoaded) return rawInput to emptyList()
        return try {
            val jsonStr = NativeBridge.parseMarkdown(rawInput)
            val obj = JSONObject(jsonStr)
            val cleanText = obj.optString("text", rawInput)
            val arr = obj.optJSONArray("entities") ?: JSONArray()
            val spans = mutableListOf<TextEntitySpan>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val offset = item.optInt("offset", 0)
                val length = item.optInt("length", 0)
                val rawType = item.opt("entity_type")
                val typeName = when (rawType) {
                    is String -> rawType
                    is JSONObject -> rawType.keys().asSequence().firstOrNull() ?: "Plain"
                    else -> "Plain"
                }
                spans.add(TextEntitySpan(offset, length, typeName))
            }
            cleanText to spans
        } catch (_: Throwable) {
            rawInput to emptyList()
        }
    }

    private fun buildRustMessagingRequest(operation: String, payload: JSONObject): JSONObject? {
        val raw = runCatching {
            NativeBridge.buildMessagingRequest(operation, payload.toString())
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    // ========================================================================
    // Chats, Stories, Archive, Secret Chats, Polls & Scheduled Messages
    // ========================================================================

    fun setActiveChat(chat: TelegramChatModel) {
        _activeChat.value = chat
        _chats.value = _chats.value.map {
            if (it.id == chat.id) it.copy(unreadCount = 0) else it
        }
        tdClient.send(JSONObject().apply {
            put("@type", "openChat")
            put("chat_id", chat.id)
        })
        if (_messages.value[chat.id].isNullOrEmpty()) loadChatHistory(chat.id)
    }

    fun loadChatHistory(chatId: Long, fromMessageId: Long = 0L, limit: Int = 50) {
        if (chatId == 0L || !tdClient.isNativeTdLibLinked) return
        tdClient.send(JSONObject().apply {
            put("@type", "getChatHistory")
            put("chat_id", chatId)
            put("from_message_id", fromMessageId)
            put("offset", 0)
            put("limit", limit.coerceIn(1, 100))
            put("only_local", false)
            put("@extra", "rustgram-history-$chatId-${System.nanoTime()}")
        })
    }

    fun clearActiveChat() {
        _activeChat.value?.let { chat ->
            tdClient.send(JSONObject().apply {
                put("@type", "closeChat")
                put("chat_id", chat.id)
            })
        }
        _activeChat.value = null
    }

    fun markStoryViewed(storyId: Long) {
        if (!_isDemoMode.value) {
            appendTdLog("STORIES", "Story viewing is not connected to TDLib yet", isError = true)
            return
        }
        _stories.value = _stories.value.map {
            if (it.id == storyId) it.copy(isViewed = true) else it
        }
    }

    fun addOwnStory(text: String) {
        if (text.isBlank()) return
        if (!_isDemoMode.value) {
            appendTdLog("STORIES", "Publishing stories is not connected to TDLib yet", isError = true)
            return
        }
        val newSlide = StorySlide(
            id = System.currentTimeMillis(),
            text = text.trim(),
            startColorHex = 0xFF007AFF,
            endColorHex = 0xFF34AADC,
            timestamp = formatCurrentTime()
        )
        val existingOwn = _stories.value.find { it.isOwnStory }
        if (existingOwn != null) {
            _stories.value = _stories.value.map {
                if (it.isOwnStory) it.copy(slides = it.slides + newSlide, isViewed = false) else it
            }
        } else {
            val own = StoryItem(
                id = 999L,
                authorName = _currentUserName.value,
                chatId = 101L,
                avatarColorIndex = 0,
                isViewed = false,
                isOwnStory = true,
                slides = listOf(newSlide)
            )
            _stories.value = listOf(own) + _stories.value
        }
    }

    fun toggleArchiveChat(chatId: Long) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        val archived = !chat.isArchived
        if (_isDemoMode.value) {
            _chats.value = _chats.value.map { if (it.id == chatId) it.copy(isArchived = archived) else it }
            return
        }
        if (!tdClient.isNativeTdLibLinked) {
            appendTdLog("CHATS", "TDLib unavailable; archive state was not changed", isError = true)
            return
        }
        val payload = JSONObject().put("chat_id", chatId).put("archived", archived)
        val request = buildRustMessagingRequest("set_chat_archived", payload) ?: JSONObject().apply {
            put("@type", if (archived) "addChatToList" else "removeChatFromList")
            put("chat_id", chatId)
            put("chat_list", JSONObject().put("@type", "chatListArchive"))
        }
        request.put("@extra", "rustgram-archive-$chatId")
        if (!tdClient.send(request)) appendTdLog("CHATS", "Archive request was not sent to TDLib", isError = true)
        // The chat list is updated only from TDLib's updateChatPosition events.
    }

    fun openOrCreateContactChat(contact: ContactModel): TelegramChatModel? {
        if (_isDemoMode.value) {
            val existing = _chats.value.find { it.id == contact.id && !it.isSecret }
            if (existing != null) return existing
            val demoChat = TelegramChatModel(
                id = contact.id,
                title = contact.name,
                type = "private",
                lastMessageText = "Start conversation",
                lastMessageTime = "Now",
                isOnline = contact.isOnline,
                username = contact.username,
                bio = contact.bio,
                phoneNumber = contact.phone,
                category = ChatCategory.PERSONAL
            )
            upsertChat(demoChat)
            return demoChat
        }

        // A Telegram user ID is not a chat ID. Ask TDLib to resolve/create the real private chat.
        val payload = JSONObject().put("user_id", contact.id)
        val request = buildRustMessagingRequest("create_private_chat", payload)
            ?: JSONObject().put("@type", "createPrivateChat").put("user_id", contact.id).put("force", false)
        request.put("@extra", "rustgram-private-${contact.id}")
        val sent = tdClient.send(request)
        if (!sent) appendTdLog("CHATS", "Cannot open ${contact.name}: TDLib is unavailable", isError = true)
        return null // The real Chat arrives asynchronously and is inserted by handleUpdate.
    }

    fun createSecretChat(contact: ContactModel): TelegramChatModel? {
        if (_isDemoMode.value) {
            val existing = _chats.value.find { it.isSecret && it.title == contact.name }
            if (existing != null) return existing
            val secretId = System.currentTimeMillis()
            val demoChat = TelegramChatModel(
                id = secretId,
                title = contact.name,
                type = "secret",
                lastMessageText = "Demo secret chat (not encrypted by Telegram)",
                lastMessageTime = formatCurrentTime(),
                isOnline = contact.isOnline,
                isSecret = true,
                selfDestructSec = 15,
                username = contact.username,
                bio = contact.bio,
                phoneNumber = contact.phone,
                category = ChatCategory.SECRET
            )
            upsertChat(demoChat)
            appendMessageLocal(secretId, TelegramMessageModel(
                id = secretId + 1,
                chatId = secretId,
                senderName = "RustGram demo",
                isOutgoing = true,
                text = "Demo only. This is not a Telegram end-to-end encrypted chat.",
                timestamp = formatCurrentTime(),
                isRead = true
            ))
            return demoChat
        }

        val request = buildRustMessagingRequest("create_secret_chat", JSONObject().put("user_id", contact.id))
            ?: JSONObject().put("@type", "createNewSecretChat").put("user_id", contact.id)
        request.put("@extra", "rustgram-secret-${contact.id}")
        val sent = tdClient.send(request)
        if (!sent) appendTdLog("SECRET", "Secret chat request was not sent: TDLib unavailable", isError = true)
        return null // Never invent a chat ID or claim encryption before TDLib returns a real secret chat.
    }

    fun setSecretSelfDestructTimer(chatId: Long, seconds: Int) {
        if (!_isDemoMode.value) {
            val valid = seconds.coerceIn(0, 365 * 24 * 60 * 60)
            val payload = JSONObject().put("chat_id", chatId).put("seconds", valid)
            val request = buildRustMessagingRequest("set_chat_ttl", payload)
                ?: JSONObject().put("@type", "setChatMessageAutoDeleteTime").put("chat_id", chatId).put("message_auto_delete_time", valid)
            val sent = tdClient.send(request)
            if (!sent) appendTdLog("SECRET", "TDLib did not accept the self-destruct timer change", isError = true)
            return // The authoritative updateChatMessageTtl will update UI state.
        }
        _chats.value = _chats.value.map { if (it.id == chatId) it.copy(selfDestructSec = seconds) else it }
        if (_activeChat.value?.id == chatId) _activeChat.value = _activeChat.value?.copy(selfDestructSec = seconds)
    }

    private fun startSecretMessageExpiryTicker() {
        scope.launch {
            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                var changed = false
                val updated = _messages.value.mapValues { (chatId, list) ->
                    val expired = list.filter { it.expiresAtEpochMs != null && now >= it.expiresAtEpochMs }
                    if (expired.isNotEmpty()) {
                        changed = true
                        if (NativeBridge.isRustCoreLoaded) {
                            expired.forEach { NativeBridge.deleteMessageFromDb(chatId, it.id) }
                        }
                        list.filterNot { it.expiresAtEpochMs != null && now >= it.expiresAtEpochMs }
                    } else list
                }
                if (changed) {
                    _messages.value = updated
                }
            }
        }
    }

    fun createGroupOrChannel(
        title: String,
        description: String,
        isChannel: Boolean,
        memberCount: Int,
        memberIds: List<Long> = emptyList()
    ): TelegramChatModel? {
        if (!_isDemoMode.value) {
            val requestToken = "rustgram-create-${System.nanoTime()}"
            if (memberIds.isNotEmpty()) pendingGroupMembers[requestToken] = memberIds.distinct().filter { it > 0L }
            val payload = JSONObject().apply {
                put("title", title.trim())
                put("description", description)
                put("is_channel", isChannel)
            }
            val request = buildRustMessagingRequest("create_supergroup", payload)
                ?: JSONObject().apply {
                    put("@type", "createNewSupergroupChat")
                    put("title", title.trim().take(128))
                    put("is_forum", false)
                    put("is_channel", isChannel)
                    put("description", description.take(255))
                    put("location", JSONObject.NULL)
                    put("message_auto_delete_time", 0)
                    put("for_import", false)
                }
            request.put("@extra", requestToken)
            val sent = tdClient.send(request)
            if (!sent) {
                pendingGroupMembers.remove(requestToken)
                appendTdLog("CHATS", "Create group/channel request was not sent: TDLib unavailable", isError = true)
            }
            // TDLib's Chat response/update is authoritative; no synthetic chat is inserted.
            return null
        }

        val newId = System.currentTimeMillis()
        val type = if (isChannel) "channel" else "supergroup"
        val demoChat = TelegramChatModel(
            id = newId,
            title = title,
            type = type,
            lastMessageText = if (isChannel) "Demo channel created" else "Demo group created",
            lastMessageTime = formatCurrentTime(),
            bio = description,
            memberCount = memberCount.coerceAtLeast(1),
            category = if (isChannel) ChatCategory.CHANNELS else ChatCategory.GROUPS
        )
        upsertChat(demoChat)
        appendMessageLocal(newId, TelegramMessageModel(
            id = newId + 1,
            chatId = newId,
            senderName = "RustGram demo",
            isOutgoing = true,
            text = "Demo only: this group/channel does not exist on Telegram.",
            timestamp = formatCurrentTime(),
            isRead = true
        ))
        return demoChat
    }

    fun sendMessage(
        chatId: Long,
        rawText: String,
        replyToId: Long? = null,
        isSilent: Boolean = false,
        scheduleDelaySec: Int = 0
    ) {
        val (cleanText, entities) = parseTextWithRust(rawText)
        if (cleanText.isBlank()) return
        val chat = _chats.value.find { it.id == chatId }
        val expiryMs = if (chat?.isSecret == true && chat.selfDestructSec > 0 && scheduleDelaySec == 0) {
            System.currentTimeMillis() + chat.selfDestructSec * 1000L
        } else null
        val localPreview = TelegramMessageModel(
            id = System.currentTimeMillis(),
            chatId = chatId,
            senderName = "You",
            isOutgoing = true,
            text = cleanText,
            timestamp = formatCurrentTime(),
            isRead = false,
            isSilent = isSilent,
            expiresAtEpochMs = expiryMs,
            entities = entities,
            replyToMessage = replyToId?.let { id -> _messages.value[chatId]?.find { it.id == id } }
        )

        // Only the deliberately selected offline demo mode may create local fixture messages.
        if (_isDemoMode.value) {
            if (scheduleDelaySec > 0) {
                val scheduled = localPreview.copy(scheduledForEpochMs = System.currentTimeMillis() + scheduleDelaySec * 1000L)
                appendMessageLocal(chatId, scheduled)
                scope.launch {
                    delay(scheduleDelaySec * 1000L)
                    val currentMap = _messages.value.toMutableMap()
                    currentMap[chatId] = currentMap[chatId]?.map {
                        if (it.id == scheduled.id) it.copy(
                            scheduledForEpochMs = null,
                            timestamp = formatCurrentTime(),
                            isRead = true
                        ) else it
                    } ?: emptyList()
                    _messages.value = currentMap
                }
            } else {
                appendMessageLocal(chatId, localPreview)
            }
            return
        }

        if (scheduleDelaySec < 0 || scheduleDelaySec > 367 * 24 * 60 * 60) {
            appendTdLog("MESSAGE", "Schedule must be between now and 367 days from now", isError = true)
            return
        }
        if (!tdClient.isNativeTdLibLinked) {
            appendTdLog("MESSAGE", "TDLib unavailable; message was not sent", isError = true)
            return
        }
        if (!dispatchTextMessageToTdLib(chatId, rawText, isSilent, replyToId, scheduleDelaySec)) {
            appendTdLog("MESSAGE", "TDLib did not accept the send request", isError = true)
        }
        // The authoritative message ID/content arrives from TDLib's message update.
        // Do not insert a synthetic local message here; that caused duplicates and false success.
    }

    private fun dispatchTextMessageToTdLib(
        chatId: Long,
        rawText: String,
        isSilent: Boolean,
        replyToId: Long?,
        scheduleDelaySec: Int = 0
    ): Boolean {
        val scheduledAt = if (scheduleDelaySec > 0) System.currentTimeMillis() / 1000L + scheduleDelaySec else null
        val payload = JSONObject().apply {
            put("chat_id", chatId)
            put("raw_text", rawText)
            put("disable_notification", isSilent)
            replyToId?.let { put("reply_to_message_id", it) }
            scheduledAt?.let { put("send_at_unix_sec", it) }
        }
        val rustJson = runCatching {
            NativeBridge.buildMessagingRequest("send_text", payload.toString())
        }.getOrNull()
        val parsedRustRequest = rustJson?.takeIf { it.isNotBlank() }?.let {
            runCatching { JSONObject(it) }.getOrNull()
        }
        val rustSupportsSchedule = scheduledAt == null ||
            parsedRustRequest?.optJSONObject("options")?.optJSONObject("scheduling_state")?.optString("@type") == "messageSchedulingStateSendAtDate"
        val request = parsedRustRequest?.takeIf { rustSupportsSchedule } ?: run {
            val (fallbackText, _) = parseTextWithRust(rawText)
            JSONObject().apply {
                put("@type", "sendMessage")
                put("chat_id", chatId)
                put("options", JSONObject().apply {
                    put("@type", "messageSendOptions")
                    put("disable_notification", isSilent)
                    put("scheduling_state", scheduledAt?.let {
                        JSONObject().put("@type", "messageSchedulingStateSendAtDate")
                            .put("send_date", it).put("repeat_period", 0)
                    } ?: JSONObject.NULL)
                })
                put("topic_id", JSONObject.NULL)
                if (replyToId != null && replyToId > 0L) {
                    put("reply_to", JSONObject().apply {
                        put("@type", "inputMessageReplyToMessage")
                        put("message_id", replyToId)
                        put("quote", JSONObject.NULL)
                        put("checklist_task_id", 0)
                    })
                } else {
                    put("reply_to", JSONObject.NULL)
                }
                put("input_message_content", JSONObject().apply {
                    put("@type", "inputMessageText")
                    put("text", JSONObject().apply {
                        put("@type", "formattedText")
                        put("text", fallbackText)
                        put("entities", JSONArray())
                    })
                })
            }
        }
        return tdClient.send(request)
    }

    fun sendPollMessage(
        chatId: Long,
        question: String,
        options: List<String>,
        isQuiz: Boolean,
        correctOptionIndex: Int?,
        allowMultiple: Boolean
    ) {
        val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }
        val cleanQuestion = question.trim()
        if (cleanQuestion.isEmpty() || cleanOptions.size !in 2..10 || cleanOptions.any { it.length > 100 }) {
            appendTdLog("POLL", "Poll requires a question and 2–10 options of at most 100 characters", isError = true)
            return
        }
        if (isQuiz && (correctOptionIndex == null || correctOptionIndex !in cleanOptions.indices)) {
            appendTdLog("POLL", "Select a valid correct answer for the quiz", isError = true)
            return
        }
        if (_isDemoMode.value) {
            val poll = PollData(
                question = cleanQuestion,
                options = cleanOptions.map { PollOption(it, 0) },
                isQuiz = isQuiz,
                correctOptionIndex = if (isQuiz) correctOptionIndex else null,
                allowMultiple = allowMultiple && !isQuiz
            )
            appendMessageLocal(chatId, TelegramMessageModel(
                id = System.currentTimeMillis(), chatId = chatId, senderName = "You",
                isOutgoing = true, text = "📊 ${poll.question}", timestamp = formatCurrentTime(),
                isRead = true, mediaType = MediaType.POLL, pollData = poll
            ))
            return
        }

        val payload = JSONObject().apply {
            put("chat_id", chatId)
            put("question", cleanQuestion)
            put("options", JSONArray(cleanOptions))
            put("is_quiz", isQuiz)
            if (correctOptionIndex != null) put("correct_option_id", correctOptionIndex)
            put("allow_multiple_answers", allowMultiple && !isQuiz)
        }
        val rustJson = runCatching {
            NativeBridge.buildMessagingRequest("send_poll", payload.toString())
        }.getOrNull()
        val request = rustJson?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: createPollRequest(chatId, cleanQuestion, cleanOptions, isQuiz, correctOptionIndex, allowMultiple)
        if (request == null || !tdClient.send(request)) {
            appendTdLog("POLL", "TDLib rejected or could not receive the poll request", isError = true)
        }
    }

    private fun createPollRequest(
        chatId: Long,
        question: String,
        options: List<String>,
        isQuiz: Boolean,
        correctOptionIndex: Int?,
        allowMultiple: Boolean
    ): JSONObject? {
        val type = if (isQuiz) {
            val correct = correctOptionIndex?.takeIf { it in options.indices } ?: return null
            JSONObject().put("@type", "pollTypeQuiz")
                .put("correct_option_id", correct)
                .put("explanation", JSONObject().put("@type", "formattedText").put("text", "").put("entities", JSONArray()))
        } else {
            JSONObject().put("@type", "pollTypeRegular")
                .put("allow_multiple_answers", allowMultiple)
        }
        val formattedOptions = JSONArray().apply {
            options.forEach { option ->
                put(JSONObject().put("@type", "formattedText").put("text", option).put("entities", JSONArray()))
            }
        }
        return JSONObject().apply {
            put("@type", "sendMessage")
            put("chat_id", chatId)
            put("options", JSONObject().put("@type", "messageSendOptions").put("disable_notification", false))
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessagePoll")
                put("question", JSONObject().put("@type", "formattedText").put("text", question).put("entities", JSONArray()))
                put("options", formattedOptions)
                put("is_anonymous", true)
                put("type", type)
                put("open_period", 0)
                put("close_date", 0)
                put("is_closed", false)
            })
        }
    }

    fun votePoll(chatId: Long, messageId: Long, optionIndex: Int) {
        if (!_isDemoMode.value) {
            val poll = _messages.value[chatId]?.firstOrNull { it.id == messageId }?.pollData
            if (poll == null || optionIndex !in poll.options.indices) {
                appendTdLog("POLL", "Poll is not synchronized from TDLib; vote was not sent", isError = true)
                return
            }
            val selected = if (poll.allowMultiple) {
                if (poll.selectedIndices.contains(optionIndex)) poll.selectedIndices - optionIndex else poll.selectedIndices + optionIndex
            } else if (poll.isQuiz && poll.selectedIndices.isNotEmpty()) {
                poll.selectedIndices
            } else {
                setOf(optionIndex)
            }
            val request = JSONObject().apply {
                put("@type", "setPollAnswer")
                put("chat_id", chatId)
                put("message_id", messageId)
                put("option_ids", JSONArray().apply { selected.sorted().forEach { put(it) } })
            }
            if (!tdClient.send(request)) appendTdLog("POLL", "TDLib did not receive the vote", isError = true)
            return
        }
        val currentMap = _messages.value.toMutableMap()
        val list = currentMap[chatId]?.map { msg ->
            val poll = msg.pollData
            if (msg.id == messageId && poll != null && optionIndex in poll.options.indices) {
                val updatedSelected = if (poll.allowMultiple) {
                    if (poll.selectedIndices.contains(optionIndex)) poll.selectedIndices - optionIndex
                    else poll.selectedIndices + optionIndex
                } else {
                    if (poll.selectedIndices.isNotEmpty() && poll.isQuiz) {
                        poll.selectedIndices // Quiz answer locked once selected
                    } else {
                        setOf(optionIndex)
                    }
                }
                val updatedOptions = poll.options.mapIndexed { idx, opt ->
                    val wasSelected = poll.selectedIndices.contains(idx)
                    val nowSelected = updatedSelected.contains(idx)
                    val delta = when {
                        !wasSelected && nowSelected -> 1
                        wasSelected && !nowSelected -> -1
                        else -> 0
                    }
                    opt.copy(votes = (opt.votes + delta).coerceAtLeast(0))
                }
                msg.copy(
                    pollData = poll.copy(
                        options = updatedOptions,
                        selectedIndices = updatedSelected
                    )
                )
            } else msg
        } ?: emptyList()
        currentMap[chatId] = list
        _messages.value = currentMap
    }

    fun sendMediaMessage(
        chatId: Long,
        mediaType: MediaType,
        mediaUri: String? = null,
        caption: String = "",
        fileName: String? = null,
        fileSize: String? = null
    ) {
        if (_isDemoMode.value) {
            val msgId = System.currentTimeMillis()
            val summary = when (mediaType) {
                MediaType.PHOTO -> if (caption.isBlank()) "📷 Demo photo" else caption
                MediaType.VIDEO -> if (caption.isBlank()) "📹 Demo video" else caption
                MediaType.DOCUMENT -> fileName ?: "Demo document"
                MediaType.VIDEO_NOTE -> "⭕ Demo video message"
                MediaType.STICKER -> caption.ifBlank { "Demo sticker" }
                else -> caption
            }
            appendMessageLocal(chatId, TelegramMessageModel(
                id = msgId,
                chatId = chatId,
                senderName = "You (demo)",
                isOutgoing = true,
                text = summary,
                timestamp = formatCurrentTime(),
                isRead = false,
                mediaType = mediaType,
                mediaUri = mediaUri,
                fileName = fileName,
                fileSize = fileSize
            ))
            return
        }

        // The current composer supplies a real content URI for photos. Other media types
        // remain disabled until their picker/recorder and download lifecycle are implemented.
        if (mediaType != MediaType.PHOTO || mediaUri.isNullOrBlank()) {
            appendTdLog("MEDIA", "This media type is not connected to real Telegram upload yet; no message was fabricated", isError = true)
            return
        }
        scope.launch {
            val localFile = runCatching {
                val uri = Uri.parse(mediaUri)
                val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open selected photo")
                val output = File(context.cacheDir, "rustgram_upload_${System.currentTimeMillis()}.img")
                input.use { source -> output.outputStream().use { destination -> source.copyTo(destination) } }
                output.takeIf { it.isFile && it.length() > 0L } ?: error("Selected photo is empty")
            }.getOrElse {
                appendTdLog("MEDIA", "Photo preparation failed: ${it.message ?: "unknown error"}", isError = true)
                return@launch
            }
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("file_path", localFile.absolutePath)
                put("caption", caption)
            }
            val rustJson = runCatching {
                NativeBridge.buildMessagingRequest("send_photo", payload.toString())
            }.getOrNull()
            val request = rustJson?.takeIf { it.isNotBlank() }?.let {
                runCatching { JSONObject(it) }.getOrNull()
            } ?: JSONObject().apply {
                put("@type", "sendMessage")
                put("chat_id", chatId)
                put("reply_to", JSONObject.NULL)
                put("options", JSONObject().put("@type", "messageSendOptions").put("disable_notification", false))
                put("input_message_content", JSONObject().apply {
                    put("@type", "inputMessagePhoto")
                    put("photo", JSONObject().put("@type", "inputFileLocal").put("path", localFile.absolutePath))
                    put("thumbnail", JSONObject.NULL)
                    put("added_sticker_file_ids", JSONArray())
                    put("width", 0)
                    put("height", 0)
                    put("caption", JSONObject().put("@type", "formattedText").put("text", caption).put("entities", JSONArray()))
                    put("show_caption_above_media", false)
                    put("self_destruct_type", JSONObject.NULL)
                })
            }
            if (!tdClient.send(request)) appendTdLog("MEDIA", "TDLib did not accept the photo upload request", isError = true)
        }
    }

    fun editMessage(chatId: Long, messageId: Long, newRawText: String) {
        val (cleanText, entities) = parseTextWithRust(newRawText)
        if (cleanText.isBlank()) return
        if (_isDemoMode.value) {
            val currentMap = _messages.value.toMutableMap()
            currentMap[chatId] = currentMap[chatId]?.map { msg ->
                if (msg.id == messageId) msg.copy(text = cleanText, entities = entities, isEdited = true) else msg
            } ?: emptyList()
            _messages.value = currentMap
            return
        }

        val payload = JSONObject().apply {
            put("chat_id", chatId)
            put("message_id", messageId)
            put("raw_text", newRawText)
        }
        val rustJson = runCatching {
            NativeBridge.buildMessagingRequest("edit_text", payload.toString())
        }.getOrNull()
        val request = rustJson?.takeIf { it.isNotBlank() }?.let {
            runCatching { JSONObject(it) }.getOrNull()
        } ?: JSONObject().apply {
            put("@type", "editMessageText")
            put("chat_id", chatId)
            put("message_id", messageId)
            put("reply_markup", JSONObject.NULL)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", JSONObject().put("@type", "formattedText").put("text", cleanText).put("entities", JSONArray()))
                put("link_preview_options", JSONObject.NULL)
                put("clear_draft", false)
            })
        }
        if (!tdClient.send(request)) appendTdLog("MESSAGE", "TDLib did not accept the edit request", isError = true)
        // Keep the old message visible until TDLib confirms the updated content.
    }

    fun forwardMessage(targetChatId: Long, msg: TelegramMessageModel) {
        if (_isDemoMode.value) {
            val newMsg = msg.copy(
                id = System.currentTimeMillis(), chatId = targetChatId, senderName = "You",
                isOutgoing = true, forwardedFrom = msg.senderName, timestamp = formatCurrentTime(), isRead = false
            )
            appendMessageLocal(targetChatId, newMsg)
            return
        }
        val payload = JSONObject().apply {
            put("chat_id", targetChatId)
            put("from_chat_id", msg.chatId)
            put("message_id", msg.id)
            put("send_copy", false)
            put("remove_caption", false)
            put("disable_notification", false)
        }
        val rustJson = runCatching {
            NativeBridge.buildMessagingRequest("forward", payload.toString())
        }.getOrNull()
        val request = rustJson?.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: JSONObject().apply {
                put("@type", "forwardMessages")
                put("chat_id", targetChatId)
                put("topic_id", JSONObject.NULL)
                put("from_chat_id", msg.chatId)
                put("message_ids", JSONArray().put(msg.id))
                put("options", JSONObject().put("@type", "messageSendOptions").put("disable_notification", false))
                put("send_copy", false)
                put("remove_caption", false)
            }
        if (!tdClient.send(request)) appendTdLog("MESSAGE", "TDLib did not receive the forward request", isError = true)
    }

    fun pinMessage(chatId: Long, msg: TelegramMessageModel) {
        if (_isDemoMode.value) {
            _chats.value = _chats.value.map { if (it.id == chatId) it.copy(pinnedMessageText = msg.text) else it }
            if (_activeChat.value?.id == chatId) _activeChat.value = _activeChat.value?.copy(pinnedMessageText = msg.text)
            return
        }
        val payload = JSONObject().put("chat_id", chatId).put("message_id", msg.id)
        val request = buildRustMessagingRequest("pin_message", payload)
            ?: JSONObject().apply {
                put("@type", "pinChatMessage")
                put("chat_id", chatId)
                put("message_id", msg.id)
                put("disable_notification", false)
                put("only_for_self", false)
            }
        if (!tdClient.send(request)) appendTdLog("MESSAGE", "Pin request was not sent to TDLib", isError = true)
        // The pinned-message banner changes only after TDLib confirms the pin update.
    }

    // ========================================================================
    // Real Voice Recording & Playback + Rust Waveform Normalization
    // ========================================================================

    fun startVoiceRecording() {
        if (_isRecordingVoice.value) return
        _isRecordingVoice.value = true
        _recordingDurationSec.value = 0
        recordedPeaks.clear()

        val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        recordingFile = outFile

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
        } catch (e: Throwable) {
            Log.w(TAG, "Hardware MediaRecorder unavailable: ${e.message}")
            mediaRecorder = null
        }

        recordingJob?.cancel()
        recordingJob = scope.launch {
            var ticks = 0
            while (isActive && _isRecordingVoice.value) {
                val amp = try {
                    val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                    if (maxAmp > 0) (maxAmp / 32767f).coerceIn(0.1f, 1f)
                    else (0.25f + ((ticks % 5) * 0.15f))
                } catch (_: Throwable) {
                    0.4f
                }
                recordedPeaks.add(amp)
                ticks++
                if (ticks % 10 == 0) {
                    _recordingDurationSec.value += 1
                }
                delay(100)
            }
        }
    }

    fun cancelVoiceRecording() {
        _isRecordingVoice.value = false
        recordingJob?.cancel()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Throwable) {}
        mediaRecorder = null
        recordingFile?.delete()
        recordingFile = null
    }

    fun stopAndSendVoiceRecording(chatId: Long) {
        if (!_isRecordingVoice.value) return
        _isRecordingVoice.value = false
        recordingJob?.cancel()
        val duration = _recordingDurationSec.value.coerceAtLeast(1)
        var savedPath: String? = null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            savedPath = recordingFile?.takeIf { it.exists() && it.length() > 0 }?.absolutePath
        } catch (_: Throwable) {}
        mediaRecorder = null

        val rawPeaks = if (recordedPeaks.isEmpty()) floatArrayOf(0.2f, 0.5f, 0.9f, 0.4f, 0.7f) else recordedPeaks.toFloatArray()
        val normalizedWaveform = if (NativeBridge.isRustCoreLoaded) {
            NativeBridge.normalizeWaveform(rawPeaks, 24).toList()
        } else {
            List(24) { idx -> rawPeaks[idx % rawPeaks.size] }
        }

        if (!_isDemoMode.value) {
            val filePath = savedPath?.takeIf { File(it).isFile && File(it).length() > 0L }
            if (filePath == null) {
                appendTdLog("VOICE", "Recording produced no readable audio file; nothing was sent", isError = true)
                return
            }
            val waveformBytes = packVoiceWaveform(normalizedWaveform)
            val waveformBase64 = Base64.encodeToString(waveformBytes, Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("chat_id", chatId)
                put("file_path", filePath)
                put("duration", duration)
                put("waveform", waveformBase64)
            }
            val rustRequest = runCatching {
                NativeBridge.buildMessagingRequest("send_voice", payload.toString())
            }.getOrNull()
            val request = rustRequest?.takeIf { it.isNotBlank() }?.let {
                runCatching { JSONObject(it) }.getOrNull()
            } ?: JSONObject().apply {
                put("@type", "sendMessage")
                put("chat_id", chatId)
                put("options", JSONObject().put("@type", "messageSendOptions").put("disable_notification", false))
                put("input_message_content", JSONObject().apply {
                    put("@type", "inputMessageVoiceNote")
                    put("voice_note", JSONObject().put("@type", "inputFileLocal").put("path", filePath))
                    put("duration", duration)
                    put("waveform", waveformBase64)
                    put("caption", JSONObject.NULL)
                    put("self_destruct_type", JSONObject.NULL)
                })
            }
            if (!tdClient.send(request)) {
                appendTdLog("VOICE", "TDLib did not accept the voice message; recording kept in app cache", isError = true)
            }
            return
        }

        val demoMsg = TelegramMessageModel(
            id = System.currentTimeMillis(),
            chatId = chatId,
            senderName = "You (demo)",
            isOutgoing = true,
            text = "Demo voice message",
            timestamp = formatCurrentTime(),
            isRead = false,
            mediaType = MediaType.VOICE,
            voiceDurationSec = duration,
            voiceFilePath = savedPath,
            voiceWaveform = normalizedWaveform
        )
        appendMessageLocal(chatId, demoMsg)
    }

    private fun packVoiceWaveform(samples: List<Float>): ByteArray {
        if (samples.isEmpty()) return byteArrayOf()
        val values = samples.map { (it.coerceIn(0f, 1f) * 31f).toInt().coerceIn(0, 31) }
        val output = ByteArray((values.size * 5 + 7) / 8)
        values.forEachIndexed { index, value ->
            val startBit = index * 5
            for (bit in 0 until 5) {
                if ((value and (1 shl bit)) != 0) {
                    val targetBit = startBit + bit
                    val byteIndex = targetBit / 8
                    val bitIndex = targetBit % 8
                    output[byteIndex] = (output[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
            }
        }
        return output
    }

    fun toggleVoicePlayback(message: TelegramMessageModel) {
        if (_playingMessageId.value == message.id) {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Throwable) {}
            mediaPlayer = null
            _playingMessageId.value = null
            return
        }

        try {
            mediaPlayer?.release()
        } catch (_: Throwable) {}
        mediaPlayer = null

        _playingMessageId.value = message.id
        val filePath = message.voiceFilePath
        if (!filePath.isNullOrEmpty() && File(filePath).exists()) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    setOnCompletionListener {
                        _playingMessageId.value = null
                        it.release()
                        mediaPlayer = null
                    }
                    start()
                }
                mediaPlayer = mp
                return
            } catch (_: Throwable) {}
        }

        scope.launch {
            val ms = (message.voiceDurationSec.coerceAtLeast(2) * 1000L)
            delay(ms)
            if (_playingMessageId.value == message.id) {
                _playingMessageId.value = null
            }
        }
    }

    fun deleteMessage(chatId: Long, messageId: Long) {
        if (_isDemoMode.value) {
            val currentMap = _messages.value.toMutableMap()
            currentMap[chatId] = currentMap[chatId].orEmpty().filterNot { it.id == messageId }
            _messages.value = currentMap
            return
        }
        val payload = JSONObject().put("chat_id", chatId).put("message_id", messageId).put("revoke", true)
        val request = buildRustMessagingRequest("delete_message", payload)
            ?: JSONObject().apply {
                put("@type", "deleteMessages")
                put("chat_id", chatId)
                put("message_ids", JSONArray().put(messageId))
                put("revoke", true)
            }
        if (!tdClient.send(request)) appendTdLog("MESSAGE", "Delete request was not sent to TDLib", isError = true)
        // The local list/cache is changed only after updateDeleteMessages arrives.
    }

    fun toggleReaction(chatId: Long, messageId: Long, emoji: String) {
        val message = _messages.value[chatId]?.firstOrNull { it.id == messageId } ?: return
        val alreadySelected = message.reactions.firstOrNull { it.emoji == emoji }?.isSelected == true
        if (_isDemoMode.value) {
            val currentMap = _messages.value.toMutableMap()
            currentMap[chatId] = currentMap[chatId].orEmpty().map { msg ->
                if (msg.id == messageId) {
                    val reactions = msg.reactions.map { it.copy() }.toMutableList()
                    val existing = reactions.find { it.emoji == emoji }
                    if (existing != null) {
                        if (existing.isSelected) {
                            existing.isSelected = false
                            existing.count = (existing.count - 1).coerceAtLeast(0)
                        } else {
                            existing.isSelected = true
                            existing.count += 1
                        }
                    } else reactions.add(ReactionItem(emoji = emoji, count = 1, isSelected = true))
                    msg.copy(reactions = reactions.filter { it.count > 0 })
                } else msg
            }
            _messages.value = currentMap
            return
        }
        val operation = if (alreadySelected) "remove_reaction" else "add_reaction"
        val payload = JSONObject().put("chat_id", chatId).put("message_id", messageId).put("emoji", emoji)
        val request = buildRustMessagingRequest(operation, payload) ?: JSONObject().apply {
            put("@type", if (alreadySelected) "removeMessageReaction" else "addMessageReaction")
            put("chat_id", chatId)
            put("message_id", messageId)
            put("reaction_type", JSONObject().put("@type", "reactionTypeEmoji").put("emoji", emoji))
            if (!alreadySelected) {
                put("is_big", false)
                put("update_recent_reactions", true)
            }
        }
        if (!tdClient.send(request)) appendTdLog("MESSAGE", "Reaction request was not sent to TDLib", isError = true)
        // The reaction chip is rendered from TDLib's subsequent message/update data.
    }

    fun pinChat(chatId: Long) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        if (_isDemoMode.value) {
            _chats.value = _chats.value.map { if (it.id == chatId) it.copy(isPinned = !it.isPinned) else it }
                .sortedByDescending { it.isPinned }
            return
        }
        val payload = JSONObject().apply {
            put("chat_id", chatId)
            put("list", if (chat.isArchived) "archive" else "main")
            put("is_pinned", !chat.isPinned)
        }
        val request = buildRustMessagingRequest("pin_chat", payload)
            ?: JSONObject().apply {
                put("@type", "toggleChatIsPinned")
                put("chat_list", JSONObject().put("@type", if (chat.isArchived) "chatListArchive" else "chatListMain"))
                put("chat_id", chatId)
                put("is_pinned", !chat.isPinned)
            }
        if (!tdClient.send(request)) appendTdLog("CHATS", "Pin state request was not sent to TDLib", isError = true)
    }

    fun toggleMuteChat(chatId: Long) {
        val chat = _chats.value.firstOrNull { it.id == chatId } ?: return
        if (_isDemoMode.value) {
            _chats.value = _chats.value.map { if (it.id == chatId) it.copy(isMuted = !it.isMuted) else it }
            if (_activeChat.value?.id == chatId) _activeChat.value = _activeChat.value?.copy(isMuted = !chat.isMuted)
            return
        }
        val payload = JSONObject().put("chat_id", chatId).put("muted", !chat.isMuted)
        val request = buildRustMessagingRequest("set_chat_mute", payload) ?: JSONObject().apply {
            val muted = !chat.isMuted
            put("@type", "setChatNotificationSettings")
            put("chat_id", chatId)
            put("notification_settings", JSONObject().apply {
                put("@type", "chatNotificationSettings")
                put("use_default_mute_for", false)
                put("mute_for", if (muted) 31_536_000 else 0)
                put("use_default_sound", true)
                put("sound_id", 0)
                put("use_default_show_preview", true)
                put("show_preview", true)
                put("use_default_mute_stories", true)
                put("mute_stories", false)
                put("use_default_story_sound", true)
                put("story_sound_id", 0)
                put("use_default_show_story_poster", true)
                put("show_story_poster", true)
                put("use_default_disable_pinned_message_notifications", true)
                put("disable_pinned_message_notifications", false)
                put("use_default_disable_mention_notifications", true)
                put("disable_mention_notifications", false)
            })
        }
        if (!tdClient.send(request)) appendTdLog("CHATS", "Mute request was not sent to TDLib", isError = true)
    }

    fun deleteChat(chatId: Long) {
        if (_isDemoMode.value) {
            _chats.value = _chats.value.filterNot { it.id == chatId }
            _messages.value = _messages.value - chatId
            return
        }
        if (!tdClient.isNativeTdLibLinked) {
            appendTdLog("CHATS", "TDLib unavailable; chat history was not deleted", isError = true)
            return
        }
        val payload = JSONObject().put("chat_id", chatId)
            .put("remove_from_list", true)
            .put("revoke", false)
        val request = buildRustMessagingRequest("delete_chat_history", payload)
            ?: JSONObject().apply {
                put("@type", "deleteChatHistory")
                put("chat_id", chatId)
                put("remove_from_list", true)
                put("revoke", false)
            }
        request.put("@extra", "rustgram-delete-chat-$chatId")
        if (!tdClient.send(request)) appendTdLog("CHATS", "TDLib did not accept the delete history request", isError = true)
        // Keep the chat visible until TDLib confirms the list-position / history updates.
    }

    /** Search the Telegram public-chat directory. Results are loaded asynchronously via TDLib. */
    fun searchPublicChats(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            activePublicSearchToken = null
            _publicSearchChats.value = emptyList()
            return
        }
        if (_isDemoMode.value || !tdClient.isNativeTdLibLinked) {
            _publicSearchChats.value = emptyList()
            return
        }
        val token = "${System.nanoTime()}"
        activePublicSearchToken = token
        _publicSearchChats.value = emptyList()
        val requestPayload = JSONObject().put("query", cleanQuery)
        val request = buildRustMessagingRequest("search_public_chats", requestPayload)
            ?: JSONObject().put("@type", "searchPublicChats").put("query", cleanQuery)
        request.put("@extra", "rustgram-public-search-$token")
        if (!tdClient.send(request)) {
            _publicSearchChats.value = emptyList()
            appendTdLog("SEARCH", "Public chat search request was not sent to TDLib", isError = true)
        }
    }

    /** Local search covers chats and message history already loaded by TDLib. */
    fun searchGlobal(query: String, publicChats: List<TelegramChatModel> = emptyList()): List<SearchHitItem> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchHitItem>()
        val queryChats = (_chats.value + publicChats).distinctBy { it.id }
        queryChats.filter {
            it.title.contains(query, ignoreCase = true) ||
                (it.username?.contains(query, ignoreCase = true) == true)
        }.forEach { results.add(SearchHitItem(chat = it)) }

        _messages.value.forEach { (chatId, msgs) ->
            val chat = queryChats.find { it.id == chatId }
            if (chat != null) {
                msgs.filter { it.text.contains(query, ignoreCase = true) }
                    .forEach { msg -> results.add(SearchHitItem(chat = chat, matchedMessage = msg)) }
            }
        }
        return results
    }

    fun logCall(callerName: String, isVideo: Boolean) {
        if (!_isDemoMode.value) {
            appendTdLog("CALLS", "Real Telegram VoIP calls and call history are not connected yet", isError = true)
            return
        }
        val item = CallHistoryItem(
            id = System.currentTimeMillis(),
            callerName = callerName,
            isVideo = isVideo,
            isOutgoing = true,
            isMissed = false,
            durationText = "0m 42s",
            timestamp = "Today, ${formatCurrentTime()}"
        )
        _calls.value = listOf(item) + _calls.value
    }

    private fun appendMessageLocal(chatId: Long, msg: TelegramMessageModel) {
        val currentMap = _messages.value.toMutableMap()
        val list = currentMap[chatId]?.toMutableList() ?: mutableListOf()
        list.add(msg)
        currentMap[chatId] = list
        _messages.value = currentMap

        val preview = when (msg.mediaType) {
            MediaType.VOICE -> "🎤 Voice message (0:${msg.voiceDurationSec.toString().padStart(2, '0')})"
            MediaType.VIDEO_NOTE -> "⭕ Video message"
            MediaType.PHOTO -> "📷 ${msg.text}"
            MediaType.DOCUMENT -> "📎 ${msg.fileName ?: msg.text}"
            MediaType.POLL -> "📊 ${msg.pollData?.question ?: msg.text}"
            else -> msg.text
        }
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(lastMessageText = preview, lastMessageTime = msg.timestamp) else it
        }
        // Avoid writing message bodies to the currently unencrypted RustGram SQLite cache.
    }

    private fun formatCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun seedDefaultChatsIfEmpty() {
        if (_chats.value.isNotEmpty()) return
        val seededChats = listOf(
            TelegramChatModel(
                id = 101L,
                title = "Saved Messages",
                type = "private",
                lastMessageText = "Welcome to RustGram Liquid Glass! 🚀",
                lastMessageTime = "12:45",
                unreadCount = 0,
                isPinned = true,
                isOnline = true,
                bio = "Your personal Telegram cloud storage",
                pinnedMessageText = "Welcome to RustGram Liquid Glass! 🚀",
                category = ChatCategory.PERSONAL
            ),
            TelegramChatModel(
                id = 102L,
                title = "Telegram News",
                type = "channel",
                lastMessageText = "📊 Poll: Which Liquid Glass theme looks best?",
                lastMessageTime = "11:20",
                unreadCount = 3,
                isPinned = true,
                isMuted = true,
                isVerified = true,
                username = "@telegram",
                bio = "The official Telegram News channel.",
                memberCount = 9420500,
                category = ChatCategory.CHANNELS
            ),
            TelegramChatModel(
                id = 103L,
                title = "Rust Developers",
                type = "supergroup",
                lastMessageText = "Alexander: Tokio + Rusqlite on Android 16 works blazingly fast!",
                lastMessageTime = "10:14",
                unreadCount = 12,
                username = "@rust_android",
                bio = "Community for systems programming in Rust on Android.",
                memberCount = 1480,
                category = ChatCategory.GROUPS
            ),
            TelegramChatModel(
                id = 104L,
                title = "Pavel Durov",
                type = "channel",
                lastMessageText = "Freedom and privacy are at the heart of our protocol.",
                lastMessageTime = "Yesterday",
                isVerified = true,
                username = "@durov",
                bio = "Thoughts by the founder of Telegram.",
                memberCount = 3210000,
                category = ChatCategory.CHANNELS
            ),
            TelegramChatModel(
                id = 105L,
                title = "Anna Petrova",
                type = "private",
                lastMessageText = "🎤 Voice message (0:08)",
                lastMessageTime = "Yesterday",
                unreadCount = 1,
                isOnline = true,
                username = "@anna_ux",
                phoneNumber = "+1 202 555 0143",
                bio = "Mobile UI Designer • iOS Liquid Glass",
                category = ChatCategory.PERSONAL
            ),
            TelegramChatModel(
                id = 106L,
                title = "RustGram Bot",
                type = "bot",
                lastMessageText = "/help — Configure Telegram API ID & Hash",
                lastMessageTime = "May 2",
                username = "@rustgram_bot",
                bio = "Official assistant bot for RustGram diagnostics.",
                category = ChatCategory.BOTS
            ),
            TelegramChatModel(
                id = 107L,
                title = "Secret: Elena Smirnova",
                type = "secret",
                lastMessageText = "🔐 End-to-end encrypted chat",
                lastMessageTime = "09:15",
                isSecret = true,
                selfDestructSec = 15,
                encryptionFingerprint = "🔐 🦀 ⚡ 💎",
                isOnline = true,
                username = "@elena_s",
                bio = "Secret Chat • 15s auto-destruct",
                category = ChatCategory.SECRET
            )
        )
        _chats.value = seededChats

        val seededMessages = mutableMapOf<Long, List<TelegramMessageModel>>()
        seededMessages[101L] = listOf(
            TelegramMessageModel(
                id = 1L,
                chatId = 101L,
                senderName = "You",
                isOutgoing = true,
                text = "Personal cloud storage powered by Rust Core + TDLib.",
                timestamp = "12:40",
                isRead = true
            ),
            TelegramMessageModel(
                id = 2L,
                chatId = 101L,
                senderName = "You",
                isOutgoing = true,
                text = "Welcome to RustGram Liquid Glass! 🚀",
                timestamp = "12:45",
                isRead = true,
                reactions = listOf(ReactionItem("🔥", 2, true))
            )
        )
        seededMessages[102L] = listOf(
            TelegramMessageModel(
                id = 3L,
                chatId = 102L,
                senderName = "Telegram News",
                isOutgoing = false,
                text = "RustGram brings iPhone Liquid Glass UI, Stories, Polls, Secret Chats and pure Rust engine to Android 16.",
                timestamp = "11:15",
                isRead = true
            ),
            TelegramMessageModel(
                id = 4L,
                chatId = 102L,
                senderName = "Telegram News",
                isOutgoing = false,
                text = "📊 Which Liquid Glass theme looks best?",
                timestamp = "11:20",
                isRead = false,
                mediaType = MediaType.POLL,
                pollData = PollData(
                    question = "Which Liquid Glass theme looks best?",
                    options = listOf(
                        PollOption("Day (Snow Glass)", 142),
                        PollOption("Classic Blue", 89),
                        PollOption("Tinted Dark", 215),
                        PollOption("OLED Night", 174)
                    ),
                    isQuiz = false,
                    allowMultiple = false
                ),
                reactions = listOf(ReactionItem("👍", 48, false), ReactionItem("❤️", 21, false))
            )
        )
        val msg5 = TelegramMessageModel(
            id = 5L,
            chatId = 103L,
            senderName = "Dmitry",
            isOutgoing = false,
            text = "Has anyone benchmarked JNI + libloading TDLib on Honor 600 Lite?",
            timestamp = "10:10",
            isRead = true
        )
        seededMessages[103L] = listOf(
            msg5,
            TelegramMessageModel(
                id = 6L,
                chatId = 103L,
                senderName = "Alexander",
                isOutgoing = false,
                text = "Tokio + Rusqlite on Android 16 works blazingly fast!",
                timestamp = "10:14",
                isRead = false,
                replyToMessage = msg5,
                reactions = listOf(ReactionItem("👏", 5, true))
            )
        )
        seededMessages[105L] = listOf(
            TelegramMessageModel(
                id = 7L,
                chatId = 105L,
                senderName = "Anna Petrova",
                isOutgoing = false,
                text = "Hey! How is the Liquid Glass redesign coming along?",
                timestamp = "Yesterday",
                isRead = true
            ),
            TelegramMessageModel(
                id = 8L,
                chatId = 105L,
                senderName = "You",
                isOutgoing = true,
                text = "Specular rim highlights, Stories, Polls and Voice recording are live!",
                timestamp = "Yesterday",
                isRead = true
            ),
            TelegramMessageModel(
                id = 9L,
                chatId = 105L,
                senderName = "Anna Petrova",
                isOutgoing = false,
                text = "Voice message",
                timestamp = "Yesterday",
                isRead = false,
                mediaType = MediaType.VOICE,
                voiceDurationSec = 8,
                voiceWaveform = listOf(0.2f, 0.35f, 0.65f, 0.85f, 1.0f, 0.7f, 0.45f, 0.25f, 0.55f, 0.9f, 0.4f, 0.6f, 0.8f, 0.3f, 0.5f, 0.75f, 0.95f, 0.6f, 0.4f, 0.25f, 0.5f, 0.7f, 0.4f, 0.2f)
            )
        )
        seededMessages[107L] = listOf(
            TelegramMessageModel(
                id = 10L,
                chatId = 107L,
                senderName = "Elena Smirnova",
                isOutgoing = false,
                text = "🔐 End-to-end encrypted message. Timer is set to 15s for new messages.",
                timestamp = "09:15",
                isRead = true
            )
        )
        _messages.value = seededMessages
    }
}

private val defaultAccounts = listOf(
    AccountProfile(1, "Pavel Rust", "@rustgram_dev", "+1 202 555 0199", "Building Telegram on Android 16 with Rust + TDLib", 0, true),
    AccountProfile(2, "RustGram Work", "@rustgram_work", "+44 20 7946 0912", "Systems Architecture & MTProto", 2, false)
)

private val defaultStories = listOf(
    StoryItem(
        id = 1L,
        authorName = "Anna Petrova",
        chatId = 105L,
        avatarColorIndex = 1,
        slides = listOf(
            StorySlide(101L, "Liquid Glass UI preview on Android 16 ✨", 0xFF5AC8FA, 0xFF007AFF, "12m ago"),
            StorySlide(102L, "Smooth 120fps glass capsules & blur!", 0xFFAF52DE, 0xFF5856D6, "5m ago")
        )
    ),
    StoryItem(
        id = 2L,
        authorName = "Pavel Durov",
        chatId = 104L,
        avatarColorIndex = 0,
        slides = listOf(
            StorySlide(201L, "Privacy is a fundamental human right. 🔐", 0xFF1D2B64, 0xFF007AFF, "1h ago")
        )
    ),
    StoryItem(
        id = 3L,
        authorName = "Rust Devs",
        chatId = 103L,
        avatarColorIndex = 3,
        slides = listOf(
            StorySlide(301L, "Compiled 16KB-aligned Rust NDK binaries for Honor & Pixel! 🦀", 0xFFFF5E3A, 0xFFFF9500, "3h ago")
        )
    ),
    StoryItem(
        id = 4L,
        authorName = "Elena S.",
        chatId = 107L,
        avatarColorIndex = 2,
        isViewed = true,
        slides = listOf(
            StorySlide(401L, "Secret chats with self-destruct timers are active ⏳", 0xFF34C759, 0xFF00C7BE, "5h ago")
        )
    )
)

private val defaultContacts = listOf(
    ContactModel(105L, "Anna Petrova", "@anna_ux", "+1 202 555 0143", "Mobile UI Designer • iOS Liquid Glass", true, "online"),
    ContactModel(201L, "Alexander Ivanov", "@alex_rust", "+44 20 7946 0912", "Systems Engineer in Rust", true, "online"),
    ContactModel(202L, "Boris Becker", "@bbecker", "+49 30 123456", "Distributed systems", false, "last seen 2 hours ago"),
    ContactModel(203L, "David Miller", "@dmiller", "+1 415 555 2671", "Android 16 Kernel Hacker", false, "last seen yesterday"),
    ContactModel(204L, "Elena Smirnova", "@elena_s", "+7 999 123 4567", "Product Manager", true, "online"),
    ContactModel(205L, "Michael Chen", "@mchen", "+1 212 555 8920", "Cryptography Researcher", false, "last seen May 1")
)

private val defaultCallHistory = listOf(
    CallHistoryItem(1L, "Anna Petrova", isVideo = false, isOutgoing = false, isMissed = false, durationText = "3m 42s", timestamp = "Today, 14:20"),
    CallHistoryItem(2L, "Alexander Ivanov", isVideo = true, isOutgoing = true, isMissed = false, durationText = "12m 05s", timestamp = "Yesterday, 19:40"),
    CallHistoryItem(3L, "Boris Becker", isVideo = false, isOutgoing = false, isMissed = true, durationText = "Missed", timestamp = "May 2, 09:15"),
    CallHistoryItem(4L, "Pavel Durov", isVideo = true, isOutgoing = false, isMissed = false, durationText = "1m 18s", timestamp = "April 28, 16:30")
)
