package pl.cardioscp.rehab.bluetooth.protocol

/**
 * One Silvermedia ECG protocol frame (SOF 0x80 … CRC16 LE).
 */
data class ProtocolFrame(
    val type: FrameType,
    val sequence: Int,
    val payload: ByteArray = ByteArray(0),
) {
    init {
        require(sequence in 0..0xFFFF) { "sequence out of ushort range: $sequence" }
        require(payload.size <= FrameCodec.MAX_PAYLOAD) {
            "payload ${payload.size} exceeds ${FrameCodec.MAX_PAYLOAD}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolFrame) return false
        return type == other.type &&
            sequence == other.sequence &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + sequence
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
