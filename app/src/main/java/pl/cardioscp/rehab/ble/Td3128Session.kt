package pl.cardioscp.rehab.ble

/**
 * Ciśnieniomierz TaiDoc TD-3128 — port z `td3128.py`.
 * Odczytuje ostatni zapisany pomiar użytkowników 1–4 (nie startuje nowego NIBP).
 *
 * Zegar: po odczycie pamięci, gdy fabryczny / dryf >3 min, wysyła `0x33` (set time)
 * jak w protokole TaiDoc / TD-42xx — **nigdy przed** `0x25`/`0x26`.
 *
 * @param turnOffWhenEmpty gdy false (tryb auto), nie wyłącza urządzenia przy braku wyniku —
 *        pozwala przejść do pomiaru live TD-3140.
 */
class Td3128Session(
    private val turnOffWhenEmpty: Boolean = true,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private enum class State {
        ReadDeviceTime,
        ReadStorageCount,
        ReadLastTime,
        ReadLastValue,
        SetDeviceClock,
        TurnOff,
        Done,
    }

    /** Jak w DPS: `user_ids = [0, 2, 3, 4]`. */
    private val userIds = intArrayOf(0, 2, 3, 4)
    private var state = State.ReadDeviceTime
    private var userIndex = 0
    private var deviceTime: TaiDocProtocol.DeviceDateTime? = null
    private var measurementOk = false
    private var sys: Int? = null
    private var dia: Int? = null
    private var pulse: Int? = null
    private var pendingWrite = true
    private var clockSyncAttempted = false

    companion object {
        const val CLOCK_DRIFT_MS: Long = 3L * 60L * 1000L
    }

    fun nextWrite(): ByteArray? {
        if (!pendingWrite) return null
        pendingWrite = false
        val uid = userIds.getOrElse(userIndex) { 0 }
        return when (state) {
            State.ReadDeviceTime -> TaiDocProtocol.readDeviceClock()
            State.ReadStorageCount -> TaiDocProtocol.readStoredNumber(uid)
            State.ReadLastTime -> TaiDocProtocol.readStoredTime(uid)
            State.ReadLastValue -> TaiDocProtocol.readStoredResult(uid)
            State.SetDeviceClock ->
                TaiDocProtocol.setDeviceClock(TaiDocProtocol.nowDeviceDateTime(nowMillis()))
            State.TurnOff -> TaiDocProtocol.turnOff()
            State.Done -> null
        }
    }

    fun onNotify(data: ByteArray): BleParseOutcome {
        if (data.size < 8) return BleParseOutcome.NeedMore
        if ((data[0].toInt() and 0xFF) != TaiDocProtocol.HDR) return BleParseOutcome.NeedMore
        when (TaiDocProtocol.commandOf(data)) {
            TaiDocProtocol.CMD_READ_DEVICE_CLOCK -> {
                if (state == State.ReadDeviceTime) {
                    deviceTime = TaiDocProtocol.parseDateTime(data) ?: return BleParseOutcome.Continue
                    state = State.ReadStorageCount
                    pendingWrite = true
                }
            }
            TaiDocProtocol.CMD_READ_STORED_NUMBER -> {
                if (state == State.ReadStorageCount) {
                    val count = (data[2].toInt() and 0xFF) + ((data[3].toInt() and 0xFF) shl 8)
                    if (count > 0) {
                        state = State.ReadLastTime
                        pendingWrite = true
                    } else {
                        return advanceUserOrFinish()
                    }
                }
            }
            TaiDocProtocol.CMD_READ_STORED_DATA_TIME -> {
                if (state == State.ReadLastTime) {
                    val mt = TaiDocProtocol.parseDateTime(data)
                    val dt = deviceTime
                    if (mt != null && dt != null && mt.toEpochMinutes() + 2 >= dt.toEpochMinutes()) {
                        state = State.ReadLastValue
                        pendingWrite = true
                    } else {
                        return advanceUserOrFinish()
                    }
                }
            }
            TaiDocProtocol.CMD_READ_STORED_DATA_RESULT -> {
                if (state == State.ReadLastValue) {
                    sys = data[2].toInt() and 0xFF
                    dia = data[4].toInt() and 0xFF
                    pulse = data[5].toInt() and 0xFF
                    if ((sys ?: 0) > 30 && (dia ?: 0) > 30 && (pulse ?: 0) > 30) {
                        measurementOk = true
                        goToTurnOff()
                    } else {
                        return advanceUserOrFinish()
                    }
                }
            }
            TaiDocProtocol.CMD_SET_DEVICE_CLOCK -> {
                if (state == State.SetDeviceClock) {
                    clockSyncAttempted = true
                    deviceTime = TaiDocProtocol.parseDateTime(data)
                        ?: TaiDocProtocol.nowDeviceDateTime(nowMillis())
                    state = State.TurnOff
                    pendingWrite = true
                }
            }
            TaiDocProtocol.CMD_TURN_OFF -> {
                state = State.Done
                return if (measurementOk) {
                    BleParseOutcome.Done(
                        VitalReading(
                            kind = BleVitalKind.BP_TD3128,
                            systolicMmHg = sys,
                            diastolicMmHg = dia,
                            pulseBpm = pulse,
                            deviceName = "TD-3128",
                        ),
                    )
                } else {
                    BleParseOutcome.Fail("Brak świeżego pomiaru TD-3128 (zmierz ciśnienie, potem połącz w ≤2 min)")
                }
            }
        }
        return BleParseOutcome.Continue
    }

    fun needsWriteAfterNotify(outcome: BleParseOutcome): Boolean =
        outcome is BleParseOutcome.Continue && pendingWrite && state != State.Done

    private fun advanceUserOrFinish(): BleParseOutcome {
        userIndex += 1
        if (userIndex >= userIds.size) {
            if (measurementOk || turnOffWhenEmpty) {
                goToTurnOff()
                return BleParseOutcome.Continue
            }
            state = State.Done
            pendingWrite = false
            return BleParseOutcome.Fail("Brak świeżego pomiaru w pamięci")
        }
        state = State.ReadStorageCount
        pendingWrite = true
        return BleParseOutcome.Continue
    }

    /** Po pamięci: opcjonalnie 0x33, potem 0x50. */
    private fun goToTurnOff() {
        if (!clockSyncAttempted && shouldSyncClock()) {
            state = State.SetDeviceClock
            pendingWrite = true
            return
        }
        state = State.TurnOff
        pendingWrite = true
    }

    private fun shouldSyncClock(): Boolean {
        val dt = deviceTime ?: return true
        return TaiDocProtocol.clockNeedsSync(dt, nowMillis(), CLOCK_DRIFT_MS)
    }
}
