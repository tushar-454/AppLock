#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh
export CI=1
export NODE_ENV=production
mkdir -p .tooling dist "$ANDROID_USER_HOME"
umask 077
if [[ -f .tooling/applock-personal.keystore && ! -s .tooling/signing-password ]]; then
  echo 'Existing signing key has no password file. Restore .tooling/signing-password privately.' >&2
  exit 1
fi
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
bash scripts/verify-apk.sh android/app/build/outputs/apk/release/app-release.apk
cp android/app/build/outputs/apk/release/app-release.apk dist/applock.apk
chmod 644 dist/applock.apk
sha256sum dist/applock.apk > dist/applock.apk.sha256
printf 'Verified standalone APK: %s/dist/applock.apk\n' "$PWD"
