package castbridge.core.lots

import castbridge.core.net.JsonLite
import castbridge.core.owner.RentalSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The pilot's issuing rules (docs/coordination/DESIGN-W16 § 1, § 3.2, § 4.1): what the PLATFORM signs for a rental whose duration the user chose, or a French refusal.
 * Pure: no clock (the issuing instant is a parameter), no Android, no desk tool. The signed line keeps its 10 fields ([RentalLines.parse]): the unit of a rental is carried by the
 * line itself (`maxUsageMinutes > 0` = hours of use, 0 = days), so every TV already installed reads what comes out of here.
 *
 * The TV engine ([RentalEngine.contracts]) clamps an hourly contract at 96 h and IGNORES a renewal line whose unit differs from the first line's: what it would lose silently,
 * THIS module refuses (never rely on the TV). Time is Douala time (UTC+1, no daylight saving).
 */
class PilotRefusal(message: String) : IllegalArgumentException(message)

/** What a person picks in the selector (docs W16 § 1.1): the default, a number of days of validity, or a number of hours of use. */
sealed class Choice {
    object Default : Choice() { override fun toString() = "Default" }
    data class Days(val n: Int) : Choice()
    data class Hours(val h: Int) : Choice()

    /** Hours of use or days: the default is a number of days. */
    val unit: RentalUnit get() = if (this is Hours) RentalUnit.HOURS else RentalUnit.DAYS

    /** The sentence of the selector, never a conversion between the two units (docs W16 § 1.3). [defaultDays] is the duration the default stands for. */
    fun label(defaultDays: Int = 30): String = when (this) {
        Default -> "Sans durée précise : ${plural(defaultDays, "jour")}"
        is Days -> plural(n, "jour")
        is Hours -> "${plural(h, "heure")} d'utilisation"
    }

    companion object {
        private val SHORT = Regex("^(\\d{1,4})([jh])$")

        /** `defaut` / `défaut` / `default`, `7j`, `12h` (case and spaces ignored); anything else, `0j` included, is refused (French). */
        fun parse(s: String): Choice {
            val t = s.trim().lowercase()
            if (t == "defaut" || t == "défaut" || t == "default") return Default
            val m = SHORT.matchEntire(t) ?: throw IllegalArgumentException("choix de durée illisible : « ${s.trim()} » (attendu : defaut, 7j ou 12h)")
            val n = m.groupValues[1].toInt()
            if (n < 1) throw IllegalArgumentException("choix de durée illisible : « ${s.trim()} » (au moins 1)")
            return if (m.groupValues[2] == "j") Days(n) else Hours(n)
        }

        internal fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
    }
}

/**
 * The settings of the pilot, named like the W12 keys (docs W16 § 4.1; `tools/pilot/pilot.json` until W12 exists). Every value is BOUNDED ([init] refuses the others, in French).
 * `hourly.maxUseHours` is bounded by the engine's clamp ([HARD_CAP_HOURS]): a higher value would sell hours the TV silently drops.
 */
data class PilotParams(
    val userChosen: Boolean = true, val defaultDays: Int = 30, val pickerDays: List<Int> = listOf(1, 3, 7, 14), val maxDays: Int = 30,
    val hourlyMaxUseHours: Int = 96, val hourlyPickerHours: List<Int> = listOf(1, 3, 6, 12, 24, 48, 96), val hourlyValidityDays: Int = 30,
    val maxConcurrent: Int = 3, val weeklyQuotaHours: Int = 192, val cooldownMin: Int = 0,
    val pilotStart: Long = DEFAULT_START, val pilotEnd: Long = DEFAULT_END, val graceDays: Int = 14,
    val freeBundles: Set<String> = emptySet(),
) {
    init {
        fun range(key: String, v: Int, r: IntRange) { if (v !in r) throw IllegalArgumentException("$key : de ${r.first} à ${r.last} (reçu $v)") }
        fun steps(key: String, l: List<Int>, r: IntRange, not: Int? = null) {
            if (l.size > 8) throw IllegalArgumentException("$key : 8 paliers au plus")
            if (l.zipWithNext().any { (a, b) -> a >= b }) throw IllegalArgumentException("$key : des entiers strictement croissants")
            l.forEach { if (it !in r) throw IllegalArgumentException("$key : chaque palier de ${r.first} à ${r.last} (reçu $it)"); if (it == not) throw IllegalArgumentException("$key : $it est la durée par défaut, pas un palier") }
        }
        range("rental.maxDays", maxDays, 1..60); range("rental.defaultDays", defaultDays, 1..maxDays)
        range("rental.hourly.maxUseHours", hourlyMaxUseHours, 1..HARD_CAP_HOURS); range("rental.hourly.validityDays", hourlyValidityDays, 1..60)
        range("rental.maxConcurrent", maxConcurrent, 1..RentalLines.MAX_CONCURRENT); range("rental.hourly.weeklyQuotaHours", weeklyQuotaHours, 0..672)
        range("rental.cooldownMin", cooldownMin, 0..1440); range("pilot.graceDays", graceDays, 0..30)
        steps("rental.pickerDays", pickerDays, 1..maxDays, defaultDays); steps("rental.hourly.pickerHours", hourlyPickerHours, 1..hourlyMaxUseHours)
        freeBundles.forEach { if (!BUNDLE_ID.matches(it)) throw IllegalArgumentException("freeBundles : identifiant de bouquet invalide « $it »") }
        if (pilotEnd <= pilotStart) throw IllegalArgumentException("pilot.end : doit suivre pilot.start")
        if (pilotEnd - pilotStart > 90 * RentalLines.DAY_MS) throw IllegalArgumentException("pilot.end : 90 jours au plus après pilot.start")
    }

    companion object {
        /** The engine's clamp of an hourly contract, in hours ([RentalConfig.maxUseMinutesPerContract]). */
        val HARD_CAP_HOURS: Int = RentalConfig().maxUseMinutesPerContract.let { if (it > 0) (it / 60).toInt() else 96 }
        private val BUNDLE_ID = Regex("^[a-z0-9][a-z0-9-]{0,63}$")
        private val KNOWN_KEYS = setOf("rental.userChosen", "rental.defaultDays", "rental.pickerDays", "rental.maxDays", "rental.hourly.maxUseHours", "rental.hourly.pickerHours",
            "rental.hourly.validityDays", "rental.maxConcurrent", "rental.hourly.weeklyQuotaHours", "rental.cooldownMin", "pilot.start", "pilot.end", "pilot.graceDays", "freeBundles")
        /** Africa/Douala: UTC+1 all year, no daylight saving; an explicit offset needs no time-zone database on any TV. */
        val DOUALA: ZoneOffset = ZoneOffset.ofHours(1)
        val DEFAULT_START: Long = startOfDay(LocalDate.of(2026, 10, 12))
        val DEFAULT_END: Long = endOfDay(LocalDate.of(2026, 11, 1))

        fun startOfDay(d: LocalDate): Long = d.atStartOfDay().toInstant(DOUALA).toEpochMilli()
        fun endOfDay(d: LocalDate): Long = startOfDay(d.plusDays(1)) - 1
        fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atOffset(DOUALA).toLocalDate()

        /**
         * `pilot.json`: a flat object with the W12 key names. Missing keys take the pilot's values, except `pilot.start` / `pilot.end` (required). A date is milliseconds or `AAAA-MM-JJ`
         * (Douala: start of that day for `pilot.start`, its last millisecond for `pilot.end`); the two pickers are `"1,3,7,14"` or an array. Out of bounds = IllegalArgumentException (French).
         */
        fun parse(json: String): PilotParams {
            val m = try { JsonLite.obj(json) } catch (e: IllegalArgumentException) { throw IllegalArgumentException("Réglages du pilote illisibles (un objet JSON est attendu) : ${e.message}") }
            m.keys.firstOrNull { it !in KNOWN_KEYS }?.let { throw IllegalArgumentException("Réglages du pilote : clé inconnue « $it » (refusée par précaution : une faute de frappe ne doit jamais rouvrir un bouquet ; clés permises : ${KNOWN_KEYS.sorted().joinToString(", ")})") }
            fun names(k: String): Set<String> = when (val v = m[k]) {
                null -> emptySet()
                is String -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                is List<*> -> v.map { (it as? String)?.trim() ?: throw IllegalArgumentException("$k : des identifiants de bouquet (texte) sont attendus") }.toSet()
                else -> throw IllegalArgumentException("$k : une liste d'identifiants de bouquet est attendue")
            }
            fun int(k: String, d: Int): Int = when (val v = m[k]) {
                null -> d
                is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else throw IllegalArgumentException("$k : nombre hors bornes")
                else -> throw IllegalArgumentException("$k : un entier est attendu")
            }
            fun bool(k: String): Boolean = when (val v = m[k]) { null -> true; is Boolean -> v; 0L -> false; 1L -> true; else -> throw IllegalArgumentException("$k : 0 ou 1 attendu") }
            fun list(k: String, d: List<Int>): List<Int> = when (val v = m[k]) {
                null -> d
                is String -> if (v.isBlank()) emptyList() else v.split(',').map { it.trim().toIntOrNull() ?: throw IllegalArgumentException("$k : des entiers séparés par des virgules sont attendus") }
                is List<*> -> v.map { (it as? Long)?.takeIf { n -> n in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: throw IllegalArgumentException("$k : des entiers sont attendus") }
                else -> throw IllegalArgumentException("$k : une liste d'entiers est attendue")
            }
            fun moment(k: String, end: Boolean): Long = when (val v = m[k]) {
                null -> throw IllegalArgumentException("$k : absent (obligatoire)")
                is Long -> v
                is String -> try { LocalDate.parse(v.trim()).let { if (end) endOfDay(it) else startOfDay(it) } } catch (e: java.time.format.DateTimeParseException) { throw IllegalArgumentException("$k : date AAAA-MM-JJ attendue") }
                else -> throw IllegalArgumentException("$k : une date est attendue")
            }
            val d = PilotParams()
            return PilotParams(
                userChosen = bool("rental.userChosen"), defaultDays = int("rental.defaultDays", d.defaultDays), pickerDays = list("rental.pickerDays", d.pickerDays), maxDays = int("rental.maxDays", d.maxDays),
                hourlyMaxUseHours = int("rental.hourly.maxUseHours", d.hourlyMaxUseHours), hourlyPickerHours = list("rental.hourly.pickerHours", d.hourlyPickerHours),
                hourlyValidityDays = int("rental.hourly.validityDays", d.hourlyValidityDays), maxConcurrent = int("rental.maxConcurrent", d.maxConcurrent),
                weeklyQuotaHours = int("rental.hourly.weeklyQuotaHours", d.weeklyQuotaHours), cooldownMin = int("rental.cooldownMin", d.cooldownMin),
                pilotStart = moment("pilot.start", false), pilotEnd = moment("pilot.end", true), graceDays = int("pilot.graceDays", d.graceDays),
                freeBundles = names("freeBundles"),
            )
        }
    }
}

/**
 * One contract of a licence as the issuer knows it (registry, or `GET /api/rental`): [product] is `loc-<bundle>`, [maxUsageMinutes] the SUM granted over its lines (0 = days),
 * [endsAt] the end the signed lines give (hours: the safety date), [endedAt] when it ended earlier (budget used up), [reissues] the reinstalls already served.
 */
data class ContractSummary(
    val product: String, val period: Long, val unit: RentalUnit, val maxUsageMinutes: Int, val endsAt: Long, val reissues: Int = 0, val endedAt: Long? = null, val installPub: String? = null,
) {
    fun isActive(now: Long) = (endedAt == null || endedAt > now) && endsAt > now
    /** The instant it ended, if it did before [now]. */
    fun endedBefore(now: Long): Long? = minOf(endedAt ?: Long.MAX_VALUE, endsAt).takeIf { it <= now }
}

/** What the issuer knows of the licence: its contracts (finished ones may be listed, they only feed the cooldown) and the hours of use issued over the last 168 SLIDING hours ([PilotRegistry.hoursIssuedLast168h]). */
data class LicenseState(val active: List<ContractSummary> = emptyList(), val hoursIssuedLast7d: Int = 0) {
    fun activeCount(now: Long) = active.count { it.isActive(now) }
}

object PilotRules {
    private const val DAY = RentalLines.DAY_MS
    private const val MAX_REISSUES = 3
    private fun refuse(msg: String): Nothing = throw PilotRefusal(msg)
    private inline fun <T> rules(block: () -> T): Result<T> = try { Result.success(block()) } catch (e: PilotRefusal) { Result.failure(e) }
    private fun day(ms: Long) = PilotParams.dateOf(ms).format(DateTimeFormatter.ofPattern("dd/MM"))
    private fun time(ms: Long) = Instant.ofEpochMilli(ms).atOffset(PilotParams.DOUALA).format(DateTimeFormatter.ofPattern("HH:mm"))
    private fun hoursText(minutes: Int) = if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60}"
    private fun name(b: Bundle) = b.title.ifBlank { b.id }

    /** The last instant an HOURLY rental may still be in use: the end of the pilot plus the grace days (15/11 for the pilot). */
    private fun hoursLimit(p: PilotParams) = PilotParams.endOfDay(PilotParams.dateOf(p.pilotEnd).plusDays(p.graceDays.toLong()))

    /** The last instant a rental in DAYS (or the default) may still run: the end of the pilot plus the longest validity (01/12 for the pilot): honoured in full. */
    private fun daysLimit(p: PilotParams) = PilotParams.endOfDay(PilotParams.dateOf(p.pilotEnd).plusDays(p.maxDays.toLong()))

    private fun defaultDaysOf(b: Bundle, p: PilotParams) = minOf(p.maxDays, if (b.rentalDays > 0) b.rentalDays else p.defaultDays)
    private fun daysCap(b: Bundle, p: PilotParams) = minOf(p.maxDays, if (b.rentalDays > 0) b.rentalDays else p.maxDays)
    private fun hoursCap(p: PilotParams) = minOf(p.hourlyMaxUseHours, PilotParams.HARD_CAP_HOURS)

    /**
     * The days of validity written in the line. Default: the bundle's `rentalDays` else `defaultDays`, at most `maxDays`. Days: as chosen. Hours: the SAFETY bound, the whole days from the
     * issuing day to the end of the pilot plus its grace (12/10: 30, 25/10: 21, 01/11: 14), at most `hourly.validityDays`, at least 1.
     */
    fun validityDays(choice: Choice, issuedAt: Long, params: PilotParams, bundleRentalDays: Int = 0): Int = when (choice) {
        Choice.Default -> minOf(params.maxDays, if (bundleRentalDays > 0) bundleRentalDays else params.defaultDays)
        is Choice.Days -> choice.n
        is Choice.Hours -> {
            val left = java.time.temporal.ChronoUnit.DAYS.between(PilotParams.dateOf(issuedAt), PilotParams.dateOf(hoursLimit(params)))
            maxOf(1L, minOf(params.hourlyValidityDays.toLong(), left)).toInt()
        }
    }

    /** The bundle must exist, hold lots, and every lot must be RESERVED: a FREE lot (Langues, CC BY-SA) or a lot of unknown family is NEVER rented (fail closed). Returns the product id. */
    private fun bundleProduct(bundleId: String, catalog: BundleCatalog, families: LotFamilies, params: PilotParams): Pair<Bundle, String> {
        val b = catalog.find(bundleId) ?: refuse("bouquet inconnu : $bundleId")
        // Langues is NEVER rented, whatever LotFamilies says (a caller that derives « reserved = all - free » from a wrong free list would open it): by type, by prefix, by the `freeBundles` setting
        if (b.type.trim().equals("langues", ignoreCase = true) || b.id.lowercase().startsWith("langues")) refuse("« ${name(b)} » est un bouquet de Langues (contenu libre, CC BY-SA) : il ne se loue jamais")
        if (b.id in params.freeBundles) refuse("« ${name(b)} » est un bouquet libre (réglage freeBundles) : il reste libre et ne se loue jamais")
        if (b.lots.isEmpty()) refuse("« ${name(b)} » ne contient aucun lot : location refusée")
        for (k in b.lots.sorted()) when (LotNames.parseKey(k)?.let { families.of(it) }) {
            LotFamily.RESERVED -> {}
            LotFamily.FREE -> refuse("« ${name(b)} » est un bouquet libre (CC BY-SA) : il reste libre et ne se loue jamais")
            null -> refuse("« ${name(b)} » : famille du lot « $k » inconnue, location refusée par précaution")
        }
        return b to (RentalDurations.productOf(bundleId) ?: refuse("bouquet « $bundleId » : identifiant trop long pour une location"))
    }

    private fun window(issuedAt: Long, p: PilotParams) {
        if (issuedAt < p.pilotStart) refuse("Le test gratuit n'a pas commencé : il débute le ${day(p.pilotStart)}")
        if (issuedAt > p.pilotEnd) refuse("Le test gratuit est terminé (fin le ${day(p.pilotEnd)}) : plus aucune location n'est émise")
    }

    private fun quota(hours: Int, state: LicenseState, p: PilotParams) {
        if (p.weeklyQuotaHours > 0 && state.hoursIssuedLast7d + hours > p.weeklyQuotaHours)
            refuse("Quota atteint : ${p.weeklyQuotaHours} heures d'utilisation sur 168 heures glissantes par licence, il en reste ${maxOf(0, p.weeklyQuotaHours - state.hoursIssuedLast7d)}")
    }

    /** Hours in a line must never end after the safety date, days never after the honoured date: an invariant checked on every spec that leaves this module. */
    private fun checkEnd(end: Long, hourly: Boolean, p: PilotParams, slackMs: Long = 0) {
        val limit = (if (hourly) hoursLimit(p) else daysLimit(p)) + slackMs
        if (end > limit) refuse(if (hourly) "Les heures doivent être utilisées avant le ${day(limit)} (fin du test gratuit)" else "Cette location se terminerait après le ${day(limit)} : durée refusée")
    }

    /**
     * A NEW rental of [bundleId]: the spec of [choice] or the refusal. Refused: unknown / FREE / unknown-family bundle; outside the pilot window; a bundle already rented (extend it);
     * a bundle that ended less than `cooldownMin` ago; a 4th active contract (`maxConcurrent`); 97 h or more; more than `maxDays` (or the bundle's `rentalDays`); the 7-day quota.
     * With `userChosen = 0` only [Choice.Default] is accepted and the spec carries the catalogue's EXACT duration ([RentalDurations.daysOf]); no pilot window then.
     */
    fun spec(choice: Choice, bundleId: String, catalog: BundleCatalog, issuedAt: Long, state: LicenseState, params: PilotParams, families: LotFamilies): Result<RentalSpec> = rules {
        val (b, product) = bundleProduct(bundleId, catalog, families, params)
        if (params.userChosen) window(issuedAt, params)
        if (state.active.any { it.product == product && it.isActive(issuedAt) }) refuse("« ${name(b)} » est déjà loué : prolongez cette location, dans la même unité, au lieu d'en créer une autre")
        if (params.cooldownMin > 0) state.active.filter { it.product == product }.mapNotNull { it.endedBefore(issuedAt) }.maxOrNull()?.let { ended ->
            val again = ended + params.cooldownMin * 60_000L
            if (issuedAt < again) refuse("« ${name(b)} » vient de se terminer : on pourra le louer de nouveau à ${time(again)} (${day(again)})")
        }
        if (state.activeCount(issuedAt) >= params.maxConcurrent) refuse("Cette licence a déjà ${params.maxConcurrent} locations en cours : au plus ${params.maxConcurrent} à la fois")
        if (!params.userChosen) {
            if (choice != Choice.Default) refuse("La durée d'une location est fixée par le serveur : aucun choix de durée n'est offert")
            val days = RentalDurations.daysOf(catalog, bundleId).getOrElse { refuse(it.message ?: "durée refusée") }
            return@rules RentalSpec(product, listOf(bundleId), days, 0, 0, params.maxConcurrent, null)
        }
        when (choice) {
            Choice.Default -> RentalSpec(product, listOf(bundleId), defaultDaysOf(b, params), 0, 0, params.maxConcurrent, null).also { checkEnd(issuedAt + it.days * DAY, false, params) }
            is Choice.Days -> {
                val cap = daysCap(b, params)
                if (choice.n !in 1..cap) refuse("Une location en jours va de 1 à $cap jours (${choice.n} demandés)")
                RentalSpec(product, listOf(bundleId), choice.n, 0, 0, params.maxConcurrent, null).also { checkEnd(issuedAt + it.days * DAY, false, params) }
            }
            is Choice.Hours -> {
                val cap = hoursCap(params)
                if (choice.h !in 1..cap) refuse("Une location en heures d'utilisation va de 1 à $cap heures par location (${choice.h} demandées)")
                quota(choice.h, state, params)
                RentalSpec(product, listOf(bundleId), validityDays(choice, issuedAt, params), choice.h * 60, 0, params.maxConcurrent, null).also { checkEnd(issuedAt + it.days * DAY, true, params) }
            }
        }
    }

    /**
     * Extends the live contract [existing] (a RENEWAL line: same product, same `period`; the engine adds the lines up and keeps the same key). Refused: another bundle's contract; an ended
     * one; a unit other than the contract's (the engine would ignore the line, or worse give a budget-less line the power to unlock an hourly one); hours that would bring the contract
     * above 96 h; a validity above `maxDays` left in days; hours whose extra day would pass the safety date; the quota (hours); after the pilot; `userChosen = 0`; a FREE bundle.
     * HOURS: the line adds `h × 60` minutes and ONE day (the engine moves the end by the line's days: one is the least a line can carry). DAYS: the line adds the days.
     */
    fun extend(choice: Choice, existing: ContractSummary, bundleId: String, catalog: BundleCatalog, issuedAt: Long, state: LicenseState, params: PilotParams, families: LotFamilies): Result<RentalSpec> = rules {
        val (b, product) = bundleProduct(bundleId, catalog, families, params)
        if (existing.product != product) refuse("Ce contrat (${existing.product}) n'est pas celui de « ${name(b)} »")
        if (!params.userChosen) refuse("La durée est fixée par le serveur : cette location ne se prolonge pas")
        window(issuedAt, params)
        liveContract(existing, issuedAt)
        if (choice.unit != existing.unit) refuse("On ne mélange pas les heures et les jours : cette location est en ${if (existing.unit == RentalUnit.HOURS) "heures d'utilisation" else "jours"}")
        val newStart = maxOf(existing.endsAt, issuedAt)
        when (choice) {
            is Choice.Hours -> {
                val cap = hoursCap(params); val capMin = cap * 60
                if (choice.h < 1) refuse("Une prolongation compte au moins 1 heure d'utilisation")
                if (existing.maxUsageMinutes + choice.h * 60 > capMin)
                    refuse("Cette location a déjà ${hoursText(existing.maxUsageMinutes)} d'utilisation : on ne dépasse pas $cap h par location" + (if (existing.maxUsageMinutes < capMin) " (au plus ${hoursText(capMin - existing.maxUsageMinutes)} de plus)" else ""))
                quota(choice.h, state, params)
                // The renewal line carries ONE day (the least a line can hold) and the engine adds it to the end: a rental issued from 16/10 on already ends on the 15th at its hour of issue,
                // so the MERGED end of an extension may reach the 16th 23:59:59 (the 15th plus ONE day of tolerance, once per rental), never beyond. The screens then say « avant le 16/11 ».
                checkEnd(newStart + DAY, true, params, slackMs = DAY)
                RentalSpec(product, listOf(bundleId), 1, choice.h * 60, 0, params.maxConcurrent, existing.period)
            }
            else -> {
                val add = if (choice is Choice.Days) choice.n else defaultDaysOf(b, params)
                val cap = daysCap(b, params)
                if (add !in 1..cap) refuse("Une prolongation en jours va de 1 à $cap jours ($add demandés)")
                val leftMs = maxOf(0L, existing.endsAt - issuedAt)
                if (leftMs + add * DAY > params.maxDays * DAY) refuse("Cette location a déjà ${(leftMs + DAY - 1) / DAY} jour(s) restant(s) : la validité ne dépasse pas ${params.maxDays} jours")
                checkEnd(newStart + add * DAY, false, params)
                RentalSpec(product, listOf(bundleId), add, 0, 0, params.maxConcurrent, existing.period)
            }
        }
    }

    /**
     * The same rental given again to a reinstalled TV: same product, same `period`, at most 3 times (`RENTAL_REISSUE_ABUSE` beyond), REQUIRING an installation key other than the contract's
     * ([ContractSummary.installPub] vs [newInstallPub]). Hours: what is left (`min(granted, 96 h) − used`, counted in the quota), days: the WHOLE days left to the original end, rounded
     * down in both units (never later than the original end). Allowed after the pilot (no new right), refused for a finished contract, a FREE / Langues bundle, `userChosen = 0`.
     * The caller records the reissue in the register (type `reemission`, hours rounded UP).
     */
    fun reissue(existing: ContractSummary, usedMinutes: Int, issuedAt: Long, newInstallPub: String?, state: LicenseState, params: PilotParams, catalog: BundleCatalog, families: LotFamilies): Result<RentalSpec> = rules {
        if (!params.userChosen) refuse("La réémission suit les règles du pilote : elle est fermée tant que la durée est fixée par le serveur")
        // No pilot window here: a reissue gives NO new right (same product, same period, never later than the original end), so it stays possible after the pilot, until that end.
        if (!existing.product.startsWith(RentalDurations.PREFIX) || existing.product.length == RentalDurations.PREFIX.length) refuse("Contrat illisible : ${existing.product}")
        val bundle = existing.product.removePrefix(RentalDurations.PREFIX)
        bundleProduct(bundle, catalog, families, params)      // a FREE / Langues / unknown bundle is never given again either
        if (existing.reissues >= MAX_REISSUES) refuse("Cette location a déjà été réémise $MAX_REISSUES fois : c'est le maximum")
        liveContract(existing, issuedAt)
        if (usedMinutes < 0) refuse("Relevé d'usage invalide")
        // The engine groups lines by (product, period): on a TV that still holds the OLD activation the new line would be ADDED to it (end about doubled, usage given back).
        // Only a DIFFERENT installation (the box of the new line is sealed for another installation key) cannot merge with the old one.
        val old = existing.installPub?.takeIf { it.isNotBlank() } ?: refuse("La clé d'installation d'origine de cette location est inconnue : réémission refusée (on ne devine pas)")
        val new = newInstallPub?.takeIf { it.isNotBlank() } ?: refuse("La TV n'a pas fourni sa clé d'installation (mettez CastBridge-TV à jour) : réémission refusée")
        if (new == old) refuse("Même installation que la location d'origine : la TV garde déjà cette location, une réémission s'y ajouterait (fin doublée, heures rendues) ; prolongez-la plutôt")
        val wholeDays = (existing.endsAt - issuedAt) / DAY      // rounded DOWN: never to the user's benefit beyond the original end
        if (wholeDays < 1) refuse("Il reste moins d'un jour à cette location : une ligne compte au moins 1 jour, elle dépasserait la fin d'origine")
        val days = wholeDays.toInt()
        if (existing.unit == RentalUnit.HOURS) {
            val granted = minOf(existing.maxUsageMinutes, hoursCap(params) * 60)      // 96 h at most, even when the summary given is wrong
            val rest = granted - usedMinutes
            if (rest <= 0) refuse("Les heures d'utilisation de cette location sont épuisées : il n'y a rien à réémettre")
            quota((rest + 59) / 60, state, params)      // the hours are given again: they count
            checkEnd(issuedAt + days * DAY, true, params, slackMs = DAY)      // an extended rental may end on the 16th
            RentalSpec(existing.product, listOf(bundle), days, rest, 0, params.maxConcurrent, existing.period)
        } else {
            checkEnd(issuedAt + days * DAY, false, params)
            RentalSpec(existing.product, listOf(bundle), days, 0, 0, params.maxConcurrent, existing.period)
        }
    }

    /** [existing] must be a readable, live contract of the pilot, started before [issuedAt]. */
    private fun liveContract(existing: ContractSummary, issuedAt: Long) {
        if (existing.unit == RentalUnit.TRIAL) refuse("La fenêtre d'essai ne se prolonge pas")
        if ((existing.unit == RentalUnit.HOURS) != (existing.maxUsageMinutes > 0)) refuse("Contrat illisible : l'unité ne correspond pas au plafond d'usage")
        if (!existing.isActive(issuedAt)) refuse("Cette location est terminée : il faut la louer de nouveau (nouvelle location, nouveau décompte)")
        if (existing.period > issuedAt) refuse("La période à prolonger ne peut pas être dans le futur")
    }
}
