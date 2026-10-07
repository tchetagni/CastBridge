package castbridge.core.owner

import castbridge.core.lots.Right
import castbridge.core.trust.TvDeviceRequest
import castbridge.core.tv.activation.KeyScan
import java.security.MessageDigest

/**
 * Obtenir la clé, puis l'installer (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md § 2 B et § 3 F3/F4). Pur : l'écran du téléphone ne fait que montrer le [Model] et exécuter les [Effect].
 *
 * Les sources de la clé ([Source]) :
 *  - [Source.CONSOLE] (B1) : la console propriétaire de ce build (`SuperAdmin.enabled`) émet la clé sur la demande lue, puis la rend : elle est installée aussitôt (le propriétaire a déjà touché « Installer sur la TV ») ;
 *  - [Source.SERVER] (B2) : « Demander l'activation à CastBridge » : états et écran PRÉPARÉS derrière [ServerActivationRequests], éteinte (route serveur non décidée) : aucune requête n'en sort ;
 *  - [Source.PASTE] (B3) : la clé reçue (WhatsApp, e-mail) collée, ou trouvée dans le presse-papiers à la reprise de l'écran : PROPOSÉE ([Model.clipboard]), jamais installée sans un geste ;
 *  - [Source.FILE] (B3) : un fichier texte choisi dans le sélecteur du système (la clé est cherchée dedans, comme sur la TV : [KeyScan]).
 *
 * Avant l'envoi, le téléphone dit ce qu'est la clé (essai ou production, durée, pour cette TV ou une autre) à partir de la demande d'appareil lue ; ce n'est qu'un avis : la TV vérifie la
 * signature, le matériel et la fenêtre de 48 h, et a le dernier mot (son horloge peut différer). Aucune clé, aucune demande ni aucun code dans un `toString` ni dans un texte (ACT-NF2).
 */
object KeyAcquisition {
    /** Une clé de moins de 20 caractères n'en est pas une (comme l'ancien bouton « Envoyer »). */
    const val MIN_KEY_CHARS = 20
    /** Le plus gros corps que la route verrouillée de la TV accepte (16 Kio, `LockedActivationApi.MAX_BODY`). */
    const val MAX_KEY_CHARS = 16_384
    /** Un texte plus long (presse-papiers, champ) n'est jamais lu pour y chercher une clé. */
    const val MAX_CLIPBOARD_CHARS = 65_536
    const val MAX_FILE_BYTES = KeyScan.MAX_FILE_BYTES
    /** L'envoi de la clé à la TV (HTTP ou Bluetooth) rend un résultat ou échoue en moins de 30 s. */
    const val INSTALL_MS = 30_000L
    const val SERVER_POLL_MS = 5_000L
    const val SERVER_WAIT_MS = 30 * 60_000L
    private const val DAY_MS = 24L * 3600 * 1000

    const val NOTICE_TITLE = "TV activée"
    const val SERVER_BUTTON = "Demander l'activation à CastBridge"
    const val NOT_A_KEY = "Ce texte ne contient pas de clé d'activation : collez la clé reçue en entier (elle commence par « cbx1. »)."
    const val NEED_TV = "Joignez d'abord la TV avec son code : la clé sera installée dès qu'elle sera jointe."
    const val LINK_LOST = "La liaison avec la TV s'est interrompue : saisissez à nouveau le code pour la rétablir ; la clé reste prête."
    const val TIMED_OUT = "La TV n'a pas répondu à temps : vérifiez qu'elle est toujours sur son écran d'activation, puis réessayez."
    const val UNREACHABLE = "La TV n'a pas répondu : vérifiez qu'elle est toujours sur son écran d'activation, puis réessayez."
    const val NO_FILE = "Aucun fichier choisi."
    const val FILE_TOO_BIG = "Ce fichier est trop gros pour contenir une clé (256 Kio au plus)."
    const val SERVER_WAIT_TIMEOUT = "Toujours en attente de CastBridge : réessayez plus tard ou utilisez une autre voie."

    /** « L'activation par le serveur » : la route (`POST /api/v1/tv/activation-requests`) n'est pas décidée (DESIGN-ACTIVATION-SIMPLE § 6.1) ; tant que c'est faux, rien n'est envoyé. */
    object ServerActivationRequests { const val ENABLED = false }

    enum class Source { CONSOLE, SERVER, PASTE, FILE }

    /** Les sources proposées, dans l'ordre : la console seulement si ce build l'a, le serveur seulement si sa capacité est allumée, coller et fichier toujours. */
    fun offers(consolePresent: Boolean, serverEnabled: Boolean = ServerActivationRequests.ENABLED): List<Source> =
        listOfNotNull(Source.CONSOLE.takeIf { consolePresent }, Source.SERVER.takeIf { serverEnabled }, Source.PASTE, Source.FILE)

    // ------------------------------------------------------------------ ce qu'est une clé

    sealed class Check {
        /** Une activation faite pour cette TV (ou dont la cible ne peut pas être vérifiée sans la demande). [usageDays] : sa durée, null = illimitée ; [maybeExpired] : la fenêtre de 48 h semble close. */
        data class Valid(val kind: ActivationKind, val usageDays: Long?, val maybeExpired: Boolean) : Check()
        /** Une activation d'une AUTRE TV : jamais envoyée. */
        object OtherTv : Check() { override fun toString() = "OtherTv" }
        /** Un texte `cbx1` qui n'est pas une activation pour une TV (ordre, commande, texte altéré). */
        object NotAnActivation : Check() { override fun toString() = "NotAnActivation" }
        /** Clé groupée ou compacte : seule la TV peut la lire. */
        object Unknown : Check() { override fun toString() = "Unknown" }
    }

    fun installable(c: Check): Boolean = c is Check.Valid || c === Check.Unknown

    /** Une clé trouvée dans un texte. Jamais imprimée. */
    data class Pick(val key: String, val check: Check) {
        val installable: Boolean get() = installable(check)
        override fun toString() = "Pick(••••, $check)"
    }

    fun assess(key: String, request: TvDeviceRequest?, now: Long): Check {
        if (!key.startsWith("cbx1.")) return Check.Unknown
        val act = Activation.decode(key)?.takeIf { it.subject == Subject.TV } ?: return Check.NotAnActivation
        if (request != null && !DeviceIdentity.matches(act.factors, act.k, Fingerprints(request.factors.toMap()))) return Check.OtherTv
        val days = act.rights.filterIsInstance<Right.Usage>().firstOrNull()?.let { (it.endsAt - it.startsAt) / DAY_MS }
        return Check.Valid(act.kind, days, maybeExpired = now > act.notAfter)
    }

    /** La phrase sous le champ de la clé. */
    fun describe(c: Check, request: TvDeviceRequest?): String = when (c) {
        is Check.Valid -> {
            val what = if (c.kind == ActivationKind.TRIAL) "Clé d'essai" else "Clé de production"
            val d = c.usageDays
            val len = if (d == null) (if (c.kind == ActivationKind.TRIAL) "" else " illimitée") else " de $d jour${if (d > 1) "s" else ""}"
            (if (request != null) "$what$len, faite pour cette TV." else "$what$len (la TV vérifiera qu'elle est bien pour elle).") +
                if (c.maybeExpired) " Elle est peut-être périmée (une clé s'installe dans les 48 h qui suivent son émission) : la TV tranchera." else ""
        }
        Check.OtherTv -> "Cette clé est celle d'une autre TV, pas de celle-ci (code d'appareil ${request?.code ?: "de cette TV"}) : demandez la clé de ce code."
        Check.NotAnActivation -> "Ce texte n'est pas une clé d'activation pour une TV."
        Check.Unknown -> "Clé d'un autre format : la TV la vérifiera."
    }

    /**
     * La clé que [text] contient (un message, un e-mail, un fichier), ou null. La clé faite pour cette TV passe avant les autres (une clé d'une autre TV placée avant ne gêne pas) ; une clé
     * `cbx1` non périmée avant une périmée ; une clé que seule la TV peut lire avant une clé refusée. [tokensOnly] : seulement les jetons `cbx1` (le presse-papiers).
     */
    fun pick(text: String?, request: TvDeviceRequest?, now: Long, tokensOnly: Boolean = false): Pick? {
        if (text.isNullOrBlank() || text.length > MAX_CLIPBOARD_CHARS) return null
        val picks = KeyScan.candidates(text)
            .filter { it.length in MIN_KEY_CHARS..MAX_KEY_CHARS && (!tokensOnly || it.startsWith("cbx1.")) }
            .map { Pick(it, assess(it, request, now)) }
        return picks.firstOrNull { (it.check as? Check.Valid)?.maybeExpired == false } ?: picks.firstOrNull { it.check is Check.Valid }
            ?: picks.firstOrNull { it.check === Check.Unknown } ?: picks.firstOrNull()
    }

    private fun fingerprint(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    fun noticeText(tvName: String) = "$tvName est activée."

    /** Le résultat de l'envoi HTTP de la clé ([ActivationSend.sendLan]) en événement. Le Bluetooth de repli, quand le Wi-Fi ne répond plus, est décidé par l'exécutant avant. */
    fun resultOf(r: ActivationSend.Result, now: Long): Event.Result = when {
        r.ok -> Event.Result(ResultKind.OK, r.message, now)
        r.pinRefused -> Event.Result(ResultKind.CODE_REFUSED, "", now)
        r.linkDown -> Event.Result(ResultKind.UNREACHABLE, "", now)
        else -> Event.Result(ResultKind.REFUSED, r.message, now)
    }

    /** Le résultat de l'envoi par Bluetooth ([castbridge.core.owner.OwnerChannelClient.Answer]) en événement. */
    fun resultOfBluetooth(ok: Boolean, message: String, now: Long): Event.Result = Event.Result(if (ok) ResultKind.OK else ResultKind.REFUSED, message, now)

    fun serverLine(s: Server): String = when (s) {
        Server.Disabled -> ""
        Server.Idle -> "Demandez l'activation à CastBridge : une fois approuvée, la clé est installée toute seule."
        Server.Sending -> "Envoi de la demande à CastBridge…"
        is Server.Waiting -> "Demande n° ${s.number} envoyée, en attente de l'approbation de CastBridge."
        is Server.Failed -> s.message
    }

    // ------------------------------------------------------------------ l'état

    /** La TV jointe. [alreadyLinked] : ce téléphone est déjà lié (de confiance) à elle, « Ajouter ma TV » n'est pas reproposé. */
    data class Tv(val name: String, val route: ActivationRoutePlan.Route, val alreadyLinked: Boolean = false)

    sealed class Phase {
        object Choosing : Phase() { override fun toString() = "Choosing" }
        data class Installing(val since: Long, val source: Source) : Phase()
        /** [staged] : clé reçue par Bluetooth, la TV attend encore « Valider la clé » : pas encore activée. */
        data class Installed(val text: String, val staged: Boolean) : Phase()
        /** [codeRefused] : la TV a refusé le code (l'écran redemande les 6 chiffres). */
        data class Failed(val text: String, val codeRefused: Boolean) : Phase()
    }

    sealed class Server {
        object Disabled : Server() { override fun toString() = "Disabled" }
        object Idle : Server() { override fun toString() = "Idle" }
        object Sending : Server() { override fun toString() = "Sending" }
        data class Waiting(val number: String, val since: Long, val lastPoll: Long) : Server()
        data class Failed(val message: String) : Server()
    }

    data class Model(
        val serverEnabled: Boolean = ServerActivationRequests.ENABLED,
        val tv: Tv? = null,
        val request: TvDeviceRequest? = null,
        /** La clé dans le champ (collée, d'un fichier, de la console, du serveur). */
        val pick: Pick? = null,
        val source: Source? = null,
        /** L'usager a donné le geste (« Installer ») : la clé part dès que la TV est jointe. */
        val confirmed: Boolean = false,
        /** Une clé vue dans le presse-papiers, proposée (jamais installée seule). */
        val clipboard: Pick? = null,
        /** Les clés déjà écartées ou refusées, par empreinte : on ne les repropose pas à chaque reprise de l'écran. */
        val dismissed: Set<String> = emptySet(),
        val message: String? = null,
        val phase: Phase = Phase.Choosing,
        val server: Server = if (serverEnabled) Server.Idle else Server.Disabled,
        val notified: Boolean = false,
        val offerAddTv: Boolean = false,
    ) {
        /** Le bouton « Installer la clé » est actif. */
        fun canInstall(): Boolean = pick?.installable == true && phase !is Phase.Installing && phase !is Phase.Installed
        override fun toString() = "Model(tv=${tv?.name}, phase=${phase::class.simpleName}, key=${if (pick != null) "présente" else "absente"}, server=$server, notified=$notified)"
    }

    enum class ResultKind { OK, REFUSED, CODE_REFUSED, UNREACHABLE }

    sealed class Event {
        data class TvFound(val tv: Tv, val request: TvDeviceRequest?, val now: Long) : Event() { override fun toString() = "TvFound(${tv.name})" }
        object TvLost : Event() { override fun toString() = "TvLost" }
        data class Pasted(val text: String, val now: Long) : Event() { override fun toString() = "Pasted(••••)" }
        data class FilePicked(val text: String?, val now: Long) : Event() { override fun toString() = "FilePicked(••••)" }
        /** À la reprise de l'écran : ce que le presse-papiers contient (null = vide ou illisible). */
        data class Clipboard(val text: String?, val now: Long) : Event() { override fun toString() = "Clipboard(••••)" }
        /** Le geste sur la proposition : « Installer cette clé ». */
        data class ClipboardAccepted(val now: Long) : Event()
        object ClipboardDismissed : Event() { override fun toString() = "ClipboardDismissed" }
        data class ConsoleKey(val token: String, val now: Long) : Event() { override fun toString() = "ConsoleKey(••••)" }
        /** Le geste « Installer la clé ». */
        data class Install(val now: Long) : Event()
        data class Result(val kind: ResultKind, val message: String, val now: Long) : Event()
        data class Tick(val now: Long) : Event()
        data class ServerRequest(val now: Long) : Event()
        data class ServerSent(val number: String, val now: Long) : Event()
        data class ServerKey(val token: String, val now: Long) : Event() { override fun toString() = "ServerKey(••••)" }
        data class ServerFailed(val message: String) : Event()
    }

    sealed class Effect {
        /** Envoyer [key] à la TV jointe (HTTP avec le code, ou Bluetooth) puis rapporter [Event.Result]. */
        data class Install(val key: String, val source: Source) : Effect() { override fun toString() = "Install(••••, $source)" }
        data class Notify(val title: String, val text: String) : Effect()
        data class SendServerRequest(val request: TvDeviceRequest) : Effect() { override fun toString() = "SendServerRequest(••••)" }
        data class PollServer(val number: String) : Effect()
    }

    data class Step(val model: Model, val effects: List<Effect> = emptyList())

    fun reduce(m: Model, e: Event): Step = when (e) {
        is Event.TvFound -> {
            // a TV found after an activation is a new session (the next TV of the agent): nothing of the previous one stays but the keys already set aside
            val base = if (m.phase is Phase.Installed) Model(serverEnabled = m.serverEnabled, dismissed = m.dismissed) else m
            val repick = base.pick?.let { Pick(it.key, assess(it.key, e.request, e.now)) }
            val joined = base.copy(tv = e.tv, request = e.request, pick = repick)
            if (joined.confirmed && joined.canInstall()) startInstall(joined, e.now) else Step(joined)
        }
        Event.TvLost -> when (m.phase) {
            is Phase.Installing -> Step(m.copy(tv = null, phase = Phase.Failed(LINK_LOST, codeRefused = false)))
            else -> Step(m.copy(tv = null))
        }
        is Event.Pasted -> pasted(m, e.text, e.now, Source.PASTE)
        is Event.FilePicked -> { val t = e.text; if (t == null) Step(m.copy(message = NO_FILE)) else pasted(m, t, e.now, Source.FILE, noKey = KeyScan.NO_KEY) }
        is Event.Clipboard -> clipboard(m, e)
        is Event.ClipboardAccepted -> m.clipboard?.let { c -> useKey(m.copy(clipboard = null), c, Source.PASTE, e.now) } ?: Step(m)
        Event.ClipboardDismissed -> Step(m.copy(clipboard = null, dismissed = m.dismissed + listOfNotNull(m.clipboard?.let { fingerprint(it.key) })))
        is Event.ConsoleKey -> {
            val p = pick(e.token, m.request, e.now)
            if (p == null || !p.installable) Step(m.copy(message = p?.let { describe(it.check, m.request) } ?: NOT_A_KEY)) else useKey(m, p, Source.CONSOLE, e.now)
        }
        is Event.Install -> install(m, e.now)
        is Event.Result -> result(m, e)
        is Event.Tick -> tick(m, e.now)
        is Event.ServerRequest -> when {
            !m.serverEnabled || m.server !is Server.Idle || m.tv == null || m.request == null -> Step(m)
            else -> Step(m.copy(server = Server.Sending), listOf(Effect.SendServerRequest(m.request)))
        }
        is Event.ServerSent -> if (m.serverEnabled && m.server is Server.Sending) Step(m.copy(server = Server.Waiting(e.number, e.now, e.now))) else Step(m)
        is Event.ServerKey -> if (m.serverEnabled && m.server is Server.Waiting) {
            val p = pick(e.token, m.request, e.now)
            if (p == null || !p.installable) Step(m.copy(server = Server.Failed(p?.let { describe(it.check, m.request) } ?: NOT_A_KEY)))
            else useKey(m.copy(server = Server.Idle), p, Source.SERVER, e.now)
        } else Step(m)
        is Event.ServerFailed -> if (m.serverEnabled && (m.server is Server.Sending || m.server is Server.Waiting)) Step(m.copy(server = Server.Failed(e.message))) else Step(m)
    }

    /** Un texte collé ou lu dans un fichier : la clé qu'il contient devient celle du champ ; rien n'est installé. */
    private fun pasted(m: Model, text: String, now: Long, source: Source, noKey: String = NOT_A_KEY): Step {
        if (m.phase is Phase.Installing) return Step(m)                       // pas de nouvelle clé pendant un envoi
        if (text.isBlank()) return Step(m.copy(pick = null, source = null, confirmed = false, message = null, phase = Phase.Choosing))
        val p = pick(text, m.request, now) ?: return Step(m.copy(pick = null, source = null, confirmed = false, message = noKey, phase = Phase.Choosing))
        return Step(m.copy(pick = p, source = source, confirmed = false, message = describe(p.check, m.request), phase = Phase.Choosing))
    }

    private fun clipboard(m: Model, e: Event.Clipboard): Step {
        if (m.phase is Phase.Installed || m.phase is Phase.Installing) return Step(m)
        val p = pick(e.text, m.request, e.now, tokensOnly = true)
        if (p == null || p.check === Check.NotAnActivation) return Step(m.copy(clipboard = null))
        val fp = fingerprint(p.key)
        if (fp in m.dismissed || m.pick?.key == p.key) return Step(m.copy(clipboard = null))
        if (!p.installable) return Step(m.copy(clipboard = null, dismissed = m.dismissed + fp, message = describe(p.check, m.request)))
        return Step(m.copy(clipboard = p))
    }

    /** Une clé voulue par l'usager (proposition acceptée, console, serveur) : elle est installée tout de suite si la TV est jointe, sinon dès qu'elle l'est. */
    private fun useKey(m: Model, p: Pick, source: Source, now: Long): Step {
        val next = m.copy(pick = p, source = source, confirmed = true, message = describe(p.check, m.request))
        return if (next.tv != null) startInstall(next, now) else Step(next.copy(message = NEED_TV))
    }

    private fun install(m: Model, now: Long): Step {
        val p = m.pick ?: return Step(m.copy(message = NOT_A_KEY))
        if (m.phase is Phase.Installing || m.phase is Phase.Installed) return Step(m)
        if (!p.installable) return Step(m.copy(message = describe(p.check, m.request)))
        val confirmed = m.copy(confirmed = true)
        return if (confirmed.tv == null) Step(confirmed.copy(message = NEED_TV)) else startInstall(confirmed, now)
    }

    private fun startInstall(m: Model, now: Long): Step {
        val p = m.pick!!
        val source = m.source ?: Source.PASTE
        return Step(m.copy(phase = Phase.Installing(now, source), message = null), listOf(Effect.Install(p.key, source)))
    }

    private fun result(m: Model, e: Event.Result): Step {
        if (m.phase !is Phase.Installing) return Step(m)
        return when (e.kind) {
            ResultKind.OK -> {
                val o = ActivationScreenState.sendOutcome(true, e.message)
                val tv = m.tv
                val activated = !o.staged
                val fx = if (activated && !m.notified) listOf<Effect>(Effect.Notify(NOTICE_TITLE, noticeText(tv?.name ?: "CastBridge-TV"))) else emptyList()
                Step(m.copy(phase = Phase.Installed(o.text, o.staged), notified = m.notified || activated, offerAddTv = activated && tv?.alreadyLinked != true, confirmed = false), fx)
            }
            // a failure ends the gesture: the key is never sent again by itself (a reconnection does not retry), the next « Installer » is a new one
            ResultKind.REFUSED -> Step(m.copy(phase = Phase.Failed(ActivationScreenState.sendOutcome(false, e.message).text, codeRefused = false), confirmed = false))
            ResultKind.CODE_REFUSED -> Step(m.copy(phase = Phase.Failed(ActivationSend.WRONG_PIN_TEXT, codeRefused = true), confirmed = false))
            ResultKind.UNREACHABLE -> Step(m.copy(phase = Phase.Failed(UNREACHABLE, codeRefused = false), confirmed = false))
        }
    }

    private fun tick(m: Model, now: Long): Step {
        var next = m
        val effects = ArrayList<Effect>()
        val ph = next.phase
        if (ph is Phase.Installing && now - ph.since >= INSTALL_MS) next = next.copy(phase = Phase.Failed(TIMED_OUT, codeRefused = false), confirmed = false)
        val s = next.server
        if (next.serverEnabled && s is Server.Waiting) {
            if (now - s.since >= SERVER_WAIT_MS) next = next.copy(server = Server.Failed(SERVER_WAIT_TIMEOUT))
            else if (now - s.lastPoll >= SERVER_POLL_MS) { next = next.copy(server = s.copy(lastPoll = now)); effects += Effect.PollServer(s.number) }
        }
        return Step(next, effects)
    }
}
