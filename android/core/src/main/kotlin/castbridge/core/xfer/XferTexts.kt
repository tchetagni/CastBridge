package castbridge.core.xfer

/** Transfer states as the phone's notifications see them (upload, Bluetooth, queue, download). `via`: null = Wi-Fi to the TV, "bluetooth", "wifi", "téléchargement" (download: same notice, no separate type). */
sealed class XferState {
    data class Uploading(val name: String, val sent: Long, val total: Long, val via: String?) : XferState()
    data class Waiting(val name: String, val reason: String, val sent: Long = 0, val total: Long = 0) : XferState()
    /** The queue service (`TransferQueueService.kt:56-59`): [waitingText] = `TransferQueue.waitingText()` (null = nothing waits), [sent]/[total] = the file in flight, if any. */
    data class Queue(val waitingText: String?, val sent: Long = 0, val total: Long = 0) : XferState()
    data class Done(val name: String) : XferState()
    data class Failed(val name: String, val reason: String) : XferState()
    data class Cancelled(val name: String) : XferState()
}

/** What a notification shows. [progress] 0..100 or null; [final] = the last word (the service may then stop); [ongoing] = not dismissible. */
data class Notice(val title: String, val text: String, val progress: Int?, val final: Boolean, val ongoing: Boolean)

/** The notification texts of every transfer service, one source (« $text · $pct % » kept). Failed/Cancelled are final and never empty (F1 of W13: the service must show them). */
object XferTexts {
    const val TITLE = "CastBridge"

    const val QUEUE_TITLE = "CastBridge : envois vers la TV"
    const val QUEUE_RUNNING = "Envoi en cours"

    private fun pct(sent: Long, total: Long): Int? = if (total > 0) (sent * 100 / total).toInt().coerceIn(0, 100) else null

    fun notification(s: XferState): Notice = when (s) {
        is XferState.Uploading -> {
            val pct = pct(s.sent, s.total)
            val what = when (s.via) {
                "bluetooth" -> "Envoi Bluetooth de ${s.name}"
                "wifi" -> "Envoi de ${s.name} (Wi-Fi)"
                "téléchargement" -> "Téléchargement de ${s.name}"
                else -> "Envoi vers la TV"
            }
            Notice(TITLE, if (pct != null) "$what · $pct %" else what, pct, false, true)
        }
        is XferState.Waiting -> {
            val p = pct(s.sent, s.total)
            val t = "En attente du réseau (${s.reason})"
            Notice(TITLE, if (p != null) "$t · $p %" else t, p, false, true)
        }
        is XferState.Queue -> {
            val p = pct(s.sent, s.total)
            val t = s.waitingText ?: QUEUE_RUNNING
            Notice(QUEUE_TITLE, if (p != null) "$t · $p %" else t, p, false, true)
        }
        is XferState.Done -> Notice(TITLE, "${s.name} : envoi terminé", 100, true, false)
        is XferState.Failed -> Notice(TITLE, "${s.name} : échec de l'envoi (${s.reason.ifBlank { "raison inconnue" }})", null, true, false)
        is XferState.Cancelled -> Notice(TITLE, "${s.name} : envoi annulé", null, true, false)
    }
}
