package pl.cardioscp.rehab.ble.host

import android.content.Context
import android.location.LocationManager
import android.os.Build
import pl.cardioscp.rehab.compat.AndroidCompat

object BleLocationGate {
    fun isSystemLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return runCatching {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(true)
    }

    fun bleScanNeedsSystemLocation(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        AndroidCompat.bleScanNeedsLocation(sdkInt)
}
