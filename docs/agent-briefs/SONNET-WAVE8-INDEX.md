> **DÉCISION DU PROPRIÉTAIRE (2026-10-02) : le multivoie reste tel quel ; la voie USB est exclue. La vague W8 n'est PAS lancée** (conception conservée comme référence, rien à exécuter). Le moteur de transfert actuel (core/xfer, LAN + Bluetooth) ne change pas. Rouvrir seulement sur demande explicite du propriétaire.

# Vague 8 pour agents Sonnet/Haiku — index (2026-10-02) : transport multivoie bas niveau (Bluetooth + Wi-Fi + USB)

Source : `docs/coordination/DESIGN-W8-TRANSPORT-MULTIVOIE.md` (lire en entier avant tout cahier). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »). **Ce fichier ne modifie pas les index des vagues 1-7.**

**Demande du propriétaire qui fonde la vague (2026-10-02)** : « conçois un dispositif de transmission des données bas niveau, pour un débit maximal exploitant toutes les technos dispo, comme le Bluetooth, Wi-Fi, USB lorsqu'ils sont tous disposés ».

**Règle d'honnêteté de la vague** : le débit de pointe est borné par le disque de la TV et sa puce Wi-Fi ; les radios ne s'additionnent pas (même antenne). La vague livre : démarrage instantané (Bluetooth), accélération progressive (Wi-Fi LAN, Wi-Fi Direct **alternatif**, lien USB si la pointe réussit), continuité quand une voie tombe, garde « jamais plus lent que la meilleure voie seule », chiffrement de session, diagnostic par voie, banc à fausses voies. Aucun cahier ne promet un chiffre : le banc sur la vraie TV les donne.

**Modèle d'exécution recommandé par cahier** : `haiku` = mécanique, scripts, docs, campagne, CI ; `sonnet` = tout le reste. Efforts en agent·jours (S ≈ 0,5-1, M ≈ 2, L ≈ 3-4).

**Prérequis fusionnés avant la vague 8** : la branche `claude/multipath-transfer` (déjà sur `integration/agents` : `core/xfer/**`, `/api/transfer/*`), `claude/bt-tunnel-keepalive` (`core/tunnel/{Mux,LinkPool,TunnelGateway}.kt`, `BtConnectLock`), w1-06 (`routes.txt`), w6-17 (`SendGuard`) **souhaité** (sinon w8-07 crée un `Verdict` local marqué « à remplacer par w6-17 ») ; **W7** (`DESIGN-W7-PLUG-AND-PLAY-SYNC.md`) **absent au moment de l'index** : w8-07 et w8-14 codent contre le contrat § 7.1 de la conception et s'alignent sur W7 s'il existe à l'exécution.

## Quatre sous-vagues séquentielles ; fichiers disjoints à l'intérieur d'une sous-vague

| Sous-vague | Objet | Cahiers | Effort |
|---|---|---|---|
| **8a** | Cœur (JVM, hors ligne, testé) : voies dynamiques, santé/stats, ordre et unité adaptative, trames CBX + CRC32C, clés de session + enregistrements AEAD, flux brut (TV + téléphone), façade `BulkTransfer` + garde, hôte v2, banc à fausses voies | w8-01 … w8-09 | ≈ 16 j |
| **8b** | TV : routes v2 + corps chiffré, service RFCOMM de vrac, pointes USB (tethering, AOA), découpage FAT32 (option) | w8-10 … w8-13 | ≈ 7 j (+3) |
| **8c** | Téléphone : `UploadService` → `BulkTransfer` + « instantané puis accéléré », lots et déplacement par le vrac, diagnostic « Voies du transfert », Wi-Fi Direct + coexistence | w8-14 … w8-17 | ≈ 9 j |
| **8d** | Banc (`--lanes`, script TV réelle), docs, campagne + CI, manifeste sur clé USB (option) | w8-18 … w8-21 | ≈ 3 j (+2) |

## Les 21 cahiers

| id | Cahier | Objet | Effort | Modèle | Statut | Dépend de |
|---|---|---|---|---|---|---|
| w8-01 | `sonnet-w8-01-laneset-dynamic.md` | `LaneSet` (ajout/retrait à chaud, drain, requeue devant), `Lane.laneId/kind/unitBytes/idle`, `Scheduler` sur `LaneSet`, pause/reprise | L | sonnet | PRÊT | — |
| w8-02 | `sonnet-w8-02-lane-health-guard.md` | `LaneHealth` (EWMA débit/RTT, score, veille/réadmission), garde « jamais plus lent que la meilleure voie seule », règles LAN∥Direct et veille Bluetooth, plafond K par `writeBps`, seau à jetons | M | sonnet | PRÊT | w8-01 (interface `Lane` ; sinon coder contre le contrat) |
| w8-03 | `sonnet-w8-03-order-policy-progressive.md` | `OrderPolicy` (vrac / progressif : tête = lecture + avance, `moov` et 2 Mio prioritaires), `contiguous()`, `SparseGrowingStream` | M | sonnet | PRÊT | w8-01 |
| w8-04 | `sonnet-w8-04-cbx-frame-crc32c.md` | `CbxFrame` (codec, 24 o, types, validation stricte), `Crc32c` pur Kotlin, vecteurs figés | M | sonnet | PRÊT | — |
| w8-05 | `sonnet-w8-05-session-keys-aead.md` | `SessionKeys` (HKDF-SHA256, nonces par voie, fenêtre anti-rejeu), `AeadRecords` (64 Kio, AES-GCM / ChaCha20-Poly1305, micro-banc), `xfer-vectors.json` | L | sonnet | PRÊT | — |
| w8-06 | `sonnet-w8-06-fake-lanes-property-tests.md` | `FakeLane` (débit, latence, gigue, pertes, coupures), `FakeClock`, tests de propriété (corruption, convergence 85 %, reprise, interblocage, mémoire, garde) | M | sonnet | PRÊT | w8-01, w8-02 |
| w8-07 | `sonnet-w8-07-bulktransfer-facade-guard.md` | `BulkTransfer` (façade, `Handle`, `Result.Refused`), paramètre `SendGuard.Verdict.Allowed`, 403 `trial`/`ERR_TRIAL` → `Refused`, `LinkSnapshot`/`LinkSet` (contrat W7), `LaneStats` JSON | M | sonnet | PRÊT | w8-01, w8-02 ; w6-17 souhaité |
| w8-08 | `sonnet-w8-08-transferhost-v2.md` | `TransferHost` v2 : sessions (`session`, `lanes`), part équitable des flux, stats par voie, `fsync` cadencé avant `.state`, test coupure de courant simulée | M | sonnet | PRÊT | w8-05 (clés), w8-03 (`contiguous`) |
| w8-09 | `sonnet-w8-09-bulkstream-bt-lane.md` | `BulkStream` (serveur TV sur `Link` : HELLO/CHUNK/ACK/STATE/PING, chien de garde), `BulkBtLane` (téléphone), `BULK_SERVICE_UUID`, tests par tubes mémoire | L | sonnet | PRÊT | w8-04, w8-05, w8-08 |
| w8-10 | `sonnet-w8-10-tv-routes-v2-encrypted.md` | TV : `caps` v2, `POST /api/transfer/{session,lanes}`, corps chiffré `X-CB-Enc: cbx1`, `state.lanes`, `SO_RCVBUF`, `routes.txt` | M | sonnet | PRÊT | 8a |
| w8-11 | `sonnet-w8-11-tv-bt-bulk-service.md` | TV : `BtBulkBridge` (cinquième service RFCOMM, règle d'essai, confiance, verrou), ligne d'état « Réception : … », `GET /api/bluetooth` enrichi | M | sonnet | PRÊT | w8-09 |
| w8-12 | `sonnet-w8-12-usb-spikes.md` | Pointes USB sur la TV de référence : tethering (lien IP sur `usb0`, `/api/net`, `caps.usbIp`) et AOA hôte (faisabilité mesurée, rapport chiffré) ; aucun code produit sans mesure | M | sonnet | **BLOQUÉ partiel** (D-W8-1) | w8-10 (`caps`) |
| w8-13 | `sonnet-w8-13-fat32-split.md` | Option D-W8-5 : `SplitFile` (lecteur concaténé), `begin` 413 `split`, bibliothèque/suppression, `/stream` | L | sonnet | OPTION | w8-10 |
| w8-14 | `sonnet-w8-14-phone-upload-bulk.md` | Téléphone : `UploadService.runFast` → `BulkTransfer` + `LaneBringup` (BT instantané, LAN, Direct, usb0 via `Network.bindSocket`), thermique/batterie, file sans choix Wi-Fi/Bluetooth | L | sonnet | PRÊT | 8a, w8-10, w8-11 |
| w8-15 | `sonnet-w8-15-phone-lots-move-bulk.md` | `BulkLotTransport` (lots par le moteur puis `/api/lots/install`), `MoveToTv` par le vrac, repli actuel | M | sonnet | PRÊT | w8-07, w8-14 (contrat) |
| w8-16 | `sonnet-w8-16-phone-lane-diagnostics.md` | Écran « Voies du transfert », journal `CbxXfer`, réglages (rapide / chiffrement / plafond), rapport copiable sans secret | M | sonnet | PRÊT | w8-07, w8-14 (contrat) |
| w8-17 | `sonnet-w8-17-phone-wifi-direct-coexistence.md` | Wi-Fi Direct comme voie alternative (jonction réutilisée, jamais avec le LAN), veille Bluetooth, 3 essais matériels documentés avant activation | M | sonnet | PRÊT (activation conditionnelle) | w8-14 |
| w8-18 | `sonnet-w8-18-bench-lanes-script.md` | `TransferBench --lanes`, script TV réelle (1 Mo / 100 Mo / 2 Go, scénarios, seuils, tableau), `run.sh --gen` | S | haiku | PRÊT | w8-06 |
| w8-19 | `sonnet-w8-19-docs-w8.md` | `docs/TRANSFER.md` v2, nouveau `docs/TRANSPORT-CBX.md` (trames, crypto), `docs/BT-PLUG-AND-PLAY.md` § service de vrac, `docs/HANDOFF.md` | S | haiku | PRÊT | 8a-8c |
| w8-20 | `sonnet-w8-20-test-campaign-ci-w8.md` | `docs/TEST-CAMPAIGN.md` § W8, CI : tests `core.xfer`, vecteurs, `routes.txt` | S | haiku | PRÊT | w8-10, w8-18 |
| w8-21 | `sonnet-w8-21-usb-drive-manifest.md` | Option : manifeste `.cbx` écrit sur la clé USB par le téléphone, import TV qui reprend par carte de blocs (hybride clé + réseau) | M | sonnet | OPTION | w8-08 |

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`.

### 8a
| id | Fichiers possédés |
|---|---|
| w8-01 | `C/xfer/{Lane,Scheduler}.kt`, nouveau `C/xfer/LaneSet.kt`, `CT/xfer/{LaneSetTest,SchedulerDynamicTest}.kt` (nouveau dossier `CT/xfer/`) |
| w8-02 | nouveaux `C/xfer/{LaneHealth,Guard,RateLimiter}.kt`, `CT/xfer/{LaneHealthTest,GuardTest}.kt` ; `C/xfer/Lanes.kt` (`KController.limitRate`) |
| w8-03 | nouveaux `C/xfer/{OrderPolicy,SparseGrowingStream}.kt`, `CT/xfer/{OrderPolicyTest,SparseStreamTest}.kt` ; `C/tv/Progressive.kt` (fonction `moovRange`) |
| w8-04 | nouveaux `C/xfer/{CbxFrame,Crc32c}.kt`, `CT/xfer/{CbxFrameTest,Crc32cTest}.kt` |
| w8-05 | nouveaux `C/xfer/{SessionKeys,AeadRecords,CipherPick}.kt`, `CT/xfer/{SessionKeysTest,AeadRecordsTest,XferVectorsTest}.kt`, `tools/activation/xfer-vectors.json` |
| w8-06 | nouveaux `CT/xfer/{FakeLane,FakeClock,PropertyTest,ResumePropertyTest}.kt` (tests seulement) |
| w8-07 | nouveaux `C/xfer/{BulkTransfer,LinkSnapshot,LaneStats}.kt`, `CT/xfer/{BulkTransferTest,LaneStatsTest}.kt` ; `C/xfer/TransferClient.kt` |
| w8-08 | `C/xfer/{TransferHost,PartAssembler}.kt`, `CT/xfer/{TransferHostV2Test,PowerCutTest}.kt` ; `CT/MultipathTransferTest.kt` (adaptation seulement) |
| w8-09 | nouveaux `C/xfer/{BulkStream,BulkBtLane}.kt`, `CT/xfer/BulkStreamTest.kt` ; `C/tv/BtProtocol.kt` (constante UUID + `ERR_*` réutilisés) |

Attention 8a : `C/xfer/Blocks.kt` (`Manifest.idBytes`) est à **w8-04** ; `C/xfer/HttpConn.kt` (`setTrafficClass`) à **w8-02** ; `C/xfer/TransferBench.kt` à **w8-18** (8d). `CT/MultipathServerTest.kt` n'est touché par personne en 8a (il doit rester vert : routes v1 intactes).

### 8b
| id | Fichiers possédés |
|---|---|
| w8-10 | `C/tv/ReceiverServer.kt` (§ transfert seulement), `CT/MultipathServerTest.kt`, nouveau `CT/xfer/ServerV2Test.kt`, `tools/routes/routes.txt`, `tools/tests/test_routes.py` |
| w8-11 | `R/BtServer.kt`, nouveau `R/BtBulkBridge.kt`, `R/TvService.kt` (crochet de 3 lignes), `docs/ADMIN.md` (§ Bluetooth) |
| w8-12 | nouveaux `R/usb/{UsbLinkWatcher,AoaSpike}.kt` (pointe, derrière un drapeau de build `USB_SPIKE`), `android/receiver/src/main/res/xml/usb_device_filter.xml`, `docs/agent-reports/sonnet-w8-12.md` (rapport chiffré) ; `C/net/NetState.kt` (`LinkKind.USB`) |
| w8-13 | nouveaux `C/tv/SplitFile.kt`, `CT/SplitFileTest.kt` ; `C/tv/{Storage,Library,LibraryStore}.kt` (option, après 8b) |

### 8c
| id | Fichiers possédés |
|---|---|
| w8-14 | `S/{UploadService,TransferQueue,TransferQueueService,BtUploadService,FastTransfer}.kt`, nouveau `S/xfer/{LaneBringup,AndroidLinkSet,PowerGuards}.kt` |
| w8-15 | `C/lots/LotPush.kt` (nouvelle classe `BulkLotTransport`, additive), `CT/lots/LotsDeliveryTest.kt`, `S/{LotsRuntime,MoveToTv}.kt` |
| w8-16 | nouveaux `S/xfer/{LaneDiagnosticsScreen,XferLog}.kt`, `S/{TvTransferScreen,TvScreen}.kt` |
| w8-17 | `S/{WifiDirectScreen,ConnectScreens}.kt`, nouveau `S/xfer/DirectJoin.kt`, `R/WifiDirectGroup.kt` (délai de groupe) |

### 8d
| id | Fichiers possédés |
|---|---|
| w8-18 | `C/xfer/TransferBench.kt`, `tools/transfer-bench/{run.sh,README.md,scenarios.md}` |
| w8-19 | `docs/{TRANSFER,BT-PLUG-AND-PLAY,HANDOFF}.md`, nouveau `docs/TRANSPORT-CBX.md` |
| w8-20 | `docs/TEST-CAMPAIGN.md` (§ W8), `.github/workflows/android.yml`, `docs/COORDINATION.md` (§ CI) |
| w8-21 | nouveaux `C/tv/UsbManifest.kt`, `CT/UsbManifestTest.kt` ; `C/tv/UsbImport.kt`, `R/UsbImporter.kt`, `S/StoragePanel.kt` |

Vérification de disjonction : aucun chemin n'apparaît deux fois dans une même sous-vague (`C/xfer/Lanes.kt` : w8-02 seul en 8a ; `C/tv/BtProtocol.kt` : w8-09 seul ; `R/TvService.kt` : w8-11 seul en 8b ; `S/TvScreen.kt` : w8-16 seul en 8c). Chevauchements entre sous-vagues résolus par l'ordre 8a → 8b → 8c → 8d et une fusion entre chaque.

## Graphe de dépendances

```
Prérequis : multipath-transfer, bt-tunnel-keepalive, w1-06 ──► 8a ;  w6-17 souhaité ──► w8-07 ;  W7 (absent) ──► contrat § 7.1 dans w8-07/w8-14

8a : w8-01 ─► w8-02 ─► w8-06        w8-01 ─► w8-03 ─► w8-08 ◄─ w8-05        w8-04 ─┐
     w8-01, w8-02 ─► w8-07                                       w8-04, w8-05, w8-08 ─► w8-09
8b : 8a ─► w8-10 ∥ w8-11 (w8-09) ∥ w8-12 (w8-10, BLOQUÉ partiel D-W8-1) ;  w8-13 option après w8-10
8c : w8-14 (8a, w8-10, w8-11) ∥ w8-15 (w8-07 ; contrat w8-14) ∥ w8-16 (w8-07 ; contrat w8-14) ∥ w8-17 (contrat w8-14 ; activation après 3 essais matériels)
8d : w8-18 (w8-06) ∥ w8-19 (8a-8c) ∥ w8-20 (w8-10, w8-18) ;  w8-21 option (w8-08)
```

## Ordre de lancement conseillé

1. **Jour 1** : w8-01, w8-04, w8-05 en parallèle ; dès le rapport de w8-01 : w8-02, w8-03 ; dès w8-02 : w8-06, w8-07 ; dès w8-03 + w8-05 : w8-08 ; dès w8-04 + w8-05 + w8-08 : w8-09. **Fusion 8a** : `cd android && gradle --offline :core:test` complet vert (dont `MultipathServerTest` inchangé), vecteurs `xfer-vectors.json` figés, tests de propriété < 60 s.
2. **Jour 8** : w8-10, w8-11, w8-12 en parallèle. **Fusion 8b** : `:receiver:compileDebugKotlin`, `test_routes.py`, installation sur la **TV de référence (32 bits)** ; copie de l'APK dans le `Download` de la clé USB (règle du propriétaire) ; **lancer le banc** `tools/transfer-bench/run.sh --tv …` (version 8a du banc) : premiers chiffres réels avant 8c.
3. **Jour 12** : w8-14, w8-15, w8-16, w8-17 en parallèle. **Fusion 8c** : `:sender:compileDebugKotlin`, campagne S1/S3/S4 sur la TV de référence ; Wi-Fi Direct reste **désactivé** tant que w8-17 n'a pas rapporté 3 transferts de 100 Mo réussis.
4. **Jour 17** : w8-18, w8-19, w8-20 ; w8-13 et w8-21 seulement sur décision (D-W8-5 ; intérêt du manifeste sur clé).
5. Après la vague : X25519 éphémère (confidentialité persistante) dès que W4 livre la courbe ; AOA en produit si la pointe w8-12 est concluante et D-W8-1 favorable.

## Décisions prises par l'architecte (renversables ; détail § 15 de la conception)
D-W8-2 chiffré quand c'est gratuit, dégradé visible sinon, jamais avec le PIN seul ; D-W8-3 pas de réécriture faststart ; D-W8-4 service RFCOMM dédié au vrac ; D-W8-6 plafond de débit réglable, éteint par défaut ; D-W8-7 pas de voie Bluetooth par le tunnel API sur une TV v1.

## Questions au propriétaire (les seules qui bloquent)

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W8-1** | Ports USB des TV cibles (nombre, type A/C, un port libre une fois la clé branchée ?) ; acceptez-vous que l'utilisateur branche son téléphone à la TV ? | la partie AOA de w8-12 (le tethering se teste sans décision) | faire la pointe sur la TV de référence ; si un seul port, abandonner AOA |
| **D-W8-5** | Découper les fichiers > 4 Gio sur FAT32 (w8-13, ≈ 3 j) ou conseiller exFAT ? | w8-13 | conseiller exFAT d'abord ; w8-13 si des retours clients le demandent |

## Routage des modèles (Fable, 2026-10-02) — vague 8 (cahiers présents à cette date : w8-01 … w8-09)

Source : `docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md` (grille § 1, règle § 7 pour les cahiers à venir) ; table machine `docs/agent-briefs/routing.json` ; `python3 tools/agents/dispatch-plan.py --wave W8a --done <ids>`. Modèle **explicite** à chaque lancement ; un seul build JVM (`tools/agents/gradle-lock.sh`) ; au plus 3 agents en parallèle.

- **sonnet** (9) : w8-01, w8-02, w8-03, w8-04, w8-05, w8-06, w8-07, w8-08, w8-09 (la colonne « Modèle » de cet index est confirmée).
- **haiku** (0) : aucun parmi les cahiers présents ; l'index annonce w8-18, w8-19, w8-20 en haiku : à confirmer à l'écriture des cahiers (forme mécanique obligatoire, § 4.7).
- **audit Opus avant fusion** : w8-05, w8-09 (crypto de transport ; protocole de vrac) — session A-W8a.
- **w8-10 … w8-21** : cahiers non encore écrits au 2026-10-02 ; leur auteur applique la règle § 7 (critères, modèle, entrée `routing.json`, en-tête de 3 lignes) avant tout lancement.
