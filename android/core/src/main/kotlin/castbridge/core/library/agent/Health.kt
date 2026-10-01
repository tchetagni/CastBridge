package castbridge.core.library.agent

/**
 * "What state is this library in?" (docs/LIBRARY-AGENT.md § 4 bis). Pure and deterministic, computed locally from names, sizes, dates and the
 * play marks the TV already keeps. It never changes anything: it produces [Finding]s (advice, with numbers) and, for the ones that are actions,
 * ready-made TRASH [Change]s that still go through the plan, the user's tick and the [Executor] like any other.
 */
enum class FindingType { BROKEN_FILE, EXACT_DUPLICATE, QUALITY_DUPLICATE, OLD_WATCHED, MISSING_EPISODES, MIXED_LOCATIONS, MIXED_NUMBERING, SPACE_PRESSURE }

data class Finding(
    val id: String,
    val type: FindingType,
    /** Insight.INFO / NOTICE / WARNING. */
    val severity: Int,
    val title: String,
    val detail: String = "",
    /** Bytes that could be freed by acting on this finding (0 for advice that frees nothing). Never counted twice in [HealthReport.recoverableBytes]. */
    val bytes: Long = 0,
    val fileKeys: List<String> = emptyList(),
    /** Volume most concerned (TV), "" = none or several. */
    val volumeId: String = "",
    /** Ready-made actions (TRASH, never ticked); empty for pure advice such as "episode 3 is missing". */
    val changes: List<Change> = emptyList(),
    /** Higher = show first. Depends on free space, recent viewing and the size at stake. */
    val priority: Double = 0.0,
)

/** A volume that is short of space and what recovering the safe findings would change. */
data class VolumePressure(val volumeId: String, val label: String, val free: Long, val total: Long, val recoverable: Long) {
    val freeAfter: Long get() = free + recoverable
}

data class HealthReport(val findings: List<Finding>, val recoverableBytes: Long, val recoverableByVolume: Map<String, Long>, val pressure: List<VolumePressure>) {
    /** Findings in the order they should be shown: what matters most, in this library and for this user, first. */
    val ranked: List<Finding> get() = findings.sortedWith(compareByDescending<Finding> { it.priority }.thenBy { it.id })
    fun of(type: FindingType) = findings.filter { it.type == type }

    /** "4,2 Go récupérables : 3 doublons, 1 fichier incomplet…" (null when there is nothing worth saying). */
    fun headline(lang: String = "fr"): String? {
        if (recoverableBytes < Health.MIN_HEADLINE_BYTES) return null
        val fr = lang != "en"
        val parts = ArrayList<String>()
        val dups = of(FindingType.EXACT_DUPLICATE).sumOf { it.fileKeys.size }
        val ver = of(FindingType.QUALITY_DUPLICATE).sumOf { it.fileKeys.size }
        val broken = of(FindingType.BROKEN_FILE).count { it.bytes > 0 }
        val old = of(FindingType.OLD_WATCHED).sumOf { it.fileKeys.size }
        if (dups > 0) parts += if (fr) (if (dups == 1) "1 doublon" else "$dups doublons") else (if (dups == 1) "1 duplicate" else "$dups duplicates")
        if (ver > 0) parts += if (fr) "$ver en double qualité" else "$ver in two qualities"
        if (broken > 0) parts += if (fr) (if (broken == 1) "1 fichier incomplet" else "$broken fichiers incomplets") else (if (broken == 1) "1 incomplete file" else "$broken incomplete files")
        if (old > 0) parts += if (fr) "$old déjà vu(s) depuis longtemps" else "$old watched long ago"
        return Text.size(recoverableBytes) + (if (fr) " récupérables" else " can be recovered") + if (parts.isEmpty()) "" else " (" + parts.joinToString(", ") + ")"
    }

    companion object { val EMPTY = HealthReport(emptyList(), 0, emptyMap(), emptyList()) }
}

object Health {
    const val MIN_HEADLINE_BYTES = 100L * 1024 * 1024
    /** A partial download younger than that may still be running: it is not even mentioned. */
    const val ACTIVE_DOWNLOAD_MS = 3L * 86_400_000
    /** "Idle" threshold after which a partial file counts as recoverable space. */
    private val PARTIAL_EXT = Regex("(?i)\\.(part|crdownload|tmp|download|partial|opdownload|!ut|!qb|temp|aria2)$")
    private const val MIN_EPISODES_FOR_GAPS = 3
    /** Below that bit rate (bits per second) a video of known duration cannot hold what its length claims. */
    private const val MIN_VIDEO_BPS = 80_000L
    private const val SIBLING_RATIO = 0.2

    private fun fr(ctx: AgentContext) = ctx.uiLang != "en"

    fun assess(snapshot: LibrarySnapshot, items: List<ItemInfo>, groups: List<DupGroup>, plan: Plan, ctx: AgentContext, habits: Habits = Habits.NONE): HealthReport {
        val l = fr(ctx)
        val findings = ArrayList<Finding>()
        val byKey = items.associateBy { it.file.key }
        val volLabel = snapshot.volumes.associate { it.id to it.label }
        val planTrash = plan.trash.associateBy { it.file.key }
        val recent = recentTitles(items, ctx.nowMs)
        val pressureVols = snapshot.volumes.filter { it.total > 0 && it.free >= 0 && (it.free < ctx.lowSpaceBytes || it.usedRatio >= 0.9) }.map { it.id }.toSet()
        fun pressureBoost(vol: String, bytes: Long) = if (vol in pressureVols) 1000.0 + bytes / 1_048_576.0 / 10 else 0.0
        fun mb(b: Long) = b / 1_048_576.0

        // ---- duplicates (exact / probable / quality versions): the plan already holds the TRASH changes
        for ((n, g) in groups.withIndex()) {
            val extras = g.extras
            val vol = extras.map { it.volumeId }.distinct().singleOrNull().orEmpty()
            val changes = extras.mapNotNull { planTrash[it.key] }
            val exact = g.kind != DupKind.VERSION
            val keepName = g.keep.name
            findings += Finding(
                id = "dup:$n", type = if (exact) FindingType.EXACT_DUPLICATE else FindingType.QUALITY_DUPLICATE, severity = Insight.NOTICE,
                title = if (exact) (if (l) "${extras.size} copie(s) identique(s) de « $keepName »" else "${extras.size} identical copy(ies) of \"$keepName\"")
                else (if (l) "« ${g.keep.name} » existe en plusieurs qualités" else "\"${g.keep.name}\" exists in several qualities"),
                detail = g.reason + (if (l) " · ${Text.size(g.bytes)} récupérables en gardant « $keepName »" else " · ${Text.size(g.bytes)} can be recovered by keeping \"$keepName\""),
                bytes = g.bytes, fileKeys = extras.map { it.key }, volumeId = vol, changes = changes,
                priority = (if (exact) 400.0 else 350.0) + minOf(mb(g.bytes) / 10, 200.0) + pressureBoost(vol, g.bytes))
        }

        // ---- broken, empty, partial
        val names = snapshot.files.groupBy { it.volumeId + "|" + it.folder }.mapValues { e -> e.value.map { it.name.lowercase() }.toSet() }
        val seenBroken = HashSet<String>()
        for (f in items.map { it.file }) {
            if (f.playing) continue
            val p = byKey[f.key]?.parsed
            val partialExt = PARTIAL_EXT.containsMatchIn(f.name)
            val sibling = names[f.volumeId + "|" + f.folder].orEmpty()
            val pairedControl = (f.name + ".aria2").lowercase() in sibling
            val age = if (f.mtime > 0) ctx.nowMs - f.mtime else -1L
            val idle = age >= ACTIVE_DOWNLOAD_MS
            val reason: String? = when {
                f.size == 0L ->
                    if (l) "Fichier vide (0 octet)" else "Empty file (0 bytes)"
                partialExt || pairedControl -> if (age in 0 until ACTIVE_DOWNLOAD_MS) null else
                    if (l) "Téléchargement interrompu" + (if (idle) " depuis ${age / 86_400_000} jours" else "") + " : à reprendre ou à supprimer" else "Interrupted download: resume or delete"
                p?.media == Media.VIDEO && f.durationMs >= 60_000 && f.size > 0 && f.size * 8_000 / f.durationMs < MIN_VIDEO_BPS ->
                    if (l) "Trop petit pour sa durée (${Text.size(f.size)} pour ${f.durationMs / 60_000} min) : probablement tronqué" else "Too small for its length (${Text.size(f.size)} for ${f.durationMs / 60_000} min): probably truncated"
                else -> null
            }
            if (reason != null && seenBroken.add(f.key)) {
                val counted = f.size == 0L || idle
                val ch = if (f.size == 0L || partialExt || pairedControl) planTrashChange(f, if (f.size == 0L) TrashWhy.EMPTY else TrashWhy.PARTIAL, reason, p?.kind ?: Kind.UNKNOWN) else null
                findings += Finding("broken:${f.key}", FindingType.BROKEN_FILE, Insight.NOTICE, "« ${f.name} »", reason, bytes = if (counted) f.size else 0, fileKeys = listOf(f.key), volumeId = f.volumeId,
                    changes = listOfNotNull(ch), priority = 500.0 + pressureBoost(f.volumeId, f.size))
            }
        }
        // a series episode far smaller than its siblings is probably cut short (advice only: it may be a short episode)
        for ((_, eps) in episodeGroups(items)) {
            val sizes = eps.map { it.file.size }.filter { it > 0 }.sorted()
            if (sizes.size < 4) continue
            val median = sizes[sizes.size / 2]
            if (median < 50L * 1024 * 1024) continue
            for (e in eps) if (e.file.size in 1 until (median * SIBLING_RATIO).toLong() && seenBroken.add(e.file.key)) {
                findings += Finding("small:${e.file.key}", FindingType.BROKEN_FILE, Insight.INFO, "« ${e.file.name} »",
                    if (l) "Beaucoup plus petit que les autres épisodes (${Text.size(e.file.size)} contre ${Text.size(median)} en général) : peut-être incomplet" else "Much smaller than the other episodes (${Text.size(e.file.size)} vs ${Text.size(median)} usually): maybe incomplete",
                    fileKeys = listOf(e.file.key), volumeId = e.file.volumeId, priority = 450.0)
            }
        }

        // ---- watched a long time ago (the plan decided: only on a volume under pressure)
        for (c in plan.trash.filter { it.why == TrashWhy.WATCHED_OLD }) {
            findings += Finding("old:${c.file.key}", FindingType.OLD_WATCHED, Insight.INFO, "« ${c.file.name} »", c.reason, bytes = c.bytes, fileKeys = listOf(c.file.key),
                volumeId = c.file.volumeId, changes = listOf(c), priority = 200.0 + pressureBoost(c.file.volumeId, c.bytes))
        }

        // ---- incomplete series: holes in the episode numbers
        for ((key, eps) in episodeGroups(items)) {
            val present = HashSet<Int>()
            for (e in eps) for (n in (e.parsed.episode!!)..(e.parsed.episodeEnd ?: e.parsed.episode)) present += n
            if (present.size < MIN_EPISODES_FOR_GAPS) continue
            val lo = present.min(); val hi = present.max()
            if (hi > 400) continue
            val start = if (lo <= 2) 1 else lo
            val missing = (start..hi).filter { it !in present }
            if (missing.isEmpty() || missing.size > maxOf(10, present.size)) continue
            val any = eps.first().parsed
            val season = any.season
            val label = any.title + (if (season != null) (if (l) " saison $season" else " season $season") else "")
            val shown = if (missing.size > 8) missing.take(8).joinToString(", ") + "…" else missing.joinToString(", ")
            val isRecent = Text.key(any.title) in recent
            findings += Finding("missing:$key", FindingType.MISSING_EPISODES, if (isRecent) Insight.NOTICE else Insight.INFO,
                if (l) "$label : " + (if (missing.size == 1) "il manque l'épisode $shown" else "il manque les épisodes $shown")
                else "$label: " + (if (missing.size == 1) "episode $shown is missing" else "episodes $shown are missing"),
                if (l) "Vous avez ${present.size} épisode(s), du ${lo} au ${hi}." else "You have ${present.size} episode(s), from $lo to $hi.",
                fileKeys = eps.map { it.file.key }, priority = 300.0 + (if (isRecent) 400.0 else if (habits.favoriteKind() == Kind.SERIES) 100.0 else 0.0) + minOf(present.size.toDouble(), 30.0) / 10)
        }

        // ---- mixed seasons: the same season on several volumes, or episodes numbered with and without a season
        for (series in items.filter { it.parsed.kind == Kind.SERIES && it.parsed.media == Media.VIDEO && it.parsed.confidence >= 0.7 && it.parsed.title.isNotBlank() }.groupBy { it.parsed.titleKey }.values) {
            val title = series.first().parsed.title
            val seasons = series.groupBy { it.parsed.season }
            for ((s, list) in seasons) {
                if (s == null) continue
                val vols = list.map { it.file.volumeId }.filter { it.isNotEmpty() }.distinct()
                if (vols.size > 1 && list.size >= 2)
                    findings += Finding("split:${Text.key(title)}:$s", FindingType.MIXED_LOCATIONS, Insight.INFO,
                        if (l) "$title saison $s est répartie sur ${vols.size} supports" else "$title season $s is spread over ${vols.size} drives",
                        vols.joinToString(", ") { volLabel[it] ?: it }, fileKeys = list.map { it.file.key }, priority = 150.0)
            }
            if (seasons.keys.contains(null) && seasons.keys.any { it != null } && series.size >= 3 && (seasons[null]?.size ?: 0) < series.size)
                findings += Finding("mixnum:${Text.key(title)}", FindingType.MIXED_NUMBERING, Insight.INFO,
                    if (l) "$title : certains épisodes n'ont pas de numéro de saison" else "$title: some episodes have no season number",
                    if (l) "Ils sont numérotés « E12 » alors que d'autres sont « S01E12 »." else "They are numbered \"E12\" while others are \"S01E12\".",
                    fileKeys = series.filter { it.parsed.season == null }.map { it.file.key }, priority = 140.0)
        }

        // ---- space: what recovering the safe findings changes, per volume
        val counted = HashSet<String>()
        var total = 0L
        val perVol = HashMap<String, Long>()
        for (f in findings.filter { it.bytes > 0 && it.type != FindingType.SPACE_PRESSURE }) {
            // bytes of one finding are the bytes of its fileKeys that were not already counted by another finding
            val fresh = f.fileKeys.filter { counted.add(it) }
            val b = if (fresh.size == f.fileKeys.size) f.bytes else fresh.sumOf { k -> byKey[k]?.file?.size ?: 0L }
            total += b
            for (k in fresh) perVol.merge(byKey[k]?.file?.volumeId ?: "", byKey[k]?.file?.size ?: 0L, Long::plus)
        }
        val pressure = snapshot.volumes.filter { it.id in pressureVols }.map { VolumePressure(it.id, it.label, it.free, it.total, perVol[it.id] ?: 0L) }
        for (p in pressure) {
            val enough = p.freeAfter >= ctx.lowSpaceBytes
            findings += Finding("space:${p.volumeId}", FindingType.SPACE_PRESSURE, Insight.WARNING,
                if (l) "Il reste ${Text.size(p.free)} sur « ${p.label} »" else "Only ${Text.size(p.free)} left on \"${p.label}\"",
                if (p.recoverable > 0) (if (l) "${Text.size(p.recoverable)} récupérables ici" + (if (enough) " : de quoi repasser au-dessus de ${Text.size(ctx.lowSpaceBytes)}" else " : pas assez, il faudra aussi déplacer ou supprimer autre chose")
                    else "${Text.size(p.recoverable)} can be recovered here" + (if (enough) ": enough to get back above ${Text.size(ctx.lowSpaceBytes)}" else ": not enough, something else must be moved or deleted"))
                else (if (l) "Rien de sûr à récupérer ici : déplacez des fichiers vers une clé USB." else "Nothing safe to recover here: move files to a USB key."),
                bytes = 0, volumeId = p.volumeId, priority = 2000.0)
        }
        return HealthReport(findings, total, perVol.filterKeys { it.isNotEmpty() }, pressure)
    }

    private fun planTrashChange(f: FileRef, why: TrashWhy, reason: String, kind: Kind) =
        Change("trash:${f.key}", ChangeType.TRASH, f, kind, why = why, reason = reason, confidence = 0.7, checked = false)

    /** Series videos with a season/episode, grouped by (title, season); the key is stable. */
    private fun episodeGroups(items: List<ItemInfo>): Map<String, List<ItemInfo>> =
        items.filter { it.parsed.kind == Kind.SERIES && it.parsed.media == Media.VIDEO && it.parsed.episode != null && it.parsed.confidence >= 0.7 && it.parsed.title.isNotBlank() }
            .groupBy { it.parsed.titleKey + "|" + (it.parsed.season ?: "") }

    /** Titles watched in the last 30 days: "you are watching this series" raises the priority of its missing episodes. */
    private fun recentTitles(items: List<ItemInfo>, now: Long): Set<String> =
        items.filter { it.file.playedAtMs > 0 && now - it.file.playedAtMs <= 30L * 86_400_000 && it.parsed.title.isNotBlank() }.map { it.parsed.titleKey }.toSet()
}
