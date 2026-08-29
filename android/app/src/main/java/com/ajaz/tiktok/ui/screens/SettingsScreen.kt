package com.ajaz.tiktok.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajaz.tiktok.ui.theme.BlackObsidian
import com.ajaz.tiktok.ui.theme.CardBorder
import com.ajaz.tiktok.ui.theme.CardSurface
import com.ajaz.tiktok.ui.theme.CrimsonAlert
import com.ajaz.tiktok.ui.theme.DarkSurface
import com.ajaz.tiktok.ui.theme.GoldAccent
import com.ajaz.tiktok.ui.theme.TextMuted
import com.ajaz.tiktok.ui.theme.TextPrimary
import com.ajaz.tiktok.ui.theme.TextSecondary
import com.ajaz.tiktok.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val settings by viewModel.settings.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackObsidian)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "System Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Routing, DNS & Protection Controls",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // Section: Network & Reconnect
        item {
            SettingsCategoryCard(title = "CONNECTION BEHAVIOR") {
                SettingSwitchRow(
                    title = "Auto Reconnect",
                    subtitle = "Automatically re-establish connection after network interruptions",
                    checked = settings.autoReconnect,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(autoReconnect = it)) }
                )
                SettingSwitchRow(
                    title = "Bypass Local LAN",
                    subtitle = "Route local subnet (192.168.x, 10.x) directly to preserve local access",
                    checked = settings.bypassLan,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(bypassLan = it)) }
                )
            }
        }

        // Section: Privacy & Leak Protection
        item {
            SettingsCategoryCard(title = "PRIVACY & LEAK PREVENTION") {
                SettingItemRow(
                    title = "Secure DNS Mode",
                    value = settings.dnsMode,
                    icon = Icons.Default.Dns,
                    onClick = {
                        val nextDns = if (settings.dnsMode.contains("1.1.1.1")) "Google (8.8.8.8)" else "Cloudflare (1.1.1.1)"
                        val nextIp = if (nextDns.contains("1.1.1.1")) "1.1.1.1" else "8.8.8.8"
                        viewModel.updateSettings(settings.copy(dnsMode = nextDns, customDns = nextIp))
                    }
                )
                SettingItemRow(
                    title = "IPv6 Safe Fallback",
                    value = settings.ipv6Mode,
                    icon = Icons.Default.Security,
                    onClick = { /* toggle */ }
                )
                SettingSwitchRow(
                    title = "Kill-Switch Behavior",
                    subtitle = "Block external traffic if VPN tunnel drops abruptly",
                    checked = settings.killSwitchEnabled,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(killSwitchEnabled = it)) }
                )
            }
        }

        // Section: Diagnostics & Logging
        item {
            SettingsCategoryCard(title = "DIAGNOSTICS & SYSTEM") {
                SettingItemRow(
                    title = "Connection Timeout",
                    value = "${settings.connectionTimeoutSeconds} seconds",
                    icon = Icons.Default.NetworkPing,
                    onClick = {
                        val next = when (settings.connectionTimeoutSeconds) {
                            15 -> 20
                            20 -> 30
                            30 -> 60
                            else -> 15
                        }
                        viewModel.updateSettings(settings.copy(connectionTimeoutSeconds = next))
                    }
                )
                SettingItemRow(
                    title = "Log Verbosity Level",
                    value = settings.logLevel,
                    icon = Icons.Default.AltRoute,
                    onClick = {
                        val next = when (settings.logLevel) {
                            "DEBUG" -> "INFO"
                            "INFO" -> "WARN"
                            "WARN" -> "ERROR"
                            else -> "DEBUG"
                        }
                        viewModel.updateSettings(settings.copy(logLevel = next))
                    }
                )
            }
        }

        // Section: Danger Zone
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showResetDialog = true },
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = CrimsonAlert,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column {
                        Text(
                            text = "Reset Application Data",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                            color = CrimsonAlert,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Purge all imported profiles, cached configs & reset settings",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Data?", color = CrimsonAlert, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This action will remove all saved network profiles and reset your configurations. This cannot be undone.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert)
                ) {
                    Text("Purge Everything", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = CardSurface
        )
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = GoldAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BlackObsidian,
                checkedTrackColor = GoldAccent,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CardSurface
            )
        )
    }
}

@Composable
private fun SettingItemRow(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "$value  ›",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = GoldAccent,
            fontWeight = FontWeight.SemiBold
        )
    }
}
