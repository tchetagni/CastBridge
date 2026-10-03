package castbridge.core.lots

import castbridge.core.owner.IssueException
import castbridge.core.owner.RentalSpec
import castbridge.core.owner.RightsSyntax
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.*

/** The pilot's issuing rules (W16-04): what the platform signs for a rental chosen by the user, and every refusal, in French. Dates are Douala time (UTC+1). */
class PilotRulesTest {
    private val catalog = BundleCatalog.parse("""{"bundles":[
        {"id":"classe-cm2","type":"classe","lots":["learn:cm2"],"title":"CM2"},
        {"id":"classe-cp","type":"classe","lots":["learn:cp"],"title":"CP","rentalDays":7},
        {"id":"classe-ce1","type":"classe","lots":["learn:ce1"],"title":"CE1"},
        {"id":"langues-fr","type":"langues","lots":["learn:lang-fr"],"title":"Français"},
        {"id":"mixte","type":"classe","lots":["learn:cm2","learn:lang-fr"],"title":"Mixte"},
        {"id":"vide","type":"classe","lots":[],"title":"Vide"},
        {"id":"inconnu-famille","type":"classe","lots":["learn:zzz"],"title":"Sans famille"}]}""")
    private val families = LotFamilies.explicit(free = setOf("learn:lang-fr"), reserved = setOf("learn:cm2", "learn:cp", "learn:ce1"))
    private val p = PilotParams(pilotStart = at("2026-10-12", 0), pilotEnd = at("2026-11-01", 23, 59, 59, 999))
    private val none = LicenseState()
    private val DAY = 24L * 3600 * 1000

    private fun at(d: String, h: Int = 12, mi: Int = 0, s: Int = 0, ms: Int = 0): Long =
        LocalDate.parse(d).atTime(h, mi, s, ms * 1_000_000).toInstant(ZoneOffset.ofHours(1)).toEpochMilli()

    private fun spec(c: Choice, day: String = "2026-10-12", bundle: String = "classe-cm2", state: LicenseState = none, params: PilotParams = p) =
        PilotRules.spec(c, bundle, catalog, at(day), state, params, families)

    private fun refusal(r: Result<RentalSpec>): String { assertTrue(r.isFailure, "should have been refused, got ${r.getOrNull()}"); assertTrue(r.exceptionOrNull() is PilotRefusal); return r.exceptionOrNull()!!.message!! }

    private fun hourly(h: Int, period: Long = at("2026-10-12"), endsDay: String = "2026-11-11", reissues: Int = 0) =
        ContractSummary("loc-classe-cm2", period, RentalUnit.HOURS, h * 60, at(endsDay), reissues, installPub = "OLD")
    private fun daily(period: Long = at("2026-10-12"), endsDay: String = "2026-11-11", reissues: Int = 0) =
        ContractSummary("loc-classe-cm2", period, RentalUnit.DAYS, 0, at(endsDay), reissues, installPub = "OLD")
    private fun reissue(c: ContractSummary, used: Int, now: Long = at("2026-10-20"), newPub: String? = "NEW", state: LicenseState = none, params: PilotParams = p, bundleCatalog: BundleCatalog = catalog) =
        PilotRules.reissue(c, used, now, newPub, state, params, bundleCatalog, families)

    // ---- the three choices ------------------------------------------------------------------------------------------------------------------------------------------------
    @Test fun defaultChoiceIsThirtyDaysWithoutBudget() {
        val s = spec(Choice.Default).getOrThrow()
        assertEquals(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 30, 0, 0, 3, null), s)
    }

    @Test fun sevenDaysIsValidityOnly() {
        val s = spec(Choice.Days(7)).getOrThrow()
        assertEquals(7, s.days); assertEquals(0, s.maxUsageMinutes); assertEquals(3, s.maxConcurrent); assertNull(s.period)
    }

    @Test fun twelveHoursIsBudgetWithSafetyBound() {
        val s = spec(Choice.Hours(12), day = "2026-10-25").getOrThrow()
        assertEquals(21, s.days, "safety bound: 25/10 -> 15/11 = 21 days"); assertEquals(720, s.maxUsageMinutes); assertEquals(3, s.maxConcurrent)
        assertEquals(30, spec(Choice.Hours(12), day = "2026-10-12").getOrThrow().days)
        assertEquals(14, spec(Choice.Hours(1), day = "2026-11-01").getOrThrow().days, "last day of the pilot: 14 days of safety")
        assertEquals(30, PilotRules.validityDays(Choice.Hours(3), at("2026-10-12"), p)); assertEquals(21, PilotRules.validityDays(Choice.Hours(3), at("2026-10-25"), p))
        assertEquals(30, PilotRules.validityDays(Choice.Default, at("2026-10-25"), p), "days and default are honoured in full")
        assertEquals(7, PilotRules.validityDays(Choice.Default, at("2026-10-25"), p, bundleRentalDays = 7))
    }

    @Test fun neverEndsAfterTheAbsoluteBounds() {
        for (day in listOf("2026-10-12", "2026-10-25", "2026-11-01")) {
            val i = at(day)
            val h = spec(Choice.Hours(96), day = day).getOrThrow()
            assertTrue(i + h.days * DAY <= at("2026-11-15", 23, 59, 59, 999), "hours before 15/11 whatever the day ($day)")
            val d = spec(Choice.Days(14), day = day).getOrThrow(); val x = spec(Choice.Default, day = day).getOrThrow()
            assertTrue(i + maxOf(d.days, x.days) * DAY <= at("2026-12-01", 23, 59, 59, 999), "days and default before 01/12 ($day)")
        }
    }

    @Test fun hoursAboveCapRefused() {
        assertTrue(spec(Choice.Hours(96)).isSuccess)
        assertTrue(refusal(spec(Choice.Hours(97))).contains("96"))
        assertTrue(spec(Choice.Hours(0)).isFailure); assertTrue(spec(Choice.Hours(-3)).isFailure)
    }

    @Test fun daysAboveMaxRefused() {
        assertTrue(spec(Choice.Days(30)).isSuccess)
        assertTrue(refusal(spec(Choice.Days(31))).contains("30"))
        assertTrue(spec(Choice.Days(0)).isFailure)
    }

    @Test fun defaultTakesBundleRentalDaysWhenPresent() {
        assertEquals(7, spec(Choice.Default, bundle = "classe-cp").getOrThrow().days)
        assertTrue(spec(Choice.Days(7), bundle = "classe-cp").isSuccess)
        assertTrue(refusal(spec(Choice.Days(14), bundle = "classe-cp")).contains("7"), "steps are bounded by the bundle's rentalDays")
    }

    // ---- extension: same unit, 96 h and 30 d ceilings ---------------------------------------------------------------------------------------------------------------------
    private fun extend(c: Choice, existing: ContractSummary, day: String = "2026-10-14", state: LicenseState = LicenseState(listOf(existing)), params: PilotParams = p, bundle: String = "classe-cm2") =
        PilotRules.extend(c, existing, bundle, catalog, at(day), state, params, families)

    @Test fun extensionKeepsUnitAndCapsAtNinetySixHours() {
        val ok = extend(Choice.Hours(36), hourly(60)).getOrThrow()      // 60 + 36 = 96: allowed
        assertEquals(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 1, 2160, 0, 3, at("2026-10-12")), ok, "renewal line: same period, hours added, one day (the engine adds it to the end)")
        val m = refusal(extend(Choice.Hours(60), hourly(60)))
        assertTrue(m.contains("a déjà") && m.contains("60 h") && m.contains("96"), m)
        assertTrue(extend(Choice.Hours(37), hourly(60)).isFailure, "one hour over 96")
        assertTrue(refusal(extend(Choice.Hours(1), hourly(96))).contains("96"))
    }

    @Test fun hourlyExtensionIsBoundedByTheSixteenthOnceNotByTheFifteenth() {
        val e15 = at("2026-11-15", 23, 59, 59, 999); val e16 = at("2026-11-16", 23, 59, 59, 999)
        // a rental issued from 16/10 to 01/11 ends on the 15th at the hour of issue: its (single) extension MUST work
        for (issued in listOf("2026-10-16", "2026-10-20", "2026-10-31", "2026-11-01")) {
            val first = spec(Choice.Hours(3), day = issued).getOrThrow()
            val end0 = at(issued) + first.days * DAY
            assertTrue(end0 <= e15, "the initial issue stays bounded by the 15th ($issued)")
            val c = ContractSummary("loc-classe-cm2", at(issued), RentalUnit.HOURS, 180, end0, installPub = "OLD")
            val ext = extend(Choice.Hours(1), c, day = issued).getOrThrow()
            assertEquals(1, ext.days); assertTrue(maxOf(end0, at(issued)) + ext.days * DAY <= e16, "merged end <= 16/11 23:59:59 ($issued)")
            if (end0 > at("2026-11-14")) {      // the extension used the one day of tolerance: a second one must not push further
                val c2 = c.copy(endsAt = end0 + DAY, maxUsageMinutes = 240)
                assertTrue(refusal(extend(Choice.Hours(1), c2, day = issued)).contains("16/11"), "second extension refused ($issued)")
            }
        }
        // exact edges of the merged end
        val edge = ContractSummary("loc-classe-cm2", at("2026-11-01"), RentalUnit.HOURS, 180, e15 , installPub = "OLD")
        assertTrue(extend(Choice.Hours(1), edge, day = "2026-11-01").isSuccess, "15/11 23:59:59.999 + 1 day = the 16th's last millisecond: allowed")
        assertTrue(extend(Choice.Hours(1), edge.copy(endsAt = e15 + 1), day = "2026-11-01").isFailure, "one millisecond more: refused")
        assertTrue(refusal(extend(Choice.Hours(1), edge.copy(endsAt = e15 + 1), day = "2026-11-01")).contains("16/11"))
        // early rental: many extensions until the 16th, never beyond
        var c = hourly(1); var total = 1; var n = 0
        while (true) { val r = extend(Choice.Hours(1), c, day = "2026-10-14"); if (r.isFailure) break; n++; total++; c = c.copy(maxUsageMinutes = total * 60, endsAt = maxOf(c.endsAt, at("2026-10-14")) + DAY) }
        assertTrue(c.endsAt <= e16 && n in 1..96, "stopped by the 16/11 or by 96 h (n=$n)")
    }

    @Test fun initialHourlyIssueIsStillBoundedByTheFifteenth() {
        val r = spec(Choice.Hours(1), day = "2026-11-01").getOrThrow()
        assertTrue(at("2026-11-01") + r.days * DAY <= at("2026-11-15", 23, 59, 59, 999))
        assertEquals(14, r.days)
    }

    @Test fun windowEdgesAreExact() {
        fun s(ms: Long) = PilotRules.spec(Choice.Default, "classe-cm2", catalog, ms, none, p, families)
        assertTrue(s(at("2026-10-12", 0)).isSuccess, "12/10 00:00 is in")
        assertTrue(refusal(s(at("2026-10-12", 0) - 1)).contains("pas commencé"), "11/10 23:59:59.999 is out")
        assertTrue(s(at("2026-11-01", 23, 59, 59, 999)).isSuccess, "01/11 23:59:59.999 is in")
        assertTrue(refusal(s(at("2026-11-02", 0))).contains("terminé"), "02/11 00:00 is out")
        assertTrue(extend(Choice.Hours(1), hourly(3), day = "2026-10-16").isSuccess, "an extension after 15/10 works")
        assertTrue(extend(Choice.Hours(1), hourly(3), day = "2026-10-31").isSuccess)
    }

    @Test fun extensionWithOtherUnitRefused() {
        assertTrue(refusal(extend(Choice.Days(3), hourly(6))).contains("ne mélange pas"))
        assertTrue(refusal(extend(Choice.Default, hourly(6))).contains("ne mélange pas"))
        assertTrue(refusal(extend(Choice.Hours(3), daily())).contains("ne mélange pas"))
    }

    @Test fun daysExtensionKeepsValidityAtThirtyDays() {
        val c = daily(endsDay = "2026-10-20")      // at 14/10 12:00: 6 days left
        val ok = extend(Choice.Days(14), c).getOrThrow()
        assertEquals(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 14, 0, 0, 3, at("2026-10-12")), ok)
        assertTrue(extend(Choice.Days(24), c).isSuccess, "6 + 24 = 30")
        assertTrue(refusal(extend(Choice.Days(25), c)).contains("30"))
        assertTrue(extend(Choice.Default, c).isFailure, "30 more days on top of 6 left")
    }

    @Test fun extensionNeedsALiveContractOfTheSameBundleAndPeriod() {
        assertTrue(refusal(extend(Choice.Hours(1), hourly(6, endsDay = "2026-10-13"))).contains("terminée"), "ended on the 13th")
        assertTrue(refusal(extend(Choice.Hours(1), hourly(6).copy(endedAt = at("2026-10-13")))).contains("terminée"), "usage budget consumed")
        assertTrue(extend(Choice.Hours(1), hourly(6).copy(product = "loc-classe-cp")).isFailure, "another bundle's contract")
        assertTrue(extend(Choice.Hours(1), hourly(6, period = at("2026-10-20"))).isFailure, "a period in the future")
        assertTrue(extend(Choice.Hours(1), ContractSummary("essai", at("2026-10-12"), RentalUnit.TRIAL, 720, at("2026-10-14"))).isFailure)
        assertTrue(extend(Choice.Hours(1), ContractSummary("loc-classe-cm2", at("2026-10-12"), RentalUnit.HOURS, 0, at("2026-11-11"))).isFailure, "inconsistent summary")
    }

    @Test fun extensionCountsInTheWeeklyQuota() {
        val c = hourly(6)
        assertTrue(extend(Choice.Hours(1), c, state = LicenseState(listOf(c), hoursIssuedLast7d = 191)).isSuccess)
        assertTrue(extend(Choice.Hours(1), c, state = LicenseState(listOf(c), hoursIssuedLast7d = 192)).isFailure)
    }

    // ---- limits of a licence ----------------------------------------------------------------------------------------------------------------------------------------------
    @Test fun afterPilotEndRefused() {
        assertTrue(spec(Choice.Hours(1), day = "2026-11-01").isSuccess)
        assertTrue(refusal(spec(Choice.Default, day = "2026-11-02")).contains("terminé"))
        assertTrue(refusal(spec(Choice.Default, day = "2026-10-11")).contains("pas commencé"))
        assertTrue(refusal(extend(Choice.Hours(1), hourly(3), day = "2026-11-02")).contains("terminé"))
        assertTrue(reissue(hourly(6), 60, at("2026-11-02")).isSuccess, "a reissue gives no new right: allowed after the pilot, until the original end")
        assertTrue(reissue(hourly(6, endsDay = "2026-11-15"), 60, at("2026-11-16")).isFailure, "but never past the original end")
    }

    @Test fun fourthActiveContractRefused() {
        fun other(b: String) = ContractSummary("loc-$b", at("2026-10-12"), RentalUnit.DAYS, 0, at("2026-11-11"))
        val three = LicenseState(listOf(other("a"), other("b"), other("c")))
        assertTrue(refusal(spec(Choice.Default, state = three)).contains("3"))
        val twoAndAnEnded = LicenseState(listOf(other("a"), other("b"), other("c").copy(endedAt = at("2026-10-13")), other("d").copy(endsAt = at("2026-10-13"))))
        assertTrue(spec(Choice.Default, day = "2026-10-14", state = twoAndAnEnded).isSuccess, "ended or past contracts do not count")
    }

    @Test fun oneContractPerBundleAtATime() {
        val st = LicenseState(listOf(daily()))
        assertTrue(refusal(spec(Choice.Hours(3), state = st)).contains("prolong"))
        assertTrue(spec(Choice.Hours(3), bundle = "classe-ce1", state = st).isSuccess)
    }

    @Test fun weeklyQuotaRefused() {
        assertTrue(spec(Choice.Hours(1), state = LicenseState(hoursIssuedLast7d = 191)).isSuccess)
        assertTrue(refusal(spec(Choice.Hours(1), state = LicenseState(hoursIssuedLast7d = 192))).contains("192"))
        assertTrue(spec(Choice.Days(7), state = LicenseState(hoursIssuedLast7d = 192)).isSuccess, "the quota is for hours only")
        assertTrue(spec(Choice.Hours(96), params = p.copy(weeklyQuotaHours = 0), state = LicenseState(hoursIssuedLast7d = 5000)).isSuccess, "0 = no quota")
    }

    @Test fun cooldownRefusesARentalTooSoonAfterTheEnd() {
        val ended = ContractSummary("loc-classe-cm2", at("2026-10-12"), RentalUnit.HOURS, 60, at("2026-11-11"), endedAt = at("2026-10-14", 12, 0))
        val cd = p.copy(cooldownMin = 60)
        val m = refusal(spec(Choice.Hours(1), day = "2026-10-14", state = LicenseState(listOf(ended)), params = cd))
        assertTrue(m.contains("13:00") && m.contains("14/10"), m)
        assertTrue(PilotRules.spec(Choice.Hours(1), "classe-cm2", catalog, at("2026-10-14", 13, 1), LicenseState(listOf(ended)), cd, families).isSuccess)
        assertTrue(spec(Choice.Hours(1), day = "2026-10-14", state = LicenseState(listOf(ended))).isSuccess, "cooldownMin = 0: the quota is enough")
    }

    // ---- never a free bundle ----------------------------------------------------------------------------------------------------------------------------------------------
    @Test fun freeBundleRefused() {
        for (c in listOf(Choice.Default, Choice.Days(7), Choice.Hours(3))) assertTrue(refusal(spec(c, bundle = "langues-fr")).contains("libre"), "free bundle with $c")
        assertTrue(spec(Choice.Default, bundle = "mixte").isFailure, "a bundle holding a free lot")
        assertTrue(spec(Choice.Default, bundle = "inconnu-famille").isFailure, "unknown family: fail closed")
        assertTrue(spec(Choice.Default, bundle = "vide").isFailure)
        assertTrue(refusal(spec(Choice.Default, bundle = "nope")).contains("inconnu"))
        assertTrue(extend(Choice.Hours(1), hourly(3).copy(product = "loc-langues-fr"), bundle = "langues-fr").isFailure)
        assertTrue(spec(Choice.Default, bundle = "langues-fr", params = p.copy(userChosen = false)).isFailure, "also with the exact-duration mode")
    }

    // ---- userChosen = 0: the exact duration of the catalogue ----------------------------------------------------------------------------------------------------------
    @Test fun withoutUserChoiceTheCatalogueDurationIsExact() {
        val off = p.copy(userChosen = false)
        assertEquals(7, spec(Choice.Default, bundle = "classe-cp", params = off).getOrThrow().days)
        assertEquals(30, spec(Choice.Default, params = off).getOrThrow().days)
        assertEquals(0, spec(Choice.Default, params = off).getOrThrow().maxUsageMinutes)
        assertTrue(spec(Choice.Days(7), params = off).isFailure); assertTrue(spec(Choice.Hours(3), params = off).isFailure)
        assertTrue(spec(Choice.Default, day = "2027-03-01", params = off).isSuccess, "no pilot window outside the pilot")
        assertTrue(extend(Choice.Days(7), daily(), params = off).isFailure)
        assertTrue(reissue(daily(), 0, at("2026-10-14"), params = off).isFailure)
    }

    // ---- reissue after a reinstall ------------------------------------------------------------------------------------------------------------------------------------------
    @Test fun reissueGivesTheRemainderAtMostThreeTimes() {
        val c = hourly(12)      // period 12/10, ends 11/11 12:00
        val r = reissue(c, 300).getOrThrow()
        assertEquals(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 22, 420, 0, 3, at("2026-10-12")), r, "720 - 300 minutes, same period, 22 whole days left (never later than the original end)")
        assertTrue(at("2026-10-20") + r.days * DAY <= c.endsAt)
        assertTrue(reissue(hourly(12, reissues = 2), 300).isSuccess)
        assertTrue(refusal(reissue(hourly(12, reissues = 3), 300)).contains("3"))
        assertTrue(refusal(reissue(c, 720)).contains("épuisées"))
        assertTrue(reissue(c, 9999).isFailure)
        assertTrue(reissue(c, 0, at("2026-11-12")).isFailure, "a finished contract is not reissued")
        assertTrue(reissue(c, -1).isFailure)
    }

    @Test fun reissueOfADaysRentalNeverGivesMoreThanTheOriginalEnd() {
        val c = daily(endsDay = "2026-11-11")      // ends 11/11 12:00
        val r = reissue(c, 0, at("2026-10-20", 18)).getOrThrow()
        assertEquals(0, r.maxUsageMinutes); assertEquals(at("2026-10-12"), r.period)
        assertEquals(21, r.days, "floor(21.75): never to the user's benefit beyond the original end")
        assertTrue(at("2026-10-20", 18) + r.days * DAY <= c.endsAt)
        assertTrue(reissue(daily(endsDay = "2026-11-11", reissues = 3), 0).isFailure)
        assertTrue(refusal(reissue(daily(endsDay = "2026-10-20"), 0, at("2026-10-20", 8))).contains("moins d'un jour"), "less than a day left: a line carries one day at least")
    }

    @Test fun reissueNeedsAnotherInstallationThanTheOriginalOne() {
        val c = hourly(12)
        assertTrue(refusal(reissue(c, 300, newPub = "OLD")).contains("Même installation"),"the TV still has the old activation: it would merge into a double end and give the usage back")
        assertTrue(refusal(reissue(c, 300, newPub = null)).contains("clé d'installation"))
        assertTrue(refusal(reissue(c, 300, newPub = " ")).contains("clé d'installation"))
        assertTrue(refusal(reissue(c.copy(installPub = null), 300)).contains("d'origine"), "unknown origin installation: refused, never guessed")
        assertTrue(refusal(reissue(daily(), 0, newPub = "OLD")).contains("Même installation"))
        assertTrue(reissue(c, 300, newPub = "NEW").isSuccess)
    }

    @Test fun reissueCountsInTheQuotaAndIsCappedAtNinetySixHours() {
        val c = hourly(12)      // 12 h granted, 5 h used: 7 h given again
        assertTrue(reissue(c, 300, state = LicenseState(listOf(c), hoursIssuedLast7d = 185)).isSuccess, "185 + 7 = 192")
        assertTrue(refusal(reissue(c, 300, state = LicenseState(listOf(c), hoursIssuedLast7d = 186))).contains("192"), "186 + 7 > 192")
        assertTrue(reissue(c, 300, state = LicenseState(listOf(c), hoursIssuedLast7d = 5000), params = p.copy(weeklyQuotaHours = 0)).isSuccess, "0 = no quota")
        val wrong = ContractSummary("loc-classe-cm2", at("2026-10-12"), RentalUnit.HOURS, 200 * 60, at("2026-11-11"), installPub = "OLD")      // a summary that is wrong: never more than 96 h
        assertEquals(96 * 60, reissue(wrong, 0).getOrThrow().maxUsageMinutes)
    }

    @Test fun reissueOfAFreeOrUnknownBundleIsRefused() {
        assertTrue(refusal(reissue(hourly(3).copy(product = "loc-langues-fr"), 0)).contains("libre"))
        assertTrue(reissue(hourly(3).copy(product = "loc-nope"), 0).isFailure)
        assertTrue(reissue(hourly(3).copy(product = "essai"), 0).isFailure)
    }

    // ---- Langues is never rented, whatever the families say ---------------------------------------------------------------------------------------------------------------
    @Test fun languagesBundlesAreRefusedWhateverTheCallerSaysAboutFamilies() {
        val sloppy = LotFamilies.explicit(free = emptySet(), reserved = setOf("learn:cm2", "learn:cp", "learn:ce1", "learn:lang-fr", "learn:lg"))      // what Cli.kt does: reserved = all - free, and the free list is wrong
        val cat = BundleCatalog.parse("""{"bundles":[
            {"id":"langues-fr","type":"langues","lots":["learn:lang-fr"],"title":"Français"},
            {"id":"xx","type":"Langues","lots":["learn:lg"],"title":"Type seul"},
            {"id":"langues-bis","type":"classe","lots":["learn:lg"],"title":"Préfixe seul"},
            {"id":"liste","type":"classe","lots":["learn:lg"],"title":"Listé"},
            {"id":"classe-cm2","type":"classe","lots":["learn:cm2"],"title":"CM2"}]}""")
        for (b in listOf("langues-fr", "xx", "langues-bis")) {
            assertTrue(refusal(PilotRules.spec(Choice.Default, b, cat, at("2026-10-14"), none, p, sloppy)).contains("Langues"), "$b")
            assertTrue(PilotRules.spec(Choice.Hours(3), b, cat, at("2026-10-14"), none, p.copy(userChosen = false), sloppy).isFailure, "$b without user choice")
            assertTrue(PilotRules.extend(Choice.Hours(1), hourly(3).copy(product = "loc-$b"), b, cat, at("2026-10-14"), none, p, sloppy).isFailure, "$b extend")
        }
        val listed = p.copy(freeBundles = setOf("liste"))
        assertTrue(refusal(PilotRules.spec(Choice.Default, "liste", cat, at("2026-10-14"), none, listed, sloppy)).contains("libre"))
        assertTrue(PilotRules.spec(Choice.Default, "liste", cat, at("2026-10-14"), none, p, sloppy).isSuccess, "only the list (or the type/prefix) closes it")
        assertTrue(PilotRules.spec(Choice.Default, "classe-cm2", cat, at("2026-10-14"), none, listed, sloppy).isSuccess)
    }

    @Test fun freeBundlesKeyIsReadAndUnknownKeysAreRefused() {
        val base = """"pilot.start":"2026-10-12","pilot.end":"2026-11-01""""
        assertEquals(setOf("a", "b-c"), PilotParams.parse("{$base,\"freeBundles\":[\"a\",\"b-c\"]}").freeBundles)
        assertEquals(setOf("a", "b-c"), PilotParams.parse("{$base,\"freeBundles\":\"a, b-c\"}").freeBundles)
        assertEquals(emptySet(), PilotParams.parse("{$base}").freeBundles)
        val m = assertFailsWith<IllegalArgumentException> { PilotParams.parse("{$base,\"freeBundle\":[\"a\"]}") }.message!!      // a typo must never silently open Langues
        assertTrue(m.contains("freeBundle") && m.contains("inconnue"), m)
        assertFailsWith<IllegalArgumentException> { PilotParams.parse("{$base,\"freeBundles\":[\"A B\"]}") }
        assertFailsWith<IllegalArgumentException> { PilotParams.parse("{$base,\"freeBundles\":5}") }
    }

    @Test fun checkChosenKeepsTheProductOfTheBundle() {
        assertNotNull(RentalDurations.checkChosen(RentalSpec("loc-classe-cp", listOf("classe-cm2"), 7, 0, 0, 3), catalog, p), "product must be loc-<bundle>")
        assertNull(RentalDurations.checkChosen(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 7, 0, 0, 3), catalog, p))
    }

    // ---- parameters ------------------------------------------------------------------------------------------------------------------------------------------------------------
    @Test fun pilotExampleParsesToThePilotValues() {
        val json = javaClass.getResource("/pilot.example.json")!!.readText().replace(": ", ":")      // the example file of the pilot's settings (same names as the W12 keys)
        val q = PilotParams.parse(json)
        assertEquals(PilotParams(pilotStart = at("2026-10-12", 0), pilotEnd = at("2026-11-01", 23, 59, 59, 999)), q)
        assertEquals(PilotParams.parse(json.replace("\"2026-10-12\"", "${at("2026-10-12", 0)}").replace("\"2026-11-01\"", "${at("2026-11-01", 23, 59, 59, 999)}")), q, "milliseconds work too")
        assertEquals(PilotParams(userChosen = false, pilotStart = q.pilotStart, pilotEnd = q.pilotEnd), PilotParams.parse(json.replace("\"rental.userChosen\":1", "\"rental.userChosen\":0")))
    }

    @Test fun parametersAreBounded() {
        val base = """{"pilot.start":"2026-10-12","pilot.end":"2026-11-01""""
        fun bad(extra: String) = assertFailsWith<IllegalArgumentException>(extra) { PilotParams.parse("$base,$extra}") }
        bad("\"rental.hourly.maxUseHours\":97")      // the TV clamps at 96 h: more would silently be lost
        bad("\"rental.hourly.maxUseHours\":0"); bad("\"rental.maxDays\":61"); bad("\"rental.defaultDays\":31")
        bad("\"rental.maxConcurrent\":0"); bad("\"rental.maxConcurrent\":21"); bad("\"rental.hourly.weeklyQuotaHours\":673"); bad("\"rental.cooldownMin\":1441")
        bad("\"pilot.graceDays\":31"); bad("\"rental.hourly.validityDays\":61")
        bad("\"rental.pickerDays\":\"7,3\""); bad("\"rental.pickerDays\":\"1,31\""); bad("\"rental.pickerDays\":\"1,30\""); bad("\"rental.hourly.pickerHours\":\"1,97\"")
        bad("\"rental.pickerDays\":\"1,2,3,4,5,6,7,8,9\"")
        assertFailsWith<IllegalArgumentException> { PilotParams.parse("""{"pilot.start":"2026-11-02","pilot.end":"2026-11-01"}""") }
        assertFailsWith<IllegalArgumentException> { PilotParams.parse("""{"pilot.start":"2026-10-01","pilot.end":"2027-03-01"}""") }
        assertFailsWith<IllegalArgumentException> { PilotParams.parse("pas du json") }
        assertFailsWith<IllegalArgumentException> { PilotParams.parse("""{"pilot.start":"2026-10-12"}""") }
        val msg = assertFailsWith<IllegalArgumentException> { PilotParams.parse("$base,\"rental.maxDays\":99}") }.message!!
        assertTrue(msg.contains("rental.maxDays"), msg)
    }

    // ---- choices -------------------------------------------------------------------------------------------------------------------------------------------------------------
    @Test fun choiceParsesAndSpeaksFrench() {
        assertEquals(Choice.Default, Choice.parse("defaut")); assertEquals(Choice.Default, Choice.parse(" Défaut ")); assertEquals(Choice.Default, Choice.parse("default"))
        assertEquals(Choice.Days(7), Choice.parse("7j")); assertEquals(Choice.Hours(12), Choice.parse("12H"))
        for (b in listOf("", "0j", "-1h", "12", "h", "7 jours", "1.5h", "99999999999h", "7jj")) assertFailsWith<IllegalArgumentException>(b) { Choice.parse(b) }
        assertEquals("Sans durée précise : 30 jours", Choice.Default.label())
        assertEquals("Sans durée précise : 7 jours", Choice.Default.label(7))
        assertEquals("7 jours", Choice.Days(7).label()); assertEquals("1 jour", Choice.Days(1).label())
        assertEquals("12 heures d'utilisation", Choice.Hours(12).label()); assertEquals("1 heure d'utilisation", Choice.Hours(1).label())
    }

    @Test fun rentalChoiceSyntax() {
        assertEquals(castbridge.core.owner.RentalChoice("classe-cm2", Choice.Hours(12), null), RightsSyntax.rentalChoice("classe-cm2=12h"))
        assertEquals(castbridge.core.owner.RentalChoice("classe-cm2", Choice.Days(7), 1234L), RightsSyntax.rentalChoice(" classe-cm2 = 7j ", 1234L))
        assertEquals(Choice.Default, RightsSyntax.rentalChoice("classe-cm2=defaut").choice)
        for (b in listOf("", "classe-cm2", "=12h", "classe-cm2=", "classe-cm2=12", "Classe CM2=12h", "a,b=12h", "classe-cm2=0h", "classe-cm2=12h=3"))
            assertFailsWith<IssueException>(b) { RightsSyntax.rentalChoice(b) }
        assertTrue(assertFailsWith<IssueException> { RightsSyntax.rentalChoice("classe-cm2=douze") }.message!!.contains("choix"))
    }

    // ---- what comes out is exactly what the installed TVs parse and the engine applies ------------------------------------------------------------------------------------
    /** The line [castbridge.core.owner.RentalIssuing.right] writes for [s] (startsAt = issuing instant, period = the one to extend or the issuing instant, no grace, empty box here). */
    private fun line(s: RentalSpec, issuedAt: Long) = Right.Rental(s.productId, s.bundleIds.sorted(), issuedAt, s.period ?: issuedAt, s.days, s.graceDays * DAY, s.maxUsageMinutes, s.maxConcurrent, "")
    private fun act(vararg r: Right.Rental) = castbridge.core.owner.Activation(castbridge.core.owner.ActivationKind.PRODUCTION, castbridge.core.owner.Subject.TV, "k", 1, "n", at("2026-10-12"), at("2026-10-12"), at("2027-10-12"), "lic", "seat", 1, emptyMap(), r.toList(), "sig")

    @Test fun producedLinesKeepTenFieldsAndAreInBoundsForEveryTv() {
        val t = at("2026-10-12")
        val new = line(spec(Choice.Hours(12)).getOrThrow(), t)
        val wire = RentalLines.line(new)
        assertEquals("rental|loc-classe-cm2|classe-cm2|$t|$t|30|0|720|3|", wire)
        assertEquals(10, wire.split('|').size)
        assertEquals(new, RentalLines.parse(wire.split('|'))); assertNull(RentalLines.bounds(new))
        val days = line(spec(Choice.Days(7)).getOrThrow(), t)
        assertEquals("rental|loc-classe-cm2|classe-cm2|$t|$t|7|0|0|3|", RentalLines.line(days)); assertNull(RentalLines.bounds(days))
        val dflt = line(spec(Choice.Default).getOrThrow(), t)
        assertEquals("rental|loc-classe-cm2|classe-cm2|$t|$t|30|0|0|3|", RentalLines.line(dflt)); assertNull(RentalLines.bounds(dflt))
        val ext = line(extend(Choice.Hours(36), hourly(60)).getOrThrow(), at("2026-10-14"))
        assertEquals("rental|loc-classe-cm2|classe-cm2|${at("2026-10-14")}|$t|1|0|2160|3|", RentalLines.line(ext)); assertNull(RentalLines.bounds(ext))
        assertEquals(ext, RentalLines.parse(RentalLines.line(ext).split('|')))
    }

    @Test fun theEngineAppliesAnIssuedExtensionWithoutLoss() {
        val t = at("2026-10-12")
        val first = line(spec(Choice.Hours(60)).getOrThrow(), t)
        val c = ContractSummary("loc-classe-cm2", t, RentalUnit.HOURS, 3600, t + 30 * DAY)
        val second = line(extend(Choice.Hours(36), c).getOrThrow(), at("2026-10-14"))
        val merged = RentalEngine.contracts(listOf(act(first, second))).single()
        assertEquals(5760L, merged.maxUsageMinutes); assertEquals(emptyList(), merged.notes, "96 h exactly: nothing is clamped, nothing is ignored")
        assertEquals(t + 31 * DAY, merged.endsAt, "the renewal adds its single day to the end")
        // what the issuer refuses is exactly what the engine would have lost
        assertTrue(extend(Choice.Hours(37), c).isFailure)
        val over = RentalEngine.contracts(listOf(act(first, line(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 1, 37 * 60, 0, 3, t), at("2026-10-14"))))).single()
        assertEquals(5760L, over.maxUsageMinutes); assertTrue(over.notes.isNotEmpty(), "the engine clamps and says so; the issuer never gets there")
        // days
        val d1 = line(spec(Choice.Days(7)).getOrThrow(), t)
        val dc = ContractSummary("loc-classe-cm2", t, RentalUnit.DAYS, 0, t + 7 * DAY)
        val d2 = line(extend(Choice.Days(14), dc).getOrThrow(), at("2026-10-14"))
        val dm = RentalEngine.contracts(listOf(act(d1, d2))).single()
        assertEquals(0L, dm.maxUsageMinutes); assertEquals(RentalUnit.DAYS, dm.unit); assertEquals(t + 21 * DAY, dm.endsAt); assertEquals(emptyList(), dm.notes)
    }

    @Test fun theHardCapFollowsTheEnginesClamp() {
        assertEquals(96, PilotParams.HARD_CAP_HOURS)
        assertEquals(RentalConfig().maxUseMinutesPerContract, PilotParams.HARD_CAP_HOURS * 60L)
    }

    @Test fun existingFixedSyntaxIsUntouched() {
        val s = RightsSyntax.rental("loc-classe-cm2=classe-cm2:30")
        assertEquals(30, s.days)
        assertTrue(assertFailsWith<IssueException> { RightsSyntax.rental("x=y:0") }.message!!.contains("1 à"))
    }
}
