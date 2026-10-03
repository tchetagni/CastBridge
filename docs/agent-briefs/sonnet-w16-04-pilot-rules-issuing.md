# w16-04 — Cœur émission : `PilotRules` (trois choix, plafonds, borne de sûreté, quotas, prolongation sans mélange d'unités, fin de pilote), `RightsSyntax` « choix », `RentalDurations` en mode « choisi par l'utilisateur », registre d'émission

<!-- routage Fable 2026-10-03 -->
> **Amendement W16-01 (audit Opus, 2026-10-03)** : le moteur TV clampe tout contrat horaire à 96 h (`RentalConfig.maxUseMinutesPerContract = 5760`, règle du moteur) et IGNORE toute ligne dont l'unité (heures / jours) diffère de la première du contrat. Les heures au-delà sont perdues (la TV le dit : « 6 h non applicables : plafond de 96 h par location »). **L'ÉMETTEUR doit donc refuser** la prolongation qui dépasserait 96 h au total et le mélange d'unités (« cette location a déjà 96 h », « on ne mélange pas les heures et les jours »), au lieu de compter sur la TV.
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (règles d'émission = ce que la TV accepte sans discuter) · statut : PRÊT (après w16-01)
> **Groupe : W16a-4** (vague W16a, cœur, autorisé pendant le gel) · prérequis : w16-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*PilotRules*' --tests '*RentalDurations*' --tests '*RightsSyntax*' --tests '*LicensedIssuer*'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : **oui**

**Vague 16a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 1.1-1.4, § 3.2, § 4.1. Branche `claude/sonnet-w16-04`. Rapport : `docs/agent-reports/sonnet-w16-04.md`.

## Objectif
Un module pur qui, à partir des paramètres du pilote (`pilot.json` ; plus tard le document `settings` W12), du catalogue de bouquets, de la date d'émission et de l'état connu des contrats d'une licence, transforme un **choix** (« defaut », « 7j », « 12h ») en `RentalSpec` correct ou en **refus français** ; qui sait prolonger sans mélanger les unités ; et qui tient le **registre** d'émission (CSV pur) que le bureau et `louer.py` lisent.

## Pourquoi (preuves)
- `C/owner/LicensedIssuer.kt:41` `RentalSpec(productId, bundleIds, days, maxUsageMinutes, graceDays, maxConcurrent, period)` ; `:50-62` `RentalIssuing.right` (period = émission ou prolongation) ; `:87-95` `RightsSyntax.rental("produit=b:JOURS[:MIN…]")` ; `:140-151` : location = production seulement, maître requis.
- `C/lots/RentalDurations.kt:10-43` : `DEFAULT_DAYS = 30`, `daysOf` (catalogue), `check` **exact** (refuse une autre durée) : à conserver pour `userChosen = 0`.
- `C/lots/RentalEngine.kt:95` : une ligne de renouvellement à usage 0 efface le budget ⇒ **refus** de mélanger ici (w16-01 le teste comme comportement moteur).
- `C/lots/RentalPolicy.kt:41-54` `refusals` (lot libre, famille inconnue) : à appeler avant d'émettre.
- `DESIGN-W5:177` : réémission ≤ 3 par contrat.

## Fichiers possédés
Nouveaux `C/lots/PilotRules.kt`, `C/lots/PilotRegistry.kt`, `CT/lots/PilotRulesTest.kt`, `CT/lots/PilotRegistryTest.kt` ; `C/lots/RentalDurations.kt` (additif : `checkChosen`), `C/owner/LicensedIssuer.kt` (**zone `RightsSyntax` seulement** : nouvelle fonction `rentalChoice`), `CT/lots/RentalDurationsTest.kt`. **Hors zone** : `RentalIssuing.right`, `IssueSpec`, `DK/`, `R/`, `S/`.

## Étapes
1. **Rouge** : `PilotRulesTest` : `defaultChoiceIsThirtyDaysWithoutBudget` ; `sevenDaysIsValidityOnly` ; `twelveHoursIsBudgetWithSafetyBound` (émise le 25/10 ⇒ `days == 21`, `maxUsageMinutes == 720`) ; `hoursAboveCapRefused` (97 h) ; `daysAboveMaxRefused` (31 j) ; `extensionKeepsUnitAndCapsAtNinetySixHours` (60 h + 60 h ⇒ refus « cette location a déjà … ») ; `extensionWithOtherUnitRefused` ; `afterPilotEndRefused` ; `fourthActiveContractRefused` ; `weeklyQuotaRefused` (192 h + 1 h) ; `freeBundleRefused` ; `reissueGivesTheRemainderAtMostThreeTimes` ; `defaultTakesBundleRentalDaysWhenPresent`. `PilotRegistryTest` : parse/format CSV, idempotence (même licence, bouquet, choix, jour), prolongation exige la `period` en cours.
2. `PilotParams` (noms = clés W12 § 4.1 : `userChosen, defaultDays, pickerDays, maxDays, hourly.maxUseHours, hourly.pickerHours, hourly.validityDays, maxConcurrent, hourly.weeklyQuotaHours, cooldownMin, pilot.start, pilot.end, pilot.graceDays`) + `parse(json)` avec **bornes** (hors bornes ⇒ `IllegalArgumentException` français) ; `Choice` sealed : `Default`, `Days(n)`, `Hours(h)` ; `Choice.parse("defaut"|"7j"|"12h")` et `label()` (« Sans durée précise : 30 jours », « 7 jours », « 12 heures d'utilisation »).
3. `PilotRules.spec(choice, bundle, catalog, issuedAt, licenseState: LicenseState, params): Result<RentalSpec>` ; `LicenseState(active: List<ContractSummary(product, period, unit, maxUsageMinutes, endsAt, reissues)>, hoursIssuedLast7d)` ; `validityDays(choice, issuedAt, params)` ; `extend(choice, existing, …)` ; `reissue(existing, usedMinutes)`.
4. `RentalDurations.checkChosen(spec, catalog, params)` : jours ≤ `min(maxDays, rentalDays du bouquet)` ; heures ≤ cap ; conserve `check` (exact) intact.
5. `RightsSyntax.rentalChoice("classe-cm2=12h", period?)` ⇒ `(bundle, Choice, period)` ; messages français.
6. `PilotRegistry` : lignes `date,licence,code masqué,bouquet,period,unite,quantite,jours,type` ; `find(licence)`, `append`, `isDuplicate`.
7. Vert : porte + `:core:test` complet.

## Critères d'acceptation
Porte verte ; 15 tests rouges puis verts ; `RentalDurations.check` existant inchangé (tests existants verts) ; `PilotRules` sans dépendance à `DK/` ni Android ; `pilot.example.json` (dans `CT/resources/` ou chaîne de test) avec les valeurs du pilote (12/10 → 01/11, grâce 14, 30, 1,3,7,14, 96, 1,3,6,12,24,48,96, 3, 192, 0).

## Cas limites
Émission le dernier jour (01/11 : heures ⇒ 14 j de sûreté) ; bouquet avec `rentalDays = 7` (défaut = 7, paliers jours ≤ 7) ; `weeklyQuotaHours = 0` (sans quota) ; `cooldownMin > 0` (relocation après la fin trop tôt ⇒ refus daté) ; `userChosen = 0` ⇒ `spec` renvoie la durée exacte du catalogue et refuse tout choix.

## À ne pas faire
Ne pas modifier `RentalIssuing.right` ni `IssueSpec` ; ne pas écrire d'heure murale (`issuedAt` est un paramètre) ; aucune valeur de prix.

## Rapport
`STATUT`, sorties rouge/vert, API de `PilotRules` (signatures) pour w16-05/08/11, exemple de `pilot.json`, question : la réémission d'une location en jours doit-elle recalculer la validité jusqu'à la date d'origine (recommandation : oui, même `endsAt`) ?
