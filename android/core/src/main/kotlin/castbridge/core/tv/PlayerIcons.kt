package castbridge.core.tv

import castbridge.core.status.IconKind
import castbridge.core.status.Tech
import castbridge.core.xfer.CopyBadge
import kotlin.math.pow

/**
 * Icônes affichées par-dessus la vidéo de CastBridge-TV : règles PURES (libellé, taille, contraste, zone de sécurité, légende). Le dessin est dans les vues Android.
 * Règle du propriétaire (2026-10-04) : jamais une icône seule, toujours un libellé court en français ; lisible à 3 m sur une dalle 720p.
 */
object PlayerIcons {
    /** Taille minimale du texte (sp) et des icônes (dp) du lecteur. */
    const val TEXT_SP = 28
    const val ICON_DP = 40
    /** Contraste minimal (WCAG) du texte blanc sur sa pastille ; la pastille noire est à 80 % d'opacité. */
    const val MIN_CONTRAST = 4.5
    const val PILL_ALPHA = 0xCC

    /** Une ligne de la « Légende des icônes ». [kind] : l'icône de la barre d'état qu'elle décrit (null : copie, licence, marques, états). */
    data class Entry(val id: String, val label: String, val meaning: String, val kind: IconKind? = null)

    /** Marges de la zone de sécurité (surbalayage des TV) : 5 % de chaque bord, en pixels de la dalle. */
    data class Safe(val horizontal: Int, val vertical: Int)
    fun safe(w: Int, h: Int) = Safe(w * 5 / 100, h * 5 / 100)

    fun legend(): List<Entry> {
        val kinds = IconKind.values().sortedBy { it.order }.map { Entry(it.wire, it.label, meaning(it), it) }
        val extra = listOf(
            Entry("copy", "Copie en cours", "Un fichier arrive sur la TV depuis un téléphone : flèche vers le bas et pourcentage reçu. Orange : la copie est ralentie."),
            Entry("edition", "Licence", "Étiquette en haut au centre : l'édition de la TV (essai, production, super illimité) et la durée de la clé."),
        )
        val marks = listOf(Tech.WIFI_LAN, Tech.WIFI_DIRECT, Tech.BLUETOOTH, Tech.ETHERNET, Tech.USB).map {
            Entry("tech-${it.wire}", "Marque : ${it.label}", "Petite marque en bas à droite d'une pastille : la liaison passe par ${it.label}.")
        }
        val states = listOf(
            Entry("state-connecting", "Point : connexion…", "Pastille un peu pâle avec un point : la liaison s'établit."),
            Entry("state-degraded", "Point : reconnexion", "Pastille pâle avec un point : la liaison est perdue, elle se rétablit seule."),
            Entry("state-error", "Fond rouge : erreur", "Pastille à fond rouge : la liaison est en erreur, ouvrez Connexions pour agir."),
        )
        return kinds + extra + marks + states
    }

    private fun meaning(k: IconKind) = when (k) {
        IconKind.INTERNET -> "La TV a accès à Internet (Wi-Fi, Ethernet ou passerelle du téléphone)."
        IconKind.PHONE -> "Un téléphone CastBridge est relié à la TV et peut la piloter."
        IconKind.REMOTE_CONTROL -> "Un téléphone pilote la lecture comme une télécommande."
        IconKind.SSH -> "Administration à distance ouverte (SSH)."
        IconKind.GATEWAY -> "La TV utilise l'Internet d'un téléphone (passerelle Bluetooth)."
        IconKind.CAST -> "Une diffusion depuis un téléphone est en cours."
        IconKind.USB_DRIVE -> "Une clé USB est branchée."
        IconKind.WIFI_DIRECT_GROUP -> "Le Wi-Fi Direct est actif : téléphone et TV se parlent sans box."
        IconKind.QUIZ_PLAYER -> "Un joueur du Quiz est connecté."
        IconKind.CHESS_PLAYER -> "Un joueur d'Échecs est connecté."
        IconKind.DOWNLOAD -> "Un téléchargement est en cours."
        IconKind.PARENTAL_MODE -> "Le mode enfant est actif."
        IconKind.UPDATE -> "Une mise à jour de CastBridge-TV est disponible ou en cours."
    }

    /** Libellé de la copie en cours, à côté de la flèche : « Copie en cours 42 % », « 2 copies en cours 7 % » ; aucun pourcentage inventé. */
    fun copyCaption(m: CopyBadge.Model): String {
        val head = if (m.count >= 2) "${m.count} copies en cours" else "Copie en cours"
        return head + (if (m.percent != null) " ${m.percent} %" else "") + (if (m.tone == CopyBadge.Tone.SLOWED) " (ralentie)" else "")
    }

    /** Contraste du texte blanc sur une pastille noire d'opacité [alpha] (0..255) posée sur l'image la plus claire possible (blanc). */
    fun contrastOverWhite(alpha: Int): Double {
        val g = (255 - alpha.coerceIn(0, 255)) / 255.0
        val l = ((g + 0.055) / 1.055).pow(2.4)
        return 1.05 / (l + 0.05)
    }

    /** La zone d'icônes du lecteur suit la barre de commandes ; elle reste pour une alerte (erreur, reconnexion) ou pendant 4 s après un changement. */
    fun zoneVisible(controlsVisible: Boolean, alert: Boolean, changedRecently: Boolean) = controlsVisible || alert || changedRecently
}
