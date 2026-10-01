package castbridge.sender

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import castbridge.core.lots.*
import castbridge.core.net.HttpLite
import castbridge.core.trust.TvAuth
import castbridge.core.update.UpdateKeys
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The phone's side of the lots (docs/LOTS.md): up to 100 Mo of « Apprendre » / « Quiz » data downloaded from the server, then
 * delivered to the TV (10 Mo) whenever the two can talk. Offline-first:
 *  - the phone syncs with the server whenever IT has Internet (a light periodic job, unmetered network by default) and never needs
 *    the TV to be there;
 *  - the delivery to the TV is DEFERRED: [LotDelivery] drains the persistent per-TV queue as soon as a link exists (Bluetooth ACL
 *    connected, the TV seen on the network, the app open), resuming from the last offset the TV confirmed, without opening the app;
 *  - nothing here blocks the user, and no lot is ever fetched by the TV from the Internet.
 * All logic lives in castbridge.core.lots (tested); this object only wires Android (files, network, jobs, the saved TV).
 */
object LotsRuntime {
    private const val TAG = "Lots"
    private const val JOB_SYNC = 4431
    private const val JOB_DELIVER = 4432
    private const val JOB_DELIVER_PERIODIC = 4433

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    lateinit var appContext: Context; private set
    lateinit var store: LotStore; private set
    lateinit var queue: DeliveryQueue; private set

    /** What the screen shows while something runs: (lot, bytes done, total), null when idle. */
    @Volatile var progress: Triple<LotId, Long, Long>? = null; private set
    @Volatile var running: String? = null; private set

    private val syncing = AtomicBoolean(false)
    private val delivering = AtomicBoolean(false)

    /** Called by CastBridgeApp.onCreate (jobs and receivers run in the same app process, so it has always run before them). */
    @Synchronized fun init(ctx: Context) {
        if (::store.isInitialized) return
        app = ctx.applicationContext
        appContext = app
        sp = app.getSharedPreferences("castbridge_lots", Context.MODE_PRIVATE)
        store = LotStore(File(app.filesDir, "lots"))
        queue = DeliveryQueue(FileQueueStore(File(app.filesDir, "lots/delivery-queue.json")))
        TvLinkManager.init(app)
    }

    // ---- settings of the screen ----
    var wifiOnly: Boolean get() = sp.getBoolean("wifi_only", true); set(v) { sp.edit().putBoolean("wifi_only", v).apply(); PhoneConnect.changed() }
    /** The classes/levels the user keeps (e.g. "cm2", "3e"): protected on the phone, first in line for the TV. */
    var selectedScopes: Set<String> get() = sp.getStringSet("scopes", emptySet()).orEmpty(); set(v) { sp.edit().putStringSet("scopes", v.toSet()).apply(); PhoneConnect.changed(); enqueueDefaultTv() }
    var lastSyncAt: Long get() = sp.getLong("sync_at", 0); private set(v) { sp.edit().putLong("sync_at", v).apply() }
    var lastSyncMessage: String? get() = sp.getString("sync_msg", null); private set(v) { sp.edit().putString("sync_msg", v).apply() }
    /** The last verified catalog (to list the classes offline). */
    val catalog: LotManifest? get() = sp.getString("catalog", null)?.let { runCatching { LotManifest.parse(it) }.getOrNull() }
    val firstSyncDone: Boolean get() = sp.getBoolean("first_sync", false)

    fun needs(): List<Need> = LotPlanner.needsOf(listOf(ProfileNeed(selectedScopes.sorted(), active = true)))
    fun protect(id: LotId) = id.scope in selectedScopes

    /** Total size of the lots of [scopes] announced by the last catalog (the wizard's estimate). */
    fun estimate(scopes: Set<String>): Long = catalog?.lots?.filter { it.id.scope in scopes }?.sumOf { it.bytes } ?: 0L

    // ---- phone <-> server ----

    private fun keys() = UpdateKeys.PUBLIC_KEYS + listOf(BuildConfig.EXTRA_UPDATE_KEY).filter { it.isNotBlank() }

    private fun net(): Net {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return Net.NONE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return Net.NONE) ?: return Net.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return Net.NONE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) Net.UNMETERED else Net.METERED
    }

    private fun transferToTvRunning(): Boolean = UploadService.state.value.let { it is UploadService.State.Uploading || it is UploadService.State.Waiting } ||
        BtUploadService.state.value.let { it is castbridge.core.tv.ResumableUpload.State.Uploading || it is castbridge.core.tv.ResumableUpload.State.Waiting }

    /**
     * Synchronises with the server. [only] = one lot (« Mettre à jour » of a row), null = the selected classes + what is already
     * on the phone (first synchronisation: the selection; later: only what changed). Returns the French message of the outcome.
     */
    fun syncNow(only: LotId? = null, userAsked: Boolean = false): String {
        val st = PhoneConnect.state
        if (st.needsConsent) return "Acceptez d'abord l'information de confidentialité pour télécharger des données."
        if (st.blocked) return "Cet appareil est bloqué par l'administrateur."
        if (!userAsked && transferToTvRunning()) return "Un envoi vers la TV est en cours : la mise à jour attendra."
        if (!syncing.compareAndSet(false, true)) return "Une mise à jour est déjà en cours."
        running = "sync"; PhoneConnect.changed()
        try {
            val remote = HttpLotRemote(st.baseUrl, deviceToken = st.deviceToken)
            val sync = LotSync(store, remote, keys(), PhoneConnect.versionCode, ::net)
            val wanted = if (only != null) listOf(only)
                else (needs().map { it.id } + store.list().map { it.meta.id }).distinct().ifEmpty { selectedScopes.flatMap { s -> listOf(LotId("learn", s), LotId("quiz", s)) } }
            // the catalog also answers "which classes exist" for the wizard: ask for it even when nothing is wanted yet
            val rep = sync.sync(wanted, ::protect, LotSync.Options(channel = st.channel, wifiOnly = wifiOnly),
                progress = { id, d, t -> progress = Triple(id, d, t); PhoneConnect.changed() }, cancelled = { !userAsked && transferToTvRunning() })
            sync.lastCatalogJson()?.let { sp.edit().putString("catalog", it).apply() }
            if (rep.blocked == null) { sp.edit().putBoolean("first_sync", true).apply(); lastSyncAt = System.currentTimeMillis() }
            val msg = rep.blocked ?: summary(rep)
            lastSyncMessage = msg
            enqueueDefaultTv()
            if (rep.results.any { it.outcome == LotSync.Outcome.INSTALLED || it.outcome == LotSync.Outcome.UPDATED }) requestDelivery(app)
            return msg
        } catch (e: Exception) {
            Log.w(TAG, "sync: ${e.javaClass.simpleName}")                       // never log URLs or tokens
            return "Mise à jour impossible : ${e.message ?: e.javaClass.simpleName}"
        } finally {
            progress = null; running = null; syncing.set(false); PhoneConnect.changed()
        }
    }

    /** Fetches only the catalog (wizard: which classes exist), no lot. */
    fun refreshCatalog(): String? {
        val st = PhoneConnect.state
        if (st.needsConsent || st.blocked) return "Téléchargement indisponible"
        val sync = LotSync(store, HttpLotRemote(st.baseUrl, deviceToken = st.deviceToken), keys(), PhoneConnect.versionCode, ::net)
        val (cat, why) = sync.fetchCatalog(LotSync.Options(channel = st.channel, wifiOnly = wifiOnly))
        if (cat != null) sync.lastCatalogJson()?.let { sp.edit().putString("catalog", it).apply() }
        PhoneConnect.changed()
        return why
    }

    private fun summary(r: LotSync.Report): String {
        val got = r.results.count { it.outcome == LotSync.Outcome.INSTALLED || it.outcome == LotSync.Outcome.UPDATED }
        val failed = r.results.firstOrNull { it.outcome == LotSync.Outcome.FAILED || it.outcome == LotSync.Outcome.SKIPPED_FULL || it.outcome == LotSync.Outcome.SKIPPED_APP_TOO_OLD }
        return when {
            failed != null -> "${failed.message.ifBlank { "une donnée n'a pas pu être téléchargée" }} (${got} téléchargée(s))"
            got > 0 -> "$got donnée(s) téléchargée(s)"
            else -> "Tout est à jour"
        }
    }

    // ---- phone -> TV (deferred delivery) ----

    private fun tvId(): String? = TvLinkManager.saved.default()?.address

    private fun delivery() = LotDelivery(queue, store) { needs() }

    /** Refreshes the queue of the default TV from what the phone holds (no contact needed). */
    fun enqueueDefaultTv() { if (!::store.isInitialized) return; tvId()?.let { runCatching { delivery().enqueue(it) } }; PhoneConnect.changed() }

    /** Is the default TV in range right now (a session is open, or it answers on the network)? */
    fun tvReachable(): Boolean = TvLinkManager.state.value is LinkUi.Connected

    /** The best link to the default TV right now: Wi-Fi (HTTP) if it has an address, else Bluetooth file transfer (CBT1), else null. */
    private fun transport(): LotTransport? {
        val tv = TvLinkManager.saved.default() ?: return null
        val session = (TvLinkManager.state.value as? LinkUi.Connected)?.session?.takeIf { it.tv.address == tv.address }
        val cred = session?.credential ?: TvLinkManager.credentialFor("bt:${tv.address}")
        val base = session?.base ?: tv.lastIps.firstOrNull { TvLinkManager.reachable("http://$it:${tv.port}") }?.let { "http://$it:${tv.port}" }
        if (base != null && cred != null) return HttpLotTransport(base, cred, label = session?.route?.label ?: "Wi-Fi")
        return Cbt1LotTransport(TvAuth.btPin(cred), { AndroidBtTransport(app).connect(tv.address) })    // a trusted phone needs no PIN over the paired link
    }

    /** Drains the queue of the default TV once; safe to call from anywhere (jobs, receivers, the screen); never blocks the UI thread's caller for long if run on a worker. */
    fun deliverNow(userAsked: Boolean = false): String? {
        val id = tvId() ?: return "Aucune TV enregistrée"
        if (!userAsked && !queue.hasWork(id)) return null
        if (transferToTvRunning()) return "Un envoi de fichier vers la TV est en cours : les données suivront."
        if (!delivering.compareAndSet(false, true)) return null
        running = "delivery"; PhoneConnect.changed()
        try {
            val t = transport() ?: run { enqueueDefaultTv(); return "La TV n'est pas à portée : les données seront envoyées dès qu'elle sera allumée à côté du téléphone." }
            val rep = delivery().deliver(id, t, cancelled = { transferToTvRunning() })
            return when {
                !rep.reachable -> "La TV n'est pas à portée : ${rep.pending} envoi(s) en attente."
                rep.refused.isNotEmpty() -> "La TV a refusé : ${rep.refused.first().second}"
                rep.sent.isNotEmpty() -> "${rep.sent.size} donnée(s) envoyée(s) à la TV"
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "delivery: ${e.javaClass.simpleName}")
            return "Envoi à la TV interrompu : il reprendra au prochain contact."
        } finally {
            running = null; delivering.set(false); PhoneConnect.changed()
        }
    }

    fun status(id: LotId): LotStatus {
        val tv = tvId() ?: ""
        return LotStatusText.of(id, store, queue, tv, tvReachable(), System.currentTimeMillis())
    }

    fun tvBudgetText(): String = LotStatusText.tvBudget(tvId() ?: "", queue, System.currentTimeMillis())

    fun skipped(): List<LotPlanner.Skipped> = tvId()?.let { runCatching { delivery().enqueue(it).skipped }.getOrDefault(emptyList()) } ?: emptyList()

    /** « Réessayer » on a lot the TV refused: back in the queue, delivered at the next contact. */
    fun retry(id: LotId) { tvId()?.let { queue.retry(it, id) }; requestDelivery(app); PhoneConnect.changed() }

    /** The user accepts to drop [id] from the TV's plan to make room for a skipped lot: it is no longer wanted for the TV. */
    fun dropFromTv(id: LotId) {
        val tv = tvId() ?: return
        queue.cancel(tv, id)
        Thread { runCatching { (transport() as? HttpLotTransport)?.remove(id) } }.start()
        PhoneConnect.changed()
    }

    // ---- scheduling (JobScheduler, like ParentalInbox: no extra dependency) ----

    /** Periodic sync (every 12 h, on an unmetered network unless the user allowed mobile data, battery not low), and a periodic delivery check. */
    fun schedule(ctx: Context) {
        init(ctx)
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        val persisted = ctx.checkSelfPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val netType = if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY
        if (js.getPendingJob(JOB_SYNC)?.let { it.networkType == netType } != true)
            js.schedule(JobInfo.Builder(JOB_SYNC, ComponentName(ctx, LotsSyncJob::class.java)).setPeriodic(12L * 3600_000L)
                .setRequiredNetworkType(netType).setRequiresBatteryNotLow(true).setPersisted(persisted).build())
        if (js.getPendingJob(JOB_DELIVER_PERIODIC) == null)
            js.schedule(JobInfo.Builder(JOB_DELIVER_PERIODIC, ComponentName(ctx, LotsDeliverJob::class.java)).setPeriodic(30L * 60_000L)
                .setRequiresBatteryNotLow(true).setPersisted(persisted).build())
    }

    /** A delivery attempt as soon as possible (the TV just became reachable, or a lot was just downloaded). */
    fun requestDelivery(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        runCatching { js.schedule(JobInfo.Builder(JOB_DELIVER, ComponentName(ctx, LotsDeliverJob::class.java)).setOverrideDeadline(1_000).build()) }
    }
}

/** Light periodic synchronisation with the server (see [LotsRuntime.schedule]). */
class LotsSyncJob : JobService() {
    @Volatile private var thread: Thread? = null
    override fun onStartJob(p: JobParameters): Boolean {
        thread = Thread { runCatching { LotsRuntime.init(applicationContext); LotsRuntime.syncNow() }; jobFinished(p, false) }.apply { start() }
        return true
    }
    override fun onStopJob(p: JobParameters): Boolean { thread?.interrupt(); return true }       // the next period retries; partial downloads resume
}

/** Delivery of the queued lots to the TV, when it is reachable (periodic check, or requested by a trigger). */
class LotsDeliverJob : JobService() {
    @Volatile private var thread: Thread? = null
    override fun onStartJob(p: JobParameters): Boolean {
        thread = Thread { runCatching { LotsRuntime.init(applicationContext); LotsRuntime.deliverNow() }; jobFinished(p, false) }.apply { start() }
        return true
    }
    override fun onStopJob(p: JobParameters): Boolean { thread?.interrupt(); return true }
}

/** Wakes the delivery when the TV's Bluetooth link comes up (ACL connected), without opening the app. Not exported: only the system sends it. */
class LotsTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED || i.action == Intent.ACTION_BOOT_COMPLETED) {
            LotsRuntime.init(c)
            if (i.action == Intent.ACTION_BOOT_COMPLETED) LotsRuntime.schedule(c)
            val tv = TvLinkManager.saved.default()
            val dev = @Suppress("DEPRECATION") i.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
            if (i.action == Intent.ACTION_BOOT_COMPLETED || (tv != null && dev?.address.equals(tv.address, ignoreCase = true))) LotsRuntime.requestDelivery(c)
        }
    }
}
