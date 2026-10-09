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
    fun ecgOnlineInfo_wojtekWithoutLeadCodes() {
        val info = PayloadCodec.parseEcgOnlineInfo(
            byteArrayOf(0xBC.toByte(), 0x1B, 0x03),
        )
        assertEquals(7100, info.avmNanoVolts)
        assertEquals(3, info.channelCount)
        assertEquals(0, info.leadCodes.size)
        assertEquals(listOf("I", "II", "V1"), PayloadCodec.onlineLeadLabels(info))
    }

    @Test
    fun ecgOnlineInfo_withOptionalLeadCodes() {
        val info = PayloadCodec.parseEcgOnlineInfo(
            byteArrayOf(0xBC.toByte(), 0x1B, 0x02, 0x01, 0x02),
        )
        assertEquals(2, info.channelCount)
        assertArrayEquals(intArrayOf(1, 2), info.leadCodes)
    }

    @Test
    fun ecgOnlineData_decodesAds10bit3ch() {
        // avm + channels + 20 B header + one 4-byte pack
        val raw = ByteArray(20 + 4)
        // After >>2: bits lay out Vx / II / I in low→high 10-bit fields.
        // Build val such that after >>2: I=16+512, II=32+512, Vx=0+512
        val i = 16 + 0x200
        val ii = 32 + 0x200
        val vx = 0 + 0x200
        var packed = vx or (ii shl 10) or (i shl 20)
        packed = packed shl 2
        raw[20] = (packed and 0xFF).toByte()
        raw[21] = ((packed shr 8) and 0xFF).toByte()
        raw[22] = ((packed shr 16) and 0xFF).toByte()
        raw[23] = ((packed shr 24) and 0xFF).toByte()
        val payload = byteArrayOf(0xBC.toByte(), 0x1B, 0x03) + raw
        val data = PayloadCodec.parseEcgOnlineData(payload, channelCountHint = 3)
        assertEquals(7100, data.avmNanoVolts)
        assertEquals(3, data.channelCount)
        assertEquals(3, data.samples.size)
        assertEquals(16.toShort(), data.samples[0])
        assertEquals(32.toShort(), data.samples[1])
        assertEquals(0.toShort(), data.samples[2])
    }
}
