package castbridge.core.free

import castbridge.core.quiz.Json
import java.util.Locale

/** Answer of `GET /api/v1/free-content/info` (the public archive of the free CC BY-SA contents). */
data class FreeContentInfo(
    val available: Boolean, val sizeBytes: Long, val sha256: String, val generatedAt: String, val licence: String, val fileName: String,
) {
    /** The ZIP can be downloaded: published, with a size and a safe file name. */
    val downloadable: Boolean get() = available && sizeBytes > 0

    companion object {
        /** Sanitised name (no path, no odd characters); falls back to a fixed name. */
        fun safeName(n: String?): String {
            val base = n.orEmpty().substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
            val name = if (base.isEmpty()) "castbridge-contenus-libres.zip" else base
            return if (name.lowercase().endsWith(".zip")) name else "$name.zip"
        }

        /** @throws IllegalArgumentException (French message) when the body is not the expected JSON. */
        fun parse(body: String): FreeContentInfo {
            val m = try { Json.parse(body) as? Map<*, *> } catch (e: Exception) { null }
                ?: throw IllegalArgumentException("Réponse du serveur illisible.")
            val av = m["available"] == true
            val size = (m["sizeBytes"] as? Number)?.toLong() ?: 0L
            val sha = (m["sha256"] as? String).orEmpty().trim().lowercase()
            if (av && sha.isNotEmpty() && !Regex("[0-9a-f]{64}").matches(sha)) throw IllegalArgumentException("Empreinte de l'archive invalide.")
            return FreeContentInfo(
                av, size.coerceAtLeast(0), sha, (m["generatedAt"] as? String).orEmpty(),
                (m["licence"] as? String)?.takeIf { it.isNotBlank() } ?: FreeLicense.NAME, safeName(m["fileName"] as? String),
            )
        }
    }
}

object FreeSizes {
    /** « 12,3 Mo », « 840 Ko », « 12 octets » (French). */
    fun format(b: Long): String = when {
        b >= 1_048_576 -> "%.1f Mo".format(Locale.FRANCE, b / 1_048_576.0)
        b >= 1024 -> "${b / 1024} Ko"
        else -> "$b octets"
    }

    fun percent(done: Long, total: Long): Int = if (total <= 0) 0 else (done * 100 / total).toInt().coerceIn(0, 100)

    /** Above this size on mobile data the user is asked first. */
    const val MOBILE_ASK_BYTES = 20L * 1024 * 1024
    fun askBeforeMobile(size: Long, onMobile: Boolean) = onMobile && size > MOBILE_ASK_BYTES
}
