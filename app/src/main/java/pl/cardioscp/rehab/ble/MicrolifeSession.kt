package pl.cardioscp.rehab.ble

import java.util.Calendar

/**
 * Protokół Microlife BLE (fff0/fff1/fff2) — port z `microlife.py`.
 * Ramki wychodzące: `4D FF …` + CRC (suma % 256).
 * Odpowiedzi: `4D 3A` + długość BE + payload + CRC.
 */
class MicrolifeSession {
    private enum class State { SetTime, ReadLast, Erase, TurnOff }

    private var state = State.SetTime
    private val buffer = ArrayList<Byte>()
    private var ok = false
    private var sys: Int? = null
    private var dia: Int? = null
    private var pulse: Int? = null
    private var setAtMs: Long = 0L

    fun nextWrite(nowMs: Long = System.currentTimeMillis()): ByteArray {
        buffer.clear()
        return when (state) {
            State.SetTime -> {
                setAtMs = nowMs
                val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
                val year = cal.get(Calendar.YEAR) % 100
                val month = cal.get(Calendar.MONTH) + 1
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val second = cal.get(Calendar.SECOND)
                frame(byteArrayOf(0x4D, 0xFF.toByte(), 0, 8, 13, year.toByte(), month.toByte(), day.toByte(), hour.toByte(), minute.toByte(), second.toByte()))
            }
            State.ReadLast -> byteArrayOf(0x4D, 0xFF.toByte(), 0, 9, 0, 0, 0, 0, 0, 0, 0, 0xFD.toByte(), 82)
            State.Erase -> byteArrayOf(0x4D, 0xFF.toByte(), 0, 3, 3, 0xFD.toByte(), 79)
            State.TurnOff -> {
                // last write — result reported by caller after this
                byteArrayOf(0x4D, 0xFF.toByte(), 0, 2, 4, 82)
            }
        }
    }

    fun afterTurnOffWrite(): BleParseOutcome = if (ok) {
        BleParseOutcome.Done(
            VitalReading(
                kind = BleVitalKind.BP_MICROLIFE,
                systolicMmHg = sys,
                diastolicMmHg = dia,
                pulseBpm = pulse,
                deviceName = "Microlife BP B6",
            ),
        )
    } else {
        BleParseOutcome.Fail("Brak świeżego pomiaru Microlife")
    }

    fun onNotify(chunk: ByteArray, nowMs: Long = System.currentTimeMillis()): BleParseOutcome {
        buffer.addAll(chunk.toList())
        if (buffer.size < 4) return BleParseOutcome.NeedMore
        if ((buffer[0].toInt() and 0xFF) != 0x4D || (buffer[1].toInt() and 0xFF) != 0x3A) {
            buffer.clear()
            return BleParseOutcome.Continue
        }
        val expected = ((buffer[2].toInt() and 0xFF) shl 8) + (buffer[3].toInt() and 0xFF) + 4
        if (buffer.size < expected) return BleParseOutcome.NeedMore
        var crc = 0
        for (i in 0 until expected - 1) crc += buffer[i].toInt() and 0xFF
        if ((crc % 256) != (buffer[expected - 1].toInt() and 0xFF)) {
            buffer.clear()
            return BleParseOutcome.Fail("CRC Microlife")
        }
        when (buffer[4].toInt() and 0xFF) {
            0x81 -> { // ACK
                state = if (state == State.SetTime) State.ReadLast else State.TurnOff
                buffer.clear()
                return BleParseOutcome.Continue
            }
            0x00 -> { // measurements
                state = State.Erase
                if (expected > 52) {
                    sys = buffer[expected - 11].toInt() and 0xFF
                    dia = buffer[expected - 10].toInt() and 0xFF
                    pulse = buffer[expected - 9].toInt() and 0xFF
                    val year = buffer[expected - 8].toInt() and 0xFF
                    val month = buffer[expected - 7].toInt() and 0xFF
                    val day = buffer[expected - 6].toInt() and 0xFF
                    val hour = buffer[expected - 5].toInt() and 0xFF
                    val minute = buffer[expected - 4].toInt() and 0xFF
                    val resultCal = Calendar.getInstance().apply {
                        set(2000 + year, month - 1, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val fresh = resultCal.timeInMillis + 3 * 60_000L > setAtMs
                    ok = (sys ?: 0) > 30 && (dia ?: 0) > 30 && (pulse ?: 0) > 30 && fresh
                }
                buffer.clear()
                return BleParseOutcome.Continue
            }
            else -> {
                buffer.clear()
                return BleParseOutcome.Continue
            }
        }
    }

    fun wantsNextWrite(outcome: BleParseOutcome): Boolean =
        outcome is BleParseOutcome.Continue && state != State.TurnOff ||
            (outcome is BleParseOutcome.Continue && state == State.TurnOff)

    fun isTurnOffPending(): Boolean = state == State.TurnOff

    private fun frame(bodyWithoutCrc: ByteArray): ByteArray {
        var crc = 0
        for (b in bodyWithoutCrc) crc += b.toInt() and 0xFF
        return bodyWithoutCrc + (crc % 256).toByte()
    }
}
