package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RouteStationKmEntity
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
import com.example.ui.theme.TextSubtle
import java.util.Locale

enum class ExportImportMode {
    EXPORT,
    IMPORT
}

/**
 * 格式转换辅助方法：
 * 将线路列表转换为导出文本
 * 格式为：位置友好名称:线路:小数距离(单位km)，每个独占一行
 */
fun formatRoutesToExportText(routes: List<RouteStationKmEntity>): String {
    return routes.joinToString("\n") { route ->
        val friendlyName = route.nickname.ifBlank { route.routeName }
        val kmFormatted = String.format(Locale.US, "%.3f", route.stationKm).trimEnd('0').let {
            if (it.endsWith('.')) it + "0" else it
        }
        "$friendlyName:${route.routeName}:$kmFormatted"
    }
}

/**
 * 文本解析辅助方法：
 * 解析输入的文本为线路实体列表
 * 支持中英文冒号（: / ：），去除空格和单位（km / 公里）
 */
fun parseRoutesFromImportText(text: String): List<RouteStationKmEntity> {
    val results = mutableListOf<RouteStationKmEntity>()
    val lines = text.lines()
    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) continue
        val parts = line.split(Regex("[:：]"))
        if (parts.size >= 3) {
            val friendlyName = parts[0].trim()
            val routeName = parts[1].trim()
            val kmRaw = parts[2].trim()
                .replace("km", "", ignoreCase = true)
                .replace("公里", "")
                .trim()
            val km = kmRaw.toDoubleOrNull()
            if (routeName.isNotBlank() && km != null && km >= 0.0) {
                results.add(
                    RouteStationKmEntity(
                        routeName = routeName,
                        stationKm = km,
                        nickname = friendlyName,
                        updatedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        } else if (parts.size == 2) {
            // 兼容容错：线路:距离
            val routeName = parts[0].trim()
            val kmRaw = parts[1].trim()
                .replace("km", "", ignoreCase = true)
                .replace("公里", "")
                .trim()
            val km = kmRaw.toDoubleOrNull()
            if (routeName.isNotBlank() && km != null && km >= 0.0) {
                results.add(
                    RouteStationKmEntity(
                        routeName = routeName,
                        stationKm = km,
                        nickname = routeName,
                        updatedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
    return results
}

@Composable
fun RouteExportImportDialog(
    savedRoutes: List<RouteStationKmEntity>,
    onDismissRequest: () -> Unit,
    onImport: (List<RouteStationKmEntity>) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // 如果当前有数据默认打开导出，没有数据默认打开导入
    var currentMode by remember {
        mutableStateOf(if (savedRoutes.isNotEmpty()) ExportImportMode.EXPORT else ExportImportMode.IMPORT)
    }

    // 导出的文本内容
    val exportedText = remember(savedRoutes) {
        formatRoutesToExportText(savedRoutes)
    }

    // 导入输入框内容
    var importInputText by remember { mutableStateOf("") }
    val parsedRoutes = remember(importInputText) {
        parseRoutesFromImportText(importInputText)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("route_export_import_dialog"),
        shape = RoundedCornerShape(16.dp),
        containerColor = SurfaceCard,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (currentMode == ExportImportMode.EXPORT) Icons.Default.FileUpload else Icons.Default.FileDownload,
                        contentDescription = "Title Icon",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "位置与公里标 导出 / 导入",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 说明横幅：导入导出功能通过文字复制和解析实现
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryBlueSoft, RoundedCornerShape(10.dp))
                        .border(1.dp, PrimaryBlue.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "说明",
                            tint = PrimaryBlueDark,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "功能说明",
                                color = PrimaryBlueDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "本功能通过文字复制和解析实现，无需网络即可快速备份、迁移或共享公里标数据。\n格式：位置友好名称:线路:小数距离(单位km)，每行一条。",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 模式切换选项卡 (导出 / 导入)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 导出 Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (currentMode == ExportImportMode.EXPORT) PrimaryBlue else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { currentMode = ExportImportMode.EXPORT }
                            .padding(vertical = 8.dp)
                            .testTag("tab_export"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = "Export Tab",
                                tint = if (currentMode == ExportImportMode.EXPORT) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "导出数据",
                                color = if (currentMode == ExportImportMode.EXPORT) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (currentMode == ExportImportMode.EXPORT) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // 导入 Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (currentMode == ExportImportMode.IMPORT) PrimaryBlue else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { currentMode = ExportImportMode.IMPORT }
                            .padding(vertical = 8.dp)
                            .testTag("tab_import"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Import Tab",
                                tint = if (currentMode == ExportImportMode.IMPORT) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "导入数据",
                                color = if (currentMode == ExportImportMode.IMPORT) Color.White else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (currentMode == ExportImportMode.IMPORT) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (currentMode) {
                    ExportImportMode.EXPORT -> {
                        // 导出视图
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "导出的文本内容 (${savedRoutes.size}条)：",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (exportedText.isNotBlank()) {
                                Text(
                                    text = "支持自行选择或一键复制",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 宽大的导出文本框
                        OutlinedTextField(
                            value = exportedText,
                            onValueChange = {}, // 只读展示，用户可自由双击选中、长按选择或滑动浏览
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 130.dp, max = 220.dp)
                                .testTag("export_text_box"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TextPrimary
                            ),
                            placeholder = {
                                Text(
                                    text = "当前暂无已保存的线路数据。\n请先点击「添加线路」配置本站位置与公里标后再导出。",
                                    fontSize = 12.sp,
                                    color = TextSubtle
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceSecondary,
                                unfocusedContainerColor = SurfaceSecondary,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = BorderLight
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 一键复制按钮
                        Button(
                            onClick = {
                                if (exportedText.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(exportedText))
                                    Toast.makeText(
                                        context,
                                        "已复制 ${savedRoutes.size} 条线路配置到剪贴板",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "当前暂无数据可复制",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = exportedText.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("copy_exported_text_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "一键复制到剪贴板",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    ExportImportMode.IMPORT -> {
                        // 导入视图
                        Text(
                            text = "在下方输入或粘贴导出的文本：",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // 宽大的输入对话框
                        OutlinedTextField(
                            value = importInputText,
                            onValueChange = { importInputText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 220.dp)
                                .testTag("import_text_input"),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = TextPrimary
                            ),
                            placeholder = {
                                Text(
                                    text = "点击此处输入或粘贴导出的文本...\n格式：位置友好名称:线路:小数距离(单位km)\n例如：\n南京站:京沪线:301.200\n常州站:京沪线:165.800\n南京南站:沪宁城际:298.500",
                                    fontSize = 12.sp,
                                    color = TextSubtle,
                                    lineHeight = 17.sp
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceSecondary,
                                unfocusedContainerColor = SurfaceSecondary,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = BorderLight
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 粘贴剪贴板快捷按钮（填满整行，和“确定导入”等宽）
                        OutlinedButton(
                            onClick = {
                                val clipboardContent = clipboardManager.getText()?.text
                                if (!clipboardContent.isNullOrBlank()) {
                                    importInputText = clipboardContent
                                    Toast.makeText(
                                        context,
                                        "已粘贴剪贴板内容",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "剪贴板为空",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PrimaryBlueSoft.copy(alpha = 0.4f),
                                contentColor = PrimaryBlueDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("paste_clipboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste",
                                tint = PrimaryBlueDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "粘贴剪贴板",
                                color = PrimaryBlueDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 实时解析识别反馈
                        if (importInputText.isNotBlank()) {
                            if (parsedRoutes.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PrimaryBlueSoft, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Valid",
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "成功识别到 ${parsedRoutes.size} 条有效线路公里标",
                                        color = EmeraldGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { importInputText = "" },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Clear",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(RedAlert.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Invalid",
                                        tint = RedAlert,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "未能解析到有效数据，请检查格式是否为「位置:线路:距离」",
                                        color = RedAlert,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        // 确定导入按钮
                        Button(
                            onClick = {
                                if (parsedRoutes.isNotEmpty()) {
                                    onImport(parsedRoutes)
                                    Toast.makeText(
                                        context,
                                        "已成功导入 ${parsedRoutes.size} 条线路公里标！",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onDismissRequest()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "未检测到有效的线路数据，请检查输入格式",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = parsedRoutes.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("confirm_import_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                disabledContainerColor = BorderLight
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Confirm",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (parsedRoutes.isNotEmpty()) "确定导入 (${parsedRoutes.size}条)" else "确定导入",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("dismiss_export_import_dialog")
            ) {
                Text(
                    text = "关闭",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    )
}
