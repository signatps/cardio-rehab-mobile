package pl.cardioscp.rehab.ble

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import pl.cardioscp.rehab.ble.host.BleDeviceHit
import pl.cardioscp.rehab.ble.host.BleLocationGate
import pl.cardioscp.rehab.ble.host.BleVitalsClient
import pl.cardioscp.rehab.compat.AndroidCompat

/**
 * Orkiestracja pomiaru BLE jak DsdViewModel (skan → connect → progress/timer → wynik).
 * Bez store/outbox — wynik zwracany callbackiem po „Zapisz”.
 */
class BleMeasureController(context: Context) {
    private val app = context.applicationContext
    private val ble = BleVitalsClient(app)
    private val bdaStore = BleBdaStore(app)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sdk = android.os.Build.VERSION.SDK_INT

    var measurePopupOpen by mutableStateOf(false)
        private set
    var measureType by mutableStateOf(VitalMeasureType.BLOOD_PRESSURE)
        private set
    /**
     * Monotoniczny licznik startu pomiaru — Compose musi odpalić skan także gdy
     * popup przechodzi ciśnienie→waga w tym samym cyklu (open zostaje true).
     */
    var measureEpoch by mutableIntStateOf(0)
        private set
    var pendingReading by mutableStateOf<VitalReading?>(null)
        private set
    var bleStatus by mutableStateOf("")
    var bleBusy by mutableStateOf(false)
    var bleProgress by mutableStateOf<Float?>(null)
    var bleWaitRemainingSec by mutableStateOf<Int?>(null)
    var bleWaitTotalSec by mutableIntStateOf(0)
        private set
    var bleWaitPhaseLabel by mutableStateOf("")
    var pendingBlePermission by mutableStateOf(false)
    var measureComment by mutableStateOf("")
    var preferredBleBda by mutableStateOf<String?>(null)
        private set
    var bleOfferOtherDevices by mutableStateOf(false)
        private set
    var bleShowOtherDevicePicker by mutableStateOf(false)
        private set
    val bleHits = mutableStateListOf<BleDeviceHit>()

    private var pendingBleAutoStart = false
    private var pendingPersistBda: Pair<String, BleVitalKind>? = null
    private var autoConnectArmed = false
    private var onSaved: ((VitalReading, String) -> Unit)? = null
    private var onCancelled: (() -> Unit)? = null

    private var bleWaitDeadlineMs: Long = 0L
    private var bleWaitPhase: BleWaitPhase = BleWaitPhase.IDLE
    private enum class BleWaitPhase { IDLE, SCAN, MEASURE }

    private val preferBdaOfferRunnable = Runnable {
        if (!measurePopupOpen || pendingReading != null) return@Runnable
        val preferred = preferredBleBda ?: return@Runnable
        if (bleHits.any { BleBda.sameAddress(it.address, preferred) }) return@Runnable
        val others = bleHits.filter { !BleBda.sameAddress(it.address, preferred) }
        if (others.isNotEmpty()) {
            bleOfferOtherDevices = true
            bleStatus = "Brak ostatniego urządzenia w skanie — możesz użyć innego"
        }
    }

    private val bleWaitTick = object : Runnable {
        override fun run() {
            if (!measurePopupOpen || pendingReading != null || bleWaitPhase == BleWaitPhase.IDLE) {
                bleWaitRemainingSec = null
                return
            }
            val progress = bleProgress
            if (bleWaitPhase == BleWaitPhase.MEASURE && progress != null) {
                bleWaitRemainingSec =
                    ((1f - progress.coerceIn(0f, 1f)) * bleWaitTotalSec).toInt().coerceAtLeast(0)
            } else if (bleWaitDeadlineMs > 0L) {
                bleWaitRemainingSec =
                    ((bleWaitDeadlineMs - SystemClock.uptimeMillis()) / 1000L)
                        .toInt()
                        .coerceAtLeast(0)
            }
            mainHandler.postDelayed(this, 400)
        }
    }

    fun startMeasure(
        type: VitalMeasureType,
        onSaved: (VitalReading, String) -> Unit,
        onCancelled: (() -> Unit)? = null,
    ) {
        ble.stop()
        measureType = type
        this.onSaved = onSaved
        this.onCancelled = onCancelled
        bleHits.clear()
        bleProgress = null
        bleBusy = false
        pendingReading = null
        pendingPersistBda = null
        measureComment = ""
        autoConnectArmed = false
        preferredBleBda = null
        bleOfferOtherDevices = false
        bleShowOtherDevicePicker = false
        mainHandler.removeCallbacks(preferBdaOfferRunnable)
        val kind = VitalKindMapping.kindFor(type)
        val bda = bdaStore.bleBdaFor(kind)
        bleStatus = if (BleBda.isValid(bda)) {
            "Szukam ostatniego ${kind.deviceHint}…"
        } else {
            "Szukam urządzenia w pobliżu…"
        }
        measurePopupOpen = true
        pendingBleAutoStart = true
        measureEpoch += 1
        armBleWaitCountdown(BleWaitPhase.SCAN, kind)
    }

    fun beginBleMeasure() {
        if (!pendingBleAutoStart) return
        pendingBleAutoStart = false
        val kind = VitalKindMapping.kindFor(measureType)
        val bda = bdaStore.bleBdaFor(kind)
        preferredBleBda = if (BleBda.isValid(bda)) BleBda.normalize(bda) else null
        bleOfferOtherDevices = false
        bleShowOtherDevicePicker = false
        requestOrStartBleScan()
    }

    fun useOtherBleDevice() {
        bleShowOtherDevicePicker = true
        bleOfferOtherDevices = false
        autoConnectArmed = false
        mainHandler.removeCallbacks(preferBdaOfferRunnable)
        bleStatus = "Wybierz urządzenie do odczytu"
    }

    fun selectableBleHits(): List<BleDeviceHit> {
        if (bleShowOtherDevicePicker) {
            val preferred = preferredBleBda
            return if (preferred != null) {
                bleHits.filter { !BleBda.sameAddress(it.address, preferred) }
            } else {
                bleHits.toList()
            }
        }
        if (preferredBleBda == null) return bleHits.toList()
        return emptyList()
    }

    fun onBlePermissionResult(granted: Boolean) {
        pendingBlePermission = false
        if (granted) {
            startBleScan()
        } else {
            bleStatus = "Bez zgody Bluetooth nie połączę urządzenia."
        }
    }

    fun requestOrStartBleScan() {
        pendingBlePermission = true
    }

    fun startBleScan() {
        val kind = VitalKindMapping.kindFor(measureType)
        bleHits.clear()
        bleBusy = true
        if (!ble.isBluetoothReady()) {
            bleBusy = false
            bleStatus = "Włącz Bluetooth."
            return
        }
        if (BleLocationGate.bleScanNeedsSystemLocation(sdk)) {
            val fine = ContextCompat.checkSelfPermission(
                app,
                AndroidCompat.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!fine) {
                bleBusy = false
                bleStatus = "Nadaj lokalizację aplikacji (wymagane do skanu BLE)."
                pendingBlePermission = true
                return
            }
            if (!BleLocationGate.isSystemLocationEnabled(app)) {
                bleBusy = false
                bleStatus = "Włącz Lokalizację w systemie, potem spróbuj ponownie."
                return
            }
        }
        autoConnectArmed = true
        bleOfferOtherDevices = false
        bleShowOtherDevicePicker = false
        mainHandler.removeCallbacks(preferBdaOfferRunnable)
        if (preferredBleBda != null) {
            mainHandler.postDelayed(preferBdaOfferRunnable, 6_000L)
        }
        runCatching {
            ble.startScan(
                kind = kind,
                onHit = { hit ->
                    val index = bleHits.indexOfFirst { it.address == hit.address }
                    if (index >= 0) bleHits[index] = hit else bleHits.add(hit)
                    bleBusy = false
                    if (pendingReading != null) return@startScan
                    val preferred = preferredBleBda
                    if (preferred != null) {
                        val preferredHit = bleHits.firstOrNull {
                            BleBda.sameAddress(it.address, preferred)
                        }
                        if (preferredHit != null && autoConnectArmed) {
                            autoConnectArmed = false
                            mainHandler.removeCallbacks(preferBdaOfferRunnable)
                            bleOfferOtherDevices = false
                            bleStatus = "Łączę z ostatnim urządzeniem…"
                            connectBle(preferredHit)
                            return@startScan
                        }
                        return@startScan
                    }
                    if (autoConnectArmed) {
                        when {
                            bleHits.size >= 2 -> {
                                autoConnectArmed = false
                                bleStatus = "Wybierz urządzenie do odczytu"
                            }
                            bleHits.size == 1 -> {
                                mainHandler.postDelayed({
                                    if (!autoConnectArmed || pendingReading != null) return@postDelayed
                                    if (bleHits.size >= 2) {
                                        autoConnectArmed = false
                                        bleStatus = "Wybierz urządzenie do odczytu"
                                    } else if (bleHits.size == 1) {
                                        autoConnectArmed = false
                                        connectBle(bleHits.first())
                                    }
                                }, 900)
                            }
                        }
                    }
                },
                onStatus = { msg ->
                    bleStatus = msg
                    bleBusy = false
                },
                onAdvertisementReading = { reading ->
                    bleBusy = false
                    bleProgress = null
                    clearBleWaitCountdown()
                    autoConnectArmed = false
                    mainHandler.removeCallbacks(preferBdaOfferRunnable)
                    presentPendingReading(reading, persistBdaFrom = null)
                },
            )
        }.onFailure { err ->
            bleBusy = false
            autoConnectArmed = false
            clearBleWaitCountdown()
            mainHandler.removeCallbacks(preferBdaOfferRunnable)
            bleStatus = "Skan: ${err.message}"
            Log.e("RehabBle", "startBleScan", err)
        }
    }

    fun connectBle(hit: BleDeviceHit) {
        val mapped = hit.resolvedKind ?: VitalKindMapping.kindFor(measureType)
        // Preferowane BDA wagi AUTO → konkretny model (JPD/iXellence bez GATT).
        val kind = bdaStore.resolveConnectKind(mapped, hit.address)
        connectBleAddress(
            address = hit.address,
            kind = kind,
            deviceName = hit.name,
            persistKind = kind,
        )
    }

    private fun connectBleAddress(
        address: String,
        kind: BleVitalKind,
        deviceName: String?,
        persistKind: BleVitalKind,
    ) {
        bleBusy = true
        bleProgress = null
        bleStatus = "Łączenie…"
        armBleWaitCountdown(BleWaitPhase.MEASURE, kind)
        ble.connect(
            address = address,
            kind = kind,
            deviceName = deviceName,
            onStatus = { bleStatus = it },
            onProgress = { p ->
                bleProgress = p
                if (p != null) {
                    bleBusy = true
                    if (bleWaitPhase != BleWaitPhase.MEASURE) {
                        armBleWaitCountdown(BleWaitPhase.MEASURE, kind)
                    }
                }
            },
            onDone = { result ->
                bleBusy = false
                bleProgress = null
                clearBleWaitCountdown()
                result.fold(
                    onSuccess = { presentPendingReading(it, persistBdaFrom = address to persistKind) },
                    onFailure = { bleStatus = it.message.orEmpty() },
                )
            },
            onChooseHistory = { _, options, fresh ->
                val pick = fresh ?: options.firstOrNull()?.reading
                if (pick != null) {
                    presentPendingReading(pick, persistBdaFrom = null)
                } else {
                    bleStatus = "Brak pomiarów w pamięci urządzenia."
                }
            },
        )
    }

    private fun presentPendingReading(
        reading: VitalReading,
        persistBdaFrom: Pair<String, BleVitalKind>?,
    ) {
        ble.stop()
        bleBusy = false
        bleProgress = null
        clearBleWaitCountdown()
        autoConnectArmed = false
        mainHandler.removeCallbacks(preferBdaOfferRunnable)
        bleOfferOtherDevices = false
        bleShowOtherDevicePicker = false
        if (!VitalKindMapping.hasValues(reading)) {
            bleStatus = "Pusta ramka."
            return
        }
        pendingPersistBda = persistBdaFrom
        pendingReading = reading
        bleStatus = "Wynik gotowy"
        val who = WhoPresentation.assess(measureType, reading)
        if (measureComment.isBlank()) measureComment = who.label
    }

    fun selectMeasureComment(comment: String) {
        measureComment = comment
    }

    /** Wynik testowy (jak DSD simulate) — pozwala iść dalej bez ciśnieniomierza / wagi. */
    fun simulateMeasure() {
        ble.stop()
        pendingBleAutoStart = false
        autoConnectArmed = false
        pendingBlePermission = false
        mainHandler.removeCallbacks(preferBdaOfferRunnable)
        bleHits.clear()
        bleOfferOtherDevices = false
        bleShowOtherDevicePicker = false
        val kind = VitalKindMapping.kindFor(measureType)
        ble.simulate(kind) { result ->
            result.fold(
                onSuccess = { reading ->
                    presentPendingReading(
                        reading.copy(measuredAtMs = System.currentTimeMillis()),
                        persistBdaFrom = null,
                    )
                    bleStatus = "Wynik symulowany"
                },
                onFailure = { bleStatus = it.message.orEmpty() },
            )
        }
    }

    fun saveMeasurePopup() {
        val reading = pendingReading ?: return
        if (!VitalKindMapping.hasValues(reading)) {
            bleStatus = "Brak wartości do zapisu."
            return
        }
        pendingPersistBda?.let { (address, kind) ->
            bdaStore.rememberBleBda(kind, address, reading.deviceName.ifBlank { kind.deviceHint })
        }
        val note = measureComment.trim()
        val cb = onSaved
        closeMeasurePopup()
        // Następna klatka: sesja może od razu otworzyć kolejny pomiar (waga po BP)
        // — Compose zobaczy zamknięcie popupu przed nowym startem.
        if (cb != null) {
            mainHandler.post { cb.invoke(reading, note) }
        }
    }

    fun retryMeasurePopup() {
        pendingReading = null
        pendingPersistBda = null
        measureComment = ""
        bleHits.clear()
        bleProgress = null
        bleBusy = false
        val kind = VitalKindMapping.kindFor(measureType)
        val hasBda = BleBda.isValid(bdaStore.bleBdaFor(kind))
        bleStatus = if (hasBda) "Ponawiam połączenie…" else "Szukam urządzenia…"
        pendingBleAutoStart = true
        preferredBleBda = if (hasBda) BleBda.normalize(bdaStore.bleBdaFor(kind)) else null
        bleOfferOtherDevices = false
        bleShowOtherDevicePicker = false
        mainHandler.removeCallbacks(preferBdaOfferRunnable)
        armBleWaitCountdown(BleWaitPhase.SCAN, kind)
        beginBleMeasure()
    }

    fun cancelMeasure() {
        ble.stop()
        val cb = onCancelled
        closeMeasurePopup()
        cb?.invoke()
    }

    fun release() {
        ble.stop()
        closeMeasurePopup()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun closeMeasurePopup() {
        measurePopupOpen = false
        pendingBleAutoStart = false
        pendingReading = null
        pendingPersistBda = null
        measureComment = ""
        bleBusy = false
        bleProgress = null
        clearBleWaitCountdown()
        bleHits.clear()
        autoConnectArmed = false
        preferredBleBda = null
        bleOfferOtherDevices = false
        bleShowOtherDevicePicker = false
        mainHandler.removeCallbacks(preferBdaOfferRunnable)
        bleStatus = ""
        onSaved = null
        onCancelled = null
    }

    private fun armBleWaitCountdown(phase: BleWaitPhase, kind: BleVitalKind) {
        bleWaitPhase = phase
        val ms = when (phase) {
            BleWaitPhase.SCAN -> BleVitalsWait.scanTimeoutMs(kind)
            BleWaitPhase.MEASURE -> BleVitalsWait.timeoutMs(kind)
            BleWaitPhase.IDLE -> 0L
        }
        bleWaitTotalSec = (ms / 1000L).toInt().coerceAtLeast(1)
        bleWaitDeadlineMs = SystemClock.uptimeMillis() + ms
        bleWaitRemainingSec = bleWaitTotalSec
        bleWaitPhaseLabel = when (phase) {
            BleWaitPhase.SCAN -> "Skan Bluetooth"
            BleWaitPhase.MEASURE -> "Oczekiwanie na pomiar"
            BleWaitPhase.IDLE -> ""
        }
        mainHandler.removeCallbacks(bleWaitTick)
        mainHandler.post(bleWaitTick)
    }

    private fun clearBleWaitCountdown() {
        bleWaitPhase = BleWaitPhase.IDLE
        bleWaitDeadlineMs = 0L
        bleWaitTotalSec = 0
        bleWaitRemainingSec = null
        bleWaitPhaseLabel = ""
        mainHandler.removeCallbacks(bleWaitTick)
    }
}
