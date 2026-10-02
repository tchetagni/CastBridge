package castbridge.owner

import android.content.Context
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.OwnerVault
import castbridge.core.owner.Pbkdf2Kdf
import castbridge.core.owner.UnlockGuard
import castbridge.core.owner.VaultBlob
import java.io.File
import java.security.SecureRandom

/**
 * The phone's own signing key (one key per tool: this key is NOT the desk tool's key) sealed by the owner's code, the unlock-attempt counter and the journal.
 * The code is never stored: only the vault's check value (one slow key derivation per guess). Private files of the app: allowBackup=false and explicit exclusions (sender res/xml/backup_rules.xml and data_extraction_rules.xml) keep them out of every backup and device transfer.
 */
class OwnerStore(ctx: Context) {
    private val dir = ctx.filesDir
    private val vaultFile = File(dir, "owner-vault.txt")
    private val journalFile = File(dir, "owner-journal.tsv")
    private val catalogFile = File(dir, "owner-bundles.json")
    private val prefs = ctx.getSharedPreferences("owner_guard", Context.MODE_PRIVATE)
    private val kdf = Pbkdf2Kdf(ITERATIONS)
    val guard = UnlockGuard({ System.currentTimeMillis() }, UnlockGuard.State(prefs.getInt("failures", 0), prefs.getLong("until", 0L)))

    fun hasVault() = vaultFile.isFile
    private fun saveGuard() = prefs.edit().putInt("failures", guard.state.failures).putLong("until", guard.state.blockedUntil).apply()

    /** Creates the key. Returns the signer (already unlocked) or null if a vault exists. */
    fun create(code: CharArray): Ed25519Signer? {
        if (hasVault()) return null
        val seed = ByteArray(32).also(SecureRandom()::nextBytes)
        val signer = Ed25519Signer(seed)
        val b = OwnerVault.seal(seed, code, kdf)
        vaultFile.writeText(listOf(HEADER, "kdf=${b.kdf}", "salt=${hex(b.salt)}", "nonce=${hex(b.nonce)}", "ct=${hex(b.ciphertext)}", "check=${hex(b.check)}",
            "kid=${signer.keyId}", "pub=${signer.publicKeyBase64}").joinToString("\n") + "\n")
        seed.fill(0)
        guard.success(); saveGuard()
        return signer
    }

    /** The signer, or null when the code is wrong (counted: delays, then temporary locks) ; throws Locked while the guard says to wait. */
    class Locked(val waitMs: Long) : Exception()
    fun unlock(code: CharArray): Ed25519Signer? {
        val wait = guard.waitMs(); if (wait > 0) throw Locked(wait)
        val kv = vaultFile.readLines().drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
        val blob = VaultBlob(kv["kdf"] ?: return null, unhex(kv["salt"]), unhex(kv["nonce"]), unhex(kv["ct"]), unhex(kv["check"]))
        val seed = OwnerVault.open(blob, code, kdf)
        if (seed == null) { guard.failure(); saveGuard(); return null }
        guard.success(); saveGuard()
        return Ed25519Signer(seed).also { seed.fill(0) }
    }

    fun publicLine(): String? = if (!hasVault()) null else vaultFile.readLines().drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
        .let { "kid=${it["kid"]} pub=${it["pub"]} scopes=ISSUE_TRIAL,ISSUE_PRODUCTION,COMMAND_SUPPORT,COMMAND_UNLOCK,COMMAND_OPEN_ALL,TRANSFER,REVOKE,REGISTRY,REACTIVATE,SUPER_UNLIMITED" }   // every right of the owner key but POLICY (the server's)

    /** What was issued (date, action, device code, kind, licence) : never the key, never a code, never the token. Hash-chained. */
    fun journal(what: String, device: String, kind: String, license: String, days: Int) {
        val lines = if (journalFile.isFile) journalFile.readLines() else emptyList()
        val prev = lines.lastOrNull()?.split('\t')?.getOrNull(6) ?: "0"
        val seq = lines.size + 1; val at = System.currentTimeMillis()
        val h = java.security.MessageDigest.getInstance("SHA-256").digest("$seq|$at|$what|$device|$kind|$license|$days|$prev".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        journalFile.appendText("$seq\t$at\t$what\t$device\t$kind\t$license/${days}j\t$h\n")
    }

    fun journalLines(): List<List<String>> = if (!journalFile.isFile) emptyList() else journalFile.readLines().map { it.split('\t') }.reversed()

    /** The server's bundle catalogue (public, no secret; not in the vault). Validated before it is kept; returns the bundle count, or throws (French message) and keeps the previous one. */
    fun saveCatalog(json: String): Int {
        val c = try { castbridge.core.lots.BundleCatalog.parse(json) } catch (e: Exception) { throw IllegalArgumentException("Catalogue illisible : ${e.message}") }
        if (c.bundles.isEmpty()) throw IllegalArgumentException("Catalogue vide")
        catalogFile.writeText(json)
        return c.bundles.size
    }
    /** Keeps a catalogue the signature of which was ALREADY verified ([castbridge.core.lots.SignedBundleCatalog.verify]); returns the bundle count. */
    fun saveVerifiedCatalog(v: castbridge.core.lots.SignedBundleCatalog.Verified): Int = saveCatalog(v.json)
    /** The generatedAt of the kept catalogue when it is a signed one from the server (null for a catalogue imported from a file without one). */
    fun catalogGeneratedAt(): String? = if (!catalogFile.isFile) null else castbridge.core.lots.SignedBundleCatalog.generatedAtOf(catalogFile.readText())
    /** The imported catalogue and its date (epoch ms), or null. */
    fun catalog(): Pair<castbridge.core.lots.BundleCatalog, Long>? =
        if (!catalogFile.isFile) null else runCatching { castbridge.core.lots.BundleCatalog.parse(catalogFile.readText()) to catalogFile.lastModified() }.getOrNull()

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String?): ByteArray = s!!.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object { const val HEADER = "castbridge-owner-vault-v1"; const val ITERATIONS = 600_000 }
}
