package castbridge.core.lots

import castbridge.core.net.JsonLite
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.*

/**
 * tools/activation/rental-pilot-vectors.json (docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md § 2.5): the pilot's signed lines, refusals, engine sentences, use counter,
 * usage statement and one whole activation, replayed by [RentalPilotVectors] (Kotlin) and tools/activation/verify_vectors.py (Python, an independent second implementation of the rules).
 * Regenerate after an intentional change with `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*RentalPilotVectorsTest*'`. The v1 and v2 files are never touched.
 * Keys, devices, master and installations are TEST values derived from public strings (same as the v2 file).
 *
 * The expectations of the cases that matter most are ALSO written by hand in the tests below (not read from the file), so that a regenerated file cannot silently bless a wrong rule.
 */
class RentalPilotVectorsTest {
    private val douala = ZoneOffset.ofHours(1)
    private val day = 24L * 3600 * 1000
    private fun at(d: String, h: Int = 12, mi: Int = 0, s: Int = 0, ms: Int = 0): Long = LocalDate.parse(d).atTime(h, mi, s, ms * 1_000_000).toInstant(douala).toEpochMilli()
    private val label = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private fun labelOf(ms: Long) = OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), douala).format(label)

    private fun seed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|rental|$n".toByteArray())
    private fun installSeed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-install|$n".toByteArray())
    private fun ephSeed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-ephemeral|$n".toByteArray())
    private val master = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-rental-master".toByteArray())
    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    private val keyDefs = listOf("desk" to castbridge.core.owner.KeyScope.ALL)
    private val devDefs = listOf("tvA" to mapOf("flashSerial" to "FLASHSERIAL-R1", "flashCid" to "cid-r1", "ethernetMac" to "AA:BB:CC:00:22:01", "wifiMac" to "10:20:30:40:60:01", "wifiSysfsPath" to soldered, "systemSerial" to "SYSR0001", "bluetoothAddress" to "11:22:33:44:66:01"))
    private val installNames = listOf("tvA-install-1", "tvA-install-2")

    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun file() = File(System.getProperty("user.dir")).let { var d: File? = it; while (d != null && !File(d, "tools/activation").isDirectory) d = d.parentFile; File(d ?: it, "tools/activation/rental-pilot-vectors.json") }
    private fun v1or2(name: String) = File(file().parentFile, name)

    private val catalog = J("bundles" to listOf(
        J("id" to "classe-cm2", "type" to "classe", "lots" to listOf("learn:cm2"), "title" to "CM2"),
        J("id" to "classe-cp", "type" to "classe", "lots" to listOf("learn:cp"), "title" to "CP", "rentalDays" to 7),
        J("id" to "classe-ce1", "type" to "classe", "lots" to listOf("learn:ce1"), "title" to "CE1"),
        J("id" to "classe-6e", "type" to "classe", "lots" to listOf("learn:6e"), "title" to "6e"),
        J("id" to "langues-fr", "type" to "langues", "lots" to listOf("learn:lang-fr"), "title" to "Français"),
        J("id" to "inconnu-famille", "type" to "classe", "lots" to listOf("learn:zzz"), "title" to "Sans famille")))
    private val params = J("pilot.start" to "2026-10-12", "pilot.end" to "2026-11-01")

    private fun skeleton(): Map<String, Any?> = J("format" to RentalPilotVectors.FORMAT,
        "warning" to "CLÉS, APPAREILS, INSTALLATIONS ET SECRET DE TEST dérivés de textes publics : ne protègent rien. Fuseau du pilote : Africa/Douala (UTC+1). Voir docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md § 2.5.",
        "master" to hex(master),
        "keys" to keyDefs.map { (n, sc) -> J("name" to n, "seed" to hex(seed(n)), "scopes" to sc.map { it.name }.sorted()) },
        "devices" to devDefs.map { (n, raw) -> J("name" to n, "raw" to raw) },
        "installs" to installNames.map { J("name" to it, "seed" to hex(installSeed(it))) },
        "params" to params, "catalog" to catalog,
        "families" to J("reserved" to listOf("learn:cm2", "learn:cp", "learn:ce1", "learn:6e"), "free" to listOf("learn:lang-fr")),
        "cases" to emptyList<Any?>())

    // ---- case builders ------------------------------------------------------------------------------------------------------------------------------------------------
    private fun contract(product: String = "loc-classe-cm2", period: Long = at("2026-10-12"), unit: String = "hours", usage: Int = 720, endsAt: Long = at("2026-11-11"), reissues: Int = 0, endedAt: Long? = null, installPub: String? = "OLD") =
        J("product" to product, "period" to period, "unit" to unit, "usage" to usage, "endsAt" to endsAt, "reissues" to reissues, "endedAt" to endedAt, "installPub" to installPub)
    private fun state(vararg c: Map<String, Any?>, hours: Int = 0) = J("active" to c.toList(), "hoursLast168" to hours)

    private fun generate(): Map<String, Any?> {
        val root = skeleton(); val cases = ArrayList<Map<String, Any?>>()
        fun fill(c: Map<String, Any?>) { cases += c + ("expect" to RentalPilotVectors.compute(root, c)) }
        fun new(id: String, choice: String, issuedAt: Long, bundle: String = "classe-cm2", st: Map<String, Any?> = state()) =
            fill(J("id" to id, "type" to "pilot-new", "choice" to choice, "bundle" to bundle, "issuedAt" to issuedAt, "issuedAtLabel" to labelOf(issuedAt), "state" to st))
        fun extend(id: String, choice: String, existing: Map<String, Any?>, issuedAt: Long, st: Map<String, Any?>? = null) =
            fill(J("id" to id, "type" to "pilot-extend", "choice" to choice, "bundle" to "classe-cm2", "issuedAt" to issuedAt, "issuedAtLabel" to labelOf(issuedAt), "existing" to existing, "state" to (st ?: state(existing))))
        fun reissue(id: String, existing: Map<String, Any?>, used: Int, issuedAt: Long, newPub: String? = "NEW", st: Map<String, Any?>? = null) =
            fill(J("id" to id, "type" to "pilot-reissue", "bundle" to "classe-cm2", "issuedAt" to issuedAt, "issuedAtLabel" to labelOf(issuedAt), "existing" to existing, "usedMinutes" to used, "newInstallPub" to newPub, "state" to (st ?: state(existing))))
        fun rl(product: String = "loc-classe-cm2", start: Long = at("2026-10-12"), period: Long = start, days: Int = 30, usage: Int = 0) = J("product" to product, "bundles" to listOf("classe-cm2"), "startsAt" to start, "period" to period, "days" to days, "graceMs" to 0, "usage" to usage, "concurrent" to 3)
        fun engine(id: String, lines: List<Map<String, Any?>>, now: Long, used: Int = 0, expired: String? = null) =
            fill(J("id" to id, "type" to "engine-contract", "lines" to lines, "now" to now, "nowLabel" to labelOf(now), "used" to used, "expired" to expired))
        fun meter(id: String, vararg ops: List<Any?>) = fill(J("id" to id, "type" to "meter", "ops" to ops.toList()))

        // ---- the three choices and the pilot window (Douala, UTC+1) ----
        new("line-default-30d", "defaut", at("2026-10-12"))
        new("line-7d", "7j", at("2026-10-14"))
        new("line-1h", "1h", at("2026-10-14"))
        new("line-12h-bound-12oct-first-instant", "12h", at("2026-10-12", 0))
        new("line-12h-bound-25oct", "12h", at("2026-10-25"))
        new("line-1h-last-instant-of-pilot", "1h", at("2026-11-01", 23, 59, 59, 999))
        new("line-96h", "96h", at("2026-10-20"))
        new("line-default-honoured-until-1dec", "defaut", at("2026-11-01", 23, 59, 59, 999))
        new("line-30d-last-instant-of-pilot", "30j", at("2026-11-01", 23, 59, 59, 999))
        new("line-default-bundle-rental-days", "defaut", at("2026-10-14"), bundle = "classe-cp")
        new("before-pilot-refused", "defaut", at("2026-10-12", 0) - 1)
        new("after-pilot-end-refused", "defaut", at("2026-11-02", 0))
        new("hours-above-cap-refused", "97h", at("2026-10-14"))
        new("days-above-max-refused", "31j", at("2026-10-14"))
        new("fourth-contract-refused", "1h", at("2026-10-14"), bundle = "classe-6e", st = state(contract("loc-a", usage = 0, unit = "days"), contract("loc-b", usage = 0, unit = "days"), contract("loc-c", usage = 0, unit = "days")))
        new("third-contract-accepted", "1h", at("2026-10-14"), bundle = "classe-6e", st = state(contract("loc-a", usage = 0, unit = "days"), contract("loc-b", usage = 0, unit = "days")))
        new("bundle-already-rented-refused", "1h", at("2026-10-14"), st = state(contract()))
        new("langues-refused", "7j", at("2026-10-14"), bundle = "langues-fr")
        new("unknown-family-refused", "7j", at("2026-10-14"), bundle = "inconnu-famille")
        new("quota-192h-168h-last-hour-accepted", "2h", at("2026-10-14"), st = state(hours = 190))
        new("quota-192h-168h-refused", "3h", at("2026-10-14"), st = state(hours = 190))
        // ---- extension ----
        extend("extend-6h-plus-6h", "6h", contract(usage = 360), at("2026-10-14"))
        extend("extend-36h-after-60h", "36h", contract(usage = 3600), at("2026-10-14"))
        extend("extend-37h-after-60h-refused", "37h", contract(usage = 3600), at("2026-10-14"))
        extend("extend-12h-after-90h-refused", "12h", contract(usage = 5400), at("2026-10-14"))
        extend("extend-days-on-hourly-contract-refused", "7j", contract(usage = 360), at("2026-10-14"))
        extend("extend-hours-on-day-contract-refused", "6h", contract(unit = "days", usage = 0), at("2026-10-14"))
        extend("extend-hours-to-16nov-once", "1h", contract(period = at("2026-11-01"), usage = 180, endsAt = at("2026-11-15", 23, 59, 59, 999)), at("2026-11-01"))
        extend("extend-hours-edge-plus-1ms-refused", "1h", contract(period = at("2026-11-01"), usage = 180, endsAt = at("2026-11-15", 23, 59, 59, 999) + 1), at("2026-11-01"))
        extend("extend-hours-after-16nov-limit-refused", "1h", contract(period = at("2026-11-01"), usage = 240, endsAt = at("2026-11-16", 23, 59, 59, 999)), at("2026-11-01"))
        extend("extend-after-pilot-end-refused", "1h", contract(usage = 180), at("2026-11-02", 0))
        extend("extend-quota-168h-refused", "5h", contract(usage = 360), at("2026-10-14"), st = state(contract(usage = 360), hours = 190))
        // ---- re-issue on another installation ----
        reissue("reissue-same-key-refused", contract(usage = 1200), 300, at("2026-10-20"), newPub = "OLD")
        reissue("reissue-hours-other-key", contract(usage = 1200, endsAt = at("2026-11-11")), 300, at("2026-10-20"))
        reissue("reissue-days-floor", contract(unit = "days", usage = 0, endsAt = at("2026-11-11")), 0, at("2026-10-20", 18))
        reissue("reissue-hours-exhausted-refused", contract(usage = 600), 600, at("2026-10-20"))
        reissue("reissue-after-pilot-allowed-until-original-end", contract(unit = "days", usage = 0, endsAt = at("2026-11-11")), 0, at("2026-11-05"))
        // ---- engine: the unit, the 96 h ceiling, the sentences ----
        engine("clamp-60h-plus-36h-no-note", listOf(rl(usage = 3600, days = 30), rl(start = at("2026-10-14"), period = at("2026-10-12"), days = 1, usage = 2160)), at("2026-10-14"))
        engine("clamp-sum-above-96h", listOf(rl(usage = 3600), rl(start = at("2026-10-14"), period = at("2026-10-12"), days = 1, usage = 3600)), at("2026-10-14"))
        engine("clamp-90h-plus-12h", listOf(rl(usage = 5400), rl(start = at("2026-10-14"), period = at("2026-10-12"), days = 1, usage = 720)), at("2026-10-14"))
        engine("mixed-units-engine-no-budget", listOf(rl(usage = 720), rl(start = at("2026-10-14"), period = at("2026-10-12"), days = 5, usage = 0)), at("2026-10-14"))
        engine("mixed-units-engine-days-first", listOf(rl(days = 7), rl(start = at("2026-10-14"), period = at("2026-10-12"), days = 1, usage = 360)), at("2026-10-14"))
        engine("status-strings-hours-active", listOf(rl(usage = 720, days = 21, start = at("2026-10-25"))), at("2026-10-26"), used = 100)
        engine("status-strings-hours-expired-usage", listOf(rl(usage = 720, days = 21, start = at("2026-10-25"))), at("2026-10-27"), used = 720)
        engine("status-strings-hours-expired-date", listOf(rl(usage = 720, days = 21, start = at("2026-10-25"))), at("2026-11-16"), used = 100)
        engine("status-strings-days", listOf(rl(days = 7)), at("2026-10-15"))
        engine("status-strings-days-ended", listOf(rl(days = 7)), at("2026-10-20"))
        // ---- the use counter ----
        meter("meter-59s-59s-58s", listOf("open", "learn:cm2", 0), listOf("tick", 59_000), listOf("tick", 118_000), listOf("tick", 176_000))
        meter("meter-close-after-30s", listOf("open", "learn:cm2", 0), listOf("close", 30_000), listOf("open", "learn:cm2", 100_000), listOf("close", 130_000))
        meter("meter-pause-6min", listOf("open", "learn:cm2", 0), listOf("tick", 60_000), listOf("pause", 60_000), listOf("tick", 420_000), listOf("resume", 420_000), listOf("tick", 480_000))
        meter("meter-idle-31min", listOf("open", "learn:cm2", 0), listOf("tick", 1_860_000), listOf("input", 1_860_000), listOf("tick", 1_920_000))
        meter("meter-reboot", listOf("open", "learn:cm2", 0), listOf("tick", 50_000), listOf("reboot"), listOf("open", "learn:cm2", 100_000), listOf("tick", 150_000))
        // ---- the usage statement ----
        fill(J("id" to "usage-report-two-contracts", "type" to "usage-report", "installId" to "0123456789abcdef", "at" to at("2026-10-14"),
            "contracts" to listOf(J("product" to "loc-cm2", "lot" to "learn:cm2", "usage" to 600, "days" to 30, "used" to 90), J("product" to "loc-cm1", "lot" to "learn:cm1", "usage" to 0, "days" to 7, "used" to 5))))
        // ---- the whole activation of a 12 h rental ----
        fill(J("id" to "build-activation-12h", "type" to "build-activation", "signer" to "desk", "device" to "tvA", "install" to "tvA-install-1", "license" to "lic-pilot-1", "bundle" to "classe-cm2", "choice" to "12h",
            "issuedAt" to at("2026-10-25"), "issuedAtLabel" to labelOf(at("2026-10-25")), "windowHours" to 48, "nonce" to "00112233445566778899aabbccddeeff", "ephSeed" to hex(ephSeed("pilot-12h"))))
        // ---- the 10-field line is always readable ----
        fill(J("id" to "line-ten-fields-empty-box", "type" to "line-parse", "line" to "rental|loc-classe-cm2|classe-cm2|1800000000000|1800000000000|30|0|720|3|"))
        fill(J("id" to "line-ten-fields-96h", "type" to "line-parse", "line" to "rental|loc-classe-cm2|classe-cm2|1800000000000|1790000000000|1|0|5760|3|v2:AAAA:BBBB"))
        fill(J("id" to "line-ten-fields-days-no-budget", "type" to "line-parse", "line" to "rental|loc-classe-cm2|classe-cm2|1800000000000|1800000000000|7|0|0|3|"))
        fill(J("id" to "line-nine-fields-refused", "type" to "line-parse", "line" to "rental|loc-classe-cm2|classe-cm2|1800000000000|1800000000000|30|0|720|3"))
        return root + mapOf("cases" to cases)
    }

    private fun pretty(v: Any?, ind: String = ""): String = when (v) {
        is Map<*, *> -> if (v.isEmpty()) "{}" else "{\n" + v.entries.joinToString(",\n") { "$ind  ${JsonLite.quote(it.key.toString())}: ${pretty(it.value, "$ind  ")}" } + "\n$ind}"
        is List<*> -> if (v.isEmpty()) "[]" else if (v.all { it is String || it is Number || it == null }) v.joinToString(", ", "[", "]") { pretty(it) } else "[\n" + v.joinToString(",\n") { "$ind  ${pretty(it, "$ind  ")}" } + "\n$ind]"
        else -> JsonLite.write(v)
    }

    @Test fun committedFileMatchesTheCodeAndEveryVectorReplays() {
        val text = pretty(generate()) + "\n"
        val f = file()
        if (System.getenv("CASTBRIDGE_WRITE_VECTORS") == "1") { f.parentFile.mkdirs(); f.writeText(text) }
        assertTrue(f.isFile, "tools/activation/rental-pilot-vectors.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the pilot vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the change is intended")
        assertEquals(emptyList(), RentalPilotVectors.run(f.readText()))
        assertTrue((JsonLite.obj(f.readText())["cases"] as List<*>).size >= 20)
    }

    @Test fun aTamperedVectorIsDetected() {
        val text = pretty(generate())
        assertTrue(RentalPilotVectors.run(text.replace("castbridge-rental-pilot-vectors-v1", "autre")).isNotEmpty())
        assertTrue(RentalPilotVectors.run(text.replace("|30|0|720|3|", "|30|0|721|3|")).isNotEmpty(), "a changed line must fail the replay")
        assertTrue(RentalPilotVectors.run(text.replace("\"minutes\": [0, 0, 1, 1]", "\"minutes\": [0, 0, 1, 2]")).isNotEmpty(), "a changed counter result must fail the replay")
    }

    // ---- expectations written BY HAND (independent of the file and of the generator) --------------------------------------------------------------------------------------
    private fun cases(): Map<String, Map<String, Any?>> = (generate()["cases"] as List<*>).map { @Suppress("UNCHECKED_CAST") (it as Map<String, Any?>) }.associateBy { it["id"] as String }
    @Suppress("UNCHECKED_CAST") private fun expect(id: String) = cases().getValue(id)["expect"] as Map<String, Any?>

    @Test fun theTwelveHourLineIsTheOneOfTheBrief() {
        assertEquals("rental|loc-classe-cm2|classe-cm2|T|T|30|0|720|3|", expect("line-12h-bound-12oct-first-instant")["line"])
        assertEquals("rental|loc-classe-cm2|classe-cm2|T|T|21|0|720|3|", expect("line-12h-bound-25oct")["line"], "25/10 -> 15/11 = 21 days of safety")
        assertEquals("rental|loc-classe-cm2|classe-cm2|T|T|14|0|60|3|", expect("line-1h-last-instant-of-pilot")["line"])
        assertEquals("rental|loc-classe-cm2|classe-cm2|T|T|30|0|0|3|", expect("line-default-30d")["line"])
        assertEquals("rental|loc-classe-cm2|classe-cm2|T|T|7|0|0|3|", expect("line-7d")["line"])
        assertEquals("rental|loc-classe-cm2|classe-cm2|T|T|26|0|5760|3|", expect("line-96h")["line"], "20/10 -> 15/11 = 26 days of safety")
        assertEquals("rental|loc-classe-cm2|classe-cm2|T2|T|1|0|2160|3|", expect("extend-36h-after-60h")["line"], "the renewal keeps the period")
    }

    @Test fun windowEdgesAndCeilingsAreRefusedOrAccepted() {
        assertNull(expect("line-12h-bound-12oct-first-instant")["refused"]); assertNull(expect("line-1h-last-instant-of-pilot")["refused"])
        for (id in listOf("before-pilot-refused", "after-pilot-end-refused", "hours-above-cap-refused", "days-above-max-refused", "fourth-contract-refused", "langues-refused", "unknown-family-refused",
            "quota-192h-168h-refused", "extend-37h-after-60h-refused", "extend-12h-after-90h-refused", "extend-days-on-hourly-contract-refused", "extend-hours-on-day-contract-refused",
            "extend-hours-after-16nov-limit-refused", "extend-hours-edge-plus-1ms-refused", "extend-after-pilot-end-refused", "reissue-same-key-refused", "reissue-hours-exhausted-refused", "bundle-already-rented-refused"))
            assertEquals(true, expect(id)["refused"], "$id must be refused")
        for (id in listOf("third-contract-accepted", "quota-192h-168h-last-hour-accepted", "extend-36h-after-60h", "extend-hours-to-16nov-once", "reissue-hours-other-key", "reissue-after-pilot-allowed-until-original-end"))
            assertNull(expect(id)["refused"], "$id must be accepted")
        assertTrue((expect("after-pilot-end-refused")["reason"] as String).contains("terminé"))
        assertTrue((expect("langues-refused")["reason"] as String).contains("Langues"))
        assertTrue((expect("extend-12h-after-90h-refused")["reason"] as String).contains("96"))
    }

    @Test fun theEngineCeilingIs96HoursAndAMixedLineChangesNothing() {
        assertEquals(5760L, expect("clamp-60h-plus-36h-no-note")["maxUsageMinutes"]); assertEquals(emptyList<String>(), expect("clamp-60h-plus-36h-no-note")["notes"], "60 h + 36 h = 5760: no note")
        assertEquals(5760L, expect("clamp-sum-above-96h")["maxUsageMinutes"]); assertEquals(5760L, expect("clamp-90h-plus-12h")["maxUsageMinutes"])
        assertEquals(listOf("6 h non applicables : plafond de 96 h par location"), expect("clamp-90h-plus-12h")["notes"])
        assertEquals(720L, expect("mixed-units-engine-no-budget")["maxUsageMinutes"], "a days line never erases the budget"); assertEquals("hours", expect("mixed-units-engine-no-budget")["unit"])
        assertEquals(0L, expect("mixed-units-engine-days-first")["maxUsageMinutes"], "an hours line never gives a days contract a budget"); assertEquals("days", expect("mixed-units-engine-days-first")["unit"])
        assertEquals("Il vous reste 10 h 20 d'utilisation · à utiliser avant le 15/11", expect("status-strings-hours-active")["message"])
        assertEquals("Vos 12 heures d'utilisation sont épuisées : ce contenu n'est plus disponible. Relouer ?", expect("status-strings-hours-expired-usage")["message"])
        assertEquals("Vos heures non utilisées ont expiré le 15/11. Relouer ?", expect("status-strings-hours-expired-date")["message"])
    }

    @Test fun theUseCounterNeverCountsTwiceNorRoundsUp() {
        @Suppress("UNCHECKED_CAST") fun minutes(id: String) = expect(id)["minutes"] as List<Number>
        assertEquals(listOf(0, 0, 1, 1), minutes("meter-59s-59s-58s").map { it.toInt() }, "176 s = 2 min 56 s: 2 minutes, 56 s carried")
        assertEquals(listOf(0, 0, 0, 1), minutes("meter-close-after-30s").map { it.toInt() }, "30 s + 30 s across a close = 1 minute")
        assertEquals(listOf(0, 1, 5, 1), minutes("meter-pause-6min").map { it.toInt() }, "1 min, a 6 min pause counted 5 min at most, 1 min after the resume")
        assertEquals(30, minutes("meter-idle-31min").first { it.toInt() > 0 }.toInt(), "no key for 31 min: 30 min counted, not 31")
        assertEquals(0, minutes("meter-reboot").sumOf { it.toInt() }, "a restart loses the carry (under one minute), never doubles it")
    }

    @Test fun theUsageStatementHoldsNoPersonalData() {
        val text = expect("usage-report-two-contracts")["text"] as String
        assertTrue(text.startsWith("castbridge-rental-usage-v1\ninstall=0123456789abcdef\n"))
        for (secret in listOf("lic-", "seat", "box", "FLASHSERIAL", "AA:BB", "profil", "enfant")) assertFalse(text.contains(secret, ignoreCase = true), secret)
        assertTrue(text.contains("contract=loc-cm1@") && text.contains("|unit=days|used=5|max=0|") && text.contains("|unit=hours|used=90|max=600|"))
    }

    @Test fun everyProducedLineHasTenFieldsAndIsReadable() {
        for ((id, c) in cases()) {
            val line = (c["expect"] as Map<*, *>)["line"] as? String ?: continue
            val f = line.split("|").toMutableList()
            f[3] = if (f[3] == "T2") "1800000000001" else "1800000000000"; f[4] = "1800000000000"
            assertEquals(10, f.size, id); RentalLines.parse(f)
        }
    }

    @Test fun theV1AndV2FilesAreUntouched() {
        // the guard is git (git diff --stat on the two files is empty); here: both files still declare their own format
        assertTrue(v1or2("rental-vectors.json").readText().contains("castbridge-rental-vectors-v1")); assertTrue(v1or2("rental-vectors-v2.json").readText().contains("castbridge-rental-vectors-v2"))
        assertNotEquals(RentalPilotVectors.FORMAT, "castbridge-rental-vectors-v2")
    }
}
