package castbridge.core.quiz.online

import castbridge.core.connect.Routes
import castbridge.core.ux.SignalLevel
import kotlin.test.*

/** La cause locale « liaison de cette TV » ne peut que BAISSER le niveau reçu du serveur (DESIGN-W20-AMENDEMENT § 2.7). */
class LinkCauseTest {
    private val green = SafetySign.of(SafetyFacts(PlayScope.INTERNET))
    private val orange = SafetySign.of(SafetyFacts(PlayScope.INTERNET, rttMs = 2_000))
    private val red = SafetySign.of(SafetyFacts(PlayScope.INTERNET, tls = TlsState.INVALID))
    private val black = SafetySign.of(SafetyFacts(PlayScope.INTERNET, tvHasInternet = false))

    @Test fun gatewayMakesItOrangeNotRed() {
        val v = LinkCause.lower(green, Routes.Via.GATEWAY, TvLink.Online, true)
        assertEquals(SignalLevel.ORANGE, v.level)
        assertEquals("Internet par le téléphone (Bluetooth) · lent", v.text)
        assertEquals("Attention", v.word); assertEquals(SignalLevel.ORANGE.shape, v.shape)   // le mot et la forme suivent toujours la couleur
        assertEquals(PlayScope.INTERNET, v.scope)
    }

    @Test fun resumingIsOrangeWithTheSecondsAndLostIsRed() {
        val r = LinkCause.lower(green, Routes.Via.DIRECT, TvLink.Resuming(12), true)
        assertEquals(SignalLevel.ORANGE, r.level); assertEquals("Internet · liaison en reprise (12 s)", r.text)
        val l = LinkCause.lower(green, null, TvLink.Lost, true)
        assertEquals(SignalLevel.RED, l.level); assertEquals("Partie Internet perdue", l.text); assertEquals("Problème", l.word)
    }

    @Test fun neverGreenerThanTheServer() {
        // le serveur est déjà orange ou rouge : une liaison saine ne le relève JAMAIS
        assertEquals(SignalLevel.ORANGE, LinkCause.lower(orange, Routes.Via.DIRECT, TvLink.Online, true).level)
        assertEquals(SignalLevel.RED, LinkCause.lower(red, Routes.Via.DIRECT, TvLink.Online, true).level)
        assertEquals(SignalLevel.RED, LinkCause.lower(red, Routes.Via.GATEWAY, TvLink.Resuming(3), true).level, "orange local sous un rouge serveur : le rouge reste")
        assertEquals(SignalLevel.GREEN, LinkCause.lower(green, Routes.Via.DIRECT, TvLink.Online, true).level, "liaison saine, serveur vert : inchangé")
        assertSame(green, LinkCause.lower(green, Routes.Via.DIRECT, TvLink.Online, true))
        // et l'inverse : jamais plus vert que le serveur, quelle que soit la combinaison
        val levels = listOf(green, orange, red, black)
        for (s in levels) for (via in listOf(null, Routes.Via.DIRECT, Routes.Via.GATEWAY)) for (link in listOf(TvLink.Online, TvLink.Resuming(5), TvLink.Lost)) for (net in listOf(true, false)) {
            val out = LinkCause.lower(s, via, link, net).level
            assertTrue(order(out) >= order(s.level), "serveur ${s.level} + $via/$link/net=$net a donné $out")
        }
    }

    @Test fun noNetworkOutsideAGameIsBlack() {
        assertEquals(SignalLevel.BLACK, LinkCause.lower(green, null, TvLink.Online, false).level)
        assertEquals(SignalLevel.BLACK, LinkCause.lower(black, null, TvLink.Lost, false).level)
        assertEquals(SignalLevel.BLACK, LinkCause.lower(green, null, TvLink.Lost, false).level, "aucun réseau : noir avant le rouge")
    }

    @Test fun invalidCertificateIsRedAndSaysSo() {
        val v = LinkCause.lower(green, Routes.Via.DIRECT, TvLink.Online, true, tlsInvalid = true)
        assertEquals(SignalLevel.RED, v.level); assertTrue(v.text.contains("certificat non valide"))
    }

    private fun order(l: SignalLevel) = when (l) { SignalLevel.GREEN -> 0; SignalLevel.ORANGE -> 1; SignalLevel.RED -> 2; SignalLevel.BLACK -> 3 }
}
