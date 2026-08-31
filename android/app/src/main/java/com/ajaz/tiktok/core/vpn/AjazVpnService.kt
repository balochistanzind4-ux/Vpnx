package com.ajaz.tiktok.core.vpn

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.Socket
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
            val err = "No valid profile selected"
            AppLogger.e("VpnService", err)
            VpnManager.updateState(VpnState.Error(err, "Please select or import a profile"))
            stopSelf()
            return
        }

        val node = profile.proxies.find { it.id == (nodeId ?: profile.selectedProxyId) } ?: profile.proxies.first()
        activeProfileName = profile.name
        activeServerName = node.name
        activeServerAddress = node.getMaskedServerAddress()

        AppLogger.i("VpnService", "Target Endpoint: ${node.name} [${node.type.displayName}] (${node.server}:${node.port})")

        val notification = createNotification(VpnState.Connecting("Connecting to secure location..."))
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                // 1. Verify remote endpoint before declaring Connected state
                VpnManager.updateState(VpnState.Connecting("Connecting to ${node.name}..."))

                val verifyResult = ConnectionVerifier.verifyTunnel(
                    node = node,
                    protectSocket = { socket -> protect(socket) },
                    timeoutMs = 8000
                )

                val exitIp = when (verifyResult) {
                    is VerificationResult.Success -> {
                        AppLogger.i("VpnService", "Handshake verified with ${node.name} (${verifyResult.latencyMs}ms)")
                        verifyResult.exitIp ?: node.server
                    }
                    is VerificationResult.Failure -> {
                        AppLogger.e("VpnService", "Tunnel verification failed: ${verifyResult.reason}")
                        VpnManager.updateState(VpnState.Error(verifyResult.reason, verifyResult.recoverySuggestion))
                        cleanup()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                }

                // 2. Build TUN Interface
                val builder = Builder()
                    .setSession("Ajaz×tiktok: ${profile.name}")
                    .setMtu(VPN_MTU)
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)

                // DNS Handling
                val settings = app.settingsRepository.settings.value
                val primaryDns = if (settings.customDns.isNotBlank()) settings.customDns.trim() else "1.1.1.1"
                builder.addDnsServer(primaryDns)
                builder.addDnsServer("8.8.8.8")
                AppLogger.i("VpnService", "Assigned DNS servers: $primaryDns, 8.8.8.8")

                // IPv6 Safe Route Fallback
                try {
                    builder.addAddress("fd00::1", 64)
                    builder.addRoute("::", 0)
                    AppLogger.d("VpnService", "IPv6 encapsulation route (::/0) active")
                } catch (e: Exception) {
                    AppLogger.w("VpnService", "IPv6 route encapsulation unsupported by host kernel: ${e.message}")
                }

                // Disallow self package to prevent routing loops
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    AppLogger.w("VpnService", "Could not disallow self package: ${e.message}")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    throw IllegalStateException("VpnService.Builder.establish() returned null. Android VPN permission may not have been granted.")
                }

                vpnInterface = pfd
                isRunning.set(true)
                connectedTimestamp = System.currentTimeMillis()

                // 3. Start real TUN-to-Proxy Packet Routing Engine
                tunEngine = Tun2ProxyEngine(
                    vpnInterface = pfd,
                    proxyNode = node,
                    primaryDns = primaryDns,
                    protectSocket = { socket -> protect(socket) },
                    protectDatagramSocket = { dgramSocket -> protect(dgramSocket) },
                    onStatisticsUpdate = { stats ->
                        VpnManager.updateStatistics(stats)
                    },
                    scope = serviceScope
                )

                val connectedState = VpnState.Connected(
                    profileName = activeProfileName,
                    serverName = activeServerName,
                    serverAddress = activeServerAddress,
                    exitIp = exitIp,
                    connectedSince = connectedTimestamp
                )
                VpnManager.updateState(connectedState)
                updateNotification(connectedState)

                AppLogger.i("VpnService", "Android VPN tunnel active and routing all traffic through ${node.name} (Exit IP: $exitIp)")

            } catch (e: Exception) {
                AppLogger.e("VpnService", "Failed to establish tunnel: ${e.message}", e)
                VpnManager.updateState(VpnState.Error("Connection failure: ${e.localizedMessage}", "Retry or choose another server"))
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopTunnel(reason: String) {
        AppLogger.i("VpnService", "Stopping VPN tunnel: $reason")
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
        } catch (e: Exception) {
            AppLogger.w("VpnService", "Error closing VPN interface: ${e.message}")
        }
        vpnInterface = null
        AppLogger.i("VpnService", "VPN interface and routing rules cleanly restored")
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
            is VpnState.Connected -> "${state.serverName} • ${state.profileName}"
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
