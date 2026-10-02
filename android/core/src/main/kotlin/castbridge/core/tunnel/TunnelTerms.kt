package castbridge.core.tunnel

import castbridge.core.net.JsonLite
import castbridge.core.owner.SafeFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The terms of use the user must accept on THIS CastBridge-TV before the remote-administration tunnel may start (docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md: without them
 * published and accepted, the tunnel is not activated on customers' TVs). Changing the text = raising [VERSION]: the TV asks again and the tunnel waits.
 *
 * TODO(propriétaire) : texte à valider par le propriétaire (et par un juriste du pays de chaque marché) avant toute commercialisation.
 */
object TunnelTerms {
    const val VERSION = "Conditions d'usage v1"
    const val TITLE = "Conditions d'usage de CastBridge"
    const val CHECKBOX = "J'ai lu et j'accepte les conditions d'usage"
    const val ACCEPT_BUTTON = "J'accepte les conditions"
    const val LATER_BUTTON = "Plus tard"
    const val MUST_ACCEPT = "Acceptez d'abord les conditions d'usage (case à cocher) avant d'activer la TV."

    const val ARTICLE_X = "Article X — Licence d'usage et propriété. CastBridge et CastBridge-TV (les « Logiciels ») sont concédés sous licence, non vendus. L'éditeur en reste seul propriétaire, " +
        "ainsi que des contenus qu'il fournit. L'abonnement, y compris « illimité » ou « permanent », confère un droit d'usage personnel, non exclusif et non transférable sur l'appareil activé, " +
        "pour la durée de la clé d'activation ; il ne transfère aucun droit de propriété sur les Logiciels ni sur les contenus loués, qui restent soumis à leurs propres durées."

    const val ARTICLE_Y = "Article Y — Assistance et contrôle à distance. L'utilisateur reconnaît et accepte que l'éditeur, directement ou par des experts qu'il désigne, puisse se connecter à distance, " +
        "de manière sécurisée, à l'appareil sur lequel CastBridge-TV est installé, lorsque celui-ci dispose d'une connexion à Internet (directe ou par l'intermédiaire de l'application CastBridge du téléphone), " +
        "afin de : (a) fournir l'assistance et la maintenance ; (b) corriger les défauts et mettre à jour les Logiciels ; (c) vérifier le respect des présentes conditions d'usage (licence, durée, nombre d'appareils, " +
        "usages interdits) ; (d) suspendre ou retirer l'accès en cas de manquement. Cet accès n'exige pas de validation à chaque session. L'éditeur s'interdit d'accéder au contenu personnel de l'utilisateur " +
        "(photos, vidéos, documents, messages) autrement que dans la mesure strictement nécessaire à l'assistance demandée par l'utilisateur ou à la constatation d'un manquement, tient un journal des accès, " +
        "et ne communique pas ces données à des tiers sauf obligation légale. L'utilisateur peut consulter dans l'application (« À propos » > « Assistance à distance ») l'état du dispositif."

    const val ARTICLE_Z = "Article Z — Données. Les informations traitées à cette occasion sont décrites dans la politique de confidentialité de CastBridge (écran « CastBridge et vos données »), " +
        "qui mentionne l'accès à distance."

    const val MUST_ACCEPT_ON_TV = "Les conditions d'usage ne sont pas encore acceptées sur la TV : sur l'écran d'activation de CastBridge-TV, cochez « J'ai lu et j'accepte les conditions d'usage », puis envoyez la clé de nouveau."
    const val PHONE_NOTE = "Avant d'activer, lisez les conditions d'usage ci-dessous. CastBridge-TV vous demandera de les accepter sur son écran (case à cocher) : sans cela, la clé n'est pas prise en compte."

    val TEXT: String get() = "$ARTICLE_X\n\n$ARTICLE_Y\n\n$ARTICLE_Z"

    /** The paragraph added to the privacy screen « CastBridge et vos données ». */
    const val PRIVACY_TITLE = "Assistance à distance"
    const val PRIVACY = "Quand cette TV a accès à Internet (directement, ou par le téléphone qui partage sa connexion), CastBridge-TV maintient une connexion de maintenance sécurisée avec le serveur de " +
        "l'éditeur, qui peut l'utiliser pour l'assistance, la maintenance et la vérification du respect des conditions d'usage. Elle ne démarre qu'après votre acceptation des conditions d'usage. " +
        "Les connexions sont notées dans un journal que vous pouvez lire sur la TV (À propos > Assistance à distance). Le contenu de vos fichiers n'est pas consulté en dehors de l'assistance que vous demandez " +
        "ou de la constatation d'un manquement."

    fun nowText(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))
}

/** The acceptance kept on the TV: which text ([version]), when ([acceptedAt], epoch ms) and for which installation ([installation] = the device code, so a copied file means nothing elsewhere). */
data class TermsAcceptance(val version: String, val acceptedAt: Long, val installation: String) {
    fun validFor(currentVersion: String, installationId: String) = version == currentVersion && installation == installationId && acceptedAt > 0

    fun toJson(): String = JsonLite.write(linkedMapOf("version" to version, "acceptedAt" to acceptedAt, "installation" to installation)) + "\n"

    companion object {
        fun parse(text: String): TermsAcceptance? = runCatching {
            val m = JsonLite.obj(text)
            TermsAcceptance(m["version"] as String, (m["acceptedAt"] as Number).toLong(), m["installation"] as String)
        }.getOrNull()
    }
}

/** Acceptance file written through [SafeFile] (atomic, `.bak`). [installationId] is read lazily (the device code is known once the activation center started). */
class TermsStore(private val file: File, private val installationId: () -> String, private val now: () -> Long = System::currentTimeMillis, private val version: String = TunnelTerms.VERSION) {
    @Volatile private var cached: TermsAcceptance? = null
    @Volatile private var loaded = false

    @Synchronized fun current(): TermsAcceptance? {
        if (!loaded) { cached = SafeFile.read(file) { TermsAcceptance.parse(it) != null }?.let { TermsAcceptance.parse(it.text) }; loaded = true }
        return cached
    }

    /** True when THIS version was accepted on THIS installation. */
    fun accepted(): Boolean = current()?.validFor(version, installationId()) == true

    /** Records the acceptance (idempotent for the same version). Throws when the file cannot be written: the tunnel then stays off. */
    @Synchronized fun accept(): TermsAcceptance {
        current()?.takeIf { it.validFor(version, installationId()) }?.let { return it }
        val a = TermsAcceptance(version, now(), installationId())
        SafeFile.write(file, a.toJson()) { TermsAcceptance.parse(it) != null }
        cached = a; loaded = true
        return a
    }

    @Synchronized fun withdraw() { file.delete(); SafeFile.bak(file).delete(); cached = null; loaded = true }
}
