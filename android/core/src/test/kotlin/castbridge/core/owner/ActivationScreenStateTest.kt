package castbridge.core.owner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
