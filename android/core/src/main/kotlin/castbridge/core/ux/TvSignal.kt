package castbridge.core.ux

/**
 * Signalétique de CastBridge-TV : la couleur dit ce que l'usager peut FAIRE (docs/coordination/DESIGN-UX-ERGONOMIE-NAVIGATION-2026-10-03.md, « Signalétique de la TV »).
 * VERT = ça marche maintenant · ORANGE = ça marche mais dégradé ou demande de l'attention · ROUGE = ne peut pas faire ce que l'étiquette promet ·
 * NOIR = inactif ou sans objet par choix. Jamais la couleur seule : chaque niveau a aussi une forme et un mot.
 * CastBridge-TV n'a JAMAIS besoin d'Internet (le téléphone apporte les données) : Internet absent est NOIR, jamais rouge ni orange.
 */
enum class SignalLevel(val shape: Shape, val word: String) {
    GREEN(Shape.CIRCLE, "OK"), ORANGE(Shape.TRIANGLE, "Attention"), RED(Shape.SQUARE, "Problème"), BLACK(Shape.RING, "Inactif");

    /** Gravité pour « le pire des indicateurs » : le noir ne pèse rien (inactif par choix). */
    val severity: Int get() = when (this) { BLACK -> 0; GREEN -> 0; ORANGE -> 1; RED -> 2 }
}

/** Forme de la pastille : distincte par niveau (lisible sans voir les couleurs). */
enum class Shape { CIRCLE, TRIANGLE, SQUARE, RING }

/** Jetons de couleur sur fond sombre de la TV (BG 0A0F1E). Distincts de l'ambre de la marque (#F5B025), qui n'est PAS une couleur d'état. */
object SignalColors {
    const val BACKGROUND = 0xFF0A0F1E.toInt()
    const val GREEN = 0xFF3DDC84.toInt()
    const val ORANGE = 0xFFFF7A1A.toInt()
    const val RED = 0xFFFF5252.toInt()
    /** Noir : pastille gris très sombre avec contour clair, jamais invisible sur fond sombre. */
    const val BLACK_FILL = 0xFF10141C.toInt()
    const val BLACK_OUTLINE = 0xFFB7C0D4.toInt()
    const val TEXT = 0xFFF4F6FB.toInt()

    fun of(level: SignalLevel): Int = when (level) { SignalLevel.GREEN -> GREEN; SignalLevel.ORANGE -> ORANGE; SignalLevel.RED -> RED; SignalLevel.BLACK -> BLACK_OUTLINE }

    /** Rapport de contraste WCAG entre deux couleurs ARGB opaques. */
    fun contrast(a: Int, b: Int): Double {
        fun lum(c: Int): Double {
            fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch((c shr 16) and 255) + 0.7152 * ch((c shr 8) and 255) + 0.0722 * ch(c and 255)
        }
        val x = lum(a); val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}

enum class LanKind { NONE, WIFI, ETHERNET }
enum class BtState { OFF, ON, UNUSABLE }
enum class StorageState { OK, LOW, FULL }
enum class IndicatorKind(val label: String) { RECEPTION("Réception"), NETWORK("Réseau"), BLUETOOTH("Bluetooth"), INTERNET("Internet"), STORAGE("Stockage") }

/** Ce que la TV sait d'elle-même, relevé par l'écran (mince) ; toutes les décisions sont dans [TvSignal.of]. */
data class TvFacts(
    val lan: LanKind = LanKind.WIFI,
    /** Wi-Fi associé mais sans adresse IP (le routeur n'a pas encore répondu) : n'est pas un réseau utilisable. */
    val wifiNoAddress: Boolean = false,
    val wifiName: String? = null,
    val weakSignal: Boolean = false,
    val bluetooth: BtState = BtState.ON,
    val pairedPhones: Int = 1,
    val listening: Boolean = true,
    val storage: StorageState = StorageState.OK,
    val freeText: String? = null,
    val usbKey: Boolean = false,
    val usbSlow: Boolean = false,
    val internet: Boolean = false,
    val parentalLock: Boolean = false,
    val transferFailed: Boolean = false,
    val pinRotating: Boolean = false,
    /** L'état de la clé USB quand il demande de l'attention (null = rien à dire). */
    val usbNote: UsbNote? = null,
) {
    /** Une adresse du réseau local est active (Wi-Fi OU Ethernet) : un téléphone peut joindre la TV sans Bluetooth. */
    val lanUp: Boolean get() = lan != LanKind.NONE && !(lan == LanKind.WIFI && wifiNoAddress)
}

/** Ce que la TV sait d'une clé USB qui demande de l'attention (en vérification par Android, illisible, retirée sans éjection) ou qui peut être retirée : voir `castbridge.core.tv.UsbVolumeState.note`. */
data class UsbNote(val level: SignalLevel, val text: String, val action: String?)

data class Indicator(val kind: IndicatorKind, val level: SignalLevel, val text: String, val action: String? = null) {
    /** Mot court pour la rangée de l'accueil (la phrase entière est dans les réglages) : seul Internet noir est raccourci. */
    val short: String get() = if (kind == IndicatorKind.INTERNET && level == SignalLevel.BLACK) "Internet : non connecté" else text
}

/** Résultat unique : la puce de l'accueil ([level], [text], [action]) et la rangée d'indicateurs ([indicators]). Aucun autre code ne choisit une couleur. */
data class TvSignalView(val level: SignalLevel, val text: String, val action: String?, val indicators: List<Indicator>) {
    fun indicator(k: IndicatorKind) = indicators.first { it.kind == k }
}

object TvSignal {
    const val ACTION_NETWORK = "Branchez le câble réseau ou connectez le Wi-Fi : MENU > Connexion & réglages"
    const val ACTION_BLUETOOTH = "Activez le Bluetooth pour recevoir sans réseau"
    const val INTERNET_OFF = "Internet : non connecté, inutile pour CastBridge"
    const val INTERNET_REQUIRED = "Internet requis"
    const val LEGEND = "Vert : ça marche · Orange : ça marche mais attention · Rouge : ne marche pas · Noir : inactif, sans importance"

    /** Seuils de stockage : sous 1 Go libre = orange, sous 100 Mo = plein. -1 = inconnu (considéré bon : on ne crie pas sans preuve). */
    const val LOW_BYTES = 1L shl 30
    const val FULL_BYTES = 100L shl 20
    fun storageOf(freeBytes: Long): StorageState = when { freeBytes < 0 -> StorageState.OK; freeBytes < FULL_BYTES -> StorageState.FULL; freeBytes < LOW_BYTES -> StorageState.LOW; else -> StorageState.OK }

    /** Les tuiles qui ont VRAIMENT besoin d'Internet (Téléchargements, liens web) : orange « Internet requis » s'il manque, sinon aucune alerte. */
    fun internetNeededTile(internet: Boolean): Indicator = Indicator(IndicatorKind.INTERNET,
        if (internet) SignalLevel.GREEN else SignalLevel.ORANGE, if (internet) "Internet : connecté" else INTERNET_REQUIRED)

    fun of(f: TvFacts): TvSignalView {
        val btWorks = f.bluetooth == BtState.ON
        val network = network(f, btWorks)
        val bt = bluetooth(f)
        val internet = if (f.internet) Indicator(IndicatorKind.INTERNET, SignalLevel.GREEN, "Internet : connecté")
        else Indicator(IndicatorKind.INTERNET, SignalLevel.BLACK, INTERNET_OFF)
        val storage = storage(f)

        // Globale = le pire des indicateurs requis (réseau, stockage) + service et verrou ; la première cause rencontrée est dite.
        var level = SignalLevel.GREEN; var text = "Prêt à recevoir"; var action: String? = null
        fun raise(l: SignalLevel, t: String, a: String?) { if (l.severity > level.severity) { level = l; text = t; action = a } }
        if (f.parentalLock) raise(SignalLevel.RED, "Contrôle parental : réception verrouillée", "Déverrouillez avec le code parental : Contrôle parental")
        if (!f.listening) raise(SignalLevel.RED, "Service arrêté : la TV n'écoute pas", "Fermez puis rouvrez CastBridge-TV, ou redémarrez la TV")
        if (f.storage == StorageState.FULL) raise(SignalLevel.RED, "Stockage plein : rien ne peut être reçu", "Libérez de la place ou branchez une clé USB : Bibliothèque")
        if (network.level == SignalLevel.RED) raise(SignalLevel.RED, "Aucun réseau : la TV ne peut rien recevoir", ACTION_NETWORK)
        if (!f.lanUp && btWorks) raise(SignalLevel.ORANGE, "Bluetooth seulement", "Le téléphone doit rester près de la TV. Pour plus de confort : $ACTION_NETWORK")
        if (f.lanUp && f.weakSignal) raise(SignalLevel.ORANGE, "Signal Wi-Fi faible", "Rapprochez la TV du routeur ou branchez le câble réseau")
        if (f.storage == StorageState.LOW) raise(SignalLevel.ORANGE, "Stockage presque plein", "Libérez de la place ou branchez une clé USB : Bibliothèque")
        f.usbNote?.let { raise(it.level, it.text, it.action) }                       // une clé qu'Android vérifie, qu'il ne lit pas, ou retirée sans éjection (vert : « prête à retirer », ne lève rien)
        if (f.transferFailed) raise(SignalLevel.ORANGE, "Un transfert a échoué et attend", "Il reprendra tout seul ; sinon renvoyez-le depuis le téléphone")
        if (f.usbSlow) raise(SignalLevel.ORANGE, "Clé USB lente", "Branchez la clé sur un port USB 3 : MENU > Clé USB")
        if (f.pinRotating) raise(SignalLevel.ORANGE, "Le code de la TV va changer", "Regardez le nouveau code : MENU > Connexion & réglages")

        val reception = Indicator(IndicatorKind.RECEPTION, level, text, action)
        return TvSignalView(level, text, action, listOf(reception, network, bt, internet, storage))
    }

    private fun network(f: TvFacts, btWorks: Boolean): Indicator = when {
        f.lan == LanKind.ETHERNET -> Indicator(IndicatorKind.NETWORK, SignalLevel.GREEN, "Réseau : câble Ethernet")
        f.lan == LanKind.WIFI && !f.wifiNoAddress -> Indicator(IndicatorKind.NETWORK, if (f.weakSignal) SignalLevel.ORANGE else SignalLevel.GREEN,
            "Wi-Fi : " + (f.wifiName?.takeIf { it.isNotBlank() } ?: "connecté") + if (f.weakSignal) " (signal faible)" else "",
            if (f.weakSignal) "Rapprochez la TV du routeur ou branchez le câble réseau" else null)
        f.lan == LanKind.WIFI -> Indicator(IndicatorKind.NETWORK, if (btWorks) SignalLevel.ORANGE else SignalLevel.RED, "Wi-Fi : en attente d'une adresse",
            "Patientez quelques secondes ; sinon : MENU > Connexion & réglages")
        else -> Indicator(IndicatorKind.NETWORK, if (btWorks) SignalLevel.ORANGE else SignalLevel.RED, "Réseau : aucun", ACTION_NETWORK)
    }

    private fun bluetooth(f: TvFacts): Indicator = when (f.bluetooth) {
        BtState.OFF -> Indicator(IndicatorKind.BLUETOOTH, SignalLevel.BLACK, "Bluetooth : désactivé", ACTION_BLUETOOTH)
        BtState.UNUSABLE -> Indicator(IndicatorKind.BLUETOOTH, SignalLevel.ORANGE, "Bluetooth : inutilisable", "Autorisez le Bluetooth pour CastBridge-TV dans les réglages de la TV")
        BtState.ON -> {
            val n = f.pairedPhones
            val who = if (n <= 0) "aucun téléphone" else if (n == 1) "1 téléphone" else "$n téléphones"
            if (n <= 0 && !f.lanUp) Indicator(IndicatorKind.BLUETOOTH, SignalLevel.ORANGE, "Bluetooth : $who", "Ajoutez un téléphone : tuile « Ajouter un téléphone »")
            else Indicator(IndicatorKind.BLUETOOTH, SignalLevel.GREEN, "Bluetooth : $who")
        }
    }

    private fun storage(f: TvFacts): Indicator {
        val free = f.freeText?.let { " · $it libres" }.orEmpty()
        val key = if (f.usbKey) " · clé branchée" else ""
        return when {
            f.storage == StorageState.FULL -> Indicator(IndicatorKind.STORAGE, SignalLevel.RED, "Stockage : plein$free$key", "Libérez de la place ou branchez une clé USB")
            f.storage == StorageState.LOW -> Indicator(IndicatorKind.STORAGE, SignalLevel.ORANGE, "Stockage : presque plein$free$key", "Libérez de la place ou branchez une clé USB")
            f.usbSlow -> Indicator(IndicatorKind.STORAGE, SignalLevel.ORANGE, "Stockage : clé lente$free", "Branchez la clé sur un port USB 3")
            f.usbNote != null -> Indicator(IndicatorKind.STORAGE, f.usbNote.level, "Stockage : ${f.usbNote.text}$free", f.usbNote.action)
            else -> Indicator(IndicatorKind.STORAGE, SignalLevel.GREEN, "Stockage$free$key")
        }
    }

    /** Le téléphone parle la même langue : « TV injoignable » rouge, « Bluetooth seulement » orange, connecté vert, pas de TV noir. */
    fun phoneLevel(state: castbridge.core.trust.LinkState): SignalLevel = when (state) {
        castbridge.core.trust.LinkState.NoTv -> SignalLevel.BLACK
        is castbridge.core.trust.LinkState.Connected -> SignalLevel.GREEN
        is castbridge.core.trust.LinkState.Degraded, castbridge.core.trust.LinkState.Connecting, castbridge.core.trust.LinkState.Bonding,
        is castbridge.core.trust.LinkState.WaitingOwner, castbridge.core.trust.LinkState.CredentialExpired, is castbridge.core.trust.LinkState.Reconnecting -> SignalLevel.ORANGE
        else -> SignalLevel.RED
    }
}
