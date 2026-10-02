package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.*

/**
 * Replays tools/activation/rental-vectors-v2.json (docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md § 6): X25519 (RFC 7748), the installation key, the v2 rental box, the v1 sunset, the
 * v2 device request and a whole v2 activation, byte for byte. Keys, devices, master and installations in the file are TEST data derived from public strings. The v1 file
 * (rental-vectors.json) is separate and untouched. Returns the failures (empty = all good).
 */
object RentalVectorsV2 {
    const val FORMAT = "castbridge-rental-vectors-v2"
    private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** The installations of the file: name to key pair (from the seed). */
    @Suppress("UNCHECKED_CAST")
    fun installs(root: Map<String, Any?>): Map<String, InstallKey> =
        (root["installs"] as List<Map<String, Any?>>).associate { it.str("name")!! to InstallKey.fromSeed(unhex(it.str("seed")!!)) }

    class Ctx(val env: RentalVectors.Env, val installs: Map<String, InstallKey>)

    @Suppress("UNCHECKED_CAST")
    fun run(json: String): List<String> {
        val root = JsonLite.obj(json)
        if (root.str("format") != FORMAT) return listOf("format inconnu")
        val ctx = Ctx(RentalVectors.env(root), installs(root))
        val fails = ArrayList<String>()
        for (c in root["cases"] as List<Map<String, Any?>>) {
            val id = c.str("id")!!
            val problem = try {
                when (c.str("type")) {
                    "x25519" -> x25519(c)
                    "x25519-shared" -> shared(c)
                    "install-key" -> installKey(c, ctx)
                    "box-v2" -> boxV2(c, ctx)
                    "box-v2-reissue" -> reissue(c, ctx)
                    "box-v2-mixed" -> mixed(c, ctx)
                    "box-v1-sunset" -> sunset(c, ctx)
                    "request-v2" -> request(c)
                    "build-activation-v2" -> build(c, ctx)
                    else -> "type de vecteur inconnu"
                }
            } catch (x: Exception) { "exception ${x::class.simpleName}: ${x.message}" }
            if (problem != null) fails += "$id : $problem"
        }
        return fails
    }

    @Suppress("UNCHECKED_CAST")
    private fun x25519(c: Map<String, Any?>): String? =
        if (hex(X25519.scalarMult(unhex(c.str("scalar")!!), unhex(c.str("u")!!))) == (c["expect"] as Map<String, Any?>).str("out")) null else "résultat X25519 différent"

    @Suppress("UNCHECKED_CAST")
    private fun shared(c: Map<String, Any?>): String? {
        val out = X25519.sharedSecret(unhex(c.str("priv")!!), unhex(c.str("pub")!!)); val exp = c["expect"] as Map<String, Any?>
        return if (exp["refused"] == true) { if (out != null) "devait être refusé (point d'ordre faible)" else null } else if (out == null) "refusé à tort" else if (hex(out) != exp.str("shared")) "secret partagé différent" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun installKey(c: Map<String, Any?>, x: Ctx): String? {
        val k = x.installs.getValue(c.str("install")!!); val exp = c["expect"] as Map<String, Any?>
        return if (hex(k.pub) != exp.str("pub")) "clé publique différente" else if (k.installId != exp.str("installId")) "identifiant d'installation différent" else null
    }

    /** The rental key of a request entry (the issuer's derivation, same as the v1 file). */
    private fun keyOf(x: Ctx, r: Map<String, Any?>): ByteArray {
        val dev = x.env.devices.getValue(r.str("device")!!); val license = r.str("license")!!
        return RentalKeys.rentalKey(x.env.master, license, SeatIds.of(license, dev), r.str("product")!!, r.long("period")!!)
    }

    @Suppress("UNCHECKED_CAST")
    private fun boxV2(c: Map<String, Any?>, x: Ctx): String? {
        val r = c["request"] as Map<String, Any?>; val exp = c["expect"] as Map<String, Any?>; val key = keyOf(x, r)
        val dev = x.env.devices.getValue(r.str("device")!!); val ik = x.installs.getValue(r.str("install")!!); val product = r.str("product")!!; val period = r.long("period")!!
        val box = RentalKeys.makeBoxV2(ik.pub, key, product, period, unhex(r.str("ephSeed")!!))
        if (box != exp.str("box")) return "enveloppe différente (octets)"
        val ok = RentalKeys.openBox(box, dev, product, period, ik) as? BoxResult.Key ?: return "enveloppe illisible avec la clé d'installation"
        if (RentalKeys.fingerprintOf(ok.bytes) != exp.str("keyFingerprint")) return "clé différente"
        if (RentalKeys.openBox(box, dev, product, period, null) != BoxResult.NeedsInstallKey) return "les empreintes seules ne doivent rien ouvrir"
        r.str("other")?.let { if (RentalKeys.openBox(box, dev, product, period, x.installs.getValue(it)) != BoxResult.OtherInstall) return "ouverte par une autre installation" }
        if (RentalKeys.openBox(box, dev, "$product-x", period, ik) != BoxResult.OtherInstall) return "ouverte pour un autre produit"
        if (RentalKeys.openBox(box, dev, product, period + 1, ik) != BoxResult.OtherInstall) return "ouverte pour une autre période"
        val altered = box.dropLast(1) + (if (box.last() == 'A') 'B' else 'A')
        if (RentalKeys.openBox(altered, dev, product, period, ik) != BoxResult.OtherInstall) return "enveloppe altérée ouverte"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun reissue(c: Map<String, Any?>, x: Ctx): String? {
        val r = c["request"] as Map<String, Any?>; val exp = c["expect"] as Map<String, Any?>; val key = keyOf(x, r)
        val dev = x.env.devices.getValue(r.str("device")!!); val product = r.str("product")!!; val period = r.long("period")!!
        val a = x.installs.getValue(r.str("installA")!!); val b = x.installs.getValue(r.str("installB")!!)
        val boxA = RentalKeys.makeBoxV2(a.pub, key, product, period, unhex(r.str("ephSeedA")!!)); val boxB = RentalKeys.makeBoxV2(b.pub, key, product, period, unhex(r.str("ephSeedB")!!))
        if (boxA != exp.str("boxA") || boxB != exp.str("boxB")) return "enveloppe différente (octets)"
        if (boxA == boxB) return "deux installations, une seule enveloppe"
        val ka = RentalKeys.openBox(boxA, dev, product, period, a) as? BoxResult.Key ?: return "A ne rouvre pas sa boîte"
        val kb = RentalKeys.openBox(boxB, dev, product, period, b) as? BoxResult.Key ?: return "B ne rouvre pas sa boîte"
        if (RentalKeys.fingerprintOf(ka.bytes) != exp.str("keyFingerprint") || RentalKeys.fingerprintOf(kb.bytes) != exp.str("keyFingerprint")) return "la réémission doit garder la même clé de location"
        if (RentalKeys.openBox(boxA, dev, product, period, b) != BoxResult.OtherInstall) return "la boîte de A s'ouvre chez B"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun mixed(c: Map<String, Any?>, x: Ctx): String? {
        val r = c["request"] as Map<String, Any?>; val key = keyOf(x, r); val dev = x.env.devices.getValue(r.str("device")!!); val ik = x.installs.getValue(r.str("install")!!)
        val product = r.str("product")!!; val period = r.long("period")!!
        val v2 = RentalKeys.makeBoxV2(ik.pub, key, product, period, unhex(r.str("ephSeed")!!)); val v1 = RentalKeys.makeBox(dev, DeviceIdentity.kFor(dev.n), key, product, period)
        val box = "$v2;$v1"
        if (box != (c["expect"] as Map<String, Any?>).str("box")) return "enveloppe mixte différente"
        return if (RentalKeys.openBox(box, dev, product, period, ik) != BoxResult.Unreadable) "une boîte mixte v1/v2 doit être illisible" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun sunset(c: Map<String, Any?>, x: Ctx): String? {
        val r = c["request"] as Map<String, Any?>; val exp = c["expect"] as Map<String, Any?>; val key = keyOf(x, r); val dev = x.env.devices.getValue(r.str("device")!!)
        val product = r.str("product")!!; val period = r.long("period")!!; val box = RentalKeys.makeBox(dev, DeviceIdentity.kFor(dev.n), key, product, period)
        if (box != exp.str("box")) return "enveloppe v1 différente (octets)"
        val sunsetMs = exp.long("sunsetMs")!!
        if (sunsetMs != RentalKeys.V1_BOX_SUNSET_MS) return "date de coucher différente"
        if (RentalKeys.openBox(box, dev, product, period, null, sunsetMs - 1) !is BoxResult.Key) return "v1 refusée avant le coucher"
        if (RentalKeys.openBox(box, dev, product, period, null, sunsetMs) != BoxResult.V1Expired) return "v1 acceptée au coucher"
        if (RentalKeys.openBox(box, dev, product, period, null, sunsetMs + 86_400_000L) != BoxResult.V1Expired) return "v1 acceptée après le coucher"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun request(c: Map<String, Any?>): String? {
        val info = OwnerFrames.parseDeviceInfo(c.str("text")!!); val exp = c["expect"] as Map<String, Any?>
        if (exp["unreadable"] == true) return if (info != null) "devait être illisible" else null
        if (info == null) return "demande illisible"
        if (info.code != exp.str("code") || info.k.toLong() != exp.long("k")) return "code ou k différent"
        if (info.installPub?.let(::hex) != exp.str("installPub")) return "clé d'installation différente"
        if (info.fp.byKind.map { "${it.key.name}|${it.value}" } != exp["factors"]) return "facteurs différents"
        if (info.unknown != exp["unknown"]) return "lignes inconnues différentes"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun build(c: Map<String, Any?>, x: Ctx): String? {
        val r = c["request"] as Map<String, Any?>; val (signer, scopes) = x.env.keys.getValue(c.str("signer")!!); val dev = x.env.devices.getValue(r.str("device")!!)
        val license = r.str("license")!!; val issuedAt = r.long("issuedAt")!!; val installPub = r.str("install")?.let { x.installs.getValue(it).pub }
        val out = try {
            val rights = (r["rentals"] as List<Map<String, Any?>>).map { m ->
                val period = m.long("period") ?: m.long("startsAt")!!
                RentalIssuing.right(RentalSpec(m.str("product")!!, m["bundles"] as List<String>, m.long("days")!!.toInt(), m.long("usage")?.toInt() ?: 0, ((m.long("graceMs") ?: 0L) / 86_400_000L).toInt(),
                    m.long("concurrent")?.toInt() ?: 0, period), issuedAt, license, SeatIds.of(license, dev), dev, x.env.master, installPub, r.str("ephSeed")?.let(::unhex))
            }
            ActivationIssuer(signer, scopes).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(dev), dev, issuedAt, Subject.TV, rights, license, null, issuedAt, r.long("windowHours")!!.toInt(), r.str("nonce"), null)).token
        } catch (e: IssueException) { null }
        val exp = c["expect"] as Map<String, Any?>
        return if (exp["refused"] == true) { if (out != null) "devait être refusé" else null } else if (out == null) "refusé à tort" else if (out != exp.str("token")) "jeton différent (octets)" else null
    }
}
