package castbridge.core.remote

import castbridge.core.quiz.Json
import castbridge.core.tv.OpenTvReply
import castbridge.core.tv.OpenTvScreen
import castbridge.core.trust.TvCredential
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

/**
 * « Ouvrir sur la TV » côté téléphone (docs/REMOTE.md) : le bouton de l'onglet « CastBridge TV », la touche « TV » de la télécommande, le raccourci de l'icône
 * et le lien `castbridge://open-tv` font tous la même chose : demander à CastBridge-TV de passer devant l'application qui est à l'écran de la TV.
 *
 * TV connue ? route (Wi-Fi, puis Bluetooth, puis tunnel de l'API) ? réponse ? → UNE ligne ([OpenTvOutcome.line]). Au plus 10 s d'attente en tout ([OpenTvFlow.BUDGET_MS]),
 * une seule tentative par route, jamais de réessai (un code faux répété verrouillerait la TV une minute). Pur : les liaisons et l'horloge sont données.
 */

/** Les voies pour joindre la TV, dans l'ordre d'essai. [capMs] : le plus long qu'une voie peut prendre. */
enum class OpenTvRoute(val label: String, val capMs: Long) {
    /** `POST /api/tv/open` sur le réseau commun (ou le groupe Wi-Fi Direct). */
    LAN("Wi-Fi", 7_000),
    /** La commande `open` du canal télécommande CBTR de la TV (aucune adresse IP nécessaire ; un téléphone de confiance n'a pas de code à donner). */
    BLUETOOTH("Bluetooth", 10_000),
    /** `POST /api/tv/open` à travers le tunnel Bluetooth de l'API (passerelle du téléphone), quand elle tourne. */
    TUNNEL("Bluetooth (API)", 10_000),
}

/** Ce que la TV a répondu : le statut (HTTP ou ligne Bluetooth), sa réponse lue si c'en est une, et ses mots si elle refuse. */
data class OpenTvAnswer(val status: Int, val reply: OpenTvReply?, val message: String? = null)

/** Une voie vers la TV. Une seule requête, bornée par [timeoutMs] (connexion et réponse comprises). */
interface OpenTvLink {
    val route: OpenTvRoute

    /** Rend ce que la TV a répondu ; lève [IOException] quand elle n'a pas répondu, [Unusable] quand c'est le TÉLÉPHONE qui ne peut pas (Bluetooth éteint, autorisation refusée). */
    @Throws(IOException::class)
    fun open(screen: OpenTvScreen?, timeoutMs: Long): OpenTvAnswer

    /** Cette voie ne peut pas servir à cause du téléphone ; [message] est une phrase complète, en français, à montrer. */
    class Unusable(message: String) : IOException(message)
}

sealed class OpenTvOutcome {
    /** La ligne unique à montrer (jamais une trace d'erreur). */
    abstract val line: String
    /** Vrai pour ce qui demande à l'utilisateur de faire quelque chose d'anormal (ligne rouge) ; faux pour une réussite ou une information. */
    open val problem: Boolean get() = true
    /** CastBridge-TV était déjà devant : le bouton de l'accueil ouvre alors la télécommande. */
    open val alreadyFront: Boolean get() = false

    /** Aucune TV associée à ce téléphone. */
    object NoTv : OpenTvOutcome() { override val line get() = OpenTvTexts.NO_TV }
    /** Une TV connue, mais aucune voie utilisable depuis le téléphone ([why] : phrase complète, ou null). */
    data class NoRoute(val why: String?) : OpenTvOutcome() { override val line get() = why ?: OpenTvTexts.NO_ROUTE }
    data class Opened(val route: OpenTvRoute, val how: String, val already: Boolean) : OpenTvOutcome() {
        override val line get() = OpenTvTexts.OPENED
        override val problem get() = false
        override val alreadyFront get() = already
    }
    /** La TV demande l'autorisation « Afficher par-dessus les autres applications » (son MENU la propose une fois). */
    data class NeedsPermission(val route: OpenTvRoute) : OpenTvOutcome() { override val line get() = OpenTvTexts.NEEDS_PERMISSION; override val problem get() = false }
    /** La TV affiche une notification à ouvrir avec sa télécommande. */
    data class Notified(val route: OpenTvRoute) : OpenTvOutcome() { override val line get() = OpenTvTexts.NOTIFIED; override val problem get() = false }
    data class Failed(val message: String?) : OpenTvOutcome() { override val line get() = message?.takeIf { it.isNotBlank() } ?: OpenTvTexts.FAILED }
    /** La TV ne connaît pas la route (CastBridge-TV plus ancien). */
    object TvTooOld : OpenTvOutcome() { override val line get() = OpenTvTexts.TV_TOO_OLD }
    /** La TV n'a pas reconnu ce téléphone (code ou jeton). */
    object Refused : OpenTvOutcome() { override val line get() = OpenTvTexts.REFUSED }
    /**
     * Aucune voie n'a obtenu de réponse : la TV est éteinte (ou son Wi-Fi et son Bluetooth le sont). [phoneHint] : ce qui, côté téléphone, a aussi empêché une voie de servir
     * (Bluetooth éteint, autorisation refusée…), phrase complète : la ligne ne met pas tout sur le dos de la TV.
     */
    data class Unreachable(val phoneHint: String? = null) : OpenTvOutcome() { override val line get() = OpenTvTexts.UNREACHABLE + (phoneHint?.takeIf { it.isNotBlank() }?.let { ". $it" } ?: "") }
}

class OpenTvFlow(private val now: () -> Long = System::currentTimeMillis, private val budgetMs: Long = BUDGET_MS) {
    /**
     * @param tvKnown une TV est associée à ce téléphone (de confiance, ou avec son code).
     * @param links les voies utilisables depuis ce téléphone ; l'ordre est imposé ici (Wi-Fi, Bluetooth, tunnel), une voie donnée deux fois n'est essayée qu'une.
     */
    fun run(tvKnown: Boolean, links: List<OpenTvLink>, screen: OpenTvScreen? = null): OpenTvOutcome {
        if (!tvKnown) return OpenTvOutcome.NoTv
        val ordered = links.distinctBy { it.route }.sortedBy { it.route.ordinal }
        if (ordered.isEmpty()) return OpenTvOutcome.NoRoute(null)
        val start = now()
        var silent = false; var refused = false; var crashed = false
        var unusable: String? = null
        for (l in ordered) {
            val left = budgetMs - (now() - start)
            if (left < MIN_ATTEMPT_MS) break                // a few hundred ms cannot open a Bluetooth link: the honest answer is the end of the wait
            val a = try { l.open(screen, minOf(left, l.route.capMs)) }
                catch (e: OpenTvLink.Unusable) { unusable = unusable ?: e.message; continue }
                catch (e: IOException) { silent = true; continue }
                catch (e: RuntimeException) { crashed = true; continue }       // a bug or a revoked permission must never crash the screen
            when {
                // the TV answered: another route would only reach the same TV, so the first answer is the answer
                a.status in 200..299 -> return a.reply?.let { decide(l.route, it) } ?: OpenTvOutcome.Failed(a.message)
                // not this route's credential (an expired token, a wrong code): never again on this route, and the Bluetooth way of a trusted phone needs none
                a.status == 401 || a.status == 403 -> refused = true
                a.status == 404 || a.status == 405 || a.status == 501 -> return OpenTvOutcome.TvTooOld
                else -> return OpenTvOutcome.Failed(a.message)
            }
        }
        return when {
            refused -> OpenTvOutcome.Refused
            silent -> OpenTvOutcome.Unreachable(unusable)
            unusable != null -> OpenTvOutcome.NoRoute(unusable)
            crashed -> OpenTvOutcome.Failed(null)
            else -> OpenTvOutcome.Unreachable()
        }
    }

    private fun decide(route: OpenTvRoute, r: OpenTvReply): OpenTvOutcome = when {
        r.opened -> OpenTvOutcome.Opened(route, r.how, r.already || r.how == OpenTvReply.HOW_ALREADY)
        r.needs == OpenTvReply.NEEDS_OVERLAY -> OpenTvOutcome.NeedsPermission(route)
        r.how == "fullscreen" -> OpenTvOutcome.Notified(route)
        else -> OpenTvOutcome.Failed(null)
    }

    companion object {
        /** Au plus 10 s d'attente en tout, toutes voies comprises. */
        const val BUDGET_MS = 10_000L
        /** Sous ce reste, on n'ouvre pas une voie de plus. */
        const val MIN_ATTEMPT_MS = 800L
    }
}

/** Les mots (une ligne chacun, « CastBridge » pour le téléphone et « CastBridge-TV » pour la TV). */
object OpenTvTexts {
    const val BUTTON = "Ouvrir sur la TV"
    /** La touche de la télécommande (comme un bouton YouTube ou Netflix). */
    const val KEY = "TV"
    const val KEY_DESCRIPTION = "Ouvrir CastBridge-TV sur la TV"
    /** Raccourci de l'icône de l'application (appui long). */
    const val SHORTCUT = "Ouvrir CastBridge-TV"
    const val WORKING = "Ouverture de CastBridge-TV…"
    const val OPENED = "CastBridge-TV est à l'écran"
    const val NEEDS_PERMISSION = "La TV demande une autorisation : MENU › Afficher par-dessus"
    const val UNREACHABLE = "La TV ne répond pas : allumez-la (le Wi-Fi ou le Bluetooth de la TV est éteint)"
    const val NO_TV = "Aucune TV n'est associée : touchez « Ajouter ma TV » dans l'onglet CastBridge TV"
    const val NO_ROUTE = "Aucune liaison avec la TV : allumez le Wi-Fi ou le Bluetooth du téléphone"
    const val NOTIFIED = "La TV affiche une notification « CastBridge-TV » : validez-la avec la télécommande de la TV"
    const val FAILED = "La TV n'a pas pu ouvrir CastBridge-TV toute seule : ouvrez-le avec la télécommande de la TV"
    const val TV_TOO_OLD = "Cette TV ne connaît pas encore ce raccourci : mettez CastBridge-TV à jour"
    const val REFUSED = "La TV ne reconnaît pas ce téléphone : réassociez-la (Ajouter ma TV) ou saisissez son code"
}

/**
 * Quelles voies bâtir pour la TV connue, d'après ce que le téléphone en sait : la règle qui garde la TV de se verrouiller. Un identifiant inutilisable (vide, tronqué, jeton
 * expiré) ne part JAMAIS (la TV le compterait comme un code faux : verrou d'une minute) ; une TV qui ne connaît pas ce téléphone et dont on n'a pas le code n'est pas contactée du tout.
 * [trusted] : la TV connaît ce téléphone (« Ajouter ma TV ») : elle reconnaît son adresse Bluetooth, la voie Bluetooth n'a pas besoin de code.
 */
object OpenTvRoutes {
    /** [needsCode] : rien à essayer tant que le code de la TV n'est pas saisi (la ligne le dit). */
    data class Choice(val routes: List<OpenTvRoute>, val needsCode: Boolean)

    fun choose(trusted: Boolean, credential: String?, hasLan: Boolean, hasBluetooth: Boolean, hasTunnel: Boolean): Choice {
        val usable = castbridge.core.trust.TvAuth.isUsable(credential)
        if (!trusted && !usable) return Choice(emptyList(), needsCode = true)
        return Choice(buildList {
            if (hasLan && usable) add(OpenTvRoute.LAN)
            if (hasBluetooth) add(OpenTvRoute.BLUETOOTH)          // trusted: known by its address; otherwise the usable code goes into the handshake
            if (hasTunnel && usable) add(OpenTvRoute.TUNNEL)
        }, needsCode = false)
    }
}

/** Le fil : ce que le téléphone envoie et comment il lit n'importe quelle réponse. */
object OpenTvWire {
    /** Route HTTP de la TV (Wi-Fi, et tunnel Bluetooth de l'API). */
    const val PATH = RemoteApi.OPEN_TV_PATH
    /** Route de la même action sur le canal Bluetooth CBTR : la ligne `POST open?screen=…`. */
    const val BT_ROUTE = "open"

    fun query(screen: OpenTvScreen?): String = screen?.let { "screen=${it.wire}" } ?: ""
    fun path(screen: OpenTvScreen?): String = PATH + screen?.let { "?screen=${it.wire}" }.orEmpty()

    /** La demande sur une liaison de télécommande déjà ouverte et reconnue (Bluetooth CBTR : la ligne `POST open?screen=…`, réponse `200 {…}`). */
    fun send(t: RemoteTransport, screen: OpenTvScreen?): OpenTvAnswer {
        val r = t.send("POST", BT_ROUTE, query(screen))
        return answer(r.status, r.body)
    }

    /** Lit une réponse : la réponse de la TV si c'en est une (2xx), et ses mots (`message`, sinon `error`) pour un refus. */
    fun answer(status: Int, body: String): OpenTvAnswer =
        OpenTvAnswer(status, if (status in 200..299) OpenTvReply.parse(body) else null, message(body))

    private fun message(body: String): String? = runCatching {
        val o = Json.obj(body)
        (o["message"] as? String)?.takeIf { it.isNotBlank() } ?: (o["error"] as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

/**
 * La voie Wi-Fi (ou tunnel de l'API) : `POST /api/tv/open` avec le code ou le jeton du téléphone, sur une connexion qui n'existe que le temps de la demande.
 * Une connexion et une lecture bornées par le temps donné : jamais une attente sans fin, jamais un identifiant inutilisable envoyé (la TV le compterait comme un code faux).
 */
class OpenTvHttpLink(override val route: OpenTvRoute, private val base: String, private val credential: String?) : OpenTvLink {
    override fun open(screen: OpenTvScreen?, timeoutMs: Long): OpenTvAnswer {
        val auth = try { TvCredential.headerLine(credential)?.plus("\r\n").orEmpty() }
            catch (e: TvCredential.Missing) { return OpenTvAnswer(401, null, "no usable credential") }
        val u = URI(base)
        val host = u.host ?: throw IOException("adresse de la TV illisible")
        val port = if (u.port > 0) u.port else 80
        val total = timeoutMs.coerceAtLeast(1)
        val deadline = System.nanoTime() + total * 1_000_000
        val s = Socket()
        try {
            castbridge.core.net.BoundRoute.bind(host, s)
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), minOf(CONNECT_MS, total).toInt())
            s.soTimeout = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1).toInt()
            val req = "POST ${OpenTvWire.path(screen)} HTTP/1.1\r\nHost: $host:$port\r\n${auth}Content-Length: 0\r\nConnection: close\r\n\r\n"
            s.getOutputStream().apply { write(req.toByteArray(Charsets.UTF_8)); flush() }
            val r = Http1.readResponse(java.io.BufferedInputStream(s.getInputStream(), 4096))
            return OpenTvWire.answer(r.status, r.body)
        } finally { runCatching { s.close() } }
    }

    private companion object { const val CONNECT_MS = 1_500L }
}
