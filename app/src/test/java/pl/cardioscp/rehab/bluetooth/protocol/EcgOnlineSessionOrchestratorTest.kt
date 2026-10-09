package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgOnlineSessionOrchestratorTest {
    @Test
    fun startStreamsWithoutOfflineAndStopsCleanly() {
        val orch = EcgOnlineSessionOrchestrator()
        val events = mutableListOf<ScenarioEvent>()
        events += orch.start(
            EcgOnlineSessionOrchestrator.Config(unixTimestampSeconds = 1L),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, init.sequence))
        assertTrue(outbound(events).none { it.type == FrameType.ECG_OFFLINE })
        val online = outbound(events).single { it.type == FrameType.ECG_ONLINE }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, online.sequence))

        events += orch.onFrame(
            ProtocolFrame(FrameType.ECG_ONLINE_INFO, 1, byteArrayOf(0xBC.toByte(), 0x1B, 0x03)),
        )
        assertTrue(events.any { it is ScenarioEvent.EcgOnlineInfoEvent })

        events += orch.stopSession()
        val stop = outbound(events).single { it.type == FrameType.ECG_ONLINE_STOP }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, stop.sequence))
        val end = outbound(events).last { it.type == FrameType.END }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, end.sequence))
        assertEquals(EcgOnlineSessionOrchestrator.Phase.FINISHED, orch.phase)
        assertTrue(outbound(events).none { it.type == FrameType.ECG_OFFLINE })
    }

    @Test
    fun deviceErrorMarksUnsupportedWithoutOffline() {
        val orch = EcgOnlineSessionOrchestrator()
        val events = mutableListOf<ScenarioEvent>()
        events += orch.start(EcgOnlineSessionOrchestrator.Config(unixTimestampSeconds = 1L))
        val init = outbound(events).single { it.type == FrameType.INIT }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, init.sequence))
        val online = outbound(events).single { it.type == FrameType.ECG_ONLINE }
        events += orch.onFrame(
            ProtocolFrame(FrameType.DEVICE_ERROR, online.sequence, byteArrayOf(0x02)),
        )
        assertTrue(events.any { it is ScenarioEvent.EcgOnlineUnsupported })
        assertTrue(outbound(events).none { it.type == FrameType.ECG_OFFLINE })
    }

    private fun outbound(events: List<ScenarioEvent>): List<ProtocolFrame> =
        events.filterIsInstance<ScenarioEvent.Outbound>().map { it.frame }
}
