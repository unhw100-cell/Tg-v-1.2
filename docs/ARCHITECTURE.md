# RustGram Architecture & Technical Specification

## Overview
RustGram is an alternative, high-performance Telegram client engineered specifically for **Android 16 (API 36)** with **Rust** as the core development language and **TDLib** (Telegram Database Library) providing direct connection to official Telegram MTProto servers.

## Rust Core Architecture

```
rust-core/
├── core/             - Core domain models (TelegramUser, TelegramMessage, TelegramChat, RustGramError)
├── networking/       - Telegram Data Centers (DC1-DC5), ConnectionState, SOCKS5/MTProto Proxy
├── messaging/        - Markdown parser, Rich entities (bold, spoilers, code), Reactions, Drafts
├── authentication/   - Auth state machine (Phone, Code, 2FA Cloud Password, QR), Multi-account
├── database/         - Encrypted SQLite local persistence (rusqlite) for chats and messages
├── media/            - Waveform generator for voice notes, aspect-fit image scaler, file cache
├── encryption/       - SHA-256 digests, CSPRNG session generator, Telegram SRP 2FA calculation
└── synchronization/  - Real-time updates handler, chat typing states tracker with auto-expiration
```

## TDLib Integration
TDLib (`libtdjson.so`) is dynamically linked and accessed via the C API:
- `td_create_client_id`
- `td_send`
- `td_receive`
- `td_execute`

## Android Layer
- Target Platform: **Android 16 (API 36)**
- UI Toolkit: **Jetpack Compose** adhering to Telegram's visual design guidelines
- Memory and background synchronization managed via Foreground Services.
