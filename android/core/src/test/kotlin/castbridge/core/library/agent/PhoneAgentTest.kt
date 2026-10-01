package castbridge.core.library.agent

import castbridge.core.connect.MemoryKeyValueStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Phone side of the assistant: plan view (filters, groups, pages), "why", caches, incremental work, proactive policy, scale. */
class PhoneAgentTest {
    private val ctx = AgentContext(folders = true, uiLang = "fr", currentYear = 2026, nowMs = 1_800_000_000_000)

    private fun f(name: String, mb: Number = 300, folder: String = "", mtime: Long = 1_700_000_000_000, dur: Long = 0) =
        FileRef(Origin.PHONE, name, mb.toLong() shl 20, mtime, "phone", folder, durationMs = dur)

    private fun snap(files: List<FileRef>) = LibrarySnapshot(Origin.PHONE, files, listOf(VolumeInfo("phone", "Téléphone", "phone", 20L shl 30, 100L shl 30)), 1)

    private fun library(): List<FileRef> = buildList {
        for (e in 1..6) add(f("Prison.Break.S01E0$e.720p.HDTV.x264.mkv", 400 + e))
        for (e in 1..3) add(f("The.Office.US.S02E0$e.1080p.WEB-DL.mkv", 800))
        add(f("Inception.2010.1080p.BluRay.x264-RARBG.mkv", 3000, dur = 8_880_000))
        add(f("Interstellar.2014.720p.BluRay.mkv", 1700))
        add(f("VID-20240315-WA0012.mp4", 12))
        add(f("Inception.2010.1080p.BluRay.x264-RARBG (1).mkv", 3000, dur = 8_880_000))
    }

    @Test fun `groups put every episode of a series together and big cleanups first`() {
        val a = LibraryAgent(ctx).analyze(snap(library()))
        val g = PlanView.groups(a.plan)
        val series = g.filter { it.kind == GroupKind.SERIES }
        assertEquals(2, series.size, g.map { it.title }.toString())
        assertEquals("Prison Break", series[0].title); assertEquals(6, series[0].changes.size)
        assertEquals(3, series[1].changes.size)
        assertTrue(series[0].changes.map { it.file.name } == series[0].changes.map { it.file.name }.sorted(), "episodes in order")
        assertTrue(g.any { it.kind == GroupKind.MOVIES && it.changes.size >= 2 })
        assertTrue(g.indexOfFirst { it.kind == GroupKind.SERIES } < g.indexOfFirst { it.kind == GroupKind.MOVIES })
    }

    @Test fun `filters keep series, films, duplicates and big files apart`() {
        val a = LibraryAgent(ctx).analyze(snap(library()))
        val counts = PlanView.counts(a.plan)
        assertEquals(a.plan.changes.size, counts[PlanFilter.ALL])
        assertTrue(counts[PlanFilter.SERIES]!! >= 9)
        assertTrue(counts[PlanFilter.MOVIES]!! >= 2)
        assertTrue(PlanView.groups(a.plan, PlanFilter.SERIES).all { it.kind == GroupKind.SERIES })
        assertTrue(PlanView.groups(a.plan, PlanFilter.BIG).flatMap { it.changes }.all { it.file.size >= PlanView.BIG_BYTES })
        assertTrue(PlanView.groups(a.plan, PlanFilter.BIG).flatMap { it.changes }.isNotEmpty())
        val dups = PlanView.groups(a.plan, PlanFilter.DUPLICATES).flatMap { it.changes }
        assertTrue(dups.all { it.type == ChangeType.TRASH })
    }

    @Test fun `a group never ticks a deletion`() {
        val a = LibraryAgent(ctx).analyze(snap(library()))
        for (g in PlanView.groups(a.plan)) assertTrue(PlanView.safeIds(g).all { a.plan.byId(it)!!.type != ChangeType.TRASH })
    }

    @Test fun `pages show a limited number of rows and the next page adds more`() {
        val files = (1..200).map { f("Serie.X.S01E${"%03d".format(it)}.720p.mkv", 100) }
        val a = LibraryAgent(ctx).analyze(snap(files))
        val g = PlanView.groups(a.plan).single()
        assertEquals(200, g.changes.size)
        assertEquals(PlanView.PAGE, g.page(1).size)
        assertEquals(2 * PlanView.PAGE, g.page(2).size)
        assertEquals(200, g.page(100).size)
    }

    @Test fun `why lists what was removed, the source and the confidence`() {
        val a = LibraryAgent(ctx).analyze(snap(listOf(f("[www.torrent9.ph] Prison.Break.S01E04.720p.HDTV.x264-RARBG.mkv"))))
        val c = a.plan.renames.single()
        val w = Explain.of(c)
        val text = w.lines.joinToString("\n")
        assertTrue(text.contains("Retiré du nom"), text)
        assertTrue(text.contains("720p") && text.contains("x264") && text.contains("torrent9"), text)
        assertTrue(text.contains("Règles de l'assistant"), text)
        assertTrue(text.contains("Modifier le nom"))
        assertEquals(Sureness.HIGH, w.sure)
    }

    @Test fun `why of a duplicate says what is kept and that nothing is erased`() {
        val a = LibraryAgent(ctx).analyze(snap(library()))
        val d = a.plan.trash.first()
        val text = Explain.of(d).lines.joinToString("\n")
        assertTrue(text.contains("Vous gardez"), text)
        assertTrue(text.contains("Corbeille CastBridge") && text.contains("jamais cochée"), text)
    }

    @Test fun `removed words ignore case and what the new name keeps`() {
        assertEquals(listOf("720p", "x264"), Explain.removed("Inception.2010.720p.x264.mkv", "Inception (2010).mkv"))
        assertEquals(emptyList(), Explain.removed("Inception (2010).mkv", "Inception (2010).mkv"))
    }

    @Test fun `a manual correction is learned and re-planning applies it to the other files of that title`() {
        val kv = MemoryKeyValueStore(); val learned = LearnedRules(kv)
        val files = (1..3).map { f("Prison.Break.S01E0$it.720p.mkv") }
        val a = LibraryAgent(ctx, learned).analyze(snap(files))
        val first = a.plan.renames.first()
        val r = a.plan.withEditedName(first.id, "PB – S01E01.mkv", learned) as Plan.Edit.Ok
        assertTrue(r.learned)
        val again = LibraryAgent(ctx, learned).analyze(snap(files))
        assertTrue(again.plan.renames.all { it.toName!!.startsWith("PB") }, again.plan.renames.map { it.toName }.toString())
        assertTrue(again.plan.renames.all { it.source == Source.LEARNED })
        assertTrue(LearnedRules(kv).size() >= 1)
    }

    // ------------------------------------------------------------------ caches

    @Test fun `the cache survives a restart and is bounded`() {
        val file = File.createTempFile("cache", ".txt"); file.delete()
        val c1 = FileAnalysisCache(file)
        c1.put("a/b.mkv|1|2", 1234)
        val fp = FileRef(Origin.TV, "x.mkv", 5, 6, "internal")
        c1.putFingerprint(fp, "abc123")
        val c2 = FileAnalysisCache(file)
        assertEquals(1234L, c2.get("a/b.mkv|1|2"))
        assertEquals("abc123", c2.fingerprint(fp))
        assertNull(c2.fingerprint(fp.copy(size = 6)), "a file that changed is read again")
        val small = FileAnalysisCache(file, maxEntries = 10)
        for (i in 1..100) small.put("k$i", i.toLong())
        assertTrue(small.size() <= 30)
        small.clear(); assertEquals(0, FileAnalysisCache(file).size())
    }

    @Test fun `a fingerprint that could not be read is not retried for a day`() {
        val file = File.createTempFile("cache", ".txt"); file.delete()
        val c = FileAnalysisCache(file)
        val x = FileRef(Origin.TV, "x.mkv", 5, 6, "internal")
        c.putFingerprint(x, null, now = 1000)
        assertEquals("", c.fingerprint(x, now = 2000))
        assertNull(c.fingerprint(x, now = 1000 + 86_400_001L))
    }

    @Test fun `caching fingerprinter reads once and each analysis goes further than the last`() {
        val file = File.createTempFile("cache", ".txt"); file.delete()
        val cache = FileAnalysisCache(file)
        var reads = 0
        val inner = Fingerprinter { reads++; "fp-" + it.name.takeLast(3) }
        // 10 pairs of same-size files: 20 candidates, budget of 6 new reads per analysis
        val files = (1..10).flatMap { i -> listOf(f("Film $i (2010).mkv", 100 + i.toLong()), f("Film $i (2010) copie.mkv", 100 + i.toLong())) }
        fun runOnce(): Int { val before = reads; LibraryAgent(ctx, null, null, CachingFingerprinter(inner, cache), maxFingerprints = 6).analyze(snap(files)); return reads - before }
        assertEquals(6, runOnce()); assertEquals(6, runOnce()); assertEquals(6, runOnce()); assertEquals(2, runOnce()); assertEquals(0, runOnce())
    }

    @Test fun `last analysis summary round-trips and speaks plain french`() {
        val l = LastAnalysis(Origin.PHONE, 1_000_000, 1240, 37, 3, 4L shl 30)
        assertEquals(l, LastAnalysis.parse(l.encode()))
        assertEquals("il y a 2 h", l.ago(1_000_000 + 2 * 3_600_000 + 5))
        assertEquals("hier", l.ago(1_000_000 + 30 * 3_600_000))
        assertNull(LastAnalysis.parse("n'importe quoi"))
    }

    // ------------------------------------------------------------------ proactive

    private val many = listOf(Insight("names", Insight.NOTICE, "37 fichiers mal nommés"), Insight("dups", Insight.NOTICE, "3 doublons = 4,2 Go", 4L shl 30))

    @Test fun `proactive notification is off by default and never nags`() {
        val s = AgentSettings(MemoryKeyValueStore())
        assertFalse(s.proactiveNotify)
        val day = 86_400_000L; val now = 100 * day
        assertNull(ProactivePolicy.decide(false, false, 0, null, many, emptyMap(), now), "option off: silent")
        val m = ProactivePolicy.decide(true, false, 0, null, many, emptyMap(), now)
        assertNotNull(m)
        assertNull(ProactivePolicy.decide(true, false, now - 2 * day, null, many, emptyMap(), now), "less than a week since the last one")
        assertNull(ProactivePolicy.decide(true, false, now - 8 * day, m!!.signature, many, emptyMap(), now), "same finding again")
        assertNull(ProactivePolicy.decide(true, true, 0, null, many, emptyMap(), now), "child profile: silent")
        assertNull(ProactivePolicy.decide(true, false, 0, null, many, mapOf("names" to now + day, "dups" to now + day), now), "snoozed advice stays hidden")
        assertNull(ProactivePolicy.decide(true, false, 0, null, listOf(Insight("names", Insight.NOTICE, "3 fichiers mal nommés")), emptyMap(), now), "not worth an interruption")
    }

    @Test fun `a full volume is worth a notification, and the settings remember it`() {
        val m = ProactivePolicy.decide(true, false, 0, null, listOf(Insight("full:usb", Insight.WARNING, "La clé « Clé USB » est pleine à 92 %")), emptyMap(), 1_000_000_000_000)
        assertNotNull(m); assertTrue(m.text.contains("92"))
        val s = AgentSettings(MemoryKeyValueStore()); s.markNotified(5, "names:37")
        assertEquals(5, s.lastNotifiedAt); assertEquals("names:37", s.lastNotifiedSignature)
        s.proactiveNotify = true; s.clearAll(); assertFalse(s.proactiveNotify)
    }

    // ------------------------------------------------------------------ scale

    @Test fun `five thousand files are analysed in a few seconds and the plan stays consistent`() {
        val titles = listOf("Prison Break", "The Office US", "Breaking Bad", "Game of Thrones", "Lost", "Dark", "Narcos", "Vikings")
        val files = ArrayList<FileRef>()
        var n = 0
        loop@ for (s in 1..12) for (e in 1..25) for (t in titles) {
            files += f("${t.replace(' ', '.')}.S${"%02d".format(s)}E${"%02d".format(e)}.720p.HDTV.x264-GRP.mkv", 200 + (n % 50), folder = "Download")
            if (++n >= 2400) break@loop
        }
        for (i in 1..2000) files += f("Film.Numero.$i.${1950 + i % 70}.1080p.BluRay.x264.mkv", 1500 + i, folder = "Movies")
        for (i in 1..600) files += f("VID-2024${"%02d".format(i % 12 + 1)}${"%02d".format(i % 28 + 1)}-WA${"%04d".format(i)}.mp4", 8, folder = "WhatsApp Video")
        val t0 = System.nanoTime()
        val a = LibraryAgent(ctx).analyze(snap(files))
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("PERF analyze(${files.size} files) = $ms ms, ${a.plan.changes.size} changes")
        assertTrue(files.size >= 5000)
        assertTrue(ms < 8_000, "analysis took $ms ms")
        val t1 = System.nanoTime()
        val g = PlanView.groups(a.plan)
        val gms = (System.nanoTime() - t1) / 1_000_000
        println("PERF groups = $gms ms, ${g.size} groups")
        assertTrue(gms < 1_500)
        assertEquals(a.plan.changes.size, g.sumOf { it.changes.size }, "every change is in exactly one group")
        assertEquals(a.plan.changes.map { it.id }.toSet().size, a.plan.changes.size, "ids are unique")
    }
}
