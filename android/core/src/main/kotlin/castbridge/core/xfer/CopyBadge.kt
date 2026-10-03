package castbridge.core.xfer

import castbridge.core.ux.CopyBadgeTexts

/**
 * R-16 (B) : la petite icône « copie en cours » du lecteur plein écran. PURE : ne dessine rien, dit seulement quoi dessiner depuis ce que la carte TV
 * lit déjà ([TransferProgress.active]).
 *
 * Règles : visible seulement s'il reste au moins une copie en cours (une copie finie ou en échec ne compte pas) ET si la barre de progression du
 * lecteur n'est pas affichée (aucun recouvrement avec la barre de lecture) ; pourcentage global = somme reçue / somme attendue (pas la moyenne des
 * pourcentages), arrondi vers le bas (jamais 100 % avant le dernier octet) ; une taille inconnue rend le tout indéterminé (aucun pourcentage inventé) ;
 * pastille de nombre quand il y a plusieurs copies ; teinte « ralentie » (orange) dès qu'une copie l'est (R-15).
 */
object CopyBadge {
    enum class Tone { NORMAL, SLOWED }

    /** [percent] null = indéterminé ; [label] = « 42 % » (vide si indéterminé) ; [countLabel] = « 2 » seulement pour plusieurs copies ; [description] = texte accessible. */
    data class Model(val visible: Boolean, val percent: Int?, val count: Int, val tone: Tone, val label: String, val countLabel: String?, val description: String)

    private val HIDDEN = Model(false, null, 0, Tone.NORMAL, "", null, "")

    fun of(items: List<TransferProgress.Item>, overlayVisible: Boolean = false): Model {
        val running = items.filter { it.phase == TransferProgress.Phase.RUNNING }
        if (running.isEmpty() || overlayVisible) return HIDDEN
        val slowed = running.any { it.slowed }
        val known = running.all { it.total > 0 }
        val percent = if (!known) null else {
            val total = running.sumOf { it.total }
            val got = running.sumOf { it.received.coerceIn(0, it.total) }
            if (got >= total) 100 else (got * 100 / total).toInt().coerceIn(0, 99)
        }
        return Model(true, percent, running.size, if (slowed) Tone.SLOWED else Tone.NORMAL, percent?.let { "$it %" } ?: "",
            if (running.size >= 2) running.size.toString() else null, CopyBadgeTexts.description(running.size, percent, slowed))
    }
}
