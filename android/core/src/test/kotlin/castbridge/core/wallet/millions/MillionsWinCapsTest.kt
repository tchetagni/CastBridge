package castbridge.core.wallet.millions

import castbridge.core.tokens.FixedWalletKey
import castbridge.core.wallet.millions.MillionsWinCaps.CapStatus
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MillionsWinCapsTest {
    private val douala = ZoneId.of("Africa/Douala")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, douala).toInstant().toEpochMilli()
    private val key = ByteArray(32) { 7 }

    private class Env { var mono = 0L; val store = InMemoryCapsStore() }

    private fun caps(env: Env, limits: WinLimits = WinLimits(3, 10, 15), def: WinDefinition = WinDefinition.GAIN_GT_STAKE, k: ByteArray? = key) =
        MillionsWinCaps(limits, def, FixedWalletKey(k), env.store) { env.mono }

    /** Une TV synchronisée le lundi 05/10/2026 à 12:00 (Douala). */
    private fun synced(env: Env = Env(), serverAt: Long = at(2026, 10, 5), limits: WinLimits = WinLimits(3, 10, 15), def: WinDefinition = WinDefinition.GAIN_GT_STAKE) =
        caps(env, limits, def).also { it.noteServerTime(serverAt) }

    private fun win(c: MillionsWinCaps, n: Int = 1, gain: Long = 1200) { repeat(n) { c.recordEnd(500, gain) } }
    private fun closed(c: MillionsWinCaps) = c.mayStart() as CapStatus.Closed

    @Test fun fourthWinOfTheDayClosesBeforeTheStake() {
        val c = synced(); win(c, 2)
        assertEquals(CapStatus.Open, c.mayStart())
        win(c, 1)
        val s = closed(c)
        assertEquals(MillionsWinCaps.Cap.DAY, s.cap)
        assertEquals("Limite atteinte : 3 parties gagnées aujourd'hui. Prochaine partie possible demain à 00:00.", s.text)
        assertEquals(at(2026, 10, 6, 0), s.reopensAtMs)
    }

    @Test fun withdrawAtStopFiveDoesNotCountForGainGtStake() {
        val c = synced(); win(c, 5, gain = 500)
        assertEquals(CapStatus.Open, c.mayStart()); assertEquals(MillionsJournalCounts(0, 0, 0), counts(c))
        c.recordEnd(500, 0); c.recordEnd(500, 499)
        assertEquals(MillionsJournalCounts(0, 0, 0), counts(c), "perdue ou sous la mise : ne compte pas")
        win(c, 1, gain = 501)
        assertEquals(MillionsJournalCounts(1, 1, 1), counts(c))
    }

    @Test fun withdrawAtStopFiveCountsForGainGtZero() {
        val c = synced(def = WinDefinition.GAIN_GT_ZERO); win(c, 3, gain = 500)
        assertEquals(MillionsJournalCounts(3, 3, 3), counts(c)); assertTrue(c.mayStart() is CapStatus.Closed)
        c.recordEnd(500, 0); assertEquals(MillionsJournalCounts(3, 3, 3), counts(c))
    }

    @Test fun gameStartedAtTwoOfThreeCanStillBeWon() {
        val c = synced(); win(c, 2)
        assertEquals(CapStatus.Open, c.mayStart())          // la mise est prise ici
        win(c, 1)                                            // ... la partie est gagnée : acceptée
        assertEquals("Parties gagnées : 3/3 aujourd'hui · 3/10 cette semaine · 3/15 ce mois", c.progressLine())
        assertTrue(c.mayStart() is CapStatus.Closed)
    }

    @Test fun progressLineText() {
        val c = synced(); win(c, 1)
        assertEquals("Parties gagnées : 1/3 aujourd'hui · 1/10 cette semaine · 1/15 ce mois", c.progressLine())
    }

    @Test fun midnightRollsTheDayWindow() {
        val env = Env(); val c = synced(env, at(2026, 10, 5, 23, 59))      // lundi 23:59
        win(c, 3); assertTrue(c.mayStart() is CapStatus.Closed)
        env.mono = 30_000; assertTrue(c.mayStart() is CapStatus.Closed, "23:59:30")
        env.mono = 61_000                                                   // mardi 00:00:01
        assertEquals(CapStatus.Open, c.mayStart())
        assertEquals("Parties gagnées : 0/3 aujourd'hui · 3/10 cette semaine · 3/15 ce mois", c.progressLine())
    }

    @Test fun sundayToMondayRollsTheWeek() {
        val env = Env(); val c = synced(env, at(2026, 10, 11, 23, 0), WinLimits(100, 10, 100))   // dimanche 11/10
        win(c, 10)
        val s = closed(c)
        assertEquals(MillionsWinCaps.Cap.WEEK, s.cap)
        assertEquals("Limite atteinte : 10 parties gagnées cette semaine. Prochaine partie possible lundi 12/10 à 00:00.", s.text)
        assertEquals(at(2026, 10, 12, 0), s.reopensAtMs)
        env.mono = 3_600_000 + 1000                                          // lundi 00:00:01
        assertEquals(CapStatus.Open, c.mayStart())
    }

    @Test fun lastDayOfMonthRollsTheMonthWindow() {
        val env = Env(); val c = synced(env, at(2026, 10, 31, 23, 30), WinLimits(100, 100, 15))
        win(c, 15)
        val s = closed(c)
        assertEquals(MillionsWinCaps.Cap.MONTH, s.cap)
        assertEquals("Limite atteinte : 15 parties gagnées ce mois-ci. Prochaine partie possible le 01/11 à 00:00.", s.text)
        env.mono = 31 * 60_000L
        assertEquals(CapStatus.Open, c.mayStart())
        assertEquals("Parties gagnées : 0/100 aujourd'hui · 15/100 cette semaine · 0/15 ce mois", c.progressLine())
    }

    @Test fun monthEndAcrossWeekIsNotConfused() {
        // 31/10/2026 est un samedi : la semaine (lun. 26/10 - dim. 01/11) déborde sur novembre, le mois non
        val env = Env(); val c = synced(env, at(2026, 10, 31, 23, 59), WinLimits(100, 10, 100)); win(c, 4)
        env.mono = 2 * 60_000L                                               // dimanche 01/11 00:01
        assertEquals("Parties gagnées : 0/100 aujourd'hui · 4/10 cette semaine · 0/100 ce mois", c.progressLine())
    }

    @Test fun mostConstrainingCapIsReported() {
        val c = synced(limits = WinLimits(3, 3, 15)); win(c, 3)           // jour et semaine atteints (lundi) : la semaine rouvre plus tard
        val s = closed(c)
        assertEquals(MillionsWinCaps.Cap.WEEK, s.cap); assertEquals(at(2026, 10, 12, 0), s.reopensAtMs)
        assertEquals("Limite atteinte : 3 parties gagnées cette semaine. Prochaine partie possible lundi 12/10 à 00:00.", s.text)
    }

    @Test fun wallClockIsNeverUsed() {
        val src = File("src/main/kotlin/castbridge/core/wallet/millions/MillionsWinCaps.kt").readText()
        for (banned in listOf("currentTimeMillis", "Instant.now", "LocalDate.now", "ZonedDateTime.now", "LocalDateTime.now", "System.nanoTime", "Clock.system"))
            assertFalse(src.contains(banned), banned)
        // sans avance du temps monotone, rien ne se rouvre, quoi qu'en dise le monde extérieur
        val env = Env(); val c = synced(env); win(c, 3)
        assertTrue(c.mayStart() is CapStatus.Closed); assertTrue(c.mayStart() is CapStatus.Closed)
    }

    @Test fun noServerTimeMeansNoReference() {
        val c = caps(Env())
        val s = closed(c)
        assertEquals(MillionsWinCaps.Cap.NO_CLOCK, s.cap)
    }

    @Test fun olderServerTimeNeverGoesBack() {
        val env = Env(); val c = synced(env, at(2026, 10, 5, 12)); win(c, 3)
        c.noteServerTime(at(2026, 10, 4, 12))            // un vieux pack rejoué : ignoré
        assertTrue(c.mayStart() is CapStatus.Closed)
        assertEquals(at(2026, 10, 5, 12), c.refNowMs())
    }

    @Test fun restartKeepsLastJudgedTime() {
        val env = Env(); val c = synced(env, at(2026, 10, 5, 23, 50)); win(c, 3)
        env.mono = 5 * 60_000L; c.mayStart()                                // jugé à 23:55 ; la TV s'éteint
        env.mono = 0                                                          // redémarrage : le temps monotone repart de zéro
        val c2 = caps(env)
        assertTrue(c2.refNowMs()!! >= at(2026, 10, 5, 23, 55), "heure de référence = max(serveur, dernière jugée)")
        assertTrue(c2.mayStart() is CapStatus.Closed)
        assertEquals(MillionsJournalCounts(3, 3, 3), counts(c2))
    }

    @Test fun tamperedSealedStateIsIgnoredInFavorOfServer() {
        val env = Env(); val c = synced(env); win(c, 3)
        val text = env.store.read()!!
        env.store.write(text.replace("|3|", "|0|"))                       // le joueur ramène le compteur à 0
        val c2 = caps(env); c2.noteServerTime(at(2026, 10, 5))
        assertEquals(MillionsJournalCounts(0, 0, 0), counts(c2), "état altéré : ignoré")
        c2.mergeServer(ServerCounts("2026-10-05", 3, "2026-10-05", 3, "2026-10", 3))
        assertTrue(c2.mayStart() is CapStatus.Closed)
    }

    @Test fun sealedWithAnotherKeyIsIgnored() {
        val env = Env(); val c = synced(env); win(c, 3)
        val other = caps(env, k = ByteArray(32) { 9 }); other.noteServerTime(at(2026, 10, 5))
        assertEquals(MillionsJournalCounts(0, 0, 0), counts(other))
        val none = caps(env, k = null); none.noteServerTime(at(2026, 10, 5))
        assertEquals(MillionsJournalCounts(0, 0, 0), counts(none))
    }

    @Test fun olderRestoredStateWithHigherServerCounterTakesMaximum() {
        val env = Env(); val c = synced(env); win(c, 1)                      // sauvegarde ancienne : 1 victoire
        val c2 = caps(env); c2.noteServerTime(at(2026, 10, 5))
        c2.mergeServer(ServerCounts("2026-10-05", 3, "2026-10-05", 5, "2026-10", 9))
        assertEquals(MillionsJournalCounts(3, 5, 9), counts(c2))
        assertTrue(c2.mayStart() is CapStatus.Closed)
        // et le local plus haut que le serveur reste
        val c3 = caps(env); c3.noteServerTime(at(2026, 10, 5))
        c3.mergeServer(ServerCounts("2026-10-05", 0, "2026-10-05", 0, "2026-10", 0))
        assertEquals(MillionsJournalCounts(3, 5, 9), counts(c3), "le maximum est conservé après persistance")
    }

    @Test fun serverCountersOfAnotherPeriodAreIgnored() {
        val c = synced()
        c.mergeServer(ServerCounts("2026-10-04", 3, "2026-09-28", 10, "2026-09", 15))      // hier, semaine passée, mois passé
        assertEquals(MillionsJournalCounts(0, 0, 0), counts(c)); assertEquals(CapStatus.Open, c.mayStart())
    }

    @Test fun serverCountersAheadOfLocalClockAreAdopted() {
        // la TV a une heure de référence en retard sur celle du serveur : le compteur du serveur, d'une période plus récente, l'emporte
        val c = synced(serverAt = at(2026, 10, 5))
        c.mergeServer(ServerCounts("2026-10-06", 2, "2026-10-05", 4, "2026-10", 6))
        assertEquals(MillionsJournalCounts(0, 4, 6), counts(c), "le jour du serveur n'est pas le jour courant de la TV")
    }

    @Test fun limitsComeFromThePack() {
        val c = synced(limits = WinLimits(1, 10, 15)); win(c, 1)
        assertTrue(c.mayStart() is CapStatus.Closed)
        c.configure(WinLimits(2, 10, 15), WinDefinition.GAIN_GT_STAKE)
        assertEquals(CapStatus.Open, c.mayStart())
    }

    @Test fun singularInTexts() {
        val c = synced(limits = WinLimits(1, 10, 15)); win(c, 1)
        assertEquals("Limite atteinte : 1 partie gagnée aujourd'hui. Prochaine partie possible demain à 00:00.", closed(c).text)
    }

    private fun counts(c: MillionsWinCaps) = c.counts().let { MillionsJournalCounts(it.day, it.week, it.month) }
}

private data class MillionsJournalCounts(val d: Int, val w: Int, val m: Int)
