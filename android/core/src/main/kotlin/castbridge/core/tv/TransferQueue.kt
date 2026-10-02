package castbridge.core.tv

import castbridge.core.lots.QueueStore
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.bool
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str

/** One file waiting to be copied or moved to the TV. */
data class QueueItem(
    val id: Long, val uri: String, val name: String, val size: Long, val move: Boolean,
    val autoPlay: Boolean = false, val progressive: Boolean = false,
    val status: QueueStatus = QueueStatus.WAITING, val error: String? = null,
    /** « Copier sur la TV et lire » : the phone hands playback to the TV during this copy ([QueueRules.next]: it goes before the plain copies waiting). */
    val playOnTv: Boolean = false,
    /** Ordered classic path from byte 0, never « Transfert rapide » ([castbridge.core.phone.CopyRoute], R-08). */
    val ordered: Boolean = false,
    /** null = the trusted link (its session, resolved when the file starts); else the TV found by this name (code path, cast target). Never a credential. */
    val tvName: String? = null,
    /** Manual address of that TV (« Échange de fichiers »), null = found by discovery. */
    val host: String? = null,
    /** Destination volume on the TV, null = the TV's choice. */
    val target: String? = null,
    val enqueuedAt: Long = 0,
    /** « Réessayer » count. */
    val attempts: Int = 0,
)

enum class QueueStatus { WAITING, RUNNING, DONE, FAILED, CANCELLED }

/** French texts of the queue (one place, tested). */
object QueueTexts {
    const val SOURCE_LOST = "Accès au fichier perdu (téléphone redémarré, ou fichier déplacé) : rouvrez-le avec « Ouvrir avec » puis « Copier vers la TV »."
    const val TV_BUSY = "En attente : un autre envoi vers la TV est en cours, celui-ci partira juste après."
    const val NO_TV = "TV non connectée : l'envoi n'a pas pu démarrer. Touchez « Réessayer » quand la TV est allumée."
    const val NO_CREDENTIAL = "Code de la TV inconnu : reconnectez la TV dans CastBridge, puis touchez « Réessayer »."
    const val ALREADY_THERE = "Déjà sur la TV : non recopié"
    const val RESTORED = "Reprise après le redémarrage de CastBridge"
}

/**
 * The rule of the queue (pure): one file at a time, never two uploads fighting for the TV's disk; the running file is never interrupted;
 * a « Copier et lire » file goes before the plain copies still waiting (FIFO among « Copier et lire »), because someone is waiting to watch it;
 * a failed file never blocks the ones behind it and can be retried (it goes to the end).
 */
object QueueRules {
    private fun active(s: QueueStatus) = s == QueueStatus.WAITING || s == QueueStatus.RUNNING

    /** The waiting files in the order they will run: the « Copier et lire » first (FIFO among them), then the plain copies (FIFO). */
    fun runOrder(items: List<QueueItem>): List<QueueItem> {
        val waiting = items.filter { it.status == QueueStatus.WAITING }
        return waiting.filter { it.playOnTv } + waiting.filterNot { it.playOnTv }
    }

    /** The next file to send, only when none is running (the running file is never interrupted). */
    fun next(items: List<QueueItem>): QueueItem? =
        if (items.any { it.status == QueueStatus.RUNNING }) null else runOrder(items).firstOrNull()

    /** 1 = running (or the next to run when nothing runs), 2 = the one after it, …; 0 = not waiting/running. */
    fun position(items: List<QueueItem>, id: Long): Int {
        val it = items.firstOrNull { x -> x.id == id } ?: return 0
        if (!active(it.status)) return 0
        val running = items.filter { x -> x.status == QueueStatus.RUNNING }
        if (it.status == QueueStatus.RUNNING) return 1
        return running.size + runOrder(items).indexOfFirst { x -> x.id == id } + 1
    }

    /** What the user is told when a file is added: « Ajouté à la file : n° 2 » (with what it waits for), or that it starts now. */
    fun admitted(items: List<QueueItem>, id: Long): String {
        val p = position(items, id)
        if (p <= 1) return "envoi en cours"
        val first = items.firstOrNull { it.status == QueueStatus.RUNNING } ?: runOrder(items).firstOrNull()
        val after = first?.let { " après « ${it.name} »" } ?: ""
        val play = items.firstOrNull { it.id == id }?.playOnTv == true
        return "Ajouté à la file : n° $p" + if (play) " — la copie et la lecture sur la TV commenceront$after" else " — l'envoi partira$after"
    }
}

/**
 * The order of the files sent to the TV, one at a time (the TV and its USB bus gain nothing from two uploads fighting each other).
 * Pure bookkeeping, no Android: the phone's runner asks [next], reports with [start] / [finish] and the screens read [items].
 * The same file is never queued twice while it is waiting or running; a failed file never blocks the ones behind it.
 * With a [store], every change is saved and the queue is read back at construction: it survives the death of the process
 * (a file that was running waits again: the TV's partial copy lets it resume).
 */
class TransferQueueModel(private val keepFinished: Int = 12, private val now: () -> Long = System::currentTimeMillis,
                         private val store: QueueStore? = null) {
    private val list = ArrayList<QueueItem>()
    private var seq = 0L

    @Synchronized fun items(): List<QueueItem> = list.toList()

    @Synchronized fun enqueue(uri: String, name: String, size: Long, move: Boolean, autoPlay: Boolean = false, progressive: Boolean = false,
                              playOnTv: Boolean = false, ordered: Boolean = false, tvName: String? = null, host: String? = null,
                              target: String? = null): QueueItem {
        val same = list.indexOfFirst { it.uri == uri && (it.status == QueueStatus.WAITING || it.status == QueueStatus.RUNNING) }
        if (same >= 0) {
            // « Copier et lire » of a file already waiting to be copied: it becomes the file someone waits to watch (it moves up, in order)
            val old = list[same]
            if (playOnTv && old.status == QueueStatus.WAITING && !old.playOnTv) { list[same] = old.copy(playOnTv = true, ordered = old.ordered || ordered); save() }
            return list[same]
        }
        val it = QueueItem(++seq, uri, name, size, move, autoPlay, progressive, playOnTv = playOnTv, ordered = ordered, tvName = tvName, host = host,
            target = target, enqueuedAt = now())
        list += it
        save()
        return it
    }

    /** The next file to send, only when none is running ([QueueRules.next]). */
    @Synchronized fun next(): QueueItem? = QueueRules.next(list)

    @Synchronized fun position(id: Long): Int = QueueRules.position(list, id)
    @Synchronized fun admitted(id: Long): String = QueueRules.admitted(list, id)
    @Synchronized fun item(id: Long): QueueItem? = list.firstOrNull { it.id == id }

    @Synchronized fun start(id: Long) { update(id) { it.copy(status = QueueStatus.RUNNING, error = null) }; save() }

    @Synchronized fun finish(id: Long, ok: Boolean, error: String? = null) {
        update(id) { it.copy(status = if (ok) QueueStatus.DONE else QueueStatus.FAILED, error = if (ok) null else error ?: "Échec de l'envoi") }
        trim(); save()
    }

    /** The running file waits again, at its place (Android refused to start it in the background; it goes first when the app is back). */
    @Synchronized fun release(id: Long) { update(id) { if (it.status == QueueStatus.RUNNING) it.copy(status = QueueStatus.WAITING) else it }; save() }

    /** The file can no longer be read (grant lost after a restart, file moved): failed with the cause, the others go on. */
    @Synchronized fun lost(id: Long) = finish(id, false, QueueTexts.SOURCE_LOST)

    /** « Réessayer » a failed or cancelled file: it waits again, at the end of the queue. */
    @Synchronized fun retry(id: Long): Boolean {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0 || (list[i].status != QueueStatus.FAILED && list[i].status != QueueStatus.CANCELLED)) return false
        // nobody waits in front of the TV any more: a retried « Copier et lire » is a plain copy (still in order)
        val old = list.removeAt(i)
        val again = old.copy(status = QueueStatus.WAITING, error = null, attempts = old.attempts + 1, playOnTv = false)
        list += again
        save(); return true
    }

    /** A waiting file leaves the queue at once; a running one is only marked: the runner cancels the transfer then calls [finishCancelled]. */
    @Synchronized fun cancel(id: Long): Boolean {
        val it = list.firstOrNull { x -> x.id == id } ?: return false
        return when (it.status) {
            QueueStatus.WAITING -> { update(id) { x -> x.copy(status = QueueStatus.CANCELLED) }; trim(); save(); false }
            QueueStatus.RUNNING -> true
            else -> false
        }
    }

    @Synchronized fun finishCancelled(id: Long) { update(id) { it.copy(status = QueueStatus.CANCELLED) }; trim(); save() }

    @Synchronized fun cancelWaiting(): Int {
        var n = 0
        for (i in list.indices) if (list[i].status == QueueStatus.WAITING) { list[i] = list[i].copy(status = QueueStatus.CANCELLED); n++ }
        trim(); save(); return n
    }

    @Synchronized fun waiting() = list.count { it.status == QueueStatus.WAITING }
    @Synchronized fun running(): QueueItem? = list.firstOrNull { it.status == QueueStatus.RUNNING }
    @Synchronized fun busy() = list.any { it.status == QueueStatus.WAITING || it.status == QueueStatus.RUNNING }

    /** « 2 fichiers en attente » / « 1 fichier en attente » / null. */
    @Synchronized fun waitingText(): String? = waiting().takeIf { it > 0 }?.let { if (it == 1) "1 fichier en attente" else "$it fichiers en attente" }

    /** Forgets finished files, oldest first, but keeps the last [keepFinished] to show what happened. */
    @Synchronized fun clearFinished() { list.removeAll { it.status != QueueStatus.WAITING && it.status != QueueStatus.RUNNING }; save() }

    /** The queue as JSON (no credential, no secret: uri, name, size, flags, TV name, status, error). */
    @Synchronized fun encode(): String = JsonLite.write(mapOf("v" to 1, "seq" to seq, "items" to list.map { i ->
        mapOf("id" to i.id, "uri" to i.uri, "name" to i.name, "size" to i.size, "move" to i.move, "autoPlay" to i.autoPlay, "progressive" to i.progressive,
            "status" to i.status.name, "error" to i.error, "playOnTv" to i.playOnTv, "ordered" to i.ordered, "tvName" to i.tvName, "host" to i.host,
            "target" to i.target, "enqueuedAt" to i.enqueuedAt, "attempts" to i.attempts)
    }))

    init { store?.let { s -> runCatching { s.load()?.let(::decodeInto) }.onFailure { list.clear(); seq = 0 } } }

    /** Reads a saved queue back: a file that was running waits again (its partial copy on the TV lets it resume); nobody waits to watch any more. */
    private fun decodeInto(text: String) {
        val o = JsonLite.obj(text)
        val read = (o["items"] as? List<*>).orEmpty().mapNotNull { e ->
            @Suppress("UNCHECKED_CAST") val m = e as? Map<String, Any?> ?: return@mapNotNull null
            val st = runCatching { QueueStatus.valueOf(m.str("status") ?: "") }.getOrNull() ?: return@mapNotNull null
            QueueItem(m.long("id") ?: return@mapNotNull null, m.str("uri") ?: return@mapNotNull null, m.str("name") ?: "fichier", m.long("size") ?: 0,
                m.bool("move") ?: false, m.bool("autoPlay") ?: false, m.bool("progressive") ?: false,
                if (st == QueueStatus.RUNNING) QueueStatus.WAITING else st, m.str("error"), playOnTv = false, ordered = m.bool("ordered") ?: false,
                tvName = m.str("tvName"), host = m.str("host"), target = m.str("target"), enqueuedAt = m.long("enqueuedAt") ?: 0, attempts = (m.long("attempts") ?: 0).toInt())
        }
        list.clear(); list += read
        seq = maxOf(o.long("seq") ?: 0, read.maxOfOrNull { it.id } ?: 0)
    }

    /** Saving never breaks the queue: a disk error only loses the copy on disk (the queue in memory goes on). */
    private fun save() { store?.let { s -> runCatching { s.save(encode()) } } }

    private fun update(id: Long, f: (QueueItem) -> QueueItem) { val i = list.indexOfFirst { it.id == id }; if (i >= 0) list[i] = f(list[i]) }
    private fun trim() {
        val done = list.filter { it.status != QueueStatus.WAITING && it.status != QueueStatus.RUNNING }
        if (done.size > keepFinished) done.take(done.size - keepFinished).forEach { d -> list.removeAll { it.id == d.id } }
    }
}
