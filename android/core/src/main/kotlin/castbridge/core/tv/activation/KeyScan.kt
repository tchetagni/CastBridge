package castbridge.core.tv.activation

/**
 * « Chercher la clé dans le fichier » (docs/TV-ACTIVATION-CLE-USB.md): the activation key may be anywhere in the chosen text file (a saved e-mail, a note with other lines),
 * not only on its first line. Pure: [candidates] only cuts the text into what LOOKS like a key, [pick] hands each one, in reading order, to the SAME verifier as a pasted key
 * (signature, binding to this TV's device code, window) and stops at the first one accepted. The text and the candidates are never logged.
 *
 * Shapes taken (anything else is ignored):
 *  - a full token: a word `cbx1.<payload>.<signature>` (surrounding quotes, brackets and final punctuation removed);
 *  - a grouped text or compact key: a run of at least [MIN_GROUPS] groups of 5 letters/digits (separated by `-` or spaces) holding at least one digit;
 *  - the same, wrapped over several consecutive lines (tried joined, after its own lines).
 */
object KeyScan {
    /** Largest text file read when the owner chooses a file (256 Kio, read once, bounded). */
    const val MAX_FILE_BYTES = 262_144
    /** At most this many candidates are verified for one file (a verification costs one signature check). */
    const val MAX_CANDIDATES = 32
    const val MIN_GROUPS = 5

    private val TOKEN = Regex("^cbx1\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9+/=_-]+$")
    /** One word of a grouped key: groups of 5, joined by `-`, possibly ending with `-` (a wrapped line). */
    private val GROUPS_WORD = Regex("^[0-9A-Za-z]{5}(-[0-9A-Za-z]{5})*-?$")
    private const val OPENERS = "\"'«»“”‘’()[]{}<>"
    private const val CLOSERS = "\"'«»“”‘’()[]{}<>.,;:!?"

    private fun strip(w: String) = w.trimStart { it in OPENERS }.trimEnd { it in CLOSERS }
    private fun groups(words: List<String>) = words.sumOf { w -> w.count { it != '-' } / 5 }

    fun candidates(text: String): List<String> {
        val out = LinkedHashSet<String>()
        fun add(c: String) { if (out.size < MAX_CANDIDATES) out += c }
        val block = ArrayList<String>()                    // consecutive lines made only of groups
        fun flushBlock() {
            if (block.size >= 2) {
                val joined = block.drop(1).fold(block[0]) { acc, l -> if (acc.endsWith("-")) acc + l else "$acc-$l" }
                if (joined.count { it != '-' } % 5 == 0 && groups(listOf(joined)) >= MIN_GROUPS && joined.any(Char::isDigit)) add(joined)
            }
            block.clear()
        }
        for (raw in text.removePrefix("﻿").lineSequence()) {
            if (out.size >= MAX_CANDIDATES) break
            val line = raw.trim()
            val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            // full tokens, wherever they are on the line
            for (w in words) strip(w).let { if (TOKEN.matches(it)) add(it) }
            // runs of group-shaped words
            val run = ArrayList<String>()
            fun flushRun() {
                if (groups(run) >= MIN_GROUPS && run.any { w -> w.any(Char::isDigit) }) add(run.joinToString(" "))
                run.clear()
            }
            for (w in words) { val s = strip(w); if (GROUPS_WORD.matches(s)) run += s else flushRun() }
            flushRun()
            // a line made only of groups may be one piece of a wrapped key
            if (words.isNotEmpty() && words.all { GROUPS_WORD.matches(it) }) block += words.joinToString("-").replace("--", "-") else flushBlock()
        }
        flushBlock()
        return out.toList()
    }

    /** [verdict] null = no candidate in the file; [index] = the accepted candidate, or the first of the most useful refusals; [tried] = verifications made. */
    data class Outcome(val verdict: Verdict?, val index: Int, val tried: Int)

    private fun rank(v: Verdict) = when (v) { Verdict.ACCEPTED -> 4; Verdict.WRONG_DEVICE -> 3; Verdict.EXPIRED -> 2; Verdict.NOT_VALID -> 1 }

    /** Verifies [candidates] in order and stops at the first ACCEPTED (installed by [verify] itself); otherwise reports the most telling refusal. */
    fun pick(candidates: List<String>, verify: (String) -> Verdict): Outcome {
        var best: Verdict? = null; var at = -1; var tried = 0
        for ((i, c) in candidates.withIndex()) {
            val v = verify(c); tried++
            if (v == Verdict.ACCEPTED) return Outcome(v, i, tried)
            if (best == null || rank(v) > rank(best)) { best = v; at = i }
        }
        return Outcome(best, at.coerceAtLeast(0), tried)
    }

    const val NO_KEY = "Aucune clé d'activation dans ce fichier : choisissez le fichier reçu avec la clé, ou collez la clé."

    /** A line to add before the verifier's own message: null when one key was tried (its message says it all) or when it was accepted. */
    fun summary(o: Outcome): String? = when {
        o.verdict == null -> NO_KEY
        o.verdict == Verdict.ACCEPTED || o.tried <= 1 -> null
        else -> "${o.tried} clés trouvées dans ce fichier, aucune n'est valable pour cette TV."
    }
}
