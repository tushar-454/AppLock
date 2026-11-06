package expo.modules.applock

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Credential work is serialized separately so KDFs never block UI lifecycle invalidation. */
object ManagementSecurity {
  val session = ManagementSession()
  private val credentials = Any()
  fun credential(context: Context): PinCredential {
    val prefs = context.getSharedPreferences("app_lock_pin_v1", Context.MODE_PRIVATE)
    fun decode(value: String?) = value?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
    fun encode(value: ByteArray?) = value?.joinToString("") { "%02x".format(it.toInt() and 255) }
    return PinCredential(read = {
      PinCredential.Record(decode(prefs.getString("salt", null)), decode(prefs.getString("verifier", null)),
        prefs.getInt("failures", 0), prefs.getLong("deadline", 0), prefs.getInt("boot", -1), prefs.getLong("wait", 0))
    }, write = { r ->
      check(prefs.edit().putString("salt", encode(r.salt)).putString("verifier", encode(r.verifier))
        .putInt("failures", r.failures).putLong("deadline", r.deadline).putInt("boot", r.boot).putLong("wait", r.wait).commit()) { "Cannot save PIN security state" }
    }, now = SystemClock::elapsedRealtime, boot = { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) }, pepper = { bytes ->
      val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      val alias = "app_lock_pin_hmac_v1"
      // Never regenerate a lost key for an existing verifier: fail closed instead.
      val key = if (store.containsAlias(alias)) store.getKey(alias, null) as SecretKey else {
        check(!prefs.contains("verifier")) { "PIN key unavailable. See recovery policy." }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
          init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKey()
      }
      Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(bytes)
    })
  }
  fun state(context: Context): Map<String, Any> = synchronized(credentials) {
    val pin = credential(context)
    val configured = pin.configured()
    val remaining = pin.remaining()
    synchronized(this) {
      mapOf("configured" to configured, "authenticated" to (session.authenticated && session.resumed),
        "retryAfterMs" to remaining, "generation" to session.generation)
    }
  }
  private fun generation(requireAuthenticated: Boolean = false): Long = synchronized(this) {
    check(session.resumed) { "Open AppLock to enter your PIN" }
    if (requireAuthenticated) session.requireAccess()
    session.generation
  }
  private fun requireCurrent(generation: Long) = synchronized(this) {
    check(session.resumed && session.generation == generation) { "AppLock session ended. Enter your PIN again." }
  }
  fun create(context: Context, pin: String, confirmation: String): Map<String, Any> = synchronized(credentials) {
    val version = generation()
    credential(context).create(pin, confirmation) { commit -> synchronized(this) {
      requireCurrent(version); commit(); session.grant(version)
    } }
    state(context)
  }
  fun verify(context: Context, pin: String): Map<String, Any> = synchronized(credentials) {
    val version = generation()
    if (credential(context).verify(pin)) synchronized(this) { session.grant(version) }
    state(context)
  }
  fun change(context: Context, current: String, next: String, confirmation: String) = synchronized(credentials) {
    val version = generation(requireAuthenticated = true)
    credential(context).change(current, next, confirmation) { commit -> synchronized(this) {
      requireCurrent(version); commit(); session.invalidate()
    } }
    RuntimeEvents.changed()
  }
  fun externalApp() = synchronized(this) { if (session.authenticated) { session.invalidate(); RuntimeEvents.changed() } }
  fun invalidate() = synchronized(this) { session.invalidate(); RuntimeEvents.changed() }
  fun <T> authorized(action: () -> T): T = synchronized(this) { session.requireAccess(); action() }
  fun resume(activity: Activity) = synchronized(this) {
    if (activity.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) session.invalidate()
    session.resume(); session.returned(); RuntimeEvents.changed()
  }
}
