package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ReceiverConnectionMode
import com.example.ui.components.CsThresholdDialog
import com.example.ui.components.DriverInstallGuideDialog
import com.example.ui.components.FftExplanationDialog
import com.example.ui.components.FirstLaunchDriverPromptDialog
import com.example.ui.components.FrequencyDialog
import com.example.ui.components.GainDialog
import com.example.ui.components.PpmDialog
import com.example.ui.components.RouteStationKmDialog
import com.example.ui.components.SignalLossDialog
import com.example.ui.components.TrainTypeRuleDialog
import com.example.ui.components.WatchlistDialog
import com.example.ui.screens.DailyCsvScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryDetailScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PacketLogScreen
import com.example.ui.screens.RoutesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrainInfoScreen
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PrimaryBlueSoft
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.screens.LocomotiveLibraryScreen
import com.example.util.LocomotiveLibrarySource
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: LbjViewModel) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val exportHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val count = viewModel.exportHistoryCsv(uri)
                Toast.makeText(
                    context,
                    "已导出 " + count + " 条 LBJ 信号记录",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "CSV 导出失败：" + (e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val importLocomotiveLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val count = viewModel.importLocomotiveLibrary(uri)
                Toast.makeText(
                    context,
                    "已导入 $count 项车型",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "车型库导入失败：" + (e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val exportLocomotiveLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val count = viewModel.exportLocomotiveLibrary(uri)
                Toast.makeText(
                    context,
                    "已导出 $count 项车型",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "车型库导出失败：" + (e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    var pendingDailyCsvExportName by remember { mutableStateOf<String?>(null) }

    val importRailwayMapDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val info = viewModel.importRailwayMapData(uri)
                Toast.makeText(
                    context,
                    "已导入地图：" + info.fileName + " · " + info.lineCount + " 条线路 · " + info.stationCount + " 个车站",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "地图数据导入失败：" + (e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    val dailyCsvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val fileName = pendingDailyCsvExportName
        pendingDailyCsvExportName = null
        if (uri != null && fileName != null) {
            scope.launch {
                try {
                    viewModel.exportDailyCsvFile(fileName, uri)
                    Toast.makeText(
                        context,
                        "已导出 " + fileName,
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "每日 CSV 导出失败：" + (e.message ?: "未知错误"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    val importHistoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val result = viewModel.importHistoryCsv(uri)
                val message = if (result.alreadyImported) {
                    "这份 CSV 已导入过，未重复添加记录"
                } else {
                    "已导入 " + result.importedCount + " 条 LBJ 信号记录"
                }
                Toast.makeText(
                    context,
                    message,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "CSV 导入失败：" + (e.message ?: "未知错误"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    val receiverState by viewModel.receiverState.collectAsState()
    val liveTelemetry by viewModel.liveTelemetry.collectAsState()
    val liveEta by viewModel.liveEta.collectAsState()
    val historyRecords by viewModel.historyRecords.collectAsState()
    val savedRoutes by viewModel.savedRouteKms.collectAsState()
    val packetLogs by viewModel.packetLogs.collectAsState()
    val railwayMapData by viewModel.railwayMapData.collectAsState()
    val railwayMapDataInfo by viewModel.railwayMapDataInfo.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedHistoryRecordId by remember { mutableStateOf<Long?>(null) }

    var showLocomotiveLibrary by remember { mutableStateOf(false) }
    var showDailyCsv by remember { mutableStateOf(false) }
    var dailyCsvFiles by remember { mutableStateOf(viewModel.getDailyCsvFiles()) }

    val locomotiveLibraryEntries by viewModel.locomotiveLibraryEntries.collectAsState()
    val locomotiveLibrarySource by viewModel.locomotiveLibrarySource.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var lastBackPressTime by remember { mutableStateOf(0L) }

    BackHandler {
        when {
            selectedHistoryRecordId != null -> {
                selectedHistoryRecordId = null
            }
            showLocomotiveLibrary -> {
                showLocomotiveLibrary = false
            }
            showDailyCsv -> {
                showDailyCsv = false
            }
            selectedTab != 0 -> {
                selectedTab = 0
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime <= 2000L) {
                    context.findActivity()?.finish()
                } else {
                    lastBackPressTime = now
                    scope.launch {
                        snackbarHostState.showSnackbar("再按一次返回退出应用")
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != 2) {
            selectedHistoryRecordId = null
        }
        if (selectedTab != 4) {
            showLocomotiveLibrary = false
            showDailyCsv = false
        }
    }

    if (!receiverState.showPacketLogTab && selectedTab == 5) {
        selectedTab = 0
    }

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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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
                    icon = { Icon(Icons.Default.Train, contentDescription = "列车信息") },
                    label = { Text("列车信息", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_train_info")
                )

                NavigationBarItem(
                    selected = (selectedTab == 2),
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "历史记录") },
                    label = { Text("历史", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_history")
                )

                NavigationBarItem(
                    selected = (selectedTab == 3),
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "位置设置") },
                    label = { Text("位置设置", fontSize = 11.sp, fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_routes")
                )

                NavigationBarItem(
                    selected = (selectedTab == 4),
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置", fontSize = 11.sp, fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal) },
                    colors = navItemColors,
                    modifier = Modifier.testTag("tab_settings")
                )

                if (receiverState.showPacketLogTab) {
                    NavigationBarItem(
                        selected = (selectedTab == 5),
                        onClick = { selectedTab = 5 },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "报文日志") },
                        label = { Text("报文日志", fontSize = 11.sp, fontWeight = if (selectedTab == 5) FontWeight.Bold else FontWeight.Normal) },
                        colors = navItemColors,
                        modifier = Modifier.testTag("tab_packet_logs")
                    )
                }
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
                onStartReceiver = { isSim -> viewModel.startReceiver(isSim) },
                onStopReceiver = { viewModel.stopReceiver() },
                onSetConnectionMode = { viewModel.setConnectionMode(it) },
                onSetTcpEndpoint = { host, port -> viewModel.setTcpEndpoint(host, port) },
                onTestTcpConnection = { viewModel.testTcpConnection() },
                onLaunchDriver = { viewModel.launchAndroidDriver() },
                onClearTelemetry = { viewModel.clearLiveTelemetry() },
                onOpenFreqDialog = { showFreqDialog = true },
                onOpenGainDialog = { showGainDialog = true },
                onToggleTunerAgc = { viewModel.setTunerAgc(it) },
                onToggleRtlAgc = { viewModel.setRtlAgc(it) },
                onOpenPpmDialog = { showPpmDialog = true },
                onOpenCsDialog = { showCsDialog = true },
                onOpenWatchlistDialog = { showWatchlistDialog = true },
                onOpenFftExplanationDialog = { showFftExplanationDialog = true },
                onToggleAlertTone = { viewModel.setAlertToneEnabled(it) },
                onToggleAlertNotification = { viewModel.setAlertNotificationEnabled(it) },
                onToggleBasebandAudio = { viewModel.setBasebandAudioEnabled(it) },
                onDismissWarning = { viewModel.clearWarning() },
                packetLogs = packetLogs,
                onNavigateToPacketLogs = { selectedTab = 5 },
                modifier = screenModifier
            )
            1 -> TrainInfoScreen(
                telemetry = liveTelemetry,
                etaInfo = liveEta,
                currentStationKmText = receiverState.currentRouteStationKmText,
                onOpenTrainTypeRuleDialog = { showTrainTypeRuleDialog = true },
                modifier = screenModifier
            )
            2 -> {
                val selectedRecord = selectedHistoryRecordId?.let { id ->
                    historyRecords.firstOrNull { it.id == id }
                }

                if (selectedRecord == null) {
                    HistoryScreen(
                        records = historyRecords,
                        onClearAll = { viewModel.clearHistory() },
                        onDeleteRecord = { id -> viewModel.deleteHistoryRecord(id) },
                        onOpenRecord = { record -> selectedHistoryRecordId = record.id },
                        onImportCsv = {
                            importHistoryLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/vnd.ms-excel",
                                    "text/*"
                                )
                            )
                        },
                        onExportCsv = {
                            exportHistoryLauncher.launch(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) + ".csv")
                        },
                        modifier = screenModifier
                    )
                } else {
                    val signals by viewModel.getTrainSignalRecords(selectedRecord.id)
                        .collectAsState(initial = emptyList())

                    HistoryDetailScreen(
                        record = selectedRecord,
                        signals = signals,
                        railwayMapData = railwayMapData,
                        onBack = { selectedHistoryRecordId = null },
                        modifier = screenModifier
                    )
                }
            }
            3 -> RoutesScreen(
                savedRoutes = savedRoutes,
                onAddOrEditRoute = { route, km, nickname ->
                    editingRouteName = route
                    editingRouteKm = km
                    editingRouteNickname = nickname
                    showRouteKmDialog = true
                },
                onDeleteRoute = { route -> viewModel.deleteRouteStationKm(route) },
                onImportRoutes = { routes -> viewModel.importRouteStationKms(routes) },
                modifier = screenModifier
            )
            4 -> if (showDailyCsv) {
                DailyCsvScreen(
                    files = dailyCsvFiles,
                    onBack = { showDailyCsv = false },
                    onRefresh = { dailyCsvFiles = viewModel.getDailyCsvFiles() },
                    onExport = { fileName ->
                        pendingDailyCsvExportName = fileName
                        dailyCsvExportLauncher.launch(fileName)
                    },
                    modifier = screenModifier
                )
            } else if (showLocomotiveLibrary) {
                LocomotiveLibraryScreen(
                    source = locomotiveLibrarySource,
                    entries = locomotiveLibraryEntries,
                    onBack = { showLocomotiveLibrary = false },
                    onSelectSource = { viewModel.selectLocomotiveLibrary(it) },
                    onAddOrEdit = { code, name ->
                        try {
                            viewModel.saveLocomotiveEntry(code, name)
                            null
                        } catch (e: Exception) {
                            e.message ?: "保存车型失败"
                        }
                    },
                    onDelete = { viewModel.deleteLocomotiveEntry(it) },
                    onImport = {
                        importLocomotiveLibraryLauncher.launch(
                            arrayOf("text/plain", "text/*", "application/octet-stream")
                        )
                    },
                    onExport = {
                        exportLocomotiveLibraryLauncher.launch(
                            if (locomotiveLibrarySource == LocomotiveLibrarySource.BUILTIN) {
                                "LBJ-Builtin-Locomotive-Library.txt"
                            } else {
                                "LBJ-External-Locomotive-Library.txt"
                            }
                        )
                    },
                    modifier = screenModifier
                )
            } else SettingsScreen(
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
                onToggleKeepScreenOn = { viewModel.setKeepScreenOn(it) },
                onToggleSimulationButton = { viewModel.setShowSimulationButton(it) },
                onTogglePacketLogTab = { viewModel.setShowPacketLogTab(it) },
                onSelectTtsEngineMode = { viewModel.setTtsEngineMode(it) },
                onSelectThemeMode = { viewModel.setThemeMode(it) },
                onClearTtsCache = { viewModel.clearTtsCache() },
                onToggleEnableExternalAutomation = { viewModel.setEnableExternalAutomation(it) },
                onOpenLocomotiveLibrary = { showLocomotiveLibrary = true },
                onOpenDailyCsv = {
                    dailyCsvFiles = viewModel.getDailyCsvFiles()
                    showDailyCsv = true
                },
                railwayMapDataInfo = railwayMapDataInfo,
                onImportRailwayMapData = {
                    importRailwayMapDataLauncher.launch(
                        arrayOf(
                            "application/json",
                            "application/geo+json",
                            "text/json",
                            "text/plain",
                            "*/*"
                        )
                    )
                },
                onResetAllSettings = { viewModel.resetAllSettings() },
                onLaunchDriver = { viewModel.launchAndroidDriver() },
                onInstallDriver = { viewModel.openDriverInstallGuide() },
                onTestVoiceBroadcast = { viewModel.testVoiceBroadcast() },
                modifier = screenModifier
            )
            5 -> PacketLogScreen(
                packetLogs = packetLogs,
                onClearLogs = { viewModel.clearPacketLogs() }
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

    if (receiverState.showFirstLaunchDriverPrompt) {
        FirstLaunchDriverPromptDialog(
            onConfirmAlreadyInstalled = { viewModel.onUserConfirmDriverAlreadyInstalled() },
            onSelectNotInstalled = { viewModel.onUserSelectDriverNotInstalled() },
            onDismiss = { viewModel.dismissFirstLaunchDriverPrompt() }
        )
    }

    if (receiverState.showDriverInstallGuideDialog) {
        DriverInstallGuideDialog(
            onInstall = { viewModel.installDriverApk() },
            onDismiss = { viewModel.dismissDriverInstallGuide() }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
