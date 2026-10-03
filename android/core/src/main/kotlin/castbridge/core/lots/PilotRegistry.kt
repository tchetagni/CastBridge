package castbridge.core.lots

import castbridge.core.owner.SafeFile
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * The issuer's register of pilot rentals (docs W16 § 3.2): one CSV line per issuing, `date,licence,code,bouquet,period,unite,quantite,jours,type`. The text part is pure; [load] and [update]
 * are the only file access, atomic (SafeFile: temporary file, fsync, rename, `.bak`) and serialised (one issuing at a time, across threads and processes). No personal data: the TV code is
 * MASKED ([mask], enforced by [Entry]), the licence is an opaque id. It carries what the rules need, not the signed line: the hours issued (quota), the period in progress per
 * (licence, bundle) (extension and reissue need it), the duplicates of the day (idempotence). Immutable: [append] returns a new register. Times are Douala (UTC+1, explicit).
 */
class PilotRegistry(val entries: List<Entry> = emptyList()) {
    /**
     * [date]: `AAAA-MM-JJ` (counts until the end of that day: conservative for the quota) or `AAAA-MM-JJTHH:MM` (Douala); [unit]: `heures`, `jours` or `defaut` ([quantity] = hours of use, days,
     * days; 0 is accepted for a REISSUE of less than an hour: the caller then records the hours rounded up, 1 at least); [days] = days of validity written in the line;
     * [type]: `nouvelle`, `prolongation`, `reemission`; [maskedCode]: see [mask].
     */
    data class Entry(val date: String, val license: String, val maskedCode: String, val bundle: String, val period: Long, val unit: String, val quantity: Int, val days: Int, val type: String, val installFp: String = "") {
        init {
            try { if (date.contains('T')) LocalDateTime.parse(date) else LocalDate.parse(date) } catch (e: DateTimeParseException) { throw IllegalArgumentException("date « $date » : AAAA-MM-JJ ou AAAA-MM-JJTHH:MM attendu") }
            for ((n, v) in listOf("licence" to license, "code" to maskedCode, "bouquet" to bundle, "unité" to unit, "type" to type))
                if (v.isBlank() || v.any { it == ',' || it == '\n' || it == '\r' }) throw IllegalArgumentException("$n : vide, ou contient une virgule ou un saut de ligne")
            if (!MASKED.matches(maskedCode)) throw IllegalArgumentException("code TV « $maskedCode » : le registre ne garde qu'un code MASQUÉ (2 caractères, « … », 2 caractères : PilotRegistry.mask)")
            if (unit !in UNITS) throw IllegalArgumentException("unité « $unit » : heures, jours ou defaut")
            if (type !in TYPES) throw IllegalArgumentException("type « $type » : nouvelle, prolongation ou reemission")
            if (period <= 0 || days < 1 || quantity < 1) throw IllegalArgumentException("period, quantité et jours : des nombres positifs (une réémission de moins d'une heure s'écrit 1 : arrondi supérieur)")
            if (installFp.isNotEmpty() && !FP.matches(installFp)) throw IllegalArgumentException("empreinte d'installation « $installFp » : 16 caractères hexadécimaux attendus (jamais la clé elle-même)")
        }
        val day: String get() = date.take(10)
        fun line() = listOf(date, license, maskedCode, bundle, period, unit, quantity, days, type, installFp.ifEmpty { "-" }).joinToString(",")
    }

    companion object {
        val UNITS = setOf("heures", "jours", "defaut")
        val TYPES = setOf("nouvelle", "prolongation", "reemission")
        const val HEADER = "date,licence,code,bouquet,period,unite,quantite,jours,type,install"
        private const val OLD_HEADER = "date,licence,code,bouquet,period,unite,quantite,jours,type"
        private val FP = Regex("^[0-9a-f]{16}$")
        private val MASKED = Regex("^[A-Za-z0-9]{1,3}…[A-Za-z0-9]{1,3}$")
        private const val WINDOW_MS = 168L * 3600 * 1000
        private val MONITOR = Any()

        /** What the register keeps of an installation key: the first 16 hex characters of its SHA-256 (a fingerprint, never the key). */
        fun fingerprint(installPub: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(installPub.toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

        /** The only form of a TV code the register keeps: the first 2 and the last 2 characters of the code (8 or more letters and digits), joined by « … ». Idempotent. */
        fun mask(code: String): String {
            val c = code.trim()
            if (MASKED.matches(c)) return c
            if (c.length < 8 || !c.all { it.isLetterOrDigit() && it.code < 128 }) throw IllegalArgumentException("code TV illisible : 8 caractères (lettres et chiffres) au moins sont attendus pour le masquer")
            return c.take(2) + "…" + c.takeLast(2)
        }

        /** Reads the file's text (CRLF, blank lines and the header line tolerated); a bad line = IllegalArgumentException « ligne N … » (French). */
        fun parse(csv: String): PilotRegistry = PilotRegistry(csv.replace("\r", "").lines().withIndex().filter { (_, l) -> l.isNotBlank() && l.trim() != HEADER && l.trim() != OLD_HEADER }.map { (i, l) ->
            val f = l.trim().split(',')
            try {
                if (f.size != 9 && f.size != 10) throw IllegalArgumentException("9 ou 10 champs attendus, ${f.size} trouvés")
                Entry(f[0], f[1], f[2], f[3], f[4].toLongOrNull() ?: throw IllegalArgumentException("period : un nombre est attendu"), f[5], f[6].toIntOrNull() ?: throw IllegalArgumentException("quantité : un nombre est attendu"),
                    f[7].toIntOrNull() ?: throw IllegalArgumentException("jours : un nombre est attendu"), f[8], f.getOrNull(9)?.let { if (it == "-") "" else it } ?: "")
            } catch (e: IllegalArgumentException) { throw IllegalArgumentException("Registre du pilote, ligne ${i + 1} illisible : ${e.message}") }
        })

        /** The register in [file] (empty when there is none yet; the `.bak` answers when the file is corrupt). Both unreadable = IllegalStateException: nothing is issued on a blind register. */
        fun load(file: File): PilotRegistry {
            val r = SafeFile.read(file) { runCatching { parse(it) }.isSuccess }
            if (r == null) {
                if (file.exists() || SafeFile.bak(file).exists()) throw IllegalStateException("Registre du pilote illisible (ni ${file.name} ni sa copie .bak) : ne rien émettre avant de l'avoir réparé")
                return PilotRegistry()
            }
            // FAIL-CLOSED: the .bak is the state BEFORE the last write. Issuing from it would write a stale register over the real one (last issuing lost, quota undercounted, period in progress forgotten).
            if (r.fromBackup) throw IllegalStateException("Registre à vérifier : ${file.name} est illisible et seule la copie précédente (.bak) répond ; rien n'est émis ni écrit tant que le registre n'a pas été contrôlé (copiez le .bak à la main après vérification)")
            return parse(r.text)
        }

        /**
         * Reads, applies [change] and writes back ATOMICALLY, under one lock (threads of this process and other processes, `<file>.lock`): two simultaneous issuings cannot lose each other.
         * A refusal thrown by [change] (duplicate, period) leaves the file untouched. Returns the new register.
         */
        fun update(file: File, change: (PilotRegistry) -> PilotRegistry): PilotRegistry = synchronized(MONITOR) {
            file.absoluteFile.parentFile?.mkdirs()
            FileChannel.open(File(file.absoluteFile.parentFile, file.name + ".lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { ch ->
                ch.lock().use {
                    val next = change(load(file))
                    SafeFile.write(file.absoluteFile, next.format()) { runCatching { parse(it) }.isSuccess }
                    next
                }
            }
        }
    }

    fun format(): String = (listOf(HEADER) + entries.map { it.line() }).joinToString("\n", postfix = "\n")

    /**
     * The contract in progress for (licence, bundle) as the TV engine will merge it ([RentalEngine.contracts], pinned by a test): the latest `period` not after [now]; `endsAt = period + Σ days`
     * of the lines of that period (new, extensions, reissues: every line adds its days, a renewal always starts before the running end); budget = Σ hours × 60 of the lines of the SAME unit as
     * the first one (the engine ignores the others), clamped at 96 h like the engine; `reissues` = number of `reemission` lines; `installPub` = the FINGERPRINT of the first line's installation
     * (null when unknown). `endedAt` is unknown to the register (null). Null when there is no such contract.
     */
    fun summary(license: String, bundle: String, now: Long): ContractSummary? {
        val mine = entries.filter { it.license == license && it.bundle == bundle }
        val period = mine.map { it.period }.filter { it <= now }.maxOrNull() ?: return null
        val all = mine.filter { it.period == period }
        val first = all.first()
        val hourly = first.unit == "heures"
        val lines = all.filter { (it.unit == "heures") == hourly }
        val budget = if (hourly) minOf(lines.sumOf { it.quantity.toLong() * 60 }, PilotParams.HARD_CAP_HOURS * 60L).toInt() else 0
        return ContractSummary("loc-$bundle", period, if (hourly) RentalUnit.HOURS else RentalUnit.DAYS, budget, period + lines.sumOf { it.days.toLong() } * RentalLines.DAY_MS,
            reissues = all.count { it.type == "reemission" }, endedAt = null, installPub = first.installFp.ifEmpty { null })
    }
    fun find(license: String): List<Entry> = entries.filter { it.license == license }

    /** The `period` of the rental in progress for (licence, bundle): the highest one issued, or null. */
    fun latestPeriod(license: String, bundle: String): Long? = entries.filter { it.license == license && it.bundle == bundle }.maxOfOrNull { it.period }

    /** Same licence, bundle, choice (unit and quantity), day and TYPE (a new rental and an extension of the same size are two orders): the same order issued twice (the tool then asks for `--encore`). */
    fun isDuplicate(e: Entry): Boolean = entries.any { it.license == e.license && it.bundle == e.bundle && it.unit == e.unit && it.quantity == e.quantity && it.day == e.day && it.type == e.type }

    /**
     * Hours of use issued to [license] over the 168 SLIDING hours ending at [nowMs] (the quota, `rental.hourly.weeklyQuotaHours`): new rentals, extensions AND reissues in hours (a reissue
     * gives the hours again), not days. A timed entry counts in `(now − 168 h, now]`; an entry with a date alone counts until the end of its day (never later than today).
     */
    fun hoursIssuedLast168h(license: String, nowMs: Long): Int {
        val lower = nowMs - WINDOW_MS
        val upper = nowMs + WINDOW_MS      // an entry written by an issuer whose clock is ahead still counts, up to 168 h ahead
        val lastDay = PilotParams.dateOf(upper)
        return entries.filter { it.license == license && it.unit == "heures" }.filter { e ->
            if (e.date.contains('T')) LocalDateTime.parse(e.date).toInstant(PilotParams.DOUALA).toEpochMilli().let { it > lower && it <= upper }
            else LocalDate.parse(e.date).let { !it.isAfter(lastDay) && PilotParams.endOfDay(it) > lower }
        }.sumOf { it.quantity }
    }

    /**
     * A new register with [e]. Refused (IllegalArgumentException, French): a duplicate (unless [allowDuplicate]); an extension or a reissue whose `period` is not the one in progress for
     * that licence and bundle; a new rental reusing a period of that licence and bundle.
     */
    fun append(e: Entry, allowDuplicate: Boolean = false): PilotRegistry {
        if (!allowDuplicate && isDuplicate(e)) throw IllegalArgumentException("Cette commande a déjà été émise le ${e.day} : même licence, même bouquet, même choix (--encore pour l'émettre une seconde fois)")
        val latest = latestPeriod(e.license, e.bundle)
        when (e.type) {
            "nouvelle" -> if (entries.any { it.license == e.license && it.bundle == e.bundle && it.period == e.period }) throw IllegalArgumentException("Cette location existe déjà (period ${e.period}) : une nouvelle location a une nouvelle period")
            else -> if (latest == null || latest != e.period) throw IllegalArgumentException("Une ${if (e.type == "prolongation") "prolongation" else "réémission"} demande la period en cours (${latest ?: "aucune location connue"}) pour ce bouquet et cette licence, pas ${e.period}")
        }
        return PilotRegistry(entries + e)
    }
}
