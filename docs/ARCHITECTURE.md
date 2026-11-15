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
├── database/         - SQLite cache for chat/message metadata (currently not encrypted)
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
- UI Toolkit: **Jetpack Compose** with an iOS-inspired Liquid Glass visual language adapted for Android
- Background work is not currently run as an always-on foreground service. TDLib connection state is reported honestly; a compliant, user-visible background-sync design must be added only when there is a real foreground task and the Android 16 service/notification rules are met.


## Current implementation boundaries

- Android UI: Kotlin + Jetpack Compose. Liquid Glass is a visual approximation implemented with translucent surfaces, gradient highlights and borders.
- Application coordination: `TelegramRepository.kt` remains the Kotlin state/repository layer. It now uses Rust JNI builders for text and scheduled sends, polls, edit/forward/photo/voice, chat lifecycle, reactions, pins, mute/archive/delete, public-chat search and proxy requests. Update orchestration, UI state, file picking, permissions and navigation are still Kotlin. Porting the domain/state layer completely to Rust is unfinished.
- Native core: `rust-core/*` crates provide Rust-side models and selected operations. The RustGram cache uses plain SQLite at present and must not be treated as an encrypted message store. `integration/` exports JNI functions. Rebuild all native libraries after changing Rust code; checked-in `.so` files do not magically update when Rust source changes.
- Telegram protocol/client: TDLib handles the Telegram-specific client protocol, asynchronous requests, updates and local Telegram storage. The application must correlate asynchronous requests with `@extra` and handle all updates in receive order.
- Search: chat title/username search and already-loaded message text search are local; TDLib public-chat directory search is remote. A full-account/global message search across unloaded history is not implemented.
- Future appearance sync: `rust-core/synchronization` currently defines a versioned profile model and merge primitive only. A secure authenticated backend, user consent/sharing permissions, conflict resolution, revocation and actual upload/download flows are still required.
