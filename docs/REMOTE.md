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

## Télécommande par Bluetooth seulement (trois voies)

Pour une TV dont le Wi-Fi est défaillant. L'option **« Bluetooth exclusivement »** (écran « Ma TV · voies Bluetooth », menu ⋮ de la télécommande) n'utilise que les voies ci-dessous et **jamais le Wi-Fi** ; il faut une TV appairée choisie comme secours Bluetooth.

| Voie | Ce qu'elle pilote | Prérequis | Statut |
|---|---|---|---|
| **A · CastBridge par Bluetooth** (CBTR) | écrans de CastBridge-TV, volume, muet, touches multimédia | TV appairée, CastBridge-TV ouverte, aucune adresse IP nécessaire | existante, fiabilisée |
| **B · Relais vers le service du fabricant** | flèches, OK, Retour, Accueil, menu, médias, volume **dans les autres applications**, sans accessibilité | TV « marque blanche » CVTE/Amlogic avec le service sur le port 8125 (`docs/REMOTE-VENDOR-CVTE.md`) | à valider sur matériel |
| **C · Téléphone = clavier/télécommande Bluetooth** (HID) | flèches, Entrée, Échap (= Retour), chiffres, Accueil, Menu, médias, volume, CH± ; aucune app sur la TV | Android 9+, TV qui accepte un clavier Bluetooth | **désactivée par défaut** tant qu'un test ne l'a pas confirmée |

- **A** : le téléphone affiche « Bluetooth seulement » (ou « Bluetooth exclusivement ») ; la liaison se rétablit seule après une coupure (`RemoteSession`) ; les touches qu'aucune voie ne peut porter sont masquées (la place reste).
- **B** : `téléphone --Bluetooth--> CastBridge-TV --ws://127.0.0.1:8125--> service système`. `RemoteHub` choisit la voie de chaque touche (`KeyRouting`) : volume = AudioManager, puis le relais si la TV refuse (volume fixe) ; écran CastBridge au premier plan = CastBridge d'abord, le relais seulement si l'écran n'a pas pris la touche (jamais deux envois) ; CastBridge pas au premier plan = relais, puis accessibilité, puis session multimédia ; « Accueil » reste l'accueil de CastBridge sauf demande « toute la TV ». Le relais n'est joignable que par l'API de télécommande déjà authentifiée (PIN ou lien Bluetooth de confiance), limité à 30 touches/s, ne cible que `127.0.0.1`, n'encode que la liste blanche `RemoteKey` (jamais POWER/veille) et tient un journal sans secret. `GET /api/remote/state` : `"vendor":{"available":bool,"state":"ABSENT|CONNECTING|READY|ERROR"}`. Test TV : Options › « Tester le relais Bluetooth » (volume + puis −).
- **C** : `BluetoothHidDevice` (`BtHidRemote`), descripteur clavier (rapport 1) + « Consumer Control » (rapport 2) construits et testés dans `core` (`HidRemote`) ; appui/relâchement idempotents. Si la TV refuse la connexion, l'état affiche « non pris en charge ». N'envoie rien tant que « Activer cette voie » n'est pas coché **et** que le test « Avez-vous vu le volume changer ? » n'a pas été confirmé ; utilisée seulement quand la liaison vers CastBridge-TV est absente.
- **Diagnostic** : bouton « Copier le diagnostic » (états des trois voies, option, version ; adresses Bluetooth masquées, aucune adresse IP, aucun code).

### Limites et risques
- B dépend d'un protocole non documenté du fabricant (peut changer) ; il n'existe que sur certaines TV.
- **Risque réseau** : ce service système n'exige **aucune authentification** sur le réseau local : toute personne sur le même Wi-Fi peut piloter la TV avec ce protocole. CastBridge ne l'expose pas (boucle locale seulement) mais ne peut pas le fermer.
- C : beaucoup de TV ignorent un clavier Bluetooth venant d'un téléphone ; pas de texte libre (seulement les touches de la liste). Un seul téléphone/TV connecté à la fois.
- Aucune des trois voies n'allume la TV ni ne change de source.
