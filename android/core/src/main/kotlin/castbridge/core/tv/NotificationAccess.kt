package castbridge.core.tv

/**
 * Notifications de CastBridge-TV (R-52, 2026-10-14 : « les copies ne signalent plus en notification ») : règles PURES. La partie Android (le MENU, le dialogue d'autorisation,
 * l'écran de réglage) ne fait que les exécuter ; le téléphone y puise aussi sa décision « demander ou ouvrir le réglage » (castbridge.core.ux.NotificationHint).
 *
 * Depuis Android 13 (API 33) POST_NOTIFICATIONS est une autorisation à accorder. Refusée, elle fait jeter EN SILENCE toute notification de l'app (« Réception : X … % »,
 * « Vidéo reçue ✓ », « Le téléphone demande d'ouvrir CastBridge-TV ») : constat ADB du 2026-10-15 sur le S21+ (`importance=NONE`, `POST_NOTIFICATIONS granted=false`).
 * Android ne remontre le dialogue qu'UNE fois de plus après un refus (RETOUR sur le dialogue compte comme un refus), puis plus jamais : seul le réglage de l'app peut alors
 * rendre l'autorisation. Sur la TV le dialogue est de surcroît GROUPÉ avec les autorisations Bluetooth au lancement ; avant R-52 rien ne détectait le refus ni ne l'expliquait.
 */
object NotificationAccessPolicy {
    /** La ligne du MENU de la TV, présente seulement tant que les notifications de CastBridge-TV sont désactivées. Commence par « Notifications » (contrôle parental : un réglage). */
    const val LINE = "Notifications : désactivées sur cette TV — autoriser (copies reçues, demandes du téléphone)"
    const val GRANTED = "Notifications autorisées"
    const val REFUSED = "Notifications refusées : MENU > Notifications pour les autoriser plus tard"
    /** Ni l'écran des notifications de l'app ni celui des détails de l'app n'existent sur ce boîtier (même mot que les autres lignes de réglage du MENU). */
    const val SETTINGS_UNAVAILABLE = "Réglage indisponible sur cette TV"
    /** Bandeau dit UNE fois par lancement de l'app, au premier transfert reçu, quand Android jette les notifications. */
    const val OFF_NOTICE = "Notifications désactivées : les copies ne s'affichent qu'à l'écran (MENU > Notifications)"

    /** Transferts retenus au plus (le service vit des jours : la mémoire des annonces reste petite ; un transfert est annoncé bien avant que 256 autres commencent). */
    const val MAX_REMEMBERED = 256

    /** Ce que fait l'appui sur la ligne : redemander (dialogue d'Android), ouvrir le réglage des notifications de l'app, ou rien. */
    enum class Action { ASK, OPEN_SETTINGS, NONE }

    /**
     * `enabled` : Android laisse l'app notifier ; `canAskAgain` : Android montrera encore son dialogue d'autorisation (voir [canAskAgain]). Notifications allumées ⇒ rien. Avant Android 8
     * (API 26) il n'y a ni canaux ni écran de réglage par app ⇒ rien à proposer. Android 13+ et dialogue encore possible ⇒ redemander ; sinon (dialogue mort, ou Android 8 à 12 qui n'en
     * a pas) ⇒ le réglage de l'app.
     */
    fun action(sdk: Int, enabled: Boolean, canAskAgain: Boolean): Action = when {
        enabled -> Action.NONE
        sdk < 26 -> Action.NONE
        sdk >= 33 && canAskAgain -> Action.ASK
        else -> Action.OPEN_SETTINGS
    }

    /**
     * Le dialogue d'autorisation peut-il encore venir ? [rationale] = `shouldShowRequestPermissionRationale` (vrai après UN refus) ; [deniedBefore] = Android a déjà répondu « refusé » à
     * cette app (dialogue groupé du lancement ou appui sur la ligne), retenu dans les préférences de la TV. Faux seulement quand Android a déjà refusé ET n'a plus de dernière chance à
     * offrir (refusé deux fois, « ne plus demander ») : demander ne servirait à rien, il faut le réglage de l'app. [rationale] seul ne suffit pas : il est faux AUSSI quand le dialogue
     * n'a jamais été montré ; c'est la mémoire du refus qui distingue « jamais demandé » de « refus définitif ».
     */
    fun canAskAgain(rationale: Boolean, deniedBefore: Boolean): Boolean = rationale || !deniedBefore

    /**
     * Vrai UNE seule fois par transfert ([seq] : numéro stable de [castbridge.core.xfer.TransferProgress.Item], reprises comprises) : le premier événement d'un transfert est dit à
     * l'écran (bandeau « ⬇ Réception de … »), les suivants non. Le transfert est noté dès cet appel, que l'écran le montre ou non. [announced] est borné à [MAX_REMEMBERED] (on oublie
     * le plus ancien ; jamais le numéro qui vient d'être noté, même dans un ensemble non ordonné).
     */
    fun firstAnnouncement(seq: Int, announced: MutableSet<Int>): Boolean {
        if (!announced.add(seq)) return false
        if (announced.size > MAX_REMEMBERED) {
            val old = announced.iterator()
            while (old.hasNext()) { if (old.next() != seq) { old.remove(); break } }
        }
        return true
    }
}
