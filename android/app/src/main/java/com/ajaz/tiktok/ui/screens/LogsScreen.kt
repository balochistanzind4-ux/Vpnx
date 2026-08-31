package com.ajaz.tiktok.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajaz.tiktok.core.logger.LogEntry
import com.ajaz.tiktok.core.logger.LogLevel
import com.ajaz.tiktok.ui.theme.AmberWarning
import com.ajaz.tiktok.ui.theme.BlackObsidian
import com.ajaz.tiktok.ui.theme.CardBorder
import com.ajaz.tiktok.ui.theme.CardBorderLight
import com.ajaz.tiktok.ui.theme.CardSurface
import com.ajaz.tiktok.ui.theme.CardSurfaceElevated
import com.ajaz.tiktok.ui.theme.CrimsonAlert
import com.ajaz.tiktok.ui.theme.DarkSurface
import com.ajaz.tiktok.ui.theme.DarkSurfaceElevated
import com.ajaz.tiktok.ui.theme.DeepCharcoal
import com.ajaz.tiktok.ui.theme.EmeraldActive
import com.ajaz.tiktok.ui.theme.GoldAccent
import com.ajaz.tiktok.ui.theme.GoldSurface
import com.ajaz.tiktok.ui.theme.TextMuted
import com.ajaz.tiktok.ui.theme.TextPrimary
import com.ajaz.tiktok.ui.theme.TextSecondary
import com.ajaz.tiktok.ui.viewmodel.MainViewModel

@Composable
fun LogsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsState()
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }

    val filteredLogs = remember(logs, selectedLevel) {
        if (selectedLevel == null) logs else logs.filter { it.level == selectedLevel }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BlackObsidian, DeepCharcoal)
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Diagnostic Stream",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${filteredLogs.size} events captured (sanitized)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TactileIconButton(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    onClick = {
                        val text = logs.joinToString("\n") { "[${it.formattedTime}] [${it.level.label}] [${it.tag}] ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Ajaz Logs", text))
                        Toast.makeText(context, "Diagnostic logs copied", Toast.LENGTH_SHORT).show()
                    }
                )

                TactileIconButton(
                    icon = Icons.Default.CleaningServices,
                    contentDescription = "Clear",
                    onClick = { viewModel.clearLogs() }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Level Filter Chips with 3D glow
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LevelFilter3DChip(label = "ALL", count = logs.size, isSelected = selectedLevel == null, onClick = { selectedLevel = null })
            LevelFilter3DChip(label = "INFO", count = logs.count { it.level == LogLevel.INFO }, isSelected = selectedLevel == LogLevel.INFO, onClick = { selectedLevel = LogLevel.INFO })
            LevelFilter3DChip(label = "WARN", count = logs.count { it.level == LogLevel.WARN }, isSelected = selectedLevel == LogLevel.WARN, onClick = { selectedLevel = LogLevel.WARN })
            LevelFilter3DChip(label = "ERROR", count = logs.count { it.level == LogLevel.ERROR }, isSelected = selectedLevel == LogLevel.ERROR, onClick = { selectedLevel = LogLevel.ERROR })
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No log events recorded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(CardBorderLight.copy(alpha = 0.6f), CardBorder.copy(alpha = 0.2f))
                    )
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs) { entry ->
                        LogItem3DView(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "btnPress"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, CardBorderLight, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LevelFilter3DChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "chipScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .shadow(
                elevation = if (isSelected) 4.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = GoldAccent
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) {
                    Brush.verticalGradient(listOf(GoldAccent, Color(0xFFB89327)))
                } else {
                    Brush.verticalGradient(listOf(DarkSurfaceElevated, DarkSurface))
                }
            )
            .border(
                width = 1.dp,
                color = if (isSelected) GoldAccent else CardBorderLight,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (isSelected) BlackObsidian else TextSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "($count)",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isSelected) BlackObsidian.copy(alpha = 0.8f) else TextMuted
                )
            }
        }
    }
}

@Composable
private fun LogItem3DView(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> TextMuted
        LogLevel.INFO -> EmeraldActive
        LogLevel.WARN -> AmberWarning
        LogLevel.ERROR -> CrimsonAlert
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(CardSurface.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.formattedTime,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "[${entry.level.label}]",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = levelColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "<${entry.tag}>",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = GoldAccent
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
