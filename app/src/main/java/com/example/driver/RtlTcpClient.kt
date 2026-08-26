package com.example.driver

import com.example.dsp.ComplexBuffer
import com.example.dsp.DspConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RtlTcpClient(
    val host: String = "127.0.0.1",
    val port: Int = 1234,
    initialFreqHz: Double = DspConstants.DEFAULT_FREQ_HZ,
    val dcOffsetHz: Double = DspConstants.DEFAULT_DC_OFFSET_HZ,
    val sampleRate: Int = DspConstants.RTL_SAMPLE_RATE,
    initialGainDb: Float = DspConstants.HW_GAIN_DB,
    initialPpm: Int = DspConstants.PPM,
    val blockSize: Int = DspConstants.BLOCK_SIZE
) {
    var targetFreqHz: Double = initialFreqHz
        private set
    var gainDb: Float = initialGainDb
        private set
    var ppm: Int = initialPpm
        private set

    enum class ConnectionState {
        IDLE,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    companion object {
        // Fast precomputed conversion table (0..255) -> Float: (b - 127.5f) / 128.0f
        val BYTE_TO_FLOAT_TABLE = FloatArray(256) { b -> (b - 127.5f) * 0.0078125f }
    }

    var connectionState: ConnectionState = ConnectionState.IDLE
        private set

    var lastErrorMessage: String? = null
        private set

    private val isRunning = AtomicBoolean(false)
    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var workerThread: Thread? = null
    private val bufferPool = ArrayBlockingQueue<ComplexBuffer>(24).apply {
        repeat(24) { offer(ComplexBuffer(blockSize)) }
    }
    private val bufferQueue = ArrayBlockingQueue<ComplexBuffer>(24)

    var onStateChanged: ((ConnectionState, String?) -> Unit)? = null

    val hwFreqHz: Double
        get() = targetFreqHz - dcOffsetHz

    fun recycleBuffer(buffer: ComplexBuffer) {
        bufferPool.offer(buffer)
    }

    fun open() {
        if (isRunning.get()) return
        isRunning.set(true)
        bufferQueue.clear()
        setState(ConnectionState.CONNECTING, null)

        workerThread = Thread({
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}
            runReaderLoop()
        }, "RTL-TCP-Reader").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun close() {
        isRunning.set(false)
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
        workerThread?.interrupt()
        workerThread = null
        setState(ConnectionState.DISCONNECTED, null)
    }

    private fun setState(state: ConnectionState, errorMsg: String?) {
        connectionState = state
        lastErrorMessage = errorMsg
        onStateChanged?.invoke(state, errorMsg)
    }

    private fun runReaderLoop() {
        val chunkBytes = blockSize * 2 // 2 bytes per IQ sample
        val byteBuffer = ByteArray(chunkBytes)

        try {
            var connectedSocket: Socket? = null
            val maxAttempts = 10
            var attempt = 0
            var lastEx: Exception? = null

            while (isRunning.get() && attempt < maxAttempts && connectedSocket == null) {
                attempt++
                try {
                    val s = Socket()
                    s.tcpNoDelay = true
                    s.receiveBufferSize = 256 * 1024
                    s.connect(InetSocketAddress(host, port), 1200)
                    connectedSocket = s
                } catch (e: Exception) {
                    lastEx = e
                    if (attempt < maxAttempts && isRunning.get()) {
                        try {
                            Thread.sleep(400) // Wait for USB driver service to start listening on port 1234
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }

            if (connectedSocket == null) {
                throw lastEx ?: RuntimeException("无法连接 RTL-TCP 服务端 ($host:$port)")
            }

            val s = connectedSocket
            socket = s
            val inStream = s.getInputStream()
            val outStream = s.getOutputStream()
            outputStream = outStream

            // Read 12-byte RTL-TCP header
            val header = ByteArray(12)
            var hdrRead = 0
            while (hdrRead < 12 && isRunning.get()) {
                val r = inStream.read(header, hdrRead, 12 - hdrRead)
                if (r < 0) throw RuntimeException("RTL-TCP 驱动服务端连接已断开")
                hdrRead += r
            }

            // Send initial tuning commands
            sendCmd(DspConstants.CMD_SET_SAMPLERATE, sampleRate.toLong())
            sendCmd(DspConstants.CMD_SET_FREQ, hwFreqHz.toLong())
            sendCmd(DspConstants.CMD_SET_GAINMODE, 1L)
            sendCmd(DspConstants.CMD_SET_GAIN, (gainDb * 10).toLong())
            sendCmd(DspConstants.CMD_SET_FREQCORR, ppm.toLong())
            sendCmd(DspConstants.CMD_SET_AGC, 0L)

            setState(ConnectionState.CONNECTED, null)

            while (isRunning.get()) {
                var bytesRead = 0
                while (bytesRead < chunkBytes && isRunning.get()) {
                    val r = inStream.read(byteBuffer, bytesRead, chunkBytes - bytesRead)
                    if (r < 0) {
                        throw RuntimeException("RTL-TCP 数据流已中断")
                    }
                    bytesRead += r
                }

                if (bytesRead == chunkBytes) {
                    val complexBuf = bufferPool.poll() ?: ComplexBuffer(blockSize)
                    val real = complexBuf.real
                    val imag = complexBuf.imag
                    val tab = BYTE_TO_FLOAT_TABLE

                    for (i in 0 until blockSize) {
                        val idx2 = i * 2
                        real[i] = tab[byteBuffer[idx2].toInt() and 0xFF]
                        imag[i] = tab[byteBuffer[idx2 + 1].toInt() and 0xFF]
                    }
                    // Offer to queue; if full, drop oldest to avoid UI freeze and latency accumulation
                    if (!bufferQueue.offer(complexBuf)) {
                        val oldest = bufferQueue.poll()
                        if (oldest != null) bufferPool.offer(oldest)
                        bufferQueue.offer(complexBuf)
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                val msg = if (e is java.net.ConnectException) {
                    "【连接被拒】未检测到运行中的 RTL-SDR 驱动，请先点击启动驱动或开启测试仿真模式。"
                } else {
                    "网络流异常: ${e.localizedMessage ?: e.message}"
                }
                setState(ConnectionState.ERROR, msg)
            }
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    fun readBlock(timeoutMs: Long = 500): ComplexBuffer? {
        return bufferQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun setFrequency(newFreqHz: Double) {
        targetFreqHz = newFreqHz
        val hwFreq = hwFreqHz.toLong()
        sendCmd(DspConstants.CMD_SET_FREQ, hwFreq)
    }

    fun setGain(newGainDb: Float) {
        // Find nearest gain in table
        var nearest = DspConstants.R820T_GAINS[0]
        var minDiff = Float.MAX_VALUE
        for (g in DspConstants.R820T_GAINS) {
            val diff = kotlin.math.abs(g - newGainDb)
            if (diff < minDiff) {
                minDiff = diff
                nearest = g
            }
        }
        gainDb = nearest
        sendCmd(DspConstants.CMD_SET_GAINMODE, 1L)
        sendCmd(DspConstants.CMD_SET_GAIN, (nearest * 10).toLong())
    }

    fun setPpm(newPpm: Int) {
        ppm = newPpm
        sendCmd(DspConstants.CMD_SET_FREQCORR, ppm.toLong())
    }

    private fun sendCmd(cmdId: Int, param: Long) {
        val out = outputStream ?: return
        try {
            val buf = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN)
            buf.put(cmdId.toByte())
            buf.putInt((param and 0xFFFFFFFFL).toInt())
            synchronized(out) {
                out.write(buf.array())
                out.flush()
            }
        } catch (_: Exception) {}
    }
}
