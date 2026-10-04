# w21-01 — Catalogue des métriques techniques (JSON de parité) et cœur pur `TechMetrics` : histogrammes à bornes fixes, enregistreurs, quotas, porte d'envoi `UploadGate`
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (aucune clé interdite, objet nul hors cohorte, budget) · statut : **PRÊT** (cœur pur : permis pendant le gel)
> **Groupe : W21-A** (ordre 1, en parallèle de w21-02 et w21-03) · prérequis : `integration/agents` ≥ `774afb31` · porte : `:core:test --tests 'castbridge.core.telemetry.*'` (par `tools/agents/gradle-lock.sh`) + `python3 -m pytest tools/analysis/tests/test_catalog.py`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 2 (catalogue), § 3 (cœur, bornes, évènements, porte d'envoi, cohorte). Branche `claude/w21-01-tech-metrics-core`. Rapport : `docs/agent-reports/sonnet-w21-01.md`.

## Objectif (autonome)
La télémétrie d'usage existe (`android/core/src/main/kotlin/castbridge/core/telemetry/{Telemetry,TelemetryUploader}.kt`, `docs/TELEMETRY.md`) : catalogue fermé, consentement, file bornée, envoi par lots à `POST /api/v1/events/batch`. Le serveur n'accepte que des **valeurs scalaires** dans `props` (≤ 2 000 caractères) et des noms d'évènement ≤ 32 caractères. Livrer, **dans le cœur, pur JVM**, ce qui permet à une TV (ou un téléphone) de mesurer passivement sa liaison et ses performances, d'agréger sur l'appareil en histogrammes à bornes fixes et d'émettre au plus quelques évènements compacts, **sans jamais gêner une partie Internet**, et seulement si le consentement « statistiques d'usage » **et** la directive serveur `pocMetrics` sont vrais.

## Fichiers possédés
- **Nouveaux** : `tools/analysis/catalog/tech-metrics.json` (source unique : 13 évènements, propriétés, bornes `TechBuckets`, dimensions fermées `via`/`cell`/codes, règles de décision du § 2 avec seuils) ; `android/core/src/main/kotlin/castbridge/core/telemetry/tech/{TechBuckets,Histogram,TechCatalog,TechMetrics,SessionRecorder,HourRecorder,TechQuota,UploadGate,NetClass}.kt` ; tests `android/core/src/test/kotlin/castbridge/core/telemetry/tech/*Test.kt` ; `tools/analysis/tests/test_catalog.py` (le JSON est bien formé, bornes croissantes, noms ≤ 32, chaque évènement au maximum ≤ 2 000 caractères de `props`).
- **Zone additive** : `TelemetryUploader.kt` : `flush(…, maxBytes: Int = 1_500_000, maxEvents: Int = 500)` (défauts = comportement actuel, octet pour octet) ; `Telemetry.kt` : `EventCatalog` côté app accepte les familles techniques **seulement** via `TechCatalog` (liste blanche lue du même contenu que le JSON : copie compilée + test de parité).
- **Interdit** : `R/`, `S/`, `backend/`, `server-play/`, `C/quiz/**` (les points de mesure sont posés par w21-04), `C/connect/**`.

## Étapes
1. **Rouge** (sortie collée) : `HistogramTest.mergeIsExactSum` (la classe n'existe pas).
2. `TechBuckets` : tableaux LAT, LEAD, KBPS, OUT, SET, UI, FRAC **exactement** comme au § 3.2 ; `Histogram(bounds)` : `IntArray` fixe, `add(v)`, `merge`, `count`, `fractionAbove(threshold)` (exacte si le seuil est une borne, sinon `IllegalArgumentException`), `percentile(p)` interpolé, `toProps(prefix)` ⇒ `prefix0…prefixK-1` + `prefixn`, `fromProps`.
3. `NetClass` : `via` ∈ {ethernet, wifi, hotspot, bt_gw, none}, `cell` ∈ {2g, 3g, 4g, 5g, unk, -} ; `measured(rttMedianMs, kbpsMedian)` ⇒ bande (`2g` < 150 kbps, `3g` < 2 000, `4g` < 50 000, `5g` au-delà ; `unk` sans échantillon).
4. `SessionRecorder` (une session de jeu Internet) : `rtt(ms)`, `rx(bytes, ms)` (rafales ≥ 2 Ko seulement), `tx(bytes, ms)`, `cut(startMono)`/`resumed(kind)`, `announcement(leadMs)`, `reveal(ms)`, `ack(ms)`, `answerFraction(pct)`, `localRelay(ms)`, `ackResult(code)`, `signal(level, cause, ms)`, `bytesIn(type, n)`, `phones(n)`, `close(end)` ⇒ liste d'évènements `net.link`, `play.game`, `play.timing` (formes du § 3.3) ; `ConnRecorder` léger pour `net.conn` ; aucune horloge murale (horloge monotone injectée).
5. `HourRecorder` : tas (échantillon par minute ⇒ p95, max), `lowmem`, `jank`, `frameP95`, saccades avec/sans copie, octets de télémétrie par voie, changements de route ; `perf.hour` toutes les heures d'usage ; `perf.quiz`, `perf.thumb`, `link.gw`, `link.wd`, `sync.xfer`, `sync.pair`, `play.fail` (≤ 20 / jour) comme constructeurs simples.
6. `TechMetrics(enabled: () -> Boolean, sink: (name, props) -> Boolean, clock)` : `enabled()` faux ⇒ **objet nul** (aucune allocation d'enregistreur, aucune mesure) ; au plus 16 enregistreurs ouverts ; anneau de 64 évènements en attente (P2 puis P1 abandonnés avant P0) ; `TechQuota` : 20 Ko compressés estimés par jour (taille JSON × 0,35) ; refuse localement toute clé de `EventCatalog.FORBIDDEN`.
7. `UploadGate.decide(facts)` ⇒ `Hold` | `Send(maxBytes, maxEvents, includeTech)` : partie Internet en cours ou fin < 30 s ⇒ `Hold` (toute télémétrie) ; copie active ⇒ `includeTech=false` ; voie `bt_gw` ⇒ `maxBytes` 8 192 compressés, ≥ 5 min entre deux lots, techniques après les autres ; sinon comportement actuel.
8. **Vert**.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées, résultat dans le rapport)
- `HistogramTest` : fusion = somme exacte ; `fractionAbove(1500)` exacte sur LAT ; seuil hors borne refusé ; `toProps`/`fromProps` aller-retour ; mutation « borne décalée » ⇒ échec.
- `TechCatalogParityTest` : la copie compilée = `tools/analysis/catalog/tech-metrics.json` (mutation : retirer une propriété ⇒ échec) ; chaque évènement rempli au maximum ⇒ `props` JSON ≤ 2 000 caractères et nom ≤ 32.
- `TechMetricsTest.disabledIsNull` : `enabled=false` ⇒ 0 évènement, 0 enregistreur alloué (compteur interne), même après 10 000 appels (mutation : ignorer `enabled` ⇒ échec) ; `forbiddenKeyNeverLeaves` ; `quotaDropsP2ThenP1KeepsP0`.
- `SessionRecorderTest` : partie simulée de 10 questions avec 2 annonces en retard ⇒ `late = 2`, `l0+l1+l2+l3 = 2` ; coupure de 12 s ⇒ `o4 = 1` (10-15 s) ; aucune propriété textuelle hors liste fermée.
- `UploadGateTest` : pendant une partie ⇒ `Hold` même pour `crash` (mutation : laisser passer les essentiels ⇒ échec) ; `bt_gw` ⇒ ≤ 8 192 ; Wi-Fi ⇒ défauts actuels.
- `TelemetryUploaderTest` existants **verts sans modification** (défauts inchangés).
- Taille : un `SessionRecorder` plein < 8 Ko de tas (mesure par sérialisation des tableaux, consignée).

## Interdits
Aucune dépendance nouvelle ; aucun réseau ; aucune sonde active ; aucun identifiant neuf ; ne pas changer `ConsentText`. Règle de symbiose W19 : additif seulement.

## Rapport
Sortie rouge, sortie verte, mutations, taille JSON maximale de chaque évènement, taille de tas mesurée, écarts avec le § 3.3 (s'il y en a : justifiés).
