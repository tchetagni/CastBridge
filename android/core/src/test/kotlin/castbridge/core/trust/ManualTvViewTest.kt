package castbridge.core.trust

import kotlin.test.*

class ManualTvViewTest {
    private val base = "http://192.168.0.5:8765"
    private fun f(base: String? = this.base, stored: Boolean = true, status: Int? = null, locked: Boolean = false, trusted: Boolean = false) =
        ManualTvFacts(base, stored, status, locked, trusted)

    private data class Row(val label: String, val f: ManualTvFacts, val reach: String?, val pin: String?, val send: Boolean)
    private val rows = listOf(
        Row("pas d'adresse", f(base = null), null, null, false),
        Row("réponse 200", f(status = 200), null, null, true),
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

    /** R-01 : DÉFAUT REPRODUIT, assertions sur le comportement ACTUEL (TvScreen.kt:61). w14-06 doit inverser : jamais interrogée => « vérification », envoi refusé. */
    @Test fun regressionR01_neverAskedCountsAsReachableAndSendable() {
        val v = ManualTvViews.decide(f())
        assertNull(v.reachableText); assertNull(v.pinError); assertTrue(v.canSend)
    }

    @Test fun wordingUsesCodeNotPinAndOneAction() {
        assertEquals("Code incorrect : retapez le code affiché sur la TV.", ManualTvViews.BAD_PIN)
        assertEquals("Trop d'essais : la TV est verrouillée 60 s, attendez puis réessayez.", ManualTvViews.LOCKED)
        for (m in listOf(ManualTvViews.BAD_PIN, ManualTvViews.LOCKED, ManualTvViews.EXPIRED)) assertFalse(m.contains("PIN"), m)
    }
}
