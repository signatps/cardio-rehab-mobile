package pl.cardioscp.rehab.ble

import android.content.Context

/** Preferowane BDA urządzeń BLE — jak DSD StationStore.bleBdas (SharedPreferences). */
class BleBdaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ble_bda", Context.MODE_PRIVATE)

    fun bleBdaFor(kind: BleVitalKind): String {
        val raw = prefs.getString(kind.name, "").orEmpty()
        val n = BleBda.normalize(raw)
        return if (BleBda.isValid(n)) n else ""
    }

    fun rememberBleBda(kind: BleVitalKind, address: String, deviceName: String = "") {
        val n = BleBda.normalize(address)
        if (!BleBda.isValid(n)) return
        prefs.edit()
            .putString(kind.name, n)
            .putString("${kind.name}_name", deviceName)
            .apply()
        // Alias AUTO → konkretny model
        when (kind) {
            BleVitalKind.BP_TD3140, BleVitalKind.BP_TD3128, BleVitalKind.BP_MICROLIFE ->
                prefs.edit().putString(BleVitalKind.BP_AUTO.name, n).apply()
            BleVitalKind.WEIGHT_CHARDER, BleVitalKind.WEIGHT_TD2555, BleVitalKind.WEIGHT_IXELLENCE ->
                prefs.edit().putString(BleVitalKind.WEIGHT_AUTO.name, n).apply()
            else -> Unit
        }
    }

    fun resolveConnectKind(kind: BleVitalKind, bda: String): BleVitalKind {
        val n = BleBda.normalize(bda)
        return when (kind) {
            BleVitalKind.WEIGHT_AUTO -> when {
                n == bleBdaFor(BleVitalKind.WEIGHT_IXELLENCE) -> BleVitalKind.WEIGHT_IXELLENCE
                n == bleBdaFor(BleVitalKind.WEIGHT_CHARDER) -> BleVitalKind.WEIGHT_CHARDER
                n == bleBdaFor(BleVitalKind.WEIGHT_TD2555) -> BleVitalKind.WEIGHT_TD2555
                else -> BleVitalKind.WEIGHT_AUTO
            }
            BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO -> when {
                n == bleBdaFor(BleVitalKind.BP_TD3140) -> BleVitalKind.BP_TD3140
                n == bleBdaFor(BleVitalKind.BP_TD3128) -> BleVitalKind.BP_TD3128
                n == bleBdaFor(BleVitalKind.BP_MICROLIFE) -> BleVitalKind.BP_MICROLIFE
                else -> kind
            }
            else -> kind
        }
    }
}
