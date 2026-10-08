package com.example.util

import android.content.Context
import androidx.core.content.edit
import com.example.decoder.LocomotiveDict

enum class LocomotiveLibrarySource {
    BUILTIN,
    EXTERNAL
}

data class LocomotiveLibraryEntry(
    val code: Int,
    val name: String
)

/**
 * 管理可编辑的内置车型库和外置车型库。
 *
 * 内置库首次使用时来自 LocomotiveDict 的默认表，之后的增删改也会持久化。
 * 外置库为空时保持为空，解码器不会回退到内置库。
 */
class LocomotiveLibraryManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "lbj_locomotive_library"
        private const val KEY_SOURCE = "source"
        private const val KEY_BUILTIN = "builtin"
        private const val KEY_EXTERNAL = "external"
        private const val SOURCE_BUILTIN = "builtin"
        private const val SOURCE_EXTERNAL = "external"
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getSource(): LocomotiveLibrarySource {
        return when (prefs.getString(KEY_SOURCE, SOURCE_BUILTIN)) {
            SOURCE_EXTERNAL -> LocomotiveLibrarySource.EXTERNAL
            else -> LocomotiveLibrarySource.BUILTIN
        }
    }

    fun setSource(source: LocomotiveLibrarySource) {
        prefs.edit { putString(KEY_SOURCE, source.storageValue()) }
        applyToDecoder()
    }

    fun getEntries(source: LocomotiveLibrarySource = getSource()): List<LocomotiveLibraryEntry> {
        return readMap(source)
            .entries
            .sortedBy { it.key }
            .map { LocomotiveLibraryEntry(it.key, it.value) }
    }

    fun addOrUpdate(code: Int, name: String) {
        require(code in 0..999) { "车型代号必须是 0~999" }
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "车型名称不能为空" }

        val source = getSource()
        val map = readMap(source).toMutableMap()
        map[code] = cleanName
        writeMap(source, map)
        applyToDecoder()
    }

    fun delete(code: Int) {
        val source = getSource()
        val map = readMap(source).toMutableMap()
        map.remove(code)
        writeMap(source, map)
        applyToDecoder()
    }

    /**
     * 将 TXT 内容整体导入当前选中的车型库，重复代号以后面的记录为准。
     *
     * 支持：
     * 代码<TAB>车型
     * 代码,车型
     * 代码=车型
     * 也允许可选表头“代号/代码 + 车型”。
     */
    fun importText(text: String): Int {
        val result = LinkedHashMap<Int, String>()
        val normalized = text
            .removePrefix("\uFEFF")
            .replace('＝', '=')
            .replace('：', ':')

        // 主要格式：100=解放    101=DF    102=DF2
        // 同一行可以连续写多项；空格/Tab 分隔；也兼容“代码:车型”。
        val entryPattern = Regex(
            """(?<!\d)(\d{1,3})\s*[=:]\s*([^\r\n]*?)(?=\s+\d{1,3}\s*[=:]\s*|$)"""
        )

        normalized.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                return@forEach
            }

            val matches = entryPattern.findAll(line).toList()
            matches.forEach { match ->
                val code = match.groupValues[1].toIntOrNull()
                val name = match.groupValues[2]
                    .trim()
                    .trimEnd(';', ',', '|')
                    .trim()

                if (code != null && code in 0..999 && name.isNotEmpty()) {
                    result[code] = name
                }
            }

            // 兜底：单独一行的“100 解放 / 100<TAB>解放 / 100,解放”
            if (matches.isEmpty()) {
                val pieces = when {
                    line.contains('\t') -> line.split('\t', limit = 2)
                    line.contains(',') -> line.split(',', limit = 2)
                    else -> {
                        val m = Regex("""^(\d{1,3})\s+(.+)$""").matchEntire(line)
                        if (m != null) listOf(m.groupValues[1], m.groupValues[2]) else emptyList()
                    }
                }

                if (pieces.size == 2) {
                    val code = pieces[0].trim().toIntOrNull()
                    val name = pieces[1].trim()
                    if (code != null && code in 0..999 && name.isNotEmpty()) {
                        result[code] = name
                    }
                }
            }
        }

        if (result.isEmpty()) {
            throw IllegalArgumentException(
                "TXT 中没有找到有效的“代号 + 车型”记录。请检查是否使用“100=解放”这类格式。"
            )
        }

        val source = getSource()
        writeMap(source, result)
        applyToDecoder()
        return result.size
    }

    fun exportText(): String {
        val builder = StringBuilder()
        builder.append('﻿')
        builder.appendLine("# ==================== 铁路机车车型库 ====================")
        builder.appendLine("# 格式: 注册号=车型名称")
        builder.appendLine("# 以 # 开头的行为注释，会被忽略")
        builder.appendLine("# ======================================================")

        getEntries()
            .chunked(5)
            .forEach { chunk ->
                builder.appendLine(
                    chunk.joinToString("    ") { entry ->
                        entry.code.toString() + "=" + entry.name
                    }
                )
            }

        return builder.toString()
    }

    fun resetBuiltinLibrary() {
        writeMap(LocomotiveLibrarySource.BUILTIN, LocomotiveDict.LOCOS)
        applyToDecoder()
    }

    fun applyToDecoder() {
        LocomotiveDict.setActiveLibrary(readMap(getSource()))
    }

    private fun readMap(source: LocomotiveLibrarySource): Map<Int, String> {
        val key = source.storageKey()
        val stored = prefs.getString(key, null)

        if (stored == null) {
            val defaults = if (source == LocomotiveLibrarySource.BUILTIN) {
                LocomotiveDict.LOCOS
            } else {
                emptyMap()
            }
            writeMap(source, defaults)
            return defaults
        }

        return parseStoredMap(stored)
    }

    private fun writeMap(source: LocomotiveLibrarySource, map: Map<Int, String>) {
        prefs.edit {
            putString(
                source.storageKey(),
                map.entries
                    .sortedBy { it.key }
                    .joinToString("\n") { "${it.key}\t${it.value.replace("\n", " ").replace("\r", " ")}" }
            )
        }
    }

    private fun parseStoredMap(stored: String): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        stored.lineSequence().forEach { line ->
            val pieces = line.split('	', limit = 2)
            if (pieces.size != 2) return@forEach
            val code = pieces[0].toIntOrNull() ?: return@forEach
            val name = pieces[1].trim()
            if (code in 0..999 && name.isNotEmpty()) {
                result[code] = name
            }
        }
        return result
    }

    private fun LocomotiveLibrarySource.storageKey(): String {
        return when (this) {
            LocomotiveLibrarySource.BUILTIN -> KEY_BUILTIN
            LocomotiveLibrarySource.EXTERNAL -> KEY_EXTERNAL
        }
    }

    private fun LocomotiveLibrarySource.storageValue(): String {
        return when (this) {
            LocomotiveLibrarySource.BUILTIN -> SOURCE_BUILTIN
            LocomotiveLibrarySource.EXTERNAL -> SOURCE_EXTERNAL
        }
    }
}
