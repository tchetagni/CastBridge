package castbridge.core.tv

/** Délai d'affichage : visible jusqu'à [holdMs] après le dernier [touch] ; la pause la garde affichée si demandé. */
class AutoHide(private val holdMs: Long) {
    private var until = Long.MIN_VALUE
    fun touch(now: Long) { until = now + holdMs }
    fun clear() { until = Long.MIN_VALUE }
    fun visible(now: Long, paused: Boolean): Boolean = until != Long.MIN_VALUE && (paused || now < until)
}

/**
 * Lecteur de CastBridge-TV avec une télécommande de base (HAUT/BAS/GAUCHE/DROITE/OK/RETOUR) : table de décision PURE des touches.
 *  - barre cachée : OK affiche la barre de commandes (Pause, Audio, Sous-titres, Affichage, Infos) ; OK long ouvre les réglages ; GAUCHE/DROITE = +-10 s, HAUT/BAS = +-60 s ;
 *  - barre visible : GAUCHE/DROITE déplacent le focus, OK actionne le bouton, BAS ouvre les réglages, RETOUR cache la barre (n'arrête pas) ;
 *  - MENU et INFO fonctionnent toujours quand la télécommande les a.
 */
object PlayerRemote {
    /** La barre se cache après 5 s sans touche (jamais en pause) ; la ligne de diagnostic reste 6 s. */
    const val BAR_MS = 5000L
    const val DIAG_MS = 6000L
    enum class Key { OK, LEFT, RIGHT, UP, DOWN, BACK, MENU, INFO, OTHER }
    /** PASS : la touche revient au système (focus ou bouton). */
    enum class Act { SHOW_BAR, HIDE_BAR, SEEK_FWD_10, SEEK_BACK_10, SEEK_FWD_60, SEEK_BACK_60, OPEN_PANEL, SHOW_INFO, STOP, PASS }

    fun decide(key: Key, longPress: Boolean, barVisible: Boolean): Act = when (key) {
        Key.MENU -> Act.OPEN_PANEL
        Key.INFO -> Act.SHOW_INFO
        Key.OK -> if (longPress) Act.OPEN_PANEL else if (barVisible) Act.PASS else Act.SHOW_BAR
        Key.LEFT -> if (barVisible) Act.PASS else Act.SEEK_BACK_10
        Key.RIGHT -> if (barVisible) Act.PASS else Act.SEEK_FWD_10
        Key.UP -> Act.SEEK_FWD_60
        Key.DOWN -> if (barVisible) Act.OPEN_PANEL else Act.SEEK_BACK_60
        Key.BACK -> if (barVisible) Act.HIDE_BAR else Act.STOP
        Key.OTHER -> Act.PASS
    }
}
