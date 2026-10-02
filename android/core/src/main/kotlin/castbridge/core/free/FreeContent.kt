package castbridge.core.free

import castbridge.core.lots.LotId

/** A file of a pack: [path] is relative to the pack folder (forward slashes, no « .. »). */
class FreeFile(val path: String, val bytes: ByteArray)

/**
 * A content pack that can be exported. [license] is the tag found in the pack metadata (null = not specified).
 * [files] must be re-iterable (the exporter reads it twice: hashes, then writing). [lotId] links the pack to the lots registry (reserved lots are never exported).
 */
class FreePack(
    val id: String, val version: Int, val license: String?, val title: String = id,
    val lotId: LotId? = null, val files: Sequence<FreeFile>,
)

/** Where the packs come from (the app's embedded resources on the TV, a fake source in tests). */
interface FreeContentSource {
    fun packs(): List<FreePack>
}

enum class SkipReason { UNTAGGED, OTHER_LICENSE, RESERVED }

/** A pack that is NOT exported, with the French reason. */
data class SkippedPack(val id: String, val license: String?, val reason: SkipReason) {
    fun message(): String = when (reason) {
        SkipReason.UNTAGGED -> "« $id » : licence non précisée"
        SkipReason.OTHER_LICENSE -> "« $id » : licence « $license » autre que CC BY-SA"
        SkipReason.RESERVED -> "« $id » : contenu réservé (lot enregistré comme non libre)"
    }
}

class FreeExportResult(val exported: List<String>, val skipped: List<SkippedPack>, val files: Int, val bytes: Long, val zipBytes: Long) {
    /** « N contenus non exportés : licence non précisée » (French, one line), empty when nothing was skipped. */
    fun skippedLine(): String {
        if (skipped.isEmpty()) return ""
        val groups = skipped.groupBy { it.reason }
        val parts = ArrayList<String>()
        groups[SkipReason.UNTAGGED]?.let { parts += "${it.size} licence non précisée" }
        groups[SkipReason.OTHER_LICENSE]?.let { parts += "${it.size} autre licence" }
        groups[SkipReason.RESERVED]?.let { parts += "${it.size} contenu réservé" }
        return "${skipped.size} contenu${if (skipped.size > 1) "s" else ""} non exporté${if (skipped.size > 1) "s" else ""} : " + parts.joinToString(", ")
    }
}

/** The export cannot be done (French message). [FreeExportRefused] = a pack that may not be exported was forced through the strict mode. */
open class FreeExportException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
class FreeExportRefused(message: String) : FreeExportException(message)
class FreeExportCancelled : FreeExportException("Export annulé.")
class FreeExportEmpty : FreeExportException("Aucun contenu libre embarqué dans cette version de CastBridge-TV : rien à exporter.")

/** CC BY-SA tags accepted (the version is part of the tag; NC / ND and other licences never match). */
object FreeLicense {
    private val ok = setOf("cc-by-sa-4.0", "cc-by-sa-3.0", "cc-by-sa")
    fun normalize(tag: String): String = tag.trim().lowercase().replace(Regex("[\\s_]+"), "-")
    fun isCcBySa(tag: String?): Boolean = tag != null && normalize(tag) in ok
    const val NAME = "CC BY-SA 4.0"
    const val URL = "https://creativecommons.org/licenses/by-sa/4.0/deed.fr"
}
