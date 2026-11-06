import { useDeferredValue, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Image, Pressable, Switch, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { SetupNotice } from '../components/SetupNotice';
import { useProtection } from '../lib/protection';

export default function Index() {
  const { apps, protectedApps, status, palette, error, loading, pending, refresh, toggle } = useProtection();
  const [query, setQuery] = useState('');
  const search = useDeferredValue(query.normalize('NFKC').toLocaleLowerCase().trim());
  const filtered = useMemo(() => apps.filter(app => !search || app.label.normalize('NFKC').toLocaleLowerCase().includes(search) || app.packageName.toLowerCase().includes(search)), [apps, search]);
  const enabled = useMemo(() => new Set(protectedApps), [protectedApps]);
  return (
    <SafeAreaView className="flex-1" style={{ backgroundColor: palette.background }} edges={['top', 'bottom']}>
      <View className="flex-row items-center justify-between px-6 pb-4 pt-4">
        <Text accessibilityRole="header" className="text-3xl" style={{ color: palette.text }}>App Lock</Text>
        <Pressable accessibilityLabel="Settings" accessibilityRole="button" onPress={() => router.push('./settings')} hitSlop={8} className="h-12 w-12 items-center justify-center rounded-full" android_ripple={{ color: palette.outline }}>
          <Text style={{ color: palette.text, fontSize: 28 }} importantForAccessibility="no">⚙</Text>
        </Pressable>
      </View>
      <View className="mx-5 mb-4 flex-row items-center rounded-full px-5" style={{ backgroundColor: palette.surface }}>
        <Text style={{ color: palette.muted, fontSize: 26 }} importantForAccessibility="no">⌕</Text>
        <TextInput accessibilityLabel="Search apps" placeholder="Search apps" placeholderTextColor={palette.muted} value={query} onChangeText={setQuery}
          className="min-h-14 flex-1 px-3 text-base" style={{ color: palette.text }} autoCapitalize="none" autoCorrect={false} returnKeyType="search" />
        {!!query && <Pressable accessibilityRole="button" accessibilityLabel="Clear search" onPress={() => setQuery('')} className="h-12 w-10 items-center justify-center"><Text style={{ color: palette.muted, fontSize: 24 }}>×</Text></Pressable>}
      </View>
      <SetupNotice />
      <View className="flex-row items-center justify-between px-6 pb-3">
        <Text className="text-sm" style={{ color: palette.muted }}>{apps.length} apps · {protectedApps.length} protected</Text>
        <Text className="text-xs" style={{ color: palette.muted }} accessibilityLiveRegion="polite">{loading ? 'Checking protection…' : status.serviceConnected && status.canAuthenticate && status.configurationHealthy ? 'Protection active' : 'Protection inactive'}</Text>
      </View>
      {!!error && <Text className="px-6 pb-3 text-sm" style={{ color: palette.text }} accessibilityRole="alert">{error}</Text>}
      {loading ? <ActivityIndicator className="mt-10" color={palette.primary} accessibilityLabel="Loading installed apps" /> :
        <FlatList data={filtered} keyExtractor={item => item.packageName} keyboardShouldPersistTaps="handled"
          onRefresh={() => { void refresh(); }} refreshing={false} initialNumToRender={12} maxToRenderPerBatch={12} windowSize={7}
          contentContainerStyle={{ paddingBottom: 24 }}
          ItemSeparatorComponent={() => <View className="ml-20 mr-6" style={{ height: 0.5, backgroundColor: palette.outline }} />}
          ListEmptyComponent={<Text className="p-8 text-center leading-6" style={{ color: palette.muted }}>{query ? 'No apps match your search.' : 'No launchable applications found. Pull down to refresh.'}</Text>}
          renderItem={({ item }) => <View className="min-h-20 flex-row items-center gap-4 px-6 py-3">
            <Image source={{ uri: item.iconUri }} className="h-11 w-11" accessibilityIgnoresInvertColors />
            <View className="flex-1">
              <Text className="text-base" style={{ color: palette.text }}>{item.label}</Text>
              {!item.canProtect && <Text className="mt-1 text-xs" style={{ color: palette.muted }}>Available for system recovery</Text>}
            </View>
            <Switch accessibilityLabel={`Protect ${item.label}`} accessibilityHint={item.canProtect ? 'Requires Android authentication when this app is opened' : item.exclusionReason}
              value={enabled.has(item.packageName)} disabled={!item.canProtect || pending.has(item.packageName) || (!status.deviceSecure && !enabled.has(item.packageName))}
              onValueChange={value => { void toggle(item.packageName, value); }} trackColor={{ false: palette.outline, true: palette.primary }} thumbColor={palette.background} />
          </View>} />}
    </SafeAreaView>
  );
}
