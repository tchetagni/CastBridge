package castbridge.core.store

import castbridge.core.lots.LotFamilies
import castbridge.core.net.JsonLite
import castbridge.core.store.StoreFiles.Doc
import castbridge.core.store.StoreFiles.Installed
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply

/**
 * Routes `/api/store*` de la TV (w17-04), une [ApiExtension] derrière l'authentification existante (PIN ou téléphone de confiance) que la TV branche d'une ligne et que le
 * harnais de parcours branche dans `TvSim`. Drapeau [enabled] éteint : aucune route n'est servie (`null` partout, la chaîne répond 404 comme avant).
 *
 * - `GET  /api/store` : la vitrine ([StoreView.json], lisible par [StoreView.parse]) + `enabled`, `catalogAt {lots, bundles, works}`, `kidActive`, `trial`, `pending`, `degraded`.
 * - `POST /api/store/catalog` : corps `{"lots": doc, "bundles": doc, "works": doc?}` (objets JSON, ou textes JSON), au plus [MAX_CATALOG_BODY] octets (413 au-delà) ; chaque document
 *   passe par [StoreFiles.install] (taille 256 Ko / 64 Ko, signature, anti-retour). Réponse 200 `{accepted, unchanged, refused {doc: phrase}}` ; 503 si l'écriture échoue.
 * - `GET  /api/store/requests` : les demandes gardées (en attente, puis les dernières acquittées).
 * - `POST /api/store/requests/ack?nonce=&state=ACCEPTED|REFUSED` : acquittement idempotent (200 APPLIED/SAME, 409 contraire, 404 inconnu, 503 écriture).
 * - `POST /api/store/request` : corps = texte `castbridge-rent-request-v1` d'origine `phone`, au plus [MAX_REQUEST_BODY] octets (413) ; mêmes refus que sur la TV (422, 409 pour un doublon).
 *
 * HORLOGE : aucune horloge ici. L'heure vient de `facts(store).nowMs` (l'intégration passe l'heure JUGÉE : `RentalEngine.judge(tvClock, wall).now`), et [requests] a son propre
 * `now` injecté (la même heure jugée). INSTANCES : une seule [StoreApi], une seule [RentRequests] et un seul [StoreFiles] par dossier (singletons du processus ; la TV les crée une fois).
 * Le `Store` est reconstruit à chaque `GET` depuis les fichiers (pas de cache : quelques ms sur les fixtures, mesuré dans le test, assertion < 50 ms). Une demande n'est jamais un droit.
 * Aucune route ne télécharge quoi que ce soit.
 *
 * @param facts les faits de la TV pour une vitrine donnée (le store rebuild lui est donné) ; @param families familles de lots ; @param tvId identité de la TV (une demande d'une autre TV est refusée).
 */
class StoreApi(
    private val files: StoreFiles, private val keys: List<String>, private val facts: (StoreCatalog.Store) -> StoreView.Facts, private val requests: RentRequests,
    private val enabled: () -> Boolean, private val families: () -> LotFamilies, private val tvId: String,
) : ApiExtension {
    override fun wantsBody(path: String) = enabled() && (path == "/api/store/catalog" || path == "/api/store/request")

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (!enabled()) return null
        if (method != "POST") return null
        return when (path) {
            "/api/store/catalog" -> catalog(body)
            "/api/store/request" -> deposit(body)
            else -> null
        }
    }

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (!enabled()) return null
        if (path != "/api/store" && !path.startsWith("/api/store/")) return null
        return when (path) {
            "/api/store" -> if (method == "GET") showcase() else err(405, "GET attendu")
            "/api/store/requests" -> if (method == "GET") list() else err(405, "GET attendu")
            "/api/store/requests/ack" -> if (method == "POST") ack(params) else err(405, "POST attendu")
            "/api/store/catalog", "/api/store/request" -> err(405, "POST avec un corps attendu")
            else -> err(404, "route inconnue")
        }
    }

    // ------------------------------------------------------------------ lecture

    private fun loadStore(): StoreCatalog.Store {
        val fam = families()
        val lots = files.read(Doc.LOTS) ?: return StoreCatalog.build(emptyList(), null, fam)
        val bundles = files.read(Doc.BUNDLES)
        return try { StoreCatalog.fromJson(lots, bundles, fam) }
        catch (e: StoreCatalog.Refused) {
            // catalogue des bouquets illisible : mode dégradé ; catalogue des lots illisible : boutique vide
            try { StoreCatalog.fromJson(lots, null, fam) } catch (e2: StoreCatalog.Refused) { StoreCatalog.build(emptyList(), null, fam) }
        }
    }

    /** Les faits du moment, après expiration et rapprochement des demandes ; [StoreView.Facts.pendingRequests] vient de la file. */
    private class Moment(val store: StoreCatalog.Store, val facts: StoreView.Facts)

    private fun moment(): Moment {
        val store = loadStore()
        val base = facts(store)
        requests.expire(base.nowMs)
        requests.reconcile(base.rentals)
        val live = requests.pendingBundles()
        val dates = requests.entries().filter { it.state == RentRequests.State.PENDING && it.request.bundle in live }.associate { it.request.bundle to it.createdAt }
        return Moment(store, base.copy(pendingRequests = dates))
    }

    private fun pendingCount() = requests.entries().count { it.state == RentRequests.State.PENDING }

    private fun showcase(): ApiReply {
        val m = moment()
        val view = JsonLite.obj(StoreView.json(StoreView.decide(m.facts)))
        val out = LinkedHashMap<String, Any?>(view)
        out["enabled"] = true
        out["catalogAt"] = linkedMapOf("lots" to m.store.catalogAtLots, "bundles" to m.store.catalogAtBundles, "works" to files.keptAt(Doc.WORKS))
        out["kidActive"] = m.facts.kidActive
        out["trial"] = m.facts.trialTv
        out["pending"] = pendingCount()
        out["degraded"] = m.store.degraded || requests.degraded
        return ApiReply(200, JsonLite.write(out))
    }

    private fun list(): ApiReply {
        val m = moment()
        val alias = m.store.items.associate { it.id to it.alias }
        val items = requests.entries().map { e ->
            val r = e.request
            linkedMapOf("nonce" to r.nonce, "state" to e.state.name, "code" to alias[r.bundle]?.let { a -> runCatching { r.shortCode(a) }.getOrNull() }, "bundle" to r.bundle, "choice" to r.choice,
                "kind" to r.kind.wire, "period" to r.period, "origin" to r.origin.wire, "createdAt" to e.createdAt, "decidedAt" to e.decidedAt, "text" to r.canonical())
        }
        return ApiReply(200, JsonLite.write(linkedMapOf("items" to items, "pending" to pendingCount(), "degraded" to requests.degraded)))
    }

    // ------------------------------------------------------------------ catalogues

    private fun catalog(body: ByteArray): ApiReply {
        if (body.size > MAX_CATALOG_BODY) return err(413, "Envoi trop volumineux (${(body.size + 1023) / 1024} Ko, au plus ${MAX_CATALOG_BODY / 1024} Ko) : refusé")
        val o = try { JsonLite.obj(String(body, Charsets.UTF_8)) } catch (e: Exception) { return err(400, "Corps illisible : le catalogue attendu est un objet JSON") }
        val sent = listOf("lots" to Doc.LOTS, "bundles" to Doc.BUNDLES, "works" to Doc.WORKS).filter { o[it.first] != null }
        if (sent.isEmpty()) return err(400, "Aucun catalogue dans l'envoi (lots, bundles ou works attendu)")
        val accepted = ArrayList<String>(); val unchanged = ArrayList<String>(); val refused = LinkedHashMap<String, String>()
        var failure: String? = null
        for ((key, doc) in sent) {
            val v = o[key]
            val text = if (v is String) v else JsonLite.write(v)
            when (val r = files.install(doc, text, keys)) {
                is Installed.Written -> accepted += key
                Installed.Unchanged -> unchanged += key
                is Installed.Refused -> refused[key] = r.message
                is Installed.Failed -> { failure = r.message; refused[key] = r.message }
            }
        }
        val answer = JsonLite.write(linkedMapOf("accepted" to accepted, "unchanged" to unchanged, "refused" to refused) + if (failure != null) mapOf("error" to failure) else emptyMap())
        return ApiReply(if (failure != null) 503 else 200, answer)
    }

    // ------------------------------------------------------------------ demandes

    private fun deposit(body: ByteArray): ApiReply {
        if (body.size > MAX_REQUEST_BODY) return err(413, "Demande trop volumineuse (au plus $MAX_REQUEST_BODY octets) : refusée")
        val parsed = RentRequest.parse(String(body, Charsets.UTF_8))
        val req = (parsed as? RentRequest.Parsed.Ok)?.request ?: return err(400, (parsed as RentRequest.Parsed.Bad).message)
        if (req.origin != RentRequest.Origin.PHONE) return err(400, "Seul le téléphone dépose une demande par cette route : demande ignorée.")
        if (req.tv != tvId) return err(409, "Cette demande vise une autre TV : elle est ignorée.")
        val m = moment()
        val item = m.store.items.firstOrNull { it.id == req.bundle }
        val fam = families()
        val facts = RentRequests.Facts(m.facts.trialTv, m.facts.kidActive, item?.shelf ?: StoreCatalog.Shelf.AUTRES, item?.lots?.map { fam.of(it.id) } ?: emptyList(), m.facts.rentals,
            m.facts.maxConcurrent, m.facts.pilotEndMs, RentRequest.Origin.PHONE)
        return when (val c = requests.create(req.bundle, req.choice, facts, extend = req.kind == RentRequest.Kind.EXTEND)) {
            is RentRequests.Created.Ok -> {
                val r = c.entry.request
                ApiReply(200, JsonLite.write(linkedMapOf("nonce" to r.nonce, "state" to c.entry.state.name, "createdAt" to c.entry.createdAt,
                    "code" to item?.alias?.let { a -> runCatching { r.shortCode(a) }.getOrNull() })))
            }
            is RentRequests.Created.Refused -> ApiReply(when (c.reason) {
                Refusal.STORAGE -> 503
                Refusal.MALFORMED -> 400
                Refusal.DUPLICATE, Refusal.QUEUE_FULL -> 409
                else -> 422
            }, JsonLite.write(linkedMapOf("error" to c.message, "reason" to c.reason.name)))
        }
    }

    private fun ack(params: Map<String, String>): ApiReply {
        val nonce = params["nonce"] ?: return err(400, "nonce manquant")
        val answer = when (params["state"]) { "ACCEPTED" -> RentRequests.Answer.ACCEPTED; "REFUSED" -> RentRequests.Answer.REFUSED; else -> return err(400, "state attendu : ACCEPTED ou REFUSED") }
        val r = requests.ack(nonce, answer)
        val json = JsonLite.write(linkedMapOf("outcome" to r.outcome.name, "state" to r.state?.name))
        return when (r.outcome) {
            RentRequests.Outcome.APPLIED, RentRequests.Outcome.SAME -> ApiReply(200, json)
            RentRequests.Outcome.CONFLICT -> ApiReply(409, withError(json, "Cette demande a déjà reçu une autre réponse."))
            RentRequests.Outcome.UNKNOWN -> ApiReply(404, withError(json, "Demande inconnue ou trop ancienne."))
            RentRequests.Outcome.STORAGE -> ApiReply(503, withError(json, "Impossible d'enregistrer la réponse : réessayez."))
        }
    }

    private fun withError(json: String, message: String) = JsonLite.write(JsonLite.obj(json) + ("error" to message))
    private fun err(code: Int, msg: String) = ApiReply(code, "{\"error\":${JsonLite.quote(msg)}}")

    companion object {
        /** Corps maximal de `POST /api/store/catalog` : 512 Ko (les documents ont chacun leur plafond, 256 Ko et 64 Ko). Le plafond du serveur de la TV (`ReceiverServer.MAX_EXT_BODY`) est de 4 Mio : il passe d'abord. */
        const val MAX_CATALOG_BODY = 512 * 1024
        /** Corps maximal d'une demande de location : 1 Ko (l'analyse refuse déjà plus de 1024 caractères). */
        const val MAX_REQUEST_BODY = 1024
    }
}
