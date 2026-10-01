package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val HGB = 1L shl 30
private const val HMB = 1L shl 20
private const val HDAY = 86_400_000L
private val HNOW = java.time.LocalDate.of(2026, 10, 1).atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
private val HCTX = AgentContext(nowMs = HNOW, zone = java.time.ZoneId.of("UTC"), currentYear = 2026)
private val HINTERNAL = VolumeInfo("internal", "Mémoire interne", "internal", free = 10 * HGB, total = 32 * HGB)
private val HUSB = VolumeInfo("usb1", "Clé USB", "usb", free = 40 * HGB, total = 58 * HGB, fs = "exFAT")

private fun f(name: String, size: Long = 700 * HMB, vol: String = "internal", dur: Long = 0, watched: Boolean = false, played: Long = 0, mtime: Long = 0) =
    FileRef(Origin.TV, name, size, mtime = mtime, volumeId = vol, durationMs = dur, watched = watched, playedAtMs = played)

private fun snap(vararg files: FileRef, vols: List<VolumeInfo> = listOf(HINTERNAL, HUSB)) = LibrarySnapshot(Origin.TV, files.toList(), vols, HNOW)

class HealthTest {
    private fun analyze(s: LibrarySnapshot, ctx: AgentContext = HCTX) = LibraryAgent(ctx).analyze(s)

    // ------------------------------------------------------------------ incomplete series

    @Test fun aMissingEpisodeInTheMiddleIsReported() {
        val a = analyze(snap(f("Prison Break – S01E01.mkv"), f("Prison Break – S01E02.mkv"), f("Prison Break – S01E04.mkv"), f("Prison Break – S01E05.mkv")))
        val m = a.health.of(FindingType.MISSING_EPISODES).single()
        assertEquals("Prison Break saison 1 : il manque l'épisode 3", m.title)
    }

    @Test fun severalMissingEpisodesAndAMissingStartAreReported() {
        val a = analyze(snap(f("Dark – S02E02.mkv"), f("Dark – S02E03.mkv"), f("Dark – S02E06.mkv"), f("Dark – S02E07.mkv")))
        assertEquals("Dark saison 2 : il manque les épisodes 1, 4, 5", a.health.of(FindingType.MISSING_EPISODES).single().title)
    }

    @Test fun aSeasonStartedLaterIsNotConsideredBroken() {
        // somebody who only has episodes 5 to 8: nothing is "missing" before 5
        val a = analyze(snap(f("Dark – S02E05.mkv"), f("Dark – S02E06.mkv"), f("Dark – S02E07.mkv"), f("Dark – S02E08.mkv")))
        assertTrue(a.health.of(FindingType.MISSING_EPISODES).isEmpty())
    }

    @Test fun tooFewEpisodesOrTooManyHolesAreNotReported() {
        assertTrue(analyze(snap(f("Dark – S02E01.mkv"), f("Dark – S02E03.mkv"))).health.of(FindingType.MISSING_EPISODES).isEmpty(), "two files prove nothing")
        val sparse = analyze(snap(f("Lost – S01E01.mkv"), f("Lost – S01E30.mkv"), f("Lost – S01E60.mkv")))
        assertTrue(sparse.health.of(FindingType.MISSING_EPISODES).isEmpty(), "a few far-apart episodes are a choice, not a hole")
    }

    @Test fun doubleEpisodesCoverTheirWholeRange() {
        val a = analyze(snap(f("Friends – S01E01-E02.mkv"), f("Friends – S01E03.mkv"), f("Friends – S01E04.mkv")))
        assertTrue(a.health.of(FindingType.MISSING_EPISODES).isEmpty())
    }

    @Test fun aSeriesYouWatchRecentlyComesFirst() {
        val recent = HNOW - 5 * HDAY
        val a = analyze(snap(
            f("Dark – S01E01.mkv"), f("Dark – S01E02.mkv"), f("Dark – S01E04.mkv"),
            f("Lost – S01E01.mkv", played = recent, watched = true), f("Lost – S01E02.mkv", played = recent, watched = true), f("Lost – S01E04.mkv", played = recent, watched = true)))
        val missing = a.health.ranked.filter { it.type == FindingType.MISSING_EPISODES }
        assertTrue(missing.first().title.startsWith("Lost"), "the series being watched is the one to complete first: ${missing.map { it.title }}")
        assertTrue(missing.first().priority > missing.last().priority + 250, missing.map { it.title + " " + it.priority }.toString())
    }

    @Test fun animeWithoutSeasonAreChecked() {
        val a = analyze(snap(f("Bleach – E10.mkv"), f("Bleach – E11.mkv"), f("Bleach – E13.mkv"), f("Bleach – E14.mkv")))
        assertEquals("Bleach : il manque l'épisode 12", a.health.of(FindingType.MISSING_EPISODES).single().title)
    }

    // ------------------------------------------------------------------ mixed seasons

    @Test fun aSeasonSpreadOverTwoVolumesIsMentioned() {
        val a = analyze(snap(f("Dark – S01E01.mkv"), f("Dark – S01E02.mkv", vol = "usb1"), f("Dark – S01E03.mkv")))
        val x = a.health.of(FindingType.MIXED_LOCATIONS).single()
        assertTrue(x.title.contains("répartie sur 2 supports"), x.title)
        assertTrue(x.detail.contains("Clé USB") && x.detail.contains("Mémoire interne"))
    }

    @Test fun mixedNumberingIsMentioned() {
        val a = analyze(snap(f("Dark – S01E01.mkv"), f("Dark – S01E02.mkv"), f("Dark – E03.mkv")))
        assertEquals(1, a.health.of(FindingType.MIXED_NUMBERING).size)
    }

    // ------------------------------------------------------------------ duplicates and recoverable space

    @Test fun qualityDuplicatesKeepTheBestAndAreCounted() {
        val a = analyze(snap(f("Dark – S01E01 [720p].mkv", size = 800 * HMB), f("Dark – S01E01 [1080p].mkv", size = 2 * HGB), f("Dark – S01E02.mkv")))
        val q = a.health.of(FindingType.QUALITY_DUPLICATE).single()
        assertEquals(800 * HMB, q.bytes)
        assertTrue(q.detail.contains("Dark – S01E01 [1080p].mkv"), "says which one stays: ${q.detail}")
        assertEquals(1, q.changes.size)
        assertFalse(q.changes.single().checked, "never ticked")
        assertEquals(800 * HMB, a.health.recoverableBytes)
    }

    @Test fun recoverableSpaceAddsEverythingOnceAndSpeaksInGigabytes() {
        val a = analyze(snap(
            f("Film.2010.1080p.mkv", size = 2 * HGB, dur = 7_200_000), f("Film.2010.1080p (1).mkv", size = 2 * HGB, dur = 7_200_000),   // same size, same duration, same name once cleaned
            f("Serie – S01E01.mkv.part", size = 1500 * HMB, mtime = HNOW - 10 * HDAY),
            f("vide.mp4", size = 0)))
        val h = a.health
        assertTrue(h.recoverableBytes >= 3 * HGB, "2 Go de doublon + 1,5 Go interrompu: ${h.recoverableBytes}")
        val line = h.headline()
        assertNotNull(line)
        assertTrue(line.contains("Go récupérables"), line)
        assertTrue(line.contains("fichier"), line)
    }

    @Test fun aFileInTwoFindingsIsCountedOnce() {
        val a = analyze(snap(f("Film.2010.mkv.part", size = 1 * HGB, mtime = HNOW - 9 * HDAY), f("Film.2010.mkv.part.aria2", size = 10 * 1024L)))
        assertEquals(1 * HGB, a.health.recoverableBytes)
    }

    // ------------------------------------------------------------------ broken files

    @Test fun emptyAndInterruptedFilesAreFoundAndRecentDownloadsAreLeftAlone() {
        val a = analyze(snap(
            f("vide.mp4", size = 0),
            f("Serie S01E01.mkv.part", size = 500 * HMB, mtime = HNOW - 10 * HDAY),
            f("En cours.mkv.part", size = 200 * HMB, mtime = HNOW - HDAY / 2),
            f("Film.2010.1080p.mkv", size = 3 * HGB), f("Film.2010.1080p.mkv.aria2", size = 4096, mtime = HNOW - 20 * HDAY)))
        val broken = a.health.of(FindingType.BROKEN_FILE)
        val names = broken.map { it.title }
        assertTrue(names.any { it.contains("vide.mp4") })
        assertTrue(names.any { it.contains("Serie S01E01.mkv.part") })
        assertFalse(names.any { it.contains("En cours") }, "a download of the last hours is not mentioned")
        assertTrue(broken.any { it.title.contains("Film.2010.1080p.mkv") && !it.title.contains("aria2") } || broken.any { it.title.contains("aria2") }, "an old aria2 pair is an interrupted download")
        val trashes = broken.flatMap { it.changes }
        assertTrue(trashes.all { it.type == ChangeType.TRASH && !it.checked && it.why in setOf(TrashWhy.EMPTY, TrashWhy.PARTIAL) })
    }

    @Test fun aVideoTooSmallForItsDurationIsFlaggedButNeverTrashedAutomatically() {
        val a = analyze(snap(f("Film.2010.1080p.mkv", size = 20 * HMB, dur = 120 * 60_000L)))
        val b = a.health.of(FindingType.BROKEN_FILE).single()
        assertTrue(b.detail.contains("tronqué"), b.detail)
        assertTrue(b.changes.isEmpty(), "a doubt is advice, not an action")
        assertEquals(0, b.bytes)
    }

    @Test fun anEpisodeMuchSmallerThanItsSiblingsIsFlagged() {
        val a = analyze(snap(f("Dark – S01E01.mkv", size = 700 * HMB), f("Dark – S01E02.mkv", size = 720 * HMB), f("Dark – S01E03.mkv", size = 690 * HMB), f("Dark – S01E04.mkv", size = 710 * HMB), f("Dark – S01E05.mkv", size = 60 * HMB)))
        val b = a.health.of(FindingType.BROKEN_FILE).single()
        assertTrue(b.title.contains("S01E05"))
        assertEquals(Insight.INFO, b.severity)
    }

    @Test fun healthyNormalLibraryHasNoFindingAndNoNoise() {
        val a = analyze(snap(f("Dark – S01E01.mkv"), f("Dark – S01E02.mkv"), f("Dark – S01E03.mkv"), f("Inception (2010).mkv", size = 2 * HGB)))
        assertTrue(a.health.findings.isEmpty(), a.health.findings.map { it.title }.toString())
        assertEquals(null, a.health.headline())
    }

    // ------------------------------------------------------------------ priorities and space

    @Test fun spacePressureComesFirstAndSaysWhatRecoveringWouldChange() {
        val tight = VolumeInfo("internal", "Mémoire interne", "internal", free = 800 * HMB, total = 32 * HGB)
        val a = analyze(snap(
            f("Dark – S01E01 [720p].mkv", size = 1500 * HMB), f("Dark – S01E01 [1080p].mkv", size = 2500 * HMB),
            f("Dark – S01E02.mkv"), f("Dark – S01E04.mkv"), f("Dark – S01E05.mkv"),
            vols = listOf(tight, HUSB)))
        assertEquals(FindingType.SPACE_PRESSURE, a.health.ranked.first().type)
        val p = a.health.pressure.single()
        assertEquals(1500 * HMB, p.recoverable)
        assertEquals(800 * HMB + 1500 * HMB, p.freeAfter)
        assertTrue(a.health.ranked.first().detail.contains("récupérables ici"))
        // the duplicate on the tight volume outranks advice about an episode
        val order = a.health.ranked.map { it.type }
        assertTrue(order.indexOf(FindingType.QUALITY_DUPLICATE) < order.indexOf(FindingType.MISSING_EPISODES), order.toString())
    }

    @Test fun whenNothingSafeCanBeRecoveredItSaysSo() {
        val tight = VolumeInfo("internal", "Mémoire interne", "internal", free = 500 * HMB, total = 32 * HGB)
        val a = analyze(snap(f("Inception (2010).mkv", size = 2 * HGB), vols = listOf(tight, HUSB)))
        val s = a.health.of(FindingType.SPACE_PRESSURE).single()
        assertTrue(s.detail.contains("Rien de sûr à récupérer"), s.detail)
    }

    @Test fun insightsCarryTheNumbers() {
        val a = analyze(snap(
            f("Film.2010.1080p.mkv", size = 2 * HGB, dur = 7_200_000), f("Film.2010.1080p (1).mkv", size = 2 * HGB, dur = 7_200_000), f("Serie.mkv.part", size = 1500 * HMB, mtime = HNOW - 10 * HDAY),
            f("Dark – S01E01.mkv"), f("Dark – S01E02.mkv"), f("Dark – S01E04.mkv")))
        val texts = a.insights.map { it.text }
        assertTrue(texts.any { it.contains("Go récupérables") }, texts.toString())
        assertTrue(texts.any { it.contains("fichier") && it.contains("incomplet") }, texts.toString())
        assertTrue(texts.any { it.contains("il manque l'épisode 3") }, texts.toString())
    }

    // ------------------------------------------------------------------ safety

    @Test fun protectedFilesAreNeverSuggestedForTheTrash() {
        val guard = object : ContentGuard {
            override fun isProtected(file: FileRef) = file.name.startsWith("Secret")
            override val childProfileActive = false
        }
        val a = analyze(snap(f("Secret vide.mp4", size = 0), f("Secret.mkv.part", size = 1 * HGB, mtime = HNOW - 9 * HDAY), f("Ok vide.mp4", size = 0)), HCTX.copy(guard = guard))
        val names = a.health.findings.flatMap { it.fileKeys }
        assertTrue(names.none { it.contains("Secret") }, names.toString())
        assertTrue(names.any { it.contains("Ok vide") })
    }

    @Test fun aChildProfileGetsNoSuggestionToDelete() {
        val guard = object : ContentGuard {
            override fun isProtected(file: FileRef) = false
            override val childProfileActive = true
        }
        val a = analyze(snap(f("vide.mp4", size = 0)), HCTX.copy(guard = guard))
        assertTrue(a.health.findings.isEmpty(), "no file is plannable under a child profile")
    }

    @Test fun healthTrashChangesRunThroughTheExecutorLikeAnyOther() {
        val ops = FakeOps(false).also { o -> o.addVolume(HINTERNAL); o.add("internal", "vide.mp4", 0, ""); o.add("internal", "x.mkv.part", 100 * HMB, "") }
        val a = analyze(snap(f("vide.mp4", size = 0), f("x.mkv.part", size = 100 * HMB, mtime = HNOW - 9 * HDAY)))
        val changes = a.health.findings.flatMap { it.changes }
        assertEquals(2, changes.size)
        val plan = Plan(changes)
        val r = Executor(ops, MemoryJournal(), HCTX).run(plan, changes.map { it.id }.toSet(), true, "h1")
        assertTrue(r.reports.all { it.state == State.DONE }, r.reports.toString())
    }
}
