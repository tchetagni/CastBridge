package castbridge.core.tv

import kotlin.test.*

/** wtv-01 : options multimédia du lecteur, règles PURES. Le câblage Android (libVLC, vues) n'est vérifié que par compilation ; la TV réelle juge la performance. */
class ResumePolicyTest {
    private val dur = 100 * 60_000L
    @Test fun ignoresTheFirst30SecondsAndTheLast5Percent() {
        assertFalse(ResumePolicy.offer(0, dur)); assertFalse(ResumePolicy.offer(29_999, dur))
        assertTrue(ResumePolicy.offer(30_000, dur)); assertTrue(ResumePolicy.offer(55 * 60_000L + 4000, dur))
        assertTrue(ResumePolicy.offer(dur * 95 / 100, dur), "exactement 95 % : encore proposé")
        assertFalse(ResumePolicy.offer(dur * 95 / 100 + 1, dur)); assertFalse(ResumePolicy.offer(dur, dur), "à 100 % : jamais")
    }
    @Test fun unknownDurationOnlyUsesThe30SecondRule() { assertTrue(ResumePolicy.offer(60_000, 0)); assertFalse(ResumePolicy.offer(5_000, 0)) }
    @Test fun labelAndDefaultChoice() {
        assertEquals("Reprendre à 55:04", ResumePolicy.label(55 * 60_000L + 4000))
        assertEquals(ResumePolicy.Choice.RESUME, ResumePolicy.DEFAULT)
        assertEquals(3_304_000L, ResumePolicy.startFor(ResumePolicy.Choice.RESUME, 3_304_000, dur))
        assertEquals(0L, ResumePolicy.startFor(ResumePolicy.Choice.RESTART, 3_304_000, dur))
        assertEquals(0L, ResumePolicy.startFor(ResumePolicy.Choice.RESUME, dur, dur), "une position qu'on n'aurait pas proposée repart de 0")
    }
}

class SleepTimerTest {
    @Test fun choicesAre15_30_60_90MinutesAndEndOfVideo() {
        assertEquals(listOf("15 min", "30 min", "60 min", "90 min", "Fin de la vidéo"), SleepTimer.LABELS)
        assertNull(SleepTimer.choice(5, 0)); assertNull(SleepTimer.choice(-1, 0)); assertNull(SleepTimer.minutes(45, 0))
    }
    @Test fun countsDownThenFadesThenExpires() {
        val t = SleepTimer.minutes(15, 1_000)!!
        assertEquals(15 * 60_000L, t.remainingMs(1_000)); assertEquals(SleepTimer.Phase.RUNNING, t.phase(1_000))
        assertEquals(SleepTimer.Phase.RUNNING, t.phase(1_000 + 15 * 60_000L - SleepTimer.FADE_MS - 1))
        assertEquals(SleepTimer.Phase.FADING, t.phase(1_000 + 15 * 60_000L - SleepTimer.FADE_MS))
        assertEquals(SleepTimer.Phase.EXPIRED, t.phase(1_000 + 15 * 60_000L)); assertEquals(0, t.remainingMs(1_000 + 20 * 60_000L))
    }
    @Test fun volumeFadesLinearlyToZero() {
        val t = SleepTimer.minutes(15, 0)!!; val end = 15 * 60_000L
        assertEquals(1f, t.volumeFactor(0)); assertEquals(1f, t.volumeFactor(end - SleepTimer.FADE_MS))
        assertEquals(0.5f, t.volumeFactor(end - SleepTimer.FADE_MS / 2), 0.001f); assertEquals(0f, t.volumeFactor(end))
    }
    @Test fun endOfVideoFollowsTheRemainingPlayingTime() {
        val t = SleepTimer.choice(4, 0)!!
        assertTrue(t.endOfVideo); assertEquals(60_000, t.remainingMs(5_000_000, 40_000, 100_000))
        assertEquals(SleepTimer.Phase.RUNNING, t.phase(0, 0, 0), "durée inconnue : on ne s'arrête pas")
        assertEquals(SleepTimer.Phase.EXPIRED, t.phase(0, 100_000, 100_000))
    }
    @Test fun countdownIsDiscreet() {
        val t = SleepTimer.minutes(15, 0)!!
        assertEquals("Arrêt dans 15:00", t.countdown(0)); assertEquals("Arrêt dans 15:00", t.countdown(1)); assertEquals("Arrêt dans 14:59", t.countdown(1_001))
        assertEquals("Arrêt dans 0:00", t.countdown(15 * 60_000L))
    }
}

class LoopAbTest {
    @Test fun pressSetsAThenBThenClears() {
        val a = LoopAB().press(10_000); assertEquals(10_000L, a.aMs); assertTrue(a.waitingForB); assertFalse(a.active)
        val ab = a.press(20_000); assertTrue(ab.active); assertEquals(10_000L, ab.aMs); assertEquals(20_000L, ab.bMs)
        assertEquals(LoopAB(), ab.press(30_000))
    }
    @Test fun bBeforeASwapsThem() {
        val ab = LoopAB().press(50_000).press(20_000)
        assertEquals(20_000L, ab.aMs, "A>B : les deux sont échangés"); assertEquals(50_000L, ab.bMs)
        assertEquals(20_000L, ab.jumpBack(50_000))
    }
    @Test fun emptyLoopIsRefused() {
        val a = LoopAB().press(10_000)
        assertEquals(a, a.press(10_000)); assertEquals(a, a.press(10_500), "moins d'une seconde")
    }
    @Test fun jumpsBackOnlyWhenBIsReached() {
        val ab = LoopAB().press(10_000).press(20_000)
        assertNull(ab.jumpBack(19_999)); assertEquals(10_000L, ab.jumpBack(20_000)); assertEquals(10_000L, ab.jumpBack(25_000))
        assertNull(LoopAB().press(10_000).jumpBack(99_000), "B pas posé : pas de boucle")
    }
    @Test fun labelsAreFrench() {
        assertEquals("Boucle A-B : non", LoopAB().label()); assertTrue(LoopAB().press(65_000).label().contains("1:05"))
        assertEquals("Boucle A-B : 0:10 - 0:20", LoopAB().press(10_000).press(20_000).label())
    }
}

class BookmarksTest {
    @Test fun keepsAtMost20() {
        var b = Bookmarks(); for (i in 1..25) b = b.add(i * 10_000L)
        assertEquals(20, b.marksMs.size); assertTrue(b.full); assertEquals(200_000L, b.marksMs.last())
        assertEquals(b, b.add(999_000), "plein : inchangé")
    }
    @Test fun sortedWithoutNearDuplicates() {
        val b = Bookmarks().add(60_000).add(10_000).add(60_500).add(30_000)
        assertEquals(listOf(10_000L, 30_000L, 60_000L), b.marksMs)
    }
    @Test fun nextPreviousAndRemove() {
        val b = Bookmarks().add(10_000).add(30_000).add(60_000)
        assertEquals(30_000L, b.next(10_000)); assertNull(b.next(60_000)); assertEquals(30_000L, b.previous(60_000)); assertNull(b.previous(10_000))
        assertEquals(listOf(10_000L, 60_000L), b.remove(1).marksMs); assertEquals(b, b.remove(9))
    }
    @Test fun roundTripAndTolerantDecode() {
        val b = Bookmarks().add(10_000).add(75_000)
        assertEquals("10,75", b.encode()); assertEquals(b, Bookmarks.decode("10,75"))
        assertEquals(listOf(5_000L), Bookmarks.decode("x,,-3,5,5").marksMs)
        assertEquals(20, Bookmarks.decode((1..40).joinToString(",")).marksMs.size)
    }
}

class PictureTuningTest {
    @Test fun everythingIsOffByDefault() {
        val t = PictureTuning(); assertTrue(t.isDefault); assertFalse(t.filtersActive); assertEquals("", t.encode())
        assertEquals(1f, t.vlcBrightness); assertEquals(1f, t.vlcGamma)
    }
    @Test fun valuesStayInTheirBounds() {
        val t = PictureTuning()
        assertEquals(150, PictureTuning.set(t, PictureTuning.Field.BRIGHTNESS, 999).brightness)
        assertEquals(50, PictureTuning.set(t, PictureTuning.Field.BRIGHTNESS, -5).brightness)
        assertEquals(0, PictureTuning.set(t, PictureTuning.Field.SATURATION, -1).saturation)
        assertEquals(200, PictureTuning.set(t, PictureTuning.Field.SATURATION, 500).saturation)
        assertEquals(300, PictureTuning.set(t, PictureTuning.Field.ZOOM, 900).zoom); assertEquals(100, PictureTuning.set(t, PictureTuning.Field.ZOOM, 10).zoom)
        assertEquals(300, PictureTuning.set(t, PictureTuning.Field.GAMMA, 301).gamma)
    }
    @Test fun panNeedsAZoomAndIsBounded() {
        val z = PictureTuning.set(PictureTuning(), PictureTuning.Field.ZOOM, 200)
        assertEquals(100, PictureTuning.set(z, PictureTuning.Field.PAN_X, 500).panX); assertEquals(-100, PictureTuning.set(z, PictureTuning.Field.PAN_Y, -500).panY)
        assertEquals(0, PictureTuning.set(PictureTuning(), PictureTuning.Field.PAN_X, 50).panX, "sans zoom : sans effet")
        assertEquals(0, PictureTuning.set(PictureTuning.set(z, PictureTuning.Field.PAN_X, 50), PictureTuning.Field.ZOOM, 100).panX, "revenir à 1x recentre")
    }
    @Test fun rotationIsAMultipleOf90() {
        assertEquals(90, PictureTuning.withRotation(PictureTuning(), 90).rotation); assertEquals(270, PictureTuning.withRotation(PictureTuning(), -90).rotation)
        assertEquals(0, PictureTuning.withRotation(PictureTuning(), 360).rotation); assertEquals(90, PictureTuning.withRotation(PictureTuning(), 450).rotation)
    }
    @Test fun filtersActiveIgnoresZoomAndRotationButNotDeinterlace() {
        assertFalse(PictureTuning(zoom = 200, rotation = 90).filtersActive); assertTrue(PictureTuning(deinterlace = true).filtersActive); assertTrue(PictureTuning(gamma = 110).filtersActive)
        val off = PictureTuning(brightness = 120, deinterlace = true, zoom = 200, rotation = 90).withoutFilters()
        assertFalse(off.filtersActive); assertEquals(200, off.zoom); assertEquals(90, off.rotation)
    }
    @Test fun roundTripAndHostileDecode() {
        val t = PictureTuning(brightness = 120, contrast = 90, saturation = 0, gamma = 150, zoom = 200, panX = -20, panY = 30, deinterlace = true, rotation = 90)
        assertEquals(t, PictureTuning.decode(t.encode()))
        assertEquals(PictureTuning(brightness = 150), PictureTuning.decode("b99999,zz,,q5,c"), "bornes et jetons inconnus")
    }
}

class PictureTransformTest {
    @Test fun noOptionAtRest() { assertTrue(PictureTuning().mediaOptions().isEmpty()); assertTrue(PictureTuning(zoom = 200, rotation = 90).mediaOptions().isEmpty(), "zoom et rotation : aucune option libVLC") }
    @Test fun adjustOptionsOnlyForWhatChanged() {
        val o = PictureTuning(brightness = 120, gamma = 90).mediaOptions()
        assertEquals(listOf(":video-filter=adjust", ":brightness=1.2", ":gamma=0.9"), o)
        assertEquals(listOf(":deinterlace=1", ":deinterlace-mode=auto"), PictureTuning(deinterlace = true).mediaOptions())
    }
    @Test fun neutralTransformAtRest() { val t = PictureTuning().viewTransform(1280, 720); assertEquals(PictureTuning.ViewTransform(1f, 0f, 0f, 0f), t) }
    @Test fun zoomAndPanMoveAtMostToTheEdgeOfTheEnlargedPicture() {
        val z = PictureTuning(zoom = 300, panX = 100, panY = -100).viewTransform(1280, 720)
        assertEquals(3f, z.scale); assertEquals(1280f, z.translateX, 0.01f); assertEquals(-720f, z.translateY, 0.01f)
        assertEquals(640f, PictureTuning(zoom = 200, panX = 100).viewTransform(1280, 720).translateX, 0.01f)
    }
    @Test fun rotatedPictureIsShrunkToFitThePanel() {
        val r = PictureTuning(rotation = 90).viewTransform(1280, 720)
        assertEquals(90f, r.rotation); assertEquals(720f / 1280f, r.scale, 0.001f); assertEquals(1f, PictureTuning(rotation = 180).viewTransform(1280, 720).scale)
    }
}

class PerformanceGuardTest {
    @Test fun trips_once_when_too_many_frames_are_lost_while_a_filter_runs() {
        val g = PerformanceGuard(minFrames = 100, maxLostPercent = 10); g.arm(1000, 50)
        assertFalse(g.check(1050, 55, true), "pas assez d'images pour juger")
        assertFalse(g.check(1180, 65, true), "8 % de perte : toléré")
        assertTrue(g.check(1200, 100, true), "25 % de perte")
        assertTrue(g.tripped); assertFalse(g.check(1400, 300, true), "une seule fois")
    }
    @Test fun neverTripsWithoutAFilterAndRearms() {
        val g = PerformanceGuard(100, 10); g.arm(0, 0)
        assertFalse(g.check(100, 100, false), "aucun filtre actif : rien à couper")
        assertTrue(g.check(100, 100, true)); g.arm(100, 100); assertFalse(g.tripped); assertFalse(g.check(150, 100, true))
    }
    @Test fun countersBeforeArmingDoNotCount() {
        val g = PerformanceGuard(100, 10); g.arm(5000, 4000)
        assertFalse(g.check(5200, 4010, true), "les pertes d'avant ne comptent pas")
    }
}

class AudioTuningTest {
    @Test fun defaultsChangeNothing() { val a = AudioTuning(); assertTrue(a.isDefault); assertEquals(100, a.volume()); assertTrue(a.filterOptions().isEmpty()); assertNull(a.warning); assertEquals("", a.encode()) }
    @Test fun gainIsBetween100And200AndWarnsAbove100() {
        assertEquals(200, AudioTuning.clampGain(999)); assertEquals(100, AudioTuning.clampGain(40))
        assertEquals(AudioTuning.WARNING, AudioTuning(gainPercent = 150).warning); assertNull(AudioTuning(gainPercent = 100).warning)
        assertEquals(200, AudioTuning(gainPercent = 200).volume())
    }
    @Test fun fadeScalesTheVolume() { assertEquals(75, AudioTuning(gainPercent = 150).volume(0.5f)); assertEquals(0, AudioTuning(gainPercent = 150).volume(0f)); assertEquals(150, AudioTuning(gainPercent = 150).volume(5f)) }
    @Test fun nightModeUsesTheCompressor() { assertTrue(AudioTuning(night = true).filterOptions().contains(":audio-filter=compressor")) }
    @Test fun roundTrip() {
        val a = AudioTuning(night = true, gainPercent = 130, keepPitch = true)
        assertEquals(a, AudioTuning.decode(a.encode())); assertEquals(AudioTuning(gainPercent = 200), AudioTuning.decode("g900,x,n"))
    }
}

class SubtitleStyleTest {
    @Test fun defaultStyleAddsNoLibVlcOption() { assertTrue(SubtitleStyle().options().isEmpty()); assertTrue(SubtitleStyle().isDefault); assertEquals("", SubtitleStyle().encode()) }
    @Test fun optionsFollowTheChoices() {
        val s = SubtitleStyle(color = SubtitleStyle.Color.YELLOW, outline = SubtitleStyle.Outline.THICK, bottomPercent = 10, encoding = "Windows-1252", font = "serif")
        val o = s.options(720)
        assertTrue("--freetype-color=16776960" in o); assertTrue("--freetype-outline-thickness=6" in o); assertTrue("--sub-margin=72" in o)
        assertTrue("--subsdec-encoding=Windows-1252" in o); assertTrue("--freetype-font=serif" in o)
    }
    @Test fun positionIsBoundedTo40Percent() {
        assertEquals(40, SubtitleStyle.clampBottom(95)); assertEquals(0, SubtitleStyle.clampBottom(-9))
        assertEquals(40, SubtitleStyle.decode("p500").bottomPercent); assertTrue("--sub-margin=288" in SubtitleStyle(bottomPercent = 200).options(720))
    }
    @Test fun unknownEncodingOrFontIsIgnored() { assertEquals(SubtitleStyle(), SubtitleStyle.decode("eKlingon,fComic,cx,oz")) }
    @Test fun roundTripAndPreview() {
        val s = SubtitleStyle(SubtitleStyle.Color.CYAN, SubtitleStyle.Outline.SHADOW, 15, "UTF-8", "monospace")
        assertEquals(s, SubtitleStyle.decode(s.encode())); assertTrue(SubtitleStyle.PREVIEW.contains("é"))
        assertEquals("auto", SubtitleStyle.ENCODINGS.first().key)
        assertTrue(SubtitleStyle.ENCODINGS.map { it.key }.containsAll(listOf("UTF-8", "ISO-8859-1", "Windows-1252")))
    }
}

class NextEpisodeTest {
    private val all = listOf("/v/Serie/Ep1.mkv", "/v/Serie/Ep10.mkv", "/v/Serie/Ep2.mkv", "/v/Serie/Ep2.fr.srt", "/v/Autre/Ep3.mkv", "/v/Serie/notes.txt")
    @Test fun naturalOrderInsideTheSameFolder() {
        assertEquals("/v/Serie/Ep2.mkv", NextEpisode.next("/v/Serie/Ep1.mkv", all)); assertEquals("/v/Serie/Ep10.mkv", NextEpisode.next("/v/Serie/Ep2.mkv", all))
    }
    @Test fun neverLeavesTheFolder() {
        assertNull(NextEpisode.next("/v/Serie/Ep10.mkv", all), "le dernier du dossier : rien, même si /v/Autre a un fichier plus grand")
        assertEquals("/v/Autre/Ep3.mkv", NextEpisode.next("/v/Autre/Ep0.mkv", all), "fichier courant absent de la liste : le premier plus grand du même dossier")
    }
    @Test fun skipsSubtitlesAndOtherFiles() { assertFalse(NextEpisode.isVideo("a.srt")); assertTrue(NextEpisode.isVideo("A.MKV")); assertEquals("Ep2.mkv", NextEpisode.next("Ep1.mkv", listOf("Ep1.mkv", "Ep1.srt", "Ep2.mkv"))) }
    @Test fun naturalCompare() { assertTrue(NextEpisode.naturalCompare("ep2", "ep10") < 0); assertTrue(NextEpisode.naturalCompare("EP2", "ep02") == 0); assertTrue(NextEpisode.naturalCompare("a", "b") < 0) }
    @Test fun countdownIs8SecondsAndCancellable() {
        val c = AutoNextCountdown(1000)
        assertEquals(8, c.remainingS(1000)); assertEquals(5, c.remainingS(4000)); assertFalse(c.due(8999)); assertTrue(c.due(9000)); assertEquals(0, c.remainingS(99_000))
        c.cancel(); assertFalse(c.due(99_000)); assertEquals(0, c.remainingS(1000))
        assertTrue(AutoNextCountdown(0).label(0, "Ep2").contains("8 s"))
    }
}

class PlayerSeekTest {
    @Test fun stepsAndLongPress() {
        assertEquals(10_000L, PlayerSeek.amountMs(10, false, false)); assertEquals(30_000L, PlayerSeek.amountMs(10, true, false)); assertEquals(30_000L, PlayerSeek.amountMs(10, false, true))
        assertEquals(20_000L, PlayerSeek.amountMs(20, false, false)); assertEquals(90_000L, PlayerSeek.amountMs(30, true, false)); assertEquals(10_000L, PlayerSeek.step(7) * 1000L)
    }
    @Test fun followUpCompletesTheSmallJumpToTheBigOne() { assertEquals(20_000L, PlayerSeek.followUpMs(10)); assertEquals(PlayerSeek.bigMs(20), PlayerSeek.smallMs(20) + PlayerSeek.followUpMs(20)) }
    @Test fun targetIsClamped() {
        assertEquals(0L, PlayerSeek.target(5_000, -10_000, 100_000)); assertEquals(99_000L, PlayerSeek.target(95_000, 30_000, 100_000)); assertEquals(500_000L, PlayerSeek.target(470_000, 30_000, 0))
    }
    @Test fun doublePress() {
        val d = DoublePress(400)
        assertFalse(d.register(1, 1000)); assertTrue(d.register(1, 1300)); assertFalse(d.register(1, 1500), "le troisième appui recommence")
        assertFalse(d.register(2, 1600)); assertFalse(d.register(1, 1700), "autre touche"); assertFalse(d.register(1, 2200), "trop lent")
    }
}

class PlayerPrefsPerFileTest {
    @Test fun keysDoNotCrossBetweenFiles() {
        assertNotEquals(PlayerPrefs.fileKey("a.mkv", 100), PlayerPrefs.fileKey("b.mkv", 100))
        assertNotEquals(PlayerPrefs.fileKey("a.mkv", 100), PlayerPrefs.fileKey("a.mkv", 101), "même nom, autre taille : autre fichier")
        val store = HashMap<String, String>()
        store[PlayerPrefs.fileKey("a.mkv", 100)] = PlayerPrefs(picture = PictureTuning(brightness = 130), bookmarks = Bookmarks().add(10_000)).encode()
        val b = PlayerPrefs.decode(store[PlayerPrefs.fileKey("b.mkv", 100)])
        assertNull(b.picture); assertTrue(b.bookmarks.marksMs.isEmpty())
        assertEquals(130, PlayerPrefs.decode(store[PlayerPrefs.fileKey("a.mkv", 100)]).picture?.brightness)
    }
    @Test fun newKeysRoundTripAndOldDataStillDecodes() {
        val p = PlayerPrefs(audio = 2, rate = 1.25f, fit = "native", picture = PictureTuning(contrast = 120), audioTuning = AudioTuning(night = true), subStyle = SubtitleStyle(font = "serif"), bookmarks = Bookmarks().add(5_000))
        assertEquals(p, PlayerPrefs.decode(p.encode()))
        val old = PlayerPrefs.decode("a=2;s=-1;sd=150;ss=125;r=1.25;ar=16:9;fm=fill")
        assertEquals(2, old.audio); assertEquals(125, old.subScale); assertNull(old.picture); assertNull(old.audioTuning); assertNull(old.subStyle)
        assertEquals(PlayerPrefs(), PlayerPrefs.decode("zz=1;pc;;bm=")); assertEquals("", PlayerPrefs().encode(), "rien d'écrit tant que rien n'est choisi")
    }
    @Test fun unchosenGroupsFollowTheDefaults() {
        val defaults = PlayerPrefs(picture = PictureTuning(brightness = 120), audioTuning = AudioTuning(night = true))
        val file = PlayerPrefs(picture = PictureTuning(brightness = 90))
        val r = file.resolved(defaults)
        assertEquals(90, r.picture?.brightness, "le réglage du fichier prime"); assertTrue(r.audioTuning!!.night, "non choisi : celui par défaut"); assertNull(r.subStyle)
    }
}

class PlayerCommandParseTest {
    private val cur = PlayerTracks(subStyle = SubtitleStyle(color = SubtitleStyle.Color.YELLOW))
    private fun c(what: String, vararg kv: Pair<String, String>) = PlayerParams.command(what, mapOf(*kv), cur)
    @Test fun newRoutesParseAndBound() {
        assertEquals(PlayerCommand.Sleep(1), c("sleep", "choice" to "1")); assertEquals(PlayerCommand.Sleep(-1), c("sleep", "choice" to "-1")); assertNull(c("sleep", "choice" to "9"))
        assertEquals(PlayerCommand.LoopAb(false), c("loop", "action" to "press")); assertEquals(PlayerCommand.LoopAb(true), c("loop", "action" to "clear")); assertNull(c("loop", "action" to "x"))
        assertEquals(PlayerCommand.BookmarkAdd, c("mark", "action" to "add")); assertEquals(PlayerCommand.BookmarkGo(3), c("mark", "action" to "go", "index" to "3")); assertNull(c("mark", "action" to "go", "index" to "20"))
        assertEquals(PlayerCommand.PictureField(PictureTuning.Field.BRIGHTNESS, 150), c("picture", "field" to "b", "value" to "9999"))
        assertEquals(PlayerCommand.PictureReset, c("picture", "reset" to "1")); assertEquals(PlayerCommand.PictureDeinterlace(true), c("picture", "deinterlace" to "on"))
        assertEquals(PlayerCommand.PictureRotation(90), c("picture", "rotation" to "450")); assertNull(c("picture", "field" to "zz", "value" to "5"))
        assertEquals(PlayerCommand.AudioGain(200), c("gain", "value" to "999")); assertEquals(PlayerCommand.AudioNight(true), c("night", "value" to "1")); assertNull(c("night", "value" to "peut-etre"))
        assertEquals(PlayerCommand.SkipStep(20), c("skipstep", "value" to "20")); assertNull(c("skipstep", "value" to "15")); assertEquals(PlayerCommand.AutoNext(false), c("autonext", "value" to "0"))
    }
    @Test fun subtitleStyleMergesOnlyTheGivenFields() {
        val r = c("substyle", "bottom" to "99") as PlayerCommand.SubStyleSet
        assertEquals(SubtitleStyle.Color.YELLOW, r.style.color, "le reste ne change pas"); assertEquals(40, r.style.bottomPercent)
        assertNull(c("substyle"))
    }
    @Test fun oldPhonesAreNotBrokenByNewJsonKeys() {
        val j = PlayerTracks(sleep = "Arrêt dans 14:59", bookmarks = listOf(1000, 2000), picture = PictureTuning(brightness = 120)).toJson()
        assertTrue(j.startsWith("""{"audio":[]""")); assertTrue(j.contains(""""audioDelayMs":0""")); assertTrue(j.contains(""""aspects":["auto"""))
        assertTrue(j.contains(""""bookmarks":[1000,2000]""")); assertTrue(j.contains(""""sleep":"Arrêt dans 14:59"""")); assertTrue(j.contains(""""b":120""")); assertTrue(j.endsWith(""""skipStep":10}"""))
        assertTrue(PlayerTracks().toJson().contains(""""sleep":null"""))
    }
}

class PlayerSettingsTest {
    private val s0 = PlayerSettings.State()
    private fun row(s: PlayerSettings.State, id: String) = PlayerSettings.rows(s).first { it.id == id }
    @Test fun sectionsInOrderAndEveryRowIsLabelledInFrench() {
        val rows = PlayerSettings.rows(s0)
        assertEquals(listOf("Lecture", "Image", "Son", "Sous-titres", "Affichage"), PlayerSettings.sections(rows).map { it.label })
        assertTrue(rows.all { it.label.isNotBlank() && it.text().isNotBlank() }); assertEquals(rows.size, rows.map { it.id }.distinct().size)
    }
    @Test fun everythingStartsAtDefaultAndImageFiltersAreOff() {
        val rows = PlayerSettings.rows(s0).filter { it.kind == PlayerSettings.Kind.VALUE }
        assertTrue(rows.all { it.isDefault }, rows.filter { !it.isDefault }.joinToString { it.id }); assertTrue(rows.all { it.text().endsWith("(par défaut)") })
    }
    @Test fun leftRightChangesAndMarksNonDefault() {
        val s = PlayerSettings.change(s0, "pic_b", 1); assertEquals(110, s.picture.brightness); assertFalse(row(s, "pic_b").isDefault); assertEquals("Luminosité : 110 %", row(s, "pic_b").text())
        assertEquals(90, PlayerSettings.change(s0, "pic_b", -1).picture.brightness)
        assertEquals(150, (1..30).fold(s0) { a, _ -> PlayerSettings.change(a, "pic_b", 1) }.picture.brightness, "s'arrête à la borne")
        assertEquals(200, (1..30).fold(s0) { a, _ -> PlayerSettings.change(a, "au_g", 1) }.audio.gainPercent); assertEquals(100, PlayerSettings.change(s0, "au_g", -1).audio.gainPercent)
    }
    @Test fun choicesWrapAround() {
        assertEquals(20, PlayerSettings.change(s0, "skip", 1).skipStepSec); assertEquals(30, PlayerSettings.change(s0, "skip", -1).skipStepSec)
        assertEquals(270, PlayerSettings.change(s0, "pic_r", -1).picture.rotation); assertEquals(SubtitleStyle.Color.ORANGE, PlayerSettings.change(s0, "st_c", -1).sub.color)
        assertTrue(PlayerSettings.change(s0, "pic_d", 1).picture.deinterlace); assertFalse(PlayerSettings.change(s0, "autonext", 1).autoNext)
        assertEquals("UTF-8", PlayerSettings.change(s0, "st_e", 1).sub.encoding); assertEquals(5, PlayerSettings.change(s0, "st_p", 1).sub.bottomPercent)
    }
    @Test fun actionRowsDoNotChangeValuesAndResetsWork() {
        assertEquals(s0, PlayerSettings.change(s0, "sleep", 1)); assertEquals(s0, PlayerSettings.change(s0, "nope", 1))
        val s = PlayerSettings.change(PlayerSettings.change(s0, "pic_b", 1), "pic_z", 1)
        assertEquals(PictureTuning(), PlayerSettings.reset(s, "pic_reset").picture); assertNull(PlayerSettings.reset(s, "pic_default").file.picture)
    }
    @Test fun perFileAndDefaultsDoNotMix() {
        val withDefault = PlayerSettings.saveDefault(PlayerSettings.change(s0, "au_n", 1), "au_save")
        assertTrue(withDefault.defaults.audioTuning!!.night)
        val other = PlayerSettings.State(file = PlayerPrefs(), defaults = withDefault.defaults)
        assertTrue(other.audio.night, "un autre fichier suit le défaut enregistré"); assertNull(other.file.audioTuning)
        assertNull(PlayerSettings.change(s0, "au_n", 1).defaults.audioTuning, "changer un fichier ne touche pas au défaut")
    }
    @Test fun fitRowFollowsDefaultThenCyclesThroughTheModes() {
        assertEquals(null, s0.file.fit); assertTrue(row(s0, "fit").value.startsWith("comme le réglage par défaut"))
        val s = PlayerSettings.change(s0, "fit", 1); assertEquals(VideoFit.Mode.values().first().key, s.file.fit)
        assertEquals(null, PlayerSettings.change(s, "fit", -1).file.fit)
    }
    @Test fun navigationWraps() { assertEquals(0, PlayerSettings.move(4, 1, 5)); assertEquals(4, PlayerSettings.move(0, -1, 5)); assertEquals(2, PlayerSettings.move(1, 1, 5)); assertEquals(0, PlayerSettings.move(3, 1, 0)) }
    @Test fun changeMapsToThePlayerCommand() {
        val s = PlayerSettings.change(s0, "pic_b", 1); assertEquals(PlayerCommand.PictureField(PictureTuning.Field.BRIGHTNESS, 110), PlayerSettings.commandFor("pic_b", s))
        assertEquals(PlayerCommand.AudioGain(110), PlayerSettings.commandFor("au_g", PlayerSettings.change(s0, "au_g", 1)))
        assertEquals(PlayerCommand.SubStyleSet(SubtitleStyle(font = "sans-serif")), PlayerSettings.commandFor("st_f", PlayerSettings.change(s0, "st_f", 1)))
        assertEquals(PlayerCommand.SkipStep(20), PlayerSettings.commandFor("skip", PlayerSettings.change(s0, "skip", 1))); assertNull(PlayerSettings.commandFor("sleep", s0))
    }
    @Test fun nextPreviousVideoRowsExist() { assertTrue(PlayerSettings.rows(s0).map { it.id }.containsAll(listOf("next", "prev", "sleep", "loop", "mark_add", "mark_go"))) }
    @Test fun gainRowShowsTheWarning() { assertTrue(row(PlayerSettings.change(s0, "au_g", 1), "au_g").value.contains("saturer")) }
}
