package castbridge.core.lots

/**
 * Wire form of the rental right (docs/RENTAL-LOTS.md, annex of docs/ACTIVATION-FORMAT.md), one `right=` line of an `activation` body:
 * `rental|<product>|<bundles>|<startsAt ms>|<period ms>|<days>|<grace ms>|<max usage minutes>|<max concurrent>|<box>`.
 * ADDED without touching any existing line: a device that does not know the kind keeps the line verbatim ([Right.Unknown]) and grants nothing.
 */
object RentalLines {
    const val KIND = "rental"
    const val DAY_MS = 24L * 3600 * 1000
    const val MAX_DAYS = 366
    const val MAX_GRACE_DAYS = 30
    const val MAX_USAGE_MINUTES = 366 * 24 * 60
    const val MAX_CONCURRENT = 20
    private val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
    private val KIND_NAME = Regex("^[a-z][a-z0-9-]{0,31}$")
    private val BOX = Regex("^[A-Za-z0-9_;:+-]{0,4096}$")

    fun line(r: Right.Rental) = "$KIND|${r.productId}|${r.bundleIds.sorted().joinToString(",")}|${r.startsAt}|${r.period}|${r.durationDays}|${r.graceMs}|${r.maxUsageMinutes}|${r.maxConcurrent}|${r.box}"

    /** [f] = the line split on `|` (f[0] == "rental"). Throws on a malformed line (the whole activation is then MALFORMED, like any known right). */
    fun parse(f: List<String>): Right.Rental {
        require(f.size == 10 && ID.matches(f[1]) && BOX.matches(f[9]))
        val bundles = f[2].split(',').filter { it.isNotEmpty() }.onEach { require(ID.matches(it)) }
        return Right.Rental(f[1], bundles, f[3].toLong(), f[4].toLong(), f[5].toInt(), f[6].toLong(), f[7].toInt(), f[8].toInt(), f[9])
    }

    /** A right of another kind: kept verbatim (it only has to look like a right line), grants nothing. */
    fun unknown(raw: String): Right.Unknown {
        require(raw.isNotEmpty() && !raw.contains('\n') && KIND_NAME.matches(raw.substringBefore('|')))
        return Right.Unknown(raw)
    }

    /** Why a rental line is out of bounds (French), or null. Checked by the issuer and by the device (BAD_RIGHTS). */
    fun bounds(r: Right.Rental): String? = when {
        r.durationDays !in 1..MAX_DAYS -> "durée de 1 à $MAX_DAYS jours"
        r.graceMs !in 0..MAX_GRACE_DAYS * DAY_MS -> "tolérance de 0 à $MAX_GRACE_DAYS jours"
        r.maxUsageMinutes !in 0..MAX_USAGE_MINUTES -> "plafond d'usage hors bornes"
        r.maxConcurrent !in 0..MAX_CONCURRENT -> "locations simultanées hors bornes"
        r.startsAt <= 0 || r.period <= 0 || r.period > r.startsAt -> "dates invalides (le début de la période ne peut pas suivre le début de la ligne)"
        r.bundleIds.isEmpty() -> "aucun bouquet"
        !ID.matches(r.productId) || r.bundleIds.any { !ID.matches(it) } -> "identifiant invalide"
        !BOX.matches(r.box) -> "enveloppe de clé invalide"
        else -> null
    }
}
