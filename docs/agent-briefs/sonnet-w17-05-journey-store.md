# w17-05 — Parcours J (harnais W14) de la Boutique : vitrine relayée, article sur la TV, demande TV → téléphone → location, TV d'essai, profil enfant, téléphone absent 8 jours, drapeau éteint

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT (pendant le gel : `CT/` seul)
> **Groupe : W17a-5** (vague W17a, tests) · prérequis : w17-01…04 fusionnés ; harnais J (`CT/journey/`, fusionné) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.Store*' --tests 'castbridge.core.journey.HarnessSmokeTest'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1 j) · audit Opus : échantillon

**Vague 17a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W17 § 7.2, § 7.5, § 4.2. Branche `claude/sonnet-w17-05`. Rapport : `docs/agent-reports/sonnet-w17-05.md`. Règle W14 : aucun `Thread.sleep`, horloge `JourneyClock`, ports 0, un test ≤ 3 s.

## Objectif
Sept parcours JVM qui traversent le couple téléphone + TV **avec la vraie `ReceiverServer`** : la vitrine relayée une fois, l'article « sur la TV » après un vrai `/api/lots/install`, la demande née sur la TV relevée par le téléphone puis acquittée et remplie par une **vraie** livraison de location (activation de TEST + lot scellé, comme `tools/rental-test`), la TV d'essai (vitrine visible, demande refusée, 403), le profil enfant (refus cœur, aucune ligne), le téléphone absent 8 jours (demande expirée, location terminée par le balayage ⇒ `TERMINE`), et le drapeau éteint (404, comportement actuel). Ces parcours sont la **porte** des cahiers d'écran w17-07/08.

## Pourquoi (preuves)
- `CT/journey/TvSim.kt:47-135` (`TvScenario(trial, locked, pin)`, `ReceiverServer(... extension = …)`, `activate(payload)`, `installedActivations`, `notices`) ; `CT/journey/PhoneSim.kt` (`pairWithTv`, `enterPin`, `send`, `credentialNow`) ; `CT/journey/ActivationApiSim.kt:12-41` (`switchTrial`) ; `CT/journey/HarnessSmokeTest.kt`.
- `CT/lots/RentalDeliveryTest.kt`, `CT/lots/RentalApiTest.kt`, `tools/rental-test/rental_test.py:53-74` (émission de TEST, `lot-chiffrer`, `/api/rental/install`) : la livraison réelle d'une location **en JVM** (clé de TEST générée dans le test, `RentalKeys.seal`).
- `C/store/StoreApi.kt` (w17-04), `C/store/RentRequests.kt` (w17-03), `C/store/StoreView.kt` (w17-02).

## Fichiers possédés
Nouveaux `CT/journey/StoreSim.kt` (branche `StoreApi` + `RentalApi` + `TvLotApi` dans la chaîne d'extensions d'un `TvSim`, dossier `files/store` sous `TvSim.dir`, faits réels : `TvLotStore.manifest()`, `RentalLedger.status`, `ActivationApiSim.trial`, `kidActive` simulé), `CT/journey/StoreJourneyTest.kt`, `CT/journey/PhoneStoreSim.kt` (le « téléphone » : `StoreRuntime` pur = relais des catalogues, relevé des demandes, confirmation, livraison via `RentalDelivery` ; **miroir** de ce que w17-06 câblera en Android) ; `CT/journey/TvSim.kt` (**zone additive seulement** : paramètre `extensions: List<ApiExtension> = emptyList()` dans `TvScenario` ou `start()`, chaîné après `ActivationApiSim` ; ≤ 6 lignes) ; fixtures signées de TEST générées dans le test. **Hors zone** : `C/**`, `S/**`, `R/**`, les autres fichiers de `CT/journey/`.

## Étapes
1. **Rouge** : écrire les sept tests d'abord ; ils échouent faute de `StoreSim` (puis, un à un, deviennent verts).
2. `J-S0 flagOffKeepsEverythingAsToday` : `enabled = false` ⇒ `GET /api/store` 404 ; `/api/lots`, `/api/rental` inchangés.
3. `J-S1 catalogRelayedOnceThenNotAgain` : le téléphone pousse les deux catalogues ⇒ `accepted = [lots, bundles]` ; second contact ⇒ `accepted = []` ; catalogue plus ancien ⇒ refusé, vitrine inchangée ; `header` = « Catalogue du JJ/MM ».
4. `J-S2 itemOnTvAfterRealLotInstall` : vitrine ⇒ `PAS_SUR_TV` ; livraison d'un lot complet par `TvLotApi` (upload + install avec preuve signée de TEST) ⇒ `SUR_TV` **sur les deux vues** (TV : `GET /api/store` ; téléphone : `StoreView.decide` avec `phoneStages`).
5. `J-S3 requestOnTvFulfilledByPhone` : `StoreSim.requestFromTv("classe-cm2", "12h")` ⇒ `PENDING`, code court affiché ; téléphone : `GET /api/store/requests` ⇒ 1 ; `ack ACCEPTED` ; livraison réelle (activation `rental|loc-classe-cm2|…|maxUsageMinutes=720` signée par la clé de TEST, lot scellé `RentalKeys.seal`, `POST /api/activation/install` puis `/api/rental/install`) ⇒ `reconcile` ⇒ `FULFILLED` ; vitrine : `LOUE` avec « 12 h » d'utilisation restantes (phrase W16 si w16-01 fusionné, sinon `message` existant) **identique** côté TV et côté téléphone (`TvRentalView`).
6. `J-S4 trialTvSeesStoreButCannotRequest` : `tv.setTrial(true)` ⇒ `GET /api/store` 200, chaque carte `BLOQUE(TRIAL_TV)` ; `requestFromTv` ⇒ refus `TRIAL_TV` ; `GET /api/store/requests` ⇒ 403 (`TrialPolicy`).
7. `J-S5 kidProfileCannotRequest` : `kidActive = true` ⇒ refus `KID_PROFILE`, `requests.json` sans ligne, carte « Demandez à un parent ».
8. `J-S6 phoneAwayEightDays` : demande `PENDING`, location de 1 jour installée ; `clock.advance(8 j)` ; `RentalSweeper.sweep(PERIODIC)` ⇒ contrat terminé ; `expire` ⇒ `EXPIRED` ; vitrine : `TERMINE` + « Relouer » ; le téléphone revient : `ack` sur une demande expirée ⇒ `UNKNOWN`/`EXPIRED`, jamais une exception.
9. **Vert** : porte ; `:core:test --tests 'castbridge.core.journey.*'` complet (aucun autre parcours cassé).

## Critères d'acceptation
Porte verte ; 7 parcours rouges puis verts ; durée totale des 7 < 15 s ; diff de `TvSim.kt` ≤ 6 lignes additives ; aucun `Thread.sleep`.

## Cas limites
`ReceiverServer` relancé (`tv.restart()`) après J-S3 ⇒ `files/store/` relus, vitrine identique (persistance) ; PIN tourné entre deux contacts ⇒ le relais échoue proprement (401 avalé **avec message**, règle W13/W14 : pas de boucle).

## À ne pas faire
Ne pas modifier `C/**` ; ne pas écrire de code Android ; ne pas simuler la signature (vraies clés Ed25519 de TEST générées) ; pas de réseau hors `127.0.0.1`.

## Rapport
`STATUT`, sorties rouge/vert, durée, les lignes ajoutées à `TvSim.kt`, ce que `PhoneStoreSim` impose à w17-06 (liste des appels dans l'ordre), question : faut-il un J-S7 « deux TV » (recommandation : après le pilote).
