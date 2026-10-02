# w12-07 — CastBridge-TV : `SettingsHub` (cache, routes `GET /api/settings` et `POST /api/settings/install`, fichier `settings` sur la clé USB, priorité sur `budget.set`), ligne « Réglages » dans « Connexion & réglages », consommateurs de la tranche 1
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (routes, `ActivationCenter`, `TrialPolicy`) · statut : PRÊT
> **Groupe : W12-c** (vague W12) · prérequis : w12-01 fusionné ; `R/TvService.kt`, `R/ActivationCenter.kt`, `R/PlayerActivity.kt`, `C/owner/TrialPolicy.kt`, `tools/routes/routes.txt` **après** w11-10/11/12, w10-08/09, w5-04, w4-07, w3-02, w4-08, w6-12 s'ils sont lancés (fichiers partagés) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*TrialRoutes*' --tests 'castbridge.core.settings.*' && cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin && python3 -m unittest discover -s tools/tests -p 'test_routes.py'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : **oui**

**Vague 12c (TV) · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.5-2.8, § 5.2-5.4, § 7. Branche `claude/sonnet-w12-07`. Rapport : `docs/agent-reports/sonnet-w12-07.md`. Référence : TV GaiaOS 32 bits, 720p ; cibles = toutes les TV.

## Objectif
(1) `SettingsHub` (objet, comme `PolicyHub`) : `SettingsEngine` sur `files/settings/`, anneau = `BuildConfig.TRUSTED_KEYS` (déjà parsé dans `ActivationCenter.kt:49` : **réutiliser** l'anneau exposé, ne pas le reparser), temps de confiance = `TvClock` de l'activation ; `Settings.bind` + `bindSubject` (licence installée, sinon sel local persistant) ; (2) **routes** : `GET /api/settings` (lecture, **autorisée en essai et en mode verrouillé** : elle ne révèle rien de personnel) → `{"seq","kid","issuedAt","expiresAt","schema","stale","cohorts":"nom:bras,…","version":"<Settings.version()>"}` ; `POST /api/settings/install` (corps = jeton ; PIN / téléphone de confiance comme `/api/activation/install` ; réponse = `SettingsAck.toText()`) ; (3) **clé USB** : le veilleur d'`ActivationCenter` lit aussi `Download/CastBridge/settings` (et `Download/settings`), même tolérance CRLF/BOM, fichier accepté une fois (empreinte mémorisée) ; (4) **priorité** entre `lots.tvBudgetMb` et l'ordre `budget.set lots_mb` : le plus récent par `issuedAt` (règle § 2.6, testée dans le cœur par une fonction pure `BudgetArbiter`) ; (5) **consommateurs tranche 1** : `LotsHub` (`maxBytes` du `TvLotStore`), `PairingSession.blockMs` (plancher 5 min **appliqué par le schéma**), `TelemetryUploader` TV (cadence), `KeyBadge` (texte « Lots locatifs : <h> d'essai » dérivé de `trial.lotsWindowMinutes`, `KeyBadge.kt:47` : **lecture seule du réglage pour le texte**, le droit installé reste la source du restant), `settings.message` (bandeau discret, comme `message.show`) ; (6) ligne « Réglages : … » dans `showSettings()` et événement essentiel `settings_applied` à chaque changement de seq ; (7) `routes.txt` régénéré ; `TrialPolicy` : `/api/settings` en lecture dans l'allowlist exacte ; `/api/settings/install` dans `PREFIXES` **non** (il passe par la règle des routes d'installation existantes : vérifier et noter).

## Pourquoi (preuves)
- Veilleur USB : `R/ActivationCenter.kt:145` (toutes les 2 s), `:159-167` (`device-request.txt`), fichier `activation` (`ACTIVATION-FORMAT.md` § 4) ; anneau `:49` ; horloge `:182,206,233`.
- Routes et garde : `C/tv/ReceiverServer.kt:330` (`serve`), `:366` (`/api/info`), `:523-541` (`denied()` : `X-CB-Token` / `X-CB-PIN`), `PublicRoutes.kt` (sans PIN) ; routes additionnelles du receiver `R/TvService.kt:855,894` ; table `tools/routes/routes.txt` + `list_routes.py --check` ; essai : `C/owner/TrialPolicy.kt:31-38`.
- Budget des lots : `R/LotsHub.kt:31` (`TvLotStore(..., maxBytes)`), `C/lots/TvLotStore.kt:62-69` ; ordre `budget.set` : `C/policy/PolicyActions.kt:32`, état `PolicyState.budgets` (`PolicyState.kt:25`) — **`PolicyHub` n'est pas branché** (`R/PolicyHub.kt:11-17`) : l'arbitre lit `PolicyHub.state` **si** initialisé, sinon seulement les réglages.
- Diagnostic : `R/PlayerActivity.kt:611-634` (liste `:617-632`) ; badge `C/owner/KeyBadge.kt:47-49`, `R/KeyBadgeOverlay.kt:21-27` ; appairage `C/trust/PairingSession.kt:19`.

## Fichiers possédés
- Nouveaux : `R/SettingsHub.kt`, `R/SettingsRoutes.kt`, `C/settings/BudgetArbiter.kt`, `CT/settings/BudgetArbiterTest.kt`, `CT/owner/TrialRoutesSettingsTest.kt`.
- Existants (zones précises) : `R/TvService.kt` (enregistrement des deux routes), `R/ActivationCenter.kt` (veilleur : fichier `settings`), `R/LotsHub.kt` (`maxBytes` via `Settings`), `R/PlayerActivity.kt` (`showSettings()` : **une** ligne), `R/KeyBadgeOverlay.kt` ou `C/owner/KeyBadge.kt` (texte du badge : **une** fonction), `C/trust/PairingSession.kt` (`blockMs` paramétré, défaut inchangé), `C/owner/TrialPolicy.kt` (allowlist : `/api/settings`), `tools/routes/routes.txt` (régénéré), TV `TelemetryUploader` (cadence).
- Hors zone : `R/PolicyHub.kt`, `R/OwnerBtHost.kt`, `C/owner/OwnerChannel.kt`, `C/policy/PolicyEngine.kt` (**w12-09**), `C/settings/{SettingsEngine,…}` (w12-01), écrans W11, `R/shop/**`.

## Étapes
1. `BudgetArbiter.lotsMaxBytes(settings: SettingsState?, policy: PolicyState?, default)` pur + tests (4 cas : aucun, réglage seul, ordre seul, les deux ⇒ plus récent).
2. `SettingsHub.init(ctx, keyRing, trustedNow)` ; chargement ; `receive(token)` ; `settings_applied` via `Telemetry` (essentiel) à chaque seq nouveau.
3. Routes ; `routes.txt` ; `TrialPolicy` ; test `TrialRoutesSettingsTest` (lecture ouverte en essai, installation soumise au PIN/jeton).
4. Veilleur USB (empreinte du dernier fichier accepté pour ne pas rejuger toutes les 2 s) ; un fichier refusé est journalisé une fois.
5. Consommateurs ; ligne de diagnostic ; message.
6. Sur la TV de référence (si disponible au coordinateur ; sinon émulateur) : USB accepté, altéré refusé, ancien seq refusé, fichier absent = défauts ; `adb` **interdit à l'exécutant** : décrire le protocole dans le rapport pour le propriétaire.

## Critères d'acceptation (hors ligne)
- Porte verte ; `routes.txt` à jour (`list_routes.py --check`) ; compilation `receiver` ; aucune route nouvelle sans PIN sauf `GET /api/settings`.
- Rapport : ce que l'audit Opus doit relire (veilleur, garde des routes, `TrialPolicy`), protocole de test TV, lignes exactes modifiées dans les fichiers partagés.
