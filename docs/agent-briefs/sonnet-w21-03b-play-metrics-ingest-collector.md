# w21-03b — Pont sans secret dans le service : route API `POST /api/v1/ingest/play-metrics` (clé dédiée, locale seulement) et collecteur côté hôte (cron, `docker cp`, DRY-RUN testé)
<!-- routage architecte 2026-10-04 (W21 données techniques du POC) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (authentification par clé, refus de tout passage par nginx, idempotence, validation stricte, aucune clé dans le service de jeu) · statut : **ATTEND la fusion de w21-02** (tables `play_metric_*`)
> **Groupe : W21-A** (ordre 2) · porte : `cd backend && mvn -q test -Dtest='PlayMetricsIngest*'` + `bash tools/analysis/tests/test_collect_play_metrics.sh`
> **Jauge : ≈ 200 k jetons entrée / 12 k sortie** (effort S, ≈ 0,7 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-W21-DONNEES-TECHNIQUES-POC-2026-10-04.md` § 5.2 (option (d)), § 9. Branche `claude/w21-03b-play-metrics-ingest`. Rapport : `docs/agent-reports/sonnet-w21-03b.md`. Livraison : **server-1.1.2** (avec w21-02) ; cron et clé : actes du propriétaire (runbook écrit par w21-09).

## Objectif (autonome)
Le service de jeu écrit chaque heure un fichier `m-<AAAAMMJJHH>-<instance>.json` dans `/var/lib/castbridge-play/metrics/` (w21-03, format `{"v":1,"instance","hour","version","metrics":[{"m","d1","d2","cnt","sum","max","b"}]}`). Il ne doit recevoir **aucun** identifiant. Livrer : (1) une route de l'API qui accepte ces fichiers **seulement** d'un appelant local muni d'une clé dédiée ; (2) un script hôte qui les copie hors du conteneur et les pousse.

## Fichiers possédés
- **Nouveaux** : `backend/src/main/java/castbridge/server/telemetry/tech/{PlayMetricsIngestController,PlayMetricsIngestService}.java` ; tests `backend/src/test/java/castbridge/server/telemetry/tech/PlayMetricsIngest*Test.java` ; `tools/analysis/collect_play_metrics.sh` ; `tools/analysis/tests/test_collect_play_metrics.sh` (faux `sudo`, faux `docker`, faux `curl`, aucune connexion).
- **Zone additive** : `backend/src/main/resources/application.yml` (`castbridge.play.metrics-key-sha256: ${CASTBRIDGE_PLAY_METRICS_KEY_SHA256:}`) ; `CastbridgeProperties.java` si c'est le modèle du projet.
- **Interdit** : `server-play/**` (aucune clé, aucune variable de clé), `V62` (w21-02), `SecurityConfig.java` (la route est sous `/api/**` en `permitAll` : la clé est vérifiée par le contrôleur), `/opt/infra`, nginx.

## Étapes
1. **Rouge** (sortie collée) : `PlayMetricsIngestTest.refusedWhenForwardedByNginx`.
2. Contrôleur `POST /api/v1/ingest/play-metrics` : empreinte absente ⇒ **404** (route inexistante) ; en-tête `X-Forwarded-For` présent ⇒ **404** (nginx en pose toujours un : seule une connexion locale directe à `127.0.0.1:7090` passe) ; `Authorization: Bearer <clé>` comparée à l'empreinte SHA-256 par `MessageDigest.isEqual` ; échec ⇒ 401 sans détail ; corps ≤ 64 Ko (lecture bornée comme `TelemetryController.MAX_BODY`).
3. Service : schéma strict (`v`=1 ; `instance` `[0-9a-f]{8}` ; `hour` ISO à l'heure pleine, ni futur > 1 h ni passé > 7 j ; `m`, `d1`, `d2` dans la liste fermée de `tech-metrics.json` section `service` ; nombres finis ≥ 0 bornés ; `Σ b = cnt` quand `b` est présent) ; **remplacement** idempotent des lignes `(stat_hour, instance)` dans `play_metric_hour` en une transaction ; réponse `{"rows":n}` sans écho ; journal : une ligne (heure, instance, rows), jamais le corps.
4. Script `collect_play_metrics.sh` (bash, `set -euo pipefail`, DRY-RUN par défaut, `--apply` pour agir) : `sudo docker cp castbridge-play:/var/lib/castbridge-play/metrics/. "$TMP"` ; pour chaque fichier absent de `~/castbridge/state/play-metrics.pushed` et dont l'heure est close : `curl -fsS --max-time 10 -H "Authorization: Bearer $(cat "$KEY")" -H 'Content-Type: application/json' --data-binary @"$f" http://127.0.0.1:7090/api/v1/ingest/play-metrics` ; succès ⇒ ajout au fichier d'état (borné aux 500 dernières lignes) ; `KEY` défaut `~/castbridge/secrets/play-metrics-ingest.key`, refus si droits ≠ 0600 ou si le chemin est sous `~/castbridge/services/play/` ; la clé n'apparaît jamais dans la sortie (`set +x` autour) ; nettoyage de `$TMP` par `trap`.
5. **Vert**.

## Critères d'acceptation (mutations appliquées puis retirées)
- `PlayMetricsIngestTest` : avec `X-Forwarded-For` ⇒ 404 (mutation : retirer le contrôle ⇒ échec) ; sans empreinte configurée ⇒ 404 ; mauvaise clé ⇒ 401 ; bonne clé ⇒ 200 et lignes ; renvoi du même fichier ⇒ mêmes lignes (pas de doublon) ; métrique hors liste ⇒ 400 ; `Σ b ≠ cnt` ⇒ 400 ; heure future ⇒ 400 ; corps de 65 Ko ⇒ 413.
- `PlayMetricsIngestTest.noBodyInLogs` : le journal capturé ne contient aucune valeur du corps.
- `test_collect_play_metrics.sh` : DRY-RUN n'appelle pas `curl` ; `--apply` pousse 2 fichiers clos, ignore l'heure en cours, ne repousse pas au second passage ; clé en 0644 ⇒ refus ; clé sous `services/play` ⇒ refus ; la clé n'apparaît pas dans la sortie capturée (mutation : `echo` de la clé ⇒ échec).
- Suite `mvn test` complète verte.

## Interdits
Aucune connexion au serveur ; aucune clé réelle générée ni écrite dans le dépôt ; ne pas modifier nginx ni le compose du service.

## Rapport
Rouge, vert, mutations, ligne de cron proposée (`*/15 * * * * …`), commande de génération de la clé et de son empreinte proposées pour le runbook (sans valeur).
