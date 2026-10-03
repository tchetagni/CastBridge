package castbridge.core.store

import castbridge.core.lots.LotFamily
import castbridge.core.lots.MemoryQueueStore
import castbridge.core.lots.RentalContract
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.RentalWarning
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.bool
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * tools/activation/store-vectors.json (`castbridge-store-vectors-v1`, DESIGN-W17 § 4). Les octets attendus (forme canonique, code court) sont ceux du code ; les ÉTATS attendus des
 * scénarios (refus, acquittement, expiration, rapprochement) sont écrits à la main ici. Le fichier est rejoué par ce test et, pour les octets, par
 * `python3 tools/activation/verify_vectors.py --only store`. Régénérer après un changement voulu :
 * `CASTBRIDGE_WRITE_VECTORS=1 tools/core-harness/run.sh :core:test --tests '*StoreVectorsTest*'`. Aucune clé, aucun secret : des identifiants d'essai publics.
 */
class StoreVectorsTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000
    private val tv = "0123456789abcdef"

    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)
    private fun file() = File(System.getProperty("user.dir")).let { var d: File? = it; while (d != null && !File(d, "tools/activation").isDirectory) d = d.parentFile; File(d ?: it, "tools/activation/store-vectors.json") }

    private fun fields(bundle: String = "classe-cm2", choice: String = "12h", kind: String = "new", period: Long = 0, nonce: String = "a1b2c3d4", at: Long = t0, origin: String = "tv") =
        J("tv" to tv, "bundle" to bundle, "choice" to choice, "kind" to kind, "period" to period, "nonce" to nonce, "at" to at, "origin" to origin)

    private fun requestOf(f: Map<String, Any?>) = RentRequest(f.str("tv")!!, f.str("bundle")!!, f.str("choice")!!, RentRequest.Kind.entries.first { it.wire == f.str("kind") }, f.long("period")!!, f.str("nonce")!!, f.long("at")!!,
        RentRequest.Origin.entries.first { it.wire == f.str("origin") })

    private fun rental(bundle: String, period: Long = t0 - 2 * day, state: String = "ACTIVE", unit: String = "days", endsAt: Long = t0 + 10 * day, usage: Long = 360) = J("bundle" to bundle, "period" to period, "state" to state, "unit" to unit, "endsAt" to endsAt, "usage" to usage)
    private fun facts(family: String? = "RESERVED", trial: Boolean = false, kid: Boolean = false, pilotEnd: Long? = null, max: Int = 3, rentals: List<Map<String, Any?>> = emptyList(), shelf: String = "APPRENDRE", lots: List<String> = listOf(family ?: "null")) =
        J("trial" to trial, "kid" to kid, "shelf" to shelf, "lots" to lots, "pilotEnd" to pilotEnd, "max" to max, "rentals" to rentals)

    private fun create(now: Long, nonce: String, bundle: String, choice: String, facts: Map<String, Any?>, expect: String, extend: Boolean = false) =
        J("op" to "create", "now" to now, "nonce" to nonce, "bundle" to bundle, "choice" to choice, "extend" to extend, "facts" to facts, "expect" to expect)
    private fun ack(now: Long, nonce: String, answer: String, expect: String) = J("op" to "ack", "now" to now, "nonce" to nonce, "answer" to answer, "expect" to expect)
    private fun expire(now: Long, expect: Int) = J("op" to "expire", "now" to now, "expect" to expect)
    private fun reconcile(now: Long, rentals: List<Map<String, Any?>>, expect: Int) = J("op" to "reconcile", "now" to now, "rentals" to rentals, "expect" to expect)
    private fun states(vararg e: String) = J("op" to "states", "expect" to e.toList())
    private fun scenario(id: String, steps: List<Map<String, Any?>>) = J("id" to id, "type" to "scenario", "steps" to steps)

    private fun generate(): Map<String, Any?> {
        val cases = mutableListOf<Map<String, Any?>>()
        // ---- octets : forme canonique et code court (rejoués en Python) ----
        val ext = fields(choice = "7j", kind = "extend", period = t0 - 3 * day, origin = "phone")
        cases += J("id" to "canonical-extend-from-phone", "type" to "canonical", "fields" to ext, "expect" to requestOf(ext).canonical())
        val f12 = fields()
        cases += J("id" to "shortcode-12h", "type" to "shortcode", "fields" to f12, "alias" to "CM2", "expect" to requestOf(f12).shortCode("CM2"))
        val fdef = fields(choice = "defaut", nonce = "0000000a")
        cases += J("id" to "shortcode-default-choice", "type" to "shortcode", "fields" to fdef, "alias" to "CM2", "expect" to requestOf(fdef).shortCode("CM2"))
        val fnew = fields(bundle = "a" + "b".repeat(63), choice = "999h", nonce = "00ff00ff", at = 0)
        cases += J("id" to "canonical-new-at-boundaries", "type" to "canonical", "fields" to fnew, "expect" to requestOf(fnew).canonical())
        cases += J("id" to "parse-ok-boundaries", "type" to "parse-ok", "text" to requestOf(fnew).canonical())
        cases += J("id" to "parse-ok-new-default", "type" to "parse-ok", "text" to requestOf(fields(choice = "defaut")).canonical())
        val good = requestOf(f12).canonical()
        cases += J("id" to "parse-refused-wrong-header", "type" to "parse-refused", "text" to good.replace("v1", "v2"), "expect" to "MALFORMED")
        cases += J("id" to "parse-refused-unsorted-lines", "type" to "parse-refused", "text" to good.split("\n").let { it.take(1) + it.drop(1).reversed() }.joinToString("\n"), "expect" to "MALFORMED")
        cases += J("id" to "parse-refused-missing-nonce", "type" to "parse-refused", "text" to good.split("\n").filterNot { it.startsWith("nonce=") }.joinToString("\n"), "expect" to "MALFORMED")
        cases += J("id" to "parse-refused-new-with-period", "type" to "parse-refused", "text" to good.replace("period=0", "period=5"), "expect" to "MALFORMED")
        cases += J("id" to "choice-labels", "type" to "labels", "choices" to listOf("defaut", "1j", "7j", "1h", "12h"),
            "expect" to listOf("Sans durée précise : 30 jours", "1 jour", "7 jours", "1 heure d'utilisation", "12 heures d'utilisation"))
        // ---- scénarios : états écrits à la main ----
        cases += scenario("lifecycle-create-ack-reconcile-expire", listOf(
            create(t0, "00000001", "classe-cm2", "12h", facts(), "OK"),
            create(t0 + 1, "00000002", "classe-cm2", "12h", facts(), "REFUSED:DUPLICATE"),
            create(t0 + 2, "00000003", "classe-cm1", "7j", facts(), "OK"),
            states("00000001:PENDING", "00000003:PENDING"),
            ack(t0 + 1000, "00000001", "ACCEPTED", "APPLIED:ACCEPTED"),
            ack(t0 + 2000, "00000001", "ACCEPTED", "SAME:ACCEPTED"),
            ack(t0 + 3000, "00000001", "REFUSED", "CONFLICT:ACCEPTED"),
            ack(t0 + 3000, "deadbeef", "ACCEPTED", "UNKNOWN:null"),
            reconcile(t0 + 4000, listOf(rental("autre")), 0),
            reconcile(t0 + 5000, listOf(rental("classe-cm2", period = t0 + 4000, endsAt = t0 + 30 * day)), 1),
            expire(t0 + 7 * day + 2, 0),
            expire(t0 + 7 * day + 3, 1),
            states("00000001:FULFILLED", "00000003:EXPIRED")))
        cases += scenario("lost-ack-pending-is-fulfilled-by-the-delivery", listOf(
            create(t0, "00000001", "classe-cm2", "12h", facts(), "OK"),
            reconcile(t0 + 1000, listOf(rental("classe-cm2", period = t0 + 500, endsAt = t0 + 30 * day)), 1),
            ack(t0 + 2000, "00000001", "ACCEPTED", "SAME:FULFILLED"),
            states("00000001:FULFILLED")))
        val hrs = facts(rentals = listOf(rental("classe-cm2", unit = "hours", usage = 360)))
        cases += scenario("two-hour-extensions-each-need-their-own-delivery", listOf(
            create(t0, "00000001", "classe-cm2", "6h", hrs, "OK"), create(t0, "00000002", "classe-cm2", "12h", hrs, "OK"),
            ack(t0, "00000001", "ACCEPTED", "APPLIED:ACCEPTED"), ack(t0, "00000002", "ACCEPTED", "APPLIED:ACCEPTED"),
            reconcile(t0 + 1, listOf(rental("classe-cm2", unit = "hours", usage = 720)), 1), states("00000001:FULFILLED", "00000002:ACCEPTED"),
            reconcile(t0 + 2, listOf(rental("classe-cm2", unit = "hours", usage = 1440)), 1), states("00000001:FULFILLED", "00000002:FULFILLED")))
        cases += scenario("langues-bundle-with-a-reserved-lot-is-free", listOf(create(t0, "00000001", "langues-zh-a0", "30j", facts(shelf = "LANGUES", lots = listOf("RESERVED")), "REFUSED:FREE_BUNDLE"), states()))
        cases += scenario("kid-profile-refused", listOf(create(t0, "00000001", "classe-cm2", "12h", facts(kid = true), "REFUSED:KID_PROFILE"), states()))
        cases += scenario("trial-tv-refused", listOf(create(t0, "00000001", "classe-cm2", "12h", facts(trial = true), "REFUSED:TRIAL_TV"), states()))
        cases += scenario("free-bundle-refused", listOf(create(t0, "00000001", "langues-zh-a0", "30j", facts(family = "FREE"), "REFUSED:FREE_BUNDLE"), states()))
        cases += scenario("quota-counts-pending-new-requests", listOf(
            create(t0, "00000001", "classe-a", "12h", facts(max = 1), "OK"), create(t0, "00000002", "classe-a", "24h", facts(max = 1), "OK"),
            create(t0, "00000003", "classe-b", "12h", facts(max = 1), "REFUSED:OVER_LIMIT")))
        cases += scenario("same-bundle-other-unit-then-same-unit-extends", listOf(
            create(t0, "00000001", "classe-cm2", "7j", facts(rentals = listOf(rental("classe-cm2", unit = "hours"))), "REFUSED:SAME_BUNDLE_OTHER_UNIT"),
            create(t0, "00000002", "classe-cm2", "6h", facts(rentals = listOf(rental("classe-cm2", unit = "hours"))), "OK"),
            states("00000002:PENDING")))
        cases += scenario("clock-set-back-never-expires", listOf(
            create(t0, "00000001", "classe-cm2", "12h", facts(), "OK"),
            expire(t0 - 30 * day, 0), states("00000001:PENDING")))
        cases += scenario("queue-full-at-twenty", (1..20).map { create(t0, "%08x".format(it), "classe-$it", "12h", facts(max = 99), "OK") } +
            listOf(create(t0, "000000ff", "classe-21", "12h", facts(max = 99), "REFUSED:QUEUE_FULL"), ack(t0, "00000001", "REFUSED", "APPLIED:REFUSED"), create(t0, "000000fe", "classe-21", "12h", facts(max = 99), "OK")))
        return J("format" to "castbridge-store-vectors-v1",
            "warning" to "Identifiants d'essai publics : aucune clé, aucun secret. Voir docs/agent-briefs/sonnet-w17-03-rent-request-core.md.", "cases" to cases)
    }

    private fun statusOf(r: Map<String, Any?>): RentalStatus {
        val hours = r.str("unit") == "hours"; val period = r.long("period")!!
        val state = RentalState.valueOf(r.str("state")!!)
        return RentalStatus(RentalContract("loc-${r.str("bundle")}@$period", "loc-${r.str("bundle")}", listOf(r.str("bundle")!!), "lic", period, period, r.long("endsAt")!!, 0, if (hours) r.long("usage")!! else 0, 3, ""),
            state, null, null, null, null, RentalWarning.NONE, "")
    }

    @Suppress("UNCHECKED_CAST")
    private fun runScenario(id: String, steps: List<Map<String, Any?>>, fail: MutableList<String>) {
        var clock = t0; var nonce = "00000000"
        val q = RentRequests(MemoryQueueStore(), tv, { clock }, { nonce })
        fun factsOf(m: Map<String, Any?>) = RentRequests.Facts(m.bool("trial")!!, m.bool("kid")!!, StoreCatalog.Shelf.valueOf(m.str("shelf")!!),
            (m["lots"] as List<String>).map { if (it == "null") null else LotFamily.valueOf(it) }, (m["rentals"] as List<Map<String, Any?>>).map(::statusOf), m.int("max")!!, m.long("pilotEnd"))
        for ((i, s) in steps.withIndex()) {
            val at = "$id#$i"
            s.long("now")?.let { clock = it }
            when (s.str("op")) {
                "create" -> {
                    nonce = s.str("nonce")!!
                    val r = q.create(s.str("bundle")!!, s.str("choice")!!, factsOf(s["facts"] as Map<String, Any?>), s.bool("extend") == true)
                    val got = when (r) { is RentRequests.Created.Ok -> "OK"; is RentRequests.Created.Refused -> "REFUSED:${r.reason}" }
                    if (got != s.str("expect")) fail += "$at create : $got au lieu de ${s.str("expect")}"
                }
                "ack" -> { val r = q.ack(s.str("nonce")!!, RentRequests.Answer.valueOf(s.str("answer")!!)); val got = "${r.outcome}:${r.state}"; if (got != s.str("expect")) fail += "$at ack : $got au lieu de ${s.str("expect")}" }
                "expire" -> { val n = q.expire(clock); if (n != s.int("expect")) fail += "$at expire : $n au lieu de ${s.int("expect")}" }
                "reconcile" -> { val n = q.reconcile((s["rentals"] as List<Map<String, Any?>>).map(::statusOf)); if (n != s.int("expect")) fail += "$at reconcile : $n au lieu de ${s.int("expect")}" }
                "states" -> { val got = q.entries().map { "${it.request.nonce}:${it.state}" }.sorted(); if (got != s["expect"]) fail += "$at états : $got au lieu de ${s["expect"]}" }
                else -> fail += "$at op inconnue"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun run(text: String): List<String> {
        val fail = mutableListOf<String>()
        for (c in JsonLite.obj(text)["cases"] as List<Map<String, Any?>>) {
            val id = c.str("id")!!
            when (c.str("type")) {
                "canonical" -> { val got = requestOf(c["fields"] as Map<String, Any?>).canonical(); if (got != c.str("expect")) fail += "$id : $got" }
                "shortcode" -> { val got = requestOf(c["fields"] as Map<String, Any?>).shortCode(c.str("alias")!!); if (got != c.str("expect")) fail += "$id : $got" }
                "parse-ok" -> { val p = RentRequest.parse(c.str("text")!!); if (p !is RentRequest.Parsed.Ok || p.request.canonical() != c.str("text")) fail += "$id : refusé ($p)" }
                "parse-refused" -> { val p = RentRequest.parse(c.str("text")!!); if (p !is RentRequest.Parsed.Bad || p.refusal.name != c.str("expect")) fail += "$id : accepté ou mauvais motif ($p)" }
                "labels" -> {
                    val got = (c["choices"] as List<String>).map { RentRequest(tv, "classe-cm2", it, RentRequest.Kind.NEW, 0, "a1b2c3d4", t0, RentRequest.Origin.TV).choiceLabel() }
                    if (got != c["expect"]) fail += "$id : $got"
                }
                "scenario" -> runScenario(id, c["steps"] as List<Map<String, Any?>>, fail)
                else -> fail += "$id : type inconnu"
            }
        }
        return fail
    }

    private fun pretty(v: Any?, ind: String = ""): String = when (v) {
        is Map<*, *> -> if (v.isEmpty()) "{}" else "{\n" + v.entries.filter { it.value != null }.joinToString(",\n") { "$ind  ${JsonLite.quote(it.key.toString())}: ${pretty(it.value, "$ind  ")}" } + "\n$ind}"
        is List<*> -> if (v.isEmpty()) "[]" else if (v.all { it is String || it is Number }) v.joinToString(", ", "[", "]") { pretty(it) } else "[\n" + v.joinToString(",\n") { "$ind  ${pretty(it, "$ind  ")}" } + "\n$ind]"
        else -> JsonLite.write(v)
    }

    @Test fun committedFileMatchesTheCodeAndEveryVectorReplays() {
        val text = pretty(generate()) + "\n"
        val f = file()
        if (System.getenv("CASTBRIDGE_WRITE_VECTORS") == "1") { f.parentFile.mkdirs(); f.writeText(text) }
        assertTrue(f.isFile, "tools/activation/store-vectors.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the store vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the change is intended")
        assertEquals(emptyList(), run(f.readText()))
        assertTrue((JsonLite.obj(f.readText())["cases"] as List<*>).size >= 12)
    }

    @Test fun theReplayCatchesAWrongExpectation() {
        val tampered = pretty(generate()).replace("REFUSED:KID_PROFILE", "OK")
        assertTrue(run(tampered).isNotEmpty())
    }
}
