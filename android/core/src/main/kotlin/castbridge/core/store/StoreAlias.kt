package castbridge.core.store

import java.text.Normalizer
import java.util.Locale

/**
 * Alias court d'un article de la Boutique (6 caractères au plus, `[A-Z0-9]`, sans accent), lisible à 3 m sur la TV et
 * dicté au téléphone dans le code court d'une demande de location (« CM2-12H-7K3Q »).
 *
 * Règle déterministe (même entrée, même alias) : le titre est découpé en mots sans accent ; les mots vides (« classe », « de »…)
 * sont écartés ; un mot contenant un chiffre est gardé entier (« CM2 », « L1 », « 3E ») ; un mot de 3 lettres ou moins aussi ;
 * quelques mots connus ont une abréviation (« Terminale » : `TLE`, « Droit » : `DR`) ; tout autre mot de 4 lettres ou plus
 * donne ses 2 premières lettres s'il y a plusieurs mots, ses 6 premières s'il est seul. Titre vide : on repart de l'identifiant.
 * Collision avec [taken] : suffixe `2`, `3`… (le radical est raccourci pour rester dans 6 caractères).
 * Aucune I/O, aucune horloge.
 */
object StoreAlias {
    const val MAX_LENGTH = 6
    private const val FALLBACK = "BQ"

    private val STOPWORDS = setOf("classe", "de", "du", "des", "la", "le", "les", "et", "d", "l", "en", "cours", "pour", "un", "une")
    private val ABBREVIATIONS = mapOf(
        "terminale" to "TLE", "droit" to "DR", "premiere" to "1RE", "seconde" to "2DE",
        "sixieme" to "6E", "cinquieme" to "5E", "quatrieme" to "4E", "troisieme" to "3E",
    )

    /** L'alias de [title] (à défaut de [id]) qui ne figure pas dans [taken]. */
    fun of(title: String, id: String, taken: Set<String>): String {
        val base = radical(title).ifEmpty { radical(id) }.ifEmpty { FALLBACK }
        if (base !in taken) return base
        var n = 2
        while (true) {
            val suffix = n.toString()
            val candidate = base.take(MAX_LENGTH - suffix.length) + suffix
            if (candidate !in taken) return candidate
            n++
        }
    }

    private fun radical(text: String): String {
        val words = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in STOPWORDS }
        val out = words.joinToString("") { w ->
            when {
                w in ABBREVIATIONS -> ABBREVIATIONS.getValue(w)
                w.any { it.isDigit() } || w.length <= 3 -> w.uppercase(Locale.ROOT)
                words.size > 1 -> w.take(2).uppercase(Locale.ROOT)
                else -> w.uppercase(Locale.ROOT)
            }
        }
        return out.take(MAX_LENGTH)
    }
}
