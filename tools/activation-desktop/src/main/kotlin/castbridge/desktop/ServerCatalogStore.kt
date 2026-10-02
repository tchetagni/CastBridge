package castbridge.desktop

import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.ServerBundleCatalog
import castbridge.core.lots.SignedBundleCatalog
import castbridge.core.net.HttpLite
import castbridge.core.update.UpdateKeys
import java.io.File

/**
 * The bundle catalogue imported FROM THE SERVER (`catalogue-serveur`, GUI button « Mettre à jour depuis le serveur »): downloaded over HTTPS,
 * signature verified with the update keys, then kept in `<dossier>/bundles-catalog.json`. Offline-first: nothing is fetched except on request;
 * `--catalogue serveur` (CLI) and the GUI use the kept copy, verified again each time.
 */
object ServerCatalogStore {
    const val NAME = "bundles-catalog.json"
    /** The value of `--catalogue` that means « the kept server copy ». */
    const val KEYWORD = "serveur"

    fun file(home: File) = File(home, NAME)

    fun keys(extra: List<String> = emptyList()): List<String> = (UpdateKeys.PUBLIC_KEYS + extra).filter { it.isNotBlank() }.distinct()

    /** Downloads, verifies, then (only then) replaces the kept copy. A failure keeps the previous one (the exception message is French). */
    fun update(home: File, baseUrl: String?, keys: List<String>, get: (String) -> HttpLite.Response = { HttpLite(userAgent = "CastBridge-desktop").request("GET", it) }): SignedBundleCatalog.Verified {
        val f = file(home)
        val kept = if (f.isFile) SignedBundleCatalog.generatedAtOf(f.readText()) else null
        val v = ServerBundleCatalog.fetch(baseUrl, keys, kept, get)
        home.mkdirs()
        val tmp = File(home, "$NAME.tmp"); tmp.writeText(v.json); if (!tmp.renameTo(f)) { f.writeText(v.json); tmp.delete() }
        return v
    }

    /** The kept copy, verified again; throws [SignedBundleCatalog.Refused] if there is none or it no longer verifies. */
    fun readVerified(home: File, keys: List<String>): SignedBundleCatalog.Verified {
        val f = file(home)
        if (!f.isFile) throw SignedBundleCatalog.Refused("Aucun catalogue du serveur enregistré : lancez « catalogue-serveur » (ou le bouton « Mettre à jour depuis le serveur »)")
        return SignedBundleCatalog.verify(f.readText(), keys)
    }

    fun readCatalog(home: File, keys: List<String>): BundleCatalog = readVerified(home, keys).catalog
}
