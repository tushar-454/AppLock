import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Animated, BackHandler, Image, Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { AppLock, type ManagementState, type Palette } from '../../modules/app-lock';

export function PinScreen({ state, palette, changing = false, onState, onCancel }: {
  state: ManagementState; palette: Palette; changing?: boolean;
  onState: (state: ManagementState) => void; onCancel: () => void;
}) {
  const [digits, setDigits] = useState('');
  const [first, setFirst] = useState('');
  const [current, setCurrent] = useState('');
  const [step, setStep] = useState<'current' | 'new' | 'confirm'>(changing ? 'current' : 'new');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [pulse] = useState(() => new Animated.Value(1));
  const [shake] = useState(() => new Animated.Value(0));
  const submitting = useRef(false);
  const mounted = useRef(true);
  const setup = !state.configured || changing;
  const throttled = state.retryAfterMs > 0;
  useEffect(() => {
    mounted.current = true;
    const back = BackHandler.addEventListener('hardwareBackPress', () => {
      if (changing) onCancel(); else BackHandler.exitApp();
      return true;
    });
    return () => { mounted.current = false; back.remove(); };
  }, [changing, onCancel]);
  // Display only. Native monotonic deadlines are the security authority.
  useEffect(() => {
    if (!throttled) return;
    const timer = setInterval(() => { void AppLock?.getManagementState().then(onState).catch(() => {}); }, 1000);
    return () => clearInterval(timer);
  }, [throttled, onState]);
  function fail(message: string) {
    setDigits(''); setError(message);
    Animated.sequence([12, -12, 8, -8, 0].map(toValue => Animated.timing(shake, { toValue, duration: 55, useNativeDriver: true }))).start();
  }
  async function submit(pin: string) {
    if (!AppLock || submitting.current) return;
    if (setup && step === 'current') { setCurrent(pin); setStep('new'); setDigits(''); return; }
    if (setup && step === 'new') { setFirst(pin); setStep('confirm'); setDigits(''); return; }
    submitting.current = true; setBusy(true);
    try {
      if (changing) {
        await AppLock.changePin(current, first, pin);
        if (mounted.current) onState(await AppLock.getManagementState());
      } else {
        const next = setup ? await AppLock.createPin(first, pin) : await AppLock.verifyPin(pin);
        if (!mounted.current) return;
        onState(next);
        if (!next.authenticated) fail('Incorrect PIN. Try again.');
      }
    } catch (e) {
      if (!mounted.current) return;
      fail(e instanceof Error ? e.message : 'Unable to verify PIN. Try again.');
      if (setup) { setFirst(''); setCurrent(''); setStep(changing ? 'current' : 'new'); }
      try { onState(await AppLock.getManagementState()); } catch { /* Keep the gate closed. */ }
    } finally { submitting.current = false; if (mounted.current) setBusy(false); }
  }
  function key(value: string) {
    if (busy || throttled || submitting.current) return;
    setError('');
    Animated.sequence([Animated.timing(pulse, { toValue: 0.96, duration: 50, useNativeDriver: true }), Animated.timing(pulse, { toValue: 1, duration: 90, useNativeDriver: true })]).start();
    if (value === '⌫') { setDigits(digits.slice(0, -1)); return; }
    if (digits.length >= 6) return;
    const next = digits + value;
    setDigits(next);
    if (next.length === 6) void submit(next);
  }
  const title = !setup ? 'Enter AppLock PIN' : step === 'current' ? 'Enter current PIN' : step === 'confirm' ? 'Confirm your PIN' : changing ? 'Create a new PIN' : 'Create AppLock PIN';
  const subtitle = !setup ? 'Enter your 6-digit PIN to continue.' : step === 'confirm' ? 'Enter the same 6-digit PIN again.' : step === 'current' ? 'Your current PIN is required to change it.' : 'Choose 6 digits to protect AppLock settings.';
  return <SafeAreaView className="flex-1" style={{ backgroundColor: palette.background }}>
    <ScrollView contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', paddingVertical: 24 }}>
      <View className="items-center px-6">
        <Image source={require('../../assets/branding/adaptive-monochrome.png')} className="mb-5 h-16 w-16" style={{ tintColor: palette.primary }} accessibilityIgnoresInvertColors />
        <Text accessibilityRole="header" className="text-center text-3xl" style={{ color: palette.text }}>{title}</Text>
        <Text className="mt-3 text-center text-base leading-6" style={{ color: palette.muted }}>{subtitle}</Text>
        <Animated.View className="my-8 flex-row gap-4" style={{ transform: [{ translateX: shake }, { scale: pulse }] }} accessibilityLabel={`${digits.length} of 6 digits entered`}>
          {Array.from({ length: 6 }, (_, index) => <View key={index} className="h-3.5 w-3.5 rounded-full border-2" style={{ borderColor: palette.primary, backgroundColor: index < digits.length ? palette.primary : 'transparent' }} />)}
        </Animated.View>
        <View className="mb-3 min-h-12 justify-center">
          {busy ? <ActivityIndicator color={palette.primary} /> : <Text accessibilityRole="alert" className="text-center text-sm leading-5" style={{ color: palette.text }}>{throttled ? `Too many attempts. Try again in ${Math.ceil(state.retryAfterMs / 1000)} seconds.` : error || ' '}</Text>}
        </View>
        <View style={{ width: '100%', maxWidth: 328 }}>
          {['123', '456', '789', ' 0⌫'].map((row, index) => <View key={index} className="mb-3 flex-row justify-between gap-3">
            {Array.from(row).map(value => value === ' ' ? <View key="empty" className="flex-1" /> : <Pressable key={value} accessibilityRole="button" accessibilityLabel={value === '⌫' ? 'Delete last digit' : value}
              disabled={busy || throttled} onPress={() => key(value)} className="h-16 flex-1 items-center justify-center rounded-full" style={{ backgroundColor: palette.surface, opacity: busy || throttled ? 0.5 : 1 }} android_ripple={{ color: palette.outline }}>
              <Text style={{ color: palette.text, fontSize: 28 }}>{value}</Text>
            </Pressable>)}
          </View>)}
        </View>
        {changing ? <Pressable accessibilityRole="button" onPress={onCancel} className="mt-3 min-h-12 justify-center px-6"><Text style={{ color: palette.primary }}>Cancel</Text></Pressable> : <Pressable accessibilityRole="button" onPress={() => Alert.alert('Forgot your AppLock PIN?', 'There is no PIN bypass or reset. Clearing AppLock storage in Android Settings removes the PIN and ALL protected-app selections. You must set up protection again. Anyone able to clear data or uninstall AppLock can remove its protection.')} className="mt-3 min-h-12 justify-center px-6"><Text style={{ color: palette.primary }}>PIN recovery policy</Text></Pressable>}
        {setup && <Text className="mt-2 text-center text-xs leading-5" style={{ color: palette.muted }}>This PIN protects AppLock only. Other apps continue using Android authentication.</Text>}
      </View>
    </ScrollView>
  </SafeAreaView>;
}
