package castbridge.core.tv

/**
 * Réglages du son (wtv-01 n°6) : purs. « Mode nuit » = compression de dynamique (filtre libVLC « compressor ») ; amplification de 100 à 200 %
 * (au-delà de 100 % : avertissement) ; « vitesse sans changer la voix » = correction de hauteur (désactivée par défaut : elle coûte du processeur).
 */
data class AudioTuning(val night: Boolean = false, val gainPercent: Int = 100, val keepPitch: Boolean = false) {
    companion object {
        const val MIN_GAIN = 100
        const val MAX_GAIN = 200
        const val GAIN_STEP = 10
        const val WARNING = "Au-delà de 100 % le son peut saturer ou grésiller : baissez si besoin."
        fun clampGain(p: Int) = p.coerceIn(MIN_GAIN, MAX_GAIN)
        fun decode(s: String?): AudioTuning {
            var t = AudioTuning()
            for (tok in s.orEmpty().split(',')) {
                if (tok.length < 2) continue
                val n = tok.substring(1).toIntOrNull() ?: continue
                when (tok[0]) { 'n' -> t = t.copy(night = n == 1); 'g' -> t = t.copy(gainPercent = clampGain(n)); 'k' -> t = t.copy(keepPitch = n == 1) }
            }
            return t
        }
    }

    val isDefault: Boolean get() = this == AudioTuning()
    /** Avertissement à afficher quand l'amplification dépasse 100 %. */
    val warning: String? get() = if (gainPercent > 100) WARNING else null

    /** Volume libVLC (0..200) : l'amplification, atténuée par le fondu du minuteur ([fade] 0..1). */
    fun volume(fade: Float = 1f): Int = (gainPercent * fade.coerceIn(0f, 1f)).toInt().coerceIn(0, MAX_GAIN)

    /** Option libVLC du mode nuit (vide : aucun filtre). */
    fun filterOptions(): List<String> = if (night) listOf(":audio-filter=compressor", ":compressor-rms-peak=0.2", ":compressor-attack=24", ":compressor-release=250", ":compressor-threshold=-25", ":compressor-ratio=6", ":compressor-knee=4", ":compressor-makeup-gain=12") else emptyList()

    fun encode(): String = buildList { if (night) add("n1"); if (gainPercent != 100) add("g$gainPercent"); if (keepPitch) add("k1") }.joinToString(",")
}
