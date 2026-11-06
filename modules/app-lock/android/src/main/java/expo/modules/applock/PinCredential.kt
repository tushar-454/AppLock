package expo.modules.applock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** No reversible PIN storage. pepper() uses a non-exportable Android Keystore HMAC key. */
class PinCredential(
  private val read: () -> Record,
  private val write: (Record) -> Unit,
  private val now: () -> Long,
  private val boot: () -> Int,
  private val pepper: (ByteArray) -> ByteArray,
  private val iterations: Int = 600_000,
) {
  data class Record(val salt: ByteArray? = null, val verifier: ByteArray? = null,
    val failures: Int = 0, val deadline: Long = 0, val boot: Int = -1, val wait: Long = 0)
  private fun valid(pin: String) = pin.length == 6 && pin.all { it in '0'..'9' }
  private fun derive(pin: String, salt: ByteArray): ByteArray {
    val chars = pin.toCharArray()
    val spec = PBEKeySpec(chars, salt, iterations, 256)
    return try {
      val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
      try { pepper(derived) } finally { derived.fill(0) }
    } finally { chars.fill('\u0000'); spec.clearPassword() }
  }
  fun configured(): Boolean = read().let { it.salt != null || it.verifier != null }
  fun remaining(): Long {
    var record = read()
    if (record.boot != boot() || record.deadline > now() + record.wait) {
      // A reboot/time change never erases accumulated failures or the outstanding penalty.
      record = record.copy(boot = boot(), deadline = now() + record.wait)
      write(record)
    }
    return (record.deadline - now()).coerceAtLeast(0)
  }
  fun create(pin: String, confirmation: String, beforeSave: ((() -> Unit) -> Unit) = { it() }) {
    check(!configured()) { "PIN already configured" }
    save(pin, confirmation, beforeSave)
  }
  private fun save(pin: String, confirmation: String, beforeSave: ((() -> Unit) -> Unit)) {
    require(valid(pin)) { "Use exactly six digits" }
    require(pin == confirmation) { "PINs do not match. Try again." }
    val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val verifier = derive(pin, salt)
    beforeSave { write(Record(salt, verifier, boot = boot())) }
  }
  fun verify(pin: String): Boolean {
    check(configured()) { "Create an AppLock PIN first" }
    check(remaining() == 0L) { "Too many attempts. Wait before trying again." }
    val record = read()
    val failures = (record.failures + 1).coerceAtMost(30)
    val wait = if (failures < 5) 0L else (30_000L * (1L shl (failures - 5).coerceAtMost(4))).coerceAtMost(300_000L)
    // Reserve an attempt durably BEFORE expensive work, including interrupted attempts.
    write(record.copy(failures = failures, deadline = now() + wait, boot = boot(), wait = wait))
    val salt = checkNotNull(record.salt) { "PIN storage damaged; see recovery policy" }
    val expected = checkNotNull(record.verifier) { "PIN storage damaged; see recovery policy" }
    val actual = derive(if (valid(pin)) pin else "invalid", salt)
    val matches = try { MessageDigest.isEqual(expected, actual) && valid(pin) } finally { actual.fill(0) }
    if (matches) write(record.copy(failures = 0, deadline = 0, boot = boot(), wait = 0))
    return matches
  }
  fun change(current: String, next: String, confirmation: String, beforeSave: ((() -> Unit) -> Unit) = { it() }) {
    require(valid(next) && next == confirmation) { "New PINs must match and contain six digits" }
    check(verify(current)) { "Incorrect current PIN" }
    save(next, confirmation, beforeSave)
  }
}
