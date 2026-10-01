package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.owner.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/**
 * tools/activation/rental-vectors.json (docs/RENTAL-LOTS.md § 8). The expected STATES are written by hand here (the rules of the clock), the expected BYTES are what the library
 * produces; the file is then replayed by the core, the desk tool and, later, the server port. Regenerate after an intentional change with
 * `CASTBRIDGE_WRITE_VECTORS=1 tools/core-harness/run.sh :core:test --tests '*RentalVectorsTest*'`. Keys, devices and master are TEST values derived from public strings.
 */
class RentalVectorsTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000
    private fun seed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|rental|$n".toByteArray())
    private val master = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-rental-master".toByteArray())
    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    private val keyDefs = listOf("desk" to KeyScope.ALL, "server" to setOf(KeyScope.ISSUE_PRODUCTION, KeyScope.REACTIVATE, KeyScope.REGISTRY), "noprod" to setOf(KeyScope.COMMAND_SUPPORT))
    private val devDefs = listOf(
        "tvA" to mapOf("flashSerial" to "FLASHSERIAL-R1", "flashCid" to "cid-r1", "ethernetMac" to "AA:BB:CC:00:22:01", "wifiMac" to "10:20:30:40:60:01", "wifiSysfsPath" to soldered, "systemSerial" to "SYSR0001", "bluetoothAddress" to "11:22:33:44:66:01"),
        "tvB" to mapOf("flashSerial" to "FLASHSERIAL-R2", "flashCid" to "cid-r2", "ethernetMac" to "AA:BB:CC:00:22:02", "wifiMac" to "10:20:30:40:60:02", "wifiSysfsPath" to soldered, "systemSerial" to "SYSR0002", "bluetoothAddress" to "11:22:33:44:66:02"),
    )

    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun file() = File(System.getProperty("user.dir")).let { var d: File? = it; while (d != null && !File(d, "tools/activation").isDirectory) d = d.parentFile; File(d ?: it, "tools/activation/rental-vectors.json") }

    private fun skeleton(): Map<String, Any?> = J("format" to RentalVectors.FORMAT,
        "warning" to "CLÉS, APPAREILS ET SECRET DE TEST dérivés de textes publics : ne protègent rien. Voir docs/RENTAL-LOTS.md § 8.",
        "master" to hex(master),
        "keys" to keyDefs.map { (n, sc) -> J("name" to n, "seed" to hex(seed(n)), "scopes" to sc.map { it.name }.sorted()) },
        "devices" to devDefs.map { (n, raw) -> J("name" to n, "raw" to raw) },
        "cases" to emptyList<Any?>())

    private val env get() = RentalVectors.env(skeleton())
    private fun rental(product: String = "loc-cm2", bundles: List<String> = listOf("classe-cm2"), start: Long = t0, days: Int = 30, grace: Long = 0, usage: Int = 0, conc: Int = 0, period: Long? = null) =
        J("product" to product, "bundles" to bundles, "startsAt" to start, "period" to (period ?: start), "days" to days, "graceMs" to grace, "usage" to usage, "concurrent" to conc)

    private fun token(rentals: List<Map<String, Any?>>, issuedAt: Long = t0, dev: String = "tvA", license: String = "lic-loc-1", signer: String = "desk", rights: List<String> = emptyList()): String {
        val e = env; val d = e.devices.getValue(dev); val (s, sc) = e.keys.getValue(signer)
        val rs = rentals.map { RentalVectors.rightOf(e, it, license, d) } + rights.map { Activation.parseRight(it) }
        return ActivationIssuer(s, sc).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(d), d, issuedAt, Subject.TV, rs, license, null, issuedAt, 48, "00112233445566778899aabbccddeeff")).token
    }

    private fun st(key: String, state: String, reason: String? = null, doubt: String? = null, rem: Long? = null, usage: Long? = null, warn: String = "NONE", msg: String) =
        J("key" to key, "state" to state, "reason" to reason, "doubt" to doubt, "remainingMs" to rem, "remainingUsageMinutes" to usage, "warning" to warn, "message" to msg)

    private fun stateCase(id: String, tokens: List<String>, wall: Long, expect: List<Map<String, Any?>>, lastSeen: Long = 0, floor: Long = 0, used: Map<String, Long> = emptyMap(), expired: Map<String, String> = emptyMap()) =
        J("id" to id, "type" to "rental-state", "device" to "tvA", "tokens" to tokens, "wallMs" to wall, "clock" to J("lastSeen" to lastSeen, "floor" to floor), "usedMinutes" to used, "expired" to expired, "expect" to expect)

    private fun generate(): Map<String, Any?> {
        val k = "loc-cm2@$t0"
        val base = token(listOf(rental()))
        val cases = ArrayList<Map<String, Any?>>()
        // ---- builds (bytes) ----
        fun build(id: String, rentals: List<Map<String, Any?>>, signer: String = "desk", refused: Boolean = false, rights: List<String> = emptyList(), window: Int = 48) {
            val req = J("device" to "tvA", "license" to "lic-loc-1", "issuedAt" to t0, "windowHours" to window, "nonce" to "00112233445566778899aabbccddeeff", "rentals" to rentals, "rights" to rights)
            val exp = if (refused) J("refused" to true) else J("token" to token(rentals, signer = signer, rights = rights))
            cases += J("id" to id, "type" to "build-activation", "signer" to signer, "request" to req, "expect" to exp)
        }
        build("build-rental-30d", listOf(rental()))
        build("build-rental-grace-usage-cap", listOf(rental(grace = 2 * day, usage = 600, conc = 2)))
        build("build-rental-with-purchase", listOf(rental()), rights = listOf("purchase|p-classe-cm2|classe-cm2|${t0 - 10 * day}"))
        build("build-rental-renewal", listOf(rental(start = t0 + 20 * day, period = t0)))
        build("build-two-rentals-one-activation", listOf(rental("loc-a", listOf("classe-cm2")), rental("loc-b", listOf("quiz-cm2"), days = 90)))
        build("build-refuse-367-days", listOf(rental(days = 367)), refused = true)
        build("build-refuse-0-days", listOf(rental(days = 0)), refused = true)
        build("build-refuse-grace-31-days", listOf(rental(grace = 31 * day)), refused = true)
        build("build-refuse-concurrent-21", listOf(rental(conc = 21)), refused = true)
        build("build-refuse-period-after-start", listOf(rental(start = t0, period = t0 + day)), refused = true)
        build("build-refuse-key-without-production-scope", listOf(rental()), signer = "noprod", refused = true)
        // ---- states (the rules of the clock, expectations written by hand) ----
        val msgEnded = RentalEngine.ENDED
        cases += stateCase("state-day-1-active", listOf(base), t0 + day, listOf(st(k, "ACTIVE", rem = 29 * day, msg = "Il vous reste 29 jours")))
        cases += stateCase("state-7-days-left-warning", listOf(base), t0 + 23 * day, listOf(st(k, "ACTIVE", rem = 7 * day, warn = "DAYS_7", msg = "Il vous reste 7 jours")))
        cases += stateCase("state-24h-left-warning", listOf(base), t0 + 30 * day - 24 * 3600_000L, listOf(st(k, "ACTIVE", rem = 24 * 3600_000L, warn = "HOURS_24", msg = "Il vous reste 1 jour")))
        cases += stateCase("state-1h-left-warning", listOf(base), t0 + 30 * day - 3600_000L, listOf(st(k, "ACTIVE", rem = 3600_000L, warn = "HOUR_1", msg = "Il vous reste 1 h")))
        cases += stateCase("state-last-millisecond-active", listOf(base), t0 + 30 * day - 1, listOf(st(k, "ACTIVE", rem = 1, warn = "HOUR_1", msg = "Il vous reste 1 min")))
        cases += stateCase("state-exact-end-expired", listOf(base), t0 + 30 * day, listOf(st(k, "EXPIRED", reason = "DATE", msg = msgEnded)))
        val grace = token(listOf(rental(days = 10, grace = 2 * day)))
        cases += stateCase("state-grace-usable", listOf(grace), t0 + 11 * day, listOf(st(k, "GRACE", rem = day, warn = "HOURS_24", msg = "Location terminée : reconnectez le téléphone pour la renouveler (1 jour de tolérance)")))
        cases += stateCase("state-grace-over", listOf(grace), t0 + 12 * day, listOf(st(k, "EXPIRED", reason = "DATE", msg = msgEnded)))
        cases += stateCase("state-clock-behind-suspended", listOf(base), t0 + 2 * day, listOf(st(k, "SUSPENDED", doubt = "BEHIND", msg = RentalEngine.CHECK_CLOCK + " L'heure semble en retard.")), lastSeen = t0 + 20 * day)
        cases += stateCase("state-clock-behind-but-already-proven-ended", listOf(base), t0 + 2 * day, listOf(st(k, "EXPIRED", reason = "DATE", doubt = "BEHIND", msg = msgEnded)), lastSeen = t0 + 40 * day)
        cases += stateCase("state-clock-far-ahead-suspended", listOf(base), t0 + 200 * day, listOf(st(k, "SUSPENDED", doubt = "AHEAD", msg = RentalEngine.CHECK_CLOCK + " L'heure semble très en avance.")), lastSeen = t0)
        cases += stateCase("state-floor-from-signed-message-proves-time", listOf(base), t0 + 2 * day, listOf(st(k, "EXPIRED", reason = "DATE", doubt = "BEHIND", msg = msgEnded)), floor = t0 + 31 * day)
        val capped = token(listOf(rental(usage = 120)))
        cases += stateCase("state-usage-ceiling-reached", listOf(capped), t0 + day, listOf(st(k, "EXPIRED", reason = "USAGE", usage = 0, msg = msgEnded)), used = mapOf(k to 120))
        cases += stateCase("state-usage-ceiling-reached-clock-behind", listOf(capped), t0 - 10 * day, listOf(st(k, "EXPIRED", reason = "USAGE", doubt = "BEHIND", usage = 0, msg = msgEnded)), lastSeen = t0 + day, used = mapOf(k to 120))
        val big = token(listOf(rental(usage = 1000)))
        cases += stateCase("state-usage-5h-left-warning", listOf(big), t0 + day, listOf(st(k, "ACTIVE", rem = 29 * day, usage = 300, warn = "HOURS_24", msg = "Il vous reste 29 jours (ou 5 h d'utilisation)")), used = mapOf(k to 700))
        cases += stateCase("state-usage-left-warning", listOf(capped), t0 + day, listOf(st(k, "ACTIVE", rem = 29 * day, usage = 20, warn = "HOUR_1", msg = "Il vous reste 29 jours (ou 20 min d'utilisation)")), used = mapOf(k to 100))
        val renewal = token(listOf(rental(start = t0 + 20 * day, period = t0)), issuedAt = t0 + 20 * day)
        cases += stateCase("state-renewal-extends-same-rental", listOf(base, renewal), t0 + 40 * day, listOf(st(k, "ACTIVE", rem = 20 * day, msg = "Il vous reste 20 jours")), floor = t0 + 20 * day)
        cases += stateCase("state-same-token-twice-no-duplicate", listOf(base, base), t0 + day, listOf(st(k, "ACTIVE", rem = 29 * day, msg = "Il vous reste 29 jours")))
        val late = token(listOf(rental(start = t0 + 35 * day, period = t0)), issuedAt = t0 + 35 * day)
        cases += stateCase("state-late-renewal-ignored", listOf(base, late), t0 + 36 * day, listOf(st(k, "EXPIRED", reason = "DATE", msg = msgEnded)), floor = t0 + 35 * day)
        val future = token(listOf(rental(start = t0 + 5 * day, days = 10)), issuedAt = t0)
        cases += stateCase("state-post-dated-not-started", listOf(future), t0 + day, listOf(st("loc-cm2@${t0 + 5 * day}", "NOT_STARTED", msg = "Location pas encore commencée")))
        cases += stateCase("state-already-swept-stays-expired", listOf(base), t0 + day, listOf(st(k, "EXPIRED", reason = "USAGE", msg = msgEnded)), expired = mapOf(k to "USAGE"))
        val two = token(listOf(rental("loc-a", start = t0, conc = 1), rental("loc-b", start = t0 + day, conc = 1)))
        cases += stateCase("state-simultaneous-limit-oldest-first", listOf(two), t0 + 2 * day,
            listOf(st("loc-a@$t0", "ACTIVE", rem = 28 * day, msg = "Il vous reste 28 jours"), st("loc-b@${t0 + day}", "OVER_LIMIT", msg = "Trop de locations en même temps : « loc-b » reprendra quand une autre sera terminée")))
        // ---- an old device ignores a right it does not know ----
        val e = env; val d = e.devices.getValue("tvA"); val (s, _) = e.keys.getValue("desk")
        val unknownRights = listOf(Right.Purchase("p-classe-cm2", listOf("classe-cm2"), t0 - 10 * day), Right.Unknown("hologram|prod|a,b|1|2"))
        val seat = SeatIds.of("lic-loc-1", d)
        val payload = Activation.payload(ActivationKind.PRODUCTION, Subject.TV, s.keyId, t0, "00112233445566778899aabbccddeeff", t0, t0, t0 + 48 * 3_600_000L, "lic-loc-1", seat, DeviceIdentity.kFor(d.n), d.byKind, unknownRights)
        val sig = java.util.Base64.getEncoder().encodeToString(s.sign(payload.toByteArray()))
        val oldTok = Activation(ActivationKind.PRODUCTION, Subject.TV, s.keyId, t0, "00112233445566778899aabbccddeeff", t0, t0, t0 + 48 * 3_600_000L, "lic-loc-1", seat, DeviceIdentity.kFor(d.n), d.byKind, unknownRights, sig).encode()
        cases += J("id" to "old-device-ignores-unknown-right", "type" to "old-device", "device" to "tvA", "token" to oldTok, "nowMs" to t0, "expect" to J("granted" to listOf("classe-cm2")))
        val rentalOnly = token(listOf(rental()))
        cases += J("id" to "rental-line-grants-nothing-without-rental-evaluation", "type" to "old-device", "device" to "tvA", "token" to rentalOnly, "nowMs" to t0 + day, "expect" to J("granted" to emptyList<String>()))
        // ---- free lots are never rented ----
        fun pol(id: String, f: String, sc: String, refused: Boolean, ed: String = "full") = cases.add(J("id" to id, "type" to "lot-policy", "lot" to J("feature" to f, "scope" to sc, "edition" to ed, "title" to "x"),
            "free" to listOf("langues:fr-a0"), "reserved" to listOf("learn:cm2", "quiz:cm2"), "expect" to J("refused" to refused)))
        pol("policy-reserved-lot-rentable", "learn", "cm2", false)
        pol("policy-free-lot-refused", "langues", "fr-a0", true)
        pol("policy-unknown-family-refused", "learn", "inconnu", true)
        pol("policy-trial-lot-refused", "learn", "cm2", true, ed = "trial")
        // ---- keys: box and sealed lot ----
        val key = RentalKeys.rentalKey(master, "lic-loc-1", SeatIds.of("lic-loc-1", d), "loc-cm2", t0)
        cases += J("id" to "box-opens-on-its-tv-only", "type" to "box", "request" to (rental() + mapOf("device" to "tvA", "otherDevice" to "tvB", "license" to "lic-loc-1")),
            "expect" to J("box" to RentalKeys.makeBox(d, DeviceIdentity.kFor(d.n), key, "loc-cm2", t0), "keyFingerprint" to RentalKeys.fingerprintOf(key)))
        val plain = "6361737462726964676520686f7273206c6f7421"
        cases += J("id" to "seal-lot-file", "type" to "seal", "request" to J("device" to "tvA", "license" to "lic-loc-1", "product" to "loc-cm2", "period" to t0, "feature" to "learn", "scope" to "cm2", "version" to 3, "plainHex" to plain),
            "expect" to J("sealedHex" to hex(RentalKeys.seal(key, LotId("learn", "cm2"), 3, plain.chunked(2).map { it.toInt(16).toByte() }.toByteArray())), "keyFingerprint" to RentalKeys.fingerprintOf(key)))
        return skeleton() + mapOf("cases" to cases)
    }

    private fun pretty(v: Any?, ind: String = ""): String = when (v) {
        is Map<*, *> -> if (v.isEmpty()) "{}" else "{\n" + v.entries.joinToString(",\n") { "$ind  ${JsonLite.quote(it.key.toString())}: ${pretty(it.value, "$ind  ")}" } + "\n$ind}"
        is List<*> -> if (v.isEmpty()) "[]" else if (v.all { it is String || it is Number }) v.joinToString(", ", "[", "]") { pretty(it) } else "[\n" + v.joinToString(",\n") { "$ind  ${pretty(it, "$ind  ")}" } + "\n$ind]"
        else -> JsonLite.write(v)
    }

    @Test fun committedFileMatchesTheCodeAndEveryVectorReplays() {
        val text = pretty(generate()) + "\n"
        val f = file()
        if (System.getenv("CASTBRIDGE_WRITE_VECTORS") == "1") { f.parentFile.mkdirs(); f.writeText(text) }
        assertTrue(f.isFile, "tools/activation/rental-vectors.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the rental vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the change is intended")
        assertEquals(emptyList(), RentalVectors.run(f.readText()))
        assertTrue((JsonLite.obj(f.readText())["cases"] as List<*>).size >= 40)
    }
}
