package castbridge.core.library.agent

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * ReDoS guard (docs/NAMING-PATTERNS.md § sécurité): every expression of the engine is exercised through [NameParser.parse] / [Namer] on pathological names
 * (255 repetitive characters, nested brackets, huge numbers of near-matches, combining marks, 6 000 random mixes of the engine's own tokens), each with a hard
 * time limit. A regex with catastrophic backtracking turns one of these into minutes: the test fails after [LIMIT_MS] instead of hanging the build.
 */
class PathologicalNamesTest {
    private val pool = Executors.newCachedThreadPool { r -> Thread(r).also { it.isDaemon = true } }

    private fun runWithin(ms: Long, label: String, block: () -> Unit) {
        val f = pool.submit(Callable { block() })
        try { f.get(ms, TimeUnit.MILLISECONDS) } catch (e: TimeoutException) { f.cancel(true); fail("too slow (> $ms ms, likely catastrophic backtracking): $label") }
    }

    private fun process(name: String, folder: String = "") {
        val p = NameParser.parse(name, folder, 0, 2026)
        Namer.propose(p, FileRef(Origin.PHONE, name, 1L shl 20, folder = folder), Labels("fr"))
        SeriesClassifier.folderFor(name, folder)
        NameParser.parse(name, folder, 3 * 60_000L, 2026)
    }

    private val EXTS = listOf(".mkv", ".mp3", ".srt", ".pdf", ".jpg", ".zip", "")

    private fun fit(s: String, n: Int = 255) = if (s.length >= n) s.take(n) else s.repeat(n / s.length + 1).take(n)

    private val UNITS = listOf(
        "a", " ", ".", "_", "-", "[", "(", "{", "]", ")", "[a", "(a", "a ", "a.", "1.", "1 ", " - 1", " - ", "S01E01", "S1E", "1x01-", "x", "E", "e1", "Ep", "Episode ", "Saison ", "Season 1 ",
        "第", "季", "集", "화", "시즌", "الموسم ", "الحلقة ", "Сезон ", "Серия ", "Bölüm ", "Sezon ", "2024", "2024 03 ", "20240315", "15-03-2024", "03-04-", "1080p", "x264", "WEB-DL", "HDTV",
        "VOSTFR ", "MULTi ", "TRUEFRENCH", "[VOSTFR]", "(Official Video)", "| ", "ft. ", "feat. ", "t.me/", "@", "@a", "www.", ".com", "[www.a.com]", "NetNaija", "-RARBG", "Part ", "Pt.", "CD1 ",
        "Director's Cut ", "IMAX ", "3D ", "OVA ", "SP01", "#", "%41", "%", "́", "é", "é", "'", "’", "&", "+", ",", "/", "\\", "~", "0", "00", "999", "1", "I", "IV", "XX",
    )

    @Test fun repetitiveAndNestedNamesStayFast() {
        val inputs = ArrayList<String>()
        for (u in UNITS) for (e in EXTS) inputs += fit(u, 251) + e
        inputs += listOf("[".repeat(120) + "]".repeat(120), "(".repeat(250), "[(".repeat(100) + ")]".repeat(100), "{[(".repeat(80), "[a".repeat(120) + "]".repeat(10), "[".repeat(100) + "S01E01" + "]".repeat(100),
            "a - ".repeat(60), "Title - 12 ".repeat(20), "[Group] " + "a - 1 ".repeat(40), "[Group] " + "a ".repeat(100) + "- 1", "S01E01".repeat(40), "S1E1E1E1E1E1E1E1E1E1E1E1E1E1E1E1E1".repeat(5),
            "S01E01-E02-E03-".repeat(14), "1x01-02-03-".repeat(20), "2024.03.15.".repeat(20), "Saison 1 Episode ".repeat(15), "Season I Episode ".repeat(15), "第1季第".repeat(40), "0".repeat(255), "9".repeat(255),
            "1".repeat(120) + "x" + "1".repeat(120), "S" + "1".repeat(250), "E" + "1".repeat(250), "a".repeat(250) + "!", "a ".repeat(120) + "(2024", "a".repeat(100) + " [" + "b".repeat(100),
            "www." + "a.".repeat(100), "t.me/" + "a/".repeat(100), "@" + "a".repeat(250), "a@".repeat(120), "́".repeat(250), "é".repeat(120), "%".repeat(250), "%41".repeat(80),
            "x".repeat(120) + ".fr.forced.sdh.fr.en.srt", ".fr".repeat(80) + ".srt", ("a." + "fr.").repeat(60) + "srt", "Part ".repeat(50), "Pt.".repeat(80), "CD1".repeat(80),
            "ft. ".repeat(60), "feat. ".repeat(40), "(Official Video) ".repeat(14), "| Prod. by ".repeat(20), " | ".repeat(80) + "x", "A1 ".repeat(80), "01 - ".repeat(50), "01. ".repeat(60)
        ).flatMap { x -> EXTS.map { fit(x, x.length.coerceAtMost(251)) + it } }
        val start = System.nanoTime()
        var worst = 0L; var worstName = ""
        for (n in inputs) {
            val t0 = System.nanoTime()
            runWithin(LIMIT_MS, n.take(60)) { process(n) }
            val dt = (System.nanoTime() - t0) / 1_000_000
            if (dt > worst) { worst = dt; worstName = n.take(60) }
        }
        println("PATHOLOGICAL: ${inputs.size} names, total ${(System.nanoTime() - start) / 1_000_000} ms, worst $worst ms ($worstName)")
        assertTrue(inputs.size > 500)
    }

    @Test fun longFolderPathsStayFast() {
        for (folder in listOf("a/".repeat(120), "Saison ".repeat(35), "[".repeat(250), "S01/".repeat(60), " ".repeat(250), "Show/" + "Saison 1/".repeat(25))) {
            runWithin(LIMIT_MS, "folder " + folder.take(30)) { process("Episode 04.mkv", folder); process("04.mkv", folder); process("Prison Break - 04 - Cut Off.mkv", folder) }
        }
    }

    @Test fun sixThousandRandomMixesOfEngineTokensStayFast() {
        val r = Random(20261003L)
        val start = System.nanoTime()
        var worst = 0L; var worstName = ""
        repeat(6_000) {
            val sb = StringBuilder()
            val target = r.nextInt(20, 255)
            while (sb.length < target) sb.append(UNITS[r.nextInt(UNITS.size)])
            val n = sb.toString().take(251) + EXTS[r.nextInt(EXTS.size)]
            val t0 = System.nanoTime()
            runWithin(LIMIT_MS, n.take(80)) { process(n, if (r.nextInt(5) == 0) "Show/Saison 2" else "") }
            val dt = (System.nanoTime() - t0) / 1_000_000
            if (dt > worst) { worst = dt; worstName = n.take(80) }
        }
        val total = (System.nanoTime() - start) / 1_000_000
        println("PATHOLOGICAL-RANDOM: 6000 names, total $total ms, worst $worst ms ($worstName)")
        assertTrue(total < 16_000, "6 000 names took $total ms")
    }

    companion object { const val LIMIT_MS = 2000L }
}
