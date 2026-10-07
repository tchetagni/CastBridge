package castbridge.core.owner

/**
 * La demande d'appareil d'une TV sous ses deux formes de texte (docs/coordination/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, ACT-F4 amendée le 2026-10-07). Pur : aucun Android, aucun fil.
 *
 *  - [complete] : `code=`, `k=`, `factor=…`, `install=x25519|…` quand la TV a sa clé d'installation, `install_sig=ed25519|…` : la demande que la TV rend au téléphone qui détient son code
 *    (route verrouillée `GET /api/activation/device-request`), que le téléphone partage et que lisent les outils du propriétaire (`DeviceRequest.parse`). `install=` est la clé PUBLIQUE X25519 de
 *    l'installation : rien de secret, et une clé d'essai en enveloppe v2 l'exige. Elle est ABSENTE (jamais vide, jamais inventée) quand la TV n'en a pas encore, ou sur une TV plus ancienne.
 *  - [forServer] : la même sans `install=` (que le serveur ne lit pas : il la tolère et l'ignore) ; `install_sig=` reste, le serveur la signe dans l'activation de production. Réservée à l'envoi au
 *    serveur (voie B2, « Demander l'activation à CastBridge », éteinte aujourd'hui) ; l'équivalent sur le modèle de la demande lue est `TvDeviceRequest.serverText()`.
 *
 * Les deux sont RECONSTRUITES depuis la demande analysée ([OwnerFrames.parseDeviceInfo]), jamais recopiées : un texte illisible (ligne mal formée, code absent, `install=` doublé ou mal écrit) donne null,
 * les lignes inconnues qu'une TV plus récente pourrait ajouter, l'empreinte lisible facultative, les fins de ligne CRLF et les lignes vides ne sortent jamais.
 */
object DeviceRequestText {
    /** La demande complète reconstruite, ou null si [text] n'est pas une demande d'appareil lisible. */
    fun complete(text: String): String? = OwnerFrames.parseDeviceInfo(text)?.let { OwnerFrames.deviceInfo(it.code, it.fp, it.installPub, it.installSig) }

    /** La demande pour le serveur : [complete] sans la ligne `install=` ; null si [text] n'est pas une demande d'appareil lisible. */
    fun forServer(text: String): String? = OwnerFrames.parseDeviceInfo(text)?.let { OwnerFrames.deviceInfo(it.code, it.fp, null, it.installSig) }
}
