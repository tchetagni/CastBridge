package castbridge.core.wallet.millions

import castbridge.core.owner.SafeFile
import castbridge.core.tokens.TokenWallet
import castbridge.core.tokens.WalletKeyProvider
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Où vit l'état scellé des plafonds (texte). Les tests : [InMemoryCapsStore] ; la TV : [FileCapsStore]. [write] rend faux si l'écriture échoue (jamais d'exception). */
interface MillionsCapsStore { fun read(): String?; fun write(text: String): Boolean }

class InMemoryCapsStore : MillionsCapsStore {
    @Volatile private var text: String? = null
    override fun read(): String? = text
    override fun write(text: String): Boolean { this.text = text; return true }
}

/** État scellé dans un fichier, écrit atomiquement par [SafeFile] (modèle : `FileWalletMark`). */
class FileCapsStore(private val file: File) : MillionsCapsStore {
    override fun read(): String? = runCatching { SafeFile.read(file)?.text }.getOrNull()
    override fun write(text: String): Boolean = runCatching { SafeFile.write(file, text) }.isSuccess
}

/**
 * Plafonds de parties GAGNÉES du Défi (conception W22 § 13.9) : [WinLimits.perDay] par jour, [WinLimits.perWeek] par semaine (lundi-dimanche), [WinLimits.perMonth] par mois civil, en heure d'Africa/Douala
 * (UTC+1, sans heure d'été). Une partie est « gagnée » selon [WinDefinition] (`GAIN_GT_STAKE` : gain > mise, donc le retrait au palier 5 qui rend la mise ne compte pas).
 *
 * HEURE DE RÉFÉRENCE : le dernier horodatage serveur signé ([noteServerTime]) + le temps MONOTONE écoulé depuis, jamais l'heure murale (aucune horloge système n'est lue dans ce fichier :
 * changer l'heure de la TV ne rouvre rien). L'heure jugée la plus haute est persistée : après un redémarrage (monotone remis à zéro), la référence vaut max(horodatage serveur, dernière heure jugée).
 *
 * ÉTAT LOCAL SCELLÉ (HMAC sous la clé du portefeuille, modèle `WalletMark`) : un état altéré, scellé par une autre clé ou absent est IGNORÉ (compteurs à zéro) au profit du compteur du serveur
 * ([mergeServer], reçu dans le pack). Fusion avec le serveur = MAXIMUM par fenêtre : restaurer une sauvegarde ancienne ne remet rien à zéro après synchronisation.
 * Le plafond n'est vérifié qu'au DÉBUT d'une partie ([mayStart]) : une partie commencée peut toujours être gagnée ([recordEnd] ne refuse jamais).
 */
class MillionsWinCaps(
    limits: WinLimits, definition: WinDefinition, private val keys: WalletKeyProvider, private val store: MillionsCapsStore, private val mono: () -> Long,
) {
    enum class Cap { DAY, WEEK, MONTH, NO_CLOCK }

    sealed class CapStatus {
        object Open : CapStatus()
        /** Fermé avant toute mise : [text] est la phrase exacte à montrer, [reopensAtMs] le début de la fenêtre suivante (instant UTC) du plafond le plus contraignant. */
        data class Closed(val cap: Cap, val reopensAtMs: Long, val text: String) : CapStatus()
    }

    var limits: WinLimits = limits; private set
    var definition: WinDefinition = definition; private set

    private var kd = NONE_DAY; private var d = 0
    private var kw = NONE_DAY; private var w = 0
    private var km = NONE_MONTH; private var m = 0
    private var serverAt = 0L
    private var lastJudged = 0L
    private var monoAtServer = mono()

    init { load() }

    /** Nouveaux plafonds et définition (lus dans un pack signé). */
    fun configure(limits: WinLimits, definition: WinDefinition) { this.limits = limits; this.definition = definition }

    /** Un horodatage serveur signé (date d'émission du pack ou de l'instantané) : devient la base de l'heure de référence, sans jamais la faire reculer. */
    fun noteServerTime(atMs: Long) {
        refNowMs()
        serverAt = atMs; monoAtServer = mono()
        refNowMs(); persist()
    }

    /** Heure de référence (ms UTC), ou null tant qu'aucun horodatage serveur n'a été reçu. Ne recule jamais. */
    fun refNowMs(): Long? {
        if (serverAt == 0L && lastJudged == 0L) return null
        val r = maxOf(serverAt + maxOf(0L, mono() - monoAtServer), lastJudged)
        if (r > lastJudged) { lastJudged = r; persist() }
        return r
    }

    /** Fusionne les compteurs du serveur : par fenêtre, période plus récente = adoptée, même période = maximum, période plus ancienne = ignorée. */
    fun mergeServer(s: ServerCounts) {
        if (s.dayKey > kd) { kd = s.dayKey; d = s.day } else if (s.dayKey == kd) d = maxOf(d, s.day)
        if (s.weekKey > kw) { kw = s.weekKey; w = s.week } else if (s.weekKey == kw) w = maxOf(w, s.week)
        if (s.monthKey > km) { km = s.monthKey; m = s.month } else if (s.monthKey == km) m = maxOf(m, s.month)
        persist()
    }

    /** Parties gagnées dans les fenêtres COURANTES (zéro sans heure de référence). */
    fun counts(): MillionsJournal.WinCounts {
        val ref = refNowMs() ?: return MillionsJournal.WinCounts(0, 0, 0)
        val p = Periods.of(ref)
        return MillionsJournal.WinCounts(if (kd == p.day) d else 0, if (kw == p.week) w else 0, if (km == p.month) m else 0)
    }

    /** Ouvert, ou fermé AVANT la mise (plafond atteint, ou heure inconnue). */
    fun mayStart(): CapStatus {
        val ref = refNowMs() ?: return CapStatus.Closed(Cap.NO_CLOCK, 0, "Heure inconnue : connectez la TV pour la synchroniser avant de jouer.")
        val p = Periods.of(ref); val c = counts()
        var best: CapStatus.Closed? = null
        fun consider(cap: Cap, reached: Boolean, reopen: Long, text: String) { if (reached && (best == null || reopen >= best!!.reopensAtMs)) best = CapStatus.Closed(cap, reopen, text) }
        consider(Cap.DAY, c.day >= limits.perDay, p.nextDayMs, "Limite atteinte : ${parties(limits.perDay)} aujourd'hui. Prochaine partie possible demain à 00:00.")
        consider(Cap.WEEK, c.week >= limits.perWeek, p.nextWeekMs, "Limite atteinte : ${parties(limits.perWeek)} cette semaine. Prochaine partie possible lundi ${p.nextWeek.fmt()} à 00:00.")
        consider(Cap.MONTH, c.month >= limits.perMonth, p.nextMonthMs, "Limite atteinte : ${parties(limits.perMonth)} ce mois-ci. Prochaine partie possible le ${p.nextMonth.fmt()} à 00:00.")
        return best ?: CapStatus.Open
    }

    /** `Parties gagnées : 1/3 aujourd'hui · 4/10 cette semaine · 7/15 ce mois`. */
    fun progressLine(): String {
        val c = counts()
        return "Parties gagnées : ${c.day}/${limits.perDay} aujourd'hui · ${c.week}/${limits.perWeek} cette semaine · ${c.month}/${limits.perMonth} ce mois"
    }

    /** Une partie vient de finir avec [gain] pour [stake] : elle compte dans les trois fenêtres courantes si elle est « gagnée » ([definition]). Ne refuse JAMAIS (partie commencée sous le plafond). */
    fun recordEnd(stake: Long, gain: Long) {
        if (!definition.isWin(gain, stake)) return
        val ref = refNowMs() ?: return
        val p = Periods.of(ref)
        if (kd != p.day) { kd = p.day; d = 0 }
        if (kw != p.week) { kw = p.week; w = 0 }
        if (km != p.month) { km = p.month; m = 0 }
        d++; w++; m++
        persist()
    }

    // ---- état scellé ----

    private fun body() = listOf(MAGIC, "c=$kd|$d|$kw|$w|$km|$m", "t=$serverAt|$lastJudged")

    private fun persist() {
        val key = keys.key()?.takeIf { it.size == 32 } ?: return
        val b = body()
        store.write((b + "mac=${TokenWallet.mac(key, b.joinToString("\n"))}").joinToString("\n") + "\n")
    }

    private fun load() {
        runCatching {
            val key = keys.key()?.takeIf { it.size == 32 } ?: return
            val lines = (store.read() ?: return).split('\n').let { if (it.last().isEmpty()) it.dropLast(1) else it }
            require(lines.size == 4 && lines[0] == MAGIC && lines[1].startsWith("c=") && lines[2].startsWith("t=") && lines[3].startsWith("mac="))
            require(java.security.MessageDigest.isEqual(TokenWallet.mac(key, lines.take(3).joinToString("\n")).toByteArray(), lines[3].removePrefix("mac=").toByteArray()))
            val c = lines[1].removePrefix("c=").split('|'); require(c.size == 6)
            val t = lines[2].removePrefix("t=").split('|'); require(t.size == 2)
            fun n(s: String, max: Long) = s.toLong().also { require(it.toString() == s && it in 0..max) }
            require(DAY_RE.matches(c[0]) && DAY_RE.matches(c[2]) && MONTH_RE.matches(c[4]))
            kd = c[0]; d = n(c[1], 100_000).toInt(); kw = c[2]; w = n(c[3], 100_000).toInt(); km = c[4]; m = n(c[5], 100_000).toInt()
            serverAt = n(t[0], 1_000_000_000_000_000L); lastJudged = n(t[1], 1_000_000_000_000_000L)
            monoAtServer = mono()
        }.onFailure { kd = NONE_DAY; d = 0; kw = NONE_DAY; w = 0; km = NONE_MONTH; m = 0; serverAt = 0; lastJudged = 0 }   // altéré : ignoré (compteur du serveur à la prochaine synchronisation)
    }

    private fun parties(n: Int) = if (n == 1) "1 partie gagnée" else "$n parties gagnées"
    private fun LocalDate.fmt() = "%02d/%02d".format(dayOfMonth, monthValue)

    /** Les fenêtres calendaires d'Africa/Douala qui contiennent l'instant [ref]. */
    private class Periods(val day: String, val week: String, val month: String, val nextDayMs: Long, val nextWeek: LocalDate, val nextWeekMs: Long, val nextMonth: LocalDate, val nextMonthMs: Long) {
        companion object {
            fun of(ref: Long): Periods {
                val date = java.time.Instant.ofEpochMilli(ref).atZone(DOUALA).toLocalDate()
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val nextMonday = monday.plusWeeks(1); val nextMonth = YearMonth.from(date).plusMonths(1).atDay(1)
                fun ms(x: LocalDate) = x.atStartOfDay(DOUALA).toInstant().toEpochMilli()
                return Periods(date.toString(), monday.toString(), YearMonth.from(date).toString(), ms(date.plusDays(1)), nextMonday, ms(nextMonday), nextMonth, ms(nextMonth))
            }
        }
    }

    private companion object {
        val DOUALA: ZoneId = ZoneId.of("Africa/Douala")
        const val MAGIC = "castbridge-millions-caps-v1"
        const val NONE_DAY = "0000-00-00"
        const val NONE_MONTH = "0000-00"
        val DAY_RE = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
        val MONTH_RE = Regex("^[0-9]{4}-[0-9]{2}$")
    }
}
