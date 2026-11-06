#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$PWD/.tooling/jdk-17.0.21+9}"
export ANDROID_HOME="${ANDROID_HOME:-$PWD/.tooling/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.tooling/android-user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.tooling/gradle}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
export CI=1
export NODE_ENV=production
mkdir -p .tooling artifacts "$ANDROID_USER_HOME"
if [[ ! -f .tooling/signing-password ]]; then
  umask 077
  node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('hex'))" > .tooling/signing-password
fi
export APPLOCK_STORE_PASSWORD
APPLOCK_STORE_PASSWORD="$(cat .tooling/signing-password)"
export APPLOCK_KEYSTORE="$PWD/.tooling/applock-personal.keystore"
if [[ ! -f "$APPLOCK_KEYSTORE" ]]; then
  keytool -genkeypair -keystore "$APPLOCK_KEYSTORE" -storepass:env APPLOCK_STORE_PASSWORD \
    -keypass:env APPLOCK_STORE_PASSWORD -alias applock -keyalg RSA -keysize 3072 -validity 3650 \
    -dname "CN=AppLock Personal Testing" -noprompt
fi
npx expo prebuild --platform android --no-install
# arm64 is the Pixel 6a ABI. Override for an emulator: APPLOCK_ABIS=x86_64.
./android/gradlew -p android :app-lock:testDebugUnitTest :app-lock:lintDebug :app:assembleRelease \
  -PreactNativeArchitectures="${APPLOCK_ABIS:-arm64-v8a}" --max-workers=2 --console=plain
cp android/app/build/outputs/apk/release/app-release.apk artifacts/AppLock-1.0.0.apk
bash scripts/verify-apk.sh
