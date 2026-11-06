# PIN and grace-period validation — 2026-11-06

## Artifact

- Standalone, signed, release Android APK: `/home/tushar/Documents/code/AppLock/dist/applock.apk`.
- Size: **39,105,082 bytes** (37.29 MiB).
- APK SHA-256: `2baf3bea8e803e0ac110cfeee11803a308f12280dc74849a057795d631427213`.
- Package: `com.tushar.applock`; version `1.0.0`; version code `2`; min API 30; target API 36; arm64-v8a.
- Signer certificate SHA-256: `7adcb0de660b8b286f416e81b1fc4945600c5085b0c90181c3152fb0e4a09cda`.
- Existing `.tooling/applock-personal.keystore` was reused. Certificate matches the prior local `dist/applock.apk` saved before implementation. No new key was generated.
- The currently installed certificate could not be checked: `adb devices -l` returned no connected devices. The older certificate documented in `VALIDATION.md` (`81a8e61c…`) differs. Installations signed with that key require the original key for an in-place update. **Uninstalling or clearing storage removes the PIN and all protection selections.**
- EAS `preview` internal-distribution APK profile, application ID, icon, splash and existing permissions were preserved. No Play Store publishing was configured.

## Checks performed

- `npm run lint`: passed.
- `npm run typecheck`: passed.
- `npm run check:native-config`: passed.
- `npm run test:engine`: **84 passed**, zero failures/errors/skips (these pure Kotlin tests are also included in the native suite).
- Native `:app-lock:testDebugUnitTest`: **130 passed**, zero failures/errors/skips.
- `:app-lock:lintDebug` and release vital lint: passed.
- Existing local `scripts/build-android.sh`: passed, including Expo SDK 58 prebuild and signed release assembly. SDK 58 regenerated the ignored Android project; no generated native source was edited manually.
- Final incremental pass against that generated project: `:app-lock:testDebugUnitTest :app-lock:lintDebug :app:assembleRelease -PreactNativeArchitectures=arm64-v8a`, using the same signing environment as the build script: passed. This pass includes the final settings-result and configuration-race regressions. Verification and artifact copying used the existing script's commands. Final hash matches the full build artifact.
- `scripts/verify-apk.sh`: passed APK signature (v2), zip alignment / 16 KB native alignment, package/version/min SDK, bundled Hermes runtime, bundled release JS, release manifest and Accessibility XML checks.
- Release manifest: non-debuggable, backup disabled, cleartext disabled, no Internet/network-state/general-overlay/Usage Access/query-all permissions. Biometric permission, boot receiver, package receiver, non-exported biometric activity, Direct Boot-aware service and accessibility metadata are present.
- Expo's generated package list contains `AppLockPackage`. APK DEX inventory and R8 mapping verify the lifecycle package, native module, management session, service and Android biometric activity. DEX contents also contain the native PBKDF2 algorithm, Keystore alias, separate PIN/grace preference stores and PIN/grace bridge entry points. R8 merges/inlines some helper classes.
- `git diff --check`: passed.

Native suite results:

| Suite | Tests | Failures |
| --- | ---: | ---: |
| AppCatalogTest | 4 | 0 |
| AppLockServiceTest | 18 | 0 |
| LockEngineTest | 42 | 0 |
| ManagementNativeTest | 10 | 0 |
| ManagementSessionTest | 13 | 0 |
| PinCredentialTest | 16 | 0 |
| PlatformPolicyTest | 13 | 0 |
| ProtectionStoreTest | 8 | 0 |
| ReceiversTest | 6 | 0 |

Reports are generated under `modules/app-lock/android/build/reports/tests/testDebugUnitTest/` and `tests/engine/build/reports/tests/test/`. APK verification output and DEX inventory are in ignored `dist/applock-verification.txt` and `dist/applock-native-classes.txt`.

## Security and behavior coverage

PIN setup, confirmation mismatch, exact six-digit validation, salted verifier, production 600,000-iteration KDF round trip, wrong/correct authentication, persistent attempt reservations, cooldown caps, reboot/elapsed-clock reset, process recreation, PIN changes, interrupted writes and existing-user selections are tested. Real exported sensitive bridge bodies reject unauthenticated/background access. Lifecycle tests check FLAG_SECURE, covers, Home, deep links, screen off without the monitoring service, recreation and settings-completion authority. No recovery bypass is implemented; the documented recovery policy requires destructive OS data clearing and reconfiguration.

Grace tests cover default 30 seconds, disabled behavior, all supported configuration validation, first-exit timing, exact expiry, independent apps, rapid switching, continuous use, new-authentication resets, monotonic calculations, screen off, boot, process/service restart, protected-package changes, temporary system windows and settings persistence. Configuration is rechecked on entry and authentication to reject stale callbacks before asynchronous preference observers run; delayed observers do not erase a grant already authenticated under the new policy.

## Not verified on hardware

No Android device was connected. No fingerprint, device-credential fallback, actual reboot, screen recording, frame-exposure, OEM navigation/window ordering or physical process-death flow is claimed. Robolectric uses Android API 35 and simulated lifecycle/broadcast events. JVM tests inject clocks and a test-only HMAC key; production uses Android Keystore. These tests do not establish hardware-backed Keystore behavior or real-device KDF latency. Use `DEVICE-VALIDATION.md` for acceptance, including full-screen OEM permission dialogs and direct return from Android settings. Settings-return handling fails closed if Android does not deliver the expected result.

Existing Android limitations remain: Accessibility events occur after windows change; other apps' notifications/thumbnails, multi-window, profiles and service downtime are outside the guarantee. Android Settings can still disable Accessibility, clear data or uninstall AppLock. A PIN cannot prevent those OS operations.

## Git state

Base branch: `main`; base HEAD: `8d295b7139fb897fc8fed5fe06be5e56cf09fc97`. Feature changes are left uncommitted. The working tree already had branding, app configuration and build-related changes when implementation began; those were preserved. No signing material or APK is tracked or committed by this task.
