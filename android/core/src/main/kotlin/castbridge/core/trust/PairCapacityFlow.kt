package castbridge.core.trust

/**
 * TV side, « la TV a déjà 8 téléphones » : le remplacement avant l'ajout, en machine d'états pure (aucun écran, aucun Bluetooth).
 *
 *   Idle --ask(9e téléphone, fenêtre ouverte)--> AwaitingRemoval(demande, liste) --choose(téléphone à retirer)--> Idle (remplacé, un seul write)
 *                                                      |--cancel()--> Idle (annulé, raison dite au téléphone une fois)
 *                                                      \--[timeoutMs]--> Idle (délai dépassé, raison dite au téléphone une fois)
 *
 * Le propriétaire de la TV décide : rien n'est retiré ni ajouté tout seul (la liste ne fait que SUGGÉRER le moins récemment vu).
 * Choisir le téléphone à retirer vaut approbation du nouveau : le retrait et l'ajout sont faits par [TrustRegistry.replace] en UNE écriture
 * (jamais 9 téléphones, jamais ni l'un ni l'autre si l'écriture échoue). Le téléphone qui demande interroge de nouveau (ERR_FULL) et trouve
 * sa confiance faite, ou la raison de l'annulation ([Answer.Cancelled], [Answer.TimedOut]).
 */
class PairCapacityFlow(
    private val registry: TrustRegistry,
    private val now: () -> Long = System::currentTimeMillis,
    val timeoutMs: Long = 120_000,
    /** Bluetooth addresses of the phones in touch with the TV right now (for « actif »). */
    private val active: () -> Set<String> = { emptySet() },
    /** An owner's cancellation counts like a refusal (see [PairingSession.recordDenial]). */
    private val onDenied: (String) -> Unit = {},
) {
    data class Request(val address: String, val name: String, val deadline: Long)

    sealed class State {
        object Idle : State()
        data class AwaitingRemoval(val request: Request, val roster: PhoneRoster.View, val sameName: Boolean = false) : State()
    }

    /** What the HELLO of the asking phone is told. */
    sealed class Answer {
        /** Not at the cap (or nobody opened the window): the normal approval path goes on. */
        object Room : Answer()
        /** Already trusted (e.g. just replaced the phone the owner chose). */
        object Trusted : Answer()
        /** Waiting for the owner to choose: ERR_FULL, the phone asks again. */
        object Pending : Answer()
        /** Another phone's replacement is being decided: ERR_BUSY. */
        object Busy : Answer()
        object Cancelled : Answer()
        object TimedOut : Answer()
    }

    sealed class Choice {
        data class Replaced(val removed: TrustedPhone, val added: TrustedPhone) : Choice()
        object NotPending : Choice()
        object NotFound : Choice()
        object WriteFailed : Choice()
    }

    enum class Kind { REQUESTED, REPLACED, CANCELLED, TIMED_OUT }
    /** What the TV tells its owner (see [PhonesTexts.tvMessage]). */
    data class Event(val kind: Kind, val name: String, val removedName: String? = null, val removedAddress: String? = null, val address: String? = null)

    private enum class Outcome { CANCELLED, TIMED_OUT }
    private class Told(val outcome: Outcome, val at: Long)

    private val lock = Any()
    private var pending: Request? = null
    private val told = HashMap<String, Told>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Event) -> Unit>()

    fun addListener(l: (Event) -> Unit) { listeners += l }
    fun removeListener(l: (Event) -> Unit) { listeners -= l }
    private fun publish(events: List<Event>) { events.forEach { e -> listeners.forEach { runCatching { it(e) } } } }

    /** Call with [lock] held: ends a request that timed out or whose cap was lifted meanwhile; forgets old outcomes. */
    private fun expire(events: MutableList<Event>) {
        val t = now()
        val p = pending
        if (p != null) {
            if (p.deadline <= t) { told[p.address] = Told(Outcome.TIMED_OUT, t); pending = null; events += Event(Kind.TIMED_OUT, p.name) }
            // a phone removed elsewhere frees a seat: the request is KEPT (never erased silently); that phone's next HELLO takes the normal approval path
        }
        told.values.removeAll { t - it.at > FORGET_MS }
    }

    fun state(): State {
        val ev = ArrayList<Event>()
        val s = synchronized(lock) {
            expire(ev)
            pending?.let { State.AwaitingRemoval(it, PhoneRoster.build(registry.list(), active(), now()), PhoneRoster.sameName(it.name, registry.list())) } ?: State.Idle
        }
        publish(ev)
        return s
    }

    /**
     * HELLO of a phone that asked to be trusted. [windowOpen]: the owner opened « Ajouter un téléphone » (no request is opened otherwise:
     * the normal path then says « fermé »).
     */
    fun ask(address: String, name: String, windowOpen: Boolean): Answer {
        val a = TrustRegistry.norm(address)
        val ev = ArrayList<Event>()
        val answer = synchronized(lock) {
            expire(ev)
            if (registry.isTrusted(a)) { if (pending?.address == a) pending = null; told.remove(a); return@synchronized Answer.Trusted }
            told.remove(a)?.let { return@synchronized if (it.outcome == Outcome.CANCELLED) Answer.Cancelled else Answer.TimedOut }
            if (registry.list().size < TrustRegistry.MAX_PHONES) return@synchronized Answer.Room
            val p = pending
            when {
                p != null -> if (p.address == a) Answer.Pending else Answer.Busy
                !windowOpen -> Answer.Room
                else -> { val r = Request(a, PhoneName.sanitize(name), now() + timeoutMs); pending = r; ev += Event(Kind.REQUESTED, r.name, address = r.address); Answer.Pending }
            }
        }
        publish(ev)
        return answer
    }

    /** The owner chose which phone to remove for the waiting one. [expectedNew] is the phone the owner SAW: a request replaced meanwhile admits nobody. */
    fun choose(removeAddress: String, expectedNew: String): Choice {
        val ev = ArrayList<Event>()
        val res = synchronized(lock) {
            expire(ev)
            val p = pending ?: return@synchronized Choice.NotPending
            if (p.address != TrustRegistry.norm(expectedNew)) return@synchronized Choice.NotPending
            val old = registry.get(removeAddress) ?: return@synchronized Choice.NotFound
            when (val r = registry.replace(removeAddress, p.address, p.name)) {
                is TrustResult.Added -> { pending = null; ev += Event(Kind.REPLACED, r.phone.name, old.name, old.address); Choice.Replaced(old, r.phone) }
                is TrustResult.Refreshed -> { pending = null; Choice.NotPending }
                TrustResult.WriteFailed -> Choice.WriteFailed
                TrustResult.NotFound, is TrustResult.Full -> Choice.NotFound
            }
        }
        publish(ev)
        return res
    }

    /** The owner gives up: the TV keeps its 8 phones. */
    fun cancel(): Boolean {
        val ev = ArrayList<Event>(); var denied: String? = null
        val r = synchronized(lock) {
            expire(ev)
            val p = pending ?: return@synchronized false
            told[p.address] = Told(Outcome.CANCELLED, now()); pending = null; ev += Event(Kind.CANCELLED, p.name); denied = p.address; true
        }
        denied?.let { runCatching { onDenied(it) } }
        publish(ev)
        return r
    }

    private companion object { const val FORGET_MS = 10 * 60_000L }
}
