package castbridge.core.xfer

/**
 * One reception on the TV home. [name] is the readable title (LibraryLogic.title), [via] = "Wi-Fi" | "Wi-Fi multivoie" | "Bluetooth";
 * [state] = "en cours" | "terminé" | "échec" | "interrompu"; [line] = the chip line of this one ([TransferProgress.Item.screenLine]).
 */
data class ReceiveCard(val name: String, val received: Long, val total: Long, val via: String, val state: String, val line: String)

/**
 * The TV home chip as PURE functions (R-04). Source: [TransferProgress.shown] (Wi-Fi, Wi-Fi multivoie and Bluetooth all feed it: the
 * Bluetooth `1-bt` status string is no longer a source) plus the old `.part` listing as a fallback for what progress does not know.
 */
object ReceiveCards {
    const val READY = "Prêt à recevoir"
    const val STARTING = "Démarrage…"

    /** [items] = `TransferProgress.shown()`; [partials] = `ReceiverServer.receiving()` (name, got, total), used only for names not in [items]. */
    fun of(items: List<TransferProgress.Item>, partials: List<Triple<String, Long, Long>> = emptyList(), serverUp: Boolean = true): List<ReceiveCard> {
        val cards = items.map {
            ReceiveCard(it.title, it.received, it.total, it.transport.label, when (it.phase) {
                TransferProgress.Phase.RUNNING -> "en cours"; TransferProgress.Phase.DONE -> "terminé"
                TransferProgress.Phase.FAILED -> "échec"; TransferProgress.Phase.ABORTED -> "interrompu"
            }, it.screenLine())
        }.toMutableList()
        val known = items.map { it.name.lowercase() }.toSet()
        for ((n, got, total) in partials) {
            if (n.lowercase() in known) continue
            val t = castbridge.core.tv.LibraryLogic.title(n)
            cards += ReceiveCard(t, got, total, "Wi-Fi", "en cours", "⬇ Réception de $t : ${got * 100 / total.coerceAtLeast(1)} %")
        }
        return cards
    }

    /** The status word of the chip: « Démarrage… » until the receiving service is up, « Prêt à recevoir » after (also while receiving: the line says the rest). */
    fun ready(serverUp: Boolean): String = if (serverUp) READY else STARTING

    /** The reception line under the status word; null = nothing is being received. A second copy adds « (+1 autre) ». */
    fun headline(cards: List<ReceiveCard>): String? =
        cards.firstOrNull()?.let { it.line + (cards.size - 1).let { o -> if (o > 0) "  (+$o autre${if (o > 1) "s" else ""})" else "" } }
}
