package castbridge.core.lots

import castbridge.core.net.JsonLite

/**
 * The lots the user downloaded BY HAND (« Télécharger » on a lesson of the server's list): they must reach the TV like the profile's
 * lots, whatever the learner profile says. File-backed (same safe write as the delivery queue), survives a restart.
 */
class PinnedLots(private val store: QueueStore) {
    private val lock = Any()
    private val ids = LinkedHashSet<LotId>()

    init {
        runCatching {
            (JsonLite.obj(store.load() ?: "{}")["pinned"] as? List<*>).orEmpty().forEach { (it as? String)?.let(LotNames::parseKey)?.let(ids::add) }
        }
    }

    private fun persist() { try { store.save(JsonLite.write(linkedMapOf("pinned" to ids.map(LotNames::key)))) } catch (_: java.io.IOException) { /* kept in memory; the next change retries */ } }

    fun add(id: LotId): Boolean = synchronized(lock) { ids.add(id).also { if (it) persist() } }
    fun remove(id: LotId): Boolean = synchronized(lock) { ids.remove(id).also { if (it) persist() } }
    fun contains(id: LotId): Boolean = synchronized(lock) { id in ids }
    fun list(): List<LotId> = synchronized(lock) { ids.sortedBy(LotNames::key) }
}

/**
 * What « Envoyer à la TV » delivers: the profile's needs PLUS the pinned (manually downloaded) lots, through the very same planner
 * (priority, TV budget, never evict silently). Pure: no I/O, tested by table.
 */
object LotsToDeliver {
    /** Same rank as the Langues profile lots (LangPlanner.NEED_BASE + 10). */
    const val PINNED_PRIORITY = 110

    enum class Action { SEND, ALREADY_ON_TV, NOT_INSTALLED, OVER_BUDGET, REFUSED_BY_TV }
    data class Item(val id: LotId, val action: Action, val reason: String? = null)
    data class Decision(val needs: List<Need>, val items: List<Item>, val plan: LotPlanner.Plan) {
        val toSend: List<LotId> get() = items.filter { it.action == Action.SEND }.map { it.id }
        fun of(id: LotId): Item? = items.firstOrNull { it.id == id }
    }

    /** The per-card « Envoyer à la TV » button: shown or not, enabled or not, and its French label. */
    data class SendButton(val visible: Boolean, val enabled: Boolean, val label: String)

    fun sendButton(stage: LotStage, held: Boolean): SendButton = when {
        !held || stage == LotStage.NOT_DOWNLOADED || stage == LotStage.REFUSED -> SendButton(false, false, "")
        stage == LotStage.UP_TO_DATE -> SendButton(true, false, "Déjà sur la TV")
        stage == LotStage.SENDING -> SendButton(true, false, "Envoi en cours")
        stage == LotStage.SENT -> SendButton(true, false, "Envoyé")
        else -> SendButton(true, true, "Envoyer à la TV")
    }

    /** The profile's needs, then the pinned lots the profile does not already ask for (they keep the profile's priority when both). */
    fun needs(profileNeeds: List<Need>, pinned: Collection<LotId>): List<Need> {
        val have = profileNeeds.map { it.id }.toSet()
        return profileNeeds + pinned.distinct().filter { it !in have }.sortedBy(LotNames::key).map { Need(it, PINNED_PRIORITY) }
    }

    fun decide(
        profileNeeds: List<Need>, pinned: Collection<LotId>, installed: Collection<LotMeta>, onTv: Collection<LotMeta>,
        starterBytes: Long = 0L, budget: Long = LotBudget.TV_MAX_BYTES, rejected: List<LotRejection> = emptyList(),
    ): Decision {
        val n = needs(profileNeeds, pinned)
        val plan = LotPlanner.plan(n, installed, onTv, starterBytes, budget)
        val held = installed.associateBy { it.id }
        val onPlan = plan.toSend.map { it.id }.toSet()
        val skipped = plan.skipped.associateBy { it.meta.id }
        val items = n.map { it.id }.distinct().map { id ->
            val m = held[id]
            val rej = rejected.firstOrNull { it.id == id && m != null && it.version >= m.version }
            when {
                m == null -> Item(id, Action.NOT_INSTALLED, "pas encore téléchargé sur le téléphone")
                id in skipped -> Item(id, Action.OVER_BUDGET, skipped.getValue(id).reason)
                id !in onPlan -> Item(id, Action.ALREADY_ON_TV)
                rej != null -> Item(id, Action.REFUSED_BY_TV, rej.reason)
                else -> Item(id, Action.SEND)
            }
        }
        return Decision(n, items, plan)
    }
}
