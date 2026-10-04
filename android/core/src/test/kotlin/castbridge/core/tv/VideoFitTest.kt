package castbridge.core.tv

import castbridge.core.tv.VideoFit.Mode
import castbridge.core.tv.VideoFit.Scale
import castbridge.core.tv.PictureQuality.Facts
import castbridge.core.ux.DisplayTexts
import castbridge.core.xfer.PlayerTuning
import kotlin.test.*

/** Affichage de la vidéo sur CastBridge-TV : table de décision pure. Rien ici ne prouve le rendu sur une vraie TV (P-54). */
class VideoFitTest {
    private fun d(w: Int, h: Int, pw: Int, ph: Int, mode: Mode, sn: Int = 1, sd: Int = 1, rot: Int = 0) = VideoFit.decide(w, h, sn, sd, rot, pw, ph, mode)

    @Test fun sixteenNineOnSixteenNineFillsExactlyWithNoBarsAndNoCrop() {
        for ((pw, ph) in listOf(1280 to 720, 1920 to 1080, 3840 to 2160)) {
            val p = d(1920, 1080, pw, ph, Mode.FIT)
            assertEquals(pw to ph, p.shownW to p.shownH); assertTrue(p.noBars); assertEquals(0, p.cropX + p.cropY); assertFalse(p.distorts)
            assertEquals(0 to 0, p.x to p.y); assertEquals(Scale.BEST_FIT, p.scale); assertNull(p.aspectRatio)
        }
    }

    @Test fun fourThreeOnSixteenNineShowsSideBarsNotStretched() {
        val p = d(640, 480, 1280, 720, Mode.FIT)
        assertEquals(960 to 720, p.shownW to p.shownH); assertEquals(320, p.barsX); assertEquals(0, p.barsY); assertEquals(160, p.x); assertFalse(p.distorts)
        val big = d(640, 480, 3840, 2160, Mode.FIT); assertEquals(2880 to 2160, big.shownW to big.shownH)
    }

    @Test fun ultraWideOnSixteenNineShowsThinBarsTopAndBottom() {
        val p = d(1920, 804, 1280, 720, Mode.FIT)               // 2.39:1
        assertEquals(1280, p.shownW); assertEquals(536, p.shownH); assertEquals(184, p.barsY); assertEquals(0, p.barsX)
        val ow = d(2560, 1080, 1280, 720, Mode.FIT)             // 21:9 file
        assertEquals(1280, ow.shownW); assertEquals(540, ow.shownH)
    }

    @Test fun aTwentyOneNineLikePanelFitsSixteenNineWithPillarBars() {
        val p = d(1920, 1080, 2560, 1080, Mode.FIT)
        assertEquals(1920 to 1080, p.shownW to p.shownH); assertEquals(640, p.barsX); assertEquals(320, p.x)
    }

    @Test fun squarePixelVideoOnSixteenNine() {
        val p = d(1000, 1000, 1920, 1080, Mode.FIT)
        assertEquals(1080 to 1080, p.shownW to p.shownH); assertEquals(840, p.barsX)
    }

    @Test fun anamorphicPixelAspectIsHonouredSoNothingLooksSqueezed() {
        val pal = d(720, 576, 1280, 720, Mode.FIT, 64, 45)      // DVD PAL 16:9: displayed 1024x576
        assertEquals(1024, pal.naturalW); assertEquals(576, pal.naturalH); assertEquals(1280 to 720, pal.shownW to pal.shownH); assertTrue(pal.noBars)
        val ntsc = d(720, 480, 1920, 1080, Mode.FIT, 32, 27)    // 853x480 = 16:9
        assertEquals(853, ntsc.naturalW); assertEquals(480, ntsc.naturalH); assertEquals(1920 to 1080, ntsc.shownW to ntsc.shownH); assertTrue(ntsc.noBars)
        val c = d(704, 576, 1280, 720, Mode.FIT, 16, 11)        // 1024x576 = 16:9
        assertEquals(1024, c.naturalW); assertEquals(1280 to 720, c.shownW to c.shownH); assertTrue(c.noBars)
        val flat = d(720, 480, 1280, 720, Mode.FIT)             // no SAR flag: 3:2, bars (what an unflagged file honestly is)
        assertEquals(1080 to 720, flat.shownW to flat.shownH); assertTrue(flat.barsX > 0)
    }

    @Test fun invalidSarFallsBackToSquarePixels() {
        assertEquals(d(640, 480, 1280, 720, Mode.FIT), d(640, 480, 1280, 720, Mode.FIT, 0, 0))
        assertEquals(d(640, 480, 1280, 720, Mode.FIT), d(640, 480, 1280, 720, Mode.FIT, -3, 2))
    }

    @Test fun rotationNinetySwapsTheDisplayedShape() {
        val p = d(1920, 1080, 1920, 1080, Mode.FIT, rot = 90)    // filmed upright: shown 1080x1920 portrait
        assertEquals(1080, p.naturalW); assertEquals(1920, p.naturalH); assertEquals(608 to 1080, p.shownW to p.shownH)
        assertEquals(p, d(1920, 1080, 1920, 1080, Mode.FIT, rot = 270))
        assertEquals(d(1920, 1080, 1280, 720, Mode.FIT), d(1920, 1080, 1280, 720, Mode.FIT, rot = 180))
        assertEquals(d(1920, 1080, 1280, 720, Mode.FIT), d(1920, 1080, 1280, 720, Mode.FIT, rot = -360))
    }

    @Test fun libvlcOrientationMapsToQuarterTurns() {
        assertEquals(listOf(0, 0, 180, 180, 90, 90, 90, 90, 0), (0..8).map { VideoFit.rotationOf(it) })
    }

    @Test fun portraitPhoneVideoIsPillarboxedInFitAndCroppedInFill() {
        val fit = d(1080, 1920, 1280, 720, Mode.FIT)
        assertEquals(405 to 720, fit.shownW to fit.shownH); assertEquals(875, fit.barsX)
        val fill = d(1080, 1920, 1280, 720, Mode.FILL)
        assertEquals(1280, fill.shownW); assertEquals(2276, fill.shownH); assertEquals(1556, fill.cropY); assertEquals(0, fill.cropX); assertEquals(-778, fill.y)
        assertFalse(fill.distorts)
    }

    @Test fun oddSizesThatAreSixteenNineWithinHalfAPercentFillExactly() {
        val p = d(853, 480, 1280, 720, Mode.FIT)
        assertEquals(1280 to 720, p.shownW to p.shownH); assertTrue(p.noBars)
        val q = d(853, 480, 1280, 720, Mode.FILL); assertEquals(0, q.cropX + q.cropY)
        val r = d(854, 480, 1920, 1080, Mode.FIT); assertEquals(1920 to 1080, r.shownW to r.shownH)
        val odd = d(1279, 719, 1280, 720, Mode.FIT); assertTrue(odd.shownW <= 1280 && odd.shownH <= 720)
    }

    @Test fun fillCropsTheOverflowEvenlyAndNeverStretches() {
        val p = d(640, 480, 1280, 720, Mode.FILL)
        assertEquals(1280 to 960, p.shownW to p.shownH); assertEquals(240, p.cropY); assertEquals(-120, p.y); assertEquals(0, p.x); assertFalse(p.distorts)
        assertEquals(Scale.FIT_SCREEN, p.scale)
        val wide = d(1920, 804, 1280, 720, Mode.FILL); assertEquals(720, wide.shownH); assertTrue(wide.shownW > 1280); assertEquals(wide.shownW - 1280, wide.cropX)
        val same = d(1920, 1080, 1920, 1080, Mode.FILL); assertEquals(0, same.cropX + same.cropY)
    }

    @Test fun stretchFillsThePanelAndSaysItDistorts() {
        val p = d(640, 480, 1280, 720, Mode.STRETCH)
        assertEquals(1280 to 720, p.shownW to p.shownH); assertTrue(p.distorts); assertEquals(Scale.FILL, p.scale)
        assertFalse(d(1920, 1080, 1280, 720, Mode.STRETCH).distorts, "same aspect: nothing is deformed")
    }

    @Test fun tinyVideoIsUpscaledInFitAndLeftUntouchedInNative() {
        val fit = d(320, 240, 1280, 720, Mode.FIT); assertEquals(960 to 720, fit.shownW to fit.shownH); assertTrue(fit.scaled)
        val nat = d(320, 240, 1280, 720, Mode.NATIVE)
        assertEquals(320 to 240, nat.shownW to nat.shownH); assertEquals(Scale.ORIGINAL, nat.scale); assertFalse(nat.scaled)
        assertEquals(480 to 240, nat.x to nat.y); assertEquals(0, nat.cropX + nat.cropY)
    }

    @Test fun hugeVideoInNativeIsOnlyScaledDownToFit() {
        val p = d(3840, 2160, 1280, 720, Mode.NATIVE)
        assertEquals(1280 to 720, p.shownW to p.shownH); assertEquals(Scale.BEST_FIT, p.scale); assertEquals(0, p.cropX + p.cropY)
        val o = d(1920, 1080, 1280, 720, Mode.NATIVE); assertEquals(1280 to 720, o.shownW to o.shownH)
        val exact = d(1280, 720, 1280, 720, Mode.NATIVE); assertEquals(Scale.ORIGINAL, exact.scale)
        val wide = d(2560, 1080, 1920, 1080, Mode.NATIVE); assertEquals(1920 to 810, wide.shownW to wide.shownH)
    }

    @Test fun nativeHonoursSarAndRotationInItsNativeSize() {
        val a = d(720, 480, 1920, 1080, Mode.NATIVE, 32, 27); assertEquals(853 to 480, a.shownW to a.shownH); assertEquals(Scale.ORIGINAL, a.scale)
        val r = d(640, 360, 1920, 1080, Mode.NATIVE, rot = 90); assertEquals(360 to 640, r.shownW to r.shownH)
    }

    @Test fun everyPanelAndEveryModeKeepsTheImageInsideTheScreenExceptFill() {
        for ((pw, ph) in listOf(1280 to 720, 1920 to 1080, 3840 to 2160, 2560 to 1080, 1366 to 768))
            for ((w, h) in listOf(1920 to 1080, 640 to 480, 1920 to 804, 320 to 240, 3840 to 2160, 1080 to 1920, 853 to 480)) {
                for (m in listOf(Mode.FIT, Mode.STRETCH, Mode.NATIVE)) {
                    val p = d(w, h, pw, ph, m)
                    assertTrue(p.shownW <= pw && p.shownH <= ph, "$m ${w}x$h on ${pw}x$ph")
                    assertEquals(0, p.cropX + p.cropY); assertEquals((pw - p.shownW) / 2, p.x); assertEquals((ph - p.shownH) / 2, p.y)
                }
                val f = d(w, h, pw, ph, Mode.FILL); assertTrue(f.shownW >= pw && f.shownH >= ph, "fill ${w}x$h on ${pw}x$ph")
            }
    }

    @Test fun unknownSizesDoNotInventAnything() {
        val p = d(0, 0, 1280, 720, Mode.FIT); assertEquals(Scale.BEST_FIT, p.scale); assertEquals(0, p.naturalW)
        assertEquals(Scale.FILL, d(0, 0, 1280, 720, Mode.STRETCH).scale)
        val none = d(1920, 1080, 0, 0, Mode.FIT); assertEquals(0, none.shownW)
    }

    @Test fun labelsAndTheInfoLine() {
        assertEquals(listOf("Remplir l'écran", "Ajusté à l'écran", "Étirer", "Natif"), Mode.values().map { DisplayTexts.label(it) })
        assertEquals("Affichage : Ajusté à l'écran 1920×1080 → 1280×720", VideoFit.infoLine(d(1920, 1080, 1280, 720, Mode.FIT), 1920, 1080, 1280, 720))
        assertEquals("Affichage : Natif 320×240 → 320×240", VideoFit.infoLine(d(320, 240, 1280, 720, Mode.NATIVE), 320, 240, 1280, 720))
        assertTrue(VideoFit.infoLine(d(640, 480, 1280, 720, Mode.STRETCH), 640, 480, 1280, 720).contains("déformée"))
    }

    // ---- défaut FILL : la vidéo remplit TOUTE la dalle, rognée au centre, jamais déformée ----

    private val panels = listOf(1280 to 720, 1920 to 1080, 3840 to 2160)
    // (largeur, hauteur, SAR num, SAR den, rotation)
    private val files = listOf(listOf(853, 480, 1, 1, 0), listOf(720, 576, 64, 45, 0), listOf(720, 576, 16, 15, 0), listOf(640, 480, 1, 1, 0),
        listOf(1920, 800, 1, 1, 0), listOf(2560, 1080, 1, 1, 0), listOf(1920, 1080, 1, 1, 90), listOf(1920, 1080, 1, 1, 0), listOf(1080, 1920, 1, 1, 0))

    @Test fun fillNeverLeavesBarsNeverDistortsAndCoversThePanelOnBothAxes() {
        for ((pw, ph) in panels) for (f in files) {
            val p = d(f[0], f[1], pw, ph, VideoFit.DEFAULT, f[2], f[3], f[4])
            val tag = "$f sur ${pw}x$ph"
            assertEquals(Mode.FILL, p.mode, tag); assertTrue(p.noBars, tag); assertFalse(p.distorts, tag)
            assertTrue(p.shownW >= pw && p.shownH >= ph, tag)
            val want = p.naturalW.toDouble() / p.naturalH
            assertTrue(Math.abs(p.shownW.toDouble() / p.shownH - want) <= 0.01 * want + 2.0 / p.shownH, tag)   // proportions vraies, à l'arrondi près
            assertTrue(Math.abs(p.x * 2 + p.shownW - pw) <= 1 && Math.abs(p.y * 2 + p.shownH - ph) <= 1, tag)   // rognage centré
            assertTrue(p.shownW == pw || p.shownH == ph, tag)                                                     // rognage minimal
        }
    }

    @Test fun fillDoesNotCropAVideoAlreadyInThePanelFormat() {
        for ((pw, ph) in panels) for (f in listOf(listOf(853, 480, 1, 1, 0), listOf(720, 576, 64, 45, 0), listOf(1280, 720, 1, 1, 0), listOf(3840, 2160, 1, 1, 0))) {
            val p = d(f[0], f[1], pw, ph, Mode.FILL, f[2], f[3])
            assertEquals(pw to ph, p.shownW to p.shownH, "$f sur ${pw}x$ph"); assertEquals(0, p.cropX + p.cropY)
        }
    }

    @Test fun fillOfFourThreeOnSixteenNineCropsOnlyTopAndBottom() {
        val p = d(640, 480, 1280, 720, Mode.FILL)
        assertEquals(1280 to 960, p.shownW to p.shownH); assertEquals(240, p.cropY); assertEquals(0, p.cropX); assertEquals(Scale.FIT_SCREEN, p.scale)
    }

    // ---- réglage jamais choisi : suit le défaut ; choisi : respecté ----

    @Test fun aSettingNeverChosenFollowsTheNewDefaultEvenIfAnOldVersionLeftFitBehind() {
        assertEquals(VideoFit.Resolved(Mode.FILL, VideoFit.Source.DEFAULT), VideoFit.resolve(null, null, false))
        assertEquals(VideoFit.Resolved(Mode.FILL, VideoFit.Source.DEFAULT), VideoFit.resolve(null, "fit", false), "« fit » sans marque de choix = ancien défaut écrit")
    }

    @Test fun anExplicitChoiceIsNeverOverwritten() {
        assertEquals(VideoFit.Resolved(Mode.FIT, VideoFit.Source.GLOBAL), VideoFit.resolve(null, "fit", true))
        assertEquals(VideoFit.Resolved(Mode.NATIVE, VideoFit.Source.GLOBAL), VideoFit.resolve(null, "native", true))
        assertEquals(VideoFit.Resolved(Mode.STRETCH, VideoFit.Source.GLOBAL), VideoFit.resolve(null, "stretch", false), "ancienne valeur non « fit » : c'était un vrai choix")
        assertEquals(VideoFit.Resolved(Mode.FIT, VideoFit.Source.FILE), VideoFit.resolve("fit", "native", true))
        assertEquals(VideoFit.Resolved(Mode.FILL, VideoFit.Source.DEFAULT), VideoFit.resolve("???", "???", true))
    }

    // ---- filet de sécurité : la ligne INFO ----

    @Test fun theDiagnosticLineSaysModeOriginSourcePanelAndPicture() {
        val p = d(720, 576, 1280, 720, Mode.FILL, 64, 45)
        assertEquals("Affichage : Remplir l'écran (par défaut) · source 720×576 SAR 64:45 · dalle 1280×720 · image 1280×720",
            VideoFit.diagnosticLine(VideoFit.Resolved(Mode.FILL, VideoFit.Source.DEFAULT), p, 720, 576, 64, 45, 1280, 720))
        val q = d(640, 480, 1920, 1080, Mode.FIT)
        assertEquals("Affichage : Ajusté à l'écran (réglage global) · source 640×480 SAR 1:1 · dalle 1920×1080 · image 1440×1080",
            VideoFit.diagnosticLine(VideoFit.Resolved(Mode.FIT, VideoFit.Source.GLOBAL), q, 640, 480, 0, 0, 1920, 1080))
        val none = VideoFit.diagnosticLine(VideoFit.Resolved(Mode.NATIVE, VideoFit.Source.FILE), null, 0, 0, 0, 0, 1280, 720)
        assertTrue(none.contains("réglage du fichier") && none.contains("non appliqué"), none)
    }

    // ---- choice and persistence ----

    @Test fun theDefaultIsFillAndTheFileOverridesTheGlobalChoice() {
        assertEquals(Mode.FILL, VideoFit.DEFAULT)
        assertEquals(Mode.FILL, VideoFit.effective(null, null)); assertEquals(Mode.FILL, VideoFit.effective("garbage", "?"))
        assertEquals(Mode.NATIVE, VideoFit.effective(null, "native")); assertEquals(Mode.FILL, VideoFit.effective("fill", "native"))
        assertEquals(Mode.NATIVE, VideoFit.effective("???", "native"), "an unreadable per-file value falls back to the global one")
        assertEquals(Mode.STRETCH, VideoFit.parse(" STRETCH "))
    }

    @Test fun perFileOverrideRoundTripsAndOldRecordsStayReadable() {
        val p = PlayerPrefs(audio = 2, rate = 1.25f, fit = "native")
        assertTrue(p.encode().contains("fm=native")); assertEquals(p, PlayerPrefs.decode(p.encode()))
        assertEquals("", PlayerPrefs().encode(), "default writes nothing")
        assertNull(PlayerPrefs.decode("a=1;fm=sideways").fit); assertNull(PlayerPrefs.decode("a=1;ar=16:9").fit)
        assertEquals("16:9", PlayerPrefs.decode("fm=fill;ar=16:9").aspect); assertEquals("fill", PlayerPrefs.decode("fm=fill;ar=16:9").fit)
    }

    // ---- picture quality ----

    private fun q(mode: Mode = Mode.FIT, interlaced: Boolean? = false, hw: Boolean? = true, sw: Boolean? = false, cores: Int = 4, copy: Boolean = false,
                  distress: Int = 0, hdr: Boolean? = null) = PictureQuality.decide(Facts(mode, "h264", 1920, 1080, interlaced, hw, sw, cores, copy, distress, 1280, 720, hdr))

    @Test fun progressiveContentGetsNoDeinterlacingAtAll() {
        val x = q(); assertEquals(0, x.deinterlace); assertNull(x.deinterlaceMode); assertTrue(x.options.none { it.contains("deinterlace-mode") })
    }

    @Test fun interlacedContentIsDeinterlacedCheaplyOnAWeakCpuAndBetterOnAStrongOne() {
        val weak = q(interlaced = true); assertEquals(1, weak.deinterlace); assertEquals("blend", weak.deinterlaceMode)
        val strong = q(interlaced = true, cores = 8); assertEquals("yadif", strong.deinterlaceMode)
        assertEquals("blend", q(interlaced = true, cores = 8, copy = true).deinterlaceMode, "a running copy makes even a strong CPU careful")
        assertTrue(weak.options.containsAll(listOf(":deinterlace=1", ":deinterlace-mode=blend")))
    }

    @Test fun unknownInterlacingIsLeftToLibvlcAutoExceptInNative() {
        assertEquals(-1, q(interlaced = null).deinterlace)
        assertEquals(0, q(mode = Mode.NATIVE, interlaced = null).deinterlace, "native = no post-processing unless the track says interlaced")
        assertEquals(1, q(mode = Mode.NATIVE, interlaced = true).deinterlace)
    }

    @Test fun betterScalingOnlyOnTheSoftwareScalerWithAStrongCpuAndNeverInNative() {
        assertEquals(PictureQuality.SWSCALE_LANCZOS, q(sw = true, cores = 8).swscaleMode)
        assertNull(q(sw = true, cores = 4).swscaleMode, "never on a weak CPU"); assertNull(q(sw = true, cores = 8, copy = true).swscaleMode)
        assertNull(q(sw = false, cores = 8).swscaleMode, "hardware surface: no software conversion to tune"); assertNull(q(sw = null, cores = 8).swscaleMode)
        assertNull(q(mode = Mode.NATIVE, sw = true, cores = 8).swscaleMode)
    }

    @Test fun distressAlwaysWinsOverQuality() {
        for (dist in 1..2) {
            val x = q(interlaced = true, sw = true, cores = 8, distress = dist)
            assertEquals(0, x.deinterlace); assertNull(x.swscaleMode); assertNull(x.deinterlaceMode)
            assertTrue(x.options.none { it.contains("swscale") || it.contains("deinterlace-mode") })
        }
    }

    @Test fun composedWithPlayerTuningQualityNeverFightsTheDistressKnobs() {
        val facts = PlayerTuning.Facts("h264", 1920, 1080, true, true, 2, 4)
        val tuning = PlayerTuning.decide(facts)
        val all = q(interlaced = true, sw = true, cores = 4, copy = true, distress = 2).options
        assertTrue(all.none { it.startsWith(":avcodec") }, "quality never touches the decoder knobs that PlayerTuning owns")
        assertEquals(0, PlayerTuning.decide(facts.copy(distress = 0, copyRunning = false)).skipLoopFilter, "at rest the loop filter stays at the default")
        assertTrue(tuning.dropLateFrames)
        assertEquals(listOf(":deinterlace=0"), all)
    }

    @Test fun nothingForcesALossyColourFormat() {
        for (m in Mode.values()) for (i in listOf(true, false, null))
            assertTrue(q(mode = m, interlaced = i, sw = true, cores = 8).options.none { it.contains("chroma", true) || it.contains("RV16", true) })
    }

    @Test fun honestNotesForHdrAndHardwareSurface() {
        assertTrue(q(hdr = true).notes.any { it.contains("tone mapping") })
        assertTrue(q(interlaced = true, hw = true).notes.any { it.contains("non vérifié") })
        assertTrue(q(interlaced = true, hw = false).notes.none { it.contains("non vérifié") })
        assertEquals(PictureQuality.Cpu.LOW, PictureQuality.cpuClass(4)); assertEquals(PictureQuality.Cpu.NORMAL, PictureQuality.cpuClass(8))
    }
}
