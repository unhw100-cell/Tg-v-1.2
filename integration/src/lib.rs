//! JNI bridge between Android 16 / Kotlin and RustGram Rust Core + TDLib.

use jni::objects::{JClass, JFloatArray, JObject, JString};
use jni::sys::{jboolean, jdouble, jfloatArray, jint, jlong, jstring, JNI_TRUE};
use jni::JNIEnv;
use rustgram_authentication::AuthManager;
use rustgram_core::{ChatId, MessageId, TelegramChat, TelegramMessage};
use rustgram_database::RustGramDatabase;
use rustgram_encryption::TelegramCrypto;
use rustgram_media::VoiceWaveform;
use rustgram_messaging::{build_messaging_request, MessageProcessor};
use rustgram_networking::NetworkManager;
use rustgram_networking::{
    disable_proxy_request, enable_proxy_request, get_proxies_request, ping_proxy_request,
    remove_proxy_request, ProxyConfig,
};
use rustgram_synchronization::SyncEngine;
use std::sync::{Mutex, OnceLock};

static AUTH_MANAGER: Mutex<Option<AuthManager>> = Mutex::new(None);
static NETWORK_MANAGER: Mutex<Option<NetworkManager>> = Mutex::new(None);
static SYNC_ENGINE: Mutex<Option<SyncEngine>> = Mutex::new(None);
static DATABASE: OnceLock<Mutex<Option<RustGramDatabase>>> = OnceLock::new();

fn db_holder() -> &'static Mutex<Option<RustGramDatabase>> {
    DATABASE.get_or_init(|| Mutex::new(None))
}

// ============================================================================
// TDLib JNI Proxy (backed by Rust `libloading` dynamic binding to libtdjson.so)
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_td_1create_1client_1id(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    rustgram_tdlib::create_client_id()
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_td_1send(
    mut env: JNIEnv,
    _class: JClass,
    client_id: jint,
    request: JString,
) {
    if let Ok(jstr) = env.get_string(&request) {
        let req_str: String = jstr.into();
        let _ = rustgram_tdlib::send_raw(client_id, &req_str);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_td_1receive(
    env: JNIEnv,
    _class: JClass,
    timeout: jdouble,
) -> jstring {
    match rustgram_tdlib::receive_raw(timeout) {
        Some(json_str) => env
            .new_string(json_str)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_td_1execute(
    mut env: JNIEnv,
    _class: JClass,
    request: JString,
) -> jstring {
    let req_str: String = env
        .get_string(&request)
        .map(|s| s.into())
        .unwrap_or_default();
    match rustgram_tdlib::execute_raw(&req_str) {
        Some(res) => env
            .new_string(res)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_td_1set_1log_1message_1callback(
    _env: JNIEnv,
    _class: JClass,
    _max_verbosity_level: jint,
    _callback: JObject,
) {
    // Verbosity managed via TDLib execute setLogVerbosityLevel
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_isTdLibNativeLinked(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if rustgram_tdlib::is_native_linked() {
        JNI_TRUE
    } else {
        0
    }
}

// ============================================================================
// Proxy request builders. Validation and TDLib JSON construction live in Rust.
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_buildProxyRequest(
    mut env: JNIEnv,
    _class: JClass,
    operation: JString,
    config_json: JString,
    proxy_id: jint,
) -> jstring {
    let operation: String = match env.get_string(&operation) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let config_json: String = match env.get_string(&config_json) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };

    let request = match operation.as_str() {
        "list" => Ok(get_proxies_request()),
        "disable" => Ok(disable_proxy_request()),
        "enable" => enable_proxy_request(proxy_id),
        "remove" => remove_proxy_request(proxy_id),
        "add" | "edit" | "ping" => {
            let parsed = if config_json.trim().is_empty() {
                None
            } else {
                serde_json::from_str::<ProxyConfig>(&config_json).ok()
            };
            match (operation.as_str(), parsed) {
                ("add", Some(config)) => config.add_request(config.enabled),
                ("edit", Some(config)) => config.edit_request(proxy_id, config.enabled),
                ("ping", Some(config)) => ping_proxy_request(Some(&config)),
                ("ping", None) => ping_proxy_request(None),
                _ => Err(rustgram_core::RustGramError::InvalidParameter(
                    "A valid proxy configuration is required".into(),
                )),
            }
        }
        _ => Err(rustgram_core::RustGramError::InvalidParameter(
            "Unsupported proxy operation".into(),
        )),
    };

    let output = match request.and_then(|value| {
        serde_json::to_string(&value)
            .map_err(|error| rustgram_core::RustGramError::Serialization(error.to_string()))
    }) {
        Ok(json) => json,
        Err(_) => return std::ptr::null_mut(),
    };
    env.new_string(output)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

// ============================================================================
// Rust messaging request builder JNI export
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_buildMessagingRequest(
    mut env: JNIEnv,
    _class: JClass,
    operation: JString,
    payload_json: JString,
) -> jstring {
    let operation: String = match env.get_string(&operation) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let payload_json: String = match env.get_string(&payload_json) {
        Ok(value) => value.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let payload = match serde_json::from_str::<serde_json::Value>(&payload_json) {
        Ok(value) => value,
        Err(_) => return std::ptr::null_mut(),
    };
    let request = match build_messaging_request(&operation, &payload) {
        Ok(value) => value,
        Err(error) => {
            tracing::warn!("Rejected Rust messaging request: {}", error);
            return std::ptr::null_mut();
        }
    };
    let output = match serde_json::to_string(&request) {
        Ok(value) => value,
        Err(_) => return std::ptr::null_mut(),
    };
    env.new_string(output)
        .map(|value| value.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

// ============================================================================
// Rust Core Engine JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_initCore(
    mut env: JNIEnv,
    _class: JClass,
    api_id: jint,
    db_path: JString,
) -> jboolean {
    let path: String = match env.get_string(&db_path) {
        Ok(value) => value.into(),
        Err(_) => return 0,
    };
    if path.trim().is_empty() {
        return 0;
    }
    let database = match RustGramDatabase::open(&path) {
        Ok(db) => db,
        Err(error) => {
            tracing::error!("RustGram database initialization failed: {}", error);
            return 0;
        }
    };

    {
        let mut auth = AUTH_MANAGER
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut manager = AuthManager::new();
        manager.api_id = api_id;
        *auth = Some(manager);
    }
    {
        let mut manager = NETWORK_MANAGER
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        *manager = Some(NetworkManager::new());
    }
    {
        let mut manager = SYNC_ENGINE
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        *manager = Some(SyncEngine::new());
    }
    {
        let mut guard = db_holder()
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        *guard = Some(database);
    }

    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_getCoreVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let linked = if rustgram_tdlib::is_native_linked() {
        "TDLib Linked"
    } else {
        "TDLib unavailable"
    };
    let version_str = format!("RustGram Core 1.1.0 (Rust + Tokio + Rusqlite + {})", linked);
    env.new_string(version_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_computePasswordHash(
    mut env: JNIEnv,
    _class: JClass,
    password: JString,
    salt1: JString,
    salt2: JString,
) -> jstring {
    let pass: String = env
        .get_string(&password)
        .map(|s| s.into())
        .unwrap_or_default();
    let s1: String = env.get_string(&salt1).map(|s| s.into()).unwrap_or_default();
    let s2: String = env.get_string(&salt2).map(|s| s.into()).unwrap_or_default();

    let hash_bytes = TelegramCrypto::compute_password_hash(&pass, s1.as_bytes(), s2.as_bytes());
    let hex_str: String = hash_bytes.iter().map(|b| format!("{:02x}", b)).collect();

    env.new_string(hex_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_parseMarkdown(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jstring {
    let raw: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
    let parsed = MessageProcessor::parse_markdown(&raw);
    let json_str = serde_json::to_string(&parsed).unwrap_or_else(|_| "{}".into());

    env.new_string(json_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_normalizeWaveform(
    env: JNIEnv,
    _class: JClass,
    peaks: JFloatArray,
    target_bars: jint,
) -> jfloatArray {
    let len = env.get_array_length(&peaks).unwrap_or(0) as usize;
    let mut raw = vec![0f32; len];
    if len > 0 {
        let _ = env.get_float_array_region(&peaks, 0, &mut raw);
    }
    let wf = VoiceWaveform::generate_normalized(&raw, target_bars.max(8) as usize);
    let normalized: Vec<f32> = wf.samples.into_iter().map(|b| (b as f32) / 31.0).collect();

    match env.new_float_array(normalized.len() as i32) {
        Ok(arr) => {
            let _ = env.set_float_array_region(&arr, 0, &normalized);
            arr.into_raw()
        }
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_saveChatJson(
    mut env: JNIEnv,
    _class: JClass,
    chat_json: JString,
) -> jboolean {
    let raw: String = env
        .get_string(&chat_json)
        .map(|s| s.into())
        .unwrap_or_default();
    if let Ok(chat) = serde_json::from_str::<TelegramChat>(&raw) {
        if let Some(db) = db_holder()
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .as_ref()
        {
            if db.save_chat(&chat).is_ok() {
                return JNI_TRUE;
            }
        }
    }
    0
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_loadChatsJson(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let json_str = if let Some(db) = db_holder()
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
    {
        let chats = db.get_all_chats().unwrap_or_default();
        serde_json::to_string(&chats).unwrap_or_else(|_| "[]".into())
    } else {
        "[]".into()
    };

    env.new_string(json_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_saveMessageJson(
    mut env: JNIEnv,
    _class: JClass,
    msg_json: JString,
) -> jboolean {
    let raw: String = env
        .get_string(&msg_json)
        .map(|s| s.into())
        .unwrap_or_default();
    if let Ok(msg) = serde_json::from_str::<TelegramMessage>(&raw) {
        if let Some(db) = db_holder()
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .as_ref()
        {
            if db.save_message(&msg).is_ok() {
                return JNI_TRUE;
            }
        }
    }
    0
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_loadMessagesJson(
    env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    limit: jint,
) -> jstring {
    let json_str = if let Some(db) = db_holder()
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
    {
        let msgs = db
            .get_chat_messages(ChatId(chat_id), limit.max(1) as u32)
            .unwrap_or_default();
        serde_json::to_string(&msgs).unwrap_or_else(|_| "[]".into())
    } else {
        "[]".into()
    };

    env.new_string(json_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_searchMessagesJson(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
    limit: jint,
) -> jstring {
    let q: String = env.get_string(&query).map(|s| s.into()).unwrap_or_default();
    let json_str = if let Some(db) = db_holder()
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
    {
        let msgs = db
            .search_messages(&q, limit.max(1) as u32)
            .unwrap_or_default();
        serde_json::to_string(&msgs).unwrap_or_else(|_| "[]".into())
    } else {
        "[]".into()
    };

    env.new_string(json_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_deleteMessageFromDb(
    _env: JNIEnv,
    _class: JClass,
    chat_id: jlong,
    msg_id: jlong,
) -> jboolean {
    if let Some(db) = db_holder()
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
    {
        if db
            .delete_message(ChatId(chat_id), MessageId(msg_id))
            .is_ok()
        {
            return JNI_TRUE;
        }
    }
    0
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_getCountriesJson(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let countries = AuthManager::get_all_countries();
    let json_str = serde_json::to_string(&countries).unwrap_or_else(|_| "[]".into());
    env.new_string(json_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_matchCountryByDialCode(
    mut env: JNIEnv,
    _class: JClass,
    dial_code: JString,
) -> jstring {
    let dial: String = env
        .get_string(&dial_code)
        .map(|s| s.into())
        .unwrap_or_default();
    let json_str = match AuthManager::match_country_by_dial_code(&dial) {
        Some(country) => serde_json::to_string(&country).unwrap_or_default(),
        None => String::new(),
    };
    env.new_string(json_str)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    #[test]
    fn test_tdlib_client_id_is_positive() {
        let client_id = rustgram_tdlib::create_client_id();
        assert!(client_id > 0, "TDLib client ID must be positive");
    }
}
