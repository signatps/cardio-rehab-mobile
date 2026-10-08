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
                lookbackSeconds = 0,
                totalSeconds = 10,
                userId = "740579",
            ),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        assertEquals(10, init.payload.size)
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, init.sequence))

        val offline = outbound(events).single { it.type == FrameType.ECG_OFFLINE }
        assertEquals(0, offline.payload[0].toInt() and 0xFF) // lookback
        assertEquals(10, offline.payload[1].toInt() and 0xFF) // total
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

    @Test
    fun lookbackUnavailable_sendsEndToClearInit() {
        val orch = EcgOfflineCreateOrchestrator()
        val events = mutableListOf<ScenarioEvent>()
        fun feed(frame: ProtocolFrame) {
            events += orch.onFrame(frame)
        }

        events += orch.start(
            EcgOfflineCreateOrchestrator.Config(
                unixTimestampSeconds = 1L,
                lookbackSeconds = 0,
                totalSeconds = 10,
                userId = "u",
            ),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, init.sequence))
        val offline = outbound(events).single { it.type == FrameType.ECG_OFFLINE }
        events.clear()

        // Device responds with 0x05 instead of ACK when buffer too small.
        feed(ProtocolFrame(FrameType.COMMAND_ERROR, offline.sequence, byteArrayOf(0x05, 0x02)))
        val end = outbound(events).single { it.type == FrameType.END }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, end.sequence))
        assertTrue(events.any { it is ScenarioEvent.Failed })
        assertEquals(EcgOfflineCreateOrchestrator.Phase.FAILED, orch.phase)
    }

    private fun outbound(events: List<ScenarioEvent>): List<ProtocolFrame> =
        events.mapNotNull { (it as? ScenarioEvent.Outbound)?.frame }
}
