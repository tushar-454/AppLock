#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$PWD/.tooling/jdk-17.0.21+9}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.tooling/gradle}"
[[ -f android/gradlew ]] || npx expo prebuild --platform android --no-install
./android/gradlew -p tests/engine test --console=plain --max-workers=2
