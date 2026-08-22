package com.example.dsp

import kotlin.math.max
import kotlin.math.min

/**
 * DPLL Clock Recovery & Bit Slicer matching _d8 in Python.
 * Takes 48kHz FM demodulated audio, applies Gaussian smoothing FIR,
 * and recovers 1200 baud digital bitstream.
 */
class BitSlicer(
    val fs: Int = DspConstants.BASEBAND_RATE,
    val baudRate: Int = DspConstants.BAUD_RATE
) {
    private val ncoStep: Double = baudRate.toDouble() / fs.toDouble() // 1200 / 48000 = 0.025
    private var ncoPhase: Double = 0.0
    private var pllIntegrator: Double = 0.0
    private var peakMax: Float = 0.0f
    private var peakMin: Float = 0.0f
    private var lastHardBit: Int = 0
    private var lastBitClk: Int = 0

    // Gaussian smoothing FIR (31 taps)
    private val bSmooth = FirDesign.gaussianSmoother(31, 1200.0, fs.toDouble(), 7.0)
    private val nt = bSmooth.size
    private val ziSmooth = FloatArray(max(0, nt - 1))

    // Preallocated buffers
    private var bbBuf = FloatArray(DspConstants.BLOCK_SIZE / 20)
    private var bitBuffer = IntArray(DspConstants.BLOCK_SIZE / 10)

    fun resetDpllSoft() {
        ncoPhase = 0.0
        pllIntegrator = 0.0
        peakMax = 0.0f
        peakMin = 0.0f
        lastHardBit = 0
        lastBitClk = 0
    }

    fun reset() {
        resetDpllSoft()
        ziSmooth.fill(0.0f)
    }

    /**
     * Process an audio chunk (pcmFloat) and return extracted bits (0 or 1).
     */
    fun process(audioChunk: FloatArray): IntArray {
        val n = audioChunk.size
        if (n == 0) return IntArray(0)

        if (bbBuf.size < n) {
            bbBuf = FloatArray(n)
        }
        val bb = bbBuf

        // 1. Direct Form II transposed FIR smoothing (Gaussian)
        val nz = nt - 1
        val b0 = bSmooth[0]
        for (i in 0 until n) {
            val xi = audioChunk[i]
            val yi = if (nz > 0) b0 * xi + ziSmooth[0] else b0 * xi
            for (j in 0 until nz - 1) {
                ziSmooth[j] = bSmooth[j + 1] * xi + ziSmooth[j + 1]
            }
            if (nz > 0) {
                ziSmooth[nz - 1] = bSmooth[nz] * xi
            }
            bb[i] = yi
        }

        // 2. DPLL Bit slicing (_d8)
        if (bitBuffer.size < n * 2) {
            bitBuffer = IntArray(n * 2)
        }
        val outBits = bitBuffer
        var bitCount = 0

        var ph = ncoPhase
        var pllInt = pllIntegrator
        var mx = peakMax
        var mn = peakMin
        var lh = lastHardBit
        var lc = lastBitClk

        val maxDelta = ncoStep * 0.02
        val step = ncoStep
        val hyst = 0.03f

        for (i in 0 until n) {
            val v = bb[i]
            val halfAmp = max(1e-6f, (mx - mn) * 0.5f)
            if (v > mx) {
                mx = v
            } else {
                mx -= halfAmp * 0.0005f
            }

            if (v < mn) {
                mn = v
            } else {
                mn += halfAmp * 0.0005f
            }

            val th = (mx + mn) * 0.5f
            val amp = max(1e-6f, mx - mn)
            val h = max(0.006f, min(hyst, 0.15f * amp))

            val hb = when {
                v > th + h -> 1
                v < th - h -> 0
                else -> lh
            }

            if (hb != lh) {
                var err = ph
                if (err > 0.5) err -= 1.0
                pllInt += 0.005 * err
                pllInt = pllInt.coerceIn(-maxDelta, maxDelta)
                ph -= (0.1 * err + pllInt)
            }
            lh = hb
            ph += step

            if (ph > 0.5) {
                if (lc == 0) {
                    outBits[bitCount++] = 1 - hb
                }
                lc = 1
            } else {
                lc = 0
            }

            if (ph >= 1.0) {
                ph -= 1.0
            }
        }

        ncoPhase = ph
        pllIntegrator = pllInt
        peakMax = mx
        peakMin = mn
        lastHardBit = lh
        lastBitClk = lc

        return outBits.copyOf(bitCount)
    }
}
