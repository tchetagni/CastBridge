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
