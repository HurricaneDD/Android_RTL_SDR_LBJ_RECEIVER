package com.example.ui

import android.app.Application
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LbjDatabase
import com.example.data.RouteStationKmEntity
import com.example.data.TrainRecord
import com.example.decoder.ArrivalEstimator
import com.example.decoder.EtaInfo
import com.example.decoder.LbjDecoder
import com.example.decoder.TrainTelemetry
import com.example.driver.DriverLauncher
import com.example.driver.RtlTcpClient
import com.example.driver.SignalSimulator
import com.example.dsp.DspConstants
import com.example.dsp.DspFrontend
import com.example.dsp.FftProcessor
import com.example.dsp.RssiGate
import com.example.service.LbjKeepAliveService
import com.example.util.SoundAlertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReceiverState(
    val isRunning: Boolean = false,
    val isSimulationMode: Boolean = false,
    val connectionState: RtlTcpClient.ConnectionState = RtlTcpClient.ConnectionState.IDLE,
    val host: String = "127.0.0.1",
    val port: Int = 1234,
    val freqHz: Double = DspConstants.DEFAULT_FREQ_HZ,
    val gainDb: Float = DspConstants.HW_GAIN_DB,
    val ppm: Int = DspConstants.PPM,
    val dcOffsetHz: Double = DspConstants.DEFAULT_DC_OFFSET_HZ,
    val bwKhz: Double = DspConstants.DEFAULT_BW_KHZ,
    val rssiDb: Float = -120.0f,
    val csThresholdDb: Float = DspConstants.DEFAULT_RSSI_THRESHOLD_DB,
    val rssiGateState: String = "OFF",
    val rssiHoldMs: Float = 0.0f,
    val afcHz: Double = 0.0,
    val afcErrHz: Double = 0.0,
    val afcScore: Double = 0.0,
    val afcEnabled: Boolean = true,
    val strictFilter: Boolean = true,
    val showErrWarn: Boolean = true,
    val filterMode: String = "highlight",
    val keywords: List<String> = emptyList(),
    val warningMessage: String = "",
    val warningTime: Long = 0L,
    val broadcastAlerts: Boolean = true,
    val alertToneEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val showSimulationButton: Boolean = false,
    val showSignalLossDialog: Boolean = false,
    val spectrumBars: FloatArray = FloatArray(32) { -120.0f },
    val peakFreqHz: Double? = null,
    val peakDeltaHz: Double? = null,
    val peakDb: Float? = null,
    val currentRouteStationKmText: String = "---"
)

class LbjViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LbjDatabase.getDatabase(application)
    private val dao = db.lbjDao()

    val historyRecords = dao.getAllTrainRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedRouteKms = dao.getAllRouteStationKms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _receiverState = MutableStateFlow(ReceiverState())
    val receiverState: StateFlow<ReceiverState> = _receiverState.asStateFlow()

    private val _liveTelemetry = MutableStateFlow(TrainTelemetry())
    val liveTelemetry: StateFlow<TrainTelemetry> = _liveTelemetry.asStateFlow()

    private val _liveEta = MutableStateFlow(EtaInfo())
    val liveEta: StateFlow<EtaInfo> = _liveEta.asStateFlow()

    private val arrivalEstimator = ArrivalEstimator()
    private val rssiGate = RssiGate()
    private val fftProcessor = FftProcessor()
    private val simulator = SignalSimulator()

    private var dspFrontend = DspFrontend(
        sampleRate = DspConstants.RTL_SAMPLE_RATE.toDouble(),
        dcOffset = DspConstants.DEFAULT_DC_OFFSET_HZ,
        bwHz = DspConstants.DEFAULT_BW_KHZ * 1000.0,
        afcEnable = true
    )

    private val rtlClient = RtlTcpClient(
        initialFreqHz = DspConstants.DEFAULT_FREQ_HZ,
        dcOffsetHz = DspConstants.DEFAULT_DC_OFFSET_HZ,
        initialGainDb = DspConstants.HW_GAIN_DB,
        initialPpm = DspConstants.PPM
    )

    private val decoder = LbjDecoder(
        arrivalEstimator = arrivalEstimator,
        strictFilter = true,
        showErrWarn = true,
        filterMode = "highlight",
        keywords = emptyList()
    )

    private var dspJob: Job? = null
    private var lastPeakValue: Float? = null
    private var lastPeakChangeTime: Long = 0L
    private var hasShownSignalLossDialog: Boolean = false

    private val soundAlertManager = SoundAlertManager(viewModelScope)
    private var lastDecodedTrainNo: String = ""
    private var lastAlertPlayTime: Long = 0L

    // Train Session Tracking (Single history record per train pass)
    private var activeTrainRecordId: Long? = null
    private var activeTrainNo: String? = null
    private var lastValidTelemetryTime: Long = 0L

    init {
        // Load initial route KM mappings from Room
        viewModelScope.launch(Dispatchers.IO) {
            val list = dao.getAllRouteStationKmsList()
            if (list.isEmpty()) {
                val defaults = listOf(
                    RouteStationKmEntity("京沪高铁", 145.8, System.currentTimeMillis()),
                    RouteStationKmEntity("京沪线", 1300.2, System.currentTimeMillis()),
                    RouteStationKmEntity("陇海线", 250.0, System.currentTimeMillis()),
                    RouteStationKmEntity("京津城际", 80.0, System.currentTimeMillis()),
                    RouteStationKmEntity("京九线", 350.0, System.currentTimeMillis()),
                    RouteStationKmEntity("杭深线", 120.0, System.currentTimeMillis())
                )
                for (d in defaults) {
                    dao.insertRouteStationKm(d)
                    arrivalEstimator.setRouteKm(d.routeName, d.stationKm)
                }
            } else {
                for (item in list) {
                    arrivalEstimator.setRouteKm(item.routeName, item.stationKm)
                }
            }
        }

        // Configure decoder callbacks
        decoder.onTelemetryUpdated = { telemetry, eta ->
            val now = System.currentTimeMillis()
            lastValidTelemetryTime = now
            _liveTelemetry.value = telemetry
            _liveEta.value = eta

            // Play 2-second alert tone on train signal decode or train change if enabled
            if (_receiverState.value.alertToneEnabled && telemetry.trainNo != "----") {
                val isTrainChanged = (lastDecodedTrainNo.isNotEmpty() && lastDecodedTrainNo != telemetry.trainNo)
                val isNewSignal = (lastDecodedTrainNo.isEmpty() || (now - lastAlertPlayTime > 4000L))

                if (isTrainChanged || isNewSignal) {
                    lastDecodedTrainNo = telemetry.trainNo
                    lastAlertPlayTime = now
                    soundAlertManager.playAlertTone()
                }
            }

            // Update foreground keep alive service notification if enabled
            if (_receiverState.value.keepAliveEnabled && _receiverState.value.isRunning) {
                if (telemetry.trainNo != "----") {
                    LbjKeepAliveService.update(
                        getApplication(),
                        "已探测列车: ${telemetry.trainNo} (${telemetry.direction})",
                        "机车: ${telemetry.locoModel} | 线路: ${telemetry.route}"
                    )
                }
            }

            // Send Android broadcast if enabled
            if (_receiverState.value.broadcastAlerts && telemetry.trainNo != "----") {
                DriverLauncher.sendAlertBroadcast(
                    getApplication(),
                    train = telemetry.trainNo,
                    direction = telemetry.direction,
                    speed = telemetry.speed,
                    position = telemetry.positionKm,
                    loco = telemetry.locoModel,
                    locoCode = telemetry.locoCode,
                    route = telemetry.route,
                    category = telemetry.category
                )
            }

            // Deduplicated Train History: Only 1 record per train pass
            if (telemetry.trainNo != "----" && telemetry.trainNo.isNotBlank()) {
                val currentNo = telemetry.trainNo
                viewModelScope.launch(Dispatchers.IO) {
                    if (activeTrainNo == null || activeTrainNo != currentNo) {
                        // Finalize previous train if any
                        activeTrainRecordId?.let { prevId ->
                            dao.updateLastSeenTime(prevId, now)
                        }
                        // Insert new train record
                        val newRecord = TrainRecord(
                            trainNo = currentNo,
                            direction = telemetry.direction,
                            locoModel = telemetry.locoModel,
                            locoCode = telemetry.locoCode,
                            route = telemetry.route,
                            category = telemetry.category,
                            firstSeenTime = now,
                            lastSeenTime = now
                        )
                        val insertedId = dao.insertTrainRecord(newRecord)
                        activeTrainRecordId = insertedId
                        activeTrainNo = currentNo
                    } else {
                        // Update existing train session
                        activeTrainRecordId?.let { recordId ->
                            dao.updateTrainSession(
                                id = recordId,
                                locoModel = telemetry.locoModel,
                                locoCode = telemetry.locoCode,
                                route = telemetry.route,
                                category = telemetry.category,
                                lastSeenTime = now
                            )
                        }
                    }
                }
            }
        }

        decoder.onWarning = { warn ->
            val now = System.currentTimeMillis()
            _receiverState.value = _receiverState.value.copy(
                warningMessage = warn,
                warningTime = now
            )
        }

        decoder.onWarningCleared = {
            val curWarn = _receiverState.value.warningMessage
            if (curWarn.contains("BCH") || curWarn.contains("校验错误") || curWarn.contains("干扰")) {
                _receiverState.value = _receiverState.value.copy(warningMessage = "")
            }
        }

        rtlClient.onStateChanged = { state, error ->
            _receiverState.value = _receiverState.value.copy(
                connectionState = state,
                warningMessage = error ?: _receiverState.value.warningMessage
            )
        }
    }

    fun startReceiver(isSimulation: Boolean = false) {
        if (_receiverState.value.isRunning) {
            stopReceiver()
        }

        hasShownSignalLossDialog = false
        lastPeakValue = null
        lastPeakChangeTime = System.currentTimeMillis()
        lastValidTelemetryTime = 0L

        if (isSimulation) {
            simulator.resetSimulation()
        }

        _receiverState.value = _receiverState.value.copy(
            isRunning = true,
            isSimulationMode = isSimulation,
            showSignalLossDialog = false,
            warningMessage = if (isSimulation) "已开启 RF 信号仿真流演示模式 (每5秒模拟报文)" else ""
        )

        if (!isSimulation) {
            rtlClient.open()
        }

        if (_receiverState.value.keepAliveEnabled) {
            LbjKeepAliveService.start(
                getApplication(),
                "SDR-LBJ 信号监听守候中",
                if (isSimulation) "仿真演示模式运行中" else "频率: ${_receiverState.value.freqHz / 1e6} MHz"
            )
        }

        dspJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
            } catch (_: Exception) {}
            runDspLoop(isSimulation)
        }
    }

    fun stopReceiver() {
        dspJob?.cancel()
        dspJob = null
        rtlClient.close()

        // Finalize active train session in DB
        finalizeActiveTrainSession()

        lastDecodedTrainNo = ""
        clearLiveTelemetry()

        if (_receiverState.value.keepAliveEnabled) {
            LbjKeepAliveService.stop(getApplication())
        }

        _receiverState.value = _receiverState.value.copy(
            isRunning = false,
            spectrumBars = FloatArray(32) { -120.0f },
            peakFreqHz = null,
            peakDeltaHz = null,
            peakDb = null,
            rssiDb = -120.0f,
            rssiGateState = "OFF",
            rssiHoldMs = 0.0f,
            afcHz = 0.0,
            afcErrHz = 0.0,
            afcScore = 0.0
        )
    }

    private fun finalizeActiveTrainSession() {
        val id = activeTrainRecordId
        if (id != null) {
            val now = System.currentTimeMillis()
            viewModelScope.launch(Dispatchers.IO) {
                dao.updateLastSeenTime(id, now)
            }
            activeTrainRecordId = null
            activeTrainNo = null
        }
    }

    fun dismissSignalLossDialog() {
        _receiverState.value = _receiverState.value.copy(showSignalLossDialog = false)
    }

    private suspend fun runDspLoop(isSimulation: Boolean) {
        val resetAfcOnRelease = true
        var lastUiUpdateTime = 0L
        var nextSimTime = System.currentTimeMillis()

        while (viewModelScope.isActive && _receiverState.value.isRunning) {
            val iq = if (isSimulation) {
                val now = System.currentTimeMillis()
                val waitMs = nextSimTime - now
                if (waitMs > 0) {
                    delay(waitMs)
                }
                if (nextSimTime < now - 150L) {
                    nextSimTime = now + 68L
                } else {
                    nextSimTime += 68L
                }
                simulator.generateBlock()
            } else {
                val block = rtlClient.readBlock(50)
                if (block == null) {
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastPeakChangeTime >= 3000L && !hasShownSignalLossDialog && !_receiverState.value.isSimulationMode) {
                        hasShownSignalLossDialog = true
                        _receiverState.value = _receiverState.value.copy(showSignalLossDialog = true)
                    }
                    delay(5)
                    continue
                }
                block
            }

            if (!viewModelScope.isActive || !_receiverState.value.isRunning) {
                break
            }

            val nowMs = System.currentTimeMillis()

            // Check if train signal is lost (>10s with no packet): auto clear live display & finalize session
            if (_liveTelemetry.value.trainNo != "----" && lastValidTelemetryTime > 0L) {
                if (nowMs - lastValidTelemetryTime > 10000L) {
                    finalizeActiveTrainSession()
                    clearLiveTelemetry()
                    if (_receiverState.value.keepAliveEnabled) {
                        LbjKeepAliveService.update(
                            getApplication(),
                            "SDR-LBJ 信号监听守候中",
                            "列车已远离 (信号丢失自动复位)"
                        )
                    }
                }
            }

            // Auto-clear BCH warning message if expired (>3.5s)
            val curWarn = _receiverState.value.warningMessage
            if (curWarn.isNotEmpty() && (curWarn.contains("BCH") || curWarn.contains("干扰")) && (nowMs - _receiverState.value.warningTime > 3500L)) {
                _receiverState.value = _receiverState.value.copy(warningMessage = "")
            }

            // 1. Process FFT & spectrum
            val curState = _receiverState.value
            val hwFreq = curState.freqHz - curState.dcOffsetHz
            val fftRes = fftProcessor.process(
                iqBuffer = iq,
                sampleRate = DspConstants.RTL_SAMPLE_RATE.toDouble(),
                hwFreqHz = hwFreq,
                targetFreqHz = curState.freqHz,
                bwKhz = curState.bwKhz
            )

            // Detect peak freeze in real SDR reception mode (3 seconds with no change)
            if (!isSimulation) {
                val curPeak = fftRes.peakInfo.peakDb
                if (curPeak == null || lastPeakValue == null || kotlin.math.abs(curPeak - (lastPeakValue ?: 0f)) > 0.001f) {
                    lastPeakValue = curPeak
                    lastPeakChangeTime = nowMs
                } else {
                    if (nowMs - lastPeakChangeTime >= 3000L && !hasShownSignalLossDialog) {
                        hasShownSignalLossDialog = true
                        _receiverState.value = _receiverState.value.copy(showSignalLossDialog = true)
                    }
                }
            }

            // 2. Process DSP frontend chain
            val dspRes = dspFrontend.process(iq, rssiGate)

            // 3. Check AFC update
            if (dspFrontend.consumeAfcUpdated()) {
                decoder.resetDpllSoft()
            }

            // 4. Feed baseband PCM to slicer & decoder
            if (dspRes.rxActive) {
                decoder.processAudioChunk(dspRes.pcmFloat)
            } else if (rssiGate.justDeactivated) {
                decoder.resetReceiverState()
                if (resetAfcOnRelease && dspFrontend.afc.enabled) {
                    dspFrontend.resetAfc()
                }
            }

            if (!isSimulation) {
                rtlClient.recycleBuffer(iq)
            }

            // Update UI state with 60ms throttle for ultra-responsive spectrum and demodulation metrics
            val stateChanged = rssiGate.justActivated || rssiGate.justDeactivated
            if (stateChanged || nowMs - lastUiUpdateTime >= 60L) {
                lastUiUpdateTime = nowMs
                val curRoute = _liveTelemetry.value.route
                val routeKm = arrivalEstimator.getKmForRoute(curRoute)
                val routeKmText = if (routeKm != null) ArrivalEstimator.formatKm(routeKm) else "未设置"

                _receiverState.value = _receiverState.value.copy(
                    rssiDb = dspRes.rssiDb,
                    rssiGateState = rssiGate.state,
                    rssiHoldMs = rssiGate.holdLeftMs,
                    afcHz = dspFrontend.afc.afcHz,
                    afcErrHz = dspFrontend.afc.lastErrHz,
                    afcScore = dspFrontend.afc.lastScore,
                    spectrumBars = fftRes.bandsDb.clone(),
                    peakFreqHz = fftRes.peakInfo.peakFreqHz,
                    peakDeltaHz = fftRes.peakInfo.peakDeltaHz,
                    peakDb = fftRes.peakInfo.peakDb,
                    currentRouteStationKmText = routeKmText
                )
            }
        }
    }

    // Tuning controls
    fun setFrequency(freqMhz: Double) {
        val freqHz = freqMhz * 1_000_000.0
        _receiverState.value = _receiverState.value.copy(freqHz = freqHz)
        rtlClient.setFrequency(freqHz)
        dspFrontend.resetAfc()
        rssiGate.reset()
    }

    fun setGain(gainDb: Float) {
        _receiverState.value = _receiverState.value.copy(gainDb = gainDb)
        rtlClient.setGain(gainDb)
    }

    fun setPpm(ppm: Int) {
        _receiverState.value = _receiverState.value.copy(ppm = ppm)
        rtlClient.setPpm(ppm)
        dspFrontend.resetAfc()
        rssiGate.reset()
    }

    fun setCsThreshold(thresholdDb: Float) {
        _receiverState.value = _receiverState.value.copy(csThresholdDb = thresholdDb)
        rssiGate.setThreshold(thresholdDb)
    }

    fun setStrictFilter(enabled: Boolean) {
        _receiverState.value = _receiverState.value.copy(strictFilter = enabled)
        decoder.strictFilter = enabled
    }

    fun setShowErrWarn(enabled: Boolean) {
        _receiverState.value = _receiverState.value.copy(showErrWarn = enabled)
        decoder.showErrWarn = enabled
    }

    fun setFilterMode(mode: String) {
        _receiverState.value = _receiverState.value.copy(filterMode = mode)
        decoder.filterMode = mode
    }

    fun setKeywords(kwList: List<String>) {
        _receiverState.value = _receiverState.value.copy(keywords = kwList)
        decoder.keywords = kwList
    }

    fun setBroadcastAlerts(enabled: Boolean) {
        _receiverState.value = _receiverState.value.copy(broadcastAlerts = enabled)
    }

    fun setAlertToneEnabled(enabled: Boolean) {
        _receiverState.value = _receiverState.value.copy(alertToneEnabled = enabled)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        _receiverState.value = _receiverState.value.copy(keepAliveEnabled = enabled)
        if (enabled && _receiverState.value.isRunning) {
            LbjKeepAliveService.start(
                getApplication(),
                "SDR-LBJ 信号监听守候中",
                "后台常驻监听服务已启动"
            )
        } else if (!enabled) {
            LbjKeepAliveService.stop(getApplication())
        }
    }

    fun setShowSimulationButton(enabled: Boolean) {
        _receiverState.value = _receiverState.value.copy(showSimulationButton = enabled)
    }

    fun resetAllSettings() {
        setFrequency(DspConstants.DEFAULT_FREQ_HZ / 1_000_000.0)
        setGain(DspConstants.HW_GAIN_DB)
        setPpm(DspConstants.PPM)
        setCsThreshold(DspConstants.DEFAULT_RSSI_THRESHOLD_DB)
        setStrictFilter(true)
        setShowErrWarn(true)
        setFilterMode("highlight")
        setKeywords(emptyList())
        setBroadcastAlerts(true)
        setAlertToneEnabled(false)
        setKeepAliveEnabled(false)
        setShowSimulationButton(false)
    }

    fun setRouteStationKm(routeName: String, stationKm: Double) {
        arrivalEstimator.setRouteKm(routeName, stationKm)
        recomputeEta()
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRouteStationKm(
                RouteStationKmEntity(
                    routeName = routeName,
                    stationKm = stationKm,
                    updatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteRouteStationKm(routeName: String) {
        arrivalEstimator.removeRouteKm(routeName)
        recomputeEta()
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteRouteStationKm(routeName)
        }
    }

    private fun recomputeEta() {
        val t = _liveTelemetry.value
        if (t.trainNo != "----") {
            val eta = arrivalEstimator.estimate(
                train = t.trainNo,
                direction = t.direction,
                speedStr = t.speed,
                positionStr = t.positionKm,
                routeStr = t.route,
                goodData = true,
                nowEpochMs = System.currentTimeMillis()
            )
            _liveEta.value = eta
        }
    }

    fun clearLiveTelemetry() {
        lastDecodedTrainNo = ""
        _liveTelemetry.value = TrainTelemetry()
        _liveEta.value = EtaInfo()
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearAllTrainRecords()
        }
        activeTrainRecordId = null
        activeTrainNo = null
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteTrainRecord(id)
        }
        if (activeTrainRecordId == id) {
            activeTrainRecordId = null
            activeTrainNo = null
        }
    }

    fun launchAndroidDriver() {
        val state = _receiverState.value
        val ok = DriverLauncher.startRtlDriver(
            context = getApplication(),
            host = state.host,
            port = state.port,
            sampleRate = DspConstants.RTL_SAMPLE_RATE,
            freqHz = state.freqHz.toLong()
        )
        if (!ok) {
            _receiverState.value = _receiverState.value.copy(
                warningMessage = "未找到 RTL-SDR 驱动应用，请安装 RTL-SDR Driver 或开启仿真演示模式。"
            )
        }
    }

    fun openDriverAppSettings() {
        DriverLauncher.openDriverAppSettings(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        stopReceiver()
        soundAlertManager.release()
    }
}
