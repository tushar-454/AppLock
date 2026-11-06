#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh
export CI=1
[[ -f android/gradlew ]] || npx expo prebuild --platform android --no-install
./android/gradlew -p android :app-lock:testDebugUnitTest :app-lock:lintDebug -PreactNativeArchitectures="${APPLOCK_ABIS:-arm64-v8a}" --max-workers=2 --console=plain
