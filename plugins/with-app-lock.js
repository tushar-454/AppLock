const { withAppBuildGradle, withDangerousMod, withGradleProperties, createRunOncePlugin } = require('expo/config-plugins');
const fs = require('node:fs');
const path = require('node:path');

function withAppLock(config) {
  config = withGradleProperties(config, (mod) => {
    const values = { 'android.minSdkVersion': '30', 'expo.gif.enabled': 'false', 'expo.webp.enabled': 'false' };
    for (const [key, value] of Object.entries(values)) {
      const existing = mod.modResults.find((item) => item.type === 'property' && item.key === key);
      if (existing) existing.value = value;
      else mod.modResults.push({ type: 'property', key, value });
    }
    return mod;
  });
  config = withAppBuildGradle(config, (mod) => {
    if (!mod.modResults.contents.includes('// AppLock personal signing')) {
      mod.modResults.contents += `
// AppLock personal signing. Values are provided only by the local build script.
if (System.getenv('APPLOCK_REQUIRE_SIGNING') == 'true' &&
    (!System.getenv('APPLOCK_KEYSTORE') || !System.getenv('APPLOCK_STORE_PASSWORD'))) {
  throw new GradleException('Release signing is required, but APPLOCK_KEYSTORE or APPLOCK_STORE_PASSWORD is missing.')
}
if (System.getenv('APPLOCK_KEYSTORE')) {
  if (!System.getenv('APPLOCK_STORE_PASSWORD')) {
    throw new GradleException('APPLOCK_STORE_PASSWORD is required when APPLOCK_KEYSTORE is set.')
  }
  android {
    signingConfigs {
      personal {
        storeFile file(System.getenv('APPLOCK_KEYSTORE'))
        storePassword System.getenv('APPLOCK_STORE_PASSWORD')
        keyAlias 'applock'
        keyPassword System.getenv('APPLOCK_STORE_PASSWORD')
      }
    }
    buildTypes { release { signingConfig signingConfigs.personal } }
  }
}
`;
    }
    return mod;
  });
  return withDangerousMod(config, ['android', async (mod) => {
    // Release has a bundled JS runtime and no network functionality.
    const dir = path.join(mod.modRequest.platformProjectRoot, 'app/src/release');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, 'AndroidManifest.xml'), `<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
  <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
  <application android:usesCleartextTraffic="false" android:allowBackup="false" tools:replace="android:allowBackup" />
</manifest>\n`);
    return mod;
  }]);
}
module.exports = createRunOncePlugin(withAppLock, 'with-app-lock', '1.0.0');
