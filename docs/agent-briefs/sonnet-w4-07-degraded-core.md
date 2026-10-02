# w4-07 — Cœur : `GateState.Degraded`, droits durables, `DegradedPolicy`, badge « CLÉ TERMINÉE »

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (D6 = oui)
> **Groupe : W4b-1** (vague W4b) · prérequis : w2-04, w1-05, w1-06 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Degraded*' --tests '*LicenseAndGate*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 4b · Effort M (≈ 1,5 j) · Statut PRÊT (décision D6 = OUI).** Conception : `docs/coordination/DESIGN-W4-MODE-DEGRADE.md` § 2, § 3, § 5, § 6. Branche `claude/sonnet-w4-07`. Rapport : `docs/agent-reports/sonnet-w4-07.md`. **Premier cahier de la sous-vague 4b** (w4-08, w4-09 en dépendent).

## Objectif
À la fin du plafond d'une clé de **production**, `TvGate` rend un accès **réduit** (`degraded = true`, achats conservés, droits temporaires éteints) au lieu de « rien » ; `FeatureGate` connaît l'état `Degraded` avec sa liste blanche figée ; `DegradedPolicy` dit quelles routes/tuiles/jeux restent ouverts ; le badge et le JSON d'état le disent ; les achats d'une clé terminée **survivent au renouvellement**. L'essai terminé reste verrouillé.

## Pourquoi (preuves)
- `C/owner/Activation.kt:189-193` : `counting.isEmpty() && live.isEmpty()` ⇒ `TvAccess(false, …, "Activation terminée")` ; `:206-232` `evaluateCounting` ne lit que les droits des activations qui comptent (achats perdus au renouvellement : nouvelle licence `lic-…` générée par les outils, `C/owner/LicensedIssuer.kt:108-113`).
- `C/owner/FeatureGate.kt:46-69` : `GateState` sans état réduit ; `Feature.lockedAllowed` ; test de la liste blanche `CT/owner/LicenseAndGateTest.kt:166-174`.
- `C/owner/TrialPolicy.kt:31-45` : liste blanche de routes ; `C/owner/KeyBadge.kt:27` ; `C/owner/KeyStatusJson.kt:22-37`.
- Tests qui figent l'ancien comportement : `CT/owner/UsageCeilingTest.kt:31`, `CT/owner/ClockRollbackTest.kt:56`.
- Audit A9-1, MO-3, UX-4 ; w2-04 a préparé `KeyBadge.endedSummary`/`reminder` (vérifier leur présence : `grep -n 'fun endedSummary\|fun reminder' android/core/src/main/kotlin/castbridge/core/owner/KeyBadge.kt` ; absents ⇒ les ajouter ici).

## Fichiers possédés
`C/owner/Activation.kt` (`TvAccess`, `TvGate` seulement : **pas** `ActivationVerifier`, pas `CompactActivation`), `C/owner/FeatureGate.kt` (`Feature`, `GateState`, `FeatureGate` : **pas** `ActivationReceiver`), nouveau `C/owner/DegradedPolicy.kt`, `C/owner/KeyBadge.kt`, `C/owner/KeyStatusJson.kt`, `C/owner/TrialPolicy.kt` (extraire au besoin une liste partagée `TrialPolicy.STREAMING_EXACT` : lecture seule sinon), tests `CT/owner/DegradedModeTest.kt` (nouveau), `CT/owner/DegradedRoutesTest.kt` (nouveau), `CT/owner/UsageCeilingTest.kt`, `CT/owner/ClockRollbackTest.kt`, `CT/owner/LicenseAndGateTest.kt`, `CT/owner/KeyBadgeTest.kt`, `CT/KeyStatusJsonTest.kt`, `tools/routes/routes.txt`, `tools/routes/list_routes.py`, `tools/tests/test_routes.py` (créés par w1-06 : ajouter la colonne « réduit » ; absents ⇒ ne pas créer, le dire). **Hors zone** : `R/**` (w4-08), `C/lots/**` (w4-09), `ActivationVerifier`, `ActivationReceiver`, docs (w4-10).

## Étapes
1. `TvAccess` : champs additifs `degraded = false`, `endedAt: Long? = null`, `endedKind: ActivationKind? = null`.
2. `TvGate.lastingRights(activations): List<Right>` = `Purchase` + `Super` de **toutes** les activations installées ; `evaluateCounting` les fusionne (dédoublonnage par ligne filaire) ; dans la branche « rien ne compte » : production terminée présente ⇒ `TvAccess(keyInstalled=false, access = Access(OK, purchased = achats durables, ∅, ∅, DegradedPolicy.MESSAGE), null, "Mode réduit : clé à renouveler", superUnlimited = false, trial = false, degraded = true, endedAt = max(fins), endedKind = PRODUCTION)` ; essai seul ⇒ inchangé. `clockDoubt == AHEAD` ⇒ ne jamais décider « terminé » sur ce temps (garder le comportement w1-05 : si une activation comptait au dernier temps sûr, elle compte ; implémenter avec `ceilingNow` déjà présent : documenter dans le KDoc ce que w1-05 a livré et s'y conformer).
3. `Feature.degradedAllowed` (défaut faux ; liste exacte § 3 de la conception), `DEGRADED_WHITELIST`, `GateState.Degraded(access)`, `FeatureGate.state` (ordre : `NotRequired`, `Activated`, **`Degraded`**, `Grace`, `Locked`), `canUse`.
4. `DegradedPolicy` : `MESSAGE`, `UPGRADE_LABEL = "Renouveler la clé"`, `UPGRADE_TITLE = "Renouveler la clé de CastBridge-TV"`, `CLOSED_TILES = {receive, usb, downloads}`, `GAMES = {sudoku}`, `routeAllowed(path)` (= `TrialPolicy.routeAllowed` + `/api/library`, `/api/thumb`, `/api/player/subfile` − préfixe `/api/lots` sauf `GET /api/lots` exact − `/api/rental/install`), `streamAllowed(loopbackToken, trustedPhone) = true`, `btFileAllowed(name) = false` (plus aucun fichier entrant, lot compris), `tileAllowed`, `gameAllowed`. Même garde anti-traversée que `TrialPolicy` (`..`, `//`, `\`, `%`).
5. `KeyBadge.of` : branche « CLÉ TERMINÉE » (production) avec la date (`endedAt`) et les trois lignes de la conception ; `KeyStatusJson.fields` : `"degraded"`, `"endedAt"`.
6. Tests : tous ceux du § 6 de la conception ; mettre à jour `UsageCeilingTest.aProductionActivationWithACeilingEndsButOneWithoutNeverDoes` (nom conservé, assertions : `degraded`, `purchased` conservé, label), `ClockRollbackTest:56` (label du mode réduit), `LicenseAndGateTest` (+ `DEGRADED_WHITELIST` figée), `KeyBadgeTest`, `KeyStatusJsonTest` ; `DegradedRoutesTest` sur le même principe que `TrialRoutesTest` (classement exhaustif si `tools/routes/routes.txt` existe).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.owner.*' --tests 'castbridge.core.KeyStatusJsonTest'   # vert
grep -n 'degradedAllowed' android/core/src/main/kotlin/castbridge/core/owner/FeatureGate.kt   # ≥ 2
grep -n '"Activation terminée"' android/core/src/main/kotlin/castbridge/core/owner/Activation.kt   # 1 (le cas essai)
python3 -m unittest discover -s tools/tests -p 'test_routes.py'   # vert si le fichier existe
```

## Cas limites
- Production terminée + essai en cours (clé d'essai installée après) : `Activated`, `trial = true`, `purchased` durables conservés (test dédié).
- `OwnerGrant` `OPEN_ALL` pendant le mode réduit ⇒ `Activated` (déjà : `live`), retour `Degraded` à sa fin.
- Deux productions, l'une terminée l'autre non ⇒ `Activated` avec achats des deux.
- `SUPER_UNLIMITED` ⇒ jamais `Degraded`.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas toucher `ActivationVerifier`/`ActivationReceiver`/`CompactActivation` ; ne pas changer `TrialPolicy.routeAllowed` (sauf extraction pure sans changement de résultat : `TrialRoutesTest` doit rester vert sans modification) ; textes en français.

## Rapport
`STATUT`, signatures ajoutées (pour w4-08/09), liste `DEGRADED_WHITELIST` finale, tests modifiés.
