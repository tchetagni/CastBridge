package castbridge.core.lots

/** A lot the TV should hold if there is room. [priority]: 0 = most important (the active profile's class), larger = less. */
data class Need(val id: LotId, val priority: Int)

/** A profile of the TV and the scopes (classes/levels) it studies, in order of interest. */
data class ProfileNeed(val scopes: List<String>, val active: Boolean = false)

/**
 * Which lots the TV should hold: the subset of what the phone has that fits in the TV's budget (10 Mo, bundled starter
 * data included), chosen deterministically. Pure and tested; the phone runs it at every contact with the TV, from the TV's own
 * manifest (so the plan always matches what the TV really holds, even after a TV reset).
 *
 * Rules: lots are taken by priority (active profile first, then the others in the order given), then smallest first
 * (more lots for the same space), then by name; a lot that does not fit is SKIPPED (with the reason and a suggestion of which
 * lots to drop to make room) and the following, smaller ones may still fit. A lot is only planned if the phone holds it.
 */
object LotPlanner {
    data class Skipped(val meta: LotMeta, val reason: String, val dropSuggestion: List<LotId>)

    data class Plan(
        /** Everything the TV should hold, most important first (counted in [usedBytes]). */
        val wanted: List<LotMeta>,
        val priority: Map<LotId, Int>,
        /** [wanted] minus what the TV already holds in that exact version. */
        val toSend: List<LotMeta>,
        val skipped: List<Skipped>,
        /** Needed but not on the phone yet (to download first when the phone has Internet). */
        val notOnPhone: List<LotId>,
        val starterBytes: Long,
        val budget: Long,
        val usedBytes: Long,
    ) {
        val remainingBytes get() = budget - usedBytes
    }

    /** One [Need] per (profile scope, feature); the first profile with a scope sets its priority; active profiles come first. */
    fun needsOf(profiles: List<ProfileNeed>, features: List<String> = listOf("learn", "quiz")): List<Need> {
        val ordered = profiles.sortedBy { if (it.active) 0 else 1 }   // stable: the order given otherwise
        val out = LinkedHashMap<LotId, Int>()
        ordered.forEachIndexed { pi, p ->
            p.scopes.forEachIndexed { si, scope ->
                for (f in features) out.getOrPut(LotId(f, scope)) { pi * 10 + minOf(si, 9) }
            }
        }
        return out.map { Need(it.key, it.value) }
    }

    fun plan(
        needs: List<Need>,
        onPhone: Collection<LotMeta>,
        onTv: Collection<LotMeta>,
        starterBytes: Long,
        budget: Long = LotBudget.TV_MAX_BYTES,
    ): Plan {
        val phone = onPhone.associateBy { it.id }
        val tv = onTv.associateBy { it.id }
        val best = LinkedHashMap<LotId, Int>()
        for (n in needs) best.merge(n.id, n.priority) { a, b -> minOf(a, b) }
        val missing = best.keys.filter { it !in phone }
        val candidates = best.filterKeys { it in phone }.map { phone.getValue(it.key) to it.value }.sortedWith(
            compareBy({ it.second }, { it.first.bytes }, { it.first.id.feature }, { it.first.id.scope }))
        var used = starterBytes
        val wanted = ArrayList<Pair<LotMeta, Int>>()
        val skipped = ArrayList<Pair<LotMeta, Int>>()
        for (c in candidates) {
            if (used + c.first.bytes <= budget) { wanted += c; used += c.first.bytes } else skipped += c
        }
        val skippedOut = skipped.map { (m, _) ->
            val missingBytes = used + m.bytes - budget
            val why = if (starterBytes + m.bytes > budget) "« ${m.title} » (${LotStore.mo(m.bytes)}) ne tient pas dans les ${LotStore.mo(budget)} de la TV, même vide"
            else "pas assez de place sur la TV pour « ${m.title} » : il manque ${LotStore.mo(missingBytes)}"
            Skipped(m, why, dropSuggestion(m, wanted, used, budget))
        }
        return Plan(
            wanted = wanted.map { it.first },
            priority = wanted.associate { it.first.id to it.second },
            toSend = wanted.map { it.first }.filter { m -> tv[m.id]?.let { it.version == m.version && it.sha256 == m.sha256 || it.version > m.version } != true },
            skipped = skippedOut, notOnPhone = missing, starterBytes = starterBytes, budget = budget, usedBytes = used,
        )
    }

    /** The least important lots (then the biggest) whose removal would make [m] fit; empty if even all of them would not be enough. */
    private fun dropSuggestion(m: LotMeta, wanted: List<Pair<LotMeta, Int>>, used: Long, budget: Long): List<LotId> {
        var need = used + m.bytes - budget
        val out = ArrayList<LotId>()
        for ((w, _) in wanted.sortedWith(compareBy({ -it.second }, { -it.first.bytes }, { it.first.id.feature }, { it.first.id.scope }))) {
            if (need <= 0) break
            out += w.id; need -= w.bytes
        }
        return if (need <= 0) out else emptyList()
    }
}
