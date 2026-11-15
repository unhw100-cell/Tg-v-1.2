//! RustGram networking models and TDLib proxy request builders.
//!
//! TDLib remains responsible for MTProto transport and data-center selection.
//! This crate validates proxy settings and builds typed TDLib JSON requests; it
//! does not scrape arbitrary public proxy lists or claim that an untested proxy works.

use rustgram_core::{Result, RustGramError};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ConnectionState {
    WaitingForNetwork,
    ConnectingToProxy,
    Connecting,
    Updating,
    Ready,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DataCenter {
    pub id: i32,
    pub ip_address: String,
    pub port: u16,
    pub location: String,
    pub is_active: bool,
    pub ping_ms: Option<u32>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum ProxyType {
    Socks5 {
        username: Option<String>,
        password: Option<String>,
    },
    Http {
        username: Option<String>,
        password: Option<String>,
        http_only: bool,
    },
    Mtproto {
        secret: String,
    },
}

// Credentials must never appear in debug logs.
impl fmt::Debug for ProxyType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Socks5 { username, password } => f
                .debug_struct("Socks5")
                .field("username", &username.as_ref().map(|_| "<set>"))
                .field("password", &password.as_ref().map(|_| "<redacted>"))
                .finish(),
            Self::Http {
                username,
                password,
                http_only,
            } => f
                .debug_struct("Http")
                .field("username", &username.as_ref().map(|_| "<set>"))
                .field("password", &password.as_ref().map(|_| "<redacted>"))
                .field("http_only", http_only)
                .finish(),
            Self::Mtproto { secret } => f
                .debug_struct("Mtproto")
                .field(
                    "secret",
                    &if secret.is_empty() {
                        "<empty>"
                    } else {
                        "<redacted>"
                    },
                )
                .finish(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProxyConfig {
    pub server: String,
    pub port: u16,
    pub proxy_type: ProxyType,
    pub enabled: bool,
}

impl ProxyConfig {
    /// Validate values before they reach TDLib. This checks syntax, not reachability.
    pub fn validate(&self) -> Result<()> {
        let server = self.server.trim();
        if server.is_empty()
            || server.len() > 253
            || server.chars().any(char::is_control)
            || server.chars().any(char::is_whitespace)
        {
            return Err(RustGramError::InvalidParameter(
                "Proxy server must be a valid hostname or IP address".into(),
            ));
        }
        if self.port == 0 {
            return Err(RustGramError::InvalidParameter(
                "Proxy port must be between 1 and 65535".into(),
            ));
        }
        match &self.proxy_type {
            ProxyType::Socks5 { username, password }
            | ProxyType::Http {
                username, password, ..
            } => {
                if username.as_deref().unwrap_or_default().len() > 255
                    || password.as_deref().unwrap_or_default().len() > 255
                {
                    return Err(RustGramError::InvalidParameter(
                        "Proxy credentials are too long".into(),
                    ));
                }
            }
            ProxyType::Mtproto { secret } => {
                if secret.is_empty()
                    || secret.len() > 512
                    || secret.chars().any(char::is_whitespace)
                    || secret.chars().any(char::is_control)
                {
                    return Err(RustGramError::InvalidParameter(
                        "MTProto proxy secret is empty or malformed".into(),
                    ));
                }
            }
        }
        Ok(())
    }

    /// Create the `proxy` object expected by TDLib's JSON interface.
    pub fn to_tdlib_object(&self) -> Result<Value> {
        self.validate()?;
        let proxy_type = match &self.proxy_type {
            ProxyType::Socks5 { username, password } => json!({
                "@type": "proxyTypeSocks5",
                "username": username.as_deref().unwrap_or_default(),
                "password": password.as_deref().unwrap_or_default()
            }),
            ProxyType::Http {
                username,
                password,
                http_only,
            } => json!({
                "@type": "proxyTypeHttp",
                "username": username.as_deref().unwrap_or_default(),
                "password": password.as_deref().unwrap_or_default(),
                "http_only": http_only
            }),
            ProxyType::Mtproto { secret } => json!({
                "@type": "proxyTypeMtproto",
                "secret": secret
            }),
        };

        Ok(json!({
            "@type": "proxy",
            "server": self.server.trim(),
            "port": self.port,
            "type": proxy_type
        }))
    }

    pub fn add_request(&self, enable: bool) -> Result<Value> {
        Ok(json!({
            "@type": "addProxy",
            "proxy": self.to_tdlib_object()?,
            "enable": enable
        }))
    }

    pub fn edit_request(&self, proxy_id: i32, enable: bool) -> Result<Value> {
        if proxy_id <= 0 {
            return Err(RustGramError::InvalidParameter(
                "Proxy ID must be positive".into(),
            ));
        }
        Ok(json!({
            "@type": "editProxy",
            "proxy_id": proxy_id,
            "proxy": self.to_tdlib_object()?,
            "enable": enable
        }))
    }

    /// Return a safe, credential-free label suitable for UI display.
    pub fn safe_label(&self) -> String {
        format!("{}:{} ({})", self.server, self.port, self.kind_label())
    }

    fn kind_label(&self) -> &'static str {
        match &self.proxy_type {
            ProxyType::Socks5 { .. } => "SOCKS5",
            ProxyType::Http { .. } => "HTTP",
            ProxyType::Mtproto { .. } => "MTProto",
        }
    }
}

pub fn get_proxies_request() -> Value {
    json!({ "@type": "getProxies" })
}

pub fn enable_proxy_request(proxy_id: i32) -> Result<Value> {
    if proxy_id <= 0 {
        return Err(RustGramError::InvalidParameter(
            "Proxy ID must be positive".into(),
        ));
    }
    Ok(json!({ "@type": "enableProxy", "proxy_id": proxy_id }))
}

pub fn disable_proxy_request() -> Value {
    json!({ "@type": "disableProxy" })
}

pub fn remove_proxy_request(proxy_id: i32) -> Result<Value> {
    if proxy_id <= 0 {
        return Err(RustGramError::InvalidParameter(
            "Proxy ID must be positive".into(),
        ));
    }
    Ok(json!({ "@type": "removeProxy", "proxy_id": proxy_id }))
}

pub fn ping_proxy_request(proxy: Option<&ProxyConfig>) -> Result<Value> {
    let proxy_value = match proxy {
        Some(config) => config.to_tdlib_object()?,
        None => Value::Null,
    };
    Ok(json!({ "@type": "pingProxy", "proxy": proxy_value }))
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NetworkManager {
    pub current_state: ConnectionState,
    pub active_dc_id: i32,
    pub datacenters: Vec<DataCenter>,
    pub proxy: Option<ProxyConfig>,
}

impl Default for NetworkManager {
    fn default() -> Self {
        // TDLib chooses and updates Telegram data centers; do not invent a fixed
        // active DC or a hard-coded ping measurement in the app layer.
        Self {
            current_state: ConnectionState::WaitingForNetwork,
            active_dc_id: 0,
            datacenters: Vec::new(),
            proxy: None,
        }
    }
}

impl NetworkManager {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_connection_state(&mut self, state: ConnectionState) {
        tracing::info!("Telegram network connection state changed to: {:?}", state);
        self.current_state = state;
    }

    pub fn set_proxy(&mut self, proxy: Option<ProxyConfig>) -> Result<()> {
        if let Some(config) = &proxy {
            config.validate()?;
        }
        self.proxy = proxy;
        Ok(())
    }

    pub fn get_active_dc(&self) -> Result<&DataCenter> {
        self.datacenters
            .iter()
            .find(|dc| dc.id == self.active_dc_id)
            .ok_or_else(|| {
                RustGramError::Network(
                    "Data-center routing is managed by TDLib and is not exposed locally".into(),
                )
            })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn socks5() -> ProxyConfig {
        ProxyConfig {
            server: "proxy.example.org".into(),
            port: 1080,
            proxy_type: ProxyType::Socks5 {
                username: Some("alice".into()),
                password: Some("secret".into()),
            },
            enabled: true,
        }
    }

    #[test]
    fn test_network_manager_starts_without_fake_dc_or_proxy() {
        let manager = NetworkManager::new();
        assert_eq!(manager.current_state, ConnectionState::WaitingForNetwork);
        assert!(manager.datacenters.is_empty());
        assert!(manager.proxy.is_none());
    }

    #[test]
    fn test_add_proxy_request_uses_tdlib_json_shape() {
        let request = socks5().add_request(true).expect("valid proxy");
        assert_eq!(request["@type"], "addProxy");
        assert_eq!(request["proxy"]["server"], "proxy.example.org");
        assert_eq!(request["proxy"]["port"], 1080);
        assert_eq!(request["proxy"]["type"]["@type"], "proxyTypeSocks5");
        assert_eq!(request["enable"], true);
    }

    #[test]
    fn test_mtproto_proxy_request() {
        let proxy = ProxyConfig {
            server: "1.2.3.4".into(),
            port: 443,
            proxy_type: ProxyType::Mtproto {
                secret: "0123456789abcdef".into(),
            },
            enabled: false,
        };
        let request = proxy.add_request(false).expect("valid MTProto proxy");
        assert_eq!(request["proxy"]["type"]["@type"], "proxyTypeMtproto");
        assert_eq!(request["proxy"]["type"]["secret"], "0123456789abcdef");
    }

    #[test]
    fn test_reject_invalid_proxy_values() {
        let mut proxy = socks5();
        proxy.port = 0;
        assert!(proxy.add_request(false).is_err());

        proxy.port = 1080;
        proxy.server = "proxy host".into();
        assert!(proxy.add_request(false).is_err());
    }

    #[test]
    fn test_proxy_debug_redacts_credentials() {
        let rendered = format!("{:?}", socks5());
        assert!(!rendered.contains("secret"));
        assert!(!rendered.contains("alice"));
        assert!(rendered.contains("redacted"));
    }

    #[test]
    fn test_proxy_lifecycle_requests() {
        assert_eq!(get_proxies_request()["@type"], "getProxies");
        assert_eq!(enable_proxy_request(7).unwrap()["proxy_id"], 7);
        assert_eq!(disable_proxy_request()["@type"], "disableProxy");
        assert_eq!(remove_proxy_request(7).unwrap()["@type"], "removeProxy");
        assert_eq!(ping_proxy_request(None).unwrap()["proxy"], Value::Null);
    }
}
