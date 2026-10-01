package castbridge.core.tunnel

import castbridge.core.ssh.ByteRelay
import castbridge.core.tv.Link
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Phone side of a TCP-over-Bluetooth service: listens on a local port (127.0.0.1 unless asked otherwise) and joins every TCP
 * connection, byte for byte, to a fresh RFCOMM link to the TV ([dial]). The phone only sees the bytes: HTTP (PIN / token) and SSH
 * (keys) stay between the client and the TV. Pure logic: Android only supplies [dial] (a secure RFCOMM socket) and shows [state].
 *
 * Every failure is explicit: a French message in [State.message] and one INFO line through [log]; a link that cannot be opened,
 * does not answer, or is closed by the TV leaves nothing behind (sockets closed, counters released).
 */
class TunnelGateway(
    private val label: String,
    /** The TV answers a [TunnelStatus] byte first (API service). The SSH service carries raw SSH bytes only. */
    private val handshake: Boolean,
    /** Opens a secure RFCOMM link to the TV's service. May block; may throw [IOException]. */
    private val dial: () -> Link,
    private val maxLinks: Int = 4,
    /** BluetoothSocket.connect() has no timeout: after this long the attempt is abandoned and its late result closed. */
    private val dialTimeoutMs: Long = 20_000,
    private val handshakeTimeoutMs: Long = 10_000,
    /** A link silent this long is closed (the TV applies its own limit too). */
    private val idleMs: Long = 120_000,
    private val bufferBytes: Int = 16 * 1024,
    private val log: (String) -> Unit = {},
    private val onChange: () -> Unit = {},
    private val watchStepMs: Long = 1000,
) {
    data class State(val running: Boolean = false, val listen: String = "", val active: Int = 0, val up: Long = 0, val down: Long = 0,
                     val message: String? = null)

    @Volatile var state = State(); private set
    private var server: ServerSocket? = null
    private val relays = CopyOnWriteArrayList<ByteRelay>()
    private val doneUp = AtomicLong(); private val doneDown = AtomicLong()
    private val stopping = AtomicBoolean(false)
    private val dialer = Executors.newCachedThreadPool { r -> Thread(r, "bt-$label-dial").apply { isDaemon = true } }

    /** Starts listening; returns the bound port. Throws [IOException] if the port is taken (the caller says so in French). */
    @Synchronized fun start(host: String, port: Int): Int {
        check(server == null) { "already started" }
        stopping.set(false)
        val ss = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getByName(host), port), 8) }
        server = ss
        update(State(running = true, listen = "$host:${ss.localPort}"))
        thread(name = "bt-$label-accept", isDaemon = true) {
            while (!stopping.get()) {
                val c = try { ss.accept() } catch (e: IOException) { break }
                thread(name = "bt-$label-link", isDaemon = true) { serve(c) }
            }
        }
        return ss.localPort
    }

    @Synchronized fun stop() {
        stopping.set(true)
        runCatching { server?.close() }; server = null
        relays.forEach { it.close() }
        dialer.shutdownNow()
        update(state.copy(running = false, active = 0))
    }

    private fun update(s: State) { state = s; runCatching(onChange) }
    private fun fail(msg: String) { log("$label: $msg"); update(state.copy(message = msg)) }
    private fun counters() { update(state.copy(active = relays.size, up = doneUp.get() + relays.sumOf { it.bytesAtoB.get() }, down = doneDown.get() + relays.sumOf { it.bytesBtoA.get() })) }

    /** Opens the link with a time limit; null (with the message set) when it fails. */
    private fun open(): Link? {
        val f = dialer.submit<Link> { dial() }
        return try { f.get(dialTimeoutMs, TimeUnit.MILLISECONDS) }
        catch (e: TimeoutException) {
            // not cancelled (a Bluetooth connect cannot be interrupted): the connect may still complete later: close what it returns so no RFCOMM link is left open
            dialer.execute { runCatching { f.get(60, TimeUnit.SECONDS).close() } }
            fail("la TV ne répond pas en Bluetooth (délai de ${dialTimeoutMs / 1000} s dépassé) : TV allumée et à portée ? Bluetooth actif des deux côtés ?"); null
        } catch (e: java.util.concurrent.ExecutionException) {
            val c = e.cause
            fail(if (c is BtDialException) c.message ?: "liaison Bluetooth impossible"
                 else "liaison Bluetooth impossible (${c?.javaClass?.simpleName ?: "?"}${c?.message?.let { ": $it" } ?: ""}) : TV allumée, appairée, service actif sur la TV ?"); null
        } catch (e: InterruptedException) { null }
    }

    private fun serve(client: Socket) {
        val t0 = System.currentTimeMillis()
        var link: Link? = null
        try {
            if (relays.size >= maxLinks) { fail("trop de connexions simultanées sur le téléphone (max $maxLinks)"); return }
            link = open() ?: return
            if (handshake) {
                val code = readStatus(link) ?: return
                if (code != TunnelStatus.OK) { fail("la TV refuse : ${TunnelStatus.describe(code)}"); return }
            }
            client.tcpNoDelay = true
            val l = link
            val r = ByteRelay(client.getInputStream(), client.getOutputStream(), l.input, l.output, { client.close() }, { l.close() }, bufferBytes, "gw-$label")
            relays += r; counters()
            r.start()
            val watch = thread(isDaemon = true, name = "bt-$label-gwwatch") {
                try {
                    var n = 0
                    while (!r.isClosed) {
                        Thread.sleep(watchStepMs)
                        if (++n % 2 == 0) counters()
                        if (System.currentTimeMillis() - r.lastActivity > idleMs) { fail("liaison Bluetooth fermée après ${idleMs / 1000} s sans échange"); r.close(); break }
                    }
                } catch (_: InterruptedException) {}
            }
            r.join()
            watch.interrupt()
            relays -= r
            doneUp.addAndGet(r.bytesAtoB.get()); doneDown.addAndGet(r.bytesBtoA.get())
            // raw service (SSH): the TV speaks first, so "closed with nothing received" means it refused the link before any data
            if (!handshake && r.bytesBtoA.get() == 0L && System.currentTimeMillis() - t0 < 10_000)
                fail("la TV a fermé la liaison avant toute donnée : SSH désactivé ou arrêté sur la TV, déjà 2 connexions ouvertes, ou client verrouillé (voir le journal de la TV)")
            else log("$label: link closed (up ${r.bytesAtoB.get()} B, down ${r.bytesBtoA.get()} B)")
            counters()
        } finally {
            runCatching { client.close() }
            runCatching { link?.close() }
        }
    }

    /** Reads the TV's status byte (with a time limit); null (message set) when it does not come. */
    private fun readStatus(link: Link): Int? {
        val f = dialer.submit<Int> { link.input.read() }
        return try {
            val c = f.get(handshakeTimeoutMs, TimeUnit.MILLISECONDS)
            if (c < 0) { fail("la TV a fermé la liaison sans réponse : service « CastBridge API » absent (CastBridge TV trop ancien ?) ou arrêté"); null } else c
        } catch (e: TimeoutException) { f.cancel(true); fail("la TV n'a pas répondu à la liaison Bluetooth (délai de ${handshakeTimeoutMs / 1000} s)"); null
        } catch (e: Exception) { fail("liaison Bluetooth coupée par la TV (${(e.cause ?: e).javaClass.simpleName})"); null }
    }
}

/** A failure of [TunnelGateway.dial] whose message is already a clear French sentence for the panel. */
class BtDialException(message: String) : IOException(message)
