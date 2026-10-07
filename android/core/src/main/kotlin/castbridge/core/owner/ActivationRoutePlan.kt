package castbridge.core.owner

import castbridge.core.trust.TvDeviceRequest
import castbridge.core.tv.WdCode

/**
 * « Activer la TV » avec le seul code affiché sur la TV (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, ACT-F2, ACT-F3) : l'ordre dans lequel le téléphone cherche la TV,
 * la borne de chaque étape, la ligne d'état de chacune et, si rien ne marche, la cause voie par voie. Pur : aucun fil, aucune horloge, aucun Android. L'exécutant
 * (`sender/ActivationDriver`) lance les [Effect.Try], rapporte des [Event] et fait tourner l'horloge avec [Event.Tick] ; toutes les décisions sont ici.
 *
 *  1. [Route.LAN] (10 s) : une TV verrouillée annoncée sur le réseau local (mDNS `locked=1`) qui accepte le code ;
 *  2. [Route.GROUP] (20 s) : le groupe Wi-Fi Direct que la TV crée et dont le nom et le mot de passe dérivent du code ([WdCode]) ; le téléphone le rejoint seul (Android 10 et plus,
 *     `WifiNetworkSpecifier`, réseau local seulement : ses données mobiles restent) ; avant Android 10 l'usager le rejoint à la main ([Phase.ManualJoin]) ;
 *  3. [Route.BLUETOOTH] (20 s ; 90 s si Android demande de valider un appairage) : le canal d'activation Bluetooth, existant.
 *
 * Le code n'apparaît dans AUCUN texte, ni dans un `toString` (ACT-NF2) ; le mot de passe dérivé non plus, sauf la ligne de la jonction manuelle qui l'affiche exprès.
 */
object ActivationRoutePlan {
    enum class Route(val label: String) {
        LAN("Réseau local"), GROUP("Réseau de la TV (Wi-Fi Direct)"), BLUETOOTH("Bluetooth")
    }

    const val LAN_MS = 10_000L
    const val GROUP_MS = 20_000L
    const val BT_MS = 20_000L
    /** Android demande de valider un appairage sur les deux écrans : la validation d'un humain n'entre pas dans les 20 s. */
    const val BT_PAIRING_MS = 90_000L
    /** `WifiNetworkSpecifier` existe depuis Android 10 (API 29). */
    const val GROUP_MIN_API = 29
    const val UPDATE_LINE = "Mettez la TV à jour pour l'activation sans réseau."
    const val LINK_LOST_TEXT = "La liaison avec le réseau de la TV s'est interrompue (téléphone resté en arrière-plan ?) : touchez « Réessayer »."
    private const val TV_NAME = "CastBridge-TV"
    private const val MAX_NAME = 40

    fun boundMs(route: Route): Long = when (route) { Route.LAN -> LAN_MS; Route.GROUP -> GROUP_MS; Route.BLUETOOTH -> BT_MS }

    /** Le Bluetooth tel que le téléphone le voit AVANT d'essayer : [SEARCH] = allumé et autorisé, la TV sera cherchée ; [PAIRED] = une TV CastBridge est déjà appairée. */
    enum class Bt { SEARCH, PAIRED, OFF, NO_PERMISSION, NO_ADAPTER }

    /** Ce que le téléphone sait au départ. [wifiOn] : la radio Wi-Fi est allumée ; [onWifi] : le téléphone est connecté à un Wi-Fi. Le code n'est jamais imprimé. */
    data class Facts(val code: String, val api: Int, val wifiOn: Boolean, val onWifi: Boolean, val bt: Bt) {
        init { require(WdCode.isValid(code)) { "code de connexion : 6 chiffres attendus" } }
        override fun toString() = "Facts(••••••, api=$api, wifiOn=$wifiOn, onWifi=$onWifi, bt=$bt)"
    }

    // ------------------------------------------------------------------ les causes, en français

    /** Pourquoi une voie n'a pas abouti. [detail] : le nom de la TV (code refusé), les secondes (verrou, délai), jamais le code. */
    data class Cause(val kind: Kind, val detail: String? = null) {
        enum class Kind {
            WIFI_OFF, NOT_ON_WIFI, NOT_ANNOUNCED, CODE_REFUSED, LOCKED_OUT, TERMS, CLOSED, NEEDS_UPDATE, UNREADABLE,
            NOT_JOINED, MANUAL_SKIPPED, GROUP_PERMISSION, BUSY, TV_SILENT,
            BT_OFF, BT_PERMISSION, BT_NO_ADAPTER, BT_NO_TV, BT_NO_CHANNEL, BT_PAIRING, TIMEOUT
        }

        /** Une phrase complète pour la ligne de la voie. [ssid] : le nom du groupe d'activation de la TV. */
        fun text(route: Route, ssid: String): String = when (kind) {
            Kind.WIFI_OFF -> "Le Wi-Fi du téléphone est éteint : allumez-le pour utiliser cette voie."
            Kind.NOT_ON_WIFI -> "Le téléphone n'est connecté à aucun Wi-Fi."
            Kind.NOT_ANNOUNCED -> "Aucune TV à activer n'est annoncée sur ce réseau."
            Kind.CODE_REFUSED -> (detail?.let { "La TV « $it » a refusé ce code" } ?: "La TV a refusé ce code") + " : relisez les 6 chiffres affichés sur l'écran de la TV."
            Kind.LOCKED_OUT -> "Trop de codes faux : la TV attend ${detail?.toLongOrNull() ?: 60} s avant de reprendre."
            Kind.TERMS -> "Les conditions d'usage ne sont pas encore acceptées sur la TV : cochez la case sur son écran d'activation."
            Kind.CLOSED -> "La TV a fermé l'activation par le Wi-Fi pour quelques minutes (trop d'essais)."
            Kind.NEEDS_UPDATE -> "Cette CastBridge-TV est trop ancienne pour lire sa demande d'appareil. $UPDATE_LINE"
            Kind.UNREADABLE -> "La TV a répondu, mais sa demande d'appareil est illisible."
            Kind.NOT_JOINED -> "Le réseau « $ssid » n'a pas été rejoint : la TV doit afficher son écran d'activation avec son Wi-Fi allumé, et la connexion doit être acceptée sur le téléphone. $UPDATE_LINE"
            Kind.MANUAL_SKIPPED -> "Le réseau de la TV n'a pas été rejoint à la main."
            Kind.GROUP_PERMISSION -> "L'autorisation « Appareils à proximité » est refusée : donnez-la à CastBridge dans les réglages d'Android."
            Kind.BUSY -> "Une autre liaison Wi-Fi Direct de CastBridge est déjà en cours."
            Kind.TV_SILENT -> "Le réseau de la TV est rejoint, mais la TV n'y répond pas."
            Kind.BT_OFF -> "Le Bluetooth du téléphone est éteint."
            Kind.BT_PERMISSION -> "CastBridge n'a pas l'autorisation « Appareils à proximité » (Bluetooth)."
            Kind.BT_NO_ADAPTER -> "Ce téléphone n'a pas de Bluetooth."
            Kind.BT_NO_TV -> "Aucune TV CastBridge n'est appairée ni visible en Bluetooth."
            Kind.BT_NO_CHANNEL -> "La TV n'offre pas le canal d'activation Bluetooth : ouvrez CastBridge-TV sur son écran d'activation, ou mettez-la à jour."
            Kind.BT_PAIRING -> "L'appairage Bluetooth n'a pas été validé sur la TV et sur le téléphone."
            Kind.TIMEOUT -> "La TV n'a pas répondu en ${detail?.toLongOrNull() ?: (boundMs(route) / 1000)} s."
        }
    }

    /** Les messages d'erreur de `TvBluetooth.with` (nos propres textes) en causes typées ; un message inconnu n'est jamais recopié. */
    fun btCause(message: String?): Cause {
        val m = message.orEmpty()
        return when {
            m.contains("Bluetooth indisponible") -> Cause(Cause.Kind.BT_NO_ADAPTER)
            m.contains("Bluetooth désactivé") -> Cause(Cause.Kind.BT_OFF)
            m.contains("Appairage non terminé") -> Cause(Cause.Kind.BT_PAIRING)
            m.contains("canal d'activation") || m.startsWith("Connexion impossible") -> Cause(Cause.Kind.BT_NO_CHANNEL)
            else -> Cause(Cause.Kind.TIMEOUT)
        }
    }

    /** Un nom venu du réseau (mDNS, Bluetooth) n'est jamais montré brut : sans contrôles ni marques de sens d'écriture, sur une ligne, 40 caractères au plus. */
    fun cleanName(raw: String?): String {
        val s = (raw ?: "").filterNot { it.code == 0x200E || it.code == 0x200F || it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 }
            .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().replace(Regex("\\s+"), " ")
        return if (s.isEmpty()) TV_NAME else s.take(MAX_NAME)
    }

    // ------------------------------------------------------------------ le plan, l'état, les événements

    enum class Kind { TRY, SKIP, MANUAL }

    /** Une étape du plan : à essayer, à sauter (avec sa cause) ou à faire à la main (Android 9 et moins). */
    data class Planned(val route: Route, val kind: Kind, val cause: Cause? = null)

    /** La TV jointe : [route], [name] (nettoyé), [base] HTTP (réseau local ou groupe ; null en Bluetooth), sa demande d'appareil si elle a pu être lue, l'adresse Bluetooth. */
    data class Found(val route: Route, val name: String, val base: String?, val request: TvDeviceRequest?, val btAddress: String? = null, val note: Cause? = null) {
        override fun toString() = "Found($route, $name, ${base ?: "-"}, demande=${if (request != null) "lue" else "absente"})"
    }

    sealed class Phase {
        object Idle : Phase() { override fun toString() = "Idle" }
        data class Trying(val route: Route, val since: Long, val boundMs: Long) : Phase()
        /** Avant Android 10 : l'usager rejoint le Wi-Fi de la TV ; le mot de passe dérivé est montré exprès, jamais écrit dans un journal. */
        data class ManualJoin(val ssid: String, val passphrase: String) : Phase() { override fun toString() = "ManualJoin($ssid, ••••••)" }
        data class Connected(val found: Found) : Phase()
        data class Failed(val message: String) : Phase()
        object Cancelled : Phase() { override fun toString() = "Cancelled" }
    }

    data class Run(
        val facts: Facts, val plan: List<Planned>, val index: Int, val phase: Phase,
        /** La cause de chaque voie terminée (échec ou saut). */
        val causes: Map<Route, Cause> = emptyMap(),
        /** Ce que l'exécutant a appris d'une voie en cours (le code refusé par une TV…) : sert de cause si la borne arrive. */
        val observed: Map<Route, Cause> = emptyMap(),
    ) {
        private fun ssid() = WdCode.networkName(facts.code)
        private fun plannedKind(route: Route) = plan.firstOrNull { it.route == route }?.kind

        /** La ligne d'état de chaque voie, dans l'ordre : ce qui se passe, ce qui est fini, ce qui reste. */
        fun lines(): List<Line> {
            val connected = (phase as? Phase.Connected)?.found?.route
            return Route.values().map { route ->
                val cause = causes[route]
                when {
                    connected == route -> Line(route, LineState.DONE, "${route.label} : TV trouvée (${(phase as Phase.Connected).found.name}).")
                    connected != null && cause == null -> Line(route, LineState.UNUSED, "${route.label} : non nécessaire.")
                    cause != null -> Line(route, if (plannedKind(route) == Kind.SKIP) LineState.SKIPPED else LineState.FAILED, "${route.label} : ${cause.text(route, ssid())}")
                    phase is Phase.Trying && phase.route == route -> Line(route, LineState.ACTIVE, "${route.label} : ${activeText(route, phase)}")
                    phase is Phase.ManualJoin && route == Route.GROUP ->
                        Line(route, LineState.ASKING, "${route.label} : Connectez le téléphone au Wi-Fi « ${phase.ssid} » affiché sur la TV (mot de passe : ${phase.passphrase}), puis touchez « C'est fait ».")
                    else -> Line(route, LineState.WAITING, "${route.label} : en attente.")
                }
            }
        }

        private fun activeText(route: Route, p: Phase.Trying): String {
            val s = p.boundMs / 1000
            return when (route) {
                Route.LAN -> "recherche d'une TV à activer ($s s au plus)…"
                Route.GROUP -> "connexion au réseau « ${ssid()} » ($s s au plus)…"
                Route.BLUETOOTH -> if (p.boundMs > BT_MS) "appairage à valider sur la TV et sur le téléphone ($s s au plus)…" else "recherche de la TV ($s s au plus)…"
            }
        }

        /** La phrase du haut de l'écran. */
        fun headline(): String = when (val p = phase) {
            Phase.Idle -> "Saisissez le code affiché sur la TV."
            is Phase.Trying -> "Recherche de la TV…"
            is Phase.ManualJoin -> "Connectez le téléphone au Wi-Fi de la TV."
            is Phase.Connected -> "TV trouvée : ${p.found.name}"
            is Phase.Failed -> p.message
            Phase.Cancelled -> "Recherche annulée."
        }

        /** Une ligne en plus quand la TV jointe est trop ancienne pour l'activation sans réseau (CastBridge-TV 0.14.43 et avant), sinon null. */
        fun hint(): String? = (phase as? Phase.Connected)?.found?.note?.takeIf { it.kind == Cause.Kind.NEEDS_UPDATE }?.let { UPDATE_LINE }
    }

    enum class LineState { WAITING, ACTIVE, ASKING, DONE, FAILED, SKIPPED, UNUSED }
    data class Line(val route: Route, val state: LineState, val text: String)

    sealed class Effect {
        /** Lancer la voie ; [manual] : le téléphone a déjà été relié au Wi-Fi de la TV par l'usager (Android 9 et moins), il ne reste qu'à sonder. */
        data class Try(val route: Route, val boundMs: Long, val manual: Boolean = false) : Effect()
        /** Arrêter la voie et rendre ce qu'elle tenait (réseau demandé, liaison Bluetooth, boucle de sondage). */
        data class Abort(val route: Route) : Effect()
    }

    data class Step(val run: Run, val effects: List<Effect> = emptyList())

    sealed class Event {
        /** La voie en cours a joint la TV. */
        data class Reached(val found: Found) : Event()
        /** La voie en cours ne peut plus aboutir (refus définitif, réseau non rejoint) : la suivante commence tout de suite. */
        data class Failed(val route: Route, val cause: Cause, val at: Long) : Event()
        /** Ce que la voie en cours a vu (une TV a refusé le code…) : devient sa cause si la borne arrive. */
        data class Observed(val route: Route, val cause: Cause) : Event()
        /** Bluetooth : Android demande de valider un appairage, la borne passe à [BT_PAIRING_MS]. */
        data class Pairing(val at: Long) : Event()
        data class Tick(val now: Long) : Event()
        /** Avant Android 10 : l'usager dit « C'est fait » (il a rejoint le Wi-Fi de la TV). */
        data class UserJoined(val at: Long) : Event()
        data class UserSkipped(val at: Long) : Event()
        /** La liaison de [route] est tombée (le groupe Wi-Fi Direct est rendu par Android ou par la TV). */
        data class Lost(val route: Route, val at: Long) : Event()
        object Cancel : Event() { override fun toString() = "Cancel" }
    }

    /** Le plan d'après ce que le téléphone sait déjà : une voie impossible est sautée avec sa cause, jamais attendue. */
    fun plan(f: Facts): List<Planned> = listOf(
        when {
            !f.wifiOn -> Planned(Route.LAN, Kind.SKIP, Cause(Cause.Kind.WIFI_OFF))
            !f.onWifi -> Planned(Route.LAN, Kind.SKIP, Cause(Cause.Kind.NOT_ON_WIFI))
            else -> Planned(Route.LAN, Kind.TRY)
        },
        when {
            !f.wifiOn -> Planned(Route.GROUP, Kind.SKIP, Cause(Cause.Kind.WIFI_OFF))
            f.api < GROUP_MIN_API -> Planned(Route.GROUP, Kind.MANUAL)
            else -> Planned(Route.GROUP, Kind.TRY)
        },
        when (f.bt) {
            Bt.SEARCH, Bt.PAIRED -> Planned(Route.BLUETOOTH, Kind.TRY)
            Bt.OFF -> Planned(Route.BLUETOOTH, Kind.SKIP, Cause(Cause.Kind.BT_OFF))
            Bt.NO_PERMISSION -> Planned(Route.BLUETOOTH, Kind.SKIP, Cause(Cause.Kind.BT_PERMISSION))
            Bt.NO_ADAPTER -> Planned(Route.BLUETOOTH, Kind.SKIP, Cause(Cause.Kind.BT_NO_ADAPTER))
        },
    )

    fun start(facts: Facts, now: Long): Step = advance(Run(facts, plan(facts), -1, Phase.Idle), now, emptyList())

    fun reduce(run: Run, e: Event): Step {
        val ph = run.phase
        return when (e) {
            is Event.Reached -> if (ph is Phase.Trying && ph.route == e.found.route) Step(run.copy(phase = Phase.Connected(e.found))) else Step(run)
            is Event.Observed -> if (ph is Phase.Trying && ph.route == e.route) Step(run.copy(observed = run.observed + (e.route to e.cause))) else Step(run)
            is Event.Failed -> if (ph is Phase.Trying && ph.route == e.route) fail(run, ph.route, e.cause, e.at) else Step(run)
            is Event.Pairing -> if (ph is Phase.Trying && ph.route == Route.BLUETOOTH && ph.boundMs < BT_PAIRING_MS) Step(run.copy(phase = ph.copy(boundMs = BT_PAIRING_MS))) else Step(run)
            is Event.Tick ->
                if (ph is Phase.Trying && e.now - ph.since >= ph.boundMs) fail(run, ph.route, run.observed[ph.route] ?: timeoutCause(ph.route, ph.boundMs), e.now) else Step(run)
            is Event.UserJoined ->
                if (ph is Phase.ManualJoin) Step(run.copy(phase = Phase.Trying(Route.GROUP, e.at, GROUP_MS)), listOf(Effect.Try(Route.GROUP, GROUP_MS, manual = true))) else Step(run)
            is Event.UserSkipped ->
                if (ph is Phase.ManualJoin) advance(run.copy(causes = run.causes + (Route.GROUP to Cause(Cause.Kind.MANUAL_SKIPPED))), e.at, emptyList()) else Step(run)
            is Event.Lost -> when {
                ph is Phase.Connected && ph.found.route == e.route -> Step(run.copy(phase = Phase.Failed(LINK_LOST_TEXT)), listOf(Effect.Abort(e.route)))
                ph is Phase.Trying && ph.route == e.route -> fail(run, ph.route, Cause(Cause.Kind.NOT_JOINED), e.at)
                else -> Step(run)
            }
            Event.Cancel -> when (ph) {
                is Phase.Trying -> Step(run.copy(phase = Phase.Cancelled), listOf(Effect.Abort(ph.route)))
                is Phase.Failed, Phase.Cancelled -> Step(run)
                else -> Step(run.copy(phase = Phase.Cancelled))
            }
        }
    }

    private fun timeoutCause(route: Route, boundMs: Long): Cause = when (route) {
        Route.LAN -> Cause(Cause.Kind.NOT_ANNOUNCED)
        Route.GROUP -> Cause(Cause.Kind.NOT_JOINED)
        Route.BLUETOOTH -> Cause(Cause.Kind.TIMEOUT, (boundMs / 1000).toString())
    }

    private fun fail(run: Run, route: Route, cause: Cause, now: Long): Step = advance(run.copy(causes = run.causes + (route to cause)), now, listOf(Effect.Abort(route)))

    /** Passe à l'étape suivante : saute celles qui sont impossibles, lance la suivante, ou conclut par l'échec avec une cause par voie. */
    private fun advance(run: Run, now: Long, effects: List<Effect>): Step {
        var r = run
        var i = r.index + 1
        while (i < r.plan.size) {
            val p = r.plan[i]
            when (p.kind) {
                Kind.SKIP -> { r = r.copy(causes = r.causes + (p.route to p.cause!!)); i++ }
                Kind.TRY -> return Step(r.copy(index = i, phase = Phase.Trying(p.route, now, boundMs(p.route))), effects + Effect.Try(p.route, boundMs(p.route)))
                Kind.MANUAL -> return Step(r.copy(index = i, phase = Phase.ManualJoin(WdCode.networkName(r.facts.code), WdCode.passphrase(r.facts.code))), effects)
            }
        }
        return Step(r.copy(index = r.plan.size, phase = Phase.Failed(failureMessage(r))), effects)
    }

    // ------------------------------------------------------------------ sonder les TV : qui, quand, et ce que chaque réponse veut dire

    const val MAX_CANDIDATES = 3
    /** Une TV qui n'a pas répondu est réinterrogée toutes les 2,5 s jusqu'à la borne de la voie. */
    const val RE_PROBE_MS = 2_500L

    /** Une TV à interroger : annoncée par mDNS ou déjà liée à ce téléphone. [locked] : verrouillée (route d'activation, texte brut) ; sinon API complète (route JSON existante). */
    data class Candidate(val name: String, val base: String, val locked: Boolean)

    /**
     * Les TV qui reçoivent le code : les verrouillées annoncées d'abord, puis la TV déjà liée à ce téléphone ; adresses privées seulement ([ActivationSend.isLanTv]), une fois chacune, trois au plus.
     * Jamais une TV déjà activée qui n'est pas la sienne (un voisin sur le même réseau).
     */
    fun candidates(announced: List<Candidate>, linked: Candidate?): List<Candidate> =
        (announced.filter { it.locked } + listOfNotNull(linked)).filter { ActivationSend.isLanTv(it.base) }.distinctBy { it.base }.take(MAX_CANDIDATES)

    /**
     * La mémoire d'une recherche sur le réseau local : quelle TV a déjà répondu autre chose qu'un silence (elle n'est PLUS interrogée : un code refusé la verrouille 60 s, et chaque essai
     * compte dans le plafond de codes faux de la TV) et quand chacune a été interrogée. [answered] traduit la réponse en événement du plan.
     */
    class LanProbes {
        private val settled = HashSet<String>()
        private val probedAt = HashMap<String, Long>()

        /** Les TV à interroger maintenant : ni réglées, ni interrogées depuis moins de [RE_PROBE_MS]. */
        fun due(candidates: List<Candidate>, now: Long): List<Candidate> =
            candidates.filter { it.base !in settled && (probedAt[it.base]?.let { t -> now - t >= RE_PROBE_MS } ?: true) }.onEach { probedAt[it.base] = now }

        /** [reply] null = la TV n'a pas répondu (elle sera réinterrogée) ; sinon l'événement à rapporter (la TV est alors réglée). */
        fun answered(c: Candidate, reply: LockedRequestRoute.Reply?): Event? {
            if (reply == null) return null
            settled += c.base
            val name = cleanName(c.name)
            return when (reply) {
                is LockedRequestRoute.Reply.Request -> Event.Reached(Found(Route.LAN, name, c.base, reply.request))
                // une TV verrouillée sans la route (CastBridge-TV 0.14.43) est là : la clé peut lui être envoyée, le code sera vérifié à ce moment ; une TV liée sans la route ne se laisse pas lire
                LockedRequestRoute.Reply.RouteMissing ->
                    if (c.locked) Event.Reached(Found(Route.LAN, name, c.base, null, note = Cause(Cause.Kind.NEEDS_UPDATE))) else Event.Observed(Route.LAN, Cause(Cause.Kind.NEEDS_UPDATE))
                LockedRequestRoute.Reply.CodeRefused -> Event.Observed(Route.LAN, Cause(Cause.Kind.CODE_REFUSED, name))
                is LockedRequestRoute.Reply.LockedOut -> Event.Observed(Route.LAN, Cause(Cause.Kind.LOCKED_OUT, reply.seconds.toString()))
                LockedRequestRoute.Reply.TermsNotAccepted -> Event.Observed(Route.LAN, Cause(Cause.Kind.TERMS))
                LockedRequestRoute.Reply.Closed -> Event.Observed(Route.LAN, Cause(Cause.Kind.CLOSED))
                is LockedRequestRoute.Reply.Unreadable -> Event.Observed(Route.LAN, Cause(Cause.Kind.UNREADABLE))
            }
        }
    }

    /** La même traduction pour le groupe Wi-Fi Direct (192.168.49.1 : une seule TV possible). Un échec est DÉFINITIF (le réseau dérivé du code est rejoint : la TV qui refuse dit le dernier mot). */
    class GroupProbes {
        private var silent = 0

        /** [reply] null = la TV ne répond pas encore (le groupe vient d'être rejoint) ; [now] : l'horloge de l'exécutant. */
        fun answered(reply: LockedRequestRoute.Reply?, now: Long): Event? = when (reply) {
            null -> if (++silent == SILENT_TRIES) Event.Observed(Route.GROUP, Cause(Cause.Kind.TV_SILENT)) else null
            is LockedRequestRoute.Reply.Request -> Event.Reached(Found(Route.GROUP, cleanName(null), castbridge.core.tv.WifiDirect.BASE_URL, reply.request))
            LockedRequestRoute.Reply.RouteMissing -> Event.Reached(Found(Route.GROUP, cleanName(null), castbridge.core.tv.WifiDirect.BASE_URL, null, note = Cause(Cause.Kind.NEEDS_UPDATE)))
            LockedRequestRoute.Reply.CodeRefused -> Event.Failed(Route.GROUP, Cause(Cause.Kind.CODE_REFUSED), now)
            is LockedRequestRoute.Reply.LockedOut -> Event.Failed(Route.GROUP, Cause(Cause.Kind.LOCKED_OUT, reply.seconds.toString()), now)
            LockedRequestRoute.Reply.TermsNotAccepted -> Event.Failed(Route.GROUP, Cause(Cause.Kind.TERMS), now)
            LockedRequestRoute.Reply.Closed -> Event.Failed(Route.GROUP, Cause(Cause.Kind.CLOSED), now)
            is LockedRequestRoute.Reply.Unreadable -> Event.Failed(Route.GROUP, Cause(Cause.Kind.UNREADABLE), now)
        }

        companion object { const val SILENT_TRIES = 3 }
    }

    /** L'échec final : une phrase, une ligne par voie avec sa cause, puis quoi faire. */
    private fun failureMessage(r: Run): String {
        val ssid = WdCode.networkName(r.facts.code)
        val lines = Route.values().map { route -> "• ${route.label} : ${(r.causes[route] ?: timeoutCause(route, boundMs(route))).text(route, ssid)}" }
        val kinds = r.causes.values.map { it.kind }
        val advice = when {
            Cause.Kind.CODE_REFUSED in kinds -> "Relisez les 6 chiffres affichés sur la TV (écran d'activation de CastBridge-TV) et saisissez-les à nouveau."
            kinds.any { it == Cause.Kind.LOCKED_OUT || it == Cause.Kind.CLOSED } -> "Attendez quelques minutes avant de réessayer."
            Cause.Kind.TERMS in kinds -> "Acceptez les conditions d'usage sur la TV, puis touchez « Réessayer »."
            else -> "Vérifiez que la TV est allumée et affiche son écran d'activation de CastBridge-TV, puis touchez « Réessayer »."
        }
        return (listOf("La TV n'a pas pu être jointe avec ce code.") + lines + advice).joinToString("\n")
    }
}
