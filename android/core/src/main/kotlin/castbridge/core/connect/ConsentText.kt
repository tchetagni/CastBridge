package castbridge.core.connect

/**
 * The information screen shown at the first launch (TV and phone), taken from docs/TELEMETRY.md § 7 (law n° 2024/017 of
 * Cameroon on personal data; to be reviewed by a lawyer). Changing the text = raising [VERSION]: the screen is shown again.
 */
object ConsentText {
    const val VERSION = "2026-11"

    /** Data controller and contact: to be completed by the coordinator (docs/TELEMETRY.md § 7); null = generic wording. */
    val CONTROLLER: String? = null
    val CONTACT: String? = null

    const val TITLE = "CastBridge et vos données"

    const val INTRO = "CastBridge envoie à son serveur des informations techniques sur cet appareil pour vous proposer les mises à jour " +
        "et corriger les problèmes. Nous ne collectons jamais le contenu de vos fichiers, les noms ou titres de vos vidéos, vos contacts, " +
        "vos mots de passe ni votre position."

    const val ESSENTIAL_TITLE = "Essentiel — toujours (nécessaire au fonctionnement)"
    const val ESSENTIAL = "Un identifiant technique de l'appareil (tiré au hasard, et une empreinte non réversible de l'identifiant Android), " +
        "le modèle et le système de l'appareil, les versions installées, l'espace de stockage, les erreurs et plantages, le résultat " +
        "des mises à jour, les signalements d'erreur de contenu (« Signaler une erreur ») que vous envoyez vous-même : l'identifiant de la question ou de la leçon, le motif et un court texte facultatif — n'y écrivez aucune donnée personnelle. Adresse IP : utilisée pour déterminer le pays approximatif, effacée après 30 jours."

    const val USAGE_TITLE = "Statistiques d'usage — seulement si vous l'acceptez"
    const val USAGE = "Les fonctionnalités utilisées et le temps passé, les envois vers la TV et les lectures (durées, formats, réussite), " +
        "les parties de quiz et d'échecs, les leçons et exercices d'« Apprendre » (pour chaque question ou exercice : combien de fois il a été vu, le taux de réussite et le temps moyen — jamais son texte —, afin de repérer les contenus à corriger), les téléchargements et l'usage de la passerelle " +
        "Bluetooth. Elles nous servent à savoir ce qui est utile et à améliorer l'application. Vous pouvez changer d'avis à tout moment " +
        "dans les Réglages."

    const val DURATION = "Durée : 13 mois pour le détail, puis des statistiques globales ; les appareils inactifs depuis un an sont effacés."

    val RIGHTS: String get() = "Vos droits : consulter les données de cet appareil (« Mes données »), les faire effacer (« Effacer mes données »), " +
        "retirer votre accord, dans Réglages › Confidentialité. " +
        (CONTROLLER?.let { "Responsable du traitement : $it. " } ?: "Responsable du traitement : l'éditeur de CastBridge. ") +
        (CONTACT?.let { "Contact : $it. " } ?: "") +
        "Vous pouvez aussi saisir l'autorité de protection des données à caractère personnel compétente."

    const val ACCEPT = "Accepter les statistiques d'usage"
    const val ESSENTIAL_ONLY = "Seulement l'essentiel"

    /** (title or null, paragraph) in display order. */
    val paragraphs: List<Pair<String?, String>>
        get() = listOf(null to INTRO, ESSENTIAL_TITLE to ESSENTIAL, USAGE_TITLE to USAGE, null to DURATION, null to RIGHTS)
}
