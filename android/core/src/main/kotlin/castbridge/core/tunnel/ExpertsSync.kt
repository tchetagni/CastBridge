package castbridge.core.tunnel

import castbridge.core.owner.SafeFile
import castbridge.core.owner.TrustedKey
import java.io.File

/** One key the tunnel sshd accepts: the expert's identifier (written in the journal) and the base64 blob of his ssh-ed25519 key. */
data class ExpertKey(val id: String, val keyBase64: String)

/**
 * The last VALID experts list on this TV: the raw signed file (`experts.json`, kept through [SafeFile]) and the `authorized_keys` text derived from it. The list is VERIFIED AGAIN when it is read
 * (signature, REGISTRY scope), so a modified file gives no key. Experts whose `notAfter` passed are dropped at read time, not only at refresh time.
 */
class ExpertsStore(private val dir: File, private val trusted: () -> List<TrustedKey>) {
    private val json get() = File(dir, "experts.json")
    private val authorized get() = File(dir, "authorized_keys")

    private fun valid(text: String) = runCatching { ExpertsList.verify(text, trusted()) }.isSuccess

    /** The list currently kept (verified), or null. */
    fun list(): ExpertsList? = SafeFile.read(json) { valid(it) }?.let { runCatching { ExpertsList.verify(it.text, trusted()).list }.getOrNull() }

    /** `generatedAt` of the kept list: a later list may not be older than this (replay protection). 0 = none. */
    fun lastGeneratedAt(): Long = list()?.generatedAt ?: 0L

    /** The keys allowed now. */
    fun keys(now: Long): List<ExpertKey> = list()?.experts?.filter { !it.expiredAt(now) }?.sortedBy { it.id }?.map { ExpertKey(it.id, it.keyBase64) }.orEmpty()

    /** Keeps [raw] (already verified by the caller) and rewrites `authorized_keys` (public keys only, mode 0644 is fine). */
    fun keep(raw: String, list: ExpertsList, now: Long) {
        SafeFile.write(json, raw) { valid(it) }
        SafeFile.write(authorized, list.authorizedKeysLines(now).joinToString("") { it + "\n" })
    }
}

/** Fetches (`GET /api/v1/tunnel/experts`) and applies the experts list. A refused list leaves the previous valid one in force. */
class ExpertsSync(private val store: ExpertsStore, private val trusted: () -> List<TrustedKey>, private val now: () -> Long = System::currentTimeMillis) {
    class Result(val ok: Boolean, val message: String, val experts: Int = 0)

    /** [answer] = (HTTP status, body) of the server, or null when the call failed. */
    fun apply(answer: Pair<Int, String>?): Result {
        if (answer == null) return Result(false, "Liste des experts : serveur injoignable (liste précédente conservée)")
        val (code, body) = answer
        if (code != 200) return Result(false, "Liste des experts : réponse $code du serveur (liste précédente conservée)")
        return try {
            val v = ExpertsList.verify(body, trusted(), store.lastGeneratedAt()).list
            store.keep(body, v, now())
            Result(true, "Liste des experts acceptée (${v.experts.size})", v.experts.count { !it.expiredAt(now()) })
        } catch (e: ExpertsList.Refused) {
            Result(false, "Liste des experts refusée : ${e.message} (liste précédente conservée)")
        } catch (e: Exception) {
            Result(false, "Liste des experts : ${e.javaClass.simpleName} (liste précédente conservée)")
        }
    }
}
