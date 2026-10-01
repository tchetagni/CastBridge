package castbridge.core.lots

import java.io.File

/**
 * Skeleton of a [LotConsumer] for a feature (Apprendre, Quiz). The feature agents implement the three marked functions with their
 * own storage; the framework guarantees that [install] is only called with a file whose size, SHA-256 and signed catalog were
 * verified, and that the TV budget is respected BEFORE the call.
 *
 * Contract of [install]: atomic. Unzip/validate into a temporary place, then swap; on any failure return false and leave the previous
 * version of that lot fully usable. [installed] must list exactly what is usable now, each with its size on disk (that is what
 * the 10 Mo cap of the TV counts).
 */
abstract class FeatureLotConsumer(override val feature: String) : LotConsumer {
    /** Validates [data] (the feature's own content check) and puts it in place atomically. */
    protected abstract fun place(meta: LotMeta, data: File): Boolean
    protected abstract fun unplace(id: LotId)
    protected abstract fun list(): List<LotMeta>

    final override fun install(meta: LotMeta, data: File): Boolean = meta.id.feature == feature && runCatching { place(meta, data) }.getOrDefault(false)
    final override fun remove(id: LotId) { if (id.feature == feature) unplace(id) }
    final override fun installed(): List<LotMeta> = runCatching { list() }.getOrDefault(emptyList()).filter { it.id.feature == feature }
}

/**
 * COMPATIBILITY SHIM (until Apprendre and Quiz migrate to lots): today's data is bundled in the APK (Apprendre packs in the core
 * jar, quiz packs handled by QuizPacks/QuizPackRelay). This consumer reports NO lot (the bundled starter data is counted by the
 * budget through the starter size, [castbridge.core.lots.StarterBudget]) and accepts nothing: the current behaviour is untouched.
 */
class StarterOnlyConsumer(override val feature: String) : LotConsumer {
    override fun install(meta: LotMeta, data: File) = false
    override fun remove(id: LotId) {}
    override fun installed(): List<LotMeta> = emptyList()
}

/** Registry used by the apps: one consumer per feature. */
class LotConsumers(consumers: List<LotConsumer>) {
    val byFeature: Map<String, LotConsumer> = consumers.associateBy { it.feature }
    fun all() = byFeature.values.toList()
}
