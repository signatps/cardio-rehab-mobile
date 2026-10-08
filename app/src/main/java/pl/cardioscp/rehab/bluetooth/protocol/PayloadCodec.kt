package pl.cardioscp.rehab.bluetooth.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object PayloadCodec {
    fun init(
        unixTimestampSeconds: Long,
        samplingHz: Int,
        pulseAverageSeconds: Int,
        clearBuffer: Boolean,
        extraLeads: ByteArray = ByteArray(0),
    ): ByteArray {
        require(extraLeads.size <= 255)
        val buf = ByteBuffer.allocate(9 + extraLeads.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(unixTimestampSeconds.toInt())
        buf.putShort(samplingHz.toShort())
        buf.put(pulseAverageSeconds.toByte())
        buf.put(if (clearBuffer) 0x01 else 0x00)
        buf.put(extraLeads.size.toByte())
        buf.put(extraLeads)
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
     * SCP Info size is a little-endian uint32 (exactly 4 bytes).
     * Confirmed for integration; re-verify against the live EHO-Mini if needed.
     */
    fun parseScpInfoSize(payload: ByteArray): Long {
        require(payload.size == 4) {
            "SCP Info size must be exactly 4 bytes, was ${payload.size}"
        }
        var size = 0L
        for (i in 0 until 4) {
            size = size or ((payload[i].toLong() and 0xFFL) shl (8 * i))
        }
        return size
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
