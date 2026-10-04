# w21-02 — API : familles techniques au catalogue fermé, cohorte POC, quotas, contrôles de cohérence, migration V62, agrégats horaires/journaliers, purges, directive `pocMetrics`, vues
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (ingestion, consentement, cohorte, effacement, k-anonymat des vues) · statut : **PRÊT**
> **Groupe : W21-A** (ordre 1, en parallèle de w21-01 et w21-03) · prérequis : `integration/agents` ≥ `774afb31` · porte : `cd backend && mvn -q test -Dtest='Telemetry*,Tech*,EventCatalog*'` puis `mvn -q test` complet
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 3.3 (évènements), § 4 (confidentialité), § 6 (stockage V62), § 7.1 (vues), § 9 (sécurité). Branche `claude/w21-02-api-tech-ingest`. Rapport : `docs/agent-reports/sonnet-w21-02.md`. Livraison de production : **server-1.1.2** (acte du propriétaire, jamais par l'exécutant).

## Objectif (autonome)
`POST /api/v1/events/batch` (`backend/src/main/java/castbridge/server/telemetry/TelemetryController.java`) valide chaque évènement contre `EventCatalog` (liste blanche, scalaires, clés interdites, consentement) puis l'écrit dans `telemetry_event` (13 mois) et met à jour les `kpi_*`. Ajouter les **13 évènements techniques** (`net.conn`, `net.link`, `play.game`, `play.timing`, `play.fail`, `link.gw`, `link.wd`, `perf.hour`, `perf.quiz`, `perf.thumb`, `sync.xfer`, `sync.pair`, `sync.relay` réservé), **refusés** sans consentement « usage » **ou** hors cohorte POC, bornés par un quota par appareil et des contrôles de cohérence, conservés 30 jours en brut, agrégés par heure (30 j) et par jour (12 mois, cases ≥ 5 appareils), et renvoyer `pocMetrics` dans les directives du heartbeat.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/resources/db/migration/V62__tech_metrics.sql` (tables `poc_cohort`, `tech_quota`, `tech_rollup_hour`, `tech_rollup_day`, `play_metric_hour`, `play_metric_day`, `tech_export_audit` + vues `v_tech_hist_day`, `v_play_game_funnel`, `v_play_srv_day` ; forme du § 6, à écrire sans `LIKE` si besoin) ; `backend/src/main/java/castbridge/server/telemetry/tech/{TechCatalog,TechValidator,TechCohortService,TechQuota,TechRollupJob,AdminTechController}.java` ; `backend/src/main/resources/tech-metrics.json` (**copie** de `tools/analysis/catalog/tech-metrics.json`, parité testée) ; tests `backend/src/test/java/castbridge/server/telemetry/tech/*Test.java`.
- **Zone additive** : `EventCatalog.java` (enregistre les définitions de `TechCatalog`, `dayCounter=false`) ; `TelemetryService.java` (dans `validate` : si famille technique ⇒ consentement, cohorte, cohérence ; dans `store` : quota par appareil ; dans `nightly` : purge par famille à 30 j **avant** la purge générale) ; `DeviceService.java` / record `Directives` (champ `pocMetrics`, absent = faux, ancien client inchangé) ; `backend/src/main/resources/application.yml` (une ligne `castbridge.tech.pid-salt: ${CASTBRIDGE_TECH_PID_SALT:}` ; w21-03b y ajoutera la sienne **après** cette fusion) ; `docs/TELEMETRY.md` **non** (w21-09).
- **Interdit** : `V1…V61`, `B/licenses/**`, `B/play/**`, `server-play/`, `android/`, `AdminKpiPages`/`KpiService` (w21-06), route d'ingestion du service de jeu (w21-03b).

## Étapes
1. **Rouge** (sortie collée) : `TechIngestTest.techEventRefusedOutsideCohort` (aucune famille technique n'existe : refus « événement inconnu », mais le test attend le motif « hors cohorte POC »).
2. Vérifier le numéro (aucune migration ≥ V62 sur aucune branche : `git log --all --oneline -- backend/src/main/resources/db/migration`) ; écrire V62 (additive, rétrocompatible) ; vérifier `spring.flyway.ignore-migration-patterns` dans `application.yml` (défaut Spring Boot `*:future`) et l'écrire dans le rapport (retour arrière vers 1.1.1 possible ?).
3. `TechCatalog` lit `tech-metrics.json` au démarrage (échec ⇒ refus de démarrer) et produit des `EventCatalog.Def` (propriétés `INT`/`ENUM`, bornes) ; `dim1`/`dim2` comme au § 3.3.
4. `TechValidator` (pur, testable) : consentement, cohorte (`TechCohortService.member(deviceId)`, cache 60 s), cohérence : pour chaque histogramme `Σ cases = n`, `n ≤ 10 000`, parts et maxima plausibles (`ms` de session ≥ somme des coupures ; `late ≤ questions` ; `phones ≤ 8`) ; motifs de refus courts et fermés : `hors cohorte POC`, `statistiques d'usage non consenties`, `incohérent`, `quota technique du jour`, `capacité`.
5. `TechQuota` : `tech_quota` (upsert) ; 400 évènements **et** 100 Ko de `props` par appareil et par jour ; plafond global 200 000 lignes brutes techniques (compte mis en cache 5 min) ⇒ refus `capacité` + une ligne de journal par heure au plus.
6. `TechRollupJob` : `@Scheduled` à `:07` chaque heure, reconstruction **idempotente** des 2 heures précédentes de `tech_rollup_hour` (supprimer puis insérer ; `devices = count(distinct device_id)`, cases fusionnées par somme, `dev_median` = médiane des médianes par appareil) ; 03:50 (Africa/Douala) : `tech_rollup_day` de la veille avec cases < 5 appareils fusionnées en `*`, purges (brut technique 30 j, horaires 30 j, `play_metric_hour` 90 j, journaliers 395 j).
7. `AdminTechController` (`/api/v1/admin/tech/**`, `ROLE_ADMIN` existant) : `GET/POST/DELETE cohort` (ajout par `deviceId`, `ownerTest` booléen, `added_by` = principal), `GET rollups?from&to&metric&via&cell` (k = 5 appliqué ; exception `ownerTest` seulement avec `owner=1`), chaque lecture de `rollups` écrite dans `tech_export_audit` ; jamais de `device_id` en sortie : pseudonyme `pid = HMAC-SHA256(sel, device_id‖AAAA-MM)` tronqué à 12 hexadécimaux (sel : propriété `castbridge.tech.pid-salt`, aléatoire au premier démarrage si absente, jamais journalisée) et seulement pour `owner=1`.
8. Directive `pocMetrics` = appartenance à la cohorte **et** consentement usage.
9. **Vert** + suite complète.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `TechIngestTest` : hors cohorte ⇒ refus motif exact (mutation : retirer le contrôle ⇒ échec) ; sans consentement ⇒ refus (mutation) ; en cohorte + consentement ⇒ accepté, ligne dans `telemetry_event` avec `dim1/dim2` attendus, **rien** dans `kpi_event_day` ; clé interdite (`ip`, `name`) dans un évènement technique ⇒ refus de l'évènement entier ; propriété non listée ⇒ ignorée ; histogramme incohérent (`Σ ≠ n`) ⇒ refus `incohérent`.
- `TechQuotaTest` : 401e évènement du jour ⇒ refus `quota technique du jour` ; le jour suivant ⇒ accepté ; plafond global ⇒ `capacité`.
- `TechRollupJobTest` : 6 appareils, 1 aberrant à 20 % des échantillons ⇒ `devices = 6`, cases = somme, `dev_median` correcte ; rejouer le job ⇒ mêmes lignes (idempotence) ; effacement d'un appareil (`DELETE /api/v1/devices/me`) puis job ⇒ l'appareil a disparu des horaires ; journalier avec 3 appareils dans une case ⇒ fusionnée en `*`.
- `RetentionTest` : brut technique de 31 jours purgé, brut d'usage de 31 jours **gardé** (mutation : purge globale ⇒ échec).
- `AdminTechControllerTest` : sans jeton admin ⇒ 401 ; case à 4 appareils masquée ; aucun champ `device_id`/`deviceId` dans aucune réponse ; une ligne `tech_export_audit` par lecture.
- `DirectivesTest` : ancien client ⇒ JSON identique + `pocMetrics:false` ; membre consentant ⇒ `true`.
- `TechCatalogParityTest` : `backend/src/main/resources/tech-metrics.json` = `tools/analysis/catalog/tech-metrics.json` (si w21-01 n'est pas encore fusionné : test marqué `@Disabled("parité à la fusion de w21-01")` et signalé dans le rapport).
- Suite `mvn test` complète verte ; migration appliquée sur la base de test (H2 ou MySQL de test du projet, selon l'existant).

## Interdits
Aucune connexion à la production ; aucun `--apply` ; aucune modification de `ConsentText` ; aucun champ texte libre ; ne jamais journaliser `props` ni le corps d'un lot.

## Rapport
Rouge, vert, mutations, plan de V62 (taille estimée par ligne), réponse sur Flyway/retour arrière, liste des motifs de refus, ce qui reste à la fusion de w21-01.
