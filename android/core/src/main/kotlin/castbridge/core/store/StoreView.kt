package castbridge.core.store

import castbridge.core.lots.LotFamily
import castbridge.core.lots.LotId
import castbridge.core.lots.LotNames
import castbridge.core.lots.LotStage
import castbridge.core.lots.LotsToDeliver
import castbridge.core.lots.RentalDurations
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.lots.TvManifest
import castbridge.core.lots.TvRentalView
import castbridge.core.net.JsonLite
import castbridge.core.store.StoreCatalog.Shelf
import castbridge.core.store.StoreCatalog.Store
import castbridge.core.store.StoreCatalog.StoreItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Vue d'état de la Boutique (w17-02, conception § 2.3 et § 2.5) : pour chaque [StoreItem] de [StoreCatalog], UN état, ses lignes de carte
 * (titre exclu), ses boutons et la raison d'un éventuel blocage, calculés à partir de faits déjà lus. Fonction pure : aucune I/O, aucune
 * horloge (l'heure est dans [Facts.nowMs]), aucun texte hors de [StoreTexts], aucun prix. Le téléphone (CastBridge) et la TV
 * (CastBridge-TV) appellent la même fonction ; seule [Facts.side] change quelques phrases, et la TV ne produit jamais [State.ENVOYE].
 */
object StoreView {
    /** Appareil qui dessine. */
    enum class Side { PHONE, TV }

    /** État d'un article (un seul ; priorité : gratuit, loué, sur la TV, terminé, envoyé, échantillon, pas sur la TV). */
    enum class State { GRATUIT, ECHANTILLON, PAS_SUR_TV, ENVOYE, SUR_TV, LOUE, TERMINE, A_LOUER, BLOQUE }

    /** Raison d'un blocage (conception § 2.5). */
    enum class Reason { NO_TV, TV_UNREACHABLE, TRIAL, KID, QUOTA, ALREADY_RENTED, FREE, UNKNOWN_FAMILY, PILOT_ENDED, SPACE }

    /** Nature du bouton de location : une location neuve, une prolongation, une nouvelle location après la fin. */
    enum class RentKind { RENT, EXTEND, RERENT }

    data class RentButton(val kind: RentKind, val label: String, val enabled: Boolean = true)

    /** [hard] = la location est impossible (bouton absent) ; sinon simple information (la demande est gardée). */
    data class Blocked(val reason: Reason, val phrase: String, val hard: Boolean)

    /**
     * Faits lus par l'appelant. [pendingRequests] : bouquet demandé, avec la date de la demande (ms). [rentals] = contrats lus sur la TV
     * (côté TV) ; [tvRentals] = ce que la TV a dit au téléphone (côté téléphone). [granted] = bouquets achetés ou abonnés.
     */
    data class Facts(
        val store: Store, val nowMs: Long, val side: Side = Side.PHONE, val tvManifest: TvManifest? = null,
        val rentals: List<RentalStatus> = emptyList(), val tvRentals: TvRentalView? = null, val granted: Set<String> = emptySet(),
        val trialTv: Boolean = false, val kidActive: Boolean = false, val tvKnown: Boolean = true, val tvReachable: Boolean = true,
        val phoneStages: Map<LotId, LotStage> = emptyMap(), val pendingRequests: Map<String, Long> = emptyMap(), val pilotEndMs: Long? = null,
        val superUnlimited: Boolean = false, val maxConcurrent: Int = 3, val zone: ZoneId = ZoneId.of("Africa/Douala"),
    )

    data class Card(val item: StoreItem, val state: State, val lines: List<String>, val rentButton: RentButton?, val sendButton: LotsToDeliver.SendButton?, val blocked: Blocked?)
    data class ShelfView(val shelf: Shelf, val section: String, val cards: List<Card>)
    data class Screen(val shelves: List<ShelfView>, val header: String, val banner: String?)

    private const val DAY_MS = 86_400_000L
    private const val RECENT_END_MS = 30 * DAY_MS

    fun decide(f: Facts): Screen = build(f)

    private class Cover(val usable: Boolean, val expired: Boolean, val endsAt: Long?, val line: String, val leftMs: Long?)

    private fun build(f: Facts): Screen {
        val covers = covers(f)
        val usableCount = if (f.side == Side.TV) f.rentals.count { it.usable } else f.tvRentals?.rentals.orEmpty().count { it.usable }
        val shelves = ArrayList<ShelfView>()
        for (item in f.store.items) {
            val c = card(f, item, covers[item.id], usableCount)
            val last = shelves.lastOrNull()
            if (last != null && last.shelf == item.shelf && last.section == item.section) shelves[shelves.lastIndex] = last.copy(cards = last.cards + c)
            else shelves += ShelfView(item.shelf, item.section, listOf(c))
        }
        return Screen(shelves, f.store.catalogLabel, banner(f))
    }

    private fun banner(f: Facts): String? {
        val tv = f.side == Side.TV
        if (f.store.items.isEmpty()) return if (tv) StoreTexts.CATALOG_ABSENT_TV else StoreTexts.CATALOG_ABSENT_PHONE
        val at = f.store.catalogAt?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
        val today = Instant.ofEpochMilli(f.nowMs).atZone(f.zone).toLocalDate()
        if (at != null && at.plusDays(30).isBefore(today)) return StoreTexts.OLD_CATALOG
        f.pilotEndMs?.takeIf { f.nowMs < it }?.let { return StoreTexts.pilotBanner(dayMonth(it, f.zone)) }
        if (tv && f.trialTv) return StoreTexts.TRIAL_TV
        return null
    }

    /** Contrat le plus parlant couvrant chaque article (par identifiant d'article) : un contrat en cours avant un contrat terminé, le plus tardif d'abord. */
    private fun covers(f: Facts): Map<String, Cover> {
        val out = HashMap<String, Cover>()
        for (item in f.store.items) {
            val product = RentalDurations.productOf(item.id)
            val keys = item.lots.map { LotNames.key(it.id) }.toSet()
            val found: List<Cover> = if (f.side == Side.TV) {
                f.rentals.filter { item.id in it.contract.bundleIds || (product != null && it.contract.productId == product) }.map { r ->
                    val usage = r.remainingUsageMinutes
                    val line = if (r.usable && usage != null) StoreTexts.usageLeft(usage) else r.message.ifBlank { r.state.name }
                    Cover(r.usable, r.state == RentalState.EXPIRED, r.contract.endsAt, line, r.remainingMs)
                }
            } else {
                f.tvRentals?.rentals.orEmpty().filter { (product != null && it.product == product) || (keys.isNotEmpty() && it.lots.containsAll(keys)) }.map { r ->
                    Cover(r.usable, r.state == RentalState.EXPIRED.name, null, r.message.ifBlank { r.state }, r.remainingMs)
                }
            }
            found.sortedWith(compareBy({ it.expired }, { -(it.endsAt ?: 0L) })).firstOrNull()?.let { out[item.id] = it }
        }
        return out
    }

    private fun card(f: Facts, item: StoreItem, cover0: Cover?, usableCount: Int): Card {
        val tvSide = f.side == Side.TV
        val free = item.family == LotFamily.FREE || item.shelf == Shelf.LANGUES
        val unlimited = f.superUnlimited || f.tvRentals?.superUnlimited == true
        val onTv = f.tvManifest?.lots?.map { it.meta.id }?.toSet().orEmpty()
        val fullOnTv = item.lots.isNotEmpty() && item.lots.all { it.id in onTv }
        val anyOnTv = item.lots.any { it.id in onTv }
        val sampleOnTv = item.trialLots.any { it.id in onTv }
        // une location terminée depuis plus de 30 jours (TV) ne compte plus ; côté téléphone la date de fin n'est pas connue
        val cover = cover0?.takeIf { !it.expired || it.endsAt == null || f.nowMs - it.endsAt <= RECENT_END_MS }
        val stage = if (tvSide) null else aggregate(item, f.phoneStages)
        val inFlight = stage == LotStage.SENDING || stage == LotStage.SENT || stage == LotStage.WAITING_TV
        val base = when {
            free -> State.GRATUIT
            cover != null && !cover.expired -> State.LOUE
            unlimited && fullOnTv -> State.SUR_TV
            cover != null -> State.TERMINE
            fullOnTv && item.id in f.granted -> State.SUR_TV
            inFlight && !anyOnTv -> State.ENVOYE
            sampleOnTv && !anyOnTv -> State.ECHANTILLON
            f.tvManifest != null && !anyOnTv -> State.PAS_SUR_TV
            else -> State.A_LOUER
        }
        val pending = f.pendingRequests[item.id]
        val kind = when (base) { State.LOUE -> RentKind.EXTEND; State.TERMINE -> RentKind.RERENT; else -> RentKind.RENT }
        val tv = f.tvManifest
        val rentable = !free && base != State.SUR_TV
        val block: Blocked? = when {
            free -> Blocked(Reason.FREE, StoreTexts.FREE_NOTHING_TO_RENT, false)
            !rentable -> null
            kind != RentKind.EXTEND && item.family == null -> Blocked(Reason.UNKNOWN_FAMILY, StoreTexts.UNKNOWN_FAMILY, true)
            f.trialTv -> Blocked(Reason.TRIAL, if (tvSide) StoreTexts.TRIAL_TV else StoreTexts.TRIAL_PHONE, true)
            f.kidActive -> Blocked(Reason.KID, if (tvSide) StoreTexts.KID_TV else StoreTexts.KID_PHONE, true)
            !tvSide && !f.tvKnown && kind != RentKind.EXTEND -> Blocked(Reason.NO_TV, StoreTexts.NO_TV_PHONE, true)
            kind != RentKind.EXTEND && !unlimited && usableCount >= f.maxConcurrent ->
                Blocked(Reason.QUOTA, if (tvSide) StoreTexts.quotaTv(f.maxConcurrent) else StoreTexts.quotaPhone(f.maxConcurrent), true)
            f.pilotEndMs != null && f.nowMs >= f.pilotEndMs -> Blocked(Reason.PILOT_ENDED, StoreTexts.PILOT_ENDED, true)
            kind == RentKind.RENT && tv != null && !anyOnTv && item.bytes > tv.remainingBytes ->
                (item.bytes - tv.remainingBytes).let { Blocked(Reason.SPACE, if (tvSide) StoreTexts.spaceTv(it) else StoreTexts.spacePhone(it), true) }
            base == State.LOUE && pending != null ->
                Blocked(Reason.ALREADY_RENTED, StoreTexts.alreadyRented(item.title, StoreTexts.shortLeft(cover?.leftMs ?: 0L)), true)
            else -> null
        }
        val unreachable = !tvSide && f.tvKnown && !f.tvReachable
        val blocked = block ?: if (rentable && unreachable && pending == null) Blocked(Reason.TV_UNREACHABLE, StoreTexts.TV_UNREACHABLE_PHONE, false) else null
        val rent = if (rentable && block == null && pending == null) RentButton(kind, when (kind) {
            RentKind.EXTEND -> StoreTexts.EXTEND
            RentKind.RERENT -> StoreTexts.RERENT
            RentKind.RENT -> if (tvSide) StoreTexts.RENT_TV else StoreTexts.RENT_FREE
        }) else null
        val state = if (block?.hard == true && (base == State.A_LOUER || base == State.PAS_SUR_TV)) State.BLOQUE else base

        val lines = ArrayList<String>()
        val features = item.lots.map { it.id.feature }.distinct().sortedBy { FEATURE_ORDER.indexOf(it).let { i -> if (i < 0) FEATURE_ORDER.size else i } }
        lines += StoreTexts.content(features, item.lots.size, item.bytes)
        tvLine(base, tvSide, fullOnTv)?.let { lines += it }
        when {
            base == State.LOUE && cover != null -> lines += cover.line
            base == State.TERMINE -> lines += cover?.endsAt?.let { StoreTexts.endedOn(dayMonth(it, f.zone)) } ?: StoreTexts.ENDED_NO_DATE
            unlimited && base == State.SUR_TV -> lines += StoreTexts.UNLIMITED
        }
        if (pending != null) lines += StoreTexts.pending(dayMonth(pending, f.zone))
        val send = if (tvSide || stage == null) null else LotsToDeliver.sendButton(stage, true).takeIf { it.visible }
        return Card(item, state, lines, rent, send, blocked)
    }

    private val FEATURE_ORDER = listOf("learn", "quiz", "langues", "oeuvres")
    private val STAGE_ORDER = listOf(LotStage.SENDING, LotStage.SENT, LotStage.WAITING_TV, LotStage.ON_PHONE, LotStage.TV_OUTDATED_DATA, LotStage.REFUSED, LotStage.UP_TO_DATE)

    /** Étape résumée des lots déjà téléchargés de l'article (null = aucun lot sur le téléphone). */
    private fun aggregate(item: StoreItem, stages: Map<LotId, LotStage>): LotStage? {
        val held = item.lots.mapNotNull { stages[it.id] }.filter { it != LotStage.NOT_DOWNLOADED }
        return STAGE_ORDER.firstOrNull { it in held }
    }

    private fun tvLine(base: State, tv: Boolean, fullOnTv: Boolean): String? = when (base) {
        State.GRATUIT -> if (!tv) StoreTexts.BADGE_FREE else if (fullOnTv) StoreTexts.FREE_ON_TV else StoreTexts.FREE_ASK_PHONE
        State.ECHANTILLON -> if (tv) StoreTexts.SAMPLE_TV else StoreTexts.SAMPLE_PHONE
        State.PAS_SUR_TV -> if (tv) StoreTexts.NOT_ON_TV_TV else StoreTexts.NOT_ON_TV_PHONE
        State.SUR_TV, State.LOUE -> if (tv) StoreTexts.ON_TV_TV else StoreTexts.ON_TV_PHONE
        State.A_LOUER -> StoreTexts.AVAILABLE_TO_RENT
        State.ENVOYE, State.TERMINE, State.BLOQUE -> null
    }

    private fun dayMonth(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) }

    // ---- Fil (GET /api/store, w17-04) ----

    /** Carte telle qu'elle voyage : sans le détail de l'article. */
    data class WireCard(val id: String, val title: String, val state: String, val lines: List<String>, val rent: RentButton?, val send: LotsToDeliver.SendButton?, val blocked: Blocked?)
    data class WireShelf(val shelf: String, val section: String, val cards: List<WireCard>)
    data class WireScreen(val header: String, val banner: String?, val shelves: List<WireShelf>)

    fun json(s: Screen): String = JsonLite.write(linkedMapOf(
        "header" to s.header, "banner" to s.banner,
        "shelves" to s.shelves.map { sh ->
            linkedMapOf("shelf" to sh.shelf.name, "section" to sh.section, "cards" to sh.cards.map { c ->
                linkedMapOf("id" to c.item.id, "title" to c.item.title, "state" to c.state.name, "lines" to c.lines,
                    "buttons" to linkedMapOf(
                        "rent" to c.rentButton?.let { linkedMapOf("kind" to it.kind.name, "label" to it.label, "enabled" to it.enabled) },
                        "send" to c.sendButton?.let { linkedMapOf("enabled" to it.enabled, "label" to it.label) }),
                    "blocked" to c.blocked?.let { linkedMapOf("reason" to it.reason.name, "phrase" to it.phrase, "hard" to it.hard) })
            })
        }))

    @Suppress("UNCHECKED_CAST")
    fun parse(text: String): WireScreen {
        val o = JsonLite.obj(text)
        fun map(v: Any?) = v as? Map<String, Any?>
        return WireScreen(o["header"] as? String ?: "", o["banner"] as? String, (o["shelves"] as? List<*>).orEmpty().mapNotNull { map(it) }.map { sh ->
            WireShelf(sh["shelf"] as? String ?: "", sh["section"] as? String ?: "", (sh["cards"] as? List<*>).orEmpty().mapNotNull { map(it) }.map { c ->
                val b = map(c["buttons"]); val r = map(b?.get("rent")); val s = map(b?.get("send")); val bl = map(c["blocked"])
                WireCard(c["id"] as? String ?: "", c["title"] as? String ?: "", c["state"] as? String ?: "", (c["lines"] as? List<*>).orEmpty().map { it.toString() },
                    r?.let { RentButton(RentKind.valueOf(it["kind"] as String), it["label"] as String, it["enabled"] == true) },
                    s?.let { LotsToDeliver.SendButton(true, it["enabled"] == true, it["label"] as? String ?: "") },
                    bl?.let { Blocked(Reason.valueOf(it["reason"] as String), it["phrase"] as? String ?: "", it["hard"] == true) })
            })
        })
    }
}
