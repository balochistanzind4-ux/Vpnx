package com.ajaz.tiktok.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ajaz.tiktok.AjazApplication
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.core.logger.LogEntry
import com.ajaz.tiktok.core.network.NetworkClient
import com.ajaz.tiktok.core.network.NetworkStatus
import com.ajaz.tiktok.core.parser.ClashYamlParser
import com.ajaz.tiktok.core.parser.NetworkProfile
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.storage.AppSettings
import com.ajaz.tiktok.core.vpn.VpnManager
import com.ajaz.tiktok.core.vpn.VpnState
import com.ajaz.tiktok.core.vpn.VpnStatistics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AjazApplication
    private val profileStorage = app.profileStorage
    private val settingsRepo = app.settingsRepository
    private val networkMonitor = app.networkMonitor

    val vpnState: StateFlow<VpnState> = VpnManager.vpnState
    val statistics: StateFlow<VpnStatistics> = VpnManager.statistics
    val networkStatus: StateFlow<NetworkStatus> = networkMonitor.status
    val profiles: StateFlow<List<NetworkProfile>> = profileStorage.profiles
    val activeProfileId: StateFlow<String?> = profileStorage.activeProfileId
    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val logs: StateFlow<List<LogEntry>> = AppLogger.logsFlow

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun toggleConnection(onNeedPermission: (Intent) -> Unit) {
        val current = vpnState.value
        if (current is VpnState.Connected || current is VpnState.Connecting) {
            VpnManager.stopVpn(app)
            return
        }

        // Check network
        if (!networkMonitor.isOnline()) {
            _userMessage.value = "No Internet connection detected"
            AppLogger.e("UI", "Cannot connect: Network is offline")
            return
        }

        // Check active profile
        val profile = profileStorage.getActiveProfile()
        if (profile == null || !profile.isValid || profile.proxies.isEmpty()) {
            _userMessage.value = "Please import a valid profile with at least 1 provider"
            AppLogger.e("UI", "Cannot connect: No valid profile selected")
            return
        }

        // Check Android VpnService permission preparation
        val prepareIntent = VpnManager.prepareVpn(app)
        if (prepareIntent != null) {
            AppLogger.i("UI", "Requesting system VPN consent dialog")
            onNeedPermission(prepareIntent)
        } else {
            startConnection()
        }
    }

    fun startConnection() {
        val profile = profileStorage.getActiveProfile() ?: return
        VpnManager.startVpn(app, profile.id, profile.selectedProxyId)
    }

    fun importFromUrl(url: String, name: String = "Subscription") {
        viewModelScope.launch {
            _isImporting.value = true
            AppLogger.i("UI", "Downloading subscription from: $url")

            val result = NetworkClient.fetchSubscription(url)
            _isImporting.value = false

            if (!result.isSuccess || result.content.isNullOrBlank()) {
                val err = result.errorMessage ?: "Failed to download subscription"
                _userMessage.value = err
                AppLogger.e("UI", "Import failed: $err")
                return@launch
            }

            val parsed = ClashYamlParser.parse(result.content, name, url)
            if (!parsed.isValid || parsed.proxies.isEmpty()) {
                _userMessage.value = "Configuration downloaded but contains no usable providers"
                AppLogger.w("UI", "Parsed 0 providers from subscription")
                return@launch
            }

            val saved = profileStorage.addOrUpdateProfile(parsed)
            _userMessage.value = "Imported ${saved.proxyCount} providers for '${saved.name}'"
            AppLogger.i("UI", "Successfully imported profile: ${saved.name}")
        }
    }

    fun importFromText(text: String, name: String = "Manual Config") {
        val parsed = ClashYamlParser.parse(text, name, null)
        if (!parsed.isValid || parsed.proxies.isEmpty()) {
            _userMessage.value = "Invalid configuration format or no providers found"
            AppLogger.e("UI", "Manual config parse failed")
            return
        }
        val saved = profileStorage.addOrUpdateProfile(parsed)
        _userMessage.value = "Loaded ${saved.proxyCount} providers"
    }

    fun refreshProfile(profile: NetworkProfile) {
        val url = profile.sourceUrl
        if (url.isNullOrBlank()) {
            _userMessage.value = "Profile does not have a subscription URL"
            return
        }
        importFromUrl(url, profile.name)
    }

    fun setActiveProfile(profileId: String) {
        profileStorage.setActiveProfile(profileId)
    }

    fun selectNode(profileId: String, nodeId: String) {
        profileStorage.selectNodeInProfile(profileId, nodeId)
        if (vpnState.value is VpnState.Connected) {
            // Reconnect to newly selected node
            VpnManager.startVpn(app, profileId, nodeId)
        }
    }

    fun deleteProfile(profileId: String) {
        profileStorage.deleteProfile(profileId)
    }

    fun renameProfile(profileId: String, newName: String) {
        profileStorage.renameProfile(profileId, newName)
    }

    fun duplicateProfile(profileId: String) {
        profileStorage.duplicateProfile(profileId)
    }

    fun updateSettings(newSettings: AppSettings) {
        settingsRepo.updateSettings(newSettings)
    }

    fun clearLogs() {
        AppLogger.clear()
    }

    fun resetAllData() {
        if (vpnState.value is VpnState.Connected) {
            VpnManager.stopVpn(app)
        }
        profileStorage.clearAll()
        settingsRepo.resetToDefaults()
        AppLogger.clear()
        _userMessage.value = "Application data reset to factory state"
    }
}
