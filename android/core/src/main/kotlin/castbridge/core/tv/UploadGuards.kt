package castbridge.core.tv

/**
 * The phone's single upload slot (R-09, audit point 1): reserved ATOMICALLY by whoever starts an upload, before any service is started,
 * and released by the upload's end, failure or the service's destruction. Two starts racing each other: exactly one gets a token, the other
 * is refused explicitly (never dropped in silence, never followed as if it were the other file).
 */
class UploadSlot {
    private var token = 0L
    private var holderToken = 0L
    private var label: String? = null

    /** A token when the slot was free (now held by [what]); null when another upload holds it. */
    @Synchronized fun tryReserve(what: String): Long? {
        if (holderToken != 0L) return null
        holderToken = ++token; label = what
        return holderToken
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
