package castbridge.core.tv

import castbridge.core.trust.TrustPersistence

/*
 * R-19 : « La reprise automatique n'est pas stable même quand les conditions idéales reviennent ». L'écran voyait la TV « Connectée » pendant que
 * l'envoi restait à 0 % sur « TV introuvable ». Logique pure de ce qui rend la TV de nouveau joignable pour l'envoi ; Android n'est qu'un fil mince
 * (sender/TvDiscovery.kt, sender/UploadService.kt, sender/TransferQueue.kt).
 */

/** La reprise bornée : 1 s, 2 s, 5 s, 10 s, puis toutes les 15 s tant que l'envoi dure. */
object RetryBackoff {
    private val STEPS = longArrayOf(1_000, 2_000, 5_000, 10_000)
    const val STEADY_MS = 15_000L
    fun delayMs(attempt: Int): Long = if (attempt < 0) STEPS[0] else STEPS.getOrElse(attempt) { STEADY_MS }
}

/**
 * La découverte mDNS (NsdManager) d'un écran ou de l'envoi, sérialisée : `stopServiceDiscovery` est asynchrone, un `discoverServices` lancé avant
 * `onDiscoveryStopped` échoue (FAILURE_ALREADY_ACTIVE) ; un échec de démarrage est réessayé avec [RetryBackoff] tant que la découverte est voulue
 * (avant R-19 : un seul échec la tuait pour toute la durée de l'envoi). Android exécute les [Action] rendues ; une seule découverte à la fois.
 */
class DiscoverySupervisor {
    enum class Phase { IDLE, STARTING, RUNNING, STOPPING }
    sealed class Action {
        object None : Action() { override fun toString() = "None" }
        object Start : Action() { override fun toString() = "Start" }
        object Stop : Action() { override fun toString() = "Stop" }
        data class RetryIn(val ms: Long) : Action()
    }

    @Volatile var phase = Phase.IDLE; private set
    private var wanted = false
    private var restartPending = false
    private var retryScheduled = false
    private var failures = 0

    @Synchronized fun start(): Action {
        wanted = true
        if (phase != Phase.IDLE || retryScheduled) return Action.None
        phase = Phase.STARTING; return Action.Start
    }

    /** Stop, wait for the real stop, then start again (network change, « Rechercher »). Several requests in a row are one restart. */
    @Synchronized fun restart(): Action {
        wanted = true
        return when (phase) {
            Phase.IDLE -> { retryScheduled = false; phase = Phase.STARTING; Action.Start }
            Phase.STARTING, Phase.STOPPING -> { restartPending = true; Action.None }
            Phase.RUNNING -> { restartPending = true; phase = Phase.STOPPING; Action.Stop }
        }
    }

    @Synchronized fun stop(): Action {
        wanted = false; restartPending = false; retryScheduled = false
        return when (phase) {
            Phase.RUNNING -> { phase = Phase.STOPPING; Action.Stop }
            else -> Action.None             // STARTING: stopped as soon as it started ([onStarted]); STOPPING / IDLE: nothing to do
        }
    }

    @Synchronized fun onStarted(): Action {
        if (phase != Phase.STARTING) return Action.None
        failures = 0
        if (!wanted || restartPending) { phase = Phase.STOPPING; return Action.Stop }
        phase = Phase.RUNNING; return Action.None
    }

    @Synchronized fun onStartFailed(): Action {
        if (phase != Phase.STARTING) return Action.None
        phase = Phase.IDLE; restartPending = false
        if (!wanted) return Action.None
        retryScheduled = true
        return Action.RetryIn(RetryBackoff.delayMs(failures++))
    }

    @Synchronized fun onRetryDue(): Action {
        if (!retryScheduled) return Action.None
        retryScheduled = false
        if (!wanted || phase != Phase.IDLE) return Action.None
        phase = Phase.STARTING; return Action.Start
    }

    @Synchronized fun onStopped(): Action {
        if (phase != Phase.STOPPING) return Action.None
        phase = Phase.IDLE
        if (wanted && restartPending) { restartPending = false; phase = Phase.STARTING; return Action.Start }
        restartPending = false
        return Action.None
    }

    /** `onDiscoveryStopped` never came (Android sometimes drops it): taken as stopped. */
    @Synchronized fun onStopTimeout(): Action = onStopped()
}

/**
 * Les événements du réseau par défaut, regroupés ([debounceMs]) : l'appel immédiat à l'enregistrement du rappel ne relance rien ; une perte puis un
 * retour à la MÊME adresse relance la découverte sans vider la liste des TV ; une NOUVELLE adresse (bail DHCP, Wi-Fi Direct ↔ réseau local) la relance
 * et vide la liste. [key] = l'identité du réseau du téléphone (interface/adresse), null = aucun réseau.
 */
class NetworkChangeFilter(private var baseline: String?, private val debounceMs: Long = 2_000) {
    data class Decision(val restart: Boolean, val clear: Boolean)
    private var pending = false
    private var lastAt = 0L
    private var latest: String? = null
    private var sawLoss = false

    @Synchronized fun onEvent(now: Long, key: String?) {
        if (!pending && !sawLoss && key == baseline) return            // the call at registration, or nothing changed
        pending = true; lastAt = now; latest = key
        if (key == null) sawLoss = true
    }

    /** The decision once the events have settled for [debounceMs], else null. */
    @Synchronized fun poll(now: Long): Decision? {
        if (!pending || now - lastAt < debounceMs) return null
        pending = false
        val k = latest ?: run { sawLoss = true; return null }          // still no network: decided when it comes back
        val changed = k != baseline
        val loss = sawLoss
        baseline = k; sawLoss = false
        return when { changed -> Decision(restart = true, clear = true); loss -> Decision(restart = true, clear = false); else -> null }
    }
}

/**
 * Les résolutions NsdManager, une à la fois (NsdManager refuse les résolutions parallèles), chacune sous un délai de garde [guardMs] : un rappel
 * qui ne vient jamais ne fige plus la file (avant R-19 : `resolving` restait vrai pour toujours). Un rappel tardif ne fait jamais avancer la file deux fois.
 */
class SerialResolveQueue<T>(private val guardMs: Long = 5_000) {
    data class Next<T>(val item: T, val token: Long)
    private val queue = ArrayDeque<T>()
    private var busy = false
    private var token = 0L
    private var since = 0L

    @Synchronized fun enqueue(item: T, now: Long): Next<T>? {
        queue.addLast(item)
        if (busy && now - since >= guardMs) busy = false
        return if (busy) null else advance(now)
    }

    @Synchronized fun done(token: Long, now: Long): Next<T>? = if (!busy || token != this.token) null else { busy = false; advance(now) }

    @Synchronized fun timeout(token: Long, now: Long): Next<T>? = if (!busy || token != this.token || now - since < guardMs) null else { busy = false; advance(now) }

    @Synchronized fun clear() { queue.clear(); busy = false; token++ }

    private fun advance(now: Long): Next<T>? {
        val i = queue.removeFirstOrNull() ?: return null
        busy = true; since = now; token++
        return Next(i, token)
    }
}

/**
 * Le nom de la TV de l'envoi parmi les TV annoncées : exact ; sinon le nom de base (sans suffixe « (n) » qu'Android ajoute en cas de conflit, sans
 * préfixe « CastBridge TV ») s'il désigne UNE seule TV ; sinon la seule TV du réseau. Jamais de choix entre deux TV du même modèle ; jamais une TV
 * que le téléphone connaît comme une autre ([otherTv]).
 */
object TvNameMatch {
    enum class How { EXACT, BASE, SOLE }
    data class Pick(val name: String, val how: How)
    private val PREFIX = Regex("""^castbridge[ -]tv\s+""", RegexOption.IGNORE_CASE)
    private val BT = Regex("""\s*\(Bluetooth\)\s*$""", RegexOption.IGNORE_CASE)
    private val SUFFIX = Regex("""\s*\(\d+\)\s*$""")

    fun base(name: String): String {
        var x = name.replace("\\032", " ").trim()
        x = BT.replace(x, "").trim()
        x = PREFIX.replace(x, "").trim()
        while (true) { val y = SUFFIX.replace(x, "").trim(); if (y == x) break; x = y }
        return x.lowercase()
    }

    fun pick(wanted: String, announced: List<String>, otherTv: (String) -> Boolean = { false }): Pick? {
        val names = announced.distinct()
        names.firstOrNull { it == wanted }?.let { return Pick(it, How.EXACT) }
        val b = base(wanted)
        val sameBase = names.filter { base(it) == b }
        if (sameBase.size > 1) return null                          // two TVs of the same model: never guessed
        sameBase.singleOrNull()?.let { return if (otherTv(it)) null else Pick(it, How.BASE) }
        val sole = names.singleOrNull() ?: return null
        return if (otherTv(sole)) null else Pick(sole, How.SOLE)
    }
}

/**
 * La dernière adresse qui a répondu pour une TV (par nom de base), persistée et valable [ttlMs] (10 min) : une file qui reprend seule (processus tué,
 * découverte encore vide) la retrouve. Toujours validée par un « hello » avant usage (repli du [TvEndpointResolver]). Jamais l'adresse de boucle locale.
 */
class TvAddressMemory(private val store: TrustPersistence, private val clock: () -> Long, private val ttlMs: Long = 600_000, private val writeEveryMs: Long = 15_000) {
    private data class Entry(val base: String, val at: Long)
    private val map: MutableMap<String, Entry> = load()
    private var lastWrite = Long.MIN_VALUE / 2

    @Synchronized fun remember(tvName: String, base: String) {
        val host = base.removePrefix("http://").substringBefore('/').substringBeforeLast(':')
        if (host == "127.0.0.1" || host.equals("localhost", true) || host.isBlank()) return
        val key = TvNameMatch.base(tvName).ifBlank { return }
        val now = clock()
        val same = map[key]?.base == base
        map[key] = Entry(base, now)
        if (same && now - lastWrite < writeEveryMs) return
        lastWrite = now
        runCatching { store.save(map.entries.joinToString("") { "${it.key}\t${it.value.base}\t${it.value.at}\n" }) }
    }

    @Synchronized fun recall(tvName: String): String? {
        val e = map[TvNameMatch.base(tvName)] ?: return null
        return e.base.takeIf { clock() - e.at in 0..ttlMs }
    }

    private fun load(): MutableMap<String, Entry> {
        val m = HashMap<String, Entry>()
        runCatching { store.load() }.getOrNull()?.lineSequence()?.forEach { line ->
            val p = line.split('\t')
            val at = p.getOrNull(2)?.toLongOrNull()
            if (p.size == 3 && p[0].isNotBlank() && p[1].startsWith("http://") && at != null) m[p[0]] = Entry(p[1], at)
        }
        return m
    }
}

/**
 * La file d'envoi après un envoi arrêté faute de TV ([TvWait.GAVE_UP_TEXT]) : le fichier reprend sa place et la file le relance dès que la TV
 * répond de nouveau, essais espacés par [RetryBackoff] (jamais figée, jamais une boucle serrée), dans la limite de [maxMs].
 */
object ResumeWait {
    enum class Result { REACHABLE, STOPPED, GAVE_UP }

    fun isUnreachable(reason: String?): Boolean = reason != null && (reason == TvWait.GAVE_UP_TEXT || reason == TvWait.WAITING_REASON)

    fun until(reachable: () -> Boolean, stop: () -> Boolean, sleep: (Long) -> Unit, clock: () -> Long, maxMs: Long = 2 * 3_600_000L): Result {
        val t0 = clock()
        var attempt = 0
        while (true) {
            if (stop()) return Result.STOPPED
            if (reachable()) return Result.REACHABLE
            if (clock() - t0 >= maxMs) return Result.GAVE_UP
            var left = RetryBackoff.delayMs(attempt++)
            while (left > 0) {                                  // in slices: « Annuler » and the pause are seen within half a second
                if (stop()) return Result.STOPPED
                val d = minOf(500L, left); sleep(d); left -= d
            }
        }
    }
}
