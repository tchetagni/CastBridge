package castbridge.core.tv.activation

import castbridge.core.ssh.Lan
import castbridge.core.tv.Pin
import fi.iki.elonen.NanoHTTPD
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Who may hold a connection to the locked activation route (audit L1 of commit 24c766e1): local addresses only, at most [perAddress] connections at the same time per
 * address. Counts are dropped as soon as they fall to zero, so the table holds only the connections open right now (bounded by the server's pool).
 */
class ConnectionGate(private val perAddress: Int = PER_ADDRESS) {
    private val open = HashMap<String, Int>()

    @Synchronized fun acquire(ip: String?): Boolean {
        if (ip.isNullOrEmpty() || !Lan.isLocal(ip)) return false
        val n = open[ip] ?: 0
        if (n >= perAddress) return false
        open[ip] = n + 1; return true
    }

    @Synchronized fun release(ip: String?) {
        if (ip == null) return
        val n = open[ip] ?: return
        if (n <= 1) open.remove(ip) else open[ip] = n - 1
    }

    @Synchronized fun tracked(): Int = open.size

    companion object { const val PER_ADDRESS = 2 }
}

/**
 * The async runner of [LockedActivationServer]: a connection the [ConnectionGate] refuses is closed at once, before its handler runs (so before any header is read);
 * the others run on a small bounded pool. The gate is released exactly once per admitted connection (when it ends, or when the pool refuses it).
 */
class LocalOnlyRunner(private val gate: ConnectionGate, maxThreads: Int, queue: Int = 8) : NanoHTTPD.AsyncRunner {
    private val admitted = java.util.concurrent.ConcurrentHashMap<NanoHTTPD.ClientHandler, String>()
    private val pool = ThreadPoolExecutor(maxThreads, maxThreads, 30, TimeUnit.SECONDS, LinkedBlockingQueue(queue)) { r ->
        Thread(r, "cb-activation-http").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    override fun closeAll() { admitted.keys.toList().forEach { it.close() }; pool.shutdownNow() }

    override fun closed(clientHandler: NanoHTTPD.ClientHandler) { admitted.remove(clientHandler)?.let { gate.release(it) } }

    override fun exec(clientHandler: NanoHTTPD.ClientHandler) {
        val ip = (clientHandler as? LockedActivationServer.Tagged)?.ip
        if (!gate.acquire(ip)) { clientHandler.close(); return }
        admitted[clientHandler] = ip!!
        try { pool.execute(clientHandler) } catch (e: RejectedExecutionException) { closed(clientHandler); clientHandler.close() }
    }
}

/**
 * Audit M1 d: the connection code shown on the activation screen of a LOCKED TV (and accepted by its Wi-Fi route) is replaced once the TV is activated, so a code seen
 * or guessed while the TV was waiting for its key does not open the full API afterwards. Only when the locked route was really opened ([Store.exposed]); a TV that
 * never ran it keeps its code (phones already linked are not disturbed). The phone that sent the key over the Wi-Fi never stored the code ([castbridge.core.owner.ActivationSend]).
 */
object LockedPinRotation {
    interface Store {
        fun exposed(): Boolean
        fun markExposed()
        /** Writes [newPin] AND clears the mark in one operation; false = nothing changed (the old code stays valid). */
        fun replace(newPin: String): Boolean
    }

    fun onLockedRouteOpened(s: Store) = s.markExposed()

    /** The new code, or null when nothing changed (route never opened, invalid code, failed write). */
    fun onActivated(s: Store, generate: () -> String = { Pin.generate() }): String? {
        if (!s.exposed()) return null
        val p = generate()
        if (!Pin.isValidFormat(p)) return null
        return if (s.replace(p)) p else null
    }
}
