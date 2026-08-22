package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.decoder.ArrivalEstimator
import com.example.dsp.DspConstants
import com.example.ui.theme.BorderLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PrimaryBlueSoft
import com.example.ui.theme.RedAlert
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun FrequencyDialog(
    currentFreqMhz: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var text by remember { mutableStateOf(String.format(Locale.US, "%.4f", currentFreqMhz)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(text = "设置接收频率 (MHz)", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "中国铁路 LBJ 标称中心频率为 821.2375 MHz (默认值)。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("频率 MHz (默认: 821.2375)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("freq_input_field")
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { text = "821.2375" },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Default", tint = PrimaryBlue, modifier = Modifier.height(16.dp).width(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("恢复默认 (821.2375)", fontSize = 11.sp, color = PrimaryBlueDark)
                    }
                    OutlinedButton(
                        onClick = { text = "450.0000" },
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text("450.0M", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val f = text.toDoubleOrNull()
                    if (f != null && f > 24.0 && f < 1800.0) {
                        onConfirm(f)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.testTag("confirm_freq_button")
            ) {
                Text("确定", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
fun GainDialog(
    currentGainDb: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var selectedGain by remember { mutableFloatStateOf(currentGainDb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(text = "设置 R820T 硬件增益 (dB)", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "推荐默认增益: 15.7 dB (信噪比与灵敏度平衡最佳)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { selectedGain = DspConstants.HW_GAIN_DB },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Default", tint = PrimaryBlue, modifier = Modifier.height(16.dp).width(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复默认增益 (15.7 dB)", fontSize = 11.sp, color = PrimaryBlueDark)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(DspConstants.R820T_GAINS.toList()) { gain ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGain = gain }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedGain == gain),
                                onClick = { selectedGain = gain },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (gain == DspConstants.HW_GAIN_DB) String.format(Locale.US, "%.1f dB (默认推荐)", gain) else String.format(Locale.US, "%.1f dB", gain),
                                color = if (selectedGain == gain) PrimaryBlueDark else TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (selectedGain == gain) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedGain) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("确定", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
fun PpmDialog(
    currentPpm: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentPpm.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(text = "设置 PPM 晶振频偏校准", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "修正 RTL-SDR 硬件晶振温漂误差。标准设备默认值为 0 PPM。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("PPM 误差 (默认: 0)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { text = "0" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Default", tint = PrimaryBlue, modifier = Modifier.height(16.dp).width(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复默认 (0 PPM)", fontSize = 11.sp, color = PrimaryBlueDark)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = text.toIntOrNull()
                    if (p != null) onConfirm(p)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("确定", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
fun CsThresholdDialog(
    currentThresholdDb: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var threshold by remember { mutableFloatStateOf(currentThresholdDb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(text = "设置 RSSI 接收门限 (dB)", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "用于静噪门控 (Squelch)。默认值为 -55 dB，低于该强度时保持静噪过滤底噪。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.US, "当前门限: %.0f dB", threshold),
                        color = EmeraldGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedButton(
                        onClick = { threshold = DspConstants.DEFAULT_RSSI_THRESHOLD_DB }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Default", tint = PrimaryBlue, modifier = Modifier.height(14.dp).width(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("默认 (-55dB)", fontSize = 11.sp, color = PrimaryBlueDark)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = -90.0f..-20.0f,
                    steps = 70,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(threshold) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("确定", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
fun WatchlistDialog(
    currentKeywords: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var text by remember { mutableStateOf(currentKeywords.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(text = "关注车次与机车过滤", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "输入需要特别关注或过滤的车次/机车号，使用逗号分隔 (例如 G102, CR400, HXD1D, 5033)。默认留空表示接收全部车次。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("关注关键词 (逗号分隔)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { text = "" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Clear", tint = PrimaryBlue, modifier = Modifier.height(16.dp).width(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空/接收全部 (默认)", fontSize = 11.sp, color = PrimaryBlueDark)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val list = text.split(",", "，")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() }
                    onConfirm(list)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("确定", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
fun RouteStationKmDialog(
    initialRoute: String = "",
    initialKm: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var routeText by remember { mutableStateOf(initialRoute) }
    var kmText by remember {
        mutableStateOf(
            if (initialKm != null) ArrivalEstimator.formatMilestone(initialKm) else ""
        )
    }

    val parsedKm = ArrivalEstimator.parseMilestone(kmText)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(text = "标定线路本站公里标", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "支持里程标写法 (如 K145+800、K1300+200) 或纯数字公里数 (如 145.8)，以便精确计算列车到达时间与距离。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = routeText,
                    onValueChange = { routeText = it },
                    label = { Text("线路名称 (如: 京沪高铁 / 陇海线)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it },
                    label = { Text("本站公里标 / 里程标 (如: K1300+200 或 1300.2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (kmText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (parsedKm != null) {
                            "✔ 解析结果: ${ArrivalEstimator.formatMilestoneWithKm(parsedKm)}"
                        } else {
                            "✖ 格式无法解析，请输入如 K145+800 或 145.8"
                        },
                        color = if (parsedKm != null) EmeraldGreen else RedAlert,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = routeText.trim()
                    if (r.isNotEmpty() && parsedKm != null) {
                        onConfirm(r, parsedKm)
                    }
                },
                enabled = routeText.isNotBlank() && parsedKm != null,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("保存", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        }
    )
}

@Composable
fun FftExplanationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "FFT 频谱监测科普说明",
                    color = PrimaryBlueDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "1. 什么是 FFT 频谱？",
                    color = PrimaryBlueDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "FFT (快速傅里叶变换) 是一种将天线接收到的无线电时域波形，实时分解为各个频段信号能量强弱分布的可视化工具。它可以让您直观「看到」空中的电磁波动态。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "2. 关键射频指标 (RSSI、AFC、G/P)",
                    color = PrimaryBlueDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "• RSSI (接收信号强度)：测量当前信道电磁波功率大小 (单位 dB)。数值越接近 0 (如 -50dB) 代表信号越强、列车越近；数值越小 (如 -110dB) 仅为环境底噪。信号冲破红线门限时才会触发解码。\n• AFC (自动频率控制)：自动频偏跟踪与动态补偿算法。列车高速行驶时的多普勒频移以及硬件温漂会导致频偏，AFC 可实时纠正频偏，确保信号锁死在中心频点，大幅提高解码率。\n• 硬件增益 (G) & PPM (P)：SDR 放大器增益与晶振频偏校准值。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "3. 图表各元素含义：",
                    color = PrimaryBlueDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "• 绿色/青色柱状图：表示对应频段的信号能量 (dB)。\n• 黄色中心垂直标尺：代表当前调谐的目标中心频率 (821.2375 MHz)。\n• 红色水平虚线：代表「静噪接收门限 (Squelch)」。只有当信号柱冲破红线时，软件才会启动解调与解码，防止将外界杂音当作列车报文。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "4. 怎样看是否有列车经过？",
                    color = PrimaryBlueDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "当附近有列车车载 LBJ 设备发射数据时，中心频点附近会迅速隆起一个明显的能量尖峰并超过红线，下方 RSSI 数值会从 -100dB 跃升至 -60dB 以上，门控状态变为 ON，随之解码出车次与速度信息。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("我知道了", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SignalLossDialog(
    onDismiss: () -> Unit,
    onOpenDriverSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = RedAlert,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "检测到信号流丢失",
                    color = RedAlert,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            val part1 = "1、可能是驱动APP后台活动受限，请尝试"
            val linkText = "取消系统对驱动APP的电池优化"
            val part2 = "，或尝试通过悬浮窗、分屏等方式将其保持在前台。\n2、可能是接收器设备故障，请尝试重新连接，重启驱动程序和本应用，自行排查问题。"

            val annotatedText = buildAnnotatedString {
                append(part1)
                pushStringAnnotation(tag = "OPEN_SETTINGS", annotation = "marto.rtl_tcp_andro")
                withStyle(
                    style = SpanStyle(
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(linkText)
                }
                pop()
                append(part2)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                ClickableText(
                    text = annotatedText,
                    style = TextStyle(
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    onClick = { offset ->
                        annotatedText.getStringAnnotations(tag = "OPEN_SETTINGS", start = offset, end = offset)
                            .firstOrNull()?.let {
                                onOpenDriverSettings()
                            }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("我知道了", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun AboutAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制 $label 到剪贴板", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                // App Launcher Icon
                Image(
                    painter = painterResource(id = com.example.R.drawable.lbj_launcher_icon_1787301349738),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SDR-LBJ (列车报警器)",
                    color = PrimaryBlueDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v1.0.0 (Build 1)",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Author Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary, RoundedCornerShape(10.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp).padding(end = 4.dp)
                            )
                            Text(
                                text = "作者：B站/知乎@HurricaneDD",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { copyToClipboard("1727364668", "QQ号") }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "QQ：1727364668",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Personal Website Card (Clickable to copy)
                val websiteUrl = "https://hurricanedd.github.io"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrimaryBlueSoft)
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { copyToClipboard(websiteUrl, "个人网站链接") }
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = PrimaryBlueDark,
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = "个人网站 (点击复制)：",
                                    color = PrimaryBlueDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = PrimaryBlueDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = websiteUrl,
                            color = PrimaryBlueDark,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Github Repo Card (Clickable to copy)
                val githubUrl = "https://github.com/HurricaneDD/RTL_SDR_LBJ_RECEIVER_Android"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceSecondary)
                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                        .clickable { copyToClipboard(githubUrl, "Github仓库地址") }
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = "Github仓库地址 (点击复制)：",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = githubUrl,
                            color = PrimaryBlue,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Acknowledgement Card (Clickable to copy ref link)
                val refUrl = "https://github.com/Sdr-Is-Fun/RTL_SDR_LBJ_RECEIVER"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceSecondary)
                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
                        .clickable { copyToClipboard(refUrl, "参考项目链接") }
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "致谢与开发说明：",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "本项目参考了 https://github.com/Sdr-Is-Fun/RTL_SDR_LBJ_RECEIVER 内的Python脚本，主体由Google AI Studio的Gemini模型完成开发。",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("关闭", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

