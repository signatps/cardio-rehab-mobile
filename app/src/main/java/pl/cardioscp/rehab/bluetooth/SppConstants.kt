package pl.cardioscp.rehab.bluetooth

import java.util.UUID

/**
 * Bluetooth Classic Serial Port Profile.
 *
 * Devices are paired in Android system Bluetooth settings. The app then opens an
 * RFCOMM socket to a bonded device whose name matches [BluetoothDeviceFilter],
 * using the well-known SPP UUID (not a vendor-specific UUID).
 */
object SppConstants {
    /** Bluetooth SIG well-known Serial Port Profile UUID. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
}
