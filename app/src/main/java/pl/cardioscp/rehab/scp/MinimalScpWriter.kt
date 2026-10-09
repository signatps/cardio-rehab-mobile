package pl.cardioscp.rehab.scp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import pl.cardioscp.rehab.session.OnlineEcgFragment

/**
 * Minimalny zapis SCP-ECG (sekcje 0 / 3 / 6) z fragmentu Online —
 * czytelny przez [ScpEcgParser] (bez kompresji, int16).
 */
object MinimalScpWriter {
    private val LEAD_IDS = mapOf(
        "I" to 1,
        "II" to 2,
        "V1" to 3,
        "Vx" to 3,
        "V2" to 4,
        "V3" to 5,
        "V4" to 6,
        "V5" to 7,
        "V6" to 8,
        "III" to 61,
        "aVR" to 62,
        "aVL" to 63,
        "aVF" to 64,
    )

    fun fromFragment(fragment: OnlineEcgFragment): ByteArray {
        require(fragment.leads.isNotEmpty()) { "brak odprowadzeń" }
        val n = fragment.sampleCount
        require(n > 0) { "pusty fragment" }
        val avm = fragment.avmNanoVolts.coerceAtLeast(1)
        val hz = fragment.samplingHz.coerceAtLeast(1)
        val periodUs = (1_000_000 / hz).coerceIn(1, 0xFFFF)
        val leadIds = fragment.leads.map { (label, _) -> LEAD_IDS[label] ?: 1 }
        val samples = fragment.leads.map { it.second.copyOf(n) }

        val sec3Body = buildSection3(leadIds, n)
        val sec6Body = buildSection6(avm, periodUs, samples)
        val sec3 = wrapSection(sectionId = 3, body = sec3Body)
        val sec6 = wrapSection(sectionId = 6, body = sec6Body)

        // Section 0: 16-byte hdr + 12 pointers × 10
        val sec0BodyLen = 12 * 10
        val sec0Total = 16 + sec0BodyLen
        // Layout: [6 B file hdr][sec0][sec3][sec6]
        val fileHdr = 6
        val sec0Off = fileHdr
        val sec3Off = sec0Off + sec0Total
        val sec6Off = sec3Off + sec3.size
        val fileSize = sec6Off + sec6.size

        val out = ByteBuffer.allocate(fileSize).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(0) // CRC placeholder
        out.putInt(fileSize)

        // Section 0 header
        out.position(sec0Off)
        out.putShort(0) // CRC
        out.putShort(0) // id
        out.putInt(sec0Total)
        out.put(10.toByte()) // version
        out.put(20.toByte()) // protocol
        out.put("SCPECG".toByteArray(Charsets.US_ASCII))

        // Pointers (1-based file offsets)
        fun putPtr(id: Int, len: Int, off0: Int) {
            out.putShort(id.toShort())
            out.putInt(len)
            out.putInt(if (len > 0) off0 + 1 else 0)
        }
        putPtr(0, sec0Total, sec0Off)
        putPtr(1, 0, 0)
        putPtr(2, 0, 0)
        putPtr(3, sec3.size, sec3Off)
        putPtr(4, 0, 0)
        putPtr(5, 0, 0)
        putPtr(6, sec6.size, sec6Off)
        for (i in 7 until 12) putPtr(i, 0, 0)

        out.position(sec3Off)
        out.put(sec3)
        out.position(sec6Off)
        out.put(sec6)
        return out.array()
    }

    private fun wrapSection(sectionId: Int, body: ByteArray): ByteArray {
        val total = 16 + body.size
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0) // CRC
        buf.putShort(sectionId.toShort())
        buf.putInt(total)
        buf.put(10.toByte())
        buf.put(20.toByte())
        buf.putShort(0) // reserved
        buf.putInt(0) // reserved
        buf.put(body)
        return buf.array()
    }

    private fun buildSection3(leadIds: List<Int>, sampleCount: Int): ByteArray {
        val buf = ByteBuffer.allocate(2 + leadIds.size * 9).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(leadIds.size.toByte())
        buf.put(1.toByte()) // parallel recording flag
        for (id in leadIds) {
            buf.putInt(1) // begin sample (1-based)
            buf.putInt(sampleCount)
            buf.put(id.toByte())
        }
        return buf.array()
    }

    private fun buildSection6(avm: Int, periodUs: Int, samples: List<ShortArray>): ByteArray {
        val n = samples.first().size
        val lens = samples.map { n * 2 }
        val bodySize = 2 + 2 + 1 + 1 + 2 * samples.size + lens.sum()
        val buf = ByteBuffer.allocate(bodySize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(avm.toShort())
        buf.putShort(periodUs.toShort())
        buf.put(0) // used_difference
        buf.put(0) // biomodal
        for (len in lens) buf.putShort(len.toShort())
        for (lead in samples) {
            for (s in lead) buf.putShort(s)
        }
        return buf.array()
    }
}
