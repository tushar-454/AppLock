import '../global.css';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ProtectionProvider, useProtection } from '../lib/protection';

function Navigation() {
  const { palette } = useProtection();
  return <><StatusBar style="auto" /><Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: palette.background } }} /></>;
}
export default function RootLayout() {
  return <SafeAreaProvider><ProtectionProvider><Navigation /></ProtectionProvider></SafeAreaProvider>;
}
