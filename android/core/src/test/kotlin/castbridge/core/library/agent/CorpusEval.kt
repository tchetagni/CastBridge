package castbridge.core.library.agent

import java.security.MessageDigest

/** Runs the real parser + namer on a case, the way the agent does. */
object CorpusEval {
    data class Out(val kind: Kind, val name: String, val folder: String)
    data class Result(val g: GCase, val out: Out) { val ok get() = out.kind == g.c.kind && out.name == g.c.name && out.folder == g.c.folder }
    data class Report(val results: List<Result>) {
        val total get() = results.size
        val passed get() = results.count { it.ok }
        val rate get() = if (total == 0) 1.0 else passed.toDouble() / total
        val failures get() = results.filter { !it.ok }
        fun byCategory() = results.groupBy { it.g.cat }.toSortedMap().map { (k, v) -> Triple(k, v.count { it.ok }, v.size) }
        fun summary(title: String) = buildString {
            append("CORPUS $title : $passed/$total = ${"%.1f".format(rate * 100)} %\n")
            for ((k, ok, n) in byCategory()) append("   %-14s %4d / %-4d = %5.1f %%\n".format(k, ok, n, 100.0 * ok / n))
        }
    }

    fun run(c: Case): Out {
        val p = NameParser.parse(c.input, c.folderIn, c.dur * 60_000L, 2026)
        val f = FileRef(Origin.PHONE, c.input, 1L shl 20, durationMs = c.dur * 60_000L, folder = c.folderIn)
        val prop = Namer.propose(p, f, Labels(c.lang))
        return Out(p.kind, prop.name, prop.folder)
    }

    fun evaluate(cases: List<GCase>) = Report(cases.map { Result(it, run(it.c)) })

    fun describe(r: Result) = "${r.g.cat} | ${r.g.c.input}  [dossier: ${r.g.c.folderIn}]\n     attendu : ${r.g.c.kind} | ${r.g.c.name} | ${r.g.c.folder}\n     obtenu  : ${r.out.kind} | ${r.out.name} | ${r.out.folder}"

    // ---- TSV (the frozen set is a file whose hash is checked)
    fun toTsv(cases: List<GCase>) = cases.joinToString("\n", postfix = "\n") { g ->
        val c = g.c
        listOf(g.cat, c.input, c.name, c.folder, c.kind.name, c.dur.toString(), c.folderIn, c.lang).joinToString("\t")
    }

    fun fromTsv(text: String): List<GCase> = text.lines().filter { it.isNotBlank() }.map { l ->
        val f = l.split('\t')
        require(f.size == 8) { "bad line: $l" }
        GCase(f[0], Case(f[1], f[2], f[3], Kind.valueOf(f[4]), f[5].toInt(), f[6], f[7]))
    }

    fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
