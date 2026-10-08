package com.example.util

import android.content.Context
import android.os.Environment
import com.example.decoder.ArrivalEstimator
import com.example.decoder.TrainTelemetry
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 按自然日追加 LBJ 信号 CSV。
 *
 * 每一次 TrainTelemetry 回调都追加一行，不做内容去重。
 * 文件保存到应用专属的 Documents/LBJ-CSV 目录，不需要存储权限。
 */
class LbjCsvLogger(context: Context) {

    companion object {
        private const val DIRECTORY_NAME = "LBJ-CSV"
        private const val HEADER = "时间,车次,方向,速度,车型,车号,线路,公里标,列车类型,经度 纬度"

        private val FILE_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        private val TIME_FORMAT = SimpleDateFormat("yyyy/M/d H:mm", Locale.getDefault())
    }

    private val lock = Any()
    private val rootDirectory: File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
            DIRECTORY_NAME
        )

    /**
     * 追加一次 LBJ 信号。
     * timestamp 使用手机本地时间对应的 epoch millis。
     */
    fun append(telemetry: TrainTelemetry, timestamp: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            try {
                if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
                    return
                }

                val fileName = "${FILE_DATE_FORMAT.format(Date(timestamp))}.csv"
                val file = File(rootDirectory, fileName)
                val isNewFile = !file.exists() || file.length() == 0L

                FileOutputStream(file, true).use { output ->
                    if (isNewFile) {
                        output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    }

                    BufferedWriter(
                        OutputStreamWriter(output, StandardCharsets.UTF_8)
                    ).use { writer ->
                        if (isNewFile) {
                            writer.write(HEADER)
                            writer.newLine()
                        }

                        val (locoModel, locoNumber) = splitLocomotive(telemetry.locoModel)
                        val coordinates = buildCoordinates(telemetry.longitude, telemetry.latitude)

                        val row = listOf(
                            TIME_FORMAT.format(Date(timestamp)),
                            csvField(telemetry.trainNo),
                            csvField(telemetry.direction),
                            csvField(normalizeSpeed(telemetry.speed)),
                            csvField(locoModel),
                            csvField(locoNumber),
                            csvField(normalizeUnknown(telemetry.route)),
                            csvField(ArrivalEstimator.normalizePositionKm(telemetry.positionKm)),
                            csvField(telemetry.category),
                            csvField(coordinates)
                        ).joinToString(",")

                        writer.write(row)
                        writer.newLine()
                        writer.flush()
                    }
                }
            } catch (_: Exception) {
                // CSV logging must never interrupt the live decoder.
            }
        }
    }

    fun getDirectory(): File = rootDirectory

    data class DailyFileInfo(
        val name: String,
        val sizeBytes: Long
    )

    fun listDailyFiles(): List<DailyFileInfo> {
        return rootDirectory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
            ?.sortedByDescending { it.name }
            ?.map { DailyFileInfo(it.name, it.length()) }
            ?.toList()
            ?: emptyList()
    }

    fun getDailyFile(name: String): File? {
        val file = File(rootDirectory, name)
        return if (
            file.isFile &&
            file.extension.equals("csv", ignoreCase = true) &&
            file.parentFile?.canonicalFile == rootDirectory.canonicalFile
        ) {
            file
        } else {
            null
        }
    }

    private fun splitLocomotive(value: String): Pair<String, String> {
        if (value.isBlank() || value == "----") {
            return "****" to "****"
        }

        val separator = value.lastIndexOf('-')
        if (separator > 0 && separator < value.lastIndex) {
            val model = value.substring(0, separator).trim()
            val number = value.substring(separator + 1).trim()
            if (model.isNotEmpty() && number.isNotEmpty()) {
                return model to number
            }
        }

        return value to "****"
    }

    private fun normalizeUnknown(value: String): String {
        return if (value.isBlank() || value == "----" || value == "---") "****" else value
    }


    private fun normalizeSpeed(value: String): String {
        return if (value.isBlank() || value == "---" || value == "----") "" else value
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

    private fun csvField(value: String): String {
        if (value.none { ch -> ch == ',' || ch == '"' || ch == '\n' || ch == '\r' }) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
