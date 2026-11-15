//! Local real-time state and versioned customization profile models.
//!
//! The profile schema is ready to serialize for a future account-linked sync
//! service. This crate does not currently upload user preferences to a server.

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

/// Versioned, account-neutral appearance settings. Never put Telegram session
/// data, phone numbers, API credentials, or message content in this profile.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CustomizationProfile {
    pub schema_version: u32,
    pub revision: u64,
    pub updated_at_unix_ms: u64,
    pub language_code: String,
    pub theme_preset: String,
    /// ARGB packed as 0xAARRGGBB.
    pub accent_argb: u32,
    pub wallpaper_style: String,
    pub bubble_radius_dp: u8,
    /// Percentage, 80..=140.
    pub font_scale_percent: u8,
    pub reduced_transparency: bool,
    pub reduced_motion: bool,
}

impl CustomizationProfile {
    pub const CURRENT_SCHEMA_VERSION: u32 = 1;

    pub fn validate(&self) -> Result<(), &'static str> {
        if self.schema_version != Self::CURRENT_SCHEMA_VERSION {
            return Err("unsupported customization schema version");
        }
        if !matches!(self.language_code.as_str(), "ru" | "en") {
            return Err("language_code must be 'ru' or 'en'");
        }
        if !matches!(
            self.theme_preset.as_str(),
            "DAY" | "CLASSIC" | "TINTED_DARK" | "OLED_NIGHT"
        ) {
            return Err("unknown theme preset");
        }
        if !matches!(
            self.wallpaper_style.as_str(),
            "TELEGRAM_PATTERN"
                | "ARCTIC_ICE"
                | "MIDNIGHT_AURORA"
                | "LAVENDER_MIST"
                | "SOLID_MINIMAL"
        ) {
            return Err("unknown wallpaper style");
        }
        if !(4..=40).contains(&self.bubble_radius_dp) {
            return Err("bubble radius must be between 4 and 40 dp");
        }
        if !(80..=140).contains(&self.font_scale_percent) {
            return Err("font scale must be between 80 and 140 percent");
        }
        Ok(())
    }
}

/// Deterministic last-write-wins merge primitive for future cloud sync.
/// A remote profile wins only if its timestamp is newer; ties prefer the
/// higher revision. The caller must persist and upload the chosen result.
pub fn merge_customization_profiles(
    local: &CustomizationProfile,
    remote: &CustomizationProfile,
) -> Result<CustomizationProfile, &'static str> {
    local.validate()?;
    remote.validate()?;
    if remote.updated_at_unix_ms > local.updated_at_unix_ms
        || (remote.updated_at_unix_ms == local.updated_at_unix_ms
            && remote.revision > local.revision)
    {
        Ok(remote.clone())
    } else {
        Ok(local.clone())
    }
}

#[derive(Debug, Clone, Default)]
pub struct SyncEngine {
    /// Active typing/action states: (ChatId, UserId) -> UserActionState.
    active_actions: HashMap<(ChatId, UserId), UserActionState>,
    customization_profile: Option<CustomizationProfile>,
}

impl SyncEngine {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_user_action(
        &mut self,
        chat_id: ChatId,
        user_id: UserId,
        action: ChatAction,
        now_sec: u64,
    ) {
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

    /// Clean actions older than 5 seconds (standard Telegram action timeout).
    pub fn cleanup_stale_actions(&mut self, now_sec: u64) {
        self.active_actions
            .retain(|_, state| now_sec.saturating_sub(state.timestamp_sec) < 5);
    }

    pub fn get_chat_actions(&self, chat_id: ChatId) -> Vec<&UserActionState> {
        self.active_actions
            .iter()
            .filter(|((cid, _), _)| *cid == chat_id)
            .map(|(_, state)| state)
            .collect()
    }

    pub fn set_customization_profile(
        &mut self,
        profile: CustomizationProfile,
    ) -> Result<(), &'static str> {
        profile.validate()?;
        self.customization_profile = Some(profile);
        Ok(())
    }

    pub fn customization_profile(&self) -> Option<&CustomizationProfile> {
        self.customization_profile.as_ref()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn profile(revision: u64, updated_at_unix_ms: u64) -> CustomizationProfile {
        CustomizationProfile {
            schema_version: 1,
            revision,
            updated_at_unix_ms,
            language_code: "ru".into(),
            theme_preset: "TINTED_DARK".into(),
            accent_argb: 0xFF007AFF,
            wallpaper_style: "ARCTIC_ICE".into(),
            bubble_radius_dp: 22,
            font_scale_percent: 100,
            reduced_transparency: false,
            reduced_motion: false,
        }
    }

    #[test]
    fn test_sync_engine_actions() {
        let mut engine = SyncEngine::new();
        let chat = ChatId(1);
        let user = UserId(10);
        engine.set_user_action(chat, user, ChatAction::Typing, 100);
        assert_eq!(engine.get_chat_actions(chat).len(), 1);
        engine.cleanup_stale_actions(106);
        assert_eq!(engine.get_chat_actions(chat).len(), 0);
    }

    #[test]
    fn test_customization_profile_round_trip_and_validation() {
        let profile = profile(1, 1000);
        profile.validate().expect("profile is valid");
        let json = serde_json::to_string(&profile).unwrap();
        let decoded: CustomizationProfile = serde_json::from_str(&json).unwrap();
        assert_eq!(decoded, profile);
    }

    #[test]
    fn test_merge_selects_newer_profile() {
        let local = profile(9, 1000);
        let remote = profile(1, 2000);
        assert_eq!(
            merge_customization_profiles(&local, &remote).unwrap(),
            remote
        );
    }

    #[test]
    fn test_profile_rejects_unknown_theme() {
        let mut candidate = profile(1, 1000);
        candidate.theme_preset = "made-up".into();
        assert!(candidate.validate().is_err());
    }
}
