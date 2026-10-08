package pl.cardioscp.rehab.ble

/**
 * Limity oczekiwania na pomiar BLE (zapisany BDA / sesja) oraz skanu.
 *
 * Glikemia: skan 120 s; ciśnienie: skan 180 s; pozostałe długie rodzaje: 60 s.
 * Puste okno skanu dla tych rodzajów wznawiane automatycznie.
 */
object BleVitalsWait {
    const val TIMEOUT_MS: Long = 120_000L
    const val TIMEOUT_SEC: Int = 120

    /** Temp / BP / PEF / glikemia / INR / waga — pomiar po wykryciu. */
    const val LONG_TIMEOUT_MS: Long = 180_000L
    const val LONG_TIMEOUT_SEC: Int = 180

    const val SCAN_DEFAULT_MS: Long = 30_000L
    /** Skan dla INR / wagi / temp / PEF. */
    const val SCAN_LONG_MS: Long = 60_000L
    /** Skan glukometru — pacjent często startuje aparat później. */
    const val SCAN_GLUCOSE_MS: Long = 120_000L
    /** Skan ciśnieniomierza — pomiar trwa dłużej, aparat bywa włączany z opóźnieniem. */
    const val SCAN_BP_MS: Long = 180_000L

    fun timeoutMs(kind: BleVitalKind?): Long = when (kind) {
        BleVitalKind.TEMP_TD1241,
        BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO,
        BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE,
        BleVitalKind.PEF_VITALOGRAPH,
        BleVitalKind.GLU_TD4277,
        BleVitalKind.WEIGHT_IXELLENCE, BleVitalKind.WEIGHT_AUTO,
        BleVitalKind.WEIGHT_TD2555, BleVitalKind.WEIGHT_CHARDER,
        BleVitalKind.INR_QLABS,
        -> LONG_TIMEOUT_MS
        else -> TIMEOUT_MS
    }

    fun timeoutSec(kind: BleVitalKind?): Int =
        (timeoutMs(kind) / 1000L).toInt()

    fun scanTimeoutMs(kind: BleVitalKind?): Long = when (kind) {
        BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO,
        BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE,
        -> SCAN_BP_MS
        BleVitalKind.GLU_TD4277 -> SCAN_GLUCOSE_MS
        BleVitalKind.TEMP_TD1241,
        BleVitalKind.PEF_VITALOGRAPH,
        BleVitalKind.WEIGHT_IXELLENCE, BleVitalKind.WEIGHT_AUTO,
        BleVitalKind.WEIGHT_TD2555, BleVitalKind.WEIGHT_CHARDER,
        BleVitalKind.INR_QLABS,
        -> SCAN_LONG_MS
        else -> SCAN_DEFAULT_MS
    }

    /** Czy po pustym oknie skanu wznawiać skan (INR/BP/glu/waga/temp/PEF). */
    fun shouldRestartEmptyScan(kind: BleVitalKind?): Boolean = when (kind) {
        BleVitalKind.TEMP_TD1241,
        BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO,
        BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE,
        BleVitalKind.PEF_VITALOGRAPH,
        BleVitalKind.GLU_TD4277,
        BleVitalKind.WEIGHT_IXELLENCE, BleVitalKind.WEIGHT_AUTO,
        BleVitalKind.WEIGHT_TD2555, BleVitalKind.WEIGHT_CHARDER,
        BleVitalKind.INR_QLABS,
        -> true
        else -> false
    }
}
