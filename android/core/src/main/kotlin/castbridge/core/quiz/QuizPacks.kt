package castbridge.core.quiz

import castbridge.core.net.HttpLite
import castbridge.core.update.Ed25519
import castbridge.core.update.UpdateKeys
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipFile

/**
 * Question packs (docs/QUIZ.md, « Packs de questions »): the big banks (thousands of questions per course) are not in the
 * APK. Each course is cut in parts of ~1 500 questions, each part a small zip (`manifest.json` + `questions.json`,
 * ~75 KB). The TV downloads a part only when the anti-repetition history says that fewer than
 * [QUIZ_PACK_THRESHOLD_GAMES] fresh games are left, from the server (directly, or through the phone's Bluetooth/Wi-Fi
 * gateway) or pushed by the phone, and keeps at most [QUIZ_PACK_MAX_BYTES] of them. Everything is optional: with no
 * pack the bundled bank plays as before.
 */
const val QUIZ_PACK_MAX_BYTES = 11_000_000L
const val QUIZ_PACK_THRESHOLD_GAMES = 60
const val QUIZ_PACK_SUFFIX = ".quiz.zip"

/** One pack as announced by the server catalog (GET /api/v1/quiz/packs), signed with the update key. */
data class QuizPackInfo(
    val id: String, val track: String, val level: String?, val field: String?, val part: Int, val parts: Int, val version: Int,
    val file: String, val size: Long, val sha256: String, val questions: Int, val keyId: String?, val signature: String,
) {
    /** Same key as [QuestionFilter.courseKey]: "general", "primary/CM2", "higher/L1/droit". */
    val course: String = listOfNotNull(track, level, field).joinToString("/")

    /** The bytes the server signs (an Ed25519 signature, like the update manifests): any change breaks it. */
    fun canonicalPayload(): String = listOf(FORMAT, "id=$id", "course=$course", "part=$part", "parts=$parts", "version=$version",
        "file=$file", "size=$size", "sha256=$sha256", "questions=$questions").joinToString("\n")

    fun signatureValid(publicKeyBase64: String): Boolean = try {
        Ed25519.verify(Base64.getDecoder().decode(publicKeyBase64.trim()), canonicalPayload().toByteArray(Charsets.UTF_8), Base64.getDecoder().decode(signature))
    } catch (e: IllegalArgumentException) { false }

    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "track" to track, "level" to level, "field" to field, "part" to part, "parts" to parts,
        "version" to version, "file" to file, "size" to size, "sha256" to sha256, "questions" to questions, "keyId" to keyId, "signature" to signature)

    companion object {
        const val FORMAT = "castbridge-quiz-pack-v1"
        private val SAFE_FILE = Regex("^quiz-[a-z0-9-]+-p\\d+-v\\d+\\.quiz\\.zip$")
        fun safeFile(name: String) = SAFE_FILE.matches(name)

        fun parse(m: Map<String, Any?>): QuizPackInfo? = runCatching {
            fun n(k: String) = (m[k] as? Number)?.toLong() ?: error("missing $k")
            fun s(k: String) = m[k] as? String ?: error("missing $k")
            QuizPackInfo(s("id"), s("track"), m["level"] as? String, m["field"] as? String, n("part").toInt(), n("parts").toInt(), n("version").toInt(),
                s("file"), n("size"), s("sha256").lowercase(), n("questions").toInt(), m["keyId"] as? String, s("signature"))
        }.getOrNull()?.takeIf { safeFile(it.file) && it.size > 0 && it.id.isNotBlank() }

        fun parseJson(json: String): QuizPackInfo? = runCatching { parse(Json.obj(json)) }.getOrNull()
    }
}

/** What a pack zip must look like (checked before anything is installed). */
object QuizPackFormat {
    const val MAX_ENTRY = 16L shl 20
    class PackError(message: String) : Exception(message)
    /** [index] / [questionsRaw]: the optional `index.json` of a lot (docs/QUIZ.md, « Lots ») and the raw `questions.json`, for [QuizLotFormat]. */
    class Content(val manifest: Map<String, Any?>, val bank: QuizBank, val index: ByteArray? = null, val questionsRaw: ByteArray? = null)

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buf = ByteArray(64 * 1024); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Reads and checks a pack: only the two known entries, bounded size, hash of the questions, every question valid and in the pack's course. */
    @Throws(PackError::class)
    fun read(file: File, expectCourse: String? = null): Content {
        try {
            ZipFile(file).use { z ->
                val entries = z.entries().toList()
                val names = entries.map { it.name }.toSet()
                if (entries.size != names.size || !names.containsAll(listOf("manifest.json", "questions.json")) || !setOf("manifest.json", "questions.json", "index.json").containsAll(names))
                    throw PackError("contenu du pack inattendu")
                fun text(name: String): ByteArray {
                    val e = z.getEntry(name) ?: throw PackError("$name manquant")
                    if (e.size > MAX_ENTRY) throw PackError("$name trop gros")
                    val out = java.io.ByteArrayOutputStream()
                    z.getInputStream(e).use { i -> val buf = ByteArray(32 * 1024); var total = 0L; while (true) { val n = i.read(buf); if (n < 0) break; total += n; if (total > MAX_ENTRY) throw PackError("$name trop gros"); out.write(buf, 0, n) } }
                    return out.toByteArray()
                }
                val manifest = Json.obj(String(text("manifest.json"), Charsets.UTF_8))
                val qbytes = text("questions.json")
                @Suppress("UNCHECKED_CAST")
                val declared = ((manifest["files"] as? Map<String, Any?>)?.get("questions.json") as? Map<String, Any?>)?.get("sha256") as? String
                if (declared == null || !declared.equals(MessageDigest.getInstance("SHA-256").digest(qbytes).joinToString("") { "%02x".format(it) }, ignoreCase = true))
                    throw PackError("empreinte des questions invalide")
                val track = Track.of(manifest["track"] as? String) ?: throw PackError("parcours inconnu")
                val filter = QuestionFilter(track, manifest["level"] as? String, manifest["field"] as? String)
                if (expectCourse != null && filter.courseKey != expectCourse) throw PackError("le pack ne correspond pas au parcours annoncé")
                val bank = try { QuizBank.parse(String(qbytes, Charsets.UTF_8), computedPlayable = true) } catch (e: Json.ParseError) { throw PackError("questions illisibles : ${e.message}") }
                bank.validate().firstOrNull()?.let { throw PackError("question invalide : $it") }
                if (bank.all.any { !filter.matches(it) }) throw PackError("question hors parcours")
                if ((manifest["questions"] as? Number)?.toInt() != bank.all.size) throw PackError("nombre de questions incohérent")
                val index = if ("index.json" in names) text("index.json") else null
                if (index != null) {
                    @Suppress("UNCHECKED_CAST")
                    val d = ((manifest["files"] as? Map<String, Any?>)?.get("index.json") as? Map<String, Any?>)?.get("sha256") as? String
                    if (d == null || !d.equals(MessageDigest.getInstance("SHA-256").digest(index).joinToString("") { "%02x".format(it) }, ignoreCase = true)) throw PackError("empreinte de l'index invalide")
                }
                return Content(manifest, bank, index, if (index != null) qbytes else null)
            }
        } catch (e: PackError) { throw e } catch (e: Exception) { throw PackError("pack illisible : ${e.message ?: e.javaClass.simpleName}") }
    }
}

/**
 * The packs installed on this TV, in [dir] (preferably a USB drive / external storage, never an internal partition that
 * is nearly full: [minFreeBytes]). A pack is `<file>` + `<file>.json` (its signed catalog entry + install counter).
 * Total size never above [maxBytes]: installing evicts packs in the order chosen by the caller until it fits.
 */
class QuizPackStore(
    val dir: File,
    val maxBytes: Long = QUIZ_PACK_MAX_BYTES,
    private val publicKeys: List<String> = UpdateKeys.PUBLIC_KEYS,
    private val requireSignature: Boolean = true,
    private val minFreeBytes: Long = 0,
) {
    data class Installed(val info: QuizPackInfo, val file: File, val seq: Long)
    sealed class Install {
        data class Ok(val evicted: List<String>) : Install()
        data class Refused(val reason: String) : Install()
    }

    private val lock = Any()
    private val banks = HashMap<String, Pair<Long, QuizBank>>()
    @Volatile private var merged: QuizBank? = null

    fun installed(): List<Installed> = synchronized(lock) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(QUIZ_PACK_SUFFIX) } ?: return emptyList()
        files.mapNotNull { f ->
            val meta = File(f.path + ".json")
            val m = runCatching { Json.obj(meta.readText(Charsets.UTF_8)) }.getOrNull() ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val info = QuizPackInfo.parse(m["info"] as? Map<String, Any?> ?: return@mapNotNull null) ?: return@mapNotNull null
            if (info.file != f.name || info.size != f.length()) return@mapNotNull null
            Installed(info, f, (m["seq"] as? Number)?.toLong() ?: 0L)
        }.sortedBy { it.seq }
    }

    fun usedBytes(): Long = installed().sumOf { it.file.length() }
    fun has(id: String, minVersion: Int = 0): Boolean = installed().any { it.info.id == id && it.info.version >= minVersion }

    /** Installs [file] (a downloaded or pushed zip) described by the catalog entry [info]. The file is left in place on refusal. */
    fun install(info: QuizPackInfo, file: File, evictionOrder: (List<Installed>) -> List<Installed> = { l -> l.sortedBy { it.seq } }): Install = synchronized(lock) {
        if (!QuizPackInfo.safeFile(info.file)) return Install.Refused("nom de fichier refusé")
        if (!file.isFile) return Install.Refused("fichier absent")
        if (file.length() != info.size) return Install.Refused("taille ${file.length()} au lieu de ${info.size} octets")
        if (requireSignature && publicKeys.none { info.signatureValid(it) }) return Install.Refused("signature invalide : pack ignoré")
        if (!QuizPackFormat.sha256(file).equals(info.sha256, ignoreCase = true)) return Install.Refused("empreinte SHA-256 différente (fichier corrompu ou modifié)")
        val content = try { QuizPackFormat.read(file, info.course) } catch (e: QuizPackFormat.PackError) { return Install.Refused(e.message ?: "pack invalide") }
        if (info.size > maxBytes) return Install.Refused("pack plus gros que le plafond de ${maxBytes / 1_000_000} Mo")
        dir.mkdirs()
        val current = installed()
        val replaced = current.filter { it.info.id == info.id }
        if (replaced.any { it.info.version > info.version }) return Install.Refused("une version plus récente est déjà installée")
        var used = current.sumOf { it.file.length() } - replaced.sumOf { it.file.length() }
        val evicted = ArrayList<String>()
        val candidates = evictionOrder(current.filter { it.info.id != info.id }).toMutableList()
        while (used + info.size > maxBytes) {
            val victim = if (candidates.isEmpty()) return Install.Refused("plafond de ${maxBytes / 1_000_000} Mo atteint : rien à retirer sans perdre des questions utiles") else candidates.removeAt(0)
            used -= victim.file.length(); evicted += victim.info.id
        }
        if (minFreeBytes > 0 && dir.usableSpace - info.size < minFreeBytes) return Install.Refused("pas assez de place libre sur ce stockage")
        val seq = (current.maxOfOrNull { it.seq } ?: 0L) + 1
        try {
            val target = File(dir, info.file)
            val tmp = File(dir, info.file + ".tmp")
            file.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(target)) { target.delete(); if (!tmp.renameTo(target)) throw IOException("renommage impossible") }
            val meta = File(target.path + ".json"); val metaTmp = File(meta.path + ".tmp")
            metaTmp.writeText(Json.write(linkedMapOf("info" to info.toMap(), "seq" to seq)), Charsets.UTF_8)
            if (!metaTmp.renameTo(meta)) { meta.delete(); metaTmp.renameTo(meta) }
        } catch (e: IOException) { return Install.Refused("écriture impossible : ${e.message}") }
        (replaced + current.filter { it.info.id in evicted }).filter { it.file.name != info.file }.forEach { delete(it) }
        banks.remove(info.id); merged = null
        return Install.Ok(evicted)
    }

    fun remove(id: String) = synchronized(lock) { installed().filter { it.info.id == id }.forEach { delete(it) }; banks.remove(id); merged = null }

    private fun delete(i: Installed) { i.file.delete(); File(i.file.path + ".json").delete(); banks.remove(i.info.id); merged = null }

    /** Questions of one installed pack (parsed once, kept until the pack changes). */
    fun bankOf(i: Installed): QuizBank? = synchronized(lock) {
        banks[i.info.id]?.takeIf { it.first == i.seq }?.second
            ?: runCatching { QuizPackFormat.read(i.file, i.info.course).bank }.getOrNull()?.also { banks[i.info.id] = i.seq to it }
    }

    /** Every installed question. Unreadable packs are skipped (and will be replaced by the next refill). */
    fun bank(): QuizBank = merged ?: synchronized(lock) {
        merged ?: QuizBank(installed().flatMap { bankOf(it)?.all.orEmpty() }).also { merged = it }
    }
}

/** A place packs can be downloaded from: the CastBridge server (directly or through the phone's gateway) — the phone itself pushes instead, see [QuizPackRelay]. */
interface QuizPackSource {
    val name: String
    /** The signed catalog (invalid entries already dropped), or null when this source cannot be reached. */
    fun catalog(): List<QuizPackInfo>?
    /** Downloads [info] into [part], continuing what is already there. true = complete; IOException = cut (what was received stays for the next attempt). */
    @Throws(IOException::class)
    fun download(info: QuizPackInfo, part: File, progress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false }): Boolean
}

/** GET /api/v1/quiz/packs (signed catalog) and GET /api/v1/quiz/packs/{file} (Range requests), docs/API-SERVER.md. */
class ServerPackSource(
    baseUrl: String, private val http: HttpLite, private val deviceToken: String? = null, private val deviceId: String? = null,
    private val publicKeys: List<String> = UpdateKeys.PUBLIC_KEYS, override val name: String = "serveur",
) : QuizPackSource {
    private val base = baseUrl.trimEnd('/')

    override fun catalog(): List<QuizPackInfo>? {
        val headers = deviceToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        val r = try { http.request("GET", "$base/api/v1/quiz/packs?" + HttpLite.query("deviceId" to deviceId), headers = headers) } catch (e: IOException) { return null }
        if (r.code != 200) return null
        val root = try { Json.obj(r.body) } catch (e: Json.ParseError) { return null }
        return (root["packs"] as? List<*>).orEmpty().mapNotNull { o ->
            @Suppress("UNCHECKED_CAST")
            (o as? Map<String, Any?>)?.let { QuizPackInfo.parse(it) }
        }.filter { i -> publicKeys.any { i.signatureValid(it) } }
    }

    @Throws(IOException::class)
    override fun download(info: QuizPackInfo, part: File, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): Boolean {
        part.parentFile?.mkdirs()
        var have = if (part.isFile) part.length() else 0L
        if (have > info.size) { part.delete(); have = 0 }
        if (have == info.size) return true
        val c = http.open("$base/api/v1/quiz/packs/${info.file}")
        try {
            deviceToken?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (have > 0) { c.setRequestProperty("Range", "bytes=$have-"); c.setRequestProperty("If-Range", "\"${info.sha256}\"") }
            val append = when (val code = c.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> { if (c.getHeaderField("Content-Range")?.startsWith("bytes $have-") != true) throw IOException("reprise refusée par le serveur"); true }
                HttpURLConnection.HTTP_OK -> { have = 0; false }
                416 -> { part.delete(); throw IOException("plage refusée, nouveau départ") }
                else -> throw IOException("HTTP $code pendant le téléchargement")
            }
            c.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(32 * 1024); var done = have
                    while (true) {
                        if (cancelled()) throw IOException("téléchargement interrompu")
                        val n = input.read(buf); if (n < 0) break
                        if (done + n > info.size) { part.delete(); throw IOException("fichier plus gros qu'annoncé") }
                        out.write(buf, 0, n); done += n; progress(done, info.size)
                    }
                    out.fd.sync()
                }
            }
        } finally { c.disconnect() }
        if (part.length() != info.size) throw IOException("téléchargement coupé à ${part.length()} / ${info.size} octets")
        return true
    }
}

/**
 * Decides when to download, what, from where, and what to throw away to stay under the cap.
 * - only when fewer than [thresholdGames] fresh games remain for the course ([QuizBank.remainingFresh]) and a part of it
 *   is not installed yet;
 * - sources tried in order (server, then the next one…): an unreachable source or a bad download switches to the next;
 * - eviction: first the packs whose questions were all asked within the last [QuizHistory] window (« épuisés »), then the
 *   packs of courses never played here, oldest first; a pack of the course being refilled that still has fresh
 *   questions is never evicted.
 */
class QuizPackManager(
    private val store: QuizPackStore,
    /** The bank in use (bundled + cache + installed packs): refreshed by [onChanged]. */
    private val combined: () -> QuizBank,
    /** History of whoever plays here (the TV's own, or the union of the phones). */
    private val history: () -> QuestionHistory?,
    private val onChanged: () -> Unit = {},
    val thresholdGames: Int = QUIZ_PACK_THRESHOLD_GAMES,
    private val perGame: Int = 15,
    private val gap: Int = DEFAULT_MIN_GAP_GAMES,
    /** Courses played here (any history entry): packs of other courses go first when room is needed. */
    private val played: () -> Set<String> = { emptySet() },
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val attemptsPerPack: Int = 3,
) {
    data class Report(val course: String, val freshBefore: Int, val freshAfter: Int, val installed: List<String>, val evicted: List<String>, val source: String?, val message: String, val unreachable: Boolean = false)
    data class Need(val course: String, val freshGames: Int)

    fun freshGames(filter: QuestionFilter): Int = combined().remainingFresh(filter, history(), gap, perGame).gamesLeft

    /** Courses (among [filters]) that are running low. */
    fun needs(filters: Collection<QuestionFilter>): List<Need> = filters.map { Need(it.courseKey, freshGames(it)) }.filter { it.freshGames < thresholdGames }

    fun refill(filter: QuestionFilter, sources: List<QuizPackSource>, maxPacks: Int = 4, progress: (String, Long, Long) -> Unit = { _, _, _ -> }, cancelled: () -> Boolean = { false }): Report {
        val course = filter.courseKey
        val before = freshGames(filter)
        if (before >= thresholdGames) return Report(course, before, before, emptyList(), emptyList(), null, "Assez de parties sans répétition ($before) : rien à télécharger")
        val got = ArrayList<String>(); val evicted = ArrayList<String>(); var used: String? = null; var why = "Aucune source n'a pu fournir de questions"
        var fresh = before
        var reached = false
        sourceLoop@ for (src in sources) {
            val cat = src.catalog()
            if (cat == null) { why = "${src.name} injoignable"; continue }
            reached = true
            while (fresh < thresholdGames && got.size < maxPacks && !cancelled()) {
                val next = cat.filter { it.course == course && !store.has(it.id, it.version) }.minByOrNull { it.part }
                if (next == null) { why = "Tous les lots de ce parcours sont déjà installés"; break@sourceLoop }
                val part = File(store.dir, "downloads/${next.file}.part")
                var ok = false
                for (attempt in 1..attemptsPerPack) {
                    try { ok = src.download(next, part, { d, t -> progress(next.id, d, t) }, cancelled); if (ok) break }
                    catch (e: IOException) { why = "${src.name} : ${e.message}"; if (cancelled()) break@sourceLoop; if (attempt < attemptsPerPack) sleep(minOf(30_000L, 1_000L shl (attempt - 1))) }
                }
                if (!ok) continue@sourceLoop                                     // this source failed: try the next one
                when (val r = store.install(next, part, evictionOrder(course))) {
                    is QuizPackStore.Install.Ok -> { part.delete(); got += next.id; evicted += r.evicted; used = src.name; onChanged(); fresh = freshGames(filter) }
                    is QuizPackStore.Install.Refused -> { part.delete(); why = "${next.id} refusé : ${r.reason}"; continue@sourceLoop }
                }
            }
            if (fresh >= thresholdGames || got.size >= maxPacks) break
        }
        val msg = if (got.isEmpty()) why else "${got.size} lot(s) installé(s) depuis $used : $fresh parties sans répétition" + if (evicted.isNotEmpty()) " (${evicted.size} ancien(s) lot(s) retiré(s))" else ""
        return Report(course, before, fresh, got, evicted, used, msg, unreachable = !reached)
    }

    /** A pack pushed by the phone: same checks, same eviction. */
    fun installPushed(info: QuizPackInfo, file: File): QuizPackStore.Install =
        store.install(info, file, evictionOrder(info.course)).also { if (it is QuizPackStore.Install.Ok) onChanged() }

    private fun exhausted(i: QuizPackStore.Installed): Boolean {
        val h = history() ?: return false
        val bank = store.bankOf(i) ?: return true
        return bank.all.none { h.age(i.info.course, it.id) >= gap }
    }

    private fun evictionOrder(target: String): (List<QuizPackStore.Installed>) -> List<QuizPackStore.Installed> = { list ->
        val playedNow = played()
        list.map { it to exhausted(it) }
            .filter { (i, ex) -> i.info.course != target || ex }                  // fresh packs of the course being refilled stay
            .sortedWith(compareBy({ (i, ex) -> if (ex) 0 else if (i.info.course !in playedNow) 1 else 2 }, { (i, _) -> i.seq }))
            .map { it.first }
    }
}

/** Bundled bank + packs installed on this TV + packs read as they are on a USB drive (`CastBridge/QuizPacks`, fichiers .quiz.zip, no download, no cap). */
class PackedQuestionSource(
    private val base: QuestionSource,
    private val store: QuizPackStore?,
    private val driveDirs: () -> List<File> = { emptyList() },
    /** Lots installed through the lots framework (docs/QUIZ.md, « Lots »): they come last, so a lot question replaces a bundled or pack one with the same id. */
    private val lots: QuizLotConsumer? = null,
) : QuestionSource {
    private class Built(val bank: QuizBank, val baseBank: QuizBank, val drive: String)
    @Volatile private var built: Built? = null
    @Volatile private var builtLevel: Built? = null
    @Volatile override var origin: String = base.origin; private set

    /** Rebuilt when the bundled/server bank, the installed packs or the files on the USB drive changed. */
    override fun bank(): QuizBank = current(base.bank(), { built }) { built = it }

    /** The bundled level of [filter] (loaded on demand, one at a time) + packs + lots; an installed lot replaces the bundled questions of the same id. */
    override fun bankFor(filter: QuestionFilter): QuizBank {
        val b = base.bankFor(filter)
        return if (b === base.bank()) bank() else current(b, { builtLevel }) { builtLevel = it }
    }

    private fun current(b: QuizBank, get: () -> Built?, set: (Built) -> Unit): QuizBank {
        val sig = driveSignature(); val c = get()
        if (c != null && c.baseBank === b && c.drive == sig) return c.bank
        return synchronized(this) {
            val again = get()
            if (again != null && again.baseBank === b && again.drive == sig) again.bank else build(b, sig).also(set).bank
        }
    }

    /** The installed packs changed: the next [bank] call rebuilds. */
    fun refresh() { synchronized(this) { built = null; builtLevel = null } }

    private fun driveFiles(): List<File> = runCatching { driveDirs() }.getOrDefault(emptyList()).flatMap { d ->
        d.listFiles { f -> f.isFile && f.name.endsWith(QUIZ_PACK_SUFFIX) }?.sortedBy { it.name }.orEmpty()
    }
    private fun driveSignature() = driveFiles().joinToString("|") { "${it.path}:${it.length()}:${it.lastModified()}" } +
        lots?.installed().orEmpty().joinToString("|", prefix = "#") { "${it.id.scope}:${it.sha256}" }

    private fun build(baseBank: QuizBank, sig: String): Built {
        var bank = baseBank
        val fromStore = store?.bank()?.all.orEmpty()
        val fromDrive = ArrayList<Question>()
        for (f in driveFiles()) runCatching { QuizPackFormat.read(f).bank.all }.getOrNull()?.let { fromDrive += it }
        if (fromStore.isNotEmpty()) bank = bank.merge(QuizBank(fromStore))
        if (fromDrive.isNotEmpty()) bank = bank.merge(QuizBank(fromDrive))
        val fromLots = lots?.bank()?.all.orEmpty()
        if (fromLots.isNotEmpty()) bank = bank.merge(QuizBank(fromLots))
        origin = base.origin + (if (fromLots.isNotEmpty()) " + ${fromLots.size} questions des thèmes installés" else "") + (if (fromStore.isNotEmpty()) " + ${fromStore.size} questions de lots" else "") + (if (fromDrive.isNotEmpty()) " + ${fromDrive.size} de la clé USB" else "")
        return Built(bank, baseBank, sig)
    }
}
