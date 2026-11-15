# RustGram feature status (2026-11-15)

This is a source-code status inventory, not a release certification. Features marked as connected still need end-to-end verification on an Android 16 device with working Telegram API credentials and native libraries.

## Product and architecture scope

RustGram is an independent Telegram client for Android. It uses TDLib for Telegram's MTProto networking, authorization, local Telegram data management, and asynchronous updates. It is not a rewrite of Telegram's cloud servers or proprietary Telegram service. The Rust workspace is the target for application/domain logic, while Jetpack Compose remains the Android presentation layer.

The current repository is a hybrid: most application state and feature orchestration still lives in `app/src/main/java/com/example/repository/TelegramRepository.kt` (Kotlin); Rust functionality is separated into crates under `rust-core/`, with a JNI bridge in `integration/`. Therefore, a complete Kotlin-to-Rust migration has **not** been completed.

## Feature matrix

| Feature | Current code status | Important limitation |
|---|---|---|
| Android 16 target | Configured | `minSdk` is API 36 for this development phase; no physical-device certification yet |
| Liquid Glass visual system | Shared Compose backdrop, translucent cards, specular borders, and floating chrome used by primary screens | Visual approximation inspired by iOS; not Apple's native effect. Final tuning and accessibility/performance testing remain |
| TDLib dynamic loading | Rust dynamic loader and JNI wrappers are present | Bundled `.so` files have not been rebuilt from the edited Rust source in this environment |
| Telegram phone/code/2FA flow | TDLib state handling and requests exist | Must be tested against a real test account; no success may be inferred from local UI state |
| Chat list and history | TDLib chat synchronization, openChat/getChatHistory, message upsert/deduplication and send acknowledgements are connected in source; archive/unarchive and history deletion now issue TDLib requests | History pagination and full-account message search across unloaded history remain incomplete; reactions, pins, mute, and deletion are wired but still need real-account verification |
| Contacts and chat creation | TDLib `getContacts`/`getUser`, `createPrivateChat`, `createNewSecretChat`, `createNewSupergroupChat` and selected-member invitation requests are connected in source | Response navigation and full profile/status/photo synchronization need device testing and refinement |
| Proxy management | Glass-styled screen for get/add/enable/disable/remove/ping plus Telegram proxy-link import | Uses official TDLib APIs; it does not crawl arbitrary public proxy websites or guarantee the trustworthiness of a proxy |
| Rust proxy validation | Rust `networking` models and typed TDLib request builders are present | Runtime uses these builders only after rebuilding `librustgram_integration.so`; old packaged binaries do not contain the new JNI export |
| Multi-account | Local UI/profile model exists | Independent concurrent TDLib clients and reliable account switching are not implemented/verified |
| Photos / voice messages | Photo picker upload and voice-note recording request paths added; TDLib receives local file inputs | Actual image rendering/downloads, progress/cancel/resume, video, generic documents, video notes and sticker-set integration remain incomplete |
| Calls | UI and local call-history model exist | Full Telegram VoIP call stack and real call-history synchronization are not implemented |
| Stories | UI/model scaffolding exists | Real story loading, playback synchronization, publishing and interactions are not implemented end-to-end |
| Theme customization | Local theme constructor and preferences exist | Settings are device-local; account-linked cloud synchronization is not connected |
| Customization sync | Versioned Rust profile schema, validation and merge primitive exist | No server endpoint, account binding, authentication, conflict persistence or upload/download implementation exists yet |
| Local app passcode | PBKDF2-HMAC-SHA256 verifier with per-passcode salt | App lock is not Telegram 2FA and does not encrypt all app data |
| Local secrets | API Hash and newly created TDLib database key are encrypted with AES-GCM using Android Keystore | Existing legacy TDLib databases are not encrypted in place; separate RustGram `rust_db` cache is currently plain SQLite and must not be treated as secure message storage |

## Rust migration status

The messaging Rust crate now builds TDLib JSON requests for text (including supported Markdown entities and scheduled sends), polls, edits, forwards, photo/voice uploads, chat creation, member invitations, secret-chat creation, chat self-destruct timers, message pin/delete, reactions, chat pin/mute/archive, chat-history deletion, and public-chat directory search. Proxy request construction and validation also live in Rust. The Kotlin repository still owns application state, most update orchestration, UI-facing state flows, device permissions, file picking, and navigation. This is an incremental extraction, not a complete Rust rewrite. Existing packaged JNI `.so` files were not rebuilt in the preparation environment; until the Android CI rebuilds and packages the JNI bridge, older installed binaries use Kotlin request fallbacks or do not expose newer Rust operations.

## Search behavior

The search field combines two different sources: chat titles/usernames and the message bodies already loaded into the local in-memory history, plus TDLib `searchPublicChats` directory results. It is not a full-account message search: unloaded older history, global message search across all conversations, contacts, and inline bot results are not covered yet. Public-chat search results are fetched as TDLib chat IDs and then resolved with `getChat` before appearing in the screen.

## Proxy API behavior

The proxy screen uses TDLib's documented `getProxies`, `addProxy`, `enableProxy`, `disableProxy`, `removeProxy`, and `pingProxy` JSON methods. Proxy management can be invoked before authorization. `pingProxy` takes a complete proxy object (or null), not a proxy ID. Telegram proxy links are parsed into editable fields and are not connected automatically without user confirmation.

There is no public-proxy web-search provider, endpoint, or scraping implementation in the source archive. A web crawler or automatically maintained public list has intentionally not been added: public proxy endpoints are untrusted and can become unavailable or be malicious. Add a provider-backed discovery service only after selecting and reviewing its sources, privacy policy, and trust model.

## Data-storage warning

TDLib is intended to remain the canonical local store for Telegram data. Fresh TDLib databases receive an Android Keystore-backed encryption key. The separate RustGram SQLite cache in `rust_db` is **not encrypted** at present; avoid persisting private message bodies there until an authenticated-encryption design is implemented and tested. Do not describe the whole app data layer as encrypted.

## CI and release gates

GitHub Actions is configured to check Rust formatting, run Rust tests and Clippy, install Android SDK Platform 36.1 and the pinned NDK, rebuild the Rust JNI library for `arm64-v8a`, `armeabi-v7a`, and `x86_64`, run Android unit tests, assemble a debug APK, and upload that APK as a workflow artifact. This workflow configuration has not yet been executed in this environment; a successful green GitHub Actions run is still required.

Before calling the project runnable:

1. Verify that each packaged `libtdjson.so` matches the TDLib headers/API expected by the Rust wrapper and that redistribution terms are satisfied.
2. Restore a standard Gradle Wrapper and run the configured CI successfully.
3. Test on Android 16: fresh install, API credentials, phone login, OTP, 2FA, relaunch/session restore, chat sync, message send/receive, attachments, proxy add/test/enable/disable, and logout.
4. Only then continue implementing the remaining Telegram functionality. Design customization synchronization as a separate authenticated backend with explicit sharing/permissions, encrypted transport/storage, conflict resolution, revocation, and export/delete controls; do not sync Telegram messages through this service.
