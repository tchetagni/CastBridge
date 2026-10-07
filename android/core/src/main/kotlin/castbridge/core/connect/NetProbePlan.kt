package castbridge.core.connect

/**
 * Quand la TV sonde Internet (relay-R1, consigne du coordinateur après l'hygiène R4). Avant : une sonde vers `connectivitycheck.gstatic.com` à CHAQUE passage de la boucle
 * réseau (60 s tant qu'Internet marche, 10 à 30 s sinon), y compris À TRAVERS le téléphone : environ 1 Mo par jour de données mobiles et un réveil radio chaque minute,
 * contraire à la discrétion du relais et à REL-F7. Maintenant :
 *
 * - **jambe directe de la TV** (réseau propre, cible inchangée) : une sonde toutes les 5 minutes, ou tout de suite sur un changement d'état ([markChanged] : réseau apparu ou
 *   perdu, lien changé) ou un test manuel ;
 * - **jambe par le téléphone** : AUCUNE sonde périodique. La vie du tuyau se déduit des trames PING/ACK de la passerelle Bluetooth ; une seule vérification de bout en bout vers le
 *   serveur du projet (jamais un tiers) à chaque nouveau tuyau (changement d'état), quand un appel réel a échoué à travers lui ([relayFailureSeen]), sur test manuel, et, si la
 *   dernière vérification a échoué, de nouveau au plus toutes les 30 s tant qu'une opération attend Internet (demande explicite) et sinon au plus toutes les 5 minutes.
 *
 * Pur : l'heure vient de l'appelant. Aucune sonde par le téléphone sans téléphone ; [relayDue] `allowed` = les portes automatiques sont ouvertes (écran d'information répondu).
 */
class NetProbePlan(
    private val directEveryMs: Long = 5 * 60_000L,
    private val relayRetryMs: Long = 5 * 60_000L,
    private val relayDemandRetryMs: Long = 30_000L,
) {
    private var lastDirectAt = NEVER
    private var changed = false
    private var probedAttach = 0L
    private var lastRelayAt = NEVER
    private var lastRelayOk = false
    private var failureSeen = false

    /** Un changement d'état de la TV elle-même (réseau apparu ou perdu, lien changé) : la prochaine sonde directe est due tout de suite. */
    @Synchronized fun markChanged() { changed = true }

    /** Un appel réel à travers le tuyau a échoué (hors réponse du serveur) : une vérification est due. */
    @Synchronized fun relayFailureSeen() { failureSeen = true }

    @Synchronized fun directDue(now: Long, manual: Boolean): Boolean = manual || changed || lastDirectAt == NEVER || now - lastDirectAt >= directEveryMs

    @Synchronized fun directDone(now: Long) { lastDirectAt = now; changed = false }

    /**
     * La vérification vers le serveur par le tuyau est-elle due ? [attachId] identifie le tuyau en cours (une valeur qui change à chaque nouvelle liaison du téléphone) ;
     * [demandLive] : une opération attend Internet en ce moment.
     */
    @Synchronized fun relayDue(now: Long, allowed: Boolean, connected: Boolean, attachId: Long, manual: Boolean, demandLive: Boolean): Boolean {
        if (!connected) return false
        if (manual) return true
        if (!allowed) return false
        if (attachId != probedAttach) return true
        if (failureSeen) return true
        if (!lastRelayOk) return now - lastRelayAt >= (if (demandLive) relayDemandRetryMs else relayRetryMs)
        return false
    }

    @Synchronized fun relayDone(now: Long, attachId: Long, ok: Boolean) {
        probedAttach = attachId; lastRelayAt = now; lastRelayOk = ok; failureSeen = false
    }

    private companion object { const val NEVER = Long.MIN_VALUE / 2 }
}
