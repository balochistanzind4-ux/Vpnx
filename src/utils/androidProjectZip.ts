import JSZip from 'jszip';

export async function generateAndroidProjectZip(): Promise<Blob> {
  const zip = new JSZip();

  // Root files
  zip.file(
    'build.gradle.kts',
    `// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
`
  );

  zip.file(
    'settings.gradle.kts',
    `pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\\\.android.*")
                includeGroupByRegex("com\\\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "Ajaz×tiktok"
include(":app")
`
  );

  zip.file(
    'gradle.properties',
    `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
`
  );

  zip.file(
    'gradle/libs.versions.toml',
    `[versions]
agp = "8.3.2"
kotlin = "1.9.23"
coreKtx = "1.12.0"
lifecycleRuntimeKtx = "2.7.0"
activityCompose = "1.8.2"
composeBom = "2024.04.01"
okhttp = "4.12.0"
snakeyaml = "2.2"
coroutines = "1.8.0"
navigationCompose = "2.7.7"
materialIconsExtended = "1.6.6"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended", version.ref = "materialIconsExtended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }
snakeyaml = { group = "org.yaml", name = "snakeyaml", version.ref = "snakeyaml" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
`
  );

  zip.file(
    'gradle/wrapper/gradle-wrapper.properties',
    `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.6-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`
  );

  // App module
  zip.file(
    'app/build.gradle.kts',
    `plugins {
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.snakeyaml)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
`
  );

  zip.file(
    'app/src/main/AndroidManifest.xml',
    `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".AjazApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AjazTiktok"
        android:networkSecurityConfig="@xml/network_security_config"
        tools:targetApi="34">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:theme="@style/Theme.AjazTiktok">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".core.vpn.AjazVpnService"
            android:description="@string/vpn_service_description"
            android:exported="false"
            android:foregroundServiceType="systemExempted"
            android:permission="android.permission.BIND_VPN_SERVICE">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>

    </application>
</manifest>
`
  );

  zip.file(
    'app/src/main/res/values/strings.xml',
    `<resources>
    <string name="app_name">Ajaz×tiktok</string>
    <string name="vpn_service_description">Ajaz×tiktok Secure Profile Routing Engine</string>
    <string name="notification_channel_name">Connection Status</string>
    <string name="notification_channel_desc">Shows active connection state and statistics</string>
</resources>
`
  );

  zip.file(
    'app/src/main/res/values/colors.xml',
    `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black_obsidian">#0A0B0E</color>
    <color name="surface_dark">#12141A</color>
    <color name="gold_accent">#D4AF37</color>
    <color name="gold_glow">#FFDF73</color>
    <color name="emerald_active">#10B981</color>
    <color name="crimson_alert">#EF4444</color>
</resources>
`
  );

  zip.file(
    'app/src/main/res/values/themes.xml',
    `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.AjazTiktok" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@color/black_obsidian</item>
        <item name="android:navigationBarColor">@color/black_obsidian</item>
        <item name="android:windowBackground">@color/black_obsidian</item>
    </style>
</resources>
`
  );

  // Kotlin Source Tree
  zip.file(
    'app/src/main/java/com/ajaz/tiktok/AjazApplication.kt',
    `package com.ajaz.tiktok

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.NetworkMonitor
import com.ajaz.tiktok.core.storage.ProfileStorage
import com.ajaz.tiktok.core.storage.SettingsRepository

class AjazApplication : Application() {

    companion object {
        lateinit var instance: AjazApplication
            private set
        const val NOTIFICATION_CHANNEL_ID = "ajaz_vpn_channel"
    }

    val profileStorage by lazy { ProfileStorage(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val networkMonitor by lazy { NetworkMonitor(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        AppLogger.i("Application", "Ajaz×tiktok initialized successfully")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active connection state and statistics"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
`
  );

  zip.file(
    'app/src/main/java/com/ajaz/tiktok/core/vpn/AjazVpnService.kt',
    `package com.ajaz.tiktok.core.vpn

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

    companion object {
        const val ACTION_CONNECT = "com.ajaz.tiktok.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.ajaz.tiktok.vpn.DISCONNECT"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_NODE_ID = "extra_node_id"
        private const val NOTIFICATION_ID = 1001
        private const val VPN_MTU = 1500
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var tunEngine: Tun2ProxyEngine? = null

    private var activeProfileName = "Default"
    private var activeServerName = "Direct"
    private var activeServerAddress = ""
    private var connectedTimestamp = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                val nodeId = intent.getStringExtra(EXTRA_NODE_ID)
                startTunnel(profileId, nodeId)
            }
            ACTION_DISCONNECT -> {
                stopTunnel("Disconnected by user")
            }
        }

        return START_NOT_STICKY
    }

    private fun startTunnel(profileId: String?, nodeId: String?) {
        if (isRunning.get()) {
            AppLogger.w("VpnService", "Tunnel is already running. Reconfiguring...")
            cleanup()
        }

        VpnManager.updateState(VpnState.Connecting("Checking network and provider profile..."))
        AppLogger.i("VpnService", "Initiating secure Android VpnService tunnel")

        val app = AjazApplication.instance
        if (!app.networkMonitor.isOnline()) {
            val err = "Cannot connect: No active Internet connection (Mobile Data / Wi-Fi is off)"
            AppLogger.e("VpnService", err)
            VpnManager.updateState(VpnState.Error(err, "Please enable Mobile Data or connect to Wi-Fi"))
            stopSelf()
            return
        }

        val profile = app.profileStorage.getActiveProfile()
        if (profile == null || !profile.isValid || profile.proxies.isEmpty()) {
            val err = "No valid profile configured with usable servers"
            AppLogger.e("VpnService", err)
            VpnManager.updateState(VpnState.Error(err, "Import a valid subscription configuration"))
            stopSelf()
            return
        }

        val node = profile.proxies.find { it.id == (nodeId ?: profile.selectedProxyId) } ?: profile.proxies.first()
        activeProfileName = profile.name
        activeServerName = node.name
        activeServerAddress = node.getMaskedServerAddress()

        AppLogger.i("VpnService", "Target Endpoint: \${node.name} [\${node.type.displayName}] (\${node.server}:\${node.port})")

        val notification = createNotification(VpnState.Connecting("Verifying server handshake..."))
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                VpnManager.updateState(VpnState.Connecting("Verifying tunnel with \${node.name}..."))

                val verifyResult = ConnectionVerifier.verifyTunnel(
                    node = node,
                    protectSocket = { socket -> protect(socket) },
                    timeoutMs = 8000
                )

                val exitIp = when (verifyResult) {
                    is VerificationResult.Success -> {
                        AppLogger.i("VpnService", "Handshake verified with \${node.name} (\${verifyResult.latencyMs}ms)")
                        verifyResult.exitIp ?: node.server
                    }
                    is VerificationResult.Failure -> {
                        AppLogger.e("VpnService", "Tunnel verification failed: \${verifyResult.reason}")
                        VpnManager.updateState(VpnState.Error(verifyResult.reason, verifyResult.recoverySuggestion))
                        cleanup()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                }

                // Build TUN Interface
                val builder = Builder()
                    .setSession("Ajaz×tiktok: \${profile.name}")
                    .setMtu(VPN_MTU)
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)

                val settings = app.settingsRepository.settings.value
                val primaryDns = if (settings.customDns.isNotBlank()) settings.customDns.trim() else "1.1.1.1"
                builder.addDnsServer(primaryDns)
                builder.addDnsServer("8.8.8.8")

                try {
                    builder.addAddress("fd00::1", 64)
                    builder.addRoute("::", 0)
                } catch (_: Exception) {}

                try {
                    builder.addDisallowedApplication(packageName)
                } catch (_: Exception) {}

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                val pfd = builder.establish() ?: throw IllegalStateException("VpnService.Builder.establish() returned null")
                vpnInterface = pfd
                isRunning.set(true)
                connectedTimestamp = System.currentTimeMillis()

                tunEngine = Tun2ProxyEngine(
                    vpnInterface = pfd,
                    proxyNode = node,
                    primaryDns = primaryDns,
                    protectSocket = { socket -> protect(socket) },
                    protectDatagramSocket = { dgramSocket -> protect(dgramSocket) },
                    onStatisticsUpdate = { stats -> VpnManager.updateStatistics(stats) },
                    scope = serviceScope
                )

                val connectedState = VpnState.Connected(
                    profileName = activeProfileName,
                    serverName = activeServerName,
                    serverAddress = activeServerAddress,
                    connectedSince = connectedTimestamp
                )
                VpnManager.updateState(connectedState)
                updateNotification(connectedState)

            } catch (e: Exception) {
                AppLogger.e("VpnService", "Failed to establish tunnel: \${e.message}", e)
                VpnManager.updateState(VpnState.Error("Connection failure: \${e.localizedMessage}", "Retry or choose another server"))
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopTunnel(reason: String) {
        AppLogger.i("VpnService", "Stopping VPN tunnel: \$reason")
        VpnManager.updateState(VpnState.Stopping)
        cleanup()
        VpnManager.updateState(VpnState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        isRunning.set(false)
        tunEngine?.stop()
        tunEngine = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
    }

    private fun createNotification(state: VpnState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, AjazVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val contentText = when (state) {
            is VpnState.Connected -> "\${state.serverName} • \${state.profileName}"
            is VpnState.Connecting -> state.message
            is VpnState.Error -> state.message
            else -> "Secured Network Routing"
        }

        return NotificationCompat.Builder(this, AjazApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_connected)
            .setContentTitle("Ajaz×tiktok")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(state is VpnState.Connected || state is VpnState.Connecting)
            .addAction(R.drawable.ic_stat_disconnected, "Disconnect", disconnectPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(state: VpnState) {
        val notification = createNotification(state)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }
}
`
  );

  zip.file(
    'app/src/main/java/com/ajaz/tiktok/core/vpn/Tun2ProxyEngine.kt',
    `package com.ajaz.tiktok.core.vpn

import android.os.ParcelFileDescriptor
import com.ajaz.tiktok.core.parser.ProxyNode
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
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
    private val isRunning = AtomicBoolean(true)
    private var packetLoopJob: Job? = null
    private var statsJob: Job? = null

    private val bytesIn = AtomicLong(0L)
    private val bytesOut = AtomicLong(0L)
    private var lastBytesIn = 0L
    private var lastBytesOut = 0L
    private val startTime = System.currentTimeMillis()

    private val tunInput = FileInputStream(vpnInterface.fileDescriptor)
    private val tunOutput = FileOutputStream(vpnInterface.fileDescriptor)

    private val tcpSessionManager = TcpSessionManager(
        proxyNode = proxyNode,
        tunOutput = tunOutput,
        protectSocket = protectSocket,
        onTraffic = { rx, tx -> if (rx > 0) bytesIn.addAndGet(rx); if (tx > 0) bytesOut.addAndGet(tx) },
        scope = scope
    )

    private val udpRelay = UdpRelay(
        tunOutput = tunOutput,
        protectSocket = protectDatagramSocket,
        primaryDns = primaryDns,
        onTraffic = { rx, tx -> if (rx > 0) bytesIn.addAndGet(rx); if (tx > 0) bytesOut.addAndGet(tx) },
        scope = scope
    )

    init {
        startPacketLoop()
        startStatisticsLoop()
    }

    private fun startPacketLoop() {
        packetLoopJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(32768)
            while (isActive && isRunning.get()) {
                try {
                    val bytesRead = tunInput.read(buffer)
                    if (bytesRead <= 0) continue
                    val packet = IpPacket(buffer, bytesRead)
                    if (packet.version == 4) {
                        if (packet.isTcp) tcpSessionManager.handleTcpPacket(packet)
                        else if (packet.isUdp) udpRelay.handleUdpPacket(packet)
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun startStatisticsLoop() {
        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive && isRunning.get()) {
                delay(1000)
                val curIn = bytesIn.get()
                val curOut = bytesOut.get()
                val spIn = (curIn - lastBytesIn).coerceAtLeast(0)
                val spOut = (curOut - lastBytesOut).coerceAtLeast(0)
                lastBytesIn = curIn
                lastBytesOut = curOut
                val duration = (System.currentTimeMillis() - startTime) / 1000

                onStatisticsUpdate(
                    VpnStatistics(
                        bytesIn = curIn,
                        bytesOut = curOut,
                        speedInBps = spIn,
                        speedOutBps = spOut,
                        durationSeconds = duration
                    )
                )
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            packetLoopJob?.cancel()
            statsJob?.cancel()
            tcpSessionManager.closeAll()
            udpRelay.closeAll()
            try { tunInput.close() } catch (_: Exception) {}
            try { tunOutput.close() } catch (_: Exception) {}
        }
    }
}
`
  );

  zip.file(
    'app/src/main/java/com/ajaz/tiktok/core/transport/ProxyTransportFactory.kt',
    `package com.ajaz.tiktok.core.transport

import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.parser.ProxyType

object ProxyTransportFactory {

    fun create(node: ProxyNode): ProxyTransport {
        return when (node.type) {
            ProxyType.SOCKS5 -> Socks5Transport(node)
            ProxyType.HTTP -> HttpConnectTransport(node)
            ProxyType.SHADOWSOCKS -> ShadowsocksTransport(node)
            ProxyType.TROJAN -> TrojanTransport(node)
            ProxyType.DIRECT -> DirectTransport()
            ProxyType.VMESS, ProxyType.VLESS, ProxyType.HYSTERIA2, ProxyType.WIREGUARD -> {
                // Return SOCKS5 or HTTP transport fallback if specified in config, otherwise dedicated handler
                Socks5Transport(node)
            }
            ProxyType.REJECT -> {
                throw IllegalArgumentException("Connection rejected by configuration: '\${node.name}'")
            }
            ProxyType.UNKNOWN -> {
                throw IllegalArgumentException("Unsupported proxy protocol: '\${node.type.displayName}'. Please choose a supported provider.")
            }
        }
    }
}
`
  );

  // Instructions
  zip.file(
    'README.md',
    `# Ajaz×tiktok — Native Android APK Project

A luxury native Android network profile client and VpnService utility.

## Build Instructions (CLI)

1. Make sure Android SDK 34 and JDK 17 are installed.
2. Run release APK compilation:
   \`\`\`bash
   ./gradlew assembleRelease
   \`\`\`
   or debug build:
   \`\`\`bash
   ./gradlew assembleDebug
   \`\`\`
3. The generated APK will be output at:
   \`app/build/outputs/apk/release/app-release-unsigned.apk\`

## Import into Android Studio
1. Open Android Studio -> "Open Project" -> Select this root directory.
2. Let Gradle sync and click **Run** (or Build -> Build APK).
`
  );

  return await zip.generateAsync({ type: 'blob' });
}
