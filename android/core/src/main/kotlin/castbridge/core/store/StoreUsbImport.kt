package castbridge.core.store

import castbridge.core.lots.LotNames
import castbridge.core.lots.TvLotStore
import castbridge.core.util.BoundedRead
import castbridge.core.store.StoreFiles.Doc
import castbridge.core.store.StoreFiles.Installed
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Locale

/**
 * Import du catalogue de la Boutique depuis une clé USB (w17-12) : la TV (CastBridge-TV) voit la boutique sans téléphone ni réseau. Aucun format neuf, aucune vérification en moins :
 * - les DEUX documents signés ([Doc.LOTS], [Doc.BUNDLES]) passent par [StoreFiles.install], le même chemin que `POST /api/store/catalog` (plafonds 256 Ko / 64 Ko AVANT la signature,
 *   signature avec les clés de confiance embarquées [keys], anti-retour sur `generatedAt`, verrou, écriture atomique) ;
 * - un lot (`castbridge-lot-<fonction>-<portée>-v<N>.lot`, preuve `<lot>.json` à côté, sinon `lots-catalog.json` de la clé) n'est JAMAIS posé autrement que par
 *   [TvLotStore.receive] puis [TvLotStore.installReceived] (signature de la preuve, taille, SHA-256, budget, anti-retour) ; un lot loué reste inaccessible sans contrat : l'import ne
 *   donne aucun droit, il ne fait que déposer des fichiers que la TV vérifie déjà.
 *
 * CHEMINS (liste blanche, un seul niveau) : on ne lit que les fichiers dont le NOM, mis en minuscules (FAT/exFAT rendent la casse à leur guise), est exactement un des quatre
 * modèles ci-dessous ; tout autre fichier est ignoré (compté, jamais ouvert). Un fichier de ce nom qui n'est pas un fichier ordinaire, ou qui est un lien symbolique, ou dont le chemin
 * réel sort du dossier, est REFUSÉ (jamais suivi) ; un nom contenant `..` est refusé ; deux noms égaux à la casse près sont refusés tous les deux. Aucune écriture hors des dossiers
 * `store/` et `lots/` de la TV ; la clé n'est jamais modifiée. Contenu : UTF-8 strict, sans octet de contrôle (les zéros de remplissage sont refusés), lu en bornant la taille.
 * Jamais d'exception vers l'appelant : tout est dans le [Result].
 *
 * @param lots null = pas de gestionnaire de lots : les lots sont refusés, les catalogues passent. Une seule instance de [files] par dossier (voir [StoreFiles]).
 */
class StoreUsbImport(private val files: StoreFiles, private val lots: TvLotStore?, private val keys: List<String>) {
    enum class Kind { DOC, LOT }
    enum class Status { ACCEPTED, UNCHANGED, REFUSED, FAILED }

    /** Un fichier traité : [name] = nom canonique (minuscules), [message] = phrase française pour l'écran. */
    data class Item(val kind: Kind, val name: String, val status: Status, val message: String)

    /** [ignored] = fichiers hors liste blanche, jamais ouverts. */
    data class Result(val items: List<Item>, val ignored: Int) {
        val empty get() = items.isEmpty()
        fun summary(): String {
            if (items.isEmpty()) return NONE
            val ok = items.count { it.status == Status.ACCEPTED }; val same = items.count { it.status == Status.UNCHANGED }; val bad = items.count { it.status == Status.REFUSED || it.status == Status.FAILED }
            return buildList {
                if (ok > 0) add("$ok accepté${if (ok > 1) "s" else ""}")
                if (same > 0) add("$same déjà à jour")
                if (bad > 0) add("$bad refusé${if (bad > 1) "s" else ""}")
            }.joinToString(", ")
        }
    }

    /** Importe le dossier [dir] (null, absent ou pas un dossier = « Aucun catalogue sur la clé »). */
    fun import(dir: File?): Result {
        if (dir == null || !dir.isDirectory) return Result(emptyList(), 0)
        val entries = try { dir.listFiles() } catch (e: SecurityException) { null } ?: return Result(emptyList(), 0)
        val byCanon = entries.groupBy { it.name.lowercase(Locale.ROOT) }
        val items = ArrayList<Item>(); var ignored = 0
        val picked = ArrayList<Pair<String, File>>()                          // (nom canonique, fichier) retenus pour traitement
        for ((canon, group) in byCanon.entries.sortedBy { it.key }) {
            val kind = kindOf(canon)
            if (kind == null) {
                if (canon.contains("..")) items += refused(Kind.DOC, display(canon), "Nom de fichier suspect : refusé.")
                else ignored += group.size
                continue
            }
            if (group.size > 1) { items += refused(kind, canon, "Deux fichiers portent ce nom à la casse près : lequel est le bon ? refusé."); continue }
            picked += canon to group.single()
        }
        val docs = picked.filter { kindOf(it.first) == Kind.DOC }
        val lotFiles = picked.filter { kindOf(it.first) == Kind.LOT && !it.first.endsWith(LotNames.PROOF_SUFFIX) }
        val proofs = picked.filter { kindOf(it.first) == Kind.LOT && it.first.endsWith(LotNames.PROOF_SUFFIX) }.associate { it.first to it.second }
        for ((canon, f) in docs) items += importDoc(canon, f, dir)
        val catalogProof by lazy { picked.firstOrNull { it.first == Doc.LOTS.fileName }?.let { readText(it.second, dir, Doc.LOTS.maxBytes) as? Read.Ok }?.text }
        for ((canon, f) in lotFiles) items += importLot(canon, f, dir, proofs[canon + LotNames.PROOF_SUFFIX], dir) { catalogProof }
        // une preuve sans son lot est simplement ignorée (comptée) : elle ne fait rien seule
        ignored += proofs.keys.count { p -> lotFiles.none { it.first + LotNames.PROOF_SUFFIX == p } }
        return Result(items, ignored)
    }

    // ------------------------------------------------------------------ documents

    private fun importDoc(canon: String, f: File, dir: File): Item {
        val doc = Doc.values().first { it.fileName == canon }
        val text = when (val r = readText(f, dir, doc.maxBytes)) { is Read.Ok -> r.text; is Read.Bad -> return refused(Kind.DOC, canon, "${doc.label} : ${r.why}") }
        return when (val r = files.install(doc, text, keys)) {
            is Installed.Written -> Item(Kind.DOC, canon, Status.ACCEPTED, "${doc.label} : enregistré (${castbridge.core.lots.SignedBundleCatalog.dateFr(r.generatedAt)}).")
            Installed.Unchanged -> Item(Kind.DOC, canon, Status.UNCHANGED, "${doc.label} : déjà à jour.")
            is Installed.Refused -> refused(Kind.DOC, canon, r.message)
            is Installed.Failed -> Item(Kind.DOC, canon, Status.FAILED, r.message)
        }
    }

    // ------------------------------------------------------------------ lots

    private fun importLot(canon: String, f: File, dir: File, proofFile: File?, root: File, catalogProof: () -> String?): Item {
        val store = lots ?: return refused(Kind.LOT, canon, "Les lots ne sont pas pris en charge ici : refusé.")
        guard(f, dir)?.let { return refused(Kind.LOT, canon, it) }
        val size = f.length()
        if (size <= 0) return refused(Kind.LOT, canon, "Lot vide : refusé.")
        if (size > store.maxBytes) return refused(Kind.LOT, canon, "Lot trop gros pour la TV (${size / 1024} Ko) : refusé.")
        val proof = when {
            proofFile != null -> when (val r = readText(proofFile, root, MAX_PROOF_BYTES)) { is Read.Ok -> r.text; is Read.Bad -> return refused(Kind.LOT, canon, "Preuve signée du lot : ${r.why}") }
            else -> catalogProof() ?: return refused(Kind.LOT, canon, "Lot sans preuve signée (fichier « $canon.json » ou catalogue des lots signé absent) : refusé.")
        }
        // copie par morceaux dans la boîte de réception de la TV : mêmes refus que l'envoi du téléphone (nom, taille, espace disque, conflit)
        try {
            Files.newInputStream(f.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                var offset = store.received(canon)
                if (offset in 1..size) input.skip(offset) else offset = 0
                val buf = ByteArray(CHUNK)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    val chunk = if (n == buf.size) buf else buf.copyOf(n)
                    when (val c = store.receive(canon, offset, size, chunk)) {
                        is TvLotStore.Chunk.Received -> offset = c.bytes
                        is TvLotStore.Chunk.Conflict -> return refused(Kind.LOT, canon, "Reste d'un envoi précédent incompatible : réessayez.")
                        is TvLotStore.Chunk.Refused -> return refused(Kind.LOT, canon, "${c.reason.replaceFirstChar { it.uppercase() }} : refusé.")
                    }
                }
            }
        } catch (e: IOException) { return Item(Kind.LOT, canon, Status.FAILED, "Lecture de la clé impossible : réessayez.") }
        return when (val r = store.installReceived(canon, proof)) {
            is TvLotStore.Result.Ok -> Item(Kind.LOT, canon, Status.ACCEPTED, "Lot installé.")
            is TvLotStore.Result.Refused -> refused(Kind.LOT, canon, "${r.reason.replaceFirstChar { it.uppercase() }} : refusé.")
        }
    }

    // ------------------------------------------------------------------ chemins et lecture

    private sealed class Read { class Ok(val text: String) : Read(); class Bad(val why: String) : Read() }

    /** null si [f] est un fichier ordinaire, directement dans [dir], qui n'est pas un lien ; sinon la phrase de refus. */
    private fun guard(f: File, dir: File): String? {
        val p = f.toPath()
        if (Files.isSymbolicLink(p)) return "Lien symbolique : refusé (jamais suivi)."
        if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) return "Ce n'est pas un fichier ordinaire : refusé."
        val real = try { f.canonicalFile } catch (e: IOException) { return "Chemin illisible : refusé." }
        if (real.parentFile != dir.canonicalFile || real.name.lowercase(Locale.ROOT) != f.name.lowercase(Locale.ROOT)) return "Le chemin sort du dossier de la clé : refusé."
        return null
    }

    /** Lit [f] (au plus [max] octets, vérifiés avant ET pendant la lecture) en UTF-8 strict ; refuse les octets de contrôle (zéros de remplissage compris). */
    private fun readText(f: File, dir: File, max: Long): Read {
        guard(f, dir)?.let { return Read.Bad(it) }
        if (f.length() > max) return Read.Bad("document trop volumineux (${(f.length() + 1023) / 1024} Ko, au plus ${max / 1024} Ko) : refusé")
        val bytes = try { Files.newInputStream(f.toPath(), LinkOption.NOFOLLOW_LINKS).use { BoundedRead.readAll(it, max.toInt()) } }
        catch (e: BoundedRead.TooLarge) { return Read.Bad("document trop volumineux : refusé") }
        catch (e: IOException) { return Read.Bad("illisible sur la clé : refusé") }
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (e: CharacterCodingException) { return Read.Bad("texte invalide (UTF-8 attendu) : refusé") }
        if (text.any { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }) return Read.Bad("octets de contrôle ou de remplissage dans le fichier : refusé")
        return Read.Ok(text)
    }

    private fun refused(kind: Kind, name: String, message: String) = Item(kind, name, Status.REFUSED, message)
    private fun display(canon: String) = canon.take(64).filter { it.code in 0x20..0x7e }

    companion object {
        const val NONE = "Aucun catalogue sur la clé"
        private const val CHUNK = 1 shl 20
        private const val MAX_PROOF_BYTES = StoreCatalog.MAX_LOTS_CATALOG_BYTES

        private val LOT_NAME = Regex("^castbridge-lot-[a-z0-9]{1,32}-[a-z0-9][a-z0-9-]{0,31}-v\\d{1,9}\\.lot(\\.json)?$")

        /** DOC pour un des deux catalogues, LOT pour un lot ou sa preuve, null = hors liste blanche. [name] est déjà en minuscules. */
        private fun kindOf(name: String): Kind? = when {
            name == Doc.LOTS.fileName || name == Doc.BUNDLES.fileName -> Kind.DOC
            LOT_NAME.matches(name) && LotNames.parseFileName(name.removeSuffix(LotNames.PROOF_SUFFIX)) != null -> Kind.LOT
            else -> null
        }

        /** `<racine>/CastBridge/store/` retrouvé sans tenir compte de la casse (FAT/exFAT) ; null s'il manque ou si un niveau est un lien symbolique. */
        fun storeDir(root: File): File? {
            fun child(parent: File, name: String): File? {
                val c = parent.listFiles()?.filter { it.name.equals(name, ignoreCase = true) }?.singleOrNull() ?: return null
                return c.takeIf { Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(it.toPath()) }
            }
            return child(root, "CastBridge")?.let { child(it, "store") }
        }
    }
}
