package castbridge.core.tv.activation

import castbridge.core.quiz.QrCode
import castbridge.core.tv.WdCode
import castbridge.core.tv.WifiDirect
import castbridge.core.tv.pin.PinDisplay

/**
 * The guided activation screen of CastBridge-TV (F6, docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md): the ways to activate are NUMBERED in the order of what the TV
 * DETECTS, one status line each, large type, five keys (up, down, left, right, OK) are enough:
 *
 *  - A, the phone (the code; the QR and the TV's own Wi-Fi Direct group when the TV has no Wi-Fi network, or a cable, or when the person asks for it ([ActivationGroupPolicy]); else the
 *    phone on the same Wi-Fi; Bluetooth),
 *  - C, the USB key (banner at plug-in),
 *  - E, typing or pasting the key with the remote: the LAST RESORT, always last.
 *
 * Order: a key that verifies for this TV (one press away) first; else a phone already linked (the person is in the middle of the phone way); else a key plugged in (its reason is what
 * they are looking for); else A, C, E. Pure: the Android screen only draws what [plan] decides. The letters A, C, E are the ways of the design document; the screen shows 1, 2, 3.
 */
object ActivationScreenPlan {
    enum class Lane(val letter: Char) { PHONE('A'), USB('C'), TYPED('E') }

    /** The activation group of the TV (Wi-Fi Direct, derived from the connection code): the part of the phone way the person does not see. */
    sealed class Group {
        /** Not asked (not a locked TV, or the screen has not asked yet). */
        object NotTried : Group()
        /**
         * The TV is on a Wi-Fi network (no cable): its own group is NOT made, the screen offers it instead ([ActivationGroupPolicy]; a TV with a single Wi-Fi radio may lose its link
         * to the box while the group exists). The phone is expected on the same Wi-Fi.
         */
        object Offered : Group()
        object Starting : Group()
        object Ready : Group()
        /** [err] = a [WifiDirect.Err] word (null = unknown). */
        data class Failed(val err: String?) : Group()
    }

    /** What the screen knows right now: detected facts only, nothing secret. */
    data class Facts(
        val phoneLinked: Boolean = false,
        val usb: UsbActivationBanner.View = UsbActivationBanner.idle(),
        val group: Group = Group.NotTried,
        /** The TV's addresses on a local network (never the group's 192.168.49.x). */
        val lanIps: List<String> = emptyList(),
    )

    /** [number] = the position on screen (1, 2, 3); [status] = ONE line. */
    data class LaneView(val number: Int, val lane: Lane, val title: String, val status: String, val tone: LineTone)

    fun order(f: Facts): List<Lane> = when {
        f.usb.canActivate -> listOf(Lane.USB, Lane.PHONE, Lane.TYPED)
        f.phoneLinked -> listOf(Lane.PHONE, Lane.USB, Lane.TYPED)
        f.usb.keyPresent -> listOf(Lane.USB, Lane.PHONE, Lane.TYPED)
        else -> listOf(Lane.PHONE, Lane.USB, Lane.TYPED)
    }

    fun plan(f: Facts): List<LaneView> = order(f).mapIndexed { i, lane ->
        val (title, status, tone) = when (lane) {
            Lane.PHONE -> phoneStatus(f).let { Triple("Avec votre téléphone (le plus simple)", it.first, it.second) }
            Lane.USB -> Triple("Avec une clé USB", f.usb.presence, if (f.usb.canActivate) LineTone.GOOD else LineTone.INFO)
            Lane.TYPED -> Triple("Saisie à la télécommande (dernier recours)", "À utiliser seulement si les autres voies échouent : collez ou saisissez la clé, ou choisissez un fichier", LineTone.INFO)
        }
        LaneView(i + 1, lane, title, status, tone)
    }

    private fun phoneStatus(f: Facts): Pair<String, LineTone> {
        val lan = f.lanIps.firstOrNull()
        if (f.phoneLinked) return "Téléphone relié : suivez les étapes sur le téléphone" to LineTone.GOOD
        return when (val g = f.group) {
            Group.Ready -> ("Réseau direct prêt" + (lan?.let { " · ou même Wi-Fi : TV $it" } ?: "") + " · en attente du téléphone") to LineTone.INFO
            Group.Starting -> ("Réseau direct en préparation…" + (lan?.let { " · même Wi-Fi : TV $it" } ?: "")) to LineTone.INFO
            is Group.Failed -> (ActivationGroupTexts.failed(g.err) + (if (lan != null) " Le téléphone peut aussi utiliser le même Wi-Fi : TV $lan." else " Le Bluetooth reste possible.")) to LineTone.WARN
            Group.Offered -> ("En attente du téléphone sur le même Wi-Fi" + (lan?.let { " · TV $it" } ?: "")) to LineTone.INFO
            Group.NotTried -> (if (lan != null) "En attente du téléphone sur le même Wi-Fi · TV $lan" else "En attente du téléphone (Bluetooth possible)") to LineTone.INFO
        }
    }

    /** « 482 913 »: the connection code, three and three, as the phone's own screen says it. */
    fun groupedCode(code: String): String = PinDisplay.grouped(code)

    /**
     * The sentence under the code of the phone way; [qrShown] false = the TV has no group to put in a QR; [sameWifi] = that is because the group is only OFFERED (the TV is on a Wi-Fi
     * network): the phone is expected on that same Wi-Fi ([ActivationGroupPolicy.SAME_WIFI_INSTRUCTION]). A QR on screen always wins.
     */
    fun phoneInstruction(code: String, qrShown: Boolean, sameWifi: Boolean = false): String = when {
        qrShown -> "Scannez avec l'appareil photo du téléphone, ou tapez le code ${groupedCode(code)} dans CastBridge › Activer la TV"
        sameWifi -> ActivationGroupPolicy.SAME_WIFI_INSTRUCTION
        else -> "Sur votre téléphone, ouvrez CastBridge › Activer la TV et tapez le code ${groupedCode(code)}"
    }

    /** The « réseau direct » line the screen offers while the group is kept for later, and the warning said under it BEFORE the person presses it. */
    data class DirectOffer(val line: String, val warning: String)

    fun directOffer(g: Group): DirectOffer? =
        if (g is Group.Offered) DirectOffer(ActivationGroupPolicy.OFFER_LINE, ActivationGroupPolicy.OFFER_WARNING) else null

    /**
     * What the phone way draws under its title (the Android screen only draws it). [code] = the connection code grouped 3+3 (null = the locked route is not open yet: nothing to
     * show); [qr] = the QR of the group, drawn ONLY while the group exists (without one the code stands alone, a QR would describe a network that is not there); [instruction] = the
     * sentence under the code; [offer] = the « réseau direct » line, only while the group is kept for later; [retry] = « Réessayer le réseau direct », only when the group failed.
     */
    data class PhoneView(val code: String?, val qr: Boolean, val instruction: String, val offer: DirectOffer?, val retry: Boolean)

    const val NO_ROUTE_INSTRUCTION = "Sur votre téléphone, ouvrez CastBridge › Activer la TV."

    fun phoneView(code: String?, group: Group): PhoneView {
        if (code == null) return PhoneView(null, false, NO_ROUTE_INSTRUCTION, null, false)
        val qr = group is Group.Ready && WdCode.isValid(code)
        return PhoneView(groupedCode(code), qr, phoneInstruction(code, qrShown = qr, sameWifi = group is Group.Offered), directOffer(group), group is Group.Failed)
    }

    /** A phone presented the right code this recently, or is in the group, counts as « relié ». */
    const val LINK_WINDOW_MS = 120_000L

    /** [lastAuthorizedMs]: a monotonic time (0 or null = never); [groupClients]: phones in the TV's group (null = unknown). */
    fun phoneLinked(nowMs: Long, lastAuthorizedMs: Long?, groupClients: Int?): Boolean =
        (groupClients ?: 0) > 0 || (lastAuthorizedMs != null && lastAuthorizedMs > 0 && nowMs - lastAuthorizedMs in 0..LINK_WINDOW_MS)
}

/** One line for each reason the TV has no group of its own ([WifiDirect.Err]); the local-network way stays open whatever the reason. Never a password. */
object ActivationGroupTexts {
    fun failed(err: String?): String = "Réseau direct impossible : " + when (err) {
        WifiDirect.Err.WIFI_OFF -> "le Wi-Fi de la TV est éteint (Paramètres › Réseau)"
        WifiDirect.Err.UNSUPPORTED -> "cette TV ne sait pas créer de réseau Wi-Fi direct"
        WifiDirect.Err.PERMISSION -> "CastBridge-TV n'a pas la permission « Appareils à proximité »"
        else -> "la TV n'a pas pu le créer"
    } + "."
}

/** The QR of the activation screen: the Wi-Fi URI of the group derived from the connection code, drawn at least a quarter of the screen high, in whole pixels. */
object ActivationQr {
    /** The quiet zone around the code, in modules (ISO 18004). */
    const val QUIET_MODULES = 4
    private const val WANTED_PERCENT = 36

    /** [modulePx] = pixels per module (a whole number: crisp edges), [quietPx] = the white margin, [sidePx] = the whole square. */
    data class Layout(val modulePx: Int, val quietPx: Int, val sidePx: Int)

    /** Never smaller than a quarter of [screenHeightPx] (ACT-F6), about [WANTED_PERCENT] % when the whole-pixel rounding allows, never more than half. [modules] = the QR's modules per side. */
    fun layout(screenHeightPx: Int, modules: Int): Layout {
        require(screenHeightPx > 0 && modules > 0)
        val total = modules + 2 * QUIET_MODULES
        val quarter = (screenHeightPx + 3) / 4
        val atLeast = (quarter + total - 1) / total
        val wanted = screenHeightPx * WANTED_PERCENT / 100 / total
        val m = maxOf(1, atLeast, wanted)
        return Layout(m, QUIET_MODULES * m, total * m)
    }

    /** What the QR holds: the WIFI: URI of the group (the phone's camera offers to join it); the code itself is not in it. */
    fun uri(code: String): String = WdCode.wifiUri(code)

    fun encode(code: String): QrCode = QrCode.encode(uri(code), QrCode.Ecl.M)
}
