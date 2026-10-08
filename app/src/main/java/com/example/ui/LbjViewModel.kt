package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.data.LbjDatabase
import com.example.data.RouteStationKmEntity
import com.example.data.TrainRecord
import com.example.data.TrainSignalRecord
import com.example.decoder.ArrivalEstimator
import com.example.decoder.EtaInfo
import com.example.decoder.LbjDecoder
import com.example.decoder.LocomotiveDict
import com.example.decoder.TrainTelemetry
import com.example.driver.DriverLauncher
import com.example.driver.RtlTcpClient
import com.example.driver.SignalSimulator
import com.example.dsp.ComplexBuffer
import com.example.dsp.DspConstants
import com.example.dsp.DspFrontend
import com.example.dsp.FftProcessor
import com.example.dsp.RssiGate
import com.example.service.LbjKeepAliveService
import com.example.util.BasebandAudioPlayer
import com.example.util.LbjPreferences
import com.example.util.HistoryCsvCodec
import com.example.util.LbjCsvLogger
import com.example.util.LocomotiveLibraryEntry
import com.example.util.LocomotiveLibraryManager
import com.example.util.LocomotiveLibrarySource
import com.example.util.RailwayMapData
import com.example.util.RailwayMapDataInfo
import com.example.util.RailwayMapDataManager
import com.example.util.SoundAlertManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReceiverConnectionMode {
    SDR,
    TCP
}

data class HistoryImportResult(
    val importedCount: Int,
    val alreadyImported: Boolean = false
)

data class DailyCsvFileInfo(
    val name: String,
    val sizeBytes: Long
)

data class PacketLogItem(
    val id: Long,
    val timestamp: Long,
    val timeFormatted: String,
    val content: String,
    val fullFormattedText: String
)

data class ReceiverState(
    val connectionMode: ReceiverConnectionMode = ReceiverConnectionMode.SDR,
    val isRunning: Boolean = false,
    val isSimulationMode: Boolean = false,
    val connectionState: RtlTcpClient.ConnectionState = RtlTcpClient.ConnectionState.IDLE,
    val host: String = "127.0.0.1",
    val port: Int = 1234,
    val freqHz: Double = DspConstants.DEFAULT_FREQ_HZ,
    val gainDb: Float = DspConstants.HW_GAIN_DB,
    val tunerAgc: Boolean = false,
    val rtlAgc: Boolean = false,
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
    val broadcastAlerts: Boolean = false,
    val alertToneEnabled: Boolean = true,
    val alertNotificationEnabled: Boolean = false,
    val keepAliveEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val showSimulationButton: Boolean = false,
    val showPacketLogTab: Boolean = false,
    val ttsEngineMode: String = "auto",
    val enableExternalAutomation: Boolean = false,
    val themeMode: String = "system",
    val basebandAudioEnabled: Boolean = true,
    val basebandAudioVolume: Int = 50,
    val ttsCacheCount: Int = 0,
    val ttsCacheBytes: Long = 0L,
    val showSignalLossDialog: Boolean = false,
    val spectrumBars: FloatArray = FloatArray(32) { -120.0f },
    val peakFreqHz: Double? = null,
    val peakDeltaHz: Double? = null,
    val peakDb: Float? = null,
    val currentRouteStationKmText: String = "---",
    val fps: Float = 0.0f,
    val isAdcClipping: Boolean = false,
    val showFirstLaunchDriverPrompt: Boolean = false,
    val showDriverInstallGuideDialog: Boolean = false
)

class LbjViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = LbjPreferences(application)
    private val db = LbjDatabase.getDatabase(application)
    private val dao = db.lbjDao()
    private val csvLogger = LbjCsvLogger(application)
    private val locomotiveLibraryManager = LocomotiveLibraryManager(application)
    private val railwayMapDataManager = RailwayMapDataManager(application)

    val historyRecords = dao.getAllTrainRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedRouteKms = dao.getAllRouteStationKms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _receiverState = MutableStateFlow(
        ReceiverState(
            connectionMode = if (prefs.connectionMode == "tcp") {
                ReceiverConnectionMode.TCP
            } else {
                ReceiverConnectionMode.SDR
            },
            host = if (prefs.connectionMode == "tcp") prefs.tcpHost else "127.0.0.1",
            port = if (prefs.connectionMode == "tcp") prefs.tcpPort else 1234,
            freqHz = prefs.freqHz,
            gainDb = prefs.gainDb,
            tunerAgc = prefs.tunerAgc,
            rtlAgc = prefs.rtlAgc,
            ppm = prefs.ppm,
            csThresholdDb = prefs.csThresholdDb,
            strictFilter = prefs.strictFilter,
            showErrWarn = prefs.showErrWarn,
            filterMode = prefs.filterMode,
            keywords = prefs.keywords,
            broadcastAlerts = prefs.broadcastAlerts,
            alertToneEnabled = prefs.alertToneEnabled,
            alertNotificationEnabled = prefs.alertNotificationEnabled,
            keepAliveEnabled = prefs.keepAliveEnabled,
            keepScreenOn = prefs.keepScreenOn,
            showSimulationButton = prefs.showSimulationButton,
            showPacketLogTab = prefs.showPacketLogTab,
            ttsEngineMode = prefs.ttsEngineMode,
            enableExternalAutomation = prefs.enableExternalAutomation,
            themeMode = prefs.themeMode,
            basebandAudioEnabled = prefs.basebandAudioEnabled,
            basebandAudioVolume = prefs.basebandAudioVolume
        )
    )
    val receiverState: StateFlow<ReceiverState> = _receiverState.asStateFlow()

    private val _locomotiveLibraryEntries =
        MutableStateFlow<List<LocomotiveLibraryEntry>>(locomotiveLibraryManager.getEntries())
    val locomotiveLibraryEntries: StateFlow<List<LocomotiveLibraryEntry>> =
        _locomotiveLibraryEntries.asStateFlow()

    private val _locomotiveLibrarySource =
        MutableStateFlow(locomotiveLibraryManager.getSource())
    val locomotiveLibrarySource: StateFlow<LocomotiveLibrarySource> =
        _locomotiveLibrarySource.asStateFlow()

    private val _packetLogs = MutableStateFlow<List<PacketLogItem>>(emptyList())
    val packetLogs: StateFlow<List<PacketLogItem>> = _packetLogs.asStateFlow()

    private val _railwayMapData = MutableStateFlow<RailwayMapData?>(null)
    val railwayMapData: StateFlow<RailwayMapData?> = _railwayMapData.asStateFlow()

    private val _railwayMapDataInfo = MutableStateFlow<RailwayMapDataInfo?>(null)
    val railwayMapDataInfo: StateFlow<RailwayMapDataInfo?> = _railwayMapDataInfo.asStateFlow()

    private val _liveTelemetry = MutableStateFlow(TrainTelemetry())
    val liveTelemetry: StateFlow<TrainTelemetry> = _liveTelemetry.asStateFlow()

    private val _liveEta = MutableStateFlow(EtaInfo())
    val liveEta: StateFlow<EtaInfo> = _liveEta.asStateFlow()

    private val arrivalEstimator = ArrivalEstimator()
    private val rssiGate = RssiGate(onDb = prefs.csThresholdDb)
    private val fftProcessor = FftProcessor()
    private val simulator = SignalSimulator()
    private val basebandAudioPlayer = BasebandAudioPlayer().apply {
        setVolume(prefs.basebandAudioVolume)
    }

    private var dspFrontend = DspFrontend(
        sampleRate = DspConstants.RTL_SAMPLE_RATE.toDouble(),
        dcOffset = DspConstants.DEFAULT_DC_OFFSET_HZ,
        bwHz = DspConstants.DEFAULT_BW_KHZ * 1000.0,
        afcEnable = true
    )

    private val rtlClient = RtlTcpClient(
        host = if (prefs.connectionMode == "tcp") prefs.tcpHost else "127.0.0.1",
        port = if (prefs.connectionMode == "tcp") prefs.tcpPort else 1234,
        initialFreqHz = prefs.freqHz,
        dcOffsetHz = DspConstants.DEFAULT_DC_OFFSET_HZ,
        initialGainDb = prefs.gainDb,
        initialTunerAgc = prefs.tunerAgc,
        initialRtlAgc = prefs.rtlAgc,
        initialPpm = prefs.ppm
    )

    private val decoder = LbjDecoder(
        arrivalEstimator = arrivalEstimator,
        strictFilter = prefs.strictFilter,
        showErrWarn = prefs.showErrWarn,
        filterMode = prefs.filterMode,
        keywords = prefs.keywords
    )

    private var dspJob: Job? = null
    private var fftJob: Job? = null
    private var lastPeakValue: Float? = null
    private var lastPeakChangeTime: Long = 0L
    private var hasShownSignalLossDialog: Boolean = false

    private val fftChannel = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    private val soundAlertManager = SoundAlertManager(getApplication(), viewModelScope).apply {
        onSpeechStateChanged = { isSpeaking ->
            basebandAudioPlayer.setDucked(isSpeaking)
        }
    }
    private var lastDecodedTrainNo: String = ""
    private var lastAlertPlayTime: Long = 0L
    private var currentTrainSignalCount: Int = 0

    // Train Session Tracking (Single history record per train pass)
    private val trainDbMutex = Mutex()
    private var activeTrainRecordId: Long? = null
    private var activeTrainNo: String? = null
    private var lastValidTelemetryTime: Long = 0L

    // Tracks train arrival speech announcements
    private var hasAnnouncedApproach: Boolean = false
    private var lastVoiceAlertTime: Long = 0L
    private var pendingApproachJob: Job? = null

    init {
        locomotiveLibraryManager.applyToDecoder()

        // First-launch driver check
        if (!prefs.hasPromptedDriverInstall) {
            _receiverState.value = _receiverState.value.copy(showFirstLaunchDriverPrompt = true)
        }

        // Refresh TTS audio cache stats
        refreshTtsCacheInfo()

        // Load locally imported railway map data without depending on online OSM.
        viewModelScope.launch(Dispatchers.IO) {
            val data = railwayMapDataManager.load()
            _railwayMapData.value = data
            _railwayMapDataInfo.value = data?.info
        }

        // 3-minute inactivity watchdog: if no telegram updates received within 3 minutes (180s),
        // automatically clear active train information and finalize session
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                checkTrainTelemetryTimeout()
            }
        }

        // Load saved route KM mappings from Room (No dummy seed routes)
        viewModelScope.launch(Dispatchers.IO) {
            val list = dao.getAllRouteStationKmsList()
            for (item in list) {
                arrivalEstimator.setRouteKm(item.routeName, item.stationKm)
            }
        }

        // Configure decoder callbacks
        decoder.onTelemetryUpdated = { telemetry, eta ->
            val now = System.currentTimeMillis()
            lastValidTelemetryTime = now
            _liveTelemetry.value = telemetry
            _liveEta.value = eta

            // Play alert sound & speak announcement immediately upon train detection
            val currentNo = LocomotiveDict.normalizeTrainNo(telemetry.trainNo)
            if (currentNo != "----" && currentNo.isNotBlank()) {
                val isSame = activeTrainNo != null && LocomotiveDict.isSameTrain(activeTrainNo!!, currentNo)
                val isNewTrainSession = !isSame

                if (isNewTrainSession) {
                    currentTrainSignalCount = 1
                    activeTrainNo = currentNo
                    hasAnnouncedApproach = false
                    pendingApproachJob?.cancel()
                    pendingApproachJob = null

                    val hasRichDetails = telemetry.isDetailed && telemetry.locoModel != "----" && telemetry.locoModel.isNotBlank()

                    if (_receiverState.value.alertToneEnabled) {
                        if (hasRichDetails) {
                            // Rich packet received on first shot: mark announced and speak full approach alert immediately
                            hasAnnouncedApproach = true
                            lastVoiceAlertTime = now
                            val speechText = SoundAlertManager.buildTrainAlertSpeechText(
                                locoModel = telemetry.locoModel,
                                route = telemetry.route,
                                direction = telemetry.direction,
                                speedKmH = telemetry.speed,
                                trainNo = currentNo
                            )
                            soundAlertManager.playAlertAndSpeak(speechText, _receiverState.value.ttsEngineMode)
                        } else {
                            // Short packet received first: start double-beep tone immediately for zero-latency alert feedback
                            soundAlertManager.playDoubleBeep()

                            // Schedule approach announcement with 600ms debounce window to absorb the detailed packet
                            pendingApproachJob = viewModelScope.launch {
                                delay(600)
                                val latest = _liveTelemetry.value
                                val bestTrainNo = activeTrainNo ?: currentNo
                                val speechText = SoundAlertManager.buildTrainAlertSpeechText(
                                    locoModel = latest.locoModel,
                                    route = latest.route,
                                    direction = latest.direction,
                                    speedKmH = latest.speed,
                                    trainNo = bestTrainNo
                                )
                                hasAnnouncedApproach = true
                                lastVoiceAlertTime = System.currentTimeMillis()
                                pendingApproachJob = null
                                if (_receiverState.value.alertToneEnabled) {
                                    soundAlertManager.speakText(speechText, _receiverState.value.ttsEngineMode)
                                }
                            }
                        }
                    } else {
                        hasAnnouncedApproach = true
                        lastVoiceAlertTime = now
                    }

                    if (_receiverState.value.alertNotificationEnabled) {
                        sendTrainNotification(
                            trainNo = currentNo,
                            route = telemetry.route,
                            direction = telemetry.direction,
                            locoModel = telemetry.locoModel,
                            speed = telemetry.speed
                        )
                    }
                } else {
                    currentTrainSignalCount++
                    // If current packet has letter prefix while active train was plain digits, upgrade active train name
                    if (currentNo.any { it.isLetter() } && activeTrainNo?.none { it.isLetter() } == true) {
                        activeTrainNo = currentNo
                    }

                    if (!hasAnnouncedApproach) {
                        // Approach announcement hasn't fired yet: cancel pending fallback job and announce approach now
                        val hasRichDetails = telemetry.isDetailed && telemetry.locoModel != "----" && telemetry.locoModel.isNotBlank()
                        pendingApproachJob?.cancel()
                        pendingApproachJob = null
                        hasAnnouncedApproach = true
                        lastVoiceAlertTime = now
                        val bestTrainNo = activeTrainNo ?: currentNo
                        val speechText = SoundAlertManager.buildTrainAlertSpeechText(
                            locoModel = if (hasRichDetails) telemetry.locoModel else _liveTelemetry.value.locoModel,
                            route = if (hasRichDetails) telemetry.route else _liveTelemetry.value.route,
                            direction = if (hasRichDetails) telemetry.direction else _liveTelemetry.value.direction,
                            speedKmH = if (hasRichDetails) telemetry.speed else _liveTelemetry.value.speed,
                            trainNo = bestTrainNo
                        )
                        if (_receiverState.value.alertToneEnabled) {
                            soundAlertManager.speakText(speechText, _receiverState.value.ttsEngineMode)
                        }
                    } else {
                        // Approach announcement already done; check for updates after 45s cooldown
                        val timeSinceLastVoice = now - lastVoiceAlertTime
                        if (timeSinceLastVoice >= 45_000L && !soundAlertManager.isSpeaking() && pendingApproachJob == null) {
                            if (_receiverState.value.alertToneEnabled) {
                                val bestTrainNo = activeTrainNo ?: currentNo
                                val updateSpeechText = SoundAlertManager.buildTrainUpdateSpeechText(bestTrainNo)
                                soundAlertManager.playAlertAndSpeak(updateSpeechText, _receiverState.value.ttsEngineMode)
                                lastVoiceAlertTime = now
                            }
                        } else {
                            if (currentTrainSignalCount % 4 == 0) {
                                if (_receiverState.value.alertToneEnabled) {
                                    soundAlertManager.playSubtlePeriodicBeep()
                                }
                            }
                        }
                    }
                }

                // Update foreground keep alive service notification if enabled
                if (_receiverState.value.keepAliveEnabled && _receiverState.value.isRunning) {
                    LbjKeepAliveService.update(
                        getApplication(),
                        "已探测列车: ${activeTrainNo ?: telemetry.trainNo} (${telemetry.direction})",
                        "机车: ${telemetry.locoModel} | 线路: ${telemetry.route}"
                    )
                }

                // Send Android broadcast if enabled
                if (_receiverState.value.broadcastAlerts) {
                    DriverLauncher.sendAlertBroadcast(
                        getApplication(),
                        train = activeTrainNo ?: telemetry.trainNo,
                        direction = telemetry.direction,
                        speed = telemetry.speed,
                        position = telemetry.positionKm,
                        loco = telemetry.locoModel,
                        locoCode = telemetry.locoCode,
                        route = telemetry.route,
                        category = telemetry.category
                    )
                }

                // Train history remains one record per train pass.
                // In parallel, every telemetry callback is stored as one separate signal record.
                // There is intentionally no content-based deduplication here.
                viewModelScope.launch(Dispatchers.IO) {
                    trainDbMutex.withLock {
                        val baseNo = LocomotiveDict.extractBaseTrainNumber(currentNo)
                        val nowSeen = now

                        // 每次收到一条 telemetry 都追加到当天 CSV；完全相同的信号也保留。
                        csvLogger.append(telemetry, nowSeen)

                        if (isNewTrainSession) {
                            // Finalize previous train record if any
                            activeTrainRecordId?.let { prevId ->
                                dao.updateLastSeenTime(prevId, nowSeen)
                            }

                            // Check if there is an existing session for the same train in DB within 10 minutes
                            val recentRecord = dao.findRecentTrainSession(
                                trainNo = currentNo,
                                baseTrainNo = baseNo,
                                minTime = nowSeen - 10 * 60 * 1000L
                            )

                            if (recentRecord != null) {
                                // Resume and merge into existing session
                                activeTrainRecordId = recentRecord.id
                                val bestTrainNo = if (currentNo.any { it.isLetter() }) currentNo else recentRecord.trainNo
                                dao.updateFullTrainRecord(
                                    id = recentRecord.id,
                                    trainNo = bestTrainNo,
                                    direction = telemetry.direction,
                                    locoModel = telemetry.locoModel,
                                    locoCode = telemetry.locoCode,
                                    route = telemetry.route,
                                    category = telemetry.category,
                                    lastSeenTime = nowSeen
                                )
                            } else {
                                val newRecord = TrainRecord(
                                    trainNo = currentNo,
                                    direction = telemetry.direction,
                                    locoModel = telemetry.locoModel,
                                    locoCode = telemetry.locoCode,
                                    route = telemetry.route,
                                    category = telemetry.category,
                                    firstSeenTime = nowSeen,
                                    lastSeenTime = nowSeen
                                )
                                val insertedId = dao.insertTrainRecord(newRecord)
                                activeTrainRecordId = insertedId
                            }
                        } else {
                            // Existing train session continuation
                            var recordId = activeTrainRecordId
                            if (recordId == null) {
                                val recentRecord = dao.findRecentTrainSession(
                                    trainNo = currentNo,
                                    baseTrainNo = baseNo,
                                    minTime = nowSeen - 10 * 60 * 1000L
                                )
                                if (recentRecord != null) {
                                    recordId = recentRecord.id
                                    activeTrainRecordId = recentRecord.id
                                }
                            }

                            if (recordId != null) {
                                val bestTrainNo = if (currentNo.any { it.isLetter() }) currentNo else (activeTrainNo ?: currentNo)
                                dao.updateFullTrainRecord(
                                    id = recordId,
                                    trainNo = bestTrainNo,
                                    direction = telemetry.direction,
                                    locoModel = telemetry.locoModel,
                                    locoCode = telemetry.locoCode,
                                    route = telemetry.route,
                                    category = telemetry.category,
                                    lastSeenTime = nowSeen
                                )
                            } else {
                                val newRecord = TrainRecord(
                                    trainNo = currentNo,
                                    direction = telemetry.direction,
                                    locoModel = telemetry.locoModel,
                                    locoCode = telemetry.locoCode,
                                    route = telemetry.route,
                                    category = telemetry.category,
                                    firstSeenTime = nowSeen,
                                    lastSeenTime = nowSeen
                                )
                                val insertedId = dao.insertTrainRecord(newRecord)
                                activeTrainRecordId = insertedId
                            }
                        }

                        // Exactly one DB row for this telemetry callback.
                        // Identical consecutive signals are intentionally retained.
                        activeTrainRecordId?.let { trainRecordId ->
                            dao.insertTrainSignalRecord(
                                TrainSignalRecord(
                                    trainRecordId = trainRecordId,
                                    trainNo = currentNo,
                                    direction = telemetry.direction,
                                    speed = telemetry.speed,
                                    locoModel = telemetry.locoModel,
                                    locoCode = telemetry.locoCode,
                                    route = telemetry.route,
                                    positionKm = telemetry.positionKm,
                                    category = telemetry.category,
                                    longitude = telemetry.longitude,
                                    latitude = telemetry.latitude,
                                    timestamp = nowSeen
                                )
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
            // Keep warning for user-specified 4 seconds duration; auto-cleared in DSP loop timer
        }

        decoder.onPacketLog = { content ->
            addPacketLog(content)
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
            warningMessage = if (isSimulation) "已开启 RF 信号仿真流演示模式 (每15秒模拟报文)" else ""
        )

        if (!isSimulation) {
            if (_receiverState.value.connectionMode == ReceiverConnectionMode.SDR) {
                // SDR 模式：先联动启动手机 RTL-SDR 驱动，再连接本地 RTL-TCP 服务。
                try {
                    launchAndroidDriver()
                } catch (_: Exception) {}
            }
            // TCP 模式：直接连接用户配置的远端 RTL-TCP 服务，不启动本机驱动。
            rtlClient.open()
        }

        if (_receiverState.value.basebandAudioEnabled) {
            basebandAudioPlayer.start()
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
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}
            try {
                runDspLoop(isSimulation)
            } catch (_: Exception) {}
        }

        // Dedicated FFT worker coroutine on a separate thread pool to distribute load to other CPU cores
        fftJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
            } catch (_: Exception) {}
            try {
                runFftWorker()
            } catch (_: Exception) {}
        }
    }

    private val fftBuffer = ComplexBuffer(512)

    private suspend fun runFftWorker() {
        for (unit in fftChannel) {
            if (!_receiverState.value.isRunning) break
            val curState = _receiverState.value
            val hwFreq = curState.freqHz - curState.dcOffsetHz
            val fftRes = fftProcessor.process(
                iqBuffer = fftBuffer,
                sampleRate = DspConstants.RTL_SAMPLE_RATE.toDouble(),
                hwFreqHz = hwFreq,
                targetFreqHz = curState.freqHz,
                bwKhz = curState.bwKhz
            )

            // Update spectrum & peak metrics without blocking the main DSP demodulation thread
            _receiverState.value = _receiverState.value.copy(
                spectrumBars = fftRes.bandsDb.clone(),
                peakFreqHz = fftRes.peakInfo.peakFreqHz,
                peakDeltaHz = fftRes.peakInfo.peakDeltaHz,
                peakDb = fftRes.peakInfo.peakDb
            )
        }
    }

    fun stopReceiver() {
        dspJob?.cancel()
        dspJob = null
        fftJob?.cancel()
        fftJob = null
        rtlClient.close()
        basebandAudioPlayer.stop()

        // Finalize active train session in DB
        finalizeActiveTrainSession()

        lastDecodedTrainNo = ""
        currentTrainSignalCount = 0
        clearLiveTelemetry()

        // Always stop keep alive service and clean up notifications when stopping receiver
        LbjKeepAliveService.stop(getApplication())

        _receiverState.value = _receiverState.value.copy(
            isRunning = false,
            isSimulationMode = false,
            warningMessage = "",
            fps = 0.0f,
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
        activeTrainRecordId = null
        activeTrainNo = null
        currentTrainSignalCount = 0
        hasAnnouncedApproach = false
        lastVoiceAlertTime = 0L
        pendingApproachJob?.cancel()
        pendingApproachJob = null
        if (id != null) {
            val now = System.currentTimeMillis()
            viewModelScope.launch(Dispatchers.IO) {
                trainDbMutex.withLock {
                    dao.updateLastSeenTime(id, now)
                }
            }
        }
    }

    private fun checkTrainTelemetryTimeout(nowMs: Long = System.currentTimeMillis()) {
        if (_liveTelemetry.value.trainNo != "----" && lastValidTelemetryTime > 0L) {
            if (nowMs - lastValidTelemetryTime >= 180_000L) {
                finalizeActiveTrainSession()
                currentTrainSignalCount = 0
                clearLiveTelemetry()
                if (_receiverState.value.keepAliveEnabled) {
                    LbjKeepAliveService.update(
                        getApplication(),
                        "SDR-LBJ 信号监听守候中",
                        "等待下一趟列车报文"
                    )
                }
            }
        }
    }

    fun dismissSignalLossDialog() {
        _receiverState.value = _receiverState.value.copy(showSignalLossDialog = false)
    }

    private suspend fun runDspLoop(isSimulation: Boolean) {
        val resetAfcOnRelease = true
        var lastUiUpdateTime = 0L
        var lastFftTriggerTime = 0L
        var nextSimTime = System.currentTimeMillis()
        var lastFpsCalcTime = System.currentTimeMillis()
        var frameCountInSec = 0
        var currentFps = 0.0f
        var lastClippingDetectedTime = 0L

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
                    val isConnectionRefused = _receiverState.value.warningMessage.contains("连接被拒") ||
                            _receiverState.value.connectionState == RtlTcpClient.ConnectionState.ERROR ||
                            _receiverState.value.connectionState == RtlTcpClient.ConnectionState.DISCONNECTED
                    if (nowMs - lastPeakChangeTime >= 3000L && !hasShownSignalLossDialog && !_receiverState.value.isSimulationMode && !isConnectionRefused) {
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

            // Check if train telemetry has timed out (>= 180s without next update packet)
            checkTrainTelemetryTimeout(nowMs)

            // Auto-clear transient warning messages (BCH/interference: 4s; ADC clipping/overload: 10s)
            val curWarn = _receiverState.value.warningMessage
            if (curWarn.isNotEmpty()) {
                val warnAge = nowMs - _receiverState.value.warningTime
                val isBchWarn = curWarn.contains("BCH") || curWarn.contains("干扰")
                val isAdcWarn = curWarn.contains("削波") || curWarn.contains("过载") || curWarn.contains("过强")
                if ((isBchWarn && warnAge >= 4000L) || (isAdcWarn && warnAge >= 10000L)) {
                    _receiverState.value = _receiverState.value.copy(warningMessage = "")
                }
            }

            // 1. Offload FFT & spectrum processing to dedicated background thread pool (throttled ~10 Hz to prevent CPU starvation on low-end CPUs)
            if (nowMs - lastFftTriggerTime >= 100L) {
                lastFftTriggerTime = nowMs
                val fftN = minOf(512, iq.size)
                System.arraycopy(iq.real, 0, fftBuffer.real, 0, fftN)
                System.arraycopy(iq.imag, 0, fftBuffer.imag, 0, fftN)
                fftChannel.trySend(Unit)
            }

            // Detect peak freeze in real SDR reception mode (3 seconds with no change)
            if (!isSimulation) {
                val isConnectionRefused = _receiverState.value.warningMessage.contains("连接被拒") ||
                        _receiverState.value.connectionState == RtlTcpClient.ConnectionState.ERROR ||
                        _receiverState.value.connectionState == RtlTcpClient.ConnectionState.DISCONNECTED
                val curPeak = _receiverState.value.peakDb
                if (curPeak == null || lastPeakValue == null || kotlin.math.abs(curPeak - (lastPeakValue ?: 0f)) > 0.001f) {
                    lastPeakValue = curPeak
                    lastPeakChangeTime = nowMs
                } else {
                    if (nowMs - lastPeakChangeTime >= 3000L && !hasShownSignalLossDialog && !isConnectionRefused) {
                        hasShownSignalLossDialog = true
                        _receiverState.value = _receiverState.value.copy(showSignalLossDialog = true)
                    }
                }
            }

            // Check for raw ADC saturation/clipping (RTL2832U 8-bit ADC saturates at ±127/128, normalized to ±1.0)
            var clipCount = 0
            val checkN = minOf(1024, iq.size)
            for (ci in 0 until checkN) {
                if (kotlin.math.abs(iq.real[ci]) >= 0.98f || kotlin.math.abs(iq.imag[ci]) >= 0.98f) {
                    clipCount++
                }
            }
            val isBlockClipping = (clipCount > (checkN * 0.03))
            if (isBlockClipping) {
                lastClippingDetectedTime = nowMs
            }

            // 2. Process DSP frontend chain (DDC, Halfband, FIR Decimation, FM Demod)
            val dspRes = dspFrontend.process(iq, rssiGate)

            if (dspRes.rssiDb >= -15.0f) {
                lastClippingDetectedTime = nowMs
            }
            // ADC clipping flag remains active for 10 seconds after detection, then automatically clears
            val activeClipping = (nowMs - lastClippingDetectedTime < 10000L)

            // Auto-warn when ADC clipping/saturation occurs
            if (activeClipping && _receiverState.value.warningMessage.isEmpty()) {
                _receiverState.value = _receiverState.value.copy(
                    warningMessage = "⚠ 射频信号过强 (RSSI接近-10dB)，ADC削波失真，建议降低硬件增益",
                    warningTime = nowMs
                )
            }

            // Stream baseband audio (analog radio static / demodulated audio) to speaker if enabled
            if (_receiverState.value.basebandAudioEnabled) {
                basebandAudioPlayer.writeSamples(dspRes.pcmFloat)
            }

            // 3. Check AFC update
            if (dspFrontend.consumeAfcUpdated()) {
                decoder.resetDpllSoft()
            }

            // 4. Feed baseband PCM to slicer & decoder continuously to avoid chopping off preamble & sync words
            decoder.processAudioChunk(dspRes.pcmFloat)

            if (rssiGate.justDeactivated) {
                if (resetAfcOnRelease && dspFrontend.afc.enabled) {
                    dspFrontend.resetAfc()
                }
            }

            if (!isSimulation) {
                rtlClient.recycleBuffer(iq)
            }

            frameCountInSec++
            if (nowMs - lastFpsCalcTime >= 1000L) {
                currentFps = (frameCountInSec * 1000.0f) / (nowMs - lastFpsCalcTime)
                frameCountInSec = 0
                lastFpsCalcTime = nowMs
            }

            // Update UI state with 100ms throttle for responsive demodulation metrics without overwhelming Compose on older Android versions
            val stateChanged = rssiGate.justActivated || rssiGate.justDeactivated
            if (stateChanged || nowMs - lastUiUpdateTime >= 100L) {
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
                    currentRouteStationKmText = routeKmText,
                    fps = currentFps,
                    isAdcClipping = activeClipping
                )
            }
        }
    }

    // Tuning controls
    fun setConnectionMode(mode: ReceiverConnectionMode) {
        val current = _receiverState.value.connectionMode
        if (current == mode) return

        if (_receiverState.value.isRunning) {
            stopReceiver()
        } else {
            rtlClient.close()
        }

        if (mode == ReceiverConnectionMode.TCP) {
            rtlClient.setEndpoint(prefs.tcpHost, prefs.tcpPort)
            _receiverState.value = _receiverState.value.copy(
                connectionMode = mode,
                host = prefs.tcpHost,
                port = prefs.tcpPort,
                connectionState = RtlTcpClient.ConnectionState.IDLE
            )
            prefs.connectionMode = "tcp"
        } else {
            rtlClient.setEndpoint("127.0.0.1", 1234)
            _receiverState.value = _receiverState.value.copy(
                connectionMode = mode,
                host = "127.0.0.1",
                port = 1234,
                connectionState = RtlTcpClient.ConnectionState.IDLE
            )
            prefs.connectionMode = "sdr"
        }
    }

    fun setTcpEndpoint(host: String, port: Int): String? {
        val normalizedHost = host.trim()
        if (normalizedHost.isEmpty()) return "TCP 地址不能为空"
        if (port !in 1..65535) return "TCP 端口必须在 1~65535"

        if (_receiverState.value.isRunning) {
            stopReceiver()
        }

        return try {
            rtlClient.setEndpoint(normalizedHost, port)
            prefs.tcpHost = normalizedHost
            prefs.tcpPort = port
            _receiverState.value = _receiverState.value.copy(
                host = normalizedHost,
                port = port,
                connectionState = RtlTcpClient.ConnectionState.IDLE
            )
            null
        } catch (e: Exception) {
            e.message ?: "TCP 地址设置失败"
        }
    }

    fun testTcpConnection() {
        if (_receiverState.value.connectionMode != ReceiverConnectionMode.TCP) {
            _receiverState.value = _receiverState.value.copy(
                warningMessage = "请先切换到 TCP 连接模式。"
            )
            return
        }

        if (_receiverState.value.isRunning) {
            _receiverState.value = _receiverState.value.copy(
                warningMessage = "正在接收中，请先停止接收再测试 TCP 连接。"
            )
            return
        }

        val host = _receiverState.value.host.trim()
        val port = _receiverState.value.port
        if (host.isEmpty()) {
            _receiverState.value = _receiverState.value.copy(
                connectionState = RtlTcpClient.ConnectionState.ERROR,
                warningMessage = "TCP 主机地址不能为空。"
            )
            return
        }

        _receiverState.value = _receiverState.value.copy(
            connectionState = RtlTcpClient.ConnectionState.CONNECTING,
            warningMessage = ""
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 3000)
                }
                withContext(Dispatchers.Main) {
                    _receiverState.value = _receiverState.value.copy(
                        connectionState = RtlTcpClient.ConnectionState.IDLE,
                        warningMessage = "TCP 测试连接成功：$host:$port"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _receiverState.value = _receiverState.value.copy(
                        connectionState = RtlTcpClient.ConnectionState.ERROR,
                        warningMessage = "TCP 测试失败：$host:$port — ${e.message ?: "无法建立连接"}"
                    )
                }
            }
        }
    }

    fun setFrequency(freqMhz: Double) {
        val freqHz = freqMhz * 1_000_000.0
        prefs.freqHz = freqHz
        _receiverState.value = _receiverState.value.copy(freqHz = freqHz)
        rtlClient.setFrequency(freqHz)
        dspFrontend.resetAfc()
        rssiGate.reset()
    }

    fun setGain(gainDb: Float) {
        prefs.gainDb = gainDb
        _receiverState.value = _receiverState.value.copy(gainDb = gainDb)
        rtlClient.setGain(gainDb)
    }

    fun setTunerAgc(enabled: Boolean) {
        prefs.tunerAgc = enabled
        _receiverState.value = _receiverState.value.copy(tunerAgc = enabled)
        rtlClient.setTunerAgc(enabled)
    }

    fun setRtlAgc(enabled: Boolean) {
        prefs.rtlAgc = enabled
        _receiverState.value = _receiverState.value.copy(rtlAgc = enabled)
        rtlClient.setRtlAgc(enabled)
    }

    fun setPpm(ppm: Int) {
        prefs.ppm = ppm
        _receiverState.value = _receiverState.value.copy(ppm = ppm)
        rtlClient.setPpm(ppm)
        dspFrontend.resetAfc()
        rssiGate.reset()
    }

    fun setCsThreshold(thresholdDb: Float) {
        prefs.csThresholdDb = thresholdDb
        _receiverState.value = _receiverState.value.copy(csThresholdDb = thresholdDb)
        rssiGate.setThreshold(thresholdDb)
    }

    fun setStrictFilter(enabled: Boolean) {
        prefs.strictFilter = enabled
        _receiverState.value = _receiverState.value.copy(strictFilter = enabled)
        decoder.strictFilter = enabled
    }

    fun setShowErrWarn(enabled: Boolean) {
        prefs.showErrWarn = enabled
        _receiverState.value = _receiverState.value.copy(showErrWarn = enabled)
        decoder.showErrWarn = enabled
    }

    fun setFilterMode(mode: String) {
        prefs.filterMode = mode
        _receiverState.value = _receiverState.value.copy(filterMode = mode)
        decoder.filterMode = mode
    }

    fun setKeywords(kwList: List<String>) {
        prefs.keywords = kwList
        _receiverState.value = _receiverState.value.copy(keywords = kwList)
        decoder.keywords = kwList
    }

    fun setBroadcastAlerts(enabled: Boolean) {
        prefs.broadcastAlerts = enabled
        _receiverState.value = _receiverState.value.copy(broadcastAlerts = enabled)
    }

    fun setAlertToneEnabled(enabled: Boolean) {
        prefs.alertToneEnabled = enabled
        _receiverState.value = _receiverState.value.copy(alertToneEnabled = enabled)
    }

    fun setAlertNotificationEnabled(enabled: Boolean) {
        prefs.alertNotificationEnabled = enabled
        _receiverState.value = _receiverState.value.copy(alertNotificationEnabled = enabled)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        prefs.keepAliveEnabled = enabled
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

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.keepScreenOn = enabled
        _receiverState.value = _receiverState.value.copy(keepScreenOn = enabled)
    }

    fun setShowSimulationButton(enabled: Boolean) {
        prefs.showSimulationButton = enabled
        _receiverState.value = _receiverState.value.copy(showSimulationButton = enabled)
    }

    fun setShowPacketLogTab(enabled: Boolean) {
        prefs.showPacketLogTab = enabled
        _receiverState.value = _receiverState.value.copy(showPacketLogTab = enabled)
    }

    fun addPacketLog(content: String, timestamp: Long = System.currentTimeMillis()) {
        val sdf = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date(timestamp))
        val full = "$timeStr 接到报文：\n$content"
        val item = PacketLogItem(
            id = System.nanoTime(),
            timestamp = timestamp,
            timeFormatted = timeStr,
            content = content,
            fullFormattedText = full
        )
        _packetLogs.update { current ->
            (listOf(item) + current).take(500)
        }
    }

    fun clearPacketLogs() {
        _packetLogs.value = emptyList()
    }

    fun setTtsEngineMode(mode: String) {
        prefs.ttsEngineMode = mode
        _receiverState.value = _receiverState.value.copy(ttsEngineMode = mode)
    }

    fun setEnableExternalAutomation(enabled: Boolean) {
        prefs.enableExternalAutomation = enabled
        _receiverState.value = _receiverState.value.copy(enableExternalAutomation = enabled)
    }

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _receiverState.value = _receiverState.value.copy(themeMode = mode)
    }

    fun setBasebandAudioEnabled(enabled: Boolean) {
        prefs.basebandAudioEnabled = enabled
        _receiverState.value = _receiverState.value.copy(basebandAudioEnabled = enabled)
        if (enabled) {
            if (_receiverState.value.isRunning) {
                basebandAudioPlayer.start()
            }
        } else {
            basebandAudioPlayer.stop()
        }
    }

    fun setBasebandAudioVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        prefs.basebandAudioVolume = clamped
        _receiverState.value = _receiverState.value.copy(basebandAudioVolume = clamped)
        basebandAudioPlayer.setVolume(clamped)
    }

    fun refreshTtsCacheInfo() {
        val (count, bytes) = soundAlertManager.getTtsCacheInfo()
        _receiverState.value = _receiverState.value.copy(
            ttsCacheCount = count,
            ttsCacheBytes = bytes
        )
    }

    fun clearTtsCache(): Pair<Int, Long> {
        val result = soundAlertManager.clearTtsCache()
        refreshTtsCacheInfo()
        return result
    }

    fun resetAllSettings() {
        prefs.resetAll()
        prefs.connectionMode = "sdr"
        prefs.tcpHost = "127.0.0.1"
        prefs.tcpPort = 1234
        if (!_receiverState.value.isRunning) {
            rtlClient.setEndpoint("127.0.0.1", 1234)
            _receiverState.value = _receiverState.value.copy(
                connectionMode = ReceiverConnectionMode.SDR,
                host = "127.0.0.1",
                port = 1234,
                connectionState = RtlTcpClient.ConnectionState.IDLE
            )
        }
        setFrequency(DspConstants.DEFAULT_FREQ_HZ / 1_000_000.0)
        setTunerAgc(false)
        setRtlAgc(false)
        setGain(DspConstants.HW_GAIN_DB)
        setPpm(DspConstants.PPM)
        setCsThreshold(DspConstants.DEFAULT_RSSI_THRESHOLD_DB)
        setStrictFilter(true)
        setShowErrWarn(true)
        setFilterMode("highlight")
        setKeywords(emptyList())
        setBroadcastAlerts(false)
        setAlertToneEnabled(true)
        setAlertNotificationEnabled(false)
        setKeepAliveEnabled(false)
        setKeepScreenOn(false)
        setShowSimulationButton(false)
        setShowPacketLogTab(false)
        setTtsEngineMode("auto")
        setEnableExternalAutomation(false)
        setThemeMode("system")
        setBasebandAudioEnabled(false)
        setBasebandAudioVolume(50)
    }

    fun setRouteStationKm(routeName: String, stationKm: Double, nickname: String = "") {
        arrivalEstimator.setRouteKm(routeName, stationKm)
        recomputeEta()
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRouteStationKm(
                RouteStationKmEntity(
                    routeName = routeName,
                    stationKm = stationKm,
                    nickname = nickname,
                    updatedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun importRouteStationKms(routes: List<RouteStationKmEntity>) {
        if (routes.isEmpty()) return
        routes.forEach {
            arrivalEstimator.setRouteKm(it.routeName, it.stationKm)
        }
        recomputeEta()
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertRouteStationKms(routes)
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
        lastValidTelemetryTime = 0L
        _liveTelemetry.value = TrainTelemetry()
        _liveEta.value = EtaInfo()
        decoder.clearCurrentTrain()
        hasAnnouncedApproach = false
        lastVoiceAlertTime = 0L
        pendingApproachJob?.cancel()
        pendingApproachJob = null
    }

    fun clearWarning() {
        _receiverState.value = _receiverState.value.copy(warningMessage = "")
    }

    fun getDailyCsvFiles(): List<DailyCsvFileInfo> {
        return csvLogger.listDailyFiles().map {
            DailyCsvFileInfo(it.name, it.sizeBytes)
        }
    }

    suspend fun exportDailyCsvFile(name: String, uri: Uri) = withContext(Dispatchers.IO) {
        val file = csvLogger.getDailyFile(name)
            ?: throw IllegalArgumentException("找不到指定的每日 CSV 文件")
        val resolver = getApplication<Application>().contentResolver
        val inputBytes = file.readBytes()
        resolver.openOutputStream(uri)?.use { output ->
            output.write(inputBytes)
            output.flush()
        } ?: throw IllegalStateException("无法打开导出文件")
    }

    fun getTrainSignalRecords(trainRecordId: Long): Flow<List<TrainSignalRecord>> {
        return dao.getTrainSignalRecords(trainRecordId)
    }

    fun selectLocomotiveLibrary(source: LocomotiveLibrarySource) {
        locomotiveLibraryManager.setSource(source)
        refreshLocomotiveLibrary()
    }

    fun saveLocomotiveEntry(code: Int, name: String) {
        locomotiveLibraryManager.addOrUpdate(code, name)
        refreshLocomotiveLibrary()
    }

    fun deleteLocomotiveEntry(code: Int) {
        locomotiveLibraryManager.delete(code)
        refreshLocomotiveLibrary()
    }

    suspend fun importLocomotiveLibrary(uri: Uri): Int = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法打开车型库文件")
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            String(bytes, Charset.forName("GB18030"))
        }

        val count = locomotiveLibraryManager.importText(text)
        refreshLocomotiveLibrary()
        count
    }

    suspend fun exportLocomotiveLibrary(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = locomotiveLibraryManager.exportText()
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: throw IllegalStateException("无法打开车型库导出文件")
        locomotiveLibraryManager.getEntries().size
    }

    private fun refreshLocomotiveLibrary() {
        _locomotiveLibraryEntries.value = locomotiveLibraryManager.getEntries()
        _locomotiveLibrarySource.value = locomotiveLibraryManager.getSource()
    }

    suspend fun exportHistoryCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val records = dao.getAllTrainSignalRecordsList()
        val csv = HistoryCsvCodec.encode(records)
        val resolver = getApplication<Application>().contentResolver
        resolver.openOutputStream(uri)?.use { output ->
            output.write(csv.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: throw IllegalStateException("无法打开导出文件")
        records.size
    }

    suspend fun importHistoryCsv(uri: Uri): HistoryImportResult = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法打开导入文件")

        val fingerprint = sha256Hex(bytes)
        if (prefs.hasImportedHistoryCsv(fingerprint)) {
            return@withContext HistoryImportResult(
                importedCount = 0,
                alreadyImported = true
            )
        }

        val utf8 = bytes.toString(Charsets.UTF_8)
        val text = if (utf8.contains("时间,车次,方向,速度")) {
            utf8
        } else {
            bytes.toString(Charset.forName("GB18030"))
        }
        val rows = HistoryCsvCodec.parse(text)
        val importedCount = importHistoryRows(rows)
        prefs.markHistoryCsvImported(fingerprint)
        HistoryImportResult(importedCount = importedCount)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val builder = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            builder.append("%02x".format(Locale.ROOT, byte.toInt() and 0xFF))
        }
        return builder.toString()
    }

    private suspend fun importHistoryRows(rows: List<HistoryCsvCodec.Row>): Int {
        if (rows.isEmpty()) return 0

        return db.withTransaction {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sortedRows = rows.sortedWith(
                compareBy<HistoryCsvCodec.Row> { it.timestamp }.thenBy { it.sourceIndex }
            )
            val groups = sortedRows.groupBy { row ->
                listOf(
                    row.trainNo,
                    row.direction,
                    dateFormat.format(Date(row.timestamp))
                ).joinToString("\u001F")
            }

            var importedCount = 0

            groups.values.forEach { group ->
                val first = group.first()
                val last = group.last()
                val metadata = group.asReversed()

                val trainRecordId = dao.insertTrainRecord(
                    TrainRecord(
                        trainNo = first.trainNo,
                        direction = first.direction,
                        locoModel = latestUseful(metadata) { it.locoModel } ?: first.locoModel,
                        locoCode = latestUseful(metadata) { it.locoCode } ?: first.locoCode,
                        route = latestUseful(metadata) { it.route } ?: first.route,
                        category = latestUseful(metadata) { it.category } ?: first.category,
                        firstSeenTime = first.timestamp,
                        lastSeenTime = last.timestamp
                    )
                )

                group.forEach { row ->
                    dao.insertTrainSignalRecord(
                        TrainSignalRecord(
                            trainRecordId = trainRecordId,
                            trainNo = row.trainNo,
                            direction = row.direction,
                            speed = row.speed,
                            locoModel = row.locoModel,
                            locoCode = row.locoCode,
                            route = row.route,
                            positionKm = row.positionKm,
                            category = row.category,
                            longitude = row.longitude,
                            latitude = row.latitude,
                            timestamp = row.timestamp
                        )
                    )
                    importedCount++
                }
            }

            importedCount
        }
    }

    private fun latestUseful(
        rows: List<HistoryCsvCodec.Row>,
        selector: (HistoryCsvCodec.Row) -> String
    ): String? {
        return rows.asSequence()
            .map(selector)
            .firstOrNull { value ->
                value.isNotBlank() &&
                    value != "----" &&
                    value != "---" &&
                    value != "****" &&
                    value != "未知"
            }
    }

    suspend fun importRailwayMapData(uri: Uri): RailwayMapDataInfo = withContext(Dispatchers.IO) {
        val data = railwayMapDataManager.importFromUri(uri)
        _railwayMapData.value = data
        _railwayMapDataInfo.value = data.info
        data.info
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            trainDbMutex.withLock {
                dao.clearAllTrainSignalRecords()
                dao.clearAllTrainRecords()
            }
        }
        activeTrainRecordId = null
        activeTrainNo = null
    }

    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            trainDbMutex.withLock {
                dao.deleteTrainSignalRecords(id)
                dao.deleteTrainRecord(id)
            }
        }
        if (activeTrainRecordId == id) {
            activeTrainRecordId = null
            activeTrainNo = null
        }
    }

    fun launchAndroidDriver() {
        if (_receiverState.value.connectionMode != ReceiverConnectionMode.SDR) {
            _receiverState.value = _receiverState.value.copy(
                warningMessage = "当前为 TCP 连接模式，不启动本机 RTL-SDR 驱动。"
            )
            return
        }

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
                warningMessage = "未找到 RTL-SDR 驱动应用，已为您弹出内置驱动安装引导。",
                showDriverInstallGuideDialog = true
            )
        }
    }

    fun openDriverAppSettings() {
        DriverLauncher.openDriverAppSettings(getApplication())
    }

    fun onUserConfirmDriverAlreadyInstalled() {
        prefs.hasPromptedDriverInstall = true
        _receiverState.value = _receiverState.value.copy(showFirstLaunchDriverPrompt = false)
    }

    fun onUserSelectDriverNotInstalled() {
        prefs.hasPromptedDriverInstall = true
        _receiverState.value = _receiverState.value.copy(
            showFirstLaunchDriverPrompt = false,
            showDriverInstallGuideDialog = true
        )
    }

    fun openDriverInstallGuide() {
        _receiverState.value = _receiverState.value.copy(showDriverInstallGuideDialog = true)
    }

    fun dismissDriverInstallGuide() {
        _receiverState.value = _receiverState.value.copy(showDriverInstallGuideDialog = false)
    }

    fun dismissFirstLaunchDriverPrompt() {
        prefs.hasPromptedDriverInstall = true
        _receiverState.value = _receiverState.value.copy(showFirstLaunchDriverPrompt = false)
    }

    fun installDriverApk(): Pair<Boolean, String?> {
        _receiverState.value = _receiverState.value.copy(showDriverInstallGuideDialog = false)
        val result = DriverLauncher.installDriverApk(getApplication())
        if (!result.first) {
            _receiverState.value = _receiverState.value.copy(
                warningMessage = "驱动安装失败: ${result.second ?: "请检查权限或文件"}"
            )
        }
        return result
    }

    fun isDriverInstalled(): Boolean {
        return DriverLauncher.isDriverInstalled(getApplication())
    }

    private var testVoiceSampleIndex = 0

    fun testVoiceBroadcast() {
        val samples = listOf(
            Triple("HXD3D-5033", "G102", "310"),
            Triple("CR400AF-2001", "G1", "350"),
            Triple("FXD1-J-0001", "D727", "160"),
            Triple("DF4D-1000", "K8401", "120")
        )
        val currentSample = samples[testVoiceSampleIndex % samples.size]
        testVoiceSampleIndex++

        val sampleText = SoundAlertManager.buildTrainAlertSpeechText(
            locoModel = currentSample.first,
            route = "京沪高铁",
            direction = "下行",
            speedKmH = currentSample.third,
            trainNo = currentSample.second
        )
        soundAlertManager.playAlertAndSpeak(sampleText, _receiverState.value.ttsEngineMode)

        if (_receiverState.value.alertNotificationEnabled) {
            sendTrainNotification(
                trainNo = currentSample.second,
                route = "京沪高铁",
                direction = "下行",
                locoModel = currentSample.first,
                speed = currentSample.third
            )
        }
    }

    private fun sendTrainNotification(
        trainNo: String,
        route: String,
        direction: String,
        locoModel: String,
        speed: String
    ) {
        try {
            val app = getApplication<Application>()
            val notificationManager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            
            val channelId = "lbj_train_alert_notification_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "SDR-LBJ 来车提醒通知",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "发现来车报文时弹出的即时提醒通知"
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val routeStr = if (route.isNotBlank() && route != "----") route else "线路"
            val directionStr = if (direction.isNotBlank() && direction != "----") direction else ""
            val routePart = if (directionStr.isNotBlank()) "$routeStr-$directionStr" else routeStr

            val title = "车次：$trainNo | $routePart"
            val locoStr = if (locoModel.isNotBlank() && locoModel != "----") locoModel else "未知机车"
            val speedStr = if (speed.isNotBlank() && speed != "----") speed else "0"
            val content = "机车：$locoStr | 速度：$speedStr KM/H"

            val launchIntent = android.content.Intent(app, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                app,
                System.currentTimeMillis().toInt(),
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(app, channelId)
                .setSmallIcon(com.example.R.drawable.ic_lbj_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                .build()

            val notificationId = 2000 + (System.currentTimeMillis() % 1000).toInt()
            notificationManager.notify(notificationId, notification)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        stopReceiver()
        soundAlertManager.release()
    }
}