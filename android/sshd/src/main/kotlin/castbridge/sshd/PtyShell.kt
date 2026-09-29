package castbridge.sshd

import castbridge.core.ssh.LineDiscipline
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Interactive shell for clients that ask for a terminal. Android apps cannot allocate a pty, so the shell
 * runs on plain pipes and [LineDiscipline] supplies echo, line editing and CR/LF conversion. Not a tty:
 * no job control, no full-screen programs, Ctrl-C only discards the current line. Without a pty request it
 * is a plain pipe-through.
 */
class PtyShell(private val argv: List<String>, private val workDir: File? = null) : Command {
    private var inp: InputStream? = null
    private var out: OutputStream? = null
    private var err: OutputStream? = null
    private var exit: ExitCallback? = null
    @Volatile private var proc: Process? = null

    override fun setInputStream(i: InputStream) { inp = i }
    override fun setOutputStream(o: OutputStream) { out = o }
    override fun setErrorStream(e: OutputStream) { err = e }
    override fun setExitCallback(c: ExitCallback) { exit = c }

    override fun start(channel: ChannelSession, env: Environment) {
        val pty = env.env.containsKey("TERM")
        val pb = ProcessBuilder(argv)
        workDir?.let { pb.directory(it) }
        pb.environment().putAll(env.env)
        val p = pb.start().also { proc = it }
        val toClient = out!!; val toErr = err!!; val fromClient = inp!!
        val shellIn = p.outputStream

        fun send(o: OutputStream, b: ByteArray) = synchronized(o) { o.write(b); o.flush() }
        val ld = if (pty) LineDiscipline(
            toClient = { runCatching { send(toClient, it) } },
            toShell = { runCatching { shellIn.write(it); shellIn.flush() } },
            onEof = { runCatching { shellIn.close() } },
        ) else null

        pump("in") {
            val buf = ByteArray(4096)
            while (true) {
                val n = fromClient.read(buf); if (n < 0) break
                if (ld != null) ld.fromClient(buf, 0, n) else { shellIn.write(buf, 0, n); shellIn.flush() }
            }
            runCatching { shellIn.close() }
        }
        fun output(from: InputStream, to: OutputStream) = pump("out") {
            val buf = ByteArray(4096)
            while (true) {
                val n = from.read(buf); if (n < 0) break
                send(to, if (ld != null) ld.fromShell(buf, 0, n) else buf.copyOf(n))
            }
        }
        val o1 = output(p.inputStream, toClient)
        val o2 = output(p.errorStream, if (pty) toClient else toErr)     // one stream on a terminal
        pump("wait") {
            val code = p.waitFor(); o1.join(2000); o2.join(2000)
            exit?.onExit(code)
        }
    }

    private fun pump(name: String, body: () -> Unit): Thread =
        Thread({ runCatching(body) }, "pty-shell-$name").apply { isDaemon = true; start() }

    override fun destroy(channel: ChannelSession) { proc?.destroyForcibly() }
}
