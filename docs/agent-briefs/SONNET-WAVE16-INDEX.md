# Vague 16 pour agents Sonnet/Haiku — index (2026-10-03) : location à durée choisie par l'utilisateur (heures d'utilisation ≤ 96 h, ou jours, défaut 30 jours), contenu livré chiffré, pilote gratuit de 3 semaines

Source : `docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md` (lire § 0, § 1 et le § cité par chaque cahier). Protocole et règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »), gabarits `EXECUTOR-PROMPT-TEMPLATE.md`. Routage : en-tête de trois lignes sur chaque cahier (`ROUTAGE-AGENTS-EXECUTION-2026-10-02.md`) ; `routing.json` : lignes proposées au § Routage (le coordinateur les ajoute).

**Décisions du propriétaire (2026-10-02/03)** : durée à l'initiative de l'utilisateur ; contenu livré chiffré avec les données de location ; test gratuit de 3 semaines pour tous les contenus ; **96 h = plafond de la location à l'heure** (temps de lecture réel par location) ; **30 jours = sans durée indiquée** (validité en jours, sans budget) ; **unités = heures et jours**. Lecture appliquée : trois choix dans un sélecteur (« Sans durée précise : 30 jours » · jours 1/3/7/14 · heures d'utilisation 1/3/6/12/24/48/96) ; la ligne `rental` existante porte l'unité par ses deux plafonds ; aucun format nouveau.

**Règles W16** : **(R1)** test rouge d'abord, horloges et carnets factices (JVM), aucun appareil réel sauf la liste humaine du propriétaire ; **(R2)** pendant le gel W15, **aucun** cahier ne touche `R/`, `S/` : w16-10 et w16-11 attendent la sortie (plan § 5) et w15-17 / w15-16 ; **(R3)** audit Opus obligatoire sur w16-01, 02, 04, 06, 08, 10, 11 (format de contrat, compteur, émission, clés, livraison) ; **(R4)** vecteurs v1/v2 jamais modifiés ; **(R5)** aucun montant, aucun texte juridique ; **(R6)** la TV du propriétaire n'est jamais la cible d'un script.

**Modèle d'exécution** : `haiku` = docs, kit, campagne (w16-12, 13, 14) ; `sonnet` = le reste ; audits Opus : 7.

## Les 14 cahiers

| id | Cahier | Objet | Tranche | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w16-01 | `sonnet-w16-01-rental-engine-units.md` | `RentalUnit` (heures/jours/essai), clamp 96 h dans `contracts()`, phrases et alertes par unité, badge | 16a | M | sonnet | **oui** | 350 / 18 | PRÊT | — |
| w16-02 | `sonnet-w16-02-use-meter-ledger.md` | `UseMeter` pur (minute entière, report, pause 5 min, inactivité 30 min, monotone), carnet : attribution par lot, mesure sans plafond, relevé | 16a | M | sonnet | **oui** | 400 / 20 | PRÊT | w16-01 |
| w16-03 | `sonnet-w16-03-rental-api-report.md` | `GET /api/rental` additif, `GET /api/rental/usage`, `TvRentalView`, `RentalUsageReport` (fusion monotone, stockage téléphone) | 16a | M | sonnet | échantillon | 350 / 18 | PRÊT | w16-01, 02 |
| w16-04 | `sonnet-w16-04-pilot-rules-issuing.md` | `PilotRules` (trois choix, plafonds, borne de sûreté, quotas, prolongation sans mélange, fin de pilote), `RightsSyntax.rentalChoice`, `RentalDurations.checkChosen`, registre | 16a | M | sonnet | **oui** | 450 / 22 | PRÊT | w16-01 |
| w16-05 | `sonnet-w16-05-desk-pilot-tools.md` | bureau `--location-choix`, `--pilote`, `--registre`, `louer`, `rapport-usage` ; `tools/pilot/{louer,bilan,rentalops}.py` | 16b | M-L | sonnet | échantillon | 500 / 25 | PRÊT | w16-03, 04 |
| w16-06 | `sonnet-w16-06-pilot-vectors-audit.md` | `rental-pilot-vectors.json` rejoué Kotlin/Java/Python, liste de contrôle d'audit | 16a | S-M | sonnet | **oui** | 400 / 20 | PRÊT | w16-01…04 |
| w16-07 | `sonnet-w16-07-telemetry-rental-events.md` | `rental_start/use/end/extend/survey` (usage, consentement) cœur + serveur + doc | 16a | S | sonnet | non | 200 / 10 | PRÊT | — (après w11-04/w11-14/w10-07 s'ils sont lancés) |
| w16-08 | `sonnet-w16-08-server-pilot-module.md` | module `castbridge.pilot` éteint : commandes prix 0, clé aléatoire sous KEK, activation signée serveur, lots scellés, relevés, réémission | 16c | L | sonnet | **oui** | 700 / 35 | PRÊT (pilote payant) | w16-04, 06 ; w4-05 |
| w16-09 | `sonnet-w16-09-admin-pilot-page.md` | `/admin/pilot/rentals` (TOTP), KPI HP1-HP10, CSV, réémission | 16c | M | sonnet | échantillon | 350 / 18 | PRÊT | w16-08 |
| w16-10 | `sonnet-w16-10-tv-meter-wiring.md` | TV : compteur par lot réel (Apprendre, Quiz), toutes locations mesurées, phrases, bandeau, question de fin, télémétrie, parcours J | 16d | M | sonnet | **oui** | 450 / 22 | **ATTEND** sortie du gel + w15-17 | w16-01, 02, 03, 07 |
| w16-11 | `sonnet-w16-11-phone-rent-screen.md` | téléphone : écran « Louer » (trois groupes, date de fin réelle), relevé automatique et partage, file, client pilote | 16d | L | sonnet | **oui** | 600 / 30 | **ATTEND** sortie du gel + w15-16 | w16-03, 04, 07 (08 ou `FakePilotApi`) |
| w16-12 | `sonnet-w16-12-docs-rental-pilot.md` | `RENTAL-LOTS.md` § 17, `ACTIVATION-TOOLS.md` § 9, `LOTS.md`, `HANDOFF.md` § 0 | 16b | S | haiku | non | 150 / 10 | PRÊT (après 16a + w16-05) | w16-01…05 |
| w16-13 | `sonnet-w16-13-pilot-kit.md` | procédure, fiche foyer, journal CSV, questionnaire, question de fin, LISEZMOI, affiche | 16b | S | haiku | non | 120 / 12 | PRÊT | — |
| w16-14 | `sonnet-w16-14-campaign-ci.md` | `gate-w16.sh`, job `pilot-tools`, plan de test humain `RENTAL-PILOT.md` | 16b | S | haiku | non | 100 / 10 | PRÊT (après tranche 1) | w16-01…07, 12, 13 |

Coûts (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées**) : sonnet 11 × ≈ 1 $ ≈ 10,5 $ ; haiku 3 × ≈ 0,15 $ ≈ 0,5 $ ; audits Opus 7 × ≈ 0,7 $ ≈ 5 $. **Total ≈ 15-16 $**, ≈ **22 agent·jours** (16a ≈ 8, 16b ≈ 4,5, 16c ≈ 4,5, 16d ≈ 5) ; **tranche 1 (16a + 16b) ≈ 12,5 j**, ≈ 1,5-2 semaines calendaires avec deux exécutants.

## Tranches

| Tranche | Cahiers | Livre | Parallélisme |
|---|---|---|---|
| **16a** (cœur) | w16-01 → (w16-02 ∥ w16-04 ∥ w16-07) → w16-03 → w16-06 | moteur, compteur, routes, règles d'émission, vecteurs, télémétrie | 3 après w16-01 |
| **16b** (outils, docs) | w16-05 (après 03, 04) ∥ w16-13 ; puis w16-12 ∥ w16-14 | émission et livraison par le bureau, bilan, kit, campagne | 2 |
| **16c** (serveur, pendant le gel, pour le pilote payant) | w16-08 → w16-09 | module pilote éteint, console | 1 |
| **16d** (apps, après la sortie du gel) | w16-10 ∥ w16-11 | compteur par lot, écran « Louer », relevé automatique | 2 (fichiers disjoints) |

**Tranche 1 du pilote = 16a + 16b** (≈ 12,5 j), jouable avec le module licences éteint, l'écran téléphone existant et **une location à la fois par TV** (`maxConcurrent = 1` dans `pilot.json` tant que w16-10 n'est pas sur la TV ; les locations en jours ne sont pas mesurées avant w16-10). Build TV verrouillé avec le cœur W16 : décision **D-W16-9**.

## Matrice de propriété (preuve de disjonction)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = receiver, `S/` = sender, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`, `B/` = `backend/src/main/java/castbridge/server/`, `TPL/` = `backend/src/main/resources/templates/`.

| id | Fichiers possédés |
|---|---|
| w16-01 | `C/lots/RentalEngine.kt`, `C/owner/KeyBadge.kt`, nouveau `CT/lots/RentalUnitsTest.kt` |
| w16-02 | nouveaux `C/lots/UseMeter.kt`, `CT/lots/UseMeterTest.kt`, `CT/lots/RentalLedgerUsageTest.kt` ; `C/lots/RentalLedger.kt` (additif) |
| w16-03 | `C/lots/RentalApi.kt`, `C/lots/RentalDelivery.kt` (zones `TvRentalView`, `status()`), nouveaux `C/lots/RentalUsageReport.kt`, `CT/lots/RentalUsageReportTest.kt` ; `CT/lots/{RentalApiTest,RentalDeliveryTest}.kt` |
| w16-04 | nouveaux `C/lots/PilotRules.kt`, `C/lots/PilotRegistry.kt`, `CT/lots/{PilotRulesTest,PilotRegistryTest}.kt` ; `C/lots/RentalDurations.kt` (additif), `C/owner/LicensedIssuer.kt` (zone `RightsSyntax`), `CT/lots/RentalDurationsTest.kt` |
| w16-05 | `DK/Cli.kt` (zones `issue`, `sealLot`, aide, nouvelles commandes), `DK/Desk.kt` (zone), tests du bureau ; nouveaux `tools/pilot/**`, `tools/tests/test_pilot_louer.py`, `tools/tests/test_pilot_bilan.py` |
| w16-06 | nouveaux `tools/activation/rental-pilot-vectors.json`, `C/lots/RentalPilotVectors.kt`, `CT/lots/RentalPilotVectorsTest.kt`, `backend/src/test/java/castbridge/server/licenses/RentalPilotVectorsTest.java`, `docs/coordination/AUDIT-W16-CHECKLIST.md` ; `tools/activation/verify_vectors.py` (section additive) |
| w16-07 | `C/telemetry/Telemetry.kt` (zone `EVENTS`), `CT/TelemetryTest.kt`, `B/telemetry/EventCatalog.java` (zone), test serveur, `docs/TELEMETRY.md` § 4 |
| w16-08 | nouveaux `B/pilot/**`, migration `V<n>__pilot_rentals.sql`, tests `castbridge/server/pilot/**` ; `application.yml` (bloc `castbridge.pilot`) |
| w16-09 | nouveaux `B/pilot/AdminPilotPage.java`, `B/pilot/PilotKpiService.java`, `TPL/admin/pilot-rentals.html`, tests ; `TPL/admin/lic-nav.html` (un lien) |
| w16-10 | `R/RentalHub.kt` (zone `meter*`), `R/LearnActivity.kt` (zone `meter`), `R/QuizActivity.kt` (zone compteur), nouveaux `R/RentalEndPrompt.kt`, `CT/journey/RentalJourneyTest.kt` ; écran des locations TV (zone phrases) |
| w16-11 | nouveaux `C/lots/PilotClient.kt`, `CT/lots/PilotClientTest.kt`, `S/RentalPickerScreen.kt`, `S/RentalRuntime.kt` ; `S/RentalDeliveryActivity.kt` (zone affichage), `S/LotsScreen.kt` (une entrée) |
| w16-12 | `docs/RENTAL-LOTS.md` (§ 17, renvois), `docs/ACTIVATION-TOOLS.md` (§ 9), `docs/LOTS.md` (une ligne), `docs/HANDOFF.md` (§ 0) |
| w16-13 | nouveaux `docs/pilot/**`, `content/pilote/**`, `tools/tests/test_pilot_kit.py` |
| w16-14 | nouveaux `tools/agents/gate-w16.sh`, `docs/test-plans/RENTAL-PILOT.md` ; `.github/workflows/tools.yml` (un job), `docs/test-plans/README.md` (une ligne) |

Disjonction : `RentalEngine.kt` = w16-01 seul ; `RentalLedger.kt` = w16-02 seul ; `RentalApi.kt`/`RentalDelivery.kt` = w16-03 seul ; `LicensedIssuer.kt` = w16-04 seul (zone) ; `Telemetry.kt` = w16-07 seul ; `DK/*` = w16-05 seul ; `B/pilot/**` : w16-08 puis w16-09 (série) ; `R/**` = w16-10 seul ; `S/**` = w16-11 seul ; `C/owner/Keys.kt`, `R/TvService.kt`, `S/LotsRuntime.kt`, `C/lots/DeliveryQueue.kt`, `RentalLines.kt`, vecteurs v1/v2 : **personne**.

## Graphe de dépendances
```
16a : w16-01 ─┬─ w16-02 ─┐
              ├─ w16-04 ─┼─ w16-03 ─ w16-06 (audit) ─┐
              └─ w16-07 ─┘                            │
16b : w16-05 (après 03, 04) ∥ w16-13 ; w16-12 ∥ w16-14 (après 16a + 05) ─► tranche 1 ─► D-W16-9 build TV ─► S0 du pilote (05-11/10)
16c : w16-08 (après 04, 06, w4-05) ─ w16-09                                  (pendant le gel, pour le pilote payant)
16d : [sortie du gel W15 ; w15-17, w15-16] ─ w16-10 ∥ w16-11
```

## Interaction avec W13 · W14 · W15 (gel) et les vagues conçues
- **Pendant le gel** : 16a, 16b, 16c seulement (plan de stabilisation § 6 : « cœur seul peut continuer » ; serveur et outils autorisés). **R5** : aucun fichier `R/`, `S/` ; `C/owner/Keys.kt` (w15-17) et `C/lots/DeliveryQueue.kt` (w15-16) ne sont pas touchés.
- **Après la sortie** : w16-10 après w15-17 (zone `TvClock` de `R/RentalHub.kt`) ; w16-11 après w15-16 (`S/LotsRuntime.kt`) ; parcours J (W14) obligatoires pour les deux ; w16-10 avant w7-13 s'il est lancé (crochets `RentalHub.sweep/install`).
- **W5/W10/W12** : amendements d'en-tête ajoutés à w4-02, w5-04, w5-06, w5-11, w5-12, w10-02, w10-13, w12-01, w12-08 (voir DESIGN-W16 § 4.3) ; les cahiers ne sont pas réécrits.
- **Fichiers partagés à ordonner** : `C/telemetry/Telemetry.kt` (w16-07 après w11-04/w11-14/w10-07 s'ils sont lancés, sinon avant et ils se rebasent d'une ligne) ; `TPL/admin/lic-nav.html` (w16-09 après w5-09/w10-06) ; migrations (`V<plus haut + 1>` au moment de la fusion).

## Ordre de lancement conseillé
1. **Jour 0** : décisions D-W16-1…12 (surtout D-W16-5 borne absolue, D-W16-9 build TV) ; lancer **w16-01** (sonnet) et **w16-13** (haiku) ; audit Opus de w16-01.
2. **Jour 1** : fusion w16-01 ; lancer **w16-02**, **w16-04**, **w16-07** en parallèle ; audits de 02 et 04.
3. **Jour 3** : fusion ; lancer **w16-03** ; puis **w16-06** (audit) et **w16-05**.
4. **Jour 5** : fusion de la tranche 1 ; **w16-12**, **w16-14** (haiku) ; `gate-w16.sh` vert ; `:core:test` complet + `compileDebugKotlin` × 2 ; **build TV verrouillé** (D-W16-9) ⇒ liste humaine `RENTAL-PILOT.md` sur la TV de référence ⇒ `Download` de la clé.
5. **Semaine S0 (05-11/10)** : pilote préparé ; **w16-08** → **w16-09** en parallèle (serveur, pour après).
6. **Sortie du gel** : **w16-10** ∥ **w16-11** (audits) ; APK complètes pour le pilote payant.

## Décisions prises par l'architecte (renversables ; détail DESIGN-W16 § 7)
Trois choix dans un sélecteur ; unité portée par les deux plafonds de la ligne (aucun champ) ; 30 jours = plafond des jours ; 96 h au sélecteur ; un contrat par bouquet ; prolongation sans mélange d'unités ; clamp TV ; borne de sûreté des heures = fin + 14 j ; jours honorés en entier (D-W16-5) ; `cooldownMin = 0`, quota 192 h / 7 j ; relevé non signé au pilote ; serveur éteint pendant le pilote ; Apprendre seul (W10 non fusionné).

## Questions au propriétaire (DESIGN-W16 § 7.1-7.2)
D-W16-1 sélecteur (**oui**) · D-W16-2 96 h à l'heure seulement, jours sans budget (**oui**) · D-W16-3 un contrat par bouquet, changer d'unité après la fin (**oui**) · D-W16-4 3 / 3 / 192 h / 0 (**oui**) · D-W16-5 borne absolue : heures ≤ 15/11, jours honorés ≤ 01/12 (**honorer**) · D-W16-6 Apprendre seul (**oui**, BLOQUÉ calendrier W10) · D-W16-7 ce qui compte (**oui**) · D-W16-8 question sans montant (**oui**) · D-W16-9 build TV pendant le gel (**oui**) · D-W16-10 serveur après (**oui**) · D-W16-11 télémétrie (**oui**) · D-W16-12 `userChosen` après le pilote (**au bilan**).

## Routage (lignes proposées pour `routing.json`, à ajouter par le coordinateur)
```
w16-01 sonnet opus-audit | w16-02 sonnet opus-audit | w16-03 sonnet opus-sample | w16-04 sonnet opus-audit | w16-05 sonnet opus-sample | w16-06 sonnet opus-audit | w16-07 sonnet - | w16-08 sonnet opus-audit | w16-09 sonnet opus-sample | w16-10 sonnet opus-audit (ATTEND) | w16-11 sonnet opus-audit (ATTEND) | w16-12 haiku - | w16-13 haiku - | w16-14 haiku -
```
