package castbridge.core.owner

import castbridge.core.lots.Right
import castbridge.core.net.JsonLite
import castbridge.core.owner.AgentFixtures.DAY
import castbridge.core.owner.AgentFixtures.T0
import castbridge.core.owner.AgentFixtures.key
import castbridge.core.sales.PriceGrid
import castbridge.core.sales.Receipt
import castbridge.core.sales.SalesLedger
import java.io.File
import kotlin.test.*

/**
 * tools/activation/agent-vectors.json (`castbridge-agent-vectors-v1`): the common vectors of the field-agent delegation, replayed by [AgentVectors] (and later by the Java and Python ports).
 * The keys are the TEST keys of test-vectors.json (copied, same seeds: derived from public strings, worth nothing) plus two agent keys; the amounts are made up. Regenerate with
 * `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*AgentVectorsTest*'` after an intentional format change.
 */
class AgentVectorsTest {
    private val now = T0 + DAY + 3_600_000L
    private val ownerRing = listOf("desk", "phone", "server")
    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private val mandate = AgentFixtures.delegation(bundles = listOf("tout"))
    private fun unchecked(owner: String = "desk", mutate: (Delegation) -> Delegation) = Delegation.sign(key(owner).signer, mutate(Delegation.decode(mandate)!!))

    // ---- cases ----
    private fun buildDelegationCases(): List<Map<String, Any?>> {
        fun case(id: String, why: String, input: Map<String, Any?>, token: String) = J("id" to id, "type" to "build-delegation", "description" to why, "signer" to "desk", "input" to input, "expect" to J("token" to token))
        val min = J("at" to T0, "seq" to T0, "nonce" to "a1a1a1a1a1a1a1a1", "agent" to "agent", "name" to "douala-akwa-01", "maxKeyDays" to 365, "maxSales" to 200, "bundles" to listOf("tout"), "validityDays" to 90)
        val w5 = J("at" to T0, "seq" to 7, "nonce" to "d4d4d4d4d4d4d4d4", "agent" to "agent2", "name" to "bonamoussadi-02", "maxKeyDays" to 90, "maxSales" to 50, "bundles" to listOf("maths", "classe-cm2"), "validityDays" to 30,
            "confirmOrders" to true, "sellVouchers" to true, "maxConfirmXafPerDay" to 50000)
        return listOf(
            case("build-delegation-minimal", "mandat minimal : lignes obligatoires seulement, maxRentalDays=0, ni maître ni clé X25519 de l'agent", min, mandate),
            case("build-delegation-w5-fields", "champs W5 : confirmOrders, sellVouchers, maxConfirmXafPerDay ; bouquets triés", w5,
                AgentFixtures.delegation(agent = "agent2", seq = 7, name = "bonamoussadi-02", maxKeyDays = 90, maxSales = 50, bundles = listOf("maths", "classe-cm2"), validityDays = 30, nonce = "d4d4d4d4d4d4d4d4",
                    confirmOrders = true, sellVouchers = true, maxConfirm = 50_000)),
        )
    }

    private fun delegationCases(): List<Map<String, Any?>> {
        fun case(id: String, why: String, token: String, result: String, at: Long = now, ring: List<String> = ownerRing, revoked: List<String> = emptyList(), last: Map<String, Long> = emptyMap(), name: String? = null, agent: String? = null) =
            J("id" to id, "type" to "delegation", "description" to why, "token" to token, "ring" to ring, "revokedKeys" to revoked, "lastSeq" to last, "now" to at,
                "expect" to if (result == "accepted") J("result" to result, "name" to name, "agent" to agent) else J("result" to result))
        val sig = mandate.split('.').let { (a, b, c) -> "$a.$b.${c.reversed()}" }
        return listOf(
            case("delegation-accepted", "mandat valide, clé du propriétaire avec DELEGATE", mandate, "accepted", name = "douala-akwa-01", agent = "agent"),
            case("delegation-accepted-w5", "mandat avec champs W5", AgentFixtures.delegation(confirmOrders = true, sellVouchers = true, maxConfirm = 20_000), "accepted", name = "douala-akwa-01", agent = "agent"),
            case("delegation-expired", "mandat périmé (91 jours plus tard)", mandate, "WINDOW_CLOSED", at = T0 + 91 * DAY),
            case("delegation-not-yet-valid", "mandat qui ne commence que dans 5 jours", unchecked { it.copy(issuedAt = T0, notBefore = T0 + 5 * DAY, expiresAt = T0 + 50 * DAY) }, "NOT_YET_VALID", at = T0),
            case("delegation-scope-outside-subset", "portée TRANSFER dans le mandat", unchecked { it.copy(scopes = setOf(KeyScope.ISSUE_PRODUCTION, KeyScope.TRANSFER)) }, "KEY_NOT_ALLOWED"),
            case("delegation-owner-key-without-delegate", "signé par une clé du propriétaire sans la portée DELEGATE", AgentFixtures.delegation("phone"), "KEY_NOT_ALLOWED"),
            case("delegation-agent-revoked", "l'agent est révoqué", mandate, "REVOKED_KEY", revoked = listOf("agent")),
            case("delegation-unknown-owner-key", "clé signataire inconnue de la TV", AgentFixtures.delegation("rogue"), "UNKNOWN_KEY"),
            case("delegation-stale-sequence", "seq plus ancienne que celle déjà acceptée", mandate, "STALE_SEQUENCE", last = mapOf("desk/agent" to T0 + 10)),
            case("delegation-rental-days-refused", "maxRentalDays=30 : les locations sont gérées en ligne (W5)", unchecked { it.copy(maxRentalDays = 30) }, "BAD_DELEGATION"),
            case("delegation-bad-signature", "signature altérée", sig, "BAD_SIGNATURE"),
        )
    }

    private fun ticketedCases(): List<Map<String, Any?>> {
        fun case(id: String, why: String, line: String, result: String, revoked: List<String> = emptyList(), device: String = "tvA", verifier: String = "delegated") =
            J("id" to id, "type" to "ticketed", "description" to why, "line" to line, "ring" to ownerRing, "revokedKeys" to revoked, "device" to device, "now" to now, "verifier" to verifier, "expect" to J("result" to result))
        fun t(a: String, d: String = mandate) = TicketedActivation.encode(d, a)
        val usage = Right.Usage(T0 + DAY, T0 + 31 * DAY)
        val full = KeyScope.ALL
        return listOf(
            case("ticket-production-accepted", "clé de production 90 jours", t(AgentFixtures.activation()), "accepted"),
            case("ticket-trial-accepted", "clé d'essai 30 jours", t(AgentFixtures.activation(kind = ActivationKind.TRIAL, days = 30)), "accepted"),
            case("ticket-unlimited-refused", "production sans durée = illimitée", t(AgentFixtures.activation(rights = emptyList())), "KEY_NOT_ALLOWED"),
            case("ticket-too-long-refused", "366 jours > maxKeyDays=365", t(AgentFixtures.activation(days = 366)), "KEY_NOT_ALLOWED"),
            case("ticket-rental-refused", "toute location est refusée à un agent (W5)", t(AgentFixtures.activation(rights = listOf(usage, Right.Rental("loc-classe-cm2", listOf("classe-cm2"), T0, T0, 90, 0L, 0, 0, "AAAA_box-0")), issuerScopes = full)), "KEY_NOT_ALLOWED"),
            case("ticket-purchase-refused", "achat définitif refusé", t(AgentFixtures.activation(rights = listOf(usage, Right.Purchase("p-classe-cm2", listOf("classe-cm2"), T0)), issuerScopes = full)), "KEY_NOT_ALLOWED"),
            case("ticket-super-refused", "droit super refusé", t(AgentFixtures.activation(rights = listOf(usage, Right.Super("super", T0)), issuerScopes = full)), "KEY_NOT_ALLOWED"),
            case("ticket-openall-refused", "« tout ouvert » refusé", t(AgentFixtures.activation(rights = listOf(usage, Right.OpenAll("ouvert", T0, T0 + 10 * DAY)), issuerScopes = full)), "KEY_NOT_ALLOWED"),
            case("ticket-old-tv-malformed", "une TV ancienne (ActivationVerifier seul) ne lit pas la ligne : MALFORMED", t(AgentFixtures.activation()), "MALFORMED", verifier = "plain"),
            case("ticket-agent-revoked", "agent révoqué : aucune nouvelle activation", t(AgentFixtures.activation()), "REVOKED_KEY", revoked = listOf("agent")),
            case("ticket-usage-overflow-refused", "usage|duree|0|9223372036854775807 : le calcul de durée ne doit pas déborder", t(AgentFixtures.forgedActivation(listOf(Right.Usage(0, Long.MAX_VALUE)))), "KEY_NOT_ALLOWED"),
            case("ticket-usage-overflow-negative-start-refused", "début négatif et fin énorme : durée qui déborderait", t(AgentFixtures.forgedActivation(listOf(Right.Usage(-9_000_000_000_000_000_000L, 9_000_000_000_000_000_000L)))), "KEY_NOT_ALLOWED"),
            case("ticket-usage-end-before-start-refused", "fin avant le début", t(AgentFixtures.forgedActivation(listOf(Right.Usage(T0 + 31 * DAY, T0 + DAY)))), "KEY_NOT_ALLOWED"),
            case("ticket-issued-before-mandate-refused", "activation datée avant le début du mandat", t(AgentFixtures.forgedActivation(listOf(Right.Usage(T0 + DAY, T0 + 30 * DAY)), issuedAt = T0 - 2 * DAY)), "KEY_NOT_ALLOWED"),
            case("ticket-wrong-device", "activation d'une autre TV", t(AgentFixtures.activation()), "WRONG_DEVICE", device = "tvN1"),
        )
    }

    private fun priceGridCases(): List<Map<String, Any?>> {
        val prices = listOf(PriceGrid.Price("cle-essai", 30, 0), PriceGrid.Price("cle-production", 30, 1000), PriceGrid.Price("cle-production", 90, 2500), PriceGrid.Price("cle-production", 365, 9000))
        val at = "2026-10-02T09:00:00Z"
        val good = PriceGrid.signedJson(key("desk").signer, at, prices)
        fun case(id: String, why: String, json: String, result: String, older: String? = null, extra: Map<String, Any?> = emptyMap()) =
            J("id" to id, "type" to "price-grid", "description" to why, "json" to json, "publicKeys" to listOf("desk"), "notOlderThan" to older, "expect" to J("result" to result) + extra)
        val altered = good.replace("\"price\":2500", "\"price\":1")
        return listOf(
            case("price-grid-signed-ok", "grille signée : montants d'essai fictifs", good, "ok", extra = J("count" to 4, "priceOf" to listOf(J("item" to "cle-production", "days" to 90, "price" to 2500), J("item" to "cle-production", "days" to 91, "price" to null)))),
            case("price-grid-modified", "un prix modifié après signature", altered, "refused"),
            case("price-grid-older", "grille plus ancienne que celle déjà gardée", good, "refused", older = "2026-11-01T00:00:00Z"),
            case("price-grid-unsigned", "grille non signée", good.replace(Regex("\"signature\":\"[^\"]*\""), "\"signature\":\"UNSIGNED\""), "refused"),
        )
    }

    private val agent = key("agent").signer
    private fun sale(seq: Long, prev: String, price: Long = 2500, cash: Long = price) =
        SalesLedger.Entry(seq, T0 + seq * 1000, SalesLedger.Kind.SALE, agent.keyId, device = AgentFixtures.dev("tvA").code, license = "lic-0001", seat = "0123456789abcdef", item = "cle-production|90", price = price, cash = cash,
            grid = "2026-10-02T09:00:00Z", receipt = Receipt.code(agent.keyId, seq, AgentFixtures.dev("tvA").code, T0 + seq * 1000), fp = "ab".repeat(32), prev = prev).sign(agent)
    private fun chain(n: Int): List<SalesLedger.Entry> { val out = ArrayList<SalesLedger.Entry>(); var prev = SalesLedger.GENESIS; for (i in 1..n) { out += sale(i.toLong(), prev); prev = out.last().hash }; return out }

    private fun ledgerCases(): List<Map<String, Any?>> {
        fun case(id: String, why: String, entries: List<SalesLedger.Entry>, result: String, seq: Long? = null, extra: Map<String, Any?> = emptyMap()) =
            J("id" to id, "type" to "ledger", "description" to why, "entries" to entries.map { it.text() }, "pub" to "agent", "expect" to J("result" to result, "seq" to seq) + extra)
        val c = chain(4)
        val badPrev = sale(3, "00000000deadbeef")
        val forged = c[1].copy(cash = 1).let { it.copy(hash = it.computeHash()) }
        return listOf(
            case("ledger-chain-ok", "chaîne de 4 ventes, solde dû = 4 × 2500 − 5000 versés", c, "ok", extra = J("balance" to 5000, "remitted" to 5000)),
            case("ledger-gap", "l'entrée 2 manque", listOf(c[0], c[2], c[3]), "gap", 2),
            case("ledger-diverged-prev", "l'entrée 3 ne suit pas l'entrée 2 (prev faux, bien signée)", listOf(c[0], c[1], badPrev, c[3]), "diverged", 3),
            case("ledger-diverged-altered", "contenu modifié sans recalcul du hash", listOf(c[0], c[1].copy(cash = 1), c[2]), "diverged", 2),
            case("ledger-bad-signature", "hash recalculé mais signature de l'ancien texte", listOf(c[0], forged, c[2]), "bad-signature", 2),
        )
    }

    private fun receiptCases(): List<Map<String, Any?>> {
        fun case(id: String, why: String, seq: Long, at: Long, typedFrom: String? = null): Map<String, Any?> {
            val dev = AgentFixtures.dev("tvA").code; val code = Receipt.code(agent.keyId, seq, dev, at)
            val typed = listOfNotNull(J("input" to code.lowercase(), "expect" to code), J("input" to code.replace('0', 'O').replace('1', 'I'), "expect" to code), J("input" to "R-ABCD-EFG!", "expect" to null),
                typedFrom?.let { J("input" to it, "expect" to null) })
            return J("id" to id, "type" to "receipt", "description" to why, "agent" to agent.keyId, "seq" to seq, "device" to dev, "at" to at, "typed" to typed, "expect" to J("code" to code))
        }
        val code7 = Receipt.code(agent.keyId, 7, AgentFixtures.dev("tvA").code, T0)
        val data = code7.removePrefix("R-").replace("-", "")
        val wrong = "R-" + data.replaceRange(2, 3, if (data[2] == 'Z') "Y" else "Z").chunked(4).joinToString("-")
        return listOf(case("receipt-code-1", "code du reçu de la vente 1 ; saisie tolérante (minuscules, O/0, I/1), forme invalide", 1, T0 + 1000),
            case("receipt-code-7", "code de la vente 7 ; un caractère faux est refusé par le contrôle", 7, T0, typedFrom = wrong))
    }

    private fun registryCases(): List<Map<String, Any?>> {
        fun events(at: Long): List<LicenseEvent> {
            val d = AgentFixtures.dev("tvA")
            val a = ActivationIssuer(agent, Delegation.ALLOWED_SCOPES).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, d.code, d.fp, at, rights = listOf(Right.Usage(at, at + 30 * DAY)), license = "lic-0001", nonce = "c3c3c3c3c3c3c3c3")).activation
            return listOf(LicenseEvent.license(agent, at - 1, "lic-0001", 1), LicenseEvent.issue(agent, a))
        }
        fun case(id: String, why: String, ev: List<LicenseEvent>, licenses: List<String>, rejected: String?) =
            J("id" to id, "type" to "registry-delegated", "description" to why, "ring" to ownerRing, "delegations" to listOf(mandate), "events" to ev.map { it.toMap() }, "expect" to J("licenses" to licenses,
                "rejected" to if (rejected == null) emptyList<Any>() else ev.indices.map { J("index" to it, "reason" to rejected) }))
        val m1 = AgentFixtures.delegation(at = T0 - 200 * DAY, nonce = "a1a1a1a1a1a1a1a1"); val m2 = AgentFixtures.delegation(at = T0, nonce = "a2a2a2a2a2a2a2a2")
        val two = listOf(LicenseEvent.license(agent, T0 - 150 * DAY, "lic-old", 1), LicenseEvent.license(agent, T0 + DAY, "lic-new", 1), LicenseEvent.license(agent, T0 - 50 * DAY, "lic-gap", 1))
        val twoCase = J("id" to "registry-delegated-two-mandates", "type" to "registry-delegated", "description" to "deux mandats successifs du même agent : chacun rejoue ses événements, l'intervalle entre les deux est refusé",
            "ring" to ownerRing, "delegations" to listOf(m1, m2), "events" to two.map { it.toMap() }, "expect" to J("licenses" to listOf("lic-new", "lic-old"), "rejected" to listOf(J("index" to 2, "reason" to "KEY_NOT_ALLOWED"))))
        return listOf(twoCase,
            case("registry-delegated-in-window", "événements license+issue signés par l'agent, dans la fenêtre du mandat : rejoués", events(T0 + DAY), listOf("lic-0001"), null),
            case("registry-delegated-out-of-window", "mêmes événements 91 jours après le début (mandat de 90 jours) : KEY_NOT_ALLOWED", events(T0 + 91 * DAY), emptyList(), "KEY_NOT_ALLOWED"),
        )
    }

    private fun generate(): Map<String, Any?> {
        val devs = listOf("tvA", "tvN1").map { AgentFixtures.dev(it) }
        val cases = buildDelegationCases() + delegationCases() + ticketedCases() + priceGridCases() + ledgerCases() + receiptCases() + registryCases()
        return J("format" to AgentVectors.FORMAT,
            "warning" to "CLÉS DE TEST UNIQUEMENT : ces graines sont dérivées de textes publics et ne valent rien ; les montants sont fictifs ; ne jamais les utiliser ailleurs que dans les tests",
            "nowMs" to now,
            "keys" to AgentFixtures.keys.map { J("name" to it.name, "seed" to hex(AgentFixtures.seedOf(if (it.name == "agent") "agent-douala" else if (it.name == "agent2") "agent-bonamoussadi" else it.name)), "publicKey" to it.signer.publicKeyBase64, "kid" to it.signer.keyId, "scopes" to it.scopes.map { s -> s.name }.sorted()) },
            "devices" to devs.map { d -> J("name" to d.name, "code" to d.code, "fingerprints" to d.fp.byKind.mapKeys { e -> e.key.name }) },
            "cases" to cases)
    }

    private fun pretty(v: Any?, indent: String = ""): String = when (v) {
        is Map<*, *> -> if (v.isEmpty()) "{}" else "{\n" + v.entries.joinToString(",\n") { "$indent  ${JsonLite.quote(it.key.toString())}: ${pretty(it.value, "$indent  ")}" } + "\n$indent}"
        is List<*> -> if (v.isEmpty()) "[]" else if (v.all { it !is Map<*, *> && it !is List<*> }) "[" + v.joinToString(", ") { pretty(it) } + "]" else "[\n" + v.joinToString(",\n") { "$indent  ${pretty(it, "$indent  ")}" } + "\n$indent]"
        null -> "null"
        else -> JsonLite.write(v)
    }

    private fun file(name: String = "agent-vectors.json"): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "tools/activation").isDirectory && !File(d, "docs").isDirectory) d = d.parentFile
        return File(d ?: File("."), "tools/activation/$name")
    }

    @Test fun vectorsAreUpToDate() {
        val text = pretty(generate()) + "\n"
        val f = file()
        if (System.getenv("CASTBRIDGE_WRITE_VECTORS") == "1") { f.parentFile.mkdirs(); f.writeText(text) }
        assertTrue(f.isFile, "tools/activation/agent-vectors.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the format change is intended")
    }

    @Test fun thereAreAtLeastTwentyVectorsAcceptedAndRefused() {
        val cases = (JsonLite.obj(file().readText())["cases"] as List<*>).map { it as Map<*, *> }
        assertTrue(cases.size >= 20, "${cases.size} cases")
        assertEquals(cases.size, cases.map { it["id"] }.toSet().size)
        val results = cases.mapNotNull { (it["expect"] as? Map<*, *>)?.get("result") }
        assertTrue(results.count { it == "accepted" || it == "ok" } >= 4 && results.count { it != "accepted" && it != "ok" } >= 15, results.groupingBy { it }.eachCount().toString())
        assertFalse(file().readText().contains("master=") || file().readText().contains("agentx="))
    }

    @Test fun everyCommittedVectorPassesTheReplayer() {
        assertEquals(emptyList(), AgentVectors.run(file().readText()))
    }

    @Test fun aWrongExpectationIsReportedByTheReplayer() {
        val broken = file().readText().replace("\"result\": \"WINDOW_CLOSED\"", "\"result\": \"accepted\"")
        assertTrue(AgentVectors.run(broken).any { it.startsWith("delegation-expired") })
        assertEquals(listOf("format inconnu"), AgentVectors.run("{\"format\":\"autre\"}"))
    }

    @Test fun testKeysAreCopiedFromTheActivationVectorsNotReferenced() {
        @Suppress("UNCHECKED_CAST") fun keys(f: File) = (JsonLite.obj(f.readText())["keys"] as List<Map<String, Any?>>).associateBy { it["name"] }
        val mine = keys(file()); val theirs = keys(file("test-vectors.json"))
        for (n in listOf("desk", "phone", "server", "rogue")) { assertEquals(theirs.getValue(n)["seed"], mine.getValue(n)["seed"], n); assertEquals(theirs.getValue(n)["publicKey"], mine.getValue(n)["publicKey"], n) }
        assertTrue("DELEGATE" in (mine.getValue("desk")["scopes"] as List<*>)); assertFalse("DELEGATE" in (mine.getValue("phone")["scopes"] as List<*>))
    }
}
