package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun FirstLaunchDriverPromptDialog(
    onConfirmAlreadyInstalled: () -> Unit,
    onSelectNotInstalled: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = SurfaceCard,
        icon = {
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = "RTL-SDR 硬件驱动",
                tint = PrimaryBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "驱动程序检测",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "欢迎使用SDR-LBJ列车报警信号接收器",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "使用本应用必须安装前置驱动程序（Android RTL-SDR Driver），Github项目地址为：https://github.com/signalwareltd/rtl_tcp_andro-",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "请问您当前设备上是否已安装该驱动程序？",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onConfirmAlreadyInstalled,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_driver_already_installed")
                ) {
                    Text("已安装驱动程序", fontSize = 12.sp, color = PrimaryBlueDark)
                }

                Button(
                    onClick = onSelectNotInstalled,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_driver_not_installed")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("首次进入，暂未安装", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = null,
        modifier = Modifier.testTag("dialog_first_launch_driver")
    )
}

@Composable
fun DriverInstallGuideDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = SurfaceCard,
        icon = {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "安装驱动程序",
                tint = PrimaryBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "安装 RTL-SDR 驱动程序",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "本应用已内置官方 RTL-SDR Driver 安装包 (sdr-driver.apk)。",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "【安装指引】\n" +
                            "1. 点击下方“立即安装”启动系统应用包安装程序；\n" +
                            "2. 若系统提示“允许安装未知来源应用”，请在弹出的系统设置中允许本应用安装权限后返回；\n" +
                            "3. 安装完成后，插入 USB RTL-SDR 接收机即可正常接收无线电信号。",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_install_driver")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("立即安装", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_cancel_install_driver")
            ) {
                Text("取消", fontSize = 13.sp, color = TextMuted)
            }
        },
        modifier = Modifier.testTag("dialog_driver_install_guide")
    )
}
