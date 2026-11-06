package expo.modules.applock

import org.junit.Assert.*
import org.junit.Test

class PlatformPolicyTest {
  @Test fun emergencyRecoveryDoesNotDependOnLauncherVisibility() {
    assertTrue("com.android.emergency" in PlatformPolicy.recoveryPackages)
    assertTrue("com.android.server.telecom" in PlatformPolicy.recoveryPackages)
    assertTrue("com.android.settings" in PlatformPolicy.recoveryPackages)
  }
  @Test fun credentialsWorkWithoutBiometricHardware() { assertEquals(PlatformPolicy.Authentication.CREDENTIAL, PlatformPolicy.authentication(false, true, true)) }
  @Test fun combinedAuthenticationPreferred() { assertEquals(PlatformPolicy.Authentication.STRONG_OR_CREDENTIAL, PlatformPolicy.authentication(true, true, true)) }
  @Test fun missingScreenLockIsNotAuthentication() { assertEquals(PlatformPolicy.Authentication.UNAVAILABLE, PlatformPolicy.authentication(true, true, false)) }
  @Test fun unavailableAuthenticationDoesNotFallbackToCustomPin() { assertEquals(PlatformPolicy.Authentication.UNAVAILABLE, PlatformPolicy.authentication(false, false, true)) }
  @Test fun permissionRevocationIsInactiveEvenIfServiceReferenceRemains() { assertEquals("Accessibility permission required", PlatformPolicy.status(false, true, true)) }
  @Test fun enabledButUnboundIsUnavailable() { assertEquals("Service unavailable", PlatformPolicy.status(true, false, true)) }
  @Test fun missingAuthenticationIsIncomplete() { assertEquals("Setup incomplete", PlatformPolicy.status(true, true, false)) }
  @Test fun readyServiceIsActive() { assertEquals("Protection active", PlatformPolicy.status(true, true, true)) }
  @Test fun corruptConfigurationIsVisible() { assertEquals("Protection configuration damaged", PlatformPolicy.status(true, true, true, false)) }
  @Test fun ownAuthenticationAndOverlayEventsAreIgnored() {
    assertTrue(PlatformPolicy.ignoreEvent("com.tushar.applock", "expo.modules.applock.AuthenticationActivity", "com.tushar.applock", emptySet()))
    assertTrue(PlatformPolicy.ignoreEvent("com.tushar.applock", "android.widget.LinearLayout", "com.tushar.applock", emptySet()))
    assertFalse(PlatformPolicy.ignoreEvent("com.tushar.applock", "com.tushar.applock.MainActivity", "com.tushar.applock", emptySet()))
  }
  @Test fun temporarySystemWindowsDoNotEndSession() {
    for (pkg in listOf("android", "com.android.systemui", "com.google.android.permissioncontroller", "com.android.permissioncontroller", "com.example.keyboard")) {
      assertTrue(PlatformPolicy.ignoreEvent(pkg, "Dialog", "com.tushar.applock", setOf("com.example.keyboard")))
    }
  }
  @Test fun unrelatedAppAndLauncherAreNotIgnored() {
    for (pkg in listOf("com.google.android.apps.nexuslauncher", "com.example.free", "com.example.permissioncontroller")) {
      assertFalse(PlatformPolicy.ignoreEvent(pkg, "Activity", "com.tushar.applock", emptySet()))
    }
  }
}
