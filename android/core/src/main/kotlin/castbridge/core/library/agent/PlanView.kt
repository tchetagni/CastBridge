package castbridge.core.library.agent

/**
 * How a plan of thousands of changes is SHOWN: filters, collapsible groups, pages, and the "pourquoi cette proposition ?" text.
 * Pure Kotlin (the screen only draws what this returns), computed off the main thread, tested on the JVM.
 */
enum class PlanFilter(val label: String) {
    ALL("Tout"), SERIES("Séries"), MOVIES("Films"), DUPLICATES("Doublons"), BIG("Gros fichiers")
}

enum class GroupKind { SERIES, MOVIES, OTHER, DUPLICATES, OLD, MOVE }

/** A collapsible block of the plan: one series, "Films", "Doublons"… */
data class PlanGroup(val id: String, val title: String, val kind: GroupKind, val changes: List<Change>) {
    val bytes: Long get() = changes.sumOf { it.bytes }
    /** The first [page] changes (the screen shows a page at a time, "Afficher la suite" adds another). */
    fun page(n: Int, size: Int = PlanView.PAGE): List<Change> = changes.take(size * n)
}

object PlanView {
    const val PAGE = 30
    /** "Gros fichier": 1 Go or more (a film in HD, a season pack). */
    const val BIG_BYTES = 1L shl 30

    fun isDuplicate(c: Change) = c.type == ChangeType.TRASH && (c.why == TrashWhy.DUPLICATE || c.why == TrashWhy.LOWER_QUALITY)

    fun matches(c: Change, f: PlanFilter): Boolean = when (f) {
        PlanFilter.ALL -> true
        PlanFilter.SERIES -> c.kind == Kind.SERIES
        PlanFilter.MOVIES -> c.kind == Kind.MOVIE
        PlanFilter.DUPLICATES -> isDuplicate(c)
        PlanFilter.BIG -> c.file.size >= BIG_BYTES
    }

    fun counts(plan: Plan): Map<PlanFilter, Int> = PlanFilter.values().associateWith { f -> if (f == PlanFilter.ALL) plan.changes.size else plan.changes.count { matches(it, f) } }

    private fun kindLabel(k: Kind) = when (k) {
        Kind.MOVIE -> "Films"; Kind.MUSIC -> "Musique"; Kind.CLIP -> "Clips"; Kind.COURSE -> "Cours"; Kind.PERSONAL -> "Vidéos personnelles"
        Kind.PHOTO -> "Photos"; Kind.DOCUMENT -> "Documents"; Kind.APP -> "Applications"; Kind.ARCHIVE -> "Archives"; Kind.SERIES -> "Séries"; Kind.UNKNOWN -> "Autres fichiers"
    }

    /**
     * Groups the changes that pass [filter]: one block per series (all its episodes together), one per other kind of file, then the
     * doublons, the "déjà vus depuis longtemps" and the moves to the USB key. Series and kinds are ordered by size of the block, so that the
     * biggest cleanup comes first; the order inside a block is the file name (episodes in order).
     */
    fun groups(plan: Plan, filter: PlanFilter = PlanFilter.ALL): List<PlanGroup> {
        val list = plan.changes.filter { matches(it, filter) }
        val out = ArrayList<PlanGroup>()
        val renames = list.filter { it.type == ChangeType.RENAME }
        val series = renames.filter { it.kind == Kind.SERIES }.groupBy { it.titleKey.ifBlank { "?" + it.file.name.lowercase() } }
        for ((key, cs) in series.entries.sortedByDescending { it.value.size }) {
            val title = cs.firstNotNullOfOrNull { NameParser.parse(it.file.name, it.file.folder).title.takeIf { t -> t.isNotBlank() } } ?: "Série"
            out += PlanGroup("s:$key", title, GroupKind.SERIES, cs.sortedBy { it.file.name })
        }
        val others = renames.filter { it.kind != Kind.SERIES }.groupBy { it.kind }
        for ((k, cs) in others.entries.sortedByDescending { it.value.size })
            out += PlanGroup("k:$k", kindLabel(k), if (k == Kind.MOVIE) GroupKind.MOVIES else GroupKind.OTHER, cs.sortedBy { it.file.name })
        val dups = list.filter { isDuplicate(it) }
        if (dups.isNotEmpty()) out += PlanGroup("dups", "Doublons à mettre dans la corbeille", GroupKind.DUPLICATES, dups.sortedBy { it.file.name })
        val old = list.filter { it.type == ChangeType.TRASH && it.why == TrashWhy.WATCHED_OLD }
        if (old.isNotEmpty()) out += PlanGroup("old", "Déjà vus depuis longtemps", GroupKind.OLD, old.sortedByDescending { it.bytes })
        val mv = list.filter { it.type == ChangeType.MOVE }
        if (mv.isNotEmpty()) out += PlanGroup("move", "À déplacer vers la clé USB", GroupKind.MOVE, mv.sortedByDescending { it.bytes })
        return out
    }

    /** Which ids of [group] are ticked, for the "2 sur 14" shown in its header. */
    fun tickedIn(group: PlanGroup, selected: Set<String>) = group.changes.count { it.id in selected }

    /** Group-level "tout cocher": never ticks a deletion (those are ticked one by one, see [Plan.allSafe]). */
    fun safeIds(group: PlanGroup): Set<String> = group.changes.filter { it.type != ChangeType.TRASH }.map { it.id }.toSet()
}

/** The "pourquoi cette proposition ?" of one change, in plain French, with what was removed from the name and how sure the assistant is. */
data class Why(val headline: String, val lines: List<String>, val sure: Sureness)

enum class Sureness(val label: String) { HIGH("Sûr"), MEDIUM("Assez sûr"), LOW("Peu sûr") }

object Explain {
    private val SEP = Regex("[\\s_\\-\\[\\]()+,.]+")
    private val SURROUND = Regex("(?<=\\d)\\.(?=\\d(?!\\d))")           // "DD5.1", "7.1" stay one word

    private fun tokens(s: String): List<String> = SEP.split(SURROUND.replace(s, "\u0001")).map { it.replace("\u0001", ".") }

    private fun stem(n: String): String { val d = n.lastIndexOf('.'); return if (d > 0 && n.length - d <= 6) n.substring(0, d) else n }

    /** Words of the old name that are not in the new one (site, quality, codec, group…): "ce qui a été retiré". */
    fun removed(before: String, after: String, max: Int = 8): List<String> {
        val keep = tokens(stem(after)).map { it.lowercase() }.toSet()
        val seen = LinkedHashSet<String>()
        for (t in tokens(stem(before))) if (t.isNotBlank() && t.lowercase() !in keep) seen += t
        return seen.toList().take(max)
    }

    fun sureness(c: Change) = when { c.confidence >= 0.85 -> Sureness.HIGH; c.confidence >= 0.6 -> Sureness.MEDIUM; else -> Sureness.LOW }

    fun of(c: Change): Why {
        val lines = ArrayList<String>()
        val s = sureness(c)
        when (c.type) {
            ChangeType.RENAME -> {
                val to = c.toName
                if (to != null) {
                    val gone = removed(c.file.name, to)
                    if (gone.isNotEmpty()) lines += "Retiré du nom : " + gone.joinToString(", ") + "."
                }
                c.toFolder?.takeIf { it.isNotEmpty() }?.let { lines += "Rangé dans « $it » parce que c'est " + kindWords(c.kind) + "." }
                if (c.pairKey != null && c.file.ext in setOf("srt", "ass", "ssa", "vtt", "sub", "idx")) lines += "Le sous-titre suit sa vidéo : il reçoit le même nom."
            }
            ChangeType.TRASH -> {
                c.keep?.let { lines += "Vous gardez « ${it.name} » : le fichier proposé n'est pas le dernier exemplaire." }
                lines += "Rien n'est effacé : le fichier va dans la « Corbeille CastBridge » et reste récupérable 30 jours."
                lines += "Une mise à la corbeille n'est jamais cochée d'avance : il faut la cocher puis la confirmer."
            }
            ChangeType.MOVE -> lines += "Le fichier est copié, vérifié, puis retiré de l'ancien emplacement. Il doit rester au moins 1 Go libre à destination."
        }
        lines += when (c.source) {
            Source.RULES -> "Règles de l'assistant, sur ce téléphone, sans réseau et sans IA."
            Source.LEARNED -> if (c.reason == "Nom choisi par vous") "Nom que vous avez saisi vous-même." else "D'après une correction que vous avez faite avant."
            Source.AI -> "Proposé par l'IA du serveur CastBridge (noms nettoyés seulement) : à vérifier."
        }
        lines += when (s) {
            Sureness.HIGH -> if (c.type == ChangeType.TRASH) "Confiance élevée (${pct(c)} %)." else "Confiance élevée (${pct(c)} %) : coché d'avance."
            Sureness.MEDIUM -> "Confiance moyenne (${pct(c)} %) : à vérifier avant d'appliquer."
            Sureness.LOW -> "Confiance faible (${pct(c)} %) : décoché, à regarder de près."
        }
        if (c.type == ChangeType.RENAME) lines += "Pas d'accord ? « Modifier le nom » : l'assistant s'en souviendra pour les fichiers du même titre."
        return Why(c.reason, lines, s)
    }

    private fun pct(c: Change) = Math.round(c.confidence * 100).toInt()

    private fun kindWords(k: Kind) = when (k) {
        Kind.SERIES -> "une série"; Kind.MOVIE -> "un film"; Kind.MUSIC -> "de la musique"; Kind.CLIP -> "un clip"; Kind.COURSE -> "un cours"
        Kind.PERSONAL -> "une vidéo personnelle"; Kind.PHOTO -> "une photo"; Kind.DOCUMENT -> "un document"; Kind.APP -> "une application"
        Kind.ARCHIVE -> "une archive"; Kind.UNKNOWN -> "un fichier à trier"
    }
}

/**
 * The user picked a folder that is itself called "Séries", "Films"… : the plan must not create "Séries/Séries/…" inside it. Drops a first folder segment
 * that has the same name as the picked folder (case and accents ignored). Only touches folders of RENAME changes; ids stay the same.
 */
fun Plan.withoutRootSegment(rootName: String): Plan {
    fun norm(x: String) = java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()
    val r = norm(rootName)
    if (r.isEmpty()) return this
    return copy(changes = changes.map { c ->
        val f = c.toFolder ?: return@map c
        val first = f.substringBefore('/')
        if (norm(first) == r) c.copy(toFolder = f.substringAfter('/', "").ifEmpty { null }) else c
    }.filter { it.type != ChangeType.RENAME || it.toName != null || it.toFolder != null })
}
