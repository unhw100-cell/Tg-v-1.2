# Telegram API ID & API Hash Configuration Guide

## How to get your API ID & API Hash
1. Log in to your Telegram account at: **[https://my.telegram.org/apps](https://my.telegram.org/apps)**
2. Go to **"API development tools"**.
3. Fill out the application form:
   - **App title**: RustGram
   - **Short name**: rustgram
   - **Platform**: Android
4. Submit the form to receive:
   - **App api_id**: (integer, e.g. `1234567`)
   - **App api_hash**: (hex string, e.g. `0123456789abcdef0123456789abcdef`)

## Where to enter in RustGram
- You can enter your credentials directly inside the app in **Settings -> Telegram API Credentials**.
- The credentials are saved securely in local Android EncryptedSharedPreferences and passed to the Rust core / TDLib engine.
- RustGram never transmits your API ID or API Hash to third-party servers.
