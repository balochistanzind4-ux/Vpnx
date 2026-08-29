import React, { useState } from 'react';
import { Shield, RotateCcw, Lock, Globe, Server, AlertTriangle } from 'lucide-react';
import { AppSettings } from '../types';

interface SettingsTabProps {
  settings: AppSettings;
  onUpdateSettings: (newSettings: AppSettings) => void;
  onResetAllData: () => void;
}

export const SettingsTab: React.FC<SettingsTabProps> = ({ settings, onUpdateSettings, onResetAllData }) => {
  const [showResetConfirm, setShowResetConfirm] = useState(false);

  const dnsOptions = [
    { label: 'Cloudflare (1.1.1.1)', value: '1.1.1.1' },
    { label: 'Google (8.8.8.8)', value: '8.8.8.8' },
    { label: 'Quad9 Secure (9.9.9.9)', value: '9.9.9.9' },
  ];

  return (
    <div className="flex flex-col h-full text-[#e6e6e6] select-none overflow-y-auto pr-1 space-y-4">
      {/* Header */}
      <div className="pb-3 border-b border-[#1a1a1a]">
        <h2 className="font-serif text-lg font-bold text-white tracking-wide">System Settings</h2>
        <p className="text-xs text-[#7a7a7a]">Native VpnService & Protection Preferences</p>
      </div>

      {/* Network Behavior */}
      <div className="space-y-2">
        <span className="text-[10px] font-bold text-[#c5a059] uppercase tracking-widest font-mono">Connection Behavior</span>
        <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-3.5 space-y-3 shadow-md shadow-black/30">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-semibold text-white">Auto Reconnect</div>
              <p className="text-[11px] text-[#7a7a7a]">Re-establish tunnel on physical network transitions</p>
            </div>
            <input
              type="checkbox"
              checked={settings.autoReconnect}
              onChange={(e) => onUpdateSettings({ ...settings, autoReconnect: e.target.checked })}
              className="w-4 h-4 accent-[#c5a059] rounded cursor-pointer bg-[#050505]"
            />
          </div>

          <div className="flex items-center justify-between pt-2 border-t border-[#1a1a1a]">
            <div>
              <div className="text-xs font-semibold text-white">Bypass Local LAN</div>
              <p className="text-[11px] text-[#7a7a7a]">Route private IP ranges (192.168.x, 10.x) directly</p>
            </div>
            <input
              type="checkbox"
              checked={settings.bypassLan}
              onChange={(e) => onUpdateSettings({ ...settings, bypassLan: e.target.checked })}
              className="w-4 h-4 accent-[#c5a059] rounded cursor-pointer bg-[#050505]"
            />
          </div>
        </div>
      </div>

      {/* DNS & Privacy */}
      <div className="space-y-2">
        <span className="text-[10px] font-bold text-[#c5a059] uppercase tracking-widest font-mono">Privacy & Leak Protection</span>
        <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-3.5 space-y-3 shadow-md shadow-black/30">
          <div>
            <label className="block text-xs font-semibold text-white mb-1">Encrypted DNS Server</label>
            <select
              value={settings.customDns}
              onChange={(e) => {
                const opt = dnsOptions.find((d) => d.value === e.target.value);
                onUpdateSettings({
                  ...settings,
                  customDns: e.target.value,
                  dnsMode: opt?.label || e.target.value,
                });
              }}
              className="w-full px-3 py-2 rounded-xl bg-[#050505] border border-[#1f1f1f] text-xs text-[#d0d0d0] focus:outline-none focus:border-[#c5a059]"
            >
              {dnsOptions.map((opt) => (
                <option key={opt.value} value={opt.value} className="bg-[#050505] text-white">
                  {opt.label}
                </option>
              ))}
            </select>
            <p className="text-[10px] text-[#666666] mt-1">
              All DNS requests are securely encapsulated through the local tunnel interface.
            </p>
          </div>

          <div className="pt-2 border-t border-[#1a1a1a]">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs font-semibold text-white">IPv6 Safe Fallback</div>
                <p className="text-[11px] text-[#7a7a7a]">Prevent unroutable IPv6 packets from leaking unencrypted</p>
              </div>
              <span className="text-[10px] px-2 py-0.5 rounded-full bg-[#c5a059]/10 text-[#c5a059] border border-[#c5a059]/30 font-mono font-medium">
                Active
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between pt-2 border-t border-[#1a1a1a]">
            <div>
              <div className="text-xs font-semibold text-white">Kill-Switch Protection</div>
              <p className="text-[11px] text-[#7a7a7a]">Block internet if virtual tunnel interface disconnects</p>
            </div>
            <input
              type="checkbox"
              checked={settings.killSwitchEnabled}
              onChange={(e) => onUpdateSettings({ ...settings, killSwitchEnabled: e.target.checked })}
              className="w-4 h-4 accent-[#c5a059] rounded cursor-pointer bg-[#050505]"
            />
          </div>
        </div>
      </div>

      {/* Timeout & Diagnostics */}
      <div className="space-y-2">
        <span className="text-[10px] font-bold text-[#c5a059] uppercase tracking-widest font-mono">Engine Controls</span>
        <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-3.5 space-y-3 shadow-md shadow-black/30">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs font-semibold text-white">Connection Timeout</div>
              <p className="text-[11px] text-[#7a7a7a]">Maximum handshake wait period</p>
            </div>
            <select
              value={settings.connectionTimeoutSeconds}
              onChange={(e) => onUpdateSettings({ ...settings, connectionTimeoutSeconds: Number(e.target.value) })}
              className="px-2.5 py-1 rounded-xl bg-[#050505] border border-[#1f1f1f] text-xs text-[#d0d0d0] focus:outline-none focus:border-[#c5a059]"
            >
              <option value={15} className="bg-[#050505]">15s</option>
              <option value={20} className="bg-[#050505]">20s</option>
              <option value={30} className="bg-[#050505]">30s</option>
              <option value={60} className="bg-[#050505]">60s</option>
            </select>
          </div>
        </div>
      </div>

      {/* Factory Reset */}
      <div className="pt-2 pb-4">
        <button
          id="reset-application-data-button"
          onClick={() => setShowResetConfirm(true)}
          className="w-full py-2.5 rounded-2xl bg-red-950/30 hover:bg-red-950/50 border border-red-800/40 text-red-400 text-xs font-bold transition-all flex items-center justify-center gap-2 shadow-sm"
        >
          <RotateCcw className="w-3.5 h-3.5" />
          <span>Reset Application Data</span>
        </button>
      </div>

      {/* Reset Confirmation Dialog */}
      {showResetConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
          <div className="bg-[#0a0a0a] border border-[#1f1f1f] rounded-3xl w-full max-w-sm p-5 text-center shadow-2xl shadow-black">
            <div className="w-10 h-10 rounded-full bg-red-950/40 border border-red-800/40 text-red-400 flex items-center justify-center mx-auto mb-3">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <h3 className="font-serif text-base font-bold text-white mb-1 tracking-wide">Reset Application Data?</h3>
            <p className="text-xs text-[#7a7a7a] mb-5">
              This will remove all stored profiles, cached configurations, and restore system settings to default.
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => setShowResetConfirm(false)}
                className="flex-1 py-2 rounded-xl bg-[#141414] text-xs font-semibold text-[#b0b0b0] hover:bg-[#1a1a1a]"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  onResetAllData();
                  setShowResetConfirm(false);
                }}
                className="flex-1 py-2 rounded-xl bg-red-700 hover:bg-red-600 text-xs font-bold text-white shadow-lg"
              >
                Confirm Reset
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
