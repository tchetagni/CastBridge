package castbridge.core.dl

/** What a download is doing, in words for people (the phone, the TV screen and the web page show [label]). */
enum class DlState(val label: String, val canPause: Boolean, val canResume: Boolean) {
    CONNECTING("Connexion…", true, false),
    METADATA("Recherche des informations du torrent…", true, false),
    CHECKING("Vérification de la place…", false, false),
    DOWNLOADING("En cours", true, false),
    QUEUED("En attente de son tour", true, false),
    PAUSED("En pause", false, true),
    WAITING_SPACE("En pause : pas assez de place", false, true),
    WAITING_DRIVE("En pause : clé USB retirée", false, false),
    SEEDING("Terminé, partage en cours", true, false),
    MOVING("Rangement dans la bibliothèque…", false, false),
    DONE("Terminé", false, false),
    ERROR("Échec", false, true),
}

/** Why the TV (not the user) paused a download. */
enum class PausedBy { USER, SPACE, DRIVE, CHECK }

/** Maps aria2's status of one download (tellStatus fields) to a [DlState]. Pure, unit-tested. */
object StateMapper {
    fun map(st: Map<String, Any?>, pausedBy: PausedBy?): DlState {
        val status = st.s("status")
        val metadata = isMetadata(st)
        return when (status) {
            "active" -> when {
                st.b("seeder") && st.n("completedLength") >= st.n("totalLength") && st.n("totalLength") > 0 -> DlState.SEEDING
                metadata -> DlState.METADATA
                st.n("downloadSpeed") == 0L && st.n("completedLength") == 0L -> DlState.CONNECTING
                else -> DlState.DOWNLOADING
            }
            "waiting" -> DlState.QUEUED
            "paused" -> when (pausedBy) {
                PausedBy.SPACE -> DlState.WAITING_SPACE
                PausedBy.DRIVE -> DlState.WAITING_DRIVE
                PausedBy.CHECK -> DlState.CHECKING
                else -> DlState.PAUSED
            }
            "complete" -> DlState.DONE
            "error" -> if (pausedBy == PausedBy.DRIVE) DlState.WAITING_DRIVE else DlState.ERROR
            "removed" -> DlState.ERROR
            else -> DlState.QUEUED
        }
    }

    /** A magnet's first phase: aria2 names its only file "[METADATA]<hash>". */
    fun isMetadata(st: Map<String, Any?>): Boolean =
        st["files"].list().firstOrNull()?.obj()?.s("path")?.startsWith("[METADATA]") == true ||
            (st["bittorrent"] != null && st["bittorrent"].obj()["info"] == null && st.n("totalLength") == 0L && st.s("infoHash") != null)

    /** Seconds left at the current speed, -1 if unknown. */
    fun eta(total: Long, done: Long, speed: Long): Long = if (speed <= 0 || total <= 0 || done >= total) -1 else (total - done + speed - 1) / speed

    /** aria2 exit/error codes (manual, "EXIT STATUS") in plain words. */
    fun error(code: Long, message: String?): String = when (code.toInt()) {
        2 -> "Le serveur ne répond pas (délai dépassé)."
        3 -> "Fichier introuvable sur le serveur (lien mort ?)."
        4 -> "Fichier introuvable sur le serveur (plusieurs essais)."
        5 -> "Téléchargement trop lent, abandonné."
        6 -> "Problème de réseau."
        7 -> "Interrompu avant la fin."
        8 -> "Le serveur ne permet pas de reprendre ce téléchargement."
        9 -> "Plus assez de place sur le stockage."
        11, 12 -> "Ce téléchargement est déjà en cours."
        13 -> "Un fichier du même nom existe déjà."
        14, 15, 16, 17, 18 -> "Erreur d'écriture sur le stockage (clé retirée ou pleine ?)."
        19 -> "Adresse du serveur introuvable (pas d'Internet ou lien erroné)."
        20 -> "Fichier Metalink illisible."
        21 -> "Le serveur FTP a refusé la demande."
        22 -> "Réponse du serveur incorrecte."
        23 -> "Trop de redirections."
        24 -> "Identifiant ou mot de passe refusé par le serveur."
        25, 26 -> "Fichier torrent illisible ou abîmé."
        27 -> "Lien magnet incorrect."
        28 -> "Option refusée."
        29 -> "Serveur surchargé, réessayez plus tard."
        32 -> "Le fichier reçu est abîmé (somme de contrôle incorrecte)."
        else -> message?.takeIf { it.isNotBlank() }?.let { "Erreur : ${it.take(160)}" } ?: "Erreur inconnue."
    }
}
