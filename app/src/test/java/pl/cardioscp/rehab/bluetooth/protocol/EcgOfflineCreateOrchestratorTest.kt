package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgOfflineCreateOrchestratorTest {
    @Test
    fun scenario3_createsScpThenEndAndReinit() {
        val orch = EcgOfflineCreateOrchestrator()
        val events = mutableListOf<ScenarioEvent>()
        fun feed(frame: ProtocolFrame) {
            events += orch.onFrame(frame)
        }

        events += orch.start(
            EcgOfflineCreateOrchestrator.Config(
                unixTimestampSeconds = 1L,
                lookbackSeconds = 5,
                totalSeconds = 10,
                userId = "740579",
            ),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, init.sequence))

        val offline = outbound(events).single { it.type == FrameType.ECG_OFFLINE }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, offline.sequence))
        events.clear()

        feed(ProtocolFrame(FrameType.ECG_OFFLINE_DONE, 99))
        val end = outbound(events).single { it.type == FrameType.END }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, end.sequence))

        val reinit = outbound(events).single { it.type == FrameType.INIT }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, reinit.sequence))
        assertTrue(events.any { it is ScenarioEvent.Finished })
        assertEquals(EcgOfflineCreateOrchestrator.Phase.FINISHED, orch.phase)
    }

    private fun outbound(events: List<ScenarioEvent>): List<ProtocolFrame> =
        events.mapNotNull { (it as? ScenarioEvent.Outbound)?.frame }
}
