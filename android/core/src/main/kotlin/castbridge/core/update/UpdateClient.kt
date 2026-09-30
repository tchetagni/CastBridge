package castbridge.core.update

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy

/**
 * Automatic updates of one app, pure logic (no Android): ask the server for the newest allowed version, check the
 * Ed25519 signature of the answer, download the APK into a ".part" file that resumes after a cut (HTTP Range +
 * If-Range on the file hash), check SHA-256 and size, then hand over the verified file. Installing it is the app's job.
 *
 * @param baseUrl       server root, e.g. "https://castbridge.example.org" (no trailing slash needed)
 * @param app           "tv" or "phone"
 * @param publicKeys    accepted Ed25519 public keys (base64, raw 32 bytes): [UpdateKeys.PUBLIC_KEYS]
 * @param proxy         optional proxy, e.g. SOCKS 127.0.0.1:1080 when the phone shares its Internet over Bluetooth
 * @param sleep         waits between download attempts (replaced in tests)
 */
class UpdateClient(
    baseUrl: String,
    private val app: String,
    private val publicKeys: List<String>,
    proxy: Proxy? = null,
    private val http: HttpLite = HttpLite(proxy, userAgent = "CastBridge-$app-updater"),
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    private val base = baseUrl.trimEnd('/')

    /** What the app knows about itself. [deviceToken] (from the device registration) lets the admin's settings apply. */
    data class Installed(
        val versionCode: Int,
        val supportedAbis: List<String>,
        val sdk: Int,
        val channel: String = "stable",
        val deviceId: String? = null,
        val deviceToken: String? = null,
    )

    sealed class Check {
        object UpToDate : Check()
        /** Newer version available; [mandatory]: install without asking (or insist) — see [UpdateManifest.isMandatoryFor]. */
        data class Available(val manifest: UpdateManifest, val mandatory: Boolean) : Check()
        /** The admin blocked this device: no update. */
        object Blocked : Check()
        data class Failed(val reason: String, val retryable: Boolean = true) : Check()
    }

    fun check(me: Installed): Check {
        if (publicKeys.isEmpty()) return Check.Failed("aucune clé publique de mise à jour configurée", retryable = false)
        val url = "$base/api/v1/updates/$app/latest?" + HttpLite.query(
            "abis" to me.supportedAbis.joinToString(",").ifEmpty { null },
            "channel" to me.channel, "versionCode" to me.versionCode, "sdk" to me.sdk, "deviceId" to me.deviceId,
        )
        val r = try {
            http.request("GET", url, headers = me.deviceToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap())
        } catch (e: IOException) {
            return Check.Failed("serveur injoignable : ${e.message ?: e.javaClass.simpleName}")
        }
        return when (r.code) {
            204 -> Check.UpToDate
            403 -> Check.Blocked
            200 -> {
                val m = try { UpdateManifest.parse(r.body) } catch (e: IllegalArgumentException) {
                    return Check.Failed("réponse du serveur illisible")
                }
                when {
                    publicKeys.none { m.signatureValid(it) } -> Check.Failed("signature du manifeste invalide : mise à jour ignorée", retryable = false)
                    m.app != app -> Check.Failed("manifeste d'une autre application (${m.app})", retryable = false)
                    m.versionCode <= me.versionCode -> Check.UpToDate
                    m.minSdk != null && m.minSdk > me.sdk -> Check.Failed("Android trop ancien pour cette version (SDK ${m.minSdk} requis)", retryable = false)
                    else -> Check.Available(m, m.isMandatoryFor(me.versionCode))
                }
            }
            else -> Check.Failed(HttpLite.errorMessage(r), retryable = r.code >= 500 || r.code == 429)
        }
    }

    /**
     * Downloads the APK of [m] into [dir] and returns the verified file ("castbridge-<app>-<versionCode>.apk").
     * Resumes a previous ".part" of the same file; retries up to [maxAttempts] times with growing pauses.
     * @param progress (bytes done, total)
     * @param cancelled polled between blocks: true stops with an IOException (the .part stays for next time)
     */
    @Throws(IOException::class)
    fun download(m: UpdateManifest, dir: File, progress: (Long, Long) -> Unit = { _, _ -> }, maxAttempts: Int = 6,
                 cancelled: () -> Boolean = { false }): File {
        dir.mkdirs()
        val target = File(dir, "castbridge-${m.app}-${m.versionCode}.apk")
        if (target.isFile && m.verifyFile(target) == null) return target
        val part = File(dir, target.name + ".part")
        val etag = "\"${m.sha256}\""
        // a .part from another build of the same versionCode is useless
        val stamp = File(dir, target.name + ".sha256")
        if (!stamp.isFile || stamp.readText().trim() != m.sha256) { part.delete(); stamp.writeText(m.sha256) }

        var attempt = 0
        var lastError: IOException? = null
        while (attempt < maxAttempts) {
            attempt++
            try {
                fetch(m, part, etag, progress, cancelled)
                val problem = m.verifyFile(part)
                if (problem != null) { part.delete(); throw IOException("APK refusé : $problem") }
                target.delete()
                if (!part.renameTo(target)) throw IOException("renommage impossible")
                stamp.delete()
                return target
            } catch (e: IOException) {
                lastError = e
                if (cancelled() || e is Cancelled) throw e
                if (attempt < maxAttempts) sleep(minOf(60_000L, 2_000L shl (attempt - 1)))
            }
        }
        throw lastError ?: IOException("téléchargement impossible")
    }

    class Cancelled : IOException("téléchargement interrompu")

    private fun fetch(m: UpdateManifest, part: File, etag: String, progress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        var have = if (part.isFile) part.length() else 0L
        if (have > m.size) { part.delete(); have = 0 }
        if (have == m.size) return
        val c = http.open(m.url)
        try {
            if (have > 0) {
                c.setRequestProperty("Range", "bytes=$have-")
                c.setRequestProperty("If-Range", etag)
            }
            val code = c.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val range = c.getHeaderField("Content-Range") ?: ""
                    if (!range.startsWith("bytes $have-")) throw IOException("reprise refusée par le serveur ($range)")
                    true
                }
                HttpURLConnection.HTTP_OK -> { have = 0; false } // server restarts from the beginning
                416 -> { part.delete(); throw IOException("plage refusée, nouveau départ") }
                410 -> throw Cancelled().also { part.delete() } // revoked meanwhile: stop
                else -> throw IOException("HTTP $code pendant le téléchargement")
            }
            c.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = have
                    progress(done, m.size)
                    while (true) {
                        if (cancelled()) throw Cancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (done + n > m.size) throw IOException("fichier plus gros qu'annoncé")
                        out.write(buf, 0, n)
                        done += n
                        progress(done, m.size)
                    }
                    out.fd.sync()
                }
            }
            if (part.length() != m.size) throw IOException("téléchargement coupé à ${part.length()} / ${m.size} octets")
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** Reads the public key answer of the server (to compare with the embedded one in a diagnostic screen). */
        fun parsePublicKey(json: String): String? = runCatching { JsonLite.obj(json)["publicKey"] as? String }.getOrNull()
    }
}
