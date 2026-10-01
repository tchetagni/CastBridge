package castbridge.core.owner

import castbridge.core.lots.LotId
import castbridge.core.lots.LotNames
import java.security.SecureRandom

/**
 * A command of the owner console to ONE TV (docs/TRIAL-EDITION.md § Console propriétaire). It is accepted only when it is signed (Ed25519,
 * a key of the [KeyRing], not revoked, allowed this [Power]), bound to the target TV (its signed factor set matches, k of n), carries the CHALLENGE
 * that this very TV issued a moment ago (single use: no replay, no wall clock needed on a TV whose clock lies) and its duration is clamped to the power's
 * maximum (30 days for « tout ouvert »). There is no secret and no hidden path in the TV: only public keys.
 */
data class OwnerCommand(
    val keyId: String, val seq: Long, val issuedAt: Long, val power: Power, val action: String, val challenge: String, val k: Int, val factors: Map<FactorKind, String>,
    val bundleIds: List<String>, val lots: List<LotId>, val days: Int, val signature: String,
) {
    /** The challenge IS the envelope nonce. `notBefore` = `issuedAt`; `expiresAt` = one day later (informative: a command is protected by its challenge, not by the wall clock). */
    fun toEnvelope(): Envelope = Envelope(TYPE, keyId, seq, challenge, issuedAt, issuedAt, issuedAt + 24L * 3600 * 1000, Envelope.Target.Device(k, factors), body(power, action, bundleIds, lots, days), signature)
    fun canonicalPayload(): String = toEnvelope().canonicalPayload()

    fun encode(): String = toEnvelope().encode()

    companion object {
        const val TYPE = "command"
        private val ID = Envelope.ID
        val SUPPORT_ACTIONS = setOf("diagnostic", "reset-trial")

        fun body(power: Power, action: String, bundleIds: List<String>, lots: List<LotId>, days: Int): List<String> =
            listOf("power=${power.name.lowercase()}", "action=$action", "bundles=${bundleIds.sorted().joinToString(",")}", "lots=${lots.map(LotNames::key).sorted().joinToString(",")}", "days=$days")

        /** The canonical text to sign. */
        fun payload(keyId: String, seq: Long, issuedAt: Long, power: Power, action: String, challenge: String, k: Int, factors: Map<FactorKind, String>,
                    bundleIds: List<String>, lots: List<LotId>, days: Int): String =
            Envelope.payload(TYPE, keyId, seq, challenge, issuedAt, issuedAt, issuedAt + 24L * 3600 * 1000, Envelope.Target.Device(k, factors), body(power, action, bundleIds, lots, days))

        fun decode(token: String): OwnerCommand? = Envelope.decode(token)?.let(::from)

        fun from(e: Envelope): OwnerCommand? = runCatching {
            require(e.type == TYPE && e.notBefore == e.issuedAt && e.expiresAt == e.issuedAt + 24L * 3600 * 1000)
            val t = e.target as Envelope.Target.Device
            require(e.body.size == 5)
            fun field(i: Int, key: String) = e.body[i].also { require(it.startsWith("$key=")) }.substringAfter('=')
            fun list(v: String) = v.split(',').filter { it.isNotEmpty() }
            val action = field(1, "action").also { require(it.isEmpty() || ID.matches(it)) }
            val bundles = list(field(2, "bundles")).onEach { require(ID.matches(it)) }
            val lots = list(field(3, "lots")).map { LotNames.parseKey(it) ?: error("lot") }
            OwnerCommand(e.keyId, e.seq, e.issuedAt, Power.valueOf(field(0, "power").uppercase()), action, e.nonce, t.k, t.factors, bundles, lots, field(4, "days").toInt(), e.signature)
                .also { require(it.canonicalPayload() == e.canonicalPayload()) }
        }.getOrNull()
    }
}

/** What a verified command grants, with its end computed on the TV's own time ([TvClock.now]) at the moment it was accepted. */
data class OwnerGrant(val power: Power, val bundleIds: List<String>, val lots: List<LotId>, val untilMs: Long, val clamped: Boolean, val action: String)

sealed class CommandResult {
    data class Accepted(val grant: OwnerGrant) : CommandResult()
    data class Rejected(val reason: Rejection, val message: String) : CommandResult()
}

/**
 * Challenges issued by the TV on the owner Bluetooth channel: random, single use, short-lived on a MONOTONIC clock (uptime), so a wrong wall clock cannot
 * extend one. [spent] persists the last consumed challenges so that a restart does not reopen a replay; at most [MAX_OPEN] are open at once.
 */
class ChallengeBook(private val uptimeMs: () -> Long, private val random: SecureRandom = SecureRandom(), private val ttlMs: Long = 120_000L,
                    private val spent: MutableSet<String> = LinkedHashSet()) {
    companion object { const val MAX_OPEN = 8; const val MAX_SPENT = 512 }
    private val open = LinkedHashMap<String, Long>()

    fun issue(): String {
        val now = uptimeMs()
        open.entries.removeAll { now - it.value > ttlMs }
        while (open.size >= MAX_OPEN) open.remove(open.keys.first())
        val c = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        open[c] = now
        return c
    }

    /** Consumes [challenge]: true once, only if this TV issued it, it is not expired and not used before. */
    fun consume(challenge: String): Boolean {
        if (challenge in spent) return false
        val at = open.remove(challenge) ?: return false
        if (uptimeMs() - at > ttlMs) return false
        spent += challenge
        while (spent.size > MAX_SPENT) spent.remove(spent.first())
        return true
    }

    fun spentSnapshot(): List<String> = spent.toList()

    /** Test and vector support: marks [challenges] as issued by this TV just now (a real TV only ever issues its own). */
    fun restore(challenges: List<String>) { val now = uptimeMs(); challenges.forEach { open[it] = now } }
}

class OwnerCommandVerifier(private val keys: KeyRing, private val challenges: ChallengeBook) {
    fun verify(token: String, device: Fingerprints, clock: TvClock, wallClockMs: Long): CommandResult {
        val env = Envelope.decode(token) ?: return no(Rejection.MALFORMED, "Commande illisible")
        if (env.type != OwnerCommand.TYPE) return no(Rejection.UNKNOWN_TYPE, "Ce message n'est pas une commande")
        val c = OwnerCommand.from(env) ?: return no(Rejection.MALFORMED, "Commande illisible")
        val key = keys.find(c.keyId) ?: return no(Rejection.UNKNOWN_KEY, "Clé inconnue de cette TV")
        if (keys.isRevoked(c.keyId)) return no(Rejection.REVOKED_KEY, "Clé révoquée")
        if (!key.verify(c.canonicalPayload(), c.signature)) return no(Rejection.BAD_SIGNATURE, "Signature invalide")
        if (!key.allows(c.power)) return no(Rejection.KEY_NOT_ALLOWED, "Cette clé n'a pas ce pouvoir")
        if (!DeviceIdentity.matches(c.factors, c.k, device)) return no(Rejection.WRONG_DEVICE, "Commande destinée à une autre TV")
        if (c.power == Power.SUPPORT && c.action !in OwnerCommand.SUPPORT_ACTIONS) return no(Rejection.BAD_COMMAND, "Action de support inconnue")
        if (c.power == Power.UNLOCK && c.bundleIds.isEmpty() && c.lots.isEmpty()) return no(Rejection.BAD_COMMAND, "Déblocage sans contenu")
        if (c.power != Power.SUPPORT && c.days < 1) return no(Rejection.BAD_COMMAND, "Durée invalide")
        if (!challenges.consume(c.challenge)) return no(Rejection.REPLAY, "Défi inconnu, périmé ou déjà utilisé")   // last: a refused command burns nothing
        val days = minOf(c.days, c.power.maxDays)
        clock.observe(wallClockMs)
        val until = clock.now(wallClockMs) + days * 24L * 3600 * 1000
        return CommandResult.Accepted(OwnerGrant(c.power, c.bundleIds, c.lots, if (c.power == Power.SUPPORT) 0L else until, days < c.days, c.action))
    }

    private fun no(r: Rejection, m: String) = CommandResult.Rejected(r, m)
}
