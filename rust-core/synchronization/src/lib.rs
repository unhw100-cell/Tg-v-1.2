//! RustGram Synchronization Engine
//! Real-time updates synchronization, typing indicators tracking and unread counters.

use rustgram_core::{ChatId, UserId};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum ChatAction {
    Typing,
    RecordingVideo,
    UploadingVideo,
    RecordingVoice,
    UploadingVoice,
    UploadingPhoto,
    UploadingDocument,
    ChoosingSticker,
    Cancel,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserActionState {
    pub user_id: UserId,
    pub action: ChatAction,
    pub timestamp_sec: u64,
}

#[derive(Debug, Clone, Default)]
pub struct SyncEngine {
    /// Active typing/action states: (ChatId, UserId) -> UserActionState
    active_actions: HashMap<(ChatId, UserId), UserActionState>,
}

impl SyncEngine {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_user_action(&mut self, chat_id: ChatId, user_id: UserId, action: ChatAction, now_sec: u64) {
        if action == ChatAction::Cancel {
            self.active_actions.remove(&(chat_id, user_id));
        } else {
            self.active_actions.insert(
                (chat_id, user_id),
                UserActionState {
                    user_id,
                    action,
                    timestamp_sec: now_sec,
                },
            );
        }
    }

    /// Clean actions older than 5 seconds (standard Telegram action timeout)
    pub fn cleanup_stale_actions(&mut self, now_sec: u64) {
        self.active_actions.retain(|_, state| now_sec.saturating_sub(state.timestamp_sec) < 5);
    }

    pub fn get_chat_actions(&self, chat_id: ChatId) -> Vec<&UserActionState> {
        self.active_actions
            .iter()
            .filter(|((cid, _), _)| *cid == chat_id)
            .map(|(_, state)| state)
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sync_engine_actions() {
        let mut engine = SyncEngine::new();
        let chat = ChatId(1);
        let user = UserId(10);

        engine.set_user_action(chat, user, ChatAction::Typing, 100);
        assert_eq!(engine.get_chat_actions(chat).len(), 1);

        // Advance 6 seconds
        engine.cleanup_stale_actions(106);
        assert_eq!(engine.get_chat_actions(chat).len(), 0);
    }
}
