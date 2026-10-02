package castbridge.core.lots

import castbridge.core.net.HttpLite
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy

/** What the network offers right now (the app maps ConnectivityManager to it). */
enum class Net { NONE, METERED, UNMETERED }

/** The server, seen by the phone. Throws [IOException] when it cannot be reached. */
interface LotRemote {
    /** The signed catalog as received (text), parsed by [LotSync]. */
    @Throws(IOException::class) fun catalogJson(channel: String): String
    /** The same, asking the server for one [feature] only (the signature binds the filter). Default: the unfiltered catalog, whose entries the caller filters itself. */
    @Throws(IOException::class) fun catalogJson(channel: String, feature: String?): String = catalogJson(channel)
    /** The bytes of [m] from [offset]; [Stream.from] is where the server really starts (0 if it ignored the range). */
    @Throws(IOException::class) fun open(m: LotMeta, offset: Long): Stream
    class Stream(val input: InputStream, val from: Long, private val onClose: () -> Unit = {}) : AutoCloseable {
        override fun close() { runCatching { input.close() }; onClose() }
    }
    /** The server no longer serves this lot (unpublished / revoked): no retry. */
    class Gone(msg: String) : IOException(msg)
}

/** [LotRemote] over HTTP(S): GET /api/v1/lots/catalog and /api/v1/lots/{feature}/{scope}/{version} (Range + If-Range on the ETag). */
class HttpLotRemote(baseUrl: String, proxy: Proxy? = null, private val deviceToken: String? = null,
                    private val http: HttpLite = HttpLite(proxy, userAgent = "CastBridge-lots")) : LotRemote {
    private val base = baseUrl.trimEnd('/')

    override fun catalogJson(channel: String): String {
        val r = http.request("GET", "$base/api/v1/lots/catalog?" + HttpLite.query("channel" to channel),
            headers = deviceToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap())
        if (r.code != 200) throw IOException(HttpLite.errorMessage(r))
        return r.body
    }

    override fun open(m: LotMeta, offset: Long): LotRemote.Stream {
        val c = http.open("$base/api/v1/lots/${m.id.feature}/${m.id.scope}/${m.version}")
        try {
            // lots are zip files (already compressed): no transfer encoding, so that a Range always means file bytes
            c.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) { c.setRequestProperty("Range", "bytes=$offset-"); c.setRequestProperty("If-Range", "\"${m.sha256}\"") }
            return when (val code = c.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val range = c.getHeaderField("Content-Range") ?: ""
                    if (!range.startsWith("bytes $offset-")) throw IOException("reprise refusée par le serveur ($range)")
                    LotRemote.Stream(c.inputStream, offset) { c.disconnect() }
                }
                HttpURLConnection.HTTP_OK -> LotRemote.Stream(c.inputStream, 0) { c.disconnect() }
                404, 410 -> throw LotRemote.Gone("lot retiré du serveur")
                416 -> throw IOException("plage refusée, nouveau départ")
                else -> throw IOException("HTTP $code pendant le téléchargement")
            }
        } catch (e: IOException) { c.disconnect(); throw e }
    }
}

/**
 * Phone <-> server synchronisation (pure logic). The phone is the only gateway to the server and syncs whenever IT has
 * Internet, whether the TV is present or not.
 *
 * - First synchronisation = the catalog + the lots the user selected ([wanted], default the profile's class/level) up to the quota.
 * - Later ones = only the lots whose version / hash changed (a lot is homogeneous, so an update = one whole lot, no diff).
 * - One lot at a time, into a ".part" file that resumes after a cut (Range + If-Range), retry with backoff, SHA-256 and size
 *   checked before the atomic install: a lot is never installed partially.
 * - [wifiOnly] (default): nothing is downloaded on a metered connection (bandwidth costs money).
 * - The catalog signature is checked first; a catalog the server key does not sign is ignored.
 */
class LotSync(
    private val store: LotStore,
    private val remote: LotRemote,
    private val publicKeys: List<String>,
    private val appVersion: Int,
    private val net: () -> Net,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    /** Edition rule (docs/TRIAL-EDITION.md): may this device hold this lot? Default: yes (nothing changes without the edition model). */
    private val allowed: (LotMeta) -> Boolean = { true },
    /**
     * The feature's own content check of a downloaded file (size and SHA-256 are already verified): a reason in French refuses the lot, which is then never stored
     * (the next sync starts clean). Default: none, so Apprendre and Quiz behave exactly as before.
     */
    private val check: (LotMeta, File) -> String? = { _, _ -> null },
) {
    enum class Outcome { UP_TO_DATE, INSTALLED, UPDATED, SKIPPED_FULL, SKIPPED_APP_TOO_OLD, NOT_IN_CATALOG, FAILED, CANCELLED, NOT_ENTITLED }

    data class Result(val id: LotId, val outcome: Outcome, val message: String = "", val bytes: Long = 0)

    data class Report(
        val catalog: LotManifest?,
        /** Why nothing was tried at all (no network, Wi-Fi required, server unreachable, bad signature…), in French. */
        val blocked: String?,
        val results: List<Result>,
    ) {
        val ok get() = blocked == null && results.none { it.outcome == Outcome.FAILED || it.outcome == Outcome.CANCELLED }
    }

    data class Options(val channel: String = "stable", val wifiOnly: Boolean = true, val maxAttempts: Int = 5)

    private var lastCatalogJson: String? = null
    fun lastCatalogJson(): String? = lastCatalogJson

    /** Fetches and verifies the catalog (no lot is downloaded). */
    fun fetchCatalog(opt: Options = Options()): Pair<LotManifest?, String?> {
        when (net()) {
            Net.NONE -> return null to "Pas de connexion Internet : les données déjà téléchargées restent utilisables"
            Net.METERED -> if (opt.wifiOnly) return null to "Connexion mobile : la mise à jour attend le Wi-Fi (option « Wi-Fi uniquement »)"
            Net.UNMETERED -> {}
        }
        if (publicKeys.isEmpty()) return null to "aucune clé publique configurée"
        val json = try { remote.catalogJson(opt.channel) } catch (e: IOException) { return null to "Serveur injoignable : ${e.message ?: e.javaClass.simpleName}" }
        val cat = try { LotManifest.parse(json) } catch (e: IllegalArgumentException) { return null to "Catalogue illisible" }
        if (!cat.signedByAny(publicKeys)) return null to "Signature du catalogue invalide : mise à jour ignorée"
        lastCatalogJson = json
        return cat to null
    }

    /**
     * Brings [wanted] up to date. [protect]: lots of the active profiles (never evicted). Lots are handled in the order given
     * (put the most important first); a lot that fails does not stop the others.
     * @param progress (lot, bytes done, total)
     */
    fun sync(wanted: List<LotId>, protect: (LotId) -> Boolean = { false }, opt: Options = Options(),
             progress: (LotId, Long, Long) -> Unit = { _, _, _ -> }, cancelled: () -> Boolean = { false }): Report {
        val (cat, why) = fetchCatalog(opt)
        if (cat == null) return Report(null, why, emptyList())
        val results = ArrayList<Result>()
        for (id in wanted.distinct()) {
            if (cancelled()) { results += Result(id, Outcome.CANCELLED, "interrompu"); continue }
            val m = cat.find(id)
            val have = store.get(id)?.meta
            results += when {
                m == null -> Result(id, Outcome.NOT_IN_CATALOG, "pas (encore) publié par le serveur")
                !allowed(m) -> Result(id, Outcome.NOT_ENTITLED, "« ${m.title} » fait partie de la version complète : débloquez-le pour le télécharger")
                have != null && have.version >= m.version && have.sha256 == m.sha256 -> Result(id, Outcome.UP_TO_DATE)
                have != null && have.version > m.version -> Result(id, Outcome.UP_TO_DATE)
                m.minAppVersion > appVersion -> Result(id, Outcome.SKIPPED_APP_TOO_OLD, "« ${m.title} » demande une version plus récente de CastBridge")
                else -> when (val f = store.fit(m, protect)) {
                    is LotStore.Fit.No -> Result(id, Outcome.SKIPPED_FULL, f.reason)
                    is LotStore.Fit.Yes -> one(m, have != null, cat.toJson(), protect, opt, progress, cancelled)
                }
            }
        }
        store.dropSupersededTrials()
        return Report(cat, null, results)
    }

    /** Every lot of the catalog newer than what is held (the "Mettre à jour" button), for the lots already on the phone. */
    fun updateAll(protect: (LotId) -> Boolean = { false }, opt: Options = Options(), progress: (LotId, Long, Long) -> Unit = { _, _, _ -> },
                  cancelled: () -> Boolean = { false }): Report =
        sync(store.list().sortedBy { if (protect(it.meta.id)) 0 else 1 }.map { it.meta.id }, protect, opt, progress, cancelled)

    private fun one(m: LotMeta, update: Boolean, proof: String, protect: (LotId) -> Boolean, opt: Options,
                    progress: (LotId, Long, Long) -> Unit, cancelled: () -> Boolean): Result {
        val part = store.partFile(m)
        part.parentFile.mkdirs()
        // a .part left by another build of the same version is useless
        val stamp = File(part.parentFile, part.name + ".sha256")
        if (!stamp.isFile || stamp.readText().trim() != m.sha256) { part.delete(); stamp.writeText(m.sha256) }
        var last: String = "téléchargement impossible"
        var attempt = 0
        while (attempt < opt.maxAttempts) {
            attempt++
            if (cancelled()) return Result(m.id, Outcome.CANCELLED, "interrompu")
            if (net() == Net.NONE || (opt.wifiOnly && net() == Net.METERED)) return Result(m.id, Outcome.FAILED, "connexion perdue : reprise au prochain passage")
            try {
                fetch(m, part, progress, cancelled)
                stamp.delete()
                if (part.length() == m.bytes && LotHash.sha256Hex(part).equals(m.sha256, ignoreCase = true)) check(m, part)?.let { why -> part.delete(); return Result(m.id, Outcome.FAILED, why) }
                return when (val r = store.install(m, part, proof, protect)) {
                    is LotStore.Install.Ok -> Result(m.id, if (update) Outcome.UPDATED else Outcome.INSTALLED, bytes = m.bytes)
                    is LotStore.Install.Full -> Result(m.id, Outcome.SKIPPED_FULL, r.reason)
                    is LotStore.Install.Refused -> Result(m.id, Outcome.FAILED, r.reason)   // corrupted: the part was deleted, the next sync starts clean
                }
            } catch (e: LotRemote.Gone) {
                part.delete(); stamp.delete()
                return Result(m.id, Outcome.FAILED, e.message ?: "lot retiré")
            } catch (e: IOException) {
                last = e.message ?: e.javaClass.simpleName
                if (attempt < opt.maxAttempts) sleep(minOf(60_000L, 2_000L shl (attempt - 1)))
            }
        }
        return Result(m.id, Outcome.FAILED, "$last (reprise possible : ${LotStore.mo(part.length())} déjà reçus)")
    }

    private fun fetch(m: LotMeta, part: File, progress: (LotId, Long, Long) -> Unit, cancelled: () -> Boolean) {
        var have = if (part.isFile) part.length() else 0L
        if (have > m.bytes) { part.delete(); have = 0 }
        if (have == m.bytes) return
        remote.open(m, have).use { s ->
            val append = s.from == have && have > 0
            if (!append && s.from != 0L) throw IOException("réponse du serveur inattendue")
            var done = if (append) have else 0L
            FileOutputStream(part, append).use { out ->
                val buf = ByteArray(32 * 1024)
                progress(m.id, done, m.bytes)
                while (true) {
                    if (cancelled()) throw IOException("interrompu")
                    val n = s.input.read(buf)
                    if (n < 0) break
                    if (done + n > m.bytes) { out.flush(); throw IOException("fichier plus gros qu'annoncé") }
                    out.write(buf, 0, n); done += n
                    progress(m.id, done, m.bytes)
                }
                out.fd.sync()
            }
        }
        if (part.length() > m.bytes) { part.delete(); throw IOException("fichier plus gros qu'annoncé") }
        if (part.length() != m.bytes) throw IOException("téléchargement coupé à ${part.length()} / ${m.bytes} octets")
    }
}
