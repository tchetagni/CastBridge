package castbridge.desktop

import castbridge.core.lots.LotId
import castbridge.core.lots.Right
import castbridge.core.net.JsonLite
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationIssuer
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.DeviceIdentity
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.IssueException
import castbridge.core.owner.KeyRing
import castbridge.core.owner.LicenseBook
import castbridge.core.owner.LicenseEvent
import castbridge.core.owner.OwnerFrames
import castbridge.core.owner.Plan
import castbridge.core.owner.Power
import castbridge.core.owner.SeatIds
import castbridge.core.owner.Signer
import castbridge.core.owner.Subject
import castbridge.core.owner.KeyScope
import java.io.File
import castbridge.core.owner.TrustedKey

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

/**
 * The desk's engine: ONE signer, the journal and the licence registry in [home]. The same [ActivationIssuer] as the owner phone and the server (same inputs, same bytes).
 * The journal lists what was issued (date, TV, rights, duration) and NEVER the key or the code; the registry is the signed set of events that the three tools exchange.
 */
class Desk(private val home: File, private val signer: Signer, private val scopes: Set<KeyScope>, private val own: TrustedKey, private val clock: () -> Long = System::currentTimeMillis) {
    private val issuer = ActivationIssuer(signer, scopes)
    val journalFile = File(home, "journal.jsonl")
    val registryFile = File(home, "registry.json")

    val trustedFile = File(home, "trusted-keys.json")

    /** The keys this desk recognises when it replays the registry: its own plus those added with « trust » (owner phone, server). Public keys only. */
    fun ring(): KeyRing {
        val others = if (trustedFile.isFile) (JsonLite.parse(trustedFile.readText()) as List<*>).map { e ->
            val m = e as Map<*, *>; TrustedKey(m["kid"] as String, m["publicKey"] as String, (m["scopes"] as List<*>).map { KeyScope.valueOf(it as String) }.toSet())
        } else emptyList()
        return KeyRing((listOf(own) + others).distinctBy { it.keyId })
    }

    fun trust(key: TrustedKey) {
        val cur = if (trustedFile.isFile) (JsonLite.parse(trustedFile.readText()) as List<*>).map { it as Map<*, *> } else emptyList()
        val row = linkedMapOf<String, Any?>("kid" to key.keyId, "publicKey" to key.publicKeyBase64, "scopes" to key.scopes.map { it.name }.sorted())
        home.mkdirs(); trustedFile.writeText(JsonLite.write(cur.filter { it["kid"] != key.keyId } + row) + "\n")
    }

    fun events(): List<LicenseEvent> = if (registryFile.isFile) LicenseBook.import(registryFile.readText()) else emptyList()

    private fun saveEvents(e: Collection<LicenseEvent>) {
        home.mkdirs(); val tmp = File(registryFile.path + ".tmp"); tmp.writeText(LicenseBook.export(e)); tmp.renameTo(registryFile)
    }

    /** Imports a registry exported by another tool and merges it (union, no duplicates, order-independent). Returns the number of new events. */
    fun importRegistry(json: String): Int {
        val mine = events(); val merged = LicenseBook.merge(mine, LicenseBook.import(json)); saveEvents(merged); return merged.size - mine.size
    }

    fun exportRegistry(): String = LicenseBook.export(events())

    /**
     * Issues an activation. For a production licence the registry decides the seat: the same hardware re-uses its seat (no seat consumed), new hardware takes a free seat,
     * and a licence with no seat left is refused (unless [seatOverride] is given by the caller who knows better, e.g. replaying a vector).
     */
    fun issue(device: DeviceRequest, spec: IssueSpec): Delivered {
        val now = clock()
        val issuedAt = spec.issuedAt ?: now
        var seat = spec.seat; var reused = false; var left: Int? = null
        val events = events()
        val state = LicenseBook.replay(events, ring())
        if (spec.kind == ActivationKind.PRODUCTION && seat == null) {
            if (spec.license !in state.licenses) throw IssueException("Licence inconnue « ${spec.license} » : créez-la d'abord (commande « licence »)")
            when (val p = LicenseBook.plan(state, spec.license, spec.subject, device.factors)) {
                is Plan.Reuse -> { seat = p.seat.seatId; reused = true; left = state.seatsLeft(spec.license) }
                is Plan.NewSeat -> { seat = p.seatId; left = p.seatsLeftAfter }
                is Plan.Refused -> throw IssueException(p.message)
            }
        }
        val req = ActivationIssuer.Request(spec.kind, device.code, device.factors, issuedAt, spec.subject, spec.rights, spec.license, seat,
            spec.notBefore ?: issuedAt, spec.windowDays, spec.nonce, null)
        val issued = issuer.issue(req)
        val a = issued.activation
        if (spec.kind == ActivationKind.PRODUCTION || KeyScope.ISSUE_TRIAL in scopes) saveEvents(LicenseBook.merge(events, listOf(LicenseEvent.issue(signer, a))))
        log(a, device)
        return Delivered(issued, a.seat, reused, left)
    }

    /** Creates a licence (a purchase): [seats] seats, a yearly cap of transfers. Signed event in the registry. */
    fun createLicense(license: String, seats: Int, maxTransfersPerYear: Int = LicenseBook.DEFAULT_TRANSFERS_PER_YEAR) {
        if (!Activation.ID.matches(license) || license == Activation.TRIAL_LICENSE) throw IssueException("Identifiant de licence invalide")
        if (seats !in 1..1000) throw IssueException("Nombre de postes hors bornes (1 à 1000)")
        if (KeyScope.ISSUE_PRODUCTION !in scopes) throw IssueException("Cette clé ne peut pas créer de licence")
        saveEvents(LicenseBook.merge(events(), listOf(LicenseEvent.license(signer, clock(), license, seats, maxTransfersPerYear))))
    }

    /** The compact key (165 characters) for manual typing: strictly bound to the device code, no rights list. */
    fun compact(code: String, windowDays: Int, kind: ActivationKind = ActivationKind.TRIAL, startDay: Int? = null, setId: Int = 0): String {
        val day = startDay ?: (((clock() - castbridge.core.owner.CompactActivation.EPOCH_MS) / 86_400_000L).toInt() - 1).coerceAtLeast(0)
        return issuer.issueCompact(kind, code, day, windowDays, setId)
    }

    /** An owner command for ONE TV; [challenge] is the one that TV just issued (valid 120 s, single use). */
    fun command(device: DeviceRequest, power: Power, challenge: String, days: Int = 0, action: String = "", bundles: List<String> = emptyList(), lots: List<LotId> = emptyList()): String =
        issuer.issueCommand(power, device.factors, challenge, days, action, bundles, lots)

    private fun log(a: Activation, d: DeviceRequest) {
        home.mkdirs()
        val line = JsonLite.write(linkedMapOf(
            "at" to clock(), "kid" to signer.keyId, "kind" to a.kind.name.lowercase(), "subject" to a.subject.name.lowercase(), "device" to d.code, "license" to a.license, "seat" to a.seat,
            "notBefore" to a.notBefore, "notAfter" to a.notAfter, "rights" to a.rights.map { Activation.rightLine(it) }))
        journalFile.appendText(line + "\n")
    }

    fun journal(): List<Map<String, Any?>> = if (journalFile.isFile) journalFile.readLines().filter { it.isNotBlank() }.map { JsonLite.obj(it) } else emptyList()

    companion object {
        const val DAY_MS = 24L * 3600 * 1000
        fun days(n: Int) = n * DAY_MS
    }
}
