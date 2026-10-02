package castbridge.core.langues

import castbridge.core.lots.LotConsumer
import castbridge.core.lots.LotHash
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.Json
import castbridge.core.util.BoundedRead
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Installs « Langues » text lots (feature `langues`, docs/LANGUES.md § 4) into [root]: `<root>/<scope>/v<version>/{lot.zip, meta.json}`, same layout and
 * same guarantees as the Apprendre consumer (staging folder + atomic rename, the previous version is removed only afterwards, a version is immutable,
 * no downgrade). A lot is a zip with `langue.json` (+ `media.json`) at its root; it is parsed and validated ([LangValidator]) BEFORE anything is written.
 * Only the TEXT lot goes to the TV (≤ 3 MB, counted in the TV's 10 MB by the lot store); the media twin (`langues-media`) is not handled here.
 * No network: lots arrive as files from the phone.
 */
class LangLotConsumer(
    private val root: File,
    private val appVersion: Int = Int.MAX_VALUE,
    private val now: () -> Long = System::currentTimeMillis,
) : LotConsumer {
    override val feature = LangLots.FEATURE

    class Installed(val meta: LotMeta, val installedAt: Long, val file: File)

    /** Why the last [install] was refused (French); null after a success. */
    @Volatile var lastError: String? = null; private set

    @Synchronized override fun install(meta: LotMeta, data: File): Boolean {
        lastError = null
        fun no(why: String): Boolean { lastError = why; return false }
        if (meta.id.feature != feature) return no("ce lot n'est pas un lot Langues")
        val scope = meta.id.scope
        val parts = LangLots.parse(scope) ?: return no("lot « $scope » : nom invalide (<cible>-<niveau>-<thème>-<départ>)")
        if (meta.minAppVersion > appVersion) return no("nécessite une version plus récente de l'application")
        if (!data.isFile || data.length() != meta.bytes) return no("taille du lot différente de celle annoncée")
        if (data.length() > MAX_TEXT_LOT_BYTES) return no("lot texte trop gros (plus de 3 Mo)")
        if (!runCatching { LotHash.sha256Hex(data).equals(meta.sha256, ignoreCase = true) }.getOrDefault(false)) return no("empreinte sha256 différente (fichier abîmé ou altéré)")
        val current = installedOne(scope)
        if (current != null) {
            if (current.meta.version == meta.version) return if (current.meta.sha256 == meta.sha256) true else no("la version ${meta.version} existe déjà avec un contenu différent")
            if (current.meta.version > meta.version) return no("une version plus récente (${current.meta.version}) est déjà installée")
        }
        val pack = when (val v = verifyContent(meta, data)) { is Verified.Ok -> v.pack; is Verified.Bad -> return no(v.reason) }
        val dir = File(root, scope)
        val stage = File(dir, ".stage-${meta.version}-${now()}")
        val target = File(dir, "v${meta.version}")
        try {
            stage.mkdirs()
            data.copyTo(File(stage, LOT_FILE), overwrite = true)
            File(stage, META_FILE).writeText(Json.write(linkedMapOf("feature" to feature, "scope" to scope, "version" to meta.version, "bytes" to meta.bytes,
                "sha256" to meta.sha256, "title" to meta.title.ifBlank { pack.title }, "minAppVersion" to meta.minAppVersion, "installedAt" to now())), Charsets.UTF_8)
            if (target.exists()) target.deleteRecursively()
            if (!stage.renameTo(target)) throw IOException("renommage impossible")
        } catch (e: IOException) {
            stage.deleteRecursively()
            return no("écriture impossible : ${e.message}")
        }
        dir.listFiles()?.filter { it != target }?.forEach { it.deleteRecursively() }
        return true
    }

    @Synchronized override fun remove(id: LotId) {
        if (id.feature == feature && LangLots.parse(id.scope) != null) File(root, id.scope).deleteRecursively()
    }

    @Synchronized override fun installed(): List<LotMeta> = installedAll().map { it.meta }

    @Synchronized fun installedAll(): List<Installed> =
        root.listFiles { f -> f.isDirectory && LangLots.parse(f.name) != null }.orEmpty().mapNotNull { installedOne(it.name) }
            .sortedWith(compareBy({ LangLots.parse(it.meta.id.scope)!!.target.ordinal }, { LangLots.parse(it.meta.id.scope)!!.level.ordinal }, { it.meta.id.scope }))

    @Synchronized fun installedOne(scope: String): Installed? {
        if (LangLots.parse(scope) == null) return null
        val versions = File(root, scope).listFiles { f -> f.isDirectory && f.name.matches(Regex("v\\d+")) }.orEmpty().sortedByDescending { it.name.drop(1).toInt() }
        for (d in versions) readInstalled(scope, d)?.let { return it }   // a damaged newest folder falls back to the previous one
        return null
    }

    /** The parsed pack of an installed lot, or null (missing, damaged). */
    fun pack(scope: String): LangPack? = installedOne(scope)?.let { runCatching { readPack(it.file) }.getOrNull() }

    /** Every installed pack, parsed (the screen's list). Damaged ones are skipped. */
    fun packs(): List<LangPack> = installedAll().mapNotNull { pack(it.meta.id.scope) }

    private fun readInstalled(scope: String, d: File): Installed? = runCatching {
        val lot = File(d, LOT_FILE); val m = Json.obj(File(d, META_FILE).readText(Charsets.UTF_8))
        val meta = LotMeta(LotId(feature, scope), (m["version"] as Number).toInt(), (m["bytes"] as Number).toLong(), m["sha256"] as String, m["title"] as? String ?: "",
            (m["minAppVersion"] as? Number)?.toInt() ?: 0)
        if (!lot.isFile || lot.length() != meta.bytes || meta.version != d.name.drop(1).toInt()) null
        else Installed(meta, (m["installedAt"] as? Number)?.toLong() ?: 0, lot)
    }.getOrNull()

    companion object {
        const val LOT_FILE = "lot.zip"
        const val META_FILE = "meta.json"
        const val MAX_TEXT_LOT_BYTES = 3L shl 20
        private const val MAX_ENTRY = 8L shl 20   // decompressed size cap per file (zip-bomb guard)

        sealed class Verified { class Ok(val pack: LangPack) : Verified(); class Bad(val reason: String) : Verified() }

        /**
         * The content checks of a Langues lot file ([meta] already vouched for), shared by the TV install and the phone's download (it never stores a lot the TV would refuse):
         * the zip reads, the pack matches its scope and version, and [LangValidator] finds nothing wrong. No file is written.
         */
        fun verifyContent(meta: LotMeta, data: File): Verified {
            val parts = LangLots.parse(meta.id.scope) ?: return Verified.Bad("lot « ${meta.id.scope} » : nom invalide (<cible>-<niveau>-<thème>-<départ>)")
            if (meta.id.feature != LangLots.FEATURE) return Verified.Bad("ce lot n'est pas un lot Langues")
            val pack = try { readPack(data) } catch (e: LangPackJson.ParseError) { return Verified.Bad(e.message ?: "lot illisible") } catch (e: IOException) { return Verified.Bad("zip illisible : ${e.message}") }
            if (pack.parts != parts) return Verified.Bad("le lot (${pack.id}) ne correspond pas à sa description (${meta.id.scope})")
            if (pack.version != meta.version) return Verified.Bad("le lot est en version ${pack.version}, sa description annonce ${meta.version}")
            val problems = LangValidator.validate(pack)
            if (problems.isNotEmpty()) return Verified.Bad("contenu refusé : ${problems.first()}" + if (problems.size > 1) " (+${problems.size - 1} autre(s))" else "")
            return Verified.Ok(pack)
        }

        /** Reads `langue.json` and `media.json` from the root of a lot zip. */
        fun readPack(zip: File): LangPack {
            val files = HashMap<String, String>()
            ZipFile(zip).use { z ->
                for (name in listOf("langue.json", "media.json")) {
                    val e = z.getEntry(name) ?: continue
                    if (e.size > MAX_ENTRY) throw LangPackJson.ParseError("$name trop gros")
                    val bytes = try { z.getInputStream(e).use { BoundedRead.readAll(it, MAX_ENTRY.toInt()) } } catch (x: BoundedRead.TooLarge) { throw LangPackJson.ParseError("$name trop gros") }
                    files[name] = String(bytes, Charsets.UTF_8)
                }
            }
            return LangPackJson.parse(files)
        }
    }
}

/** What the « Langues » screen lists: languages with their levels, from the parsed installed packs. Pure. */
object LangCatalog {
    data class Entry(val pack: LangPack, val units: Int, val exercises: Int)

    fun entries(packs: List<LangPack>): List<Entry> = packs.map { p -> Entry(p, p.units.size, p.units.sumOf { it.exercises.size }) }

    fun languages(packs: List<LangPack>): List<Lang> = packs.map { it.parts.target }.distinct().sortedBy { it.ordinal }

    /** Levels held for [lang], in order. */
    fun levels(packs: List<LangPack>, lang: Lang): List<LangLevel> = packs.filter { it.parts.target == lang }.map { it.parts.level }.distinct().sortedBy { it.ordinal }

    fun packsFor(packs: List<LangPack>, lang: Lang, level: LangLevel): List<LangPack> = packs.filter { it.parts.target == lang && it.parts.level == level }.sortedBy { it.parts.theme }

    /** Label of an exercise kind for the learner. */
    fun kindLabel(k: LangExerciseKind) = when (k) {
        LangExerciseKind.DICTATION -> "Dictée"; LangExerciseKind.MATCH -> "Appariement"; LangExerciseKind.ORDER -> "Remettre dans l'ordre"; LangExerciseKind.MCQ -> "QCM"
        LangExerciseKind.CLOZE -> "Texte à trous"; LangExerciseKind.TRANSLATE -> "Traduction"; LangExerciseKind.TRUEFALSE -> "Vrai ou faux"
        LangExerciseKind.SPEAK -> "Expression orale"; LangExerciseKind.WRITE -> "Expression écrite"; LangExerciseKind.STROKES -> "Tracé"
    }

    const val EMPTY_MESSAGE = "Aucune langue installée : envoyez un lot depuis le téléphone"
}
