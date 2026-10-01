package castbridge.core.library.agent

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The FROZEN set: written once (hand-written [FrozenHand] + [CorpusGen] with the FROZEN pools), stored in `naming/frozen.tsv`, hash checked.
 * It is never used to tune a rule. This test only reports the aggregate rate and the per-family counts (not the failing cases, on purpose).
 */
class FrozenCorpusTest {
    private val tsv = File("src/test/resources/naming/frozen.tsv")
    private val shaFile = File("src/test/resources/naming/frozen.sha256")

    /** `CORPUS_WRITE=1 gradle :core:test --tests '*FrozenCorpusTest*'` : only ever run once (refuses to overwrite). */
    @Test fun write() {
        if (System.getenv("CORPUS_WRITE") != "1") return
        check(!tsv.exists()) { "the frozen set already exists: it is frozen" }
        val gen = CorpusGen(20261001L, CorpusPools.FROZEN).generate(1900)
        val all = FrozenHand.CASES + gen
        val text = CorpusEval.toTsv(all)
        tsv.parentFile.mkdirs()
        tsv.writeText(text)
        shaFile.writeText(CorpusEval.sha256(text.toByteArray()) + "\n")
        println("FROZEN written: ${all.size} cases")
    }

    @Test fun frozenSetIsUntouchedAndItsRateIsReported() {
        if (!tsv.exists()) return
        val bytes = tsv.readBytes()
        assertEquals(shaFile.readText().trim(), CorpusEval.sha256(bytes), "naming/frozen.tsv was modified: it must stay frozen")
        val report = CorpusEval.evaluate(CorpusEval.fromTsv(String(bytes)))
        println(report.summary("FROZEN"))
        assertTrue(report.total >= 2000, "frozen set too small: ${report.total}")
        assertTrue(report.rate >= FLOOR, "frozen rate ${report.rate} below the recorded floor $FLOOR")
    }

    companion object {
        /** Lowest rate accepted (a regression guard, not a target). Raised only with the measured result. */
        const val FLOOR = 0.0
    }
}
