package castbridge.core.library.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Same contract as [FrozenCorpusTest], for the FROZEN-HARD set (`naming/frozen-hard.tsv`): unusual shapes, written before any rule change, never tuned against. */
class FrozenHardCorpusTest {
    private val tsv = File("src/test/resources/naming/frozen-hard.tsv")
    private val shaFile = File("src/test/resources/naming/frozen-hard.sha256")

    @Test fun write() {
        if (System.getenv("CORPUS_WRITE") != "1") return
        check(!tsv.exists()) { "the frozen set already exists: it is frozen" }
        val all = FrozenHardHand.CASES + CorpusGenHard(20261002L, CorpusPools.FROZEN).generate(700)
        val text = CorpusEval.toTsv(all)
        tsv.writeText(text)
        shaFile.writeText(CorpusEval.sha256(text.toByteArray()) + "\n")
        println("FROZEN-HARD written: ${all.size} cases")
    }

    @Test fun frozenHardSetIsUntouchedAndItsRateIsReported() {
        if (!tsv.exists()) return
        val bytes = tsv.readBytes()
        assertEquals(shaFile.readText().trim(), CorpusEval.sha256(bytes), "naming/frozen-hard.tsv was modified: it must stay frozen")
        val report = CorpusEval.evaluate(CorpusEval.fromTsv(String(bytes)))
        println(report.summary("FROZEN-HARD"))
        assertTrue(report.total >= 700, "frozen-hard set too small: ${report.total}")
        assertTrue(report.rate >= FLOOR, "frozen-hard rate ${report.rate} below the recorded floor $FLOOR")
    }

    companion object { const val FLOOR = 0.98 }
}
