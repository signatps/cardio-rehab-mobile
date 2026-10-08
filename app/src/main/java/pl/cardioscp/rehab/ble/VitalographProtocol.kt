package pl.cardioscp.rehab.ble

import java.util.Locale

/**
 * Parsed Lung Monitor BTLE ASTD payload (DI=`G`). asma-1 (`C`) is PEF-only.
 */
data class VitalographAstd(
    val di: Char,
    val pefLMin: Int,
    val fev075L: Double? = null,
    val fev1L: Double? = null,
    val fev10L: Double? = null,
    val fev1OverFev10Percent: Int? = null,
    val fef2575Lps: Double? = null,
    val fev1PersonalBestL: Double? = null,
    val pefPersonalBestLMin: Int? = null,
    val fev1Percent: Int? = null,
    val pefPercent: Int? = null,
    val greenZonePercent: Int? = null,
    val yellowZonePercent: Int? = null,
    val orangeZonePercent: Int? = null,
    val goodTestFailedQa: Boolean = false,
) {
    /** Ekran wyniku: wiersze poza nagłówkowym PEF. */
    fun screenRows(): List<Pair<String, String>> = buildList {
        fev075L?.let { add("FEV0.75" to litres(it)) }
        fev1L?.let { add("FEV1" to litres(it)) }
        fev10L?.let { add("FEV1.0" to litres(it)) }
        fev1OverFev10Percent?.let { add("FEV1/FEV1.0" to "$it%") }
        fef2575Lps?.let { add("FEF25-75" to litresPerSec(it)) }
        fev1PersonalBestL?.let { add("FEV1 PB" to litres(it)) }
        pefPersonalBestLMin?.let { add("PEF PB" to "$it L/min") }
        fev1Percent?.let { add("FEV1%" to "$it%") }
        pefPercent?.let { add("PEF%" to "$it%") }
        greenZonePercent?.let { add("strefa zieleń" to "$it%") }
        yellowZonePercent?.let { add("strefa żółta" to "$it%") }
        orangeZonePercent?.let { add("strefa pomarańcz" to "$it%") }
        if (goodTestFailedQa) add("jakość" to "słaba")
    }

    /** Dodatkowe linie komentarza EKG (bez wcięcia — wcięcie dodaje PatientVitals). */
    fun extraCommentLines(): List<String> = buildList {
        val line1 = listOfNotNull(
            fev075L?.let { "FEV0.75 ${litres(it)}" },
            fev1L?.let { "FEV1 ${litres(it)}" },
            fev10L?.let { "FEV1.0 ${litres(it)}" },
            fev1OverFev10Percent?.let { "FEV1/FEV1.0 $it%" },
        )
        if (line1.isNotEmpty()) add(line1.joinToString(" · "))
        val line2 = listOfNotNull(
            fef2575Lps?.let { "FEF25-75 ${litresPerSec(it)}" },
            fev1PersonalBestL?.let { "FEV1 PB ${litres(it)}" },
            pefPersonalBestLMin?.let { "PEF PB $it L/min" },
        )
        if (line2.isNotEmpty()) add(line2.joinToString(" · "))
        val zones = if (greenZonePercent != null || yellowZonePercent != null || orangeZonePercent != null) {
            "strefa zieleń ${greenZonePercent ?: "—"} / żółta ${yellowZonePercent ?: "—"} / pomarańcz ${orangeZonePercent ?: "—"}"
        } else {
            null
        }
        val line3 = listOfNotNull(
            fev1Percent?.let { "FEV1% $it" },
            pefPercent?.let { "PEF% $it" },
            zones,
        )
        if (line3.isNotEmpty()) add(line3.joinToString(" · "))
        if (goodTestFailedQa) add("słaba jakość")
    }

    /** Zwięzły dopisek na karcie zlecenia (co najmniej FEV1). */
    fun compactOrderLine(): String? {
        val parts = listOfNotNull(
            fev1L?.let { "FEV1 ${litres(it)}" },
            fev075L?.let { "FEV0.75 ${litres(it)}" },
            fev10L?.let { "FEV1.0 ${litres(it)}" },
            fev1OverFev10Percent?.let { "FEV1/FEV1.0 $it%" },
            fef2575Lps?.let { "FEF25-75 ${litresPerSec(it)}" },
            fev1Percent?.let { "FEV1% $it" },
            pefPercent?.let { "PEF% $it" },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    companion object {
        fun parse(frame: ByteArray, di: Char, pef: Int): VitalographAstd {
            val qa = if (di == VitalographProtocol.DI_LUNG_BTLE &&
                frame.size > VitalographProtocol.BTLE_GOOD_TEST_OFFSET
            ) {
                (frame[VitalographProtocol.BTLE_GOOD_TEST_OFFSET].toInt() and 0xFF).toChar() == '1'
            } else {
                false
            }
            if (di != VitalographProtocol.DI_LUNG_BTLE ||
                frame.size < VitalographProtocol.BTLE_FULL_FIELDS_MIN_LEN
            ) {
                return VitalographAstd(di = di, pefLMin = pef, goodTestFailedQa = qa)
            }
            return VitalographAstd(
                di = di,
                pefLMin = pef,
                fev075L = asciiLitres(frame, VitalographProtocol.BTLE_FEV075_OFFSET),
                fev1L = asciiLitres(frame, VitalographProtocol.BTLE_FEV1_OFFSET),
                fev10L = asciiLitres(frame, VitalographProtocol.BTLE_FEV10_OFFSET),
                fev1OverFev10Percent = asciiInt(frame, VitalographProtocol.BTLE_FEV1_FEV10_OFFSET),
                fef2575Lps = asciiLitres(frame, VitalographProtocol.BTLE_FEF2575_OFFSET),
                fev1PersonalBestL = asciiLitres(frame, VitalographProtocol.BTLE_FEV1_PB_OFFSET),
                pefPersonalBestLMin = asciiInt(frame, VitalographProtocol.BTLE_PEF_PB_OFFSET),
                fev1Percent = asciiInt(frame, VitalographProtocol.BTLE_FEV1_PCT_OFFSET),
                pefPercent = asciiInt(frame, VitalographProtocol.BTLE_PEF_PCT_OFFSET),
                greenZonePercent = asciiInt(frame, VitalographProtocol.BTLE_GREEN_ZONE_OFFSET),
                yellowZonePercent = asciiInt(frame, VitalographProtocol.BTLE_YELLOW_ZONE_OFFSET),
                orangeZonePercent = asciiInt(frame, VitalographProtocol.BTLE_ORANGE_ZONE_OFFSET),
                goodTestFailedQa = qa,
            )
        }

        private fun ascii3(frame: ByteArray, offset: Int): String? {
            if (frame.size < offset + 3) return null
            return String(frame, offset, 3, Charsets.US_ASCII).trim()
        }

        private fun asciiInt(frame: ByteArray, offset: Int): Int? = ascii3(frame, offset)?.toIntOrNull()

        /** 3 ASCII cyfry = wartość × 100 (litry albo L/s). */
        private fun asciiLitres(frame: ByteArray, offset: Int): Double? =
            asciiInt(frame, offset)?.let { it / 100.0 }

        internal fun litres(value: Double): String = String.format(Locale.US, "%.2f L", value)

        internal fun litresPerSec(value: Double): String = String.format(Locale.US, "%.2f L/s", value)
    }
}

/**
 * Vitalograph Model 4000 API (07424) — framing for Lung Monitor BTLE / asma-1.
 *
 * Komendy (§4): STX | DI(dst)|DI(src) | ID(2) | Data | ETX | BCC + ACK/NAK.
 *
 * **ASTD §10.1 (scenariusz główny PEF)** — wyjątek od §4, push po dmuchnięciu:
 * STX | DI(src) | "TD" | Data | ETX | BCC  (bez DI(dst)!)
 *   — Lung Monitor BTLE: DI=`G`, PEF @ 14, pełna ramka 71 B
 *   — asma-1: DI=`C`, PEF @ 17 (zwykle Classic SPP, nie GATT)
 *
 * BCC = XOR od STX do ETX włącznie. ACK 0x06 — §4; §10.1 nie rysuje ACK.
 */
object VitalographProtocol {
    const val STX: Byte = 0x02
    const val ETX: Byte = 0x03
    const val ACK: Byte = 0x06
    const val NAK: Byte = 0x15

    /** Host / PC (przykłady API: DI(src)=`V`). */
    const val DI_HOST = 'V'

    /** Lung Monitor BTLE (Bluetooth 4.0). */
    const val DI_LUNG_BTLE = 'G'

    /** asma-1 (Bluetooth 2.0 Classic — zwykle SPP, nie GATT). */
    const val DI_ASMA1 = 'C'

    /** Lung Monitor (USB/Classic, bez PEF w ASTD). */
    const val DI_LUNG = 'F'

    const val MSG_TEST_DATA = "TD"
    const val MSG_DEVICE_ID = "DI"
    const val MSG_EXIT_REMOTE = "XR"
    /** Get Bluetooth Module Version (Lung Monitor BTLE) — API §10.5. */
    const val MSG_BT_MODULE = "AT"
    const val BT_MODULE_CMD = "03"

    /** Minimalna długość nagłówka ASTD: STX + DI + T + D. */
    const val ASTD_HEADER_LEN = 4

    /** Lung Monitor BTLE ASTD: 4 hdr + 65 data + ETX + BCC = 71. */
    const val BTLE_ASTD_FULL_LEN = 71

    /** PEF w Lung Monitor BTLE ASTD (po 10 B Device ID). */
    const val BTLE_PEF_OFFSET = 14
    const val BTLE_FEV075_OFFSET = 17
    const val BTLE_FEV1_OFFSET = 20
    const val BTLE_FEV10_OFFSET = 23
    const val BTLE_FEV1_FEV10_OFFSET = 26
    const val BTLE_FEF2575_OFFSET = 29
    const val BTLE_FEV1_PB_OFFSET = 32
    const val BTLE_PEF_PB_OFFSET = 35
    const val BTLE_FEV1_PCT_OFFSET = 38
    const val BTLE_PEF_PCT_OFFSET = 41
    const val BTLE_GREEN_ZONE_OFFSET = 44
    const val BTLE_YELLOW_ZONE_OFFSET = 47
    const val BTLE_ORANGE_ZONE_OFFSET = 50
    /** Offset pomarańcz + 3 B — minimalna długość pełnych pól BTLE. */
    const val BTLE_FULL_FIELDS_MIN_LEN = 53

    /** Good Test flag w Lung Monitor BTLE ASTD. */
    const val BTLE_GOOD_TEST_OFFSET = 65

    /** PEF w asma-1 ASTD (Device ID 10 + FEV1 3). */
    const val ASMA1_PEF_OFFSET = 17

    fun bcc(bytes: ByteArray, from: Int = 0, toExclusive: Int = bytes.size): Byte {
        var x = 0
        for (i in from until toExclusive) {
            x = x xor (bytes[i].toInt() and 0xFF)
        }
        return x.toByte()
    }

    fun ackFrame(): ByteArray = byteArrayOf(ACK)

    /**
     * Device Identification request — zalecany pierwszy komunikat po wejściu w Remote Mode.
     * API: STX G V D I ETX BCC
     */
    fun deviceIdentificationRequest(deviceDi: Char = DI_LUNG_BTLE): ByteArray =
        buildRequest(deviceDi, MSG_DEVICE_ID)

    fun exitRemoteRequest(deviceDi: Char = DI_LUNG_BTLE): ByteArray =
        buildRequest(deviceDi, MSG_EXIT_REMOTE)

    /** API §10.5: `STX G V A T 0 3 ETX BCC` — wersja modułu BT. */
    fun bluetoothModuleVersionRequest(deviceDi: Char = DI_LUNG_BTLE): ByteArray =
        buildRequest(deviceDi, MSG_BT_MODULE, BT_MODULE_CMD)

    fun buildRequest(deviceDi: Char, messageId: String, data: String = ""): ByteArray {
        require(messageId.length == 2) { "message ID must be 2 ASCII chars" }
        val body = ByteArray(1 + 1 + 1 + 2 + data.length + 1)
        var i = 0
        body[i++] = STX
        body[i++] = deviceDi.code.toByte()
        body[i++] = DI_HOST.code.toByte()
        body[i++] = messageId[0].code.toByte()
        body[i++] = messageId[1].code.toByte()
        for (c in data) body[i++] = c.code.toByte()
        body[i] = ETX
        return body + byteArrayOf(bcc(body))
    }

    /**
     * Buduje przykładową ramkę ASTD Lung Monitor BTLE (testy).
     * [pef] w L/min, 25..900.
     */
    fun buildBtleAstdSample(
        pef: Int,
        deviceId: String = "1234567VIT",
        goodTestFailedQa: Boolean = false,
    ): ByteArray {
        val pefStr = pef.toString().padStart(3, '0').takeLast(3)
        val id = deviceId.padEnd(10, ' ').take(10)
        val data = buildString {
            append(id)
            append(pefStr) // PEF
            append("289") // FEV0.75
            append("327") // FEV1
            append("480") // FEV10
            append("068") // FEV1/FEV10
            append("395") // FEF2575
            append("380") // FEV1 PB
            append(pefStr) // PEF PB
            append("086") // FEV1%
            append("100") // PEF%
            append("080") // green
            append("050") // yellow
            append("030") // orange
            append("131025") // yymmdd
            append("123030") // hhmmss
            append(if (goodTestFailedQa) '1' else '0')
            append("912") // SW
        }
        require(data.length == 65) { "BTLE ASTD data must be 65 chars, was ${data.length}" }
        val body = ByteArray(ASTD_HEADER_LEN + 65 + 1)
        body[0] = STX
        body[1] = DI_LUNG_BTLE.code.toByte()
        body[2] = MSG_TEST_DATA[0].code.toByte()
        body[3] = MSG_TEST_DATA[1].code.toByte()
        for (j in data.indices) body[ASTD_HEADER_LEN + j] = data[j].code.toByte()
        body[body.lastIndex] = ETX
        return body + byteArrayOf(bcc(body))
    }

    fun isAstdHeader(buf: List<Byte>, start: Int): Boolean {
        if (start + 3 >= buf.size) return false
        if ((buf[start].toInt() and 0xFF) != (STX.toInt() and 0xFF)) return false
        val di = buf[start + 1].toInt().toChar()
        if (di != DI_LUNG_BTLE && di != DI_ASMA1) return false
        return buf[start + 2].toInt().toChar() == 'T' &&
            buf[start + 3].toInt().toChar() == 'D'
    }

    fun pefOffsetForDi(di: Char): Int = when (di) {
        DI_LUNG_BTLE -> BTLE_PEF_OFFSET
        DI_ASMA1 -> ASMA1_PEF_OFFSET
        else -> BTLE_PEF_OFFSET
    }
}
