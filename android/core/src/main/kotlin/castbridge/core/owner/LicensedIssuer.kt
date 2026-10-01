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
    val windowDays: Int = 30, val notBefore: Long? = null, val issuedAt: Long? = null, val seat: String? = null, val nonce: String? = null,
)

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
        val issued = issuer.issue(ActivationIssuer.Request(spec.kind, device.code, device.factors, issuedAt, spec.subject, spec.rights, spec.license, seat,
            spec.notBefore ?: issuedAt, spec.windowDays, spec.nonce, null))
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
