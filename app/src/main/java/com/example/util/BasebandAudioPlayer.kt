package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.dsp.DspConstants
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * BasebandAudioPlayer streams demodulated baseband audio / IQ RF noise in real-time
 * to the Android device's media speaker (occupies STREAM_MUSIC / USAGE_MEDIA).
 *
 * When no carrier signal is present, it naturally produces the authentic analog radio
 * receiver static / hiss ("沙沙声"). When an LBJ FSK signal arrives, the 1200 baud modem
 * chirps can be heard over the speaker.
 */
class BasebandAudioPlayer(
    private val sampleRate: Int = DspConstants.BASEBAND_RATE
) {
    private val isPlaying = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    // Preallocated short buffers for zero-allocation queueing
    private val poolCapacity = 32
    private val bufferSize = DspConstants.BLOCK_SIZE / 20 // 3276 samples @ 48kHz (~68ms)
    private val bufferPool = ArrayBlockingQueue<ShortArray>(poolCapacity).apply {
        repeat(poolCapacity) { offer(ShortArray(bufferSize)) }
    }
    private val audioQueue = ArrayBlockingQueue<ShortArray>(poolCapacity)

    // Single-pole de-emphasis filter state (simulates analog receiver 75µs RC network)
    private var deemphState: Float = 0.0f
    private var volumeGain: Float = 0.15f // 50% volume equals 30% of initial baseline gain (0.15f)

    @Synchronized
    fun start() {
        if (isPlaying.get()) return
        isPlaying.set(true)
        audioQueue.clear()
        deemphState = 0.0f

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // ~350ms buffer for smooth playback even under high CPU load on older devices
            val trackBufSize = maxOf(minBufSize * 2, sampleRate / 3)

            val track = AudioTrack(
                audioAttributes,
                audioFormat,
                trackBufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                audioTrack = track

                audioThread = Thread({
                    try {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
                    } catch (_: Exception) {}

                    val trk = audioTrack
                    while (isPlaying.get() && trk != null) {
                        try {
                            val chunk = audioQueue.poll(80, TimeUnit.MILLISECONDS)
                            if (chunk != null) {
                                trk.write(chunk, 0, chunk.size)
                                bufferPool.offer(chunk)
                            }
                        } catch (_: InterruptedException) {
                            break
                        } catch (_: Exception) {
                            break
                        }
                    }
                }, "Baseband-Audio-Player").apply {
                    isDaemon = true
                    priority = Thread.MAX_PRIORITY
                    start()
                }
            } else {
                track.release()
                isPlaying.set(false)
            }
        } catch (_: Exception) {
            isPlaying.set(false)
        }
    }

    /**
     * Feed demodulated PCM float array (-1.0f .. 1.0f) from DSP frontend.
     * Zero-allocation, non-blocking.
     */
    fun writeSamples(pcm: FloatArray) {
        if (!isPlaying.get()) return
        val n = pcm.size
        if (n == 0) return

        val shortBuf = bufferPool.poll() ?: ShortArray(n)
        val targetSize = minOf(n, shortBuf.size)

        var dState = deemphState
        val gain = volumeGain

        // Fast de-emphasis + soft limiting (prevents clipping clicks)
        for (i in 0 until targetSize) {
            val raw = pcm[i]
            dState += 0.35f * (raw - dState) // RC ~ 75µs filter for warm analog radio hiss
            val saturated = (dState * gain).coerceIn(-0.95f, 0.95f)
            shortBuf[i] = (saturated * 32767.0f).toInt().toShort()
        }
        deemphState = dState

        if (!audioQueue.offer(shortBuf)) {
            // Drop oldest to avoid audio delay accumulation
            val old = audioQueue.poll()
            if (old != null) bufferPool.offer(old)
            audioQueue.offer(shortBuf)
        }
    }

    @Synchronized
    fun stop() {
        if (!isPlaying.get()) return
        isPlaying.set(false)
        audioThread?.interrupt()
        audioThread = null

        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        pause()
                        flush()
                        stop()
                    } catch (_: Exception) {}
                    release()
                }
            }
        } catch (_: Exception) {}
        audioTrack = null
        audioQueue.clear()
    }

    fun setVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        volumeGain = (clamped.toFloat() / 50.0f) * 0.15f
    }

    fun setVolumeGain(gain: Float) {
        volumeGain = gain.coerceIn(0.05f, 1.5f)
    }

    fun isRunning(): Boolean = isPlaying.get()
}
