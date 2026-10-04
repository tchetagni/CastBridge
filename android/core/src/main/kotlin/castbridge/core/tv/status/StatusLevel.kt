package castbridge.core.tv.status

import castbridge.core.brand.BrandTokens
import castbridge.core.status.IconKind
import castbridge.core.status.IconState
import castbridge.core.status.StatusIcon
import castbridge.core.status.Tech

/**
 * Niveau « signalétique » d'une pastille d'état (demande du propriétaire du 2026-10-04) : noir, vert, orange, rouge, plus bleu (en cours) et gris (pas encore mesuré).
 * La couleur n'est JAMAIS le seul signal : chaque pastille garde son icône, son libellé français et un mot d'état court ([Verdict.word]).
 */
enum class StatusLevel(val wire: String, val colour: String, val meaning: String) {
    OFF("off", "Noir", "désactivé volontairement ou absent"),
    OK("ok", "Vert", "fonctionne"),
    WARN("warn", "Orange", "dégradé ou à surveiller"),
    ERROR("error", "Rouge", "panne ou état critique"),
    BUSY("busy", "Bleu", "en cours ou transitoire (connexion, copie)"),
    UNKNOWN("unknown", "Gris", "pas encore mesuré");

    companion object { fun fromWire(w: String) = values().firstOrNull { it.wire == w } }
}

/** Le niveau et le mot d'état court (« connecté », « 12 % libre », « lent »). */
data class Verdict(val level: StatusLevel, val word: String) {
    /** Texte complet de la pastille : « Wi-Fi · connecté ». */
    fun text(label: String) = if (word.isEmpty() || label.endsWith(word)) label else "$label · $word"
}

/** Tous les seuils au même endroit. */
object StatusThresholds {
    /** Stockage : au moins 20 % libre = vert ; de 5 % (inclus) à 20 % = orange ; moins de 5 % = rouge. */
    const val STORAGE_OK_FREE_PERCENT = 20
    const val STORAGE_WARN_FREE_PERCENT = 5
    /** Wi-Fi : signal à -70 dBm ou plus faible = faible. */
    const val WIFI_WEAK_DBM = -70
    /** Liaison lente (Internet, passerelle, Quiz, téléphone) : latence mesurée de 1,5 s ou plus. */
    const val SLOW_LATENCY_MS = 1500L
    /** Licence : orange pendant les 7 derniers jours. */
    const val LICENCE_WARN_DAYS = 7
    /** Téléphones synchronisés : 8 au plus par TV ; complet = orange. */
    const val PHONES_MAX = 8
    /** Jetons : orange à 5 restants ou moins. */
    const val TOKENS_LOW = 5
}

enum class DirectPhase { IDLE, STARTING, GROUP_ACTIVE, FAILED }
enum class BtPhase { IDLE, CONNECTING, PHONE_LINKED, GATEWAY_ONLY, ADAPTER_ERROR, PHONE_REFUSED }
enum class InternetPath { UNTESTED, CHECKING, DIRECT, VIA_PHONE, NONE }
enum class LicenceState { NOT_REQUIRED, VALID, TRIAL, PENDING_NOTIFICATION, EXPIRED, INVALID }
enum class QuizLink { IDLE, CONNECTING, CONNECTED, NOT_ACTIVATED, SERVER_UNREACHABLE }

/** Les règles : l'état MESURÉ de chaque indicateur donne un niveau. Fonctions pures, sans Android. */
object StatusRules {
    private fun v(l: StatusLevel, w: String) = Verdict(l, w)
    private fun slow(ms: Long?) = ms != null && ms >= StatusThresholds.SLOW_LATENCY_MS

    /** Désactivé = noir ; en connexion = bleu ; activé mais non connecté = rouge ; connecté : sans Internet ou signal faible = orange, sinon vert. */
    fun wifi(enabled: Boolean, connected: Boolean, connecting: Boolean, rssiDbm: Int?, hasInternet: Boolean?): Verdict = when {
        !enabled -> v(StatusLevel.OFF, "désactivé")
        connecting -> v(StatusLevel.BUSY, "connexion…")
        !connected -> v(StatusLevel.ERROR, "non connecté")
        hasInternet == false -> v(StatusLevel.WARN, "sans Internet")
        rssiDbm != null && rssiDbm <= StatusThresholds.WIFI_WEAK_DBM -> v(StatusLevel.WARN, "signal faible")
        else -> v(StatusLevel.OK, "connecté")
    }

    fun wifiDirect(enabled: Boolean, phase: DirectPhase): Verdict = when {
        !enabled -> v(StatusLevel.OFF, "désactivé")
        phase == DirectPhase.FAILED -> v(StatusLevel.ERROR, "échec")
        phase == DirectPhase.STARTING -> v(StatusLevel.BUSY, "en cours…")
        phase == DirectPhase.GROUP_ACTIVE -> v(StatusLevel.OK, "groupe actif")
        else -> v(StatusLevel.OFF, "inactif")
    }

    fun bluetooth(enabled: Boolean, phase: BtPhase, slowNetwork: Boolean): Verdict = when {
        !enabled -> v(StatusLevel.OFF, "désactivé")
        phase == BtPhase.ADAPTER_ERROR -> v(StatusLevel.ERROR, "erreur adaptateur")
        phase == BtPhase.PHONE_REFUSED -> v(StatusLevel.ERROR, "téléphone refusé")
        phase == BtPhase.CONNECTING -> v(StatusLevel.BUSY, "connexion…")
        phase == BtPhase.GATEWAY_ONLY && slowNetwork -> v(StatusLevel.WARN, "passerelle lente")
        phase == BtPhase.GATEWAY_ONLY -> v(StatusLevel.OK, "passerelle")
        phase == BtPhase.PHONE_LINKED -> v(StatusLevel.OK, "téléphone relié")
        else -> v(StatusLevel.OK, "prêt")
    }

    fun internet(path: InternetPath, expected: Boolean = true, latencyMs: Long? = null): Verdict = when (path) {
        InternetPath.UNTESTED -> v(StatusLevel.UNKNOWN, "non testé")
        InternetPath.CHECKING -> v(StatusLevel.BUSY, "vérification…")
        InternetPath.VIA_PHONE -> v(StatusLevel.WARN, "lent")
        InternetPath.DIRECT -> if (slow(latencyMs)) v(StatusLevel.WARN, "lent") else v(StatusLevel.OK, "connecté")
        InternetPath.NONE -> if (expected) v(StatusLevel.ERROR, "aucun accès") else v(StatusLevel.OFF, "hors ligne")
    }

    /** Au moins 20 % libre = vert ; 5 % (inclus) à 20 % = orange ; moins de 5 %, lecture seule ou erreur = rouge ; absent = noir. Calcul exact, sans arrondi. */
    fun storage(present: Boolean, freeBytes: Long, totalBytes: Long, readOnly: Boolean = false, error: Boolean = false): Verdict = when {
        !present -> v(StatusLevel.OFF, "absent")
        error -> v(StatusLevel.ERROR, "erreur")
        readOnly -> v(StatusLevel.ERROR, "lecture seule")
        totalBytes <= 0 -> v(StatusLevel.UNKNOWN, "non mesuré")
        else -> {
            val free = freeBytes.coerceIn(0, totalBytes)
            val word = "${free * 100 / totalBytes} % libre"
            when {
                free * 100 >= totalBytes * StatusThresholds.STORAGE_OK_FREE_PERCENT -> v(StatusLevel.OK, word)
                free * 100 >= totalBytes * StatusThresholds.STORAGE_WARN_FREE_PERCENT -> v(StatusLevel.WARN, word)
                else -> v(StatusLevel.ERROR, word)
            }
        }
    }

    fun phones(count: Int): Verdict = when {
        count <= 0 -> v(StatusLevel.OFF, "aucun")
        count >= StatusThresholds.PHONES_MAX -> v(StatusLevel.WARN, "$count/${StatusThresholds.PHONES_MAX} complet")
        else -> v(StatusLevel.OK, "$count/${StatusThresholds.PHONES_MAX}")
    }

    fun copy(running: Boolean, percent: Int?, slowed: Boolean, failed: Boolean): Verdict = when {
        failed -> v(StatusLevel.ERROR, "échec")
        !running -> v(StatusLevel.OFF, "aucune")
        slowed -> v(StatusLevel.WARN, "ralentie")
        else -> v(StatusLevel.BUSY, percent?.let { "$it %" } ?: "en cours")
    }

    /** Valide = vert (orange pendant les 7 derniers jours) ; essai = orange (édition limitée, à surveiller) ; en attente de notification = orange ; expirée ou invalide = rouge. */
    fun licence(state: LicenceState, daysLeft: Int?): Verdict = when (state) {
        LicenceState.NOT_REQUIRED -> v(StatusLevel.OFF, "non requise")
        LicenceState.INVALID -> v(StatusLevel.ERROR, "invalide")
        LicenceState.EXPIRED -> v(StatusLevel.ERROR, "expirée")
        LicenceState.PENDING_NOTIFICATION -> v(StatusLevel.WARN, "en attente")
        LicenceState.TRIAL -> if (daysLeft != null && daysLeft <= 0) v(StatusLevel.ERROR, "essai terminé") else v(StatusLevel.WARN, "essai")
        LicenceState.VALID -> if (daysLeft != null && daysLeft <= StatusThresholds.LICENCE_WARN_DAYS) v(StatusLevel.WARN, "expire dans $daysLeft j") else v(StatusLevel.OK, "valide")
    }

    fun quiz(link: QuizLink, latencyMs: Long? = null): Verdict = when (link) {
        QuizLink.IDLE -> v(StatusLevel.OFF, "hors partie")
        QuizLink.CONNECTING -> v(StatusLevel.BUSY, "connexion…")
        QuizLink.NOT_ACTIVATED -> v(StatusLevel.ERROR, "TV non activée")
        QuizLink.SERVER_UNREACHABLE -> v(StatusLevel.ERROR, "serveur injoignable")
        QuizLink.CONNECTED -> if (slow(latencyMs)) v(StatusLevel.WARN, "liaison lente") else v(StatusLevel.OK, "en ligne")
    }

    fun tokens(serverReachable: Boolean?, balance: Int?): Verdict = when {
        serverReachable == null -> v(StatusLevel.UNKNOWN, "non testé")
        !serverReachable -> v(StatusLevel.WARN, "serveur hors ligne")
        balance == null -> v(StatusLevel.UNKNOWN, "solde inconnu")
        balance <= 0 -> v(StatusLevel.ERROR, "plus de jeton")
        balance <= StatusThresholds.TOKENS_LOW -> v(StatusLevel.WARN, "$balance restants")
        else -> v(StatusLevel.OK, "$balance jetons")
    }

    /** Liaison du téléphone vers la TV (pastille de la télécommande du téléphone). */
    fun phoneLink(connected: Boolean, connecting: Boolean, offline: Boolean, rttMs: Long?): Verdict = when {
        connecting -> v(StatusLevel.BUSY, "connexion…")
        offline -> v(StatusLevel.ERROR, "hors ligne")
        connected -> if (slow(rttMs)) v(StatusLevel.WARN, "lent") else v(StatusLevel.OK, "connecté")
        else -> v(StatusLevel.ERROR, "code ?")
    }

    /** Les pastilles de la barre d'état de la TV, une par [IconKind] : l'état de la liaison d'abord, puis la règle propre au type. */
    fun icon(i: StatusIcon): Verdict = when (i.state) {
        IconState.CONNECTING -> v(StatusLevel.BUSY, "connexion…")
        IconState.DEGRADED -> v(StatusLevel.WARN, "reconnexion")
        IconState.ERROR -> v(StatusLevel.ERROR, if (i.kind == IconKind.INTERNET) "aucun accès" else "erreur")
        IconState.CONNECTED -> when (i.kind) {
            IconKind.INTERNET -> when {
                i.tech == Tech.BLUETOOTH -> v(StatusLevel.WARN, "lent")
                slow(i.latencyMs) -> v(StatusLevel.WARN, "lent")
                else -> v(StatusLevel.OK, "connecté")
            }
            IconKind.GATEWAY -> if (slow(i.latencyMs)) v(StatusLevel.WARN, "lent") else v(StatusLevel.OK, "actif")
            IconKind.PHONE -> v(StatusLevel.OK, "relié")
            IconKind.REMOTE_CONTROL -> v(StatusLevel.OK, "actif")
            IconKind.SSH -> v(StatusLevel.WARN, "ouvert")
            IconKind.CAST -> v(StatusLevel.BUSY, "en cours")
            IconKind.USB_DRIVE -> v(StatusLevel.OK, "branchée")
            IconKind.WIFI_DIRECT_GROUP -> v(StatusLevel.OK, "groupe actif")
            IconKind.QUIZ_PLAYER, IconKind.CHESS_PLAYER -> v(StatusLevel.OK, "connecté")
            IconKind.DOWNLOAD -> v(StatusLevel.BUSY, "en cours")
            IconKind.PARENTAL_MODE -> v(StatusLevel.OK, "actif")
            IconKind.UPDATE -> v(StatusLevel.WARN, "disponible")
        }
    }

    /** Texte complet d'une pastille de la barre : le texte existant sans son ancien suffixe d'état, puis le mot d'état du niveau. */
    fun chipText(i: StatusIcon): String = icon(i).text(i.text().removeSuffix(" · ${i.state.label}"))
}

/** Couleurs ARGB (thème sombre de la TV) de chaque niveau, depuis les tokens de la charte. Le noir reste visible : remplissage noir, anneau clair, icône atténuée. */
object StatusPalette {
    /** Anneau autour de l'icône : la couleur du niveau ; pour le noir, un anneau CLAIR (3:1 au moins sur le fond sombre et sur le noir). */
    fun ring(l: StatusLevel): Int = when (l) {
        StatusLevel.OFF -> BrandTokens.Semantic.OFF_RING_DARK
        StatusLevel.OK -> BrandTokens.Semantic.SUCCESS_DARK
        StatusLevel.WARN -> BrandTokens.Semantic.WARNING_DARK
        StatusLevel.ERROR -> BrandTokens.Semantic.ERROR_DARK
        StatusLevel.BUSY -> BrandTokens.Semantic.INFO_DARK
        StatusLevel.UNKNOWN -> BrandTokens.Semantic.UNKNOWN_DARK
    }
    /** Fond du disque : noir pur pour OFF, fond de la TV pour les autres. */
    fun fill(l: StatusLevel): Int = if (l == StatusLevel.OFF) BrandTokens.Semantic.OFF_DARK else BrandTokens.Dark.BACKGROUND
    /** Petit point d'état : la couleur du niveau ; noir (avec contour clair) pour OFF. */
    fun dot(l: StatusLevel): Int = if (l == StatusLevel.OFF) BrandTokens.Semantic.OFF_DARK else ring(l)
    /** Opacité de l'icône : atténuée pour OFF et UNKNOWN, jamais invisible. */
    fun glyphAlpha(l: StatusLevel): Float = when (l) { StatusLevel.OFF -> 0.6f; StatusLevel.UNKNOWN -> 0.75f; else -> 1f }
    /** Thème clair (téléphone). */
    fun dotLight(l: StatusLevel): Int = when (l) {
        StatusLevel.OFF -> BrandTokens.Semantic.OFF_LIGHT
        StatusLevel.OK -> BrandTokens.Semantic.SUCCESS_LIGHT
        StatusLevel.WARN -> BrandTokens.Semantic.WARNING_LIGHT
        StatusLevel.ERROR -> BrandTokens.Semantic.ERROR_LIGHT
        StatusLevel.BUSY -> BrandTokens.Semantic.INFO_LIGHT
        StatusLevel.UNKNOWN -> BrandTokens.Semantic.UNKNOWN_LIGHT
    }
}
