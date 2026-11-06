# Validation record — 2026-11-06

## Environment

Expo 58.0.23, React Native 0.88.1, React 19.3, npm/package-lock.json, NativeWind 5 RC and Tailwind 4 were preserved. No npm runtime dependencies were added, replaced or downgraded. Java 17 and an Android SDK were provisioned into ignored `.tooling/` because this environment initially had no Java, SDK or ADB. The build uses Gradle 9.4.1, compile API 37, target API 36, minimum API 30 and arm64-v8a for Pixel 6a. Generated `android/` is ignored; the local Expo module and config plugin are the source of native configuration. There was no existing EAS configuration to overwrite.

## Automated checks

| Check | Verified result |
| --- | --- |
| `npm run typecheck` | Passed |
| `npm run lint` | Passed |
| ESLint for the typed bridge, config plugin and native-config verifier | Passed |
| Expo Doctor | 20/20 checks passed |
| Expo config public/introspection and Android prebuild | Passed; local AppLock module resolved by autolinking |
| Native manifest/privacy assertions | Passed |
| Production Expo Android export | Passed |
| Independent Kotlin JVM engine/policy tests | 41 passed; these are a subset of the native suite below |
| Native Android unit tests / Robolectric | 71 passed, zero failures or errors |
| Android module lint | Zero errors, eight warnings; error failure enforcement enabled explicitly |
| Release lint vital checks | Passed |
| Bundled UI source freshness | Source-map contents matched current protection provider, app-list screen and setup notice |
| Shell script syntax and Git whitespace checks | Passed |

The 71 tests comprise 28 state-machine tests, 13 platform-policy tests, 12 service-adapter tests, eight persistence tests, six receiver tests and four app-catalog tests. They cover exact package matching, invalid identifiers, stale callbacks, concurrent/duplicate requests, rapid switching, success, failure, cancellation, expiry, screen lock, service interruption, protected re-entry, system-window filtering, emergency recovery, missing capabilities, credential fallback policy, package install/change/removal, boot initialization, corrupt preferences, native persistence and concurrent writes. Service tests verify the real adapter produces a package-bound intent for the native authentication activity. A further test checks recovery when the opaque host supplies coverage after an unavailable overlay.

Robolectric platform fakes and manually driven callbacks validate application logic; they do not validate a physical fingerprint sensor, actual overlay touch interception, or Android background-launch enforcement. The independent JVM tests do not require the Android runtime.

The remaining lint warnings concern the API-31 accessibility-tool attribute ignored on API 30, two intentionally pinned dependencies, public Android color-resource lookup, and four optional Kotlin extension replacements. No lint errors are suppressed. The persistence implementation retains `commit()`'s Boolean result to report failed durable writes. Expo and React Native emit additional upstream deprecation/manifest notices during compilation; their installed versions were preserved.

The lint review corrected biometric status comparisons to use the official symbolic success constant. It also replaced the service registry's strong reference with a weak reference. The UI detaches native status and permission observers in the background, so protection events do not schedule JavaScript refresh work.

## Device and boot verification limits

`adb devices -l` completed successfully and returned no attached devices, including the re-check after the interrupted session resumed. No APK installation, app launch, real fingerprint/PIN authentication, touch blocking, cold-start timing, service restoration on a physical phone, or physical reboot was tested. No emulator was available and `/dev/kvm` was absent. No device was rebooted.

Direct Boot-aware service/receiver declarations and device-protected storage are implemented. Unit tests cover boot receiver configuration restoration and in-memory trust reset. The actual earliest Android service binding before/after first credential unlock remains unverified. Android's keyguard remains responsible for the locked-boot phase; the receiver never launches a prohibited background service. Physical reboot verification requires the owner's explicit approval.

The required physical checks are in [DEVICE-VALIDATION.md](DEVICE-VALIDATION.md). These remain acceptance work for the target Pixel 6a; a successful build must not be interpreted as completed hardware validation. Brief initial exposure, notifications, task previews, split-screen, secondary displays, other profiles and service downtime remain limitations of this architecture.

## Final standalone APK

Release assembly completed successfully after the final native lint corrections: `:app-lock:testDebugUnitTest :app-lock:lintDebug :app:assembleRelease`, with arm64-v8a. Gradle reported `BUILD SUCCESSFUL`.

- Absolute path: `/home/tushar/Documents/code/AppLock/artifacts/AppLock-1.0.0.apk`
- Size: 38,980,422 bytes (37.17 MiB).
- Package: `com.tushar.applock`; version 1.0.0, version code 1.
- Minimum API 30; target API 36; compiled API 37.
- APK SHA-256: `044ca3fd3058fb43eef358c8049c3e8a8b5d36ad7738a5b88e9c42a183842539`.
- `apksigner verify --verbose --print-certs`: passed; APK Signature Scheme v2, one RSA-3072 personal-testing signer.
- Certificate SHA-256: `certificate SHA-256 digest: 81a8e61c36668512a94a9ab1ab9c76147af3df2366d8c3f4f08ee92c09acc64d`.
- `zipalign -c -P 16 -v 4`: passed.
- `aapt dump badging` / `dump permissions`: passed package/permission assertions.
- Embedded `assets/index.android.bundle` and arm64 Hermes libraries verified; Metro is not required.
- Release permissions: `USE_BIOMETRIC`, `RECEIVE_BOOT_COMPLETED`, and the app-specific signature-level AndroidX receiver permission. No Internet, network-state, general overlay, Usage Access or QUERY_ALL_PACKAGES permission.

These verify APK structure, signature, ABI, alignment and packaged runtime. Installation and successful launch on the Pixel 6a remain unverified because no Android device was attached.

The personal signing key/password, build toolchain, native generated project and APK are ignored by Git. Preserve `.tooling/applock-personal.keystore` and `.tooling/signing-password` privately for future compatible updates. The clean starting commit was tagged `applock-before-native-c60520c`. No remote push was requested or performed.
