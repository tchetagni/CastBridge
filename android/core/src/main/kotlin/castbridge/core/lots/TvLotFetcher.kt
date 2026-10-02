package castbridge.core.lots

import castbridge.core.langues.LangLotConsumer
import castbridge.core.langues.LangLots
import castbridge.core.net.HttpLite
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/**
 * [LotRemote] for a TV that has Internet itself (docs/LOTS.md « La TV connectée télécharge ses lots Langues »), hardened more than [HttpLotRemote]:
 * HTTPS only (plain http only to a loopback address, for tests), NO redirect is ever followed (a 3xx is an error: the request cannot leave the server host),
 * hard connect and read timeouts, a size cap on the catalog, no credentials of any kind (the catalog and the lots are public; the TV sends nothing about itself).
 */
class SecureHttpLotRemote(
    baseUrl: String,
    private val proxy: Proxy? = null,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
    allowLoopbackHttp: Boolean = false,
) : LotRemote {
    private val base: URL = URL(baseUrl.trimEnd('/')).also { u ->
        val loopback = u.host == "127.0.0.1" || u.host == "localhost"
        require(u.protocol == "https" || (allowLoopbackHttp && loopback && u.protocol == "http")) { "HTTPS obligatoire pour le serveur de lots" }
        require(u.userInfo == null && u.host.isNotEmpty() && u.query == null && u.ref == null) { "adresse de serveur invalide" }
    }

    private fun connect(path: String): HttpURLConnection {
        val u = URL(base.protocol, base.host, base.port, base.path.trimEnd('/') + path)
        val c = (if (proxy != null) u.openConnection(proxy) else u.openConnection()) as HttpURLConnection
        c.connectTimeout = connectTimeoutMs; c.readTimeout = readTimeoutMs
        c.instanceFollowRedirects = false; c.useCaches = false
        c.setRequestProperty("User-Agent", "CastBridge-TV-lots"); c.setRequestProperty("Accept-Encoding", "identity")
        return c
    }

    private fun refuseRedirect(code: Int) { if (code in 300..399) throw IOException("redirection refusée : le serveur doit répondre directement") }

    override fun catalogJson(channel: String): String = catalogJson(channel, null)

    override fun catalogJson(channel: String, feature: String?): String {
        val c = connect("/api/v1/lots/catalog?" + HttpLite.query("channel" to channel, "feature" to feature))
        try {
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            refuseRedirect(code)
            if (code != 200) throw IOException("HTTP $code")
            val bytes = c.inputStream.use { it.readNBytes(MAX_CATALOG + 1) }
            if (bytes.size > MAX_CATALOG) throw IOException("catalogue trop gros")
            return String(bytes, Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    override fun open(m: LotMeta, offset: Long): LotRemote.Stream {
        if (!LotNames.valid(m.id)) throw IOException("lot invalide")
        val c = connect("/api/v1/lots/${m.id.feature}/${m.id.scope}/${m.version}")
        try {
            if (offset > 0) { c.setRequestProperty("Range", "bytes=$offset-"); c.setRequestProperty("If-Range", "\"${m.sha256}\"") }
            val code = c.responseCode
            refuseRedirect(code)
            return when (code) {
                HttpURLConnection.HTTP_PARTIAL -> {
                    val range = c.getHeaderField("Content-Range") ?: ""
                    if (!range.startsWith("bytes $offset-")) throw IOException("reprise refusée par le serveur")
                    LotRemote.Stream(c.inputStream, offset) { c.disconnect() }
                }
                HttpURLConnection.HTTP_OK -> LotRemote.Stream(c.inputStream, 0) { c.disconnect() }
                404, 410 -> throw LotRemote.Gone("lot retiré du serveur")
                else -> throw IOException("HTTP $code pendant le téléchargement")
            }
        } catch (e: IOException) { c.disconnect(); throw e }
    }

    companion object { const val MAX_CATALOG = 1 shl 20 }
}

/**
 * The TV downloads its own « Langues » text lots when the user asks for it and the TV has Internet (server -> TV). Pure logic over a [LotRemote] (a fake in
 * the tests) and the [TvLotStore]; Android only supplies [online] and the button.
 *
 * What it does, in this order: refuses to start without Internet ([online]); fetches the signed catalog filtered to `langues` ([LotRemote.catalogJson]); verifies its
 * Ed25519 signature with the app's keys ([publicKeys], the server's production key) BEFORE reading anything else from it; keeps ONLY entries of feature `langues`
 * (never `learn`, `quiz`, `langues-media`, nor any other feature), full edition, with a valid name, a size within [LangLotConsumer.MAX_TEXT_LOT_BYTES] and an app
 * version this TV satisfies; keeps only those missing or older on the TV (a lot never goes down); optionally only [families] FREE ones; downloads each one with
 * resume (Range + If-Range), bounded retries and a deadline, checks size and SHA-256 itself, then hands it to [TvLotStore.installReceived] with the signed catalog as
 * proof, i.e. exactly the checks a phone upload goes through (signature again, vouching entry, size, SHA-256, minAppVersion, no downgrade, budget, the consumer's own content
 * check). It NEVER evicts anything to make room: a lot that does not fit in the 10 Mo is skipped with a plain message. Nothing runs by itself: no schedule, no listener.
 */
class TvLotFetcher(
    private val remote: LotRemote,
    private val store: TvLotStore,
    private val publicKeys: List<String>,
    private val appVersion: Int,
    private val online: () -> Boolean,
    /** When given, anything that is not FREE (reserved or unknown family) is skipped. The TV has no family list of its own: the server only publishes free lots (publish_lots.py refuses the others). */
    private val families: LotFamilies? = null,
    private val channel: String = "stable",
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = System::currentTimeMillis,
    private val maxAttempts: Int = 3,
    /** Whole time allowed to download ONE lot, retries included. */
    private val lotDeadlineMs: Long = 180_000,
    private val maxLots: Int = 100,
) {
    enum class Blocked { NO_INTERNET, NO_KEY, SERVER_UNREACHABLE, CATALOG_UNREADABLE, SIGNATURE_INVALID }
    enum class Status { INSTALLED, UPDATED, SKIPPED_FULL, SKIPPED_APP_TOO_OLD, REFUSED, FAILED, CANCELLED }

    data class Result(val id: LotId, val status: Status, val message: String = "", val bytes: Long = 0)
    data class Progress(val id: LotId, val done: Long, val total: Long, val index: Int, val count: Int)

    data class Report(val blocked: Blocked?, val detail: String = "", val results: List<Result> = emptyList(), val upToDate: Int = 0) {
        val ok get() = blocked == null && results.none { it.status == Status.FAILED || it.status == Status.CANCELLED || it.status == Status.REFUSED }
        val installed get() = results.count { it.status == Status.INSTALLED || it.status == Status.UPDATED }

        /** What the screen says, in plain French. */
        fun message(): String = when (blocked) {
            Blocked.NO_INTERNET -> "Cette TV n'a pas accès à Internet : branchez-la au réseau, ou envoyez les leçons depuis le téléphone."
            Blocked.NO_KEY -> "Mise à jour impossible : aucune clé de vérification dans cette application."
            Blocked.SERVER_UNREACHABLE -> "Le serveur CastBridge ne répond pas ($detail) : réessayez plus tard."
            Blocked.CATALOG_UNREADABLE -> "Le catalogue reçu du serveur est illisible : réessayez plus tard."
            Blocked.SIGNATURE_INVALID -> "Le catalogue reçu n'est pas signé par CastBridge : mise à jour refusée par sécurité."
            null -> {
                val problem = results.firstOrNull { it.status == Status.FAILED || it.status == Status.REFUSED || it.status == Status.SKIPPED_FULL || it.status == Status.SKIPPED_APP_TOO_OLD }
                when {
                    problem != null -> "${problem.message.ifBlank { "une leçon n'a pas pu être installée" }} ($installed installée(s))"
                    results.any { it.status == Status.CANCELLED } -> "Mise à jour interrompue ($installed installée(s))."
                    installed > 0 -> "$installed leçon(s) de langues installée(s)."
                    else -> "Les leçons de langues sont à jour."
                }
            }
        }
    }

    /** The lots to download, in order: updates of what the TV holds first, then new ones, smaller first. Pure. [skippedAppTooOld] = entries this TV's app version cannot take. */
    fun select(cat: LotManifest, held: List<TvLot>): Selection {
        val heldBy = held.associateBy { it.meta.id }
        val newest = cat.lots.asSequence()
            .filter { it.id.feature == LangLots.FEATURE && it.edition == Edition.FULL && LangLots.parse(it.id.scope) != null && it.bytes in 1..LangLotConsumer.MAX_TEXT_LOT_BYTES }
            .filter { families == null || families.of(it.id) == LotFamily.FREE }
            .groupBy { it.id }.mapValues { (_, v) -> v.maxBy { it.version } }.values
        var upToDate = 0
        val todo = ArrayList<LotMeta>()
        for (m in newest) { val h = heldBy[m.id]?.meta; if (h == null || h.version < m.version) todo += m else upToDate++ }
        val ordered = todo.sortedWith(compareBy({ if (heldBy.containsKey(it.id)) 0 else 1 }, { it.bytes }, { it.id.scope })).take(maxLots)
        return Selection(ordered, upToDate)
    }

    data class Selection(val lots: List<LotMeta>, val upToDate: Int)

    /** One run at a time (the screen disables its button meanwhile); [cancelled] is polled during every download. */
    @Synchronized fun run(progress: (Progress) -> Unit = {}, cancelled: () -> Boolean = { false }): Report {
        cancelFlag = cancelled
        try { return runInternal(progress, cancelled) } finally { cancelFlag = { false } }
    }

    private fun runInternal(progress: (Progress) -> Unit, cancelled: () -> Boolean): Report {
        if (!online()) return Report(Blocked.NO_INTERNET)
        if (publicKeys.isEmpty()) return Report(Blocked.NO_KEY)
        val json = try { remote.catalogJson(channel, LangLots.FEATURE) } catch (e: IOException) { return Report(Blocked.SERVER_UNREACHABLE, e.message ?: e.javaClass.simpleName) }
        val cat = try { LotManifest.parse(json) } catch (e: IllegalArgumentException) { return Report(Blocked.CATALOG_UNREADABLE) }
        // the signature first: nothing else of the catalog is trusted before it is verified
        if (!cat.signedByAny(publicKeys)) return Report(Blocked.SIGNATURE_INVALID)
        if (cat.channel != channel || (cat.feature != null && cat.feature != LangLots.FEATURE) || cat.lots.size > MAX_ENTRIES) return Report(Blocked.CATALOG_UNREADABLE)
        val proof = cat.toJson()
        val sel = select(cat, store.manifest().lots)
        val results = ArrayList<Result>()
        // lots this app is too old for are reported, not downloaded
        sel.lots.filter { it.minAppVersion > appVersion }.forEach { results += Result(it.id, Status.SKIPPED_APP_TOO_OLD, "« ${it.title} » demande une version plus récente de CastBridge-TV (mise à jour par clé USB)") }
        val todo = sel.lots.filter { it.minAppVersion <= appVersion }
        todo.forEachIndexed { i, m ->
            results += if (cancelled()) Result(m.id, Status.CANCELLED, "interrompu") else one(m, proof) { d -> progress(Progress(m.id, d, m.bytes, i + 1, todo.size)) }
        }
        return Report(null, "", results, sel.upToDate)
    }

    private fun one(m: LotMeta, proof: String, progress: (Long) -> Unit): Result {
        val manifest = store.manifest()
        val have = manifest.lots.firstOrNull { it.meta.id == m.id }?.meta
        val net = m.bytes - (have?.bytes ?: 0L)
        if (net > manifest.remainingBytes)
            return Result(m.id, Status.SKIPPED_FULL, "Pas assez de place sur la TV pour « ${m.title} » (${LotStore.mo(m.bytes)}) : il manque ${LotStore.mo(net - manifest.remainingBytes)} (rien n'a été supprimé)")
        val name = LotNames.fileName(m)
        val part = store.partFile(name)
        part.parentFile.mkdirs()
        if (part.length() > m.bytes) part.delete()
        if (part.parentFile.usableSpace < m.bytes - part.length() + (1L shl 20)) return Result(m.id, Status.SKIPPED_FULL, "Espace de stockage insuffisant sur la TV pour « ${m.title} »")
        val start = now()
        var last = "téléchargement impossible"
        for (attempt in 1..maxAttempts) {
            if (cancelFlag()) return Result(m.id, Status.CANCELLED, "interrompu")
            if (!online()) return Result(m.id, Status.FAILED, "connexion Internet perdue : relancez la mise à jour, elle reprendra")
            try {
                fetch(m, part, start, progress)
                if (!LotHash.sha256Hex(part).equals(m.sha256, ignoreCase = true)) { part.delete(); throw IOException("empreinte SHA-256 différente : fichier abîmé pendant le transfert") }
                return when (val r = store.installReceived(name, proof)) {
                    is TvLotStore.Result.Ok -> Result(m.id, if (have != null) Status.UPDATED else Status.INSTALLED, bytes = m.bytes)
                    is TvLotStore.Result.Refused -> Result(m.id, Status.REFUSED, r.reason)
                }
            } catch (e: LotRemote.Gone) {
                part.delete(); return Result(m.id, Status.FAILED, "« ${m.title} » n'est plus proposé par le serveur")
            } catch (e: IOException) {
                last = e.message ?: e.javaClass.simpleName
                if (now() - start > lotDeadlineMs) break
                if (attempt < maxAttempts) sleep(minOf(30_000L, 1_000L shl (attempt - 1)))
            }
        }
        return Result(m.id, Status.FAILED, "$last (reprise possible : ${LotStore.mo(part.length())} déjà reçus)")
    }

    @Volatile private var cancelFlag: () -> Boolean = { false }

    private fun fetch(m: LotMeta, part: File, start: Long, progress: (Long) -> Unit) {
        var have = if (part.isFile) part.length() else 0L
        if (have > m.bytes) { part.delete(); have = 0 }
        if (have < m.bytes) {
            remote.open(m, have).use { s ->
                val append = s.from == have && have > 0
                if (!append && s.from != 0L) throw IOException("réponse du serveur inattendue")
                var done = if (append) have else 0L
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(32 * 1024)
                    progress(done)
                    while (true) {
                        if (cancelFlag()) throw IOException("interrompu")
                        if (now() - start > lotDeadlineMs) throw IOException("délai dépassé")
                        val n = s.input.read(buf)
                        if (n < 0) break
                        if (done + n > m.bytes) { out.flush(); throw IOException("fichier plus gros qu'annoncé") }
                        out.write(buf, 0, n); done += n
                        progress(done)
                    }
                    out.fd.sync()
                }
            }
        }
        if (part.length() > m.bytes) { part.delete(); throw IOException("fichier plus gros qu'annoncé") }
        if (part.length() != m.bytes) throw IOException("téléchargement coupé à ${part.length()} / ${m.bytes} octets")
    }

    companion object { const val MAX_ENTRIES = 5000 }
}
