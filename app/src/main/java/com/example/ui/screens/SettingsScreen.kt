package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.driver.DriverLauncher
import com.example.ui.ReceiverState
import com.example.ui.components.AboutAppDialog
import com.example.ui.theme.AmberSignal
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.RedAlert
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceSecondary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SettingsScreen(
    state: ReceiverState,
    onOpenFreqDialog: () -> Unit,
    onOpenGainDialog: () -> Unit,
    onOpenPpmDialog: () -> Unit,
    onOpenCsDialog: () -> Unit,
    onOpenWatchlistDialog: () -> Unit,
    onToggleStrictFilter: (Boolean) -> Unit,
    onToggleShowErrWarn: (Boolean) -> Unit,
    onToggleFilterMode: (String) -> Unit,
    onToggleBroadcastAlerts: (Boolean) -> Unit,
    onToggleAlertTone: (Boolean) -> Unit,
    onToggleKeepAlive: (Boolean) -> Unit,
    onToggleSimulationButton: (Boolean) -> Unit,
    onResetAllSettings: () -> Unit,
    onLaunchDriver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onToggleKeepAlive(true)
            Toast.makeText(context, "已授予通知权限，后台常驻保活已开启", Toast.LENGTH_SHORT).show()
        } else {
            onToggleKeepAlive(false)
            Toast.makeText(context, "需要通知权限以在后台保持常驻服务", Toast.LENGTH_LONG).show()
        }
    }

    val handleKeepAliveToggle: (Boolean) -> Unit = { enable ->
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    onToggleKeepAlive(true)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                onToggleKeepAlive(true)
            }
        } else {
            onToggleKeepAlive(false)
        }
    }

    if (showAboutDialog) {
        AboutAppDialog(onDismiss = { showAboutDialog = false })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // ================= 大类 1: 基础用户设置 (放在最前) =================
        Text(
            text = "基础用户设置",
            color = PrimaryBlueDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "常规功能偏好、后台保活服务与系统配置管理",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column {
                SettingsSwitchItem(
                    title = "来车提示音开关",
                    subtitle = "成功解析到列车信号或车次变更时播放2秒滴滴警示音",
                    checked = state.alertToneEnabled,
                    onCheckedChange = onToggleAlertTone
                )

                SettingsSwitchItem(
                    title = "后台常驻保活",
                    subtitle = "以常驻通知形式维持前台服务，锁屏或切后台时持续监听列车信号",
                    checked = state.keepAliveEnabled,
                    onCheckedChange = handleKeepAliveToggle
                )

                SettingsItem(
                    title = "关于本应用",
                    subtitle = "作者信息、项目开源仓库及开发致谢说明",
                    value = "查看",
                    onClick = { showAboutDialog = true }
                )

                SettingsItem(
                    title = "恢复所有设置",
                    subtitle = "将所有射频频率、增益、门限、校验及用户偏好恢复为默认值",
                    value = "恢复默认",
                    onClick = { showResetDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ================= 大类 2: 接收机设置与高级调谐 =================
        Text(
            text = "接收机设置与高级调谐",
            color = PrimaryBlueDark,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "配置 SDR 射频前端、信道滤波器、报文校验及外部联动",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: Radio Parameters
        SettingsSectionHeader(icon = Icons.Default.CellTower, title = "SDR 射频参数")
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column {
                SettingsItem(
                    title = "中心频率 (Frequency)",
                    subtitle = "LBJ 标称频率 821.2375 MHz (默认)",
                    value = String.format(Locale.US, "%.4f MHz", state.freqHz / 1_000_000.0),
                    onClick = onOpenFreqDialog
                )
                SettingsItem(
                    title = "硬件增益 (R820T Gain)",
                    subtitle = "调节接收灵敏度与信噪比 (默认: 15.7 dB)",
                    value = String.format(Locale.US, "%.1f dB", state.gainDb),
                    onClick = onOpenGainDialog
                )
                SettingsItem(
                    title = "PPM 晶振频偏校准",
                    subtitle = "修正 Dongle 晶振温度漂移误差 (默认: 0)",
                    value = "${state.ppm} PPM",
                    onClick = onOpenPpmDialog
                )
                SettingsItem(
                    title = "RSSI 接收静噪门限 (Squelch)",
                    subtitle = "低于门限时静噪，避免底噪误报 (默认: -55 dB)",
                    value = String.format(Locale.US, "%.0f dB", state.csThresholdDb),
                    onClick = onOpenCsDialog
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2: Decoder & Filtering
        SettingsSectionHeader(icon = Icons.Default.FilterAlt, title = "报文解码与过滤")
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column {
                SettingsSwitchItem(
                    title = "严格 BCH 错包拦截",
                    subtitle = "丢弃无法通过 BCH(31,21) 校验的畸变报文",
                    checked = state.strictFilter,
                    onCheckedChange = onToggleStrictFilter
                )
                SettingsSwitchItem(
                    title = "微弱/干扰信号预警",
                    subtitle = "探测到受干扰报文时在仪表盘提示告警",
                    checked = state.showErrWarn,
                    onCheckedChange = onToggleShowErrWarn
                )
                SettingsItem(
                    title = "关注关键词 (Watchlist)",
                    subtitle = "设置特定车次或机车号进行重点监控",
                    value = if (state.keywords.isNotEmpty()) state.keywords.joinToString(",") else "全部显示 (默认)",
                    onClick = onOpenWatchlistDialog
                )
                SettingsSwitchItem(
                    title = "仅显示关注目标 (严格模式)",
                    subtitle = if (state.filterMode == "strict") "开启: 仅显示命中关键词的车次" else "关闭: 高亮显示关注车次 (默认)",
                    checked = (state.filterMode == "strict"),
                    onCheckedChange = { isStrict ->
                        onToggleFilterMode(if (isStrict) "strict" else "highlight")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 3: Android Automation & Driver
        SettingsSectionHeader(icon = Icons.Default.NotificationsActive, title = "外部联动与自动化")
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                SettingsSwitchItem(
                    title = "MacroDroid / Tasker 广播联动",
                    subtitle = "解调到有效列车报文时发送 com.train.alert 广播",
                    checked = state.broadcastAlerts,
                    onCheckedChange = onToggleBroadcastAlerts
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            DriverLauncher.sendAlertBroadcast(
                                context = context,
                                train = "G102",
                                direction = "下行",
                                speed = "310",
                                position = "145.8",
                                loco = "CR400BF-5033",
                                locoCode = "311",
                                route = "京沪高铁",
                                category = "高速动车组"
                            )
                            Toast.makeText(context, "已发送测试广播 (com.train.alert)", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).testTag("test_broadcast_button")
                    ) {
                        Text("发送测试广播", fontSize = 12.sp, color = AmberSignal)
                    }

                    OutlinedButton(
                        onClick = onLaunchDriver,
                        modifier = Modifier.weight(1f).testTag("settings_launch_driver_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "Driver",
                            tint = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("尝试驱动设备", fontSize = 12.sp, color = PrimaryBlueDark)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 4: 开发者选项 (Developer Options)
        SettingsSectionHeader(icon = Icons.Default.Code, title = "开发者选项")
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column {
                SettingsSwitchItem(
                    title = "开启虚拟数据演示按钮",
                    subtitle = "在仪表盘显示仿真演示按钮，用于无外置硬件时模拟 RF 信号流 (默认: 关闭)",
                    checked = state.showSimulationButton,
                    onCheckedChange = onToggleSimulationButton
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = RedAlert, modifier = Modifier.padding(end = 8.dp))
                    Text("恢复所有设置", color = RedAlert, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "确定要将所有设置（中心频率 821.2375MHz、增益 15.7dB、门限 -55dB、BCH校验、关注列表等）全部恢复为默认值吗？",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAllSettings()
                        showResetDialog = false
                        Toast.makeText(context, "已恢复所有设置为默认值", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                ) {
                    Text("确认恢复", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.height(18.dp).width(18.dp).padding(end = 6.dp)
        )
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Text(
            text = value,
            color = PrimaryBlueDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue,
                uncheckedTrackColor = SurfaceSecondary
            )
        )
    }
}
