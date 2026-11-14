package com.example.rustgram

import android.util.Log

/**
 * JNI Bridge between Android 16 (Kotlin) and RustGram Rust Core + TDLib.
 *
 * Loading order matters:
 * 1. `tdjson` (TDLib C shared library) is loaded into process memory first.
 * 2. `rustgram_integration` (compiled Rust cdylib) binds `td_*` C symbols via `libloading`
 *    and exposes the complete JNI interface for both TDLib and Rust Core modules.
 */
object NativeBridge {
    private const val TAG = "RustGram_NativeBridge"

    var isTdLibLoaded = false
        private set
    var isRustCoreLoaded = false
        private set

    init {
        try {
            System.loadLibrary("tdjson")
            isTdLibLoaded = true
            Log.i(TAG, "TDLib (libtdjson.so) loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load libtdjson.so: ${e.message}", e)
        }

        try {
            System.loadLibrary("rustgram_integration")
            isRustCoreLoaded = true
            Log.i(TAG, "RustGram native core (librustgram_integration.so) loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load librustgram_integration.so: ${e.message}", e)
        }
    }

    // TDLib JNI exports (implemented in Rust `integration` crate via dynamic C-symbol binding)
    external fun td_create_client_id(): Int
    external fun td_send(clientId: Int, request: String)
    external fun td_receive(timeout: Double): String?
    external fun td_execute(request: String): String?
    external fun td_set_log_message_callback(maxVerbosityLevel: Int, callback: Any?)
    external fun isTdLibNativeLinked(): Boolean

    // Rust Core Engine JNI exports
    external fun initCore(apiId: Int, dbPath: String): Boolean
    external fun getCoreVersion(): String
    external fun computePasswordHash(password: String, salt1: String, salt2: String): String
    external fun parseMarkdown(input: String): String
    external fun normalizeWaveform(peaks: FloatArray, targetBars: Int): FloatArray

    // Rust SQLite (`rusqlite`) persistence JNI exports
    external fun saveChatJson(chatJson: String): Boolean
    external fun loadChatsJson(): String
    external fun saveMessageJson(msgJson: String): Boolean
    external fun loadMessagesJson(chatId: Long, limit: Int): String
    external fun searchMessagesJson(query: String, limit: Int): String
    external fun deleteMessageFromDb(chatId: Long, msgId: Long): Boolean

    // Rust Auth Country Directory JNI exports
    external fun getCountriesJson(): String
    external fun matchCountryByDialCode(dialCode: String): String
}
