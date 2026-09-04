package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderLight
import com.example.ui.theme.BorderMedium
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PrimaryBlueSoft
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.max

@Composable
fun SpectrumWaterfallView(
    spectrumBars: FloatArray,
    freqHz: Double,
    gainDb: Float,
    ppm: Int,
    rssiDb: Float,
    csThresholdDb: Float,
    gateState: String,
    holdMs: Float,
    afcHz: Double,
    afcErrHz: Double,
    afcScore: Double,
    peakFreqHz: Double?,
    peakDeltaHz: Double?,
    peakDb: Float?,
    fps: Float = 0.0f,
    isReceiving: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val linePath = remember { Path() }
    val fillPath = remember { Path() }
    val lineStroke = remember {
        Stroke(
            width = 4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    }
    val fillGradient = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0x3300FF66), Color(0x0500FF66))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("spectrum_waterfall_card")
    ) {
        Column {
            // Header Row: Title & 科普说明 on left, Gain/PPM & SampleRate/FPS on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FFT 频谱监测",
                        color = PrimaryBlueDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (onClick != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(PrimaryBlueSoft, RoundedCornerShape(4.dp))
                                .clickable { onClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("fft_info_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "科普说明",
                                    tint = PrimaryBlueDark,
                                    modifier = Modifier.height(12.dp).width(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "说明",
                                    color = PrimaryBlueDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Top right info: Line 1 = 硬件增益 & PPM偏置, Line 2 = 采样率 & 帧率 (Font sizes match)
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = String.format(Locale.US, "增益: %.1fdB · PPM: %+d", gainDb, ppm),
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    val fpsText = if (fps > 0f) String.format(Locale.US, "%.1f FPS", fps) else "-- FPS"
                    Text(
                        text = "采样率: 960kS/s · $fpsText",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val gridDbs = remember { floatArrayOf(-10f, -30f, -50f, -70f) }

            val minDb = -70.0f
            val maxDb = -10.0f
            val dbRange = maxDb - minDb

            // Row containing Left RSSI Y-Axis and Main Spectrum Canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RSSI Y-Axis Scale labels on the left (-10dB to -70dB)
                Column(
                    modifier = Modifier
                        .width(28.dp)
                        .height(88.dp)
                        .padding(end = 5.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("-10", color = TextMuted, fontSize = 9.sp, lineHeight = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text("-30", color = TextMuted, fontSize = 9.sp, lineHeight = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("-50", color = TextMuted, fontSize = 9.sp, lineHeight = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("-70", color = TextMuted, fontSize = 9.sp, lineHeight = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                // 32-band FFT Spectrum Canvas (Line Graph)
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(88.dp)
                        .background(Color(0xFF0B1120), RoundedCornerShape(6.dp))
                        .border(1.dp, BorderMedium, RoundedCornerShape(6.dp))
                        .testTag("spectrum_canvas")
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val numPoints = spectrumBars.size
                    if (numPoints == 0) return@Canvas

                    val padY = 4f
                    val usableHeight = canvasHeight - 2f * padY

                    // Draw grid lines and left-side scale tick lines (-10dB, -30dB, -50dB, -70dB)
                    for (gDb in gridDbs) {
                        val normY = (1.0f - (gDb - minDb) / dbRange).coerceIn(0f, 1f)
                        val yPos = padY + normY * usableHeight
                        // Full horizontal grid line
                        drawLine(
                            color = Color(0x26FFFFFF),
                            start = Offset(0f, yPos),
                            end = Offset(canvasWidth, yPos),
                            strokeWidth = 1f
                        )
                        // Prominent tick mark at left edge
                        drawLine(
                            color = Color(0xB3FFFFFF),
                            start = Offset(0f, yPos),
                            end = Offset(8f, yPos),
                            strokeWidth = 1.5f
                        )
                    }

                    // Vertical Quarter Grid Lines
                    drawLine(
                        color = Color(0x1AFFFFFF),
                        start = Offset(canvasWidth * 0.25f, 0f),
                        end = Offset(canvasWidth * 0.25f, canvasHeight),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color(0x1AFFFFFF),
                        start = Offset(canvasWidth * 0.75f, 0f),
                        end = Offset(canvasWidth * 0.75f, canvasHeight),
                        strokeWidth = 1f
                    )

                    // Draw Squelch Threshold Line (Bright Rose Red / 亮玫红色)
                    val thresholdNormY = (1.0f - (csThresholdDb - minDb) / dbRange).coerceIn(0f, 1f)
                    val thresholdY = padY + thresholdNormY * usableHeight
                    drawLine(
                        color = Color(0xFFFF1493), // Bright Rose / 亮玫红
                        start = Offset(0f, thresholdY),
                        end = Offset(canvasWidth, thresholdY),
                        strokeWidth = 1.5f
                    )

                    // Draw RSSI Bright Green Line Graph (only when receiving signal)
                    if (isReceiving && numPoints > 1) {
                        linePath.rewind()
                        fillPath.rewind()

                        for (i in 0 until numPoints) {
                            val x = i * (canvasWidth / (numPoints - 1))
                            val db = spectrumBars[i].coerceIn(minDb, maxDb)
                            val normY = (1.0f - (db - minDb) / dbRange).coerceIn(0f, 1f)
                            val y = padY + normY * usableHeight

                            if (i == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, padY + usableHeight)
                                fillPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }
                        fillPath.lineTo(canvasWidth, padY + usableHeight)
                        fillPath.close()

                        // Soft subtle green fill under the curve
                        drawPath(
                            path = fillPath,
                            brush = fillGradient
                        )

                        // Bright green polyline
                        drawPath(
                            path = linePath,
                            color = Color(0xFF00FF66),
                            style = lineStroke
                        )
                    }

                    // Center Carrier Marker
                    val centerX = canvasWidth / 2f
                    drawLine(
                        color = Color(0xFFFDE047),
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, canvasHeight),
                        strokeWidth = 1.5f
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Frequency Labels on Left, Center, Right aligned below the Canvas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Placeholder to offset width matching Y-axis
                Spacer(modifier = Modifier.width(28.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftFreqMhz = (freqHz - 480_000.0) / 1_000_000.0
                    val centerFreqMhz = freqHz / 1_000_000.0
                    val rightFreqMhz = (freqHz + 480_000.0) / 1_000_000.0

                    Text(
                        text = String.format(Locale.US, "%.3fM", leftFreqMhz),
                        color = TextMuted,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = String.format(Locale.US, "▲ %.4fM", centerFreqMhz),
                        color = PrimaryBlueDark,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = String.format(Locale.US, "%.3fM", rightFreqMhz),
                        color = TextMuted,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Signal Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAboveGate = rssiDb >= csThresholdDb
                Text(
                    text = String.format(Locale.US, "RSSI: %.0f dB", rssiDb),
                    color = if (isAboveGate) EmeraldGreen else TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format(Locale.US, "门限: %.0f dB", csThresholdDb),
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "门控:$gateState",
                    color = when (gateState) {
                        "ON" -> EmeraldGreen
                        "HOLD" -> PrimaryBlue
                        "ARM" -> Color(0xFFD97706)
                        else -> TextMuted
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                if (gateState == "HOLD") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "(%.0fms)", holdMs),
                        color = PrimaryBlue,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // AFC & Peak Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.US, "AFC: %+.0fHz (误差:%+.0fHz S:%.2f)", afcHz, afcErrHz, afcScore),
                    color = if (kotlin.math.abs(afcErrHz) > 50) PrimaryBlueDark else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (peakFreqHz != null && peakDeltaHz != null && peakDb != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "峰值: %.6fM (Δ%+.1fkHz %.0fdB)", peakFreqHz / 1_000_000.0, peakDeltaHz / 1000.0, peakDb),
                    color = Color(0xFF9333EA),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
