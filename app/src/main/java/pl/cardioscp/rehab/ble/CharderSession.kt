package pl.cardioscp.rehab.ble

/**
 * Waga Charder — port z `charder.py`.
 * Write ASCII `'P'` (0x50), bufor odpowiedzi; masa z offsetu 173, jednostka na 181.
 * Stabilizacja: 3 kolejne odczyty w ±0.5 kg i waga > 10 kg.
 */
class CharderSession {
    private val buffer = ArrayList<Byte>()
    private var prev1 = 0.0
    private var prev2 = 0.0

    fun pollCommand(): ByteArray {
        buffer.clear()
        return byteArrayOf(0x50) // 'P'
    }

    fun onNotify(chunk: ByteArray): BleParseOutcome {
        val asText = String(chunk, Charsets.ISO_8859_1)
        if (asText.startsWith("MODEL")) buffer.clear()
        buffer.addAll(chunk.toList())
        if (buffer.size < 310) return BleParseOutcome.NeedMore
        val valuePos = 173
        val unitPos = 181
        if (buffer.size <= unitPos) return BleParseOutcome.NeedMore
        val raw = buffer.subList(valuePos, unitPos - 1)
            .map { (it.toInt() and 0xFF).toChar() }
            .joinToString("")
            .trim()
        var weight = raw.toDoubleOrNull() ?: run {
            buffer.clear()
            return BleParseOutcome.Continue
        }
        val unit = (buffer[unitPos].toInt() and 0xFF).toChar()
        if (unit == 'l') {
            weight = weight * 454.0 / 1000.0
        }
        weight = (kotlin.math.round(weight * 10.0) / 10.0)
        if (weight > 10.0) {
            val stable = weight in (prev1 - 0.5)..(prev1 + 0.5) &&
                weight in (prev2 - 0.5)..(prev2 + 0.5) &&
                prev1 > 0 && prev2 > 0
            if (stable) {
                prev1 = 0.0
                prev2 = 0.0
                return BleParseOutcome.Done(
                    VitalReading(
                        kind = BleVitalKind.WEIGHT_CHARDER,
                        weightKg = weight,
                        deviceName = "Charder 6110BT",
                    ),
                )
            }
        }
        prev2 = prev1
        prev1 = weight
        buffer.clear()
        return BleParseOutcome.Continue // caller should re-poll with 'P'
    }
}
