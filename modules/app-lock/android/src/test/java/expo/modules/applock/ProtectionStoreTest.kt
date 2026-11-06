package expo.modules.applock

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ProtectionStoreTest {
  private lateinit var context: Context
  @Before fun setup() {
    context = RuntimeEnvironment.getApplication()
    context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE).edit().clear().commit()
  }
  @Test fun configurationSurvivesStoreRecreation() { ProtectionStore(context).set("com.example.app", true); assertEquals(setOf("com.example.app"), ProtectionStore(context).read()) }
  @Test fun removalPersists() { val s = ProtectionStore(context); s.set("com.example.app", true); s.set("com.example.app", false); assertTrue(ProtectionStore(context).read().isEmpty()) }
  @Test(expected = IllegalArgumentException::class) fun rejectsInvalidPackage() { ProtectionStore(context).set("invalid", true) }
  @Test fun corruptValueFailsSafely() {
    context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE).edit().putInt("packages", 7).commit()
    assertTrue(ProtectionStore(context).read().isEmpty())
    assertFalse(ProtectionStore(context).healthy())
    assertEquals(setOf("com.example.app"), ProtectionStore(context).set("com.example.app", true))
  }
  @Test fun invalidRecordsAreFiltered() {
    context.createDeviceProtectedStorageContext().getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE).edit().putStringSet("packages", setOf("bad", "com.example.app")).commit()
    assertEquals(setOf("com.example.app"), ProtectionStore(context).read())
  }
  @Test fun concurrentStoreInstancesRetainEveryPackage() {
    val threads = (1..12).map { Thread { ProtectionStore(context).set("com.example.app$it", true) }.apply { start() } }
    threads.forEach { it.join() }
    assertEquals(12, ProtectionStore(context).read().size)
  }
  @Test fun usesDeviceProtectedStorageOnly() {
    ProtectionStore(context).set("com.example.app", true)
    assertFalse(context.getSharedPreferences("app_lock_protection", Context.MODE_PRIVATE).contains("packages"))
  }
  @Test fun concurrentWritesRetainEveryPackage() {
    val store = ProtectionStore(context)
    val threads = (1..12).map { Thread { store.set("com.example.app$it", true) }.apply { start() } }
    threads.forEach { it.join() }
    assertEquals(12, ProtectionStore(context).read().size)
  }
}
