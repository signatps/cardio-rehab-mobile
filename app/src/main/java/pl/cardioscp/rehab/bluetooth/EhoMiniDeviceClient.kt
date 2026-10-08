package pl.cardioscp.rehab.bluetooth

import pl.cardioscp.rehab.bluetooth.protocol.ProtocolFrame

/**
 * Transport boundary for the Pro-PLUS ECG recorder (Silvermedia framing over SPP).
 *
 * Pairing happens in Android Bluetooth settings. The app connects to a bonded
 * device matching [BluetoothDeviceFilter] via RFCOMM + [SppConstants.SPP_UUID].
 * Protocol codec + session logic: [pl.cardioscp.rehab.bluetooth.protocol].
 */
interface EhoMiniDeviceClient {
    /** List bonded devices matching [BluetoothDeviceFilter] (no active inquiry required). */
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

class StubEhoMiniDeviceClient : EhoMiniDeviceClient {
    override suspend fun listBondedCandidates(): List<BondedEcgDevice> = emptyList()
    override suspend fun connect(address: String) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun sendFrame(frame: ProtocolFrame) = Unit
    override suspend fun sendRaw(payload: ByteArray) = Unit
}
