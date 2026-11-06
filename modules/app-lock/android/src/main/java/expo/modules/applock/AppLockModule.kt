package expo.modules.applock

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.biometric.BiometricManager
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.functions.Queues
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.Locale

class AppLockModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext) { "Android context is unavailable" }
  private var catalog: AppCatalog? = null
  private var observer: ContentObserver? = null
  private var observedContext: Context? = null
  private var revision = -1L
  companion object { const val SETTINGS_REQUEST = 48021 }
  private val notify: () -> Unit = { sendEvent("onStatusChanged", emptyMap<String, Any>()) }
  private fun catalog(): AppCatalog {
    if (catalog == null) catalog = AppCatalog(context)
    if (revision != RuntimeEvents.catalogRevision) { catalog!!.invalidate(); revision = RuntimeEvents.catalogRevision }
    return catalog!!
  }
  private fun status(): Map<String, Any> {
    val manager = context.getSystemService(AccessibilityManager::class.java)
    val component = ComponentName(context, AppLockService::class.java)
    val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
      ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == component
    }
    val auth = BiometricManager.from(context).canAuthenticate(AuthenticationActivity.AUTHENTICATORS)
    val secure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
    val credential = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
    val canAuthenticate = PlatformPolicy.authentication(auth == BiometricManager.BIOMETRIC_SUCCESS, credential == BiometricManager.BIOMETRIC_SUCCESS, secure) != PlatformPolicy.Authentication.UNAVAILABLE
    val healthy = ProtectionStore(context).healthy()
    val active = enabled && RuntimeEvents.service != null
    return mapOf("accessibilityEnabled" to enabled, "serviceConnected" to active, "deviceSecure" to secure,
      "canAuthenticate" to canAuthenticate, "authenticationCode" to auth,
      "configurationHealthy" to healthy, "status" to PlatformPolicy.status(enabled, active, canAuthenticate, healthy),
      "authenticationMethod" to "Strong biometric or Android PIN, pattern or password")
  }
  override fun definition() = ModuleDefinition {
    Name("AppLock")
    Events("onStatusChanged")
    OnStartObserving {
      RuntimeEvents.add(notify)
      val ctx = context
      observedContext = ctx
      observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { notify() }
      }.also { ctx.contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, it) }
    }
    OnStopObserving { stopObserving() }
    OnDestroy { stopObserving() }
    OnActivityEntersForeground { notify() }
    OnActivityResult { _, result ->
      if (result.requestCode == SETTINGS_REQUEST) synchronized(ManagementSecurity) { ManagementSecurity.session.settingsCompleted() }
    }
    AsyncFunction("getManagementState") { ManagementSecurity.state(context) }
    AsyncFunction("createPin") { pin: String, confirmation: String -> ManagementSecurity.create(context, pin, confirmation) }
    AsyncFunction("verifyPin") { pin: String -> ManagementSecurity.verify(context, pin) }
    AsyncFunction("changePin") { current: String, next: String, confirmation: String ->
      // Check before resolving context too; no unauthenticated bridge entry can touch credentials.
      synchronized(ManagementSecurity) { ManagementSecurity.session.requireAccess() }
      ManagementSecurity.change(context, current, next, confirmation)
    }
    AsyncFunction("lockManagement") { ManagementSecurity.invalidate() }
    AsyncFunction("acknowledgeManagementScreen") { generation: Long ->
      appContext.currentActivity?.let { activity ->
        activity.runOnUiThread {
          synchronized(ManagementSecurity) {
            if (ManagementSecurity.session.resumed && ManagementSecurity.session.generation == generation) {
              val root = activity.window.decorView as android.view.ViewGroup
              root.findViewWithTag<android.view.View>("applock-privacy-cover")?.let { root.removeView(it) }
            }
          }
        }
      }
    }
    AsyncFunction("getGraceSettings") { ManagementSecurity.authorized {
      GraceSettings(context).read().let { mapOf("enabled" to it.enabled, "durationMs" to it.durationMs) }
    } }
    AsyncFunction("setGraceSettings") { enabled: Boolean, durationMs: Long -> ManagementSecurity.authorized {
      GraceSettings(context).set(enabled, durationMs)
      RuntimeEvents.changed()
    } }
    AsyncFunction("getInstalledApps") { ManagementSecurity.authorized { catalog().apps() } }
    AsyncFunction("getProtectedApps") { ManagementSecurity.authorized { ProtectionStore(context).read().toList() } }
    AsyncFunction("setAppProtected") { packageName: String, enabled: Boolean -> ManagementSecurity.authorized {
      require(PackageNames.valid(packageName)) { "Invalid package name" }
      if (enabled) {
        require(catalog().canProtect(packageName)) { "This app is reserved for system recovery or is not launchable" }
        require(context.getSystemService(KeyguardManager::class.java).isDeviceSecure) { "Set an Android screen lock before protecting apps" }
      }
      ProtectionStore(context).set(packageName, enabled).toList().also { RuntimeEvents.changed() }
    }
    }
    AsyncFunction("getProtectionStatus") { status() }
    AsyncFunction("openAccessibilitySettings") { ManagementSecurity.authorized {
      launchSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    } }.runOnQueue(Queues.MAIN)
    AsyncFunction("openSecuritySettings") { ManagementSecurity.authorized {
      launchSettings(Intent(Settings.ACTION_SECURITY_SETTINGS))
    } }.runOnQueue(Queues.MAIN)
    AsyncFunction("openAppSettings") { ManagementSecurity.authorized {
      launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    } }.runOnQueue(Queues.MAIN)
    AsyncFunction("getTheme") {
      val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
      fun color(name: String, fallback: String): String {
        val id = context.resources.getIdentifier(name, "color", "android")
        return if (android.os.Build.VERSION.SDK_INT >= 31 && id != 0) String.format(Locale.ROOT, "#%06X", context.getColor(id) and 0xFFFFFF) else fallback
      }
      mapOf("background" to color(if (dark) "system_neutral1_900" else "system_neutral1_10", if (dark) "#141318" else "#FFFBFE"),
        "surface" to color(if (dark) "system_neutral1_800" else "system_neutral1_50", if (dark) "#25232A" else "#F3EDF7"),
        "text" to color(if (dark) "system_neutral1_50" else "system_neutral1_900", if (dark) "#E6E1E5" else "#1D1B20"),
        "muted" to color(if (dark) "system_neutral2_200" else "system_neutral2_600", if (dark) "#CAC4D0" else "#625B71"),
        "primary" to color(if (dark) "system_accent1_200" else "system_accent1_600", if (dark) "#D0BCFF" else "#6750A4"),
        "outline" to color(if (dark) "system_neutral2_700" else "system_neutral2_200", if (dark) "#49454F" else "#CAC4D0"))
    }
  }
  private fun launchSettings(intent: Intent) {
    ManagementSecurity.session.settings()
    try {
      val activity = checkNotNull(appContext.currentActivity) { "Open AppLock to manage Android permissions" }
      // A matching Android activity result is required to preserve a settings excursion.
      // Launch in this task: simply resurfacing AppLock from another task cannot finish it.
      @Suppress("DEPRECATION")
      activity.startActivityForResult(intent, SETTINGS_REQUEST)
    }
    catch (error: RuntimeException) { ManagementSecurity.session.returned(); throw error }
  }
  private fun stopObserving() {
    RuntimeEvents.remove(notify)
    observer?.let { observedContext?.contentResolver?.unregisterContentObserver(it) }
    observer = null; observedContext = null
  }
}
