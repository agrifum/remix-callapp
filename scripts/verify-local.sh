#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [ ! -f "debug.keystore" ]; then
  echo "debug.keystore not found. Generating disposable debug keystore..."
  command -v keytool >/dev/null 2>&1 || {
    echo "ERROR: keytool not found in PATH. Please install a JDK or add keytool to PATH." >&2
    exit 1
  }
  keytool -genkeypair -v \
    -keystore debug.keystore \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass android \
    -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" \
    -storetype JKS
  echo "debug.keystore generated successfully."
fi

chmod +x gradlew

echo "Executing Gradle verification: compileDebugKotlin, assembleDebug, testDebugUnitTest, lintDebug..."
./gradlew --no-daemon \
  :app:compileDebugKotlin \
  :app:assembleDebug \
  :app:testDebugUnitTest \
  :app:lintDebug
