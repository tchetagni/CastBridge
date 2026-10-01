# Télécommande des TV « marque blanche » CVTE / Amlogic : protocole observé

Notes d'interopérabilité écrites avec nos mots, à partir de l'observation de la TV de référence de l'owner et de l'analyse
locale d'une app de télécommande installée sur son téléphone. Aucun code ni ressource du fabricant n'est reproduit dans ce
dépôt (le code décompilé ne doit jamais y entrer). Statut : **vérifié sur matériel** (2026-10-01, touches de volume).

## Découverte (mDNS / DNS-SD)
- Type de service `_share._tcp` : la TV annonce une instance (vue : « BytelloRemoteServer ») ; le type `_maxhubmobile._tcp`
  annonce le service de diffusion d'écran (vue : « TV-… »).
- Attributs TXT lus par l'app du fabricant : `device_mac`, `device_name`, `device_code`, `device_ip`, `screen_width`,
  `screen_height`, `http_port`, `websocket_port`, `config` (JSON de capacités).
- Si l'adresse de l'instance manque, l'app retombe sur `device_ip`.

## Transport
- WebSocket (RFC 6455) en clair vers `ws://<ip>:<websocket_port>` ; port par défaut **8125**.
- **Aucune authentification n'est exigée** sur la TV de référence : la poignée de main (`101 Switching`) réussit depuis
  n'importe quel appareil du réseau local. Conséquence de sécurité à dire à l'owner : toute personne sur le même Wi-Fi peut
  piloter cette TV avec ce protocole. Un « code de connexion » existe côté app, mais il ne fait qu'encoder l'adresse IP
  (préfixe 10./172./192.168.) et un indice de port avec une somme de contrôle : ce n'est pas un secret.
- À la connexion la TV pousse une trame **texte JSON** : `{"status":500,"data":{"code":…,"config":{AppManager, AppStore,
  Browser, Camera, Gesture, GoogleVoice, Screenshots, Video, Voice : booléens},"height":720,"width":1280,"httpPort":"9909",
  "ip":…,"name":…,"port":8125,"websocketPort":"8125"},"msg":"successful"}` (le `status` 500 est celui observé avec
  `msg:"successful"` : ne pas le traiter comme une erreur).
- Le port `httpPort` (9909) sert une liste de fichiers (icônes d'applications) : inutile pour la télécommande.

## Messages (trames binaires, protobuf)
Message unique, champs :
| n° | type | rôle |
|---|---|---|
| 1 | int32 | `type` de l'événement |
| 2 | string | `action` (texte) |
| 3 | répété, sous-message { 1 : float x, 2 : float y } | pointeurs (souris / tactile, coordonnées en pixels de l'écran de la TV) |
| 4 | bytes | données (voix) |
| 5 | sous-message | réservé / non étudié |

Types vus : **1 = événement de touche** (`action` = code de touche Android en décimal, ex. `"24"` volume +, `"25"` volume −,
`"19"` haut, `"66"` entrée, `"4"` retour, `"3"` accueil) ; **4 = clic de souris** ; **9 = contenu vocal** (octets audio dans le
champ 4). D'autres types existent (déplacement du curseur, molette, jeu) : à relever par observation.
Encodage minimal d'une touche : `08 01 12 <len> "<code>"` envoyé dans une trame WebSocket binaire (client → serveur : masquée).

## Vérification sur la TV de référence (Amlogic « SMART_TV », Android 14)
- Poignée de main WebSocket sur le port 8125 : **réussie**, trame d'information reçue (nom, capacités, 1280×720).
- Envoi de deux touches (volume + puis volume −, effet net nul) : acceptées par la TV (elle répond par une trame JSON) ;
  **effet visible confirmé par l'owner** (la barre de volume de la TV a bougé) : le protocole est donc validé de bout en bout sur la TV de référence.

## Limites et prudence
- Protocole propre au fabricant, non documenté, susceptible de changer : à isoler derrière une « stratégie » remplaçable,
  avec détection de version/échec et repli sur une autre stratégie.
- Ne jamais envoyer de commande à un appareil inconnu : l'utilisateur choisit la TV, un test réversible (volume +/−) demande
  confirmation visuelle avant d'enregistrer la stratégie comme « fonctionne ».

## Implémentation dans CastBridge (2026-10-01)
Stratégie `cvte` (`core/…/remote/smart/Strategies.kt`, statut **stable**, volume vérifié) : encodeur protobuf écrit à la main (`Pb`, sans dépendance) ; `CvteMessage.key(code)` produit exactement `08 01 12 <len> "<code>"` (tests : `24`, `25`, `4`, `23`) ; client WebSocket minimal (`WsClient`, trames masquées, ping→pong) ; lecture du JSON d'information (`CvteInfo`, le `status` 500 n'est **pas** une erreur) ; point d'accès depuis un enregistrement DNS-SD `_share._tcp` (`websocket_port`, repli sur `device_ip`, port 8125 par défaut) ; **reconnexion** silencieuse au premier envoi après une coupure (la TV ferme les liaisons inactives). L'app avertit l'owner que la TV **n'exige aucune authentification** (risque réseau).
Types d'événements : seuls les types 1 (touche) et 4 (clic, décodage seulement) sont codés ; **aucun autre type n'a été confirmé** (curseur, molette, voix, jeu : toujours à relever par observation sur la TV).
