package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Complex buffer representation with real and imaginary float arrays.
 */
class ComplexBuffer(val size: Int) {
    val real: FloatArray = FloatArray(size)
    val imag: FloatArray = FloatArray(size)
}

/**
 * _D0: IQ DC offset correction and IQ amplitude gain balance.
 */
class IqCorrection {
    private var dcI: Float = 0.0f
    private var dcQ: Float = 0.0f
    private var gr: Float = 1.0f

    fun process(buffer: ComplexBuffer) {
        val n = buffer.size
        if (n == 0) return
        var sumI = 0.0
        var sumQ = 0.0
        for (k in 0 until n) {
            sumI += buffer.real[k]
            sumQ += buffer.imag[k]
        }
        val meanI = (sumI / n).toFloat()
        val meanQ = (sumQ / n).toFloat()

        dcI += 0.001f * (meanI - dcI)
        dcQ += 0.001f * (meanQ - dcQ)

        var sumII = 0.0
        var sumQQ = 0.0
        for (k in 0 until n) {
            val iVal = buffer.real[k] - dcI
            val qVal = buffer.imag[k] - dcQ
            buffer.real[k] = iVal
            buffer.imag[k] = qVal
            sumII += (iVal * iVal)
            sumQQ += (qVal * qVal)
        }

        val pi = (sumII / n).toFloat()
        val pq = (sumQQ / n).toFloat()
        if (pi > 1e-20f) {
            val targetGr = sqrt(pq / pi)
            gr += 0.0001f * (targetGr - gr)
            gr = gr.coerceIn(0.9f, 1.1f)
            for (k in 0 until n) {
                buffer.imag[k] *= gr
            }
        }
    }
}

/**
 * _D1: Ultra-fast Direct Digital Synthesis (DDS) Digital Down Converter.
 * Uses 4096-entry sine/cosine lookup tables and 32-bit fixed point phase accumulator.
 */
class Ddc(val sampleRate: Double, var offsetHz: Double = 0.0) {
    companion object {
        private const val TABLE_SIZE = 4096
        private const val TABLE_MASK = TABLE_SIZE - 1
        private val sinTable = FloatArray(TABLE_SIZE) { i -> sin(2.0 * PI * i / TABLE_SIZE).toFloat() }
        private val cosTable = FloatArray(TABLE_SIZE) { i -> cos(2.0 * PI * i / TABLE_SIZE).toFloat() }
    }

    private var phaseAcc: Long = 0L
    private var phaseStep: Long = 0L

    init {
        updateStep()
    }

    private fun updateStep() {
        val normalized = (-offsetHz / sampleRate)
        phaseStep = (normalized * 4294967296.0).toLong()
    }

    fun setOffset(newOffsetHz: Double) {
        offsetHz = newOffsetHz
        updateStep()
    }

    fun addOffset(deltaHz: Double) {
        setOffset(offsetHz + deltaHz)
    }

    fun process(buffer: ComplexBuffer) {
        if (kotlin.math.abs(offsetHz) < 0.1) return
        val n = buffer.size
        var ph = phaseAcc
        val step = phaseStep

        val real = buffer.real
        val imag = buffer.imag
        val sTab = sinTable
        val cTab = cosTable

        for (k in 0 until n) {
            val idx = ((ph ushr 20).toInt()) and TABLE_MASK
            val cosV = cTab[idx]
            val sinV = sTab[idx]
            val i = real[k]
            val q = imag[k]

            // (i + j*q) * (cos + j*sin) = (i*cos - q*sin) + j*(i*sin + q*cos)
            real[k] = i * cosV - q * sinV
            imag[k] = i * sinV + q * cosV

            ph += step
        }
        phaseAcc = ph
    }
}

/**
 * High-performance polyphase Halfband Decimator (decimate by 2).
 * Evaluates only non-zero symmetric filter taps directly at downsampled indices.
 */
class HalfbandDecimator(numTaps: Int = 31) {
    private val h = FirDesign.halfband(numTaps)
    private val nt = h.size
    private val centerTap = (nt - 1) / 2
    private val centerCoeff = h[centerTap]
    private val prevTailReal = FloatArray(nt)
    private val prevTailImag = FloatArray(nt)
    private var hasHistory = false

    fun process(input: ComplexBuffer, output: ComplexBuffer) {
        val inLen = input.size
        val outLen = inLen / 2
        val inR = input.real
        val inI = input.imag
        val outR = output.real
        val outI = output.imag

        // Combine history + input seamlessly
        for (k in 0 until outLen) {
            val inIdx = k * 2
            var sumR = 0.0f
            var sumI = 0.0f

            for (t in 0 until nt) {
                val coeff = h[t]
                if (coeff == 0.0f) continue
                val tapPos = inIdx - (nt - 1) + t
                val r: Float
                val i: Float
                if (tapPos < 0) {
                    val hIdx = nt + tapPos
                    r = if (hasHistory && hIdx in 0 until nt) prevTailReal[hIdx] else 0.0f
                    i = if (hasHistory && hIdx in 0 until nt) prevTailImag[hIdx] else 0.0f
                } else {
                    r = inR[tapPos]
                    i = inI[tapPos]
                }
                sumR += coeff * r
                sumI += coeff * i
            }

            outR[k] = sumR
            outI[k] = sumI
        }

        // Save tail for next block
        val tailStart = max(0, inLen - nt)
        val tailLen = inLen - tailStart
        System.arraycopy(inR, tailStart, prevTailReal, nt - tailLen, tailLen)
        System.arraycopy(inI, tailStart, prevTailImag, nt - tailLen, tailLen)
        hasHistory = true
    }
}

/**
 * High-performance Decimating Lowpass Channel FIR Filter (240 kHz -> 48 kHz, factor of 5).
 * Only computes convolution at output points (5k), reducing computation by 80x.
 */
class DecimatingChannelFilter(
    srIn: Double = DspConstants.MID_RATE.toDouble(),
    srOut: Double = DspConstants.BASEBAND_RATE.toDouble(),
    cutoffHz: Double = 17500.0,
    numTaps: Int = 31
) {
    val decimation: Int = (srIn / srOut).toInt()
    private val h = FirDesign.firwinLowPass(numTaps, cutoffHz, srIn)
    private val nt = h.size
    private val prevTailReal = FloatArray(nt)
    private val prevTailImag = FloatArray(nt)
    private var hasHistory = false

    fun process(input: ComplexBuffer, output: ComplexBuffer) {
        val inLen = input.size
        val outLen = inLen / decimation
        val inR = input.real
        val inI = input.imag
        val outR = output.real
        val outI = output.imag

        for (k in 0 until outLen) {
            val inIdx = k * decimation
            var sumR = 0.0f
            var sumI = 0.0f

            for (t in 0 until nt) {
                val coeff = h[t]
                val tapPos = inIdx - (nt - 1) + t
                val r: Float
                val i: Float
                if (tapPos < 0) {
                    val hIdx = nt + tapPos
                    r = if (hasHistory && hIdx in 0 until nt) prevTailReal[hIdx] else 0.0f
                    i = if (hasHistory && hIdx in 0 until nt) prevTailImag[hIdx] else 0.0f
                } else {
                    r = inR[tapPos]
                    i = inI[tapPos]
                }
                sumR += coeff * r
                sumI += coeff * i
            }

            outR[k] = sumR
            outI[k] = sumI
        }

        val tailStart = max(0, inLen - nt)
        val tailLen = inLen - tailStart
        System.arraycopy(inR, tailStart, prevTailReal, nt - tailLen, tailLen)
        System.arraycopy(inI, tailStart, prevTailImag, nt - tailLen, tailLen)
        hasHistory = true
    }
}

/**
 * _D5: Fast FM Quadrature Demodulator with fast rational atan2 approximation.
 * y[n] = angle(x[n] * conj(x[n-1])) / PI
 */
class FmDemodulator {
    private var prevI: Float = 0.0f
    private var prevQ: Float = 0.0f

    companion object {
        private const val INV_PI = (1.0 / PI).toFloat()

        // Fast polynomial atan2 with max error < 0.005 radians, 10x faster than Math.atan2
        fun fastAtan2(y: Float, x: Float): Float {
            if (x == 0.0f && y == 0.0f) return 0.0f
            val ax = if (x < 0) -x else x
            val ay = if (y < 0) -y else y
            val a = if (ax < ay) ax / ay else ay / ax
            val s = a * a
            var r = ((-0.0464964749f * s + 0.15931422f) * s - 0.327622764f) * s * a + a
            if (ay > ax) r = 1.57079632679f - r
            if (x < 0) r = 3.14159265359f - r
            if (y < 0) r = -r
            return r
        }
    }

    fun process(input: ComplexBuffer, out: FloatArray) {
        val n = input.size
        var pI = prevI
        var pQ = prevQ
        val inR = input.real
        val inI = input.imag

        for (k in 0 until n) {
            val curI = inR[k]
            val curQ = inI[k]
            val dReal = curI * pI + curQ * pQ
            val dImag = curQ * pI - curI * pQ
            out[k] = fastAtan2(dImag, dReal) * INV_PI

            pI = curI
            pQ = curQ
        }
        prevI = pI
        prevQ = pQ
    }
}

/**
 * _A1: RSSI Squelch Gate with hysteresis and hold timer.
 */
class RssiGate(
    var onDb: Float = DspConstants.DEFAULT_RSSI_THRESHOLD_DB,
    var hysteresisDb: Float = DspConstants.DEFAULT_RSSI_HYST_DB,
    var holdMs: Float = DspConstants.DEFAULT_RSSI_HOLD_MS,
    var confirmBlocks: Int = 1,
    var enabled: Boolean = true
) {
    var offDb: Float = onDb - hysteresisDb
        private set
    var active: Boolean = false
        private set
    var holdLeftMs: Float = 0.0f
        private set
    private var onCount: Int = 0
    var justActivated: Boolean = false
        private set
    var justDeactivated: Boolean = false
        private set
    var state: String = if (!enabled) "BYPASS" else "OFF"
        private set

    fun setThreshold(newOnDb: Float) {
        onDb = newOnDb
        offDb = onDb - hysteresisDb
    }

    fun reset() {
        active = false
        holdLeftMs = 0.0f
        onCount = 0
        justActivated = false
        justDeactivated = false
        state = if (!enabled) "BYPASS" else "OFF"
    }

    fun update(rssiDb: Float, audioLen: Int, fsAudio: Int = DspConstants.BASEBAND_RATE): Boolean {
        justActivated = false
        justDeactivated = false
        if (!enabled) {
            active = true
            holdLeftMs = holdMs
            state = "BYPASS"
            return true
        }

        val blockMs = (1000.0f * max(0, audioLen)) / fsAudio.toFloat()
        val wasActive = active

        if (!active) {
            if (rssiDb >= onDb) {
                onCount++
                if (onCount >= confirmBlocks) {
                    active = true
                    holdLeftMs = holdMs
                }
            } else {
                onCount = 0
            }
        } else if (rssiDb >= offDb) {
            holdLeftMs = holdMs
            onCount = confirmBlocks
        } else {
            holdLeftMs -= blockMs
            if (holdLeftMs <= 0.0f) {
                active = false
                holdLeftMs = 0.0f
                onCount = 0
            }
        }

        justActivated = !wasActive && active
        justDeactivated = wasActive && !active

        state = when {
            active -> if (rssiDb >= offDb) "ON" else "HOLD"
            onCount > 0 -> "ARM"
            else -> "OFF"
        }
        return active
    }
}

/**
 * _D6: Automatic Frequency Control (AFC) on demodulated FM audio.
 */
class Afc(
    val fs: Double = DspConstants.BASEBAND_RATE.toDouble(),
    val baseDdcHz: Double = DspConstants.DEFAULT_DC_OFFSET_HZ,
    var maxAfcHz: Double = DspConstants.DEFAULT_AFC_MAX_HZ,
    var loopGain: Double = 0.45,
    var maxStepHz: Double = 350.0,
    var minAltScore: Double = 0.25
) {
    var afcHz: Double = 0.0
    var dcNorm: Double = 0.0
    var lastErrHz: Double = 0.0
    var lastScore: Double = 0.0
    var updated: Boolean = false
    var enabled: Boolean = true

    private fun robustMean(x: FloatArray): Double {
        val n = x.size
        if (n == 0) return 0.0
        var mean = 0.0
        for (i in 0 until n) mean += x[i]
        mean /= n
        var devSum = 0.0
        for (i in 0 until n) {
            val d = x[i] - mean
            devSum += d * d
        }
        val std = sqrt(devSum / n)
        val cutoff = 1.6 * std
        var count = 0
        var robustSum = 0.0
        for (i in 0 until n) {
            if (kotlin.math.abs(x[i] - mean) <= cutoff) {
                robustSum += x[i]
                count++
            }
        }
        return if (count > 0) robustSum / count else mean
    }

    fun process(y: FloatArray, ddc: Ddc, updateAllowed: Boolean = true): FloatArray {
        updated = false
        if (!enabled) return y
        val n = y.size
        if (n < 160) {
            val dc = dcNorm.toFloat()
            for (i in 0 until n) y[i] -= dc
            return y
        }

        if (!updateAllowed) {
            lastScore = 0.0
            lastErrHz = dcNorm * fs * 0.5
            val dc = dcNorm.toFloat()
            for (i in 0 until n) y[i] -= dc
            return y
        }

        var dcNow = robustMean(y)
        val maxNorm = 2.0 * maxAfcHz / fs
        dcNow = dcNow.coerceIn(-maxNorm, maxNorm)

        val spb = max(1, (fs / DspConstants.BAUD_RATE).toInt())
        val score = if (n > spb) {
            var pwr = 1e-12
            var autoCorr = 0.0
            val dcF = dcNow.toFloat()
            for (i in 0 until n - spb) {
                val y1 = y[i] - dcF
                val y2 = y[i + spb] - dcF
                autoCorr += y1 * y2
                pwr += y1 * y1
            }
            -autoCorr / pwr
        } else {
            0.0
        }
        lastScore = score

        val alphaDc = if (score >= minAltScore) 1.0 else 0.01
        dcNorm += alphaDc * (dcNow - dcNorm)
        dcNorm = dcNorm.coerceIn(-maxNorm, maxNorm)

        if (score >= minAltScore) {
            var fErrHz = dcNow * fs * 0.5
            fErrHz = fErrHz.coerceIn(-maxAfcHz, maxAfcHz)
            lastErrHz = fErrHz
            var step = loopGain * fErrHz
            step = step.coerceIn(-maxStepHz, maxStepHz)
            val oldAfc = afcHz
            afcHz = (afcHz + step).coerceIn(-maxAfcHz, maxAfcHz)
            if (kotlin.math.abs(afcHz - oldAfc) >= 5.0) {
                ddc.setOffset(baseDdcHz + afcHz)
                updated = true
            }
        } else {
            lastErrHz = dcNorm * fs * 0.5
        }

        val dc = dcNorm.toFloat()
        for (i in 0 until n) y[i] -= dc
        return y
    }

    fun reset(ddc: Ddc) {
        afcHz = 0.0
        dcNorm = 0.0
        lastErrHz = 0.0
        lastScore = 0.0
        updated = false
        ddc.setOffset(baseDdcHz)
    }
}

/**
 * _D7: Complete Zero-Allocation High-Performance DSP Frontend chain.
 * IQ Correction -> Fast DDS DDC -> Halfband 1 -> Halfband 2 -> Decimating FIR -> FM Demod -> RSSI -> AFC
 */
class DspFrontend(
    val sampleRate: Double = DspConstants.RTL_SAMPLE_RATE.toDouble(),
    val halfbandStages: Int = DspConstants.HALFBAND_STAGES,
    val midRate: Double = DspConstants.MID_RATE.toDouble(),
    val dcOffset: Double = DspConstants.DEFAULT_DC_OFFSET_HZ,
    val userOffset: Double = 0.0,
    val bwHz: Double = DspConstants.DEFAULT_BW_KHZ * 1000.0,
    var rssiOffset: Float = 0.0f,
    afcEnable: Boolean = true,
    afcMaxHz: Double = DspConstants.DEFAULT_AFC_MAX_HZ,
    afcGain: Double = 0.45
) {
    val baseDdc: Double = dcOffset + userOffset
    val iqCorr = IqCorrection()
    val ddc = Ddc(sampleRate, baseDdc)

    private val hb1 = HalfbandDecimator(31)
    private val hb2 = HalfbandDecimator(31)
    private val decChannelFilter = DecimatingChannelFilter(midRate, DspConstants.BASEBAND_RATE.toDouble(), cutoffHz = 17500.0, numTaps = 31)
    private val fmDemod = FmDemodulator()
    val afc = Afc(DspConstants.BASEBAND_RATE.toDouble(), baseDdc, maxAfcHz = afcMaxHz, loopGain = afcGain)

    // Preallocated buffers for zero-GC operation
    private val hb1Buf = ComplexBuffer(DspConstants.BLOCK_SIZE / 2)
    private val hb2Buf = ComplexBuffer(DspConstants.BLOCK_SIZE / 4)
    private val basebandBuf = ComplexBuffer(DspConstants.BLOCK_SIZE / 20)
    private val pcmBuf = FloatArray(DspConstants.BLOCK_SIZE / 20)

    init {
        afc.enabled = afcEnable
    }

    data class ProcessResult(
        val pcmFloat: FloatArray,
        val rssiDb: Float,
        val rxActive: Boolean
    )

    fun process(iqBuffer: ComplexBuffer, rssiGate: RssiGate? = null): ProcessResult {
        iqCorr.process(iqBuffer)
        ddc.process(iqBuffer)

        // Stage 1: 960k -> 480k
        hb1.process(iqBuffer, hb1Buf)

        // Stage 2: 480k -> 240k
        hb2.process(hb1Buf, hb2Buf)

        // Stage 3: 240k -> 48k (decimate by 5 with 31-tap bandpass filter)
        decChannelFilter.process(hb2Buf, basebandBuf)

        // RSSI Calculation on baseband complex samples
        var pwrSum = 0.0
        val n = basebandBuf.size
        val bbR = basebandBuf.real
        val bbI = basebandBuf.imag
        for (k in 0 until n) {
            val r = bbR[k]
            val i = bbI[k]
            pwrSum += (r * r + i * i)
        }
        val avgPwr = (pwrSum / max(1, n)).toFloat()
        val rssi = (10.0 * log10(max(1e-12f, avgPwr).toDouble()) + rssiOffset).toFloat()

        // FM Demodulation
        fmDemod.process(basebandBuf, pcmBuf)

        var rxActive = true
        if (rssiGate != null) {
            rxActive = rssiGate.update(rssi, n, DspConstants.BASEBAND_RATE)
        }
        val y = afc.process(pcmBuf, ddc, updateAllowed = rxActive)

        return ProcessResult(y, rssi, rxActive)
    }

    fun resetAfc() {
        afc.reset(ddc)
    }

    fun consumeAfcUpdated(): Boolean {
        val was = afc.updated
        afc.updated = false
        return was
    }
}
