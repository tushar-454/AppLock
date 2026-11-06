package expo.modules.applock

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppLockServiceTest {
  private lateinit var controller: ServiceController<AppLockService>
  private lateinit var service: AppLockService
  private val a = "com.example.alpha"
  private val b = "com.example.beta"
  @Before fun setup() {
    val context = RuntimeEnvironment.getApplication()
    context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE).edit().clear().commit()
    ProtectionStore(context).set(a, true); ProtectionStore(context).set(b, true)
    controller = Robolectric.buildService(AppLockService::class.java).create()
    service = controller.get()
    AppLockService::class.java.getDeclaredMethod("onServiceConnected").apply { isAccessible = true }.invoke(service)
    shadowOf(Looper.getMainLooper()).idle()
  }
  @After fun tearDown() { controller.destroy(); shadowOf(Looper.getMainLooper()).idle() }
  private fun event(pkg: String, name: String = "$pkg.MainActivity", type: Int = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
    service.onAccessibilityEvent(AccessibilityEvent.obtain(type).apply { packageName = pkg; className = name })
  }
  private fun begin(): String { event(a); val id = service.engine.request!!.id; assertTrue(service.startPrompt(id)); return id }
  @Test fun eventStartsPackageBoundNativeAuthentication() {
    event(a)
    val intent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
    assertEquals(AuthenticationActivity::class.java.name, intent.component?.className)
    assertEquals(a, intent.getStringExtra("packageName")); assertEquals(service.engine.request?.id, intent.getStringExtra("requestId"))
    assertTrue(service.engine.blocked())
  }
  @Test fun duplicateEventsDoNotLaunchAnotherHost() {
    event(a); shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
    event(a); assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
  }
  @Test fun unrelatedEventTypesNeverTriggerProtection() {
    event(a, type = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
    assertNull(service.engine.request)
  }
  @Test fun systemAndOwnAuthenticationEventsPreserveSession() {
    val id = begin(); assertTrue(service.authenticated(id))
    event("com.android.systemui"); event(service.packageName, AuthenticationActivity::class.java.name)
    assertEquals(id, service.engine.request?.id); assertFalse(service.engine.blocked())
  }
  @Test fun mainUiEndsSession() {
    val id = begin(); service.authenticated(id)
    event(service.packageName, "${service.packageName}.MainActivity"); event(a)
    assertNotEquals(id, service.engine.request?.id); assertTrue(service.engine.blocked())
  }
  @Test fun opaqueHostCanCoverWhenOverlayWasUnavailable() {
    val request = service.engine.enter(a)!!
    assertEquals(LockEngine.State.LOCK_REQUIRED, service.engine.state)
    assertFalse(service.authenticated(request.id))
    service.hostVisible(request.id)
    assertTrue(service.startPrompt(request.id))
    assertTrue(service.engine.blocked())
  }
  @Test fun rapidSwitchRejectsStaleSuccess() { val id = begin(); event(b); assertFalse(service.authenticated(id)); assertEquals(b, service.engine.request?.packageName) }
  @Test fun cancellationDoesNotGrantAccess() { val id = begin(); service.cancelAndHome(id); assertTrue(service.engine.blocked()); assertFalse(service.authenticated(id)) }
  @Test fun destroyedHostDoesNotGrantAccess() { val id = begin(); service.hostLost(id); assertTrue(service.engine.blocked()); assertFalse(service.authenticated(id)) }
  @Test fun screenOffInvalidatesCallbacks() {
    val id = begin()
    RuntimeEnvironment.getApplication().sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
    shadowOf(Looper.getMainLooper()).idle()
    assertFalse(service.authenticated(id)); assertNull(service.engine.request)
  }
  @Test fun emergencyWindowRemovesCoverWithoutLauncherEntry() { begin(); event("com.android.emergency"); assertFalse(service.engine.blocked()); assertNull(service.engine.request) }
  @Test fun interruptedServiceRelocksOnNextEntry() { val id = begin(); service.authenticated(id); service.onInterrupt(); event(a); assertNotEquals(id, service.engine.request?.id); assertTrue(service.engine.blocked()) }
}
