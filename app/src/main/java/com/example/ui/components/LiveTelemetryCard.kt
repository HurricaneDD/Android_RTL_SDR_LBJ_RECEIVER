package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.decoder.ArrivalEstimator
import com.example.decoder.EtaInfo
import com.example.decoder.TrainTelemetry
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.BlueUp
import com.example.ui.theme.BlueUpSoft
import com.example.ui.theme.BorderLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PrimaryBlueSoft
import com.example.ui.theme.PurpleTech
import com.example.ui.theme.RedAlert
import com.example.ui.theme.RedSoft
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextSubtle
import java.util.Locale

@Composable
fun LiveTelemetryCard(
    telemetry: TrainTelemetry,
    etaInfo: EtaInfo,
    warningMessage: String,
    currentStationKmText: String,
    modifier: Modifier = Modifier
) {
    val isHit = telemetry.isHit
    val borderColor = when {
        isHit -> AmberSignal
        telemetry.trainNo != "----" -> PrimaryBlue
        else -> BorderLight
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(if (isHit) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .testTag("telemetry_hud_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Warning Banner if present
            AnimatedVisibility(
                visible = warningMessage.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(RedSoft, RoundedCornerShape(8.dp))
                        .border(1.dp, RedAlert.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = RedAlert,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = warningMessage,
                        color = RedAlert,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Top Row: [Train Number + Category Tag] (Left) + [Direction Badge & Watchlist Badge] (Right)
            val (dirBg, dirFg) = when (telemetry.direction) {
                "下行" -> Pair(EmeraldSoft, EmeraldGreen)
                "上行" -> Pair(BlueUpSoft, BlueUp)
                else -> Pair(SurfaceSecondary, TextMuted)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Train Number & Category
                Column {
                    Text(
                        text = "列车车次 (Train No.)",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = telemetry.trainNo,
                        color = if (isHit) AmberSignal else if (telemetry.trainNo != "----") TextPrimary else TextSubtle,
                        fontSize = 32.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.testTag("train_number_text")
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(PrimaryBlueSoft, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = telemetry.category,
                                color = PrimaryBlueDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isHit) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(AmberSoft, RoundedCornerShape(4.dp))
                                    .border(1.dp, AmberSignal, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "★ 关注目标",
                                    color = AmberSignal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Right Column: Direction Badge
                Box(
                    modifier = Modifier
                        .background(dirBg, RoundedCornerShape(6.dp))
                        .border(1.dp, dirFg.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("direction_badge")
                ) {
                    Text(
                        text = telemetry.direction,
                        color = dirFg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2: [机车型号/代号 (Left)] + [实时速度 (Right)]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Locomotive Card (Left)
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .background(SurfaceSecondary, RoundedCornerShape(10.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Train,
                                contentDescription = "Locomotive",
                                tint = PurpleTech,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                            Text(
                                text = "机车型号 / 代号",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = telemetry.locoModel,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (telemetry.locoCode != "---") {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "代号: ${telemetry.locoCode}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Real-Time Speed Card (Right of Locomotive Card)
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .background(SurfaceSecondary, RoundedCornerShape(10.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                        .testTag("speed_card")
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                            Text(
                                text = "实时速度",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (telemetry.speed != "---") "${telemetry.speed} km/h" else "--- km/h",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 3 (Merged Long Card): 运行线路 / 行驶方向 / 公里位置
            val parsedKm = ArrivalEstimator.parseKm(telemetry.positionKm)
            val milestoneStr = if (parsedKm != null) " (${ArrivalEstimator.formatMilestone(parsedKm)})" else ""
            val routeText = if (telemetry.route != "----") telemetry.route else "----"
            val directionText = telemetry.direction
            val kmText = if (telemetry.positionKm != "---.-") "${telemetry.positionKm} KM$milestoneStr" else "---.- KM"
            val mergedDisplay = "$routeText - $directionText - $kmText"

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSecondary, RoundedCornerShape(10.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .testTag("merged_route_direction_km_card")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AltRoute,
                                contentDescription = "Route Direction Km",
                                tint = EmeraldGreen,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = "运行线路 / 行驶方向 / 公里位置",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = "本站: $currentStationKmText",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = mergedDisplay,
                        color = if (telemetry.isRouteValid) EmeraldGreen else TextPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Station Arrival & ETA Monitor Section
            val (etaBg, etaFg) = when (etaInfo.etaStatus) {
                "即将到达" -> Pair(RedSoft, RedAlert)
                "接近 (5分内)", "接近", "接近中" -> Pair(AmberSoft, AmberSignal)
                "远离/已过" -> Pair(SurfaceSecondary, TextMuted)
                else -> Pair(PrimaryBlueSoft, PrimaryBlueDark)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(etaBg, RoundedCornerShape(10.dp))
                    .border(1.dp, etaFg.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .testTag("eta_monitor_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "ETA",
                                tint = etaFg,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "本站到达估算 (ETA)",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val secText = if (etaInfo.etaSeconds != null) {
                            val totalSec = etaInfo.etaSeconds.toInt()
                            val mins = totalSec / 60
                            val remSec = totalSec % 60
                            if (mins > 0) "${mins}分${remSec}秒" else "${totalSec}秒"
                        } else {
                            "---"
                        }
                        Text(
                            text = "$secText (${etaInfo.etaTime})",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, etaFg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = etaInfo.etaStatus,
                                color = etaFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (etaInfo.etaDistanceKm != null) {
                                String.format(Locale.US, "距本站: %.1f KM", etaInfo.etaDistanceKm)
                            } else {
                                "距本站: --- KM"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

