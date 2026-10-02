package castbridge.sshd

import castbridge.core.owner.SafeFile
import castbridge.core.tunnel.Enrollment
import castbridge.core.tunnel.HostKeyPin
import castbridge.core.tunnel.HostKeyPins
import castbridge.core.tunnel.TunnelException
import castbridge.core.tunnel.TunnelSession
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.apache.sshd.client.ClientBuilder
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.core.CoreModuleProperties
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The SSH CLIENT of CastBridge-TV's remote administration (docs/REMOTE-TUNNEL-TV.md): connects OUT to the editor's server as the tunnel account, with a key pair that belongs to the tunnel only
 * (created at the first need, ed25519, stored in the app's private folder with mode 0600; it is NOT the activation key), checks the host key ([HostKeyPin]) and asks for the remote forward EXACTLY
 * `127.0.0.1:<port>` -> `127.0.0.1:<localPort>` where the tunnel-only sshd of the TV listens. 30 s keep-alive. Nothing is ever opened on the TV for the outside.
 */
class TunnelSshClient(private val dataDir: File, private val log: (String) -> Unit = {}) {
    private val seedFile get() = File(dataDir, "tunnel_key")

    init {
        dataDir.mkdirs()
        if (System.getProperty("user.home").isNullOrBlank()) System.setProperty("user.home", dataDir.absolutePath)
        org.apache.sshd.common.util.security.SecurityUtils.isEDDSACurveSupported()
    }

    private val spec get() = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)

    /** The key pair of the tunnel: created on first use. A damaged file is replaced (the TV then enrolls again with the new key). */
    @Synchronized fun keyPair(): KeyPair {
        val seed = SafeFile.read(seedFile) { SEED.matches(it.trim()) }?.text?.trim()?.let { hex -> ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }
            ?: ByteArray(32).also { b ->
                SecureRandom().nextBytes(b)
                SafeFile.write(seedFile, b.joinToString("") { "%02x".format(it) } + "\n") { SEED.matches(it.trim()) }
                restrict(seedFile); restrict(SafeFile.bak(seedFile))
            }
        val priv = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, spec))
        val pub = EdDSAPublicKey(EdDSAPublicKeySpec(priv.a, spec))
        return KeyPair(pub, priv)
    }

    /** `ssh-ed25519 AAAA… castbridge-tv`: what is sent to the server at enrollment. */
    fun publicKeyLine(): String = PublicKeyEntry.toString(keyPair().public) + " castbridge-tv"

    /** Short identifier of the key (first 16 hex of its SHA-256), to know whether the enrollment still matches it. */
    fun keyId(): String = MessageDigest.getInstance("SHA-256").digest(PublicKeyEntry.toString(keyPair().public).toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    private fun restrict(f: File) { if (f.isFile) { f.setReadable(false, false); f.setWritable(false, false); f.setExecutable(false, false); f.setReadable(true, true); f.setWritable(true, true) } }

    /**
     * Opens the tunnel. [dial] = null: the SSH client connects to the server by itself (TV's own network). Otherwise every TCP connection is made by [dial] (the TV's SOCKS5 proxy towards the phone gateway)
     * and the SSH client talks to a loopback relay of that socket, so the key exchange and the host-key check are unchanged.
     * Throws [TunnelException] (kind says what failed).
     */
    fun open(e: Enrollment, pins: HostKeyPins, localPort: Int, dial: ((String, Int) -> Socket)?, onClosed: () -> Unit): TunnelSession {
        val relay = dial?.let { LoopRelay { it(e.host, e.sshPort) } }
        var client: SshClient? = null
        try {
            val kp = keyPair()
            var hostKeyProblem: String? = null
            val c = ClientBuilder.builder().keyExchangeFactories(NamedFactory.setUpTransformedFactories(true,
                listOf(BuiltinDHFactories.ecdhp256, BuiltinDHFactories.ecdhp384, BuiltinDHFactories.ecdhp521, BuiltinDHFactories.dhgex256, BuiltinDHFactories.dhg14_256), ClientBuilder.DH2KEX)).build()
            client = c
            c.serverKeyVerifier = ServerKeyVerifier { _, _, key ->
                val presented = KeyUtils.getFingerPrint(key)
                val v = HostKeyPin.decide(e.hostKeyFingerprint, pins.get(e.host, e.sshPort), presented)
                when (v.decision) {
                    HostKeyPin.Decision.REJECT -> { hostKeyProblem = v.reason; false }
                    HostKeyPin.Decision.ACCEPT_AND_PIN -> { runCatching { pins.pin(e.host, e.sshPort, presented) }; true }
                    HostKeyPin.Decision.ACCEPT -> true
                }
            }
            // the only thing the server may make this client do: hand the connections that arrive on OUR remote port to the local tunnel-only sshd (the filter sees the LOCAL target) (no agent, no X11, no other port)
            c.forwardingFilter = object : org.apache.sshd.server.forward.ForwardingFilter {
                override fun canForwardAgent(session: Session, requestType: String) = false
                override fun canForwardX11(session: Session, requestType: String) = false
                override fun canListen(address: SshdSocketAddress, session: Session) = false
                override fun canConnect(type: org.apache.sshd.server.forward.TcpForwardingFilter.Type, address: SshdSocketAddress, session: Session) =
                    type == org.apache.sshd.server.forward.TcpForwardingFilter.Type.Forwarded && address.port == localPort && address.hostName == "127.0.0.1"
            }
            CoreModuleProperties.HEARTBEAT_INTERVAL.set(c, Duration.ofSeconds(KEEPALIVE_S))
            CoreModuleProperties.HEARTBEAT_REQUEST.set(c, "keepalive@openssh.com")
            CoreModuleProperties.HEARTBEAT_REPLY_WAIT.set(c, Duration.ofSeconds(25))      // no answer to a keep-alive within 25 s: the session is closed, the machine reconnects
            CoreModuleProperties.IDLE_TIMEOUT.set(c, Duration.ofDays(3650))              // the heartbeat is the liveness check, not the idle timer
            CoreModuleProperties.AUTH_TIMEOUT.set(c, Duration.ofSeconds(60))
            c.start()
            val host = if (relay != null) "127.0.0.1" else e.host
            val port = if (relay != null) relay.port else e.sshPort
            val session: ClientSession = try {
                c.connect(e.user, host, port).verify(CONNECT_TIMEOUT_S, TimeUnit.SECONDS).session
            } catch (x: IOException) { throw TunnelException("connexion au serveur impossible (${x.javaClass.simpleName})", TunnelException.Kind.NETWORK) }
            session.addPublicKeyIdentity(kp)
            try { session.auth().verify(CONNECT_TIMEOUT_S, TimeUnit.SECONDS) }
            catch (x: IOException) {
                runCatching { session.close(true) }
                if (hostKeyProblem != null) throw TunnelException("clé de l'hôte refusée : $hostKeyProblem", TunnelException.Kind.HOSTKEY)
                val auth = x.javaClass.simpleName.contains("SshException") && (x.message ?: "").contains("authentication", ignoreCase = true)
                throw TunnelException(if (auth) "authentification refusée par le serveur" else "connexion interrompue (${x.javaClass.simpleName})", if (auth) TunnelException.Kind.AUTH else TunnelException.Kind.NETWORK)
            }
            val bound = try {
                session.startRemotePortForwarding(SshdSocketAddress("127.0.0.1", e.port), SshdSocketAddress("127.0.0.1", localPort))
            } catch (x: IOException) { runCatching { session.close(true) }; throw TunnelException("redirection du port ${e.port} refusée par le serveur", TunnelException.Kind.FORWARD) }
            if (bound.port != e.port) { runCatching { session.close(true) }; throw TunnelException("le serveur a ouvert le port ${bound.port} au lieu de ${e.port}", TunnelException.Kind.FORWARD) }
            val handle = Handle(c, session, relay)
            session.addSessionListener(object : SessionListener { override fun sessionClosed(s: Session) { if (!handle.closing) onClosed() } })
            if (!session.isOpen) { handle.close(); throw TunnelException("session fermée aussitôt", TunnelException.Kind.NETWORK) }
            log("tunnel: up, remote port ${e.port}")
            return handle
        } catch (x: TunnelException) { runCatching { client?.stop() }; relay?.close(); throw x }
        catch (x: Exception) { runCatching { client?.stop() }; relay?.close(); throw TunnelException("erreur du client SSH (${x.javaClass.simpleName})", TunnelException.Kind.NETWORK) }
    }

    private class Handle(private val client: SshClient, private val session: ClientSession, private val relay: LoopRelay?) : TunnelSession {
        @Volatile var closing = false
        override fun alive() = !closing && session.isOpen && client.isStarted
        override fun close() { closing = true; runCatching { session.close(true) }; runCatching { client.stop() }; relay?.close() }
    }

    companion object {
        const val KEEPALIVE_S = 30L
        const val CONNECT_TIMEOUT_S = 45L
        private val SEED = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * One-connection loopback relay: the SSH client connects to 127.0.0.1:[port]; the relay opens the real connection with [dial] (through the phone gateway's SOCKS5 proxy) and copies both ways
 * until either side ends. Two short-lived threads, only while the tunnel goes through the phone.
 */
internal class LoopRelay(private val dial: () -> Socket) {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port get() = server.localPort
    @Volatile private var closed = false
    private val socks = ArrayList<Socket>()

    init {
        Thread({
            try {
                val local = server.accept(); synchronized(socks) { socks += local }
                server.close()
                val remote = dial(); synchronized(socks) { socks += remote }
                pump(local, remote, "relay-up"); pump(remote, local, "relay-down")
            } catch (_: Exception) { close() }
        }, "tunnel-relay").apply { isDaemon = true; start() }
    }

    private fun pump(from: Socket, to: Socket, name: String) {
        Thread({
            val buf = ByteArray(16 * 1024)
            try { val i = from.getInputStream(); val o = to.getOutputStream(); while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); o.flush() } } catch (_: Exception) {}
            close()
        }, name).apply { isDaemon = true; start() }
    }

    fun close() {
        if (closed) return; closed = true
        runCatching { server.close() }
        synchronized(socks) { socks.forEach { runCatching { it.close() } } }
    }
}
