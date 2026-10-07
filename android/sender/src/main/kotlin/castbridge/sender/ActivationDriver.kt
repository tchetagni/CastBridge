package castbridge.sender

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import castbridge.core.lots.HttpTvTransport
import castbridge.core.owner.ActivationExecutors
import castbridge.core.owner.ActivationRoutePlan
import castbridge.core.owner.ActivationRoutePlan.Bt
import castbridge.core.owner.ActivationRoutePlan.Cause
import castbridge.core.owner.ActivationRoutePlan.Found
import castbridge.core.owner.ActivationRoutePlan.Phase
import castbridge.core.owner.ActivationRoutePlan.Route
import castbridge.core.owner.ActivationSend
import castbridge.core.owner.KeyAcquisition
import castbridge.core.owner.LockedRequestRoute
import castbridge.core.owner.LockedRequestRoute.Reply
import castbridge.core.trust.DeviceRequestParse
import castbridge.core.tv.WdCode
import castbridge.core.tv.WifiDirect
import castbridge.owner.TvBluetooth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ScheduledFuture

/**
 * L'exécutant de « Activer la TV » (DESIGN-ACTIVATION-SIMPLE-2026-10-07) : mince. Tout ce qui décide est dans le cœur, testé en JVM : l'ordre, les bornes, les lignes d'état et les causes
 * des voies ([ActivationRoutePlan]), puis l'obtention et l'installation de la clé ([KeyAcquisition]). Ici : sonder (réseau local par mDNS, groupe Wi-Fi Direct par
 * [ActivationGroupJoin], Bluetooth par [TvBluetooth]), rapporter les événements, faire tourner l'horloge (monotone), envoyer la clé, publier l'état.
 *
 * Un seul fil « machine » réduit les événements dans l'ordre ; le travail qui bloque (HTTP, Bluetooth) tourne ailleurs et rapporte par [postRoute] : le résultat d'une exécution
 * abandonnée (autre code saisi, voie arrêtée) est ignoré (génération). Toute soumission aux deux exécuteurs passe par [ActivationExecutors] (audit B1) : une réponse de la TV qui arrive après
 * [release] (écran quitté pendant « Installation… ») est ignorée, jamais jetée dans un fil du pool. Aucun code, aucune clé, aucune demande dans un journal : ce fichier n'écrit AUCUNE ligne de journal.
 */
class ActivationDriver(ctx: Context, private val discovery: TvDiscovery, val consolePresent: Boolean) {
    /** Ce que l'écran dessine. [reading] : la demande est lue par Bluetooth ; [btNote] : le résultat de cette lecture. */
    data class Ui(val run: ActivationRoutePlan.Run?, val acq: KeyAcquisition.Model, val reading: Boolean = false, val btNote: String? = null)

    private val app = ctx.applicationContext
    /** Le fil « machine » et le pool de ce qui bloque, gardés : aucune soumission après [release], jamais de `RejectedExecutionException` dans un fil de pool. */
    private val exec = ActivationExecutors.standard()
    private fun onMachine(task: () -> Unit) { exec.onMachine(task) }
    private fun onIo(task: () -> Unit) { exec.onIo(task) }
    private val _ui = MutableStateFlow(Ui(null, KeyAcquisition.Model()))
    val ui: StateFlow<Ui> = _ui

    // état tenu par le fil « machine » seulement
    private var run: ActivationRoutePlan.Run? = null
    private var acq = KeyAcquisition.Model()
    @Volatile private var code: String? = null
    private var reading = false
    private var btNote: String? = null
    private var group: ActivationGroupJoin? = null
    private var ticker: ScheduledFuture<*>? = null
    @Volatile private var generation = 0
    @Volatile private var active: Pair<Route, Int>? = null
    private val released: Boolean get() = exec.released
    /** Les TV annoncées par mDNS. [TvDiscovery.tvs] ne vit que tant que quelqu'un le collecte (`WhileSubscribed`) : l'exécutant le collecte lui-même, sinon sa valeur resterait vide. */
    @Volatile private var announced: List<Tv> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        runCatching { TvLinkManager.init(app) }
        scope.launch { discovery.tvs.collect { announced = it } }
        ticker = exec.every(TICK_MS) { tick() }
    }

    private fun now() = SystemClock.elapsedRealtime()
    private fun publish() { _ui.value = Ui(run, acq, reading, btNote) }

    // ------------------------------------------------------------------ ce que l'écran demande

    /** Le sixième chiffre est saisi : lancer le plan. Un plan en cours est abandonné. */
    fun start(code: String) = onMachine {
        if (released || !WdCode.isValid(code)) return@onMachine
        abortRoutes()
        this.code = code; generation++
        btNote = null
        handleAcq(KeyAcquisition.reduce(acq, KeyAcquisition.Event.TvLost))                    // un envoi en cours échoue, la clé reste prête
        val facts = ActivationRoutePlan.Facts(code, Build.VERSION.SDK_INT, wifiOn(), onWifi(), btFact())
        applyPlan(ActivationRoutePlan.start(facts, now()))
    }

    /** « Réessayer » : même code. */
    fun retry() { code?.let(::start) }

    /** Le code est effacé ou modifié : tout s'arrête, la clé reste. */
    fun cancel() = onMachine {
        abortRoutes(); generation++
        run = null; code = null
        handleAcq(KeyAcquisition.reduce(acq, KeyAcquisition.Event.TvLost))
        publish()
    }

    /** Avant Android 10 : « C'est fait » (le téléphone est sur le Wi-Fi de la TV) ou « Passer au Bluetooth ». */
    fun userJoined() = postPlan(ActivationRoutePlan.Event.UserJoined(now()))
    fun skipManual() = postPlan(ActivationRoutePlan.Event.UserSkipped(now()))

    fun paste(text: String) = postAcq(KeyAcquisition.Event.Pasted(text, now()))
    fun fileText(text: String?) = postAcq(KeyAcquisition.Event.FilePicked(text, now()))
    fun clipboard(text: String?) = postAcq(KeyAcquisition.Event.Clipboard(text, now()))
    fun acceptClipboard() = postAcq(KeyAcquisition.Event.ClipboardAccepted(now()))
    fun dismissClipboard() = postAcq(KeyAcquisition.Event.ClipboardDismissed)
    fun consoleKey(token: String) = postAcq(KeyAcquisition.Event.ConsoleKey(token, now()))
    fun install() = postAcq(KeyAcquisition.Event.Install(now()))
    /** « Demander l'activation à CastBridge » : sans effet tant que la capacité est éteinte ([KeyAcquisition.ServerActivationRequests]). */
    fun serverRequest() = postAcq(KeyAcquisition.Event.ServerRequest(now()))

    /** La TV jointe par le réseau ne livre pas sa demande (version ancienne) : la lire par Bluetooth si une TV est appairée. */
    fun readRequestByBluetooth() = onMachine {
        val f = (run?.phase as? Phase.Connected)?.found ?: return@onMachine
        if (reading) return@onMachine
        reading = true; btNote = null; publish()
        onIo {
            val tv = bluetoothTv()
            val outcome: Pair<DeviceRequestParse?, String?> = when {
                tv == null -> null to "Aucune TV appairée en Bluetooth : appairez-la d'abord (« Ajouter ma TV »)."
                else -> try {
                    val text = TvBluetooth.with(app, tv) { c -> c.deviceInfo() }
                    if (text == null) null to "La TV n'a pas donné sa demande d'appareil." else LockedRequestRoute.parse(text) to null
                } catch (e: Exception) { null to ActivationRoutePlan.btCause(e.message).text(Route.BLUETOOTH, WdCode.networkName(code ?: "000000")) }
            }
            onMachine {
                reading = false
                val parsed = outcome.first
                if (parsed is DeviceRequestParse.Ok && run?.phase is Phase.Connected) {
                    val cur = (run!!.phase as Phase.Connected).found
                    val updated = cur.copy(request = parsed.request, note = null)
                    run = run!!.copy(phase = Phase.Connected(updated))
                    handleAcq(KeyAcquisition.reduce(acq, KeyAcquisition.Event.TvFound(KeyAcquisition.Tv(updated.name, updated.route, isLinked(updated)), parsed.request, now())))
                } else btNote = (parsed as? DeviceRequestParse.Refused)?.message ?: outcome.second
                publish()
            }
        }
    }

    /**
     * Rend tout : réseau demandé, liaison des sockets, fils. Dès cet instant plus rien ne se lance ([ActivationExecutors]) : la réponse de la TV qui arrive plus tard dans un fil du pool est ignorée,
     * jamais jetée (audit B1 : `RejectedExecutionException` dans un fil de pool = processus tué).
     */
    fun release() {
        active = null
        scope.cancel()
        exec.release { ticker?.cancel(false); group?.release(); group = null }
    }

    // ------------------------------------------------------------------ la machine

    private fun postPlan(e: ActivationRoutePlan.Event) = onMachine { run?.let { applyPlan(ActivationRoutePlan.reduce(it, e)) } }
    private fun postAcq(e: KeyAcquisition.Event) = onMachine { handleAcq(KeyAcquisition.reduce(acq, e)) }

    /** Rapport d'une voie : ignoré s'il vient d'une exécution abandonnée, ou arrive après [release]. */
    private fun postRoute(g: Int, e: ActivationRoutePlan.Event) = onMachine {
        if (g == generation) run?.let { applyPlan(ActivationRoutePlan.reduce(it, e)) }
    }

    private fun tick() {
        val r = run
        if (r != null && r.phase is Phase.Trying) applyPlan(ActivationRoutePlan.reduce(r, ActivationRoutePlan.Event.Tick(now())))
        if (acq.phase is KeyAcquisition.Phase.Installing || acq.server is KeyAcquisition.Server.Waiting) handleAcq(KeyAcquisition.reduce(acq, KeyAcquisition.Event.Tick(now())))
    }

    private fun applyPlan(step: ActivationRoutePlan.Step) {
        val before = run?.phase
        run = step.run
        step.effects.forEach(::perform)
        val ph = step.run.phase
        if (ph is Phase.Connected && before !is Phase.Connected) connected(ph.found)           // une seule fois par jonction : un rapport tardif ne relance rien
        publish()
    }

    private fun connected(f: Found) {
        val tv = KeyAcquisition.Tv(f.name, f.route, isLinked(f))
        handleAcq(KeyAcquisition.reduce(acq, KeyAcquisition.Event.TvFound(tv, f.request, now())))
    }

    private fun handleAcq(step: KeyAcquisition.Step) {
        val before = acq.phase
        acq = step.model
        step.effects.forEach(::performAcq)
        // activated: the group is given back (the TV drops it too); the link to keep for « Ajouter ma TV » is the trusted one, not this one
        if (acq.phase is KeyAcquisition.Phase.Installed && before !is KeyAcquisition.Phase.Installed) { group?.release(); group = null }
        publish()
    }

    private fun perform(e: ActivationRoutePlan.Effect) {
        when (e) {
            is ActivationRoutePlan.Effect.Abort -> {
                if (active?.first == e.route) active = null
                if (e.route == Route.GROUP) { group?.release(); group = null }
            }
            is ActivationRoutePlan.Effect.Try -> {
                val g = generation
                active = e.route to g
                when (e.route) {
                    Route.LAN -> onIo { lanLoop(g) }
                    Route.GROUP -> startGroup(g, e.manual)
                    Route.BLUETOOTH -> onIo { bluetoothAttempt(g) }
                }
            }
        }
    }

    private fun performAcq(e: KeyAcquisition.Effect) {
        when (e) {
            is KeyAcquisition.Effect.Install -> {
                val found = (run?.phase as? Phase.Connected)?.found; val c = code; val g = generation
                onIo { if (found != null && c != null) installNow(found, c, e.key, g) else postAcq(KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.UNREACHABLE, "", now())) }
            }
            is KeyAcquisition.Effect.Notify -> ActivationNotice.post(app, e.title, e.text)
            // capacité éteinte (KeyAcquisition.ServerActivationRequests.ENABLED = false) : le cœur n'émet jamais ces effets, et aucune requête réseau n'existe ici
            is KeyAcquisition.Effect.SendServerRequest, is KeyAcquisition.Effect.PollServer -> {}
        }
    }

    private fun abortRoutes() {
        active = null
        group?.release(); group = null
    }

    private fun isActive(route: Route, g: Int) = !released && g == generation && active == (route to g)

    // ------------------------------------------------------------------ voie 1 : le réseau local

    /** Les TV annoncées et la TV liée, telles que le plan les trie ([ActivationRoutePlan.candidates] : verrouillées d'abord, adresses privées, trois au plus). */
    private fun lanCandidates(): List<ActivationRoutePlan.Candidate> {
        val linked = (TvLinkManager.state.value as? LinkUi.Connected)?.session?.let { s -> s.base?.let { ActivationRoutePlan.Candidate(s.tv.name, it, false) } }
        return ActivationRoutePlan.candidates(announced.map { ActivationRoutePlan.Candidate(it.name, it.base, it.locked) }, linked)
    }

    private fun lanLoop(g: Int) {
        val code = this.code ?: return
        val probes = ActivationRoutePlan.LanProbes()
        while (isActive(Route.LAN, g)) {
            for (c in probes.due(lanCandidates(), now())) {
                if (!isActive(Route.LAN, g)) return
                val event = probes.answered(c, probe(c.base, c.locked, code)) ?: continue      // sans réponse : réessayé jusqu'à la borne du plan
                postRoute(g, event)
                if (event is ActivationRoutePlan.Event.Reached) return
            }
            sleep(LOOP_MS)
        }
    }

    /** La demande d'appareil de la TV avec le code (cœur : [LockedRequestRoute.probe], testé contre de vraies conversations HTTP) ; null = la TV n'a pas répondu (réessayer). */
    private fun probe(base: String, locked: Boolean, code: String): Reply? = LockedRequestRoute.probe(base, code, locked)

    // ------------------------------------------------------------------ voie 2 : le groupe Wi-Fi Direct d'activation

    private fun startGroup(g: Int, manual: Boolean) {
        val code = this.code ?: return
        val join = ActivationGroupJoin(app)
        group?.release(); group = join
        val ssid = WdCode.networkName(code)
        val joined = { onIo { probeGroup(g, code) } }
        val failed = { cause: Cause -> postRoute(g, ActivationRoutePlan.Event.Failed(Route.GROUP, cause, now())) }
        onIo {
            if (manual) join.bindToJoinedWifi(joined, failed)
            else join.join(ssid, WdCode.passphrase(code), ActivationRoutePlan.GROUP_MS.toInt(), joined, failed) { postRoute(g, ActivationRoutePlan.Event.Lost(Route.GROUP, now())) }
        }
    }

    private fun probeGroup(g: Int, code: String) {
        val probes = ActivationRoutePlan.GroupProbes()
        while (isActive(Route.GROUP, g)) {
            val reply = probe(WifiDirect.BASE_URL, true, code)
            val event = probes.answered(reply, now())
            if (event != null) postRoute(g, event)
            if (event is ActivationRoutePlan.Event.Reached || event is ActivationRoutePlan.Event.Failed) return           // une réponse de la TV est définitive
            if (reply == null) sleep(LOOP_MS * 2)
        }
    }

    // ------------------------------------------------------------------ voie 3 : Bluetooth (le canal d'activation existant)

    /** La TV Bluetooth déjà appairée (celle qui déclare un service CastBridge, sinon la seule appairée), ou null. */
    private fun bluetoothTv(): TvBluetooth.Tv? = TvBluetooth.pairedTvs(app).let { l -> l.firstOrNull { it.sure } ?: l.singleOrNull() }

    private fun bluetoothAttempt(g: Int) {
        var tv = bluetoothTv()
        if (tv == null) {
            val seen = java.util.concurrent.CopyOnWriteArrayList<TvBluetooth.Tv>()
            runCatching { TvBluetooth.scan(app, SCAN_SECONDS) { if (isActive(Route.BLUETOOTH, g)) seen += it } }
            tv = seen.firstOrNull()
        }
        if (!isActive(Route.BLUETOOTH, g)) return
        if (tv == null) return postRoute(g, ActivationRoutePlan.Event.Failed(Route.BLUETOOTH, Cause(Cause.Kind.BT_NO_TV), now()))
        if (!tv.bonded) postRoute(g, ActivationRoutePlan.Event.Pairing(now()))               // Android demande de valider l'appairage : la borne s'allonge
        try {
            val text = TvBluetooth.with(app, tv) { c -> c.deviceInfo() }
            if (!isActive(Route.BLUETOOTH, g)) return
            val req = text?.let { (LockedRequestRoute.parse(it) as? DeviceRequestParse.Ok)?.request }
            postRoute(g, ActivationRoutePlan.Event.Reached(Found(Route.BLUETOOTH, ActivationRoutePlan.cleanName(tv.name), null, req, btAddress = tv.address,
                note = if (req == null) Cause(Cause.Kind.UNREADABLE) else null)))
        } catch (e: Exception) {
            if (isActive(Route.BLUETOOTH, g)) postRoute(g, ActivationRoutePlan.Event.Failed(Route.BLUETOOTH, ActivationRoutePlan.btCause(e.message), now()))
        }
    }

    // ------------------------------------------------------------------ installer la clé

    private fun installNow(f: Found, code: String, key: String, g: Int) {
        val result: KeyAcquisition.Event.Result = try {
            when {
                f.base != null -> installHttp(f, code, key)
                f.btAddress != null -> installBluetooth(TvBluetooth.Tv(f.name, f.btAddress!!, true, true), key)
                else -> KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.UNREACHABLE, "", now())
            }
        } catch (e: Exception) { KeyAcquisition.Event.Result(KeyAcquisition.ResultKind.UNREACHABLE, "", now()) }
        onMachine { if (g == generation) handleAcq(KeyAcquisition.reduce(acq, result)) }
    }

    private fun installHttp(f: Found, code: String, key: String): KeyAcquisition.Event.Result {
        val r = ActivationSend.sendLan(HttpTvTransport(f.base!!, code), key)
        // le Wi-Fi ne répond plus : repli sur le Bluetooth appairé, comme l'ancien écran ; sinon le résultat est celui du cœur
        if (r.linkDown) bluetoothTv()?.let { tv -> runCatching { installBluetooth(tv, key) }.getOrNull()?.let { return it } }
        return KeyAcquisition.resultOf(r, now())
    }

    private fun installBluetooth(tv: TvBluetooth.Tv, key: String): KeyAcquisition.Event.Result {
        val a = TvBluetooth.with(app, tv) { c -> c.sendActivation(key) }
        return KeyAcquisition.resultOfBluetooth(a.ok, a.message, now())
    }

    // ------------------------------------------------------------------ ce que le téléphone sait avant d'essayer

    private fun wifiOn(): Boolean = runCatching { (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun onWifi(): Boolean = runCatching {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }.getOrDefault(false)

    private fun btFact(): Bt {
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return Bt.NO_ADAPTER
        if (!TvBluetooth.permitted(app)) return Bt.NO_PERMISSION
        if (!adapter.isEnabled) return Bt.OFF
        return if (bluetoothTv() != null) Bt.PAIRED else Bt.SEARCH
    }

    /** Ce téléphone est-il déjà de confiance pour cette TV ? (alors « Ajouter ma TV » n'est pas reproposé) */
    private fun isLinked(f: Found): Boolean = runCatching {
        val host = f.base?.let(ActivationSend::hostOf)
        TvLinkManager.saved.list().any { s ->
            (f.btAddress != null && s.address.equals(f.btAddress, true)) || (host != null && host in s.lastIps) || (f.route == Route.LAN && (s.name == f.name || s.mdns == f.name))
        }
    }.getOrDefault(false)

    private fun sleep(ms: Long) { try { Thread.sleep(ms) } catch (e: InterruptedException) { Thread.currentThread().interrupt() } }

    private companion object {
        const val TICK_MS = 500L
        const val LOOP_MS = 500L
        const val SCAN_SECONDS = 8
    }
}
