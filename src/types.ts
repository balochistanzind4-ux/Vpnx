export type ProxyType =
  | 'Shadowsocks'
  | 'VMess'
  | 'Trojan'
  | 'VLess'
  | 'Hysteria2'
  | 'WireGuard'
  | 'Socks5'
  | 'HTTP'
  | 'Direct'
  | 'Custom';

export interface ProxyNode {
  id: string;
  name: string;
  type: ProxyType;
  server: string;
  port: number;
  cipher?: string;
  alterId?: number;
  network?: string;
  tls?: boolean;
  sni?: string;
  host?: string;
  path?: string;
  udp?: boolean;
  latencyMs?: number;
  isOnline?: boolean;
}

export interface NetworkProfile {
  id: string;
  name: string;
  sourceUrl?: string;
  rawConfig: string;
  proxyCount: number;
  proxies: ProxyNode[];
  proxyGroups: string[];
  selectedProxyId?: string;
  createdAt: number;
  updatedAt: number;
  isValid: boolean;
  validationMessage?: string;
  subscriptionUserInfo?: string;
}

export type VpnConnectionState =
  | { type: 'DISCONNECTED' }
  | { type: 'CONNECTING'; message: string }
  | { type: 'CONNECTED'; profileName: string; serverName: string; serverAddress: string; connectedSince: number }
  | { type: 'STOPPING' }
  | { type: 'ERROR'; message: string; recoveryAction?: string };

export interface VpnStats {
  bytesIn: number;
  bytesOut: number;
  speedInBps: number;
  speedOutBps: number;
  latencyMs: number;
  durationSeconds: number;
}

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

export interface DiagnosticLog {
  id: string;
  timestamp: number;
  level: LogLevel;
  tag: string;
  message: string;
}

export interface AppSettings {
  autoReconnect: boolean;
  startOnBoot: boolean;
  dnsMode: string;
  customDns: string;
  ipv6Mode: string;
  routingMode: string;
  bypassLan: boolean;
  killSwitchEnabled: boolean;
  connectionTimeoutSeconds: number;
  logLevel: string;
}
