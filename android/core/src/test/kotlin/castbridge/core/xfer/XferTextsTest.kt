package castbridge.core.xfer

import kotlin.test.*

class XferTextsTest {
    private data class Row(val label: String, val s: XferState, val text: String, val pct: Int?, val final: Boolean, val ongoing: Boolean)
    private val rows = listOf(
        Row("Wi-Fi 50 %", XferState.Uploading("a.mp4", 50, 100, null), "Envoi vers la TV · 50 %", 50, false, true),
        Row("Wi-Fi 0 %", XferState.Uploading("a.mp4", 0, 100, null), "Envoi vers la TV · 0 %", 0, false, true),
        Row("Wi-Fi 100 %", XferState.Uploading("a.mp4", 100, 100, null), "Envoi vers la TV · 100 %", 100, false, true),
        Row("taille inconnue", XferState.Uploading("a.mp4", 5, 0, null), "Envoi vers la TV", null, false, true),
        Row("dépassement borné", XferState.Uploading("a.mp4", 300, 100, null), "Envoi vers la TV · 100 %", 100, false, true),
        Row("Bluetooth", XferState.Uploading("a.mp4", 1, 4, "bluetooth"), "Envoi Bluetooth de a.mp4 · 25 %", 25, false, true),
        Row("Wi-Fi nommé", XferState.Uploading("a.mp4", 1, 2, "wifi"), "Envoi de a.mp4 (Wi-Fi) · 50 %", 50, false, true),
        Row("téléchargement", XferState.Uploading("b.mp4", 3, 4, "téléchargement"), "Téléchargement de b.mp4 · 75 %", 75, false, true),
        Row("attente réseau", XferState.Waiting("a.mp4", "pas de Wi-Fi"), "En attente du réseau (pas de Wi-Fi)", null, false, true),
        Row("terminé", XferState.Done("a.mp4"), "a.mp4 : envoi terminé", 100, true, false),
        Row("échec", XferState.Failed("a.mp4", "PIN incorrect"), "a.mp4 : échec de l'envoi (PIN incorrect)", null, true, false),
        Row("échec sans raison", XferState.Failed("a.mp4", ""), "a.mp4 : échec de l'envoi (raison inconnue)", null, true, false),
        Row("annulé", XferState.Cancelled("a.mp4"), "a.mp4 : envoi annulé", null, true, false),
    )

    @Test fun table() {
        for (r in rows) {
            val n = XferTexts.notification(r.s)
            assertEquals(r.text, n.text, r.label); assertEquals(r.pct, n.progress, r.label)
            assertEquals(r.final, n.final, r.label); assertEquals(r.ongoing, n.ongoing, r.label)
            assertEquals("CastBridge", n.title, r.label)
        }
    }

    @Test fun everyStateHasText() {
        val all = listOf(XferState.Uploading("x", 1, 2, null), XferState.Waiting("x", "r"), XferState.Done("x"), XferState.Failed("x", "r"), XferState.Cancelled("x"))
        for (s in all) assertTrue(XferTexts.notification(s).text.isNotBlank())
        for (s in all.filter { it is XferState.Failed || it is XferState.Cancelled || it is XferState.Done }) assertTrue(XferTexts.notification(s).final)
        for (s in all.filter { it is XferState.Uploading || it is XferState.Waiting }) assertFalse(XferTexts.notification(s).final)
    }
}
