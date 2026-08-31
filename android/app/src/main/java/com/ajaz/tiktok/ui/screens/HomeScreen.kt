package com.ajaz.tiktok.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajaz.tiktok.core.parser.NetworkProfile
import com.ajaz.tiktok.core.parser.ProxyNode
import com.ajaz.tiktok.core.vpn.VpnState
import com.ajaz.tiktok.ui.theme.AmberWarning
import com.ajaz.tiktok.ui.theme.BlackObsidian
import com.ajaz.tiktok.ui.theme.CardBorder
import com.ajaz.tiktok.ui.theme.CardBorderLight
import com.ajaz.tiktok.ui.theme.CardHighlight
import com.ajaz.tiktok.ui.theme.CardSurface
import com.ajaz.tiktok.ui.theme.CardSurfaceElevated
import com.ajaz.tiktok.ui.theme.CrimsonAlert
import com.ajaz.tiktok.ui.theme.CrimsonGlow
import com.ajaz.tiktok.ui.theme.DarkSurface
import com.ajaz.tiktok.ui.theme.DarkSurfaceElevated
import com.ajaz.tiktok.ui.theme.DeepCharcoal
import com.ajaz.tiktok.ui.theme.EmeraldActive
import com.ajaz.tiktok.ui.theme.EmeraldGlow
import com.ajaz.tiktok.ui.theme.GoldAccent
import com.ajaz.tiktok.ui.theme.GoldGlow
import com.ajaz.tiktok.ui.theme.GoldMuted
import com.ajaz.tiktok.ui.theme.GoldSurface
import com.ajaz.tiktok.ui.theme.TextDisabled
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BlackObsidian, DeepCharcoal)
                )
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Luxury 3D Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ajaz×tiktok",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 22.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = GoldAccent,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GoldSurface)
                                .border(0.5.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SECURE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldAccent
                            )
                        }
                    }
                    Text(
                        text = "Secure Connection",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }

                // Physical Network Status 3D Pill
                Box(
                    modifier = Modifier
                        .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(DarkSurfaceElevated, DarkSurface)
                            )
                        )
                        .border(1.dp, CardBorderLight.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (networkStatus.isConnected) EmeraldActive else CrimsonAlert)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Icon(
                            imageVector = if (networkStatus.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (networkStatus.isConnected) EmeraldActive else CrimsonAlert,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = networkStatus.typeName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Center 3D Interactive Connection Orb Section
        item {
            Spacer(modifier = Modifier.height(10.dp))
            InteractiveConnectionOrb(
                state = vpnState,
                onClick = {
                    viewModel.toggleConnection { prepareIntent ->
                        onRequestVpnPermission(prepareIntent)
                    }
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Status Card & State Transition Container
        item {
            ConnectionStatusDisplay(
                state = vpnState,
                activeProfile = activeProfile,
                onRetry = {
                    viewModel.toggleConnection { prepareIntent ->
                        onRequestVpnPermission(prepareIntent)
                    }
                },
                onChangeService = {
                    showNodeSelector = true
                }
            )
        }

        // Traffic & Latency 3D Tactile Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TactileMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Download",
                    value = formatSpeed(stats.speedInBps),
                    icon = Icons.Default.ArrowDownward,
                    accentColor = EmeraldActive,
                    glowColor = EmeraldGlow
                )
                TactileMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Upload",
                    value = formatSpeed(stats.speedOutBps),
                    icon = Icons.Default.ArrowUpward,
                    accentColor = GoldAccent,
                    glowColor = GoldGlow
                )
                TactileMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Duration",
                    value = formatDuration(stats.durationSeconds),
                    icon = Icons.Default.Speed,
                    accentColor = TextSecondary,
                    glowColor = Color.White
                )
            }
        }

        // Active Profile & Service Selector Card with 3D Depth
        item {
            ActiveProfileElevationCard(
                profile = activeProfile,
                onManageClick = onNavigateProfiles,
                onToggleNodeList = { showNodeSelector = !showNodeSelector },
                isNodeListExpanded = showNodeSelector
            )
        }

        // Expandable Smooth 3D Service Selector
        item {
            AnimatedVisibility(
                visible = showNodeSelector && activeProfile != null && activeProfile.proxies.isNotEmpty(),
                enter = fadeIn(tween(250)) + expandVertically(spring(dampingRatio = 0.8f)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                ServiceSelector3DCard(
                    profile = activeProfile!!,
                    onNodeSelected = { node ->
                        viewModel.selectNode(activeProfile.id, node.id)
                        showNodeSelector = false
                    }
                )
            }
        }
    }
}

@Composable
private fun InteractiveConnectionOrb(
    state: VpnState,
    onClick: () -> Unit
) {
    val isConnected = state is VpnState.Connected
    val isConnecting = state is VpnState.Connecting
    val isError = state is VpnState.Error

    // Idle subtle floating / breathing animation
    val infiniteTransition = rememberInfiniteTransition(label = "orbTransition")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.06f else if (isConnecting) 1.04f else 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isConnected) 1800 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = if (isConnected) 0.40f else if (isConnecting) 0.50f else 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Tactile press state
    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pressScale"
    )

    val buttonBorderColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldActive
            isConnecting -> GoldAccent
            isError -> CrimsonAlert
            else -> CardBorderLight
        },
        animationSpec = tween(400),
        label = "buttonBorderColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(190.dp)
            .scale(pressScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            }
    ) {
        // Outer 3D Ambient Halo Rings
        Box(
            modifier = Modifier
                .size(184.dp)
                .scale(breathingScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = when {
                            isConnected -> listOf(EmeraldGlow.copy(alpha = breathingAlpha), Color.Transparent)
                            isConnecting -> listOf(GoldGlow.copy(alpha = breathingAlpha), Color.Transparent)
                            isError -> listOf(CrimsonGlow.copy(alpha = breathingAlpha * 0.8f), Color.Transparent)
                            else -> listOf(GoldAccent.copy(alpha = breathingAlpha * 0.4f), Color.Transparent)
                        }
                    )
                )
        )

        // Middle Orbiting Ring for Connecting
        if (isConnecting) {
            Box(
                modifier = Modifier
                    .size(152.dp)
                    .rotate(rotationAngle)
                    .clip(CircleShape)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                GoldAccent.copy(alpha = 0.2f),
                                GoldAccent,
                                GoldGlow,
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // Convex 3D Outer Bezel
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(136.dp)
                .shadow(
                    elevation = if (isConnected) 16.dp else 10.dp,
                    shape = CircleShape,
                    spotColor = if (isConnected) EmeraldActive else if (isConnecting) GoldAccent else Color.Black
                )
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            CardSurfaceElevated,
                            DeepCharcoal
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            buttonBorderColor.copy(alpha = 0.9f),
                            buttonBorderColor.copy(alpha = 0.3f)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            // Inner Core Button with 3D Radial Depth
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(114.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = when {
                                isConnected -> listOf(EmeraldActive, Color(0xFF047857), Color(0xFF064E3B))
                                isConnecting -> listOf(DarkSurfaceElevated, DeepCharcoal, BlackObsidian)
                                isError -> listOf(Color(0xFF7F1D1D), Color(0xFF450A0A), BlackObsidian)
                                else -> listOf(CardSurfaceElevated, DarkSurface, DeepCharcoal)
                            },
                            center = Offset(57f, 35f), // Top-light reflection
                            radius = 90f
                        )
                    )
                    .drawBehind {
                        // Top rim specular highlight
                        drawCircle(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                                startY = 0f,
                                endY = size.height * 0.4f
                            )
                        )
                    }
            ) {
                if (isConnecting) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = GoldAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CONNECTING",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp,
                            color = GoldGlow
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle Connection",
                            tint = when {
                                isConnected -> BlackObsidian
                                isError -> CrimsonGlow
                                else -> GoldAccent
                            },
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isConnected -> "PROTECTED"
                                isError -> "RETRY"
                                else -> "CONNECT"
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = when {
                                isConnected -> BlackObsidian.copy(alpha = 0.9f)
                                isError -> CrimsonGlow
                                else -> TextSecondary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusDisplay(
    state: VpnState,
    activeProfile: NetworkProfile?,
    onRetry: () -> Unit,
    onChangeService: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    when (state) {
                        is VpnState.Connected -> EmeraldActive.copy(alpha = 0.5f)
                        is VpnState.Connecting -> GoldAccent.copy(alpha = 0.5f)
                        is VpnState.Error -> CrimsonAlert.copy(alpha = 0.6f)
                        else -> CardBorderLight.copy(alpha = 0.4f)
                    },
                    CardBorder.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is VpnState.Connected -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldActive,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECURE CONNECTION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = EmeraldActive
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.serverName,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Status: Protected",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                is VpnState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = GoldAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CONNECTING...",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = GoldGlow
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connecting to secure network...",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                is VpnState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CrimsonAlert,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CONNECTION PROBLEM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = CrimsonAlert
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    if (!state.recoveryAction.isNullOrBlank()) {
                        Text(
                            text = state.recoveryAction,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // In-place recovery buttons without app restart
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = BlackObsidian, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", color = BlackObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onChangeService,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderLight)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change Location", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DISCONNECTED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (activeProfile != null) "Ready on ${activeProfile.name}" else "No Profile Selected",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun TactileMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    glowColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "metricScale"
    )

    Card(
        modifier = modifier
            .scale(scale)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp), spotColor = Color.Black),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(CardBorderLight.copy(alpha = 0.7f), CardBorder.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
private fun ActiveProfileElevationCard(
    profile: NetworkProfile?,
    onManageClick: () -> Unit,
    onToggleNodeList: () -> Unit,
    isNodeListExpanded: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(GoldAccent.copy(alpha = 0.4f), CardBorder.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(GoldAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SELECTED PROFILE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = GoldAccent
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onManageClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (profile != null) {
                val selectedNode = profile.proxies.find { it.id == profile.selectedProxyId } ?: profile.proxies.firstOrNull()
                val flag = if (selectedNode != null) UiHelpers.getCountryFlag(selectedNode.name, selectedNode.server) else "🌐"

                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Interactive 3D Selected Node Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(CardSurfaceElevated, CardSurface)
                            )
                        )
                        .border(1.dp, CardBorderLight, RoundedCornerShape(12.dp))
                        .clickable { onToggleNodeList() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = flag,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = selectedNode?.name ?: "Automatic Location",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Secure Route",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldActive
                                )
                                Text(
                                    text = " • Optimal",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${profile.proxyCount} Locations",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(if (isNodeListExpanded) 180f else 0f)
                        )
                    }
                }
            } else {
                Text(
                    text = "No profile selected. Go to Profiles to add one.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ServiceSelector3DCard(
    profile: NetworkProfile,
    onNodeSelected: (ProxyNode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderLight)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Text(
                text = "SELECT LOCATION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = GoldAccent,
                modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(profile.proxies) { node ->
                    val isSelected = node.id == profile.selectedProxyId
                    val flag = UiHelpers.getCountryFlag(node.name, node.server)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (isSelected) 4.dp else 0.dp,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) {
                                    Brush.horizontalGradient(
                                        listOf(DarkSurfaceElevated, CardSurfaceElevated)
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(DarkSurface.copy(alpha = 0.5f), DarkSurface.copy(alpha = 0.5f))
                                    )
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) GoldAccent.copy(alpha = 0.8f) else CardBorder.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onNodeSelected(node) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = flag, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = node.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = if (isSelected) GoldAccent else TextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Available",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldActive
                                    )
                                    Text(
                                        text = " • Fast Connection",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(GoldAccent.copy(alpha = 0.2f))
                                    .padding(4.dp)
                            ) {
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
