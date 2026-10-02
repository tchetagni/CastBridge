package castbridge.core.owner

import castbridge.core.lots.Access
import castbridge.core.lots.RentalLines
import castbridge.core.lots.RentalPolicy
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.Right
import castbridge.core.lots.Verdict
import castbridge.core.lots.SubState
import castbridge.core.lots.SubStatus
import java.util.Base64

enum class ActivationKind { TRIAL, PRODUCTION }

/** The device an activation is for: the TV or the phone (each has its own rights and is one seat of a licence). */
enum class Subject { TV, PHONE }

/**
 * A signed activation for ONE device: the envelope type `activation` (docs/ACTIVATION-FORMAT.md). The envelope header carries the key, the sequence number, the nonce, the
 * window in which it may be INSTALLED (`notBefore`, `expiresAt` = [notAfter], at most 1 year in the offline phase) and the target device (the SET of factor fingerprints, accepted when k of
 * the n match, [DeviceIdentity.matches]); the body carries the kind, the subject, the licence and seat and the rights (same [Right] types as the phone token). Once installed it stays:
 * a purchase is definitive, a subscription carries its own end date. With the activation requirement on, nothing opens until one is installed; a TRIAL activation opens the trial only.
 */
data class Activation(
    val kind: ActivationKind, val subject: Subject, val keyId: String, val seq: Long, val nonce: String, val issuedAt: Long, val notBefore: Long, val notAfter: Long,
    /** Licence the seat belongs to ("trial" for a trial key). */
    val license: String, /** Seat of the licence this device occupies; the same hardware keeps the same seat. */ val seat: String,
    val k: Int, val factors: Map<FactorKind, String>, val rights: List<Right>, val signature: String,
) {
    fun toEnvelope(): Envelope = Envelope(TYPE, keyId, seq, nonce, issuedAt, notBefore, notAfter, Envelope.Target.Device(k, factors), body(kind, subject, license, seat, rights), signature)
    fun canonicalPayload(): String = toEnvelope().canonicalPayload()

    /** "cbx1.<payload base64url, no padding>.<signature base64 with padding>": a file `activation` in Download/CastBridge, the Bluetooth payload, a QR code. */
    fun encode(): String = toEnvelope().encode()

    companion object {
        const val TYPE = "activation"
        const val FILE_NAME = "activation"
        const val TRIAL_LICENSE = "trial"
        /** What a TRIAL key may carry: its usage ceiling and its one-time window of rented lots (reserved product [RentalLines.TRIAL_PRODUCT]); never a purchase, a subscription or any other rental. */
        fun trialRight(r: Right) = r is Right.Usage || (r is Right.Rental && r.productId == RentalLines.TRIAL_PRODUCT)
        val HEX = Envelope.HEX
        val ID = Envelope.ID

        fun body(kind: ActivationKind, subject: Subject, license: String, seat: String, rights: List<Right>): List<String> =
            listOf("kind=${kind.name.lowercase()}", "subject=${subject.name.lowercase()}", "license=$license", "seat=$seat") + rights.map(::rightLine).sorted().map { "right=$it" }

        /** The canonical text to sign (the envelope payload of an activation). */
        fun payload(kind: ActivationKind, subject: Subject, keyId: String, seq: Long, nonce: String, issuedAt: Long, notBefore: Long, notAfter: Long, license: String,
                    seat: String, k: Int, factors: Map<FactorKind, String>, rights: List<Right>): String =
            Envelope.payload(TYPE, keyId, seq, nonce, issuedAt, notBefore, notAfter, Envelope.Target.Device(k, factors), body(kind, subject, license, seat, rights))

        fun rightLine(r: Right) = when (r) {
            is Right.Purchase -> "purchase|${r.productId}|${r.bundleIds.sorted().joinToString(",")}|${r.grantedAt}"
            is Right.Subscription -> "subscription|${r.productId}|${r.bundleIds.sorted().joinToString(",")}|${r.startsAt}|${r.endsAt}|${r.graceMs}|${if (r.autoRenew) 1 else 0}"
            is Right.OpenAll -> "openall|${r.productId}|${r.startsAt}|${r.endsAt}"
            is Right.Rental -> RentalLines.line(r)
            is Right.Super -> "super|${r.productId}|${r.grantedAt}"
            is Right.Usage -> "usage|${r.productId}|${r.startsAt}|${r.endsAt}"
            is Right.Unknown -> r.raw
        }

        fun parseRight(line: String): Right {
            val f = line.split('|')
            fun ids(s: String) = s.split(',').filter { it.isNotEmpty() }.onEach { require(ID.matches(it)) }
            return when (f[0]) {
                "purchase" -> { require(f.size == 4 && ID.matches(f[1])); Right.Purchase(f[1], ids(f[2]), f[3].toLong()) }
                "subscription" -> { require(f.size == 7 && ID.matches(f[1])); Right.Subscription(f[1], ids(f[2]), f[3].toLong(), f[4].toLong(), f[5].toLong(), f[6] == "1") }
                "openall" -> { require(f.size == 4 && ID.matches(f[1])); Right.OpenAll(f[1], f[2].toLong(), f[3].toLong()) }
                "super" -> { require(f.size == 3 && ID.matches(f[1])); Right.Super(f[1], f[2].toLong()) }
                "usage" -> { require(f.size == 4 && f[1] == Right.Usage.ID); Right.Usage(f[2].toLong(), f[3].toLong()) }
                RentalLines.KIND -> RentalLines.parse(f)
                else -> RentalLines.unknown(line)
            }
        }

        fun decode(token: String): Activation? = Envelope.decode(token)?.let(::from)

        /** The activation view of an envelope of type `activation`, or null (wrong type, target not a device, body not canonical). */
        fun from(e: Envelope): Activation? = runCatching {
            require(e.type == TYPE)
            val t = e.target as Envelope.Target.Device
            fun field(i: Int, key: String) = e.body[i].also { require(it.startsWith("$key=")) }.substringAfter('=')
            val seat = field(3, "seat").also { require(HEX.matches(it)) }
            val license = field(2, "license").also { require(ID.matches(it)) }
            val rights = e.body.drop(4).map { require(it.startsWith("right=")); parseRight(it.removePrefix("right=")) }
            Activation(ActivationKind.valueOf(field(0, "kind").uppercase()), Subject.valueOf(field(1, "subject").uppercase()), e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt,
                license, seat, t.k, t.factors, rights, e.signature).also { require(it.canonicalPayload() == e.canonicalPayload()) }
        }.getOrNull()
    }
}

enum class Rejection {
    MALFORMED, UNKNOWN_KEY, REVOKED_KEY, KEY_NOT_ALLOWED, BAD_SIGNATURE, WRONG_DEVICE, WRONG_SUBJECT, NOT_YET_VALID, WINDOW_CLOSED, WINDOW_TOO_LONG,
    REPLAY, CHALLENGE_EXPIRED, BAD_COMMAND, REVOKED_SEAT, TRANSFER_LIMIT, UNKNOWN_LICENSE, NO_SEAT_LEFT, BAD_RIGHTS, STALE_SEQUENCE, UNKNOWN_TYPE, WRONG_TARGET, BAD_ORDER
}

sealed class ActivationResult {
    data class Accepted(val activation: Activation, val weakIdentity: Boolean) : ActivationResult()
    /** [suspect] = signature good but the hardware does not match: the device keeps what it already has and offers the manual unlock, never a flat refusal. */
    data class Rejected(val reason: Rejection, val message: String, val suspect: Boolean = false) : ActivationResult()
}

/** What a device has been told to forget (docs/ACTIVATION-FORMAT.md § Révocation): keys and seats. Applied when the device next receives a signed list. */
class RevocationState(val keys: Set<String> = emptySet(), val seats: Map<String, Long> = emptyMap()) {
    /** An activation is revoked if its seat was revoked at or after its issue date (a later re-issue for the same seat is valid again). */
    fun seatRevoked(a: Activation): Boolean = seats["${a.license}|${a.seat}"]?.let { a.issuedAt <= it } ?: false
    fun merge(o: RevocationState) = RevocationState(keys + o.keys, (seats.keys + o.seats.keys).associateWith { maxOf(seats[it] ?: 0L, o.seats[it] ?: 0L) })
}

/**
 * Checks an activation on the device (pure, no clock of its own: the caller passes the [TvClock] time). The key must be known, not revoked, and have the scope the
 * activation needs; the licence seat must not be revoked; the sequence number must not go back for this key. [expect] is the kind of device doing the check. On acceptance the
 * sequence number is recorded in [seqState] (persist it).
 */
/**
 * Commercial policy of activation codes: a code can be INSTALLED during 48 hours from its creation, no longer, for everybody (the rights it grants have their own dates).
 * What the super administrator may add (scope SUPER_UNLIMITED) is the `super` right: reads and unlocks everything, rentals included, for good.
 */
object ActivationPolicy {
    const val CODE_VALIDITY_HOURS = 48
    const val HOUR_MS = 3_600_000L
    const val CODE_VALIDITY_MS = CODE_VALIDITY_HOURS * HOUR_MS

    /** Usage ceiling the owner sets per activation (days): a trial always has one (default 30), a production one optionally (none = no ceiling). */
    const val TRIAL_DEFAULT_DAYS = 30
    const val TRIAL_MAX_DAYS = 365
    const val PRODUCTION_MAX_DAYS = 3660
    const val USAGE_SKEW_MS = 24L * 3600 * 1000
}

class ActivationVerifier(private val keys: KeyRing, private val maxWindowMs: Long = ActivationPolicy.CODE_VALIDITY_MS, private val skewMs: Long = 24L * 3600 * 1000,
                         private val revocations: RevocationState = RevocationState(), private val expect: Subject = Subject.TV, private val seqState: SeqState = SeqState()) {
    companion object {
        const val MAX_OPEN_ALL_MS = 30L * 24 * 3600 * 1000
    }

    fun verify(token: String, device: Fingerprints, nowMs: Long): ActivationResult {
        val env = Envelope.decode(token) ?: return no(Rejection.MALFORMED, "Activation illisible")
        if (env.type != Activation.TYPE) return no(if (env.type.isEmpty()) Rejection.MALFORMED else Rejection.UNKNOWN_TYPE, "Ce message n'est pas une activation")
        val a = Activation.from(env) ?: return no(Rejection.MALFORMED, "Activation illisible")
        val key = keys.find(a.keyId) ?: return no(Rejection.UNKNOWN_KEY, "Activation signée par une clé inconnue de cet appareil")
        if (keys.isRevoked(a.keyId) || a.keyId in revocations.keys) return no(Rejection.REVOKED_KEY, "Activation signée par une clé révoquée")
        if (!key.verify(a.canonicalPayload(), a.signature)) return no(Rejection.BAD_SIGNATURE, "Signature de l'activation invalide")
        val allowed = if (a.kind == ActivationKind.TRIAL) key.allows(KeyScope.ISSUE_TRIAL) else key.allows(KeyScope.ISSUE_PRODUCTION) || key.allows(KeyScope.REACTIVATE)
        if (!allowed) return no(Rejection.KEY_NOT_ALLOWED, "Cette clé n'a pas le droit de délivrer ce type d'activation")
        if (a.rights.any { it is Right.OpenAll } && !key.allows(KeyScope.COMMAND_OPEN_ALL)) return no(Rejection.KEY_NOT_ALLOWED, "Cette clé ne peut pas délivrer « tout ouvert »")
        if (a.rights.any { it is Right.Super } && !key.allows(KeyScope.SUPER_UNLIMITED)) return no(Rejection.KEY_NOT_ALLOWED, "Cette clé n'est pas celle du super administrateur")
        if (a.kind == ActivationKind.TRIAL && a.rights.any { !Activation.trialRight(it) }) return no(Rejection.BAD_RIGHTS, "Une clé d'essai ne porte aucun droit (seulement une durée d'usage et sa fenêtre de lots)")
        if (a.rights.filterIsInstance<Right.OpenAll>().any { it.endsAt - it.startsAt > MAX_OPEN_ALL_MS || it.endsAt <= it.startsAt }) return no(Rejection.BAD_RIGHTS, "« Tout ouvert » : 30 jours au plus")
        a.rights.filterIsInstance<Right.Rental>().firstNotNullOfOrNull { RentalLines.bounds(it) }?.let { return no(Rejection.BAD_RIGHTS, "Location : $it") }
        if (a.subject != expect) return no(Rejection.WRONG_SUBJECT, "Cette activation est celle d'un autre type d'appareil")
        if (a.notAfter - a.notBefore > maxWindowMs) return no(Rejection.WINDOW_TOO_LONG, "Fenêtre d'installation trop longue (48 h au plus)")
        if (!DeviceIdentity.matches(a.factors, a.k, device))
            return ActivationResult.Rejected(Rejection.WRONG_DEVICE, "Cette activation n'est pas celle de cet appareil : déblocage manuel possible", suspect = true)
        if (revocations.seatRevoked(a)) return no(Rejection.REVOKED_SEAT, "Ce poste de la licence a été transféré ou révoqué")
        if (a.seq < seqState.last(a.keyId)) return no(Rejection.STALE_SEQUENCE, "Activation plus ancienne que celle déjà installée")
        val now = maxOf(nowMs, a.issuedAt)          // a signed message proves time has reached its issue date
        if (now + skewMs < a.notBefore) return no(Rejection.NOT_YET_VALID, "Activation pas encore valable")
        if (now > a.notAfter) return no(Rejection.WINDOW_CLOSED, "Activation périmée : à refaire")
        seqState.record(a.keyId, a.seq)
        return ActivationResult.Accepted(a, DeviceIdentity.isWeak(device))
    }

    private fun no(r: Rejection, m: String) = ActivationResult.Rejected(r, m)
}

/** What a TV may open, computed from what it holds. No key installed = nothing at all (not even the trial). */
data class TvAccess(val keyInstalled: Boolean, val access: Access, val openAllUntil: Long?, val label: String, /** SUPER_UNLIMITED installed: everything, rentals included, for good. */ val superUnlimited: Boolean = false,
                    /** Only a TRIAL key counts (no production key, no owner grant): the edition is restricted by [TrialPolicy]. */ val trial: Boolean = false) {
    val opensContent get() = keyInstalled
}

object TvGate {
    /**
     * [activations]: the verified ones installed on the TV; [grants]: owner commands still in force ([OwnerGrant]); [nowMs]: [TvClock.now].
     * Rights add up (trial floor + purchases + valid subscription + owner grants); an expired grant simply stops counting: the TV falls back
     * to its acquired rights, nothing is deleted.
     */
    fun evaluate(activations: List<Activation>, grants: List<OwnerGrant>, nowMs: Long, rentals: List<RentalStatus> = emptyList()): TvAccess {
        val live = grants.filter { it.untilMs > nowMs && it.power != Power.SUPPORT }
        // an activation whose usage ceiling has passed (or has not begun) counts for nothing: a trial then locks again, a production key must be renewed
        val counting = activations.filter { a -> a.rights.filterIsInstance<Right.Usage>().none { nowMs >= it.endsAt || nowMs < it.startsAt - ActivationPolicy.USAGE_SKEW_MS } }
        if (activations.isNotEmpty() && counting.isEmpty() && live.isEmpty()) return TvAccess(false, Access.TRIAL_ONLY.copy(message = "Activation terminée : demandez une nouvelle clé"), null, "Activation terminée")
        return evaluateCounting(counting, live, nowMs, rentals)
    }

    private fun evaluateCounting(activations: List<Activation>, live: List<OwnerGrant>, nowMs: Long, rentals: List<RentalStatus>): TvAccess {
        if (activations.isEmpty() && live.isEmpty()) return TvAccess(false, Access.TRIAL_ONLY.copy(message = "Aucune clé installée : aucun contenu ouvert"), null, "Aucune clé installée")
        val rights = activations.flatMap { it.rights }
        val purchased = rights.filterIsInstance<Right.Purchase>().flatMap { it.bundleIds }.toSortedSet()
        val subs = rights.filterIsInstance<Right.Subscription>().map { s ->
            SubStatus(s, when { nowMs < s.startsAt -> SubState.NOT_STARTED; nowMs < s.endsAt -> SubState.ACTIVE; nowMs < s.endsAt + s.graceMs -> SubState.GRACE; else -> SubState.EXPIRED })
        }
        val subscribed = subs.filter { it.state == SubState.ACTIVE || it.state == SubState.GRACE }.flatMap { it.right.bundleIds }.toSortedSet()
        val openAllRights = rights.filterIsInstance<Right.OpenAll>().filter { nowMs >= it.startsAt && nowMs < it.endsAt }.maxOfOrNull { it.endsAt }
        val openAll = listOfNotNull(live.filter { it.power == Power.OPEN_ALL }.maxOfOrNull { it.untilMs }, openAllRights).maxOrNull()
        val extraBundles = live.flatMap { it.bundleIds }.toSortedSet()
        val extraLots = live.flatMap { it.lots }.toSet()
        val superUnlimited = rights.any { it is Right.Super }                    // reads and unlocks everything, rentals included (RentalEngine makes them permanent)
        val bundles = (subscribed + extraBundles + if (openAll != null || superUnlimited) setOf("tout") else emptySet()).toSortedSet()
        val label = when {
            superUnlimited -> "Super illimité"
            Right.ALL_BUNDLE in purchased -> "Illimité"                    // a permanent account (bought "tout"): everything, but its rentals still end
            openAll != null -> "Tout ouvert (temporaire)"
            activations.any { it.kind == ActivationKind.PRODUCTION } || live.isNotEmpty() -> "Version complète"
            else -> "Version d'essai"
        }
        // rentals are evaluated by the RentalLedger (clock rules, usage ceiling); with none given (an old caller) a rental line grants NOTHING
        val access = RentalPolicy.mergeAccess(Access(Verdict.OK, purchased, bundles, subs, "", extraLots), rentals)
        val rentedLabel = if (access.rented.isNotEmpty() && access.purchased.isEmpty() && bundles.isEmpty() && openAll == null) "Location en cours" else label
        val trialOnly = activations.isNotEmpty() && activations.all { it.kind == ActivationKind.TRIAL } && live.isEmpty() && !superUnlimited
        return TvAccess(true, access, openAll, rentedLabel, superUnlimited, trialOnly)
    }
}

/**
 * Compact activation for manual typing (last resort; docs/TRIAL-EDITION.md): 18 bytes of header + the 64-byte Ed25519 signature = 82 bytes,
 * written in Crockford Base32 as groups of 5 characters (4 data + 1 check): 33 groups, 165 characters. It carries no rights list, only a [setId]
 * (0 = trial key, others = a named product set of the catalog) and is STRICTLY bound to the device code (no k-of-n: a typed key cannot carry the
 * factor set). It does not carry lot keys: encrypted lots need the full activation (Bluetooth or USB file).
 */
object CompactActivation {
    const val HEADER = 18
    const val BYTES = HEADER + 64
    private const val DOMAIN = "castbridge-activation-compact-v1\n"

    /** [notBeforeUnit] / [windowUnits] are HOURS in version 2 (current: exact 48 h) and DAYS in version 1 (old keys, still read, capped at 2 days). */
    data class Header(val kind: ActivationKind, val keyTag: Int, val notBeforeUnit: Int, val windowUnits: Int, val setId: Int, val bind: ByteArray, val version: Int = 2) {
        fun bytes(): ByteArray {
            val b = ByteArray(HEADER)
            b[0] = version.toByte(); b[1] = kind.ordinal.toByte(); b[2] = (keyTag shr 8).toByte(); b[3] = keyTag.toByte()
            b[4] = (notBeforeUnit shr 8).toByte(); b[5] = notBeforeUnit.toByte(); b[6] = (windowUnits shr 8).toByte(); b[7] = windowUnits.toByte()
            b[8] = (setId shr 8).toByte(); b[9] = setId.toByte(); System.arraycopy(bind, 0, b, 10, 8)
            return b
        }
    }

    const val EPOCH_MS = 1_767_225_600_000L        // 2026-01-01T00:00:00Z: day 0 of notBeforeDay
    const val DAY_MS = 24L * 3600 * 1000
    const val HOUR_MS = ActivationPolicy.HOUR_MS

    fun bindOf(deviceCode: String): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(("castbridge-bind|" + (DeviceCode.parse(deviceCode) ?: error("code"))).toByteArray()).copyOf(8)
    fun keyTag(keyId: String): Int = java.security.MessageDigest.getInstance("SHA-256").digest(keyId.toByteArray()).let { ((it[0].toInt() and 0xff) shl 8) or (it[1].toInt() and 0xff) }
    fun signedText(header: ByteArray): String = DOMAIN + header.joinToString("") { "%02x".format(it) }

    /** Console side: builds the typed text from a header and the signature it made over [signedText]. */
    fun encode(header: Header, signature: ByteArray): String {
        require(signature.size == 64)
        val raw = Base32C.encode(header.bytes() + signature)
        val padded = raw.padEnd((raw.length + 3) / 4 * 4, '0')
        return padded.chunked(4).mapIndexed { i, g -> g + Base32C.check(g, salt = i + 1) }.joinToString("-")
    }

    sealed class Parsed {
        data class Ok(val header: Header, val signature: ByteArray) : Parsed()
        /** [group] is 1-based: the group to retype. */
        data class BadGroup(val group: Int) : Parsed()
        object Malformed : Parsed()
    }

    fun parse(text: String): Parsed {
        val chars = text.filter { it != '-' && !it.isWhitespace() }
        if (chars.isEmpty() || chars.length % 5 != 0) return Parsed.Malformed
        val data = StringBuilder()
        for ((i, g) in chars.chunked(5).withIndex()) {
            val norm = g.map { c -> val v = Base32C.value(c); if (v < 0) return Parsed.BadGroup(i + 1) else Base32C.ALPHABET[v] }.joinToString("")
            if (Base32C.check(norm.take(4), salt = i + 1) != norm[4]) return Parsed.BadGroup(i + 1)
            data.append(norm, 0, 4)
        }
        val bytes = Base32C.decode(data.toString(), BYTES) ?: return Parsed.Malformed
        val h = bytes.copyOf(HEADER)
        if (h[0].toInt() !in 1..2 || h[1].toInt() !in ActivationKind.values().indices) return Parsed.Malformed
        return Parsed.Ok(Header(ActivationKind.values()[h[1].toInt()], ((h[2].toInt() and 0xff) shl 8) or (h[3].toInt() and 0xff),
            ((h[4].toInt() and 0xff) shl 8) or (h[5].toInt() and 0xff), ((h[6].toInt() and 0xff) shl 8) or (h[7].toInt() and 0xff),
            ((h[8].toInt() and 0xff) shl 8) or (h[9].toInt() and 0xff), h.copyOfRange(10, 18), version = h[0].toInt()), bytes.copyOfRange(HEADER, BYTES))
    }

    /** TV side: parse, find the key by tag, verify the signature, the strict device binding and the install window. */
    fun verify(text: String, keys: KeyRing, candidates: List<TrustedKey>, deviceCode: String, nowMs: Long, skewMs: Long = ActivationPolicy.HOUR_MS): ActivationResult {
        val p = parse(text)
        if (p is Parsed.BadGroup) return ActivationResult.Rejected(Rejection.MALFORMED, "Groupe ${p.group} mal saisi : le ressaisir")
        if (p !is Parsed.Ok) return ActivationResult.Rejected(Rejection.MALFORMED, "Clé illisible")
        val key = candidates.firstOrNull { keyTag(it.keyId) == p.header.keyTag } ?: return ActivationResult.Rejected(Rejection.UNKNOWN_KEY, "Clé inconnue de cette TV")
        if (keys.isRevoked(key.keyId)) return ActivationResult.Rejected(Rejection.REVOKED_KEY, "Clé révoquée")
        if (!key.verify(signedText(p.header.bytes()), Base64.getEncoder().encodeToString(p.signature))) return ActivationResult.Rejected(Rejection.BAD_SIGNATURE, "Signature invalide")
        if (!key.allows(if (p.header.kind == ActivationKind.TRIAL) KeyScope.ISSUE_TRIAL else KeyScope.ISSUE_PRODUCTION)) return ActivationResult.Rejected(Rejection.KEY_NOT_ALLOWED, "Cette clé n'a pas ce droit")
        if (!p.header.bind.contentEquals(bindOf(deviceCode))) return ActivationResult.Rejected(Rejection.WRONG_DEVICE, "Cette clé n'est pas celle de cette TV", suspect = true)
        val unit = if (p.header.version == 1) DAY_MS else HOUR_MS
        val from = EPOCH_MS + p.header.notBeforeUnit * unit
        val to = from + p.header.windowUnits * unit
        if (to - from > ActivationPolicy.CODE_VALIDITY_MS) return ActivationResult.Rejected(Rejection.WINDOW_TOO_LONG, "Fenêtre trop longue (48 h au plus)")
        if (nowMs + skewMs < from) return ActivationResult.Rejected(Rejection.NOT_YET_VALID, "Clé pas encore valable")
        if (nowMs > to) return ActivationResult.Rejected(Rejection.WINDOW_CLOSED, "Clé périmée : à refaire (une clé est valable 48 h)")
        return ActivationResult.Accepted(Activation(p.header.kind, Subject.TV, key.keyId, 0L, "", from, from, to, Activation.TRIAL_LICENSE, "", 0, emptyMap(), emptyList(), ""), weakIdentity = false)
    }
}
