package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameStreamParserTest {
    @Test
    fun parsesSplitAndNoisyStream() {
        val a = FrameCodec.encode(ProtocolFrame(FrameType.ACK, 1))
        val b = FrameCodec.encode(ProtocolFrame(FrameType.PULSE_VALUE, 2, byteArrayOf(72)))
        val stream = byteArrayOf(0x00, 0x11) + a + b
        val parser = FrameStreamParser()
        val first = parser.push(stream.copyOfRange(0, 5))
        assertEquals(0, first.size)
        val rest = parser.push(stream.copyOfRange(5, stream.size))
        assertEquals(2, rest.size)
        assertEquals(FrameType.ACK, rest[0].type)
        assertEquals(FrameType.PULSE_VALUE, rest[1].type)
        assertEquals(72, PayloadCodec.parsePulseValue(rest[1].payload))
    }
}
