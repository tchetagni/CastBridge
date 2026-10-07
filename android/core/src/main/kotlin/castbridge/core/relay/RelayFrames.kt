package castbridge.core.relay

import castbridge.core.owner.OwnerFrames

/**
 * Trames du relais sur le canal propriétaire Bluetooth (service `…0005`, types libres 11-15 : [OwnerFrames.RELAY_ASK_PIPE], [OwnerFrames.RELAY_STATE] ; 13-15 sont
 * réservés au coursier, relay-R2). Charge utile = lignes `clé=valeur` en ASCII minuscule : jamais un nom, un code, une clé ni une adresse ; additive (clés et
 * valeurs inconnues ignorées, version `v` pour plus tard).
 *
 * Sens : le téléphone synchronisé se présente à la TV (il se réveille quand la TV frappe à sa porte, ou à l'ouverture de CastBridge) et lui dit son état de relais
 * ([OwnerFrames.RELAY_STATE]) ; la TV répond [OwnerFrames.RELAY_ASK_PIPE] quand elle veut un tuyau, ou un simple « ok ». Le téléphone en conclut seul s'il ouvre le tuyau.
 */
object RelayFrames {
    const val VERSION = 1
    const val DEFAULT_TTL_SEC = 120
    const val MIN_TTL_SEC = 10
    const val MAX_TTL_SEC = 600

    enum class Phase(val wire: String) {
        IDLE("idle"), OPENING("opening"), OPEN("open"), REFUSED("refused");
        companion object { fun of(w: String?): Phase? = values().firstOrNull { it.wire == w } }
    }

    /** « La TV veut un tuyau » : pourquoi ([needs], le téléphone refuse un « bulk » sur données mobiles) et pour combien de temps elle le veut ([ttlSec]). */
    data class Ask(val needs: List<PipeNeed>, val ttlSec: Int)

    /** L'état de relais du téléphone : [reason] = pourquoi il ne relaie pas, [metered] = son réseau est facturé, [leftKb] = ce qui reste du plafond du jour. */
    data class State(val phase: Phase, val reason: RelayReason? = null, val metered: Boolean? = null, val leftKb: Long? = null)

    fun encodeAsk(a: Ask): ByteArray = ("v=$VERSION\nneed=${a.needs.joinToString(",") { it.wire }}\nttl=${a.ttlSec.coerceIn(MIN_TTL_SEC, MAX_TTL_SEC)}\n").toByteArray(Charsets.US_ASCII)

    fun decodeAsk(p: ByteArray): Ask? {
        val kv = parse(p) ?: return null
        val needs = kv["need"].orEmpty().split(',').mapNotNull { PipeNeed.of(it.trim()) }.distinct().ifEmpty { listOf(PipeNeed.PLAY) }
        val ttl = kv["ttl"]?.toIntOrNull()?.coerceIn(MIN_TTL_SEC, MAX_TTL_SEC) ?: DEFAULT_TTL_SEC
        return Ask(needs, ttl)
    }

    fun encodeState(s: State): ByteArray = buildString {
        append("v=").append(VERSION).append('\n')
        append("state=").append(s.phase.wire).append('\n')
        s.reason?.let { append("why=").append(it.wire).append('\n') }
        s.metered?.let { append("metered=").append(if (it) 1 else 0).append('\n') }
        s.leftKb?.let { append("left=").append(it.coerceAtLeast(0)).append('\n') }
    }.toByteArray(Charsets.US_ASCII)

    fun decodeState(p: ByteArray): State? {
        val kv = parse(p) ?: return null
        val phase = Phase.of(kv["state"]) ?: return null
        return State(phase, RelayReason.of(kv["why"]), when (kv["metered"]) { "1" -> true; "0" -> false; else -> null }, kv["left"]?.toLongOrNull()?.takeIf { it >= 0 })
    }

    /** Les lignes `clé=valeur` d'une trame, ou null si ce n'est pas une trame du relais (trop grosse, sans version). */
    private fun parse(p: ByteArray): Map<String, String>? {
        if (p.isEmpty() || p.size > OwnerFrames.MAX_PAYLOAD) return null
        val kv = String(p, Charsets.UTF_8).lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it).trim() to l.substring(it + 1).trim() } }.toMap()
        return kv.takeIf { it["v"]?.toIntOrNull() != null }
    }
}

/** La TV côté canal propriétaire : ce qu'elle fait d'un téléphone qui se présente. Android la branche sur le courtier ([PipeBroker]) et le registre des téléphones de confiance. */
interface RelayChannelHost {
    /** Téléphone synchronisé ET toujours appairé ; [peer] est l'adresse Bluetooth prouvée par la liaison appairée (jamais une déclaration du pair). */
    fun isTrusted(peer: String?): Boolean
    /** Un téléphone de confiance dit son état de relais. Rend la demande de tuyau de la TV, ou null si elle n'en veut pas. */
    fun onPhoneState(peer: String?, state: RelayFrames.State): RelayFrames.Ask?
}

/** Ce que la TV a répondu au téléphone. */
sealed class RelayAnswer {
    data class Wanted(val ask: RelayFrames.Ask) : RelayAnswer()
    object NotWanted : RelayAnswer() { override fun toString() = "NotWanted" }
    /** TV ancienne (« Non pris en charge ») ou pair non reconnu : le téléphone ne fait rien de plus (comportement actuel, manuel). */
    object Unsupported : RelayAnswer() { override fun toString() = "Unsupported" }
    object LinkLost : RelayAnswer() { override fun toString() = "LinkLost" }
}
