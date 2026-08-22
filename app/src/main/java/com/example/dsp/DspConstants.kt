package com.example.dsp

object DspConstants {
    const val BASEBAND_RATE = 48000
    const val BAUD_RATE = 1200
    const val RTL_SAMPLE_RATE = 960000
    const val HALFBAND_STAGES = 2
    const val MID_RATE = 240000
    const val BLOCK_SIZE = 65536
    const val DEFAULT_FREQ_HZ = 821237500.0 // 821.2375 MHz
    const val DEFAULT_DC_OFFSET_HZ = 50000.0 // +50 kHz
    const val DEFAULT_BW_KHZ = 35.0
    const val DEFAULT_AFC_MAX_HZ = 8000.0
    const val DEFAULT_RSSI_THRESHOLD_DB = -55.0f
    const val DEFAULT_RSSI_HYST_DB = 4.0f
    const val DEFAULT_RSSI_HOLD_MS = 700.0f
    const val DEFAULT_ETA_MAX_SECONDS = 6 * 3600 // 6 hours
    const val HW_GAIN_DB = 15.7f
    const val PPM = 1

    val R820T_GAINS = floatArrayOf(
        0.0f, 0.9f, 1.4f, 2.7f, 3.7f, 7.7f, 8.7f, 12.5f, 14.4f, 15.7f, 16.6f, 19.7f, 20.7f,
        22.9f, 25.4f, 28.0f, 29.7f, 32.8f, 33.8f, 36.4f, 37.2f, 38.6f, 40.2f, 42.1f, 43.4f,
        43.9f, 44.5f, 48.0f, 49.6f
    )

    const val CMD_SET_FREQ = 1
    const val CMD_SET_SAMPLERATE = 2
    const val CMD_SET_GAINMODE = 3
    const val CMD_SET_GAIN = 4
    const val CMD_SET_FREQCORR = 5
    const val CMD_SET_AGC = 8

    const val SYNC_STD = 0x7CD215D8L // 2094142936L
    const val SYNC_INV = 0x832DEA27L // 2200824359L
    const val IDLE_WORD = 0x7A89C197L // 2055848343L
}
