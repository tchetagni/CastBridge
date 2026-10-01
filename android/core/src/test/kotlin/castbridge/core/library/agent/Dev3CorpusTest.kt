package castbridge.core.library.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * DEV-3: what the rules of docs/NAMING-PATTERNS.md are tuned against (hand-written [Dev3Hand] + generated [Dev3Gen], DEV pools).
 * The failures go to `build/reports/naming-dev3-failures.txt`. The honest number is the one of GELÉ-3 ([Frozen3CorpusTest]).
 */
class Dev3CorpusTest {
    private val frozenInputs: Set<String> by lazy {
        listOf("frozen.tsv", "frozen-hard.tsv", "frozen-3.tsv").map { File("src/test/resources/naming/$it") }.filter { it.exists() }
            .flatMap { CorpusEval.fromTsv(it.readText()) }.map { key(it.c) }.toSet()
    }
    private fun key(c: Case) = c.input.lowercase() + "|" + c.folderIn.lowercase()

    private fun all(): List<GCase> = (Dev3Hand.CASES + Dev3Gen(31L, CorpusPools.DEV).generate(1500)).filter { key(it.c) !in frozenInputs }

    @Test fun dev3() {
        val cases = all()
        val report = Dev3Eval.evaluate(cases)
        println(report.summary("DEV-3"))
        val out = File("build/reports/naming-dev3-failures.txt")
        out.parentFile.mkdirs()
        out.writeText(report.failures.joinToString("\n") { Dev3Eval.describe(it) })
        assertTrue(cases.size >= 1500, "dev-3 corpus too small: ${cases.size}")
        assertTrue(report.rate >= FLOOR, "dev-3 rate ${"%.3f".format(report.rate)} below $FLOOR (see build/reports/naming-dev3-failures.txt)")
    }

    @Test fun handWrittenDev3CasesAreNotFrozenCases() {
        val clash = Dev3Hand.CASES.filter { key(it.c) in frozenInputs }.map { it.c.input }
        assertTrue(clash.isEmpty(), "dev-3 cases that are also frozen cases:\n" + clash.joinToString("\n"))
    }

    companion object { const val FLOOR = 0.0 }
}
