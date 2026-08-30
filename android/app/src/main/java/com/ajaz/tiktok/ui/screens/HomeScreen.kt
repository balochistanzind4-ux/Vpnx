package com.ajaz.tiktok.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajaz.tiktok.core.parser.NetworkProfile
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.vpn.VpnState
import com.ajaz.tiktok.ui.theme.AmberWarning
import com.ajaz.tiktok.ui.theme.BlackObsidian
import com.ajaz.tiktok.ui.theme.CardBorder
import com.ajaz.tiktok.ui.theme.CardSurface
import com.ajaz.tiktok.ui.theme.CrimsonAlert
import com.ajaz.tiktok.ui.theme.DarkSurface
import com.ajaz.tiktok.ui.theme.EmeraldActive
import com.ajaz.tiktok.ui.theme.GoldAccent
import com.ajaz.tiktok.ui.theme.GoldGlow
import com.ajaz.tiktok.ui.theme.TextMuted
import com.ajaz.tiktok.ui.theme.TextPrimary
import com.ajaz.tiktok.ui.theme.TextSecondary
import com.ajaz.tiktok.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onRequestVpnPermission: (Intent) -> Unit,
    onNavigateProfiles: () -> Unit
) {
    val vpnState by viewModel.vpnState.collectAsState()
    val stats by viewModel.statistics.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val activeProfile = profiles.find { it.id == activeProfileId }

    var showNodeSelector by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackObsidian)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Luxury Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Ajaz×tiktok",
                    style = MaterialTheme.typography.headlineMedium,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Private Network Suite",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            // Physical Network Status Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (networkStatus.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = if (networkStatus.isConnected) EmeraldActive else CrimsonAlert,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = networkStatus.typeName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Center Connection Button & Halo Pulse
        ConnectionOrbSection(
            state = vpnState,
            onClick = {
                viewModel.toggleConnection { prepareIntent ->
                    onRequestVpnPermission(prepareIntent)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Status Label & Details
        when (val s = vpnState) {
            is VpnState.Connected -> {
                Text(
                    text = "PROTECTED & ENCRYPTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldActive,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = s.serverName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (!s.exitIp.isNullOrBlank()) "Exit IP: ${s.exitIp} (${s.serverAddress})" else s.serverAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            is VpnState.Connecting -> {
                Text(
                    text = "SECURING PROTOCOL...",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldGlow,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            is VpnState.Error -> {
                Text(
                    text = "CONNECTION ALERT",
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonAlert,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonAlert,
                    textAlign = TextAlign.Center
                )
            }
            else -> {
                Text(
                    text = "DISCONNECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = if (activeProfile != null) "Ready: ${activeProfile.name}" else "No Profile Configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Traffic & Latency Dashboard Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Download",
                value = formatSpeed(stats.speedInBps),
                icon = Icons.Default.ArrowDownward,
                accentColor = EmeraldActive
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Upload",
                value = formatSpeed(stats.speedOutBps),
                icon = Icons.Default.ArrowUpward,
                accentColor = GoldAccent
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Duration",
                value = formatDuration(stats.durationSeconds),
                icon = Icons.Default.Speed,
                accentColor = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Profile & Endpoint Selector
        ActiveProfileCard(
            profile = activeProfile,
            onManageClick = onNavigateProfiles,
            onSelectNodeClick = { showNodeSelector = !showNodeSelector }
        )

        // Collapsible Node Selection List
        AnimatedVisibility(visible = showNodeSelector && activeProfile != null && activeProfile.proxies.isNotEmpty()) {
            NodeListSheet(
                profile = activeProfile!!,
                onNodeSelected = { node ->
                    viewModel.selectNode(activeProfile.id, node.id)
                    showNodeSelector = false
                }
            )
        }
    }
}

@Composable
private fun ConnectionOrbSection(
    state: VpnState,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val isConnected = state is VpnState.Connected
    val isConnecting = state is VpnState.Connecting

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(180.dp)
    ) {
        // Outer halo ring
        if (isConnected || isConnecting) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .scale(if (isConnected) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isConnected) {
                                listOf(EmeraldActive.copy(alpha = 0.25f), Color.Transparent)
                            } else {
                                listOf(GoldAccent.copy(alpha = 0.25f), Color.Transparent)
                            }
                        )
                    )
            )
        }

        // Inner solid button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isConnected) {
                            listOf(EmeraldActive, Color(0xFF059669))
                        } else if (isConnecting) {
                            listOf(DarkSurface, CardSurface)
                        } else {
                            listOf(CardSurface, DarkSurface)
                        }
                    )
                )
                .border(
                    width = 2.dp,
                    color = if (isConnected) EmeraldActive else if (isConnecting) GoldAccent else CardBorder,
                    shape = CircleShape
                )
                .clickable { onClick() }
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = GoldAccent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Toggle Connection",
                    tint = if (isConnected) BlackObsidian else TextPrimary,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ActiveProfileCard(
    profile: NetworkProfile?,
    onManageClick: () -> Unit,
    onSelectNodeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.clickable { onManageClick() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (profile != null) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                val selectedNode = profile.proxies.find { it.id == profile.selectedProxyId } ?: profile.proxies.firstOrNull()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable { onSelectNodeClick() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Endpoint: ${selectedNode?.name ?: "Auto"} (${selectedNode?.type?.displayName ?: "Direct"})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${profile.proxyCount} servers ▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAccent
                    )
                }
            } else {
                Text(
                    text = "No profile imported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun NodeListSheet(
    profile: NetworkProfile,
    onNodeSelected: (ProxyNode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            items(profile.proxies) { node ->
                val isSelected = node.id == profile.selectedProxyId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) DarkSurface else Color.Transparent)
                        .clickable { onNodeSelected(node) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = node.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) GoldAccent else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = "${node.type.displayName} • ${node.server}:${node.port}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
