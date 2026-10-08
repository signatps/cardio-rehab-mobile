package pl.cardioscp.rehab.ble

/**
 * Elastyczny parser wagi TD-2555 / FORA W550.
 * Urządzenie po pomiarze samo pushuje wynik — najpierw nasłuch, potem ewentualnie 0x71.
 * Obsługiwane formaty: TaiDoc 0x71, SIG 2A9D, proprietary FFF1 (`00 00 WW WW …`).
 */
class Td2555Session {
    private enum class State { Listen, RequestWeight, TurnOff, Done }

    private var state = State.Listen
    private var weightKg: Double? = null
    private var pendingWrite = false
    private var requested = false

    /** Po włączeniu CCCD — nie pisz od razu, czekaj na push. */
    fun armListen() {
        state = State.Listen
        pendingWrite = false
    }

    /** Po kilku sekundach bez wyniku — wyślij READ_WEIGHT (0x71). */
    fun requestWeightNow() {
        if (weightKg != null || state == State.Done || state == State.TurnOff) return
        state = State.RequestWeight
        pendingWrite = true
        requested = true
    }

    fun nextWrite(): ByteArray? {
        if (!pendingWrite) return null
        pendingWrite = false
        return when (state) {
            State.RequestWeight -> TaiDocProtocol.readWeight()
            State.TurnOff -> TaiDocProtocol.turnOff()
            else -> null
        }
    }

    fun onNotify(data: ByteArray): BleParseOutcome {
        // TaiDoc turn-off / inne komendy — przed heurystyką masy (unika fałszywych trafień).
        if (data.size >= 2 && (data[0].toInt() and 0xFF) == TaiDocProtocol.HDR) {
            when (TaiDocProtocol.commandOf(data)) {
                TaiDocProtocol.CMD_TURN_OFF -> {
                    state = State.Done
                    val w = weightKg
                    return if (w != null) {
                        BleParseOutcome.Done(
                            VitalReading(kind = BleVitalKind.WEIGHT_TD2555, weightKg = w, deviceName = "TD-2555"),
                        )
                    } else {
                        BleParseOutcome.Fail("Brak wyniku wagi — stań na wadze, poczekaj na stabilny wynik, potem połącz ponownie")
                    }
                }
                TaiDocProtocol.CMD_READ_WEIGHT -> {
                    val kg = WeightParser.parseAny(data)
                    if (kg != null) {
                        weightKg = kg
                        state = State.TurnOff
                        pendingWrite = true
                        return BleParseOutcome.Continue
                    }
                    return BleParseOutcome.NeedMore
                }
                TaiDocProtocol.CMD_ENTERING_COMM -> return BleParseOutcome.NeedMore
                else -> {
                    // Inna ramka TaiDoc — nie zgaduj masy z CRC/bajtów.
                    return BleParseOutcome.NeedMore
                }
            }
        }
        val parsed = WeightParser.parseAny(data)
        if (parsed != null) {
            weightKg = parsed
            // Przy listen-only (WSS/FFF1) kończymy od razu bez turn-off.
            if (state == State.Listen) {
                state = State.Done
                return BleParseOutcome.Done(
                    VitalReading(kind = BleVitalKind.WEIGHT_TD2555, weightKg = parsed, deviceName = "TD-2555"),
                )
            }
            state = State.TurnOff
            pendingWrite = true
            return BleParseOutcome.Continue
        }
        return BleParseOutcome.NeedMore
    }

    fun needsWriteAfterNotify(outcome: BleParseOutcome): Boolean =
        outcome is BleParseOutcome.Continue && pendingWrite && state != State.Done

    fun finishWithoutTurnOff(): BleParseOutcome {
        val w = weightKg
        return if (w != null) {
            state = State.Done
            BleParseOutcome.Done(
                VitalReading(kind = BleVitalKind.WEIGHT_TD2555, weightKg = w, deviceName = "TD-2555"),
            )
        } else {
            BleParseOutcome.Fail("Brak wyniku wagi — stań na wadze, poczekaj na stabilny wynik, potem połącz ponownie")
        }
    }
}

object WeightParser {
    /**
     * Próbuje wyciągnąć masę [kg] z dowolnego znanego layoutu.
     * Zwraca null gdy niepewne (żeby nie zabić sesji fałszywym Fail).
     */
    fun parseAny(data: ByteArray): Double? {
        if (data.isEmpty()) return null

        // TaiDoc: 51 71 ww ww …
        if (data.size >= 4 &&
            (data[0].toInt() and 0xFF) == TaiDocProtocol.HDR &&
            (data[1].toInt() and 0xFF) == TaiDocProtocol.CMD_READ_WEIGHT
        ) {
            pickU16(data, 2)?.let { return it }
        }

        // SIG Weight Measurement 2A9D (flags + uint16)
        if (data.size >= 3) {
            val flags = data[0].toInt() and 0xFF
            // Heurystyka: flagi WSS zwykle mają niskie bity, bez 0x51 nagłówka TaiDoc.
            if ((data[0].toInt() and 0xFF) != TaiDocProtocol.HDR && flags <= 0x1F) {
                val raw = u16le(data, 1)
                val kg = if (flags and 0x01 != 0) raw * 0.01 * 0.45359237 else raw * 0.005
                accept(kg)?.let { return it }
            }
        }

        // Proprietary FFF1: 00 00 WW WW … (BE, ×0.1) — jak w typowych zrzutach wag BLE
        if (data.size >= 5 && data[0].toInt() == 0 && data[1].toInt() == 0) {
            pickU16(data, 2, preferBe = true)?.let { return it }
            pickU16(data, 2, preferBe = false)?.let { return it }
        }

        // Ogólne: przeszukaj pary bajtów
        for (offset in 0 until (data.size - 1).coerceAtMost(8)) {
            pickU16(data, offset, preferBe = true)?.let { return it }
            pickU16(data, offset, preferBe = false)?.let { return it }
        }

        // iXellence-like (≥14 B): weight BE at [2], flags at [8]
        if (data.size >= 14) {
            var weight = ((data[2].toInt() and 0xFF) shl 8) + (data[3].toInt() and 0xFF)
            val flags = data[8].toInt() and 0xFF
            if (flags and 0x01 != 0) {
                weight = when {
                    flags and 0b110 == 0 -> weight / 10
                    flags and 0b110 == 0b100 -> weight / 100
                    else -> weight / 10
                }
                var kg = weight.toDouble()
                if (flags and 0b11000 != 0) kg = kg * 454.0 / 1000.0
                accept(kg)?.let { return it }
            }
        }
        return null
    }

    fun parseWss(data: ByteArray): BleParseOutcome {
        val kg = parseAny(data) ?: return BleParseOutcome.NeedMore
        return BleParseOutcome.Done(
            VitalReading(kind = BleVitalKind.WEIGHT_TD2555, weightKg = kg, deviceName = "TD-2555"),
        )
    }

    private fun pickU16(data: ByteArray, offset: Int, preferBe: Boolean = false): Double? {
        if (data.size < offset + 2) return null
        val raw = if (preferBe) u16be(data, offset) else u16le(data, offset)
        if (raw == 0 || raw == 0xFFFF) return null
        // 0.1 kg, 0.01 kg, WSS 0.005 kg
        return accept(raw / 10.0)
            ?: accept(raw / 100.0)
            ?: accept(raw * 0.005)
    }

    private fun accept(kg: Double): Double? {
        val rounded = kotlin.math.round(kg * 10.0) / 10.0
        return if (rounded in 15.0..250.0) rounded else null
    }

    private fun u16le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun u16be(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
}
