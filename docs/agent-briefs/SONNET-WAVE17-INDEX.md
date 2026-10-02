# Vague 17 pour agents Sonnet/Haiku — index (2026-10-03) : la Boutique sur le téléphone (CastBridge) et vue par la TV (CastBridge-TV), hors ligne, avec demande de location depuis la TV

Source : `docs/coordination/DESIGN-W17-STORE-TELEPHONE-ET-TV-2026-10-03.md` (lire § 0, § 2.3, § 4 et le § cité par chaque cahier). Protocole et règles communes : `docs/COORDINATION.md`, en-tête de `SONNET-WAVES-INDEX.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport vivant `docs/agent-reports/<id>.md`, jamais `main` ni le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »), gabarits `EXECUTOR-PROMPT-TEMPLATE.md`. Routage : en-tête de trois lignes sur chaque cahier ; `routing.json` : lignes proposées au § Routage (le coordinateur les ajoute).

**Décisions du propriétaire (2026-10-03)** : « Je ne vois pas le store » (aucun écran de boutique n'existe : seul « Données hors ligne » télécharge et envoie des lots) ; « Le store devrait être aussi vu par la TV ». Lecture appliquée : **une seule Boutique, deux rendus** ; le cœur décide, les écrans dessinent ; la TV reste hors ligne (vitrine = les deux catalogues signés existants, poussés par le téléphone) ; la TV **demande**, le téléphone **commande** (chemin W16), personne ne paie sur la TV ; aucun format signé nouveau ; aucun prix dans W17.

**Règles W17** : **(R1)** test rouge d'abord, horloges et fichiers factices (JVM), aucun appareil réel sauf la liste humaine du propriétaire ; **(R2)** pendant le gel W15, **aucun** cahier ne touche `R/`, `S/` : w17-06, 07, 08 attendent la sortie (ou l'exception D-W17-11, drapeau éteint par défaut) ; **(R3)** audit Opus obligatoire sur w17-03 (la demande ne vaut jamais un droit) ; échantillon sur w17-01, 02, 04, 05, 06, 07, 08 ; **(R4)** aucune décision d'état dans un écran (lint de pureté W14) ; **(R5)** aucun montant, aucun texte juridique, aucun secret ; **(R6)** la TV du propriétaire n'est jamais la cible d'un script ; **(R7)** drapeau `store.enabled` éteint ⇒ les deux applications sont **identiques à aujourd'hui** (parcours J-S0).

**Modèle d'exécution** : `haiku` = docs, plan de test (w17-09, 10) ; `sonnet` = le reste ; audit Opus : 1 obligatoire, 7 échantillons (≈ 1 sur 3 effectif).

## Les 11 cahiers

| id | Cahier | Objet | Tranche | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w17-01 | `sonnet-w17-01-store-catalog-core.md` | `StoreCatalog` : fusion lot-catalogue + bouquets ⇒ articles, rayons, alias, bornes de taille, mode dégradé | 17a | M | sonnet | échantillon | 400 / 20 | PRÊT | — |
| w17-02 | `sonnet-w17-02-store-view-state.md` | `StoreView` (états purs des articles, mêmes valeurs téléphone/TV), `StoreTexts` (toutes les phrases FR), `StoreFlag`, « Boutique » dans `KID_HOME`, `store` dans `TV_FEATURES`, `store.enabled` dans `FLAGS` | 17a | M | sonnet | échantillon | 400 / 20 | PRÊT | w17-01 |
| w17-03 | `sonnet-w17-03-rent-request-core.md` | `castbridge-rent-request-v1` : format, code court, file de la TV (refus enfant/essai/libre/quota/doublon, nonce, ack idempotent, expiration 7 j, rapprochement), vecteurs Kotlin + Python | 17a | M | sonnet | **oui** | 450 / 22 | PRÊT | w17-01 (w16-04 souhaité) |
| w17-04 | `sonnet-w17-04-store-api-core.md` | `StoreApi` : `GET /api/store`, `POST /api/store/catalog` (deux documents signés, anti-retour, `files/store/`), demandes, ack, dépôt ; `TrialPolicy` (listes), `routes.txt` | 17a | M | sonnet | échantillon | 400 / 20 | PRÊT | w17-01, 02, 03 |
| w17-05 | `sonnet-w17-05-journey-store.md` | 7 parcours J (W14) : drapeau éteint, catalogue relayé une fois, article sur la TV, demande TV → téléphone → location réelle, TV d'essai, enfant, téléphone absent 8 j ; `StoreSim`, `PhoneStoreSim` | 17a | M | sonnet | échantillon | 400 / 20 | PRÊT | w17-01…04 |
| w17-06 | `sonnet-w17-06-phone-store-runtime.md` | téléphone : `StoreRuntime` (catalogue des bouquets, cache, relais, relevé des demandes, notification, confirmation adulte, exécution W16) | 17c | M | sonnet | échantillon | 400 / 20 | **ATTEND** sortie du gel (ou D-W17-11) + w15-16 | w17-01…05 (w16-11 souhaité) |
| w17-07 | `sonnet-w17-07-phone-store-screen.md` | téléphone : écran « Boutique » (rayons, cartes, sélecteur W16 → demande, envoi existant, demandes de la TV, Ma TV), « Boutique » à la place de « Locations », bouton dans « Données hors ligne » | 17c | L | sonnet | échantillon | 800 / 40 | **ATTEND** sortie du gel (ou D-W17-11) | w17-02, 06 (w16-11 souhaité) |
| w17-08 | `sonnet-w17-08-tv-store-screen.md` | TV : tuile « Boutique », `StoreActivity` (colonne, grille 4 × 2, fiche, « Louer » = demande + code court, Mes locations), `StoreHub`, branchement d'une ligne dans `TvService`, essai/enfant en lecture | 17c | L | sonnet | échantillon | 800 / 40 | **ATTEND** sortie du gel (ou D-W17-11 : TV d'abord, lecture seule) + w15-17 | w17-02, 03, 04 (w16-01/10 souhaités) |
| w17-09 | `sonnet-w17-09-docs-store.md` | `docs/STORE.md`, renvois `LOTS.md`, `RENTAL-LOTS.md`, `HANDOFF.md` § 0 | 17b | S | haiku | non | 120 / 12 | PRÊT (après 17a) | w17-01…04 |
| w17-10 | `sonnet-w17-10-store-test-plan-gate.md` | `docs/test-plans/STORE-PILOT.md` (10 points humains), `tools/agents/gate-w17.sh` | 17b | S | haiku | non | 100 / 10 | PRÊT (après w17-05) | w17-01…05 |
| w17-11 | `sonnet-w17-11-desk-rent-request-tool.md` | `tools/pilot/rentreq.py` : lire/vérifier une demande (fichier ou code court), imprimer la commande `louer.py` W16 ; rejeu Python des vecteurs | 17b | S | sonnet | non | 150 / 8 | PRÊT (après w17-03) | w17-03 (w16-05 souhaité) |

Coûts (prix 2026-09 : haiku 1/5, sonnet 2/10, opus 4/20 $/M ; jauges **estimées, non vérifiées**) : sonnet M 6 × ≈ 1,0 $ = 6 $ ; sonnet L 2 × ≈ 2,0 $ = 4 $ ; sonnet S 1 × ≈ 0,4 $ ; haiku S 2 × ≈ 0,1 $ ; audit Opus obligatoire 1 × ≈ 0,7 $ ; échantillons ≈ 1,5 $. **Total ≈ 12,5-13 $**, ≈ **16 agent·jours** (17a ≈ 7, 17b ≈ 1,1, 17c ≈ 6 ; marge ≈ 2). **Pendant le gel : 17a + 17b ≈ 8 j, ≈ 6 $.**

## Tranches

| Tranche | Cahiers | Livre | Parallélisme |
|---|---|---|---|
| **17a** (cœur, pendant le gel) | w17-01 → w17-02 ∥ w17-03 → w17-04 → w17-05 | articles, états, phrases, demande, routes, 7 parcours J : **tout ce qui décide**, testé ; les écrans deviennent minces | 2 après w17-01 |
| **17b** (docs, campagne, outil, pendant le gel) | w17-11 (après 03) ∥ w17-09 (après 04) ∥ w17-10 (après 05) | doc, liste humaine, porte, outil du propriétaire | 3 |
| **17c** (écrans, après la sortie du gel ou exception D-W17-11) | w17-06 → w17-07 ∥ w17-08 | Boutique visible sur le téléphone **et** la TV, drapeau éteint par défaut, allumée par ordre signé | 2 (fichiers disjoints : `S/store/**` vs `R/Store*`) |

**Plus petite tranche visible sur les deux écrans** = 17a puis w17-06 → (w17-07 ∥ w17-08) : ≈ 13 j, ≈ 11 $ ; sans W5 (paiement), W10 (œuvres), W11 (navigation), W12 (réglages). L'exécution des demandes passe par **W16 tranche 1** (`louer.py`, bureau) ou **tranche 2** (module pilote, w16-08 + w16-11). Exception « TV d'abord » (D-W17-11) : w17-08 seul après 17a, en **lecture seule** (vitrine relayée par un script du propriétaire via le relais `nc` du téléphone, sans `S/`), ≈ 2,5 j.

## Matrice de propriété (preuve de disjonction)

Préfixes : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = receiver, `S/` = sender.

| id | Fichiers possédés |
|---|---|
| w17-01 | nouveaux `C/store/StoreCatalog.kt`, `C/store/StoreAlias.kt`, `CT/store/{StoreCatalogTest,StoreAliasTest}.kt`, `CT/store/fixtures/store-*.json` |
| w17-02 | nouveaux `C/store/{StoreView,StoreTexts,StoreFlag}.kt`, `CT/store/{StoreViewTest,StoreTextsTest,StoreFlagTest}.kt` ; une ligne dans `C/parental/ParentalModel.kt`, `C/telemetry/Telemetry.kt`, `C/policy/PolicyActions.kt` (+ une assertion dans leurs tests) |
| w17-03 | nouveaux `C/store/{RentRequest,RentRequests,RentRequestStore}.kt`, `CT/store/{RentRequestTest,RentRequestsTest,StoreVectorsTest}.kt`, `tools/activation/store-vectors.json` ; `tools/activation/verify_vectors.py` (section additive `store`) |
| w17-04 | nouveaux `C/store/{StoreApi,StoreFiles}.kt`, `CT/store/{StoreApiTest,StoreFilesTest}.kt`, `CT/owner/TrialRoutesStoreTest.kt` ; `C/owner/TrialPolicy.kt` (listes), `tools/routes/routes.txt` (5 lignes) |
| w17-05 | nouveaux `CT/journey/{StoreSim,StoreJourneyTest,PhoneStoreSim}.kt` ; `CT/journey/TvSim.kt` (zone additive ≤ 6 lignes) |
| w17-06 | nouveaux `S/store/{StoreRuntime,StoreNotifier,StoreSyncHook}.kt` ; `S/CastBridgeApp.kt` (une ligne) |
| w17-07 | nouveaux `S/store/{StoreActivity,StoreScreen,StoreItemCard,StoreRequestsScreen,StoreMyTvSection}.kt` ; `S/MainActivity.kt` (une action), `S/LotsScreen.kt` (un bouton), manifeste téléphone (une activité) |
| w17-08 | nouveaux `R/{StoreHub,StoreActivity,StoreViews}.kt`, `res/drawable/ic_t_store.xml` ; `R/PlayerActivity.kt` (une tuile), `R/TvService.kt` (une ligne), manifeste TV (une activité) |
| w17-09 | nouveau `docs/STORE.md` ; une ligne dans `docs/LOTS.md`, `docs/RENTAL-LOTS.md`, `docs/HANDOFF.md` |
| w17-10 | nouveaux `docs/test-plans/STORE-PILOT.md`, `tools/agents/gate-w17.sh` ; `docs/test-plans/README.md` (une ligne) |
| w17-11 | nouveaux `tools/pilot/rentreq.py`, `tools/pilot/README-rentreq.md`, `tools/tests/test_pilot_rentreq.py` |

Disjonction : chaque fichier `C/store/*.kt` a **un** propriétaire ; `TvSim.kt` = w17-05 seul (zone) ; `S/**` = w17-06 (runtime) et w17-07 (écrans), fichiers distincts ; `R/**` = w17-08 seul ; `TrialPolicy.kt`/`routes.txt` = w17-04 (**aussi dans la zone de w10-08, non lancé** : le second à partir se rebase) ; `S/LotsRuntime.kt`, `C/lots/**`, `C/tv/ReceiverServer.kt`, `R/LotsHub.kt`, `R/RentalHub.kt` : **personne** (lignes proposées au rapport).

## Graphe de dépendances
```
17a : w17-01 ─┬─ w17-02 ─┐
              └─ w17-03 ─┼─ w17-04 ─ w17-05 ─────────────────────┐
17b :            w17-11 (après 03) ∥ w17-09 (après 04) ∥ w17-10 (après 05)
17c : [sortie du gel W15 ou D-W17-11 ; w15-16, w15-17] ─ w17-06 ─ (w17-07 ∥ w17-08)
W16 : 16a (cœur) en parallèle de 17a (fichiers disjoints : C/lots vs C/store) ; 16d (w16-10/11) avant ou en parallèle de 17c (phrases par unité, sélecteur)
```

## Interaction avec W5 · W10 · W11 · W12 · W14 · W15 · W16
- **Pendant le gel** : 17a, 17b seulement (plan de stabilisation § 6 : « cœur seul peut continuer » ; tests, docs, outils autorisés). R2 : aucun fichier `R/`, `S/`.
- **Après la sortie** : w17-06 après w15-16 (`S/LotsRuntime.kt` zone `hasWork` : ligne proposée) ; w17-08 après w15-17 (`R/TvService.kt`, un seul cahier à la fois) ; parcours J (w17-05) = porte des trois cahiers d'écran ; fumée F `--tv fake` (téléphone) et `--tv emu` (TV) avant fusion.
- **Amendements d'en-tête** (les cahiers ne sont pas réécrits) : w5-11 (`StoreScreen` W17 = conteneur ; `ShopRuntimeView` étend `StoreRuntime`), w10-08 (`POST /api/store/catalog` porte aussi le catalogue des œuvres ; `/api/oeuvres/catalog` inutile), w10-09 (l'écran « Œuvres » = rayon de `StoreActivity`), w10-11 (onglet « Œuvres locales » dans `StoreScreen`), w16-10 (phrases/bandeau consommés par `StoreActivity › Mes locations`), w16-11 (« Louer » vit dans `StoreScreen` ; `PickerModel.confirm` ⇒ `RentRequest`).
- **W12** : `store.enabled` lu par `StoreFlag` (W12 › ordre signé `flag.set` › défaut compilé) ; aucune modification de w12-01 (la clé est déclarée à la fusion de W12, défaut 0 hors pilote).
- **Fichiers partagés à ordonner** : `C/owner/TrialPolicy.kt`, `tools/routes/routes.txt` (w17-04 ↔ w10-08) ; `C/telemetry/Telemetry.kt` (w17-02 après w16-07/w11-04/w10-07 s'ils sont lancés, sinon avant et ils se rebasent d'une ligne) ; `R/TvService.kt` (w17-08 après w15-17, jamais en parallèle de w7-xx/w10-08).

## Ordre de lancement conseillé
1. **Jour 0** : décisions D-W17-1…12 (surtout D-W17-11 : attendre la sortie du gel ou exception TV d'abord) ; lancer **w17-01** (sonnet).
2. **Jour 1** : fusion w17-01 ; lancer **w17-02** ∥ **w17-03** ; audit Opus de w17-03.
3. **Jour 3** : fusion ; lancer **w17-04**, puis **w17-11** (sonnet S) en parallèle.
4. **Jour 4** : fusion w17-04 ; lancer **w17-05** ; puis **w17-09**, **w17-10** (haiku).
5. **Jour 6** : 17a + 17b fusionnées ; `gate-w17.sh` vert ; `:core:test` complet + `compileDebugKotlin` × 2 (rien ne change dans les apps : vérifier que le diff `S/`, `R/` est vide).
6. **Sortie du gel** (ou exception) : **w17-06**, puis **w17-07** ∥ **w17-08** ; F PASS ; liste humaine `STORE-PILOT.md` 10/10 sur la TV de référence ; ordre signé `flag.set store.enabled=1` ; build TV verrouillé dans `Download` de la clé.

## Décisions prises par l'architecte (renversables ; détail DESIGN-W17 § 8.2)
Un seul cœur `C/store/` pour les deux écrans ; états d'article en table ; la demande ne vaut jamais droit ; nonce + ack idempotent ; ≤ 20 demandes en attente, 1 par (bouquet, choix), expiration 7 j ; `/api/store` lecture ouverte en essai, demandes fermées ; « Boutique » dans `KID_HOME` ; `store` dans `TV_FEATURES` ; tuile provisoire après `langues` ; aucune image au pilote ; vérification Ed25519 à la réception seulement ; aucun prix.

## Questions au propriétaire (DESIGN-W17 § 8.1)
D-W17-1 « Boutique » remplace « Locations » (**oui**) · D-W17-2 tuile TV provisoire (**oui**) · D-W17-3 file relevée + code court, pas de QR (**oui**) · D-W17-4 deux catalogues existants, aucun format nouveau (**oui**) · D-W17-5 grille 4 × 2 (**oui**) · D-W17-6 essai : vitrine visible (**oui**) · D-W17-7 enfant : vitrine visible, aucune demande (**oui**) · D-W17-8 demande du téléphone déposée sur la TV (**oui**) · D-W17-9 catalogue par clé USB (**non**) · D-W17-10 drapeau éteint par défaut en release (**oui**) · D-W17-11 écrans : attendre la sortie du gel (**attendre**, sinon TV d'abord en lecture seule) · D-W17-12 « Louer gratuitement » (**oui**).

## Routage (lignes proposées pour `routing.json`, à ajouter par le coordinateur)
```
w17-01 sonnet opus-sample | w17-02 sonnet opus-sample | w17-03 sonnet opus-audit | w17-04 sonnet opus-sample | w17-05 sonnet opus-sample | w17-06 sonnet opus-sample (ATTEND) | w17-07 sonnet opus-sample (ATTEND) | w17-08 sonnet opus-sample (ATTEND) | w17-09 haiku - | w17-10 haiku - | w17-11 sonnet -
```
