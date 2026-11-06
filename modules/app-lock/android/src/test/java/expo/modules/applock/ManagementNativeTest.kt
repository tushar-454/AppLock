package expo.modules.applock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.WindowManager
import expo.modules.kotlin.functions.UntypedAsyncFunctionComponent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ManagementNativeTest {
  private val context get() = RuntimeEnvironment.getApplication()
  @Before fun setup() {
    ManagementSecurity.invalidate()
    context.getSharedPreferences("app_lock_pin_v1", Context.MODE_PRIVATE).edit().clear().commit()
    context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE).edit().clear().commit()
    GraceSettings(context).set(true, 30_000)
  }
  @After fun tearDown() { ManagementSecurity.invalidate() }
  @Suppress("UNCHECKED_CAST")
  private fun bridge(name: String, vararg args: Any?) {
    // Invoke the real exported function body, without a JS runtime or an invented auth flag.
    val function = AppLockModule().definition().asyncFunctions.getValue(name)
    val field = UntypedAsyncFunctionComponent::class.java.getDeclaredField("body").apply { isAccessible = true }
    val body = field.get(function) as (Array<out Any?>) -> Any?
    body(args)
  }
  @Test fun allSensitiveBridgeBodiesRejectUnauthenticatedCallsBeforeAccessingContext() {
    val calls = mapOf<String, Array<out Any?>>(
      "getInstalledApps" to emptyArray(), "getProtectedApps" to emptyArray(),
      "setAppProtected" to arrayOf<Any?>("com.example.app", false),
      "getGraceSettings" to emptyArray(), "setGraceSettings" to arrayOf<Any?>(false, 30_000L),
      "changePin" to arrayOf("123456", "654321", "654321"),
      "openAccessibilitySettings" to emptyArray(), "openSecuritySettings" to emptyArray(), "openAppSettings" to emptyArray())
    calls.forEach { (name, args) ->
      try { bridge(name, *args); fail("$name allowed unauthenticated access") }
      catch (e: IllegalStateException) { assertTrue("$name: ${e.message}", e.message!!.contains("Unlock AppLock")) }
    }
  }
  @Test fun authenticatedButBackgroundBridgeCallsAreRejected() {
    synchronized(ManagementSecurity) { ManagementSecurity.session.resume(); ManagementSecurity.session.grant(ManagementSecurity.session.generation); ManagementSecurity.session.pause() }
    try { bridge("setAppProtected", "com.example.app", false); fail("Background mutation allowed") }
    catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Unlock AppLock")) }
  }
  @Test fun nativeAuthorizedActionRunsOnlyInCurrentSession() {
    var executed = false
    synchronized(ManagementSecurity) { ManagementSecurity.session.resume(); ManagementSecurity.session.grant(ManagementSecurity.session.generation) }
    ManagementSecurity.authorized { executed = true }; assertTrue(executed)
    ManagementSecurity.invalidate()
    try { ManagementSecurity.authorized { fail("Executed after invalidation") } } catch (_: IllegalStateException) {}
  }
  @Test fun existingUserMigrationRetainsAllProtectedPackages() {
    ProtectionStore(context).set("com.example.existing", true)
    assertFalse(ManagementSecurity.state(context)["configured"] as Boolean)
    assertFalse(ManagementSecurity.state(context)["authenticated"] as Boolean)
    assertEquals(setOf("com.example.existing"), ProtectionStore(context).read())
  }
  @Test fun lifecycleSecuresWindowAndRevokesOnHome() {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()
    val listener = AppLockPackage().createReactActivityLifecycleListeners(activity).single()
    listener.onCreate(activity, null); listener.onResume(activity)
    assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    synchronized(ManagementSecurity) { ManagementSecurity.session.grant(ManagementSecurity.session.generation) }
    listener.onUserLeaveHint(activity); listener.onPause(activity)
    assertFalse(ManagementSecurity.session.authenticated)
    assertNotNull(activity.window.decorView.findViewWithTag<View>("applock-privacy-cover"))
    listener.onResume(activity); assertFalse(ManagementSecurity.session.authenticated)
    listener.onDestroy(activity); controller.pause().stop().destroy()
  }
  @Test fun deepLinkCoversExistingUiAndRevokesNativeSession() {
    val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
    val listener = AppLockPackage().createReactActivityLifecycleListeners(activity).single()
    listener.onCreate(activity, null); listener.onResume(activity)
    synchronized(ManagementSecurity) { ManagementSecurity.session.grant(ManagementSecurity.session.generation) }
    listener.onNewIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("applock://settings")))
    assertFalse(ManagementSecurity.session.authenticated)
    assertNotNull(activity.window.decorView.findViewWithTag<View>("applock-privacy-cover"))
    listener.onDestroy(activity); controller.pause().stop().destroy()
  }
  @Test fun screenLockRevokesManagementEvenWithoutMonitoringService() {
    val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
    val listener = AppLockPackage().createReactActivityLifecycleListeners(activity).single()
    listener.onCreate(activity, null); listener.onResume(activity)
    synchronized(ManagementSecurity) { ManagementSecurity.session.grant(ManagementSecurity.session.generation) }
    context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF)); shadowOf(Looper.getMainLooper()).idle()
    assertFalse(ManagementSecurity.session.authenticated)
    listener.onDestroy(activity); controller.pause().stop().destroy()
  }
  @Test fun recreationDoesNotRestoreNativeManagementAuthority() {
    ManagementSecurity.session.resume(); ManagementSecurity.session.grant(ManagementSecurity.session.generation)
    val controller = Robolectric.buildActivity(Activity::class.java).setup(); val activity = controller.get()
    val listener = AppLockPackage().createReactActivityLifecycleListeners(activity).single()
    listener.onCreate(activity, android.os.Bundle()); listener.onResume(activity)
    assertFalse(ManagementSecurity.session.authenticated)
    listener.onDestroy(activity); controller.pause().stop().destroy()
  }
  @Test fun graceSettingsPersistWithoutPersistingGrants() {
    GraceSettings(context).set(false, 120_000)
    assertEquals(GracePolicy(false, 120_000), GraceSettings(context).read())
    BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    assertEquals(GracePolicy(false, 120_000), GraceSettings(context).read())
    val engine = LockEngine({ 1000L }); engine.configure(setOf("com.example.app"))
    assertNotNull(engine.enter("com.example.app"))
  }
  @Test fun graceDefaultsAndDurationValidation() {
    context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_grace", Context.MODE_PRIVATE).edit().clear().commit()
    assertEquals(GracePolicy(true, 30_000), GraceSettings(context).read())
    try { GraceSettings(context).set(true, 1234); fail("Invalid duration accepted") } catch (_: IllegalArgumentException) {}
    assertEquals(GracePolicy(true, 30_000), GraceSettings(context).read())
  }
}
