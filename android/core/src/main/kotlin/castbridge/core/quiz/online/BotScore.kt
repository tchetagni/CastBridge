package castbridge.core.quiz.online

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Soupçon de robot, 0 à 100, par siège (DESIGN-W20 § 2.8, T-8). PUR : aucune horloge, aucun état ; le service passe la trace des réponses.
 *
 * Règles (chacune ajoute des points, le total est borné à 100) :
 *  - `BOT_FAST`     : au moins [MIN_ANSWERS] réponses et au moins 90 % sous 300 ms : 40 points ; et au moins 90 % sous 150 ms : 20 de plus. Un élève honnête lit la
 *                     question pendant le délai d'avant ouverture (1 à 2 s) puis touche à l'ouverture : 200 à 300 ms, jamais le palier 150 ms de façon constante ;
 *  - `BOT_UNIFORM`  : au moins 6 réponses dont le temps varie à peine (coefficient de variation < 0,08 : mécanique) : 25 points, SAUF si la moyenne est sous 150 ms (la compensation du
 *                     RTT ramène les temps d'un joueur rapide à 0 : ce n'est pas de la régularité) ;
 *  - `BOT_ACCURATE` : exactitude > 95 % sur au moins 10 questions : 10 points (un bon élève peut tout réussir : signal faible, jamais suffisant seul) ;
 *  - `BOT_TWINS`    : deux sièges du MÊME appareil (`deviceHash`) qui répondent pareil, au même instant, sur au moins 80 % de leurs questions communes (au moins 5) :
 *                     40 points chacun. L'adresse seule ne compte JAMAIS (école, CGNAT, famille : des dizaines de joueurs honnêtes la partagent).
 *
 * Retirées après l'audit Opus : « réponse juste en moins de 250 ms sur question difficile » (le serveur refuse tout avant l'ouverture, et un joueur honnête qui a lu pendant le
 * délai touche à l'ouverture : la règle accusait les meilleurs) et « exactitude sur questions réservées » (rien n'alimentait le drapeau).
 *
 * À partir de [THRESHOLD] (70) CE SIÈGE sort du classement ([rankedSeats]) : la partie se joue normalement pour tous, aucune expulsion, aucun blocage ; son écran ne dit que
 * [NEUTRAL_TEXT] (jamais « tricheur » ni « robot »). Les motifs (`reasons`) restent internes : modération, jamais affichés. Il faut au moins deux signaux indépendants.
 */
object BotScore {
    const val THRESHOLD = 70
    const val MIN_ANSWERS = 8
    const val FAST_MS = 300L
    const val VERY_FAST_MS = 150L
    const val NEUTRAL_TEXT = "Classement non pris en compte."
    /** Code visible : pourquoi un siège n'est pas classé, sans accusation. */
    const val RANK_SKIPPED = "RANK_SKIPPED"

    /** Une réponse : temps compté par le SERVEUR, juste ou non, choix donné. */
    data class Answer(val questionIndex: Int, val elapsedMs: Long, val correct: Boolean, val choice: Int = -1)

    data class Trace(val seat: String, val ip: String?, val deviceHash: String?, val answers: List<Answer>)

    data class Result(val score: Int, val reasons: List<String>) { val flagged: Boolean get() = score >= THRESHOLD }

    /** Score d'un siège seul (sans la règle des jumeaux). */
    fun score(t: Trace): Result = combine(single(t), emptyList())

    /** Score de tous les sièges d'une table, jumeaux compris. */
    fun scoreAll(traces: List<Trace>): Map<String, Result> {
        val twins = HashMap<String, MutableList<String>>()
        for (i in traces.indices) for (j in i + 1 until traces.size) {
            val a = traces[i]; val b = traces[j]
            if (a.deviceHash != null && a.deviceHash == b.deviceHash && identical(a, b)) {
                twins.getOrPut(a.seat) { ArrayList() } += "BOT_TWINS"; twins.getOrPut(b.seat) { ArrayList() } += "BOT_TWINS"
            }
        }
        return traces.associate { it.seat to combine(single(it), twins[it.seat].orEmpty()) }
    }

    /** Les sièges qui restent classés : tous sauf ceux qui atteignent le seuil. */
    fun rankedSeats(results: Map<String, Result>): Set<String> = results.filterValues { !it.flagged }.keys

    private class Part(val points: Int, val reason: String)

    private fun combine(parts: List<Part>, twin: List<String>): Result {
        val all = parts + twin.distinct().map { Part(40, it) }
        return Result(all.sumOf { it.points }.coerceIn(0, 100), all.map { it.reason })
    }

    private fun single(t: Trace): List<Part> {
        val a = t.answers
        val out = ArrayList<Part>()
        if (a.size >= MIN_ANSWERS) {
            if (a.count { it.elapsedMs < FAST_MS }.toDouble() / a.size >= 0.9) out += Part(40, "BOT_FAST")
            if (a.count { it.elapsedMs < VERY_FAST_MS }.toDouble() / a.size >= 0.9) out += Part(20, "BOT_FAST")
        }
        if (a.size >= 6) {
            val mean = a.sumOf { it.elapsedMs }.toDouble() / a.size
            val sd = sqrt(a.sumOf { val d = it.elapsedMs - mean; d * d } / a.size)
            if (mean >= 150.0 && sd / mean < 0.08) out += Part(25, "BOT_UNIFORM")
        }
        if (a.size >= 10 && a.count { it.correct }.toDouble() / a.size > 0.95) out += Part(10, "BOT_ACCURATE")
        return out
    }

    /** Mêmes choix et mêmes instants (à 60 ms près) sur au moins 80 % des questions communes, et au moins 5 questions communes. */
    private fun identical(x: Trace, y: Trace): Boolean {
        val bx = x.answers.associateBy { it.questionIndex }
        val common = y.answers.filter { it.questionIndex in bx }
        if (common.size < 5) return false
        val same = common.count { val o = bx.getValue(it.questionIndex); o.choice == it.choice && it.choice >= 0 && abs(o.elapsedMs - it.elapsedMs) <= 60 }
        return same.toDouble() / common.size >= 0.8
    }
}
