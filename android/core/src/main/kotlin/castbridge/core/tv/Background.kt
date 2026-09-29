package castbridge.core.tv

/**
 * Thrown by the TV app's player when a remote asks to play but the screen cannot be brought to the front (Android 10+
 * restricts activity starts from the background). The server answers 409 {"needsForeground":true,"message"}.
 */
class NeedsForeground(message: String) : RuntimeException(message)

/** When the TV app's background service starts (pure; the Android receiver only passes the broadcast action). */
object BootPolicy {
    const val BOOT = "android.intent.action.BOOT_COMPLETED"
    const val QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"          // some TV firmwares send this one instead
    const val HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    const val REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

    /** Start at boot only if "Démarrer avec la TV" is on; after an update, restart if it is on (the update stopped the app). */
    fun shouldStart(action: String?, autostart: Boolean): Boolean =
        autostart && action in setOf(BOOT, QUICKBOOT, HTC_QUICKBOOT, REPLACED)
}

/**
 * How to show the TV screen when a remote asks for playback while CastBridge TV is not in front. Android 10+ blocks
 * activity starts from the background unless the app may "display over other apps"; otherwise a (full-screen, if allowed)
 * notification is the only way, and the phone is told to ask someone to open the app.
 */
object LaunchPolicy {
    enum class Way { DIRECT, START_ACTIVITY, FULL_SCREEN_NOTIFICATION, NOTIFICATION }

    data class State(
        /** The player screen is resumed and visible. */
        val screenVisible: Boolean,
        /** SYSTEM_ALERT_WINDOW granted ("afficher par-dessus les autres apps"): exempt from the background start restriction. */
        val overlayAllowed: Boolean,
        /** USE_FULL_SCREEN_INTENT usable (NotificationManager.canUseFullScreenIntent on API 34+). */
        val fullScreenAllowed: Boolean,
        /** Notifications not blocked for the app (POST_NOTIFICATIONS on API 33+). */
        val notificationsAllowed: Boolean,
        val sdk: Int,
    )

    /** What to try, in order. An empty list means: nothing can bring the screen up, tell the phone. */
    fun ways(s: State): List<Way> = when {
        s.screenVisible -> listOf(Way.DIRECT)
        s.sdk < 29 || s.overlayAllowed -> listOf(Way.START_ACTIVITY)
        else -> listOfNotNull(
            Way.FULL_SCREEN_NOTIFICATION.takeIf { s.fullScreenAllowed && s.notificationsAllowed },
            Way.NOTIFICATION.takeIf { s.notificationsAllowed },
        )
    }

    /** True when the playback request can be answered only after someone acts on the TV. */
    fun needsSomeone(ways: List<Way>) = ways.none { it == Way.DIRECT || it == Way.START_ACTIVITY }

    /** What the phone shows when the screen could not be brought up by itself. */
    fun message(ways: List<Way>): String = when {
        ways.contains(Way.FULL_SCREEN_NOTIFICATION) || ways.contains(Way.NOTIFICATION) ->
            "La TV a affiché une notification « CastBridge TV » : ouvrez-la avec la télécommande (ou ouvrez l'app CastBridge TV) pour lancer la lecture. " +
                "Pour que la lecture démarre seule, autorisez « Afficher par-dessus les autres apps » pour CastBridge TV (MENU > Lecture à distance)."
        else -> "Ouvrez l'app CastBridge TV sur la TV pour lancer la lecture. Pour que la lecture démarre seule, autorisez « Afficher par-dessus les autres apps » " +
            "pour CastBridge TV (MENU > Lecture à distance)."
    }

    /** A pending request older than this is dropped (nobody wants a film to start by itself an hour later). */
    const val PENDING_MAX_AGE_MS = 120_000L

    fun pendingValid(createdAtMs: Long, nowMs: Long) = nowMs - createdAtMs in 0..PENDING_MAX_AGE_MS
}
