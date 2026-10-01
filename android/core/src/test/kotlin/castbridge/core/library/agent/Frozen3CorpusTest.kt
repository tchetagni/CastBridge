package castbridge.core.library.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GELÉ-3: [Frozen3Hand] + [Dev3Gen] with the FROZEN pools and another seed, stored in `naming/frozen-3.tsv` (hash checked). Written BEFORE any rule change
 * of the docs/NAMING-PATTERNS.md work and never used to tune a rule: this test prints the aggregate and per-family counts, not the failing cases.
 */
class Frozen3CorpusTest {
    private val tsv = File("src/test/resources/naming/frozen-3.tsv")
    private val shaFile = File("src/test/resources/naming/frozen-3.sha256")

    @Test fun write() {
        if (System.getenv("CORPUS_WRITE") != "1") return
        check(!tsv.exists()) { "the frozen set already exists: it is frozen" }
        val all = (Frozen3Hand.CASES + Dev3Gen(20261003L, Pools3.FROZEN).generate(700)).distinctBy { it.c.input + "|" + it.c.folderIn }
        val text = CorpusEval.toTsv(all)
        tsv.writeText(text)
        shaFile.writeText(CorpusEval.sha256(text.toByteArray()) + "\n")
        println("FROZEN-3 written: ${all.size} cases")
    }

    @Test fun frozen3SetIsUntouchedAndItsRateIsReported() {
        if (!tsv.exists()) return
        val bytes = tsv.readBytes()
        assertEquals(shaFile.readText().trim(), CorpusEval.sha256(bytes), "naming/frozen-3.tsv was modified: it must stay frozen")
        val report = Dev3Eval.evaluate(CorpusEval.fromTsv(String(bytes)))
        println(report.summary("GELÉ-3"))
        assertTrue(report.total >= 700, "frozen-3 set too small: ${report.total}")
        assertTrue(report.rate >= FLOOR, "frozen-3 rate ${report.rate} below the recorded floor $FLOOR")
    }

    companion object {
        /** Regression guard (not a target), raised only with a measured result. */
        const val FLOOR = 0.0
    }
}
