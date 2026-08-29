import React, { useState } from 'react';
import { Plus, RefreshCw, Trash2, Edit2, Copy, Link as LinkIcon, FileText, Check, AlertTriangle, Layers } from 'lucide-react';
import { NetworkProfile, ProxyNode } from '../types';
import { maskServerAddress } from '../utils/yamlParser';

interface ProfilesTabProps {
  profiles: NetworkProfile[];
  activeProfileId: string | null;
  isImporting: boolean;
  onSelectActiveProfile: (id: string) => void;
  onImportFromUrl: (url: string, name: string) => Promise<void>;
  onImportFromText: (text: string, name: string) => void;
  onRefreshProfile: (profile: NetworkProfile) => void;
  onRenameProfile: (id: string, newName: string) => void;
  onDuplicateProfile: (id: string) => void;
  onDeleteProfile: (id: string) => void;
}

export const ProfilesTab: React.FC<ProfilesTabProps> = ({
  profiles,
  activeProfileId,
  isImporting,
  onSelectActiveProfile,
  onImportFromUrl,
  onImportFromText,
  onRefreshProfile,
  onRenameProfile,
  onDuplicateProfile,
  onDeleteProfile,
}) => {
  const [showImportModal, setShowImportModal] = useState(false);
  const [importTab, setImportTab] = useState<'url' | 'text'>('url');
  const [profileNameInput, setProfileNameInput] = useState('');
  const [urlInput, setUrlInput] = useState('');
  const [yamlInput, setYamlInput] = useState('');
  const [editingProfileId, setEditingProfileId] = useState<string | null>(null);
  const [renameInput, setRenameInput] = useState('');
  const [inspectProfile, setInspectProfile] = useState<NetworkProfile | null>(null);

  const handleImportSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const finalName = profileNameInput.trim() || (importTab === 'url' ? 'Clash Subscription' : 'Custom Config');

    if (importTab === 'url' && urlInput.trim()) {
      await onImportFromUrl(urlInput.trim(), finalName);
      setUrlInput('');
      setProfileNameInput('');
      setShowImportModal(false);
    } else if (importTab === 'text' && yamlInput.trim()) {
      onImportFromText(yamlInput.trim(), finalName);
      setYamlInput('');
      setProfileNameInput('');
      setShowImportModal(false);
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target?.result as string;
      if (content) {
        onImportFromText(content, file.name.replace(/\.[^/.]+$/, ''));
      }
    };
    reader.readAsText(file);
  };

  return (
    <div className="flex flex-col h-full text-[#e6e6e6] select-none">
      {/* Header */}
      <div className="flex items-center justify-between pb-3.5 border-b border-[#1a1a1a]">
        <div>
          <h2 className="font-serif text-lg font-bold text-white tracking-wide">Profile Repository</h2>
          <p className="text-xs text-[#7a7a7a]">{profiles.length} network profiles configured</p>
        </div>
        <button
          id="open-import-modal-button"
          onClick={() => setShowImportModal(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#c5a059] hover:bg-[#d4af37] text-[#050505] font-bold text-xs shadow-md shadow-[#c5a059]/20 transition-all"
        >
          <Plus className="w-4 h-4" />
          <span>Import Profile</span>
        </button>
      </div>

      {/* Loading Bar */}
      {isImporting && (
        <div className="my-3 px-3 py-2 rounded-xl bg-[#14120c] border border-[#c5a059]/40 flex items-center gap-2.5 text-xs text-[#e5c378] animate-pulse">
          <RefreshCw className="w-4 h-4 animate-spin text-[#c5a059]" />
          <span>Downloading and validating Clash YAML configuration...</span>
        </div>
      )}

      {/* Profile List */}
      <div className="flex-1 overflow-y-auto py-3 space-y-3">
        {profiles.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center text-[#7a7a7a]">
            <Layers className="w-12 h-12 text-[#333333] mb-3" />
            <h3 className="font-serif text-sm font-semibold text-[#d0d0d0]">No Profiles Configured</h3>
            <p className="text-xs text-[#666666] max-w-xs mt-1">
              Import a Clash YAML subscription URL (e.g. Ermao Sub) or paste raw text to start routing.
            </p>
            <button
              onClick={() => {
                setUrlInput('https://www.ermao.net/sub/clash/ermao.net');
                setProfileNameInput('Ermao Network');
                setShowImportModal(true);
              }}
              className="mt-4 px-3 py-1.5 rounded-xl bg-[#141414] border border-[#262626] text-xs text-[#c5a059] hover:bg-[#1a1a1a] hover:border-[#c5a059]/40"
            >
              Paste Demo Ermao Subscription
            </button>
          </div>
        ) : (
          profiles.map((profile) => {
            const isActive = profile.id === activeProfileId;
            return (
              <div
                key={profile.id}
                onClick={() => onSelectActiveProfile(profile.id)}
                className={`p-3.5 rounded-2xl border transition-all cursor-pointer shadow-md shadow-black/30 ${
                  isActive
                    ? 'bg-[#0f0e0c] border-[#c5a059] shadow-lg shadow-[#c5a059]/10 ring-1 ring-[#c5a059]/30'
                    : 'bg-[#0c0c0c] border-[#1a1a1a] hover:border-[#2a2a2a]'
                }`}
              >
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-2">
                    <span
                      className={`w-2.5 h-2.5 rounded-full ${
                        isActive ? 'bg-[#c5a059] ring-2 ring-[#c5a059]/30' : 'bg-[#333333]'
                      }`}
                    />
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-serif font-semibold text-sm text-white tracking-wide">{profile.name}</span>
                        {isActive && (
                          <span className="text-[9px] uppercase font-bold px-1.5 py-0.5 rounded bg-[#c5a059]/15 text-[#c5a059] border border-[#c5a059]/30 font-mono">
                            Active
                          </span>
                        )}
                      </div>
                      <p className="text-[11px] text-[#7a7a7a] mt-0.5">
                        {profile.proxyCount} Endpoints • Updated {new Date(profile.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </p>
                    </div>
                  </div>

                  {!profile.isValid && (
                    <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-red-950/40 border border-red-800/40 text-red-400 text-[10px] font-medium">
                      <AlertTriangle className="w-3 h-3" />
                      <span>Invalid</span>
                    </div>
                  )}
                </div>

                {profile.sourceUrl && (
                  <div className="mt-2 text-[10px] font-mono text-[#666666] truncate bg-[#070707] px-2 py-1 rounded border border-[#191919]">
                    {profile.sourceUrl}
                  </div>
                )}

                {/* Bottom Action Row */}
                <div className="mt-3 pt-2.5 border-t border-[#1a1a1a] flex items-center justify-between text-xs text-[#7a7a7a]">
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setInspectProfile(profile);
                    }}
                    className="text-[#c5a059] hover:underline text-[11px] font-medium tracking-wide"
                  >
                    View {profile.proxies.length} Nodes →
                  </button>

                  <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                    {profile.sourceUrl && (
                      <button
                        title="Refresh Subscription"
                        onClick={() => onRefreshProfile(profile)}
                        className="p-1.5 rounded-lg hover:bg-[#191919] text-[#7a7a7a] hover:text-white"
                      >
                        <RefreshCw className="w-3.5 h-3.5" />
                      </button>
                    )}
                    <button
                      title="Rename"
                      onClick={() => {
                        setEditingProfileId(profile.id);
                        setRenameInput(profile.name);
                      }}
                      className="p-1.5 rounded-lg hover:bg-[#191919] text-[#7a7a7a] hover:text-white"
                    >
                      <Edit2 className="w-3.5 h-3.5" />
                    </button>
                    <button
                      title="Duplicate"
                      onClick={() => onDuplicateProfile(profile.id)}
                      className="p-1.5 rounded-lg hover:bg-[#191919] text-[#7a7a7a] hover:text-white"
                    >
                      <Copy className="w-3.5 h-3.5" />
                    </button>
                    <button
                      title="Delete"
                      onClick={() => onDeleteProfile(profile.id)}
                      className="p-1.5 rounded-lg hover:bg-red-950/40 text-[#7a7a7a] hover:text-red-400"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Import Modal */}
      {showImportModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
          <div className="bg-[#0a0a0a] border border-[#1f1f1f] rounded-3xl w-full max-w-md p-5 shadow-2xl shadow-black">
            <h3 className="font-serif text-base font-bold text-white mb-1 tracking-wide">Import Configuration</h3>
            <p className="text-xs text-[#7a7a7a] mb-4">
              Enter a Clash subscription URL or paste raw YAML configuration.
            </p>

            {/* Tabs */}
            <div className="flex bg-[#050505] p-1 rounded-xl border border-[#1f1f1f] mb-4">
              <button
                type="button"
                onClick={() => setImportTab('url')}
                className={`flex-1 py-1.5 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors ${
                  importTab === 'url' ? 'bg-[#c5a059] text-[#050505] shadow' : 'text-[#7a7a7a] hover:text-white'
                }`}
              >
                <LinkIcon className="w-3.5 h-3.5" />
                <span>Subscription URL</span>
              </button>
              <button
                type="button"
                onClick={() => setImportTab('text')}
                className={`flex-1 py-1.5 rounded-lg text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors ${
                  importTab === 'text' ? 'bg-[#c5a059] text-[#050505] shadow' : 'text-[#7a7a7a] hover:text-white'
                }`}
              >
                <FileText className="w-3.5 h-3.5" />
                <span>Paste YAML / Text</span>
              </button>
            </div>

            <form onSubmit={handleImportSubmit} className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-[#b0b0b0] mb-1">Profile Name (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. Ermao Sub / Hong Kong Cluster"
                  value={profileNameInput}
                  onChange={(e) => setProfileNameInput(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#050505] border border-[#1f1f1f] text-xs text-white placeholder-[#555555] focus:outline-none focus:border-[#c5a059]"
                />
              </div>

              {importTab === 'url' ? (
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-xs font-medium text-[#b0b0b0]">Subscription URL</label>
                    <button
                      type="button"
                      onClick={() => setUrlInput('https://www.ermao.net/sub/clash/ermao.net')}
                      className="text-[10px] text-[#c5a059] hover:underline"
                    >
                      Sample Ermao URL
                    </button>
                  </div>
                  <input
                    type="url"
                    required
                    placeholder="https://www.ermao.net/sub/clash/..."
                    value={urlInput}
                    onChange={(e) => setUrlInput(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-[#050505] border border-[#1f1f1f] text-xs text-white placeholder-[#555555] focus:outline-none focus:border-[#c5a059]"
                  />
                  <p className="text-[10px] text-[#666666] mt-1">
                    Accepts text/yaml, text/plain, and application/yaml content types.
                  </p>
                </div>
              ) : (
                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-xs font-medium text-[#b0b0b0]">Raw YAML Config</label>
                    <label className="text-[10px] text-[#c5a059] hover:underline cursor-pointer">
                      Upload File (.yaml/.txt)
                      <input type="file" accept=".yaml,.yml,.txt" onChange={handleFileUpload} className="hidden" />
                    </label>
                  </div>
                  <textarea
                    required
                    rows={5}
                    placeholder="proxies:&#10;  - name: HK-01&#10;    type: ss&#10;    server: 1.2.3.4&#10;    port: 443..."
                    value={yamlInput}
                    onChange={(e) => setYamlInput(e.target.value)}
                    className="w-full px-3 py-2 rounded-xl bg-[#050505] border border-[#1f1f1f] text-xs font-mono text-white placeholder-[#555555] focus:outline-none focus:border-[#c5a059]"
                  />
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowImportModal(false)}
                  className="px-4 py-2 rounded-xl bg-[#141414] text-xs font-semibold text-[#b0b0b0] hover:bg-[#1e1e1e]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isImporting}
                  className="px-4 py-2 rounded-xl bg-[#c5a059] hover:bg-[#d4af37] text-[#050505] text-xs font-bold transition-all disabled:opacity-50 shadow-md shadow-[#c5a059]/20"
                >
                  {isImporting ? 'Processing...' : 'Download & Parse'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Rename Dialog */}
      {editingProfileId && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
          <div className="bg-[#0a0a0a] border border-[#1f1f1f] rounded-2xl w-full max-w-sm p-4 shadow-2xl">
            <h3 className="font-serif text-sm font-bold text-white mb-2 tracking-wide">Rename Profile</h3>
            <input
              type="text"
              value={renameInput}
              onChange={(e) => setRenameInput(e.target.value)}
              className="w-full px-3 py-2 rounded-xl bg-[#050505] border border-[#1f1f1f] text-xs text-white focus:border-[#c5a059] focus:outline-none"
            />
            <div className="flex justify-end gap-2 mt-3">
              <button
                onClick={() => setEditingProfileId(null)}
                className="px-3 py-1.5 rounded-lg bg-[#141414] text-xs text-[#b0b0b0] hover:bg-[#1a1a1a]"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  if (renameInput.trim()) {
                    onRenameProfile(editingProfileId, renameInput.trim());
                  }
                  setEditingProfileId(null);
                }}
                className="px-3 py-1.5 rounded-lg bg-[#c5a059] text-xs font-bold text-[#050505] hover:bg-[#d4af37]"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Node Inspector Modal */}
      {inspectProfile && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/85 backdrop-blur-md">
          <div className="bg-[#0a0a0a] border border-[#1f1f1f] rounded-3xl w-full max-w-md p-5 max-h-[80vh] flex flex-col shadow-2xl">
            <div className="flex items-center justify-between pb-3 border-b border-[#1a1a1a]">
              <div>
                <h3 className="font-serif text-sm font-bold text-white tracking-wide">{inspectProfile.name}</h3>
                <p className="text-xs text-[#7a7a7a]">{inspectProfile.proxies.length} Parsed Endpoints</p>
              </div>
              <button
                onClick={() => setInspectProfile(null)}
                className="p-1 rounded-lg text-[#7a7a7a] hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="flex-1 overflow-y-auto py-3 space-y-2">
              {inspectProfile.proxies.map((node) => (
                <div
                  key={node.id}
                  className="p-2.5 rounded-xl bg-[#070707] border border-[#191919] flex items-center justify-between text-xs"
                >
                  <div>
                    <div className="font-semibold text-white">{node.name}</div>
                    <div className="text-[11px] text-[#7a7a7a] font-mono mt-0.5">
                      <span className="text-[#c5a059]">{node.type}</span> • {maskServerAddress(node.server, node.port)}
                      {node.cipher && ` • ${node.cipher}`}
                    </div>
                  </div>
                  <div className="text-right">
                    <span className="text-[10px] text-[#c5a059] font-mono">
                      {node.latencyMs ? `${node.latencyMs}ms` : 'Ready'}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
