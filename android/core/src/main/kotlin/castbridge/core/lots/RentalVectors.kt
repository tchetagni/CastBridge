package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.*

/**
 * Replays tools/activation/rental-vectors.json (docs/RENTAL-LOTS.md § 8): the common vectors of the rental right, replayed by the core, the desk tool and (later) the server port.
 * Same inputs, same bytes (tokens, boxes, sealed lots) and same states (the clock rules). A separate file: no existing vector of tools/activation/test-vectors.json is touched.
 * Returns the failures (empty = all good). The keys and devices in the file are TEST data derived from public strings.
 */
object RentalVectors {
    /** The vectors v1/v2 describe the sentences and thresholds of before W16: per-unit messages are off, explicitly. */
    private val LEGACY = RentalConfig(perUnitMessages = false)

    const val FORMAT = "castbridge-rental-vectors-v1"
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    class Env(val keys: Map<String, Pair<Ed25519Signer, Set<KeyScope>>>, val devices: Map<String, Fingerprints>, val master: ByteArray)

    @Suppress("UNCHECKED_CAST")
    fun env(root: Map<String, Any?>): Env {
        val keys = (root["keys"] as List<Map<String, Any?>>).associate { k -> k.str("name")!! to (Ed25519Signer(hex(k.str("seed")!!)) to (k["scopes"] as List<String>).map { KeyScope.valueOf(it) }.toSet()) }
        val devices = (root["devices"] as List<Map<String, Any?>>).associate { d ->
            val r = d["raw"] as Map<String, Any?>
            d.str("name")!! to DeviceIdentity.fingerprints(RawFactors(r.str("flashSerial"), r.str("flashCid"), r.str("ethernetMac"), r.str("wifiMac"), r.str("wifiSysfsPath"), r.str("systemSerial"), r.str("bluetoothAddress")))
        }
        return Env(keys, devices, hex(root.str("master")!!))
    }

    /** The right of one request entry (also used by the generator): the box is built from the issuer's master. */
    @Suppress("UNCHECKED_CAST")
    fun rightOf(e: Env, r: Map<String, Any?>, license: String, device: Fingerprints): Right.Rental {
        val product = r.str("product")!!; val period = r.long("period") ?: r.long("startsAt")!!
        val key = RentalKeys.rentalKey(e.master, license, SeatIds.of(license, device), product, period)
        return Right.Rental(product, (r["bundles"] as List<String>), r.long("startsAt")!!, period, r.long("days")!!.toInt(), r.long("graceMs") ?: 0L, r.long("usage")?.toInt() ?: 0, r.long("concurrent")?.toInt() ?: 0,
            RentalKeys.makeBox(device, DeviceIdentity.kFor(device.n), key, product, period))
    }

    @Suppress("UNCHECKED_CAST")
    fun run(json: String): List<String> {
        val root = JsonLite.obj(json)
        val fails = ArrayList<String>()
        if (root.str("format") != FORMAT) return listOf("format inconnu")
        val e = env(root)
        for (c in root["cases"] as List<Map<String, Any?>>) {
            val id = c.str("id")!!
            val problem = try {
                when (c.str("type")) {
                    "build-activation" -> build(c, e)
                    "rental-state" -> state(c, e)
                    "old-device" -> oldDevice(c, e)
                    "lot-policy" -> policy(c)
                    "seal" -> seal(c, e)
                    "box" -> box(c, e)
                    else -> "type de vecteur inconnu"
                }
            } catch (x: Exception) { "exception ${x::class.simpleName}: ${x.message}" }
            if (problem != null) fails += "$id : $problem"
        }
        return fails
    }

    @Suppress("UNCHECKED_CAST")
    private fun build(c: Map<String, Any?>, e: Env): String? {
        val r = c["request"] as Map<String, Any?>; val (signer, scopes) = e.keys.getValue(c.str("signer")!!); val dev = e.devices.getValue(r.str("device")!!)
        val license = r.str("license")!!
        val rights = (r["rentals"] as List<Map<String, Any?>>).map { rightOf(e, it, license, dev) } + (r["rights"] as? List<String>).orEmpty().map { Activation.parseRight(it) }
        val req = ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(dev), dev, r.long("issuedAt")!!, Subject.TV, rights, license, null, r.long("issuedAt")!!, r.long("windowHours")!!.toInt(), r.str("nonce"), null)
        val out = try { ActivationIssuer(signer, scopes).issue(req).token } catch (x: IssueException) { null }
        val exp = c["expect"] as Map<String, Any?>
        return if (exp["refused"] == true) { if (out != null) "devait être refusé" else null } else if (out == null) "refusé à tort" else if (out != exp.str("token")) "jeton différent (octets)" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun statusJson(s: RentalStatus): Map<String, Any?> = linkedMapOf("key" to s.key, "state" to s.state.name, "reason" to s.reason?.name, "doubt" to s.doubt?.name, "remainingMs" to s.remainingMs,
        "remainingUsageMinutes" to s.remainingUsageMinutes, "warning" to s.warning.name, "message" to s.message)

    /** The states the engine gives for a case (the generator stores them after checking them against the hand-written expectation). */
    @Suppress("UNCHECKED_CAST")
    fun statesOf(c: Map<String, Any?>, e: Env): List<Map<String, Any?>> {
        val dev = e.devices.getValue(c.str("device")!!)
        val acts = (c["tokens"] as List<String>).map { t -> Activation.decode(t) ?: error("jeton illisible") }
        val clock = c["clock"] as Map<String, Any?>
        val used = (c["usedMinutes"] as? Map<String, Any?>).orEmpty().mapValues { (it.value as Number).toLong() }
        val expired = (c["expired"] as? Map<String, Any?>).orEmpty().mapValues { ExpiryReason.valueOf(it.value as String) }
        val tv = TvClock(clock.long("lastSeen") ?: 0L, clock.long("floor") ?: 0L)
        val st = RentalEngine.evaluate(RentalEngine.contracts(acts), RentalInputs(RentalEngine.judge(tv, c.long("wallMs")!!), used, expired, RentalEngine.superUnlimited(acts)), LEGACY)
        check(dev.n > 0)
        return st.map(::statusJson)
    }

    @Suppress("UNCHECKED_CAST")
    private fun state(c: Map<String, Any?>, e: Env): String? {
        val dev = e.devices.getValue(c.str("device")!!)
        // every token must be a valid activation of that device at its issue time (same checks as the TV)
        val ring = KeyRing(e.keys.values.map { (s, sc) -> s.trusted(sc) })
        for (t in c["tokens"] as List<String>) {
            val a = Activation.decode(t) ?: return "jeton illisible"
            val v = ActivationVerifier(ring).verify(t, dev, a.issuedAt)
            if (v !is ActivationResult.Accepted) return "jeton refusé à l'installation : $v"
        }
        val got = statesOf(c, e); val want = c["expect"] as List<Map<String, Any?>>
        if (got.size != want.size) return "${got.size} locations au lieu de ${want.size}"
        for ((g, w) in got.zip(want)) {
            for (k in w.keys) if ((g[k]?.let { (it as? Number)?.toLong() ?: it }) != (w[k]?.let { (it as? Number)?.toLong() ?: it })) return "${w["key"]} : $k = ${g[k]} au lieu de ${w[k]}"
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun oldDevice(c: Map<String, Any?>, e: Env): String? {
        val dev = e.devices.getValue(c.str("device")!!)
        val ring = KeyRing(e.keys.values.map { (s, sc) -> s.trusted(sc) })
        val t = c.str("token")!!
        val v = ActivationVerifier(ring).verify(t, dev, c.long("nowMs")!!)
        if (v !is ActivationResult.Accepted) return "devait être accepté (droit inconnu ignoré) : $v"
        val canon = Activation.decode(t)!!.encode()
        if (canon != t) return "la forme canonique ne se reconstruit pas à l'identique"
        val access = TvGate.evaluate(listOf(v.activation), emptyList(), c.long("nowMs")!!).access
        val want = ((c["expect"] as Map<String, Any?>)["granted"] as List<String>).toSet()
        return if (access.granted != want) "accordé ${access.granted} au lieu de $want" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun policy(c: Map<String, Any?>): String? {
        val m = c["lot"] as Map<String, Any?>
        val ed = if (m.str("edition") == "trial") Edition.TRIAL else Edition.FULL
        val id = LotId(m.str("feature")!!, m.str("scope")!!)
        val meta = LotMeta(id, 1, 1, "a".repeat(64), m.str("title") ?: "", 0, ed)
        val fam = LotFamilies.explicit((c["free"] as List<String>).toSet(), (c["reserved"] as List<String>).toSet())
        val refused = RentalPolicy.refusal(meta, fam) != null
        return if (refused != ((c["expect"] as Map<String, Any?>)["refused"] as Boolean)) "refus = $refused" else null
    }

    @Suppress("UNCHECKED_CAST")
    private fun seal(c: Map<String, Any?>, e: Env): String? {
        val r = c["request"] as Map<String, Any?>; val dev = e.devices.getValue(r.str("device")!!)
        val key = RentalKeys.rentalKey(e.master, r.str("license")!!, SeatIds.of(r.str("license")!!, dev), r.str("product")!!, r.long("period")!!)
        val id = LotId(r.str("feature")!!, r.str("scope")!!); val version = r.long("version")!!.toInt()
        val plain = hex(r.str("plainHex")!!); val exp = c["expect"] as Map<String, Any?>
        val sealed = RentalKeys.seal(key, id, version, plain)
        if (hex(sealed) != exp.str("sealedHex")) return "lot chiffré différent (octets)"
        if (RentalKeys.fingerprintOf(key) != exp.str("keyFingerprint")) return "clé de location différente"
        if (!RentalKeys.open(key, id, version, sealed)!!.contentEquals(plain)) return "relecture impossible"
        if (RentalKeys.open(key, id, version + 1, sealed) != null) return "lu avec une autre version"
        if (RentalKeys.open(null, id, version, sealed) != null) return "lu sans clé"
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun box(c: Map<String, Any?>, e: Env): String? {
        val r = c["request"] as Map<String, Any?>; val dev = e.devices.getValue(r.str("device")!!); val other = e.devices.getValue(r.str("otherDevice")!!)
        val rental = rightOf(e, r, r.str("license")!!, dev)
        val exp = c["expect"] as Map<String, Any?>
        if (rental.box != exp.str("box")) return "enveloppe différente (octets)"
        val key = RentalKeys.openBox(rental.box, dev, rental.productId, rental.period) ?: return "enveloppe illisible sur le bon appareil"
        if (RentalKeys.fingerprintOf(key) != exp.str("keyFingerprint")) return "clé différente"
        if (RentalKeys.openBox(rental.box, other, rental.productId, rental.period) != null) return "ouverte sur un autre appareil"
        return null
    }
}
