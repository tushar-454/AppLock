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
import expo.modules.kotlin.modules.ModuleDefinition
import java.util.Locale

class AppLockModule : Module() {
  private val context get() = requireNotNull(appContext.reactContext) { "Android context is unavailable" }
  private var catalog: AppCatalog? = null
  private var observer: ContentObserver? = null
  private var observedContext: Context? = null
  private var revision = -1L
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
    AsyncFunction("getInstalledApps") { catalog().apps() }
    AsyncFunction("getProtectedApps") { ProtectionStore(context).read().toList() }
    AsyncFunction("setAppProtected") { packageName: String, enabled: Boolean ->
      require(PackageNames.valid(packageName)) { "Invalid package name" }
      if (enabled) {
        require(catalog().canProtect(packageName)) { "This app is reserved for system recovery or is not launchable" }
        require(context.getSystemService(KeyguardManager::class.java).isDeviceSecure) { "Set an Android screen lock before protecting apps" }
      }
      ProtectionStore(context).set(packageName, enabled).toList().also { RuntimeEvents.changed() }
    }
    AsyncFunction("getProtectionStatus") { status() }
    AsyncFunction("openAccessibilitySettings") {
      context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    AsyncFunction("openSecuritySettings") { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    AsyncFunction("openAppSettings") {
      context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
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
  private fun stopObserving() {
    RuntimeEvents.remove(notify)
    observer?.let { observedContext?.contentResolver?.unregisterContentObserver(it) }
    observer = null; observedContext = null
  }
}
