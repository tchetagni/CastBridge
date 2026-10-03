package castbridge.core.store

import castbridge.core.lots.LotFamily
import castbridge.core.lots.QueueStore
import castbridge.core.lots.RentalDurations
import castbridge.core.lots.RentalLines
import castbridge.core.lots.RentalState
import castbridge.core.lots.RentalStatus
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import castbridge.core.store.RentRequest.Kind
import java.io.IOException

/**
 * La file des demandes de location de la TV (DESIGN-W17 § 4.2), persistée dans un [QueueStore] (`files/store/requests.json` sur la TV).
 *
 * CE QUE LA DEMANDE N'EST PAS. Une demande est une intention : elle ne crée, ne prolonge, ne signe et n'installe rien, et aucune méthode de cette classe ne rend ni ne reçoit
 * autre chose que des faits en lecture seule (les contrats déjà connus de la TV) et des états de demande. Seul l'émetteur décide, après qu'un adulte a confirmé sur le téléphone.
 * La TV ne signe rien : le fichier ne contient ni code parental, ni jeton, ni clé, ni preuve.
 *
 * MODÈLE DE MENACE (surface = nuisance bornée). Quiconque a le code d'accès du réseau local peut déposer une demande : au pire il remplit la file ou fait apparaître une
 * notification sur le téléphone, qui n'accorde rien sans l'adulte. Bornes : au plus [MAX_PENDING] demandes en attente, une seule par (bouquet, choix), expiration à
 * [EXPIRY_MS] (7 jours), au plus [MAX_DECIDED] demandes acquittées gardées [KEEP_DECIDED_MS] (30 jours). Profil enfant, TV d'essai, bouquet libre ou de famille inconnue,
 * quota de contrats, unité différente d'un contrat en cours et fin de pilote sont refusés ICI, avant toute écriture ([create]).
 *
 * NONCE, ACQUITTEMENT, REJEU. Le nonce (8 hex) vient de l'aléa injecté et n'est jamais réutilisé tant que la demande est gardée. [ack] est idempotent : même réponse deux fois =
 * [Outcome.SAME] ; une réponse contraire, ou une réponse après expiration, ne change rien ([Outcome.CONFLICT]) ; nonce inconnu ou mal formé = [Outcome.UNKNOWN].
 *
 * HORLOGE. [now] est INJECTÉ et obligatoire : l'intégration (w17-04) passe l'heure JUGÉE de la TV (`RentalEngine.judge(tvClock, wall).now`) ; la file n'a aucune mémoire d'horloge
 * propre. Une horloge reculée ne fait JAMAIS expirer une demande (âge négatif = pas d'expiration) ; une demande expire seulement quand `maintenant - création > 7 jours`.
 *
 * CORRUPTION ET REPRISE. Un fichier illisible donne une file vide et [degraded] = vrai, jamais une exception. Un fichier servi par la copie `.bak` ([BackupAwareStore]) est
 * [degraded] et ne rend AUCUNE demande en attente (elle a pu être acquittée depuis : on ne la ressuscite pas, l'utilisateur redemandera). À la relecture, une demande d'une
 * autre TV que [tvId] est écartée et les bornes (20 en attente, 50 acquittées, 30 jours) sont réappliquées.
 *
 * FICHIER UNIQUE. L'intégration doit garantir UNE seule instance de [RentRequests] par fichier (singleton du processus) : la file ne verrouille que dans l'instance. Rendre
 * [Outcome.STORAGE] et [Refusal.STORAGE] en erreur serveur (5xx) dans `StoreApi`, jamais en 200.
 */
class RentRequests(private val store: QueueStore, private val tvId: String, private val now: () -> Long, private val random: () -> String) {
    enum class State { PENDING, ACCEPTED, REFUSED, EXPIRED, FULFILLED }
    enum class Answer { ACCEPTED, REFUSED }
    enum class Outcome { APPLIED, SAME, CONFLICT, UNKNOWN, STORAGE }

    /** Résultat de [ack] : ce qui s'est passé et l'état de la demande après (null si le nonce est inconnu). */
    data class AckResult(val outcome: Outcome, val state: State?)

    /** Une demande gardée avec son état ; [decidedAt] vaut 0 tant qu'elle est en attente. */
    data class Entry(val request: RentRequest, val state: State, val createdAt: Long, val decidedAt: Long)

    /**
     * Les faits en lecture seule dont [create] a besoin : l'identité de la TV, TV d'essai, profil enfant, famille du bouquet (`null` = inconnue), les contrats déjà connus,
     * le nombre de contrats utilisables permis, la fin du pilote (`null` = inconnue) et l'origine de la demande.
     */
    data class Facts(
        val trialTv: Boolean, val kidActive: Boolean, val shelf: StoreCatalog.Shelf, val lotFamilies: List<LotFamily?>, val rentals: List<RentalStatus>,
        val maxConcurrent: Int, val pilotEndMs: Long?, val origin: RentRequest.Origin = RentRequest.Origin.TV,
    ) {
        /** La famille du bouquet entier, par [familyOf]. */
        val family: LotFamily? get() = familyOf(shelf, lotFamilies)
    }

    sealed class Created {
        data class Ok(val entry: Entry) : Created()
        data class Refused(val reason: Refusal, val message: String) : Created()
    }

    /** [baseEnd] et [baseUsage] : seuils que le contrat doit DÉPASSER pour que la prolongation soit livrée (fin ou minutes d'usage à la création, plus ce que les prolongations encore ouvertes ajouteront d'abord). */
    private class Record(val request: RentRequest, var state: State, val createdAt: Long, var decidedAt: Long, val baseEnd: Long, val baseUsage: Long)

    private val lock = Any()
    private var items = ArrayList<Record>()

    /** Vrai si le fichier était illisible (ou en partie) au chargement : la file repart de ce qui était valide. */
    var degraded: Boolean = false
        private set

    init { load() }

    /**
     * Crée une demande `PENDING` pour [bundle] et [choice], ou la refuse. Ordre de contrôle, le premier qui s'applique gagne :
     * MALFORMED (téléviseur, bouquet, grammaire du choix) · TRIAL_TV · KID_PROFILE · UNKNOWN_FAMILY · FREE_BUNDLE · BAD_CHOICE (hors sélecteur) · PILOT_ENDED ·
     * [extend] sans contrat : ENDED (contrat terminé) ou NO_CONTRACT · SAME_BUNDLE_OTHER_UNIT (contrat non terminé d'une autre unité) · OVER_LIMIT (nouvelle location seulement) ·
     * DUPLICATE · QUEUE_FULL · STORAGE (nonce impossible à tirer, écriture impossible : rien n'est gardé).
     * Un contrat non terminé du bouquet dans la même unité fait une prolongation (`kind=extend`, `period` du contrat) ; sinon c'est une nouvelle location.
     */
    fun create(bundle: String, choice: String, facts: Facts, extend: Boolean = false): Created = synchronized(lock) {
        val t = now()
        if (!RentRequest.validTv(tvId) || !RentRequest.validBundle(bundle) || !RentRequest.validChoice(choice)) return refuse(Refusal.MALFORMED)
        if (facts.trialTv) return refuse(Refusal.TRIAL_TV)
        if (facts.kidActive) return refuse(Refusal.KID_PROFILE)
        when (facts.family) { null -> return refuse(Refusal.UNKNOWN_FAMILY); LotFamily.FREE -> return refuse(Refusal.FREE_BUNDLE); LotFamily.RESERVED -> {} }
        if (!RentRequest.inPicker(choice)) return refuse(Refusal.BAD_CHOICE)
        if (facts.pilotEndMs != null && t > facts.pilotEndMs) return refuse(Refusal.PILOT_ENDED)
        val snapshot = ArrayList(items)
        expireLocked(t)
        val mine = facts.rentals.filter { bundle in it.contract.bundleIds }
        val live = mine.filter { it.state != RentalState.EXPIRED }
        val current = live.firstOrNull { it.usable } ?: live.firstOrNull()
        if (extend && current == null) return refuse(if (mine.isNotEmpty()) Refusal.ENDED else Refusal.NO_CONTRACT)
        val wantHours = choice.endsWith("h")
        if (current != null && (current.contract.maxUsageMinutes > 0) != wantHours) return refuse(Refusal.SAME_BUNDLE_OTHER_UNIT)
        if (current == null) {
            val usable = facts.rentals.filter { it.usable }
            val covered = usable.flatMap { it.contract.bundleIds }.toSet()
            val openNew = items.filter { it.request.kind == Kind.NEW && (it.state == State.PENDING || it.state == State.ACCEPTED) && it.request.bundle != bundle && it.request.bundle !in covered }.map { it.request.bundle }.toSet()
            if (usable.size + openNew.size >= facts.maxConcurrent) return refuse(Refusal.OVER_LIMIT, facts.maxConcurrent)
        }
        if (items.any { it.state == State.PENDING && it.request.bundle == bundle && it.request.choice == choice }) return refuse(Refusal.DUPLICATE)
        if (items.count { it.state == State.PENDING } >= MAX_PENDING) return refuse(Refusal.QUEUE_FULL)
        val nonce = generateSequence { random() }.take(8).firstOrNull { RentRequest.validNonce(it) && items.none { r -> r.request.nonce == it } }
        if (nonce == null) { items = snapshot; return refuse(Refusal.STORAGE) }
        val request = RentRequest(tvId, bundle, choice, if (current != null) Kind.EXTEND else Kind.NEW, current?.contract?.period ?: 0L, nonce, t, facts.origin)
        val ahead = if (current == null) emptyList() else items.filter { (it.state == State.PENDING || it.state == State.ACCEPTED) && it.request.kind == Kind.EXTEND && it.request.bundle == bundle && it.request.period == current.contract.period && it.request.unit == request.unit }
        val baseEnd = (current?.contract?.endsAt ?: 0L) + if (wantHours) 0L else ahead.sumOf { amountOf(it.request.choice) * RentalLines.DAY_MS }
        val baseUsage = (current?.contract?.maxUsageMinutes ?: 0L) + if (wantHours) ahead.sumOf { amountOf(it.request.choice) * 60L } else 0L
        val record = Record(request, State.PENDING, t, 0, baseEnd, baseUsage)
        items.add(record)
        prune(t)
        if (!persist()) { items = snapshot; return refuse(Refusal.STORAGE) }
        Created.Ok(entryOf(record))
    }

    /**
     * Acquittement du téléphone, idempotent (voir la description de la classe). Applique d'abord l'expiration : une demande de plus de 7 jours ne ressuscite jamais.
     * Écriture impossible : [Outcome.STORAGE], rien n'est changé.
     */
    fun ack(nonce: String, answer: Answer): AckResult = synchronized(lock) {
        val t = now()
        val snapshot = items.map { Record(it.request, it.state, it.createdAt, it.decidedAt, it.baseEnd, it.baseUsage) }
        val expired = expireLocked(t)
        val r = if (RentRequest.validNonce(nonce)) items.firstOrNull { it.request.nonce == nonce } else null
        if (r == null) { if (expired > 0) persist(); return AckResult(Outcome.UNKNOWN, null) }
        val result = when {
            r.state == State.PENDING -> { r.state = if (answer == Answer.ACCEPTED) State.ACCEPTED else State.REFUSED; r.decidedAt = t; AckResult(Outcome.APPLIED, r.state) }
            (answer == Answer.ACCEPTED && (r.state == State.ACCEPTED || r.state == State.FULFILLED)) || (answer == Answer.REFUSED && r.state == State.REFUSED) -> AckResult(Outcome.SAME, r.state)
            else -> AckResult(Outcome.CONFLICT, r.state)
        }
        if (result.outcome == Outcome.APPLIED || expired > 0) {
            prune(t)
            if (!persist()) { items = ArrayList(snapshot); return AckResult(Outcome.STORAGE, snapshot.firstOrNull { it.request.nonce == nonce }?.state) }
        }
        result
    }

    /** Fait expirer les demandes en attente de plus de 7 jours à [nowMs] ; rend leur nombre. Une horloge reculée n'expire rien. */
    fun expire(nowMs: Long = now()): Int = synchronized(lock) { expireLocked(nowMs).also { if (it > 0) { prune(nowMs); persist() } } }

    /**
     * Rapproche les demandes ouvertes (`PENDING` ou `ACCEPTED`) des contrats que la TV connaît : une demande devient `FULFILLED` quand un contrat UTILISABLE couvre son bouquet
     * (nouvelle location), ou, pour une prolongation, quand le contrat de même `period` a dépassé le seuil de la demande : sa fin repoussée (jours) ou son usage augmenté (heures :
     * la borne de sûreté peut laisser la fin inchangée). Une demande `PENDING` y passe aussi : l'adulte a pu confirmer, la livraison est arrivée et l'acquittement s'est perdu ; sans
     * cela l'écran proposerait une seconde émission. Deux prolongations ouvertes (6 h puis 12 h) n'aboutissent qu'avec leur livraison respective (seuils cumulés).
     * Rend le nombre de demandes accomplies. Ne regarde que des contrats, n'en crée jamais.
     */
    fun reconcile(rentals: List<RentalStatus>): Int = synchronized(lock) {
        val t = now()
        var n = 0
        for (r in items) {
            if (r.state != State.PENDING && r.state != State.ACCEPTED) continue
            val done = rentals.any {
                it.usable && r.request.bundle in it.contract.bundleIds && (r.request.kind == Kind.NEW || (it.contract.period == r.request.period &&
                    if (r.request.unit == RentRequest.Unit.HOURS) it.contract.maxUsageMinutes > r.baseUsage else it.contract.endsAt > r.baseEnd))
            }
            if (done) { r.state = State.FULFILLED; r.decidedAt = t; n++ }
        }
        if (n > 0) { prune(t); persist() }
        n
    }

    /** Les bouquets qui ont une demande encore en attente (et non périmée) : pour `StoreView.Facts.pendingRequests`. */
    fun pendingBundles(): Set<String> = synchronized(lock) {
        val t = now()
        items.filter { it.state == State.PENDING && !stale(it, t) }.map { it.request.bundle }.toSet()
    }

    /** Toutes les demandes gardées, dans l'ordre de création. */
    fun entries(): List<Entry> = synchronized(lock) { items.map(::entryOf) }

    private fun entryOf(r: Record) = Entry(r.request, r.state, r.createdAt, r.decidedAt)
    private fun stale(r: Record, t: Long) = t - r.createdAt > EXPIRY_MS

    private fun expireLocked(t: Long): Int {
        var n = 0
        for (r in items) if (r.state == State.PENDING && stale(r, t)) { r.state = State.EXPIRED; r.decidedAt = r.createdAt + EXPIRY_MS; n++ }
        return n
    }

    /** Garde les demandes en attente, et au plus [MAX_DECIDED] acquittées de moins de [KEEP_DECIDED_MS] (les plus récentes). */
    private fun prune(t: Long) {
        val decided = items.withIndex().filter { it.value.state != State.PENDING && t - it.value.decidedAt <= KEEP_DECIDED_MS }
            .sortedWith(compareByDescending<IndexedValue<Record>> { it.value.decidedAt }.thenByDescending { it.index }).take(MAX_DECIDED).map { it.value }.toSet()
        items = ArrayList(items.filter { it.state == State.PENDING || it in decided })
    }

    private fun persist(): Boolean {
        val doc = mapOf("format" to FILE_FORMAT, "items" to items.map {
            mapOf("request" to it.request.canonical(), "state" to it.state.name, "createdAt" to it.createdAt, "decidedAt" to it.decidedAt, "baseEnd" to it.baseEnd, "baseUsage" to it.baseUsage)
        })
        return try { store.save(JsonLite.write(doc)); true } catch (e: IOException) { false }
    }

    private fun load() {
        val text: String? = try { store.load() } catch (e: Exception) { degraded = true; null }
        if (text == null) return
        try {
            val doc = JsonLite.obj(text)
            val list = doc["items"] as? List<*> ?: run { degraded = true; return }
            if (doc.str("format") != FILE_FORMAT) { degraded = true; return }
            for (raw in list) {
                @Suppress("UNCHECKED_CAST") val m = raw as? Map<String, Any?>
                if (m == null) { degraded = true; continue }
                val req = (m.str("request")?.let { RentRequest.parse(it) } as? RentRequest.Parsed.Ok)?.request
                val state = m.str("state")?.let { s -> State.entries.firstOrNull { it.name == s } }
                val created = m.long("createdAt")
                if (req == null || state == null || created == null || req.tv != tvId || items.any { it.request.nonce == req.nonce }) { degraded = true; continue }
                items.add(Record(req, state, created, m.long("decidedAt") ?: 0L, m.long("baseEnd") ?: 0L, m.long("baseUsage") ?: 0L))
            }
        } catch (e: Exception) { degraded = true; items = ArrayList() }
        if ((store as? BackupAwareStore)?.loadedFromBackup == true) { degraded = true; items.removeAll { it.state == State.PENDING } }
        val t = now()
        expireLocked(t)
        if (items.count { it.state == State.PENDING } > MAX_PENDING) {
            var seen = 0
            items = ArrayList(items.filter { it.state != State.PENDING || ++seen <= MAX_PENDING })
            degraded = true
        }
        prune(t)
    }

    private fun amountOf(choice: String): Long = if (choice == RentRequest.DEFAULT) RentalDurations.DEFAULT_DAYS.toLong() else choice.dropLast(1).toLong()

    // TODO(w17-02) : phrases locales BAD_CHOICE, ENDED, NO_CONTRACT, STORAGE et QUEUE_FULL (absentes de DESIGN-W17 § 2.5) ; reprendre toutes les phrases dans StoreTexts à la fusion de w17-02.
    private fun refuse(reason: Refusal, max: Int = 0): Created.Refused = Created.Refused(reason, when (reason) {
        Refusal.MALFORMED -> "Demande de location invalide : bouquet, choix ou téléviseur illisible."
        Refusal.BAD_CHOICE -> "Cette durée n'est pas proposée : choisissez une durée de la liste."
        Refusal.TRIAL_TV -> "Version complète nécessaire : les locations demandent une clé de production."
        Refusal.KID_PROFILE -> "Un profil enfant est actif : demandez à un parent."
        Refusal.FREE_BUNDLE -> "Gratuit : rien à louer."
        Refusal.UNKNOWN_FAMILY -> "Cet article n'est pas louable pour le moment."
        Refusal.OVER_LIMIT -> "$max locations en cours sur cette TV : attendez la fin de l'une d'elles."
        Refusal.SAME_BUNDLE_OTHER_UNIT -> "Ce bouquet est déjà loué avec une autre unité : prolongez-le dans la même unité, ou attendez la fin pour changer."
        Refusal.PILOT_ENDED -> "Le test gratuit est terminé ; tarifs bientôt disponibles."
        Refusal.DUPLICATE -> "Déjà demandé : en attente de confirmation sur le téléphone."
        Refusal.QUEUE_FULL -> "Trop de demandes en attente ($MAX_PENDING) : confirmez ou refusez-en sur le téléphone."
        Refusal.ENDED -> "Location terminée : relouer pour la reprendre."
        Refusal.NO_CONTRACT -> "Aucune location à prolonger pour ce bouquet."
        Refusal.STORAGE -> "Impossible d'enregistrer la demande : réessayez."
    })

    companion object {
        /**
         * La famille d'un bouquet entier à partir de celle de chacun de ses lots (`null` = lot inconnu). Un bouquet est GRATUIT dès qu'il appartient au rayon Langues OU qu'il contient
         * un lot gratuit (décision du propriétaire : Langues reste gratuit, jamais louable) ; sinon inconnu (`null`) s'il est vide ou contient un lot inconnu ; sinon réservé.
         */
        fun familyOf(shelf: StoreCatalog.Shelf, lots: List<LotFamily?>): LotFamily? = when {
            shelf == StoreCatalog.Shelf.LANGUES || lots.any { it == LotFamily.FREE } -> LotFamily.FREE
            lots.isEmpty() || lots.any { it == null } -> null
            else -> LotFamily.RESERVED
        }

        const val MAX_PENDING = 20
        const val MAX_DECIDED = 50
        const val EXPIRY_MS = 7L * 24 * 3600 * 1000
        const val KEEP_DECIDED_MS = 30L * 24 * 3600 * 1000
        const val FILE_FORMAT = "castbridge-rent-requests-v1"
    }
}
