package com.example.decoder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

class ArrivalEstimator(
    var defaultKm: Double? = null,
    var maxSeconds: Double = 6.0 * 3600.0,
    routeKmInitialMap: Map<String, Double> = emptyMap()
) {
    var downIncreases: Boolean = true
    val routeKmMap = HashMap<String, Double>()
    val knownRoutes = ArrayList<String>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        for ((r, km) in routeKmInitialMap) {
            setRouteKm(r, km)
        }
    }

    companion object {
        private val INVALID_CHARS_PATTERN = Pattern.compile("[\\*U\\(\\)<>\\[\\]{}\\\\/|;:,.，。！？?！@#$%^&_=+`~]")
        private val VALID_NAME_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9\\-\\s]+$")
        private val HAS_ALPHANUM_CHINESE = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z0-9]")

        fun sanitizeRoute(route: String?): String {
            if (route == null) return ""
            val r = route.replace("\u0000", "").trim()
            if (r.isEmpty() || r in listOf("----", "---", "未知", "等待信号...")) return ""
            return r
        }

        fun formatKm(km: Double?): String {
            return if (km != null) String.format(Locale.US, "%.1f KM", km) else "---"
        }

        /**
         * Parses railway milestone string, e.g.:
         * "K1300+200" -> 1300.2
         * "k145+800"  -> 145.8
         * "1300+200"  -> 1300.2
         * "K1300"     -> 1300.0
         * "145.8"     -> 145.8
         * "145.8KM"   -> 145.8
         */
        fun parseMilestone(v: Any?): Double? {
            if (v == null) return null
            val raw = v.toString().trim().uppercase()
                .replace("KM", "")
                .replace("公里", "")
                .replace("千米", "")
                .replace("米", "")
                .trim()
            if (raw.isEmpty() || raw in listOf("---", "---.-", "----", "未知")) return null

            val withoutK = if (raw.startsWith("K")) raw.substring(1).trim() else raw

            // Milestone format with '+' (e.g. 1300+200)
            if (withoutK.contains('+')) {
                val parts = withoutK.split('+')
                if (parts.size == 2) {
                    val kmPart = parts[0].trim().toDoubleOrNull() ?: return null
                    val mStr = parts[1].trim()
                    val mVal = mStr.toDoubleOrNull() ?: return null
                    // If user enters +200 -> 200m (0.2km), +80 -> 80m, +8 -> 800m or 8m? Standard: mVal / 1000.0
                    val total = kmPart + (mVal / 1000.0)
                    if (total < 0.0 || total > 99999.9) return null
                    return round(total * 1000.0) / 1000.0
                }
            }

            val direct = withoutK.toDoubleOrNull() ?: return null
            if (direct < 0.0 || direct > 99999.9) return null
            return round(direct * 1000.0) / 1000.0
        }

        fun parseKm(v: Any?): Double? {
            return parseMilestone(v)
        }

        /**
         * Formats kilometer double to railway milestone format, e.g.:
         * 1300.2 -> "K1300+200"
         * 145.8  -> "K145+800"
         * 1300.0 -> "K1300+000"
         */
        fun formatMilestone(km: Double?): String {
            if (km == null) return "---"
            val wholeKm = km.toInt()
            val m = round((km - wholeKm) * 1000.0).toInt().coerceIn(0, 999)
            return String.format(Locale.US, "K%d+%03d", wholeKm, m)
        }

        fun formatMilestoneWithKm(km: Double?): String {
            if (km == null) return "---"
            val wholeKm = km.toInt()
            val m = round((km - wholeKm) * 1000.0).toInt().coerceIn(0, 999)
            return String.format(Locale.US, "K%d+%03d (%.1f KM)", wholeKm, m, km)
        }

        fun isRouteValid(route: String?): Boolean {
            val r = sanitizeRoute(route)
            if (r.isEmpty() || r.length > 24) return false
            if (INVALID_CHARS_PATTERN.matcher(r).find()) return false
            if (!HAS_ALPHANUM_CHINESE.matcher(r).find()) return false
            return VALID_NAME_PATTERN.matcher(r).matches()
        }
    }

    fun addKnownRoute(route: String): String {
        val r = sanitizeRoute(route)
        if (!isRouteValid(r)) return ""
        if (!knownRoutes.contains(r)) {
            knownRoutes.add(r)
            if (knownRoutes.size > 20) {
                knownRoutes.removeAt(0)
            }
        }
        return r
    }

    fun getKmForRoute(route: String?): Double? {
        val r = sanitizeRoute(route)
        if (r.isNotEmpty()) {
            // 1. Exact match
            if (routeKmMap.containsKey(r)) {
                return routeKmMap[r]
            }
            // 2. Fuzzy match (e.g. "京沪" in "京沪高铁", "京沪线" vs "京沪高铁")
            val cleanR = r.replace("高铁", "").replace("客专", "").replace("铁路", "").replace("城际", "").replace("线", "").trim()
            for ((key, km) in routeKmMap) {
                if (r == key || r.contains(key) || key.contains(r)) {
                    return km
                }
                val cleanKey = key.replace("高铁", "").replace("客专", "").replace("铁路", "").replace("城际", "").replace("线", "").trim()
                if (cleanR.isNotEmpty() && cleanKey.isNotEmpty()) {
                    if (cleanR == cleanKey || cleanR.contains(cleanKey) || cleanKey.contains(cleanR)) {
                        return km
                    }
                }
            }
        }
        // 3. If there is only one route configured by the user, use it as default fallback
        if (routeKmMap.size == 1) {
            return routeKmMap.values.first()
        }
        return defaultKm
    }

    fun setRouteKm(route: String?, km: Double?): Boolean {
        val r = sanitizeRoute(route)
        if (r.isEmpty() || km == null) return false
        routeKmMap[r] = km
        addKnownRoute(r)
        return true
    }

    fun removeRouteKm(route: String?) {
        val r = sanitizeRoute(route)
        if (r.isNotEmpty()) {
            routeKmMap.remove(r)
        }
    }

    fun estimate(
        train: String?,
        direction: String?,
        speedStr: String?,
        positionStr: String?,
        routeStr: String?,
        goodData: Boolean = true,
        nowEpochMs: Long = System.currentTimeMillis()
    ): EtaInfo {
        val route = sanitizeRoute(routeStr)
        val routeGood = goodData && isRouteValid(route)
        if (routeGood) {
            addKnownRoute(route)
        }

        if (!goodData) {
            return EtaInfo(etaStatus = "错包忽略", etaTrain = train ?: "----", etaRoute = route)
        }
        if (route.isEmpty() && routeKmMap.size != 1 && defaultKm == null) {
            return EtaInfo(etaStatus = "线路未知", etaTrain = train ?: "----", etaRoute = "----")
        }

        val userKm = getKmForRoute(route)
            ?: return EtaInfo(etaStatus = "未设置本站位置", etaTrain = train ?: "----", etaRoute = route)

        if (train.isNullOrBlank() || train == "----") {
            return EtaInfo(etaStatus = "等待车次", etaTrain = "----", etaRoute = route)
        }
        if (direction != "上行" && direction != "下行") {
            return EtaInfo(etaStatus = "方向未知", etaTrain = train, etaRoute = route)
        }

        val speed = speedStr?.replace("km/h", "")?.trim()?.toDoubleOrNull()
        val trainKm = parseKm(positionStr)

        if (trainKm == null) {
            return EtaInfo(etaStatus = "公里标未知", etaTrain = train, etaRoute = route)
        }

        val distanceKm = if (downIncreases) {
            if (direction == "下行") userKm - trainKm else trainKm - userKm
        } else {
            if (direction == "下行") trainKm - userKm else userKm - trainKm
        }

        if (distanceKm < -0.05) {
            return EtaInfo(
                etaSeconds = null,
                etaTime = "--:--:--",
                etaDistanceKm = abs(distanceKm),
                etaStatus = "远离/已过",
                etaTrain = train,
                etaRoute = route
            )
        }

        if (speed == null || speed <= 0.0) {
            return EtaInfo(
                etaSeconds = null,
                etaTime = "--:--:--",
                etaDistanceKm = max(0.0, distanceKm),
                etaStatus = "停靠/静止",
                etaTrain = train,
                etaRoute = route
            )
        }

        val nonNegDistance = max(0.0, distanceKm)
        val etaSec = (nonNegDistance / speed) * 3600.0
        if (etaSec > maxSeconds) {
            return EtaInfo(
                etaSeconds = null,
                etaTime = "--:--:--",
                etaDistanceKm = nonNegDistance,
                etaStatus = "ETA过大",
                etaTrain = train,
                etaRoute = route
            )
        }

        val etaTime = timeFormat.format(Date(nowEpochMs + (etaSec * 1000).toLong()))
        val status = when {
            etaSec <= 15.0 || nonNegDistance <= 0.3 -> "即将到达"
            etaSec <= 300.0 -> "接近 (5分内)"
            else -> "接近中"
        }

        return EtaInfo(
            etaSeconds = etaSec,
            etaTime = etaTime,
            etaDistanceKm = nonNegDistance,
            etaStatus = status,
            etaTrain = train,
            etaRoute = route
        )
    }
}

