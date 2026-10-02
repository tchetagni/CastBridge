package castbridge.core.owner

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.sales.PriceGrid
import castbridge.core.sales.Receipt
import castbridge.core.sales.SalesLedger

/**
 * Replays tools/activation/agent-vectors.json (`castbridge-agent-vectors-v1`): the common vectors of the field-agent delegation (mandate, ticketed activation, mandate-signed registry
 * events, price grid, sales ledger, receipt), replayed by the core and, later, by the other implementations (Java server, Python tools). A separate file: no vector of
 * tools/activation/test-vectors.json is touched. The keys and devices in the file are TEST data derived from public strings. Returns the failures (empty = all good).
 */
object AgentVectors {
    const val FORMAT = "castbridge-agent-vectors-v1"
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private class Env(val keys: Map<String, Map<String, Any?>>, val devices: Map<String, Fingerprints>) {
        @Suppress("UNCHECKED_CAST")
        fun trusted(names: Collection<*>) = names.map { n -> keys.getValue(n as String).let { k -> TrustedKey(k.str("kid")!!, k.str("publicKey")!!, (k["scopes"] as List<String>).map { KeyScope.valueOf(it) }.toSet()) } }
        fun kid(name: String) = keys.getValue(name).str("kid")!!
        fun signer(name: String) = Ed25519Signer(hex(keys.getValue(name).str("seed")!!))
        fun pub(name: String) = keys.getValue(name).str("publicKey")!!
        @Suppress("UNCHECKED_CAST")
        fun ring(c: Map<String, Any?>) = KeyRing(trusted(c["ring"] as List<*>), (c["revokedKeys"] as? List<*>).orEmpty().map { kid(it as String) }.toSet())
    }

    @Suppress("UNCHECKED_CAST")
    private fun env(root: Map<String, Any?>): Env {
        val keys = (root["keys"] as List<Map<String, Any?>>).associateBy { it.str("name")!! }
        val devices = (root["devices"] as List<Map<String, Any?>>).associate { d ->
            d.str("name")!! to Fingerprints((d["fingerprints"] as Map<String, String>).entries.associate { FactorKind.valueOf(it.key) to it.value })
        }
        return Env(keys, devices)
    }

    @Suppress("UNCHECKED_CAST")
    fun run(json: String): List<String> {
        val root = JsonLite.obj(json)
        if (root.str("format") != FORMAT) return listOf("format inconnu")
        val e = env(root)
        val fails = ArrayList<String>()
        for (c in root["cases"] as List<Map<String, Any?>>) {
            val id = c.str("id")!!
            val problem = try {
                when (c.str("type")) {
                    "build-delegation" -> buildDelegation(c, e)
                    "delegation" -> delegation(c, e)
                    "ticketed" -> ticketed(c, e)
                    "price-grid" -> priceGrid(c, e)
                    "ledger" -> ledger(c, e)
                    "receipt" -> receipt(c)
                    "registry-delegated" -> registry(c, e)
                    else -> "type de vecteur inconnu"
                }
            } catch (x: Exception) { "exception ${x::class.simpleName}: ${x.message}" }
            if (problem != null) fails += "$id : $problem"
        }
        return fails
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildDelegation(c: Map<String, Any?>, e: Env): String? {
        val i = c["input"] as Map<String, Any?>
        val token = Delegation.issue(e.signer(c.str("signer")!!), i.long("at")!!, i.long("seq")!!, i.str("nonce")!!, e.pub(i.str("agent")!!), i.str("name")!!, i.long("maxKeyDays")!!.toInt(), i.long("maxSales")!!.toInt(),
            (i["bundles"] as List<String>), validityDays = i.long("validityDays")!!.toInt(), confirmOrders = i["confirmOrders"] == true, sellVouchers = i["sellVouchers"] == true, maxConfirmXafPerDay = i.long("maxConfirmXafPerDay")?.toInt())
        val exp = (c["expect"] as Map<String, Any?>).str("token")
        return if (token == exp) null else "jeton différent"
    }

    @Suppress("UNCHECKED_CAST")
    private fun delegation(c: Map<String, Any?>, e: Env): String? {
        val seq = SeqState((c["lastSeq"] as Map<String, Any?>).entries.associate { en -> en.key.split('/').joinToString("/") { e.kid(it) } to (en.value as Number).toLong() })       // "owner/agent" -> "ownerKid/agentKid"
        val r = Delegation.verify(c.str("token")!!, e.ring(c), RevocationState(), c.long("now")!!, seq)
        val exp = c["expect"] as Map<String, Any?>
        val got = if (r is DelegationResult.Accepted) "accepted" else (r as DelegationResult.Refused).reason.name
        if (got != exp.str("result")) return "attendu ${exp.str("result")}, obtenu $got"
        if (r is DelegationResult.Accepted && (r.delegation.name != exp.str("name") || r.delegation.agent != e.kid(exp.str("agent")!!))) return "mandat accepté mais nom ou agent différent"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun ticketed(c: Map<String, Any?>, e: Env): String? {
        val ring = e.ring(c); val dev = e.devices.getValue(c.str("device")!!); val now = c.long("now")!!
        val result = if (c.str("verifier") == "plain") ActivationVerifier(ring).verify(c.str("line")!!, dev, now) else DelegatedVerifier(ring).verify(c.str("line")!!, dev, now).result
        val exp = c["expect"] as Map<String, Any?>
        val got = if (result is ActivationResult.Accepted) "accepted" else (result as ActivationResult.Rejected).reason.name
        return if (got == exp.str("result")) null else "attendu ${exp.str("result")}, obtenu $got"
    }

    @Suppress("UNCHECKED_CAST")
    private fun priceGrid(c: Map<String, Any?>, e: Env): String? {
        val exp = c["expect"] as Map<String, Any?>
        val got = try {
            val v = PriceGrid.verify(c.str("json")!!, (c["publicKeys"] as List<String>).map { e.pub(it) }, c.str("notOlderThan"))
            for (p in (exp["priceOf"] as? List<Map<String, Any?>>).orEmpty()) if (v.priceOf(p.str("item")!!, p.long("days")!!.toInt()) != p.long("price")?.toInt()) return "prix de ${p.str("item")} différent"
            if (exp.long("count") != null && v.items().size.toLong() != exp.long("count")) return "nombre de lignes différent"
            "ok"
        } catch (x: PriceGrid.Refused) { "refused" }
        return if (got == exp.str("result")) null else "attendu ${exp.str("result")}, obtenu $got"
    }

    @Suppress("UNCHECKED_CAST")
    private fun ledger(c: Map<String, Any?>, e: Env): String? {
        val entries = (c["entries"] as List<String>).map { SalesLedger.Entry.parse(it) ?: return "entrée illisible" }
        val r = SalesLedger.chain(entries, e.pub(c.str("pub")!!))
        val exp = c["expect"] as Map<String, Any?>
        val (got, seq) = when (r) {
            is SalesLedger.ChainResult.Ok -> "ok" to null
            is SalesLedger.ChainResult.Gap -> "gap" to r.seq
            is SalesLedger.ChainResult.Diverged -> "diverged" to r.seq
            is SalesLedger.ChainResult.BadSignature -> "bad-signature" to r.seq
        }
        if (got != exp.str("result") || seq != exp.long("seq")) return "attendu ${exp.str("result")}@${exp.long("seq")}, obtenu $got@$seq"
        if (r is SalesLedger.ChainResult.Ok && exp.long("balance") != null && SalesLedger.balance(entries, exp.long("remitted") ?: 0L) != exp.long("balance")) return "solde différent"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun receipt(c: Map<String, Any?>): String? {
        val exp = c["expect"] as Map<String, Any?>
        val code = Receipt.code(c.str("agent")!!, c.long("seq")!!, c.str("device")!!, c.long("at")!!)
        if (code != exp.str("code")) return "code attendu ${exp.str("code")}, obtenu $code"
        for (t in (c["typed"] as? List<Map<String, Any?>>).orEmpty()) if (Receipt.parse(t.str("input")!!) != t.str("expect")) return "saisie « ${t.str("input")} » : résultat différent"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun registry(c: Map<String, Any?>, e: Env): String? {
        val ring = e.ring(c)
        val keys = Delegation.replayKeys((c["delegations"] as List<String>), ring)
        val events = (c["events"] as List<Map<String, Any?>>).map { LicenseEvent.fromMap(it) ?: return "événement illisible" }
        val state = LicenseBook.replay(events, ring.withDelegated(keys))
        val exp = c["expect"] as Map<String, Any?>
        val rejected = state.rejected.associate { (id, why) -> id to why.name }
        val wanted = (exp["rejected"] as List<Map<String, Any?>>).associate { events[it.long("index")!!.toInt()].id to it.str("reason")!! }
        if (rejected != wanted) return "rejetés attendus $wanted, obtenus $rejected"
        val licenses = state.licenses.keys.sorted()
        return if (licenses == (exp["licenses"] as List<String>).sorted()) null else "licences attendues ${exp["licenses"]}, obtenues $licenses"
    }
}
