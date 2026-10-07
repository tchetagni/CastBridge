package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.os.SystemClock
import castbridge.core.btact.BtActAd
import castbridge.core.btact.BtActClient
import castbridge.core.owner.ActivationRoutePlan.Cause
import castbridge.core.owner.BleSearch
import castbridge.core.tunnel.BtConnectLock
import castbridge.core.tv.BtProtocol
import castbridge.owner.TvBluetooth
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * « Bluetooth sans appairage » de « Activer la TV » côté téléphone (act-bt, docs/BT-PLUG-AND-PLAY.md) : mince. Les décisions sont dans le cœur, testées en JVM ([BleSearch] : quelles annonces BLE sont
 * essayées, dans quel ordre, ce que dit chaque réponse de la TV ; [BtActClient] : la PAKE sur le code et le canal chiffré). Ici : balayer en BLE le service `…0008` (filtre sur l'UUID ; le tag du code
 * se vérifie dans le cœur sur l'enregistrement de la réponse de balayage), ouvrir le canal vers la TV SANS appairage ni boîte d'Android, lancer le cœur dessus, rendre ce qu'il conclut.
 *
 * Le canal est d'abord L2CAP « insecure » (Android 10 et plus, si l'annonce donne un PSM) : il passe par la liaison BLE que le téléphone vient de voir, dont l'adresse est aléatoire et ne sert pas à ouvrir une
 * liaison Bluetooth classique ; sinon, ou en cas d'échec, RFCOMM « insecure » sur l'UUID du service (qui n'aboutit que si l'adresse annoncée en BLE est celle du Bluetooth classique de la TV). Chaque ouverture se
 * fait sous le verrou de la TV ([BtConnectLock]), après l'arrêt du balayage, et sous une borne ([BleSearch.ATTEMPT_MS]) : une socket qui bloque est fermée de force.
 *
 * Bloquant : appeler hors du fil principal. Le code, la clé et la demande ne vont dans AUCUN journal (ce fichier n'écrit aucune ligne de journal) ; un échec ne rend qu'une cause typée.
 */
@SuppressLint("MissingPermission")
class BtActivationClient(ctx: Context) {
    private val app = ctx.applicationContext
    private val timers = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-act-ble-timer").apply { isDaemon = true } }

    /** Ce que la recherche rend : la TV qui a prouvé le code (son nom, sa version, sa demande d'appareil lue), ou la cause de l'échec. */
    sealed class Search {
        class Tv(val name: String, val version: String, val requestText: String?) : Search()
        class Failed(val cause: Cause) : Search()
    }

    /** Rend les minuteries. Un appel qui court encore ne lève jamais d'exception dans son fil (audit B1) : [later] et [every] rendent null une fois les minuteries rendues. */
    fun release() { timers.shutdownNow() }

    private fun later(ms: Long, task: Runnable): ScheduledFuture<*>? = try { timers.schedule(task, ms, TimeUnit.MILLISECONDS) } catch (e: RejectedExecutionException) { null }
    private fun every(ms: Long, task: Runnable): ScheduledFuture<*>? = try { timers.scheduleWithFixedDelay(task, ms, ms, TimeUnit.MILLISECONDS) } catch (e: RejectedExecutionException) { null }

    private fun adapter(): BluetoothAdapter? = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Pourquoi la voie est impossible tout de suite (Bluetooth absent, éteint, sans autorisation, sans balayeur BLE), ou null. */
    private fun impossible(adapter: BluetoothAdapter?): Cause? = when {
        adapter == null -> Cause(Cause.Kind.BT_NO_ADAPTER)
        !TvBluetooth.permitted(app) -> Cause(Cause.Kind.BLE_PERMISSION)
        !adapter.isEnabled -> Cause(Cause.Kind.BT_OFF)
        adapter.bluetoothLeScanner == null -> Cause(Cause.Kind.BLE_UNSUPPORTED)
        else -> null
    }

    // ------------------------------------------------------------------ chercher la TV et lire sa demande d'appareil

    /**
     * Balaye, essaie les TV dont le tag est celui de [code] (la plus forte d'abord), lit la demande d'appareil de la première qui prouve le code. null = abandonné ([active] est devenu faux : le plan est
     * passé à autre chose). [observed] reçoit ce que dit chaque TV qui refuse (code refusé, verrou…), pour que la cause serve si la borne du plan arrive avant la fin.
     */
    fun find(code: String, active: () -> Boolean, observed: (Cause) -> Unit): Search? = try { findNow(code, active, observed) }
        catch (e: InterruptedException) { Thread.currentThread().interrupt(); null }
        catch (e: Exception) { Search.Failed(Cause(Cause.Kind.BLE_CONNECT)) }                 // nothing but a typed cause leaves this class: a stray exception would end a pool thread, and the app with it

    private fun findNow(code: String, active: () -> Boolean, observed: (Cause) -> Unit): Search? {
        val adapter = adapter()
        impossible(adapter)?.let { return Search.Failed(it) }
        val heard = when (val s = scan(adapter!!, code, active)) { is Scanned.Failed -> return Search.Failed(s.cause); is Scanned.Heard -> s.tvs }
        if (!active()) return null
        val attempts = ArrayList<BleSearch.Attempt>()
        for ((device, c) in heard) {
            if (!active()) return null
            val a = attempt(adapter, device, c, code, active) { session ->
                when (val r = session.readRequest()) {
                    is BtActClient.Session.Request.Text -> BleSearch.Attempt.Reached(session.tvName, session.tvVersion, r.text)
                    BtActClient.Session.Request.Unavailable, BtActClient.Session.Request.Limit -> BleSearch.Attempt.Reached(session.tvName, session.tvVersion, null)
                    BtActClient.Session.Request.Lost -> BleSearch.Attempt.Lost()
                }
            }
            if (a is BleSearch.Attempt.Reached) return Search.Tv(a.tvName, a.tvVersion, a.requestText)
            attempts += a
            observed(BleSearch.causeOf(a))
        }
        return if (active()) Search.Failed(BleSearch.verdict(attempts, heard.size)) else null
    }

    // ------------------------------------------------------------------ installer la clé

    /**
     * Le canal est rouvert (la clé arrive parfois longtemps après la lecture de la demande : collée, reçue par WhatsApp) : balayage, PAKE sur le même code, puis la clé dans le canal chiffré. Ce que la TV
     * en dit est rendu au cœur ([BleSearch.installResult]). null = abandonné.
     */
    fun install(code: String, key: String, active: () -> Boolean): BleSearch.Install? = try { installNow(code, key, active) }
        catch (e: InterruptedException) { Thread.currentThread().interrupt(); null }
        catch (e: Exception) { BleSearch.Install.NotFound }

    private fun installNow(code: String, key: String, active: () -> Boolean): BleSearch.Install? {
        val adapter = adapter()
        if (impossible(adapter) != null) return BleSearch.Install.NotFound
        val heard = when (val s = scan(adapter!!, code, active)) { is Scanned.Failed -> return BleSearch.Install.NotFound; is Scanned.Heard -> s.tvs }
        if (!active()) return null
        if (heard.isEmpty()) return BleSearch.Install.NotFound
        var blocked: BleSearch.Attempt? = null
        for ((device, c) in heard) {
            if (!active()) return null
            var done: BtActClient.Session.Installed? = null
            val a = attempt(adapter, device, c, code, active) { session -> done = session.install(key); BleSearch.Attempt.Reached(session.tvName, session.tvVersion, null) }
            val d = done
            if (d != null) return BleSearch.Install.Done(d)
            blocked = a
        }
        return BleSearch.Install.Blocked(blocked ?: BleSearch.Attempt.NoConnection())
    }

    // ------------------------------------------------------------------ le balayage BLE

    private sealed class Scanned {
        class Heard(val tvs: List<Pair<BluetoothDevice, BleSearch.Candidate>>) : Scanned()
        class Failed(val cause: Cause) : Scanned()
    }

    private fun scan(adapter: BluetoothAdapter, code: String, active: () -> Boolean): Scanned {
        val scanner = adapter.bluetoothLeScanner ?: return Scanned.Failed(Cause(Cause.Kind.BLE_UNSUPPORTED))
        val collector = BleSearch.Collector(code)
        val devices = HashMap<String, BluetoothDevice>()
        val failed = AtomicBoolean(false)
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                // the manufacturer record rides in the advertisement (or, in the fallback layout, in its scan response: an advertisement heard before its response has none yet and is simply not a candidate yet)
                val payload = result.scanRecord?.getManufacturerSpecificData(BtActAd.COMPANY_ID)
                synchronized(collector) { if (collector.add(BleSearch.Sighting(device.address, result.rssi, payload), SystemClock.elapsedRealtime())) devices[device.address] = device }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>?) { results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) } }
            override fun onScanFailed(errorCode: Int) { failed.set(true) }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(BtActAd.SERVICE_UUID))).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val started = SystemClock.elapsedRealtime()
        try { scanner.startScan(listOf(filter), settings, callback) } catch (e: Exception) { return Scanned.Failed(Cause(Cause.Kind.BLE_SCAN_FAILED)) }
        try {
            while (active() && !failed.get() && !synchronized(collector) { collector.scanDone(started, SystemClock.elapsedRealtime()) }) Thread.sleep(POLL_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            runCatching { scanner.stopScan(callback) }                          // BEFORE any connection: Android connects slowly while it scans
        }
        val tvs = synchronized(collector) { collector.candidates().mapNotNull { c -> devices[c.id]?.let { it to c } } }
        if (failed.get() && tvs.isEmpty()) return Scanned.Failed(Cause(Cause.Kind.BLE_SCAN_FAILED))
        return Scanned.Heard(tvs)
    }

    // ------------------------------------------------------------------ une TV : ouvrir le canal, lancer le cœur dessus, tout fermer

    /** One TV: opens the channel, runs the handshake of the core, hands the session to [then], closes everything. */
    private fun attempt(adapter: BluetoothAdapter, device: BluetoothDevice, c: BleSearch.Candidate, code: String, active: () -> Boolean, then: (BtActClient.Session) -> BleSearch.Attempt): BleSearch.Attempt {
        runCatching { adapter.cancelDiscovery() }
        val deadline = SystemClock.elapsedRealtime() + BleSearch.ATTEMPT_MS
        val tried = ArrayList<BleSearch.Channel>()
        val opened = open(device, c.psm, deadline, active, tried) ?: return BleSearch.Attempt.NoConnection(tried)
        val socket = opened.first; val channel = opened.second
        // whole attempt bounded, and dropped at once when the plan moves on: the socket is closed from here, which wakes a blocked read
        val guard = every(WATCH_MS) { if (!active() || SystemClock.elapsedRealtime() >= deadline) runCatching { socket.close() } }
        try {
            return when (val connect = BtActClient().connect(socket.inputStream, socket.outputStream, code)) {
                is BtActClient.Connect.Ready -> try { then(connect.session) } finally { connect.session.finish() }
                else -> BleSearch.attemptOf(connect, channel) ?: BleSearch.Attempt.Lost(channel)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return BleSearch.Attempt.Lost(channel)
        } catch (e: Exception) {
            return BleSearch.Attempt.Lost(channel)                                              // an IOException (link cut, socket closed by the watchdog) or anything unexpected: the TV is not joined
        } finally {
            guard?.cancel(false)
            runCatching { socket.close() }
        }
    }

    /** The channels in the order the core decides ([BleSearch.channels]): L2CAP then RFCOMM, or RFCOMM alone; the first that opens wins and is returned with its kind. [tried] collects the kinds attempted. */
    private fun open(device: BluetoothDevice, psm: Int, deadline: Long, active: () -> Boolean, tried: MutableList<BleSearch.Channel>): Pair<BluetoothSocket, BleSearch.Channel>? {
        for (channel in BleSearch.channels(Build.VERSION.SDK_INT, psm)) {
            if (!active() || SystemClock.elapsedRealtime() >= deadline) return null
            tried += channel
            val socket: BluetoothSocket? = when (channel) {
                BleSearch.Channel.L2CAP -> if (Build.VERSION.SDK_INT >= 29) try { device.createInsecureL2capChannel(psm) } catch (e: Exception) { null } else null
                BleSearch.Channel.RFCOMM -> try { device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.ACTIVATION_SERVICE_UUID)) } catch (e: Exception) { null }
            }
            if (socket != null) connect(device, socket, deadline)?.let { return it to channel }
        }
        return null
    }

    /** Connects [s], under the lock of this TV and a bound; null when it does not open (the socket is closed). */
    private fun connect(device: BluetoothDevice, s: BluetoothSocket, deadline: Long): BluetoothSocket? {
        val left = (deadline - SystemClock.elapsedRealtime()).coerceIn(1_000L, CONNECT_MS)
        val bound = later(left) { runCatching { s.close() } }
        try {
            synchronized(BtConnectLock.of(device.address)) { s.connect() }
            return s
        } catch (e: IOException) {
            runCatching { s.close() }
            return null
        } finally {
            bound?.cancel(false)
        }
    }

    private companion object {
        const val POLL_MS = 100L
        const val WATCH_MS = 200L
        /** One channel opening: the L2CAP then the RFCOMM share [BleSearch.ATTEMPT_MS], none takes more than this. */
        const val CONNECT_MS = 8_000L
    }
}
