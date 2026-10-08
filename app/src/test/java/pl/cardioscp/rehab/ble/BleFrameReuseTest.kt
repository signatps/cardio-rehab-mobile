package pl.cardioscp.rehab.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port testów ramek z CardioSCP-mobile-android CoreTest (BLE, bez EHO12 USB). */
class BleFrameReuseTest {
    @Test
    fun taiDocAndBleParsersMatchDpsPython() {
        val bpFrame = TaiDocProtocol.startBpMeasure()
        assertEquals(8, bpFrame.size)
        assertEquals(0x51, bpFrame[0].toInt() and 0xFF)
        assertEquals(0x43, bpFrame[1].toInt() and 0xFF)
        var crc = 0
        for (i in 0 until 7) crc += bpFrame[i].toInt() and 0xFF
        assertEquals(crc % 256, bpFrame[7].toInt() and 0xFF)

        val td = Td3140Session()
        td.nextWrite()
        val resultFrame = byteArrayOf(0x51, 0x43, 0, 128.toByte(), 80, 70, 0xA3.toByte(), 0)
        crcFix(resultFrame)
        td.onNotify(resultFrame)
        val offAck = byteArrayOf(0x51, 0x50, 0, 0, 0, 0, 0xA3.toByte(), 0)
        crcFix(offAck)
        val enter = byteArrayOf(0x51, 0x54, 0, 0, 0, 0, 0xA3.toByte(), 0)
        crcFix(enter)
        td.onNotify(enter)
        val done = td.onNotify(offAck)
        assertTrue(done is BleParseOutcome.Done)
        val reading = (done as BleParseOutcome.Done).reading
        assertEquals(128, reading.systolicMmHg)
        assertEquals(80, reading.diastolicMmHg)
        assertEquals(70, reading.pulseBpm)

        val spo2 = Td8255Session()
        fun spo2Frame(sat: Int, pulse: Int): ByteArray {
            val f = byteArrayOf(0x51, 0x61, sat.toByte(), 0, pulse.toByte(), 0, 0xA3.toByte(), 0)
            crcFix(f)
            return f
        }
        assertTrue(spo2.onNotify(spo2Frame(0, 0)) is BleParseOutcome.Continue)
        assertEquals(0, spo2.validCount)
        assertTrue(spo2.onNotify(spo2Frame(98, 74)) is BleParseOutcome.Continue)
        assertEquals(1, spo2.validCount)
        val spo2Done = spo2.onNotify(spo2Frame(97, 72))
        assertTrue(spo2Done is BleParseOutcome.Done)
        assertEquals(97, (spo2Done as BleParseOutcome.Done).reading.spo2Percent)
        assertEquals(Spo2Who.Band.NORMAL, Spo2Who.band(97))
        assertEquals(Spo2Who.Band.CAUTION, Spo2Who.band(92))
        assertEquals(Spo2Who.Band.CRITICAL, Spo2Who.band(88))

        assertEquals(BpWho.Band.OPTIMAL, BpWho.band(118, 76))
        assertEquals(BpWho.Band.GRADE1, BpWho.band(148, 92))
        assertEquals(22.5, BmiWho.compute(72.0, 179.0)!!, 0.05)
        assertEquals(TempWho.Band.NORMAL, TempWho.band(36.6))
        assertTrue(BleProfiles.matchesName(BleVitalKind.TEMP_TD1241, "TD-1241"))
        assertTrue(BleProfiles.matchesName(BleVitalKind.BP_AUTO, "Microlife BP B6"))
        // Microlife: nazwa BT „B6 Connect” (bez słowa Microlife).
        assertTrue(BleDeviceIdentity.isMicrolifeName("B6 Connect"))
        assertTrue(BleDeviceIdentity.isMicrolifeName("BP B6 Connect"))
        assertTrue(BleProfiles.scanMatches(BleVitalKind.BP_AUTO, "B6 Connect"))
        assertTrue(BleProfiles.scanMatches(BleVitalKind.BP_MICROLIFE, "B6Connect"))
        assertEquals(BleVitalKind.BP_TD3128, BleDeviceIdentity.resolveBp("TD-3128"))
        assertEquals(BleVitalKind.WEIGHT_CHARDER, BleDeviceIdentity.resolveWeight("Charder"))
        assertTrue(BleDeviceIdentity.isTaiDocAddress("C0:26:AA:BB:CC:DD"))
        assertTrue(!BleDeviceIdentity.isTaiDocName("STUDIO Speaker"))
        assertTrue(BleProfiles.scanMatches(BleVitalKind.BP_AUTO, "TD-3140", "C026AABBCCDD"))
        assertTrue(!BleProfiles.scanMatches(BleVitalKind.BP_AUTO, null, "AA:BB:CC:DD:EE:FF", listOf("0000fff0-0000-1000-8000-00805f9b34fb")))
        // Glikemia: tylko Glucomaxx / TD-4277 — nie TD-8255 (SpO₂).
        assertTrue(BleProfiles.scanMatches(BleVitalKind.GLU_TD4277, "Glucomaxx Connect"))
        assertTrue(BleProfiles.scanMatches(BleVitalKind.GLU_TD4277, "TAIDOC TD4277"))
        assertTrue(!BleProfiles.scanMatches(BleVitalKind.GLU_TD4277, "TAIDOC TD8255"))
        assertTrue(!BleProfiles.scanMatches(BleVitalKind.GLU_TD4277, "TAIDOC", "C026AABBCCDD"))
        // SpO₂: 8255 i 8201
        assertTrue(BleProfiles.scanMatches(BleVitalKind.SPO2_TD8255, "TAIDOC TD8255"))
        assertTrue(BleProfiles.scanMatches(BleVitalKind.SPO2_TD8255, "TD-8201"))
        assertTrue(!BleProfiles.scanMatches(BleVitalKind.SPO2_TD8255, "Glucomaxx Connect"))
        // Termometr tylko 1241
        assertTrue(BleProfiles.scanMatches(BleVitalKind.TEMP_TD1241, "TD-1241"))
        assertTrue(!BleProfiles.scanMatches(BleVitalKind.TEMP_TD1241, "TD-3140"))
        // Ciśnienie: 3140 / 3128 / Microlife — nie sam C026
        assertTrue(BleProfiles.scanMatches(BleVitalKind.BP_AUTO, "TD-3128"))
        assertTrue(!BleProfiles.scanMatches(BleVitalKind.BP_AUTO, "TAIDOC TD8255"))
        assertTrue(!BleProfiles.scanMatches(BleVitalKind.BP_AUTO, null, "C026AABBCCDD"))
    }

    @Test
    fun healthThermometerParsesIeeeFloatAndIgnoresShortJunk() {
        val ieee = byteArrayOf(0x00, 0x6E, 0x01, 0x00, 0xFF.toByte())
        val done = HealthThermometerParser.parse(ieee)
        assertTrue(done is BleParseOutcome.Done)
        assertEquals(36.6, (done as BleParseOutcome.Done).reading.temperatureC!!, 0.05)
        assertTrue(HealthThermometerParser.parse(byteArrayOf(0x00, 0x01)) is BleParseOutcome.NeedMore)
    }

    @Test
    fun td3128ReadsFreshStoredPressure() {
        // Zegar urządzenia = „teraz” → bez 0x33, od razu turn-off.
        val nowMs = java.util.Calendar.getInstance().apply {
            set(2024, java.util.Calendar.JUNE, 15, 12, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val s = Td3128Session(nowMillis = { nowMs })
        assertArrayEquals(TaiDocProtocol.readDeviceClock(), s.nextWrite())
        val yearOff = 24
        val month = 6
        val day = 15
        val date = day or (month shl 5) or (yearOff shl 9)
        fun clockFrame(cmd: Int, d: Int, minute: Int, hour: Int): ByteArray {
            val payload = byteArrayOf(
                (d and 0xFF).toByte(),
                ((d shr 8) and 0xFF).toByte(),
                minute.toByte(),
                hour.toByte(),
            )
            return TaiDocProtocol.buildFrame(cmd, payload)
        }
        s.onNotify(clockFrame(TaiDocProtocol.CMD_READ_DEVICE_CLOCK, date, 30, 12))
        assertArrayEquals(TaiDocProtocol.readStoredNumber(0), s.nextWrite())
        s.onNotify(TaiDocProtocol.buildFrame(TaiDocProtocol.CMD_READ_STORED_NUMBER, byteArrayOf(1, 0, 0, 0)))
        assertArrayEquals(TaiDocProtocol.readStoredTime(0), s.nextWrite())
        s.onNotify(clockFrame(TaiDocProtocol.CMD_READ_STORED_DATA_TIME, date, 30, 12))
        assertArrayEquals(TaiDocProtocol.readStoredResult(0), s.nextWrite())
        s.onNotify(TaiDocProtocol.buildFrame(TaiDocProtocol.CMD_READ_STORED_DATA_RESULT, byteArrayOf(128.toByte(), 0, 82, 70)))
        assertArrayEquals(TaiDocProtocol.turnOff(), s.nextWrite())
        val done = s.onNotify(TaiDocProtocol.buildFrame(TaiDocProtocol.CMD_TURN_OFF, null, fromDevice = true))
        assertTrue(done is BleParseOutcome.Done)
        val r = (done as BleParseOutcome.Done).reading
        assertEquals(128, r.systolicMmHg)
        assertEquals(82, r.diastolicMmHg)
        assertEquals(70, r.pulseBpm)
    }

    @Test
    fun td3128SetsClockAfterMemoryWhenDrifted() {
        val nowMs = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 8, 14, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val s = Td3128Session(nowMillis = { nowMs })
        s.nextWrite()
        // Device clock: 2024-06-15 12:30 — wyraźny dryf względem telefonu.
        val yearOff = 24
        val month = 6
        val day = 15
        val date = day or (month shl 5) or (yearOff shl 9)
        fun clockFrame(cmd: Int, d: Int, minute: Int, hour: Int): ByteArray {
            val payload = byteArrayOf(
                (d and 0xFF).toByte(),
                ((d shr 8) and 0xFF).toByte(),
                minute.toByte(),
                hour.toByte(),
            )
            return TaiDocProtocol.buildFrame(cmd, payload, fromDevice = true)
        }
        s.onNotify(clockFrame(TaiDocProtocol.CMD_READ_DEVICE_CLOCK, date, 30, 12))
        s.nextWrite()
        s.onNotify(
            TaiDocProtocol.buildFrame(
                TaiDocProtocol.CMD_READ_STORED_NUMBER,
                byteArrayOf(1, 0, 0, 0),
                fromDevice = true,
            ),
        )
        s.nextWrite()
        s.onNotify(clockFrame(TaiDocProtocol.CMD_READ_STORED_DATA_TIME, date, 30, 12))
        s.nextWrite()
        s.onNotify(
            TaiDocProtocol.buildFrame(
                TaiDocProtocol.CMD_READ_STORED_DATA_RESULT,
                byteArrayOf(128.toByte(), 0, 82, 70),
                fromDevice = true,
            ),
        )
        val setClock = s.nextWrite()!!
        assertEquals(TaiDocProtocol.CMD_SET_DEVICE_CLOCK, TaiDocProtocol.commandOf(setClock))
        assertArrayEquals(
            TaiDocProtocol.setDeviceClock(TaiDocProtocol.nowDeviceDateTime(nowMs)),
            setClock,
        )
        s.onNotify(
            TaiDocProtocol.buildFrame(
                TaiDocProtocol.CMD_SET_DEVICE_CLOCK,
                TaiDocProtocol.encodeDateTime(TaiDocProtocol.nowDeviceDateTime(nowMs)),
                fromDevice = true,
            ),
        )
        assertArrayEquals(TaiDocProtocol.turnOff(), s.nextWrite())
        val done = s.onNotify(TaiDocProtocol.buildFrame(TaiDocProtocol.CMD_TURN_OFF, null, fromDevice = true))
        assertTrue(done is BleParseOutcome.Done)
        assertEquals(128, (done as BleParseOutcome.Done).reading.systolicMmHg)
    }

    @Test
    fun td2555ParsesWeightFromMultipleLayouts() {
        val s = Td2555Session()
        s.armListen()
        val done = s.onNotify(byteArrayOf(0x00, 0x00, 0x02, 0xC9.toByte(), 0x07, 0x00, 0x02))
        assertTrue(done is BleParseOutcome.Done)
        assertEquals(71.3, (done as BleParseOutcome.Done).reading.weightKg!!, 0.05)
        assertEquals(
            78.2,
            WeightParser.parseAny(
                TaiDocProtocol.buildFrame(TaiDocProtocol.CMD_READ_WEIGHT, byteArrayOf(0x0E, 0x03, 0, 0)),
            )!!,
            0.05,
        )
        assertEquals(78.2, WeightParser.parseAny(byteArrayOf(0x00, 0x18, 0x3D))!!, 0.05)
    }

    @Test
    fun ixellenceParsesStableWeightFromManufacturerData() {
        val companyId = 0xFFFF
        val payload = byteArrayOf(
            0x02, 0xC9.toByte(),
            0, 0, 0, 0,
            0x01,
            0, 0, 0, 0, 0,
        )
        val session = IxellenceSession()
        val done = IxellenceSession.parseAdvertisement(companyId, payload, session)
        assertTrue(done is BleParseOutcome.Done)
        assertEquals(71.3, (done as BleParseOutcome.Done).reading.weightKg!!, 0.05)
        val s2 = IxellenceSession()
        val unstable = payload.copyOf().also { it[6] = 0x00 }
        assertTrue(IxellenceSession.parseAdvertisement(companyId, unstable, s2) is BleParseOutcome.Continue)
        val stable = payload.copyOf().also { it[6] = 0x01 }
        val done2 = IxellenceSession.parseAdvertisement(companyId, stable, s2)
        assertTrue(done2 is BleParseOutcome.Done)
        assertEquals(71.3, (done2 as BleParseOutcome.Done).reading.weightKg!!, 0.05)
    }

    @Test
    fun vitalographParsesBtleAstdAndQlabsInr() {
        val frame = VitalographProtocol.buildBtleAstdSample(pef = 420)
        assertEquals(71, frame.size)
        val session = VitalographSession()
        val done = session.onNotify(frame)
        assertTrue(done is BleParseOutcome.Done)
        val pef = (done as BleParseOutcome.Done).reading
        assertEquals(420, pef.peakFlowLMin)
        assertEquals(BleVitalKind.PEF_VITALOGRAPH, pef.kind)
        assertEquals(3.27, pef.spirometry?.fev1L ?: 0.0, 0.01)

        val hello = QlabsProtocol.buildFrame("hello client")
        assertTrue(hello.size >= 5)
        val q = QlabsSession(QlabsVariant.V3)
        q.nextWrite()
        q.markHostHelloSent()
        assertTrue(q.onNotify(QlabsProtocol.buildFrame("success")) is BleParseOutcome.Continue)
        q.nextWrite()
        // Po świeżym wyniku sesja pyta o pełną listę historii (przycisk importu w UI).
        val afterInr = q.onNotify(QlabsProtocol.buildFrame("INR: 2.10"))
        assertTrue(afterInr is BleParseOutcome.Continue)
        val listCmd = q.nextWrite()
        assertTrue(listCmd != null)
        assertTrue(String(listCmd!!, Charsets.US_ASCII).contains("get result list"))
        val inrDone = q.onNotify(QlabsProtocol.buildFrame("result list {}"))
        assertTrue(inrDone is BleParseOutcome.Done)
        assertEquals(2.10, (inrDone as BleParseOutcome.Done).reading.inrValue!!, 0.01)
        assertEquals(2.10, QlabsProtocol.parseInr("INR: 2.10").getOrThrow(), 0.01)
    }

    @Test
    fun microlifeParsesFresh4d3aMeasurement() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 6, 15, 20, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMs = cal.timeInMillis
        val s = MicrolifeSession()
        val setTime = s.nextWrite(nowMs)
        assertEquals(0x4D.toByte(), setTime[0])
        assertEquals(0xFF.toByte(), setTime[1])
        assertTrue(s.onNotify(microlifeAck()) is BleParseOutcome.Continue)
        val meas = microlifeMeasurement(128, 82, 70, year = 26, month = 10, day = 6, hour = 15, minute = 20)
        assertTrue(s.onNotify(meas) is BleParseOutcome.Continue)
        s.nextWrite(nowMs)
        assertTrue(s.onNotify(microlifeAck()) is BleParseOutcome.Continue)
        assertTrue(s.isTurnOffPending())
        val done = s.afterTurnOffWrite()
        assertTrue(done is BleParseOutcome.Done)
        val r = (done as BleParseOutcome.Done).reading
        assertEquals(128, r.systolicMmHg)
        assertEquals(82, r.diastolicMmHg)
        assertEquals(70, r.pulseBpm)
        assertEquals(BleVitalKind.BP_MICROLIFE, r.kind)
        val bad = meas.copyOf()
        bad[bad.lastIndex] = (bad.last().toInt() + 1).toByte()
        assertTrue(MicrolifeSession().onNotify(bad) is BleParseOutcome.Fail)
        assertTrue(MicrolifeSession().onNotify(byteArrayOf(0x4D, 0x3A, 0)) is BleParseOutcome.NeedMore)
    }

    @Test
    fun charderNeedsThreeStable310ByteReadings() {
        assertEquals(0x50.toByte(), CharderSession().pollCommand().single())
        assertTrue(CharderSession().onNotify(ByteArray(40) { 0x20 }) is BleParseOutcome.NeedMore)
        val s = CharderSession()
        val frame = charderFrame("72.4")
        assertTrue(s.onNotify(frame) is BleParseOutcome.Continue)
        assertTrue(s.onNotify(frame) is BleParseOutcome.Continue)
        val done = s.onNotify(frame)
        assertTrue(done is BleParseOutcome.Done)
        val kg = (done as BleParseOutcome.Done).reading
        assertEquals(72.4, kg.weightKg!!, 0.05)
        assertEquals(BleVitalKind.WEIGHT_CHARDER, kg.kind)
        val lb = CharderSession()
        val pounds = charderFrame("50.0", unit = 'l')
        repeat(2) { assertTrue(lb.onNotify(pounds) is BleParseOutcome.Continue) }
        val lbDone = lb.onNotify(pounds)
        assertTrue(lbDone is BleParseOutcome.Done)
        assertEquals(22.7, (lbDone as BleParseOutcome.Done).reading.weightKg!!, 0.05)
    }

    @Test
    fun frameBufferAssemblesSplitTaiDocChunks() {
        val full = TaiDocProtocol.startBpMeasure()
        val buf = TaiDocProtocol.FrameBuffer()
        assertTrue(buf.offer(full.copyOfRange(0, 3)).isEmpty())
        val frames = buf.offer(full.copyOfRange(3, 8))
        assertEquals(1, frames.size)
        assertArrayEquals(full, frames.single())
    }

    private fun crcFix(frame: ByteArray) {
        var c = 0
        for (i in 0 until 7) c += frame[i].toInt() and 0xFF
        frame[7] = (c % 256).toByte()
    }

    private fun microlifeAck(): ByteArray {
        val buf = byteArrayOf(0x4D, 0x3A, 0, 2, 0x81.toByte(), 0)
        var crc = 0
        for (i in 0 until 5) crc += buf[i].toInt() and 0xFF
        buf[5] = (crc % 256).toByte()
        return buf
    }

    private fun microlifeMeasurement(
        sys: Int,
        dia: Int,
        pulse: Int,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): ByteArray {
        val expected = 60
        val buf = ByteArray(expected)
        buf[0] = 0x4D
        buf[1] = 0x3A
        val be = expected - 4
        buf[2] = ((be shr 8) and 0xFF).toByte()
        buf[3] = (be and 0xFF).toByte()
        buf[4] = 0x00
        buf[expected - 11] = sys.toByte()
        buf[expected - 10] = dia.toByte()
        buf[expected - 9] = pulse.toByte()
        buf[expected - 8] = year.toByte()
        buf[expected - 7] = month.toByte()
        buf[expected - 6] = day.toByte()
        buf[expected - 5] = hour.toByte()
        buf[expected - 4] = minute.toByte()
        var crc = 0
        for (i in 0 until expected - 1) crc += buf[i].toInt() and 0xFF
        buf[expected - 1] = (crc % 256).toByte()
        return buf
    }

    private fun charderFrame(weight: String, unit: Char = 'k'): ByteArray {
        val buf = ByteArray(310) { 0x20 }
        val chars = weight.padEnd(7).toByteArray(Charsets.ISO_8859_1)
        chars.copyInto(buf, 173, 0, minOf(chars.size, 7))
        buf[181] = unit.code.toByte()
        return buf
    }
}
