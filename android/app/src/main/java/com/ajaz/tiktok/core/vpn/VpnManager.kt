package com.ajaz.tiktok.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.ajaz.tiktok.core.logger.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnManager {

    private val _vpnState = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private val _statistics = MutableStateFlow(VpnStatistics())
    val statistics: StateFlow<VpnStatistics> = _statistics.asStateFlow()

    fun updateState(newState: VpnState) {
        _vpnState.value = newState
    }

    fun updateStatistics(stats: VpnStatistics) {
        _statistics.value = stats
    }

    fun prepareVpn(context: Context): Intent? {
        return VpnService.prepare(context)
    }

    fun startVpn(context: Context, profileId: String? = null, nodeId: String? = null) {
        AppLogger.i("VpnManager", "Requesting VPN start for profile: $profileId, node: $nodeId")
        val intent = Intent(context, AjazVpnService::class.java).apply {
            action = AjazVpnService.ACTION_CONNECT
            putExtra(AjazVpnService.EXTRA_PROFILE_ID, profileId)
            putExtra(AjazVpnService.EXTRA_NODE_ID, nodeId)
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            AppLogger.e("VpnManager", "Failed to start VPN service intent: ${e.message}")
            updateState(VpnState.Error("Could not start background service: ${e.localizedMessage}"))
        }
    }

    fun stopVpn(context: Context) {
        AppLogger.i("VpnManager", "Requesting VPN stop")
        val intent = Intent(context, AjazVpnService::class.java).apply {
            action = AjazVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
