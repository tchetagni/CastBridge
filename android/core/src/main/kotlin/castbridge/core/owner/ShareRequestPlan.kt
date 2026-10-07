package castbridge.core.owner

import castbridge.core.quiz.QrCode

/**
 * Comment la demande d'appareil d'une TV part dans la feuille de partage d'Android (R-50, confirmé en production par le propriétaire le 2026-10-07 : « Partager la demande » ne marchait pas sur WhatsApp).
 *
 *  - **« Partager la demande » = le TEXTE seul** : `text/plain`, `EXTRA_SUBJECT`, `EXTRA_TEXT` = la demande complète, **jamais de flux**. Avant, l'écran envoyait UNE intention `image/png` avec `EXTRA_STREAM` (le QR)
 *    ET `EXTRA_TEXT` : WhatsApp (et d'autres) envoie alors l'image et abandonne le texte, alors que le texte est ce que lisent les outils de l'agent.
 *  - **« Partager le code QR » = un bouton SÉPARÉ**, présent seulement quand le QR tient ([LockedRequestRoute.qr] non nul, jamais un QR tronqué) : `image/png` avec `EXTRA_STREAM`, la `ClipData` de l'URI et
 *    `FLAG_GRANT_READ_URI_PERMISSION` posés sur l'intention ET sur le sélecteur (`Intent.createChooser` ne recopie sur le sélecteur que ce que l'intention porte déjà, une `ClipData` ET le drapeau, et le sélecteur
 *    lui-même doit lire l'image pour son aperçu). Le texte de la demande reste en `EXTRA_TEXT` à titre indicatif (un e-mail le prend en corps ; WhatsApp peut l'ignorer : le texte part par l'autre bouton).
 *
 * Pur : le plan dit QUOI poser et OÙ ; le dessin de l'intention Android est dans `sender` (`ShareRequestIntents`, qui obéit au plan), le même pour « Activer la TV » et « Demande d'appareil » (qui partage le texte
 * seul depuis toujours, avec son propre sujet et son propre titre). Aucun texte d'état ne recopie la demande (ACT-NF2) : [Send.toString] ne dit que la forme.
 */
object ShareRequestPlan {
    const val TEXT_MIME = "text/plain"
    const val IMAGE_MIME = "image/png"

    /** Les libellés des boutons de « Activer la TV » (dans une rangée qui passe à la ligne : la limite de 14 caractères de [castbridge.core.ux.AppBarBudget] ne vaut que pour une barre d'application). */
    const val REQUEST_BUTTON = "Partager la demande"
    const val QR_BUTTON = "Partager le code QR"
    const val COPY_BUTTON = "Copier"
    /** Dit quand l'image du QR n'a pas pu être écrite dans le cache (le texte reste partageable). */
    const val QR_FAILED = "Le code QR n'a pas pu être préparé : envoyez plutôt la demande en texte."

    /** Où une pose est faite : sur l'intention `ACTION_SEND` elle-même, ou sur l'intention du sélecteur (`ACTION_CHOOSER`) qui l'enveloppe. */
    enum class Place { INTENT, CHOOSER }
    private val BOTH = setOf(Place.INTENT, Place.CHOOSER)

    /**
     * Ce qu'un partage doit porter. [image] = le QR à écrire en PNG et à joindre en `EXTRA_STREAM` (null : jamais de flux). [clipData] et [grantFlag] = où poser la `ClipData` de l'URI de l'image et
     * `FLAG_GRANT_READ_URI_PERMISSION` (vides quand il n'y a pas d'image : rien à autoriser).
     */
    class Send(
        val mime: String, val subject: String, val text: String, val chooserTitle: String,
        val image: QrCode? = null, val clipData: Set<Place> = emptySet(), val grantFlag: Set<Place> = emptySet(),
    ) {
        val stream: Boolean get() = image != null
        override fun toString() = "Send($mime, ${if (stream) "avec image" else "texte seul"})"
    }

    /** « Partager la demande » : le texte [text] seul, avec son [subject] ; jamais de flux, même quand le QR tient. */
    fun request(subject: String, text: String, chooserTitle: String = REQUEST_BUTTON): Send = Send(TEXT_MIME, subject, text, chooserTitle)

    /** « Partager le code QR » : l'image de [qr] seule, avec l'autorisation de lecture sur l'intention et le sélecteur ; null quand le QR ne tient pas (pas de bouton). */
    fun qr(subject: String, text: String, qr: QrCode?, chooserTitle: String = QR_BUTTON): Send? =
        qr?.let { Send(IMAGE_MIME, subject, text, chooserTitle, image = it, clipData = BOTH, grantFlag = BOTH) }
}
