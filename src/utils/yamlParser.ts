import * as yaml from 'js-yaml';
import { NetworkProfile, ProxyNode, ProxyType } from '../types';

export function parseClashConfig(rawText: string, profileName: string = 'Subscription', sourceUrl?: string): NetworkProfile {
  let textToParse = (rawText || '').trim();
  const id = 'prof_' + Math.random().toString(36).substring(2, 9);

  if (!textToParse) {
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

  // 1. Check if the payload is a Base64-encoded subscription string
  if (!textToParse.includes('proxies:') && !textToParse.includes('server:') && isLikelyBase64(textToParse)) {
    try {
      const decoded = decodeBase64Safe(textToParse);
      if (decoded && decoded.trim().length > 0) {
        textToParse = decoded.trim();
      }
    } catch {}
  }

  // 2. Try standard YAML parsing (Clash / Mihomo format)
  try {
    const parsed = yaml.load(textToParse) as any;
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

      if (proxiesList.length > 0) {
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
          isValid: true,
          validationMessage: `Parsed ${proxiesList.length} endpoints successfully`,
        };
      }
    }
  } catch (err: any) {
    console.warn('YAML parser warning:', err);
  }

  // 3. Try URI line parsing (vmess://, vless://, trojan://, ss://, socks5://, etc.)
  const uriNodes = parseUriList(textToParse);
  if (uriNodes.length > 0) {
    return {
      id,
      name: profileName,
      sourceUrl,
      rawConfig: rawText,
      proxyCount: uriNodes.length,
      proxies: uriNodes,
      proxyGroups: [],
      selectedProxyId: uriNodes[0]?.id,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      isValid: true,
      validationMessage: `Parsed ${uriNodes.length} endpoints from subscription links`,
    };
  }

  // 4. Fallback: line by line search
  const fallbackNodes = parseFallbackLines(textToParse);
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

    const wsOpts = item['ws-opts'] || item['ws-headers'] || {};
    const path = item['ws-path'] || item.path || wsOpts.path || undefined;
    const host = item.host || wsOpts.headers?.Host || wsOpts.headers?.host || undefined;
    const sni = item.sni || item.servername || item['server-name'] || undefined;

    const realityOpts = item['reality-opts'] || {};
    const realityPublicKey = realityOpts['public-key'] || item['public-key'] || undefined;
    const realityShortId = realityOpts['short-id'] || item['short-id'] || undefined;

    const alpn = Array.isArray(item.alpn) ? item.alpn.map(String) : undefined;
    const wsHeaders = wsOpts.headers && typeof wsOpts.headers === 'object' ? wsOpts.headers : undefined;

    return {
      id: 'node_' + Math.random().toString(36).substring(2, 9),
      name,
      type,
      server,
      port,
      cipher: item.cipher ? String(item.cipher) : undefined,
      password: item.password ? String(item.password) : undefined,
      uuid: item.uuid || item.password || item.username ? String(item.uuid || item.password || item.username) : undefined,
      alterId: item.alterId !== undefined ? Number(item.alterId) : undefined,
      network: item.network ? String(item.network) : (item['ws-opts'] ? 'ws' : undefined),
      tls: Boolean(item.tls || item.security === 'tls' || item.security === 'reality'),
      sni: sni ? String(sni) : undefined,
      host: host ? String(host) : undefined,
      path: path ? String(path) : undefined,
      wsHeaders,
      alpn,
      realityPublicKey: realityPublicKey ? String(realityPublicKey) : undefined,
      realityShortId: realityShortId ? String(realityShortId) : undefined,
      skipCertVerify: Boolean(item['skip-cert-verify'] || item.skipCertVerify),
      udp: item.udp !== false,
      latencyMs: Math.floor(Math.random() * 40) + 25,
      isOnline: true,
    };
  } catch {
    return null;
  }
}

function parseUriList(text: string): ProxyNode[] {
  const nodes: ProxyNode[] = [];
  const lines = text.split('\n');

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;

    try {
      if (line.startsWith('vmess://')) {
        const base64Data = line.substring(8).trim();
        const jsonStr = decodeBase64Safe(base64Data);
        const json = JSON.parse(jsonStr);
        if (json.add) {
          nodes.push({
            id: 'node_' + Math.random().toString(36).substring(2, 9),
            name: json.ps || 'VMess Endpoint',
            type: 'VMess',
            server: String(json.add).trim(),
            port: Number(json.port) || 443,
            uuid: json.id ? String(json.id) : undefined,
            network: json.net || 'tcp',
            tls: json.tls === 'tls',
            host: json.host || undefined,
            path: json.path || undefined,
            sni: json.sni || json.host || undefined,
            cipher: json.type || 'auto',
            alterId: Number(json.aid) || 0,
            skipCertVerify: Boolean(json.verify_cert === false || json.skipCertVerify),
            latencyMs: Math.floor(Math.random() * 40) + 25,
            isOnline: true,
          });
        }
      } else if (line.startsWith('vless://')) {
        const url = new URL(line);
        nodes.push({
          id: 'node_' + Math.random().toString(36).substring(2, 9),
          name: url.hash ? decodeURIComponent(url.hash.substring(1)) : 'VLESS Endpoint',
          type: 'VLess',
          server: url.hostname,
          port: Number(url.port) || 443,
          uuid: url.username || undefined,
          network: url.searchParams.get('type') || 'tcp',
          tls: url.searchParams.get('security') === 'tls' || url.searchParams.get('security') === 'reality',
          sni: url.searchParams.get('sni') || url.searchParams.get('peer') || undefined,
          host: url.searchParams.get('host') || undefined,
          path: url.searchParams.get('path') || undefined,
          realityPublicKey: url.searchParams.get('pbk') || undefined,
          realityShortId: url.searchParams.get('sid') || undefined,
          alpn: url.searchParams.get('alpn') ? url.searchParams.get('alpn')!.split(',') : undefined,
          skipCertVerify: url.searchParams.get('allowInsecure') === '1',
          latencyMs: Math.floor(Math.random() * 40) + 25,
          isOnline: true,
        });
      } else if (line.startsWith('trojan://')) {
        const url = new URL(line);
        nodes.push({
          id: 'node_' + Math.random().toString(36).substring(2, 9),
          name: url.hash ? decodeURIComponent(url.hash.substring(1)) : 'Trojan Endpoint',
          type: 'Trojan',
          server: url.hostname,
          port: Number(url.port) || 443,
          password: url.username || url.password || undefined,
          network: url.searchParams.get('type') || 'tcp',
          tls: true,
          sni: url.searchParams.get('sni') || url.searchParams.get('peer') || undefined,
          host: url.searchParams.get('host') || undefined,
          path: url.searchParams.get('path') || undefined,
          alpn: url.searchParams.get('alpn') ? url.searchParams.get('alpn')!.split(',') : undefined,
          skipCertVerify: url.searchParams.get('allowInsecure') === '1',
          latencyMs: Math.floor(Math.random() * 40) + 25,
          isOnline: true,
        });
      } else if (line.startsWith('ss://')) {
        let afterScheme = line.substring(5);
        let tag = 'Shadowsocks Endpoint';
        if (afterScheme.includes('#')) {
          const parts = afterScheme.split('#');
          afterScheme = parts[0];
          tag = decodeURIComponent(parts[1]);
        }

        if (afterScheme.includes('@')) {
          // Format: base64(method:pass)@host:port
          const [userInfoEncoded, hostPort] = afterScheme.split('@');
          const [server, portStr] = hostPort.split(':');
          const decodedUserInfo = decodeBase64Safe(userInfoEncoded);
          const [cipher, password] = decodedUserInfo.split(':');

          nodes.push({
            id: 'node_' + Math.random().toString(36).substring(2, 9),
            name: tag,
            type: 'Shadowsocks',
            server,
            port: Number(portStr) || 8388,
            cipher: cipher || 'aes-256-gcm',
            password: password || '',
            latencyMs: Math.floor(Math.random() * 40) + 25,
            isOnline: true,
          });
        } else {
          // Format: base64(method:pass@host:port)
          const decoded = decodeBase64Safe(afterScheme);
          if (decoded.includes('@')) {
            const [methodPass, hostPort] = decoded.split('@');
            const [cipher, password] = methodPass.split(':');
            const [server, portStr] = hostPort.split(':');

            nodes.push({
              id: 'node_' + Math.random().toString(36).substring(2, 9),
              name: tag,
              type: 'Shadowsocks',
              server,
              port: Number(portStr) || 8388,
              cipher: cipher || 'aes-256-gcm',
              password: password || '',
              latencyMs: Math.floor(Math.random() * 40) + 25,
              isOnline: true,
            });
          }
        }
      } else if (line.startsWith('hysteria2://') || line.startsWith('hy2://')) {
        const scheme = line.startsWith('hysteria2://') ? 'hysteria2://' : 'hy2://';
        const url = new URL(line.replace('hy2://', 'https://').replace('hysteria2://', 'https://'));
        nodes.push({
          id: 'node_' + Math.random().toString(36).substring(2, 9),
          name: url.hash ? decodeURIComponent(url.hash.substring(1)) : 'Hysteria 2 Endpoint',
          type: 'Hysteria2',
          server: url.hostname,
          port: Number(url.port) || 443,
          password: url.username || url.password || undefined,
          tls: true,
          sni: url.searchParams.get('sni') || undefined,
          skipCertVerify: url.searchParams.get('insecure') === '1',
          latencyMs: Math.floor(Math.random() * 40) + 25,
          isOnline: true,
        });
      }
    } catch {}
  }

  return nodes;
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

function isLikelyBase64(s: string): boolean {
  const clean = s.replace(/[\r\n\s]/g, '');
  if (clean.length < 16) return false;
  return /^[A-Za-z0-9+/=_-]+$/.test(clean);
}

function decodeBase64Safe(s: string): string {
  const clean = s.replace(/[\r\n\s]/g, '').replace(/-/g, '+').replace(/_/g, '/');
  const padLen = (4 - (clean.length % 4)) % 4;
  const padded = clean + '='.repeat(padLen);
  if (typeof atob === 'function') {
    return atob(padded);
  } else if (typeof Buffer !== 'undefined') {
    return Buffer.from(padded, 'base64').toString('utf-8');
  }
  return '';
}

export function maskServerAddress(server: string, port: number): string {
  if (server.length > 8) {
    return `${server.substring(0, 4)}***${server.substring(server.length - 4)}:${port}`;
  }
  return `${server}:${port}`;
}
