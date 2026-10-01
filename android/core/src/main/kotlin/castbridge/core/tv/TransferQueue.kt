package castbridge.core.tv

/** One file waiting to be copied or moved to the TV. */
data class QueueItem(
    val id: Long, val uri: String, val name: String, val size: Long, val move: Boolean,
    val autoPlay: Boolean = false, val progressive: Boolean = false,
    val status: QueueStatus = QueueStatus.WAITING, val error: String? = null,
)

enum class QueueStatus { WAITING, RUNNING, DONE, FAILED, CANCELLED }

/**
 * The order of the files sent to the TV, one at a time (the TV and its USB bus gain nothing from two uploads fighting each other).
 * Pure bookkeeping, no Android: the phone's runner asks [next], reports with [start] / [finish] and the screens read [items].
 * The same file is never queued twice while it is waiting or running; a failed file never blocks the ones behind it.
 */
class TransferQueueModel(private val keepFinished: Int = 12) {
    private val list = ArrayList<QueueItem>()
    private var seq = 0L

    @Synchronized fun items(): List<QueueItem> = list.toList()

    @Synchronized fun enqueue(uri: String, name: String, size: Long, move: Boolean, autoPlay: Boolean = false, progressive: Boolean = false): QueueItem {
        list.firstOrNull { it.uri == uri && (it.status == QueueStatus.WAITING || it.status == QueueStatus.RUNNING) }?.let { return it }
        val it = QueueItem(++seq, uri, name, size, move, autoPlay, progressive)
        list += it
        return it
    }

    /** The next file to send, only when none is running. */
    @Synchronized fun next(): QueueItem? =
        if (list.any { it.status == QueueStatus.RUNNING }) null else list.firstOrNull { it.status == QueueStatus.WAITING }

    @Synchronized fun start(id: Long) = update(id) { it.copy(status = QueueStatus.RUNNING, error = null) }

    @Synchronized fun finish(id: Long, ok: Boolean, error: String? = null) {
        update(id) { it.copy(status = if (ok) QueueStatus.DONE else QueueStatus.FAILED, error = if (ok) null else error ?: "Échec de l'envoi") }
        trim()
    }

    /** A waiting file leaves the queue at once; a running one is only marked: the runner cancels the transfer then calls [finishCancelled]. */
    @Synchronized fun cancel(id: Long): Boolean {
        val it = list.firstOrNull { x -> x.id == id } ?: return false
        return when (it.status) {
            QueueStatus.WAITING -> { update(id) { x -> x.copy(status = QueueStatus.CANCELLED) }; trim(); false }
            QueueStatus.RUNNING -> true
            else -> false
        }
    }

    @Synchronized fun finishCancelled(id: Long) { update(id) { it.copy(status = QueueStatus.CANCELLED) }; trim() }

    @Synchronized fun cancelWaiting(): Int {
        var n = 0
        for (i in list.indices) if (list[i].status == QueueStatus.WAITING) { list[i] = list[i].copy(status = QueueStatus.CANCELLED); n++ }
        trim(); return n
    }

    @Synchronized fun waiting() = list.count { it.status == QueueStatus.WAITING }
    @Synchronized fun running(): QueueItem? = list.firstOrNull { it.status == QueueStatus.RUNNING }
    @Synchronized fun busy() = list.any { it.status == QueueStatus.WAITING || it.status == QueueStatus.RUNNING }

    /** « 2 fichiers en attente » / « 1 fichier en attente » / null. */
    @Synchronized fun waitingText(): String? = waiting().takeIf { it > 0 }?.let { if (it == 1) "1 fichier en attente" else "$it fichiers en attente" }

    /** Forgets finished files, oldest first, but keeps the last [keepFinished] to show what happened. */
    @Synchronized fun clearFinished() { list.removeAll { it.status != QueueStatus.WAITING && it.status != QueueStatus.RUNNING } }

    private fun update(id: Long, f: (QueueItem) -> QueueItem) { val i = list.indexOfFirst { it.id == id }; if (i >= 0) list[i] = f(list[i]) }
    private fun trim() {
        val done = list.filter { it.status != QueueStatus.WAITING && it.status != QueueStatus.RUNNING }
        if (done.size > keepFinished) done.take(done.size - keepFinished).forEach { d -> list.removeAll { it.id == d.id } }
    }
}
