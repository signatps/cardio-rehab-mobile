package pl.cardioscp.rehab.bluetooth.protocol

/**
 * Incremental parser for a Bluetooth byte stream that may contain partial frames,
 * noise before SOF, or back-to-back frames.
 */
class FrameStreamParser(
    private val maxBuffer: Int = FrameCodec.MAX_FRAME_SIZE * 4,
) {
    private val buffer = ArrayList<Byte>(maxBuffer)

    fun push(chunk: ByteArray): List<ProtocolFrame> {
        for (b in chunk) {
            buffer.add(b)
        }
        trimIfNeeded()
        return drain()
    }

    fun reset() {
        buffer.clear()
    }

    private fun drain(): List<ProtocolFrame> {
        val frames = mutableListOf<ProtocolFrame>()
        while (true) {
            val sofIndex = buffer.indexOf(FrameCodec.SOF)
            if (sofIndex < 0) {
                buffer.clear()
                break
            }
            if (sofIndex > 0) {
                repeat(sofIndex) { buffer.removeAt(0) }
            }
            if (buffer.size < FrameCodec.MIN_FRAME_SIZE) break
            val msgLen = (buffer[4].toInt() and 0xFF) or ((buffer[5].toInt() and 0xFF) shl 8)
            if (msgLen > FrameCodec.MAX_PAYLOAD) {
                buffer.removeAt(0)
                continue
            }
            val total = FrameCodec.MIN_FRAME_SIZE + msgLen
            if (buffer.size < total) break
            val candidate = ByteArray(total) { i -> buffer[i] }
            val frame = FrameCodec.decode(candidate)
            if (frame == null) {
                // Bad CRC / type — resync after this SOF.
                buffer.removeAt(0)
                continue
            }
            repeat(total) { buffer.removeAt(0) }
            frames += frame
        }
        return frames
    }

    private fun trimIfNeeded() {
        if (buffer.size > maxBuffer) {
            val drop = buffer.size - maxBuffer
            repeat(drop) { buffer.removeAt(0) }
        }
    }
}
