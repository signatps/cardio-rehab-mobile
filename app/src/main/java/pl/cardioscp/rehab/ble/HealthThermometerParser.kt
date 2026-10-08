package pl.cardioscp.rehab.ble

/**
 * Generic Health Thermometer (0x1809 / 0x2A1C) — TD-1035 / TD-1241.
 *
 * Charakterystyka Temperature Measurement używa **Indicate** (nie Notify).
 * Format: Flags (1) + IEEE-11073 FLOAT (4) [+ Timestamp (7)] [+ Type (1)].
 * Zachowujemy też heurystykę z `genericHealthThermometer.py` dla dłuższych ramek TaiDoc.
 */
object HealthThermometerParser {
    fun parse(data: ByteArray): BleParseOutcome {
        if (data.isEmpty()) return BleParseOutcome.NeedMore

        // DPS wymagał len > 12; standard SIG często wysyła 5 B (bez timestamp).
        if (data.size >= 5 && data.size <= 12) {
            return parseIeee(data)
        }
        if (data.size > 12) {
            if ((data[1].toInt() and 0xFF) == 0xAA &&
                (data[2].toInt() and 0xFF) == 0xAA &&
                (data[3].toInt() and 0xFF) == 0x00
            ) {
                return BleParseOutcome.Fail("Temperatura invalid (AA AA 00)")
            }
            // Heurystyka DPS (niepełny FLOAT) — działa na dłuższych payloadach TaiDoc.
            var temperature = (
                ((data[3].toInt() and 0xFF) shl 16) +
                    ((data[2].toInt() and 0xFF) shl 8) +
                    (data[1].toInt() and 0xFF)
                ) / 10.0
            val expByte = data[4].toInt() and 0xFF
            val divider = if (expByte > 0xF0) 0xFF - expByte else 0
            repeat(divider) { temperature /= 10.0 }
            return accept(temperature)
        }
        return BleParseOutcome.NeedMore
    }

    private fun parseIeee(data: ByteArray): BleParseOutcome {
        val flags = data[0].toInt() and 0xFF
        var temperature = ieee11073Float(data, 1) ?: return BleParseOutcome.NeedMore
        // Bit 0: 0 = Celsius, 1 = Fahrenheit
        if (flags and 0x01 != 0) {
            temperature = (temperature - 32.0) * 5.0 / 9.0
        }
        return accept(temperature)
    }

    /** IEEE-11073 32-bit FLOAT: 24-bit mantissa LE + 8-bit signed exponent. */
    private fun ieee11073Float(data: ByteArray, offset: Int): Double? {
        if (data.size < offset + 4) return null
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF
        val b3 = data[offset + 3].toInt() and 0xFF
        // Specjalne wartości NaN / NRes / +INF / -INF
        if (b0 == 0xFF && b1 == 0xFF && (b2 == 0x7F || b2 == 0x80 || b2 == 0xFF)) return null
        var mantissa = b0 or (b1 shl 8) or ((b2 and 0x7F) shl 16)
        if (b2 and 0x80 != 0) mantissa -= 1 shl 24
        val exponent = b3.toByte().toInt()
        return mantissa * Math.pow(10.0, exponent.toDouble())
    }

    private fun accept(temperature: Double): BleParseOutcome =
        if (temperature > 30.0 && temperature < 45.0) {
            BleParseOutcome.Done(
                VitalReading(
                    kind = BleVitalKind.TEMP_TD1241,
                    temperatureC = kotlin.math.round(temperature * 100.0) / 100.0,
                    deviceName = "TD-1241",
                ),
            )
        } else if (temperature > 0 && temperature < 45.0) {
            // Dolny zakres (np. powietrze) — czekamy na właściwy pomiar ciała.
            BleParseOutcome.NeedMore
        } else {
            BleParseOutcome.Fail("Temperatura poza zakresem: %.2f".format(temperature))
        }
}
