package pl.cardioscp.rehab.bluetooth

/**
 * Discovery filter for Pro-PLUS ECG recorders.
 *
 * Advertising name format: `PRO_PLUS_ECG_` + 6-digit serial suffix
 * (last part of the device serial), e.g. `PRO_PLUS_ECG_740579`.
 */
object BluetoothDeviceFilter {
    const val NAME_PREFIX = "PRO_PLUS_ECG_"
    private val NAME_REGEX = Regex("^${Regex.escape(NAME_PREFIX)}\\d{6}$")

    fun matches(deviceName: String?): Boolean {
        val name = deviceName?.trim().orEmpty()
        if (name.isEmpty()) return false
        return NAME_REGEX.matches(name)
    }

    /** Returns the 6-digit serial suffix, or null if the name does not match. */
    fun serialSuffix(deviceName: String?): String? {
        val name = deviceName?.trim().orEmpty()
        if (!matches(name)) return null
        return name.removePrefix(NAME_PREFIX)
    }
}
