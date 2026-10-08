package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCodecTest {
    @Test
    fun roundTrip_emptyAck() {
        val frame = ProtocolFrame(FrameType.ACK, sequence = 431)
        val bytes = FrameCodec.encode(frame)
        assertEquals(FrameCodec.SOF, bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
        assertEquals(0xAF.toByte(), bytes[2])
        assertEquals(0x01.toByte(), bytes[3])
        assertEquals(8, bytes.size)
        val decoded = FrameCodec.decode(bytes)
        assertEquals(frame, decoded)
    }

    @Test
    fun rejectsBadCrc() {
        val bytes = FrameCodec.encode(ProtocolFrame(FrameType.END, 1))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0xFF).toByte()
        assertNull(FrameCodec.decode(bytes))
    }

    @Test
    fun encodeInitPayload() {
        val payload = PayloadCodec.init(
            unixTimestampSeconds = 0x7FFFFFFF,
            samplingHz = 500,
            pulseAverageSeconds = 10,
            clearBuffer = true,
            extraLeads = byteArrayOf(0x01, 0x02),
        )
        val frame = ProtocolFrame(FrameType.INIT, 0x0101, payload)
        val bytes = FrameCodec.encode(frame)
        val again = FrameCodec.decode(bytes)
        assertNotNull(again)
        assertTrue(payload.contentEquals(again!!.payload))
    }
}
