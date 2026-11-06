import { createContext, useCallback, useContext, useEffect, useState, type PropsWithChildren } from 'react';
import { ActivityIndicator, AppState, Text, useColorScheme, View } from 'react-native';
import { AppLock, type ManagementState, type Palette } from '../../modules/app-lock';
import { PinScreen } from '../components/PinScreen';

const ManagementContext = createContext({ changePin: () => {} });
export const useManagement = () => useContext(ManagementContext);
export function ManagementGate({ children }: PropsWithChildren) {
  const scheme = useColorScheme();
  const [state, setState] = useState<ManagementState>();
  const [active, setActive] = useState(AppState.currentState === 'active');
  const [changing, setChanging] = useState(false);
  const [error, setError] = useState('');
  const [nativePalette, setPalette] = useState<Palette>();
  const palette = nativePalette ?? (scheme === 'dark'
    ? { background: '#141318', surface: '#25232A', text: '#E6E1E5', muted: '#CAC4D0', primary: '#D0BCFF', outline: '#49454F' }
    : { background: '#FFFBFE', surface: '#F3EDF7', text: '#1D1B20', muted: '#625B71', primary: '#6750A4', outline: '#CAC4D0' });
  const update = useCallback((next: ManagementState) => {
    setState(next);
    if (!next.authenticated) setChanging(false);
  }, []);
  const cancel = useCallback(() => setChanging(false), []);
  useEffect(() => {
    let mounted = true;
    let revision = 0;
    const refresh = async () => {
      const current = ++revision;
      try {
        const next = await AppLock?.getManagementState();
        if (mounted && current === revision && next) update(next);
      } catch { if (mounted && current === revision) { setState(undefined); setError('Unable to read PIN security state. Close and reopen AppLock.'); } }
    };
    void refresh();
    let events: ReturnType<NonNullable<typeof AppLock>['addListener']> | undefined;
    const observe = () => { events ??= AppLock?.addListener('onStatusChanged', () => { void refresh(); }); };
    if (!AppState.currentState || AppState.currentState === 'active') observe();
    const lifecycle = AppState.addEventListener('change', next => {
      setActive(next === 'active'); setState(undefined); setChanging(false);
      if (next === 'active') { observe(); void refresh(); }
      else { revision++; events?.remove(); events = undefined; }
    });
    return () => { mounted = false; events?.remove(); lifecycle.remove(); };
  }, [update]);
  useEffect(() => { void AppLock?.getTheme().then(setPalette).catch(() => {}); }, [scheme, active]);
  useEffect(() => {
    if (!active || !state) return;
    // Wait until the PIN gate has committed before removing the native pause cover.
    const frame = requestAnimationFrame(() => { void AppLock?.acknowledgeManagementScreen(state.generation); });
    return () => cancelAnimationFrame(frame);
  }, [state, active]);
  if (!AppLock || !active || !state) return <View className="flex-1 items-center justify-center px-6" style={{ backgroundColor: palette.background }}>
    {AppLock && !error ? <ActivityIndicator color={palette.primary} /> : <Text style={{ color: palette.text }}>{error || 'Install the standalone Android APK to use secure AppLock.'}</Text>}
  </View>;
  if (!state.authenticated || changing) return <PinScreen key={`${state.generation}-${changing}`} state={state} palette={palette} changing={changing} onState={update} onCancel={cancel} />;
  return <ManagementContext value={{ changePin: () => setChanging(true) }}>{children}</ManagementContext>;
}
