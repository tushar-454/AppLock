package expo.modules.applock

import android.content.Context
import android.content.SharedPreferences

class GraceSettings(context: Context) {
  private val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_grace", Context.MODE_PRIVATE)
  fun read() = GracePolicy(prefs.getBoolean("enabled", true), prefs.getLong("duration", 30_000L).takeIf { it in GracePolicy.DURATIONS } ?: 30_000L)
  fun set(enabled: Boolean, duration: Long) {
    val policy = GracePolicy(enabled, duration)
    check(prefs.edit().putBoolean("enabled", policy.enabled).putLong("duration", policy.durationMs).commit()) { "Could not persist grace settings" }
  }
  fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
  fun unlisten(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
