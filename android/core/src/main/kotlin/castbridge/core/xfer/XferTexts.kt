package castbridge.core.xfer

/** Transfer states as the phone's notifications see them (upload, Bluetooth, queue, download). `via`: null = Wi-Fi to the TV, "bluetooth", "wifi", "téléchargement" (download: same notice, no separate type). */
sealed class XferState {
    data class Uploading(val name: String, val sent: Long, val total: Long, val via: String?) : XferState()
    data class Waiting(val name: String, val reason: String) : XferState()
    data class Done(val name: String) : XferState()
    data class Failed(val name: String, val reason: String) : XferState()
    data class Cancelled(val name: String) : XferState()
}

/** What a notification shows. [progress] 0..100 or null; [final] = the last word (the service may then stop); [ongoing] = not dismissible. */
data class Notice(val title: String, val text: String, val progress: Int?, val final: Boolean, val ongoing: Boolean)

/** The notification texts of every transfer service, one source (« $text · $pct % » kept). Failed/Cancelled are final and never empty (F1 of W13: the service must show them). */
object XferTexts {
    const val TITLE = "CastBridge"

    fun notification(s: XferState): Notice = when (s) {
        is XferState.Uploading -> {
            val pct = if (s.total > 0) (s.sent * 100 / s.total).toInt().coerceIn(0, 100) else null
            val what = when (s.via) {
                "bluetooth" -> "Envoi Bluetooth de ${s.name}"
                "wifi" -> "Envoi de ${s.name} (Wi-Fi)"
                "téléchargement" -> "Téléchargement de ${s.name}"
                else -> "Envoi vers la TV"
            }
            Notice(TITLE, if (pct != null) "$what · $pct %" else what, pct, false, true)
        }
        is XferState.Waiting -> Notice(TITLE, "En attente du réseau (${s.reason})", null, false, true)
        is XferState.Done -> Notice(TITLE, "${s.name} : envoi terminé", 100, true, false)
        is XferState.Failed -> Notice(TITLE, "${s.name} : échec de l'envoi (${s.reason.ifBlank { "raison inconnue" }})", null, true, false)
        is XferState.Cancelled -> Notice(TITLE, "${s.name} : envoi annulé", null, true, false)
    }
}
