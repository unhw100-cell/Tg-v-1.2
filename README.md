# RustGram

RustGram is an independent Android Telegram client. The current app uses Jetpack Compose for its Android interface, Rust crates for selected domain/networking/storage functions, a JNI bridge, and TDLib for Telegram protocol/client functionality.

> **Development status:** early development. The Rust migration is partial, Telegram feature parity is not complete, and this source has not passed a complete Android build or end-to-end device test. Do not treat it as production-ready or as an independently audited secure messenger.

## Repository layout

- `app/` — Android application, Kotlin UI, repository layer, and bundled native libraries.
- `rust-core/` — Rust domain, authentication state, networking models, messaging, database, media, encryption helpers, and synchronization modules.
- `tdlib/` — Rust dynamic loader and wrapper for TDLib's C JSON interface.
- `integration/` — JNI exports used by Kotlin.
- `tests/` — Rust integration tests.

## Requirements

- Android 16 device/emulator (API 36+) for the current development target.
- Android Studio with Android SDK Platform 36.1 (Android 16 QPR2) and the pinned Android Gradle Plugin 9.1.1 / Gradle 9.3.1 toolchain.
- JDK 17 or 21 (use the version required by the Gradle wrapper/plugin in this checkout).
- Rust stable toolchain and Cargo.
- Android NDK installed locally for rebuilding Rust native libraries.
- `cargo-ndk` for cross-compiling Rust to Android ABIs. The project pins NDK `28.2.13676358` in Gradle and CI.

## Run the repository preflight checks

From the repository root, run:

```bash
./scripts/preflight.sh
```

The script checks required project files, native-library presence for the configured ABIs, obvious credential leaks, and runs Rust formatting/tests/Clippy when Cargo is installed. It reports missing Gradle/Java tooling instead of pretending that an Android build was completed. This is a convenience check, not a security audit or release certification.

## Build the Rust workspace (host)

```bash
cargo fmt --all -- --check
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
```

The commands above have **not** been executed in the preparation environment because the Rust toolchain is not installed there. Run them locally before publishing.

## Build Rust libraries for Android

The `.cargo/config.toml` intentionally does not contain machine-specific NDK paths. Set `ANDROID_NDK_HOME` to NDK `28.2.13676358`, install `cargo-ndk`, then build the ABIs configured for the app. For example (Linux/macOS host):

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/<installed-version>"
cargo install cargo-ndk
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o app/src/main/jniLibs build --release -p rustgram-integration
```

Repeat for any additional ABI that you actually support. The app currently includes checked-in native binaries for several ABIs; those binaries must be rebuilt from matching source/toolchain versions before claiming reproducible builds. Do not add Cargo's `target/` directory to Git.

## Build the Android app

The supplied archive does not contain `gradlew` or `gradle-wrapper.jar`, so the wrapper commands are not available yet. Install Gradle 9.3.1, then generate the standard wrapper once. Debug builds use Gradle’s default debug keystore; release builds require your own private upload keystore supplied through environment variables:

```bash
gradle wrapper --gradle-version 9.3.1 --distribution-type bin
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Commit the generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` files. After that, open the project in Android Studio and sync it.

The currently retained native ABIs are `arm64-v8a`, `armeabi-v7a`, and `x86_64`. The incomplete `x86` native folder was removed because it did not contain `libtdjson.so`.

A release build additionally requires a private signing keystore and passwords supplied through environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`). Never commit keystores, passwords, API credentials, Telegram session data, or `.env` files.

## Project scope and current feature status

The Rust messaging crate contains TDLib request builders for formatted/scheduled text, polls, edits, forwards, photo and voice uploads, private/secret/group chat creation, member invitations, self-destruct timers, message pin/delete, reactions, chat pin/mute/archive, chat-history deletion, and public-chat directory search. Rust also validates/builds proxy requests. The Android repository still coordinates asynchronous results and UI state in Kotlin. Native JNI libraries must be rebuilt before these new Rust request builders are available at runtime; checked-in older libraries use Kotlin fallbacks. Media, chat creation, search, and history paths still require on-device testing and do not amount to complete Telegram parity. Local search only covers loaded message history; global account-wide message search is not implemented.

GitHub Actions is configured to check Rust formatting/tests/Clippy, install Android SDK Platform 36.1 and the pinned NDK, rebuild the Rust JNI library for the supported Android ABIs, run Android unit tests, and assemble a debug APK. This CI path has been configured in source but has not yet run successfully on GitHub.

See [`docs/FEATURE_STATUS.md`](docs/FEATURE_STATUS.md) and [`docs/ROADMAP.md`](docs/ROADMAP.md) for the current inventory and next milestones.

TDLib is a client library. RustGram is a separate client application using TDLib; it does not recreate Telegram's cloud backend or proprietary service.

## Telegram API credentials

Configure your own Telegram API ID and API Hash using the app's configuration flow. Do not publish personal credentials. Telegram's TDLib implementation is responsible for the actual Telegram authentication protocol; do not substitute a locally invented password hash for TDLib's 2FA/SRP flow.

## Security notes — read before public release

- The Android manifest disables Android Auto Backup and the extraction rules exclude app databases and preferences. This reduces accidental cloud/transfer exposure but does not protect data on a rooted or compromised device.
- The app passcode verifier uses PBKDF2-HMAC-SHA256 with a per-passcode random salt and upgrades older locally stored hashes after a successful unlock. A local app passcode is not Telegram 2FA and does not encrypt all app data.
- API Hash and newly initialized TDLib database encryption keys are stored using AES-GCM with an Android Keystore key. Existing legacy TDLib databases with no encryption key are deliberately left unchanged for compatibility; reset/backup-based migration is still a separate task. The separate RustGram `rust_db` cache currently uses ordinary, unencrypted SQLite; do not treat it as a secure message store.
- Bundled `libtdjson.so` and other `.so` files are third-party/native artifacts. Verify their exact TDLib version, source, build provenance, and license notices before redistributing them.
- Demo mode is explicitly separate and uses fixture contacts/chats; those fixtures are not real Telegram data. A real-device test with a test account is required.
- Proxy settings use TDLib's own proxy API. The project does not currently crawl public websites for free proxy addresses.
- The Android UI and `TelegramRepository` remain mostly Kotlin. The project is not yet a complete Rust rewrite.

## Data storage warning

TDLib is the canonical source of Telegram chat/message persistence. The secondary RustGram cache at `rust_db` currently uses bundled SQLite without SQLCipher or field-level authenticated encryption. The app is not ready for security-sensitive use until message content is removed from that cache or protected by a reviewed encryption implementation.

## GitHub release checklist

1. Run Rust formatting, tests, and Clippy.
2. Run Android unit tests and build a debug APK.
3. Verify a real TDLib login, chat sync, send/receive, media, and logout on a physical device.
4. Scan the complete Git diff for credentials, private keys, session files, personal data, and build artifacts.
5. Add the correct license and third-party notices only after confirming redistribution rights for all included assets and native libraries.
6. Rebuild the checked-in native libraries reproducibly or document their provenance.

## License

No license file is included yet. Choose and add a license only after confirming that you own or have redistribution rights for all project code and bundled dependencies. Until then, public visibility on GitHub does not grant others permission to reuse the code.
