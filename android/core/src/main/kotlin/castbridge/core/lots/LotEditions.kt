package castbridge.core.lots

/**
 * Trial lots are SEPARATE lots named "<scope>-trial" (docs/TRIAL-EDITION.md § Choix de conception), not slices of the full lot:
 * a lot stays atomic (installed, replaced, signed and verified whole), the server can refuse the full one and still serve the
 * trial one, and the full lot replaces the trial one cleanly (same lesson / question identifiers inside, nothing to merge).
 */
object LotEditions {
    const val SUFFIX = "-trial"

    fun isTrialScope(scope: String) = scope.endsWith(SUFFIX) && scope.length > SUFFIX.length
    fun trialOf(id: LotId) = if (isTrialScope(id.scope)) id else LotId(id.feature, id.scope + SUFFIX)
    fun fullOf(id: LotId) = if (isTrialScope(id.scope)) LotId(id.feature, id.scope.removeSuffix(SUFFIX)) else id
    fun isTrial(id: LotId) = isTrialScope(id.scope)

    /** A lot is TRIAL exactly when its scope ends with "-trial" (a full scope may never end that way); the length limit still holds. */
    fun consistent(m: LotMeta) = (m.edition == Edition.TRIAL) == isTrialScope(m.id.scope)

    /** The trial lots that a full lot of the same base makes redundant: remove them (nothing is lost: same identifiers inside the full lot). */
    fun supersededTrials(held: Collection<LotId>): List<LotId> {
        val full = held.filterNot(::isTrial).toSet()
        return held.filter { isTrial(it) && fullOf(it) in full }.sortedWith(compareBy({ it.feature }, { it.scope }))
    }
}
