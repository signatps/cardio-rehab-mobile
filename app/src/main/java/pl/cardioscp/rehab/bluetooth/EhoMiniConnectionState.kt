package pl.cardioscp.rehab.bluetooth

/**
 * High-level connection lifecycle for the Plus EHO-Mini ECG recorder.
 * Connect path: Android-bonded Classic SPP device + Silvermedia framing.
 */
sealed interface EhoMiniConnectionState {
    data object Idle : EhoMiniConnectionState
    data object PermissionsRequired : EhoMiniConnectionState
    data object Scanning : EhoMiniConnectionState
    data class Connected(val deviceName: String, val address: String) : EhoMiniConnectionState
    data class Error(val message: String) : EhoMiniConnectionState
}
