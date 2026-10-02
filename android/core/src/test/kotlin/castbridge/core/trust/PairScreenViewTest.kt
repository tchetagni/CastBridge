package castbridge.core.trust

import kotlin.test.*

class PairScreenViewTest {
    private val machine = LinkMachine()
    private fun step(s: LinkState) = machine.view(LinkMachine.Model(shown = s, tvName = "Salon"))
    private val good = step(LinkState.Connected(RouteKind.LAN, "Salon"))

    private data class Row(val label: String, val saved: Int, val def: String?, val step: LinkView?, val list: List<String>, val ok: (LinkState) -> Boolean, val empty: Boolean)
    private val rows = listOf(
        Row("rien", 0, null, null, emptyList(), { it is LinkState.NoTv }, true),
        Row("rien mais pas publié NoTv", 0, null, step(LinkState.NoTv), emptyList(), { it is LinkState.NoTv }, true),
        Row("une TV, pas de pas", 1, "Salon", null, listOf("Salon"), { it is LinkState.Connecting }, false),
        Row("une TV, liaison bonne", 1, "Salon", good, listOf("Salon"), { it.isGood }, false),
        Row("une TV, pas NoTv publié : vérification", 1, "Salon", step(LinkState.NoTv), listOf("Salon"), { it is LinkState.Connecting }, false),
        Row("trois TV sans choix", 3, null, null, listOf("a", "b", "c"), { it is LinkState.Connecting }, false),
        Row("liste connue, compteur à zéro", 0, null, null, listOf("Salon"), { it is LinkState.Connecting }, false),
        Row("compteur sans liste", 2, null, null, emptyList(), { it is LinkState.Connecting }, false),
        Row("TV injoignable", 1, "Salon", step(LinkState.TvUnreachable(AbsentKind.NO_ANSWER)), listOf("Salon"), { it is LinkState.TvUnreachable }, false),
        Row("TV oubliée", 1, "Salon", step(LinkState.TvForgotMe(0)), listOf("Salon"), { it is LinkState.TvForgotMe }, false),
    )

    @Test fun table() {
        for (r in rows) {
            val v = PairScreenView.decide(r.saved, r.def, r.step, r.list)
            assertTrue(r.ok(v.state), "${r.label} : ${v.state}")
            assertEquals(if (r.empty) PairScreenView.NO_TV_LIST else null, PairScreenView.emptyListText(r.saved, r.list), "${r.label} : liste vide")
        }
    }

    @Test fun noTvAddedOnlyWhenNothingSaved() {
        assertNull(PairScreenView.emptyListText(1, emptyList()))
        assertEquals("Aucune TV ajoutée.", PairScreenView.emptyListText(0, emptyList()))
    }
}
