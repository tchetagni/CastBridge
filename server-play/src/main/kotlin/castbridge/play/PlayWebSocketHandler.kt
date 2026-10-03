package castbridge.play

import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Session WebSocket de jeu (`/play/ws`) : lecture dans le fil de la connexion, écriture par un fil dédié et une file de sortie bornée (64 Ko : au-delà,
 * fermeture 1008). Ping toutes les `pingMs` (25 s : nginx coupe un flux muet à 75 s), fermeture si plus rien reçu depuis `pongTimeoutMs` (40 s).
 * Aucune logique de jeu : les messages texte vont à [PlayHub].
 */
class WsConn(id: String, ip: String, private val socket: Socket, private val cfg: PlayConfig, private val hub: PlayHub) : PlayConn(id, ip, cfg.ratePerSec, cfg.burst) {
    private class Item(val opcode: Int, val payload: ByteArray)

    private val queue = LinkedBlockingQueue<Item>()
    private val queued = AtomicInteger()
    private val out = socket.getOutputStream()
    @Volatile private var lastPingMs = System.currentTimeMillis()
    @Volatile private var closing = false

    override fun offer(text: String): Boolean {
        if (closing) return true
        val b = text.toByteArray(Charsets.UTF_8)
        if (queued.addAndGet(b.size) > cfg.outboxMaxBytes) return false
        queue.add(Item(WsProtocol.OP_TEXT, b)); return true
    }

    override fun close(code: Int, reason: String) {
        if (closing) return
        closing = true
        queue.clear(); queued.set(0)
        queue.add(Item(WsProtocol.OP_CLOSE, WsProtocol.closePayload(code, reason)))
        // si l'écrivain est bloqué (client qui ne lit plus), la socket est fermée de force
        Thread.startVirtualThread { try { Thread.sleep(2_000) } catch (_: InterruptedException) {}; runCatching { socket.close() } }
    }

    override fun housekeeping(nowMs: Long) {
        if (closing) return
        if (nowMs - lastInboundMs >= cfg.pongTimeoutMs) { close(1001, "pas de réponse"); return }
        if (nowMs - lastPingMs >= cfg.pingMs) { lastPingMs = nowMs; queue.add(Item(WsProtocol.OP_PING, ByteArray(0))) }
    }

    private fun writerLoop() {
        try {
            while (true) {
                val it = queue.take()
                if (it.opcode == WsProtocol.OP_TEXT) queued.addAndGet(-it.payload.size)
                WsProtocol.writeFrame(out, it.opcode, it.payload)
                if (it.opcode == WsProtocol.OP_CLOSE) break
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
        try {
            while (true) {
                val f = WsProtocol.readFrame(input, cfg.maxFrameBytes) ?: break
                lastInboundMs = System.currentTimeMillis()
                when (f.opcode) {
                    WsProtocol.OP_PING -> queue.add(Item(WsProtocol.OP_PONG, f.payload))
                    WsProtocol.OP_PONG -> {}
                    WsProtocol.OP_CLOSE -> { close(1000, "au revoir"); break }
                    WsProtocol.OP_BINARY -> throw WsError(1003, "texte seulement")
                    WsProtocol.OP_TEXT, WsProtocol.OP_CONT -> {
                        if (f.opcode == WsProtocol.OP_TEXT) { if (partialOp >= 0) throw WsError(1002, "fragment attendu"); partialOp = WsProtocol.OP_TEXT; partial.reset() }
                        else if (partialOp < 0) throw WsError(1002, "fragment orphelin")
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
            if (!closing) { closing = true; queue.clear(); queue.add(Item(WsProtocol.OP_CLOSE, WsProtocol.closePayload(1001, "fin"))) }
            runCatching { writer.join(2_000) }
            runCatching { socket.close() }
        }
    }
}
