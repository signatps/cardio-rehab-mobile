package pl.cardioscp.rehab.ble

/**
 * TaiDoc ciśnienie bez rozróżnienia modelu: najpierw świeży wynik z pamięci (TD-3128),
 * jeśli brak — start pomiaru live (TD-3140) bez wcześniejszego turn-off.
 */
class BpTaiDocAutoSession {
    private enum class Phase { Memory, Live }

    private var phase = Phase.Memory
    private val memory = Td3128Session(turnOffWhenEmpty = false)
    private var live: Td3140Session? = null
    private var switchToLive = false

    fun nextWrite(): ByteArray? {
        if (switchToLive) {
            switchToLive = false
            phase = Phase.Live
            live = Td3140Session()
        }
        return when (phase) {
            Phase.Memory -> memory.nextWrite()
            Phase.Live -> live?.nextWrite()
        }
    }

    fun onNotify(data: ByteArray): BleParseOutcome {
        return when (phase) {
            Phase.Memory -> {
                when (val o = memory.onNotify(data)) {
                    is BleParseOutcome.Done -> BleParseOutcome.Done(
                        o.reading.copy(kind = BleVitalKind.BP_TAIDOC_AUTO),
                    )
                    is BleParseOutcome.Fail -> {
                        switchToLive = true
                        BleParseOutcome.Continue
                    }
                    else -> o
                }
            }
            Phase.Live -> {
                val session = live ?: return BleParseOutcome.Fail("Brak sesji TD-3140")
                when (val o = session.onNotify(data)) {
                    is BleParseOutcome.Done -> BleParseOutcome.Done(
                        o.reading.copy(kind = BleVitalKind.BP_TAIDOC_AUTO),
                    )
                    else -> o
                }
            }
        }
    }

    fun needsWriteAfterNotify(outcome: BleParseOutcome): Boolean {
        if (switchToLive) return true
        return when (phase) {
            Phase.Memory -> memory.needsWriteAfterNotify(outcome)
            Phase.Live -> live?.needsWriteAfterNotify(outcome) == true
        }
    }
}
