package castbridge.core.ssh

import java.io.ByteArrayOutputStream

/**
 * Minimal terminal line discipline, used because an Android app cannot allocate a real pseudo-terminal
 * (no ioctl from Java, no native code shipped): when an SSH client asks for a PTY it puts its terminal
 * in raw mode and expects the remote side to echo and edit. This emulates just that:
 *
 *  - client -> shell: echo typed characters, line editing (Backspace, Ctrl-U, Ctrl-W), Enter sends the
 *    line, Ctrl-D on an empty line = end of input, Ctrl-C discards the line, escape sequences (arrows,
 *    function keys) are swallowed.
 *  - shell -> client: LF becomes CR LF.
 *
 * It is NOT a tty: no job control, no full-screen programs (vi, top), no history. Commands run with
 * `ssh host 'cmd'` (exec, no PTY) are unaffected and are the recommended way for scripts and agents.
 */
class LineDiscipline(
    private val toClient: (ByteArray) -> Unit,
    private val toShell: (ByteArray) -> Unit,
    private val onEof: () -> Unit = {},
    private val onInterrupt: () -> Unit = {},
) {
    private val line = ByteArrayOutputStream()
    private val charLens = ArrayList<Int>()          // byte length of each typed character (for Backspace)
    private var esc = 0                              // 0 normal, 1 after ESC, 2 in CSI, 3 after SS3
    private var lastWasCr = false
    private val partial = ByteArrayOutputStream()    // incomplete UTF-8 sequence
    private var partialNeed = 0

    @Synchronized fun fromClient(b: ByteArray, off: Int = 0, len: Int = b.size - off) {
        for (i in off until off + len) byte(b[i].toInt() and 0xff)
    }

    private fun echo(s: String) = toClient(s.toByteArray(Charsets.UTF_8))

    private fun byte(c: Int) {
        // escape sequences
        when (esc) {
            1 -> { esc = when (c) { '['.code -> 2; 'O'.code -> 3; else -> 0 }; return }
            2 -> { if (c in 0x40..0x7e) esc = 0; return }
            3 -> { esc = 0; return }
        }
        if (partialNeed > 0) {                        // continuation bytes of a multi-byte character
            if (c and 0xc0 == 0x80) {
                partial.write(c)
                if (--partialNeed == 0) commitChar(partial.toByteArray().also { partial.reset() })
                return
            }
            partial.reset(); partialNeed = 0          // malformed: drop and reprocess this byte
        }
        val wasCr = lastWasCr
        lastWasCr = c == 0x0d
        when {
            c == 0x1b -> esc = 1
            c == 0x0d -> enter()
            c == 0x0a -> if (!wasCr) enter()
            c == 0x7f || c == 0x08 -> erase(1)
            c == 0x15 -> erase(charLens.size)                                   // Ctrl-U
            c == 0x17 -> {                                                      // Ctrl-W
                val s = String(line.toByteArray(), Charsets.UTF_8)
                var i = s.length
                while (i > 0 && s[i - 1] == ' ') i--
                while (i > 0 && s[i - 1] != ' ') i--
                erase(s.codePointCount(i, s.length))
            }
            c == 0x03 -> { line.reset(); charLens.clear(); echo("^C\r\n"); onInterrupt() }
            c == 0x04 -> if (charLens.isEmpty()) onEof() else flushWithoutNewline()
            c < 0x20 && c != 0x09 -> {}                                         // other control characters: ignored
            c < 0x80 -> commitChar(byteArrayOf(c.toByte()))
            c and 0xe0 == 0xc0 -> { partial.write(c); partialNeed = 1 }
            c and 0xf0 == 0xe0 -> { partial.write(c); partialNeed = 2 }
            c and 0xf8 == 0xf0 -> { partial.write(c); partialNeed = 3 }
        }
    }

    private fun commitChar(bytes: ByteArray) {
        line.write(bytes); charLens += bytes.size
        toClient(bytes)
    }

    private fun enter() {
        val out = line.toByteArray() + byteArrayOf('\n'.code.toByte())
        line.reset(); charLens.clear()
        echo("\r\n")
        toShell(out)
    }

    private fun flushWithoutNewline() {
        val out = line.toByteArray(); line.reset(); charLens.clear(); toShell(out)
    }

    private fun erase(n: Int) {
        repeat(minOf(n, charLens.size)) {
            val l = charLens.removeAt(charLens.size - 1)
            val all = line.toByteArray(); line.reset(); line.write(all, 0, all.size - l)
            echo("\b \b")
        }
    }

    /** Converts shell output for a raw-mode terminal: LF -> CR LF (a lone CR is left alone). */
    fun fromShell(b: ByteArray, off: Int = 0, len: Int = b.size - off): ByteArray {
        val out = ByteArrayOutputStream(len + 16)
        var prev = if (outLastCr) 0x0d else 0
        for (i in off until off + len) {
            val c = b[i].toInt() and 0xff
            if (c == 0x0a && prev != 0x0d) out.write(0x0d)
            out.write(c); prev = c
        }
        outLastCr = prev == 0x0d
        return out.toByteArray()
    }

    private var outLastCr = false
}
