//! RustGram Database Engine
//! Local SQLite persistence for chats, messages, contacts and offline full-text search.

use rusqlite::{params, Connection};
use rustgram_core::{
    ChatId, ChatType, MessageContent, MessageId, Result, RustGramError, TelegramChat,
    TelegramMessage, UserId,
};
use std::sync::{Arc, Mutex};

pub struct RustGramDatabase {
    conn: Arc<Mutex<Connection>>,
}

impl RustGramDatabase {
    pub fn open_in_memory() -> Result<Self> {
        let conn =
            Connection::open_in_memory().map_err(|e| RustGramError::Database(e.to_string()))?;
        let db = Self {
            conn: Arc::new(Mutex::new(conn)),
        };
        db.init_tables()?;
        Ok(db)
    }

    pub fn open(path: &str) -> Result<Self> {
        let conn = Connection::open(path).map_err(|e| RustGramError::Database(e.to_string()))?;
        let db = Self {
            conn: Arc::new(Mutex::new(conn)),
        };
        db.init_tables()?;
        Ok(db)
    }

    fn init_tables(&self) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS chats (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                chat_type TEXT NOT NULL,
                unread_count INTEGER NOT NULL DEFAULT 0,
                is_pinned INTEGER NOT NULL DEFAULT 0,
                is_muted INTEGER NOT NULL DEFAULT 0,
                avatar_path TEXT
            );

            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER NOT NULL,
                chat_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                sender_name TEXT NOT NULL,
                date INTEGER NOT NULL,
                is_outgoing INTEGER NOT NULL,
                is_read INTEGER NOT NULL,
                summary_text TEXT NOT NULL,
                content_json TEXT NOT NULL,
                PRIMARY KEY (id, chat_id)
            );

            CREATE INDEX IF NOT EXISTS idx_messages_chat_date ON messages(chat_id, date DESC);
            ",
        )
        .map_err(|e| RustGramError::Database(e.to_string()))?;
        Ok(())
    }

    pub fn save_chat(&self, chat: &TelegramChat) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let type_str = match chat.chat_type {
            ChatType::Private => "private",
            ChatType::BasicGroup => "group",
            ChatType::Supergroup => "supergroup",
            ChatType::Channel => "channel",
            ChatType::SecretChat => "secret",
        };

        conn.execute(
            "INSERT OR REPLACE INTO chats (id, title, chat_type, unread_count, is_pinned, is_muted, avatar_path)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                chat.id.0,
                chat.title,
                type_str,
                chat.unread_count,
                if chat.is_pinned { 1 } else { 0 },
                if chat.is_muted { 1 } else { 0 },
                chat.avatar_path,
            ],
        )
        .map_err(|e| RustGramError::Database(e.to_string()))?;
        Ok(())
    }

    pub fn get_all_chats(&self) -> Result<Vec<TelegramChat>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn
            .prepare(
                "SELECT id, title, chat_type, unread_count, is_pinned, is_muted, avatar_path
                 FROM chats ORDER BY is_pinned DESC, id ASC",
            )
            .map_err(|e| RustGramError::Database(e.to_string()))?;

        let rows = stmt
            .query_map([], |row| {
                let id: i64 = row.get(0)?;
                let title: String = row.get(1)?;
                let chat_type_str: String = row.get(2)?;
                let unread_count: u32 = row.get(3)?;
                let is_pinned: i32 = row.get(4)?;
                let is_muted: i32 = row.get(5)?;
                let avatar_path: Option<String> = row.get(6)?;

                let chat_type = match chat_type_str.as_str() {
                    "group" => ChatType::BasicGroup,
                    "supergroup" => ChatType::Supergroup,
                    "channel" => ChatType::Channel,
                    "secret" => ChatType::SecretChat,
                    _ => ChatType::Private,
                };

                Ok(TelegramChat {
                    id: ChatId(id),
                    title,
                    chat_type,
                    unread_count,
                    last_message: None,
                    is_pinned: is_pinned == 1,
                    is_muted: is_muted == 1,
                    avatar_path,
                })
            })
            .map_err(|e| RustGramError::Database(e.to_string()))?;

        let mut chats = Vec::new();
        for r in rows {
            chats.push(r.map_err(|e| RustGramError::Database(e.to_string()))?);
        }
        Ok(chats)
    }

    pub fn save_message(&self, msg: &TelegramMessage) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        let content_json = serde_json::to_string(&msg.content)
            .map_err(|e| RustGramError::Serialization(e.to_string()))?;

        conn.execute(
            "INSERT OR REPLACE INTO messages (id, chat_id, sender_id, sender_name, date, is_outgoing, is_read, summary_text, content_json)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            params![
                msg.id.0,
                msg.chat_id.0,
                msg.sender_id.0,
                msg.sender_name,
                msg.date,
                if msg.is_outgoing { 1 } else { 0 },
                if msg.is_read { 1 } else { 0 },
                msg.summary_text(),
                content_json,
            ],
        )
        .map_err(|e| RustGramError::Database(e.to_string()))?;
        Ok(())
    }

    pub fn delete_message(&self, chat_id: ChatId, msg_id: MessageId) -> Result<()> {
        let conn = self.conn.lock().unwrap();
        conn.execute(
            "DELETE FROM messages WHERE chat_id = ?1 AND id = ?2",
            params![chat_id.0, msg_id.0],
        )
        .map_err(|e| RustGramError::Database(e.to_string()))?;
        Ok(())
    }

    pub fn get_chat_messages(&self, chat_id: ChatId, limit: u32) -> Result<Vec<TelegramMessage>> {
        let conn = self.conn.lock().unwrap();
        let mut stmt = conn
            .prepare(
                "SELECT id, chat_id, sender_id, sender_name, date, is_outgoing, is_read, content_json
                 FROM messages WHERE chat_id = ?1 ORDER BY date ASC LIMIT ?2",
            )
            .map_err(|e| RustGramError::Database(e.to_string()))?;

        let rows = stmt
            .query_map(params![chat_id.0, limit], |row| Self::map_message_row(row))
            .map_err(|e| RustGramError::Database(e.to_string()))?;

        let mut messages = Vec::new();
        for r in rows {
            messages.push(r.map_err(|e| RustGramError::Database(e.to_string()))?);
        }
        Ok(messages)
    }

    pub fn search_messages(&self, query: &str, limit: u32) -> Result<Vec<TelegramMessage>> {
        let conn = self.conn.lock().unwrap();
        let pattern = format!("%{}%", query);
        let mut stmt = conn
            .prepare(
                "SELECT id, chat_id, sender_id, sender_name, date, is_outgoing, is_read, content_json
                 FROM messages WHERE summary_text LIKE ?1 OR sender_name LIKE ?1
                 ORDER BY date DESC LIMIT ?2",
            )
            .map_err(|e| RustGramError::Database(e.to_string()))?;

        let rows = stmt
            .query_map(params![pattern, limit], |row| Self::map_message_row(row))
            .map_err(|e| RustGramError::Database(e.to_string()))?;

        let mut messages = Vec::new();
        for r in rows {
            messages.push(r.map_err(|e| RustGramError::Database(e.to_string()))?);
        }
        Ok(messages)
    }

    fn map_message_row(row: &rusqlite::Row) -> rusqlite::Result<TelegramMessage> {
        let id: i64 = row.get(0)?;
        let chat_id: i64 = row.get(1)?;
        let sender_id: i64 = row.get(2)?;
        let sender_name: String = row.get(3)?;
        let date: u64 = row.get(4)?;
        let is_outgoing: i32 = row.get(5)?;
        let is_read: i32 = row.get(6)?;
        let content_json: String = row.get(7)?;

        let content: MessageContent = serde_json::from_str(&content_json)
            .unwrap_or(MessageContent::Text {
                text: "[Unknown format]".into(),
            });

        Ok(TelegramMessage {
            id: MessageId(id),
            chat_id: ChatId(chat_id),
            sender_id: UserId(sender_id),
            sender_name,
            date,
            is_outgoing: is_outgoing == 1,
            is_read: is_read == 1,
            edit_date: None,
            reply_to_message_id: None,
            content,
            reactions: vec![],
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_database_crud_and_search() {
        let db = RustGramDatabase::open_in_memory().expect("open DB in memory");
        let chat = TelegramChat {
            id: ChatId(999),
            title: "RustGram Official".into(),
            chat_type: ChatType::Supergroup,
            unread_count: 5,
            last_message: None,
            is_pinned: true,
            is_muted: false,
            avatar_path: None,
        };

        db.save_chat(&chat).expect("save chat");
        let chats = db.get_all_chats().expect("get chats");
        assert_eq!(chats.len(), 1);
        assert_eq!(chats[0].title, "RustGram Official");

        let msg = TelegramMessage {
            id: MessageId(1),
            chat_id: ChatId(999),
            sender_id: UserId(42),
            sender_name: "Rust Dev".into(),
            date: 1700000001,
            is_outgoing: false,
            is_read: false,
            edit_date: None,
            reply_to_message_id: None,
            content: MessageContent::Text {
                text: "Welcome to RustGram!".into(),
            },
            reactions: vec![],
        };
        db.save_message(&msg).expect("save message");

        let loaded = db.get_chat_messages(ChatId(999), 10).expect("load messages");
        assert_eq!(loaded.len(), 1);

        let search_hits = db.search_messages("RustGram", 10).expect("search messages");
        assert_eq!(search_hits.len(), 1);
        assert_eq!(search_hits[0].summary_text(), "Welcome to RustGram!");

        db.delete_message(ChatId(999), MessageId(1))
            .expect("delete message");
        assert_eq!(db.get_chat_messages(ChatId(999), 10).unwrap().len(), 0);
    }
}
