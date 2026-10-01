package castbridge.core.policy

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.RevocationState

data class PolicyMessage(val text: String, val level: String, val untilMs: Long)

/**
 * The CURRENT policy set of a TV (docs/ORDRES.md § État): what the applied orders say, nothing else. Immutable; [version] counts the applied orders (shown in "À propos > Politiques
 * appliquées"). Nothing in it can delete anything: a suspended licence only makes the app fall back to the locked state (activation only), data stays on disk.
 */
data class PolicyState(
    val version: Long = 0,
    val licenses: Map<String, LicenseMode> = emptyMap(),
    val extensions: Map<String, Long> = emptyMap(),
    val revocations: RevocationState = RevocationState(),
    val refreshRequestedAtVersion: Long = 0,
    val flags: Map<String, Boolean> = emptyMap(),
    val minVersion: Int = 0,
    val channel: String = "stable",
    val available: Set<String> = emptySet(),
    val retired: Set<String> = emptySet(),
    val budgets: Map<String, Long> = emptyMap(),
    val messages: Map<String, PolicyMessage> = emptyMap(),
) {
    fun mode(license: String): LicenseMode = licenses[license] ?: LicenseMode.ACTIVE
    fun flag(name: String, default: Boolean = false) = flags[name] ?: default
    /** Messages still to show at [nowMs] (a message with `until` 0 stays until cleared). */
    fun activeMessages(nowMs: Long): Map<String, PolicyMessage> = messages.filterValues { it.untilMs == 0L || nowMs <= it.untilMs }

    /** The state after [c] (absolute statements: applying the same change twice leaves the same state, only [version] counts applications). */
    fun apply(c: PolicyActions.Change): PolicyState = when (c) {
        is PolicyActions.Change.LicenseMode -> copy(licenses = if (c.mode == LicenseMode.ACTIVE) licenses - c.license else licenses + (c.license to c.mode))
        is PolicyActions.Change.Extend -> copy(extensions = extensions + (c.license to c.untilMs))
        is PolicyActions.Change.RevokeKey -> copy(revocations = revocations.merge(RevocationState(keys = setOf(c.kid))))
        is PolicyActions.Change.RevokeSeat -> copy(revocations = revocations.merge(RevocationState(seats = mapOf("${c.license}|${c.seat}" to c.atMs))))
        is PolicyActions.Change.Refresh -> copy(refreshRequestedAtVersion = version + 1)
        is PolicyActions.Change.Flag -> copy(flags = flags + (c.name to c.on))
        is PolicyActions.Change.MinVersion -> copy(minVersion = c.versionCode)
        is PolicyActions.Change.Channel -> copy(channel = c.name)
        is PolicyActions.Change.Available -> copy(available = available + c.lots, retired = retired - c.lots)
        is PolicyActions.Change.Retire -> copy(retired = retired + c.lots, available = available - c.lots)
        is PolicyActions.Change.Budget -> copy(budgets = budgets + (c.name to c.value))
        is PolicyActions.Change.Message -> copy(messages = messages + (c.id to PolicyMessage(c.text, c.level, c.untilMs)))
        is PolicyActions.Change.ClearMessage -> copy(messages = messages - c.id)
    }

    /** Same content, [version] raised by one (done once per accepted order, by the engine). */
    fun bumped() = copy(version = version + 1)

    fun toJson(): Map<String, Any?> = mapOf(
        "version" to version, "licenses" to licenses.mapValues { it.value.name }, "extensions" to extensions,
        "revKeys" to revocations.keys.sorted(), "revSeats" to revocations.seats, "refreshAt" to refreshRequestedAtVersion,
        "flags" to flags, "minVersion" to minVersion, "channel" to channel, "available" to available.sorted(), "retired" to retired.sorted(), "budgets" to budgets,
        "messages" to messages.mapValues { mapOf("text" to it.value.text, "level" to it.value.level, "until" to it.value.untilMs) },
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(m: Map<String, Any?>): PolicyState = PolicyState(
            version = m.long("version") ?: 0,
            licenses = (m["licenses"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> runCatching { k to LicenseMode.valueOf(v as String) }.getOrNull() }.toMap(),
            extensions = (m["extensions"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toLong() } }.toMap(),
            revocations = RevocationState((m["revKeys"] as? List<Any?>).orEmpty().filterIsInstance<String>().toSet(), (m["revSeats"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toLong() } }.toMap()),
            refreshRequestedAtVersion = m.long("refreshAt") ?: 0,
            flags = (m["flags"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }.toMap(),
            minVersion = (m.long("minVersion") ?: 0).toInt(), channel = m.str("channel") ?: "stable",
            available = (m["available"] as? List<Any?>).orEmpty().filterIsInstance<String>().toSet(), retired = (m["retired"] as? List<Any?>).orEmpty().filterIsInstance<String>().toSet(),
            budgets = (m["budgets"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toLong() } }.toMap(),
            messages = (m["messages"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Map<String, Any?>)?.let { k to PolicyMessage(it.str("text") ?: "", it.str("level") ?: "info", it.long("until") ?: 0) } }.toMap(),
        )
        fun parse(text: String): PolicyState = fromJson(JsonLite.obj(text))
    }
}

/** What the TV reports back for one order: technical only (device code, key, sequence, result), never personal data. */
data class OrderAck(val kid: String, val seq: Long, val nonce: String, val reason: AckReason, val policyVersion: Long, val atMs: Long) {
    val applied get() = reason.applied
    /** Wire text (one line per field) shared by the Bluetooth ack frame and the server `POST /api/v1/orders/acks`. */
    fun toText(): String = "kid=$kid\nseq=$seq\nnonce=$nonce\nresult=${if (applied) "applied" else "refused"}\nreason=${reason.name}\npolicyVersion=$policyVersion\nat=$atMs"

    companion object {
        fun parse(text: String): OrderAck? = runCatching {
            val f = text.split('\n').associate { it.substringBefore('=') to it.substringAfter('=') }
            OrderAck(f.getValue("kid"), f.getValue("seq").toLong(), f.getValue("nonce"), AckReason.valueOf(f.getValue("reason")), f.getValue("policyVersion").toLong(), f.getValue("at").toLong())
                .also { require(it.kid.matches(Regex("^[0-9a-f]{16}$")) && it.seq >= 0 && it.nonce.matches(Regex("^[0-9a-f]{8,64}$"))) }
        }.getOrNull()
    }
}

/** One line of the local, READ-ONLY journal (À propos > Politiques appliquées). No parameter values of personal nature exist; the action id and the result are enough. */
data class JournalEntry(val atMs: Long, val kid: String, val seq: Long, val action: String, val reason: AckReason, val policyVersion: Long, val clockRolledBack: Boolean = false) {
    fun line(): String = "${if (reason.applied) "appliqué" else "refusé (${reason.name})"} · $action · n°$seq" + if (clockRolledBack) " · horloge en retrait détectée" else ""
}
