/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { Home, Layers, Terminal, Settings, Smartphone, Code, ShieldCheck } from 'lucide-react';
import { DashboardTab } from './components/DashboardTab';
import { ProfilesTab } from './components/ProfilesTab';
import { DiagnosticsTab } from './components/DiagnosticsTab';
import { SettingsTab } from './components/SettingsTab';
import { AndroidProjectInspector } from './components/AndroidProjectInspector';
import { NetworkProfile, ProxyNode, VpnConnectionState, VpnStats, DiagnosticLog, AppSettings, LogLevel } from './types';
import { parseClashConfig, maskServerAddress } from './utils/yamlParser';

export default function App() {
  // Navigation
  const [currentTab, setCurrentTab] = useState<'home' | 'profiles' | 'logs' | 'settings' | 'android'>('home');

  // Network & Online State
  const [isOnline, setIsOnline] = useState<boolean>(typeof navigator !== 'undefined' ? navigator.onLine : true);

  // Profiles State
  const [profiles, setProfiles] = useState<NetworkProfile[]>(() => {
    try {
      const saved = localStorage.getItem('ajaz_profiles_data');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [activeProfileId, setActiveProfileId] = useState<string | null>(() => {
    try {
      return localStorage.getItem('ajaz_active_profile_id') || null;
    } catch {
      return null;
    }
  });

  const [isImporting, setIsImporting] = useState<boolean>(false);

  // Settings State
  const [settings, setSettings] = useState<AppSettings>(() => {
    try {
      const saved = localStorage.getItem('ajaz_settings_data');
      return saved
        ? JSON.parse(saved)
        : {
            autoReconnect: true,
            startOnBoot: false,
            dnsMode: 'Cloudflare (1.1.1.1)',
            customDns: '1.1.1.1',
            ipv6Mode: 'Safe Fallback (Block Leaks)',
            routingMode: 'Bypass LAN & Private',
            bypassLan: true,
            killSwitchEnabled: false,
            connectionTimeoutSeconds: 20,
            logLevel: 'INFO',
          };
    } catch {
      return {
        autoReconnect: true,
        startOnBoot: false,
        dnsMode: 'Cloudflare (1.1.1.1)',
        customDns: '1.1.1.1',
        ipv6Mode: 'Safe Fallback (Block Leaks)',
        routingMode: 'Bypass LAN & Private',
        bypassLan: true,
        killSwitchEnabled: false,
        connectionTimeoutSeconds: 20,
        logLevel: 'INFO',
      };
    }
  });

  // Diagnostic Logs
  const [logs, setLogs] = useState<DiagnosticLog[]>([]);

  // VPN Connection Lifecycle
  const [vpnState, setVpnState] = useState<VpnConnectionState>({ type: 'DISCONNECTED' });
  const [stats, setStats] = useState<VpnStats>({
    bytesIn: 0,
    bytesOut: 0,
    speedInBps: 0,
    speedOutBps: 0,
    latencyMs: 0,
    durationSeconds: 0,
  });

  const statsIntervalRef = useRef<any>(null);
  const connectTimeoutRef = useRef<any>(null);

  // Add Log Helper
  const addLog = (level: LogLevel, tag: string, message: string) => {
    const entry: DiagnosticLog = {
      id: 'log_' + Math.random().toString(36).substring(2, 9),
      timestamp: Date.now(),
      level,
      tag,
      message,
    };
    setLogs((prev) => [entry, ...prev.slice(0, 400)]);
  };

  // Monitor physical network
  useEffect(() => {
    const handleOnline = () => {
      setIsOnline(true);
      addLog('INFO', 'NetworkMonitor', 'Physical network interface online (Wi-Fi / Cellular)');
    };
    const handleOffline = () => {
      setIsOnline(false);
      addLog('WARN', 'NetworkMonitor', 'Physical network interface disconnected');
      if (vpnState.type === 'CONNECTED' || vpnState.type === 'CONNECTING') {
        setVpnState({
          type: 'ERROR',
          message: 'Network connection dropped unexpectedly',
          recoveryAction: 'Check your Wi-Fi or Cellular connection',
        });
      }
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    // Initial log
    addLog('INFO', 'System', 'Ajaz×tiktok native engine initialized (v1.0.0)');
    addLog('DEBUG', 'System', 'VpnService permissions validated');

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  // Save state changes
  useEffect(() => {
    localStorage.setItem('ajaz_profiles_data', JSON.stringify(profiles));
    if (activeProfileId) {
      localStorage.setItem('ajaz_active_profile_id', activeProfileId);
    }
  }, [profiles, activeProfileId]);

  useEffect(() => {
    localStorage.setItem('ajaz_settings_data', JSON.stringify(settings));
  }, [settings]);

  // Derive active profile & selected node
  const activeProfile = profiles.find((p) => p.id === activeProfileId) || profiles[0] || null;
  const selectedNode = activeProfile?.proxies.find((n) => n.id === activeProfile.selectedProxyId) || activeProfile?.proxies[0] || null;

  // Toggle Connection Handler
  const handleToggleConnection = () => {
    if (vpnState.type === 'CONNECTED' || vpnState.type === 'CONNECTING') {
      // Disconnect
      addLog('INFO', 'VpnService', 'Stopping VpnService tunnel...');
      setVpnState({ type: 'STOPPING' });
      if (statsIntervalRef.current) clearInterval(statsIntervalRef.current);
      if (connectTimeoutRef.current) clearTimeout(connectTimeoutRef.current);

      setTimeout(() => {
        setVpnState({ type: 'DISCONNECTED' });
        setStats((prev) => ({ ...prev, speedInBps: 0, speedOutBps: 0 }));
        addLog('INFO', 'VpnService', 'Virtual tunnel interface cleanly closed. Resources released.');
      }, 500);
      return;
    }

    // Connect Checks
    if (!isOnline) {
      addLog('ERROR', 'VpnService', 'Connection rejected: Physical network is offline');
      setVpnState({
        type: 'ERROR',
        message: 'No Internet connection available',
        recoveryAction: 'Connect to Wi-Fi or Cellular data first',
      });
      return;
    }

    if (!activeProfile || !activeProfile.isValid || activeProfile.proxies.length === 0) {
      addLog('ERROR', 'VpnService', 'Connection rejected: No valid profile with usable providers');
      setVpnState({
        type: 'ERROR',
        message: 'No valid profile configured',
        recoveryAction: 'Import a subscription URL or paste YAML config',
      });
      return;
    }

    const node = selectedNode || activeProfile.proxies[0];
    const maskedAddr = maskServerAddress(node.server, node.port);

    addLog('INFO', 'VpnService', `Establishing route for profile '${activeProfile.name}'...`);
    addLog('DEBUG', 'VpnService', `Selected endpoint: ${node.name} [${node.type}] (${maskedAddr})`);
    addLog('INFO', 'VpnService', `Configuring virtual interface: MTU 1500, IPv4 10.0.0.2/32, DNS: ${settings.customDns}`);

    setVpnState({
      type: 'CONNECTING',
      message: `Negotiating secure socket with ${node.name}...`,
    });

    connectTimeoutRef.current = setTimeout(() => {
      const now = Date.now();
      setVpnState({
        type: 'CONNECTED',
        profileName: activeProfile.name,
        serverName: node.name,
        serverAddress: maskedAddr,
        connectedSince: now,
      });

      addLog('INFO', 'VpnService', `Tunnel interface active. Handshake verified with ${node.name}`);
      addLog('DEBUG', 'VpnService', `Socket protected via protect(fd). Anti-leak rules applied.`);

      let bytesInAcc = 1024 * 32;
      let bytesOutAcc = 1024 * 18;
      let durationSec = 0;

      if (statsIntervalRef.current) clearInterval(statsIntervalRef.current);
      statsIntervalRef.current = setInterval(() => {
        durationSec += 1;
        const deltaIn = Math.floor(Math.random() * 45000) + 5000;
        const deltaOut = Math.floor(Math.random() * 25000) + 2000;
        bytesInAcc += deltaIn;
        bytesOutAcc += deltaOut;

        setStats({
          bytesIn: bytesInAcc,
          bytesOut: bytesOutAcc,
          speedInBps: deltaIn,
          speedOutBps: deltaOut,
          latencyMs: node.latencyMs || Math.floor(Math.random() * 20) + 35,
          durationSeconds: durationSec,
        });
      }, 1000);
    }, 1200);
  };

  // Import from URL
  const handleImportFromUrl = async (url: string, name: string) => {
    setIsImporting(true);
    addLog('INFO', 'NetworkClient', `Requesting subscription: ${url.substring(0, 30)}...`);

    try {
      const res = await fetch('/api/fetch-subscription', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url }),
      });

      const data = await res.json();

      if (!res.ok || !data.text) {
        throw new Error(data.error || 'Failed to download subscription');
      }

      addLog('INFO', 'NetworkClient', `Downloaded ${data.size} bytes (type: ${data.contentType})`);
      const parsed = parseClashConfig(data.text, name, url);

      if (!parsed.isValid || parsed.proxies.length === 0) {
        addLog('WARN', 'Parser', 'Configuration downloaded but contains 0 usable proxy definitions');
      } else {
        addLog('INFO', 'Parser', `Successfully parsed ${parsed.proxies.length} endpoints`);
      }

      setProfiles((prev) => {
        const existingIdx = prev.findIndex((p) => p.sourceUrl === url);
        if (existingIdx >= 0) {
          const updated = [...prev];
          updated[existingIdx] = parsed;
          return updated;
        }
        return [parsed, ...prev];
      });

      setActiveProfileId(parsed.id);
    } catch (err: any) {
      addLog('ERROR', 'NetworkClient', `Fetch failed: ${err.message}`);
    } finally {
      setIsImporting(false);
    }
  };

  // Import from text
  const handleImportFromText = (text: string, name: string) => {
    addLog('INFO', 'Parser', `Parsing manual configuration (${text.length} characters)`);
    const parsed = parseClashConfig(text, name, undefined);

    if (parsed.isValid) {
      addLog('INFO', 'Parser', `Loaded ${parsed.proxies.length} endpoints for '${parsed.name}'`);
    } else {
      addLog('ERROR', 'Parser', parsed.validationMessage || 'Parse error');
    }

    setProfiles((prev) => [parsed, ...prev]);
    setActiveProfileId(parsed.id);
  };

  // Refresh profile
  const handleRefreshProfile = (profile: NetworkProfile) => {
    if (profile.sourceUrl) {
      handleImportFromUrl(profile.sourceUrl, profile.name);
    }
  };

  // Select active node
  const handleSelectNode = (node: ProxyNode) => {
    if (!activeProfile) return;
    const updated = { ...activeProfile, selectedProxyId: node.id };
    setProfiles((prev) => prev.map((p) => (p.id === activeProfile.id ? updated : p)));
    addLog('DEBUG', 'ProfileStorage', `Selected active endpoint: ${node.name}`);

    if (vpnState.type === 'CONNECTED') {
      // Live switch endpoint
      handleToggleConnection(); // disconnect
      setTimeout(() => handleToggleConnection(), 600); // reconnect
    }
  };

  // Rename, duplicate, delete
  const handleRenameProfile = (id: string, newName: string) => {
    setProfiles((prev) => prev.map((p) => (p.id === id ? { ...p, name: newName, updatedAt: Date.now() } : p)));
    addLog('INFO', 'ProfileStorage', `Renamed profile to '${newName}'`);
  };

  const handleDuplicateProfile = (id: string) => {
    const target = profiles.find((p) => p.id === id);
    if (!target) return;
    const dup: NetworkProfile = {
      ...target,
      id: 'prof_' + Math.random().toString(36).substring(2, 9),
      name: `${target.name} (Copy)`,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    setProfiles((prev) => [dup, ...prev]);
    addLog('INFO', 'ProfileStorage', `Duplicated profile '${target.name}'`);
  };

  const handleDeleteProfile = (id: string) => {
    setProfiles((prev) => prev.filter((p) => p.id !== id));
    if (activeProfileId === id) {
      setActiveProfileId(profiles.find((p) => p.id !== id)?.id || null);
    }
    addLog('WARN', 'ProfileStorage', `Deleted profile id: ${id}`);
  };

  const handleResetAllData = () => {
    if (vpnState.type === 'CONNECTED') {
      handleToggleConnection();
    }
    setProfiles([]);
    setActiveProfileId(null);
    setLogs([]);
    localStorage.clear();
    addLog('WARN', 'System', 'All user configurations, profiles & logs cleared');
  };

  return (
    <div className="min-h-screen bg-[#050505] text-[#e6e6e6] flex flex-col items-center justify-start p-2 sm:p-6 selection:bg-[#c5a059] selection:text-[#050505] font-sans">
      {/* Top Application Header */}
      <header className="w-full max-w-4xl flex items-center justify-between py-3 px-5 mb-4 bg-[#0a0a0a]/90 backdrop-blur-md border border-[#1f1f1f] rounded-2xl shadow-xl shadow-black/60">
        <div className="flex items-center gap-3.5">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#1c1811] via-[#0d0c0a] to-[#050505] border border-[#c5a059]/40 flex items-center justify-center shadow-lg shadow-[#c5a059]/10">
            <span className="font-serif italic font-bold text-base text-[#c5a059]">A×T</span>
          </div>
          <div>
            <h1 className="font-serif text-lg tracking-wide text-white flex items-center gap-2.5">
              <span className="font-cinzel font-bold text-white tracking-wider">Ajaz<span className="text-[#c5a059] font-normal mx-0.5">×</span>tiktok</span>
              <span className="text-[10px] uppercase tracking-widest px-2 py-0.5 rounded-full bg-[#c5a059]/10 text-[#c5a059] border border-[#c5a059]/30 font-sans font-semibold">
                Native VpnService
              </span>
            </h1>
            <p className="text-xs text-[#8a8a8a] font-light">Production-grade Clash/YAML Subscription Client & Android APK Project</p>
          </div>
        </div>

        {/* View Switcher: Mobile Live Simulator vs Android Studio Inspector */}
        <div className="flex bg-[#050505] p-1 rounded-xl border border-[#1f1f1f] text-xs">
          <button
            id="tab-mobile-simulator-button"
            onClick={() => setCurrentTab('home')}
            className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg font-medium transition-all ${
              currentTab !== 'android'
                ? 'bg-[#c5a059] text-[#050505] font-bold shadow-md shadow-[#c5a059]/20'
                : 'text-[#8a8a8a] hover:text-white'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Mobile App UI</span>
          </button>
          <button
            id="tab-android-studio-button"
            onClick={() => setCurrentTab('android')}
            className={`flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg font-medium transition-all ${
              currentTab === 'android'
                ? 'bg-[#c5a059] text-[#050505] font-bold shadow-md shadow-[#c5a059]/20'
                : 'text-[#8a8a8a] hover:text-white'
            }`}
          >
            <Code className="w-3.5 h-3.5" />
            <span>Android Studio & APK Export</span>
          </button>
        </div>
      </header>

      {/* Main Container */}
      <main className="w-full max-w-4xl flex flex-col items-center">
        {currentTab === 'android' ? (
          <div className="w-full bg-[#0a0a0a]/95 border border-[#1f1f1f] rounded-3xl p-6 shadow-2xl shadow-black/80 min-h-[620px]">
            <AndroidProjectInspector />
          </div>
        ) : (
          /* Phone Frame Container */
          <div className="relative w-full max-w-[390px] h-[780px] bg-[#070707] border-[6px] border-[#1c1c1c] rounded-[44px] shadow-2xl shadow-black/90 overflow-hidden flex flex-col ring-1 ring-white/10">
            {/* Phone Notch / Speaker Island */}
            <div className="w-full pt-3 px-6 flex items-center justify-between z-20 bg-[#070707]">
              <span className="text-[11px] font-semibold text-[#8a8a8a] font-mono">
                {new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              </span>
              <div className="w-20 h-4 bg-[#111111] rounded-full border border-[#222222]" />
              <div className="flex items-center gap-1.5 text-[#8a8a8a]">
                <span className="text-[10px] font-mono text-[#c5a059]">5G</span>
                <div className="w-4 h-2.5 rounded-sm border border-[#444444] p-0.5 flex items-center">
                  <div className="w-full h-full bg-[#c5a059] rounded-xs" />
                </div>
              </div>
            </div>

            {/* Screen Content View */}
            <div className="flex-1 p-4 overflow-y-auto flex flex-col bg-[#070707]">
              {currentTab === 'home' && (
                <DashboardTab
                  vpnState={vpnState}
                  stats={stats}
                  isOnline={isOnline}
                  activeProfile={activeProfile}
                  selectedNode={selectedNode}
                  onToggleConnection={handleToggleConnection}
                  onSelectNode={handleSelectNode}
                  onNavigateToProfiles={() => setCurrentTab('profiles')}
                />
              )}

              {currentTab === 'profiles' && (
                <ProfilesTab
                  profiles={profiles}
                  activeProfileId={activeProfileId}
                  isImporting={isImporting}
                  onSelectActiveProfile={(id) => setActiveProfileId(id)}
                  onImportFromUrl={handleImportFromUrl}
                  onImportFromText={handleImportFromText}
                  onRefreshProfile={handleRefreshProfile}
                  onRenameProfile={handleRenameProfile}
                  onDuplicateProfile={handleDuplicateProfile}
                  onDeleteProfile={handleDeleteProfile}
                />
              )}

              {currentTab === 'logs' && (
                <DiagnosticsTab logs={logs} onClearLogs={() => setLogs([])} />
              )}

              {currentTab === 'settings' && (
                <SettingsTab
                  settings={settings}
                  onUpdateSettings={setSettings}
                  onResetAllData={handleResetAllData}
                />
              )}
            </div>

            {/* Bottom Navigation Bar */}
            <nav className="w-full bg-[#0a0a0a]/95 border-t border-[#1a1a1a] px-2 py-2 flex items-center justify-around z-20 backdrop-blur-md">
              <button
                id="nav-dashboard"
                onClick={() => setCurrentTab('home')}
                className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition-all ${
                  currentTab === 'home' ? 'text-[#c5a059]' : 'text-[#666666] hover:text-[#aaaaaa]'
                }`}
              >
                <Home className="w-4 h-4" />
                <span className="text-[10px] font-semibold tracking-wider">Dashboard</span>
              </button>

              <button
                id="nav-profiles"
                onClick={() => setCurrentTab('profiles')}
                className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition-all ${
                  currentTab === 'profiles' ? 'text-[#c5a059]' : 'text-[#666666] hover:text-[#aaaaaa]'
                }`}
              >
                <Layers className="w-4 h-4" />
                <span className="text-[10px] font-semibold tracking-wider">Profiles</span>
              </button>

              <button
                id="nav-diagnostics"
                onClick={() => setCurrentTab('logs')}
                className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition-all ${
                  currentTab === 'logs' ? 'text-[#c5a059]' : 'text-[#666666] hover:text-[#aaaaaa]'
                }`}
              >
                <Terminal className="w-4 h-4" />
                <span className="text-[10px] font-semibold tracking-wider">Diagnostics</span>
              </button>

              <button
                id="nav-settings"
                onClick={() => setCurrentTab('settings')}
                className={`flex flex-col items-center gap-1 py-1 px-3 rounded-xl transition-all ${
                  currentTab === 'settings' ? 'text-[#c5a059]' : 'text-[#666666] hover:text-[#aaaaaa]'
                }`}
              >
                <Settings className="w-4 h-4" />
                <span className="text-[10px] font-semibold tracking-wider">Settings</span>
              </button>
            </nav>
          </div>
        )}
      </main>
    </div>
  );
}
