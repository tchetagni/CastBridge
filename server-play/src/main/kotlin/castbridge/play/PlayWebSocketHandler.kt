package castbridge.play

import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Session WebSocket de jeu (`/play/ws`) : lecture dans le fil de la connexion, écriture par un fil dédié et une file de sortie bornée (64 Ko : au-delà,
 * fermeture 1008). Ping toutes les `pingMs` (25 s : nginx coupe un flux muet à 75 s), fermeture si plus rien reçu depuis `pongTimeoutMs` (40 s).
 * Aucune logique de jeu : les messages texte vont à [PlayHub].
 *
 * Audit Opus de w20-03 : (B1) les trames de contrôle du client ne remplissent jamais la file : UN SEUL pong en attente (le dernier, RFC 6455 § 5.5.3), ses octets comptés
 * dans la file, et un seau dédié (≈ 5 trames de contrôle par seconde, rafale 10) au-delà duquel la session est fermée 1008 ; (B2) aucun `synchronized` ni `wait` sur un chemin
 * bloquant (fils virtuels, JEP 444) : verrou d'écriture explicite, écriture à échéance (un client qui ne lit plus est coupé), tueur sur un ordonnanceur de plateforme.
 */
class WsConn(id: String, ip: String, private val socket: Socket, private val cfg: PlayConfig, private val hub: PlayHub) : PlayConn(id, ip, cfg.ratePerSec, cfg.burst) {
    private class Item(val opcode: Int, val payload: ByteArray)

    /** Le `state` encore en file : le prochain `state` le REMPLACE (coalescence « dernier état seulement », w20-04b). */
    private val pendingState = AtomicReference<Item?>(null)

    private val queue = LinkedBlockingQueue<Item>()
    private val queued = AtomicInteger()
    private val pendingPong = AtomicReference<ByteArray?>(null)
    private val pingPending = AtomicBoolean(false)
    private val control = TokenBucket(CONTROL_PER_SEC, CONTROL_BURST)
    private val writeLock = ReentrantLock()
    @Volatile private var writeStartedMs = 0L
    private val out = socket.getOutputStream()
    @Volatile private var lastPingMs = System.currentTimeMillis()
    private val closing = AtomicBoolean(false)
    private val killer = AtomicReference<java.util.concurrent.ScheduledFuture<*>?>(null)

    /** Octets en attente de sortie (messages et pong) : doit rester sous `outboxMaxBytes`. */
    fun queuedBytes(): Int = queued.get()

    /** Nombre de pongs en attente d'écriture : 0 ou 1, jamais plus. */
    internal fun pendingPongs(): Int = if (pendingPong.get() != null) 1 else 0

    /** Les messages texte encore en file, dans l'ordre (tests et diagnostic). */
    internal fun queuedTexts(): List<String> = queue.filter { it.opcode == WsProtocol.OP_TEXT }.map { String(it.payload, Charsets.UTF_8) }

    override fun offer(text: String): Boolean {
        if (closing.get()) return true
        val b = text.toByteArray(Charsets.UTF_8)
        // Coalescence : une vue `state` est COMPLÈTE ; le `state` pas encore écrit est retiré, le nouveau prend sa place EN FIN de file. Aucun autre type n'est jamais retiré ni réordonné.
        val isState = text.startsWith(PlayFallbackController.STATE_PREFIX)
        if (isState) pendingState.getAndSet(null)?.let { old -> if (queue.remove(old)) queued.addAndGet(-old.payload.size) }   // faux : l'écrivain l'a déjà pris, il décompte lui-même
        if (queued.addAndGet(b.size) > cfg.outboxMaxBytes) return false
        val item = Item(WsProtocol.OP_TEXT, b)
        if (isState) pendingState.set(item)
        queue.add(item); return true
    }

    override fun close(code: Int, reason: String) {
        if (!closing.compareAndSet(false, true)) return
        queue.clear(); queued.set(0)
        queue.add(Item(WsProtocol.OP_CLOSE, WsProtocol.closePayload(code, reason)))
        // si l'écrivain est bloqué (client qui ne lit plus), la socket est fermée de force par un ordonnanceur de PLATEFORME (jamais un fil virtuel)
        killer.set(KILLER.schedule({ runCatching { socket.close() } }, 2, TimeUnit.SECONDS))
    }

    override fun housekeeping(nowMs: Long) {
        if (closing.get()) return
        val w = writeStartedMs
        if (w != 0L && nowMs - w >= cfg.writeTimeoutMs) { closing.set(true); runCatching { socket.close() }; return }   // écriture bloquée : le client ne lit plus
        if (nowMs - lastInboundMs >= cfg.pongTimeoutMs) { close(1001, "pas de réponse"); return }
        if (nowMs - lastPingMs >= cfg.pingMs && pingPending.compareAndSet(false, true)) { lastPingMs = nowMs; queue.add(Item(WsProtocol.OP_PING, ByteArray(0))) }
    }

    /** Un ping du client : UN SEUL pong en attente, le dernier ; ses octets sont comptés dans la file. */
    internal fun onClientPing(payload: ByteArray) {
        val old = pendingPong.getAndSet(payload)
        if (old == null) { queued.addAndGet(payload.size + CONTROL_OVERHEAD); queue.add(Item(PONG_WAKE, ByteArray(0))) } else queued.addAndGet(payload.size - old.size)
    }

    private fun write(opcode: Int, payload: ByteArray) {
        writeLock.withLock {
            writeStartedMs = System.currentTimeMillis()
            try { WsProtocol.writeFrame(out, opcode, payload) } finally { writeStartedMs = 0L }
        }
    }

    private fun writerLoop() {
        try {
            while (true) {
                val it = queue.take()
                when (it.opcode) {
                    PONG_WAKE -> { val p = pendingPong.getAndSet(null) ?: continue; queued.addAndGet(-(p.size + CONTROL_OVERHEAD)); write(WsProtocol.OP_PONG, p) }
                    WsProtocol.OP_PING -> { pingPending.set(false); write(WsProtocol.OP_PING, it.payload) }
                    WsProtocol.OP_TEXT -> { queued.addAndGet(-it.payload.size); write(WsProtocol.OP_TEXT, it.payload) }
                    else -> { write(it.opcode, it.payload); if (it.opcode == WsProtocol.OP_CLOSE) break }
                }
            }
        } catch (_: IOException) {} catch (_: InterruptedException) {}
        runCatching { socket.close() }
    }

    /** Boucle de lecture ; rend la main quand la session est finie (la socket est alors fermée). */
    fun run(input: InputStream) {
        socket.soTimeout = 0
        val writer = Thread.ofVirtual().start { writerLoop() }
        val partial = java.io.ByteArrayOutputStream()
        var partialOp = -1
        var emptyContinuations = 0
        try {
            while (true) {
                val f = WsProtocol.readFrame(input, cfg.maxFrameBytes) ?: break
                lastInboundMs = System.currentTimeMillis()
                if (f.opcode == WsProtocol.OP_PING || f.opcode == WsProtocol.OP_PONG) { if (!control.take()) throw WsError(1008, "trop de trames de contrôle") }
                when (f.opcode) {
                    WsProtocol.OP_PING -> onClientPing(f.payload)
                    WsProtocol.OP_PONG -> {}
                    WsProtocol.OP_CLOSE -> { close(1000, "au revoir"); break }
                    WsProtocol.OP_BINARY -> throw WsError(1003, "texte seulement")
                    WsProtocol.OP_TEXT, WsProtocol.OP_CONT -> {
                        if (f.opcode == WsProtocol.OP_TEXT) { if (partialOp >= 0) throw WsError(1002, "fragment attendu"); partialOp = WsProtocol.OP_TEXT; partial.reset(); emptyContinuations = 0 }
                        else if (partialOp < 0) throw WsError(1002, "fragment orphelin")
                        else if (f.payload.isEmpty() && !f.fin && ++emptyContinuations > MAX_EMPTY_CONTINUATIONS) throw WsError(1008, "fragments vides en rafale")
                        partial.write(f.payload)
                        if (partial.size() > cfg.maxFrameBytes) throw WsError(1009, "message trop gros")
                        if (f.fin) {
                            val text = WsProtocol.utf8(partial.toByteArray()); partialOp = -1; partial.reset()
                            if (!hub.onText(this, text)) break
                        }
                    }
                }
            }
        } catch (e: WsError) { close(e.code, e.message ?: "erreur") }
        catch (_: IOException) {}
        finally {
            hub.onClosed(this)
            if (closing.compareAndSet(false, true)) { queue.clear(); queue.add(Item(WsProtocol.OP_CLOSE, WsProtocol.closePayload(1001, "fin"))) }
            runCatching { writer.join(2_000) }
            killer.get()?.cancel(false)   // le tueur n'a plus rien à tuer : la tâche ne reste pas dans l'ordonnanceur
            runCatching { socket.close() }
        }
    }

    companion object {
        private const val PONG_WAKE = -1
        private const val CONTROL_OVERHEAD = 2
        const val CONTROL_PER_SEC = 5
        const val CONTROL_BURST = 10
        const val MAX_EMPTY_CONTINUATIONS = 8
        /** Ordonnanceur de PLATEFORME (un seul fil) : ferme de force les sessions dont l'écrivain est bloqué. */
        private val KILLER: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "play-killer").apply { isDaemon = true } }
    }
}
