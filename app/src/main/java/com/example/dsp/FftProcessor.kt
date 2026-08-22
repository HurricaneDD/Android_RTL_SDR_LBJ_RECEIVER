package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/**
 * Ultra-fast Radix-2 FFT Processor with precomputed Twiddle and Bit-Reversal Tables.
 * Zero-allocations during process execution for maximum performance on older Android CPUs.
 */
class FftProcessor(
    val fftSize: Int = 512,
    val numBands: Int = 32
) {
    private val hanningWindow = FloatArray(fftSize) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat()
    }
    private val invScaleSq: Double

    // Precomputed Bit-Reversal and Twiddle tables
    private val bitRev = IntArray(fftSize)
    private val cosTwiddle = FloatArray(fftSize / 2)
    private val sinTwiddle = FloatArray(fftSize / 2)

    private val realBuf = FloatArray(fftSize)
    private val imagBuf = FloatArray(fftSize)
    private val fullDbBuf = FloatArray(fftSize)
    val smoothedBars = FloatArray(numBands) { -120.0f }
    private val bandsOutput = FloatArray(numBands)

    init {
        var sum = 0.0
        for (w in hanningWindow) sum += w
        val winMean = (sum / fftSize).toFloat()
        val scale = (fftSize * winMean).toDouble()
        invScaleSq = 1.0 / (scale * scale)

        // Precompute bit reversal permutation
        var j = 0
        for (i in 0 until fftSize) {
            bitRev[i] = j
            var k = fftSize shr 1
            while (k <= j && k > 0) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Precompute twiddle factors: W_N^k = e^(-2*pi*j*k/N)
        for (k in 0 until fftSize / 2) {
            val angle = -2.0 * PI * k / fftSize
            cosTwiddle[k] = cos(angle).toFloat()
            sinTwiddle[k] = sin(angle).toFloat()
        }
    }

    data class PeakInfo(
        var peakFreqHz: Double? = null,
        var peakDeltaHz: Double? = null,
        var peakDb: Float? = null
    )

    data class FftResult(
        val bandsDb: FloatArray,
        val fullSpectrumDb: FloatArray,
        val peakInfo: PeakInfo
    )

    private val cachedPeakInfo = PeakInfo()
    private val cachedResult = FftResult(bandsOutput, fullDbBuf, cachedPeakInfo)

    /**
     * Compute FFT on the first `fftSize` complex samples.
     */
    fun process(
        iqBuffer: ComplexBuffer,
        sampleRate: Double,
        hwFreqHz: Double,
        targetFreqHz: Double,
        bwKhz: Double
    ): FftResult {
        val n = fftSize
        if (iqBuffer.size < n) {
            return cachedResult
        }

        // 1. Apply Hanning window with bit-reversal reordering directly
        val real = realBuf
        val imag = imagBuf
        val inR = iqBuffer.real
        val inI = iqBuffer.imag
        val w = hanningWindow
        val brev = bitRev

        for (i in 0 until n) {
            val src = brev[i]
            val win = w[src]
            real[i] = inR[src] * win
            imag[i] = inI[src] * win
        }

        // 2. Perform Cooley-Tukey Radix-2 FFT using precomputed Twiddle factors
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val stepTwiddle = n / len

            var i = 0
            while (i < n) {
                for (k in 0 until halfLen) {
                    val twIdx = k * stepTwiddle
                    val wR = cosTwiddle[twIdx]
                    val wI = sinTwiddle[twIdx]

                    val idx1 = i + k
                    val idx2 = idx1 + halfLen

                    val vR2 = real[idx2]
                    val vI2 = imag[idx2]
                    val vR = vR2 * wR - vI2 * wI
                    val vI = vR2 * wI + vI2 * wR

                    val uR = real[idx1]
                    val uI = imag[idx1]

                    real[idx1] = uR + vR
                    imag[idx1] = uI + vI
                    real[idx2] = uR - vR
                    imag[idx2] = uI - vI
                }
                i += len
            }
            len = len shl 1
        }

        // 3. FFT Shift and linear power calculation: pwr = (r*r + im*im) * invScaleSq
        val half = n shr 1

        // 4. Pool into numBands in linear power domain first (saves 480 log10 calls per block)
        val chunkSize = n / numBands
        for (b in 0 until numBands) {
            var maxPwr = 1e-12
            val start = b * chunkSize
            val end = start + chunkSize
            for (k in start until end) {
                val srcIdx = if (k < half) k + half else k - half
                val r = real[srcIdx]
                val im = imag[srcIdx]
                val pwr = (r * r + im * im).toDouble() * invScaleSq
                if (pwr > maxPwr) maxPwr = pwr
            }
            val bandDb = (10.0 * log10(maxPwr)).toFloat()
            val smoothed = 0.65f * smoothedBars[b] + 0.35f * bandDb
            smoothedBars[b] = smoothed
            bandsOutput[b] = smoothed
        }

        // 5. Peak detector within target channel bandwidth in linear domain
        val targetOffsetHz = targetFreqHz - hwFreqHz
        val targetBwHz = max(1000.0, bwKhz * 1000.0)
        val minOffset = targetOffsetHz - targetBwHz * 0.5
        val maxOffset = targetOffsetHz + targetBwHz * 0.5

        var peakIdx = -1
        var peakPwr = 1e-12
        val freqPerBin = sampleRate / n
        val minBin = ((minOffset / freqPerBin) + half).toInt().coerceIn(0, n - 1)
        val maxBin = ((maxOffset / freqPerBin) + half).toInt().coerceIn(0, n - 1)

        for (i in minBin..maxBin) {
            val srcIdx = if (i < half) i + half else i - half
            val r = real[srcIdx]
            val im = imag[srcIdx]
            val pwr = (r * r + im * im).toDouble() * invScaleSq
            if (pwr > peakPwr) {
                peakPwr = pwr
                peakIdx = i
            }
        }

        if (peakIdx >= 0) {
            val peakFreqOffset = (peakIdx - half) * freqPerBin
            val peakFreq = hwFreqHz + peakFreqOffset
            cachedPeakInfo.peakFreqHz = peakFreq
            cachedPeakInfo.peakDeltaHz = peakFreq - targetFreqHz
            cachedPeakInfo.peakDb = (10.0 * log10(peakPwr)).toFloat()
        } else {
            cachedPeakInfo.peakFreqHz = null
            cachedPeakInfo.peakDeltaHz = null
            cachedPeakInfo.peakDb = null
        }

        return cachedResult
    }
}
