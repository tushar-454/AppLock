#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export CI=1
[[ -f android/gradlew ]] || npx expo prebuild --platform android --no-install
./android/gradlew -p tests/engine test --console=plain --max-workers=2
