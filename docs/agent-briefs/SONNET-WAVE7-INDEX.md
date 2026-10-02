# Vague 7 pour agents Sonnet/Haiku — index (2026-10-02) : plug and play et synchronisation native CastBridge ↔ CastBridge-TV

Source : `docs/coordination/DESIGN-W7-PLUG-AND-PLAY-SYNC.md` (lire en entier avant tout cahier). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »). Ce fichier ne modifie pas les index des vagues 1-6 ; le § « Changements aux cahiers antérieurs » liste ce que la vague 7 amende.

**Demande du propriétaire qui fonde la vague (2026-10-02)** : « optimise le plug and play entre l'app phone et l'app TV pour une synchro native » : les deux applications se trouvent, s'associent, restent liées et synchronisent tout (activation/preuve, lots, bibliothèque, transferts, rapports parentaux, boutique/jetons, télécommande, passerelle d'assistance) sans code, sans IP, sans étape manuelle, sur TV modestes et téléphones Android 8-14, dans les conditions du Cameroun.

**Modèle d'exécution par cahier** : `haiku` = docs, scripts, CI, campagne, miroir Python ; `sonnet` = tout le reste. Efforts en agent·jours (S ≈ 1, M ≈ 2-2,5, L ≈ 3-3,5).

**Prérequis** : vagues 1-3 fusionnées (en particulier w1-02 `SafeFile`, w1-05 `TvClock`, w1-06 `routes.txt`, w1-11/w1-07 CI) ; **souhaités** : w4-01 (`X25519.kt`, `InstallKey`) et w4-03 (`KeystoreWrapper`) — sinon repli « clé en fichier, protection logicielle » dans w7-03/w7-12 ; w6-02/w6-03/w6-16 (`PhoneSync`, `TvProof`, `ProofSync`) — sinon `ActState` minimal dans w7-18 et preuve transportée comme texte opaque (w7-04). **Aucun** cahier W4/W5/W6 n'est fusionné à ce jour (vérifié : aucun fichier, aucun rapport) : la vague 7 est conçue pour tourner **avant ou après** eux.

## Quatre sous-vagues séquentielles ; fichiers disjoints à l'intérieur d'une sous-vague

| Sous-vague | Objet | Cahiers | Effort |
|---|---|---|---|
| **7a** | Cœur (JVM, testé, aucun Android) : `LinkManager`, trames `CBSY`, canal `CBSX`, moteur et 8 domaines, identité/registre v2/ré-adoption/fenêtre, résultat d'activation v2 + `keyring`, journal/auto-test/textes, découverte et routes, grand livre des transferts, miroir Python | w7-01 … w7-11 | ≈ 22 j |
| **7b** | TV : `TvBeacon` + permissions, `SyncHost`, association v2 (hello v2, toc, fenêtre auto, QR, SAS), écran Connexion + grand livre | w7-12 … w7-15 | ≈ 12 j |
| **7c** | Téléphone : `LinkRuntime` + CDM, découverte v2, `SyncClient` + magasins, première liaison + permissions + Réparer, écran Connexion + puce, politique de voie + file persistée + WD auto, résultat d'activation | w7-16 … w7-22 | ≈ 18 j |
| **7d** | Docs, campagne terrain + scripts (la partie « mesures de base » se lance **dès le début de 7a**), CI + table OEM | w7-23 … w7-25 | ≈ 5 j |

## Les 25 cahiers

| id | Cahier | Objet | Effort | Modèle | Statut | Dépend de |
|---|---|---|---|---|---|---|
| w7-01 | `sonnet-w7-01-link-manager-core.md` | `LinkManager` (phases, routes de contrôle/masse, `LinkSnapshot`) enveloppant `LinkMachine`/`LinkDriver` | L | sonnet | PRÊT | — |
| w7-02 | `sonnet-w7-02-sync-frames-codec.md` | trames `CBSY` v1, codec, vecteurs, `SYNC-PROTOCOL.md` § 1 | M | sonnet | PRÊT | — |
| w7-03 | `sonnet-w7-03-secure-session-cbsx.md` | poignée de main X25519 XX, HKDF, AES-GCM, SAS, vecteurs, § 2 | L | sonnet | PRÊT | w4-01 souhaité (sinon `X25519Lite`) |
| w7-04 | `sonnet-w7-04-sync-engine-domains-a.md` | `SyncEngine`, `SyncDomain`, `DeltaLog`, domaines `act/lots/lib/xfer`, § 3 | L | sonnet | PRÊT | w7-02 (contrat), w7-10 (contrat `TransferLedger`) |
| w7-05 | `sonnet-w7-05-sync-domains-b.md` | domaines `par/shop/set/ico` | M | sonnet | PRÊT | w7-04 (contrat) |
| w7-06 | `sonnet-w7-06-identity-trust-v2-readoption.md` | registre v2 (clé), `HelloInfo` v2, `…0006`, `Identity`, `Readoption`, `PairingWindowPolicy`, compteur de refus | L | sonnet | PRÊT | w7-03 (contrat `StaticKey`) |
| w7-07 | `sonnet-w7-07-activation-result-v2-keyring.md` | `RESULT` v2 structuré, trames `KNOCK`/`KEYRING`, enveloppe `keyring`, textes avec action | M | sonnet | PRÊT | — |
| w7-08 | `sonnet-w7-08-link-journal-selftest-texts.md` | `LinkJournal`, `SelfTest`, `LinkTexts`, `OemBattery`, 4 étapes de `Diagnostics` | M | sonnet | PRÊT | w7-01 (contrat `Isolation`) |
| w7-09 | `sonnet-w7-09-discovery-planner-route-policy.md` | `DiscoveryPlanner`, isolation, `RouteTableImpl`, `RoutePolicy` (+ étiquettes corrigées), `MdnsTxt` v2 | M | sonnet | PRÊT | w7-01 (contrat `RouteTable`) |
| w7-10 | `sonnet-w7-10-transfer-ledger-queue-persist.md` | `TransferLedger` (TV), `TransferQueueModel` persistable | M | sonnet | PRÊT | — |
| w7-11 | `sonnet-w7-11-python-sync-vectors.md` | `verify_vectors.py` rejoue trames, `CBSX`, `keyring` | S | haiku | PRÊT | w7-02, w7-03, w7-07 |
| w7-12 | `sonnet-w7-12-tv-beacon-permissions.md` | `TvBeacon` (`…0005`, `…0006`, mDNS v2, boot, job), `TvPermissions` sans boucle, `TvService` allégé | L | sonnet | PRÊT | 7a |
| w7-13 | `sonnet-w7-13-tv-sync-host.md` | `SyncHost` RFCOMM + HTTP `/api/sync/*`, domaines branchés, `NOTIFY`, `KeyringStore` | L | sonnet | PRÊT | 7a ; w7-12 (contrat) |
| w7-14 | `sonnet-w7-14-tv-association-v2.md` | `/api/hello` v2, `/api/knock`, fenêtre auto 10 min, toc, QR pur, dialogue SAS, empreinte | L | sonnet | PRÊT | 7a ; w7-12/13 (contrats) |
| w7-15 | `sonnet-w7-15-tv-connection-diag-ledger.md` | grand livre branché (`BtServer`, `/api/transfer`), `LinkDiagActivity`, `/api/link/*`, « Autoriser les mises à jour », `routes.txt` | M | sonnet | PRÊT | 7a ; w7-12 (manifeste, menu) |
| w7-16 | `sonnet-w7-16-phone-link-runtime-cdm.md` | `LinkRuntime`, façade `TvLinkManager`, permissions harmonisées, CDM, jobs manqués, clé du téléphone | L | sonnet | PRÊT | 7a, 7b |
| w7-17 | `sonnet-w7-17-phone-discovery-mdns-qr-knock.md` | NSD v2, `LanProbe`, lien profond QR (`TvDeepLinkCodec`), `Knock` | M | sonnet | PRÊT | 7a, 7b ; w7-16 (contrat) |
| w7-18 | `sonnet-w7-18-phone-sync-client-stores.md` | `SyncClient` (BT + HTTP), magasins, notifications, TOFU, branchements lots/parental/preuve, `SIMPLE` | L | sonnet | PRÊT | 7a, 7b ; w7-16 (contrat) |
| w7-19 | `sonnet-w7-19-phone-first-run-permissions-repair.md` | 3 touches, onglet par défaut, QR/toc/ré-adoption, `PermissionFlow`, « Réparer la connexion », dialogue identité, son | L | sonnet | PRÊT | 7a, 7b ; w7-16/17/18/20 (contrats) |
| w7-20 | `sonnet-w7-20-phone-connection-screen-chip.md` | `LinkChip`, `ConnectionScreen`, rapport exportable, carte OEM, `LinkSyncLine` | M | sonnet | PRÊT | 7a, 7b ; w7-16/18 (contrats) |
| w7-21 | `sonnet-w7-21-phone-route-policy-queue-wd.md` | voie LAN > WD > BT, `WifiDirectAuto`, dialogue ETA, file persistée, `XferCard`, étiquettes | M | sonnet | PRÊT | 7a, 7b ; w7-16/18 (contrats) |
| w7-22 | `sonnet-w7-22-phone-activation-result-ux.md` | `ActivateTvActivity` par le domaine `act`, `ResultV2` à l'écran, console : envoi `keyring` | S | sonnet | PRÊT | 7a, 7b ; w7-18 (contrat) |
| w7-23 | `sonnet-w7-23-docs-plug-and-play-sync.md` | `PLUG-AND-PLAY-SYNC.md`, ADMIN/PARENTAL/TRANSFER/REMOTE-TUNNEL-TV/TRIAL-EDITION/HANDOFF/SYNC-PROTOCOL § 4-5 | M | haiku | PRÊT | 7a-7c |
| w7-24 | `sonnet-w7-24-field-test-campaign-measures.md` | mesures de base (B2, **à lancer en avance**), `TEST-CAMPAIGN.md` § W7, `tools/link-test/*` | M | haiku | PRÊT | base : — ; campagne : 7a-7c |
| w7-25 | `sonnet-w7-25-ci-oem-table.md` | CI (vecteurs, tests `link`, contrôles de source), test de la table OEM | S | haiku | PRÊT | w7-11, w7-24, 7a-7c |

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`.

### 7a
| id | Fichiers possédés |
|---|---|
| w7-01 | nouveaux `C/link/{LinkManager,LinkConfig,LinkSnapshot}.kt`, `CT/link/LinkManagerTest.kt` |
| w7-02 | nouveaux `C/link/{SyncFrames,SyncCodec}.kt`, `CT/link/SyncCodecTest.kt`, `tools/activation/sync-frames-vectors.json`, `docs/SYNC-PROTOCOL.md` **§ 1** (création du fichier) |
| w7-03 | nouveaux `C/link/{SecureSession,Hkdf}.kt` (+ `X25519Lite.kt` si W4 absent), `CT/link/{SecureSessionTest,X25519LiteTest}.kt`, `tools/activation/sync-vectors.json`, `docs/SYNC-PROTOCOL.md` **§ 2** |
| w7-04 | nouveaux `C/link/{SyncEngine,SyncDomain,DeltaLog}.kt`, `C/link/domains/{ActDomain,LotsDomain,LibDomain,XferDomain}.kt`, `CT/link/{SyncEngineTest,DomainsATest}.kt`, `docs/SYNC-PROTOCOL.md` **§ 3** |
| w7-05 | nouveaux `C/link/domains/{ParDomain,ShopDomain,SetDomain,IcoDomain}.kt`, `CT/link/DomainsBTest.kt` |
| w7-06 | `C/trust/{TrustRegistry,HelloHandler,PairingSession}.kt`, `C/tv/BtProtocol.kt` (`HelloInfo` + constante), `CT/TrustTest.kt` ; nouveaux `C/link/{Identity,Readoption,PairingWindowPolicy}.kt`, `CT/link/{IdentityTest,ReadoptionTest}.kt` |
| w7-07 | `C/owner/{OwnerFrames,OwnerChannel,ActivationScreenState,Envelope}.kt`, `CT/owner/{ActivationScreenStateTest,OwnerChannelTest}.kt`, `tools/activation/test-vectors.json` (ajouts), `docs/ACTIVATION-FORMAT.md` (§ 3.5, § 5.2) ; nouveaux `C/owner/Keyring.kt`, `CT/owner/KeyringTest.kt` |
| w7-08 | nouveaux `C/link/{LinkJournal,SelfTest,LinkTexts,OemBattery}.kt`, `CT/link/{LinkJournalTest,SelfTestTest,LinkTextsTest}.kt` ; `C/trust/Diagnostics.kt`, `CT/DiagnosticsTest.kt` |
| w7-09 | nouveaux `C/link/{DiscoveryPlanner,RoutePolicy,MdnsTxt,RouteTableImpl}.kt`, `CT/link/{DiscoveryPlannerTest,RoutePolicyTest,MdnsTxtTest}.kt` ; `C/remote/RemoteClient.kt` (`routeName`), `C/lots/LotPush.kt` (`label`) |
| w7-10 | nouveau `C/link/TransferLedger.kt`, `CT/link/TransferLedgerTest.kt` ; `C/tv/TransferQueue.kt`, `CT/tv/TransferQueueTest.kt` |
| w7-11 | `tools/activation/verify_vectors.py`, `tools/tests/test_verify_vectors.py`, `docs/ACTIVATION-FORMAT.md` (§ 11.1 : 3 lignes) — **après** w7-02/03/07 (fin de 7a) |

Exception documentée : `docs/SYNC-PROTOCOL.md` est touché par w7-02, w7-03 et w7-04 dans **trois sections distinctes** (§ 1, § 2, § 3) ; `docs/ACTIVATION-FORMAT.md` par w7-07 (§ 3.5, § 5.2) puis w7-11 (§ 11.1, après fusion de w7-07). Le coordinateur fusionne ces fichiers section par section.

### 7b
| id | Fichiers possédés |
|---|---|
| w7-12 | nouveaux `R/{TvBeacon,TvPermissions,BeaconJob,LinkJournalTv}.kt` ; `R/{TvApp,TvService,ActivationActivity,PlayerActivity,OwnerBtHost}.kt`, `android/receiver/src/main/AndroidManifest.xml` |
| w7-13 | nouveaux `R/{SyncHost,SyncSources,SyncHttp,KeyringStore}.kt`, `CT/link/SyncHostTest.kt` ; crochets dans `R/{ActivationCenter,LotsHub,RentalHub,ParentalHub,TvPrefs}.kt` |
| w7-14 | `R/{PairActivity,HomeScreen}.kt`, `C/tv/ReceiverServer.kt` (`/api/hello` v2, `/api/knock`) ; nouveaux `R/{PairQr,ReadoptDialog}.kt`, `CT/tv/QrTest.kt` |
| w7-15 | `R/BtServer.kt`, `C/xfer/TransferHost.kt` (additif), `tools/routes/routes.txt`, `tools/tests/test_routes.py` ; nouveaux `R/{LinkDiagActivity,LinkDiagApi,TransferLedgerTv}.kt` (+ layout si XML) |

Lignes `À BRANCHER` attendues à la fusion 7b (une ligne chacune, posées par le coordinateur) : `TvService` → `.then(SyncHost.api)` et `TvBeacon.attachSync(SyncHost)` (w7-13) ; `ReceiverServer` upload classique → `ledger.begin/done` (w7-15) ; `TvService` → `helloExtra` pour `/api/hello` v2 (w7-14) ; `SyncHost.expectSas` (w7-14 ↔ w7-13).

### 7c
| id | Fichiers possédés |
|---|---|
| w7-16 | nouveaux `S/link/{LinkRuntime,DiscoveryAndroid,CdmAssociation,LinkCompanionService,PhoneKeyStore,LinkJournalPhone}.kt` ; `S/{TvLink,LinkAndroid,PhoneConnect}.kt`, `android/sender/src/main/AndroidManifest.xml`, `android/sender/src/main/res/xml/{backup_rules,data_extraction_rules}.xml` |
| w7-17 | nouveaux `S/link/{NsdDiscoveryV2,LanProbe,DeepLinkTv,Knock}.kt`, `C/link/TvDeepLink.kt`, `CT/link/DeepLinkTvTest.kt` ; `S/TvDiscovery.kt` |
| w7-18 | nouveaux `S/link/{SyncClient,SyncTransports,DomainStores,SyncNotifications,ActState,TvPinStore}.kt`, `C/link/SyncPolicy.kt`, `CT/link/SyncPolicyTest.kt` ; `S/{LotsRuntime,ParentalInbox}.kt`, `S/gate/ProofSync.kt` (si présent) |
| w7-19 | `S/{MainActivity,TvPairScreen,TvHome,BtPermission}.kt` ; nouveaux `S/link/{PermissionFlow,RepairScreen,IdentityDialog,ReadoptFlow}.kt`, `android/sender/src/main/res/raw/link_ok.ogg` |
| w7-20 | nouveaux `S/link/{ConnectionScreen,LinkChip,LinkSyncLine,JournalShare,OemBatteryCard}.kt` |
| w7-21 | `S/{TransferQueue,TransferQueueService,UploadService,BtUploadService,FastTransfer,WifiDirectScreen}.kt` ; nouveaux `S/link/{WifiDirectAuto,BulkRoute,XferCard,QueueStore}.kt`, `C/link/BulkDecision.kt`, `CT/link/BulkDecisionTest.kt` |
| w7-22 | `S/ActivateTvActivity.kt`, `OL/{TvBluetooth,ConsoleActivity}.kt`, `C/owner/OwnerChannel.kt` (**client** seulement), `CT/owner/OwnerChannelClientTest.kt` |

Lignes `À BRANCHER` attendues à la fusion 7c : `intent-filter castbridge://tv` dans le manifeste (w7-17/w7-19 → w7-16) ; `ConnectionActivity` dans le manifeste (w7-20 → w7-16) ; `TvHome` appelle `XferCard` (w7-21 → w7-19) et `LinkSyncLine` (w7-20 → w7-19) ; `MainActivity` appelle `LinkChip` (w7-20 → w7-19) ; `TvLinkStatus.onDiagnose` → `ConnectionScreen.open` (w7-20 → w7-19) ; `PairStep.Done` → `CdmAssociation.offerOnce` (w7-16 → w7-19).

### 7d
| id | Fichiers possédés |
|---|---|
| w7-23 | nouveau `docs/PLUG-AND-PLAY-SYNC.md` ; `docs/{BT-PLUG-AND-PLAY,ADMIN,PARENTAL,TRANSFER,REMOTE-TUNNEL-TV,API-SERVER,TRIAL-EDITION,HANDOFF,SYNC-PROTOCOL,CHANGELOG}.md` |
| w7-24 | nouveaux `tools/link-test/{measure_bt,measure_sync,battery,jobs,journal_pull}.sh`, `tools/link-test/README.md`, `tools/tests/test_link_test_scripts.py` ; `docs/TEST-CAMPAIGN.md` (§ W7) |
| w7-25 | `.github/workflows/{tools,android}.yml`, `docs/COORDINATION.md` (§ CI), `tools/requirements-dev.txt` ; nouveaux `tools/checks/check_w7_sources.sh`, `tools/tests/{test_check_w7_sources,test_oem_table}.py` |

Vérification de disjonction : aucun chemin n'apparaît deux fois dans une même sous-vague (`docs/SYNC-PROTOCOL.md` et `docs/ACTIVATION-FORMAT.md` : sections distinctes, voir l'exception ci-dessus ; `C/owner/OwnerChannel.kt` : w7-07 en 7a (serveur) puis w7-22 en 7c (client) ; `C/tv/ReceiverServer.kt` : w7-14 seul en 7b ; `tools/routes/routes.txt` : w7-15 seul). Chevauchements **entre** sous-vagues résolus par l'ordre 7a → 7b → 7c → 7d et une fusion entre chaque.

## Graphe de dépendances

```
Prérequis : w1-02, w1-05, w1-06 ──► 7a ;  w4-01/w4-03 souhaités ──► w7-03, w7-12 ;  w6-02/03/16 souhaités ──► w7-04, w7-18
w7-24 (mesures de base, B2) ──► LinkConfig de w7-01 (constantes injectables : la fusion ajuste)

7a : w7-01 ─┬─► w7-08, w7-09 (contrats)      w7-02 ─► w7-04 ─► w7-05      w7-03 ─► w7-06      w7-10 ─► w7-04      w7-07
            └──────────────────────────────────────────────────────────────────── w7-11 (après w7-02, w7-03, w7-07)
7b : w7-12 ∥ w7-13 ∥ w7-14 ∥ w7-15   (contrats : TvBeacon.attachSync, SyncHost.expectSas, LinkDiagActivity)
7c : w7-16 ∥ w7-17 ∥ w7-18 ∥ w7-19 ∥ w7-20 ∥ w7-21 ∥ w7-22   (contrats : LinkRuntime, SyncClient, DomainStores, CdmAssociation, LinkChip, XferCard)
7d : w7-23 ∥ w7-24 (campagne) ∥ w7-25
```

## Ordre de lancement conseillé

1. **Jour 0** : w7-24 partie « mesures de base » (B2) avec le propriétaire (durées Bluetooth réelles, débits) : résultats dans son rapport.
2. **Jour 1** : w7-01, w7-02, w7-03, w7-07, w7-10 en parallèle ; dès les rapports de w7-01/02/03/10 : w7-04, w7-06, w7-08, w7-09 ; dès w7-04 : w7-05 ; en fin : w7-11. **Fusion 7a** : `gradle --offline :core:test` complet, `python3 tools/activation/verify_vectors.py` (anciens vecteurs intacts : `git diff --quiet origin/integration/agents -- tools/activation/test-vectors.json` **doit échouer** seulement par ajout de cas, vérifier `grep '^-'` vide), `grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/link/` vide ; ajuster `LinkConfig` avec les mesures de base.
3. **Jour 7** : w7-12, w7-13, w7-14, w7-15 en parallèle. **Fusion 7b** : poser les lignes `À BRANCHER`, `:receiver:compileDebugKotlin`, `test_routes.py`, installation sur la **TV de référence (32 bits)** : beacon visible TV verrouillée, aucune boucle de permission, QR affiché, `curl /api/hello` v2, transfert interrompu visible ; copie de l'APK dans le `Download` de la clé USB (règle du propriétaire).
4. **Jour 11** : w7-16 à w7-22 en parallèle. **Fusion 7c** : lignes `À BRANCHER`, `:sender:compileDebugKotlin`, `:ownerlib:compileDebugKotlin`, `check_w7_sources.sh` (si w7-25 est prêt, sinon les greps des cahiers) ; parcours sur le téléphone du propriétaire : première liaison 3 touches, `pm clear` puis ré-adoption, TV réinstallée, Wi-Fi coupé, isolation (box de test), clé posée sur la TV ⇒ notification < 3 s.
5. **Jour 16** : w7-23, w7-24 (campagne), w7-25. Campagne avec le propriétaire ; **ne pas** publier une version TV ou téléphone de la vague 7 avant la campagne et la mise à jour de `docs/HANDOFF.md` § 9.
6. Après la vague : BLE si une TV du parc l'offre (D-W7-1), HTTP de masse dans `CBSY`/`BULK` chiffré (R4), relais distant (hors W7), mode enfant du téléphone (W6 § 3.9).

## Changements aux cahiers antérieurs (sans les éditer ; l'exécutant d'un cahier encore à lancer lit ceci d'abord)

| Cahier | Changement | Repris par |
|---|---|---|
| **w2-09** (`TvReachability`, écran « Dépannage ») | **ne plus exécuter** : absorbé par `SelfTest` + « Réparer la connexion » | w7-08, w7-19 |
| **w2-07** (découpe de `TvService`) | compatible ; relire après w7-12 (plus de `startOwnerChannel`/`register` dans `TvService`) | w7-12 |
| **w3-02** (FGS type, démarrage à froid) | compatible ; `TvBeacon` n'est pas un FGS ; `ActivationCenter.init` hors fil principal reste souhaité | — |
| **w3-09** (relais de révocation) | inchangé ; peut utiliser le domaine `act` pour **signaler** une liste à pousser (hors W7) | — |
| **w4-01 / w4-03** (`X25519`, `InstallKey`, `KeystoreWrapper`) | `InstallKey` sert aussi d'identité `CBSX` (HKDF `info` distinct) ; si W7 passe avant W4, `X25519Lite` et la graine fichier sont à **réconcilier** (un seul fichier gagne) | w7-03, w7-12 |
| **w5-12** (`TvShopCache`) | lit le domaine `shop` quand `SyncClient` est `LIVE`, sinon ses lectures HTTP | w7-05, w7-18 |
| **w6-02 / w6-16** (`PhoneSync`, `ProofSync`) | nourris par le domaine `act` (plus de sondage) ; `GET /api/activation/proof?nonce=` reste pour une TV sans `sync` | w7-04, w7-18 |
| **w6-03 / w6-12** (`TvProof`, route de preuve) | inchangés ; `ActDomain` transporte la preuve à la demande | w7-04, w7-13 |
| **w6-15** (`HolderHttp`, CBTP v2) | inchangé ; `ParDomain` n'annonce que des ids | w7-05 |
| `bt-tunnel-keepalive`, `smart-remote`, `bt-remote` | inchangés ; étiquette « Wi-Fi » corrigée par `RoutePolicy.label` | w7-09 |
| `protect-02` (porte de classe) | `TvBeacon` respecte la porte (seul `…0005` sur PAS_TV) | w7-12 |

## Décisions prises par l'architecte (renversables ; détail § 13 de la conception)
D-W7-1 pas de BLE ni de HDMI-CEC en v1 ; D-W7-2 fenêtre d'association auto-ouverte 10 min sans téléphone de confiance, 2 min sur toc, manuel sinon ; D-W7-3 identité par clés X25519 (TV = `InstallKey`, téléphone = `PhoneKey` fichier), TOFU + empreinte ; D-W7-4 `RESULT` v2 structuré + message signé `keyring` (portée `REGISTRY`) ; D-W7-6 CDM optionnel, une fois ; D-W7-7 onglet « CastBridge TV » par défaut sans TV liée ; D-W7-8 partage d'Internet proposé en une touche, automatique seulement pour activation/mise à jour.

## Questions au propriétaire (les seules qui bloquent)

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W7-5** | Le « code foyer » de la ré-adoption est-il le **code parental** existant, ou un code distinct ? | le champ « code foyer » de w7-19 et `householdCheck` de w7-13 (sans réponse : ré-adoption = fenêtre + un « Autoriser », rien n'est bloqué) | **code parental** (un seul code à retenir ; absent ⇒ parcours normal) |
| **B2** | Mesures Bluetooth réelles (durée de `connect`, libération, débit) sur la TV de référence et le S21+ | les constantes de `LinkConfig` (injectables : la fusion 7a les ajuste) | lancer w7-24 « base » au jour 0 |
