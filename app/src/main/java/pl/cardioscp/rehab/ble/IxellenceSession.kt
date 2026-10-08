package pl.cardioscp.rehab.ble

/**
 * Waga iXellence / Jumper **JPD-BS200 / BS201** — reklamy BLE (bez GATT).
 *
 * Protokół: „Bluetooth Protocol for JPD-BS200/BS201” (Shenzhen Jumper).
 * Nazwa BT zwykle `JPD…` (nie „iXellence”).
 *
 * AD: Len=0x10, Type=0xFF, domena 15 B:
 * version(0xC0), serial, weight BE, resistance BE, productId BE, attributes, MAC×6.
 *
 * Android oddziela pierwsze 2 B domeny jako Company ID — [parseAdvertisement]
 * składa je z powrotem. Bit0 attributes = lock (stabilny wynik).
 */
class IxellenceSession {
    private var steppedOn = false

    /**
     * @param domain pełna 15-bajtowa domena Manufacturer Data (z version+serial)
     */
    fun onDomain(domain: ByteArray): BleParseOutcome {
        if (domain.size < 9) return BleParseOutcome.NeedMore
        // Version bywa 0xC0; tolerujemy też inne, by nie blokować OEM.
        var weight = ((domain[2].toInt() and 0xFF) shl 8) or (domain[3].toInt() and 0xFF)
        val attrs = domain[8].toInt() and 0xFF
        if (!steppedOn) steppedOn = true
        // Bit0: 0 = non-locking (w toku), 1 = lock (stabilny)
        if (attrs and 0x01 == 0) return BleParseOutcome.Continue
        // Bit2-1: miejsca dziesiętne
        val decimals = (attrs shr 1) and 0b11
        var value = weight.toDouble()
        when (decimals) {
            0b00 -> value /= 10.0   // 1 miejsce (domyślnie)
            0b01 -> Unit           // 0 miejsc
            0b10 -> value /= 100.0 // 2 miejsca
            else -> value /= 10.0
        }
        // Bit4-3: jednostka
        when ((attrs shr 3) and 0b11) {
            0b00 -> Unit // KG
            0b01 -> value *= 0.5 // Jin → kg
            0b10 -> value = value * 453.59237 / 1000.0 // LB → kg
            0b11 -> {
                // ST:LB — protokół: ST = część całkowita raw/…; uproszczenie jak LB
                value = value * 453.59237 / 1000.0
            }
        }
        val kg = kotlin.math.round(value * 10.0) / 10.0
        val serial = (domain[1].toInt() and 0xFF).toString()
        return if (kg > 10.0) {
            steppedOn = false
            BleParseOutcome.Done(
                VitalReading(
                    kind = BleVitalKind.WEIGHT_IXELLENCE,
                    weightKg = kg,
                    deviceName = "JPD / iXellence",
                    rawNote = "JPD-BS200/BS201",
                    deviceSerial = serial,
                ),
            )
        } else {
            BleParseOutcome.Continue
        }
    }

    companion object {
        const val VERSION_BYTE = 0xC0

        /** Czy Manufacturer Data wygląda na JPD (version 0xC0 w Company ID LE). */
        fun looksLikeJpd(companyId: Int, payload: ByteArray): Boolean {
            if ((companyId and 0xFF) != VERSION_BYTE) return false
            return payload.size >= 7 // min. weight(2)+res(2)+prod(2)+attrs(1)
        }

        /**
         * Android: [companyId] = version|serial<<8, [payload] = od Weight…
         * Składa domenę 15 B i parsuje.
         */
        fun parseAdvertisement(companyId: Int, payload: ByteArray, session: IxellenceSession): BleParseOutcome {
            val domain = rebuildDomain(companyId, payload) ?: return BleParseOutcome.NeedMore
            return session.onDomain(domain)
        }

        fun rebuildDomain(companyId: Int, payload: ByteArray): ByteArray? {
            // Payload już jest pełną domeną (rzadkie)
            if (payload.size >= 15 && (payload[0].toInt() and 0xFF) == VERSION_BYTE) {
                return payload.copyOf(15)
            }
            if (payload.size < 7) return null
            val domain = ByteArray(2 + payload.size)
            domain[0] = (companyId and 0xFF).toByte()
            domain[1] = ((companyId shr 8) and 0xFF).toByte()
            payload.copyInto(domain, 2)
            return domain
        }
    }
}
