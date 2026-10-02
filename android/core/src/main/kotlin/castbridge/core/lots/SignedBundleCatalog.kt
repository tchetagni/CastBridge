package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.str
import castbridge.core.update.Ed25519
import java.util.Base64

/**
 * The bundle catalogue (bouquets) as the SERVER publishes it (GET /api/v1/catalog/bundles) and as the owner's tools import it.
 * It is signed OFFLINE by the owner (python3 tools/trial-edition/trial_edition.py sign-catalog) with the same Ed25519 key as the
 * app updates and the lot catalogues ([castbridge.core.update.UpdateKeys]); the server only relays the file. A tool that issues
 * rentals (the owner console, the desktop tool) refuses a catalogue that is unsigned or altered: the rental duration of a bundle
 * is a commercial rule and must not be forgeable by whoever sits between the server and the phone.
 *
 * Signed text (line-based, rebuilt here from the parsed fields; keep identical to trial_edition.canonical_bundles and
 * backend BundleCatalogApiTest): `castbridge-bundle-catalog-v1`, `generatedAt=...`, then per bundle (sorted by id)
 * `bundle=id|type|rawBytes|rentalDays|sha256(title)|lot,lot` (lots sorted).
 */
object SignedBundleCatalog {
    const val FORMAT = "castbridge-bundle-catalog-v1"
    private val AT = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")

    /** A catalogue that passed every check. [json] is the text to keep (it holds the signature). */
    class Verified(val catalog: BundleCatalog, val generatedAt: String, val keyId: String?, val json: String) {
        /** "JJ/MM/AAAA" of [generatedAt]. */
        val dateFr: String get() = dateFr(generatedAt)
    }

    /** Refused catalogue; the message is French and meant for the screen. */
    class Refused(message: String) : Exception(message)

    fun dateFr(generatedAt: String): String = if (generatedAt.length >= 10) "${generatedAt.substring(8, 10)}/${generatedAt.substring(5, 7)}/${generatedAt.substring(0, 4)}" else generatedAt

    fun canonicalPayload(generatedAt: String, bundles: List<Bundle>): String = buildList {
        add(FORMAT)
        add("generatedAt=$generatedAt")
        bundles.sortedBy { it.id }.forEach {
            add("bundle=${it.id}|${it.type}|${it.rawBytes}|${it.rentalDays}|${LotHash.sha256Hex(it.title.toByteArray(Charsets.UTF_8))}|${it.lots.sorted().joinToString(",")}")
        }
    }.joinToString("\n")

    /**
     * Parses and verifies [json] against [publicKeys] (base64 raw keys). [notOlderThan] is the generatedAt of the catalogue already kept:
     * an older signed catalogue is refused (a replay of an old file would bring back old rental durations).
     */
    fun verify(json: String, publicKeys: List<String>, notOlderThan: String? = null): Verified {
        val m = try { JsonLite.obj(json) } catch (e: Exception) { throw Refused("Catalogue illisible : ${e.message}") }
        val sig = m.str("signature")?.takeIf { it.isNotBlank() && it != "UNSIGNED" } ?: throw Refused("Catalogue non signé : refusé")
        val at = m.str("generatedAt")?.takeIf { AT.matches(it) } ?: throw Refused("Catalogue sans date de génération valide : refusé")
        val catalog = try { BundleCatalog.parse(json) } catch (e: Exception) { throw Refused("Catalogue illisible : ${e.message}") }
        if (catalog.bundles.isEmpty()) throw Refused("Catalogue vide")
        if (catalog.bundles.any { it.lots.isEmpty() || it.id.isBlank() }) throw Refused("Catalogue invalide : bouquet sans lot")
        if (catalog.bundles.map { it.id }.toSet().size != catalog.bundles.size) throw Refused("Catalogue invalide : bouquet en double")
        val keys = publicKeys.filter { it.isNotBlank() }
        if (keys.isEmpty()) throw Refused("Aucune clé de vérification dans cette application : catalogue refusé")
        val msg = canonicalPayload(at, catalog.bundles).toByteArray(Charsets.UTF_8)
        val ok = try {
            val raw = Base64.getDecoder().decode(sig)
            keys.any { k -> try { Ed25519.verify(Base64.getDecoder().decode(k.trim()), msg, raw) } catch (e: IllegalArgumentException) { false } }
        } catch (e: IllegalArgumentException) { false }
        if (!ok) throw Refused("Signature du catalogue invalide (fichier modifié ou signé avec une autre clé) : refusé")
        if (notOlderThan != null && AT.matches(notOlderThan) && at < notOlderThan)
            throw Refused("Le serveur propose un catalogue plus ancien (${dateFr(at)}) que celui déjà enregistré (${dateFr(notOlderThan)}) : refusé")
        return Verified(catalog, at, m.str("keyId"), json)
    }

    /** The generatedAt of a kept catalogue, or null if it carries none (an old file import). Never throws. */
    fun generatedAtOf(json: String): String? = runCatching { JsonLite.obj(json).str("generatedAt") }.getOrNull()?.takeIf { AT.matches(it) }
}
