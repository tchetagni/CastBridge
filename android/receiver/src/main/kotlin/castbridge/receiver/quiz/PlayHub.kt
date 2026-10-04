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
import castbridge.core.quiz.online.PlayErrors
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

    fun gatewayProxy() = TvService.running?.gateway?.proxy()

    /** La TV voit Internet par son réseau ou par la passerelle Bluetooth d'un téléphone. */
    fun hasInternet(ctx: Context): Boolean {
        if (gatewayProxy() != null) return true
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        return runCatching { cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }.getOrDefault(false)
    }

    private fun edition(): HostEdition = PlayGate.editionOf(ActivationCenter.locked(), ActivationCenter.trial(), ActivationCenter.state() is GateState.Grace)

    /** La tuile « Partie Internet » : rien, une raison, ou disponible (toute la décision est dans [PlayGate]). */
    fun tile(ctx: Context): PlayTile = PlayGate.tile(flagOn(ctx), edition(), hasInternet(ctx), ParentalHub.kidHomeActive(), ActivationCenter.clockSuspended(), serviceUp())

    // ------------------------------------------------------------------ le service répond-il ?

    @Volatile private var probeUp: Boolean? = null
    @Volatile private var probeAt = 0L
    @Volatile private var probing = false

    /** Dernier sondage du service (null : pas encore sondé ⇒ on essaie). */
    fun serviceUp(): Boolean? = probeUp

    /** Sonde `GET /play/.well-known/caps` (sans secret) par le chemin réseau de la TV, en arrière-plan ; [done] est appelé ensuite (sur le fil du sondage). */
    fun probe(force: Boolean = false, done: () -> Unit = {}) {
        if (probing || (!force && probeUp != null && SystemClock.elapsedRealtime() - probeAt < PROBE_TTL_MS)) { done(); return }
        probing = true
        Thread({
            try {
                val link = TvConnect.link
                val base = link?.state?.baseUrl?.takeIf { it.isNotBlank() }
                probeUp = if (link == null || base == null) null else try {
                    link.routes.call { p -> HttpLite(p, connectTimeoutMs = 8_000, readTimeoutMs = 8_000, userAgent = "CastBridge-TV").request("GET", "$base/play/.well-known/caps").code in 200..299 }
                } catch (e: IOException) { false }
                probeAt = SystemClock.elapsedRealtime()
            } finally { probing = false }
            done()
        }, "play-probe").apply { isDaemon = true }.start()
    }

    // ------------------------------------------------------------------ ticket `cbp1` (jamais journalisé)

    /** Un ticket frais, par la route réseau de la TV puis par la passerelle du téléphone ([Routes]). Bloquant : jamais sur le fil principal. */
    fun fetchTicket(): PlayTicketReply {
        val link = TvConnect.link ?: return PlayTicketReply.Refused(PlayErrors.NO_INTERNET)
        val token = link.state.deviceToken ?: return PlayTicketReply.Refused(PlayErrors.ticketHttp(401))
        val base = link.state.baseUrl.takeIf { it.isNotBlank() } ?: return PlayTicketReply.Refused(PlayErrors.NO_INTERNET)
        val deviceCode = runCatching { ActivationCenter.deviceCode }.getOrNull() ?: return PlayTicketReply.Refused(PlayRules.MSG_ACTIVATE)
        return try {
            val r = link.routes.call { p ->
                HttpLite(p, connectTimeoutMs = 10_000, readTimeoutMs = 15_000, userAgent = "CastBridge-TV")
                    .request("POST", "$base/api/v1/play/ticket", """{"deviceCode":"$deviceCode"}""", mapOf("Authorization" to "Bearer $token"))
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
    fun start(ctx: Context, intent: PlayIntent, onResult: (String?) -> Unit) {
        stop()
        worker.execute {
            try {
                val first = fetchTicket()
                if (first !is PlayTicketReply.Ok) { onResult((first as PlayTicketReply.Refused).text); return@execute }
                val activation = TunnelEnroll.pickActivation(ActivationCenter.allActivations(), ActivationCenter.now())?.encode()
                if (activation == null) { onResult(PlayRules.MSG_ACTIVATE); return@execute }
                val link = TvConnect.link ?: run { onResult(PlayErrors.NO_INTERNET); return@execute }
                val routes = link.routes
                val base = link.state.baseUrl
                var pending: String? = first.ticket
                val ticket = { synchronized(this) { pending?.also { pending = null } } ?: (fetchTicket() as? PlayTicketReply.Ok)?.ticket }
                val factory = TransportFactory { t ->
                    PlayHttpTransport(base, t, proxy = { if (routes.lastVia == Routes.Via.GATEWAY) gatewayProxy() else null }, clock = mono,
                        refreshTicket = { (fetchTicket() as? PlayTicketReply.Ok)?.ticket })
                }
                val s = PlayTvSession(clock = mono, transports = factory, ticket = ticket, via = { routes.lastVia }, tvHasNetwork = { hasInternet(ctx) })
                val deviceHash = TvConnect.hash(ActivationCenter.deviceCode)
                s.start(deviceHash, activation, when (intent) {
                    PlayIntent.Create -> PlayTvSession.Intent.Create(name = null, mode = "DUEL")   // la TV ne joue pas : elle héberge ; ses téléphones jouent par elle
                    is PlayIntent.Join -> PlayTvSession.Intent.Join(RoomCode.normalize(intent.code) ?: intent.code, null)
                })
                session = s
                relay = RelayAuthority(s, mono)
                ticking?.cancel(false)
                ticking = worker.scheduleWithFixedDelay({ runCatching { session?.tick() } }, 300, 300, TimeUnit.MILLISECONDS)
                TvConnect.feature("quiz_online", "menu")
                onResult(null)
            } catch (e: Exception) {
                onResult(PlayErrors.GENERIC)
            }
        }
    }

    /** Commandes de l'hôte : « Commencer » = duel, hôte automatique (le service enchaîne les questions), départ. */
    fun hostStart() {
        val a = session?.authority ?: return
        a.act(null, "mode", null, null, "DUEL"); a.act(null, "autohost", null, null, null); a.act(null, "start", null, null, null)
    }

    /** Arrêt volontaire : retour au menu. */
    fun stop() {
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
