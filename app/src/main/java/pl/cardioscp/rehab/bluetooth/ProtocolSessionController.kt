package pl.cardioscp.rehab.bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import pl.cardioscp.rehab.bluetooth.protocol.CommandFactory
import pl.cardioscp.rehab.bluetooth.protocol.EcgOfflineCreateOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.EcgOnlineSessionOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.ElectrodeStatus
import pl.cardioscp.rehab.bluetooth.protocol.FrameType
import pl.cardioscp.rehab.bluetooth.protocol.PayloadCodec
import pl.cardioscp.rehab.bluetooth.protocol.ProtocolFrame
import pl.cardioscp.rehab.bluetooth.protocol.PulseScenarioOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.ScenarioEvent
import pl.cardioscp.rehab.bluetooth.protocol.ScpDownloadOrchestrator
import pl.cardioscp.rehab.bluetooth.protocol.SequenceGenerator
import pl.cardioscp.rehab.scp.MinimalScpWriter
import pl.cardioscp.rehab.session.LiveEcgBuffer
import pl.cardioscp.rehab.session.LiveEcgHr
import pl.cardioscp.rehab.session.LiveEcgSnapshot
import pl.cardioscp.rehab.session.SessionEcgTape

/**
 * Runs documented SPP scenarios over a live [EhoMiniDeviceClient] link.
 * Shares one [SequenceGenerator] for the lifetime of the BT session.
 *
 * Offline: [runEcgOfflineCreate] + [runScpDownload].
 * Online: [startOnlineSession] → taśma / fragmenty → [stopOnlineSession] (bez Offline).
 */
class ProtocolSessionController(
    private val client: EhoMiniDeviceClient,
    private val scope: CoroutineScope,
) {
    enum class Scenario { PULSE, ECG_OFFLINE_CREATE, ECG_ONLINE_SESSION, SCP_DOWNLOAD }

    sealed interface Status {
        data object Idle : Status
        data class Running(val scenario: Scenario) : Status
        data class Info(val message: String) : Status
        data class Failed(val reason: String) : Status
        data object Finished : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lastPulseBpm = MutableStateFlow<Int?>(null)
    val lastPulseBpm: StateFlow<Int?> = _lastPulseBpm.asStateFlow()

    private val _pulseSampleCount = MutableStateFlow(0)
    val pulseSampleCount: StateFlow<Int> = _pulseSampleCount.asStateFlow()

    private val _electrodeWarning = MutableStateFlow<String?>(null)
    val electrodeWarning: StateFlow<String?> = _electrodeWarning.asStateFlow()

    private val _electrodeStatus = MutableStateFlow(ElectrodeStatus.unknown())
    val electrodeStatus: StateFlow<ElectrodeStatus> = _electrodeStatus.asStateFlow()

    private val _lastScpBytes = MutableStateFlow<ByteArray?>(null)
    val lastScpBytes: StateFlow<ByteArray?> = _lastScpBytes.asStateFlow()

    private val liveEcgBuffer = LiveEcgBuffer(samplingHz = 250)
    private val _liveEcg = MutableStateFlow(liveEcgBuffer.snapshot())
    val liveEcg: StateFlow<LiveEcgSnapshot> = _liveEcg.asStateFlow()

    private val sessionTape = SessionEcgTape(samplingHz = 250)
    val onlineSessionActive: Boolean
        get() = onlineSessionOrch?.phase == EcgOnlineSessionOrchestrator.Phase.STREAMING

    private val sequences = SequenceGenerator()
    private val commands = CommandFactory(sequences)
    private val scenarioMutex = Mutex()

    private var pulseOrch: PulseScenarioOrchestrator? = null
    private var ecgOrch: EcgOfflineCreateOrchestrator? = null
    private var onlineSessionOrch: EcgOnlineSessionOrchestrator? = null
    private var scpOrch: ScpDownloadOrchestrator? = null
    private var collectJob: Job? = null
    private var electrodeListenJob: Job? = null
    private var electrodePollJob: Job? = null
    private var activeOutcome: CompletableDeferred<Status>? = null
    private var onlineStartedAtMs: Long = 0L
    /** Throttle przeliczania R-R z taśmy Online (ms). */
    private var lastEcgHrAtMs: Long = 0L
    private val ecgHrMinIntervalMs: Long = 500L

    init {
        electrodeListenJob = scope.launch(Dispatchers.IO) {
            client.incomingFrames.collect { frame ->
                noteElectrodeFrame(frame)
            }
        }
    }

    fun resetElectrodes() {
        _electrodeStatus.value = ElectrodeStatus.unknown()
        _electrodeWarning.value = null
        electrodePollJob?.cancel()
        electrodePollJob = null
    }

    /** Poll Get(electrodes) while linked — keeps mannequin in sync outside pulse/ECG streams. */
    fun startElectrodePolling(intervalMs: Long = 4_000L) {
        electrodePollJob?.cancel()
        electrodePollJob = scope.launch(Dispatchers.IO) {
            while (true) {
                runCatching { refreshElectrodes() }
                delay(intervalMs)
            }
        }
    }

    fun stopElectrodePolling() {
        electrodePollJob?.cancel()
        electrodePollJob = null
    }

    /**
     * Query `Get 0x02` (odpięte elektrody). Returns latest [ElectrodeStatus].
     * Safe to call while idle; during an active scenario still works (shared frame bus).
     */
    suspend fun refreshElectrodes(timeoutMs: Long = 3_500L): ElectrodeStatus =
        withContext(Dispatchers.IO) {
            val before = _electrodeStatus.value.updatedAtMs
            val get = commands.get(PayloadCodec.GetInfoId.ELECTRODES)
            runCatching { client.sendFrame(get) }.getOrElse {
                return@withContext _electrodeStatus.value
            }
            withTimeoutOrNull(timeoutMs) {
                client.incomingFrames.first { frame ->
                    if (frame.type != FrameType.GET_ANS || frame.payload.isEmpty()) {
                        return@first false
                    }
                    val ans = PayloadCodec.parseGetAns(frame.payload)
                    if (ans.infoId != PayloadCodec.GetInfoId.ELECTRODES) return@first false
                    applyElectrodeStatus(ElectrodeStatus.fromGetAnsElectrodes(ans.value))
                    true
                }
            }
            // If device answered with same timestamp edge-case, still return current.
            if (_electrodeStatus.value.updatedAtMs == before && before == null) {
                _electrodeStatus.value
            } else {
                _electrodeStatus.value
            }
        }

    /** True when protocol reported all of RA/LA/LF/RF/V1 attached. */
    fun electrodesReady(): Boolean = _electrodeStatus.value.allAttached

    fun startPulseScenario() {
        scope.launch {
            runCatching { runPulseFor(durationSec = 25) }
        }
    }

    fun startEcgOfflineCreateScenario(userId: String, totalSeconds: Int = 10) {
        scope.launch {
            runCatching { runEcgOfflineCreate(userId, totalSeconds) }
        }
    }

    fun startScpDownload() {
        scope.launch {
            runCatching { runScpDownload() }
        }
    }

    /** Init → ECG Offline → Offline Done → End. SCP zostaje na urządzeniu. */
    suspend fun runEcgOfflineCreate(userId: String, totalSeconds: Int = 10) =
        withContext(Dispatchers.IO) {
            scenarioMutex.withLock {
                prepareDevice()
                liveEcgBuffer.reset()
                publishLiveEcg()
                check(!onlineSessionActive) {
                    "Sesja Online aktywna — Offline tylko w trybie Offline (zatrzymaj Online)."
                }
                val orch = EcgOfflineCreateOrchestrator(commands = commands)
                ecgOrch = orch
                onlineSessionOrch = null
                pulseOrch = null
                scpOrch = null
                val outcome = CompletableDeferred<Status>()
                activeOutcome = outcome
                _status.value = Status.Running(Scenario.ECG_OFFLINE_CREATE)
                collectJob?.cancel()
                // Osobny dispatcher — unikamy deadlocku Main (await vs collect).
                collectJob = scope.launch(Dispatchers.IO) {
                    client.incomingFrames.collect { frame ->
                        noteDeviceAlerts(frame)
                        dispatch(orch.onFrame(frame))
                    }
                }
                dispatch(
                    orch.start(
                        EcgOfflineCreateOrchestrator.Config(
                            unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                            samplingHz = 250,
                            pulseAverageSeconds = 10,
                            lookbackSeconds = 0,
                            totalSeconds = totalSeconds.coerceAtLeast(2),
                            userId = userId,
                            reinitAfterEnd = false,
                        ),
                    ),
                )
                val result = withTimeout((totalSeconds + 60L) * 1_000) { outcome.await() }
                activeOutcome = null
                if (result is Status.Failed) error(result.reason)
            }
        }

    /**
     * Start ciągłej sesji Online (raz na sesję rehab). Bez Offline na urządzeniu.
     * Próbki → [liveEcg] + taśma; fragmenty: [captureOnlineFragment].
     */
    suspend fun startOnlineSession() = withContext(Dispatchers.IO) {
        scenarioMutex.withLock {
            if (onlineSessionActive) return@withLock
            prepareDevice()
            liveEcgBuffer.reset()
            sessionTape.reset()
            _lastPulseBpm.value = null
            lastEcgHrAtMs = 0L
            publishLiveEcg()
            val orch = EcgOnlineSessionOrchestrator(commands = commands)
            onlineSessionOrch = orch
            ecgOrch = null
            pulseOrch = null
            scpOrch = null
            _status.value = Status.Running(Scenario.ECG_ONLINE_SESSION)
            collectJob?.cancel()
            collectJob = scope.launch(Dispatchers.IO) {
                client.incomingFrames.collect { frame ->
                    noteDeviceAlerts(frame)
                    dispatch(orch.onFrame(frame))
                }
            }
            dispatch(
                orch.start(
                    EcgOnlineSessionOrchestrator.Config(
                        unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                        samplingHz = 250,
                        pulseAverageSeconds = 10,
                        clearBuffer = true,
                    ),
                ),
            )
            onlineStartedAtMs = System.currentTimeMillis()
            val deadline = System.currentTimeMillis() + 8_000L
            while (System.currentTimeMillis() < deadline) {
                if (orch.isUnsupported) error("ECG Online niedostępne na tym firmware")
                if (orch.isStreaming || sessionTape.availableSamples() > 0) break
                delay(50)
            }
            if (orch.isUnsupported) error("ECG Online niedostępne na tym firmware")
        }
    }

    /**
     * Czeka [seconds] świeżego strumienia, wycina ostatnie [seconds] z taśmy
     * i buduje SCP w aplikacji (bez komendy Offline).
     */
    suspend fun captureOnlineFragment(seconds: Int): ByteArray = withContext(Dispatchers.IO) {
        val orch = onlineSessionOrch
            ?: error("Brak sesji Online — wywołaj startOnlineSession()")
        if (orch.isUnsupported) error("ECG Online niedostępne")
        val secs = seconds.coerceAtLeast(2)
        val need = secs * 250
        val mark = sessionTape.availableSamples()
        val markDeadline = System.currentTimeMillis() + secs * 1_000L
        while (System.currentTimeMillis() < markDeadline) {
            if (orch.isUnsupported || orch.phase == EcgOnlineSessionOrchestrator.Phase.FAILED) {
                error("Strumień Online przerwany")
            }
            delay(50)
        }
        val grown = sessionTape.availableSamples() - mark
        val fragment = sessionTape.cutLastSeconds(secs)
            ?: error("Brak próbek Online do fragmentu (dostępne=${sessionTape.availableSamples()})")
        if (grown < need / 2 && fragment.sampleCount < need / 2) {
            error("Za mało próbek Online (${fragment.sampleCount}) na fragment ${secs}s")
        }
        MinimalScpWriter.fromFragment(fragment)
    }

    /** Puls podczas aktywnej sesji Online (bez End). */
    suspend fun runPulseDuringOnline(durationSec: Int) = withContext(Dispatchers.IO) {
        val orch = onlineSessionOrch ?: error("Brak sesji Online")
        scenarioMutex.withLock {
            _lastPulseBpm.value = null
            _pulseSampleCount.value = 0
            dispatch(orch.startPulse(intervalTenths = 10))
            delay(durationSec.coerceAtLeast(1) * 1_000L)
            dispatch(orch.stopPulse())
            delay(400)
        }
    }

    /** Koniec sesji Online — Stop → End. */
    suspend fun stopOnlineSession() = withContext(Dispatchers.IO) {
        scenarioMutex.withLock {
            val orch = onlineSessionOrch ?: return@withLock
            val outcome = CompletableDeferred<Status>()
            activeOutcome = outcome
            dispatch(orch.stopSession())
            withTimeoutOrNull(15_000) { outcome.await() }
            activeOutcome = null
            onlineSessionOrch = null
            liveEcgBuffer.clearStreaming()
            publishLiveEcg()
            _status.value = Status.Idle
        }
    }

    /** Init → GetScp* → pełny plik → ScpDone → End. */
    suspend fun runScpDownload(): ByteArray =
        withContext(Dispatchers.IO) {
            scenarioMutex.withLock {
                prepareDevice()
                _lastScpBytes.value = null
                val orch = ScpDownloadOrchestrator(commands = commands)
                scpOrch = orch
                pulseOrch = null
                ecgOrch = null
                val outcome = CompletableDeferred<Status>()
                activeOutcome = outcome
                _status.value = Status.Running(Scenario.SCP_DOWNLOAD)
                collectJob?.cancel()
                collectJob = scope.launch(Dispatchers.IO) {
                    client.incomingFrames.collect { frame ->
                        noteDeviceAlerts(frame)
                        dispatch(orch.onFrame(frame))
                    }
                }
                dispatch(
                    orch.start(
                        ScpDownloadOrchestrator.Config(
                            unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                            samplingHz = 250,
                            pulseAverageSeconds = 10,
                        ),
                    ),
                )
                val result = withTimeout(180_000) { outcome.await() }
                activeOutcome = null
                if (result is Status.Failed) error(result.reason)
                _lastScpBytes.value?.takeIf { it.isNotEmpty() }
                    ?: error("Pobrano pusty SCP")
            }
        }

    /**
     * Init → Get Pulse (stream) przez [durationSec] → stop → End.
     * BPM na [lastPulseBpm] w trakcie.
     */
    suspend fun runPulseFor(durationSec: Int) =
        withContext(Dispatchers.IO) {
            scenarioMutex.withLock {
                prepareDevice()
                _lastPulseBpm.value = null
                _pulseSampleCount.value = 0
                val orch = PulseScenarioOrchestrator(
                    commands = commands,
                    autoStopAfterPulses = Int.MAX_VALUE,
                )
                pulseOrch = orch
                ecgOrch = null
                scpOrch = null
                val outcome = CompletableDeferred<Status>()
                activeOutcome = outcome
                _status.value = Status.Running(Scenario.PULSE)
                collectJob?.cancel()
                collectJob = scope.launch(Dispatchers.IO) {
                    client.incomingFrames.collect { frame ->
                        noteDeviceAlerts(frame)
                        if (frame.type == FrameType.PULSE_VALUE && frame.payload.isNotEmpty()) {
                            publishPulse(PayloadCodec.parsePulseValue(frame.payload))
                        }
                        dispatch(orch.onFrame(frame))
                    }
                }
                dispatch(
                    orch.start(
                        PulseScenarioOrchestrator.Config(
                            unixTimestampSeconds = System.currentTimeMillis() / 1000L,
                            samplingHz = 250,
                            pulseAverageSeconds = 10,
                            pulseIntervalTenths = 10,
                        ),
                    ),
                )
                delay(durationSec.coerceAtLeast(1) * 1_000L)
                dispatch(orch.requestStop())
                val result = withTimeout(45_000) { outcome.await() }
                activeOutcome = null
                if (result is Status.Failed) error(result.reason)
            }
        }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        pulseOrch = null
        ecgOrch = null
        onlineSessionOrch = null
        scpOrch = null
        liveEcgBuffer.clearStreaming()
        publishLiveEcg()
        activeOutcome?.let { def ->
            if (!def.isCompleted) def.complete(Status.Failed("Scenariusz przerwany"))
        }
        activeOutcome = null
        _status.value = Status.Idle
    }

    private fun publishLiveEcg() {
        val snap = liveEcgBuffer.snapshot()
        _liveEcg.value = snap
        maybePublishHrFromEcg(snap)
    }

    /**
     * Tętno z detekcji R-R na próbkach Online ([HeartRateStatsEngine]) —
     * nie z GetPulse urządzenia.
     */
    private fun maybePublishHrFromEcg(snap: LiveEcgSnapshot) {
        if (!snap.hasTrace || snap.onlineUnsupported) return
        val now = System.currentTimeMillis()
        if (now - lastEcgHrAtMs < ecgHrMinIntervalMs) return
        lastEcgHrAtMs = now
        val bpm = LiveEcgHr.fromSnapshot(snap) ?: return
        if (bpm > 0) publishPulse(bpm)
    }

    private suspend fun prepareDevice() {
        stop()
        _status.value = Status.Info("Sending End to clear previous Init (if any)")
        val end = commands.end()
        runCatching { client.sendFrame(end) }
        withTimeoutOrNull(1_500) {
            client.incomingFrames.first {
                it.type == FrameType.ACK && it.sequence == end.sequence ||
                    it.type == FrameType.COMMAND_ERROR
            }
        }
        delay(200)
    }

    private fun publishPulse(bpm: Int) {
        _lastPulseBpm.value = bpm
        _pulseSampleCount.value = _pulseSampleCount.value + 1
    }

    private fun noteDeviceAlerts(frame: ProtocolFrame) {
        noteElectrodeFrame(frame)
    }

    private fun noteElectrodeFrame(frame: ProtocolFrame) {
        when (frame.type) {
            FrameType.DEVICE_ERROR -> {
                ElectrodeStatus.fromDevErrorPayload(frame.payload)?.let { applyElectrodeStatus(it) }
            }
            FrameType.GET_ANS -> {
                if (frame.payload.isEmpty()) return
                val ans = runCatching { PayloadCodec.parseGetAns(frame.payload) }.getOrNull() ?: return
                if (ans.infoId == PayloadCodec.GetInfoId.ELECTRODES) {
                    applyElectrodeStatus(ElectrodeStatus.fromGetAnsElectrodes(ans.value))
                }
            }
            else -> Unit
        }
    }

    private fun applyElectrodeStatus(status: ElectrodeStatus) {
        _electrodeStatus.value = status
        _electrodeWarning.value = if (status.detachedSites.isNotEmpty()) {
            status.summaryPl
        } else {
            null
        }
    }

    private suspend fun dispatch(events: List<ScenarioEvent>) {
        for (event in events) {
            when (event) {
                is ScenarioEvent.Outbound -> client.sendFrame(event.frame)
                // GetPulse ignorujemy w sesji Online — BPM tylko z R-R na przebiegu EKG.
                is ScenarioEvent.Pulse -> {
                    if (onlineSessionOrch == null) publishPulse(event.bpm)
                }
                is ScenarioEvent.ScpFileReady -> {
                    _lastScpBytes.value = event.bytes
                    _status.value = Status.Info("Pobrano cały plik SCP (${event.bytes.size} B)")
                }
                is ScenarioEvent.EcgOnlineInfoEvent -> {
                    val labels = PayloadCodec.onlineLeadLabels(event.info)
                    liveEcgBuffer.onInfo(labels)
                    sessionTape.onInfo(labels, event.info.avmNanoVolts)
                    publishLiveEcg()
                }
                is ScenarioEvent.EcgOnlineSamples -> {
                    liveEcgBuffer.append(event.leadLabels, event.samplesMv)
                    val raw = event.samplesRaw
                    if (raw != null) {
                        sessionTape.append(event.leadLabels, raw, event.avmNanoVolts)
                    }
                    publishLiveEcg()
                }
                ScenarioEvent.EcgOnlineUnsupported -> {
                    liveEcgBuffer.markUnsupported()
                    publishLiveEcg()
                    _status.value = Status.Info(
                        "ECG Online niedostępne na tym FW",
                    )
                }
                is ScenarioEvent.Info -> _status.value = Status.Info(event.message)
                is ScenarioEvent.Failed -> {
                    _status.value = Status.Failed(event.reason)
                    completeOutcome(Status.Failed(event.reason))
                }
                ScenarioEvent.Finished -> {
                    _status.value = Status.Finished
                    completeOutcome(Status.Finished)
                }
            }
        }
    }

    private fun completeOutcome(status: Status) {
        val def = activeOutcome ?: return
        if (!def.isCompleted) def.complete(status)
    }
}
