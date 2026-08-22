package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.decoder.EtaInfo
import com.example.decoder.TrainTelemetry
import com.example.driver.RtlTcpClient
import com.example.ui.ReceiverState
import com.example.ui.components.LiveTelemetryCard
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
    telemetry: TrainTelemetry,
    etaInfo: EtaInfo,
    onStartReceiver: (Boolean) -> Unit,
    onStopReceiver: () -> Unit,
    onLaunchDriver: () -> Unit,
    onClearTelemetry: () -> Unit,
    onOpenFreqDialog: () -> Unit,
    onOpenGainDialog: () -> Unit,
    onOpenPpmDialog: () -> Unit,
    onOpenCsDialog: () -> Unit,
    onOpenWatchlistDialog: () -> Unit,
    onOpenFftExplanationDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

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
                // Row 1: Status Pill Indicator & Dynamic Current Freq
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val (statusBg, statusFg, statusText) = when {
                        state.isSimulationMode && state.isRunning -> Triple(AmberSoft, AmberSignal, "仿真信号流运行中")
                        state.isRunning -> Triple(EmeraldSoft, EmeraldGreen, "SDR 实时接收中")
                        state.connectionState == RtlTcpClient.ConnectionState.CONNECTING -> Triple(PrimaryBlueSoft, PrimaryBlueDark, "正在连接驱动...")
                        state.connectionState == RtlTcpClient.ConnectionState.ERROR -> Triple(RedSoft, RedAlert, "驱动未连接")
                        else -> Triple(SurfaceSecondary, TextMuted, "待驱动接收器")
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

                    // 尝试驱动设备 (原联动驱动)
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
                                text = "尝试驱动设备",
                                color = PrimaryBlueDark,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
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
            onClick = onOpenFftExplanationDialog
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Live Telemetry HUD Card
        LiveTelemetryCard(
            telemetry = telemetry,
            etaInfo = etaInfo,
            warningMessage = state.warningMessage,
            currentStationKmText = state.currentRouteStationKmText
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Tuning Toolbar
        Text(
            text = "快速调谐与参数 (Quick Controls)",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Freq Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenFreqDialog() }
                    .padding(8.dp)
            ) {
                Column {
                    Text("频率", color = TextMuted, fontSize = 10.sp)
                    Text(
                        String.format(Locale.US, "%.4f M", state.freqHz / 1_000_000.0),
                        color = PrimaryBlueDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Gain Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenGainDialog() }
                    .padding(8.dp)
            ) {
                Column {
                    Text("增益", color = TextMuted, fontSize = 10.sp)
                    Text(
                        String.format(Locale.US, "%.1f dB", state.gainDb),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // PPM Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenPpmDialog() }
                    .padding(8.dp)
            ) {
                Column {
                    Text("PPM", color = TextMuted, fontSize = 10.sp)
                    Text(
                        "${state.ppm}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Squelch Threshold Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenCsDialog() }
                    .padding(8.dp)
            ) {
                Column {
                    Text("门限", color = TextMuted, fontSize = 10.sp)
                    Text(
                        String.format(Locale.US, "%.0f dB", state.csThresholdDb),
                        color = EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Watchlist Chip
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceCard, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                    .clickable { onOpenWatchlistDialog() }
                    .padding(8.dp)
            ) {
                Column {
                    Text("关注", color = TextMuted, fontSize = 10.sp)
                    val kwText = if (state.keywords.isNotEmpty()) "${state.keywords.size}个" else "全部"
                    Text(
                        kwText,
                        color = AmberSignal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
