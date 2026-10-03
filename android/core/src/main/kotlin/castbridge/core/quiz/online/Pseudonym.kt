package castbridge.core.quiz.online

import java.text.Normalizer

/**
 * Contrôle des pseudonymes du Quiz en ligne (DESIGN-W20 § 3.3, T-7). PUR (la liste vient d'une ressource du cœur). Aucun texte libre dans le jeu :
 * le pseudonyme est la seule surface de contact entre inconnus, d'où la rigueur.
 *
 * Étapes, dans cet ordre (le premier motif rencontré est celui qui est dit) :
 *  1. NFKC, espaces réduits, apostrophe typographique ramenée à `'` ;
 *  2. pas d'adresse web ni de courriel (`http`, `www`, `://`, `@`, nom de domaine) ;
 *  3. pas de numéro de téléphone : au plus 6 chiffres au total (les chiffres sont comptés sans les séparateurs : « 6 99 00 11 22 » en compte 9) ;
 *     (URL et téléphone sont dits AVANT la longueur : un numéro long doit être refusé comme numéro, pas comme « trop long ») ;
 *  3 bis. 2 à 16 caractères ([MIN]..[MAX]) ;
 *  4. alphabet : lettres, chiffres, espace, `-`, `'`, `.` (le point : « Amina N. ») ;
 *  5. usurpation : `CastBridge`, `Admin`, `Modérateur`, `Prof`, `Support`… (comparaison sur la forme normalisée) ;
 *  6. liste de mots interdits fr / en / pidgin (`blocklist.txt`), sur la forme normalisée.
 *
 * Forme normalisée pour 5 et 6 : minuscules, sans accents, homoglyphes cyrilliques et grecs ramenés au latin, « leet » (0→o 1→i 3→e 4→a 5→s 7→t 8→b @→a $→s !→i),
 * séparateurs retirés, lettres répétées réduites à une (« fuuuck » = « fuck »). Les mots courts de la liste (ligne commençant par `=`) ne comptent que comme MOT
 * entier du nom (sans cela « Constance » serait refusé à cause de « con ») ; les autres comptent comme fragment.
 *
 * Limites honnêtes : une liste ne remplace pas la modération humaine ; elle est volontairement courte et se complète à chaque signalement (docs/PLAY-PROTOCOL.md).
 */
object Pseudonym {
    const val MIN = 2
    const val MAX = 16
    const val MAX_DIGITS = 6

    enum class Reason(val code: String, val message: String) {
        EMPTY("EMPTY", "il faut un pseudonyme"),
        LENGTH("LENGTH", "2 à 16 caractères"),
        URL("URL", "pas d'adresse web ni de courriel"),
        PHONE("PHONE", "pas de numéro de téléphone"),
        CHARS("CHARS", "lettres, chiffres, espace, tiret, apostrophe et point seulement"),
        IMPERSONATION("IMPERSONATION", "ce nom évoque l'équipe ou l'application"),
        BLOCKED("BLOCKED", "ce mot n'est pas permis"),
    }

    sealed class Result {
        data class Ok(val name: String) : Result()
        data class Refused(val reason: Reason) : Result() {
            /** Phrase française à montrer au joueur (le mot refusé n'est jamais répété). */
            val text: String get() = "Pseudonyme refusé : ${reason.message}."
        }
    }

    /** Liste lue : fragments (forme réduite), mots entiers, fragments à forme NON réduite (`~`). */
    private class Lists(val fragments: List<String>, val words: Set<String>, val rawFragments: List<String>)

    private val lists: Lists by lazy { load() }

    private val impersonationFragments by lazy { listOf("castbridge", "administrateur", "moderateur", "moderator").map { reduce(it) } }
    private val impersonationWords by lazy { setOf("admin", "modo", "support", "staff", "officiel", "official", "system", "systeme").map { reduce(it) }.toSet() }

    private val homoglyphs: Map<Char, Char> = buildMap {
        // cyrillique (formes qui ressemblent à une lettre latine, ou dont la lecture latine est évidente)
        "аеорсхуіјѕԁԛԝ".zip("aeopcxyijsdqw").forEach { (a, b) -> put(a, b) }
        mapOf('к' to 'k', 'м' to 'm', 'н' to 'h', 'т' to 't', 'в' to 'b').forEach { (a, b) -> put(a, b) }
        // grec
        mapOf('α' to 'a', 'β' to 'b', 'ε' to 'e', 'ι' to 'i', 'κ' to 'k', 'ν' to 'v', 'ο' to 'o', 'ρ' to 'p', 'τ' to 't', 'υ' to 'u', 'χ' to 'x').forEach { (a, b) -> put(a, b) }
        // i sans point, petites capitales latines
        put('ı', 'i')
        mapOf('ᴀ' to 'a', 'ʙ' to 'b', 'ᴄ' to 'c', 'ᴅ' to 'd', 'ᴇ' to 'e', 'ꜰ' to 'f', 'ɢ' to 'g', 'ʜ' to 'h', 'ɪ' to 'i', 'ᴊ' to 'j', 'ᴋ' to 'k', 'ʟ' to 'l', 'ᴍ' to 'm', 'ɴ' to 'n',
            'ᴏ' to 'o', 'ᴘ' to 'p', 'ʀ' to 'r', 'ꜱ' to 's', 'ᴛ' to 't', 'ᴜ' to 'u', 'ᴠ' to 'v', 'ᴡ' to 'w', 'ʏ' to 'y', 'ᴢ' to 'z').forEach { (a, b) -> put(a, b) }
    }
    private val leet = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '8' to 'b', '@' to 'a', '$' to 's', '!' to 'i', '€' to 'e')
    private val domain = Regex("[a-z0-9-]+\\s?\\.\\s?(com|net|org|cm|fr|io|me|ly|co|app|xyz|info|tv|ru|cn|gg|be|ch)\\b", RegexOption.IGNORE_CASE)

    fun check(raw: String?): Result {
        val n = Normalizer.normalize(raw ?: "", Normalizer.Form.NFKC).replace('’', '\'').replace('‘', '\'').replace(Regex("\\s+"), " ").trim()
        if (n.isEmpty()) return Result.Refused(Reason.EMPTY)
        val low = n.lowercase()
        if ("://" in low || "www" in low || "http" in low || '@' in low && low.contains('.') || domain.containsMatchIn(low)) return Result.Refused(Reason.URL)
        if (n.count { it.isDigit() } > MAX_DIGITS) return Result.Refused(Reason.PHONE)
        if (n.codePointCount(0, n.length) !in MIN..MAX) return Result.Refused(Reason.LENGTH)
        if (!alphabetOk(n)) return Result.Refused(Reason.CHARS)
        val forms = forms(n)
        val flat = forms.joinToString("") { it.first }
        if (impersonationFragments.any { it in flat } || forms.any { it.first in impersonationWords }) return Result.Refused(Reason.IMPERSONATION)
        // mot par mot (un fragment ne se cherche jamais à cheval sur deux mots : « Sasha Wolf » n'est pas « ashawo »)
        val l = lists
        if (forms.any { (c, raw0) -> l.words.contains(c) || l.fragments.any { it in c } || l.rawFragments.any { it in raw0 } }) return Result.Refused(Reason.BLOCKED)
        return Result.Ok(n)
    }

    /**
     * Lettres, chiffres, espace, `-`, `'`, `.`, et les MARQUES SANS CHASSE (tons des langues camerounaises : ɔ̀ ɛ́ ŋ̀) à la condition qu'elles suivent une lettre et soient 2 au plus
     * par lettre (une pile de marques, « Zalgo », est refusée). Une seule écriture : latin mêlé de cyrillique ou de grec (« pеdo ») est refusé.
     */
    private fun alphabetOk(n: String): Boolean {
        var marks = 0
        var prevLetter = false
        val scripts = HashSet<Character.UnicodeScript>()
        var i = 0
        while (i < n.length) {
            val cp = n.codePointAt(i); i += Character.charCount(cp)
            when {
                Character.getType(cp) == Character.NON_SPACING_MARK.toInt() -> { if (!prevLetter || ++marks > 2) return false }
                Character.isLetter(cp) -> { marks = 0; prevLetter = true; Character.UnicodeScript.of(cp).let { s -> if (s != Character.UnicodeScript.COMMON && s != Character.UnicodeScript.INHERITED) scripts += s } }
                Character.isDigit(cp) || cp == ' '.code || cp == '-'.code || cp == '\''.code || cp == '.'.code -> { marks = 0; prevLetter = false }
                else -> return false
            }
        }
        if (Character.isISOControl(n.first()) || n.any { it.isISOControl() }) return false
        val latin = Character.UnicodeScript.LATIN
        return !(latin in scripts && (Character.UnicodeScript.CYRILLIC in scripts || Character.UnicodeScript.GREEK in scripts) || Character.UnicodeScript.CYRILLIC in scripts && Character.UnicodeScript.GREEK in scripts)
    }

    /**
     * Les mots du nom, chacun en deux formes : RÉDUITE (lettres répétées réduites à une) et NON réduite (« nigger » reste « nigger », pas « niger » : le pays). Les suites de
     * lettres isolées (« c.o.n », « f u c k ») sont recollées en un seul mot.
     */
    internal fun forms(s: String): List<Pair<String, String>> {
        val parts = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .split(' ', '-', '\'', '.').map { it.map { c -> leet[c] ?: homoglyphs[c] ?: c }.filter { c -> c.isLetter() }.joinToString("") }.filter { it.isNotEmpty() }
        val merged = ArrayList<String>()
        var run = StringBuilder()
        fun flush() { if (run.isNotEmpty()) { merged += run.toString(); run = StringBuilder() } }
        for (p in parts) if (p.length == 1) run.append(p) else { flush(); merged += p }
        flush()
        return merged.map { collapse(it) to it }
    }

    /** Forme réduite d'un terme (entrées de liste, usurpations). */
    internal fun reduce(s: String): String = forms(s).joinToString("") { it.first }

    private fun collapse(s: String): String {
        val b = StringBuilder()
        for (c in s) if (b.isEmpty() || b.last() != c) b.append(c)
        return b.toString()
    }

    private fun load(): Lists {
        val stream = Pseudonym::class.java.getResourceAsStream("/castbridge/quiz/online/blocklist.txt")
            ?: throw IllegalStateException("blocklist.txt introuvable : refus plutôt qu'absence de contrôle")
        val fragments = ArrayList<String>(); val words = HashSet<String>(); val raws = ArrayList<String>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val t = line.substringBefore('#').trim()
                if (t.isEmpty()) continue
                when {
                    t.startsWith("=") -> reduce(t.substring(1)).takeIf { it.isNotEmpty() }?.let { words += it }
                    t.startsWith("~") -> t.substring(1).trim().lowercase().takeIf { it.length >= 4 }?.let { raws += it }
                    else -> reduce(t).takeIf { it.length >= 3 }?.let { fragments += it }
                }
            }
        }
        return Lists(fragments, words, raws)
    }
}
