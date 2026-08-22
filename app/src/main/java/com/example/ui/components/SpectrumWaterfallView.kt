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
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
            .then(clickModifier)
            .padding(14.dp)
            .testTag("spectrum_waterfall_card")
    ) {
        Column {
            // Header Row: Title & 科普说明 on left, Frequency on the right
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
                                .padding(horizontal = 6.dp, vertical = 2.dp)
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

                // Center Frequency positioned clearly on the right
                Text(
                    text = String.format(Locale.US, "▲ %.4f MHz", freqHz / 1_000_000.0),
                    color = PrimaryBlueDark,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 硬件增益、PPM、采样率 各占一整行，无灰色背景，无粗体字
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "硬件增益: %.1f dB", gainDb),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = String.format(Locale.US, "PPM 偏置: %d PPM", ppm),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = "采样率: 960 kS/s",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val gradientColors = remember { listOf(Color(0xFF38BDF8), Color(0xFF10B981), Color(0xFF047857)) }
            val gridDbs = remember { floatArrayOf(-90f, -70f, -50f) }

            // 32-band FFT Spectrum Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .border(1.dp, BorderMedium, RoundedCornerShape(8.dp))
                    .testTag("spectrum_canvas")
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val numBars = spectrumBars.size
                if (numBars == 0) return@Canvas

                val minDb = -110.0f
                val maxDb = -30.0f
                val dbRange = maxDb - minDb

                val barSpacing = 2.dp.toPx()
                val totalSpacing = barSpacing * (numBars - 1)
                val barWidth = max(2f, (canvasWidth - totalSpacing) / numBars)

                val gradientBrush = Brush.verticalGradient(
                    colors = gradientColors,
                    startY = 0f,
                    endY = canvasHeight
                )

                // Draw grid lines (-90dB, -70dB, -50dB)
                for (gDb in gridDbs) {
                    val normY = (1.0f - (gDb - minDb) / dbRange).coerceIn(0f, 1f)
                    val yPos = normY * canvasHeight
                    drawLine(
                        color = Color(0x33FFFFFF),
                        start = Offset(0f, yPos),
                        end = Offset(canvasWidth, yPos),
                        strokeWidth = 1f
                    )
                }

                // Draw Squelch Threshold Line
                val thresholdNormY = (1.0f - (csThresholdDb - minDb) / dbRange).coerceIn(0f, 1f)
                val thresholdY = thresholdNormY * canvasHeight
                drawLine(
                    color = Color(0xFFEF4444),
                    start = Offset(0f, thresholdY),
                    end = Offset(canvasWidth, thresholdY),
                    strokeWidth = 2f
                )

                // Draw Spectrum Bars
                val allIdle = spectrumBars.all { it <= minDb }
                if (!allIdle) {
                    for (i in 0 until numBars) {
                        val db = spectrumBars[i].coerceIn(minDb, maxDb)
                        val normHeight = ((db - minDb) / dbRange).coerceIn(0.0f, 1.0f)
                        if (normHeight > 0.01f) {
                            val barHeight = normHeight * canvasHeight
                            val x = i * (barWidth + barSpacing)
                            val y = canvasHeight - barHeight

                            drawRect(
                                brush = gradientBrush,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barHeight)
                            )
                        }
                    }
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

            Spacer(modifier = Modifier.height(10.dp))

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
