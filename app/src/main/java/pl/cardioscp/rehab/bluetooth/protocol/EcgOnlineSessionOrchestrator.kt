package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Ciągła sesja ECG Online (FW Wojtek) — **bez Offline**:
 *
 * Init → Online → (Info/Data)* przez całą sesję → [opcjonalnie GetPulse] →
 * Online Stop → End.
 *
 * Fragmenty EKG na platformę wycina aplikacja z taśmy ([SessionEcgTape]),
 * nie komenda Offline urządzenia.
 */
class EcgOnlineSessionOrchestrator(
    private val commands: CommandFactory = CommandFactory(),
) {
    data class Config(
        val unixTimestampSeconds: Long,
        val samplingHz: Int = 250,
        val pulseAverageSeconds: Int = 10,
        val clearBuffer: Boolean = true,
    )

    private enum class AckKind {
        INIT,
        ECG_ONLINE,
        GET_PULSE_START,
        GET_PULSE_STOP,
        ECG_ONLINE_STOP,
        END,
    }

    enum class Phase { IDLE, STREAMING, STOPPING, FINISHED, FAILED }

    var phase: Phase = Phase.IDLE
        private set

    private var config: Config? = null
    private val pendingAcks = mutableMapOf<Int, AckKind>()
    private var onlineArmed = false
    private var onlineUnsupported = false
    private var onlineInfo: PayloadCodec.EcgOnlineInfo? = null
    private var sampleCursor = 0
    private var pulseActive = false

    val isStreaming: Boolean get() = phase == Phase.STREAMING && onlineArmed && !onlineUnsupported
    val isUnsupported: Boolean get() = onlineUnsupported

    fun start(config: Config): List<ScenarioEvent> {
        check(phase == Phase.IDLE || phase == Phase.FINISHED || phase == Phase.FAILED)
        this.config = config
        pendingAcks.clear()
        onlineArmed = false
        onlineUnsupported = false
        onlineInfo = null
        sampleCursor = 0
        pulseActive = false
        phase = Phase.STREAMING
        val init = commands.init(
            config.unixTimestampSeconds,
            config.samplingHz,
            config.pulseAverageSeconds,
            config.clearBuffer,
        )
        pendingAcks[init.sequence] = AckKind.INIT
        return listOf(ScenarioEvent.Outbound(init))
    }

    /** Get Pulse na aktywnej sesji Online (bez End / bez ponownego Init). */
    fun startPulse(intervalTenths: Int = 10): List<ScenarioEvent> {
        if (phase != Phase.STREAMING || onlineUnsupported) return emptyList()
        if (pulseActive) return emptyList()
        pulseActive = true
        val pulse = commands.getPulse(intervalTenths.coerceAtLeast(1))
        pendingAcks[pulse.sequence] = AckKind.GET_PULSE_START
        return listOf(
            ScenarioEvent.Outbound(pulse),
            ScenarioEvent.Info("Get Pulse podczas Online interval=$intervalTenths"),
        )
    }

    fun stopPulse(): List<ScenarioEvent> {
        if (!pulseActive) return emptyList()
        val stop = commands.getPulse(0)
        pendingAcks[stop.sequence] = AckKind.GET_PULSE_STOP
        return listOf(
            ScenarioEvent.Outbound(stop),
            ScenarioEvent.Info("Get Pulse stop (Online sesja trwa)"),
        )
    }

    fun stopSession(): List<ScenarioEvent> {
        if (phase != Phase.STREAMING) return emptyList()
        val out = mutableListOf<ScenarioEvent>()
        phase = Phase.STOPPING
        if (pulseActive) {
            out += stopPulse()
        }
        if (onlineArmed || pendingAcks.values.any { it == AckKind.ECG_ONLINE }) {
            pendingAcks.keys.filter { pendingAcks[it] == AckKind.ECG_ONLINE }
                .forEach { pendingAcks.remove(it) }
            val stop = commands.ecgOnlineStop()
            pendingAcks[stop.sequence] = AckKind.ECG_ONLINE_STOP
            out += ScenarioEvent.Outbound(stop)
            out += ScenarioEvent.Info("ECG Online Stop — koniec sesji")
        } else {
            sendEnd(out)
        }
        return out
    }

    fun onFrame(frame: ProtocolFrame): List<ScenarioEvent> {
        val out = mutableListOf<ScenarioEvent>()
        when (frame.type) {
            FrameType.ACK -> handleAck(frame, out)
            FrameType.ECG_ONLINE_INFO -> handleOnlineInfo(frame, out)
            FrameType.ECG_ONLINE_DATA -> handleOnlineData(frame, out)
            FrameType.PULSE_VALUE -> handlePulse(frame, out)
            FrameType.COMMAND_ERROR -> handleCommandError(frame, out)
            FrameType.DEVICE_ERROR -> handleDeviceError(frame, out)
            else -> Unit
        }
        return out
    }

    private fun handleAck(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        when (pendingAcks.remove(frame.sequence) ?: return) {
            AckKind.INIT -> {
                val online = commands.ecgOnline()
                pendingAcks[online.sequence] = AckKind.ECG_ONLINE
                out += ScenarioEvent.Outbound(online)
                out += ScenarioEvent.Info("ECG Online — ciągła sesja (bez Offline)")
            }
            AckKind.ECG_ONLINE -> {
                onlineArmed = true
                out += ScenarioEvent.Info("ECG Online ACK — strumień aktywny")
            }
            AckKind.GET_PULSE_START -> out += ScenarioEvent.Info("Pulse podczas Online — armed")
            AckKind.GET_PULSE_STOP -> {
                pulseActive = false
                out += ScenarioEvent.Info("Pulse stop ACK")
            }
            AckKind.ECG_ONLINE_STOP -> {
                onlineArmed = false
                sendEnd(out)
            }
            AckKind.END -> {
                phase = Phase.FINISHED
                out += ScenarioEvent.Finished
            }
        }
    }

    private fun handlePulse(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        out += ScenarioEvent.Outbound(commands.ack(frame.sequence))
        out += ScenarioEvent.Pulse(PayloadCodec.parsePulseValue(frame.payload))
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
        if (onlineUnsupported || phase != Phase.STREAMING) return
        val hint = onlineInfo?.channelCount ?: 3
        val bits = onlineInfo?.bits ?: 10
        val data = runCatching {
            PayloadCodec.parseEcgOnlineData(frame.payload, channelCountHint = hint, bits = bits)
        }.getOrElse {
            out += ScenarioEvent.Info("Online Data: ${it.message}")
            return
        }
        if (onlineInfo == null) {
            onlineInfo = PayloadCodec.EcgOnlineInfo(
                avmNanoVolts = data.avmNanoVolts,
                channelCount = data.channelCount,
                leadCodes = IntArray(0),
                bits = bits,
            )
            out += ScenarioEvent.EcgOnlineInfoEvent(onlineInfo!!)
        }
        val info = onlineInfo!!
        val labels = PayloadCodec.onlineLeadLabels(info)
        val ch = info.channelCount
        val frames = data.samples.size / ch
        if (frames <= 0) return
        val avm = data.avmNanoVolts.takeIf { it > 0 } ?: info.avmNanoVolts
        val rowsMv = Array(frames) { fi ->
            DoubleArray(ch) { c ->
                PayloadCodec.onlineSampleToMv(data.samples[fi * ch + c], avm)
            }
        }
        val rowsRaw = Array(frames) { fi ->
            ShortArray(ch) { c -> data.samples[fi * ch + c] }
        }
        val first = sampleCursor
        sampleCursor += frames
        onlineArmed = true
        out += ScenarioEvent.EcgOnlineSamples(
            leadLabels = labels,
            samplesMv = rowsMv,
            samplesRaw = rowsRaw,
            avmNanoVolts = avm,
            firstSampleIndex = first,
        )
    }

    private fun handleCommandError(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        val err = PayloadCodec.parseCommandError(frame.payload)
        if (pendingAcks.values.any { it == AckKind.ECG_ONLINE } || onlineArmed) {
            pendingAcks.keys.filter { pendingAcks[it] == AckKind.ECG_ONLINE }
                .forEach { pendingAcks.remove(it) }
            markUnsupported(out, "command error 0x${err.code.toString(16)}")
            sendEnd(out)
            return
        }
        out += ScenarioEvent.Info("command error 0x${err.code.toString(16)}")
    }

    private fun handleDeviceError(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
        if (code == 0x01) {
            out += ScenarioEvent.Info("Uwaga: elektrody odpięte (DevError 0x01)")
            return
        }
        if (code == 0x02 && (onlineArmed || pendingAcks.values.any { it == AckKind.ECG_ONLINE })) {
            pendingAcks.keys.filter { pendingAcks[it] == AckKind.ECG_ONLINE }
                .forEach { pendingAcks.remove(it) }
            markUnsupported(out, "DevError 0x02")
            sendEnd(out)
            return
        }
        out += ScenarioEvent.Info("device async error during Online session")
    }

    private fun markUnsupported(out: MutableList<ScenarioEvent>, detail: String) {
        if (onlineUnsupported) return
        onlineUnsupported = true
        onlineArmed = false
        phase = Phase.FAILED
        out += ScenarioEvent.EcgOnlineUnsupported
        out += ScenarioEvent.Info("ECG Online niedostępne — $detail")
        out += ScenarioEvent.Failed("ECG Online niedostępne ($detail)")
    }

    private fun sendEnd(out: MutableList<ScenarioEvent>) {
        val end = commands.end()
        pendingAcks[end.sequence] = AckKind.END
        out += ScenarioEvent.Outbound(end)
    }
}
