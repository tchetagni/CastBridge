package castbridge.core.sales

import castbridge.core.owner.Base32C
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset

/**
 * The receipt of a sale (docs/coordination/DESIGN-W4-VENTE-TERRAIN.md § 4): a short code `R-XXXX-XXXX` (7 Crockford characters from a hash of agent, sequence, device and time, plus 1 check
 * character) and a French text to share (WhatsApp, SMS, print). The owner finds a receipt on the server by its code; a customer who disputes shows it.
 */
object Receipt {
    private const val DOMAIN = "castbridge-receipt-v1|"

    /** `R-XXXX-XXXX` of the sale number [seq] of the agent [agentKid] for the TV [deviceCode] at [at] (ms). */
    fun code(agentKid: String, seq: Long, deviceCode: String, at: Long): String {
        val hash = MessageDigest.getInstance("SHA-256").digest("$DOMAIN$agentKid|$seq|$deviceCode|$at".toByteArray(Charsets.UTF_8))
        val data = Base32C.encode(hash.copyOf(5)).take(7)
        return "R-" + (data + Base32C.check(data, salt = 0)).chunked(4).joinToString("-")
    }

    /** The normalised code in [text] (typed by a human: lower case, spaces, O for 0, I or L for 1 are tolerated), or null when the shape or the check character is wrong. */
    fun parse(text: String): String? {
        val s = text.trim().uppercase()
        val t = (if (s.startsWith("R-")) s.drop(2) else s).filter { it != '-' && !it.isWhitespace() }
        if (t.length != 8) return null
        val norm = t.map { c -> Base32C.value(c).let { v -> if (v < 0) return null else Base32C.ALPHABET[v] } }.joinToString("")
        if (Base32C.check(norm.take(7), salt = 0) != norm[7]) return null
        return "R-" + norm.chunked(4).joinToString("-")
    }

    /**
     * The text of the receipt of a `SALE` [entry]: code, TV, what was bought, amount, agent name, date and [contact] (given by the caller, never written here). [grid] is only used for the
     * amount of an entry that has no `cash`.
     */
    fun text(entry: SalesLedger.Entry, name: String, contact: String, grid: PriceGrid.Verified? = null): String {
        require(entry.kind == SalesLedger.Kind.SALE) { "un reçu ne concerne qu'une vente" }
        val parts = entry.item!!.split('|')
        val amount = entry.cash ?: entry.price ?: parts.getOrNull(1)?.toIntOrNull()?.let { d -> grid?.priceOf(parts[0], d)?.toLong() }
        val date = Instant.ofEpochMilli(entry.at).atZone(ZoneOffset.UTC).toLocalDate().let { "%02d/%02d/%04d".format(it.dayOfMonth, it.monthValue, it.year) }
        return listOfNotNull("CastBridge", "Reçu ${entry.receipt}", "TV ${entry.device}", label(parts), amount?.let { "${xaf(it)} XAF" }, "Point focal $name", date, contact.takeIf { it.isNotBlank() }).joinToString(" — ")
    }

    private fun label(p: List<String>): String = when {
        p[0] == "cle-production" -> "Version complète ${p[1]} jours"
        p[0] == "cle-essai" -> "Clé d'essai ${p[1]} jours"
        p[0] == "bon" -> "Bon de recharge ${p[1]}"
        p[0] == "commande" -> "Commande ${p[1]}"
        p[0].startsWith("loc-") -> "Location ${p[0].removePrefix("loc-")} ${p[1]} jours"
        else -> p.joinToString(" ")
    }

    /** 5000 -> "5 000" (plain space). */
    private fun xaf(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()
}
