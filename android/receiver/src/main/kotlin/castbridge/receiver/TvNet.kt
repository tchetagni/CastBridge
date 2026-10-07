package castbridge.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.util.Log
import castbridge.core.connect.NetFacts
import castbridge.core.connect.NetProbePlan
import castbridge.core.connect.NetState
import castbridge.core.connect.NetStates
import castbridge.core.connect.Routes
import castbridge.core.net.LinkKind
import castbridge.core.net.JsonLite
import castbridge.core.owner.OwnerFrames
import castbridge.core.quiz.online.PlayRelay
import castbridge.core.quiz.online.PlayRelayProfile
import castbridge.core.quiz.online.RelayLinkMeter
import castbridge.core.relay.PhoneInfo
import castbridge.core.relay.PipeBroker
import castbridge.core.relay.PipeEnv
import castbridge.core.relay.PipeNeed
import castbridge.core.relay.PipeOutcome
import castbridge.core.relay.PipeWhy
import castbridge.core.relay.RelayChannelHost
import castbridge.core.relay.RelayFrames
import castbridge.core.relay.RelayReason
import castbridge.core.relay.RelayText
import castbridge.core.trust.TrustRegistry
import castbridge.core.tv.BtProtocol
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LA vérité réseau de CastBridge-TV et la demande de tuyau au téléphone (relay-R1, docs/coordination/DESIGN-RELAIS-TELEPHONE-2026-10-07.md § 2.4, § 2.5).
 *
 * - [state] : `direct` / `via_relay` / `none` ([NetState]), lu par le portefeuille, le jeu, « Langues », le tunnel d'assistance et `Routes` : plus de définition propre à chacun
 *   (inventaire I-3). Elle se lit sur `TvService` (réseau propre, tuyau) et `TvConnect` (dernière réponse directe du serveur).
 * - [need] / [ensure] : quand une opération « vivante » (jeu en ligne, portefeuille, mise à jour immédiate, assistance) a besoin d'Internet et que l'état est `none`, la TV demande un
 *   tuyau aux téléphones synchronisés ([PipeBroker]) : elle frappe à leur porte en Bluetooth ([knock] : le téléphone, réveillé, vient lire la demande sur le canal propriétaire), lève le
 *   drapeau `pipeWanted` que la liaison de confiance leur porte (HELLO, `/api/info`) et répond `RELAY_ASK_PIPE` à leur `RELAY_STATE` ([channelHost]). Le téléphone ouvre le tuyau seul.
 * - Sondes : la TV ne sonde plus par le téléphone. La vie du tuyau se lit sur ses trames PING ; UNE vérification vers le serveur du projet à chaque nouveau tuyau, après un appel réel
 *   qui a échoué, sur test manuel ou pendant qu'une opération attend ([NetProbePlan]) ; la jambe directe : toutes les 5 minutes.
 */
object TvNet : PipeEnv {
    private const val TAG = "CastBridgeNet"
    /** Combien de temps une opération vivante attend que le téléphone ouvre le tuyau (réveil, canal propriétaire, liaison Bluetooth, vérification de bout en bout). */
    const val PIPE_WAIT_MS = 40_000L

    private val plan = NetProbePlan()
    private val meter = RelayLinkMeter()
    private val broker = PipeBroker(this)
    /** Téléphones vus AVEC le bit « tuyau à la demande » dans leur HELLO (vrai) ou SANS (faux : un CastBridge ancien) ; absent = pas vu depuis le démarrage. */
    private val capable = ConcurrentHashMap<String, Boolean>()
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-net").apply { isDaemon = true } }
    /** Des délais de garde qui ne dépendent pas du travail de fond (une vérification de 10 s ne doit pas retarder la fermeture d'un `connect()` bloqué). */
    private val timers: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "cb-net-timer").apply { isDaemon = true } }
    private val knockers = Executors.newCachedThreadPool { r -> Thread(r, "cb-knock").apply { isDaemon = true } }
    private val knocking = ConcurrentHashMap.newKeySet<String>()
    private val pumping = AtomicBoolean(false)
    private var started = false

    @Volatile private var lastDirectMs: Long? = null
    private class RelayCheck(val attach: Int, val ok: Boolean, val ms: Long?)
    @Volatile private var relayCheck: RelayCheck? = null
    @Volatile private var seenAttach = -1
    @Volatile private var attachedAt = 0L
    @Volatile private var lastPingAt = 0L
    @Volatile private var lastPingOkAt = 0L
    @Volatile private var sampleAt = 0L
    @Volatile private var sampleBytes = 0L
    /** Le réseau du téléphone qui relaie est facturé (dit par le téléphone dans `RELAY_STATE`) : les gros téléchargements de fond attendent. */
    @Volatile private var phoneMetered: Boolean? = null

    private val svc get() = TvService.running

    /** Une fois, au démarrage du service : le travail de fond du tuyau (une pulsation toutes les 5 s, sans réseau tant qu'aucun téléphone n'est attaché). */
    @Synchronized fun start() {
        if (started) return
        started = true
        worker.scheduleWithFixedDelay({ runCatching { housekeeping() } }, 5, 5, TimeUnit.SECONDS)
    }

    // ------------------------------------------------------------------ la vérité

    fun facts(): NetFacts {
        val s = svc ?: return NetFacts(checked = false, linkUp = false, directValidated = false, directContactRecent = false, relayConnected = false, relayConfirmed = false)
        val gw = s.gateway
        val st = TvConnect.link?.state
        val connected = gw != null && gw.connected
        return NetFacts(
            checked = s.netCheckedAt > 0,
            linkUp = TvNetDiag.linkKind(s) != LinkKind.NONE,
            directValidated = s.netDirectMs != null,
            directContactRecent = st != null && NetStates.contactRecent(st.lastContactOk, st.lastContactVia, st.lastContactAt, System.currentTimeMillis()),
            relayConnected = connected,
            relayConfirmed = gw != null && connected && relayMs(gw) != null,
        )
    }

    /** `direct`, `via_relay` ou `none`. */
    fun state(): NetState = NetStates.of(facts())

    /** Avant la première mesure la TV ne se dit pas hors ligne. */
    fun reachable(): Boolean = NetStates.reachable(facts())

    fun checked(): Boolean = (svc?.netCheckedAt ?: 0L) > 0

    /** Le chemin d'abord pour `Routes` : celui que la vérité dit bon ; aucun avis avant la première mesure ou sans Internet. */
    fun preferredVia(): Routes.Via? = if (!checked()) null else when (state()) {
        NetState.DIRECT -> Routes.Via.DIRECT
        NetState.VIA_RELAY -> Routes.Via.GATEWAY
        NetState.NONE -> null
    }

    /** Le proxy du tuyau quand c'est lui qui porte Internet (jamais pour parler au réseau propre). */
    fun gatewayProxy(): java.net.Proxy? = svc?.gateway?.proxy()

    // ------------------------------------------------------------------ mesures (appelées par la boucle réseau de TvService)

    class Measured(val directMs: Long?, val gatewayMs: Long?, val probed: Boolean, val measured: Boolean)

    /**
     * Ce que la boucle réseau de la TV en retient à chaque passage (60 s en régime établi) : la jambe directe est sondée toutes les 5 minutes (ou sur changement d'état, ou test
     * manuel) et entre deux sondes on reprend la dernière réponse ; la jambe du tuyau ne coûte aucune requête : sa vie se lit sur ses trames PING.
     */
    fun measure(probeMode: Boolean, manual: Boolean, systemValidated: Boolean): Measured {
        val now = System.currentTimeMillis()
        var probed = false
        val directMs: Long? = when {
            probeMode && plan.directDue(now, manual) -> { probed = true; plan.directDone(now); TvNetDiag.probe(null).also { lastDirectMs = it } }
            systemValidated -> if (probeMode) (lastDirectMs ?: 0L) else 0L
            probeMode -> lastDirectMs
            else -> null
        }
        val gw = svc?.gateway
        if (manual && gw?.connected == true) checkRelayNow()                    // « Tests Internet » : l'utilisateur le demande, le tuyau aussi est vérifié
        val gatewayMs = if (gw?.connected == true) relayMs(gw) else null
        return Measured(directMs, gatewayMs, probed, measured = probed || (probeMode && lastDirectMs != null))
    }

    /** Un changement d'état de la TV elle-même (réseau apparu ou perdu, passerelle branchée ou débranchée, appel direct échoué) : la prochaine sonde directe est due. */
    fun markChanged() { plan.markChanged() }

    /** Un appel réel a échoué à travers le tuyau (hors réponse du serveur) : une vérification est due. */
    fun relayFailureSeen() { plan.relayFailureSeen(); pump() }

    /** La passerelle vient de se brancher ou de se débrancher. */
    fun gatewayChanged(connected: Boolean) {
        if (!connected) { meter.reset(); phoneMetered = null }
        pump()
    }

    /** « Mesure du tuyau » : en millisecondes (0 = présumé vivant sans vérification de bout en bout), null = pas de tuyau vérifié. */
    private fun relayMs(gw: BtGatewayHost): Long? {
        val c = relayCheck
        if (c != null && c.attach == gw.attachId) return c.ms
        // pas de vérification pour CE tuyau : si l'écran d'information n'a pas été répondu rien ne part vers le serveur, le tuyau est présumé vivant tant que ses PING répondent ;
        // sinon on attend la vérification (quelques secondes après le branchement)
        if (!consentGiven()) return if (pingAlive()) 0L else null
        return null
    }

    private fun consentGiven(): Boolean = TvConnect.link?.state?.needsConsent == false

    private fun pingAlive(): Boolean {
        val now = System.currentTimeMillis()
        return (lastPingOkAt > 0 && now - lastPingOkAt < 3 * 60_000L) || (lastPingOkAt == 0L && now - attachedAt < 60_000L)
    }

    // ------------------------------------------------------------------ travail de fond

    private fun pump() {
        if (!pumping.compareAndSet(false, true)) return
        try { worker.execute { try { runCatching { housekeeping() } } finally { pumping.set(false) } } } catch (e: Exception) { pumping.set(false) }
    }

    private fun housekeeping() {
        val s = svc ?: return
        val gw = s.gateway
        val now = System.currentTimeMillis()
        if (gw == null || !gw.connected) { seenAttach = -1; return }
        if (gw.attachId != seenAttach) {            // un nouveau tuyau : mesures remises à zéro, un premier PING tout de suite
            seenAttach = gw.attachId; attachedAt = now; lastPingAt = 0L; lastPingOkAt = 0L
            meter.reset(); sampleAt = now; sampleBytes = gw.linkBytes()
        }
        // la vie du tuyau : un PING toutes les minutes tant qu'il sert ou qu'une opération attend, toutes les 5 minutes sinon (14 octets sur la liaison Bluetooth, rien sur les données mobiles)
        val every = if (gw.openStreams > 0 || broker.wanted()) 60_000L else 5 * 60_000L
        if (now - lastPingAt >= every) {
            lastPingAt = now
            gw.pingLink()?.let { meter.pingSample(it); lastPingOkAt = now }
        }
        // le débit : seulement sur du vrai trafic
        if (now - sampleAt >= 5_000L) {
            val bytes = gw.linkBytes()
            if (gw.openStreams > 0) meter.throughputSample(bytes - sampleBytes, now - sampleAt)
            sampleAt = now; sampleBytes = bytes
        }
        probeRelayIfDue(false)
        if (state().up) broker.clear()
        publishStatus()
    }

    /** La vérification de bout en bout du tuyau, vers le serveur du projet, quand [NetProbePlan] la dit due. */
    private fun probeRelayIfDue(manual: Boolean) {
        val s = svc ?: return
        val gw = s.gateway?.takeIf { it.connected } ?: return
        val now = System.currentTimeMillis()
        if (!plan.relayDue(now, consentGiven(), true, gw.attachId.toLong(), manual, broker.wanted())) return
        val proxy = gw.proxy() ?: return
        val base = TvConnect.link?.state?.baseUrl?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_SERVER
        var r = TvNetDiag.probeServer(base, proxy)
        if (r.authBroken && !gw.socksAuthDisabled) { gw.disableSocksToken(); r = TvNetDiag.probeServer(base, proxy) }     // filet de sécurité : cet Android ne présente pas le jeton
        val attach = gw.attachId
        relayCheck = RelayCheck(attach, r.ms != null, r.ms)
        plan.relayDone(now, attach.toLong(), r.ms != null)
        s.netRecheck()                                // la pastille et les écrans relisent l'état tout de suite
    }

    /** Test manuel de l'écran « Tests Internet » : une vérification du tuyau, maintenant. */
    fun checkRelayNow() { worker.execute { runCatching { probeRelayIfDue(true) } } }

    // ------------------------------------------------------------------ la demande de tuyau

    /** Une opération vivante a besoin d'Internet : sans rien attendre, demande un tuyau si la TV n'en a pas. [force] : l'utilisateur vient d'appuyer. */
    fun need(n: PipeNeed, force: Boolean = false) {
        if (state().up) return
        broker.need(n, force)
        pump(); publishStatus()
    }

    /** La même chose en attendant (jamais sur le fil principal) : rend `Up` dès qu'Internet est là, ou l'échec dit en français. */
    fun ensure(n: PipeNeed, timeoutMs: Long = PIPE_WAIT_MS, force: Boolean = false): PipeOutcome {
        val o = broker.ensure(n, timeoutMs, force)
        publishStatus()
        // un téléphone est bien attaché mais la vérification de bout en bout a échoué : ce n'est pas qu'il ne répond pas, c'est qu'il n'a pas Internet derrière
        if (o is PipeOutcome.Failed && o.why == PipeWhy.TIMEOUT && pipeUpWithoutInternet())
            return PipeOutcome.Failed(PipeWhy.REFUSED, RelayText.refusal(RelayReason.OFFLINE), RelayReason.OFFLINE)
        return o
    }

    private fun pipeUpWithoutInternet(): Boolean {
        val gw = svc?.gateway ?: return false
        val c = relayCheck
        return gw.connected && c != null && c.attach == gw.attachId && !c.ok
    }

    /**
     * Le drapeau que la liaison de confiance porte aux téléphones synchronisés (HELLO, `GET /api/info`). Il retombe dès qu'un téléphone est attaché, même avant la vérification de bout en
     * bout : un second téléphone ne doit pas ouvrir un second tuyau pendant ces quelques secondes (la TV n'en sert qu'un à la fois).
     */
    fun pipeWanted(): Boolean = broker.wanted() && svc?.gateway?.connected != true

    /** La ligne d'état de la demande en cours (jamais un nom de téléphone), ou null. */
    fun requestLine(): String? = broker.text()

    private fun publishStatus() { svc?.setStatus("6-relay", broker.text()) }

    /** Un téléphone synchronisé peut-il donner Internet à la TV ? (pour la tuile « Partie Internet » quand elle n'en a pas). */
    fun relayAvailability(): PlayRelay {
        val p = phones()
        return when {
            p.isEmpty() -> PlayRelay.NO_PHONE
            p.all { it.relayCapable == false } -> PlayRelay.OLD_PHONE
            else -> PlayRelay.POSSIBLE
        }
    }

    /** Le HELLO d'un téléphone : le bit « tuyau à la demande » ([BtProtocol.HELLO_RELAY]) dit s'il sait lire la demande. */
    fun onHelloFlags(peer: String, flags: Int) { capable[TrustRegistry.norm(peer)] = flags and BtProtocol.HELLO_RELAY != 0 }

    /** Le canal propriétaire (service …0005) : un téléphone de confiance dit son état de relais, la TV répond si elle veut un tuyau. */
    val channelHost = object : RelayChannelHost {
        override fun isTrusted(peer: String?): Boolean = peer != null && svc?.btTrusted(peer) == true
        override fun onPhoneState(peer: String?, state: RelayFrames.State): RelayFrames.Ask? {
            if (peer != null) {
                val a = TrustRegistry.norm(peer)
                capable[a] = true
                broker.report(a, if (state.phase == RelayFrames.Phase.REFUSED) (state.reason ?: RelayReason.BUSY) else null)
            }
            state.metered?.let { phoneMetered = it }
            publishStatus()
            if (!pipeWanted()) return null
            return RelayFrames.Ask(broker.currentNeeds().ifEmpty { listOf(PipeNeed.PLAY) }, RelayFrames.DEFAULT_TTL_SEC)
        }
    }

    // ------------------------------------------------------------------ PipeEnv

    override fun now() = System.currentTimeMillis()
    override fun sleep(ms: Long) { Thread.sleep(ms); pump() }
    override fun net(): NetState = state()

    override fun phones(): List<PhoneInfo> {
        val s = svc ?: return emptyList()
        return runCatching { s.trust.list() }.getOrDefault(emptyList())
            .filter { s.btTrusted(it.address) }
            .map { PhoneInfo(it.address, capable[TrustRegistry.norm(it.address)], it.lastSeen) }
    }

    override fun knock(address: String) {
        if (!knocking.add(address)) return
        knockers.execute { try { knockOnce(address) } finally { knocking.remove(address) } }
    }

    /**
     * Frappe à la porte du téléphone : une tentative de connexion Bluetooth vers lui sur le service du canal propriétaire. Le téléphone n'y écoute pas (il vient lire la demande, c'est lui
     * qui se connecte à la TV) : la tentative échoue après la recherche de service, mais la liaison de base s'est ouverte et le téléphone en est averti (diffusion Bluetooth « appareil
     * connecté »), ce qui réveille CastBridge même fermé. Rien n'est envoyé, rien n'est écrit.
     */
    @SuppressLint("MissingPermission")
    private fun knockOnce(address: String) {
        val s = svc ?: return
        try {
            val ad = s.getSystemService(BluetoothManager::class.java)?.adapter ?: return
            if (!ad.isEnabled) return
            runCatching { ad.cancelDiscovery() }
            val sock = ad.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(OwnerFrames.SERVICE_UUID))
            val guard = timers.schedule(Runnable { runCatching { sock.close() } }, 10, TimeUnit.SECONDS)
            try { sock.connect() } catch (_: IOException) {} finally { guard.cancel(false); runCatching { sock.close() } }
        } catch (e: SecurityException) {
            Log.w(TAG, "knock: Bluetooth permission refused")
        } catch (e: Exception) { /* hors de portée, éteint : la demande reviendra */ }
    }

    // ------------------------------------------------------------------ jeu en ligne par relais, tâches de fond

    /** La TV n'a Internet que par le tuyau d'un téléphone (l'en-tête informatif `X-CB-Via: relay` du jeu, le mode « relais » du jeu). */
    fun viaRelay(): Boolean = state() == NetState.VIA_RELAY

    /** Les règles du jeu pour la liaison mesurée ([PlayRelayProfile]) : aucune adaptation hors relais. */
    fun playRules(): PlayRelayProfile.Rules = PlayRelayProfile.rules(state(), meter.profile())

    /**
     * Les tâches de fond qui déplacent beaucoup d'octets (mise à jour de l'application, questions, lots de questions) attendent tant que le tuyau d'un téléphone est précieux : une
     * partie en cours, ou un téléphone sur données mobiles (REL-F7). Le battement de cœur et les actions de l'utilisateur ne sont jamais retenus.
     */
    fun backgroundBulkAllowed(): Boolean = !(state() == NetState.VIA_RELAY && (castbridge.receiver.quiz.PlayHub.active() || phoneMetered == true))

    /** État lisible pour `GET /api/relay` : des comptes seulement, jamais un nom ni une adresse. */
    fun json(): String {
        val p = phones()
        val link = meter.profile()
        val c = relayCheck
        return JsonLite.write(linkedMapOf(
            "net" to state().wire, "checked" to checked(), "pipeWanted" to pipeWanted(), "request" to requestLine(),
            "phones" to linkedMapOf("synchronized" to p.size, "relayCapable" to p.count { it.relayCapable == true }, "old" to p.count { it.relayCapable == false }),
            "link" to linkedMapOf("rttMs" to link.rttMs, "kbps" to link.kbps, "samples" to link.samples),
            "check" to (c?.let { linkedMapOf("ok" to it.ok, "ms" to it.ms) }),
            "socksAuth" to (svc?.gateway?.socksAuthDisabled?.not()),
            "phoneMetered" to phoneMetered,
        ))
    }

    /** Raison lisible d'un échec de demande pour l'écran qui attendait (jamais le tuyau ouvert : voir [PipeOutcome.Up]). */
    fun failureText(o: PipeOutcome): String = when (o) {
        is PipeOutcome.Up -> RelayText.VIA_PHONE
        is PipeOutcome.Failed -> o.text
    }

    /** Vrai quand l'échec est celui d'un téléphone qui ne sait pas faire (pour proposer la mise à jour de CastBridge plutôt que de réessayer). */
    fun isOldPhone(o: PipeOutcome): Boolean = o is PipeOutcome.Failed && o.why == PipeWhy.OLD_PHONE
}
