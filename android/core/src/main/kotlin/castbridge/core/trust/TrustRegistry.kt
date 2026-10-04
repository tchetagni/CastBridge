package castbridge.core.trust

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/** A phone the TV owner approved on the TV screen. The Bluetooth address is what the paired (authenticated) link proves. */
data class TrustedPhone(val address: String, val name: String, val addedAt: Long, val lastSeen: Long)

/** Where the registry is kept (a private file on Android, memory in tests). */
interface TrustPersistence {
    fun load(): String?
    fun save(text: String)
    /** The previous good copy, when the store keeps one (see [FileTrustPersistence]); used when [load] returns damaged text. */
    fun loadBackup(): String? = null
}

class MemoryTrustPersistence(var text: String? = null) : TrustPersistence {
    override fun load() = text
    override fun save(text: String) { this.text = text }
}

/** Result of adding (or replacing) a phone: never silent, never more than [TrustRegistry.MAX_PHONES]. */
sealed class TrustResult {
    data class Added(val phone: TrustedPhone) : TrustResult()
    data class Refreshed(val phone: TrustedPhone) : TrustResult()
    /** The TV already has [TrustRegistry.MAX_PHONES] phones: nothing was changed; the owner must remove one first. */
    data class Full(val phones: List<TrustedPhone>) : TrustResult()
    /** [TrustRegistry.replace] asked to remove a phone the TV does not know (nothing was changed). */
    object NotFound : TrustResult()
    /** The registry file could not be written: nothing was changed (the owner keeps the phone he wanted to replace). */
    object WriteFailed : TrustResult()
}

/** Device token as it travels: only the phone and the TV's hash of it ever exist. */
data class IssuedToken(val token: String, val expiresAt: Long)

/**
 * "Trusted phones" of a TV: who may use the TV without the PIN, and the per-phone tokens they use on Wi-Fi.
 *
 * - A phone becomes trusted only through [PairingSession] (Bluetooth pairing + on-TV approval), never by itself.
 * - A token is random (256 bits), tied to one phone, expires after [tokenTtlMs], and is stored only as a SHA-256 hash
 *   (the registry file alone cannot be used to log in). Revoking a phone deletes its tokens at once.
 * - The administration PIN is never involved, stored or returned here.
 */
class TrustRegistry(
    private val persistence: TrustPersistence,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    val tokenTtlMs: Long = 12 * 3600_000L,
    private val maxTokensPerPhone: Int = 4,
) {
    private class Tok(val address: String, val expiresAt: Long)

    /** Random id of this installation: phones compare it to tell "the TV was reset or reinstalled" from "this phone was removed". Persisted with the registry. */
    @Volatile var installId: String = newInstallId(random); private set
    /** The stored registry was damaged at startup (checksum): true when the backup copy was used, false when nothing could be read. */
    @Volatile var recoveredFromBackup: Boolean? = null; private set

    private val phones = LinkedHashMap<String, TrustedPhone>()
    private val tokens = HashMap<String, Tok>()          // sha256(token) -> owner
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    init { synchronized(this) { load() } }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun changed() { listeners.forEach { runCatching { it() } } }

    @Synchronized fun list(): List<TrustedPhone> = phones.values.sortedBy { it.addedAt }
    @Synchronized fun isTrusted(address: String?): Boolean = address != null && phones.containsKey(norm(address))
    @Synchronized fun get(address: String): TrustedPhone? = phones[norm(address)]

    /**
     * Records an approval (the caller checked the approval: see [PairingSession]). A NEW phone is refused with [TrustResult.Full] when
     * [MAX_PHONES] are already trusted: nothing is evicted, nothing is written, the owner must choose a phone to remove ([replace]).
     * The check and the add happen under the registry lock, so concurrent adds never exceed the cap. A phone already trusted just refreshes.
     */
    fun trust(address: String, name: String): TrustResult {
        val a = norm(address)
        val r = synchronized(this) {
            val known = phones[a]
            if (known == null && phones.size >= MAX_PHONES) return@synchronized TrustResult.Full(list())
            val t = now()
            val p = TrustedPhone(a, PhoneName.sanitize(name), known?.addedAt ?: t, t)
            phones[a] = p
            save()
            if (known == null) TrustResult.Added(p) else TrustResult.Refreshed(p)
        }
        if (r !is TrustResult.Full) changed()
        return r
    }

    /**
     * Removes [removeAddress] (and its tokens) and trusts [newAddress] in ONE persisted write: the file never holds nine phones, and when the
     * write fails nothing changes (the owner keeps the phone he wanted to replace and the new one is not trusted). A [newAddress] that is
     * already trusted only refreshes (nobody is removed).
     */
    fun replace(removeAddress: String, newAddress: String, newName: String): TrustResult {
        val rm = norm(removeAddress); val a = norm(newAddress)
        val r = synchronized(this) {
            if (phones.containsKey(a)) return@synchronized trustLocked(a, newName)
            if (rm == a || !phones.containsKey(rm)) return@synchronized TrustResult.NotFound
            val phonesBefore = LinkedHashMap(phones); val tokensBefore = HashMap(tokens)
            phones.remove(rm); tokens.values.removeAll { it.address == rm }
            val t = now()
            val p = TrustedPhone(a, PhoneName.sanitize(newName), t, t)
            phones[a] = p
            if (save()) TrustResult.Added(p)
            else { phones.clear(); phones.putAll(phonesBefore); tokens.clear(); tokens.putAll(tokensBefore); TrustResult.WriteFailed }
        }
        if (r is TrustResult.Added || r is TrustResult.Refreshed) changed()
        return r
    }

    private fun trustLocked(a: String, name: String): TrustResult {
        val known = phones.getValue(a); val t = now()
        val p = TrustedPhone(a, PhoneName.sanitize(name), known.addedAt, t)
        phones[a] = p; save()
        return TrustResult.Refreshed(p)
    }

    /** Forgets one phone and every token it holds. */
    fun revoke(address: String): Boolean {
        val r = synchronized(this) {
            val a = norm(address)
            val had = phones.remove(a) != null
            tokens.values.removeAll { it.address == a }
            if (had) save()
            had
        }
        if (r) changed()
        return r
    }

    /** "Oublier tous les téléphones". */
    fun revokeAll(): Int {
        val n = synchronized(this) { val n = phones.size; phones.clear(); tokens.clear(); save(); n }
        if (n > 0) changed()
        return n
    }

    /** A fresh token for a trusted phone (null for anybody else). The older ones stay valid until they expire. */
    @Synchronized fun issueToken(address: String): IssuedToken? {
        val a = norm(address)
        val p = phones[a] ?: return null
        purge()
        val mine = tokens.entries.filter { it.value.address == a }.sortedBy { it.value.expiresAt }
        mine.take((mine.size - (maxTokensPerPhone - 1)).coerceAtLeast(0)).forEach { tokens.remove(it.key) }
        val raw = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val token = TOKEN_PREFIX + raw
        val exp = now() + tokenTtlMs
        tokens[hash(token)] = Tok(a, exp)
        phones[a] = p.copy(lastSeen = now())
        save()
        return IssuedToken(token, exp)
    }

    /** The address of the phone this token belongs to, or null (unknown, expired, revoked). */
    @Synchronized fun verifyToken(token: String?): String? {
        if (token == null || !TOKEN_FORMAT.matches(token)) return null
        val t = tokens[hash(token)] ?: return null
        if (t.expiresAt <= now() || !phones.containsKey(t.address)) { tokens.remove(hash(token)); return null }
        return t.address
    }

    @Synchronized fun tokenCount(address: String) = tokens.values.count { it.address == norm(address) && it.expiresAt > now() }

    private fun purge() { val t = now(); tokens.values.removeAll { it.expiresAt <= t } }

    // ---- persistence: one line per record, tab separated, names URL-encoded (never trusted to be free of separators)
    /** False when the file could not be written. */
    private fun save(): Boolean {
        purge()
        val sb = StringBuilder()
        sb.append("I\t").append(installId).append('\n')
        phones.values.forEach { sb.append("P\t").append(it.address).append('\t').append(it.addedAt).append('\t').append(it.lastSeen).append('\t').append(enc(it.name)).append('\n') }
        tokens.forEach { (h, t) -> sb.append("T\t").append(t.address).append('\t').append(h).append('\t').append(t.expiresAt).append('\n') }
        val sum = digest(sb.toString())
        sb.append(CHECK).append('\t').append(sum).append('\n')   // a truncated or edited file is detected, not half-believed
        return runCatching { persistence.save(sb.toString()) }.isSuccess
    }

    private fun load() {
        val first = runCatching { persistence.load() }.getOrNull() ?: return
        val text = if (intact(first)) first else (runCatching { persistence.loadBackup() }.getOrNull()?.takeIf(::intact)?.also { recoveredFromBackup = true } ?: first.also { recoveredFromBackup = false })
        for (line in text.lineSequence()) {
            val f = line.split('\t')
            runCatching {
                when (f[0]) {
                    "I" -> if (INSTALL.matches(f[1])) installId = f[1]
                    "P" -> { val a = norm(f[1]); if (ADDRESS.matches(a)) phones[a] = TrustedPhone(a, PhoneName.sanitize(dec(f[4])), f[2].toLong(), f[3].toLong()) }
                    "T" -> if (HASH.matches(f[2])) tokens[f[2]] = Tok(norm(f[1]), f[3].toLong())
                }
            }
        }
        tokens.values.removeAll { !phones.containsKey(it.address) }
        purge()
        if (recoveredFromBackup == false) { phones.clear(); tokens.clear(); installId = newInstallId(random) }   // damaged and no good copy: nothing is believed, the phones must be added again (new install id tells them)
    }

    companion object {
        /** A TV is synchronized with at most this many phones (owner rule 2026-10-04); also the bound of the 8 local relayed players of online play. */
        const val MAX_PHONES = 8
        const val TOKEN_PREFIX = "cbk_"
        private const val CHECK = "C"
        private val INSTALL = Regex("^[0-9a-f]{32}$")
        fun newInstallId(random: SecureRandom = SecureRandom()) = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        private fun digest(body: String) = hash(body)
        /** True for a legacy file (no checksum line) or one whose checksum matches; false when truncated or edited. */
        internal fun intact(text: String): Boolean {
            val i = text.lastIndexOf("\n$CHECK\t") + 1
            if (!text.startsWith("I\t")) return !text.contains('\u0000')    // legacy format (no id line, no checksum); a file of zeros is what a power cut leaves
            if (i == 0) return false                                    // new format without its checksum line: cut short
            return text.substring(i).trim().removePrefix("$CHECK\t") == digest(text.substring(0, i))
        }
        private val TOKEN_FORMAT = Regex("^cbk_[0-9a-f]{64}$")
        private val HASH = Regex("^[0-9a-f]{64}$")
        private val ADDRESS = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
        fun norm(address: String) = address.trim().uppercase()
        fun isAddress(a: String?) = a != null && ADDRESS.matches(norm(a))
        internal fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")
    }
}

/** Names come from another device: never shown raw. */
object PhoneName {
    fun sanitize(raw: String?, max: Int = 40): String {
        val s = (raw ?: "").filter { it != '‮' && it != '‏' && it != '‎' && it != '‭' && it != '⁦' && it != '⁧' && it != '⁨' && it != '⁩' }
            .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().replace(Regex("\\s+"), " ")
        return if (s.isEmpty()) "Téléphone" else s.take(max)
    }
}

/**
 * What a phone presents to the TV API instead of the PIN: a token ([TrustRegistry.TOKEN_PREFIX]...) or the 6-digit PIN.
 * One string, so existing screens keep passing "the credential" around; [header] says which HTTP header carries it.
 */
object TvAuth {
    const val PIN_HEADER = "X-CB-Pin"
    const val TOKEN_HEADER = "X-CB-Token"
    /** Sent in the PIN field of the Bluetooth protocols by a trusted phone: the TV ignores it for a trusted peer and rejects it for anybody else. */
    const val NO_PIN = "------"

    fun isToken(c: String?) = c != null && c.startsWith(TrustRegistry.TOKEN_PREFIX)
    fun isUsable(c: String?) = c != null && (castbridge.core.tv.Pin.isValidFormat(c) || (isToken(c) && c.length == 4 + 64))
    fun header(c: String): Pair<String, String> = (if (isToken(c)) TOKEN_HEADER else PIN_HEADER) to c
    /** What to put in the PIN field of CBT1/CBTN/CBTR/CBTG for this credential. */
    fun btPin(c: String?): String = if (c != null && castbridge.core.tv.Pin.isValidFormat(c)) c else NO_PIN

    /** Routes a token never opens (the PIN, i.e. the owner, is needed): persistent access or software installation. */
    fun tokenMayCall(path: String): Boolean =
        !(path.startsWith("/api/ssh") || path.startsWith("/api/apk/install") || path.startsWith("/api/update/install") || path.startsWith("/api/activation/install"))
}
