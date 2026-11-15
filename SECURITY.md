# Security policy

RustGram is experimental and has not undergone an independent security audit. Do not use it for sensitive communications until the remaining storage, native-library, and end-to-end testing gaps below are closed.

## Reporting a vulnerability

Do not publish exploit details, Telegram session data, phone numbers, API credentials, or private messages in a public issue. A private security contact has not yet been configured for this repository.

## Current limitations

- Telegram API Hash and newly generated TDLib database encryption keys are stored using AES-256-GCM with an Android Keystore key. Existing TDLib databases created before this change are not encrypted in place and remain unencrypted unless separately migrated.
- The separate RustGram cache in `rust_db` currently uses ordinary SQLite (`rusqlite` with bundled SQLite), without SQLCipher or field-level encryption. Do not store real message content in that cache until encryption is implemented; TDLib's own database remains the canonical message store.
- The local app passcode uses PBKDF2-HMAC-SHA256 with a per-passcode salt, but it only gates the UI. It is not Telegram 2FA and does not encrypt all app data.
- Bundled native libraries (`libtdjson.so`, `librustgram_integration.so`) have not yet been reproducibly rebuilt from the edited source in this checkout. Their exact provenance, matching TDLib version, and redistribution terms still need verification.
- Demo mode uses fixture data and must never be mistaken for a real Telegram connection.
- The Rust-to-Kotlin repository migration is partial; this is not yet a complete Rust implementation of all visible UI features.

## Safer development defaults

- Android Auto Backup and device-transfer extraction are disabled for app data.
- Do not commit `.env`, signing keystores, API credentials, TDLib databases, session files, or real proxy secrets.
- Avoid logging request JSON: TDLib requests may contain login codes, 2FA passwords, and proxy credentials.
