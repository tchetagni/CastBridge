package castbridge.core.trust

import kotlin.test.*

class ManualTvViewTest {
    private val base = "http://192.168.0.5:8765"
    private fun f(base: String? = this.base, stored: Boolean = true, status: Int? = null, ok: Long? = null, locked: Boolean = false, trusted: Boolean = false) =
        ManualTvFacts(base, stored, status, ok, 1_000L, locked, trusted)

    private data class Row(val label: String, val f: ManualTvFacts, val reach: String?, val pin: String?, val send: Boolean)
    private val rows = listOf(
        Row("pas d'adresse", f(base = null), null, null, false),
        Row("jamais interrogée (défaut actuel : joignable)", f(), null, null, true),
        Row("réponse 200", f(status = 200, ok = 900), null, null, true),
        Row("réponse 204", f(status = 204), null, null, true),
        Row("aucune réponse (0)", f(status = 0), ManualTvViews.UNREACHABLE, null, true),
        Row("500", f(status = 500), ManualTvViews.UNREACHABLE, null, true),
        Row("401 code faux", f(status = 401), null, ManualTvViews.BAD_PIN, false),
        Row("401 verrouillée", f(status = 401, locked = true), null, ManualTvViews.LOCKED, false),
        Row("401 jeton expiré", f(status = 401, trusted = true), null, ManualTvViews.EXPIRED, false),
        Row("401 verrouillée prime sur jeton", f(status = 401, locked = true, trusted = true), null, ManualTvViews.LOCKED, false),
        Row("code non gardé", f(stored = false, status = 200), null, null, false),
        Row("pas d'adresse même si 0", f(base = null, status = 0), null, null, false),
    )

    @Test fun table() {
        for (r in rows) {
            val v = ManualTvViews.decide(r.f)
            assertEquals(r.reach, v.reachableText, "${r.label} : injoignable")
            assertEquals(r.pin, v.pinError, "${r.label} : erreur de code")
            assertEquals(r.send, v.canSend, "${r.label} : envoi")
        }
    }
}
