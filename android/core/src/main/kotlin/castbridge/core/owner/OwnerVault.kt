package castbridge.core.owner

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based key derivation. PRODUCTION on the owner phone: Argon2id (memory-hard) with m = 64 MiB, t = 3, p = 1 (about 0.5 to 1 s on a mid-range
 * phone, which makes one guess costly in memory and not only in time) or scrypt N = 2^15, r = 8, p = 1 (32 MiB) if no Argon2 library is allowed.
 * The JDK has neither, so the core defines the interface and tests it with [Pbkdf2Kdf]; the owner flavour plugs the library in (docs/TRIAL-EDITION.md § Console propriétaire).
 */
interface Kdf {
    val id: String
    fun derive(passphrase: CharArray, salt: ByteArray, outLen: Int): ByteArray
}

/** PBKDF2-HMAC-SHA256: the only KDF in the JDK; fallback and test double, NOT memory-hard (use many iterations if it is ever the real one). */
class Pbkdf2Kdf(private val iterations: Int) : Kdf {
    override val id get() = "pbkdf2-sha256-$iterations"
    override fun derive(passphrase: CharArray, salt: ByteArray, outLen: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(passphrase, salt, iterations, outLen * 8)).encoded
}

/** The signing seed encrypted at rest. The passphrase is never stored: only [check], a value that proves it is right (costing one KDF run per guess). */
class VaultBlob(val kdf: String, val salt: ByteArray, val nonce: ByteArray, val ciphertext: ByteArray, val check: ByteArray)

object OwnerVault {
    private fun keys(kdf: Kdf, pass: CharArray, salt: ByteArray) = kdf.derive(pass, salt, 64).let { it.copyOf(32) to it.copyOfRange(32, 64) }
    private fun checkOf(macKey: ByteArray) = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(macKey, "HmacSHA256")); doFinal("castbridge-vault-check".toByteArray()) }

    fun seal(secret: ByteArray, passphrase: CharArray, kdf: Kdf, rnd: SecureRandom = SecureRandom()): VaultBlob {
        val salt = ByteArray(16).also(rnd::nextBytes); val nonce = ByteArray(12).also(rnd::nextBytes)
        val (enc, mac) = keys(kdf, passphrase, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(enc, "AES"), GCMParameterSpec(128, nonce)); c.updateAAD(kdf.id.toByteArray())
        return VaultBlob(kdf.id, salt, nonce, c.doFinal(secret), checkOf(mac))
    }

    /** The secret, or null when the passphrase is wrong (the check is compared first: a wrong code never reaches the decryption). */
    fun open(blob: VaultBlob, passphrase: CharArray, kdf: Kdf): ByteArray? {
        if (blob.kdf != kdf.id) return null
        val (enc, mac) = keys(kdf, passphrase, blob.salt)
        if (!MessageDigest.isEqual(checkOf(mac), blob.check)) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, SecretKeySpec(enc, "AES"), GCMParameterSpec(128, blob.nonce)); c.updateAAD(kdf.id.toByteArray())
            c.doFinal(blob.ciphertext)
        }.getOrNull()
    }
}

/**
 * Attempt counter for the unlock code: free for the first [FREE] failures, then 1 s, 2 s, 4 s… (capped at 5 minutes), and after [LOCK_AFTER] failures a
 * temporary lock of 30 minutes that doubles at each further lot of failures (capped at 24 h). A success resets everything. Persist [state] (the phone stores it
 * next to the vault); the clock is injected and should be monotonic.
 */
class UnlockGuard(private val now: () -> Long, var state: State = State()) {
    data class State(val failures: Int = 0, val blockedUntil: Long = 0L)
    companion object { const val FREE = 3; const val LOCK_AFTER = 10; const val LOCK_MS = 30L * 60 * 1000; const val MAX_LOCK_MS = 24L * 3600 * 1000; const val MAX_DELAY_MS = 5L * 60 * 1000 }

    /** Milliseconds to wait before the next attempt is allowed (0 = go ahead). */
    fun waitMs(): Long = maxOf(0L, state.blockedUntil - now())

    fun failure() {
        val f = state.failures + 1
        val delay = when {
            f >= LOCK_AFTER -> minOf(MAX_LOCK_MS, LOCK_MS shl ((f - LOCK_AFTER) / LOCK_AFTER).coerceAtMost(10))
            f > FREE -> minOf(MAX_DELAY_MS, 1000L shl (f - FREE - 1).coerceAtMost(20))
            else -> 0L
        }
        state = State(f, now() + delay)
    }

    fun success() { state = State() }
}

/** Append-only audit log of the console's commands, hash-chained: removing or editing a line breaks the chain (tamper evidence, not prevention). */
class AuditChain(entries: List<Entry> = emptyList()) {
    data class Entry(val seq: Int, val atMs: Long, val action: String, val target: String, val outcome: String, val prev: String, val hash: String)
    private val list = ArrayList(entries)
    val entries: List<Entry> get() = list

    private fun h(seq: Int, at: Long, action: String, target: String, outcome: String, prev: String) =
        MessageDigest.getInstance("SHA-256").digest("$seq|$at|$action|$target|$outcome|$prev".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun append(atMs: Long, action: String, target: String, outcome: String): Entry {
        val prev = list.lastOrNull()?.hash ?: "0".repeat(64)
        return Entry(list.size + 1, atMs, action, target, outcome, prev, h(list.size + 1, atMs, action, target, outcome, prev)).also { list += it }
    }

    fun verify(): Boolean {
        var prev = "0".repeat(64)
        for ((i, e) in list.withIndex()) {
            if (e.seq != i + 1 || e.prev != prev || e.hash != h(e.seq, e.atMs, e.action, e.target, e.outcome, e.prev)) return false
            prev = e.hash
        }
        return true
    }
}
