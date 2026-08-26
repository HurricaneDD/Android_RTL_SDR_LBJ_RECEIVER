package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    initialNickname: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String) -> Unit
) {
    var nicknameText by remember { mutableStateOf(initialNickname) }
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
            Text(text = "标定本站公里标", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "设定你当前位于线路的大致位置，以估算列车到达时间。",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nicknameText,
                    onValueChange = { nicknameText = it },
                    label = { Text("本站位置/观察点昵称（如：中和桥道口、中华门站）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = routeText,
                    onValueChange = { routeText = it },
                    label = { Text("线路名称（如：宁芜线、京沪高铁）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it },
                    label = { Text("本站里程（如K130+200或130.2千米）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (kmText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (parsedKm != null) {
                            val nickPrefix = if (nicknameText.isNotBlank()) "${nicknameText.trim()} → " else ""
                            val rName = if (routeText.isNotBlank()) routeText.trim() else "线路"
                            val kmStr = String.format(Locale.US, "%.3f", parsedKm).trimEnd('0').let { if (it.endsWith('.')) it + "0" else it }
                            "✔ 解析结果: $nickPrefix$rName ${ArrivalEstimator.formatMilestone(parsedKm)}（${kmStr}KM）"
                        } else {
                            "✖ 格式无法解析，请输入如 K130+200、130.2千米 或 130.2"
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
                        onConfirm(r, parsedKm, nicknameText.trim())
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
                    text = "• 绿色折线：表示对应频段的信号能量 (dB)。\n• 黄色中心垂直标尺：代表当前调谐的目标中心频率 (821.2375 MHz)。\n• 红色水平虚线：代表「静噪接收门限 (Squelch)」。只有当信号折线冲破红线时，软件才会启动解调与解码，防止将外界杂音当作列车报文。",
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
                // App Launcher Vector Icon
                Image(
                    painter = painterResource(id = com.example.R.drawable.ic_app_vector_icon),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SDR-LBJ",
                    color = PrimaryBlueDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v1.0.0 (Build 10)",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "构建时间：2026-08-23 16:00",
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Module 1: Author Info
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

                // Module 2: Personal Website Card (Clickable to copy, matching style)
                val websiteUrl = "https://hurricanedd.github.io"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary, RoundedCornerShape(10.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(10.dp))
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
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                )
                                Text(
                                    text = "个人网站 (点击复制)：",
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
                            text = websiteUrl,
                            color = PrimaryBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Module 3: Github Repo Card (Clickable to copy)
                val githubUrl = "https://github.com/HurricaneDD/RTL_SDR_LBJ_RECEIVER_Android"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary, RoundedCornerShape(10.dp))
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
                                    tint = PrimaryBlue,
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

                Spacer(modifier = Modifier.height(10.dp))

                // Module 4: Acknowledgement Card (Clickable to copy ref link)
                val refUrl = "https://github.com/Sdr-Is-Fun/RTL_SDR_LBJ_RECEIVER"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary, RoundedCornerShape(10.dp))
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

@Composable
fun TrainTypeRuleDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Train,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "列车车次编排规定",
                    color = PrimaryBlueDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Front Note Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8EEF5), RoundedCornerShape(8.dp))
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "现行的车次编号规范来自于2014年颁布的铁总运[2014]308号文《列车车次编排规定》。",
                        color = PrimaryBlueDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable table preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .horizontalScroll(rememberScrollState())
                ) {
                    TrainRulesTableContent()
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

@Composable
fun TrainRulesTableContent() {
    Column(
        modifier = Modifier
            .width(620.dp)
            .background(Color.White)
            .border(1.dp, Color(0xFF222222))
    ) {
        // Title Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "列车车次编排规定",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2F2F2))
                .border(1.dp, Color(0xFF222222))
        ) {
            RuleTableCell(text = "序号", width = 45.dp, isHeader = true)
            RuleTableCell(text = "列车种类", width = 145.dp, isHeader = true)
            RuleTableCell(text = "前缀", width = 65.dp, isHeader = true)
            RuleTableCell(text = "车次范围", width = 175.dp, isHeader = true)
            RuleTableCell(text = "备注", width = 190.dp, isHeader = true, isLast = true)
        }

        // Section 1: 一、旅客列车
        RuleSectionHeader(title = "一、旅客列车")
        RuleTableRow("1", "高速动车组旅客列车", "G\n读“高”", "G1—G9998", "直通  G1—G4998  (G4001—G4998为直通临客预留)\n管内  G5001—G9998 (G9001—G9998为管内临客预留)")
        RuleTableRow("2", "城际动车组旅客列车", "C\n读“城”", "C1—C9998", "C9001—C9998为临客预留")
        RuleTableRow("3", "动车组旅客列车", "D\n读“动”", "D1—D9998", "直通  D1—D4998  (D4001—D4998为直通临客预留)\n管内  D5001—D9998 (D9001—D9998为管内临客预留)")
        RuleTableRow("4", "直达特快旅客列车", "Z\n读“直”", "Z1—Z9998", "160km/h\n直通  Z1—Z4998 (Z4001—Z4998为直通临客预留)\n管内  Z5001—Z9998 (Z9001—Z9998为管内临客预留)")
        RuleTableRow("5", "特快旅客列车", "T\n读“特”", "T1—T9998", "140km/h\n直通  T1—T3998 (T3001—T3998为直通临客预留)\n管内  T4001—T9998 (T4001—T4998为管内临客预留)")
        RuleTableRow("6", "快速旅客列车", "K\n读“快”", "K1—K9998", "120km/h\n直通  K1—K4998 (K4001—K4998为直通临客预留)\n管内  K5001—K9998 (K5001—K6998为管内临客预留)")
        RuleTableRow("7", "普通旅客列车\n(普快/普慢)", "—", "1001—5998 (普快)\n6001—7598 (普慢)", "120km/h\n普快直通: 1001—3998 (3001—3998临客预留)\n普快管内: 4001—5998\n普慢直通: 6001—6198 / 管内: 6201—7598")
        RuleTableRow("8", "通勤列车", "—", "7601—8998", "—")
        RuleTableRow("9", "临时旅客列车", "L\n读“临”", "L1—L9998", "100km/h\n直通  L1—L6998\n管内  L7001—L9998")
        RuleTableRow("10", "旅游列车", "Y\n读“游”", "Y1—Y998", "120km/h\n直通  Y1—Y498\n管内  Y501—Y998")

        // Section 2: 二、特快货物班列
        RuleSectionHeader(title = "二、特快货物班列")
        RuleTableRow("—", "特快货物班列", "X\n读“行”", "X1—X198", "160km/h")

        // Section 3: 三、货物列车
        RuleSectionHeader(title = "三、货物列车")
        RuleTableRow("1", "快运货物列车\n• 快速货物班列\n• 货物快运列车\n• 中欧中亚铁水班列\n• 普快货物班列", "X\n读“行”", "X201—X398\nX401—X998 (管内)\nX2401—X2998 (直通)\nX8001—X9998\n80001—81998", "120km/h (快速货物班列)\n120km/h (直通/管内快运)\n中欧中亚: X8001—X8998 (120km/h)\n中亚/水铁: X9001—X9998 (普货标尺)\n普快货物班列: 80001—81998 (普货标尺)")
        RuleTableRow("2", "煤炭直达列车", "—", "82001—84998", "—")
        RuleTableRow("3", "石油直达列车", "—", "85001—85998", "—")
        RuleTableRow("4", "始发直达列车", "—", "86001—86998", "—")
        RuleTableRow("5", "空车直达列车", "—", "87001—87998", "—")
        RuleTableRow("6", "技术直达列车", "—", "10001—19998", "—")
        RuleTableRow("7", "直通货物列车", "—", "20001—29998", "—")
        RuleTableRow("8", "区段货物列车", "—", "30001—39998", "—")
        RuleTableRow("9", "摘挂列车", "—", "40001—44998", "—")
        RuleTableRow("10", "小运转列车", "—", "45001—49998", "—")
        RuleTableRow("11", "重载货物列车", "—", "71001—77998", "根据实际运输组织模式，由铁路局制定具体车次分段")
        RuleTableRow("12", "自备车列车", "—", "60001—69998", "—")
        RuleTableRow("13", "超限货物列车", "—", "70001—70998", "—")
        RuleTableRow("14", "保温列车", "—", "78001—78998", "—")

        // Section 4: 五、单机和路用列车
        RuleSectionHeader(title = "五、单机和路用列车")
        RuleTableRow("1", "单机 (客/货/小运转)", "—", "50001—50998 (客车)\n51001—51998 (货车)\n52001—52998 (小运转)", "—")
        RuleTableRow("2", "补机", "—", "53001—54998", "—")
        RuleTableRow("3", "动车组检测/确认列车", "DJ\n读“动检”", "DJ1—DJ1998 (检测)\nDJ5001—DJ8998 (确认)", "300km/h检测: 直通DJ1-400, 管内DJ401-998\n250km/h检测: 直通DJ1001-1400, 管内DJ1401-1998\n确认列车: 直通DJ5001-6998, 管内DJ7001-8998")
        RuleTableRow("4", "试运转列车", "—", "55001—55300 (普客货)\n55301—55500 (300km/h)\n55501—55998 (250km/h)", "—")
        RuleTableRow("5", "轻油动车、轨道车", "—", "56001—56998", "—")
        RuleTableRow("6", "路用列车", "—", "57001—57998", "—")
        RuleTableRow("7", "救援列车", "—", "58101—58998", "—")
        RuleTableRow("8", "回送客车底列车", "00 / 0 / F", "001—00100 (动车有火)\n00101—00298 (动车无火)\n00301—00498 (普客无火)\n0+图定车次 (图定回送)\nF+原车次 (折返)", "“00”均为数字\n“00”均为数字\n“00”均为数字\n“0”为数字\n“F”读“返”")
    }
}

@Composable
private fun RuleSectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8EEF5))
            .border(0.5.dp, Color(0xFF222222))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun RuleTableRow(
    id: String,
    type: String,
    prefix: String,
    range: String,
    remark: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0xFF222222))
    ) {
        RuleTableCell(text = id, width = 45.dp, alignCenter = true)
        RuleTableCell(text = type, width = 145.dp)
        RuleTableCell(text = prefix, width = 65.dp, alignCenter = true)
        RuleTableCell(text = range, width = 175.dp)
        RuleTableCell(text = remark, width = 190.dp, isLast = true)
    }
}

@Composable
private fun RuleTableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    alignCenter: Boolean = false,
    isLast: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = if (isHeader || alignCenter) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = if (isHeader) 11.5.sp else 10.5.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = Color.Black,
            lineHeight = 14.5.sp,
            textAlign = if (alignCenter || isHeader) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
        )
    }
}

@Composable
fun TtsEngineSelectionDialog(
    currentMode: String,
    onSelectMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple("auto", "自动选择 (推荐)", "优先检测系统中文 TTS 语音引擎；若系统无合适中文语音或播报失败，自动切换至备选在线云端 TTS 服务。"),
        Triple("system", "系统 TTS 引擎", "强制使用本地系统内置语音合成引擎（如系统自带可用的中文TTS）。"),
        Triple("online", "在线TTS API (仅联网)", "强制使用在线网络语音合成 API 服务；在联网播报后自动下载保存至本地离线缓存，以便下次使用。")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = "选择语音合成引擎",
                color = PrimaryBlueDark,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (modeKey, title, desc) ->
                    val isSelected = (currentMode == modeKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PrimaryBlueDark.copy(alpha = 0.08f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) PrimaryBlueDark else BorderLight,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onSelectMode(modeKey)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectMode(modeKey)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PrimaryBlueDark,
                                unselectedColor = TextMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PrimaryBlueDark else TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                fontSize = 11.5.sp,
                                color = TextMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = PrimaryBlueDark, fontWeight = FontWeight.Bold)
            }
        }
    )
}



