# « Le rangement sur téléphone ne crée pas d'arborescence de classement » (R-13)

Date : 2026-10-03. Branche `claude/copy-dedup-filing` (depuis `integration/agents` 6bd5ee26). Mots du propriétaire :
« le rangement sur téléphone ne crée pas d'arborescence de classement ». Travail **sur le code et en tests JVM seulement** (aucun émulateur, aucun appareil).
Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/` ; lignes d'avant correctif.

## 1. Inventaire des « rangements » et ce qui casse

| # | Fonction | Où | Crée-t-elle l'arborescence ? | Scénario qui échoue (avant) |
|---|---|---|---|---|
| 1 | **Option du téléphone** « Ranger automatiquement les nouveaux envois » (assistant, réglages) | `S/agent/AgentAuto.kt`, `S/UploadService.kt:130,412`, libellé `S/agent/AssistantScreen.kt:409` | **NON, par construction** : elle ne fait que **renommer** à l'envoi, désactivée par défaut, et son texte dit « Jamais de dossier » | le propriétaire l'active : aucun dossier n'apparaît (c'est ce que l'option promettait) |
| 2 | Rangement **par la TV à la réception** (beba7d8) | `C/tv/ReceiverServer.kt` `fileReceived`, `C/tv/Filing.kt` ; réglage TV `file_on_receive` **activé** par défaut (`R/TvService.kt:271`) | oui, mais : | (a) **« Copier et lire »** (le chemin principal du propriétaire, R-08) : le fichier est lu pendant qu'il arrive ⇒ `fileReceived` rend `null` (`streamUse.busy || isPlaying`, `ReceiverServer.kt:297`) et le fichier reste **à plat pour toujours** (seul « Ranger ma bibliothèque », manuel, le rangerait) ; (b) une vidéo sans année (`Avatar.mp4`, `ma_video.mp4`, `video.mp4`) va dans **« À trier »**, jamais dans `Films/` (`Filing.classify` : `MOVIE` < 0,8 ou `UNKNOWN` vidéo ⇒ `toSort`), verrouillé par l'ancien test `FilingServerTest.unknownNamesGoToATrierWithTheirOwnName` ; (c) un partage « Ouvrir avec » sans nom d'affichage envoie l'identifiant du fournisseur (`uri.lastPathSegment` : `1000023456`, `msf:1234`, sans extension : `S/UploadService.kt:98`, `S/OpenWithActivity.kt:233`, `S/TvTransferScreen.kt:75`, `S/TvHub.kt:66`) ⇒ `À trier/1000023456` sans extension (la TV classe avec `mime = null`) ; (d) photos à plat dans `Photos/` (aucun mois) |
| 2' | idem, **plusieurs fichiers** (file R-09), chemin rapide multivoie, chemin ordonné (R-08) | `uploadCounted` et `transferFinish` appellent tous deux `fileReceived` | oui : la file n'est **pas** en cause (chaque fichier passe par le même commit), le chemin rapide non plus | seul (a) concerne le chemin ordonné (lecture pendant la copie) |
| 2'' | TV **d'essai**, nom déjà pris | `TrialPolicy` (403 avant le premier octet) ; `NAME_TAKEN` (409, rien n'est écrit) ; collision de nom propre ⇒ « (2) » (`Filing.place`) | sans objet : aucun fichier n'est reçu | — (comportement voulu, gardé) |
| 3 | **Fichiers reçus de la TV** sur le téléphone (« Échanger des fichiers, dans les deux sens ») | `S/DownloadService.kt:103` (MediaStore `Téléchargements/CastBridge`), `:117` (Android 8-9), `:130` (dossier choisi) | **NON : tout atterrit à plat** dans `Téléchargements/CastBridge/` (ou à la racine du dossier choisi), aucune classification | télécharger un épisode et un film : deux fichiers côte à côte dans `Download/CastBridge` |
| 4 | Bibliothèque « Sur le téléphone » | `S/player/PhoneLibrary.kt` (MediaStore, onglet « Dossiers » par dossier) | ne range rien (lecture seule) ; profite des nouveaux sous-dossiers (seaux Films, Séries…) | — |
| 5 | **Assistant** « Ranger ma bibliothèque » lancé depuis le téléphone sur la **bibliothèque de la TV** | `S/agent/AssistantModel.kt:133` (`agentContext(folders = !tv)`), `C/library/agent/TvOps.kt:102-110` | **NON sur le disque** : seulement des dossiers **virtuels** (`/api/folders/set`, « moves no byte, changes nothing on the volumes ») ; rien sur la clé USB | lancer l'assistant sur la TV : la clé USB reste à plat. Le vrai rangement est le bouton « Ranger » de la bibliothèque TV du téléphone (`S/TvLibraryScreen.kt:262,299` → `/api/library/organize/apply`) |
| 6 | Assistant sur un **dossier du téléphone** (sélecteur système) | `C/library/agent/DocTree.kt` (`createDir`), `Planner.kt:83` (`ctx.folders && PHONE`) | oui (vrais dossiers) | — (fonctionne ; demande de choisir un dossier) |

**Lecture la plus plausible** des mots du propriétaire : le « rangement » réglé **sur le téléphone** (n° 1, son libellé dit « Jamais de dossier ») et
la copie depuis le téléphone qui n'arrive pas dans `Films/…` (n° 2 a-c, surtout « Copier et lire ») ; en second, les fichiers qui arrivent **sur le téléphone**
à plat (n° 3). Le n° 5 est signalé mais non changé (§ 5).

## 2. Ce qui change

- **`C/tv/FilingPlan.kt`** (nouveau, pur) : la catégorie et le nom propre d'une **nouvelle** copie, construits sur `Filing.classify` (même analyseur, mêmes
  noms, même placement sans écrasement) :
  vidéo non identifiée ⇒ `Films/` (nom gardé) ; audio ⇒ `Musique/` ou `Musique/<Artiste>/<Album>` si les étiquettes existent ; image ⇒ `Photos/<AAAA-MM>`
  si la date est connue (nom d'appareil photo, métadonnée), sinon `Photos/` ; document ⇒ `Documents/` ; séries `Séries/<Titre>/Saison NN` et films avec année
  `Films/` inchangés ; vraiment inconnu ⇒ `À trier/` (le dossier « Autres » de la bibliothèque : l'index `.filing`, la corbeille et « Ranger ma bibliothèque » le
  connaissent déjà ; un second dossier « Autres » aurait séparé les inconnus en deux) ; installateurs et paquets lus par leur nom ⇒ à plat.
  Meilleur nom : titre des métadonnées si le téléphone n'a qu'un identifiant de contenu ou un nom générique, **jamais un identifiant** dans le nom rangé.
  Noms Unicode, longs ou à caractères interdits sur exFAT : `SafeName` + `Filing.place` (≤ 250 octets, « (2) » en cas de collision).
- **TV** (`ReceiverServer`) : `fileReceived` utilise `FilingPlan` ; un fichier lu pendant sa réception est **mis de côté** et rangé dès que plus rien ne le lit
  (`playbackChanged`, `onPlaybackEnded` hors liste de lecture ; fil `cb-deferred-filing`, sous le verrou de nom des envois ; un fichier supprimé ou modifié
  entre-temps est oublié) ; « Ranger ma bibliothèque » (`/api/library/organize`) utilise le même plan (réception et rangement manuel d'accord) ;
  paramètre additif `filing=0` sur `PUT /upload` et `POST /api/transfer/begin` : le téléphone dont l'option est éteinte garde ce fichier à plat (valable
  pour cet envoi seulement ; une TV plus ancienne l'ignore).
- **Téléphone** : réglage « **Classer les nouveaux envois dans des dossiers** », **activé par défaut** (`AgentSettings.fileTree`), dans l'écran de réglages
  de l'assistant existant, avec l'explication d'une ligne : « Activé par défaut : chaque copie vers la TV (et sa clé USB) va dans Films, Séries/Titre/Saison,
  Musique, Photos/AAAA-MM ou Documents ; ce qui vient de la TV va dans Téléchargements/CastBridge, classé de même. Désactivez pour garder les nouveaux fichiers à
  la racine. » L'ancienne option devient « Renommer automatiquement les nouveaux envois » (toujours désactivée par défaut, texte corrigé). `UploadService`
  envoie `filing=0` si le réglage est éteint, et remplace un identifiant de contenu ou un nom générique par le **titre** MediaStore (`FilingPlan.sendName` ;
  sans titre, un nom **stable** `msf_1234.mp4` : la reprise après coupure garde le même nom et deux vidéos ne se disputent jamais un nom).
- **TV → téléphone** (`S/DownloadService.kt`) : `Téléchargements/CastBridge/<Catégorie>/…` (MediaStore `RELATIVE_PATH`, sans nouvelle permission), même
  arbre sous Android 8-9 (dossier propre de l'app) et dans un dossier choisi (sous-dossiers créés par `DocumentsContract`, dossier existant réutilisé, à la
  racine du dossier choisi si le fournisseur refuse).

Gardé : jamais d'écrasement (`fileInto` refuse une cible existante), `NAME_TAKEN` / `PART_OTHER`, R-08 (le fichier lu garde son nom tant qu'il est lu),
R-09 (file inchangée), copie vérifiée avant la suppression d'un « Déplacer » (`checkMoved`, `MoveProof`), réglage TV `file_on_receive` (éteint = à plat).

## 3. Preuves

ROUGE (ébauche de `FilingPlan` = ancien `Filing.classify`, pas de rangement différé ni de `filing=0`) : `:core:test` ciblé → « 49 tests completed, 21 failed »,
dont **11 sur cette partie**, tous par assertion : `FilingPlanTest` (7 : vidéo non identifiée ⇒ `À trier/ma_video.mp4` au lieu de `Films/`, noms génériques et
identifiants, musique par étiquettes, photos par mois, Unicode/long (« Famille » pour un titre contenant « vacances »), arbre du téléphone, nom envoyé) ;
`FilingTreeServerTest` (4, vraie `ReceiverServer` : `Films/` créé, file de 5 envois, **« Copier et lire » resté `Inception.2010.1080p.mkv` à plat**, `filing=0`).
Verts dès le rouge (gardes) : TV d'essai sans dossier ni fichier, clé exFAT. Ajoutés ensuite : `theFastMultiConnectionPathIsFiledAndHonoursTheSwitchToo`,
`theFolderTreeIsOnByDefaultForNewCopiesAndCanBeSwitchedOff`. Test existant modifié (décision R-13) : `FilingServerTest.unknownNamesGoToATrierWithTheirOwnName`
devient `unknownNamesKeepTheirOwnNameAndVideosGoToFilms` (`Films/ma_video.mp4`, `À trier/notes.xyz`).

## 4. Vert

Voir § 7.

## 5. Risques et limites

- Changement visible : les vidéos non identifiées vont désormais dans `Films/` (avant : `À trier/`) ; une vidéo familiale sans mot-clé ni date d'appareil peut
  s'y retrouver (« Mariage », « vacances », noms d'appareil photo et WhatsApp vont toujours dans `Famille/`).
- Un fichier mis de côté pendant la lecture n'est rangé qu'au prochain changement d'état du lecteur ; un redémarrage de la TV entre-temps le laisse à plat
  (« Ranger ma bibliothèque » le range). Jamais pendant une liste de lecture (elle tient les noms plats).
- Le choix `filing=0` vit en mémoire de la TV jusqu'au commit ; une TV redémarrée au milieu d'un envoi « à plat » le range (sans perte).
- Volumes du sélecteur système côté TV (SAF) : pas de dossiers réels (inchangé).
- Non changé : l'assistant du téléphone sur la bibliothèque **TV** reste en dossiers virtuels (n° 5) ; le bouton « Ranger » de la bibliothèque TV fait le vrai
  rangement. Piste : brancher l'assistant sur `/api/library/organize/apply` (hors gel).
- Les fichiers déjà à plat dans `Download/CastBridge` ne sont pas déplacés (seules les nouvelles réceptions sont classées).

## 6. Ce que seuls les vrais appareils confirment

Création des dossiers `Download/CastBridge/<Catégorie>` par MediaStore sur le S21+ (Android 14) et leur affichage dans « Fichiers » / la Galerie ; un
dossier choisi sur carte SD (fournisseur qui refuse peut-être la création de dossiers : le fichier va alors à la racine) ; le rangement différé après « Copier et
lire » sur la vraie TV (libVLC relâche le fichier à l'arrêt) ; la clé exFAT réelle ; le titre MediaStore d'un partage Telegram / WhatsApp.

## 7. Vert (suite complète, même exécution pour R-12 et R-13)

`gradle --offline :core:test :sender:compileDebugKotlin :receiver:compileDebugKotlin` (verrou `gradle-lock.sh`, une exécution après la dernière modification) → BUILD SUCCESSFUL : **3 067 tests, 0 échec**, 4 ignorés (préexistants) ; `DedupDecisionTest` 13/13, `ContentIndexServerTest` 6/6, `CopyDedupQueueTest` 4/4, `FilingPlanTest` 13/13, `FilingTreeServerTest` 7/7, `FilingServerTest` 17/17, `TrialRoutesTest` vert ; `python3 tools/tests/test_routes.py` → OK (routes.txt : + `/api/have`, + `/api/rental/usage` qui manquait déjà à HEAD).
