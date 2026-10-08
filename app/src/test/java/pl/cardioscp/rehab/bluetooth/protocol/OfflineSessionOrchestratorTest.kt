package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSessionOrchestratorTest {
    @Test
    fun happyPath_oneEcgAndPulse() {
        val orch = OfflineSessionOrchestrator()
        val scpBytes = ByteArray(5) { it.toByte() }
        val events = mutableListOf<OfflineSessionOrchestrator.Event>()

        fun feed(frame: ProtocolFrame) {
            events += orch.onFrame(frame)
        }

        events += orch.start(
            OfflineSessionOrchestrator.Config(
                unixTimestampSeconds = 1_700_000_000L,
                ecgJobs = listOf(
                    OfflineSessionOrchestrator.EcgJob(lookbackSeconds = 10, totalSeconds = 10, userId = "u1"),
                ),
            ),
        )

        val init = outbound(events).single { it.type == FrameType.INIT }
        events.clear()
        feed(ProtocolFrame(FrameType.ACK, init.sequence))

        val afterInit = outbound(events)
        val getPulse = afterInit.single { it.type == FrameType.GET_PULSE }
        val offline = afterInit.single { it.type == FrameType.ECG_OFFLINE }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, getPulse.sequence))
        feed(ProtocolFrame(FrameType.ACK, offline.sequence))
        feed(ProtocolFrame(FrameType.ECG_OFFLINE_DONE, 99))

        val fetch = outbound(events)
        assertTrue(fetch.any { it.type == FrameType.GET_SCP_INFO })
        val getScp = fetch.single { it.type == FrameType.GET_SCP }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, getScp.sequence))
        feed(
            ProtocolFrame(
                FrameType.SCP_INFO,
                100,
                byteArrayOf(5, 0, 0, 0),
            ),
        )
        feed(ProtocolFrame(FrameType.SCP_FRAGMENT, 101, scpBytes))

        val afterFrag = events.toList()
        assertTrue(afterFrag.any { it is OfflineSessionOrchestrator.Event.Outbound && it.frame.type == FrameType.ACK })
        val completed = afterFrag.filterIsInstance<OfflineSessionOrchestrator.Event.ScpCompleted>().single()
        assertTrue(scpBytes.contentEquals(completed.bytes))
        val scpDone = outbound(afterFrag).single { it.type == FrameType.SCP_DONE }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, scpDone.sequence))
        // ECG branch done but End waits for pulse
        assertTrue(outbound(events).none { it.type == FrameType.END })
        events.clear()

        feed(ProtocolFrame(FrameType.PULSE_VALUE, 200, byteArrayOf(68)))
        val afterPulse = events.toList()
        assertTrue(afterPulse.any { it is OfflineSessionOrchestrator.Event.Pulse && it.bpm == 68 })
        val end = outbound(afterPulse).single { it.type == FrameType.END }
        events.clear()

        feed(ProtocolFrame(FrameType.ACK, end.sequence))
        assertTrue(events.any { it is OfflineSessionOrchestrator.Event.Finished })
        assertEquals(OfflineSessionOrchestrator.Phase.FINISHED, orch.phase)
    }

    private fun outbound(events: List<OfflineSessionOrchestrator.Event>): List<ProtocolFrame> =
        events.mapNotNull { (it as? OfflineSessionOrchestrator.Event.Outbound)?.frame }
}
