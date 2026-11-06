package expo.modules.applock

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

/** In-process status notification; never contains accessibility event contents. */
object RuntimeEvents {
  private val listeners = CopyOnWriteArraySet<() -> Unit>()
  @Volatile var catalogRevision = 0L
  @Volatile private var serviceReference = WeakReference<AppLockService>(null)
  var service: AppLockService?
    get() = serviceReference.get()
    set(value) { serviceReference = WeakReference(value) }
  fun add(listener: () -> Unit) { listeners.add(listener) }
  fun remove(listener: () -> Unit) { listeners.remove(listener) }
  fun changed() { listeners.forEach { it() } }
}
