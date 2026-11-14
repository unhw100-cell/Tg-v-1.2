use rustgram_authentication::{AuthManager, AuthorizationState};
use rustgram_core::{ChatId, ChatType, MessageContent, MessageId, TelegramChat, TelegramMessage, UserId};
use rustgram_database::RustGramDatabase;
use rustgram_encryption::TelegramCrypto;
use rustgram_media::VoiceWaveform;
use rustgram_messaging::MessageProcessor;
use rustgram_networking::{ConnectionState, NetworkManager};
use rustgram_synchronization::{ChatAction, SyncEngine};

#[test]
fn test_end_to_end_messaging_and_caching() {
    // 1. Database
    let db = RustGramDatabase::open_in_memory().expect("Database init failed");

    // 2. Chat creation
    let chat = TelegramChat {
        id: ChatId(1001),
        title: "RustGram Community".to_string(),
        chat_type: ChatType::Supergroup,
        unread_count: 1,
        last_message: None,
        is_pinned: true,
        is_muted: false,
        avatar_path: None,
    };
    db.save_chat(&chat).expect("Save chat failed");

    // 3. Message processing
    let parsed_text = MessageProcessor::parse_markdown("Welcome to **RustGram**!");
    assert_eq!(parsed_text.text, "Welcome to RustGram!");

    let mut message = TelegramMessage {
        id: MessageId(5001),
        chat_id: ChatId(1001),
        sender_id: UserId(777),
        sender_name: "Rust Senior Dev".to_string(),
        date: 1718000000,
        is_outgoing: false,
        is_read: false,
        edit_date: None,
        reply_to_message_id: None,
        content: MessageContent::Text {
            text: parsed_text.text,
        },
        reactions: vec![],
    };

    MessageProcessor::toggle_reaction(&mut message, "🔥");
    assert_eq!(message.reactions.len(), 1);

    db.save_message(&message).expect("Save message failed");

    let history = db.get_chat_messages(ChatId(1001), 10).expect("Get history failed");
    assert_eq!(history.len(), 1);
    assert_eq!(history[0].summary_text(), "Welcome to RustGram!");

    // 4. Voice Waveform
    let peaks = vec![0.2, 0.5, 0.8, 0.9, 0.4, 0.1];
    let waveform = VoiceWaveform::generate_normalized(&peaks, 10);
    assert_eq!(waveform.samples.len(), 10);

    // 5. Crypto
    let sha = TelegramCrypto::sha256_hex(b"RustGram Android 16");
    assert!(!sha.is_empty());

    // 6. Network state
    let mut nm = NetworkManager::new();
    nm.set_connection_state(ConnectionState::Ready);
    assert_eq!(nm.current_state, ConnectionState::Ready);

    // 7. Synchronization
    let mut sync = SyncEngine::new();
    sync.set_user_action(ChatId(1001), UserId(777), ChatAction::Typing, 1000);
    assert_eq!(sync.get_chat_actions(ChatId(1001)).len(), 1);
}
