package pl.cardioscp.rehab.bluetooth

import pl.cardioscp.rehab.bluetooth.protocol.ProtocolFrame

/**
 * Transport boundary for the Plus EHO-Mini Bluetooth device (Silvermedia framing).
 *
 * Socket / SPP wiring lands once the advertising name and RFCOMM UUID are confirmed.
 * Protocol codec + session logic live in [pl.cardioscp.rehab.bluetooth.protocol].
 */
interface EhoMiniDeviceClient {
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(address: String)
    suspend fun disconnect()
    suspend fun sendFrame(frame: ProtocolFrame)
    suspend fun sendRaw(payload: ByteArray)
}

class StubEhoMiniDeviceClient : EhoMiniDeviceClient {
    override suspend fun startScan() = Unit
    override suspend fun stopScan() = Unit
    override suspend fun connect(address: String) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun sendFrame(frame: ProtocolFrame) = Unit
    override suspend fun sendRaw(payload: ByteArray) = Unit
}
