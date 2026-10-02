package castbridge.core.tunnel

import java.io.File

/**
 * The LOCAL journal of the remote assistance (docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md § 4): connections and disconnections of the tunnel, and the identifiers of the experts that logged in. Small rotating file
 * (the current one plus the previous one), readable by the user on the TV (À propos > Assistance à distance). Never a key, a token or a device code.
 */
class TunnelJournal(private val file: File, private val maxBytes: Long = 24_000L, private val now: () -> Long = System::currentTimeMillis) {
    private val old get() = File(file.parentFile, file.name + ".1")

    @Synchronized fun add(text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val line = TunnelTerms.nowText(now()) + "  " + text.replace('\n', ' ').take(300) + "\n"
            if (file.length() + line.length > maxBytes) { old.delete(); file.renameTo(old) }
            file.appendText(line, Charsets.UTF_8)
        }
    }

    /** The last [max] lines, oldest first. */
    @Synchronized fun lines(max: Int = 200): List<String> = runCatching {
        ((if (old.isFile) old.readLines(Charsets.UTF_8) else emptyList()) + (if (file.isFile) file.readLines(Charsets.UTF_8) else emptyList())).takeLast(max)
    }.getOrDefault(emptyList())
}

/** The words shown to the user (French). Exactly three states in the line of « À propos », the detail goes on a second line. */
object TunnelText {
    const val TITLE = "Assistance à distance"
    const val CONNECTED = "Assistance à distance : connectée"
    const val OFFLINE = "Assistance à distance : hors ligne"
    const val NEEDS_TERMS = "Assistance à distance : en attente d'acceptation des conditions"
    const val EMPTY_JOURNAL = "Aucune connexion enregistrée pour l'instant."

    fun line(state: TunnelState): String = when (state) {
        TunnelState.UP -> CONNECTED
        TunnelState.NEEDS_TERMS -> NEEDS_TERMS
        else -> OFFLINE
    }

    fun detail(state: TunnelState, path: TunnelPath, error: String?, retryInMs: Long): String? = when (state) {
        TunnelState.UP -> if (path == TunnelPath.GATEWAY) "Par l'Internet du téléphone (Bluetooth)." else "Par le réseau de la TV."
        TunnelState.NEEDS_TERMS -> "Acceptez les conditions d'usage pour activer l'assistance à distance."
        TunnelState.BACKOFF -> (error?.let { "$it. " } ?: "") + "Nouvel essai dans ${(retryInMs + 999) / 1000} s."
        TunnelState.REVOKED -> "Désactivée pour cette TV par l'éditeur."
        TunnelState.ENROLLING, TunnelState.CONNECTING -> "Connexion en cours…"
        TunnelState.IDLE -> error
    }
}
