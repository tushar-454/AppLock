import { Pressable, Text, View } from 'react-native';
import { useProtection } from '../lib/protection';

export function SetupNotice() {
  const { status, palette, openSettings, nativeAvailable, loading } = useProtection();
  if (loading) return null;
  if (status.serviceConnected && status.canAuthenticate && status.configurationHealthy) return null;
  return (
    <View className="mx-5 mb-4 rounded-3xl p-5" style={{ backgroundColor: palette.surface }} accessibilityLiveRegion="polite">
      <Text className="text-lg font-semibold" style={{ color: palette.text }}>{status.status}</Text>
      <Text className="mt-2 text-sm leading-6" style={{ color: palette.muted }}>
        {!status.configurationHealthy ? 'Stored protection settings are damaged. Review and reselect your protected apps; protection cannot be trusted until the configuration is restored.' :
          !nativeAvailable ? 'Install the standalone Android build. Expo Go cannot run the protection engine.' :
          !status.deviceSecure ? 'First, set a PIN, pattern or password in Android Settings. App Lock uses your existing system authentication.' :
          !status.accessibilityEnabled ? 'Allow App Lock to detect app window package names and cover protected apps. It does not read screen content, messages, passwords or typed input. Everything stays on this device.' :
          !status.serviceConnected ? 'Android has not connected the protection service. Open Accessibility settings and check App Lock. Protection is inactive until the service connects.' :
          'Android authentication is currently unavailable. Check your screen lock in Android Settings.'}
      </Text>
      {nativeAvailable && status.configurationHealthy && <Pressable
        accessibilityRole="button"
        onPress={() => { void openSettings(!status.deviceSecure || (status.serviceConnected && !status.canAuthenticate) ? 'security' : 'accessibility'); }}
        className="mt-4 min-h-12 items-center justify-center rounded-full px-5"
        style={{ backgroundColor: palette.primary }}>
        <Text className="font-semibold" style={{ color: palette.background }}>{!status.deviceSecure ? 'Open security settings' : 'Open Android settings'}</Text>
      </Pressable>}
    </View>
  );
}
