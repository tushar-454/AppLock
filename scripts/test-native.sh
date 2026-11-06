#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$PWD/.tooling/jdk-17.0.21+9}"
export ANDROID_HOME="${ANDROID_HOME:-$PWD/.tooling/android-sdk}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.tooling/android-user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.tooling/gradle}"
export CI=1
[[ -f android/gradlew ]] || npx expo prebuild --platform android --no-install
./android/gradlew -p android :app-lock:testDebugUnitTest :app-lock:lintDebug --max-workers=2 --console=plain
