package castbridge.core.trust

import kotlin.test.*

/** Table test of the home-screen decisions (chip, wizard, wizard reason). */
class HomeLinkViewTest {
    private val machine = LinkMachine()
    private fun step(s: LinkState, name: String = "Salon") = machine.view(LinkMachine.Model(shown = s, tvName = name))
    private val good = step(LinkState.Connected(RouteKind.LAN, "Salon"))
    private val now = 1_000_000L

    private data class Row(val label: String, val f: HomeFacts, val state: (LinkState) -> Boolean, val wizard: Boolean, val reason: String? = null, val title: String? = null)

    private fun facts(saved: Int = 0, def: String? = null, step: LinkView? = null, pin: String? = null, stored: Boolean = false,
                      ok: Long? = null, status: Int? = null) = HomeFacts(saved, def, step, pin, stored, ok, status, now)

    private val rows = listOf(
        Row("rien du tout", facts(), { it is LinkState.NoTv }, true),
        Row("TV à code, code absent", facts(pin = "CastBridge TV Salon"), { it is LinkState.NoTv }, true),
        Row("TV à code, code gardé, jamais interrogée", facts(pin = "CastBridge TV Salon", stored = true), { it is LinkState.Connecting }, false, title = "Vérification de la liaison avec Salon…"),
        Row("TV à code, réponse fraîche", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 2_000, status = 200), { it is LinkState.Connected }, false, title = "Connectée"),
        Row("TV à code, réponse vieille de 20 s : pas « Connectée »", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 20_000, status = 200), { it is LinkState.Connecting }, false),
        Row("TV à code, 401 : l'assistant s'ouvre avec le motif", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 1_000, status = 401), { it is LinkState.Connecting }, true, HomeLinkView.CODE_CHANGED),
        Row("TV à code, aucune réponse (0)", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 1_000, status = 0), { it is LinkState.Connecting }, false),
        Row("TV à code, horloge reculée (ok dans le futur)", facts(pin = "CastBridge TV Salon", stored = true, ok = now + 5_000, status = 200), { it is LinkState.Connecting }, false),
        Row("registre : une TV choisie, aucun pas publié", facts(saved = 1, def = "Salon"), { it is LinkState.Connecting }, false, title = "Vérification de la liaison avec Salon…"),
        Row("registre : pas publié NoTv", facts(saved = 1, def = "Salon", step = step(LinkState.NoTv)), { it is LinkState.Connecting }, false),
        Row("registre : liaison bonne", facts(saved = 1, def = "Salon", step = good), { it is LinkState.Connected }, false),
        Row("registre : TV injoignable", facts(saved = 1, def = "Salon", step = step(LinkState.TvUnreachable(AbsentKind.NO_ANSWER))), { it is LinkState.TvUnreachable }, false),
        Row("registre : plusieurs TV, aucune choisie", facts(saved = 3), { it is LinkState.Connecting }, false, title = "Choisissez votre TV"),
        Row("registre : 401 = jeton à renouveler, jamais l'assistant", facts(saved = 1, def = "Salon", step = good, status = 401), { it is LinkState.Connected }, false, HomeLinkView.RECONNECTING),
        Row("registre prioritaire sur le chemin code", facts(saved = 1, def = "Salon", step = good, pin = "CastBridge TV Autre", stored = true, status = 401), { it is LinkState.Connected }, false, HomeLinkView.RECONNECTING),
    )

    @Test fun table() {
        for (r in rows) {
            val v = HomeLinkView.decide(r.f)
            assertTrue(r.state(v.view.state), "${r.label} : état ${v.view.state}")
            assertEquals(r.wizard, v.showWizard, "${r.label} : assistant")
            assertEquals(r.reason, v.wizardReason, "${r.label} : motif")
            r.title?.let { assertEquals(it, v.view.title, "${r.label} : titre") }
        }
    }

    @Test fun neverConnectedOnStaleMemory() {
        for (age in listOf(10_001L, 30_000L, 3_600_000L)) {
            val v = HomeLinkView.decide(facts(pin = "CastBridge TV Salon", stored = true, ok = now - age, status = 200))
            assertFalse(v.view.state.isGood, "âge $age ms")
        }
        assertTrue(HomeLinkView.decide(facts(pin = "CastBridge TV Salon", stored = true, ok = now - 10_000, status = 200)).view.state.isGood)
    }

    @Test fun displayNameDropsPrefix() {
        assertEquals("Salon", HomeLinkView.decide(facts(pin = "CastBridge TV Salon", stored = true, ok = now, status = 200)).view.detail)
    }
}
