package castbridge.core.library.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GB = 1L shl 30
private const val MB = 1L shl 20
private const val DAY = 86_400_000L

private val NOW = java.time.LocalDate.of(2026, 10, 1).atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
private val CTX = AgentContext(nowMs = NOW, zone = java.time.ZoneId.of("UTC"), currentYear = 2026)

private fun tv(name: String, size: Long = 700 * MB, vol: String = "internal", dur: Long = 0, watched: Boolean = false, played: Long = 0, playing: Boolean = false, mtime: Long = 0) =
    FileRef(Origin.TV, name, size, mtime = mtime, volumeId = vol, durationMs = dur, watched = watched, playedAtMs = played, playing = playing)

private val INTERNAL = VolumeInfo("internal", "Mémoire interne", "internal", free = 10 * GB, total = 32 * GB)
private val USB = VolumeInfo("usb1", "Clé USB", "usb", free = 40 * GB, total = 58 * GB, fs = "exFAT")

private fun snap(vararg f: FileRef, vols: List<VolumeInfo> = listOf(INTERNAL, USB)) = LibrarySnapshot(Origin.TV, f.toList(), vols, NOW)

class AgentPlanTest {
    private fun analyze(s: LibrarySnapshot, ctx: AgentContext = CTX, fp: Fingerprinter? = null, learned: LearnedRules? = null, model: NamingModel? = null) =
        LibraryAgent(ctx, learned, model, fp).analyze(s)

    // ------------------------------------------------------------------ renames

    @Test fun seriesGetReadableNamesAndConfidentOnesStartTicked() {
        val a = analyze(snap(tv("Prison.Break.S01E04.FRENCH.DVDRip.XviD-JMT.avi"), tv("random video.mp4"), tv("Prison Break – S01E05.mkv")))
        val r = a.plan.renames.single()
        assertEquals("Prison Break – S01E04.avi", r.toName)
        assertTrue(r.checked, "a SxxExx name is a confident proposal")
        assertNull(r.toFolder, "the TV library is flat: no folder")
        assertEquals(setOf(r.id), a.plan.defaultSelection())
        assertEquals(1, a.stats.toRename)
    }

    @Test fun unknownNamesAreNeverRenamedWithoutASiteMark() {
        val a = analyze(snap(tv("Mon voyage.mp4"), tv("clip123.mp4"), tv("[www.torrent9.ph] Mystere.mp4")))
        val names = a.plan.renames.map { it.toName }
        assertEquals(listOf("Mystere.mp4"), names)
        assertFalse(a.plan.renames.single().checked, "an unknown name cleaned of its site mark is only a suggestion")
    }

    @Test fun phoneFoldersAreProposedOnlyWhenTheSourceHasFolders() {
        val f = FileRef(Origin.PHONE, "Inception.2010.1080p.BluRay.mkv", GB, volumeId = "phone")
        val tvCtx = analyze(LibrarySnapshot(Origin.PHONE, listOf(f), emptyList()), CTX.copy(folders = false))
        assertNull(tvCtx.plan.renames.single().toFolder)
        val ph = analyze(LibrarySnapshot(Origin.PHONE, listOf(f), emptyList()), CTX.copy(folders = true))
        assertEquals("Films/Inception (2010)", ph.plan.renames.single().toFolder)
        assertEquals("Inception (2010).mkv", ph.plan.renames.single().toName)
    }

    @Test fun alreadyOrganisedPhoneFilesAreLeftAlone() {
        val f = FileRef(Origin.PHONE, "Inception (2010).mkv", GB, volumeId = "phone", folder = "Films/Inception (2010)")
        val a = analyze(LibrarySnapshot(Origin.PHONE, listOf(f), emptyList()), CTX.copy(folders = true))
        assertTrue(a.plan.changes.isEmpty())
    }

    @Test fun nameConflictsGetASuffixNeverAnOverwrite() {
        val a = analyze(snap(
            tv("WhatsApp Video 2024-03-15 at 14.22.11.mp4", size = 10 * MB),
            tv("WhatsApp Video 2024-03-15 at 14.22.11 (1).mp4", size = 12 * MB),
            tv("Vidéo WhatsApp – 2024-03-15 14h22 (2).mp4", size = 14 * MB),
        ))
        val targets = a.plan.renames.mapNotNull { it.toName }.sorted()
        assertEquals(targets.size, targets.toSet().size, "no two files may get the same name: $targets")
        assertTrue(targets.all { it.startsWith("Vidéo WhatsApp – 2024-03-15 14h22") })
        // the existing "(2)" file is not touched, and the new names avoid it
        assertFalse(targets.contains("Vidéo WhatsApp – 2024-03-15 14h22 (2).mp4"))
    }

    @Test fun targetNameTakenByAnotherLibraryFileOnAnotherVolumeIsAvoided() {
        // the TV has ONE name space across its volumes: renaming on the USB key must not collide with the internal memory
        val a = analyze(snap(tv("Inception (2010).mkv", size = 5 * GB, vol = "internal"), tv("Inception.2010.mkv", size = 4 * GB, vol = "usb1")))
        a.plan.renames.mapNotNull { it.toName }.forEach { assertTrue(it != "Inception (2010).mkv") }
    }

    @Test fun uniqueNamePrefersAMeaningfulDifferenceThenANumber() {
        val pl = Planner(CTX)
        val taken = mutableSetOf("tv|show – s01e01.mkv")
        val p = NameParser.parse("Show.S01E01.VOSTFR.1080p.mkv")
        assertEquals("Show – S01E01 [VOSTFR].mkv", pl.uniqueName("Show – S01E01.mkv", p, taken, "tv"))
        taken += "tv|show – s01e01 [vostfr].mkv"
        assertEquals("Show – S01E01 [1080p].mkv", pl.uniqueName("Show – S01E01.mkv", p, taken, "tv"))
        taken += "tv|show – s01e01 [1080p].mkv"
        assertEquals("Show – S01E01 (2).mkv", pl.uniqueName("Show – S01E01.mkv", p, taken, "tv"))
    }

    @Test fun languageTagFollowsTheUsersHabit() {
        // a user who mostly watches VOSTFR does not need the tag, but VF is worth writing
        val watched = (1..6).map { tv("Serie.S01E0$it.VOSTFR.mkv", played = NOW - it * DAY, watched = true) }
        val a = analyze(snap(*watched.toTypedArray(), tv("Autre.Serie.S01E01.FRENCH.mkv"), tv("Autre.Serie.S01E02.VOSTFR.mkv")))
        assertEquals(Audio.VOSTFR, Habits.from(a.snapshot.files, { f -> NameParser.parse(f.name) }, java.time.ZoneId.of("UTC")).audioPref)
        val byName = a.plan.renames.associate { it.file.name to it.toName }
        assertEquals("Autre Serie – S01E01 [VF].mkv", byName["Autre.Serie.S01E01.FRENCH.mkv"])
        assertEquals("Autre Serie – S01E02.mkv", byName["Autre.Serie.S01E02.VOSTFR.mkv"])
    }

    @Test fun subtitlesFollowTheirVideo() {
        val a = analyze(snap(tv("Prison.Break.S01E04.FRENCH.DVDRip.avi"), tv("Prison.Break.S01E04.FRENCH.srt", size = 60_000)))
        val sub = a.plan.renames.first { it.file.name.endsWith(".srt") }
        assertEquals("Prison Break – S01E04.fr.srt", sub.toName)
        assertNotNull(sub.pairKey)
        val vid = a.plan.renames.first { it.file.name.endsWith(".avi") }
        assertEquals(vid.pairKey, sub.pairKey)
        // ticking the video ticks its subtitles
        assertTrue(a.plan.withPairs(setOf(vid.id)).contains(sub.id))
    }

    @Test fun learnedCorrectionsChangeFutureProposals() {
        val kv = castbridge.core.connect.MemoryKeyValueStore()
        val learned = LearnedRules(kv)
        val first = analyze(snap(tv("Prison.Break.S01E04.mkv")), learned = learned)
        val c = first.plan.renames.single()
        val edit = first.plan.withEditedName(c.id, "PB – S01E04.mkv", learned) as Plan.Edit.Ok
        assertTrue(edit.learned)
        assertEquals("PB – S01E04.mkv", edit.plan.byId(c.id)!!.toName)
        // next analysis: another episode of the same title uses the learned title
        val again = analyze(snap(tv("Prison.Break.S01E05.mkv")), learned = LearnedRules(kv))
        assertEquals("PB – S01E05.mkv", again.plan.renames.single().toName)
        assertEquals(Source.LEARNED, again.plan.renames.single().source)
        // erasable
        LearnedRules(kv).clear()
        assertEquals("Prison Break – S01E05.mkv", analyze(snap(tv("Prison.Break.S01E05.mkv")), learned = LearnedRules(kv)).plan.renames.single().toName)
    }

    @Test fun editedNamesAreValidatedLikeAnyOther() {
        val a = analyze(snap(tv("Prison.Break.S01E04.mkv")))
        val c = a.plan.renames.single()
        listOf("../x.mkv", "a/b.mkv", "a:b.mkv", "", ".hidden", "x*.mkv").forEach {
            assertTrue(a.plan.withEditedName(c.id, it) is Plan.Edit.Refused, "should refuse '$it'")
        }
    }

    // ------------------------------------------------------------------ duplicates

    @Test fun exactDuplicatesNeedMatchingFingerprints() {
        val a = tv("Film A.mkv", size = 2 * GB, played = NOW - DAY)
        val b = tv("Film A (1).mkv", size = 2 * GB)
        val c = tv("Other.mkv", size = 2 * GB)
        val fp = Fingerprinter { f -> if (f.name == "Other.mkv") "zzz" else "aaa" }
        val an = analyze(snap(a, b, c), fp = fp)
        val t = an.plan.trash.single()
        assertEquals("Film A (1).mkv", t.file.name, "the copy with the viewing history is kept")
        assertEquals(TrashWhy.DUPLICATE, t.why)
        assertFalse(t.checked)
        assertEquals(2 * GB, an.stats.duplicateBytes)
    }

    @Test fun sameSizeButDifferentContentIsNotADuplicate() {
        val an = analyze(snap(tv("A.mkv", size = GB), tv("B.mkv", size = GB)), fp = Fingerprinter { f -> f.name })
        assertTrue(an.plan.trash.isEmpty())
    }

    @Test fun withoutFingerprintOnlyDurationAndCleanedNameMakeAProbableDuplicate() {
        val same = analyze(snap(tv("Inception 2010.mkv", size = GB, dur = 8_880_000), tv("Inception.2010.mkv", size = GB, dur = 8_880_500)))
        assertEquals(1, same.plan.trash.size)
        assertEquals(0.75, same.plan.trash.single().confidence)
        val other = analyze(snap(tv("Inception 2010.mkv", size = GB, dur = 8_880_000), tv("Avatar.2009.mkv", size = GB, dur = 8_880_000)))
        assertTrue(other.plan.trash.isEmpty(), "same size and duration but other names: not proposed")
        val noDuration = analyze(snap(tv("Inception 2010.mkv", size = GB), tv("Inception.2010.mkv", size = GB)))
        assertTrue(noDuration.plan.trash.isEmpty(), "no duration known: stay cautious")
    }

    @Test fun lowerQualityVersionsOfTheSameEpisodeAreSuggestedNotForcedAndLanguagesAreKept() {
        val a = analyze(snap(
            tv("Show.S01E01.720p.mkv", size = 800 * MB), tv("Show.S01E01.1080p.mkv", size = 2 * GB),
            tv("Show.S01E02.VOSTFR.720p.mkv", size = 800 * MB), tv("Show.S01E02.FRENCH.1080p.mkv", size = 2 * GB),
        ))
        val t = a.plan.trash.single()
        assertEquals("Show.S01E01.720p.mkv", t.file.name)
        assertEquals(TrashWhy.LOWER_QUALITY, t.why)
        assertEquals("Show.S01E01.1080p.mkv", t.keep!!.name)
        assertFalse(t.checked)
        assertTrue(a.plan.renames.none { it.file.name == t.file.name }, "a file going to the trash is not renamed")
    }

    // ------------------------------------------------------------------ space

    @Test fun lowInternalSpaceProposesMovesToTheUsbKeyKeeping1GbFree() {
        val internal = INTERNAL.copy(free = GB + 400 * MB)
        val usb = USB.copy(free = 3 * GB + 500 * MB)
        val a = analyze(snap(tv("Big1.mkv", size = 1500 * MB), tv("Big2.mkv", size = 1400 * MB), tv("Big3.mkv", size = 1300 * MB), tv("Small.mkv", size = 100 * MB), vols = listOf(internal, usb)))
        val moves = a.plan.moves
        assertTrue(moves.isNotEmpty())
        var free = usb.free
        for (m in moves) { free -= m.file.size; assertTrue(free >= GB, "the USB key must keep >= 1 GB free, had $free after ${m.file.name}") }
        assertEquals("usb1", moves.first().toVolume)
        assertTrue(moves.none { it.checked }, "moves are heavy: never ticked by default")
        assertEquals("Big1.mkv", moves.first().file.name, "biggest files first")
    }

    @Test fun noMoveWhenNoUsbKeyIsPluggedButTheUserIsTold() {
        val a = analyze(snap(tv("Big1.mkv", size = 1500 * MB), vols = listOf(INTERNAL.copy(free = 500 * MB))))
        assertTrue(a.plan.moves.isEmpty())
        assertTrue(a.plan.notes.any { it.contains("aucune clé USB") })
        assertTrue(a.insights.any { it.severity == Insight.WARNING })
    }

    @Test fun nothingIsMovedWhenInternalSpaceIsFine() {
        assertTrue(analyze(snap(tv("Big1.mkv", size = 1500 * MB))).plan.moves.isEmpty())
    }

    @Test fun fat32KeysNeverReceiveFilesOver4Gb() {
        val usb = USB.copy(fs = "FAT32", maxFileBytes = 4 * GB - 1, free = 50 * GB)
        val a = analyze(snap(tv("Huge.mkv", size = 5 * GB), vols = listOf(INTERNAL.copy(free = 600 * MB), usb)))
        assertTrue(a.plan.moves.isEmpty())
    }

    @Test fun watchedLongAgoFilesAreOnlySuggestedWhenSpaceIsTightAndNeverTicked() {
        val old = tv("Old.S01E01.mkv", size = 1500 * MB, watched = true, played = NOW - 120 * DAY)
        val recent = tv("Recent.S01E01.mkv", size = 1500 * MB, watched = true, played = NOW - 10 * DAY)
        val unseen = tv("Unseen.S01E01.mkv", size = 1500 * MB)
        val tight = analyze(snap(old, recent, unseen, vols = listOf(INTERNAL.copy(free = 500 * MB, total = 32 * GB))))
        val t = tight.plan.trash.single()
        assertEquals("Old.S01E01.mkv", t.file.name)
        assertEquals(TrashWhy.WATCHED_OLD, t.why)
        assertFalse(t.checked)
        assertTrue(analyze(snap(old, recent, unseen)).plan.trash.isEmpty(), "plenty of room: leave the old files alone")
    }

    @Test fun playingProtectedAndChildProfileFilesAreNeverPlanned() {
        val guard = object : ContentGuard {
            override fun isProtected(file: FileRef) = file.name.contains("Adulte")
            override val childProfileActive = false
        }
        val a = analyze(snap(tv("Film.Adulte.2020.1080p.mkv"), tv("Prison.Break.S01E01.mkv", playing = true), tv("Prison.Break.S01E02.mkv")), CTX.copy(guard = guard))
        assertEquals(listOf("Prison Break – S01E02.mkv"), a.plan.renames.map { it.toName })
        assertEquals(setOf("en cours de lecture"), a.plan.skipped.map { it.reason }.toSet(), "a protected file is not even listed as skipped")
        assertEquals(1, a.snapshot.protectedCount)
        assertTrue(a.snapshot.files.none { it.name.contains("Adulte") })
        val child = object : ContentGuard { override fun isProtected(file: FileRef) = false; override val childProfileActive = true }
        val c = analyze(snap(tv("Prison.Break.S01E02.mkv")), CTX.copy(guard = child))
        assertTrue(c.plan.changes.isEmpty())
        assertTrue(c.plan.notes.any { it.contains("profil enfant") })
    }

    // ------------------------------------------------------------------ insights and habits

    @Test fun insightsAreShortAndUseTheWordsOfTheSpec() {
        val a = analyze(snap(
            tv("A.S01E01.mkv"), tv("B.S01E01.mkv"), tv("C.S01E01.mkv", size = GB, dur = 100_000), tv("C.S01E01 (1).mkv", size = GB, dur = 100_000),
            vols = listOf(INTERNAL, USB.copy(free = 4 * GB, total = 58 * GB)),
        ))
        val texts = a.insights.map { it.text }
        assertTrue(texts.any { it.matches(Regex("\\d+ fichiers mal nommés")) }, texts.toString())
        assertTrue(texts.any { it.startsWith("1 doublon = ") }, texts.toString())
        assertTrue(texts.any { it == "La clé « Clé USB » est pleine à 93 %" }, texts.toString())
    }

    @Test fun snoozedInsightsStayHidden() {
        val all = listOf(Insight("names", 1, "x"), Insight("dups", 1, "y"))
        assertEquals(listOf("y"), InsightFilter.visible(all, mapOf("names" to NOW + DAY), NOW).map { it.text })
        assertEquals(2, InsightFilter.visible(all, mapOf("names" to NOW - 1), NOW).size)
    }

    @Test fun habitsFindBusyHours() {
        val utc = java.time.ZoneId.of("UTC")
        val at = { h: Int, d: Int -> NOW - d * DAY + h * 3_600_000L }
        val plays = (1..8).map { tv("S.S01E0$it.mkv", played = at(20, it)) } + (1..6).map { tv("T.S01E0$it.mkv", played = at(21, it)) } + tv("U.mkv", played = at(4, 1))
        val h = Habits.from(plays, { NameParser.parse(it.name) }, utc)
        assertTrue(h.isBusy(20) && h.isBusy(21) && !h.isBusy(4), h.busyHours().toString())
        assertEquals(Kind.SERIES, h.favoriteKind())
    }
}
