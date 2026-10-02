package castbridge.core.free

import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotId
import castbridge.core.quiz.Json
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * The packs embedded in the TV app (core resources, no Android API): the « Langues » starter (`castbridge/langues/embedded/`, licence tag and family written
 * at build time from content/langues/lots.json) and the « Apprendre » base packs (`castbridge/learn/embedded/`, licence = `license` field of each `pack.json`;
 * today none carries one, so none is exported). Nothing is read until [packs] files are iterated.
 */
class EmbeddedFreeSource(
    private val langBase: String = "/castbridge/langues/embedded/",
    private val learnBase: String = "/castbridge/learn/embedded/",
) : FreeContentSource {
    private val reserved = LinkedHashSet<String>()
    private val free = LinkedHashSet<String>()

    /** Families of the embedded lots (from the build-time registry); call after [packs]. */
    fun families(): LotFamilies { packs(); return LotFamilies.explicit(free, reserved) }

    private val cache: List<FreePack> by lazy { langues() + learn() }
    override fun packs(): List<FreePack> = cache

    private fun res(base: String, name: String): InputStream? = EmbeddedFreeSource::class.java.getResourceAsStream(base + name)

    @Suppress("UNCHECKED_CAST")
    private fun catalog(base: String): List<Map<String, Any?>> {
        val text = res(base, "catalog.json")?.use { String(it.readBytes(), Charsets.UTF_8) } ?: return emptyList()
        return runCatching { (Json.obj(text)["packs"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> } }.getOrDefault(emptyList())
    }

    private fun langues(): List<FreePack> = catalog(langBase).mapNotNull { m ->
        val id = m["id"] as? String ?: return@mapNotNull null
        val key = "langues:$id"
        when (m["family"] as? String) { "reserved" -> reserved += key; "free" -> free += key }
        val names = listOf("langue.json", "media.json").filter { res(langBase, "$id/$it")?.use { true } == true }
        if (names.isEmpty()) return@mapNotNull null
        val title = runCatching { Json.obj(res(langBase, "$id/langue.json")!!.use { String(it.readBytes(), Charsets.UTF_8) })["title"] as? String }.getOrNull() ?: id
        FreePack("langues-$id", (m["version"] as? Number)?.toInt() ?: 1, m["license"] as? String, title, LotId("langues", id),
            sequence { for (n in names) res(langBase, "$id/$n")?.use { yield(FreeFile(n, it.readBytes())) } })
    }

    private fun learn(): List<FreePack> = catalog(learnBase).mapNotNull { m ->
        val id = m["id"] as? String ?: return@mapNotNull null
        val file = m["file"] as? String ?: return@mapNotNull null
        val license = runCatching { packJsonLicense(file) }.getOrNull()
        FreePack("apprendre-$id", (m["version"] as? Number)?.toInt() ?: 1, license, (m["title"] as? String) ?: id,
            (m["scope"] as? String)?.let { LotId("learn", it) },
            sequence { res(learnBase, file)?.use { yield(FreeFile(file, it.readBytes())) } })
    }

    private fun packJsonLicense(file: String): String? = res(learnBase, file)?.use { raw ->
        ZipInputStream(raw).use { z ->
            generateSequence { z.nextEntry }.firstOrNull { it.name == "pack.json" }
                ?.let { Json.obj(String(z.readBytes(), Charsets.UTF_8))["license"] as? String }
        }
    }
}
