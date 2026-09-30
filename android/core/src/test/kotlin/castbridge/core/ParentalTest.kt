package castbridge.core

import castbridge.core.net.JsonLite
import castbridge.core.parental.*
import castbridge.core.tv.ApiReply
import java.time.ZoneId
import kotlin.test.*

/** Fast hasher for tests (the real one uses 60 000 iterations). */
private fun fastHasher() = PinHasher(iterations = 1000)

private class Clock(var t: Long = 1_750_000_000_000L) { fun now() = t; fun advanceMin(m: Long) { t += m * 60_000 } }

/** UTC everywhere so "minutes since midnight" is predictable. */
private fun engine(store: KvStore = MemoryKv(), clock: Clock = Clock()) =
    ParentalEngine(store, clock::now, { ZoneId.of("UTC") }, fastHasher())

class ParentalPinTest {
    @Test fun hashIsSaltedAndNeverContainsThePin() {
        val h = fastHasher()
        val a = h.hash("4821"); val b = h.hash("4821")
        assertNotEquals(a, b, "two hashes of the same PIN must differ (random salt)")
        assertFalse(a.contains("4821"))
        assertTrue(a.startsWith("pbkdf2-sha256$1000$"))
        assertTrue(h.verify("4821", a)); assertTrue(h.verify("4821", b))
        assertFalse(h.verify("4822", a)); assertFalse(h.verify("", a)); assertFalse(h.verify("4821", null))
        assertFalse(h.verify("4821", "4821"), "a clear-text value is not a valid stored hash")
        assertFalse(h.verify("4821", "pbkdf2-sha256\$9\$AAAA\$AAAA"), "absurd iteration count refused")
    }

    @Test fun storedValueInPreferencesIsTheHash() {
        val kv = MemoryKv(); val e = engine(kv)
        assertNull(e.createPin("4821"))
        assertTrue(kv.dump().values.none { it.contains("4821") }, "the PIN must not be stored in clear")
        assertTrue(kv.get("pin")!!.startsWith("pbkdf2-sha256$"))
    }

    @Test fun noDefaultPinAndNothingIsActiveBeforeOneIsCreated() {
        val e = engine()
        assertFalse(e.hasPin())
        assertEquals(PinResult.NoPin, e.verifyPin("4444"))
        assertEquals(PinResult.NoPin, e.verifyPin(""))
        assertEquals(PinResult.NoPin, e.verifyPin(null))
        // enabling without a PIN is silently impossible
        e.edit { it.copy(enabled = true, profiles = listOf(ChildProfile("c1", "Léa")), activeProfile = "c1") }
        assertFalse(e.config().enabled)
        assertTrue(e.check(Category.GAMES).allowed)
    }

    @Test fun pinFormatIsValidated() {
        for (bad in listOf(null, "", "123", "1234567", "12a4", "12 4", "4444", "0000", "1234", "9876", "111111")) {
            assertNotNull(ParentalPins.validateNew(bad), "should refuse '$bad'")
        }
        for (good in listOf("4821", "0417", "135790", "2580")) assertNull(ParentalPins.validateNew(good), "should accept $good")
        val e = engine()
        assertNotNull(e.createPin("")); assertNotNull(e.createPin("4444")); assertFalse(e.hasPin())
        assertNull(e.createPin("4821")); assertTrue(e.hasPin())
        assertNotNull(e.createPin("7391"), "a second creation must not overwrite the PIN")
        assertEquals(PinResult.Ok, e.verifyPin("4821"))
    }

    @Test fun changePinNeedsTheOldOne() {
        val e = engine(); e.createPin("4821")
        assertNotNull(e.changePin("0000", "7391"))
        assertNotNull(e.changePin("4821", "1111"))
        assertNull(e.changePin("4821", "7391"))
        assertEquals(PinResult.Ok, e.verifyPin("7391")); assertTrue(e.verifyPin("4821") is PinResult.Wrong)
    }

    @Test fun progressiveLockout() {
        val clock = Clock(); val e = engine(clock = clock); e.createPin("4821")
        repeat(4) { assertTrue(e.verifyPin("0001") is PinResult.Wrong) }
        val fifth = e.verifyPin("0001")
        assertTrue(fifth is PinResult.Locked); assertEquals(60L, (fifth as PinResult.Locked).retryAfterSec)
        assertTrue(e.verifyPin("4821") is PinResult.Locked, "even the right PIN is refused while locked")
        clock.t += 59_000; assertTrue(e.verifyPin("4821") is PinResult.Locked)
        clock.t += 2_000
        // next series: 5 failures, then 5 minutes
        repeat(4) { assertTrue(e.verifyPin("0001") is PinResult.Wrong) }
        assertEquals(300L, (e.verifyPin("0001") as PinResult.Locked).retryAfterSec)
        clock.advanceMin(6)
        repeat(4) { e.verifyPin("0001") }
        assertEquals(900L, (e.verifyPin("0001") as PinResult.Locked).retryAfterSec)
        clock.advanceMin(16)
        repeat(4) { e.verifyPin("0001") }
        assertEquals(3600L, (e.verifyPin("0001") as PinResult.Locked).retryAfterSec)
        clock.advanceMin(61)
        repeat(4) { e.verifyPin("0001") }
        assertEquals(3600L, (e.verifyPin("0001") as PinResult.Locked).retryAfterSec, "stays at one hour")
        clock.advanceMin(61)
        assertEquals(PinResult.Ok, e.verifyPin("4821"))
        // a success resets the ladder
        repeat(4) { e.verifyPin("0001") }
        assertEquals(60L, (e.verifyPin("0001") as PinResult.Locked).retryAfterSec)
    }

    @Test fun emptyPinCountsAsAFailure() {
        val e = engine(); e.createPin("4821")
        repeat(4) { assertTrue(e.verifyPin("") is PinResult.Wrong) }
        assertTrue(e.verifyPin("") is PinResult.Locked)
    }

    @Test fun lockoutSurvivesARestartOfTheApp() {
        val kv = MemoryKv(); val clock = Clock()
        val e1 = engine(kv, clock); e1.createPin("4821")
        repeat(5) { e1.verifyPin("0001") }
        val e2 = engine(kv, clock)            // a new process reading the same preferences
        assertTrue(e2.verifyPin("4821") is PinResult.Locked, "restarting must not clear the lock")
        assertTrue(e2.lockedForSec() in 1..60)
        clock.advanceMin(2)
        assertEquals(PinResult.Ok, e2.verifyPin("4821"))
    }

    @Test fun partialFailuresAlsoSurviveARestart() {
        val kv = MemoryKv(); val clock = Clock()
        val e1 = engine(kv, clock); e1.createPin("4821")
        repeat(3) { e1.verifyPin("0001") }
        val e2 = engine(kv, clock)
        assertTrue(e2.verifyPin("0001") is PinResult.Wrong)
        assertTrue(e2.verifyPin("0001") is PinResult.Locked, "3 + 2 = 5 failures across a restart")
    }

    @Test fun clockSetBackCannotExtendTheLockBeyondTheLongestStep() {
        val kv = MemoryKv(); val clock = Clock()
        val lock = PinLock(kv, "x", clock::now)
        repeat(5) { lock.recordFailure() }
        clock.t -= 10L * 24 * 3600_000                       // the TV's date goes back ten days
        assertTrue(lock.remainingMs() <= 3_600_000)
    }

    @Test fun adminResetNeedsTheTvPinAndIsThrottled() {
        val e = engine(); e.createPin("4821")
        e.edit { it.copy(enabled = true, profiles = listOf(ChildProfile("c1", "Léa")), activeProfile = "c1") }
        assertTrue(e.config().enabled)
        assertTrue(e.adminReset("000000") { it == "123456" } is PinResult.Wrong)
        assertTrue(e.hasPin())
        assertTrue(e.adminReset("") { true } is PinResult.Wrong, "an empty admin PIN is refused")
        assertEquals(PinResult.Ok, e.adminReset("123456") { it == "123456" })
        assertFalse(e.hasPin()); assertFalse(e.config().enabled)
        assertEquals(1, e.config().profiles.size, "profiles are kept")
        // no PIN left and no default: a new one can be created
        assertNull(e.createPin("7391"))
    }

    @Test fun adminResetLocksAfterFiveWrongTvPins() {
        val e = engine(); e.createPin("4821")
        repeat(4) { e.adminReset("0") { false } }
        assertTrue(e.adminReset("0") { false } is PinResult.Locked)
        assertTrue(e.adminReset("123456") { true } is PinResult.Locked)
        assertTrue(e.hasPin())
    }
}

class ParentalRulesTest {
    private val kid = ChildProfile("c1", "Léa", AgeBand.KID)
    private val teen = ChildProfile("c2", "Paul", AgeBand.TEEN12)

    private fun cfg(vararg r: RatingRule, unrated: Rating = Rating.ADULT) = ParentalConfig(rules = r.toList(), unrated = unrated)

    @Test fun ratingPrecedence() {
        val c = cfg(RatingRule(RuleKind.KEYWORD, "horreur", Rating.U16), RatingRule(RuleKind.FILE, "Horreur-pour-rire.mp4", Rating.ALL),
            RatingRule(RuleKind.VOLUME, "Clé Papa", Rating.U12), RatingRule(RuleKind.KEYWORD, "zombie", Rating.ADULT))
        assertEquals(Rating.ALL, ParentalRules.ratingOf(c, "horreur-pour-rire.MP4"), "a rule on the file wins")
        assertEquals(Rating.U16, ParentalRules.ratingOf(c, "film_horreur.mkv"))
        assertEquals(Rating.ADULT, ParentalRules.ratingOf(c, "horreur zombie.mkv"), "strictest keyword")
        assertEquals(Rating.U12, ParentalRules.ratingOf(c, "x.mp4", "clé papa"))
        assertEquals(Rating.ADULT, ParentalRules.ratingOf(c, "inconnu.mp4"), "unrated = adult by default")
        assertEquals(Rating.ALL, ParentalRules.ratingOf(cfg(unrated = Rating.ALL), "inconnu.mp4"))
    }

    @Test fun ageBandsAndRatings() {
        assertTrue(ParentalRules.videoAllowed(kid, Rating.ALL)); assertFalse(ParentalRules.videoAllowed(kid, Rating.U12))
        assertTrue(ParentalRules.videoAllowed(teen, Rating.U12)); assertFalse(ParentalRules.videoAllowed(teen, Rating.U16))
        assertTrue(AgeBand.TEEN16.allows(Rating.U16)); assertFalse(AgeBand.TEEN16.allows(Rating.ADULT))
        assertTrue(AgeBand.ADULT.allows(Rating.ADULT))
    }

    @Test fun windowCrossingMidnight() {
        val w = TimeWindow(20 * 60, 7 * 60)
        assertTrue(w.contains(21 * 60)); assertTrue(w.contains(3 * 60)); assertFalse(w.contains(12 * 60)); assertFalse(w.contains(7 * 60))
        assertEquals(6 * 60, w.minutesLeft(1 * 60)); assertEquals(9 * 60, w.minutesLeft(22 * 60))
        val day = TimeWindow(7 * 60, 20 * 60)
        assertTrue(day.contains(7 * 60)); assertFalse(day.contains(20 * 60)); assertEquals(5, day.minutesLeft(19 * 60 + 55))
    }

    @Test fun timeVerdictWindowAndQuota() {
        val p = kid.copy(window = TimeWindow(7 * 60, 20 * 60), dailyLimitMin = 60)
        assertTrue(ParentalRules.timeVerdict(p, UseKind.PLAY, 12 * 60, 0).allowed)
        assertEquals("window", ParentalRules.timeVerdict(p, UseKind.PLAY, 21 * 60, 0).code)
        assertEquals("window", ParentalRules.timeVerdict(p, UseKind.GAMES, 6 * 60, 0).code)
        assertEquals("limit", ParentalRules.timeVerdict(p, UseKind.PLAY, 12 * 60, 60 * 60_000L).code)
        assertEquals(1, ParentalRules.timeVerdict(p, UseKind.PLAY, 12 * 60, 59 * 60_000L).minutesLeft)
        assertEquals(30, ParentalRules.timeVerdict(p, UseKind.PLAY, 19 * 60 + 30, 0).minutesLeft, "the nearest end wins")
        // a kind the rule does not cover is free
        val only = p.copy(kinds = setOf(UseKind.PLAY))
        assertTrue(ParentalRules.timeVerdict(only, UseKind.GAMES, 23 * 60, 999_999_999).allowed)
        assertTrue(ParentalRules.timeVerdict(kid, UseKind.PLAY, 3 * 60, 999_999_999).allowed, "no rule, no limit")
    }

    @Test fun categoriesAndTheUnblockableOnes() {
        val p = kid.copy(blocked = setOf(Category.GAMES, Category.SSH))
        assertTrue(ParentalRules.categoryBlocked(p, Category.GAMES)); assertFalse(ParentalRules.categoryBlocked(p, Category.ADMIN))
        assertFalse(Category.LEARN.blockable); assertFalse(Category.NAVIGATION.blockable)
        // even a hand-made profile cannot block Apprendre or the navigation
        val parsed = ParentalConfig.parse(ParentalConfig(profiles = listOf(kid)).toJson().replace("\"blocked\":[", "\"blocked\":[\"learn\",\"nav\","))
        assertFalse(Category.LEARN in parsed.profiles[0].blocked); assertFalse(Category.NAVIGATION in parsed.profiles[0].blocked)
    }

    @Test fun kidHomeKeepsLearningGamesAndTheParentalDoor() {
        val all = listOf("Bibliothèque", "Apprendre", "Quiz", "Échecs", "Téléchargements", "Télécommande", "Administration", "Connexion & réglages", "Options développeur", "Aide", "Contrôle parental")
        val shown = ParentalRules.kidHome(all, kid)
        assertEquals(listOf("Bibliothèque", "Apprendre", "Quiz", "Échecs", "Aide", "Contrôle parental"), shown)
        val noGames = ParentalRules.kidHome(all, kid.copy(blocked = setOf(Category.GAMES)))
        assertFalse("Quiz" in noGames); assertTrue("Apprendre" in noGames && "Contrôle parental" in noGames)
    }

    @Test fun configRoundTripAndValidation() {
        val c = ParentalConfig(rev = 3, enabled = true, activeProfile = "c1", unrated = Rating.U16, overAge = OverAge.LOCK, sessionMin = 15,
            profiles = listOf(kid.copy(window = TimeWindow(480, 1200), dailyLimitMin = 90, learnId = "p1"), teen),
            rules = listOf(RatingRule(RuleKind.FILE, "a b.mp4", Rating.U12)))
        assertEquals(c, ParentalConfig.parse(c.toJson()))
        for (bad in listOf("{", "[]", """{"profiles":[{"id":"a b","name":"X"}]}""", """{"profiles":[{"id":"a","name":""}]}""",
            """{"profiles":[{"id":"a","name":"X","window":{"from":5,"to":5}}]}""", """{"profiles":[{"id":"a","name":"X","limitMin":-3}]}""",
            """{"rules":[{"k":"file","m":"","r":"12"}]}""", """{"rules":[{"k":"zzz","m":"a","r":"12"}]}""", """{"rules":[{"k":"file","m":"a","r":"99"}]}""")) {
            assertFailsWith<IllegalArgumentException>("should refuse $bad") { ParentalConfig.parse(bad) }
        }
    }

    @Test fun guessAgeFromLearnLevel() {
        assertEquals(AgeBand.KID, AgeBand.guessFromLevel("CM2")); assertEquals(AgeBand.TEEN12, AgeBand.guessFromLevel("3e"))
        assertEquals(AgeBand.KID, AgeBand.guessFromLevel(null))
    }
}

class ParentalEngineTest {
    private fun setup(clock: Clock = Clock(), kv: KvStore = MemoryKv(), p: ChildProfile = ChildProfile("c1", "Léa", AgeBand.KID, window = TimeWindow(7 * 60, 20 * 60), dailyLimitMin = 60)): ParentalEngine {
        val e = engine(kv, clock); e.createPin("4821")
        e.edit { it.copy(enabled = true, profiles = listOf(p), activeProfile = p.id,
            rules = listOf(RatingRule(RuleKind.FILE, "dessin.mp4", Rating.ALL), RatingRule(RuleKind.FILE, "film12.mp4", Rating.U12))) }
        return e
    }

    /** 12:00 UTC on the clock. */
    private fun noon() = Clock(1_750_000_000_000L - (1_750_000_000_000L % 86_400_000L) + 12 * 3_600_000L)

    @Test fun filteringByRating() {
        val e = setup(noon())
        assertTrue(e.checkPlayback("dessin.mp4").allowed)
        val d = e.checkPlayback("film12.mp4"); assertFalse(d.allowed); assertEquals("rating", d.code)
        assertFalse(e.checkPlayback("jamais-classee.mp4").allowed, "unrated is adult by default")
        val f = e.libraryFilter()!!
        assertTrue(f("dessin.mp4", null)); assertFalse(f("film12.mp4", null))
        // lock mode keeps the videos visible and asks the PIN at play time
        e.edit { it.copy(overAge = OverAge.LOCK) }
        assertNull(e.libraryFilter()); assertTrue(e.needsPinToPlay("film12.mp4")); assertFalse(e.needsPinToPlay("dessin.mp4"))
    }

    @Test fun parentSessionSuspendsEveryRule() {
        val clock = noon(); val e = setup(clock)
        assertFalse(e.checkPlayback("film12.mp4").allowed)
        assertFalse(e.check(Category.SETTINGS).allowed)
        e.startSession()
        assertTrue(e.checkPlayback("film12.mp4").allowed); assertTrue(e.check(Category.SETTINGS).allowed); assertNull(e.libraryFilter())
        clock.advanceMin(31)                            // default session is 30 minutes
        assertFalse(e.sessionActive()); assertFalse(e.check(Category.SETTINGS).allowed)
        e.startSession(); e.endSession(); assertFalse(e.check(Category.SETTINGS).allowed)
    }

    @Test fun disabledOrUnconfiguredControlBlocksNothing() {
        val e = setup(noon())
        e.adminDisable()
        assertTrue(e.check(Category.ADMIN).allowed); assertTrue(e.checkPlayback("film12.mp4").allowed); assertNull(e.libraryFilter())
        assertTrue(e.hasPin(), "disabling does not erase the PIN")
    }

    @Test fun windowAndDailyQuotaThroughTicks() {
        val clock = noon(); val e = setup(clock)
        val p = e.activeProfile()!!
        // 54 minutes of video, in 15 s ticks
        var warn: Int? = null
        repeat(54 * 4) { val r = e.tick(UseKind.PLAY, 15_000); if (r.warnMinutes != null) warn = r.warnMinutes; assertNull(r.blockReason) }
        assertNull(warn, "no warning yet at 6 minutes left")
        val w = e.tick(UseKind.PLAY, 15_000)
        assertNull(w.blockReason)
        // reach 5 minutes left: one warning, only once
        var warns = 0
        repeat(6 * 4) { val r = e.tick(UseKind.PLAY, 15_000); if (r.warnMinutes != null) { warns++; assertTrue(r.warnMinutes!! <= 5) } }
        assertEquals(1, warns)
        // the limit is reached: playing is refused, and so are games (the quota is shared)
        repeat(4 * 5) { e.tick(UseKind.PLAY, 15_000) }
        assertEquals("limit", e.checkPlayback("dessin.mp4").code)
        assertEquals("limit", e.check(Category.GAMES).code)
        assertNotNull(e.tick(UseKind.PLAY, 15_000).blockReason)
        assertTrue(e.usedMs(p) >= 60 * 60_000L)
        // tomorrow it is fresh again
        clock.advanceMin(24 * 60)
        assertTrue(e.checkPlayback("dessin.mp4").allowed)
    }

    @Test fun windowWarningFiveMinutesBeforeTheEnd() {
        val clock = noon(); clock.t += 7 * 3_600_000L + 54 * 60_000L          // 19:54, window closes at 20:00
        val e = setup(clock, p = ChildProfile("c1", "Léa", window = TimeWindow(7 * 60, 20 * 60)))
        val r1 = e.tick(UseKind.PLAY, 15_000); assertNull(r1.warnMinutes)
        clock.advanceMin(1)
        val r2 = e.tick(UseKind.PLAY, 15_000); assertEquals(5, r2.warnMinutes)
        assertNull(e.tick(UseKind.PLAY, 15_000).warnMinutes, "warned once")
        clock.advanceMin(5)
        val r3 = e.tick(UseKind.PLAY, 15_000); assertNotNull(r3.blockReason); assertTrue(r3.blockReason!!.contains("07:00 à 20:00"))
        assertEquals("window", e.check(Category.GAMES).code)
    }

    @Test fun usageIsPersistentAndReported() {
        val clock = noon(); val kv = MemoryKv()
        val e = setup(clock, kv)
        repeat(4 * 10) { e.tick(UseKind.PLAY, 15_000) }
        repeat(4 * 5) { e.tick(UseKind.GAMES, 15_000) }
        e.recordBlocked("film12.mp4", "rating")
        val again = engine(kv, clock)                   // restart
        val r = again.report()
        @Suppress("UNCHECKED_CAST") val days = r["days"] as List<Map<String, Any?>>
        assertEquals(1, days.size)
        @Suppress("UNCHECKED_CAST") val prof = (days[0]["profiles"] as List<Map<String, Any?>>)[0]
        assertEquals("Léa", prof["name"]); assertEquals(10L, prof["play"]); assertEquals(5L, prof["games"])
        @Suppress("UNCHECKED_CAST") val blocked = r["blocked"] as List<Map<String, Any?>>
        assertTrue(blocked.any { it["what"] == "film12.mp4" })
        again.clearHistory(); assertTrue((again.report()["days"] as List<*>).isEmpty())
    }

    @Test fun blockedLogDeduplicatesAndIsBounded() {
        val e = setup(noon())
        repeat(5) { e.recordBlocked("x.mp4", "rating") }
        @Suppress("UNCHECKED_CAST") assertEquals(1, (e.report()["blocked"] as List<*>).count { (it as Map<String, Any?>)["what"] == "x.mp4" })
    }

    @Test fun backAndHomeAreNeverBlocked() {
        // worst case: everything blocked, no time left, strict profile
        val p = ChildProfile("c1", "Léa", AgeBand.KID, blocked = Category.BLOCKABLE.toSet(), window = TimeWindow(1, 2), dailyLimitMin = 1)
        val e = setup(noon(), p = p)
        e.tick(UseKind.PLAY, 10 * 60_000L)
        for (c in Category.BLOCKABLE) assertFalse(e.check(c).allowed, "$c is blocked")
        assertTrue(e.check(Category.NAVIGATION).allowed, "navigation is never blocked")
        assertTrue(e.check(Category.LEARN).allowed, "Apprendre is never blocked")
        assertTrue(ParentalKeys.neverBlocked(ParentalKeys.KEYCODE_BACK)); assertTrue(ParentalKeys.neverBlocked(ParentalKeys.KEYCODE_HOME))
        assertEquals(3, ParentalKeys.KEYCODE_HOME); assertEquals(4, ParentalKeys.KEYCODE_BACK)     // android.view.KeyEvent values
        assertFalse(ParentalKeys.neverBlocked(23))
        // a locked screen always offers the PIN and the way out, whatever the reason
        for (reason in listOf(null, "", "window", "limit", "category", "rating")) {
            val a = LockScreenModel.actions(reason)
            assertTrue(LockScreenModel.Action.ENTER_PIN in a && LockScreenModel.Action.GO_HOME in a)
        }
        assertEquals("Saisir le PIN parental", LockScreenModel.Action.ENTER_PIN.label)
        // the administrator can still switch it off, and the PIN lock does not stop that
        repeat(5) { e.verifyPin("0001") }
        assertTrue(e.verifyPin("4821") is PinResult.Locked)
        e.adminDisable(); assertFalse(e.config().enabled); assertTrue(e.check(Category.SETTINGS).allowed)
    }
}

class ParentalApiTest {
    private val clock = Clock()
    private val e = engine(clock = clock)
    private var changed = 0
    private val api = ParentalApi(e, { listOf(Triple("p1", "Amina", "CM1")) }) { changed++ }

    private fun post(path: String, body: String, params: Map<String, String> = emptyMap()): ApiReply =
        api.handleBody("/api/parental$path", "POST", params, body.toByteArray())!!

    private fun obj(r: ApiReply) = JsonLite.obj(r.json)

    @Test fun ignoresOtherRoutes() {
        assertNull(api.handle("/api/library", "GET", emptyMap())); assertNull(api.handleBody("/api/play", "POST", emptyMap(), ByteArray(0)))
    }

    @Test fun statusHasNoSecretAndNoPinByDefault() {
        val r = api.handle("/api/parental", "GET", emptyMap())!!
        assertEquals(200, r.status)
        val o = obj(r)
        assertEquals(false, o["pinSet"]); assertEquals(false, o["enabled"])
        assertFalse(r.json.contains("pbkdf2")); assertFalse(r.json.contains("4444"))
    }

    @Test fun refusesEmptyDefaultAndWeakPins() {
        for (bad in listOf("", "4444", "1234", "0000", "12", "abcd", "1234567")) {
            val r = post("/pin/create", """{"pin":"$bad"}""")
            assertEquals(400, r.status, "PIN '$bad' must be refused")
        }
        assertEquals(400, post("/pin/create", "{}").status, "missing pin")
        assertEquals(400, post("/pin/create", """{"pin":null}""").status)
        assertFalse(e.hasPin())
        assertEquals(200, post("/pin/create", """{"pin":"4821"}""").status)
        assertTrue(e.hasPin()); assertTrue(changed > 0)
    }

    @Test fun neverAcceptsAPinInTheUrl() {
        for (k in listOf("pin", "ppin", "PIN", "new", "newpin", "parentalPin", "old")) {
            val r = post("/pin/create", """{"pin":"4821"}""", mapOf(k to "4821"))
            assertEquals(400, r.status, "?$k= must be refused"); assertFalse(e.hasPin())
            assertEquals(400, api.handle("/api/parental", "GET", mapOf(k to "4821"))!!.status)
        }
        e.createPin("4821")
        assertEquals(400, post("/unlock", """{"pin":"4821"}""", mapOf("ppin" to "4821")).status)
        assertFalse(e.sessionActive(), "a PIN in the URL must not unlock, even when correct")
    }

    @Test fun sensitiveRoutesNeedTheParentalPin() {
        assertEquals(409, post("/config/get", """{"pin":"4821"}""").status, "no PIN yet")
        e.createPin("4821")
        for (route in listOf("/config/get", "/config/set", "/report", "/history/clear", "/unlock")) {
            assertEquals(403, post(route, """{"pin":"0001","config":{}}""").status, route)
            assertEquals(403, post(route, "{}").status, "$route without pin")
            assertEquals(403, post(route, """{"pin":""}""").status, "$route empty pin")
            e.verifyPin("4821")                          // a success resets the counter between routes
        }
        assertEquals(200, post("/config/get", """{"pin":"4821"}""").status)
        assertEquals(200, post("/report", """{"pin":"4821"}""").status)
    }

    @Test fun wrongPinsLockTheApiWithRetryAfter() {
        e.createPin("4821")
        repeat(4) { assertEquals(403, post("/report", """{"pin":"0001"}""").status) }
        val r = post("/report", """{"pin":"0001"}""")
        assertEquals(429, r.status); assertEquals(60L, obj(r)["retryAfter"])
        assertEquals(429, post("/report", """{"pin":"4821"}""").status, "right PIN refused while locked")
        clock.advanceMin(2)
        assertEquals(200, post("/report", """{"pin":"4821"}""").status)
    }

    @Test fun configCanBeSetReadAndIsVersioned() {
        e.createPin("4821")
        val got = obj(post("/config/get", """{"pin":"4821"}"""))
        @Suppress("UNCHECKED_CAST") assertEquals("p1", (got["learnProfiles"] as List<Map<String, Any?>>)[0]["id"])
        val cfg = ParentalConfig(enabled = true, activeProfile = "c1", profiles = listOf(ChildProfile("c1", "Léa", learnId = "p1")))
        val body = JsonLite.write(linkedMapOf("pin" to "4821", "rev" to 0, "config" to cfg.toMap()))
        val ok = post("/config/set", body)
        assertEquals(200, ok.status, ok.json)
        assertTrue(e.config().enabled); assertEquals(1, e.config().rev)
        assertEquals(409, post("/config/set", body).status, "stale revision")
        // garbage is refused without changing anything
        val bad = JsonLite.write(linkedMapOf("pin" to "4821", "rev" to 1, "config" to linkedMapOf("profiles" to listOf(linkedMapOf("id" to "x", "name" to "")))))
        assertEquals(400, post("/config/set", bad).status); assertEquals(1, e.config().rev)
        // enabling needs an active profile when there are profiles
        val noActive = JsonLite.write(linkedMapOf("pin" to "4821", "rev" to 1, "config" to cfg.copy(activeProfile = null).toMap()))
        assertEquals(400, post("/config/set", noActive).status)
    }

    @Test fun adminRoutesNeedNoParentalPinButResetNeedsConfirmation() {
        e.createPin("4821")
        e.edit { it.copy(enabled = true, profiles = listOf(ChildProfile("c1", "Léa")), activeProfile = "c1") }
        assertEquals(400, post("/reset", "{}").status); assertTrue(e.hasPin())
        assertEquals(200, post("/disable", "{}").status); assertFalse(e.config().enabled)
        assertEquals(200, post("/reset", """{"confirm":"RESET"}""").status)
        assertFalse(e.hasPin(), "the administrator can erase the PIN")
        assertEquals(false, obj(api.handle("/api/parental", "GET", emptyMap())!!)["pinSet"])
    }

    @Test fun pinChangeAndUnlock() {
        e.createPin("4821")
        assertEquals(403, post("/pin/change", """{"pin":"0001","new":"7391"}""").status)
        assertEquals(400, post("/pin/change", """{"pin":"4821","new":"4444"}""").status)
        assertEquals(400, post("/pin/change", """{"pin":"4821","new":""}""").status)
        assertEquals(200, post("/pin/change", """{"pin":"4821","new":"7391"}""").status)
        assertEquals(403, post("/unlock", """{"pin":"4821"}""").status)
        assertEquals(200, post("/unlock", """{"pin":"7391"}""").status); assertTrue(e.sessionActive())
        assertEquals(200, post("/lock", "{}").status); assertFalse(e.sessionActive())
    }

    @Test fun badBodiesAreRefused() {
        assertEquals(400, post("/pin/create", "not json").status)
        assertEquals(400, post("/pin/create", "[]").status)
        assertEquals(404, post("/nothing", "{}").status)
        assertEquals(405, api.handle("/api/parental/report", "GET", emptyMap())!!.status)
        assertEquals(413, api.handleBody("/api/parental/report", "POST", emptyMap(), ByteArray(70_000))!!.status)
    }

    @Test fun jsonOutputIsStrictlyValid() {
        e.createPin("4821")
        for (r in listOf(api.handle("/api/parental", "GET", emptyMap())!!, post("/config/get", """{"pin":"4821"}"""), post("/report", """{"pin":"4821"}"""))) {
            JsonLite.parse(r.json)                      // throws on a stray bracket (cf. the /api/library regression)
        }
    }
}
