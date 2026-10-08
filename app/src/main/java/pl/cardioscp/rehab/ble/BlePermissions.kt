package pl.cardioscp.rehab.ble

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import pl.cardioscp.rehab.compat.AndroidCompat

object BlePermissions {
    fun needed(sdk: Int = Build.VERSION.SDK_INT): Array<String> =
        AndroidCompat.runtimeBleSessionPermissions(sdk).toTypedArray()

    fun missing(context: android.content.Context): Array<String> =
        needed().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
}
