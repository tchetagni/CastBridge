# Plan en vagues pour agents Sonnet — index (2026-10-02)

Source : `docs/coordination/RECOMMANDATIONS-FABLE-2026-10-02.md`. Chaque cahier `sonnet-w<vague>-<nn>-<slug>.md` est **autonome** : un agent Sonnet l'exécute sans autre contexte. Protocole : `docs/COORDINATION.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main`, jamais le serveur, jamais de secret, textes utilisateur en français, dire « CastBridge » (téléphone) / « CastBridge-TV »).

**Règles communes à tous les cahiers**
- Commandes de test : `cd android && gradle --offline :core:test --tests '<filtre>'` (repli sans SDK Android : `GRADLE="gradle --offline" tools/core-harness/run.sh :core:test --tests '<filtre>'`) ; serveur : `cd backend && ./mvnw -q -o test -Dtest=<Classe>` ; Python : `python3 -m unittest discover -s <dossier>` (quiz-bank : `python3.12`).
- Ne jamais committer sur `integration/agents` ni `main` ; ne pas déployer ; ne pas toucher `~/.castbridge-signing`, `backend/.env`, `secrets/`.
- Un cahier **BLOQUÉ** : l'agent fait ce qui ne dépend pas de la question, pose la question dans son rapport (`STATUT: BLOQUÉ`, `QUESTION: …`) et ne devine pas.
- Au sein d'une vague, les fichiers sont **disjoints** (matrice ci-dessous) : les tâches peuvent tourner en parallèle. Un fichier non listé est **hors zone** (lecture seule).

**42 cahiers** : vague 1 = 13, vague 2 = 15, vague 3 = 14.

## Vague 1 — sécurité rapide, fiabilité, hygiène (13 tâches, toutes indépendantes)

| id | Cahier | Objet | Effort | Statut |
|---|---|---|---|---|
| w1-01 | `sonnet-w1-01-backups-superadmin.md` | `allowBackup=false` TV + téléphone, exclusions, `noSuperAdmin` par défaut, test des règles XML | S | PRÊT |
| w1-02 | `sonnet-w1-02-fsync-safefile.md` | `SafeFile`/`AtomicFile` pour coffre de location, index des lots, progression, file de livraison, liste « lus » | S | PRÊT |
| w1-03 | `sonnet-w1-03-api-tv-hardening.md` | PIN en en-tête seul, contrôle `Host`/adresses privées, rétrogradation refusée en release | S | PRÊT |
| w1-04 | `sonnet-w1-04-offline-profile.md` | Sonde gstatic sur action manuelle seulement, DHT/BitTorrent coupés, licence aria2 | S/M | PRÊT (D3 pour le retrait total) |
| w1-05 | `sonnet-w1-05-clock-grace.md` | Uptime cumulé persisté, règle AHEAD 45 j pour `TvGate`, grâce refusée sans `clock.txt` | M | PRÊT (D1 pour `graceDays=0`) |
| w1-06 | `sonnet-w1-06-trial-routes-exhaustive.md` | Table des routes générée depuis le code ; test « chaque route classée » ; `packs/import` fermé | S | PRÊT |
| w1-07 | `sonnet-w1-07-ci-tools.md` | `tools.yml` (Python, vecteurs, contenu, sshd), `release.yml` neutralisé, `requirements-dev.txt` | S | PRÊT |
| w1-08 | `sonnet-w1-08-releases-doc-gitignore.md` | `docs/RELEASES.md`, `.gitignore` secrets/logs, script `SHA256SUMS` | S | PRÊT |
| w1-09 | `sonnet-w1-09-flaky-tests.md` | Trois tests instables rendus déterministes (port 0 interne, horloge injectée, aléa injecté) | S | PRÊT |
| w1-10 | `sonnet-w1-10-vectors-java-python.md` | `rental-vectors.json` rejoué en Java et Python ; `server-issued.json` en Python ; plafond implicite 30 j aligné | S | PRÊT |
| w1-11 | `sonnet-w1-11-backend-hygiene.md` | `deviceName` retiré/haché, purge des événements bruts, règle Flyway, cap taille catalogue, § rotation de clé | S | PRÊT |
| w1-12 | `sonnet-w1-12-ops-monitoring-backups.md` | Copie hors site chiffrée, sondes/pings, script hôte, runbook, exercice de restauration | S/M | PRÊT |
| w1-13 | `sonnet-w1-13-rental-honest-docs.md` | Doc et commentaires honnêtes sur KEK/lot en clair ; test renommé | S | PRÊT (D4) |

## Vague 2 — produit, architecture, monétisation phase 0 (15 tâches)

| id | Cahier | Objet | Effort | Statut |
|---|---|---|---|---|
| w2-01 | `sonnet-w2-01-tv-revocation-seq.md` | `SeqState` et `RevocationState` persistés, `cbr1` par fichier/Bluetooth/HTTP, clé de secours | M | PRÊT |
| w2-02 | `sonnet-w2-02-ssh-release.md` | Pas de shell/exec en release, SFTP limité au média, drapeau de build | M | PRÊT (D5) |
| w2-03 | `sonnet-w2-03-ux-activation.md` | Avis sans placeholder, contact du vendeur, refus actionnables, clé Bluetooth auto-acceptée, ordre du téléphone | M | BLOQUÉ partiel (D7 : texte + contact) |
| w2-04 | `sonnet-w2-04-key-expiry-badge.md` | Écran « clé terminée », rappels J-7/3/1, badge lisible et accessible, tuile Langues grise en essai | M | PRÊT (D6 pour le mode dégradé) |
| w2-05 | `sonnet-w2-05-learn-tv-ux-threads.md` | Tailles 3 m, textes « aperçu », compteur d'essai dans Apprendre, notice de fin routée, threads/handlers | M | PRÊT |
| w2-06 | `sonnet-w2-06-phone-shop-phase0.md` | Écran « Louer des leçons » : catalogue signé → commande → preuve → clé+lots par Bluetooth | L | BLOQUÉ (D8, D9) |
| w2-07 | `sonnet-w2-07-tvservice-split.md` | `TvNetMonitor`, `TvExtraRoutes`, `ScreenLauncher`, `StorageEvents` extraits de `TvService` | M | PRÊT |
| w2-08 | `sonnet-w2-08-executors.md` | Exécuteur partagé, threads bruts remplacés (receiver hors Learn/TvService/Activation) | S | PRÊT |
| w2-09 | `sonnet-w2-09-phone-troubleshooting.md` | `TvReachability` → raisons en français ; écran « Dépannage » unique ; ETA Bluetooth | M | PRÊT |
| w2-10 | `sonnet-w2-10-backend-shop-phase0.md` | Tables `shop_order`/`shop_price`, endpoints, page admin « Commandes », émission à la confirmation, secret maître serveur | L | BLOQUÉ (D8, D9) |
| w2-11 | `sonnet-w2-11-langues-tv.md` | Badge « voix de synthèse », limites en une ligne, focus initial | S | PRÊT |
| w2-12 | `sonnet-w2-12-content-quality-gate.md` | Porte qualité v1 (`LessonValidator` + `cbvalidate.py`), `index --lot`, vocabulaire d'état unique | M | PRÊT (D16 pour le vocabulaire) |
| w2-13 | `sonnet-w2-13-content-pipeline.md` | Registre de versions unique, `TRIAL-MANIFEST.json` commité, `publish-content.sh` strict | M | PRÊT |
| w2-14 | `sonnet-w2-14-core-hygiene.md` | Code mort, un seul JSON, analyseurs téléphone → cœur, drawables inutilisés | M | PRÊT |
| w2-15 | `sonnet-w2-15-tunnel-hardening.md` | Enrôlement réservé à `production`, portée `EXPERTS`, `notAfter` obligatoire, texte de divulgation | S | BLOQUÉ partiel (D13) |

## Vague 3 — échelle, architecture profonde, campagne (14 tâches)

| id | Cahier | Objet | Effort | Statut |
|---|---|---|---|---|
| w3-01 | `sonnet-w3-01-route-table.md` | `RouteTable` en cœur, `ReceiverServer` découpé (`UploadHandler`, `StorageApi`, `StreamHandler`) | L | PRÊT (après w2-07, w1-06) |
| w3-02 | `sonnet-w3-02-cold-start-fgs.md` | `ActivationCenter.init` hors fil principal, `scanFiles` sur `bg`, type de service d'avant-plan API 34, StrictMode debug | M | PRÊT (après w2-01, w2-07) |
| w3-03 | `sonnet-w3-03-ownedlots-catalog.md` | Catalogue signé livré à la TV, `OwnedLots.of(…, catalog)`, cap 3 Mo à la réception | M | PRÊT |
| w3-04 | `sonnet-w3-04-teacher-review-loop.md` | CSV par pack des 488 fiches embarquées, rapports QA par lot, import des décisions | M | PRÊT (D16) |
| w3-05 | `sonnet-w3-05-langues-mvp.md` | `languelib.py`, 18 unités A0-A1 d'une langue, `LangValidator` | L | BLOQUÉ (D14) |
| w3-06 | `sonnet-w3-06-programmes-70pct.md` | Format `docs/curriculum/programmes/*.md`, porte 70 % dans `cbvalidate.py` | M | BLOQUÉ partiel (D15) |
| w3-07 | `sonnet-w3-07-content-lycee.md` | 2nde +55 fiches, chimie/english/français/hist-géo 1re-Tle à 10-12 fiches | L | PRÊT (après w2-12) |
| w3-08 | `sonnet-w3-08-content-primaire-droit.md` | Class 1-5 French/Citizenship, droit L1-L2 +25 fiches, avertissements droit/santé | L | PRÊT (après w2-12) |
| w3-09 | `sonnet-w3-09-revocation-relay.md` | Téléphone relaie `GET /api/v1/revocations` vers la TV ; serveur compte les essais par appareil | M | PRÊT (après w2-01) |
| w3-10 | `sonnet-w3-10-release-keystore-process.md` | Procédure de keystore de release, migration du parc, tags, `SHA256SUMS` | S | BLOQUÉ (D12) |
| w3-11 | `sonnet-w3-11-app-tests-extraction.md` | `homeTools()`, `menuItems()`, aides Quiz → cœur avec tests ; écrans Quiz en fichiers | M | PRÊT (après w2-04) |
| w3-12 | `sonnet-w3-12-apk-slimming.md` | `-keep` affinés, `libvlc` vs `libvlc-all`, mesure APK v7a | M | PRÊT |
| w3-13 | `sonnet-w3-13-legal-drafts.md` | Brouillons CGV/CGU location, divulgation tunnel, consentement parent, crédits/licences | M | BLOQUÉ partiel (juriste) |
| w3-14 | `sonnet-w3-14-device-campaign.md` | Liste de contrôle de 40 étapes TV + téléphone, scripts `curl`/adb, `rental_test` sur vraie TV | M | PRÊT (propriétaire présent) |

## Matrice de propriété des fichiers (preuve de disjonction par vague)

Préfixes : `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = `backend/src/test/java/castbridge/server/`.

### Vague 1

| id | Fichiers possédés (modifiables) |
|---|---|
| w1-01 | `android/receiver/src/main/AndroidManifest.xml`, `android/receiver/src/main/res/xml/backup_rules.xml`, `android/receiver/src/main/res/xml/data_extraction_rules.xml`, `android/sender/src/main/AndroidManifest.xml`, `android/sender/src/main/res/xml/*`, `android/ownerlib/build.gradle.kts`, `android/ownerlib/src/main/kotlin/castbridge/owner/OwnerStore.kt` (commentaire), nouveau `tools/tests/test_backup_rules.py`, `docs/OWNER-CONSOLE.md` (§ sauvegarde) |
| w1-02 | `C/lots/RentalVault.kt`, `C/lots/TvLotStore.kt`, `C/lots/DeliveryQueue.kt`, `C/learn/Progress.kt`, `C/tv/Storage.kt`, `CT/lots/RentalTest.kt`, `CT/lots/TvLotStoreTest.kt` (ou équivalent existant), `CT/LearnLogicTest.kt` (section progression) |
| w1-03 | `C/tv/ReceiverServer.kt`, `C/tv/Security.kt`, `R/UpdateInstaller.kt`, `CT/TvHardeningTest.kt`, `CT/SecurityTest.kt`, `docs/ADMIN.md` (§ API) |
| w1-04 | `R/TvNetDiag.kt`, `R/TvService.kt` (zone `netTick` seulement), `R/TvPrefs.kt`, `C/dl/Aria2Config.kt`, `CT/dl/*`, `android/receiver/src/main/jniLibs/COPYING-aria2.txt` (nouveau), `docs/DOWNLOADS.md` |
| w1-05 | `C/owner/Keys.kt`, `C/owner/FeatureGate.kt` (`FleetMigration` seulement), `C/owner/Activation.kt` (`TvGate.evaluate` paramètre `clockDoubt` AHEAD seulement), `R/ActivationCenter.kt`, `R/PolicyHub.kt`, `android/receiver/build.gradle.kts` (bloc grâce), `CT/owner/ClockRollbackTest.kt`, `CT/owner/GraceMigrationTest.kt`, `docs/TRIAL-EDITION.md` (§ 14) |
| w1-06 | `C/owner/TrialPolicy.kt`, `CT/owner/TrialRoutesTest.kt`, nouveau `tools/routes/list_routes.py`, nouveau `tools/routes/routes.txt`, nouveau `tools/tests/test_routes.py` |
| w1-07 | `.github/workflows/*.yml`, nouveau `tools/requirements-dev.txt`, `docs/COORDINATION.md` (§ CI) |
| w1-08 | nouveau `docs/RELEASES.md`, `.gitignore`, nouveau `tools/release/sha256sums.sh`, `version.properties` (commentaire seulement) |
| w1-09 | `android/sshd/**` (main + test), `C/ssh/SshPolicy.kt`, `C/trust/TrustRegistry.kt`, `C/chess/ChessTransport.kt`, `CT/TrustTest.kt`, `CT/ChessRelayTest.kt`, `docs/HANDOFF.md` (ligne 13 seulement) |
| w1-10 | `tools/activation/verify_vectors.py`, `B/licenses/WireActivation.java`, `B/licenses/EnvelopeVerifier.java`, nouveau `BT/licenses/RentalVectorsTest.java`, `docs/ACTIVATION-FORMAT.md` (§ vecteurs) |
| w1-11 | `B/devices/**`, `B/telemetry/**`, `B/lots/BundleCatalogController.java`, `R/TvConnect.kt` (champ `deviceName`), `C/connect/*` (champ `deviceName`), `backend/src/main/resources/db/migration/README.md` (nouveau), `docs/LICENSE-ADMIN.md` (§ 3.6), `docs/TELEMETRY.md`, `BT/` tests correspondants |
| w1-12 | `ops/monitoring/**` (nouveau), `backend/backup.sh`, `backend/README.md`, `ops/tunnel/README.md` (non) |
| w1-13 | `docs/RENTAL-LOTS.md`, `docs/TRIAL-EDITION.md` (§ 9 seulement), `C/lots/RentalKeys.kt` (commentaires seulement), `docs/agent-reports/` |

### Vague 2

| id | Fichiers possédés |
|---|---|
| w2-01 | `R/ActivationCenter.kt`, `R/OwnerBtHost.kt`, `C/owner/Activation.kt`, `C/owner/Envelope.kt`, `C/owner/License.kt`, `C/owner/OwnerFrames.kt`, `C/owner/FeatureGate.kt` (`ActivationReceiver` seulement), `R/RentalHub.kt` (`ActivationInstallApi` seulement), `CT/owner/LicenseAndGateTest.kt`, nouveau `CT/owner/RevocationOnTvTest.kt`, `docs/ACTIVATION-FORMAT.md` (§ révocation TV) |
| w2-02 | `android/sshd/**`, `R/SshControl.kt`, `android/receiver/build.gradle.kts` (drapeau `castbridge.sshShell`), `docs/ADMIN.md` (§ SSH) |
| w2-03 | `C/owner/FeatureGate.kt` (`LockedTexts` seulement), nouveau `C/owner/RejectionTexts.kt`, `R/ActivationActivity.kt`, `R/ActivationScreenState.kt` (ou son emplacement réel), `S/ActivateTvActivity.kt`, `S/MainActivity.kt` (barre d'actions), `android/receiver/build.gradle.kts` (`OWNER_CONTACT`), `android/sender/build.gradle.kts` (`OWNER_CONTACT`), `CT/owner/LicenseAndGateTest.kt` (ligne du test de l'avis **seulement si** D7 fourni) |
| w2-04 | `C/owner/KeyBadge.kt`, `R/KeyBadgeOverlay.kt`, `R/PlayerActivity.kt`, `R/HomeScreen.kt`, `CT/owner/KeyBadgeTest.kt`, `android/receiver/src/main/res/values/cb_colors.xml` |
| w2-05 | `R/LearnActivity.kt`, `R/LearnViews.kt`, `R/LearnReader.kt`, `R/LearnHub.kt`, `C/learn/BaseContent.kt` (chaînes), `C/lots/RentalEngine.kt` (chaînes), `R/RentalHub.kt` (hors `ActivationInstallApi`), `C/lots/RentalSweeper.kt` (callback de notice) |
| w2-06 | nouveaux `S/shop/**`, `C/lots/ShopOrder.kt`, `C/lots/RentalDelivery.kt`, `S/RentalDeliveryActivity.kt`, `S/LotsRuntime.kt`, `CT/lots/ShopOrderTest.kt`, `docs/SHOP.md` (nouveau) |
| w2-07 | `R/TvService.kt`, nouveaux `R/TvNetMonitor.kt`, `R/TvExtraRoutes.kt`, `R/ScreenLauncher.kt`, `R/StorageEvents.kt`, `R/IconSync.kt` |
| w2-08 | nouveau `R/TvExecutors.kt`, `R/ChessActivity.kt`, `R/SudokuActivity.kt`, `R/ParentalUi.kt`, `R/HomeScreen.kt` (exécuteur seulement — coordonner : w2-04 possède le reste ; **si conflit, w2-08 ne touche pas HomeScreen**), `R/LibraryScreen.kt`, `R/TvCards.kt`, `R/DownloadsActivity.kt` |
| w2-09 | `S/TvHome.kt`, `S/UploadService.kt`, `S/TvPairScreen.kt`, `S/MyTvActivity.kt`, `S/BtRoutesScreen.kt`, nouveau `S/TroubleshootScreen.kt`, nouveau `C/tv/TvReachability.kt`, `CT/tv/TvReachabilityTest.kt` |
| w2-10 | `B/shop/**` (nouveau), `B/licenses/ActivationService.java` (point d'entrée réémission), `backend/src/main/resources/db/migration/V62__shop.sql`, `backend/src/main/resources/templates/admin/shop*.html`, `backend/src/main/resources/application.yml` (bloc `castbridge.shop`), `B/config/CastbridgeProperties.java`, `BT/shop/**`, `docs/API-SERVER.md` (§ boutique) |
| w2-11 | `R/LanguesActivity.kt`, `R/LanguesHub.kt`, `C/langues/LangPack.kt` (accesseur `synthetic`), `docs/LANGUES.md` (§ 11 ligne 1 vs § 13) |
| w2-12 | `C/learn/LessonValidator.kt`, `C/learn/LessonModel.kt`, `C/learn/LearnTool.kt`, `tools/content-validation/cbvalidate.py`, `tools/content-validation/test_cbvalidate.py`, `CT/LearnContentTest.kt`, `docs/CONTENT-VALIDATION.md`, `docs/LEARN.md` (§ validation) |
| w2-13 | `tools/trial-edition/**`, `tools/publish-content.sh`, `tools/content-lots/build_lots.py`, `tools/content-lots/make_release.py`, `tools/tests/test_content_tools.py`, `content/TRIAL-MANIFEST.json`, `content/LOT-VERSIONS.json`, `docs/CONTENT-PUBLISH.md` |
| w2-14 | `C/trust/ResilientCall.kt`, `C/learn/LearnQuiz.kt`, `C/curriculum/LevelPick.kt`, `C/owner/PhoneConsole.kt`, `C/xfer/Lanes.kt`, `C/xfer/Lane.kt`, `C/net/JsonLite.kt`, `C/quiz/Json.kt`, `C/dl/Json.kt`, `C/tv/Library.kt`, `S/TvLibraryScreen.kt`, `S/StoragePanel.kt`, `S/TvPlayerSettings.kt`, `C/tv/TvClient.kt`, `android/receiver/src/main/res/drawable*/` (16 fichiers listés), tests cœur correspondants |
| w2-15 | `B/tunnel/**`, `C/tunnel/ExpertsList.kt`, `C/owner/Keys.kt` (`KeyScope.EXPERTS`), `tools/activation-desktop/src/main/kotlin/castbridge/desktop/ExpertsStore.kt`, `CT/tunnel/**`, `BT/tunnel/**`, `docs/REMOTE-TUNNEL.md` |

### Vague 3

| id | Fichiers possédés |
|---|---|
| w3-01 | `C/tv/ReceiverServer.kt`, nouveaux `C/tv/RouteTable.kt`, `C/tv/UploadHandler.kt`, `C/tv/StorageApi.kt`, `C/tv/StreamHandler.kt`, `R/TvExtraRoutes.kt`, `CT/tv/**`, `CT/TvHardeningTest.kt` |
| w3-02 | `R/ActivationCenter.kt`, `R/TvService.kt`, `R/TvApp.kt`, `R/PlayerActivity.kt` (`onCreate` seulement), `android/receiver/src/main/AndroidManifest.xml` |
| w3-03 | `C/lots/OwnedLots.kt`, `C/lots/RentalApi.kt`, `C/lots/TvLotStore.kt`, `R/RentalHub.kt`, `C/lots/RentalDelivery.kt`, `CT/lots/**` |
| w3-04 | `content/validation/**` (nouveau), `content/qa/**` (nouveau), nouveau `tools/content-validation/review_export.py`, `docs/LEARN-REVIEW.md` (en-tête), `tools/pedagogy-report/**` |
| w3-05 | `content/langues/<lang>-a0-*`, `content/langues/<lang>-a1-*`, nouveau `tools/langues/languelib.py`, `tools/langues/test_languelib.py`, `C/langues/LangValidator.kt` (règles seulement), `content/langues/lots.json`, `content/langues/embedded.txt` |
| w3-06 | `docs/curriculum/programmes/**` (nouveau), `tools/content-validation/cbvalidate.py` (commande `programme`), `tools/content-validation/test_cbvalidate.py`, `docs/CONTENT-ARCHITECTURE.md` (§ porte) |
| w3-07 | `content/learn/2nde-*`, `content/learn/1ere-*` (chimie, english, hist-géo), `content/learn/tle-*` (english, français, chimie), `content/learn/lots.json` (ces lots) |
| w3-08 | `content/learn/class1-*` … `class5-*` (French, Citizenship), `content/learn/droit-l1-*`, `content/learn/droit-l2-*`, `content/learn/scopes.txt` (ces lignes), `content/learn/lots.json` (ces lots) |
| w3-09 | nouveau `S/RevocationRelay.kt`, `C/connect/ServerLink.kt` (route révocations), `B/licenses/PublicLicenseController.java`, `B/licenses/AbuseService.java`, `BT/licenses/**`, `CT/connect/**` |
| w3-10 | `docs/RELEASES.md` (§ signature), nouveau `tools/release/migrate-signing.md`, `docs/HANDOFF.md` (§ 6) |
| w3-11 | `R/PlayerActivity.kt`, `R/QuizActivity.kt`, nouveaux `R/QuizScreens/*.kt`, nouveaux `C/tv/HomeTools.kt`, `C/quiz/QuizText.kt`, `CT/tv/HomeToolsTest.kt`, `CT/QuizTextTest.kt`, `R/ParentalHub.kt` (`TRIAL_CLOSED_LABELS` → ids) |
| w3-12 | `android/receiver/build.gradle.kts`, `android/receiver/proguard-rules.pro`, `android/sshd/build.gradle.kts`, `docs/RELEASES.md` (§ taille) |
| w3-13 | nouveaux `docs/legal/CGV-location.md`, `docs/legal/DIVULGATION-tunnel.md`, `docs/legal/CONSENTEMENT-parent.md`, `docs/legal/CREDITS-licences.md`, `docs/TELEMETRY.md` (§ 7) |
| w3-14 | nouveau `docs/TEST-CAMPAIGN.md`, `tools/rental-test/**`, nouveau `tools/device/**`, `docs/HANDOFF.md` (§ 9) |

## Ordre de lancement conseillé

1. **Jour 1** : w1-01, w1-02, w1-03, w1-06, w1-07, w1-08, w1-13 (tous S) en parallèle ; puis w1-04, w1-05, w1-09, w1-10, w1-11, w1-12.
2. **Fusion vague 1** par le coordinateur (`:core:test`, `:sshd:test`, `./mvnw test`, vecteurs Python) ; compilation `:receiver`/`:sender` ; installation sur la TV de référence.
3. **Jour 3-4** : w2-01, w2-02, w2-05, w2-07, w2-08, w2-09, w2-11, w2-12, w2-13, w2-14 ; w2-03, w2-04 dès D6/D7 ; w2-06, w2-10, w2-15 dès D8/D9/D13.
4. **Fusion vague 2** ; campagne w3-14 avec le propriétaire (bloque la vague 3 côté TV).
5. **Jour 7+** : w3-01, w3-03, w3-04, w3-07, w3-08, w3-09, w3-11, w3-12 ; w3-02 après w3-01 ; w3-05, w3-06, w3-10, w3-13 dès les décisions.
