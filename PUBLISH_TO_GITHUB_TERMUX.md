# Publish RustGram from Android with Termux

These commands publish the prepared source. They do not build or release an APK. GitHub Actions is configured to rebuild the Rust JNI bridge and create a debug APK after you push; you must inspect the Actions result before treating that APK as usable.

## 1. Install tools in Termux

```bash
pkg update && pkg upgrade
pkg install git unzip
```

If you use the GitHub CLI and it is available for your Termux setup, install it too and authenticate with `gh auth login`. Otherwise create an empty repository on GitHub in the browser first.

## 2. Extract the prepared archive

Copy `rustgram-github-ready-v3.zip` to the phone's Download folder, then run:

```bash
termux-setup-storage
mkdir -p ~/RustGram
unzip ~/storage/downloads/rustgram-github-ready-v3.zip -d ~/RustGram
cd ~/RustGram
```

If you extracted into a subfolder, `cd` into the folder containing `Cargo.toml` and `settings.gradle.kts`.

## 3. Run the preflight check

```bash
chmod +x scripts/preflight.sh
./scripts/preflight.sh
```

On a phone-only Termux installation, Cargo, Gradle, the Android SDK, and the NDK may not all be available. The script will report checks it could not run. Do not treat a missing tool as a passing build. The configured GitHub Actions workflows are the intended reproducible build environment for this project.

## 3. Review and commit

```bash
git init
git branch -M main
git status --short
git add .
git status --short
git commit -m "Prepare RustGram source for public review"
```

Check the staged file list before committing. Never add `.env`, keystores, private keys, Telegram session directories, or personal data.

## 5. Connect the GitHub repository and push

Create an empty repository on GitHub (do not initialize it with a README), then replace `YOUR_USERNAME` with your GitHub username:

```bash
git remote add origin https://github.com/YOUR_USERNAME/RustGram.git
git push -u origin main
```

GitHub does not accept account passwords for Git operations. Authenticate with GitHub CLI (`gh auth login`) or a secure credential manager / personal access token prompt. Do not paste a token into the remote URL or commit it to the repository.

## Important

The project compiles against Android SDK Platform 36.1 (Android 16 QPR2) and uses AGP 9.1.1 / Gradle 9.3.1. The repository has no license file yet, and the bundled `libtdjson.so` files still need provenance and redistribution-license verification. Resolve both before treating the repository as a public open-source release. Check the GitHub Actions run: it should compile the Rust JNI bridge, run Android tests, assemble an APK, and publish the APK as an artifact. This does not prove Telegram login or runtime behavior; test on an Android 16 device. See `README.md` and `SECURITY.md`.
