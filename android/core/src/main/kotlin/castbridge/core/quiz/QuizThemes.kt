package castbridge.core.quiz

import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta

/**
 * « Mes thèmes » (Quiz tab of CastBridge, phone): one line per lot with what the phone holds, what the server last announced,
 * and what CastBridge-TV holds. Pure: built from local data only, so it works with no Internet (the catalog is then the last
 * one received, or null).
 */
object QuizThemes {
    enum class State { NOT_DOWNLOADED, UP_TO_DATE, UPDATE_AVAILABLE, LOCAL_ONLY }

    data class Theme(
        val scope: String, val title: String, val state: State,
        val version: Int?, val bytes: Long, val questions: Int?,
        /** Version announced by the server (null = catalog unknown, e.g. never synchronised or offline). */
        val latestVersion: Int?, val latestBytes: Long?,
        /** null = unknown (TV not reached lately), else whether CastBridge-TV holds a lot of this theme. */
        val onTv: Boolean?, val tvVersion: Int?,
        val ageDays: Long?,
    ) {
        /** The phone holds a version that the TV does not have yet: to be delivered to the TV when it is reachable. */
        val tvBehind: Boolean get() = version != null && onTv != null && (!onTv || (tvVersion ?: 0) < version)
        val freshness: String get() = freshnessLabel(ageDays)
    }

    fun build(phone: List<LotMeta>, catalog: List<LotMeta>?, tv: List<LotMeta>?, installedAt: (String) -> Long?, now: Long, questions: (String) -> Int? = { null }): List<Theme> {
        val have = phone.filter { it.id.feature == QuizLotScopes.FEATURE }.associateBy { it.id.scope }
        val latest = catalog?.filter { it.id.feature == QuizLotScopes.FEATURE }?.associateBy { it.id.scope }
        val onTv = tv?.filter { it.id.feature == QuizLotScopes.FEATURE }?.associateBy { it.id.scope }
        val scopes = LinkedHashSet<String>().apply { addAll(QuizLotScopes.specs.map { it.scope }); latest?.keys?.let { addAll(it) }; addAll(have.keys) }
        return scopes.map { s ->
            val h = have[s]; val l = latest?.get(s)
            val state = when {
                h == null -> State.NOT_DOWNLOADED
                l != null && l.version > h.version -> State.UPDATE_AVAILABLE
                l == null && latest != null -> State.LOCAL_ONLY
                else -> State.UP_TO_DATE
            }
            Theme(s, QuizLotScopes.title(s).takeIf { it != s } ?: h?.title ?: l?.title ?: s, state, h?.version, h?.bytes ?: 0L, h?.let { questions(s) },
                l?.version, l?.bytes, onTv?.let { it.containsKey(s) }, onTv?.get(s)?.version,
                h?.let { installedAt(s) }?.let { Math.max(0L, (now - it) / DAY_MS) })
        }.filter { it.state != State.NOT_DOWNLOADED || it.latestVersion != null || QuizLotScopes.spec(it.scope) != null }
    }

    private const val DAY_MS = 86_400_000L

    fun freshnessLabel(ageDays: Long?): String = when {
        ageDays == null -> "pas encore téléchargé"
        ageDays == 0L -> "mis à jour aujourd'hui"
        ageDays == 1L -> "mis à jour hier"
        ageDays < 60 -> "mis à jour il y a $ageDays jours"
        else -> "mis à jour il y a ${ageDays / 30} mois"
    }

    fun sizeLabel(bytes: Long): String = if (bytes >= 1_000_000) "%.1f Mo".format(bytes / 1e6).replace('.', ',') else "${maxOf(1, (bytes + 500) / 1000)} Ko"

    /** One-line status shown under the theme's name. */
    fun statusLine(t: Theme): String = when (t.state) {
        State.NOT_DOWNLOADED -> "Pas téléchargé" + (t.latestBytes?.let { " · ${sizeLabel(it)}" } ?: "") + " · à télécharger avec Internet"
        State.UPDATE_AVAILABLE -> "Version ${t.version} · mise à jour ${t.latestVersion} disponible · ${t.freshness}"
        else -> "Version ${t.version} · ${sizeLabel(t.bytes)} · ${t.freshness}"
    }

    fun tvLine(t: Theme): String = when {
        t.onTv == null -> "TV : état inconnu (TV non jointe récemment)"
        t.onTv && !t.tvBehind -> "Sur la TV"
        t.onTv -> "Sur la TV (version ${t.tvVersion}), mise à jour à envoyer"
        t.version != null -> "Pas encore sur la TV : sera envoyé quand la TV est à portée"
        else -> "Pas sur la TV"
    }

    /** The lots (in [QuizLotScopes.quizPriority] order) that the phone should fetch next: not downloaded or out of date. */
    fun toDownload(themes: List<Theme>, priority: List<String>): List<String> {
        val order = priority.withIndex().associate { it.value to it.index }
        return themes.filter { it.state == State.NOT_DOWNLOADED && it.latestVersion != null || it.state == State.UPDATE_AVAILABLE }
            .sortedBy { order[it.scope] ?: Int.MAX_VALUE }.map { it.scope }
    }

    /** Last catalog seen by the phone, kept as a small file so « Mes thèmes » is complete with no Internet. */
    object CatalogCache {
        fun write(file: java.io.File, catalog: List<LotMeta>, fetchedAtMs: Long) {
            file.parentFile?.mkdirs()
            val tmp = java.io.File(file.path + ".tmp")
            tmp.writeText(Json.write(linkedMapOf("fetchedAt" to fetchedAtMs, "lots" to catalog.filter { it.id.feature == QuizLotScopes.FEATURE }.map {
                linkedMapOf("scope" to it.id.scope, "version" to it.version, "bytes" to it.bytes, "sha256" to it.sha256, "title" to it.title, "minAppVersion" to it.minAppVersion)
            })), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }

        /** (catalog, fetchedAt) or null when there is none / it is unreadable. */
        fun read(file: java.io.File): Pair<List<LotMeta>, Long>? = runCatching {
            val m = Json.obj(file.readText(Charsets.UTF_8))
            val lots = (m["lots"] as List<*>).map { o ->
                @Suppress("UNCHECKED_CAST") val x = o as Map<String, Any?>
                LotMeta(LotId(QuizLotScopes.FEATURE, x["scope"] as String), (x["version"] as Number).toInt(), (x["bytes"] as Number).toLong(), x["sha256"] as String,
                    x["title"] as String, (x["minAppVersion"] as? Number)?.toInt() ?: 0)
            }
            lots to ((m["fetchedAt"] as? Number)?.toLong() ?: 0L)
        }.getOrNull()
    }
}
