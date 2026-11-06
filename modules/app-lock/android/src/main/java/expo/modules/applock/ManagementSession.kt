package expo.modules.applock

/** Memory-only, native-owned management authority. All calls are serialized by ManagementSecurity. */
class ManagementSession {
  var authenticated = false; private set
  var resumed = false; private set
  var generation = 0L; private set
  private var settingsTrip = false
  private var settingsResult = false
  fun resume() {
    if (settingsTrip && !settingsResult) invalidate()
    resumed = true
  }
  fun pause() { resumed = false }
  fun invalidate() { authenticated = false; settingsTrip = false; settingsResult = false; generation++ }
  fun grant(expectedGeneration: Long) {
    check(resumed && generation == expectedGeneration) { "AppLock session ended. Enter your PIN again." }
    authenticated = true
  }
  fun requireAccess() { check(authenticated && resumed) { "Unlock AppLock with your application PIN first" } }
  fun settings() { requireAccess(); settingsTrip = true; settingsResult = false }
  fun leave() { if (!settingsTrip) invalidate() }
  fun settingsCompleted() { if (settingsTrip) settingsResult = true }
  fun returned() { settingsTrip = false; settingsResult = false }
  fun externalIntent() { invalidate() }
}
