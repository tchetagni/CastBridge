package castbridge.core.owner

import castbridge.core.lots.Access
import castbridge.core.lots.Right
import castbridge.core.net.JsonLite
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

/**
 * tools/activation/proof-vectors.json (`castbridge-proof-vectors-v1`): the vectors the TV proof builder and the phone verifier must pass (docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md § 3.3).
 * Same inputs, same bytes (Ed25519 is deterministic). The keys are TEST KEYS derived from public strings, worth nothing. Regenerate with
 * `CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*ProofVectorsTest*'` after an intentional format change.
 */
class ProofVectorsTest {
    private val t0 = 1_800_000_000_000L
    private val day = 24L * 3600 * 1000
    private val hour = 3_600_000L
    private val nonce = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun seedOf(name: String) = MessageDigest.getInstance("SHA-256").digest("castbridge-test-vector-key|$name".toByteArray())
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private class K(val name: String, val seed: ByteArray, val scopes: Set<KeyScope>?) {
        val signer = Ed25519Signer(seed); val install = InstallSigner(seed)
    }
    private val keys = listOf(
        K("desk", seedOf("desk"), KeyScope.ALL), K("support", seedOf("support"), setOf(KeyScope.COMMAND_SUPPORT)), K("rogue", seedOf("rogue"), KeyScope.ALL),
        K("tv-install-1", seedOf("tv-install-1"), null), K("tv-install-2", seedOf("tv-install-2"), null),
    )
    private fun key(n: String) = keys.first { it.name == n }

    private val soldered = "/sys/devices/platform/soc/ffe03000.sd/mmc_host/mmc1/mmc1:0001/net/wlan0"
    private val devices = listOf(
        "tvA" to DeviceIdentity.fingerprints(RawFactors("FLASHSERIAL-A1", "cid-a1", "AA:BB:CC:00:11:01", "10:20:30:40:50:01", soldered, "SYSA0001", "11:22:33:44:55:01")),
        "tvB" to DeviceIdentity.fingerprints(RawFactors("FLASHSERIAL-B2", "cid-b2", "AA:BB:CC:00:11:02", "10:20:30:40:50:02", soldered, "SYSB0002", "11:22:33:44:55:02")),
    )
    private fun fp(n: String) = devices.first { it.first == n }.second

    private fun J(vararg p: Pair<String, Any?>): Map<String, Any?> = linkedMapOf(*p)

    private fun act(signer: String = "desk", dev: String = "tvA", kind: ActivationKind = ActivationKind.PRODUCTION, rights: List<Right> = emptyList(), issued: Long = t0): String {
        val s = key(signer).signer; val f = fp(dev); val seat = SeatIds.of("lic-0001", f); val k = DeviceIdentity.kFor(f.n); val nc = "00112233445566778899aabbccddeeff"
        val p = Activation.payload(kind, Subject.TV, s.keyId, 1, nc, issued, issued - hour, issued + 47 * hour, "lic-0001", seat, k, f.byKind, rights)
        return Activation(kind, Subject.TV, s.keyId, 1, nc, issued, issued - hour, issued + 47 * hour, "lic-0001", seat, k, f.byKind, rights, Base64.getEncoder().encodeToString(s.sign(p.toByteArray()))).encode()
    }

    private class Acc(val keyInstalled: Boolean = true, val trial: Boolean = false, val suspended: Boolean = false, val superUnlimited: Boolean = false, val label: String = "Version complète") {
        fun access() = TvAccess(keyInstalled, Access.TRIAL_ONLY, null, label, superUnlimited, trial, suspended)
        fun json(): Map<String, Any?> = linkedMapOf("keyInstalled" to keyInstalled, "trial" to trial, "suspended" to suspended, "superUnlimited" to superUnlimited, "label" to label)
    }

    private fun build(install: String = "tv-install-1", dev: String = "tvA", n: String = nonce, seq: Long = 5, now: Long = t0, acc: Acc = Acc(), activation: String? = act(), name: String = "Salon", grace: Boolean = false, uptime: Long = 12_345) =
        TvProof.build(key(install).install, fp(dev), n, seq, now, acc.access(), activation, name, grace, uptime)

    private fun ringOf(names: List<String>, revoked: List<String>) = KeyRing(names.map { key(it).signer.trusted(key(it).scopes ?: KeyScope.ALL) }, revoked.map { key(it).signer.keyId }.toSet())
    private fun pinOf(name: String?) = name?.let { TrustedKey(key(it).install.keyId, key(it).install.publicKeyBase64) }

    private fun outcome(r: ProofResult): Map<String, Any?> = when (r) {
        is ProofResult.Accepted -> J("result" to "accepted", "tvCode" to r.proof.tvCode, "tvName" to r.proof.tvName, "installKid" to r.proof.installKeyId, "endsAt" to (r.proof.endsAt ?: 0L),
            "super" to r.proof.superUnlimited, "verifiedAt" to r.proof.verifiedAt, "seq" to r.proof.seq)
        is ProofResult.Rejected -> J("result" to "rejected", "reason" to r.reason.name.lowercase(), "tvCode" to r.tvCode, "tvName" to r.tvName)
        is ProofResult.NeedsPin -> J("result" to "needs-pin", "keyId" to r.keyId, "fingerprint" to r.fingerprint, "tvCode" to r.tvCode, "tvName" to r.tvName)
        is ProofResult.IdentityChanged -> J("result" to "identity-changed", "keyId" to r.keyId, "fingerprint" to r.fingerprint, "tvCode" to r.tvCode, "tvName" to r.tvName)
    }

    private fun tamper(token: String, edit: (Envelope) -> List<String>, resign: String? = null): String {
        val e = Envelope.decode(token)!!; val body = edit(e)
        val sig = resign?.let { key(it).install.sign(Envelope.payload(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body)) } ?: e.signature
        return Envelope(e.type, e.keyId, e.seq, e.nonce, e.issuedAt, e.notBefore, e.expiresAt, e.target, body, sig).encode()
    }

    private fun proofCase(id: String, why: String, token: String, pinned: String? = "tv-install-1", now: Long = t0 + 60_000, expectNonce: String = nonce, consumed: List<String> = emptyList(),
                          lastSeq: Long? = null, trusted: List<String> = listOf("desk", "support"), revoked: List<String> = emptyList()): Map<String, Any?> {
        val r = TvProof.verify(token, expectNonce, pinOf(pinned), ringOf(trusted, revoked), now, consumed.toSet(), lastSeq)
        return J("type" to "proof", "id" to id, "description" to why, "token" to token, "nonce" to expectNonce, "pinned" to pinned, "trustedKeys" to trusted, "revokedKeys" to revoked,
            "consumed" to consumed, "lastSeq" to lastSeq, "nowMs" to now, "expect" to outcome(r))
    }

    private fun buildCase(id: String, why: String, install: String = "tv-install-1", dev: String = "tvA", seq: Long = 5, now: Long = t0, acc: Acc = Acc(), activation: String? = act(), name: String = "Salon", grace: Boolean = false, uptime: Long = 12_345) =
        J("type" to "build-proof", "id" to id, "description" to why, "installKey" to install, "device" to dev, "nonce" to nonce, "seq" to seq, "tvClockNow" to now, "name" to name, "uptimeMs" to uptime, "grace" to grace,
            "access" to acc.json(), "activation" to activation, "expect" to J("token" to build(install, dev, nonce, seq, now, acc, activation, name, grace, uptime)))

    private fun generate(): Map<String, Any?> {
        val out = ArrayList<Map<String, Any?>>()
        val good = build()
        out += proofCase("proof-valid", "TV de production liée : preuve acceptée", good)
        out += proofCase("proof-valid-super", "activation super : acceptée, super=1, pas de fin",
            build(acc = Acc(superUnlimited = true), activation = act(rights = listOf(Right.Super("super", t0)))))
        out += proofCase("proof-valid-usage-end", "plafond d'usage : endsAt de la preuve = fin du plafond",
            build(activation = act(rights = listOf(Right.Usage(t0 - hour, t0 + 90 * day)))))
        out += proofCase("proof-valid-months-later", "l'activation hors de sa fenêtre d'installation de 48 h compte toujours : preuve du jour J+100", build(now = t0 + 100 * day, activation = act()), now = t0 + 100 * day)
        out += proofCase("proof-first-link-needs-pin", "aucune clé épinglée : première liaison, l'appelant décide du TOFU", good, pinned = null)
        out += proofCase("proof-identity-changed", "TV réinstallée (autre clé d'installation) : confirmation demandée, rien d'accepté", build(install = "tv-install-2"))
        out += proofCase("proof-nonce-replayed", "nonce déjà consommé", good, consumed = listOf(nonce))
        out += proofCase("proof-nonce-different", "nonce différent de celui du défi", good, expectNonce = "ff".repeat(32))
        out += proofCase("proof-bad-signature", "état modifié sans resigner", tamper(build(acc = Acc(trial = true)), { e -> e.body.map { if (it.startsWith("state=")) "state=production" else it } }))
        out += proofCase("proof-old-sequence", "seq plus ancien que le dernier vu", build(seq = 4), lastSeq = 5)
        out += proofCase("proof-window-closed", "fenêtre de 10 min fermée (au-delà de la tolérance d'horloge)", good, now = t0 + 3 * day)
        out += proofCase("proof-state-trial", "TV d'essai", build(acc = Acc(trial = true)))
        out += proofCase("proof-state-suspended", "TV suspendue : vérifiez l'heure", build(acc = Acc(suspended = true)))
        out += proofCase("proof-state-grace", "TV en grâce", build(acc = Acc(keyInstalled = false), grace = true, activation = null))
        out += proofCase("proof-state-degraded", "mode réduit signé par la TV (w4-07)", tamper(good, { e -> e.body.map { if (it.startsWith("state=")) "state=degraded" else it } }, resign = "tv-install-1"))
        out += proofCase("proof-no-activation", "TV sans aucune activation : verrouillée", build(acc = Acc(keyInstalled = false), activation = null))
        out += proofCase("proof-activation-trial", "activation d'essai présentée comme production", build(activation = act(kind = ActivationKind.TRIAL, rights = listOf(Right.Usage(t0 - hour, t0 + 30 * day)))))
        out += proofCase("proof-activation-usage-passed", "plafond d'usage de l'activation dépassé", build(activation = act(rights = listOf(Right.Usage(t0 - 40 * day, t0 - day)))))
        out += proofCase("proof-code-mismatch", "activation d'une autre TV", build(activation = act(dev = "tvB")))
        out += proofCase("proof-activation-unknown-key", "activation signée par une clé inconnue", build(activation = act(signer = "rogue")))
        out += proofCase("proof-activation-key-without-scope", "activation signée par une clé sans portée de production", build(activation = act(signer = "support")))
        out += proofCase("proof-activation-key-revoked", "activation signée par une clé révoquée", good, revoked = listOf("desk"))
        out += buildCase("build-production", "même entrées, mêmes octets : TV de production")
        out += buildCase("build-super", "même entrées, mêmes octets : super", acc = Acc(superUnlimited = true, label = "Super illimité"), activation = act(rights = listOf(Right.Super("super", t0))))
        out += buildCase("build-trial-long-name", "essai, nom coupé à 40 caractères et sans retour à la ligne", acc = Acc(trial = true, label = "Version d'essai"), activation = null, name = "Salon\nde la maison avec un nom vraiment trop long pour tenir")
        out += buildCase("build-grace", "grâce", acc = Acc(keyInstalled = false, label = "Aucune clé installée"), activation = null, grace = true, install = "tv-install-2", dev = "tvB", seq = 9)
        return J("format" to "castbridge-proof-vectors-v1",
            "note" to "Clés de TEST dérivées de chaînes publiques (SHA-256(\"castbridge-test-vector-key|<nom>\")), sans valeur. Régénération : CASTBRIDGE_WRITE_VECTORS=1 gradle :core:test --tests '*ProofVectorsTest*'",
            "keys" to keys.map { J("name" to it.name, "seed" to hex(it.seed), "publicKey" to it.signer.publicKeyBase64, "kid" to it.signer.keyId, "scopes" to it.scopes?.map { s -> s.name }?.sorted(), "fingerprint" to it.install.fingerprintText()) },
            "devices" to devices.map { (n, f) -> J("name" to n, "code" to DeviceCode.of(f), "k" to DeviceIdentity.kFor(f.n), "fingerprints" to f.byKind.entries.associate { it.key.name to it.value }) },
            "cases" to out)
    }

    private fun pretty(v: Any?, indent: String = ""): String = when (v) {
        is Map<*, *> -> v.entries.filter { it.value != null }.let { es -> if (es.isEmpty()) "{}" else "{\n" + es.joinToString(",\n") { "$indent  ${JsonLite.quote(it.key.toString())}: ${pretty(it.value, "$indent  ")}" } + "\n$indent}" }
        is List<*> -> if (v.isEmpty()) "[]" else if (v.all { it !is Map<*, *> && it !is List<*> }) "[" + v.joinToString(", ") { pretty(it) } + "]" else "[\n" + v.joinToString(",\n") { "$indent  ${pretty(it, "$indent  ")}" } + "\n$indent]"
        else -> JsonLite.write(v)
    }

    private fun file(): File {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "tools/activation").isDirectory && !File(d, "docs").isDirectory) d = d.parentFile
        return File(d ?: File("."), "tools/activation/proof-vectors.json")
    }

    @Test fun vectorsAreUpToDate() {
        val text = pretty(generate()) + "\n"
        val f = file()
        if (System.getenv("CASTBRIDGE_WRITE_VECTORS") == "1") { f.parentFile.mkdirs(); f.writeText(text) }
        assertTrue(f.isFile, "tools/activation/proof-vectors.json is missing: run with CASTBRIDGE_WRITE_VECTORS=1")
        assertEquals(f.readText(), text, "the vectors differ from what the code produces: regenerate with CASTBRIDGE_WRITE_VECTORS=1 if the format change is intended")
    }

    @Test fun thereAreEnoughVectorsAcceptedAndRefused() {
        val cases = (JsonLite.obj(file().readText())["cases"] as List<*>).map { it as Map<*, *> }
        assertTrue(cases.size >= 12, "${cases.size} cases")
        val results = cases.filter { it["type"] == "proof" }.map { (it["expect"] as Map<*, *>)["result"] }
        assertTrue(results.count { it == "accepted" } >= 3 && results.count { it == "rejected" } >= 10 && "needs-pin" in results && "identity-changed" in results, results.groupingBy { it }.eachCount().toString())
    }

    /** A harness that only reads the JSON file, as another implementation would: every case must give the recorded result. */
    @Test fun everyCommittedVectorPassesTheBuilderAndTheVerifier() {
        val root = JsonLite.obj(file().readText())
        assertEquals("castbridge-proof-vectors-v1", root["format"])
        @Suppress("UNCHECKED_CAST") val ks = (root["keys"] as List<Map<String, Any?>>).associateBy { it["name"] as String }
        @Suppress("UNCHECKED_CAST") val ds = (root["devices"] as List<Map<String, Any?>>).associateBy { it["name"] as String }
        fun seed(n: String) = ks.getValue(n)["seed"].toString().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun fpOf(n: String) = Fingerprints((ds.getValue(n)["fingerprints"] as Map<*, *>).entries.associate { FactorKind.valueOf(it.key as String) to it.value as String })
        fun trusted(names: List<*>) = names.map { n -> val k = ks.getValue(n as String); TrustedKey(k["kid"] as String, k["publicKey"] as String, (k["scopes"] as List<*>).map { KeyScope.valueOf(it as String) }.toSet()) }
        var checked = 0
        for (c in (root["cases"] as List<*>).map { @Suppress("UNCHECKED_CAST") (it as Map<String, Any?>) }) {
            val id = c["id"] as String; @Suppress("UNCHECKED_CAST") val expect = c["expect"] as Map<String, Any?>
            when (c["type"]) {
                "proof" -> {
                    val pinned = (c["pinned"] as String?)?.let { n -> InstallSigner(seed(n)).let { TrustedKey(it.keyId, it.publicKeyBase64) } }
                    val ring = KeyRing(trusted(c["trustedKeys"] as List<*>), (c["revokedKeys"] as List<*>).map { ks.getValue(it as String)["kid"] as String }.toSet())
                    val r = TvProof.verify(c["token"] as String, c["nonce"] as String, pinned, ring, (c["nowMs"] as Number).toLong(), (c["consumed"] as List<*>).map { it as String }.toSet(), (c["lastSeq"] as Number?)?.toLong())
                    assertEquals(JsonLite.write(expect), JsonLite.write(outcome(r)), id)
                }
                "build-proof" -> {
                    @Suppress("UNCHECKED_CAST") val a = c["access"] as Map<String, Any?>
                    val acc = TvAccess(a["keyInstalled"] as Boolean, Access.TRIAL_ONLY, null, a["label"] as String, a["superUnlimited"] as Boolean, a["trial"] as Boolean, a["suspended"] as Boolean)
                    val token = TvProof.build(InstallSigner(seed(c["installKey"] as String)), fpOf(c["device"] as String), c["nonce"] as String, (c["seq"] as Number).toLong(), (c["tvClockNow"] as Number).toLong(),
                        acc, c["activation"] as String?, c["name"] as String, c["grace"] as Boolean, (c["uptimeMs"] as Number).toLong())
                    assertEquals(expect["token"], token, id)
                }
                else -> fail("type inconnu : ${c["type"]}")
            }
            checked++
        }
        assertEquals((root["cases"] as List<*>).size, checked)
    }
}
