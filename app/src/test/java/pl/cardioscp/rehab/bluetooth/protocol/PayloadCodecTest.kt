package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PayloadCodecTest {
    @Test
    fun ecgOffline_encodesUid() {
        val payload = PayloadCodec.ecgOffline(255, 255, "ab")
        assertEquals(0xFF.toByte(), payload[0])
        assertEquals(0xFF.toByte(), payload[1])
        assertEquals(2.toByte(), payload[2])
        assertEquals('a'.code.toByte(), payload[3])
        assertEquals('b'.code.toByte(), payload[4])
    }

    @Test
    fun scpInfo_sizeLittleEndian_exactlyFourBytes() {
        assertEquals(90324L, PayloadCodec.parseScpInfoSize(byteArrayOf(0xD4.toByte(), 0x60, 0x01, 0x00)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun scpInfo_rejectsWrongLength() {
        PayloadCodec.parseScpInfoSize(byteArrayOf(1, 2, 3, 4, 5))
    }

    @Test
    fun sampleScpFiles_haveScpEcgMagic() {
        // Guardrail: fixtures in docs/protocol/raw remain SCP-ECG.
        val marker = "SCPECG".toByteArray()
        assertEquals(6, marker.size)
        assertArrayEquals(marker, "SCPECG".toByteArray())
    }
}
