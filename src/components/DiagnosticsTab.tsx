import React, { useState } from 'react';
import { Copy, Trash2, Check, ShieldCheck, Terminal } from 'lucide-react';
import { DiagnosticLog, LogLevel } from '../types';

interface DiagnosticsTabProps {
  logs: DiagnosticLog[];
  onClearLogs: () => void;
}

export const DiagnosticsTab: React.FC<DiagnosticsTabProps> = ({ logs, onClearLogs }) => {
  const [selectedLevel, setSelectedLevel] = useState<LogLevel | 'ALL'>('ALL');
  const [copied, setCopied] = useState(false);

  const filteredLogs = selectedLevel === 'ALL' ? logs : logs.filter((l) => l.level === selectedLevel);

  const handleCopy = () => {
    const text = logs
      .map((l) => `[${new Date(l.timestamp).toISOString()}] [${l.level}] [${l.tag}] ${l.message}`)
      .join('\n');
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getLevelColor = (level: LogLevel) => {
    switch (level) {
      case 'DEBUG':
        return 'text-[#666666]';
      case 'INFO':
        return 'text-[#c5a059]';
      case 'WARN':
        return 'text-[#e5c378]';
      case 'ERROR':
        return 'text-red-400';
    }
  };

  return (
    <div className="flex flex-col h-full text-[#e6e6e6] select-none">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-[#1a1a1a]">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="font-serif text-lg font-bold text-white tracking-wide">Diagnostic Stream</h2>
            <span className="flex items-center gap-1 text-[10px] text-[#c5a059] bg-[#c5a059]/10 px-2 py-0.5 rounded-full border border-[#c5a059]/30 font-mono font-medium">
              <ShieldCheck className="w-3 h-3" />
              Sanitized
            </span>
          </div>
          <p className="text-xs text-[#7a7a7a]">{filteredLogs.length} real-time system events</p>
        </div>

        <div className="flex items-center gap-1.5">
          <button
            id="copy-logs-button"
            onClick={handleCopy}
            title="Copy Logs"
            className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl bg-[#0e0e0e] border border-[#1f1f1f] hover:bg-[#191919] text-xs font-medium text-[#b0b0b0] transition-colors"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-[#c5a059]" /> : <Copy className="w-3.5 h-3.5 text-[#8a8a8a]" />}
            <span>{copied ? 'Copied' : 'Copy'}</span>
          </button>
          <button
            id="clear-logs-button"
            onClick={onClearLogs}
            title="Clear Logs"
            className="p-1.5 rounded-xl bg-[#0e0e0e] border border-[#1f1f1f] hover:bg-red-950/40 text-[#7a7a7a] hover:text-red-400 transition-colors"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Filter Chips */}
      <div className="flex items-center gap-2 my-3 overflow-x-auto pb-1">
        {(['ALL', 'INFO', 'WARN', 'ERROR', 'DEBUG'] as const).map((lvl) => (
          <button
            key={lvl}
            onClick={() => setSelectedLevel(lvl)}
            className={`px-3 py-1 rounded-full text-xs font-semibold transition-colors font-mono ${
              selectedLevel === lvl
                ? 'bg-[#c5a059] text-[#050505] shadow-sm shadow-[#c5a059]/20'
                : 'bg-[#0e0e0e] border border-[#1f1f1f] text-[#7a7a7a] hover:text-white'
            }`}
          >
            {lvl}
          </button>
        ))}
      </div>

      {/* Terminal View */}
      <div className="flex-1 bg-[#050505] border border-[#1a1a1a] rounded-2xl p-3 overflow-y-auto font-mono text-[11px] leading-relaxed space-y-1.5 shadow-inner shadow-black">
        {filteredLogs.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-[#555555] font-sans">
            <Terminal className="w-8 h-8 mb-2 opacity-50 text-[#c5a059]" />
            <span>No events recorded for this level filter</span>
          </div>
        ) : (
          filteredLogs.map((log) => (
            <div key={log.id} className="flex items-start gap-2 hover:bg-[#0e0e0e] px-1 py-0.5 rounded">
              <span className="text-[#555555] text-[10px] shrink-0 font-mono">
                {new Date(log.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })}
              </span>
              <span className={`font-bold shrink-0 text-[10px] ${getLevelColor(log.level)}`}>
                [{log.level}]
              </span>
              <span className="text-[#c5a059] shrink-0 font-medium text-[10px]">
                &lt;{log.tag}&gt;
              </span>
              <span className="text-[#cccccc] break-all">{log.message}</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
