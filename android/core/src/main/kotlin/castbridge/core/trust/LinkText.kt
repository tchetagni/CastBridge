package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.TvClient
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** The ONE thing the screen proposes in a given situation (a single button). */
enum class LinkAction(val label: String) {
    NONE(""), RETRY("Réessayer"), ADD_TV("Ajouter ma TV"), REASSOCIATE("Réassocier"), ENABLE_BLUETOOTH("Activer le Bluetooth"),
    GRANT_PERMISSION("Autoriser"), PAIR("Associer la TV"), REMOVE_BOND("Ouvrir les réglages Bluetooth"), ENTER_CODE("Saisir le code de la TV"),
}

/** A stable, user-facing explanation: what happens, and the single recommended [action]. Never a stack trace, never an exception name. */
data class Advice(val title: String, val detail: String, val action: LinkAction = LinkAction.NONE)

/** Why the TV did not answer, as far as Android's error text lets us tell (the text is only used here, never shown). */
enum class AbsentKind {
    /** Nobody answered within the page timeout, or the network says unreachable: TV off, out of range, Bluetooth off there. */
    NO_ANSWER,
    /** The TV answered at once but offers no CastBridge service (SDP query failed): the TV app is not running. */
    SERVICE_ABSENT,
    /** The link was refused almost instantly after being accepted: the typical sign of a bond one side no longer knows. */
    CLOSED_AT_ONCE;

    companion object {
        /** [elapsedMs]: how long the connect attempt took (a TV that is simply off makes Android wait ~10 s, a refused bond fails in about a second). */
        fun classify(message: String?, elapsedMs: Long): AbsentKind {
            val m = message.orEmpty().lowercase()
            return when {
                "service discovery" in m || "sdp" in m -> SERVICE_ABSENT
                ("read failed" in m || "socket might closed" in m || "socket closed" in m || "read ret" in m || "reset by peer" in m) && elapsedMs in 1..QUICK_MS -> CLOSED_AT_ONCE
                else -> NO_ANSWER
            }
        }
        const val QUICK_MS = 3_000L
    }
}

/** French text of every outcome of the link and of the pairing flow. Table-tested: no outcome is left without a message. */
object LinkText {
    /** The ERR_* codes that have their own wording (the others get [generic]). */
    val explicitCodes: Set<Int> = setOf(BtProtocol.ERR_MAGIC, BtProtocol.ERR_PIN, BtProtocol.ERR_NAME, BtProtocol.ERR_SPACE, BtProtocol.ERR_LOCKED, BtProtocol.ERR_IO,
        BtProtocol.ERR_SIZE, BtProtocol.ERR_UNTRUSTED, BtProtocol.ERR_DENIED, BtProtocol.ERR_TIMEOUT, BtProtocol.ERR_NOT_OPEN, BtProtocol.ERR_BUSY,
        BtProtocol.ERR_FULL, BtProtocol.ERR_FULL_CANCELED, BtProtocol.ERR_FULL_TIMEOUT)

    fun untrusted(hint: Int) = untrustedAdvice(hint).detail

    fun untrustedAdvice(hint: Int) = when (hint) {
        BtProtocol.HINT_OTHER_INSTALL -> Advice("TV réinitialisée", "La TV a été réinitialisée ou réinstallée : elle ne vous reconnaît plus.", LinkAction.REASSOCIATE)
        BtProtocol.HINT_SAME_INSTALL -> Advice("Cette TV vous a retiré", "Ajoutez-la à nouveau : ce téléphone a été retiré de la liste des téléphones de la TV.", LinkAction.REASSOCIATE)
        else -> Advice("La TV ne vous reconnaît plus", "La TV a été réinitialisée ou réinstallée, ou ce téléphone a été retiré de sa liste.", LinkAction.REASSOCIATE)
    }

    fun refused(code: Int, hint: Int = BtProtocol.HINT_NONE): Advice = when (code) {
        BtProtocol.ERR_UNTRUSTED -> untrustedAdvice(hint)
        BtProtocol.ERR_DENIED -> Advice("Refusé sur la TV", "Le propriétaire a refusé ce téléphone. Après trois refus, la TV l'ignore pendant 10 minutes.", LinkAction.REASSOCIATE)
        BtProtocol.ERR_TIMEOUT -> Advice("Personne n'a répondu sur la TV", "Sur la TV, choisissez « Autoriser » avec la télécommande dans la minute qui suit.", LinkAction.RETRY)
        BtProtocol.ERR_NOT_OPEN -> Advice("« Ajouter un téléphone » est fermé", "Sur la TV, ouvrez CastBridge-TV puis « Ajouter un téléphone ».", LinkAction.RETRY)
        BtProtocol.ERR_BUSY -> Advice("La TV est occupée", "La TV traite déjà une demande, ou reçoit trop d'essais. Patientez quelques secondes.", LinkAction.RETRY)
        BtProtocol.ERR_FULL -> Advice("Cette TV a déjà ${TrustRegistry.MAX_PHONES} téléphones", "Sur la TV, choisissez le téléphone à retirer, puis réessayez.", LinkAction.RETRY)
        BtProtocol.ERR_FULL_CANCELED -> Advice("Ajout annulé sur la TV", "Le propriétaire a annulé : la TV garde ses ${TrustRegistry.MAX_PHONES} téléphones. Sur la TV, retirez-en un, puis réessayez.", LinkAction.RETRY)
        BtProtocol.ERR_FULL_TIMEOUT -> Advice("Personne n'a répondu sur la TV", "Aucun téléphone n'a été choisi à retirer dans les 2 minutes. Réessayez quand vous êtes devant la TV.", LinkAction.RETRY)
        BtProtocol.ERR_MAGIC -> Advice("CastBridge-TV à mettre à jour", "Cette TV n'a pas la dernière version de CastBridge-TV : installez la mise à jour sur la TV.", LinkAction.RETRY)
        BtProtocol.ERR_PIN -> Advice("Code incorrect", "Le code de la TV n'est pas le bon. Ce téléphone n'essaiera pas de nouveau tout seul.", LinkAction.ENTER_CODE)
        BtProtocol.ERR_LOCKED -> Advice("TV verrouillée un moment", "Trop d'essais avec un mauvais code : la TV se rouvre dans une minute.", LinkAction.RETRY)
        BtProtocol.ERR_SPACE -> Advice("Plus de place sur la TV", "Libérez de l'espace sur la TV (supprimez des vidéos) puis réessayez.", LinkAction.RETRY)
        BtProtocol.ERR_NAME -> Advice("Nom de fichier refusé", "La TV n'accepte pas ce nom de fichier : renommez-le puis réessayez.", LinkAction.RETRY)
        BtProtocol.ERR_SIZE -> Advice("Fichier vide ou invalide", "La TV a refusé la taille de ce fichier.", LinkAction.RETRY)
        BtProtocol.ERR_IO -> Advice("Erreur d'écriture sur la TV", "La TV n'a pas pu enregistrer le fichier (disque plein ou retiré).", LinkAction.RETRY)
        else -> generic(code)
    }

    /** The TV is full and its owner is choosing (the phone waits, bounded): the live state of the pairing screen. */
    fun fullPending(secondsLeft: Long) = Advice("En attente de la TV…",
        "Cette TV a déjà ${TrustRegistry.MAX_PHONES} téléphones. Sur la TV, choisissez le téléphone à retirer, puis réessayez. Nouvel essai automatique ($secondsLeft s).")

    private fun generic(code: Int) = Advice("La TV a répondu par une erreur", "Réponse inattendue de la TV (code $code). Mettez à jour CastBridge et CastBridge-TV, puis réessayez.", LinkAction.RETRY)

    fun absent(kind: AbsentKind, tv: String): Advice = when (kind) {
        AbsentKind.NO_ANSWER -> Advice("$tv est introuvable", "Allumez la TV et restez à proximité : la connexion est automatique.", LinkAction.RETRY)
        AbsentKind.SERVICE_ABSENT -> Advice("CastBridge-TV n'est pas ouvert", "La TV répond en Bluetooth, mais l'application CastBridge-TV ne tourne pas. Ouvrez-la sur la TV.", LinkAction.RETRY)
        AbsentKind.CLOSED_AT_ONCE -> Advice("$tv ferme la liaison", "La TV coupe la connexion aussitôt. Allumez-la, ouvrez CastBridge-TV, et approchez le téléphone.", LinkAction.RETRY)
    }

    fun bluetooth(reason: BtUnavailable.Reason) = when (reason) {
        BtUnavailable.Reason.OFF -> Advice("Bluetooth éteint", "Activez le Bluetooth du téléphone : la TV se reconnectera toute seule.", LinkAction.ENABLE_BLUETOOTH)
        BtUnavailable.Reason.NO_PERMISSION -> Advice("Autorisation Bluetooth manquante", "Autorisez « Appareils à proximité » pour CastBridge.", LinkAction.GRANT_PERMISSION)
        BtUnavailable.Reason.NO_ADAPTER -> Advice("Pas de Bluetooth", "Ce téléphone n'a pas de Bluetooth : saisissez le code de la TV.", LinkAction.ENTER_CODE)
    }

    val noTv = Advice("Aucune TV ajoutée", "Ajoutez votre TV : aucun code à saisir.", LinkAction.ADD_TV)
    val notBonded = Advice("TV non associée", "Ce téléphone n'est plus associé en Bluetooth à la TV. Associez-la à nouveau.", LinkAction.PAIR)
    val bonding = Advice("Association en cours", "Comparez le code affiché sur le téléphone et sur la TV, puis validez les deux.")
    val staleBond = Advice("Association Bluetooth périmée",
        "Android garde une ancienne association que la TV ne reconnaît plus. Supprimez la TV dans les réglages Bluetooth du téléphone (Oublier / Dissocier), puis revenez ici : la suite est automatique.",
        LinkAction.REMOVE_BOND)
    val credentialExpired = Advice("Accès expiré", "L'autorisation de ce téléphone a expiré ou a été révoquée : renouvellement en cours.", LinkAction.RETRY)

    fun lost(side: LossSide) = when (side) {
        LossSide.TV -> Advice("Liaison perdue, reconnexion…", "La TV ne répond plus (éteinte, trop loin, ou l'application redémarre). Reconnexion automatique.")
        LossSide.PHONE_BLUETOOTH -> Advice("Liaison perdue, reconnexion…", "Le Bluetooth du téléphone a été coupé. Reconnexion dès qu'il revient.")
        LossSide.PHONE_NETWORK -> Advice("Liaison perdue, reconnexion…", "Le Wi-Fi du téléphone a été perdu. Reconnexion dès qu'il revient.")
    }

    fun connected(tv: String, route: RouteKind) = Advice("$tv connectée", when (route) {
        RouteKind.LAN -> "Par le Wi-Fi de la maison"
        RouteKind.DIRECT -> "Par Wi-Fi Direct"
        RouteKind.BLUETOOTH -> "Par Bluetooth (la TV n'a pas de Wi-Fi à partager)"
    })

    fun degraded(tv: String) = Advice("$tv connectée, liaison réduite", "Par Bluetooth seulement, plus lent : le téléphone et la TV ne sont pas sur le même Wi-Fi ? Le Wi-Fi sera repris tout seul.")

    /** Any failure of a call, in one sentence: never the exception's own text. */
    fun failure(t: Throwable): String = when (t) {
        is BtUnavailable -> bluetooth(t.reason).title + " : " + bluetooth(t.reason).detail
        is BtProtocol.Refused -> refused(t.code, t.hint).let { it.title + " : " + it.detail }
        is TvClient.HttpError -> http(t.code, t.message.orEmpty())
        is SocketTimeoutException -> "La TV ne répond pas (délai dépassé)."
        is ConnectException, is NoRouteToHostException -> "Connexion à la TV impossible : est-elle allumée et sur le même réseau ?"
        is UnknownHostException -> "TV introuvable sur le réseau."
        is InterruptedIOException -> "Opération annulée."
        is IOException -> "La liaison avec la TV a été interrompue."
        is SecurityException -> "Une autorisation du téléphone manque (Bluetooth ou réseau)."
        else -> "Erreur inattendue : réessayez."
    }

    fun http(code: Int, body: String): String = when {
        code == 401 && "locked" in body -> "Trop d'essais avec un mauvais code : attendez une minute."
        code == 401 && "bad token" in body -> "L'autorisation de ce téléphone a expiré : reconnexion à la TV en cours."
        code == 401 -> "Code de la TV incorrect."
        code == 403 -> "Cette action demande le code de la TV (un téléphone de confiance ne suffit pas)."
        code == 404 -> "Cette TV ne connaît pas cette fonction : mettez CastBridge-TV à jour."
        code == 409 -> "Conflit avec la TV : réessayez."
        code == 413 || code == 507 -> "Plus de place sur la TV."
        code == 503 -> "La TV est occupée ou démarre : patientez."
        code >= 500 -> "La TV a rencontré une erreur : réessayez."
        else -> "La TV a refusé la demande."
    }
}
