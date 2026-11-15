#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0
warn=0
pass() { printf '\033[32m[OK]\033[0m %s\n' "$1"; }
notice() { printf '\033[33m[WARN]\033[0m %s\n' "$1"; warn=$((warn + 1)); }
error() { printf '\033[31m[FAIL]\033[0m %s\n' "$1"; fail=$((fail + 1)); }

printf 'RustGram preflight — %s\n\n' "$ROOT"

for f in Cargo.toml Cargo.lock settings.gradle.kts app/build.gradle.kts app/src/main/AndroidManifest.xml; do
  if [[ -f "$f" ]]; then pass "Found $f"; else error "Missing $f"; fi
done

if [[ -x ./gradlew && -f gradle/wrapper/gradle-wrapper.jar ]]; then
  pass 'Gradle Wrapper is present'
else
  notice 'Gradle Wrapper scripts/JAR are absent; Android build needs installed Gradle or wrapper generation'
fi

if command -v cargo >/dev/null 2>&1; then
  pass "Cargo found ($(cargo --version))"
  cargo fmt --all -- --check || error 'cargo fmt failed'
  cargo test --workspace --locked || error 'cargo test failed'
  cargo clippy --workspace --all-targets --locked -- -D warnings || error 'cargo clippy failed'
else
  notice 'Cargo is not installed; Rust format/tests/Clippy were not run'
fi

if command -v gradle >/dev/null 2>&1; then
  pass "Gradle found ($(gradle --version | grep -m1 '^Gradle ' || true))"
else
  notice 'Gradle is not installed; Android compilation/tests were not run'
fi

if command -v java >/dev/null 2>&1; then
  pass "Java found ($(java -version 2>&1 | head -n1))"
else
  notice 'Java is not installed'
fi

# Ensure every ABI configured for packaging contains both required native libraries.
for abi in arm64-v8a armeabi-v7a x86_64; do
  for lib in libtdjson.so librustgram_integration.so; do
    if [[ -s "app/src/main/jniLibs/$abi/$lib" ]]; then
      pass "Native library present: $abi/$lib"
    else
      error "Missing native library: $abi/$lib"
    fi
  done
done

# Never print matching lines: this only reports the paths of likely leaked secrets.
secret_paths=$(grep -RIlE --exclude-dir=.git --exclude-dir=build --exclude-dir=target \
  --exclude='*.so' --exclude='*.png' --exclude='*.jpg' \
  '(BEGIN (RSA|EC|OPENSSH) PRIVATE KEY|gh[pousr]_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{30,}|api_hash["=: ]+[0-9a-fA-F]{20,})' . 2>/dev/null || true)
if [[ -n "$secret_paths" ]]; then
  error "Potential secrets found in these paths (contents not printed):$(printf '\n%s' "$secret_paths")"
else
  pass 'No obvious private-key/API-token patterns found by the basic scanner'
fi

if find . -type d \( -name target -o -name build -o -name .gradle \) -prune -print -quit | grep -q .; then
  notice 'Build-output directories exist locally; verify they are ignored before git add'
else
  pass 'No common build-output directories found'
fi

printf '\nSummary: %d failure(s), %d warning(s)\n' "$fail" "$warn"
if (( fail > 0 )); then exit 1; fi
printf 'Preflight passed its available checks. Warnings are not release approval.\n'
