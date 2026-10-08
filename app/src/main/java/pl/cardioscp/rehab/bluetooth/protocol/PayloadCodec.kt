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
        require(lookbackSeconds <= totalSeconds) { "lookback cannot exceed total measurement time" }
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
}
