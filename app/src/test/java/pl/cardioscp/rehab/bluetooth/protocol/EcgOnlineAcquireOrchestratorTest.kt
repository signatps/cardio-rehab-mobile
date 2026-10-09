package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcgOnlineAcquireOrchestratorTest {
    @Test
    fun onlineThenOfflineDone_stopsOnlineAndEnds() {
        val orch = EcgOnlineAcquireOrchestrator()
        val events = mutableListOf<ScenarioEvent>()
        events += orch.start(
            EcgOnlineAcquireOrchestrator.Config(
                unixTimestampSeconds = 1_700_000_000L,
                totalSeconds = 5,
                userId = "u1",
            ),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, init.sequence))

        val online = outbound(events).single { it.type == FrameType.ECG_ONLINE }
        val offline = outbound(events).single { it.type == FrameType.ECG_OFFLINE }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, online.sequence))
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, offline.sequence))

        events += orch.onFrame(
            ProtocolFrame(
                FrameType.ECG_ONLINE_INFO,
                1,
                byteArrayOf(
                    0xBC.toByte(), 0x1B, // AVM 7100
                    0x01, // 1 channel
                    0x03, // V1
                ),
            ),
        )
        assertTrue(events.any { it is ScenarioEvent.EcgOnlineInfoEvent })

        events += orch.onFrame(
            ProtocolFrame(
                FrameType.ECG_ONLINE_DATA,
                2,
                byteArrayOf(
                    0x00, 0x00, // first sample
                    0x10, 0x00, // int16 = 16
                    0x20, 0x00,
                ),
            ),
        )
        assertTrue(events.any { it is ScenarioEvent.EcgOnlineSamples })

        events += orch.onFrame(ProtocolFrame(FrameType.ECG_OFFLINE_DONE, 99))
        val stop = outbound(events).single { it.type == FrameType.ECG_ONLINE_STOP }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, stop.sequence))
        val end = outbound(events).last { it.type == FrameType.END }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, end.sequence))

        assertEquals(EcgOnlineAcquireOrchestrator.Phase.FINISHED, orch.phase)
        assertTrue(events.any { it is ScenarioEvent.Finished })
    }

    @Test
    fun deviceErrorOnlineUnsupported_continuesOffline() {
        val orch = EcgOnlineAcquireOrchestrator()
        val events = mutableListOf<ScenarioEvent>()
        events += orch.start(
            EcgOnlineAcquireOrchestrator.Config(
                unixTimestampSeconds = 1L,
                totalSeconds = 4,
                userId = "u",
            ),
        )
        val init = outbound(events).single { it.type == FrameType.INIT }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, init.sequence))
        val online = outbound(events).single { it.type == FrameType.ECG_ONLINE }
        val offline = outbound(events).single { it.type == FrameType.ECG_OFFLINE }
        events += orch.onFrame(
            ProtocolFrame(FrameType.DEVICE_ERROR, online.sequence, byteArrayOf(0x02, 0x4E)),
        )
        assertTrue(events.any { it is ScenarioEvent.EcgOnlineUnsupported })

        events += orch.onFrame(ProtocolFrame(FrameType.ACK, offline.sequence))
        events += orch.onFrame(ProtocolFrame(FrameType.ECG_OFFLINE_DONE, 9))
        val end = outbound(events).single { it.type == FrameType.END }
        events += orch.onFrame(ProtocolFrame(FrameType.ACK, end.sequence))
        assertEquals(EcgOnlineAcquireOrchestrator.Phase.FINISHED, orch.phase)
    }

    private fun outbound(events: List<ScenarioEvent>): List<ProtocolFrame> =
        events.filterIsInstance<ScenarioEvent.Outbound>().map { it.frame }
}
