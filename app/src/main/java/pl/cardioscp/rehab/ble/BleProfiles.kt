package pl.cardioscp.rehab.ble

/**
 * Profile BLE GATT przeniesione z `DPS_extDev_python` (signatps).
 * UUID zapisane w postaci kanonicznej (małe litery, z myślnikami).
 */
enum class BleVitalKind(
    val code: String,
    val label: String,
    val deviceHint: String,
) {
    BP_AUTO("BP_AUTO", "Ciśnienie", "ciśnieniomierz BLE"),
    BP_TAIDOC_AUTO("BP_TAIDOC_AUTO", "Ciśnienie TaiDoc", "TaiDoc BP"),
    BP_TD3140("BP_TD3140", "Ciśnienie TD-3140", "TD-3140"),
    BP_TD3128("BP_TD3128", "Ciśnienie TD-3128", "TD-3128"),
    BP_MICROLIFE("BP_MICROLIFE", "Ciśnienie Microlife B6", "Microlife / BP"),
    /** UI: jeden przycisk wagi — rozpoznanie Charder / TD-2555 / iXellence. */
    WEIGHT_AUTO("WEIGHT_AUTO", "Waga", "waga BLE"),
    WEIGHT_CHARDER("WEIGHT_CHARDER", "Waga Charder 6110BT", "Charder / MS"),
    WEIGHT_TD2555("WEIGHT_TD2555", "Waga TD-2555", "TD-2555"),
    /** Reklamy BLE (Manufacturer Data), bez GATT — jak w DPS. */
    WEIGHT_IXELLENCE("WEIGHT_IXELLENCE", "Waga iXellence", "iXellence"),
    SPO2_TD8255("SPO2_TD8255", "SpO₂ TD-8255", "TD-8255"),
    TEMP_TD1241("TEMP_TD1241", "Temperatura TD-1241", "TD-1241"),
    /** Pikflometr Vitalograph Lung Monitor BT Smart (seria 4000 BTLE) — DPS `lung4000.py`. */
    PEF_VITALOGRAPH("PEF_VITALOGRAPH", "PEF Vitalograph", "Lung Monitor BT Smart"),
    /**
     * INR Micropoint qLabs ElectroMeter (Q-3 / Q-1) — Interface Protocol V3.06,
     * serial-over-BLE (NUS / FFF0).
     */
    INR_QLABS("INR_QLABS", "INR qLabs", "qLabs Q3 / Q1"),
    /** Glukometr TaiDoc TD-4277 / Glucomaxx — DPS `td4277.py`. */
    GLU_TD4277("GLU_TD4277", "Glikemia", "TD-4277 / Glucomaxx"),
}

data class BleGattIds(
    val serviceUuid: String,
    val notifyUuid: String,
    val writeUuid: String? = null,
    val nameHints: List<String> = emptyList(),
)

object BleProfiles {
    val TAIDOC = BleGattIds(
        serviceUuid = "00001523-1212-efde-1523-785feabcd123",
        notifyUuid = "00001524-1212-efde-1523-785feabcd123",
        writeUuid = "00001524-1212-efde-1523-785feabcd123",
        nameHints = listOf(
            "TD-3140", "TD3140", "TD-3128", "TD3128",
            "TD-8255", "TD8255", "TD-8201", "TD8201",
            "TD-2555", "TD2555",
            "TD-1241", "TD1241",
            "TD-4277", "TD4277", "Glucomaxx", "GLUCOMAXX",
            "FORA", "W550",
        ),
    )

    val MICROLIFE = BleGattIds(
        serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
        notifyUuid = "0000fff1-0000-1000-8000-00805f9b34fb",
        writeUuid = "0000fff2-0000-1000-8000-00805f9b34fb",
        nameHints = listOf("Microlife", "Microlife BP B6", "BP B6", "B6 Connect"),
    )

    val CHARDER = BleGattIds(
        serviceUuid = "3a1bc6e0-fb06-11e1-b9c2-0002a5d5c51b",
        notifyUuid = "cc330a40-fb09-11e1-a84d-0002a5d5c51b",
        writeUuid = "cc330a40-fb09-11e1-a84d-0002a5d5c51b",
        nameHints = listOf("Charder", "MS6110", "MS-6110"),
    )

    val HEALTH_THERMOMETER = BleGattIds(
        serviceUuid = "00001809-0000-1000-8000-00805f9b34fb",
        notifyUuid = "00002a1c-0000-1000-8000-00805f9b34fb",
        writeUuid = null,
        nameHints = listOf("TD-1241", "TD1241", "TD-1035", "TD1035"),
    )

    val WEIGHT_SCALE = BleGattIds(
        serviceUuid = "0000181d-0000-1000-8000-00805f9b34fb",
        notifyUuid = "00002a9d-0000-1000-8000-00805f9b34fb",
        writeUuid = null,
        nameHints = listOf("TD-2555", "TD2555", "W550", "FORA"),
    )

    val WEIGHT_FFF0 = BleGattIds(
        serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
        notifyUuid = "0000fff1-0000-1000-8000-00805f9b34fb",
        writeUuid = "0000fff2-0000-1000-8000-00805f9b34fb",
        nameHints = listOf("TD-2555", "TD2555", "W550", "FORA"),
    )

    /** iXellence / Jumper JPD-BS200/BS201 — reklamy BLE. */
    val IXELLENCE = BleGattIds(
        serviceUuid = "",
        notifyUuid = "",
        writeUuid = null,
        nameHints = listOf(
            "iXellence", "Ixellence", "Xellence", "IXELLENCE",
            "JPD", "JPD-BS200", "JPD-BS201", "BS200", "BS201", "Jumper",
        ),
    )

    /**
     * Vitalograph Lung Monitor BT Smart / seria 4000 BTLE.
     * API 07424 §3/§10: proprietary BLE serial (nie Classic asma-1 SPP).
     * UUID-y jak w DPS `lung4000.py` (chip; DPS przez BlueGiga).
     * Na tablecie — Android GATT. Usługa to **Telit Terminal I/O** (`fefb`):
     * - `00000002` TX data Notify (GTD)
     * - `00000001` RX data Write (ACK/DI)
     * - `00000003` TX credits Write (host→device — bez tego aparat milczy!)
     * - `00000004` RX credits Indicate
     * BlueGiga na RPi obsługuje credits w stacku; Android musi je wysłać ręcznie.
     */
    val VITALOGRAPH = BleGattIds(
        serviceUuid = "0000fefb-0000-1000-8000-00805f9b34fb",
        notifyUuid = "00000002-0000-1000-8000-008025000000",
        writeUuid = "00000001-0000-1000-8000-008025000000",
        nameHints = listOf(
            "Vitalograph", "asma-1", "asma1",
            "lung monitor", "Lung Monitor", "BT Smart", "BTSmart", "BTLE",
            "LUNG4000", "LUNG_", "PEF", "4000",
        ),
    )
    /** Telit TIO: host grants credits so device may send GTD. */
    const val VITALOGRAPH_TIO_CREDITS_WRITE = "00000003-0000-1000-8000-008025000000"
    /** Telit TIO: device grants credits for host writes (ACK). */
    const val VITALOGRAPH_TIO_CREDITS_INDICATE = "00000004-0000-1000-8000-008025000000"

    /**
     * qLabs ElectroMeter V1 (Q-1/Q-2) — Communication Protocol:
     * broadcast service `FFF0`, host→meter write `FFF1`, meter→host notify `FFF4`.
     * V3/Q-3: NUS gdy dostępne; na Tab S4 Q3 często tylko proprietary `a002`
     * (fotka 0.9.95: c302 write + c305 notify — bez NUS/FFF0).
     */
    val QLABS_FFF0 = BleGattIds(
        serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
        notifyUuid = "0000fff4-0000-1000-8000-00805f9b34fb",
        writeUuid = "0000fff1-0000-1000-8000-00805f9b34fb",
        nameHints = listOf(
            "qLabs", "qlabs", "Q-3", "Q3", "Q-1", "Q1", "QV-3", "QV3",
            "Micropoint", "ElectroMeter", "Electro Meter",
        ),
    )

    /** V3/Q-3: NUS gdy stack go eksponuje; inaczej [QLABS_A002]. */
    val QLABS_NUS = BleGattIds(
        serviceUuid = "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
        notifyUuid = "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
        writeUuid = "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
        nameHints = QLABS_FFF0.nameHints,
    )

    /**
     * Q3 na Android GATT (Tab): usługa `0000a002` zamiast NUS.
     * Chars (fotka 0.9.95): c300/c301 R, c302 W, c303 w, c304 W, c305 N.
     * `c302` odrzuca hello 16 B (GATT 13, 0.9.96) — to nie UART.
     * Serial: write `c304` (DEFAULT), fallback `c303` WNR, notify `c305`.
     */
    val QLABS_A002 = BleGattIds(
        serviceUuid = "0000a002-0000-1000-8000-00805f9b34fb",
        notifyUuid = "0000c305-0000-1000-8000-00805f9b34fb",
        writeUuid = "0000c304-0000-1000-8000-00805f9b34fb",
        nameHints = QLABS_FFF0.nameHints,
    )

    /** Starsze mapowanie FFF1/FFF2 — zostawione jako dodatkowy fallback. */
    val QLABS_FFF0_LEGACY = BleGattIds(
        serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
        notifyUuid = "0000fff1-0000-1000-8000-00805f9b34fb",
        writeUuid = "0000fff2-0000-1000-8000-00805f9b34fb",
        nameHints = QLABS_FFF0.nameHints,
    )

    fun forKind(kind: BleVitalKind): BleGattIds = when (kind) {
        BleVitalKind.BP_TAIDOC_AUTO, BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128,
        BleVitalKind.SPO2_TD8255, BleVitalKind.WEIGHT_TD2555,
        BleVitalKind.WEIGHT_AUTO, BleVitalKind.GLU_TD4277 -> TAIDOC
        // BP_AUTO: najpierw Microlife (FFF0); TaiDoc w alternateProfiles.
        BleVitalKind.BP_AUTO -> MICROLIFE
        BleVitalKind.BP_MICROLIFE -> MICROLIFE
        BleVitalKind.WEIGHT_CHARDER -> CHARDER
        BleVitalKind.WEIGHT_IXELLENCE -> IXELLENCE
        BleVitalKind.TEMP_TD1241 -> HEALTH_THERMOMETER
        BleVitalKind.PEF_VITALOGRAPH -> VITALOGRAPH
        BleVitalKind.INR_QLABS -> QLABS_FFF0
    }

    fun alternateProfiles(kind: BleVitalKind): List<BleGattIds> = when (kind) {
        BleVitalKind.WEIGHT_TD2555 -> listOf(WEIGHT_SCALE, WEIGHT_FFF0, TAIDOC)
        BleVitalKind.WEIGHT_AUTO -> listOf(CHARDER, WEIGHT_SCALE, WEIGHT_FFF0, TAIDOC, IXELLENCE)
        BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO -> listOf(MICROLIFE, TAIDOC)
        BleVitalKind.INR_QLABS -> listOf(QLABS_NUS, QLABS_A002, QLABS_FFF0_LEGACY, WEIGHT_FFF0)
        else -> alternateForKind(kind)?.let { listOf(it) }.orEmpty()
    }

    fun alternateForKind(kind: BleVitalKind): BleGattIds? = when (kind) {
        BleVitalKind.WEIGHT_TD2555 -> WEIGHT_SCALE
        BleVitalKind.BP_AUTO -> MICROLIFE
        else -> null
    }

    fun matchesName(kind: BleVitalKind, advertisedName: String?): Boolean {
        if (advertisedName.isNullOrBlank()) return false
        val name = advertisedName
        return when (kind) {
            BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO ->
                BleDeviceIdentity.isBloodPressureMeterName(name)
            BleVitalKind.BP_TD3140 -> BleDeviceIdentity.isTd3140Name(name)
            BleVitalKind.BP_TD3128 -> BleDeviceIdentity.isTd3128Name(name)
            BleVitalKind.BP_MICROLIFE -> BleDeviceIdentity.isMicrolifeName(name)
            BleVitalKind.SPO2_TD8255 -> BleDeviceIdentity.isSpo2MeterName(name)
            BleVitalKind.TEMP_TD1241 -> BleDeviceIdentity.isTd1241Name(name)
            BleVitalKind.GLU_TD4277 -> BleDeviceIdentity.isGlucoseMeterName(name)
            BleVitalKind.WEIGHT_TD2555 ->
                BleDeviceIdentity.nameContainsWeightTd(name)
            BleVitalKind.WEIGHT_AUTO ->
                BleDeviceIdentity.isCharderName(name) ||
                    BleDeviceIdentity.isIxellenceName(name) ||
                    BleDeviceIdentity.nameContainsWeightTd(name)
            BleVitalKind.WEIGHT_CHARDER -> BleDeviceIdentity.isCharderName(name)
            BleVitalKind.WEIGHT_IXELLENCE -> BleDeviceIdentity.isIxellenceName(name)
            BleVitalKind.PEF_VITALOGRAPH -> BleDeviceIdentity.isVitalographName(name)
            BleVitalKind.INR_QLABS -> BleDeviceIdentity.isQlabsName(name)
        }
    }

    /**
     * Filtr skanu według profilu pomiaru — **tylko dozwolone modele w nazwie BT**.
     * Nie łączymy po samym BDA C026 / UUID (to mieszało TD-8255 z glikemią).
     */
    fun scanMatches(
        kind: BleVitalKind,
        advertisedName: String?,
        address: String? = null,
        serviceUuids: Collection<String> = emptyList(),
    ): Boolean {
        @Suppress("UNUSED_PARAMETER")
        val ignoredAddress = address
        if (matchesName(kind, advertisedName)) return true
        // Charder często reklamuje UUID usługi bez czytelnej nazwy BT.
        val uuids = serviceUuids.map { it.lowercase() }
        val charderUuid = CHARDER.serviceUuid.lowercase()
        val isCharderUuid = charderUuid.isNotBlank() && uuids.any { it.contains(charderUuid) }
        return when (kind) {
            BleVitalKind.WEIGHT_AUTO, BleVitalKind.WEIGHT_CHARDER -> isCharderUuid
            else -> false
        }
    }
}

data class VitalReading(
    val kind: BleVitalKind,
    val systolicMmHg: Int? = null,
    val diastolicMmHg: Int? = null,
    val pulseBpm: Int? = null,
    val weightKg: Double? = null,
    val spo2Percent: Int? = null,
    val temperatureC: Double? = null,
    /** PEF [L/min] — Vitalograph / LUNG4000. */
    val peakFlowLMin: Int? = null,
    /** INR — qLabs ElectroMeter (Q3/Q1). */
    val inrValue: Double? = null,
    /** Glukoza [mg/dL] — TD-4277 / Glucomaxx. */
    val glucoseMgDl: Int? = null,
    val deviceName: String = "",
    val rawNote: String = "",
    /** Epoch ms pomiaru z urządzenia (gdy znany). */
    val measuredAtMs: Long? = null,
    /** Pełna spirometria ASTD (Lung Monitor BTLE); banner nadal pokazuje PEF. */
    val spirometry: VitalographAstd? = null,
    /** Adres BLE (BDA), forma z dwukropkami lub 12 hex. */
    val deviceAddress: String = "",
    /** Numer seryjny z peryferium (gdy protokół/reklama go podaje). */
    val deviceSerial: String = "",
) {
    val summary: String
        get() = when (kind) {
            BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO,
            BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE ->
                if (systolicMmHg != null && diastolicMmHg != null) {
                    "$systolicMmHg/$diastolicMmHg" + (pulseBpm?.let { " · $it/min" } ?: "")
                } else {
                    "brak"
                }
            BleVitalKind.WEIGHT_AUTO, BleVitalKind.WEIGHT_CHARDER,
            BleVitalKind.WEIGHT_TD2555, BleVitalKind.WEIGHT_IXELLENCE ->
                weightKg?.let { "%.1f kg".format(it) } ?: "brak"
            BleVitalKind.SPO2_TD8255 ->
                spo2Percent?.let { "$it%" + (pulseBpm?.let { p -> " · $p/min" } ?: "") } ?: "brak"
            BleVitalKind.TEMP_TD1241 -> temperatureC?.let { "%.1f °C".format(it) } ?: "brak"
            BleVitalKind.PEF_VITALOGRAPH ->
                peakFlowLMin?.let { "$it L/min" } ?: "brak"
            BleVitalKind.INR_QLABS ->
                inrValue?.let { "INR %.2f".format(it) + if (rawNote.isNotBlank()) " · $rawNote" else "" }
                    ?: "brak"
            BleVitalKind.GLU_TD4277 ->
                glucoseMgDl?.let {
                    GlucoseWho.format(it) + if (rawNote.isNotBlank()) " · $rawNote" else ""
                } ?: "brak"
        }
}

/** Opcja z historii pamięci urządzenia (INR / glikemia). */
data class VitalHistoryOption(
    val id: String,
    val label: String,
    val reading: VitalReading,
)

sealed class BleParseOutcome {
    data object NeedMore : BleParseOutcome()
    data object Continue : BleParseOutcome()
    data class Done(val reading: VitalReading) : BleParseOutcome()
    data class Fail(val reason: String) : BleParseOutcome()
    /**
     * Historia z pamięci urządzenia (INR / glikemia).
     * [freshReading] — świeży wynik do pokazania od razu; UI może dodatkowo
     * pokazać przycisk „Pobierz historię”, gdy [options] ma więcej pozycji.
     */
    data class Choose(
        val title: String,
        val options: List<VitalHistoryOption>,
        val freshReading: VitalReading? = null,
    ) : BleParseOutcome()
}
