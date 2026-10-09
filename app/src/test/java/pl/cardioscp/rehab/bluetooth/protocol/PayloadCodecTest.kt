package pl.cardioscp.rehab.bluetooth.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PayloadCodecTest {
    @Test
    fun ecgOffline_encodesUid() {
        val payload = PayloadCodec.ecgOffline(254, 255, "ab")
        assertEquals(254.toByte(), payload[0])
        assertEquals(0xFF.toByte(), payload[1])
        assertEquals(2.toByte(), payload[2])
        assertEquals('a'.code.toByte(), payload[3])
        assertEquals('b'.code.toByte(), payload[4])
    }

    @Test
    fun scpInfo_parsesSizeFromSevenByteFirmwarePacket() {
        // file_size=90324, pad 0x00, crcfile example
        val payload = byteArrayOf(
            0xD4.toByte(), 0x60, 0x01, 0x00,
            0x00,
            0x12, 0x34,
        )
        assertEquals(90324L, PayloadCodec.parseScpInfoSize(payload))
        assertEquals(0x3412, PayloadCodec.parseScpInfoFileCrc(payload))
    }

    @Test
    fun sampleScpFiles_haveScpEcgMagic() {
        val marker = "SCPECG".toByteArray()
        assertEquals(6, marker.size)
        assertArrayEquals(marker, "SCPECG".toByteArray())
    }

    @Test
    fun ecgOnlineInfo_andData_parsePerSpec() {
        val info = PayloadCodec.parseEcgOnlineInfo(
            byteArrayOf(0xBC.toByte(), 0x1B, 0x02, 0x01, 0x02),
        )
        assertEquals(7100, info.avmNanoVolts)
        assertEquals(2, info.channelCount)
        assertArrayEquals(intArrayOf(1, 2), info.leadCodes)

        val data = PayloadCodec.parseEcgOnlineData(
            byteArrayOf(0x05, 0x00, 0x10, 0x00, 0x20, 0x00),
            channelCount = 2,
        )
        assertEquals(5, data.firstSampleIndex)
        assertEquals(2, data.samples.size)
        assertEquals(16.toShort(), data.samples[0])
        assertEquals(32.toShort(), data.samples[1])
    }
}
