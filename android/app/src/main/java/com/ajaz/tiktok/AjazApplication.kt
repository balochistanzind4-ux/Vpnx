package com.ajaz.tiktok

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.network.NetworkMonitor
import com.ajaz.tiktok.core.storage.ProfileStorage
import com.ajaz.tiktok.core.storage.SettingsRepository
import com.ajaz.tiktok.core.vpn.VpnManager

class AjazApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ajaz_vpn_status_channel"
        lateinit var instance: AjazApplication
            private set
    }

    lateinit var profileStorage: ProfileStorage
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        AppLogger.i("System", "Ajaz×tiktok core initialized (v1.0.0)")

        // Initialize Persistence & Network
        profileStorage = ProfileStorage(this)
        settingsRepository = SettingsRepository(this)
        networkMonitor = NetworkMonitor(this)

        createNotificationChannel()

        AppLogger.d("System", "Registered subsystems: ProfileStorage, Settings, NetworkCallback")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
