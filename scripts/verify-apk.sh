#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/android-env.sh
apk="${1:-$PWD/dist/applock.apk}"
tools="$ANDROID_HOME/build-tools/37.0.0"
test -s "$apk"
"$tools/apksigner" verify --verbose --print-certs "$apk"
"$tools/zipalign" -c -P 16 -v 4 "$apk" > /dev/null
badging="$("$tools/aapt" dump badging "$apk")"
printf '%s\n' "$badging" | sed -n '1,8p'
[[ "$badging" == *"name='com.tushar.applock'"* ]]
version_code="$(node -p 'require("./app.json").expo.android.versionCode')"
[[ "$badging" == *"versionCode='$version_code'"* ]]
[[ "$badging" == *"sdkVersion:'30'"* ]]
permissions="$("$tools/aapt" dump permissions "$apk")"
printf '%s\n' "$permissions"
if printf '%s\n' "$permissions" | rg -q 'android.permission.(INTERNET|ACCESS_NETWORK_STATE|SYSTEM_ALERT_WINDOW|PACKAGE_USAGE_STATS|QUERY_ALL_PACKAGES)'; then
  echo 'Unexpected sensitive/network permission in release APK' >&2; exit 1
fi
unzip -l "$apk" | rg 'assets/index.android.bundle|lib/arm64-v8a/libhermes'
# Inspect the packaged, merged manifest rather than only the source manifest.
manifest="$("$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" manifest print "$apk")"
APK_MANIFEST="$manifest" python3 - <<'PY'
import os
import xml.etree.ElementTree as ET
root = ET.fromstring(os.environ['APK_MANIFEST'])
a = '{http://schemas.android.com/apk/res/android}'
app = root.find('application')
assert app.get(a + 'debuggable', 'false') == 'false', 'Debuggable release'
assert app.get(a + 'allowBackup') == 'false', 'Backup must stay disabled'
assert app.get(a + 'usesCleartextTraffic') == 'false'
permissions = {x.get(a + 'name') for x in root.findall('uses-permission')}
assert {'android.permission.USE_BIOMETRIC', 'android.permission.RECEIVE_BOOT_COMPLETED'} <= permissions
def component(tag, name):
    return next(x for x in app.findall(tag) if x.get(a + 'name') == 'expo.modules.applock.' + name)
service = component('service', 'AppLockService')
assert service.get(a + 'permission') == 'android.permission.BIND_ACCESSIBILITY_SERVICE'
assert service.get(a + 'directBootAware') == 'true'
assert any(x.get(a + 'name') == 'android.accessibilityservice' and x.get(a + 'resource')
           for x in service.findall('meta-data'))
assert component('activity', 'AuthenticationActivity').get(a + 'exported') == 'false'
receiver = component('receiver', 'BootReceiver')
assert receiver.get(a + 'directBootAware') == 'true'
assert {'android.intent.action.BOOT_COMPLETED', 'android.intent.action.LOCKED_BOOT_COMPLETED',
        'android.intent.action.USER_UNLOCKED', 'android.intent.action.MY_PACKAGE_REPLACED'} <= {
    x.get(a + 'name') for x in receiver.findall('intent-filter/action')}
component('receiver', 'PackageReceiver')
print('Packaged release manifest: accessibility metadata, biometric activity, boot/package receivers verified.')
PY
# Release resource optimization shortens ZIP paths; resolve the XML by resource name.
accessibility_path="$("$tools/aapt" dump --values resources "$apk" | python3 -c '
import re, sys
m = re.search(r"(?m)^\s+resource [^\n]+:xml/app_lock_accessibility:[^\n]*\n\s+\(string(?:8|16)\) \"([^\"]+)\"", sys.stdin.read())
assert m, "Accessibility XML resource missing"
print(m.group(1))
')"
"$tools/aapt" dump xmltree "$apk" "$accessibility_path" | python3 -c '
import re, sys
xml = sys.stdin.read()
for name in ["canRetrieveWindowContent", "canPerformGestures"]:
    assert re.search(name + r"[^\n]*=\(type 0x12\)0x0\b", xml), name + " must remain false"
assert re.search(r"accessibilityEventTypes[^\n]*=\(type 0x11\)0x20\b", xml)
print("Packaged accessibility XML: window-state events only; content retrieval and gestures disabled.")
'
sha256sum "$apk"
stat --printf='APK bytes: %s\n' "$apk"
printf 'APK: %s\n' "$apk"
