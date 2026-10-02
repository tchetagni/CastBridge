# w12-10 — Serveur : télémétrie des expériences (`exp` dans la liste blanche, `settings_applied` essentiel), agrégats `kpi_experiment_day`, page `/admin/kpi/experiments` (bras × métriques, garde-fous, « clore » ⇒ brouillon), export CSV
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (vie privée) · statut : PRÊT
> **Groupe : W12-d** (vague W12) · prérequis : w12-03 fusionné (registre `settings_experiment`, migration) ; w12-02 pour le sens de `exp` ; `B/telemetry/EventCatalog.java` **après** w5-16 / w10-07 s'ils sont lancés · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='ExperimentKpi*Test,EventCatalog*Test'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : échantillon

**Vague 12d (mesure) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.8, § 3.2-3.4, § 4.1 (5), § 4.3. Branche `claude/sonnet-w12-10`. Rapport : `docs/agent-reports/sonnet-w12-10.md`.

## Objectif
(1) `EventCatalog` : propriété **`exp`** (code `[a-z0-9-]{1,24}:[a-d](,…)*` ≤ 64) acceptée sur les événements **d'usage** uniquement (`shop_view`, `shop_order`, `tokens_spend` quand ils existeront ; aujourd'hui `playback_end`, `quiz_game`, `content_stat`, `feature_used`) ; événement **essentiel** `settings_applied{seq, kid, schema, stale}` ; **refus** d'`exp` sur un événement essentiel (test) ; (2) agrégation quotidienne `kpi_experiment_day (exp, arm, day, metric, n, value)` depuis `telemetry_event` (métriques : `devices`, `events`, `playback_pct_median`, `content_completions`, `quiz_games`) et depuis les tables **comptables si présentes** (`shop_order` W5, journal des agents W4-C : `conversions`, `renewals`, `token_purchases` — via une interface `ExperimentSaleSource` à **une** implémentation vide tant que W4-C/W5 ne sont pas fusionnés) ; (3) page `/admin/kpi/experiments` : par expérience (registre w12-03) : bras × métriques, fenêtre, N par bras, garde-fou (vert/rouge **textuel**), seuils go/no-go, bouton « clore avec le bras … » ⇒ `POST …/experiments/{name}/close` (brouillon, jamais une signature) ; export CSV ; (4) migration `V<plus haut + 1>__kpi_experiments.sql` (**après** celle de w12-03 : vérifier `git log --all`).

## Pourquoi (preuves)
- Catalogue et consentement : `B/telemetry/EventCatalog.java:31-35` (drapeau `essential`), `:81` (`playback_end`), `:117-118` (`content_stat`) ; refus des événements non consentis `TelemetryService.java:120` ; agrégats existants `kpi_*` (`V3__telemetry.sql:35-79`), pages `B/admin/AdminKpiPages.java:62,78`, API `B/telemetry/AdminKpiController.java:13-42`.
- Rétention `telemetry_event` 13 mois (`docs/TELEMETRY.md:137-150`) ; clés interdites `:55-58` (aucune ne doit entrer).
- Décision : aucune décision automatique (§ 3.4) ; seuils W10 § 10.2.

## Fichiers possédés
- Existants : `B/telemetry/EventCatalog.java` (deux ajouts), `B/telemetry/TelemetryService.java` (**une** ligne : alimentation de l'agrégat ; la ligne `SettingsDeviceSink` est w12-03), `B/admin/AdminKpiPages.java` (une route), `TPL/kpi-experiments.html` (nouveau), `docs/TELEMETRY.md` § 5 (indicateurs).
- Nouveaux : `B/telemetry/ExperimentKpiService.java`, `B/telemetry/ExperimentSaleSource.java` (+ `NoSalesSource`), `B/telemetry/AdminExperimentKpiController.java` (`GET /api/v1/admin/kpi/experiments[?exp=]`, `.csv`), `backend/src/main/resources/db/migration/V<n>__kpi_experiments.sql`, `BT/telemetry/{ExperimentKpiServiceTest,EventCatalogExpTest,AdminExperimentKpiControllerTest}.java`.
- Hors zone : `B/settings/**` (w12-03 : appeler `SettingsService.experiments()`), pages `/admin/settings` (w12-04), apps.

## Étapes
1. `EventCatalog` : `exp` + `settings_applied` ; tests : `exp` refusé sur `crash` ; `exp` mal formé ignoré (propriété retirée, événement gardé) ; `settings_applied` accepté sans consentement « statistiques ».
2. Migration ; `ExperimentKpiService.aggregate(day)` (tâche quotidienne existante des `kpi_*` : s'y accrocher) ; médiane de `pct` par bras ; `devices` distincts (par `deviceId`, jamais exporté).
3. Page et API ; CSV ; `close` ⇒ appel du service w12-03.
4. Tests avec jeu de données synthétique (2 bras, 30 appareils).

## Critères d'acceptation (hors ligne)
- Porte verte ; aucune clé interdite ; `exp` jamais sur un essentiel ; page sans JavaScript tiers ; rapport : numéro de migration, métriques disponibles aujourd'hui vs « à la fusion de W4-C/W5 ».
