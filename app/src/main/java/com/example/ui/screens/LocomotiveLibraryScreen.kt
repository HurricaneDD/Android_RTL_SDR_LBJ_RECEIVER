package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.RedAlert
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.LocomotiveLibraryEntry
import com.example.util.LocomotiveLibrarySource

@Composable
fun LocomotiveLibraryScreen(
    source: LocomotiveLibrarySource,
    entries: List<LocomotiveLibraryEntry>,
    onBack: () -> Unit,
    onSelectSource: (LocomotiveLibrarySource) -> Unit,
    onAddOrEdit: (Int, String) -> String?,
    onDelete: (Int) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingEntry by remember { mutableStateOf<LocomotiveLibraryEntry?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var codeText by remember { mutableStateOf("") }
    var nameText by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<String?>(null) }

    fun openAdd() {
        editingEntry = null
        codeText = ""
        nameText = ""
        editorError = null
        showEditor = true
    }

    fun openEdit(entry: LocomotiveLibraryEntry) {
        editingEntry = entry
        codeText = entry.code.toString()
        nameText = entry.name
        editorError = null
        showEditor = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "车型库",
                    color = PrimaryBlueDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "仅使用当前选中的车型库进行 LBJ 机车代号解析",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = ::openAdd) {
                Icon(Icons.Default.Add, contentDescription = "新增")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onSelectSource(LocomotiveLibrarySource.BUILTIN) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (source == LocomotiveLibrarySource.BUILTIN) "✓ 内置车型库" else "内置车型库",
                    fontSize = 12.sp
                )
            }
            OutlinedButton(
                onClick = { onSelectSource(LocomotiveLibrarySource.EXTERNAL) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (source == LocomotiveLibrarySource.EXTERNAL) "✓ 外置车型库" else "外置车型库",
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前使用",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (source == LocomotiveLibrarySource.BUILTIN) "内置车型库" else "外置车型库",
                        color = PrimaryBlueDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${entries.size} 项",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Text("导入 TXT", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onExport,
                enabled = entries.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("导出 TXT", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (entries.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderLight, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard)
            ) {
                Text(
                    text = "当前车型库为空。收到该代号时将显示“未知(代号)”，不会回退到另一个车型库。",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.code }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.code.toString().padStart(3, '0'),
                                color = PrimaryBlueDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.size(width = 52.dp, height = 28.dp)
                            )
                            Text(
                                text = entry.name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { openEdit(entry) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "编辑",
                                    tint = PrimaryBlueDark
                                )
                            }
                            IconButton(
                                onClick = { onDelete(entry.code) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = RedAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = {
                Text(if (editingEntry == null) "新增车型" else "编辑车型")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it.filter(Char::isDigit).take(3) },
                        label = { Text("车型代号") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text("车型名称") },
                        singleLine = true
                    )
                    editorError?.let {
                        Text(it, color = RedAlert, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val code = codeText.toIntOrNull()
                        editorError = when {
                            code == null || code !in 0..999 -> "车型代号必须是 0~999"
                            nameText.trim().isEmpty() -> "车型名称不能为空"
                            else -> {
                                editingEntry?.let { oldEntry ->
                                    if (oldEntry.code != code) {
                                        onDelete(oldEntry.code)
                                    }
                                }
                                onAddOrEdit(code, nameText)
                            }
                        }
                        if (editorError == null) {
                            showEditor = false
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("取消")
                }
            }
        )
    }
}
