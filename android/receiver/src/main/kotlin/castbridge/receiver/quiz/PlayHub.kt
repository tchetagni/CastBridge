package castbridge.receiver.quiz

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import castbridge.core.connect.Routes
import castbridge.core.net.HttpLite
import castbridge.core.owner.GateState
import castbridge.core.quiz.QuizHttp
import castbridge.core.quiz.online.HostEdition
import castbridge.core.quiz.online.OpenGate
import castbridge.core.quiz.online.PlayActivation
import castbridge.core.quiz.online.PlayErrors
import castbridge.core.quiz.online.PlayProof
import castbridge.core.quiz.online.PlayRelay
import castbridge.core.quiz.online.PlayTvName
import castbridge.core.relay.PipeNeed
import castbridge.core.relay.PipeOutcome
import castbridge.receiver.TvNet
import castbridge.core.quiz.online.TicketCache
import castbridge.core.quiz.online.PlayGate
import castbridge.core.quiz.online.PlayHttpTransport
import castbridge.core.quiz.online.PlayIntent
import castbridge.core.quiz.online.PlayRules
import castbridge.core.quiz.online.PlayTicketReply
import castbridge.core.quiz.online.PlayTile
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.QuizOnlineFlag
import castbridge.core.quiz.online.RelayAuthority
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.TransportFactory
import castbridge.core.tunnel.TunnelEnroll
import castbridge.receiver.ActivationCenter
import castbridge.receiver.ParentalHub
import castbridge.receiver.TvConnect
import castbridge.receiver.TvService
import castbridge.receiver.wallet.WalletHub
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Câblage Android du Quiz en ligne de CastBridge-TV (w20-05 POC, DESIGN-W20-AMENDEMENT § 2.6). AUCUNE décision ici : la porte, l'automate, les textes et les erreurs sont du cœur
 * (`PlayTvScreens.kt`, testé) ; ce fichier ne fait que brancher le réseau (ticket par `Routes`, transport `PlayHttpTransport`) et l'horloge. Vérifié par compilation seulement.
 *
 * Seule une TV activée, connectée à Internet, joue ; ses téléphones ne parlent qu'à elle (`/quiz`, servi par [relayHttp] via [RelayAuthority]).
 * Aucun secret n'est journalisé : ni jeton d'appareil, ni ticket, ni activation, ni jeton de siège.
 */
object PlayHub {
    private const val PREFS = "castbridge_quiz"
    private const val KEY_ENABLED = "online_enabled"
    private const val PROBE_TTL_MS = 60_000L

    // ------------------------------------------------------------------ interrupteur et porte

    /** L'interrupteur de l'utilisateur (réglages de la TV) : null = jamais touché (le défaut compilé, allumé, s'applique). */
    fun setting(ctx: Context): Boolean? = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let { if (it.contains(KEY_ENABLED)) it.getBoolean(KEY_ENABLED, true) else null }
    fun setSetting(ctx: Context, on: Boolean) { ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply() }
    /** L'ordre signé `flag.set` n'existe pas pour ce drapeau (liste close partagée avec le serveur, hors périmètre) : null. */
    fun flagOn(ctx: Context): Boolean = QuizOnlineFlag.enabled(setting(ctx), null, QuizOnlineFlag.COMPILED_DEFAULT)

    fun gatewayProxy() = TvNet.gatewayProxy()

    /**
     * La TV voit Internet par son réseau ou par le tuyau d'un téléphone : la vérité réseau UNIQUE de la TV ([TvNet.state], relay-R1), plus de définition propre au jeu (inventaire I-3).
     * Avant la première mesure la TV ne se dit pas hors ligne.
     */
    @Suppress("UNUSED_PARAMETER")
    fun hasInternet(ctx: Context): Boolean = TvNet.reachable()

    private fun edition(): HostEdition = PlayGate.editionOf(ActivationCenter.locked(), ActivationCenter.trial(), ActivationCenter.state() is GateState.Grace)

    /** La tuile « Partie Internet » : rien, une raison, ou disponible (toute la décision est dans [PlayGate]) ; sans Internet elle reste proposée si un téléphone synchronisé peut en donner (relay-R1). */
    fun tile(ctx: Context): PlayTile = PlayGate.tile(flagOn(ctx), edition(), hasInternet(ctx), ParentalHub.kidHomeActive(), ActivationCenter.clockSuspended(), serviceUp(),
        verifiableActivation = PlayActivation.pick(ActivationCenter.allActivations(), ActivationCenter.now()) != null,   // M-4 : la même règle que le service
        relay = if (hasInternet(ctx)) PlayRelay.UNKNOWN else TvNet.relayAvailability())

    /** « Partie par relais : liaison lente » (informatif, sans alarme) quand la TV n'a Internet que par le tuyau d'un téléphone, sinon null. */
    fun relayLine(): String? = TvNet.playRules().line

    /** La ligne de la demande de tuyau en cours (« Demande d'Internet au téléphone… », ou pourquoi aucun téléphone ne peut), sinon null. */
    fun requestLine(): String? = TvNet.requestLine()

    // ------------------------------------------------------------------ le service répond-il ?

    @Volatile private var probeUp: Boolean? = null
    @Volatile private var probeAt = 0L
    @Volatile private var probing = false

    /** Le sondage a appris que le service n'applique pas encore les révocations (`"revocations":"off"`) : le menu le dit (audit Opus, honnêteté). */
    @Volatile var revocationsOff = false; private set

    /** Dernier sondage du service (null : pas encore sondé ⇒ on essaie). */
    fun serviceUp(): Boolean? = probeUp

    /** Sonde `GET /play/.well-known/caps` (sans secret) par le chemin réseau de la TV, en arrière-plan ; [done] est appelé ensuite (sur le fil du sondage). */
    fun probe(force: Boolean = false, done: () -> Unit = {}) {
        if (probing || (!force && probeUp != null && SystemClock.elapsedRealtime() - probeAt < PROBE_TTL_MS)) { done(); return }
        probing = true
        Thread({
            try {
                // relay-R1 (J-R4) : l'utilisateur entre dans « Partie Internet » sans Internet et un téléphone synchronisé peut en donner : la TV lui demande un tuyau et l'attend avant de sonder
                // le service. Le Quiz qui s'ouvre seulement (force = false) ne demande rien : il n'y a pas encore d'intention de jouer en ligne.
                if (force && !TvNet.state().up && TvNet.relayAvailability() == PlayRelay.POSSIBLE) TvNet.ensure(PipeNeed.PLAY, force = true)
                val link = TvConnect.link
                val base = link?.state?.baseUrl?.takeIf { it.isNotBlank() }
                probeUp = if (link == null || base == null || !TvNet.state().up) null else try {      // sans réseau le service ne peut pas être jugé : « pas encore sondé », jamais « indisponible »
                    link.routes.call { p -> HttpLite(p, connectTimeoutMs = 8_000, readTimeoutMs = 8_000, userAgent = "CastBridge-TV").request("GET", "$base/play/.well-known/caps").let { r -> revocationsOff = r.body.contains("\"revocations\":\"off\""); r.code in 200..299 } }
                } catch (e: IOException) { false }
                probeAt = SystemClock.elapsedRealtime()
            } finally { probing = false }
            done()
        }, "play-probe").apply { isDaemon = true }.start()
    }

    // ------------------------------------------------------------------ ticket `cbp1` (jamais journalisé)

    /** H-3 : la clé d'installation de la TV (publique) est donnée à l'API, qui l'épingle ; la TV prouvera ensuite qu'elle la détient. Jamais la clé privée, qui ne quitte pas l'appareil. */
    private fun installKeyJson(): String = WalletHub.installSigner()?.let { ""","installKey":"${it.publicKeyBase64}"""" } ?: ""

    /** Un ticket frais, par la route réseau de la TV puis par la passerelle du téléphone ([Routes]). Bloquant : jamais sur le fil principal. */
    fun fetchTicket(): PlayTicketReply {
        val link = TvConnect.link ?: return PlayTicketReply.Refused(PlayErrors.NO_INTERNET)
        val token = link.state.deviceToken ?: return PlayTicketReply.Refused(PlayErrors.ticketHttp(401))
        val base = link.state.baseUrl.takeIf { it.isNotBlank() } ?: return PlayTicketReply.Refused(PlayErrors.NO_INTERNET)
        val deviceCode = runCatching { ActivationCenter.deviceCode }.getOrNull() ?: return PlayTicketReply.Refused(PlayRules.MSG_ACTIVATE)
        return try {
            val r = link.routes.call { p ->
                HttpLite(p, connectTimeoutMs = 10_000, readTimeoutMs = 15_000, userAgent = "CastBridge-TV")
                    .request("POST", "$base/api/v1/play/ticket", """{"deviceCode":"$deviceCode"${installKeyJson()}}""", mapOf("Authorization" to "Bearer $token"))
            }
            PlayTicketReply.parse(r.code, r.body)
        } catch (e: IOException) { PlayTicketReply.Refused(PlayErrors.NO_INTERNET) }
    }

    // ------------------------------------------------------------------ la session

    @Volatile var session: PlayTvSession? = null; private set
    @Volatile var relay: RelayAuthority? = null; private set
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "play-session").apply { isDaemon = true } }
    private var ticking: ScheduledFuture<*>? = null
    private val mono = { SystemClock.elapsedRealtime() }

    /** Une partie Internet est en cours : `/quiz` est servi par le relais et non par la salle locale. */
    fun active(): Boolean = relay?.let { !it.closed() } == true

    /** Les routes `/quiz` des téléphones de la maison pendant une partie Internet (même JSON, mêmes codes que la salle locale). */
    val relayHttp = QuizHttp(QuizHttp.AuthoritySource { relay?.takeIf { !it.closed() } })

    /**
     * Ouvre la partie (créer ou rejoindre). Tout se passe en arrière-plan ; [onResult] reçoit null si la session est partie (l'écran suit ensuite `session`), ou le texte du refus.
     * Le premier ticket est demandé ici pour que son refus soit dit clairement ; la session en redemande un à chaque NOUVELLE session de service.
     */
    private val gate = OpenGate()

    fun start(ctx: Context, intent: PlayIntent, onResult: (String?) -> Unit) {
        stop()   // annule aussi toute ouverture encore en cours (génération)
        val gen = gate.begin()
        worker.execute {
            try {
                // relay-R1 (J-R4) : sans Internet, la TV demande un tuyau au téléphone synchronisé et l'attend (borné) ; elle dit pourquoi si personne ne peut
                if (!TvNet.state().up) {
                    val o = TvNet.ensure(PipeNeed.PLAY, force = true)      // l'utilisateur vient d'appuyer : une demande tout de suite
                    if (!gate.isCurrent(gen)) return@execute
                    if (o is PipeOutcome.Failed) { onResult(o.text); return@execute }
                }
                val first = fetchTicket()
                if (!gate.isCurrent(gen)) return@execute   // M-5 : « Retour » pendant l'attente du ticket : rien n'est ouvert, aucune salle fantôme
                if (first !is PlayTicketReply.Ok) { onResult((first as PlayTicketReply.Refused).text); return@execute }
                val activation = PlayActivation.pick(ActivationCenter.allActivations(), ActivationCenter.now())?.encode()   // M-4 : une activation que le service sait vérifier
                if (activation == null) { onResult(PlayRules.MSG_ACTIVATE); return@execute }
                val link = TvConnect.link ?: run { onResult(PlayErrors.NO_INTERNET); return@execute }
                val routes = link.routes
                val base = link.state.baseUrl
                var pending: String? = first.ticket
                val cache = TicketCache(mono) { (fetchTicket() as? PlayTicketReply.Ok)?.ticket }.also { it.adopt(first.ticket) }
                val ticket = { synchronized(this) { pending?.also { pending = null } } ?: cache.fresh() }   // création / entrée : toujours un ticket neuf (usage unique)
                val signer = WalletHub.installSigner()
                // M-6 : le service juge le ticket à la création de la session seulement : il part au premier POST, plus à chaque envoi (aucun renouvellement, aucun appel d'API en cours de partie)
                // relay-R1 (§ 2.5) : le chemin vient de la vérité réseau unique (direct : le réseau de la TV ; via_relay : le tuyau du téléphone) ; en mode relais les délais du transport suivent la
                // latence mesurée, chaque requête porte l'en-tête informatif `X-CB-Via: relay`, la courbe de réouverture est plus espacée et les téléphones de la maison gardent la fenêtre de réponse
                // du service plus la latence (bornée). Rien ne change au protocole du service.
                val factory = TransportFactory { t ->
                    val rules = TvNet.playRules()
                    PlayHttpTransport(base, t, proxy = { pipeProxy(routes) }, clock = mono, connectTimeoutMs = rules.connectTimeoutMs, postReadTimeoutMs = rules.postReadTimeoutMs,
                        ticketOnEveryPost = false, viaRelay = { TvNet.viaRelay() })
                }
                val s = PlayTvSession(clock = mono, transports = factory, ticket = ticket, via = { if (TvNet.viaRelay()) null else routes.lastVia }, tvHasNetwork = { hasInternet(ctx) },
                    curveSec = TvNet.playRules().curveSec,
                    resumeTicket = { cache.reusable() },   // une reprise réutilise le ticket courant (< 9 min)
                    prover = { t, a -> signer?.let { PlayProof.build(it::sign, it.publicKeyBase64, t, a) } })   // H-3 : preuve de possession de la clé d'installation
                val deviceHash = TvConnect.hash(ActivationCenter.deviceCode)
                s.start(deviceHash, activation, when (intent) {
                    PlayIntent.Create -> PlayTvSession.Intent.Create(name = null, mode = "DUEL")   // la TV ne joue pas : elle héberge ; ses téléphones jouent par elle
                    is PlayIntent.Join -> PlayTvSession.Intent.Join(RoomCode.normalize(intent.code) ?: intent.code, PlayTvName.of(android.os.Build.MODEL))   // C-1 : la TV envoie un nom
                })
                if (!gate.isCurrent(gen)) { runCatching { s.stop() }; return@execute }   // annulée pendant l'ouverture : on ferme ce qu'on vient d'ouvrir
                session = s
                relay = RelayAuthority(s, mono, extraWindowMs = { TvNet.playRules().answerExtraMs })
                ticking?.cancel(false)
                var beat = 0
                ticking = worker.scheduleWithFixedDelay({
                    runCatching { session?.tick() }
                    // la partie tient la demande de tuyau vivante tant que la TV est sans Internet (bail renouvelé, redemandé au rythme de la règle de TvNet), jamais plus d'une fois toutes les 5 s
                    if (++beat % 16 == 0) runCatching { if (!TvNet.state().up) TvNet.need(PipeNeed.PLAY) }
                }, 300, 300, TimeUnit.MILLISECONDS)
                TvConnect.feature("quiz_online", "menu")
                onResult(null)
            } catch (e: Exception) {
                if (gate.isCurrent(gen)) onResult(PlayErrors.GENERIC)
            }
        }
    }

    /** Le chemin du transport : la vérité réseau unique décide (direct : null ; via_relay : le tuyau) ; sans Internet un instant, le dernier chemin qui a marché. */
    private fun pipeProxy(routes: Routes): java.net.Proxy? = when (TvNet.state()) {
        castbridge.core.connect.NetState.DIRECT -> null
        castbridge.core.connect.NetState.VIA_RELAY -> gatewayProxy()
        castbridge.core.connect.NetState.NONE -> if (routes.lastVia == Routes.Via.GATEWAY) gatewayProxy() else null
    }

    /** Commandes de l'hôte : « Commencer » = duel, hôte automatique (le service enchaîne les questions), départ. */
    fun hostStart() {
        val a = session?.authority ?: return
        a.act(null, "mode", null, null, "DUEL"); a.act(null, "autohost", null, null, null); a.act(null, "start", null, null, null)
    }

    /** Arrêt volontaire : retour au menu. */
    fun stop() {
        gate.cancel()
        ticking?.cancel(false); ticking = null
        runCatching { session?.stop() }
        session = null; relay = null
    }

    /** Pour `GET /api/quiz` du téléphone pendant une partie Internet : le code de la salle Internet (celui de la TV, jamais celui d'un autre) et le nombre de téléphones d'ici. */
    fun statusJson(url: String): String {
        val r = relay ?: return """{"open":false}"""
        val code = session?.authority?.code?.let { RoomCode.display(it) } ?: ""
        return """{"open":true,"code":"$code","stage":"LOBBY","mode":"DUEL","players":${r.phoneCount()},"max":${RelayAuthority.MAX_PHONES},"url":${castbridge.core.tv.ReceiverServer.q(url)},"online":true}"""
    }
}
