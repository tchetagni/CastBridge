package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import kotlin.test.*

/**
 * « Ouvrir avec CastBridge » and the first view of the link (field bug 2026-10-02: « Aucune TV ajoutée » with a TV saved and connected).
 * Table-driven: one row per situation, checked against the route, the buttons, the action and the words that must (not) appear.
 */
class SendChoiceTest {
    private val machine = LinkMachine()
    private fun stepView(s: LinkState, name: String = "Salon") = machine.view(LinkMachine.Model(shown = s, tvName = name))

    private data class Row(val label: String, val f: SendFacts, val route: SendRoute, val copy: Boolean, val move: Boolean, val action: SendAction,
                           val says: String? = null, val note: Boolean? = null)

    private val rows = listOf(
        // ---- nothing known at all: the only case where « Aucune TV » is allowed
        Row("registre vide, pas de TV à code", SendFacts(), SendRoute.NONE, false, false, SendAction.ADD_TV, says = "Aucune TV ajoutée"),
        // ---- trusted TV saved, cold process: nothing published yet (the hypothesis of the brief)
        Row("TV de confiance, aucun pas publié", SendFacts(1, "Salon", null), SendRoute.QUEUE, true, false, SendAction.NONE, says = "Vérification de la liaison avec Salon", note = true),
        Row("TV de confiance, pas publié = NoTv (modèle restauré)", SendFacts(1, "Salon", stepView(LinkState.NoTv)), SendRoute.QUEUE, true, false, SendAction.NONE, says = "Vérification", note = true),
        Row("TV de confiance, connexion", SendFacts(1, "Salon", stepView(LinkState.Connecting)), SendRoute.QUEUE, true, false, SendAction.NONE, says = "Connexion à Salon", note = true),
        Row("TV de confiance, reconnexion", SendFacts(1, "Salon", stepView(LinkState.Reconnecting(LossSide.TV, LinkState.Connected(RouteKind.LAN, "Salon")))), SendRoute.QUEUE, true, false, SendAction.NONE, note = true),
        Row("TV de confiance, injoignable (la boucle réessaie)", SendFacts(1, "Salon", stepView(LinkState.TvUnreachable(AbsentKind.NO_ANSWER))), SendRoute.QUEUE, true, false, SendAction.NONE, note = true),
        Row("TV de confiance, jeton en renouvellement", SendFacts(1, "Salon", stepView(LinkState.CredentialExpired)), SendRoute.QUEUE, true, false, SendAction.NONE, note = true),
        Row("plusieurs TV, aucune par défaut", SendFacts(2, null, stepView(LinkState.NoTv)), SendRoute.NONE, false, false, SendAction.OPEN_APP, says = "Choisissez votre TV"),
        // ---- trusted session
        Row("session Wi-Fi", SendFacts(1, "Salon", stepView(LinkState.Connected(RouteKind.LAN, "Salon")), session = true, sessionName = "Salon"), SendRoute.QUEUE, true, true, SendAction.NONE, says = "TV : Salon", note = false),
        Row("session Bluetooth seul", SendFacts(1, "Salon", stepView(LinkState.Degraded("Salon")), session = true, sessionName = "Salon", btOnly = true), SendRoute.QUEUE, true, false, SendAction.NONE, says = "Bluetooth", note = true),
        // ---- trusted link that does not retry by itself: explained, no dead queue
        Row("la TV ne reconnaît plus ce téléphone", SendFacts(1, "Salon", stepView(LinkState.TvForgotMe(BtProtocol.HINT_OTHER_INSTALL))), SendRoute.NONE, false, false, SendAction.OPEN_APP, says = "réinitialisée"),
        Row("Bluetooth éteint, pas de code", SendFacts(1, "Salon", stepView(LinkState.BtBlocked(BtUnavailable.Reason.OFF))), SendRoute.NONE, false, false, SendAction.OPEN_APP, says = "Bluetooth"),
        Row("Bluetooth éteint mais code vérifié", SendFacts(1, "Salon", stepView(LinkState.BtBlocked(BtUnavailable.Reason.OFF)), pinTvName = "CastBridge TV Salon", pinStored = true, pinCheck = PinCheck.OK), SendRoute.PIN_UPLOAD, true, true, SendAction.NONE),
        // ---- the owner's phone on 2026-10-02: registry EMPTY, TV used through the code path (home screen green, 49,8 Go)
        Row("TV à code, vérification en cours", SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true), SendRoute.PIN_UPLOAD, true, false, SendAction.NONE, says = "Vérification de la liaison avec SMART_TV", note = true),
        Row("TV à code, code accepté", SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.OK), SendRoute.PIN_UPLOAD, true, true, SendAction.NONE, says = "TV : SMART_TV", note = false),
        Row("TV à code, introuvable pour le moment", SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.UNREACHABLE), SendRoute.PIN_UPLOAD, true, false, SendAction.NONE, says = "ne répond pas", note = true),
        Row("TV à code, code refusé", SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.REJECTED), SendRoute.NONE, false, false, SendAction.ENTER_PIN, says = "code PIN"),
        Row("TV à code, verrouillée", SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.LOCKED), SendRoute.NONE, false, false, SendAction.ENTER_PIN, says = "verrouillée"),
        Row("TV à code, aucun code gardé", SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = false), SendRoute.NONE, false, false, SendAction.ENTER_PIN, says = "code PIN"),
    )

    @Test fun everySituationHasTheExpectedChoice() {
        for (r in rows) {
            val c = SendChoices.decide(r.f)
            assertEquals(r.route, c.route, r.label); assertEquals(r.copy, c.copyEnabled, r.label + " (copier)"); assertEquals(r.move, c.moveEnabled, r.label + " (déplacer)")
            assertEquals(r.action, c.action, r.label + " (action)")
            r.says?.let { assertTrue(it in c.status, "${r.label}: « $it » attendu dans « ${c.status} »") }
            r.note?.let { assertEquals(it, c.note != null, r.label + " (ligne d'explication)") }
        }
    }

    @Test fun noTvIsNeverClaimedWhenATvIsKnown() {
        for (r in rows.filter { it.f.savedCount > 0 || it.f.pinTvName != null })
            assertFalse("Aucune TV" in SendChoices.decide(r.f).status, r.label)
    }

    @Test fun moveIsOfferedOnlyWithAJoinedTv() {
        for (r in rows) {
            val c = SendChoices.decide(r.f)
            if (c.moveEnabled) assertTrue((r.f.session && !r.f.btOnly) || r.f.pinCheck == PinCheck.OK, r.label)
            if (c.copyEnabled && !c.moveEnabled) assertNotNull(c.note, r.label + " : pourquoi « Déplacer » est grisé")
        }
    }

    @Test fun pinActionUsesTheAgreedWording() {
        assertEquals("Saisir le code PIN de la TV", SendAction.ENTER_PIN.label)
    }

    @Test fun linkStartNeverSaysNoTvWithASavedTv() {
        assertNull(LinkStart.view(0, null, null), "registre vide : l'écran garde son « Ajouter ma TV »")
        assertEquals("Vérification de la liaison avec Salon…", LinkStart.view(1, "Salon", null)!!.title)
        assertEquals("Vérification de la liaison avec Salon…", LinkStart.view(1, "Salon", stepView(LinkState.NoTv))!!.title)
        assertEquals("Choisissez votre TV", LinkStart.view(3, null, null)!!.title)
        val ok = stepView(LinkState.Connected(RouteKind.LAN, "Salon"))
        assertSame(ok, LinkStart.view(1, "Salon", ok), "a real step passes through unchanged")
        for (n in 1..3) for (d in listOf("Salon", null)) for (v in listOf(null, stepView(LinkState.NoTv), ok))
            assertFalse(LinkStart.view(n, d, v)!!.state is LinkState.NoTv, "n=$n d=$d v=${v?.state}")
    }

    @Test fun displayNameMatchesTheHomeScreen() {
        assertEquals("SMART_TV", SendChoices.display("CastBridge TV SMART_TV"))
        assertEquals("Ma TV", SendChoices.display("CastBridge TV "))
        assertEquals("Salon", SendChoices.display("Salon"))
    }
}
