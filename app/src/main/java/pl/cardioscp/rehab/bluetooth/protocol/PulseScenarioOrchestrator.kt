package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Scenario 2: Init → Get Pulse (interval>0) → PulseValues → Get Pulse(0) → final Pulse → End.
 *
 * Firmware (`app_get_pulse`): after stop (interval=0) it ACKs then sends one last Pulse Value.
 */
class PulseScenarioOrchestrator(
    private val commands: CommandFactory = CommandFactory(),
    private val ackPulseValues: Boolean = true,
    /** Pulses to collect before stop; keep ≥ avg window so BPM can leave 0. */
    private val autoStopAfterPulses: Int = 12,
) {
    data class Config(
        val unixTimestampSeconds: Long,
        val samplingHz: Int = 500,
        val pulseAverageSeconds: Int = 10,
        val clearBuffer: Boolean = false,
        val pulseIntervalTenths: Int = 10,
    )

    private enum class AckKind { INIT, GET_PULSE_START, GET_PULSE_STOP, END }

    enum class Phase { IDLE, RUNNING, FINISHED, FAILED }

    var phase: Phase = Phase.IDLE
        private set

    private var config: Config? = null
    private val pendingAcks = mutableMapOf<Int, AckKind>()
    private var pulsesReceived = 0
    private var stopRequested = false
    private var waitingFinalPulse = false

    fun start(config: Config): List<ScenarioEvent> {
        check(phase == Phase.IDLE || phase == Phase.FINISHED || phase == Phase.FAILED)
        require(config.pulseIntervalTenths > 0) { "start interval must be > 0" }
        this.config = config
        pendingAcks.clear()
        pulsesReceived = 0
        stopRequested = false
        waitingFinalPulse = false
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

    fun requestStop(): List<ScenarioEvent> {
        if (phase != Phase.RUNNING || stopRequested) return emptyList()
        return sendStopPulse()
    }

    fun onFrame(frame: ProtocolFrame): List<ScenarioEvent> {
        val out = mutableListOf<ScenarioEvent>()
        when (frame.type) {
            FrameType.ACK -> handleAck(frame, out)
            FrameType.PULSE_VALUE -> handlePulse(frame, out)
            FrameType.COMMAND_ERROR -> {
                val err = PayloadCodec.parseCommandError(frame.payload)
                fail("command error 0x${err.code.toString(16)}", out)
            }
            FrameType.DEVICE_ERROR -> {
                // Electrode disconnect etc. — do not abort; UI reads DeviceError separately.
                val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
                if (code == 0x01) {
                    out += ScenarioEvent.Info("Uwaga: elektrody odpięte (DevError 0x01)")
                }
            }
            else -> Unit
        }
        return out
    }

    private fun handleAck(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        when (pendingAcks.remove(frame.sequence) ?: return) {
            AckKind.INIT -> {
                val cfg = config ?: return
                val pulse = commands.getPulse(cfg.pulseIntervalTenths)
                pendingAcks[pulse.sequence] = AckKind.GET_PULSE_START
                out += ScenarioEvent.Outbound(pulse)
                out += ScenarioEvent.Info("Get Pulse start interval=${cfg.pulseIntervalTenths}")
            }
            AckKind.GET_PULSE_START -> out += ScenarioEvent.Info("Pulse stream armed")
            AckKind.GET_PULSE_STOP -> {
                waitingFinalPulse = true
                out += ScenarioEvent.Info("Waiting final Pulse Value after stop")
            }
            AckKind.END -> {
                phase = Phase.FINISHED
                out += ScenarioEvent.Finished
            }
        }
    }

    private fun handlePulse(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        if (ackPulseValues) {
            out += ScenarioEvent.Outbound(commands.ack(frame.sequence))
        }
        val bpm = PayloadCodec.parsePulseValue(frame.payload)
        out += ScenarioEvent.Pulse(bpm)

        if (waitingFinalPulse) {
            waitingFinalPulse = false
            val end = commands.end()
            pendingAcks[end.sequence] = AckKind.END
            out += ScenarioEvent.Outbound(end)
            return
        }

        pulsesReceived += 1
        if (!stopRequested && pulsesReceived >= autoStopAfterPulses) {
            out += sendStopPulse()
        }
    }

    private fun sendStopPulse(): List<ScenarioEvent> {
        stopRequested = true
        val stop = commands.getPulse(0)
        pendingAcks[stop.sequence] = AckKind.GET_PULSE_STOP
        return listOf(
            ScenarioEvent.Outbound(stop),
            ScenarioEvent.Info("Get Pulse stop (interval=0)"),
        )
    }

    private fun fail(reason: String, out: MutableList<ScenarioEvent>) {
        phase = Phase.FAILED
        out += ScenarioEvent.Failed(reason)
    }
}
