package castbridge.core.learn

/**
 * The Cameroonian school system as offered by « Apprendre » (docs/LEARN.md): the two sub-systems (francophone and
 * anglophone), their levels from nursery school to the Licence, the national exams and the subjects. This catalog is
 * always embedded (a few kB); lessons and exercises come in packs (embedded sampler, packs on a drive, future server).
 */
object LearnCatalog {
    enum class Stage(val key: String, val fr: String, val en: String) {
        NURSERY("nursery", "Maternelle", "Nursery"),
        PRIMARY("primary", "Primaire", "Primary"),
        SECONDARY("secondary", "Secondaire", "Secondary"),
        HIGHER("higher", "Supérieur", "Higher education");
    }

    /** A curriculum = a stage in one sub-system (fr = francophone, en = anglophone). */
    data class Cursus(val key: String, val stage: Stage, val lang: String, val label: String, val authority: String)

    data class Level(val key: String, val cursus: String, val label: String, val order: Int)

    /** A national exam, taken at the end of [level]. [organizer] is the body that runs it (informational). */
    data class Exam(
        val key: String, val label: String, val level: String, val lang: String, val organizer: String,
        val description: String, val series: List<String> = emptyList(),
    )

    data class Subject(val key: String, val fr: String, val en: String, val color: Int) {
        fun label(lang: String) = if (lang == "en") en else fr
    }

    val cursus: List<Cursus> = listOf(
        Cursus("maternelle", Stage.NURSERY, "fr", "Maternelle", "MINEDUB"),
        Cursus("nursery", Stage.NURSERY, "en", "Nursery", "MINEDUB"),
        Cursus("primaire", Stage.PRIMARY, "fr", "Primaire (francophone)", "MINEDUB"),
        Cursus("primary", Stage.PRIMARY, "en", "Primary (anglophone)", "MINEDUB"),
        Cursus("secondaire", Stage.SECONDARY, "fr", "Secondaire (francophone)", "MINESEC"),
        Cursus("secondary", Stage.SECONDARY, "en", "Secondary (anglophone)", "MINESEC"),
        Cursus("superieur", Stage.HIGHER, "fr", "Supérieur (Licence)", "MINESUP / universités"),
    )

    val levels: List<Level> = run {
        var o = 0
        fun lv(c: String, vararg keys: String) = keys.map { Level(it, c, it, o++) }
        lv("maternelle", "PS", "MS", "GS").map { it.copy(label = mapOf("PS" to "Petite section", "MS" to "Moyenne section", "GS" to "Grande section")[it.key]!!) } +
            lv("nursery", "Nursery 1", "Nursery 2") +
            lv("primaire", "SIL", "CP", "CE1", "CE2", "CM1", "CM2") +
            lv("primary", "Class 1", "Class 2", "Class 3", "Class 4", "Class 5", "Class 6") +
            lv("secondaire", "6e", "5e", "4e", "3e", "2nde", "1re", "Tle") +
            lv("secondary", "Form 1", "Form 2", "Form 3", "Form 4", "Form 5", "Lower Sixth", "Upper Sixth") +
            lv("superieur", "L1", "L2", "L3")
    }

    val exams: List<Exam> = listOf(
        Exam("CEP", "CEP", "CM2", "fr", "MINEDUB", "Certificat d'études primaires, fin du CM2"),
        Exam("FSLC", "FSLC", "Class 6", "en", "MINEDUB", "First School Leaving Certificate, end of Class 6"),
        Exam("BEPC", "BEPC", "3e", "fr", "MINESEC", "Brevet d'études du premier cycle, fin de 3e"),
        Exam("GCE-OL", "GCE O Level", "Form 5", "en", "Cameroon GCE Board", "General Certificate of Education, Ordinary Level"),
        Exam("PROBATOIRE", "Probatoire", "1re", "fr", "Office du Baccalauréat du Cameroun", "Examen de fin de Première", listOf("A", "C", "D")),
        Exam("BAC", "Baccalauréat", "Tle", "fr", "Office du Baccalauréat du Cameroun", "Examen de fin de Terminale", listOf("A", "C", "D")),
        Exam("GCE-AL", "GCE A Level", "Upper Sixth", "en", "Cameroon GCE Board", "General Certificate of Education, Advanced Level"),
    )

    val subjects: List<Subject> = listOf(
        Subject("maths", "Mathématiques", "Mathematics", 0xFF1E88E5.toInt()),
        Subject("francais", "Français", "French", 0xFFE53935.toInt()),
        Subject("english", "Anglais", "English Language", 0xFFD81B60.toInt()),
        Subject("sciences", "Sciences", "Science", 0xFF43A047.toInt()),
        Subject("pct", "Physique-Chimie-Technologie", "Physics-Chemistry-Technology", 0xFF8E24AA.toInt()),
        Subject("physique-chimie", "Physique-Chimie", "Physics & Chemistry", 0xFF8E24AA.toInt()),
        Subject("svt", "SVT", "Life and Earth Sciences", 0xFF2E7D32.toInt()),
        Subject("biology", "Biologie", "Biology", 0xFF2E7D32.toInt()),
        Subject("physics", "Physique", "Physics", 0xFF6A1B9A.toInt()),
        Subject("chemistry", "Chimie", "Chemistry", 0xFF00897B.toInt()),
        Subject("histoire-geo", "Histoire-Géographie-ECM", "History-Geography-Citizenship", 0xFFF4511E.toInt()),
        Subject("philosophie", "Philosophie", "Philosophy", 0xFF5D4037.toInt()),
        Subject("decouverte", "Découverte du monde", "Discovering the world", 0xFFFFB300.toInt()),
        Subject("droit", "Droit", "Law", 0xFF546E7A.toInt()),
        Subject("economie", "Économie", "Economics", 0xFF00ACC1.toInt()),
    )

    /** Ages and levels are what the profile stores; nothing else about the child. */
    fun level(key: String?): Level? = levels.firstOrNull { it.key == key }
    fun cursus(key: String?): Cursus? = cursus.firstOrNull { it.key == key }
    fun exam(key: String?): Exam? = exams.firstOrNull { it.key == key }
    fun subject(key: String?): Subject? = subjects.firstOrNull { it.key == key }
    fun levelsOf(cursus: String) = levels.filter { it.cursus == cursus }
    fun cursusOfLevel(level: String?): Cursus? = level(level)?.let { cursus(it.cursus) }
    /** Exams reachable from [level] (its own, and the next one for the class just below). */
    fun examsFor(level: String?): List<Exam> = exams.filter { it.level == level }

    /** Primary and nursery levels: lessons may be read aloud (TextToSpeech) by default. */
    fun readAloudByDefault(level: String?): Boolean = cursusOfLevel(level)?.stage.let { it == Stage.NURSERY || it == Stage.PRIMARY }
}
