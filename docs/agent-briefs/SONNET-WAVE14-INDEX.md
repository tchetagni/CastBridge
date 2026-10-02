# Vague 14 pour agents Sonnet/Haiku — index (2026-10-02) : « Barrière anti-régression » (parcours JVM, fumée scriptée, discipline de remise)

Source : `docs/coordination/DESIGN-W14-BARRIERE-ANTI-REGRESSION-2026-10-02.md` (lire § 0-1 avant tout cahier ; § 3 = couche J, § 2 = fumée, § 4 = remise, § 5 = processus). Parcours : `docs/test-plans/PARCOURS-CRITIQUES.md` ; liste humaine : `docs/test-plans/CHECKLIST-HUMAINE-LIVRAISON.md`. Protocole et règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »), gabarits `EXECUTOR-PROMPT-TEMPLATE.md` (A Haiku, B Sonnet, C Opus audit). Ce fichier ne modifie pas les index des vagues 1-13 ; le § « Changements aux cahiers antérieurs » liste ce que W14 impose à W13 et W7.

**Signal fondateur (propriétaire, 2026-10-02)** : « Il y a trop de régression » : cinq défauts de terrain en un jour (PIN à ressaisir sans message ; « Aucune TV ajoutée » avec une TV connectée ; téléphone de confiance cassé par « Réassocier » ; TV sans progression ; activation/Bluetooth/permissions), ~2 100 tests JVM verts, 0 test dans `:sender`/`:receiver`, 0 parcours rejoué avant la remise d'une APK.

**Place de W14** : **avant W13 et W7, et avant toute nouvelle vague de fonctions.** W13 (12 cahiers) et W7 (25) modifient exactement les fichiers où les régressions naissent (`C/trust`, `C/xfer`, `S/UploadService`, `S/TvLink`, `R/TvService`) ; sans la couche J et la fumée, chacun de leurs cahiers est une régression possible. Ordre : **W14-S1 → W13-S1 → W14-S2/S3 (en parallèle de W13-S2) → W7**.

**Modèle d'exécution par cahier** : `haiku` = docs, CI, scripts à contenu donné ; `sonnet` = le reste ; **audit Opus obligatoire** sur w14-01 (harnais = preuve de toute la vague) et w14-05 (fonctions pures = vérité de la puce, des notifications, des clés de PIN) ; échantillon sur w14-02/03/04/06. Efforts en agent·jours.

## Trois tranches ; fichiers disjoints à l'intérieur d'une tranche

| Tranche | Objet | Cahiers | Effort | Coût estimé (exécution) |
|---|---|---|---|---|
| **S1 — à livrer d'abord** | harnais J, 20 parcours (liaison/PIN, transfert/progression, Ouvrir avec/activation/formats), fonctions pures + câblage des écrans + lint de pureté, journal des régressions | w14-01, 02, 03, 04, 05, 06, 14 | ≈ 9 j → **≈ 6,5 j** en parallèle (3 groupes) | ≈ 11 $ + audit ≈ 2,5 $ |
| **S2 — fumée** | fausse TV du Mac, squelette `tools/smoke/`, pas téléphone (adb), pas TV (API, relais lecture seule), marqueurs `CB_JOURNEY`, migration en place | w14-10, 07, 08, 09, 11, 13 | ≈ 7 j → ≈ 4 j | ≈ 7 $ |
| **S3 — discipline** | candidate, `handover.sh`, retour arrière, CI + porte locale | w14-12, 15 | ≈ 1,5 j | ≈ 0,6 $ |

## Les 15 cahiers

| id | Cahier | Objet | Effort | Modèle | Audit Opus | Jauge (entrée / sortie, k jetons) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|
| w14-01 | `sonnet-w14-01-journey-harness-core.md` | `JourneyKit`, `TvSim` (vraie `ReceiverServer` + registre + `TransferHost` + HELLO `FakeTv`), `PhoneSim` (`LinkDriver` branché en HTTP réel, `PinStore` pur, envoi), 3 tests de fumée du harnais | M+ | sonnet | **oui** | 450 / 30 | PRÊT | — |
| w14-02 | `sonnet-w14-02-journey-link-pin-tests.md` | J-01…J-11 : association, TV réinstallée, Réassocier (abandon), téléphone réinstallé, PIN juste/faux/tourné/absent, verrou, jeton 12 h | M | sonnet | échantillon | 350 / 22 | PRÊT | w14-01 |
| w14-03 | `sonnet-w14-03-journey-transfer-tests.md` | J-12…J-18 : copie LAN petite/grosse avec progression **des deux côtés**, Bluetooth, annulation, redémarrage TV, essai sans boucle, plus de place | M | sonnet | échantillon | 350 / 22 | PRÊT | w14-01 |
| w14-04 | `sonnet-w14-04-journey-openwith-activation-formats.md` | J-19 (Ouvrir avec sur 8 états **réels**), J-20 (activation vue du téléphone), fixtures de formats persistés, `UpdateRules` pur | M | sonnet | échantillon | 400 / 25 | PRÊT | w14-01 |
| w14-05 | `sonnet-w14-05-pure-ui-state-core.md` | `HomeLinkView`, `ManualTvView`, `PairScreenView`, `ActivateTargetView`, `PinKeys`, `XferTexts`, `ReceiveCard` : décisions des écrans extraites à l'identique, table-testées | M+ | sonnet | **oui** | 450 / 28 | PRÊT | — |
| w14-06 | `sonnet-w14-06-screens-wiring-purity-lint.md` | écrans et services câblés sur les fonctions pures ; `UpdateInstaller`/`BtServer`/`HomeScreen` ; `UiStatePurityTest` + liste blanche datée | M | sonnet | échantillon | 500 / 24 | PRÊT | w14-04, w14-05 |
| w14-07 | `sonnet-w14-07-smoke-skeleton-report.md` | `tools/smoke/smoke.py` : modes `fake`/`emu`/`real-ro`, verrou, budget < 10 min, preuves, `REPORT.md` d'une page | M | sonnet | non | 250 / 20 | PRÊT | — (w14-10 pour l'usage réel) |
| w14-08 | `sonnet-w14-08-smoke-phone-checks.md` | pas téléphone : installation, PIN, Ouvrir avec (3 états), copie avec notification échantillonnée, permissions ; parseurs `dumpsys`/`uiautomator`/logcat | M | sonnet | non | 350 / 22 | PRÊT | w14-07, w14-10 |
| w14-09 | `sonnet-w14-09-smoke-tv-checks-readonly.md` | pas TV : API, empreinte avant/après en **lecture seule** sur la vraie TV (relais `nc`, `cbdev status`), écran pendant la copie, redémarrage et Apprendre sur émulateur | S-M | sonnet | non | 300 / 18 | PRÊT | w14-07 |
| w14-10 | `sonnet-w14-10-fake-tv-cli.md` | `FakeTvMain` : la fausse TV sacrifiable du Mac (scénarios `fresh`/`reinstalled`/`pin-rotated`/`trial`/`full`/`slow`/`busy`, routes `/__smoke/*`), tâche `:core:fakeTv`, `fake_tv.sh` | M | sonnet | non | 300 / 18 | PRÊT | w14-01 |
| w14-11 | `sonnet-w14-11-logcat-journey-markers.md` | marqueurs `CB_JOURNEY` (18 étapes, sans secret, ≤ 1/s) dans les deux apps + lint de présence | S | sonnet | non | 300 / 14 | PRÊT | w14-06 |
| w14-12 | `sonnet-w14-12-release-candidate-rollback.md` | `candidate.sh`, `handover.sh` (refuse sans fumée PASS + liste humaine), `rollback-plan.sh`, `docs/RELEASES.md` § 15-16 | S | haiku | non | 120 / 10 | PRÊT | w14-07 |
| w14-13 | `sonnet-w14-13-migrate-in-place-test.md` | `migrate_test.py` : N-1 → N sur émulateur, empreinte d'état avant/après, deux chemins d'installation TV, rétrogradation 409 | M | sonnet | non | 350 / 20 | PRÊT | w14-07 (w14-10 souhaité) |
| w14-14 | `sonnet-w14-14-regressions-log-rules.md` | `docs/REGRESSIONS.md` (R-01…R-05), règle « test rouge d'abord », portes par type de cahier dans `COORDINATION.md` et les gabarits | S | haiku | non | 100 / 10 | PRÊT | — |
| w14-15 | `sonnet-w14-15-ci-gate-wiring.md` | `tools/checks/gate.sh`, job `journeys` (android.yml), tests Python de la fumée (tools.yml), § CI | S | haiku | non | 80 / 6 | PRÊT | w14-01…07 |

Coûts (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**) : sonnet 12 × ≈ (0,7 + 0,2) ≈ 11 $ ; haiku 3 × ≈ 0,15 ≈ 0,5 $ ; audits Opus 2 obligatoires + ≈ 2 échantillons × (150 k / 10 k) ≈ 3,2 $. **Total ≈ 15-19 $.**

## Matrice de propriété (preuve de disjonction par tranche)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = `android/core/src/test/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`.

### S1
| id | Fichiers possédés |
|---|---|
| w14-01 | nouveaux `CT/journey/{JourneyKit,TvSim,PhoneSim,Scenario,ActivationApiSim,HarnessSmokeTest}.kt` |
| w14-02 | nouveaux `CT/journey/{LinkJourneyTest,PinJourneyTest}.kt` |
| w14-03 | nouveau `CT/journey/TransferJourneyTest.kt` |
| w14-04 | nouveaux `CT/journey/{OpenWithJourneyTest,ActivationJourneyTest,PersistenceCompatTest}.kt`, `android/core/src/test/resources/fixtures/formats/**`, `C/update/UpdateRules.kt`, `CT/update/UpdateRulesTest.kt` |
| w14-05 | nouveaux `C/trust/{HomeLinkView,ManualTvView,PairScreenView,ActivateTargetView,PinKeys}.kt`, `C/xfer/{XferTexts,ReceiveCard}.kt`, `CT/trust/{HomeLinkViewTest,ManualTvViewTest,PairScreenViewTest,ActivateTargetViewTest,PinKeysTest}.kt`, `CT/xfer/{XferTextsTest,ReceiveCardTest}.kt` |
| w14-06 | `S/{TvHome,TvScreen,TvPairScreen,ActivateTvActivity,UploadService,TransferQueueService,BtUploadService,DownloadService,PinStore}.kt`, `R/{UpdateInstaller,BtServer}.kt`, `R/TvService.kt` (zone statut transferts), `R/HomeScreen.kt` (zone `headline`) ; nouveaux `CT/lint/UiStatePurityTest.kt`, `CT/lint/purity-allowlist.txt` |
| w14-14 | nouveau `docs/REGRESSIONS.md` ; `docs/COORDINATION.md` (section ajoutée), `docs/agent-briefs/EXECUTOR-PROMPT-TEMPLATE.md` (bloc ajouté) |

Ordre interne de S1 : **groupe a** w14-01 ∥ w14-05 ∥ w14-14 ; **groupe b** (après w14-01) w14-02 ∥ w14-03 ∥ w14-04 ; **groupe c** (après w14-04 et w14-05) w14-06.

### S2
| id | Fichiers possédés |
|---|---|
| w14-07 | nouveaux `tools/smoke/smoke.py`, `tools/smoke/core/{config,devices,lock,steps,evidence,report}.py`, `tools/smoke/checks/__init__.py`, `tools/smoke/README.md`, `tools/tests/test_smoke.py` |
| w14-08 | nouveaux `tools/smoke/checks/phone_{install,pin,openwith,copy,permissions}.py`, `tools/smoke/lib/{ui,notif,logcat,media}.py`, `tools/tests/test_smoke_phone.py`, `tools/tests/fixtures/smoke/*` |
| w14-09 | nouveaux `tools/smoke/checks/tv_{api,state_snapshot,screen,reboot,learn}.py`, `tools/smoke/lib/{relay,png,cbdev}.py`, `tools/tests/test_smoke_tv.py` |
| w14-10 | nouveaux `CT/journey/{FakeTvMain,SmokeControlApi,FakeTvMainTest}.kt`, `tools/smoke/fake_tv.sh` ; `android/core/build.gradle.kts` (tâche `fakeTv` seulement) |
| w14-11 | nouveaux `S/link/JourneyLog.kt`, `R/JourneyLog.kt`, `CT/lint/JourneyMarkersLintTest.kt` ; zones (une ligne par point) : `S/{TvLink,UploadService,TransferQueueService,BtUploadService,OpenWithActivity,ActivateTvActivity,PinStore}.kt`, `R/{TvService,BtServer,ActivationCenter,UpdateInstaller}.kt` |
| w14-13 | nouveaux `tools/smoke/migrate_test.py`, `tools/smoke/checks/migration.py`, `tools/smoke/lib/fingerprint.py`, `tools/tests/test_migrate.py` |

Ordre interne de S2 : **groupe d** (après w14-01) w14-07 ∥ w14-10 ; **groupe e** (après d) w14-08 ∥ w14-09 ∥ w14-13 ; **groupe f** (après w14-06 **fusionné**, car mêmes fichiers Android) w14-11. Disjonction : `S/UploadService.kt` et `R/BtServer.kt` sont à w14-06 (S1) puis à w14-11 (S2, groupe f, après fusion) : jamais en parallèle ; `tools/smoke/lib/` : fichiers distincts par cahier ; `tools/smoke/checks/__init__.py` : w14-07 seul (les autres **enregistrent** par import, sans l'éditer).

### S3
| id | Fichiers possédés |
|---|---|
| w14-12 | nouveaux `tools/release/{candidate,handover,rollback-plan}.sh`, `tools/tests/test_release_gate.py` ; `docs/RELEASES.md` (§ 15-16 ajoutés) |
| w14-15 | nouveau `tools/checks/gate.sh` ; `.github/workflows/{android,tools}.yml` (jobs ajoutés), `docs/COORDINATION.md` (§ CI : 6 lignes) |

`docs/COORDINATION.md` : w14-14 (S1, section « Barrière ») puis w14-15 (S3, § CI) : jamais en parallèle.

## Graphe de dépendances

```
w14-01 ─┬─► w14-02 ─┐
        ├─► w14-03 ─┼─► (S1 fusionnée) ─► w14-11 ─┐
        ├─► w14-04 ─┼─► w14-06 ◄── w14-05          │
        └─► w14-10 ─┬─► w14-08                      ├─► w14-15
w14-07 ─────────────┼─► w14-09                      │
                    ├─► w14-13                      │
                    └─► w14-12 ─────────────────────┘
w14-14 (indépendant, S1)
```

## Ordre de lancement conseillé

1. **Jour 0** : w14-01 (sonnet), w14-05 (sonnet), w14-14 (haiku) en parallèle ; audits Opus de w14-01 et w14-05 à leur rapport. Propriétaire : répondre à **B-W14-1** (téléphone réel utilisable par la fumée ?) et Q-W14-1/2/3.
2. **Jour 2** : w14-02, w14-03, w14-04 en parallèle (sonnet) ; w14-07 et w14-10 en parallèle (sonnet). Les tests `@Ignore("REGRESSION …")` produits ici alimentent `docs/REGRESSIONS.md`.
3. **Jour 4** : w14-06 (sonnet) ; **fusion S1** : `tools/checks/gate.sh --full` (ou `:core:test` complet), `:sender:compileDebugKotlin :receiver:compileDebugKotlin`, lint de pureté vert, `HANDOFF.md` § 0 et § 9 mis à jour **dans le même commit**. Dès S1 fusionnée : **W13-S1 peut partir** (voir « Changements » ci-dessous).
4. **Jour 5** : w14-08, w14-09, w14-13 en parallèle ; w14-12 (haiku).
5. **Jour 7** : w14-11 (après w14-06 fusionné) ; w14-15 (haiku) ; **première fumée réelle** par le coordinateur : `tools/smoke/fake_tv.sh --scenario fresh` + `smoke.py --tv fake --phone emu` sur `cbconnect_phone`, puis `--tv emu` sur `cbconnect_tv` avec la candidate TV verrouillée + clés de TEST ; `REPORT.md` cité dans `HANDOFF.md`.
6. **Première livraison sous barrière** : `candidate.sh`, fumée PASS, installation sur le téléphone du propriétaire, liste humaine 12 points par le propriétaire, `handover.sh` ⇒ clé USB. Toute APK remise avant ce point l'est **hors barrière** et doit être dite telle dans `HANDOFF.md`.

## Changements aux cahiers antérieurs (sans les éditer ; l'exécutant d'un cahier encore à lancer lit ceci d'abord)

| Cahier | Changement | Repris par |
|---|---|---|
| **w13-07** (`UploadService`, `BtUploadService`, `TransferQueue*`) | se **rebase** sur w14-06 : les textes de notification viennent de `XferTexts` (ajouter l'état `Blocked(blocker)` à `XferState` **dans le cœur**, pas dans le service) ; la `Notice` finale est obligatoire ; parcours J imposé : étendre `TransferJourneyTest` (J-08 passe de `@Ignore` à vert) | w14-05, w14-06, w14-03 |
| **w13-08** (`PinStore.putAll`, `PinPrompt`) | `putAll` = `PinKeys.keysOf` (w14-05) ; ne pas redéfinir les clés ; parcours J : `PinJourneyTest` J-06/J-08 | w14-05, w14-02 |
| **w13-09** (`TvLinkManager.health`, puce 3 niveaux, `TvHome`/`TvScreen`) | `HomeLinkView`/`ManualTvViews` (w14-05) restent la **seule** décision des écrans ; `LinkHealth` les alimente (champ `health` dans `HomeFacts`) ; le lint de pureté (w14-06) doit rester vert : toute nouvelle décision va dans `C/link` ou `C/trust` | w14-05, w14-06 |
| **w13-05** (`ReceiverServer` codes, PIN absent non compté) | parcours J-09 (`PinJourneyTest.missingPinAsksBeforeFirstByte`) doit passer de « comportement actuel » à « non compté » : mettre à jour l'assertion, pas la supprimer | w14-02 |
| **w13-10** (`BlockerCatalogueTest`, `ScriptedTv`) | réutiliser `TvSim` (w14-01) comme serveur scripté plutôt qu'un NanoHTTPD à part ; lint `RouteStatusLintTest` et `UiStatePurityTest` cohabitent dans `CT/lint`-`CT/link` | w14-01, w14-06 |
| **w13-06** (écran TV bandeau PIN, `HomeScreen`) | `ReceiveCards.headline` (w14-05) est la source du titre d'accueil ; le bandeau s'ajoute, ne remplace pas | w14-05, w14-06 |
| **w13-12** (CI contrôles de source) | `tools/checks/gate.sh` existe (w14-15) : y ajouter `check_w13_sources.sh`, ne pas créer un second point d'entrée | w14-15 |
| **w7-10** (`TransferLedger`) | absorbe `ReceiveCard` (w14-05) : même modèle, persistance en plus ; `TransferJourneyTest` J-12/J-14 sont ses tests d'acceptation | w14-05, w14-03 |
| **w7-16** (`LinkRuntime`, façade `TvLinkManager`) | `PhoneSim` (w14-01) doit pouvoir piloter `LinkManager` comme il pilote `LinkDriver` : exposer la même interface d'étape ; le transport HELLO **TCP** côté téléphone (debug) demandé par w14-10 est à livrer ici | w14-01, w14-10 |
| **w7-24** (campagne terrain) | repart de `docs/test-plans/PARCOURS-CRITIQUES.md` et de la liste humaine (12 points) : pas de troisième liste | — |
| **w7-19 / w7-20** (« Réparer », puce) | mêmes règles que w13-09 : décision dans le cœur, lint vert | w14-06 |
| **Tous les cahiers W13/W7** | un **parcours J nommé** (existant étendu ou nouveau dans `CT/journey/`) dans « Critères d'acceptation » ; rapport avec la ligne `FUMÉE:` ; fusion seulement avec `REPORT.md` PASS si `S/**` ou `R/**` est touché (`docs/COORDINATION.md` § Barrière) | w14-14 |

## Décisions prises par l'architecte (renversables ; détail § 7 de la conception)
D-W14-1 trois étages J/F/H ; D-W14-2 harnais sur `LinkFixtures` + vraie `ReceiverServer` (pas de faux HTTP) ; D-W14-3 la TV du propriétaire n'est jamais une cible active d'un script ; D-W14-4 fonctions pures + lint de pureté en `:core:test` ; D-W14-5 émulateur TV = AVD Android TV 34 arm64 existant, le 32 bits reste humain ; D-W14-6 candidate = même `versionCode`, nom `-rc.N`, promotion par rebuild du même commit ; D-W14-7 marqueurs `CB_JOURNEY` ; D-W14-8 W14-S1 avant W13/W7.

## Questions au propriétaire

| # | Question | Bloque | Recommandation |
|---|---|---|---|
| **B-W14-1** | Le téléphone réel (S21+, utilisateur principal) peut-il servir à la fumée automatique : `adb install -r` d'une candidate, partage de fichiers de test, lecture de `dumpsys`/`logcat`, **jamais** d'effacement ? | le mode `--phone real` de w14-08 (sinon émulateur seulement ; la liste humaine garde les points téléphone) | **oui**, lecture + `install -r` seulement |
| Q-W14-1 | Accepter l'émulateur Android TV arm64 comme substitut de la TV pour la fumée (le 32 bits = points 1-4 de la liste humaine) ? | rien (défaut : oui) | oui |
| Q-W14-2 | Qui exécute la liste humaine (15 min) à chaque livraison ? | la première livraison sous barrière | le propriétaire, ou un testeur désigné ; jamais le coordinateur seul |
| Q-W14-3 | Candidate nommée `-rc.N` (visible dans « Version ») puis rebuild sans suffixe, ou build unique promue telle quelle ? | w14-12 (défaut : `-rc.N` + rebuild) | `-rc.N` + rebuild du même commit |
