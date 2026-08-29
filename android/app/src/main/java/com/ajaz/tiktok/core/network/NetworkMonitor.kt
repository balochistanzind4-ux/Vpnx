package com.ajaz.tiktok.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.vpn.VpnManager
import com.ajaz.tiktok.core.vpn.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val hasInternetCapability: Boolean = false,
    val typeName: String = "Offline"
)

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _status = MutableStateFlow(getCurrentStatus())
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateStatus()
            AppLogger.d("NetworkMonitor", "Physical network interface available")
        }

        override fun onLost(network: Network) {
            updateStatus()
            AppLogger.w("NetworkMonitor", "Physical network interface lost")

            // If no physical connection remains while VPN was active, prevent fake connected state
            if (!isOnline()) {
                val currentState = VpnManager.vpnState.value
                if (currentState is VpnState.Connected || currentState is VpnState.Connecting) {
                    AppLogger.e("NetworkMonitor", "Underlying network is down. Stopping VPN tunnel.")
                    VpnManager.stopVpn(context)
                    VpnManager.updateState(
                        VpnState.Error(
                            "Network lost: Mobile Data & Wi-Fi are disconnected",
                            "Turn on Mobile Data or connect to Wi-Fi to re-establish tunnel"
                        )
                    )
                }
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateStatus()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            AppLogger.e("NetworkMonitor", "Failed to register network callback: ${e.message}")
        }
    }

    fun isOnline(): Boolean {
        return _status.value.isConnected && _status.value.hasInternetCapability
    }

    private fun updateStatus() {
        _status.value = getCurrentStatus()
    }

    private fun getCurrentStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus()
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus()

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        val name = when {
            isWifi -> "Wi-Fi (${if (hasInternet) "Online" else "No Internet"})"
            isCellular -> "Cellular 4G/5G"
            isVpn -> "Protected Tunnel"
            hasInternet -> "Ethernet / Active"
            else -> "Disconnected"
        }

        return NetworkStatus(
            isConnected = hasInternet,
            isWifi = isWifi,
            isCellular = isCellular,
            hasInternetCapability = hasInternet,
            typeName = name
        )
    }
}
