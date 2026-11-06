package expo.modules.applock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

class AppLockService : AccessibilityService() {
  val engine = LockEngine(SystemClock::elapsedRealtime)
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var store: ProtectionStore
  private lateinit var catalog: AppCatalog
  private lateinit var grace: GraceSettings
  private var overlay: View? = null
  private var overlayId: String? = null
  private val worker = Executors.newSingleThreadExecutor()
  private var connected = false
  private val configListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
    handler.post {
      engine.configure(store.read())
      engine.configureGrace(grace.read())
      if (!engine.blocked()) removeOverlay()
      RuntimeEvents.changed()
    }
  }
  private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        Intent.ACTION_SCREEN_OFF -> { ManagementSecurity.invalidate(); resetSessions() }
        Intent.ACTION_USER_UNLOCKED -> { resetSessions(); packagesChanged() }
      }
    }
  }

  override fun onServiceConnected() {
    super.onServiceConnected()
    connected = true
    store = ProtectionStore(this)
    catalog = AppCatalog(this)
    grace = GraceSettings(this)
    engine.reset()
    engine.configure(store.read())
    engine.configureGrace(grace.read())
    grace.listen(configListener)
    store.listen(configListener)
    ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
      addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_UNLOCKED)
    }, ContextCompat.RECEIVER_NOT_EXPORTED)
    RuntimeEvents.service = this
    if (getSystemService(UserManager::class.java).isUserUnlocked) packagesChanged()
    RuntimeEvents.changed()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (!connected || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { resetSessions(); return }
    val pkg = event.packageName?.toString() ?: return
    // Only metadata is accessed. No event text, source nodes, or window contents.
    if (pkg in PlatformPolicy.recoveryPackages) {
      // Emergency/recovery UI must remain reachable even if it has no launcher activity.
      engine.reset()
      removeOverlay()
      RuntimeEvents.changed()
      return
    }
    if (catalog.ignoreEvent(pkg, event.className?.toString().orEmpty())) {
      return
    }
    if (pkg == packageName) { detect(pkg); return }
    if (PackageNames.valid(pkg)) detect(pkg)
  }

  private fun detect(pkg: String) {
    if (pkg != packageName) ManagementSecurity.externalApp()
    val previous = engine.request?.id
    syncConfiguration()
    val request = engine.enter(pkg)
    if (previous != engine.request?.id) {
      removeOverlay()
      RuntimeEvents.changed() // Dismiss an obsolete prompt before starting a new one.
    }
    if (request != null) protect(request)
    else if (engine.blocked() && overlay == null && AuthenticationActivity.currentRequest != engine.request?.id) {
      engine.request?.let { showOverlay(it, "Authentication is required to continue") }
    }
  }

  private fun protect(request: LockEngine.Request) {
    if (showOverlay(request, "Use your fingerprint or device screen lock")) engine.visible(request.id)
    RuntimeEvents.changed()
    launchAuthentication(request)
  }

  private fun launchAuthentication(request: LockEngine.Request) {
    if (!engine.matches(request.id)) return
    try {
      startActivity(Intent(this, AuthenticationActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        putExtra("requestId", request.id); putExtra("packageName", request.packageName)
      })
      // If Android suppresses a background activity launch, keep the overlay and its user-operated Unlock button.
    } catch (_: RuntimeException) {
      engine.error(request.id)
      showOverlay(request, "Tap Unlock to authenticate, or return home")
      RuntimeEvents.changed()
    }
  }

  fun hostVisible(id: String) {
    if (!engine.matches(id)) return
    // The activity can supply the opaque cover even if WindowManager rejected the overlay.
    if (engine.state == LockEngine.State.LOCK_REQUIRED) engine.visible(id)
    removeOverlay()
  }
  private fun syncConfiguration() {
    engine.configure(store.read())
    engine.configureGrace(grace.read())
  }
  fun startPrompt(id: String): Boolean { syncConfiguration(); return engine.authenticate(id) }
  fun authenticated(id: String): Boolean {
    syncConfiguration()
    if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { resetSessions(); return false }
    if (!engine.success(id)) return false
    removeOverlay()
    RuntimeEvents.changed()
    return true
  }
  fun authenticationError(id: String, message: String) {
    if (!engine.error(id)) return
    engine.request?.let { showOverlay(it, message) }
    RuntimeEvents.changed()
  }
  fun cancelAndHome(id: String) {
    if (!engine.cancel(id)) return
    engine.request?.let { showOverlay(it, "App remains locked. Return home or try again.") }
    RuntimeEvents.changed()
    goHome()
  }
  fun hostLost(id: String) {
    if (!engine.matches(id) || !engine.blocked()) return
    engine.error(id)
    engine.request?.let { showOverlay(it, "Authentication interrupted. Tap Unlock to try again.") }
    RuntimeEvents.changed()
  }
  private fun goHome() { performGlobalAction(GLOBAL_ACTION_HOME) }
  private fun retry() {
    if (!engine.blocked()) return
    engine.retry()?.let { protect(it) }
  }
  private fun showOverlay(request: LockEngine.Request, message: String): Boolean {
    if (overlay != null && overlayId == request.id) return true
    removeOverlay()
    val view = LockSurface.create(this, request.packageName, message, { retry() }, { cancelAndHome(request.id) })
    val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SECURE
    val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.OPAQUE)
    params.setTitle("App Lock protection")
    return try {
      getSystemService(WindowManager::class.java).addView(view, params)
      overlay = view; overlayId = request.id
      true
    } catch (_: RuntimeException) {
      false // The opaque authentication activity is still the next cover attempt.
    }
  }
  private fun removeOverlay() {
    overlay?.let { try { getSystemService(WindowManager::class.java).removeViewImmediate(it) } catch (_: RuntimeException) {} }
    overlay = null; overlayId = null
  }
  fun resetSessions() {
    engine.reset()
    removeOverlay()
    RuntimeEvents.changed()
  }
  fun packagesChanged() {
    if (!connected) return
    worker.execute { catalog.invalidate(); catalog.refreshClassification() }
  }
  override fun onInterrupt() { resetSessions() }
  override fun onUnbind(intent: Intent?): Boolean { shutdown(); return super.onUnbind(intent) }
  override fun onDestroy() { shutdown(); worker.shutdownNow(); super.onDestroy() }
  private fun shutdown() {
    if (!connected) return
    connected = false
    store.unlisten(configListener)
    grace.unlisten(configListener)
    unregisterReceiver(screenReceiver)
    resetSessions()
    if (RuntimeEvents.service === this) RuntimeEvents.service = null
    RuntimeEvents.changed()
  }
}
