package pl.cardioscp.rehab.bluetooth

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import pl.cardioscp.rehab.bluetooth.protocol.ProtocolFrame

/**
 * Transport boundary for the Pro-PLUS ECG recorder (Silvermedia framing over SPP).
 *
 * Pairing happens in Android Bluetooth settings. The app connects to a bonded
 * device matching [BluetoothDeviceFilter] via RFCOMM + [SppConstants.SPP_UUID].
 */
interface EhoMiniDeviceClient {
    val connectionState: StateFlow<EhoMiniConnectionState>
    val incomingFrames: SharedFlow<ProtocolFrame>

    suspend fun listBondedCandidates(): List<BondedEcgDevice>
    suspend fun connect(address: String)
    suspend fun disconnect()
    suspend fun sendFrame(frame: ProtocolFrame)
    suspend fun sendRaw(payload: ByteArray)
}

data class BondedEcgDevice(
    val name: String,
    val address: String,
    val serialSuffix: String,
)
