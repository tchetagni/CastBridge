package castbridge.core.dl

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Runs the aria2c process and keeps it running: start, wait until its RPC answers, restart it (with a growing delay)
 * if it dies, give up after too many crashes in a row, and stop it cleanly (saveSession + shutdown, then force, and
 * always reap the process: no zombie). aria2 is also started with --stop-with-process=<app pid>, so it exits on its own
 * if the app dies.
 *
 * A fresh random RPC secret is made at every start; it lives only in memory and in a conf file that is deleted as soon
 * as aria2 is up. It is never logged (log lines are also scrubbed of it, just in case).
 */
class Aria2Supervisor(
    /** The executable (on Android: nativeLibraryDir/libaria2c.so); null or missing = engine not shipped in this build. */
    private val binary: File?,
    private val workDir: File,
    /** Builds the command line for a start (without the binary). */
    private val argsFor: (conf: File, port: Int) -> List<String>,
    private val env: Map<String, String> = emptyMap(),
    private val launcher: (List<String>, Map<String, String>, File) -> Process = ::launch,
    private val onLog: (String) -> Unit = {},
    /** Called on the supervisor thread each time aria2 is (re)started and ready. */
    private val onReady: (Aria2Rpc) -> Unit = {},
    private val preferredPort: Int = 6800,
    private val readyTimeoutMs: Long = 15_000,
    private val backoffMs: (Int) -> Long = { n -> minOf(60_000L, 2_000L shl minOf(n, 5)) },
    private val maxCrashes: Int = 6,
    private val crashWindowMs: Long = 10 * 60_000L,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    enum class State { UNAVAILABLE, STOPPED, STARTING, RUNNING, RESTARTING, FAILED }

    @Volatile var state: State = if (available) State.STOPPED else State.UNAVAILABLE; private set
    @Volatile var message: String = if (available) "" else NOT_SHIPPED; private set
    @Volatile var rpc: Aria2Rpc? = null; private set
    @Volatile var version: String? = null; private set
    @Volatile private var process: Process? = null
    @Volatile private var stopping = false
    private var thread: Thread? = null
    private val crashes = ArrayDeque<Long>()
    private val logTail = ArrayDeque<String>()
    @Volatile private var secret: String = ""

    val available: Boolean get() = binary != null && binary.isFile

    /** Last lines aria2 printed (warnings and errors), secret removed. */
    fun log(): List<String> = synchronized(logTail) { logTail.toList() }

    @Synchronized fun start() {
        if (!available) { state = State.UNAVAILABLE; message = NOT_SHIPPED; return }
        if (thread?.isAlive == true) return
        stopping = false
        crashes.clear()
        thread = Thread(::loop, "cb-aria2").apply { isDaemon = true; start() }
    }

    private fun loop() {
        var attempt = 0
        while (!stopping) {
            state = if (attempt == 0) State.STARTING else State.RESTARTING
            val started = System.nanoTime()
            val ok = try { runOnce() } catch (e: Exception) { message = "Le moteur n'a pas pu démarrer : ${scrub(e.message ?: e.javaClass.simpleName)}"; false }
            if (stopping) break
            rpc = null
            // It ran for a while: that was a crash, not a start-up failure. Count it and try again.
            val now = System.nanoTime() / 1_000_000
            crashes.addLast(now)
            while (crashes.isNotEmpty() && now - crashes.first() > crashWindowMs) crashes.removeFirst()
            if (crashes.size >= maxCrashes) {
                state = State.FAILED
                message = "Le moteur de téléchargement s'est arrêté $maxCrashes fois de suite : relancez l'app. " + log().lastOrNull().orEmpty()
                break
            }
            if (ok && (System.nanoTime() - started) / 1_000_000 > crashWindowMs) attempt = 0
            state = State.RESTARTING
            if (message.isEmpty()) message = "Le moteur s'est arrêté, redémarrage…"
            sleep(backoffMs(attempt++))
        }
        if (state != State.FAILED) state = State.STOPPED
    }

    /** One life of the process. True if it came up. */
    private fun runOnce(): Boolean {
        workDir.mkdirs()
        secret = newSecret()
        val port = freePort(preferredPort)
        val conf = File(workDir, "aria2.conf")
        writePrivate(conf, Aria2Config.conf(secret))
        val cmd = listOf(binary!!.absolutePath) + argsFor(conf, port)
        val p = launcher(cmd, env, workDir)
        process = p
        val reader = Thread({
            runCatching {
                p.inputStream.bufferedReader().useLines { lines -> lines.forEach { l -> if (l.isNotBlank()) addLog(l) } }
            }
        }, "cb-aria2-log").apply { isDaemon = true; start() }
        val client = Aria2Rpc(port, secret)
        val deadline = System.nanoTime() + readyTimeoutMs * 1_000_000
        var up = false
        while (System.nanoTime() < deadline && p.isAlive && !stopping) {
            try { version = client.getVersion(); up = true; break } catch (e: IOException) { sleep(150) }
        }
        conf.delete()                                  // aria2 has read it (or failed): the secret leaves the disk
        if (!up) {
            reap(p)
            reader.join(1000)
            if (!stopping) message = "Le moteur n'a pas démarré. " + (log().lastOrNull() ?: "")
            return false
        }
        rpc = client
        state = State.RUNNING
        message = ""
        runCatching { onReady(client) }.onFailure { addLog("onReady: ${it.message}") }
        p.waitFor()
        reader.join(1000)
        if (!stopping) { addLog("aria2 exited with code ${runCatching { p.exitValue() }.getOrNull()}"); message = "" }
        return true
    }

    /** Clean stop: aria2 writes its session and says goodbye to trackers, then exits. Bounded: ~10 s at worst. */
    fun stop() {
        val t: Thread?
        synchronized(this) { stopping = true; t = thread; thread = null }
        val p = process
        val r = rpc
        if (p != null && p.isAlive) {
            runCatching { r?.saveSession() }
            runCatching { r?.shutdown() }
            if (!p.waitFor(6, TimeUnit.SECONDS)) {
                runCatching { r?.forceShutdown() }
                if (!p.waitFor(2, TimeUnit.SECONDS)) reap(p)
            }
        }
        t?.join(3000)
        rpc = null; process = null
        if (state != State.UNAVAILABLE) state = State.STOPPED
    }

    /** SIGTERM, then SIGKILL, and always wait: a process that is never waited for stays a zombie. */
    private fun reap(p: Process) {
        p.destroy()
        if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroyForcibly(); p.waitFor(3, TimeUnit.SECONDS) }
    }

    private fun addLog(line: String) = synchronized(logTail) {
        logTail.addLast(scrub(line).take(300))
        while (logTail.size > 40) logTail.removeFirst()
        onLog(logTail.last())
    }

    private fun scrub(s: String) = if (secret.isEmpty()) s else s.replace(secret, "***")

    companion object {
        const val NOT_SHIPPED = "Moteur de téléchargement non inclus dans cette version de l'app (voir docs/DOWNLOADS.md)."

        fun newSecret(): String = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

        fun launch(cmd: List<String>, env: Map<String, String>, dir: File): Process =
            ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).also { it.environment().putAll(env) }.start()

        /** [preferred] if it is free on loopback, else any free port. */
        fun freePort(preferred: Int): Int {
            val lo = InetAddress.getByName("127.0.0.1")
            runCatching { ServerSocket(preferred, 1, lo).use { return preferred } }
            return ServerSocket(0, 1, lo).use { it.localPort }
        }

        /** Owner-only file (the RPC secret). */
        fun writePrivate(f: File, text: String) {
            f.delete()
            f.createNewFile()
            f.setReadable(false, false); f.setWritable(false, false)
            f.setReadable(true, true); f.setWritable(true, true)
            f.writeText(text)
        }
    }
}
