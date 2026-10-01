package castbridge.core.lots

import castbridge.core.update.Ed25519
import java.util.Base64

/**
 * The right to open content beyond the free trial (docs/TRIAL-EDITION.md § Droit d'accès). Three kinds of rights ADD UP:
 *  1. the TRIAL: always there, never in a token (it is the floor: the "-trial" lots);
 *  2. a PURCHASE of a bundle (one lot, a class, a subject, a language with its levels, a Quiz pack): definitive;
 *  3. a SUBSCRIPTION to a set of bundles for a period, with an end date, an offline grace period and what happens at the end
 *     (the purchases stay, the rest goes back to the trial; see [EditionPolicy.reconcile]).
 *
 * No price and no payment provider here (owner's decisions): only product identifiers, kinds and durations.
 * A token is signed by the server (Ed25519, same key family as the lot catalog) and bound to ONE device.
 */
enum class ProductKind { PURCHASE, SUBSCRIPTION }

/** What a payment provider's product maps to. [periodDays] is for subscriptions only. */
data class Product(val id: String, val kind: ProductKind, val bundleIds: List<String>, val periodDays: Int? = null)

sealed class Right {
    abstract val productId: String
    abstract val bundleIds: List<String>

    data class Purchase(override val productId: String, override val bundleIds: List<String>, val grantedAt: Long) : Right()

    /** [endsAt] and [graceMs] decide the state; [autoRenew] is information for the screen (the server renews and re-issues the token). */
    data class Subscription(override val productId: String, override val bundleIds: List<String>, val startsAt: Long, val endsAt: Long,
                            val graceMs: Long, val autoRenew: Boolean) : Right()

    /** « Tout ouvert » : every bundle (the "tout" bundle) between [startsAt] and [endsAt], at most 30 days, no grace. Needs a key with the open-all scope. */
    data class OpenAll(override val productId: String, val startsAt: Long, val endsAt: Long) : Right() {
        override val bundleIds: List<String> get() = listOf(ALL_BUNDLE)
    }

    /**
     * LOCATION (docs/RENTAL-LOTS.md): bundles opened for [durationDays] from [startsAt] (at most 366, offline), with an optional offline [graceMs] and an optional second ceiling of
     * [maxUsageMinutes] of use (0 = none); [maxConcurrent] = at most that many rentals of one licence at once on a device (0 = no limit). [period] = start of the contract this line belongs to:
     * a RENEWAL is a new line with the same product and the same [period] (it extends the same rental, never duplicates it). [box] = the rental key wrapped per device ([RentalKeys]).
     * Wire line: [RentalLines]. A device that does not know this right ignores it and grants NOTHING (see [Unknown]).
     */
    data class Rental(override val productId: String, override val bundleIds: List<String>, val startsAt: Long, val period: Long, val durationDays: Int, val graceMs: Long = 0L,
                      val maxUsageMinutes: Int = 0, val maxConcurrent: Int = 0, val box: String = "") : Right() {
        val endsAt: Long get() = startsAt + durationDays * RentalLines.DAY_MS
    }

    /** A right line of a kind this build does not know (a newer format): kept VERBATIM so the signed canonical text rebuilds exactly, and it grants nothing, never "everything". */
    data class Unknown(val raw: String) : Right() {
        override val productId: String get() = "unknown"
        override val bundleIds: List<String> get() = emptyList()
    }

    companion object { const val ALL_BUNDLE = "tout" }
}

/** The signed content of a token. Everything the evaluation uses is in the signed [canonicalPayload]; nothing is read from elsewhere. */
data class Entitlement(val deviceId: String, val issuedAt: Long, val keyId: String, val rights: List<Right>, val signature: String) {
    fun canonicalPayload(): String = payload(deviceId, issuedAt, keyId, rights)

    fun signatureValid(publicKeyBase64: String): Boolean = try {
        Ed25519.verify(Base64.getDecoder().decode(publicKeyBase64.trim()), canonicalPayload().toByteArray(Charsets.UTF_8), Base64.getDecoder().decode(signature))
    } catch (e: IllegalArgumentException) { false }

    /** "cbe1.<payload base64url>.<signature base64>" : opaque for the user, parsed back from the very text that was signed. */
    fun encode(): String = "$PREFIX." + Base64.getUrlEncoder().withoutPadding().encodeToString(canonicalPayload().toByteArray(Charsets.UTF_8)) + "." + signature

    companion object {
        const val FORMAT = "castbridge-entitlement-v1"
        const val PREFIX = "cbe1"
        private val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val DEVICE = Regex("^[A-Za-z0-9._-]{1,64}$")

        fun payload(deviceId: String, issuedAt: Long, keyId: String, rights: List<Right>): String = buildList {
            add(FORMAT)
            add("device=$deviceId")
            add("issuedAt=$issuedAt")
            add("kid=$keyId")
            rights.map(::line).sorted().forEach { add("right=$it") }
        }.joinToString("\n")

        private fun line(r: Right) = when (r) {
            is Right.Purchase -> "purchase|${r.productId}|${r.bundleIds.sorted().joinToString(",")}|${r.grantedAt}"
            is Right.Subscription -> "subscription|${r.productId}|${r.bundleIds.sorted().joinToString(",")}|${r.startsAt}|${r.endsAt}|${r.graceMs}|${if (r.autoRenew) 1 else 0}"
            is Right.OpenAll -> "openall|${r.productId}|${r.startsAt}|${r.endsAt}"
            is Right.Rental -> RentalLines.line(r)
            is Right.Unknown -> r.raw
        }

        /** Null when the text is not a well-formed token (the signature is NOT checked here). */
        fun decode(token: String): Entitlement? = runCatching {
            val parts = token.trim().split('.')
            require(parts.size == 3 && parts[0] == PREFIX)
            val text = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            val lines = text.split('\n')
            require(lines[0] == FORMAT)
            fun field(i: Int, k: String) = lines[i].also { require(it.startsWith("$k=")) }.substringAfter('=')
            val device = field(1, "device").also { require(DEVICE.matches(it)) }
            val issued = field(2, "issuedAt").toLong()
            val keyId = field(3, "kid")
            val rights = lines.drop(4).map { l ->
                require(l.startsWith("right="))
                val f = l.removePrefix("right=").split('|')
                fun ids(s: String) = s.split(',').filter { it.isNotEmpty() }.onEach { require(ID.matches(it)) }
                when (f[0]) {
                    "purchase" -> { require(f.size == 4 && ID.matches(f[1])); Right.Purchase(f[1], ids(f[2]), f[3].toLong()) }
                    "subscription" -> { require(f.size == 7 && ID.matches(f[1])); Right.Subscription(f[1], ids(f[2]), f[3].toLong(), f[4].toLong(), f[5].toLong(), f[6] == "1") }
                    "openall" -> { require(f.size == 4 && ID.matches(f[1])); Right.OpenAll(f[1], f[2].toLong(), f[3].toLong()) }
                    RentalLines.KIND -> RentalLines.parse(f)
                    else -> RentalLines.unknown(l.removePrefix("right="))
                }
            }
            val e = Entitlement(device, issued, keyId, rights, parts[2])
            require(e.canonicalPayload() == text)        // the canonical form is the only accepted form
            e
        }.getOrNull()
    }
}

enum class SubState { NOT_STARTED, ACTIVE, GRACE, EXPIRED }
enum class Verdict { OK, NO_TOKEN, MALFORMED, BAD_SIGNATURE, OTHER_DEVICE }

data class SubStatus(val right: Right.Subscription, val state: SubState) {
    val graceEndsAt get() = right.endsAt + right.graceMs
}

/** What a device may open right now. Always includes the trial (that is [EditionPolicy]'s floor, not a bundle). */
data class Access(
    val verdict: Verdict,
    /** Bundles owned for good. */
    val purchased: Set<String>,
    /** Bundles reachable through a subscription that is ACTIVE or in GRACE. */
    val subscribed: Set<String>,
    val subscriptions: List<SubStatus>,
    val message: String,
    /** Single lots opened for a limited time by an owner command (no bundle needed); empty for ordinary tokens. */
    val extraLots: Set<LotId> = emptySet(),
    /** Bundles reachable ONLY through a usable rental (a bundle already owned or subscribed is never listed here: no double count). Empty unless the TV evaluated its rentals. */
    val rented: Set<String> = emptySet(),
    /** Every rental seen with its state and countdown (for the screens). */
    val rentals: List<RentalStatus> = emptyList(),
) {
    val granted: Set<String> get() = purchased + subscribed + rented
    fun grants(bundleId: String) = bundleId in granted
    /** True while a subscription is past its end but still honoured: the screen asks to reconnect. */
    val inGrace: Boolean get() = subscriptions.any { it.state == SubState.GRACE }

    companion object {
        val TRIAL_ONLY = Access(Verdict.NO_TOKEN, emptySet(), emptySet(), emptyList(), "Version d'essai")
    }
}

object Entitlements {
    /** Default offline tolerance a server can put in a subscription: 7 days after its end date to reach the Internet again. */
    const val DEFAULT_GRACE_MS = 7L * 24 * 3600 * 1000

    /**
     * Evaluates [token] for [deviceId]. [nowMs] is the phone's clock; [lastSeenMs] is the highest time the app ever saw (kept by the
     * app) so that setting the clock back does not extend a subscription; the token's own issue date is a floor too.
     * Nothing is thrown: a bad token gives the trial only, with the reason in French (and the caller keeps its previous good token).
     */
    fun evaluate(token: String?, deviceId: String, nowMs: Long, publicKeys: List<String>, lastSeenMs: Long = 0L): Access {
        if (token.isNullOrBlank()) return Access.TRIAL_ONLY
        fun bad(v: Verdict, why: String) = Access(v, emptySet(), emptySet(), emptyList(), why)
        val e = Entitlement.decode(token) ?: return bad(Verdict.MALFORMED, "Jeton de droits illisible : version d'essai")
        if (publicKeys.none { e.signatureValid(it) }) return bad(Verdict.BAD_SIGNATURE, "Jeton de droits non signé par le serveur CastBridge : version d'essai")
        if (e.deviceId != deviceId) return bad(Verdict.OTHER_DEVICE, "Ce jeton est lié à un autre appareil : version d'essai")
        val now = maxOf(nowMs, lastSeenMs, e.issuedAt)
        val purchased = e.rights.filterIsInstance<Right.Purchase>().flatMap { it.bundleIds }.toSortedSet()
        val subs = e.rights.filterIsInstance<Right.Subscription>().map { s ->
            SubStatus(s, when {
                now < s.startsAt -> SubState.NOT_STARTED
                now < s.endsAt -> SubState.ACTIVE
                now < s.endsAt + s.graceMs -> SubState.GRACE
                else -> SubState.EXPIRED
            })
        }
        val openAllOn = e.rights.filterIsInstance<Right.OpenAll>().any { now >= it.startsAt && now < it.endsAt }
        val subscribed = (subs.filter { it.state == SubState.ACTIVE || it.state == SubState.GRACE }.flatMap { it.right.bundleIds } +
            if (openAllOn) listOf(Right.ALL_BUNDLE) else emptyList()).toSortedSet()
        val msg = when {
            subs.any { it.state == SubState.GRACE } -> "Abonnement terminé : reconnectez-vous à Internet pour le renouveler (tolérance en cours)"
            subs.any { it.state == SubState.EXPIRED } -> "Abonnement terminé : vos achats restent, le reste repasse en version d'essai"
            else -> ""
        }
        return Access(Verdict.OK, purchased, subscribed, subs, msg)
    }
}
