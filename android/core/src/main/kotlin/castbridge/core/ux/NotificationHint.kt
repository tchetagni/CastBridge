package castbridge.core.ux

import castbridge.core.tv.NotificationAccessPolicy

/**
 * R-52 (2026-10-14, « les copies ne signalent plus en notification »), côté téléphone : texte dit au-dessus de la carte « Envoi vers la TV » et de la file d'attente quand Android
 * jette les notifications de CastBridge. Sur le S21+ (Android 14) POST_NOTIFICATIONS n'était pas accordée (`importance=NONE`) et l'app ne disait rien : la progression des copies
 * disparaissait de la barre sans une ligne d'explication. Cause côté téléphone : depuis l'ergonomie du 2026-10-03 l'app s'ouvre sur l'accueil « CastBridge TV », et la seule demande
 * d'autorisation (`MainActivity.App`) vit dans l'onglet « TV DLNA » : elle n'était plus jamais posée à qui n'ouvre pas cet onglet. Pur : l'écran lit l'état d'Android et dessine ce qui
 * sort d'ici (garde de source `NotificationHintSourceTest`) ; la décision « redemander l'autorisation ou ouvrir le réglage » est celle de [NotificationAccessPolicy], la même que sur la TV.
 */
object NotificationHint {
    /** Le bouton à côté du texte : demande l'autorisation, ou ouvre le réglage des notifications de l'app. */
    const val BUTTON = "Autoriser"

    /**
     * Les canaux de notification des copies, tels que les services du téléphone les créent (identifiant, nom lisible) : `UploadService` (envoi par le réseau),
     * `TransferQueueService` (la file) et `BtUploadService` (envoi par Bluetooth). Un test de source garde ces deux listes d'accord.
     */
    val COPY_CHANNELS: List<Pair<String, String>> = listOf(
        "upload" to "Envoi vers la TV",
        "queue" to "File d'attente des envois",
        "btupload" to "Envoi Bluetooth",
    )

    private const val TAIL = "la progression de ces copies n'apparaît pas dans la barre."

    /**
     * Le texte à montrer, ou null quand tout va bien. [enabled] = Android laisse CastBridge notifier ; [blockedChannels] = les noms lisibles des canaux de copie que l'usager a
     * coupés un à un. L'app entière coupée dit tout (pas de liste de canaux par-dessus).
     */
    fun text(enabled: Boolean, blockedChannels: List<String>): String? = when {
        !enabled -> "Notifications désactivées pour CastBridge : la progression des copies n'apparaît pas dans la barre."
        blockedChannels.isEmpty() -> null
        blockedChannels.size == 1 -> "Canal « ${blockedChannels[0]} » désactivé : $TAIL"
        else -> "Canaux ${names(blockedChannels)} désactivés : $TAIL"
    }

    /**
     * Ce que fait « Autoriser ». Seuls des CANAUX coupés ([appEnabled] vrai) : l'autorisation est déjà accordée, rien à redemander, ils se rallument dans le réglage de l'app. App entière
     * coupée : [NotificationAccessPolicy] décide d'après Android 13+ ([sdk]), `shouldShowRequestPermissionRationale` ([rationale]) et la mémoire d'un refus déjà reçu ([deniedBefore]) ;
     * un téléphone qui n'a jamais été interrogé (l'app s'ouvre sur l'accueil, la demande vit dans l'onglet « TV DLNA ») reçoit donc le dialogue d'Android, pas le réglage.
     */
    fun buttonAction(sdk: Int, appEnabled: Boolean, rationale: Boolean, deniedBefore: Boolean): NotificationAccessPolicy.Action =
        NotificationAccessPolicy.action(sdk, enabled = false, canAskAgain = !appEnabled && NotificationAccessPolicy.canAskAgain(rationale, deniedBefore))

    /** « A », « A » et « B », « A », « B » et « C ». */
    private fun names(l: List<String>): String =
        if (l.size == 1) "« ${l[0]} »" else l.dropLast(1).joinToString(", ") { "« $it »" } + " et « ${l.last()} »"
}
