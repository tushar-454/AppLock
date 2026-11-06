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
interface AppLockNative {
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
