package castbridge.core.xfer

/** One reception in progress on the TV home. [via] = "Wi-Fi" | "Bluetooth"; [state] = "en cours" | "terminé". */
data class ReceiveCard(val name: String, val received: Long, val total: Long, val via: String, val state: String)

/** The TV home's reception cards from `TransferHost.stateJson` objects (a JSON array) and the `1-bt` status line, plus the one-line headline. */
object ReceiveCards {
    const val READY = "Prêt à recevoir"
    private val OBJ = Regex("""\{"id":""")
    private val NAME = Regex(""""name":"((?:[^"\\]|\\.)*)"""")
    private val BT = Regex("""réception de (.+) (\d{1,3}) %""")
    private fun num(o: String, k: String) = Regex(""""$k":(\d+)""").find(o)?.groupValues?.get(1)?.toLongOrNull()
    private fun unq(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n")

    fun of(transfersJson: String, btStatusLine: String?): List<ReceiveCard> {
        val cards = ArrayList<ReceiveCard>()
        val starts = OBJ.findAll(transfersJson).map { it.range.first }.toList()
        starts.forEachIndexed { i, st ->
            val o = transfersJson.substring(st, starts.getOrNull(i + 1) ?: transfersJson.length)
            val name = NAME.find(o)?.groupValues?.get(1)?.let(::unq) ?: return@forEachIndexed
            val size = num(o, "size") ?: 0; val blocks = num(o, "blocks") ?: 0; val done = num(o, "done") ?: 0
            val got = if (blocks > 0) (size * done.coerceAtMost(blocks) / blocks) else 0
            cards += ReceiveCard(name, got, size, "Wi-Fi", if (o.contains("\"ready\":true") || (blocks > 0 && done >= blocks)) "terminé" else "en cours")
        }
        // REGRESSION R-04 : comportement actuel (BtServer.kt:113-127) : la ligne 1-bt est une chaîne écrasée ; seule « réception de X n % » donne une carte
        btStatusLine?.let { BT.find(it) }?.let { m -> cards += ReceiveCard(m.groupValues[1], m.groupValues[2].toLong().coerceIn(0, 100), 100, "Bluetooth", "en cours") }
        return cards
    }

    /** « Prêt à recevoir » only when nothing is being received. */
    fun headline(cards: List<ReceiveCard>): String = when {
        cards.isEmpty() -> READY
        cards.size > 1 -> "Réception de ${cards.size} fichiers"
        else -> cards[0].let { c ->
            val pct = if (c.total > 0) (c.received * 100 / c.total).toInt() else 0
            if (c.via == "Bluetooth") "Réception par Bluetooth : ${c.name} $pct %" else "Réception de ${c.name} : $pct %"
        }
    }
}
