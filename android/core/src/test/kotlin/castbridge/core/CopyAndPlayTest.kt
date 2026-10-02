package castbridge.core

import castbridge.core.phone.MediaKind
import castbridge.core.trust.*
import castbridge.core.trust.CopyAndPlay.Decision
import castbridge.core.trust.CopyAndPlay.Edition
import castbridge.core.trust.CopyAndPlay.Facts
import castbridge.core.trust.CopyAndPlay.Link
import kotlin.test.*

/**
 * « Copier et lire sur la TV » of « Ouvrir avec CastBridge »: one row per situation (link x transport x edition x media kind),
 * and the mapping from the dialog's own facts ([SendFacts]/[SendChoice]) to the link kind. Pure: the activity only draws the result.
 */
class CopyAndPlayTest {
    private enum class Want { ENABLED, DEGRADED, DISABLED }
    private data class Row(val label: String, val f: Facts, val want: Want, val says: String? = null)
    private fun f(link: Link, ip: Boolean = true, ed: Edition = Edition.PRODUCTION, kind: MediaKind = MediaKind.VIDEO) = Facts(link, ip, ed, kind)

    private val rows = listOf(
        Row("session de confiance, production, vidéo", f(Link.SESSION), Want.ENABLED),
        Row("session de confiance, édition inconnue", f(Link.SESSION, ed = Edition.UNKNOWN), Want.ENABLED),
        Row("cas du propriétaire : TV à code connectée, registre vide", f(Link.PIN_PATH, ed = Edition.UNKNOWN), Want.ENABLED),
        Row("TV à code connectée, production", f(Link.PIN_PATH), Want.ENABLED),
        Row("vérification en cours : actif comme « Copier »", f(Link.CHECKING, ed = Edition.UNKNOWN), Want.ENABLED),
        Row("vérification en cours, production", f(Link.CHECKING), Want.ENABLED),
        Row("audio", f(Link.SESSION, kind = MediaKind.AUDIO), Want.ENABLED),
        Row("image", f(Link.PIN_PATH, kind = MediaKind.IMAGE), Want.ENABLED),
        Row("TV d'essai, session : lire seulement", f(Link.SESSION, ed = Edition.TRIAL), Want.DEGRADED, says = "La copie n'est pas disponible en version d'essai"),
        Row("TV d'essai, code PIN : lire seulement", f(Link.PIN_PATH, ed = Edition.TRIAL), Want.DEGRADED, says = "La copie n'est pas disponible en version d'essai"),
        Row("TV d'essai, vérification : lire seulement", f(Link.CHECKING, ed = Edition.TRIAL), Want.DEGRADED, says = "version d'essai"),
        Row("Bluetooth seul, production", f(Link.SESSION, ip = false), Want.DISABLED, says = "Wi-Fi"),
        Row("Bluetooth seul, essai : le Bluetooth l'emporte", f(Link.SESSION, ip = false, ed = Edition.TRIAL), Want.DISABLED, says = "Wi-Fi"),
        Row("aucune TV prête", f(Link.NONE), Want.DISABLED, says = "TV"),
        Row("aucune TV prête, essai", f(Link.NONE, ed = Edition.TRIAL), Want.DISABLED),
        Row("fichier non lisible (autre type)", f(Link.SESSION, kind = MediaKind.OTHER), Want.DISABLED, says = "lire"),
        Row("fichier non lisible, TV d'essai", f(Link.PIN_PATH, ed = Edition.TRIAL, kind = MediaKind.OTHER), Want.DISABLED),
        Row("fichier non lisible, aucune TV", f(Link.NONE, kind = MediaKind.OTHER), Want.DISABLED),
    )

    @Test fun table() {
        assertTrue(rows.size >= 15)
        for (r in rows) {
            val d = CopyAndPlay.decide(r.f)
            when (r.want) {
                Want.ENABLED -> { assertIs<Decision.Enabled>(d, r.label); assertTrue(d.copies, r.label); assertEquals(CopyAndPlay.LABEL, d.label, r.label); assertNull(d.reason, r.label) }
                Want.DEGRADED -> { assertIs<Decision.Degraded>(d, r.label); assertFalse(d.copies, r.label); assertEquals(CopyAndPlay.LABEL_PLAY_ONLY, d.label, r.label); assertNotNull(d.reason, r.label) }
                Want.DISABLED -> { assertIs<Decision.Disabled>(d, r.label); assertFalse(d.copies, r.label); assertFalse(d.reason.isNullOrBlank(), r.label) }
            }
            r.says?.let { assertTrue(it in d.reason.orEmpty(), "${r.label}: « ${d.reason} » devrait contenir « $it »") }
            d.reason?.let { assertEquals(1, it.lines().size, "${r.label}: une seule ligne") }
        }
    }

    @Test fun aCopyIsPromisedOnlyByEnabledAndNeverInTheTrial() {
        for (e in Edition.values()) for (l in Link.values()) for (ip in listOf(true, false)) for (k in MediaKind.values()) {
            val d = CopyAndPlay.decide(f(l, ip, e, k))
            assertEquals(d is Decision.Enabled, d.copies, "$l/$ip/$e/$k")
            if (e == Edition.TRIAL) assertFalse(d.copies, "$l/$ip/$e/$k")
        }
    }

    // ---- link kind from the dialog's own facts (the activity feeds SendFacts to SendChoices.decide, then this)
    private val machine = LinkMachine()
    private fun linkOf(sf: SendFacts) = CopyAndPlay.linkOf(sf, SendChoices.decide(sf))

    @Test fun linkKindFollowsTheDialogStates() {
        val ok = machine.view(LinkMachine.Model(shown = LinkState.Connected(RouteKind.LAN, "Salon"), tvName = "Salon"))
        val pin = "CastBridge TV SMART_TV"
        assertEquals(Link.NONE, linkOf(SendFacts()))
        assertEquals(Link.SESSION, linkOf(SendFacts(1, "Salon", ok, session = true, sessionName = "Salon")))
        assertEquals(Link.SESSION, linkOf(SendFacts(1, "Salon", ok, session = true, sessionName = "Salon", btOnly = true)))
        assertEquals(Link.CHECKING, linkOf(SendFacts(1, "Salon", null)))
        assertEquals(Link.PIN_PATH, linkOf(SendFacts(pinTvName = pin, pinStored = true, pinCheck = PinCheck.OK)))
        assertEquals(Link.CHECKING, linkOf(SendFacts(pinTvName = pin, pinStored = true, pinCheck = PinCheck.UNKNOWN)))
        assertEquals(Link.NONE, linkOf(SendFacts(pinTvName = pin, pinStored = true, pinCheck = PinCheck.REJECTED)))
        assertEquals(Link.NONE, linkOf(SendFacts(pinTvName = pin, pinStored = false)))
        assertEquals(Link.NONE, linkOf(SendFacts(2, null, null)))
    }

    @Test fun ownersCaseEndToEnd() {
        // PIN-path TV connected, trusted registry empty
        val sf = SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.OK)
        assertIs<Decision.Enabled>(CopyAndPlay.decide(Facts(linkOf(sf), true, Edition.UNKNOWN, MediaKind.VIDEO)))
    }
}
