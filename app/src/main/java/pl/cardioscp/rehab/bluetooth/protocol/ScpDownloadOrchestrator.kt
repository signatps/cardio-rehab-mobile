package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Downloads the **complete** SCP file from the device.
 *
 * Firmware requires `app_init_flag` for GetScpInfo / GetScp / ScpDone
 * (`SEND APP INIT CMD BEFOR APP_GET_SCP_INFO`).
 *
 * Flow: Init → GetScpInfo → GetScp → (ACK each fragment) → ScpFileReady(full)
 * → ScpDone → End → Finished
 *
 * Partial `0x0A` chunks are never exposed — only the reassembled file.
 */
class ScpDownloadOrchestrator(
    private val commands: CommandFactory = CommandFactory(),
) {
    private enum class AckKind { INIT, GET_SCP, SCP_DONE, END }

    enum class Phase { IDLE, RUNNING, FINISHED, FAILED }

    var phase: Phase = Phase.IDLE
        private set

    private var config: Config? = null
    private val pendingAcks = mutableMapOf<Int, AckKind>()
    private val scpBuffer = ArrayList<Byte>(64 * 1024)
    private var expectedSize: Long? = null
    private var fileReadyEmitted = false

    data class Config(
        val unixTimestampSeconds: Long,
        val samplingHz: Int = 500,
        val pulseAverageSeconds: Int = 10,
        val clearBuffer: Boolean = false,
    )

    fun start(config: Config): List<ScenarioEvent> {
        check(phase == Phase.IDLE || phase == Phase.FINISHED || phase == Phase.FAILED)
        this.config = config
        pendingAcks.clear()
        scpBuffer.clear()
        expectedSize = null
        fileReadyEmitted = false
        phase = Phase.RUNNING

        val init = commands.init(
            config.unixTimestampSeconds,
            config.samplingHz,
            config.pulseAverageSeconds,
            config.clearBuffer,
        )
        pendingAcks[init.sequence] = AckKind.INIT
        return listOf(
            ScenarioEvent.Info("Init przed pobraniem SCP…"),
            ScenarioEvent.Outbound(init),
        )
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
            FrameType.DEVICE_ERROR -> {
                val code = frame.payload.firstOrNull()?.toInt()?.and(0xFF)
                if (code == 0x01) {
                    // Electrode alert — ignore during transfer.
                    out += ScenarioEvent.Info("Uwaga: elektrody (DevError 0x01)")
                } else {
                    fail(
                        "DeviceError 0x${code?.toString(16) ?: "?"} — zwykle brak Init przed GetScp",
                        out,
                    )
                }
            }
            else -> Unit
        }
        return out
    }

    private fun handleAck(frame: ProtocolFrame, out: MutableList<ScenarioEvent>) {
        when (pendingAcks.remove(frame.sequence)) {
            AckKind.INIT -> {
                out += ScenarioEvent.Info("Pobieranie całego pliku SCP…")
                out += ScenarioEvent.Outbound(commands.getScpInfo())
                val getScp = commands.getScp()
                pendingAcks[getScp.sequence] = AckKind.GET_SCP
                out += ScenarioEvent.Outbound(getScp)
            }
            AckKind.GET_SCP -> Unit
            AckKind.SCP_DONE -> {
                val end = commands.end()
                pendingAcks[end.sequence] = AckKind.END
                out += ScenarioEvent.Outbound(end)
                out += ScenarioEvent.Info("ScpDone OK — End")
            }
            AckKind.END -> {
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
        out += ScenarioEvent.Outbound(commands.ack(frame.sequence))
        for (b in frame.payload) scpBuffer.add(b)
        val size = expectedSize
        if (size != null) {
            out += ScenarioEvent.Info("SCP: ${scpBuffer.size} / $size B")
        }
        maybeComplete(out)
    }

    private fun maybeComplete(out: MutableList<ScenarioEvent>) {
        val size = expectedSize ?: return
        if (fileReadyEmitted) return
        if (scpBuffer.size.toLong() < size) return

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
        // Best-effort End so next scenario can Init cleanly.
        out += ScenarioEvent.Outbound(commands.end())
        out += ScenarioEvent.Failed(reason)
    }
}
