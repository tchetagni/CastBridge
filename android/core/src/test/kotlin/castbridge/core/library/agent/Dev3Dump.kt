package castbridge.core.library.agent

import java.io.File
import kotlin.test.Test

/** Writes what the engine answers on every hand-written DEV-3 case (`build/reports/dev3-hand-verdicts.tsv`): the source of the « reconnu avant / après » columns of docs/NAMING-PATTERNS.md. */
class Dev3Dump {
    @Test fun dump() {
        val out = File("build/reports/dev3-hand-verdicts.tsv")
        out.parentFile.mkdirs()
        out.writeText(Dev3Hand.CASES.joinToString("\n", postfix = "\n") { g ->
            val o = CorpusEval.run(g.c)
            listOf(g.cat, g.c.input, g.c.folderIn, Dev3Eval.judge(g, o).name, o.kind.name, o.name, o.folder).joinToString("\t")
        })
    }
}
