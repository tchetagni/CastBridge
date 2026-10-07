package castbridge.core.remote

/**
 * Quand l'intention qui a lancé l'application peut-elle faire passer CastBridge-TV devant l'écran de la TV (« Ouvrir CastBridge-TV » : appui long sur l'icône, ou `castbridge://open-tv`) ?
 *
 * R-34 (audit anti-régression 2026-10-07 b, I-15) : seulement pour un GESTE NEUF. `MainActivity` ne « consommait » le lien qu'en mémoire (`intent.data = null`). Raccourci, puis mort du
 * processus (mémoire, mise à jour), puis retour par les applications récentes : Android recrée l'activité avec l'intention d'ORIGINE, lien compris, et l'ouverture partait de nouveau, sans
 * geste : la TV quittait ce qu'elle jouait. Une activité recréée a un état sauvegardé ([restored]) et/ou l'intention porte `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` ([fromHistory]) : ce n'est
 * jamais un geste.
 */
object OpenTvLaunch {
    /** Vrai seulement pour une intention neuve : ni activité recréée ([restored] : `savedInstanceState != null`), ni retour par les récents ([fromHistory]). */
    fun fires(restored: Boolean, fromHistory: Boolean): Boolean = !restored && !fromHistory
}
