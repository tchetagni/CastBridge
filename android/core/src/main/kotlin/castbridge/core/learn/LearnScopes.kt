package castbridge.core.learn

/**
 * Lots of « Apprendre » (docs/LEARN.md § Lots): one lot = all the packs of ONE class/level (the *scope*). The table that
 * ties pack ids to scopes is `content/learn/scopes.txt` (builder side); the phone and the TV only need the *ladder* of
 * levels below, to order what is kept on the TV ([learnPriority]) and to place a pack found without a table ([guess]).
 */
object LearnScopes {
    /** Scopes of the two sub-systems and of the Licence, in teaching order; scopes sharing an inner list are the same year. */
    val ladders: Map<String, List<List<String>>> = linkedMapOf(
        "fr" to listOf(listOf("maternelle"), listOf("cp"), listOf("ce1"), listOf("ce2"), listOf("cm1"), listOf("cm2"),
            listOf("6e"), listOf("5e"), listOf("4e"), listOf("3e"), listOf("2nde"), listOf("1ere"),
            listOf("tle-commun", "tle-cd", "tle-a")),
        "en" to listOf(listOf("nursery"), listOf("class1"), listOf("class2"), listOf("class3"), listOf("class4"), listOf("class5"), listOf("class6"),
            listOf("form1"), listOf("form2"), listOf("form3"), listOf("form4"), listOf("form5"), listOf("lower-sixth"), listOf("upper-sixth")),
        "licence" to listOf(listOf("droit-l1"), listOf("droit-l2"), listOf("droit-l3")),
    )

    private val levelScope: Map<String, String> = linkedMapOf(
        "PS" to "maternelle", "MS" to "maternelle", "GS" to "maternelle", "Nursery 1" to "nursery", "Nursery 2" to "nursery",
        "SIL" to "cp", "CP" to "cp", "CE1" to "ce1", "CE2" to "ce2", "CM1" to "cm1", "CM2" to "cm2",
        "Class 1" to "class1", "Class 2" to "class2", "Class 3" to "class3", "Class 4" to "class4", "Class 5" to "class5", "Class 6" to "class6",
        "6e" to "6e", "5e" to "5e", "4e" to "4e", "3e" to "3e", "2nde" to "2nde", "1re" to "1ere", "Tle" to "tle-cd",
        "Form 1" to "form1", "Form 2" to "form2", "Form 3" to "form3", "Form 4" to "form4", "Form 5" to "form5",
        "Lower Sixth" to "lower-sixth", "Upper Sixth" to "upper-sixth", "L1" to "droit-l1", "L2" to "droit-l2", "L3" to "droit-l3",
    )

    /** The scope of a class of the catalog ([LearnCatalog.levels]); Terminale = the C/D lot (the other series have their own scopes). */
    fun ofLevel(level: String?): String? = levelScope[level]

    /** Scope of a pack that no table lists (a loose pack on a USB drive): by level, philosophy being common to all Terminale series. */
    fun guess(m: PackManifest): String? = when {
        m.level == "Tle" && m.subject == "philosophie" -> "tle-commun"
        else -> ofLevel(m.level)
    }

    /** Human label of a scope (fallback when the lot title is not known). */
    fun label(scope: String): String = LABELS[scope] ?: scope

    private val LABELS = mapOf(
        "maternelle" to "Maternelle", "cp" to "CP", "ce1" to "CE1", "ce2" to "CE2", "cm1" to "CM1", "cm2" to "CM2", "6e" to "6e", "5e" to "5e",
        "4e" to "4e", "3e" to "3e", "2nde" to "2nde", "1ere" to "Première", "tle-cd" to "Terminale C/D", "tle-a" to "Terminale A", "tle-commun" to "Terminale (toutes séries)",
        "nursery" to "Nursery", "class1" to "Class 1", "class2" to "Class 2", "class3" to "Class 3", "class4" to "Class 4", "class5" to "Class 5", "class6" to "Class 6",
        "form1" to "Form 1", "form2" to "Form 2", "form3" to "Form 3", "form4" to "Form 4", "form5" to "Form 5", "lower-sixth" to "Lower Sixth", "upper-sixth" to "Upper Sixth",
        "droit-l1" to "Droit L1", "droit-l2" to "Droit L2", "droit-l3" to "Droit L3",
    )

    /** A scope name that is safe as a folder name and in the server's API. */
    fun valid(scope: String) = Regex("[a-z0-9][a-z0-9-]{0,31}").matches(scope)

    /** Parsed `scopes.txt`: scope → (title, pack ids), in file order. */
    class Table(val lots: Map<String, Lot>) {
        class Lot(val scope: String, val title: String, val packs: List<String>)
        fun scopeOf(pack: String): String? = lots.values.firstOrNull { pack in it.packs }?.scope
    }

    fun parseTable(text: String): Table {
        val lots = LinkedHashMap<String, Table.Lot>(); val seen = HashSet<String>()
        for ((n, raw) in text.lines().withIndex()) {
            val line = raw.trim(); if (line.isEmpty() || line.startsWith("#")) continue
            val f = line.split('|').map { it.trim() }
            require(f.size == 3) { "scopes.txt ligne ${n + 1} : « scope | titre | packs » attendu" }
            require(valid(f[0])) { "scopes.txt ligne ${n + 1} : scope « ${f[0]} » invalide" }
            require(f[0] !in lots) { "scopes.txt ligne ${n + 1} : scope « ${f[0]} » en double" }
            val ids = f[2].split(Regex("\\s+")).filter { it.isNotEmpty() }
            require(ids.isNotEmpty()) { "scopes.txt ligne ${n + 1} : aucun pack" }
            ids.forEach { require(seen.add(it)) { "scopes.txt : le pack $it est dans deux lots" } }
            lots[f[0]] = Table.Lot(f[0], f[1], ids)
        }
        return Table(lots)
    }
}

/**
 * What the TV should keep first, for the framework's planner: the scopes ordered by usefulness for a student of [level] —
 * the own class first, then the year above (the student is about to be there), the year below, two above, two below…
 * Only scopes of the student's own system (fr / en / licence). Unknown or missing level = empty (the planner keeps what it has).
 */
fun learnPriority(level: String?): List<String> {
    val own = LearnScopes.ofLevel(level) ?: return emptyList()
    val ladder = LearnScopes.ladders.values.first { l -> l.any { own in it } }
    val at = ladder.indexOfFirst { own in it }
    val out = LinkedHashSet<String>()
    out += ladder[at].sortedByDescending { it == own }            // same year: own series first
    for (d in 1 until ladder.size) { ladder.getOrNull(at + d)?.let { out += it }; ladder.getOrNull(at - d)?.let { out += it } }
    return out.toList()
}

/** Several students share the TV: each one's own class comes before anybody's second choice (round-robin merge, no duplicates). */
fun learnPriority(profiles: List<Profile>): List<String> {
    val lists = profiles.sortedBy { it.createdAt }.map { learnPriority(it.level) }
    val out = LinkedHashSet<String>()
    for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) for (l in lists) l.getOrNull(i)?.let { out += it }
    return out.toList()
}
