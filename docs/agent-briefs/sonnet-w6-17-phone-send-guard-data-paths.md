# w6-17 — CastBridge (téléphone) : garde de la file de transfert (`SendGuard`) et chemins de données (lots, téléchargements, lecteur, jeux, Apprendre, boutique, bibliothèque et admin de la TV, tunnel)

**Vague 6d · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT (après 6a ; en parallèle de w6-16 : coder contre `PhoneGateRuntime`/`GateWall` du contrat de w6-16 ; tant qu'ils n'existent pas, un `object PhoneGateRuntime` minimal **dans `S/gate/GateStub.kt`** que w6-16 supprimera — le dire).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.4 (chemin des données), § 3.7 (garde, matrice), § 3.6. Branche `claude/sonnet-w6-17`. Rapport : `docs/agent-reports/sonnet-w6-17.md`.

## Objectif
Faire respecter la matrice **sur le chemin des données**, pas seulement dans l'interface : (1) `C/xfer/SendGuard.kt` (pur, testé) : `check(target: TargetTvState): Verdict` (`Allowed` / `Refused(messageId)`) utilisé par la file de transfert **avant d'enfiler** ; `TargetTvState(edition: TvEditionState, sync: SyncClass, proofValid: Boolean)` ; (2) Android : `TransferQueue.enqueue` (cœur `C/xfer/**` : trouver la classe de file, `grep -rn "class TransferQueue" C/ S/`) reçoit le verdict et **refuse d'enfiler** ; `TransferQueueService`, `UploadService`, `BtUploadService`, `FastTransfer`, `MoveToTv`, `DlnaHandoff` (mode « déplacer/copier »), `ShareToTvActivity` (partage depuis une autre app), `TransferQueueService.onStartCommand` : refus **avant tout démarrage** avec le message du catalogue (notification ou toast via `PhoneGateTexts`), rien de mis en attente pour une TV non prouvée ; (3) `LotsRuntime` : sans `Linked`, ne télécharge/livre que `LotEditions.TRIAL` (`LotPlanner`/`LotSync` : filtre `allowed`), `X-CB-TV-Proof` (jeton d'activation de la preuve) ajouté aux requêtes de lots complets ; (4) `DownloadService.start` ⇒ refus ; (5) `player/PlaybackService` et `PhoneLibraryScreen` ⇒ `GateWall(PHONE_LIBRARY_PLAYER)` + refus de démarrage ; (6) `GamesScreen`, `LearnScreen`, `QuizScreen`, `ChessScreen` ⇒ `GateWall` ; (7) `TvHub`/`TvLibraryScreen` : `TV_LIBRARY_BROWSE/MANAGE`, `TV_ADMIN` (mise à jour de CastBridge-TV **autorisée** en E), `INTERNET_GATEWAY_FOR_TV` ouvert, `CAST_TO_LINKED_TV` ouvert (« TV décide ») ; (8) `S/shop/**` (si w5-11/12 fusionnés) : `SHOP_BROWSE` ouvert en lecture, `SHOP_ORDER`/`TOKENS` derrière le mur + en-tête `X-CB-TV-Proof` dans `HttpShopApi` ; (9) `BtSshGateway`/relais du tunnel : `REMOTE_TUNNEL_GATEWAY` ; (10) refus **de la TV** (403 `TrialPolicy`/`DegradedPolicy`/parental) ⇒ `M-TV-REFUSED`/`M-PARENTAL-BLOCKED` via le catalogue + `PhoneGateRuntime.syncNow`.

## Pourquoi (preuves)
- `S/TransferQueue.kt`, `S/TransferQueueService.kt`, `S/UploadService.kt`, `S/BtUploadService.kt`, `S/FastTransfer.kt`, `S/MoveToTv.kt`, `S/DlnaHandoff.kt`, `S/ShareToTvActivity.kt` (tous présents, `ls S/`) ; `C/xfer/**` ; `S/LotsRuntime.kt` (en-tête : « never needs the TV », `LotDelivery`), `C/lots/LotSync` (option `allowed`, TRIAL-EDITION § 1), `C/lots/LotEditions` ; `S/DownloadService.kt` ; `S/player/{PlaybackService,PhoneLibrary}.kt` ; `S/TvHub.kt:262-273` (APK sur la TV), `S/TvLibraryScreen.kt` ; `C/owner/TrialPolicy.kt:31-45` (ce que la TV ferme déjà : `/api/transfer`, `/api/upload`, `/upload`…) ; DESIGN-W4-MODE-DEGRADE § 3 (`DegradedPolicy`) ; w6-09 (format 403 serveur).

## Fichiers possédés
Nouveaux `C/xfer/SendGuard.kt`, `CT/xfer/SendGuardTest.kt`, `S/gate/GateStub.kt` (temporaire) ; modifiés `S/{TransferQueue,TransferQueueService,UploadService,BtUploadService,FastTransfer,MoveToTv,DlnaHandoff,ShareToTvActivity,LotsRuntime,DownloadService,DownloadsScreen,GamesScreen,LearnScreen,QuizScreen,ChessScreen,TvHub,TvLibraryScreen,TvTransferScreen,BtSshGateway}.kt`, `S/player/{PlaybackService,PhoneLibrary}.kt`, `S/shop/{ShopScreen,HttpShopApi,ShopRuntime}.kt` (si présents), la classe de file dans `C/xfer/**` (nommée dans le rapport). **Hors zone** : `S/MainActivity.kt`, `S/TvLink.kt`, `S/gate/{ProofStore,ProofSync,PhoneGateRuntime,GateWall,TargetTvChip}.kt` (w6-16), `S/Parental*.kt` (w6-18), `OL/**` (w6-19), `C/owner/**`.

## Étapes
1. `SendGuard` : table exacte de la ligne « Copier / envoyer un média » de la matrice (A-H) ; tests : essai ⇒ `M-TV-TRIAL`, dégradé ⇒ `M-TV-ENDED`, jamais synchronisée ⇒ `M-SYNC-FIRST`, expirée ⇒ `M-PROOF-EXPIRED`, injoignable ⇒ `M-TV-UNREACHABLE`, production fraîche ⇒ `Allowed`, super ⇒ `Allowed` (la TV décide).
2. File de transfert : `enqueue(item, target)` ⇒ `Refused` **sans** persistance ; services : `onStartCommand` vérifie et s'arrête (`stopSelf`) avec notification du message ; `ShareToTvActivity` : mur avant la liste des TV, et par TV cible dans la liste (une TV d'essai est grisée avec « essai : copie impossible », une TV de production est choisie par défaut : `SEND_TO_OTHER_TV`).
3. Lots : filtre ; en-tête ; message `M-TRIAL-LOTS-ONLY` dans `LotsScreen`.
4. Écrans : `GateWall` autour du contenu de chaque écran listé ; `TvHub` : « Installer des APK » reste ouvert en E **seulement** pour l'APK de CastBridge-TV (vérifier le nom de paquet) ; cast/télécommande sans mur.
5. Refus de la TV : un `TvClient.HttpError` 403 portant `Version d'essai`/`Mode réduit`/`parental` ⇒ `PhoneGateTexts.refusalFromTv` + `syncNow`.
6. Télémétrie : événement `feature_used` inchangé ; **pas** d'événement « refus » (pas de nouveau nom au catalogue sans w6-20).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.xfer.SendGuardTest'   # vert, ≥ 8 cas
cd android && gradle --offline :sender:compileDebugKotlin   # compile
grep -rn 'SendGuard' android/sender/src/main/kotlin | wc -l   # ≥ 7 (tous les chemins d'envoi)
grep -rn 'exige une TV' android/sender/src/main/kotlin | grep -v PhoneGateTexts | wc -l   # 0
```
Observable (`-PrequireTvProof=true`, TV d'essai) : « Envoyer » depuis la galerie Android vers CastBridge ⇒ mur M-TV-TRIAL, **aucune** entrée dans la file (`adb shell run-as … ls files/` : pas de fichier de file nouveau) ; « Lire en direct » fonctionne ; après clé de production + synchro : l'envoi part.

## Cas limites
Course : la clé expire entre deux synchros ⇒ la TV refuse (`DegradedPolicy`), le téléphone affiche `M-TV-REFUSED` puis synchronise et passe en E ; envoi en cours quand la preuve expire : **on ne coupe pas** un transfert commencé ; file déjà remplie avant W6 pour une TV d'essai : purgée au premier démarrage avec une notification « N envois annulés : <message> » ; multi-chemins (`TRANSFER_MULTIPATH`) suit `SEND_FILES_TO_TV`.

## À ne pas faire
Aucune phrase hors catalogue ; ne pas masquer les écrans ; ne pas supprimer de média du téléphone ; ne pas toucher aux fichiers de w6-16/18/19 ; ne pas interrompre un transfert commencé.

## Rapport
`STATUT`, classe de file trouvée, liste des points gardés, captures.
