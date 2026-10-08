package com.example.ui.screens

import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.example.data.TrainRecord
import com.example.data.TrainSignalRecord
import com.example.ui.theme.BlueUp
import com.example.ui.theme.BlueUpSoft
import com.example.ui.theme.BorderLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PurpleTech
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.RailwayMapData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun HistoryDetailScreen(
    record: TrainRecord,
    signals: List<TrainSignalRecord>,
    railwayMapData: RailwayMapData? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    var mapMode by remember { mutableStateOf(HistoryMapMode.OSM) }
    var selectedSignalId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    LaunchedEffect(signals) {
        val selectedStillExists = selectedSignalId?.let { id ->
            signals.any { it.id == id && hasSignalCoordinate(it) }
        } == true
        if (!selectedStillExists) {
            selectedSignalId = signals.asReversed()
                .firstOrNull { hasSignalCoordinate(it) }
                ?.id
        }
    }
    val durationSeconds = max(0L, (record.lastSeenTime - record.firstSeenTime) / 1000L)
    val durationText = if (durationSeconds >= 60L) {
        (durationSeconds / 60L).toString() + "分" + (durationSeconds % 60L) + "秒"
    } else {
        durationSeconds.toString() + "秒"
    }

    val pageScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(pageScrollState)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回历史列表",
                    tint = TextPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "列车详情 · " + record.trainNo,
                    color = PrimaryBlueDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "共 " + signals.size + " 条 LBJ 信号记录",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Box {
                var mapMenuExpanded by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { mapMenuExpanded = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mapMode == HistoryMapMode.OSM) {
                            SurfaceSecondary
                        } else {
                            PrimaryBlueDark.copy(alpha = 0.10f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (mapMode == HistoryMapMode.OSM) "OSM" else "ESRI",
                            color = if (mapMode == HistoryMapMode.OSM) TextPrimary else PrimaryBlueDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "选择地图类型",
                            tint = TextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = mapMenuExpanded,
                    onDismissRequest = { mapMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("OSM") },
                        onClick = {
                            mapMode = HistoryMapMode.OSM
                            mapMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("ESRI") },
                        onClick = {
                            mapMode = HistoryMapMode.SATELLITE
                            mapMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        HistorySummaryCard(
            record = record,
            selectedSignal = selectedSignalId?.let { id -> signals.firstOrNull { it.id == id } },
            durationText = durationText,
            firstSeenText = timeFormat.format(Date(record.firstSeenTime)),
            lastSeenText = timeFormat.format(Date(record.lastSeenTime))
        )

        Spacer(modifier = Modifier.height(20.dp))

        val hasCoordinates = signals.any { hasSignalCoordinate(it) }
        val hasRailwayMapData = railwayMapData?.hasFeatures == true
        if (hasCoordinates || hasRailwayMapData) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                HistoryTrackMap(
                    signals = signals,
                    railwayMapData = railwayMapData,
                    mapMode = mapMode,
                    selectedSignalId = selectedSignalId,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        if (signals.isEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "该历史记录暂无逐条 LBJ 信号",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "历史公里标",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "左右滑动查看全部",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(signals, key = { it.id }) { signal ->
                    SignalRecordCard(
                        modifier = Modifier.width(132.dp),
                        signal = signal,
                        timeText = timeFormat.format(Date(signal.timestamp)),
                        selected = signal.id == selectedSignalId,
                        onClick = {
                            if (hasSignalCoordinate(signal)) {
                                selectedSignalId = signal.id
                            } else {
                                Toast.makeText(
                                    context,
                                    "该公里标没有接收到经纬信息",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
        Spacer(modifier = Modifier.height(96.dp))
    }
}

private fun buildCoordinateText(signal: TrainSignalRecord?): String {
    if (signal == null || !hasSignalCoordinate(signal)) return "暂无经纬信息"
    return listOf(signal.longitude.trim(), signal.latitude.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifBlank { "暂无经纬信息" }
}

@Composable
private fun HistorySummaryCard(
    record: TrainRecord,
    selectedSignal: TrainSignalRecord?,
    durationText: String,
    firstSeenText: String,
    lastSeenText: String
) {
    val (dirBg, dirFg) = when (record.direction) {
        "下行" -> EmeraldSoft to EmeraldGreen
        "上行" -> BlueUpSoft to BlueUp
        else -> SurfaceSecondary to TextMuted
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.trainNo,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .background(dirBg, RoundedCornerShape(4.dp))
                        .border(0.5.dp, dirFg.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                       .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(record.direction, color = dirFg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = record.category,
                    color = PrimaryBlueDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            DetailLine(
                icon = Icons.Default.Train,
                label = "机车",
                value = buildLocomotiveText(record),
                emphasized = true
            )
            DetailLine(
                icon = Icons.Default.Route,
                label = "线路",
                value = record.route,
                emphasized = true
            )
            DetailLine(
                icon = Icons.Default.LocationOn,
                label = "经纬度",
                value = buildCoordinateText(selectedSignal)
            )
            DetailLine(
                icon = Icons.Default.AccessTime,
                label = "首次收到",
                value = firstSeenText
            )
            DetailLine(
                icon = Icons.Default.AccessTime,
                label = "最后收到",
                value = lastSeenText
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "持续 " + durationText,
                    color = PrimaryBlueDark,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "信号 " + record.trainNo + " → " + record.direction,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DetailLine(
    icon: ImageVector,
    label: String,
    value: String,
    emphasized: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PurpleTech,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = label + ": ",
            color = TextMuted,
            fontSize = if (emphasized) 15.sp else 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value.ifBlank { "未知" },
            color = TextPrimary,
            fontSize = if (emphasized) 18.sp else 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun buildLocomotiveText(record: TrainRecord): String {
    return if (record.locoCode.isNotBlank() && record.locoCode != "---") {
        record.locoModel + " (" + record.locoCode + ")"
    } else {
        record.locoModel
    }
}

@Composable
private fun SignalRecordCard(
    modifier: Modifier = Modifier,
    signal: TrainSignalRecord,
    timeText: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val hasCoordinate = hasSignalCoordinate(signal)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) PrimaryBlueDark else BorderLight,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                androidx.compose.ui.graphics.Color(0xFFF1F7FF)
            } else {
                SurfaceCard
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = signal.positionKm.ifBlank { "未解析" },
                color = if (selected) PrimaryBlueDark else TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "速度 " + signal.speed.ifBlank { "未知" } + " km/h",
                color = if (selected) PrimaryBlueDark else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = timeText.substringAfter(' '),
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (hasCoordinate) "点击定位" else "无经纬信息",
                color = if (hasCoordinate) PrimaryBlueDark else TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun hasSignalCoordinate(signal: TrainSignalRecord): Boolean {
    return parseSignalCoordinateForDetail(signal) != null
}

private fun parseSignalCoordinateForDetail(signal: TrainSignalRecord): Pair<Double, Double>? {
    fun parseDms(value: String): Double? {
        val text = value.trim()
        if (text.isEmpty()) return null
        val degreeIndex = text.indexOf('°')
        if (degreeIndex < 1) return null

        val minuteEnd = text.indexOfFirst { it == '′' || it == '\'' }
        val minuteText = if (minuteEnd > degreeIndex) {
            text.substring(degreeIndex + 1, minuteEnd)
        } else {
            text.substring(degreeIndex + 1)
                .removeSuffix("E")
                .removeSuffix("W")
                .removeSuffix("N")
                .removeSuffix("S")
        }

        val degrees = text.substring(0, degreeIndex).toDoubleOrNull() ?: return null
        val minutes = minuteText.toDoubleOrNull() ?: return null
        if (minutes !in 0.0..<60.0) return null

        val suffix = text.lastOrNull()
        val multiplier = if (suffix == 'W' || suffix == 'S') -1.0 else 1.0
        return multiplier * (degrees + minutes / 60.0)
    }

    val longitude = parseDms(signal.longitude)
    val latitude = parseDms(signal.latitude)
    if (longitude == null || latitude == null) return null
    if (longitude !in -180.0..180.0 || latitude !in -90.0..90.0) return null
    if (kotlin.math.abs(longitude) <= 0.001 && kotlin.math.abs(latitude) <= 0.001) return null
    return longitude to latitude
}
