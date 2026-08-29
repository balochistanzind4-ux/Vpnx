package com.ajaz.tiktok.core.storage

import android.content.Context
import android.content.SharedPreferences
import com.ajaz.tiktok.core.logger.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val autoReconnect: Boolean = true,
    val startOnBoot: Boolean = false,
    val dnsMode: String = "Cloudflare (1.1.1.1)",
    val customDns: String = "1.1.1.1",
    val ipv6Mode: String = "Safe Fallback (Block Leaks)",
    val routingMode: String = "Bypass LAN & China",
    val bypassLan: Boolean = true,
    val killSwitchEnabled: Boolean = false,
    val connectionTimeoutSeconds: Int = 20,
    val logLevel: String = "INFO"
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ajaz_settings_pref", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            autoReconnect = prefs.getBoolean("auto_reconnect", true),
            startOnBoot = prefs.getBoolean("start_on_boot", false),
            dnsMode = prefs.getString("dns_mode", "Cloudflare (1.1.1.1)") ?: "Cloudflare (1.1.1.1)",
            customDns = prefs.getString("custom_dns", "1.1.1.1") ?: "1.1.1.1",
            ipv6Mode = prefs.getString("ipv6_mode", "Safe Fallback (Block Leaks)") ?: "Safe Fallback (Block Leaks)",
            routingMode = prefs.getString("routing_mode", "Bypass LAN & Private") ?: "Bypass LAN & Private",
            bypassLan = prefs.getBoolean("bypass_lan", true),
            killSwitchEnabled = prefs.getBoolean("kill_switch", false),
            connectionTimeoutSeconds = prefs.getInt("connection_timeout", 20),
            logLevel = prefs.getString("log_level", "INFO") ?: "INFO"
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        prefs.edit().apply {
            putBoolean("auto_reconnect", newSettings.autoReconnect)
            putBoolean("start_on_boot", newSettings.startOnBoot)
            putString("dns_mode", newSettings.dnsMode)
            putString("custom_dns", newSettings.customDns)
            putString("ipv6_mode", newSettings.ipv6Mode)
            putString("routing_mode", newSettings.routingMode)
            putBoolean("bypass_lan", newSettings.bypassLan)
            putBoolean("kill_switch", newSettings.killSwitchEnabled)
            putInt("connection_timeout", newSettings.connectionTimeoutSeconds)
            putString("log_level", newSettings.logLevel)
            apply()
        }
        AppLogger.i("Settings", "Updated application settings")
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _settings.value = AppSettings()
        AppLogger.w("Settings", "Settings reset to defaults")
    }
}
