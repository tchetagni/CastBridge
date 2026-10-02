package castbridge.core.free

import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotFamily
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes the free (CC BY-SA) packs as ONE reproducible ZIP stream, same layout as the server archive (tools/free-content):
 * `LISEZ-MOI.txt`, `ATTRIBUTION.md`, `LICENCE-CC-BY-SA-4.0.txt`, `MANIFEST.json` (sha256 of every file), then `<pack id>/…`.
 * Works offline, without activation. Streams: only one file is in memory at a time. Entries are sorted and dated at a fixed day (same bytes every time).
 */
object FreeContentExporter {
    const val README = "LISEZ-MOI.txt"
    const val ATTRIBUTION = "ATTRIBUTION.md"
    const val LICENCE = "LICENCE-CC-BY-SA-4.0.txt"
    const val MANIFEST = "MANIFEST.json"
    const val NOT_EXPORTED = "NON-EXPORTES.txt"

    class Selection(val exportable: List<FreePack>, val skipped: List<SkippedPack>)

    /** Splits the packs: only explicitly CC BY-SA tagged and not reserved ones are exportable. Everything else is listed with its reason. */
    fun select(packs: List<FreePack>, families: LotFamilies = LotFamilies { null }): Selection {
        val ok = ArrayList<FreePack>(); val no = ArrayList<SkippedPack>()
        for (p in packs.sortedBy { it.id }) {
            val reserved = p.lotId?.let { families.of(it) } == LotFamily.RESERVED
            when {
                reserved -> no += SkippedPack(p.id, p.license, SkipReason.RESERVED)
                p.license.isNullOrBlank() -> no += SkippedPack(p.id, null, SkipReason.UNTAGGED)
                !FreeLicense.isCcBySa(p.license) -> no += SkippedPack(p.id, p.license, SkipReason.OTHER_LICENSE)
                else -> ok += p
            }
        }
        return Selection(ok, no)
    }

    /**
     * @param strict true = a pack that may not be exported makes the export fail ([FreeExportRefused]) instead of being listed in `NON-EXPORTES.txt`
     * @param progress (bytes done, bytes total) of the content, never decreasing, ends with done == total
     * @param cancelled polled between files and chunks: true throws [FreeExportCancelled]
     */
    fun export(
        source: FreeContentSource, out: OutputStream, families: LotFamilies = LotFamilies { null }, strict: Boolean = false,
        date: String = "", progress: (Long, Long) -> Unit = { _, _ -> }, cancelled: () -> Boolean = { false },
    ): FreeExportResult {
        val sel = select(source.packs(), families)
        if (strict && sel.skipped.isNotEmpty()) throw FreeExportRefused("Export refusé : " + sel.skipped.joinToString(" ; ") { it.message() })
        if (sel.exportable.isEmpty()) throw FreeExportEmpty()

        // pass 1: validate paths, hash, total
        data class Item(val pack: FreePack, val path: String, val size: Long, val sha: String)
        val items = ArrayList<Item>()
        val seen = HashSet<String>()
        for (p in sel.exportable) {
            if (!SAFE_ID.matches(p.id)) throw FreeExportRefused("Identifiant de contenu invalide : « ${p.id} ».")
            val paths = ArrayList<Item>()
            for (f in p.files) {
                if (cancelled()) throw FreeExportCancelled()
                if (!safePath(f.path)) throw FreeExportRefused("Chemin de fichier invalide dans « ${p.id} » : « ${f.path} ».")
                if (!seen.add("${p.id}/${f.path}")) throw FreeExportRefused("Fichier en double dans « ${p.id} » : « ${f.path} ».")
                paths += Item(p, f.path, f.bytes.size.toLong(), sha256(f.bytes))
            }
            items += paths.sortedBy { it.path }
        }
        val readme = readme(sel, date).toByteArray(Charsets.UTF_8)
        val attribution = attribution(sel).toByteArray(Charsets.UTF_8)
        val licence = LICENCE_TEXT.toByteArray(Charsets.UTF_8)
        val manifest = manifest(sel, items.map { Triple("${it.pack.id}/${it.path}", it.size, it.sha) }).toByteArray(Charsets.UTF_8)
        val notExported = if (sel.skipped.isEmpty()) null else notExported(sel).toByteArray(Charsets.UTF_8)
        val head = listOfNotNull(README to readme, ATTRIBUTION to attribution, LICENCE to licence, MANIFEST to manifest, notExported?.let { NOT_EXPORTED to it })
        val total = head.sumOf { it.second.size.toLong() } + items.sumOf { it.size }

        // pass 2: write
        val counting = CountingOutputStream(out)
        val zip = ZipOutputStream(counting); zip.setLevel(6)
        var done = 0L; var last = -1L
        fun tick() { if (done != last) { last = done; progress(done, total) } }
        progress(0, total); last = 0
        for ((n, b) in head) { put(zip, n, b); done += b.size; tick() }
        for (p in sel.exportable) {
            for (f in p.files.sortedBy { it.path }) {
                if (cancelled()) throw FreeExportCancelled()
                put(zip, "${p.id}/${f.path}", f.bytes); done += f.bytes.size; tick()
            }
        }
        zip.finish(); zip.flush()
        if (done != total) throw FreeExportException("Le contenu a changé pendant l'export : réessayez.")
        tick()
        return FreeExportResult(sel.exportable.map { it.id }, sel.skipped, items.size + head.size, total, counting.count)
    }

    /** Estimated size of the archive (uncompressed content) for the free-space check. */
    fun contentBytes(source: FreeContentSource, families: LotFamilies = LotFamilies { null }): Long =
        select(source.packs(), families).exportable.sumOf { p -> p.files.sumOf { it.bytes.size.toLong() } } + 40_000L

    // ---- texts ----
    private fun readme(sel: Selection, date: String) = buildString {
        append("CastBridge — contenus libres (licence ${FreeLicense.NAME})\n")
        if (date.isNotBlank()) append("Archive du $date\n")
        append("\nCette archive rassemble les contenus de CastBridge-TV placés sous licence ${FreeLicense.NAME} (${FreeLicense.URL}).\n")
        append("Vous pouvez les copier, les partager et les modifier, à condition de citer les auteurs (voir $ATTRIBUTION) et de partager vos modifications sous la même licence.\n")
        append("Elle fonctionne sans clé d'activation et sans connexion Internet.\n\n")
        append("Contenu : ${sel.exportable.size} paquet(s), un dossier par paquet.\n")
        for (p in sel.exportable) append(" - ${p.id} (version ${p.version}) : ${p.title}\n")
        append("\n$MANIFEST donne l'empreinte SHA-256 de chaque fichier.\n")
        if (sel.skipped.isNotEmpty()) append("Des contenus n'ont pas été exportés : voir $NOT_EXPORTED.\n")
    }

    private fun attribution(sel: Selection) = buildString {
        append("# Attribution\n\nContenus libres de CastBridge, licence [${FreeLicense.NAME}](${FreeLicense.URL}).\n\n")
        append("| Paquet | Version | Licence |\n|---|---|---|\n")
        for (p in sel.exportable) append("| ${p.id} | ${p.version} | ${p.license?.trim()} |\n")
        append("\nLes sources et auteurs de chaque média sont indiqués dans les fichiers du paquet (par exemple `media.json`).\n")
    }

    private fun notExported(sel: Selection) = buildString {
        append("Contenus non exportés\n\nCes contenus ne sont pas inclus : seuls les contenus explicitement sous licence ${FreeLicense.NAME} sont exportés.\n\n")
        for (s in sel.skipped) append(" - ${s.message()}\n")
    }

    private fun manifest(sel: Selection, files: List<Triple<String, Long, String>>) = buildString {
        append("{\n \"format\": 1,\n \"license\": \"CC-BY-SA-4.0\",\n \"packs\": [")
        append(sel.exportable.joinToString(",") { "\n  {\"id\": ${q(it.id)}, \"version\": ${it.version}, \"license\": ${q(it.license.orEmpty().trim())}}" })
        append("\n ],\n \"files\": [")
        append(files.joinToString(",") { "\n  {\"path\": ${q(it.first)}, \"bytes\": ${it.second}, \"sha256\": \"${it.third}\"}" })
        append("\n ]\n}\n")
    }

    // ---- helpers ----
    private val SAFE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
    private fun safePath(p: String) = p.isNotEmpty() && p.length <= 240 && !p.startsWith("/") && !p.contains('\\') && !p.contains('\u0000') && p.split('/').none { it.isEmpty() || it == "." || it == ".." }
    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** Fixed entry date (2026-01-01 in the local zone, so the DOS time is the same everywhere). */
    private val FIXED_TIME: Long = Calendar.getInstance().apply { clear(); set(2026, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis

    private fun put(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val e = ZipEntry(name); e.time = FIXED_TIME
        zip.putNextEntry(e); zip.write(bytes); zip.closeEntry()
    }

    private class CountingOutputStream(private val o: OutputStream) : OutputStream() {
        var count = 0L
        override fun write(b: Int) { o.write(b); count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { o.write(b, off, len); count += len }
        override fun flush() = o.flush()
        override fun close() = o.close()
    }

    private val LICENCE_TEXT = """
Licence Creative Commons Attribution - Partage dans les Mêmes Conditions 4.0 International (CC BY-SA 4.0)

Vous êtes autorisé à partager (copier, distribuer, communiquer) et adapter (remixer, transformer, créer à partir du contenu) pour toute utilisation, y compris commerciale, aux conditions suivantes :
 - Attribution : vous devez créditer l'auteur, intégrer un lien vers la licence et indiquer si des modifications ont été effectuées.
 - Partage dans les mêmes conditions : si vous modifiez ce contenu, vous devez diffuser vos contributions sous la même licence.
 - Pas de restrictions complémentaires : vous ne pouvez pas appliquer de conditions légales ou de mesures techniques qui restreindraient légalement les autres.

Texte juridique complet : ${FreeLicense.URL.replace("/deed.fr", "/legalcode.fr")}
Résumé : ${FreeLicense.URL}
""".trimIndent() + "\n"

}
