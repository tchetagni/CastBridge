package castbridge.sender

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import castbridge.core.link.BulkLine
import castbridge.core.link.BulkRoute
import castbridge.core.link.JoinMethod
import castbridge.core.link.StateLine
import castbridge.core.link.WdBackoff
import castbridge.core.link.WdClient
import castbridge.core.link.WdJoin
import castbridge.core.link.WdPermission
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.LinkPlanner
import castbridge.core.tv.WifiDirect
import castbridge.core.trust.LinkSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import castbridge.core.link.HelloIps
import castbridge.core.link.WdDiagnostic
import castbridge.core.link.WdManualLease
import castbridge.core.link.WdManualView
import castbridge.core.trust.SavedTv
import castbridge.core.trust.TvAuth
import castbridge.core.tunnel.TunnelJournal
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * « Seul le Bluetooth : Wi-Fi Direct automatique » (docs/agent-reports/auto-wifi-direct.md, R-14), l'exécutant Android, mince. Tout ce qui décide est dans
 * core ([BulkRoute.decide], [WdClient.reduce], [WdBackoff]) ; ici : demander le groupe à la TV par le lien Bluetooth appairé (CBTN, mot de passe frais
 * reçu chiffré, gardé en mémoire le temps de la jonction), le rejoindre ([WdJoin.method] : `WifiP2pManager.connect` sans boîte sur Android 13+,
 * `WifiNetworkSpecifier` sur 10-12), sonder `GET /api/hello`, donner l'adresse à la file d'envoi, rendre le groupe après 30 s de file vide.
 * Le Bluetooth reste la voie de contrôle et de repli. Rien n'est écrit sur disque sauf deux préférences (réglage, « déjà demandé »).
 */
@SuppressLint("MissingPermission")
object AutoWifiDirect {
    private const val TAG = "AutoWifiDirect"
    private const val PREFS = "castbridge_transfer"
    private const val KEY_AUTO = "auto_wifi_direct"
    private const val KEY_PERM_ASKED = "wd_perm_asked"
    private const val KEY_WIFI_ASKED = "wd_wifi_asked"
    private const val ASK_WAIT_MS = 30_000L
    private const val TICK_MS = 2_000L
    /** Une cause de la TV qui ne change pas d'elle-même (Wi-Fi éteint, essai, refus) est retenue 10 minutes. */
    private const val TV_ERR_MS = 10 * 60_000L

    /** Réglage « Wi-Fi Direct automatique (si seul le Bluetooth est disponible) », activé par défaut. */
    fun enabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true)
    fun set(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO, on).apply()
        if (!on) release()
    }

    private val _state = MutableStateFlow<WdClient.State>(WdClient.State.Off)
    val state: StateFlow<WdClient.State> = _state
    private val _line = MutableStateFlow<StateLine?>(null)
    /** La ligne d'état honnête de l'envoi en cours (null = rien à dire). */
    val line: StateFlow<StateLine?> = _line
    private val _ask = MutableStateFlow<BulkRoute.Ask?>(null)
    /** Une demande à l'usager, juste à temps, une fois : [AutoWifiDirectAsker] la présente (l'app est devant). */
    val ask: StateFlow<BulkRoute.Ask?> = _ask

    private val _manual = MutableStateFlow(false)
    /** La session en cours est celle du bouton « Wi-Fi Direct » (manuelle) : elle reste jusqu'à l'arrêt ou 10 minutes hors de l'écran ([WdManualLease]). */
    val manual: StateFlow<Boolean> = _manual
    private val _report = MutableStateFlow<String?>(null)
    /** Le relevé de terrain de la dernière session manuelle ([WdDiagnostic.format]) : ni mot de passe ni code. null = pas encore. */
    val report: StateFlow<String?> = _report
    private val _measuring = MutableStateFlow(false)
    val measuring: StateFlow<Boolean> = _measuring
    @Volatile private var lease = WdManualLease()
    @Volatile private var screenOn = false
    @Volatile private var manualStartedAt = 0L
    @Volatile private var hadLanBefore: Boolean? = null
    @Volatile private var diagSsid: String? = null
    @Volatile private var diagDone = false
    private var journal: TunnelJournal? = null

    private lateinit var app: Context
    /** Le fil de l'automate : chaque événement est réduit ici, dans l'ordre. */
    private val machine = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "auto-wd").apply { isDaemon = true } }
    /** Le travail qui bloque (connexion Bluetooth, sonde HTTP) : jamais sur le fil de l'automate. */
    private val io = Executors.newCachedThreadPool { r -> Thread(r, "auto-wd-io").apply { isDaemon = true } }
    @Volatile private var backoff = WdBackoff()
    @Volatile private var lastDecision: BulkRoute.Decision? = null
    @Volatile private var lastLostAt = 0L
    @Volatile private var tvErr: String? = null
    @Volatile private var tvErrAt = 0L
    @Volatile private var askingPermission = false
    /** La TV à qui le groupe a été demandé, et le titre pour le CBTN (en mémoire seulement). */
    @Volatile private var target: Pair<String, String>? = null
    @Volatile private var ticking = false
    // jonction en cours ou établie
    private var p2p: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var specCb: ConnectivityManager.NetworkCallback? = null
    @Volatile private var method = JoinMethod.NONE

    /** Horloge MONOTONE (repli de 10 minutes, délais de l'automate) : un changement d'heure du téléphone ne change rien. */
    fun clock(): Long = android.os.SystemClock.elapsedRealtime()
    private fun now() = clock()

    fun permission(ctx: Context): WdPermission {
        val p = WdJoin.permission(Build.VERSION.SDK_INT) ?: return WdPermission.NOT_NEEDED
        return when {
            ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED -> WdPermission.GRANTED
            askingPermission -> WdPermission.ASKING
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PERM_ASKED, false) -> WdPermission.DENIED
            else -> WdPermission.NOT_ASKED
        }
    }

    /** Résultat de la demande (une fois) : refusée = Bluetooth avec explication, jamais redemandée automatiquement. */
    fun onPermissionResult(ctx: Context, granted: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PERM_ASKED, true).apply()
        askingPermission = false; _ask.value = null
        Log.i(TAG, "permission Wi-Fi Direct : ${if (granted) "accordée" else "refusée"}")
    }

    fun onWifiAsked(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WIFI_ASKED, true).apply()
        _ask.value = null
    }

    private fun wifiOn(ctx: Context) = runCatching { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled }.getOrDefault(false)

    /** Le téléphone est-il connecté à un Wi-Fi (STA) ? Sur Android 10-12, le spécificateur peut le lui faire quitter. */
    private fun onWifi(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    /** Android 10-12 et Wi-Fi connecté : deux sondes LAN espacées avant de quitter ce Wi-Fi ([BulkRoute.lanConfirmedDead], audit I-3). */
    private suspend fun lanConfirmedDead(s: LinkSession): Boolean {
        val bases = HelloIps.lanOnly(s.info.link.ips).map { "http://$it:${s.info.link.port}" }
        if (bases.isEmpty()) return true                                   // the TV has no LAN address at all: nothing to lose
        val probes = mutableListOf<Pair<Long, Boolean>>()
        repeat(2) { i ->
            if (i > 0) delay(BulkRoute.LAN_PROBE_GAP_MS)
            probes += now() to bases.any { TvLinkManager.reachable(it) }
        }
        return BulkRoute.lanConfirmedDead(probes)
    }

    private fun facts(ctx: Context, s: LinkSession, bytes: Long, lanDead: Boolean) = BulkRoute.Facts(
        bytes = bytes,
        lanAlive = BulkRoute.lanRoute(s.route),              // never a 192.168.49.x « LAN » (audit I-1)
        btConnected = true,                                  // a session exists: the Bluetooth control link answered
        api = Build.VERSION.SDK_INT,
        permission = permission(ctx),
        now = now(),
        phoneOnWifi = onWifi(ctx),
        lanDeadConfirmed = lanDead,
        phoneWifiOn = wifiOn(ctx),
        tvOffersWd = s.info.link.wdCap,
        tvWdError = tvErr?.takeIf { now() - tvErrAt < TV_ERR_MS },
        autoWifiDirect = enabled(ctx),
        wdUp = _state.value is WdClient.State.Up,
        backoffUntil = backoff.until,
        wifiAskedOnce = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIFI_ASKED, false),
        foreground = TvLinkManager.foreground,
    )

    private fun publishLine() { lastDecision?.let { _line.value = BulkLine.of(it, _state.value) } }

    /**
     * Avant un envoi en masse sur le lien de confiance : l'adresse HTTP de la TV dans le groupe Wi-Fi Direct (monté s'il le faut, sans action de l'usager
     * dans le cas normal), ou null = la voie de la session (LAN, passerelle Bluetooth, CBT1). Ne bloque jamais plus d'environ une minute.
     */
    suspend fun bulkBase(ctx: Context, s: LinkSession, bytes: Long): String? {
        app = ctx.applicationContext
        val specifierLeavesWifi = WdJoin.method(Build.VERSION.SDK_INT) == JoinMethod.NETWORK_SPECIFIER && onWifi(app) && TvLinkManager.foreground &&
            !BulkRoute.lanRoute(s.route) && bytes >= BulkRoute.MIN_WD_BYTES && enabled(app)
        val lanDead = if (specifierLeavesWifi) lanConfirmedDead(s) else false
        fun f() = facts(app, s, bytes, lanDead)
        var d = BulkRoute.decide(f())
        if (d is BulkRoute.Decision.AskOnce) {
            if (d.what == BulkRoute.Ask.PERMISSION) askingPermission = true
            lastDecision = d; publishLine()
            _ask.value = d.what
            withTimeoutOrNull(ASK_WAIT_MS) { _ask.first { it == null } }
            if (_ask.value != null) { _ask.value = null; askingPermission = false }
            // the Wi-Fi panel: a moment for the radio to come up
            if (d.what == BulkRoute.Ask.PHONE_WIFI) withTimeoutOrNull(10_000) { while (!wifiOn(app)) delay(500) }
            d = BulkRoute.decide(f())
        }
        if (d is BulkRoute.Decision.Wait || d is BulkRoute.Decision.AskOnce) {
            withTimeoutOrNull(ASK_WAIT_MS) { while (askingPermission) delay(500) }
            d = BulkRoute.decide(f()).let { if (it is BulkRoute.Decision.Wait || it is BulkRoute.Decision.AskOnce) BulkRoute.Decision.UseBt(BulkRoute.Why.PERMISSION_PENDING) else it }
        }
        lastDecision = d; publishLine()
        Log.i(TAG, "voie de masse : $d")
        return when (d) {
            is BulkRoute.Decision.UseWd -> {
                if (!d.start) (_state.value as? WdClient.State.Up)?.base
                else {
                    target = s.tv.address to s.credential
                    method = WdJoin.method(Build.VERSION.SDK_INT)
                    machine.submit { step(WdClient.Event.Start(now(), method)) }.get()
                    ensureTicking()
                    val end = withTimeoutOrNull(WdClient.REQUEST_TIMEOUT_MS + WdJoin.joinTimeoutMs(method) + 15_000) {
                        _state.first { it is WdClient.State.Up || it is WdClient.State.Failed || it == WdClient.State.Off }
                    }
                    if (end !is WdClient.State.Up) {
                        val reroute = BulkRoute.decide(f().copy(wdUp = false, backoffUntil = backoff.until))
                        lastDecision = if (reroute is BulkRoute.Decision.UseWd) BulkRoute.Decision.UseBt(BulkRoute.Why.BACKOFF) else reroute
                        publishLine()
                    }
                    (end as? WdClient.State.Up)?.base
                }
            }
            else -> null
        }
    }

    /** Le groupe est-il tombé depuis [since] (ms) ? La file remet alors le fichier en attente pour une nouvelle décision ([BulkRoute.rerouteAfterLoss]). */
    fun lostSince(since: Long) = lastLostAt >= since

    /** Rendre le groupe tout de suite (réglage désactivé, TV oubliée). */
    fun release() { machine.execute { step(WdClient.Event.Release(now())) } }

    /** Plus rien à envoyer : efface la ligne d'état (le groupe part tout seul après 30 s de file vide). */
    fun queueIdle() { if (_state.value !is WdClient.State.Up) { _line.value = null; lastDecision = null } }

    // ------------------------------------------------------------------ le bouton « Wi-Fi Direct » (démarrage explicite)

    /** L'écran de la TV est-il devant l'usager ? (la session manuelle tient 10 minutes hors de lui) */
    fun screenVisible(on: Boolean) { screenOn = on }

    /**
     * Ce que le téléphone sait de [tv] pour le bouton ([WdManualView.of], pur). Lecture seule : [credential] n'est testé qu'utilisable, jamais affiché ni copié.
     * La liaison appairée est tenue pour acquise quand une session existe (le HELLO Bluetooth a répondu).
     */
    fun manualFacts(ctx: Context, tv: SavedTv?, session: LinkSession?, credential: String?): WdManualView.Facts {
        val a = ctx.applicationContext
        return WdManualView.Facts(
            hasTv = tv != null,
            btBonded = tv != null && (session != null || bonded(a, tv.address)),
            btLinked = session != null,
            credentialValid = TvAuth.isUsable(credential),
            tvOffersWd = session?.info?.link?.wdCap,
            api = Build.VERSION.SDK_INT,
            permission = permission(a),
            phoneWifiOn = wifiOn(a),
            tvWdError = tvErr?.takeIf { now() - tvErrAt < TV_ERR_MS },
            lanAlive = session != null && BulkRoute.lanRoute(session.route),
            state = _state.value,
            manual = _manual.value,
        )
    }

    private fun bonded(ctx: Context, address: String): Boolean = runCatching {
        ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty().any { it.address.equals(address, true) }
    }.getOrDefault(false)

    /**
     * Le toucher sur « Wi-Fi Direct » : même mécanique que la voie automatique (CBTN sur le lien Bluetooth appairé, jonction, sonde) mais SANS le seuil de
     * 5 Mo, SANS la pause de 10 minutes, SANS le réglage automatique ; JAMAIS sans lien, sans code valide, sans capacité de la TV ([WdManualView.start]).
     * [userAsked] faux : aucun réseau commun n'est jamais remplacé. [lanConfirmed] : l'usager a répondu oui à « Un réseau commun fonctionne déjà… ».
     * Renvoie ce qu'il faut montrer : [WdManualView.Start.ConfirmLan] = poser la question puis rappeler avec [lanConfirmed] vrai.
     */
    suspend fun startNow(ctx: Context, tv: SavedTv, credential: String, session: LinkSession?, userAsked: Boolean, lanConfirmed: Boolean = false): WdManualView.Start {
        app = ctx.applicationContext
        tvErr = null                                                        // l'usager réessaie à la main : une cause retenue par la voie automatique ne le bloque pas
        fun decide() = WdManualView.start(manualFacts(app, tv, session, credential), lanConfirmed = userAsked && lanConfirmed)
        var d = decide()
        if (d == WdManualView.Start.AskPermission) {
            askingPermission = true; _ask.value = BulkRoute.Ask.PERMISSION
            withTimeoutOrNull(ASK_WAIT_MS) { _ask.first { it == null } }
            if (_ask.value != null) { _ask.value = null; askingPermission = false }
            d = decide().let { if (it == WdManualView.Start.AskPermission) WdManualView.Start.Refuse(WdManualView.Cause.PERMISSION_DENIED) else it }
        }
        if (d != WdManualView.Start.Go) return d
        target = tv.address to credential
        method = WdJoin.method(Build.VERSION.SDK_INT)
        hadLanBefore = session?.let { HelloIps.lanOnly(it.info.link.ips).isNotEmpty() }
        manualStartedAt = now(); lease = WdManualLease().seen(now(), screenOn); diagDone = false
        _report.value = null; lastDecision = null
        _manual.value = true
        log("Wi-Fi Direct manuel : demandé (Android ${Build.VERSION.SDK_INT}, jonction $method)")
        machine.submit { step(WdClient.Event.Start(now(), method)) }.get()
        ensureTicking()
        return d
    }

    /** « Arrêter » (ou « Annuler » pendant la mise en place) : le groupe est rendu tout de suite. */
    fun stopManual() { log("Wi-Fi Direct manuel : arrêté par l'usager"); _manual.value = false; release() }

    /** Une ligne dans le journal local du Wi-Fi Direct (jamais un mot de passe, un code ni un jeton). */
    private fun log(text: String) {
        Log.i(TAG, text)
        runCatching {
            val j = journal ?: TunnelJournal(java.io.File(app.filesDir, "wifi-direct-journal.log")).also { journal = it }
            j.add(castbridge.core.trust.Redact.scrub(text))
        }
    }

    /** Après la jonction d'une session manuelle : latence ×20, petit transfert, clé USB, Wi-Fi de la TV ; le relevé va à l'écran et au journal. */
    private fun startDiagnostic(up: WdClient.State.Up) {
        val cred = target?.second ?: return
        val ssid = diagSsid
        val joined = (up.since - manualStartedAt).coerceAtLeast(0)
        val hadLan = hadLanBefore
        _measuring.value = true
        io.execute {
            val text = runCatching {
                val m = WdDiagnostic.measure(WdFieldProbe(up.base, cred), { now() })
                WdDiagnostic.format(WdDiagnostic.report(ssid, up.base.removePrefix("http://").substringBefore(':'), joined, hadLan, m))
            }.getOrElse { "Relevé impossible : ${it.javaClass.simpleName}" }
            _report.value = text
            log("Relevé Wi-Fi Direct : " + text.replace('\n', ' '))
            _measuring.value = false
        }
    }

    // ------------------------------------------------------------------ l'automate et ses effets

    private fun step(e: WdClient.Event) {
        val r = WdClient.reduce(_state.value, e)
        if (r.state != _state.value) Log.i(TAG, "${_state.value} -> ${r.state}")
        _state.value = r.state
        if (r.state == WdClient.State.Off || r.state is WdClient.State.Failed) {
            if (_manual.value) log("Wi-Fi Direct manuel : " + ((r.state as? WdClient.State.Failed)?.let { "échec · " + WdClient.explain(it.fail, it.detail) } ?: "arrêté"))
            _manual.value = false
        }
        (r.state as? WdClient.State.Up)?.let { if (_manual.value && !diagDone) { diagDone = true; startDiagnostic(it) } }
        publishLine()
        r.effects.forEach(::run)
    }

    private fun post(e: WdClient.Event) = machine.execute { step(e) }

    private fun ensureTicking() {
        if (ticking) return
        ticking = true
        machine.schedule(object : Runnable {
            override fun run() {
                val s = _state.value
                if (s == WdClient.State.Off || s is WdClient.State.Failed) { ticking = false; return }
                val transfers = TransferQueue.busy() || UploadService.active()
                if (_manual.value) {                                 // session manuelle : jamais rendue pour « file vide », seulement par l'usager ou 10 min hors écran
                    lease = lease.seen(now(), screenOn)
                    if (lease.expired(now(), transfers)) { log("Wi-Fi Direct manuel : arrêté (10 minutes hors de l'écran de la TV)"); _manual.value = false; step(WdClient.Event.Release(now())) }
                }
                val busy = transfers || _manual.value
                step(WdClient.Event.Tick(now(), busy))
                if (_state.value is WdClient.State.Up) checkStillJoined()
                machine.schedule(this, TICK_MS, TimeUnit.MILLISECONDS)
            }
        }, TICK_MS, TimeUnit.MILLISECONDS)
    }

    private fun run(f: WdClient.Effect) {
        when (f) {
            WdClient.Effect.RequestGroup -> io.execute { requestGroup() }
            is WdClient.Effect.Join -> join(f.ssid, f.pass, f.method)
            is WdClient.Effect.Probe -> io.execute { probe(f.base) }
            WdClient.Effect.Leave -> leave()
            WdClient.Effect.ReleaseTv -> io.execute { cbtn(BtProtocol.WD_RELEASE) }
            is WdClient.Effect.Failure -> {
                backoff = backoff.failed(now())
                if (f.fail == WdClient.Fail.LOST) lastLostAt = now()
                Log.i(TAG, "échec Wi-Fi Direct : ${f.fail}")
            }
            WdClient.Effect.Success -> backoff = backoff.succeeded()
        }
    }

    /** CBTN over the paired, encrypted RFCOMM link; the trusted phone's token never travels (NO_PIN), the TV knows the phone by the socket's address. */
    private fun cbtn(flags: Int): castbridge.core.tv.LinkInfo? {
        val (addr, cred) = target ?: return null
        return runCatching {
            AndroidBtTransport(app).connect(addr).use { l -> BtProtocol.negotiate(l.input, l.output, castbridge.core.trust.TvAuth.btPin(cred), flags) }
        }.onFailure { Log.i(TAG, "CBTN: ${it.javaClass.simpleName}") }.getOrNull()
    }

    private fun requestGroup() {
        val info = cbtn(BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE)
        val ssid = info?.wdSsid; val pass = info?.wdPass
        when {
            info == null -> post(WdClient.Event.TvRefused(now()))
            ssid != null && pass != null && WifiDirect.isValidNetworkName(ssid) && WifiDirect.isValidPassphrase(pass) ->
            { diagSsid = ssid; post(WdClient.Event.Creds(ssid, pass, info.wdIp, info.port, now(), method)) }   // le nom seulement est gardé pour le relevé, jamais le mot de passe
            else -> {
                info.wdErr?.let { tvErr = it; tvErrAt = now() }
                post(WdClient.Event.NoGroup(info.wdErr, now()))
            }
        }
    }

    private fun join(ssid: String, pass: String, m: JoinMethod) {
        when (m) {
            JoinMethod.P2P_CONNECT -> joinP2p(ssid, pass)
            JoinMethod.NETWORK_SPECIFIER -> joinSpecifier(ssid, pass)
            JoinMethod.NONE -> post(WdClient.Event.JoinFailed(WdClient.Fail.JOIN_DENIED, now()))
        }
    }

    /** API 33+ : rejoindre un groupe dont on connaît le nom et le mot de passe : pas d'invitation, donc aucune boîte, ni ici ni sur la TV. */
    private fun joinP2p(ssid: String, pass: String) {
        try {
            val mgr = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
                ?: return post(WdClient.Event.JoinFailed(WdClient.Fail.JOIN_DENIED, now()))
            val ch = channel ?: mgr.initialize(app, Looper.getMainLooper()) { post(WdClient.Event.Lost(now())) }.also { channel = it }
            p2p = mgr
            val cfg = WifiP2pConfig.Builder().setNetworkName(ssid).setPassphrase(pass).enablePersistentMode(false).build()
            mgr.connect(ch, cfg, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { pollJoined(mgr, ch) }
                override fun onFailure(reason: Int) {
                    Log.i(TAG, "connect: refus $reason")
                    post(WdClient.Event.JoinFailed(if (reason == WifiP2pManager.BUSY) WdClient.Fail.JOIN_TIMEOUT else WdClient.Fail.JOIN_DENIED, now()))
                }
            })
        } catch (e: SecurityException) { post(WdClient.Event.JoinFailed(WdClient.Fail.JOIN_DENIED, now()))
        } catch (e: IllegalArgumentException) { post(WdClient.Event.JoinFailed(WdClient.Fail.BAD_CREDENTIALS, now())) }
    }

    /** Android dit « demande acceptée » avant l'association : on attend le groupe formé (l'automate coupe à [WdJoin.joinTimeoutMs]). */
    private fun pollJoined(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
        machine.schedule({
            if (_state.value !is WdClient.State.Joining) return@schedule
            runCatching {
                mgr.requestConnectionInfo(ch) { info ->
                    if (info != null && info.groupFormed && !info.isGroupOwner) post(WdClient.Event.Joined(info.groupOwnerAddress?.hostAddress, now()))
                    else pollJoined(mgr, ch)
                }
            }.onFailure { post(WdClient.Event.JoinFailed(WdClient.Fail.JOIN_DENIED, now())) }
        }, 1_000, TimeUnit.MILLISECONDS)
    }

    /**
     * API 29-32 : Android demande « Se connecter à l'appareil ? ». Seuls les sockets vers le groupe (192.168.49.x) passent par son réseau
     * ([castbridge.core.net.BoundRoute] : `Network.openConnection` / `bindSocket`) ; le reste de l'app garde son Internet (audit I-3 : plus de
     * `bindProcessToNetwork`).
     */
    private fun joinSpecifier(ssid: String, pass: String) {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val spec = runCatching { WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(pass).build() }.getOrNull()
            ?: return post(WdClient.Event.JoinFailed(WdClient.Fail.BAD_CREDENTIALS, now()))
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                castbridge.core.net.BoundRoute.set(HelloIps.GROUP_PREFIX, object : castbridge.core.net.BoundRoute.Binding {
                    override fun open(url: java.net.URL): java.net.URLConnection = network.openConnection(url)
                    override fun bind(s: java.net.Socket) = network.bindSocket(s)
                })
                post(WdClient.Event.Joined(null, now()))
            }
            override fun onUnavailable() { post(WdClient.Event.JoinFailed(WdClient.Fail.JOIN_DENIED, now())) }
            override fun onLost(network: Network) { post(WdClient.Event.Lost(now())) }
        }
        specCb = cb
        runCatching { cm.requestNetwork(req, cb, WdJoin.joinTimeoutMs(JoinMethod.NETWORK_SPECIFIER).toInt()) }
            .onFailure { specCb = null; post(WdClient.Event.JoinFailed(WdClient.Fail.JOIN_DENIED, now())) }
    }

    private fun probe(base: String) {
        if (_state.value is WdClient.State.Probing && (_state.value as WdClient.State.Probing).tries > 0) Thread.sleep(1_000)
        val ok = runCatching {
            val c = castbridge.core.net.BoundRoute.open(java.net.URL("$base/api/hello")) as java.net.HttpURLConnection
            c.connectTimeout = 2_000; c.readTimeout = 2_000
            c.responseCode == 200 && c.inputStream.use { it.readBytes() }.decodeToString().contains("castbridge-tv")
        }.getOrDefault(false)
        post(if (ok) WdClient.Event.ProbeOk(now()) else WdClient.Event.ProbeFail(now()))
    }

    /** Groupe toujours là ? (P2P : `requestConnectionInfo` ; le spécificateur prévient lui-même par onLost.) */
    private fun checkStillJoined() {
        val mgr = p2p ?: return; val ch = channel ?: return
        runCatching { mgr.requestConnectionInfo(ch) { info -> if (info == null || !info.groupFormed) post(WdClient.Event.Lost(now())) } }
    }

    private fun leave() {
        val mgr = p2p; val ch = channel
        if (mgr != null && ch != null) {
            runCatching { mgr.cancelConnect(ch, null) }
            runCatching { mgr.removeGroup(ch, null) }           // as a client: leaves the TV's group (the TV removes it once no client is left)
            runCatching { ch.close() }
        }
        p2p = null; channel = null
        specCb?.let { cb ->
            val cm = app.getSystemService(ConnectivityManager::class.java)
            castbridge.core.net.BoundRoute.clear()
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
        specCb = null
    }
}

/**
 * Les demandes du Wi-Fi Direct automatique, juste à temps et une fois : l'autorisation « Appareils à proximité » (Android 13+ ; déjà accordée avec le
 * Bluetooth sur la plupart des téléphones, Android l'accorde alors sans boîte) ou le panneau Wi-Fi quand le Wi-Fi du téléphone est éteint.
 * Posée une fois à la racine de l'écran (MainActivity) ; aucun nouvel écran.
 */
@Composable
fun AutoWifiDirectAsker() {
    val ctx = LocalContext.current
    val ask by AutoWifiDirect.ask.collectAsState()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> AutoWifiDirect.onPermissionResult(ctx, g) }
    LaunchedEffect(ask) {
        when (ask) {
            BulkRoute.Ask.PERMISSION -> {
                val p = WdJoin.permission(Build.VERSION.SDK_INT)
                if (p == null) AutoWifiDirect.onPermissionResult(ctx, true) else runCatching { perm.launch(p) }.onFailure { AutoWifiDirect.onPermissionResult(ctx, false) }
            }
            BulkRoute.Ask.PHONE_WIFI -> {
                runCatching { ctx.startActivity(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                AutoWifiDirect.onWifiAsked(ctx)
            }
            null -> {}
        }
    }
}
