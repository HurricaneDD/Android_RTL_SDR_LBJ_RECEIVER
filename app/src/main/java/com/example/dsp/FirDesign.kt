package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

object FirDesign {

    /**
     * Designs a Blackman-Harris windowed low-pass FIR filter.
     * numTaps is the number of taps (must be odd).
     * cutoffHz is the cutoff frequency in Hz.
     * fs is the sample rate in Hz.
     */
    fun firwinLowPass(numTaps: Int, cutoffHz: Double, fs: Double): FloatArray {
        val n = if (numTaps % 2 == 0) numTaps + 1 else numTaps
        val h = FloatArray(n)
        val m = (n - 1) / 2
        val fc = cutoffHz / fs // normalized cutoff (0..0.5)

        for (i in 0 until n) {
            val k = i - m
            val sinc = if (k == 0) {
                2.0 * fc
            } else {
                sin(2.0 * PI * fc * k) / (PI * k)
            }
            // Blackman-Harris window (4-term)
            val a0 = 0.35875
            val a1 = 0.48829
            val a2 = 0.14128
            val a3 = 0.01168
            val w = a0 - a1 * cos(2.0 * PI * i / (n - 1)) +
                    a2 * cos(4.0 * PI * i / (n - 1)) -
                    a3 * cos(6.0 * PI * i / (n - 1))

            h[i] = (sinc * w).toFloat()
        }

        // Normalize DC gain to 1.0
        var sum = 0.0
        for (v in h) sum += v
        if (sum != 0.0) {
            for (i in h.indices) h[i] = (h[i] / sum).toFloat()
        }
        return h
    }

    /**
     * Designs a halfband filter with normalized cutoff at 0.25 (fs/4).
     */
    fun halfband(numTaps: Int = 63): FloatArray {
        return firwinLowPass(numTaps, 0.25 * 1000.0, 1000.0)
    }

    /**
     * Designs a Gaussian windowed FIR smoothing filter matching Python:
     * firwin(numtaps=31, cutoff=1200, fs=fs, window=('gaussian', 7.0))
     */
    fun gaussianSmoother(numTaps: Int = 31, cutoffHz: Double = 1200.0, fs: Double = 48000.0, std: Double = 7.0): FloatArray {
        val n = if (numTaps % 2 == 0) numTaps + 1 else numTaps
        val h = FloatArray(n)
        val m = (n - 1) / 2.0
        val fc = cutoffHz / fs

        for (i in 0 until n) {
            val k = i - m
            val sinc = if (k == 0.0) {
                2.0 * fc
            } else {
                sin(2.0 * PI * fc * k) / (PI * k)
            }
            // Gaussian window with std dev
            val g = exp(-0.5 * ((i - m) / std) * ((i - m) / std))
            h[i] = (sinc * g).toFloat()
        }

        var sum = 0.0
        for (v in h) sum += v
        if (sum != 0.0) {
            for (i in h.indices) h[i] = (h[i] / sum).toFloat()
        }
        return h
    }
}
