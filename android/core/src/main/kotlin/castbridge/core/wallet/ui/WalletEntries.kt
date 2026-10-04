package castbridge.core.wallet.ui

/**
 * Saisie du code de réception à la télécommande à 5 touches : une « roulette » par caractère (haut / bas change le caractère, gauche / droite change de place, OK valide).
 * Le code est « R » + 8 caractères + 1 caractère de contrôle, alphabet sans I, L, O, U (comme le serveur) ; le « R » ne se change pas. Le contrôle est celui du serveur : un seul caractère faux
 * est vu tout de suite, avant tout appel réseau (le serveur reste le juge).
 */
class CodeEntry {
    private val chars = CharArray(10) { if (it == 0) 'R' else '0' }
    var cursor: Int = 1; private set

    fun up() { chars[cursor] = ALPHABET[(ALPHABET.indexOf(chars[cursor]) + 1) % ALPHABET.length] }
    fun down() { chars[cursor] = ALPHABET[(ALPHABET.indexOf(chars[cursor]) + ALPHABET.length - 1) % ALPHABET.length] }
    fun left() { cursor = maxOf(1, cursor - 1) }
    fun right() { cursor = minOf(9, cursor + 1) }

    /** Les 10 caractères sans tirets. */
    fun canonical(): String = String(chars)

    /** « R123-4567-89 » : la forme affichée par le serveur. */
    fun text(): String = canonical().let { "${it.substring(0, 4)}-${it.substring(4, 8)}-${it.substring(8)}" }

    /** Le caractère de contrôle est le bon (même formule que `ReceiveCodeService.check`). */
    fun complete(): Boolean = check(canonical().substring(0, 9)) == chars[9]

    companion object {
        const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        internal fun check(nine: String): Char = ALPHABET[nine.indices.sumOf { (it + 1) * ALPHABET.indexOf(nine[it]) } % ALPHABET.length]
    }
}

/** Saisie d'un montant à la télécommande : une roulette de chiffres, de la droite (unités) vers la gauche. Les chiffres tournent sans retenue : 9 + 1 = 0 pour ce chiffre seul. */
class AmountEntry(val maxDigits: Int = 9) {
    private val d = IntArray(maxDigits)
    var cursor: Int = maxDigits - 1; private set

    fun up() { d[cursor] = (d[cursor] + 1) % 10 }
    fun down() { d[cursor] = (d[cursor] + 9) % 10 }
    fun left() { cursor = maxOf(0, cursor - 1) }
    fun right() { cursor = minOf(maxDigits - 1, cursor + 1) }
    fun value(): Long = d.fold(0L) { acc, x -> acc * 10 + x }
    fun digits(): String = d.joinToString("")
}
