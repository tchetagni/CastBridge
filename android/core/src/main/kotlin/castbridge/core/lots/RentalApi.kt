package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.owner.Activation
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import java.io.File

/**
 * TV side routes for RENTED lots (an [ApiExtension] behind the TV's authentication: PIN or trusted-phone token), additive to [TvLotApi]:
 * - GET  /api/rental                               the rentals of this TV: state, time left, lots held, SUPER_UNLIMITED or not
 * - POST /api/rental/install?name=&contract=       body = the signed catalog. The lot file (SEALED for this TV by [RentalKeys], sent in chunks through /api/lots/upload) is opened with
 *                                                  the contract's key from the rental safe, then handed to the normal [TvLotStore] (signed catalog, plain size and SHA-256, budget), then
 *                                                  registered as rented so that the autonomous sweep deletes it at the end of the rental. 422 + French reason otherwise.
 * - POST /api/rental/sweep                         runs the sweep now (the phone calls it at reconnection; the TV also runs it at start and every few hours)
 *
 * The family of a lot (free / reserved) is the SEALING party's duty (the issuing tools refuse a free lot before they sign): only the holder of the rental master can seal a lot that opens with
 * this TV's contract key, so a lot that opens here is reserved by construction. A trial sample is still refused here.
 * There is deliberately NO route that fetches anything from the Internet: the TV stays offline.
 */
class RentalApi(
    private val store: TvLotStore, private val ledger: RentalLedger, private val vault: RentalVault, private val activations: () -> List<Activation>,
    private val sweeper: RentalSweeper? = null, private val families: LotFamilies = LotFamilies { LotFamily.RESERVED },
) : ApiExtension {
    override fun wantsBody(path: String) = path == "/api/rental/install"

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (path != "/api/rental/install" || method != "POST") return null
        val name = params["name"] ?: return err(400, "name manquant")
        val contract = params["contract"] ?: return err(400, "contract manquant")
        val (id, version) = LotNames.parseFileName(name) ?: return err(400, "nom de lot invalide")
        val part = store.partFile(name)
        if (!part.isFile) return err(422, "lot non reçu")
        val key = vault.getKey(contract) ?: return err(422, "Clé de location absente ou location terminée : installez d'abord l'activation de la location sur cette TV")
        val plain = RentalKeys.open(key, id, version, part.readBytes())
            ?: return err(422, "Lot illisible : il n'est pas chiffré pour cette TV et cette location, ou il a été altéré")
        // the signed catalog vouches for the PLAIN lot (size, SHA-256): put it back where the store expects the received file
        val tmp = File(part.parentFile, part.name + ".plain"); tmp.writeBytes(plain)
        if (!tmp.renameTo(part)) { part.delete(); tmp.renameTo(part) }
        val meta = runCatching { LotManifest.parse(String(body, Charsets.UTF_8)).lots.firstOrNull { it.id == id && it.version == version } }.getOrNull()
        return when (val r = store.installReceived(name, String(body, Charsets.UTF_8))) {
            is TvLotStore.Result.Refused -> err(422, r.reason)
            is TvLotStore.Result.Ok -> {
                val why = if (meta == null) "le catalogue signé ne contient pas ce lot" else ledger.markRented(contract, id, meta, families)
                if (why != null) { store.remove(id); err(422, why) } else ApiReply(200, """{"installed":true,"rented":true}""")
            }
        }
    }

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (!path.startsWith("/api/rental")) return null
        return when {
            path == "/api/rental" && method == "GET" -> ApiReply(200, statusJson())
            path == "/api/rental/sweep" && method == "POST" -> sweeper?.let { s ->
                val r = s.sweep(SweepTrigger.PHONE_RECONNECT)
                ApiReply(200, JsonLite.write(linkedMapOf("ended" to r.endedNow, "keysDestroyed" to r.keysDestroyed, "lotsRemoved" to r.lotsRemoved.map(LotNames::key), "notices" to r.notices)))
            } ?: err(404, "balayage indisponible")
            else -> null
        }
    }

    fun statusJson(): String {
        val acts = activations()
        val statuses = ledger.status(acts)
        return JsonLite.write(linkedMapOf(
            "superUnlimited" to RentalEngine.superUnlimited(acts),
            "rentals" to statuses.map { s ->
                linkedMapOf("contract" to s.key, "product" to s.contract.productId, "bundles" to s.contract.bundleIds, "state" to s.state.name, "usable" to s.usable,
                    "remainingMs" to s.remainingMs, "message" to s.message, "endsAt" to s.contract.endsAt,
                    "lots" to (ledger.rec(s.key)?.lots?.toList() ?: emptyList()), "keyInSafe" to vault.hasKey(s.key))
            }))
    }

    private fun err(code: Int, msg: String) = ApiReply(code, "{\"error\":${JsonLite.quote(msg)}}")
}
