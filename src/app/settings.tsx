import { Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import Constants from 'expo-constants';
import { useProtection } from '../lib/protection';

export default function Settings() {
  const { palette, status, error, openSettings } = useProtection();
  const row = (title: string, detail: string, action?: () => void) => (
    <Pressable key={title} disabled={!action} accessibilityRole={action ? 'button' : undefined} onPress={action}
      className="min-h-20 border-b px-6 py-5" style={{ borderColor: palette.outline }} android_ripple={action ? { color: palette.outline } : undefined}>
      <Text className="text-base font-medium" style={{ color: palette.text }}>{title}{action ? '  ›' : ''}</Text>
      <Text className="mt-2 text-sm leading-6" style={{ color: palette.muted }}>{detail}</Text>
    </Pressable>
  );
  return <SafeAreaView className="flex-1" style={{ backgroundColor: palette.background }} edges={['top', 'bottom']}>
    <View className="flex-row items-center gap-3 px-4 py-3">
      <Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={() => router.back()} className="h-12 w-12 items-center justify-center"><Text style={{ color: palette.text, fontSize: 28 }}>‹</Text></Pressable>
      <Text accessibilityRole="header" className="text-2xl" style={{ color: palette.text }}>Settings</Text>
    </View>
    <ScrollView contentContainerStyle={{ paddingBottom: 32 }}>
      {row('Protection status', status.status)}
      {row('Accessibility', `${status.accessibilityEnabled ? 'Enabled' : 'Permission required'}. Detects window package names and covers protected apps. Screen contents and typed input are never read. You can disable protection here at any time.`, () => { void openSettings('accessibility'); })}
      {row('Authentication', `${status.authenticationMethod}. ${status.deviceSecure ? 'Device screen lock is set.' : 'Set a device screen lock to begin.'} ${status.canAuthenticate ? 'Android authentication is available.' : 'Authentication is unavailable.'}`, () => { void openSettings('security'); })}
      {row('Re-lock policy', 'Authenticate when entering from the launcher or another app. Re-lock on exit, screen lock, service restart, reboot, and after five minutes. System dialogs and keyboards do not end a session.')}
      {row('Theme', 'Follows your system light or dark theme. Uses Material You wallpaper colors on Android 12 and later.')}
      {row('Android app settings', 'Manage permissions, disable or uninstall App Lock. For sideloaded apps, Android may require “Allow restricted settings” here before Accessibility can be enabled.', () => { void openSettings('app'); })}
      {row('Privacy & limitations', 'All settings stay on this device. No accounts, analytics or network access in the release build. Android window events arrive after an app opens: brief content exposure is possible. App Lock cannot secure notifications, recent-app thumbnails, split-screen, work profiles, or protect while its service is disabled. Use Android screen lock and app-specific privacy controls for stronger protection.')}
      {row('About', `App Lock · ${Constants.expoConfig?.version ?? '1.0.0'}\nPersonal Android app protection using system biometrics and screen-lock credentials.`)}
      {!!error && <Text accessibilityRole="alert" className="px-6 py-4" style={{ color: palette.text }}>{error}</Text>}
    </ScrollView>
  </SafeAreaView>;
}
