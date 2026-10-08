package com.example.util

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Color as AndroidColor
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File

data class RailwayMapLine(
    val name: String,
    val points: List<GeoPoint>,
    val color: Int,
    val width: Float,
    val opacity: Float,
    val outlineColor: Int,
    val outlineWidth: Float
)

data class RailwayMapStation(
    val name: String,
    val lineName: String,
    val point: GeoPoint,
    val importance: Int = 4
)

data class RailwayMapData(
    val fileName: String,
    val lines: List<RailwayMapLine>,
    val stations: List<RailwayMapStation>
) {
    val featureCount: Int
        get() = lines.size + stations.size

    val hasFeatures: Boolean
        get() = lines.isNotEmpty() || stations.isNotEmpty()

    val info: RailwayMapDataInfo
        get() = RailwayMapDataInfo(
            fileName = fileName,
            lineCount = lines.size,
            stationCount = stations.size
        )
}

data class RailwayMapDataInfo(
    val fileName: String,
    val lineCount: Int,
    val stationCount: Int
)

class RailwayMapDataManager(context: Context) {

    private val appContext = context.applicationContext
    private val storageDir = File(appContext.filesDir, "railway_map")
    private val dataFile = File(storageDir, "current.json")
    private val nameFile = File(storageDir, "current_name.txt")

    fun load(): RailwayMapData? {
        if (!dataFile.isFile || dataFile.length() <= 0L) return null

        return try {
            val text = dataFile.readText(Charsets.UTF_8)
            val fileName = if (nameFile.isFile) {
                nameFile.readText(Charsets.UTF_8).trim().ifBlank { "current.json" }
            } else {
                "current.json"
            }
            parse(text, fileName)
        } catch (_: Exception) {
            null
        }
    }

    fun importFromUri(uri: Uri): RailwayMapData {
        val resolver = appContext.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法打开地图数据文件")

        if (bytes.isEmpty()) {
            throw IllegalArgumentException("地图数据文件为空")
        }

        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val displayName = queryDisplayName(uri)
            ?.trim()
            ?.ifBlank { "imported.json" }
            ?: "imported.json"

        val parsed = parse(text, displayName)
        if (!parsed.hasFeatures) {
            throw IllegalArgumentException("GeoJSON 中没有可识别的线路或车站要素")
        }

        storageDir.mkdirs()
        dataFile.writeText(text, Charsets.UTF_8)
        nameFile.writeText(displayName, Charsets.UTF_8)
        return parsed
    }

    fun info(): RailwayMapDataInfo? = load()?.info

    private fun parse(text: String, fileName: String): RailwayMapData {
        val root = JSONObject(text)
        val type = root.optString("type")
        if (!type.equals("FeatureCollection", ignoreCase = true)) {
            throw IllegalArgumentException("地图数据不是 GeoJSON FeatureCollection")
        }

        val features = root.optJSONArray("features")
            ?: throw IllegalArgumentException("GeoJSON 缺少 features")

        val lines = ArrayList<RailwayMapLine>()
        val stations = ArrayList<RailwayMapStation>()

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val properties = feature.optJSONObject("properties") ?: JSONObject()
            when (geometry.optString("type")) {
                "LineString" -> {
                    val railway = properties.optString("railway").trim().lowercase()
                    val type = properties.optString("type").trim().lowercase()

                    // The map project marks railway alignment explicitly as railway=rail.
                    // Keep type=normal as a compatible fallback when railway is omitted.
                    val isRailwayLine = railway == "rail" || (railway.isBlank() && type == "normal")
                    if (!isRailwayLine) continue

                    val points = parseLineString(geometry.optJSONArray("coordinates"))
                    if (points.size >= 2) {
                        lines += RailwayMapLine(
                            name = properties.optString("name").trim(),
                            points = points,
                            color = parseColor(properties.optString("color"), "#FFEB3B"),
                            width = properties.optDouble("width", 2.0).toFloat().coerceIn(1f, 12f),
                            opacity = properties.optDouble("opacity", 0.9).toFloat().coerceIn(0.05f, 1f),
                            outlineColor = parseColor(properties.optString("outlineColor"), "#000000"),
                            outlineWidth = properties.optDouble("outlineWidth", 1.0).toFloat().coerceIn(0f, 8f)
                        )
                    }
                }

                "Point" -> {
                    val coordinate = geometry.optJSONArray("coordinates") ?: continue
                    if (coordinate.length() < 2) continue

                    val longitude = coordinate.optDouble(0, Double.NaN)
                    val latitude = coordinate.optDouble(1, Double.NaN)
                    if (!longitude.isFinite() || !latitude.isFinite()) continue
                    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue

                    val railway = properties.optString("railway").trim().lowercase()
                    val category = properties.optString("category").trim().lowercase()
                    val pointType = properties.optString("type").trim().lowercase()
                    val name = properties.optString("name").trim()
                    val isStation =
                        (railway == "station" || railway == "halt" || railway == "service_station") ||
                        pointType == "station" ||
                        category == "station" ||
                        name.contains("站")

                    if (isStation) {
                        var importance = 4
                        when (railway) {
                            "station" -> importance = 4
                            "halt" -> importance = 8
                            "service_station" -> importance = 7
                        }
                        if (name.contains("线路所")) importance = 7
                        else if (name.contains("乘降所")) importance = 8
                        else if (name.contains("废弃")) importance = 9

                        stations += RailwayMapStation(
                            name = name.ifBlank { "车站" },
                            lineName = properties.optString("_lineName").trim()
                                .ifBlank { properties.optString("line").trim() },
                            point = GeoPoint(latitude, longitude),
                            importance = importance
                        )
                    }
                }
            }
        }

        return RailwayMapData(
            fileName = fileName,
            lines = lines,
            stations = stations
        )
    }

    private fun parseLineString(coordinates: JSONArray?): List<GeoPoint> {
        if (coordinates == null) return emptyList()
        val result = ArrayList<GeoPoint>(coordinates.length())
        for (i in 0 until coordinates.length()) {
            val pair = coordinates.optJSONArray(i) ?: continue
            if (pair.length() < 2) continue
            val longitude = pair.optDouble(0, Double.NaN)
            val latitude = pair.optDouble(1, Double.NaN)
            if (!longitude.isFinite() || !latitude.isFinite()) continue
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue
            result += GeoPoint(latitude, longitude)
        }
        return result
    }

    private fun parseColor(value: String, fallback: String): Int {
        return try {
            AndroidColor.parseColor(value.trim().ifBlank { fallback })
        } catch (_: Exception) {
            AndroidColor.parseColor(fallback)
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                uri.lastPathSegment
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }
}
