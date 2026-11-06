import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const file = (name) => readFileSync(name, 'utf8');
const root = 'modules/app-lock/android/src/main/';
const manifest = file(root + 'AndroidManifest.xml');
const xml = file(root + 'res/xml/app_lock_accessibility.xml');
const plugin = file('plugins/with-app-lock.js');
assert.match(manifest, /android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"/);
assert.match(manifest, /android:name=".AuthenticationActivity" android:exported="false"/);
assert.match(manifest, /android:directBootAware="true"/);
assert.match(manifest, /LOCKED_BOOT_COMPLETED/);
assert.match(xml, /canRetrieveWindowContent="false"/);
assert.match(xml, /canPerformGestures="false"/);
assert.match(xml, /typeWindowStateChanged/);
for (const permission of ['QUERY_ALL_PACKAGES', 'SYSTEM_ALERT_WINDOW', 'PACKAGE_USAGE_STATS', 'FOREGROUND_SERVICE']) {
  assert.ok(!manifest.includes(permission), `Unexpected permission ${permission}`);
}
assert.match(plugin, /android.permission.INTERNET" tools:node="remove"/);
const engine = file(root + 'java/expo/modules/applock/LockEngine.kt');
assert.ok(!engine.includes('android.'), 'State machine must remain independently testable');
console.log('Native manifest/privacy configuration checks passed.');
