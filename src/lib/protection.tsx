import { AppLock, type GraceSettings, type InstalledApp, type Palette, type ProtectionStatus } from '../../modules/app-lock';
import { createContext, useCallback, useContext, useEffect, useRef, useState, type PropsWithChildren } from 'react';
import { AppState, useColorScheme } from 'react-native';

const light: Palette = { background: '#FFFBFE', surface: '#F3EDF7', text: '#1D1B20', muted: '#625B71', primary: '#6750A4', outline: '#CAC4D0' };
const dark: Palette = { background: '#141318', surface: '#25232A', text: '#E6E1E5', muted: '#CAC4D0', primary: '#D0BCFF', outline: '#49454F' };
const unavailable: ProtectionStatus = { configurationHealthy: true, accessibilityEnabled: false, serviceConnected: false, deviceSecure: false, canAuthenticate: false, authenticationCode: -1, status: 'Native Android build required', authenticationMethod: 'Android authentication' };
function useProtectionState() {
  const scheme = useColorScheme();
  const [nativePalette, setPalette] = useState<Palette>();
  const palette = nativePalette ?? (scheme === 'dark' ? dark : light);
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [protectedApps, setProtectedApps] = useState<string[]>([]);
  const [status, setStatus] = useState(unavailable);
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(!!AppLock);
  const [grace, setGrace] = useState<GraceSettings>({ enabled: true, durationMs: 30000 });
  const [gracePending, setGracePending] = useState(false);
  const graceWriting = useRef(false);
  const [pending, setPending] = useState<Set<string>>(new Set());
  const inFlight = useRef(false);
  const rerun = useRef(false);
  const mounted = useRef(true);
  const writes = useRef(new Set<string>());
  const refresh = useCallback(async () => {
    if (!AppLock || (AppState.currentState && AppState.currentState !== 'active')) return;
    if (inFlight.current) { rerun.current = true; return; }
    inFlight.current = true;
    do {
      rerun.current = false;
      try {
        const [nextApps, nextProtected, nextStatus, nextPalette, nextGrace] = await Promise.all([
          AppLock.getInstalledApps(), AppLock.getProtectedApps(), AppLock.getProtectionStatus(), AppLock.getTheme(), AppLock.getGraceSettings(),
        ]);
        if (mounted.current) {
          setGrace(nextGrace); setApps(nextApps); setProtectedApps(nextProtected); setStatus(nextStatus); setPalette(nextPalette); setError(undefined);
        }
      } catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : String(e)); }
    } while (rerun.current && mounted.current);
    inFlight.current = false;
    if (mounted.current) setLoading(false);
  }, []);
  useEffect(() => {
    mounted.current = true;
    void Promise.resolve().then(refresh);
    let event: ReturnType<NonNullable<typeof AppLock>['addListener']> | undefined;
    const observe = () => {
      event ??= AppLock?.addListener('onStatusChanged', () => { void refresh(); });
    };
    if (!AppState.currentState || AppState.currentState === 'active') observe();
    // Detach native UI observers in background. Enforcement never wakes JavaScript.
    const lifecycle = AppState.addEventListener('change', (state) => {
      if (state === 'active') { observe(); void refresh(); }
      else { event?.remove(); event = undefined; }
    });
    return () => { mounted.current = false; event?.remove(); lifecycle.remove(); };
  }, [refresh]);
  useEffect(() => { void Promise.resolve().then(refresh); }, [scheme, refresh]);
  const toggle = useCallback(async (pkg: string, enabled: boolean) => {
    if (!AppLock || writes.current.has(pkg)) return;
    writes.current.add(pkg); setPending(new Set(writes.current));
    try {
      const next = await AppLock.setAppProtected(pkg, enabled);
      if (mounted.current) { setProtectedApps(next); setError(undefined); }
    } catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : String(e)); }
    finally {
      writes.current.delete(pkg);
      if (mounted.current) setPending(new Set(writes.current));
      void refresh();
    }
  }, [refresh]);
  const openSettings = useCallback(async (kind: 'accessibility' | 'security' | 'app') => {
    try {
      if (!AppLock) throw new Error('Install the native Android APK to enable protection.');
      await (kind === 'accessibility' ? AppLock.openAccessibilitySettings() : kind === 'security' ? AppLock.openSecuritySettings() : AppLock.openAppSettings());
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  }, []);
  const updateGrace = useCallback(async (enabled: boolean, durationMs: number) => {
    if (!AppLock || graceWriting.current) return;
    graceWriting.current = true; setGracePending(true);
    try { await AppLock.setGraceSettings(enabled, durationMs); await refresh(); }
    catch (e) { if (mounted.current) setError(e instanceof Error ? e.message : String(e)); }
    finally { graceWriting.current = false; if (mounted.current) setGracePending(false); }
  }, [refresh]);
  return { grace, gracePending, updateGrace, apps, protectedApps, status, palette, error, loading, pending, refresh, toggle, openSettings, nativeAvailable: !!AppLock };
}
const ProtectionContext = createContext<ReturnType<typeof useProtectionState> | null>(null);
export function ProtectionProvider({ children }: PropsWithChildren) {
  const value = useProtectionState();
  return <ProtectionContext value={value}>{children}</ProtectionContext>;
}
export function useProtection() {
  const value = useContext(ProtectionContext);
  if (!value) throw new Error('ProtectionProvider is required');
  return value;
}
