package castbridge.core.journey

import castbridge.core.lots.HttpTvTransport
import castbridge.core.lots.LotId
import castbridge.core.lots.LotStage
import castbridge.core.lots.RentalDelivery
import castbridge.core.lots.RentalDeliveryReport
import castbridge.core.lots.SealedLot
import castbridge.core.lots.TvLotApi
import castbridge.core.lots.TvManifest
import castbridge.core.lots.TvReply
import castbridge.core.net.JsonLite
import castbridge.core.store.StoreCatalog
import castbridge.core.store.StoreView
import java.io.IOException

/**
 * Le « téléphone » de la Boutique (CastBridge) en cœur pur : ce que w17-06 câblera en Android, dans l'ordre, par `HttpTvTransport` vers la VRAIE TV du parcours.
 *
 * Appels, dans l'ordre (le contrat que [PhoneStoreSim] impose à w17-06) :
 * 1. [relay] : `GET /api/store` (lit `catalogAt`), puis `POST /api/store/catalog` des seuls documents plus récents que ceux de la TV (un second contact n'envoie rien) ;
 * 2. [view] : la vitrine du téléphone = `StoreView.decide` sur les catalogues du téléphone + `GET /api/lots` (manifeste) + `GET /api/rental` (état des locations) ;
 * 3. [poll] : `GET /api/store/requests` (relève les demandes nées sur la TV ; une notification « La TV demande … » par demande en attente nouvelle) ;
 * 4. [ack] : `POST /api/store/requests/ack` (Confirmer ou Refuser) ;
 * 5. [deliver] : `RentalDelivery.deliver` (envoi du lot scellé par morceaux puis `/api/rental/install`) ; la TV rapproche ensuite la demande d'elle-même.
 *
 * [base] et [credential] sont lus à chaque appel : le test choisit ce que le téléphone « croit » (un PIN tourné entre deux contacts donne un 401, avalé AVEC un message).
 */
class PhoneStoreSim(private val clock: JourneyClock, private val base: () -> String, private val credential: () -> String) {
    /** Les catalogues que le téléphone détient (JSON signés), comme après son actualisation par Internet. */
    var lotsJson: String? = null
    var bundlesJson: String? = null

    /** Étape de chaque lot sur le téléphone (posée par le test : ce que `LotStatus` dirait). */
    val stages: MutableMap<LotId, LotStage> = LinkedHashMap()

    /** Notifications montrées au propriétaire (« La TV demande … »), une par demande en attente vue pour la première fois. */
    val notifications: MutableList<String> = ArrayList()
    private val notified = HashSet<String>()

    /** Messages affichés quand un contact échoue (401, liaison coupée…) : jamais avalés en silence, jamais de boucle. */
    val messages: MutableList<String> = ArrayList()

    private val transport get() = HttpTvTransport(base(), credential(), timeoutMs = 2_000)

    private fun call(method: String, path: String, params: Map<String, String> = emptyMap(), body: ByteArray? = null): TvReply = transport.call(method, path, params, body)

    /** Réponse du relais : ce que la TV a accepté / trouvé identique / refusé (document → phrase), ou l'échec (code 0 = liaison coupée). */
    data class Relay(val status: Int, val accepted: List<String>, val unchanged: List<String>, val refused: Map<String, String>, val message: String?)

    /** Le relais des catalogues : [onlyNewer] = n'envoyer que ce qui est plus récent que le catalogue de la TV ; sinon tout, de force. */
    fun relay(onlyNewer: Boolean = true): Relay {
        val at = try {
            val g = call("GET", "/api/store")
            if (g.status == 401) return fail(401, STALE_CODE)
            if (g.status != 200) return fail(g.status, "La TV refuse de donner sa Boutique (code ${g.status}).")
            (JsonLite.obj(g.json)["catalogAt"] as? Map<*, *>).orEmpty()
        } catch (e: IOException) { return fail(0, "La TV n'est pas à portée.") }
        val send = LinkedHashMap<String, Any?>()
        fun newer(key: String, json: String?): Boolean = json != null && (!onlyNewer || (JsonLite.obj(json)["generatedAt"] as? String).orEmpty() > (at[key] as? String).orEmpty())
        if (newer("lots", lotsJson)) send["lots"] = JsonLite.obj(lotsJson!!)
        if (newer("bundles", bundlesJson)) send["bundles"] = JsonLite.obj(bundlesJson!!)
        if (send.isEmpty()) return Relay(200, emptyList(), emptyList(), emptyMap(), null)
        return try {
            val r = call("POST", "/api/store/catalog", body = JsonLite.write(send).toByteArray())
            if (r.status != 200) return fail(r.status, if (r.status == 401) STALE_CODE else "La TV a refusé les catalogues (code ${r.status}).")
            val o = JsonLite.obj(r.json)
            Relay(200, strings(o["accepted"]), strings(o["unchanged"]), (o["refused"] as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value.toString() }, null)
        } catch (e: IOException) { fail(0, "La liaison avec la TV a été coupée pendant l'envoi des catalogues.") }
    }

    private fun fail(status: Int, message: String): Relay { messages += message; return Relay(status, emptyList(), emptyList(), emptyMap(), message) }
    private fun strings(v: Any?) = (v as? List<*>).orEmpty().map { it.toString() }

    /** Le manifeste des lots de la TV (`GET /api/lots`), ou null si illisible. */
    fun tvManifest(): TvManifest? = try { call("GET", "/api/lots").takeIf { it.status == 200 }?.let { TvManifest.parse(it.json) } } catch (e: IOException) { null }

    /** La vitrine du téléphone, par le cœur pur, depuis ses propres catalogues et ce que la TV dit (`GET /api/lots`, `GET /api/rental`). */
    fun view(granted: Set<String> = emptySet(), families: castbridge.core.lots.LotFamilies): StoreView.Screen {
        val store = StoreCatalog.fromJson(lotsJson!!, bundlesJson, families)
        val facts = StoreView.Facts(store, clock.now(), StoreView.Side.PHONE, tvManifest = tvManifest(), tvRentals = RentalDelivery(transport).status(), granted = granted, phoneStages = stages.toMap())
        return StoreView.decide(facts)
    }

    /** Une demande vue sur la TV. */
    data class Request(val nonce: String, val state: String, val code: String?, val bundle: String, val choice: String, val origin: String)

    /** Relève les demandes de la TV et notifie les nouvelles demandes en attente. */
    fun poll(): List<Request> {
        val r = try { call("GET", "/api/store/requests") } catch (e: IOException) { messages += "La TV n'est pas à portée."; return emptyList() }
        if (r.status != 200) { messages += "La TV refuse de donner les demandes (code ${r.status})."; return emptyList() }
        val items = (JsonLite.obj(r.json)["items"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }.map {
            Request(it["nonce"].toString(), it["state"].toString(), it["code"] as? String, it["bundle"].toString(), it["choice"].toString(), it["origin"].toString())
        }
        for (q in items) if (q.state == "PENDING" && notified.add(q.nonce)) notifications += "La TV demande ${q.code ?: q.bundle} : Confirmer ?"
        return items
    }

    /** Confirme ([accepted] = true) ou refuse une demande ; rend la réponse brute de la TV. */
    fun ack(nonce: String, accepted: Boolean = true): TvReply = call("POST", "/api/store/requests/ack", mapOf("nonce" to nonce, "state" to if (accepted) "ACCEPTED" else "REFUSED"))

    /** Un lot COMPLET (non scellé) poussé par le chemin normal : `POST /api/lots/upload` par morceaux puis `POST /api/lots/install` avec le catalogue signé comme preuve. */
    fun pushLot(name: String, data: ByteArray, proofJson: String): TvReply {
        var off = 0
        while (off < data.size) {
            val n = minOf(TvLotApi.CHUNK, data.size - off)
            val r = call("POST", "/api/lots/upload", mapOf("name" to name, "offset" to off.toString(), "total" to data.size.toString()), data.copyOfRange(off, off + n))
            if (r.status != 200) return r
            off += n
        }
        return call("POST", "/api/lots/install", mapOf("name" to name), proofJson.toByteArray())
    }

    /** La livraison réelle d'une location : envoi du lot scellé puis installation par la TV. */
    fun deliver(contract: String, catalogJson: String, lots: List<SealedLot>): RentalDeliveryReport = RentalDelivery(transport).deliver(contract, catalogJson, lots)

    private companion object { const val STALE_CODE = "La TV a changé de code : ressaisissez le code de la TV." }
}
