package castbridge.desktop

import castbridge.core.lots.LotId
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationIssuer
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.ActivationResult
import castbridge.core.owner.ActivationVerifier
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceIdentity
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.KeyRing
import castbridge.core.owner.KeyScope
import castbridge.core.owner.Power
import castbridge.core.owner.RawFactors
import castbridge.core.owner.RevocationState
import castbridge.core.owner.Subject
import java.io.File

/**
 * Replays the common test vectors (tools/activation/test-vectors.json) through the SAME library the three tools use: same inputs, same bytes. `selftest` runs it for the owner
 * (« mon outil produit-il bien ce que la TV attend ? »), and the unit tests run it on every build. Returns the list of failures (empty = all good).
 */
object Vectors {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    class Key(val name: String, val signer: Ed25519Signer, val scopes: Set<KeyScope>)
    class Dev(val name: String, val raw: RawFactors, val fp: Fingerprints, val code: String)

    @Suppress("UNCHECKED_CAST")
    fun run(file: File): List<String> {
        val root = JsonLite.obj(file.readText())
        val keys = (root["keys"] as List<Map<String, Any?>>).associate { k ->
            k.str("name")!! to Key(k.str("name")!!, Ed25519Signer(hex(k.str("seed")!!)), (k["scopes"] as List<String>).map { KeyScope.valueOf(it) }.toSet())
        }
        val devices = (root["devices"] as List<Map<String, Any?>>).associate { d ->
            val r = d["raw"] as Map<String, Any?>
            val raw = RawFactors(r.str("flashSerial"), r.str("flashCid"), r.str("ethernetMac"), r.str("wifiMac"), r.str("wifiSysfsPath"), r.str("systemSerial"), r.str("bluetoothAddress"))
            val fp = DeviceIdentity.fingerprints(raw)
            d.str("name")!! to Dev(d.str("name")!!, raw, fp, d.str("code")!!)
        }
        val fails = ArrayList<String>()
        var count = 0
        for (c in root["cases"] as List<Map<String, Any?>>) {
            val id = c.str("id")!!
            val problem = try {
                when (c.str("type")) {
                    "fingerprints" -> fingerprints(c, id)
                    "build-activation" -> buildActivation(c, keys, devices)
                    "build-compact" -> buildCompact(c, keys)
                    "build-command" -> buildCommand(c, keys, devices)
                    "activation" -> activation(c, keys, devices)
                    else -> null.also { count-- }
                }
            } catch (e: Exception) { "exception ${e::class.simpleName}: ${e.message}" }
            count++
            if (problem != null) fails += "$id : $problem"
        }
        if (count == 0) fails += "aucun vecteur rejoué"
        lastCount = count
        return fails
    }

    /** Number of vectors replayed by the last [run] (shown by `selftest`). */
    @Volatile var lastCount = 0

    private fun fingerprints(c: Map<String, Any?>, id: String): String? {
        @Suppress("UNCHECKED_CAST") val r = c["raw"] as Map<String, Any?>
        val fp = DeviceIdentity.fingerprints(RawFactors(r.str("flashSerial"), r.str("flashCid"), r.str("ethernetMac"), r.str("wifiMac"), r.str("wifiSysfsPath"), r.str("systemSerial"), r.str("bluetoothAddress")))
        @Suppress("UNCHECKED_CAST") val e = c["expect"] as Map<String, Any?>
        val want = (e["fingerprints"] as Map<String, String>)
        if (fp.byKind.mapKeys { it.key.name } != want) return "empreintes différentes"
        val code = e["code"] as String?
        if (fp.n > 0 && DeviceCode.of(fp) != code) return "code d'appareil différent"
        if (fp.n == 0 && code != null) return "code attendu absent"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun rightsOf(r: Map<String, Any?>) = (r["rights"] as List<String>).map { Activation.parseRight(it) }

    @Suppress("UNCHECKED_CAST")
    private fun buildActivation(c: Map<String, Any?>, keys: Map<String, Key>, devices: Map<String, Dev>): String? {
        val k = keys.getValue(c.str("signer")!!); val r = c["request"] as Map<String, Any?>; val d = devices.getValue(r.str("device")!!)
        val expect = c["expect"] as Map<String, Any?>
        val req = ActivationIssuer.Request(
            ActivationKind.valueOf(r.str("kind")!!.uppercase()), r.str("deviceCodeOverride") ?: d.code, d.fp, r.long("issuedAt")!!, Subject.valueOf(r.str("subject")!!.uppercase()),
            rightsOf(r), r.str("license")!!, r.str("seat"), r.long("notBefore")!!, (r["windowDays"] as Number).toInt(), r.str("nonce"), null)
        val out = try { ActivationIssuer(k.signer, k.scopes).issue(req).token } catch (e: castbridge.core.owner.IssueException) { null }
        return if (expect["refused"] == true) { if (out != null) "devait être refusé" else null }
        else if (out == null) "refusé à tort" else if (out != expect.str("token")) "jeton différent (octets)" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildCompact(c: Map<String, Any?>, keys: Map<String, Key>): String? {
        val k = keys.getValue(c.str("signer")!!); val r = c["request"] as Map<String, Any?>
        val text = ActivationIssuer(k.signer, k.scopes).issueCompact(ActivationKind.valueOf(r.str("kind")!!.uppercase()), r.str("deviceCode")!!, (r["notBeforeDay"] as Number).toInt(), (r["windowDays"] as Number).toInt(), (r["setId"] as Number).toInt())
        return if (text != (c["expect"] as Map<String, Any?>).str("text")) "clé saisissable différente" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildCommand(c: Map<String, Any?>, keys: Map<String, Key>, devices: Map<String, Dev>): String? {
        val k = keys.getValue(c.str("signer")!!); val r = c["request"] as Map<String, Any?>; val d = devices.getValue(r.str("device")!!)
        val power = Power.valueOf(r.str("power")!!.uppercase())
        val token = ActivationIssuer(k.signer, k.scopes).issueCommand(power, d.fp, r.str("challenge")!!, (r["issuedAt"] as Number).toLong(), (r["days"] as Number).toInt(), r.str("action") ?: "",
            (r["bundles"] as List<String>), (r["lots"] as List<String>).map { s -> LotId(s.substringBefore(':'), s.substringAfter(':')) })
        return if (token != (c["expect"] as Map<String, Any?>).str("token")) "commande différente" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun activation(c: Map<String, Any?>, keys: Map<String, Key>, devices: Map<String, Dev>): String? {
        val trusted = (c["trustedKeys"] as List<String>).map { keys.getValue(it).let { k -> k.signer.trusted(k.scopes) } }
        val revoked = (c["revokedKeys"] as List<String>).map { keys.getValue(it).signer.keyId }.toSet()
        val seats = (c["revokedSeats"] as List<Map<String, Any?>>).associate { "${it["license"]}|${it["seat"]}" to it.long("at")!! }
        val subject = Subject.valueOf(c.str("expectSubject")!!.uppercase())
        // last sequence number already seen per signing key (the vector names the key by its alias): a LOWER number is refused (stale), the SAME one is the file reinstalled
        val lastSeq = (c["lastSeq"] as? Map<String, Any?>).orEmpty().entries.associate { (alias, v) -> keys.getValue(alias).signer.keyId to (v as Number).toLong() }
        val r = ActivationVerifier(KeyRing(trusted, revoked), revocations = RevocationState(emptySet(), seats), expect = subject, seqState = castbridge.core.owner.SeqState(lastSeq)).verify(c.str("token")!!, devices.getValue(c.str("device")!!).fp, c.long("nowMs")!!)
        val e = c["expect"] as Map<String, Any?>
        return when (r) {
            is ActivationResult.Accepted -> if (e.str("result") != "accepted") "accepté à tort" else if (e.str("license") != r.activation.license || e.str("seat") != r.activation.seat) "licence/poste différents" else null
            is ActivationResult.Rejected -> if (e.str("result") != "rejected") "refusé à tort : ${r.reason}" else if (e.str("reason") != r.reason.name) "raison ${r.reason} au lieu de ${e.str("reason")}" else null
        }
    }
}
