package castbridge.core.library.agent

/**
 * Evaluation for the DEV-3 / GELÉ-3 sets: like [CorpusEval] but each failure is classed as an OMISSION (the engine said "unknown" for something it
 * should have understood: the file is left alone, harmless) or a FALSE result (wrong kind, wrong season / episode, junk kept in the title, a movie
 * taken for a series…: the harmful kind). False results are the ones to drive to zero.
 */
object Dev3Eval {
    enum class Verdict { OK, OMISSION, FALSE }
    data class Row(val g: GCase, val out: CorpusEval.Out, val verdict: Verdict)

    class Report(val rows: List<Row>) {
        val total get() = rows.size
        val passed get() = rows.count { it.verdict == Verdict.OK }
        val omissions get() = rows.count { it.verdict == Verdict.OMISSION }
        val falses get() = rows.count { it.verdict == Verdict.FALSE }
        /** The gravest false results: another kind than expected (a movie taken for a series, a series for a movie…), "unknown" excluded. */
        val wrongKind get() = rows.count { it.out.kind != it.g.c.kind && it.out.kind != Kind.UNKNOWN }
        val rate get() = if (total == 0) 1.0 else passed.toDouble() / total
        val failures get() = rows.filter { it.verdict != Verdict.OK }
        fun summary(title: String) = buildString {
            append("CORPUS $title : $passed/$total = ${"%.1f".format(rate * 100)} %  (omissions $omissions, faux $falses = ${"%.1f".format(100.0 * falses / total.coerceAtLeast(1))} %, dont mauvais type $wrongKind)\n")
            for ((cat, v) in rows.groupBy { it.g.cat }.toSortedMap()) {
                val ok = v.count { it.verdict == Verdict.OK }; val om = v.count { it.verdict == Verdict.OMISSION }; val fa = v.count { it.verdict == Verdict.FALSE }; val wk = v.count { it.out.kind != it.g.c.kind && it.out.kind != Kind.UNKNOWN }
                append("   %-14s %4d / %-4d = %5.1f %%   omissions %3d   faux %3d (mauvais type %d)\n".format(cat, ok, v.size, 100.0 * ok / v.size, om, fa, wk))
            }
        }
    }

    fun judge(g: GCase, o: CorpusEval.Out): Verdict {
        val c = g.c
        if (o.kind == c.kind && o.name == c.name && o.folder == c.folder) return Verdict.OK
        // the engine kept the file unrecognised although something was expected: a harmless omission
        if (o.kind == Kind.UNKNOWN && c.kind != Kind.UNKNOWN) return Verdict.OMISSION
        return Verdict.FALSE
    }

    fun evaluate(cases: List<GCase>) = Report(cases.map { g -> val o = CorpusEval.run(g.c); Row(g, o, judge(g, o)) })

    fun describe(r: Row) = "${r.verdict} ${r.g.cat} | ${r.g.c.input}  [dossier: ${r.g.c.folderIn}]\n     attendu : ${r.g.c.kind} | ${r.g.c.name} | ${r.g.c.folder}\n     obtenu  : ${r.out.kind} | ${r.out.name} | ${r.out.folder}"
}
