package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.PacketLogItem
import com.example.ui.ReceiverConnectionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.driver.RtlTcpClient
import com.example.ui.ReceiverState
import com.example.ui.components.SpectrumWaterfallView
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.BorderLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PrimaryBlueSoft
import com.example.ui.theme.RedAlert
import com.example.ui.theme.RedSoft
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun DashboardScreen(
    state: ReceiverState,
    onStartReceiver: (Boolean) -> Unit,
    onStopReceiver: () -> Unit,
    onSetConnectionMode: (ReceiverConnectionMode) -> Unit = {},
    onSetTcpEndpoint: (String, Int) -> String? = { _, _ -> null },
    onTestTcpConnection: () -> Unit = {},
    onLaunchDriver: () -> Unit,
    onClearTelemetry: () -> Unit,
    onOpenFreqDialog: () -> Unit,
    onOpenGainDialog: () -> Unit,
    onToggleTunerAgc: (Boolean) -> Unit,
    onToggleRtlAgc: (Boolean) -> Unit,
    onOpenPpmDialog: () -> Unit,
    onOpenCsDialog: () -> Unit,
    onOpenWatchlistDialog: () -> Unit,
    onOpenFftExplanationDialog: () -> Unit,
    onToggleAlertTone: (Boolean) -> Unit,
    onToggleAlertNotification: (Boolean) -> Unit,
    onToggleBasebandAudio: (Boolean) -> Unit,
    onDismissWarning: () -> Unit = {},
    packetLogs: List<PacketLogItem> = emptyList(),
    onNavigateToPacketLogs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var tcpHostText by remember(state.host) { mutableStateOf(state.host) }
    var tcpPortText by remember(state.port) { mutableStateOf(state.port.toString()) }
    var tcpEndpointError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.host, state.port, state.connectionMode) {
        if (state.connectionMode == ReceiverConnectionMode.TCP) {
            tcpHostText = state.host
            tcpPortText = state.port.toString()
            tcpEndpointError = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Top Action & Status Control Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard, RoundedCornerShape(14.dp))
                .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column {
                // Connection mode selector
                Text(
                    text = "连接方式",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            tcpEndpointError = null
                            onSetConnectionMode(ReceiverConnectionMode.SDR)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connection_mode_sdr")
                    ) {
                        Text(
                            text = if (state.connectionMode == ReceiverConnectionMode.SDR) {
                                "✓ SDR连接"
                            } else {
                                "SDR连接"
                            },
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            tcpEndpointError = null
                            onSetConnectionMode(ReceiverConnectionMode.TCP)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connection_mode_tcp")
                    ) {
                        Text(
                            text = if (state.connectionMode == ReceiverConnectionMode.TCP) {
                                "✓ TCP连接"
                            } else {
                                "TCP连接"
                            },
                            fontSize = 12.sp
                        )
                    }
                }

                if (state.connectionMode == ReceiverConnectionMode.TCP) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tcpHostText,
                            onValueChange = {
                                tcpHostText = it
                                tcpEndpointError = null
                            },
                            label = { Text("IP / 主机") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tcpPortText,
                            onValueChange = {
                                tcpPortText = it.filter(Char::isDigit).take(5)
                                tcpEndpointError = null
                            },
                            label = { Text("端口") },
                            singleLine = true,
                            modifier = Modifier.width(104.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val port = tcpPortText.toIntOrNull()
                                tcpEndpointError = if (port == null) {
                                    "请输入有效端口"
                                } else {
                                    onSetTcpEndpoint(tcpHostText, port)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("应用地址", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val port = tcpPortText.toIntOrNull()
                                if (port == null || port !in 1..65535) {
                                    tcpEndpointError = "请输入 1~65535 的有效端口"
                                } else {
                                    tcpEndpointError = onSetTcpEndpoint(tcpHostText, port)
                                    if (tcpEndpointError == null) {
                                        onTestTcpConnection()
                                    }
                                }
                            },
                            enabled = !state.isRunning &&
                                tcpHostText.trim().isNotEmpty() &&
                                tcpPortText.toIntOrNull() in 1..65535,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("测试连接", fontSize = 12.sp)
                        }
                    }

                    tcpEndpointError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = it,
                            color = RedAlert,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 1: Status Pill Indicator & Dynamic Current Freq
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val (statusBg, statusFg, statusText) = when {
                        state.isSimulationMode && state.isRunning -> Triple(AmberSoft, AmberSignal, "仿真信号流运行中")
                        state.isRunning && state.connectionMode == ReceiverConnectionMode.TCP ->
                            Triple(EmeraldSoft, EmeraldGreen, "TCP 实时接收中")
                        state.isRunning -> Triple(EmeraldSoft, EmeraldGreen, "SDR 实时接收中")
                        state.connectionState == RtlTcpClient.ConnectionState.CONNECTING &&
                            state.connectionMode == ReceiverConnectionMode.TCP ->
                            Triple(PrimaryBlueSoft, PrimaryBlueDark, "正在连接 TCP...")
                        state.connectionState == RtlTcpClient.ConnectionState.CONNECTING ->
                            Triple(PrimaryBlueSoft, PrimaryBlueDark, "正在连接驱动...")
                        state.connectionState == RtlTcpClient.ConnectionState.ERROR &&
                            state.connectionMode == ReceiverConnectionMode.TCP ->
                            Triple(RedSoft, RedAlert, "TCP 未连接")
                        state.connectionState == RtlTcpClient.ConnectionState.ERROR ->
                            Triple(RedSoft, RedAlert, "驱动未连接")
                        else -> Triple(SurfaceSecondary, TextMuted, "待接收器")
                    }

                    Box(
                        modifier = Modifier
                            .background(statusBg, RoundedCornerShape(6.dp))
                            .border(1.dp, statusFg.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusFg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Dynamically formatted frequency in top right
                    Text(
                        text = String.format(Locale.US, "%.4f MHz", state.freqHz / 1_000_000.0),
                        color = PrimaryBlueDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row 2: Prominent Main Action Button (加大加宽的 开始接收 / 停止接收 按钮)
                if (!state.isRunning) {
                    Button(
                        onClick = { onStartReceiver(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_sdr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            tint = Color.White,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "开始接收",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Button(
                        onClick = onStopReceiver,
                        colors = ButtonDefaults.buttonColors(containerColor = RedAlert),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("stop_receiver_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = "停止接收",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 3: Auxiliary Action Buttons Row (尝试驱动设备、清屏，以及开发者选项开启后的仿真演示)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.showSimulationButton) {
                        // 虚拟数据演示 (仅在开发者选项开启时显示)
                        OutlinedButton(
                            onClick = { onStartReceiver(true) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.05f)
                                .height(40.dp)
                                .testTag("start_simulation_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Simulate",
                                    tint = AmberSignal,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .padding(end = 3.dp)
                                )
                                Text(
                                    text = "虚拟演示",
                                    color = AmberSignal,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // SDR 模式下提供本机 RTL-SDR 驱动联动；TCP 模式不启动本机驱动。
                    if (state.connectionMode == ReceiverConnectionMode.SDR) {
                        OutlinedButton(
                            onClick = onLaunchDriver,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(if (state.showSimulationButton) 1.35f else 1.5f)
                                .height(40.dp)
                                .testTag("launch_driver_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = "Driver",
                                    tint = PrimaryBlue,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .padding(end = 3.dp)
                                )
                                Text(
                                    text = "尝试重新驱动设备",
                                    color = PrimaryBlueDark,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 清屏
                    OutlinedButton(
                        onClick = onClearTelemetry,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(if (state.showSimulationButton) 0.85f else 1f)
                            .height(40.dp)
                            .testTag("clear_hud_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .padding(end = 3.dp)
                            )
                            Text(
                                text = "清屏",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Red Warning Banner if present (e.g. 连接被拒, 已开启RF信号仿真流演示模式)
                AnimatedVisibility(
                    visible = state.warningMessage.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val warningAnnotated = remember(state.warningMessage) {
                        val cleanText = state.warningMessage.removePrefix("⚠").trimStart()
                        buildAnnotatedString {
                            val regex = Regex("上行|下行")
                            var cursor = 0
                            for (match in regex.findAll(cleanText)) {
                                if (match.range.first > cursor) {
                                    append(cleanText.substring(cursor, match.range.first))
                                }
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(match.value)
                                }
                                cursor = match.range.last + 1
                            }
                            if (cursor < cleanText.length) {
                                append(cleanText.substring(cursor))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .background(RedSoft, RoundedCornerShape(8.dp))
                            .border(1.dp, RedAlert.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = RedAlert,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 6.dp)
                        )
                        Text(
                            text = warningAnnotated,
                            color = RedAlert,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss warning",
                            tint = RedAlert.copy(alpha = 0.8f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onDismissWarning() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Spectrum and Waterfall Visualizer (Clickable for explanation)
        SpectrumWaterfallView(
            spectrumBars = state.spectrumBars,
            freqHz = state.freqHz,
            gainDb = state.gainDb,
            ppm = state.ppm,
            rssiDb = state.rssiDb,
            csThresholdDb = state.csThresholdDb,
            gateState = state.rssiGateState,
            holdMs = state.rssiHoldMs,
            afcHz = state.afcHz,
            afcErrHz = state.afcErrHz,
            afcScore = state.afcScore,
            peakFreqHz = state.peakFreqHz,
            peakDeltaHz = state.peakDeltaHz,
            peakDb = state.peakDb,
            fps = state.fps,
            isReceiving = state.isRunning,
            isAdcClipping = state.isAdcClipping,
            onClick = onOpenFftExplanationDialog
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 播报系统设置
        Text(
            text = "播报系统设置",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                .testTag("broadcast_system_settings_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Item 1: 来车语音播报
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleAlertTone(!state.alertToneEnabled) }
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (state.alertToneEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "语音播报",
                        tint = if (state.alertToneEnabled) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "来车语音播报",
                        color = if (state.alertToneEnabled) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (state.alertToneEnabled) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Switch(
                        checked = state.alertToneEnabled,
                        onCheckedChange = onToggleAlertTone,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue,
                            uncheckedTrackColor = SurfaceSecondary
                        ),
                        modifier = Modifier
                            .scale(0.75f)
                            .height(26.dp)
                            .testTag("dashboard_toggle_speech_alert")
                    )
                }

                // Divider line 1
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(BorderLight)
                )

                // Item 2: 来车提示通知
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleAlertNotification(!state.alertNotificationEnabled) }
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (state.alertNotificationEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                        contentDescription = "提示通知",
                        tint = if (state.alertNotificationEnabled) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "来车提示通知",
                        color = if (state.alertNotificationEnabled) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (state.alertNotificationEnabled) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Switch(
                        checked = state.alertNotificationEnabled,
                        onCheckedChange = onToggleAlertNotification,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue,
                            uncheckedTrackColor = SurfaceSecondary
                        ),
                        modifier = Modifier
                            .scale(0.75f)
                            .height(26.dp)
                            .testTag("dashboard_toggle_notification_alert")
                    )
                }

                // Divider line 2
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(44.dp)
                        .background(BorderLight)
                )

                // Item 3: 收听基带音频
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleBasebandAudio(!state.basebandAudioEnabled) }
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (state.basebandAudioEnabled) Icons.Default.Headphones else Icons.Default.HeadsetOff,
                        contentDescription = "收听音频",
                        tint = if (state.basebandAudioEnabled) PrimaryBlue else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "收听基带音频",
                        color = if (state.basebandAudioEnabled) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (state.basebandAudioEnabled) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Switch(
                        checked = state.basebandAudioEnabled,
                        onCheckedChange = onToggleBasebandAudio,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue,
                            uncheckedTrackColor = SurfaceSecondary
                        ),
                        modifier = Modifier
                            .scale(0.75f)
                            .height(26.dp)
                            .testTag("dashboard_toggle_baseband_audio")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Tuning Toolbar
        Text(
            text = "快速调谐与参数 (Quick Controls)",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 9.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1.35f)
                    .height(68.dp)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenFreqDialog() }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("频率", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        String.format(Locale.US, "%.4f MHz", state.freqHz / 1_000_000.0),
                        color = PrimaryBlueDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .height(68.dp)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable {
                        if (state.tunerAgc) {
                            Toast.makeText(
                                context,
                                "Tuner AGC 已开启，正在自动控制硬件增益；请先关闭 Tuner AGC 再手动设置增益",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            onOpenGainDialog()
                        }
                    }
                    .padding(horizontal = 9.dp, vertical = 9.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("增益", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        String.format(Locale.US, "%.1f dB", state.gainDb),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(0.78f)
                    .height(68.dp)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenPpmDialog() }
                    .padding(horizontal = 8.dp, vertical = 9.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("PPM", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${state.ppm}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(0.82f)
                    .height(68.dp)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenCsDialog() }
                    .padding(horizontal = 8.dp, vertical = 9.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("门限", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(
                        String.format(Locale.US, "%.0f dB", state.csThresholdDb),
                        color = EmeraldGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "射频自动增益",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                .testTag("dashboard_agc_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(154.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(154.dp)
                            .padding(horizontal = 7.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Tuner AGC",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = if (state.tunerAgc) "已开启" else "已关闭",
                                color = if (state.tunerAgc) PrimaryBlueDark else TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "自动控制 R820T 模拟前端增益",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.tunerAgc) "自动调节硬件增益，手动增益已锁定" else "关闭后可手动调整硬件增益",
                            color = if (state.tunerAgc) PrimaryBlueDark else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Switch(
                                checked = state.tunerAgc,
                                onCheckedChange = onToggleTunerAgc,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryBlue,
                                    uncheckedTrackColor = SurfaceSecondary
                                ),
                                modifier = Modifier.scale(1.05f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(154.dp)
                            .background(BorderLight)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(154.dp)
                            .padding(horizontal = 7.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "RTL AGC",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = if (state.rtlAgc) "已开启" else "已关闭",
                                color = if (state.rtlAgc) PrimaryBlueDark else TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = "自动控制 RTL2832U 数字端 AGC",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.rtlAgc) "数字端自动增益正在工作" else "关闭后使用固定数字增益",
                            color = if (state.rtlAgc) PrimaryBlueDark else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Switch(
                                checked = state.rtlAgc,
                                onCheckedChange = onToggleRtlAgc,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryBlue,
                                    uncheckedTrackColor = SurfaceSecondary
                                ),
                                modifier = Modifier.scale(1.05f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                        .clickable {
                            if (state.tunerAgc) {
                                Toast.makeText(
                                    context,
                                    "Tuner AGC 已开启，当前由调谐器自动控制硬件增益",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onOpenGainDialog()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "手动硬件增益",
                            color = if (state.tunerAgc) TextMuted else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (state.tunerAgc) "Tuner AGC 开启后不可手动修改" else "点击设置 R820T Gain",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = if (state.tunerAgc) "自动" else String.format(Locale.US, "%.1f dB", state.gainDb),
                        color = if (state.tunerAgc) TextMuted else PrimaryBlueDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        if (state.showPacketLogTab) {
            Spacer(modifier = Modifier.height(16.dp))
            DashboardPacketLogCard(
                packetLogs = packetLogs,
                onNavigateToPacketLogs = onNavigateToPacketLogs
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "关注车次",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                .clickable { onOpenWatchlistDialog() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            if (state.keywords.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全部车次",
                        color = AmberSignal,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "未设置关注名单",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    state.keywords.chunked(4).forEach { rowKeywords ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            rowKeywords.forEach { keyword ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(AmberSoft, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = keyword,
                                        color = AmberSignal,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }
                            }
                            repeat(4 - rowKeywords.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text(
                        text = "点击此处修改关注车次",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardPacketLogCard(
    packetLogs: List<PacketLogItem>,
    onNavigateToPacketLogs: (() -> Unit)?
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderLight)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_dashboard_packet_logs")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "报文日志",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(PrimaryBlueSoft, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${packetLogs.size} 条",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlueDark
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onNavigateToPacketLogs != null) {
                        TextButton(
                            onClick = onNavigateToPacketLogs,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("完整页面", fontSize = 12.sp, color = PrimaryBlueDark)
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(Icons.Default.OpenInNew, contentDescription = "打开完整页面", modifier = Modifier.size(14.dp), tint = PrimaryBlueDark)
                        }
                    }
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "收起" else "展开",
                            tint = TextMuted
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (packetLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无报文日志。启动接收机或开启仿真后将实时捕获并解析报文。",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        packetLogs.take(5).forEach { item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "${item.timeFormatted} 接到报文：",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PrimaryBlueDark
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.content,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = TextPrimary,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                        if (packetLogs.size > 5 && onNavigateToPacketLogs != null) {
                            TextButton(
                                onClick = onNavigateToPacketLogs,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("查看全部 ${packetLogs.size} 条日志 >", fontSize = 12.sp, color = PrimaryBlueDark)
                            }
                        }
                    }
                }
            }
        }
    }
}
