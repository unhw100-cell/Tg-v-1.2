# RustGram — High-Performance Telegram Client for Android 16

RustGram is an open-source Telegram client engineered with **Rust** as its core business engine and **TDLib** (v1.8.65) for native connection to Telegram MTProto servers.

## Features
- **100% Rust Core**: Domain logic, state machines, encryption, database, media processing, networking, and synchronization implemented in Rust.
- **Official TDLib Integration**: Connects to the real Telegram cloud infrastructure.
- **Telegram Authentication**: Phone number login, SMS/Telegram verification code, Two-Factor Authentication (2FA Cloud Password), QR code login, and multi-account management.
- **Full Chat Experience**: Private chats, groups, supergroups, channels, saved messages, reactions, replies, pinning, and rich markdown parsing.
- **Media Engine**: Photos, videos, voice notes with waveforms, documents, and stickers.
- **Android 16 Tailored**: Optimized for Android 16 (API 36) with edge-to-edge layout, predictive back gestures, and Material 3 design reflecting the classic Telegram aesthetic.
- **Security & Privacy**: Zero third-party telemetry, on-device encrypted storage, official MTProto protocol.

## Build Requirements
- Rust 1.80+ (Stable)
- Android SDK 36 (Android 16)
- Java 17 / 21
- Gradle 9+
