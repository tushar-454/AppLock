#!/usr/bin/env bash
set -euo pipefail

apk="${1:?Usage: verify-release-apk.sh APK VERSION_NAME VERSION_CODE}"
expected_version="${2:?Expected version name required}"
expected_code="${3:?Expected version code required}"
expected_package='com.tushar.applock'
expected_certificate='7adcb0de660b8b286f416e81b1fc4945600c5085b0c90181c3152fb0e4a09cda'
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
build_tools="$sdk_root/build-tools"

test -s "$apk" || { echo 'APK is missing or empty.' >&2; exit 1; }
test -d "$build_tools" || { echo 'Android build-tools were not found.' >&2; exit 1; }
apksigner="$(find "$build_tools" -maxdepth 2 -type f -name apksigner -print | sort -V | tail -n 1)"
aapt="$(find "$build_tools" -maxdepth 2 -type f -name aapt -print | sort -V | tail -n 1)"
test -x "$apksigner" && test -x "$aapt" || { echo 'apksigner and aapt are required.' >&2; exit 1; }

signature="$($apksigner verify --verbose --print-certs "$apk")"
printf '%s\n' "$signature" | rg -q '^Verifies$' || { echo 'APK signature verification failed.' >&2; exit 1; }
printf '%s\n' "$signature" | grep -Fqx 'Number of signers: 1' || {
  echo 'APK must have exactly one signer.' >&2; exit 1;
}
fingerprints="$(printf '%s\n' "$signature" | sed -n -E \
  -e 's/^Signer #[0-9]+ certificate SHA-256 digest: //p' \
  -e 's/^V[0-9]+ Signer: certificate SHA-256 digest: //p' \
  | tr '[:upper:]' '[:lower:]' | tr -d ':' | sort -u)"
[[ "$(printf '%s\n' "$fingerprints" | sed '/^$/d' | wc -l | tr -d ' ')" == 1 ]] || {
  echo 'APK has missing or inconsistent signer certificates.' >&2; exit 1;
}
fingerprint="$fingerprints"
[[ "$fingerprint" == "$expected_certificate" ]] || {
  echo 'APK signer certificate does not match the pinned AppLock certificate.' >&2; exit 1;
}

badging="$($aapt dump badging "$apk")"
printf '%s\n' "$badging" | grep -Fq "package: name='${expected_package}' versionCode='${expected_code}' versionName='${expected_version}' " || {
  echo 'APK package name or version does not match the release.' >&2; exit 1;
}
printf 'Verified package: %s\nVersion: %s (%s)\nSigner SHA-256: %s\n' \
  "$expected_package" "$expected_version" "$expected_code" "$fingerprint"
