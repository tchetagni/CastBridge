package castbridge.core.lots

import castbridge.core.owner.*
import java.time.ZoneId
import kotlin.test.*

/** W16 (w16-01): the unit of a contract (hours of use / days / trial), the 96 h clamp, the refusal of mixed units, the per-unit sentences and alerts, the key badge. Pure engine, fake clock, no signature involved. */
class RentalUnitsTest {
    private val day = 24L * 3600 * 1000
    private val t0 = 1_800_000_000_000L
    private val perUnit = RentalConfig()
    private val legacy = RentalConfig(perUnitMessages = false)
    private val fmt: (Long) -> String = { "J${(it - t0) / day}" }

    private fun rental(product: String = "loc-cm2", start: Long = t0, days: Int = 30, usage: Int = 0, period: Long = t0) =
        Right.Rental(product, listOf("classe-cm2"), start, period, days, 0L, usage, 3, "box")

    private fun act(vararg r: Right.Rental) = Activation(ActivationKind.PRODUCTION, Subject.TV, "k", 1, "n", t0, t0, t0 + 400 * day, "lic", "seat", 1, emptyMap(), r.toList(), "sig")

    private fun status(c: RentalContract, now: Long = t0, used: Long = 0, cfg: RentalConfig = perUnit, expired: Map<String, ExpiryReason> = emptyMap()) =
        RentalEngine.evaluate(listOf(c), RentalInputs(JudgedTime(now, null), mapOf(c.key to used), expired), cfg, fmt).single()

    private fun contract(vararg r: Right.Rental, cfg: RentalConfig = RentalConfig()) = RentalEngine.contracts(listOf(act(*r)), cfg).single()

    @Test fun hourlyContractIsClampedAtCap() {
        val c = contract(rental(usage = 3600), rental(usage = 3600, start = t0 + day))
        assertEquals(5760L, c.maxUsageMinutes)
        assertEquals(RentalUnit.HOURS, c.unit)
        assertEquals(5760L, RentalEngine.contracts(listOf(act(rental(usage = 3600), rental(usage = 3600, start = t0 + day)))).single().maxUsageMinutes, "default signature clamps too")
        assertEquals(7200L, contract(rental(usage = 3600), rental(usage = 3600, start = t0 + day), cfg = RentalConfig(maxUseMinutesPerContract = 0)).maxUsageMinutes, "0 = no clamp, as before")
        assertEquals(720L, contract(rental(usage = 360), rental(usage = 360, start = t0 + day)).maxUsageMinutes, "below the cap: plain sum")
        assertEquals(3600L, contract(rental(usage = 3600), rental(usage = 3600)).maxUsageMinutes, "identical lines count once")
        assertEquals(5760L, status(c, used = 0).maxUsageMinutes)
        assertEquals(5760L, status(c, used = 0).remainingUsageMinutes)
    }

    @Test fun dayContractHasNoBudget() {
        val c = contract(rental(days = 7))
        assertEquals(RentalUnit.DAYS, c.unit)
        val s = status(c, now = t0 + day)
        assertNull(s.remainingUsageMinutes); assertEquals(0L, s.maxUsageMinutes)
        assertEquals("Il vous reste 6 jours", s.message)
        assertEquals(RentalWarning.NONE, s.warning, "a 7-day rental is not alerted 6 days before its end")
        assertEquals(RentalWarning.HOURS_24, status(c, now = t0 + 6 * day + 3600_000L).warning, "last 24 h: only above 2 days")
        assertEquals(RentalWarning.HOUR_1, status(c, now = t0 + 7 * day - 3600_000L).warning)
    }

    @Test fun dayAlertsFollowTheLengthOfTheRental() {
        val one = contract(rental(days = 1)); val two = contract(rental(days = 2)); val three = contract(rental(days = 3))
        val long = contract(rental(days = 30)); val fortnight = contract(rental(days = 14))
        assertEquals(RentalWarning.NONE, status(one, now = t0).warning, "opening a 1-day rental is silent")
        assertEquals(RentalWarning.NONE, status(one, now = t0 + day / 2).warning)
        assertEquals(RentalWarning.HOUR_1, status(one, now = t0 + day - 1800_000L).warning, "the last hour always alerts")
        assertEquals(RentalWarning.NONE, status(two, now = t0 + day + 1).warning, "2 days: no last-24 h alert")
        assertEquals(RentalWarning.HOURS_24, status(three, now = t0 + 2 * day + 1).warning)
        assertEquals(RentalWarning.NONE, status(long, now = t0 + 22 * day).warning)
        assertEquals(RentalWarning.DAYS_7, status(long, now = t0 + 23 * day).warning)
        assertEquals(RentalWarning.DAYS_7, status(fortnight, now = t0 + 7 * day).warning, "14 days: the 7-day alert applies")
        assertEquals(RentalWarning.NONE, status(contract(rental(days = 13)), now = t0 + 6 * day + 1).warning, "13 days: it does not")
    }

    @Test fun oneHourRentalWarnsAtTenMinutesNotAtOpening() {
        val c = contract(rental(usage = 60))
        assertEquals(RentalWarning.NONE, status(c, used = 0).warning, "opening a 1 h rental is silent")
        assertEquals(RentalWarning.NONE, status(c, used = 40).warning, "20 min left")
        assertEquals(RentalWarning.HOURS_24, status(c, used = 45).warning, "15 min left (a quarter)")
        assertEquals(RentalWarning.HOUR_1, status(c, used = 50).warning, "10 min left")
        assertEquals(RentalWarning.HOUR_1, status(c, used = 59).warning)
        val twelve = contract(rental(usage = 720))
        assertEquals(RentalWarning.NONE, status(twelve, used = 400).warning)
        assertEquals(RentalWarning.DAYS_7, status(twelve, used = 540).warning, "180 min = 25 %")
        assertEquals(RentalWarning.HOURS_24, status(twelve, used = 660).warning, "60 min")
        assertEquals(RentalWarning.HOUR_1, status(twelve, used = 710).warning)
        assertEquals(RentalWarning.NONE, RentalEngine.usageWarningFor(null, 600))
        assertEquals(RentalWarning.NONE, status(twelve, now = t0 + 29 * day, used = 0).warning, "an hourly rental has no date alert before the safety date (W16-10's banner covers it)")
    }

    @Test fun mixedUnitsAreRefused() {
        // 6 h rented, then a "5 days, no budget" line signed by mistake: the line does not apply, the budget stays
        val c = contract(rental(usage = 360), rental(usage = 0, start = t0 + day, days = 5))
        assertEquals(360L, c.maxUsageMinutes)
        assertEquals(RentalUnit.HOURS, c.unit)
        assertEquals(t0 + 30 * day, c.endsAt, "the ignored line does not extend the contract")
        assertEquals(listOf("Une ligne de renouvellement en jours a été ignorée : on ne mélange pas les heures et les jours"), c.notes)
        val s = status(c, now = t0 + 2 * day, used = 100)
        assertEquals(260L, s.remainingUsageMinutes)
        assertEquals(c.notes, s.notes, "the cause is visible in the status")
        assertEquals(RentalState.EXPIRED, status(c, now = t0 + 2 * day, used = 360).state, "the hours still run out")
        // the other way round: hours added to a rental in days are ignored too
        val d = contract(rental(days = 7), rental(usage = 600, start = t0 + day, days = 30))
        assertEquals(RentalUnit.DAYS, d.unit); assertEquals(0L, d.maxUsageMinutes); assertEquals(t0 + 7 * day, d.endsAt)
        assertEquals(listOf("Une ligne de renouvellement en heures a été ignorée : on ne mélange pas les heures et les jours"), d.notes)
    }

    @Test fun excessOver96HoursIsExposedAndTheCapIsAnEngineRule() {
        val c = contract(rental(usage = 5400), rental(usage = 720, start = t0 + day))     // 90 h then 12 h
        assertEquals(5760L, c.maxUsageMinutes)
        assertEquals(listOf("6 h non applicables : plafond de 96 h par location"), status(c).notes)
        // pinned: a single hourly line above 96 h is cut as well (no such contract was ever issued in production)
        val big = contract(rental(usage = 6000))
        assertEquals(5760L, big.maxUsageMinutes)
        assertEquals(listOf("4 h non applicables : plafond de 96 h par location"), big.notes)
        assertTrue(contract(rental(usage = 5760)).notes.isEmpty())
        assertTrue(contract(rental(days = 30)).notes.isEmpty())
    }

    @Test fun trialWindowIsNotHourly() {
        val c = contract(rental(product = RentalLines.TRIAL_PRODUCT, days = 3, usage = 720))
        assertEquals(RentalUnit.TRIAL, c.unit)
        assertEquals("Il vous reste 3 jours (ou 12 h d'utilisation)", status(c, cfg = perUnit).message)
        assertEquals(status(c, cfg = legacy).message, status(c, cfg = perUnit).message)
        assertEquals(RentalWarning.HOUR_1, status(c, used = 700, cfg = perUnit).warning, "20 min left: the legacy usage threshold (1 h)")
        assertEquals(RentalEngine.ENDED, status(c, used = 720, cfg = perUnit).message)
    }

    @Test fun sentencesOfTheThreeUnits() {
        val h = contract(rental(usage = 720))
        assertEquals("Il vous reste 5 h 20 d'utilisation · à utiliser avant le J30", status(h, used = 400).message)
        assertEquals("Il vous reste 45 min d'utilisation · à utiliser avant le J30", status(h, used = 675).message)
        assertEquals("Il vous reste 5 h d'utilisation · à utiliser avant le J30", status(h, used = 420).message)
        assertEquals("Il vous reste 5 h 05 d'utilisation · à utiliser avant le J30", status(h, used = 415).message)
        val spent = status(h, used = 720)
        assertEquals(ExpiryReason.USAGE, spent.reason)
        assertEquals("Vos 12 heures d'utilisation sont épuisées : ce contenu n'est plus disponible. Relouer ?", spent.message)
        assertEquals("Votre 1 heure d'utilisation est épuisée : ce contenu n'est plus disponible. Relouer ?", status(contract(rental(usage = 60)), used = 60).message)
        // the date ends it with minutes left (no "test gratuit" wording: the context decides, not the engine)
        val byDate = status(h, now = t0 + 31 * day, used = 100)
        assertEquals(RentalState.EXPIRED, byDate.state); assertEquals(ExpiryReason.DATE, byDate.reason)
        assertEquals("Vos heures non utilisées ont expiré le J30. Relouer ?", byDate.message)
        assertEquals("Vos heures non utilisées ont expiré le J30. Relouer ?", status(h, used = 100, expired = mapOf(h.key to ExpiryReason.DATE)).message, "swept contract: same sentence")
        val d = contract(rental(days = 7))
        assertEquals("Il vous reste 5 jours", status(d, now = t0 + 2 * day).message)
        assertEquals("Location terminée (7 jours, jusqu'au J7) : ce contenu n'est plus disponible. Relouer ?", status(d, now = t0 + 8 * day).message)
        // grace keeps the existing sentence
        val g = contract(Right.Rental("loc-cm2", listOf("b"), t0, t0, 2, day, 0, 3, "box"))
        assertTrue(status(g, now = t0 + 2 * day + 1).message.startsWith("Location terminée : reconnectez le téléphone"))
    }

    @Test fun legacySentencesWhenPerUnitIsOffExplicitly() {
        val h = contract(rental(usage = 720))
        val s = status(h, now = t0 + day, used = 100, cfg = legacy)
        assertEquals("Il vous reste 29 jours (ou 10 h d'utilisation)", s.message)
        assertFalse(s.perUnit)
        assertEquals(RentalEngine.ENDED, status(h, used = 720, cfg = legacy).message)
    }

    @Test fun perUnitMessagesAreTheDefaultAndDatesAreInDouala() {
        assertTrue(RentalConfig().perUnitMessages)
        val nov15 = java.time.Instant.parse("2026-11-15T22:30:00Z").toEpochMilli()
        assertEquals("15/11", RentalEngine.defaultDate(nov15), "23:30 in Douala, still the 15th")
        assertEquals("16/11", RentalEngine.defaultDate(nov15 + 3600_000L), "00:30 in Douala")
    }

    @Test fun oldContractsSignatureStillCompiles() {
        assertEquals(1, RentalEngine.contracts(listOf(act(rental()))).size)
        assertEquals(5760L, RentalConfig().maxUseMinutesPerContract)
    }

    @Test fun badgeSaysTheUnit() {
        val zone = ZoneId.of("UTC")
        fun badge(vararg r: Right.Rental, now: Long, used: Long = 0, cfg: RentalConfig = perUnit): String {
            val a = act(*r); val cs = RentalEngine.contracts(listOf(a))
            val st = RentalEngine.evaluate(cs, RentalInputs(JudgedTime(now, null), cs.associate { it.key to used }), cfg, fmt)
            return KeyBadge.of(listOf(a), now, st, zone).lines.single { it.startsWith("Location") }
        }
        assertEquals("Location : 5 h 20 d'utilisation restante(s)", badge(rental(usage = 720), now = t0, used = 400))
        assertEquals("Location : 45 min d'utilisation restante(s)", badge(rental(usage = 720), now = t0, used = 675))
        assertEquals("Location : 5 jour(s) restant(s)", badge(rental(days = 7), now = t0 + 2 * day))
        assertFalse("utilisation" in badge(rental(usage = 720), now = t0, used = 400, cfg = legacy), "per-unit off: the old line")
    }

    @Test fun badgeKeepsTheFewestHoursAndNeverConvertsDays() {
        val zone = ZoneId.of("UTC")
        val a = act(rental(product = "loc-a", usage = 720, days = 5), rental(product = "loc-b", usage = 300, days = 30), rental(product = "loc-c", days = 3))
        val cs = RentalEngine.contracts(listOf(a))
        fun lines(now: Long) = KeyBadge.of(listOf(a), now, RentalEngine.evaluate(cs, RentalInputs(JudgedTime(now, null)), perUnit, fmt), zone).lines.filter { it.startsWith("Location") }
        // loc-a ends first by date, loc-b has the fewest hours: the badge shows loc-b's 5 h, and the days of loc-c apart
        assertEquals(listOf("Location : 5 h d'utilisation restante(s)", "Location : 3 jour(s) restant(s)"), lines(t0))
        // between 1 and 2 days left: "2 jour(s)", never "30 h"
        assertEquals(listOf("Location : 5 h d'utilisation restante(s)", "Location : 2 jour(s) restant(s)"), lines(t0 + day + day / 2))
        // an hourly rental in grace shows no hours left
        val g = act(Right.Rental("loc-g", listOf("b"), t0, t0, 2, 3 * day, 60, 3, "box"))
        val gs = RentalEngine.evaluate(RentalEngine.contracts(listOf(g)), RentalInputs(JudgedTime(t0 + 2 * day + 1, null)), perUnit, fmt)
        assertEquals(RentalState.GRACE, gs.single().state)
        val gl = KeyBadge.of(listOf(g), t0 + 2 * day + 1, gs, zone).lines.filter { it.startsWith("Location") }
        assertTrue(gl.none { "utilisation" in it }, gl.toString())
    }
}
