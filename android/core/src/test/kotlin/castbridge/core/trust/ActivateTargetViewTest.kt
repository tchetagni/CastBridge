package castbridge.core.trust

import kotlin.test.*

class ActivateTargetViewTest {
    private val machine = LinkMachine()
    private fun step(s: LinkState) = machine.view(LinkMachine.Model(shown = s, tvName = "Salon"))
    private val lan = step(LinkState.Connected(RouteKind.LAN, "Salon"))
    private val bt = step(LinkState.Connected(RouteKind.BLUETOOTH, "Salon"))
    private val pin = "CastBridge TV Chambre"
    private val base = "http://192.168.0.7:8765"

    private val sessionBase = "http://192.168.0.9:8765"

    private data class Row(val label: String, val saved: Int, val def: String?, val step: LinkView?, val pin: String?, val base: String?,
                           val target: String?, val viaPin: Boolean, val msg: String?, val session: String? = "http://192.168.0.9:8765", val tbase: String? = null, val key: String? = null)
    private val rows = listOf(
        Row("rien", 0, null, null, null, null, null, false, ActivateTargetView.NO_TV),
        Row("rien mais chemin code avec adresse", 0, null, null, pin, base, "Chambre", true, null, tbase = base, key = pin),
        Row("chemin code sans adresse : jamais « Aucune TV »", 0, null, null, pin, null, "Chambre", true, ActivateTargetView.NOT_JOINED, key = pin),
        Row("registre, liaison LAN", 1, "Salon", lan, null, null, "Salon", false, null, tbase = sessionBase, key = "Salon"),
        Row("registre, LAN sans base de session : non lisible", 1, "Salon", lan, null, null, "Salon", false, ActivateTargetView.NOT_JOINED, session = null, key = "Salon"),
        Row("registre, liaison Bluetooth seule", 1, "Salon", bt, null, null, "Salon", false, ActivateTargetView.BT_ONLY, key = "Salon"),
        Row("registre, pas de pas publié", 1, "Salon", null, null, null, "Salon", false, ActivateTargetView.NOT_JOINED),
        Row("registre non joint", 1, "Salon", step(LinkState.TvUnreachable(AbsentKind.NO_ANSWER)), null, null, "Salon", false, ActivateTargetView.NOT_JOINED),
        Row("registre sans TV choisie", 2, null, null, null, null, null, false, ActivateTargetView.NOT_JOINED),
        Row("registre non joint mais chemin code joint", 1, "Salon", null, pin, base, "Chambre", true, null, tbase = base, key = pin),
        Row("registre joint prime sur le chemin code", 1, "Salon", lan, pin, base, "Salon", false, null, tbase = sessionBase, key = "Salon"),
        Row("registre joint BT, chemin code ignoré", 1, "Salon", bt, pin, base, "Salon", false, ActivateTargetView.BT_ONLY),
    )

    @Test fun table() {
        for (r in rows) {
            val (t, m) = ActivateTargetView.decide(r.saved, r.def, r.step, r.pin, r.base, r.session)
            if (r.tbase != null) assertEquals(r.tbase, t?.base, "${r.label} : base")
            t?.let { assertEquals(if (it.viaPin) r.pin else r.def, it.credentialKey, "${r.label} : clé du crédit") }
            assertEquals(r.target, t?.name, "${r.label} : cible")
            assertEquals(r.viaPin, t?.viaPin ?: false, "${r.label} : chemin code")
            assertEquals(r.msg, m, "${r.label} : message")
        }
    }

    @Test fun noTvMessageNeverWhenPinTvKnown() {
        for (saved in 0..2) for (b in listOf(null, base)) {
            val (_, m) = ActivateTargetView.decide(saved, null, null, pin, b, null)
            assertNotEquals(ActivateTargetView.NO_TV, m)
        }
    }
}
