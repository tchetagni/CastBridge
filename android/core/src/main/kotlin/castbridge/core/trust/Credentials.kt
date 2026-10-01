package castbridge.core.trust

import java.io.IOException
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * The ONLY way to build an authenticated request to a TV (HTTP header, header line, Bluetooth PIN field).
 *
 * A credential is the 6-digit PIN or a trusted phone's token ([TvAuth]); which header carries it is decided here, never by a screen.
 * A credential that is not usable (empty, truncated, an expired token the phone already dropped) is NOT sent: the call fails
 * locally with [Missing], because sending it would be counted as a wrong PIN by the TV (progressive lockout of the whole phone).
 * `null` means "no credential on purpose" (a TV without PIN in tests) and adds nothing.
 */
object TvCredential {
    /** Nothing usable to present to the TV: the call is not made. */
    class Missing : IOException("no usable credential")

    fun isUsable(c: String?) = TvAuth.isUsable(c)
    fun isToken(c: String?) = TvAuth.isToken(c)

    /** (header name, value). Throws [Missing] for an unusable credential. */
    fun header(c: String): Pair<String, String> = if (TvAuth.isUsable(c)) TvAuth.header(c) else throw Missing()

    /** At most one entry; empty for `null`. Throws [Missing] for an unusable non-null credential. */
    fun headers(c: String?): Map<String, String> = if (c == null) emptyMap() else header(c).let { mapOf(it) }

    fun apply(conn: URLConnection, c: String?) { headers(c).forEach { (k, v) -> conn.setRequestProperty(k, v) } }

    /** "Name: value", for hand-written HTTP requests. */
    fun headerLine(c: String?): String? = if (c == null) null else header(c).let { "${it.first}: ${it.second}" }

    /** What to put in the PIN field of CBT1/CBTN/CBTR/CBTG: the PIN, or [TvAuth.NO_PIN] for a trusted phone. */
    fun btPin(c: String?) = TvAuth.btPin(c)

    /** The credential as it may appear in a log or a report: never. */
    const val REDACTED = "••••••"
}

/**
 * Remembers which credential a TV refused, so that no automatic loop ever presents it again.
 * A wrong PIN repeated by a retry loop locks the TV for a minute; an expired or revoked token is replaced by a new HELLO, never re-sent.
 * Only a different credential (a new token, a PIN the user typed) clears the block. Only a hash is kept.
 */
class CredentialGate {
    private val refused = ConcurrentHashMap<String, MutableSet<String>>()

    fun refuse(tv: String, credential: String?) { if (credential != null) refused.getOrPut(tv) { ConcurrentHashMap.newKeySet() }.let { if (it.size < 16) it.add(TrustRegistry.hash(credential)) } }
    fun allows(tv: String, credential: String?): Boolean = credential != null && refused[tv]?.contains(TrustRegistry.hash(credential)) != true
    fun clear(tv: String) { refused.remove(tv) }
}
