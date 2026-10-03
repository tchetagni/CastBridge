package castbridge.core.link

import castbridge.core.tv.WifiDirect

/**
 * L'automate du contrôleur Wi-Fi Direct du téléphone (CastBridge), pur : demander le groupe à la TV par Bluetooth (CBTN) → le rejoindre → sonder
 * `GET /api/hello` à l'adresse du propriétaire du groupe → le remettre à la file d'envoi → le rendre après 30 s de file vide. L'exécutant Android
 * (`sender/AutoWifiDirect.kt`) ne fait que les [Effect] et rapporte les [Event] ; l'horloge est celle des événements (testée en temps simulé).
 *
 * Le mot de passe du groupe ne vit que dans l'effet [Effect.Join] (mémoire, le temps de la jonction) : aucun état ne le garde, rien ne l'écrit.
 */
object WdClient {
    /** La TV attend jusqu'à 8 s la création du groupe (R/TvService.linkInfo) : 15 s pour la réponse CBTN, connexion Bluetooth comprise. */
    const val REQUEST_TIMEOUT_MS = 15_000L
    /** File vide depuis 30 s : le téléphone quitte le groupe et la TV le supprime. */
    const val IDLE_RELEASE_MS = 30_000L
    const val PROBE_TRIES = 3

    enum class Fail { TV_REFUSED, TV_NO_GROUP, REQUEST_TIMEOUT, JOIN_DENIED, JOIN_TIMEOUT, BAD_CREDENTIALS, PROBE_FAILED, LOST }

    sealed class State {
        object Off : State() { override fun toString() = "Off" }
        data class Requesting(val at: Long) : State()
        /** [tvIp]/[port] : l'adresse que la TV a dite (le mot de passe n'est PAS ici). */
        data class Joining(val at: Long, val timeoutMs: Long, val tvIp: String?, val port: Int) : State()
        data class Probing(val base: String, val at: Long, val tries: Int) : State()
        data class Up(val base: String, val since: Long, val lastUse: Long) : State()
        data class Failed(val fail: Fail, val at: Long, val detail: String? = null) : State()
    }

    sealed class Event {
        data class Start(val now: Long, val method: JoinMethod) : Event()
        /** La TV a répondu à CBTN avec son groupe. */
        class Creds(val ssid: String, val pass: String, val tvIp: String?, val port: Int, val now: Long, val method: JoinMethod) : Event() {
            override fun toString() = "Creds($ssid, ••••••, $tvIp, $port, $now, $method)"
        }
        /** La TV a répondu sans groupe ; [err] = sa cause ([WifiDirect.Err]). */
        data class NoGroup(val err: String?, val now: Long) : Event()
        /** CBTN refusé (PIN, TV ancienne) ou lien Bluetooth cassé. */
        data class TvRefused(val now: Long) : Event()
        data class Joined(val goIp: String?, val now: Long) : Event()
        data class JoinFailed(val fail: Fail, val now: Long) : Event()
        data class ProbeOk(val now: Long) : Event()
        data class ProbeFail(val now: Long) : Event()
        data class Lost(val now: Long) : Event()
        /** Horloge ; [busy] = la file d'envoi a du travail (le groupe sert). */
        data class Tick(val now: Long, val busy: Boolean) : Event()
        data class Release(val now: Long) : Event()
    }

    sealed class Effect {
        object RequestGroup : Effect() { override fun toString() = "RequestGroup" }
        class Join(val ssid: String, val pass: String, val method: JoinMethod) : Effect() {
            override fun equals(other: Any?) = other is Join && other.ssid == ssid && other.pass == pass && other.method == method
            override fun hashCode() = ssid.hashCode() * 31 + method.hashCode()
            override fun toString() = "Join($ssid, ••••••, $method)"
        }
        data class Probe(val base: String) : Effect()
        object Leave : Effect() { override fun toString() = "Leave" }
        /** CBTN avec [castbridge.core.tv.BtProtocol.WD_RELEASE] : la TV supprime son groupe automatique. */
        object ReleaseTv : Effect() { override fun toString() = "ReleaseTv" }
        data class Failure(val fail: Fail) : Effect()
        object Success : Effect() { override fun toString() = "Success" }
    }

    data class Step(val state: State, val effects: List<Effect> = emptyList())

    fun reduce(s: State, e: Event): Step = when (e) {
        is Event.Start -> when (s) {
            State.Off, is State.Failed -> Step(State.Requesting(e.now), listOf(Effect.RequestGroup))
            is State.Up -> Step(s.copy(lastUse = e.now))
            else -> Step(s)                                                   // une demande est déjà en cours : jamais deux
        }
        is Event.Creds -> if (s is State.Requesting)
            Step(State.Joining(e.now, WdJoin.joinTimeoutMs(e.method), e.tvIp, e.port), listOf(Effect.Join(e.ssid, e.pass, e.method)))
            else Step(s)
        is Event.NoGroup -> if (s is State.Requesting) fail(Fail.TV_NO_GROUP, e.now, e.err, leave = false) else Step(s)
        is Event.TvRefused -> if (s is State.Requesting) fail(Fail.TV_REFUSED, e.now, leave = false) else Step(s)
        is Event.Joined -> if (s is State.Joining) {
            val b = WdAddress.base(e.goIp, s.tvIp, s.port)
            Step(State.Probing(b, e.now, 0), listOf(Effect.Probe(b)))
        } else Step(s)
        is Event.JoinFailed -> if (s is State.Joining) fail(e.fail, e.now) else Step(s)
        is Event.ProbeOk -> if (s is State.Probing) Step(State.Up(s.base, e.now, e.now), listOf(Effect.Success)) else Step(s)
        is Event.ProbeFail -> if (s is State.Probing) {
            if (s.tries + 1 >= PROBE_TRIES) fail(Fail.PROBE_FAILED, e.now) else Step(s.copy(tries = s.tries + 1), listOf(Effect.Probe(s.base)))
        } else Step(s)
        is Event.Lost -> when (s) {
            is State.Up, is State.Probing -> fail(Fail.LOST, e.now)
            is State.Joining -> fail(Fail.JOIN_TIMEOUT, e.now)
            else -> Step(s)
        }
        is Event.Tick -> when (s) {
            is State.Requesting -> if (e.now - s.at >= REQUEST_TIMEOUT_MS) fail(Fail.REQUEST_TIMEOUT, e.now, leave = false) else Step(s)
            is State.Joining -> if (e.now - s.at >= s.timeoutMs) fail(Fail.JOIN_TIMEOUT, e.now) else Step(s)
            is State.Up -> when {
                e.busy -> Step(s.copy(lastUse = e.now))
                e.now - s.lastUse >= IDLE_RELEASE_MS -> Step(State.Off, listOf(Effect.Leave, Effect.ReleaseTv))
                else -> Step(s)
            }
            else -> Step(s)
        }
        is Event.Release -> when (s) {
            State.Off -> Step(s)
            is State.Requesting, is State.Failed -> Step(State.Off, listOf(Effect.ReleaseTv))
            else -> Step(State.Off, listOf(Effect.Leave, Effect.ReleaseTv))
        }
    }

    private fun fail(f: Fail, now: Long, detail: String? = null, leave: Boolean = true) =
        Step(State.Failed(f, now, detail), (if (leave) listOf<Effect>(Effect.Leave) else emptyList()) + Effect.Failure(f))

    /** Une phrase, en français, pour la ligne d'état. */
    fun explain(f: Fail, detail: String? = null): String = when (f) {
        Fail.TV_REFUSED -> "La TV n'a pas répondu à la demande de Wi-Fi Direct."
        Fail.TV_NO_GROUP -> when (detail) {
            WifiDirect.Err.WIFI_OFF -> "Le Wi-Fi de la TV est éteint."
            WifiDirect.Err.PERMISSION -> "Wi-Fi Direct non autorisé sur la TV (autorisation « Appareils à proximité »)."
            WifiDirect.Err.UNSUPPORTED -> "Cette TV ne gère pas le Wi-Fi Direct."
            WifiDirect.Err.TRIAL -> "Version d'essai de la TV : pas de Wi-Fi Direct."
            WifiDirect.Err.POLICY -> "La TV garde son Wi-Fi : Wi-Fi Direct non lancé."
            else -> "La TV n'a pas pu créer son réseau Wi-Fi Direct."
        }
        Fail.REQUEST_TIMEOUT -> "La TV n'a pas répondu à temps (Wi-Fi Direct)."
        Fail.JOIN_DENIED -> "Connexion Wi-Fi Direct refusée sur le téléphone."
        Fail.JOIN_TIMEOUT -> "Le téléphone n'a pas pu rejoindre le réseau de la TV."
        Fail.BAD_CREDENTIALS -> "Le réseau Wi-Fi Direct de la TV a refusé la connexion."
        Fail.PROBE_FAILED -> "La TV ne répond pas sur le Wi-Fi Direct."
        Fail.LOST -> "Liaison Wi-Fi Direct perdue."
    }
}

/**
 * Le groupe AUTOMATIQUE de la TV (créé pour un téléphone par CBTN) : supprimé quand le téléphone l'a rendu, quand il est parti (plus aucun client
 * après le délai de jonction), ou quand il ne sert plus. Jamais pendant une réception. Le groupe que l'utilisateur a activé au MENU (`wd_enabled`) n'est
 * pas concerné. Le mot de passe disparaît avec le groupe (un nouveau à chaque création).
 */
object WdGroupLease {
    /** Le téléphone a 45 s pour rejoindre après la création. */
    const val JOIN_GRACE_MS = 45_000L
    /** Nombre de clients inconnu : 30 s sans usage. */
    const val IDLE_MS = 30_000L
    /** Un téléphone encore associé mais muet depuis 10 minutes (application tuée) : le groupe part. */
    const val STALE_MS = 10 * 60_000L

    /**
     * [clients] : nombre de téléphones associés (`WifiP2pGroup.clientList`), null = inconnu. [lastUse] : dernière réception HTTP ([LeaseBusy]).
     * [holders] : téléphones qui ont demandé le groupe et ne l'ont pas rendu (bail PAR téléphone, [TvWdHost]) ; 0 = tous l'ont rendu.
     */
    data class Facts(val auto: Boolean, val createdAt: Long, val lastUse: Long, val activeTransfers: Int, val clients: Int?, val holders: Int, val now: Long)

    fun shouldRemove(f: Facts): Boolean {
        if (!f.auto || f.activeTransfers > 0) return false
        if (f.holders <= 0) return true
        if (f.now - f.createdAt < JOIN_GRACE_MS) return false
        if (f.clients == 0) return true
        val idle = f.now - maxOf(f.lastUse, f.createdAt)
        return idle >= (if (f.clients == null) IDLE_MS else STALE_MS)
    }
}
