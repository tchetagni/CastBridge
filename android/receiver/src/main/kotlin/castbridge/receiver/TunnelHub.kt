package castbridge.receiver

import android.content.Context
import android.util.Log
import castbridge.core.net.HttpLite
import castbridge.core.tunnel.EnrollOutcome
import castbridge.core.tunnel.EnrollRequest
import castbridge.core.tunnel.Enrollment
import castbridge.core.tunnel.EnrollmentStore
import castbridge.core.tunnel.ExpertsStore
import castbridge.core.tunnel.ExpertsSync
import castbridge.core.tunnel.HostKeyPins
import castbridge.core.tunnel.TermsStore
import castbridge.core.tunnel.TunnelBackoff
import castbridge.core.tunnel.TunnelConnectivity
import castbridge.core.tunnel.TunnelEnroll
import castbridge.core.tunnel.TunnelEnv
import castbridge.core.tunnel.TunnelException
import castbridge.core.tunnel.TunnelJournal
import castbridge.core.tunnel.TunnelMachine
import castbridge.core.tunnel.TunnelPath
import castbridge.core.tunnel.TunnelSession
import castbridge.core.tunnel.TunnelState
import castbridge.core.tunnel.TunnelText
import castbridge.core.tunnel.TunnelTransport
import castbridge.sshd.TunnelSshClient
import castbridge.sshd.TvSshServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

/**
 * Remote administration of CastBridge-TV by its editor (docs/REMOTE-TUNNEL-TV.md, server side docs/REMOTE-TUNNEL.md): an SSH REVERSE tunnel from this TV to the server, one remote port per TV, silent in daily
 * use, readable on demand (« À propos > Assistance à distance »: the line, the local journal).
 *
 * This is the EDITOR's channel, NOT the user's SSH feature: [SshControl] (menu, API, Bluetooth, LAN, off by default, refused in the trial edition) is a different server, on its own port and its own
 * keys, and stays as it is. The tunnel has its own sshd instance ([TunnelSshd]) that listens on 127.0.0.1 only, accepts only the experts the owner signed, and exists only while the tunnel is up and the terms
 * were accepted: in the trial edition too.
 *
 * Gates (any closed = nothing starts, nothing is sent): terms accepted on this TV; TV not locked and a counting activation; Internet (the TV's own network, else the phone's Internet over Bluetooth through
 * CastBridge-TV's local SOCKS5 proxy: the SAME path as the TV's other server calls, [BtGatewayHost.proxy]). An offline TV makes no attempt and queues nothing.
 *
 * One worker thread: it sleeps (a monitor) until the next step the machine asked for, or until something changes (network, terms accepted, session closed). Idle cost: nothing while offline.
 */
object TunnelHub {
    private const val TAG = "CastBridgeTunnel"
    /**
     * Where the tunnel-only sshd of the TV listens (loopback). NOT 2222: that is the user's SSH feature ([TvSshServer.DEFAULT_PORT]) and both can run at once. NOT 2223 either (R-28, inventory I-8):
     * that is the SSH of the development app « CastBridge Dev » (DevService.PORT, all interfaces) on the same device, whichever started second could not listen. The server only knows the REMOTE port.
     */
    const val LOCAL_PORT = 2224

    private lateinit var app: Context
    @Volatile private var ready = false
    private lateinit var dir: File
    lateinit var terms: TermsStore; private set
    lateinit var journal: TunnelJournal; private set
    private lateinit var machine: TunnelMachine
    private lateinit var experts: ExpertsStore
    private lateinit var sync: ExpertsSync
    private lateinit var client: TunnelSshClient
    private lateinit var pins: HostKeyPins
    /** The home asked for the terms once during this process (« Plus tard » = asked again at the next start). */
    @Volatile var askedThisRun = false
    private val monitor = Object()
    @Volatile private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var sshd: TvSshServer? = null
    /** The path the current/last step used (for the phone-gateway proxy of the SSH sockets). */
    @Volatile private var lastPath = TunnelPath.OFFLINE

    @Synchronized fun init(ctx: Context) {
        if (ready) return
        app = ctx.applicationContext
        ActivationCenter.init(app)
        dir = File(app.filesDir, "tunnel").also { it.mkdirs() }
        terms = TermsStore(File(dir, "terms.json"), { ActivationCenter.deviceCode })
        journal = TunnelJournal(File(dir, "journal.log"))
        experts = ExpertsStore(File(dir, "experts")) { ActivationCenter.trustedKeys() }
        sync = ExpertsSync(experts, { ActivationCenter.trustedKeys() })
        client = TunnelSshClient(File(dir, "client"), { Log.i(TAG, it) })
        pins = HostKeyPins(File(dir, "host_keys"))
        machine = TunnelMachine(Env, Transport, EnrollmentStore(File(dir, "enrollment.json")), TunnelBackoff(), journal::add)
        machine.onWake { synchronized(monitor) { monitor.notifyAll() } }
        ready = true
    }

    /** Starts the worker (idempotent). Called by [TvService] once the TV is unlocked, so it also runs after a reboot (BootReceiver -> TvService). */
    @Synchronized fun start(ctx: Context) {
        init(ctx)
        if (running) { poke(); return }
        running = true
        worker = Thread({ loop() }, "cb-tunnel").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() {
        running = false
        synchronized(monitor) { monitor.notifyAll() }
        worker = null
        if (ready) runCatching { machine.shutdown() }
        stopSshd()
    }

    /** Something changed (network, terms, activation): run the next step now. */
    fun poke() { if (ready) machine.wake() }

    private fun loop() {
        while (running) {
            var wait = 60_000L
            try { wait = machine.step() } catch (t: Throwable) { Log.w(TAG, "step: ${t.javaClass.simpleName}") }
            try {
                synchronized(monitor) { if (running && wait > 0) monitor.wait(wait.coerceAtLeast(500L)) }
            } catch (_: InterruptedException) { return }
        }
    }

    // ---- terms (the activation screens and the home dialog call these) ----
    fun termsAccepted(ctx: Context): Boolean { init(ctx); return terms.accepted() }
    fun acceptTerms(ctx: Context): Boolean {
        init(ctx)
        return try { terms.accept(); journal.add("conditions d'usage acceptées (${castbridge.core.tunnel.TunnelTerms.VERSION})"); poke(); true }
        catch (e: Exception) { Log.w(TAG, "terms: ${e.javaClass.simpleName}"); false }
    }
    fun withdrawTerms(ctx: Context) { init(ctx); terms.withdraw(); journal.add("acceptation des conditions retirée"); poke() }

    // ---- what the screens show ----
    fun state(): TunnelState = if (ready) (if (!terms.accepted()) TunnelState.NEEDS_TERMS else machine.state) else TunnelState.NEEDS_TERMS
    /** « Assistance à distance : connectée / hors ligne / en attente d'acceptation des conditions ». */
    fun statusLine(ctx: Context): String { init(ctx); return TunnelText.line(state()) }
    fun detail(ctx: Context): String? { init(ctx); val retry = (machine.retryAt - System.currentTimeMillis()).coerceAtLeast(0); return TunnelText.detail(state(), machine.path, machine.lastError, retry) }
    fun journalLines(ctx: Context, max: Int = 60): List<String> { init(ctx); return journal.lines(max) }
    /** Key of the tunnel known to the server (never printed): is this TV enrolled? */
    fun enrolled(): Boolean = ready && machine.enrollment != null

    // ---- environment ----
    private object Env : TunnelEnv {
        override fun now() = System.currentTimeMillis()
        override fun termsAccepted() = terms.accepted()
        override fun locked() = ActivationCenter.locked()
        override fun activation(): String? = TunnelEnroll.pickActivation(ActivationCenter.allActivations(), ActivationCenter.now())?.encode()
        override fun connectivity(): TunnelPath {
            val svc = TvService.running ?: return TunnelPath.OFFLINE
            if (svc.netCheckedAt == 0L) return TunnelPath.OFFLINE                 // not probed yet: a few seconds after the start
            val gw = svc.gateway
            return TunnelConnectivity.choose(svc.netDirectMs != null, gw?.connected == true, svc.netGatewayMs != null).also { lastPath = it }
        }
        override fun keyId() = client.keyId()
    }

    private fun proxyFor(path: TunnelPath): Proxy? = if (path == TunnelPath.GATEWAY) TvService.running?.gateway?.proxy() else null

    private fun base(): String = TvConnect.link?.state?.baseUrl ?: BuildConfig.DEFAULT_SERVER

    private object Transport : TunnelTransport {
        override fun enroll(activation: String, path: TunnelPath): EnrollOutcome {
            val proxy = proxyFor(path)
            if (path == TunnelPath.GATEWAY && proxy == null) return EnrollOutcome.Retry("passerelle du téléphone indisponible")
            val actId = TunnelEnroll.activationId(activation); val keyId = client.keyId()
            return try {
                val r = HttpLite(proxy, connectTimeoutMs = 20_000, readTimeoutMs = 30_000, userAgent = "CastBridge-TV").request("POST", base() + "/api/v1/tunnel/enroll",
                    EnrollRequest(activation, client.publicKeyLine(), ActivationCenter.deviceCode).toJson())
                TunnelEnroll.outcome(r.code, r.body, actId, keyId)
            } catch (e: IOException) { EnrollOutcome.Retry("serveur injoignable (${e.javaClass.simpleName})") }
        }

        override fun open(e: Enrollment, path: TunnelPath, onClosed: () -> Unit): TunnelSession {
            val proxy = proxyFor(path)
            if (path == TunnelPath.GATEWAY && proxy == null) throw TunnelException("passerelle du téléphone indisponible")
            val server = startSshd()
            try {
                val dial: ((String, Int) -> Socket)? = proxy?.let { p -> { host, port -> Socket(p).also { s -> try { s.connect(InetSocketAddress.createUnresolved(host, port), 40_000) } catch (x: IOException) { runCatching { s.close() }; throw x } } } }
                val ssh = client.open(e, pins, LOCAL_PORT, dial, onClosed)
                return object : TunnelSession {
                    override fun alive() = ssh.alive()
                    override fun close() { runCatching { ssh.close() }; stopSshd() }
                }
            } catch (x: Throwable) { stopSshd(); throw x }
        }

        override fun refreshExperts(path: TunnelPath): ExpertsSync.Result {
            val proxy = proxyFor(path)
            val answer = try {
                HttpLite(proxy, connectTimeoutMs = 20_000, readTimeoutMs = 30_000, userAgent = "CastBridge-TV").request("GET", base() + "/api/v1/tunnel/experts").let { it.code to it.body }
            } catch (e: IOException) { null }
            return sync.apply(answer)
        }
    }

    // ---- the tunnel-only sshd ----
    @Synchronized private fun startSshd(): TvSshServer {
        sshd?.takeIf { it.running }?.let { return it }
        val s = TvSshServer(
            dataDir = File(dir, "sshd"), sftpRoot = app.getExternalFilesDir(null) ?: app.filesDir, port = LOCAL_PORT,
            // the editor's channel: loopback only, keys of the experts signed by the owner (never a password, never the user's authorized_keys), no idle stop
            bindHost = "127.0.0.1", idleStop = false,
            keyAuthority = { presented -> experts.keys(System.currentTimeMillis()).firstOrNull { it.keyBase64 == presented }?.id },
            onLogin = { id -> journal.add("expert « $id » : session ouverte") },
            log = { Log.i(TAG, it) },
        )
        s.start(); sshd = s
        return s
    }

    @Synchronized private fun stopSshd() { sshd?.let { runCatching { it.stop() } }; sshd = null }
}
