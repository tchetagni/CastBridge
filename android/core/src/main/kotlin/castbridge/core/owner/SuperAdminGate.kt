package castbridge.core.owner

import at.favre.lib.crypto.bcrypt.BCrypt
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies

/**
 * The door of the « Super administration » screen of the phone apps: the owner's password checked against a bcrypt hash injected at BUILD time
 * (never in the repository). LOCAL ONLY: no link with the server.
 *
 * What it is NOT: a security boundary for the powers. The hash lives in the APK, so it can be attacked offline (only the password's length and
 * randomness protect it) and the check can be patched out; that is why the real power (signing activations, commanding a TV) comes from the
 * per-phone vault key ([OwnerVault]), which is never shipped. A patched app gets an empty screen.
 */
class SuperAdminGate(private val hash: String?, private val guard: UnlockGuard, private val verify: (CharArray, String) -> Boolean = BcryptVerifier::verify) {
    sealed class Result {
        object Open : Result()
        object Wrong : Result()
        /** Too many failures: wait this long. */
        data class Locked(val waitMs: Long) : Result()
        /** No (valid) hash was injected at build time: the entry does not exist in this build. */
        object Disabled : Result()
    }

    /** False when the build carries no valid hash: the hidden entry must not even react. */
    val enabled: Boolean get() = hash != null && SHAPE.matches(hash)

    fun attempt(code: CharArray): Result {
        if (!enabled) return Result.Disabled
        val wait = guard.waitMs(); if (wait > 0) return Result.Locked(wait)
        val ok = runCatching { verify(code, hash!!) }.getOrDefault(false)       // always the full bcrypt work: a wrong and a right password cost the same
        return if (ok) { guard.success(); Result.Open } else { guard.failure(); Result.Wrong }
    }

    companion object { val SHAPE = Regex("^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$") }
}

object BcryptVerifier {
    /** Same behaviour as the server's Spring encoder for long passwords (truncated at 72 bytes). */
    fun verify(code: CharArray, hash: String): Boolean =
        BCrypt.verifyer(BCrypt.Version.VERSION_2A, LongPasswordStrategies.truncate(BCrypt.Version.VERSION_2A)).verify(code, hash).verified
}

/**
 * The hidden entry gesture: [taps] taps on the title, each within [windowMs] of the previous one, then a long press within [longPressWithinMs] of the
 * last tap. A wrong or incomplete sequence just resets; nothing is shown. The numbers are build-time constants.
 */
class TapSequence(private val taps: Int = 7, private val windowMs: Long = 2_500, private val longPressWithinMs: Long = 3_000, private val now: () -> Long = System::currentTimeMillis) {
    private var count = 0
    private var last = 0L

    fun tap() { val t = now(); count = if (count > 0 && t - last > windowMs) 1 else count + 1; last = t }

    /** True when the whole sequence was done; the counter is reset either way. */
    fun longPress(): Boolean {
        val ok = count >= taps && now() - last <= longPressWithinMs
        count = 0
        return ok
    }
}
