package castbridge.core.owner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivationScreenStateTest {
    private val now = 1_000_000_000L
    private val day = 86_400_000L
    private val S = ActivationScreenState
    private fun info(r: Boolean, l: Boolean, t: Boolean, lb: String = "", e: Long? = null) = ActivationScreenState.Info(r, l, t, lb, e)

    @Test fun trialSaysEssaiWithRemainingTimeAndUpgradeHint() {
        val v = S.view(info(true, false, true, "essai", now + 12 * day), null, now)
        assertEquals(ActivationScreenState.Kind.TRIAL, v.kind); assertEquals("Cette TV est en version d'essai", v.headline)
        assertTrue(v.detail!!.contains("12 jours")); assertTrue(v.detail!!.contains("Collez la clé de production pour passer en version complète"))
        assertFalse(v.headline.contains("déverrouillée"))
    }
    @Test fun lockedAndProductionAndNoLock() {
        assertEquals(ActivationScreenState.Kind.LOCKED, S.view(info(true, true, false), null, now).kind)
        val p = S.view(info(true, false, false, "Licence 1"), null, now)
        assertEquals(ActivationScreenState.Kind.PRODUCTION, p.kind); assertEquals("Version complète", p.headline); assertTrue(p.good)
        assertEquals(ActivationScreenState.Kind.NO_LOCK, S.view(info(false, false, false), null, now).kind)
    }
    @Test fun unreachable() {
        val v = S.view(null, "TV non jointe", now)
        assertEquals(ActivationScreenState.Kind.UNREACHABLE, v.kind); assertEquals("TV non jointe", v.headline)
    }
    @Test fun remainingWording() {
        assertEquals("1 jour 3 h", S.remaining(now + day + 3 * 3_600_000L, now)); assertEquals("5 h", S.remaining(now + 5 * 3_600_000L, now))
        assertEquals("moins d'une heure", S.remaining(now + 600_000L, now)); assertEquals("terminé", S.remaining(now - 1, now)); assertEquals(null, S.remaining(null, now))
    }
    @Test fun sendOutcomes() {
        val staged = S.sendOutcome(true, "Clé reçue et valide : sur la TV, appuyez sur « Valider la clé »")
        assertTrue(staged.ok && staged.staged); assertTrue(staged.text.contains("Valider la clé"))
        val done = S.sendOutcome(true, "Activée"); assertTrue(done.ok && !done.staged)
        val bad = S.sendOutcome(false, "Clé pour une autre TV"); assertFalse(bad.ok); assertEquals("Refusée par la TV : Clé pour une autre TV", bad.text)
    }
    @Test fun requestShareHasCodeAndLines() {
        val t = S.requestShare("code=AAAA-BBBB-CCCC-DDDD\nk=1\nfactor=x\n")
        assertTrue(t.startsWith("Demande de passage en production pour CastBridge-TV — code d'appareil AAAA-BBBB-CCCC-DDDD"))
        assertTrue(t.contains("k=1") && t.contains("factor=x"))
    }

    // ---- période de grâce (installation antérieure au verrou, jusqu'au 2026-11-01) : la TV tourne son API complète et `trial` est VRAI (restrictions de l'essai), mais ce n'est PAS un essai ----
    private val utc = java.time.ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = java.time.LocalDateTime.of(y, m, d, h, min).toInstant(utc).toEpochMilli()
    private fun grace(until: Long?, trial: Boolean = true) = ActivationScreenState.Info(true, false, trial, "Période de grâce : activation à fournir", null, grace = true, graceUntil = until)

    @Test fun aTvInGraceIsNeverSaidToBeATrial() {
        val until = at(2026, 11, 1)
        val v = S.view(grace(until), null, until - 12 * day, utc)
        assertEquals(ActivationScreenState.Kind.GRACE, v.kind); assertNotEquals(ActivationScreenState.Kind.TRIAL, v.kind)
        assertEquals("TV en période de grâce : activation à fournir", v.headline); assertFalse(v.good)
        val d = v.detail!!
        assertTrue(d.startsWith("Jusqu'au 01/11/2026 (restant : 12 jours)."), d)
        assertTrue("Collez la clé d'activation reçue" in d && "« Activer la TV »" in d, d)
        for (text in listOf(v.headline, d)) assertFalse("essai" in text.lowercase() || "production" in text.lowercase(), "la grâce n'est pas un essai : « $text »")
        assertEquals(ActivationScreenState.Kind.GRACE, S.view(grace(until, trial = false), null, until - 12 * day, utc).kind)       // la grâce est lue avant `trial`, qu'il soit vrai ou non
    }

    @Test fun theGraceDateIsReadInTheZoneOfTheScreen() {
        val until = at(2026, 10, 31, 23, 30)            // 23 h 30 à Greenwich = 0 h 30 le 1er novembre à Douala (UTC+1)
        assertTrue(S.view(grace(until), null, until - day, utc).detail!!.startsWith("Jusqu'au 31/10/2026 "))
        assertTrue(S.view(grace(until), null, until - day, java.time.ZoneId.of("Africa/Douala")).detail!!.startsWith("Jusqu'au 01/11/2026 "))
    }

    @Test fun graceWithoutADateStillExplainsAndAnEndedGraceNeverSaysRestantTermine() {
        val noDate = S.view(grace(null), null, now, utc)
        assertEquals(ActivationScreenState.Kind.GRACE, noDate.kind); assertFalse("Jusqu'au" in noDate.detail!!); assertTrue("« Activer la TV »" in noDate.detail!!)
        val late = S.view(grace(at(2026, 11, 1)), null, at(2026, 11, 2), utc).detail!!       // les horloges du téléphone et de la TV peuvent différer d'un jour
        assertTrue(late.startsWith("Jusqu'au 01/11/2026. "), late); assertFalse("terminé" in late)
    }

    @Test fun aTvWithoutTheGraceFieldKeepsItsOldReading() {
        // une TV 0.14.49 ou plus ancienne ne dit pas `grace` : l'écran lit exactement ce qu'il lisait avant (et le défaut du champ est faux)
        val old = ActivationScreenState.Info(true, false, true, "Essai", now + 12 * day)
        assertFalse(old.grace); assertNull(old.graceUntil)
        assertEquals(ActivationScreenState.Kind.TRIAL, S.view(old, null, now).kind)
        assertEquals(ActivationScreenState.Kind.TRIAL, S.view(ActivationScreenState.Info(true, false, true, "Essai", now + 12 * day, grace = false, graceUntil = null), null, now).kind)
        assertEquals(ActivationScreenState.Kind.PRODUCTION, S.view(info(true, false, false, "Licence 1"), null, now).kind)
        assertEquals(ActivationScreenState.Kind.LOCKED, S.view(ActivationScreenState.Info(true, true, false), null, now).kind)
        assertEquals(ActivationScreenState.Kind.NO_LOCK, S.view(ActivationScreenState.Info(false, false, false), null, now).kind)
    }

    @Test fun bothEndsOfTheWireSpeakOfGraceTheSameWay() {
        fun src(relative: String): String {
            val f = java.io.File("../$relative").takeIf { it.exists() } ?: java.io.File(relative)
            assertTrue(f.exists(), "source introuvable : $relative (le garde ne doit jamais passer en silence)")
            return f.readText()
        }
        val route = src("receiver/src/main/kotlin/castbridge/receiver/TvService.kt").lineSequence().single { """path == "/api/activation" && method == "GET"""" in it }
        assertTrue("""\"grace\":""" in route && """\"graceUntil\":""" in route, "GET /api/activation ne dit plus grace / graceUntil")
        assertTrue("GateState.Grace" in route, "grace et graceUntil viennent de l'état de la TV (GateState.Grace), pas de ActivationCenter.trial()")
        val screen = src("sender/src/main/kotlin/castbridge/sender/ActivateTvActivity.kt")
        assertTrue("""optBoolean("grace")""" in screen && """isNull("graceUntil")""" in screen && """optLong("graceUntil")""" in screen, "« Activer la TV » ne lit plus grace / graceUntil")
    }
}
