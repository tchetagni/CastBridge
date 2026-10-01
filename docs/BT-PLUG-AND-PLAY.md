# Bluetooth plug and play (téléphones de confiance)

Objectif : CastBridge (téléphone) trouve et pilote CastBridge-TV par Bluetooth, sans IP ni PIN, **sans intervention de l'utilisateur dans toutes les situations réalistes** ; quand c'est impossible, l'écran dit exactement pourquoi et propose **une seule action évidente**.

Principe : la boucle de l'app ne contient plus de logique. Tout est décidé par du code pur de `core` (`castbridge.core.trust`), testé avec une fausse TV et une fausse horloge :

| Fichier | Rôle |
|---|---|
| `LinkMachine.kt` | machine d'états : états, textes, hystérésis, politique de nouvel essai |
| `LinkDriver.kt` | une `step()` = observer, parler à la TV si utile, mettre à jour la machine, dire quand revenir (garde-vivant, repli de route, renouvellement du jeton, détection de réinitialisation) |
| `PairFlow.kt` | « Ajouter ma TV » / « Réassocier » de bout en bout, avec réparation guidée d'une association périmée |
| `LinkText.kt` | tous les messages français, un par issue (aucun texte d'exception) |
| `Credentials.kt` | `TvCredential` : la seule façon de construire une requête authentifiée ; `CredentialGate` |
| `ResilientCall.kt` | appel « conscient de la liaison » : attente calme, nouveaux essais bornés, jamais d'identifiant refusé rejoué |
| `Diagnostics.kt` | « Diagnostic Bluetooth » et rapport copiable sans secret |
| `PhonePresence.kt` | TV : qui est connecté, bannière non répétitive, message de refus |
| `TrustFiles.kt`, `Storm.kt` | registre de la TV sur disque (écriture atomique + copie), limiteur d'essais, gigue |

Le code Android est volontairement mince : `LinkAndroid.kt` (questions posées au système), `TvLink.kt` (boucle : dormir, appeler `step()`, publier), `TvPairScreen.kt` (affichage).

## Sécurité
- Un téléphone n'est « de confiance » qu'après (1) l'appairage Android (comparaison de code sur les deux écrans) et (2) « Autoriser <nom> à piloter cette TV ? » validé à la télécommande, dans la fenêtre « Ajouter un téléphone » (2 min, ouverte par le propriétaire, un téléphone par ouverture, « Refuser » présélectionné, 3 refus = blocage 10 min).
- Sockets RFCOMM sécurisés uniquement. Le pair est identifié par l'adresse du socket (clé d'appairage), jamais par ce qu'il écrit ; la TV vérifie aussi qu'Android le dit toujours appairé.
- CBTH (HELLO) : un pair non approuvé reçoit 1 octet d'erreur, jamais nom, IP ni jeton. Un pair de confiance reçoit nom de la TV, version, IP:port, **l'identifiant d'installation de la TV**, et un jeton propre au téléphone (256 bits, 12 h, renouvelé à mi-vie, stocké haché côté TV, révoqué avec le téléphone). Le PIN n'est jamais transmis.
- HTTP : en-tête `X-CB-Token`. Le jeton n'ouvre pas `/api/ssh*`, `/api/apk/install`, `/api/update/install` (PIN seulement). **Un jeton refusé ne compte jamais comme un PIN faux** (pas de verrouillage).
- RFCOMM CBT1/CBTN/CBTR/passerelle : un pair de confiance envoie `------` dans le champ PIN, ignoré pour lui ; pour tout autre pair c'est un PIN faux (compté, par adresse Bluetooth : le téléphone A ne verrouille jamais B). Le client Mac et les téléphones à PIN ne changent pas.
- Aucune télémétrie ajoutée ; l'adresse Bluetooth ne quitte jamais l'appareil ; aucun composant exporté de plus (le service et le récepteur de reprise en arrière-plan sont `exported="false"`).
- Registre de la TV et préférences du téléphone : exclus des sauvegardes (`trusted_phones.txt`, `.tmp`, `.bak` ; `castbridge_trust.xml`).
- Rien de secret dans les journaux, les URL, le diagnostic : jeton, PIN et clé n'y figurent pas (le rapport masque aussi, par précaution, toute valeur qui y ressemble, et les adresses Bluetooth sauf les deux derniers octets).

## États du lien (téléphone)
Chaque état a un titre et une explication stables, **une** action, et une règle de nouvel essai. Délais : premier raté confirmé après 1,5 s, puis 4, 8, 15, 30, 60 s au premier plan (±25 % de gigue) ; en arrière-plan 1, 2, 5, 10, 15 min. « jamais » = aucun essai automatique, on attend l'utilisateur ou un événement système.

| État | Titre affiché | Explication | Action | Essai (1er plan) | Essai (arrière-plan) |
|---|---|---|---|---|---|
| Aucune TV | « Aucune TV ajoutée » | Ajoutez votre TV : aucun code à saisir. | Ajouter ma TV | jamais | jamais |
| Connexion (démarrage) | « Connexion à … » | Recherche de la TV par Bluetooth | — | 1,5 s puis courbe | idem, lente |
| Bluetooth éteint | « Bluetooth éteint » | Activez le Bluetooth du téléphone : la TV se reconnectera toute seule. | Activer le Bluetooth | 15 s (+ diffusion système) | 15 min |
| Permission manquante | « Autorisation Bluetooth manquante » | Autorisez « Appareils à proximité » pour CastBridge. | Autoriser | 15 s | 15 min |
| Pas de Bluetooth | « Pas de Bluetooth » | Saisissez le code de la TV. | Saisir le code | 15 s | 15 min |
| TV non associée | « TV non associée » | Ce téléphone n'est plus associé en Bluetooth à la TV. | Associer la TV | 5 s | jamais (attend le signal d'association) |
| Association en cours | « Association en cours » | Comparez le code sur le téléphone et sur la TV, puis validez. | — | 2 s | 2 s |
| Association périmée | « Association Bluetooth périmée » | Android garde une ancienne association que la TV ne reconnaît plus. Supprimez la TV dans les réglages Bluetooth (Oublier / Dissocier), puis revenez ici : la suite est automatique. | Ouvrir les réglages Bluetooth | 3 s (détecte la suppression) | jamais |
| TV injoignable | « … est introuvable » | Allumez la TV et restez à proximité : la connexion est automatique. | Réessayer | courbe | courbe lente |
| App TV fermée (SDP absent) | « CastBridge-TV n'est pas ouvert » | La TV répond en Bluetooth, mais l'application ne tourne pas. Ouvrez-la sur la TV. | Réessayer | courbe | courbe lente |
| Liaison refusée aussitôt | « … ferme la liaison » | La TV coupe la connexion aussitôt (3 fois de suite sans attente = association périmée, voir ci-dessus). | Réessayer | courbe | courbe lente |
| **TV réinitialisée** | « TV réinitialisée » | La TV a été réinitialisée ou réinstallée : elle ne vous reconnaît plus. | **Réassocier** | **jamais** | **jamais** |
| Téléphone retiré | « Téléphone retiré de la TV » | Ce téléphone a été retiré de la liste des téléphones de la TV. | Réassocier | jamais | jamais |
| TV ne reconnaît plus (TV ancienne) | « La TV ne vous reconnaît plus » | Réinitialisée, réinstallée, ou téléphone retiré (une TV ancienne ne dit pas lequel). | Réassocier | jamais | jamais |
| Refusé par le propriétaire | « Refusé sur la TV » | Après trois refus, la TV ignore ce téléphone 10 minutes. | Réassocier | **jamais** | **jamais** |
| Accès expiré | « Accès expiré » | L'autorisation a expiré ou été révoquée : renouvellement en cours. | Réessayer | courbe | courbe lente |
| TV trop ancienne (ERR_MAGIC) | « CastBridge-TV à mettre à jour » | Installez la mise à jour sur la TV. | Réessayer | 10 min | jamais |
| Connecté | « … connectée » | Par le Wi-Fi de la maison / Par Wi-Fi Direct / Par Bluetooth | — | garde-vivant 15 s | 5 min |
| Liaison réduite | « …, liaison réduite » | Par Bluetooth seulement, plus lent ; le Wi-Fi sera repris tout seul. | — | 15 s | 5 min |
| Liaison perdue | « Liaison perdue, reconnexion… » | Dit **quel côté** : la TV ne répond plus / le Bluetooth du téléphone a été coupé / le Wi-Fi du téléphone a été perdu. | — | courbe | courbe lente |

### Hystérésis (plus de cartes qui clignotent)
- Un état d'échec n'apparaît qu'après **2 résultats identiques consécutifs** et **3 s d'affichage minimum** de l'état précédent. Immédiats car certains : « aucune TV », Bluetooth coupé/permission, « association en cours », et « connecté » (sauf juste après « liaison perdue » : 3 s mini).
- Un raté isolé garde la dernière bonne carte avec la ligne « Liaison perdue, reconnexion… » ; « introuvable » n'apparaît qu'après 40 s de silence (confirmé 2 fois). Un lien qui bat toutes les quelques secondes ne change jamais la carte.
- Le délai de nouvel essai **ne revient à zéro qu'après 30 s de connexion stable**.

### Jamais réessayé
- TV qui dit « je ne vous connais pas » ou « refusé » : ni minuterie, ni diffusion Bluetooth, ni job d'arrière-plan ne la sollicitent. Seuls le bouton de l'utilisateur ou un nouvel appairage Bluetooth relancent (c'était la cause des connexions/déconnexions « chaque minute » du journal).
- Identifiant refusé : un PIN faux (verrouillage d'une minute par erreur répétée) et un jeton que la TV a refusé ne sont **plus jamais renvoyés** (`CredentialGate`). Un jeton expiré se remplace par un nouveau HELLO.

## Réinitialisation de la TV (additif, rétrocompatible)
- La TV a un **identifiant d'installation** (16 octets aléatoires, enregistré avec le registre). Il change si le registre est perdu (réinstallation, fichier illisible sans copie).
- HELLO réponse OK : ligne `id=<hex>` en plus (les anciens téléphones ignorent les clés inconnues ; une ancienne TV l'omet : le téléphone n'en tient alors pas compte).
- HELLO requête : bit 1 des drapeaux (`HELLO_HAS_INSTALL_ID`) puis `u8 longueur | id` que le téléphone retient. Un ancien téléphone n'envoie rien : la réponse est **exactement** l'ancienne.
- HELLO réponse d'erreur à un pair appairé qui a envoyé un id : un octet d'indice de plus (`0` inconnu, `1` autre installation → « TV réinitialisée », `2` même installation → « Téléphone retiré »). Un pair non appairé n'apprend rien (indice 0). Une ancienne TV ferme après l'octet d'état : le téléphone n'attend pas l'indice.
- « Réassocier » (un seul toucher) : oublie la TV localement, ouvre « Ajouter ma TV » qui lance seul tout le parcours (association Android si besoin, attente que le propriétaire ouvre « Ajouter un téléphone », nouveau HELLO avec demande de confiance). Les codes `ERR_*` et CBT1/CBTN/CBTR sont inchangés.

## Association Bluetooth périmée
- Détection : liaison fermée presque aussitôt (< 3 s) après acceptation, 3 fois de suite sur un téléphone appairé (une TV éteinte, elle, fait attendre ~10 s), ou « inconnu » sur une association que la TV n'a plus.
- Pas de `removeBond()` (peu fiable sous Android 14) : lien vers les réglages Bluetooth (`ACTION_BLUETOOTH_SETTINGS`) avec la consigne « Oublier / Dissocier ». L'app suit les diffusions `BOND_STATE_CHANGED` (et sonde), voit l'association disparaître, la recrée seule (`createBond`), attend la validation, puis reprend le parcours. Si l'association est supprimée puis recréée et que la TV refuse encore, le parcours s'arrête avec la même action unique (pas de boucle).
- Association en cours (`BOND_BONDING`) : état « Association en cours », annulation de la boîte Android détectée (retour à `NONE`). Changement de nom de la TV : suivi au HELLO. Changement d'adresse Bluetooth de la TV : la TV enregistrée est suivie si une TV appairée de même nom apparaît (`SavedTvs.addressChanges`).

## Perte et reprise de liaison (téléphone)
- **Détection** : diffusion `ACL_DISCONNECTED` ; garde-vivant toutes les ~15 s sur la route Wi-Fi (requête authentifiée `GET /api/info` avec le jeton, **deux** essais, jamais un seul délai) ; échec d'un socket en cours d'usage → l'appelant signale (`ResilientCall.suspect`). La requête authentifiée détecte aussi « la TV est là mais refuse ce jeton » (TV réinstallée, téléphone retiré, jeton révoqué) en moins de 15 s.
- **Quel côté** : Bluetooth du téléphone coupé (adaptateur) → dit tout de suite ; réseau du téléphone perdu → « Le Wi-Fi du téléphone a été perdu » ; sinon « La TV ne répond plus ».
- **Continuité** : le jeton reste utilisable tant qu'il est valide (marge de 60 s pour les écarts d'horloge ; les durées sont mesurées sur l'horloge du téléphone, pas celle de la TV). Reconnexion sans nouveau HELLO si le jeton vit encore (la TV qui redémarre garde ses jetons : registre sur disque). Un renouvellement à mi-vie qui échoue ne jette rien ; il est retenté au tiers du temps restant.
- **Routes** : Wi-Fi commun → Wi-Fi Direct → Bluetooth seul. Si la route Wi-Fi tombe : repli (avec le même jeton) ; la route rapide est reprise toute seule dès qu'elle répond, sans rouvrir d'écran.
- **Opérations en cours** : `ResumableUpload`/`ResumableDownload` reprennent à l'octet confirmé par la TV (fichier `.part`), prennent le jeton **vivant** à chaque essai, n'envoient plus un jeton refusé et attendent calmement « Autorisation de la TV à renouveler » ; les envois Bluetooth CBT1 reprennent à l'offset annoncé ; la télécommande (`RemoteSession`) garde ses touches (numéros de séquence), traite un jeton expiré comme un renouvellement et pas comme un « PIN refusé » ; le lecteur du téléphone prend le jeton à chaque requête de plage. Les appels ponctuels passent par `ResilientCall` (attente « En attente de la TV », nouveaux essais bornés, abandon avec phrase actionnable après 5 min ; un appel non idempotent n'est pas rejoué).
- **Tempête** : gigue ±25 %, limiteur de 12 HELLO/min côté téléphone, 10/min par pair et 40/min au total côté TV (réponse `ERR_BUSY`, traitée comme transitoire). Les demandes « Ajouter un téléphone » ne sont pas limitées (pilotées par le propriétaire).
- **Veille / process tué** : l'état et le jeton sont enregistrés (préférences privées) ; une carte « Liaison perdue, reconnexion… » est reconstruite au redémarrage (jamais « connecté » sans vérification). Déclencheurs : ouverture de l'app, écran allumé, réseau, diffusions Bluetooth (ACL, association, adaptateur), job périodique.

## Arrière-plan (téléphone)
- `LinkJobService` (JobScheduler, ~15 min, persistant après redémarrage, batterie non faible) et `LinkWakeReceiver` (non exporté : ACL connecté, association, adaptateur) lancent **une seule** étape du pilote ; pas de service au premier plan, pas de verrou de réveil (le système garde le CPU pendant le job), pas d'alarme exacte. Compatible Android 12–14 (aucun démarrage de service depuis l'arrière-plan). WorkManager n'a pas été ajouté (nouvelle dépendance non vérifiable dans le cloud) : JobScheduler donne la même garantie.
- En arrière-plan : garde-vivant toutes les 5 min, nouveaux essais 1 → 15 min : une panne de 48 h coûte quelques dizaines de tentatives.

## Côté TV
- `HelloHandler` : verrou par appareil, une demande d'ajout répétée par le même téléphone (lien coupé puis revenu) **reprend** sa demande en cours (l'ancienne se libère aussitôt, donc son emplacement `BtServer`), un autre téléphone reçoit `ERR_BUSY`.
- Registre : fichier écrit par `FileTrustPersistence` (tmp + fsync + renommage atomique), copie `.bak` de la dernière version saine, somme SHA-256 en dernière ligne. Fichier tronqué/modifié → copie ; ni l'un ni l'autre → **rien n'est cru**, registre vide et nouvel identifiant (« TV réinitialisée » côté téléphones). Les anciens fichiers sans somme se chargent encore.
- Écran « Ajouter un téléphone » (existant) : compte à rebours, nom du téléphone qui demande, Autoriser/Refuser (Refuser présélectionné), liste des téléphones de confiance avec **état** (connecté, liaison reprise, téléphone déconnecté) et « Retirer », ligne d'état courte (« Bluetooth prêt · 2 téléphones de confiance (1 connecté) »). Messages de refus : « Galaxy a été refusé. », « … est ignoré 10 minutes : trois refus de suite. », « … doit patienter ».
- Bannière « téléphone connecté » : au plus une par 10 min par téléphone ; une reconnexion après perte est seulement « Liaison reprise : … » (ligne d'état, sans bannière).
- Verrouillage par appareil : PIN faux compté par adresse Bluetooth (RFCOMM) ou par IP (HTTP) ; un jeton refusé n'est jamais compté.

## Diagnostic Bluetooth (téléphone : carte de la TV et « Mes TV »)
Étapes, chacune OK / KO / non faite / info avec l'action suivante : Bluetooth allumé, permission, TV enregistrée, adresse (masquée), association, service CastBridge-TV (SDP), connexion RFCOMM, réponse HELLO (code), route choisie, validité du jeton (durée restante, jamais le jeton), version et identité de la TV (« identité différente » = TV réinstallée). Bouton **Copier le rapport** : texte brut sans jeton, PIN ni clé, adresses tronquées.

## Identifiants : une seule voie
`TvCredential` construit tout en-tête, toute ligne d'en-tête et tout champ PIN Bluetooth. Un identifiant inutilisable (vide, tronqué, jeton expiré déjà abandonné) **n'est pas envoyé** (`TvCredential.Missing`) : la TV le compterait comme PIN faux. Deux tests de code source interdisent tout `"X-CB-Pin"`/`TvAuth.header` hors de l'aide et toute vérification `Pin.isValidFormat(pin)` dans un écran (un jeton n'a pas 6 chiffres).

Audit des clients : `TvClient`, `DownloadsClient`, `ParentalClient`, `RemoteClient` (Wi-Fi), `QuizPackRelay` (envoyait le jeton dans `X-CB-Pin` : corrigé), `ConnectScreens`, lecteur (`PlaybackService`, jeton pris à chaque requête), `DownloadsActivity` (TV) : tous par `TvCredential`. Écrans qui bloquaient un téléphone de confiance parce que le jeton n'a pas 6 chiffres : `CastSheet`, `DlnaHandoff` (corrigés). `LearnApi`, `QuizSync`, agent de bibliothèque : parlent à d'autres serveurs ou par ces mêmes clients, pas d'en-tête de TV. `BtGateway`/`RemoteBt`/CBT1 : `TvCredential.btPin`. Reste à migrer vers `ResilientCall` : les écrans qui appellent `ParentalClient`/`DownloadsClient`/`TvClient` directement (ils reprennent déjà le jeton renouvelé quand l'écran se recompose).

## Tests
`cd android && gradle :core:test --tests 'castbridge.core.*'` (`./gradlew` n'existe pas dans le dépôt). Nouveaux : `LinkMachineTest` (états, textes, hystérésis, politique, persistance), `LinkDriverTest` (échecs observés a–e, TV éteinte/rallumée, application TV tuée, TV réinstallée, téléphone retiré, jeton expiré/révoqué, repli et retour de route, coupure à chaque octet du HELLO, battement du lien, panne de 48 h, redémarrage du téléphone, deux coupures simultanées, arrière-plan), `PairFlowTest`, `ResilientCallTest`, `CredentialTest`, `HelloCompatTest` (vieux/nouveau téléphone et TV, corruption du registre, HELLO concurrents, limiteur), `InFlightTest` (relais coupant à l'octet près, TV redémarrée pendant un envoi, jeton expiré pendant une panne, téléchargement, Bluetooth CBT1, télécommande), `DiagnosticsTest`/`ErrorTextTest`/`PhonePresenceTest` (table : aucune issue sans message).

## Limites connues et risques résiduels

## Service « CastBridge API » (tout par Bluetooth)
- Troisième service RFCOMM de la TV, UUID `7c5e3b9a-4d2f-4c61-9b0e-cb0000000003` : un tunnel d'octets vers l'API HTTP locale (détails : `docs/ADMIN.md` §11). Socket sécurisé, appareils appairés seulement.
- **Règle de confiance** : un appareil appairé est admis si l'interrupteur « API par Bluetooth » de la TV est actif (défaut : actif, comme le service fichiers) **ou** si c'est un **téléphone de confiance** (inscrit ici ET toujours appairé) ; sinon la TV répond « refusé » (octet d'état 2) et le journalise. Le serveur HTTP demande ensuite le jeton du téléphone de confiance ou le PIN (échecs comptés par appareil `bt:<adresse>`, jamais contre le Wi-Fi ni les autres appareils). Le jeton n'ouvre toujours pas `/api/ssh*`, `/api/apk/install`, `/api/update/install`.
- Retirer un téléphone de confiance (ou le dés-appairer) lui retire le droit d'utiliser l'API par Bluetooth dès que l'interrupteur est coupé ; interrupteur actif, il reste utilisable avec le PIN comme tout appareil appairé.

## Liaison API persistante (service « CastBridge API v2 »)
Défaut mesuré (S21+ Android 15 ↔ TV 0.13.3, Wi-Fi du téléphone coupé) : une liaison RFCOMM par requête HTTP ; la pile Bluetooth n'a pas libéré la précédente (`already at opened state`), les `connect()` échouent en boucle, la télécommande passe « hors ligne » alors que la TV ne voit aucune erreur.
- **Une seule liaison par TV, réutilisée.** Quatrième service RFCOMM `7c5e3b9a-4d2f-4c61-9b0e-cb0000000004` (nom SDP « CastBridge API v2 ») : après l'octet d'état (0 = ok…), des **trames** `type u8 | flux u16 | longueur u16 | données` (OPEN, DATA, CLOSE, PING, PONG ; `Mux.kt`). Chaque connexion HTTP locale (127.0.0.1:18765) est un flux, joint côté TV à une connexion loopback neuve et attribuée à l'appareil, exactement comme le service v1 : le serveur HTTP fait toujours toute la vérification. 4 flux au plus par liaison ; les écritures sont sérialisées (une trame à la fois), le flux lent ralentit la liaison (pas de file illimitée : 256 Ko par flux).
- **Rétrocompatible.** Le service v1 (`…0003`, une liaison par connexion) reste servi tel quel : un ancien téléphone marche avec une TV neuve. Un téléphone neuf avec une TV ancienne : le service v2 est introuvable, il essaie v1 une fois, retient « TV ancienne » 10 min et fait une liaison par requête comme avant (avec les mêmes verrou et réessais).
- **Jamais deux `connect()` à la fois** vers la même TV : `LinkPool` (verrou d'appelants) + `BtConnectLock` (un verrou par adresse pour toute l'app, pris aussi par la télécommande CBTR pour son `connect()`). Pendant un `connect()` lent les autres demandes attendent et réutilisent la liaison obtenue.
- **Réessais.** Après une fermeture on n'ouvre pas avant **1,5 s** ; entre deux essais ratés 1,5 s puis 3 s (±25 % de gigue), **3 essais**, puis « Bluetooth : la TV ne répond pas (…) » et 8 s de pause pendant lesquelles les demandes échouent aussitôt (pas de boucle serrée). Un refus explicite de la TV (octet d'état ≠ 0) n'est pas réessayé.
- **Garde-vivant.** Le téléphone envoie PING toutes les ~15 s tant que la liaison sert (un flux ouvert, une requête dans les 45 s, ou `TunnelGateway.keepAlive()`) ; la TV ferme une liaison sans aucune trame après 30 s (10 min si elle est occupée par un envoi/téléchargement) : les PING suffisent. Le téléphone ferme lui-même une liaison inutilisée depuis 45 s, et une liaison dont la TV ne répond plus à 3 PING.
- **Télécommande.** Le canal CBTR (touches) utilise le service n° 1, **indépendant** du tunnel HTTP : il marche sans lui. Seul le `connect()` est coordonné (même verrou par TV), ce qui supprime la collision avec le tunnel en reconnexion. Latence et reprise : non mesurées dans le cloud (voir « à valider »).
- **Diagnostic** (« Passerelle Bluetooth » du téléphone) : liaisons ouvertes, ouvertures depuis le début, « liaison partagée » / « TV ancienne », dernière fermeture (motif en français). Côté TV : `GET /api/bluetooth/tunnel` gagne `sharedLinks`, `sharedListening`, `lastClose`.
- Tests JVM : `BtMuxTunnelTest` (faux transport RFCOMM : ouverture lente, `already opened`, 3 échecs puis pause, coupure en pleine réponse, 4 requêtes simultanées, TV qui ferme à 30 s (mise à l'échelle), garde-vivant, TV ancienne, téléphone ancien, gros fichier + petite requête en parallèle).
- **À valider sur matériel** : que le S21+ ouvre bien le service v2 sans « already opened » ; la durée réelle de libération d'une liaison (1,5 s suffit-il ?) ; que 15 s de PING évite la fermeture à 30 s ; la télécommande sur Bluetooth seul (latence, reprise). Résultat dans le cloud : `BtMuxTunnelTest` 12/12 et `BtApiTunnelTest` 12/12 verts (le `gradle :core:test` complet n'a pas pu être lancé : `kotlin-test` non téléchargeable (429) ; tests compilés avec le compilateur Kotlin et un mini-shim `kotlin.test`). Code Android (`BtSshGateway.kt`, `BtApiControl.kt`, `RemoteController.kt`) non compilé dans le cloud.

## Limites connues
- API par Bluetooth active par défaut : un appareil appairé (donc approuvé à l'appairage sur la TV) qui connaît le PIN peut tout faire par ce canal, comme par le Wi-Fi ; couper l'interrupteur pour ne garder que les téléphones de confiance.
- Jeton en clair sur le Wi-Fi local (HTTP) : rejouable par quelqu'un sur le même réseau pendant sa durée de vie.
- Téléphone volé et déverrouillé : accès jusqu'au « Retirer » sur la TV.
- Android exige un OK à la télécommande pour rendre la TV visible (boîte système).
- **Non vérifié sur matériel** : durées réelles du Bluetooth (le seuil « refus instantané < 3 s » vient de l'observation d'Android, à confirmer), suppression de l'association sous Android 14, limites d'arrière-plan réelles (JobScheduler peut retarder les jobs en économie d'énergie), comportement réel d'une coupure Wi-Fi/Bluetooth en pleine copie. Le code Android (écrans Compose, `TvLink.kt`, récepteur) n'a pas été compilé dans le cloud (Google Maven inaccessible) ; `LinkAndroid.kt` et `TvLink.kt` ont été compilés contre les classes du framework Android 14.
- Une ancienne TV (sans identifiant) ne distingue pas « réinitialisée » de « téléphone retiré » : message générique, même action.
- Une TV dont le fichier de registre est détruit sans copie oublie tous ses téléphones (volontaire : rien n'est cru sans vérification).
- Le HELLO du téléphone en route Bluetooth seule sert de garde-vivant (1/min au premier plan) et délivre un jeton à chaque fois (4 au plus par téléphone côté TV).
