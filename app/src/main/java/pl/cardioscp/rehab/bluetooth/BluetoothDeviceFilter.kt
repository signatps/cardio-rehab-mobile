package pl.cardioscp.rehab.bluetooth

/**
 * Discovery filter for ECG recorders.
 *
 * For now any Bluetooth name is accepted — EHO-Mini advertising name is not locked yet.
 * Pass an optional [nameContains] later to narrow the scan without changing call sites.
 */
object BluetoothDeviceFilter {
    /** When null/blank, every discovered device name (including empty) matches. */
    @Volatile
    var nameContains: String? = null

    fun matches(deviceName: String?): Boolean {
        val needle = nameContains?.trim().orEmpty()
        if (needle.isEmpty()) return true
        return deviceName.orEmpty().contains(needle, ignoreCase = true)
    }
}
