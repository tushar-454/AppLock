package expo.modules.applock

/** Pure policy so missing capabilities and permission transitions are testable. */
object PlatformPolicy {
  val recoveryPackages = setOf("com.android.settings", "com.android.phone", "com.android.server.telecom", "com.android.emergency", "com.google.android.apps.safetyhub", "com.android.packageinstaller", "com.google.android.packageinstaller")
  enum class Authentication { STRONG_OR_CREDENTIAL, CREDENTIAL, UNAVAILABLE }
  fun authentication(combinedAvailable: Boolean, credentialAvailable: Boolean, secure: Boolean): Authentication = when {
    !secure -> Authentication.UNAVAILABLE
    combinedAvailable -> Authentication.STRONG_OR_CREDENTIAL
    credentialAvailable -> Authentication.CREDENTIAL
    else -> Authentication.UNAVAILABLE
  }
  fun status(enabled: Boolean, connected: Boolean, canAuthenticate: Boolean, healthy: Boolean = true): String = when {
    !healthy -> "Protection configuration damaged"
    !enabled -> "Accessibility permission required"
    !connected -> "Service unavailable"
    !canAuthenticate -> "Setup incomplete"
    else -> "Protection active"
  }
  fun ignoreEvent(pkg: String, className: String, ownPackage: String, keyboards: Set<String>): Boolean = when {
    pkg == ownPackage -> !className.endsWith("MainActivity")
    pkg == "android" || pkg == "com.android.systemui" || pkg in keyboards -> true
    pkg == "com.google.android.permissioncontroller" || pkg == "com.android.permissioncontroller" -> true
    else -> false
  }
}
