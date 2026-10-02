# w6-08 — Agrégation v2 sur le téléphone (cœur) : `ParentalLedger` absorbe les tranches, l'écran, les connexions ; aujourd'hui / semaine ; carte horaire ; exports

**Vague 6a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (en parallèle de w6-07 : codez contre le schéma JSON v2 de § 2.5 ; un rapport v1 doit rester absorbé à l'identique).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.5, § 2.8. Branche `claude/sonnet-w6-08`. Rapport : `docs/agent-reports/sonnet-w6-08.md`.

## Objectif
(1) `WholeTvModel` : `AppDayFact(tv, day, pkg, label, min, slots[24]?, launches, quality)`, `ScreenDayFact(tv, day, onMin, segments)`, `ConnectionFact`, `InventoryFact` ; (2) `ParentalLedger.absorb` lit les champs v2 (idempotent, trous ≠ zéros, « le plus récent gagne » par jour), rétention existante ; (3) `ReportAggregator` : résumé **Aujourd'hui / Cette semaine** par profil et « Toute la TV » (écran, CastBridge-TV, autres applications, limite contre réalisé avec « au moins » quand ◐ manque), **top 3 applications avec mini-carte horaire**, lancements, connexions, nombre de détenteurs ; (4) `Heatmap` : carte jour × heure par application (semaine) à partir des tranches ; (5) `Trends` : comparaison semaine précédente **par jour reçu** ; (6) `Exports` texte/CSV/PDF : colonnes v2 ; (7) `LearnInsights` : Sudoku ; tests.

## Pourquoi (preuves)
- `C/parental/tab/ParentalLedger.kt` (règles : idempotence, ordre, trous, purge, profils renommés), `ReportAggregator.kt`, `Heatmap.kt`/`UseStats`, `Trends.kt`/`Goals`, `Exports.kt`, `LearnInsights.kt`, `Freshness.kt`, `LiveReport.kt`, `TimeRanges.kt` (`Period`) ; `CT/parental/tab/{AggregationTest,ExportsAndLockTest,ParentalLedgerTest,TabTestSupport}.kt` ; PARENTAL.md § « Qualité des données (règle absolue) ».

## Fichiers possédés
Nouveaux `C/parental/tab/WholeTvModel.kt`, `CT/parental/tab/WholeTvTest.kt` ; modifiés `C/parental/tab/{ParentalLedger,ReportAggregator,Heatmap,Trends,Exports,LearnInsights,Freshness,LiveReport,TimeRanges}.kt`, `CT/parental/tab/{AggregationTest,ExportsAndLockTest,ParentalLedgerTest,TabTestSupport}.kt`. **Hors zone** : `C/parental/tab/{TvJournal,SessionTracker,TabModel}.kt` (w6-05), `C/parental/*.kt` (w6-06/07), `S/**`.

## Étapes
1. `WholeTvModel` + `absorb` : `apps[].slots/launches/quality`, `screen`, `connections`, `newApps/removedApps`, `holders`, `games.sudoku`, `tokens` (déjà w5-18 ?) ; v1 ⇒ comportement inchangé ; clé de dédoublonnage `tv|day|pkg` (le dernier rapport du jour gagne).
2. `ReportAggregator.summarize(period = TODAY|WEEK|…, tv, profile?)` : `WholeTvSummary(screenOnMin: Figure, cbMin: Figure, otherAppsMin: Figure(approx), limit: Goal?, topApps: List<AppRow(label, min, slots?, launches)>, launches, connections: List<ConnectionRow>, holders: Int?, daysReceived/expected)` ; une période incomplète est dite « X j sur Y reçus ».
3. `Heatmap.perApp(tv, pkg, week)` → 7 × 24 ; `Heatmap.textAlternative(...)` (« surtout entre 19 h et 21 h, lundi et jeudi »).
4. `Trends.compare(current, previous)` par jour reçu (règle existante).
5. `Exports` : texte (section « Toute la TV »), CSV (`day,tv,profile,kind,pkg,label,min,launches,slot00..slot23`), PDF (page « Toute la TV ») ; rien d'envoyé.
6. Tests : absorption v1 inchangée (tests existants verts), v2 idempotente et dans le désordre, trou de jour ⇒ absent, `quality = unavailable` ⇒ « indisponible » (jamais 0), top 3 et carte horaire, semaine incomplète, comparaison, exports (en-têtes CSV), Sudoku dans `LearnInsights`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.parental.tab.*'   # vert
grep -n 'slots' android/core/src/main/kotlin/castbridge/core/parental/tab/ParentalLedger.kt   # ≥ 1
```

## Cas limites
Un rapport `weekly` v2 sans `daily` pour certains jours : les `slotsSum` hebdomadaires alimentent la carte sans inventer de jours ; deux TV : agrégats par TV, « Toutes les TV » = somme **par jour reçu** avec mention ; `shareTitles = false` : titres « Vidéo » affichés tels quels.

## À ne pas faire
Pas d'Android ni de Compose ; aucun envoi réseau ; ne pas changer la rétention par défaut (90 j) ni les bornes sans le dire.

## Rapport
`STATUT`, API (pour w6-18), exemples de résumé en texte.
