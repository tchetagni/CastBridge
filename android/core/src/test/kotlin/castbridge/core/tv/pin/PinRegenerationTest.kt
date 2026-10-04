package castbridge.core.tv.pin

import castbridge.core.tv.Pin
import castbridge.core.tv.PinGuard
import castbridge.core.tv.home.HomeEntry
import castbridge.core.tv.home.HomeGroups
import castbridge.core.tv.home.HomeTileInfo
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Random

class PinRegenerationTest {
    // ---- affichage
    @Test fun `groupe par 3 chiffres`() { assertEquals("482 913", PinDisplay.grouped("482913")) }
    @Test fun `un code invalide n'est pas regroupe`() { assertEquals("12", PinDisplay.grouped("12")) }
    @Test fun `masque sous profil enfant`() {
        assertEquals("••••••", PinDisplay.shown("482913", true))
        assertEquals("482 913", PinDisplay.shown("482913", false))
        assertEquals("Code PIN · 482 913", PinDisplay.tileStatus("482913", false))
        assertEquals("Code PIN · ••••••", PinDisplay.tileStatus("482913", true))
        assertFalse(PinDisplay.tileStatus("482913", true).contains("482"))
    }

    // ---- regles
    private val H = PinRules.WINDOW_MS
    @Test fun `autorise sans obstacle`() { assertEquals(PinDecision.Allowed, PinRules.decide(PinContext(), emptyList(), 10_000)) }
    @Test fun `profil enfant refuse`() {
        val d = PinRules.decide(PinContext(childProfileActive = true), emptyList(), 10_000) as PinDecision.Refused
        assertEquals(PinRefusal.CHILD_PROFILE, d.reason)
    }
    @Test fun `copie en cours refuse avec le bon texte`() {
        val d = PinRules.decide(PinContext(transferInProgress = true), emptyList(), 10_000) as PinDecision.Refused
        assertEquals(PinRefusal.TRANSFER_IN_PROGRESS, d.reason)
        assertTrue(d.text.contains("Une copie est en cours"))
    }
    @Test fun `trois par heure puis refus avec delai`() {
        val t = listOf(1_000L, 2_000L, 3_000L)
        val d = PinRules.decide(PinContext(), t, 10_000) as PinDecision.Refused
        assertEquals(PinRefusal.RATE_LIMITED, d.reason)
        assertEquals(1_000L + H - 10_000, d.retryAfterMs)
        assertEquals(PinDecision.Allowed, PinRules.decide(PinContext(), t.take(2), 10_000))
    }
    @Test fun `la fenetre glisse`() {
        val t = listOf(1_000L, 2_000L, 3_000L)
        assertEquals(PinDecision.Allowed, PinRules.decide(PinContext(), t, 1_000L + H + 1))
        assertEquals(listOf(2_000L, 3_000L), PinRules.pruneTimes(t, 1_000L + H))
    }
    @Test fun `le profil enfant prime sur la copie et la limite`() {
        val d = PinRules.decide(PinContext(true, true), listOf(1L, 2L, 3L), 10) as PinDecision.Refused
        assertEquals(PinRefusal.CHILD_PROFILE, d.reason)
    }
    @Test fun `nouveau code valide et different de l'actuel et des deux precedents`() {
        val cur = "111111"
        val prev = listOf(PinRules.fingerprint("222222"), PinRules.fingerprint("333333"))
        repeat(50) {
            // un generateur biaise qui sort d'abord les anciens codes
            val seq = ArrayDeque(listOf("111111", "222222", "333333").flatMap { c -> c.map { it - '0' } })
            val r = object : Random() { override fun nextInt(bound: Int) = if (seq.isNotEmpty()) seq.removeFirst() else super.nextInt(bound) }
            val n = PinRules.nextPin(cur, prev, r)
            assertTrue(Pin.isValidFormat(n)); assertNotEquals(cur, n); assertFalse(PinRules.fingerprint(n) in prev)
        }
    }
    @Test fun `l'empreinte ne contient pas le code`() {
        val f = PinRules.fingerprint("482913")
        assertFalse(f.contains("482913")); assertEquals(f, PinRules.fingerprint("482913")); assertNotEquals(f, PinRules.fingerprint("482914"))
    }
    @Test fun `on retient les deux derniers`() {
        var l = emptyList<String>()
        l = PinRules.pushFingerprints(l, "100000"); l = PinRules.pushFingerprints(l, "200000"); l = PinRules.pushFingerprints(l, "300000")
        assertEquals(listOf(PinRules.fingerprint("300000"), PinRules.fingerprint("200000")), l)
    }

    // ---- regeneration complete
    private class Store(var pin: String, var fps: List<String> = emptyList(), var times: List<Long> = emptyList(), var fail: Boolean = false) : PinStore {
        override fun current() = pin
        override fun fingerprints() = fps
        override fun times() = times
        override fun commit(pin: String, fingerprints: List<String>, times: List<Long>): Boolean {
            if (fail) return false
            this.pin = pin; fps = fingerprints; this.times = times; return true
        }
    }
    private var clock = 1_000_000L
    private val log = ArrayList<String>()
    private val applied = ArrayList<String>()
    private fun regen(store: Store, guard: PinGuard) = PinRegenerator(store, guard, { log += it }, { applied += it }, { clock })

    @Test fun `regenere, persiste, applique, journalise sans valeur`() {
        val s = Store("123456"); val g = PinGuard("123456")
        val o = regen(s, g).regenerate(PinContext()) as PinOutcome.Done
        assertNotEquals("123456", o.pin); assertEquals(o.pin, s.pin); assertEquals(listOf(o.pin), applied); assertEquals(clock, o.at)
        assertEquals(listOf("PIN régénéré"), log)
        assertTrue(log.none { it.contains(o.pin) || it.contains("123456") })
        assertEquals(listOf(PinRules.fingerprint("123456")), s.fps); assertEquals(listOf(clock), s.times)
    }
    @Test fun `l'ancien code est refuse, le nouveau accepte`() {
        val s = Store("123456"); val g = PinGuard("123456")
        val o = regen(s, g).regenerate(PinContext()) as PinOutcome.Done
        assertEquals(PinGuard.Result.BAD, g.check("10.0.0.2", "123456"))
        assertEquals(PinGuard.Result.OK, g.check("10.0.0.3", o.pin))
    }
    @Test fun `les compteurs d'essais sont remis a zero`() {
        val s = Store("123456"); val g = PinGuard("123456")
        repeat(5) { g.check("10.0.0.2", "000000") }
        assertEquals(PinGuard.Result.LOCKED, g.check("10.0.0.2", "123456"))
        val o = regen(s, g).regenerate(PinContext()) as PinOutcome.Done
        assertEquals(PinGuard.Result.OK, g.check("10.0.0.2", o.pin))
        assertEquals(0L, g.retryAfterSeconds("10.0.0.2"))
    }
    @Test fun `echec d'ecriture garde l'ancien code partout`() {
        val s = Store("123456", fail = true); val g = PinGuard("123456")
        assertEquals(PinOutcome.WriteFailed, regen(s, g).regenerate(PinContext()))
        assertEquals("123456", s.pin); assertTrue(applied.isEmpty()); assertEquals(PinGuard.Result.OK, g.check("1.1.1.1", "123456"))
        assertTrue(log.none { it.contains("123456") })
    }
    @Test fun `refus ne change rien`() {
        val s = Store("123456"); val g = PinGuard("123456")
        val o = regen(s, g).regenerate(PinContext(childProfileActive = true)) as PinOutcome.Refused
        assertEquals(PinRefusal.CHILD_PROFILE, o.decision.reason)
        assertEquals("123456", s.pin); assertTrue(applied.isEmpty()); assertTrue(s.times.isEmpty())
    }
    @Test fun `quatrieme regeneration dans l'heure refusee`() {
        val s = Store("123456"); val g = PinGuard("123456"); val r = regen(s, g)
        repeat(3) { clock += 1000; assertTrue(r.regenerate(PinContext()) is PinOutcome.Done) }
        clock += 1000
        assertTrue(r.regenerate(PinContext()) is PinOutcome.Refused)
        clock += H
        assertTrue(r.regenerate(PinContext()) is PinOutcome.Done)
    }
    @Test fun `jamais deux fois le meme code sur de nombreuses regenerations`() {
        val s = Store("123456"); val g = PinGuard("123456"); val r = regen(s, g)
        var prev = listOf("123456")
        repeat(40) { clock += H; val o = r.regenerate(PinContext()) as PinOutcome.Done; assertFalse(o.pin in prev); prev = (listOf(o.pin) + prev).take(3) }
    }

    // ---- machine d'etats
    private val ok = PinDecision.Allowed
    private val no = PinDecision.Refused(PinRefusal.TRANSFER_IN_PROGRESS, "Une copie est en cours")
    @Test fun `generer demande d'abord confirmation, Annuler revient`() {
        var s = PinFlow.step(PinFlowState(), PinEvent.GENERATE, ok)
        assertEquals(PinPhase.CONFIRMING, s.phase); assertFalse(s.write)
        s = PinFlow.step(s, PinEvent.CANCEL, ok)
        assertEquals(PinPhase.SHOWING, s.phase); assertFalse(s.write)
    }
    @Test fun `confirmer ecrit une seule fois puis termine`() {
        var s = PinFlow.step(PinFlowState(), PinEvent.GENERATE, ok)
        s = PinFlow.step(s, PinEvent.CONFIRM, ok)
        assertTrue(s.write)
        s = PinFlow.step(s, PinEvent.SAVED, ok)
        assertEquals(PinPhase.DONE, s.phase); assertFalse(s.write)
        assertFalse(PinFlow.step(s, PinEvent.CONFIRM, ok).write)
    }
    @Test fun `refus des le clic generer, pas de confirmation`() {
        val s = PinFlow.step(PinFlowState(), PinEvent.GENERATE, no)
        assertEquals(PinPhase.SHOWING, s.phase); assertEquals("Une copie est en cours", s.notice); assertFalse(s.write)
    }
    @Test fun `une copie qui demarre pendant la confirmation annule l'ecriture`() {
        var s = PinFlow.step(PinFlowState(), PinEvent.GENERATE, ok)
        s = PinFlow.step(s, PinEvent.CONFIRM, no)
        assertFalse(s.write); assertEquals(PinPhase.SHOWING, s.phase); assertNotNull(s.notice)
    }
    @Test fun `un double OK n'ecrit qu'une fois`() {
        var s = PinFlow.step(PinFlowState(), PinEvent.GENERATE, ok)
        s = PinFlow.step(s, PinEvent.CONFIRM, ok)
        assertTrue(s.write)
        assertFalse(PinFlow.step(s, PinEvent.CONFIRM, ok).write)
        assertEquals(PinPhase.SAVING, PinFlow.step(s, PinEvent.GENERATE, ok).phase)
    }
    @Test fun `confirmer hors confirmation n'ecrit pas`() {
        assertFalse(PinFlow.step(PinFlowState(), PinEvent.CONFIRM, ok).write)
    }
    @Test fun `echec d'ecriture dit que l'ancien code reste`() {
        var s = PinFlow.step(PinFlowState(), PinEvent.GENERATE, ok)
        s = PinFlow.step(s, PinEvent.CONFIRM, ok)
        s = PinFlow.step(s, PinEvent.SAVE_FAILED, ok)
        assertEquals(PinPhase.SHOWING, s.phase); assertTrue(s.notice!!.contains("ancien code reste"))
    }

    // ---- accueil
    private fun t(id: String) = HomeTileInfo(id, "L-$id")
    @Test fun `la tuile pin est en acces rapide apres Bibliotheque, jamais dans un groupe`() {
        assertTrue("pin" in HomeGroups.QUICK_ACCESS)
        assertEquals(HomeGroups.QUICK_ACCESS.indexOf("library") + 1, HomeGroups.QUICK_ACCESS.indexOf("pin"))
        assertNull(HomeGroups.groupOf("pin"))
        val es = HomeGroups.layout(listOf(t("library"), t("pin"), t("usb"), t("receive")))
        assertEquals(listOf("library", "pin", "media"), es.map { it.id })
        assertTrue(es[1] is HomeEntry.Direct)
    }
    @Test fun `la tuile pin n'est pas perdue sans Bibliotheque`() {
        assertEquals(listOf("pin"), HomeGroups.layout(listOf(t("pin"))).map { it.id })
    }
    @Test fun `PlayerActivity declare la tuile pin avec le formateur`() {
        val f = File("../receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt").takeIf { it.exists() } ?: File("receiver/src/main/kotlin/castbridge/receiver/PlayerActivity.kt")
        val src = f.readText()
        assertTrue(src.contains("tile(\"pin\""))
        assertTrue(src.contains("PinDisplay.tileStatus("))
    }
}
