#!/usr/bin/env bash
# Shared local-toolchain discovery; explicit environment variables take precedence.
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x "$PWD/.tooling/jdk-17.0.21+9/bin/java" ]]; then
    export JAVA_HOME="$PWD/.tooling/jdk-17.0.21+9"
  else
    export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  fi
fi
if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [[ -d "$PWD/.tooling/android-sdk" ]]; then
    export ANDROID_HOME="$PWD/.tooling/android-sdk"
  else
    export ANDROID_HOME="$HOME/Android/Sdk"
  fi
fi
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.tooling/android-user}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
[[ -x "$JAVA_HOME/bin/java" && -d "$ANDROID_HOME/build-tools" ]] || {
  echo 'Set JAVA_HOME and ANDROID_HOME to an installed JDK and Android SDK.' >&2
  exit 1
}
