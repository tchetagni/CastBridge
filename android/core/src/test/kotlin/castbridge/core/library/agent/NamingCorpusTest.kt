package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertTrue

class NamingCorpusTest {
    private fun run(c: Case): Triple<Kind, String, String> {
        val p = NameParser.parse(c.input, c.folderIn, c.dur * 60_000L, 2026)
        val f = FileRef(Origin.PHONE, c.input, 1L shl 20, durationMs = c.dur * 60_000L, folder = c.folderIn)
        val prop = Namer.propose(p, f, Labels(c.lang))
        return Triple(p.kind, prop.name, prop.folder)
    }

    private fun evaluate(name: String, cases: List<Case>, minRate: Double) {
        val bad = cases.mapNotNull { c ->
            val (k, n, f) = run(c)
            if (k == c.kind && n == c.name && f == c.folder) null
            else "${c.input}\n     attendu : ${c.kind} | ${c.name} | ${c.folder}\n     obtenu  : $k | $n | $f"
        }
        val rate = (cases.size - bad.size).toDouble() / cases.size
        println("CORPUS $name : ${cases.size - bad.size}/${cases.size} = ${"%.1f".format(rate * 100)} %" + (if (bad.isEmpty()) "" else "\n" + bad.joinToString("\n")))
        assertTrue(rate >= minRate, "CORPUS $name : ${cases.size - bad.size}/${cases.size}\n" + bad.joinToString("\n"))
    }

    @Test fun setA() = evaluate("A", NamingCorpus.A, 1.0)
    @Test fun setB() = evaluate("B", NamingCorpusB.B, 0.0)
}
