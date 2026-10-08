package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Scenario 3: Init → ECG Offline → Offline Done → End → Init (restart).
 * SCP file download happens later after reconnect (not in this scenario).
 */
class EcgOfflineCreateOrchestrator(
    private val commands: CommandFactory = CommandFactory(),
) {
    data class Config(
        val unixTimestampSeconds: Long,
        val samplingHz: Int = 500,
        val pulseAverageSeconds: Int = 10,
        val clearBuffer: Boolean = false,
        val lookbackSeconds: Int,
        val totalSeconds: Int,
        val userId: String,
        val reinitAfterEnd: Boolean = true,
    )

    private enum class AckKind { INIT, ECG_OFFLINE, END, REINIT }

    enum class Phase { IDLE, RUNNING, FINISHED, FAILED }

    var phase: Phase = Phase.IDLE
        private set

    private var config: Config? = null
    private val pendingAcks = mutableMapOf<Int, AckKind>()
    private var waitingOfflineDone = false

    fun start(config: Config): List<ScenarioEvent> {
        check(phase == Phase.IDLE || phase == Phase.FINISHED || phase == Phase.FAILED)
        require(config.lookbackSeconds <= config.totalSeconds)
        this.config = config
        pendingAcks.clear()
        waitingOfflineDone = false
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
            FrameType.ECG_OFFLINE_DONE -> handleOfflineDone(out)
            FrameType.COMMAND_ERROR -> {
                val err = PayloadCodec.parseCommandError(frame.payload)
                fail("command error 0x${err.code.toString(16)}", out)
            }
            FrameType.DEVICE_ERROR -> fail("device error", out)
            else -> Unit
        }
        return out
    }

    private fun handleAck(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        when (pendingAcks.remove(frame.sequence) ?: return) {
            AckKind.INIT -> {
                val cfg = config ?: return
                val offline = commands.ecgOffline(cfg.lookbackSeconds, cfg.totalSeconds, cfg.userId)
                pendingAcks[offline.sequence] = AckKind.ECG_OFFLINE
                out += ScenarioEvent.Outbound(offline)
                out += ScenarioEvent.Info(
                    "ECG Offline lookback=${cfg.lookbackSeconds}s total=${cfg.totalSeconds}s",
                )
            }
            AckKind.ECG_OFFLINE -> {
                waitingOfflineDone = true
                out += ScenarioEvent.Info("Waiting for ECG Offline Done")
            }
            AckKind.END -> {
                val cfg = config ?: return
                if (cfg.reinitAfterEnd) {
                    val init = commands.init(
                        System.currentTimeMillis() / 1000L,
                        cfg.samplingHz,
                        cfg.pulseAverageSeconds,
                        clearBuffer = false,
                    )
                    pendingAcks[init.sequence] = AckKind.REINIT
                    out += ScenarioEvent.Outbound(init)
                    out += ScenarioEvent.Info("Re-Init after End")
                } else {
                    phase = Phase.FINISHED
                    out += ScenarioEvent.Finished
                }
            }
            AckKind.REINIT -> {
                phase = Phase.FINISHED
                out += ScenarioEvent.Info("SCP file kept on device until Get SCP after reconnect")
                out += ScenarioEvent.Finished
            }
        }
    }

    private fun handleOfflineDone(out: MutableList<ScenarioEvent>) {
        if (!waitingOfflineDone) return
        waitingOfflineDone = false
        out += ScenarioEvent.Info("ECG Offline Done — SCP created on device")
        val end = commands.end()
        pendingAcks[end.sequence] = AckKind.END
        out += ScenarioEvent.Outbound(end)
    }

    private fun fail(reason: String, out: MutableList<ScenarioEvent>) {
        phase = Phase.FAILED
        out += ScenarioEvent.Failed(reason)
    }
}
