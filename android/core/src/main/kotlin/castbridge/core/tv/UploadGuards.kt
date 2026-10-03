package castbridge.core.tv

/**
 * The phone's single upload slot (R-09, audit point 1): reserved ATOMICALLY by whoever starts an upload, before any service is started,
 * and released by the upload's end, failure or the service's destruction. Two starts racing each other: exactly one gets a token, the other
 * is refused explicitly (never dropped in silence, never followed as if it were the other file).
 */
class UploadSlot(private val now: () -> Long = System::currentTimeMillis, private val staleMs: Long = STALE_MS) {
    private var token = 0L
    private var holderToken = 0L
    private var label: String? = null
    private var lastSeen = 0L

    /**
     * A token when the slot was free (now held by [what]); null when another upload holds it. A reservation whose owner gave no sign of life
     * ([touch]) for [staleMs] is stale (owner dead or stuck): it is taken over, and the old owner can neither release nor write any more.
     */
    @Synchronized fun tryReserve(what: String): Long? {
        if (holderToken != 0L && now() - lastSeen <= staleMs) return null
        holderToken = ++token; label = what; lastSeen = now()
        return holderToken
    }

    /** Sign of life of the owner (progress, waiting for the network): false when [t] no longer holds the slot. */
    @Synchronized fun touch(t: Long): Boolean {
        if (t == 0L || t != holderToken) return false
        lastSeen = now(); return true
    }

    /** Frees the slot only for the token that holds it (a late release of an older upload never frees the current one). */
    @Synchronized fun release(t: Long): Boolean {
        if (t == 0L || t != holderToken) return false
        holderToken = 0L; label = null
        return true
    }

    @Synchronized fun held(): Boolean = holderToken != 0L
    @Synchronized fun holder(): String? = label
    @Synchronized fun holds(t: Long): Boolean = t != 0L && holderToken == t

    companion object { const val STALE_MS = 5 * 60_000L }
}

/**
 * How the queue reads the end of a launched file (pure, R-09 second review): the cause set by the owner of the current upload generation
 * (Android's time limit) is never overwritten by a late « annulé » of its worker; « the previous upload is still ending » puts the file back
 * at its place instead of failing it.
 */
object QueueOutcome {
    enum class Kind { DONE, CANCELLED, RETRY_SOON, PAUSE_TIME_LIMIT, PAUSE_BACKGROUND, FAILED }

    fun of(outcome: String?, cancelAsked: Boolean, backgroundRefusal: Boolean): Kind = when {
        cancelAsked -> Kind.CANCELLED
        outcome == null -> Kind.DONE
        outcome.startsWith(QueueTexts.TIME_LIMIT) -> Kind.PAUSE_TIME_LIMIT
        outcome == QueueTexts.PREVIOUS_ENDING -> Kind.RETRY_SOON
        backgroundRefusal -> Kind.PAUSE_BACKGROUND
        else -> Kind.FAILED
    }

    /** The failure the service publishes: the owner's cause (time limit) wins over what its worker returns afterwards. */
    fun finalCause(ownerCause: String?, workerResult: String): String = ownerCause ?: workerResult
}

/**
 * What « Annuler » does to a queued file (R-09, audit point 3), pure:
 * a waiting file leaves the queue; a running file not launched yet is only marked, and the runner must not launch it ([mayLaunch]);
 * a launched file stops its upload; a cancellation that lands between the launch and its record stops the upload just after ([afterLaunch]);
 * a cancelled move never asks for the deletion of the phone's file ([mayDeleteMoved]).
 */
object QueueCancel {
    enum class Action { NOTHING, REMOVE, MARK, STOP_UPLOAD }

    fun onCancel(status: QueueStatus, launched: Boolean): Action = when (status) {
        QueueStatus.WAITING -> Action.REMOVE
        QueueStatus.RUNNING -> if (launched) Action.STOP_UPLOAD else Action.MARK
        else -> Action.NOTHING
    }
    fun mayLaunch(cancelAsked: Boolean): Boolean = !cancelAsked
    fun afterLaunch(cancelAsked: Boolean): Action = if (cancelAsked) Action.STOP_UPLOAD else Action.NOTHING
    fun mayDeleteMoved(cancelled: Boolean, tvHoldsCompleteCopy: Boolean): Boolean = !cancelled && tvHoldsCompleteCopy

    /** The upload launched for (id, attempts) belongs to [item] only if it is the same attempt: a retried file waiting its turn was never launched. */
    fun isLaunched(launchedId: Long, launchedAttempt: Int, item: QueueItem): Boolean =
        launchedId >= 0 && launchedId == item.id && launchedAttempt == item.attempts
}

/**
 * Deletions of moved files waiting for the user (R-09, minor 6): a FIFO, so that two moves ending close together never overwrite each other;
 * the screen shows [head] and calls [done] when it is handled.
 */
class MoveInbox<T> {
    private val q = ArrayDeque<T>()
    @Synchronized fun offer(x: T) { q.addLast(x) }
    @Synchronized fun head(): T? = q.firstOrNull()
    /** The head is handled: the next one (or null) becomes the head. */
    @Synchronized fun done(): T? { q.removeFirstOrNull(); return q.firstOrNull() }
    @Synchronized fun size(): Int = q.size
}
