import { requireOptionalNativeModule, type EventSubscription } from 'expo';

export interface InstalledApp {
  packageName: string;
  label: string;
  iconUri: string;
  canProtect: boolean;
  exclusionReason: string;
}
export interface ProtectionStatus {
  configurationHealthy: boolean;
  accessibilityEnabled: boolean;
  serviceConnected: boolean;
  deviceSecure: boolean;
  canAuthenticate: boolean;
  authenticationCode: number;
  status: string;
  authenticationMethod: string;
}
export interface Palette {
  background: string;
  surface: string;
  text: string;
  muted: string;
  primary: string;
  outline: string;
}
export interface ManagementState {
  configured: boolean;
  authenticated: boolean;
  retryAfterMs: number;
  generation: number;
}
export interface GraceSettings { enabled: boolean; durationMs: number }
interface AppLockNative {
  getManagementState(): Promise<ManagementState>;
  createPin(pin: string, confirmation: string): Promise<ManagementState>;
  verifyPin(pin: string): Promise<ManagementState>;
  changePin(current: string, next: string, confirmation: string): Promise<void>;
  lockManagement(): Promise<void>;
  acknowledgeManagementScreen(generation: number): Promise<void>;
  getGraceSettings(): Promise<GraceSettings>;
  setGraceSettings(enabled: boolean, durationMs: number): Promise<void>;
  addListener(event: 'onStatusChanged', listener: () => void): EventSubscription;
  getInstalledApps(): Promise<InstalledApp[]>;
  getProtectedApps(): Promise<string[]>;
  setAppProtected(packageName: string, enabled: boolean): Promise<string[]>;
  getProtectionStatus(): Promise<ProtectionStatus>;
  getTheme(): Promise<Palette>;
  openAccessibilitySettings(): Promise<void>;
  openSecuritySettings(): Promise<void>;
  openAppSettings(): Promise<void>;
}
export const AppLock = requireOptionalNativeModule<AppLockNative>('AppLock');
