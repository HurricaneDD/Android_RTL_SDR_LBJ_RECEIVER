package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CsThresholdDialog
import com.example.ui.components.FftExplanationDialog
import com.example.ui.components.FrequencyDialog
import com.example.ui.components.GainDialog
import com.example.ui.components.PpmDialog
import com.example.ui.components.RouteStationKmDialog
import com.example.ui.components.SignalLossDialog
import com.example.ui.components.TrainTypeRuleDialog
import com.example.ui.components.WatchlistDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.RoutesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PrimaryBlueSoft
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LbjViewModel) {
    val receiverState by viewModel.receiverState.collectAsState()
    val liveTelemetry by viewModel.liveTelemetry.collectAsState()
    val liveEta by viewModel.liveEta.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    val savedRoutes by viewModel.savedRouteKms.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Dialog state controllers
    var showFreqDialog by remember { mutableStateOf(false) }
    var showGainDialog by remember { mutableStateOf(false) }
    var showPpmDialog by remember { mutableStateOf(false) }
    var showCsDialog by remember { mutableStateOf(false) }
    var showWatchlistDialog by remember { mutableStateOf(false) }
    var showRouteKmDialog by remember { mutableStateOf(false) }
    var showFftExplanationDialog by remember { mutableStateOf(false) }
    var showTrainTypeRuleDialog by remember { mutableStateOf(false) }
    var editingRouteName by remember { mutableStateOf("") }
    var editingRouteKm by remember { mutableStateOf<Double?>(null) }
    var editingRouteNickname by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SDR-LBJ",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCard)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                val navItemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlueDark,
                    selectedTextColor = PrimaryBlueDark,
                    indicatorColor = PrimaryBlueSoft,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                )

                NavigationBarItem(
                    selected = (selectedTab == 0),
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "仪表盘") },
                    label = { Text("仪表盘", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_dashboard")
                )

                NavigationBarItem(
                    selected = (selectedTab == 1),
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "历史记录") },
                    label = { Text("历史", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_history")
                )

                NavigationBarItem(
                    selected = (selectedTab == 2),
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "位置设置") },
                    label = { Text("位置设置", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_routes")
                )

                NavigationBarItem(
                    selected = (selectedTab == 3),
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        },
        containerColor = BackgroundLight
    ) { paddingValues ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)

        when (selectedTab) {
            0 -> DashboardScreen(
                state = receiverState,
                telemetry = liveTelemetry,
                etaInfo = liveEta,
                onStartReceiver = { isSim -> viewModel.startReceiver(isSim) },
                onStopReceiver = { viewModel.stopReceiver() },
                onLaunchDriver = { viewModel.launchAndroidDriver() },
                onClearTelemetry = { viewModel.clearLiveTelemetry() },
                onOpenFreqDialog = { showFreqDialog = true },
                onOpenGainDialog = { showGainDialog = true },
                onOpenPpmDialog = { showPpmDialog = true },
                onOpenCsDialog = { showCsDialog = true },
                onOpenWatchlistDialog = { showWatchlistDialog = true },
                onOpenFftExplanationDialog = { showFftExplanationDialog = true },
                onOpenTrainTypeRuleDialog = { showTrainTypeRuleDialog = true },
                modifier = screenModifier
            )
            1 -> HistoryScreen(
                records = historyRecords,
                onClearAll = { viewModel.clearHistory() },
                onDeleteRecord = { id -> viewModel.deleteHistoryRecord(id) },
                modifier = screenModifier
            )
            2 -> RoutesScreen(
                savedRoutes = savedRoutes,
                onAddOrEditRoute = { route, km, nickname ->
                    editingRouteName = route
                    editingRouteKm = km
                    editingRouteNickname = nickname
                    showRouteKmDialog = true
                },
                onDeleteRoute = { route -> viewModel.deleteRouteStationKm(route) },
                modifier = screenModifier
            )
            3 -> SettingsScreen(
                state = receiverState,
                onOpenFreqDialog = { showFreqDialog = true },
                onOpenGainDialog = { showGainDialog = true },
                onOpenPpmDialog = { showPpmDialog = true },
                onOpenCsDialog = { showCsDialog = true },
                onOpenWatchlistDialog = { showWatchlistDialog = true },
                onToggleStrictFilter = { viewModel.setStrictFilter(it) },
                onToggleShowErrWarn = { viewModel.setShowErrWarn(it) },
                onToggleFilterMode = { viewModel.setFilterMode(it) },
                onToggleBroadcastAlerts = { viewModel.setBroadcastAlerts(it) },
                onToggleAlertTone = { viewModel.setAlertToneEnabled(it) },
                onToggleAlertNotification = { viewModel.setAlertNotificationEnabled(it) },
                onToggleBasebandAudio = { viewModel.setBasebandAudioEnabled(it) },
                onSetBasebandAudioVolume = { viewModel.setBasebandAudioVolume(it) },
                onToggleKeepAlive = { viewModel.setKeepAliveEnabled(it) },
                onToggleSimulationButton = { viewModel.setShowSimulationButton(it) },
                onSelectTtsEngineMode = { viewModel.setTtsEngineMode(it) },
                onSelectThemeMode = { viewModel.setThemeMode(it) },
                onClearTtsCache = { viewModel.clearTtsCache() },
                onToggleEnableExternalAutomation = { viewModel.setEnableExternalAutomation(it) },
                onResetAllSettings = { viewModel.resetAllSettings() },
                onLaunchDriver = { viewModel.launchAndroidDriver() },
                onTestVoiceBroadcast = { viewModel.testVoiceBroadcast() },
                modifier = screenModifier
            )
        }
    }

    // Dialogs
    if (showFreqDialog) {
        FrequencyDialog(
            currentFreqMhz = receiverState.freqHz / 1_000_000.0,
            onDismiss = { showFreqDialog = false },
            onConfirm = { freqMhz ->
                viewModel.setFrequency(freqMhz)
                showFreqDialog = false
            }
        )
    }

    if (showGainDialog) {
        GainDialog(
            currentGainDb = receiverState.gainDb,
            onDismiss = { showGainDialog = false },
            onConfirm = { gainDb ->
                viewModel.setGain(gainDb)
                showGainDialog = false
            }
        )
    }

    if (showPpmDialog) {
        PpmDialog(
            currentPpm = receiverState.ppm,
            onDismiss = { showPpmDialog = false },
            onConfirm = { ppm ->
                viewModel.setPpm(ppm)
                showPpmDialog = false
            }
        )
    }

    if (showCsDialog) {
        CsThresholdDialog(
            currentThresholdDb = receiverState.csThresholdDb,
            onDismiss = { showCsDialog = false },
            onConfirm = { threshold ->
                viewModel.setCsThreshold(threshold)
                showCsDialog = false
            }
        )
    }

    if (showWatchlistDialog) {
        WatchlistDialog(
            currentKeywords = receiverState.keywords,
            onDismiss = { showWatchlistDialog = false },
            onConfirm = { list ->
                viewModel.setKeywords(list)
                showWatchlistDialog = false
            }
        )
    }

    if (showRouteKmDialog) {
        RouteStationKmDialog(
            initialRoute = editingRouteName,
            initialKm = editingRouteKm,
            initialNickname = editingRouteNickname,
            onDismiss = { showRouteKmDialog = false },
            onConfirm = { route, km, nickname ->
                viewModel.setRouteStationKm(route, km, nickname)
                showRouteKmDialog = false
            }
        )
    }

    if (showFftExplanationDialog) {
        FftExplanationDialog(
            onDismiss = { showFftExplanationDialog = false }
        )
    }

    if (showTrainTypeRuleDialog) {
        TrainTypeRuleDialog(
            onDismiss = { showTrainTypeRuleDialog = false }
        )
    }

    if (receiverState.showSignalLossDialog) {
        SignalLossDialog(
            onDismiss = { viewModel.dismissSignalLossDialog() },
            onOpenDriverSettings = {
                viewModel.openDriverAppSettings()
            }
        )
    }
}
