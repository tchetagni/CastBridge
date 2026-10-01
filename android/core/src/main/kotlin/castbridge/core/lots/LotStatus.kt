package castbridge.core.lots

/** What the « Données » screen shows for one lot, in plain French (principle: the user always knows where the data is). */
enum class LotStage { NOT_DOWNLOADED, ON_PHONE, WAITING_TV, SENDING, SENT, UP_TO_DATE, REFUSED, TV_OUTDATED_DATA }

data class LotStatus(val id: LotId, val stage: LotStage, val label: String, val detail: String, val title: String)

object LotStatusText {
    /** "il y a 3 jours" (the age of a datum). */
    fun age(thenMs: Long, nowMs: Long): String {
        if (thenMs <= 0) return "date inconnue"
        val s = maxOf(0L, (nowMs - thenMs) / 1000)
        return when {
            s < 90 -> "à l'instant"
            s < 3600 -> "il y a ${s / 60} min"
            s < 86400 -> "il y a ${s / 3600} h"
            s < 86400 * 2 -> "hier"
            else -> "il y a ${s / 86400} jours"
        }
    }

    /**
     * The state of [id] for [tv]: phone side first (not downloaded / downloaded), then its delivery to the TV.
     * [reachable]: is the TV in range right now (the app knows, e.g. Bluetooth connected or LAN answer).
     */
    fun of(id: LotId, store: LotStore, queue: DeliveryQueue, tv: String, reachable: Boolean, nowMs: Long): LotStatus {
        val held = store.get(id)
        if (held == null) return LotStatus(id, LotStage.NOT_DOWNLOADED, "Pas encore téléchargé", "Il sera téléchargé au prochain passage du téléphone sur Internet.", id.scope)
        val title = held.meta.title
        val age = "données ${age(held.installedAt, nowMs)}"
        val d = queue.get(tv, id)
        return when {
            d == null || d.state == DeliveryState.CANCELLED -> LotStatus(id, LotStage.ON_PHONE, "Téléchargé sur le téléphone", "Pas prévu pour la TV ($age).", title)
            d.state == DeliveryState.CONFIRMED -> LotStatus(id, LotStage.UP_TO_DATE, "À jour sur la TV", "Version ${d.lot.version}, $age.", title)
            d.state == DeliveryState.SENT -> LotStatus(id, LotStage.SENT, "Envoyé", "La TV l'installe ; confirmation à son prochain contact ($age).", title)
            d.state == DeliveryState.SENDING -> LotStatus(id, LotStage.SENDING, "Envoi en cours", "${pct(d)} % envoyés.", title)
            d.state == DeliveryState.REFUSED -> LotStatus(id, LotStage.REFUSED, "Refusé par la TV", d.lastError ?: "raison inconnue", title)
            reachable -> LotStatus(id, LotStage.WAITING_TV, "En attente d'envoi à la TV", "L'envoi va commencer ($age).", title)
            else -> LotStatus(id, LotStage.WAITING_TV, "En attente d'envoi à la TV (la TV n'est pas à portée)",
                "Il partira dès que la TV sera allumée à portée du téléphone ($age).", title)
        }
    }

    private fun pct(d: Delivery) = if (d.lot.bytes <= 0) 0 else (d.offset * 100 / d.lot.bytes).toInt().coerceIn(0, 100)

    /** The TV's budget line, from what the phone last learnt about it. */
    fun tvBudget(tv: String, queue: DeliveryQueue, nowMs: Long): String {
        val s = queue.seen(tv) ?: return "TV jamais vue : budget inconnu (${LotStore.mo(LotBudget.TV_MAX_BYTES)} maximum)"
        val m = s.manifest
        return "TV : ${LotStore.mo(m.usedBytes)} utilisés sur ${LotStore.mo(m.maxBytes)}, ${LotStore.mo(maxOf(0, m.remainingBytes))} libres (vu ${age(s.at, nowMs)})"
    }

    /** One French sentence for a skipped lot: which one, why, and what the user could drop. */
    fun skipped(s: LotPlanner.Skipped): String = buildString {
        append("Non envoyé : ").append(s.reason).append('.')
        if (s.dropSuggestion.isNotEmpty()) append(" Pour faire de la place, vous pouvez retirer de la TV : ").append(s.dropSuggestion.joinToString(", ") { "${it.feature} ${it.scope}" }).append('.')
    }
}
