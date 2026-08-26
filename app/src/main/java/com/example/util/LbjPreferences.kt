package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.dsp.DspConstants

class LbjPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sdr_lbj_app_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALERT_TONE = "pref_alert_tone_enabled"
        private const val KEY_KEEP_ALIVE = "pref_keep_alive_enabled"
        private const val KEY_BROADCAST_ALERTS = "pref_broadcast_alerts"
        private const val KEY_STRICT_FILTER = "pref_strict_filter"
        private const val KEY_SHOW_ERR_WARN = "pref_show_err_warn"
        private const val KEY_FILTER_MODE = "pref_filter_mode"
        private const val KEY_KEYWORDS = "pref_keywords"
        private const val KEY_FREQ_HZ = "pref_freq_hz"
        private const val KEY_GAIN_DB = "pref_gain_db"
        private const val KEY_PPM = "pref_ppm"
        private const val KEY_CS_THRESHOLD_DB = "pref_cs_threshold_db"
        private const val KEY_SHOW_SIMULATION_BTN = "pref_show_simulation_button"
        private const val KEY_TTS_ENGINE_MODE = "pref_tts_engine_mode"
        private const val KEY_ENABLE_EXTERNAL_AUTOMATION = "pref_enable_external_automation"
        private const val KEY_ALERT_NOTIFICATION = "pref_alert_notification_enabled"
    }

    var alertNotificationEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERT_NOTIFICATION, false)
        set(value) = prefs.edit().putBoolean(KEY_ALERT_NOTIFICATION, value).apply()

    var ttsEngineMode: String
        get() = prefs.getString(KEY_TTS_ENGINE_MODE, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_TTS_ENGINE_MODE, value).apply()

    var enableExternalAutomation: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_EXTERNAL_AUTOMATION, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_EXTERNAL_AUTOMATION, value).apply()

    var alertToneEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERT_TONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALERT_TONE, value).apply()

    var keepAliveEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ALIVE, value).apply()

    var broadcastAlerts: Boolean
        get() = prefs.getBoolean(KEY_BROADCAST_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_BROADCAST_ALERTS, value).apply()

    var strictFilter: Boolean
        get() = prefs.getBoolean(KEY_STRICT_FILTER, true)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_FILTER, value).apply()

    var showErrWarn: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ERR_WARN, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_ERR_WARN, value).apply()

    var filterMode: String
        get() = prefs.getString(KEY_FILTER_MODE, "highlight") ?: "highlight"
        set(value) = prefs.edit().putString(KEY_FILTER_MODE, value).apply()

    var keywords: List<String>
        get() {
            val raw = prefs.getString(KEY_KEYWORDS, "") ?: ""
            return if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        set(value) {
            val raw = value.joinToString(",")
            prefs.edit().putString(KEY_KEYWORDS, raw).apply()
        }

    var freqHz: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_FREQ_HZ, java.lang.Double.doubleToRawLongBits(DspConstants.DEFAULT_FREQ_HZ))
        )
        set(value) = prefs.edit().putLong(KEY_FREQ_HZ, java.lang.Double.doubleToRawLongBits(value)).apply()

    var gainDb: Float
        get() = prefs.getFloat(KEY_GAIN_DB, DspConstants.HW_GAIN_DB)
        set(value) = prefs.edit().putFloat(KEY_GAIN_DB, value).apply()

    var ppm: Int
        get() = prefs.getInt(KEY_PPM, DspConstants.PPM)
        set(value) = prefs.edit().putInt(KEY_PPM, value).apply()

    var csThresholdDb: Float
        get() = prefs.getFloat(KEY_CS_THRESHOLD_DB, DspConstants.DEFAULT_RSSI_THRESHOLD_DB)
        set(value) = prefs.edit().putFloat(KEY_CS_THRESHOLD_DB, value).apply()

    var showSimulationButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SIMULATION_BTN, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SIMULATION_BTN, value).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
