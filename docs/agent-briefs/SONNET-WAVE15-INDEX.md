# Vague 15 pour agents Sonnet/Haiku — index (2026-10-02) : stabilisation des fonctions multimédias et de synchronisation, et ordre fusionné avec W13 et W14

Source : `docs/coordination/PLAN-STABILISATION-MULTIMEDIA-SYNC-2026-10-02.md` (lire § 0, § 2.8 et le § cité par chaque cahier) ; endurance : `docs/test-plans/ENDURANCE.md`. Protocole et règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »), gabarits `EXECUTOR-PROMPT-TEMPLATE.md`. Ce fichier ne modifie pas les index des vagues 1-14 ; le § « Ordre fusionné » et le § « Changements aux cahiers antérieurs » disent comment W13, W14 et W15 s'enchaînent sans se chevaucher.

**Demande du propriétaire (2026-10-02)** : « stabilise toutes les fonctions multimédias et de synchronisation » ; « il y a trop de régression » ; **gel des fonctionnalités**. Cette vague ne livre **aucune fonctionnalité** : des causes racines corrigées, chacune par un test rouge d'abord.

**Règles W15 (en plus des règles communes)** : **(R1)** tout cahier commence par le test qui échoue sur `integration/agents` et cite sa sortie rouge puis verte ; **(R2)** au plus **2 cahiers risqués** en parallèle (transfert, liaison, confiance, cycle de vie) et **un seul** à la fois sur `C/tv/ReceiverServer.kt`, `C/trust/LinkDriver.kt`, `S/UploadService.kt`, `S/TvLink.kt`, `R/TvService.kt` ; **(R3)** aucune fusion hors W13/W14/W15 et correctifs terrain pendant le gel ; **(R4)** audit Opus sur tout diff de ces zones ; **(R5)** fichiers hors zone gelés ; **(R6)** la TV du propriétaire n'est jamais la cible d'un script ; **(R7)** après chaque fusion risquée : `:core:test` complet + `compileDebugKotlin` des deux apps + fumée W14 `--tv fake` dès qu'elle existe.

**Modèle d'exécution** : `haiku` = docs, campagne (tout est donné) ; `sonnet` = le reste ; **audit Opus obligatoire** sur w15-01, 02, 03, 04, 05, 10, 11, 17 (transfert, secret, liaison, cycle de vie) ; échantillon sur w15-06, 12, 13, 15, 16.

## Les 18 cahiers

| id | Cahier | Objet | Tranche | Effort | Modèle | Audit Opus | Jauge (entrée / sortie, k) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w15-01 | `sonnet-w15-01-tv-receive-progress-sweep.md` | TV : `TransferHost.progress()`, `receiving()` fusionné, push vers l'accueil, balayage des sessions, éviction au lieu du 429 (R-04 ; T-01, T-02, T-06) | S0-a | M | sonnet | **oui** | 400 / 20 | PRÊT (après `diag-receiver-progress`) | — |
| w15-02 | `sonnet-w15-02-pinkeys-resolution.md` | `C/trust/PinKeys` : résolution tolérante des clés d'écran, `PinStore` par toutes les clés (R-01 ; L-01) | S0-a | S-M | sonnet | **oui** | 250 / 14 | PRÊT | — |
| w15-03 | `sonnet-w15-03-bt-keepalive-token.md` | garde-vivant Bluetooth-tunnel par `check`, `issueToken` idempotent, persistance hors verrou (R-01 ; L-02, L-03, L-07) | S0-a | M | sonnet | **oui** | 400 / 18 | PRÊT | — |
| w15-04 | `sonnet-w15-04-phone-upload-services-bounds.md` | services d'envoi : `SecurityException`, bornes, `onTimeout`, `startForeground` protégé, génération de travail, état BT initial, `abort` (X-15, X-04, X-01, X-02, X-05, X-16, Q-01) | S0-a | M | sonnet | **oui** | 450 / 22 | PRÊT | — |
| w15-05 | `sonnet-w15-05-no-deletion-without-proof.md` | `/api/part?size`, 409 `NAME_TAKEN`, preuve avant « Déplacer », `MoveRequests` persistée, `Mover` revérifie et hache (X-03, X-07, B-02) | S0-b | M | sonnet | **oui** | 450 / 22 | PRÊT | w15-01, w15-04 |
| w15-06 | `sonnet-w15-06-persistence-clock-index-registry.md` | corbeille sous horloge suspecte, `.filing` tolérant, registre `.bak`, état des téléchargements atomique (B-03, B-05, L-20, B-10b) | S0-b | M | sonnet | échantillon | 300 / 16 | PRÊT | w15-03 |
| w15-07 | `sonnet-w15-07-deterministic-tests.md` | ports liés directement, bornes, keep-alive, `LearnLotsTest` tolérant (Q-01, Q-02, Q-03) | S1 | S | sonnet | non | 150 / 8 | PRÊT | w15-04 |
| w15-08 | `sonnet-w15-08-soak-harness.md` | `tools/soak/` (scénarios, perturbations, relevés, rapport) + `GET /api/transfer/sessions` | S1 | M | sonnet | non | 350 / 25 | PRÊT | w15-01 (W14 w14-10 souhaité) |
| w15-09 | `sonnet-w15-09-docs-alignment.md` | CHANGELOG, HANDOFF § 0/§ 9, REGRESSIONS R-01…R-07, STORAGE (UsbMigration), TRANSFER, tests Python locaux (Q-04, Q-06, B-04) | S1 | S | haiku | non | 120 / 12 | PRÊT | S0 fusionnée |
| w15-10 | `sonnet-w15-10-transfer-engine-hardening.md` | ordonnanceur, 429/503, récursion, `HttpConn`, exclusion classique/rapide, `finish` répété, préallocation FAT, séries hors Compose (X-06…X-13, X-17, X-22, T-03…T-05, T-16…T-18) | S2-a | M-L | sonnet | **oui** | 500 / 25 | PRÊT | S0, W13 w13-04 |
| w15-11 | `sonnet-w15-11-link-trust-hardening.md` | `connect()` sous verrou et délai, appairage annulable, refus non comptés, homonymes, permissions (L-04, L-10 tél., L-12, L-13, L-14, L-21, L-22) | S2-a | M | sonnet | **oui** | 450 / 22 | PRÊT | w15-03 |
| w15-12 | `sonnet-w15-12-discovery-tunnel.md` | `TvDiscovery` unique + `ResolveQueue`, `LinkPool` repli sur refus seulement, `Mux` sans tête de ligne, réveils (L-08, L-09, L-16, L-18, L-19) | S2-b | M | sonnet | échantillon | 350 / 18 | PRÊT | w15-03 |
| w15-13 | `sonnet-w15-13-tv-player-hardening.md` | `PlayerPolicy` : sauvegarde périodique, `stop()` hors fil principal, repli logiciel, progressif borné, miniatures, Langues/Learn (P-04…P-07, P-11, P-12, P-14, P-16, P-17) | S2-c | M | sonnet | échantillon | 350 / 18 | PRÊT | S0 |
| w15-14 | `sonnet-w15-14-companion-files-subtitles.md` | `Companions`, `SubtitleCharset`, sous-titres qui suivent la vidéo partout (P-01, P-02, P-03, P-03b, B-06) | S2-c | M | sonnet | non | 300 / 16 | PRÊT | w15-05, w15-06 |
| w15-15 | `sonnet-w15-15-phone-player-streaming.md` | `MediaToken`, `CastSession` qui abandonne, notification Media3 retirée, cache en octets, DLNA (P-08…P-10, P-15, P-18…P-21, F-06, F-07) | S2-c | M | sonnet | échantillon | 350 / 18 | PRÊT | S0 |
| w15-16 | `sonnet-w15-16-library-performance-namespace.md` | cache des noms (fin du O(N²)), dossiers d'une clé absente, `delete` sûr, téléchargements/imports rangés, vignettes, lots, parental (B-01, B-07…B-18, B-14) | S2-c | M-L | sonnet | échantillon | 450 / 22 | PRÊT | w15-06, w15-14 |
| w15-17 | `sonnet-w15-17-tv-lifecycle-notifications.md` | `TvService` : boot sûr, Bluetooth relancé, permissions, wake lock, `restartApp`, pool, `TvClock` unique (T-07…T-15, T-19, L-05, L-06, L-11, L-17, B-11, T-08) | S2-a | M | sonnet | **oui** | 450 / 22 | PRÊT | w15-01 |
| w15-18 | `sonnet-w15-18-soak-campaign.md` | trois nuits d'endurance, rapports, lignes de régression | S3 | S × 3 | haiku | non | 100 / 10 par nuit | PRÊT (après S2) | w15-08, S2 |

Coûts (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**) : sonnet 15 × ≈ 1 $ ≈ 15 $ ; haiku 2 × ≈ 0,2 $ ≈ 0,5 $ ; audits Opus 8 × ≈ 0,8 $ ≈ 6,5 $ ; échantillons ≈ 1,5 $. **Total ≈ 23-25 $**, ≈ 27 agent·jours (S0 ≈ 9, S1 ≈ 3, S2 ≈ 14, S3 ≈ 1,5), ≈ 3 semaines calendaires avec deux risqués en parallèle.

## Tranches

| Tranche | Cahiers | Livre | Parallélisme (R2) |
|---|---|---|---|
| **S0-a** | w15-01 ∥ w15-03 (risqués), puis w15-02 ∥ w15-04 (risqués) | R-04 et R-01 corrigés à la cause, plus de plantage connu | 2 à la fois |
| **S0-b** | w15-05 ∥ w15-06 | plus de suppression sans preuve ; persistance qui survit à l'horloge, aux lectures en échec, aux coupures | w15-05 risqué + w15-06 non risqué |
| **S1** | w15-07 ∥ w15-08 ∥ w15-09 | tests déterministes, banc d'endurance, docs vraies | 3 (non risqués) |
| **S2-a** | w15-10 ∥ w15-17 (risqués) ; puis w15-11 (risqué) ∥ w15-12 | moteur, cycle de vie TV, liaison, découverte/tunnel | 2 risqués à la fois |
| **S2-c** | w15-13 ∥ w15-14 ∥ w15-15 ∥ w15-16 | lecteurs, compagnons, streaming téléphone, bibliothèque | 4 (non risqués ; w15-16 après w15-14) |
| **S3** | w15-18 | trois nuits conformes ⇒ sortie de stabilisation | — |

## Matrice de propriété (preuve de disjonction par tranche)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OD/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`.

### S0-a
| id | Fichiers possédés |
|---|---|
| w15-01 | `C/xfer/TransferHost.kt`, `C/tv/ReceiverServer.kt` (zones `listing/receiving/activeTransfers`, `begin`), `C/tv/TvInfo.kt` (additif), `R/TvService.kt` (zone `statusesChanged`/`transferTick`), `R/HomeScreen.kt` (zone `refreshStatus`), `R/PlayerActivity.kt` (zone `status()`), `CT/MultipathServerTest.kt`, `CT/UxTest.kt` |
| w15-02 | nouveaux `C/trust/PinKeys.kt`, `CT/trust/PinKeysTest.kt` ; `S/PinStore.kt` ; `S/TvLink.kt` (zone `savedFor`/`credentialFor*` `:191-208`) |
| w15-03 | `C/trust/{LinkDriver,TrustRegistry,TrustFiles(écriture),PhoneLink}.kt`, `CT/{LinkDriverTest,TrustTest,HelloCompatTest,LinkFixtures}.kt` |
| w15-04 | `S/{UploadService,TransferQueueService,TransferQueue,BtUploadService}.kt`, `C/tv/TvClient.kt` (`ResumableUpload/Download`), `C/xfer/TransferClient.kt` (`abort`, borne `begin`), nouveaux `C/tv/UploadJobs.kt`, `CT/tv/UploadJobsTest.kt` ; `CT/InFlightTest.kt`, `CT/ReceiverTest.kt` (borne) |

Disjonction S0-a : `ReceiverServer.kt` = w15-01 seul ; `TvLink.kt` = w15-02 seul (zone) ; `LinkDriver.kt` = w15-03 seul ; `UploadService.kt` = w15-04 seul ; `TvClient.kt` = w15-04 seul.

### S0-b
| id | Fichiers possédés |
|---|---|
| w15-05 | `C/tv/TvClient.kt` (`part(name,size)`), `C/tv/ReceiverServer.kt` (zones `partJson`, `uploadCounted`, `findFinalOrOrigin`), `C/tv/Mover.kt`, `C/tv/TvInfo.kt` (`TvDedupe`), `S/MoveToTv.kt`, `S/UploadService.kt` (zone `checkMoved`), `S/TransferQueue.kt` (zone `move`), nouveaux `C/tv/MoveRequests.kt`, `CT/tv/MoveRequestsTest.kt` ; `CT/InFlightTest.kt`, `CT/MultiVolumeTest.kt`, `CT/ReceiverTest.kt` |
| w15-06 | `C/library/agent/TvTrash.kt`, `C/tv/Filing.kt` (`FiledIndex` lecture/`save`), `C/trust/TrustFiles.kt` (lecture), `C/trust/TrustRegistry.kt` (zone `load/restore`), `C/dl/DownloadManager.kt` (zone `atomicWrite/load`), `C/tv/LibraryStore.kt` (zone `LibraryDb.save`), `CT/library/agent/TvAgentTest.kt`, `CT/tv/FilingTest.kt`, `CT/HelloCompatTest.kt`, `CT/dl/DownloadManagerTest.kt` |

### S1
| id | Fichiers possédés |
|---|---|
| w15-07 | `CT/{DeviceTest,ReceiverTest,ParentalTest(ParentalHttpTest),LearnLotsTest}.kt`, `C/tv/ReceiverServer.kt` (zone `start()/boundPort`), `docs/HANDOFF.md` (ligne tests instables) |
| w15-08 | nouveaux `tools/soak/**`, `C/tv/SoakTvMain.kt` ; `android/core/build.gradle.kts` (tâche `soakTv`), `C/tv/ReceiverServer.kt` (nouvelle route `/api/transfer/sessions`), `tools/routes/routes.txt`, `CT/MultipathServerTest.kt` (un test), `docs/test-plans/ENDURANCE.md` (§ 1), `.gitignore` |
| w15-09 | `docs/{CHANGELOG,HANDOFF,REGRESSIONS,STORAGE,TRANSFER}.md`, `tools/tests/test_content_tools.py`, `tools/content-split-repo/check_sizes.py`, `tools/content-media/check_media.py` |

`ReceiverServer.kt` en S1 : w15-07 (zone `start`) et w15-08 (route nouvelle) sont disjoints ; les lancer **en série** (w15-07 puis w15-08) pour respecter R2. `HANDOFF.md` : w15-07 (une ligne) puis w15-09.

### S2
| id | Fichiers possédés |
|---|---|
| w15-10 | `C/xfer/{Scheduler,TransferClient,HttpConn,PartAssembler,TransferHost}.kt`, `C/tv/ReceiverServer.kt` (zones `uploadCounted`, `finish`, `storage/check`, `delete` session), `C/tv/Volumes.kt` (zone `commit`), `S/{TransferQueue,TransferQueueService,UploadService(zones play/credential),TvTransferScreen,TvHub(zone série)}.kt`, `CT/{MultipathTransferTest,MultipathServerTest,TransferQueueTest,TvHardeningTest}.kt` |
| w15-17 | `R/{TvService,TvApp,BtServer,OwnerBtHost,BtGatewayHost,ActivationActivity(zone permissions),PairActivity(zone permissions/onStop),ActivationCenter(zone TvClock),RentalHub(zone TvClock)}.kt`, `R/AndroidManifest.xml`, `C/tv/Background.kt`, nouveau `CT/tv/TvLifecycleTest.kt`, `C/tv/Storage.kt` (pool), `C/tv/ReceiverServer.kt` (zones `init` différé, `maxHttpThreads`), `C/tv/BtProtocol.kt` (zone réception), `C/owner/Keys.kt` (`TvClock`), `C/trust/TrustRegistry.kt` (zone `purge`), `CT/BackgroundTest.kt`, `CT/owner/*Clock*` |
| w15-11 | `C/trust/{LinkDriver,PairFlow,PairingSession,PhoneLink}.kt`, `S/TvLink.kt` (zones hors `PinKeys`), `S/LinkAndroid.kt`, `S/TvPairScreen.kt` (zone Annuler/Reconnecter), `S/BtSshGateway.kt` (zone `dial`/timers), `OD/TvBluetooth.kt`, `CT/{LinkDriverTest,PairFlowTest,TrustTest}.kt`, nouveau `CT/trust/PairingSessionTest.kt` |
| w15-12 | nouveaux `C/net/ResolveQueue.kt`, `CT/net/ResolveQueueTest.kt` ; `S/TvDiscovery.kt`, `C/tunnel/{LinkPool,Mux,TunnelMachine}.kt`, `R/TunnelHub.kt` (zone boucle), `C/connect/ServerLink.kt`, `CT/{BtMuxTunnelTest,BtApiTunnelTest,ConnectTest}.kt`, `CT/tunnel/TunnelMachineTest.kt` |
| w15-13 | nouveaux `C/tv/PlayerPolicy.kt`, `CT/tv/PlayerPolicyTest.kt` ; `C/tv/Progressive.kt`, `C/tv/ReceiverServer.kt` (zones `/stream`, `playIncomplete`/`playurl`), `R/{PlayerActivity,PlayerExtras(hors sous-titres),Thumbnailer,LanguesActivity,LearnHub(zone playVideo),LanguesHub(zone mediaFile)}.kt`, `CT/ProgressiveTest.kt` |
| w15-14 | nouveaux `C/tv/{Companions,SubtitleCharset}.kt`, `CT/tv/{CompanionsTest,SubtitleCharsetTest}.kt` ; `C/tv/PlayerFeatures.kt`, `C/tv/Filing.kt` (zone `place/classify`), `C/tv/ReceiverServer.kt` (zones `startMove`, `rename`, `delete` compagnons), `C/tv/Mover.kt` (liste), `C/library/agent/TvTrash.kt` (`put/restore`), `R/PlayerExtras.kt` (sous-titres), `R/PlayerActivity.kt` (zone options libVLC), `S/player/Media.kt` (zone sous-titres), `CT/{PlayerFeaturesTest,MultiVolumeTest}.kt`, `CT/tv/FilingServerTest.kt`, `CT/library/agent/TvAgentTest.kt` |
| w15-15 | nouveaux `C/net/MediaToken.kt`, `CT/net/MediaTokenTest.kt` ; `C/upnp/Soap.kt`, `CT/upnp/*`, `S/{MediaServer,ServerService,Upnp,TvPlayerSettings(zone lecture),DlnaHandoff}.kt`, `S/player/{CastSession,PlaybackService,PlayerActivity(zone URI),PhoneLibrary(zone cache)}.kt`, `android/sender/src/main/res/values*/strings.xml`, `CT/{PhonePlayerTest,CopyProgressTest}.kt` |
| w15-16 | `C/tv/{Volumes(FileStore cache, refresh),Folders,UsbImport}.kt`, `C/tv/ReceiverServer.kt` (zones `libraryItems`, `delete`, `organize*`, `/api/folders`), `C/dl/{DownloadManager(hors atomicWrite),Aria2Supervisor}.kt`, `C/tv/LibraryStore.kt` (zone `markFailed`), `C/library/agent/TvTrash.kt` (zone `restore`/quota), `C/lots/DeliveryQueue.kt`, `C/parental/ParentalSync.kt` (zone ack), `S/{LotsRuntime(zone hasWork),ParentalInbox,DownloadService}.kt`, `R/TvDownloads.kt`, `R/TvService.kt` (zones `/api/folders`, rescan), tests `CT/{MultiVolumeTest,VolumesTest,LibraryStoreTest,ParentalReportsTest}.kt`, `CT/library/agent/TvFoldersTest.kt`, `CT/tv/FilingServerTest.kt`, `CT/dl/*`, `CT/lots/LotsDeliveryTest.kt` |

**Partages à ordonner en S2** (zones nommées distinctes, mais R2 impose la série sur le même fichier) : `C/tv/ReceiverServer.kt` : w15-10 → w15-17 → w15-13 → w15-14 → w15-16 (jamais deux en même temps) ; `R/TvService.kt` : w15-17 puis w15-16 ; `R/PlayerActivity.kt`/`R/PlayerExtras.kt` : w15-13 puis w15-14 ; `C/tv/Mover.kt`, `TvTrash.kt`, `MultiVolumeTest.kt`, `FilingServerTest.kt` : w15-14 puis w15-16 ; `C/trust/TrustRegistry.kt` : w15-17 (zone `purge`) après w15-11 ; `S/UploadService.kt`, `S/TransferQueue*.kt` : w15-10 seul en S2 ; `C/tv/TvClient.kt` : personne en S2 (W13 w13-04 l'a eu entre S0 et S2).

## Graphe de dépendances

```
S0-a : w15-01 ─┐        w15-03 ─┐        (puis)  w15-02 ─┐   w15-04 ─┐
               ├─ fusion a ─────┴──────────────────────────┴──────────┤
S0-b : w15-05 (après 01, 04)  ∥  w15-06 (après 03)                     │
S1   : w15-07 (après 04) → w15-08 (après 01) ∥ w15-09 (après S0)       │
W13-S1 rebasée (w13-04 après w15-04 ; w13-08 après w15-02 ; w13-07/09 après w15-04/02)
S2-a : w15-10 (après S0 + w13-04) ∥ w15-17 (après 01) ; puis w15-11 (après 03) ∥ w15-12 (après 03)
S2-c : w15-13 ∥ w15-14 (après 05, 06) ∥ w15-15 ; w15-16 (après 06, 14)
S3   : w15-18 (après 08 et S2) ─► critères de sortie ─► fin du gel ─► W7
```

## Ordre fusionné W13 · W14 · W15 (ce que chaque vague possède)

| Étape | Vague et tranche | Ce qu'elle possède (zones) | Pourquoi à ce rang |
|---|---|---|---|
| 1 | **W14-S1** (w14-01…06, w14-14 : harnais J, 20 parcours, fonctions pures `HomeLinkView`/`XferTexts`/`PinKeys`-consommateur, lint de pureté, `REGRESSIONS.md`) **en parallèle de W15-S0-a** | `CT/journey/**`, `C/trust/HomeLinkView.kt`, `C/xfer/XferTexts.kt`, `C/update/UpdateRules.kt`, `CT/lint/**`, `docs/REGRESSIONS.md` ; **w14-05 ne crée pas `PinKeys`** : il consomme celui de w15-02 (contrat § « Changements ») | zones disjointes de W15-S0 ; la porte doit exister avant S2 |
| 2 | **W15-S0-a** puis **S0-b** | voir matrice | les 8 P0 ; priorité absolue du propriétaire |
| 3 | **W13-S1** rebasée (w13-01/02/03/05/10 inchangés ; w13-04, 07, 08, 09 **après** w15-04/02) | `C/link/**`, `C/tv/TvClient.kt`/`C/xfer/{TransferClient,Lanes,Lane,Scheduler}.kt` (w13-04, après w15-04), `C/tv/{ReceiverServer,Security}.kt` zone codes/PIN (w13-05, après w15-01 et w15-05), `S/**` services et écrans (w13-07/08/09, après w15-02/04), `S/PinStore.kt` (w13-08 : `putAll` réutilise `PinKeys.keysOf`) | même objectif (présentation des blocages) sur les mêmes fichiers : les causes d'abord, les textes ensuite |
| 4 | **W15-S1** | tests, `tools/soak/`, docs | observabilité minimale avant d'attaquer S2 |
| 5 | **W14-S2** (w14-07…11, 13 : fumée `tools/smoke/`, fausse TV, marqueurs `CB_JOURNEY`, migration) | `tools/smoke/**`, `C/tv/FakeTvMain.kt`, marqueurs logcat dans `S/**`/`R/**` (zones « journal » seulement) | la fumée protège S2 ; `FakeTvMain` sert aussi à `tools/soak` (w15-08 a son repli `SoakTvMain` si w14-10 est en retard : **les fusionner** en un seul exécutable à ce rang, w14-10 absorbe `SoakTvMain`) |
| 6 | **W15-S2** | voir matrice | durcissement, sous porte J + F |
| 7 | **W13-S2** (w13-06 écran TV, w13-11 docs, w13-12 CI) et **W14-S3** (w14-12 candidate/retour arrière, w14-15 CI + `gate.sh`) | `R/{HomeScreen,TvService,TvPrefs,PlayerActivity}.kt` zones nommées (après w15-17 et w15-13), docs, CI | écrans TV après le cycle de vie stabilisé ; CI en dernier |
| 8 | **W15-S3** (w15-18) | rapports | trois nuits ⇒ critères de sortie |
| 9 | **Sortie de stabilisation** (plan § 5) ⇒ fin du gel ⇒ **W7** (rebasée : `PinKeys`, garde-vivant tunnel, `TransferHost.progress` = base de `TransferLedger` w7-10, `ReceiveCard` W14), puis w6-17, W10/W11/W12 côté apps | — | — |

Ce que **W15 ne fait pas** : aucun `Blocker`, texte ou code support (W13) ; aucun harnais J, fumée, lint, candidate, `REGRESSIONS.md` initial (W14 ; w15-09 **ajoute** des lignes) ; aucun `LinkManager`, `CBSX`/`CBSY`, `TvBeacon`, fenêtre v2 (W7) ; aucune fonctionnalité.

## Changements aux cahiers antérieurs (sans les éditer ; l'exécutant d'un cahier encore à lancer lit ceci d'abord)

| Cahier | Changement | Repris par |
|---|---|---|
| **w14-05** (fonctions pures) | `C/trust/PinKeys.kt` est **créé par w15-02** avec la signature `keysOf(tv, apiPort, tunnelPort)` / `resolve(key, saved, default, …)` ; w14-05 ne le crée pas, il l'importe ; `XferTexts.notification(state)` doit accepter `State.Failed(reason, sourceLost, resumable)` (champs additifs de w15-04) | w15-02, w15-04 |
| **w14-01/03** (harnais, parcours transfert) | `TvSim.transferState(id)` peut lire `TransferHost.progress()` (w15-01) pour asserter la progression côté TV pendant la copie (J-12) ; `TvSim.restart()` doit rejouer `sweep` ; P-16 utilise la reprise après éviction (404 ⇒ nouveau `begin`) | w15-01 |
| **w14-10** (`FakeTvMain`) | absorbe `C/tv/SoakTvMain.kt` et ses scénarios `slow|busy|pin-rotated|pause` si w15-08 l'a livré avant ; un seul exécutable | w15-08 |
| **w14-11** (`CB_JOURNEY`) | ajouter les marqueurs `xfer.sessions`, `xfer.evicted`, `link.token.reused` (w15-01, w15-03) ; `tools/soak` les lit s'ils existent | w15-01, w15-03, w15-08 |
| **w14-14** (`REGRESSIONS.md`) | si w15-09 passe avant : le fichier existe déjà avec R-01…R-07 ; w14-14 n'écrase pas, il complète la règle | w15-09 |
| **w13-04** (couche transfert ⇒ `BlockerMap`) | se rebase sur w15-04 : `giveUpAfter` borné, `catch (SecurityException)`, `State.Failed` additif, `abort()` à l'annulation ; conserve ces bornes ; `TRIAL_CLOSED`/`PIN_*` restent `Failed` immédiats | w15-04 |
| **w13-05** (TV : codes d'erreur) | se rebase sur w15-01 (`begin` : éviction, texte français) et w15-05 (`409 {"code":"NAME_TAKEN"}` déjà émis) ; D-W13-2 (PIN absent non compté) reste à w13-05 (T-12 n'est **pas** fait par W15) | w15-01, w15-05 |
| **w13-07** (services branchés) | se rebase sur w15-04 (`UploadJobs`, génération, `onTimeout`, refus du 2ᵉ `start`) ; `QUEUE_NO_LINK` ⇒ repli PIN utilise `PinKeys.resolve` | w15-04, w15-02 |
| **w13-08** (`PinPrompt`, `PinStore.putAll`) | `putAll(tv, code)` = `PinKeys.keysOf(tv).forEach { put }` ; l'effacement sur `PIN_WRONG` reste à w13-08 | w15-02 |
| **w13-09** (puce à trois vérités) | `HealthProbe` remplace les sondes empilées (L-22) : w15-11 **ne touche pas** `TvHome.kt:65-77`/`TvScreen.kt:88-91` si w13-09 est fusionné avant | w15-11 |
| **w13-06** (écran TV « un téléphone attend le code ») | après w15-17 (permissions, `TvService`) et w15-01 (`HomeScreen.refreshStatus` sur `io`) ; conserve la carte de réception de w15-01 | w15-01, w15-17 |
| **w7-10** (`TransferLedger`) | part de `TransferHost.progress()` + `/api/transfer/sessions` (w15-01, w15-08) au lieu de repartir de zéro | w15-01, w15-08 |
| **w7-12/14/16** (`TvBeacon`, `TvPermissions`, `LinkRuntime`) | `PermissionPolicy` (w15-17) est la base de `TvPermissions` ; le garde-vivant tunnel (w15-03) et `PinKeys` (w15-02) sont acquis ; le récepteur `ACTION_STATE_CHANGED` (w15-17) est à conserver | w15-02, w15-03, w15-17 |
| **w7-21** (`XferCard`, file persistée) | `MoveRequests` (w15-05) et `UploadJobs` (w15-04) sont la base de la file persistée ; `BluetoothLane` reste non branchée (W8 abandonnée) | w15-04, w15-05 |
| **w2-07** (découpage de `TvService`), **w3-01** (`RouteTable`) | **attendent la sortie** : refactors de fichiers chauds interdits pendant le gel | — |
| **w1-09** (tests instables) | couvert par w15-07 (mêmes tests, méthode port 0) : si w1-09 est déjà fusionné, w15-07 ne refait que `ReceiverTest` et `LearnLotsTest` | w15-07 |

## Ordre de lancement conseillé

1. **Jour 0** : attendre le rapport `diag-receiver-progress` (Opus) ; lancer **w15-01** et **w15-03** (sonnet) ; en parallèle W14-S1 (autre coordinateur : zones disjointes). Propriétaire : D-W15-2 (éviter « Déplacer » et les gros déplacements), D-W15-8 (jetons), fournir le logcat si R-01 se reproduit.
2. **Jour 2** : audits Opus de w15-01 et w15-03 ; fusion a1 ; lancer **w15-02** et **w15-04** ; audits ; fusion a2 (`:core:test` complet, `compileDebugKotlin` × 2). **Sur le téléphone du propriétaire + TV de référence** (D-W15-3, moment 1) : liste humaine points 3, 4, 6, 7, 9 ; APK TV **verrouillée** dans le `Download` de la clé (règle du propriétaire).
3. **Jour 4** : **w15-05** (audit) ∥ **w15-06** ; fusion S0 ; HANDOFF § 0.
4. **Jour 6** : **w15-07** → **w15-08** ∥ **w15-09** (haiku) ; W13-S1 rebasée démarre (w13-01/02/03/05/10 pouvaient déjà tourner dès le jour 0 : zones disjointes).
5. **Jour 8** : W14-S2 (fumée) ; puis **w15-10** ∥ **w15-17** (audits), **w15-11** ∥ **w15-12**, **w15-13/14/15/16** ; chaque fusion risquée suivie d'un F complet.
6. **Jour 15** : W13-S2, W14-S3 ; liste humaine 12/12 (moment 2).
7. **Jour 16-19** : **w15-18** trois nuits (la 3ᵉ par le propriétaire) ; verdict de sortie ; fin du gel ; HANDOFF § 0 et § 9 dans le même commit.

## Décisions prises par l'architecte (renversables ; détail plan § 7)
D-W15-01a éviction de la plus ancienne session non `finishing` au 4ᵉ `begin` (plutôt que 429) ; D-W15-05a 409 `NAME_TAKEN` pour un homonyme de taille différente (plutôt qu'un renommage silencieux) ; W15-S0 **avant** W13-S1 (les causes avant les textes) ; W14-S1 en parallèle de W15-S0-a (zones disjointes) ; `PinKeys` créé par W15 et consommé par W13/W14 ; `SoakTvMain` absorbé par `FakeTvMain` quand w14-10 arrive ; aucun refactor de fichier chaud (w2-07, w3-01) pendant le gel.

## Questions au propriétaire (plan § 7)
D-W15-1 durée du gel (**jusqu'aux critères de sortie, ≈ 3 semaines**) · D-W15-2 éviter « Déplacer » jusqu'à w15-05 (**oui**) · D-W15-3 trois moments de test sur vrais appareils (**oui**) · D-W15-4 `UsbMigration` : retirer de la doc (**oui**) · D-W15-5 serveur média : jeton (**oui**) · D-W15-6 émulateur arm64 pour l'endurance (**oui**) · D-W15-7 téléphone réel en lecture + `install -r` (**oui**) · D-W15-8 jetons TV non persistés (**oui**) · D-W15-9 logcat de R-01 à fournir.
