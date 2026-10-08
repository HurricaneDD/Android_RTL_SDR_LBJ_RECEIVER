package com.example.ui.screens

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.SurfaceCard
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.TrainSignalRecord
import com.example.util.RailwayMapData
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class HistoryMapMode {
    OSM,
    SATELLITE
}

// 与 LBJ_Map/map.html 保持一致：ESRI 18 级为最高原生影像级别。
// MapView 允许继续放大到 22 级；osmdroid 会优先从缓存/低级瓦片生成放大后的近似瓦片。
private val EsriWorldImageryTileSource = object : OnlineTileSourceBase(
    "ESRI World Imagery",
    0,
    18,
    256,
    ".jpg",
    arrayOf(
        "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
    ),
    "Esri, Maxar, Earthstar Geographics, and the GIS User Community"
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return getBaseUrl() +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) +
            mImageFilenameEnding
    }
}

private data class MapPoint(
    val signal: TrainSignalRecord,
    val geoPoint: GeoPoint
)

private data class MapViewportKey(
    val points: List<String>,
    val selectedSignalId: Long?,
    val mapSignature: String
)

private class HistoryMapView(context: Context) : MapView(context) {
    var renderedMapMode: HistoryMapMode? = null
    var renderedRailwaySignature: String = ""
    var trainPositionOverlay: TrainPositionOverlay? = null
    var railwayLabelOverlay: RailwayLabelOverlay? = null

    private val railwayLabelRefreshHandler = Handler(Looper.getMainLooper())
    private val railwayLabelRefreshRunnable = Runnable {
        railwayLabelOverlay?.refreshLabels()
        invalidate()
    }

    fun scheduleRailwayLabelRefresh() {
        railwayLabelRefreshHandler.removeCallbacks(railwayLabelRefreshRunnable)
        railwayLabelRefreshHandler.postDelayed(railwayLabelRefreshRunnable, 150L)
    }

    fun clearRailwayLabelRefresh() {
        railwayLabelRefreshHandler.removeCallbacks(railwayLabelRefreshRunnable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

@Composable
fun HistoryTrackMap(
    signals: List<TrainSignalRecord>,
    railwayMapData: RailwayMapData? = null,
    mapMode: HistoryMapMode = HistoryMapMode.OSM,
    selectedSignalId: Long? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapPoints = remember(signals) {
        signals.mapNotNull { signal ->
            parseSignalCoordinate(signal)?.let { geoPoint ->
                MapPoint(signal, geoPoint)
            }
        }
    }
    val railwayMapSignature = railwayMapData?.let { it.fileName + ":" + it.lines.size + ":" + it.stations.size } ?: ""
    val viewportKey = remember(mapPoints, selectedSignalId, railwayMapSignature) {
        MapViewportKey(
            points = mapPoints.map {
                "${it.signal.id}:${it.geoPoint.latitude}:${it.geoPoint.longitude}"
            },
            selectedSignalId = selectedSignalId,
            mapSignature = railwayMapSignature
        )
    }
    var lastViewportKey by remember { mutableStateOf<MapViewportKey?>(null) }

    val mapView = remember(context) {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue =
            "SDR-LBJ/1.1.2 (" + context.packageName + ")"
        HistoryMapView(context).apply {
            setTileSource(if (mapMode == HistoryMapMode.OSM) TileSourceFactory.MAPNIK else EsriWorldImageryTileSource)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setUseDataConnection(true)
            minZoomLevel = 2.0
            maxZoomLevel = 22.0
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.scheduleRailwayLabelRefresh()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                mapView.scheduleRailwayLabelRefresh()
                return true
            }
        }
        mapView.addMapListener(mapListener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(mapListener)
            mapView.clearRailwayLabelRefresh()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val shouldFitViewport = viewportKey != lastViewportKey
                renderHistoryTrack(
                    mapView = view,
                    points = mapPoints,
                    railwayMapData = railwayMapData,
                    mapMode = mapMode,
                    selectedSignalId = selectedSignalId,
                    fitViewport = shouldFitViewport
                )
                if (shouldFitViewport) {
                    lastViewportKey = viewportKey
                }
            }
        )

        Card(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 8.dp),
            shape = RoundedCornerShape(7.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.92f))
        ) {
            Column {
                IconButton(
                    onClick = { mapView.controller.zoomIn() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "放大地图",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { mapView.controller.zoomOut() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "缩小地图",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


private class RailwayLabelOverlay(
    private val mapView: MapView,
    private val data: RailwayMapData
) : org.osmdroid.views.overlay.Overlay() {

    private val projectionPointA = Point()
    private val projectionPointB = Point()
    private val projectionPoint = Point()

    private val stationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }

    private val stationStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
    }

    private val textStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private data class LineLabel(
        val point: GeoPoint,
        val angle: Float,
        val name: String
    )

    private var lineLabels: List<LineLabel> = emptyList()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val zoom = mapView.zoomLevelDouble
        drawRailwayNames(canvas, zoom)
        drawStations(canvas, zoom)
    }

    /**
     * 按 LBJ_Map/map.html 的 renderLineLabels() 重新计算标签锚点。
     *
     * 关键点：
     * 1. 重新计算只发生在地图缩放/移动结束后的防抖刷新，而不是每一帧。
     * 2. 标签锚点取“当前可见连续线路段的屏幕中点”。
     * 3. 计算完成后锚点保存为 GeoPoint，所以用户拖动地图时，标签会跟着线路一起移动；
     *    下一次移动结束后才重新取新的可见段中点。
     */
    fun refreshLabels() {
        val zoom = mapView.zoomLevelDouble
        if (zoom < 8.0) {
            lineLabels = emptyList()
            return
        }

        val bounds = mapView.boundingBox
        val labels = ArrayList<LineLabel>()

        data.lines.forEach lineLoop@ { line ->
            val lineName = line.name.trim()

            // 与 LBJ_Map 一致：线路数字编号不显示。
            if (lineName.isEmpty() || Regex("^线路\\d+$").matches(lineName)) return@lineLoop

            val points = line.points
            if (points.size < 2) return@lineLoop

            // 与 LBJ_Map 一致：只取当前视野内连续可见的线路段。
            val visibleSegments = ArrayList<List<GeoPoint>>()
            var currentSegment = ArrayList<GeoPoint>()

            points.forEach { point ->
                if (bounds.contains(point)) {
                    currentSegment.add(point)
                } else {
                    if (currentSegment.size >= 2) {
                        visibleSegments.add(currentSegment)
                    }
                    currentSegment = ArrayList()
                }
            }

            if (currentSegment.size >= 2) {
                visibleSegments.add(currentSegment)
            }

            visibleSegments.forEach segmentLoop@ { segment ->
                val pixelPoints = segment.map { point ->
                    mapView.projection.toPixels(point, Point())
                }

                var pixelLength = 0.0
                for (i in 1 until pixelPoints.size) {
                    val dx = (pixelPoints[i].x - pixelPoints[i - 1].x).toDouble()
                    val dy = (pixelPoints[i].y - pixelPoints[i - 1].y).toDouble()
                    pixelLength += Math.hypot(dx, dy)
                }

                // 与 LBJ_Map 一致：不足 80px 不放线路名。
                if (pixelLength < 80.0) return@segmentLoop

                val targetDistance = pixelLength / 2.0
                var accumulated = 0.0
                var midPoint = pixelPoints.first()
                var directionA = pixelPoints.first()
                var directionB = pixelPoints.getOrNull(1) ?: directionA

                for (i in 1 until pixelPoints.size) {
                    val p1 = pixelPoints[i - 1]
                    val p2 = pixelPoints[i]
                    val dx = (p2.x - p1.x).toDouble()
                    val dy = (p2.y - p1.y).toDouble()
                    val segmentDistance = Math.hypot(dx, dy)

                    if (accumulated + segmentDistance >= targetDistance) {
                        directionA = p1
                        directionB = p2
                        val ratio = if (segmentDistance > 0.0) {
                            (targetDistance - accumulated) / segmentDistance
                        } else {
                            0.0
                        }

                        midPoint = Point(
                            (p1.x + (p2.x - p1.x) * ratio).roundToInt(),
                            (p1.y + (p2.y - p1.y) * ratio).roundToInt()
                        )
                        break
                    }

                    accumulated += segmentDistance
                }

                var angle = Math.toDegrees(
                    atan2(
                        (directionB.y - directionA.y).toDouble(),
                        (directionB.x - directionA.x).toDouble()
                    )
                ).toFloat()

                // 与 LBJ_Map 一致：文字保持正向。
                if (angle > 90f) {
                    angle -= 180f
                } else if (angle < -90f) {
                    angle += 180f
                }

                // 与 LBJ_Map 的 containerPointToLatLng(midPt) 等价：
                // 将当前可见段的屏幕中点转换回地理坐标，作为本次标签的地理锚点。
                val projectedCenter = mapView.projection.fromPixels(
                    midPoint.x,
                    midPoint.y,
                    null
                )
                val geoAnchor = GeoPoint(
                    projectedCenter.latitude,
                    projectedCenter.longitude
                )

                labels.add(
                    LineLabel(
                        point = geoAnchor,
                        angle = angle,
                        name = lineName
                    )
                )
            }
        }

        // 与 LBJ_Map 一致：按屏幕位置做简单碰撞过滤。
        // 这里先按照刚刚计算出的地理锚点投影一次，再保存通过碰撞检测的标签。
        val placedBoxes = ArrayList<RectF>()
        val filtered = ArrayList<LineLabel>()
        val density = mapView.resources.displayMetrics.density
        textPaint.textSize = 11f * density
        textStrokePaint.textSize = textPaint.textSize

        labels.forEach { label ->
            val anchor = mapView.projection.toPixels(label.point, projectionPoint)
            val textWidth = textPaint.measureText(label.name) + 10f * density
            val box = RectF(
                anchor.x - textWidth / 2f,
                anchor.y - 8f * density,
                anchor.x + textWidth / 2f,
                anchor.y + 8f * density
            )

            val overlap = placedBoxes.any { existing ->
                !(
                    box.right < existing.left - 10f * density ||
                        box.left > existing.right + 10f * density ||
                        box.bottom < existing.top - 5f * density ||
                        box.top > existing.bottom + 5f * density
                )
            }

            if (!overlap) {
                placedBoxes.add(box)
                filtered.add(label)
            }
        }

        lineLabels = filtered
    }

    private fun drawRailwayNames(canvas: Canvas, zoom: Double) {
        if (zoom < 8.0) return

        val density = mapView.resources.displayMetrics.density
        textPaint.textSize = 11f * density
        textStrokePaint.textSize = textPaint.textSize
        textStrokePaint.strokeWidth = 3.0f * density

        lineLabels.forEach { label ->
            val anchor = mapView.projection.toPixels(label.point, projectionPoint)
            val x = anchor.x.toFloat()
            val y = anchor.y.toFloat()

            if (x !in -250f..(mapView.width + 250f) ||
                y !in -150f..(mapView.height + 150f)
            ) {
                return@forEach
            }

            canvas.save()
            canvas.rotate(label.angle, x, y)
            val baseline = y - 4f * density
            canvas.drawText(label.name, x, baseline, textStrokePaint)
            canvas.drawText(label.name, x, baseline, textPaint)
            canvas.restore()
        }
    }

    private fun drawStations(canvas: Canvas, zoom: Double) {
        val density = mapView.resources.displayMetrics.density

        data.stations.forEach stationLoop@ { station ->
            val importance = station.importance
            val shouldShow = when {
                zoom >= 14.0 -> true
                zoom >= 11.0 -> importance <= 5
                zoom >= 9.0 -> importance <= 3
                zoom >= 7.0 -> importance <= 2
                zoom >= 5.0 -> importance <= 1
                else -> importance <= 0
            }
            if (!shouldShow) return@stationLoop

            mapView.projection.toPixels(station.point, projectionPoint)
            if (projectionPoint.x !in -100..(mapView.width + 100) ||
                projectionPoint.y !in -100..(mapView.height + 100)
            ) {
                return@stationLoop
            }

            val radius = when {
                zoom >= 15.0 -> 4.0f * density
                zoom >= 12.0 -> 3.5f * density
                else -> 3.0f * density
            }
            stationStrokePaint.strokeWidth = max(1f, 1.0f * density)
            canvas.drawCircle(
                projectionPoint.x.toFloat(),
                projectionPoint.y.toFloat(),
                radius + 1f * density,
                stationStrokePaint
            )
            canvas.drawCircle(
                projectionPoint.x.toFloat(),
                projectionPoint.y.toFloat(),
                radius,
                stationPaint
            )

            if (station.name.isNotBlank() && zoom >= 10.0) {
                textPaint.textSize = when {
                    zoom >= 15.0 -> 14f * density
                    zoom >= 13.0 -> 12f * density
                    else -> 10f * density
                }
                textStrokePaint.textSize = textPaint.textSize
                textStrokePaint.strokeWidth = 3.0f * density
                val textX = projectionPoint.x.toFloat()
                val textY = projectionPoint.y.toFloat() - radius - 5f * density
                canvas.drawText(station.name, textX, textY, textStrokePaint)
                canvas.drawText(station.name, textX, textY, textPaint)
            }
        }
    }
}

private fun renderHistoryTrack(
    mapView: HistoryMapView,
    points: List<MapPoint>,
    railwayMapData: RailwayMapData?,
    mapMode: HistoryMapMode,
    selectedSignalId: Long?,
    fitViewport: Boolean
) {
    val railwaySignature = railwayMapData?.let {
        it.fileName + ":" + it.lines.size + ":" + it.stations.size
    } ?: ""

    val baseChanged =
        mapView.renderedMapMode != mapMode ||
            mapView.renderedRailwaySignature != railwaySignature

    val geoPoints = points.map { it.geoPoint }
    val uniqueGeoPoints = geoPoints.distinctBy { point ->
        point.latitude.toString() + "," + point.longitude.toString()
    }
    val selectedPoint = selectedSignalId?.let { signalId ->
        points.firstOrNull { it.signal.id == signalId }
    }

    if (baseChanged) {
        mapView.setTileSource(
            if (mapMode == HistoryMapMode.OSM) {
                TileSourceFactory.MAPNIK
            } else {
                EsriWorldImageryTileSource
            }
        )
        mapView.overlays.clear()
        mapView.trainPositionOverlay = null
        mapView.railwayLabelOverlay = null

        val mapLinePoints = if (mapMode == HistoryMapMode.SATELLITE) {
            railwayMapData?.lines?.flatMap { it.points }.orEmpty()
        } else {
            emptyList()
        }

        val mapPointsForFit = when {
            uniqueGeoPoints.isNotEmpty() -> uniqueGeoPoints
            mapLinePoints.isNotEmpty() -> mapLinePoints
            else -> emptyList()
        }

        if (fitViewport) {
            when {
                selectedPoint != null -> {
                    mapView.controller.setCenter(selectedPoint.geoPoint)
                    mapView.controller.setZoom(18.5)
                }
                mapPointsForFit.size == 1 -> {
                    mapView.controller.setCenter(mapPointsForFit.first())
                    mapView.controller.setZoom(17.0)
                }
                mapPointsForFit.size > 1 -> {
                    val bounds = BoundingBox.fromGeoPoints(mapPointsForFit)
                    mapView.controller.setCenter(bounds.center)
                    mapView.zoomToBoundingBox(bounds.increaseByScale(1.25f), false)
                    val zoom = mapView.zoomLevelDouble.coerceIn(2.0, 22.0)
                    mapView.controller.setZoom(zoom)
                }
            }
        }

        val density = mapView.resources.displayMetrics.density
        if (mapMode == HistoryMapMode.SATELLITE) railwayMapData?.lines?.forEach { line ->
            if (line.points.size < 2) return@forEach

            if (line.outlineWidth > 0f) {
                val outline = Polyline(mapView).apply {
                    setPoints(line.points)
                    outlinePaint.color = withOpacity(line.outlineColor, line.opacity)
                    outlinePaint.strokeWidth = (line.width + line.outlineWidth * 2f) * density
                }
                mapView.overlays.add(outline)
            }

            val polyline = Polyline(mapView).apply {
                setPoints(line.points)
                outlinePaint.color = withOpacity(line.color, line.opacity)
                outlinePaint.strokeWidth = line.width * density
            }
            mapView.overlays.add(polyline)
        }

        if (mapMode == HistoryMapMode.SATELLITE && railwayMapData?.hasFeatures == true) {
            val labelOverlay = RailwayLabelOverlay(mapView, railwayMapData)
            mapView.railwayLabelOverlay = labelOverlay
            mapView.overlays.add(labelOverlay)
            labelOverlay.refreshLabels()
        }

        mapView.renderedMapMode = mapMode
        mapView.renderedRailwaySignature = railwaySignature
    } else if (fitViewport && selectedPoint != null) {
        // 切换公里标时只移动视口，不重建底图与铁路图层。
        mapView.controller.setCenter(selectedPoint.geoPoint)
        mapView.controller.setZoom(18.5)
    }

    val markerIndex = selectedPoint?.let { selected ->
        points.indexOfFirst { it.signal.id == selected.signal.id }
    }?.takeIf { it >= 0 }

    val markerPoint = markerIndex?.let { points.getOrNull(it) }
    if (markerPoint != null) {
        val angle = calculateScreenTrainAngle(mapView, points, markerIndex)
        val overlay = mapView.trainPositionOverlay

        if (overlay == null) {
            val newOverlay = TrainPositionOverlay(
                mapView,
                markerPoint.geoPoint
            )
            mapView.trainPositionOverlay = newOverlay
            mapView.overlays.add(newOverlay)
        } else {
            overlay.updatePosition(
                markerPoint.geoPoint,
                angle
            )
        }
    } else {
        mapView.trainPositionOverlay?.let { oldOverlay ->
            mapView.overlays.remove(oldOverlay)
            mapView.trainPositionOverlay = null
        }
    }

    mapView.invalidate()
}
private fun withOpacity(color: Int, opacity: Float): Int {
    val alpha = (AndroidColor.alpha(color) * opacity.coerceIn(0.0f, 1.0f)).toInt().coerceIn(0, 255)
    return AndroidColor.argb(
        alpha,
        AndroidColor.red(color),
        AndroidColor.green(color),
        AndroidColor.blue(color)
    )
}
private fun calculateScreenTrainAngle(
    mapView: MapView,
    points: List<MapPoint>,
    markerIndex: Int
): Float {
    if (points.size < 2) return 0f

    val target = points[markerIndex].geoPoint
    val neighbor = when {
        markerIndex < points.lastIndex -> points[markerIndex + 1].geoPoint
        markerIndex > 0 -> points[markerIndex - 1].geoPoint
        else -> target
    }

    if (target == neighbor) return 0f

    val a = mapView.projection.toPixels(target, Point())
    val b = mapView.projection.toPixels(neighbor, Point())
    val dx = (b.x - a.x).toFloat()
    val dy = (b.y - a.y).toFloat()

    if (dx == 0f && dy == 0f) return 0f

    return (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f) % 360f
}

private class TrainPositionOverlay(
    private val mapView: MapView,
    private var geoPoint: GeoPoint
) : org.osmdroid.views.overlay.Overlay() {

    private var angle: Float = 0f

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(85, 33, 150, 243)
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(33, 150, 243)
        style = Paint.Style.FILL
    }

    fun updatePosition(newGeoPoint: GeoPoint, newAngle: Float) {
        geoPoint = newGeoPoint
        angle = newAngle
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val density = mapView.resources.displayMetrics.density
        val point = mapView.projection.toPixels(geoPoint, Point())
        val centerX = point.x.toFloat()
        val centerY = point.y.toFloat()

        // 位置标记保持为无方向圆点；angle 仅保留给将来需要时的调试兼容，
        // 不对圆点做旋转，因此切换公里标不会造成方向图标重建闪烁。
        canvas.drawCircle(centerX, centerY, 11f * density, haloPaint)
        canvas.drawCircle(centerX, centerY, 7f * density, ringPaint)
        canvas.drawCircle(centerX, centerY, 5f * density, centerPaint)
    }

    override fun onSingleTapConfirmed(
        e: MotionEvent?,
        mapView: MapView?
    ): Boolean {
        return true
    }
}

private fun parseSignalCoordinate(signal: TrainSignalRecord): GeoPoint? {
    val longitude = parseDms(signal.longitude)
    val latitude = parseDms(signal.latitude)
    if (longitude == null || latitude == null) {
        val combined = listOf(signal.longitude.trim(), signal.latitude.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return parseCombinedCoordinate(combined)
    }

    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }
    if (kotlin.math.abs(latitude) <= 0.001 && kotlin.math.abs(longitude) <= 0.001) {
        return null
    }
    return GeoPoint(latitude, longitude)
}

private fun parseCombinedCoordinate(value: String): GeoPoint? {
    if (value.isBlank()) return null
    val parts = value.trim().split(Regex("\\s+"))
    if (parts.size < 2) return null

    val first = parseDms(parts[0]) ?: return null
    val second = parseDms(parts[1]) ?: return null

    // LBJ stores longitude first and latitude second.
    val longitude = first
    val latitude = second

    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
        return null
    }
    if (kotlin.math.abs(latitude) <= 0.001 && kotlin.math.abs(longitude) <= 0.001) {
        return null
    }
    return GeoPoint(latitude, longitude)
}

private fun parseDms(value: String): Double? {
    val text = value.trim()
    if (text.isEmpty()) return null

    val degreeIndex = text.indexOf('°')
    if (degreeIndex < 1) return null

    val suffix = text.lastOrNull()
    val minuteEnd = text.indexOfFirst { it == '′' || it == '\'' }
    val minuteText = if (minuteEnd > degreeIndex) {
        text.substring(degreeIndex + 1, minuteEnd)
    } else {
        text.substring(degreeIndex + 1).removeSuffix("E")
            .removeSuffix("W").removeSuffix("N").removeSuffix("S")
    }

    val degrees = text.substring(0, degreeIndex).toDoubleOrNull() ?: return null
    val minutes = minuteText.toDoubleOrNull() ?: return null
    if (minutes !in 0.0..<60.0) return null

    val directionMultiplier = when (suffix) {
        'W', 'S' -> -1.0
        else -> 1.0
    }
    return directionMultiplier * (degrees + minutes / 60.0)
}