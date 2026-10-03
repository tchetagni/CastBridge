package castbridge.core.store

import castbridge.core.lots.LotManifest
import castbridge.core.lots.SignedBundleCatalog
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.SafeFile
import java.io.File
import java.io.IOException

/**
 * Les fichiers de la Boutique sous un dossier injecté (`files/store/` sur la TV) : `lots-catalog.json`, `bundles-catalog.json`, `works-catalog.json` et `requests.json`
 * (ce dernier est écrit par [FileRentRequestStore], pas ici). Écriture atomique `SafeFile` (tmp + rename + `.bak`), lecture tolérante (fichier absent ou illisible = rien, jamais d'exception).
 *
 * [install] est LE chemin d'écriture d'un catalogue, réutilisé tel quel par l'import USB (w17-12) : plafond de taille AVANT toute vérification de signature (économie de CPU),
 * signature Ed25519 avec les clés injectées (aucune clé = tout refusé), anti-retour sur `generatedAt`, puis écriture, le tout sous un verrou : deux envois concurrents ne se
 * croisent pas, le second voit la date du premier. Une seule instance par dossier (singleton du processus), comme [RentRequests].
 */
class StoreFiles(private val dir: File) {
    /** Les documents signés gardés ; [maxBytes] = plafond de taille ([StoreCatalog]) ; [label] = nom dans les phrases. */
    enum class Doc(val fileName: String, val maxBytes: Long, val label: String) {
        LOTS("lots-catalog.json", StoreCatalog.MAX_LOTS_CATALOG_BYTES, "Catalogue des lots"),
        BUNDLES("bundles-catalog.json", StoreCatalog.MAX_BUNDLES_CATALOG_BYTES, "Catalogue des bouquets"),
        WORKS("works-catalog.json", StoreCatalog.MAX_LOTS_CATALOG_BYTES, "Catalogue des œuvres"),
    }

    /** Résultat de [install] ; les messages sont en français et destinés à l'écran. [Failed] = écriture impossible (erreur serveur, pas un refus du document). */
    sealed class Installed {
        data class Written(val generatedAt: String) : Installed()
        object Unchanged : Installed()
        data class Refused(val message: String) : Installed()
        data class Failed(val message: String) : Installed()
    }

    private val lock = Any()
    private val at = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")

    fun file(doc: Doc): File = File(dir, doc.fileName)
    fun requestsFile(): File = File(dir, "requests.json")

    private fun valid(text: String) = runCatching { JsonLite.obj(text) }.isSuccess

    /** Le texte gardé du document (ou de sa copie `.bak`), ou null s'il est absent ou illisible. */
    fun read(doc: Doc): String? = SafeFile.read(file(doc), ::valid)?.text

    /** Le `generatedAt` du document gardé, ou null. */
    fun keptAt(doc: Doc): String? = read(doc)?.let { runCatching { JsonLite.obj(it).str("generatedAt") }.getOrNull() }?.takeIf { at.matches(it) }

    /** Vérifie [json] avec [keys] (clés publiques base64) puis l'écrit si sa date est plus récente que celle du document gardé. */
    fun install(doc: Doc, json: String, keys: List<String>): Installed = synchronized(lock) {
        if (doc == Doc.WORKS) return Installed.Refused("${doc.label} : non pris en charge par cette version de CastBridge-TV.")
        StoreCatalog.checkSize(json, doc.maxBytes)?.let { return Installed.Refused("${doc.label} : ${it.replaceFirstChar { c -> c.lowercase() }}") }
        val (newAt, why) = if (doc == Doc.LOTS) verifyLots(json, keys) else try { SignedBundleCatalog.verify(json, keys).generatedAt to null } catch (e: SignedBundleCatalog.Refused) { null to e.message }
        if (newAt == null) return Installed.Refused(why ?: "${doc.label} : refusé")
        val kept = keptAt(doc)
        if (kept != null) {
            if (newAt == kept) return Installed.Unchanged
            if (newAt < kept) return Installed.Refused("Le téléphone propose un catalogue plus ancien (${SignedBundleCatalog.dateFr(newAt)}) que celui déjà gardé par la TV (${SignedBundleCatalog.dateFr(kept)}) : refusé")
        }
        try { SafeFile.write(file(doc), json, ::valid) } catch (e: IOException) { return Installed.Failed("Impossible d'enregistrer le catalogue sur la TV : réessayez.") }
        Installed.Written(newAt)
    }

    /** (date, null) si le catalogue des lots est lisible, signé par une des clés et daté ; sinon (null, phrase de refus). */
    private fun verifyLots(json: String, keys: List<String>): Pair<String?, String?> {
        val m = try { LotManifest.parse(json) } catch (e: Exception) { return null to "Catalogue des lots illisible : ${e.message}" }
        if (m.signature.isBlank() || m.signature == "UNSIGNED") return null to "Catalogue des lots non signé : refusé"
        if (!at.matches(m.generatedAt)) return null to "Catalogue des lots sans date de génération valide : refusé"
        val usable = keys.filter { it.isNotBlank() }
        if (usable.isEmpty()) return null to "Aucune clé de vérification dans cette application : catalogue refusé"
        if (!m.signedByAny(usable)) return null to "Signature du catalogue des lots invalide (fichier modifié ou signé avec une autre clé) : refusé"
        return m.generatedAt to null
    }
}
