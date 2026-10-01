package castbridge.sender

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import castbridge.core.lots.FileQueueStore
import castbridge.core.owner.OwnerFrames
import castbridge.core.policy.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The phone as MESSENGER of the server's deferred orders (docs/ORDRES.md § Téléphone). No screen, no notification, no setting: a background job fetches the signed orders of the paired TVs
 * when the phone has Internet, a second job hands them to the TV as soon as a link exists, and the TV's acknowledgements go back to the server at the next connection. The phone cannot read
 * (it never decodes for display), alter or invent an order: the signature is the TV's to check. All logic is in castbridge.core.policy (tested); this object only wires Android.
 * NOT COMPILED in the cloud (Android plugin unavailable): the coordinator compiles and tests on the phone and the TV.
 */
object OrdersRuntime {
    private const val TAG = "Orders"
    private const val JOB_SYNC = 4441
    private const val JOB_DELIVER = 4442
    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    lateinit var queue: OrderQueue; private set
    private val busy = AtomicBoolean(false)

    @Synchronized fun init(ctx: Context) {
        if (::queue.isInitialized) return
        app = ctx.applicationContext
        sp = app.getSharedPreferences("castbridge_orders", Context.MODE_PRIVATE)
        queue = OrderQueue(FileQueueStore(File(app.filesDir, "orders/queue.json")))
    }

    /** Device code of each TV paired with this phone, learnt from the TV's DEVICE_INFO frame ([OwnerFrames.parseDeviceInfo]); address (Bluetooth) → code. */
    fun learnTvCode(address: String, code: String) { sp.edit().putString("code:$address", code).apply() }
    private fun codeOf(address: String): String? = sp.getString("code:$address", null)
    private fun pairedCodes(): Map<String, String> = TvLinkManager.saved.list().mapNotNull { tv -> codeOf(tv.address)?.let { it to tv.address } }.toMap()

    /** With Internet: fetch the orders of the paired TVs, upload the pending acknowledgements. */
    fun syncNow(): Boolean {
        val st = PhoneConnect.state
        if (st.needsConsent || st.blocked || st.deviceToken.isNullOrBlank()) return false
        return OrderCourier(queue, HttpOrderServer(st.baseUrl, st.deviceToken!!)).syncServer(pairedCodes().keys)
    }

    /** With a link to a TV (Bluetooth now; the Wi-Fi tunnel only needs another [OrderLink]): hand over its orders, collect the acknowledgements. */
    fun deliverNow() {
        if (!busy.compareAndSet(false, true)) return
        try {
            for ((code, address) in pairedCodes()) {
                if (!queue.hasWork(code)) continue
                val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled } ?: return
                val sock = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(OwnerFrames.SERVICE_UUID))
                try {
                    sock.connect()
                    val r = OrderCourier(queue, object : OrderServerApi { override fun fetch(since: Long) = null; override fun postAcks(acks: List<PendingAck>) = false })
                        .deliver(code, StreamOrderLink(sock.inputStream, sock.outputStream))
                    Log.i(TAG, "livraison: ${r::class.simpleName}")        // never log tokens or codes
                } catch (e: Exception) { Log.w(TAG, "liaison: ${e.javaClass.simpleName}") } finally { runCatching { sock.close() } }
            }
        } finally { busy.set(false) }
    }

    /** Every 6 h with any network and battery not low (sync), every 30 min (delivery attempt: cheap when the queue is empty). Battery respected, system-managed. */
    fun schedule(ctx: Context) {
        init(ctx)
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        val persisted = ctx.checkSelfPermission(android.Manifest.permission.RECEIVE_BOOT_COMPLETED) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (js.getPendingJob(JOB_SYNC) == null) js.schedule(JobInfo.Builder(JOB_SYNC, ComponentName(ctx, OrdersSyncJob::class.java)).setPeriodic(6L * 3600_000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setRequiresBatteryNotLow(true).setPersisted(persisted).build())
        if (js.getPendingJob(JOB_DELIVER) == null) js.schedule(JobInfo.Builder(JOB_DELIVER, ComponentName(ctx, OrdersDeliverJob::class.java)).setPeriodic(30L * 60_000L)
            .setRequiresBatteryNotLow(true).setPersisted(persisted).build())
    }
}

class OrdersSyncJob : JobService() {
    @Volatile private var thread: Thread? = null
    override fun onStartJob(p: JobParameters): Boolean { thread = Thread { runCatching { OrdersRuntime.init(applicationContext); OrdersRuntime.syncNow() }; jobFinished(p, false) }.apply { start() }; return true }
    override fun onStopJob(p: JobParameters): Boolean { thread?.interrupt(); return true }
}

class OrdersDeliverJob : JobService() {
    @Volatile private var thread: Thread? = null
    override fun onStartJob(p: JobParameters): Boolean { thread = Thread { runCatching { OrdersRuntime.init(applicationContext); OrdersRuntime.deliverNow() }; jobFinished(p, false) }.apply { start() }; return true }
    override fun onStopJob(p: JobParameters): Boolean { thread?.interrupt(); return true }
}
