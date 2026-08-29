package com.ajaz.tiktok.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.ajaz.tiktok.ui.theme.CardSurface
import com.ajaz.tiktok.ui.theme.CrimsonAlert
import com.ajaz.tiktok.ui.theme.DarkSurface
import com.ajaz.tiktok.ui.theme.EmeraldActive
import com.ajaz.tiktok.ui.theme.GoldAccent
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
            .background(BlackObsidian)
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
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${filteredLogs.size} events captured (sanitized)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Row {
                IconButton(
                    onClick = {
                        val text = logs.joinToString("\n") { "[${it.formattedTime}] [${it.level.label}] [${it.tag}] ${it.message}" }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Ajaz Logs", text))
                        Toast.makeText(context, "Diagnostic logs copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary)
                }

                IconButton(
                    onClick = { viewModel.clearLogs() }
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Clear", tint = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Level Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LevelFilterChip(label = "ALL", isSelected = selectedLevel == null, onClick = { selectedLevel = null })
            LevelFilterChip(label = "INFO", isSelected = selectedLevel == LogLevel.INFO, onClick = { selectedLevel = LogLevel.INFO })
            LevelFilterChip(label = "WARN", isSelected = selectedLevel == LogLevel.WARN, onClick = { selectedLevel = LogLevel.WARN })
            LevelFilterChip(label = "ERROR", isSelected = selectedLevel == LogLevel.ERROR, onClick = { selectedLevel = LogLevel.ERROR })
        }

        Spacer(modifier = Modifier.height(12.dp))

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs) { entry ->
                    LogItemView(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun LevelFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) GoldAccent else CardSurface)
            .border(1.dp, if (isSelected) GoldAccent else CardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) BlackObsidian else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun LogItemView(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.DEBUG -> TextMuted
        LogLevel.INFO -> EmeraldActive
        LogLevel.WARN -> AmberWarning
        LogLevel.ERROR -> CrimsonAlert
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.formattedTime,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "[${entry.level.label}]",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = levelColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "<${entry.tag}>",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = GoldAccent
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = entry.message,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
