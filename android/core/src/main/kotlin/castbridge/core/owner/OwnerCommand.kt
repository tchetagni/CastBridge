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
    val keyId: String, val power: Power, val action: String, val challenge: String, val k: Int, val factors: Map<FactorKind, String>,
    val bundleIds: List<String>, val lots: List<LotId>, val days: Int, val signature: String,
) {
    fun canonicalPayload(): String = payload(keyId, power, action, challenge, k, factors, bundleIds, lots, days)

    fun encode(): String = "cbo1." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(canonicalPayload().toByteArray(Charsets.UTF_8)) + "." + signature

    companion object {
        const val FORMAT = "castbridge-owner-command-v1"
        private val HEX = Regex("^[0-9a-f]{16,64}$")
        private val ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val FP = Regex("^[0-9a-f]{32}$")
        val SUPPORT_ACTIONS = setOf("diagnostic", "reset-trial")

        fun payload(keyId: String, power: Power, action: String, challenge: String, k: Int, factors: Map<FactorKind, String>,
                    bundleIds: List<String>, lots: List<LotId>, days: Int): String = buildList {
            add(FORMAT); add("keyId=$keyId"); add("power=${power.name.lowercase()}"); add("action=$action"); add("challenge=$challenge"); add("k=$k")
            factors.toSortedMap().forEach { (f, fp) -> add("factor=${f.name}|$fp") }
            add("bundles=${bundleIds.sorted().joinToString(",")}")
            add("lots=${lots.map(LotNames::key).sorted().joinToString(",")}")
            add("days=$days")
        }.joinToString("\n")

        fun decode(token: String): OwnerCommand? = runCatching {
            val parts = token.trim().split('.')
            require(parts.size == 3 && parts[0] == "cbo1")
            val text = String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            val lines = text.split('\n')
            require(lines[0] == FORMAT)
            fun field(i: Int, key: String) = lines[i].also { require(it.startsWith("$key=")) }.substringAfter('=')
            val keyId = field(1, "keyId").also { require(ID.matches(it)) }
            val power = Power.valueOf(field(2, "power").uppercase())
            val action = field(3, "action").also { require(it.isEmpty() || ID.matches(it)) }
            val challenge = field(4, "challenge").also { require(HEX.matches(it)) }
            val k = field(5, "k").toInt()
            val rest = lines.drop(6)
            val factors = LinkedHashMap<FactorKind, String>()
            val factorLines = rest.takeWhile { it.startsWith("factor=") }
            factorLines.forEach { l -> l.removePrefix("factor=").split('|').let { require(it.size == 2 && FP.matches(it[1])); factors[FactorKind.valueOf(it[0])] = it[1] } }
            val tail = rest.drop(factorLines.size)
            require(tail.size == 3)
            fun list(l: String, key: String) = l.also { require(it.startsWith("$key=")) }.substringAfter('=').split(',').filter { it.isNotEmpty() }
            val bundles = list(tail[0], "bundles").onEach { require(ID.matches(it)) }
            val lots = list(tail[1], "lots").map { LotNames.parseKey(it) ?: error("lot") }
            val days = tail[2].also { require(it.startsWith("days=")) }.substringAfter('=').toInt()
            val c = OwnerCommand(keyId, power, action, challenge, k, factors, bundles, lots, days, parts[2])
            require(c.canonicalPayload() == text)
            c
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
}

class OwnerCommandVerifier(private val keys: KeyRing, private val challenges: ChallengeBook) {
    fun verify(token: String, device: Fingerprints, clock: TvClock, wallClockMs: Long): CommandResult {
        val c = OwnerCommand.decode(token) ?: return no(Rejection.MALFORMED, "Commande illisible")
        val key = keys.find(c.keyId) ?: return no(Rejection.UNKNOWN_KEY, "Clé inconnue de cette TV")
        if (keys.isRevoked(c.keyId)) return no(Rejection.REVOKED_KEY, "Clé révoquée")
        if (!key.verify(c.canonicalPayload(), c.signature)) return no(Rejection.BAD_SIGNATURE, "Signature invalide")
        if (c.power.rank > key.maxPower.rank) return no(Rejection.KEY_NOT_ALLOWED, "Cette clé n'a pas ce pouvoir")
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
