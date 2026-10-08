package pl.cardioscp.rehab.ble

/** Ramki protokołu TaiDoc (TD-3140, TD-3128, TD-8255, TD-2555, TD-4277, …) — jak `taidoc.py`. */
object TaiDocProtocol {
    const val HDR: Int = 0x51
    /** Host → meter (DPS `taidoc.build_frame`). */
    const val FOOTER: Int = 0xA3
    /** Meter → host (TD-42xx / glucometers.tech `direction-in`). */
    const val FOOTER_DEVICE: Int = 0xA5

    const val CMD_READ_DEVICE_CLOCK: Int = 0x23
    const val CMD_READ_STORED_DATA_TIME: Int = 0x25
    const val CMD_READ_STORED_DATA_RESULT: Int = 0x26
    const val CMD_READ_STORED_NUMBER: Int = 0x2B
    /** Ustawienie zegara urządzenia (TD-42xx / TD-4277). */
    const val CMD_SET_DEVICE_CLOCK: Int = 0x33
    const val CMD_START_TEMPERATURE: Int = 0x41
    const val CMD_START_BP: Int = 0x43
    const val CMD_START_STOP_SPO2: Int = 0x47
    const val CMD_TURN_OFF: Int = 0x50
    const val CMD_ENTERING_COMM: Int = 0x54
    const val CMD_SPO2_VALUE_HR: Int = 0x61
    const val CMD_READ_WEIGHT: Int = 0x71
    const val CMD_SET_USER_PROFILE: Int = 0x72

    fun buildFrame(cmd: Int, data: ByteArray? = null, fromDevice: Boolean = false): ByteArray {
        val payload = data ?: ByteArray(4)
        require(payload.size == 4) { "TaiDoc data always 4 bytes" }
        val frame = ByteArray(8)
        frame[0] = HDR.toByte()
        frame[1] = cmd.toByte()
        payload.copyInto(frame, 2)
        frame[6] = (if (fromDevice) FOOTER_DEVICE else FOOTER).toByte()
        var crc = 0
        for (i in 0 until 7) crc += frame[i].toInt() and 0xFF
        frame[7] = (crc % 256).toByte()
        return frame
    }

    fun startBpMeasure(): ByteArray = buildFrame(CMD_START_BP, byteArrayOf(0, 0, 1, 1))
    fun startSpo2(): ByteArray = buildFrame(CMD_START_STOP_SPO2, byteArrayOf(2, 0, 0, 0))
    fun turnOff(): ByteArray = buildFrame(CMD_TURN_OFF, null)
    fun readDeviceClock(): ByteArray = buildFrame(CMD_READ_DEVICE_CLOCK, null)
    fun setDeviceClock(dt: DeviceDateTime): ByteArray =
        buildFrame(CMD_SET_DEVICE_CLOCK, encodeDateTime(dt))
    fun readStoredNumber(userId: Int): ByteArray =
        buildFrame(CMD_READ_STORED_NUMBER, byteArrayOf(userId.toByte(), 0, 0, 0))
    fun readStoredTime(userId: Int): ByteArray =
        buildFrame(CMD_READ_STORED_DATA_TIME, byteArrayOf(0, 0, 0, userId.toByte()))
    fun readStoredResult(userId: Int): ByteArray =
        buildFrame(CMD_READ_STORED_DATA_RESULT, byteArrayOf(0, 0, 0, userId.toByte()))
    /**
     * TD-42xx / TD-4277: indeks rekordu LE w bajtach 0–1 (0 = najnowszy).
     * @see https://protocols.glucometers.tech/taidoc/td42xx.html
     */
    fun readRecordTime(index: Int): ByteArray = buildFrame(
        CMD_READ_STORED_DATA_TIME,
        byteArrayOf(
            (index and 0xFF).toByte(),
            ((index shr 8) and 0xFF).toByte(),
            0,
            0,
        ),
    )
    fun readRecordResult(index: Int): ByteArray = buildFrame(
        CMD_READ_STORED_DATA_RESULT,
        byteArrayOf(
            (index and 0xFF).toByte(),
            ((index shr 8) and 0xFF).toByte(),
            0,
            0,
        ),
    )
    fun readWeight(): ByteArray = buildFrame(CMD_READ_WEIGHT, null)

    fun commandOf(frame: ByteArray): Int? {
        if (frame.size < 2) return null
        if ((frame[0].toInt() and 0xFF) != HDR) return null
        return frame[1].toInt() and 0xFF
    }

    /** Data/czas TaiDoc z 4 bajtów payloadu (jak w `td3128.py` / TD-42xx). */
    data class DeviceDateTime(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
    ) {
        fun toEpochMinutes(): Long {
            // Przybliżony timestamp do porównania ±2 min (bez ZoneId — wystarczy delta).
            val yDay = year * 366L + month * 31L + day
            return yDay * 24L * 60L + hour * 60L + minute
        }
    }

    fun encodeDateTime(dt: DeviceDateTime): ByteArray {
        val yearOff = (dt.year - 2000).coerceIn(0, 127)
        val date = (dt.day and 0b11111) or
            ((dt.month and 0b1111) shl 5) or
            ((yearOff and 0b1111111) shl 9)
        return byteArrayOf(
            (date and 0xFF).toByte(),
            ((date shr 8) and 0xFF).toByte(),
            (dt.minute and 0b111111).toByte(),
            (dt.hour and 0b11111).toByte(),
        )
    }

    fun parseDateTime(data: ByteArray, offset: Int = 2): DeviceDateTime? {
        if (data.size < offset + 4) return null
        val date = (data[offset].toInt() and 0xFF) + ((data[offset + 1].toInt() and 0xFF) shl 8)
        val minute = data[offset + 2].toInt() and 0b111111
        val hour = data[offset + 3].toInt() and 0b11111
        val day = date and 0b11111
        val month = (date shr 5) and 0b1111
        val year = 2000 + ((date shr 9) and 0b1111111)
        // Fabryczny / nieustawiony zegar często ma miesiąc/dzień = 0.
        if (month !in 1..12 || day !in 1..31) return null
        return DeviceDateTime(year, month, day, hour, minute)
    }

    /**
     * Zegar „ustawiony”, gdy rok wygląda na realny (nie fabryczny 2000 / śmieci).
     * Świeżość pomiaru i tak porównujemy względem zegara urządzenia, nie telefonu.
     */
    fun clockLooksSet(
        device: DeviceDateTime,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (device.year < 2020) return false
        val phoneYear = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
            .get(java.util.Calendar.YEAR)
        if (device.year > phoneYear + 1) return false
        return true
    }

    /** True, gdy trzeba wysłać 0x33 — fabryczny zegar albo dryf względem telefonu. */
    fun clockNeedsSync(
        device: DeviceDateTime,
        nowMillis: Long = System.currentTimeMillis(),
        maxDriftMs: Long = 3L * 60L * 1000L,
    ): Boolean {
        if (!clockLooksSet(device, nowMillis)) return true
        val deviceMs = runCatching {
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, device.year)
                set(java.util.Calendar.MONTH, device.month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, device.day)
                set(java.util.Calendar.HOUR_OF_DAY, device.hour)
                set(java.util.Calendar.MINUTE, device.minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull() ?: return true
        return kotlin.math.abs(deviceMs - nowMillis) > maxDriftMs
    }

    fun nowDeviceDateTime(nowMillis: Long = System.currentTimeMillis()): DeviceDateTime {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        return DeviceDateTime(
            year = cal.get(java.util.Calendar.YEAR),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            day = cal.get(java.util.Calendar.DAY_OF_MONTH),
            hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
            minute = cal.get(java.util.Calendar.MINUTE),
        )
    }

    /**
     * Składa niepełne notyfikacje BLE w pełne ramki 8 B (HDR…CRC).
     * Android czasem dzieli wartość charakterystyki — bez bufora giną 0x25/0x26.
     */
    class FrameBuffer {
        private val buf = ArrayList<Byte>(24)

        fun offer(chunk: ByteArray): List<ByteArray> {
            for (b in chunk) buf.add(b)
            val out = ArrayList<ByteArray>()
            while (true) {
                val hdr = buf.indexOfFirst { (it.toInt() and 0xFF) == HDR }
                if (hdr < 0) {
                    buf.clear()
                    break
                }
                if (hdr > 0) repeat(hdr) { buf.removeAt(0) }
                if (buf.size < 8) break
                val frame = ByteArray(8) { buf[it] }
                // Słaba walidacja CRC — suma 7 bajtów % 256.
                var sum = 0
                for (i in 0 until 7) sum += frame[i].toInt() and 0xFF
                if ((sum % 256) != (frame[7].toInt() and 0xFF)) {
                    buf.removeAt(0) // zły HDR — szukaj dalej
                    continue
                }
                repeat(8) { buf.removeAt(0) }
                out += frame
            }
            // Limit śmieci
            if (buf.size > 32) {
                val keep = buf.takeLast(7)
                buf.clear()
                buf.addAll(keep)
            }
            return out
        }

        fun clear() = buf.clear()
    }
}
