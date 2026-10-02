package castbridge.core.owner

import castbridge.core.lots.Right

/** What a TV tells the owner (the « demande d'appareil », frame DEVICE_INFO): its code, k and the fingerprints of its factors. */
class DeviceRequest(val code: String, val k: Int, val factors: Fingerprints) {
    companion object {
        /** From the TV's text (`code=…` / `k=…` / `factor=TYPE|hash` lines). Tolerates CRLF and blank lines. */
        fun parse(text: String): DeviceRequest {
            val clean = text.replace("\r", "").lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            val (code, k, fp) = OwnerFrames.parseDeviceInfo(clean) ?: throw IssueException("Demande d'appareil illisible : attendu « code=… », « k=… » puis des lignes « factor=TYPE|empreinte »")
            if (code != DeviceCode.of(fp)) throw IssueException("Le code d'appareil ne correspond pas aux empreintes : demande corrompue ou modifiée")
            return DeviceRequest(code, k, fp)
        }
    }
}

/** What the owner chose. Same fields as the `request` of the test vectors (tools/activation/test-vectors.json). */
class IssueSpec(
    val kind: ActivationKind, val subject: Subject = Subject.TV, val rights: List<Right> = emptyList(), val license: String = Activation.TRIAL_LICENSE,
    val windowHours: Int = ActivationIssuer.MAX_WINDOW_HOURS, val notBefore: Long? = null, val issuedAt: Long? = null, val seat: String? = null, val nonce: String? = null,
    /** Rentals to add to [rights]: their keys are derived from [rentalMaster] and the seat the registry picks (docs/RENTAL-LOTS.md). */
    val rentals: List<RentalSpec> = emptyList(), val rentalMaster: ByteArray? = null,
    /** Optional policy check (free lots refused): returns a French reason or null. */
    val rentalCheck: ((RentalSpec) -> String?)? = null,
    /** Usage ceiling in days (see [Right.Usage]): trial 1..[ActivationPolicy.TRIAL_MAX_DAYS] (null = [ActivationPolicy.TRIAL_DEFAULT_DAYS]), production 1..[ActivationPolicy.PRODUCTION_MAX_DAYS] (null = none). */
    val usageDays: Int? = null,
    /** A TRIAL key carries the one-time 12 h window of rented lots ([RentalLines.TRIAL_PRODUCT]); needs [rentalMaster]. The owner's tools turn it on for every trial key. */
    val trialLots: Boolean = false,
)

/**
 * What the owner asks for a rental: `produit=bouquet1,bouquet2:JOURS[:MINUTES_D_USAGE_MAX[:TOLERANCE_JOURS[:SIMULTANEES]]]` ([RightsSyntax.rental]). [period] = the start of the rental
 * this one renews (null = a NEW rental starting now): a renewal extends the same rental without duplicate and keeps the same key (docs/RENTAL-LOTS.md § 1).
 */
data class RentalSpec(val productId: String, val bundleIds: List<String>, val days: Int, val maxUsageMinutes: Int = 0, val graceDays: Int = 0, val maxConcurrent: Int = 0, val period: Long? = null)

/** The ONE place a [RentalSpec] becomes a signed-ready [Right.Rental] (desk tool, owner phone console and server port all go through it). */
object RentalIssuing {
    /** [period] null = a new rental starting at [issuedAt]; the key is derived from [master], the licence, the seat, the product and the period, then boxed for the device. */
    fun right(r: RentalSpec, issuedAt: Long, license: String, seat: String, device: Fingerprints, master: ByteArray): Right.Rental {
        val period = r.period ?: issuedAt
        if (period > issuedAt) throw IssueException("Location : la période à prolonger ne peut pas être dans le futur")
        val key = castbridge.core.lots.RentalKeys.rentalKey(master, license, seat, r.productId, period)
        return Right.Rental(r.productId, r.bundleIds.sorted(), issuedAt, period, r.days, r.graceDays * DAY_MS, r.maxUsageMinutes, r.maxConcurrent,
            castbridge.core.lots.RentalKeys.makeBox(device, DeviceIdentity.kFor(device.n), key, r.productId, period))
    }
}

/** Result of one issuing: every encoding of the token plus where the registry stands. */
class Delivered(val issued: ActivationIssuer.Issued, val seat: String, val reused: Boolean, val seatsLeft: Int?)

/** The owner's short syntax for rights (CLI options and the GUI's rights box). Errors are French sentences for the screen. */
object RightsSyntax {
    private fun bad(msg: String): Nothing = throw IssueException(msg)

    /** `produit=bouquet1,bouquet2` */
    fun purchase(s: String, now: Long): Right {
        val f = s.split('=', limit = 2); if (f.size != 2 || f[0].isBlank() || f[1].isBlank()) bad("Achat : « produit=bouquet1,bouquet2 » attendu")
        return Right.Purchase(f[0].trim(), f[1].split(',').map { it.trim() }.sorted(), now)
    }

    /** `produit=bouquets:jours[:tolérance en jours[:auto]]` */
    fun subscription(s: String, now: Long): Right {
        val f = s.split(':'); val pb = f[0].split('=', limit = 2)
        if (pb.size != 2 || f.size < 2) bad("Abonnement : « produit=bouquets:jours[:tolérance[:auto]] » attendu")
        val days = f[1].toLongOrNull() ?: bad("Abonnement : durée en jours invalide")
        return Right.Subscription(pb[0].trim(), pb[1].split(',').map { it.trim() }.sorted(), now, now + days * DAY_MS, (f.getOrNull(2)?.toLongOrNull() ?: 7L) * DAY_MS, f.getOrNull(3) == "auto")
    }

    /** `produit=bouquet1,bouquet2:JOURS[:MINUTES_D_USAGE_MAX[:TOLERANCE_JOURS[:SIMULTANEES]]]` : une location (1 à 366 jours, usage maximal optionnel en minutes). */
    fun rental(s: String, period: Long? = null): RentalSpec {
        val f = s.split(':'); val pb = f[0].split('=', limit = 2)
        if (pb.size != 2 || pb[0].isBlank() || pb[1].isBlank() || f.size < 2 || f.size > 5) bad("Location : « produit=bouquet1,bouquet2:JOURS[:MINUTES_D_USAGE_MAX] » attendu")
        fun n(i: Int, what: String, dflt: Int) = f.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: bad("Location : $what invalide") } ?: dflt
        val spec = RentalSpec(pb[0].trim(), pb[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.sorted(), n(1, "durée en jours", 0), n(2, "minutes d'usage maximales", 0), n(3, "tolérance en jours", 0), n(4, "plafond de locations simultanées", 0), period)
        if (spec.days !in 1..castbridge.core.lots.RentalLines.MAX_DAYS) bad("Location : de 1 à ${castbridge.core.lots.RentalLines.MAX_DAYS} jours")
        if (spec.bundleIds.isEmpty()) bad("Location : au moins un bouquet")
        return spec
    }

    /** `produit:jours` (30 jours au plus, clé avec la portée « tout ouvert ») */
    fun openAll(s: String, now: Long): Right {
        val f = s.split(':'); val days = f.getOrNull(1)?.toLongOrNull() ?: bad("Tout ouvert : « produit:jours » attendu")
        return Right.OpenAll(f[0].trim(), now, now + days * DAY_MS)
    }

    /** One line per right: `achat …`, `abonnement …`, `tout-ouvert …` or a raw wire line (`purchase|…`). Blank lines and `#` comments are skipped. */
    fun parseBox(text: String, now: Long): List<Right> = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { l ->
        val kw = l.substringBefore(' ').lowercase(); val rest = l.substringAfter(' ', "").trim()
        when (kw) { "achat" -> purchase(rest, now); "abonnement" -> subscription(rest, now); "tout-ouvert" -> openAll(rest, now); else -> runCatching { Activation.parseRight(l) }.getOrElse { bad("Droit illisible : $l") } }
    }
}

/**
 * The licence-aware issuing flow shared by the three tools (desk, owner phone, server): the registry decides the seat of a production activation (same hardware = same seat,
 * no seat consumed; new hardware = a free seat; none left = refused), the activation is issued by [ActivationIssuer], and the signed `issue` event is added to the registry.
 * Storage is the caller's: [events] reads the registry, [save] writes it back (a file on the desk, the phone's private storage, the server's database).
 */
class LicensedIssuer(
    private val signer: Signer, private val scopes: Set<KeyScope>, private val ring: () -> KeyRing,
    private val events: () -> List<LicenseEvent>, private val save: (List<LicenseEvent>) -> Unit, private val clock: () -> Long = System::currentTimeMillis,
) {
    private val issuer = ActivationIssuer(signer, scopes)

    fun issue(device: DeviceRequest, spec: IssueSpec): Delivered {
        val issuedAt = spec.issuedAt ?: clock()
        var seat = spec.seat; var reused = false; var left: Int? = null
        val current = events()
        if (spec.kind == ActivationKind.PRODUCTION && seat == null) {
            val state = LicenseBook.replay(current, ring())
            if (spec.license !in state.licenses) throw IssueException("Licence inconnue « ${spec.license} » : créez-la d'abord")
            when (val p = LicenseBook.plan(state, spec.license, spec.subject, device.factors)) {
                is Plan.Reuse -> { seat = p.seat.seatId; reused = true; left = state.seatsLeft(spec.license) }
                is Plan.NewSeat -> { seat = p.seatId; left = p.seatsLeftAfter }
                is Plan.Refused -> throw IssueException(p.message)
            }
        }
        val trialWindow = if (spec.kind == ActivationKind.TRIAL && spec.trialLots) listOf(RentalSpec(castbridge.core.lots.RentalLines.TRIAL_PRODUCT, listOf(castbridge.core.lots.Right.ALL_BUNDLE),
            castbridge.core.lots.RentalLines.TRIAL_DAYS, castbridge.core.lots.RentalLines.TRIAL_USAGE_MINUTES)) else emptyList()
        val rentals = spec.rentals + trialWindow
        val rights = if (rentals.isEmpty()) spec.rights else {
            if (spec.kind != ActivationKind.PRODUCTION && spec.rentals.isNotEmpty()) throw IssueException("Une location demande une activation de production")
            val master = spec.rentalMaster ?: throw IssueException("Location : secret de location absent")
            val theSeat = seat ?: SeatIds.of(spec.license, device.factors)
            spec.rights + rentals.map { r ->
                spec.rentalCheck?.invoke(r)?.let { throw IssueException(it) }
                RentalIssuing.right(r, issuedAt, spec.license, theSeat, device.factors, master)
            }
        }
        val days = when (spec.kind) {
            ActivationKind.TRIAL -> (spec.usageDays ?: ActivationPolicy.TRIAL_DEFAULT_DAYS).also { if (it !in 1..ActivationPolicy.TRIAL_MAX_DAYS) throw IssueException("Durée d'un essai : 1 à ${ActivationPolicy.TRIAL_MAX_DAYS} jours") }
            ActivationKind.PRODUCTION -> spec.usageDays?.also { if (it !in 1..ActivationPolicy.PRODUCTION_MAX_DAYS) throw IssueException("Durée d'usage d'une production : 1 à ${ActivationPolicy.PRODUCTION_MAX_DAYS} jours") }
        }
        if (days != null && rights.any { it is Right.Super }) throw IssueException("SUPER_UNLIMITED est permanent : pas de durée d'usage")
        val withUsage = if (days == null) rights else rights + Right.Usage(issuedAt, issuedAt + days * 24L * 3600 * 1000)
        val issued = issuer.issue(ActivationIssuer.Request(spec.kind, device.code, device.factors, issuedAt, spec.subject, withUsage, spec.license, seat,
            spec.notBefore ?: issuedAt, spec.windowHours, spec.nonce, null))
        save(LicenseBook.merge(current, listOf(LicenseEvent.issue(signer, issued.activation))))
        return Delivered(issued, issued.activation.seat, reused, left)
    }

    /** Creates a licence (a purchase): [seats] seats and a yearly cap of transfers; a signed event in the registry. */
    fun createLicense(license: String, seats: Int, maxTransfersPerYear: Int = LicenseBook.DEFAULT_TRANSFERS_PER_YEAR) {
        if (!Activation.ID.matches(license) || license == Activation.TRIAL_LICENSE) throw IssueException("Identifiant de licence invalide")
        if (seats !in 1..1000) throw IssueException("Nombre de postes hors bornes (1 à 1000)")
        if (KeyScope.ISSUE_PRODUCTION !in scopes) throw IssueException("Cette clé ne peut pas créer de licence")
        save(LicenseBook.merge(events(), listOf(LicenseEvent.license(signer, clock(), license, seats, maxTransfersPerYear))))
    }
}

private const val DAY_MS = 24L * 3600 * 1000
