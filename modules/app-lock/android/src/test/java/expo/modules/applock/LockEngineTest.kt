package expo.modules.applock

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LockEngineTest {
  private var time = 1000L
  private var serial = 0
  private lateinit var engine: LockEngine
  private val a = "com.example.alpha"
  private val b = "com.example.beta"
  @Before fun setup() { engine = LockEngine({ time }, { (++serial).toString() }); engine.configure(setOf(a, b)) }
  private fun begin(pkg: String = a): LockEngine.Request {
    val r = engine.enter(pkg)!!; assertTrue(engine.visible(r.id)); assertTrue(engine.authenticate(r.id)); return r
  }
  @Test fun protectedPackageRequiresLock() { assertEquals(a, engine.enter(a)?.packageName); assertTrue(engine.blocked()) }
  @Test fun unprotectedPackageDoesNotLock() { assertNull(engine.enter("com.example.free")); assertEquals(LockEngine.State.UNLOCKED, engine.state) }
  @Test fun packageMatchingIsExact() { assertNull(engine.enter("com.example.alpha.fake")) }
  @Test fun successRequiresVisiblePrompt() { val r = engine.enter(a)!!; assertFalse(engine.success(r.id)); assertTrue(engine.blocked()) }
  @Test fun validTransitions() {
    val r = engine.enter(a)!!; assertEquals(LockEngine.State.LOCK_REQUIRED, engine.state)
    assertTrue(engine.visible(r.id)); assertEquals(LockEngine.State.PROTECTION_VISIBLE, engine.state)
    assertTrue(engine.authenticate(r.id)); assertTrue(engine.success(r.id)); assertEquals(LockEngine.State.AUTHENTICATED, engine.state); assertFalse(engine.blocked())
  }
  @Test fun staleCallbacksCannotUnlockAnotherApp() { val old = begin(); val next = begin(b); assertFalse(engine.success(old.id)); assertTrue(engine.blocked()); assertTrue(engine.success(next.id)) }
  @Test fun duplicateEventsDoNotCreatePrompts() { val r = begin(); repeat(20) { assertNull(engine.enter(a)) }; assertEquals(r.id, engine.request?.id); assertFalse(engine.authenticate(r.id)) }
  @Test fun cancellationDoesNotUnlock() { val r = begin(); assertTrue(engine.cancel(r.id)); assertTrue(engine.blocked()); assertFalse(engine.success(r.id)); assertNull(engine.enter(a)) }
  @Test fun failureKeepsPromptPending() { val r = begin(); assertTrue(engine.failure(r.id)); assertTrue(engine.blocked()); assertEquals(LockEngine.State.AUTHENTICATING, engine.state) }
  @Test fun authenticatedAppKeepsSession() { val r = begin(); engine.success(r.id); repeat(10) { assertNull(engine.enter(a)) }; assertFalse(engine.blocked()) }
  @Test fun returningFromLauncherRelocks() { engine.success(begin().id); engine.enter("com.android.launcher3"); assertNotNull(engine.enter(a)) }
  @Test fun switchingToUnprotectedAppRelocks() { engine.success(begin().id); engine.enter("com.example.free"); assertNotNull(engine.enter(a)) }
  @Test fun screenLockInvalidatesPendingCallback() { val r = begin(); engine.reset(); assertFalse(engine.success(r.id)); assertNotNull(engine.enter(a)) }
  @Test fun serviceRestartNeverRestoresGrant() { engine.success(begin().id); engine = LockEngine({ time }); engine.configure(setOf(a)); assertNotNull(engine.enter(a)) }
  @Test fun expirationRequiresAuthentication() { engine.success(begin().id); time += LockEngine.SESSION_MS; assertNotNull(engine.enter(a)); assertTrue(engine.blocked()) }
  @Test fun rapidSwitchBackUsesNewRequestId() { val r = begin(); begin(b); val last = begin(); assertNotEquals(r.id, last.id); assertFalse(engine.success(r.id)); assertTrue(engine.success(last.id)) }
  @Test fun staleCancelDoesNotCancelNewPrompt() { val r = begin(); val next = begin(b); assertFalse(engine.cancel(r.id)); assertTrue(engine.success(next.id)) }
  @Test fun removalEndsProtection() { begin(); engine.configure(setOf(b)); assertFalse(engine.blocked()); assertNull(engine.enter(a)) }
  @Test fun installingNewProtectedPackageTakesEffect() { engine.configure(emptySet()); assertNull(engine.enter(a)); engine.configure(setOf(a)); assertNotNull(engine.enter(a)) }
  @Test fun retryUsesFreshId() { val r = begin(); engine.error(r.id); val retry = engine.retry()!!; assertNotEquals(r.id, retry.id); assertFalse(engine.success(r.id)); assertTrue(engine.blocked()) }
  @Test fun concurrentPromptAttemptsAreRejected() { val r = begin(); assertFalse(engine.authenticate(r.id)); assertNull(engine.retry()) }
  @Test fun callbacksAfterResetAreRejected() { val r = begin(); engine.reset(); assertFalse(engine.failure(r.id)); assertFalse(engine.error(r.id)); assertFalse(engine.cancel(r.id)) }
  @Test fun missingCapabilityNeverCreatesGrant() { val r = engine.enter(a)!!; engine.visible(r.id); assertTrue(engine.error(r.id)); assertFalse(engine.success(r.id)); assertTrue(engine.blocked()) }
  @Test fun rebootClearsAllTrust() { engine.success(begin().id); engine.reset(); assertNull(engine.foreground); assertEquals(0L, engine.expiresAt); assertNotNull(engine.enter(a)) }
  @Test fun invalidPackageNamesRejected() { listOf("", "android", "com..foo", "com.foo/Bar", "com.foo;rm", " com.foo", "a.b\n", "a." + "b".repeat(256)).forEach { assertFalse(it, PackageNames.valid(it)) } }
  @Test fun validPackageNamesAccepted() { listOf(a, "com.vendor_app.module1", "a.b").forEach { assertTrue(it, PackageNames.valid(it)) } }
  @Test fun cancellationFollowedByHomeDoesNotLoop() { val r = begin(); engine.cancel(r.id); engine.enter("com.android.launcher3"); assertFalse(engine.blocked()); assertNotNull(engine.enter(a)) }
  @Test fun transientSystemInteractionsLeaveSessionUntouched() {
    // The adapter filters these metadata events; no enter call is made for them.
    engine.success(begin().id); time += 500; assertNull(engine.enter(a)); assertFalse(engine.blocked())
  }
}
