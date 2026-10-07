# Télécommande du téléphone

Le téléphone pilote CastBridge TV comme sa télécommande : pavé directionnel, OK, Retour, Accueil, Menu, Info, lecture/pause/stop, ±10 s, précédent/suivant, volume/muet, CH±, chiffres, clavier (texte tapé dans le champ sélectionné de la TV), pavé tactile (glisser = flèches, toucher = OK, appui long = OK long).

- **Téléphone** : onglet « CastBridge TV » › tuile **Télécommande**, bouton télécommande de la carte « Sur la TV » et de la feuille de lecture, tuile Paramètres rapides « Télécommande TV » (facultatif, à ajouter soi-même au panneau). Pendant que l'écran est ouvert, les **boutons de volume du téléphone** règlent la TV (option du menu ⋮). Vibration à chaque touche (option). Bouton **« Ouvrir sur la TV »**, touche **« TV »** et raccourci de l'icône : voir « Ouvrir CastBridge-TV depuis le téléphone » plus bas.
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
| `POST /api/tv/open[?screen=home\|library\|player\|games\|quiz]` (corps facultatif `{"screen":"library"}`) | **ouvre CastBridge-TV devant les autres applications** (section ci-dessous) ; PIN ou jeton de téléphone de confiance ; ouverte aussi à l'édition d'essai |
| `POST open[?screen=…]` sur le canal Bluetooth CBTR (= `POST /api/remote/open`) | la même action sans réseau commun |

Liste blanche (`castbridge.core.remote.RemoteKey`) : `DPAD_UP/DOWN/LEFT/RIGHT/CENTER` (alias `UP`, `OK`…), `BACK`, `MENU`, `HOME`, `PLAY_PAUSE`, `PLAY`, `PAUSE`, `STOP`, `NEXT`, `PREVIOUS`, `REWIND`, `FAST_FORWARD`, `VOLUME_UP/DOWN/MUTE`, `CHANNEL_UP/DOWN`, `INFO`, `CAPTIONS`, `AUDIO_TRACK`, `GUIDE`, `ENTER`, `DEL`, `0`–`9`. Tout le reste (POWER, SLEEP, codes numériques…) : 400.

- **Appui maintenu** : `action=down` au toucher, `down&repeat=1,2…` toutes les 90 ms après 400 ms pour les touches qui se répètent (flèches, volume, ±10 s, CH, effacer), `up` au lâcher. Un OK maintenu devient un appui long sur la TV. Si la liaison tombe pendant un appui, la TV relâche la touche d'elle-même après 1,2 s sans nouvelles.
- **Pas de doublon, pas de perte** : chaque événement porte `sid` (session du téléphone) et `seq` ; le téléphone renvoie après une reconnexion ce qui n'a pas reçu de réponse, la TV ignore un numéro déjà appliqué. Un appui vieux de plus de 3 s n'est pas rejoué (un « OK » en retard serait une surprise) ; un relâchement l'est toujours.
- **Latence** : une seule connexion TCP ouverte (HTTP/1.1 keep-alive, `TCP_NODELAY`), un ping toutes les 3 s la garde chaude (la TV ferme les connexions inactives après 15 s). Côté TV, la touche passe devant le travail d'affichage en file (dans l'ordre) et la réponse n'attend pas plus de 150 ms un écran occupé.
- **Sans réseau commun** : secours **Bluetooth** (TV appairée, choisi dans « Choisir la TV »). Le service Bluetooth de fichiers de la TV accepte `"CBTR" + code`, puis une requête par ligne (`POST key?code=…\n` → `200 {…}\n`), mêmes routes. Pendant une session Bluetooth de télécommande, les envois de fichiers par Bluetooth attendent. Sans Wi-Fi ni secours choisi, le téléphone affiche « Télécommande indisponible : la TV ne répond pas sur le réseau… ».

## Ouvrir CastBridge-TV depuis le téléphone

Comme la touche YouTube ou Netflix d'une télécommande : **un geste sur le téléphone fait apparaître CastBridge-TV au premier plan de la TV**, même si une autre application (YouTube…) est devant, et la ligne affichée dit clairement quoi faire si la TV ne répond pas. Code : cœur pur et testé `core/…/tv/OpenTv.kt` (`OpenTvPlan`, `OpenTvReply`, `OverlayOfferPolicy`) et `core/…/remote/OpenTvFlow.kt` (`OpenTvFlow`, `OpenTvHttpLink`, `OpenTvTexts`) ; branchement Android `receiver/…/TvForeground.kt` (TV) et `sender/…/OpenTv.kt`, `OpenTvUi.kt` (téléphone).

### Les gestes (téléphone CastBridge)

- **Bouton « Ouvrir sur la TV »** (icône TV) en tête de l'onglet « CastBridge TV ». Si CastBridge-TV est déjà au premier plan, le bouton **ouvre la télécommande** (rien n'est touché sur la TV : une vidéo qui joue n'est jamais interrompue).
- **Touche « TV »** dans la rangée système de la télécommande (Retour, Accueil, Menu, Info, Clavier, TV), ronde comme les autres ; elle n'existe pas quand « Ma TV » pilote une autre marque que CastBridge-TV.
- **Raccourci d'application** : appui long sur l'icône de CastBridge › « Ouvrir CastBridge-TV » (`res/xml/shortcuts.xml`).
- **Lien** `castbridge://open-tv[?screen=library]`, traité par `MainActivity` (même action ; un message court dit le résultat). Il n'est volontairement **pas** « BROWSABLE » (une page web ne peut pas faire apparaître la TV) et une seconde demande dans les 2 s est ignorée.

### Ce que dit le téléphone : une ligne, 10 s d'attente au plus, aucun réessai sans fin

Le téléphone cherche la TV (téléphone de confiance, sinon TV connue par son code), essaie les voies **Wi-Fi, puis Bluetooth, puis tunnel de l'API** (une seule tentative par voie, la TV qui répond a le dernier mot), et rend UNE ligne (`OpenTvFlow`, testé avec de fausses liaisons et une fausse horloge : au plus 10 s en tout, chaque voie reçoit ce qui reste).

| Situation | Ligne |
|---|---|
| CastBridge-TV est devant (ouvert ou déjà là) | « CastBridge-TV est à l'écran » |
| la TV a besoin de l'autorisation « par-dessus » | « La TV demande une autorisation : MENU › Afficher par-dessus » |
| une notification attend sur la TV | « La TV affiche une notification « CastBridge-TV » : validez-la avec la télécommande de la TV » |
| aucune voie ne répond | « La TV ne répond pas : allumez-la (le Wi-Fi ou le Bluetooth de la TV est éteint) » |
| aucune TV associée | « Aucune TV n'est associée : touchez « Ajouter ma TV »… » |
| le téléphone n'a aucune voie (Bluetooth éteint, autorisation refusée) | la raison, côté téléphone |
| la TV ne reconnaît pas le téléphone (jeton expiré, code inconnu) | « La TV ne reconnaît pas ce téléphone : réassociez-la… ou saisissez son code » (jamais un code inutilisable envoyé : la TV le compterait comme faux) |
| TV plus ancienne que la route | « Cette TV ne connaît pas encore ce raccourci : mettez CastBridge-TV à jour » |

### Côté TV : `POST /api/tv/open`

PIN ou jeton, comme les autres routes de la télécommande. Corps facultatif (`Content-Type: application/json`, 1 Kio au plus) ou paramètre `?screen=` (le paramètre l'emporte) : `home` (accueil, quitte la vidéo), `library`, `player` (l'écran tel qu'il est), `games`, `quiz` ; sans écran, CastBridge-TV revient tel qu'il était. Écran inconnu : 400. Sur l'édition d'essai, `library` devient `home` et `quiz` devient `games` (leurs tuiles sont fermées). Réponse **200 dans tous les cas où la TV a répondu** :

```json
{"opened":true,"how":"direct|fullscreen|accessibility","needs":null}
{"opened":true,"already":true,"how":"already","needs":null}
{"opened":false,"how":"fullscreen|none","needs":"overlay"}
```

`already` : un écran de CastBridge-TV est déjà devant, **rien n'est touché**. Sinon la TV essaie, dans l'ordre (règle pure `OpenTvPlan`), en **vérifiant après chaque essai** que l'écran est vraiment là (sous Android 10+, un démarrage bloqué ne lève aucune erreur) :

1. **direct** (`direct`) : le système ramène la tâche de CastBridge-TV devant telle qu'elle est (`AppTask.moveToFront`), ou démarre l'écran demandé (`startActivity` NEW_TASK | CLEAR_TOP) ; possible avec l'autorisation « Afficher par-dessus les autres applications » (`SYSTEM_ALERT_WINDOW`, déjà déclarée côté TV) ou avant Android 10 ; attend 2 s au plus ;
2. **notification** (`fullscreen`) : la notification du canal « Demandes du téléphone » (celui de la lecture demandée à distance), avec l'intention plein écran si Android l'autorise (`canUseFullScreenIntent`), sinon une notification qu'on ouvre avec la télécommande ; attend 0,8 s ; retirée si l'écran est venu par une autre voie ;
3. **accessibilité** (`accessibility`) : le service « CastBridge Télécommande » s'il est actif (Android l'autorise à démarrer un écran depuis l'arrière-plan) ; attend 2 s au plus.

**Ce qui revient à l'écran** (R-40, audit anti-régression 2026-10-07 b, I-1 ; règle pure `OpenTvPlan.target(screen, top, taskAlive)`, testée) : `PlayerActivity` est `singleTask` et racine de la tâche, la démarrer alors que d'autres écrans sont au-dessus (Quiz, partie en ligne, Échecs, Langues, Portefeuille) les fermerait tous et arrêterait la partie en ligne (`PlayOnlineActivity.onDestroy`). Donc **sans écran demandé, la tâche revient TELLE QU'ELLE ÉTAIT** : `AppTask.moveToFront`, ou (notification, accessibilité) une intention vers l'écran qui était au sommet avec `NEW_TASK | REORDER_TO_FRONT`, jamais `CLEAR_TOP` ; `PlayerActivity` n'est démarré sans écran que s'il n'y a aucune tâche vivante. **Un écran demandé explicitement** (`home`, `library`, `player`, `games`, `quiz`) s'ouvre comme avant (les écrans au-dessus se ferment : c'est ce qui est demandé). Le bouton « Ouvrir sur la TV » du téléphone n'envoie aucun écran : la partie derrière YouTube est donc intacte au retour.

Au pire 4,8 s côté TV. `needs:"overlay"` : rien n'a ouvert l'écran **et** l'autorisation « par-dessus » règlerait le problème **et** l'écran de réglage existe sur ce boîtier. Alors la TV propose **UNE fois**, dans son MENU, la ligne « **Autoriser CastBridge-TV à s'afficher par-dessus les autres applications** » (ouvre `ACTION_MANAGE_OVERLAY_PERMISSION` depuis l'écran visible, comme la ligne d'exemption de batterie) ; choisie (accord ou refus), elle ne revient plus : le refus est mémorisé, aucune boucle. La ligne permanente « Lecture à distance : autoriser l'affichage par-dessus les autres apps » reste. Sur un boîtier sans cet écran (GaiaOS, à confirmer), la TV ne promet rien : `needs` reste vide et le téléphone dit d'ouvrir CastBridge-TV avec la télécommande de la TV (ou d'activer le mode « toute la TV » une fois, qui permet l'ouverture directe par l'accessibilité).

### Limites

- **Aucun allumage de la TV.** Une TV éteinte ne répond ni en Wi-Fi ni en Bluetooth : la ligne dit de l'allumer. Ni HDMI-CEC (la commande « One Touch Play » passe par la permission système `HDMI_CEC`, réservée aux applications du fabricant : impossible sans droits système), ni **Wake-on-LAN** (l'adresse MAC d'un autre appareil n'est plus lisible depuis Android 10, et beaucoup de TV n'écoutent rien en veille).
- Une TV en veille profonde n'a plus de service qui tourne (voir `docs/ADMIN.md`) : même ligne.
- Sans l'autorisation « par-dessus » ni l'accessibilité, Android 10+ ne laisse qu'une notification à ouvrir avec la télécommande de la TV : le téléphone le dit.
- Android durcit peu à peu les démarrages d'écran depuis l'arrière-plan (d'après la documentation d'Android 15, l'autorisation « par-dessus » seule ne suffirait plus sans fenêtre visible : à confirmer sur une TV) : c'est pourquoi **chaque essai est vérifié** avant de passer au suivant, au lieu de supposer qu'une voie marche parce qu'elle est autorisée.
- À vérifier sur la TV : le démarrage d'écran par le service d'accessibilité (voie 3) n'a pas été essayé ; la TV de référence (GaiaOS, Android 14) dira laquelle des trois voies ouvre réellement CastBridge-TV devant YouTube.
- Le téléphone ne demande **aucune permission de plus** (le Bluetooth est celui du reste de l'application) ; `SYSTEM_ALERT_WINDOW` n'est déclarée que côté TV et proposée une seule fois.
- Le retour de la tâche « telle qu'elle était » (`AppTask.moveToFront`, `REORDER_TO_FRONT`, R-40) n'est pas mesuré sur la TV de référence : parcours P-89 en ajoutant « partie en ligne ouverte, YouTube devant, puis « Ouvrir sur la TV » : la partie est-elle intacte ? ».
- Pas encore mesuré sur une vraie TV : voir le parcours P-89 (`docs/test-plans/PARCOURS-CRITIQUES.md`).

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
