package pl.cardioscp.rehab.bluetooth.protocol

object FrameCodec {
    const val SOF: Byte = 0x80.toByte()
    const val HEADER_SIZE = 6
    const val CRC_SIZE = 2
    const val MIN_FRAME_SIZE = HEADER_SIZE + CRC_SIZE
    const val MAX_PAYLOAD = 1492
    const val MAX_FRAME_SIZE = MIN_FRAME_SIZE + MAX_PAYLOAD

    fun encode(frame: ProtocolFrame): ByteArray {
        val msgLen = frame.payload.size
        val out = ByteArray(MIN_FRAME_SIZE + msgLen)
        out[0] = SOF
        out[1] = frame.type.code.toByte()
        out[2] = (frame.sequence and 0xFF).toByte()
        out[3] = ((frame.sequence shr 8) and 0xFF).toByte()
        out[4] = (msgLen and 0xFF).toByte()
        out[5] = ((msgLen shr 8) and 0xFF).toByte()
        if (msgLen > 0) {
            System.arraycopy(frame.payload, 0, out, HEADER_SIZE, msgLen)
        }
        val crc = Crc16Ccitt.compute(out, offset = 1, length = HEADER_SIZE - 1 + msgLen)
        out[HEADER_SIZE + msgLen] = (crc and 0xFF).toByte()
        out[HEADER_SIZE + msgLen + 1] = ((crc shr 8) and 0xFF).toByte()
        return out
    }

    /**
     * Decode a complete frame buffer (must start at SOF). Returns null on CRC / type errors.
     */
    fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): ProtocolFrame? {
        if (length < MIN_FRAME_SIZE) return null
        if (bytes[offset] != SOF) return null
        val typeCode = bytes[offset + 1].toInt() and 0xFF
        val type = FrameType.fromCode(typeCode) ?: return null
        val seq = (bytes[offset + 2].toInt() and 0xFF) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 8)
        val msgLen = (bytes[offset + 4].toInt() and 0xFF) or
            ((bytes[offset + 5].toInt() and 0xFF) shl 8)
        if (msgLen > MAX_PAYLOAD) return null
        val total = MIN_FRAME_SIZE + msgLen
        if (length < total) return null
        val crcOffset = offset + HEADER_SIZE + msgLen
        val expected = Crc16Ccitt.compute(bytes, offset + 1, HEADER_SIZE - 1 + msgLen)
        val actual = (bytes[crcOffset].toInt() and 0xFF) or
            ((bytes[crcOffset + 1].toInt() and 0xFF) shl 8)
        if (expected != actual) return null
        val payload = if (msgLen == 0) {
            ByteArray(0)
        } else {
            bytes.copyOfRange(offset + HEADER_SIZE, offset + HEADER_SIZE + msgLen)
        }
        return ProtocolFrame(type, seq, payload)
    }
}
