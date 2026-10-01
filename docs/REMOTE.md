# Télécommande du téléphone

Le téléphone pilote CastBridge TV comme sa télécommande : pavé directionnel, OK, Retour, Accueil, Menu, Info, lecture/pause/stop, ±10 s, précédent/suivant, volume/muet, CH±, chiffres, clavier (texte tapé dans le champ sélectionné de la TV), pavé tactile (glisser = flèches, toucher = OK, appui long = OK long).

- **Téléphone** : onglet « CastBridge TV » › tuile **Télécommande**, bouton télécommande de la carte « Sur la TV » et de la feuille de lecture, tuile Paramètres rapides « Télécommande TV » (facultatif, à ajouter soi-même au panneau). Pendant que l'écran est ouvert, les **boutons de volume du téléphone** règlent la TV (option du menu ⋮). Vibration à chaque touche (option).
- **TV** : tuile d'accueil « Télécommande » = écran d'aide, avec l'activation pas à pas du mode « toute la TV ».

## Ce qu'Android permet, et ce qu'il ne permet pas

Une application normale **ne peut pas injecter de touches dans les autres applications ni dans le système** (il faut la permission système `INJECT_EVENTS`). D'où deux niveaux :

1. **CastBridge (toujours disponible)** : les touches arrivent dans l'écran CastBridge au premier plan (accueil, bibliothèque, lecteur, quiz, échecs, téléchargements, dialogues et menus compris). La TV les donne à la fenêtre de l'application qui a le focus (`View.dispatchKeyEvent` sur le fil principal), puis fait elle-même le déplacement du focus que le pipeline d'entrée d'Android ferait pour une flèche que personne n'a consommée. Rien n'est injecté ailleurs.
   - **Accueil** = l'accueil de CastBridge (quitte la vidéo, le quiz, les échecs…) ; il ouvre aussi CastBridge quand une autre app est à l'écran (comme la lecture à distance : direct, ou par notification si Android bloque).
   - **Volume / muet** : `AudioManager` (flux multimédia), partout, même hors de CastBridge. Une TV à volume fixe (HDMI-CEC, ampli) refuse : le téléphone l'affiche.
   - **Touches multimédia** hors de CastBridge : envoyées à la session multimédia active (`AudioManager.dispatchMediaKeyEvent`), sans permission ; l'app qui joue décide.
   - Le clavier du téléphone écrit dans le champ de saisie qui a le focus (recherche, lien de téléchargement, nom de joueur…). Le clavier virtuel de la TV peut rester affiché : les flèches du téléphone vont à l'écran, pas au clavier virtuel.
2. **Toute la TV (facultatif)** : service d'accessibilité **« CastBridge Télécommande »**, à activer par l'utilisateur dans Réglages › Accessibilité (l'écran d'aide y mène). Il permet : Retour, Accueil de la TV, apps récentes, notifications, réglages rapides, menu marche/arrêt (`performGlobalAction`) ; flèches et OK dans les autres applications (Android 13+ : actions D-pad du système ; avant : recherche du focus par `AccessibilityNodeInfo`, `ACTION_FOCUS`, `ACTION_CLICK`/`ACTION_LONG_CLICK`, défilement en bout de liste) ; texte dans le champ sélectionné (`ACTION_SET_TEXT`). Le service **n'agit que sur ordre d'un téléphone authentifié par le code**, ignore tous les événements d'accessibilité, ne lit rien de lui-même, ne garde et n'envoie rien. Le téléphone indique s'il est actif (interrupteur « Piloter toute la TV »).

### Limites

- Pas d'allumage/extinction de la TV, pas de changement de source, pas de HDMI-CEC : réservé au système.
- MENU, chiffres, CH±, Info, sous-titres ne peuvent pas être envoyés aux autres applications (le téléphone affiche pourquoi).
- Certains lanceurs ou applications ignorent l'accessibilité ; sur certaines TV l'écran d'accessibilité est caché ou absent (alors seul le mode CastBridge existe).
- Android désactive le service d'accessibilité si l'application est **arrêtée de force** (Réglages › Applications › Forcer l'arrêt) : il faut alors le réactiver. Une mise à jour normale le garde.
- « Apps récentes » est accepté mais peut n'avoir aucun effet visible sur les lanceurs Google TV.

## Protocole (API de la TV, port 8765, code PIN obligatoire)

| Route | Rôle |
|---|---|
| `POST /api/remote/key?code=DPAD_UP[&action=press\|down\|up\|long][&repeat=n][&target=auto\|app\|system][&sid=…&seq=n]` | une touche de la liste blanche |
| `POST /api/remote/text?value=…[&mode=insert\|replace\|clear]` | texte (500 caractères max, pas de caractères de contrôle) |
| `POST /api/remote/pointer?dx=…&dy=…[&step=60]` ou `?tap=1` | pavé tactile : un glissement devient des flèches (10 max) |
| `POST /api/remote/global?action=BACK\|HOME\|RECENTS\|NOTIFICATIONS\|QUICK_SETTINGS\|POWER_DIALOG` | action système (mode « toute la TV ») |
| `GET /api/remote/state` | écran au premier plan, champ de saisie sélectionné, accessibilité activée/connectée, volume, muet |
| `POST /api/remote/ping` | maintien de la liaison / mesure de latence |
| `POST /api/remote/system/setup` | affiche l'aide « toute la TV » sur la TV |

Liste blanche (`castbridge.core.remote.RemoteKey`) : `DPAD_UP/DOWN/LEFT/RIGHT/CENTER` (alias `UP`, `OK`…), `BACK`, `MENU`, `HOME`, `PLAY_PAUSE`, `PLAY`, `PAUSE`, `STOP`, `NEXT`, `PREVIOUS`, `REWIND`, `FAST_FORWARD`, `VOLUME_UP/DOWN/MUTE`, `CHANNEL_UP/DOWN`, `INFO`, `CAPTIONS`, `AUDIO_TRACK`, `GUIDE`, `ENTER`, `DEL`, `0`–`9`. Tout le reste (POWER, SLEEP, codes numériques…) : 400.

- **Appui maintenu** : `action=down` au toucher, `down&repeat=1,2…` toutes les 90 ms après 400 ms pour les touches qui se répètent (flèches, volume, ±10 s, CH, effacer), `up` au lâcher. Un OK maintenu devient un appui long sur la TV. Si la liaison tombe pendant un appui, la TV relâche la touche d'elle-même après 1,2 s sans nouvelles.
- **Pas de doublon, pas de perte** : chaque événement porte `sid` (session du téléphone) et `seq` ; le téléphone renvoie après une reconnexion ce qui n'a pas reçu de réponse, la TV ignore un numéro déjà appliqué. Un appui vieux de plus de 3 s n'est pas rejoué (un « OK » en retard serait une surprise) ; un relâchement l'est toujours.
- **Latence** : une seule connexion TCP ouverte (HTTP/1.1 keep-alive, `TCP_NODELAY`), un ping toutes les 3 s la garde chaude (la TV ferme les connexions inactives après 15 s). Côté TV, la touche passe devant le travail d'affichage en file (dans l'ordre) et la réponse n'attend pas plus de 150 ms un écran occupé.
- **Sans réseau commun** : secours **Bluetooth** (TV appairée, choisi dans « Choisir la TV »). Le service Bluetooth de fichiers de la TV accepte `"CBTR" + code`, puis une requête par ligne (`POST key?code=…\n` → `200 {…}\n`), mêmes routes. Pendant une session Bluetooth de télécommande, les envois de fichiers par Bluetooth attendent. Sans Wi-Fi ni secours choisi, le téléphone affiche « Télécommande indisponible : la TV ne répond pas sur le réseau… ».

## Télécommande en arrière-plan (téléphone)
`RemoteService` (service de premier plan `connectedDevice`) garde le lien de `RemoteController` quand l'application n'est plus au premier plan ou que l'écran est éteint :
- notification persistante : Vol −, Muet, Vol +, Lecture/pause, Arrêter ; toucher la notification rouvre la télécommande ;
- les boutons de volume du téléphone pilotent la TV (session média à volume distant), sauf si « Boutons de volume du téléphone → TV » est décoché ;
- option « Garder la télécommande en arrière-plan » (menu de la télécommande, activée par défaut). Désactivée, ou après « Arrêter », le lien est fermé en quittant l'écran.
Limite : tant que le service tourne, les boutons de volume du téléphone ne règlent plus le volume du téléphone.


## Télécommande « intelligente » : plusieurs stratégies (téléphone CastBridge)

But : piloter le plus de TV possible, pas seulement CastBridge-TV. Écran **Ma TV** (menu ⋮ de la télécommande › « Ma TV ») : l'utilisateur **choisit la TV** (adresse), CastBridge l'**identifie sans rien lui envoyer**, puis essaie les stratégies dans l'ordre. Code : `core/…/remote/smart/` (pur JVM, testé) et, côté téléphone, `SmartRemote`, `MyTvActivity`, `SmartPlatform` (module `:sender`).

### Identification (passive)
`TvIdentifier` combine des indices indépendants (probabilité cumulée, 0–1) : annonces mDNS/DNS-SD (type et TXT), réponse **SSDP** (un seul `M-SEARCH`, réponse de cette TV seulement) et `description.xml` UPnP (fabricant, modèle, services), **ports ouverts** (connexions TCP seules, ~14 ports, une par une, 400 ms), signatures HTTP, nom Bluetooth, préfixe MAC (OUI : petit échantillon, indice **faible**). Résultat : `TvFingerprint` (fabricant, famille, modèle probable, confiance, preuves) + **stratégies candidates classées**. Sous 0,3 : « inconnu ». Un indice « Android TV » seul ne l'emporte pas sur un fabricant (Sony, Philips… tournent sous Android TV). Sur Android 10+, l'adresse MAC d'un autre appareil n'est pas lisible : l'OUI ne sert que si une source la fournit.

### Ordre d'essai (orchestrateur)
1. la stratégie **mémorisée et confirmée** pour cette TV (date conservée) ; 2. **CastBridge-TV natif** dès que la TV est détectée (HTTP/Bluetooth + jeton, chemin existant `RemoteController`) ; 3. les candidates de l'empreinte ; 4. le reste dans l'ordre par défaut (CVTE, Roku, Samsung, LG, Sony, Android TV, Philips, Vizio, DLNA, Bluetooth HID, infrarouge, app du fabricant). Chaque stratégie commence par un `probe()` **non intrusif** (connexion TCP / lecture seule) ; une stratégie injoignable n'est même pas contactée.
- **Bascule** : si l'envoi échoue, une reconnexion de la même stratégie, puis la suivante (30 s de répit pour celles qui viennent d'échouer) ; **retour** à la meilleure toutes les 60 s ; reprise après coupure (CVTE, Samsung, LG se reconnectent seuls).
- **Mémoire** : une stratégie n'est enregistrée qu'après le test « Avez-vous vu le volume changer ? » (**Tester** = volume + puis volume −, jamais laissé en hausse) ou quand la TV accuse elle-même réception (Roku, DLNA, Sony, CastBridge). Répondre « Non » l'écarte.
- **Limites de débit** : 15 touches/s au plus. **Journal** sans secret (adresses réduites `192.168.1.x`, MAC tronquées, jetons/PIN/PSK masqués) ; bouton **Copier le diagnostic**.
- Choix manuel d'une stratégie (écran Ma TV) ; les **expérimentales** sont désactivées par défaut (interrupteur).

### Stratégies, statut et limites par marque
| Stratégie | Statut | Protocole (source) | Limites |
|---|---|---|---|
| CastBridge-TV | stable | existant (HTTP 8765 / Bluetooth, jeton) | voir plus haut |
| **CVTE / Amlogic** « marque blanche » | stable (volume **vérifié sur la TV de référence**) | WebSocket 8125 + protobuf écrit à la main, touches Android en décimal ; mDNS `_share._tcp` (`docs/REMOTE-VENDOR-CVTE.md`) | **aucune authentification** (risque réseau signalé dans Ma TV) ; seules les touches de volume vérifiées ; pas de texte/pointeur |
| **Roku** | stable | ECP HTTP 8060 (doc Roku) : `/keypress/<Touche>`, texte `Lit_`, `device-info` | volume/chaînes seulement sur Roku TV ; pas de chiffres |
| **DLNA / UPnP** | stable | SOAP AVTransport + RenderingControl (standards UPnP) | lecture/pause/stop/suivant/précédent et volume/muet seulement ; pas de flèches/OK |
| **Sony BRAVIA** | stable (non vérifiée sur matériel) | IRCC SOAP + en-tête `X-Auth-PSK` (doc Sony) | « Contrôle IP » + PSK à régler sur la TV ; pas de texte |
| **Samsung Tizen** | expérimentale | WebSocket 8002 (wss) / 8001, `ms.remote.control`, jeton accordé une fois sur la TV (doc communautaire) | acceptation à l'écran ; certificat auto-signé accepté pour cette TV seule |
| **LG webOS** | expérimentale | SSAP WebSocket 3000/3001, `register` + clé client ; boutons via la prise « pointeur » | acceptation à l'écran ; pas de texte |
| **Philips JointSpace** | expérimentale | API v1 HTTP 1925 (`/1/input/key`) | **v6 (appairage signé) non gérée** : TV récentes exclues |
| **Vizio SmartCast** | expérimentale | HTTPS 7345/9000, appairage par code, `PUT /key_command/` | certificat auto-signé accepté pour cette TV seule |
| **Android TV / Google TV (Remote v2)** | expérimentale (squelette) | TLS 6467 (appairage code à 6 caractères) / 6466, protobuf à préfixe de longueur ; encodage et secret SHA-256 testés | l'identité TLS (certificat client) doit venir de la plateforme : **non fournie dans cette version**, la stratégie le dit au lieu de simuler |
| **Bluetooth HID** | expérimentale | `BluetoothHidDevice` (Android 9+), clavier + commandes multimédia, descripteur dans `HidReports` | la TV doit accepter un clavier ; sans retour d'information |
| **Infrarouge** | expérimentale | `ConsumerIrManager` ; motifs NEC / Samsung32 / Sony SIRC-12 **générés par programme** (spécifications publiques) | Samsung, LG, Sony ; codes issus des tables communautaires publiques (LIRC / documentation IRremote), à valider ; téléphone à émetteur IR seulement |
| **App du fabricant** | stable | ouvre l'app de télécommande installée (Samsung SmartThings, LG ThinQ, Roku, Sony, Google Home) | n'envoie aucune touche ; noms de paquets à vérifier sur un vrai téléphone |

**Origine des codes** : aucun protocole n'est copié d'une application propriétaire ; formats tirés des documentations publiques (Roku ECP, UPnP, Sony IRCC) ou communautaires (samsungctl, pywebostv, pyvizio, notes Android TV Remote v2), et des spécifications IR publiques. Les vecteurs de test (octets IRCC, protobuf, NEC/Samsung32) sont dans `core/src/test/…/smart/`.

### Sécurité et vie privée
L'utilisateur choisit la TV avant tout envoi ; aucun balayage du réseau (ports et SSDP **uniquement vers cette TV**, une stratégie ne parle jamais à une autre adresse) ; jetons d'appairage (Samsung, LG, Vizio, clé PSK Sony) dans les préférences privées de l'app (`castbridge_smart`), jamais journalisés ni dans le diagnostic ; aucune télémétrie des adresses ni des codes. Les certificats auto-signés des TV ne sont acceptés que pour l'adresse de la TV choisie.

### À valider sur du vrai matériel
Tout sauf le volume CVTE : Roku, DLNA, Sony, Samsung, LG, Vizio, Philips, Android TV, Bluetooth HID, infrarouge ; la compilation du module `:sender` (voir `docs/HANDOFF.md`).
