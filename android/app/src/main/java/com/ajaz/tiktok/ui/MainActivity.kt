package com.ajaz.tiktok.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajaz.tiktok.core.logger.AppLogger
import com.ajaz.tiktok.ui.screens.HomeScreen
import com.ajaz.tiktok.ui.screens.LogsScreen
import com.ajaz.tiktok.ui.screens.ProfileScreen
import com.ajaz.tiktok.ui.screens.SettingsScreen
import com.ajaz.tiktok.ui.theme.AjazTiktokTheme
import com.ajaz.tiktok.ui.theme.BlackObsidian
import com.ajaz.tiktok.ui.theme.CardBorder
import com.ajaz.tiktok.ui.theme.CardBorderLight
import com.ajaz.tiktok.ui.theme.CardSurface
import com.ajaz.tiktok.ui.theme.CardSurfaceElevated
import com.ajaz.tiktok.ui.theme.DarkSurface
import com.ajaz.tiktok.ui.theme.DarkSurfaceElevated
import com.ajaz.tiktok.ui.theme.GoldAccent
import com.ajaz.tiktok.ui.theme.GoldSurface
import com.ajaz.tiktok.ui.theme.TextMuted
import com.ajaz.tiktok.ui.theme.TextSecondary
import com.ajaz.tiktok.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            AppLogger.i("MainActivity", "User granted Android VPN permission")
            viewModel.startConnection()
        } else {
            AppLogger.w("MainActivity", "User declined VPN permission request")
            Toast.makeText(this, "VPN permission required for network routing", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if opened via custom scheme (e.g. clash://install-config?url=... or ajaz://)
        intent?.data?.let { uri ->
            AppLogger.i("MainActivity", "Launched with URI intent: $uri")
            val urlParam = uri.getQueryParameter("url")
            val nameParam = uri.getQueryParameter("name") ?: "Imported Link"
            if (!urlParam.isNullOrBlank()) {
                viewModel.importFromUrl(urlParam, nameParam)
            }
        }

        setContent {
            AjazTiktokTheme {
                MainAppContent(
                    viewModel = viewModel,
                    onRequestVpnPermission = { prepareIntent ->
                        vpnPermissionLauncher.launch(prepareIntent)
                    }
                )
            }
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    onRequestVpnPermission: (Intent) -> Unit
) {
    var currentTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage by viewModel.userMessage.collectAsState()

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 12.dp, spotColor = Color.Black)
                    .border(
                        width = 1.dp,
                        shape = RoundedCornerShape(12.dp),
                        brush = Brush.verticalGradient(
                            listOf(CardBorderLight.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            ) {
                NavigationBar(
                    containerColor = DarkSurfaceElevated,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard", fontSize = 11.sp, fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GoldAccent,
                            selectedTextColor = GoldAccent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = GoldSurface
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.ListAlt, contentDescription = "Profiles") },
                        label = { Text("Profiles", fontSize = 11.sp, fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GoldAccent,
                            selectedTextColor = GoldAccent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = GoldSurface
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.AltRoute, contentDescription = "Logs") },
                        label = { Text("Diagnostics", fontSize = 11.sp, fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GoldAccent,
                            selectedTextColor = GoldAccent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = GoldSurface
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings", fontSize = 11.sp, fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GoldAccent,
                            selectedTextColor = GoldAccent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = GoldSurface
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BlackObsidian)
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 200),
                label = "tabCrossfade"
            ) { tab ->
                when (tab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onRequestVpnPermission = onRequestVpnPermission,
                        onNavigateProfiles = { currentTab = 1 }
                    )
                    1 -> ProfileScreen(viewModel = viewModel)
                    2 -> LogsScreen(viewModel = viewModel)
                    3 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
