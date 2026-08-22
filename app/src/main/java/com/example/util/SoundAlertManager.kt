package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sin

/**
 * SoundAlertManager generates and plays a crisp 2-second "滴滴" (Di-Di) alert tone
 * when incoming train telemetry is decoded or train number changes.
 */
class SoundAlertManager(private val scope: CoroutineScope) {

    private var playJob: Job? = null
    private val sampleRate = 16000

    // Pre-calculated 2.0-second PCM 16-bit byte buffer containing pulsed beeps (滴-滴-滴...)
    private val toneBytes: ByteArray by lazy {
        val totalSamples = (sampleRate * 2.0).toInt() // 2 seconds
        val pcm = ShortArray(totalSamples)
        val beepSamples = (sampleRate * 0.12).toInt() // 120ms beep
        val silenceSamples = (sampleRate * 0.08).toInt() // 80ms silence
        val cycleSamples = beepSamples + silenceSamples
        val freq = 1200.0 // 1.2 kHz crisp railway alert pitch

        for (i in 0 until totalSamples) {
            val inCycle = i % cycleSamples
            if (inCycle < beepSamples) {
                // Smooth linear attack and decay envelope to prevent popping sounds
                val attack = 80
                val gain = when {
                    inCycle < attack -> inCycle.toDouble() / attack
                    beepSamples - inCycle < attack -> (beepSamples - inCycle).toDouble() / attack
                    else -> 1.0
                }
                val sampleVal = (sin(2.0 * Math.PI * freq * i / sampleRate) * 0.85 * gain * Short.MAX_VALUE).toInt()
                pcm[i] = sampleVal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            } else {
                pcm[i] = 0
            }
        }
        val bytes = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val s = pcm[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        bytes
    }

    /**
     * Plays the 2-second "滴滴" alert sound.
     */
    fun playAlertTone() {
        playJob?.cancel()
        playJob = scope.launch(Dispatchers.IO) {
            var audioTrack: AudioTrack? = null
            try {
                audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioTrack(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                        toneBytes.size,
                        AudioTrack.MODE_STATIC,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        toneBytes.size,
                        AudioTrack.MODE_STATIC
                    )
                }

                audioTrack.write(toneBytes, 0, toneBytes.size)
                audioTrack.play()
                delay(2050)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            } catch (_: Exception) {
                // Fallback to ToneGenerator if AudioTrack encounters device restriction
                try {
                    val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 2000)
                    delay(2050)
                    tg.release()
                } catch (_: Exception) {}
            } finally {
                try {
                    audioTrack?.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun release() {
        playJob?.cancel()
    }
}
