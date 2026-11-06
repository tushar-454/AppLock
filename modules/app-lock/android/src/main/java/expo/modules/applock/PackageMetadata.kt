package expo.modules.applock

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/** Typed flags on current Android, with the public legacy overloads for API 30–32. */
object PackageMetadata {
  @Suppress("DEPRECATION")
  fun launchers(pm: PackageManager, intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    else pm.queryIntentActivities(intent, 0)
  @Suppress("DEPRECATION")
  fun packageInfo(pm: PackageManager, pkg: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
    else pm.getPackageInfo(pkg, 0)
  @Suppress("DEPRECATION")
  fun applicationInfo(pm: PackageManager, pkg: String): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= 33) pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
    else pm.getApplicationInfo(pkg, 0)
}
