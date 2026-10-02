# w16-02 — Cœur compteur : `UseMeter` (temps d'utilisation réel, horloge monotone, minute entière avec report, pause, inactivité) et carnet (attribution par lot, relevé, persistance)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (compteur = la monnaie du pilote ; double compte ou perte = fausse mesure) · statut : PRÊT (après w16-01)
> **Groupe : W16a-2** (vague W16a, cœur, autorisé pendant le gel) · prérequis : w16-01 (`RentalUnit`, `contracts(acts, cfg)`) · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*UseMeter*' --tests '*RentalLedger*' --tests '*Rental*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 2 j) · audit Opus : **oui**

**Vague 16a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 2.1-2.4. Branche `claude/sonnet-w16-02`. Rapport : `docs/agent-reports/sonnet-w16-02.md`.

## Objectif
Un compteur **pur** (aucune dépendance Android) qui transforme « contenu loué ouvert à l'écran » en **minutes entières** d'utilisation, sourd à l'horloge murale, sans double compte ni perte > 1 minute, et un carnet qui les impute au **lot réellement ouvert**, les compte **aussi pour les locations en jours** (mesure), les persiste à chaque minute et sait produire un **relevé** par contrat.

## Pourquoi (preuves)
- `C/lots/RentalLedger.kt:137-148` : `recordUsage(lot, minutes, all)` impute à la location **couvrant le lot** (`RentalLogic.covers`, `:221-224`) qui finit en premier, incrémente `used` même sans plafond (`:143`), pose `expiredAt` au plafond (`:144`), sauve (`:145`). Borne `0..1440` (`:138`).
- `R/RentalHub.kt:153-158` (hors zone, w16-10) : impute au premier contrat plafonné, ignore les contrats sans plafond, premier lot : faux à deux contrats, aveugle pour les jours.
- `C/owner/Keys.kt:105-165` : `TvClock` reçoit un `mono: () -> Long` (`R/RentalHub.kt:42` passe `elapsedRealtime`) : le même temps monotone alimente le compteur, **sans toucher `Keys.kt`** (possédé par w15-17).
- `CT/lots/TrialWindowTest.kt:35-44` : fin par usage avec `recordUsage(lot, 60)`.
- Persistance : `RentalLedger.save` (`:211-218`) via `SafeFile.write` avec relecture.

## Fichiers possédés
Nouveaux `C/lots/UseMeter.kt`, `CT/lots/UseMeterTest.kt`, `CT/lots/RentalLedgerUsageTest.kt` ; `C/lots/RentalLedger.kt` (additif : `recordUsage` inchangé ; nouveaux `usageReport()`, `status` passe `config` à `contracts`). **Hors zone** : `RentalEngine.kt` (w16-01), `RentalApi.kt` (w16-03), `C/owner/Keys.kt`, tout `R/`.

## Étapes
1. **Rouge** : `UseMeterTest` : `fiftyNineSecondsTwiceCountsOneMinuteAndCarriesTheRest` ; `closingAfterThirtySecondsCountsNothingAndDropsTheCarry` ; `pauseLongerThanFiveMinutesStopsCounting` (reprise ⇒ reprend) ; `idleThirtyMinutesStopsCounting` (`input()` relance) ; `monotonicGoingBackwardsNeverGivesNegative` ; `tickWithoutOpenLotIsZero` ; `rebootRecreatesAnEmptyMeter`. `RentalLedgerUsageTest` : `minutesGoToTheContractCoveringTheOpenLot` (deux contrats, deux lots, chacun reçoit les siennes) ; `dayContractCountsMinutesWithoutEverExpiringByUsage` ; `everyMinuteIsOnDiskBeforeTheNextTick` (relire le fichier après chaque `recordUsage`) ; `powerCutLosesAtMostOneMinute` (fake `wall`, fake fichier) ; `usageReportListsEveryContractWithUnitUsedMaxStateReason`.
2. `UseMeter(cfg = UseMeterConfig(pauseStopMs = 5 min, idleStopMs = 30 min, minuteMs = 60_000))` : `open(lot: LotId, nowMono)`, `close(nowMono): Tick`, `pause/resume/input(nowMono)`, `tick(nowMono): Tick` où `Tick(lot: LotId?, minutes: Int)` ; état interne § 2.2 ; `carryMs` jamais persisté ; toutes les méthodes `@Synchronized`.
3. `RentalLedger.usageReport(all: List<Activation>, installId: String, nowTv: Long): String` : texte `castbridge-rental-usage-v1` (format DESIGN-W16 § 2.5 : en-tête `install=`, une ligne par contrat `contract=|unit=|used=|max=|state=|reason=|endsAt=|at=`), lignes triées par contrat, **aucune** autre donnée ; `usageOf(key): Pair<used, max>`.
4. `RentalLedger.status` : `RentalEngine.contracts(activations, config)` (clamp actif sur la TV).
5. Vert : porte + `:core:test` complet.

## Critères d'acceptation (hors ligne, horloge factice)
Porte verte ; 12 tests rouges puis verts ; `UseMeter.kt` sans `import android` ni `System.currentTimeMillis` ; `usageReport` ne contient ni licence, ni poste, ni facteur, ni profil (test par recherche de chaînes, comme `RentalTest` « journal sans donnée personnelle ») ; `rentals.json` reste rétro-lisible (fixture d'un ancien fichier, inchangée).

## Cas limites
Deux lots d'un même contrat ouverts successivement dans la même minute (une seule minute, au contrat) ; `open` d'un lot non loué (compte zéro : `recordUsage` ne trouve aucun contrat couvrant, `used` inchangé) ; contrat `SUSPENDED` (horloge) : minutes comptées (`:140` inclut `SUSPENDED`) ; `minutes > 1440` impossible par construction (test).

## À ne pas faire
Pas de `Thread`, `Handler`, `Timer` dans le cœur ; ne pas modifier `recordUsage` ni le format de `rentals.json` ; ne pas écrire dans `R/`.

## Rapport
`STATUT`, sorties rouge/vert, format exact du relevé (exemple), API de `UseMeter` (pour w16-10), question : faut-il persister `lastUseAt` par contrat dans `rentals.json` (champ additif) pour le relevé ? (recommandation : oui, additif, rétro-lisible).
