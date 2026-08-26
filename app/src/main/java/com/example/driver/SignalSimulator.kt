package com.example.driver

import com.example.decoder.BchDecoder
import com.example.dsp.ComplexBuffer
import com.example.dsp.DspConstants
import java.nio.charset.Charset
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SignalSimulator(
    val sampleRate: Int = DspConstants.RTL_SAMPLE_RATE,
    val blockSize: Int = DspConstants.BLOCK_SIZE
) {
    private val random = Random()
    private var phase = 0.0
    private var simTime = 0.0

    // Simulation scenario variables
    private var currentTrainIndex = 0
    private var trainKm = 115.0
    private var lastBurstTime = -5.0

    // Pre-computed noise buffer and sine table for ultra-fast zero-overhead generation on older Android CPUs
    private val noiseTableSize = 8192
    private val noiseTableReal = FloatArray(noiseTableSize) { (random.nextGaussian() * 0.02).toFloat() }
    private val noiseTableImag = FloatArray(noiseTableSize) { (random.nextGaussian() * 0.02).toFloat() }
    private var noiseIdx = 0

    private val trigTableSize = 4096
    private val trigMask = trigTableSize - 1
    private val sinLookup = FloatArray(trigTableSize) { i -> sin(2.0 * PI * i / trigTableSize).toFloat() }
    private val cosLookup = FloatArray(trigTableSize) { i -> cos(2.0 * PI * i / trigTableSize).toFloat() }
    private val reusableBuffer = ComplexBuffer(blockSize)

    private val sampleDuration = 1.0 / sampleRate
    private val carrierFreqHz = DspConstants.DEFAULT_DC_OFFSET_HZ // +50 kHz offset from LO
    private val fskDeviationHz = 2400.0 // 2-FSK frequency shift

    private val demoTrains = listOf(
        DemoTrainScenario(trainBase = "102", prefix = "G ", locoCode = 311, locoNum = "5033", route = "京沪高铁", dir = "下行", speed = 310.0, startKm = 115.0),
        DemoTrainScenario(trainBase = "516", prefix = "K ", locoCode = 141, locoNum = "0428", route = "陇海线", dir = "上行", speed = 115.0, startKm = 220.5),
        DemoTrainScenario(trainBase = "28", prefix = "G ", locoCode = 310, locoNum = "3012", route = "京津城际", dir = "下行", speed = 348.0, startKm = 56.4),
        DemoTrainScenario(trainBase = "71022", prefix = "  ", locoCode = 240, locoNum = "0123", route = "大秦铁路", dir = "下行", speed = 78.0, startKm = 45.2),
        DemoTrainScenario(trainBase = "901", prefix = "Z ", locoCode = 141, locoNum = "0981", route = "京九线", dir = "上行", speed = 158.0, startKm = 312.8),
        DemoTrainScenario(trainBase = "3125", prefix = "D ", locoCode = 310, locoNum = "2144", route = "杭深线", dir = "下行", speed = 198.0, startKm = 88.0)
    )

    data class DemoTrainScenario(
        val trainBase: String,
        val prefix: String,
        val locoCode: Int,
        val locoNum: String,
        val route: String,
        val dir: String,
        val speed: Double,
        val startKm: Double
    )

    private var phaseAcc: Int = 0
    private var cachedBitStream: IntArray = IntArray(0)
    private var cachedScenarioIndex: Int = -1
    private var cachedBurstId: Int = -1

    private val step1: Int = (((carrierFreqHz + fskDeviationHz) / sampleRate) * 4294967296.0).toInt()
    private val step0: Int = (((carrierFreqHz - fskDeviationHz) / sampleRate) * 4294967296.0).toInt()
    private val samplesPerBit: Int = sampleRate / DspConstants.BAUD_RATE // 800 samples per bit

    fun resetSimulation() {
        simTime = 0.0
        lastBurstTime = -15.0
        phaseAcc = 0
        currentTrainIndex = -1
        trainKm = demoTrains[0].startKm
        cachedScenarioIndex = -1
        cachedBurstId = -1
    }

    fun generateBlock(): ComplexBuffer {
        val buffer = reusableBuffer
        val now = simTime

        // Trigger a new train packet burst cycle strictly every 15.0 seconds
        if (now - lastBurstTime >= 15.0) {
            lastBurstTime = now
            currentTrainIndex = (currentTrainIndex + 1) % demoTrains.size
            val scenario = demoTrains[currentTrainIndex]
            trainKm = scenario.startKm
            cachedScenarioIndex = -1
        }

        val activeScenario = if (currentTrainIndex >= 0) demoTrains[currentTrainIndex] else demoTrains[0]

        // Fast zero-allocation noise copy
        var nIdx = noiseIdx
        val nMask = noiseTableSize - 1
        val real = buffer.real
        val imag = buffer.imag
        val nReal = noiseTableReal
        val nImag = noiseTableImag
        val bSize = blockSize

        for (i in 0 until bSize) {
            real[i] = nReal[nIdx]
            imag[i] = nImag[nIdx]
            nIdx = (nIdx + 1) and nMask
        }
        noiseIdx = nIdx

        val burstElapsed = now - lastBurstTime
        val inBurst1 = burstElapsed in 0.0..1.6
        val inBurst2 = burstElapsed in 3.0..4.6

        if (inBurst1 || inBurst2) {
            val burstId = if (inBurst1) 1 else 2
            if (cachedScenarioIndex != currentTrainIndex || cachedBurstId != burstId) {
                cachedScenarioIndex = currentTrainIndex
                cachedBurstId = burstId
                val currentKm = if (inBurst1) trainKm else (trainKm + (if (activeScenario.dir == "下行") 0.25 else -0.25))
                cachedBitStream = generateLbjBitstream(activeScenario, currentKm)
            }

            val tInCurrentBurst = if (inBurst1) burstElapsed else (burstElapsed - 3.0)
            val bitStream = cachedBitStream
            val numBits = bitStream.size
            val spb = samplesPerBit
            val sAmp = 0.65f

            var startSampleIdx = (tInCurrentBurst * sampleRate).toInt()
            var ph = phaseAcc
            val s1 = step1
            val s0 = step0
            val sLookup = sinLookup
            val cLookup = cosLookup
            val tMask = trigMask

            for (i in 0 until bSize) {
                val curSample = startSampleIdx + i
                val bitIdx = curSample / spb
                val bit = if (bitIdx in 0 until numBits) bitStream[bitIdx] else 0

                ph += if (bit == 1) s1 else s0
                val idx = (ph ushr 20) and tMask
                real[i] += sAmp * cLookup[idx]
                imag[i] += sAmp * sLookup[idx]
            }
            phaseAcc = ph
        }

        simTime += bSize * sampleDuration
        return buffer
    }

    private fun generateLbjBitstream(scenario: DemoTrainScenario, currentKm: Double): IntArray {
        val bits = ArrayList<Int>()

        // 1. Preamble: 576 alternating bits (101010...) for DPLL bit sync
        for (i in 0 until 576) {
            bits.add(i % 2)
        }

        // 2. Build 65-character BCD payload (15 chars short telemetry + 50 chars detailed payload)
        // Short telemetry (15 chars): trainBase (6 chars), speed (3 chars), space (1 char), position (5 chars)
        val trainPadded = String.format("%-6s", scenario.trainBase)
        val spdPadded = String.format("%03d", scenario.speed.toInt().coerceIn(0, 400))
        val kmInt = (currentKm * 10).toInt().coerceIn(0, 99999)
        val posPadded = String.format("%05d", kmInt)
        val shortBcd = "$trainPadded$spdPadded $posPadded" // exactly 15 chars

        // Detailed payload (50 chars):
        // prefix hex (4 chars) + loco info (8 chars) + spaces (2 chars) + route GBK hex (16 chars) + padding (20 chars)
        val prefixHex = buildString {
            for (c in scenario.prefix.take(2)) {
                append(String.format("%02X", c.code))
            }
            while (length < 4) append("20")
        }
        val prefixBcd = prefixHex.map { hexToBcdChar(it) }.joinToString("")

        val locoPart = String.format("%03d%-5s", scenario.locoCode, scenario.locoNum) // 8 chars

        val gbkBytes = try {
            scenario.route.toByteArray(Charset.forName("GBK"))
        } catch (_: Exception) {
            scenario.route.toByteArray()
        }
        val gbkHex = buildString {
            for (b in gbkBytes) append(String.format("%02X", b.toInt() and 0xFF))
            while (length < 16) append("20")
        }
        val routeBcd = gbkHex.take(16).map { hexToBcdChar(it) }.joinToString("")

        val detailedBcd = buildString {
            append(prefixBcd)   // 4 chars (index 0..3)
            append(locoPart)    // 8 chars (index 4..11)
            append("  ")        // 2 chars (index 12..13)
            append(routeBcd)    // 16 chars (index 14..29)
            while (length < 50) append(' ') // 20 chars pad to total 50
        }

        val fullBcd = shortBcd + detailedBcd // 65 characters = 13 message codewords

        // 3. Batch 1: Address codeword in Frame 7 (Codeword 14) + 1st Message Codeword (Codeword 15)
        appendCodeword(bits, DspConstants.SYNC_STD)
        val func = if (scenario.dir == "下行") 1 else 3
        val baseAddr = 1233999L / 8L // Frame 7: 1233999 % 8 == 7

        for (i in 0 until 14) {
            appendCodeword(bits, DspConstants.IDLE_WORD)
        }
        val addrCw = BchDecoder.encodeCodeword(isMessage = false, data20 = baseAddr, func = func)
        appendCodeword(bits, addrCw)
        val msgCw1 = encodeBcdCodeword(fullBcd.substring(0, 5))
        appendCodeword(bits, msgCw1)

        // 4. Batch 2: Next 12 message codewords + 4 idle codewords
        appendCodeword(bits, DspConstants.SYNC_STD)
        for (w in 1..12) {
            val chunk = fullBcd.substring(w * 5, (w + 1) * 5)
            appendCodeword(bits, encodeBcdCodeword(chunk))
        }
        for (i in 0 until 4) {
            appendCodeword(bits, DspConstants.IDLE_WORD)
        }

        // 5. Batch 3: Trailing idle words to cleanly finalize message
        appendCodeword(bits, DspConstants.SYNC_STD)
        for (i in 0 until 4) {
            appendCodeword(bits, DspConstants.IDLE_WORD)
        }

        return bits.toIntArray()
    }

    private fun hexToBcdChar(c: Char): Char = when (c.uppercaseChar()) {
        'A' -> '*'
        'B' -> 'U'
        'C' -> ' '
        'D' -> '-'
        'E' -> ')'
        'F' -> '('
        else -> c
    }

    private val BCD_MAP = "0123456789*U -)("

    private fun encodeBcdCodeword(fiveChars: String): Long {
        var data20 = 0L
        for (i in 0 until 5) {
            val c = if (i < fiveChars.length) fiveChars[i] else ' '
            val rev = BCD_MAP.indexOf(c).let { if (it >= 0) it else BCD_MAP.indexOf(' ') }
            // Reverse 4 bits to get raw nibble value
            val nibble = ((rev and 1) shl 3) or ((rev and 2) shl 1) or ((rev and 4) shr 1) or ((rev and 8) shr 3)
            data20 = (data20 shl 4) or (nibble.toLong() and 0xF)
        }
        return BchDecoder.encodeCodeword(isMessage = true, data20 = data20)
    }

    private fun appendCodeword(bits: ArrayList<Int>, cw: Long) {
        for (i in 31 downTo 0) {
            bits.add(((cw shr i) and 1L).toInt())
        }
    }
}
