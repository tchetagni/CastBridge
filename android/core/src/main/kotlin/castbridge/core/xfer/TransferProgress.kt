package castbridge.core.xfer

import java.util.Locale

/**
 * What the TV is receiving right now, published from ONE place (docs/agent-reports/diag-receiver-progress.md).
 *
 * Every receive path feeds it: the classic `PUT /upload/` (Wi-Fi), the multi-connection `/api/transfer/...` (Wi-Fi multivoie, whose
 * bytes live in a hidden `.cbx/<id>.data` until the end and were therefore invisible to the screen), and the Bluetooth CBT1 link
 * (through [Sink]). The TV notification and the home screen both read it, so they can never disagree.
 *
 * Rules: [begin] and every end ([finish], [fail], [abort], [sweep] of a silent transfer) always notify; [advance] notifies at most every
 * [minEmitMs] per transfer (and once more when the last byte is there). A transfer whose connection broke ([interrupted]) stays
 * listed (the phone resumes it) until it has been silent for [staleMs]. Listeners are called outside the lock, on the caller's thread.
 */
class TransferProgress(
    private val now: () -> Long = System::currentTimeMillis,
    private val minEmitMs: Long = 1000,
    private val staleMs: Long = 90_000,
    private val keepEndedMs: Long = 8_000,
) {
    enum class Transport(val label: String) { WIFI("Wi-Fi"), WIFI_MULTI("Wi-Fi multivoie"), BLUETOOTH("Bluetooth") }
    enum class Phase { RUNNING, DONE, FAILED, ABORTED }

    /** One transfer. [seq] is stable for the transfer's whole life (resumes included): the TV uses it as the notification id. */
    data class Item(
        val id: String, val seq: Int, val name: String, val total: Long, val received: Long, val transport: Transport,
        val source: String?, val startedAt: Long, val updatedAt: Long, val bytesPerSec: Long, val phase: Phase, val message: String? = null,
        /** R-15: the copy is held back so that a video playing on the TV stays smooth. */
        val slowed: Boolean = false,
    ) {
        val percent: Int get() = if (total > 0) (received * 100 / total).toInt().coerceIn(0, 100) else 0
        val ended: Boolean get() = phase != Phase.RUNNING
        val title: String get() = castbridge.core.tv.LibraryLogic.title(name)
        private val video: Boolean get() = castbridge.core.tv.UsbImport.isVideo(name)

        /** « 42 % · 1,8 Mo/s · Wi-Fi · depuis Galaxy A52 » (or the reason of a pause). */
        fun detail(): String = buildString {
            append("$percent %")
            if (bytesPerSec > 0 && message == null) append(" · ").append(speed(bytesPerSec))
            append(" · ").append(transport.label)
            source?.let { append(" · depuis ").append(it) }
            message?.let { append(" · ").append(it) }
            if (slowed && message == null) append(" · ").append(PlaybackAwareCopyPolicy.SLOWED_TEXT)
        }

        /** The final line once it ended: « Vidéo reçue ✓ », « Fichier reçu ✓ » or why it stopped. */
        fun endLine(): String = when (phase) {
            Phase.DONE -> if (video) "Vidéo reçue ✓" else "Fichier reçu ✓"
            Phase.FAILED -> "Échec de la réception" + (message?.let { " : $it" } ?: "")
            Phase.ABORTED -> "Réception interrompue" + (message?.let { " : $it" } ?: "")
            Phase.RUNNING -> detail()
        }

        /** One line for the TV home chip, readable from the sofa (no tiny details). */
        fun screenLine(): String = if (ended) "${endLine()} · $title" else "⬇ Réception de $title : ${detail()}"
    }

    fun interface Listener { fun changed(item: Item) }

    private class Live(var item: Item, var lastEmit: Long, var sampleAt: Long, var sampleBytes: Long)

    /** Interval between two progress repaints of one transfer; [PlaybackGovernor] raises it while a video plays (notification + home chip cost CPU). */
    @Volatile var emitEveryMs: Long = minEmitMs
    /** R-15: asked at each repaint of a running transfer; true = « Copie ralentie pour ne pas gêner la lecture » is added to its line. */
    @Volatile var slowedNow: () -> Boolean = { false }

    private val live = LinkedHashMap<String, Live>()
    private val ended = LinkedHashMap<String, Item>()
    private val seqOf = HashMap<String, Int>()
    private var seq = 0
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()

    fun addListener(l: Listener) { listeners += l }
    fun removeListener(l: Listener) { listeners -= l }

    /** True while [id] is being received (begun, not ended). */
    @Synchronized fun isRunning(id: String): Boolean = live.containsKey(id)

    /** A transfer starts, or resumes at [received] (a new connection of the same transfer keeps its [Item.seq] and its source). */
    fun begin(id: String, name: String, total: Long, transport: Transport, source: String?, received: Long = 0): Item {
        val item = synchronized(this) {
            val t = now()
            ended.remove(id)
            val l = live[id]
            if (l != null) {
                l.item = l.item.copy(name = name, total = total, received = received, updatedAt = t,
                    source = source ?: l.item.source, message = null)
                l.lastEmit = t; l.sampleAt = t; l.sampleBytes = received
                l.item
            } else {
                val n = seqOf.getOrPut(id) { ++seq }
                Item(id, n, name, total, received, transport, source, t, t, 0, Phase.RUNNING).also { live[id] = Live(it, t, t, received) }
            }
        }
        emit(item)
        return item
    }

    /** [received] bytes are now on the TV for [id]. Unknown ids are ignored (a late chunk after the end). */
    fun advance(id: String, received: Long) {
        val item = synchronized(this) {
            val l = live[id] ?: return
            val t = now()
            var bps = l.item.bytesPerSec
            val dt = t - l.sampleAt
            if (dt >= 500) {
                val inst = ((received - l.sampleBytes).coerceAtLeast(0) * 1000 / dt)
                bps = if (bps <= 0) inst else (bps * 6 + inst * 4) / 10
                l.sampleAt = t; l.sampleBytes = received
            }
            l.item = l.item.copy(received = received, updatedAt = t, bytesPerSec = bps, message = null, slowed = runCatching(slowedNow).getOrDefault(false))
            val last = l.item.total in 1..received
            if (!last && t - l.lastEmit < emitEveryMs) return
            l.lastEmit = t
            l.item
        }
        emit(item)
    }

    /** The connection of [id] broke; the phone may resume it. It stays listed with [why] until [staleMs] of silence. */
    fun interrupted(id: String, why: String = "connexion coupée, reprise en attente") {
        val item = synchronized(this) {
            val l = live[id] ?: return
            l.item = l.item.copy(message = why, bytesPerSec = 0, updatedAt = now()); l.lastEmit = now(); l.item
        }
        emit(item)
    }

    /** Everything arrived; [finalName] is the name it was filed under (null = the name it was sent with). */
    fun finish(id: String, finalName: String? = null) = end(id, Phase.DONE, null, finalName)
    fun fail(id: String, reason: String) = end(id, Phase.FAILED, reason, null)
    fun abort(id: String, reason: String? = null) = end(id, Phase.ABORTED, reason, null)

    private fun end(id: String, phase: Phase, reason: String?, finalName: String?) {
        val item = synchronized(this) {
            val l = live.remove(id) ?: return
            val it = l.item.copy(phase = phase, message = reason, updatedAt = now(), bytesPerSec = 0,
                received = if (phase == Phase.DONE) l.item.total else l.item.received, name = finalName ?: l.item.name)
            ended[id] = it
            it
        }
        emit(item)
    }

    /** Ends (ABORTED) the transfers nobody fed for [staleMs], and forgets ended ones older than [keepEndedMs]. Called by the readers. */
    fun sweep() {
        val gone = synchronized(this) {
            val t = now()
            ended.values.removeAll { t - it.updatedAt > keepEndedMs }
            live.values.filter { t - it.item.updatedAt > staleMs }.map { it.item.id }
        }
        gone.forEach { abort(it, "plus de nouvelles du téléphone") }
    }

    /** Transfers running now, oldest first. */
    fun active(): List<Item> { sweep(); return synchronized(this) { live.values.map { it.item } } }

    /** What the screen shows: running transfers, else the ones that just ended (for a few seconds). */
    fun shown(): List<Item> { sweep(); return synchronized(this) { live.values.map { it.item }.ifEmpty { ended.values.toList().asReversed() } } }

    private fun emit(item: Item) { for (l in listeners) runCatching { l.changed(item) } }

    /**
     * A Bluetooth CBT1 connection: its progress callback ([progress], from [castbridge.core.tv.BtProtocol.serve]) begins the transfer on the first
     * call; the outcome of the connection ends it. A connection that carried no file (HELLO, remote, refused) leaves nothing behind.
     */
    inner class Sink(private val peer: String, private val source: String?) {
        @Volatile private var id: String? = null
        fun progress(name: String, done: Long, total: Long) {
            val cur = id
            if (cur == null) { val n = "bt:$peer:${name.lowercase(Locale.ROOT)}"; id = n; begin(n, name, total, Transport.BLUETOOTH, source, done) }
            else advance(cur, done)
        }
        /** The connection ended with a status: OK ends the transfer as received, anything else as failed with [reason]. */
        fun end(ok: Boolean, reason: String? = null) { val i = id ?: return; if (ok) finish(i) else fail(i, reason ?: "refusée par la TV") }
        /** The Bluetooth link broke mid-file: the phone resumes from the bytes kept on the TV. */
        fun broken() { id?.let { interrupted(it) } }
    }

    companion object {
        fun speed(bps: Long): String = if (bps >= 1_000_000) String.format(Locale.FRANCE, "%.1f Mo/s", bps / 1e6) else "${bps / 1000} ko/s"
    }
}
