//! RustGram Networking Engine
//! Connection states, Data Center (DC) routing, MTProto proxy and network state tracking.

use rustgram_core::{Result, RustGramError};
use serde::{Deserialize, Serialize};

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

#[derive(Debug, Clone, Serialize, Deserialize)]
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

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProxyConfig {
    pub server: String,
    pub port: u16,
    pub proxy_type: ProxyType,
    pub enabled: bool,
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
        Self {
            current_state: ConnectionState::WaitingForNetwork,
            active_dc_id: 2, // Default EU/NL production DC
            datacenters: vec![
                DataCenter {
                    id: 1,
                    ip_address: "149.154.175.50".into(),
                    port: 443,
                    location: "Miami, USA".into(),
                    is_active: false,
                    ping_ms: None,
                },
                DataCenter {
                    id: 2,
                    ip_address: "149.154.167.51".into(),
                    port: 443,
                    location: "Amsterdam, Netherlands".into(),
                    is_active: true,
                    ping_ms: Some(34),
                },
                DataCenter {
                    id: 3,
                    ip_address: "149.154.175.100".into(),
                    port: 443,
                    location: "Miami, USA".into(),
                    is_active: false,
                    ping_ms: None,
                },
                DataCenter {
                    id: 4,
                    ip_address: "149.154.167.91".into(),
                    port: 443,
                    location: "Amsterdam, Netherlands".into(),
                    is_active: false,
                    ping_ms: None,
                },
                DataCenter {
                    id: 5,
                    ip_address: "91.108.56.165".into(),
                    port: 443,
                    location: "Singapore".into(),
                    is_active: false,
                    ping_ms: None,
                },
            ],
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

    pub fn set_proxy(&mut self, proxy: Option<ProxyConfig>) {
        self.proxy = proxy;
    }

    pub fn get_active_dc(&self) -> Result<&DataCenter> {
        self.datacenters
            .iter()
            .find(|dc| dc.id == self.active_dc_id)
            .ok_or_else(|| RustGramError::Network(format!("Active DC {} not found", self.active_dc_id)))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_network_manager_defaults() {
        let mut nm = NetworkManager::new();
        assert_eq!(nm.current_state, ConnectionState::WaitingForNetwork);
        nm.set_connection_state(ConnectionState::Ready);
        assert_eq!(nm.current_state, ConnectionState::Ready);

        let active_dc = nm.get_active_dc().expect("DC2 must exist");
        assert_eq!(active_dc.id, 2);
        assert_eq!(active_dc.location, "Amsterdam, Netherlands");
    }
}
