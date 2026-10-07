package castbridge.core.tv

import castbridge.core.tv.OpenTvPlan.Way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * « Ouvrir CastBridge-TV depuis le téléphone » (docs/REMOTE.md) : la règle PURE qui choisit comment faire passer CastBridge-TV devant une autre application
 * (YouTube…), l'ordre d'essai, la réponse de la TV et la ligne de MENU proposée une seule fois. Aucun Android ici.
 */
class OpenTvPlanTest {
    private fun st(front: Boolean = false, sdk: Int = 34, overlay: Boolean = false, screen: Boolean = true, fullScreen: Boolean = true,
                   notifications: Boolean = true, accessibility: Boolean = false) =
        OpenTvPlan.State(front, sdk, overlay, screen, fullScreen, notifications, accessibility)

    // ---------------------------------------------------------------- l'ordre d'essai

    @Test fun alreadyInFrontNeedsNothing() {
        val p = OpenTvPlan.plan(st(front = true, overlay = true, accessibility = true))
        assertTrue(p.already)
        assertEquals(emptyList(), p.ways)
        assertFalse(p.needsOverlay)
    }

    @Test fun overlayGrantedStartsDirectlyThenNotificationThenAccessibility() {
        val p = OpenTvPlan.plan(st(overlay = true, accessibility = true))
        assertFalse(p.already)
        assertEquals(listOf(Way.DIRECT, Way.FULLSCREEN, Way.ACCESSIBILITY), p.ways)
        assertFalse(p.needsOverlay, "granted: nothing more to ask")
    }

    @Test fun beforeAndroid10NothingRestrictsABackgroundStart() {
        for (sdk in listOf(21, 26, 28)) {
            val p = OpenTvPlan.plan(st(sdk = sdk, overlay = false))
            assertEquals(Way.DIRECT, p.ways.first(), "sdk $sdk")
            assertFalse(p.needsOverlay, "sdk $sdk: the permission would change nothing")
        }
    }

    @Test fun onAndroid10AndUpADirectStartWithoutTheOverlayIsNotTried() {
        for (sdk in listOf(29, 30, 33, 34, 35)) {
            val p = OpenTvPlan.plan(st(sdk = sdk, overlay = false, accessibility = true))
            assertFalse(Way.DIRECT in p.ways, "sdk $sdk")
            assertEquals(listOf(Way.FULLSCREEN, Way.ACCESSIBILITY), p.ways, "sdk $sdk")
        }
    }

    @Test fun theNotificationNeedsNotificationsToBeOn() {
        assertFalse(Way.FULLSCREEN in OpenTvPlan.plan(st(notifications = false, accessibility = true)).ways)
        assertEquals(listOf(Way.ACCESSIBILITY), OpenTvPlan.plan(st(notifications = false, accessibility = true)).ways)
    }

    @Test fun theFullScreenFlagFollowsAndroidsPermissionButTheNotificationIsStillPosted() {
        val p = OpenTvPlan.plan(st(fullScreen = false))
        assertEquals(listOf(Way.FULLSCREEN), p.ways, "a plain notification can still be opened with the remote")
        assertFalse(p.fullScreenIntent)
        assertTrue(OpenTvPlan.plan(st(fullScreen = true)).fullScreenIntent)
    }

    @Test fun accessibilityIsLastAndOnlyWhenTheServiceIsConnected() {
        assertFalse(Way.ACCESSIBILITY in OpenTvPlan.plan(st(accessibility = false, overlay = true)).ways)
        assertEquals(Way.ACCESSIBILITY, OpenTvPlan.plan(st(accessibility = true, overlay = true)).ways.last())
    }

    @Test fun waysAreNeverRepeated() {
        val ways = OpenTvPlan.plan(st(overlay = true, accessibility = true)).ways
        assertEquals(ways.toSet().size, ways.size)
    }

    // ---------------------------------------------------------------- l'autorisation « par-dessus »

    @Test fun theOverlayIsAskedOnlyWhenItWouldHelpAndTheScreenExists() {
        assertTrue(OpenTvPlan.plan(st(sdk = 34, overlay = false, screen = true)).needsOverlay)
        assertFalse(OpenTvPlan.plan(st(sdk = 34, overlay = false, screen = false)).needsOverlay, "GaiaOS-like box: no screen, nothing the owner can do there")
        assertFalse(OpenTvPlan.plan(st(sdk = 34, overlay = true, screen = true)).needsOverlay, "already granted")
        assertFalse(OpenTvPlan.plan(st(sdk = 28, overlay = false, screen = true)).needsOverlay, "no restriction before Android 10")
        assertTrue(OpenTvPlan.plan(st(sdk = 29, overlay = false, screen = true)).needsOverlay)
    }

    @Test fun noWayAtAllIsAPlanNotACrash() {
        val p = OpenTvPlan.plan(st(notifications = false, accessibility = false, overlay = false))
        assertEquals(emptyList(), p.ways)
        assertTrue(p.needsOverlay)
    }

    @Test fun eachWayWaitsLongEnoughForAWeakBoxButTheWholeStaysUnderTheBudgetOfThePhone() {
        assertTrue(Way.DIRECT.waitMs >= 1500 && Way.ACCESSIBILITY.waitMs >= 1500, "a 32-bit box opens its screen in about a second")
        assertTrue(Way.FULLSCREEN.waitMs < Way.DIRECT.waitMs, "a notification cannot be verified: short wait")
        assertTrue(Way.values().sumOf { it.waitMs } <= 5_000, "the phone waits 10 s in all, other routes included")
    }

    // ---------------------------------------------------------------- l'exécution (essais dans l'ordre, vérifiés)

    /** A scripted TV: which ways cannot even be tried, and which one finally brings the screen up. */
    private class Script(val opensWith: Way? = null, val cannotStart: Set<Way> = emptySet()) : OpenTvPlan.Actions {
        val log = mutableListOf<String>()
        private var last: Way? = null
        override fun start(way: Way, screen: OpenTvScreen?, fullScreenIntent: Boolean): Boolean {
            log += "start:${way.wire}:${screen?.wire}:$fullScreenIntent"
            if (way in cannotStart) return false
            last = way; return true
        }
        override fun awaitFront(maxMs: Long): Boolean { log += "wait:$maxMs"; return last != null && last == opensWith }
        override fun withdraw() { log += "withdraw" }
    }

    @Test fun alreadyInFrontTouchesNothing() {
        val a = Script()
        val r = OpenTvPlan.execute(st(front = true, overlay = true), OpenTvScreen.QUIZ, a)
        assertEquals(OpenTvReply.already(), r)
        assertEquals(emptyList(), a.log, "a screen in front is never started again (a video would be interrupted)")
    }

    @Test fun directStartThatComesUpEndsTheSearch() {
        val a = Script(opensWith = Way.DIRECT)
        val r = OpenTvPlan.execute(st(overlay = true, accessibility = true), null, a)
        assertEquals(OpenTvReply(true, "direct", null), r)
        assertEquals(listOf("start:direct:null:true", "wait:2000"), a.log, "no notification after a success")
    }

    @Test fun aBlockedDirectStartIsDetectedByLookingAtTheScreenAndTheNotificationFollows() {
        val a = Script(opensWith = Way.FULLSCREEN)
        val r = OpenTvPlan.execute(st(overlay = true), OpenTvScreen.LIBRARY, a)
        assertEquals(OpenTvReply(true, "fullscreen", null), r)
        assertEquals(listOf("start:direct:library:true", "wait:2000", "start:fullscreen:library:true", "wait:800", "withdraw"), a.log,
            "the notification did its job once the screen is up: it is taken down")
    }

    @Test fun aWayThatCannotBeTriedIsSkippedWithoutWaiting() {
        val a = Script(opensWith = Way.ACCESSIBILITY, cannotStart = setOf(Way.DIRECT))
        val r = OpenTvPlan.execute(st(overlay = true, accessibility = true), null, a)
        assertEquals(OpenTvReply(true, "accessibility", null), r)
        assertEquals(listOf("start:direct:null:true", "start:fullscreen:null:true", "wait:800", "start:accessibility:null:true", "wait:2000", "withdraw"), a.log)
    }

    @Test fun accessibilityBringsTheScreenWhenTheNotificationIsNotEnough() {
        val a = Script(opensWith = Way.ACCESSIBILITY)
        val r = OpenTvPlan.execute(st(accessibility = true), null, a)
        assertEquals("accessibility", r.how); assertTrue(r.opened)
        assertEquals(listOf("start:fullscreen:null:true", "wait:800", "start:accessibility:null:true", "wait:2000", "withdraw"), a.log)
    }

    @Test fun nothingWorksButANotificationWasPostedSaysSoAndNamesThePermission() {
        val a = Script(opensWith = null)
        val r = OpenTvPlan.execute(st(overlay = false, screen = true), null, a)
        assertEquals(OpenTvReply(false, "fullscreen", "overlay"), r)
        assertFalse("withdraw" in a.log, "the notification stays: it is the only way left for the viewer")
    }

    @Test fun nothingWorksAndTheScreenDoesNotExistNamesNoPermission() {
        val r = OpenTvPlan.execute(st(overlay = false, screen = false), null, Script(opensWith = null))
        assertEquals(OpenTvReply(false, "fullscreen", null), r)
    }

    @Test fun noWayToTryAnswersNoneAndStillNamesThePermission() {
        val a = Script()
        val r = OpenTvPlan.execute(st(notifications = false), null, a)
        assertEquals(OpenTvReply(false, "none", "overlay"), r)
        assertEquals(emptyList(), a.log)
    }

    @Test fun aNotificationThatCouldNotBePostedIsNotReportedAsPosted() {
        val a = Script(opensWith = null, cannotStart = setOf(Way.FULLSCREEN))
        val r = OpenTvPlan.execute(st(), null, a)
        assertEquals("none", r.how)
        assertFalse(r.opened)
    }

    @Test fun theScreenAndTheFullScreenFlagReachEveryAttempt() {
        val a = Script(opensWith = null)
        OpenTvPlan.execute(st(overlay = true, fullScreen = false, accessibility = true), OpenTvScreen.GAMES, a)
        assertEquals(listOf("start:direct:games:false", "wait:2000", "start:fullscreen:games:false", "wait:800", "start:accessibility:games:false", "wait:2000"), a.log)
    }

    @Test fun openedNeverCarriesAPermissionRequest() {
        val r = OpenTvPlan.execute(st(overlay = false), null, Script(opensWith = Way.FULLSCREEN))
        assertTrue(r.opened); assertNull(r.needs)
    }

    // ---------------------------------------------------------------- la réponse JSON

    @Test fun replyJsonIsExactlyWhatThePhoneReads() {
        assertEquals("""{"opened":true,"how":"direct","needs":null}""", OpenTvReply(true, "direct", null).toJson())
        assertEquals("""{"opened":false,"how":"none","needs":"overlay"}""", OpenTvReply(false, "none", "overlay").toJson())
        assertEquals("""{"opened":true,"already":true,"how":"already","needs":null}""", OpenTvReply.already().toJson())
    }

    @Test fun replyRoundTrips() {
        for (r in listOf(OpenTvReply(true, "direct", null), OpenTvReply(false, "fullscreen", "overlay"), OpenTvReply.already(), OpenTvReply(true, "accessibility", null)))
            assertEquals(r, OpenTvReply.parse(r.toJson()), r.toString())
    }

    @Test fun theReplyOfAnotherVersionIsReadTolerantly() {
        assertEquals(OpenTvReply(true, "sorcery", null), OpenTvReply.parse("""{"opened":true,"how":"sorcery","extra":[1,2],"needs":null}"""))
        assertEquals(OpenTvReply.already(), OpenTvReply.parse("""{"opened":true,"how":"already"}"""), "a TV that only says how: already")
        assertTrue(OpenTvReply.parse("""{"opened":true,"already":true,"how":"none"}""")!!.already, "or only the flag")
        assertFalse(OpenTvReply.parse("""{"opened":true,"already":"yes","how":"direct"}""")!!.already, "only a real true counts")
        assertEquals(OpenTvReply(false, "none", null), OpenTvReply.parse("""{"opened":false}"""), "how missing")
        assertEquals(OpenTvReply(false, "none", null), OpenTvReply.parse("""{"opened":false,"how":"none","needs":7}"""), "needs must be a word")
        assertNull(OpenTvReply.parse("""{"how":"direct"}"""), "no opened: not an answer")
        assertNull(OpenTvReply.parse("""{"opened":"yes"}"""))
        assertNull(OpenTvReply.parse("not json")); assertNull(OpenTvReply.parse("")); assertNull(OpenTvReply.parse("[]"))
    }

    // ---------------------------------------------------------------- l'écran demandé

    @Test fun screensParseCaseAndSpacesTolerantly() {
        assertEquals(OpenTvScreen.HOME, OpenTvScreen.parse("home"))
        assertEquals(OpenTvScreen.LIBRARY, OpenTvScreen.parse(" Library "))
        assertEquals(OpenTvScreen.PLAYER, OpenTvScreen.parse("PLAYER"))
        assertEquals(OpenTvScreen.GAMES, OpenTvScreen.parse("games"))
        assertEquals(OpenTvScreen.QUIZ, OpenTvScreen.parse("quiz"))
        for (bad in listOf(null, "", "  ", "settings", "home;rm", "quiz2", "x".repeat(100))) assertNull(OpenTvScreen.parse(bad), "$bad")
    }

    @Test fun theTrialTvOpensNeitherTheLibraryNorTheQuiz() {
        assertEquals(OpenTvScreen.HOME, OpenTvScreen.forEdition(OpenTvScreen.LIBRARY, trial = true), "the library tile is closed in the trial")
        assertEquals(OpenTvScreen.GAMES, OpenTvScreen.forEdition(OpenTvScreen.QUIZ, trial = true), "only the Sudoku is open in the games hub")
        for (s in listOf(null, OpenTvScreen.HOME, OpenTvScreen.PLAYER, OpenTvScreen.GAMES)) assertEquals(s, OpenTvScreen.forEdition(s, trial = true), "$s")
        for (s in listOf(null) + OpenTvScreen.values().toList()) assertEquals(s, OpenTvScreen.forEdition(s, trial = false), "a full TV opens anything: $s")
    }

    @Test fun theListOfScreensInTheErrorMessageIsTheRealList() {
        for (s in OpenTvScreen.values()) assertTrue(s.wire in OpenTvScreen.LIST, s.wire)
        assertEquals(5, OpenTvScreen.values().size)
    }

    // ---------------------------------------------------------------- la ligne de MENU, une seule fois

    @Test fun theMenuLineIsOfferedOnceAfterAPhoneCouldNotOpenTheTv() {
        val line = OverlayOfferPolicy.LINE
        assertEquals("Autoriser CastBridge-TV à s'afficher par-dessus les autres applications", line)
        assertTrue(OverlayOfferPolicy.shouldOffer(wanted = true, overlayAllowed = false, screenExists = true, alreadyAsked = false, sdk = 34))
        assertFalse(OverlayOfferPolicy.shouldOffer(wanted = false, overlayAllowed = false, screenExists = true, alreadyAsked = false, sdk = 34), "no phone asked: no noise in the MENU")
        assertFalse(OverlayOfferPolicy.shouldOffer(wanted = true, overlayAllowed = true, screenExists = true, alreadyAsked = false, sdk = 34), "already granted")
        assertFalse(OverlayOfferPolicy.shouldOffer(wanted = true, overlayAllowed = false, screenExists = false, alreadyAsked = false, sdk = 34), "the box has no such screen")
        assertFalse(OverlayOfferPolicy.shouldOffer(wanted = true, overlayAllowed = false, screenExists = true, alreadyAsked = true, sdk = 34), "refusal remembered: never again")
        assertFalse(OverlayOfferPolicy.shouldOffer(wanted = true, overlayAllowed = false, screenExists = true, alreadyAsked = false, sdk = 28), "nothing to authorize before Android 10")
    }
}
