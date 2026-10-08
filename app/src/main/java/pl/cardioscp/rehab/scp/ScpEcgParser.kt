package pl.cardioscp.rehab.scp

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ScpLeadWaveform(
    val leadId: Int,
    val label: String,
    /** Raw signed samples from section 6; physical unit = sample × AVM nV. */
    val samples: ShortArray,
) {
    fun sampleMv(index: Int, avmNvPerLsb: Int): Float =
        EcgScale.sampleToMv(samples[index].toInt(), avmNvPerLsb)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScpLeadWaveform) return false
        return leadId == other.leadId && label == other.label && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = leadId
        result = 31 * result + label.hashCode()
        result = 31 * result + samples.contentHashCode()
        return result
    }
}

data class ScpEcgRecording(
    val fileSize: Int,
    /** Amplitude Value Multiplier in nanovolts / LSB (EN 1064 §5.9). */
    val avm: Int,
    /** Sample period in microseconds. */
    val samplePeriodUs: Int,
    val samplingHz: Int,
    val leads: List<ScpLeadWaveform>,
) {
    val durationSeconds: Double
        get() {
            val n = leads.maxOfOrNull { it.samples.size } ?: 0
            return if (samplingHz <= 0) 0.0 else n.toDouble() / samplingHz
        }

    /** µV per LSB — handy for UI labels. */
    val uvPerLsb: Float get() = avm / 1000f
}

/**
 * Minimal SCP-ECG reader for EHO-MINI files (sections 0 / 3 / 6).
 * Expects uncompressed int16 lead data (used_difference=0, biomodal=0).
 */
object ScpEcgParser {
    private val LEAD_LABELS = mapOf(
        1 to "I",
        2 to "II",
        3 to "V1",
        4 to "V2",
        5 to "V3",
        6 to "V4",
        7 to "V5",
        8 to "V6",
        61 to "III",
        62 to "aVR",
        63 to "aVL",
        64 to "aVF",
        111 to "III",
        112 to "aVR",
        113 to "aVL",
        114 to "aVF",
        // Seen in Pro-PLUS sample SCPs as third measured lead
        71 to "Vx",
    )

    fun parse(bytes: ByteArray): ScpEcgRecording {
        require(bytes.size >= 32) { "SCP too short: ${bytes.size}" }
        val section6 = findSection(bytes, sectionId = 6)
            ?: error("Brak sekcji 6 (dane EKG) w pliku SCP")
        val leadIds = parseLeadIds(bytes)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var o = section6.offset + 16 // skip SCP_HDR_TYPE
        val avm = buf.getShort(o).toInt() and 0xFFFF
        o += 2
        val samplePeriodUs = buf.getShort(o).toInt() and 0xFFFF
        o += 2
        val usedDifference = bytes[o].toInt() and 0xFF
        o += 1
        val biomodal = bytes[o].toInt() and 0xFF
        o += 1
        require(usedDifference == 0 && biomodal == 0) {
            "Nieobsługiwana kompresja SCP (diff=$usedDifference biomodal=$biomodal)"
        }

        val leadCount = when {
            leadIds.isNotEmpty() -> leadIds.size
            else -> 3
        }
        val leadLens = IntArray(leadCount) {
            val len = buf.getShort(o).toInt() and 0xFFFF
            o += 2
            len
        }

        val samplingHz = if (samplePeriodUs > 0) 1_000_000 / samplePeriodUs else 0
        val leads = ArrayList<ScpLeadWaveform>(leadCount)
        for (i in 0 until leadCount) {
            val byteLen = leadLens[i]
            require(o + byteLen <= bytes.size) { "Lead $i wykracza poza plik" }
            val sampleCount = byteLen / 2
            val samples = ShortArray(sampleCount)
            for (s in 0 until sampleCount) {
                samples[s] = buf.getShort(o)
                o += 2
            }
            val id = leadIds.getOrElse(i) { i + 1 }
            leads += ScpLeadWaveform(
                leadId = id,
                label = LEAD_LABELS[id] ?: "L$id",
                samples = samples,
            )
        }

        return ScpEcgRecording(
            fileSize = bytes.size,
            avm = avm,
            samplePeriodUs = samplePeriodUs,
            samplingHz = samplingHz,
            leads = leads,
        )
    }

    private data class SectionRef(val offset: Int, val length: Int)

    /** Section pointers in section 0 use 1-based file offsets (EHO-MINI firmware). */
    private fun findSection(bytes: ByteArray, sectionId: Int): SectionRef? {
        val sec0 = 6
        if (bytes.size < sec0 + 16 + 10) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val ptrBase = sec0 + 16
        for (i in 0 until 12) {
            val off = ptrBase + i * 10
            if (off + 10 > bytes.size) break
            val id = buf.getShort(off).toInt() and 0xFFFF
            val len = buf.getInt(off + 2)
            val ptr = buf.getInt(off + 6)
            if (id == sectionId && len > 0 && ptr > 0) {
                val base = ptr - 1
                if (base >= 0 && base + 8 <= bytes.size) {
                    val hdrId = buf.getShort(base + 2).toInt() and 0xFFFF
                    if (hdrId == sectionId) {
                        return SectionRef(base, len)
                    }
                }
            }
        }
        return null
    }

    private fun parseLeadIds(bytes: ByteArray): List<Int> {
        val section3 = findSection(bytes, sectionId = 3) ?: return emptyList()
        // After 16-byte hdr: leads_count (u8), flags (u8), then lead structs
        var o = section3.offset + 16
        if (o + 2 > bytes.size) return emptyList()
        val count = bytes[o].toInt() and 0xFF
        o += 2 // count + flags
        val ids = ArrayList<Int>(count)
        // Each lead: begin u32, end u32, lead_id u8 (+ optional pad in struct)
        // sizeof lead in firmware: 4+4+1 = 9, may pad to even
        for (i in 0 until count) {
            if (o + 9 > bytes.size) break
            val leadId = bytes[o + 8].toInt() and 0xFF
            ids += leadId
            o += 9
        }
        return ids
    }
}
