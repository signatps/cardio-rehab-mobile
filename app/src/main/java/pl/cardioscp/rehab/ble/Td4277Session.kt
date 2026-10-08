package pl.cardioscp.rehab.ble

/**
 * Glukometr TaiDoc TD-4277 (Glucomaxx) — Meter ICD V1.12 + DPS `td4277.py`
 * + [TD-42xx protocol](https://protocols.glucometers.tech/taidoc/td42xx.html).
 *
 * Sekwencja: liczba → zegar urządzenia → rekordy (czas/wartość) →
 * **opcjonalnie 0x33 (set time)** gdy zegar fabryczny / dryf → turn-off.
 *
 * `0x33` **nigdy przed pamięcią** — na części firmware blokowało `0x25`/`0x26`.
 * Po pełnym odczycie rekordów protokół dopuszcza `settime` (0x33); wtedy
 * synchronizujemy zegar telefonu z metrem przed `0x50`.
 *
 * 0x54 (entering comm) — urządzenie bywa wysyła na starcie; ponawiamy bieżącą komendę.
 *
 * Świeżość jak DPS: measurement_time + 10 min > device_time.
 * Gdy najnowszy jest stary → [BleParseOutcome.Choose] z alertem.
 */
class Td4277Session(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private enum class State {
        ReadStorageCount,
        ReadDeviceTime,
        ReadRecordTime,
        ReadRecordValue,
        SetDeviceClock,
        TurnOff,
        Done,
    }

    private var state = State.ReadStorageCount
    private var deviceTime: TaiDocProtocol.DeviceDateTime? = null
    private var measurementTime: TaiDocProtocol.DeviceDateTime? = null
    private var pendingWrite = true
    /** Ostatnia ramka host→meter — Android na 1524 bywa echo-notify tego samego zapisu. */
    private var lastWrite: ByteArray? = null
    /** Klient BLE — opóźnienie przed kolejnym write. */
    var delayNextWriteMs: Long = 0L
        private set
    private var storageCount = 0
    private var recordIndex = 0
    private var collectingHistory = false
    private var latestWasStale = false
    /** True po ACK 0x33 (albo gdy sync nie był potrzebny). */
    private var clockSyncAttempted = false
    /** True po odpowiedzi 0x26 — do diagnostyki / retry. */
    private var memoryReadOk = false
    private val history = ArrayList<VitalHistoryOption>()
    private var pendingReading: VitalReading? = null
    private var failReason: String =
        "Brak świeżego pomiaru glikemii (zmierz na TD-4277, potem połącz w ≤10 min)"
    private val frameBuf = TaiDocProtocol.FrameBuffer()

    companion object {
        const val FRESH_WINDOW_MS: Long = 10L * 60L * 1000L
        const val CLOCK_DRIFT_MS: Long = 3L * 60L * 1000L
        /** Pełna historia z pamięci TD-4277 (bezpieczny sufit). */
        const val MAX_HISTORY: Int = 200
        /** Jak DPS `sleep(0.1)` między komendami TaiDoc. */
        const val INTER_CMD_DELAY_MS: Long = 100L
    }

    fun nextWrite(): ByteArray? {
        if (!pendingWrite) return null
        pendingWrite = false
        delayNextWriteMs = 0L
        val frame = when (state) {
            State.ReadStorageCount -> TaiDocProtocol.readStoredNumber(0)
            State.ReadDeviceTime -> TaiDocProtocol.readDeviceClock()
            // DPS / Meter V1.12 / glucometers.tech: indeks LE w bajtach 0–1 (0 = najnowszy).
            State.ReadRecordTime -> TaiDocProtocol.readRecordTime(recordIndex)
            State.ReadRecordValue -> TaiDocProtocol.readRecordResult(recordIndex)
            State.SetDeviceClock ->
                TaiDocProtocol.setDeviceClock(TaiDocProtocol.nowDeviceDateTime(nowMillis()))
            State.TurnOff -> TaiDocProtocol.turnOff()
            State.Done -> null
        }
        lastWrite = frame
        return frame
    }

    fun onNotify(data: ByteArray): BleParseOutcome {
        val frames = frameBuf.offer(data)
        if (frames.isEmpty()) return BleParseOutcome.NeedMore
        var last: BleParseOutcome = BleParseOutcome.NeedMore
        for (frame in frames) {
            last = handleFrame(frame)
            if (last is BleParseOutcome.Done ||
                last is BleParseOutcome.Fail ||
                last is BleParseOutcome.Choose
            ) {
                return last
            }
        }
        return last
    }

    private fun handleFrame(data: ByteArray): BleParseOutcome {
        val echo = lastWrite
        // Android na 1524: notify często = echo zapisu (A3). Odpowiedź metra = A5 lub inny payload.
        if (echo != null && data.contentEquals(echo)) {
            return BleParseOutcome.Continue
        }
        when (TaiDocProtocol.commandOf(data)) {
            TaiDocProtocol.CMD_ENTERING_COMM -> {
                // Meter V1.12 §2.2.11: MD → GW, bez odpowiedzi; ponów bieżącą komendę.
                if (state != State.Done && state != State.TurnOff) {
                    pendingWrite = true
                    delayNextWriteMs = INTER_CMD_DELAY_MS
                }
                return BleParseOutcome.Continue
            }
            TaiDocProtocol.CMD_READ_STORED_NUMBER -> {
                if (state == State.ReadStorageCount) {
                    storageCount = (data[2].toInt() and 0xFF) + ((data[3].toInt() and 0xFF) shl 8)
                    if (storageCount <= 0) {
                        failReason = "Brak pomiarów glikemii w pamięci TD-4277"
                        finishAfterMemory()
                    } else {
                        state = State.ReadDeviceTime
                        pendingWrite = true
                        delayNextWriteMs = INTER_CMD_DELAY_MS
                    }
                }
            }
            TaiDocProtocol.CMD_READ_DEVICE_CLOCK -> {
                if (state == State.ReadDeviceTime) {
                    deviceTime = TaiDocProtocol.parseDateTime(data)
                    // Najpierw rekordy — 0x33 dopiero po pamięci (protokół TD-42xx).
                    recordIndex = 0
                    state = State.ReadRecordTime
                    pendingWrite = true
                    delayNextWriteMs = INTER_CMD_DELAY_MS
                }
            }
            TaiDocProtocol.CMD_READ_STORED_DATA_TIME -> {
                if (state == State.ReadRecordTime) {
                    measurementTime = TaiDocProtocol.parseDateTime(data)
                    state = State.ReadRecordValue
                    pendingWrite = true
                    delayNextWriteMs = INTER_CMD_DELAY_MS
                }
            }
            TaiDocProtocol.CMD_READ_STORED_DATA_RESULT -> {
                if (state == State.ReadRecordValue) {
                    memoryReadOk = true
                    val glu = (data[2].toInt() and 0xFF) + ((data[3].toInt() and 0xFF) shl 8)
                    // Meter V1.12 / glucometers.tech: meal w data[5] (0x40/0x80) albo Type I w data[4].
                    val mealByte = data[5].toInt() and 0xFF
                    val typeI = data[4].toInt() and 0xFF
                    val meal = when {
                        mealByte == 0x40 || typeI == 0x1 -> "przed posiłkiem"
                        mealByte == 0x80 || typeI == 0x2 -> "po posiłku"
                        else -> ""
                    }
                    handleGlucoseRecord(glu, meal)
                }
            }
            TaiDocProtocol.CMD_SET_DEVICE_CLOCK -> {
                // TD-42xx: odpowiedź 0x33 z datetime — zegar ustawiony.
                if (state == State.SetDeviceClock) {
                    clockSyncAttempted = true
                    deviceTime = TaiDocProtocol.parseDateTime(data)
                        ?: TaiDocProtocol.nowDeviceDateTime(nowMillis())
                    finishTowardOff()
                }
            }
            TaiDocProtocol.CMD_TURN_OFF -> {
                state = State.Done
                val opts = history.toList()
                val fresh = pendingReading
                return when {
                    fresh != null && opts.size <= 1 && !latestWasStale ->
                        BleParseOutcome.Done(fresh)
                    opts.isNotEmpty() -> {
                        val title = when {
                            latestWasStale ->
                                "Najświeższy pomiar ma więcej niż 10 min — historia z pamięci"
                            opts.size > 1 ->
                                "Historia glikemii w pamięci TD-4277"
                            else ->
                                "Wybierz pomiar glikemii z pamięci TD-4277"
                        }
                        BleParseOutcome.Choose(
                            title = title,
                            options = opts,
                            freshReading = fresh?.takeIf { !latestWasStale },
                        )
                    }
                    else -> BleParseOutcome.Fail(failReason)
                }
            }
        }
        return BleParseOutcome.Continue
    }

    fun needsWriteAfterNotify(outcome: BleParseOutcome): Boolean =
        outcome is BleParseOutcome.Continue && pendingWrite && state != State.Done

    /** True póki czekamy na pierwszą odpowiedź 0x2B (przed jakimkolwiek postępem). */
    fun awaitingFirstMemoryResponse(): Boolean =
        state == State.ReadStorageCount && !memoryReadOk && history.isEmpty()

    /** True gdy czekamy na odpowiedź 0x23 (zegar urządzenia). */
    fun awaitingDeviceClock(): Boolean = state == State.ReadDeviceTime

    /** True gdy czekamy na 0x25/0x26 (pamięć po zegarze urządzenia). */
    fun awaitingRecordResponse(): Boolean =
        state == State.ReadRecordTime || state == State.ReadRecordValue

    /** True gdy czekamy na ACK 0x33 (ustawienie zegara). */
    fun awaitingSetClock(): Boolean = state == State.SetDeviceClock

    /** Cisza — ponów bieżącą komendę (0x2B / 0x23 / 0x25 / 0x26 / 0x33). */
    fun awaitingHostCommand(): Boolean =
        awaitingFirstMemoryResponse() ||
            awaitingDeviceClock() ||
            awaitingRecordResponse() ||
            awaitingSetClock()

    /** Wymuś ponowne wysłanie bieżącej komendy (np. po ciszy / 0x54). */
    fun forceResendCurrent() {
        if (state == State.Done) return
        frameBuf.clear()
        pendingWrite = true
        delayNextWriteMs = 0L
    }

    private fun handleGlucoseRecord(glu: Int, meal: String) {
        val mt = measurementTime
        val dt = deviceTime
        // DPS: świeży gdy (measurement_time + 10 min) > device_time.
        val freshVsDevice = glu > 0 && mt != null && dt != null &&
            mt.toEpochMinutes() + 10L > dt.toEpochMinutes()
        val measuredAt = mt?.let { minutesToEpochApprox(it) }
        val freshVsPhone = glu > 0 && measuredAt != null &&
            nowMillis() - measuredAt in 0..FRESH_WINDOW_MS
        // Zegar fabryczny/niesynchroniczny — nie ufaj wyłącznie delcie device-relative
        // (oba znaczniki mogą być „świeże” względem siebie przy roku 2000).
        val fresh = if (dt != null && TaiDocProtocol.clockLooksSet(dt, nowMillis())) {
            freshVsDevice
        } else {
            freshVsPhone
        }
        val timeLabel = mt?.let {
            "%02d.%02d.%04d %02d:%02d".format(it.day, it.month, it.year, it.hour, it.minute)
        } ?: "pamięć #$recordIndex"
        if (glu > 0) {
            val note = buildList {
                if (meal.isNotBlank()) add(meal)
                add(timeLabel)
                if (!fresh && recordIndex == 0) add("starszy niż 10 min")
            }.joinToString(" · ")
            val reading = VitalReading(
                kind = BleVitalKind.GLU_TD4277,
                glucoseMgDl = glu,
                deviceName = "TD-4277 / Glucomaxx",
                rawNote = note,
                measuredAtMs = measuredAt,
            )
            if (fresh && recordIndex == 0) {
                pendingReading = reading
            }
            if (recordIndex == 0 && !fresh) {
                latestWasStale = true
            }
            collectingHistory = true
            history += VitalHistoryOption(
                id = "glu-$recordIndex",
                label = buildString {
                    append(GlucoseWho.format(glu))
                    append(" · ")
                    append(timeLabel)
                    if (recordIndex == 0 && !fresh) append(" · >10 min")
                },
                reading = reading,
            )
        }
        // Zawsze zbieraj całą pamięć (przycisk historii na popupie).
        val maxIdx = (storageCount - 1).coerceAtMost(MAX_HISTORY - 1)
        if (recordIndex < maxIdx) {
            recordIndex++
            state = State.ReadRecordTime
            pendingWrite = true
            delayNextWriteMs = INTER_CMD_DELAY_MS
        } else {
            if (history.isEmpty() && pendingReading == null) {
                failReason =
                    "Brak pomiarów glikemii w pamięci (glu=0 lub pusta historia)"
            }
            finishAfterMemory()
        }
    }

    /**
     * Po pełnym odczycie pamięci: gdy zegar wymaga sync — `0x33` (TD-42xx settime),
     * potem `0x50`. Nigdy `0x33` przed `0x25`/`0x26`.
     */
    private fun finishAfterMemory() {
        if (!clockSyncAttempted && shouldSyncClock()) {
            state = State.SetDeviceClock
            pendingWrite = true
            delayNextWriteMs = INTER_CMD_DELAY_MS
            return
        }
        finishTowardOff()
    }

    private fun shouldSyncClock(): Boolean {
        val dt = deviceTime ?: return true
        return TaiDocProtocol.clockNeedsSync(dt, nowMillis(), CLOCK_DRIFT_MS)
    }

    private fun finishTowardOff() {
        state = State.TurnOff
        pendingWrite = true
        delayNextWriteMs = INTER_CMD_DELAY_MS
    }

    private fun minutesToEpochApprox(dt: TaiDocProtocol.DeviceDateTime): Long =
        runCatching {
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, dt.year)
                set(java.util.Calendar.MONTH, dt.month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, dt.day)
                set(java.util.Calendar.HOUR_OF_DAY, dt.hour)
                set(java.util.Calendar.MINUTE, dt.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrDefault(0L)
}
