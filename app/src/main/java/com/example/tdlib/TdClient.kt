package com.example.tdlib

import android.content.Context
import android.util.Log
import com.example.rustgram.NativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * TDLib client wrapper communicating through the Rust JNI bridge (`librustgram_integration.so`),
 * which dynamically binds `libtdjson.so` and drives TDLib C symbols safely on Android 16.
 */
class TdClient(private val context: Context) {
    companion object {
        private const val TAG = "RustGram_TdClient"
    }

    private var clientId: Int = -1
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var receiveJob: Job? = null

    private val _updates = MutableSharedFlow<JSONObject>(extraBufferCapacity = 64)
    val updates: SharedFlow<JSONObject> = _updates.asSharedFlow()

    var isNativeTdLibLinked: Boolean = false
        private set

    init {
        initClient()
    }

    private fun initClient() {
        if (NativeBridge.isRustCoreLoaded) {
            try {
                isNativeTdLibLinked = NativeBridge.isTdLibNativeLinked()
                // Silence verbose TDLib native C++ stdout logs
                execute(JSONObject().apply {
                    put("@type", "setLogVerbosityLevel")
                    put("new_verbosity_level", 1)
                })
                clientId = NativeBridge.td_create_client_id()
                Log.i(TAG, "TDLib Client created via Rust JNI with ID: $clientId (nativeLinked=$isNativeTdLibLinked)")
                if (isNativeTdLibLinked) {
                    startReceiveLoop()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize TDLib via Rust JNI: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Native Rust integration library not loaded. Running in local fallback mode.")
        }
    }

    private fun startReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            while (isActive) {
                try {
                    val res = NativeBridge.td_receive(1.0)
                    if (!res.isNullOrEmpty()) {
                        val json = JSONObject(res)
                        _updates.emit(json)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in td_receive loop", e)
                }
            }
        }
    }

    fun send(request: JSONObject) {
        if (clientId != -1 && NativeBridge.isRustCoreLoaded) {
            try {
                NativeBridge.td_send(clientId, request.toString())
            } catch (e: Throwable) {
                Log.e(TAG, "td_send failed: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Fallback TDLib send: $request")
        }
    }

    fun execute(request: JSONObject): JSONObject? {
        if (NativeBridge.isRustCoreLoaded) {
            return try {
                val res = NativeBridge.td_execute(request.toString())
                if (!res.isNullOrEmpty()) JSONObject(res) else null
            } catch (e: Throwable) {
                Log.e(TAG, "td_execute failed: ${e.message}", e)
                null
            }
        }
        return null
    }

    fun getDatabaseDirectory(): String {
        val dir = File(context.filesDir, "tdlib_db")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    fun getFilesDirectory(): String {
        val dir = File(context.filesDir, "tdlib_files")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    fun getRustDbFilePath(): String {
        val dir = File(context.filesDir, "rust_db")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "rustgram_cache.sqlite").absolutePath
    }

    fun close() {
        receiveJob?.cancel()
    }
}
