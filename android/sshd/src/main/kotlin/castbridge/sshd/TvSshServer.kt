package castbridge.sshd

import castbridge.core.ssh.AuthorizedKeys
import castbridge.core.ssh.FailureTracker
import castbridge.core.ssh.Lan
import castbridge.core.ssh.PeerRegistry
import castbridge.core.ssh.SshKey
import castbridge.core.ssh.SshPolicy
import org.apache.sshd.common.NamedFactory
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.kex.BuiltinDHFactories
import org.apache.sshd.server.ServerBuilder
import org.apache.sshd.server.shell.ShellFactory
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.keyprovider.KeyPairProvider
import org.apache.sshd.common.session.Session
import org.apache.sshd.common.session.SessionListener
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.core.CoreModuleProperties
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.auth.UserAuthFactory
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.forward.RejectAllForwardingFilter
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.scp.server.ScpCommandFactory
import org.apache.sshd.server.shell.ProcessShellFactory
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import java.io.File
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SSH access to the TV app, for administration by a person or by an agent (ssh host 'command').
 *
 * Safety model (see docs/ADMIN.md): off unless [start] is called on an explicit action; public keys
 * only (authorized_keys in [dataDir], managed through [addKey]/[removeKey]); LAN clients only; failed
 * logins lock the client's address; no port/agent/X11 forwarding; SFTP/SCP confined to [sftpRoot];
 * stops by itself after the policy's idle timeout. The shell runs with the app's own uid, no root.
 */
class TvSshServer(
    private val dataDir: File,
    private val sftpRoot: File,
    val policy: SshPolicy = SshPolicy(),
    val port: Int = DEFAULT_PORT,
    private val shell: String = "/system/bin/sh",
    private val failures: FailureTracker = FailureTracker(),
    private val log: (String) -> Unit = {},
    private val requireLan: Boolean = true,
    /** Identity of tunnelled (Bluetooth) clients, which all arrive from 127.0.0.1: failures are counted per device. */
    val peers: PeerRegistry = PeerRegistry(),
    /** In-process commands (`ssh host 'cbdev …'`): given the command line, writes its output and returns the exit code, or null when the line is not one of them (the shell runs it). */
    private val builtin: ((String, java.io.OutputStream) -> Int?)? = null,
    /**
     * Listen on this address only (null = every interface). The editor's tunnel instance (docs/REMOTE-TUNNEL-TV.md) binds 127.0.0.1: the only way in is the reverse tunnel.
     */
    private val bindHost: String? = null,
    /**
     * Replaces the authorized_keys file: given the base64 blob of the key a client presents, returns the identifier of the matching key (an expert) or null (refused). Keys only, never a password.
     * Used by the tunnel instance, whose keys are the owner-signed experts list; it has no key file and [addKey] is not used with it.
     */
    private val keyAuthority: ((String) -> String?)? = null,
    /** Told the identifier returned by [keyAuthority] at each successful login (the tunnel's local journal). */
    private val onLogin: ((String) -> Unit)? = null,
    /** False = no idle stop (the tunnel instance lives exactly as long as the tunnel does). */
    private val idleStop: Boolean = true,
) {
    private var sshd: SshServer? = null
    private val stopping = AtomicBoolean(false)
    @Volatile private var watchdog: Thread? = null

    private val keysFile get() = File(dataDir, "authorized_keys")
    private val hostKeyFile get() = File(dataDir, "ssh_host_key")

    init {
        dataDir.mkdirs()
        // MINA resolves "~" from this property, which Android leaves unset.
        if (System.getProperty("user.home").isNullOrBlank()) System.setProperty("user.home", dataDir.absolutePath)
    }

    val running: Boolean @Synchronized get() = sshd?.isStarted == true

    @Synchronized fun keys(): List<SshKey> = if (keysFile.isFile) AuthorizedKeys.parse(keysFile.readText()) else emptyList()

    /** Adds a key given as one authorized_keys line. Throws [AuthorizedKeys.Invalid]. */
    @Synchronized fun addKey(line: String): SshKey {
        val k = AuthorizedKeys.parseLine(line)
        writeKeys(AuthorizedKeys.add(keys(), k)); return k
    }

    @Synchronized fun removeKey(fingerprint: String): Boolean {
        val before = keys(); val after = AuthorizedKeys.remove(before, fingerprint)
        if (after.size != before.size) writeKeys(after)
        return after.size != before.size
    }

    private fun writeKeys(keys: List<SshKey>) {
        val tmp = File(dataDir, "authorized_keys.tmp")
        tmp.writeText(AuthorizedKeys.render(keys))
        if (!tmp.renameTo(keysFile)) { keysFile.delete(); if (!tmp.renameTo(keysFile)) throw java.io.IOException("cannot save keys") }
    }

    /** SHA256 fingerprint of the host key, to be shown on the TV so the first connection can be verified. */
    @Synchronized fun hostKeyFingerprint(): String? = runCatching {
        val kp = hostKeyProvider().loadKeys(null).firstOrNull() ?: return null
        KeyUtils.getFingerPrint(kp.public)
    }.getOrNull()

    private fun hostKeyProvider() = SimpleGeneratorHostKeyProvider(hostKeyFile.toPath()).apply {
        algorithm = "EC"; keySize = 256
    }

    @Synchronized fun start(idleMinutes: Int? = null) {
        if (running) { policy.enable(idleMinutes); return }
        policy.enable(idleMinutes)
        // Only key exchanges every Android JCE offers: curve25519 is missing there and MINA's own probing of it
        // touches JMX classes that Android does not have (so the default builder cannot even be created).
        val s = ServerBuilder.builder().keyExchangeFactories(NamedFactory.setUpTransformedFactories(true,
            listOf(BuiltinDHFactories.ecdhp256, BuiltinDHFactories.ecdhp384, BuiltinDHFactories.ecdhp521,
                BuiltinDHFactories.dhgex256, BuiltinDHFactories.dhg14_256), ServerBuilder.DH2KEX)).build()
        s.port = port
        if (bindHost != null) s.host = bindHost
        s.keyPairProvider = hostKeyProvider() as KeyPairProvider
        s.userAuthFactories = listOf<UserAuthFactory>(UserAuthPublicKeyFactory.INSTANCE)   // no passwords, ever
        s.publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { user, key, session ->
            val who = peers.clientKey(session.remoteAddress as? InetSocketAddress)
            val presented = PublicKeyEntry.toString(key).substringAfter(' ').trim()
            val id = if (failures.isLocked(who)) null else if (keyAuthority != null) keyAuthority.invoke(presented) else keys().firstOrNull { it.base64 == presented }?.base64
            val ok = id != null
            if (ok) { failures.recordSuccess(who); log("ssh: login ${user}@$who"); if (keyAuthority != null) session.setAttribute(LOGIN_ID, id!!) } else { failures.recordFailure(who); log("ssh: refused $who") }
            ok
        }
        s.forwardingFilter = RejectAllForwardingFilter.INSTANCE
        // No pty on Android: PtyShell emulates echo/line editing when the client asks for a terminal.
        s.shellFactory = ShellFactory { PtyShell(listOf(shell, "-i"), sftpRoot) }
        s.commandFactory = ScpCommandFactory.Builder().withDelegate(CommandFactory { ch, cmd ->
            if (builtin != null && (cmd == "cbdev" || cmd.startsWith("cbdev "))) BuiltinCommand(cmd, builtin)
            else ProcessShellFactory(cmd, shell, "-c", cmd).createShell(ch)
        }).build()
        s.subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
        s.fileSystemFactory = VirtualFileSystemFactory(sftpRoot.also { it.mkdirs() }.toPath())
        CoreModuleProperties.AUTH_TIMEOUT.set(s, Duration.ofSeconds(30))
        CoreModuleProperties.IDLE_TIMEOUT.set(s, Duration.ofMinutes(15))
        CoreModuleProperties.MAX_AUTH_REQUESTS.set(s, 3)
        s.addSessionListener(object : SessionListener {
            override fun sessionCreated(session: Session) {
                val ip = (session.remoteAddress as? InetSocketAddress)?.address?.hostAddress.orEmpty()
                val who = peers.clientKey(session.remoteAddress as? InetSocketAddress)
                if ((requireLan && !Lan.isLocal(ip)) || failures.isLocked(who)) {
                    log("ssh: connection from $who dropped"); session.close(true)
                }
            }
            override fun sessionEvent(session: Session, event: SessionListener.Event) {
                if (event == SessionListener.Event.Authenticated) { policy.onSessionOpened(); session.getAttribute(LOGIN_ID)?.let { id -> runCatching { onLogin?.invoke(id) } } }
            }
            override fun sessionClosed(session: Session) { if (session.isAuthenticated) policy.onSessionClosed() }
        })
        s.start()
        sshd = s
        stopping.set(false)
        if (idleStop) watchdog = Thread({
            while (!stopping.get()) {
                try { Thread.sleep(5_000) } catch (e: InterruptedException) { return@Thread }
                if (policy.shouldStop()) { log("ssh: idle timeout, stopping"); stop(); return@Thread }
            }
        }, "ssh-watchdog").apply { isDaemon = true; start() }
        log("ssh: listening on $port")
    }

    @Synchronized fun stop() {
        stopping.set(true)
        watchdog?.interrupt(); watchdog = null
        policy.disable()
        sshd?.let { runCatching { it.stop(true) } }
        sshd = null
    }

    companion object {
        const val DEFAULT_PORT = 2222
        private val LOGIN_ID = org.apache.sshd.common.AttributeRepository.AttributeKey<String>()
        init { SecurityUtils.isEDDSACurveSupported() }   // touch security registration early
    }
}

/** One in-process command of [TvSshServer.builtin]: runs on its own thread, answers on the channel, reports its exit code. */
private class BuiltinCommand(private val line: String, private val handler: (String, java.io.OutputStream) -> Int?) : org.apache.sshd.server.command.Command {
    private var out: java.io.OutputStream? = null
    private var err: java.io.OutputStream? = null
    private var exit: org.apache.sshd.server.ExitCallback? = null
    override fun setInputStream(`in`: java.io.InputStream?) {}
    override fun setOutputStream(out: java.io.OutputStream?) { this.out = out }
    override fun setErrorStream(err: java.io.OutputStream?) { this.err = err }
    override fun setExitCallback(callback: org.apache.sshd.server.ExitCallback?) { exit = callback }
    override fun start(channel: org.apache.sshd.server.channel.ChannelSession?, env: org.apache.sshd.server.Environment?) {
        Thread({
            val o = out ?: return@Thread
            val code = try { handler(line, o) ?: 127 } catch (e: Throwable) { runCatching { o.write("erreur : ${e.message}\n".toByteArray()) }; 1 }
            runCatching { o.flush() }
            exit?.onExit(code)
        }, "ssh-builtin").apply { isDaemon = true; start() }
    }
    override fun destroy(channel: org.apache.sshd.server.channel.ChannelSession?) {}
}
