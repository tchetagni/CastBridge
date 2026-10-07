package castbridge.core.connect

/**
 * La jambe DIRECTE de la vérité réseau de la TV (son réseau propre : Wi-Fi, Ethernet) : ce qu'un tour de la boucle réseau en retient (R-41, audit anti-régression 2026-10-07 b, I-2).
 *
 * Avant relay-R1 chaque tour (60 s) sondait : une coupure d'Internet de la box (Wi-Fi relié, plus rien derrière) se voyait en une soixantaine de secondes. Avec la sonde espacée de
 * 5 minutes ([NetProbePlan]), une sonde RATÉE était remplacée au tour suivant par « validé » dès que le système disait `NET_CAPABILITY_VALIDATED` (Android ne revalide que rarement) :
 * la coupure restait masquée 15 à 20 minutes, le portefeuille se disait « en ligne », chaque opération attendait son délai et AUCUN tuyau n'était demandé au téléphone. Règle :
 *
 *  - en mode sonde (conditions acceptées, réglage « sonde » ou test manuel), la SONDE fait foi : son dernier verdict, réussi OU raté, est gardé tel quel jusqu'à la sonde suivante ;
 *    l'avis du système ne remplace JAMAIS une sonde ratée ;
 *  - sans mode sonde (aucune requête vers un tiers), l'avis du système est la seule source : « validé » = joignable (0 ms), sinon inconnu ;
 *  - un appel réel au serveur qui a échoué sur le réseau propre ([callFailed]) rend la sonde due au prochain tour et annule la preuve d'un contact direct plus ancien
 *    ([NetStates.contactRecent]) ;
 *  - un changement d'état du réseau ([networkChanged] : réseau apparu ou perdu, avis `VALIDATED` changé) rend aussi la sonde due au prochain tour.
 *
 * Pur : l'heure vient de l'appelant, la sonde est injectée ([probe] : millisecondes, ou null si Internet ne répond pas).
 */
class DirectLeg(private val plan: NetProbePlan = NetProbePlan(), private val probe: () -> Long?) {
    /** [ms] : verdict du tour (null = pas joignable, ou pas su) ; [probed] : une vraie sonde a été faite à ce tour. */
    class Round(val ms: Long?, val probed: Boolean)

    /** Le résultat de la dernière sonde (réussie ou ratée), null avant toute sonde. */
    @Volatile var lastProbeMs: Long? = null; private set

    fun measure(now: Long, probeMode: Boolean, manual: Boolean, systemValidated: Boolean): Round {
        if (probeMode && plan.directDue(now, manual)) {
            plan.directDone(now)                     // avant la sonde : un événement qui arrive PENDANT elle reste dû au tour suivant
            val ms = probe()
            lastProbeMs = ms
            return Round(ms, true)
        }
        val ms = when {
            probeMode -> lastProbeMs                  // la sonde fait foi, réussie OU ratée : l'avis du système ne remplace jamais une sonde ratée
            systemValidated -> 0L                     // sans mode sonde (aucune requête vers un tiers) : l'avis du système, sans mesure
            else -> null
        }
        return Round(ms, false)
    }

    /** Un changement d'état du réseau de la TV : la prochaine sonde est due tout de suite. */
    fun networkChanged() = plan.markChanged()

    /** Un appel réel au serveur a échoué sur le réseau propre, à [now] (hors réponse du serveur). */
    fun callFailed(now: Long) = plan.directFailureSeen(now)

    /** Date du dernier appel réel échoué sur le réseau propre, 0 = aucun. */
    fun failedAt(): Long = plan.directFailedAt()

    companion object {
        /** Délais de connexion et de lecture de la sonde (`TvNetDiag.probe`) : le pire cas d'une sonde dure donc deux fois cela. */
        const val PROBE_TIMEOUT_MS = 6_000L
        /** Après un appel réel échoué, la TV re-vérifie son réseau dans ce délai au plus : un tour de boucle (60 s) + une sonde au pire cas. */
        const val RECHECK_BUDGET_MS = 90_000L
    }
}
