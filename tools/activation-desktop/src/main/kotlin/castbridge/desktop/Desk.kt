package castbridge.desktop

import castbridge.core.lots.LotId
import castbridge.core.net.JsonLite
import castbridge.core.owner.Activation
import castbridge.core.owner.ActivationIssuer
import castbridge.core.owner.ActivationKind
import castbridge.core.owner.ActivationPolicy
import castbridge.core.owner.CompactActivation
import castbridge.core.owner.DeviceRequest
import castbridge.core.owner.Delivered
import castbridge.core.owner.IssueSpec
import castbridge.core.owner.KeyRing
import castbridge.core.owner.KeyScope
import castbridge.core.owner.LicenseBook
import castbridge.core.owner.LicenseEvent
import castbridge.core.owner.LicensedIssuer
import castbridge.core.owner.Power
import castbridge.core.owner.Signer
import castbridge.core.owner.TrustedKey
import java.io.File

/**
 * The desk's engine: ONE signer, the journal and the licence registry in [home] (files). The issuing flow is the shared [LicensedIssuer] of the core (same rules as the owner phone and the server);
 * the same [ActivationIssuer] (same inputs, same bytes). The journal lists what was issued (date, TV, rights, duration) and NEVER the key or the code; the registry is the signed set of events
 * that the three tools exchange.
 */
class Desk(private val home: File, private val signer: Signer, private val scopes: Set<KeyScope>, private val own: TrustedKey, private val clock: () -> Long = System::currentTimeMillis) {
    private val issuer = ActivationIssuer(signer, scopes)
    val journalFile = File(home, "journal.jsonl")
    val registryFile = File(home, "registry.json")
    val trustedFile = File(home, "trusted-keys.json")
    private val flow = LicensedIssuer(signer, scopes, ::ring, ::events, ::saveEvents, clock)

    /** The keys this desk recognises when it replays the registry: its own plus those added with « faire-confiance » (owner phone, server). Public keys only. */
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

    private fun saveEvents(e: List<LicenseEvent>) {
        home.mkdirs(); val tmp = File(registryFile.path + ".tmp"); tmp.writeText(LicenseBook.export(e)); tmp.renameTo(registryFile)
    }

    /** Imports a registry exported by another tool and merges it (union, no duplicates, order-independent). Returns the number of new events. */
    fun importRegistry(json: String): Int {
        val mine = events(); val merged = LicenseBook.merge(mine, LicenseBook.import(json)); saveEvents(merged); return merged.size - mine.size
    }

    fun exportRegistry(): String = LicenseBook.export(events())

    /** Issues an activation (see [LicensedIssuer]) and writes the journal line. */
    fun issue(device: DeviceRequest, spec: IssueSpec): Delivered = flow.issue(device, spec).also { log(it.issued.activation, device) }

    fun createLicense(license: String, seats: Int, maxTransfersPerYear: Int = LicenseBook.DEFAULT_TRANSFERS_PER_YEAR) = flow.createLicense(license, seats, maxTransfersPerYear)

    /** The compact key (165 characters) for manual typing: strictly bound to the device code, no rights list. */
    fun compact(code: String, kind: ActivationKind = ActivationKind.TRIAL, setId: Int = 0, unlimited: Boolean = false): String {
        val hour = ((clock() - CompactActivation.EPOCH_MS) / ActivationPolicy.HOUR_MS).toInt().coerceAtLeast(0)      // the 48 h run from the creation (to the hour)
        return issuer.issueCompact(kind, code, hour, ActivationPolicy.CODE_VALIDITY_HOURS, setId, unlimited)
    }

    /** An owner command for ONE TV; [challenge] is the one that TV just issued (valid 120 s, single use). */
    fun command(device: DeviceRequest, power: Power, challenge: String, days: Int = 0, action: String = "", bundles: List<String> = emptyList(), lots: List<LotId> = emptyList()): String =
        issuer.issueCommand(power, device.factors, challenge, clock(), days, action, bundles, lots)

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
        const val MAX_WINDOW = 366
    }
}
