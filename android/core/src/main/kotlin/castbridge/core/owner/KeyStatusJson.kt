package castbridge.core.owner

import castbridge.core.lots.RentalLines
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.Right
import java.time.ZoneId

/** The activation properties of the TV as JSON (GET /api/activation, additive part), for the administration web page. Pure: everything comes from the arguments. */
object KeyStatusJson {
    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    /** End of the usage right: the latest end among the counting activations, null = unlimited (or no key). */
    fun usageEndsAt(activations: List<Activation>, nowMs: Long): Long? {
        val counting = activations.filter { a -> a.rights.filterIsInstance<Right.Usage>().none { nowMs >= it.endsAt } }
        if (counting.isEmpty()) return null
        val ends = counting.map { a -> a.rights.filterIsInstance<Right.Usage>().maxOfOrNull { it.endsAt } }
        return if (ends.any { it == null }) null else ends.filterNotNull().max()
    }

    /** Fields to append inside the JSON object (starts with a comma, no braces). */
    fun fields(activations: List<Activation>, nowMs: Long, rentals: List<RentalStatus>, trial: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        val b = KeyBadge.of(activations, nowMs, rentals, zone)
        val w = rentals.firstOrNull { it.contract.productId == RentalLines.TRIAL_PRODUCT }
        val state = when { w == null -> "none"; w.state == RentalState.EXPIRED -> "ended"; else -> "active" }
        val minutes = if (state == "active") (w!!.remainingUsageMinutes ?: RentalLines.TRIAL_USAGE_MINUTES.toLong()) else 0L
        val sb = StringBuilder()
        sb.append(",\"edition\":").append(q(b.title))
        sb.append(",\"badge\":").append(q(b.text))
        sb.append(",\"lines\":[").append(b.lines.joinToString(",") { q(it) }).append(']')
        sb.append(",\"trial\":").append(trial)
        sb.append(",\"ended\":").append(b.ended)
        sb.append(",\"trialWindow\":{\"state\":").append(q(state)).append(",\"minutesLeft\":").append(minutes).append('}')
        sb.append(",\"usageEndsAt\":").append(usageEndsAt(activations, nowMs)?.toString() ?: "null")
        if (trial) sb.append(",\"restrictions\":").append(q(TrialPolicy.MESSAGE))
        return sb.toString()
    }
}
