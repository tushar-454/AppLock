package expo.modules.applock

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PinCredentialTest {
  private var record = PinCredential.Record()
  private var time = 1000L
  private var boot = 1
  private lateinit var pin: PinCredential
  private fun instance(iterations: Int = 1000) = PinCredential({ record }, { record = it }, { time }, { boot }, {
    Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 42 }, "HmacSHA256")) }.doFinal(it)
  }, iterations)
  @Before fun setup() { pin = instance() }
  private fun rejected(action: () -> Unit) {
    try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
  }
  @Test fun firstSetupStoresVerifierAndAuthenticates() {
    assertFalse(pin.configured()); pin.create("012345", "012345")
    assertTrue(pin.configured()); assertEquals(32, record.salt!!.size); assertEquals(32, record.verifier!!.size)
    assertFalse(record.verifier!!.contentEquals("012345".toByteArray())); assertTrue(pin.verify("012345"))
  }
  @Test fun mismatchDoesNotCompleteSetup() { rejected { pin.create("123456", "123457") }; assertFalse(pin.configured()) }
  @Test fun exactSixAsciiDigitsRequired() {
    listOf("", "12345", "1234567", "abcdef", "١٢٣٤٥٦").forEach { rejected { pin.create(it, it) } }; assertFalse(pin.configured())
  }
  @Test fun setupCannotOverwriteExistingPin() { pin.create("123456", "123456"); rejected { pin.create("000000", "000000") }; assertTrue(pin.verify("123456")) }
  @Test fun wrongPinRejected() { pin.create("123456", "123456"); assertFalse(pin.verify("000000")); assertEquals(1, record.failures) }
  @Test fun independentRandomSalts() {
    pin.create("123456", "123456"); val first = record; record = PinCredential.Record(); pin.create("123456", "123456")
    assertFalse(first.salt!!.contentEquals(record.salt!!)); assertFalse(first.verifier!!.contentEquals(record.verifier!!))
  }
  @Test fun throttlingPersistsThroughProcessRecreation() {
    pin.create("123456", "123456"); repeat(5) { assertFalse(pin.verify("000000")) }
    assertEquals(30_000L, pin.remaining()); pin = instance()
    rejected { pin.verify("123456") }; time += 30_000; assertTrue(pin.verify("123456")); assertEquals(0, record.failures)
  }
  @Test fun rebootRetainsOutstandingPenalty() {
    pin.create("123456", "123456"); repeat(5) { pin.verify("000000") }
    boot++; time = 10; pin = instance(); assertEquals(30_000L, pin.remaining()); rejected { pin.verify("123456") }
  }
  @Test fun cooldownCappedAndNeverPermanent() {
    pin.create("123456", "123456")
    repeat(25) { time += pin.remaining(); assertFalse(pin.verify("000000")); assertTrue(pin.remaining() <= 300_000) }
    time += pin.remaining(); assertTrue(pin.verify("123456"))
  }
  @Test fun changeRequiresCurrentPinAndConfirmation() {
    pin.create("123456", "123456"); rejected { pin.change("000000", "654321", "654321") }
    rejected { pin.change("123456", "654321", "111111") }; assertTrue(pin.verify("123456"))
    pin.change("123456", "654321", "654321"); assertFalse(pin.verify("123456")); assertTrue(pin.verify("654321"))
  }
  @Test fun corruptedPartialRecordCannotBeResetAsFirstUse() {
    record = PinCredential.Record(salt = ByteArray(32)); assertTrue(pin.configured()); rejected { pin.create("123456", "123456") }
    rejected { pin.verify("123456") }
  }
  @Test fun productionKdfRoundTrip() { pin = instance(600_000); pin.create("654321", "654321"); assertTrue(pin.verify("654321")) }
  @Test fun interruptedVerificationAlreadyConsumedAttempt() {
    pin.create("123456", "123456")
    val interrupted = PinCredential({ record }, { record = it }, { time }, { boot }, { error("Simulated process loss") }, 1000)
    rejected { interrupted.verify("000000") }; assertEquals(1, record.failures)
  }
  @Test fun lifecycleChangeDuringSetupPreventsCredentialWrite() {
    rejected { pin.create("123456", "123456") { error("Session ended during KDF") } }
    assertFalse(pin.configured())
  }
  @Test fun lifecycleChangeDuringPinChangeKeepsOldCredential() {
    pin.create("123456", "123456")
    rejected { pin.change("123456", "654321", "654321") { error("Session ended during KDF") } }
    assertTrue(pin.verify("123456")); assertFalse(pin.verify("654321"))
  }
  @Test fun elapsedResetWithoutBootCountStillAppliesBoundedPenalty() {
    time = 900_000; pin.create("123456", "123456"); repeat(5) { pin.verify("000000") }
    time = 0; assertEquals(30_000L, pin.remaining()); time += 30_000; assertTrue(pin.verify("123456"))
  }
}
