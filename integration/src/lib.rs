//! JNI bridge between Android 16 / Kotlin and RustGram Rust Core + TDLib.

use jni::objects::{JClass, JFloatArray, JObject, JString};
use jni::sys::{jboolean, jdouble, jfloatArray, jint, jlong, jstring, JNI_TRUE};
use jni::JNIEnv;
use rustgram_authentication::AuthManager;
use rustgram_core::{ChatId, MessageId, TelegramChat, TelegramMessage};
use rustgram_database::RustGramDatabase;
use rustgram_encryption::TelegramCrypto;
use rustgram_media::VoiceWaveform;
use rustgram_messaging::MessageProcessor;
use rustgram_networking::NetworkManager;
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
// Rust Core Engine JNI Exports
// ============================================================================

#[no_mangle]
pub extern "system" fn Java_com_example_rustgram_NativeBridge_initCore(
    mut env: JNIEnv,
    _class: JClass,
    api_id: jint,
    db_path: JString,
) -> jboolean {
    {
        let mut auth = AUTH_MANAGER.lock().unwrap();
        let mut a = AuthManager::new();
        a.api_id = api_id;
        *auth = Some(a);
    }
    {
        let mut nm = NETWORK_MANAGER.lock().unwrap();
        *nm = Some(NetworkManager::new());
    }
    {
        let mut sync = SYNC_ENGINE.lock().unwrap();
        *sync = Some(SyncEngine::new());
    }

    if let Ok(path_jstr) = env.get_string(&db_path) {
        let path: String = path_jstr.into();
        if !path.is_empty() {
            if let Ok(db) = RustGramDatabase::open(&path) {
                let mut guard = db_holder().lock().unwrap();
                *guard = Some(db);
            }
        }
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
        "TDLib Fallback"
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
    let pass: String = env.get_string(&password).map(|s| s.into()).unwrap_or_default();
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
    let raw: String = env.get_string(&chat_json).map(|s| s.into()).unwrap_or_default();
    if let Ok(chat) = serde_json::from_str::<TelegramChat>(&raw) {
        if let Some(db) = db_holder().lock().unwrap().as_ref() {
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
    let json_str = if let Some(db) = db_holder().lock().unwrap().as_ref() {
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
    let raw: String = env.get_string(&msg_json).map(|s| s.into()).unwrap_or_default();
    if let Ok(msg) = serde_json::from_str::<TelegramMessage>(&raw) {
        if let Some(db) = db_holder().lock().unwrap().as_ref() {
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
    let json_str = if let Some(db) = db_holder().lock().unwrap().as_ref() {
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
    let json_str = if let Some(db) = db_holder().lock().unwrap().as_ref() {
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
    if let Some(db) = db_holder().lock().unwrap().as_ref() {
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
    let dial: String = env.get_string(&dial_code).map(|s| s.into()).unwrap_or_default();
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
    fn test_native_bridge_compiles() {
        assert!(true);
    }
}
