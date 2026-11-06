# AppLock branding release — 2026-11-06

This record covers the current branded APK. `VALIDATION.md` records the earlier implementation build and a different signing certificate.

## Scope and configuration

The starting Git working tree was clean. App screens, Kotlin source, native module manifest, AndroidX biometric dependencies, custom config plugin and dependency lockfile were preserved. Generated root `android/` was absent initially and was regenerated with Expo prebuild, without hand-editing native files.

- Expo SDK 58.0.23; React Native 0.88.1; React 19.3.0.
- Splash plugin 58.0.13; Router 58.0.30; config plugins 58.0.12; custom `with-app-lock` 1.0.0.
- App name `AppLock`; slug `applock`; established Android ID `com.tushar.applock` preserved.
- Version `1.0.0`; Android version code incremented from 1 to 2.
- Existing default orientation and automatic light/dark appearance preserved. iOS configuration unchanged.
- Existing accessibility overlay, biometric authentication activity, boot/package receivers, permissions and Android minimum API 30 preserved.
- Official splash plugin: transparent logo, `imageWidth: 88`, `resizeMode: contain`, background `#FFFBFE`; dark logo on `#141218`.
- `eas.json`: Android `preview`, `distribution: internal`, `developmentClient: false`, `buildType: apk`, local app version source. No store submission profile.

## Branding verification

Editable master: `assets/branding/applock.svg`. Original shield and negative-space lock geometry; no external artwork, text, gradients or fonts. PNGs were rendered with system librsvg/Cairo using `scripts/render-branding.py` and inspected visually.

| PNG | Dimensions | Transparency |
| --- | --- | --- |
| `assets/branding/icon.png` | 1024 × 1024 | Fully opaque lavender background |
| `assets/branding/adaptive-foreground.png` | 1024 × 1024 | Transparent canvas and lock cutout |
| `assets/branding/adaptive-monochrome.png` | 1024 × 1024 | White alpha mask, transparent cutout |
| `assets/branding/splash.png` | 288 × 288 | Transparent |
| `assets/branding/splash-dark.png` | 288 × 288 | Transparent |

Every visible adaptive pixel fits inside a 285.56 px radius about the center, below Android's 312.89 px central safe-circle limit at this resolution. Both adaptive layers have identical alignment. Generated native resources contain foreground/background/monochrome icon references and splash drawables for light/dark mode across all Android densities. Splash visuals have not been observed on physical hardware.

## Configuration validation

- `npm run typecheck`: passed.
- `npm run lint`: passed.
- `npm run check:native-config`: passed.
- Native Kotlin/Robolectric tests: 71 passed, zero failures, errors or skipped tests (28 engine, 13 policy, 12 service, eight persistence, six receiver and four app-catalog tests).
- Native AppLock Android lint: passed, zero errors and eight existing warnings. Existing upstream/deprecation warnings were not suppressed.
- Network-enabled `npx expo-doctor`: 20/20 checks passed.
- Expo dependency check: compatible with the installed SDK map; no dependency changes.
- Expo Android prebuild and config introspection: passed.
- EAS CLI 27.4.1 `@expo/eas-json` schema/profile resolution: passed.
- EAS CLI source-copy implementation: verified all 17 Kotlin source/test files and local module configuration, manifest, Gradle script, custom plugin, new branding and `eas.json` are included. No files from ignored root `android/`, `.tooling/`, `dist/` or `node_modules/` were included. Empty ignored directory entries can exist in a source copy; signing files are excluded.
- Shell syntax and Git whitespace checks: passed.

## Build and signing

Local native Gradle release build, through `npm run build:android`, using the installed Java 21 JDK and Android SDK. No EAS cloud build, Expo project linking or account login was required. No build method switch was necessary.

SDK defaults retained: Gradle 9.4.1, AGP 9.2.1, Kotlin plugin 2.2.10, build tools 37.0.0, compile API 37, target API 36, NDK 27.1.12297006. Pixel 6a ABI: `arm64-v8a`.

The old private signing key referenced in the earlier validation record was absent. A new RSA-3072 personal key and cryptographically random password were generated in ignored `.tooling/`, with private file permissions. Preserve `.tooling/applock-personal.keystore` and `.tooling/signing-password` privately. An older installation signed with the previous certificate requires that original key for an in-place update. Uninstalling an older installation deletes its local protection settings.

## Verified final APK

- Path: `/home/tushar/Documents/code/AppLock/dist/applock.apk`.
- Size: **39,061,958 bytes (37.25 MiB)**.
- APK SHA-256: `ffe303da4853841f1cf2af3a36448b116b4be5fba7b0b5a19370f289dba212d1`.
- Certificate SHA-256: `7adcb0de660b8b286f416e81b1fc4945600c5085b0c90181c3152fb0e4a09cda`.
- `apksigner verify --verbose --print-certs`: passed, Signature Scheme v2, one RSA-3072 signer, `CN=AppLock Personal Testing`.
- `zipalign -c -P 16 -v 4`: passed.
- Package `com.tushar.applock`; version `1.0.0`; version code `2`; minimum API 30; target API 36; compile API 37; arm64-v8a.
- Gradle `:app-lock:testDebugUnitTest :app-lock:lintDebug :app:assembleRelease`: `BUILD SUCCESSFUL`, 695 tasks, 21m 15s. Release lint vital checks passed.
- Final copy compared byte-for-byte against the verified Gradle output.
- Merged manifest extracted from APK: service, required accessibility metadata, private biometric activity and direct-boot-aware boot/package receivers verified. Release is not debuggable; backup and cleartext traffic disabled.
- APK permissions: `USE_BIOMETRIC`, `RECEIVE_BOOT_COMPLETED`, and app-specific AndroidX `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No Internet, network-state, general overlay, Usage Access or broad package-query permission.
- Packaged accessibility XML resolved through the resource table because release optimization shortens resource ZIP paths. Window-state events verified; content retrieval and gestures remain disabled. No native configuration was altered to accommodate verification.
- `apkanalyzer dex packages --defined-only` with the R8 mappings verified defined `AppLockModule`, `AppLockService`, `AuthenticationActivity`, `BootReceiver`, `PackageReceiver`, `ExpoModulesPackageList`, Expo's native `Module` base and AndroidX biometric classes. R8 optimization preserves the existing native implementation.
- Packaged adaptive icon XML verified to reference foreground, background and monochrome layers.
- Bundled `assets/index.android.bundle`, `lib/arm64-v8a/libhermesvm.so` and other native libraries verified. A standalone release was built; no Expo Go or development server dependency.
- Supporting ignored outputs: `dist/applock.apk.sha256`, `dist/applock-verification.txt`, `dist/applock-native-classes.txt`.

The reusable build script discovers installed tools, preserves existing signing material, verifies the signed release and only then copies it to `dist/applock.apk`. The verifier follows resource-table names, so optimized resource filenames do not cause false failures. No APK, secret or generated native project was committed.

## Device validation limits

ADB was checked with host access and returned no connected devices. Installation, launch, physical fingerprint/PIN authentication, actual overlay interception and visual inspection on the Pixel 6a remain unverified. No reboot or device security setting changes were performed. Follow `DEVICE-VALIDATION.md` for physical acceptance checks.

To install, transfer `dist/applock.apk` to the Pixel 6a, open it and permit installation from the chosen file source when Android requests it. Open AppLock and follow its existing Accessibility setup. Android may require the owner to allow restricted settings for this sideloaded app. Alternatively, with an authorized USB connection, run `adb install -r dist/applock.apk`. The installed release requires no Expo Go, Metro, server or subsequent USB connection.

## Documentation used

- [Expo SDK 58](https://docs.expo.dev/versions/v58.0.0/) and [Expo documentation index](https://docs.expo.dev/llms.txt).
- [SDK 58 splash plugin](https://docs.expo.dev/versions/v58.0.0/sdk/splash-screen/).
- [SDK 58 app configuration](https://docs.expo.dev/versions/v58.0.0/config/app/).
- [EAS build profiles](https://docs.expo.dev/build/eas-json/) and [APK builds](https://docs.expo.dev/build-reference/apk/).
- [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).
