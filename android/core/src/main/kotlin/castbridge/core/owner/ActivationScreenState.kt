package castbridge.core.owner

/**
 * What the phone screen « Activer la TV » says about the TV's edition (locked / trial / production / unreachable), and how it words the answer of a key sent to the TV. Pure: the screen reads
 * GET /api/activation and passes the fields here; nothing Android, nothing secret.
 */
object ActivationScreenState {
    enum class Kind { UNREACHABLE, NO_LOCK, LOCKED, TRIAL, PRODUCTION }

    /** The fields of GET /api/activation the screen needs. [usageEndsAt] is epoch millis (null = unknown / unlimited). */
    data class Info(val required: Boolean, val locked: Boolean, val trial: Boolean, val label: String = "", val usageEndsAt: Long? = null)

    data class View(val kind: Kind, val headline: String, val detail: String?, val good: Boolean)

    const val UPGRADE_HINT = "Collez la clé de production pour passer en version complète."

    fun remaining(endsAt: Long?, nowMs: Long): String? {
        if (endsAt == null) return null
        val ms = endsAt - nowMs
        if (ms <= 0) return "terminé"
        val min = ms / 60_000
        val d = min / 1440; val h = (min % 1440) / 60
        return when { d >= 2 -> "$d jours"; d == 1L -> "1 jour" + (if (h > 0) " $h h" else ""); h >= 1 -> "$h h"; else -> "moins d'une heure" }
    }

    /** [info] null = the TV could not be read; [why] then explains (no TV added, Bluetooth only, old version...). */
    fun view(info: Info?, why: String?, nowMs: Long): View {
        if (info == null) return View(Kind.UNREACHABLE, why ?: "L'état de la TV n'est pas lisible pour le moment.", "Vous pouvez quand même envoyer une clé par Bluetooth.", false)
        return when {
            !info.required -> View(Kind.NO_LOCK, "Cette TV n'exige pas d'activation (version sans verrou).", null, true)
            info.locked -> View(Kind.LOCKED, "TV verrouillée : en attente d'une clé d'activation.", null, false)
            info.trial -> {
                val left = remaining(info.usageEndsAt, nowMs)
                View(Kind.TRIAL, "Cette TV est en version d'essai", (if (left != null) "Temps restant de la clé d'essai : $left. " else "") + UPGRADE_HINT, false)
            }
            else -> View(Kind.PRODUCTION, TrialPolicy.FULL_VERSION, (if (info.label.isNotBlank()) "Clé : ${info.label}. " else "") + "Rien à faire ; vous pouvez ajouter une autre clé si besoin.", true)
        }
    }

    /** Wording of the TV's answer to a key sent from the phone. [staged]: Bluetooth keys wait for « Valider la clé » on the TV. */
    data class SendOutcome(val ok: Boolean, val staged: Boolean, val text: String)

    fun sendOutcome(ok: Boolean, message: String): SendOutcome {
        if (!ok) return SendOutcome(false, false, "Refusée par la TV : " + message.ifBlank { "clé non acceptée" })
        val staged = message.contains("Valider la clé", ignoreCase = true)
        return if (staged) SendOutcome(true, true, "Clé reçue et valide, pas encore activée. Sur la TV, appuyez sur « Valider la clé » pour terminer le passage en version complète.")
        else SendOutcome(true, false, "Clé acceptée par la TV." + if (message.isNotBlank()) " $message" else "")
    }

    /** The message the customer shares to ask for a production key: [request] is the device request text (`code=…`, `k=…`, `factor=…`). */
    fun requestShare(request: String): String {
        val lines = request.replace("\r", "").lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val code = lines.firstOrNull { it.startsWith("code=") }?.removePrefix("code=")?.trim().orEmpty()
        return "Demande de passage en production pour CastBridge-TV — code d'appareil $code\n\n" + lines.joinToString("\n")
    }
}
