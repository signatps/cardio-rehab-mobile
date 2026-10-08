package pl.cardioscp.rehab.ble

/**
 * Maszyna stanów TD-8255 — port z `td8255.py`.
 *
 * Nieprawidłowe odczyty nie kończą sesji (Continue).
 * Pierwszy prawidłowy wynik jest odrzucany; zapisuje się drugi
 * (stabilizacja po założeniu czujnika), w oknie do [Spo2Who.WAIT_TIMEOUT_MS].
 */
class Td8255Session {
    private var started = false
    /** Liczba dotychczasowych prawidłowych odczytów (1 = odrzucony, 2 = przyjęty). */
    var validCount: Int = 0
        private set

    /** Krótki komunikat UI po ostatnim notify (albo null). */
    var statusHint: String? = null
        private set

    fun nextWrite(): ByteArray = TaiDocProtocol.startSpo2()

    fun onNotify(data: ByteArray): BleParseOutcome {
        statusHint = null
        if (data.size < 8) return BleParseOutcome.NeedMore
        if ((data[0].toInt() and 0xFF) != TaiDocProtocol.HDR) return BleParseOutcome.NeedMore
        when (TaiDocProtocol.commandOf(data)) {
            TaiDocProtocol.CMD_SPO2_VALUE_HR -> {
                val sat = data[2].toInt() and 0xFF
                val pulse = data[4].toInt() and 0xFF
                if (!Spo2Who.isPlausible(sat, pulse)) {
                    statusHint = "SpO₂: odczyt nieprawidłowy ($sat% / $pulse) — czekam dalej…"
                    return BleParseOutcome.Continue
                }
                validCount++
                if (validCount == 1) {
                    statusHint = "SpO₂: pierwszy wynik odrzucony ($sat%) — czekam na kolejny…"
                    return BleParseOutcome.Continue
                }
                return BleParseOutcome.Done(
                    VitalReading(
                        kind = BleVitalKind.SPO2_TD8255,
                        spo2Percent = sat,
                        pulseBpm = pulse,
                        deviceName = "TD-8255",
                        rawNote = Spo2Who.label(Spo2Who.band(sat)),
                    ),
                )
            }
            TaiDocProtocol.CMD_START_STOP_SPO2 -> {
                started = true
                statusHint = "SpO₂: monitoring uruchomiony — załóż czujnik…"
                return BleParseOutcome.Continue
            }
        }
        return BleParseOutcome.Continue
    }
}
