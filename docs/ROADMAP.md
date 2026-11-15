# RustGram roadmap

This roadmap is aligned with the initial product requirements: Rust-centred Telegram client, broad feature migration, iOS-inspired Liquid Glass UI, Android 16 first, and future opt-in customization sharing.

## Phase 0 — honest and reproducible foundation

- Keep Android presentation in Jetpack Compose; move application/domain behavior and TDLib request construction into Rust crates behind a small JNI boundary.
- Keep TDLib as the official Telegram client library for MTProto, authorization, local Telegram storage and updates. RustGram is a client app, not a reimplementation of Telegram's servers.
- Rebuild JNI/TDLib binaries from pinned source/toolchains for Android arm64-v8a, armeabi-v7a and x86_64; verify provenance, ABI compatibility and licensing.
- Restore Gradle Wrapper, CI, Rust tests, Android build and an Android 16 device test matrix.

## Phase 1 — usable messaging core

- Complete authorization, chat list ordering, history pagination, updates, delivery acknowledgements and local draft handling.
- Complete text formatting, replies, editing, deletion, forwarding, reactions, polls and scheduled messages.
- Complete photo/video/document/audio upload and download pipelines, thumbnails, progress, cancellation, retries and media viewing.
- Implement actual secret-chat lifecycle and TTL via TDLib; never label a local simulation as end-to-end encrypted.
- Complete private chat, group/channel member management, invite links, permissions and admin controls.

## Phase 2 — network and user experience

- Complete proxy management using TDLib; decide whether any proxy discovery provider is desirable, then vet sources and privacy risks before adding it.
- Bring every screen, dialog, sheet, popup, menu and empty/loading/error state into the same Liquid Glass visual system.
- Validate contrast, dynamic type, TalkBack, reduced motion, GPU/battery load, blur fallbacks and Android 16 edge-to-edge behavior.

## Phase 3 — remaining Telegram feature coverage

- Implement stories, calls/VoIP, topics, search, bots, stickers, folders, notifications, background sync and multi-account support against current TDLib APIs.
- Build feature-by-feature parity tests against a dedicated Telegram test account.

## Phase 4 — optional customization sharing

- Add a Rust service only after the core client is stable. Store appearance profiles separately from Telegram account/message data.
- Implement explicit user-to-user sharing, link expiry/revocation, scopes (theme only vs. wallpaper/layout), authenticated access, encrypted transport and storage, versioning and conflict policy.
- Make import/export possible without an account and provide clear preview/consent before applying another user's profile.

## Current non-goals

- Rewriting Telegram's cloud infrastructure or server-side proprietary product.
- Automatically connecting untrusted public proxy addresses.
- Advertising full feature parity, audited security or production readiness before reproducible builds and real-device verification.
