package castbridge.core.library.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The DEV corpus: what the rules are tuned against. Generated (thousands of names, DEV pools, fixed seeds) + hand-written ([DevHand]) + the
 * older sets A and B. The failures are written to `build/reports/naming-dev-failures.txt` for whoever tunes the rules.
 * The honest number is the one of the FROZEN sets ([FrozenCorpusTest], [FrozenHardCorpusTest]).
 */
class DevCorpusTest {
    private val frozenInputs: Set<String> by lazy {
        listOf("frozen.tsv", "frozen-hard.tsv").map { File("src/test/resources/naming/$it") }.filter { it.exists() }
            .flatMap { CorpusEval.fromTsv(it.readText()) }.map { key(it.c) }.toSet()
    }
    private fun key(c: Case) = c.input.lowercase() + "|" + c.folderIn.lowercase()

    /** Everything the rules are tuned on, minus any input that is also a frozen input (a name that happens to be generated twice is judged on the frozen side only). */
    private fun all(): List<GCase> =
        (CorpusGen(1L, CorpusPools.DEV).generate(3500) + CorpusGenHard(2L, CorpusPools.DEV).generate(1500) + DevHand.CASES +
            NamingCorpus.A.map { GCase("setA", it) } + NamingCorpusB.B.map { GCase("setB", it) }).filter { key(it.c) !in frozenInputs }

    @Test fun devCorpus() {
        val cases = all()
        val report = CorpusEval.evaluate(cases)
        println(report.summary("DEV"))
        val out = File("build/reports/naming-dev-failures.txt")
        out.parentFile.mkdirs()
        out.writeText(report.failures.joinToString("\n") { CorpusEval.describe(it) })
        assertTrue(cases.size >= 5000, "dev corpus too small: ${cases.size}")
        assertTrue(report.rate >= FLOOR, "dev rate ${"%.3f".format(report.rate)} below $FLOOR (see build/reports/naming-dev-failures.txt)")
    }

    /** Anti-leak guard: the hand-written dev cases must not reuse a frozen input (the generated ones are filtered out by [all]). */
    @Test fun handWrittenDevCasesAreNotFrozenCases() {
        val clash = DevHand.CASES.filter { key(it.c) in frozenInputs }.map { it.c.input }
        assertTrue(clash.isEmpty(), "dev cases that are also frozen cases (tuning on the frozen set is forbidden):\n" + clash.joinToString("\n"))
    }

    /** What the agent produces for its own names must be stable (otherwise every run would rename the library again): checked on every generated case. */
    @Test fun ourNamesAreStable() {
        val bad = all().filter { it.c.name.contains('.') && !it.c.name.startsWith("Mindhunter") }.mapNotNull { g ->
            val c = g.c
            val again = CorpusEval.run(Case(c.name, c.name, c.folder, c.kind, c.dur, c.folder.let { "" }, c.lang))
            if (again.name == c.name) null else "${c.name}  ->  ${again.name}"
        }
        println("STABILITY: ${bad.size} unstable of ${all().size}")
        assertTrue(bad.size <= MAX_UNSTABLE, "names that change when the agent runs again:\n" + bad.take(30).joinToString("\n"))
    }

    companion object {
        const val FLOOR = 0.995
        const val MAX_UNSTABLE = 0
    }
}
