# w21-06 — `/admin/kpi` : section « POC en ligne » (k = 5), gestion de la cohorte, vue « Mes TV de test », exports journalisés
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : haiku** · escalade : audit Opus **échantillon** (plancher k sur chaque case et chaque export, rôles, CSRF) · statut : **ATTEND la fusion de w21-02**
> **Groupe : W21-C** (ordre 3) · porte : `cd backend && mvn -q test -Dtest='AdminKpi*,KpiService*,PocEnLigne*'` puis `mvn -q test`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1 j) · exécutant le moins cher compétent : haiku (travail de motif : les dix sections existantes sont le modèle)

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 4.2 (k-anonymat, `owner_test`), § 7.4 (page). Branche `claude/w21-06-kpi-poc`. Rapport : `docs/agent-reports/haiku-w21-06.md`.

## Objectif (autonome)
Les pages `/admin/kpi` sont construites à partir du modèle `Kpi.Section` / `Tile` / `Chart` / `Table` (`backend/src/main/java/castbridge/server/telemetry/Kpi.java`), calculées par `KpiService.section(key, filter)` (`switch` sur `usage`, `cast`, …, `connectivite`) et rendues par `backend/src/main/java/castbridge/server/admin/AdminKpiPages.java` (filtres, export CSV `/admin/kpi/export`), en JSON par `GET /api/v1/admin/kpi/{section}`. w21-02 a créé `tech_rollup_hour`, `tech_rollup_day`, `play_metric_*`, `poc_cohort`, `tech_export_audit` et `GET/POST/DELETE /api/v1/admin/tech/cohort`. Ajouter la section `poc-en-ligne` en suivant **exactement** ce modèle.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/telemetry/tech/PocEnLigneSection.java` (calcul de la section) ; gabarit Thymeleaf de la sous-page cohorte si le projet en utilise (même dossier que les gabarits `/admin/kpi`) ; tests `backend/src/test/java/castbridge/server/telemetry/tech/PocEnLigne*Test.java`.
- **Zone additive** : `Kpi.java` (`SECTIONS` + `poc-en-ligne`, « POC en ligne ») ; `KpiService.java` (une branche du `switch` qui délègue à `PocEnLigneSection`) ; `AdminKpiPages.java` (filtre additionnel `via` facultatif ; export de cette section écrit une ligne `tech_export_audit`) ; gabarit de la page KPI (onglet).
- **Interdit** : V62 et toute migration ; `TelemetryService`, `TechValidator` (w21-02) ; `SecurityConfig.java` ; `android/`, `server-play/`.

## Étapes
1. **Rouge** (sortie collée) : `PocEnLigneSectionTest.cellUnderFiveDevicesIsHidden`.
2. Tuiles : TV de la cohorte actives (7 j), parties Internet, part d'annonces en retard (M-12), part de révélations > 2 s (M-14), part d'`ack` > 1,5 s (M-15), part de rafales < 40 kbps sur `bt_gw` (M-02), dernière heure reçue du service (M-52 / collecteur).
3. Graphiques (`Kpi.Chart`, rendus par l'`admin.js` existant) : RTT par classe (parts au-delà de 400/1 000/1 500/2 000 ms), marge des annonces, durée des coupures, ouverture décomposée, refus `PLAY_*` par jour (service).
4. Tableaux : refus `PLAY_*` × étape, plafonds atteints, fermetures, taille d'un `state` par nombre de joueurs, dernières recommandations (lecture d'un fichier `out/recommandations-*.csv` **non** : seulement les règles calculées en SQL simple ; le rapport complet reste `tools/analysis`).
5. **Plancher k = 5** : toute case (tuile, point de graphique, cellule de tableau, ligne d'export) dont `devices < 5` est remplacée par « < 5 appareils » / valeur nulle ; les métriques du service (sans appareil) n'y sont pas soumises.
6. Sous-page « Mes TV de test » : appareils `poc_cohort.owner_test = true` seulement, identifiés par l'étiquette admin existante ou fabricant + modèle (jamais le nom donné par l'utilisateur, qui n'est pas stocké) ; gestion de la cohorte (ajouter par identifiant d'appareil, cocher `owner_test`, retirer) par formulaires CSRF existants appelant le service de w21-02.
7. **Vert**.

## Critères d'acceptation (JVM ; mutations appliquées puis retirées)
- `PocEnLigneSectionTest.cellUnderFiveDevicesIsHidden` : une classe à 4 appareils ⇒ masquée dans tuile, graphique, tableau et export CSV (mutation : seuil 4 ⇒ échec) ; à 5 ⇒ visible.
- `PocEnLigneSectionTest.serviceMetricsNotMasked` ; `ownerTestOnlyListsOwnerDevices` (un appareil de cohorte non `owner_test` n'apparaît jamais dans la sous-page).
- `PocEnLigneExportAuditTest` : un export ⇒ une ligne `tech_export_audit` (admin, action, paramètres, nombre de lignes).
- Rôles : `/admin/kpi?section=poc-en-ligne` sans session ⇒ redirection vers la connexion ; JSON sans jeton ⇒ 401 (chaînes existantes, aucun changement de `SecurityConfig`).
- Les dix sections existantes : réponses JSON **identiques** avant/après (test de non-régression sur une base de test peuplée).

## Interdits
Aucune donnée par appareil hors `owner_test` ; aucun `device_id` dans le HTML ni le JSON de la section ; aucun script externe (CSP existante).

## Rapport
Rouge, vert, mutations, capture textuelle de la section sur base de test, cases masquées.
