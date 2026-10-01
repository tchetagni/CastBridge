package castbridge.core.owner

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import java.security.MessageDigest
import java.util.Base64

/**
 * Licences, seats and transfers (docs/ACTIVATION-FORMAT.md § Licences). A LICENCE is the unit of accounting: one per purchase, with a number of SEATS (devices that may be
 * activated), a yearly cap of transfers, and the rights its activations carry. Everything the three tools (desk, owner phone, server) do is written as SIGNED EVENTS; the REGISTRY is
 * simply the set of events, exported and imported as a JSON file. Replaying the events (sorted, deterministic) gives the state, so merging two registries is a set union:
 * no duplicates, no dependence on the order, and a seat issued twice by two tools for the same hardware is detected.
 *
 * HONEST LIMIT: offline, the old TV of a transferred seat does not learn that it lost its rights; the protection is the ledger (who issued what, how many seats are used).
 * Online, the server counts seats and can push a revocation ([RevocationNotice]) that the TV applies at its next connection.
 */
class LicenseEvent(val keyId: String, val text: String, val signature: String) {
    val id: String get() = MessageDigest.getInstance("SHA-256").digest("$keyId|$text".toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }
    val fields: Map<String, String> by lazy { text.split('\n').drop(1).filter { !it.startsWith("factor=") }.associate { it.substringBefore('=') to it.substringAfter('=') } }
    val factors: Map<FactorKind, String> by lazy {
        text.split('\n').filter { it.startsWith("factor=") }.associate { l -> l.removePrefix("factor=").split('|').let { FactorKind.valueOf(it[0]) to it[1] } }
    }
    val type: String get() = fields["type"] ?: ""
    val at: Long get() = fields["at"]?.toLongOrNull() ?: 0L

    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "kid" to keyId, "text" to text, "signature" to signature)

    companion object {
        const val FORMAT = "castbridge-licence-event-v1"
        fun fromMap(m: Map<String, Any?>): LicenseEvent? = runCatching { LicenseEvent(m.str("kid")!!, m.str("text")!!, m.str("signature")!!).takeIf { m.str("id") == it.id && it.text.startsWith(FORMAT + "\n") } }.getOrNull()

        private fun base(type: String, kid: String, at: Long, vararg more: Pair<String, Any>) =
            (listOf(FORMAT, "type=$type", "kid=$kid", "at=$at") + more.map { "${it.first}=${it.second}" })

        private fun sign(s: Signer, lines: List<String>, factors: Map<FactorKind, String> = emptyMap()): LicenseEvent {
            val text = (lines + factors.toSortedMap().map { "factor=${it.key.name}|${it.value}" }).joinToString("\n")
            return LicenseEvent(s.keyId, text, Base64.getEncoder().encodeToString(s.sign(text.toByteArray(Charsets.UTF_8))))
        }

        /** A purchase creates a licence with [seats] seats. Needs ISSUE_PRODUCTION. */
        fun license(s: Signer, at: Long, license: String, seats: Int, maxTransfersPerYear: Int = LicenseBook.DEFAULT_TRANSFERS_PER_YEAR) =
            sign(s, base("license", s.keyId, at, "license" to license, "seats" to seats, "maxTransfersPerYear" to maxTransfersPerYear))

        /** Every activation issued is logged (who issued what), with the seat it occupies. Needs ISSUE_TRIAL (trial) or ISSUE_PRODUCTION. */
        fun issue(s: Signer, a: Activation) =
            sign(s, base("issue", s.keyId, a.issuedAt, "license" to a.license, "seat" to a.seat, "subject" to a.subject.name.lowercase(), "kind" to a.kind.name.lowercase(),
                "nonce" to a.nonce, "notAfter" to a.notAfter, "k" to a.k), a.factors)

        /** Moves a seat to other hardware. Needs TRANSFER (never the server). */
        fun transfer(s: Signer, at: Long, license: String, seat: String, newFactors: Fingerprints, k: Int = DeviceIdentity.kFor(newFactors.n), nonce: String) =
            sign(s, base("transfer", s.keyId, at, "license" to license, "seat" to seat, "k" to k, "nonce" to nonce), newFactors.byKind)

        /** Revokes a key or a seat. Needs REVOKE. */
        fun revokeKey(s: Signer, at: Long, kid: String) = sign(s, base("revoke", s.keyId, at, "target" to "key", "value" to kid))
        fun revokeSeat(s: Signer, at: Long, license: String, seat: String) = sign(s, base("revoke", s.keyId, at, "target" to "seat", "value" to "$license|$seat"))
    }
}

/** What replaying the events produced. */
class LicenseState(
    val licenses: Map<String, LicenseInfo>,
    val seats: Map<String, SeatInfo>,                 // "license|seat"
    val transfers: List<TransferInfo>,
    val revocations: RevocationState,
    /** Same hardware issued under two seat ids (two tools): the later one is merged into the earlier one and counted once. */
    val duplicates: List<Pair<String, String>>,
    val warnings: List<String>,
    /** Events not applied, with the reason (unknown key, bad signature, scope missing, transfer cap, unknown licence). */
    val rejected: List<Pair<String, Rejection>>,
) {
    fun usedSeats(license: String) = seats.values.count { it.license == license }
    fun seatsLeft(license: String) = (licenses[license]?.seats ?: 0) - usedSeats(license)

    /** The registry the three tools exchange has the same state when the same events were applied: this digest compares them. */
    fun digest(): String = MessageDigest.getInstance("SHA-256").digest(
        (licenses.toSortedMap().values.joinToString("\n") { "L|${it.id}|${it.seats}|${it.maxTransfersPerYear}" } + "\n" +
            seats.toSortedMap().values.joinToString("\n") { "S|${it.license}|${it.seatId}|${it.factors.toSortedMap()}|${it.issuedBy.sorted()}" } + "\n" +
            transfers.joinToString("\n") { "T|${it.license}|${it.seat}|${it.at}" }).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

data class LicenseInfo(val id: String, val seats: Int, val maxTransfersPerYear: Int, val createdAt: Long, val createdBy: String)
data class SeatInfo(val license: String, val seatId: String, val subject: Subject, val factors: Map<FactorKind, String>, val k: Int, val firstIssuedAt: Long, val lastIssuedAt: Long,
                    /** Kids of the keys (tools) that issued this seat's activations. */ val issuedBy: Set<String>)
data class TransferInfo(val license: String, val seat: String, val at: Long, val by: String)

sealed class Plan {
    /** The same hardware already holds this seat: re-issue the SAME seat (no seat consumed). */
    data class Reuse(val seat: SeatInfo) : Plan()
    data class NewSeat(val seatId: String, val seatsLeftAfter: Int) : Plan()
    data class Refused(val reason: Rejection, val message: String) : Plan()
}

object LicenseBook {
    const val DEFAULT_TRANSFERS_PER_YEAR = 2
    private const val YEAR_MS = 365L * 24 * 3600 * 1000

    private fun fpOf(f: Map<FactorKind, String>) = Fingerprints(f)

    /** Replays [events] (any order, duplicates allowed) against the key ring: deterministic. */
    fun replay(events: Collection<LicenseEvent>, ring: KeyRing): LicenseState {
        val licenses = LinkedHashMap<String, LicenseInfo>()
        val seats = LinkedHashMap<String, SeatInfo>()
        val alias = HashMap<String, String>()                     // "license|seat" of a merged duplicate -> the kept one
        val transfers = ArrayList<TransferInfo>()
        var rev = RevocationState()
        val duplicates = ArrayList<Pair<String, String>>(); val warnings = ArrayList<String>(); val rejected = ArrayList<Pair<String, Rejection>>()
        val seen = HashSet<String>()
        for (e in events.sortedWith(compareBy({ it.at }, { it.id }))) {
            if (!seen.add(e.id)) continue
            val key = ring.find(e.keyId)
            val scope = when (e.type) { "license", "issue" -> if (e.fields["kind"] == "trial") KeyScope.ISSUE_TRIAL else KeyScope.ISSUE_PRODUCTION; "transfer" -> KeyScope.TRANSFER; "revoke" -> KeyScope.REVOKE; else -> null }
            when {
                key == null -> { rejected += e.id to Rejection.UNKNOWN_KEY; continue }
                ring.isRevoked(e.keyId) -> { rejected += e.id to Rejection.REVOKED_KEY; continue }
                !key.verify(e.text, e.signature) -> { rejected += e.id to Rejection.BAD_SIGNATURE; continue }
                scope == null -> { rejected += e.id to Rejection.MALFORMED; continue }
                !key.allows(scope) -> { rejected += e.id to Rejection.KEY_NOT_ALLOWED; continue }
            }
            val f = e.fields
            when (e.type) {
                "license" -> {
                    val id = f["license"] ?: continue
                    if (id !in licenses) licenses[id] = LicenseInfo(id, f["seats"]?.toIntOrNull() ?: 0, f["maxTransfersPerYear"]?.toIntOrNull() ?: DEFAULT_TRANSFERS_PER_YEAR, e.at, e.keyId)
                }
                "issue" -> {
                    val lic = f["license"] ?: continue; val seat = f["seat"] ?: continue
                    if (lic == Activation.TRIAL_LICENSE) continue                     // trial keys are logged by the tools, not counted as seats
                    val info = licenses[lic]
                    if (info == null) { rejected += e.id to Rejection.UNKNOWN_LICENSE; continue }
                    val subject = Subject.valueOf((f["subject"] ?: "tv").uppercase()); val k = f["k"]?.toIntOrNull() ?: 1
                    val keyOf = "$lic|${alias["$lic|$seat"] ?: seat}"
                    val existing = seats[keyOf]
                    if (existing != null) {
                        seats[keyOf] = existing.copy(lastIssuedAt = maxOf(existing.lastIssuedAt, e.at), firstIssuedAt = minOf(existing.firstIssuedAt, e.at), issuedBy = existing.issuedBy + e.keyId)
                        continue
                    }
                    val same = seats.values.firstOrNull { it.license == lic && it.subject == subject && DeviceIdentity.matches(it.factors, it.k, fpOf(e.factors)) }
                    if (same != null) {                                               // same hardware, other seat id: two tools issued it
                        duplicates += "$lic|${same.seatId}" to "$lic|$seat"; alias["$lic|$seat"] = same.seatId
                        seats["$lic|${same.seatId}"] = same.copy(lastIssuedAt = maxOf(same.lastIssuedAt, e.at), issuedBy = same.issuedBy + e.keyId)
                        continue
                    }
                    if (seats.values.count { it.license == lic } >= info.seats) warnings += "licence $lic : plus de postes que prévu (${info.seats}) : poste $seat"
                    seats[keyOf] = SeatInfo(lic, seat, subject, e.factors, k, e.at, e.at, setOf(e.keyId))
                }
                "transfer" -> {
                    val lic = f["license"] ?: continue; val seat = alias["$lic|${f["seat"]}"] ?: f["seat"] ?: continue
                    val info = licenses[lic]; val cur = seats["$lic|$seat"]
                    if (info == null || cur == null) { rejected += e.id to Rejection.UNKNOWN_LICENSE; continue }
                    if (transfers.count { it.license == lic && it.at > e.at - YEAR_MS && it.at <= e.at } >= info.maxTransfersPerYear) { rejected += e.id to Rejection.TRANSFER_LIMIT; continue }
                    seats["$lic|$seat"] = cur.copy(factors = e.factors, k = f["k"]?.toIntOrNull() ?: cur.k, issuedBy = cur.issuedBy + e.keyId)
                    transfers += TransferInfo(lic, seat, e.at, e.keyId)
                    rev = rev.merge(RevocationState(emptySet(), mapOf("$lic|$seat" to e.at)))
                }
                "revoke" -> when (f["target"]) {
                    "key" -> rev = rev.merge(RevocationState(setOf(f["value"] ?: ""), emptyMap()))
                    "seat" -> rev = rev.merge(RevocationState(emptySet(), mapOf((f["value"] ?: "") to e.at)))
                }
            }
        }
        return LicenseState(licenses, seats, transfers, rev, duplicates, warnings, rejected)
    }

    /**
     * What to do for a device asking for an activation of [license]: re-issue the seat it already holds (same hardware, at least k of n factors: no seat consumed, whichever tool
     * answers), or take a new seat if one is left. The default seat id of a new seat is deterministic ([SeatIds.of]).
     */
    fun plan(state: LicenseState, license: String, subject: Subject, factors: Fingerprints): Plan {
        val info = state.licenses[license] ?: return Plan.Refused(Rejection.UNKNOWN_LICENSE, "Licence inconnue : $license")
        state.seats.values.firstOrNull { it.license == license && it.subject == subject && DeviceIdentity.matches(it.factors, it.k, factors) }?.let { return Plan.Reuse(it) }
        val left = info.seats - state.usedSeats(license)
        if (left <= 0) return Plan.Refused(Rejection.NO_SEAT_LEFT, "Plus de poste disponible sur la licence $license (${info.seats} postes) : transfert nécessaire")
        return Plan.NewSeat(SeatIds.of(license, factors), left - 1)
    }

    /** Can [seat] of [license] move at [at]? The cap is counted over the trailing year (default 2). */
    fun transferAllowed(state: LicenseState, license: String, at: Long): Boolean {
        val info = state.licenses[license] ?: return false
        return state.transfers.count { it.license == license && it.at > at - YEAR_MS } < info.maxTransfersPerYear
    }

    // ---- registry file ----
    fun export(events: Collection<LicenseEvent>): String = JsonLite.write(linkedMapOf("format" to "castbridge-licence-registry-v1",
        "events" to events.sortedWith(compareBy({ it.at }, { it.id })).distinctBy { it.id }.map { it.toMap() }))

    /** The events of a registry file; malformed entries (id that does not match the text, missing field) are skipped, never trusted. Signatures are checked at [replay]. */
    fun import(json: String): List<LicenseEvent> {
        val m = JsonLite.obj(json)
        if (m["format"] != "castbridge-licence-registry-v1") throw JsonLite.ParseError("not a licence registry")
        return (m["events"] as? List<*>).orEmpty().mapNotNull { @Suppress("UNCHECKED_CAST") LicenseEvent.fromMap(it as? Map<String, Any?> ?: return@mapNotNull null) }
    }

    /** Union without duplicates (same event id once). Order-independent. */
    fun merge(a: Collection<LicenseEvent>, b: Collection<LicenseEvent>): List<LicenseEvent> = (a + b).distinctBy { it.id }.sortedWith(compareBy({ it.at }, { it.id }))
}

/**
 * A signed list of what devices must forget (`cbr1.…`): revoked keys and revoked seats with the date. The TV (or phone) applies it at its next contact (Bluetooth, USB file, or the server
 * on a connection). Signed by a key with REVOKE.
 */
object RevocationNotice {
    const val FORMAT = "castbridge-revocation-v1"
    const val PREFIX = "cbr1"

    fun issue(s: Signer, at: Long, state: RevocationState): String {
        val text = (listOf(FORMAT, "kid=${s.keyId}", "issuedAt=$at") + state.keys.sorted().map { "key=$it" } + state.seats.toSortedMap().map { "seat=${it.key}|${it.value}" }).joinToString("\n")
        return "$PREFIX." + Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8)) + "." + Base64.getEncoder().encodeToString(s.sign(text.toByteArray(Charsets.UTF_8)))
    }

    /** The revocations of a verified notice, or null (unknown or revoked key, no REVOKE scope, bad signature, non canonical text). */
    fun verify(token: String, ring: KeyRing): RevocationState? = runCatching {
        val p = token.trim().split('.'); require(p.size == 3 && p[0] == PREFIX)
        val text = String(Base64.getUrlDecoder().decode(p[1]), Charsets.UTF_8)
        val lines = text.split('\n'); require(lines[0] == FORMAT)
        val kid = lines[1].removePrefix("kid="); require(lines[1].startsWith("kid=") && lines[2].startsWith("issuedAt="))
        val key = ring.find(kid) ?: return null
        if (ring.isRevoked(kid) || !key.allows(KeyScope.REVOKE) || !key.verify(text, p[2])) return null
        val keys = lines.drop(3).filter { it.startsWith("key=") }.map { it.removePrefix("key=") }.toSet()
        val seats = lines.drop(3).filter { it.startsWith("seat=") }.associate { l -> l.removePrefix("seat=").split('|').let { "${it[0]}|${it[1]}" to it[2].toLong() } }
        val rebuilt = (listOf(FORMAT, "kid=$kid", lines[2]) + keys.sorted().map { "key=$it" } + seats.toSortedMap().map { "seat=${it.key}|${it.value}" }).joinToString("\n")
        require(rebuilt == text)
        RevocationState(keys, seats)
    }.getOrNull()
}
