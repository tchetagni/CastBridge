# Vague 6 pour agents Sonnet/Haiku — index (2026-10-02) : rapports parentaux de toute la TV, mode minimal du téléphone, session super administrateur

Source : `docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md` (lire en entier avant tout cahier). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »). **Ce fichier ne modifie pas les index des vagues 1-5** ; le § « Changements aux cahiers w4/w5 » liste ce que la vague 6 amende sans les éditer.

**Demande du propriétaire qui fonde la vague (2026-10-02)** : tous les rapports d'usage de CastBridge-TV **et de la TV entière** au détenteur de l'application téléphone ; **fonctions minimales** du téléphone tant qu'il n'est pas connecté et synchronisé avec une TV en **activation de production** (par exemple : pas de copie de vidéo vers une TV qui n'est pas en production ; messages de déblocage liés aux synchronisations) ; **fonctions totales** pour le téléphone du super administrateur après activation sur sa console.

**Modèle d'exécution recommandé par cahier** : `haiku` = mécanique, scripts, CI, campagne ; `sonnet` = tout le reste. Efforts en agent·jours (S ≈ 0,5-1, M ≈ 2, L ≈ 3-4).

**Prérequis fusionnés avant la vague 6** : w1-02 (`SafeFile`), w1-06 (`routes.txt`), w2-04 (`KeyBadgeOverlay` lisible), w5-18 (contrôle parental des achats : W6 s'y empile) ; **souhaités** : 4a (w4-01 `SecretWrapper`, w4-03 `KeystoreWrapper` : sinon repli « protection logicielle » dans w6-03, w6-12, w6-13, w6-19), 4b (w4-07 `TvAccess.degraded` : sinon `DEGRADED` est modélisé côté téléphone sans le champ), w4-13/w5-13 (mode point focal : sinon `agentActive = false`), w5-11/12 (boutique téléphone : sinon w6-17 ne gate pas la boutique), w5-16 (sa route `GET /api/activation/proof` **sans nonce** est remplacée par w6-12).

## Cinq sous-vagues séquentielles ; fichiers disjoints à l'intérieur d'une sous-vague

| Sous-vague | Objet | Cahiers | Effort |
|---|---|---|---|
| **6a** | Cœur (JVM, testé) : porte du téléphone + matrice, synchronisation + catalogue de messages, preuve de TV + cache + vecteurs, session super, collecte « toute la TV », détenteurs + confidentialité, rapports v2 signés, agrégation v2 | w6-01 … w6-08 | ≈ 15 j |
| **6b** | Serveur (preuve pour lots complets / boutique), miroirs Java/Python, interrupteur et grâce du téléphone, règles de sauvegarde | w6-09 … w6-11 | ≈ 4 j |
| **6c** | TV : clé de signature + route/trames de preuve, collecte branchée + magasin chiffré, consentement + indicateur, Wi-Fi détenteur + CBTP v2 + tunnel | w6-12 … w6-15 | ≈ 10 j |
| **6d** | Téléphone : porte, synchro, mur, puce « TV cible » ; garde de la file de transfert et chemins de données ; onglet Parental v2 ; session super + contrôle de publication | w6-16 … w6-19 | ≈ 12 j |
| **6e** | Docs, brouillons juridiques, campagne de test, CI | w6-20 … w6-23 | ≈ 5 j |

## Les 23 cahiers

| id | Cahier | Objet | Effort | Modèle | Statut | Dépend de |
|---|---|---|---|---|---|---|
| w6-01 | `sonnet-w6-01-phone-gate-core.md` | `PhoneFeature`, `MINIMAL_WHITELIST`/`AGENT_WHITELIST` figées, `PhoneGateState`, `PhoneGate.state/canUse`, **matrice** fonction × état (`EXPECTED_MATRIX` figée), règle TV cible / au moins une TV | M | sonnet | PRÊT | — |
| w6-02 | `sonnet-w6-02-phone-sync-messages.md` | `PhoneSync` (machine d'états par TV, horloge monotone) + `PhoneGateTexts` (catalogue unique des messages de déblocage, actions) | M | sonnet | PRÊT | w6-01 (constantes ; sinon locales) |
| w6-03 | `sonnet-w6-03-tv-proof-core.md` | `InstallSigner`, `TvProof` (enveloppe `proof` au défi), `ProofCache` (14 j, `TvClock`), trames 9/10, `proof-vectors.json` | L | sonnet | PRÊT | w4-01 souhaité |
| w6-04 | `sonnet-w6-04-super-session-core.md` | `SuperSession` (1/4/12/24 h, monotone, 5 échecs, `AuditChain`) | S | sonnet | PRÊT | — |
| w6-05 | `sonnet-w6-05-parental-collect-core.md` | `UsageSlots`, `ScreenSegments`, `ConnectionLog`, `AppInventoryDiff`, `DailyAggregates`, `EventType.SUDOKU/SCREEN/CONNECTION`, journal 800/14 j, écriture ≤ 1/min | M | sonnet | PRÊT | — |
| w6-06 | `sonnet-w6-06-holders-privacy-core.md` | `Holders` (consentement TV, `tokenId`, auto-révocation), `ParentalPrivacy` (collecté / jamais, textes), `shareTitles`/`lateUseAlert`, routes `holders/*` | M | sonnet | PRÊT | w5-18 (si fusionné) |
| w6-07 | `sonnet-w6-07-report-v2-core.md` | `ReportV2` (daily/weekly/alert/snapshot), signature Ed25519 + HMAC, CBTP v2, `ReportInbox` vérifie `sig`, liste noire testée | L | sonnet | PRÊT | w6-05, w6-06 (interfaces), w6-03 (`InstallSigner`) |
| w6-08 | `sonnet-w6-08-phone-aggregation-v2.md` | `WholeTvModel`, `ParentalLedger` v2, `ReportAggregator` aujourd'hui/semaine, `Heatmap` par application, `Trends`, exports v2, Sudoku | M | sonnet | PRÊT | schéma v2 (w6-07) |
| w6-09 | `sonnet-w6-09-server-tv-proof.md` | `X-CB-TV-Proof` exigé (lots complets, boutique, jetons), `TvProofHeader`, `TvProof.java` + vecteurs | M | sonnet | PRÊT | w6-03 ; w5-06/07/08 si fusionnés |
| w6-10 | `sonnet-w6-10-python-proof-vectors.md` | `verify_vectors.py` rejoue `proof-vectors.json` | S | haiku | PRÊT | w6-03 |
| w6-11 | `sonnet-w6-11-phone-build-flag-grace.md` | `REQUIRE_TV_PROOF` (éteint), `phone.lock.*`, `TRUSTED_KEYS` téléphone, exclusions de sauvegarde (preuves, session), RELEASES | S | haiku | PRÊT | — (w4-13 pour `TRUSTED_KEYS` si déjà là) |
| w6-12 | `sonnet-w6-12-tv-proof-route.md` | TV : `installSigner()`, `GET /api/activation/proof?nonce=`, trames BT `PROOF_REQUEST/PROOF`, empreinte dans « À propos » | M | sonnet | PRÊT | w6-03 ; w4-03 souhaité |
| w6-13 | `sonnet-w6-13-tv-collect-store.md` | TV : collecte branchée (écran, tranches, lancements, Sudoku, connexions), `ParentalStore` chiffré, rapports v2 émis, `snapshot` journalisé | L | sonnet | PRÊT | w6-05, w6-06, w6-07 ; w6-12 en parallèle |
| w6-14 | `sonnet-w6-14-tv-consent-indicator.md` | TV : `HolderConsentActivity`, page « Téléphones des parents », indicateur permanent adulte/enfant, « À propos » | M | sonnet | PRÊT | w6-06 |
| w6-15 | `sonnet-w6-15-tv-holder-wifi-cbtp.md` | TV : `HolderHttp` + routes `/api/parental/holder/*` (jeton de téléphone de confiance), CBTP v2 dans `BtServer`, routes parentales interdites au tunnel, `routes.txt` | M | sonnet | PRÊT | w6-06, w6-07 |
| w6-16 | `sonnet-w6-16-phone-gate-runtime-wall.md` | Téléphone : `ProofStore`, `ProofSync` (BT + Wi-Fi, TOFU, identité changée), `PhoneGateRuntime`, `GateWall`, onglets cadenassés, puce « TV cible », avertissement périmé | L | sonnet | PRÊT | 6a, w6-11, w6-12 |
| w6-17 | `sonnet-w6-17-phone-send-guard-data-paths.md` | Téléphone : `SendGuard` (cœur) + file de transfert qui refuse d'enfiler, lots d'essai seulement, téléchargements, lecteur, jeux, Apprendre, bibliothèque/admin TV, boutique (`X-CB-TV-Proof`), tunnel, refus de la TV transmis | L | sonnet | PRÊT | 6a ; w6-16 en parallèle (contrat) |
| w6-18 | `sonnet-w6-18-phone-parental-tab-v2.md` | Téléphone : onglet Parental v2 (Aujourd'hui/Semaine, Toute la TV, Parents de cette TV, Confidentialité), tirage Wi-Fi, murs `PARENTAL_*` | L | sonnet | PRÊT | 6a, w6-15 ; w6-16 en parallèle |
| w6-19 | `sonnet-w6-19-super-session-ui-release-check.md` | Console : session super (durée, bandeau, fermeture, scellement Keystore), `check_no_superadmin.sh` + test, RELEASES | M | sonnet | PRÊT | w6-04 |
| w6-20 | `sonnet-w6-20-docs-w6.md` | PARENTAL, nouveau PHONE-GATE (matrice + catalogue), ACTIVATION-FORMAT (`proof`), OWNER-CONSOLE (session, perte), TRIAL-EDITION § 14, API-SERVER, TELEMETRY, REMOTE-TUNNEL-TV, HANDOFF | M | sonnet | PRÊT | 6a-6d |
| w6-21 | `sonnet-w6-21-legal-parental-household.md` | Brouillons : information du foyer, divulgation `PACKAGE_USAGE_STATS`, complément de politique de confidentialité, registre des traitements | M | sonnet | **BLOQUÉ partiel** (D-W6-4 juriste, D7) | w6-06 (listes) |
| w6-22 | `sonnet-w6-22-test-campaign-w6.md` | `docs/TEST-CAMPAIGN.md` § W6 (≈ 45 étapes TV + téléphone + super) | S | haiku | PRÊT | 6c, 6d |
| w6-23 | `sonnet-w6-23-ci-w6.md` | CI : vecteurs de preuve, contrôle « aucun haché », tests Python, COORDINATION § CI | S | haiku | PRÊT | w6-10, w6-11, w6-15, w6-19 |

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = `backend/src/test/java/castbridge/server/`.

### 6a
| id | Fichiers possédés |
|---|---|
| w6-01 | nouveaux `C/owner/PhoneGate.kt`, `CT/owner/{PhoneGateTest,PhoneMatrixTest}.kt` |
| w6-02 | nouveaux `C/owner/{PhoneSync,PhoneGateTexts}.kt`, `CT/owner/{PhoneSyncTest,PhoneGateTextsTest}.kt` |
| w6-03 | nouveaux `C/owner/{InstallSigner,TvProof,ProofCache}.kt`, `CT/owner/{TvProofTest,ProofCacheTest,ProofVectorsTest}.kt`, `tools/activation/proof-vectors.json` ; `C/owner/OwnerFrames.kt` (deux constantes) |
| w6-04 | nouveaux `C/owner/SuperSession.kt`, `CT/owner/SuperSessionTest.kt` |
| w6-05 | nouveaux `C/parental/Collect.kt`, `CT/parental/CollectTest.kt`, `CT/parental/tab/TvJournalTest.kt` ; `C/parental/tab/{TvJournal,SessionTracker,TabModel}.kt` |
| w6-06 | nouveaux `C/parental/{Holders,ParentalPrivacy}.kt`, `CT/ParentalHoldersTest.kt` ; `C/parental/{ParentalApi,ParentalModel,ParentalEngine,Supervision}.kt`, `CT/Parental*Test.kt` (racine) |
| w6-07 | nouveau `C/parental/ReportV2.kt`, `CT/parental/{ReportV2Test,ReportSyncV2Test}.kt` ; `C/parental/{ParentalReports,ParentalSync}.kt` |
| w6-08 | nouveaux `C/parental/tab/WholeTvModel.kt`, `CT/parental/tab/WholeTvTest.kt` ; `C/parental/tab/{ParentalLedger,ReportAggregator,Heatmap,Trends,Exports,LearnInsights,Freshness,LiveReport,TimeRanges}.kt`, `CT/parental/tab/{AggregationTest,ExportsAndLockTest,ParentalLedgerTest,TabTestSupport}.kt` |

### 6b
| id | Fichiers possédés |
|---|---|
| w6-09 | nouveaux `B/licenses/{TvProofHeader,TvProof}.java`, `BT/licenses/{TvProofHeaderTest,TvProofVectorsTest}.java` ; le contrôleur des lots (nommé dans le rapport), `B/shop/ShopController.java`, `B/shop/tokens/**` (si w5 fusionné), `docs/API-SERVER.md` |
| w6-10 | `tools/activation/verify_vectors.py`, `tools/tests/test_verify_vectors.py` |
| w6-11 | `android/sender/build.gradle.kts`, `version.properties`, `android/sender/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`, `tools/tests/test_backup_rules.py`, `docs/RELEASES.md` |

### 6c
| id | Fichiers possédés |
|---|---|
| w6-12 | `R/{ActivationCenter,RentalHub,OwnerBtHost,KeystoreWrapper,ActivationActivity}.kt`, `android/core/src/main/resources/castbridge/admin.html`, `C/owner/OwnerChannel.kt` (branche additive) |
| w6-13 | `R/ParentalHub.kt`, nouveau `R/ParentalStore.kt`, `R/{ForegroundWatcher,TvService,UsbImporter,SshControl,TunnelHub,SudokuActivity}.kt` (crochets d'une ligne sauf ParentalHub/ParentalStore), `android/receiver/src/main/res/xml/{backup_rules,data_extraction_rules}.xml` |
| w6-14 | `R/{ParentalActivity,ParentalUi,KeyBadgeOverlay,HomeScreen,PlayerActivity}.kt`, nouveau `R/HolderConsentActivity.kt`, le fichier « À propos » (s'il n'est pas `PlayerActivity.kt` : à nommer et à réserver dans le rapport **avant** d'éditer), `android/receiver/src/main/AndroidManifest.xml`, `android/receiver/src/main/res/drawable/ic_t_parental_shared.xml` |
| w6-15 | nouveaux `C/parental/HolderHttp.kt`, `CT/parental/HolderHttpTest.kt` ; `C/parental/ParentalApi.kt`, `R/BtServer.kt`, `tools/routes/routes.txt`, `tools/tests/test_routes.py` |

Attention 6c : `R/TunnelHub.kt` est à **w6-13** (crochet « assistance à distance ») ; la liste noire des routes parentales du tunnel de **w6-15** se fait donc **dans `C/parental/ParentalApi.kt` ou `R/BtServer.kt`** si la garde peut y vivre ; sinon w6-15 demande à w6-13 (rapport) d'ajouter la ligne dans `TunnelHub.kt`. w6-12 ne touche pas `TvService.kt` (w6-13) ; w6-14 ne touche pas `ParentalHub.kt` (w6-13).

### 6d
| id | Fichiers possédés |
|---|---|
| w6-16 | nouveaux `S/gate/{ProofStore,ProofSync,PhoneGateRuntime,GateWall,TargetTvChip}.kt` ; `S/{MainActivity,TvLink,Ui,TvPairScreen}.kt` |
| w6-17 | nouveaux `C/xfer/SendGuard.kt`, `CT/xfer/SendGuardTest.kt`, `S/gate/GateStub.kt` (temporaire) ; `S/{TransferQueue,TransferQueueService,UploadService,BtUploadService,FastTransfer,MoveToTv,DlnaHandoff,ShareToTvActivity,LotsRuntime,DownloadService,DownloadsScreen,GamesScreen,LearnScreen,QuizScreen,ChessScreen,TvHub,TvLibraryScreen,TvTransferScreen,BtSshGateway}.kt`, `S/player/{PlaybackService,PhoneLibrary}.kt`, `S/shop/{ShopScreen,HttpShopApi,ShopRuntime}.kt` (si présents), la classe de file dans `C/xfer/**` |
| w6-18 | `S/Parental{Tab,Inbox,ReportsUi,Charts,Export,Data,WholeTv,Screen,Activity}.kt`, nouveaux `S/{ParentalHolders,ParentalPrivacyScreen,ParentalWholeTvV2}.kt` |
| w6-19 | `OL/{SuperAdmin,ConsoleActivity,OwnerStore}.kt`, nouveaux `OL/{SuperSessionStore,PhoneKeystoreWrapper,SuperBanner}.kt`, `tools/release/check_no_superadmin.sh`, `tools/tests/test_check_no_superadmin.py`, `docs/RELEASES.md` |

### 6e
| id | Fichiers possédés |
|---|---|
| w6-20 | `docs/{PARENTAL,ACTIVATION-FORMAT,OWNER-CONSOLE,TRIAL-EDITION,API-SERVER,TELEMETRY,REMOTE-TUNNEL-TV,HANDOFF}.md`, nouveau `docs/PHONE-GATE.md` |
| w6-21 | nouveaux `docs/legal/{INFORMATION-FOYER-CONTROLE-PARENTAL,DIVULGATION-ACCES-DONNEES-UTILISATION,POLITIQUE-CONFIDENTIALITE-complement-W6,REGISTRE-TRAITEMENTS-parental}.md` (+ `docs/legal/README.md` s'il manque) |
| w6-22 | `docs/TEST-CAMPAIGN.md` (§ W6) |
| w6-23 | `.github/workflows/{tools,android,release}.yml`, `docs/COORDINATION.md` (§ CI), `tools/requirements-dev.txt` |

Vérification de disjonction : aucun chemin n'apparaît deux fois dans une même sous-vague (`docs/RELEASES.md` : w6-11 en 6b puis w6-19 en 6d, sous-vagues différentes ; `C/parental/ParentalApi.kt` : w6-06 en 6a puis w6-15 en 6c ; `C/owner/OwnerFrames.kt` : w6-03 seul). Chevauchements **entre** sous-vagues résolus par l'ordre 6a → 6b → 6c → 6d → 6e et une fusion entre chaque.

## Graphe de dépendances

```
Prérequis : w1-02, w1-06, w2-04, w5-18 ──► 6a ;  4a (w4-01, w4-03) souhaité ──► w6-03, w6-12, w6-13, w6-19 ;  4b (w4-07) souhaité ──► w6-01, w6-03 ;  w5-11/12 ──► w6-17 (boutique) ;  w5-06..08 ──► w6-09 (boutique)

6a : w6-01 ─► w6-02 (constantes)      w6-03      w6-04      w6-05 ─┬─► w6-07 ─► w6-08 (schéma)
                                                            w6-06 ─┘
6b : w6-03 ─► w6-09, w6-10 ;  w6-11 indépendant
6c : w6-03 ─► w6-12 ∥ w6-13 (w6-05, 06, 07) ∥ w6-14 (w6-06) ∥ w6-15 (w6-06, 07)
6d : w6-16 (6a, w6-11, w6-12) ∥ w6-17 (6a ; contrat w6-16) ∥ w6-18 (6a, w6-15 ; contrat w6-16) ∥ w6-19 (w6-04)
6e : w6-20, w6-21, w6-22, w6-23 en parallèle (w6-20 et w6-22 après fusion de 6d ; w6-23 après w6-19)
```

## Ordre de lancement conseillé

1. **Jour 1** : w6-01, w6-03, w6-04, w6-05, w6-06 en parallèle ; dès le rapport de w6-01 : w6-02 ; dès w6-05 + w6-06 : w6-07 ; dès le schéma v2 de w6-07 (rapport) : w6-08. **Fusion 6a** : `:core:test` complet, `python3 tools/activation/verify_vectors.py` (vecteurs existants intacts : `git diff --quiet tools/activation/test-vectors.json tools/activation/rental-vectors*.json`), listes blanches et matrice figées.
2. **Jour 5** : w6-09, w6-10, w6-11 en parallèle. **Fusion 6b** : `./mvnw test`, tests Python, `:sender:compileDebugKotlin` avec et sans `-PrequireTvProof=true` (refus sans clé).
3. **Jour 7** : w6-12, w6-13, w6-14, w6-15 en parallèle. **Fusion 6c** : `:receiver:compileDebugKotlin`, `:core:test`, `test_routes.py`, `test_backup_rules.py` ; installation sur la **TV de référence (32 bits)** : route de preuve, consentement, indicateur, rapport v2 avec tranches, magasin illisible par `run-as`, tunnel refusé ; copie de l'APK dans le `Download` de la clé USB (règle du propriétaire).
4. **Jour 11** : w6-16, w6-17, w6-18, w6-19 en parallèle. **Fusion 6d** : `:sender:compileDebugKotlin`, `:ownerlib:compileDebugKotlin`, `:core:test`, `check_no_superadmin.sh` sur une build ordinaire (passe) et propriétaire (échoue) ; parcours sur le téléphone du propriétaire **avec l'interrupteur éteint** (rien ne change), puis une build `-PrequireTvProof=true` **à côté** (TV d'essai ⇒ murs ; clé de production ⇒ tout s'ouvre ; copie vers l'essai refusée et rien en file).
5. **Jour 15** : w6-20, w6-21, w6-22, w6-23. Campagne w6-22 avec le propriétaire. **Ne pas** allumer `REQUIRE_TV_PROOF` dans une version publiée avant les trois temps de § 3.4 ; **ne pas** distribuer la fonction parentale v2 hors du cercle de test avant D-W6-4 (juriste).
6. Après la vague : relais distant chiffré de bout en bout (§ 2.9, v1.5, sur décision), message signé `keyring` (ajout de clé par la clé de secours, § 4.5), mode enfant du téléphone (§ 3.9), activation `subject=phone` si un jour un droit propre au téléphone est voulu.

## Changements aux cahiers w4/w5 (sans les éditer ; l'exécutant d'un cahier w4/w5 encore à lancer lit ceci d'abord)

| Cahier | Changement | Repris par |
|---|---|---|
| **w5-16** (`ShopHub`, `GET /api/activation/proof`) | la route **sans nonce** ne doit plus être créée ; si déjà fusionnée, w6-12 la remplace par `GET /api/activation/proof?nonce=` (réponse `cbx1` type `proof`) ; la boutique continue d'envoyer **le jeton d'activation brut** au serveur (`X-CB-TV-Proof`, inchangé) | w6-12 |
| **w5-04** (`ActivationProof`) | la classe reste (preuve brute pour le serveur) ; la preuve au défi est `TvProof` (w6-03) ; ne pas dupliquer | w6-03 |
| **w5-11 / w5-12** (boutique téléphone) | les écrans de commande passent derrière `GateWall(SHOP_ORDER)` ; `HttpShopApi` ajoute `X-CB-TV-Proof` à partir de `ProofStore` ; la consultation reste ouverte | w6-17 |
| **w5-13** (mode point focal réduit) | l'état `Agent` de `PhoneGate` borne le mode : vente de clés, bons, confirmation de commandes, lecture de la demande d'une TV ; **pas** de lecteur/jeux/parental par ce biais | w6-01, w6-16 |
| **w5-18** (parental des achats) | inchangé ; ses compteurs entrent dans le rapport v2 ; W6 ajoute le **suivi** du Sudoku (temps, parties), pas seulement sa catégorie | w6-05, w6-07, w6-13 |
| **w4-07 / w4-08** (mode réduit) | inchangés ; `TvProof` lit `TvAccess.degraded` si présent ; le téléphone tombe en `Minimal` (M-TV-ENDED) quand sa seule TV est en mode réduit ; `PARENTAL` reste ouvert sur la TV **et** le tableau de bord du téléphone (colonne E) | w6-01, w6-03 |
| **w4-13** (`TRUSTED_KEYS` du téléphone) | si déjà fait, w6-11 ne le refait pas | w6-11 |
| `sonnet-w2-03` (UX activation, textes) | les textes du **téléphone** relatifs au mode minimal viennent de `PhoneGateTexts` (w6-02) ; w2-03 garde les textes de la **TV** | w6-02 |
| `protect-*` | inchangés ; la porte du téléphone est une porte d'interface + refus local : documenté honnêtement (§ 3.6) | w6-20 |

## Décisions prises par l'architecte (renversables ; détail au § 9 de la conception)
D-W6-1 liste minimale (§ 3.2) et matrice (§ 3.7, dont : copie de média = production prouvée seulement ; cast/télécommande/Apprendre sur la TV = la TV décide ; lots d'essai vers une TV d'essai ; contenus libres toujours ; parental ouvert en mode réduit) ; D-W6-2 pas de relais distant en v1 ; D-W6-3 le super administrateur n'a pas accès aux rapports parentaux des TV qui ne l'ont pas désigné ; D-W6-5 preuve 14 j, session super 12 h (24 max), grâce téléphone 14 j ; fonctions propres au téléphone ouvertes dès qu'**une** TV est prouvée (pas « la plus stricte ») ; pas de mode enfant du téléphone en W6.

## Questions au propriétaire (les seules qui bloquent)

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **D-W6-4** | Validation par un juriste des textes d'information du foyer, de la divulgation « accès aux données d'utilisation » et du complément de politique de confidentialité | distribution de la fonction parentale v2 hors du cercle de test (w6-21 rédige les brouillons sans attendre) | faire relire avant toute diffusion ; garder l'indicateur non désactivable quoi qu'il arrive |
| **D7** (existant) | Nom commercial et contact | textes de w6-21, « À propos », mur du téléphone (« demandez une clé à … ») | inchangé |
