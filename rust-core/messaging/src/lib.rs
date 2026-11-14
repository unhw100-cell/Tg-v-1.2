//! RustGram Messaging Engine
//! Message parsing, rich text entities (bold, italic, code, spoilers, mentions, hashtags, URLs),
//! drafts, search and reaction handling.

use rustgram_core::{ChatId, MessageId, TelegramMessage, TelegramReaction};
use serde::{Deserialize, Serialize};

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
                let token_end = rest
                    .find(|c: char| c.is_whitespace())
                    .unwrap_or(rest.len());
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
