package pl.cardioscp.rehab.ble

/** Maszyna stanów TD-3140 — port z `td3140.py`. */
class Td3140Session {
    private enum class State { Start, WaitResult, TurnOff }

    private var state = State.Start
    private var finished = false
    private var ok = false
    private var sys: Int? = null
    private var dia: Int? = null
    private var pulse: Int? = null

    fun nextWrite(): ByteArray? = when (state) {
        State.Start -> TaiDocProtocol.startBpMeasure()
        State.TurnOff -> TaiDocProtocol.turnOff()
        State.WaitResult -> null
    }

    fun onNotify(data: ByteArray): BleParseOutcome {
        if (data.size < 8) return BleParseOutcome.NeedMore
        if ((data[0].toInt() and 0xFF) != TaiDocProtocol.HDR) return BleParseOutcome.NeedMore
        when (TaiDocProtocol.commandOf(data)) {
            TaiDocProtocol.CMD_START_BP -> {
                finished = true
                sys = data[3].toInt() and 0xFF
                dia = data[4].toInt() and 0xFF
                pulse = data[5].toInt() and 0xFF
                ok = (sys ?: 0) > 30 && (dia ?: 0) > 30 && (pulse ?: 0) > 30
                return BleParseOutcome.Continue
            }
            TaiDocProtocol.CMD_ENTERING_COMM -> {
                if (finished) {
                    state = State.TurnOff
                    return BleParseOutcome.Continue // caller should write turn-off
                }
            }
            TaiDocProtocol.CMD_START_STOP_SPO2 -> {
                if (state == State.Start) state = State.WaitResult
                return BleParseOutcome.Continue
            }
            TaiDocProtocol.CMD_TURN_OFF -> {
                return if (ok) {
                    BleParseOutcome.Done(
                        VitalReading(
                            kind = BleVitalKind.BP_TD3140,
                            systolicMmHg = sys,
                            diastolicMmHg = dia,
                            pulseBpm = pulse,
                            deviceName = "TD-3140",
                        ),
                    )
                } else {
                    BleParseOutcome.Fail("Nieważny pomiar ${sys}/${dia}/${pulse}")
                }
            }
        }
        return BleParseOutcome.Continue
    }

    fun needsWriteAfterNotify(outcome: BleParseOutcome): Boolean =
        outcome is BleParseOutcome.Continue && state == State.TurnOff
}
