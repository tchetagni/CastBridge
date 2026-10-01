package castbridge.sender

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelUuid
import castbridge.core.trust.BondState
import castbridge.core.trust.BtUnavailable
import castbridge.core.trust.DiagEnv
import castbridge.core.trust.LinkEnv
import castbridge.core.trust.LinkStore
import castbridge.core.trust.PairEnv
import castbridge.core.trust.StoredCredential
import castbridge.core.trust.TokenCheck
import castbridge.core.trust.Trigger
import castbridge.core.trust.TrustRegistry
import castbridge.core.trust.TvCandidate
import castbridge.core.trust.TvCredential
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Registers for a protected system broadcast the way the rest of the app does (Android 13+ wants an explicit flag). */
fun registerSystemReceiver(ctx: Context, r: BroadcastReceiver, f: IntentFilter) {
    if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else ctx.registerReceiver(r, f)
}

/** The phone's answers to the link driver's questions (core/.../LinkDriver.kt): Bluetooth, bond, network, probes. */
@SuppressLint("MissingPermission")
class AndroidLinkEnv(private val ctx: Context, private val foregroundNow: () -> Boolean) : LinkEnv {
    private fun adapter(): BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    override fun now() = System.currentTimeMillis()

    override fun btProblem(): BtUnavailable.Reason? {
        val ad = adapter() ?: return BtUnavailable.Reason.NO_ADAPTER
        if (!hasBtPermission(ctx)) return BtUnavailable.Reason.NO_PERMISSION
        return if (runCatching { ad.isEnabled }.getOrDefault(false)) null else BtUnavailable.Reason.OFF
    }

    override fun bond(address: String): BondState = runCatching {
        when (adapter()?.getRemoteDevice(address)?.bondState) {
            BluetoothDevice.BOND_BONDED -> BondState.BONDED
            BluetoothDevice.BOND_BONDING -> BondState.BONDING
            else -> BondState.NONE
        }
    }.getOrDefault(BondState.NONE)

    override fun networkUp(): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }.getOrDefault(false)

    /** Does this address answer like a CastBridge TV? (short timeout: the phone may simply be on another network). */
    override fun probe(base: String): Boolean = runCatching {
        val c = URL("$base/api/hello").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 1200; c.readTimeout = 1200
            c.responseCode == 200 && c.inputStream.use { it.readBytes() }.decodeToString().contains("castbridge-tv")
        } finally { c.disconnect() }
    }.getOrDefault(false)

    /** GET /api/info with the token: any answer but "bad token" means the TV is there and accepts it (a bad token is not a wrong PIN: nothing is counted). */
    override fun check(base: String, token: String): TokenCheck = try {
        val c = URL("$base/api/info").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 1500; c.readTimeout = 1500
            TvCredential.apply(c, token)
            val code = c.responseCode
            if (code == 401 && "bad token" in (c.errorStream?.use { it.readBytes() }?.decodeToString().orEmpty())) TokenCheck.TOKEN_REJECTED else TokenCheck.OK
        } finally { c.disconnect() }
    } catch (e: IOException) { TokenCheck.UNREACHABLE }

    override fun foreground() = foregroundNow()

    override fun bondedTvs(): List<TvCandidate> = runCatching {
        val cbt = ParcelUuid(UUID.fromString(BtProtocol.SERVICE_UUID))
        adapter()?.bondedDevices.orEmpty()
            .map { d -> TvCandidate(d.address, runCatching { d.name }.getOrNull().orEmpty(), true, d.uuids?.any { it == cbt }) }
            .filter { it.hasCbt1 == true }
    }.getOrDefault(emptyList())
}

/** What survives the process dying or a reboot: the state machine's last state and the token per TV (private prefs, excluded from backups). */
class PrefsLinkStore(private val sp: SharedPreferences) : LinkStore {
    override fun loadModel(): String? = sp.getString("link.model", null)
    override fun saveModel(text: String) { sp.edit().putString("link.model", text).apply() }

    override fun loadCredential(address: String): StoredCredential? {
        val f = sp.getString("cred.${TrustRegistry.norm(address)}", null)?.split('|') ?: return null
        // "token|expiresAt" (older versions) or "token|issuedAt|expiresAt"
        return when (f.size) {
            2 -> StoredCredential(f[0], 0L, f[1].toLongOrNull() ?: return null)
            3 -> StoredCredential(f[0], f[1].toLongOrNull() ?: return null, f[2].toLongOrNull() ?: return null)
            else -> null
        }
    }

    override fun saveCredential(address: String, c: StoredCredential) {
        sp.edit().putString("cred.${TrustRegistry.norm(address)}", "${c.token}|${c.issuedAt}|${c.expiresAt}").apply()
    }

    override fun clearCredential(address: String) { sp.edit().remove("cred.${TrustRegistry.norm(address)}").apply() }
}

/** The bond side of « Ajouter ma TV »: create the bond, and wait for a bond state to be reached (broadcast + polling, so a missed broadcast never hangs). */
@SuppressLint("MissingPermission")
class AndroidPairEnv(private val ctx: Context, private val env: AndroidLinkEnv) : PairEnv {
    override fun btProblem() = env.btProblem()
    override fun bond(address: String) = env.bond(address)

    override fun createBond(address: String): Boolean = runCatching {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.apply { cancelDiscovery() }?.getRemoteDevice(address)?.createBond() == true
    }.getOrDefault(false)

    override fun awaitBond(address: String, target: BondState, timeoutMs: Long): BondState {
        if (env.bond(address) == target) return target
        val latch = CountDownLatch(1)
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { latch.countDown() } }
        registerSystemReceiver(ctx, r, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        try {
            val end = System.currentTimeMillis() + timeoutMs
            var sawBonding = false
            while (System.currentTimeMillis() < end) {
                val s = env.bond(address)
                if (s == target) break
                if (s == BondState.BONDING) sawBonding = true
                if (target == BondState.BONDED && sawBonding && s == BondState.NONE) break      // the user cancelled Android's pairing dialog
                latch.await(1000, TimeUnit.MILLISECONDS)
            }
        } finally { runCatching { ctx.unregisterReceiver(r) } }
        return env.bond(address)
    }

    override fun sleep(ms: Long) { Thread.sleep(ms) }
    override fun now() = System.currentTimeMillis()
}

/** « Diagnostic Bluetooth »: the same questions, asked one by one (core/.../Diagnostics.kt). */
@SuppressLint("MissingPermission")
class AndroidDiagEnv(private val ctx: Context, private val env: AndroidLinkEnv, private val transport: AndroidBtTransport) : DiagEnv {
    override fun btProblem() = env.btProblem()
    override fun bond(address: String) = env.bond(address)
    override fun hasCbt1Service(address: String): Boolean? = runCatching {
        val cbt = ParcelUuid(UUID.fromString(BtProtocol.SERVICE_UUID))
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address)?.uuids?.let { u -> u.any { it == cbt } }
    }.getOrNull()
    override fun connect(address: String): Link = transport.connect(address)
    override fun probe(base: String) = env.probe(base)
    override fun canJoinWifiDirect() = Build.VERSION.SDK_INT >= 29
    override fun now() = System.currentTimeMillis()
    override fun appVersion(): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
}

/**
 * Reconnects without the app in front: a periodic job (every ~15 min, survives a reboot, no wake lock: the system holds the CPU only while it runs)
 * and a one-shot job started by the Bluetooth broadcasts of [LinkWakeReceiver]. Both only run one [castbridge.core.trust.LinkDriver] step.
 */
class LinkJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        TvLinkManager.init(this)
        Thread({
            try { TvLinkManager.stepOnce(Trigger.WORKER) } catch (_: Exception) {}
            jobFinished(params, false)
        }, "cb-link-job").apply { isDaemon = true; start() }
        return true
    }

    override fun onStopJob(params: JobParameters) = false

    companion object {
        private const val PERIODIC_ID = 7101
        private const val ONE_SHOT_ID = 7102

        /** Idempotent: re-scheduling the same periodic job keeps its place. */
        fun schedulePeriodic(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            if (js.allPendingJobs.any { it.id == PERIODIC_ID }) return
            runCatching {
                js.schedule(JobInfo.Builder(PERIODIC_ID, ComponentName(ctx, LinkJobService::class.java))
                    .setPeriodic(15 * 60_000L).setPersisted(true).setRequiresBatteryNotLow(true).build())
            }
        }

        /** A Bluetooth event woke the process in the background: one step soon, not a foreground service. */
        fun scheduleOnce(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            runCatching {
                js.schedule(JobInfo.Builder(ONE_SHOT_ID, ComponentName(ctx, LinkJobService::class.java)).setMinimumLatency(1_000).setOverrideDeadline(30_000).build())
            }
        }
    }
}

/** Manifest-registered, not exported: system broadcasts about the Bluetooth link of a phone that has a TV saved start one quick job. */
class LinkWakeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val app = c.applicationContext
        TvLinkManager.init(app)
        if (TvLinkManager.saved.list().isEmpty()) return
        LinkJobService.scheduleOnce(app)
    }
}
