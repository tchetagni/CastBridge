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
                      ok: Long? = null, status: Int? = null, token: Boolean = false) = HomeFacts(saved, def, step, pin, stored, ok, status, now, token)

    private val rows = listOf(
        Row("rien du tout", facts(), { it is LinkState.NoTv }, true),
        Row("TV à code, code absent", facts(pin = "CastBridge TV Salon"), { it is LinkState.NoTv }, true),
        Row("TV à code, code gardé, jamais interrogée", facts(pin = "CastBridge TV Salon", stored = true), { it is LinkState.Connecting }, false, title = "Vérification de la liaison avec Salon…"),
        Row("TV à code, réponse fraîche", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 2_000, status = 200), { it is LinkState.Connected }, false, title = "Connectée"),
        Row("TV à code, réponse vieille de 20 s : pas « Connectée »", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 20_000, status = 200), { it is LinkState.Connecting }, false),
        Row("TV à code, 401 : corrigé par construction (motif rendu), l'assistant s'ouvre", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 1_000, status = 401), { it is LinkState.Connecting }, true, HomeLinkView.CODE_CHANGED),
        Row("TV à code, aucune réponse (0)", facts(pin = "CastBridge TV Salon", stored = true, ok = now - 1_000, status = 0), { it is LinkState.Connecting }, false),
        Row("TV à code, horloge reculée (ok dans le futur)", facts(pin = "CastBridge TV Salon", stored = true, ok = now + 5_000, status = 200), { it is LinkState.Connecting }, false),
        Row("registre : une TV choisie, aucun pas publié", facts(saved = 1, def = "Salon"), { it is LinkState.Connecting }, false, title = "Vérification de la liaison avec Salon…"),
        Row("registre : pas publié NoTv", facts(saved = 1, def = "Salon", step = step(LinkState.NoTv)), { it is LinkState.Connecting }, false),
        Row("registre : liaison bonne", facts(saved = 1, def = "Salon", step = good), { it is LinkState.Connected }, false),
        Row("registre : TV injoignable", facts(saved = 1, def = "Salon", step = step(LinkState.TvUnreachable(AbsentKind.NO_ANSWER))), { it is LinkState.TvUnreachable }, false),
        Row("registre : plusieurs TV, aucune choisie", facts(saved = 3), { it is LinkState.Connecting }, false, title = "Choisissez votre TV"),
        Row("jeton refusé (401) : « vérification », jamais Connectée, jamais l'assistant", facts(saved = 1, def = "Salon", step = good, status = 401, token = true), { it is LinkState.Connecting }, false, HomeLinkView.RECONNECTING),
        Row("jeton, 200 : état du registre", facts(saved = 1, def = "Salon", step = good, status = 200, token = true), { it is LinkState.Connected }, false),
        Row("jeton refusé, TV par défaut inconnue : titre sans nom inventé", facts(saved = 2, status = 401, token = true), { it is LinkState.Connecting }, false, HomeLinkView.RECONNECTING),
        Row("registre prioritaire sur le chemin code (jeton)", facts(saved = 1, def = "Salon", step = good, pin = "CastBridge TV Autre", stored = true, status = 401, token = true), { it is LinkState.Connecting }, false, HomeLinkView.RECONNECTING),
        Row("cas mixte : une TV enregistrée, pas de session, sonde avec le code, 401 : assistant AVEC motif", facts(saved = 1, def = "Salon", pin = "CastBridge TV Salon", stored = true, status = 401), { it is LinkState.Connecting }, true, HomeLinkView.CODE_CHANGED),
        Row("cas mixte, 200 frais", facts(saved = 1, def = "Salon", pin = "CastBridge TV Salon", stored = true, ok = now - 1_000, status = 200), { it is LinkState.Connected }, false),
        Row("cas mixte, jamais interrogée : vérification", facts(saved = 1, def = "Salon", pin = "CastBridge TV Salon", stored = true), { it is LinkState.Connecting }, false),
        Row("registre sans jeton ni code : vue du registre, pas d'assistant", facts(saved = 1, def = "Salon", step = good), { it is LinkState.Connected }, false),
        Row("registre, code non gardé, vue du registre", facts(saved = 1, def = "Salon", pin = "CastBridge TV Salon", stored = false), { it is LinkState.Connecting }, false),
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
        for (age in listOf(15_001L, 30_000L, 3_600_000L)) {
            val v = HomeLinkView.decide(facts(pin = "CastBridge TV Salon", stored = true, ok = now - age, status = 200))
            assertFalse(v.view.state.isGood, "âge $age ms")
        }
        assertTrue(HomeLinkView.decide(facts(pin = "CastBridge TV Salon", stored = true, ok = now - 15_000, status = 200)).view.state.isGood)
    }

    @Test fun tokenFourOhOneNeverConnectedNorWizard() {
        for (step in listOf(null, good, step(LinkState.NoTv))) for (saved in 1..3) {
            val v = HomeLinkView.decide(facts(saved = saved, def = "Salon", step = step, status = 401, token = true))
            assertFalse(v.view.state.isGood); assertFalse(v.showWizard); assertEquals(HomeLinkView.RECONNECTING, v.wizardReason)
        }
    }

    @Test fun wizardAlwaysCarriesItsReasonWhenOpenedByARefusal() {
        for (saved in 0..2) {
            val v = HomeLinkView.decide(facts(saved = saved, def = "Salon".takeIf { saved > 0 }, pin = "CastBridge TV Salon", stored = true, status = 401))
            assertTrue(v.showWizard); assertEquals(HomeLinkView.CODE_CHANGED, v.wizardReason)
        }
    }

    @Test fun freshWindowIs15s() { assertEquals(15_000L, HomeFacts.FRESH_MS) }

    @Test fun displayNameDropsPrefix() {
        assertEquals("Salon", HomeLinkView.decide(facts(pin = "CastBridge TV Salon", stored = true, ok = now, status = 200)).view.detail)
    }
}
