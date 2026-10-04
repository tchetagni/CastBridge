package castbridge.play.poc.client

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * SOCKS5 de TEST qui simule la liaison de la passerelle Bluetooth en EDGE (DESIGN-W20-AMENDEMENT § 2.7) : latence par sens ([latencyMs] ± [jitterMs]), bande passante
 * bornée par sens ([bytesPerSec], 5 000 = 40 kbps ; 0 = illimitée), coût d'ouverture d'une connexion (un aller-retour), coupures ([cut] : les connexions sont rompues, les
 * nouvelles refusées pendant la durée). Tout le trafic de la « TV » passe par UNE liaison : la capacité de chaque sens est partagée par toutes ses connexions.
 *
 * Elle MESURE aussi : l'instant où le service écrit chaque évènement SSE ([ingress], avant la mise en forme : c'est « l'envoi serveur »), les octets par type de message
 * serveur ([downByType]), les octets envoyés par la TV, le débit moyen et de pointe par seconde. Elle est entièrement en octets bruts : aucune confiance, aucun secret retenu.
 * Les connexions sont relayées vers [connectTo] quelle que soit la cible demandée.
 */
class SlowSocksProxy(
    private val connectTo: InetSocketAddress,
    @Volatile var latencyMs: Long = 0, @Volatile var jitterMs: Long = 0, @Volatile var bytesPerSec: Long = 0, @Volatile var coalesceStates: Boolean = false, @Volatile var controlFirst: Boolean = false,
    seed: Long = 1,
) : AutoCloseable {
    private val rnd = Random(seed)
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    @Volatile private var running = true
    @Volatile private var cutUntil = 0L
    private val sockets: MutableSet<Socket> = Collections.synchronizedSet(HashSet())
    /** Connexions SOCKS acceptées (une connexion HTTP gardée vivante n'en compte qu'une). */
    val connections = AtomicInteger()
    val refusedDuringCut = AtomicInteger()
    /** `state` remplacés dans la file par un plus récent (coalescence) : jamais envoyés. */
    val droppedStates = AtomicInteger()
    val upBytes = AtomicLong()
    val downBytes = AtomicLong()
    private val t0 = System.currentTimeMillis()
    private val downPerSec = ConcurrentHashMap<Long, AtomicLong>()
    private val upPerSec = ConcurrentHashMap<Long, AtomicLong>()

    /** Évènements SSE écrits par le service : (type, clé, octets de l'évènement, instant d'écriture côté service). */
    class Ev(val type: String, val key: String, val bytes: Int, val ingressMs: Long)
    val events = CopyOnWriteArrayList<Ev>()
    /** Clé `type|seq[|ref]` → instant où le service a écrit l'évènement (l'envoi serveur). */
    val ingress = ConcurrentHashMap<String, Long>()
    /** `PHASE|index` → instant où le service a écrit le PREMIER `state` de cette phase du Duel (révélation, classement). */
    val phaseIngress = ConcurrentHashMap<String, Long>()
    /** Taille de chaque `state` écrit par le service, dans l'ordre, avec le nombre de joueurs de la vue. */
    val stateSizes = CopyOnWriteArrayList<Pair<Int, Int>>()
    val downByType = ConcurrentHashMap<String, AtomicLong>()
    val countByType = ConcurrentHashMap<String, AtomicInteger>()
    /** Corps des POST de la TV, par type de message client : (nombre, octets de corps). */
    val postBodies = ConcurrentHashMap<String, AtomicLong>()
    val postCounts = ConcurrentHashMap<String, AtomicInteger>()
    /** Lignes de requête vues sur la liaison (méthode + adresse) : aucune ne doit contenir un secret. */
    val requestLines = CopyOnWriteArrayList<String>()

    private val nextFreeUp = AtomicLong(); private val nextFreeDown = AtomicLong()

    init { Thread({ acceptLoop() }, "slow-socks-accept").apply { isDaemon = true; start() } }

    /** Coupure (« changement de cellule ») : les connexions en cours sont rompues et les nouvelles refusées pendant [ms]. */
    fun cut(ms: Long) { cutUntil = System.currentTimeMillis() + ms; synchronized(sockets) { sockets.toList() }.forEach { runCatching { it.close() } } }

    fun avgDownBytesPerSec(fromMs: Long = t0, toMs: Long = System.currentTimeMillis()) = downBytes.get() * 1000.0 / maxOf(1L, toMs - fromMs)
    fun peakDownBytesPerSec(): Long = downPerSec.values.maxOfOrNull { it.get() } ?: 0L
    fun peakUpBytesPerSec(): Long = upPerSec.values.maxOfOrNull { it.get() } ?: 0L

    override fun close() { running = false; runCatching { server.close() }; synchronized(sockets) { sockets.toList() }.forEach { runCatching { it.close() } } }

    private fun acceptLoop() {
        while (running) {
            val c = try { server.accept() } catch (_: IOException) { return }
            Thread({ serve(c) }, "slow-socks-conn").apply { isDaemon = true; start() }
        }
    }

    private fun delay(): Long = (latencyMs + if (jitterMs > 0) (rnd.nextInt((2 * jitterMs + 1).toInt()) - jitterMs) else 0L).coerceAtLeast(0L)

    private fun serve(client: Socket) {
        sockets += client
        var upstream: Socket? = null
        try {
            if (System.currentTimeMillis() < cutUntil) { refusedDuringCut.incrementAndGet(); client.close(); return }
            val cin = client.getInputStream(); val cout = client.getOutputStream()
            // SOCKS5 : accueil, puis CONNECT (adresse ignorée : tout va vers `connectTo`)
            if (cin.read() != 5) return
            val n = cin.read(); cin.readNBytes(n)
            cout.write(byteArrayOf(5, 0)); cout.flush()
            cin.readNBytes(3)
            when (cin.read()) { 1 -> cin.readNBytes(4); 3 -> cin.readNBytes(cin.read()); 4 -> cin.readNBytes(16) }
            cin.readNBytes(2)
            connections.incrementAndGet()
            Thread.sleep(2 * delay())                                   // l'ouverture coûte un aller-retour
            if (System.currentTimeMillis() < cutUntil) { refusedDuringCut.incrementAndGet(); return }
            upstream = Socket().also { it.connect(connectTo, 5_000); sockets += it }
            cout.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); cout.flush()
            val up = upstream
            val t = Thread({ pump(up.getInputStream(), cout, down = true) }, "slow-socks-down").apply { isDaemon = true; start() }
            pump(cin, up.getOutputStream(), down = false)
            t.join(100)
        } catch (_: Exception) {
        } finally { sockets -= client; runCatching { client.close() }; upstream?.let { sockets -= it; runCatching { it.close() } } }
    }

    /** Une unité de la file de la liaison : un bloc SSE entier (`type` = type du message serveur) ou un morceau d'octets ; `len < 0` = fin. */
    private class Frame(val data: ByteArray, val len: Int, val type: String?)
    private class Wire(val data: ByteArray, val len: Int, val at: Long)

    /**
     * Lit [src] et écrit sur [dst] en deux étages : SÉRIALISATION (bande passante, partagée par toutes les connexions du même sens ; c'est là que la file attend) puis
     * PROPAGATION (latence ± gigue, ordre conservé). `down` = du service vers la TV. Un flux SSE est lu bloc par bloc ; avec [coalesceStates], un `state` encore en file
     * (sa sérialisation n'a pas commencé) est REMPLACÉ par le suivant : c'est exactement la coalescence « dernier état » de w20-04b, posée au point de congestion.
     */
    private fun pump(src: InputStream, dst: OutputStream, down: Boolean) {
        val pending = java.util.LinkedList<Frame>()
        val lock = Object()
        val delivery = LinkedBlockingQueue<Wire>()
        val free = if (down) nextFreeDown else nextFreeUp
        val deliverer = Thread({
            try {
                while (true) {
                    val w = delivery.take()
                    if (w.len < 0) break
                    val wait = w.at - System.currentTimeMillis(); if (wait > 0) Thread.sleep(wait)
                    dst.write(w.data, 0, w.len); dst.flush()
                    val sec = (System.currentTimeMillis() - t0) / 1000
                    (if (down) downPerSec else upPerSec).computeIfAbsent(sec) { AtomicLong() }.addAndGet(w.len.toLong())
                    (if (down) downBytes else upBytes).addAndGet(w.len.toLong())
                }
            } catch (_: Exception) { runCatching { src.close() } }
        }, "slow-socks-deliver").apply { isDaemon = true; start() }
        val serializer = Thread({
            var lastAt = 0L
            try {
                while (true) {
                    val u = synchronized(lock) { while (pending.isEmpty()) lock.wait(); pending.removeFirst() }
                    if (u.len < 0) { delivery.put(Wire(u.data, -1, 0)); break }
                    val ser = if (bytesPerSec > 0) u.len * 1000L / bytesPerSec else 0L
                    val end = synchronized(free) { (maxOf(free.get(), System.currentTimeMillis()) + ser).also { free.set(it) } }
                    val wait = end - System.currentTimeMillis(); if (wait > 0) Thread.sleep(wait)
                    val at = maxOf(lastAt, end + delay()); lastAt = at
                    delivery.put(Wire(u.data, u.len, at))
                }
            } catch (_: Exception) { runCatching { src.close() } }
        }, "slow-socks-serialize").apply { isDaemon = true; start() }
        fun offer(u: Frame) = synchronized(lock) {
            if (coalesceStates && u.type == "state") { val it = pending.iterator(); while (it.hasNext()) if (it.next().type == "state") { it.remove(); droppedStates.incrementAndGet() } }
            // file de sortie : les messages de COMMANDE (ack, question, reveal, ping, erreurs) passent devant les `state` encore en file (jamais devant celui qui est déjà parti)
            val firstState = if (controlFirst && u.type != null && u.type != "state") pending.indexOfFirst { it.type == "state" } else -1
            if (firstState >= 0) pending.add(firstState, u) else pending.addLast(u)
            lock.notifyAll()
        }
        val sse = StringBuilder()
        var sseMode = false
        val raw = java.io.ByteArrayOutputStream()   // octets d'un flux SSE pas encore découpés en blocs
        try {
            val buf = ByteArray(1460)
            while (true) {
                val n = src.read(buf); if (n < 0) break
                val now = System.currentTimeMillis()
                if (!down) { observeUp(buf, n); offer(Frame(buf.copyOf(n), n, null)); continue }
                observeDown(buf, n, sse, now)
                if (!sseMode && String(buf, 0, n, Charsets.ISO_8859_1).contains("text/event-stream")) sseMode = true
                if (!sseMode) { offer(Frame(buf.copyOf(n), n, null)); continue }
                raw.write(buf, 0, n)
                // découpe : l'en-tête HTTP + `retry:` d'abord, puis un bloc par ligne vide (`\n\n`)
                while (true) {
                    val bytes = raw.toByteArray(); val text = String(bytes, Charsets.ISO_8859_1)
                    val end = text.indexOf("\n\n"); if (end < 0) break
                    val block = bytes.copyOfRange(0, end + 2); raw.reset(); raw.write(bytes, end + 2, bytes.size - end - 2)
                    val type = Regex("event: ([a-zA-Z]+)").find(String(block, Charsets.ISO_8859_1))?.groupValues?.get(1)
                    offer(Frame(block, block.size, type))
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { synchronized(lock) { pending.addLast(Frame(ByteArray(0), -1, null)); lock.notifyAll() } }
            serializer.join(2_000); deliverer.join(2_000); runCatching { dst.close() }
        }
    }

    // ------------------------------------------------------------------ mesures (au point d'entrée de la liaison, avant la mise en forme)

    private val sseEvent = Regex("event: ([a-zA-Z]+)\ndata: (\\{.*\\})\n\n", RegexOption.DOT_MATCHES_ALL)

    private fun observeDown(b: ByteArray, n: Int, sse: StringBuilder, now: Long) {
        sse.append(String(b, 0, n, Charsets.UTF_8))
        while (true) {
            val end = sse.indexOf("\n\n"); if (end < 0) break
            val block = sse.substring(0, end + 2); sse.delete(0, end + 2)
            val ev = Regex("event: ([a-zA-Z]+)").find(block)?.groupValues?.get(1) ?: continue
            val data = block.substringAfter("data: ", "")
            val seq = Regex("\"seq\":(\\d+)").find(data)?.groupValues?.get(1) ?: "0"
            val ref = Regex("\"ref\":(\\d+)").find(data)?.groupValues?.get(1)
            val key = "$ev|$seq" + (ref?.let { "|$it" } ?: "")
            val size = block.toByteArray(Charsets.UTF_8).size
            events += Ev(ev, key, size, now); ingress.putIfAbsent(key, now)
            if (ev == "state") {
                Regex("\"duel\":\\{\"phase\":\"(\\w+)\",\"index\":(\\d+)").find(data)?.let { phaseIngress.putIfAbsent(it.groupValues[1] + "|" + it.groupValues[2], now) }
                stateSizes += size to Regex("\"id\":\"p\\d+\",\"name\"").findAll(data.substringBefore("\"game\"")).count()
            }
            downByType.computeIfAbsent(ev) { AtomicLong() }.addAndGet(size.toLong()); countByType.computeIfAbsent(ev) { AtomicInteger() }.incrementAndGet()
        }
        if (sse.length > 200_000) sse.setLength(0)
    }

    private fun observeUp(b: ByteArray, n: Int) {
        val text = String(b, 0, n, Charsets.ISO_8859_1)
        Regex("^(GET|POST) (\\S+) HTTP").find(text)?.let { requestLines += it.groupValues[1] + " " + it.groupValues[2] }
        val i = text.indexOf("{\"t\":\""); if (i < 0) return
        val type = text.substring(i + 6).substringBefore('"')
        postBodies.computeIfAbsent(type) { AtomicLong() }.addAndGet((n - i).toLong()); postCounts.computeIfAbsent(type) { AtomicInteger() }.incrementAndGet()
    }
}

/**
 * Façade HTTP/1.1 de TEST qui joue le rôle de nginx devant le service : le client garde SA connexion ouverte (keep-alive) alors que le service, lui, répond
 * `Connection: close` (une requête par connexion). Chaque requête du client ouvre une connexion neuve vers le service ; les réponses `text/event-stream` sont relayées
 * telles quelles jusqu'à la fermeture. Sans elle, chaque POST de la TV paierait une ouverture de connexion complète.
 */
class KeepAliveFront(private val backend: InetSocketAddress, private val injectTicket: (() -> String)? = null) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = server.localPort
    @Volatile private var running = true
    private val socks: MutableSet<Socket> = Collections.synchronizedSet(HashSet())
    val requests = AtomicInteger()

    init { Thread({ while (running) { val c = try { server.accept() } catch (_: IOException) { return@Thread }; Thread({ serve(c) }, "front-conn").apply { isDaemon = true; start() } } }, "front-accept").apply { isDaemon = true; start() } }

    override fun close() { running = false; runCatching { server.close() }; synchronized(socks) { socks.toList() }.forEach { runCatching { it.close() } } }

    private fun readHead(i: InputStream): String? {
        val sb = java.io.ByteArrayOutputStream(); var last = 0
        while (true) { val b = i.read(); if (b < 0) return if (sb.size() == 0) null else null; sb.write(b); last = (last shl 8) or b; if (last == 0x0d0a0d0a) return sb.toString(Charsets.ISO_8859_1) }
    }

    private fun serve(c: Socket) {
        socks += c
        try {
            val cin = java.io.BufferedInputStream(c.getInputStream()); val cout = c.getOutputStream()
            while (running) {
                var head = readHead(cin) ?: return
                // modèle d'un service qui ne juge le ticket qu'à la création de la session : la façade ajoute le ticket aux POST qui n'en portent pas (la liaison ne le voit jamais)
                if (injectTicket != null && head.startsWith("POST ") && !head.contains("x-play-ticket", ignoreCase = true)) head = head.removeSuffix("\r\n") + "X-Play-Ticket: ${injectTicket.invoke()}\r\n\r\n"
                requests.incrementAndGet()
                val len = Regex("(?i)content-length: *(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                val body = cin.readNBytes(len)
                Socket().use { b ->
                    b.connect(backend, 5_000)
                    b.getOutputStream().write(head.toByteArray(Charsets.ISO_8859_1) + body); b.getOutputStream().flush()
                    val bin = java.io.BufferedInputStream(b.getInputStream())
                    val rhead = readHead(bin) ?: return
                    if (rhead.contains("text/event-stream", ignoreCase = true)) {     // flux : relayé tel quel jusqu'à la fin, puis la connexion du client se ferme
                        cout.write(rhead.toByteArray(Charsets.ISO_8859_1)); cout.flush()
                        val buf = ByteArray(1460)
                        while (true) { val n = bin.read(buf); if (n < 0) break; cout.write(buf, 0, n); cout.flush() }
                        return
                    }
                    val rlen = Regex("(?i)content-length: *(\\d+)").find(rhead)?.groupValues?.get(1)?.toInt() ?: 0
                    val rbody = bin.readNBytes(rlen)
                    val h = rhead.replace(Regex("(?i)connection: *close\r\n"), "Connection: keep-alive\r\nKeep-Alive: timeout=60\r\n")
                    cout.write(h.toByteArray(Charsets.ISO_8859_1) + rbody); cout.flush()
                }
            }
        } catch (_: Exception) {
        } finally { socks -= c; runCatching { c.close() } }
    }
}
