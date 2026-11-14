//! RustGram Core Engine
//! Base domain models, type definitions, unified error types, and core abstractions.

use serde::{Deserialize, Serialize};
use thiserror::Error;

pub type Result<T> = std::result::Result<T, RustGramError>;

#[derive(Error, Debug, Clone, Serialize, Deserialize)]
pub enum RustGramError {
    #[error("Network connection error: {0}")]
    Network(String),
    #[error("Authentication failed: {0}")]
    Auth(String),
    #[error("Database error: {0}")]
    Database(String),
    #[error("TDLib protocol error [{code}]: {message}")]
    TdLib { code: i32, message: String },
    #[error("Serialization error: {0}")]
    Serialization(String),
    #[error("Crypto verification error: {0}")]
    Crypto(String),
    #[error("Invalid parameter: {0}")]
    InvalidParameter(String),
    #[error("Operation cancelled or timeout")]
    Timeout,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct ChatId(pub i64);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct UserId(pub i64);

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct MessageId(pub i64);

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum ChatType {
    Private,
    BasicGroup,
    Supergroup,
    Channel,
    SecretChat,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum UserStatus {
    Online,
    Offline { was_online: u64 },
    Recently,
    LastWeek,
    LastMonth,
    Empty,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelegramUser {
    pub id: UserId,
    pub first_name: String,
    pub last_name: String,
    pub username: Option<String>,
    pub phone_number: String,
    pub status: UserStatus,
    pub is_bot: bool,
    pub is_verified: bool,
    pub is_premium: bool,
    pub avatar_path: Option<String>,
}

impl TelegramUser {
    pub fn display_name(&self) -> String {
        if self.last_name.is_empty() {
            self.first_name.clone()
        } else {
            format!("{} {}", self.first_name, self.last_name)
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MessageContent {
    Text {
        text: String,
    },
    Photo {
        caption: String,
        file_path: Option<String>,
        width: u32,
        height: u32,
    },
    Video {
        caption: String,
        file_path: Option<String>,
        duration_sec: u32,
        width: u32,
        height: u32,
    },
    VoiceNote {
        duration_sec: u32,
        waveform: Vec<u8>,
        file_path: Option<String>,
    },
    Document {
        file_name: String,
        file_size_bytes: u64,
        mime_type: String,
        file_path: Option<String>,
    },
    Sticker {
        emoji: String,
        set_id: i64,
        width: u32,
        height: u32,
    },
    Call {
        is_video: bool,
        duration_sec: u32,
        is_missed: bool,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelegramReaction {
    pub emoji: String,
    pub count: u32,
    pub is_chosen: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelegramMessage {
    pub id: MessageId,
    pub chat_id: ChatId,
    pub sender_id: UserId,
    pub sender_name: String,
    pub date: u64,
    pub is_outgoing: bool,
    pub is_read: bool,
    pub edit_date: Option<u64>,
    pub reply_to_message_id: Option<MessageId>,
    pub content: MessageContent,
    pub reactions: Vec<TelegramReaction>,
}

impl TelegramMessage {
    pub fn summary_text(&self) -> String {
        match &self.content {
            MessageContent::Text { text } => text.clone(),
            MessageContent::Photo { caption, .. } => {
                if caption.is_empty() { "📷 Photo".into() } else { format!("📷 {}", caption) }
            }
            MessageContent::Video { caption, .. } => {
                if caption.is_empty() { "📹 Video".into() } else { format!("📹 {}", caption) }
            }
            MessageContent::VoiceNote { duration_sec, .. } => {
                format!("🎤 Voice message ({}s)", duration_sec)
            }
            MessageContent::Document { file_name, .. } => format!("📎 {}", file_name),
            MessageContent::Sticker { emoji, .. } => format!("{} Sticker", emoji),
            MessageContent::Call { is_missed, .. } => {
                if *is_missed { "☎️ Missed Call".into() } else { "📞 Phone Call".into() }
            }
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TelegramChat {
    pub id: ChatId,
    pub title: String,
    pub chat_type: ChatType,
    pub unread_count: u32,
    pub last_message: Option<TelegramMessage>,
    pub is_pinned: bool,
    pub is_muted: bool,
    pub avatar_path: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_user_display_name() {
        let user = TelegramUser {
            id: UserId(100),
            first_name: "Pavel".into(),
            last_name: "Durov".into(),
            username: Some("durov".into()),
            phone_number: "+123456789".into(),
            status: UserStatus::Online,
            is_bot: false,
            is_verified: true,
            is_premium: true,
            avatar_path: None,
        };
        assert_eq!(user.display_name(), "Pavel Durov");
    }

    #[test]
    fn test_message_summary() {
        let msg = TelegramMessage {
            id: MessageId(1),
            chat_id: ChatId(10),
            sender_id: UserId(100),
            sender_name: "Pavel".into(),
            date: 1700000000,
            is_outgoing: false,
            is_read: true,
            edit_date: None,
            reply_to_message_id: None,
            content: MessageContent::Text {
                text: "Hello from RustGram!".into(),
            },
            reactions: vec![],
        };
        assert_eq!(msg.summary_text(), "Hello from RustGram!");
    }
}
