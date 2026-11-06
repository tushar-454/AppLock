# AppLock management PIN and protected-app grace sessions

## Two independent authentication domains

AppLock management requires its own six-digit PIN. Protected third-party applications still use the existing AndroidX BiometricPrompt with BIOMETRIC_STRONG | DEVICE_CREDENTIAL. Neither credential can create a grant in the other domain. Grace settings never extend management access.

The root React Native gate mounts neither the router stack nor ProtectionProvider before native management authentication. This also gates settings deep links and prevents fetching the installed/protected lists before authentication. Native API checks remain authoritative if a route or JS state is manipulated.

An autolinked Expo `AppLockPackage` lifecycle listener applies FLAG_SECURE before JS, places an opaque touch-blocking cover over the native root on creation, pause and external intents, and revokes management sessions on genuine departure, stop, activity destruction/recreation, screen off and external intents. Native screen-off handling is independent of the AccessibilityService. The cover is removed after the React gate commits, with a matching native session generation. Temporary pauses do not erase authority, but protected APIs cannot run while the activity is paused. Ordinary Expo Router navigation remains in the same activity. Explicit authenticated trips to Android Accessibility, Security or App Info settings launch for a native activity result in the same task. Only completion of that request can preserve a session on direct return; resurfacing AppLock without the result revokes it. Failed launches cancel the exemption, and external relaunch/deep links revoke authority. While Settings is the top activity, returning to that task continues the explicit settings excursion; no AppLock management UI is accessible until Settings finishes. Service metadata provides an additional external-app exit signal when monitoring is enabled.

## PIN storage and verification

`PinCredential` uses PBKDF2WithHmacSHA256 with 600,000 iterations, a fresh SecureRandom 32-byte salt and a 256-bit derived value. The final verifier is HMAC-SHA256 of that derived value with a per-installation, non-exportable Android Keystore key. This key makes a copied preference file alone insufficient for offline PIN guessing. Hardware-backed storage depends on the device. There is no encrypted/recoverable PIN, hardcoded key, master PIN, or verifier getter on the bridge. Comparison uses MessageDigest.isEqual. KDF inputs/derived byte buffers are cleared where practical; JavaScript and JVM immutable strings cannot be reliably zeroed.

Only salt, verifier and retry metadata are stored in app-private, credential-protected native preferences (`app_lock_pin_v1`). App backup remains disabled. Existing device-protected `app_lock_protection` package selections are untouched by PIN setup, verification and PIN changes. A partial/damaged credential record fails closed. Losing the Keystore key does not cause automatic verifier replacement.

Credential work runs on Expo's native module queue, outside the UI thread. A credential lock serializes verification and credential writes; a separate short native monitor serializes lifecycle state changes and bridge authorization. Generation checks before credential writes and before granting authority reject work completed after the initiating session ended. The KDF never holds the lifecycle monitor. Setup checks that no credential exists, validates confirmation in Kotlin and grants only a current foreground session. Changing the PIN requires an existing foreground session, verifies the old PIN using the same throttled verifier, checks confirmation, writes a new salt/verifier, and ends the management session.

Every verification reserves a durable attempt before KDF work. After five failures, cooldowns start at 30 seconds and grow to a maximum of five minutes. Successful verification clears failures. Deadlines use elapsedRealtime and BOOT_COUNT; a reboot re-applies the last outstanding penalty conservatively instead of trusting wall time. UI countdown timers only display native state. Killing/restarting JS or the process does not erase failures or cooldowns. No permanent lockout is created. This is application-level throttling, not a hardware-backed attempt counter; root, app-data replacement and a compromised OS are outside its guarantee.

All current sensitive exported functions check native authority: installed/protected list reads, setAppProtected, grace settings reads/writes, changePin, and opening Android management settings. There are no exported bulk-clear/disable operations. Internal ProtectionStore reads and uninstall cleanup stay available to the service/receivers without creating management authority. Future sensitive bridge methods must use `ManagementSecurity.authorized` too.

## Recovery policy

This release intentionally has **no PIN recovery bypass**, including no device-credential PIN reset. A forgotten PIN or unavailable/corrupt Keystore credential requires clearing AppLock storage in Android Settings and setting up protection again. **This removes all protected-app selections as well as the PIN.** Uninstalling has the same data-loss consequence. The recovery policy appears on the PIN screen and Settings. Knowing the Android device credential does not unlock this PIN-protected management interface.

Android Settings, emergency functions and uninstall/data-clear remain reachable. Someone able to disable Accessibility, clear application data or uninstall AppLock can remove third-party protection. An ordinary application cannot prevent these actions. This is not device-owner policy or tamper-resistant OS security.

## Grace policy

Defaults are enabled with a duration of 30 seconds. Choices are 10, 15, 30, 60, 120 and 300 seconds. Only this configuration persists in separate device-protected native preferences (`app_lock_grace`), across process restarts and reboot.

`LockEngine` retains its existing request-ID/state transitions, with a memory-only map of grants keyed by package name. A successful Android prompt creates a grant without an exit timestamp. The first genuine departure records elapsedRealtime. Returning before firstExit + duration reuses the grant; further departures do not change firstExit. Expiry is evaluated only on re-entry, so a continuously foreground app is never interrupted. A new successful Android authentication replaces that app's grant. A grant for A never authenticates B. When disabled, leaving immediately drops the corresponding grant.

For example: authenticate A at 00:00, leave at 00:10, return at 00:20, leave at 00:25, return at 00:45: the original 00:40 deadline has passed, so another Android prompt is required.

SystemUI (notification shade, Quick Settings, system biometric UI), permission controllers, keyboards and AppLock's authentication host are filtered before entering the engine. Genuine Home/launcher and other app entries start the countdown; unknown valid external package events are treated conservatively as departure. Android Settings/emergency recovery windows clear grants and remain accessible. No JS timers, alarms, polling or disk grants are used. Screen lock, reboot, process/service restart, untrustworthy service interruption, protected package changes/removal, and any grace-setting change invalidate all grants. Invalidating all grants on configuration changes is deliberately conservative. Stale callbacks cannot authorize a new request.

## Validation scope

Run `npm run lint`, `npm run typecheck`, `npm run test:engine`, `npm run test:native`, and `npm run build:android`. `docs/PIN-GRACE-VALIDATION.md` records this release's actual results. The engine and credential tests inject clocks for deterministic deadlines and reboot scenarios; the production KDF also has a full 600,000-iteration round trip test. Robolectric tests exercise the real exported sensitive bridge bodies, lifecycle windows and data migration.

Robolectric cannot establish Android Keystore hardware security, fingerprint behavior, OEM foreground event ordering, or frame-by-frame UI privacy. No physical device was connected during implementation. Use the updated device checklist before relying on the feature on a particular Android build. Multi-window, notifications, other profiles, third-party app thumbnails, OS force-stop/revocation, and the interval before an Accessibility event remain existing limitations.

References: [Expo 58](https://docs.expo.dev/versions/v58.0.0/), [Expo Android lifecycle listeners](https://docs.expo.dev/modules/android-lifecycle-listeners/), [Expo module API](https://docs.expo.dev/modules/module-api/), [Android cryptography](https://developer.android.com/privacy-and-security/cryptography), [Android Keystore](https://developer.android.com/privacy-and-security/keystore).
