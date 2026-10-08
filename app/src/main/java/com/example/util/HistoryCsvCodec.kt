package com.example.util

import com.example.decoder.ArrivalEstimator

import com.example.data.TrainSignalRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史 LBJ 信号 CSV 编解码。
 *
 * 文件格式与 Windows 导出的历史 CSV 保持一致：
 * 时间,车次,方向,速度,车型,车号,线路,公里标,列车类型,经度 纬度
 *
 * 不做内容去重：CSV 中每一行都代表一个独立信号记录。
 */
object HistoryCsvCodec {

    const val HEADER = "时间,车次,方向,速度,车型,车号,线路,公里标,列车类型,经度 纬度"

    private val outputTimeFormat = SimpleDateFormat("yyyy/M/d H:mm", Locale.getDefault())
    private val inputTimeFormats = listOf(
        SimpleDateFormat("yyyy/M/d H:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy/M/d H:mm", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    )

    data class Row(
        val timestamp: Long,
        val trainNo: String,
        val direction: String,
        val speed: String,
        val locoModel: String,
        val locoCode: String,
        val route: String,
        val positionKm: String,
        val category: String,
        val longitude: String,
        val latitude: String,
        val sourceIndex: Int
    )

    fun encode(records: List<TrainSignalRecord>): String {
        val builder = StringBuilder()
        builder.append("\uFEFF")
        builder.appendLine(HEADER)
        records.forEach { record ->
            val coordinates = buildCoordinates(record.longitude, record.latitude)
            val row = listOf(
                outputTimeFormat.format(Date(record.timestamp)),
                record.trainNo,
                record.direction,
                record.speed,
                record.locoModel,
                record.locoCode,
                record.route,
                ArrivalEstimator.normalizePositionKm(record.positionKm),
                record.category,
                coordinates
            ).joinToString(",") { csvField(it) }
            builder.appendLine(row)
        }
        return builder.toString()
    }

    fun parse(csvText: String): List<Row> {
        val normalized = csvText.removePrefix("\uFEFF")
        val lines = normalized
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) {
            throw IllegalArgumentException("CSV 文件为空")
        }

        val header = parseLine(lines.first()).map { it.trim() }.joinToString(",")
        if (header != HEADER) {
            throw IllegalArgumentException(
                "CSV 表头不匹配，应为：$HEADER"
            )
        }

        val result = ArrayList<Row>(lines.size - 1)
        lines.drop(1).forEachIndexed { index, line ->
            val fields = parseLine(line)
            if (fields.size < 10) {
                return@forEachIndexed
            }

            val timestamp = parseTimestamp(fields[0])
                ?: return@forEachIndexed

            val (longitude, latitude) = parseCoordinates(fields[9])

            result += Row(
                timestamp = timestamp,
                trainNo = normalize(fields[1], "----"),
                direction = normalize(fields[2], "未知"),
                speed = fields[3].trim(),
                locoModel = normalize(fields[4], "----"),
                locoCode = normalize(fields[5], "---"),
                route = normalize(fields[6], "----"),
                positionKm = fields[7].trim(),
                category = normalize(fields[8], "列车"),
                longitude = longitude,
                latitude = latitude,
                sourceIndex = index
            )
        }

        if (result.isEmpty()) {
            throw IllegalArgumentException("CSV 中没有可导入的有效信号记录")
        }
        return result
    }

    private fun parseTimestamp(value: String): Long? {
        val text = value.trim()
        inputTimeFormats.forEach { format ->
            format.isLenient = false
            try {
                return format.parse(text)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseCoordinates(value: String): Pair<String, String> {
        val text = value.trim()
        if (text.isEmpty()) return "" to ""

        val match = Regex("^(.+?E)\\s+(.+?N)$").find(text)
        return if (match != null) {
            match.groupValues[1].trim() to match.groupValues[2].trim()
        } else {
            "" to ""
        }
    }

    private fun buildCoordinates(longitude: String, latitude: String): String {
        val lon = longitude.trim()
        val lat = latitude.trim()
        return when {
            lon.isNotEmpty() && lat.isNotEmpty() -> "$lon $lat"
            lon.isNotEmpty() -> lon
            lat.isNotEmpty() -> lat
            else -> ""
        }
    }

    private fun normalize(value: String, fallback: String): String {
        return value.trim().ifEmpty { fallback }
    }

    private fun csvField(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return value
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun parseLine(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0

        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                ch == '"' -> {
                    quoted = !quoted
                }
                ch == ',' && !quoted -> {
                    result += current.toString()
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            index++
        }

        result += current.toString()
        return result
    }
}
