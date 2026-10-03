# Éviter les doublons lors de la copie : empreinte du contenu avant l'envoi (R-12)

Date : 2026-10-03. Branche `claude/copy-dedup-filing` (depuis `integration/agents` 6bd5ee26). Demande du propriétaire, mot pour mot :
« éviter les doublons lors de la copie, vérifie le hash des fichiers disponibles pour empêcher la copie de doublons en amont ».
Travail fait **sur le code et en tests JVM seulement** : aucun émulateur, aucun appareil, aucun `adb`.

## 1. Constat (avant)

| Où | Ce qui se passait | Preuve |
|---|---|---|
| TV, réception | seuls les conflits de **nom** sont refusés (`NAME_TAKEN` : même nom, autre taille ; `PART_OTHER` : partiel d'un autre contenu) ; même nom + même taille = « déjà là » | `C/tv/ReceiverServer.kt` `uploadCounted` (`nameTaken`, `partOtherJson`), `transferFinish` |
| TV, bibliothèque | le doublon de contenu n'est signalé **qu'après** la copie (`duplicate` par nom normalisé + taille) | `C/tv/Library.kt:78`, `C/tv/LibraryStore.kt` |
| Téléphone, file | le seul contrôle avant l'envoi est **nom + taille** (`TvDedupe.alreadyThere`) | `S/TransferQueue.kt` (`dedupe`) |
| Résultat | le même film renommé (« Film (1).mp4 », copie de « Fichiers », partage depuis une autre app), ou déjà rangé par la TV sous un autre nom (`Films/Titre (2010).mkv`), était **recopié en entier** ; un « Déplacer » aussi | — |

## 2. Ce qui change

**TV — index du contenu** (`C/tv/ContentIndex.kt`, nouveau) : taille + SHA-256 de chaque fichier **terminé** des dossiers réels (mémoire interne,
clé USB ; pas les dossiers du sélecteur système), calculés **paresseusement** :
- un seul fil démon `cb-content-index`, priorité minimale (`Thread.MIN_PRIORITY`, soit `nice 19` sous Android), lectures par blocs de 1 Mio
  bornées à 16 Mio/s (`RatePacer`) ;
- **jamais** pendant une lecture ou une mise en mémoire tampon, une réception (PUT, multivoie, Bluetooth via `TransferProgress`) ni un déplacement :
  `ReceiverServer.indexIdle()`. La lecture en cours d'un fichier **se met en pause** (empreinte partielle gardée en mémoire) et ne reprend que
  si le fichier n'a pas changé (taille + mtime) ; jamais sur le fil de l'interface ni sur un fil de requête (R-06, R-08, R-11) ;
- valide pour (chemin, taille, mtime) : un fichier modifié redevient « inconnu », jamais « présent » sur une vieille empreinte ;
- cache **sur chaque volume** (`.cbhash`, écrit atomiquement, fichier caché ignoré par la bibliothèque) : reprise après redémarrage, suit la clé USB ;
  ligne abîmée ignorée ; un fichier rangé à la réception garde son empreinte (`renamed`) ;
- une question ne fait **jamais** lire un fichier : ce qui n'est pas encore connu répond « indexing » et passe en tête de la file du fil de fond.

**TV — route additive** `GET /api/have?size=N[&sha256=H]` (code PIN ou jeton comme les autres routes de fichiers ; **fermée en essai** par la liste
blanche de `TrialPolicy`, classée dans `TrialRoutesTest`, `tools/routes/routes.txt` régénéré) :
- `{"state":"absent"}` ; `{"state":"candidates","count":n,"indexing":bool}` (question par taille : **jamais un nom**) ;
- `{"state":"indexing","pending":n}` (même taille, empreinte pas encore prête) ;
- `{"state":"present","name","folder","volume","size","sha256","complete":true}` : le **seul** fichier correspondant.
- 400 pour une taille ou une empreinte mal formées. Une TV plus ancienne répond 404 : le téléphone copie comme avant.

**Téléphone — avant de lancer chaque élément de la file** (`S/TransferQueue.kt` `contentDedupe`, chemin Wi-Fi : TV de confiance, ou TV à code dont
l'adresse est connue) :
1. question **par taille** ; aucune empreinte si la TV n'a aucun fichier de cette taille et si la file n'en a pas d'autre (une vidéo de 4 Go n'est
   jamais hachée pour rien) ;
2. sinon SHA-256 du fichier du téléphone, **lu en flux hors du fil principal** (`Dispatchers.IO`), « Vérification de « nom » (doublon ?) : n % »
   dans la carte de la file, annulable par « Annuler » (aucune décision sur une empreinte partielle) ;
3. question par empreinte ; si « indexing », 4 nouvelles questions à 3 s (la TV traite ce fichier en premier), puis la copie part ;
4. décision pure `DedupDecision` (`C/tv/DedupDecision.kt`) :

| Cas | Copie | Message |
|---|---|---|
| TV : même taille + même SHA-256, fichier complet ; COPIER | non | « Déjà sur la TV : Films/… — contenu identique, non recopié. » |
| idem ; COPIER ET LIRE | non : la TV lit **son** fichier (au point de lecture du téléphone par `CastSession`, via `heldAs`) | « … lecture du fichier de la TV, sans copie. » |
| idem ; DÉPLACER | non ; suppression de l'original proposée **seulement** si `MoveProof.byContentHash` (§ 3) | « … l'original peut être retiré du téléphone (Android demande confirmation). » |
| même contenu qu'un fichier **déjà envoyé par cette file** (deux noms, deux fichiers du téléphone), présent sur la TV | non (lecture pour COPIER ET LIRE) ; DÉPLACER : l'original **reste** | « Même contenu que « … » … » / « … l'original reste sur le téléphone … » |
| même taille, autre empreinte ; TV ancienne ; « indexing » ; partiel ; empreinte locale absente ou annulée | oui | — (la TV signale le doublon après coup, comme avant) |
| « Copier quand même » (proposé **une fois** dans la carte de la file) | oui, nouvel élément forcé | — |

Doublons **dans** la file : même fichier deux fois = un seul élément (règle R-09 inchangée) ; même contenu sous deux noms : l'élément suivant est
haché (avec les éléments déjà envoyés de même taille, une seule fois, empreinte gardée avec la file) puis reconnu (`twinOf`), sans copie.

## 3. Règle de suppression d'un DÉPLACEMENT sans copie (pour l'audit)

`C/tv/MoveRequests.kt` `MoveProof.byContentHash` — l'original n'est supprimé **que** si TOUT est vrai :
1. le téléphone a calculé lui-même l'empreinte de l'original, à partir de ses octets, juste avant (jamais une empreinte reçue) ;
2. la TV a répondu `present` pour un fichier **terminé**, dont elle a calculé l'empreinte à partir de **ses** octets sur le disque, encore valide pour sa taille et son mtime ;
3. tailles égales et > 0, empreintes SHA-256 égales (64 hexadécimaux) ;
4. alors seulement, la demande passe par `UploadService.offerVerifiedMove` → `MoveHandler` : Android demande confirmation (`createDeleteRequest` / `deleteDocument`).

Tout le reste (un nom, une taille, « indexing », un jumeau de la file, une TV ancienne) n'est **pas** une preuve : l'original reste, le message le dit.
Le déplacement normal (avec copie) garde son contrôle existant (`checkMoved`). Une collision SHA-256 entre deux fichiers de même taille est tenue pour impossible.

## 4. Preuves

ROUGE (ébauches qui compilent : décision toujours « copier », index toujours « absent », jamais de jumeau) : `:core:test` ciblé →
« 49 tests completed, 21 failed » dont **10 sur les 23 tests de cette partie**, tous par assertion (`AssertionError` / `ComparisonFailure`) :
`DedupDecisionTest` (5 : présent ⇒ non recopié, lire, déplacer vérifié, jumeau, déplacer invérifiable), `ContentIndexServerTest` (4 : présent après
indexation, mtime, lecture en cours, reprise après redémarrage), `CopyDedupQueueTest` (1 : jumeau dans la file).

VERT : voir § 7 (suite complète après la dernière modification).

## 5. Risques

- **Temps d'indexation** sur une grosse bibliothèque (clé exFAT USB 2 : 16 Mio/s bornés ⇒ ~1 h pour 60 Go) : tant qu'un fichier n'est pas haché,
  la copie part (jamais bloquée) et la TV signale le doublon après coup, comme avant. Les fichiers interrogés passent en tête.
- **Contention** : l'index lit la clé quand la TV est inactive ; une lecture qui démarre met la lecture d'index en pause au bloc suivant (≤ 1 Mio, ≤ 2 s).
- **mtime** : une clé passée sur un ordinateur qui réécrit les dates (copie) force un nouveau calcul (sûr, juste plus lent).
- Chemins **hors file** (`TvScreen`, `TvHub`, `DlnaHandoff` appellent encore `UploadService.start` directement) et le **Bluetooth** : pas de contrôle
  de contenu (rien ne change pour eux). Une TV à code trouvée seulement par découverte (sans adresse connue) n'est pas interrogée.
- Le cache `.cbhash` est un fichier caché dans le dossier de la bibliothèque : ignoré par `list()`, `FiledIndex`, le nettoyage des orphelins.

## 6. Ce que seuls les vrais appareils confirment

Durée réelle du hachage sur le S21+ (fichier de 4 Go : attendu ~10 s) et sur la TV GaiaOS 32 bits (SHA-256 Java sur A55) ; absence d'à-coups de
lecture pendant que l'index tourne puis s'interrompt ; dialogue de suppression d'Android pour un « Déplacer » sans copie ; comportement d'une clé
FAT32/exFAT réelle (mtime à 2 s).

## 7. Vert

`gradle --offline :core:test :sender:compileDebugKotlin :receiver:compileDebugKotlin` (verrou `gradle-lock.sh`, une exécution après la dernière modification) → BUILD SUCCESSFUL : **3 067 tests, 0 échec**, 4 ignorés (préexistants) ; `DedupDecisionTest` 13/13, `ContentIndexServerTest` 6/6, `CopyDedupQueueTest` 4/4, `FilingPlanTest` 13/13, `FilingTreeServerTest` 7/7, `FilingServerTest` 17/17, `TrialRoutesTest` vert ; `python3 tools/tests/test_routes.py` → OK (routes.txt : + `/api/have`, + `/api/rental/usage` qui manquait déjà à HEAD).
