package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Downloads the **complete** SCP file from the device.
 *
 * Wire protocol still uses `0x0A` fragments; this orchestrator reassembles them
 * and only emits [ScenarioEvent.ScpFileReady] once the full file is in memory.
 * Partial chunks are never exposed to the UI.
 *
 * Flow: GetScpInfo → GetScp → (ACK each fragment) → ScpFileReady(full) → ScpDone → Finished
 */
class ScpDownloadOrchestrator(
    private val commands: CommandFactory = CommandFactory(),
) {
    private enum class AckKind { GET_SCP, SCP_DONE }

    enum class Phase { IDLE, RUNNING, FINISHED, FAILED }

    var phase: Phase = Phase.IDLE
        private set

    private val pendingAcks = mutableMapOf<Int, AckKind>()
    private val scpBuffer = ArrayList<Byte>(64 * 1024)
    private var expectedSize: Long? = null
    private var fileReadyEmitted = false

    fun start(): List<ScenarioEvent> {
        check(phase == Phase.IDLE || phase == Phase.FINISHED || phase == Phase.FAILED)
        pendingAcks.clear()
        scpBuffer.clear()
        expectedSize = null
        fileReadyEmitted = false
        phase = Phase.RUNNING

        val out = mutableListOf<ScenarioEvent>()
        out += ScenarioEvent.Info("Pobieranie całego pliku SCP…")
        out += ScenarioEvent.Outbound(commands.getScpInfo())
        val getScp = commands.getScp()
        pendingAcks[getScp.sequence] = AckKind.GET_SCP
        out += ScenarioEvent.Outbound(getScp)
        return out
    }

    fun onFrame(frame: ProtocolFrame): List<ScenarioEvent> {
        if (phase != Phase.RUNNING) return emptyList()
        val out = mutableListOf<ScenarioEvent>()
        when (frame.type) {
            FrameType.ACK -> handleAck(frame, out)
            FrameType.SCP_INFO -> handleScpInfo(frame, out)
            FrameType.SCP_FRAGMENT -> handleFragment(frame, out)
            FrameType.COMMAND_ERROR -> {
                val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
                fail(
                    when (code) {
                        0x04 -> "Brak pliku SCP na urządzeniu (CmdError 0x04)"
                        else -> "CommandError 0x${code?.toString(16) ?: "?"}"
                    },
                    out,
                )
            }
            FrameType.DEVICE_ERROR -> fail("DeviceError podczas pobierania SCP", out)
            else -> Unit
        }
        return out
    }

    private fun handleAck(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        when (pendingAcks.remove(frame.sequence)) {
            AckKind.GET_SCP -> Unit
            AckKind.SCP_DONE -> {
                phase = Phase.FINISHED
                out += ScenarioEvent.Finished
            }
            null -> Unit
        }
    }

    private fun handleScpInfo(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        val size = PayloadCodec.parseScpInfoSize(frame.payload)
        if (size <= 0L) {
            fail("ScpInfo: rozmiar pliku = $size", out)
            return
        }
        expectedSize = size
        val crc = PayloadCodec.parseScpInfoFileCrc(frame.payload)
        out += ScenarioEvent.Info(
            "SCP na urządzeniu: $size B" + (crc?.let { ", CRC=0x${it.toString(16)}" } ?: ""),
        )
        maybeComplete(out)
    }

    private fun handleFragment(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        // Always ACK each fragment (firmware waits).
        out += ScenarioEvent.Outbound(commands.ack(frame.sequence))
        for (b in frame.payload) scpBuffer.add(b)
        val size = expectedSize
        if (size != null) {
            out += ScenarioEvent.Info(
                "SCP: ${scpBuffer.size} / $size B",
            )
        }
        maybeComplete(out)
    }

    private fun maybeComplete(out: MutableList<ScenarioEvent>) {
        val size = expectedSize ?: return
        if (fileReadyEmitted) return
        if (scpBuffer.size.toLong() < size) return

        // Take exactly the declared file size (drop trailing padding if any).
        val full = ByteArray(size.toInt()) { scpBuffer[it] }
        fileReadyEmitted = true
        out += ScenarioEvent.ScpFileReady(full)

        val done = commands.scpDone()
        pendingAcks[done.sequence] = AckKind.SCP_DONE
        out += ScenarioEvent.Outbound(done)
        out += ScenarioEvent.Info("Cały plik SCP pobrany (${full.size} B), wysyłam ScpDone")
    }

    private fun fail(reason: String, out: MutableList<ScenarioEvent>) {
        phase = Phase.FAILED
        out += ScenarioEvent.Failed(reason)
    }
}
