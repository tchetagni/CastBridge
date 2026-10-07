# Stockage sur clé USB (branche `feat/tv-usb-storage`)

Objectif : utiliser une clé USB branchée sur la TV comme **espace supplémentaire** pour les vidéos reçues du téléphone
(envoi reprenable, lecture pendant l'envoi, lecture depuis la clé), **sans aucune permission de stockage large** :
uniquement ce qu'Android accorde à une app (scoped storage). Rien ici ne contourne Android.

> **Avertissement.** La TV d'Esaie tourne sous « GaiaOS » dont le comportement (réglages, sélecteur de fichiers, façon d'exposer une
> clé USB à `getExternalFilesDirs`, diffusion des événements de montage) est **inconnu**. Tout ce qui suit est validé par des tests JVM
> sur des dossiers temporaires et par la compilation, **pas** sur la vraie TV avec la vraie clé. Voir « Ce qui est validé / non validé ».

## 1. Stratégie : trois niveaux, avec repli automatique

| # | Niveau | Mécanisme Android | Permission / confirmation | Lecture pendant l'envoi |
|---|---|---|---|---|
| 1 | **Dossier de l'app sur la clé** | `Context.getExternalFilesDirs(null)` : `<clé>/Android/data/castbridge.receiver/files/videos` | aucune, aucune confirmation | **oui** (fichier ordinaire : `java.io.File`, `GrowingStream`, libVLC par chemin) |
| 2 | **Dossier choisi (SAF)** | `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`, écriture `ContentResolver`/`DocumentsContract`, lecture libVLC par descripteur (`Media(libVlc, FileDescriptor)`) | une fois, par l'utilisateur, sur la TV (si elle a un sélecteur) | **non** : envoi complet avant lecture (voir §3) |
| 3 | **Mémoire interne** | `getExternalFilesDir("videos")` (comportement d'avant) | aucune | oui |

Ordre en cible `auto` : volumes amovibles sûrs (le plus d'espace libre d'abord) -> dossier SAF s'il a été choisi -> mémoire interne -> en dernier
recours les volumes « risqués » (clé lente, ou système de fichiers inconnu pour un fichier de plus de 4 Go). Un volume qui ne peut pas prendre le fichier est
écarté avec sa raison, et le suivant est essayé (y compris après éviction, voir §5).

### 1. Volumes secondaires (`AndroidVolumeProvider`)

- On garde les entrées de `getExternalFilesDirs()` d'indice >= 1 qui sont **amovibles** (`Environment.isExternalStorageRemovable(file)`) **et montées**
  (`Environment.getExternalStorageState(file) == MEDIA_MOUNTED`).
- **Clé « adoptée »** : Android la présente comme stockage interne (non amovible) : elle est ignorée ici, il n'y a rien de spécial à faire (elle fait partie de la mémoire interne).
- **Test d'écriture réel** (`WriteProbe`) : création, écriture, `fsync`, relecture de la taille, suppression d'un petit fichier. « Monté » ne prouve pas « inscriptible »
  (clé protégée, système de fichiers en lecture seule après un retrait brutal, NTFS monté en lecture seule...). Une clé non inscriptible est **listée mais ignorée**, avec
  un `formatAdvice` qui explique quoi faire.
- **Système de fichiers** lu dans `/proc/mounts` (`FsInfo`) : `vfat` -> FAT32 ; `exfat` ; `ntfs`/`ntfs3`/`tntfs`/`ufsd` ; `ext4` ; `f2fs`. Le point de montage visible (`/storage/XXXX-XXXX`) est
  presque toujours un wrapper FUSE/sdcardfs : le vrai type est cherché sur `/mnt/media_rw/XXXX-XXXX`. `fuseblk`, `sdfat` ou un `/proc/mounts` illisible -> **inconnu**
  (les noms de fichiers sont alors restreints par précaution, et un fichier > 4 Go est déclassé, voir matrice).
- **Débit d'écriture** : au montage, hors du fil principal (une clé lente figerait l'interface), écriture séquentielle de 4 Mo + `fsync`, chronométrée (une seule fois par montage,
  relançable : MENU > « re-détecter la clé » ou `POST /api/storage/rescan?measure=1`). C'est une **estimation d'écriture** : elle ne dit rien de la lecture, ni du débit soutenu
  (cache de la clé, échauffement).
- Le dossier de l'app sur une clé est **effacé par Android à la désinstallation de l'app** ; l'app ne supprime jamais rien d'autre que ses fichiers (§7).

## 2. Matrice de cas (cible `auto` sauf mention)

| Cas | Comportement | Test |
|---|---|---|
| Clé absente | mémoire interne, sans message | `StoragePolicyTest.driveAbsent` |
| Clé présente, exFAT, assez de place | la clé (`part` + `meta` dans son dossier) | `autoTargetsTheDriveAndTheListingAggregates`, `partAndMetaLive...` |
| Clé en lecture seule / non inscriptible | ignorée, envoi vers l'interne ; `/api/storage` explique (`formatAdvice`) | `readOnlyDriveIsIgnoredAndExplained...` |
| Clé pleine | ignorée (ou éviction des fichiers déjà lus **de cette clé**), sinon interne | `fullDriveIsSkipped`, `evictsOnlyOnTheTargetVolume...` |
| Clé FAT32, fichier < 4 Go | la clé ; avertissement « FAT32 : 4 Go ou plus impossible » | |
| Clé FAT32, fichier >= 4 Go (4 Go - 1 octet passe) | clé écartée ; **interne si le fichier y tient**, sinon refus **413** avant l'envoi avec message précis (« reformatez en exFAT ») | `fat32RefusesAFileOverFourGiB...`, `fat32WithABigFile...` |
| Clé exFAT ou NTFS, gros fichier | la clé (pas de limite de 4 Go) | `exfatHasNoFourGigLimit` |
| Clé lente (< 1,5 Mo/s mesurés) | déclassée après l'interne, utilisée si l'interne ne peut pas ; avertissement de lecture pendant l'envoi | `slowDriveOrUnknownFs...` |
| Système de fichiers inconnu, fichier > 4 Go | déclassée, avertissement | idem |
| Cible `internal` | jamais la clé | `explicitTargets` |
| Cible = une clé précise, absente | 503 `volume unavailable` (pas de repli silencieux) | `explicitDriveTargetWithoutDriveAnswers503` |
| Cible = clé FAT32 et fichier > 4 Go | 413, pas de repli silencieux | `explicitTargets` |
| Dossier SAF choisi | entre la clé et l'interne ; **envoi complet avant lecture** | `safIsAnAutoFallback...` |
| Clé retirée pendant un envoi | 503 `{"error":"volume removed"}`, le téléphone attend et **reprend** au retour de la clé | `HotRemovalTest` |
| Clé retirée pendant une lecture | la lecture s'arrête, message sur la TV | `removalWhilePlayingStopsPlayback...` |
| Deux volumes avec le même nom de fichier | les deux sont listés avec `"duplicate":true` ; un nouvel envoi du même nom remplace l'ancien où qu'il soit | `duplicatesAreReported...` |

## 3. Le repli SAF : ce qui est raisonnable et ce qui ne l'est pas

Livré : choix du dossier (MENU sur la TV, ou `POST /api/storage/saf/pick` qui l'ouvre **sur l'écran de la TV** : quelqu'un doit valider avec la télécommande), permission
persistée, tous les fichiers sont rangés dans un sous-dossier `CastBridge/` créé dans le dossier choisi (rien d'autre de l'arborescence n'est lu ni modifié), écriture en ajout
(`openOutputStream(uri, "wa")`) dans `<nom>.part` renommé à la fin, reprise à la taille du `.part`, lecture par `ParcelFileDescriptor` donné à libVLC (fermé à l'arrêt), message
clair si `ACTION_OPEN_DOCUMENT_TREE` n'a aucune activité sur cette TV. Déplacements de/vers ce dossier via `/api/storage/move?to=saf`.

**Lecture pendant l'envoi : non, volontairement.** Le `.part` est un document SAF : pas de fichier `java.io.File` que `GrowingStream` puisse relire par offset pendant
qu'il grandit, pas de `.meta` fiable, et libVLC ne sait pas suivre un descripteur de fichier qui grossit (il verrait une fin de fichier). Le contourner demanderait un serveur de
flux par `ContentResolver` par-dessus des fournisseurs de documents dont la latence et le comportement (troncature sur `"w"`, renommage) varient. Le mode SAF est donc limité au
**préchargement complet** : le téléphone reçoit `409 {"error":"preload only"}` à `/api/play` tant que l'envoi n'est pas fini (il réessaie), et l'avertissement est affiché.
Non validé : que le fournisseur de la TV honore le mode `"wa"` (sinon, un envoi repris ne se complète pas : la taille finale ne correspond jamais, sans corruption d'autres fichiers).

## 4. Politique « cible de stockage »

`GET /api/storage` -> `target`. `POST /api/storage/target?value=auto|internal|<idDeVolume>` (valeur validée contre la liste des volumes connus ; **jamais un chemin**),
persistée dans les préférences de la TV (`storage_target`) et rejouée au démarrage.

- **Contraintes FAT32** : refus **avant** d'envoyer (`GET /api/storage/check?name=&size=&dur=`, appelé automatiquement par `ResumableUpload` avant le premier octet ; un `PUT` direct est aussi refusé
  avant écriture, 413). Le message dit : fichier trop gros pour ce volume, 4 Go - 1 octet maximum, reformatez la clé en exFAT ou envoyez vers la mémoire interne.
  En `auto`, retomber sur l'interne si le fichier y tient. exFAT et NTFS : pas de limite de 4 Go.
- **Noms de fichiers** (`NameRules`) : `safeName` reste la barrière côté client (pas de `/`, `\`, NUL, pas de nom caché, pas de `.part`/`.meta`, 200 caractères). Sur FAT32/exFAT/NTFS (et
  volume inconnu) les caractères `\ / : * ? " < > |` et de contrôle sont remplacés par `_`, les points/espaces finaux retirés ; tout nom modifié reçoit un suffixe stable `~xxxxxx` (hash de l'original)
  avant l'extension, pour que `a:b.mp4` et `a_b.mp4` restent deux fichiers, que le même nom du téléphone donne toujours le même nom stocké, et que le nom stocké s'applique à lui-même (idempotent).
  Le téléphone peut donc redemander le fichier par son nom d'origine **ou** par celui affiché dans la liste. Tous les systèmes de fichiers (ext4 compris) : 255 **octets** UTF-8 au plus, `.part` compris
  (troncature propre sans couper un caractère). **Casse** : FAT/exFAT/NTFS sont insensibles à la casse : `A.mp4` et `a.mp4` sont le même fichier (recherche insensible à la casse sur ces volumes).
- **Débit** : `writeBps` par volume dans `/api/storage`. Avertissement si `débit d'écriture < 1,5 x débit vidéo` (`taille / durée`, `dur` en ms passé à `/check`) ; sans durée, seuil de 2,5 Mo/s. Une clé sous 1,5 Mo/s
  est déclassée en `auto`.

## 5. Plusieurs dossiers dans `ReceiverServer`

- **Listing agrégé** : `/api/info` -> `files[]` avec `volume` (id) et `duplicate` ; `volumes[]` (id, label, kind, fs, removable, writable, free, total, used, quota, writeBps, warnings, formatAdvice) ; les
  champs `free`, `used`, `quota` de niveau supérieur décrivent le volume où irait le **prochain** envoi (compatibilité des anciens clients). Un volume retiré apparaît avec `present:false`.
- **Upload** : la cible est choisie une fois (au premier octet) ; `.part` et `.meta` sont dans **le même dossier** ; toute reprise retourne sur le volume qui contient le `.part`. Si le volume qui le contenait est absent, la réponse est
  503 (jamais « 0 octet » : le téléphone repartirait de zéro ailleurs et laisserait deux copies partielles au retour de la clé). À la fin, les autres copies finies du même nom sont supprimées (remplacement, pas de doublon silencieux).
- `/stream`, `/api/play`, `/api/delete` (option `volume=` pour ne supprimer qu'une copie), `/api/rename`, `/api/part`, `/api/reset` résolvent le fichier sur **tous** les volumes.
- **Quotas et éviction par volume** : le quota explicite (`quotaMb`) s'applique à la mémoire interne (rare et petite) ; une clé a un quota de 95 % de « utilisé + libre », sans plafond. Éviction (si activée) : les plus anciens fichiers **déjà lus** du
  volume visé seulement, jamais le fichier en cours de lecture, jamais un autre volume. L'espace récupérable par éviction est pris en compte avant d'écarter un volume.
- **Déplacement** `POST /api/storage/move?name=&to=<idDeVolume|saf>` : vérifie l'espace, la limite du système de fichiers cible, refuse si le fichier est en lecture (409), s'il existe déjà sur la cible (409), ou si un envoi est en cours.
  Copie par flux de 64 Ko dans `<nom>.part` **sur la cible**, reprend un `.part` de déplacement précédent si sa taille annoncée correspond, ne renomme qu'à la taille exacte, revérifie la taille finale et **ne supprime la source qu'ensuite**.
  Annulable (`POST /api/storage/move/cancel`, le `.part` est gardé pour reprise). État dans `/api/storage` -> `move`. Le marquage « lu » suit le fichier. Un seul déplacement à la fois. Pas de progression détaillée au-delà de `done/total`.
- **`.part` orphelins** nettoyés par volume au démarrage et au retour d'un volume : 24 h pour l'interne, **7 jours** pour une clé (une clé absente quelques jours ne doit pas perdre ses envois reprenables).
- Le Bluetooth et l'import USB (MENU > USB) écrivent toujours dans la mémoire interne (limite documentée, hors périmètre de cette branche).

## 6. Retrait à chaud

Sources de détection : `BroadcastReceiver` enregistré dynamiquement (`ACTION_MEDIA_MOUNTED/UNMOUNTED/EJECT/REMOVED/BAD_REMOVAL`, schéma `file`), `StorageVolumeCallback` (API 30+), et un balayage de sécurité toutes les 15 s
(si le firmware n'envoie aucun des deux). Sur retrait : le volume sort du registre **immédiatement**, les envois en cours reçoivent 503 (`volume removed`) au bloc suivant (chaque bloc de 64 Ko vérifie la présence du volume, et toute
`IOException` d'écriture dont le dossier a disparu est traitée pareil), `GrowingStream` cesse d'attendre (`IOException: volume removed`) au lieu de bloquer 30 s, la lecture d'un fichier de ce volume est arrêtée avec un message sur la TV,
un déplacement échoue en gardant la source intacte. Aucune exception non attrapée : tout passe par `try/catch` et des `use` (pas de descripteur qui fuit). Au retour : le volume est re-détecté, le `.part` retrouvé, le téléphone (qui réessayait) reprend
à la taille du `.part`.

**Clé retirée SANS éjection, clé « en vérification » ou « illisible », « Préparer le retrait de la clé USB » : voir §11** (ce que la TV peut et ne peut pas faire, sans aucun droit système).

## 7. Sécurité

Toutes les routes nouvelles sont derrière le PIN existant (test `everyNewRouteNeedsThePin`), aucun chemin absolu n'est accepté ni divulgué (`value=`/`to=` sont des identifiants de volume, `name` passe par `safeName`),
pas de traversée de répertoire (séparateurs refusés, dossier par volume fixé côté TV), aucun PIN journalisé. L'app ne supprime que : ses propres fichiers (`.part`, `.meta`, `.played`, vidéos qu'elle a reçues, sur demande ou éviction configurée),
et son fichier de test d'écriture `.cb-probe-*`. **Elle ne formate jamais rien** (voir §8).

## 8. Formater la clé

Une app Android normale **ne peut pas formater** un volume : `StorageManager.partition`/`format` sont des API système protégées par `MOUNT_FORMAT_FILESYSTEMS`, réservée aux apps
signées par la plateforme. CastBridge ne prétend pas le faire et ne tente aucun contournement. `/api/storage` explique seulement pourquoi l'écriture échoue et ce qui aiderait (`formatAdvice`), et l'app peut
**ouvrir les réglages de stockage de la TV** (MENU > « Stockage : ouvrir les réglages… » ou `POST /api/storage/open-settings`, derrière le PIN) : elle essaie `Settings.ACTION_INTERNAL_STORAGE_SETTINGS`, puis
`android.settings.MEMORY_CARD_SETTINGS`, puis `Settings.ACTION_SETTINGS`, et dit clairement si aucune ne s'ouvre sur GaiaOS.

Les trois voies réelles, à lancer **par l'utilisateur** (jamais par l'app) :

1. **Depuis un ordinateur** (la plus sûre). Recommandé : **exFAT** (pas de limite de 4 Go, lisible par Windows, macOS et Linux récent). **Pas FAT32** si des fichiers dépassent 4 Go. Attention : NTFS/ext4 sont souvent
   non inscriptibles par une app Android (selon le firmware) ; et exFAT doit être pris en charge par le noyau de la TV (à vérifier : brancher la clé, voir si `/api/storage` la donne `exFAT` et `writable:true`). **Le formatage efface tout**
   (sauvegardez d'abord).
2. **Depuis les réglages de la TV**, si GaiaOS les expose (souvent « Stockage > clé > Formater comme stockage portable »). Inconnu sur cette TV.
3. **Par ADB depuis un ordinateur** (débogage USB/réseau activé sur la TV) :
   ```sh
   adb shell sm list-disks                       # repérer l'id, par exemple disk:8,16
   adb shell sm partition disk:8,16 public       # volume PORTABLE (exFAT/FAT32 selon la taille) : lisible ailleurs, recommandé
   adb shell sm partition disk:8,16 private      # ADOPTÉ comme stockage interne (chiffré)
   ```
   Risques : **efface tout** ; « private » **lie la clé à cette TV** (illisible ailleurs), et les apps déplacées sur la clé **se dégradent ou plantent si elle est retirée**. Avec une clé adoptée, Android la traite comme de la
   mémoire interne : CastBridge n'a alors rien de spécial à faire (elle sort de la liste des volumes amovibles). `sm` peut être absent ou restreint sur GaiaOS. Ces commandes sont des indications : **CastBridge ne les exécute jamais**.

## Ce qui est validé / non validé

**Validé (tests `gradle :core:test`, dossiers temporaires simulant plusieurs volumes)** : détection du système de fichiers sur des `/proc/mounts` types ; mappage de noms FAT/exFAT/NTFS et limite de 255 octets ; sélection de la cible
(clé absente, lecture seule, pleine, FAT32 + gros fichier, exFAT, lente, inconnue, SAF, cibles explicites) ; résolution sur plusieurs volumes (part, stream+Range, play, rename, delete, reset) ; listing agrégé et doublons ; upload reprenable avec retrait du volume
au milieu puis retour ; 503 + `{"error":"volume removed"}` ; arrêt de lecture au retrait ; déplacement avec vérification, annulation/reprise, échec de vérification (source conservée), retrait pendant la copie ; éviction par volume ;
nettoyage des orphelins par volume ; PIN sur toutes les routes nouvelles ; aucun chemin absolu ; pré-vérification avant envoi ; page web. `gradle :receiver:assembleDebug :sender:assembleDebug` compile.

**Non validable sans la vraie TV et la vraie clé (rien n'a tourné sur l'appareil)** :
- comment GaiaOS expose la clé à `getExternalFilesDirs` (dossier présent ? `isExternalStorageRemovable` vrai ? clé « adoptée » d'office ?) ;
- la lecture de `/proc/mounts` par l'app sur cette version d'Android (sinon : système de fichiers « inconnu », noms restreints par précaution) ;
- la présence d'un sélecteur `ACTION_OPEN_DOCUMENT_TREE`, le mode `"wa"` du fournisseur SAF, `Media(libVlc, FileDescriptor)` sur libVLC 3.6.5 armeabi-v7a ;
- la diffusion des événements de montage (le balayage de 15 s couvre son absence) ; le comportement réel d'un retrait brutal (erreurs d'E/S, `remount-ro`) ;
- le débit réel (le test de 4 Mo mesure l'écriture seulement, et la lecture pendant l'envoi dépend aussi du décodage sur une TV 32 bits) ;
- les intents de réglages de stockage.

**Compromis retenus** : le SAF sans lecture pendant l'envoi (§3) ; le quota explicite réservé à l'interne ; la cible explicite ne se replie **pas** silencieusement (l'utilisateur a choisi) ; une clé au système de fichiers inconnu n'est pas
écartée mais déclassée pour les gros fichiers ; les `.part` d'une clé absente survivent 7 jours en mémoire de l'app seulement (la liste des `.part` d'un volume absent est en RAM : si l'app redémarre pendant que la clé est absente, un
nouvel envoi du même nom démarre ailleurs et le `.part` de la clé sera listé comme doublon au retour, `duplicate:true`, supprimable avec `/api/delete?volume=`).

## 9. Données lourdes sur la clé : `Download/CastBridge/` (branche `claude/usb-data`)

**Pourquoi.** Le dossier `Android/data/castbridge.receiver/` d'une clé est effacé par Android à la désinstallation. `Download/` est un dossier public : les
contenus lourds y survivent. Mesuré sur la TV de référence (Android 14) : l'app peut créer et écrire dans `/storage/<id>/Download/` sans permission. Autres Android (10, 11, 13) : non
vérifié ; **repli automatique** sur le dossier de l'app sur la clé si l'écriture est refusée (`AndroidVolumeProvider`, test d'écriture réel).

**Disposition** (`UsbLayout`) : `<clé>/Download/CastBridge/` avec `Bibliotheque/` (vidéos et médias reçus : c'est le dossier de la bibliothèque), `Telechargements/` (réservé aria2),
`Medias/` (futurs lots multimédias lourds, hors budget 10 Mo des lots de données), `index/` (`castbridge-store.json`, `settings.json`).
La livraison différée de lots (≤ 10 Mo) reste en mémoire interne (choix volontaire : petit, secret éventuel, et la clé peut être absente).

**Politique** (`HeavyStorage.decide`, réglage « Contenus lourds sur la clé USB », oui par défaut) : clé inscriptible présente → la clé (celle choisie, sinon la plus vide) ; sinon mémoire interne
avec l'avertissement « Aucune clé USB… mémoire interne, qui est petite » ; clé lecture seule ou pleine jamais choisie ; clé lente ou FAT32 : utilisée avec avertissement (FAT32 : 4 Go max, comme §2).
`POST /api/storage?heavyOnUsb=true|false&heavyDrive=<idDeClé|vide>` ; `GET /api/storage` → bloc additif `heavy` {`enabled`, `where` (`usb`/`internal`), `drive`, `label`, `root` (`Download/CastBridge`, jamais un chemin absolu),
`free`, `writeBps`, `warnings`, `readopt`}. L'écran « Données » du téléphone (agent `lots-framework`) peut afficher « Contenus lourds : clé USB … » à partir de ce bloc.

**Réadoption** (`UsbReadopt`, `TvService.readopt()`) : au démarrage et à chaque insertion, analyse de `Download/CastBridge/` **uniquement** (profondeur 6, 20 000 entrées, 5 s au plus), jamais le reste de la clé.
Résultat : fichiers, `.part` reprenables (retrouvés par le registre, repris par le téléphone), doublons (signalés, jamais supprimés), entrées ignorées (liens symboliques, noms invalides, trop profond, exécutables/APK : jamais ouverts ni installés).
L'index `index/castbridge-store.json` (version de format, id de la clé, date, nombre de fichiers) est écrit de façon atomique (fichier temporaire + `fsync` + renommage) ; absent, trop gros (> 256 Ko), illisible ou d'une version inconnue → reconstruit depuis les fichiers.
Une date d'avant 2020 (TV sans horloge) n'est pas retenue. La bibliothèque et les dossiers virtuels se relisent depuis les fichiers ; les vignettes sont un cache régénérable.

**Sauvegarde des réglages** (`SafeSettings`, optionnelle, `index/settings.json`) : liste blanche `language, storageProfile, quotaMb, tiles, heavyOnUsb`. Tout autre champ est refusé à l'écriture, et les mots PIN, jeton, SSH, parental, téléphones de confiance, rapports,
appairage, clé, code, mot de passe sont refusés même s'ils entraient dans la liste (test `noSecretCanBeWrittenToTheDrive`). À la lecture le fichier est filtré de la même façon (taille limitée à 16 Ko) ; il n'est pas appliqué automatiquement.

**Migration** (`UsbMigration`) : « Déplacer les contenus vers la clé » et « Rapatrier » = même moteur dans les deux sens. Copie vers `<nom>.part` (reprise à la taille du `.part`, `fsync`), vérification complète (taille **et** SHA-256) avant le renommage final,
**jamais** de suppression de la source sans `deleteSources = true` (la confirmation de l'utilisateur), jamais d'écrasement d'un fichier différent (conflit signalé), noms adaptés exFAT/FAT, fichier trop gros pour FAT32 refusé avant la copie, clé retirée = arrêt sans perte.
Une coupure à n'importe quelle étape (copie, vérification, renommage, suppression) laisse la source intacte et la relance termine (tests `interruptionAtEveryStepLosesNothingAndResumes`).
**Reste à brancher** : l'écran/la route qui l'appelle avec la confirmation (le moteur et ses tests sont prêts). Les fichiers déjà dans `Android/data/…/videos` de la clé ne sont plus listés quand « clé USB » est activé : ils sont à migrer (mêmes dossiers source/destination) ou à copier à la main.

**Sécurité.** La clé est un support non fiable : rien n'est exécuté ; chaque nom et chemin lu est validé (`UsbPaths` : pas de `..`, ni chemin absolu, ni `\`, caractères interdits, 255 octets, profondeur 8, sortie de la racine par lien symbolique refusée) ; index et réglages lus ont une taille maximale ; rien n'est lu hors de `Download/CastBridge/`.

**Limites connues.** Clé « sale » (exFAT non démonté proprement) : le test d'écriture la classe en lecture seule ou en erreur, l'utilisateur doit la réparer sur un ordinateur. L'horodatage de la TV peut être faux : on ne s'en sert pas pour décider. Clé lente (~1,4 Mo/s mesuré) : écriture en tâche de fond, avertissement. aria2 écrit toujours dans `.cb-downloads` sous `Bibliotheque/` (non déplacé vers `Telechargements/`).

### Protocole de test manuel (TV + clé)
1. Brancher la clé, ouvrir CastBridge-TV ; `GET /api/storage` → `heavy.where = "usb"`, `heavy.warnings` cohérents (clé lente : avertissement attendu).
2. Depuis le téléphone, envoyer une vidéo. Sur un ordinateur (ou `adb shell ls /storage/<id>/Download/CastBridge/Bibliotheque/`) : le fichier est là, `index/castbridge-store.json` existe.
3. Couper l'envoi (retirer la clé en cours d'écriture) : un `.part` reste ; remettre la clé : l'envoi reprend, pas de doublon.
4. **Désinstaller CastBridge-TV**. Vérifier que `Download/CastBridge/` est toujours sur la clé.
5. Réinstaller, relancer : `heavy.readopt` indique les fichiers retrouvés ; la vidéo réapparaît dans la bibliothèque (vignette régénérée).
6. Retirer puis réinsérer la clé : la TV annonce le retrait, la lecture s'arrête ; au retour, nouvelle réadoption.
7. Régler « Contenus lourds sur la clé USB » sur non : l'envoi suivant va en mémoire interne ; vérifier le repli sans clé (message « Aucune clé USB »).
8. Si l'écriture dans `Download/` est refusée (autre Android) : la clé reste utilisée dans `Android/data/…` (`heavy.root = null`) : le noter.

## 10. Rangement à la réception : de vrais dossiers par catégorie, un nom propre (non validé sur TV)

**Avant** : tout ce que la TV recevait était posé À PLAT dans le dossier du volume ; les dossiers (Films, Séries…) n'étaient que des étiquettes virtuelles (`FolderIndex`). **Maintenant** : à la fin de l'envoi, le fichier est rangé tout seul dans un vrai dossier, sous un nom propre, sur le même volume (mémoire interne : `<dossier du volume>/Films/…` ; clé : `Download/CastBridge/Bibliotheque/Films/…`). Réglage `file_on_receive` (oui par défaut ; non = ancien comportement à plat). Code : `core/tv/Filing.kt` (règles pures, JVM) + `FiledIndex` + `FileStore.fileInto`.

| Ce que le nom dit (`NameParser`/`Namer`, les mêmes que l'assistant du téléphone) | Dossier | Nom final |
|---|---|---|
| Épisode sûr (confiance ≥ 0,7, titre, S/E ou date) | `Séries/<Titre>/Saison NN` | `Titre – S01E04.ext` |
| Film avec année (confiance ≥ 0,8) | `Films` | `Titre (2010).ext` |
| Audio ; clip | `Musique` ; `Musique/Clips` | `Artiste – Titre.ext` si sûr, sinon nom conservé |
| Photo ; capture/enregistrement d'écran ; vidéo perso (caméra, WhatsApp) | `Photos` ; `Captures` ; `Famille` | `Photo – 2024-03-15 14h22.ext`… |
| Cours (matière reconnue) | `Cours/<Matière>` | nom conservé |
| Document ; archive | `Documents` ; `Archives` | nom conservé (nettoyé) |
| Installateur `.apk/.xapk/.apks/.apkm`, paquets `.learn.zip/.lot.zip/.quiz.zip`, lots | RESTENT À PLAT à la réception | nom d'origine |
| Tout le reste, ou nom pas assez sûr (`One.Piece.1045.VOSTFR.mkv`, `video.mp4`, `.exe`) | `À trier` | nom conservé (nettoyé) |

**Garanties.** Jamais d'écrasement : le nom est unique sur TOUTE la TV (un seul espace de noms, toutes clés confondues), une collision ajoute « (2) », « (3) »… ; chemin et noms passent par `UsbPaths`/`SafeName`/`NameRules` (pas de `..`, pas de séparateur, profondeur ≤ 8, 255 octets par segment) ; un fichier trop gros pour le système de fichiers (FAT32 : 4 Go − 1) n'est jamais placé (le refus d'espace existait déjà avant l'envoi) ; un fichier en cours de lecture ou de diffusion reste à plat (rangé plus tard à la demande) ; l'essai refuse toujours envois et rangement (liste blanche `TrialPolicy`, routes par défaut interdites) ; tout échec laisse le fichier à plat, ce qui est toujours valide.

**Le nom d'un fichier reste son identité** (une seule clé plate : `/stream/<nom>`, `/api/play`, `/api/rename`, `/api/delete`, corbeille, marques « lu », vignettes, dossiers virtuels). L'emplacement réel est dans `<dossier du volume>/.filing` (`clé -> chemin`, `nom d'origine -> clé`), écrit atomiquement (fichier temporaire, sync, renommage) AVANT le renommage du fichier : une coupure de courant laisse soit le fichier à plat (valide), soit le fichier rangé avec son entrée. Les fichiers font foi : une entrée dont le fichier a disparu est oubliée (sauf si le volume est absent), et un index perdu est reconstruit en parcourant les dossiers de catégorie (borné) au démarrage et au branchement (`resync`). Le dossier virtuel du téléphone est aligné sur le vrai dossier (`FolderIndex.set`).

**Reprise, doublons.** L'envoi garde son fichier partiel À PLAT sous son vrai nom (`<nom>.part`, bitmap des blocs, `.cbx`) : la reprise est inchangée. À la fin : validation, `commit`, puis rangement (renommage atomique du même volume). « Même nom, même taille, déjà là » : `/api/part`, `PUT /upload` et `/api/transfer/begin` retrouvent le fichier rangé par le NOM D'ORIGINE (index), et, si l'index est perdu, par le nom sous lequel il serait rangé aujourd'hui (même taille exigée) ; un fichier de même nom mais d'une autre taille est un AUTRE fichier (rangé, numéroté). API additive : `/api/part` et la réponse de `/api/transfer/finish` ajoutent `folder` et `finalName` ; `/api/info` et `/api/library` ajoutent `folder` (réel) et `origin` ; un ancien téléphone ignore ces champs et fonctionne (il envoie, les fichiers sont rangés).

**« Ranger ma bibliothèque » (une fois, à la demande).** `GET /api/library/organize` = plan (essai à blanc, rien ne bouge) ; `POST /api/library/organize/apply[?max=500]` = applique le plan RECALCULÉ sur la TV (rien de ce que le téléphone envoie ne décide). Ne considère que les fichiers terminés encore à plat (idempotent : un second passage ne change rien). Refusé (403) pendant qu'un profil enfant est actif ; les fichiers protégés par le contrôle parental ne sont ni planifiés, ni nommés (seul leur nombre est donné) ; occupés (lecture, diffusion, envoi) laissés en place avec la raison ; volumes SAF comptés à part. Ce sont des renommages sur le même volume : rien n'est supprimé, donc la corbeille 30 jours n'est pas sollicitée (aucune perte possible). Les installateurs sont rangés dans `Applications` à cette occasion seulement. Le téléphone (Bibliothèque de la TV > icône dossier) montre le plan et ne range qu'après « Ranger ».

**Ce qui n'est pas fait** : déplacer un fichier d'un volume à l'autre (`Mover`) le pose à plat sur la destination ; le rangement de l'assistant du téléphone (dossiers virtuels) ne déplace toujours aucun octet ; pas de bouton sur l'écran de la TV (retour D-pad : une notification « Bibliothèque rangée : N fichiers » et « Vidéo reçue ✓ Titre → Dossier ») ; pas de réglage visible pour `file_on_receive` ; langue des dossiers fixée à « fr » ; pas d'annulation du rangement (le plan et la réponse listent les déplacements). Tests : `FilingTest` (règles, ≥ 40 noms), `FilingServerTest` (serveur réel : réception, reprise, doublons, essai, parental, enfant, clé USB, transfert multi-connexion).


## 11. Clé USB mal éjectée : ce que la TV peut et ne peut pas faire (2026-10-07, NON VÉRIFIÉ SUR TV)

Demande du propriétaire : « pouvoir lire la clé USB même si elle a été mal éjectée ». Terrain : les clés (Lexar exFAT 128 Go, une autre en FAT32) sont retirées sans éjection ; une autre TV affiche « mal débranchée » ; parfois Android marque la clé « en vérification » ou « illisible / endommagée ».

### Les faits d'Android (sans aucun droit système)

- Au branchement, `vold` lance le contrôle du système de fichiers (`fsck`) : le volume passe à l'état `checking` (diffusion `ACTION_MEDIA_CHECKING`), puis à `mounted` (`ACTION_MEDIA_MOUNTED`, `mounted_ro` si le montage est en lecture seule) ou à `unmountable` (`ACTION_MEDIA_UNMOUNTABLE`), `nofs` (aucun système de fichiers connu). Le contrôle dure quelques secondes sur une clé propre, **plusieurs minutes** sur une grosse clé retirée sans éjection.
- Une clé retirée sans éjection donne `bad_removal` (`ACTION_MEDIA_BAD_REMOVAL`), puis `unmounted` et `removed` ; une éjection demandée donne `ejecting` (`ACTION_MEDIA_EJECT`) puis `unmounted`.
- **Une application ne peut ni réparer, ni formater, ni démonter, ni forcer le montage d'un volume** : `MOUNT_UNMOUNT_FILESYSTEMS` et `MOUNT_FORMAT_FILESYSTEMS` sont des permissions de la plateforme. CastBridge-TV ne les demande pas, n'utilise aucun `su`, et ne prétend rien faire de tout cela.

### Ce que la TV fait

1. **Lire l'état de chaque clé** : `receiver/UsbVolumeWatch.kt` écoute les diffusions média, relit `StorageManager.storageVolumes` au démarrage, à chaque question d'un écran, toutes les 5 s tant qu'une clé est là (toutes les 15 s sinon : une diffusion perdue par un boîtier ne laisse pas la TV aveugle), et le rappel de volume d'Android 11 et plus. Toutes les décisions sont dans le cœur pur : `core/tv/UsbVolumeTracker.kt` (l'historique : depuis quand dure la vérification, retrait sans éjection retenu d'un démarrage à l'autre, fichier coupé), `core/tv/UsbVolumeState.kt` (la règle : l'état et l'historique donnent la ligne française et l'action) et `core/tv/UsbMountRetry.kt` (le réessai). Rien dans les journaux de ce qui identifie un fichier.
2. **Attendre la fin de la vérification, et le dire** (la ligne dit aussi pourquoi, quand elle le sait) :

   | État d'Android | Ligne (extrait) | Action |
   |---|---|---|
   | `checking` | « Clé « Lexar » : vérification par Android (elle a été retirée sans éjection)… patientez » ; après 20 s « … en cours depuis 35 s… ne la retirez pas » ; après 2 min « c'est long : laissez-la faire encore quelques minutes ; sinon retirez-la et vérifiez-la sur un ordinateur » | attendre |
   | `mounted` après `checking` | « Clé « Lexar » prête » (10 s, puis plus rien : une clé prête ne harcèle pas l'accueil) | aucune |
   | `mounted_ro` | « … lecture seule. Les vidéos se lisent, mais rien ne peut y être copié. Retirez le verrou de la clé, ou réparez-la sur un ordinateur » | aucune |
   | `unmountable` | « Clé illisible : Android n'a pas pu la réparer. Sur un ordinateur : Mac › Utilitaire de disque › S.O.S ; Windows › clic droit › Propriétés › Outils › Vérifier ; ou Réglages de la TV › Stockage › Réparer/Formater (le formatage efface tout) » | bouton « Ouvrir les réglages de stockage » |
   | `nofs` | « … format non reconnu par la TV (clé vierge, ou format qu'Android ne lit pas). Sur un ordinateur, formatez-la en exFAT (le formatage efface tout)… » | le même bouton |
   | `bad_removal` pendant une copie | « Clé retirée pendant une copie : le fichier « … » est incomplet, il sera repris » (10 min) | aucune |
   | `bad_removal` sans copie | « Clé « … » retirée sans éjection. La prochaine fois : MENU › Préparer le retrait de la clé USB » | aucune |
   | `bad_removal` APRÈS « Préparer le retrait » | « Clé « Lexar » retirée : elle était préparée, rien n'est perdu. Au prochain branchement Android la vérifiera : c'est normal sans éjection » (en vert, une minute, pas de pastille orange) | aucune |
   | `ejecting`, `unmounted` | « éjection en cours, ne la retirez pas encore » ; « éjectée : vous pouvez la retirer » | aucune |
   | copie vers la clé | « Ne retirez pas la clé : copie en cours » (devant la ligne de réception de l'accueil) | aucune |

   Android appelle « retrait brutal » toute clé retirée sans éjection, même si « Préparer le retrait » avait tout vidé : la TV, elle, sait que tout était écrit (`UsbVolumeTracker` retient « préparée » au moment du `bad_removal`), le dit en vert et annonce que la vérification du prochain branchement est normale. La puce de l'accueil (deux lignes au plus) garde une **version courte** des longs guides (« … illisible : Android n'a pas pu la réparer (le guide : MENU > Clé USB) ») ; le guide entier est dans la tuile « Clé USB » et dans MENU (« Clé USB : que faire de la clé ? (le guide) », avec le bouton des réglages de stockage), dans la bibliothèque et dans l'explorateur.

   La cause « retirée sans éjection » n'est affirmée que si la TV l'a **vue** (diffusion `bad_removal`, retenue dans les préférences d'un démarrage à l'autre, huit clés au plus) ; sinon, une vérification longue dit « sans doute ». Une TV éteinte ou une application tuée ne voit rien : elle ne dit alors pas ce qu'elle ne sait pas.
3. **Montrer l'état là où la personne regarde** : la ligne d'état et la pastille de l'accueil (orange : vérification, clé illisible, retrait sans éjection ; verte : « prête à être retirée »), la tuile « Clé USB » (« Vérification par Android… » au lieu d'un faux « Aucune clé »), « Connexion & réglages » (« État de la clé USB »), la bibliothèque (le mot « patientez » ou le guide en en-tête et dans la bibliothèque vide, avec le bouton), l'explorateur de fichiers (la même ligne et le même bouton), la bannière de l'écran d'activation.
4. **Tolérance de lecture : un seul réessai par montage.** Quand la recherche du fichier d'activation, l'explorateur ou la bibliothèque échouent pendant que la clé est en `checking`, ils sont relancés UNE fois quand elle passe à `mounted` (`UsbMountRetry` : un lecteur qui échoue encore sur la clé montée n'est pas relancé, pas de boucle ; un nouveau montage renouvelle le crédit). L'écran d'activation, lui, ne cherche pas pendant la vérification : il dit « vérification par Android… la recherche de l'activation reprend dès qu'elle est prête », puis cherche au montage.
5. **Lire une clé montée en lecture seule** : `AndroidVolumeProvider` accepte `mounted_ro` en plus de `mounted` (la clé est lue et listée, jamais écrite : `writable = false`, « lecture seule, ignorée » pour les envois).

### Retrait sûr : MENU › « Préparer le retrait de la clé USB »

Aussi dans la tuile « Clé USB » de l'accueil. Écran `UsbRemovalActivity` ; les étapes et les mots sont dans le cœur pur `core/tv/UsbSafeRemoval.kt` (22 tests, mutations tuées) ; le déroulement vit dans `receiver/UsbRemoval.kt` (il continue si l'écran est fermé).

1. **Des copies écrivent sur la clé** (réceptions du téléphone en un flux ou en multivoie, déplacement vers la clé, téléchargement) : l'écran les nomme avec leur avancement et propose **« Attendre la fin de la copie »** (plus aucune nouvelle copie n'est acceptée sur la clé, celles en cours finissent) ou **« Mettre en pause et préparer le retrait »** (elles s'arrêtent au bloc suivant).
2. **Ce que « barrer la clé » veut dire côté serveur** (`ReceiverServer.fenceVolume`) : toute nouvelle copie vers la clé est refusée par `503 {"error":"volume removed","cause":"held","retry":true}` : le téléphone attend comme pour une clé retirée (« Clé USB retirée ou indisponible : remettez-la, l'envoi reprendra »), **rien n'est redirigé sur la mémoire interne et rien n'est perdu** ; une copie multivoie dont l'état est sur la clé n'est jamais recommencée ailleurs. En arrêt net (STOP) : l'envoi en un flux est coupé au tampon suivant (ce qui est arrivé est écrit), les sessions multivoies finissent leur bloc, sont forcées sur le support (`force`), fermées (carte des blocs sauvée) et oubliées de la TV, un déplacement est annulé (le `.part` est gardé), les téléchargements sont mis en pause et repartent tout seuls à la levée. Un « attendre » tardif n'affaiblit jamais un « pause ».
3. **Quand plus rien n'écrit** : arrêt net, puis vidage : `fsync` de chaque `.part` et de chaque fichier d'un transfert multivoie inachevé, puis `sync` du système (attendu, 15 s au plus, au mieux : la commande `sync` d'Android, sans permission). Une copie arrivée à 100 % dont la TV relit tout le fichier pour le contrôle final (des minutes sur une clé) **n'est jamais coupée** : elle finit, prend son nom, puis lâche la clé ; si elle y est encore 20 s après l'ordre d'arrêt, l'écran dit « La copie « … » finit sa vérification : attendez-la, puis réessayez » (et non « ne s'arrête pas »). Si une copie ne lâche pas la clé 20 s après l'ordre d'arrêt, ou si un fichier n'est pas confirmé : l'écran dit « La TV n'a pas pu confirmer que tout est écrit sur la clé. Ne la retirez pas tout de suite » (boutons : réessayer, réglages de stockage, reprendre l'utilisation) ; **jamais un faux « prête »**.
4. **« Vous pouvez retirer la clé « Lexar ». »** (et « Les copies en pause reprendront quand vous remettrez la clé » si des copies ont été mises en pause) + « Pour une éjection complète : Réglages › Stockage › Éjecter », avec le bouton « Ouvrir les réglages de stockage » : Android seul peut démonter le volume. L'accueil dit « Vous pouvez retirer la clé… (les copies sont en pause) » tant que la clé est préparée.
5. La clé retirée : tout est rendu (le prochain branchement reprend les copies). Dix minutes sans retrait : les copies reprennent toutes seules (« La clé n'a pas été retirée : les copies reprennent »). Le bouton « Reprendre l'utilisation de la clé » rend la clé à tout moment.

Limites dites : lire une vidéo de la clé peut encore écrire une petite marque « vu » au bout du fichier (écriture atomique avec `fsync`) ; un téléchargement mis en pause par la préparation repart seul, celui que le propriétaire avait mis en pause reste en pause ; le `sync` d'Android ne vide pas le cache de la clé elle-même (aucune API ne le permet : certaines clés bon marché mentent) : l'éjection d'Android reste la voie complète.

### Écritures de CastBridge-TV vers un volume amovible (audit du 2026-10-07)

La règle : un fichier écrit sur un volume amovible est fermé avec `fsync` **à la fin du fichier**, jamais un `fsync` par bloc (R-20 l'a retiré : la clé et le lecteur partagent le bus) ; le commit final demande un `sync` du système, au mieux, **une fois, groupé** (`ShellSync` : un seul fil, au plus un vidage toutes les 3 s, une demande faite pendant un vidage en obtient un autre). Test `DiskFlushTest` : 500 blocs de 64 Kio = aucun vidage, un seul à la fin du fichier, après le renommage, jamais sur la mémoire interne.

| Écriture | `fsync` | `sync` du système |
|---|---|---|
| Réception en un flux (`PUT /upload`) | tous les 64 Mo (R-20) et à la fin (`FileStore.commit`) avant le renommage | demandé après le renommage |
| Réception multivoie (`PartAssembler`) | `force(false)` tous les 64 Mo (hors fil de requête), `force(true)` avant le contrôle final, puis `commit` | demandé après le renommage |
| Déplacement (`Mover`) | tous les 64 Mo, `syncPart`, puis `commit` | demandé après le renommage |
| Rangement / renommage d'un fichier d'une clé | `fsync` de la cible renommée | — |
| Migration vers la clé (`UsbMigration`) | `fsync` du `.part`, puis de la cible renommée | — |
| Corbeille (`TvTrash`) | `fsync` du fichier déplacé | — |
| Archive des contenus libres (`FreeExportFiles`) | `fsync` avant le renommage | demandé à la fin |
| **Demande d'appareil `device-request.txt`** (`ActivationCenter.exportRequest`) | **ajouté** : écrit puis `fsync` | **ajouté** |
| **Téléchargement terminé** (`DownloadManager.finish`, aria2 n'appelle pas `fsync`) | **ajouté** : `fsync` du fichier avant d'annoncer « terminé » | **ajouté** (une fois par tâche) |
| Index, marques « vu », rangement (`AtomicFile`, `SafeFile`) | fichier temporaire, `fsync`, renommage | — |
| Lots, packs Quiz et Apprendre | mémoire interne (jamais sur la clé) | — |

### Ce qui n'est pas vérifié

Rien de ce chapitre n'a tourné sur la TV : les états réels qu'une TV GaiaOS diffuse pour une clé exFAT (a-t-elle un `checking` visible ? combien de temps ?), la présence de `Settings.ACTION_INTERNAL_STORAGE_SETTINGS` ou de « Éjecter » dans ses réglages, l'effet réel de la commande `sync` (permise par l'application ? sur ce noyau ?), la lecture d'une clé montée en lecture seule, l'écran à 720p et le focus. Parcours : **P-90** de `docs/test-plans/PARCOURS-CRITIQUES.md`. Vérifié en JVM (2026-10-07 ; `:core:test` : 4668 tests verts, `:receiver:compileDebugKotlin` sans erreur) : la règle (`UsbVolumeStateTest`, 36 tests), l'historique (`UsbVolumeTrackerTest`, 23), le réessai « un seul par montage » (`UsbMountRetryTest`, 12), le déroulement du retrait (`UsbSafeRemovalTest`, 22), le serveur réel (`SafeRemovalServerTest`, 23 : barrage, arrêt au bloc suivant, reprise octet pour octet, copie multivoie, déplacement, flux d'arrêt, copie en vérification jamais coupée), le vidage (`DiskFlushTest`, 16), les téléchargements (`DownloadHoldTest`, 10), la bannière d'activation (`UsbActivationBannerTest`) et la pastille (`TvSignalUsbNoteTest`). 136 mutations (règle, historique, réessai, retrait, serveur, téléchargements, vidage, bannière, pastille) : toutes tuées par un test qui passe au rouge. Ces tests ne remplacent pas l'essai sur la TV : voir P-90.
