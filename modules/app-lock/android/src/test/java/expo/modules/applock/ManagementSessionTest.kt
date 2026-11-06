package expo.modules.applock

import org.junit.Assert.*
import org.junit.Test

class ManagementSessionTest {
  private fun denied(action: () -> Unit) { try { action(); fail("Expected native access denial") } catch (_: IllegalStateException) {} }
  private fun unlocked() = ManagementSession().apply { resume(); grant(generation) }
  @Test fun protectedOperationsRequireNativeAuthority() { val s = ManagementSession(); denied { s.requireAccess() }; s.resume(); denied { s.requireAccess() } }
  @Test fun correctVerificationCanGrantSession() { unlocked().requireAccess() }
  @Test fun genuineExitInvalidatesSession() { val s = unlocked(); s.leave(); denied { s.requireAccess() }; s.resume(); denied { s.requireAccess() } }
  @Test fun temporaryPauseDoesNotEraseAuthentication() { val s = unlocked(); s.pause(); denied { s.requireAccess() }; s.resume(); s.requireAccess() }
  @Test fun settingsTripPreservesOnlyExistingSession() { val s = unlocked(); s.settings(); s.leave(); s.pause(); denied { s.requireAccess() }; s.settingsCompleted(); s.resume(); s.returned(); s.requireAccess(); s.leave(); denied { s.requireAccess() } }
  @Test fun unauthenticatedSettingsCannotCreateExemption() { denied { ManagementSession().settings() } }
  @Test fun externalDeepLinkInvalidatesEvenDuringSettingsTrip() { val s = unlocked(); s.settings(); s.externalIntent(); s.resume(); denied { s.requireAccess() } }
  @Test fun processRestartHasNoManagementGrant() { unlocked(); val recreated = ManagementSession(); recreated.resume(); denied { recreated.requireAccess() } }
  @Test fun staleVerificationCannotGrantAfterLifecycleChange() { val s = ManagementSession(); s.resume(); val version = s.generation; s.invalidate(); denied { s.grant(version) } }
  @Test fun backgroundVerificationCannotGrant() { val s = ManagementSession(); s.resume(); val version = s.generation; s.pause(); denied { s.grant(version) } }
  @Test fun screenLockAndPinChangeRevokeAuthority() { val s = unlocked(); s.invalidate(); denied { s.requireAccess() } }
  @Test fun resumingWithoutSettingsCompletionCannotReuseExemption() {
    val s = unlocked(); s.settings(); s.leave(); s.pause(); s.resume(); denied { s.requireAccess() }
  }
  @Test fun staleOrUnsolicitedSettingsResultCannotCreateAuthority() {
    val s = unlocked(); s.settings(); s.externalIntent(); s.settingsCompleted(); s.resume(); denied { s.requireAccess() }
    val locked = ManagementSession(); locked.settingsCompleted(); locked.resume(); denied { locked.requireAccess() }
  }
}
