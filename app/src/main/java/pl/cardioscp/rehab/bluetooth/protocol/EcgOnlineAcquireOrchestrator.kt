package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Tryb Online + archiwum SCP:
 * Init → ECG Online + ECG Offline → (Online Info / Data)* → Offline Done →
 * Online Stop → End.
 *
 * Gdy FW zwraca DevError/CmdError na Online — emituje [ScenarioEvent.EcgOnlineUnsupported]
 * i dokańcza Offline jak zwykle (bez podglądu).
 */
class EcgOnlineAcquireOrchestrator(
    private val commands: CommandFactory = CommandFactory(),
) {
    data class Config(
        val unixTimestampSeconds: Long,
        val samplingHz: Int = 250,
        val pulseAverageSeconds: Int = 10,
        val clearBuffer: Boolean = false,
        val lookbackSeconds: Int = 0,
        val totalSeconds: Int,
        val userId: String,
    )

    private enum class AckKind { INIT, ECG_ONLINE, ECG_OFFLINE, ECG_ONLINE_STOP, END, END_AFTER_ERROR }

    enum class Phase { IDLE, RUNNING, FINISHED, FAILED }

    var phase: Phase = Phase.IDLE
        private set

    private var config: Config? = null
    private val pendingAcks = mutableMapOf<Int, AckKind>()
    private var waitingOfflineDone = false
    private var onlineArmed = false
    private var onlineUnsupported = false
    private var onlineInfo: PayloadCodec.EcgOnlineInfo? = null
    private var onlineStopSent = false

    fun start(config: Config): List<ScenarioEvent> {
        check(phase == Phase.IDLE || phase == Phase.FINISHED || phase == Phase.FAILED)
        require(config.totalSeconds > config.lookbackSeconds) {
            "firmware requires totalSeconds > lookbackSeconds"
        }
        this.config = config
        pendingAcks.clear()
        waitingOfflineDone = false
        onlineArmed = false
        onlineUnsupported = false
        onlineInfo = null
        onlineStopSent = false
        phase = Phase.RUNNING
        val init = commands.init(
            config.unixTimestampSeconds,
            config.samplingHz,
            config.pulseAverageSeconds,
            config.clearBuffer,
        )
        pendingAcks[init.sequence] = AckKind.INIT
        return listOf(ScenarioEvent.Outbound(init))
    }

    fun onFrame(frame: ProtocolFrame): List<ScenarioEvent> {
        val out = mutableListOf<ScenarioEvent>()
        when (frame.type) {
            FrameType.ACK -> handleAck(frame, out)
            FrameType.ECG_ONLINE_INFO -> handleOnlineInfo(frame, out)
            FrameType.ECG_ONLINE_DATA -> handleOnlineData(frame, out)
            FrameType.ECG_OFFLINE_DONE -> handleOfflineDone(out)
            FrameType.COMMAND_ERROR -> handleCommandError(frame, out)
            FrameType.DEVICE_ERROR -> handleDeviceError(frame, out)
            else -> Unit
        }
        return out
    }

    private fun handleAck(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        when (pendingAcks.remove(frame.sequence) ?: return) {
            AckKind.INIT -> {
                val cfg = config ?: return
                val online = commands.ecgOnline()
                pendingAcks[online.sequence] = AckKind.ECG_ONLINE
                out += ScenarioEvent.Outbound(online)
                out += ScenarioEvent.Info("ECG Online — start strumienia")

                val offline = commands.ecgOffline(cfg.lookbackSeconds, cfg.totalSeconds, cfg.userId)
                pendingAcks[offline.sequence] = AckKind.ECG_OFFLINE
                out += ScenarioEvent.Outbound(offline)
                out += ScenarioEvent.Info(
                    "ECG Offline lookback=${cfg.lookbackSeconds}s total=${cfg.totalSeconds}s",
                )
            }
            AckKind.ECG_ONLINE -> {
                onlineArmed = true
                out += ScenarioEvent.Info("ECG Online ACK — czekam na Online Info/Data")
            }
            AckKind.ECG_OFFLINE -> {
                waitingOfflineDone = true
                out += ScenarioEvent.Info("Waiting for ECG Offline Done")
            }
            AckKind.ECG_ONLINE_STOP -> {
                sendEnd(out, AckKind.END)
            }
            AckKind.END -> {
                phase = Phase.FINISHED
                out += ScenarioEvent.Finished
            }
            AckKind.END_AFTER_ERROR -> {
                phase = Phase.FAILED
                out += ScenarioEvent.Failed("ECG acquire failed; End sent to clear Init")
            }
        }
    }

    private fun handleOnlineInfo(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        val info = runCatching { PayloadCodec.parseEcgOnlineInfo(frame.payload) }.getOrElse {
            out += ScenarioEvent.Info("Online Info: ${it.message}")
            return
        }
        onlineInfo = info
        onlineArmed = true
        out += ScenarioEvent.EcgOnlineInfoEvent(info)
        out += ScenarioEvent.Info(
            "Online Info: ${info.channelCount} ch, AVM=${info.avmNanoVolts}",
        )
    }

    private fun handleOnlineData(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        val info = onlineInfo ?: return
        val data = runCatching {
            PayloadCodec.parseEcgOnlineData(frame.payload, info.channelCount)
        }.getOrElse {
            out += ScenarioEvent.Info("Online Data: ${it.message}")
            return
        }
        val labels = info.leadCodes.map { PayloadCodec.scpLeadLabel(it) }
        val frames = data.samples.size / info.channelCount
        if (frames <= 0) return
        val rows = Array(frames) { fi ->
            DoubleArray(info.channelCount) { ch ->
                val raw = data.samples[fi * info.channelCount + ch]
                PayloadCodec.onlineSampleToMv(raw, info.avmNanoVolts)
            }
        }
        out += ScenarioEvent.EcgOnlineSamples(
            leadLabels = labels,
            samplesMv = rows,
            firstSampleIndex = data.firstSampleIndex,
        )
    }

    private fun handleOfflineDone(out: MutableList<ScenarioEvent>) {
        if (!waitingOfflineDone) return
        waitingOfflineDone = false
        out += ScenarioEvent.Info("ECG Offline Done — SCP created on device")
        if (onlineArmed && !onlineUnsupported && !onlineStopSent) {
            onlineStopSent = true
            val stop = commands.ecgOnlineStop()
            pendingAcks[stop.sequence] = AckKind.ECG_ONLINE_STOP
            out += ScenarioEvent.Outbound(stop)
            out += ScenarioEvent.Info("ECG Online Stop")
        } else {
            sendEnd(out, AckKind.END)
        }
    }

    private fun handleCommandError(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        val err = PayloadCodec.parseCommandError(frame.payload)
        val onlinePending = pendingAcks.entries.any { it.value == AckKind.ECG_ONLINE }
        if (onlinePending || onlineArmed) {
            pendingAcks.keys.filter { pendingAcks[it] == AckKind.ECG_ONLINE }
                .forEach { pendingAcks.remove(it) }
            markOnlineUnsupported(out, "command error 0x${err.code.toString(16)}")
            // Offline może nadal trwać — nie kończymy całej akwizycji.
            if (!waitingOfflineDone && pendingAcks.none { it.value == AckKind.ECG_OFFLINE }) {
                // Offline też padł / nie wystartował
                failOffline(out, "command error 0x${err.code.toString(16)}")
            }
            return
        }
        failOffline(out, "command error 0x${err.code.toString(16)}")
    }

    private fun handleDeviceError(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
        if (code == 0x01) {
            out += ScenarioEvent.Info("Uwaga: elektrody odpięte (DevError 0x01)")
            return
        }
        // FW EHO-MINI: Online → DevError 0x02 „CMD NOT SUPPORTED”
        if (code == 0x02 && (onlineArmed || pendingAcks.values.any { it == AckKind.ECG_ONLINE })) {
            pendingAcks.keys.filter { pendingAcks[it] == AckKind.ECG_ONLINE }
                .forEach { pendingAcks.remove(it) }
            markOnlineUnsupported(out, "DevError 0x02 (Online nieobsługiwane na FW)")
            return
        }
        out += ScenarioEvent.Info("device async error during ECG online acquire")
    }

    private fun markOnlineUnsupported(out: MutableList<ScenarioEvent>, detail: String) {
        if (onlineUnsupported) return
        onlineUnsupported = true
        onlineArmed = false
        out += ScenarioEvent.EcgOnlineUnsupported
        out += ScenarioEvent.Info("ECG Online niedostępne — $detail; kontynuuję Offline/SCP")
    }

    private fun failOffline(out: MutableList<ScenarioEvent>, detail: String) {
        pendingAcks.keys
            .filter { pendingAcks[it] == AckKind.ECG_OFFLINE }
            .forEach { pendingAcks.remove(it) }
        waitingOfflineDone = false
        out += ScenarioEvent.Info(detail)
        sendEnd(out, AckKind.END_AFTER_ERROR)
    }

    private fun sendEnd(out: MutableList<ScenarioEvent>, kind: AckKind) {
        val end = commands.end()
        pendingAcks[end.sequence] = kind
        out += ScenarioEvent.Outbound(end)
    }
}
