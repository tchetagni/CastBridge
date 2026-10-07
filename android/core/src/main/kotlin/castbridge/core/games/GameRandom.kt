package castbridge.core.games

/**
 * L'aléa des parties : un SplitMix64 (Sebastiano Vigna, domaine public) écrit ici, volontairement PAS `java.util.Random` ni `Collections.shuffle`. Un même couple
 * (graine, jeu de cartes) doit donner le même mélange sur la TV (Android 32 bits), sur le serveur Java et chez celui qui rejoue un journal, aujourd'hui comme dans cinq ans ;
 * le détail d'un algorithme de la bibliothèque standard peut changer d'une version à l'autre, celui-ci est figé par les vecteurs de `GameRandomTest` (calculés par une
 * implémentation indépendante).
 *
 * La graine vient de l'autorité de la partie (la TV à la maison, le serveur en ligne) et reste secrète tant que la partie dure : elle fixe la main de chacun.
 * Pas thread-safe : une instance par usage.
 */
class GameRandom(seed: Long) {
    private var state = seed

    /** 64 bits uniformes (SplitMix64). */
    fun nextLong(): Long {
        state += GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        return z xor (z ushr 31)
    }

    /**
     * Entier uniforme dans 0 until [bound], SANS biais : on tire 31 bits (les poids forts de [nextLong]) et on refuse les valeurs du dernier paquet incomplet
     * (`2^31 mod bound` valeurs) au lieu de les replier par un modulo.
     */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "borne strictement positive attendue : $bound" }
        val b = bound.toLong()
        val limit = (1L shl 31) - ((1L shl 31) % b)
        while (true) {
            val r = nextLong() ushr 33
            if (r < limit) return (r % b).toInt()
        }
    }

    /** Mélange de Fisher-Yates, du dernier élément au deuxième : pour i = n-1 jusqu'à 1, échange l'élément i avec l'élément `nextInt(i + 1)`. */
    fun <T> shuffle(list: MutableList<T>) {
        for (i in list.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val t = list[i]; list[i] = list[j]; list[j] = t
        }
    }

    private companion object {
        const val GAMMA = -0x61c8864680b583ebL          // 0x9E3779B97F4A7C15
        const val MIX1 = -0x40a7b892e31b1a47L           // 0xBF58476D1CE4E5B9
        const val MIX2 = -0x6b2fb644ecceee15L           // 0x94D049BB133111EB
    }
}
