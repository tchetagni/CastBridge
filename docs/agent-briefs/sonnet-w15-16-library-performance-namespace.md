# w15-16 — Bibliothèque : cache des noms par volume (fin du O(N²)), dossiers virtuels d'une clé absente conservés, suppression sûre, téléchargements et imports dans l'espace de noms, miniatures retentées, lots et parental sans gel
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus par échantillon (`/api/delete`) · statut : PRÊT
> **Groupe : W15-S2-c** (vague W15, tranche S2 ; fichiers disjoints de w15-13/14/15) · prérequis : w15-06 fusionné (`Filing`), w15-14 fusionné (compagnons de `delete`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*MultiVolume*' --tests '*Volumes*' --tests '*TvFolders*' --tests 'castbridge.core.tv.Filing*' --tests '*LibraryStore*' --tests 'castbridge.core.dl.*' --tests 'castbridge.core.lots.LotsDelivery*' --tests '*ParentalReports*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M-L, ≈ 2 j) · audit Opus : échantillon

**Vague 15 S2 (cœur + TV + téléphone) · Effort M-L · Modèle : sonnet · Statut PRÊT.** Branche `claude/sonnet-w15-16`. Rapport : `docs/agent-reports/sonnet-w15-16.md`. Règle : test rouge d'abord.

## Défauts traités (preuves `PLAN-STABILISATION` § 2.6)
B-01 (`C/tv/Volumes.kt:219-225,233,130,164-168`, `ReceiverServer.kt:812-880,944-952` : `dir.list()` à chaque `diskName` ⇒ O(N²) ; `organizeApply` refait le plan) · B-07 (`C/tv/Folders.kt:96-105`, `R/TvService.kt:249` : purge des dossiers d'une clé absente) · B-08 (`ReceiverServer.kt:459-475` : `/api/delete` supprime toutes les copies, ignore `streamUse`, pas de corbeille) · B-09 (`C/dl/DownloadManager.kt:323-333`, `C/tv/UsbImport.kt:107-114` : homonyme à plat masque le fichier rangé) · B-10 (`UsbImport.kt:65-87` : réimport, `renameTo` sans fsync) · B-12 (`C/tv/LibraryStore.kt:140` : échec de vignette permanent) · B-13 (`Volumes.kt:358-379`, `ReceiverServer.kt:901` : `refresh`/`markRemoved` sous verrou pendant un listage ; `listing()` en troupeau) · B-15 (`R/TvDownloads.kt:104-118`, `C/dl/Aria2Supervisor.kt:75-79`, `DownloadManager.kt:167-310` : wake lock tenu avec moteur FAILED ; RPC sous verrou ; `copyTo`+`delete`) · B-16 (`C/lots/DeliveryQueue.kt:118-120`, `S/LotsRuntime.kt:226-228` : SENT ignoré par `hasWork`) · B-17 (`S/ParentalInbox.kt:46,72`, `C/parental/ParentalSync.kt:211` : verrou partagé ; rejets acquittés) · B-18 (corbeille hors quota, restauration à la racine) · B-14 (`S/DownloadService.kt:49,178` : 2ᵉ téléchargement ignoré).

## Fichiers possédés
`C/tv/Volumes.kt` (`FileStore` : cache `Set` des noms réels par volume, invalidé par `invalidate()` ; `refresh` hors verrou), `C/tv/Folders.kt`, `C/tv/ReceiverServer.kt` (**zones** `libraryItems` cache 2 s, `delete`, `organize*`, `/api/folders`), `C/tv/UsbImport.kt`, `C/dl/DownloadManager.kt` (hors zone `atomicWrite` de w15-06), `C/dl/Aria2Supervisor.kt`, `C/tv/LibraryStore.kt` (zone `markFailed`), `C/library/agent/TvTrash.kt` (zone `restore` : dossier d'origine ; quota), `C/lots/DeliveryQueue.kt`, `C/parental/ParentalSync.kt` (zone ack), `S/LotsRuntime.kt` (zone `hasWork`), `S/ParentalInbox.kt`, `S/DownloadService.kt`, `R/TvDownloads.kt`, `R/TvService.kt` (**zone** `/api/folders` `:249` et rescan `:695-725`), tests `CT/MultiVolumeTest.kt`, `CT/VolumesTest.kt`, `CT/library/agent/TvFoldersTest.kt`, `CT/tv/FilingServerTest.kt`, `CT/LibraryStoreTest.kt`, `CT/dl/*`, `CT/lots/LotsDeliveryTest.kt`, `CT/ParentalReportsTest.kt`. **Hors zone** : `Filing.kt` (w15-06), `Mover`, `Companions`, lecteur, `TransferHost`.

## Étapes (test rouge, correctif, vert)
1. B-01 : `MultiVolumeTest.libraryListingIsLinearInFiles` (5 000 fichiers sur faux volume exFAT, compteur de `listFiles` ≤ 3 par `/api/library`) ⇒ cache de noms + `libraryItems()` 2 s + plan de rangement calculé une fois et rejoué par `apply` (jeton de plan).
2. B-07 : `TvFoldersTest.foldersOfAnAbsentVolumeSurvive` ⇒ `prune` limité aux noms absents de tous les volumes **connus** (présents + marqués retirés), id de volume par entrée.
3. B-08 : `delete` exige `volume` si `duplicate`, refuse (`409 busy`) si `busyReason(name)`, passe par la corbeille par défaut (`?hard=1` pour l'ancien comportement, PIN requis), emporte les compagnons (`Companions`).
4. B-09/B-10 : fin de téléchargement et import ⇒ `nameTaken` global puis `fileReceived` (rangement, `Meta`) ; import reconnaît un fichier déjà rangé par `findFinalOrOrigin` ; `AtomicFile`.
5. B-12 : échec de vignette retenté après 10 min puis 1 h (compteur) ; B-13 : `refresh` lit hors moniteur puis applique ; `listing()` sous un `synchronized` court avec résultat partagé (troupeau).
6. B-15 : `engineOnDemand` relance depuis FAILED après 5 min ; wake lock seulement si `ACTIVE ∧ moteur RUNNING` ; RPC hors verrou ; `copyTo` ⇒ `.part` + rename.
7. B-16/B-17/B-14 : `hasWork` compte SENT ; ack seulement pour les rapports stockés (rejets non acquittés, journalisés) ; verrou d'`init` séparé de `sync` ; `DownloadService` : 2ᵉ demande en file (modèle `UploadJobs` de w15-04) avec message.
8. B-18 : restauration dans le dossier d'origine (`.filing`) ; corbeille comptée dans `Storage.used`.
9. Vert : porte ; `:receiver:compileDebugKotlin`, `:sender:compileDebugKotlin`.

## Critères d'acceptation (hors ligne)
Porte verte ; ≥ 12 tests nouveaux rouges puis verts ; `grep -n "dir.list()" C/tv/Volumes.kt` dans `diskName` remplacé par le cache ; `/api/delete` : test « doublon sur deux volumes sans `volume` ⇒ 400 » ; aucune ligne de `Filing.kt` modifiée.

## À ne pas faire
Pas de nouvel index persistant de bibliothèque (cache mémoire seulement) ; ne pas changer le format `folders.db` au-delà d'un champ additif ; ne pas toucher `Filing.kt`, `Mover.kt`, le lecteur ; pas de texte W13.

## Rapport
`STATUT`, table défaut ⇒ test, mesure du nombre de `listFiles` avant/après, questions (quota de la corbeille : plafond proposé 10 % du volume).
