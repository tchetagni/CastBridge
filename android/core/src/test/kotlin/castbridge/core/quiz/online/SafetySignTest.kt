package castbridge.core.quiz.online

import castbridge.core.ux.SignalLevel
import castbridge.core.ux.SignalLevel.*
import kotlin.test.*

/** One test per row of the table of docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md § 1.3 (+ the edge cases of the brief). */
class SafetySignTest {
    private fun f(scope: PlayScope, tls: TlsState = TlsState.OK, serverLink: Link3 = Link3.OK, lostSec: Int = 0, lan: Link3 = Link3.OK,
                  guests: Int = 0, kid: Boolean = false, parent: Boolean = false, ticket: Boolean = true, clock: Boolean = false,
                  tvNet: Boolean = true, off: Boolean = false, resuming: String? = null, gone: String? = null, rtt: Int = 0) =
        SafetyFacts(scope, tls, serverLink, lostSec, lan, 0, 0, guests, kid, parent, ticket, clock, tvNet, off, resuming, gone, rtt)

    private fun check(facts: SafetyFacts, level: SignalLevel, text: String, action: String? = null) {
        val v = SafetySign.of(facts)
        assertEquals(level, v.level); assertEquals(text, v.text); assertEquals(action, v.action)
        assertEquals(facts.scope, v.scope); assertEquals(level.shape, v.shape)
        assertTrue(v.word.isNotBlank()); assertTrue(text in v.detail)
    }

    @Test fun r01TvOnlyGreen() = check(f(PlayScope.TV_ONLY), GREEN, "TV seule · personne d'autre ne peut entrer")
    @Test fun r02LanGreen() = check(f(PlayScope.LAN), GREEN, "Réseau local · rien ne sort de la maison")
    @Test fun r03LanGuestOrange() = check(f(PlayScope.LAN, guests = 1), ORANGE, "Réseau local · 1 invité dans le Wi-Fi de la TV", "Changer le mot de passe Wi-Fi Direct à la fin")
    @Test fun r03bTwoGuestsPlural() = check(f(PlayScope.LAN, guests = 2), ORANGE, "Réseau local · 2 invités dans le Wi-Fi de la TV", "Changer le mot de passe Wi-Fi Direct à la fin")
    @Test fun r04LanPlayerGoneOrange() = check(f(PlayScope.LAN, gone = "Amina"), ORANGE, "Réseau local · Amina ne répond plus")
    @Test fun r05LanLostRed() = check(f(PlayScope.LAN, lan = Link3.LOST), RED, "Réseau local perdu : les téléphones ne peuvent plus répondre", "Télécommande : continuer seul, ou attendre")
    @Test fun r06InternetGreen() = check(f(PlayScope.INTERNET), GREEN, "Internet · partie sûre · chiffrée, pseudonymes seulement")
    @Test fun r07InternetLinkResumingOrange() = check(f(PlayScope.INTERNET, serverLink = Link3.LOST, lostSec = 12), ORANGE, "Internet · liaison en reprise (12 s)")
    @Test fun r08InternetPlayerResumingOrange() = check(f(PlayScope.INTERNET, resuming = "Koffi"), ORANGE, "Internet · Koffi en reprise")
    @Test fun r09InternetSlowOrange() = check(f(PlayScope.INTERNET, rtt = 1_501), ORANGE, "Internet · réseau lent")
    @Test fun r09bRttAtLimitIsStillGreen() = assertEquals(GREEN, SafetySign.of(f(PlayScope.INTERNET, rtt = 1_500)).level)
    @Test fun r10TlsInvalidRed() = check(f(PlayScope.INTERNET, tls = TlsState.INVALID), RED, "Internet : impossible · certificat non valide", "Fermer Internet")
    @Test fun r11TicketRefusedRed() = check(f(PlayScope.INTERNET, ticket = false), RED, "Internet : impossible · autorisation refusée", "Réessayer")
    @Test fun r12ServerLostRed() = check(f(PlayScope.INTERNET, serverLink = Link3.LOST, lostSec = 60), RED, "Internet perdu : la partie continue en local", "Réessayer")
    @Test fun r12bLost59sIsStillOrange() = check(f(PlayScope.INTERNET, serverLink = Link3.LOST, lostSec = 59), ORANGE, "Internet · liaison en reprise (59 s)")
    @Test fun r13DisabledByOwnerBlack() = check(f(PlayScope.INTERNET, off = true), BLACK, "Internet : désactivé", "Contrôle parental")
    @Test fun r14KidWithoutParentBlack() = check(f(PlayScope.INTERNET, kid = true), BLACK, "Internet : réservé aux adultes (code parental)", "Contrôle parental")
    @Test fun r14bKidWithParentGreen() = assertEquals(GREEN, SafetySign.of(f(PlayScope.INTERNET, kid = true, parent = true)).level)
    @Test fun r15TvWithoutInternetBlackNeverRed() {
        check(f(PlayScope.INTERNET, tvNet = false), BLACK, "Internet : la TV n'est pas connectée", "Connexion & réglages")
        assertEquals(BLACK, SafetySign.of(f(PlayScope.INTERNET, tvNet = false, serverLink = Link3.LOST, lostSec = 300, tls = TlsState.INVALID, ticket = false)).level)
    }
    @Test fun r16ClockDoubtBlack() = check(f(PlayScope.INTERNET, clock = true), BLACK, "Internet : vérifiez l'heure de la TV", "Connexion & réglages")

    @Test fun tvOnlyIsGreenWhateverTheFacts() {
        val hostile = f(PlayScope.TV_ONLY, tls = TlsState.INVALID, serverLink = Link3.LOST, lostSec = 999, lan = Link3.LOST, guests = 5, kid = true,
            ticket = false, clock = true, tvNet = false, off = true, resuming = "X", gone = "Y", rtt = 9_999)
        assertEquals(GREEN, SafetySign.of(hostile).level)
    }

    @Test fun worstCauseWinsAndDetailListsAllCauses() {
        val v = SafetySign.of(f(PlayScope.INTERNET, tls = TlsState.INVALID, rtt = 2_000, resuming = "Koffi"))
        assertEquals(RED, v.level); assertTrue(v.text.contains("certificat"))
        assertEquals(3, v.detail.size)
        assertTrue(v.detail.any { it.contains("réseau lent") } && v.detail.any { it.contains("Koffi") })
        val lan = SafetySign.of(f(PlayScope.LAN, guests = 1, gone = "Amina"))
        assertEquals(ORANGE, lan.level); assertTrue(lan.text.contains("invité")); assertEquals(2, lan.detail.size)
    }

    @Test fun everyLevelCarriesShapeAndWord() {
        val views = listOf(f(PlayScope.TV_ONLY), f(PlayScope.LAN, guests = 1), f(PlayScope.LAN, lan = Link3.LOST), f(PlayScope.INTERNET, off = true)).map { SafetySign.of(it) }
        assertEquals(setOf(GREEN, ORANGE, RED, BLACK), views.map { it.level }.toSet())
        assertEquals("Partie sûre", views[0].word)
        assertEquals(4, views.map { it.shape }.toSet().size)
    }
}
