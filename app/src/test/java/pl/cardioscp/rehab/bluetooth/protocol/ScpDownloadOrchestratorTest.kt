package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScpDownloadOrchestratorTest {
    @Test
    fun initThenReassemblesFragmentsIntoSingleCompleteFile() {
        val orch = ScpDownloadOrchestrator()
        val events = mutableListOf<ScenarioEvent>()

        fun feed(frame: ProtocolFrame) {
            events += orch.onFrame(frame)
        }

        events += orch.start(
            ScpDownloadOrchestrator.Config(unixTimestampSeconds = 1_700_000_000L),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, init.sequence))
        val afterInit = outbound(events)
        assertTrue(afterInit.any { it.type == FrameType.GET_SCP_INFO })
        val getScp = afterInit.single { it.type == FrameType.GET_SCP }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, getScp.sequence))
        feed(
            ProtocolFrame(
                FrameType.SCP_INFO,
                10,
                byteArrayOf(5, 0, 0, 0, 0, 0x11, 0x22),
            ),
        )
        events.clear()

        feed(ProtocolFrame(FrameType.SCP_FRAGMENT, 11, byteArrayOf(1, 2)))
        assertTrue(outbound(events).any { it.type == FrameType.ACK && it.sequence == 11 })
        assertTrue(events.none { it is ScenarioEvent.ScpFileReady })
        events.clear()

        feed(ProtocolFrame(FrameType.SCP_FRAGMENT, 12, byteArrayOf(3, 4, 5)))
        val ready = events.filterIsInstance<ScenarioEvent.ScpFileReady>().single()
        assertTrue(byteArrayOf(1, 2, 3, 4, 5).contentEquals(ready.bytes))
        val done = outbound(events).single { it.type == FrameType.SCP_DONE }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, done.sequence))
        val end = outbound(events).single { it.type == FrameType.END }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, end.sequence))
        assertTrue(events.any { it is ScenarioEvent.Finished })
        assertEquals(ScpDownloadOrchestrator.Phase.FINISHED, orch.phase)
    }

    @Test
    fun missingScp_commandError04() {
        val orch = ScpDownloadOrchestrator()
        val events = mutableListOf<ScenarioEvent>()
        events += orch.start(ScpDownloadOrchestrator.Config(unixTimestampSeconds = 1L))
        val init = outbound(events).single { it.type == FrameType.INIT }
        events.clear()
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, init.sequence))
        events.clear()
        events += orch.onFrame(ProtocolFrame(FrameType.COMMAND_ERROR, 1, byteArrayOf(0x04)))
        val failed = events.filterIsInstance<ScenarioEvent.Failed>().single()
        assertTrue(failed.reason.contains("0x04"))
        assertEquals(ScpDownloadOrchestrator.Phase.FAILED, orch.phase)
    }

    private fun outbound(events: List<ScenarioEvent>): List<ProtocolFrame> =
        events.mapNotNull { (it as? ScenarioEvent.Outbound)?.frame }
}
