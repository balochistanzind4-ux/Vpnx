package com.ajaz.tiktok.core.vpn

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.ajaz.tiktok.AjazApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class AjazTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = scope.launch {
            VpnManager.vpnState.collectLatest { state ->
                val tile = qsTile ?: return@collectLatest
                when (state) {
                    is VpnState.Connected -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.label = "Ajaz×tiktok"
                        tile.subtitle = state.serverName
                    }
                    is VpnState.Connecting -> {
                        tile.state = Tile.STATE_UNAVAILABLE
                        tile.label = "Ajaz×tiktok"
                        tile.subtitle = "Connecting..."
                    }
                    else -> {
                        tile.state = Tile.STATE_INACTIVE
                        tile.label = "Ajaz×tiktok"
                        tile.subtitle = "Disconnected"
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
    }

    override fun onClick() {
        super.onClick()
        val currentState = VpnManager.vpnState.value
        if (currentState is VpnState.Connected || currentState is VpnState.Connecting) {
            VpnManager.stopVpn(this)
        } else {
            val app = AjazApplication.instance
            val profile = app.profileStorage.getActiveProfile()
            if (profile != null) {
                VpnManager.startVpn(this, profile.id, profile.selectedProxyId)
            }
        }
    }
}
