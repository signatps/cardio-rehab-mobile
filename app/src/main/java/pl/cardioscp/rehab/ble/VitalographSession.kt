package pl.cardioscp.rehab.ble

/**
 * Pikflometr Vitalograph Lung Monitor BT Smart (seria 4000 BTLE).
 *
 * **Scenariusz główny = API 07424 §10.1 Automatic Single Test Data:**
 * po dmuchnięciu aparat sam pushuje `STX|G|TD|65 ASCII|ETX|BCC` (PEF @ 14).
 * Bez żądania hosta, bez Remote Mode. DPS `lung4000.py` = ten sam nagłówek GTD
 * na BlueGiga; tu Android GATT.
 *
 * Po ramce: ACK `0x06` (§4 ogólny; §10.1 go nie rysuje — DPS też nie ACK).
 * `armWake()` / DI — tylko Remote Mode (§2.3/§5), nie ścieżka ASTD.
 *
 * asma-1 Classic (DI=`C`, SPP) nie idzie GATT — PEF @ 17 gdy CTD jednak dojdzie.
 */
class VitalographSession {
    private val buffer = ArrayList<Byte>(512)
    private val pendingWrites = ArrayDeque<ByteArray>()

    var bytesReceived: Int = 0
        private set
    var lastStatus: String = ""
        private set
    var lastDi: Char? = null
        private set

    /** Po starcie sesji — Device Identification (API §5) jako probe łącza. */
    fun armWake() {
        pendingWrites.clear()
        pendingWrites.addLast(VitalographProtocol.deviceIdentificationRequest())
    }

    /** Probe: DI, potem ewent. AT03 (wersja BT). */
    fun armCommProbe() {
        pendingWrites.clear()
        pendingWrites.addLast(VitalographProtocol.deviceIdentificationRequest())
        pendingWrites.addLast(VitalographProtocol.bluetoothModuleVersionRequest())
    }

    /**
     * Odpowiedź na probe (ACK / DI / AT) — nie kończy sesji ASTD.
     * @return komunikat statusu albo null gdy to nie probe.
     */
    fun consumeProbeFeedback(data: ByteArray): String? {
        if (data.isEmpty()) return null
        if (data.size == 1) {
            val v = data[0].toInt() and 0xFF
            if (v == VitalographProtocol.ACK.toInt() and 0xFF) {
                return "PEF probe: ACK — łącze TX/RX żyje"
            }
            if (v == VitalographProtocol.NAK.toInt() and 0xFF) {
                return "PEF probe: NAK — ponawiam DI…"
            }
        }
        // STX | DI(dst)=V | DI(src)=G | ID…
        if (data.size >= 5 &&
            (data[0].toInt() and 0xFF) == (VitalographProtocol.STX.toInt() and 0xFF)
        ) {
            val id = String(data, 3, 2.coerceAtMost(data.size - 3), Charsets.US_ASCII)
            when (id) {
                VitalographProtocol.MSG_DEVICE_ID -> {
                    val payload = if (data.size > 6) {
                        String(data, 5, (data.size - 7).coerceAtLeast(0), Charsets.US_ASCII)
                            .trim { it <= ' ' || it == '\u0000' }
                    } else {
                        ""
                    }
                    return if (payload.isNotBlank()) {
                        "PEF probe DI OK: $payload — dmuchnij (ASTD)"
                    } else {
                        "PEF probe DI OK — dmuchnij HARD+FAST+LONG (ASTD)"
                    }
                }
                VitalographProtocol.MSG_BT_MODULE -> {
                    val payload = if (data.size > 6) {
                        String(data, 5, (data.size - 7).coerceAtLeast(0), Charsets.US_ASCII)
                            .trim { it <= ' ' || it == '\u0000' }
                    } else {
                        ""
                    }
                    return "PEF probe BT module: ${payload.ifBlank { "OK" }}"
                }
            }
        }
        return null
    }

    fun nextWrite(): ByteArray? = pendingWrites.removeFirstOrNull()

    fun needsWriteAfterNotify(outcome: BleParseOutcome): Boolean =
        pendingWrites.isNotEmpty() &&
            (outcome is BleParseOutcome.Done ||
                outcome is BleParseOutcome.Continue ||
                outcome is BleParseOutcome.NeedMore)

    fun onNotify(data: ByteArray): BleParseOutcome {
        for (b in data) buffer.add(b)
        bytesReceived += data.size
        lastStatus = "Vitalograph: odebrano $bytesReceived B…"
        // Pojedyncze ACK/NAK z urządzenia — ignoruj.
        if (buffer.size == 1) {
            val v = buffer[0].toInt() and 0xFF
            if (v == VitalographProtocol.ACK.toInt() and 0xFF ||
                v == VitalographProtocol.NAK.toInt() and 0xFF
            ) {
                buffer.clear()
                lastStatus = if (v == VitalographProtocol.ACK.toInt() and 0xFF) {
                    "Vitalograph: ACK — czekam na dmuchnięcie…"
                } else {
                    "Vitalograph: NAK — ponawiam DI…"
                }
                if (v == VitalographProtocol.NAK.toInt() and 0xFF) {
                    pendingWrites.addLast(VitalographProtocol.deviceIdentificationRequest())
                }
                return BleParseOutcome.NeedMore
            }
        }
        if (buffer.size > 400) {
            val stx = indexOfAstd()
            if (stx < 0) {
                val keep = buffer.takeLast(8)
                buffer.clear()
                buffer.addAll(keep)
                return BleParseOutcome.NeedMore
            }
            if (stx > 0) repeat(stx) { buffer.removeAt(0) }
        }
        // Po złym BCC / śmieciach — skanuj dalej w tym samym notify.
        var guard = 0
        while (guard++ < 64) {
            // Live notify: jak DPS lung4000 — czekaj ~70 B zanim akceptujesz PEF.
            // Krótkie ramki tylko przez flushPartial (cisza 0,7 s).
            when (val o = tryParse(allowShort = false)) {
                is BleParseOutcome.Done,
                is BleParseOutcome.Fail,
                is BleParseOutcome.Choose,
                -> return o
                BleParseOutcome.Continue -> return o
                BleParseOutcome.NeedMore -> {
                    // Gdy bufor nadal ma ASTD dalej, spróbuj jeszcze raz (po shift o 1).
                    if (indexOfAstd() > 0) {
                        buffer.removeAt(0)
                        continue
                    }
                    return o
                }
            }
        }
        return BleParseOutcome.NeedMore
    }

    /** Wymuś akceptację po ciszy (timer w kliencie BLE) — akceptuj PEF z fragmentów. */
    fun flushPartial(): BleParseOutcome = tryParse(allowShort = true)

    private fun tryParse(allowShort: Boolean): BleParseOutcome {
        val start = indexOfAstd()
        if (start < 0) {
            lastStatus = "Vitalograph: czekam na TD ($bytesReceived B)…"
            return BleParseOutcome.NeedMore
        }
        if (start > 0) repeat(start) { buffer.removeAt(0) }
        val di = buffer[1].toInt().toChar()
        lastDi = di
        val pefOff = VitalographProtocol.pefOffsetForDi(di)
        val minForPef = pefOff + 3
        if (buffer.size < minForPef) {
            lastStatus = "Vitalograph: TD — czekam na PEF (${buffer.size}/$minForPef)…"
            return BleParseOutcome.NeedMore
        }

        // Pełna ramka z ETX+BCC — waliduj checksum gdy możliwe.
        val etxIdx = indexOfEtx(from = VitalographProtocol.ASTD_HEADER_LEN)
        if (etxIdx >= 0 && buffer.size > etxIdx + 1) {
            val frameLen = etxIdx + 2 // ETX + BCC
            val frame = ByteArray(frameLen) { buffer[it] }
            val expect = VitalographProtocol.bcc(frame, 0, etxIdx + 1)
            val got = frame[etxIdx + 1]
            if (expect != got) {
                // DPS lung4000 nie sprawdza BCC — przy ≥70 B i poprawnym PEF akceptuj.
                val pefRaw = String(ByteArray(3) { buffer[pefOff + it] }, Charsets.US_ASCII).trim()
                val pef = pefRaw.toIntOrNull()
                if (buffer.size >= 70 && pef != null && pef in 25..900) {
                    lastStatus = "Vitalograph: PEF=$pef (bez BCC, jak DPS)…"
                    val take = buffer.size.coerceAtMost(VitalographProtocol.BTLE_ASTD_FULL_LEN)
                    val out = ByteArray(take) { buffer[it] }
                    return finishFrame(out, di, pefOff, full = false)
                }
                lastStatus = "Vitalograph: złe BCC — szukam dalej…"
                buffer.removeAt(0)
                return BleParseOutcome.NeedMore
            }
            return finishFrame(frame, di, pefOff, full = true)
        }

        val pefRaw = String(ByteArray(3) { buffer[pefOff + it] }, Charsets.US_ASCII).trim()
        val pef = pefRaw.toIntOrNull()
        if (pef == null || pef !in 25..900) {
            if (buffer.size < VitalographProtocol.BTLE_ASTD_FULL_LEN) {
                lastStatus = "Vitalograph: TD — PEF '$pefRaw' (${buffer.size} B)…"
                return BleParseOutcome.NeedMore
            }
            buffer.removeAt(0)
            return BleParseOutcome.NeedMore
        }
        // Jak DPS lung4000 (≥70 B) — bez allowShort nie kończ na krótkim fragmencie.
        if (!allowShort && buffer.size < 70) {
            lastStatus = "Vitalograph: TD PEF=$pef — czekam (${buffer.size}/70)…"
            return BleParseOutcome.NeedMore
        }
        // Krótka ramka (fragmenty BLE / flush po ciszy) — akceptuj PEF.
        val take = buffer.size.coerceAtMost(VitalographProtocol.BTLE_ASTD_FULL_LEN)
        val out = ByteArray(take) { buffer[it] }
        return finishFrame(out, di, pefOff, full = false)
    }

    private fun finishFrame(
        frame: ByteArray,
        di: Char,
        pefOff: Int,
        full: Boolean,
    ): BleParseOutcome {
        val pefRaw = String(frame, pefOff, 3, Charsets.US_ASCII).trim()
        val pef = pefRaw.toIntOrNull()
        if (pef == null || pef !in 25..900) {
            buffer.removeAt(0)
            return BleParseOutcome.NeedMore
        }
        val astd = VitalographAstd.parse(frame, di, pef)
        val goodFailedQa = astd.goodTestFailedQa
        // Usuń skonsumowane bajty z bufora.
        val consume = if (full) frame.size else frame.size.coerceAtMost(buffer.size)
        repeat(consume.coerceAtMost(buffer.size)) { buffer.removeAt(0) }

        pendingWrites.addLast(VitalographProtocol.ackFrame())
        lastStatus = "Vitalograph: PEF=$pef L/min (${frame.size} B)" +
            if (goodFailedQa) " · słaba jakość (!)" else ""
        return BleParseOutcome.Done(
            VitalReading(
                kind = BleVitalKind.PEF_VITALOGRAPH,
                peakFlowLMin = pef,
                deviceName = when (di) {
                    VitalographProtocol.DI_ASMA1 -> "asma-1"
                    else -> "Lung Monitor BT Smart"
                },
                rawNote = buildString {
                    if (goodFailedQa) append("jakość=!")
                    if (!full) {
                        if (isNotEmpty()) append(" · ")
                        append("ramka ${frame.size}B")
                    }
                },
                spirometry = astd,
            ),
        )
    }

    private fun indexOfAstd(): Int {
        for (i in 0 until buffer.size - 3) {
            if (VitalographProtocol.isAstdHeader(buffer, i)) return i
        }
        return -1
    }

    private fun indexOfEtx(from: Int): Int {
        for (i in from until buffer.size) {
            if ((buffer[i].toInt() and 0xFF) == (VitalographProtocol.ETX.toInt() and 0xFF)) {
                return i
            }
        }
        return -1
    }
}
