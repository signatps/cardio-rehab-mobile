package pl.cardioscp.rehab.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.cardioscp.rehab.bluetooth.protocol.FrameCodec
import pl.cardioscp.rehab.bluetooth.protocol.FrameStreamParser
import pl.cardioscp.rehab.bluetooth.protocol.ProtocolFrame
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SppEhoMiniDeviceClient(
    context: Context,
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : EhoMiniDeviceClient {
    private val appContext = context.applicationContext

    private val _connectionState =
        MutableStateFlow<EhoMiniConnectionState>(EhoMiniConnectionState.Idle)
    override val connectionState: StateFlow<EhoMiniConnectionState> = _connectionState.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<ProtocolFrame>(extraBufferCapacity = 64)
    override val incomingFrames: SharedFlow<ProtocolFrame> = _incomingFrames.asSharedFlow()

    private val ioMutex = Mutex()
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var readerJob: Job? = null
    private val parser = FrameStreamParser()
    @Volatile private var intentionalDisconnect: Boolean = false

    private fun adapter(): BluetoothAdapter? {
        val manager = appContext.getSystemService(BluetoothManager::class.java)
        return manager?.adapter
    }

    @SuppressLint("MissingPermission")
    override suspend fun listBondedCandidates(): List<BondedEcgDevice> = withContext(Dispatchers.IO) {
        val adapter = adapter() ?: return@withContext emptyList()
        if (!adapter.isEnabled) return@withContext emptyList()
        adapter.bondedDevices.orEmpty()
            .mapNotNull { device ->
                val name = device.name ?: return@mapNotNull null
                val serial = BluetoothDeviceFilter.serialSuffix(name) ?: return@mapNotNull null
                BondedEcgDevice(name = name, address = device.address, serialSuffix = serial)
            }
            .sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): Unit = withContext(Dispatchers.IO) {
        intentionalDisconnect = false
        disconnectInternal()
        val adapter = adapter()
            ?: throw IOException("Bluetooth niedostępny na tym urządzeniu")
        if (!adapter.isEnabled) {
            throw IOException("Włącz Bluetooth")
        }
        val device = adapter.getRemoteDevice(address)
        val name = device.name ?: address
        _connectionState.value = EhoMiniConnectionState.Connecting(name, address)

        val connectedSocket = openSocket(device)
        try {
            connectedSocket.connect()
        } catch (connectError: IOException) {
            runCatching { connectedSocket.close() }
            // Fallback: insecure SPP (common with medical SPP dongles).
            val insecure = device.createInsecureRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
            try {
                insecure.connect()
                wireSocket(insecure, name, address)
            } catch (insecureError: IOException) {
                runCatching { insecure.close() }
                _connectionState.value = EhoMiniConnectionState.Idle
                throw IOException(
                    "Nie udało się otworzyć SPP do $name (${connectError.message})",
                    insecureError,
                )
            }
            return@withContext
        }
        wireSocket(connectedSocket, name, address)
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(device: BluetoothDevice): BluetoothSocket {
        val adapter = adapter()
        adapter?.cancelDiscovery()
        return device.createRfcommSocketToServiceRecord(SppConstants.SPP_UUID)
    }

    private fun wireSocket(connected: BluetoothSocket, name: String, address: String) {
        socket = connected
        input = connected.inputStream
        output = connected.outputStream
        parser.reset()
        _connectionState.value = EhoMiniConnectionState.Connected(name, address)
        readerJob = appScope.launch { readLoop() }
        Log.i(TAG, "SPP connected to $name ($address)")
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        intentionalDisconnect = true
        disconnectInternal()
        _connectionState.value = EhoMiniConnectionState.Idle
    }

    override suspend fun sendFrame(frame: ProtocolFrame) {
        sendRaw(FrameCodec.encode(frame))
    }

    override suspend fun sendRaw(payload: ByteArray) = ioMutex.withLock {
        withContext(Dispatchers.IO) {
            val out = output ?: throw IOException("Brak aktywnego połączenia SPP")
            out.write(payload)
            out.flush()
        }
    }

    private suspend fun readLoop() {
        val buffer = ByteArray(1024)
        val stream = input ?: return
        try {
            while (currentCoroutineContext().isActive) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                val chunk = buffer.copyOf(read)
                val frames = parser.push(chunk)
                for (frame in frames) {
                    _incomingFrames.emit(frame)
                }
            }
        } catch (_: IOException) {
            // Socket closed or link dropped.
        } finally {
            val wasConnected = _connectionState.value is EhoMiniConnectionState.Connected
            withContext(Dispatchers.IO) { disconnectInternal() }
            if (!intentionalDisconnect && wasConnected) {
                _connectionState.value = EhoMiniConnectionState.Error("Połączenie SPP przerwane")
            }
        }
    }

    private suspend fun disconnectInternal() {
        readerJob?.cancelAndJoin()
        readerJob = null
        ioMutex.withLock {
            runCatching { input?.close() }
            runCatching { output?.close() }
            runCatching { socket?.close() }
            input = null
            output = null
            socket = null
            parser.reset()
        }
    }

    companion object {
        private const val TAG = "SppEhoMini"
    }
}
