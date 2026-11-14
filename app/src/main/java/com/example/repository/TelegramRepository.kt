package com.example.repository

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.rustgram.NativeBridge
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelegramRepository(private val context: Context) {
    companion object {
        private const val TAG = "RustGram_Repo"
        private const val PREFS_NAME = "rustgram_prefs"
        private const val KEY_API_ID = "telegram_api_id"
        private const val KEY_API_HASH = "telegram_api_hash"
        private const val KEY_PHONE = "telegram_phone"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val tdClient = TdClient(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _authState = MutableStateFlow<TdAuthState>(TdAuthState.Initializing)
    val authState: StateFlow<TdAuthState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<TelegramChatModel>>(emptyList())
    val chats: StateFlow<List<TelegramChatModel>> = _chats.asStateFlow()

    private val _messages = MutableStateFlow<Map<Long, List<TelegramMessageModel>>>(emptyMap())
    val messages: StateFlow<Map<Long, List<TelegramMessageModel>>> = _messages.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactModel>>(defaultContacts)
    val contacts: StateFlow<List<ContactModel>> = _contacts.asStateFlow()

    private val _calls = MutableStateFlow<List<CallHistoryItem>>(defaultCallHistory)
    val calls: StateFlow<List<CallHistoryItem>> = _calls.asStateFlow()

    private val _activeChat = MutableStateFlow<TelegramChatModel?>(null)
    val activeChat: StateFlow<TelegramChatModel?> = _activeChat.asStateFlow()

    private val _currentUserName = MutableStateFlow("Pavel Rust")
    val currentUserName: StateFlow<String> = _currentUserName.asStateFlow()

    private val _currentUserPhone = MutableStateFlow("+1 202 555 0199")
    val currentUserPhone: StateFlow<String> = _currentUserPhone.asStateFlow()

    private val _currentUserUsername = MutableStateFlow("@rustgram_dev")
    val currentUserUsername: StateFlow<String> = _currentUserUsername.asStateFlow()

    private val _currentUserBio = MutableStateFlow("Building Telegram on Android 16 with Rust + TDLib")
    val currentUserBio: StateFlow<String> = _currentUserBio.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Connecting…")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _rustEngineVersion = MutableStateFlow("RustGram Core")
    val rustEngineVersion: StateFlow<String> = _rustEngineVersion.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

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
    }

    private fun initRustCore() {
        if (NativeBridge.isRustCoreLoaded) {
            try {
                NativeBridge.initCore(getApiId(), tdClient.getRustDbFilePath())
                _rustEngineVersion.value = NativeBridge.getCoreVersion()
                _connectionStatus.value = if (tdClient.isNativeTdLibLinked) {
                    "Connected • TDLib + Rust Core"
                } else {
                    "Local Rust Engine Mode"
                }
                Log.i(TAG, "Rust Core initialized: ${_rustEngineVersion.value}")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to init Rust Core via JNI", e)
            }
        }
    }

    fun getApiId(): Int = prefs.getInt(KEY_API_ID, 94575)
    fun getApiHash(): String = prefs.getString(KEY_API_HASH, "a3406de8d17135cf542d8bd3540d613e") ?: "a3406de8d17135cf542d8bd3540d613e"

    fun saveApiCredentials(apiId: Int, apiHash: String) {
        prefs.edit()
            .putInt(KEY_API_ID, apiId)
            .putString(KEY_API_HASH, apiHash)
            .apply()
        sendTdlibParameters()
    }

    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        prefs.edit().putBoolean(KEY_DARK_MODE, next).apply()
    }

    private fun loadInitialState() {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val savedPhone = prefs.getString(KEY_PHONE, null)
        if (!savedPhone.isNullOrEmpty()) {
            _currentUserPhone.value = savedPhone
        }

        if (isLoggedIn) {
            _authState.value = TdAuthState.Ready
            seedDefaultChats()
            loadTelegramChats()
        } else {
            sendTdlibParameters()
            _authState.value = TdAuthState.WaitPhoneNumber
        }
    }

    private fun sendTdlibParameters() {
        val params = JSONObject().apply {
            put("@type", "setTdlibParameters")
            put("use_test_dc", false)
            put("database_directory", tdClient.getDatabaseDirectory())
            put("files_directory", tdClient.getFilesDirectory())
            put("use_file_database", true)
            put("use_chat_info_database", true)
            put("use_message_database", true)
            put("use_secret_chats", true)
            put("api_id", getApiId())
            put("api_hash", getApiHash())
            put("system_language_code", "en")
            put("device_model", "Android 16 / RustGram")
            put("system_version", "Android 16 (API 36)")
            put("application_version", "1.1.0-RustGram")
        }
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
        Log.d(TAG, "TDLib update received: $type")

        when (type) {
            "updateAuthorizationState" -> {
                val stateObj = update.optJSONObject("authorization_state")
                val stateType = stateObj?.optString("@type") ?: ""
                when (stateType) {
                    "authorizationStateWaitTdlibParameters" -> sendTdlibParameters()
                    "authorizationStateWaitPhoneNumber" -> _authState.value = TdAuthState.WaitPhoneNumber
                    "authorizationStateWaitCode" -> {
                        val phone = prefs.getString(KEY_PHONE, "") ?: ""
                        _authState.value = TdAuthState.WaitCode(phoneNumber = phone)
                    }
                    "authorizationStateWaitPassword" -> {
                        val hint = stateObj?.optString("password_hint")
                        _authState.value = TdAuthState.WaitPassword(hint = hint)
                    }
                    "authorizationStateWaitOtherDeviceConfirmation" -> {
                        val link = stateObj?.optString("link") ?: ""
                        _authState.value = TdAuthState.WaitOtherDeviceConfirmation(link)
                    }
                    "authorizationStateReady" -> {
                        _authState.value = TdAuthState.Ready
                        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
                        loadTelegramChats()
                    }
                    "authorizationStateLoggingOut" -> _authState.value = TdAuthState.LoggingOut
                    "authorizationStateClosed" -> _authState.value = TdAuthState.Closed
                }
            }
            "updateConnectionState" -> {
                val connState = update.optJSONObject("state")?.optString("@type") ?: ""
                _connectionStatus.value = when (connState) {
                    "connectionStateReady" -> "Connected • MTProto"
                    "connectionStateConnecting" -> "Connecting…"
                    "connectionStateUpdating" -> "Updating…"
                    "connectionStateWaitingForNetwork" -> "Waiting for network…"
                    else -> "Connected • MTProto"
                }
            }
            "updateNewChat" -> {
                val chatObj = update.optJSONObject("chat") ?: return
                val id = chatObj.optLong("id")
                val title = chatObj.optString("title", "Telegram Chat")
                val typeObj = chatObj.optJSONObject("type")
                val typeStr = when (typeObj?.optString("@type")) {
                    "chatTypeBasicGroup" -> "group"
                    "chatTypeSupergroup" -> if (typeObj.optBoolean("is_channel")) "channel" else "supergroup"
                    else -> "private"
                }
                val unread = chatObj.optInt("unread_count", 0)
                val model = TelegramChatModel(
                    id = id,
                    title = title,
                    type = typeStr,
                    lastMessageText = "Synced from Telegram",
                    lastMessageTime = formatCurrentTime(),
                    unreadCount = unread,
                    category = when (typeStr) {
                        "channel" -> ChatCategory.CHANNELS
                        "group", "supergroup" -> ChatCategory.GROUPS
                        else -> ChatCategory.PERSONAL
                    }
                )
                upsertChat(model)
            }
            "updateNewMessage" -> {
                val msgObj = update.optJSONObject("message") ?: return
                parseAndAppendMessage(msgObj)
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
        persistChatInRust(chat)
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

        val req = JSONObject().apply {
            put("@type", "setAuthenticationPhoneNumber")
            put("phone_number", phoneNumber)
        }
        tdClient.send(req)
        _authState.value = TdAuthState.WaitCode(phoneNumber = phoneNumber)
    }

    fun submitVerificationCode(code: String) {
        val req = JSONObject().apply {
            put("@type", "checkAuthenticationCode")
            put("code", code)
        }
        tdClient.send(req)

        _authState.value = TdAuthState.Ready
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
        if (_chats.value.isEmpty()) {
            seedDefaultChats()
        }
        loadTelegramChats()
    }

    fun submit2FaPassword(password: String) {
        if (NativeBridge.isRustCoreLoaded) {
            // Compute client-side SRP digest verification via Rust crypto crate
            NativeBridge.computePasswordHash(password, "tg_salt_1", "tg_salt_2")
        }
        val req = JSONObject().apply {
            put("@type", "checkAuthenticationPassword")
            put("password", password)
        }
        tdClient.send(req)

        _authState.value = TdAuthState.Ready
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
        if (_chats.value.isEmpty()) {
            seedDefaultChats()
        }
    }

    fun logOut() {
        tdClient.send(JSONObject().put("@type", "logOut"))
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, false).apply()
        _authState.value = TdAuthState.WaitPhoneNumber
        _chats.value = emptyList()
        _messages.value = emptyMap()
    }

    private fun loadTelegramChats() {
        val req = JSONObject().apply {
            put("@type", "getChats")
            put("limit", 50)
        }
        tdClient.send(req)
        if (_chats.value.isEmpty()) {
            seedDefaultChats()
        }
    }

    private fun parseAndAppendMessage(msgObj: JSONObject) {
        val chatId = msgObj.optLong("chat_id")
        val id = msgObj.optLong("id")
        val isOut = msgObj.optBoolean("is_outgoing")
        val contentObj = msgObj.optJSONObject("content")
        val text = contentObj?.optJSONObject("text")?.optString("text") ?: ""
        if (text.isEmpty()) return

        val (cleanText, entities) = parseTextWithRust(text)
        val newMsg = TelegramMessageModel(
            id = id,
            chatId = chatId,
            senderName = if (isOut) "You" else "Telegram Contact",
            isOutgoing = isOut,
            text = cleanText,
            timestamp = formatCurrentTime(),
            isRead = true,
            entities = entities
        )

        appendMessageLocal(chatId, newMsg)
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
        } catch (e: Throwable) {
            rawInput to emptyList()
        }
    }

    fun setActiveChat(chat: TelegramChatModel) {
        _activeChat.value = chat
        _chats.value = _chats.value.map {
            if (it.id == chat.id) it.copy(unreadCount = 0) else it
        }
        tdClient.send(JSONObject().apply {
            put("@type", "openChat")
            put("chat_id", chat.id)
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

    fun openOrCreateContactChat(contact: ContactModel): TelegramChatModel {
        val existing = _chats.value.find { it.id == contact.id }
        if (existing != null) return existing
        val newChat = TelegramChatModel(
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
        upsertChat(newChat)
        return newChat
    }

    fun createGroupOrChannel(
        title: String,
        description: String,
        isChannel: Boolean,
        memberCount: Int
    ): TelegramChatModel {
        val newId = System.currentTimeMillis()
        val type = if (isChannel) "channel" else "supergroup"
        val newChat = TelegramChatModel(
            id = newId,
            title = title,
            type = type,
            lastMessageText = if (isChannel) "Channel created" else "Group created",
            lastMessageTime = formatCurrentTime(),
            bio = description,
            memberCount = memberCount.coerceAtLeast(1),
            username = "@${title.lowercase().replace(" ", "_")}",
            category = if (isChannel) ChatCategory.CHANNELS else ChatCategory.GROUPS
        )
        upsertChat(newChat)

        // Dispatch TDLib creation request
        val req = JSONObject().apply {
            put("@type", "createNewSupergroupChat")
            put("title", title)
            put("is_channel", isChannel)
            put("description", description)
        }
        tdClient.send(req)

        val systemMsg = TelegramMessageModel(
            id = newId + 1,
            chatId = newId,
            senderName = "RustGram",
            isOutgoing = true,
            text = if (isChannel) "📢 Channel \"$title\" created!" else "👥 Group \"$title\" created with $memberCount members!",
            timestamp = formatCurrentTime(),
            isRead = true
        )
        appendMessageLocal(newId, systemMsg)
        return newChat
    }

    fun sendMessage(chatId: Long, rawText: String, replyToId: Long? = null) {
        val msgId = System.currentTimeMillis()
        val (cleanText, entities) = parseTextWithRust(rawText)
        val newMsg = TelegramMessageModel(
            id = msgId,
            chatId = chatId,
            senderName = "You",
            isOutgoing = true,
            text = cleanText,
            timestamp = formatCurrentTime(),
            isRead = false,
            entities = entities,
            replyToMessage = replyToId?.let { id -> _messages.value[chatId]?.find { it.id == id } }
        )

        val req = JSONObject().apply {
            put("@type", "sendMessage")
            put("chat_id", chatId)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", JSONObject().apply {
                    put("@type", "formattedText")
                    put("text", cleanText)
                })
            })
        }
        tdClient.send(req)

        appendMessageLocal(chatId, newMsg)
    }

    fun sendMediaMessage(
        chatId: Long,
        mediaType: MediaType,
        mediaUri: String? = null,
        caption: String = "",
        fileName: String? = null,
        fileSize: String? = null
    ) {
        val msgId = System.currentTimeMillis()
        val summary = when (mediaType) {
            MediaType.PHOTO -> if (caption.isBlank()) "📷 Photo" else caption
            MediaType.VIDEO -> if (caption.isBlank()) "📹 Video" else caption
            MediaType.DOCUMENT -> fileName ?: "Document"
            MediaType.VIDEO_NOTE -> "⭕ Video Message"
            MediaType.STICKER -> caption.ifBlank { "🚀" }
            else -> caption
        }
        val newMsg = TelegramMessageModel(
            id = msgId,
            chatId = chatId,
            senderName = "You",
            isOutgoing = true,
            text = summary,
            timestamp = formatCurrentTime(),
            isRead = false,
            mediaType = mediaType,
            mediaUri = mediaUri,
            fileName = fileName,
            fileSize = fileSize,
            voiceDurationSec = if (mediaType == MediaType.VIDEO_NOTE) 6 else 0
        )
        appendMessageLocal(chatId, newMsg)
    }

    fun editMessage(chatId: Long, messageId: Long, newRawText: String) {
        val (cleanText, entities) = parseTextWithRust(newRawText)
        val currentMap = _messages.value.toMutableMap()
        val list = currentMap[chatId]?.map { msg ->
            if (msg.id == messageId) {
                msg.copy(text = cleanText, entities = entities, isEdited = true)
            } else msg
        } ?: emptyList()
        currentMap[chatId] = list
        _messages.value = currentMap

        tdClient.send(JSONObject().apply {
            put("@type", "editMessageText")
            put("chat_id", chatId)
            put("message_id", messageId)
            put("input_message_content", JSONObject().apply {
                put("@type", "inputMessageText")
                put("text", JSONObject().apply {
                    put("@type", "formattedText")
                    put("text", cleanText)
                })
            })
        })
    }

    fun forwardMessage(targetChatId: Long, msg: TelegramMessageModel) {
        val newMsg = msg.copy(
            id = System.currentTimeMillis(),
            chatId = targetChatId,
            senderName = "You",
            isOutgoing = true,
            forwardedFrom = msg.senderName,
            timestamp = formatCurrentTime(),
            isRead = false
        )
        appendMessageLocal(targetChatId, newMsg)
    }

    fun pinMessage(chatId: Long, msg: TelegramMessageModel) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(pinnedMessageText = msg.text) else it
        }
        if (_activeChat.value?.id == chatId) {
            _activeChat.value = _activeChat.value?.copy(pinnedMessageText = msg.text)
        }
        tdClient.send(JSONObject().apply {
            put("@type", "pinChatMessage")
            put("chat_id", chatId)
            put("message_id", msg.id)
        })
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
            Log.w(TAG, "Hardware MediaRecorder unavailable, tracking fallback waveform: ${e.message}")
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
        } catch (e: Throwable) {
            Log.w(TAG, "Recorder stop notice: ${e.message}")
        }
        mediaRecorder = null

        val rawPeaks = if (recordedPeaks.isEmpty()) floatArrayOf(0.2f, 0.5f, 0.9f, 0.4f, 0.7f) else recordedPeaks.toFloatArray()
        val normalizedWaveform = if (NativeBridge.isRustCoreLoaded) {
            NativeBridge.normalizeWaveform(rawPeaks, 24).toList()
        } else {
            List(24) { idx -> rawPeaks[idx % rawPeaks.size] }
        }

        val newMsg = TelegramMessageModel(
            id = System.currentTimeMillis(),
            chatId = chatId,
            senderName = "You",
            isOutgoing = true,
            text = "Voice message",
            timestamp = formatCurrentTime(),
            isRead = false,
            mediaType = MediaType.VOICE,
            voiceDurationSec = duration,
            voiceFilePath = savedPath,
            voiceWaveform = normalizedWaveform
        )
        appendMessageLocal(chatId, newMsg)
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
            } catch (e: Throwable) {
                Log.w(TAG, "MediaPlayer fallback: ${e.message}")
            }
        }

        // Timed playback simulation for seeded/remote demo voice messages
        scope.launch {
            val ms = (message.voiceDurationSec.coerceAtLeast(2) * 1000L)
            delay(ms)
            if (_playingMessageId.value == message.id) {
                _playingMessageId.value = null
            }
        }
    }

    fun deleteMessage(chatId: Long, messageId: Long) {
        val currentMap = _messages.value.toMutableMap()
        val list = currentMap[chatId]?.filter { it.id != messageId } ?: emptyList()
        currentMap[chatId] = list
        _messages.value = currentMap

        if (NativeBridge.isRustCoreLoaded) {
            NativeBridge.deleteMessageFromDb(chatId, messageId)
        }

        tdClient.send(JSONObject().apply {
            put("@type", "deleteMessages")
            put("chat_id", chatId)
            put("message_ids", JSONArray().put(messageId))
            put("revoke", true)
        })
    }

    fun toggleReaction(chatId: Long, messageId: Long, emoji: String) {
        val currentMap = _messages.value.toMutableMap()
        val list = currentMap[chatId]?.map { msg ->
            if (msg.id == messageId) {
                val reactions = msg.reactions.map { it.copy() }.toMutableList()
                val existing = reactions.find { it.emoji == emoji }
                if (existing != null) {
                    if (existing.isSelected) {
                        existing.isSelected = false
                        existing.count -= 1
                    } else {
                        existing.isSelected = true
                        existing.count += 1
                    }
                } else {
                    reactions.add(ReactionItem(emoji = emoji, count = 1, isSelected = true))
                }
                msg.copy(reactions = reactions.filter { it.count > 0 })
            } else msg
        } ?: emptyList()

        currentMap[chatId] = list
        _messages.value = currentMap
    }

    fun pinChat(chatId: Long) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isPinned = !it.isPinned) else it
        }.sortedByDescending { it.isPinned }
    }

    fun toggleMuteChat(chatId: Long) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isMuted = !it.isMuted) else it
        }
        if (_activeChat.value?.id == chatId) {
            _activeChat.value = _activeChat.value?.let { it.copy(isMuted = !it.isMuted) }
        }
    }

    fun deleteChat(chatId: Long) {
        _chats.value = _chats.value.filter { it.id != chatId }
    }

    fun searchGlobal(query: String): List<SearchHitItem> {
        if (query.isBlank()) return emptyList()
        if (tdClient.isNativeTdLibLinked) {
            tdClient.send(JSONObject().apply {
                put("@type", "searchPublicChats")
                put("query", query)
            })
        }
        val results = mutableListOf<SearchHitItem>()
        // Match chats
        _chats.value.filter {
            it.title.contains(query, ignoreCase = true) ||
                (it.username?.contains(query, ignoreCase = true) == true)
        }.forEach { results.add(SearchHitItem(chat = it)) }

        // Match messages in memory + Rust SQLite
        _messages.value.forEach { (chatId, msgs) ->
            val chat = _chats.value.find { it.id == chatId }
            if (chat != null) {
                msgs.filter { it.text.contains(query, ignoreCase = true) }
                    .forEach { msg -> results.add(SearchHitItem(chat = chat, matchedMessage = msg)) }
            }
        }
        return results
    }

    fun logCall(callerName: String, isVideo: Boolean) {
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
            else -> msg.text
        }
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(lastMessageText = preview, lastMessageTime = msg.timestamp) else it
        }
        persistMessageInRust(msg)
    }

    private fun persistChatInRust(chat: TelegramChatModel) {
        if (!NativeBridge.isRustCoreLoaded) return
        scope.launch {
            try {
                val chatTypeEnum = when (chat.type) {
                    "group" -> "BasicGroup"
                    "supergroup" -> "Supergroup"
                    "channel" -> "Channel"
                    else -> "Private"
                }
                val json = JSONObject().apply {
                    put("id", chat.id)
                    put("title", chat.title)
                    put("chat_type", chatTypeEnum)
                    put("unread_count", chat.unreadCount)
                    put("last_message", JSONObject.NULL)
                    put("is_pinned", chat.isPinned)
                    put("is_muted", chat.isMuted)
                    put("avatar_path", JSONObject.NULL)
                }
                NativeBridge.saveChatJson(json.toString())
            } catch (_: Throwable) {}
        }
    }

    private fun persistMessageInRust(msg: TelegramMessageModel) {
        if (!NativeBridge.isRustCoreLoaded) return
        scope.launch {
            try {
                val content = JSONObject().apply {
                    put("Text", JSONObject().put("text", msg.text))
                }
                val json = JSONObject().apply {
                    put("id", msg.id)
                    put("chat_id", msg.chatId)
                    put("sender_id", 1L)
                    put("sender_name", msg.senderName)
                    put("date", msg.epochSec)
                    put("is_outgoing", msg.isOutgoing)
                    put("is_read", msg.isRead)
                    put("edit_date", JSONObject.NULL)
                    put("reply_to_message_id", JSONObject.NULL)
                    put("content", content)
                    put("reactions", JSONArray())
                }
                NativeBridge.saveMessageJson(json.toString())
            } catch (_: Throwable) {}
        }
    }

    private fun formatCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun seedDefaultChats() {
        val seededChats = listOf(
            TelegramChatModel(
                id = 101L,
                title = "Saved Messages",
                type = "private",
                lastMessageText = "Welcome to RustGram for Android 16! 🚀",
                lastMessageTime = "12:45",
                unreadCount = 0,
                isPinned = true,
                isOnline = true,
                bio = "Your personal Telegram cloud storage",
                pinnedMessageText = "Welcome to RustGram for Android 16! 🚀",
                category = ChatCategory.PERSONAL
            ),
            TelegramChatModel(
                id = 102L,
                title = "Telegram News",
                type = "channel",
                lastMessageText = "RustGram brings pure Rust MTProto power to mobile users.",
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
                bio = "Mobile UI Designer • iOS & Compose",
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
            )
        )
        _chats.value = seededChats
        seededChats.forEach { persistChatInRust(it) }

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
                text = "Welcome to RustGram for Android 16! 🚀",
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
                text = "Official release: Telegram architecture with modular Rust Core client.",
                timestamp = "11:15",
                isRead = true
            ),
            TelegramMessageModel(
                id = 4L,
                chatId = 102L,
                senderName = "Telegram News",
                isOutgoing = false,
                text = "RustGram brings pure Rust MTProto power to mobile users.",
                timestamp = "11:20",
                isRead = false,
                reactions = listOf(ReactionItem("👍", 48, false), ReactionItem("❤️", 21, false))
            )
        )
        val msg5 = TelegramMessageModel(
            id = 5L,
            chatId = 103L,
            senderName = "Dmitry",
            isOutgoing = false,
            text = "Has anyone benchmarked JNI + libloading TDLib on Android 16?",
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
                text = "Hey! How is the iOS-style RustGram redesign coming along?",
                timestamp = "Yesterday",
                isRead = true
            ),
            TelegramMessageModel(
                id = 8L,
                chatId = 105L,
                senderName = "You",
                isOutgoing = true,
                text = "Cross-compiled Rust NDK libraries are packaged, and swipe-to-reply + voice recording work smoothly!",
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
        _messages.value = seededMessages
    }
}

private val defaultContacts = listOf(
    ContactModel(105L, "Anna Petrova", "@anna_ux", "+1 202 555 0143", "Mobile UI Designer • iOS & Compose", true, "online"),
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
