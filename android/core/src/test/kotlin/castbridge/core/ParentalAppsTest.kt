package castbridge.core

import castbridge.core.net.JsonLite
import castbridge.core.parental.*
import java.time.ZoneId
import kotlin.test.*

private class AClock(var t: Long = 1_750_000_000_000L) { fun now() = t; fun advanceMin(m: Long) { t += m * 60_000 } }

private val ENV = AppEnv("castbridge.receiver", neverBlock = setOf("com.acme.launcher"), settingsPkgs = setOf("com.acme.settings"))

private fun eng(store: KvStore = MemoryKv(), clock: AClock = AClock()) =
    ParentalEngine(store, clock::now, { ZoneId.of("UTC") }, PinHasher(iterations = 1000))

/** An engine with a PIN, one active profile "c1" and the supervision on. */
private fun setup(window: TimeWindow? = null, limit: Int = 0, store: KvStore = MemoryKv(), clock: AClock = AClock(), configure: (AppSettings) -> AppSettings = { it }): ParentalEngine {
    val e = eng(store, clock)
    e.createPin("4821")
    e.edit { it.copy(enabled = true, profiles = listOf(ChildProfile("c1", "Léa", window = window, dailyLimitMin = limit)), activeProfile = "c1") }
    e.editApps { configure(it.copy(supervise = true, baselined = true)) }
    return e
}

class ParentalAppDecisionTest {
    private val p = ChildProfile("c1", "Léa")

    @Test fun essentialsAreNeverBlockable() {
        val s = AppSettings(supervise = true, newApp = NewAppDefault.BLOCK,
            rules = mapOf("c1" to listOf(AppRule("castbridge.receiver", AppState.BLOCKED), AppRule("com.acme.launcher", AppState.BLOCKED), AppRule("com.android.systemui", AppState.BLOCKED))))
        for (pkg in listOf("castbridge.receiver", "com.acme.launcher", "com.android.systemui", "com.google.android.tvlauncher", "android")) {
            assertTrue(AppRules.decide(p, s, pkg, ENV, 12 * 60, Long.MAX_VALUE / 2, 0, false).allowed, "$pkg must stay reachable (even outside every window, over every limit)")
            assertTrue(AppRules.neverBlockable(pkg, ENV))
        }
        assertFalse(AppRules.neverBlockable("com.netflix.ninja", ENV))
    }

    @Test fun settingsFollowTheSettingsCategory() {
        val s = AppSettings(supervise = true)
        assertEquals("category", AppRules.decide(p, s, "com.acme.settings", ENV, 600, 0, 0, false).code)
        assertEquals("category", AppRules.decide(p, s, "com.android.tv.settings", ENV, 600, 0, 0, false).code)
        assertTrue(AppRules.decide(p.copy(blocked = emptySet()), s, "com.android.tv.settings", ENV, 600, 0, 0, false).allowed)
    }

    @Test fun statesAllowedBlockedLimitedPin() {
        val s = AppSettings(supervise = true, rules = mapOf("c1" to listOf(
            AppRule("a.blocked", AppState.BLOCKED), AppRule("a.pin", AppState.PIN), AppRule("a.limited", AppState.LIMITED, 20), AppRule("a.ok", AppState.ALLOWED))))
        assertEquals("app", AppRules.decide(p, s, "a.blocked", ENV, 600, 0, 0, false).code)
        assertEquals("apppin", AppRules.decide(p, s, "a.pin", ENV, 600, 0, 0, false).code)
        assertTrue(AppRules.decide(p, s, "a.pin", ENV, 600, 0, 0, true).allowed, "the PIN opens it")
        assertTrue(AppRules.decide(p, s, "a.limited", ENV, 600, 0, 19 * 60_000L, false).allowed)
        assertEquals(1, AppRules.decide(p, s, "a.limited", ENV, 600, 0, 19 * 60_000L, false).minutesLeft)
        assertEquals("applimit", AppRules.decide(p, s, "a.limited", ENV, 600, 0, 20 * 60_000L, false).code)
        assertTrue(AppRules.decide(p, s, "a.ok", ENV, 600, 0, 0, false).allowed)
        assertTrue(AppRules.decide(p, s, "anything.else", ENV, 600, 0, 0, false).allowed, "a known app without a rule is allowed")
    }

    @Test fun rulesArePerProfile() {
        val s = AppSettings(supervise = true, rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED))))
        assertFalse(AppRules.decide(p, s, "a.x", ENV, 600, 0, 0, false).allowed)
        assertTrue(AppRules.decide(ChildProfile("c2", "Marc"), s, "a.x", ENV, 600, 0, 0, false).allowed)
    }

    @Test fun newAppFollowsTheParentsDefault() {
        val news = listOf(NewApp("a.new", "Nouvelle", 1))
        val block = AppSettings(supervise = true, newApp = NewAppDefault.BLOCK, news = news)
        val allow = block.copy(newApp = NewAppDefault.ALLOW)
        assertEquals("newapp", AppRules.decide(p, block, "a.new", ENV, 600, 0, 0, false).code)
        assertTrue(AppRules.decide(p, allow, "a.new", ENV, 600, 0, 0, false).allowed)
        // an explicit rule beats the default
        val ruled = block.copy(rules = mapOf("c1" to listOf(AppRule("a.new", AppState.ALLOWED))))
        assertTrue(AppRules.decide(p, ruled, "a.new", ENV, 600, 0, 0, false).allowed)
    }

    @Test fun windowAcrossMidnightAndDailyQuotaApplyToApps() {
        val night = p.copy(window = TimeWindow(20 * 60, 7 * 60), dailyLimitMin = 60)
        val s = AppSettings(supervise = true)
        assertEquals("window", AppRules.decide(night, s, "a.x", ENV, 12 * 60, 0, 0, false).code)
        assertTrue(AppRules.decide(night, s, "a.x", ENV, 23 * 60 + 59, 0, 0, false).allowed)
        assertTrue(AppRules.decide(night, s, "a.x", ENV, 0, 0, 0, false).allowed, "00:00 is inside 20:00-07:00")
        assertTrue(AppRules.decide(night, s, "a.x", ENV, 6 * 60 + 59, 0, 0, false).allowed)
        assertEquals("window", AppRules.decide(night, s, "a.x", ENV, 7 * 60, 0, 0, false).code)
        assertEquals("limit", AppRules.decide(night, s, "a.x", ENV, 22 * 60, 60 * 60_000L, 0, false).code)
        // apps are not counted when the parent left them out of the profile's kinds
        assertTrue(AppRules.decide(night.copy(kinds = setOf(UseKind.PLAY)), s, "a.x", ENV, 12 * 60, 0, 0, false).allowed)
    }

    @Test fun categoryGuess() {
        assertEquals(AppCategory.BROWSER, AppCategory.guess("com.android.chrome"))
        assertEquals(AppCategory.VIDEO, AppCategory.guess("com.netflix.ninja"))
        assertEquals(AppCategory.VIDEO, AppCategory.guess("com.google.android.youtube.tv"))
        assertEquals(AppCategory.GAMES, AppCategory.guess("com.some.game", isGame = true))
        assertEquals(AppCategory.OTHER, AppCategory.guess("com.some.tool"))
    }
}

class ParentalAppEngineTest {
    @Test fun settingsAreSeparateFromTheConfigAndSurviveAnOldPhoneSavingTheConfig() {
        val e = setup { it.copy(rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED)))) }
        // an old phone saves the whole config without knowing about apps
        val cfg = e.config()
        assertNotNull(e.edit(cfg.rev) { it.copy(sessionMin = 45) })
        assertEquals(AppState.BLOCKED, e.appSettings().rule("c1", "a.x")?.state, "app rules are not stored in the config")
        assertFalse(e.config().toJson().contains("a.x"))
    }

    @Test fun staleRevIsRefusedAndRulesOfDeletedProfilesAreDropped() {
        val e = setup { it.copy(rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED)), "gone" to listOf(AppRule("a.y", AppState.BLOCKED)))) }
        val rev = e.appSettings().rev
        assertNull(e.appSettings().rules["gone"], "unknown profile")
        assertNull(e.editApps(rev - 1) { it })
        assertNotNull(e.editApps(rev) { it })
    }

    @Test fun nothingIsSupervisedWithoutProfileOrWhenOffOrDuringAParentSession() {
        val e = setup { it.copy(rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED)))) }
        assertFalse(e.checkApp("a.x", ENV).allowed)
        e.startSession()
        assertTrue(e.checkApp("a.x", ENV).allowed, "a parent session suspends the rules")
        e.endSession()
        e.editApps { it.copy(supervise = false) }
        assertTrue(e.checkApp("a.x", ENV).allowed)
        assertEquals(SupervisionState.OFF, e.supervision().state)
    }

    @Test fun appUsageCountsIntoTheDailyQuotaAndWarnsFiveMinutesBefore() {
        val e = setup(limit = 30)
        var warn: Int? = null
        // 24 min = under the warn threshold, then the 25th minute warns once
        repeat(24 * 4) { assertNull(e.appTick("a.video", "Vidéo", 15_000, ENV).blockReason) }
        repeat(4) { val r = e.appTick("a.video", "Vidéo", 15_000, ENV); if (r.warnMinutes != null) warn = r.warnMinutes }
        assertNotNull(warn); assertTrue(warn!! <= 5)
        assertEquals(25 * 60_000L, e.appUsedMs("c1", "a.video"))
        // CastBridge-TV playing counts in the same quota
        e.tick(UseKind.PLAY, 4 * 60_000L)
        val r = e.appTick("a.video", "Vidéo", 60_000, ENV)
        assertNotNull(r.blockReason, "30 min over apps + CastBridge TV reached")
        assertEquals("limit", e.checkApp("a.video", ENV).code)
        @Suppress("UNCHECKED_CAST") val prof = ((e.report(1)["days"] as List<Map<String, Any?>>)[0]["profiles"] as List<Map<String, Any?>>)[0]
        assertEquals(26L, prof["apps"]); assertEquals(4L, prof["play"])
    }

    @Test fun perAppLimitStopsOnlyThatApp() {
        val e = setup { it.copy(rules = mapOf("c1" to listOf(AppRule("a.limited", AppState.LIMITED, 2)))) }
        assertNull(e.appTick("a.limited", "Jeu", 60_000, ENV).blockReason)
        assertNotNull(e.appTick("a.limited", "Jeu", 60_000, ENV).blockReason)
        assertEquals("applimit", e.checkApp("a.limited", ENV).code)
        assertTrue(e.checkApp("a.other", ENV).allowed)
    }

    @Test fun essentialsAreNeverCounted() {
        val e = setup()
        e.appTick("com.acme.launcher", "Accueil", 60_000, ENV)
        e.appTick("castbridge.receiver", "CastBridge TV", 60_000, ENV)
        assertEquals(0L, e.usedMs(e.config().active()!!))
    }

    @Test fun countingStopsAtMidnightAndStartsAFreshDay() {
        val clock = AClock(1_750_000_000_000L)           // 2025-06-15 15:06 UTC
        val e = setup(limit = 10, clock = clock)
        repeat(10) { e.appTick("a.x", "X", 60_000, ENV) }
        assertEquals("limit", e.checkApp("a.x", ENV).code)
        clock.advanceMin(9 * 60)                          // 00:06 next day
        assertTrue(e.checkApp("a.x", ENV).allowed, "a new day, a new quota")
        assertEquals(0L, e.appUsedMs("c1", "a.x"))
    }

    @Test fun pinGrantOpensAnAppForTheSessionLength() {
        val clock = AClock()
        val e = setup(clock = clock) { it.copy(rules = mapOf("c1" to listOf(AppRule("a.pin", AppState.PIN)))) }
        assertEquals("apppin", e.checkApp("a.pin", ENV).code)
        e.grantApp("a.pin")
        assertTrue(e.checkApp("a.pin", ENV).allowed)
        clock.advanceMin(31)
        assertEquals("apppin", e.checkApp("a.pin", ENV).code, "the grant expires")
    }

    @Test fun baselineThenNewAppDetectionAndReview() {
        val events = mutableListOf<ParentalEvent>()
        val e = eng(); e.createPin("4821")
        e.edit { it.copy(enabled = true, profiles = listOf(ChildProfile("c1", "Léa")), activeProfile = "c1") }
        e.onEvent = { events += it }
        e.editApps { it.copy(supervise = true, newApp = NewAppDefault.BLOCK) }
        val base = listOf(InstalledApp("a.one", "Un"), InstalledApp("a.two", "Deux"))
        assertTrue(e.syncInstalled(base).isEmpty(), "first sync = baseline, nothing is new on setup day")
        assertTrue(events.isEmpty())
        assertTrue(e.checkApp("a.one", ENV).allowed)
        val fresh = e.syncInstalled(base + InstalledApp("a.three", "Trois\u0007"))
        assertEquals(listOf("a.three"), fresh.map { it.pkg })
        assertEquals("Trois", fresh[0].label, "control characters are removed")
        assertEquals(1, events.filterIsInstance<ParentalEvent.NewAppInstalled>().size)
        assertEquals("newapp", e.checkApp("a.three", ENV).code, "blocked until reviewed")
        assertTrue(e.syncInstalled(base + InstalledApp("a.three", "Trois")).isEmpty(), "reported once")
        // review: a rule (or "seen") makes it known
        e.editApps { it.copy(known = it.known + "a.three") }
        assertTrue(e.appSettings().news.isEmpty())
        assertTrue(e.checkApp("a.three", ENV).allowed)
    }

    @Test fun blockedAttemptIsLoggedOnceAndAlertedAndDoesNotCountTime() {
        val events = mutableListOf<ParentalEvent>()
        val e = setup { it.copy(rules = mapOf("c1" to listOf(AppRule("a.x", AppState.BLOCKED)))) }
        e.onEvent = { events += it }
        val r1 = e.appTick("a.x", "Jeu X", 15_000, ENV)
        val r2 = e.appTick("a.x", "Jeu X", 15_000, ENV)
        assertNotNull(r1.blockReason); assertNotNull(r2.blockReason)
        assertEquals(0L, e.appUsedMs("c1", "a.x"))
        val blocked = (e.report(1)["blocked"] as List<*>)
        assertEquals(1, blocked.size, "the same refusal inside a minute is logged once")
        assertTrue(events.any { it is ParentalEvent.AppBlocked && it.pkg == "a.x" })
    }

    @Test fun limitReachedAlertIsSentOncePerDay() {
        val events = mutableListOf<ParentalEvent>()
        val e = setup(limit = 1)
        e.onEvent = { events += it }
        e.appTick("a.x", "X", 60_000, ENV); e.appTick("a.x", "X", 15_000, ENV); e.appTick("a.x", "X", 15_000, ENV)
        assertEquals(1, events.filterIsInstance<ParentalEvent.LimitReached>().size)
    }

    @Test fun reportAggregatesPerAppAndKeepsOldFields() {
        val e = setup()
        e.appTick("a.video", "Vidéo", 5 * 60_000, ENV); e.appTick("a.game", "Jeu", 2 * 60_000, ENV); e.appTick("a.video", "Vidéo", 3 * 60_000, ENV)
        e.tick(UseKind.GAMES, 4 * 60_000)
        val rep = e.report(7)
        @Suppress("UNCHECKED_CAST") val prof = ((rep["days"] as List<Map<String, Any?>>)[0]["profiles"] as List<Map<String, Any?>>)[0]
        assertEquals(10L, prof["apps"]); assertEquals(4L, prof["games"]); assertEquals(0L, prof["play"])
        @Suppress("UNCHECKED_CAST") val by = prof["byApp"] as List<Map<String, Any?>>
        assertEquals(listOf("a.video" to 8L, "a.game" to 2L), by.map { it["pkg"] as String to it["min"] as Long })
        assertEquals("Vidéo", by[0]["label"])
        assertNotNull(rep["supervision"]); assertNotNull(rep["newApps"]); assertNotNull(rep["tamper"])
        // the whole report is valid strict JSON
        JsonLite.obj(JsonLite.write(rep))
    }

    @Test fun tamperIsDetectedWhenActiveBecomesUnauthorizedAndAlsoAfterAReboot() {
        val events = mutableListOf<ParentalEvent>()
        val kv = MemoryKv()
        var e = setup(store = kv)
        e.onEvent = { events += it }
        e.reportSupervision(SupervisionInfo(SupervisionState.ACTIVE, "usage"))
        assertTrue(events.isEmpty())
        e.reportSupervision(SupervisionInfo(SupervisionState.NOT_AUTHORIZED, "none", detail = "accès aux statistiques retiré"))
        assertEquals(1, events.filterIsInstance<ParentalEvent.Tamper>().size)
        assertTrue((e.report(1)["tamper"] as List<*>).isNotEmpty())
        e.reportSupervision(SupervisionInfo(SupervisionState.NOT_AUTHORIZED))
        assertEquals(1, events.size, "no repeat while it stays revoked")
        // reboot: new engine on the same store, access revoked while the TV was off
        e.reportSupervision(SupervisionInfo(SupervisionState.ACTIVE, "usage"))
        e = ParentalEngine(kv, { 1_750_000_000_000L }, { ZoneId.of("UTC") }, PinHasher(iterations = 1000)); e.onEvent = { events += it }
        e.reportSupervision(SupervisionInfo(SupervisionState.NOT_AUTHORIZED))
        assertEquals(2, events.filterIsInstance<ParentalEvent.Tamper>().size)
    }

    @Test fun noTamperAlertWhenSupervisionIsOffOrWasNeverActive() {
        val events = mutableListOf<ParentalEvent>()
        val e = setup(); e.onEvent = { events += it }
        e.reportSupervision(SupervisionInfo(SupervisionState.NOT_AUTHORIZED))
        assertTrue(events.isEmpty(), "never active: just a status")
        e.reportSupervision(SupervisionInfo(SupervisionState.ACTIVE)); e.editApps { it.copy(supervise = false) }
        e.reportSupervision(SupervisionInfo(SupervisionState.NOT_AUTHORIZED))
        assertTrue(events.isEmpty(), "the parent switched it off: not tampering")
    }

    @Test fun supervisionStateNeverClaimsMoreThanIsWorking() {
        fun st(sup: Boolean = true, usage: Boolean = false, exists: Boolean = true, a11y: Boolean = false, fresh: Boolean = true) =
            SupervisionState.compute(sup, usage, exists, a11y, fresh)
        assertEquals(SupervisionState.OFF, st(sup = false, usage = true))
        assertEquals(SupervisionState.ACTIVE, st(usage = true))
        assertEquals(SupervisionState.ACTIVE, st(a11y = true))
        assertEquals(SupervisionState.NOT_AUTHORIZED, st())
        assertEquals(SupervisionState.UNAVAILABLE, st(exists = false))
        assertEquals(SupervisionState.NOT_AUTHORIZED, st(usage = true, fresh = false), "a dead poller is not « active »")
        assertEquals(SupervisionState.OFF.label, "Surveillance de toute la TV : désactivée")
        assertEquals("Surveillance de toute la TV : active", SupervisionState.ACTIVE.label)
        assertTrue(SupervisionState.NOT_AUTHORIZED.label.endsWith("non autorisée"))
        assertTrue(SupervisionState.UNAVAILABLE.label.endsWith("indisponible"))
    }

    @Test fun statusCarriesSupervisionAdditively() {
        val e = setup()
        e.supervisionProbe = { SupervisionInfo(SupervisionState.ACTIVE, "usage") }
        @Suppress("UNCHECKED_CAST") val sup = e.statusMap()["supervision"] as Map<String, Any?>
        assertEquals("active", sup["state"])
        assertTrue(e.statusMap().containsKey("pinSet"), "old fields still there")
    }
}

class ParentalForegroundTrackerTest {
    @Test fun followsResumedAndPaused() {
        val t = ForegroundTracker()
        assertNull(t.current)
        assertEquals("a", t.feed(listOf(FgEvent("a", true, 1))))
        // B resumes, then A pauses (late): B stays in front
        assertEquals("b", t.feed(listOf(FgEvent("a", false, 3), FgEvent("b", true, 2))))
        assertEquals("b", t.current)
        assertNull(t.feed(listOf(FgEvent("b", false, 5))))
    }

    @Test fun ignoresOldEventsAndResets() {
        val t = ForegroundTracker()
        t.feed(listOf(FgEvent("a", true, 10)))
        assertEquals("a", t.feed(listOf(FgEvent("b", true, 5))), "an event older than the last seen one is ignored")
        t.reset(); assertNull(t.current)
    }
}
