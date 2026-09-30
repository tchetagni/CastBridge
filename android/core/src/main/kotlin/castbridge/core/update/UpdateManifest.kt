package castbridge.core.update

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.bool
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * The signed answer of GET /api/v1/updates/{app}/latest (server: backend/, UpdateManifest.java).
 *
 * The signature covers [canonicalPayload], a line-based text rebuilt here from the parsed fields, exactly as the
 * server builds it: changing a field (URL, hash, size, version…) breaks the signature. The APK itself is then checked
 * against [sha256] and [size] ([verifyFile]).
 */
data class UpdateManifest(
    val app: String,
    val channel: String,
    val abi: String,
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val minSdk: Int?,
    val notes: String,
    val mandatory: Boolean,
    val minSupportedVersionCode: Int,
    val publishedAt: String,
    val keyId: String?,
    val signature: String,
) {
    fun canonicalPayload(): String = listOf(
        FORMAT,
        "app=$app",
        "channel=$channel",
        "abi=$abi",
        "versionCode=$versionCode",
        "versionName=$versionName",
        "url=$url",
        "sha256=$sha256",
        "size=$size",
        "minSdk=${minSdk ?: ""}",
        "mandatory=$mandatory",
        "minSupportedVersionCode=$minSupportedVersionCode",
        "publishedAt=$publishedAt",
        "notesSha256=${hex(sha256(notes.toByteArray(Charsets.UTF_8)))}",
    ).joinToString("\n")

    /** @param publicKeyBase64 the raw 32-byte Ed25519 public key in base64 ([UpdateKeys.PUBLIC_KEY]) */
    fun signatureValid(publicKeyBase64: String): Boolean = try {
        val key = Base64.getDecoder().decode(publicKeyBase64.trim())
        val sig = Base64.getDecoder().decode(signature)
        Ed25519.verify(key, canonicalPayload().toByteArray(Charsets.UTF_8), sig)
    } catch (e: IllegalArgumentException) {
        false
    }

    /** Must the user (or the TV) install it? The server said so, or the installed version is no longer supported. */
    fun isMandatoryFor(installedVersionCode: Int): Boolean = mandatory || installedVersionCode < minSupportedVersionCode

    /** null if [file] is exactly the announced APK, else why not (in French, for the logs / the screen). */
    fun verifyFile(file: File): String? {
        if (!file.isFile) return "fichier absent"
        if (file.length() != size) return "taille ${file.length()} au lieu de $size octets"
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        val got = hex(md.digest())
        return if (got.equals(sha256, ignoreCase = true)) null else "empreinte SHA-256 différente (fichier corrompu ou modifié)"
    }

    companion object {
        const val FORMAT = "castbridge-update-manifest-v1"

        /** Parses the JSON; throws [JsonLite.ParseError] if a required field is missing. */
        fun parse(json: String): UpdateManifest {
            val m = JsonLite.obj(json)
            fun s(k: String) = m.str(k) ?: throw JsonLite.ParseError("missing \"$k\"")
            return UpdateManifest(
                app = s("app"), channel = s("channel"), abi = s("abi"),
                versionCode = m.int("versionCode") ?: throw JsonLite.ParseError("missing \"versionCode\""),
                versionName = s("versionName"), url = s("url"), sha256 = s("sha256"),
                size = m.long("size") ?: throw JsonLite.ParseError("missing \"size\""),
                minSdk = m.int("minSdk"), notes = m.str("notes") ?: "",
                mandatory = m.bool("mandatory") ?: false,
                minSupportedVersionCode = m.int("minSupportedVersionCode") ?: 0,
                publishedAt = s("publishedAt"), keyId = m.str("keyId"), signature = s("signature"),
            )
        }

        internal fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
        internal fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    }
}
