'use strict';
// Constantes partagées par le service CastBridge-TV Web (aucune dépendance).

module.exports = {
  APP_ID: 'com.sticm.castbridge.tv',
  SERVICE_ID: 'com.sticm.castbridge.tv.service',
  /** Version de CastBridge-TV Web (version.properties : tvweb.versionName). */
  VERSION: '0.1.0-beta',
  /** Niveau de l'API HTTP imitée : ReceiverServer.VERSION de la CastBridge-TV Android (clé « v » de /api/hello). */
  API_VERSION: '0.7',
  /** Port de l'API : ReceiverServer.PORT. */
  HTTP_PORT: 8765,
  /** Type DNS-SD : ReceiverServer.SERVICE_TYPE (« _castbridge._tcp. »). */
  MDNS_SERVICE: '_castbridge._tcp',
  /** Nom d'instance mDNS = nom de la TV affiché par le téléphone. */
  INSTANCE: 'CastBridge-TV Web',
  /** Attributs TXT annoncés par TvService.register() : role=receiver, v=0.2 (jamais locked=1 : pas d'activation ici). */
  TXT: { role: 'receiver', v: '0.2' },
  /** Titre par défaut quand le téléphone n'en envoie pas de valide (ReceiverServer : « Depuis le téléphone »). */
  DEFAULT_TITLE: 'Depuis le téléphone',
  /** Texte du 409 que le téléphone affiche tel quel (CastSession.live lit la clé « message »). */
  FOREGROUND_MESSAGE: 'La TV doit être au premier plan : ouvrez CastBridge-TV Web sur la TV.',
  /** Au-delà de ce délai sans signe de vie de l'app, la TV est dite absente (lecteur au repos, lecture refusée). */
  PRESENCE_MS: 10000,
  MAX_URL: 4096,
  MAX_TITLE: 200
};
