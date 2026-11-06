package expo.modules.applock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.inputmethod.InputMethodManager
import java.io.File
import java.io.FileOutputStream
import java.text.Collator

class AppCatalog(private val context: Context) {
  private val pm = context.packageManager
  @Volatile private var cache: List<Map<String, Any>>? = null
  @Volatile private var launchable: Set<String> = emptySet()
  @Volatile private var homes: Set<String> = emptySet()
  @Volatile private var keyboards: Set<String> = emptySet()
  @Volatile private var critical: Set<String> = emptySet()

  @Synchronized fun apps(): List<Map<String, Any>> {
    cache?.let { return it }
    refreshClassification()
    val activities = PackageMetadata.launchers(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    val icons = File(context.cacheDir, "app-icons").apply { mkdirs() }
    val result = activities.distinctBy { it.activityInfo.packageName }.mapNotNull { info ->
      val pkg = info.activityInfo.packageName
      if (pkg == context.packageName) return@mapNotNull null
      try {
        val label = info.loadLabel(pm).toString()
        val appInfo = PackageMetadata.packageInfo(pm, pkg)
        val file = File(icons, "$pkg-${appInfo.lastUpdateTime}.png")
        if (!file.exists()) {
          icons.listFiles { _, name -> name.startsWith("$pkg-") }?.forEach { it.delete() }
          val drawable = info.loadIcon(pm)
          val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
          drawable.setBounds(0, 0, 96, 96)
          drawable.draw(Canvas(bitmap))
          FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
          bitmap.recycle()
        }
        mapOf<String, Any>("packageName" to pkg, "label" to label, "iconUri" to Uri.fromFile(file).toString(),
          "canProtect" to (pkg !in critical), "exclusionReason" to if (pkg in critical) "System recovery, phone, launcher or keyboard" else "")
      } catch (_: PackageManager.NameNotFoundException) { null }
    }.sortedWith { a, b -> collator.compare(a["label"] as String, b["label"] as String) }
    cache = result
    return result
  }

  @Synchronized fun refreshClassification() {
    val activities = PackageMetadata.launchers(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
    launchable = activities.map { it.activityInfo.packageName }.toSet()
    homes = PackageMetadata.launchers(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
      .map { it.activityInfo.packageName }.toSet()
    keyboards = context.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
    val dialers = PackageMetadata.launchers(pm, Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))).map { it.activityInfo.packageName }
    critical = (homes + keyboards + dialers + PlatformPolicy.recoveryPackages + setOf(context.packageName, "com.android.systemui", "com.google.android.permissioncontroller", "com.android.permissioncontroller"))
  }
  @Synchronized fun invalidate() { cache = null }
  fun isHome(pkg: String) = pkg in homes
  fun ignoreEvent(pkg: String, className: String) = PlatformPolicy.ignoreEvent(pkg, className, context.packageName, keyboards)
  fun isLaunchable(pkg: String) = pkg in launchable
  fun canProtect(pkg: String): Boolean { apps(); return pkg in launchable && pkg !in critical }
}
