package pl.cardioscp.rehab.bluetooth

/**
 * Transport boundary for the Plus EHO-Mini Bluetooth device.
 *
 * Implementation is intentionally a stub until the protocol / firmware dump
 * (services, characteristics, framing, commands) is provided.
 */
interface EhoMiniDeviceClient {
    suspend fun startScan()
    suspend fun stopScan()
    suspend fun connect(address: String)
    suspend fun disconnect()
    suspend fun sendCommand(payload: ByteArray)
}

class StubEhoMiniDeviceClient : EhoMiniDeviceClient {
    override suspend fun startScan() = Unit
    override suspend fun stopScan() = Unit
    override suspend fun connect(address: String) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun sendCommand(payload: ByteArray) = Unit
}
