package castbridge.core.remote

/**
 * Keys the phone remote may send to CastBridge TV (docs/REMOTE.md). A closed list: anything else is refused (400), so the
 * route can never be used to inject arbitrary key codes. [code] is the android.view.KeyEvent constant (stable public values,
 * copied here so this module stays plain JVM and testable).
 */
enum class RemoteKey(val wire: String, val code: Int, val kind: Kind, val label: String) {
    DPAD_UP("DPAD_UP", 19, Kind.NAV, "Haut"),
    DPAD_DOWN("DPAD_DOWN", 20, Kind.NAV, "Bas"),
    DPAD_LEFT("DPAD_LEFT", 21, Kind.NAV, "Gauche"),
    DPAD_RIGHT("DPAD_RIGHT", 22, Kind.NAV, "Droite"),
    DPAD_CENTER("DPAD_CENTER", 23, Kind.NAV, "OK"),
    BACK("BACK", 4, Kind.SYSTEM, "Retour"),
    MENU("MENU", 82, Kind.SYSTEM, "Menu"),
    /** The CastBridge home (never the TV's launcher: that one is [RemoteGlobal.HOME], accessibility mode only). */
    HOME("HOME", 3, Kind.SYSTEM, "Accueil CastBridge"),
    PLAY_PAUSE("PLAY_PAUSE", 85, Kind.MEDIA, "Lecture/pause"),
    PLAY("PLAY", 126, Kind.MEDIA, "Lecture"),
    PAUSE("PAUSE", 127, Kind.MEDIA, "Pause"),
    STOP("STOP", 86, Kind.MEDIA, "Stop"),
    NEXT("NEXT", 87, Kind.MEDIA, "Suivant"),
    PREVIOUS("PREVIOUS", 88, Kind.MEDIA, "Précédent"),
    REWIND("REWIND", 89, Kind.MEDIA, "−10 s"),
    FAST_FORWARD("FAST_FORWARD", 90, Kind.MEDIA, "+10 s"),
    VOLUME_UP("VOLUME_UP", 24, Kind.VOLUME, "Volume +"),
    VOLUME_DOWN("VOLUME_DOWN", 25, Kind.VOLUME, "Volume −"),
    VOLUME_MUTE("VOLUME_MUTE", 164, Kind.VOLUME, "Muet"),
    CHANNEL_UP("CHANNEL_UP", 166, Kind.APP, "CH +"),
    CHANNEL_DOWN("CHANNEL_DOWN", 167, Kind.APP, "CH −"),
    INFO("INFO", 165, Kind.APP, "Info"),
    CAPTIONS("CAPTIONS", 175, Kind.APP, "Sous-titres"),
    AUDIO_TRACK("AUDIO_TRACK", 222, Kind.APP, "Piste audio"),
    GUIDE("GUIDE", 172, Kind.APP, "Bibliothèque"),
    ENTER("ENTER", 66, Kind.TEXT, "Entrée"),
    DEL("DEL", 67, Kind.TEXT, "Effacer"),
    NUM_0("0", 7, Kind.DIGIT, "0"), NUM_1("1", 8, Kind.DIGIT, "1"), NUM_2("2", 9, Kind.DIGIT, "2"),
    NUM_3("3", 10, Kind.DIGIT, "3"), NUM_4("4", 11, Kind.DIGIT, "4"), NUM_5("5", 12, Kind.DIGIT, "5"),
    NUM_6("6", 13, Kind.DIGIT, "6"), NUM_7("7", 14, Kind.DIGIT, "7"), NUM_8("8", 15, Kind.DIGIT, "8"),
    NUM_9("9", 16, Kind.DIGIT, "9");

    enum class Kind { NAV, SYSTEM, MEDIA, VOLUME, APP, TEXT, DIGIT }

    /** Keys that auto-repeat while held (a held arrow scrolls a list; a held OK is a long press instead). */
    val repeatable: Boolean get() = kind == Kind.NAV && this != DPAD_CENTER || kind == Kind.VOLUME && this != VOLUME_MUTE ||
        this == DEL || this == REWIND || this == FAST_FORWARD || this == CHANNEL_UP || this == CHANNEL_DOWN

    val isDirection: Boolean get() = this == DPAD_UP || this == DPAD_DOWN || this == DPAD_LEFT || this == DPAD_RIGHT

    companion object {
        private val ALIASES = mapOf(
            "UP" to DPAD_UP, "DOWN" to DPAD_DOWN, "LEFT" to DPAD_LEFT, "RIGHT" to DPAD_RIGHT, "OK" to DPAD_CENTER, "CENTER" to DPAD_CENTER,
            "SELECT" to DPAD_CENTER, "MEDIA_PLAY_PAUSE" to PLAY_PAUSE, "MEDIA_PLAY" to PLAY, "MEDIA_PAUSE" to PAUSE, "MEDIA_STOP" to STOP,
            "MEDIA_NEXT" to NEXT, "MEDIA_PREVIOUS" to PREVIOUS, "MEDIA_REWIND" to REWIND, "MEDIA_FAST_FORWARD" to FAST_FORWARD,
            "MUTE" to VOLUME_MUTE, "MEDIA_AUDIO_TRACK" to AUDIO_TRACK, "BACKSPACE" to DEL,
        )
        private val BY_WIRE = values().associateBy { it.wire }

        /** "DPAD_UP", "keycode_dpad_up", "up", "OK", "5"… -> the key, or null when it is not on the list. */
        fun parse(raw: String?): RemoteKey? {
            val s = raw?.trim()?.uppercase()?.removePrefix("KEYCODE_") ?: return null
            if (s.isEmpty() || s.length > 32) return null
            return BY_WIRE[s] ?: ALIASES[s] ?: s.removePrefix("NUM_").takeIf { it.length == 1 && it[0].isDigit() }?.let { BY_WIRE[it] }
        }
    }
}

/** System-wide actions, possible only through the optional accessibility service on the TV (AccessibilityService constants). */
enum class RemoteGlobal(val wire: String, val action: Int, val label: String) {
    BACK("BACK", 1, "Retour"),
    HOME("HOME", 2, "Accueil de la TV"),
    RECENTS("RECENTS", 3, "Apps récentes"),
    NOTIFICATIONS("NOTIFICATIONS", 4, "Notifications"),
    QUICK_SETTINGS("QUICK_SETTINGS", 5, "Réglages rapides"),
    POWER_DIALOG("POWER_DIALOG", 6, "Menu marche/arrêt");

    companion object {
        fun parse(raw: String?): RemoteGlobal? = raw?.trim()?.uppercase()?.let { s -> values().firstOrNull { it.wire == s } }
    }
}

/** How a key is sent: a full press (down + up), the halves of a held key, or a long press (held past the long-press delay). */
enum class KeyAction(val wire: String) {
    PRESS("press"), DOWN("down"), UP("up"), LONG("long");
    companion object { fun parse(raw: String?): KeyAction? = if (raw.isNullOrEmpty()) PRESS else values().firstOrNull { it.wire == raw.lowercase() } }
}

/** Where keys go: CastBridge's own screens, the whole TV (accessibility service), or the first that is possible. */
enum class RemoteTarget(val wire: String) {
    AUTO("auto"), APP("app"), SYSTEM("system");
    companion object { fun parse(raw: String?): RemoteTarget? = if (raw.isNullOrEmpty()) AUTO else values().firstOrNull { it.wire == raw.lowercase() } }
}

/** What to do with typed text in the focused field. */
enum class TextMode(val wire: String) {
    INSERT("insert"), REPLACE("replace"), CLEAR("clear");
    companion object { fun parse(raw: String?): TextMode? = if (raw.isNullOrEmpty()) INSERT else values().firstOrNull { it.wire == raw.lowercase() } }
}
