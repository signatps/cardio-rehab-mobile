package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Protocol-only session state matching the BPMN dump:
 * Init → parallel (ECG Offline download loop ‖ Pulse) → End.
 *
 * No UI. Caller feeds inbound frames and sends returned outbound frames over BT.
 */
class OfflineSessionOrchestrator(
    private val commands: CommandFactory = CommandFactory(),
    private val ackPulseValues: Boolean = true,
) {
    enum class Phase {
        IDLE,
        WAIT_INIT_ACK,
        RUNNING,
        WAIT_END_ACK,
        FINISHED,
        FAILED,
    }

    data class EcgJob(
        val lookbackSeconds: Int,
        val totalSeconds: Int,
        val userId: String,
    )

    data class Config(
        val unixTimestampSeconds: Long,
        val samplingHz: Int = 500,
        val pulseAverageSeconds: Int = 10,
        val clearBuffer: Boolean = false,
        val pulseIntervalTenths: Int = 10,
        val ecgJobs: List<EcgJob>,
    )

    sealed interface Event {
        data class Outbound(val frame: ProtocolFrame) : Event
        data class Pulse(val bpm: Int) : Event
        data class ScpCompleted(val bytes: ByteArray) : Event {
            override fun equals(other: Any?): Boolean =
                other is ScpCompleted && bytes.contentEquals(other.bytes)

            override fun hashCode(): Int = bytes.contentHashCode()
        }
        data class Failed(val reason: String) : Event
        data object Finished : Event
    }

    private enum class AckKind {
        INIT,
        GET_PULSE,
        ECG_OFFLINE,
        GET_SCP,
        SCP_DONE,
        END,
    }

    var phase: Phase = Phase.IDLE
        private set

    private var config: Config? = null
    private var ecgIndex = 0
    private val pendingAcks = mutableMapOf<Int, AckKind>()
    private var scpSize: Long? = null
    private val scpBuffer = ArrayList<Byte>()
    private var pulseBranchStarted = false
    private var ecgBranchDone = false
    private var pulseSatisfied = false
    private var waitingOfflineDone = false
    private var fetchingScp = false

    fun start(config: Config): List<Event> {
        check(phase == Phase.IDLE || phase == Phase.FINISHED || phase == Phase.FAILED)
        require(config.ecgJobs.isNotEmpty()) { "at least one ECG job required" }
        this.config = config
        ecgIndex = 0
        pendingAcks.clear()
        scpSize = null
        scpBuffer.clear()
        pulseBranchStarted = false
        ecgBranchDone = false
        pulseSatisfied = false
        waitingOfflineDone = false
        fetchingScp = false
        val init = commands.init(
            config.unixTimestampSeconds,
            config.samplingHz,
            config.pulseAverageSeconds,
            config.clearBuffer,
        )
        pendingAcks[init.sequence] = AckKind.INIT
        phase = Phase.WAIT_INIT_ACK
        return listOf(Event.Outbound(init))
    }

    fun onFrame(frame: ProtocolFrame): List<Event> {
        val out = mutableListOf<Event>()
        when (frame.type) {
            FrameType.ACK -> handleAck(frame, out)
            FrameType.ECG_OFFLINE_DONE -> handleOfflineDone(out)
            FrameType.SCP_INFO -> handleScpInfo(frame, out)
            FrameType.SCP_FRAGMENT -> handleScpFragment(frame, out)
            FrameType.PULSE_VALUE -> handlePulse(frame, out)
            FrameType.COMMAND_ERROR -> {
                val err = PayloadCodec.parseCommandError(frame.payload)
                fail("command error 0x${err.code.toString(16)}", out)
            }
            FrameType.DEVICE_ERROR -> fail("device error", out)
            else -> Unit
        }
        return out
    }

    private fun handleAck(frame: ProtocolFrame, out: MutableList<Event>) {
        val kind = pendingAcks.remove(frame.sequence) ?: return
        when (kind) {
            AckKind.INIT -> {
                phase = Phase.RUNNING
                startParallelBranches(out)
            }
            AckKind.GET_PULSE -> Unit
            AckKind.ECG_OFFLINE -> {
                waitingOfflineDone = true
            }
            AckKind.GET_SCP -> Unit
            AckKind.SCP_DONE -> advanceAfterScpDone(out)
            AckKind.END -> {
                phase = Phase.FINISHED
                out += Event.Finished
            }
        }
    }

    private fun startParallelBranches(out: MutableList<Event>) {
        val cfg = config ?: return
        val pulse = commands.getPulse(cfg.pulseIntervalTenths)
        pendingAcks[pulse.sequence] = AckKind.GET_PULSE
        out += Event.Outbound(pulse)
        pulseBranchStarted = true
        startNextEcg(out)
    }

    private fun startNextEcg(out: MutableList<Event>) {
        val cfg = config ?: return
        if (ecgIndex >= cfg.ecgJobs.size) {
            ecgBranchDone = true
            maybeEnd(out)
            return
        }
        val job = cfg.ecgJobs[ecgIndex]
        val frame = commands.ecgOffline(job.lookbackSeconds, job.totalSeconds, job.userId)
        scpSize = null
        scpBuffer.clear()
        fetchingScp = false
        waitingOfflineDone = false
        pendingAcks[frame.sequence] = AckKind.ECG_OFFLINE
        out += Event.Outbound(frame)
    }

    private fun handleOfflineDone(out: MutableList<Event>) {
        if (!waitingOfflineDone) return
        waitingOfflineDone = false
        fetchingScp = true
        out += Event.Outbound(commands.getScpInfo())
        val getScp = commands.getScp()
        pendingAcks[getScp.sequence] = AckKind.GET_SCP
        out += Event.Outbound(getScp)
    }

    private fun handleScpInfo(frame: ProtocolFrame, out: MutableList<Event>) {
        if (!fetchingScp) return
        scpSize = PayloadCodec.parseScpInfoSize(frame.payload)
        maybeCompleteScp(out)
    }

    private fun handleScpFragment(frame: ProtocolFrame, out: MutableList<Event>) {
        if (!fetchingScp) return
        out += Event.Outbound(commands.ack(frame.sequence))
        for (b in frame.payload) scpBuffer.add(b)
        maybeCompleteScp(out)
    }

    private fun maybeCompleteScp(out: MutableList<Event>) {
        val size = scpSize ?: return
        if (scpBuffer.size.toLong() < size) return
        if (pendingAcks.values.contains(AckKind.SCP_DONE)) return
        val bytes = ByteArray(scpBuffer.size) { scpBuffer[it] }
        out += Event.ScpCompleted(bytes)
        fetchingScp = false
        val done = commands.scpDone()
        pendingAcks[done.sequence] = AckKind.SCP_DONE
        out += Event.Outbound(done)
    }

    private fun advanceAfterScpDone(out: MutableList<Event>) {
        ecgIndex += 1
        val cfg = config ?: return
        if (ecgIndex < cfg.ecgJobs.size) {
            startNextEcg(out)
        } else {
            ecgBranchDone = true
            maybeEnd(out)
        }
    }

    private fun handlePulse(frame: ProtocolFrame, out: MutableList<Event>) {
        if (ackPulseValues) {
            out += Event.Outbound(commands.ack(frame.sequence))
        }
        out += Event.Pulse(PayloadCodec.parsePulseValue(frame.payload))
        pulseSatisfied = true
        maybeEnd(out)
    }

    private fun maybeEnd(out: MutableList<Event>) {
        if (phase != Phase.RUNNING) return
        if (!ecgBranchDone || !pulseSatisfied || !pulseBranchStarted) return
        if (pendingAcks.values.contains(AckKind.END)) return
        val end = commands.end()
        pendingAcks[end.sequence] = AckKind.END
        phase = Phase.WAIT_END_ACK
        out += Event.Outbound(end)
    }

    private fun fail(reason: String, out: MutableList<Event>) {
        phase = Phase.FAILED
        out += Event.Failed(reason)
    }
}
