import React, { useState } from 'react';
import { Download, FolderCode, FileCode, Check, Terminal, ShieldCheck, Cpu } from 'lucide-react';
import { generateAndroidProjectZip } from '../utils/androidProjectZip';

export const AndroidProjectInspector: React.FC = () => {
  const [isGeneratingZip, setIsGeneratingZip] = useState(false);
  const [downloadSuccess, setDownloadSuccess] = useState(false);
  const [selectedFile, setSelectedFile] = useState<string>('VlessTransport.kt');

  const fileContents: Record<string, { lang: string; path: string; code: string }> = {
    'VlessTransport.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/transport/VlessTransport.kt',
      code: `package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.network.DnsResolver
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Production VLESS Transport implementation.
 * Supports XTLS / Reality, TLS (SNI + ALPN), WebSocket fallback, and VLESS Addons header parsing.
 */
class VlessTransport(private val node: ProxyNode) : ProxyTransport {
    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        val rawSocket = Socket()
        protectSocket(rawSocket)
        rawSocket.connect(java.net.InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        var streamSocket: Socket = rawSocket
        if (node.tls || node.port == 443) {
            val sslFactory = createSslSocketFactory(node.skipCertVerify)
            val sni = node.sni ?: node.host ?: node.server
            val ssl = sslFactory.createSocket(streamSocket, sni, node.port, true) as SSLSocket
            ssl.startHandshake()
            streamSocket = ssl
        }

        if (node.network.equals("ws", true) || !node.path.isNullOrBlank()) {
            streamSocket = WebSocketStreamWrapper(streamSocket, node.host ?: node.server, node.path ?: "/", node.wsHeaders)
        }

        // Send VLESS Command Frame (UUID + Command 0x01 + Target Port & Host)
        val header = buildVlessHeader(node.uuid ?: "", targetHost, targetPort)
        streamSocket.getOutputStream().write(header)
        streamSocket.getOutputStream().flush()

        // Strip VLESS server response header (Version + Addons length)
        stripVlessResponse(streamSocket.getInputStream())
        streamSocket.soTimeout = 0
        streamSocket
    }
}`,
    },
    'TrojanTransport.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/transport/TrojanTransport.kt',
      code: `package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.network.DnsResolver
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket
import java.security.MessageDigest
import javax.net.ssl.SSLSocket

/**
 * Production Trojan Protocol Transport implementation.
 * Encapsulates Hex(SHA224(Password)) + CRLF + Command 0x01 + ATYP + Address + CRLF.
 */
class TrojanTransport(private val node: ProxyNode) : ProxyTransport {
    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val serverIp = DnsResolver.resolve(node.server, protectSocket)
        val rawSocket = Socket()
        protectSocket(rawSocket)
        rawSocket.connect(java.net.InetSocketAddress(serverIp, node.port), connectTimeoutMs)

        val sslFactory = createSslSocketFactory(node.skipCertVerify)
        val sni = node.sni ?: node.host ?: node.server
        val ssl = sslFactory.createSocket(rawSocket, sni, node.port, true) as SSLSocket
        ssl.startHandshake()

        var streamSocket: Socket = ssl
        if (node.network.equals("ws", true) || !node.path.isNullOrBlank()) {
            streamSocket = WebSocketStreamWrapper(ssl, node.host ?: node.server, node.path ?: "/", node.wsHeaders)
        }

        // Send Trojan Request Header
        val passwordHash = sha224Hex(node.password ?: "")
        val header = buildTrojanHeader(passwordHash, targetHost, targetPort)
        streamSocket.getOutputStream().write(header)
        streamSocket.getOutputStream().flush()

        streamSocket.soTimeout = 0
        streamSocket
    }
}`,
    },
    'DnsResolver.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/network/DnsResolver.kt',
      code: `package com.ajaz.tiktok.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

/**
 * Bypasses local ISP DNS poisoning by sending raw RFC 1035 UDP queries directly
 * over protected DatagramSockets to 1.1.1.1 / 8.8.8.8.
 */
object DnsResolver {
    private val DNS_SERVERS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

    suspend fun resolve(host: String, protectSocket: (Socket) -> Boolean): InetAddress = withContext(Dispatchers.IO) {
        if (isIpAddress(host)) return@withContext InetAddress.getByName(host)

        for (dnsServer in DNS_SERVERS) {
            try {
                val ip = queryUdpDns(host, dnsServer)
                if (ip != null) return@withContext ip
            } catch (_: Exception) {}
        }
        InetAddress.getByName(host)
    }
}`,
    },
    'WebSocketStreamWrapper.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/transport/WebSocketStreamWrapper.kt',
      code: `package com.ajaz.tiktok.core.transport

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * Strict RFC 6455 WebSocket client wrapper for VLESS / Trojan / VMess.
 * Reads HTTP 101 response byte-by-byte without BufferedReader to prevent binary frame truncation.
 */
class WebSocketStreamWrapper(
    private val delegate: Socket,
    private val hostHeader: String,
    private val path: String,
    private val customHeaders: Map<String, String> = emptyMap()
) : Socket() {
    init {
        performHandshake()
    }
    override fun getInputStream(): InputStream = WebSocketInputStream(delegate.getInputStream())
    override fun getOutputStream(): OutputStream = WebSocketOutputStream(delegate.getOutputStream())
}`,
    },
    'AjazVpnService.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/vpn/AjazVpnService.kt',
      code: `package com.ajaz.tiktok.core.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.ajaz.tiktok.AjazApplication
import com.ajaz.tiktok.core.network.ConnectionVerifier
import com.ajaz.tiktok.core.network.VerificationResult
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class AjazVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunEngine: Tun2ProxyEngine? = null

    private fun startTunnel(profileId: String?, nodeId: String?) {
        val app = AjazApplication.instance
        val profile = app.profileStorage.getActiveProfile() ?: return
        val node = profile.proxies.find { it.id == (nodeId ?: profile.selectedProxyId) } ?: profile.proxies.first()

        serviceScope.launch {
            VpnManager.updateState(VpnState.Connecting("Verifying tunnel with \${node.name}..."))
            val verifyResult = ConnectionVerifier.verifyTunnel(node, { socket -> protect(socket) }, 10000)

            val exitIp = when (verifyResult) {
                is VerificationResult.Success -> verifyResult.exitIp ?: node.server
                is VerificationResult.Failure -> {
                    VpnManager.updateState(VpnState.Error(verifyResult.reason, verifyResult.recoverySuggestion))
                    stopSelf()
                    return@launch
                }
            }

            val builder = Builder()
                .setSession("Ajaz×tiktok: \${profile.name}")
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            val pfd = builder.establish() ?: return@launch
            vpnInterface = pfd
            isRunning.set(true)

            tunEngine = Tun2ProxyEngine(
                vpnInterface = pfd,
                proxyNode = node,
                primaryDns = "1.1.1.1",
                protectSocket = { protect(it) },
                protectDatagramSocket = { protect(it) },
                onStatisticsUpdate = { stats -> VpnManager.updateStatistics(stats) },
                scope = serviceScope
            )
            tunEngine?.start()

            VpnManager.updateState(VpnState.Connected(profile.name, node.name, node.getMaskedServerAddress()))
        }
    }
}`,
    },
    'ClashYamlParser.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/parser/ClashYamlParser.kt',
      code: `package com.ajaz.tiktok.core.parser

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.util.UUID

object ClashYamlParser {
    fun parse(rawText: String, profileName: String, sourceUrl: String?): NetworkProfile {
        // Decodes Base64 subscriptions, parses Clash / Clash.Meta / Mihomo YAML, and vless/trojan/ss/vmess URIs
        val yaml = Yaml(SafeConstructor())
        val map = yaml.load<Map<String, Any>>(rawText)
        val rawProxies = map["proxies"] as? List<Map<String, Any>> ?: emptyList()

        val nodes = rawProxies.mapNotNull { item ->
            ProxyNode(
                id = UUID.randomUUID().toString(),
                name = item["name"]?.toString() ?: "Node",
                type = ProxyType.fromString(item["type"]?.toString()),
                server = item["server"]?.toString() ?: return@mapNotNull null,
                port = (item["port"] as? Number)?.toInt() ?: 443,
                password = item["password"]?.toString(),
                uuid = item["uuid"]?.toString(),
                sni = item["sni"]?.toString(),
                tls = item["tls"] == true || item["security"] == "tls",
                network = item["network"]?.toString()
            )
        }

        return NetworkProfile(
            id = UUID.randomUUID().toString(),
            name = profileName,
            sourceUrl = sourceUrl,
            rawConfig = rawText,
            proxyCount = nodes.size,
            proxies = nodes,
            isValid = nodes.isNotEmpty()
        )
    }
}`,
    },
    'AndroidManifest.xml': {
      lang: 'xml',
      path: 'app/src/main/AndroidManifest.xml',
      code: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".AjazApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.AjazTiktok">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".core.vpn.AjazVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:foregroundServiceType="systemExempted">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>
    </application>
</manifest>`,
    },
  };

  const handleDownloadZip = async () => {
    try {
      setIsGeneratingZip(true);
      const blob = await generateAndroidProjectZip();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'AjazTiktok-Native-Android-Project.zip';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      setDownloadSuccess(true);
      setTimeout(() => setDownloadSuccess(false), 3000);
    } catch (err) {
      console.error('Error creating project ZIP:', err);
    } finally {
      setIsGeneratingZip(false);
    }
  };

  return (
    <div className="flex flex-col h-full text-[#e6e6e6] select-none overflow-y-auto space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-[#1a1a1a]">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="font-serif text-lg font-bold text-white tracking-wide">Android Studio & APK Suite</h2>
            <span className="text-[10px] px-2 py-0.5 rounded-full bg-[#c5a059]/10 text-[#c5a059] border border-[#c5a059]/30 font-mono font-medium">
              Native SDK 34
            </span>
          </div>
          <p className="text-xs text-[#7a7a7a]">Complete Gradle, Kotlin, VpnService & Manifest source tree</p>
        </div>

        <button
          id="download-android-project-zip-button"
          onClick={handleDownloadZip}
          disabled={isGeneratingZip}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#c5a059] hover:bg-[#d4af37] text-[#050505] font-bold text-xs shadow-md shadow-[#c5a059]/20 transition-all disabled:opacity-50"
        >
          {downloadSuccess ? (
            <>
              <Check className="w-4 h-4 text-[#050505]" />
              <span>Downloaded ZIP!</span>
            </>
          ) : (
            <>
              <Download className="w-4 h-4" />
              <span>{isGeneratingZip ? 'Packaging...' : 'Download Android Studio ZIP'}</span>
            </>
          )}
        </button>
      </div>

      {/* Build & Compilation Guide */}
      <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-4 space-y-3 shadow-md shadow-black/30">
        <div className="flex items-center gap-2 text-xs font-bold text-[#c5a059] tracking-wide">
          <Terminal className="w-4 h-4" />
          <span>Quick Build Commands</span>
        </div>

        <div className="bg-[#050505] rounded-xl p-3 border border-[#1f1f1f] font-mono text-xs text-[#d0d0d0] space-y-2 shadow-inner">
          <div>
            <span className="text-[#666666]"># 1. Build release APK</span>
            <div className="text-[#c5a059] font-bold">./gradlew assembleRelease</div>
          </div>
          <div>
            <span className="text-[#666666]"># 2. Build debug APK</span>
            <div className="text-[#e5c378] font-bold">./gradlew assembleDebug</div>
          </div>
          <div>
            <span className="text-[#666666]"># Output APK path:</span>
            <div className="text-[#888888] text-[11px]">app/build/outputs/apk/release/app-release-unsigned.apk</div>
          </div>
        </div>
      </div>

      {/* Source Code File Explorer */}
      <div className="bg-[#0c0c0c] border border-[#1a1a1a] rounded-2xl p-4 space-y-3 shadow-md shadow-black/30">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-bold text-white tracking-wide">
            <FolderCode className="w-4 h-4 text-[#c5a059]" />
            <span>Native Project Files</span>
          </div>
          <span className="text-[11px] text-[#7a7a7a] font-mono">{fileContents[selectedFile]?.path}</span>
        </div>

        {/* File Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
          {Object.keys(fileContents).map((fileName) => (
            <button
              key={fileName}
              onClick={() => setSelectedFile(fileName)}
              className={`flex items-center gap-1.5 px-3 py-1 rounded-xl text-xs font-medium transition-colors shrink-0 ${
                selectedFile === fileName
                  ? 'bg-[#c5a059] text-[#050505] font-bold shadow-md shadow-[#c5a059]/20'
                  : 'bg-[#050505] text-[#7a7a7a] hover:text-white border border-[#1f1f1f]'
              }`}
            >
              <FileCode className="w-3.5 h-3.5" />
              <span>{fileName}</span>
            </button>
          ))}
        </div>

        {/* Code Box */}
        <div className="bg-[#050505] border border-[#1f1f1f] rounded-xl p-3 max-h-72 overflow-y-auto font-mono text-[11px] leading-relaxed text-[#d0d0d0] shadow-inner">
          <pre>{fileContents[selectedFile]?.code}</pre>
        </div>
      </div>
    </div>
  );
};
