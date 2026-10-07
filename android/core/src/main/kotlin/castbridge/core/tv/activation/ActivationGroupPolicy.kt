package castbridge.core.tv.activation

/**
 * When the activation screen of a LOCKED TV makes its own Wi-Fi Direct group (act-tv-2, docs/TV-ACTIVATION-CLE-USB.md « Voie A : deux cas »).
 *
 * Why: a TV with a SINGLE Wi-Fi radio (a USB Wi-Fi key, the reference TV) may lose its link to the box while its own group exists. The phone tries the local network first
 * (mDNS, the TV on the same Wi-Fi), so a group made for nothing could cut the very way the phone is about to use. The rule:
 *  1. the TV has no Wi-Fi network and no cable: nothing to lose, the group is made at once (it is the phone's only way in);
 *  2. the TV is on a Wi-Fi network: the group is NOT made; the screen says « Téléphone sur le même Wi-Fi : tapez le code … » and offers a focusable line « Le téléphone n'est
 *     pas sur ce Wi-Fi ? OK : réseau direct » that makes the group on request, with the warning that the TV's Wi-Fi may drop while it exists;
 *  3. the TV has a cable: Wi-Fi Direct never touches it, the group is made at once;
 *  4. once the group is given back, a Wi-Fi link that it cut is relaunched ([restore]: `WifiManager.reconnect()`, best effort, bounded, logged).
 *
 * Pure: the Android side ([castbridge.receiver.ActivationNet]) only reads the facts and does what this decides. Nothing here is secret: no code, no address, no password.
 */
object ActivationGroupPolicy {
    /**
     * What the TV is linked to, as the system says it: [wifiConnected] = a Wi-Fi network with a local IPv4 address (never the activation group's own 192.168.49.x),
     * [ethernetUp] = a cable network with one.
     */
    data class Net(val wifiConnected: Boolean = false, val ethernetUp: Boolean = false)

    /**
     * Why the group is made now ([create]) or kept for the person's request (not [create]). [log] = the one line for `adb logcat`: no digit, so never a code or an address.
     */
    enum class Decision(val create: Boolean, val log: String) {
        /** No Wi-Fi network and no cable: nothing to cut. */
        NO_WIFI(true, "la TV n'est reliée à aucun Wi-Fi ni câble : réseau direct créé tout de suite"),
        /** A cable: Wi-Fi Direct never touches it. */
        ETHERNET(true, "la TV est reliée par câble, que le Wi-Fi Direct ne touche pas : réseau direct créé tout de suite"),
        /** The person pressed « réseau direct » (or « Réessayer le réseau direct »). */
        ASKED(true, "réseau direct demandé à l'écran : créé"),
        /** On a Wi-Fi network, no cable, nobody asked: the screen offers the group, it does not make it. */
        WIFI_PRESENT(false, "la TV est sur un Wi-Fi : réseau direct proposé à l'écran, pas créé"),
    }

    /** [asked] = the person asked for the direct network on this opening of the screen (kept until the group is given back). */
    fun decide(net: Net, asked: Boolean): Decision = when {
        asked -> Decision.ASKED
        net.ethernetUp -> Decision.ETHERNET
        !net.wifiConnected -> Decision.NO_WIFI
        else -> Decision.WIFI_PRESENT
    }

    /** The focusable line offered while the group is kept for later. « OK » is the remote's OK key. */
    const val OFFER_LINE = "Le téléphone n'est pas sur ce Wi-Fi ? OK : réseau direct"

    /** Said under the line, BEFORE the person presses it: a TV with a single Wi-Fi radio can lose its link while its own network exists. */
    const val OFFER_WARNING = "Le Wi-Fi de la TV peut se couper le temps de l'activation."

    /** The sentence under the code while the group is kept for later (no QR: it would describe a network that does not exist). */
    const val SAME_WIFI_INSTRUCTION = "Téléphone sur le même Wi-Fi : tapez le code dans CastBridge › Activer la TV"

    // ---- the Wi-Fi link once the group is given back ----

    /** What to do about the TV's Wi-Fi link at one look after the group is given back. */
    enum class Restore {
        /** Nothing was cut, it is back, or the radio is off: leave it. */
        NOTHING,
        /** `WifiManager.reconnect()`, then look again. */
        RECONNECT,
        /** Tried [RESTORE_ATTEMPTS] times: say it once, stop. */
        GIVE_UP,
    }

    /** Reconnections tried at most after one group (Android 10+ may refuse them to an app like this one: they are best effort, the journal says what happened). */
    const val RESTORE_ATTEMPTS = 2

    /** The first look, once the group is gone (Android needs a moment to bring the Wi-Fi back by itself). */
    private const val RESTORE_FIRST_LOOK_MS = 3_000L

    /** The looks after a reconnection. */
    private const val RESTORE_NEXT_LOOK_MS = 7_000L

    /** Delay before the look number [attempts] + 1 (0 = right after the group): 3 s, then 7 s each, 17 s at most in all. */
    fun restoreDelayMs(attempts: Int): Long = if (attempts <= 0) RESTORE_FIRST_LOOK_MS else RESTORE_NEXT_LOOK_MS

    /**
     * [wifiBefore] = the TV was on a Wi-Fi network when the group was made, [wifiNow] = it is now, [wifiEnabled] = the Wi-Fi radio is on, [attempts] = reconnections already tried.
     * Only a link that was there and is not any more is relaunched: a TV that had no Wi-Fi is left alone, and so is a radio somebody switched off.
     */
    fun restore(wifiBefore: Boolean, wifiNow: Boolean, wifiEnabled: Boolean, attempts: Int): Restore = when {
        !wifiBefore || wifiNow || !wifiEnabled -> Restore.NOTHING
        attempts >= RESTORE_ATTEMPTS -> Restore.GIVE_UP
        else -> Restore.RECONNECT
    }
}
