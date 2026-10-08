package pl.cardioscp.rehab.ble

/**
 * Rozpoznawanie ciśnieniomierza / wagi po nazwie i BDA (nie po „gołych” UUID).
 *
 * TaiDoc: BDA zawsze zaczyna się od `C026`; nazwa zawiera `TAIDOC` albo `TD`
 * (np. TD-3140, TD2555).
 */
object BleDeviceIdentity {
    /** Prefiks OUI TaiDoc w BDA (12 hex bez dwukropków). */
    const val TAIDOC_BDA_PREFIX = "C026"

    /** Nazwa: słowo TAIDOC albo TD (samodzielne / model TD-… / TD…). */
    private val taidocNameRegex = Regex("""(?i)(^|[^A-Za-z0-9])TD([- ]?\d|\b)|taidoc""")

    fun isTaiDocAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        return BleBda.normalize(address).startsWith(TAIDOC_BDA_PREFIX)
    }

    fun isTaiDocName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return taidocNameRegex.containsMatchIn(name.trim())
    }

    /** TaiDoc po nazwie albo po BDA `C026…`. */
    fun isTaiDoc(name: String?, address: String?): Boolean =
        isTaiDocName(name) || isTaiDocAddress(address)

    /**
     * Ciśnienie Microlife BP B6 — szukaj w nazwie BT **B6 Connect**
     * (reklama często bez słowa „Microlife”: `B6 Connect`, `BP B6 Connect`).
     */
    fun isMicrolifeName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.trim()
        if (n.contains("microlife", ignoreCase = true)) return true
        // Pilot: dokładny wzorzec z urządzenia — „B6 Connect” / „B6Connect”.
        if (Regex("""(?i)b6\s*connect""").containsMatchIn(n)) return true
        if (Regex("""(?i)bp\s*b6""").containsMatchIn(n)) return true
        return false
    }

    /** Model w nazwie BT: TD-3140 / TD3140 / 3140 przy TAIDOC. */
    fun isTd3140Name(name: String?): Boolean = nameContainsModel(name, "3140")

    fun isTd3128Name(name: String?): Boolean = nameContainsModel(name, "3128")

    fun isTd1241Name(name: String?): Boolean = nameContainsModel(name, "1241")

    fun isTd8255Name(name: String?): Boolean = nameContainsModel(name, "8255")

    fun isTd8201Name(name: String?): Boolean = nameContainsModel(name, "8201")

    /** SpO₂: TD-8255 albo TD-8201. */
    fun isSpo2MeterName(name: String?): Boolean = isTd8255Name(name) || isTd8201Name(name)

    /** Ciśnienie: TD-3140, TD-3128 albo Microlife BP B6. */
    fun isBloodPressureMeterName(name: String?): Boolean =
        isTd3140Name(name) || isTd3128Name(name) || isMicrolifeName(name)

    /**
     * Glikemia: tylko TD-4277 / Glucomaxx — jedna z tych nazw musi być w BT.
     * Nie wystarczy samo „TAIDOC” (to łapie też TD-8255).
     */
    fun isGlucoseMeterName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name
        return n.contains("glucomaxx", ignoreCase = true) ||
            nameContainsModel(n, "4277")
    }

    /** Waga TaiDoc TD-2555 / FORA W550 — nie mylić z innymi TD. */
    fun nameContainsWeightTd(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name
        return nameContainsModel(n, "2555") ||
            n.contains("w550", ignoreCase = true) ||
            n.contains("fora", ignoreCase = true)
    }

    private fun nameContainsModel(name: String?, modelDigits: String): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.trim()
        // TD-3140, TD3140, TAIDOC TD8255
        if (Regex("""(?i)(^|[^0-9])td[- ]?$modelDigits([^0-9]|$)""").containsMatchIn(n)) return true
        // „TAIDOC 8255” bez przedrostka TD
        return n.contains("taidoc", ignoreCase = true) &&
            Regex("""(?i)(^|[^0-9])$modelDigits([^0-9]|$)""").containsMatchIn(n)
    }

    fun isCharderName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name
        return n.contains("charder", ignoreCase = true) ||
            n.contains("ms6110", ignoreCase = true) ||
            n.contains("ms-6110", ignoreCase = true)
    }

    fun isIxellenceName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name
        return n.contains("ixellence", ignoreCase = true) ||
            n.contains("xellence", ignoreCase = true) ||
            n.contains("jumper", ignoreCase = true) ||
            n.contains("JPD", ignoreCase = true) ||
            n.contains("BS200", ignoreCase = true) ||
            n.contains("BS201", ignoreCase = true)
    }

    fun isVitalographName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name
        // API 07424: LUNG_XXXX / ASMA_XXXX (ostatnie 4 cyfry SN).
        return n.contains("vitalograph", ignoreCase = true) ||
            n.contains("asma-1", ignoreCase = true) ||
            n.contains("asma1", ignoreCase = true) ||
            n.startsWith("ASMA_", ignoreCase = true) ||
            n.contains("lung", ignoreCase = true) ||
            n.startsWith("LUNG_", ignoreCase = true) ||
            n.contains("LUNG4000", ignoreCase = true) ||
            n.contains("BT Smart", ignoreCase = true) ||
            n.contains("BTSmart", ignoreCase = true) ||
            n.contains("BTLE", ignoreCase = true) ||
            n.contains("PEF", ignoreCase = true) ||
            Regex("""(?i)\b4000\b""").containsMatchIn(n)
    }

    fun isQlabsName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name
        return n.contains("qlabs", ignoreCase = true) ||
            n.contains("micropoint", ignoreCase = true) ||
            n.contains("electrometer", ignoreCase = true) ||
            Regex("""(?i)(^|[^A-Za-z0-9])Q-?[13]\b""").containsMatchIn(n) ||
            Regex("""(?i)QV-?3""").containsMatchIn(n)
    }

    /**
     * Protokół qLabs wyłącznie z nazwy BT:
     * — zawiera „V3” (lub Q-3 / QV-3 / Q3) → [QlabsVariant.V3],
     * — w przeciwnym razie → [QlabsVariant.V1] (Q-1 / Q-2).
     */
    fun qlabsVariant(name: String?): QlabsVariant {
        val n = name.orEmpty()
        if (n.contains("V3", ignoreCase = true)) return QlabsVariant.V3
        if (n.contains("QV-3", ignoreCase = true) || n.contains("QV3", ignoreCase = true)) {
            return QlabsVariant.V3
        }
        if (n.contains("Q-3", ignoreCase = true)) return QlabsVariant.V3
        if (Regex("""(?i)(^|[^A-Za-z0-9])Q3([^A-Za-z0-9]|$)""").containsMatchIn(n)) {
            return QlabsVariant.V3
        }
        return QlabsVariant.V1
    }

    fun isGlucomaxxName(name: String?): Boolean = isGlucoseMeterName(name)

    fun resolveBp(
        name: String?,
        serviceUuids: Collection<String> = emptyList(),
        address: String? = null,
    ): BleVitalKind? {
        @Suppress("UNUSED_PARAMETER")
        val ignoredAddress = address
        @Suppress("UNUSED_PARAMETER")
        val ignoredUuids = serviceUuids
        val n = name.orEmpty()
        if (isMicrolifeName(n)) return BleVitalKind.BP_MICROLIFE
        if (isTd3128Name(n)) return BleVitalKind.BP_TD3128
        if (isTd3140Name(n)) return BleVitalKind.BP_TD3140
        // Bez zgadywania po samym BDA C026 — inne TaiDoc (8255, 4277…) nie są ciśnieniem.
        return null
    }

    fun resolveWeight(
        name: String?,
        serviceUuids: Collection<String> = emptyList(),
        address: String? = null,
    ): BleVitalKind? {
        val n = name.orEmpty()
        if (isIxellenceName(n)) return BleVitalKind.WEIGHT_IXELLENCE
        if (isCharderName(n)) return BleVitalKind.WEIGHT_CHARDER
        // TaiDoc OEM: nazwa TD/TAIDOC, BDA C026, albo FORA/W550/2555
        if (isTaiDoc(n, address) ||
            n.contains("2555") ||
            n.contains("w550", ignoreCase = true) ||
            n.contains("fora", ignoreCase = true)
        ) {
            return BleVitalKind.WEIGHT_TD2555
        }
        return null
    }

    fun isBpKind(kind: BleVitalKind): Boolean = when (kind) {
        BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO,
        BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE -> true
        else -> false
    }

    fun isWeightKind(kind: BleVitalKind): Boolean = when (kind) {
        BleVitalKind.WEIGHT_AUTO, BleVitalKind.WEIGHT_TD2555,
        BleVitalKind.WEIGHT_CHARDER, BleVitalKind.WEIGHT_IXELLENCE -> true
        else -> false
    }
}
