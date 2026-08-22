package com.example.decoder

import com.example.dsp.BitSlicer
import com.example.dsp.DspConstants
import java.nio.charset.Charset
import java.util.regex.Pattern

class LbjDecoder(
    val arrivalEstimator: ArrivalEstimator? = null,
    var strictFilter: Boolean = true,
    var showErrWarn: Boolean = true,
    var filterMode: String = "highlight", // "highlight" or "strict"
    var keywords: List<String> = emptyList(),
    var onTelemetryUpdated: ((TrainTelemetry, EtaInfo) -> Unit)? = null,
    var onWarning: ((String) -> Unit)? = null,
    var onWarningCleared: (() -> Unit)? = null,
    var onRawPacket: ((String) -> Unit)? = null
) {
    private val bitSlicer = BitSlicer()

    private var state = 0
    private var shiftReg = 0L
    private var cwBitCount = 0
    private var pol = 1
    private var wordCount = 0
    private var framePos = 0
    private var inMessage = false
    private var currentAddr = 0L
    private var currentFunc = 0
    private val currentMsgCws = ArrayList<Long>()
    private var huntTimeout = 0
    private var currentMsgHasError = false

    private val sessions = HashMap<String, MutableMap<String, Any?>>()
    private var lastTrain: String? = null
    private var lastWarnTime = 0L

    private var currentTelemetry = TrainTelemetry()
    private var currentEta = EtaInfo()

    fun resetDpllSoft() {
        bitSlicer.resetDpllSoft()
    }

    fun resetReceiverState() {
        bitSlicer.reset()
        state = 0
        shiftReg = 0L
        cwBitCount = 0
        pol = 1
        wordCount = 0
        framePos = 0
        inMessage = false
        currentMsgCws.clear()
        currentMsgHasError = false
        huntTimeout = 0
    }

    fun processAudioChunk(audioChunk: FloatArray) {
        val bits = bitSlicer.process(audioChunk)
        for (bit in bits) {
            processBit(bit)
        }
    }

    private fun processBit(bit: Int) {
        shiftReg = ((shiftReg shl 1) or (bit.toLong() and 1L)) and 0xFFFFFFFFL

        if (state == 0) {
            if (BchDecoder.popcount32(shiftReg xor DspConstants.SYNC_STD) <= 2) {
                pol = 1
                state = 1
                wordCount = 0
                framePos = 0
                cwBitCount = 0
                huntTimeout = 0
            } else if (BchDecoder.popcount32(shiftReg xor DspConstants.SYNC_INV) <= 2) {
                pol = -1
                state = 1
                wordCount = 0
                framePos = 0
                cwBitCount = 0
                huntTimeout = 0
            }

            if (inMessage) {
                huntTimeout++
                if (huntTimeout > 64) {
                    triggerLbjParse()
                }
            }
        } else if (state == 1) {
            cwBitCount++
            if (cwBitCount == 32) {
                cwBitCount = 0
                val raw = if (pol == 1) shiftReg else (shiftReg.inv() and 0xFFFFFFFFL)
                val (corrected, isBchValid) = BchDecoder.bchDecodeFast(raw)

                framePos++
                wordCount++

                if (BchDecoder.popcount32(corrected xor DspConstants.SYNC_STD) <= 2) {
                    wordCount = 0
                    framePos = 0
                } else if (BchDecoder.popcount32(corrected xor DspConstants.IDLE_WORD) <= 2) {
                    if (inMessage) {
                        triggerLbjParse()
                    }
                } else if ((corrected ushr 31) == 0L) {
                    if (inMessage) {
                        triggerLbjParse()
                    }
                    val addrCandidate = ((corrected ushr 13) and 0x3FFFFL) * 8L + ((framePos - 1) / 2)
                    val funcCandidate = ((corrected ushr 11) and 3L).toInt()
                    if (isBchValid && addrCandidate in 1233000L..1235000L) {
                        currentFunc = funcCandidate
                        currentAddr = addrCandidate
                        currentMsgCws.clear()
                        inMessage = true
                        currentMsgHasError = false
                    } else {
                        inMessage = false
                        currentMsgCws.clear()
                    }
                } else if ((corrected ushr 31) == 1L) {
                    if (inMessage) {
                        currentMsgCws.add(corrected)
                        if (!isBchValid) {
                            currentMsgHasError = true
                        }
                    }
                }

                if (wordCount >= 16) {
                    state = 0
                }
            }
        }
    }

    private fun triggerLbjParse() {
        inMessage = false
        if (currentMsgCws.isNotEmpty()) {
            if (currentMsgHasError) {
                if (showErrWarn) {
                    emitWarning(currentFunc)
                }
                if (strictFilter) {
                    currentMsgCws.clear()
                    currentMsgHasError = false
                    return
                }
            }
            val validForEta = !currentMsgHasError
            if (validForEta) {
                onWarningCleared?.invoke()
            }
            val bcd = extractBcd(currentMsgCws)
            onRawPacket?.invoke(bcd)
            decodeLbj(bcd, validForEta = validForEta)
            currentMsgCws.clear()
            currentMsgHasError = false
        }
    }

    private fun emitWarning(func: Int) {
        val now = System.currentTimeMillis()
        if (now - lastWarnTime < 1500) return
        lastWarnTime = now
        val d = if (func == 1) "下行" else if (func == 3) "上行" else "未知"
        val warnMsg = "⚠ 探测到 $d 信号 干扰严重 (BCH校验错误)"
        onWarning?.invoke(warnMsg)
    }

    private val BCD_MAP = "0123456789*U -)("

    fun extractBcd(cwList: List<Long>): String {
        val sb = StringBuilder()
        for (cw in cwList) {
            val data20 = ((cw shr 11) and 0xFFFFFL).toInt()
            for (n in 0 until 5) {
                val value = (data20 shr (16 - n * 4)) and 0xF
                val rev = ((value and 1) shl 3) or ((value and 2) shl 1) or ((value and 4) shr 1) or ((value and 8) shr 3)
                if (rev in BCD_MAP.indices) {
                    sb.append(BCD_MAP[rev])
                } else {
                    sb.append('?')
                }
            }
        }
        return sb.toString()
    }

    private fun bcdToHexChar(c: Char): Char {
        return when (c) {
            '*' -> 'A'
            'U' -> 'B'
            ' ' -> 'C'
            '-' -> 'D'
            ')' -> 'E'
            '(' -> 'F'
            else -> c
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            val h = Character.digit(s[i], 16)
            val l = Character.digit(s[i + 1], 16)
            if (h != -1 && l != -1) {
                data[i / 2] = ((h shl 4) + l).toByte()
            }
            i += 2
        }
        return data
    }

    fun decodeLbj(bcd: String, validForEta: Boolean = true) {
        val addr = currentAddr
        val func = currentFunc
        val now = System.currentTimeMillis()

        if (addr == 1234008L || (addr in listOf(1233999L, 1234000L) && func == 0 && bcd.length == 5 && bcd.firstOrNull() in listOf('-', '*') && bcd.getOrNull(4) != '-')) {
            return
        }

        val isShort = (addr == 1233999L || addr == 1234000L) && bcd.length >= 15
        val isMerged = (addr == 1233999L || addr == 1234000L || addr == 1234002L) && bcd.length >= 65
        val isStandalone = (addr == 1234001L || addr == 1234002L) && bcd.length < 65

        var baseTrain: String? = null

        if (isShort) {
            val raw = if (bcd.length >= 6) bcd.substring(0, 6).trim() else ""
            baseTrain = if (strictFilter) {
                if (Pattern.matches("^[A-Za-z0-9]+$", raw)) raw else "----"
            } else {
                if (!raw.contains('*') && !raw.contains('-')) raw else "----"
            }
            lastTrain = baseTrain

            val direction = when (func) {
                1 -> "下行"
                3 -> "上行"
                else -> "未知($func)"
            }

            val rs = if (bcd.length >= 9) bcd.substring(6, 9).replace(' ', '0').replace('U', '0').replace('*', '0') else ""
            val speedInt = rs.toIntOrNull()
            val speed = if (speedInt != null && speedInt in 0..400) speedInt.toString() else "---"

            val pr = if (bcd.length >= 15) bcd.substring(10, 15) else ""
            val position = if (pr.any { it in listOf('*', '-', 'X', 'U') } || pr.length < 5) {
                "---.-"
            } else {
                val ps = pr.replace(' ', '0')
                if (ps.all { it.isDigit() }) "${ps.substring(0, 4)}.${ps[4]}" else "---.-"
            }

            val session = sessions.getOrPut(baseTrain) {
                mutableMapOf(
                    "base_train" to baseTrain,
                    "prefix" to "",
                    "direction" to direction,
                    "speed" to speed,
                    "position" to position,
                    "loco" to "----",
                    "loco_code" to "---",
                    "route" to "----",
                    "route_valid" to false,
                    "is_detailed" to false,
                    "timestamp" to now
                )
            }
            if (direction != "未知" && !direction.startsWith("未知")) {
                session["direction"] = direction
            }
            if (speed != "---") {
                session["speed"] = speed
            }
            if (position != "---.-") {
                session["position"] = position
            }
            session["timestamp"] = now
        }

        if (isMerged || isStandalone) {
            val tid = if (isMerged) baseTrain else lastTrain
            if (tid != null && sessions.containsKey(tid)) {
                val session = sessions[tid]!!
                val lastTs = (session["timestamp"] as? Long) ?: 0L
                if (!(isStandalone && now - lastTs > 2000)) {
                    val buf = if (bcd.length >= 50) bcd.takeLast(50) else bcd
                    val ih = buildString {
                        for (c in buf) append(bcdToHexChar(c))
                    }

                    var lc = ""
                    if (ih.length >= 6) {
                        try {
                            val c1 = ih.substring(0, 2).toInt(16)
                            val c2 = ih.substring(2, 4).toInt(16)
                            if (strictFilter) {
                                if (c1 in 48..57 || c1 in 65..90) lc += c1.toChar()
                                if (c2 in 48..57 || c2 in 65..90) lc += c2.toChar()
                            } else {
                                if (c1 in 32..126 && c1 != 34 && c1 != 44) lc += c1.toChar()
                                if (c2 in 32..126 && c2 != 34 && c2 != 44) lc += c2.toChar()
                            }
                        } catch (_: Exception) {}
                    }
                    lc = lc.replace(" ", "").trim()

                    val lr = if (buf.length >= 12) buf.substring(4, 12) else ""
                    var lm = "----"
                    var lk = "---"
                    if (lr.length >= 3) {
                        val cp = lr.substring(0, 3)
                        val np = lr.substring(3).trim()
                        if (cp.all { it.isDigit() }) {
                            lk = cp
                            val ti = cp.toInt()
                            val ln = LocomotiveDict.getLocoName(ti)
                            var locoNum = np
                            if (strictFilter) {
                                if (!locoNum.all { it.isDigit() }) locoNum = "----"
                            } else if (locoNum.isEmpty() || locoNum.any { it in listOf('*', '-', 'X', ' ') }) {
                                locoNum = "----"
                            }
                            lm = "$ln-$locoNum"
                        }
                    }

                    var ru = "----"
                    if (ih.length >= 30) {
                        try {
                            val hexPart = ih.substring(14, 30)
                            val bytes = hexStringToByteArray(hexPart)
                            val gbkCharset = try {
                                Charset.forName("GBK")
                            } catch (_: Exception) {
                                Charset.forName("GB2312")
                            }
                            val dec = String(bytes, gbkCharset).replace("\u0000", "").trim()
                            if (strictFilter) {
                                if (dec.isNotEmpty() && Pattern.matches("^[\\u4e00-\\u9fa5A-Za-z0-9\\-\\s]+$", dec)) {
                                    ru = dec
                                }
                            } else if (dec.isNotEmpty() && Pattern.compile("[\\u4e00-\\u9fa5a-zA-Z0-9]").matcher(dec).find()) {
                                ru = dec
                            }
                        } catch (_: Exception) {}
                    }

                    if (lc.isNotEmpty()) {
                        session["prefix"] = lc
                    }
                    if (lm != "----") {
                        session["loco"] = lm
                        session["loco_code"] = lk
                    }
                    if (validForEta && ru != "----" && ArrivalEstimator.isRouteValid(ru)) {
                        session["route"] = ru
                        session["route_valid"] = true
                    }
                    session["is_detailed"] = true
                    session["timestamp"] = now
                }
            }
        }

        val aid = if (isShort) baseTrain else lastTrain
        if (aid != null && sessions.containsKey(aid)) {
            val s = sessions[aid]!!
            val prefix = (s["prefix"] as? String) ?: ""
            val bTrain = (s["base_train"] as? String) ?: ""
            val fullTrain = "$prefix$bTrain".replace(" ", "").trim()

            val dir = (s["direction"] as? String) ?: "未知"
            val spd = (s["speed"] as? String) ?: "---"
            val pos = (s["position"] as? String) ?: "---.-"
            val loco = (s["loco"] as? String) ?: "----"
            val locoCode = (s["loco_code"] as? String) ?: "---"
            val route = (s["route"] as? String) ?: "----"
            val routeValid = (s["route_valid"] as? Boolean) ?: false
            val isDet = (s["is_detailed"] as? Boolean) ?: false
            val cat = TrainCategorizer.categorize(fullTrain, isDet)

            val isHit = checkWatchlistHit(fullTrain, loco)

            if (keywords.isNotEmpty() && filterMode == "strict" && !isHit) {
                // Ignore non-matching in strict filter mode
            } else {
                currentTelemetry = TrainTelemetry(
                    trainNo = fullTrain,
                    direction = dir,
                    speed = spd,
                    positionKm = pos,
                    locoModel = loco,
                    locoCode = locoCode,
                    route = route,
                    category = cat,
                    isDetailed = isDet,
                    isRouteValid = routeValid,
                    isHit = isHit,
                    timestamp = now,
                    rawBcd = bcd
                )

                if (arrivalEstimator != null) {
                    val routeOk = validForEta && routeValid
                    currentEta = arrivalEstimator.estimate(
                        train = fullTrain,
                        direction = dir,
                        speedStr = spd,
                        positionStr = pos,
                        routeStr = route,
                        goodData = routeOk,
                        nowEpochMs = now
                    )
                }

                onTelemetryUpdated?.invoke(currentTelemetry, currentEta)
            }
        }

        // Evict expired sessions (>10s)
        val expired = sessions.filter { now - ((it.value["timestamp"] as? Long) ?: 0L) > 10000 }.keys
        for (k in expired) {
            sessions.remove(k)
            if (lastTrain == k) {
                lastTrain = null
            }
        }
    }

    private fun checkWatchlistHit(trainNo: String, loco: String): Boolean {
        if (keywords.isEmpty()) return false
        val tu = trainNo.replace(" ", "").uppercase()
        val lu = loco.replace(" ", "").uppercase()

        for (kw in keywords) {
            val ku = kw.replace(" ", "").uppercase()
            if (ku.isNotEmpty() && lu.contains(ku)) return true
            if (ku.isNotEmpty() && tu.isNotEmpty()) {
                if (ku == tu) return true
                val kwDigits = ku.filter { it.isDigit() }
                val trDigits = tu.filter { it.isDigit() }
                if (kwDigits.isNotEmpty() && trDigits.isNotEmpty() && kwDigits == trDigits) {
                    if (ku.all { it.isDigit() }) return true
                    if (!tu.all { it.isDigit() }) return true
                }
            }
        }
        return false
    }
}
