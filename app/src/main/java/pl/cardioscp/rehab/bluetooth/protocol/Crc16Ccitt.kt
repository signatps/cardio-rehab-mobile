package pl.cardioscp.rehab.bluetooth.protocol

/**
 * CRC-16-CCITT as required by the Silvermedia ECG frame spec:
 * polynomial 0x1021, initial value 0xFFFF, no final XOR.
 */
object Crc16Ccitt {
    private const val POLY = 0x1021
    private const val INIT = 0xFFFF

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = INIT
        val end = offset + length
        for (i in offset until end) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor POLY) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }
}
