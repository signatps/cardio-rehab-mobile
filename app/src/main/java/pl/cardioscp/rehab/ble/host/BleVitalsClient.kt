package pl.cardioscp.rehab.ble.host

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import pl.cardioscp.rehab.ble.BleDeviceIdentity
import pl.cardioscp.rehab.ble.BleParseOutcome
import pl.cardioscp.rehab.ble.BleProfiles
import pl.cardioscp.rehab.ble.BleVitalKind
import pl.cardioscp.rehab.ble.BleVitalsWait
import pl.cardioscp.rehab.ble.BpTaiDocAutoSession
import pl.cardioscp.rehab.ble.CharderSession
import pl.cardioscp.rehab.ble.HealthThermometerParser
import pl.cardioscp.rehab.ble.IxellenceSession
import pl.cardioscp.rehab.ble.MicrolifeSession
import pl.cardioscp.rehab.ble.QlabsProtocol
import pl.cardioscp.rehab.ble.QlabsSession
import pl.cardioscp.rehab.ble.QlabsVariant
import pl.cardioscp.rehab.ble.Spo2Who
import pl.cardioscp.rehab.ble.Td2555Session
import pl.cardioscp.rehab.ble.Td3128Session
import pl.cardioscp.rehab.ble.Td3140Session
import pl.cardioscp.rehab.ble.Td4277Session
import pl.cardioscp.rehab.ble.Td8255Session
import pl.cardioscp.rehab.ble.TaiDocProtocol
import pl.cardioscp.rehab.ble.VitalReading
import pl.cardioscp.rehab.ble.VitalHistoryOption
import pl.cardioscp.rehab.ble.VitalographAstd
import pl.cardioscp.rehab.ble.VitalographProtocol
import pl.cardioscp.rehab.ble.VitalographSession
import pl.cardioscp.rehab.ble.WeightParser
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class BleDeviceHit(
    val address: String,
    val name: String,
    val rssi: Int,
    val resolvedKind: BleVitalKind? = null,
)

/**
 * Sesja BLE GATT dla jednego rodzaju pomiaru.
 * Logika ramek w `:core`; tu host Android: skan + `BluetoothGatt` / `TRANSPORT_LE`.
 *
 * Prosty host jak DPS/BlueGiga (skan→connect→CCCD→write/listen) — bez pre-skanu
 * po BDA, GATT refresh ani równoległego hello na NUS (eksperymenty 0.9.48–0.9.53
 * psuły cały BT na tablecie).
 */
class BleVitalsClient(context: Context) {
    // Host GATT = kopia CardioSCP-mobile-android bc92276.
    // Różnice DSD (tylko wiring): callbacki UI na main (Compose), bezpieczna nazwa skanu (API 31+),
    // runCatching na start/stopScan (Android 10 lokalizacja). Bez własnego settle/FGS w connect.
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var kind: BleVitalKind? = null
    private var sessionKind: BleVitalKind? = null
    private var deviceNameHint: String? = null
    private var weightListenOnly = false
    private var td3140: Td3140Session? = null
    private var td3128: Td3128Session? = null
    private var bpAuto: BpTaiDocAutoSession? = null
    private var td2555: Td2555Session? = null
    private var td8255: Td8255Session? = null
    private var microlife: MicrolifeSession? = null
    private var charder: CharderSession? = null
    private var ixellence: IxellenceSession? = null
    private var ixellenceWatchAddress: String? = null
    private var vitalograph: VitalographSession? = null
    private var qlabs: QlabsSession? = null
    private var qlabsVariant: QlabsVariant = QlabsVariant.V1
    private var td4277: Td4277Session? = null
    private var active = AtomicBoolean(false)
    private var onStatus: (String) -> Unit = {}
    private var onDone: (Result<VitalReading>) -> Unit = {}
    private var onChooseHistory: ((title: String, options: List<VitalHistoryOption>, fresh: VitalReading?) -> Unit)? = null
    private var onScanHit: (BleDeviceHit) -> Unit = {}
    private var onAdvertisementReading: ((VitalReading) -> Unit)? = null
    private var onProgress: ((Float?) -> Unit)? = null
    private val hits = LinkedHashMap<String, BleDeviceHit>()
    private val cccdQueue = ArrayDeque<BluetoothGattDescriptor>()
    /** True od writeDescriptor do onDescriptorWrite — kolejka może być pusta wcześniej. */
    private var cccdInFlight = false
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    /** Numer bieżącego write — unieważnia stary timer odblokowania kolejki. */
    private var writeSeq: Int = 0
    /** Ponowienia 0x2B/0x25 przy ciszy (jak DPS timeout loop). */
    private var gluSilenceRetries: Int = 0
    /** MTU gotowe (callback lub timeout). qLabs: true dopiero po requestMtu przy ramce >20 B. */
    private var mtuReady = false
    private var pendingKickAfterMtu = false
    private var mtuRequested = false
    private val mtuTimeoutRunnable = Runnable {
        if (!active.get() || mtuReady) return@Runnable
        mtuReady = true
        val g = gatt
        if (pendingKickAfterMtu) {
            onStatus("qLabs: MTU domyślne — start protokołu…")
            maybeKickPending()
        } else if (g != null) {
            onStatus("qLabs: MTU domyślne — kontynuuję zapis…")
            flushWriteQueue(g)
        }
    }
    /** Kolejne lokalne odrzucenia writeCharacteristic (false) — GATT busy. */
    private var writeRejectStreak: Int = 0
    private val subscribed = HashSet<UUID>()
    /** Ponowienia discoverServices przy pustej liście / błędzie. */
    private var servicesDiscoverRetries: Int = 0
    /** Kolejne nieudane writeCharacteristic — bez limitu wisiało do timeoutu (Q3 GATT 13). */
    private var writeFailStreak: Int = 0
    /** Ostatni payload write — nie ufaj characteristic.value po błędzie (Samsung). */
    private var lastWritePayload: ByteArray? = null
    /** CCCD write fail (192/133 na Tab S4) — limit zamiast wiecznego retry. */
    private var cccdFailStreak: Int = 0
    /** Adres bieżącego connect — PEF reconnect przy CCCD 133/192. */
    private var connectAddress: String? = null
    /** PEF: jeden reconnect GATT po CCCD fail (Samsung Tab). */
    private var pefCccdReconnectTried = false
    /** PEF: charakterystyka GTD (jak DPS lung4000 notify). */
    private var pefNotifyChar: BluetoothGattCharacteristic? = null
    /** PEF: CCCD zapisane OK — dopiero wtedy wolno kick/nasłuch. */
    private var pefCccdOk = false
    /** PEF: DPS robi read_by_type 0x2902 przed write — trzymamy pending. */
    private var pefPendingCccd: BluetoothGattDescriptor? = null
    private var pefPendingCccdValue: ByteArray? = null
    /** PEF Telit TIO: write credits (00000003) — bez grantu urządzenie nie wyśle GTD. */
    private var pefTioCreditsWrite: BluetoothGattCharacteristic? = null
    /** PEF Telit TIO: indicate credits (00000004). */
    private var pefTioCreditsIndicate: BluetoothGattCharacteristic? = null
    private var pefTioReadCredits: Int = 0
    /** PEF: już próbowaliśmy CCCD Indicate po ciszy ASTD 0 B. */
    private var pefIndicateFailoverTried = false
    /** qLabs: jeden reconnect gdy writeCharacteristic ciągle false. */
    private var qlabsWriteReconnectTried = false
    /**
     * qLabs V3: próby gdy Tab oddaje GATT bez NUS/FFF0 (fotka: tylko 1800/1801/a002).
     * 0 = pierwszy discover, 1 = rediscover in-place, potem clean reconnect.
     * `BluetoothGatt.refresh()` przed discover dawało wyścig → niepełny GATT (0.9.93/94).
     */
    private var qlabsNusRecoverStep = 0
    /** Już zrobiliśmy reconnect bez refresh przy braku NUS. */
    private var qlabsNusCleanReconnectTried = false
    /** qLabs: pierwszy writeCharacteristic=true — dopiero wtedy hello-retry. */
    private var qlabsWriteAccepted = false
    private val qlabsKickSettleRunnable = Runnable {
        if (!active.get() || sessionKind != BleVitalKind.INR_QLABS) return@Runnable
        kickProtocol()
    }
    private val pefSilenceRunnable = object : Runnable {
        override fun run() {
            if (!active.get() || sessionKind != BleVitalKind.PEF_VITALOGRAPH) return
            val session = vitalograph ?: return
            val rx = session.bytesReceived
            if (rx > 0) {
                onStatus("PEF⑦ RX $rx B — czekam na pełne TD (≥70 B jak DPS)…")
                handler.postDelayed(this, 2_500)
                return
            }
            if (!pefCccdOk) {
                onStatus("PEF④ CCCD jeszcze nie OK — bez nasłuchu (DPS by nie szedł dalej)")
                handler.postDelayed(this, 2_000)
                return
            }
            // Notify CCCD „OK” ale 0 B — spróbuj Indicate (część firmware BTLE).
            if (!pefIndicateFailoverTried) {
                tryPefIndicateFailover()
                handler.postDelayed(this, 5_000)
                return
            }
            onStatus(
                "PEF⑥ ASTD 0 B — dmuchnij HARD+FAST+LONG (IFU); aparat sam wyśle GTD",
            )
            handler.postDelayed(this, 5_000)
        }
    }
    /**
     * Android wymaga discoverServices, żeby oddać uchwyty ATT dla znanych UUID
     * (protokół znamy z BleProfiles — to nie jest szukanie protokołu).
     * Bez timeoutu Tab S4 potrafi wisieć w nieskończoność (0.9.62).
     */
    private val gattHandlesTimeoutRunnable = Runnable {
        if (!active.get()) return@Runnable
        finishFail(
            "GATT: brak uchwytów ATT w 8 s — wyłącz/włącz BT urządzenia i Ponów",
        )
    }
    private var spo2WaitStartedAt = 0L
    private var measureWaitStartedAt = 0L
    private var measureWaitLabel = "pomiar"
    private var measureWaitTimeoutMs: Long = BleVitalsWait.TIMEOUT_MS
    /** Tryb ciągły SpO₂ (EKG) — nie kończy sesji po pierwszym stabilnym wyniku. */
    private var spo2LiveMode = false
    private var onLiveReading: ((VitalReading) -> Unit)? = null
    private val measureProgressTick = object : Runnable {
        override fun run() {
            if (!active.get()) {
                onProgress?.invoke(null)
                return
            }
            val elapsed = android.os.SystemClock.uptimeMillis() - measureWaitStartedAt
            val limit = measureWaitTimeoutMs
            val p = (elapsed / limit.toFloat()).coerceIn(0f, 1f)
            onProgress?.invoke(p)
            if (elapsed >= limit) {
                if (spo2LiveMode) {
                    onStatus("SpO₂: nadal czekam na prawidłowy odczyt…")
                    measureWaitStartedAt = android.os.SystemClock.uptimeMillis()
                    handler.postDelayed(this, 200)
                    return
                }
                val sec = (limit / 1000L).toInt()
                val hint = when (sessionKind) {
                    BleVitalKind.SPO2_TD8255 -> when (td8255?.validCount ?: 0) {
                        0 -> "Brak prawidłowego SpO₂ w $sec s"
                        1 -> "Tylko jeden odczyt SpO₂ — potrzebny drugi (stabilny)"
                        else -> "Timeout SpO₂"
                    }
                    else -> "Brak pomiaru ($measureWaitLabel) w $sec s — Przerwij lub Ponów"
                }
                finishFail(hint)
            } else {
                handler.postDelayed(this, 200)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Binder thread — Compose tylko przez emit*; Samsung API 31+: device.name bez CONNECT.
            runCatching { handleScanResult(result) }
                .onFailure { emitStatus("Skan: pominięto reklamę (${it.javaClass.simpleName})") }
        }

        override fun onScanFailed(errorCode: Int) {
            emitStatus("Skan BLE nieudany ($errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun advertisedName(result: ScanResult): String? {
        val fromRecord = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
        if (fromRecord != null) return fromRecord
        return runCatching { result.device?.name?.takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun emitStatus(msg: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) onStatus(msg)
        else handler.post { onStatus(msg) }
    }

    private fun emitScanHit(hit: BleDeviceHit) {
        if (Looper.myLooper() == Looper.getMainLooper()) onScanHit(hit)
        else handler.post { onScanHit(hit) }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else handler.post(block)
    }

    private fun bindStatus(cb: (String) -> Unit) {
        onStatus = { msg -> runOnMain { cb(msg) } }
    }

    private fun bindDone(cb: (Result<VitalReading>) -> Unit) {
        onDone = { result -> runOnMain { cb(result) } }
    }

    private fun bindScanHit(cb: (BleDeviceHit) -> Unit) {
        onScanHit = { hit -> runOnMain { cb(hit) } }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val name = advertisedName(result)
        val k = kind ?: return
        val address = device.address ?: return
        if (ixellenceWatchAddress != null) {
            if (!address.equals(ixellenceWatchAddress, ignoreCase = true)) return
            consumeIxellenceAds(result)
            return
        }
        val uuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid.toString() }
        val weightMode = BleDeviceIdentity.isWeightKind(k)
        if (weightMode && (BleDeviceIdentity.isIxellenceName(name) || hasJpdManufacturer(result))) {
            val ix = consumeIxellenceAds(result)
            val hit = BleDeviceHit(
                address,
                name ?: "JPD",
                result.rssi,
                BleVitalKind.WEIGHT_IXELLENCE,
            )
            hits[address] = hit
            emitScanHit(hit)
            if (ix) return
            return
        }
        if (!BleProfiles.scanMatches(k, name, address, uuids)) return
        val resolved = when {
            BleDeviceIdentity.isBpKind(k) -> BleDeviceIdentity.resolveBp(name, uuids, address)
            weightMode -> BleDeviceIdentity.resolveWeight(name, uuids, address)
            else -> k
        }
        if (resolved == null && (BleDeviceIdentity.isBpKind(k) || weightMode)) return
        val hit = BleDeviceHit(address, name ?: address, result.rssi, resolved)
        hits[address] = hit
        emitScanHit(hit)
    }

    fun isBluetoothReady(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan(
        kind: BleVitalKind,
        onHit: (BleDeviceHit) -> Unit,
        onStatus: (String) -> Unit,
        onAdvertisementReading: ((VitalReading) -> Unit)? = null,
    ) {
        stop()
        this.kind = kind
        bindScanHit(onHit)
        bindStatus(onStatus)
        this.onAdvertisementReading = onAdvertisementReading
        bindDone { }
        hits.clear()
        ixellenceWatchAddress = null
        ixellence = if (BleDeviceIdentity.isWeightKind(kind)) IxellenceSession() else null
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            emitStatus("Bluetooth niedostępny")
            return
        }
        active.set(true)
        emitStatus(
            when {
                BleDeviceIdentity.isBpKind(kind) ->
                    "Szukam TD-3140, TD-3128 lub Microlife BP B6…"
                kind == BleVitalKind.SPO2_TD8255 -> "Szukam TD-8255 lub TD-8201…"
                kind == BleVitalKind.TEMP_TD1241 -> "Szukam TD-1241…"
                kind == BleVitalKind.GLU_TD4277 -> "Szukam Glucomaxx / TD-4277…"
                BleDeviceIdentity.isWeightKind(kind) ->
                    "Szukam wagi (Charder / TD-2555 / iXellence)…"
                else -> "Szukam ${kind.deviceHint}…"
            },
        )
        val started = runCatching { scanner.startScan(scanCallback) }
        if (started.isFailure) {
            active.set(false)
            val detail = started.exceptionOrNull()?.message ?: started.exceptionOrNull()?.javaClass?.simpleName
            emitStatus(
                "Nie udało się zacząć skanu${detail?.let { ": $it" } ?: ""}. " +
                    "Android 10: włącz Lokalizację systemową i nadaj lokalizację aplikacji.",
            )
            return
        }
        scheduleScanWindow(scanner, kind)
    }

    /**
     * Okno skanu (domyślnie 60 s dla INR/BP/glu/wagi). Gdy kończy się bez hitów —
     * ponawia skan, dopóki sesja jest aktywna (pacjent często włącza aparat później).
     */
    @SuppressLint("MissingPermission")
    private fun scheduleScanWindow(scanner: android.bluetooth.le.BluetoothLeScanner, kind: BleVitalKind) {
        val scanMs = BleVitalsWait.scanTimeoutMs(kind)
        handler.postDelayed({
            if (!active.get() || ixellenceWatchAddress != null) return@postDelayed
            runCatching { scanner.stopScan(scanCallback) }
            if (hits.isNotEmpty()) {
                emitStatus("Znaleziono ${hits.size} — wybierz z listy.")
                return@postDelayed
            }
            if (BleVitalsWait.shouldRestartEmptyScan(kind)) {
                emitStatus(
                    "Brak urządzenia — ponawiam skan (${scanMs / 1000} s). " +
                        "Włącz pomiar na aparacie…",
                )
                val again = runCatching { scanner.startScan(scanCallback) }
                if (again.isSuccess) {
                    scheduleScanWindow(scanner, kind)
                } else {
                    active.set(false)
                    emitStatus(
                        "Nie udało się ponowić skanu. " +
                            "Android 10: lokalizacja systemowa ON + uprawnienie lokalizacji.",
                    )
                }
            } else {
                active.set(false)
                emitStatus(
                    "Nie znaleziono urządzenia w ${scanMs / 1000} s — " +
                        "włącz pomiar na aparacie i skanuj ponownie.",
                )
            }
        }, scanMs)
    }

    @SuppressLint("MissingPermission")
    fun connect(
        address: String,
        kind: BleVitalKind,
        onStatus: (String) -> Unit,
        onDone: (Result<VitalReading>) -> Unit,
        deviceName: String? = null,
        onProgress: ((Float?) -> Unit)? = null,
        onChooseHistory: ((title: String, options: List<VitalHistoryOption>, fresh: VitalReading?) -> Unit)? = null,
    ) {
        stopGattOnly()
        handler.removeCallbacksAndMessages(null)
        this.kind = kind
        bindStatus(onStatus)
        bindDone(onDone)
        this.onChooseHistory = onChooseHistory?.let { cb ->
            { title, options, fresh -> runOnMain { cb(title, options, fresh) } }
        }
        this.onAdvertisementReading = null
        this.onProgress = onProgress?.let { cb ->
            { v -> runOnMain { cb(v) } }
        }
        this.deviceNameHint = deviceName ?: hits[address]?.name
        this.connectAddress = address
        pefCccdReconnectTried = false
        qlabsWriteReconnectTried = false
        qlabsNusRecoverStep = 0
        qlabsNusCleanReconnectTried = false
        qlabsWriteAccepted = false
        pefIndicateFailoverTried = false
        pefTioCreditsWrite = null
        pefTioCreditsIndicate = null
        pefTioReadCredits = 0
        val prior = hits[address]?.resolvedKind
        sessionKind = when {
            kind == BleVitalKind.BP_AUTO || kind == BleVitalKind.BP_TAIDOC_AUTO ->
                prior
                    ?: BleDeviceIdentity.resolveBp(deviceNameHint, emptyList(), address)
                    ?: run {
                        // Bez modelu w nazwie nie zgaduj TaiDoc — Microlife po FFF0 w GATT.
                        BleVitalKind.BP_AUTO
                    }
            kind == BleVitalKind.WEIGHT_AUTO ->
                prior
                    ?: BleDeviceIdentity.resolveWeight(deviceNameHint, emptyList(), address)
                    ?: BleVitalKind.WEIGHT_TD2555
            else -> kind
        }
        weightListenOnly = false
        td3140 = null
        td3128 = null
        bpAuto = null
        td2555 = null
        td8255 = null
        microlife = null
        charder = null
        ixellence = null
        ixellenceWatchAddress = null
        vitalograph = null
        qlabs = null
        td4277 = null
        gluSilenceRetries = 0
        servicesDiscoverRetries = 0
        writeFailStreak = 0
        writeRejectStreak = 0
        lastWritePayload = null
        cccdFailStreak = 0
        mtuReady = false
        mtuRequested = false
        pendingKickAfterMtu = false
        pefNotifyChar = null
        pefCccdOk = false
        pefPendingCccd = null
        pefPendingCccdValue = null
        onProgress?.invoke(null)
        when (sessionKind) {
            BleVitalKind.BP_TD3140 -> td3140 = Td3140Session()
            BleVitalKind.BP_TD3128 -> td3128 = Td3128Session()
            BleVitalKind.BP_TAIDOC_AUTO -> bpAuto = BpTaiDocAutoSession()
            // BP_AUTO: protokół po rozpoznaniu modelu (GATT / nazwa) — nie zgaduj TaiDoc.
            BleVitalKind.BP_AUTO -> Unit
            BleVitalKind.BP_MICROLIFE -> microlife = MicrolifeSession()
            BleVitalKind.WEIGHT_TD2555 -> td2555 = Td2555Session().also { it.armListen() }
            BleVitalKind.SPO2_TD8255 -> td8255 = Td8255Session()
            BleVitalKind.WEIGHT_CHARDER -> charder = CharderSession()
            BleVitalKind.WEIGHT_IXELLENCE -> {
                startIxellenceWatch(address)
                return
            }
            BleVitalKind.PEF_VITALOGRAPH ->
                // API §10.1 ASTD: listen-only (bez DI). ACK po TD.
                vitalograph = VitalographSession()
            BleVitalKind.INR_QLABS -> {
                qlabsVariant = BleDeviceIdentity.qlabsVariant(deviceNameHint)
                qlabs = QlabsSession(variant = qlabsVariant, deviceName = deviceNameHint)
                emitStatus(
                    if (qlabsVariant == QlabsVariant.V3) {
                        "qLabs V3 — NUS (jak gdy działało)…"
                    } else {
                        "qLabs V1 — hello FFF1/FFF4 (fallback NUS)…"
                    },
                )
            }
            BleVitalKind.GLU_TD4277 -> td4277 = Td4277Session()
            else -> Unit
        }
        val device = adapter?.getRemoteDevice(address)
        if (device == null) {
            onDone(Result.failure(IllegalStateException("Brak adaptera BLE")))
            return
        }
        active.set(true)
        val label = sessionKind?.deviceHint ?: address
        emitStatus("Łączenie z $label ($address)…")
        // TRANSPORT_LE — obowiązkowe dla dual-mode (Vitalograph asma-1 BT Classic+BLE).
        // Bez tego Android bywa łączy BR/EDR i GATT milczy (PEF „nie działa”).
        gatt = device.connectGatt(app, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private fun startIxellenceWatch(address: String) {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            onDone(Result.failure(IllegalStateException("Bluetooth niedostępny")))
            return
        }
        ixellenceWatchAddress = address
        ixellence = IxellenceSession()
        sessionKind = BleVitalKind.WEIGHT_IXELLENCE
        active.set(true)
        emitStatus("iXellence: nasłuch reklam BLE — stań na wadze…")
        val started = runCatching { scanner.startScan(scanCallback) }
        if (started.isFailure) {
            active.set(false)
            onDone(
                Result.failure(
                    IllegalStateException(
                        "Skan iXellence nie wystartował — na Android 10 włącz Lokalizację systemową.",
                    ),
                ),
            )
            return
        }
        startMeasureWait("iXellence")
        val waitMs = BleVitalsWait.timeoutMs(BleVitalKind.WEIGHT_IXELLENCE)
        val waitSec = BleVitalsWait.timeoutSec(BleVitalKind.WEIGHT_IXELLENCE)
        handler.postDelayed({
            if (active.get() && ixellenceWatchAddress != null) {
                runCatching { scanner.stopScan(scanCallback) }
                finishFail("Timeout iXellence — brak stabilnego wyniku w $waitSec s")
            }
        }, waitMs)
    }

    /**
     * Ciągły monitoring SpO₂ (np. podczas EKG). Po odrzuceniu pierwszego prawidłowego
     * wynik aktualizuje [onReading] bez zamykania GATT.
     */
    @SuppressLint("MissingPermission")
    fun startSpo2Monitor(
        address: String,
        onStatus: (String) -> Unit,
        onReading: (VitalReading) -> Unit,
        deviceName: String? = null,
    ) {
        stop()
        spo2LiveMode = true
        onLiveReading = onReading
        connect(
            address = address,
            kind = BleVitalKind.SPO2_TD8255,
            onStatus = onStatus,
            onDone = { },
            deviceName = deviceName ?: "TD-8255",
            onProgress = null,
        )
        spo2LiveMode = true
        onLiveReading = onReading
    }

    fun simulate(kind: BleVitalKind, onDone: (Result<VitalReading>) -> Unit) {
        val reading = when (kind) {
            BleVitalKind.BP_AUTO, BleVitalKind.BP_TAIDOC_AUTO, BleVitalKind.BP_TD3140 ->
                VitalReading(BleVitalKind.BP_TD3140, 128, 82, 72, deviceName = "TD-3140 (sym)")
            BleVitalKind.BP_TD3128 -> VitalReading(kind, 130, 84, 70, deviceName = "TD-3128 (sym)")
            BleVitalKind.BP_MICROLIFE -> VitalReading(kind, 124, 78, 68, deviceName = "Microlife (sym)")
            BleVitalKind.WEIGHT_AUTO, BleVitalKind.WEIGHT_TD2555 ->
                VitalReading(BleVitalKind.WEIGHT_TD2555, weightKg = 78.2, deviceName = "TD-2555 (sym)")
            BleVitalKind.WEIGHT_CHARDER -> VitalReading(kind, weightKg = 72.4, deviceName = "Charder (sym)")
            BleVitalKind.WEIGHT_IXELLENCE -> VitalReading(kind, weightKg = 81.5, deviceName = "iXellence (sym)")
            BleVitalKind.SPO2_TD8255 -> VitalReading(kind, spo2Percent = 98, pulseBpm = 74, deviceName = "TD-8255 (sym)")
            BleVitalKind.TEMP_TD1241 -> VitalReading(kind, temperatureC = 36.6, deviceName = "TD-1241 (sym)")
            BleVitalKind.PEF_VITALOGRAPH ->
                VitalReading(
                    kind,
                    peakFlowLMin = 420,
                    deviceName = "Vitalograph (sym)",
                    spirometry = VitalographAstd(
                        di = VitalographProtocol.DI_LUNG_BTLE,
                        pefLMin = 420,
                        fev075L = 2.89,
                        fev1L = 3.27,
                        fev10L = 4.80,
                        fev1OverFev10Percent = 68,
                        fef2575Lps = 3.95,
                        fev1PersonalBestL = 3.80,
                        pefPersonalBestLMin = 420,
                        fev1Percent = 86,
                        pefPercent = 100,
                        greenZonePercent = 80,
                        yellowZonePercent = 50,
                        orangeZonePercent = 30,
                    ),
                )
            BleVitalKind.INR_QLABS ->
                VitalReading(kind, inrValue = 1.25, deviceName = "qLabs (sym)", rawNote = "PT=12.5s")
            BleVitalKind.GLU_TD4277 ->
                VitalReading(kind, glucoseMgDl = 92, deviceName = "TD-4277 (sym)")
        }
        onDone(Result.success(reading))
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        active.set(false)
        handler.removeCallbacksAndMessages(null)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        stopGattOnly()
        hits.clear()
        ixellenceWatchAddress = null
        ixellence = null
        onAdvertisementReading = null
        onProgress?.invoke(null)
        onProgress = null
        spo2LiveMode = false
        onLiveReading = null
    }

    /** Manufacturer Data JPD-BS200/BS201 (version byte 0xC0). */
    private fun hasJpdManufacturer(result: ScanResult): Boolean {
        val sparse = result.scanRecord?.manufacturerSpecificData ?: return false
        for (i in 0 until sparse.size()) {
            val companyId = sparse.keyAt(i)
            val payload = sparse.valueAt(i) ?: continue
            if (IxellenceSession.looksLikeJpd(companyId, payload)) return true
        }
        return false
    }

    /** @return true gdy uzyskano stabilny wynik z reklamy. */
    @SuppressLint("MissingPermission")
    private fun consumeIxellenceAds(result: ScanResult): Boolean {
        val session = ixellence ?: return false
        val sparse = result.scanRecord?.manufacturerSpecificData ?: return false
        var anyJpd = false
        for (i in 0 until sparse.size()) {
            val companyId = sparse.keyAt(i)
            val payload = sparse.valueAt(i) ?: continue
            if (!IxellenceSession.looksLikeJpd(companyId, payload) && payload.size < 7) continue
            anyJpd = anyJpd || IxellenceSession.looksLikeJpd(companyId, payload)
            when (val o = IxellenceSession.parseAdvertisement(companyId, payload, session)) {
                is BleParseOutcome.Done -> {
                    runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
                    ixellenceWatchAddress = null
                    onStatus("OK: ${o.reading.summary}")
                    if (onAdvertisementReading != null) {
                        active.set(false)
                        onAdvertisementReading?.invoke(o.reading)
                    } else {
                        finish(o)
                    }
                    return true
                }
                is BleParseOutcome.Continue -> onStatus("JPD / iXellence: pomiar w toku — czekam na lock…")
                else -> Unit
            }
        }
        return anyJpd
    }

    @SuppressLint("MissingPermission")
    private fun stopGattOnly() {
        runCatching { gatt?.close() }
        gatt = null
        writeChar = null
        cccdQueue.clear()
        cccdInFlight = false
        writeQueue.clear()
        writeInFlight = false
        writeSeq = 0
        mtuReady = false
        pendingKickAfterMtu = false
        subscribed.clear()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Protokół = ramki z BleProfiles. Tu tylko mapowanie UUID→uchwyt ATT
                // (jak DPS find_information). Bez tego Android nie odda Characteristic.
                onStatus(
                    if (sessionKind == BleVitalKind.PEF_VITALOGRAPH) {
                        "PEF① połączono — mapuję ATT (jak DPS find_information)…"
                    } else {
                        "Połączono — mapuję uchwyty ATT…"
                    },
                )
                // requestMtu PRZED discover na Tab S4 zawieszało callback (0.9.62).
                // qLabs: MTU dopiero przy ramce >20 B (nie przed hello — 0.9.80).
                mtuReady = sessionKind != BleVitalKind.INR_QLABS
                mtuRequested = false
                pendingKickAfterMtu = false
                writeRejectStreak = 0
                qlabsWriteAccepted = false
                // HIGH zaraz po connect na Tab bywa GATT busy (Q3 write false ×12)
                // i CCCD 133 (PEF). DPS BlueGiga nie zmienia interval.
                // Nie wołamy requestConnectionPriority dla PEF ani qLabs.
                if (sessionKind != BleVitalKind.PEF_VITALOGRAPH &&
                    sessionKind != BleVitalKind.INR_QLABS
                ) {
                    runCatching {
                        g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    }
                }
                handler.removeCallbacks(gattHandlesTimeoutRunnable)
                // Q3: dłuższy timeout — settle + ewentualny rediscover/reconnect.
                val handlesTimeout =
                    if (sessionKind == BleVitalKind.INR_QLABS) 14_000L else 8_000L
                handler.postDelayed(gattHandlesTimeoutRunnable, handlesTimeout)
                // Q3/Tab: NIE wołaj refresh() przed discover — wyścig z wewnętrznym
                // rediscover → tylko 1800/1801/a002, bez NUS (fotka 0.9.94).
                // Jak 0.9.45 / DPS: settle, potem discoverServices.
                if (sessionKind == BleVitalKind.INR_QLABS && qlabsVariant == QlabsVariant.V3) {
                    handler.postDelayed({
                        if (!active.get() || gatt !== g) return@postDelayed
                        onStatus("qLabs V3: discover NUS (settle)…")
                        if (!g.discoverServices()) {
                            handler.removeCallbacks(gattHandlesTimeoutRunnable)
                            finishFail("GATT: nie przyjęto mapowania uchwytów ATT")
                        }
                    }, 500)
                    return
                }
                if (!g.discoverServices()) {
                    handler.removeCallbacks(gattHandlesTimeoutRunnable)
                    finishFail("GATT: nie przyjęto mapowania uchwytów ATT")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.removeCallbacks(gattHandlesTimeoutRunnable)
                handler.removeCallbacks(mtuTimeoutRunnable)
                if (active.get()) onStatus("Rozłączono")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            handler.removeCallbacks(mtuTimeoutRunnable)
            mtuReady = true
            if (sessionKind == BleVitalKind.INR_QLABS) {
                // MTU teraz dopiero gdy ramka >20 B — nie blokuj hello.
                if (pendingKickAfterMtu) {
                    onStatus("qLabs: MTU=$mtu — start protokołu…")
                    maybeKickPending()
                } else {
                    onStatus("qLabs: MTU=$mtu — kontynuuję zapis…")
                    flushWriteQueue(g)
                }
            } else {
                maybeKickPending()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || g.services.isNullOrEmpty()) {
                if (servicesDiscoverRetries < 2) {
                    servicesDiscoverRetries++
                    onStatus("Brak uchwytów ($status) — ponawiam (#$servicesDiscoverRetries)…")
                    handler.postDelayed({
                        if (!active.get()) return@postDelayed
                        g.discoverServices()
                    }, 500)
                    return
                }
                handler.removeCallbacks(gattHandlesTimeoutRunnable)
                finishFail(
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        "GATT: uchwyty ATT niedostępne (status=$status)"
                    } else {
                        "GATT: brak uchwytów — wyłącz/włącz BT urządzenia i Ponów"
                    },
                )
                return
            }
            handler.removeCallbacks(gattHandlesTimeoutRunnable)
            val k = kind ?: return
            onStatus("Uchwyty OK — protokół ${sessionKind?.deviceHint ?: k.deviceHint}…")
            // Od razu profil z BleProfiles (znane UUID), nie skan „wszystkiego”.
            if (k == BleVitalKind.BP_AUTO || sessionKind == BleVitalKind.BP_TAIDOC_AUTO ||
                sessionKind == BleVitalKind.BP_AUTO || sessionKind == BleVitalKind.BP_MICROLIFE ||
                sessionKind == BleVitalKind.BP_TD3140 || sessionKind == BleVitalKind.BP_TD3128
            ) {
                val uuids = g.services.map { it.uuid.toString().lowercase() }
                val name = deviceNameHint ?: g.device?.name
                val resolved = BleDeviceIdentity.resolveBp(name, uuids, g.device?.address)
                    ?: when {
                        // Microlife: usługa FFF0, bez TaiDoc 1523
                        uuids.any { it.startsWith("0000fff0") } &&
                            uuids.none { it.contains("1523") } &&
                            !BleDeviceIdentity.isTd3140Name(name) &&
                            !BleDeviceIdentity.isTd3128Name(name) ->
                            BleVitalKind.BP_MICROLIFE
                        BleDeviceIdentity.isTd3140Name(name) -> BleVitalKind.BP_TD3140
                        BleDeviceIdentity.isTd3128Name(name) -> BleVitalKind.BP_TD3128
                        else -> null
                    }
                if (resolved != null && resolved != sessionKind) {
                    sessionKind = resolved
                    td3140 = null
                    td3128 = null
                    bpAuto = null
                    microlife = null
                    when (resolved) {
                        BleVitalKind.BP_MICROLIFE -> microlife = MicrolifeSession()
                        BleVitalKind.BP_TD3140 -> td3140 = Td3140Session()
                        BleVitalKind.BP_TD3128 -> td3128 = Td3128Session()
                        else -> bpAuto = BpTaiDocAutoSession()
                    }
                    onStatus("Wykryto: ${resolved.deviceHint}")
                } else if (sessionKind == BleVitalKind.BP_AUTO && resolved == null) {
                    finishFail(
                        "Nie rozpoznano modelu. Potrzebne: TD-3140, TD-3128 lub Microlife BP B6.",
                    )
                    return
                }
            }

            if (k == BleVitalKind.WEIGHT_AUTO || k == BleVitalKind.WEIGHT_TD2555) {
                val uuids = g.services.map { it.uuid.toString() }
                val resolved = BleDeviceIdentity.resolveWeight(
                    deviceNameHint ?: g.device?.name,
                    uuids,
                    g.device?.address,
                )
                    ?: sessionKind
                when (resolved) {
                    BleVitalKind.WEIGHT_CHARDER -> {
                        sessionKind = BleVitalKind.WEIGHT_CHARDER
                        charder = CharderSession()
                        td2555 = null
                        onStatus("Wykryto: Charder")
                        // fall through to standard Charder GATT setup below
                    }
                    BleVitalKind.WEIGHT_IXELLENCE -> {
                        val addr = g.device?.address
                        stopGattOnly()
                        if (addr != null) startIxellenceWatch(addr)
                        else finishFail("Brak adresu iXellence")
                        return
                    }
                    else -> {
                        sessionKind = BleVitalKind.WEIGHT_TD2555
                        td2555 = Td2555Session().also { it.armListen() }
                        charder = null
                        setupWeightSubscriptions(g)
                        return
                    }
                }
            }

            if (k == BleVitalKind.WEIGHT_TD2555 && sessionKind == BleVitalKind.WEIGHT_TD2555) {
                setupWeightSubscriptions(g)
                return
            }

            val sk = sessionKind ?: k
            // PEF: GTD CCCD jak DPS. Settle 0,6 s po discover (BlueGiga ma naturalne opóźnienia).
            if (sk == BleVitalKind.PEF_VITALOGRAPH) {
                onStatus("PEF① ATT OK — settle 0,6 s przed CCCD (Tab)…")
                handler.postDelayed({
                    if (!active.get() || gatt !== g) return@postDelayed
                    if (setupVitalographListen(g)) return@postDelayed
                    finishFail("Brak charakterystyki GTD Vitalograph (fefb / 00000002)")
                }, 600)
                return
            }
            val primary: pl.cardioscp.rehab.ble.BleGattIds
            val alts: List<pl.cardioscp.rehab.ble.BleGattIds>
            if (sk == BleVitalKind.INR_QLABS) {
                // V3/Q3: NUS gdy jest; inaczej a002 (Tab: c302/c305 — fotka 0.9.95).
                // V1: FFF0 write FFF1 / notify FFF4 (pad 20 B); NUS failover po ciszy.
                if (qlabsVariant == QlabsVariant.V3) {
                    primary = BleProfiles.QLABS_NUS
                    alts = listOf(
                        BleProfiles.QLABS_A002,
                        BleProfiles.QLABS_FFF0,
                        BleProfiles.QLABS_FFF0_LEGACY,
                    )
                } else {
                    primary = BleProfiles.QLABS_FFF0
                    alts = listOf(
                        BleProfiles.QLABS_FFF0_LEGACY,
                        BleProfiles.QLABS_NUS,
                        BleProfiles.QLABS_A002,
                    )
                }
            } else {
                primary = BleProfiles.forKind(sk)
                alts = BleProfiles.alternateProfiles(sk)
            }
            var ids = primary
            var service = findService(g, primary.serviceUuid)
            if (service == null) {
                for (alt in alts) {
                    service = findService(g, alt.serviceUuid)
                    if (service != null) {
                        ids = alt
                        break
                    }
                }
            }
            if (service == null) {
                if (sk == BleVitalKind.INR_QLABS && qlabsVariant == QlabsVariant.V3) {
                    // Tab Q3: często tylko a002 (bez NUS) — użyj od razu, nie szukaj NUS.
                    val a002 = findService(g, BleProfiles.QLABS_A002.serviceUuid)
                    if (a002 != null) {
                        ids = BleProfiles.QLABS_A002
                        service = a002
                        onStatus("qLabs V3: kanał a002 (c304/c305) — bez NUS…")
                    } else {
                        val svcHint = g.services.take(8).joinToString(",") {
                            it.uuid.toString().lowercase().take(8)
                        }
                        val a002Hint = qlabsA002CharHint(g)
                        when {
                            qlabsNusRecoverStep == 0 -> {
                                qlabsNusRecoverStep = 1
                                onStatus(
                                    "qLabs: brak NUS/a002 (svc=$svcHint$a002Hint) — rediscover…",
                                )
                                handler.postDelayed({
                                    if (!active.get() || gatt !== g) return@postDelayed
                                    if (!g.discoverServices()) {
                                        finishFail("GATT: rediscover nie przyjęty")
                                    }
                                }, 700)
                                return
                            }
                            !qlabsNusCleanReconnectTried -> {
                                qlabsNusCleanReconnectTried = true
                                qlabsNusRecoverStep = 0
                                onStatus(
                                    "qLabs: dalej bez NUS/a002 (svc=$svcHint$a002Hint) — reconnect…",
                                )
                                reconnectQlabsGatt(refreshCache = false)
                                return
                            }
                            else -> {
                                finishFail(
                                    "Brak NUS/a002 na Q3 (svc=$svcHint$a002Hint) — " +
                                        "wyłącz/włącz BT aparatu i Ponów",
                                )
                                return
                            }
                        }
                    }
                }
                if (service == null) {
                    // PEF handled above — never fall through here.
                    if (sk == BleVitalKind.PEF_VITALOGRAPH) {
                        if (setupVitalographListen(g)) return
                    }
                    finishFail("Brak usługi ${primary.serviceUuid}")
                    return
                }
            }
            val n = service.getCharacteristic(UUID.fromString(ids.notifyUuid))
            writeChar = ids.writeUuid?.let { wu ->
                service.getCharacteristic(UUID.fromString(wu))
                    ?: findCharacteristicEverywhere(g, wu)
            }
            // qLabs: FFF1/FFF4 (V1) albo resolve; V3 TYLKO znane UUID (NUS TX / FFF1).
            if (sk == BleVitalKind.INR_QLABS && (n == null || writeChar == null)) {
                val resolved = resolveQlabsChars(service)
                if (resolved != null && isQlabsKnownWriteUuid(resolved.second.uuid)) {
                    writeChar = resolved.second
                    enableCccd(g, resolved.first)
                    // Jak happy-path: wszystkie Notify + V1 równoległy NUS.
                    subscribeAllNotify(g, service)
                    if (qlabsVariant == QlabsVariant.V1) {
                        val nus = findService(g, BleProfiles.QLABS_NUS.serviceUuid)
                        if (nus != null && nus.uuid != service.uuid) {
                            subscribeAllNotify(g, nus)
                            onStatus("qLabs V1: resolve FFF + nasłuch NUS…")
                        }
                    }
                    flushCccdQueue(g)
                    pendingKickAfterMtu = true
                    maybeKickAfterCccd()
                    onStatus(
                        "Gotowe — qLabs write@${resolved.second.uuid.toString().take(8)}…",
                    )
                    return
                }
                if (qlabsVariant == QlabsVariant.V3) {
                    val nusTx = findCharacteristicEverywhere(g, BleProfiles.QLABS_NUS.writeUuid!!)
                    val nusRx = findCharacteristicEverywhere(g, BleProfiles.QLABS_NUS.notifyUuid)
                    if (nusTx != null && nusRx != null) {
                        writeChar = nusTx
                        enableCccd(g, nusRx)
                        flushCccdQueue(g)
                        pendingKickAfterMtu = true
                        maybeKickAfterCccd()
                        onStatus("Gotowe — qLabs NUS TX (szukany globalnie)…")
                        return
                    }
                    // Tab: a002/c302+c305 gdy NUS nie ma TX/RX.
                    val a002 = findService(g, BleProfiles.QLABS_A002.serviceUuid)
                    val a002Setup = a002?.let { setupQlabsA002(g, it) }
                    if (a002Setup == true) return
                    if (!qlabsNusCleanReconnectTried) {
                        qlabsNusCleanReconnectTried = true
                        qlabsNusRecoverStep = 0
                        onStatus("qLabs: usługa bez TX/RX — reconnect…")
                        reconnectQlabsGatt(refreshCache = false)
                        return
                    }
                    finishFail("Brak kanału serial Q3 (NUS/a002) po reconnect")
                    return
                }
            }
            if (sk == BleVitalKind.INR_QLABS && qlabsVariant == QlabsVariant.V3 &&
                (n == null || writeChar == null) && ids == BleProfiles.QLABS_NUS
            ) {
                // Swap: czasem firmware ma TX/RX na odwrót w NUS.
                val altN = service.getCharacteristic(UUID.fromString(BleProfiles.QLABS_NUS.writeUuid!!))
                val altW = service.getCharacteristic(UUID.fromString(BleProfiles.QLABS_NUS.notifyUuid))
                if (n == null && altN != null) {
                    enableCccd(g, altN)
                    // Write nadal na właściwy TX (6e400002), nie na RX!
                    writeChar = writeChar
                        ?: service.getCharacteristic(UUID.fromString(BleProfiles.QLABS_NUS.writeUuid!!))
                        ?: altW
                    flushCccdQueue(g)
                    pendingKickAfterMtu = true
                    maybeKickAfterCccd()
                    onStatus(
                        "Gotowe — qLabs NUS (notify@TX uuid) write@${writeChar?.uuid.toString().take(8)}…",
                    )
                    return
                }
            }
            // a002: upewnij się że mamy c302/c305 gdy to ten profil.
            if (sk == BleVitalKind.INR_QLABS && ids == BleProfiles.QLABS_A002 &&
                (n == null || writeChar == null)
            ) {
                if (setupQlabsA002(g, service)) return
                finishFail("Brak c302/c305 na usłudze a002")
                return
            }
            // V3: tylko znane write UUID (NUS / FFF1 / a002 c302…).
            if (sk == BleVitalKind.INR_QLABS && writeChar != null &&
                !isQlabsKnownWriteUuid(writeChar!!.uuid)
            ) {
                onStatus(
                    "qLabs: odrzucam write@${writeChar!!.uuid.toString().take(8)} — szukam TX…",
                )
                writeChar = findCharacteristicEverywhere(g, BleProfiles.QLABS_NUS.writeUuid!!)
                    ?: findCharacteristicEverywhere(g, BleProfiles.QLABS_FFF0.writeUuid!!)
                    ?: findCharacteristicEverywhere(g, BleProfiles.QLABS_A002.writeUuid!!)
                if (writeChar == null || !isQlabsKnownWriteUuid(writeChar!!.uuid)) {
                    finishFail(
                        "Zły write UUID (${writeChar?.uuid?.toString()?.take(8) ?: "?"}) — brak TX",
                    )
                    return
                }
            }
            if (n == null) {
                finishFail("Brak charakterystyki notify/indicate")
                return
            }
            // a002 na Tab = bare hello jak NUS.
            if (sk == BleVitalKind.INR_QLABS && ids == BleProfiles.QLABS_A002) {
                qlabs?.useNusFraming()
            }
            enableCccd(g, n)
            // Jak 0.9.45: wszystkie Notify w usłudze NUS/FFF (nie PEF — PEF w setupVitalographListen).
            if (sk == BleVitalKind.INR_QLABS) {
                subscribeAllNotify(g, service)
            }
            // V1 §2.3: write FFF1 / notify FFF4 — równoległy nasłuch NUS (failover).
            if (sk == BleVitalKind.INR_QLABS && qlabsVariant == QlabsVariant.V1) {
                val nus = findService(g, BleProfiles.QLABS_NUS.serviceUuid)
                if (nus != null && nus.uuid != service.uuid) {
                    subscribeAllNotify(g, nus)
                    onStatus("qLabs V1: FFF0 + nasłuch NUS…")
                }
            }
            if (sk == BleVitalKind.GLU_TD4277) {
                // DPS td4277: ta sama 1524 do write i notify.
                writeChar = n
            }
            flushCccdQueue(g)
            pendingKickAfterMtu = true
            maybeKickAfterCccd()
            onStatus(
                if (sk == BleVitalKind.INR_QLABS) {
                    val ch = if (ids == BleProfiles.QLABS_A002) "a002" else "qLabs"
                    "Gotowe — $ch write@${writeChar?.uuid?.toString()?.take(8) ?: "?"}…"
                } else {
                    "Gotowe — czekam na pomiar (${sk.deviceHint})…"
                },
            )
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorRead(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            handlePefCccdRead(g, descriptor, status)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorRead(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray,
        ) {
            handlePefCccdRead(g, descriptor, status)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            cccdInFlight = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // 192 (0xC0) / 133 — Tab S4. DPS BlueGiga nie ma tego błędu (inny stack).
                cccdFailStreak++
                val prev = descriptor.value?.getOrNull(0)?.toInt()?.and(0xFF) ?: 0x01
                descriptor.value = if (prev == 0x02) {
                    byteArrayOf(0x02, 0x00)
                } else {
                    byteArrayOf(0x01, 0x00)
                }
                if (cccdFailStreak >= 5) {
                    finishFail(
                        if (sessionKind == BleVitalKind.PEF_VITALOGRAPH) {
                            "PEF④ CCCD FAIL status=$status ×$cccdFailStreak — " +
                                "tu się wywala (DPS write 0x2902 OK). Wyłącz/włącz BT aparatu."
                        } else {
                            "CCCD status=$status ×$cccdFailStreak — wyłącz/włącz BT aparatu i Ponów"
                        },
                    )
                    return
                }
                // PEF + 133/192: po 2 failach — jeden reconnect GATT (klasyczny fix Tab S4).
                if (sessionKind == BleVitalKind.PEF_VITALOGRAPH &&
                    (status == 133 || status == 192) &&
                    cccdFailStreak >= 2 &&
                    !pefCccdReconnectTried
                ) {
                    pefCccdReconnectTried = true
                    cccdFailStreak = 0
                    cccdQueue.clear()
                    onStatus("PEF④ CCCD $status — reconnect GATT (Samsung fix)…")
                    reconnectPefGatt()
                    return
                }
                val delayMs = when (status) {
                    192, 133 -> (600L * cccdFailStreak).coerceAtMost(2_500L)
                    else -> 250L
                }
                onStatus(
                    if (sessionKind == BleVitalKind.PEF_VITALOGRAPH) {
                        "PEF④ CCCD status=$status — ponawiam (#$cccdFailStreak)…"
                    } else {
                        "CCCD status=$status — ponawiam (#$cccdFailStreak)…"
                    },
                )
                cccdQueue.addFirst(descriptor)
                handler.postDelayed({ flushCccdQueue(g) }, delayMs)
                return
            }
            cccdFailStreak = 0
            if (sessionKind == BleVitalKind.PEF_VITALOGRAPH) {
                // Nie czyść kolejki — TIO: najpierw CCCD credits (00000004), potem GTD.
                if (cccdQueue.isNotEmpty()) {
                    flushCccdQueue(g)
                    return
                }
                pefNotifyChar?.let { ch ->
                    runCatching { g.setCharacteristicNotification(ch, true) }
                }
                pefTioCreditsIndicate?.let { ch ->
                    runCatching { g.setCharacteristicNotification(ch, true) }
                }
                // Telit: grant credits, ASTD dopiero po potwierdzeniu zapisu (lub timeout).
                if (grantPefTioCredits(g, force = true)) {
                    onStatus("PEF⑤ TIO credits wysłane — czekam na ACK…")
                    handler.postDelayed({
                        if (!active.get() || pefCccdOk) return@postDelayed
                        onStatus("PEF⑤ TIO credits — start ASTD (bez ACK write)…")
                        pefCccdOk = true
                        maybeKickAfterCccd()
                    }, 400)
                    return
                }
                onStatus("PEF⑤ CCCD OK (brak 00000003) — ASTD; może milczeć…")
                pefCccdOk = true
                maybeKickAfterCccd()
                return
            }
            flushCccdQueue(g)
            maybeKickAfterCccd()
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            if (handlePefTioCreditNotify(g, characteristic.uuid, data)) return
            handleNotify(data)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (handlePefTioCreditNotify(g, characteristic.uuid, value)) return
            handleNotify(value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            handleCharacteristicRead(
                g,
                characteristic.uuid,
                status,
                characteristic.value,
            )
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleCharacteristicRead(g, characteristic.uuid, status, value)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeInFlight = false
            // Telit TIO: po ACK zapisu credits → dopiero ASTD (guide §7.5).
            if (sessionKind == BleVitalKind.PEF_VITALOGRAPH &&
                !pefCccdOk &&
                isPefTioCreditsWriteUuid(characteristic.uuid)
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onStatus("PEF⑤ TIO credits ACK — ASTD nasłuch…")
                    pefCccdOk = true
                    maybeKickAfterCccd()
                } else {
                    onStatus("PEF⑤ TIO credits status=$status — ponawiam…")
                    handler.postDelayed({
                        if (!active.get() || pefCccdOk) return@postDelayed
                        grantPefTioCredits(g, force = true)
                    }, 300)
                }
                return
            }
            val ml = microlife
            if (ml != null && ml.isTurnOffPending() && status == BluetoothGatt.GATT_SUCCESS) {
                finish(ml.afterTurnOffWrite())
                return
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                val sk = sessionKind
                if (sk == BleVitalKind.INR_QLABS || sk == BleVitalKind.GLU_TD4277) {
                    val failed = lastWritePayload?.takeIf { it.isNotEmpty() }
                        ?: characteristic.value?.takeIf { it.isNotEmpty() }
                    if (failed != null) {
                        val uuidShort = characteristic.uuid.toString().take(8)
                        // 13 = GATT_INVALID_ATTRIBUTE_LENGTH.
                        // Pad ATT=20; gdy nadal 13 → bare (16 B hello); potem NUS.
                        if (status == 13 && sk == BleVitalKind.INR_QLABS) {
                            // a002: c302 to nie UART (GATT 13 @ 16 B, fotka 0.9.96) → c304/c303.
                            if (tryQlabsA002WriteFallback(g)) {
                                onStatus(
                                    "BLE write 13 (@$uuidShort, ${failed.size}B) — inny char a002…",
                                )
                                writeFailStreak++
                                writeQueue.addFirst(QlabsProtocol.stripBlePadding(failed))
                                flushWriteQueue(g)
                                return
                            }
                            // V3: NIGDY nie paduj 16→20 (fotka: pętla GATT 13 na złym UUID).
                            // Najpierw wymuś NUS TX jeśli write nie jest znany.
                            if (!isQlabsKnownWriteUuid(characteristic.uuid) &&
                                tryQlabsNusWriteFallback(g)
                            ) {
                                onStatus(
                                    "BLE write 13 (@$uuidShort) — zły UUID, NUS TX…",
                                )
                                writeFailStreak++
                                val bare = QlabsProtocol.stripBlePadding(failed)
                                writeQueue.addFirst(bare)
                                flushWriteQueue(g)
                                return
                            }
                            // V1 FFF1: pad do 20 tylko gdy bare <20 i nadal na FFF.
                            if (qlabsVariant == QlabsVariant.V1 &&
                                failed.size < QlabsProtocol.MIN_BLE_WRITE_BYTES &&
                                writeFailStreak < 1
                            ) {
                                val pad = ByteArray(QlabsProtocol.MIN_BLE_WRITE_BYTES - failed.size)
                                onStatus(
                                    "BLE write 13 (${failed.size}B @$uuidShort <20) — pad→20…",
                                )
                                writeFailStreak++
                                writeQueue.addFirst(failed + pad)
                                flushWriteQueue(g)
                                return
                            }
                            if (failed.size in QlabsProtocol.PROTOCOL_MIN_WRITE_BYTES..20 &&
                                writeFailStreak < 2
                            ) {
                                onStatus(
                                    "BLE write 13 (${failed.size}B @$uuidShort) — MTU + ponów…",
                                )
                                writeFailStreak++
                                mtuReady = false
                                mtuRequested = false
                                pendingKickAfterMtu = false
                                writeQueue.addFirst(
                                    if (qlabsVariant == QlabsVariant.V3) {
                                        QlabsProtocol.stripBlePadding(failed)
                                    } else {
                                        failed.copyOf()
                                    },
                                )
                                requestQlabsMtuAfterCccd()
                                handler.postDelayed({
                                    if (!active.get()) return@postDelayed
                                    mtuReady = true
                                    flushWriteQueue(g)
                                }, 1_400)
                                return
                            }
                            // Pad 20 / MTU nie pomogły — spróbuj bare (bez zer po CRC).
                            val bare = QlabsProtocol.stripBlePadding(failed)
                            if (bare.size < failed.size && writeFailStreak < 3) {
                                onStatus(
                                    "BLE write 13 (${failed.size}B @$uuidShort) — bare ${bare.size}B…",
                                )
                                writeFailStreak++
                                writeQueue.addFirst(bare)
                                flushWriteQueue(g)
                                return
                            }
                            // Ostatnia szansa: NUS TX zamiast FFF1.
                            if (writeFailStreak < 4 &&
                                tryQlabsNusWriteFallback(g)
                            ) {
                                onStatus(
                                    "BLE write 13 (@$uuidShort) — fallback NUS TX…",
                                )
                                writeFailStreak++
                                writeQueue.addFirst(QlabsProtocol.stripBlePadding(failed))
                                flushWriteQueue(g)
                                return
                            }
                            if (failed.size > 20 && writeFailStreak < 2) {
                                onStatus(
                                    "BLE write 13 (${failed.size}B @$uuidShort >20) — MTU…",
                                )
                                writeFailStreak++
                                mtuReady = false
                                mtuRequested = false
                                pendingKickAfterMtu = false
                                writeQueue.addFirst(failed.copyOf())
                                requestQlabsMtuAfterCccd()
                                handler.postDelayed({
                                    if (!active.get()) return@postDelayed
                                    mtuReady = true
                                    flushWriteQueue(g)
                                }, 1_400)
                                return
                            }
                            finishFail(
                                "BLE write 13 ×$writeFailStreak (@$uuidShort, ${failed.size}B) — " +
                                    "zły kanał lub MTU; wyłącz BT Q3 i Ponów",
                            )
                            return
                        }
                        writeFailStreak++
                        if (writeFailStreak >= 4) {
                            finishFail(
                                "BLE write status=$status ×$writeFailStreak " +
                                    "(${failed.size}B @$uuidShort) — sprawdź BT / Ponów",
                            )
                            return
                        }
                        onStatus(
                            "BLE write status=$status (${failed.size}B @$uuidShort) " +
                                "— ponawiam (#$writeFailStreak)…",
                        )
                        writeQueue.addFirst(failed.copyOf())
                    }
                }
            } else {
                writeFailStreak = 0
            }
            flushWriteQueue(g)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupWeightSubscriptions(g: BluetoothGatt) {
        weightListenOnly = true
        td2555?.armListen()
        val wanted = listOf(
            BleProfiles.WEIGHT_SCALE,
            BleProfiles.WEIGHT_FFF0,
            BleProfiles.TAIDOC,
        )
        var any = false
        for (ids in wanted) {
            val service = findService(g, ids.serviceUuid) ?: continue
            val notify = service.getCharacteristic(UUID.fromString(ids.notifyUuid))
            if (notify != null) {
                enableCccd(g, notify)
                any = true
            }
            // Dodatkowe charakterystyki FFF2/FFF3 (Indicate/Notify)
            if (ids == BleProfiles.WEIGHT_FFF0) {
                for (extra in listOf("0000fff2-0000-1000-8000-00805f9b34fb", "0000fff3-0000-1000-8000-00805f9b34fb")) {
                    val ch = service.getCharacteristic(UUID.fromString(extra))
                    if (ch != null) enableCccd(g, ch)
                }
            }
            if (ids.writeUuid != null && writeChar == null) {
                writeChar = service.getCharacteristic(UUID.fromString(ids.writeUuid))
            }
        }
        if (!any) {
            finishFail("Brak usługi wagi (181D / FFF0 / TaiDoc 1523)")
            return
        }
        flushCccdQueue(g)
        pendingKickAfterMtu = true
        maybeKickAfterCccd()
        onStatus("Nasłuch wagi — stań na wadze lub poczekaj na push wyniku…")
    }

    private fun findService(g: BluetoothGatt, uuid: String): BluetoothGattService? =
        g.services.firstOrNull { it.uuid.toString().equals(uuid, ignoreCase = true) }
            ?: g.services.firstOrNull { svc ->
                // Krótki UUID (np. fefb) — BlueGiga / DPS porównuje 16-bit.
                val short = uuid.substringAfter("0000").substringBefore("-")
                short.length in 4..8 && svc.uuid.toString().contains(short, ignoreCase = true)
            }

    private fun findCharacteristicEverywhere(
        g: BluetoothGatt,
        uuid: String,
    ): BluetoothGattCharacteristic? {
        val target = uuid.lowercase()
        for (svc in g.services) {
            svc.getCharacteristic(UUID.fromString(uuid))?.let { return it }
            for (ch in svc.characteristics) {
                val u = ch.uuid.toString().lowercase()
                if (u == target) return ch
                // BlueGiga-style UUID chipa urządzenia (nie host API z DPS/RPi).
                if (target.contains("008025000000") && u.contains("008025000000")) {
                    if (target.startsWith("00000001") && u.startsWith("00000001")) return ch
                    if (target.startsWith("00000002") && u.startsWith("00000002")) return ch
                    if (!target.startsWith("00000001") && !target.startsWith("00000002")) return ch
                }
                if (target.startsWith("00000002") && u.startsWith("00000002")) return ch
                if (target.startsWith("00000001") && u.startsWith("00000001")) return ch
            }
        }
        return null
    }

    /**
     * Vitalograph / LUNG4000 — Telit Terminal I/O (`fefb`) + API §10.1 ASTD.
     * DPS BlueGiga: subscribe notify GTD — credits w stacku BG.
     * Android: ① CCCD Indicate `00000004` ② CCCD Notify `00000002`
     * ③ write credits `00000003` ④ listen GTD.
     */
    @SuppressLint("MissingPermission")
    private fun setupVitalographListen(g: BluetoothGatt): Boolean {
        val ids = BleProfiles.VITALOGRAPH
        val primaryNotify = g.services
            .asSequence()
            .mapNotNull { it.getCharacteristic(UUID.fromString(ids.notifyUuid)) }
            .firstOrNull()
            ?: findCharacteristicEverywhere(g, ids.notifyUuid)
        if (writeChar == null) {
            writeChar = ids.writeUuid?.let { wu ->
                g.services
                    .asSequence()
                    .mapNotNull { it.getCharacteristic(UUID.fromString(wu)) }
                    .firstOrNull()
                    ?: findCharacteristicEverywhere(g, wu)
            } ?: discoverVitalographWrite(g)
        }
        if (primaryNotify == null) {
            onStatus("PEF② brak GTD UUID ${ids.notifyUuid.take(8)}… w GATT")
            return false
        }
        pefNotifyChar = primaryNotify
        pefCccdOk = false
        pefTioReadCredits = 0
        val fefb = primaryNotify.service
        pefTioCreditsIndicate = fefb?.characteristics?.firstOrNull { ch ->
            ch.uuid.toString().equals(BleProfiles.VITALOGRAPH_TIO_CREDITS_INDICATE, ignoreCase = true) ||
                ch.uuid.toString().lowercase().startsWith("00000004")
        }
        pefTioCreditsWrite = fefb?.characteristics?.firstOrNull { ch ->
            ch.uuid.toString().equals(BleProfiles.VITALOGRAPH_TIO_CREDITS_WRITE, ignoreCase = true) ||
                ch.uuid.toString().lowercase().startsWith("00000003")
        }
        val props = primaryNotify.properties
        val hasN = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val hasI = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        val descCount = primaryNotify.descriptors.size
        onStatus(
            "PEF② GTD N=${if (hasN) 1 else 0} I=${if (hasI) 1 else 0} " +
                "TIO cW=${if (pefTioCreditsWrite != null) 1 else 0} " +
                "cI=${if (pefTioCreditsIndicate != null) 1 else 0} desc=$descCount…",
        )
        // Telit: najpierw Indicate na credits RX, potem Notify na GTD.
        pefTioCreditsIndicate?.let { enableCccd(g, it) }
        enableCccd(g, primaryNotify)
        if (cccdQueue.isEmpty() && pefPendingCccd == null) {
            onStatus(
                "PEF④ BRAK deskryptora 0x2902 (desc=$descCount) — " +
                    "lokalne notify; urządzenie może milczeć",
            )
        }
        if (pefPendingCccd == null) {
            flushCccdQueue(g)
        }
        pendingKickAfterMtu = true
        maybeKickAfterCccd()
        return true
    }

    /**
     * Samsung Tab S4: CCCD 133/192 często leczy świeży `connectGatt` (bez resetu sesji).
     * DPS BlueGiga nie ma tego błędu — to artefakt stacka Androida.
     */
    @SuppressLint("MissingPermission")
    private fun reconnectPefGatt() {
        val addr = connectAddress
        if (addr.isNullOrBlank()) {
            finishFail("PEF④ CCCD — brak adresu do reconnect")
            return
        }
        pefCccdOk = false
        pefNotifyChar = null
        pefPendingCccd = null
        pefPendingCccdValue = null
        subscribed.clear()
        gatt?.let { refreshGattCache(it) }
        stopGattOnly()
        handler.postDelayed({
            if (!active.get()) return@postDelayed
            val device = adapter?.getRemoteDevice(addr)
            if (device == null) {
                finishFail("PEF④ reconnect — brak adaptera BLE")
                return@postDelayed
            }
            onStatus("PEF① reconnect $addr…")
            gatt = device.connectGatt(app, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }, 700)
    }

    /** Ukryte API Samsung/AOSP — czyści cache usług przed ponownym discover. */
    private fun refreshGattCache(g: BluetoothGatt) {
        runCatching {
            val m = g.javaClass.getMethod("refresh")
            m.isAccessible = true
            val ok = m.invoke(g) as? Boolean ?: false
            onStatus("GATT refresh=${if (ok) "OK" else "no"}…")
        }
    }

    /** Diagnostyka: UUID charakterystyk pod 0000a002 (częsty jedyny custom svc gdy brak NUS). */
    private fun qlabsA002CharHint(g: BluetoothGatt): String {
        val svc = g.services.firstOrNull {
            it.uuid.toString().lowercase().startsWith("0000a002")
        } ?: return ""
        val chars = svc.characteristics.orEmpty().take(6).joinToString(",") { ch ->
            val u = ch.uuid.toString().lowercase().take(8)
            val p = ch.properties
            val flags = buildString {
                if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("N")
                if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append("I")
                if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("W")
                if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) append("w")
                if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("R")
            }
            "$u:$flags"
        }
        return if (chars.isEmpty()) " a002=∅" else " a002=[$chars]"
    }

    /**
     * Telit TIO: host→device credits na `00000003`.
     * Bez tego aparat ma 0 kredytów TX i GTD nie wychodzi (ASTD 0 B na Tab).
     */
    @SuppressLint("MissingPermission")
    private fun grantPefTioCredits(g: BluetoothGatt, force: Boolean = false): Boolean {
        val ch = pefTioCreditsWrite ?: return false
        val minCredits = 16
        val maxCredits = 64
        if (!force && pefTioReadCredits > 0) {
            pefTioReadCredits -= 1
        }
        if (!force && pefTioReadCredits > minCredits) return true
        val newCredits = (maxCredits - pefTioReadCredits).coerceIn(1, 255)
        pefTioReadCredits += newCredits
        val props = ch.properties
        // Pierwszy grant: DEFAULT gdy dostępne — dostajemy onCharacteristicWrite (Telit §7.5).
        ch.writeType = when {
            force && props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val ok = writeCharacteristicCompat(g, ch, byteArrayOf(newCredits.toByte()))
        if (!ok) {
            pefTioReadCredits -= newCredits
            onStatus("PEF⑤ TIO credits write nie przyjęty…")
            return false
        }
        onStatus("PEF⑤ TIO credits +$newCredits (Σ=$pefTioReadCredits)…")
        return true
    }

    private fun isPefTioCreditsWriteUuid(uuid: UUID): Boolean {
        val u = uuid.toString().lowercase()
        return u.startsWith("00000003") ||
            u.equals(BleProfiles.VITALOGRAPH_TIO_CREDITS_WRITE, ignoreCase = true)
    }

    /** Indicate na 00000004 = write-credits od urządzenia; GTD na 00000002 → odnów credits. */
    @SuppressLint("MissingPermission")
    private fun handlePefTioCreditNotify(
        g: BluetoothGatt,
        uuid: UUID,
        data: ByteArray,
    ): Boolean {
        if (sessionKind != BleVitalKind.PEF_VITALOGRAPH) return false
        val u = uuid.toString().lowercase()
        if (u.startsWith("00000004") ||
            u.equals(BleProfiles.VITALOGRAPH_TIO_CREDITS_INDICATE, ignoreCase = true)
        ) {
            // Credits for our writes (ACK) — nie mieszaj z buforem ASTD.
            return true
        }
        if (u.startsWith("00000002") ||
            u.equals(BleProfiles.VITALOGRAPH.notifyUuid, ignoreCase = true)
        ) {
            // Po każdym fragmencie GTD — odnów credits (jak Telit sample).
            grantPefTioCredits(g, force = false)
            return false // dalej parse ASTD
        }
        return false
    }

    /** DPS: read_by_type 0x2902 → dopiero potem write CCCD. */
    @SuppressLint("MissingPermission")
    private fun handlePefCccdRead(
        g: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
    ) {
        if (sessionKind != BleVitalKind.PEF_VITALOGRAPH) return
        val pending = pefPendingCccd ?: return
        if (pending.uuid != descriptor.uuid) return
        val want = pefPendingCccdValue ?: byteArrayOf(0x01, 0x00)
        pefPendingCccd = null
        pefPendingCccdValue = null
        if (status != BluetoothGatt.GATT_SUCCESS) {
            onStatus("PEF③ read 0x2902 status=$status — write i tak…")
        } else {
            onStatus("PEF③ 0x2902 odczytany — write CCCD…")
        }
        descriptor.value = want.copyOf()
        cccdQueue.addLast(descriptor)
        flushCccdQueue(g)
    }

    /** Subskrypcja wszystkich Notify/Indicate w usłudze (V1 qLabs NUS failover). */
    @SuppressLint("MissingPermission")
    private fun subscribeAllNotify(g: BluetoothGatt, service: BluetoothGattService) {
        for (ch in service.characteristics) {
            val p = ch.properties
            val canRx = p and (
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_INDICATE
                ) != 0
            if (canRx) enableCccd(g, ch)
        }
    }

    /** Wymuś CCCD Notify albo Indicate (PEF failover przy 0 B RX). */
    @SuppressLint("MissingPermission")
    private fun forceCccd(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        indicate: Boolean,
    ) {
        g.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            ?: characteristic.descriptors.firstOrNull { d ->
                d.uuid.toString().contains("2902", ignoreCase = true)
            } ?: return
        cccd.value = if (indicate) {
            byteArrayOf(0x02, 0x00) // Indicate — świeża tablica (nie shared static)
        } else {
            byteArrayOf(0x01, 0x00) // Notify
        }
        subscribed.remove(characteristic.uuid) // pozwól ponowić enable
        subscribed.add(characteristic.uuid)
        cccdQueue.addLast(cccd)
    }

    /** Notify CCCD OK ale ASTD 0 B — spróbuj Indicate (API bywa Indicate-only). */
    @SuppressLint("MissingPermission")
    private fun tryPefIndicateFailover() {
        if (pefIndicateFailoverTried) return
        val g = gatt ?: return
        val ch = pefNotifyChar ?: return
        val hasI = ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        if (!hasI) {
            onStatus("PEF⑥ brak Indicate na GTD — zostaję przy Notify")
            pefIndicateFailoverTried = true
            return
        }
        pefIndicateFailoverTried = true
        onStatus("PEF⑥ ASTD 0 B — CCCD Indicate failover…")
        forceCccd(g, ch, indicate = true)
        flushCccdQueue(g)
    }

    /** Write GATT: UUID `00000001-…` albo pierwsza Write w usłudze fefb. */
    private fun discoverVitalographWrite(g: BluetoothGatt): BluetoothGattCharacteristic? {
        BleProfiles.VITALOGRAPH.writeUuid?.let { findCharacteristicEverywhere(g, it) }?.let { return it }
        val fefb = findService(g, BleProfiles.VITALOGRAPH.serviceUuid)
        if (fefb != null) {
            for (ch in fefb.characteristics) {
                val u = ch.uuid.toString().lowercase()
                if (u.startsWith("00000002")) continue // notify
                val p = ch.properties
                if (p and (
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                        ) != 0
                ) {
                    return ch
                }
            }
        }
        for (svc in g.services) {
            for (ch in svc.characteristics) {
                val u = ch.uuid.toString().lowercase()
                if (!u.startsWith("00000001")) continue
                val p = ch.properties
                if (p and (
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                        ) != 0
                ) {
                    return ch
                }
            }
        }
        return null
    }

    /**
     * Fallback qLabs: pierwsza usługa z charakterystyką Notify/Indicate + Write.
     * @return Pair(notifyChar, writeChar)
     */
    private fun discoverSerialChars(g: BluetoothGatt): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        for (service in g.services) {
            resolveQlabsChars(service)?.let { return it }
            var notify: BluetoothGattCharacteristic? = null
            var write: BluetoothGattCharacteristic? = null
            for (ch in service.characteristics) {
                val props = ch.properties
                val canNotify = props and (
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE
                    ) != 0
                val canWrite = props and (
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                    ) != 0
                if (canNotify && notify == null) notify = ch
                if (canWrite && write == null) write = ch
            }
            if (notify != null && write != null) return notify to write
        }
        return null
    }

    /** Preferuj mapowanie z protokołu V1: write FFF1, notify FFF4. */
    private fun resolveQlabsChars(
        service: BluetoothGattService,
    ): Pair<BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        val fff1 = service.getCharacteristic(UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"))
        val fff4 = service.getCharacteristic(UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"))
        if (fff1 != null && fff4 != null) return fff4 to fff1
        val fff2 = service.getCharacteristic(UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"))
        if (fff1 != null && fff2 != null) {
            // Legacy: FFF1 notify / FFF2 write — albo odwrotnie.
            val nProps = fff1.properties
            val wProps = fff2.properties
            val fff1Notify = nProps and (
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_INDICATE
                ) != 0
            val fff2Write = wProps and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (fff1Notify && fff2Write) return fff1 to fff2
            val fff2Notify = wProps and (
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_INDICATE
                ) != 0
            val fff1Write = nProps and (
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                ) != 0
            if (fff2Notify && fff1Write) return fff2 to fff1
        }
        return null
    }

    /** NUS TX / FFF1 / FFF2 / a002 c302–c304 (fotka Tab: jedyny kanał Q3). */
    private fun isQlabsKnownWriteUuid(uuid: UUID): Boolean {
        val u = uuid.toString().lowercase()
        return u == BleProfiles.QLABS_NUS.writeUuid!!.lowercase() ||
            u == BleProfiles.QLABS_FFF0.writeUuid!!.lowercase() ||
            u == BleProfiles.QLABS_FFF0_LEGACY.writeUuid!!.lowercase() ||
            u == BleProfiles.QLABS_A002.writeUuid!!.lowercase() ||
            u.startsWith("6e400002") ||
            u.startsWith("0000fff1") ||
            u.startsWith("0000fff2") ||
            u.startsWith("0000c302") ||
            u.startsWith("0000c303") ||
            u.startsWith("0000c304")
    }

    /**
     * Q3 Tab: usługa a002 — write **c304** (DEFAULT), nie c302.
     * c302: GATT 13 na hello 16 B (fotka 0.9.96) — za krótki max length, nie UART.
     */
    @SuppressLint("MissingPermission")
    private fun setupQlabsA002(g: BluetoothGatt, service: BluetoothGattService): Boolean {
        val notify = service.getCharacteristic(
            UUID.fromString(BleProfiles.QLABS_A002.notifyUuid),
        ) ?: service.characteristics.firstOrNull { ch ->
            ch.uuid.toString().lowercase().startsWith("0000c305") ||
                (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
        } ?: return false
        // Serial TX: c304 WRITE, potem c303 WNR. c302 pomijamy (GATT 13 @ 16 B).
        val write = service.getCharacteristic(
            UUID.fromString(BleProfiles.QLABS_A002.writeUuid!!),
        ) ?: service.getCharacteristic(
            UUID.fromString("0000c303-0000-1000-8000-00805f9b34fb"),
        ) ?: return false
        writeChar = write
        qlabs?.useNusFraming()
        enableCccd(g, notify)
        subscribeAllNotify(g, service)
        flushCccdQueue(g)
        pendingKickAfterMtu = true
        maybeKickAfterCccd()
        onStatus(
            "Gotowe — qLabs a002 write@${write.uuid.toString().take(8)} " +
                "notify@${notify.uuid.toString().take(8)}…",
        )
        return true
    }

    /**
     * GATT 13 na a002: kolejny char serial (c304 → c303). Nie wracaj na c302.
     */
    @SuppressLint("MissingPermission")
    private fun tryQlabsA002WriteFallback(g: BluetoothGatt): Boolean {
        val svc = findService(g, BleProfiles.QLABS_A002.serviceUuid) ?: return false
        val current = writeChar?.uuid?.toString()?.lowercase() ?: ""
        val order = listOf(
            "0000c304-0000-1000-8000-00805f9b34fb",
            "0000c303-0000-1000-8000-00805f9b34fb",
        )
        for (uuid in order) {
            if (current.startsWith(uuid.take(8))) continue
            val ch = svc.getCharacteristic(UUID.fromString(uuid)) ?: continue
            writeChar = ch
            qlabs?.useNusFraming()
            writeInFlight = false
            onStatus("qLabs a002: write → ${uuid.take(8)}…")
            return true
        }
        return false
    }

    /**
     * Po GATT 13 na FFF1 — przełącz write na NUS TX (Q3 dongle/serial).
     * @return true gdy udało się zmienić [writeChar].
     */
    @SuppressLint("MissingPermission")
    private fun tryQlabsNusWriteFallback(g: BluetoothGatt): Boolean {
        val nus = findService(g, BleProfiles.QLABS_NUS.serviceUuid) ?: return false
        val tx = nus.getCharacteristic(UUID.fromString(BleProfiles.QLABS_NUS.writeUuid!!))
            ?: return false
        val cur = writeChar?.uuid
        if (cur != null && cur == tx.uuid) return false
        writeChar = tx
        // V1 na NUS: bez pad 20 B (jak V3) — pad na NUS dawał problemy.
        if (qlabsVariant == QlabsVariant.V1) {
            qlabs?.useNusFraming()
            // Zamień zaległe padowane ramki na bare hello.
            writeQueue.clear()
            writeInFlight = false
            val bare = QlabsProtocol.buildFrame(QlabsProtocol.CMD_HELLO_CLIENT, padToBleMin = false)
            writeQueue.addLast(bare)
        }
        // Włącz notify NUS RX jeśli jeszcze nie.
        val rx = nus.getCharacteristic(UUID.fromString(BleProfiles.QLABS_NUS.notifyUuid))
        if (rx != null) enableCccd(g, rx)
        flushCccdQueue(g)
        return true
    }

    private fun startMeasureWait(label: String) {
        measureWaitLabel = label
        measureWaitStartedAt = android.os.SystemClock.uptimeMillis()
        measureWaitTimeoutMs = BleVitalsWait.timeoutMs(sessionKind ?: kind)
        onProgress?.invoke(0f)
        handler.removeCallbacks(measureProgressTick)
        handler.post(measureProgressTick)
    }

    @SuppressLint("MissingPermission")
    private fun enableCccd(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!subscribed.add(characteristic.uuid)) return
        g.setCharacteristicNotification(characteristic, true)
        // Standard CCCD 0x2902; część Bluegiga (Vitalograph) bywa bez deskryptora —
        // wtedy wystarczy setCharacteristicNotification.
        val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            ?: characteristic.descriptors.firstOrNull { d ->
                d.uuid.toString().endsWith("2902", ignoreCase = true) ||
                    d.uuid.toString().contains("2902", ignoreCase = true)
            }
        if (cccd == null) {
            onStatus(
                "PEF/Notify: brak deskryptora 2902 (desc=${characteristic.descriptors.size}) " +
                    "— lokalne powiadomienia, urządzenie może milczeć…",
            )
            return
        }
        val props = characteristic.properties
        val hasIndicate = props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        val hasNotify = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        val u = characteristic.uuid.toString().lowercase()
        // Telit TIO credits RX (00000004) — ZAWSZE Indicate (nie Notify).
        val tioCreditsIndicate = sessionKind == BleVitalKind.PEF_VITALOGRAPH &&
            (u.startsWith("00000004") ||
                u.equals(BleProfiles.VITALOGRAPH_TIO_CREDITS_INDICATE, ignoreCase = true))
        // DPS (BlueGiga): przy obu flagach Notify, nie Indicate — poza TIO credits.
        val preferNotifyOnly = !tioCreditsIndicate && (
            sessionKind == BleVitalKind.PEF_VITALOGRAPH ||
                sessionKind == BleVitalKind.INR_QLABS ||
                sessionKind == BleVitalKind.GLU_TD4277
            )
        cccd.value = when {
            tioCreditsIndicate -> byteArrayOf(0x02, 0x00)
            hasIndicate && !hasNotify -> byteArrayOf(0x02, 0x00)
            preferNotifyOnly && hasNotify -> byteArrayOf(0x01, 0x00)
            hasNotify && hasIndicate -> byteArrayOf(0x01, 0x00)
            hasNotify -> byteArrayOf(0x01, 0x00)
            hasIndicate -> byteArrayOf(0x02, 0x00)
            else -> byteArrayOf(0x01, 0x00)
        }
        if (sessionKind == BleVitalKind.PEF_VITALOGRAPH) {
            val cccdHex = cccd.value?.joinToString("") { "%02X".format(it) }
            onStatus(
                "PEF③ write CCCD $cccdHex @${u.take(8)}… " +
                    "(N=${if (hasNotify) 1 else 0} I=${if (hasIndicate) 1 else 0})",
            )
            cccdQueue.addLast(cccd)
            return
        }
        cccdQueue.addLast(cccd)
    }

    @SuppressLint("MissingPermission")
    private fun flushCccdQueue(g: BluetoothGatt) {
        if (cccdInFlight) return
        val next = cccdQueue.pollFirst() ?: return
        cccdInFlight = true
        // Samsung Tab: zawsze świeża kopia wartości przed writeDescriptor
        // (shared / stale byte[] → status 192).
        val v = next.value
        if (v != null && v.isNotEmpty()) {
            next.value = v.copyOf()
        }
        // Tab S4: krótka przerwa po setCharacteristicNotification zanim writeDescriptor.
        val write = Runnable {
            if (!active.get()) {
                cccdInFlight = false
            } else if (!g.writeDescriptor(next)) {
                cccdInFlight = false
                onStatus("CCCD write nie przyjęty — ponawiam…")
                cccdQueue.addFirst(next)
                handler.postDelayed({ flushCccdQueue(g) }, 120)
            }
        }
        if (sessionKind == BleVitalKind.PEF_VITALOGRAPH) {
            // Krótka przerwa jak inne urządzenia — 450 ms + multi-CCCD spowalniało Tab.
            handler.postDelayed(write, 200)
        } else {
            write.run()
        }
    }

    private fun cccdSettled(): Boolean =
        !cccdInFlight && cccdQueue.isEmpty() && pefPendingCccd == null

    private fun maybeKickAfterCccd() {
        if (!cccdSettled()) {
            scheduleCccdSettleTimeout()
            return
        }
        handler.removeCallbacks(cccdSettleTimeoutRunnable)
        if (pendingKickAfterMtu) {
            maybeKickPending()
            return
        }
        kickProtocol()
    }

    private val cccdSettleTimeoutRunnable = Runnable {
        if (!active.get()) return@Runnable
        if (cccdSettled()) {
            maybeKickAfterCccd()
            return@Runnable
        }
        val g = gatt ?: return@Runnable
        // Nie startuj protokołu bez CCCD — hello/PEF/glu byłyby głuche.
        cccdInFlight = false
        if (cccdQueue.isNotEmpty()) {
            onStatus("CCCD timeout — ponawiam deskryptor…")
            flushCccdQueue(g)
            scheduleCccdSettleTimeout()
            return@Runnable
        }
        finishFail("CCCD nie włączone — brak powiadomień BLE")
    }

    private fun scheduleCccdSettleTimeout() {
        handler.removeCallbacks(cccdSettleTimeoutRunnable)
        handler.postDelayed(cccdSettleTimeoutRunnable, 2_500)
    }

    private fun maybeKickPending() {
        if (!pendingKickAfterMtu) return
        if (!cccdSettled()) {
            scheduleCccdSettleTimeout()
            return
        }
        // V3/V1 hello ≤20 B mieści się w MTU 23 — nie requestMtu przed kick
        // (0.9.79: MTU przed hello → writeCharacteristic false „nie przyjęty”).
        // MTU dopiero przy ramce >20 B w flushWriteQueue (get result data 0 = 21 B).
        pendingKickAfterMtu = false
        handler.removeCallbacks(cccdSettleTimeoutRunnable)
        handler.removeCallbacks(mtuTimeoutRunnable)
        handler.removeCallbacks(qlabsKickSettleRunnable)
        // Tab: po CCCD stack bywa jeszcze busy → settle 0,6 s przed hello (0.9.82).
        if (sessionKind == BleVitalKind.INR_QLABS) {
            onStatus("qLabs: settle 0,6 s po CCCD — potem hello…")
            handler.postDelayed(qlabsKickSettleRunnable, 600)
        } else {
            handler.post { kickProtocol() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestQlabsMtuAfterCccd() {
        if (mtuRequested) return
        val g = gatt ?: return
        mtuRequested = true
        onStatus("qLabs: MTU przed ramką >20 B…")
        handler.removeCallbacks(mtuTimeoutRunnable)
        handler.postDelayed(mtuTimeoutRunnable, 1_200)
        if (!g.requestMtu(64)) {
            handler.removeCallbacks(mtuTimeoutRunnable)
            mtuReady = true
            if (pendingKickAfterMtu) {
                maybeKickPending()
            } else {
                flushWriteQueue(g)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun kickProtocol() {
        // Czekaj na CCCD tylko jeśli jeszcze w toku — z timeoutem w scheduleCccdSettleTimeout.
        if (!cccdSettled()) {
            pendingKickAfterMtu = true
            scheduleCccdSettleTimeout()
            onStatus("Czekam na CCCD…")
            return
        }
        pendingKickAfterMtu = false
        handler.removeCallbacks(cccdSettleTimeoutRunnable)
        val g = gatt ?: return
        val w = writeChar
        when (sessionKind ?: kind) {
            BleVitalKind.BP_TD3140 -> {
                val frame = td3140?.nextWrite()
                if (frame != null && w != null) write(g, w, frame)
                startMeasureWait("ciśnienie")
            }
            BleVitalKind.BP_TD3128 -> {
                val frame = td3128?.nextWrite()
                if (frame != null && w != null) write(g, w, frame)
                startMeasureWait("ciśnienie")
            }
            BleVitalKind.BP_TAIDOC_AUTO -> {
                val frame = bpAuto?.nextWrite()
                if (frame != null && w != null) write(g, w, frame)
                startMeasureWait("ciśnienie")
            }
            BleVitalKind.BP_AUTO -> {
                when {
                    microlife != null -> {
                        val frame = microlife?.nextWrite()
                        if (frame != null && w != null) write(g, w, frame)
                    }
                    bpAuto != null -> {
                        val frame = bpAuto?.nextWrite()
                        if (frame != null && w != null) write(g, w, frame)
                    }
                    td3140 != null -> {
                        val frame = td3140?.nextWrite()
                        if (frame != null && w != null) write(g, w, frame)
                    }
                    td3128 != null -> {
                        val frame = td3128?.nextWrite()
                        if (frame != null && w != null) write(g, w, frame)
                    }
                    else -> {
                        finishFail("Brak protokołu ciśnienia — wybierz TD-3140, TD-3128 lub Microlife BP B6.")
                        return
                    }
                }
                startMeasureWait("ciśnienie")
            }
            BleVitalKind.WEIGHT_TD2555 -> {
                onStatus("Czekam na wynik wagi (Indicate/Notify)…")
                // Po 2.5 s bez wyniku — spróbuj READ_WEIGHT 0x71 jeśli jest write.
                handler.postDelayed({
                    if (!active.get()) return@postDelayed
                    val session = td2555 ?: return@postDelayed
                    session.requestWeightNow()
                    val frame = session.nextWrite()
                    val gg = gatt
                    val ww = writeChar
                    if (frame != null && gg != null && ww != null) {
                        onStatus("Żądam wagi (TaiDoc 0x71)…")
                        write(gg, ww, frame)
                    }
                }, 2_500)
                // Timeout sesji
                handler.postDelayed({
                    if (!active.get()) return@postDelayed
                    val session = td2555 ?: return@postDelayed
                    finish(session.finishWithoutTurnOff())
                }, 25_000)
            }
            BleVitalKind.SPO2_TD8255 -> {
                val frame = td8255?.nextWrite()
                if (frame != null && w != null) write(g, w, frame)
                spo2WaitStartedAt = android.os.SystemClock.uptimeMillis()
                onStatus("SpO₂: czekam do ${BleVitalsWait.timeoutSec(BleVitalKind.SPO2_TD8255)} s (pierwszy prawidłowy odrzucam)…")
                startMeasureWait("SpO₂")
            }
            BleVitalKind.BP_MICROLIFE -> {
                val frame = microlife?.nextWrite()
                if (frame != null && w != null) write(g, w, frame)
                startMeasureWait("ciśnienie")
            }
            BleVitalKind.WEIGHT_CHARDER -> {
                val frame = charder?.pollCommand()
                if (frame != null && w != null) write(g, w, frame)
                startMeasureWait("waga")
            }
            BleVitalKind.WEIGHT_IXELLENCE, BleVitalKind.WEIGHT_AUTO ->
                onStatus("iXellence / waga — nasłuch…")
            BleVitalKind.TEMP_TD1241 -> {
                onStatus("Zmierz temperaturę na TD-1241 — czekam na Indicate…")
                startMeasureWait("temperatura")
            }
            BleVitalKind.PEF_VITALOGRAPH -> {
                // DPS lung4000.send_request: clear buf + timeout — BEZ write/READ/probe.
                if (!pefCccdOk) {
                    onStatus("PEF④ czekam na CCCD zanim nasłuch (jak DPS)…")
                    pendingKickAfterMtu = true
                    scheduleCccdSettleTimeout()
                    return
                }
                onStatus(
                    "PEF⑥ ASTD — dmuchnij HARD+FAST+LONG; aparat sam wyśle GTD (§10.1)…",
                )
                startMeasureWait("PEF")
                handler.removeCallbacks(pefSilenceRunnable)
                handler.postDelayed(pefSilenceRunnable, 5_000)
            }
            BleVitalKind.INR_QLABS -> {
                // Wypchnij całą kolejkę sesji (nie tylko pierwszą ramkę).
                var firstLen: Int? = null
                while (true) {
                    val frame = qlabs?.nextWrite() ?: break
                    if (firstLen == null) firstLen = frame.size
                    enqueueWrite(frame)
                }
                onStatus(
                    if (qlabsVariant == QlabsVariant.V3) {
                        "qLabs V3/Q3: hello ${firstLen ?: 16}B " +
                            "@${w?.uuid?.toString()?.take(8) ?: "?"}…"
                    } else {
                        "qLabs V1: hello client (FFF1 pad 20 B)…"
                    },
                )
                startMeasureWait("INR")
                // Hello-retry dopiero po pierwszym przyjętym write (inaczej flood ×12).
                if (qlabsWriteAccepted) {
                    scheduleQlabsHelloRetry()
                }
            }
            BleVitalKind.GLU_TD4277 -> {
                enqueueWrite(td4277?.nextWrite())
                onStatus("TD-4277: odczyt pamięci, potem sync zegara (0x33) jeśli trzeba…")
                startMeasureWait("glikemia")
                scheduleGluSilenceRetry()
            }
            null -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleNotify(data: ByteArray) {
        val sk = sessionKind ?: kind
        val outcome = when (sk) {
            BleVitalKind.BP_TD3140 -> {
                val session = td3140 ?: return
                val o = session.onNotify(data)
                if (session.needsWriteAfterNotify(o)) writeNext(session.nextWrite())
                o
            }
            BleVitalKind.BP_TD3128 -> {
                val session = td3128 ?: return
                val o = session.onNotify(data)
                if (TaiDocProtocol.commandOf(data) == TaiDocProtocol.CMD_SET_DEVICE_CLOCK) {
                    onStatus("TD-3128: ustawiam zegar urządzenia (0x33)…")
                }
                if (session.needsWriteAfterNotify(o)) writeNext(session.nextWrite())
                o
            }
            BleVitalKind.BP_TAIDOC_AUTO -> {
                val session = bpAuto ?: return
                val o = session.onNotify(data)
                if (session.needsWriteAfterNotify(o)) writeNext(session.nextWrite())
                o
            }
            BleVitalKind.BP_AUTO -> {
                when {
                    microlife != null -> {
                        val session = microlife!!
                        val o = session.onNotify(data)
                        if (o is BleParseOutcome.Continue) writeNext(session.nextWrite())
                        o
                    }
                    td3140 != null -> {
                        val session = td3140!!
                        val o = session.onNotify(data)
                        if (session.needsWriteAfterNotify(o)) writeNext(session.nextWrite())
                        o
                    }
                    td3128 != null -> {
                        val session = td3128!!
                        val o = session.onNotify(data)
                        if (session.needsWriteAfterNotify(o)) writeNext(session.nextWrite())
                        o
                    }
                    bpAuto != null -> {
                        val session = bpAuto!!
                        val o = session.onNotify(data)
                        if (session.needsWriteAfterNotify(o)) writeNext(session.nextWrite())
                        o
                    }
                    else -> return
                }
            }
            BleVitalKind.WEIGHT_TD2555 -> {
                // Najpierw szybki parse dowolnego formatu (WSS / FFF1).
                val quick = WeightParser.parseAny(data)
                if (quick != null && weightListenOnly) {
                    weightListenOnly = false
                    BleParseOutcome.Done(
                        VitalReading(
                            kind = BleVitalKind.WEIGHT_TD2555,
                            weightKg = quick,
                            deviceName = deviceNameHint ?: "TD-2555",
                        ),
                    )
                } else {
                    val session = td2555 ?: return
                    val o = session.onNotify(data)
                    if (session.needsWriteAfterNotify(o)) writeNext(session.nextWrite())
                    o
                }
            }
            BleVitalKind.SPO2_TD8255 -> {
                val session = td8255 ?: return
                val o = session.onNotify(data)
                session.statusHint?.let { onStatus(it) }
                o
            }
            BleVitalKind.BP_MICROLIFE -> {
                val session = microlife ?: return
                val o = session.onNotify(data)
                if (o is BleParseOutcome.Continue) writeNext(session.nextWrite())
                o
            }
            BleVitalKind.WEIGHT_CHARDER -> {
                val session = charder ?: return
                val o = session.onNotify(data)
                if (o is BleParseOutcome.Continue) writeNext(session.pollCommand())
                o
            }
            BleVitalKind.WEIGHT_AUTO, BleVitalKind.WEIGHT_IXELLENCE -> null
            BleVitalKind.TEMP_TD1241 -> HealthThermometerParser.parse(data)
            BleVitalKind.PEF_VITALOGRAPH -> {
                val session = vitalograph ?: return
                // ASTD §10.1 — tylko bufor GTD (bez gałęzi probe DI/AT).
                val o = session.onNotify(data)
                if (session.bytesReceived > 0) {
                    handler.removeCallbacks(pefSilenceRunnable)
                    onStatus(
                        "PEF⑦ RX ${data.size}B (łącznie ${session.bytesReceived} B)…",
                    )
                }
                session.lastStatus.takeIf { it.isNotBlank() }?.let { onStatus(it) }
                if (session.needsWriteAfterNotify(o)) {
                    enqueueWrite(session.nextWrite())
                }
                if (o is BleParseOutcome.NeedMore && session.bytesReceived in 1..24) {
                    val hex = data.take(12).joinToString("") { "%02X".format(it) }
                    onStatus("PEF⑦ fragment [$hex…] (łącznie ${session.bytesReceived} B)")
                }
                if (o is BleParseOutcome.NeedMore || o is BleParseOutcome.Continue) {
                    handler.removeCallbacks(pefFlushRunnable)
                    handler.postDelayed(pefFlushRunnable, 700)
                }
                if (o is BleParseOutcome.Done) {
                    handler.removeCallbacks(pefSilenceRunnable)
                    handler.postDelayed({ finish(o) }, 400)
                    return
                }
                o
            }
            BleVitalKind.INR_QLABS -> {
                val session = qlabs ?: return
                val o = session.onNotify(data)
                if (o !is BleParseOutcome.NeedMore) {
                    handler.removeCallbacks(qlabsHelloRetryRunnable)
                }
                if (session.needsWriteAfterNotify(o)) {
                    enqueueWrite(session.nextWrite())
                    // Druga komenda z kolejki (np. success + get information).
                    handler.postDelayed({
                        if (!active.get()) return@postDelayed
                        enqueueWrite(session.nextWrite())
                    }, 80)
                }
                o
            }
            BleVitalKind.GLU_TD4277 -> {
                val session = td4277 ?: return
                val o = session.onNotify(data)
                // Nie myl echa zapisu 0x2B (A3) z odpowiedzią „liczba OK”.
                when (TaiDocProtocol.commandOf(data)) {
                    TaiDocProtocol.CMD_READ_STORED_NUMBER ->
                        if (!session.awaitingFirstMemoryResponse()) {
                            onStatus("TD-4277: liczba w pamięci OK…")
                        }
                    TaiDocProtocol.CMD_READ_DEVICE_CLOCK ->
                        if (!session.awaitingDeviceClock()) {
                            onStatus("TD-4277: zegar odczytany — pobieram pomiary…")
                        }
                    TaiDocProtocol.CMD_READ_STORED_DATA_TIME ->
                        onStatus("TD-4277: czas rekordu…")
                    TaiDocProtocol.CMD_READ_STORED_DATA_RESULT ->
                        onStatus("TD-4277: wartość z pamięci…")
                    TaiDocProtocol.CMD_SET_DEVICE_CLOCK ->
                        if (!session.awaitingSetClock()) {
                            onStatus("TD-4277: zegar ustawiony (0x33)…")
                        } else {
                            onStatus("TD-4277: ustawiam zegar urządzenia…")
                        }
                    TaiDocProtocol.CMD_ENTERING_COMM ->
                        onStatus("TD-4277: entering comm (0x54) — ponawiam…")
                }
                if (session.needsWriteAfterNotify(o)) {
                    // Postęp protokołu — odnów budżet ciszy (0x2B nie może zjeść 0x25).
                    gluSilenceRetries = 0
                    val delay = session.delayNextWriteMs
                    val frame = session.nextWrite()
                    if (delay > 0) {
                        handler.postDelayed({ enqueueWrite(frame) }, delay)
                    } else {
                        enqueueWrite(frame)
                    }
                }
                scheduleGluSilenceRetry()
                o
            }
            null -> null
        } ?: return
        finish(outcome)
    }

    @SuppressLint("MissingPermission")
    private fun writeNext(frame: ByteArray?) {
        enqueueWrite(frame)
    }

    @SuppressLint("MissingPermission")
    private fun enqueueWrite(frame: ByteArray?) {
        if (frame == null) return
        writeQueue.addLast(frame)
        val g = gatt ?: return
        flushWriteQueue(g)
    }

    @SuppressLint("MissingPermission")
    private fun flushWriteQueue(g: BluetoothGatt) {
        if (writeInFlight) return
        val w = writeChar ?: return
        val frame = writeQueue.pollFirst() ?: return
        // qLabs: hello 16–20 B OK na MTU 23; „get result data 0” = 21 B → MTU najpierw.
        if (sessionKind == BleVitalKind.INR_QLABS && frame.size > 20 && !mtuReady) {
            writeQueue.addFirst(frame)
            onStatus("qLabs: ramka ${frame.size} B — czekam na MTU…")
            requestQlabsMtuAfterCccd()
            return
        }
        writeInFlight = true
        val seq = ++writeSeq
        write(g, w, frame)
        // Odblokuj kolejkę zawsze — TaiDoc/niektóre stacki nie wołają
        // onCharacteristicWrite dla DEFAULT, a NO_RESPONSE bywa bez callbacku.
        val unlockMs = if (w.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            80L
        } else {
            180L
        }
        handler.postDelayed({
            if (!active.get()) return@postDelayed
            // Tylko jeśli to nadal ten sam write (callback nie przyszedł).
            if (writeInFlight && writeSeq == seq) {
                writeInFlight = false
                flushWriteQueue(g)
            }
        }, unlockMs)
    }

    @SuppressLint("MissingPermission")
    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
        lastWritePayload = value.copyOf()
        ch.value = value
        val props = ch.properties
        val noResp = props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val withResp = props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        ch.writeType = when {
            // V1 na NUS: jak V3 — DEFAULT (NO_RESPONSE na NUS dawało E024).
            sessionKind == BleVitalKind.INR_QLABS && qlabsVariant == QlabsVariant.V1 &&
                ch.service?.uuid?.toString().equals(BleProfiles.QLABS_NUS.serviceUuid, ignoreCase = true) == true ->
                when {
                    withResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
            // V1 na FFF0: jak V3 — Write Request (DEFAULT). NO_RESPONSE bywało
            // „wysłane” bez dojścia do miernika → hello bez success.
            sessionKind == BleVitalKind.INR_QLABS && qlabsVariant == QlabsVariant.V1 -> when {
                withResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            // V3: Write Request — NO_RESPONSE dawało E024.
            sessionKind == BleVitalKind.INR_QLABS && qlabsVariant == QlabsVariant.V3 -> when {
                withResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            // TaiDoc 1524: DEFAULT jak DPS / inne TaiDoc. NO_RESPONSE bywa gubione.
            sessionKind == BleVitalKind.GLU_TD4277 -> when {
                withResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            // Vitalograph: ACK (1 B) / DI — Write Without Response gdy dostępne (Android GATT).
            sessionKind == BleVitalKind.PEF_VITALOGRAPH -> when {
                noResp -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                withResp -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                else -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        val ok = writeCharacteristicCompat(g, ch, value)
        if (!ok) {
            writeInFlight = false
            writeRejectStreak++
            if (sessionKind == BleVitalKind.INR_QLABS &&
                writeRejectStreak >= 4 &&
                !qlabsWriteReconnectTried
            ) {
                qlabsWriteReconnectTried = true
                writeRejectStreak = 0
                writeQueue.clear()
                writeQueue.addFirst(value)
                onStatus("qLabs: write GATT busy — reconnect 1×…")
                reconnectQlabsGatt()
                return
            }
            if (writeRejectStreak >= 12) {
                finishFail(
                    "BLE write nie przyjęty ×$writeRejectStreak — GATT zajęty; " +
                        "wyłącz/włącz BT na Q3 i Ponów",
                )
                return
            }
            val delay = when {
                writeRejectStreak <= 2 -> 400L
                writeRejectStreak <= 5 -> 700L
                else -> 1_100L
            }
            onStatus(
                "BLE write nie przyjęty — ponawiam (#$writeRejectStreak) " +
                    "@${ch.uuid.toString().take(8)} props=0x${"%02X".format(ch.properties)}…",
            )
            handler.postDelayed({
                if (!active.get()) return@postDelayed
                // Odśwież value — Samsung bywa czyści characteristic po fail.
                ch.value = value.copyOf()
                writeQueue.addFirst(value)
                flushWriteQueue(g)
            }, delay)
        } else {
            writeRejectStreak = 0
            if (sessionKind == BleVitalKind.INR_QLABS) {
                // Hello: flag dopiero po przyjęciu write — inaczej meter-first
                // hello trafia jako echo i sesja wiszi.
                val ascii = String(value, Charsets.US_ASCII)
                if (ascii.contains(QlabsProtocol.CMD_HELLO_CLIENT, ignoreCase = true)) {
                    qlabs?.markHostHelloSent()
                }
                if (!qlabsWriteAccepted) {
                    qlabsWriteAccepted = true
                    scheduleQlabsHelloRetry()
                }
            }
        }
    }

    /** API 33+ writeCharacteristic(ch, value, type); starsze: ch.value + writeCharacteristic(ch). */
    @SuppressLint("MissingPermission")
    private fun writeCharacteristicCompat(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val code = g.writeCharacteristic(ch, value, ch.writeType)
            return code == BluetoothGatt.GATT_SUCCESS
        }
        @Suppress("DEPRECATION")
        ch.value = value
        @Suppress("DEPRECATION")
        return g.writeCharacteristic(ch)
    }

    private fun handleCharacteristicRead(
        g: BluetoothGatt,
        uuid: UUID,
        status: Int,
        value: ByteArray?,
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS) return
        val data = value ?: return
        // PEF: niespodziewany read (np. stary stack) — potraktuj jak notify GTD.
        if (sessionKind == BleVitalKind.PEF_VITALOGRAPH && data.isNotEmpty()) {
            handleNotify(data)
        }
    }

    /**
     * Q3/Tab: świeży `connectGatt` po write-busy albo pustym cache bez NUS.
     * `refreshCache` — ukryte `BluetoothGatt.refresh()` przed disconnect.
     */
    @SuppressLint("MissingPermission")
    private fun reconnectQlabsGatt(refreshCache: Boolean = false) {
        val addr = connectAddress
        if (addr.isNullOrBlank()) {
            finishFail("qLabs reconnect — brak adresu")
            return
        }
        handler.removeCallbacks(qlabsKickSettleRunnable)
        handler.removeCallbacks(qlabsHelloRetryRunnable)
        writeQueue.clear()
        writeInFlight = false
        if (refreshCache) {
            gatt?.let { refreshGattCache(it) }
        }
        stopGattOnly()
        handler.postDelayed({
            if (!active.get()) return@postDelayed
            val device = adapter?.getRemoteDevice(addr)
            if (device == null) {
                finishFail("qLabs reconnect — brak adaptera BLE")
                return@postDelayed
            }
            qlabs = QlabsSession(variant = qlabsVariant, deviceName = deviceNameHint)
            qlabsWriteAccepted = false
            mtuReady = false
            mtuRequested = false
            servicesDiscoverRetries = 0
            onStatus("qLabs① reconnect $addr…")
            gatt = device.connectGatt(app, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }, 1_200)
    }

    private val pefFlushRunnable = Runnable {
        if (!active.get()) return@Runnable
        val session = vitalograph ?: return@Runnable
        val o = session.flushPartial()
        if (o is BleParseOutcome.Done) {
            if (session.needsWriteAfterNotify(o)) {
                enqueueWrite(session.nextWrite())
            }
            handler.postDelayed({ finish(o) }, 400)
        } else {
            finish(o)
        }
    }

    private val qlabsHelloRetryRunnable = object : Runnable {
        override fun run() {
            if (!active.get()) return
            val session = qlabs ?: return
            val frame = session.retryHelloIfWaiting() ?: return
            val n = session.helloRetryCount()
            // V1: po ciszy przełącz write na NUS — ale NIE doklejaj padded hello
            // zbudowanego przed useNusFraming() (pad 20 na NUS psuł unstick 0.9.83).
            if (qlabsVariant == QlabsVariant.V1 && (n == 1 || n == 3 || n == 5)) {
                val g = gatt ?: return
                if (tryQlabsNusWriteFallback(g)) {
                    onStatus("qLabs: NUS failover — hello bare (#$n)…")
                    flushWriteQueue(g)
                    handler.postDelayed(this, 1_200L)
                    return
                }
            }
            onStatus("qLabs: ponawiam hello client (#$n)…")
            writeNext(frame)
            val again = if (qlabsVariant == QlabsVariant.V3) 2_500L else 1_200L
            handler.postDelayed(this, again)
        }
    }

    private fun scheduleQlabsHelloRetry() {
        handler.removeCallbacks(qlabsHelloRetryRunnable)
        val first = if (qlabsVariant == QlabsVariant.V3) 2_500L else 1_200L
        handler.postDelayed(qlabsHelloRetryRunnable, first)
    }

    private val gluSilenceRetryRunnable = object : Runnable {
        override fun run() {
            if (!active.get()) return
            val session = td4277 ?: return
            if (!session.awaitingHostCommand()) return
            if (gluSilenceRetries >= 6) return
            gluSilenceRetries++
            session.forceResendCurrent()
            onStatus("TD-4277: cisza — ponawiam komendę (#$gluSilenceRetries)…")
            enqueueWrite(session.nextWrite())
            handler.postDelayed(this, 1_500)
        }
    }

    private fun scheduleGluSilenceRetry() {
        handler.removeCallbacks(gluSilenceRetryRunnable)
        if (sessionKind != BleVitalKind.GLU_TD4277) return
        val session = td4277 ?: return
        if (!session.awaitingHostCommand()) return
        handler.postDelayed(gluSilenceRetryRunnable, 1_500)
    }

    private fun finish(outcome: BleParseOutcome) {
        when (outcome) {
            is BleParseOutcome.Done -> {
                if (spo2LiveMode && sessionKind == BleVitalKind.SPO2_TD8255) {
                    onStatus("SpO₂: ${outcome.reading.summary}")
                    onLiveReading?.invoke(outcome.reading)
                    return
                }
                active.set(false)
                handler.removeCallbacksAndMessages(null)
                onProgress?.invoke(null)
                onStatus("OK: ${outcome.reading.summary}")
                onDone(Result.success(outcome.reading))
                stopGattOnly()
            }
            is BleParseOutcome.Choose -> {
                active.set(false)
                handler.removeCallbacksAndMessages(null)
                onProgress?.invoke(null)
                val freshReading = outcome.freshReading
                onStatus(
                    if (freshReading != null) "OK: ${freshReading.summary}"
                    else "Wybierz pomiar z historii…",
                )
                stopGattOnly()
                val cb = onChooseHistory
                if (cb != null) {
                    cb(outcome.title, outcome.options, freshReading)
                } else if (freshReading != null) {
                    onDone(Result.success(freshReading))
                } else {
                    onDone(Result.failure(IllegalStateException("Brak świeżego pomiaru — historia niedostępna w UI")))
                }
            }
            is BleParseOutcome.Fail -> finishFail(outcome.reason)
            BleParseOutcome.Continue, BleParseOutcome.NeedMore -> Unit
        }
    }

    private fun finishFail(reason: String) {
        if (spo2LiveMode && sessionKind == BleVitalKind.SPO2_TD8255) {
            onStatus(reason)
            return
        }
        active.set(false)
        handler.removeCallbacksAndMessages(null)
        onProgress?.invoke(null)
        onStatus(reason)
        onDone(Result.failure(IllegalStateException(reason)))
        stopGattOnly()
    }
}
