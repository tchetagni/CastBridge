package castbridge.core.trust

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * « Téléphones synchronisés » (écran de la TV), vue pure : tous les téléphones de confiance (actifs d'abord, puis le plus récemment vu),
 * avec « ajouté le … », « vu il y a … » ou « actif », l'état (actif / hors de portée), le compteur « 7 / 8 » et, quand la TV est pleine,
 * le candidat suggéré au retrait (le moins récemment vu, hors téléphones actifs si possible). La suggestion n'est qu'une suggestion :
 * rien n'est retiré sans le choix du propriétaire.
 */
object PhoneRoster {
    enum class PhoneState(val label: String) { ACTIVE("actif"), OUT_OF_RANGE("hors de portée") }

    data class Row(val address: String, val name: String, val addedText: String, val seenText: String, val state: PhoneState, val suggested: Boolean) {
        /** Lu par le lecteur d'écran (contentDescription) : tout ce que la ligne dit. */
        val label: String get() = PhonesTexts.labeled(name, address)
        val description: String get() = listOf(label, addedText, seenText, state.label, if (suggested) PhonesTexts.SUGGESTION else null).filterNotNull().joinToString(" · ")
    }

    data class View(val rows: List<Row>, val count: Int, val max: Int) {
        /** « 7 / 8 ». */
        val counter: String get() = PhonesTexts.counter(count, max)
        val full: Boolean get() = count >= max
        /** More phones than the cap (a file restored from before the cap): how many to remove. */
        val overBy: Int get() = (count - max).coerceAtLeast(0)
        val suggestedAddress: String? get() = rows.firstOrNull { it.suggested }?.address
    }

    /** The name (cleaned, case-insensitive) is already used by a synchronized phone. */
    fun sameName(name: String, phones: List<TrustedPhone>) = phones.any { it.name.equals(PhoneName.sanitize(name), ignoreCase = true) }

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun build(phones: List<TrustedPhone>, active: Set<String>, now: Long, zone: ZoneId = ZoneId.systemDefault(), max: Int = TrustRegistry.MAX_PHONES): View {
        val act = active.map { TrustRegistry.norm(it) }.toSet()
        val sorted = phones.sortedWith(compareBy<TrustedPhone>({ it.address !in act }, { -it.lastSeen }, { it.addedAt }, { it.address }))
        val suggest = if (phones.size >= max) sorted.minWithOrNull(compareBy<TrustedPhone>({ it.address in act }, { it.lastSeen }, { it.addedAt }, { it.address }))?.address else null
        val rows = sorted.map { p ->
            val isActive = p.address in act
            Row(p.address, p.name, "ajouté le " + DATE.format(Instant.ofEpochMilli(p.addedAt).atZone(zone)),
                if (isActive) PhoneState.ACTIVE.label else "vu " + ago(now - p.lastSeen),
                if (isActive) PhoneState.ACTIVE else PhoneState.OUT_OF_RANGE, p.address == suggest)
        }
        return View(rows, phones.size, max)
    }

    /** « à l'instant », « il y a 5 min », « il y a 3 h », « il y a 2 j » (une horloge revenue en arrière n'est pas un âge négatif). */
    fun ago(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 1 -> "à l'instant"
            min < 60 -> "il y a $min min"
            min < 24 * 60 -> "il y a ${min / 60} h"
            else -> "il y a ${min / (24 * 60)} j"
        }
    }
}
