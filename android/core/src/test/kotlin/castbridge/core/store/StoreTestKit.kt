package castbridge.core.store

import castbridge.core.lots.Bundle
import castbridge.core.lots.Kit
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.lots.SignedBundleCatalog
import castbridge.core.net.JsonLite
import java.security.Signature
import java.util.Base64

/** Documents signés de TEST pour les tests de la Boutique (clé de test de [Kit], jamais une vraie clé). */
object StoreTestKit {
    fun lot(feature: String, scope: String, bytes: Long = 1000) = LotMeta(LotId(feature, scope), 1, bytes, "0".repeat(63) + "1", "$feature $scope", 0)

    fun lotsJson(at: String = "2026-10-01T08:00:00Z", lots: List<LotMeta> = listOf(lot("learn", "cm2", 2_000_000), lot("quiz", "cm2", 1_000_000))): String =
        Kit.sign(lots, feature = null, at = at).toJson()

    /** Catalogue des bouquets signé par la clé de test. */
    fun bundlesJson(at: String = "2026-09-28T10:00:00Z", bundles: List<Bundle> = listOf(Bundle("classe-cm2", "classe", setOf("learn:cm2", "quiz:cm2"), "Classe CM2", 0, 30))): String {
        val sig = Signature.getInstance("Ed25519").run { initSign(Kit.pair.private); update(SignedBundleCatalog.canonicalPayload(at, bundles).toByteArray(Charsets.UTF_8)); sign() }
        val doc = linkedMapOf("generatedAt" to at, "signature" to Base64.getEncoder().encodeToString(sig), "bundles" to bundles.map {
            linkedMapOf("id" to it.id, "type" to it.type, "title" to it.title, "rawBytes" to it.rawBytes, "rentalDays" to it.rentalDays, "lots" to it.lots.sorted())
        })
        return JsonLite.write(doc)
    }

    /** Corps de `POST /api/store/catalog` : les documents en objets JSON. */
    fun body(lots: String? = null, bundles: String? = null, works: String? = null): ByteArray =
        JsonLite.write(linkedMapOf<String, Any?>("lots" to lots?.let { JsonLite.obj(it) }, "bundles" to bundles?.let { JsonLite.obj(it) }, "works" to works?.let { JsonLite.obj(it) })
            .filterValues { it != null }).toByteArray()
}
