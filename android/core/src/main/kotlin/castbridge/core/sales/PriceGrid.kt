package castbridge.core.sales

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.Signer
import castbridge.core.update.Ed25519
import java.util.Base64

/**
 * The signed price grid of the field agents (docs/coordination/DESIGN-W4-VENTE-TERRAIN.md § 6): `content/prices.json`, signed OFFLINE with the key of the app updates
 * ([castbridge.core.update.UpdateKeys]), relayed by the server, read by the agent app and the owner tools. Same discipline as [castbridge.core.lots.SignedBundleCatalog]: an unsigned,
 * altered or older grid is refused; an item that is not in the grid cannot be sold. No amount is written in the code.
 *
 * File: `{"format","generatedAt","currency":"XAF","prices":[{"item","days","price"}…],"signature","keyId"}`. Signed text (keep identical to the Python signer): `castbridge-price-grid-v1`,
 * `generatedAt=…`, `currency=XAF`, then the lines `price=<item>|<days>|<xaf>` sorted as plain text (strict ASCII / UTF-16 order of the whole line, not numeric: `price=a|365|…` comes before `price=a|90|…`). The Python signer of w4-17 MUST sort the same way (plain `sorted()` on the lines) and write days and prices as JSON integers.
 */
object PriceGrid {
    const val FORMAT = "castbridge-price-grid-v1"
    const val CURRENCY = "XAF"
    private val AT = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")
    private val ITEM = Regex("^[a-z0-9][a-z0-9-]{0,63}$")

    /** One line of the grid: [item] sold for [days] days at [xaf] francs. */
    data class Price(val item: String, val days: Int, val xaf: Int)

    /** A grid that passed every check; [json] is the text to keep (it holds the signature). */
    class Verified(val generatedAt: String, private val prices: List<Price>, val keyId: String?, val json: String) {
        /** Every line of the grid, sorted like the signed text. */
        fun items(): List<Price> = prices
        /** The price of [item] for [days] days, or null: not in the grid, so not sellable. */
        fun priceOf(item: String, days: Int): Int? = prices.firstOrNull { it.item == item && it.days == days }?.xaf
        val dateFr: String get() = if (generatedAt.length >= 10) "${generatedAt.substring(8, 10)}/${generatedAt.substring(5, 7)}/${generatedAt.substring(0, 4)}" else generatedAt
    }

    /** Refused grid; the message is French and meant for the screen. */
    class Refused(message: String) : Exception(message)

    /** Largest price of a line, in francs. */
    const val MAX_XAF = 100_000_000L

    /**
     * [v] must be an exact JSON integer ([Long]): a decimal (30.9, 30.0) or a string throws (the grid is refused as unreadable), never truncated. A Long beyond [range] is not narrowed
     * (4294972296 would wrap to 5000): it is saturated to a value outside the bounds, so the bounds check refuses the grid.
     */
    private fun exactInt(v: Any?, range: LongRange): Int {
        require(v is Long) { "entier exact attendu" }
        return if (v in range) v.toInt() else if (v < range.first) range.first.toInt() - 1 else range.last.coerceAtMost(Int.MAX_VALUE - 1L).toInt() + 1
    }

    private fun line(p: Price) = "price=${p.item}|${p.days}|${p.xaf}"

    fun canonicalPayload(generatedAt: String, prices: List<Price>): String =
        (listOf(FORMAT, "generatedAt=$generatedAt", "currency=$CURRENCY") + prices.map(::line).sorted()).joinToString("\n")

    /** A signed grid file (the owner's tools and the tests; production grids are signed by the Python tool with the updates key). */
    fun signedJson(signer: Signer, generatedAt: String, prices: List<Price>): String {
        val sig = Base64.getEncoder().encodeToString(signer.sign(canonicalPayload(generatedAt, prices).toByteArray(Charsets.UTF_8)))
        return JsonLite.write(linkedMapOf("format" to FORMAT, "generatedAt" to generatedAt, "currency" to CURRENCY,
            "prices" to prices.sortedBy(::line).map { linkedMapOf("item" to it.item, "days" to it.days, "price" to it.xaf) }, "keyId" to signer.keyId, "signature" to sig))
    }

    /**
     * Parses and verifies [json] against [publicKeys] (base64 raw keys). [notOlderThan] is the `generatedAt` of the grid already kept: an older signed grid is refused
     * (a replay of an old file would bring back old prices).
     */
    fun verify(json: String, publicKeys: List<String>, notOlderThan: String? = null): Verified {
        val m = try { JsonLite.obj(json) } catch (e: Exception) { throw Refused("Grille illisible : ${e.message}") }
        if (m.str("format") != FORMAT) throw Refused("Grille de prix de format inconnu : refusée")
        val sig = m.str("signature")?.takeIf { it.isNotBlank() && it != "UNSIGNED" } ?: throw Refused("Grille non signée : refusée")
        val at = m.str("generatedAt")?.takeIf { AT.matches(it) } ?: throw Refused("Grille sans date de génération valide : refusée")
        if (m.str("currency") != CURRENCY) throw Refused("Grille dans une autre monnaie que $CURRENCY : refusée")
        val prices = try {
            (m["prices"] as List<*>).map { e ->
                val p = e as Map<*, *>
                Price(p["item"] as String, exactInt(p["days"], 1L..3660L), exactInt(p["price"], 0L..MAX_XAF))
            }
        } catch (e: Exception) { throw Refused("Grille illisible : lignes de prix invalides") }
        if (prices.isEmpty()) throw Refused("Grille vide")
        if (prices.any { !ITEM.matches(it.item) || it.days !in 1..3660 || it.xaf < 0 || it.xaf > MAX_XAF }) throw Refused("Grille invalide : article, durée ou montant hors bornes")
        if (prices.map { it.item to it.days }.toSet().size != prices.size) throw Refused("Grille invalide : article en double")
        val keys = publicKeys.filter { it.isNotBlank() }
        if (keys.isEmpty()) throw Refused("Aucune clé de vérification dans cette application : grille refusée")
        val msg = canonicalPayload(at, prices).toByteArray(Charsets.UTF_8)
        val ok = try {
            val raw = Base64.getDecoder().decode(sig)
            keys.any { k -> try { Ed25519.verify(Base64.getDecoder().decode(k.trim()), msg, raw) } catch (e: IllegalArgumentException) { false } }
        } catch (e: IllegalArgumentException) { false }
        if (!ok) throw Refused("Signature de la grille invalide (fichier modifié ou signé avec une autre clé) : refusée")
        if (notOlderThan != null && AT.matches(notOlderThan) && at < notOlderThan) throw Refused("Grille plus ancienne que celle déjà enregistrée : refusée")
        return Verified(at, prices.sortedBy(::line), m.str("keyId"), json)
    }
}
