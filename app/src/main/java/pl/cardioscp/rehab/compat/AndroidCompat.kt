package pl.cardioscp.rehab.compat

/**
 * Uprawnienia i zachowanie hosta Androida 10–16 (API 29–36).
 * Nazwy permissionów jako stringi — `:core` jest JVM, bez android.jar.
 *
 * CardioSCP android10 trzymał targetSdk 29. DSD ma minSdk 29 i targetSdk 36,
 * więc te gałęzie są obowiązkowe, nie opcjonalne.
 */
object AndroidCompat {
    const val MIN_SDK = 29
    const val TARGET_SDK = 36

    const val BLUETOOTH = "android.permission.BLUETOOTH"
    const val BLUETOOTH_ADMIN = "android.permission.BLUETOOTH_ADMIN"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val FOREGROUND_SERVICE = "android.permission.FOREGROUND_SERVICE"
    const val FOREGROUND_SERVICE_CONNECTED_DEVICE =
        "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"

    fun runtimeBluetoothPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 31) {
            listOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
        } else {
            // Tab S4 / Android 10: Samsung bywa wymaga fine+coarse do skanu BLE.
            listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
        }

    fun runtimeGeotagPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 29) listOf(ACCESS_FINE_LOCATION) else emptyList()

    fun runtimeNotificationPermissions(sdkInt: Int): List<String> =
        if (sdkInt >= 33) listOf(POST_NOTIFICATIONS) else emptyList()

    /**
     * Skan + sesja GATT. Na 33+ dochodzi POST_NOTIFICATIONS, bo krótki FGS
     * connectedDevice (Android 14–16) pokazuje trwałe powiadomienie.
     */
    fun runtimeBleSessionPermissions(sdkInt: Int): List<String> =
        runtimeBluetoothPermissions(sdkInt) + runtimeNotificationPermissions(sdkInt)

    /** Android 12+ skan BLE może iść bez lokalizacji, gdy neverForLocation=true. */
    fun bleScanNeedsLocation(sdkInt: Int): Boolean = sdkInt < 31

    /** Connected-device FGS type jest wymagany od API 34 przy pomiarze w tle. */
    fun bleSessionNeedsConnectedDeviceFgs(sdkInt: Int): Boolean = sdkInt >= 34

    /**
     * CardioSCP na Tab S4 (target 29) nie używał FGS. Na API 29–30 z targetSdk 36
     * start FGS z typem z XML (`connectedDevice`) wywalał proces przy skanie.
     * KEEP_SCREEN_ON w UI wystarcza; FGS od API 31+.
     */
    fun bleSessionUsesForegroundService(sdkInt: Int): Boolean = sdkInt >= 31

    /**
     * Wartość dla `Service.startForeground(…, type)` — bez android.jar w `:core`.
     * - API 34+: CONNECTED_DEVICE = 0x10
     * - API 31–33: NONE = 0 (nie MANIFEST — unikamy connectedDevice z XML)
     * - API &lt; 31: nieużywane ([bleSessionUsesForegroundService] = false)
     */
    const val FGS_TYPE_NONE = 0
    const val FGS_TYPE_CONNECTED_DEVICE = 0x10

    fun bleForegroundServiceType(sdkInt: Int): Int = when {
        sdkInt >= 34 -> FGS_TYPE_CONNECTED_DEVICE
        sdkInt >= 31 -> FGS_TYPE_NONE
        else -> -1
    }

    fun registerReceiverExportedDefault(sdkInt: Int): Boolean = sdkInt >= 33

    fun vendorNotes(manufacturer: String, sdkInt: Int): List<String> {
        val oem = manufacturer.lowercase()
        val notes = mutableListOf<String>()
        notes += "API $sdkInt · min ${MIN_SDK} · target ${TARGET_SDK}"
        when {
            "samsung" in oem -> {
                notes +=
                    "Samsung / One UI: BLE bywa w parze systemowej; OTG (USB) to osobna zgoda — DSD MVP idzie Bluetooth, nie USB."
                if (sdkInt in 29..30) {
                    notes +=
                        "Tab S4 (SM-T835) / Android 10: przed skanem BLE włącz Lokalizację systemową i uprawnienie lokalizacji dla Pro-PLUS (jak CardioSCP)."
                }
            }
            "lenovo" in oem -> notes +=
                "Lenovo: oszczędzanie baterii uśpi GATT. Sesja pomiaru trzyma PARTIAL_WAKE_LOCK do 15 min — i tak mierz z włączonym ekranem."
            "xiaomi" in oem || "redmi" in oem || "poco" in oem -> notes +=
                "Xiaomi / HyperOS: Autostart + oszczędzanie baterii potrafią zabić GATT. FGS i wakelock nie zastępują wyjątku w ustawieniach OEM; ekran włączony."
        }
        if (sdkInt >= 31) {
            notes += "Android 12+: BLUETOOTH_SCAN/CONNECT zamiast lokalizacji przy skanie (neverForLocation)."
        } else {
            notes += "Android 10–11: skan BLE wymaga lokalizacji (nie geotagu pomiaru)."
        }
        if (sdkInt >= 33) notes += "Android 13+: POST_NOTIFICATIONS na telefonie."
        if (sdkInt >= 34) notes += "Android 14+: ewentualny FGS connectedDevice, bez wiecznej pętli w tle."
        if (sdkInt >= 35) notes += "Android 15–16: nie polegaj na nieograniczonym starcie usług z tła."
        return notes
    }
}
