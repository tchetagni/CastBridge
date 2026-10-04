package castbridge.core.tv.status

import castbridge.core.status.StatusIcon

/** Une pastille prête à dessiner : niveau, libellé français et mot d'état (la couleur n'est jamais seule). */
data class Badge(val id: String, val level: StatusLevel, val label: String, val stateWord: String) {
    val text: String get() = Verdict(level, stateWord).text(label)
    /** Orange et rouge : toujours étiquetées et JAMAIS masquées derrière « + n ». */
    val urgent: Boolean get() = level == StatusLevel.WARN || level == StatusLevel.ERROR
}

enum class TokensSync { NEVER, SYNCED, OFFLINE }

/**
 * Les mesures BRUTES lues sur la TV, sans interprétation. Chaque valeur par défaut veut dire « pas encore mesuré » : un instantané vide ne montre
 * donc AUCUNE pastille verte (gris). [badges] applique les règles de [StatusRules] et fixe l'ordre d'affichage.
 */
data class StatusSnapshot(
    val wifiEnabled: Boolean? = null, val wifiConnected: Boolean = false, val wifiConnecting: Boolean = false,
    val wifiRssiDbm: Int? = null, val wifiHasInternet: Boolean? = null,
    val internet: InternetPath = InternetPath.UNTESTED, val internetExpected: Boolean = true, val internetLatencyMs: Long? = null,
    val wifiDirectEnabled: Boolean = false, val wifiDirect: DirectPhase = DirectPhase.IDLE,
    val btEnabled: Boolean? = null, val bt: BtPhase = BtPhase.IDLE, val btSlowNetwork: Boolean = false,
    val storagePresent: Boolean? = null, val storageFreeBytes: Long = 0, val storageTotalBytes: Long = 0,
    val storageReadOnly: Boolean = false, val storageError: Boolean = false,
    val phones: Int? = null,
    val copyRunning: Boolean = false, val copyPercent: Int? = null, val copySlowed: Boolean = false, val copyFailed: Boolean = false,
    val licence: LicenceState? = null, val licenceTiming: LicenceTiming? = null, val nowMs: Long = 0,
    val quiz: QuizLink = QuizLink.IDLE, val quizLatencyMs: Long? = null,
    val tokens: TokensSync = TokensSync.NEVER, val tokenBalance: Int? = null,
) {
    private fun unmeasured(id: String, label: String, word: String = "non mesuré") = Badge(id, StatusLevel.UNKNOWN, label, word)
    private fun of(id: String, label: String, v: Verdict) = Badge(id, v.level, label, v.word)

    /** Les pastilles dans l'ordre d'affichage : Internet, Wi-Fi, Bluetooth, Stockage, Wi-Fi Direct, Téléphones, Copie, Licence, Quiz, Jetons. Celles qui n'ont pas de sens (copie à l'arrêt, quiz hors partie, Wi-Fi Direct éteint) sont absentes. */
    fun badges(): List<Badge> {
        val out = ArrayList<Badge>()
        out += of("internet", "Internet", StatusRules.internet(internet, internetExpected, internetLatencyMs))
        out += if (wifiEnabled == null) unmeasured("wifi", "Wi-Fi") else of("wifi", "Wi-Fi", StatusRules.wifi(wifiEnabled, wifiConnected, wifiConnecting, wifiRssiDbm, wifiHasInternet))
        out += if (btEnabled == null) unmeasured("bluetooth", "Bluetooth") else of("bluetooth", "Bluetooth", StatusRules.bluetooth(btEnabled, bt, btSlowNetwork))
        out += if (storagePresent == null) unmeasured("storage", "Stockage") else of("storage", "Stockage", StatusRules.storage(storagePresent, storageFreeBytes, storageTotalBytes, storageReadOnly, storageError))
        if (wifiDirectEnabled || wifiDirect != DirectPhase.IDLE) out += of("wifi_direct", "Wi-Fi Direct", StatusRules.wifiDirect(wifiDirectEnabled, wifiDirect))
        out += if (phones == null) unmeasured("phones", "Téléphones") else of("phones", "Téléphones", StatusRules.phones(phones))
        if (copyRunning || copyFailed) out += of("copy", "Copie", StatusRules.copy(copyRunning, copyPercent, copySlowed, copyFailed))
        if (licence != LicenceState.NOT_REQUIRED) out += if (licence == null) unmeasured("licence", "Licence", "non vérifiée") else of("licence", "Licence", StatusRules.licence(licence, licenceTiming, nowMs))
        if (quiz != QuizLink.IDLE) out += of("quiz", "Quiz en ligne", StatusRules.quiz(quiz, quizLatencyMs))
        out += when (tokens) {
            TokensSync.NEVER -> unmeasured("tokens", "Jetons", "jamais synchronisé")
            TokensSync.SYNCED -> of("tokens", "Jetons", StatusRules.tokens(true, tokenBalance))
            TokensSync.OFFLINE -> of("tokens", "Jetons", StatusRules.tokens(false, tokenBalance))
        }
        return out
    }
}

/** Ce qui tient dans la barre et ce qui passe derrière « + n ». */
data class BadgeSplit(val visible: List<Badge>, val hidden: List<Badge>)

object StatusBadges {
    /** Nombre de pastilles lisibles en même temps sur une dalle 720p (libellés de 28 sp pendant la lecture). */
    const val MAX_VISIBLE_PLAYER = 6
    const val MAX_VISIBLE_HOME = 8
    /** Un instantané plus vieux que cela n'est plus cru : tout repasse en gris « périmé » (le relevé tourne toutes les 5 s). */
    const val STALE_MS = 30_000L

    /** Une pastille de la barre de connexions (téléphone, SSH, clé USB...) sous forme de [Badge]. */
    fun fromIcon(i: StatusIcon): Badge { val v = StatusRules.icon(i); return Badge(i.id, v.level, i.text().removeSuffix(" · ${i.state.label}"), v.word) }

    /** Orange et rouge ne sont JAMAIS masquées, même au-delà de [max] ; les autres se remplissent dans l'ordre d'affichage. L'ordre est conservé. */
    fun split(all: List<Badge>, max: Int): BadgeSplit {
        val keep = HashSet<String>()
        for (b in all) if (b.urgent) keep += b.id
        for (b in all) { if (keep.size >= max) break; keep += b.id }
        return BadgeSplit(all.filter { it.id in keep }, all.filter { it.id !in keep })
    }

    /** Les badges de l'instantané, puis ceux de la barre de connexions dont l'identité n'est pas déjà couverte (Internet, Wi-Fi Direct, téléchargement/copie). */
    fun merge(snapshot: List<Badge>, icons: List<StatusIcon>): List<Badge> {
        val covered = setOf(castbridge.core.status.IconKind.INTERNET, castbridge.core.status.IconKind.WIFI_DIRECT_GROUP)
        return snapshot + icons.filter { it.kind !in covered }.map { fromIcon(it) }
    }
}

/** Tient le dernier instantané et sa date : sans relevé récent, rien n'est vert (gris « périmé »). Thread-safe. */
class StatusBoard(private val clock: () -> Long) {
    private var snap: StatusSnapshot? = null
    private var at = 0L
    private var badges: List<Badge> = emptyList()

    @Synchronized fun update(s: StatusSnapshot) { snap = s; at = clock(); badges = s.badges() }

    /** Les pastilles à dessiner maintenant. Jamais relevé : toutes grises ; relevé trop vieux : grises « périmé ». */
    @Synchronized fun current(): List<Badge> {
        val s = snap ?: return StatusSnapshot().badges()
        if (clock() - at > StatusBadges.STALE_MS) return badges.map { Badge(it.id, StatusLevel.UNKNOWN, it.label, "périmé") }
        return badges
    }
}
