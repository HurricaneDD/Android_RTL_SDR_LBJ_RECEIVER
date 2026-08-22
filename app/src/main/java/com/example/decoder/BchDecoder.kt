package com.example.decoder

object BchDecoder {

    // Generator polynomial for POCSAG BCH(31,21): x^10 + x^9 + x^8 + x^6 + x^5 + x^3 + 1
    // Binary: 11101101001 (0x769 = 1897)
    private const val POLY = 0x769L

    /**
     * Calculates the syndrome (remainder of division by generator polynomial) for a 31-bit codeword.
     * For valid codewords, returns 0.
     */
    fun calcSyndrome(d31: Long): Int {
        var reg = d31 and 0x7FFFFFFFL
        for (i in 30 downTo 10) {
            if (((reg shr i) and 1L) == 1L) {
                reg = reg xor (POLY shl (i - 10))
            }
        }
        return (reg and 0x3FFL).toInt()
    }

    /**
     * Encodes 21 information bits into a 32-bit POCSAG codeword with 10-bit BCH parity and 1-bit overall even parity.
     * @param isMessage true if message codeword (bit 31 = 1), false if address codeword (bit 31 = 0)
     * @param data20 20 bits of data (for message) or 18 bits base address shifted left by 2 (for address)
     * @param func 2 bits function code (0..3) for address codewords
     */
    fun encodeCodeword(isMessage: Boolean, data20: Long, func: Int = 0): Long {
        val info21 = if (isMessage) {
            (1L shl 20) or (data20 and 0xFFFFFL)
        } else {
            ((data20 and 0x3FFFFL) shl 2) or (func.toLong() and 3L)
        }
        val parity10 = calcSyndrome(info21 shl 10)
        val d31 = (info21 shl 10) or (parity10.toLong() and 0x3FFL)
        val cw31 = d31 shl 1
        val ones = java.lang.Long.bitCount(cw31)
        val parityBit = (ones and 1).toLong()
        return cw31 or parityBit
    }

    private val syndromeLut: Map<Int, Int> = run {
        val map = HashMap<Int, Int>()
        for (p in 0 until 31) {
            val syn = calcSyndrome(1L shl p)
            map[syn] = p
        }
        map
    }

    /**
     * Fast BCH(31,21) decoding with 1-bit error correction.
     * Returns Pair(correctedCodeword, isValid).
     */
    fun bchDecodeFast(cwVal: Long): Pair<Long, Boolean> {
        val data31 = (cwVal shr 1) and 0x7FFFFFFFL
        val syn = calcSyndrome(data31)
        if (syn == 0) {
            return Pair(cwVal, true)
        }
        val flipPos = syndromeLut[syn]
        if (flipPos != null) {
            val correctedData = data31 xor (1L shl flipPos)
            val correctedCw = (correctedData shl 1) or (cwVal and 1L)
            return Pair(correctedCw, true)
        }
        return Pair(cwVal, false)
    }

    fun popcount32(x: Long): Int {
        var v = x and 0xFFFFFFFFL
        return java.lang.Long.bitCount(v)
    }
}
