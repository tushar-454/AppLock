#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$PWD/.tooling/jdk-17.0.21+9}"
export ANDROID_HOME="${ANDROID_HOME:-$PWD/.tooling/android-sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
apk="${1:-$PWD/artifacts/AppLock-1.0.0.apk}"
tools="$ANDROID_HOME/build-tools/37.0.0"
test -s "$apk"
"$tools/apksigner" verify --verbose --print-certs "$apk"
"$tools/zipalign" -c -P 16 -v 4 "$apk" > /dev/null
badging="$("$tools/aapt" dump badging "$apk")"
printf '%s\n' "$badging" | sed -n '1,8p'
[[ "$badging" == *"name='com.tushar.applock'"* ]]
permissions="$("$tools/aapt" dump permissions "$apk")"
printf '%s\n' "$permissions"
if printf '%s\n' "$permissions" | rg -q 'android.permission.(INTERNET|ACCESS_NETWORK_STATE|SYSTEM_ALERT_WINDOW|PACKAGE_USAGE_STATS|QUERY_ALL_PACKAGES)'; then
  echo 'Unexpected sensitive/network permission in release APK' >&2; exit 1
fi
unzip -l "$apk" | rg 'assets/index.android.bundle|lib/arm64-v8a/libhermes'
sha256sum "$apk"
stat --printf='APK bytes: %s\n' "$apk"
printf 'APK: %s\n' "$apk"
