import React, { useState } from 'react';
import { Download, FolderCode, FileCode, Check, Terminal, ExternalLink, ShieldAlert, Cpu } from 'lucide-react';
import { generateAndroidProjectZip } from '../utils/androidProjectZip';

export const AndroidProjectInspector: React.FC = () => {
  const [isGeneratingZip, setIsGeneratingZip] = useState(false);
  const [downloadSuccess, setDownloadSuccess] = useState(false);
  const [selectedFile, setSelectedFile] = useState<string>('AjazVpnService.kt');

  const fileContents: Record<string, { lang: string; path: string; code: string }> = {
    'AjazVpnService.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/vpn/AjazVpnService.kt',
      code: `package com.ajaz.tiktok.core.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.ajaz.tiktok.AjazApplication
import com.ajaz.tiktok.R
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.ConnectionVerifier
import com.ajaz.tiktok.core.network.VerificationResult
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.ui.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class AjazVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunEngine: Tun2ProxyEngine? = null

    private fun startTunnel(profileId: String?, nodeId: String?) {
        val app = AjazApplication.instance
        if (!app.networkMonitor.isOnline()) {
            VpnManager.updateState(VpnState.Error("No active network connection", "Please turn on Mobile Data or Wi-Fi"))
            stopSelf()
            return
        }

        val profile = app.profileStorage.getActiveProfile() ?: return
        val node = profile.proxies.find { it.id == (nodeId ?: profile.selectedProxyId) } ?: profile.proxies.first()

        serviceScope.launch {
            VpnManager.updateState(VpnState.Connecting("Verifying tunnel with \${node.name}..."))
            val verifyResult = ConnectionVerifier.verifyTunnel(node, { socket -> protect(socket) }, 8000)

            val exitIp = when (verifyResult) {
                is VerificationResult.Success -> verifyResult.exitIp ?: node.server
                is VerificationResult.Failure -> {
                    VpnManager.updateState(VpnState.Error(verifyResult.reason, verifyResult.recoverySuggestion))
                    stopSelf()
                    return@launch
                }
            }

            // Build TUN interface with full-device routing
            val builder = Builder()
                .setSession("Ajaz×tiktok: \${profile.name}")
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            try {
                builder.addAddress("fd00::1", 64)
                builder.addRoute("::", 0)
            } catch (_: Exception) {}

            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            val pfd = builder.establish() ?: return@launch
            vpnInterface = pfd
            isRunning.set(true)

            // Start real Layer-3 packet forwarding engine
            tunEngine = Tun2ProxyEngine(
                vpnInterface = pfd,
                proxyNode = node,
                primaryDns = "1.1.1.1",
                protectSocket = { protect(it) },
                protectDatagramSocket = { protect(it) },
                onStatisticsUpdate = { stats -> VpnManager.updateStatistics(stats) },
                scope = serviceScope
            )

            VpnManager.updateState(VpnState.Connected(profile.name, node.name, node.getMaskedServerAddress()))
        }
    }
}`,
    },
    'Tun2ProxyEngine.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/vpn/Tun2ProxyEngine.kt',
      code: `package com.ajaz.tiktok.core.vpn

import android.os.ParcelFileDescriptor
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

class Tun2ProxyEngine(
    private val vpnInterface: ParcelFileDescriptor,
    private val proxyNode: ProxyNode,
    private val primaryDns: String,
    private val protectSocket: (Socket) -> Boolean,
    private val protectDatagramSocket: (DatagramSocket) -> Boolean,
    private val onStatisticsUpdate: (VpnStatistics) -> Unit,
    private val scope: CoroutineScope
) {
    private val bytesIn = AtomicLong(0L)
    private val bytesOut = AtomicLong(0L)
    private val tunInput = FileInputStream(vpnInterface.fileDescriptor)
    private val tunOutput = FileOutputStream(vpnInterface.fileDescriptor)

    private val tcpSessionManager = TcpSessionManager(
        proxyNode = proxyNode,
        tunOutput = tunOutput,
        protectSocket = protectSocket,
        onTraffic = { rx, tx -> bytesIn.addAndGet(rx); bytesOut.addAndGet(tx) },
        scope = scope
    )

    private val udpRelay = UdpRelay(
        tunOutput = tunOutput,
        protectSocket = protectDatagramSocket,
        primaryDns = primaryDns,
        onTraffic = { rx, tx -> bytesIn.addAndGet(rx); bytesOut.addAndGet(tx) },
        scope = scope
    )

    fun start() {
        scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            while (isActive) {
                val len = tunInput.read(buffer)
                if (len <= 0) continue
                val packet = IpPacket(buffer, len)
                if (packet.version == 4) {
                    if (packet.isTcp) tcpSessionManager.handleTcpPacket(packet)
                    else if (packet.isUdp) udpRelay.handleUdpPacket(packet)
                }
            }
        }
    }
}`,
    },
    'ConnectionVerifier.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/network/ConnectionVerifier.kt',
      code: `package com.ajaz.tiktok.core.network

import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.transport.ProxyTransportFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket

object ConnectionVerifier {
    suspend fun verifyTunnel(
        node: ProxyNode,
        protectSocket: (Socket) -> Boolean,
        timeoutMs: Int = 8000
    ): VerificationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val transport = ProxyTransportFactory.create(node)
            val socket = transport.openTunnel("1.1.1.1", 80, protectSocket, timeoutMs)
            val latency = System.currentTimeMillis() - startTime
            socket.close()
            VerificationResult.Success(exitIp = node.server, latencyMs = latency)
        } catch (e: Exception) {
            VerificationResult.Failure(
                reason = "Remote server unreachable: \${e.localizedMessage}",
                recoverySuggestion = "Check server endpoint or select a different server"
            )
        }
    }
}`,
    },
    'Socks5Transport.kt': {
      lang: 'kotlin',
      path: 'app/src/main/java/com/ajaz/tiktok/core/transport/Socks5Transport.kt',
      code: `package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class Socks5Transport(private val node: ProxyNode) : ProxyTransport {
    override suspend fun openTunnel(
        targetHost: String,
        targetPort: Int,
        protectSocket: (Socket) -> Boolean,
        connectTimeoutMs: Int
    ): Socket = withContext(Dispatchers.IO) {
        val socket = Socket()
        protectSocket(socket)
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(node.server, node.port), connectTimeoutMs)

        val out = DataOutputStream(socket.getOutputStream())
        val \`in\` = DataInputStream(socket.getInputStream())

        // 1. Negotiation Handshake
        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        val ver = \`in\`.readUnsignedByte()
        val method = \`in\`.readUnsignedByte()

        // 2. Connect Command (ATYP=0x03 Domain)
        out.writeByte(0x05)
        out.writeByte(0x01)
        out.writeByte(0x00)
        val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
        out.writeByte(0x03)
        out.writeByte(hostBytes.size)
        out.write(hostBytes)
        out.writeShort(targetPort)
        out.flush()

        // 3. Response Status Check
        val repVer = \`in\`.readUnsignedByte()
        val repStatus = \`in\`.readUnsignedByte()
        if (repStatus != 0x00) throw java.io.IOException("SOCKS5 error code: $repStatus")

        socket
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
    fun parse(rawText: String, name: String, url: String?): NetworkProfile {
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
                uuid = item["uuid"]?.toString()
            )
        }

        return NetworkProfile(name = name, sourceUrl = url, proxies = nodes)
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
    'app/build.gradle.kts': {
      lang: 'kotlin',
      path: 'app/build.gradle.kts',
      code: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.ajaz.tiktok"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ajaz.tiktok"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.okhttp)
    implementation(libs.snakeyaml)
    implementation(libs.kotlinx.coroutines.android)
}`,
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
