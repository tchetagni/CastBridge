package castbridge.core.link

import castbridge.core.link.WdDiagnostic.Sample
import kotlin.test.*

/** Le relevé de terrain du Wi-Fi Direct : mesuré ou « inconnu », jamais un secret (docs/agent-reports/wd-manual-button.md). */
class WdDiagnosticTest {
    private val MB = 1L shl 20
    private val full = WdDiagnostic.Report(
        ssid = "DIRECT-ab-CastBridge", ownerIp = "192.168.49.1", joinedMs = 4_300,
        latenciesMs = List(20) { 10L + it % 3 }, network = Sample(2 * MB, 400), usb = Sample(2 * MB, 1000),
        tvHadLanBefore = true, tvLinkAfter = "wifi",
    )

    @Test fun fullReadoutHasEveryField() {
        val t = WdDiagnostic.format(full)
        assertTrue(t.contains("Groupe : DIRECT-ab (créé par la TV)"), t)
        assertTrue(t.contains("Adresse : 192.168.49.1"), t)
        assertTrue(t.contains("Joint en 4,3 s"), t)
        assertTrue(t.contains("Latence médiane 11 ms (20/20 réponses)"), t)
        assertTrue(t.contains("Débit estimé 5,0 Mo/s (TV→téléphone"), t)
        assertTrue(t.contains("Wi-Fi de la TV conservé : oui"), t)
        assertTrue(t.contains("Clé USB : lecture 2,0 Mo/s"), t)
        assertTrue(t.contains("Verdict : bon."), t)
    }

    @Test fun whatCannotBeKnownSaysUnknownNeverAGuess() {
        val t = WdDiagnostic.format(WdDiagnostic.Report(null, null, null, emptyList(), null, null, null, null))
        for (k in listOf("Groupe : inconnu", "Adresse : inconnu", "Joint en : inconnu", "Latence médiane : inconnu (0/20 réponses)", "Débit estimé : inconnu",
            "Wi-Fi de la TV conservé : inconnu", "Clé USB : inconnu")) assertTrue(t.contains(k), "$k manque dans : $t")
        assertFalse(t.contains("Verdict"), "pas de verdict sans mesure")
    }

    @Test fun tooFewAnswersGiveNoMedian() {
        assertNull(WdDiagnostic.median(List(WdDiagnostic.MIN_ANSWERS - 1) { 5L }))
        assertEquals(5L, WdDiagnostic.median(List(WdDiagnostic.MIN_ANSWERS) { 5L }))
        assertEquals(15L, WdDiagnostic.median(List(20) { if (it < 10) 10L else 20L }), "médiane de 20 valeurs = moyenne des deux du milieu")
    }

    @Test fun tinyOrZeroDurationSamplesAreNotThroughput() {
        assertNull(WdDiagnostic.mbPerSec(Sample(1000, 50)))
        assertNull(WdDiagnostic.mbPerSec(Sample(2 * MB, 0)))
        assertNull(WdDiagnostic.mbPerSec(null))
        assertEquals(1.0, WdDiagnostic.mbPerSec(Sample(MB, 1000)))
    }

    @Test fun tvWifiKeptNeedsAKnownBefore() {
        assertTrue(WdDiagnostic.tvWifiKept(true, "wifi")!!); assertTrue(WdDiagnostic.tvWifiKept(true, "ethernet")!!)
        assertFalse(WdDiagnostic.tvWifiKept(true, "none")!!)
        assertNull(WdDiagnostic.tvWifiKept(true, null)); assertNull(WdDiagnostic.tvWifiKept(true, "other"))
        assertNull(WdDiagnostic.tvWifiKept(false, "none"), "elle n'en avait pas avant : on ne dit pas « non »")
        assertNull(WdDiagnostic.tvWifiKept(null, "wifi"))
    }

    @Test fun verdictThresholds() {
        assertEquals("bon", WdDiagnostic.grade(5.0)); assertEquals("bon", WdDiagnostic.grade(30.0))
        assertEquals("acceptable pour la copie", WdDiagnostic.grade(4.99)); assertEquals("acceptable pour la copie", WdDiagnostic.grade(1.0))
        assertEquals("niveau Bluetooth", WdDiagnostic.grade(0.99)); assertNull(WdDiagnostic.grade(null))
    }

    @Test fun neverAPassphraseOrAPinInTheText() {
        val pass = "Zq7xK2mPw9Lr4Tn8"                      // 16 caractères comme le mot de passe d'un groupe
        val pin = "482913"
        // le nom complet d'un réseau peut porter n'importe quoi après DIRECT-xx : seul « DIRECT-xx » est écrit
        val t = WdDiagnostic.format(full.copy(ssid = "DIRECT-ab-$pass-$pin"))
        assertFalse(t.contains(pass), t); assertFalse(t.contains(pin), t); assertFalse(t.contains("DIRECT-ab-"), t)
        // une adresse ou un nom piégés ne passent pas le filtre commun
        val trap = WdDiagnostic.format(full.copy(ownerIp = "cbk_0123456789abcdef"))
        assertFalse(trap.contains("cbk_0123456789abcdef"), trap)
        // un nom qui n'est pas un groupe n'est pas écrit du tout
        assertTrue(WdDiagnostic.format(full.copy(ssid = "MaBox-$pass")).contains("Groupe : inconnu"))
    }

    @Test fun measureRunsTwentyHelloProbesAndKeepsOnlyTheAnswers() {
        var clock = 0L
        var calls = 0
        val probe = object : WdDiagnostic.Probe {
            override fun hello(): Boolean { calls++; clock += 7; return calls % 4 != 0 }   // 5 échecs sur 20
            override fun networkSample() = Sample(2 * MB, 500)
            override fun usbSample(): Sample? = null
            override fun tvNetLink() = "wifi"
        }
        val m = WdDiagnostic.measure(probe, { clock })
        assertEquals(20, calls); assertEquals(15, m.latenciesMs.size); assertTrue(m.latenciesMs.all { it == 7L })
        assertNull(m.usb); assertEquals("wifi", m.tvLinkAfter)
        val t = WdDiagnostic.format(WdDiagnostic.report("DIRECT-zz-x", "192.168.49.1", 2_000, true, m))
        assertTrue(t.contains("Latence médiane 7 ms (15/20 réponses)"), t); assertTrue(t.contains("Clé USB : inconnu"), t)
    }
}
