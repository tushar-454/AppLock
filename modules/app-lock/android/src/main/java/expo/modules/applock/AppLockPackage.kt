package expo.modules.applock

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import expo.modules.core.interfaces.Package
import expo.modules.core.interfaces.ReactActivityLifecycleListener

/** Runs before JS. FLAG_SECURE also prevents management thumbnails/screenshots. */
class AppLockPackage : Package {
  override fun createReactActivityLifecycleListeners(activityContext: Context): List<ReactActivityLifecycleListener> = listOf(object : ReactActivityLifecycleListener {
    private var cover: View? = null
    private var host: Activity? = null
    private var screenReceiver: android.content.BroadcastReceiver? = null
    private var callbacks: Application.ActivityLifecycleCallbacks? = null
    override fun onCreate(activity: Activity, savedInstanceState: Bundle?) {
      host = activity
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
      showCover(activity)
      ManagementSecurity.invalidate()
      screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { ManagementSecurity.invalidate() }
      }.also { androidx.core.content.ContextCompat.registerReceiver(activity, it,
        android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
      callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStopped(a: Activity) { if (a === activity) synchronized(ManagementSecurity) { ManagementSecurity.session.leave() } }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityResumed(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
      }.also { activity.application.registerActivityLifecycleCallbacks(it) }
    }
    override fun onResume(activity: Activity) {
      ManagementSecurity.resume(activity)
      // JS removes this privacy cover only after committing its authentication gate.
    }
    override fun onPause(activity: Activity) {
      synchronized(ManagementSecurity) { ManagementSecurity.session.pause() }
      showCover(activity)
      RuntimeEvents.changed()
    }
    override fun onUserLeaveHint(activity: Activity) = synchronized(ManagementSecurity) { ManagementSecurity.session.leave(); RuntimeEvents.changed() }
    private fun showCover(activity: Activity) {
      val root = activity.window.decorView as android.view.ViewGroup
      if (cover?.parent == null) cover = FrameLayout(activity).apply {
        setBackgroundColor(activity.getColor(android.R.color.background_dark))
        isClickable = true; isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        tag = "applock-privacy-cover"
        root.addView(this, android.view.ViewGroup.LayoutParams(-1, -1))
      }
    }
    override fun onNewIntent(intent: Intent): Boolean {
      host?.let { showCover(it) }
      ManagementSecurity.invalidate(); return false
    }
    override fun onDestroy(activity: Activity) {
      screenReceiver?.let { activity.unregisterReceiver(it) }
      callbacks?.let { activity.application.unregisterActivityLifecycleCallbacks(it) }
      ManagementSecurity.invalidate(); cover = null; host = null
    }
  })
}
