package castbridge.sshd

import castbridge.core.ssh.AuthorizedKeys
import castbridge.core.ssh.FailureTracker
import castbridge.core.ssh.Lan
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
        s.keyPairProvider = hostKeyProvider() as KeyPairProvider
        s.userAuthFactories = listOf<UserAuthFactory>(UserAuthPublicKeyFactory.INSTANCE)   // no passwords, ever
        s.publickeyAuthenticator = org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator { user, key, session ->
            val ip = (session.remoteAddress as? InetSocketAddress)?.address?.hostAddress.orEmpty()
            val presented = PublicKeyEntry.toString(key).substringAfter(' ').trim()
            val ok = !failures.isLocked(ip) && keys().any { it.base64 == presented }
            if (ok) { failures.recordSuccess(ip); log("ssh: login ${user}@$ip") } else { failures.recordFailure(ip); log("ssh: refused $ip") }
            ok
        }
        s.forwardingFilter = RejectAllForwardingFilter.INSTANCE
        // No pty on Android: PtyShell emulates echo/line editing when the client asks for a terminal.
        s.shellFactory = ShellFactory { PtyShell(listOf(shell, "-i"), sftpRoot) }
        s.commandFactory = ScpCommandFactory.Builder().withDelegate(CommandFactory { ch, cmd ->
            ProcessShellFactory(cmd, shell, "-c", cmd).createShell(ch)
        }).build()
        s.subsystemFactories = listOf(SftpSubsystemFactory.Builder().build())
        s.fileSystemFactory = VirtualFileSystemFactory(sftpRoot.also { it.mkdirs() }.toPath())
        CoreModuleProperties.AUTH_TIMEOUT.set(s, Duration.ofSeconds(30))
        CoreModuleProperties.IDLE_TIMEOUT.set(s, Duration.ofMinutes(15))
        CoreModuleProperties.MAX_AUTH_REQUESTS.set(s, 3)
        s.addSessionListener(object : SessionListener {
            override fun sessionCreated(session: Session) {
                val ip = (session.remoteAddress as? InetSocketAddress)?.address?.hostAddress.orEmpty()
                if ((requireLan && !Lan.isLocal(ip)) || failures.isLocked(ip)) {
                    log("ssh: connection from $ip dropped"); session.close(true)
                }
            }
            override fun sessionEvent(session: Session, event: SessionListener.Event) {
                if (event == SessionListener.Event.Authenticated) policy.onSessionOpened()
            }
            override fun sessionClosed(session: Session) { if (session.isAuthenticated) policy.onSessionClosed() }
        })
        s.start()
        sshd = s
        stopping.set(false)
        watchdog = Thread({
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
        init { SecurityUtils.isEDDSACurveSupported() }   // touch security registration early
    }
}
