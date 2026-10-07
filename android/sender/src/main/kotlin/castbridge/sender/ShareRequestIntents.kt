package castbridge.sender

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import castbridge.core.owner.ShareRequestPlan
import castbridge.core.owner.ShareRequestPlan.Place

/**
 * Dessine, pour Android, l'intention de la feuille de partage d'un [ShareRequestPlan.Send] (R-50) : la règle (quoi poser, et où) est dans le plan pur et testé, ici ne sont faits que les appels d'Android,
 * sans aucune décision. [uri] = l'image du QR écrite par l'écran (fournisseur de fichiers privé) ; elle n'est jointe que si le plan joint une image : un plan de texte ne porte JAMAIS de flux, même si un
 * appelant en passe un. Rend l'intention du sélecteur, que l'écran démarre (le même dessin pour « Activer la TV » et « Demande d'appareil »).
 */
object ShareRequestIntents {
    fun chooser(plan: ShareRequestPlan.Send, uri: Uri? = null): Intent {
        require(!plan.stream || uri != null) { "un plan avec image demande l'URI de l'image" }
        val image = uri.takeIf { plan.stream }
        val send = Intent(Intent.ACTION_SEND).setType(plan.mime).putExtra(Intent.EXTRA_SUBJECT, plan.subject).putExtra(Intent.EXTRA_TEXT, plan.text)
        if (image != null) {
            send.putExtra(Intent.EXTRA_STREAM, image)
            if (Place.INTENT in plan.clipData) send.clipData = ClipData.newRawUri(plan.subject, image)
            if (Place.INTENT in plan.grantFlag) send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, plan.chooserTitle)
        if (image != null) {
            if (Place.CHOOSER in plan.clipData) chooser.clipData = ClipData.newRawUri(plan.subject, image)
            if (Place.CHOOSER in plan.grantFlag) chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return chooser
    }
}
