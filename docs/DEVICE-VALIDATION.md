# Pixel 6a acceptance checklist

Automated tests validate the state machine, policy and native persistence; they cannot establish the behavior of a physical fingerprint sensor, OEM Accessibility binding or background activity launch enforcement. Record the device model, Android build fingerprint and AppLock APK SHA-256 before running this checklist.

1. Install the standalone APK, launch App Lock with Metro stopped, and verify actual installed app labels/icons. Search mixed-case Latin names and Unicode names, then clear the search. Verify Settings, dialer, keyboard and launcher rows cannot be protected; AppLock is excluded.
2. With Accessibility off, verify the UI says protection is inactive. Follow the explicit permission disclosure into Android settings. For a sideloaded APK, Android may require App info → menu → Allow restricted settings. Enable App Lock. Return and verify both permission and connected status update. Disable permission and verify inactive status updates; there must be no forced re-enabling.
3. Ensure the device has a secure PIN/pattern/password and optionally enrolled fingerprints. Enable protection for two ordinary apps. Force-close/reopen only the RN task (swipe it from recents; do not Android force-stop AppLock). Verify selections persist.
4. Open protected app A from the launcher. Verify the cover is opaque and blocks app touches, then Android's genuine biometric dialog appears above the opaque native host. Compare earliest launch frames, without claiming a zero-frame guarantee. Fingerprint success should release A without relaunching or losing its current activity.
5. Cancel the Android prompt. Verify A never becomes interactive before Home is reached, Home is usable and no endless prompt loop occurs. Reopen A; authentication must be required. Test the explicit Home button and Back/gesture Back.
6. Try a wrong fingerprint, transient sensor interruption and biometric lockout. Verify no grant is created. Test Android's Use PIN/pattern/password fallback, including a device with no fingerprint enrolled. Never enter the device credential into an AppLock-designed field.
7. Authenticate A; trigger its keyboard, permission dialog, notification shade, and another activity in A. These temporary interactions should not prompt again. Leave to an unrelated app or Home and return within the selected grace duration: A should remain available. Return after the first-exit deadline: A must require Android authentication. With grace disabled, every re-entry must prompt. Switch rapidly A ↔ protected B and cancel/succeed: the latest visible app alone may receive a grant.
8. While the prompt is open, rotate the device, press Power, unlock the device, open notification shade, press Home, and launch B. Check opaque coverage, no duplicate prompt and no stale grant. Repeat with “Don't keep activities” enabled if appropriate, then restore that developer setting.
9. Remain continuously in A for over five minutes: no new prompt should appear. Test the 00:10 first exit / 00:20 return / 00:25 exit / 00:45 return example with 30-second grace: the final return must prompt. Lock/unlock the phone and return to A. Verify the prior session is invalidated.
10. Revoke/re-enable Accessibility, and verify Android reconnects the service and requires new authentication. Test an Android-driven process/service recreation. Force-stop AppLock separately: protection is expected to be unavailable until the user/OS allows the service to run again. Report the gap.
11. Install, update and uninstall a harmless test app. Verify the list refreshes and uninstall removes its protection selection. Verify an in-place update retains selection.
12. With the physical owner's **explicit approval**, reboot. Observe service binding before and after first credential unlock, record any gap and verify protected apps require authentication afterward. Fingerprints may be unavailable until first device credential unlock. Do not disable keyguard or reboot without approval.
13. Verify Settings, emergency dialer and Home remain reachable. Verify disabling/uninstalling AppLock works normally. Test screen sizes/rotation, light/dark theme, Material You colors, large fonts and TalkBack.
14. Verify no networking permissions in the release manifest. Measure cold launch and cover latency on the actual device; no hardware timing result is implied by a successful build.

Useful read-only diagnostics after granting USB debugging:

```sh
adb devices -l
adb shell getprop ro.build.fingerprint
adb shell dumpsys accessibility
adb shell dumpsys package com.tushar.applock
```

Avoid logging Accessibility event payloads or personal screen content. Screenshots of protected apps may expose personal information; use harmless test apps and explicit user consent for captured content.

## Management PIN acceptance

- Upgrade in place with the same certificate; verify previous selections survive first PIN setup. Mismatched confirmation must retry without data loss.
- Confirm six obscured dots, custom keypad, no Gboard, TalkBack button labels, dark/light and Material You colors. PIN windows/screenshots and recents must be secure.
- Wrong PINs clear the dots. After five failures, retry is disabled for the native cooldown; process recreation must not erase it. Never record the real PIN or accessibility payloads.
- Home, Recents, another app, screen lock, external `applock://settings` launch, notification launch, Back/finish, and activity recreation must require the management PIN again. No list or switch may be usable before authentication.
- Permission dialogs and direct return from explicitly opened Android settings should preserve an existing management session. Home during the settings trip followed by relaunch must require PIN. Ordinary in-app Settings navigation must not re-lock.
- Changing PIN requires the old PIN and matching new PIN, then requires the new PIN to unlock. Old PIN must fail afterward.
- Read recovery disclosure. Do not clear real app data as a test: it removes all selections. No device credential or hidden PIN should bypass management authentication.
- Grace settings and selections must persist across restart/reboot; active third-party grants and management sessions must not. A/B deadlines must be independent, including rapid switching. Configuration changes clear grants.
