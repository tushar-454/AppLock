package expo.modules.applock

import android.content.Context
import android.content.SharedPreferences

/** Package identifiers only; grants and authentication data never reach disk. */
class ProtectionStore(context: Context) {
  private val prefs: SharedPreferences = context.createDeviceProtectedStorageContext()
    .getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE)
  companion object { private val lock = Any() }
  fun read(): Set<String> = synchronized(lock) { try {
    prefs.getStringSet("packages", emptySet()).orEmpty().filter(PackageNames::valid).toSet()
  } catch (_: ClassCastException) { emptySet() } }

  fun healthy(): Boolean = synchronized(lock) {
    try { prefs.getStringSet("packages", emptySet()).orEmpty().all(PackageNames::valid) }
    catch (_: ClassCastException) { false }
  }
  fun set(packageName: String, enabled: Boolean): Set<String> = synchronized(lock) {
    require(PackageNames.valid(packageName)) { "Invalid Android package name" }
    val next = read().toMutableSet().apply { if (enabled) add(packageName) else remove(packageName) }.toSet()
    check(prefs.edit().putStringSet("packages", next).commit()) { "Could not persist protection settings" }
    next
  }
  fun listen(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
  fun unlisten(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
