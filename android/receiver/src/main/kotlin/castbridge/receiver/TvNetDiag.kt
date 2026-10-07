package castbridge.receiver

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

/**
 * Connectivity test of a network path (the TV's own network, or the phone's through the Bluetooth gateway when [proxy]
 * is set): DNS, TCP, HTTP, HTTPS, public address; ping and traceroute only on the TV's own network (ICMP cannot cross
 * the TCP tunnel — the phone runs those for the gateway path). Every step reports in plain words with its time.
 */
object TvNetDiag {
    private fun ms(t0: Long) = (System.nanoTime() - t0) / 1_000_000

    /**
     * This request leaves for a third party (connectivitycheck.gstatic.com), so it is NOT sent by default (offline profile, audit SE-7): only on a manual action (Tests Internet screen),
     * when the « netProbe » setting is on, or once the terms of the remote-assistance tunnel were accepted (`TvService.netTick`: `probe = manual || netProbe || termsAccepted`).
     * In the last two cases it IS periodic, but only on the TV's OWN network and only every 5 minutes (castbridge.core.connect.NetProbePlan; it was every 60 s, 10 to 30 s while offline),
     * or at once when the network changes. Through a phone's gateway it is NEVER sent any more (it cost mobile data and woke the phone's radio every minute): see [probeServer].
     * One quick HTTP check (204 expected) on a path; returns the time in ms, or null if Internet does not answer. */
    fun probe(proxy: Proxy?): Long? = runCatching {
        val t0 = System.nanoTime()
        (URL("http://connectivitycheck.gstatic.com/generate_204").openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).run {
            connectTimeout = 6000; readTimeout = 6000; instanceFollowRedirects = false
            val c = responseCode; disconnect()
            if (c == 204) ms(t0) else null
        }
    }.getOrNull()

    /** Result of [probeServer]: the time in ms (any HTTP answer counts), or why there is none. [authBroken] = the local SOCKS refused our token (see [BtGatewayHost.disableSocksToken]). */
    class ServerProbe(val ms: Long?, val authBroken: Boolean = false)

    /**
     * relay-R1 (consigne du coordinateur après l'hygiène R4): the end-to-end check of a phone's pipe goes to the PROJECT's server, never to a third party: one tiny public GET
     * (`/api/v1/updates/public-key`, about 250 bytes) on the base address of the TV. Any HTTP answer proves TCP, TLS (certificate checked, no custom trust) and HTTP through the pipe.
     * It is made once per new pipe, after a real call failed through it, on a manual test, and while an operation waits for Internet (castbridge.core.connect.NetProbePlan): never periodically.
     */
    fun probeServer(base: String, proxy: Proxy?): ServerProbe {
        val t0 = System.nanoTime()
        return try {
            (URL(base.trimEnd('/') + "/api/v1/updates/public-key").openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).run {
                connectTimeout = 10_000; readTimeout = 10_000; instanceFollowRedirects = false; useCaches = false
                setRequestProperty("User-Agent", "CastBridge-TV")
                val code = responseCode
                runCatching { (if (code < 400) inputStream else errorStream)?.use { it.readBytes() } }
                disconnect()
                ServerProbe(if (code in 100..599) ms(t0) else null)
            }
        } catch (e: java.net.SocketException) {
            val m = e.message.orEmpty()
            ServerProbe(null, authBroken = m.contains("authentication failed", ignoreCase = true) || m.contains("No acceptable methods", ignoreCase = true))
        } catch (e: Exception) { ServerProbe(null) }
    }

    /** How the TV itself is connected, in plain words (Wi-Fi name if readable, Ethernet, or none). */
    fun localLink(ctx: android.content.Context): String = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "aucun réseau"
        when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "câble Ethernet"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "réseau"
        }
    }.getOrDefault("réseau")

    /** The same, as a [castbridge.core.net.LinkKind] for the Internet state badge. */
    fun linkKind(ctx: android.content.Context): castbridge.core.net.LinkKind = runCatching {
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return castbridge.core.net.LinkKind.NONE
        castbridge.core.net.NetStateTracker.linkKind(caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET),
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI), true)
    }.getOrDefault(castbridge.core.net.LinkKind.OTHER)

    fun run(host: String, proxy: Proxy?, out: (String) -> Unit) {
        val via = proxy != null
        // DNS (through the gateway the phone resolves names itself: nothing to test here)
        if (!via) {
            val t0 = System.nanoTime()
            runCatching { InetAddress.getAllByName("www.google.com").first().hostAddress }
                .onSuccess { out("✓ DNS : www.google.com → $it (${ms(t0)} ms)") }
                .onFailure { out("✗ DNS : aucun serveur de noms ne répond (${it.javaClass.simpleName})") }
        }
        // TCP
        for ((h, port) in listOf(host to 53, "www.google.com" to 443)) {
            val t0 = System.nanoTime()
            runCatching {
                (if (proxy != null) Socket(proxy) else Socket()).use { s ->
                    s.connect(if (proxy != null) InetSocketAddress.createUnresolved(h, port) else InetSocketAddress(h, port), 8000)
                }
            }.onSuccess { out("✓ Connexion TCP $h:$port (${ms(t0)} ms)") }
                .onFailure { out("✗ Connexion TCP $h:$port impossible (${it.javaClass.simpleName})") }
        }
        // HTTP / HTTPS
        for (u in listOf("http://connectivitycheck.gstatic.com/generate_204", "https://www.google.com/generate_204")) {
            val t0 = System.nanoTime()
            runCatching {
                (URL(u).openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).run {
                    connectTimeout = 8000; readTimeout = 8000; instanceFollowRedirects = false
                    responseCode.also { disconnect() }
                }
            }.onSuccess { c -> out((if (c == 204) "✓ " else "⚠ ") + "${if (u.startsWith("https")) "HTTPS" else "HTTP"} : réponse $c (${ms(t0)} ms)" +
                if (c != 204) " — un portail captif ou un filtre intercepte peut-être la connexion" else "") }
                .onFailure { out("✗ ${if (u.startsWith("https")) "HTTPS" else "HTTP"} : pas de réponse (${it.javaClass.simpleName})") }
        }
        // Public address
        runCatching {
            (URL("https://api.ipify.org").openConnection(proxy ?: Proxy.NO_PROXY) as HttpURLConnection).run {
                connectTimeout = 8000; readTimeout = 8000; inputStream.bufferedReader().use { it.readText().trim() }
            }
        }.onSuccess { out("✓ Adresse publique : $it") }.onFailure { out("✗ Adresse publique : inconnue") }
        if (via) return
        // Ping and traceroute (ICMP, from the TV)
        runCatching {
            val p = ProcessBuilder("/system/bin/ping", "-c", "3", "-W", "2", host).redirectErrorStream(true).start()
            p.inputStream.bufferedReader().forEachLine { if (it.isNotBlank()) out("  $it") }; p.waitFor()
        }.onFailure { out("ping indisponible sur la TV : ${it.message}") }
        out("Traceroute vers $host (15 sauts max) :")
        for (ttl in 1..15) {
            val t0 = System.nanoTime()
            val text = runCatching {
                val p = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", "-t", ttl.toString(), host).redirectErrorStream(true).start()
                p.inputStream.bufferedReader().readText().also { p.waitFor() }
            }.getOrDefault("")
            val from = Regex("[Ff]rom ([0-9a-fA-F.:]+)").find(text)?.groupValues?.get(1)?.trimEnd(':')
            val time = Regex("time=([0-9.]+) ?ms").find(text)?.groupValues?.get(1)
            when {
                time != null -> { out("  %2d  %s  %s ms".format(ttl, host, time)); out("  Arrivé en $ttl sauts."); return }
                from != null -> out("  %2d  %s  ~%d ms".format(ttl, from, ms(t0)))
                else -> out("  %2d  *".format(ttl))
            }
        }
        out("  Destination non atteinte en 15 sauts (ICMP peut être filtré par la box ou l'opérateur).")
    }
}
