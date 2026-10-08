package pl.cardioscp.rehab.ble

/**
 * BDA zapisujemy ciągiem, dużymi literami, bez dwukropków: `AABBCCDDEEFF`.
 * Android GATT wymaga formy z dwukropkami — [toColonForm].
 */
object BleBda {
    private val hexOnly = Regex("^[0-9A-F]{12}$")

    /** Normalizacja wpisu użytkownika → 12 hex uppercase albo pusty string. */
    fun normalize(raw: String): String {
        val cleaned = raw.uppercase()
            .replace(":", "")
            .replace("-", "")
            .replace(" ", "")
            .filter { it in '0'..'9' || it in 'A'..'F' }
            .take(12)
        return cleaned
    }

    fun isValid(normalized: String): Boolean = hexOnly.matches(normalized)

    /** Porównanie adresów niezależnie od dwukropków / wielkości liter. */
    fun sameAddress(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val na = normalize(a)
        val nb = normalize(b)
        return na.length == 12 && na == nb
    }

    /** `AABBCCDDEEFF` → `AA:BB:CC:DD:EE:FF` */
    fun toColonForm(normalized: String): String {
        require(isValid(normalized)) { "BDA musi mieć 12 hex: $normalized" }
        return normalized.chunked(2).joinToString(":")
    }

    fun bdaFor(kind: BleVitalKind, settingsBdas: Map<BleVitalKind, String>): String =
        settingsBdas[kind].orEmpty()
}
