package pl.cardioscp.rehab.bluetooth

/**
 * High-level connection lifecycle for the Pro-PLUS ECG recorder.
 * Connect path: Android-bonded Classic SPP device + Silvermedia framing.
 */
sealed interface EhoMiniConnectionState {
    data object Idle : EhoMiniConnectionState
    data object PermissionsRequired : EhoMiniConnectionState
    data object LookingForBonded : EhoMiniConnectionState
    data class Connecting(val deviceName: String, val address: String) : EhoMiniConnectionState
    data class Connected(val deviceName: String, val address: String) : EhoMiniConnectionState
    data class Error(val message: String) : EhoMiniConnectionState
}
