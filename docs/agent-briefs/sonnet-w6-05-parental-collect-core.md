# w6-05 — Collecte « toute la TV » (cœur) : tranches horaires par application, écran allumé/éteint, lancements, connexions, Sudoku, journal 14 jours

**Vague 6a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.2, § 2.3. Branche `claude/sonnet-w6-05`. Rapport : `docs/agent-reports/sonnet-w6-05.md`.

## Objectif
Structures **pures, bornées, testées** que la TV alimente (w6-13) et que les rapports v2 lisent (w6-07) : (1) `UsageSlots` (par jour, par application : `IntArray(24)` minutes + lancements) ; (2) `ScreenSegments` (segments allumé/éteint, ≤ 200/jour, total minutes) ; (3) `ConnectionLog` (`kind ∈ {phone, usb, ssh, remote_assist}`, libellé ≤ 40, compteurs par jour, **jamais** d'adresse) ; (4) `AppInventoryDiff` (nouvelles / retirées par jour) ; (5) `EventType.SUDOKU`, `EventType.SCREEN`, `EventType.CONNECTION` ; (6) `TvJournal` : 800 événements / 14 jours ; (7) `DailyAggregates` 35 jours (minutes/jour/application, écran) pour la comparaison hebdomadaire ; (8) sérialisation compacte (JSON, tableaux d'entiers), écriture **au plus une fois par minute** (`dirty` + `flushIfDue`).

## Pourquoi (preuves)
- `C/parental/ParentalEngine.kt:284` (`appTick(pkg, label, dtMs, env)` : minutes par application/jour, clé `appusage`), `:221` (`tick`), `:306` (`syncInstalled` → `NewApp`), `:417` (`report(days)` : `days[].profiles[].byApp`) ; `C/parental/tab/TvJournal.kt:12` (400 / 8 j), `SessionTracker.kt`, `TabModel.kt` (`EventType`) ; `R/ParentalHub.kt:340-374` (tick 15 s), `:428-443` (`onForeground`), `:446-458` (`accountApp`) : **les points d'appel** que w6-13 branchera ; `C/parental/KvStore` (interface) ; budget TV § 2.3 (≤ 300 Ko).

## Fichiers possédés
Nouveaux `C/parental/Collect.kt` (toutes les structures), `CT/parental/CollectTest.kt`, `CT/parental/tab/TvJournalTest.kt` ; modifiés `C/parental/tab/TvJournal.kt`, `C/parental/tab/SessionTracker.kt`, `C/parental/tab/TabModel.kt` (ajouts d'`EventType` **additifs** avec libellés français). **Hors zone** : `ParentalEngine.kt`, `ParentalModel.kt`, `ParentalApi.kt` (w6-06), `ParentalReports.kt`/`ParentalSync.kt` (w6-07), `C/parental/tab/{ParentalLedger,ReportAggregator,…}.kt` (w6-08), `R/**`.

## Étapes
1. `UsageSlots(store, now, zone)` : `add(day, pkg, label, minuteOfDay, dtMs)` (répartit `dt` sur la tranche courante, bascule de tranche et de jour correctes), `launch(day, pkg)`, `forDay(day): List<AppSlots(pkg, label, slots: IntArray(24), minutes, launches)>`, `days()` ; rétention 7 jours ; ≤ 60 applications/jour (au-delà : agrégées sous `pkg = "autres"`).
2. `ScreenSegments(store, now)` : `observe(screenOn: Boolean, nowMs)` (ouvre/ferme, coupe à minuit), `forDay(day): Segments(onMin, segments)`, rétention 7 j, ≤ 200 segments/jour (au-delà : fusion des plus courts).
3. `ConnectionLog(store)` : `note(kind, label, nowMs)` dédupliqué 1/min par (kind,label) ; `forDay(day)` ; `label` nettoyé (pas de `:` hex, pas de chiffres seuls > 8) ; rétention 14 j.
4. `AppInventoryDiff` : `apply(installed: List<InstalledApp>)` ⇒ nouvelles/retirées du jour (l'engine garde `syncInstalled` ; ici seulement le **retrait**, qui manque).
5. `DailyAggregates(store)` : `roll(day, slotsOfDay, screenOfDay)` ⇒ `{day, screenOnMin, apps:[{pkg,min}]}` 35 jours ; `week(days)`.
6. `TvJournal` : `max = 800`, `maxAgeMs = 14 j`, `record` inchangé ; `SessionTracker` : accepte `EventType.SUDOKU`.
7. `Flush` : chaque structure expose `dirty` et `flushIfDue(nowMs, minIntervalMs = 60_000)` ; `flushNow()` au changement de jour et à l'arrêt.
8. Tests : répartition d'un `dt` à cheval sur deux tranches et sur minuit ; bornes (61ᵉ application → « autres », 201ᵉ segment) ; écran éteint ⇒ aucune écriture (`store.put` non appelé : KvStore espion) ; rétention ; `flushIfDue` au plus une écriture/minute ; sérialisation compacte (< 6 Ko pour 40 applications × 24 tranches) ; `ConnectionLog` refuse une adresse Bluetooth dans le libellé ; journal 800/14 j.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.parental.CollectTest' --tests 'castbridge.core.parental.tab.TvJournalTest' --tests 'castbridge.core.parental.tab.*'   # vert (les tests existants restent verts)
grep -rn 'TvConnect\|ServerLink\|HttpLite' android/core/src/main/kotlin/castbridge/core/parental   # 0 hit
```

## Cas limites
Horloge de la TV reculée en cours de journée : `day` vient de l'appelant (`TvClock`), les tranches reçoivent un `minuteOfDay` cohérent ; application sans libellé : `pkg` ; TV sans `UsageStatsManager` : `UsageSlots` reste vide (le rapport dira « indisponible », w6-07).

## À ne pas faire
Ne pas toucher `ParentalEngine` ; aucun contenu d'application (fenêtre, texte, URL) ; pas d'Android ; pas de nouvelle permission.

## Rapport
`STATUT`, API publique (pour w6-07, w6-08, w6-13), taille mesurée des sérialisations.
