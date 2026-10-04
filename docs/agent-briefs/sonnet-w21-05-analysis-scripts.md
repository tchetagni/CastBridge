# w21-05 — Scripts d'analyse `tools/analysis/` : percentiles et parts exactes par classe de liaison, séries temporelles, entonnoir, anomalies, recommandations chiffrées avec confiance, rapport hebdomadaire
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (statistiques robustes, plancher k, aucune proposition hors borne) · statut : **ATTEND la fusion de w21-02** (forme des réponses `GET /api/v1/admin/tech/rollups`)
> **Groupe : W21-C** (ordre 3) · porte : `python3 -m pytest tools/analysis/tests -q`
> **Jauge : ≈ 350 k jetons entrée / 20 k sortie** (effort M, ≈ 1,2 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 2 (règles de décision), § 3.2 (bornes), § 7 (analyse, statistiques robustes), § 8.3 (chemin d'une valeur). Branche `claude/w21-05-analysis`. Rapport : `docs/agent-reports/sonnet-w21-05.md`.

## Objectif (autonome)
Les agrégats techniques sont servis par `GET /api/v1/admin/tech/rollups` (w21-02 ; jeton admin ; cases fusionnées par somme, `devices`, `dev_median`, k = 5 appliqué ; `owner=1` pour les TV de test du propriétaire) et les métriques du service sont dans `play_metric_*` (lues par la même API, section `service`). Le catalogue `tools/analysis/catalog/tech-metrics.json` (w21-01) porte les bornes et les **règles de décision** (paramètre, valeur actuelle, borne permise, seuil, sens). Livrer des scripts Python 3 **stdlib seulement** qui transforment ces agrégats en rapports et en recommandations chiffrées.

## Fichiers possédés
- **Nouveaux** : `tools/analysis/{techlib.py,tech_fetch.py,tech_percentiles.py,tech_timeseries.py,tech_funnel.py,tech_anomalies.py,tech_recommend.py,tech_weekly.py,README.md}` ; `tools/analysis/tests/{test_techlib.py,test_recommend.py,test_weekly.py,fixtures/*.json}`.
- **Zone additive** : `tools/analysis/catalog/tech-metrics.json` (seulement la section `rules` si une règle du § 2 y manque : ajout, jamais de changement de borne).
- **Interdit** : toute connexion à la base de production ; toute dépendance (`numpy`, `pandas`…) ; `backend/`, `android/`, `server-play/`.

## Étapes
1. **Rouge** (sortie collée) : `test_techlib.py::test_fraction_above_is_exact_on_bound`.
2. `techlib.py` : fusion de cases, `fraction_above(seuil)` exacte (seuil = borne, sinon erreur), percentile interpolé, intervalle de Wilson à 95 %, médiane des médianes, poids plafonné à 20 % par appareil (sur les données `owner=1` ou les `dev_median`), moyenne tronquée à 10 %, MAD.
3. `tech_fetch.py` : `--from --to --metric` ; URL de base et jeton lus dans `~/.castbridge/admin.env` (droits 0600 vérifiés ; jamais en argument, jamais affichés) ; écrit `out/rollups-<de>-<à>.json` ; mode `--fixture` pour les tests.
4. `tech_percentiles.py`, `tech_timeseries.py` (rupture = médiane glissante 24 h ± 3 MAD), `tech_funnel.py` (ouvertures → `welcome` → parties finies / perdues / quittées par classe), `tech_anomalies.py` (appareils `owner=1` à > 3 MAD ; classes sous k ; heures du service manquantes = collecteur en panne).
5. `tech_recommend.py` : pour chaque règle du catalogue : parts exactes, IC, `devices`, valeur poolée **et** médiane des médianes ; **confiance** `haute` (≥ 10 appareils et IC entièrement d'un côté du seuil, poolée et médiane d'accord), `moyenne` (≥ 5 appareils), `insuffisante` (aucune proposition) ; valeur proposée **toujours dans la borne** de la règle ; jamais de proposition pour une constante marquée `security` (`MAX_COMPENSATION_RTT_MS`, `MAX_RELAY_RTT_MS`, essai, relayés, tickets) : seulement « information » ; sortie `out/recommandations-<AAAA-Www>.md` + `.csv` (paramètre, actuelle, proposée, règle, n appareils, n échantillons, part ± IC, confiance).
6. `tech_weekly.py` : rapport Markdown (P0 du catalogue, recommandations, anomalies, budget de télémétrie M-50, santé de l'ingestion M-51/M-52, cases masquées par k) ; aucune donnée par appareil hors section « Mes TV de test » (pseudonymes `pid` seulement).
7. **Vert**.

## Critères d'acceptation (pytest ; mutations appliquées puis retirées)
- `test_techlib.py` : parts exactes sur borne, erreur hors borne ; Wilson correct sur 3 cas de référence ; poids plafonné : un appareil à 90 % des échantillons ne déplace pas la médiane des médianes (mutation : retirer le plafond ⇒ échec).
- `test_recommend.py` (fixtures) : `late%` = 3 % sur `bt_gw` avec 12 appareils ⇒ mode adaptatif proposé, confiance haute ; même cas avec 4 appareils ⇒ `insuffisante`, aucune valeur ; proposition du délai jamais hors 1 000-2 000 (mutation : borne retirée ⇒ échec) ; constante `security` ⇒ jamais de valeur proposée ; `fallbackIdleMs` : p95 coupures `bt_gw` > 40 s ⇒ 60 000 proposé.
- `test_weekly.py` : le rapport ne contient aucun champ `device_id`, aucune adresse, aucun nom ; cases < 5 appareils rendues « < 5 appareils ».
- `README.md` : commandes d'usage et exemple de sortie (fixtures).

## Interdits
Aucune connexion réseau dans les tests ; aucun jeton dans le dépôt ; aucune donnée réelle dans les fixtures.

## Rapport
Rouge, vert, mutations, exemple de rapport hebdomadaire sur fixtures, règles du catalogue non traitées (s'il y en a).
