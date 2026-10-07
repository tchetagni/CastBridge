package castbridge.core.owner

import castbridge.core.quiz.QrCode
import castbridge.core.trust.DeviceRequestParse
import castbridge.core.trust.TvAuthReply
import castbridge.core.trust.TvDeviceRequest
import castbridge.core.trust.TvDeviceRequestParser
import castbridge.core.trust.TvDeviceRequestTexts
import castbridge.core.tv.TvClient
import castbridge.core.tv.TvDeviceRequestApi
import java.io.IOException

/**
 * La demande d'appareil d'une TV VERROUILLÉE, lue par le téléphone avec le code à 6 chiffres (docs/TV-DEMANDE-APPAREIL.md, DESIGN-ACTIVATION-SIMPLE § 3 F2) :
 * `GET /api/activation/device-request`, en-tête `X-CB-Pin`, réponse en TEXTE BRUT (`code=`, `k=`, `factor=…`, `install_sig=…`, jamais `install=` : ACT-F4). Une TV qui n'a pas encore la
 * route (CastBridge-TV 0.14.43 et avant) répond 403 comme pour toute autre route : [Reply.RouteMissing], la clé peut quand même lui être envoyée avec le code.
 *
 * Pur. Lecture stricte (code contrôlé, empreintes de 32 hexadécimaux, code d'accord avec les empreintes) ; ce qui n'est pas dans le modèle ne sort jamais : le texte partagé et le QR
 * sont reconstruits à partir des champs, sans la ligne `install=`. Aucun texte ne recopie le code, la demande ou le corps d'une réponse (ACT-NF2).
 */
object LockedRequestRoute {
    const val PATH = "/api/activation/device-request"
    const val MAX_CHARS = TvDeviceRequestParser.MAX_CHARS
    const val SHARE_SUBJECT = "Demande d'activation CastBridge-TV"

    private val HEX32 = Regex("[0-9a-f]{32}")
    /** U+FEFF written as a code point: no invisible character in the source. */
    private val BOM = 0xFEFF.toChar().toString()
    private const val TOO_BIG = "La réponse de la TV est trop volumineuse pour une demande d'appareil : elle est ignorée."
    private const val MISMATCH = "Le code d'appareil ne correspond pas aux empreintes : réponse de la TV corrompue ou modifiée."

    sealed class Reply {
        data class Request(val request: TvDeviceRequest) : Reply() { override fun toString() = "Request" }
        /** 403 ou 404 : la TV n'a pas cette route (version d'avant l'activation sans réseau). */
        object RouteMissing : Reply() { override fun toString() = "RouteMissing" }
        object CodeRefused : Reply() { override fun toString() = "CodeRefused" }
        data class LockedOut(val seconds: Long) : Reply()
        object TermsNotAccepted : Reply() { override fun toString() = "TermsNotAccepted" }
        object Closed : Reply() { override fun toString() = "Closed" }
        data class Unreadable(val message: String) : Reply()
    }

    /**
     * [status] et [body] de la réponse (pour une erreur, le texte de l'exception HTTP suffit : seuls `"locked"` et `retryAfter` y sont lus).
     * 200 = la demande ; 401 = code refusé ou verrou ; 403/404 = route absente ; 409 = conditions d'usage non acceptées ; 429 = activation par le Wi-Fi fermée.
     */
    fun interpret(status: Int, body: String): Reply = when (status) {
        200 -> fromParse(parse(body))
        401 -> unauthorised(body)
        403, 404 -> Reply.RouteMissing
        409 -> Reply.TermsNotAccepted
        429 -> Reply.Closed
        else -> failure(status)
    }

    /**
     * The same replies for a TV that runs its FULL API (an activated or trial TV the phone is linked to): `GET /api/tv/device-request` with the code as PIN, JSON answer
     * (docs/TV-DEMANDE-APPAREIL.md, read by [TvDeviceRequestParser]).
     */
    fun interpretFull(status: Int, body: String): Reply = when (status) {
        200 -> fromParse(TvDeviceRequestParser.parse(body))
        401 -> unauthorised(body)
        403, 404 -> Reply.RouteMissing
        else -> failure(status)
    }

    /**
     * Asks the TV at [base] for its device request with [code] (a locked TV: [PATH], text; a TV the phone is linked to, [locked] false: `/api/tv/device-request`, JSON). The code goes in the PIN
     * header of this one request and nowhere else. The call goes through [castbridge.core.net.BoundRoute] (a Wi-Fi Direct group is reached by its own network). null = the TV did not answer
     * (refused connection, timeout, no route): the caller tries again until the bound of its route.
     */
    fun probe(base: String, code: String, locked: Boolean): Reply? = try {
        if (locked) interpret(200, TvClient(base, code).raw("GET", PATH)) else interpretFull(200, TvClient(base, code).raw("GET", TvDeviceRequestApi.PATH))
    } catch (e: TvClient.HttpError) {
        if (locked) interpret(e.code, e.message.orEmpty()) else interpretFull(e.code, e.message.orEmpty())
    } catch (e: IOException) { null } catch (e: RuntimeException) { null }

    private fun fromParse(p: DeviceRequestParse): Reply = when (p) { is DeviceRequestParse.Ok -> Reply.Request(p.request); is DeviceRequestParse.Refused -> Reply.Unreadable(p.message) }
    private fun unauthorised(body: String): Reply = when (val k = TvAuthReply.of(401, body)) { is TvAuthReply.Kind.Locked -> Reply.LockedOut(k.retryAfterSec); else -> Reply.CodeRefused }
    private fun failure(status: Int): Reply = Reply.Unreadable("La TV a répondu par une erreur ($status) : réessayez dans un instant.")

    /** Le texte brut de la TV en demande d'appareil, ou le refus avec sa raison. Tolère BOM, CRLF, lignes vides, lignes inconnues (ignorées, jamais recopiées). */
    fun parse(text: String): DeviceRequestParse {
        if (text.length > MAX_CHARS) return DeviceRequestParse.Refused(TOO_BIG)
        if (text.trimStart().startsWith("{")) return TvDeviceRequestParser.parse(text)           // tolerance: the JSON of « /api/tv/device-request » (same five fields, same strict reader)
        val clean =text.removePrefix(BOM).replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        val info = OwnerFrames.parseDeviceInfo(clean) ?: return DeviceRequestParse.Refused(TvDeviceRequestTexts.UNREADABLE)
        val n = info.fp.n
        if (n !in 1..FactorKind.values().size || info.k !in 1..n || info.fp.byKind.values.any { !HEX32.matches(it) }) return DeviceRequestParse.Refused(TvDeviceRequestTexts.UNREADABLE)
        if (info.code != DeviceCode.of(info.fp)) return DeviceRequestParse.Refused(MISMATCH)
        fun hex(b: ByteArray?) = b?.joinToString("") { "%02x".format(it) }
        return DeviceRequestParse.Ok(TvDeviceRequest(info.code, info.k, info.fp.byKind.toList(), hex(info.installPub), hex(info.installSig)))
    }

    /** Ce que l'usager envoie à l'agent (WhatsApp, e-mail) : la demande pour le serveur, jamais la ligne `install=`. Les lignes sont celles que lisent les outils du propriétaire. */
    fun shareText(r: TvDeviceRequest): String = r.serverText()

    /** Le QR du texte partagé : correction M si elle tient, sinon L ; null quand même L ne suffit pas (le texte partagé reste alors la seule voie, jamais un QR tronqué). */
    fun qr(r: TvDeviceRequest): QrCode? {
        val t = shareText(r)
        return listOf(QrCode.Ecl.M, QrCode.Ecl.L).firstNotNullOfOrNull { ecl -> runCatching { QrCode.encode(t, ecl) }.getOrNull() }
    }
}
