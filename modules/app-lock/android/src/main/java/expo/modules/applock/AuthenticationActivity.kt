package expo.modules.applock

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class AuthenticationActivity : FragmentActivity() {
  companion object {
    const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    var currentRequest: String? = null; private set
  }
  private lateinit var id: String
  private lateinit var pkg: String
  private var prompt: BiometricPrompt? = null
  private var credentialOnly = false
  private val service get() = RuntimeEvents.service
  private val stateListener: () -> Unit = {
    runOnUiThread {
      if (::id.isInitialized && (service?.engine?.matches(id) != true || service?.engine?.blocked() != true)) {
        prompt?.cancelAuthentication()
        finishAndRemoveTask()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    id = intent.getStringExtra("requestId") ?: run { finishAndRemoveTask(); return }
    pkg = intent.getStringExtra("packageName") ?: run { finishAndRemoveTask(); return }
    val engine = service?.engine
    if (engine?.matches(id) != true || engine.request?.packageName != pkg || !engine.blocked()) { finishAndRemoveTask(); return }
    RuntimeEvents.add(stateListener)
    setContentView(LockSurface.create(this, pkg, "Only Android verifies your fingerprint or screen lock.", { beginAuthentication() }, { cancel() }))
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() { cancel() }
    })
    // Reattach callbacks on recreation; AndroidX retains the running prompt across rotation.
    prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        if (service?.authenticated(id) == true) finishAndRemoveTask()
      }
      override fun onAuthenticationFailed() { service?.engine?.failure(id) /* No grant; Android keeps its prompt. */ }
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        if (service?.engine?.matches(id) != true) return
        when (errorCode) {
          BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> cancel()
          BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
            if (!credentialOnly && BiometricManager.from(this@AuthenticationActivity).canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
              credentialOnly = true
              showPrompt(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
              service?.authenticationError(id, errString.toString())
              finishAndRemoveTask()
            }
          }
          else -> {
            service?.authenticationError(id, errString.toString())
            finishAndRemoveTask()
          }
        }
      }
    })
    if (savedInstanceState == null) window.decorView.post { beginAuthentication() }
  }

  private fun beginAuthentication() {
    if (service?.engine?.matches(id) != true) { finishAndRemoveTask(); return }
    if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { cancel(); return }
    val manager = BiometricManager.from(this)
    val choice = PlatformPolicy.authentication(manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS,
      manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS,
      getSystemService(KeyguardManager::class.java).isDeviceSecure)
    if (choice == PlatformPolicy.Authentication.UNAVAILABLE) {
      service?.authenticationError(id, "Android authentication unavailable. Set a device screen lock in Settings, then try again.")
      finishAndRemoveTask(); return
    }
    if (service?.startPrompt(id) != true) return
    credentialOnly = choice == PlatformPolicy.Authentication.CREDENTIAL
    showPrompt(if (credentialOnly) BiometricManager.Authenticators.DEVICE_CREDENTIAL else AUTHENTICATORS)
  }
  private fun showPrompt(authenticators: Int) {
    try {
      prompt?.authenticate(BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock protected app")
        .setSubtitle("App Lock • Use your fingerprint or screen lock")
        .setAllowedAuthenticators(authenticators)
        .setConfirmationRequired(true)
        .build())
    } catch (_: RuntimeException) {
      service?.authenticationError(id, "Android could not start authentication. Try again or return home.")
      finishAndRemoveTask()
    }
  }
  override fun onResume() {
    super.onResume()
    if (::id.isInitialized && service?.engine?.matches(id) == true) {
      currentRequest = id
      service?.hostVisible(id)
    } else finishAndRemoveTask()
  }
  override fun onStop() {
    if (::id.isInitialized && currentRequest == id) currentRequest = null
    super.onStop()
  }
  private fun cancel() {
    service?.cancelAndHome(id)
    prompt?.cancelAuthentication()
    finishAndRemoveTask()
  }
  override fun onDestroy() {
    RuntimeEvents.remove(stateListener)
    if (::id.isInitialized) {
      if (currentRequest == id) currentRequest = null
      if (!isChangingConfigurations) service?.hostLost(id)
    }
    super.onDestroy()
  }
}
