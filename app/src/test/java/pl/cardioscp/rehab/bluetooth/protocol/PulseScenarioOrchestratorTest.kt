package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseScenarioOrchestratorTest {
    @Test
    fun scenario2_waitsForFinalPulseBeforeEnd() {
        val orch = PulseScenarioOrchestrator(autoStopAfterPulses = 2)
        val events = mutableListOf<ScenarioEvent>()
        fun feed(frame: ProtocolFrame) {
            events += orch.onFrame(frame)
        }

        events += orch.start(
            PulseScenarioOrchestrator.Config(
                unixTimestampSeconds = 1L,
                pulseIntervalTenths = 10,
            ),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, init.sequence))

        val startPulse = outbound(events).single { it.type == FrameType.GET_PULSE }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, startPulse.sequence))
        events.clear()

        feed(ProtocolFrame(FrameType.PULSE_VALUE, 50, byteArrayOf(70)))
        feed(ProtocolFrame(FrameType.PULSE_VALUE, 51, byteArrayOf(71)))
        val stop = outbound(events).single { it.type == FrameType.GET_PULSE }
        assertEquals(0, stop.payload[0].toInt() and 0xFF)
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, stop.sequence))
        assertTrue(outbound(events).none { it.type == FrameType.END })
        events.clear()

        // Firmware sends one more pulse after stop ACK.
        feed(ProtocolFrame(FrameType.PULSE_VALUE, 52, byteArrayOf(72)))
        val end = outbound(events).single { it.type == FrameType.END }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, end.sequence))
        assertTrue(events.any { it is ScenarioEvent.Finished })
        assertEquals(PulseScenarioOrchestrator.Phase.FINISHED, orch.phase)
    }

    private fun outbound(events: List<ScenarioEvent>): List<ProtocolFrame> =
        events.mapNotNull { (it as? ScenarioEvent.Outbound)?.frame }
}
