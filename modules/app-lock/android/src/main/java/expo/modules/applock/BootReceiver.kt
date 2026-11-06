package expo.modules.applock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Android binds enabled accessibility services. Never launch a background FGS. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in setOf(Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
    ProtectionStore(context).read() // Available before the first credential unlock.
    RuntimeEvents.service?.resetSessions()
    RuntimeEvents.changed()
  }
}

class PackageReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action !in setOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_CHANGED)) return
    val pkg = intent.data?.schemeSpecificPart ?: return
    if (intent.action == Intent.ACTION_PACKAGE_REMOVED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && PackageNames.valid(pkg)) {
      ProtectionStore(context).set(pkg, false)
    }
    RuntimeEvents.catalogRevision++
    RuntimeEvents.service?.packagesChanged()
    RuntimeEvents.changed()
  }
}
