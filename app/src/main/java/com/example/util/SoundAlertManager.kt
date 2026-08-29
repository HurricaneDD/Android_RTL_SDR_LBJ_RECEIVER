package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

/**
 * SoundAlertManager manages audio alerts (short double-beep) and speech announcements
 * for SDR-LBJ incoming train signals.
 *
 * Speech Strategy:
 * 1. Uses System TextToSpeech if available and configured with Chinese voice data.
 * 2. Automatic fallback to NetworkTtsManager (online high-fidelity TTS API with persistent
 *    local disk caching for offline reuse).
 */
class SoundAlertManager(
    private val context: Context,
    private val externalScope: CoroutineScope? = null
) : TextToSpeech.OnInitListener {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val sampleRate = 16000
    private var playJob: Job? = null
    private val networkTts = NetworkTtsManager(context.applicationContext)

    var onSpeechStateChanged: ((Boolean) -> Unit)? = null

    init {
        networkTts.onSpeechStateChanged = { isSpeaking ->
            onSpeechStateChanged?.invoke(isSpeaking)
        }
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (_: Exception) {
            isTtsReady = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINA)
            val isChinaSupported = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            val fallbackRes = if (!isChinaSupported) tts?.setLanguage(Locale.CHINESE) else result
            val isChineseSupported = fallbackRes != TextToSpeech.LANG_MISSING_DATA && fallbackRes != TextToSpeech.LANG_NOT_SUPPORTED

            if (!isChineseSupported) {
                isTtsReady = false
                return
            }
            tts?.setPitch(1.0f)
            tts?.setSpeechRate(1.05f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeechStateChanged?.invoke(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onSpeechStateChanged?.invoke(false)
                }
            })

            isTtsReady = true
        } else {
            isTtsReady = false
        }
    }

    // Pre-calculated 0.25-second PCM 16-bit short buffer containing two short 1000Hz beeps ("滴滴")
    private val doubleBeepPcm: ShortArray by lazy {
        val totalDurationSec = 0.25
        val totalSamples = (sampleRate * totalDurationSec).toInt()
        val pcm = ShortArray(totalSamples)
        val freq = 1000.0 // 1.0 kHz railway alert pitch

        val beep1End = (sampleRate * 0.090).toInt()
        val silenceEnd = (sampleRate * 0.160).toInt()
        val beep2End = (sampleRate * 0.250).toInt()

        val envSamples = (sampleRate * 0.008).toInt() // 8ms fast attack/decay to prevent audio pop

        for (i in 0 until totalSamples) {
            when {
                i < beep1End -> {
                    val gain = when {
                        i < envSamples -> i.toDouble() / envSamples
                        beep1End - i < envSamples -> (beep1End - i).toDouble() / envSamples
                        else -> 1.0
                    }
                    val sampleVal = (sin(2.0 * Math.PI * freq * i / sampleRate) * 0.92 * gain * Short.MAX_VALUE).toInt()
                    pcm[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                i < silenceEnd -> {
                    pcm[i] = 0
                }
                i < beep2End -> {
                    val i2 = i - silenceEnd
                    val b2Len = beep2End - silenceEnd
                    val gain = when {
                        i2 < envSamples -> i2.toDouble() / envSamples
                        b2Len - i2 < envSamples -> (b2Len - i2).toDouble() / envSamples
                        else -> 1.0
                    }
                    val sampleVal = (sin(2.0 * Math.PI * freq * i2 / sampleRate) * 0.92 * gain * Short.MAX_VALUE).toInt()
                    pcm[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                else -> {
                    pcm[i] = 0
                }
            }
        }
        pcm
    }

    /**
     * Plays two short beeps ("滴滴") safely.
     */
    fun playDoubleBeep(onComplete: (() -> Unit)? = null) {
        playJob?.cancel()
        playJob = scope.launch(Dispatchers.IO) {
            onSpeechStateChanged?.invoke(true)
            try {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 92)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                delay(120)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                delay(120)
                tg.release()
            } catch (_: Exception) {}

            if (currentCoroutineContext().isActive) {
                if (onComplete != null) {
                    onComplete.invoke()
                } else {
                    delay(60)
                    onSpeechStateChanged?.invoke(false)
                }
            } else {
                onSpeechStateChanged?.invoke(false)
            }
        }
    }

    /**
     * Plays two short beeps ("滴滴"), and then immediately broadcasts the railway TTS speech.
     */
    fun playAlertAndSpeak(speechText: String, engineMode: String = "auto") {
        playDoubleBeep {
            speakText(speechText, engineMode)
        }
    }

    /**
     * Speaks the given text based on selected engine mode:
     * - "auto": Checks system TTS for valid Chinese support; if unavailable or fails, falls back to online TTS.
     * - "system": Forces System TTS engine.
     * - "online": Forces Online TTS API with local disk caching.
     */
    fun speakText(text: String, engineMode: String = "auto") {
        if (text.isBlank()) return
        scope.launch(Dispatchers.Main) {
            try {
                when (engineMode) {
                    "system" -> {
                        if (tts != null && isTtsReady) {
                            speakViaSystemTts(text)
                        } else {
                            // Fallback if system TTS is not ready
                            networkTts.speak(text)
                        }
                    }
                    "online" -> {
                        networkTts.speak(text)
                    }
                    else -> { // "auto"
                        var systemSuccess = false
                        if (tts != null && isTtsReady) {
                            systemSuccess = speakViaSystemTts(text)
                        }
                        if (!systemSuccess) {
                            networkTts.speak(text)
                        }
                    }
                }
            } catch (_: Exception) {
                networkTts.speak(text)
            }
        }
    }

    private fun speakViaSystemTts(text: String): Boolean {
        return try {
            val utteranceId = "train_alert_${System.currentTimeMillis()}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val params = Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                res == TextToSpeech.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                val params = HashMap<String, String>().apply {
                    put(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
                    put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                @Suppress("DEPRECATION")
                val res = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
                res == TextToSpeech.SUCCESS
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getTtsCacheInfo(): Pair<Int, Long> = networkTts.getCacheInfo()

    fun clearTtsCache(): Pair<Int, Long> = networkTts.clearCache()

    fun release() {
        playJob?.cancel()
        networkTts.release()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isTtsReady = false
    }

    companion object {

        /**
         * Converts digit strings into isolated single-digit Chinese characters directly
         * without intermediate spaces (e.g. "5033" -> "五零三三", "1000" -> "一零零零").
         */
        fun convertDigitsToSpoken(text: String): String {
            val sb = StringBuilder()
            for (ch in text) {
                when (ch) {
                    '0' -> sb.append("零")
                    '1' -> sb.append("一")
                    '2' -> sb.append("二")
                    '3' -> sb.append("三")
                    '4' -> sb.append("四")
                    '5' -> sb.append("五")
                    '6' -> sb.append("六")
                    '7' -> sb.append("七")
                    '8' -> sb.append("八")
                    '9' -> sb.append("九")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        /**
         * Transforms locomotive model and number according to railway pronunciation requirements:
         * - FXD1-J special case: "复兴电1集，xxxx"
         * - CR series: CR200/300/400 -> 二百/三百/四百, letters like AF/BF distinct
         * - Hyphen '-' separates model and number with a comma pause (e.g. "DF4D-1000" -> "东风4D，一零零零")
         * - Numbers are read directly digit by digit (e.g. "5033" -> "五零三三").
         */
        fun formatLocoForSpeech(locoModel: String): String {
            val clean = locoModel.trim().replace("----", "")
            if (clean.isBlank() || clean == "未知") return "未知"

            // Special Case: FXD1-J (复兴电1集)
            val fxd1jRegex = Regex("""^FXD1[-_]?J[-_]?(.*)$""", RegexOption.IGNORE_CASE)
            val fxd1jMatch = fxd1jRegex.matchEntire(clean)
            if (fxd1jMatch != null) {
                val numPart = fxd1jMatch.groupValues[1].trim()
                return if (numPart.isNotEmpty()) {
                    "复兴电1集，${convertDigitsToSpoken(numPart)}"
                } else {
                    "复兴电1集"
                }
            }

            // Special Case: CR / CRH Series (CR200, CR300, CR400, CRH380, etc.)
            if (clean.startsWith("CR", ignoreCase = true)) {
                val parts = clean.split('-')
                val modelParts = mutableListOf<String>()
                var numPart = ""

                if (parts.size >= 2 && parts.last().all { it.isDigit() }) {
                    numPart = parts.last()
                    modelParts.addAll(parts.dropLast(1))
                } else {
                    modelParts.addAll(parts)
                }

                val rawModel = modelParts.joinToString("-")
                var formattedModel = rawModel
                    .replace("CRH", "C R H ", ignoreCase = true)
                    .replace("CR", "C R ", ignoreCase = true)
                    .replace("400", "四百")
                    .replace("300", "三百")
                    .replace("200", "二百")
                    .replace("380", "三百八十")

                // Separate letters AF, BF, etc. so they are pronounced distinctly without liaison burst
                formattedModel = formattedModel
                    .replace("AF", "A F", ignoreCase = true)
                    .replace("BF", "B F", ignoreCase = true)
                    .replace("-", " ")

                formattedModel = formattedModel.trim().replace(Regex("\\s+"), " ")

                return if (numPart.isNotEmpty()) {
                    "$formattedModel，${convertDigitsToSpoken(numPart)}"
                } else {
                    formattedModel
                }
            }

            // Standard locomotive cases with hyphen (e.g. "DF4D-1000", "HXD3D-5033", "SS9G-0088", "HXN5-0001")
            if (clean.contains('-')) {
                val dashIdx = clean.lastIndexOf('-')
                val modelPartRaw = clean.substring(0, dashIdx).trim()
                val numPartRaw = clean.substring(dashIdx + 1).trim()

                val modelPart = formatLocoPrefix(modelPartRaw)
                val numPart = convertDigitsToSpoken(numPartRaw)
                return if (numPart.isNotEmpty()) {
                    "$modelPart，$numPart"
                } else {
                    modelPart
                }
            }

            // Standard locomotive without hyphen but with trailing digits (e.g. "DF4D1000", "HXD3D5033")
            val trailingDigitsRegex = Regex("""^([A-Za-z0-9\u4e00-\u9fa5]+?)(\d{4,})$""")
            val trailingMatch = trailingDigitsRegex.matchEntire(clean)
            if (trailingMatch != null) {
                val modelPart = formatLocoPrefix(trailingMatch.groupValues[1])
                val numPart = convertDigitsToSpoken(trailingMatch.groupValues[2])
                return "$modelPart，$numPart"
            }

            return formatLocoPrefix(clean)
        }

        private fun formatLocoPrefix(model: String): String {
            return model
                .replace("HXD", "和谐电")
                .replace("FXD", "复兴电")
                .replace("HXN", "和谐内")
                .replace("FXN", "复兴内")
                .replace("SS", "韶山")
                .replace("DF", "东风")
        }

        /**
         * Transforms train number according to railway pronunciation requirements:
         * G -> 高, D -> 动, C -> 城, S -> 市, Z -> 直, T -> 特, K -> 快, L -> 临, Y -> 游, X -> 行, DJ -> 动检
         * The numerical portion (e.g. 5033, 102) is read digit-by-digit without spaces ("五零三三", "一零二").
         */
        fun formatTrainNoForSpeech(rawTrainNo: String): String {
            val clean = rawTrainNo.replace(" ", "").trim().replace("----", "")
            if (clean.isBlank()) return "未知车次"

            val (prefixText, numPart) = when {
                clean.startsWith("DJ", ignoreCase = true) -> "动检" to clean.substring(2)
                clean.startsWith("CR", ignoreCase = true) -> "C R" to clean.substring(2)
                clean.startsWith("G", ignoreCase = true) -> "高" to clean.substring(1)
                clean.startsWith("D", ignoreCase = true) -> "动" to clean.substring(1)
                clean.startsWith("C", ignoreCase = true) -> "城" to clean.substring(1)
                clean.startsWith("S", ignoreCase = true) -> "市" to clean.substring(1)
                clean.startsWith("Z", ignoreCase = true) -> "直" to clean.substring(1)
                clean.startsWith("T", ignoreCase = true) -> "特" to clean.substring(1)
                clean.startsWith("K", ignoreCase = true) -> "快" to clean.substring(1)
                clean.startsWith("L", ignoreCase = true) -> "临" to clean.substring(1)
                clean.startsWith("Y", ignoreCase = true) -> "游" to clean.substring(1)
                clean.startsWith("X", ignoreCase = true) -> "行" to clean.substring(1)
                clean.startsWith("F", ignoreCase = true) -> "返" to clean.substring(1)
                else -> "" to clean
            }

            val spokenDigits = convertDigitsToSpoken(numPart)
            return if (prefixText.isNotEmpty()) {
                "$prefixText$spokenDigits"
            } else {
                spokenDigits
            }
        }

        /**
         * Formats route line and direction:
         * e.g., "京沪高铁下行", "陇海线上行", etc.
         */
        fun formatRouteForSpeech(route: String, direction: String): String {
            val cleanRoute = route.trim().replace("----", "").replace("未知", "")
            val cleanDir = direction.trim().replace("----", "").replace("未知", "")
            return when {
                cleanRoute.isNotEmpty() && cleanDir.isNotEmpty() -> {
                    if (cleanRoute.endsWith("线") || cleanRoute.endsWith("高铁") || cleanRoute.endsWith("客专") || cleanRoute.endsWith("城际") || cleanRoute.endsWith("铁路")) {
                        "$cleanRoute$cleanDir"
                    } else {
                        "${cleanRoute}线$cleanDir"
                    }
                }
                cleanRoute.isNotEmpty() -> cleanRoute
                cleanDir.isNotEmpty() -> cleanDir
                else -> ""
            }
        }

        /**
         * Builds complete spoken alert announcement sentence:
         * "有火车接近，机车为：xxx，xx线上/下行，速度：xx，车次：xxx"
         */
        fun buildTrainAlertSpeechText(
            locoModel: String,
            route: String,
            direction: String,
            speedKmH: String,
            trainNo: String
        ): String {
            val locoStr = formatLocoForSpeech(locoModel)
            val routeStr = formatRouteForSpeech(route, direction)
            val speedParsed = speedKmH.replace("km/h", "", ignoreCase = true).replace("KM/H", "").trim().toDoubleOrNull()?.toInt()?.coerceAtLeast(0)
            val speedStr = (speedParsed?.toString() ?: speedKmH.replace("km/h", "", ignoreCase = true).trim()).ifBlank { "0" }
            val trainStr = formatTrainNoForSpeech(trainNo)

            val routeClause = if (routeStr.isNotBlank()) "，$routeStr" else ""
            return "有火车接近，机车为：$locoStr$routeClause，速度：$speedStr，车次：$trainStr"
        }
    }
}
