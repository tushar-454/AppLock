package expo.modules.applock

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ReceiversTest {
  private lateinit var context: Context
  @Before fun setup() {
    context = RuntimeEnvironment.getApplication()
    RuntimeEvents.service = null
    context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE).edit().clear().commit()
  }
  @Test fun lockedBootReadsConfigurationWithoutRemovingIt() {
    ProtectionStore(context).set("com.example.app", true)
    BootReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
    assertTrue(ProtectionStore(context).read().contains("com.example.app"))
    assertNull(RuntimeEvents.service) // The receiver does not illegally start a service.
  }
  @Test fun bootAndUserUnlockKeepConfiguration() {
    ProtectionStore(context).set("com.example.app", true)
    for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_USER_UNLOCKED, Intent.ACTION_MY_PACKAGE_REPLACED)) BootReceiver().onReceive(context, Intent(action))
    assertEquals(setOf("com.example.app"), ProtectionStore(context).read())
  }
  @Test fun uninstallRemovesStaleConfiguration() {
    ProtectionStore(context).set("com.example.app", true)
    PackageReceiver().onReceive(context, Intent(Intent.ACTION_PACKAGE_REMOVED, Uri.parse("package:com.example.app")))
    assertTrue(ProtectionStore(context).read().isEmpty())
  }
  @Test fun packageUpgradeRetainsProtection() {
    ProtectionStore(context).set("com.example.app", true)
    PackageReceiver().onReceive(context, Intent(Intent.ACTION_PACKAGE_REMOVED, Uri.parse("package:com.example.app")).putExtra(Intent.EXTRA_REPLACING, true))
    assertEquals(setOf("com.example.app"), ProtectionStore(context).read())
  }
  @Test fun installAndChangeInvalidateCatalog() {
    val before = RuntimeEvents.catalogRevision
    PackageReceiver().onReceive(context, Intent(Intent.ACTION_PACKAGE_ADDED, Uri.parse("package:com.example.new")))
    PackageReceiver().onReceive(context, Intent(Intent.ACTION_PACKAGE_CHANGED, Uri.parse("package:com.example.new")))
    assertEquals(before + 2, RuntimeEvents.catalogRevision)
  }
  @Test fun unknownBroadcastHasNoEffect() {
    val before = RuntimeEvents.catalogRevision
    PackageReceiver().onReceive(context, Intent("invalid", Uri.parse("package:com.example.app")))
    assertEquals(before, RuntimeEvents.catalogRevision)
  }
}
