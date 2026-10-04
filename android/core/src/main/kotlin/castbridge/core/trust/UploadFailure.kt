package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.TvClient
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** L'étape où une copie s'est arrêtée. */
enum class CopyStep(val label: String) { SEARCH("recherche de la TV"), CONNECT("connexion"), SEND("envoi"), VERIFY("vérification"), START("démarrage") }

/**
 * Pourquoi une copie vers la TV a échoué : la CAUSE, une phrase française, l'ACTION à faire et la cause technique non sensible en une ligne.
 * Pur et testé (`UploadFailureTest`). Trois portes : une exception ([ofException]), un code HTTP de la TV ([ofHttp]) et le texte d'échec que la file ou le
 * service d'envoi gardent déjà ([ofReason]). Rien de sensible : ni code PIN, ni jeton, ni chemin, ni adresse de contenu ([sanitize]).
 */
object UploadFailure {
    enum class Cause { TV_NOT_FOUND, TV_UNREACHABLE, PIN_REFUSED, PIN_LOCKED, TOKEN_EXPIRED, NO_SPACE, CONNECTION_LOST, TIMEOUT, FILE_UNREADABLE, FILE_EMPTY,
        NETWORK_CHANGED, TV_BUSY, TV_OLD, NAME_TAKEN, TIME_LIMIT, BACKGROUND_BLOCKED, SERVICE_STOPPED, RESUME_FAILED, CANCELLED, TV_ERROR, UNKNOWN }

    /** [what]: la phrase de la cause (« la connexion à la TV a été coupée »), [action]: quoi faire, [asksPin]: toucher mène au champ du code PIN. */
    data class Info(val cause: Cause, val what: String, val action: String, val technical: String, val asksPin: Boolean = false)

    private class Row(val what: String, val action: String, val pin: Boolean = false)
    private const val RETRY = "Touchez pour réessayer"
    private val rows: Map<Cause, Row> = mapOf(
        Cause.TV_NOT_FOUND to Row("la TV est introuvable sur le Wi-Fi", "Vérifiez qu'elle est allumée et sur le même Wi-Fi, puis touchez pour réessayer"),
        Cause.TV_UNREACHABLE to Row("la TV ne répond pas", "Vérifiez qu'elle est allumée (pas en veille), puis touchez pour réessayer"),
        Cause.PIN_REFUSED to Row("la TV a refusé le code PIN", "Touchez pour saisir le code PIN affiché sur la TV", pin = true),
        Cause.PIN_LOCKED to Row("la TV est verrouillée après trop de codes faux", "Attendez une minute, puis touchez pour saisir le code PIN affiché sur la TV", pin = true),
        Cause.TOKEN_EXPIRED to Row("l'autorisation de ce téléphone a expiré", "$RETRY dans un instant : la liaison se renouvelle d'elle-même"),
        Cause.NO_SPACE to Row("il n'y a plus assez de place sur la TV", "Libérez de la place sur la TV, puis touchez pour réessayer"),
        Cause.CONNECTION_LOST to Row("la connexion à la TV a été coupée", RETRY),
        Cause.TIMEOUT to Row("la TV ne répond plus (délai dépassé)", RETRY),
        Cause.FILE_UNREADABLE to Row("le téléphone ne peut plus lire le fichier", "Rouvrez-le avec « Ouvrir avec CastBridge », puis touchez Copier"),
        Cause.FILE_EMPTY to Row("le fichier est vide", "Vérifiez le fichier d'origine"),
        Cause.NETWORK_CHANGED to Row("le Wi-Fi du téléphone a changé ou s'est coupé", "Reconnectez le téléphone au Wi-Fi de la TV, puis touchez pour réessayer"),
        Cause.TV_BUSY to Row("la TV est occupée", "Patientez un instant, puis touchez pour réessayer"),
        Cause.TV_OLD to Row("cette TV ne connaît pas cette fonction", "Mettez CastBridge-TV à jour sur la TV"),
        Cause.NAME_TAKEN to Row("un autre fichier du même nom est déjà sur la TV", "Renommez le fichier ou supprimez celui de la TV"),
        Cause.TIME_LIMIT to Row("Android a limité la durée des envois en arrière-plan", "Ouvrez CastBridge, puis touchez pour reprendre"),
        Cause.BACKGROUND_BLOCKED to Row("Android a bloqué l'envoi en arrière-plan", "Ouvrez CastBridge, puis touchez pour réessayer"),
        Cause.SERVICE_STOPPED to Row("l'envoi s'est arrêté sans message (Android l'a peut-être stoppé pour économiser la batterie)", "Désactivez l'économie de batterie pour CastBridge, puis touchez pour réessayer"),
        Cause.RESUME_FAILED to Row("la TV n'a pas pu reprendre la copie là où elle s'était arrêtée", "Touchez pour recommencer la copie"),
        Cause.CANCELLED to Row("la copie a été annulée", "Touchez pour la recommencer si besoin"),
        Cause.TV_ERROR to Row("la TV a rencontré une erreur", RETRY),
        Cause.UNKNOWN to Row("une erreur inattendue est survenue", RETRY),
    )

    private fun info(c: Cause, technical: String): Info { val r = rows.getValue(c); return Info(c, r.what, r.action, technical.take(MAX_TECH), r.pin) }
    private const val MAX_TECH = 120

    private val urls = Regex("(?:content|file)://\\S+")
    private val paths = Regex("/[\\w.\\-%@+]+/.*?(?=: |$)")
    private val bearer = Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE)
    private val tokens = Regex("cbt_\\w+")
    private val digits = Regex("\\d{6,}")

    /** Une ligne, sans chemin, adresse de contenu, jeton ni suite de 6 chiffres ou plus (un code PIN), 120 caractères au plus. */
    fun sanitize(s: String?): String = s.orEmpty().replace(Regex("\\s+"), " ").replace(urls, "…").replace(bearer, "…").replace(tokens, "…").replace(paths, "…").replace(digits, "…").trim().take(MAX_TECH)

    fun ofHttp(code: Int, body: String): Info {
        val b = body.lowercase()
        val c = when {
            code == 401 && "locked" in b -> Cause.PIN_LOCKED
            code == 401 && "bad token" in b -> Cause.TOKEN_EXPIRED
            code == 401 || code == 403 -> Cause.PIN_REFUSED
            code == 404 -> Cause.TV_OLD
            code == 408 -> Cause.TIMEOUT
            code == 409 || code == 429 || code == 503 -> Cause.TV_BUSY
            code == 413 || code == 507 -> Cause.NO_SPACE
            else -> Cause.TV_ERROR
        }
        val t = sanitize(body)
        return info(c, "HTTP $code" + if (t.isNotEmpty()) " : $t" else "")
    }

    fun ofException(t: Throwable): Info {
        val m = t.message.orEmpty().lowercase()
        val tech = t.javaClass.simpleName + (sanitize(t.message).takeIf { it.isNotEmpty() }?.let { ": $it" } ?: "")
        if (t is TvClient.HttpError) return ofHttp(t.code, t.message.orEmpty().substringAfter(": ", ""))
        if (t is TvCredential.Missing) return info(Cause.PIN_REFUSED, tech)
        if (t is BtProtocol.Refused) return Info(if (LinkRefusalTexts.asksPin(t.code)) Cause.PIN_REFUSED else Cause.TV_ERROR, LinkRefusalTexts.cause(t.code).replaceFirstChar { it.lowercase() },
            LinkRefusalTexts.action(t.code).replaceFirstChar { it.uppercase() }, "refus ${t.code}", LinkRefusalTexts.asksPin(t.code))
        val c = when {
            "network is unreachable" in m || "enetunreach" in m -> Cause.NETWORK_CHANGED
            t is SocketTimeoutException -> Cause.TIMEOUT
            t is ConnectException || t is NoRouteToHostException -> Cause.TV_UNREACHABLE
            t is UnknownHostException -> Cause.TV_NOT_FOUND
            t is SecurityException || t is FileNotFoundException -> Cause.FILE_UNREADABLE
            t is InterruptedIOException -> Cause.CANCELLED
            "no space left" in m || "enospc" in m -> Cause.NO_SPACE
            t is IOException -> Cause.CONNECTION_LOST          // broken pipe, EPIPE, reset, abort and every other cut link
            else -> Cause.UNKNOWN
        }
        return info(c, tech)
    }

    /** Le texte d'échec déjà gardé par la file ou le service d'envoi (français, parfois d'une autre couche) classé sans en perdre le détail. */
    fun ofReason(reason: String): Info {
        val r = reason.lowercase()
        val c = when {
            r.startsWith("annulé") -> Cause.CANCELLED
            "limité les envois" in r || "limite de durée" in r -> Cause.TIME_LIMIT
            r.startsWith("service refusé") || r.startsWith("en pause : android bloque") -> Cause.BACKGROUND_BLOCKED
            r.startsWith("envoi interrompu") || "n'a pas démarré" in r -> Cause.SERVICE_STOPPED
            "mauvais code" in r || "trop d'essais" in r -> Cause.PIN_LOCKED
            "code de la tv incorrect" in r || "code de la tv inconnu" in r -> Cause.PIN_REFUSED
            "autorisation" in r && "expiré" in r -> Cause.TOKEN_EXPIRED
            "plus de place" in r || "espace insuffisant" in r -> Cause.NO_SPACE
            "même nom" in r -> Cause.NAME_TAKEN
            "ne garde pas le transfert" in r || "refuse la copie après" in r -> Cause.RESUME_FAILED
            "fichier vide" in r -> Cause.FILE_EMPTY
            "fichier illisible" in r || "accès au fichier perdu" in r -> Cause.FILE_UNREADABLE
            "introuvable" in r || "tv non connectée" in r -> Cause.TV_NOT_FOUND
            "liaison perdue" in r || "interrompue" in r || "interrompu" in r || "coupé" in r -> Cause.CONNECTION_LOST
            "délai" in r -> Cause.TIMEOUT
            "mettez castbridge-tv à jour" in r -> Cause.TV_OLD
            "occupée" in r -> Cause.TV_BUSY
            else -> Cause.UNKNOWN
        }
        return info(c, sanitize(reason))
    }

    /** « Copie interrompue : la connexion à la TV a été coupée à 63 %. Touchez pour réessayer » (« Copie impossible » quand rien n'était parti). */
    fun message(i: Info, percent: Int?): String {
        val started = percent != null && percent > 0
        val detail = if (i.cause == Cause.UNKNOWN && i.technical.isNotEmpty()) " (détail : ${i.technical})" else ""
        return (if (started) "Copie interrompue" else "Copie impossible") + " : ${i.what}" + (if (started) " à $percent %" else "") + detail + ". ${i.action}"
    }
}
