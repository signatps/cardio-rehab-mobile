package pl.cardioscp.rehab.ble

/** Wariant protokołu qLabs — z nazwy BLE (V3 w nazwie → V3, inaczej V1). */
enum class QlabsVariant { V1, V3 }

/**
 * Micropoint qLabs® ElectroMeter — V1.0 (Q-1/Q-2) oraz V3.06 (Q-3 / QV-3).
 *
 * Ramka: STX `0x02` + COMMAND (ASCII) + ETX `0x03` + CRC16 Modbus RTU (L, H).
 *
 * Host: `hello client` → `success` → `get result data 0`.
 * V1 (FFF1): pad do **20 B** (ATT MTU 23; protokół „≥18”).
 * V3/Q-3: **bez paddingu**, kanał **NUS** (jak 0.9.45 gdy działało).
 * FFF0-first + pad 18 na Q3 (0.9.68–0.9.73) → GATT 13 ×4.
 */
object QlabsProtocol {
    const val STX: Byte = 0x02
    const val ETX: Byte = 0x03
    /**
     * Pad write ≥20 B na FFF1 (ATT max przy MTU 23). V1 „≥18 B”; nie stosować na NUS/V3.
     */
    const val MIN_BLE_WRITE_BYTES: Int = 20
    /** V1 protokół: minimum 18 — diagnostyka / bare fallback. */
    const val PROTOCOL_MIN_WRITE_BYTES: Int = 18
    const val MAX_RESULT_INDEX: Int = 40

    const val CMD_HELLO_CLIENT: String = "hello client"
    const val CMD_GET_INFORMATION: String = "get information"
    const val CMD_GET_RESULT_LIST: String = "get result list"
    fun resultDataCommand(index: Int): String = "get result data $index"

    fun crc16Modbus(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0xFFFF
        val end = offset + length
        for (i in offset until end) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    fun buildFrame(command: String, padToBleMin: Boolean = false): ByteArray {
        val cmd = command.toByteArray(Charsets.US_ASCII)
        val body = ByteArray(2 + cmd.size)
        body[0] = STX
        System.arraycopy(cmd, 0, body, 1, cmd.size)
        body[body.lastIndex] = ETX
        val crc = crc16Modbus(body)
        val frame = body + byteArrayOf((crc and 0xFF).toByte(), ((crc ushr 8) and 0xFF).toByte())
        if (!padToBleMin || frame.size >= MIN_BLE_WRITE_BYTES) return frame
        return frame + ByteArray(MIN_BLE_WRITE_BYTES - frame.size)
    }

    /** Jak [buildFrame] z paddingiem spacjami do ≥[MIN_BLE_WRITE_BYTES]. */
    fun buildFramePaddedWithSpaces(command: String): ByteArray {
        val frame = buildFrame(command, padToBleMin = false)
        if (frame.size >= MIN_BLE_WRITE_BYTES) return frame
        return frame + ByteArray(MIN_BLE_WRITE_BYTES - frame.size) { 0x20 }
    }

    /** Usuń trailing zera po CRC (pad BLE) — bare hello = 16 B. */
    fun stripBlePadding(frame: ByteArray): ByteArray {
        if (frame.size <= 5) return frame
        var end = frame.size
        while (end > 5 && frame[end - 1] == 0.toByte()) end--
        return if (end == frame.size) frame else frame.copyOf(end)
    }

    fun extractCommands(buffer: MutableList<Byte>): List<String> {
        val out = ArrayList<String>()
        while (buffer.size >= 5) {
            val stxIdx = buffer.indexOfFirst { (it.toInt() and 0xFF) == (STX.toInt() and 0xFF) }
            if (stxIdx < 0) {
                // V1 bywa ASCII bez STX (`success`). Nie kasuj — [extractCommandsLenient].
                break
            }
            if (stxIdx > 0) {
                // Bare ASCII przed kolejnym STX (np. success po echo hello) — nie kasuj.
                val prefix = ByteArray(stxIdx) { buffer[it] }
                val ascii = String(prefix, Charsets.US_ASCII)
                if (ascii.contains("success", ignoreCase = true) ||
                    ascii.contains("hello", ignoreCase = true) ||
                    ascii.contains("INR", ignoreCase = true) ||
                    ascii.contains("result", ignoreCase = true)
                ) {
                    out.addAll(extractCommandsLenient(prefix.toMutableList()))
                }
                repeat(stxIdx) { buffer.removeAt(0) }
            }
            if (buffer.size < 5) break
            val etxIdx = (1 until buffer.size - 2).firstOrNull {
                (buffer[it].toInt() and 0xFF) == (ETX.toInt() and 0xFF)
            } ?: break
            if (buffer.size < etxIdx + 3) break
            val frameLen = etxIdx + 1
            val frame = ByteArray(frameLen) { buffer[it] }
            val crcL = buffer[etxIdx + 1].toInt() and 0xFF
            val crcH = buffer[etxIdx + 2].toInt() and 0xFF
            val got = crcL or (crcH shl 8)
            val expect = crc16Modbus(frame)
            if (got != expect) {
                buffer.removeAt(0)
                continue
            }
            out.add(String(frame, 1, frameLen - 2, Charsets.US_ASCII))
            repeat(etxIdx + 3) { buffer.removeAt(0) }
            // V1: padding zerami po CRC — nie zostawiaj śmieci przed ASCII `success`.
            while (buffer.isNotEmpty() && buffer[0].toInt() == 0) buffer.removeAt(0)
        }
        // Po ramkach STX — bare ASCII w ogonie (success bez STX), tylko gdy coś już wyjęto.
        if (out.isNotEmpty() && buffer.isNotEmpty()) {
            val ascii = String(buffer.toByteArray(), Charsets.US_ASCII)
            if (ascii.contains("success", ignoreCase = true) ||
                ascii.contains("hello", ignoreCase = true) ||
                ascii.contains("INR", ignoreCase = true) ||
                ascii.contains("result", ignoreCase = true)
            ) {
                out.addAll(extractCommandsLenient(ArrayList(buffer)).also {
                    if (it.isNotEmpty()) buffer.clear()
                })
            }
        }
        return out
    }

    fun extractCommandsLenient(buffer: MutableList<Byte>): List<String> {
        val out = ArrayList<String>()
        while (buffer.size >= 3) {
            val stxIdx = buffer.indexOfFirst { (it.toInt() and 0xFF) == (STX.toInt() and 0xFF) }
            if (stxIdx < 0) {
                val ascii = String(buffer.toByteArray(), Charsets.US_ASCII)
                if (ascii.contains("INR", ignoreCase = true) ||
                    ascii.contains("SHOWRES", ignoreCase = true) ||
                    ascii.contains("result", ignoreCase = true) ||
                    ascii.contains("success", ignoreCase = true) ||
                    ascii.contains("hello", ignoreCase = true)
                ) {
                    out.add(ascii.trim { it.code <= 0x20 })
                    buffer.clear()
                }
                break
            }
            if (stxIdx > 0) {
                repeat(stxIdx) { buffer.removeAt(0) }
            }
            val etxIdx = (1 until buffer.size).firstOrNull {
                (buffer[it].toInt() and 0xFF) == (ETX.toInt() and 0xFF)
            } ?: break
            out.add(String(ByteArray(etxIdx - 1) { buffer[it + 1] }, Charsets.US_ASCII))
            val drop = if (buffer.size >= etxIdx + 3) etxIdx + 3 else etxIdx + 1
            repeat(drop) { buffer.removeAt(0) }
        }
        return out
    }

    fun parseResultList(command: String): List<Int> {
        val body = Regex("""result\s*list\s*\{([^}]*)\}""", RegexOption.IGNORE_CASE)
            .find(command)?.groupValues?.get(1)?.trim().orEmpty()
        if (body.isEmpty()) return emptyList()
        val out = LinkedHashSet<Int>()
        for (part in body.split(',')) {
            val token = part.trim()
            if (token.isEmpty()) continue
            val range = token.split('-')
            when (range.size) {
                1 -> token.toIntOrNull()?.let { out += it }
                else -> {
                    val a = range[0].trim().toIntOrNull() ?: continue
                    val b = range[1].trim().toIntOrNull() ?: continue
                    val lo = minOf(a, b)
                    val hi = maxOf(a, b)
                    for (i in lo..hi) out += i
                }
            }
        }
        return out.toList()
    }

    fun parseInr(commandOrBody: String): Result<Double> {
        val body = commandOrBody.trim()
        if (body.equals("fail", ignoreCase = true) || body.equals("lost", ignoreCase = true)) {
            return Result.failure(IllegalStateException("qLabs: $body (brak wyniku)"))
        }
        Regex("""INR\s*>\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(body)?.let { m ->
            val inr = m.groupValues[1].toDoubleOrNull()
                ?: return Result.failure(IllegalStateException("qLabs INR>: nieparsowalne"))
            return Result.success(inr)
        }
        val withCode = Regex("""INR\s*:\s*(E\d{3})(?:#([\d.]+))?""", RegexOption.IGNORE_CASE)
            .find(body)
        if (withCode != null) {
            val code = withCode.groupValues[1].uppercase()
            val value = withCode.groupValues[2]
            if (code != "E000") {
                return Result.failure(IllegalStateException("qLabs INR błąd $code"))
            }
            if (value.isBlank()) {
                return Result.failure(IllegalStateException("qLabs INR: E000 bez wartości"))
            }
            val inr = value.toDoubleOrNull()
                ?: return Result.failure(IllegalStateException("qLabs INR: nieparsowalne '$value'"))
            return Result.success(inr)
        }
        val plain = Regex("""INR\s*:\s*[<>]?\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(body)
        if (plain != null) {
            val inr = plain.groupValues[1].toDoubleOrNull()
                ?: return Result.failure(IllegalStateException("qLabs INR: nieparsowalne"))
            return Result.success(inr)
        }
        return Result.failure(IllegalStateException("qLabs: brak pola INR w odpowiedzi"))
    }

    fun extractPtNote(body: String): String {
        val m = Regex("""PT\s*:\s*(?:E\d{3}#)?([\d.]+)\s*s?""", RegexOption.IGNORE_CASE).find(body)
        return m?.groupValues?.get(1)?.let { "PT=${it}s" }.orEmpty()
    }

    fun extractDateNote(body: String): String {
        val m = Regex(
            """date\s*:\s*([\d./\-]+\s*[\d:]*)""",
            RegexOption.IGNORE_CASE,
        ).find(body)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }

    fun parseResultDateMs(body: String): Long? {
        val m = Regex(
            """date\s*:\s*(\d{1,2})/(\d{1,2})/(\d{2,4})(?:\s+(\d{1,2}):(\d{2}))?""",
            RegexOption.IGNORE_CASE,
        ).find(body) ?: return null
        val day = m.groupValues[1].toIntOrNull() ?: return null
        val month = m.groupValues[2].toIntOrNull() ?: return null
        var year = m.groupValues[3].toIntOrNull() ?: return null
        if (year < 100) year += 2000
        val hour = m.groupValues[4].toIntOrNull() ?: 0
        val minute = m.groupValues[5].toIntOrNull() ?: 0
        return runCatching {
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month - 1)
                set(java.util.Calendar.DAY_OF_MONTH, day)
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
    }

    fun isFreshResult(body: String, nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 24L * 60 * 60 * 1000): Boolean {
        val at = parseResultDateMs(body) ?: return true
        return nowMs - at in 0..maxAgeMs
    }
}

/**
 * Sesja BLE: [variant] V3 lub V1.
 * `hello client` → `success` → `get result data 0`.
 * Nieświeży wynik → historia + [BleParseOutcome.Choose].
 */
class QlabsSession(
    private val variant: QlabsVariant = QlabsVariant.V3,
    @Suppress("UNUSED_PARAMETER") deviceName: String? = null,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    /** V1→FFF1: pad 20 B; V3→NUS: bez pad. Po failover V1→NUS wyłącz pad. */
    private var pad = variant == QlabsVariant.V1
    private val buffer = ArrayList<Byte>(512)
    private val writeQueue = ArrayDeque<ByteArray>()
    private var phase = Phase.WAIT_ONLINE
    private var resultIndex = 0
    private var pendingIndices: MutableList<Int> = mutableListOf()
    private var askedList = false
    private var lenientPasses = 0
    private var helloRetries = 0
    /** True po wysłaniu naszego `hello client` — RX hello wtedy = echo, nie master. */
    private var hostHelloSent = false
    private var collectHistory = false
    private val history = ArrayList<VitalHistoryOption>()
    /** Świeży wynik indeksu 0 — UI pokazuje go od razu + przycisk pełnej historii. */
    private var freshReading: VitalReading? = null

    private enum class Phase { WAIT_ONLINE, WAIT_INFO, WAIT_LIST, WAIT_RESULT, DONE }

    companion object {
        /** Pełna historia qLabs (protokół do [QlabsProtocol.MAX_RESULT_INDEX]). */
        const val MAX_HISTORY: Int = QlabsProtocol.MAX_RESULT_INDEX + 1
    }

    init {
        // V1: protokół §2.2 — pairing zbędny; BTlogin (§5.7) bez odpowiedzi
        // blokował kolejkę (kickProtocol brał tylko pierwszą ramkę). Od razu hello.
        writeQueue.addLast(frame(QlabsProtocol.CMD_HELLO_CLIENT))
    }

    /** Po przełączeniu write na NUS (V1 failover) — bare jak V3 (pad 20 psuło NUS). */
    fun useNusFraming() {
        pad = false
    }

    fun usesBlePadding(): Boolean = pad

    private fun frame(command: String): ByteArray =
        QlabsProtocol.buildFrame(command, padToBleMin = pad)

    private fun isHelloFrame(bytes: ByteArray): Boolean {
        val ascii = String(bytes, Charsets.US_ASCII)
        return ascii.contains(QlabsProtocol.CMD_HELLO_CLIENT, ignoreCase = true)
    }

    fun nextWrite(): ByteArray? {
        if (writeQueue.isEmpty()) return null
        return writeQueue.removeFirst()
    }
    fun needsWriteAfterNotify(outcome: BleParseOutcome): Boolean =
        (outcome is BleParseOutcome.Continue || outcome is BleParseOutcome.NeedMore) &&
            writeQueue.isNotEmpty()

    /** GATT przyjął write hello — dopiero wtedy RX hello = echo, nie master. */
    fun markHostHelloSent() {
        hostHelloSent = true
    }

    fun isHostHelloSent(): Boolean = hostHelloSent

    /** Ponów hello — V1 agresywnie; V3 max 2× przy ciszy (flood → E024). */
    fun retryHelloIfWaiting(): ByteArray? {
        if (phase != Phase.WAIT_ONLINE) return null
        val max = if (variant == QlabsVariant.V1) 8 else 2
        if (helloRetries >= max) return null
        helloRetries++
        // hostHelloSent ustawia host po przyjęciu write — nie tu.
        return QlabsProtocol.buildFrame(QlabsProtocol.CMD_HELLO_CLIENT, padToBleMin = pad)
    }

    fun helloRetryCount(): Int = helloRetries

    fun onNotify(chunk: ByteArray): BleParseOutcome {
        for (b in chunk) buffer.add(b)
        var commands = QlabsProtocol.extractCommands(buffer)
        if (commands.isEmpty()) {
            val ascii = String(buffer.toByteArray(), Charsets.US_ASCII)
            val looksOnline = ascii.contains("success", ignoreCase = true) ||
                ascii.contains("hello", ignoreCase = true) ||
                ascii.contains("INR", ignoreCase = true) ||
                ascii.contains("result", ignoreCase = true)
            // `success` = 7 B — wcześniej próg 8 + clear w extractCommands gubił odpowiedź V1.
            if (looksOnline && (buffer.size >= 7 || lenientPasses >= 1)) {
                commands = QlabsProtocol.extractCommandsLenient(ArrayList(buffer)).also {
                    if (it.isNotEmpty()) buffer.clear()
                }
            } else if (buffer.size >= 16) {
                lenientPasses += 1
                if (lenientPasses >= 2 || buffer.size >= 120) {
                    commands = QlabsProtocol.extractCommandsLenient(ArrayList(buffer)).also {
                        if (it.isNotEmpty()) buffer.clear()
                    }
                }
            }
        }
        if (commands.isEmpty()) return BleParseOutcome.NeedMore
        lenientPasses = 0
        for (cmd in commands) {
            when (phase) {
                Phase.WAIT_ONLINE -> when {
                    cmd.equals("success", ignoreCase = true) -> {
                        // V1 API §6.1: po success → get information → get result list → get result data.
                        // V3: skrót do get result data 0 (działa na NUS).
                        if (variant == QlabsVariant.V1) {
                            phase = Phase.WAIT_INFO
                            writeQueue.addLast(frame(QlabsProtocol.CMD_GET_INFORMATION))
                        } else {
                            phase = Phase.WAIT_RESULT
                            enqueueResultRequest(0)
                        }
                        return BleParseOutcome.Continue
                    }
                    cmd.equals("fail", ignoreCase = true) ||
                        cmd.equals("lost", ignoreCase = true) -> {
                        val maxRetry = if (variant == QlabsVariant.V1) 6 else 1
                        if (helloRetries < maxRetry) {
                            helloRetries++
                            writeQueue.addLast(frame(QlabsProtocol.CMD_HELLO_CLIENT))
                            return BleParseOutcome.Continue
                        }
                        return BleParseOutcome.Fail("qLabs hello: $cmd")
                    }
                    cmd.startsWith("hello", ignoreCase = true) -> {
                        val deviceHello = cmd.contains("server", ignoreCase = true) || !hostHelloSent
                        if (deviceHello) {
                            // Meter master / §5.1 hello server — success, bez ponownego hello.
                            writeQueue.clear()
                            writeQueue.addLast(frame("success"))
                            if (variant == QlabsVariant.V1) {
                                phase = Phase.WAIT_INFO
                                writeQueue.addLast(frame(QlabsProtocol.CMD_GET_INFORMATION))
                            } else {
                                phase = Phase.WAIT_RESULT
                                enqueueResultRequest(0)
                            }
                            return BleParseOutcome.Continue
                        }
                        // Echo naszego hello client — nie return: w tej samej paczce bywa `success`.
                        continue
                    }
                    looksLikeResult(cmd) -> {
                        phase = Phase.WAIT_RESULT
                        return handleResultCommand(cmd)
                    }
                    cmd.contains("result list", ignoreCase = true) ||
                        cmd.contains("resultlist", ignoreCase = true) -> {
                        phase = Phase.WAIT_LIST
                        return handleResultList(cmd)
                    }
                }
                Phase.WAIT_INFO -> when {
                    cmd.startsWith("information", ignoreCase = true) ||
                        cmd.contains("information{", ignoreCase = true) ||
                        cmd.equals("success", ignoreCase = true) -> {
                        // Po get information (API §6.1 krok 3–5) → get result list.
                        askedList = true
                        phase = Phase.WAIT_LIST
                        writeQueue.addLast(frame(QlabsProtocol.CMD_GET_RESULT_LIST))
                        return BleParseOutcome.Continue
                    }
                    cmd.equals("fail", ignoreCase = true) ||
                        cmd.equals("lost", ignoreCase = true) -> {
                        // Brak info — i tak spróbuj listę / wynik 0.
                        askedList = true
                        phase = Phase.WAIT_LIST
                        writeQueue.addLast(frame(QlabsProtocol.CMD_GET_RESULT_LIST))
                        return BleParseOutcome.Continue
                    }
                    looksLikeResult(cmd) -> {
                        phase = Phase.WAIT_RESULT
                        return handleResultCommand(cmd)
                    }
                    cmd.contains("result list", ignoreCase = true) ||
                        cmd.contains("resultlist", ignoreCase = true) -> {
                        phase = Phase.WAIT_LIST
                        return handleResultList(cmd)
                    }
                }
                Phase.WAIT_LIST -> when {
                    cmd.contains("result list", ignoreCase = true) ||
                        cmd.contains("resultlist", ignoreCase = true) ->
                        return handleResultList(cmd)
                    cmd.equals("fail", ignoreCase = true) ||
                        cmd.equals("lost", ignoreCase = true) -> {
                        if (collectHistory) return finishHistoryOrFail()
                        phase = Phase.WAIT_RESULT
                        enqueueResultRequest(0)
                        return BleParseOutcome.Continue
                    }
                    cmd.equals("success", ignoreCase = true) -> {
                        if (!askedList) {
                            askedList = true
                            writeQueue.addLast(frame(QlabsProtocol.CMD_GET_RESULT_LIST))
                        }
                        return BleParseOutcome.Continue
                    }
                    looksLikeResult(cmd) -> {
                        phase = Phase.WAIT_RESULT
                        return handleResultCommand(cmd)
                    }
                }
                Phase.WAIT_RESULT -> when {
                    cmd.equals("fail", ignoreCase = true) ||
                        cmd.equals("lost", ignoreCase = true) -> {
                        if (pendingIndices.isNotEmpty()) {
                            val next = pendingIndices.removeAt(0)
                            enqueueResultRequest(next)
                            return BleParseOutcome.Continue
                        }
                        if (collectHistory) return finishHistoryOrFail()
                        if (!askedList && resultIndex == 0) {
                            askedList = true
                            phase = Phase.WAIT_LIST
                            writeQueue.addLast(frame(QlabsProtocol.CMD_GET_RESULT_LIST))
                            return BleParseOutcome.Continue
                        }
                        if (resultIndex < QlabsProtocol.MAX_RESULT_INDEX) {
                            enqueueResultRequest(resultIndex + 1)
                            return BleParseOutcome.Continue
                        }
                        return BleParseOutcome.Fail(
                            "qLabs: brak wyniku w pamięci (sprawdzono indeksy 0…$resultIndex)",
                        )
                    }
                    looksLikeResult(cmd) -> return handleResultCommand(cmd)
                    cmd.contains("result list", ignoreCase = true) ||
                        cmd.contains("resultlist", ignoreCase = true) ->
                        return handleResultList(cmd)
                    cmd.equals("success", ignoreCase = true) -> {
                        if (writeQueue.none { String(it, Charsets.US_ASCII).contains("get result") }) {
                            enqueueResultRequest(resultIndex)
                        }
                        return BleParseOutcome.Continue
                    }
                }
                Phase.DONE -> Unit
            }
        }
        return if (writeQueue.isNotEmpty()) BleParseOutcome.Continue else BleParseOutcome.NeedMore
    }

    private fun handleResultList(cmd: String): BleParseOutcome {
        val indices = QlabsProtocol.parseResultList(cmd)
        phase = Phase.WAIT_RESULT
        if (indices.isEmpty()) {
            if (collectHistory) return finishHistoryOrFail()
            enqueueResultRequest(0)
        } else {
            // Pełna lista z pamięci (nie tylko 10) — przycisk historii na popupie.
            val remaining = indices.filter { idx ->
                history.none { it.id == "inr-$idx" }
            }.take(MAX_HISTORY)
            pendingIndices = remaining.toMutableList()
            collectHistory = true
            if (pendingIndices.isEmpty()) return finishHistoryOrFail()
            val first = pendingIndices.removeAt(0)
            enqueueResultRequest(first)
        }
        return BleParseOutcome.Continue
    }

    private fun looksLikeResult(cmd: String): Boolean =
        (cmd.startsWith("result", ignoreCase = true) &&
            !cmd.contains("result list", ignoreCase = true) &&
            !cmd.contains("resultlist", ignoreCase = true)) ||
            cmd.contains("SHOWRES", ignoreCase = true) ||
            cmd.contains("INR:", ignoreCase = true) ||
            cmd.contains("INR>", ignoreCase = true)

    private fun enqueueResultRequest(index: Int) {
        resultIndex = index
        writeQueue.addLast(frame(QlabsProtocol.resultDataCommand(index)))
    }

    private fun handleResultCommand(cmd: String): BleParseOutcome {
        val inr = QlabsProtocol.parseInr(cmd).getOrElse {
            return advanceAfterBadResult(it.message ?: "qLabs parse")
        }
        if (inr <= 0.0) {
            return advanceAfterBadResult("qLabs: INR=0")
        }
        val dateNote = QlabsProtocol.extractDateNote(cmd)
        val at = QlabsProtocol.parseResultDateMs(cmd)
        val fresh = QlabsProtocol.isFreshResult(cmd, nowMillis())
        val option = VitalHistoryOption(
            id = "inr-$resultIndex",
            label = buildString {
                append("INR %.2f".format(inr))
                if (dateNote.isNotBlank()) append(" · ").append(dateNote)
                else append(" · pamięć #$resultIndex")
            },
            reading = VitalReading(
                kind = BleVitalKind.INR_QLABS,
                inrValue = inr,
                deviceName = if (variant == QlabsVariant.V3) "qLabs V3" else "qLabs V1",
                rawNote = listOfNotNull(
                    QlabsProtocol.extractPtNote(cmd).takeIf { it.isNotBlank() },
                    dateNote.takeIf { it.isNotBlank() },
                    if (resultIndex > 0) "pamięć #$resultIndex" else null,
                ).joinToString(" · "),
                measuredAtMs = at,
            ),
        )
        if (resultIndex == 0 && fresh) {
            freshReading = option.reading
        }
        if (!collectHistory && resultIndex == 0) {
            if (history.none { it.id == option.id }) history += option
            collectHistory = true
            if (!askedList) {
                askedList = true
                phase = Phase.WAIT_LIST
                writeQueue.addLast(frame(QlabsProtocol.CMD_GET_RESULT_LIST))
                return BleParseOutcome.Continue
            }
        }
        if (collectHistory) {
            if (history.none { it.id == option.id }) history += option
            if (pendingIndices.isNotEmpty()) {
                enqueueResultRequest(pendingIndices.removeAt(0))
                return BleParseOutcome.Continue
            }
            return finishHistoryOrFail()
        }
        return advanceAfterBadResult("nieświeży")
    }

    private fun advanceAfterBadResult(reason: String): BleParseOutcome {
        if (pendingIndices.isNotEmpty()) {
            enqueueResultRequest(pendingIndices.removeAt(0))
            return BleParseOutcome.Continue
        }
        if (collectHistory) return finishHistoryOrFail()
        if (!askedList && resultIndex == 0) {
            askedList = true
            phase = Phase.WAIT_LIST
            writeQueue.addLast(frame(QlabsProtocol.CMD_GET_RESULT_LIST))
            return BleParseOutcome.Continue
        }
        if (resultIndex < QlabsProtocol.MAX_RESULT_INDEX) {
            enqueueResultRequest(resultIndex + 1)
            return BleParseOutcome.Continue
        }
        return BleParseOutcome.Fail(reason)
    }

    private fun finishHistoryOrFail(): BleParseOutcome {
        phase = Phase.DONE
        val opts = history.distinctBy { it.reading.inrValue to it.reading.rawNote }
        val fresh = freshReading
        return when {
            opts.isEmpty() -> BleParseOutcome.Fail("qLabs: brak wyniku INR w pamięci")
            fresh != null && opts.size == 1 -> BleParseOutcome.Done(fresh)
            else -> BleParseOutcome.Choose(
                title = when {
                    fresh == null && opts.isNotEmpty() ->
                        "Historia INR w pamięci qLabs"
                    opts.size > 1 ->
                        "Historia INR w pamięci qLabs"
                    else ->
                        "Wybierz pomiar INR z pamięci qLabs"
                },
                options = opts,
                freshReading = fresh,
            )
        }
    }
}
