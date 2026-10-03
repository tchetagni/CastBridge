package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.owner.*
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Replays tools/activation/rental-pilot-vectors.json (docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md § 2.5): the pilot's decisions ([PilotRules]) and the lines they
 * sign, the engine's unit and 96 h ceiling and sentences, the use counter, the usage statement and one whole activation. Each case holds its inputs and the `expect` object that [compute]
 * must reproduce exactly; the Python replay (tools/activation/verify_vectors.py) re-derives the decisions with its own code. Keys, devices, master and installations are TEST data derived from
 * public strings. The v1 and v2 files are separate and untouched. Returns the failures (empty = all good).
 */
object RentalPilotVectors {
    const val FORMAT = "castbridge-rental-pilot-vectors-v1"
    private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private val DOUALA = ZoneOffset.ofHours(1)

    /** The label of an instant in Douala time, as written in the file next to every date that matters. */
    fun labelOf(ms: Long): String = OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), DOUALA).format(LABEL)

    @Suppress("UNCHECKED_CAST")
    fun run(json: String): List<String> {
        val root = JsonLite.obj(json)
        if (root.str("format") != FORMAT) return listOf("format inconnu")
        val fails = ArrayList<String>()
        for (c in root["cases"] as List<Map<String, Any?>>) {
            val id = c.str("id")!!
            val problem = try {
                labelProblem(c) ?: JsonLite.write(compute(root, c)).let { got -> JsonLite.write(c["expect"]).let { want -> if (got == want) null else "résultat différent : obtenu $got, attendu $want" } }
            } catch (x: Exception) { "exception ${x::class.simpleName}: ${x.message}" }
            if (problem != null) fails += "$id : $problem"
        }
        return fails
    }

    /** Every date written with its label must agree (the label is for humans and for the Python replay, which converts independently). */
    private fun labelProblem(c: Map<String, Any?>): String? {
        for ((k, l) in listOf("issuedAt" to "issuedAtLabel", "now" to "nowLabel")) {
            val ms = c.long(k); val label = c.str(l)
            if (ms != null && label != null && labelOf(ms) != label) return "$l : $label ne correspond pas à $ms ($k)"
        }
        return null
    }

    private fun params(root: Map<String, Any?>) = PilotParams.parse(JsonLite.write(root["params"]))

    @Suppress("UNCHECKED_CAST")
    private fun catalog(root: Map<String, Any?>) = BundleCatalog.parse(JsonLite.write(root["catalog"]))

    @Suppress("UNCHECKED_CAST")
    private fun families(root: Map<String, Any?>): LotFamilies {
        val f = root["families"] as Map<String, Any?>
        return LotFamilies.explicit((f["free"] as List<String>).toSet(), (f["reserved"] as List<String>).toSet())
    }

    private fun summary(m: Map<String, Any?>) = ContractSummary(m.str("product")!!, m.long("period")!!, if (m.str("unit") == "days") RentalUnit.DAYS else RentalUnit.HOURS, m.long("usage")!!.toInt(), m.long("endsAt")!!,
        m.long("reissues")?.toInt() ?: 0, m.long("endedAt"), m.str("installPub"))

    @Suppress("UNCHECKED_CAST")
    private fun stateOf(m: Map<String, Any?>) = LicenseState((m["active"] as List<Map<String, Any?>>).map(::summary), m.long("hoursLast168")!!.toInt())

    /** What the code produces for [case]: the object the file's `expect` must equal. */
    @Suppress("UNCHECKED_CAST")
    fun compute(root: Map<String, Any?>, case: Map<String, Any?>): Map<String, Any?> = when (case.str("type")) {
        "pilot-new", "pilot-extend", "pilot-reissue" -> decision(root, case)
        "engine-contract" -> engine(case)
        "meter" -> meter(case)
        "usage-report" -> usageReport(root, case)
        "build-activation" -> build(root, case)
        "line-parse" -> parseLine(case)
        else -> throw IllegalArgumentException("type de vecteur inconnu")
    }

    @Suppress("UNCHECKED_CAST")
    private fun specOf(root: Map<String, Any?>, c: Map<String, Any?>): Result<RentalSpec> {
        val p = params(root); val cat = catalog(root); val fam = families(root); val st = stateOf(c["state"] as Map<String, Any?>); val at = c.long("issuedAt")!!; val bundle = c.str("bundle")!!
        return when (c.str("type")) {
            "pilot-new" -> PilotRules.spec(Choice.parse(c.str("choice")!!), bundle, cat, at, st, p, fam)
            "pilot-extend" -> PilotRules.extend(Choice.parse(c.str("choice")!!), summary(c["existing"] as Map<String, Any?>), bundle, cat, at, st, p, fam)
            else -> PilotRules.reissue(summary(c["existing"] as Map<String, Any?>), c.long("usedMinutes")!!.toInt(), at, c.str("newInstallPub"), st, p, cat, fam)
        }
    }

    /** The line a spec signs, with the box emptied and the two dates replaced by `T` (the period) and `T2` (the issue instant when it differs). */
    private fun template(spec: RentalSpec, issuedAt: Long): String {
        val period = spec.period ?: issuedAt
        val f = RentalLines.line(Right.Rental(spec.productId, spec.bundleIds, issuedAt, period, spec.days, spec.graceDays * RentalLines.DAY_MS, spec.maxUsageMinutes, spec.maxConcurrent, "")).split("|").toMutableList()
        f[3] = if (issuedAt == period) "T" else "T2"; f[4] = "T"
        return f.joinToString("|")
    }

    private fun decision(root: Map<String, Any?>, c: Map<String, Any?>): Map<String, Any?> = specOf(root, c).fold(
        { s -> linkedMapOf("ok" to true, "days" to s.days, "usage" to s.maxUsageMinutes, "concurrent" to s.maxConcurrent, "line" to template(s, c.long("issuedAt")!!)) },
        { e -> linkedMapOf("refused" to true, "reason" to (e.message ?: "")) })

    @Suppress("UNCHECKED_CAST")
    private fun rentals(c: Map<String, Any?>) = (c["lines"] as List<Map<String, Any?>>).map { m ->
        Right.Rental(m.str("product")!!, m["bundles"] as List<String>, m.long("startsAt")!!, m.long("period")!!, m.long("days")!!.toInt(), m.long("graceMs") ?: 0L, m.long("usage")!!.toInt(), m.long("concurrent")!!.toInt(), "box")
    }

    private fun act(vararg r: Right.Rental, issuedAt: Long = 1L) = Activation(ActivationKind.PRODUCTION, Subject.TV, "k", 1, "n", issuedAt, issuedAt, issuedAt + 400 * RentalLines.DAY_MS, "lic", "seat", 1, emptyMap(), r.toList(), "sig")

    private fun engine(c: Map<String, Any?>): Map<String, Any?> {
        val contract = RentalEngine.contracts(listOf(act(*rentals(c).toTypedArray())), RentalConfig()).single()
        val expired = c.str("expired")?.let { mapOf(contract.key to ExpiryReason.valueOf(it)) } ?: emptyMap()
        val s = RentalEngine.evaluate(listOf(contract), RentalInputs(JudgedTime(c.long("now")!!, null), mapOf(contract.key to c.long("used")!!), expired), RentalConfig()).single()
        return linkedMapOf("unit" to contract.unit.name.lowercase(), "maxUsageMinutes" to contract.maxUsageMinutes, "endsAt" to contract.endsAt, "notes" to contract.notes,
            "state" to s.state.name, "reason" to s.reason?.name, "remainingUsageMinutes" to s.remainingUsageMinutes, "warning" to s.warning.name, "message" to s.message)
    }

    @Suppress("UNCHECKED_CAST")
    private fun meter(c: Map<String, Any?>): Map<String, Any?> {
        var m = UseMeter(); val minutes = ArrayList<Int>()
        for (op in c["ops"] as List<List<Any?>>) {
            fun t(i: Int) = (op[i] as Number).toLong()
            when (op[0] as String) {
                "open" -> minutes += m.open(LotNames.parseKey(op[1] as String)!!, t(2)).minutes
                "tick" -> minutes += m.tick(t(1)).minutes
                "close" -> minutes += m.close(t(1)).minutes
                "pause" -> m.pause(t(1))
                "resume" -> m.resume(t(1))
                "input" -> m.input(t(1))
                "reboot" -> m = UseMeter()
                else -> throw IllegalArgumentException("opération inconnue ${op[0]}")
            }
        }
        return linkedMapOf("minutes" to minutes)
    }

    @Suppress("UNCHECKED_CAST")
    private fun usageReport(root: Map<String, Any?>, c: Map<String, Any?>): Map<String, Any?> {
        val env = RentalVectors.env(root); val fp = env.devices.getValue("tvA"); val t0 = c.long("at")!!
        val dir = Files.createTempDirectory("pilot-vectors").toFile()
        try {
            var wall = t0
            val vault = RentalVault(File(dir, "r")); val ledger = RentalLedger(File(dir, "r"), TvClock(), RentalConfig(), { wall })
            val list = c["contracts"] as List<Map<String, Any?>>
            val rights = list.map { m -> Right.Rental(m.str("product")!!, listOf("classe-${m.str("product")}"), t0, t0, m.long("days")!!.toInt(), 0L, m.long("usage")!!.toInt(), 3, "box") }
            val acts = rights.map { act(it, issuedAt = t0) }
            acts.forEach { ledger.install(it, acts, fp, vault) }
            val families = LotFamilies.explicit(emptySet(), list.map { it.str("lot")!! }.toSet())
            list.forEachIndexed { i, m ->
                val lot = LotNames.parseKey(m.str("lot")!!)!!
                ledger.markRented("${rights[i].productId}@$t0", lot, LotMeta(lot, 1, 10, "a".repeat(64), m.str("lot")!!), families)
                ledger.recordUsage(lot, m.long("used")!!.toInt(), acts)
            }
            wall = t0 + 3 * 60_000L
            return linkedMapOf("text" to ledger.usageReport(acts, c.str("installId")!!))
        } finally { dir.deleteRecursively() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun build(root: Map<String, Any?>, c: Map<String, Any?>): Map<String, Any?> {
        val env = RentalVectors.env(root); val installs = (root["installs"] as List<Map<String, Any?>>).associate { it.str("name")!! to InstallKey.fromSeed(unhex(it.str("seed")!!)) }
        val (signer, scopes) = env.keys.getValue(c.str("signer")!!); val dev = env.devices.getValue(c.str("device")!!)
        val license = c.str("license")!!; val at = c.long("issuedAt")!!; val ik = installs.getValue(c.str("install")!!)
        val spec = PilotRules.spec(Choice.parse(c.str("choice")!!), c.str("bundle")!!, catalog(root), at, LicenseState(), params(root), families(root)).getOrThrow()
        val right = RentalIssuing.right(spec, at, license, SeatIds.of(license, dev), dev, env.master, ik.pub, unhex(c.str("ephSeed")!!))
        val token = ActivationIssuer(signer, scopes).issue(ActivationIssuer.Request(ActivationKind.PRODUCTION, DeviceCode.of(dev), dev, at, Subject.TV, listOf(right), license, null, at, c.long("windowHours")!!.toInt(), c.str("nonce"), null)).token
        return linkedMapOf("line" to template(spec, at), "token" to token)
    }

    private fun parseLine(c: Map<String, Any?>): Map<String, Any?> = try {
        val r = RentalLines.parse(c.str("line")!!.split("|"))
        linkedMapOf("ok" to true, "product" to r.productId, "days" to r.durationDays, "usage" to r.maxUsageMinutes, "concurrent" to r.maxConcurrent, "bounds" to RentalLines.bounds(r), "line" to RentalLines.line(r))
    } catch (e: IllegalArgumentException) { linkedMapOf("refused" to true) }
}
