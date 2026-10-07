package castbridge.core.relay

/**
 * Les mots du relais, en français, pour les deux applications (CastBridge sur le téléphone, CastBridge-TV sur la TV). Jamais un nom de fichier, un code,
 * une clé ni une adresse : le relais est discret (DESIGN-RELAIS § 2.6). La notification du téléphone est la même pour tous les usages.
 */
object RelayText {
    // ---- TV ----
    const val NO_PHONE = "Aucun téléphone n'est synchronisé avec la TV : synchronisez-en un pour avoir Internet par le téléphone."
    /** Le texte demandé par le propriétaire pour un téléphone ancien (sans demande de tuyau). */
    const val OLD_PHONE = "Mettez CastBridge à jour pour l'Internet par relais"
    const val ASKING = "Demande d'Internet au téléphone…"
    const val TIMEOUT = "Le téléphone ne répond pas : gardez-le près de la TV, Bluetooth allumé."
    const val PAUSED = "Le téléphone n'a pas ouvert Internet. Appuyez de nouveau pour réessayer."
    /** Ligne d'état de la TV quand le tuyau est ouvert. */
    const val VIA_PHONE = "Internet par le téléphone"
    /** Pendant une partie par relais (§ 2.5) : informatif, sans alarme. */
    const val PLAY_LINE = "Partie par relais : liaison lente"

    fun refusal(r: RelayReason): String = when (r) {
        RelayReason.NOT_SYNCED -> "Ce téléphone n'est pas synchronisé avec la TV."
        RelayReason.OPTED_OUT -> "Le téléphone ne relaie plus Internet pour cette TV (réglage de CastBridge)."
        RelayReason.OFFLINE -> "Le téléphone n'a pas Internet : vérifiez son Wi-Fi ou ses données mobiles."
        RelayReason.METERED_BULK -> "Le téléphone est sur données mobiles : ce téléchargement attend le Wi-Fi."
        RelayReason.CAP -> "Le téléphone a atteint son plafond de données mobiles pour aujourd'hui."
        RelayReason.BACKGROUND -> "Ouvrez CastBridge sur le téléphone pour qu'il partage Internet avec la TV."
        RelayReason.BUSY -> "Le téléphone est occupé : réessayez dans un instant."
    }

    // ---- téléphone ----
    /** La seule notification du relais : neutre, sans nom de fichier ni de contenu ; le nom de la TV est celui que l'utilisateur lui a donné. */
    fun notification(tvName: String): String = "CastBridge relaie pour " + clean(tvName)
    const val NOTIFICATION_PUBLIC = "CastBridge"
    const val NOTIFICATION_CHANNEL = "Internet partagé avec la TV"
    const val STOP = "Arrêter"

    private fun clean(name: String): String = name.filter { !it.isISOControl() }.trim().take(40).ifEmpty { "la TV" }
}
