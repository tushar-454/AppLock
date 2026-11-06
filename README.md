# AppLock

A personal Android app-protection utility for Pixel 6a, built with Expo SDK 58, React Native 0.88, Kotlin and the project's existing NativeWind 5 / Tailwind 4 integration.

The React Native interface lists real launchable apps, searches Unicode names and persists protection switches through a local Expo module. A native AccessibilityService detects app window package names and covers selected apps. A native FragmentActivity hosts AndroidX BiometricPrompt for the device's enrolled strong biometrics or existing screen-lock PIN, pattern or password. Enforcement does not depend on JavaScript remaining alive.

Android 11+ is required. This app needs a standalone native build; Expo Go cannot run its engine.

## Install and enable

1. Install the generated `dist/applock.apk`, or use `adb install -r dist/applock.apk`.
2. Open App Lock and create/confirm a separate six-digit management PIN. Existing protection selections survive this upgrade. Ensure an Android PIN, pattern or password is also set for protected-app authentication.
3. Read the Accessibility disclosure and open Android settings. Enable **App Lock protection**. Android may require **App info → menu → Allow restricted settings** for a sideloaded APK.
4. Return to App Lock and verify **Protection active**. Enable a switch for an ordinary app, leave to Home, then open that app.
5. Android verifies your fingerprint or screen lock. Cancellation returns Home and never creates a grant.

Launchers, Settings, dialers, keyboards, recovery apps and AppLock itself cannot be selected. AppLock protection settings require the management PIN. Android still permits disabling the service, clearing data or uninstalling through system Settings. This is not tamper-resistant security.

## Build a standalone APK locally

Keep the existing npm lockfile and tooling:

```sh
npm ci
npm run typecheck
npm run lint
npx expo-doctor
npm run build:android
```

The build script expects a compatible JDK (Java 21 was used for the branding release) and an Android SDK. Set `JAVA_HOME` and `ANDROID_HOME` to your installed toolchain. When unset, it discovers Java from `PATH` and the SDK at `~/Android/Sdk`, preferring an existing `.tooling/jdk-17.0.21+9` or `.tooling/android-sdk` if present. Install platform tools, `platforms;android-37.0`, build tools 37.0.0, NDK 27.1.12297006 and CMake 3.22.1. Accept the SDK licenses. The installed Expo SDK follows compile API 37 / target API 36; this project sets minimum API 30 for combined system authentication compatibility.

The script generates ignored `android/` through Expo prebuild, runs native unit tests and assembles a release APK with a bundled Hermes JS runtime. No Metro connection or Expo account is required. It builds arm64-v8a for Pixel 6a; `APPLOCK_ABIS=x86_64 npm run build:android` builds for an x86 emulator instead.

A personal RSA signing key and random password are generated inside ignored `.tooling/`. Preserve `.tooling/applock-personal.keystore` and `.tooling/signing-password` privately to install updates without uninstalling. Do not commit or distribute the private key/password. The script verifies the signature, alignment, bundled runtime and merged manifest before copying the APK to `dist/applock.apk`. The APK, its SHA-256 sidecar and local signing material are ignored.

The previous validation record describes a different signing certificate whose private key was absent from this checkout. The branding release therefore uses a new personal key. An existing installation signed by the old certificate cannot be updated with this APK; restore the old key before rebuilding if an in-place update is required. Uninstalling the old app deletes its local settings.

## Branding and optional EAS builds

The editable SVG master and rendered launcher, adaptive, monochrome and light/dark splash assets are in [assets/branding](assets/branding/README.md). Regenerate PNGs with `python3 scripts/render-branding.py` using system librsvg/Cairo. AppLock's existing screens and native engine are unchanged. The package remains `com.tushar.applock`, app version `1.0.0`, with Android version code `2`. See the [branding release validation record](docs/BRANDING-RELEASE.md) for this APK; the original validation record describes the earlier build.

`eas.json` provides a standalone Android `preview` profile with internal distribution and `buildType: apk`. No development client or store submission is configured. If cloud builds are needed later, run `npx eas-cli@latest login`, link the intended project with `npx eas-cli@latest init`, and import the existing personal Android key through `npx eas-cli@latest credentials -p android` before `npx eas-cli@latest build -p android --profile preview`. Keep the same signing certificate for compatible updates. Download the APK from the completed build's artifact link. Local builds require no Expo account or cloud signing credentials.

The EAS source-copy rules were checked with EAS CLI: tracked `modules/app-lock/android/` and `plugins/with-app-lock.js`, plus uncommitted branding/config changes, are included. Generated root `android/`, private `.tooling/`, dependency directories and APK outputs are excluded. Expo prebuild and autolinking regenerate the native app from the included sources.

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

No general overlay permission, Usage Access, battery exemption, foreground service, account, analytics, ads or cloud service is used. A separate custom six-digit PIN protects only AppLock management; it never authenticates third-party apps. Release builds remove Internet/network-state permissions and disable app backup. Package identifiers are stored locally in device-protected native preferences; authenticated grants exist only in memory.

## Re-locking and limitations

Third-party unlock grace defaults to enabled / 30 seconds, with 10s, 15s, 30s, 1m, 2m and 5m choices in Settings. Each app has its own in-memory grant. The countdown starts on its first genuine exit after authentication; returning never restarts it. Continuous foreground use is not interrupted. With grace disabled, every new entry requires Android authentication. Screen lock, service/process restart, reboot and configuration changes clear grants. Known system dialogs, biometric UI, permission controllers and keyboards do not start the countdown.

AppLock management requires its separate PIN again after genuine departure/reopen. Native lifecycle checks and bridge authorization protect configuration even if JS routes are manipulated. The PIN uses salted PBKDF2 plus Android Keystore HMAC, with persistent retry throttling. There is no PIN reset or bypass: **clearing data or uninstalling removes all protected-app selections and the PIN**. See [PIN and grace security](docs/PIN-AND-GRACE.md) and the [feature validation record](docs/PIN-GRACE-VALIDATION.md).

Third-party locking is an interaction barrier, not an Android security boundary. Accessibility events arrive after window changes, so brief content exposure is possible. Notifications, recent-app previews, picture-in-picture, split-screen, secondary displays, other users/profiles and service downtime are outside the protection guarantee. Android controls accessibility-service binding and background activity starts. Direct Boot support reads device-protected configuration but does not guarantee coverage at every boot stage. Device credentials may be required before biometrics work after reboot.

See [native architecture](docs/ARCHITECTURE.md), [physical-device acceptance checklist](docs/DEVICE-VALIDATION.md), and [validation record](docs/VALIDATION.md). Hardware results must be recorded from an actual device; compilation and mocked tests are not evidence of fingerprint or reboot behavior.
