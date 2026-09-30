package castbridge.core.parental

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Small persistent key/value store (SharedPreferences on the TV, a map or a file in tests). Writes must be durable when [put] returns. */
interface KvStore {
    fun get(key: String): String?
    /** null removes the key. */
    fun put(key: String, value: String?)
}

class MemoryKv : KvStore {
    private val m = LinkedHashMap<String, String>()
    @Synchronized override fun get(key: String) = m[key]
    @Synchronized override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    @Synchronized fun dump(): Map<String, String> = LinkedHashMap(m)
}

/** The parental PIN: 4 to 6 digits, chosen by the parent. There is NO default PIN anywhere. */
object ParentalPins {
    const val MIN = 4
    const val MAX = 6

    fun isValidFormat(p: String?) = p != null && p.length in MIN..MAX && p.all { it in '0'..'9' }

    /** Why a PIN is too easy to guess (French, for the screen), or null. */
    fun weakReason(p: String): String? {
        if (p.all { it == p[0] }) return "Un code avec le même chiffre répété (comme 4444) est trop facile à deviner."
        val up = p.zipWithNext().all { (a, b) -> b - a == 1 }
        val down = p.zipWithNext().all { (a, b) -> a - b == 1 }
        if (up || down) return "Une suite de chiffres (comme 1234) est trop facile à deviner."
        return null
    }

    /** null = acceptable for a new PIN, else the French reason. An empty PIN is always refused. */
    fun validateNew(p: String?): String? = when {
        p.isNullOrEmpty() -> "Le code parental est vide : choisissez 4 à 6 chiffres."
        !isValidFormat(p) -> "Le code parental doit avoir de $MIN à $MAX chiffres, rien d'autre."
        else -> weakReason(p)
    }
}

/**
 * Salted, slow hash of the parental PIN (PBKDF2-HMAC-SHA256, available on Android 8+ and on the JVM). Stored as
 * `pbkdf2-sha256$iterations$salt$hash` (Base64). The PIN itself is never stored nor logged. Comparison is constant-time.
 * A 4-6 digit PIN can always be tried offline by someone who can read the app's private storage: the hash only keeps it
 * out of logs, backups and casual reads; the online attempts are throttled by [PinLock].
 */
class PinHasher(private val iterations: Int = DEFAULT_ITERATIONS, private val random: SecureRandom = SecureRandom()) {
    fun hash(pin: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val enc = Base64.getEncoder()
        return listOf(ALGO, iterations.toString(), enc.encodeToString(salt), enc.encodeToString(derive(pin, salt, iterations))).joinToString("$")
    }

    /** False for a wrong PIN, an empty PIN, or a stored value in an unknown format. */
    fun verify(pin: String, stored: String?): Boolean {
        if (pin.isEmpty() || stored == null) return false
        val p = stored.split('$')
        if (p.size != 4 || p[0] != ALGO) return false
        val it = p[1].toIntOrNull() ?: return false
        if (it < 1000 || it > 5_000_000) return false
        val salt = runCatching { Base64.getDecoder().decode(p[2]) }.getOrNull() ?: return false
        val want = runCatching { Base64.getDecoder().decode(p[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(want, derive(pin, salt, it))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded

    companion object {
        const val ALGO = "pbkdf2-sha256"
        const val DEFAULT_ITERATIONS = 60_000
    }
}

/**
 * Progressive lockout, persisted (a restart of the app does not reset it): 5 wrong PINs lock for 1 minute, the next 5 for
 * 5 minutes, then 15 minutes, then 1 hour each time. While locked, even the right PIN is refused. A success resets all.
 * The lock is global (not per client address): changing phone or address does not help. It uses the wall clock: changing
 * the TV's date forward would shorten it (the TV's settings are themselves blockable for the child profile).
 */
class PinLock(
    private val store: KvStore,
    private val name: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val threshold: Int = 5,
    private val steps: List<Long> = listOf(60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L),
) {
    private fun k(s: String) = "lock.$name.$s"
    private fun num(s: String) = store.get(k(s))?.toLongOrNull() ?: 0L

    /** Milliseconds left before a new attempt is taken into account, 0 = open. */
    @Synchronized fun remainingMs(): Long {
        val until = num("until")
        if (until <= 0) return 0
        val t = now()
        val cap = t + steps.last()                      // a clock set backwards cannot make the lock longer than the longest step
        if (until > cap) { store.put(k("until"), cap.toString()); return steps.last() }
        return (until - t).coerceAtLeast(0)
    }

    fun isLocked() = remainingMs() > 0

    /** Wrong attempts left before the next lock (while not locked). */
    @Synchronized fun attemptsLeft(): Int = (threshold - num("fails").toInt()).coerceAtLeast(0)

    /** Records a wrong PIN. Returns the lock applied now in ms (0 = none yet). */
    @Synchronized fun recordFailure(): Long {
        if (remainingMs() > 0) return 0
        val fails = num("fails") + 1
        if (fails < threshold) { store.put(k("fails"), fails.toString()); return 0 }
        val level = num("level").toInt()
        val ms = steps[level.coerceAtMost(steps.size - 1)]
        store.put(k("until"), (now() + ms).toString())
        store.put(k("level"), (level + 1).toString())
        store.put(k("fails"), "0")
        return ms
    }

    @Synchronized fun recordSuccess() {
        store.put(k("fails"), null); store.put(k("level"), null); store.put(k("until"), null)
    }
}
