package expo.modules.applock

import java.util.UUID

/** No Android dependencies. Called only on the service's main thread. No durable grants. */
class LockEngine(private val now: () -> Long, private val newId: () -> String = { UUID.randomUUID().toString() }) {
  enum class State { UNLOCKED, LOCK_REQUIRED, PROTECTION_VISIBLE, AUTHENTICATING, AUTHENTICATED, CANCELLED, ERROR }
  data class Request(val id: String, val packageName: String)
  var state = State.UNLOCKED; private set
  var request: Request? = null; private set
  var foreground: String? = null; private set
  var expiresAt = 0L; private set
  private var protected = emptySet<String>()
  private data class Grant(var firstExit: Long? = null)
  private val grants = mutableMapOf<String, Grant>()
  private var grace = GracePolicy()

  fun configureGrace(policy: GracePolicy) {
    if (grace != policy) { reset(); grace = policy }
  }

  fun configure(packages: Set<String>) {
    if (protected != packages) reset()
    protected = packages.toSet()
  }
  fun enter(packageName: String): Request? {
    if (foreground != packageName) {
      foreground?.let { pkg ->
        grants[pkg]?.let { grant ->
          if (!grace.enabled) grants.remove(pkg)
          else if (grant.firstExit == null) grant.firstExit = now()
        }
      }
      clearRequest()
      foreground = packageName
    }
    if (packageName !in protected) return null
    // Expiry is checked only on re-entry. Never interrupt continuous foreground use.
    if (state == State.AUTHENTICATED) return null
    val grant = grants[packageName]
    if (grant != null && (grant.firstExit == null || (grace.enabled && now() - grant.firstExit!! in 0 until grace.durationMs))) {
      state = State.AUTHENTICATED
      expiresAt = grant.firstExit?.plus(grace.durationMs) ?: 0L
      return null
    }
    grants.remove(packageName)
    if (request != null) return null // Duplicates and cancellation never auto-retry.
    request = Request(newId(), packageName)
    state = State.LOCK_REQUIRED
    return request
  }
  fun visible(id: String): Boolean = transition(id, setOf(State.LOCK_REQUIRED), State.PROTECTION_VISIBLE)
  fun authenticate(id: String): Boolean = transition(id, setOf(State.PROTECTION_VISIBLE), State.AUTHENTICATING)
  fun success(id: String): Boolean {
    if (!matches(id) || state != State.AUTHENTICATING) return false
    state = State.AUTHENTICATED
    grants[foreground!!] = Grant()
    expiresAt = 0L
    return true
  }
  fun failure(id: String): Boolean = matches(id) && state == State.AUTHENTICATING
  fun cancel(id: String): Boolean = transition(id, setOf(State.LOCK_REQUIRED, State.PROTECTION_VISIBLE, State.AUTHENTICATING, State.ERROR), State.CANCELLED)
  fun error(id: String): Boolean = transition(id, setOf(State.LOCK_REQUIRED, State.PROTECTION_VISIBLE, State.AUTHENTICATING), State.ERROR)
  fun retry(): Request? {
    if (state !in setOf(State.CANCELLED, State.ERROR, State.PROTECTION_VISIBLE)) return null
    val pkg = foreground ?: return null
    clearRequest()
    return enter(pkg)
  }
  fun reset() { grants.clear(); clearRequest(); foreground = null }
  fun matches(id: String): Boolean = request?.id == id && request?.packageName == foreground
  fun blocked(): Boolean = request != null && state != State.AUTHENTICATED
  private fun clearRequest() { request = null; expiresAt = 0; state = State.UNLOCKED }
  private fun transition(id: String, from: Set<State>, to: State): Boolean {
    if (!matches(id) || state !in from) return false
    state = to
    return true
  }
}

object PackageNames {
  private val pattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
  fun valid(value: String) = value.length <= 255 && pattern.matches(value)
}
