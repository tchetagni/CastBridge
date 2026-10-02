# Vague 12 pour agents Sonnet/Haiku — index (2026-10-02) : réglages signés et phase de « teasing » (expérimentation des valeurs du produit sans recompiler)

Source : `docs/coordination/DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` (lire § 0, § 2 et les § cités par chaque cahier avant de commencer). Protocole et règles communes : `docs/COORDINATION.md` et l'en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, textes en français, dire « CastBridge » (téléphone) / « CastBridge-TV »). Ce fichier ne modifie pas les index des vagues 4-11 ; le § « Amendements aux cahiers W4-W11 » liste ce que la vague 12 change **sans les éditer**.

**Décision du propriétaire qui fonde la vague (2026-10-02)** : « le premier lancement sera teasing pour les réglages » : tout réglage commercial ou de comportement doit être modifiable **sans recompiler** (la TV reste hors ligne : les valeurs lui arrivent par le téléphone, signées), **mesuré avec consentement** et **réversible**. Le juridique est reporté au 2026-12-31 : aucun cahier n'écrit de texte juridique.

**Modèle d'exécution recommandé par cahier** (règle de `docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md` § 1 : haiku = forme mécanique avant/après, C = 0, E = 0 ; audit Opus sur tout diff C = 2) : `haiku` = w12-11, w12-12 ; `sonnet` = tout le reste ; **audit Opus obligatoire** = w12-01, w12-03, w12-05, w12-07, w12-09 (signature, séquence, routes, canal Bluetooth, bornes €). Chaque cahier porte l'en-tête de routage (`<!-- routage Fable 2026-10-02 -->`) ; les lignes `routing.json` proposées sont au § Routage (fichier non possédé par W12 : le coordinateur les ajoute). Efforts en agent·jours (S ≈ 0,5-1, M ≈ 1,5-2, L ≈ 2,5-3) ; coût API estimé de la vague ≈ 9 $ (jauges, non vérifiées).

**Interrupteurs** : serveur `CASTBRIDGE_SETTINGS_ENABLED` (éteint par défaut) ; applications : sans document (ou sans clés de confiance), **défauts compilés**, comportement identique à aujourd'hui ; document `reset=all` = retour aux défauts partout. Aucun drapeau Gradle nouveau (le téléphone gagne `TRUSTED_KEYS`, tolérant à l'absence du fichier : w12-06).

**Prérequis fusionnés** : aucun (la vague tient seule sur ce qui est dans `integration/agents` le 2026-10-02 : enveloppe `cbx1`, `PolicyEngine`, `OrdersRuntime`, module serveur `orders`, console propriétaire, outil de bureau). **Vérifié absent** le 2026-10-02 : `tools/prices`, `content/prices.json`, `C/shop`, `C/tokens`, `C/sales`, `S/shop`, `S/focal`, `C/owner/PhoneGate.kt`, `C/owner/DegradedPolicy.kt`, `B/shop`, `B/agents` (W4-C, W5, W6 : conçus, non fusionnés). **Souhaités** : w12-02 et w12-10 donnent les expériences ; w12-09 donne le chemin Bluetooth (sans lui, Wi-Fi local et clé USB suffisent).

## Quatre sous-vagues ; fichiers disjoints à l'intérieur d'une sous-vague

| Sous-vague | Objet | Cahiers | Effort |
|---|---|---|---|
| **12a — cœur** (JVM, testé, vecteurs) | schéma à bornes, type `settings`, vérificateur, moteur, façade `Settings` ; expériences et cohortes, `exp`, `settings_applied` | w12-01 d'abord (contrat), puis w12-02 | ≈ 5 j |
| **12b — serveur et outils** | API + migration + signature + audit ; page `/admin/settings` ; miroir Python + signeur de secours | w12-03 et w12-05 en parallèle (après w12-01) ; w12-04 après w12-03 | ≈ 6,5 j |
| **12c — applications** | téléphone (tirage, relais, import) ; TV (routes, USB, consommateurs, diagnostic) ; émetteurs (console, bureau, CLI, serveur) ; Bluetooth + `PolicyHub` | w12-06, w12-07, w12-08 en parallèle (après w12-01) ; w12-09 après w12-07 | ≈ 7,5 j |
| **12d — mesure et docs** | KPI expériences ; `REGLAGES.md` + 6 docs ; campagne + CI + calendrier | w12-10 après w12-03 ; w12-11 après la tranche 1 ; w12-12 dès maintenant (CI après w12-05) | ≈ 3 j |

Total ≈ **22 agent·jours**, 12 cahiers. **Première tranche** (§ 7 de la conception, ≈ 11 j) : w12-01 → {w12-03, w12-06, w12-07, w12-08} → w12-04.

## Les 12 cahiers

| id | Cahier | Objet | Effort | Modèle | Audit Opus | Tranche 1 | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|
| w12-01 | `sonnet-w12-01-settings-core.md` | `SettingsSchema` (43 clés, bornes, défauts = constantes), `SettingsDoc`/`SettingsIssuer`, `SettingsVerifier` (12 étapes), `SettingsEngine`/`State`, façade `Settings`, vecteurs `settings-vectors.json`, `tools/settings/schema.json` | L | sonnet | **oui** | **oui** | PRÊT | — |
| w12-02 | `sonnet-w12-02-experiments-cohorts.md` | `Cohort.arm`, `Subject.resolve` (licence → agent → local), `Experiments.effective/arms/expProp`, propriété `exp` (usage seulement), `settings_applied` essentiel | M | sonnet | échantillon | non | PRÊT | w12-01 ; `Telemetry.kt` après w11-04/14, w10-07 |
| w12-03 | `sonnet-w12-03-server-settings-api.md` | module `B/settings/**` : miroir Java, `OrderSigner` (clé `POLICY`), seq sous verrou, migration `V<plus haut + 1>__settings.sql`, routes publiques et admin, audit chaîné, registre d'expériences, `settings_device` | L | sonnet | **oui** | **oui** | PRÊT | w12-01 |
| w12-04 | `sonnet-w12-04-admin-settings-page.md` | `/admin/settings` : courants, brouillon à bornes, diff + impact, signer/publier (TOTP), historique/retour arrière, interrupteur, parc par version, grilles (affichage) | M | sonnet | non | **oui** | PRÊT | w12-03 ; `lic-nav.html` après w5-09/w10-06 |
| w12-05 | `sonnet-w12-05-python-mirror-offline-signer.md` | `verify_vectors.py` (section `settings`), `tools/settings/{sign_settings,read_settings,settingslib}.py` (secours hors ligne : clé maîtresse du bureau, USB, QR) | M | sonnet | **oui** | non | PRÊT | w12-01 |
| w12-06 | `sonnet-w12-06-phone-settings-runtime.md` | `SettingsRuntime` (6 h, cache, `TRUSTED_KEYS` tolérant), relais Wi-Fi vers la TV, adoption inverse, import fichier/collage/QR, consommateurs tranche 1, ligne À propos | M | sonnet | échantillon | **oui** | PRÊT | w12-01 ; `PhoneConnect.kt`/`ConnectScreens.kt` après w11-07/09 |
| w12-07 | `sonnet-w12-07-tv-settings-hub.md` | `SettingsHub`, `GET /api/settings`, `POST /api/settings/install`, fichier USB `settings`, `BudgetArbiter` (réglage vs `budget.set`), consommateurs (lots, appairage, télémétrie, badge, message), ligne « Réglages », `routes.txt`, `TrialPolicy` | L | sonnet | **oui** | **oui** | PRÊT | w12-01 ; fichiers partagés après w11-10/11/12, w10-08/09, w5-04, w4-07, w3-02, w4-08, w6-12 |
| w12-08 | `sonnet-w12-08-issuers-read-settings.md` | console téléphone, bureau (`reglages-serveur`), `OwnerCli --reglages`, serveur `ActivationService` : lecture de `trial.*`, `rental.defaultDays`, `price.variant` ; « Réglages v<seq> » ; avertissement périmé | M | sonnet | échantillon | **oui** | PRÊT | w12-01 ; w12-05 souhaité ; après w4-02, w4-12, w5-14, w6-19 |
| w12-09 | `sonnet-w12-09-tv-bluetooth-policy-wiring.md` | trames 16-21 → `PolicyHub` ; `PolicyHub.init` + horloge ; aiguillage `order`/`settings` ; `PolicyGate.effective` ; « À propos > Politiques et réglages appliqués » | L | sonnet | **oui** | non | PRÊT | w12-07 ; après w2-01, w4-15, w6-12, w11-11 |
| w12-10 | `sonnet-w12-10-server-experiments-kpi.md` | `exp` et `settings_applied` dans `EventCatalog`, `kpi_experiment_day`, `/admin/kpi/experiments`, CSV, « clore » ⇒ brouillon, `ExperimentSaleSource` (vide tant que W4-C/W5 absents) | M | sonnet | échantillon | non | PRÊT | w12-03 ; w12-02 ; `EventCatalog.java` après w5-16/w10-07 |
| w12-11 | `sonnet-w12-11-docs-reglages.md` | `docs/REGLAGES.md` + `ACTIVATION-FORMAT` § 3.5 + `API-SERVER`, `TELEMETRY`, `ORDRES`, `ADMIN`, `HANDOFF` | S | haiku | non | après | PRÊT | rapports de la tranche 1 |
| w12-12 | `sonnet-w12-12-campaign-ci.md` | `TEST-CAMPAIGN.md` § W12 (≥ 30 étapes), CI `tools/settings`, `CALENDRIER-TEASING-W12.md` | S | haiku | non | non | PRÊT | — (CI après w12-05) |

## Matrice de propriété (preuve de disjonction par sous-vague)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `OL/` = `android/ownerlib/src/main/kotlin/castbridge/owner/`, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = tests serveur, `TPL/` = gabarits, `D/` = `docs/`.

### 12a
| id | Fichiers possédés |
|---|---|
| w12-01 | nouveaux `C/settings/{SettingsSchema,SettingsDoc,SettingsVerifier,SettingsEngine,SettingsState,Settings}.kt`, `CT/settings/{SettingsSchemaTest,SettingsDocTest,SettingsVerifierTest,SettingsEngineTest,SettingsVectorsTest}.kt`, `tools/activation/settings-vectors.json`, `tools/settings/schema.json` |
| w12-02 (après w12-01) | nouveaux `C/settings/{Experiments,Cohort,Subject}.kt`, `CT/settings/{ExperimentsTest,CohortTest,SubjectTest}.kt` ; `C/telemetry/Telemetry.kt` (zone `exp`, `settings_applied`), `C/settings/Settings.kt` (**une** méthode `bindSubject`), `D/TELEMETRY.md` § 4 (deux lignes) |

### 12b
| id | Fichiers possédés |
|---|---|
| w12-03 | nouveaux `B/settings/{SettingsProperties,SettingsSchema,SettingsEnvelope,SettingsService,SettingsController,AdminSettingsController,SettingsAudit,SettingsDeviceSink}.java`, `BT/settings/*` (5 tests), `db/migration/V<n>__settings.sql`, `src/main/resources/settings/schema.json` (copie) ; `application.yml` (bloc), `backend/README.md` (§), `B/telemetry/TelemetryService.java` (**une** ligne), `backend/.env.example` |
| w12-05 | nouveaux `tools/settings/{sign_settings,read_settings,settingslib,test_sign_settings,test_read_settings}.py`, `tools/settings/README.md` ; `tools/activation/verify_vectors.py` (section), `tools/tests/test_verify_vectors.py` |
| w12-04 (après w12-03) | nouveaux `B/settings/AdminSettingsPage.java`, `TPL/settings{,-draft,-diff,-history,-fleet,-experiments}.html`, `BT/settings/AdminSettingsPageTest.java` ; `TPL/lic-nav.html` (un lien) |

### 12c
| id | Fichiers possédés |
|---|---|
| w12-06 | nouveaux `S/settings/{SettingsRuntime,SettingsSyncJob,SettingsRelay,SettingsImport,SettingsMessageCard}.kt`, `C/settings/SettingsTransport.kt`, `CT/settings/SettingsTransportTest.kt` ; `S/PhoneConnect.kt` (une ligne), `S/ConnectScreens.kt` (`AboutSection`), point de liaison `/api/hello` côté téléphone (**un** crochet), `android/sender/build.gradle.kts` (`TRUSTED_KEYS`), `TelemetryUploader` téléphone (cadence) |
| w12-07 | nouveaux `R/SettingsHub.kt`, `R/SettingsRoutes.kt`, `C/settings/BudgetArbiter.kt`, `CT/settings/BudgetArbiterTest.kt`, `CT/owner/TrialRoutesSettingsTest.kt` ; `R/TvService.kt` (routes), `R/ActivationCenter.kt` (veilleur `settings`), `R/LotsHub.kt` (`maxBytes`), `R/PlayerActivity.kt` (`showSettings()` : une ligne), `C/owner/KeyBadge.kt` ou `R/KeyBadgeOverlay.kt` (texte), `C/trust/PairingSession.kt` (`blockMs`), `C/owner/TrialPolicy.kt` (allowlist), `tools/routes/routes.txt`, `TelemetryUploader` TV (cadence) |
| w12-08 | `OL/ConsoleActivity.kt` (onglet Activer), `OL/OwnerStore.kt` (`settings=`), `DK/Cli.kt`, `DK/Desk.kt`, nouveau `DK/SettingsStore.kt` (+ test), `C/owner/OwnerCli.kt`, `C/lots/RentalDurations.kt` (+ test), `B/licenses/ActivationService.java` (**une** lecture), `BT/licenses/ActivationServiceSettingsTest.java`, `D/OWNER-CONSOLE.md` (§), `D/ACTIVATION-TOOLS.md` (§) |
| w12-09 (après w12-07) | `C/owner/OwnerChannel.kt` (trames 16-21), `R/OwnerBtHost.kt`, `R/PolicyHub.kt`, `C/policy/OrderFrames.kt` (`settingsSeq` additif), `C/policy/OrderCourier.kt` (si possible sans `S/OrdersRuntime.kt`), `R/ActivationCenter.kt` (**une** composition `PolicyGate.effective` : **après** w12-07, même fichier), point d'init TV (`R/TvApp.kt` : une ligne), `R/PlayerActivity.kt` (une entrée de menu : **après** w12-07), nouveau `R/PoliciesAboutActivity.kt`, `CT/policy/{FrameRoutingTest,OrderFramesSettingsSeqTest}.kt`, `CT/owner/OwnerChannelOrdersTest.kt`, `D/ORDRES.md` § 13 |

Disjonction 12c : w12-06 (téléphone), w12-07 (TV + 3 fichiers cœur nommés), w12-08 (console, bureau, CLI, serveur licences) ne partagent aucun fichier ; w12-09 touche `R/ActivationCenter.kt` et `R/PlayerActivity.kt` **après** w12-07 (séquentiel), et ne touche pas `C/settings/**`.

### 12d
| id | Fichiers possédés |
|---|---|
| w12-10 (après w12-03) | `B/telemetry/EventCatalog.java`, `B/telemetry/TelemetryService.java` (**une** ligne, autre que celle de w12-03), nouveaux `B/telemetry/{ExperimentKpiService,ExperimentSaleSource,NoSalesSource,AdminExperimentKpiController}.java`, `B/admin/AdminKpiPages.java` (une route), `TPL/kpi-experiments.html`, `db/migration/V<n>__kpi_experiments.sql`, `BT/telemetry/*` (3 tests), `D/TELEMETRY.md` § 5 |
| w12-11 | nouveau `D/REGLAGES.md` ; `D/ACTIVATION-FORMAT.md` (§ 3.5, § 11.1), `D/API-SERVER.md`, `D/TELEMETRY.md` (§ 4-5 : **après** w12-02 et w12-10), `D/ORDRES.md` (§ 2, § 13 : **après** w12-09), `D/ADMIN.md`, `D/HANDOFF.md` |
| w12-12 | `D/TEST-CAMPAIGN.md` (§ W12), `.github/workflows/tools.yml`, `tools/requirements-dev.txt`, `D/COORDINATION.md` (§ CI), nouveau `docs/coordination/CALENDRIER-TEASING-W12.md` |

## Graphe de dépendances

```
w12-01 ──┬── w12-02 ──────────────────────────┐
         ├── w12-03 ──┬── w12-04               ├── w12-10 ── w12-11 (seconde passe)
         │            └── (w12-10 : migration) ┘
         ├── w12-05 ── (CI : w12-12)
         ├── w12-06
         ├── w12-07 ── w12-09
         └── w12-08
w12-12 (campagne) : dès maintenant ; w12-11 (docs) : après la fusion de la tranche 1 (w12-01, 03, 04, 06, 07, 08)
```

## Ordre de lancement conseillé

1. **Jour 0** : w12-12 (campagne et calendrier, haiku) et **w12-01** (seul : il fixe le contrat de tous les autres). Décisions D-W12-1…8 du propriétaire avant la fin de w12-01 (la recommandation s'applique par défaut).
2. **Après w12-01 fusionné (audit Opus)** : w12-03, w12-05, w12-06, w12-07, w12-08 en parallèle (cinq exécutants, fichiers disjoints) ; w12-02 en parallèle **si** `Telemetry.kt` n'est pas réservé par W10/W11.
3. **Après w12-03** : w12-04 ; **après w12-07** : w12-09 ; **après w12-03 et w12-02** : w12-10.
4. **Après la tranche 1 fusionnée** : w12-11 (première passe) ; serveur de **préproduction** (port 7091, clé de test) : publier **seq 1 = défauts** ; TV de référence : campagne § W12 ; **puis seulement** le serveur de production (`tools/release/deploy-server.sh`, règles `docs/coordination/VERSIONING-DEVOPS-2026-10-02.md`), interrupteur éteint jusqu'à la vérification du parc.
5. **Après w12-09, w12-10** : w12-11 (seconde passe).

Limite de parallélisme Gradle : `tools/agents/gradle-lock.sh` (ROUTAGE § 6) ; au plus un cahier TV compilant à la fois (w12-07 puis w12-09).

## Vagues W4-W11 : fichiers partagés et ordre (aucune n'est fusionnée le 2026-10-02)

| Fichier | Cahiers d'autres vagues | Cahier W12 | Règle |
|---|---|---|---|
| `C/telemetry/Telemetry.kt` | w11-04, w11-14, w10-07, w5-16 | w12-02 | w12-02 **après** eux, ou fusion |
| `R/TvService.kt`, `R/ActivationCenter.kt`, `R/PlayerActivity.kt` | w11-10/11/12, w10-08/09, w3-02, w4-08, w6-12, w6-14, w7-13 | w12-07, w12-09 | W12 **après** ; w12-09 après w12-07 |
| `C/owner/TrialPolicy.kt`, `tools/routes/routes.txt` | w5-04, w4-07, w10-08, w6-15, w8-10 | w12-07 | après ; régénérer `routes.txt` en dernier |
| `R/OwnerBtHost.kt`, `C/owner/OwnerChannel.kt`, `R/PolicyHub.kt` | w2-01, w4-15, w6-12 | w12-09 | après |
| `OL/ConsoleActivity.kt`, `OL/OwnerStore.kt`, `DK/*`, `C/owner/OwnerCli.kt` | w4-02, w4-12, w5-14, w6-19 | w12-08 | après |
| `B/telemetry/EventCatalog.java` | w5-16, w10-07 | w12-10 | après |
| `TPL/lic-nav.html` | w5-09, w10-06 | w12-04 | un lien, après |
| `S/PhoneConnect.kt`, `S/ConnectScreens.kt` | w11-07, w11-09, w6-16 | w12-06 | après |
| `android/sender/build.gradle.kts` | w4-13, w6-11 | w12-06 | `TRUSTED_KEYS` : si w4-13 l'a déjà ajouté, **ne pas doubler** |
| `tools/activation/verify_vectors.py` | w1-10, w4-05, w5-05, w6-10 | w12-05 | section additive |
| migrations Flyway | w1-11 (V62 ?), w2-10/W5 (V62/63), W4-C, W10 | w12-03, w12-10 | **plus haut + 1 au lancement**, `git log --all` |

Rien de W12 ne dépend **fonctionnellement** de W4-W11 ; les clés W5/W10 sont déclarées dans le schéma avec leurs défauts de conception pour que ces vagues les lisent à leur fusion.

## Amendements aux cahiers W4-W11 (sans les éditer : à lire comme un amendement)

Règle : « là où le cahier code la valeur, lire `Settings.int(<clé>)` avec cette valeur comme défaut compilé ; si `castbridge.core.settings` n'est pas fusionné, garder la constante **dans un seul endroit nommé** ». Tableau complet : conception § 8. Les plus importants :
- **w5-01, w5-14** : la grille de prix ne porte **plus** de lignes `set=tokens.*` (D-W12-3) ; `sign_prices.py` n'émet que `price=` et, en option, l'en-tête `variant=<id>`.
- **w10-02, w10-14** : `set=works.*` sortent de la grille ; `releve.py --parts/--seuil` lit `tools/settings/read_settings.py --json`.
- **w5-04, w5-06, w5-17, w5-18** : `quiz.tasterPerDay`, `rental.maxOnlineDays`, `trial.lotsWindow*`, `rental.maxConcurrent`, `rental.freeReissues`, `tokens.*` via `Settings`.
- **w6-02, w6-03** : `proof.validityDays`, `proof.staleDays` via `Settings` ; **`graceDays = 14` du téléphone reste compilé** (invariant).
- **w7-06, w7-14, w11-10** : `pairing.autoWindowMin`, `pairing.knockWindowMin`, `ui.idleDimMin` via `Settings`.
- **w4-11, w4-12** : `delegation.*`, `rental.maxOnlineDays` via `Settings` ; les durées de clé vendables restent celles de la grille (argent).
- **w11-02** : le texte « 12 h » du badge dérive de `trial.lotsWindowMinutes` (w12-07 le fait si w11-02 est déjà fusionné ; sinon w11-02 appelle `Settings.int`).

## Décisions prises par l'architecte (renversables, appliquées par défaut)
Enveloppe `cbx1` type `settings` ; portée `POLICY` ; cible `any` seulement ; `seq` propre aux réglages ; validité 90 j (1 h–366 j) ; instantané complet, idempotent ; clé inconnue ignorée, hors bornes ignorée et journalisée, signature fausse = rien ; expiration = valeurs gardées, expériences arrêtées, émetteurs prudents ; interrupteur `reset=all` ; prix hors du document (grille `variant`) ; `tokens.*`/`works.*` hors de la grille ; cohorte licence → agent → local, la TV fait foi ; bras jamais affichés ; `settings_applied` essentiel ; module serveur éteint par défaut ; téléphone propriétaire **ne signe pas** de réglages.

## Questions au propriétaire (aucune ne bloque la tranche 1 : la recommandation s'applique par défaut)

| # | Question | Recommandation |
|---|---|---|
| D-W12-1 | Signataires : clé serveur `POLICY` (console web, TOTP) + clé maîtresse du bureau en secours ; jamais le téléphone propriétaire ni un agent | oui |
| D-W12-2 | Les prix restent dans la grille signée hors ligne ; le document choisit une grille (`variant`) | oui |
| D-W12-3 | `tokens.*`, `works.*` sortent de la grille vers le document | oui |
| D-W12-4 | Grâce, 48 h, listes blanches : jamais à distance | oui |
| D-W12-5 | Méthode d'expérience au pilote : séquentiel + par point focal ; A/B par licence à ≥ 200 foyers | oui |
| D-W12-6 | Les 10 clés de la tranche 1 ; Langues défaut **500 Mo** (code : 2048) | oui, 500 |
| D-W12-7 | Expiration : valeurs gardées + expériences arrêtées + émetteurs prudents | oui |
| D-W12-8 | Première valeur testée en S3 | seq 1 = défauts en S1, puis `trial.defaultDays` 14 j en S3 |

## Routage (lignes à ajouter à `routing.json` par le coordinateur)

| id | Modèle | Pourquoi | Jauge | Groupe | Porte | Audit |
|---|---|---|---|---|---|---|
| w12-01 | sonnet | nouveau type d'enveloppe, bornes €/🔒, vecteurs | 800 k / 40 k | W12-a | `:core:test --tests 'castbridge.core.settings.*'` | oui |
| w12-02 | sonnet | cohortes, vie privée, télémétrie | 400 k / 20 k | W12-a | idem + `*Telemetry*` | échantillon |
| w12-03 | sonnet | signature serveur, seq sous verrou, migration | 800 k / 40 k | W12-b | `./mvnw -o test -Dtest='Settings*Test'` | oui |
| w12-04 | sonnet | pages admin, TOTP en appel seulement | 400 k / 20 k | W12-b | `-Dtest='AdminSettingsPage*Test'` | non |
| w12-05 | sonnet | signeur hors ligne Python, miroir | 400 k / 20 k | W12-b | `verify_vectors.py` + `unittest tools/settings` | oui |
| w12-06 | sonnet | tâche de fond, relais, clés de confiance du téléphone | 400 k / 20 k | W12-c | `:core:test` + `:sender:compileDebugKotlin` | échantillon |
| w12-07 | sonnet | routes TV, veilleur USB, `TrialPolicy` | 800 k / 40 k | W12-c | `:core:test` + `:receiver:compileDebugKotlin` + `test_routes.py` | oui |
| w12-08 | sonnet | trois outils d'émission + serveur | 400 k / 20 k | W12-c | `:core:test` + desktop `test` + `:ownerlib:compileDebugKotlin` | échantillon |
| w12-09 | sonnet | canal Bluetooth, `PolicyHub`, gate | 800 k / 40 k | W12-c | `:core:test --tests '*Policy*'` + `:receiver:compileDebugKotlin` | oui |
| w12-10 | sonnet | catalogue d'événements, agrégats, page | 400 k / 20 k | W12-d | `-Dtest='ExperimentKpi*Test,EventCatalog*Test'` | échantillon |
| w12-11 | haiku | docs depuis les rapports | 60 k / 5 k | W12-d | `ls docs/REGLAGES.md …` | non |
| w12-12 | haiku | campagne, CI, calendrier | 60 k / 5 k | W12-d | YAML valide + `grep -c '^- \[ \]'` | non |
