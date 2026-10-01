package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str

/**
 * TRIAL-MANIFEST.json as written by tools/trial-edition (docs/TRIAL-EDITION.md): the exact list of trial lots and files with their
 * sizes, per sub-category. The tool already refuses to write an invalid manifest; [TrialBudget.verify] re-checks the same hard
 * rules in the app's own language (a second line of defence, run by the tests and usable by the server before publishing).
 */
data class TrialFile(val path: String, val bytes: Long)
data class TrialLot(val feature: String, val scope: String, val fullFeature: String, val fullScope: String, val bytes: Long,
                    val files: List<TrialFile>, val items: List<String>, val bundles: List<String>)
data class TrialSub(val key: String, val category: String, val selectedItems: Int, val totalItems: Int, val selectedBytes: Long, val fullBytes: Long)
data class TrialManifest(val valid: Boolean, val capBytes: Long, val totalBytes: Long, val inventory: String,
                         val lots: List<TrialLot>, val subcategories: List<TrialSub>, val bundles: BundleCatalog) {
    companion object {
        fun parse(json: String): TrialManifest {
            val m = JsonLite.obj(json)
            fun maps(v: Any?): List<Map<String, Any?>> = (v as? List<*>).orEmpty().map {
                @Suppress("UNCHECKED_CAST") (it as? Map<String, Any?> ?: throw JsonLite.ParseError("object expected"))
            }
            fun strs(v: Any?) = (v as? List<*>).orEmpty().map { it.toString() }
            val lots = maps(m["lots"]).map { l ->
                val full = l["fullLot"] as? Map<*, *> ?: throw JsonLite.ParseError("fullLot")
                TrialLot(l.str("feature") ?: "", l.str("scope") ?: "", full["feature"].toString(), full["scope"].toString(), l.long("bytes") ?: 0L,
                    maps(l["files"]).map { TrialFile(it.str("path") ?: "", it.long("bytes") ?: 0L) }, strs(l["items"]), strs(l["bundles"]))
            }
            val subs = maps(m["subcategories"]).map {
                TrialSub(it.str("key") ?: "", it.str("category") ?: "", it.int("selectedItems") ?: 0, it.int("totalItems") ?: 0, it.long("selectedBytes") ?: 0L, it.long("fullBytes") ?: 0L)
            }
            return TrialManifest(m["valid"] == true, m.long("capBytes") ?: 0L, m.long("totalBytes") ?: 0L, m.str("inventory") ?: "", lots, subs, BundleCatalog.parse(json))
        }
    }
}

object TrialBudget {
    const val CAP_BYTES = 100L shl 20
    const val TEXT_LOT_MAX = 3L shl 20
    const val MEDIA_LOT_MAX = 100L shl 20

    /**
     * The hard rules; an empty list = the manifest is acceptable. [fullItems] (optional): for each FULL lot key ("learn:cm2") the
     * "kind/id" of everything the full lot holds; given, every trial item must exist there with the same identifier (stability).
     */
    fun verify(m: TrialManifest, cap: Long = CAP_BYTES, fullItems: Map<String, Set<String>>? = null): List<String> {
        val out = ArrayList<String>()
        if (!m.valid) out += "le manifeste se déclare invalide"
        val total = m.lots.sumOf { it.bytes }
        if (total != m.totalBytes) out += "totalBytes (${m.totalBytes}) ne correspond pas à la somme des lots ($total)"
        if (total > cap) out += "plafond dépassé : $total octets > $cap"
        if (m.capBytes > CAP_BYTES) out += "plafond déclaré supérieur à 100 Mo"
        for (s in m.subcategories) if (s.selectedItems < 1) out += "sous-catégorie vide : ${s.key}"
        for (l in m.lots) {
            val id = LotId(l.feature, l.scope)
            if (!LotNames.valid(id) || !LotEditions.isTrialScope(l.scope)) out += "nom de lot d'essai invalide : ${l.feature}:${l.scope}"
            if (LotEditions.fullOf(id) != LotId(l.fullFeature, l.fullScope)) out += "lot ${l.scope} : le lot complet ${l.fullFeature}:${l.fullScope} ne correspond pas"
            if (l.files.sumOf { it.bytes } != l.bytes) out += "lot ${l.scope} : la somme des fichiers ne correspond pas à sa taille"
            if (l.bytes > (if (l.feature == "langmedia") MEDIA_LOT_MAX else TEXT_LOT_MAX)) out += "lot d'essai trop gros : ${l.feature}:${l.scope}"
            if (l.items.size != l.items.toSet().size) out += "lot ${l.scope} : identifiant en double"
            if (l.bundles.isEmpty()) out += "lot ${l.scope} : dans aucun bouquet payant"
            for (b in l.bundles) if ("${l.fullFeature}:${l.fullScope}" !in (m.bundles.find(b)?.lots ?: emptySet())) out += "lot ${l.scope} : bouquet $b ne contient pas le lot complet"
            val full = fullItems?.get("${l.fullFeature}:${l.fullScope}")
            if (fullItems != null) {
                if (full == null) out += "lot ${l.scope} : lot complet inconnu"
                else l.items.filter { it !in full }.forEach { out += "lot ${l.scope} : $it n'existe pas dans le lot complet (identifiant instable)" }
            }
        }
        return out
    }

    /** Same manifest content = same fingerprint (determinism check for tools and tests). */
    fun fingerprint(m: TrialManifest): String = LotHash.sha256Hex(
        m.lots.sortedBy { it.feature + ":" + it.scope }.joinToString("\n") { "${it.feature}:${it.scope}|${it.bytes}|${it.items.sorted().joinToString(",")}" }.toByteArray(Charsets.UTF_8))
}
