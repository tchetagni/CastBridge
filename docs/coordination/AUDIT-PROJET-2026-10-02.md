# Audit du projet CastBridge (2026-10-02)

> **Portée.** Branche `integration/agents`, dernier commit `1c26a11`, avec en plus le travail **non commité** d'un autre agent (catalogue de bouquets signé : `SignedBundleCatalog.kt`, `BundleCatalogController.java`, etc.).
>
> **Méthode.** Audit en lecture seule :
> - aucun fichier modifié en dehors de celui-ci ;
> - pas de gradle, pas de maven, aucun accès au serveur, à `main` ou à `~/.castbridge-signing`.
>
> Chaque constat Critique ou Élevé a été relu dans le code. « Non vérifié » signale ce qui n'a pas pu être confirmé.
>
> **Ce qui n'a pas pu être exécuté.**
> - `tools/activation/verify_vectors.py` s'arrête faute du module Python `cryptography` : la concordance des vecteurs n'a pas été rejouée.
> - Le fichier `~/.castbridge-signing/activation-trusted-keys.txt` n'a pas été lu (consigne) : les portées réelles des clés embarquées dans les APK distribuées ne sont donc pas vérifiées.
>
> **Secrets.** Aucun n'est reproduit ici. Seuls leurs emplacements sont cités.

## 1. Résumé exécutif : les 10 constats principaux

| # | Gravité | Constat (résumé) | Où |
|---|---|---|---|
| 1 | **Critique** | **La clé qui enveloppe chaque location se calcule à partir de données publiques.** Elle est dérivée des empreintes des facteurs de la TV. Or ces empreintes figurent **en clair dans le même jeton d'activation** (cible `factor=…`), ainsi que dans `device-request.txt`. Avec le fichier `activation` et le lot scellé, n'importe qui ouvre la boîte, puis déchiffre le lot sur un PC, sans la TV et pour toujours. La destruction de clé ne protège donc rien : la clé se recalcule à partir du fichier d'activation. Même défaut pour les « lots chiffrés » (`LotKeys.DeviceKeyBox`). | `RentalKeys.kt:76-84`, `LotKeys.kt:53-56`, `Envelope.kt:57`, `OwnerFrames.kt:49-50`, `LicensedIssuer.kt:46` |
| 2 | **Critique** | **Période de grâce illimitée, toutes fonctions ouvertes, sans aucune clé.** Il suffit de réinstaller l'APK verrouillée par-dessus elle-même : c'est alors une « mise à jour », donc `existingInstall=true`, donc 30 jours de `GateState.Grace` (tout ouvert, même pas les restrictions de l'essai). Un « Effacer les données » relance ensuite 30 jours, indéfiniment. `castbridge.graceDays` vaut 30 par défaut. | `ActivationCenter.kt:185-194`, `FeatureGate.kt:32-34,52`, `receiver/build.gradle.kts:31` |
| 3 | **Élevé** | **Reculer l'horloge gèle le temps de la TV, et le plafond d'usage ne finit jamais.** `TvClock.now` renvoie le « maximum vu » tant que l'horloge murale est en dessous. `TvGate` n'a aucune règle de doute, contrairement aux locations qui sont suspendues. Ainsi l'essai de 30 jours ou le plafond d'une production durent autant que le recul choisi. À l'inverse, un saut en avant de moins de 400 jours consomme le plafond pour toujours. | `Keys.kt:82-92`, `Activation.kt:180-185` |
| 4 | **Élevé** | **Une clé d'essai « saisissable » (compacte) ne porte aucun droit `usage` : c'est un essai sans fin.** Plus largement, le vérificateur n'exige pas de ligne `usage` sur une activation TRIAL : la règle « essai toujours plafonné » n'existe que chez l'émetteur. Les outils (CLI de bureau `compact`, `OwnerCli compact`) savent encore émettre ces clés. | `Activation.kt:149,280-295`, `ActivationIssuer.kt:139` |
| 5 | **Élevé** | **Le lot loué est stocké en clair, et toute la sauvegarde de l'app est récupérable.** `RentalApi` déchiffre le lot puis l'installe **en clair** dans `TvLotStore` ; `RentalVault.putLot/readLot` n'est appelé nulle part. Le manifeste n'a pas `allowBackup="false"`, et les règles de sauvegarde n'excluent ni `rental/`, ni `lots/`, ni `activations.txt`, `clock.txt`, `first_run*`, ni `shared_prefs` (PIN). Avec `adb backup` puis `restore` sur Android ≤ 11 (GaiaOS), on rembobine les locations, la fenêtre d'essai et les pierres tombales. La documentation et le test `afterTheKeyIsGoneACopiedOrRestoredFileIsNoise` affirment l'inverse. | `RentalApi.kt:35-41`, `receiver/AndroidManifest.xml:49-57`, `res/xml/backup_rules.xml` |
| 6 | **Élevé** | **Le client peut réécrire l'état de licence avec son propre PIN.** `/api/ssh/enable` et `/api/ssh/key` ouvrent un shell qui tourne sous l'identité Linux de l'app. On peut alors réécrire `clock.txt`, créer `first_run_existing` (grâce), modifier `rentals.json` et copier les clés de location en clair (`rental/keys/*.key`). | `TvSshServer.kt:122-125`, `ActivationCenter.kt:182-193`, `RentalVault.kt:35-38` |
| 7 | **Élevé** | **L'essai permet encore de copier ou déplacer des médias.** `TrialPolicy` est une liste noire qui ne s'applique qu'aux routes HTTP. Restent ouverts : la réception de fichiers par Bluetooth (CBT1), `POST /api/usb/import`, `/api/ssh*` (SFTP et shell), `/stream/<nom>` avec `/api/library`, `/api/apk/install` et `/api/playurl`. | `TrialPolicy.kt:22-25`, `TvService.kt:257,773,815,819`, `BtServer.kt:113`, `ReceiverServer.kt:272-277` |
| 8 | **Élevé** | **La TV n'est pas hors ligne.** Points relevés : <ul><li>`INTERNET` et `usesCleartextTraffic="true"` pour tout le trafic ;</li><li>une sonde `connectivitycheck.gstatic.com` toutes les 60 s, sans consentement ;</li><li>aria2 avec BitTorrent et DHT à l'écoute sur 6881-6889 de toutes les interfaces ;</li><li>`/api/playurl` ;</li><li>battement, mises à jour et quiz vers le serveur si `DEFAULT_SERVER` est défini ;</li><li>une passerelle SOCKS Bluetooth sans authentification sur 127.0.0.1.</li></ul> | `receiver/AndroidManifest.xml:3,55`, `TvNetDiag.kt:19-26`, `TvService.kt:439`, `core/.../dl/Aria2Config.kt:80-84`, `gateway/BtGateway.kt:244` |
| 9 | **Élevé** | **Une clé compromise ne peut pas être révoquée, et le contrôle de séquence est inopérant.** Côté TV, `ActivationReceiver` est recréé à chaque appel, avec un `SeqState` neuf et un `RevocationState` vide, et `KeyRing.revoked` est vide. Conséquences : <ul><li>une clé de signature qui fuit (bureau ou coffre du téléphone, qui dérive aussi le secret maître des locations) reste acceptée par toutes les TV jusqu'à une nouvelle APK ;</li><li>une ancienne TV garde ses droits après le transfert de son poste.</li></ul> De plus, `parseKey` donne **toutes** les portées (dont `SUPER_UNLIMITED`) à une ligne sans `scopes=` : le défaut est ouvert. | `ActivationCenter.kt:69-73,102`, `FeatureGate.kt:72-75`, `Envelope.kt:102-107` |
| 10 | **Élevé** | **La persistance est fragile.** Points relevés : <ul><li>`activations.txt` est écrit sans fichier temporaire ni fsync, et l'erreur est avalée : une coupure de courant peut verrouiller la TV d'un client payant ;</li><li>un `rentals.json` illisible donne un registre vide (défaut ouvert) : plus aucun balayage, la règle « essai une fois » et le plancher d'horloge sont perdus ;</li><li>une exception du balayage arrête tous les contrats suivants sans alerte ;</li><li>`owned = { emptySet() }` : un lot acheté pendant sa location est supprimé à l'échéance.</li></ul> | `ActivationCenter.kt:170-173`, `RentalLedger.kt:141-142`, `RentalHub.kt:28,54`, `RentalSweeper.kt:61` |

**Points produit et juridiques prioritaires** (pas un avis juridique) :
- À l'échéance du plafond d'une production, **toute** l'activation cesse de compter, achats compris (`Activation.kt:183-184`) : la TV ne lit même plus les médias du client.
- L'avis d'usage marqué « [Texte à valider par le propriétaire…] » est **affiché tel quel** aux utilisateurs (`FeatureGate.kt:159-162`).
- Le contenu CC BY-SA est enfermé derrière l'activation alors que la licence interdit les mesures techniques de restriction. La catégorie « Langues » est entièrement sous CC BY-SA (`docs/LANGUES.md:283`), donc pas louable selon les propres règles du projet.

---

## 2. Constats par axe

Notation. **Gravité** : C = Critique, É = Élevé, M = Moyen, F = Faible. **Effort** : S ≤ 1 j, M ≤ 3 j, L > 3 j. Chemins Android relatifs à `android/`, sauf indication contraire.

### Axe 1 : Activation et licences

| id | Gravité | Fichier:ligne | Constat | Scénario | Correctif conseillé | Effort |
|---|---|---|---|---|---|---|
| A1-1 | **C** | `receiver/.../ActivationCenter.kt:185-194` ; `core/.../owner/FeatureGate.kt:32-34,52` ; `receiver/build.gradle.kts:31` | La grâce est accordée dès que `lastUpdateTime > firstInstallTime + 60 s`, c'est-à-dire après n'importe quelle réinstallation par-dessus. Elle est mémorisée dans `first_run.txt`, que l'on efface avec les données de l'app. La grâce vaut `Grace` = tout ouvert, sans `TrialPolicy` (`trial()` exige une clé). | Installer l'APK deux fois, puis « Effacer les données » tous les 30 jours : usage complet, gratuit et permanent. Vérifié dans le code ; non testé sur appareil. | Construire les APK distribuées avec `-Pcastbridge.graceDays=0` et en faire le **défaut** de `requireActivation=true`. Si une grâce reste nécessaire (parc existant), la limiter à une liste de codes d'appareil signée, ou à une date de fin absolue compilée dans l'APK. | S |
| A1-2 | **É** | `core/.../owner/Keys.kt:82-92` ; `Activation.kt:180-185` ; `ActivationCenter.kt:76-78` | `TvClock.now()` = `max(horloge, maximum vu)`. Si l'horloge recule, le temps reste figé au maximum vu tant qu'elle ne l'a pas rattrapé. `TvGate` n'applique aucun `ClockDoubt` (les locations sont suspendues, `RentalEngine.kt:73,118` ; les activations non). Le commentaire « a clock going BACK never … extends anything » est inexact. | Reculer la date de la TV de 5 ans juste après l'activation : un essai de 30 jours dure 5 ans. Autre cas : le client efface les données et remet une date antérieure à l'émission ; l'activation est réacceptée (`max(now, issuedAt)`), avec la même conséquence. | Ajouter un **compteur d'usage monotone** : `SystemClock.elapsedRealtime()` cumulé et persisté, et le plafond expire au premier des deux (date ou usage cumulé). Suspendre (écran « Vérifiez l'heure ») quand `rolledBack()` est vrai, comme pour les locations. | M |
| A1-3 | **É** | `Keys.kt:85` ; `Activation.kt:183` | Un saut en avant de moins de 400 jours est cru et devient le nouveau maximum vu, qui ne redescend jamais. | Une TV démarrée avec une pile d'horloge vide ou une date fausse d'un an brûle l'essai ou le plafond de production : le client est verrouillé sans faute de sa part. | Réutiliser la règle `AHEAD` des locations (au-delà de 45 j, suspension et confirmation) pour `TvGate`. | M |
| A1-4 | **É** | `Activation.kt:149,280-295` ; `ActivationIssuer.kt:139` ; `tools/activation-desktop/.../Cli.kt:88,269` | Une clé compacte acceptée porte `rights = emptyList()`, donc aucun `usage`. Dans `TvGate`, une activation sans `usage` compte toujours. Le vérificateur n'exige pas de `usage` pour TRIAL. | Le propriétaire (ou un outil) émet une clé d'essai saisissable : l'essai est illimité. | Côté TV : refuser une activation TRIAL sans ligne `usage` (`BAD_RIGHTS`), et pour une clé compacte, dériver un plafond de l'en-tête (version 3 portant les jours) ou refuser le type TRIAL. Côté outils : retirer `compact` pour l'essai. | S |
| A1-5 | **É** | `ActivationCenter.kt:69-73,102` ; `FeatureGate.kt:72-75` ; `Envelope.kt:102-107` | `receiver()` crée à chaque appel un `ActivationVerifier` avec `SeqState()` neuf (jamais persisté) et `RevocationState()` vide. `KeyRing(trusted)` n'a aucun révoqué. Le contrôle `STALE_SEQUENCE` et la révocation (clé ou poste) sont donc **inopérants** sur la TV. Le HANDOFF le reconnaît en partie (« liste de révocation côté TV : pas encore »). | Une clé de bureau ou un coffre de téléphone volé signe des activations, y compris `super` si la clé a la portée, acceptées par tout le parc jusqu'à une nouvelle APK. Un poste transféré garde ses droits sur l'ancienne TV. | Persister `SeqState` et une `RevocationState` alimentée par une liste `cbr1` signée, reçue par fichier, Bluetooth ou le téléphone. Prévoir dès maintenant une **clé de secours hors ligne** dans l'anneau. | M |
| A1-6 | M | `ActivationCenter.kt:71` | Une ligne de clé de confiance sans `scopes=` reçoit `KeyScope.ALL` (défaut ouvert). Une portée inconnue est ignorée en silence. | Une ligne de clé serveur mal recopiée dans le fichier de confiance (sans `scopes=`) donne au serveur `SUPER_UNLIMITED` et `COMMAND_OPEN_ALL`. Contenu réel du fichier : **non vérifié**. | Refuser la ligne (ou ne lui donner aucune portée) quand `scopes=` manque ; faire échouer le build. | S |
| A1-7 | M | `receiver/.../TvService.kt:171-196` | Le verrou n'est évalué qu'au démarrage du service (`startCore`) et de `PlayerActivity`. Une activation dont le plafond expire en cours de fonctionnement laisse l'API HTTP (envoi, SSH…) active jusqu'au prochain redémarrage. | TV jamais éteinte (veille) : usage au-delà de l'échéance. | Réévaluer `locked()` périodiquement (déjà une minuterie de 15 min) et arrêter le cœur ; contrôler `locked()` dans la garde de route. | S |
| A1-8 | M | `backend/.../licenses/WireActivation.java:98-147` ; `tools/activation/verify_vectors.py:266-271` ; `Activation.kt:72-73` | **Divergence entre vérificateurs.** Pour un droit de genre inconnu : Kotlin le garde (`Right.Unknown`, accepté en production) ; Java et Python le refusent (`MALFORMED`). Python ne vérifie pas le format des identifiants. Aucun des trois ne borne `usage` (`to > from`, durée maximale). | Un jeton émis par un outil plus récent est accepté par la TV mais refusé par le serveur ou le vérificateur Python, ou l'inverse. | Aligner sur la spécification (RENTAL-LOTS § 10.1 : genre inconnu conservé) dans Java et Python ; ajouter les bornes `usage` aux trois ; ajouter des vecteurs. | S |
| A1-9 | M | `/api/apk/install` (`receiver/.../UpdateInstaller.kt:133`) | Avec `force=1`, une APK plus ancienne signée par la même clé s'installe (PIN requis). Toute APK non verrouillée déjà distribuée (bêta) reste un contournement permanent, par cette route ou par désinstallation puis installation. | Le client retrouve une ancienne APK non verrouillée et l'installe. Versions anciennes en circulation : **non vérifié**. | Refuser la rétrogradation en release ; recenser et retirer les APK non verrouillées publiées ; à terme, changer la clé de signature de la TV au passage à la version verrouillée (rupture assumée). | S |
| A1-10 | F | `KeyBadge.kt:26` vs `Activation.kt:183` | Le badge et `TvGate` ne calculent pas « l'activation compte » de la même façon (le badge ignore `startsAt − skew`). | Badge « ESSAI » pendant que la TV est verrouillée, ou l'inverse, dans une fenêtre limite. | Une seule fonction partagée. | S |
| A1-12 | **É** | `core/.../owner/TrialPolicy.kt:22-25` ; `TvService.kt:257,773,815,819` ; `BtServer.kt:113` ; `core/.../tv/BtProtocol.kt:187` ; `ReceiverServer.kt:272-277` | `TrialPolicy` est une liste noire qui ne s'applique qu'aux routes HTTP. Sans contrôle de l'essai : réception de fichiers Bluetooth (CBT1), `POST /api/usb/import`, `/api/ssh*` (SFTP et shell), `GET /stream/<nom>` avec `/api/library` et `/api/thumb`, `/api/apk/install`, `/api/reset`, `/api/playurl`. | Une TV en essai reçoit des films par Bluetooth, importe depuis la clé USB, ou en copie par SFTP ou `/stream` : la restriction « streaming et Sudoku seulement » ne tient pas. | Passer à une **liste blanche** de routes pour l'essai ; contrôler `ActivationCenter.trial()` dans `BtProtocol`/`BtServer`, `UsbImporter` et `SshControl`. | S/M |
| A1-11 | F | `ActivationCenter.kt:121,177` | `installedAt` est l'heure murale et non `TvClock.now()`. Au rechargement, l'activation est revérifiée « à cette date ». | Horloge très en avance au moment de l'installation : l'activation peut disparaître au redémarrage suivant. | Persister `now()`. | S |

### Axe 2 : Chiffrement des locations et des lots

| id | Gravité | Fichier:ligne | Constat | Scénario | Correctif conseillé | Effort |
|---|---|---|---|---|---|---|
| A2-1 | **C** | `core/.../lots/RentalKeys.kt:76-84,87-97` ; `owner/LotKeys.kt:53-56` ; `owner/Envelope.kt:57` ; `owner/OwnerFrames.kt:49-50` ; `ActivationCenter.kt:144-150` | `kek = HKDF("TYPE=empreinte…")` est calculé sur les **empreintes** (`Fingerprints.byKind`). Ces mêmes valeurs figurent dans la cible de l'enveloppe (`factor=TYPE\|empreinte`), donc dans tout fichier `activation`, QR ou message WhatsApp. Elles figurent aussi dans `device-request.txt`, que la TV écrit dans `Download/CastBridge` de la clé USB. Le commentaire de `makeBox` (« the factor VALUES stay secret to whoever lacks the TV ») est faux pour les empreintes, qui sont le seul secret utilisé. | Script d'une vingtaine de lignes (algorithme publié dans RENTAL-LOTS § 10.4) : il lit l'activation, calcule le KEK, ouvre la boîte et déchiffre les lots scellés (que le téléphone transporte), pour toujours, après l'échéance et sur n'importe quel appareil. Preuve exécutable non faite (module `cryptography` absent) ; **confirmé par lecture du code**. | Envelopper la clé de location pour une **clé publique propre à l'installation** : X25519 générée dans l'Android Keystore (non exportable), sa partie publique jointe à la demande d'appareil, enveloppe ECIES. À défaut, ajouter au KEK un secret de la TV qui n'apparaît dans **aucun** jeton ni fichier. Nouvelle version de format, nouveaux vecteurs Kotlin, Java et Python. | L |
| A2-2 | **É** | `RentalApi.kt:35-41` ; `RentalVault.kt:67-78` (inutilisé) ; `docs/RENTAL-LOTS.md:61,111` | Le lot scellé est déchiffré et réécrit **en clair** à la place du `.part`, puis installé dans `TvLotStore` (`files/lots`). Le coffre chiffré n'est jamais utilisé. La protection contre la « copie passive » et la restauration n'existe donc pas pour la copie installée. | Une sauvegarde adb ou un SFTP pendant la location donne une copie en clair. | Garder le lot scellé dans le coffre et déchiffrer en mémoire à la lecture (`readLot`) ; corriger la documentation et le test qui affirme l'inverse. | M |
| A2-3 | **É** | `receiver/AndroidManifest.xml:49-57` ; `res/xml/backup_rules.xml` ; `data_extraction_rules.xml` | `allowBackup` n'est pas déclaré, donc vrai par défaut. Seuls `trusted_phones*` et les rapports parentaux sont exclus. | `adb backup` pendant une location, puis `adb restore` après l'échéance : registre `LIVE`, clé présente, horloge ancienne. La location revit. L'essai « une seule fois » revit aussi. | `android:allowBackup="false"` sur `:receiver` (comme `:owner`), ou au minimum exclure `rental/`, `lots/`, `activations*`, `clock.txt`, `first_run*`, `ssh/` et `shared_prefs`. | S |
| A2-4 | **É** | `RentalVault.kt:35-38` ; `sshd/.../TvSshServer.kt:122-125` | Les clés de location sont en clair dans `files/rental/keys`, lisibles par le shell SSH ouvert avec le PIN. La limite est reconnue (RENTAL-LOTS § 11), mais le shell est une fonction **livrée** de l'app, pas seulement le cas d'une « TV en mode test ». | Le client copie les clés et les lots ; la destruction ultérieure ne sert à rien. | En release : SFTP limité au dossier média, ni shell ni exec ; clés enveloppées par le Keystore. | M |
| A2-5 | M | `RentalLedger.kt:75` | Règle « essai une fois pour la vie de l'application » : en pratique, pour la vie des **données**. Effacer les données ou réinstaller la remet à zéro. Le nom du test `theWindowIsGrantedOnceForTheLifeOfTheApplication` est trompeur. | Fenêtre de 12 h de lots renouvelable à volonté, si la clé d'essai est encore dans sa fenêtre d'installation ou avec un recul d'horloge (A1-2). | Côté serveur ou outil, n'émettre la fenêtre d'essai qu'une fois par code d'appareil (registre) ; côté TV, ancrer la règle dans un stockage qui survit à l'effacement (non garanti sur Android). Documenter la limite. | S |
| A2-6 | M | `RentalApi.kt:24` ; `tools/activation-desktop/.../Cli.kt:205,234-236` ; `ownerlib` (pas de `RentalPolicy`) | La TV considère par défaut tout lot comme `RESERVED`, contrairement au principe affiché « famille inconnue = refus ». L'outil de bureau ne vérifie les lots libres que si `--catalogue` est fourni. La console du téléphone ne les vérifie pas. | Un lot CC BY-SA est loué, scellé, puis supprimé : non-respect de la licence (voir axe 8). | Rendre `--catalogue` et `--lots-libres` obligatoires pour toute location ; même contrôle dans la console ; famille signée dans le catalogue de lots et vérifiée par la TV. | S |
| A2-7 | M | `RentalKeys.kt:27-28` | `masterFrom(signer)` = SHA-256(signature Ed25519 déterministe). Si la clé de signature fuit, toutes les clés de location passées et futures sont dérivables. Faire tourner la clé change le maître, ce qui empêche de prolonger ou de resceller une location existante. Le serveur ne détient **pas** le maître (vérifié par grep : aucun `masterFrom` ni `RentalKeys` dans `backend/`). | Clé du coffre téléphone compromise : déchiffrement de toutes les locations (de toute façon déjà possible, A2-1). | Après A2-1 : maître distinct de la clé de signature, conservé chiffré à part, avec un identifiant de version de maître dans la ligne `rental`. | M |
| A2-8 | F | `RentalLedger.kt:163-164` ; `RentalVault.kt:37,73` | Écriture via fichier temporaire puis renommage, mais sans fsync ; repli `delete()` puis `renameTo()` non atomique. | Coupure de courant : fichier vide. | `FileOutputStream.fd.sync()` avant renommage ; garder un `.bak`. | S |

### Axe 3 : Réseau et principe « TV hors ligne »

| id | Gravité | Fichier:ligne | Constat | Scénario | Correctif conseillé | Effort |
|---|---|---|---|---|---|---|
| A3-1 | **É** | `receiver/AndroidManifest.xml:3,55` ; `TvNetDiag.kt:19-26` ; `TvService.kt:439` | Permission `INTERNET`, `usesCleartextTraffic="true"` global, pas de `network_security_config`. Sonde `http://connectivitycheck.gstatic.com/generate_204` toutes les 60 s (10-30 s hors ligne) **sans consentement**. Le diagnostic (manuel) contacte google.com et api.ipify.org (IP publique). | Contraire au principe du propriétaire ; fuite de métadonnées (présence, IP) vers des tiers. | Désactiver la sonde par défaut (diagnostic manuel seulement) ; `network_security_config` qui n'autorise le clair que vers localhost et les adresses privées. | S |
| A3-2 | **É** | `core/.../dl/Aria2Config.kt:80-84` ; `jniLibs/*/libaria2c.so` | Les téléchargements de la TV utilisent aria2 : http, magnet, torrent, avec **DHT et BitTorrent à l'écoute sur 6881-6889** de toutes les interfaces. | La TV est joignable par des pairs d'Internet ; risque juridique du partage BitTorrent ; incompatible avec « TV hors ligne ». | Retirer les téléchargements de la build distribuée, ou les faire passer par le téléphone ; à défaut `enable-dht=false`, `listen-port` fermé, `bt-*` désactivés. | M |
| A3-3 | M | `TvConnect.kt:72-92` ; `TvService.kt:228` ; `ServerLink.kt:152,172` | Battement toutes les 15 min, télémétrie, crashs, mises à jour toutes les 12 h, quiz du jour. Soumis au consentement, et seulement si `DEFAULT_SERVER` est défini au build. | Une build compilée avec `DEFAULT_SERVER` contacte le serveur. | Saveur « hors ligne » sans `ServerLink` ; faire de `DEFAULT_SERVER` vide une vérification de build pour la TV distribuée. | S |
| A3-4 | M | `ReceiverServer.kt:414-422` | `/api/playurl` fait ouvrir n'importe quelle URL http(s) à la TV (PIN requis). | La TV sert de relais vers Internet ou le réseau local (SSRF limitée au lecteur). | Fermer en édition distribuée ou limiter au réseau local. | S |
| A3-5 | M | `ReceiverServer.kt:78,458` ; `tv/Security.kt:28-47` ; `TvPrefs.kt:12-15` | `NanoHTTPD(port)` écoute toutes les interfaces, y compris une IPv6 globale ; pas de contrôle de `Host` (rebinding DNS). PIN à 6 chiffres jamais renouvelé, accepté en paramètre `?pin=` : CSRF possible par un POST `no-cors` depuis une page web, PIN visible dans les journaux. Blocage par IP (5 échecs puis 60 s) contournable en changeant d'adresse IPv6 ; table d'IP non bornée. | Une page web visitée sur le réseau local, ou un voisin en IPv6, tente des PIN. | N'accepter que les adresses privées ; vérifier `Host` ; PIN en en-tête seulement ; budget global d'échecs avec attente croissante ; rotation du PIN. | M |
| A3-6 | M | `gateway/BtGateway.kt:244` ; `BtGatewayHost.kt:20-35` | Passerelle SOCKS 127.0.0.1 sans authentification, qui fait sortir le trafic par le téléphone. Une seule clé d'échecs `"bt-gateway"` : un attaquant bloque tous les téléphones. | Une autre app de la TV utilise la connexion Internet du téléphone. | Authentification SOCKS par jeton aléatoire ; échecs comptés par appareil. | S |
| A3-7 | M | `UpdateInstaller.kt:133` | Voir A1-9 (rétrogradation via `force=1`). | | | |
| A3-8 | M | `devbridge/.../DevService.kt:34-35` ; `DevCommands.kt:66-67` | CastBridge Dev : absente des APK TV et téléphone (vérifié). Mais si elle est installée : SSH au démarrage, clés compilées, installation de paquets sans action de l'utilisateur, `QUERY_ALL_PACKAGES`. | Une APK Dev qui fuit est une porte dérobée complète. | Clé de signature distincte, durée de vie limitée (date d'expiration compilée), jamais copiée sur la clé USB du client. | S |
| A3-9 | F | `receiver/AndroidManifest.xml:76-85` ; `ReceiverServer.kt:263,468` | `BootReceiver` exporté sans permission (n'importe quelle app démarre le service). `admin.html` sans `X-Frame-Options` ni CSP. Les messages d'exception sont renvoyés au client. | Faible (le PIN reste à saisir). | `exported=false` si possible ; en-têtes de sécurité ; message d'erreur générique. | S |
| A3-10 | F | `ownerlib/.../OwnerStore.kt:14-21` ; `sender/res/xml/backup_rules.xml` | Le coffre propriétaire du téléphone (`owner-vault.txt`, `owner_guard.xml`) est inclus dans la sauvegarde Google et le transfert entre appareils, malgré le commentaire « excluded from every backup ». | Récupération du coffre, à casser hors ligne (PBKDF2 à 600 000 itérations, phrase d'au moins 12 caractères) ; restaurer `owner_guard` remet le compteur d'essais à zéro. | Exclure ces fichiers ; envelopper le coffre par le Keystore. | S |

### Axe 4 : Serveur (`backend/`)

| id | Gravité | Fichier:ligne | Constat | Scénario | Correctif conseillé | Effort |
|---|---|---|---|---|---|---|
| A4-1 | M | `licenses/ActivationService.java:170,199-208,222-225` ; `LicenseService.java:243-244` ; `Role.java:11` | Une réémission pour le même poste recommence le plafond à partir du nouvel `issuedAt`. Le rôle SUPPORT réémet sans TOTP et choisit `usageDays`. Une licence TRIAL peut n'avoir aucune date de fin. | Un compte support (ou volé) renouvelle les essais sans fin. | Date de fin obligatoire pour TRIAL ; plafond compté depuis la première émission du poste ; `usageDays` interdit en SUPPORT. | S/M |
| A4-2 | M | `licenses/Actor.java:15` ; `LicenseApiController.java:51` ; `RateLimitFilter.java:50` | Le jeton d'administration statique vaut OWNER « fort » (pas de TOTP) et échappe à la limite de débit. | Fuite du jeton (`.env`, environnement du conteneur) : contrôle total des licences. | Jeton distinct et restreint pour les licences, ou en-tête TOTP exigé ; IP dans l'audit ; rotation. | M |
| A4-3 | M | `admin/AdminAccounts.java:57-66` | 5 échecs = compte verrouillé 15 min, compté par compte et non par IP ; le nom réel figure dans `backend/.env.example:12`. | N'importe qui tient le propriétaire hors de la console. | Verrou par (compte, IP), délai croissant, file dédiée pour `/admin/login` ; nom de remplacement dans `.env.example`. | S |
| A4-4 | M | `server/castbridge_server.py:97-103,252` | Le serveur vidéo Python sur PC (distinct du serveur de licences) écoute 0.0.0.0 sans authentification : relais http/rtsp/udp ; avec `--allow-local`, lecture de fichiers locaux via ffmpeg. | Un appareil du réseau local s'en sert comme relais ou lit des fichiers. | Jeton, liste d'hôtes, dossier racine, écoute sur une seule interface. | S |
| A4-5 | F | `licenses/PublicLicenseController.java:36-40` ; `RevocationService.java:55-81` | Un appareil enregistré interroge les droits de n'importe quel `deviceCode` et met à jour son `last_seen`. | Énumération des licences d'autrui. | Lier le code aux appairages ; ne pas modifier `last_seen` ici. | S |
| A4-6 | F | `backend/pom.xml:9` | Spring Boot 3.5.16. La fin du support libre de la série 3.5 est à vérifier sur spring.io (probablement passée) ; aucune CVE précise n'est affirmée ici. | Correctifs de sécurité plus fournis. | Planifier le passage en 4.0.x. | L |
| A4-7 | F | `application.yml:42,79` ; `docker-compose.yml:67` | `useSSL=false` vers la base (acceptable sur le réseau Docker interne). La clé de signature des mises à jour peut être fournie dans l'environnement. | Fuite par `docker inspect`. | Variantes `_FILE` et secrets Docker partout. | S |
| A4-8 | F (non commité) | `lots/BundleCatalogController.java` ; `SignedBundleCatalog.kt` | Bonne conception : le serveur relaie seulement, le catalogue est signé hors ligne, l'ancienneté est contrôlée. Mais la même clé Ed25519 sert aux mises à jour, aux catalogues de lots et aux bouquets (séparés seulement par un préfixe), et le fichier est relu à chaque requête. | Réutilisation de clé entre usages. | Clé dédiée ou préfixe de domaine documenté ; cache en mémoire. | S |

### Axe 5 : Hygiène du dépôt et secrets

| id | Gravité | Fichier:ligne | Constat | Correctif conseillé | Effort |
|---|---|---|---|---|---|
| A5-1 | M | `.gitignore` | `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `release.pass*`, `.env` à la racine, `secrets/` et `*.apk` ne sont pas ignorés (seuls `backend/.env` et `backend/secrets/` le sont). Aujourd'hui aucun n'est suivi (vérifié). | Ajouter ces motifs, plus un crochet pre-commit de détection de secrets. | S |
| A5-2 | M | `android/ownerlib/build.gradle.kts:9-16` ; `SuperAdminGate.kt` | Le hachage bcrypt du mot de passe super-administrateur est compilé dans toute APK téléphone construite sans `-Pcastbridge.noSuperAdmin=true`, donc attaquable hors ligne (limite reconnue dans le code). | « Pas de super-administration » par défaut pour toute APK distribuée ; phrase longue. | S |
| A5-3 | M | `android/receiver/src/main/jniLibs/*/libaria2c.so` | Binaires aria2 (GPLv2+, 8,3 et 6,1 Mo) sans texte de licence ni offre de sources. | Ajouter COPYING et l'offre de sources (le script `tools/build-aria2-android.sh` existe), ou retirer aria2 (A3-2). | S |
| A5-4 | M | `version.properties:2` ; `docs/HANDOFF.md:4,7` | `docs/RELEASES.md` est cité mais **n'existe pas**. L'en-tête du HANDOFF date du 2026-10-01 et cite `claude/plug-and-play-robust` et `feat/ssh` comme branche d'intégration (en réalité `integration/agents`). L'entrée la plus récente dit « rien commité » alors que le travail est dans `1c26a11`. | Écrire RELEASES.md ; mettre à jour l'en-tête et la section 0 du HANDOFF. | S |
| A5-5 | F | `content/quiz/lots/*.zip` (292 fichiers, environ 28 Mo) ; `docs/LEARN-REVIEW.md` (1 Mo) | Artefacts générés suivis ; dépôt d'environ 117 Mo, `.git` d'environ 161 Mo (OneDrive). | Git LFS ou publication hors dépôt. | M |
| A5-6 | F | `backend/src/test/resources/application-test.yml:7,12,22` | Jeton et mot de passe de test, plus la clé de test RFC 8032 (jamais acceptée par le code de production : vérifié). | S'assurer qu'ils ne sont jamais réutilisés. | S |
| A5-7 | F | `git stash list` | `stash@{0}` « Teleport auto-stash » (feat/ssh) : seulement un changement de mode 644 → 755 sur `ServerLink.kt`. | Peut être supprimé. | S |
| A5-8 | F | branches | Non fusionnées : `wip/external-ai-changes` (220 fichiers non relus, dont une clé SSH publique codée en dur, une porte dérobée de fait : **ne jamais fusionner**), `audit/external-ai`, `origin/claude/agy-mission`, `origin/claude/castbridge-sender` (documents). | Archiver `wip/external-ai-changes` sous un tag, puis supprimer la branche distante. | S |
| A5-9 | F | `tools/activation/*.json`, `lots.json` | Vecteurs et lots générés modifiés dans le même commit que leurs générateurs (`17a79f2`). Concordance **non vérifiée** (Gradle et `cryptography` requis). | Lancer en CI le vérificateur Python, `:core:test` et les tests serveur des vecteurs. | S |

Les versions sont cohérentes : TV 0.14.15-beta (code 56, +1 verrouillée), téléphone 1.2.27-beta (57), propriétaire 0.2.1 (3), conformes au message du commit `1c26a11`.

### Axe 6 : Correction et qualité

| id | Gravité | Fichier:ligne | Constat | Correctif conseillé | Effort |
|---|---|---|---|---|---|
| A6-1 | **É** | `ActivationCenter.kt:170-173,183,193` | `activations.txt`, `clock.txt` et `first_run.txt` sont écrits par `writeText` direct (troncature puis écriture) et l'erreur est avalée (`runCatching`). | Fichier temporaire, fsync, renommage, plus un `.bak` relu en secours. | S |
| A6-2 | **É** | `RentalLedger.kt:141-142` | Un `rentals.json` illisible donne un registre vide (défaut ouvert) : plus de balayage, plancher d'horloge perdu, règle « essai une fois » oubliée. | Registre illisible : état suspect, locations suspendues, `.bak`. | S/M |
| A6-3 | M | `RentalHub.kt:39,46-54` ; `RentalSweeper.kt:61` | `runCatching{…}.getOrDefault(emptyList())` sur le balayage, le comptage et les statuts. `check(destroyKey)` lève une exception qui interrompt les contrats suivants, sans journal. | Un bloc try par contrat ; échec consigné dans le journal et affiché. | S |
| A6-4 | M | `RentalHub.kt:28` | `owned = { emptySet() }` : un lot loué puis acheté est supprimé à l'échéance (le test JVM passe, mais pas le chemin réel). | Brancher les lots couverts par `TvGate`. | S |
| A6-5 | M | `RentalSweeper.sweep` ; `RentalApi.handleBody` | Balayage non synchronisé, appelé depuis 4 fils ; `RentalApi` réécrit le `.part` hors du verrou de `TvLotStore`. | Verrou unique pour les locations. | S |
| A6-6 | M | `TvLotStore.receive` ; `RentalApi.kt:35-36` ; `LearnHub.kt:118` | Un lot peut faire jusqu'à 10 Mo (tout le budget) au lieu des 3 Mo annoncés ; le chiffré, le clair et le tampon sont tous en mémoire, soit environ 30 Mo sur une TV 32 bits, sans `largeHeap`. | Plafond de 3 Mo à la réception ; déchiffrement par flux. | S |
| A6-7 | F | `LearnActivity` | Le compteur d'usage (12 h d'essai) compte même quand personne ne touche la télécommande ; un `Thread` brut est créé chaque minute ; `restoreHint` n'est pas retiré dans `onDestroy`. | Compter une minute seulement si une touche a été pressée récemment. | S |
| A6-8 | F | `LearnViews`, `QuizViews`, `SudokuActivity`, `LanguesActivity` | Pas de `contentDescription` (TalkBack) ; focus au D-pad jamais validé sur la TV (HANDOFF § 9). | Passe d'accessibilité sur la TV réelle. | M |

### Axe 7 : Lacunes de tests

| id | Gravité | Constat | Correctif conseillé |
|---|---|---|---|
| A7-1 | **É** | **Jamais exercé sur un appareil réel** (HANDOFF et BILAN) : <ul><li>locations sur la TV GaiaOS (émulateur seulement) ;</li><li>livraison téléphone → TV (`RentalDelivery`) ;</li><li>Bluetooth réel pour l'envoi de clé ;</li><li>APK avec le contenu de base (+1,7 Mo) ;</li><li>Parental ;</li><li>transfert multivoie ;</li><li>tunnel Bluetooth v2 ;</li><li>démarrage automatique sur GaiaOS ;</li><li>focus au D-pad.</li></ul> | Campagne sur la TV de référence, avec liste de contrôle signée. |
| A7-2 | **É** | Aucun test d'attaque des licences : réinstallation (A1-1), recul d'horloge (A1-2), clé compacte d'essai (A1-4), restauration adb (A2-3), ouverture de la boîte à partir du seul jeton (A2-1), routes d'essai non bloquées (A1-12). | Ajouter ces tests **négatifs** au cœur (JVM) et à `tools/rental-test`. |
| A7-3 | M | Coupure de courant simulée par `onStep`, jamais par un fichier tronqué ; aucun test de `rentals.json`, `activations.txt` ou `clock.txt` corrompu. | Tests de corruption et de troncature. |
| A7-4 | M | Tests qui figent un comportement discutable : <ul><li>`UsageCeilingTest.aProductionActivationWithACeilingEnds…` (perte des achats) ;</li><li>`LicenseAndGateTest.kt:251` (exige que l'avis contienne « à valider par le propriétaire ») ;</li><li>`TrialWindowTest.theWindowIsGrantedOnce…` (nom trompeur) ;</li><li>`RentalTest.afterTheKeyIsGoneACopiedOrRestoredFileIsNoise` (propriété non mise en œuvre dans l'app) ;</li><li>`RentalTest.anAcquiredRentalCountsFromActivation…` (décision encore ouverte).</li></ul> | Revoir chacun après décision du propriétaire. |
| A7-5 | F | Une vingtaine de tests réseau ou Bluetooth avec `Thread.sleep` ou l'horloge réelle ; trois tests instables connus (HANDOFF). Le vérificateur Python ne tourne pas sans `cryptography`. | `requirements.txt` ; horloges injectées ; CI. |

### Axe 8 : Contenu

| id | Gravité | Constat | Correctif conseillé |
|---|---|---|---|
| A8-1 | **É** | La CC BY-SA 4.0 (§ 2.a.5.B) interdit d'imposer des mesures techniques effectives. Or : <ul><li>le contenu original Langues est publié sous CC BY-SA (`docs/LANGUES.md:283`) ;</li><li>la TV n'ouvre **aucun contenu sans clé** ;</li><li>la TV traite par défaut tout lot comme réservé (A2-6) ;</li><li>la catégorie Langues (6 Go) est donc ni louable ni verrouillable selon les règles du projet (RENTAL-LOTS § 11).</li></ul> | Décision du propriétaire : soit Langues reste sous licence réservée, soit l'archive libre est réellement publiée **et** les lots libres sont lisibles sans mesure technique. Famille signée dans le catalogue. |
| A8-2 | M | Les 259 packs et 2 537 fiches de `content/learn` sont tous en `draft`, rédigés par IA, non relus par un enseignant, sans champ `license`. La porte « 70 % de correspondance au programme » est introuvable. | Ne rien vendre ni louer avant relecture ; champ licence et crédits par pack. |
| A8-3 | M | Une seule mention « pas un conseil juridique » sur 10 packs de droit ; pas d'avertissement au niveau des packs santé et SVT. | Avertissement par pack, affiché à l'ouverture. |
| A8-4 | F | Les `programRef` sont prudents (« à vérifier », « reconstitué ») : ne pas afficher « programme officiel » dans la boutique. Pas de doublon exact, mais des consignes génériques répétées. | Libellés explicites. |
| A8-5 | M | `synthetic: true` est lu (`LangPack.kt`) mais aucun écran de la TV ne l'affiche ; pas d'écran Crédits. | Badge « voix de synthèse » et écran Crédits. |
| A8-6 | M | Données d'enfants. Le contrôle parental est bien local (PBKDF2, conservation de 90 jours, pas de PIN par défaut). Mais : <ul><li>le consentement à la télémétrie est donné sur la TV par n'importe qui, possiblement un enfant ;</li><li>le serveur VPS est hors du Cameroun ;</li><li>les formalités de la loi 2024/017 sont « à valider par un juriste » (`docs/TELEMETRY.md:163-170`) ;</li><li>les profils contiennent des prénoms.</li></ul> | Consentement par le parent (téléphone, PIN parental) ; registre des traitements ; avis d'un juriste. |
| A8-7 | F | « Apprendre » est ouvert en essai avec le contenu de base : décision du propriétaire en attente. | Trancher et documenter dans TRIAL-EDITION. |

### Axe 9 : Risques produit et juridiques à signaler au propriétaire (pas un avis juridique)

| id | Gravité | Constat | Piste |
|---|---|---|---|
| A9-1 | **É** | À l'échéance d'un plafond de production, l'activation entière cesse de compter, **achats compris** (`Activation.kt:183-184`) : `PlayerActivity` n'affiche plus que l'écran d'activation, sans période de grâce. Cela contredit le commentaire de `TvGate` (« falls back to its acquired rights »). | Décider : retour au mode réduit (achats et lecture des médias personnels conservés) plutôt que verrouillage total ; préavis à l'écran. |
| A9-2 | **É** | L'« Avis d'usage » provisoire (« [Texte à valider par le propriétaire : il ne constitue pas un avis juridique.] ») est affiché aux utilisateurs (`FeatureGate.kt:159-162`, `ActivationActivity.kt:78`), et un test le fige. Le texte de transparence d'`ORDRES.md` § 11 n'est pas validé non plus. | Texte définitif validé, puis mise à jour du test. |
| A9-3 | M | Pas de conditions de vente ou de location, ni de règles de remboursement, rétractation, conservation des données ou recours. Points à écrire : suppression automatique à l'échéance, durée comptée dès l'activation (pas dès l'ouverture), effet d'une horloge fausse (suspension), perte en cas de réinitialisation, essai unique. | Rédiger des CGV et CGU de location avant toute vente. |
| A9-4 | M | L'usage de l'essai est compté même sans utilisateur devant l'écran (A6-7) : contestable par un client. | Détection d'inactivité. |
| A9-5 | F | Titularité des droits sur un contenu rédigé par IA, avant de le vendre ou de le publier sous CC BY-SA. | Avis d'un juriste. |
| A9-6 | F | BitTorrent embarqué sur la TV (A3-2) : exposition à des usages illicites attribuables au revendeur. | Retirer de la build distribuée. |

---

## 3. Ce qui est solide (vérifié)

**Format et vérification des activations**
- Enveloppe `cbx1` unique.
- Forme canonique reconstruite et comparée octet pour octet (`Activation.from`, `canonicalPayload`).
- Signatures Ed25519.
- Contrôle des portées par type : TRIAL/PRODUCTION/REACTIVATE, `openall` ⇒ `COMMAND_OPEN_ALL`, `super` ⇒ `SUPER_UNLIMITED`.
- Fenêtre d'installation de 48 h appliquée sans exception (y compris aux clés compactes).
- Liaison au matériel k parmi n ; « suspect » au lieu d'un refus net.
- Décodage en échec fermé (`getOrNull` ⇒ refus).
- Bornes des locations identiques en Kotlin, Java et Python (`bounds`, `rentalBounds`, `rental_ok`).

**Conception du balayage des locations**
- Étapes idempotentes et persistées ; clé détruite avant les fichiers (réécriture à zéro puis au hasard, avec fsync).
- Pierre tombale ; suppression limitée aux lots enregistrés comme loués.
- Chemins du coffre vérifiés (expressions régulières et chemin canonique).
- Clock doubt `BEHIND`/`AHEAD` qui suspend au lieu de supprimer.
- Horloges injectées dans les tests.

**Principes**
- Aucune clé privée dans les APK.
- Clés publiques de confiance injectées au build depuis un fichier hors dépôt ; le build refuse le verrou sans clé.
- Facteurs de test réservés au debug.
- La console super-administrateur fait toujours tout le travail bcrypt, avec un blocage progressif.

**API de la TV**
- PIN comparé en temps constant et généré par SecureRandom.
- Jetons de téléphone de 256 bits stockés hachés ; un jeton ne peut ni activer SSH, ni installer une APK ou une activation.
- `safeName` empêche de sortir du dossier.
- Pas d'en-têtes CORS ; corps limités à 4 Mo.
- `admin.html` n'écrit qu'en `textContent` (pas de XSS repéré).
- SSH : clés seulement, réseau local seulement, sans redirection, 3 essais.
- RPC aria2 sur la boucle locale avec secret.
- Mises à jour signées (SHA-256, même signataire).
- Une TV verrouillée ne démarre ni serveur ni télémétrie.

**Serveur**
- Console : BCrypt coût 12, TOTP, rotation de session, CSRF actif, cookies `HttpOnly`/`Secure`/`SameSite=strict`, CSP stricte, frame-deny.
- Jeton d'administration en temps constant, au moins 32 caractères.
- Actuator sur un port non publié ; pas de H2 ni de Swagger en production.
- Comptage des postes sous `SELECT … FOR UPDATE` avec signature dans la transaction ; réémission idempotente (`idem_key`).
- Durées bornées (essai 1-365 j, jamais illimité).
- Requêtes SQL paramétrées.
- Journaux sans secret ; audit chaînable par HMAC.
- Clé de signature dans `/run/secrets`, avec contrôle de chemin ; TOTP chiffrés en AES-GCM.
- Validation des zips (évasion de chemin, bombe).
- Le serveur ne détient **pas** le secret maître des locations.

**Dépôt**
- Aucune clé privée, keystore, `local.properties` ni `.env` réel suivi.
- `:owner` déclare `allowBackup=false`.
- CastBridge Dev n'est intégrée à aucune APK distribuée.
- Versions cohérentes.

---

## 4. Plan priorisé sur 2 semaines

**Semaine 1 : fermer les contournements de licence bon marché (rien à déployer sur le serveur, `main` intacte)**

| Jour | Travail | Constats |
|---|---|---|
| J1 | `graceDays=0` par défaut avec `requireActivation=true` (et grâce limitée à une liste signée si besoin). `allowBackup="false"` (ou exclusions) sur `:receiver` et pour le coffre de `:sender`. Refus de la rétrogradation par `/api/apk/install`. | A1-1, A2-3, A3-10, A1-9 |
| J2 | `TrialPolicy` en **liste blanche**, plus des contrôles `trial()` dans `BtProtocol`/`BtServer`, `UsbImporter`, `SshControl` et `/stream`. SSH en release : SFTP limité au dossier média, ni shell ni exec. | A1-12, A2-4 |
| J3 | Le vérificateur exige `usage` sur TRIAL ; retrait des clés compactes d'essai. `parseKey` en défaut fermé. Réévaluation périodique du verrou. | A1-4, A1-6, A1-7 |
| J4 | Compteur d'usage monotone persisté ; règles `BEHIND`/`AHEAD` appliquées à `TvGate`. | A1-2, A1-3 |
| J5 | Écritures atomiques (fsync et `.bak`) pour les activations, l'horloge et le registre ; registre illisible = suspension ; balayage contrat par contrat ; branchement de `owned` ; verrou unique. Tests négatifs JVM pour tout ce qui précède. | A6-1 à A6-5, A7-2, A7-3 |

**Semaine 2 : refondre l'enveloppe des locations et revenir au hors ligne**

| Jour | Travail | Constats |
|---|---|---|
| J6-J8 | Nouvelle enveloppe des locations et des lots chiffrés : X25519 par installation (Keystore), clé publique dans la demande d'appareil, ECIES. Lot conservé scellé et déchiffré en mémoire. Nouveaux vecteurs Kotlin, Java et Python, avec un test « le jeton seul n'ouvre rien ». Maître distinct de la clé de signature. Mise à jour de RENTAL-LOTS § 3 et § 11. | A2-1, A2-2, A2-7 |
| J9 | Révocation sur la TV : liste `cbr1` par fichier, Bluetooth ou téléphone ; `SeqState` persisté ; clé de secours hors ligne. Alignement Java/Python (genre inconnu, bornes `usage`). | A1-5, A1-8 |
| J10 | Profil hors ligne : sonde gstatic désactivée, `network_security_config`, aria2 retiré ou DHT/BT coupés (et licence GPL si conservé), `/api/playurl` fermé, HTTP limité au réseau local avec contrôle de `Host`, PIN en en-tête seulement et budget d'échecs global. Campagne de tests sur la TV GaiaOS réelle (liste A7-1). | A3-1 à A3-6, A5-3, A7-1 |

**En parallèle (décisions du propriétaire, sans code)**
1. Comportement à l'échéance d'une production : garder achats et lecture ? (A9-1)
2. Texte définitif de l'avis d'usage. (A9-2)
3. CGV et CGU de location. (A9-3)
4. Licence de Langues : réservée ou CC BY-SA réellement libre ? (A8-1)
5. Avertissements droit et santé. (A8-3)
6. Consentement parental à la télémétrie et formalités de la loi 2024/017. (A8-6)

**Petits correctifs de documentation et d'hygiène (S)**
- Écrire `docs/RELEASES.md`.
- En-tête et section 0 du HANDOFF.
- `.gitignore` des secrets.
- Supprimer le stash.
- Archiver `wip/external-ai-changes`.
- Placeholder dans `.env.example`.
- `requirements.txt` du vérificateur Python.
