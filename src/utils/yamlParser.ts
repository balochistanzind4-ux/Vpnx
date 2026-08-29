import * as yaml from 'js-yaml';
import { NetworkProfile, ProxyNode, ProxyType } from '../types';

export function parseClashConfig(rawText: string, profileName: string = 'Subscription', sourceUrl?: string): NetworkProfile {
  const trimmed = (rawText || '').trim();
  const id = 'prof_' + Math.random().toString(36).substring(2, 9);

  if (!trimmed) {
    return {
      id,
      name: profileName,
      sourceUrl,
      rawConfig: rawText,
      proxyCount: 0,
      proxies: [],
      proxyGroups: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
      isValid: false,
      validationMessage: 'Configuration content is empty',
    };
  }

  try {
    const parsed = yaml.load(trimmed) as any;
    if (parsed && typeof parsed === 'object') {
      const proxiesList: ProxyNode[] = [];
      const groupsList: string[] = [];

      if (Array.isArray(parsed.proxies)) {
        for (const item of parsed.proxies) {
          if (item && typeof item === 'object') {
            const node = parseProxyItem(item);
            if (node) proxiesList.push(node);
          }
        }
      }

      if (Array.isArray(parsed['proxy-groups'])) {
        for (const grp of parsed['proxy-groups']) {
          if (grp && typeof grp === 'object' && grp.name) {
            groupsList.push(String(grp.name));
          }
        }
      }

      const isValid = proxiesList.length > 0;
      return {
        id,
        name: profileName,
        sourceUrl,
        rawConfig: rawText,
        proxyCount: proxiesList.length,
        proxies: proxiesList,
        proxyGroups: groupsList,
        selectedProxyId: proxiesList[0]?.id,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        isValid,
        validationMessage: isValid
          ? `Parsed ${proxiesList.length} endpoints successfully`
          : 'Valid YAML syntax but found 0 supported proxy definitions',
      };
    }
  } catch (err: any) {
    console.warn('YAML parser warning:', err);
  }

  // Fallback: line by line search
  const fallbackNodes = parseFallbackLines(trimmed);
  const isValid = fallbackNodes.length > 0;

  return {
    id,
    name: profileName,
    sourceUrl,
    rawConfig: rawText,
    proxyCount: fallbackNodes.length,
    proxies: fallbackNodes,
    proxyGroups: [],
    selectedProxyId: fallbackNodes[0]?.id,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    isValid,
    validationMessage: isValid
      ? `Extracted ${fallbackNodes.length} endpoints via fallback parser`
      : 'Invalid configuration format: No valid proxy definitions found',
  };
}

function parseProxyItem(item: any): ProxyNode | null {
  try {
    const server = String(item.server || '').trim();
    if (!server) return null;

    const name = String(item.name || 'Endpoint').trim();
    const port = Number(item.port) || 443;
    const typeStr = String(item.type || '').toLowerCase();

    let type: ProxyType = 'Custom';
    if (typeStr.includes('ss') || typeStr.includes('shadowsocks')) type = 'Shadowsocks';
    else if (typeStr.includes('vmess')) type = 'VMess';
    else if (typeStr.includes('trojan')) type = 'Trojan';
    else if (typeStr.includes('vless')) type = 'VLess';
    else if (typeStr.includes('hy2') || typeStr.includes('hysteria2')) type = 'Hysteria2';
    else if (typeStr.includes('wg') || typeStr.includes('wireguard')) type = 'WireGuard';
    else if (typeStr.includes('socks')) type = 'Socks5';
    else if (typeStr.includes('http')) type = 'HTTP';
    else if (typeStr.includes('direct')) type = 'Direct';

    return {
      id: 'node_' + Math.random().toString(36).substring(2, 9),
      name,
      type,
      server,
      port,
      cipher: item.cipher ? String(item.cipher) : undefined,
      network: item.network ? String(item.network) : undefined,
      tls: Boolean(item.tls),
      sni: item.sni || item.servername ? String(item.sni || item.servername) : undefined,
      host: item.host ? String(item.host) : undefined,
      path: item['ws-path'] || item.path ? String(item['ws-path'] || item.path) : undefined,
      udp: item.udp !== false,
      latencyMs: Math.floor(Math.random() * 60) + 25,
      isOnline: true,
    };
  } catch {
    return null;
  }
}

function parseFallbackLines(text: string): ProxyNode[] {
  const nodes: ProxyNode[] = [];
  const lines = text.split('\n');

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('- {') && trimmed.includes('name:') && trimmed.includes('server:')) {
      try {
        const itemContent = trimmed.replace(/^-/, '').trim();
        const parsed = yaml.load(itemContent) as any;
        const node = parseProxyItem(parsed);
        if (node) nodes.push(node);
      } catch {}
    }
  }

  return nodes;
}

export function maskServerAddress(server: string, port: number): string {
  if (server.length > 8) {
    return `${server.substring(0, 4)}***${server.substring(server.length - 4)}:${port}`;
  }
  return `${server}:${port}`;
}
