package castbridge.core.library.agent

/** One line of advice, discreet by design (a banner, never a pop-up). */
data class Insight(val id: String, val severity: Int, val text: String, val bytes: Long = 0) {
    companion object { const val INFO = 0; const val NOTICE = 1; const val WARNING = 2 }
}

/** Explains a proposal in plain French (or English), for the "why" line of each change. */
object Reasons {
    fun describe(p: Parsed, lang: String): String {
        val fr = lang != "en"
        val base = when (p.kind) {
            Kind.SERIES -> buildString {
                append(if (fr) "Série « ${p.title} »" else "Series \"${p.title}\"")
                p.season?.let { append(if (fr) ", saison $it" else ", season $it") }
                p.episode?.let { append(if (fr) ", épisode $it" else ", episode $it") }
            }
            Kind.MOVIE -> (if (fr) "Film « ${p.title} »" else "Movie \"${p.title}\"") + (p.year?.let { " ($it)" } ?: "")
            Kind.MUSIC -> if (fr) "Musique" + (p.artist?.let { " de $it" } ?: "") else "Music" + (p.artist?.let { " by $it" } ?: "")
            Kind.CLIP -> if (fr) "Clip" + (p.artist?.let { " de $it" } ?: "") else "Music video" + (p.artist?.let { " by $it" } ?: "")
            Kind.COURSE -> (if (fr) "Cours" else "Course") + (p.subject?.let { " : $it" } ?: "")
            Kind.PERSONAL -> (if (fr) "Média personnel" else "Personal media") + (p.origin?.let { " ($it)" } ?: "") + (p.date?.let { " du $it" } ?: "")
            Kind.PHOTO -> (if (fr) "Photo" else "Photo") + (p.date?.let { " du $it" } ?: "")
            Kind.DOCUMENT -> if (fr) "Document" else "Document"
            Kind.APP -> if (fr) "Application" else "App"
            Kind.ARCHIVE -> "Archive"
            Kind.UNKNOWN -> if (fr) "Nom nettoyé" else "Cleaned name"
        }
        return if (p.hadJunk && p.kind != Kind.UNKNOWN) base + (if (fr) " · site, qualité ou séparateurs retirés" else " · site, quality or separators removed") else base
    }
}

/** Builds the list of proposed changes from what was understood. Pure and deterministic. */
class Planner(private val ctx: AgentContext, private val learned: LearnedRules? = null) {
    private val labels = Labels(ctx.uiLang)

    private fun ns(origin: Origin, volume: String, folder: String) = if (origin == Origin.TV) "tv" else "$volume|${folder.lowercase()}"
    private fun dateOf(ms: Long): String? = if (ms <= 0) null else java.time.Instant.ofEpochMilli(ms).atZone(ctx.zone).toLocalDate().toString()

    private fun splitExt(n: String): Pair<String, String> {
        val dot = n.lastIndexOf('.')
        return if (dot > 0 && n.length - dot <= 6) n.substring(0, dot) to n.substring(dot) else n to ""
    }

    /** A name free in its folder: first a meaningful difference (language, quality), then " (2)", " (3)"… Never an overwrite. */
    fun uniqueName(candidate: String, p: Parsed, taken: MutableSet<String>, nsKey: String): String {
        fun k(n: String) = nsKey + "|" + n.lowercase()
        if (k(candidate) !in taken) return candidate
        val (base, ext) = splitExt(candidate)
        val options = ArrayList<String>()
        p.audio?.let { if (!candidate.contains("[${it.tag}]")) options += "$base [${it.tag}]$ext" }
        p.resolution?.let { options += "$base [${it}p]$ext" }
        for (o in options) if (k(o) !in taken) return o
        var i = 2
        while (true) { val o = "$base ($i)$ext"; if (k(o) !in taken) return o; i++ }
    }

    fun plan(snapshot: LibrarySnapshot, items: List<ItemInfo>, groups: List<DupGroup>, skipped: List<Skipped>): Plan {
        val changes = ArrayList<Change>()
        val notes = ArrayList<String>()
        val trashed = HashSet<String>()

        // ---- duplicates first: a file going to the trash is not renamed
        for (g in groups) for (x in g.extras) {
            if (!trashed.add(x.key)) continue
            val why = when (g.kind) { DupKind.VERSION -> TrashWhy.LOWER_QUALITY; else -> TrashWhy.DUPLICATE }
            val conf = when (g.kind) { DupKind.EXACT -> 0.95; DupKind.PROBABLE -> 0.75; DupKind.VERSION -> 0.7 }
            changes += Change("trash:${x.key}", ChangeType.TRASH, x, kindOf(items, x), why = why, keep = g.keep, confidence = conf, checked = false,
                reason = g.reason + " · garde « ${g.keep.name} »" + (if (g.keep.volumeId != x.volumeId && snapshot.volumes.size > 1) " (sur ${volLabel(snapshot, g.keep.volumeId)})" else ""))
        }

        // ---- names and folders
        val byKey = items.associateBy { it.file.key }
        val live = items.filter { it.file.key !in trashed }
        val target = LinkedHashMap<String, String>()                    // file key -> proposed name
        val taken = HashSet<String>()
        val renamedKeys = live.filter { it.proposal.name != it.file.name && !it.proposal.name.equals(it.file.name, true) }.map { it.file.key }.toSet()
        for (i in items) if (i.file.key !in renamedKeys) taken += ns(i.file.origin, i.file.volumeId, i.file.folder) + "|" + i.file.name.lowercase()

        for (i in live.sortedWith(compareBy({ it.file.origin }, { it.file.folder }, { it.file.name }))) {
            val f = i.file; val p = i.parsed
            val wantName = i.proposal.name
            val wantFolder = if (ctx.folders && f.origin == Origin.PHONE) i.proposal.folder else null
            val nameChanges = wantName != f.name && !wantName.equals(f.name, ignoreCase = true)
            val folderChanges = wantFolder != null && !wantFolder.equals(f.folder, ignoreCase = true)
            // never rename what we do not understand, unless a download site left its mark; loose unknown files only go to "À trier"
            val nameWanted = nameChanges && (if (p.kind == Kind.UNKNOWN) p.hadJunk else p.confidence >= 0.5)
            val folderWanted = folderChanges && (if (p.kind == Kind.UNKNOWN) f.folder.isEmpty() else p.confidence >= 0.5)
            if (!nameWanted && !folderWanted) continue
            var finalName = f.name
            if (nameWanted || folderWanted) {
                val nsKey = ns(f.origin, f.volumeId, if (folderWanted) wantFolder!! else f.folder)
                finalName = uniqueName(if (nameWanted) wantName else f.name, p, taken, nsKey)
                taken += nsKey + "|" + finalName.lowercase()
            }
            val doName = finalName != f.name
            val doFolder = folderWanted
            if (!doName && !doFolder) continue
            val conf = if (p.kind == Kind.UNKNOWN) 0.55 else p.confidence
            val src = when {
                i.source == Source.AI -> Source.AI
                learned != null && learned.aliasFor(p.titleKey) != null -> Source.LEARNED
                else -> Source.RULES
            }
            val pair = if (p.media == Media.SUBTITLE || p.identity != null && (p.media == Media.VIDEO)) p.identity else null
            changes += Change("rename:${f.key}", ChangeType.RENAME, f, p.kind, toName = if (doName) finalName else null, toFolder = if (doFolder) wantFolder else null,
                reason = Reasons.describe(p, ctx.uiLang) + (i.proposal.note.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                confidence = conf, source = src, checked = conf >= 0.85 && src != Source.AI, titleKey = p.titleKey, pairKey = pair)
        }

        // subtitles follow their video: they start ticked only if the video is
        val videoChecked = changes.filter { it.pairKey != null && it.file.ext !in SUB_EXT }.associate { it.pairKey to it.checked }
        for ((idx, c) in changes.withIndex()) if (c.pairKey != null && c.file.ext in SUB_EXT && videoChecked[c.pairKey] != null) changes[idx] = c.copy(checked = videoChecked[c.pairKey]!!)

        // ---- space: old watched files, then moves to the USB key
        spacePlan(snapshot, items, trashed, changes, notes)
        return Plan(changes.sortedWith(compareBy({ it.type }, { it.file.name })), skipped, notes)
    }

    private val SUB_EXT = setOf("srt", "ass", "ssa", "vtt", "sub", "idx")

    private fun kindOf(items: List<ItemInfo>, f: FileRef) = items.firstOrNull { it.file.key == f.key }?.parsed?.kind ?: Kind.UNKNOWN
    private fun volLabel(s: LibrarySnapshot, id: String) = s.volumes.firstOrNull { it.id == id }?.label ?: id

    private fun spacePlan(snapshot: LibrarySnapshot, items: List<ItemInfo>, trashed: Set<String>, changes: MutableList<Change>, notes: MutableList<String>) {
        if (snapshot.origin != Origin.TV) return
        val vols = snapshot.volumes.associateBy { it.id }
        val freeNow = HashMap<String, Long>().also { m -> snapshot.volumes.forEach { m[it.id] = it.free } }
        fun pressure(v: VolumeInfo) = v.free in 0 until ctx.lowSpaceBytes || v.usedRatio >= 0.9

        // (a) already watched a long time ago, only on a volume under pressure
        val oldMs = ctx.watchedOldDays * 86_400_000L
        val keepKinds = setOf(Kind.SERIES, Kind.MOVIE, Kind.CLIP)
        for (v in snapshot.volumes.filter { pressure(it) && it.writable }) {
            val cands = items.filter { it.file.volumeId == v.id && it.file.key !in trashed && it.file.watched && it.file.playedAtMs > 0 && ctx.nowMs - it.file.playedAtMs >= oldMs &&
                it.parsed.kind in keepKinds && !it.file.playing }.sortedByDescending { it.file.size }
            var free = freeNow[v.id] ?: 0
            for (c in cands) {
                if (free >= ctx.lowSpaceBytes && v.usedRatio < 0.9) break
                val months = ((ctx.nowMs - c.file.playedAtMs) / (30L * 86_400_000L)).coerceAtLeast(1)
                changes += Change("trash:${c.file.key}", ChangeType.TRASH, c.file, c.parsed.kind, why = TrashWhy.WATCHED_OLD, confidence = 0.6, checked = false,
                    reason = "Déjà vu il y a ${months} mois · libère ${Text.size(c.file.size)} sur ${v.label}")
                free += c.file.size
            }
        }

        // (b) the internal memory is nearly full and a USB key has room: move the biggest files, USB first, keep >= 1 GB free there
        val dests = snapshot.volumes.filter { it.removable && it.writable && it.free > ctx.minFreeAfterBytes }.sortedByDescending { it.free }
        for (src in snapshot.volumes.filter { it.kind == "internal" && it.free in 0 until ctx.lowSpaceBytes }) {
            val cands = items.filter { it.file.volumeId == src.id && it.file.key !in trashed && !it.file.playing && it.parsed.media in setOf(Media.VIDEO, Media.AUDIO) &&
                changes.none { c -> c.file.key == it.file.key && c.type == ChangeType.TRASH } }.sortedByDescending { it.file.size }
            var srcFree = freeNow[src.id] ?: 0
            if (dests.isEmpty()) { notes += "La mémoire interne n'a plus que ${Text.size(src.free)} libre et aucune clé USB n'est branchée : libérez de la place ou branchez une clé."; continue }
            for (c in cands) {
                if (srcFree >= ctx.lowSpaceBytes) break
                val dest = dests.firstOrNull { d -> (freeNow[d.id] ?: 0) - c.file.size >= ctx.minFreeAfterBytes && c.file.size <= d.maxFileBytes } ?: continue
                freeNow[dest.id] = (freeNow[dest.id] ?: 0) - c.file.size
                srcFree += c.file.size
                val left = freeNow[dest.id] ?: 0
                changes += Change("move:${c.file.key}", ChangeType.MOVE, c.file, c.parsed.kind, toVolume = dest.id, confidence = 0.8, checked = false,
                    reason = "La mémoire interne n'a plus que ${Text.size(src.free)} libre : déplacer vers « ${dest.label} » (il y restera ${Text.size(left)})")
            }
        }
        val fat = snapshot.volumes.filter { it.removable && it.maxFileBytes != Long.MAX_VALUE && it.maxFileBytes > 0 }
        if (fat.isNotEmpty()) notes += "« ${fat.first().label} » est en ${fat.first().fs.ifEmpty { "FAT32" }} : les fichiers de plus de 4 Go n'y tiennent pas."
    }
}
