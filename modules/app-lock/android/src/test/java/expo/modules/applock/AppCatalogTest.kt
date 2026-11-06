package expo.modules.applock

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AppCatalogTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private fun install(pkg: String, label: String, home: Boolean = false) {
    val app = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label; icon = android.R.drawable.sym_def_app_icon }
    shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkg; applicationInfo = app })
    val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.MainActivity"; applicationInfo = app; nonLocalizedLabel = label; icon = android.R.drawable.sym_def_app_icon } }
    shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), info)
    if (home) shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), info)
  }
  @Before fun setup() { context.cacheDir.resolve("app-icons").deleteRecursively() }
  @Test fun discoveryUsesLabelsIconsAndSortedLaunchers() {
    install("com.example.zulu", "Zulu"); install("com.example.alpha", "Alpha"); install("com.example.unicode", "বাংলা")
    val apps = AppCatalog(context).apps()
    assertEquals("Alpha", apps.first()["label"])
    assertTrue(apps.any { it["label"] == "বাংলা" })
    assertTrue(apps.all { (it["iconUri"] as String).startsWith("file:") })
  }
  @Test fun recoveryAppsCannotBeProtectedAndSelfIsExcluded() {
    install("com.android.settings", "Settings"); install("com.example.launcher", "Home", home = true)
    install(context.packageName, "App Lock"); install("com.example.safe", "Test app")
    val catalog = AppCatalog(context)
    assertFalse(catalog.canProtect("com.android.settings")); assertFalse(catalog.canProtect("com.example.launcher"))
    assertFalse(catalog.canProtect(context.packageName)); assertTrue(catalog.canProtect("com.example.safe"))
    assertFalse(catalog.apps().any { it["packageName"] == context.packageName })
  }
  @Test fun cacheRefreshesOnlyWhenInvalidated() {
    install("com.example.alpha", "Alpha")
    val catalog = AppCatalog(context); val original = catalog.apps()
    install("com.example.beta", "Beta")
    assertSame(original, catalog.apps())
    catalog.invalidate()
    assertTrue(catalog.apps().any { it["packageName"] == "com.example.beta" })
  }
  @Test fun nonLaunchablePackagesCannotBeProtected() { assertFalse(AppCatalog(context).canProtect("com.example.internalservice")) }
}
