package castbridge.core.owner

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the phone screen « Activer la TV » says about the TV's edition (locked / grace / trial / production / unreachable), and how it words the answer of a key sent to the TV. Pure: the screen reads
 * GET /api/activation and passes the fields here; nothing Android, nothing secret. The states behind these words: docs/ACTIVATION-ETATS-ET-VOIES.md.
 */
object ActivationScreenState {
    enum class Kind { UNREACHABLE, NO_LOCK, LOCKED, GRACE, TRIAL, PRODUCTION }

    /**
     * The fields of GET /api/activation the screen needs. [usageEndsAt] is epoch millis (null = unknown / unlimited). [grace] / [graceUntil] (epoch millis): the TV is in its grace period, an install that
     * predates the lock, until 2026-11-01 ([GateState.Grace]). `trial` is TRUE in grace too (the grace applies the restrictions of the trial edition), which is why [view] looks at [grace] first. A TV that does
     * not say it (CastBridge-TV 0.14.49 and before) reads as « not in grace »: the reading it always had.
     */
    data class Info(val required: Boolean, val locked: Boolean, val trial: Boolean, val label: String = "", val usageEndsAt: Long? = null, val grace: Boolean = false, val graceUntil: Long? = null)

    data class View(val kind: Kind, val headline: String, val detail: String?, val good: Boolean)

    const val UPGRADE_HINT = "Collez la clé de production pour passer en version complète."
    const val GRACE_HEADLINE = "TV en période de grâce : activation à fournir"
    private const val GRACE_ASK = "Collez la clé d'activation reçue, ou demandez-la avec « Activer la TV »."
    private const val ENDED = "terminé"
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE)

    fun remaining(endsAt: Long?, nowMs: Long): String? {
        if (endsAt == null) return null
        val ms = endsAt - nowMs
        if (ms <= 0) return ENDED
        val min = ms / 60_000
        val d = min / 1440; val h = (min % 1440) / 60
        return when { d >= 2 -> "$d jours"; d == 1L -> "1 jour" + (if (h > 0) " $h h" else ""); h >= 1 -> "$h h"; else -> "moins d'une heure" }
    }

    /** « Jusqu'au 01/11/2026 (restant : 12 jours). » then what to do. The date is read in [zone]; an ended « restant » (the phone's clock a day ahead of the TV's) is not said; no date = only what to do. */
    private fun graceDetail(untilMs: Long?, nowMs: Long, zone: ZoneId): String {
        if (untilMs == null) return GRACE_ASK
        val left = remaining(untilMs, nowMs)
        return "Jusqu'au ${DATE.format(Instant.ofEpochMilli(untilMs).atZone(zone))}" + (if (left != null && left != ENDED) " (restant : $left)" else "") + ". " + GRACE_ASK
    }

    /** [info] null = the TV could not be read; [why] then explains (no TV added, Bluetooth only, old version...). [zone]: the one the grace date is shown in (the phone's). */
    fun view(info: Info?, why: String?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): View {
        if (info == null) return View(Kind.UNREACHABLE, why ?: "L'état de la TV n'est pas lisible pour le moment.", "Vous pouvez quand même envoyer une clé par Bluetooth.", false)
        return when {
            !info.required -> View(Kind.NO_LOCK, "Cette TV n'exige pas d'activation (version sans verrou).", null, true)
            info.locked -> View(Kind.LOCKED, "TV verrouillée : en attente d'une clé d'activation.", null, false)
            // the grace has the trial's restrictions (`trial` is true) but is NOT a trial: no trial key to upgrade, a key is still to be provided
            info.grace -> View(Kind.GRACE, GRACE_HEADLINE, graceDetail(info.graceUntil, nowMs, zone), false)
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
