import React, { useState } from 'react';
import { Power, Wifi, WifiOff, ArrowDown, ArrowUp, Clock, ChevronDown, Check, AlertCircle, Shield } from 'lucide-react';
import { NetworkProfile, ProxyNode, VpnConnectionState, VpnStats } from '../types';
import { maskServerAddress } from '../utils/yamlParser';

interface DashboardTabProps {
  vpnState: VpnConnectionState;
  stats: VpnStats;
  isOnline: boolean;
  activeProfile: NetworkProfile | null;
  selectedNode: ProxyNode | null;
  onToggleConnection: () => void;
  onSelectNode: (node: ProxyNode) => void;
  onNavigateToProfiles: () => void;
}

export const DashboardTab: React.FC<DashboardTabProps> = ({
  vpnState,
  stats,
  isOnline,
  activeProfile,
  selectedNode,
  onToggleConnection,
  onSelectNode,
  onNavigateToProfiles,
}) => {
  const [showNodePicker, setShowNodePicker] = useState(false);

  const isConnected = vpnState.type === 'CONNECTED';
  const isConnecting = vpnState.type === 'CONNECTING';
  const isError = vpnState.type === 'ERROR';

  const formatSpeed = (bytes: number) => {
    if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB/s`;
    if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB/s`;
    return `${bytes} B/s`;
  };

  const formatDuration = (secs: number) => {
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = secs % 60;
    if (h > 0) {
      return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    }
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  return (
    <div className="flex flex-col items-center justify-between min-h-[580px] w-full text-[#e6e6e6] select-none">
      {/* Top Bar */}
      <div className="w-full flex items-center justify-between pt-2 pb-3.5 px-1 border-b border-[#1a1a1a]">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-serif italic font-bold text-xl tracking-wide text-[#c5a059]">Ajaz<span className="text-white not-italic mx-0.5">×</span>tiktok</span>
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-[#c5a059]/10 text-[#c5a059] border border-[#c5a059]/30 font-mono font-medium">
              VpnService
            </span>
          </div>
          <p className="text-[11px] text-[#7a7a7a] tracking-wide">Private Network Suite</p>
        </div>

        {/* Network status indicator */}
        <div
          id="network-status-badge"
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-[#0e0e0e] border border-[#1f1f1f] text-xs font-medium text-[#b0b0b0] shadow-sm"
        >
          {isOnline ? (
            <>
              <span className="w-1.5 h-1.5 rounded-full bg-[#c5a059] animate-pulse" />
              <Wifi className="w-3.5 h-3.5 text-[#c5a059]" />
              <span className="text-[11px]">Wi-Fi Online</span>
            </>
          ) : (
            <>
              <span className="w-1.5 h-1.5 rounded-full bg-red-500" />
              <WifiOff className="w-3.5 h-3.5 text-red-400" />
              <span className="text-[11px]">Offline</span>
            </>
          )}
        </div>
      </div>

      {/* Main Connection Halo Section */}
      <div className="flex flex-col items-center my-6">
        <div className="relative flex items-center justify-center">
          {/* Outer Pulse Rings */}
          {isConnected && (
            <>
              <div className="absolute w-44 h-44 rounded-full bg-[#c5a059]/10 animate-ping duration-1000" />
              <div className="absolute w-36 h-36 rounded-full bg-[#c5a059]/15 animate-pulse" />
            </>
          )}
          {isConnecting && (
            <div className="absolute w-36 h-36 rounded-full bg-[#c5a059]/15 animate-spin border-2 border-dashed border-[#c5a059]" />
          )}

          {/* Central Luxury Button */}
          <button
            id="vpn-toggle-button"
            onClick={onToggleConnection}
            className={`relative z-10 w-28 h-28 rounded-full flex flex-col items-center justify-center transition-all duration-300 shadow-2xl focus:outline-none ${
              isConnected
                ? 'bg-gradient-to-b from-[#e5c378] to-[#9c7832] text-[#050505] ring-4 ring-[#c5a059]/40 shadow-xl shadow-[#c5a059]/30 scale-105'
                : isConnecting
                ? 'bg-gradient-to-b from-[#1c1811] to-[#0c0c0c] text-[#c5a059] ring-4 ring-[#c5a059]/40 shadow-lg shadow-black'
                : 'bg-gradient-to-b from-[#141414] to-[#0a0a0a] text-[#b0b0b0] hover:text-white ring-1 ring-[#262626] hover:ring-[#c5a059]/60 hover:shadow-lg hover:shadow-black'
            }`}
          >
            {isConnecting ? (
              <div className="w-8 h-8 border-2 border-[#c5a059] border-t-transparent rounded-full animate-spin" />
            ) : (
              <Power className={`w-10 h-10 ${isConnected ? 'text-[#050505] stroke-[2.5]' : 'text-[#c5a059]'}`} />
            )}
            <span className={`text-[10px] uppercase font-bold tracking-widest mt-1 font-mono ${isConnected ? 'text-[#050505]' : 'text-[#8a8a8a]'}`}>
              {isConnected ? 'Active' : isConnecting ? 'Securing' : 'Connect'}
            </span>
          </button>
        </div>

        {/* Status Text & Subtitle */}
        <div className="text-center mt-5">
          {isConnected && (
            <div>
              <div className="inline-flex items-center gap-1.5 px-3 py-0.5 rounded-full bg-[#c5a059]/15 border border-[#c5a059]/30 text-[#c5a059] text-xs font-semibold uppercase tracking-wider mb-1">
                <Shield className="w-3 h-3 text-[#c5a059]" />
                <span>Protected & Routed</span>
              </div>
              <h3 className="font-serif text-base font-semibold text-white">{vpnState.serverName}</h3>
              <p className="text-xs text-[#7a7a7a] font-mono mt-0.5">{vpnState.serverAddress}</p>
            </div>
          )}

          {isConnecting && (
            <div>
              <span className="text-xs font-semibold text-[#c5a059] uppercase tracking-wider">Securing Tunnel...</span>
              <p className="text-xs text-[#9a9a9a] mt-1 max-w-[260px]">{vpnState.message}</p>
            </div>
          )}

          {isError && (
            <div className="px-4 py-2 rounded-xl bg-red-950/30 border border-red-800/40 max-w-[280px]">
              <div className="flex items-center justify-center gap-1 text-red-400 text-xs font-semibold mb-0.5">
                <AlertCircle className="w-3.5 h-3.5" />
                <span>Connection Alert</span>
              </div>
              <p className="text-xs text-red-300">{vpnState.message}</p>
              {vpnState.recoveryAction && (
                <p className="text-[11px] text-[#7a7a7a] mt-1 italic">{vpnState.recoveryAction}</p>
              )}
            </div>
          )}

          {vpnState.type === 'DISCONNECTED' && (
            <div>
              <span className="text-xs font-semibold text-[#8a8a8a] uppercase tracking-widest">Disconnected</span>
              <p className="text-xs text-[#666666] mt-0.5">
                {activeProfile ? `Profile: ${activeProfile.name}` : 'No profile configured'}
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Traffic Statistics */}
      <div className="w-full grid grid-cols-3 gap-2 my-2">
        <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-2.5 flex flex-col items-center justify-center shadow-md shadow-black/40">
          <div className="flex items-center gap-1 text-[#c5a059] text-[11px] font-medium mb-1">
            <ArrowDown className="w-3 h-3" />
            <span>Download</span>
          </div>
          <span className="font-mono font-bold text-sm text-white">{formatSpeed(stats.speedInBps)}</span>
          <span className="text-[9px] text-[#666666] mt-0.5 font-mono">{(stats.bytesIn / (1024 * 1024)).toFixed(2)} MB total</span>
        </div>

        <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-2.5 flex flex-col items-center justify-center shadow-md shadow-black/40">
          <div className="flex items-center gap-1 text-[#d4af37] text-[11px] font-medium mb-1">
            <ArrowUp className="w-3 h-3" />
            <span>Upload</span>
          </div>
          <span className="font-mono font-bold text-sm text-white">{formatSpeed(stats.speedOutBps)}</span>
          <span className="text-[9px] text-[#666666] mt-0.5 font-mono">{(stats.bytesOut / (1024 * 1024)).toFixed(2)} MB total</span>
        </div>

        <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-2.5 flex flex-col items-center justify-center shadow-md shadow-black/40">
          <div className="flex items-center gap-1 text-[#8a8a8a] text-[11px] font-medium mb-1">
            <Clock className="w-3 h-3" />
            <span>Duration</span>
          </div>
          <span className="font-mono font-bold text-sm text-white">{formatDuration(stats.durationSeconds)}</span>
          <span className="text-[9px] text-[#c5a059] mt-0.5 font-mono">{stats.latencyMs > 0 ? `${stats.latencyMs} ms` : 'Active'}</span>
        </div>
      </div>

      {/* Profile & Server Selector Card */}
      <div className="w-full bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-3.5 relative shadow-md shadow-black/40">
        <div className="flex items-center justify-between mb-2">
          <span className="text-[10px] font-bold text-[#c5a059] uppercase tracking-widest font-mono">Active Profile</span>
          <button
            id="manage-profiles-button"
            onClick={onNavigateToProfiles}
            className="text-xs text-[#8a8a8a] hover:text-[#c5a059] transition-colors"
          >
            Manage Profiles →
          </button>
        </div>

        {activeProfile ? (
          <div>
            <div className="flex items-center justify-between">
              <div>
                <h4 className="font-serif text-sm font-semibold text-white tracking-wide">{activeProfile.name}</h4>
                <p className="text-xs text-[#7a7a7a]">
                  {activeProfile.proxyCount} servers • CLASH YAML
                </p>
              </div>
              <button
                id="select-server-dropdown-toggle"
                onClick={() => setShowNodePicker(!showNodePicker)}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#141414] hover:bg-[#1a1a1a] text-xs font-medium text-[#d0d0d0] border border-[#262626] transition-colors"
              >
                <span>{selectedNode ? selectedNode.name : 'Select Node'}</span>
                <ChevronDown className="w-3.5 h-3.5 text-[#8a8a8a]" />
              </button>
            </div>

            {/* Collapsible node list */}
            {showNodePicker && activeProfile.proxies.length > 0 && (
              <div className="mt-3 max-h-40 overflow-y-auto rounded-xl bg-[#070707] border border-[#1f1f1f] p-1.5 space-y-1">
                {activeProfile.proxies.map((node) => {
                  const isSelected = selectedNode?.id === node.id;
                  return (
                    <button
                      key={node.id}
                      onClick={() => {
                        onSelectNode(node);
                        setShowNodePicker(false);
                      }}
                      className={`w-full text-left px-2.5 py-1.5 rounded-lg text-xs flex items-center justify-between transition-colors ${
                        isSelected
                          ? 'bg-[#1c1811] text-[#c5a059] font-medium border border-[#c5a059]/40'
                          : 'hover:bg-[#121212] text-[#b0b0b0]'
                      }`}
                    >
                      <div>
                        <div className="font-medium text-white">{node.name}</div>
                        <div className="text-[10px] text-[#666666] font-mono">
                          {node.type} • {maskServerAddress(node.server, node.port)}
                        </div>
                      </div>
                      {isSelected && <Check className="w-3.5 h-3.5 text-[#c5a059]" />}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        ) : (
          <div className="flex items-center justify-between py-1">
            <span className="text-xs text-[#7a7a7a]">No profile imported yet</span>
            <button
              onClick={onNavigateToProfiles}
              className="px-3 py-1 rounded-xl bg-[#c5a059] text-[#050505] text-xs font-bold hover:bg-[#d4af37] transition-colors shadow-sm shadow-[#c5a059]/20"
            >
              + Import Config
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
