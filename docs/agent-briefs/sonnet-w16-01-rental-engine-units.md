# w16-01 — Cœur moteur : unité du contrat (heures d'utilisation / jours), clamp du budget à 96 h, phrases et alertes par unité, badge

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (règles de fusion des contrats et plafonds : un bug donne du contenu gratuit ou vole des heures) · statut : PRÊT
> **Groupe : W16a-1** (vague W16a, cœur, **autorisé pendant le gel** : aucun fichier `R/`, `S/`) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Rental*' --tests '*KeyBadge*' --tests '*TrialWindow*'`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M, ≈ 1,5 j) · audit Opus : **oui**

**Vague 16a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W16-LOCATION-DUREE-CHOISIE-PILOTE-2026-10-02.md` § 1.1-1.4, § 2.5 (lire en entier). Branche `claude/sonnet-w16-01`. Rapport : `docs/agent-reports/sonnet-w16-01.md`. Textes utilisateur en français ; dire « CastBridge-TV ». **Premier cahier de la vague** : w16-02, 03, 04, 06 codent contre `RentalUnit` et `RentalConfig.maxUseMinutesPerContract`.

## Objectif
Le moteur pur `RentalEngine` sait qu'un contrat est **à l'heure** (budget d'usage) ou **en jours** (validité seule), ne laisse jamais un contrat à l'heure dépasser `maxUseMinutesPerContract` (5 760 min = 96 h) quel que soit le nombre de lignes de renouvellement, produit les phrases et alertes propres à chaque unité, et le badge de clé dit l'unité. **La ligne signée ne change pas** (10 champs).

## Pourquoi (preuves)
- `C/lots/RentalLines.kt:5, 28, 32` : 10 champs, `parse` exige `f.size == 10` : aucun champ ajouté possible ; les deux grandeurs existent (`durationDays`, `maxUsageMinutes`).
- `C/lots/RentalEngine.kt:84-100` : `contracts()` fusionne par `(produit, period)` ; `:95` additionne `maxUsageMinutes` si **toutes** les lignes en ont un, sinon **aucun** plafond (une ligne à 0 efface le budget : à bloquer côté émetteur, w16-04, et à **tester ici** comme comportement connu).
- `:107-132` : `evaluate`, ordre des règles ; `:116` fin par usage ; `:142-155` alertes (seuils d'usage 24 h / 5 h / 1 h, absurdes pour une location d'1 h) ; `:158-173` phrases (`:172` : « (ou X h d'utilisation) » en second).
- `C/owner/KeyBadge.kt:41` : « Location : … restant(s) » lu sur `remainingMs` seulement.
- Tests existants à garder verts : `CT/lots/RentalTest.kt`, `CT/lots/TrialWindowTest.kt`, `CT/lots/RentalVectorsTest.kt`, `CT/lots/RentalVectorsV2Test.kt` (**aucun vecteur existant modifié**).

## Fichiers possédés
`C/lots/RentalEngine.kt`, `C/owner/KeyBadge.kt`, nouveau `CT/lots/RentalUnitsTest.kt`. **Hors zone** : `RentalLines.kt`, `RentalLedger.kt` (w16-02), `RentalApi.kt`/`RentalDelivery.kt` (w16-03), `RentalDurations.kt`/`LicensedIssuer.kt` (w16-04), vecteurs (w16-06), tout `R/`, `S/`.

## Étapes
1. **Rouge d'abord** : `RentalUnitsTest` : (a) `hourlyContractIsClampedAtCap` : deux lignes 60 h + 60 h même `period` ⇒ `maxUsageMinutes == 5760`, pas 7200 ; (b) `dayContractHasNoBudget` : ligne 7 j / usage 0 ⇒ `unit == DAYS`, `remainingUsageMinutes == null` ; (c) `oneHourRentalWarnsAtTenMinutesNotAtOpening` ; (d) `mixedLinesLoseTheBudget` (documente `:95` : 6 h + ligne à 0 ⇒ aucun plafond ; le test **affirme** ce comportement et renvoie à w16-04 pour le refus) ; (e) `trialWindowIsNotHourly` (produit `essai` ⇒ `unit == TRIAL`, phrases inchangées) ; (f) phrases exactes des trois unités ; (g) badge.
2. `RentalConfig` : `maxUseMinutesPerContract: Long = 5760` (0 = sans clamp). `contracts()` : après la somme, `usage = if (cfg...) min(sum, cap)` — **ajouter le paramètre `cfg`** à `contracts(activations, cfg = RentalConfig())` avec valeur par défaut (les appelants existants compilent) ; `RentalLedger.status` (w16-02) passera `config`.
3. `enum class RentalUnit { HOURS, DAYS, TRIAL }` ; `RentalContract.unit` : `TRIAL` si `productId == RentalLines.TRIAL_PRODUCT`, `HOURS` si `maxUsageMinutes > 0`, sinon `DAYS`. `RentalStatus` gagne `usedMinutes: Long` et `maxUsageMinutes: Long` (additifs, défaut 0 ; `evaluate` reçoit `inputs.usedMinutes`).
4. Alertes : `usageWarningFor(left: Long?, max: Long)` : `HOUR_1` ≤ 10 min, `HOURS_24` ≤ 60 min, `DAYS_7` ≤ 25 % de `max` ; `status()` l'utilise pour `HOURS` ; `DAYS` garde `dateWarning` seul ; `TRIAL` garde les seuils actuels (`usageWarning`, inchangé pour la fenêtre d'essai).
5. Phrases (constantes publiques, réutilisées par w16-03/10/11) : `HOURS` actif : `"Il vous reste ${h} h ${mm} d'utilisation · à utiliser avant le ${JJ/MM}"` (formatage `hoursLeft(min)` : « 5 h 20 », « 45 min ») ; `HOURS` expiré par usage : `"Vos ${H} heures d'utilisation sont épuisées : ce contenu n'est plus disponible. Relouer ?"` ; par date : `"Vos heures non utilisées ont expiré le ${JJ/MM} (fin du test gratuit). Relouer ?"` ; `DAYS` expiré : `"Location terminée (${N} jours, jusqu'au ${JJ/MM}) : ce contenu n'est plus disponible. Relouer ?"` ; `DAYS` actif : `countdown` existant. `ENDED` existant conservé pour `TRIAL`. La date vient de `contract.endsAt` formatée par un `(Long) -> String` injecté (défaut : `DateTimeFormatter` zone système) pour rester pur et testable.
6. `KeyBadge` : pour l'unité `HOURS` : `"Location : ${hoursLeft} d'utilisation restante(s)"` ; `DAYS` : ligne existante.
7. Vert : porte + `:core:test` complet ; `git diff tools/activation/*.json` **vide**.

## Critères d'acceptation (hors ligne, horloge factice)
Porte verte ; 7 tests rouges puis verts ; `grep -n "maxUseMinutesPerContract" C/lots/RentalEngine.kt` ≥ 2 ; vecteurs v1/v2 inchangés (`git diff --stat tools/activation/` vide) ; aucun changement de `RentalLines.kt` ; `RentalEngine.contracts(acts)` (ancienne signature) compile toujours.

## Cas limites
`maxUseMinutesPerContract = 0` (pas de clamp : comportement d'avant, testé) ; contrat `HOURS` dont `endsAt` est passé avec des minutes restantes ⇒ `EXPIRED`/`DATE` et phrase « heures non utilisées ont expiré » ; `superUnlimited` ⇒ `PERMANENT` inchangé ; `OVER_LIMIT` inchangé ; `GRACE` (grâce > 0) garde la phrase existante.

## À ne pas faire
Ne pas toucher `RentalLines`, les vecteurs, `RentalLedger`, les routes ; pas de nouvelle dépendance ; pas de conversion heures ↔ jours dans une phrase ; ne pas changer les phrases de la fenêtre d'essai.

## Rapport
`STATUT`, sorties rouge/vert, signature finale de `contracts()`, liste des constantes de phrases (pour w16-03/10/11), question : faut-il un seuil relatif aussi pour `DAYS` courts (1 jour : alerte à 7 j n'a pas de sens) ? (recommandation : `DAYS_7` seulement si `durationDays ≥ 14`).
