package com.ajaz.tiktok.core.vpn

sealed class VpnState(val statusName: String) {
    object Disconnected : VpnState("Disconnected")
    data class Connecting(val message: String = "Initializing secure socket...") : VpnState("Connecting")
    data class Connected(
        val profileName: String,
        val serverName: String,
        val serverAddress: String,
        val exitIp: String? = null,
        val connectedSince: Long = System.currentTimeMillis()
    ) : VpnState("Connected")
    object Stopping : VpnState("Stopping")
    data class Error(val message: String, val recoveryAction: String? = null) : VpnState("Error")
}

data class VpnStatistics(
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val speedInBps: Long = 0,
    val speedOutBps: Long = 0,
    val latencyMs: Long = -1,
    val durationSeconds: Long = 0
)
