package castbridge.core.lots

import castbridge.core.learn.LearnCatalogFile
import java.io.File
import kotlin.system.exitProcess

/**
 * Size of the Learn + Quiz STARTER data bundled in the TV APK. It counts in the TV's 10 Mo budget ([LotBudget.TV_MAX_BYTES]),
 * so the build fails above it (gradle :core:checkStarterBudget, part of `check`) and the TV subtracts it at runtime.
 *
 * Sources: the Apprendre packs listed in the embedded catalog (zips, bytes as shipped) and the bundled quiz question banks
 * (castbridge/quiz/questions*.json, raw bytes: conservative, the APK compresses them).
 */
object StarterBudget {
    private val QUIZ_RESOURCES = listOf("questions.json", "questions-school.json")

    data class Report(val learnBytes: Long, val quizBytes: Long, val items: Map<String, Long>) {
        val total get() = learnBytes + quizBytes
        fun text(max: Long = LotBudget.TV_MAX_BYTES) = buildString {
            appendLine("Données de démarrage embarquées dans CastBridge-TV :")
            items.forEach { (k, v) -> appendLine("  %-40s %9d octets".format(k, v)) }
            appendLine("  Apprendre ${LotStore.mo(learnBytes)} + Quiz ${LotStore.mo(quizBytes)} = ${LotStore.mo(total)} sur ${LotStore.mo(max)} (${total * 100 / max} %)")
        }
    }

    /** Measures the classpath resources (works from the jar and from the Android APK). */
    fun measure(): Report {
        val items = LinkedHashMap<String, Long>()
        var learn = 0L
        val base = "/castbridge/learn/embedded/"
        val cat = StarterBudget::class.java.getResourceAsStream(base + "catalog.json")?.use { String(it.readBytes(), Charsets.UTF_8) }
        if (cat != null) for (item in LearnCatalogFile.parse(cat)) {
            val f = item.file ?: continue
            val n = StarterBudget::class.java.getResourceAsStream(base + f)?.use { it.readBytes().size.toLong() } ?: continue
            items["learn/$f"] = n; learn += n
        }
        var quiz = 0L
        for (r in QUIZ_RESOURCES) {
            val n = StarterBudget::class.java.getResourceAsStream("/castbridge/quiz/$r")?.use { it.readBytes().size.toLong() } ?: continue
            items["quiz/$r"] = n; quiz += n
        }
        // « Langues » free starter (castbridge/langues/embedded/): a few KB, but it counts like the rest
        val lang = castbridge.core.langues.EmbeddedLangSource().bytes()
        if (lang > 0) { items["langues/embedded"] = lang; learn += lang }
        return Report(learn, quiz, items)
    }

    /** Cached for the app (the bundled data never changes while it runs). */
    val bytes: Long by lazy { measure().total }

    /** `StarterBudget` tool (gradle :core:checkStarterBudget): prints the report, exit code 1 above the budget. */
    @JvmStatic fun main(args: Array<String>) {
        val max = args.firstOrNull()?.toLongOrNull() ?: LotBudget.TV_MAX_BYTES
        val r = measure()
        print(r.text(max))
        if (r.total > max) { System.err.println("ÉCHEC : les données de démarrage (${r.total} octets) dépassent le budget de la TV ($max octets)"); exitProcess(1) }
    }
}
