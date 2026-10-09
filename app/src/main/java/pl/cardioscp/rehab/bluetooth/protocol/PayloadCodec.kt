package pl.cardioscp.rehab.bluetooth.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object PayloadCodec {
    /** SCP-ECG lead codes used by EHO-MINI firmware (`scp.h`). */
    object ScpLead {
        const val I: Byte = 1
        const val II: Byte = 2
        const val V1: Byte = 3
    }

    /**
     * Init 0x04 payload — lengths accepted by EHO-MINI firmware
     * (`restor_packets.c` → `is_valid_packet_len`):
     * - **10** = ts(4)+Hz(2)+pulse(1)+clear(1)+count(1)+1 lead
     * - **12** = same + count==3 and 3 lead codes
     *
     * Default: msgLen=10 with count=1 and lead V1 (matches `CALCULATE_ADDITIONAL_LEADS==0`).
     */
    fun init(
        unixTimestampSeconds: Long,
        samplingHz: Int,
        pulseAverageSeconds: Int,
        clearBuffer: Boolean,
        extraLeads: ByteArray = byteArrayOf(ScpLead.V1),
    ): ByteArray {
        val leads = if (extraLeads.isEmpty()) byteArrayOf(ScpLead.V1) else extraLeads
        require(leads.size == 1 || leads.size == 3) {
            "EHO-MINI Init accepts only 1 lead (msgLen=10) or 3 leads (msgLen=12)"
        }
        val buf = ByteBuffer
            .allocate(8 + 1 + leads.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(unixTimestampSeconds.toInt())
        buf.putShort(samplingHz.toShort())
        buf.put(pulseAverageSeconds.toByte())
        buf.put(if (clearBuffer) 0x01 else 0x00)
        buf.put(leads.size.toByte())
        buf.put(leads)
        return buf.array()
    }

    fun ecgOffline(lookbackSeconds: Int, totalSeconds: Int, userIdUtf8: String): ByteArray {
        require(lookbackSeconds in 0..255)
        require(totalSeconds in 0..255)
        // Firmware: measure_time > back_time (strict), else device error.
        require(lookbackSeconds < totalSeconds) {
            "firmware requires totalSeconds > lookbackSeconds"
        }
        val uid = userIdUtf8.toByteArray(StandardCharsets.UTF_8)
        require(uid.size <= 255)
        return byteArrayOf(
            lookbackSeconds.toByte(),
            totalSeconds.toByte(),
            uid.size.toByte(),
        ) + uid
    }

    fun getPulse(intervalTenthsOfSecond: Int): ByteArray {
        require(intervalTenthsOfSecond in 0..0xFFFF)
        return byteArrayOf(
            (intervalTenthsOfSecond and 0xFF).toByte(),
            ((intervalTenthsOfSecond shr 8) and 0xFF).toByte(),
        )
    }

    fun get(infoId: Int): ByteArray = byteArrayOf(infoId.toByte())

    /**
     * SCP Info from EHO-MINI (`create_scp_info_done_packet`): msgLen = **7**
     * `uint32 fileSize | 0x00 | uint16 fileCrc`.
     * Size is the first 4 bytes (little-endian).
     */
    fun parseScpInfoSize(payload: ByteArray): Long {
        require(payload.size >= 4) {
            "SCP Info payload too short: ${payload.size}"
        }
        var size = 0L
        for (i in 0 until 4) {
            size = size or ((payload[i].toLong() and 0xFFL) shl (8 * i))
        }
        return size
    }

    fun parseScpInfoFileCrc(payload: ByteArray): Int? {
        if (payload.size < 7) return null
        return (payload[5].toInt() and 0xFF) or ((payload[6].toInt() and 0xFF) shl 8)
    }

    fun parsePulseValue(payload: ByteArray): Int {
        require(payload.isNotEmpty())
        return payload[0].toInt() and 0xFF
    }

    data class CommandError(
        val code: Int,
        val detail: ByteArray,
    )

    fun parseCommandError(payload: ByteArray): CommandError {
        require(payload.isNotEmpty())
        return CommandError(payload[0].toInt() and 0xFF, payload.copyOfRange(1, payload.size))
    }

    data class GetAns(
        val infoId: Int,
        val value: ByteArray,
    )

    fun parseGetAns(payload: ByteArray): GetAns {
        require(payload.isNotEmpty())
        return GetAns(payload[0].toInt() and 0xFF, payload.copyOfRange(1, payload.size))
    }

    object GetInfoId {
        const val BATTERY = 0x01
        const val ELECTRODES = 0x02
        const val DEVICE_ID = 0x03
        const val TIME = 0x04
    }

    object CommandErrorCode {
        const val ECG_IN_PROGRESS = 0x03
        const val NO_SCP_FILE = 0x04
        const val LOOKBACK_UNAVAILABLE = 0x05
    }

    /** ECG Online Info 0x0F — AVM (×10⁻⁹ V), liczba kanałów, kody SCP odprowadzeń. */
    data class EcgOnlineInfo(
        val avmNanoVolts: Int,
        val channelCount: Int,
        val leadCodes: IntArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EcgOnlineInfo) return false
            return avmNanoVolts == other.avmNanoVolts &&
                channelCount == other.channelCount &&
                leadCodes.contentEquals(other.leadCodes)
        }

        override fun hashCode(): Int =
            31 * (31 * avmNanoVolts + channelCount) + leadCodes.contentHashCode()
    }

    /** ECG Online Data 0x10 — numer pierwszej próbki + int16 interleaved. */
    data class EcgOnlineData(
        val firstSampleIndex: Int,
        val samples: ShortArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EcgOnlineData) return false
            return firstSampleIndex == other.firstSampleIndex && samples.contentEquals(other.samples)
        }

        override fun hashCode(): Int = 31 * firstSampleIndex + samples.contentHashCode()
    }

    fun parseEcgOnlineInfo(payload: ByteArray): EcgOnlineInfo {
        require(payload.size >= 3) { "Online Info too short: ${payload.size}" }
        val avm = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
        val n = payload[2].toInt() and 0xFF
        require(n > 0) { "Online Info: 0 channels" }
        require(payload.size >= 3 + n) { "Online Info truncated for $n leads" }
        val leads = IntArray(n) { i -> payload[3 + i].toInt() and 0xFF }
        return EcgOnlineInfo(avmNanoVolts = avm, channelCount = n, leadCodes = leads)
    }

    fun parseEcgOnlineData(payload: ByteArray, channelCount: Int): EcgOnlineData {
        require(channelCount > 0)
        require(payload.size >= 2) { "Online Data too short" }
        val first = (payload[0].toInt() and 0xFF) or ((payload[1].toInt() and 0xFF) shl 8)
        val sampleBytes = payload.size - 2
        require(sampleBytes % 2 == 0) { "Online Data odd sample byte length" }
        require(sampleBytes % (2 * channelCount) == 0) {
            "Online Data length not divisible by channel count"
        }
        val nShorts = sampleBytes / 2
        val samples = ShortArray(nShorts)
        var o = 2
        for (i in 0 until nShorts) {
            val lo = payload[o].toInt() and 0xFF
            val hi = payload[o + 1].toInt() and 0xFF
            samples[i] = ((hi shl 8) or lo).toShort()
            o += 2
        }
        return EcgOnlineData(firstSampleIndex = first, samples = samples)
    }

    /** Etykieta odprowadzenia SCP (`scp.h`: I=1, II=2, V1=3…). */
    fun scpLeadLabel(code: Int): String = when (code) {
        ScpLead.I.toInt() and 0xFF -> "I"
        ScpLead.II.toInt() and 0xFF -> "II"
        ScpLead.V1.toInt() and 0xFF -> "V1"
        4 -> "V2"
        5 -> "V3"
        6 -> "V4"
        7 -> "V5"
        8 -> "V6"
        9 -> "III"
        10 -> "aVR"
        11 -> "aVL"
        12 -> "aVF"
        else -> "L$code"
    }

    /** int16 × AVM(×10⁻⁹ V) → mV. */
    fun onlineSampleToMv(sample: Short, avmNanoVolts: Int): Double =
        sample.toInt() * (avmNanoVolts.toDouble() * 1e-6)
}
