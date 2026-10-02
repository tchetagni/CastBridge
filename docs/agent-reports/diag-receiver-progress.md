# Diagnostic : la TV reste sur « Prêt à recevoir » pendant une copie (2026-10-02)

Branche `claude/fix-receiver-progress` (depuis `integration/agents` 5cec337), un commit, non poussé.

## Symptôme

Signalé par le propriétaire le 2026-10-02, déjà vu le 2026-10-01. Pendant qu'un fichier est copié du téléphone vers la TV, CastBridge-TV n'affiche
aucune notification de progression et la puce de l'accueil reste sur « ● Prêt à recevoir · code … ».
Appareils : TV 0.14.18-beta-verrouillee, téléphone 1.2.31-beta.

## Preuves

- **Téléphone** (`logcat -d`, lecture seule) : copies de « Prison Break [S02 - E02].avi » (16:26) et « [S02 - E03].avi » (17:07 à 17:09:49)
  par `CastSession` en mode COPYING : « Copie vers CastBridge TV SMART_TV : 100 % ».
  Notification `upload` (id 2) mise à jour toutes les 1 à 3 s, puis `UploadService.onDestroy`. Aucune ligne Bluetooth, donc la copie passait par le réseau local.
  Le téléphone ne journalise pas le protocole choisi.
- **Code du téléphone** : `CastSession` appelle `UploadService.start(..., progressive = false)`.
  `UploadService` prend alors `runFast` (le réglage `FastTransfer.enabled` vaut true par défaut), c'est-à-dire le protocole **multivoie** `/api/transfer/...`.
  La TV 0.14.18 le propose : le moteur 1b2fe28 précède le commit de version c81fa23.
- **Code de la TV** : le multivoie écrit dans `<volume>/.cbx/<id>.data` (`PartAssembler`).
  Le fichier ne devient `<nom>.part` qu'au `finish`, où il est aussitôt validé.
  Or l'accueil (`PlayerActivity.homeApi().status()`) lisait `ReceiverServer.receiving()`, construit à partir du listage des `.part` qui ont un `.meta`.
  Pendant toute une copie multivoie, ce listage est vide, donc la puce affiche « Prêt à recevoir ».
- **Test** : `ReceptionProgressServerTest.theMultiConnectionCopyIsVisibleWhileTheOldListingSeesNothing` reproduit le cas.
  Après `/api/transfer/begin`, `receiving()` est vide, alors que la nouvelle source montre bien la copie.
- **TV par SSH, émulateur** : non utilisés. Les tentatives précédentes ne donnaient ni logcat ni dumpsys.
  L'émulateur a castbridge.receiver, mais une copie pilotée demande le PIN ou un jeton : la reproduction s'est faite par les tests JVM du vrai `ReceiverServer`.

## Causes, par ordre d'importance

1. **Confirmée.** La copie multivoie (`/api/transfer`, chemin par défaut du téléphone depuis 1b2fe28) est invisible pour l'écran : aucun `.part` ni `.meta` avant la fin.
2. **Confirmée.** Aucune notification de réception n'existe dans `TvService`.
   La seule notification est celle de premier plan, « Prêt à recevoir des vidéos » (`IMPORTANCE_MIN`), jamais mise à jour.
   L'absence de notification n'était donc pas une régression : la fonction n'avait jamais été écrite.
3. **Confirmée (Bluetooth).** `BtProtocol.serve` n'écrit pas de `.meta`, donc le listage ignore aussi la copie par Bluetooth.
   L'accueil lisait alors la chaîne d'état unique `1-bt`. Toute autre liaison (HELLO, télécommande) qui se termine l'écrase par « Bluetooth : prêt » (constat 7 de W13).
4. **Mineure.** La puce n'est rafraîchie que toutes les 4 s, et seulement quand l'accueil est visible.

Le code récent d'`integration/agents` ne cause pas le bug et ne le corrige pas : rangement automatique au `finish`, `Connection: close` sur les refus non GET, `Filing`/`FiledIndex`.
Il ne touche que la fin de la copie, et la cause 1 existe déjà en 0.14.18.

## Correctif

- `core/xfer/TransferProgress.kt` : un seul détenteur de la progression, pour chaque transfert :
  - informations publiées : nom, octets reçus, %, débit lissé, transport (Wi-Fi, Wi-Fi multivoie, Bluetooth), téléphone source, phase ;
  - `seq` stable pour toute la vie du transfert, reprises comprises ;
  - signalement limité à environ une fois par seconde par transfert, toujours au début et à la fin ;
  - une coupure garde le transfert affiché (« reprise en attente ») ; 90 s de silence le clôturent (« plus de nouvelles du téléphone ») ;
  - la fin reste affichée 8 s (« Vidéo reçue ✓ », « Fichier reçu ✓ » ou le motif de l'échec) ;
  - adaptateur `Sink` pour une liaison Bluetooth CBT1.
- `ReceiverServer` l'alimente sur tous les chemins :
  - `PUT /upload` : début ou reprise, avancée à chaque écriture, fin, coupure, erreur disque ;
  - `/api/transfer/begin|chunk|finish|abort`, ainsi que le retrait du support et les erreurs disque ;
  - le téléphone source vient du jeton déjà vérifié par la requête (`ThreadLocal`, aucune deuxième vérification), sinon de l'adresse IP.
- `BtServer` alimente le même détenteur par `Sink`.
- `TvService` : canal « Réceptions en cours » (`IMPORTANCE_LOW`), une notification par transfert (id 5000 + seq) avec barre de progression.
  La ligne finale est retirée après 10 s. Si `POST_NOTIFICATIONS` est refusé, l'échec est journalisé et l'écran affiche quand même la progression.
- Accueil : la puce affiche la ligne en direct, rafraîchie à chaque signalement au lieu d'attendre 4 s. Exemple :
  « ⬇ Réception de Prison Break [S02 - E03] : 42 % · 1,8 Mo/s · Wi-Fi multivoie · depuis Galaxy · code … ».
  Elle tient sur 2 lignes au plus, avec « +N autres » s'il y a plusieurs copies.
  L'ancien listage reste en secours.

## Tests

- `TransferProgressTest` (8 tests) :
  - début, avancée limitée, fin ;
  - fichier ou vidéo ;
  - échec et annulation ;
  - reprise sur le même id avec la même source ;
  - clôture après 90 s de silence ;
  - transferts simultanés sur les deux transports ;
  - 8 fils en parallèle ;
  - `Sink` Bluetooth.
- `ReceptionProgressServerTest` (3 tests) : sur le vrai serveur, le multivoie (cause reproduite, puis annulation), une copie multivoie complète, et `PUT /upload` en deux morceaux.
- `:core:test` complet vert. Pendant la mise au point, `InFlightTest` a détecté une deuxième vérification du jeton, corrigée par le `ThreadLocal`.
- `:receiver:compileDebugKotlin` vert.

## Ce que seule la vraie TV peut confirmer

- Que le lanceur de la TV (GaiaOS 720p) **affiche** les notifications d'une app. Beaucoup de TV Android n'ont pas de volet de notifications visible : l'écran reste alors la surface fiable.
- Que `POST_NOTIFICATIONS` est accordé.
- La lisibilité de la puce en 720p.
- Le cas Bluetooth réel.
- **Un nouveau build de la TV (verrouillé, `-PrequireActivation=true`) est nécessaire.** Le correctif est entièrement côté TV ; le téléphone n'est pas modifié.
