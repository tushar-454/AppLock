# AppLock

A personal Android app-protection utility for Pixel 6a, built with Expo SDK 58, React Native 0.88, Kotlin and the project's existing NativeWind 5 / Tailwind 4 integration.

The React Native interface lists real launchable apps, searches Unicode names and persists protection switches through a local Expo module. A native AccessibilityService detects app window package names and covers selected apps. A native FragmentActivity hosts AndroidX BiometricPrompt for the device's enrolled strong biometrics or existing screen-lock PIN, pattern or password. Enforcement does not depend on JavaScript remaining alive.

Android 11+ is required. This app needs a standalone native build; Expo Go cannot run its engine.

## Install and enable

1. Install the generated `artifacts/AppLock-1.0.0.apk`, or use `adb install -r artifacts/AppLock-1.0.0.apk`.
2. Open App Lock. Ensure an Android PIN, pattern or password is set.
3. Read the Accessibility disclosure and open Android settings. Enable **App Lock protection**. Android may require **App info → menu → Allow restricted settings** for a sideloaded APK.
4. Return to App Lock and verify **Protection active**. Enable a switch for an ordinary app, leave to Home, then open that app.
5. Android verifies your fingerprint or screen lock. Cancellation returns Home and never creates a grant.

Launchers, Settings, dialers, keyboards, recovery apps and AppLock itself cannot be selected. Protection settings can be changed and the service disabled/uninstalled normally. This is not tamper-resistant security.

## Build a standalone APK locally

Keep the existing npm lockfile and tooling:

```sh
npm ci
npm run typecheck
npm run lint
npx expo-doctor
npm run build:android
```

The build script expects Java 17 and an Android SDK. Set `JAVA_HOME` and `ANDROID_HOME` to your installed toolchain. When unset, it uses the local `.tooling/jdk-17.0.21+9` and `.tooling/android-sdk` provisioned during this task. Install platform tools, `platforms;android-37.0`, build tools 37.0.0, NDK 27.1.12297006 and CMake 3.22.1. Accept the SDK licenses. The installed Expo SDK follows compile API 37 / target API 36; this project sets minimum API 30 for combined system authentication compatibility.

The script generates ignored `android/` through Expo prebuild, runs native unit tests and assembles a release APK with a bundled Hermes JS runtime. No Metro connection or Expo account is required. It builds arm64-v8a for Pixel 6a; `APPLOCK_ABIS=x86_64 npm run build:android` builds for an x86 emulator instead.

A personal testing RSA signing key and random password are generated inside ignored `.tooling/`. Preserve those files privately to install updates without uninstalling. They are not production signing credentials. Do not commit or distribute the private key/password. The script verifies the APK signature and package metadata. Output is `artifacts/AppLock-1.0.0.apk`; the APK and local build caches are ignored.

To run Android-independent state-machine tests with Java 17 (no Android SDK needed after prebuild), use `npm run test:engine`. To run native Android tests after prebuild with your configured toolchain:

```sh
npm run test:native
```

For UI development, use `npx expo run:android` with an authorized Android device or emulator. `npm start` runs Metro. Native code changes require rebuilding. No native source should be hand-edited in generated `android/`.

## Permissions and privacy

- User-enabled Accessibility: package/class metadata for foreground app detection and the accessibility overlay. Window-content retrieval and gestures are disabled.
- `USE_BIOMETRIC`: Android's official prompt; no biometric data is stored.
- `RECEIVE_BOOT_COMPLETED`: initialize local state when Android delivers boot events.
- Targeted launcher/home/keyboard/dialer package-visibility queries; no `QUERY_ALL_PACKAGES`.

No general overlay permission, Usage Access, battery exemption, foreground service, account, analytics, ads, cloud service or custom PIN is used. Release builds remove Internet/network-state permissions and disable app backup. Package identifiers are stored locally in device-protected native preferences; authenticated grants exist only in memory.

## Re-locking and limitations

Re-entry from Home or another launchable app, device screen lock, service restart, reboot and a five-minute session deadline require new authentication. Known system dialogs, biometric UI, permission controllers and keyboards do not end sessions. Expiry during a transient system window is deferred until the next ordinary app event.

Third-party locking is an interaction barrier, not an Android security boundary. Accessibility events arrive after window changes, so brief content exposure is possible. Notifications, recent-app previews, picture-in-picture, split-screen, secondary displays, other users/profiles and service downtime are outside the protection guarantee. Android controls accessibility-service binding and background activity starts. Direct Boot support reads device-protected configuration but does not guarantee coverage at every boot stage. Device credentials may be required before biometrics work after reboot.

See [native architecture](docs/ARCHITECTURE.md), [physical-device acceptance checklist](docs/DEVICE-VALIDATION.md), and [validation record](docs/VALIDATION.md). Hardware results must be recorded from an actual device; compilation and mocked tests are not evidence of fingerprint or reboot behavior.
