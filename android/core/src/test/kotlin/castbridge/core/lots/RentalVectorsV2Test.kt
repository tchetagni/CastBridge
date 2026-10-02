package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.owner.*
import java.io.File
import java.security.MessageDigest
import kotlin.test.*

/**
 * tools/activation/rental-vectors-v2.json (docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md § 6). The X25519 expectations are the RFC 7748 values written BY HAND here; the other
 * expected bytes are what the library produces. Regenerate after an intentional change with `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*RentalVectorsV2Test*'`.
 * Keys, devices, master and installations are TEST values derived from public strings (same as the v1 file, copied; the v1 file is not touched).
 */
class RentalVectorsV2Test {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000
    private fun seed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|rental|$n".toByteArray())
    private fun installSeed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-install|$n".toByteArray())
    private fun ephSeed(n: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-ephemeral|$n".toByteArray())
    private val master = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-rental-master".toByteArray())
    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    private val keyDefs = listOf("desk" to KeyScope.ALL, "server" to setOf(KeyScope.ISSUE_PRODUCTION, KeyScope.REACTIVATE, KeyScope.REGISTRY), "noprod" to setOf(KeyScope.COMMAND_SUPPORT))
    private val devDefs = listOf(
        "tvA" to mapOf("flashSerial" to "FLASHSERIAL-R1", "flashCid" to "cid-r1", "ethernetMac" to "AA:BB:CC:00:22:01", "wifiMac" to "10:20:30:40:60:01", "wifiSysfsPath" to soldered, "systemSerial" to "SYSR0001", "bluetoothAddress" to "11:22:33:44:66:01"),
        "tvB" to mapOf("flashSerial" to "FLASHSERIAL-R2", "flashCid" to "cid-r2", "ethernetMac" to "AA:BB:CC:00:22:02", "wifiMac" to "10:20:30:40:60:02", "wifiSysfsPath" to soldered, "systemSerial" to "SYSR0002", "bluetoothAddress" to "11:22:33:44:66:02"),
    )
    private val installNames = listOf("tvA-install-1", "tvA-install-2", "tvB-install-1")

    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun file() = File(System.getProperty("user.dir")).let { var d: File? = it; while (d != null && !File(d, "tools/activation").isDirectory) d = d.parentFile; File(d ?: it, "tools/activation/rental-vectors-v2.json") }

    private fun skeleton(): Map<String, Any?> = J("format" to RentalVectorsV2.FORMAT,
        "warning" to "CLÉS, APPAREILS, INSTALLATIONS ET SECRET DE TEST dérivés de textes publics : ne protègent rien. Voir docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md § 5 et § 6.",
        "master" to hex(master),
        "keys" to keyDefs.map { (n, sc) -> J("name" to n, "seed" to hex(seed(n)), "scopes" to sc.map { it.name }.sorted()) },
        "devices" to devDefs.map { (n, raw) -> J("name" to n, "raw" to raw) },
        "installs" to installNames.map { J("name" to it, "seed" to hex(installSeed(it))) },
        "cases" to emptyList<Any?>())

    private val env get() = RentalVectors.env(skeleton())
    private val installs get() = RentalVectorsV2.installs(skeleton())

    private fun rental(product: String = "loc-cm2", bundles: List<String> = listOf("classe-cm2"), start: Long = t0, days: Int = 30, period: Long? = null) =
        J("product" to product, "bundles" to bundles, "startsAt" to start, "period" to (period ?: start), "days" to days, "graceMs" to 0, "usage" to 0, "concurrent" to 0)

    private fun rentalKey(dev: String, license: String, product: String, period: Long): ByteArray {
        val d = env.devices.getValue(dev); return RentalKeys.rentalKey(master, license, SeatIds.of(license, d), product, period)
    }

    private fun generate(): Map<String, Any?> {
        val cases = ArrayList<Map<String, Any?>>()
        val ik = installs
        // ---- X25519: RFC 7748, expectations written by hand ----
        fun x(id: String, scalar: String, u: String, out: String) { cases += J("id" to id, "type" to "x25519", "scalar" to scalar, "u" to u, "expect" to J("out" to out)) }
        x("x25519-rfc7748-5-2-first", "a546e36bf0527c9d3b16154b82465edd62144c0ac1fc5a18506a2244ba449ac4", "e6db6867583030db3594c1a424b15f7c726624ec26b3353b10a903a6d0ab1c4c", "c3da55379de9c6908e94ea4df28d084f32eccf03491c71f754b4075577a28552")
        x("x25519-rfc7748-5-2-second", "4b66e9d4d1b4673c5ad22691957d6af5c11b6421e0ea01d42ca4169e7918ba0d", "e5210f12786811d3f4b7959d0538ae2c31dbe7106fc03c3efc4cd549c715a493", "95cbde9476e8907d7aade45cb4b873f88b595a68799fa152e6f8f7647aac7957")
        val nine = "09" + "00".repeat(31)
        x("x25519-rfc7748-6-1-alice-public", "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a", nine, "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        x("x25519-rfc7748-6-1-bob-public", "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb", nine, "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
        fun sh(id: String, priv: String, pub: String, shared: String?) { cases += J("id" to id, "type" to "x25519-shared", "priv" to priv, "pub" to pub, "expect" to (if (shared == null) J("refused" to true) else J("shared" to shared))) }
        val aPriv = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"; val bPriv = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
        val aPub = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"; val bPub = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
        sh("x25519-rfc7748-6-1-shared-alice", aPriv, bPub, "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        sh("x25519-rfc7748-6-1-shared-bob", bPriv, aPub, "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
        sh("x25519-small-order-zero-refused", aPriv, "00".repeat(32), null)
        sh("x25519-small-order-eight-refused", aPriv, "e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800", null)
        // ---- installation keys ----
        for (n in installNames.take(2)) cases += J("id" to "install-key-$n", "type" to "install-key", "install" to n, "expect" to J("pub" to hex(ik.getValue(n).pub), "installId" to ik.getValue(n).installId))
        // ---- boxes ----
        val lic = "lic-loc-1"
        fun req(dev: String, install: String, other: String? = null) = J("device" to dev, "install" to install, "other" to other, "license" to lic, "product" to "loc-cm2", "period" to t0, "ephSeed" to hex(ephSeed("$dev|$install")))
        fun boxOf(r: Map<String, Any?>) = RentalKeys.makeBoxV2(ik.getValue(r["install"] as String).pub, rentalKey(r["device"] as String, lic, "loc-cm2", t0), "loc-cm2", t0, ephSeed("${r["device"]}|${r["install"]}"))
        val fpr = RentalKeys.fingerprintOf(rentalKey("tvA", lic, "loc-cm2", t0))
        val r1 = req("tvA", "tvA-install-1")
        cases += J("id" to "box-v2", "type" to "box-v2", "request" to r1, "expect" to J("box" to boxOf(r1), "keyFingerprint" to fpr))
        val r2 = req("tvA", "tvA-install-1", other = "tvA-install-2")
        cases += J("id" to "box-v2-other-install", "type" to "box-v2", "request" to r2, "expect" to J("box" to boxOf(r2), "keyFingerprint" to fpr))
        val rA = RentalKeys.makeBoxV2(ik.getValue("tvA-install-1").pub, rentalKey("tvA", lic, "loc-cm2", t0), "loc-cm2", t0, ephSeed("reissue-a"))
        val rB = RentalKeys.makeBoxV2(ik.getValue("tvA-install-2").pub, rentalKey("tvA", lic, "loc-cm2", t0), "loc-cm2", t0, ephSeed("reissue-b"))
        cases += J("id" to "box-v2-reissue", "type" to "box-v2-reissue", "request" to J("device" to "tvA", "installA" to "tvA-install-1", "installB" to "tvA-install-2", "license" to lic, "product" to "loc-cm2", "period" to t0,
            "ephSeedA" to hex(ephSeed("reissue-a")), "ephSeedB" to hex(ephSeed("reissue-b"))), "expect" to J("boxA" to rA, "boxB" to rB, "keyFingerprint" to fpr))
        val v1 = RentalKeys.makeBox(env.devices.getValue("tvA"), DeviceIdentity.kFor(env.devices.getValue("tvA").n), rentalKey("tvA", lic, "loc-cm2", t0), "loc-cm2", t0)
        cases += J("id" to "box-v2-mixed-with-v1-refused", "type" to "box-v2-mixed", "request" to r1, "expect" to J("box" to boxOf(r1) + ";" + v1))
        cases += J("id" to "box-v1-then-v2-mixed-refused", "type" to "box-v2-mixed", "request" to r1, "expect" to J("order" to "v1-first", "box" to v1 + ";" + boxOf(r1)))
        cases += J("id" to "box-v1-sunset", "type" to "box-v1-sunset", "request" to J("device" to "tvA", "license" to lic, "product" to "loc-cm2", "period" to t0), "expect" to J("box" to v1, "sunsetMs" to RentalKeys.V1_BOX_SUNSET_MS))
        // ---- device requests ----
        val dA = env.devices.getValue("tvA"); val code = DeviceCode.of(dA); val pubA = ik.getValue("tvA-install-1").pub
        val factors = dA.byKind.map { "${it.key.name}|${it.value}" }
        val withInstall = OwnerFrames.deviceInfo(code, dA, pubA)
        cases += J("id" to "request-v2", "type" to "request-v2", "text" to withInstall, "expect" to J("code" to code, "k" to DeviceIdentity.kFor(dA.n), "installPub" to hex(pubA), "factors" to factors, "unknown" to emptyList<String>()))
        cases += J("id" to "request-v1-old-tv-no-install", "type" to "request-v2", "text" to OwnerFrames.deviceInfo(code, dA), "expect" to J("code" to code, "k" to DeviceIdentity.kFor(dA.n), "installPub" to null, "factors" to factors, "unknown" to emptyList<String>()))
        cases += J("id" to "request-v2-unknown-line-ignored", "type" to "request-v2", "text" to withInstall + "\nfuture=1", "expect" to J("code" to code, "k" to DeviceIdentity.kFor(dA.n), "installPub" to hex(pubA), "factors" to factors, "unknown" to listOf("future=1")))
        cases += J("id" to "request-v2-bad-install-refused", "type" to "request-v2", "text" to OwnerFrames.deviceInfo(code, dA) + "\ninstall=x25519|1234", "expect" to J("unreadable" to true))
        // ---- whole activations ----
        fun build(id: String, install: String?, rentals: List<Map<String, Any?>>, signer: String = "desk", refused: Boolean = false, seedName: String = "build") {
            val r = J("device" to "tvA", "install" to install, "license" to lic, "issuedAt" to t0, "windowHours" to 48, "nonce" to "00112233445566778899aabbccddeeff", "ephSeed" to hex(ephSeed(seedName)), "rentals" to rentals)
            val exp = if (refused) J("refused" to true) else {
                val d = env.devices.getValue("tvA"); val (s, sc) = env.keys.getValue(signer)
                val rights = rentals.map { m -> RentalIssuing.right(RentalSpec(m["product"] as String, m["bundles"] as List<String>, (m["days"] as Int), 0, 0, 0, m["period"] as Long), t0, lic, SeatIds.of(lic, d), d, master, ik.getValue(install!!).pub, ephSeed(seedName)) }
                J("token" to ActivationIssuer(s, sc).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(d), d, t0, Subject.TV, rights, lic, null, t0, 48, "00112233445566778899aabbccddeeff", null)).token)
            }
            cases += J("id" to id, "type" to "build-activation-v2", "signer" to signer, "request" to r, "expect" to exp)
        }
        build("build-activation-v2", "tvA-install-1", listOf(rental()))
        build("build-activation-v2-two-rentals", "tvA-install-1", listOf(rental("loc-a", listOf("classe-cm2")), rental("loc-b", listOf("quiz-cm2"), days = 90)), seedName = "build-two")
        build("build-activation-v2-renewal-keeps-period", "tvA-install-2", listOf(rental(start = t0 + 20 * day, period = t0)), seedName = "build-renew")
        build("build-refuse-without-install-key", null, listOf(rental()), refused = true)
        build("build-refuse-key-without-production-scope", "tvA-install-1", listOf(rental()), signer = "noprod", refused = true)
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
        assertTrue(f.isFile, "tools/activation/rental-vectors-v2.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the v2 rental vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the change is intended")
        assertEquals(emptyList(), RentalVectorsV2.run(f.readText()))
        assertTrue((JsonLite.obj(f.readText())["cases"] as List<*>).size >= 12)
    }

    @Test fun aTamperedVectorIsDetected() {
        val text = pretty(generate())
        val box = Regex("\"box\": \"(v2:[^\"]+)\"").find(text)!!.groupValues[1]
        val bad = text.replace(box, box.dropLast(2) + "AA")
        assertTrue(RentalVectorsV2.run(bad).isNotEmpty(), "a changed box must fail the replay")
        assertTrue(RentalVectorsV2.run(text.replace("castbridge-rental-vectors-v2", "autre")).isNotEmpty())
    }
}
