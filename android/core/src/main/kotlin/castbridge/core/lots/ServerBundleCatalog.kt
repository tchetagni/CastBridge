package castbridge.core.lots

import castbridge.core.connect.ServerUrl
import castbridge.core.net.HttpLite

/**
 * « Mettre à jour le catalogue depuis le serveur » (owner console, phone and desktop): downloads GET /api/v1/catalog/bundles over
 * HTTPS and verifies the signature ([SignedBundleCatalog]) BEFORE anything is stored. Only called on the owner's request (offline-first:
 * never at start-up). Any failure is a French [SignedBundleCatalog.Refused]; the caller keeps the catalogue it already has.
 */
object ServerBundleCatalog {
    const val PATH = "/api/v1/catalog/bundles"

    fun fetch(baseUrl: String?, publicKeys: List<String>, notOlderThan: String? = null,
              get: (String) -> HttpLite.Response = { HttpLite(userAgent = "CastBridge-owner").request("GET", it) }): SignedBundleCatalog.Verified {
        val base = ServerUrl.normalize(baseUrl ?: ServerUrl.DEFAULT) ?: throw SignedBundleCatalog.Refused("Adresse du serveur invalide : ${ServerUrl.problem(baseUrl)}")
        val r = try { get(base + PATH) } catch (e: Exception) {
            throw SignedBundleCatalog.Refused("Serveur injoignable (${e.javaClass.simpleName}) : le catalogue enregistré est conservé.")
        }
        when {
            r.code == 404 -> throw SignedBundleCatalog.Refused("Le serveur n'a pas encore de catalogue des bouquets : le catalogue enregistré est conservé.")
            r.code != 200 -> throw SignedBundleCatalog.Refused("Le serveur a refusé (${HttpLite.errorMessage(r)}) : le catalogue enregistré est conservé.")
        }
        return SignedBundleCatalog.verify(r.body, publicKeys, notOlderThan)
    }
}
