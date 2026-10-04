package castbridge.core.trust

import castbridge.core.net.JsonLite
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceRequest
import castbridge.core.owner.FactorKind
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.OwnerFrames
import castbridge.core.tv.TvClient
import java.io.IOException

/**
 * The TV's « demande d'appareil » as the phone reads it (docs/TV-DEMANDE-APPAREIL.md): code, k, the factors' salted fingerprints and the installation's PUBLIC key.
 * Nothing else is ever read from the TV by this path. The texts are rebuilt by [OwnerFrames.deviceInfo], the very function the TV uses, so the main copy is identical
 * to `ActivationCenter.requestText()` byte for byte.
 */
data class TvDeviceRequest(val code: String, val k: Int, val factors: List<Pair<FactorKind, String>>, val installHex: String?, val installSigHex: String? = null) {
    private fun fingerprints() = Fingerprints(factors.toMap())
    private fun pub(): ByteArray? = installHex?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
    private fun sigPub(): ByteArray? = installSigHex?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()

    /** The main copy: `code=…`, `k=…`, `factor=TYPE|empreinte` lines and `install=x25519|…`; what the owner's tools read. */
    fun fullText(): String = OwnerFrames.deviceInfo(code, fingerprints(), pub(), sigPub())

    /**
     * The same without the `install=` line (the licence server's admin API refuses any line but code, k, factor and `install_sig` since the W23-05 audit corrections: « ligne inattendue »). The
     * `install_sig=` line stays: the server signs that key into the production activation it issues, so only this TV gets its licence registered and paid automatically.
     */
    fun serverText(): String = OwnerFrames.deviceInfo(code, fingerprints(), null, sigPub())

    /** Screen lines (French labels), fingerprints whole: to be drawn in a monospace font, scrolling if needed. */
    fun viewLines(): List<String> = buildList {
        add("Code d'appareil : $code")
        add("Seuil de reconnaissance : $k facteur${if (k > 1) "s" else ""} sur ${factors.size}")
        factors.forEach { (kind, h) -> add("Facteur ${label(kind)} : $h") }
        add(if (installHex != null) "Clé d'installation (publique, non secrète) : $installHex" else "Clé d'installation : absente (CastBridge-TV ancienne)")
        if (installSigHex != null) add("Clé de signature de la TV (publique, non secrète) : $installSigHex")
    }

    private fun label(k: FactorKind) = when (k) {
        FactorKind.FLASH -> "mémoire flash"; FactorKind.ETHERNET -> "Ethernet"; FactorKind.WIFI -> "Wi-Fi"
        FactorKind.SYSTEM_SERIAL -> "numéro de série système"; FactorKind.BLUETOOTH -> "Bluetooth"
    }
}

sealed class DeviceRequestParse {
    data class Ok(val request: TvDeviceRequest) : DeviceRequestParse()
    data class Refused(val message: String) : DeviceRequestParse()
}

/**
 * Reads the answer of `GET /api/tv/device-request`. Strict on what it keeps (a checked code, known factor types, 32-hex fingerprints, a 64-hex key: nothing else can reach
 * a text or a clipboard), tolerant of the rest (unknown fields ignored, missing `install` = an older TV), bounded ([MAX_CHARS]).
 */
object TvDeviceRequestParser {
    const val MAX_CHARS = 4096
    private val HEX32 = Regex("[0-9a-f]{32}")
    private val HEX64 = Regex("[0-9a-f]{64}")
    private const val BAD = "La réponse de la TV n'est pas une demande d'appareil lisible (CastBridge-TV trop ancienne ou réponse altérée)."

    fun parse(json: String): DeviceRequestParse {
        if (json.length > MAX_CHARS) return DeviceRequestParse.Refused("La réponse de la TV est trop volumineuse pour une demande d'appareil : elle est ignorée.")
        return try {
            val o = JsonLite.obj(json)
            val code = (o["code"] as? String)?.let { DeviceCode.parse(it) }?.takeIf { it == o["code"] } ?: return DeviceRequestParse.Refused(BAD)
            val k = (o["k"] as? Number)?.toInt() ?: return DeviceRequestParse.Refused(BAD)
            val list = (o["factors"] as? List<*>)?.takeIf { it.size in 1..FactorKind.values().size } ?: return DeviceRequestParse.Refused(BAD)
            val factors = list.map { f ->
                val m = f as? Map<*, *> ?: return DeviceRequestParse.Refused(BAD)
                val kind = FactorKind.values().firstOrNull { it.name == m["type"] } ?: return DeviceRequestParse.Refused(BAD)
                val h = (m["fingerprint"] as? String)?.takeIf { HEX32.matches(it) } ?: return DeviceRequestParse.Refused(BAD)
                kind to h
            }
            if (factors.map { it.first }.toSet().size != factors.size || k !in 1..factors.size) return DeviceRequestParse.Refused(BAD)
            val install = when (val i = o["install"]) { null -> null; is String -> i.takeIf { HEX64.matches(it) } ?: return DeviceRequestParse.Refused(BAD); else -> return DeviceRequestParse.Refused(BAD) }
            val installSig = when (val i = o["installSig"]) { null -> null; is String -> i.takeIf { HEX64.matches(it) } ?: return DeviceRequestParse.Refused(BAD); else -> return DeviceRequestParse.Refused(BAD) }
            val req = TvDeviceRequest(code, k, factors.sortedBy { it.first }, install, installSig)
            // the same check the owner's tools make: the code must match the fingerprints
            runCatching { DeviceRequest.parse(req.fullText()) }.getOrNull() ?: return DeviceRequestParse.Refused("Le code d'appareil ne correspond pas aux empreintes : réponse de la TV corrompue ou modifiée.")
            DeviceRequestParse.Ok(req)
        } catch (e: Exception) { DeviceRequestParse.Refused(BAD) }
    }
}

/** French texts of the screen « Demande d'appareil de la TV ». No credential ever appears in them. */
object TvDeviceRequestTexts {
    const val TITLE = "Demande d'appareil de la TV"
    const val INTRO = "Ce que la TV fournit pour qu'une clé d'activation soit générée pour elle. Lecture seule : rien n'est modifié sur la TV."
    const val COPY_FULL = "Copier la demande complète"
    const val SHARE = "Partager la demande complète"
    const val COPY_SERVER = "Copier pour le serveur"
    const val EXPLAIN_FULL = "La demande complète contient la clé publique d'installation (ligne install=) : elle n'est pas secrète, mais seuls les outils du propriétaire s'en servent (clés de location)."
    const val EXPLAIN_SERVER = "Le serveur de licences refuse la ligne install= : envoyez-lui cette copie, sans cette ligne."
    const val COPIED = "Copié."
    const val NOT_AUTHORISED = "Ce téléphone n'est pas autorisé pour cette TV : ajoutez la TV dans CastBridge (téléphone de confiance) ou saisissez le code à 6 chiffres de la TV, puis rouvrez cet écran. Rien n'a été demandé à la TV."
    const val UNREACHABLE = "La TV est injoignable : vérifiez qu'elle est allumée, que CastBridge-TV est ouvert et sur le même Wi-Fi, puis réessayez."
    const val UNREADABLE = "La TV a répondu, mais sa demande d'appareil est illisible : mettez CastBridge-TV à jour."

    fun forStatus(code: Int): String = when (code) {
        401 -> "La TV a refusé le code ou l'autorisation de ce téléphone : saisissez à nouveau le code à 6 chiffres affiché sur la TV, ou rétablissez la liaison de confiance."
        403 -> "La TV refuse cette lecture pour le moment (édition d'essai ou code requis) : saisissez le code de la TV."
        404 -> "Cette CastBridge-TV est trop ancienne pour fournir sa demande d'appareil : mettez-la à jour."
        429 -> "Trop d'essais : attendez quelques minutes avant de réessayer."
        else -> "La TV n'a pas pu fournir sa demande d'appareil (erreur $code). Réessayez."
    }
}

sealed class DeviceRequestResult {
    data class Shown(val request: TvDeviceRequest) : DeviceRequestResult()
    data class Refused(val message: String) : DeviceRequestResult()
}

/**
 * The authorisation gate and the one read. [base] is the TV's HTTP base (a Wi-Fi route), [credential] what the phone presents (a trusted-phone token or a PIN already kept):
 * without both, NOTHING is sent. [fetch] does the `GET` (base, credential) and returns the body; it is the only place the credential goes.
 */
object TvDeviceRequestReader {
    fun authorised(base: String?, credential: String?): Boolean =
        base != null && base.isNotBlank() && base.startsWith("http") && credential != null && credential != TvAuth.NO_PIN && TvAuth.isUsable(credential)

    fun read(base: String?, credential: String?, fetch: (String, String) -> String): DeviceRequestResult {
        if (!authorised(base, credential)) return DeviceRequestResult.Refused(TvDeviceRequestTexts.NOT_AUTHORISED)
        val body = try { fetch(base!!, credential!!) }
            catch (e: TvClient.HttpError) { return DeviceRequestResult.Refused(TvDeviceRequestTexts.forStatus(e.code)) }
            catch (e: IOException) { return DeviceRequestResult.Refused(TvDeviceRequestTexts.UNREACHABLE) }
            catch (e: Exception) { return DeviceRequestResult.Refused(TvDeviceRequestTexts.UNREADABLE) }
        return when (val p = TvDeviceRequestParser.parse(body)) {
            is DeviceRequestParse.Ok -> DeviceRequestResult.Shown(p.request)
            is DeviceRequestParse.Refused -> DeviceRequestResult.Refused(p.message)
        }
    }
}
