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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.data.TrainRecord
import com.example.ui.theme.BlueUp
import com.example.ui.theme.BlueUpSoft
import com.example.ui.theme.BorderLight
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PurpleTech
import com.example.ui.theme.RedAlert
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextSubtle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    records: List<TrainRecord>,
    onClearAll: () -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onOpenRecord: (TrainRecord) -> Unit = {},
    onImportCsv: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "列车接收历史 (${records.size})",
                    color = PrimaryBlueDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "点击列车卡片查看逐条 LBJ 信号、速度、位置及坐标记录",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            OutlinedButton(
                onClick = { if (records.isNotEmpty()) showClearDialog = true },
                enabled = records.isNotEmpty(),
                modifier = Modifier.testTag("clear_history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear",
                    tint = if (records.isNotEmpty()) RedAlert else TextMuted,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = "一键清空",
                    color = if (records.isNotEmpty()) RedAlert else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onImportCsv,
                modifier = Modifier.weight(1f)
            ) {
                Text("导入 CSV", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onExportCsv,
                enabled = records.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("导出 CSV", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Empty",
                        tint = TextSubtle,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无列车接收记录",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "启动 SDR 接收或仿真模式后，探测到的列车将自动留档在此",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        TrainRecordCard(
                            record = record,
                            firstSeenStr = timeFormat.format(Date(record.firstSeenTime)),
                            lastSeenStr = timeFormat.format(Date(record.lastSeenTime)),
                            onDelete = { onDeleteRecord(record.id) },
                            onClick = { onOpenRecord(record) }
                        )
                    }
                }

                HistoryScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .offset(x = 8.dp)
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = SurfaceCard,
            title = { Text("清空历史记录", color = RedAlert, fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除所有已保存的列车接收记录吗？此操作无法撤销。", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAlert)
                ) {
                    Text("清空", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun HistoryScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo.size

    if (totalItems <= visibleItems || visibleItems == 0) return

    var trackHeightPx by remember { mutableStateOf(0) }
    val viewportHeightPx =
        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
    val minThumbPx = 64
    val rawThumbPx = (viewportHeightPx.toFloat() * visibleItems / totalItems).roundToInt()
    val thumbHeightPx = rawThumbPx.coerceIn(minThumbPx, viewportHeightPx)
    val maxTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0)

    val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()
    val averageItemHeightPx = if (visibleItems > 0) {
        viewportHeightPx.toFloat() / visibleItems
    } else {
        1f
    }
    val maxFirstPosition = (totalItems - visibleItems).coerceAtLeast(1)
    val firstPosition = (firstVisible?.index ?: 0) +
        ((firstVisible?.offset ?: 0) / averageItemHeightPx)
    val progress = (firstPosition / maxFirstPosition).coerceIn(0f, 1f)
    val thumbTopPx = (maxTravelPx * progress).roundToInt()
    val scrollScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .width(28.dp)
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(totalItems, visibleItems, trackHeightPx, thumbHeightPx) {
                var dragStartTopPx = thumbTopPx.toFloat()
                var accumulatedDragPx = 0f
                var dragScrollJob: Job? = null

                detectDragGestures(
                    onDragStart = {
                        dragStartTopPx = thumbTopPx.toFloat()
                        accumulatedDragPx = 0f
                        dragScrollJob?.cancel()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (maxTravelPx <= 0) return@detectDragGestures

                        accumulatedDragPx += dragAmount.y
                        val nextTopPx = (dragStartTopPx + accumulatedDragPx)
                            .coerceIn(0f, maxTravelPx.toFloat())

                        val targetProgress = (nextTopPx / maxTravelPx).coerceIn(0f, 1f)
                        val targetIndex = (targetProgress * maxFirstPosition)
                            .roundToInt()
                            .coerceIn(0, maxFirstPosition)

                        dragScrollJob?.cancel()
                        dragScrollJob = scrollScope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    },
                    onDragEnd = {
                        dragScrollJob?.cancel()
                        dragScrollJob = null
                    },
                    onDragCancel = {
                        dragScrollJob?.cancel()
                        dragScrollJob = null
                    }
                )
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(
                    BorderLight.copy(alpha = 0.65f),
                    RoundedCornerShape(4.dp)
                )
        )

        Box(
            modifier = Modifier
                .width(10.dp)
                .height(
                    with(androidx.compose.ui.platform.LocalDensity.current) {
                        thumbHeightPx.toDp()
                    }
                )
                .offset(
                    x = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp },
                    y = with(androidx.compose.ui.platform.LocalDensity.current) { thumbTopPx.toDp() }
                )
                .align(Alignment.TopEnd)
                .background(
                    TextMuted.copy(alpha = 0.82f),
                    RoundedCornerShape(6.dp)
                )
        )
    }
}

@Composable
fun TrainRecordCard(
    record: TrainRecord,
    firstSeenStr: String,
    lastSeenStr: String,
    onDelete: () -> Unit,
    onClick: () -> Unit = {}
) {
    val durationSeconds = kotlin.math.max(0L, (record.lastSeenTime - record.firstSeenTime) / 1000L)
    val durationStr = if (durationSeconds >= 60) {
        "${durationSeconds / 60}分${durationSeconds % 60}秒"
    } else {
        "${durationSeconds}秒"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Train No + Direction + Category + Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Train number
                Text(
                    text = record.trainNo,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Direction badge
                val (dirBg, dirFg) = when (record.direction) {
                    "下行" -> Pair(EmeraldSoft, EmeraldGreen)
                    "上行" -> Pair(BlueUpSoft, BlueUp)
                    else -> Pair(SurfaceSecondary, TextMuted)
                }
                Box(
                    modifier = Modifier
                        .background(dirBg, RoundedCornerShape(4.dp))
                        .border(0.5.dp, dirFg.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(record.direction, color = dirFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Category
                Text(
                    text = record.category,
                    color = PrimaryBlueDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextSubtle,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Locomotive & Route
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Train,
                        contentDescription = null,
                        tint = PurpleTech,
                        modifier = Modifier.size(15.dp).padding(end = 4.dp)
                    )
                    val locoDisplay = if (record.locoCode != "---" && record.locoCode.isNotBlank()) {
                        "${record.locoModel} (${record.locoCode})"
                    } else {
                        record.locoModel
                    }
                    Text(
                        text = "机车: $locoDisplay",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(15.dp).padding(end = 4.dp)
                    )
                    Text(
                        text = "线路: ${record.route}",
                        color = EmeraldGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Timestamps (首次收到 & 信号丢失) + Duration
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceSecondary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp).padding(end = 3.dp)
                            )
                            Text(
                                text = "首次收到: $firstSeenStr",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(12.dp).padding(end = 3.dp)
                            )
                            Text(
                                text = "信号丢失: $lastSeenStr",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(SurfaceCard, RoundedCornerShape(4.dp))
                            .border(0.5.dp, BorderLight, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "持续: $durationStr",
                            color = PrimaryBlueDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}