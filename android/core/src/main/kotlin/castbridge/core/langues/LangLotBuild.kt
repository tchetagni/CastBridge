package castbridge.core.langues

import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotHash
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * Builds the `langues` TEXT lots of content/langues/<pack id>/ (docs/LANGUES.md § 14): a deterministic zip with `langue.json` + `media.json` at its root,
 * file name `castbridge-lot-langues-<scope>-v<N>.lot`, plus `lots-catalog.json` (same shape as the Apprendre lots catalog). NEVER signed here (the
 * catalog signature is the publisher's job, with the real key, outside this repository). The version is kept in content/langues/lots.json,
 * which also holds the family of each lot (free = CC BY-SA derived, never sealed or rented).
 */
object LangLotBuilder {
    const val MAX_TEXT_LOT_BYTES = LangLotConsumer.MAX_TEXT_LOT_BYTES
    private const val FIXED_TIME = 1767225600000L   // 2026-01-01T00:00:00Z

    class Failure(msg: String) : Exception(msg)
    class Built(val meta: LotMeta, val bytes: ByteArray, val file: String, val date: String, val family: String, val units: Int, val exercises: Int)
    class Result(val lots: List<Built>, val registry: LangLotRegistry)

    /** The pack folders of [content]: every sub-folder holding a langue.json. */
    fun packDirs(content: File): List<File> = content.listFiles { f -> f.isDirectory && File(f, "langue.json").isFile }.orEmpty().sortedBy { it.name }

    /** Licence field written into the langue.json of a FREE lot (the server's LangLotValidator refuses a langues lot without it: « free only » is checked, not just promised). */
    const val LICENSE_TAG = "CC-BY-SA-4.0"

    /** [json] with `"license": "CC-BY-SA-4.0"` as first member of the root object (textual, so the rest of the file stays byte for byte as authored). */
    fun withLicense(json: String): String {
        val i = json.indexOf('{')
        require(i >= 0) { "langue.json illisible" }
        return json.substring(0, i + 1) + "\"license\": \"$LICENSE_TAG\"," + json.substring(i + 1)
    }

    /** Deterministic zip of one pack folder (same bytes on every machine for the same content). [free] = write the licence field into langue.json. */
    fun zip(dir: File, free: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.setLevel(9)
            for (name in listOf("langue.json", "media.json")) {
                val f = File(dir, name); if (!f.isFile) continue
                val b = if (free && name == "langue.json") withLicense(f.readText(Charsets.UTF_8)).toByteArray(Charsets.UTF_8) else f.readBytes()
                val e = ZipEntry(name); e.time = FIXED_TIME
                z.putNextEntry(e); z.write(b); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun contentHash(dir: File): String {
        val crc = StringBuilder()
        for (name in listOf("langue.json", "media.json")) File(dir, name).takeIf { it.isFile }?.let { crc.append(name).append(':').append(LotHash.sha256Hex(it)).append(';') }
        return LotHash.sha256Hex(crc.toString().toByteArray()).take(32)
    }

    fun build(content: File, registry: LangLotRegistry, today: String, update: Boolean, minAppVersion: Int = 0): Result {
        val entries = registry.entries.toMutableMap()
        val built = ArrayList<Built>()
        for (dir in packDirs(content)) {
            val pack = try { LangLotConsumer.readPack(zipTemp(dir)) } catch (e: Exception) { throw Failure("${dir.name} : ${e.message}") }
            val scope = pack.parts.scope
            if (scope != dir.name) throw Failure("${dir.name} : le dossier doit porter le nom du lot ($scope)")
            val problems = LangValidator.validate(pack)
            if (problems.isNotEmpty()) throw Failure("${dir.name} : ${problems.first()}" + if (problems.size > 1) " (+${problems.size - 1})" else "")
            val family = registry.familyOf(scope) ?: throw Failure("$scope : famille inconnue : ajouter le lot à « free » ou « reserved » de content/langues/lots.json")
            val hash = contentHash(dir)
            val old = entries[scope]
            val e = when {
                old != null && old.hash == hash -> old
                !update -> throw Failure("le contenu du lot $scope a changé (ou le lot est nouveau) : relancer avec -Pupdate pour passer à la version ${(old?.version ?: 0) + 1} et enregistrer content/langues/lots.json")
                else -> LangLotRegistry.Entry((old?.version ?: 0) + 1, hash, today).also { entries[scope] = it }
            }
            if (pack.version != e.version) {
                // the pack file carries its own version (checked by the consumer): it must be raised together with the lot
                throw Failure("$scope : langue.json est en version ${pack.version} mais le lot sera en version ${e.version} : aligner \"version\" dans langue.json")
            }
            val bytes = zip(dir, free = family == "free")
            if (bytes.size > MAX_TEXT_LOT_BYTES) throw Failure("$scope : lot texte de ${bytes.size} octets (plafond 3 Mo)")
            val meta = LotMeta(LotId(LangLots.FEATURE, scope), e.version, bytes.size.toLong(), LotHash.sha256Hex(bytes), pack.title, minAppVersion)
            built += Built(meta, bytes, "castbridge-lot-${LangLots.FEATURE}-$scope-v${e.version}.lot", e.date, family, pack.units.size, pack.units.sumOf { it.exercises.size })
        }
        return Result(built, registry.copy(entries = entries))
    }

    private fun zipTemp(dir: File): File = File.createTempFile("langlot", ".zip").also { it.deleteOnExit(); it.writeBytes(zip(dir)) }

    /** lots-catalog.json: same fields as the Apprendre one (`LearnLotCatalogFile`), plus `family`; `signature` is deliberately absent (UNSIGNED). */
    fun catalogJson(lots: List<Built>): String = Json.write(linkedMapOf("format" to 1, "feature" to LangLots.FEATURE, "signature" to "UNSIGNED", "lots" to lots.map { b ->
        linkedMapOf("feature" to b.meta.id.feature, "scope" to b.meta.id.scope, "version" to b.meta.version, "bytes" to b.meta.bytes, "sha256" to b.meta.sha256,
            "title" to b.meta.title, "minAppVersion" to b.meta.minAppVersion, "file" to b.file, "date" to b.date, "family" to b.family,
            "units" to b.units, "exercises" to b.exercises)
    })) + "\n"

    /** `LangLotBuilder <content/langues> <out> [--update] [--date=YYYY-MM-DD]` (gradle :core:buildLangLots). */
    @JvmStatic fun main(args: Array<String>) {
        if (args.size < 2) { System.err.println("usage : LangLotBuilder <content/langues> <out> [--update] [--date=AAAA-MM-JJ]"); exitProcess(2) }
        val content = File(args[0]); val out = File(args[1]); val update = "--update" in args
        val date = args.firstOrNull { it.startsWith("--date=") }?.substringAfter('=') ?: java.time.LocalDate.now().toString()
        val regFile = File(content, "lots.json")
        val r = try { build(content, LangLotRegistry.parse(regFile.takeIf { it.isFile }?.readText()), date, update) } catch (e: Failure) { System.err.println("✗ ${e.message}"); exitProcess(1) }
        out.mkdirs()
        out.listFiles { f -> f.name.endsWith(".lot") }?.forEach { it.delete() }
        for (b in r.lots) File(out, b.file).writeBytes(b.bytes)
        File(out, "lots-catalog.json").writeText(catalogJson(r.lots))
        if (update) regFile.writeText(r.registry.json())
        for (b in r.lots) println("✓ ${b.file}  ${b.bytes.size} octets  (${b.family}, ${b.units} unité(s), ${b.exercises} exercice(s))")
        println("catalogue NON SIGNÉ : ${File(out, "lots-catalog.json").path}")
    }
}

/**
 * content/langues/lots.json: `{"format":1,"free":["langues:<scope>"],"reserved":[…],"lots":{"<scope>":{"version":N,"hash":"…","date":"…"}}}`.
 * `free` = derived from CC BY-SA sources (published under CC BY-SA 4.0, no content key, never rented); `reserved` = original / public-domain content
 * (sealed per TV, rentable). A lot belongs to exactly one list (never mixed, docs/LANGUES.md § 13); in neither = unknown = refused.
 */
data class LangLotRegistry(val free: Set<String>, val reserved: Set<String>, val entries: Map<String, Entry>) {
    data class Entry(val version: Int, val hash: String, val date: String)

    fun familyOf(scope: String): String? = when {
        "${LangLots.FEATURE}:$scope" in free -> "free"
        "${LangLots.FEATURE}:$scope" in reserved -> "reserved"
        else -> null
    }

    /** The families as the rental policy wants them (explicit lists, fail closed). */
    fun families(): LotFamilies = LotFamilies.explicit(free, reserved)

    fun json(): String = Json.write(linkedMapOf("format" to 1, "free" to free.sorted(), "reserved" to reserved.sorted(),
        "lots" to entries.toSortedMap().mapValues { linkedMapOf("version" to it.value.version, "hash" to it.value.hash, "date" to it.value.date) })) + "\n"

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parse(text: String?): LangLotRegistry {
            if (text == null) return LangLotRegistry(emptySet(), emptySet(), emptyMap())
            val m = Json.obj(text)
            fun set(k: String) = (m[k] as? List<Any?>).orEmpty().mapNotNull { it as? String }.toSet()
            val free = set("free"); val reserved = set("reserved")
            require(free.intersect(reserved).isEmpty()) { "un lot ne peut être à la fois libre et réservé : ${free.intersect(reserved)}" }
            val lots = (m["lots"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) ->
                (v as? Map<String, Any?>)?.let { e -> k to Entry((e["version"] as? Number)?.toInt() ?: return@mapNotNull null, e["hash"] as? String ?: "", e["date"] as? String ?: "") }
            }.toMap()
            return LangLotRegistry(free, reserved, lots)
        }
    }
}

/**
 * The starter bundled in the TV APK (castbridge/langues/embedded/): `catalog.json` = {"format":1,"packs":[{"id","version"}]} and, per pack,
 * `<id>/langue.json` + `<id>/media.json`. Generated at build time from content/langues/embedded.txt (gradle :core:embedLanguesPacks).
 * Free content only (CC BY-SA derived): it is readable by anyone, never sealed.
 */
class EmbeddedLangSource(private val base: String = "/castbridge/langues/embedded/") {
    private fun res(name: String) = EmbeddedLangSource::class.java.getResourceAsStream(base + name)

    @Suppress("UNCHECKED_CAST")
    private val ids: List<String> by lazy {
        val cat = res("catalog.json")?.use { String(it.readBytes(), Charsets.UTF_8) } ?: return@lazy emptyList()
        runCatching { (Json.obj(cat)["packs"] as? List<Any?>).orEmpty().mapNotNull { (it as? Map<String, Any?>)?.get("id") as? String } }.getOrDefault(emptyList())
    }

    /** The parsed starter packs (unreadable or invalid ones are skipped). */
    val packs: List<LangPack> by lazy {
        ids.mapNotNull { id ->
            runCatching {
                val files = HashMap<String, String>()
                for (n in listOf("langue.json", "media.json")) res("$id/$n")?.use { files[n] = String(it.readBytes(), Charsets.UTF_8) }
                LangPackJson.parse(files).takeIf { LangValidator.validate(it).isEmpty() && it.parts.scope == id }
            }.getOrNull()
        }
    }

    /** Bytes of the starter resources (counts in the TV's 10 MB budget). */
    fun bytes(): Long = ids.sumOf { id -> listOf("langue.json", "media.json").sumOf { n -> res("$id/$n")?.use { it.readBytes().size.toLong() } ?: 0L } }

    companion object {
        /**
         * What the screen lists: the starter plus the installed lots; for the same scope the installed lot wins when its version is at least the starter's
         * (a lot is never replaced by an older starter). Sorted like [LangLotConsumer.installedAll].
         */
        fun merge(starter: List<LangPack>, installed: List<LangPack>): List<LangPack> {
            val by = LinkedHashMap<String, LangPack>()
            for (p in starter) by[p.parts.scope] = p
            for (p in installed) { val s = by[p.parts.scope]; if (s == null || p.version >= s.version) by[p.parts.scope] = p }
            return by.values.sortedWith(compareBy({ it.parts.target.ordinal }, { it.parts.level.ordinal }, { it.parts.scope }))
        }
    }
}
