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

    /** State word of a copy the TV holds in part but that nobody writes any more (R-51), and what its chip line tells the owner to do. */
    const val STALLED_STATE = "interrompu"
    const val STALLED_HINT = "MENU > Copie : annuler"
    /** Start of the MENU line that cancels a copy (`ParentalHub.categoryOfLabel` puts every line that starts with it under the settings). */
    const val CANCEL_PREFIX = "Copie : annuler"

    /**
     * [items] = `TransferProgress.shown()`; [partials] = `ReceiverServer.receiving()` (name, got, total), used only for names not in [items];
     * [stalled] = `ReceiverServer.stalledCopies()` (name, got, total): copies the TV still holds but nobody writes any more (R-51), one « interrompu » card each, after the
     * running ones, never beside a card of the same name (any case) and never twice.
     */
    fun of(items: List<TransferProgress.Item>, partials: List<Triple<String, Long, Long>> = emptyList(), serverUp: Boolean = true,
           stalled: List<Triple<String, Long, Long>> = emptyList()): List<ReceiveCard> {
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
        val taken = HashSet<String>(known)
        partials.mapTo(taken) { it.first.lowercase() }
        for ((n, got, total) in stalled) {
            if (!taken.add(n.lowercase())) continue
            val t = castbridge.core.tv.LibraryLogic.title(n)
            cards += ReceiveCard(t, got, total, "Wi-Fi", STALLED_STATE, "⏸ Copie interrompue : $t (${percent(got, total)} %) · $STALLED_HINT")
        }
        return cards
    }

    /** Whole percent received, never below 0 nor above 100; an unknown size (0) reads 0 %. */
    fun percent(received: Long, total: Long): Int = if (total <= 0) 0 else (received * 100 / total).toInt().coerceIn(0, 100)

    /** The MENU line (and the line of the « Recevoir du téléphone » tile) that cancels a copy the TV holds in part: « Copie : annuler « Film » (42 %, interrompue) ». */
    fun cancelLine(name: String, received: Long, total: Long, live: Boolean): String =
        "$CANCEL_PREFIX « ${castbridge.core.tv.LibraryLogic.title(name)} » (${percent(received, total)} %, ${if (live) "en cours" else "interrompue"})"

    /** The status word of the chip: « Démarrage… » until the receiving service is up, « Prêt à recevoir » after (also while receiving: the line says the rest). */
    fun ready(serverUp: Boolean): String = if (serverUp) READY else STARTING

    /** The reception line under the status word; null = nothing is being received. A second copy adds « (+1 autre) ». */
    fun headline(cards: List<ReceiveCard>): String? =
        cards.firstOrNull()?.let { it.line + (cards.size - 1).let { o -> if (o > 0) "  (+$o autre${if (o > 1) "s" else ""})" else "" } }
}
