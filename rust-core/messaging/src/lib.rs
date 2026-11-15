//! RustGram Messaging Engine
//! Message parsing, rich text entities (bold, italic, code, spoilers, mentions, hashtags, URLs),
//! drafts, search and reaction handling.

use rustgram_core::{ChatId, MessageId, TelegramMessage, TelegramReaction};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum MessageEntityType {
    Mention,
    Hashtag,
    BotCommand,
    Url,
    EmailAddress,
    Bold,
    Italic,
    Underline,
    Strikethrough,
    Spoiler,
    Code,
    Pre { language: String },
    TextUrl { url: String },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FormattedEntity {
    pub offset: u32,
    pub length: u32,
    pub entity_type: MessageEntityType,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FormattedText {
    pub text: String,
    pub entities: Vec<FormattedEntity>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DraftMessage {
    pub chat_id: ChatId,
    pub reply_to_message_id: Option<MessageId>,
    pub text: String,
    pub date: u64,
}

#[derive(Debug, Clone, Default)]
pub struct MessageProcessor;

impl MessageProcessor {
    pub fn new() -> Self {
        Self
    }

    /// Parse markdown & Telegram inline tokens into FormattedText entities:
    /// `**bold**`, `__italic__`, `~~strike~~`, `||spoiler||`, `` `code` ``, `@mentions`, `#hashtags`, `https://urls`
    pub fn parse_markdown(input: &str) -> FormattedText {
        let mut clean_text = String::new();
        let mut entities = Vec::new();
        let chars: Vec<char> = input.chars().collect();
        let mut i = 0;

        while i < chars.len() {
            if i + 1 < chars.len() && chars[i] == '*' && chars[i + 1] == '*' {
                if let Some((content, next_i)) = Self::read_delimited(&chars, i + 2, '*', '*') {
                    let offset = clean_text.encode_utf16().count() as u32;
                    let length = content.encode_utf16().count() as u32;
                    clean_text.push_str(&content);
                    if length > 0 {
                        entities.push(FormattedEntity {
                            offset,
                            length,
                            entity_type: MessageEntityType::Bold,
                        });
                    }
                    i = next_i;
                    continue;
                }
            } else if i + 1 < chars.len() && chars[i] == '_' && chars[i + 1] == '_' {
                if let Some((content, next_i)) = Self::read_delimited(&chars, i + 2, '_', '_') {
                    let offset = clean_text.encode_utf16().count() as u32;
                    let length = content.encode_utf16().count() as u32;
                    clean_text.push_str(&content);
                    if length > 0 {
                        entities.push(FormattedEntity {
                            offset,
                            length,
                            entity_type: MessageEntityType::Italic,
                        });
                    }
                    i = next_i;
                    continue;
                }
            } else if i + 1 < chars.len() && chars[i] == '~' && chars[i + 1] == '~' {
                if let Some((content, next_i)) = Self::read_delimited(&chars, i + 2, '~', '~') {
                    let offset = clean_text.encode_utf16().count() as u32;
                    let length = content.encode_utf16().count() as u32;
                    clean_text.push_str(&content);
                    if length > 0 {
                        entities.push(FormattedEntity {
                            offset,
                            length,
                            entity_type: MessageEntityType::Strikethrough,
                        });
                    }
                    i = next_i;
                    continue;
                }
            } else if i + 1 < chars.len() && chars[i] == '|' && chars[i + 1] == '|' {
                if let Some((content, next_i)) = Self::read_delimited(&chars, i + 2, '|', '|') {
                    let offset = clean_text.encode_utf16().count() as u32;
                    let length = content.encode_utf16().count() as u32;
                    clean_text.push_str(&content);
                    if length > 0 {
                        entities.push(FormattedEntity {
                            offset,
                            length,
                            entity_type: MessageEntityType::Spoiler,
                        });
                    }
                    i = next_i;
                    continue;
                }
            } else if chars[i] == '`' {
                let start_idx = clean_text.encode_utf16().count() as u32;
                let mut j = i + 1;
                let mut content = String::new();
                while j < chars.len() && chars[j] != '`' {
                    content.push(chars[j]);
                    j += 1;
                }
                if j < chars.len() && !content.is_empty() {
                    let len = content.encode_utf16().count() as u32;
                    clean_text.push_str(&content);
                    entities.push(FormattedEntity {
                        offset: start_idx,
                        length: len,
                        entity_type: MessageEntityType::Code,
                    });
                    i = j + 1;
                    continue;
                }
            }

            clean_text.push(chars[i]);
            i += 1;
        }

        // Detect @mentions, #hashtags, /commands, and URLs in clean_text
        Self::scan_inline_tokens(&clean_text, &mut entities);

        FormattedText {
            text: clean_text,
            entities,
        }
    }

    fn read_delimited(chars: &[char], start: usize, c1: char, c2: char) -> Option<(String, usize)> {
        let mut j = start;
        let mut content = String::new();
        while j + 1 < chars.len() {
            if chars[j] == c1 && chars[j + 1] == c2 {
                return Some((content, j + 2));
            }
            content.push(chars[j]);
            j += 1;
        }
        None
    }

    fn scan_inline_tokens(text: &str, entities: &mut Vec<FormattedEntity>) {
        let mut byte_idx = 0;
        let bytes = text.as_bytes();
        let len = bytes.len();

        while byte_idx < len {
            let rest = &text[byte_idx..];
            if rest.starts_with("https://") || rest.starts_with("http://") {
                let token_end = rest.find(|c: char| c.is_whitespace()).unwrap_or(rest.len());
                let token = &rest[..token_end];
                let offset = text[..byte_idx].encode_utf16().count() as u32;
                let u16_len = token.encode_utf16().count() as u32;
                entities.push(FormattedEntity {
                    offset,
                    length: u16_len,
                    entity_type: MessageEntityType::Url,
                });
                byte_idx += token_end;
                continue;
            }

            let ch = rest.chars().next().unwrap();
            let prev_is_boundary = byte_idx == 0
                || text[..byte_idx]
                    .chars()
                    .last()
                    .map(|c| c.is_whitespace() || c == '(')
                    .unwrap_or(true);

            if prev_is_boundary && (ch == '@' || ch == '#') {
                let mut token_bytes = ch.len_utf8();
                for sub_ch in rest[ch.len_utf8()..].chars() {
                    if sub_ch.is_alphanumeric() || sub_ch == '_' {
                        token_bytes += sub_ch.len_utf8();
                    } else {
                        break;
                    }
                }
                if token_bytes > 1 {
                    let token = &rest[..token_bytes];
                    let offset = text[..byte_idx].encode_utf16().count() as u32;
                    let u16_len = token.encode_utf16().count() as u32;
                    entities.push(FormattedEntity {
                        offset,
                        length: u16_len,
                        entity_type: if ch == '@' {
                            MessageEntityType::Mention
                        } else {
                            MessageEntityType::Hashtag
                        },
                    });
                    byte_idx += token_bytes;
                    continue;
                }
            }

            byte_idx += ch.len_utf8();
        }
    }

    /// Toggle a reaction on a message
    pub fn toggle_reaction(message: &mut TelegramMessage, emoji: &str) {
        if let Some(pos) = message.reactions.iter().position(|r| r.emoji == emoji) {
            if message.reactions[pos].is_chosen {
                message.reactions[pos].is_chosen = false;
                if message.reactions[pos].count > 0 {
                    message.reactions[pos].count -= 1;
                }
                if message.reactions[pos].count == 0 {
                    message.reactions.remove(pos);
                }
            } else {
                message.reactions[pos].is_chosen = true;
                message.reactions[pos].count += 1;
            }
        } else {
            message.reactions.push(TelegramReaction {
                emoji: emoji.to_string(),
                count: 1,
                is_chosen: true,
            });
        }
    }
}

/// Build real TDLib JSON requests in Rust. This keeps Telegram-domain request
/// construction out of the Android UI/repository layer.
pub fn build_messaging_request(operation: &str, payload: &Value) -> Result<Value, String> {
    match operation {
        "send_text" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 {
                return Err("chat_id is required".into());
            }
            let raw_text = payload
                .get("raw_text")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if raw_text.trim().is_empty() {
                return Err("message text must not be empty".into());
            }
            let formatted = MessageProcessor::parse_markdown(raw_text);
            let entities: Vec<Value> = formatted.entities.iter().map(|entity| {
                let entity_type = match &entity.entity_type {
                    MessageEntityType::Mention => json!({"@type":"textEntityTypeMention"}),
                    MessageEntityType::Hashtag => json!({"@type":"textEntityTypeHashtag"}),
                    MessageEntityType::BotCommand => json!({"@type":"textEntityTypeBotCommand"}),
                    MessageEntityType::Url => json!({"@type":"textEntityTypeUrl"}),
                    MessageEntityType::EmailAddress => json!({"@type":"textEntityTypeEmailAddress"}),
                    MessageEntityType::Bold => json!({"@type":"textEntityTypeBold"}),
                    MessageEntityType::Italic => json!({"@type":"textEntityTypeItalic"}),
                    MessageEntityType::Underline => json!({"@type":"textEntityTypeUnderline"}),
                    MessageEntityType::Strikethrough => json!({"@type":"textEntityTypeStrikethrough"}),
                    MessageEntityType::Spoiler => json!({"@type":"textEntityTypeSpoiler"}),
                    MessageEntityType::Code => json!({"@type":"textEntityTypeCode"}),
                    MessageEntityType::Pre { language } => json!({"@type":"textEntityTypePre", "language":language}),
                    MessageEntityType::TextUrl { url } => json!({"@type":"textEntityTypeTextUrl", "url":url}),
                };
                json!({"@type":"textEntity", "offset":entity.offset, "length":entity.length, "type":entity_type})
            }).collect();
            let reply_to = payload
                .get("reply_to_message_id")
                .and_then(Value::as_i64)
                .filter(|id| *id > 0)
                .map(|message_id| {
                    json!({
                        "@type":"inputMessageReplyToMessage",
                        "message_id":message_id,
                        "quote":null,
                        "checklist_task_id":0
                    })
                });
            let mut options = json!({
                "@type":"messageSendOptions",
                "disable_notification":payload.get("disable_notification").and_then(Value::as_bool).unwrap_or(false),
                "scheduling_state":null
            });
            if let Some(send_date) = payload.get("send_at_unix_sec").and_then(Value::as_i64) {
                let now = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .map_err(|_| "system clock is before UNIX epoch")?
                    .as_secs() as i64;
                if send_date <= now || send_date > now + 367 * 24 * 60 * 60 {
                    return Err(
                        "scheduled send date must be in the future and within 367 days".into(),
                    );
                }
                options["scheduling_state"] = json!({
                    "@type":"messageSchedulingStateSendAtDate",
                    "send_date":send_date,
                    "repeat_period":0
                });
            }
            Ok(json!({
                "@type":"sendMessage",
                "chat_id":chat_id,
                "topic_id":null,
                "reply_to":reply_to,
                "options":options,
                "input_message_content":{
                    "@type":"inputMessageText",
                    "text":{"@type":"formattedText", "text":formatted.text, "entities":entities}
                }
            }))
        }
        "pin_message" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let message_id = payload
                .get("message_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 || message_id == 0 {
                return Err("chat_id and message_id are required".into());
            }
            Ok(json!({
                "@type":"pinChatMessage",
                "chat_id":chat_id,
                "message_id":message_id,
                "disable_notification":payload.get("disable_notification").and_then(Value::as_bool).unwrap_or(false),
                "only_for_self":payload.get("only_for_self").and_then(Value::as_bool).unwrap_or(false)
            }))
        }
        "delete_message" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let message_id = payload
                .get("message_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 || message_id == 0 {
                return Err("chat_id and message_id are required".into());
            }
            Ok(json!({
                "@type":"deleteMessages",
                "chat_id":chat_id,
                "message_ids":[message_id],
                "revoke":payload.get("revoke").and_then(Value::as_bool).unwrap_or(true)
            }))
        }
        "delete_chat_history" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 {
                return Err("chat_id is required".into());
            }
            Ok(json!({
                "@type":"deleteChatHistory",
                "chat_id":chat_id,
                "remove_from_list":payload.get("remove_from_list").and_then(Value::as_bool).unwrap_or(true),
                "revoke":payload.get("revoke").and_then(Value::as_bool).unwrap_or(false)
            }))
        }
        "search_public_chats" => {
            let query = payload
                .get("query")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .trim();
            if query.is_empty() || query.chars().count() > 255 {
                return Err("public chat search query must contain 1..255 characters".into());
            }
            Ok(json!({"@type":"searchPublicChats", "query":query}))
        }
        "add_reaction" | "remove_reaction" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let message_id = payload
                .get("message_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let emoji = payload
                .get("emoji")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if chat_id == 0
                || message_id == 0
                || emoji.trim().is_empty()
                || emoji.chars().count() > 16
            {
                return Err("chat_id, message_id and a valid emoji reaction are required".into());
            }
            let reaction_type = json!({"@type":"reactionTypeEmoji", "emoji":emoji});
            if operation == "remove_reaction" {
                Ok(
                    json!({"@type":"removeMessageReaction", "chat_id":chat_id, "message_id":message_id, "reaction_type":reaction_type}),
                )
            } else {
                Ok(json!({
                    "@type":"addMessageReaction",
                    "chat_id":chat_id,
                    "message_id":message_id,
                    "reaction_type":reaction_type,
                    "is_big":false,
                    "update_recent_reactions":true
                }))
            }
        }
        "set_chat_archived" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 {
                return Err("chat_id is required".into());
            }
            let archived = payload
                .get("archived")
                .and_then(Value::as_bool)
                .unwrap_or(false);
            if archived {
                Ok(json!({
                    "@type":"addChatToList",
                    "chat_id":chat_id,
                    "chat_list":{"@type":"chatListArchive"}
                }))
            } else {
                Ok(json!({
                    "@type":"removeChatFromList",
                    "chat_id":chat_id,
                    "chat_list":{"@type":"chatListArchive"}
                }))
            }
        }
        "pin_chat" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 {
                return Err("chat_id is required".into());
            }
            let list_type = match payload
                .get("list")
                .and_then(Value::as_str)
                .unwrap_or("main")
            {
                "archive" => "chatListArchive",
                "main" => "chatListMain",
                _ => return Err("chat list must be main or archive".into()),
            };
            Ok(json!({
                "@type":"toggleChatIsPinned",
                "chat_list":{"@type":list_type},
                "chat_id":chat_id,
                "is_pinned":payload.get("is_pinned").and_then(Value::as_bool).unwrap_or(false)
            }))
        }
        "set_chat_mute" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 {
                return Err("chat_id is required".into());
            }
            let muted = payload
                .get("muted")
                .and_then(Value::as_bool)
                .unwrap_or(false);
            let mute_for = if muted { 31_536_000 } else { 0 };
            Ok(json!({
                "@type":"setChatNotificationSettings",
                "chat_id":chat_id,
                "notification_settings":{
                    "@type":"chatNotificationSettings",
                    "use_default_mute_for":false,
                    "mute_for":mute_for,
                    "use_default_sound":true,
                    "sound_id":0,
                    "use_default_show_preview":true,
                    "show_preview":true,
                    "use_default_mute_stories":true,
                    "mute_stories":false,
                    "use_default_story_sound":true,
                    "story_sound_id":0,
                    "use_default_show_story_poster":true,
                    "show_story_poster":true,
                    "use_default_disable_pinned_message_notifications":true,
                    "disable_pinned_message_notifications":false,
                    "use_default_disable_mention_notifications":true,
                    "disable_mention_notifications":false
                }
            }))
        }
        "send_poll" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 {
                return Err("chat_id is required".into());
            }
            let question = payload
                .get("question")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .trim();
            if question.is_empty() || question.chars().count() > 255 {
                return Err("poll question must contain 1..255 characters".into());
            }
            let options = payload
                .get("options")
                .and_then(Value::as_array)
                .ok_or("poll options are required")?;
            if !(2..=10).contains(&options.len()) {
                return Err("poll must contain between 2 and 10 options".into());
            }
            let mut formatted_options = Vec::with_capacity(options.len());
            for option in options {
                let text = option.as_str().unwrap_or_default().trim();
                if text.is_empty() || text.chars().count() > 100 {
                    return Err("each poll option must contain 1..100 characters".into());
                }
                formatted_options
                    .push(json!({"@type":"formattedText", "text":text, "entities":[]}));
            }
            let is_quiz = payload
                .get("is_quiz")
                .and_then(Value::as_bool)
                .unwrap_or(false);
            let poll_type = if is_quiz {
                let correct = payload
                    .get("correct_option_id")
                    .and_then(Value::as_u64)
                    .filter(|id| (*id as usize) < options.len())
                    .ok_or("quiz requires a valid correct_option_id")?;
                json!({"@type":"pollTypeQuiz", "correct_option_id":correct, "explanation":{"@type":"formattedText", "text":"", "entities":[]}})
            } else {
                json!({"@type":"pollTypeRegular", "allow_multiple_answers":payload.get("allow_multiple_answers").and_then(Value::as_bool).unwrap_or(false)})
            };
            Ok(json!({
                "@type":"sendMessage", "chat_id":chat_id,
                "options":{"@type":"messageSendOptions", "disable_notification":false},
                "input_message_content":{
                    "@type":"inputMessagePoll",
                    "question":{"@type":"formattedText", "text":question, "entities":[]},
                    "options":formatted_options,
                    "is_anonymous":true,
                    "type":poll_type,
                    "open_period":0,
                    "close_date":0,
                    "is_closed":false
                }
            }))
        }
        "forward" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let from_chat_id = payload
                .get("from_chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let message_id = payload
                .get("message_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if chat_id == 0 || from_chat_id == 0 || message_id == 0 {
                return Err("chat_id, from_chat_id, and message_id are required".into());
            }
            Ok(json!({
                "@type":"forwardMessages",
                "chat_id":chat_id,
                "topic_id":null,
                "from_chat_id":from_chat_id,
                "message_ids":[message_id],
                "options":{"@type":"messageSendOptions", "disable_notification":payload.get("disable_notification").and_then(Value::as_bool).unwrap_or(false)},
                "send_copy":payload.get("send_copy").and_then(Value::as_bool).unwrap_or(false),
                "remove_caption":payload.get("remove_caption").and_then(Value::as_bool).unwrap_or(false)
            }))
        }
        "edit_text" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let message_id = payload
                .get("message_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let raw_text = payload
                .get("raw_text")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if chat_id == 0 || message_id == 0 || raw_text.trim().is_empty() {
                return Err("chat_id, message_id, and non-empty raw_text are required".into());
            }
            let formatted = MessageProcessor::parse_markdown(raw_text);
            let entities: Vec<Value> = formatted.entities.iter().map(|entity| {
                let entity_type = match &entity.entity_type {
                    MessageEntityType::Mention => json!({"@type":"textEntityTypeMention"}),
                    MessageEntityType::Hashtag => json!({"@type":"textEntityTypeHashtag"}),
                    MessageEntityType::BotCommand => json!({"@type":"textEntityTypeBotCommand"}),
                    MessageEntityType::Url => json!({"@type":"textEntityTypeUrl"}),
                    MessageEntityType::EmailAddress => json!({"@type":"textEntityTypeEmailAddress"}),
                    MessageEntityType::Bold => json!({"@type":"textEntityTypeBold"}),
                    MessageEntityType::Italic => json!({"@type":"textEntityTypeItalic"}),
                    MessageEntityType::Underline => json!({"@type":"textEntityTypeUnderline"}),
                    MessageEntityType::Strikethrough => json!({"@type":"textEntityTypeStrikethrough"}),
                    MessageEntityType::Spoiler => json!({"@type":"textEntityTypeSpoiler"}),
                    MessageEntityType::Code => json!({"@type":"textEntityTypeCode"}),
                    MessageEntityType::Pre { language } => json!({"@type":"textEntityTypePre", "language":language}),
                    MessageEntityType::TextUrl { url } => json!({"@type":"textEntityTypeTextUrl", "url":url}),
                };
                json!({"@type":"textEntity", "offset":entity.offset, "length":entity.length, "type":entity_type})
            }).collect();
            Ok(json!({
                "@type":"editMessageText",
                "chat_id":chat_id,
                "message_id":message_id,
                "reply_markup":null,
                "input_message_content":{
                    "@type":"inputMessageText",
                    "text":{"@type":"formattedText", "text":formatted.text, "entities":entities},
                    "link_preview_options":null,
                    "clear_draft":false
                }
            }))
        }
        "send_voice" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let file_path = payload
                .get("file_path")
                .and_then(Value::as_str)
                .unwrap_or_default();
            let duration = payload
                .get("duration")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let waveform = payload
                .get("waveform")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if chat_id == 0 || file_path.trim().is_empty() || duration <= 0 || duration > 3600 {
                return Err("chat_id, local file_path, and duration (1..3600) are required".into());
            }
            Ok(json!({
                "@type":"sendMessage",
                "chat_id":chat_id,
                "reply_to":null,
                "options":{"@type":"messageSendOptions", "disable_notification":false},
                "input_message_content":{
                    "@type":"inputMessageVoiceNote",
                    "voice_note":{"@type":"inputFileLocal", "path":file_path},
                    "duration":duration,
                    "waveform":waveform,
                    "caption":null,
                    "self_destruct_type":null
                }
            }))
        }
        "create_private_chat" => {
            let user_id = payload
                .get("user_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if user_id <= 0 {
                return Err("positive user_id is required".into());
            }
            Ok(json!({"@type":"createPrivateChat", "user_id":user_id, "force":false}))
        }
        "create_secret_chat" => {
            let user_id = payload
                .get("user_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            if user_id <= 0 {
                return Err("positive user_id is required".into());
            }
            Ok(json!({"@type":"createNewSecretChat", "user_id":user_id}))
        }
        "create_supergroup" => {
            let title = payload
                .get("title")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .trim();
            let description = payload
                .get("description")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if title.is_empty() || title.chars().count() > 128 || description.chars().count() > 255
            {
                return Err(
                    "title must contain 1..128 characters and description at most 255".into(),
                );
            }
            Ok(json!({
                "@type":"createNewSupergroupChat",
                "title":title,
                "is_forum":false,
                "is_channel":payload.get("is_channel").and_then(Value::as_bool).unwrap_or(false),
                "description":description,
                "location":null,
                "message_auto_delete_time":0,
                "for_import":false
            }))
        }
        "add_chat_members" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let user_ids = payload
                .get("user_ids")
                .and_then(Value::as_array)
                .ok_or("user_ids are required")?;
            let members: Vec<i64> = user_ids
                .iter()
                .filter_map(Value::as_i64)
                .filter(|id| *id > 0)
                .collect();
            if chat_id == 0 || members.is_empty() {
                return Err("chat_id and at least one positive user_id are required".into());
            }
            Ok(
                json!({"@type":"addChatMembers", "chat_id":chat_id, "user_ids":members, "forward_limit":0}),
            )
        }
        "set_chat_ttl" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let seconds = payload.get("seconds").and_then(Value::as_i64).unwrap_or(-1);
            if chat_id == 0 || !(0..=31_536_000).contains(&seconds) {
                return Err("chat_id and seconds in 0..31536000 are required".into());
            }
            Ok(
                json!({"@type":"setChatMessageAutoDeleteTime", "chat_id":chat_id, "message_auto_delete_time":seconds}),
            )
        }
        "send_photo" => {
            let chat_id = payload
                .get("chat_id")
                .and_then(Value::as_i64)
                .unwrap_or_default();
            let file_path = payload
                .get("file_path")
                .and_then(Value::as_str)
                .unwrap_or_default();
            let caption = payload
                .get("caption")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if chat_id == 0 || file_path.trim().is_empty() {
                return Err("chat_id and local photo file_path are required".into());
            }
            let formatted = MessageProcessor::parse_markdown(caption);
            let entities: Vec<Value> = formatted.entities.iter().map(|entity| {
                let entity_type = match &entity.entity_type {
                    MessageEntityType::Mention => json!({"@type":"textEntityTypeMention"}),
                    MessageEntityType::Hashtag => json!({"@type":"textEntityTypeHashtag"}),
                    MessageEntityType::BotCommand => json!({"@type":"textEntityTypeBotCommand"}),
                    MessageEntityType::Url => json!({"@type":"textEntityTypeUrl"}),
                    MessageEntityType::EmailAddress => json!({"@type":"textEntityTypeEmailAddress"}),
                    MessageEntityType::Bold => json!({"@type":"textEntityTypeBold"}),
                    MessageEntityType::Italic => json!({"@type":"textEntityTypeItalic"}),
                    MessageEntityType::Underline => json!({"@type":"textEntityTypeUnderline"}),
                    MessageEntityType::Strikethrough => json!({"@type":"textEntityTypeStrikethrough"}),
                    MessageEntityType::Spoiler => json!({"@type":"textEntityTypeSpoiler"}),
                    MessageEntityType::Code => json!({"@type":"textEntityTypeCode"}),
                    MessageEntityType::Pre { language } => json!({"@type":"textEntityTypePre", "language":language}),
                    MessageEntityType::TextUrl { url } => json!({"@type":"textEntityTypeTextUrl", "url":url}),
                };
                json!({"@type":"textEntity", "offset":entity.offset, "length":entity.length, "type":entity_type})
            }).collect();
            Ok(json!({
                "@type":"sendMessage",
                "chat_id":chat_id,
                "reply_to":null,
                "options":{"@type":"messageSendOptions", "disable_notification":false},
                "input_message_content":{
                    "@type":"inputMessagePhoto",
                    "photo":{"@type":"inputFileLocal", "path":file_path},
                    "thumbnail":null,
                    "added_sticker_file_ids":[],
                    "width":0,
                    "height":0,
                    "caption":{"@type":"formattedText", "text":formatted.text, "entities":entities},
                    "show_caption_above_media":false,
                    "self_destruct_type":null
                }
            }))
        }
        _ => Err(format!("unsupported messaging operation: {operation}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rustgram_core::{MessageContent, UserId};

    #[test]
    fn test_markdown_and_tokens_parsing() {
        let input = "Hello **world** __fast__ `rust` @durov #rustgram https://telegram.org";
        let formatted = MessageProcessor::parse_markdown(input);
        assert_eq!(
            formatted.text,
            "Hello world fast rust @durov #rustgram https://telegram.org"
        );
        assert!(formatted
            .entities
            .iter()
            .any(|e| e.entity_type == MessageEntityType::Bold));
        assert!(formatted
            .entities
            .iter()
            .any(|e| e.entity_type == MessageEntityType::Italic));
        assert!(formatted
            .entities
            .iter()
            .any(|e| e.entity_type == MessageEntityType::Code));
        assert!(formatted
            .entities
            .iter()
            .any(|e| e.entity_type == MessageEntityType::Mention));
        assert!(formatted
            .entities
            .iter()
            .any(|e| e.entity_type == MessageEntityType::Hashtag));
        assert!(formatted
            .entities
            .iter()
            .any(|e| e.entity_type == MessageEntityType::Url));
    }

    #[test]
    fn test_build_text_request_parses_rich_text_in_rust() {
        let request = build_messaging_request(
            "send_text",
            &json!({"chat_id":42, "raw_text":"Hi **Rust**", "disable_notification":true}),
        )
        .expect("valid text request");
        assert_eq!(request["@type"], "sendMessage");
        assert_eq!(request["input_message_content"]["text"]["text"], "Hi Rust");
        assert_eq!(
            request["input_message_content"]["text"]["entities"][0]["type"]["@type"],
            "textEntityTypeBold"
        );
        assert_eq!(request["options"]["disable_notification"], true);
    }

    #[test]
    fn test_build_poll_and_forward_requests() {
        let poll = build_messaging_request(
            "send_poll",
            &json!({"chat_id":7, "question":"Pick", "options":["A","B"], "is_quiz":true, "correct_option_id":1}),
        ).expect("valid poll");
        assert_eq!(poll["input_message_content"]["@type"], "inputMessagePoll");
        assert_eq!(
            poll["input_message_content"]["type"]["@type"],
            "pollTypeQuiz"
        );
        let forward = build_messaging_request(
            "forward",
            &json!({"chat_id":8, "from_chat_id":7, "message_id":9}),
        )
        .expect("valid forward");
        assert_eq!(forward["@type"], "forwardMessages");
        assert_eq!(forward["message_ids"][0], 9);
    }

    #[test]
    fn test_build_edit_text_and_voice_requests() {
        let edit = build_messaging_request(
            "edit_text",
            &json!({"chat_id":3, "message_id":4, "raw_text":"updated **text**"}),
        )
        .expect("valid edit request");
        assert_eq!(edit["@type"], "editMessageText");
        assert_eq!(
            edit["input_message_content"]["text"]["text"],
            "updated text"
        );

        let voice = build_messaging_request(
            "send_voice",
            &json!({"chat_id":3, "file_path":"/cache/voice.m4a", "duration":5, "waveform":"AQI="}),
        )
        .expect("valid voice request");
        assert_eq!(
            voice["input_message_content"]["@type"],
            "inputMessageVoiceNote"
        );
        assert_eq!(
            voice["input_message_content"]["voice_note"]["path"],
            "/cache/voice.m4a"
        );

        let photo = build_messaging_request(
            "send_photo",
            &json!({"chat_id":3, "file_path":"/cache/photo.jpg", "caption":"hello **photo**"}),
        )
        .expect("valid photo request");
        assert_eq!(photo["input_message_content"]["@type"], "inputMessagePhoto");
        assert_eq!(
            photo["input_message_content"]["caption"]["text"],
            "hello photo"
        );
    }

    #[test]
    fn test_scheduled_text_uses_tdlib_scheduling_state() {
        let send_date = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64
            + 600;
        let request = build_messaging_request(
            "send_text",
            &json!({"chat_id":42, "raw_text":"later", "send_at_unix_sec":send_date}),
        )
        .expect("valid scheduled message");
        assert_eq!(
            request["options"]["scheduling_state"]["@type"],
            "messageSchedulingStateSendAtDate"
        );
        assert_eq!(
            request["options"]["scheduling_state"]["send_date"],
            send_date
        );
    }

    #[test]
    fn test_message_and_chat_action_requests() {
        let pin =
            build_messaging_request("pin_message", &json!({"chat_id":1,"message_id":2})).unwrap();
        assert_eq!(pin["@type"], "pinChatMessage");
        assert_eq!(pin["only_for_self"], false);
        let delete =
            build_messaging_request("delete_message", &json!({"chat_id":1,"message_id":2}))
                .unwrap();
        assert_eq!(delete["@type"], "deleteMessages");
        let delete_chat =
            build_messaging_request("delete_chat_history", &json!({"chat_id":1})).unwrap();
        assert_eq!(delete_chat["@type"], "deleteChatHistory");
        assert_eq!(delete_chat["remove_from_list"], true);
        assert_eq!(delete_chat["revoke"], false);
        let search =
            build_messaging_request("search_public_chats", &json!({"query":"rustgram"})).unwrap();
        assert_eq!(search["@type"], "searchPublicChats");
        assert_eq!(search["query"], "rustgram");
        let add = build_messaging_request(
            "add_reaction",
            &json!({"chat_id":1,"message_id":2,"emoji":"👍"}),
        )
        .unwrap();
        assert_eq!(add["@type"], "addMessageReaction");
        let remove = build_messaging_request(
            "remove_reaction",
            &json!({"chat_id":1,"message_id":2,"emoji":"👍"}),
        )
        .unwrap();
        assert_eq!(remove["@type"], "removeMessageReaction");
        let archive =
            build_messaging_request("set_chat_archived", &json!({"chat_id":1,"archived":true}))
                .unwrap();
        assert_eq!(archive["@type"], "addChatToList");
        assert_eq!(archive["chat_list"]["@type"], "chatListArchive");
        let unarchive =
            build_messaging_request("set_chat_archived", &json!({"chat_id":1,"archived":false}))
                .unwrap();
        assert_eq!(unarchive["@type"], "removeChatFromList");
        let pinned = build_messaging_request(
            "pin_chat",
            &json!({"chat_id":1,"list":"archive","is_pinned":true}),
        )
        .unwrap();
        assert_eq!(pinned["chat_list"]["@type"], "chatListArchive");
        let mute =
            build_messaging_request("set_chat_mute", &json!({"chat_id":1,"muted":true})).unwrap();
        assert_eq!(mute["notification_settings"]["mute_for"], 31_536_000);
    }

    #[test]
    fn test_build_chat_lifecycle_requests() {
        let private =
            build_messaging_request("create_private_chat", &json!({"user_id":7})).unwrap();
        assert_eq!(private["@type"], "createPrivateChat");
        let secret = build_messaging_request("create_secret_chat", &json!({"user_id":7})).unwrap();
        assert_eq!(secret["@type"], "createNewSecretChat");
        let group = build_messaging_request(
            "create_supergroup",
            &json!({"title":"RustGram", "is_channel":false}),
        )
        .unwrap();
        assert_eq!(group["@type"], "createNewSupergroupChat");
        let members =
            build_messaging_request("add_chat_members", &json!({"chat_id":99, "user_ids":[7,8]}))
                .unwrap();
        assert_eq!(members["user_ids"].as_array().unwrap().len(), 2);
        let ttl = build_messaging_request("set_chat_ttl", &json!({"chat_id":99, "seconds":3600}))
            .unwrap();
        assert_eq!(ttl["message_auto_delete_time"], 3600);
    }

    #[test]
    fn test_reject_invalid_voice_request() {
        assert!(build_messaging_request(
            "send_voice",
            &json!({"chat_id":3, "file_path":"", "duration":0})
        )
        .is_err());
    }

    #[test]
    fn test_reject_invalid_poll_request() {
        assert!(build_messaging_request(
            "send_poll",
            &json!({"chat_id":7,"question":"Only one", "options":["A"]})
        )
        .is_err());
    }

    #[test]
    fn test_toggle_reactions() {
        let mut msg = TelegramMessage {
            id: MessageId(1),
            chat_id: ChatId(10),
            sender_id: UserId(2),
            sender_name: "Alice".into(),
            date: 1234567,
            is_outgoing: false,
            is_read: true,
            edit_date: None,
            reply_to_message_id: None,
            content: MessageContent::Text {
                text: "Nice!".into(),
            },
            reactions: vec![],
        };

        MessageProcessor::toggle_reaction(&mut msg, "👍");
        assert_eq!(msg.reactions.len(), 1);
        assert_eq!(msg.reactions[0].emoji, "👍");
        assert_eq!(msg.reactions[0].count, 1);
        assert!(msg.reactions[0].is_chosen);

        MessageProcessor::toggle_reaction(&mut msg, "👍");
        assert_eq!(msg.reactions.len(), 0);
    }
}
