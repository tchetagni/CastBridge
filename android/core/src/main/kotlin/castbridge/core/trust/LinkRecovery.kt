package castbridge.core.trust

import castbridge.core.tv.BtProtocol

/**
 * R-20 : la « reprise » d'une TV déjà appairée après une réinstallation ne s'adresse qu'à ce qui peut être une CastBridge-TV.
 * Un appareil Bluetooth appairé (écouteurs, enceinte, voiture) dont les services déclarés ne contiennent pas l'UUID CastBridge
 * (7c5e3b9a-4d2f-4c61-9b0e-cb00000000xx) n'est jamais contacté, sauf s'il est déjà enregistré comme TV.
 */
object RecoveryCandidates {
    const val UUID_PREFIX = "7c5e3b9a-4d2f-4c61-9b0e-cb00000000"
    fun declaresCastBridge(uuids: Collection<String>?): Boolean? = if (uuids.isNullOrEmpty()) null else uuids.any { it.lowercase().startsWith(UUID_PREFIX) }
    /**
     * [declares] : services lus (true = CastBridge, false = services étrangers SANS l'UUID CastBridge, null = inconnus : cache SDP vide, TV appairée avant que
     * son service écoute). M3 (audit 2026-10-07) : « inconnu » est ÉLIGIBLE (une vraie TV ne doit pas être perdue après une réinstallation) ; la
     * protection contre les écouteurs sans UUID repose sur [UntrustedBackoff.onNoAnswer] (30 s, 1 min, 5 min), pas sur l'exclusion.
     */
    fun eligible(declares: Boolean?, registered: Boolean): Boolean = declares != false || registered
}

/**
 * R-20 : « téléphone non autorisé par la TV » (code 8) n'est pas réessayé toutes les 13 s : 30 s, 1 min, puis 5 min, par TV, jusqu'à ce que
 * l'utilisateur saisisse le code de la TV ([clear]). [onRefused] ne rend vrai qu'UNE fois par TV et par refus (un seul message à l'écran, avec
 * l'action « saisir le code de la TV », parcours P-62). Un autre code de refus n'est pas concerné.
 */
class UntrustedBackoff(private val now: () -> Long, private val stepsMs: LongArray = longArrayOf(30_000, 60_000, 300_000)) {
    private class S(var count: Int, var nextAt: Long)
    private val state = HashMap<String, S>()

    @Synchronized fun mayTry(address: String): Boolean = state[TrustRegistry.norm(address)]?.let { now() >= it.nextAt } ?: true
    @Synchronized fun waitMs(address: String): Long = state[TrustRegistry.norm(address)]?.let { (it.nextAt - now()).coerceAtLeast(0) } ?: 0

    /** Enregistre une réponse ; vrai = afficher le message maintenant (premier refus de cette TV depuis le dernier [clear]). */
    @Synchronized fun onRefused(address: String, code: Int): Boolean {
        if (code != BtProtocol.ERR_UNTRUSTED) return false
        val s = state.getOrPut(TrustRegistry.norm(address)) { S(0, 0) }
        val first = s.count == 0
        s.nextAt = now() + stepsMs[s.count.coerceAtMost(stepsMs.size - 1)]
        s.count++
        return first
    }
    /** M3 : un appareil aux services inconnus qui n'a pas répondu comme une TV (écouteurs…) : même rythme que le refus, mais sans message à l'écran. */
    @Synchronized fun onNoAnswer(address: String) {
        val s = state.getOrPut(TrustRegistry.norm(address)) { S(0, 0) }
        s.nextAt = now() + stepsMs[s.count.coerceAtMost(stepsMs.size - 1)]
        s.count++
    }
    /** La TV reconnaît de nouveau ce téléphone (code saisi, connecté) : on oublie les refus. */
    @Synchronized fun clear(address: String) { state.remove(TrustRegistry.norm(address)) }
}
