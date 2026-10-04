# Serveur 1.2.1 : suivi des activations (V63, éteint), dates MySQL tolérantes, correctifs de tests

> Préparé le 2026-10-04. **Non déployé.** Reprend le cadre de `docs/DEPLOIEMENT-SERVEUR-1.2.0-PORTEFEUILLE.md` (sauvegarde automatique de la base par `deploy-server.sh`, retour arrière automatique si la santé échoue, `secrets/` et `docker-compose.override.yml` recopiés depuis la release en service). Le serveur en service est le `1.2.0` (V62 appliquée, licences et portefeuille allumés depuis le 2026-10-04 13:57).

## 1. Contenu de la livraison

| Élément | Effet en production |
|---|---|
| Migration `V63__activation_tracking.sql` (23 tables `act_*`) | Ajoute des tables, ne modifie aucune table existante. Durée attendue : sous la seconde. |
| Module `castbridge.server.activations` | **ÉTEINT par défaut** (`CASTBRIDGE_ACTIVATIONS_ENABLED=false`) : routes en 404, aucune tâche planifiée, aucune clé nécessaire. Rien ne change pour les TV et les téléphones. |
| `common/Times` (convertisseur de dates tolérant) | Corrige un défaut **déjà présent en production** : le pilote MySQL renvoie `LocalDateTime` pour les colonnes `DATETIME` et le code forçait `Timestamp` (`ClassCastException` ⇒ erreur 500) dans `orders/OrderService` (5 endroits). Les routes des commandes différées passent donc de « 500 sur MySQL » à « fonctionnent ». Ces routes ne sont pas utilisées aujourd'hui. |
| Tests | Banc de concurrence du grand livre corrigé (défaut du test, pas de la production), répétition V61 → V62 → rollback visant V62. |

**Hors livraison, connu :** `OrderService.release` se bloque sur MySQL quand deux libérations arrivent en même temps (erreur 500) : test désactivé et expliqué, à corriger plus tard. Le suivi des activations n'est pas allumé ici : l'allumer demande les clés `act-*` (hors de cette livraison, voir `docs/ACTIVATION-TRACKING.md`).

## 2. Ce qui est prouvé (MySQL 8.4 réel, Testcontainers)

- `ActivationsMySqlTest` : 6 tests verts avec les paramètres JDBC de production (journal, rapport, 16 écrivains, deux journaux concurrents, exports, archive, réconciliation, retour arrière sur un schéma Flyway réel).
- `OrdersMySqlTest` : le défaut des dates est rouge avant le correctif, vert après.
- `WalletMySqlContainerTest` : oracle sur 2 000 suites aléatoires et concurrence 16 × 500 verts.
- Suite complète du serveur : voir le résultat dans le rapport de livraison (le nombre de tests change à chaque lot).

## 3. Avant le jour J (sur le Mac)

1. `python3 tools/release/check_versions.py` : 0 erreur (serveur 1.2.1, code 5).
2. La branche est poussée sur GitHub (`git push origin integration/agents`, puis `git fetch origin`) : le script refuse une révision qu'aucune branche `origin/*` n'atteint.
3. Arbre suivi propre : `git status --short | grep -v '^??'` vide.

## 4. Déploiement (commandes à lancer par le propriétaire avec le préfixe `!` si le contrôle automatique refuse à l'assistant)

```sh
git tag -a server-1.2.1 -m "serveur 1.2.1 : suivi des activations V63 (éteint), dates MySQL tolérantes" HEAD
bash tools/release/deploy-server.sh server-1.2.1                # plan à blanc : relire (référence, version 1.2.1, aucune ligne BLOQUANT)
bash tools/release/deploy-server.sh --status --apply            # état avant : 1.2.0
bash tools/release/deploy-server.sh server-1.2.1 --apply        # sauvegarde de la base, construction, démarrage, santé
bash tools/release/deploy-server.sh --status --apply            # état après : version 1.2.1, santé healthy, previous 1.2.0
```

Coupure d'API de 30 à 90 secondes pendant le démarrage. Les modules licences et portefeuille restent allumés : l'override et les secrets de `current` sont recopiés par le script.

## 5. Vérifications après déploiement

```sh
ssh ubuntu@bridge.sti-cm.com
sudo docker exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE \"act\\_%\"; SELECT COUNT(*) FROM device;"'
# attendu : 63 1 / 62 1 / 61 1 ; 23 ; le même nombre d'appareils qu'avant
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:7090/api/v1/admin/activations/summary   # 404 (module éteint)
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:7090/api/v1/wallet/policy               # 401 (portefeuille allumé, exige l'appareil)
curl -s http://127.0.0.1:7090/api/v1/revocations | cut -c1-12                                     # cbx1…
sudo docker logs --since 5m castbridge-api 2>&1 | grep -i -E "ERROR|licence module|wallet" | tail -6
cd /home/ubuntu/castbridge/current && ./smoke-test.sh                                              # aucun ÉCHEC
```

## 6. Retour arrière (du plus léger au plus lourd)

1. Rien à éteindre : le module des activations est déjà éteint.
2. `bash tools/release/deploy-server.sh --rollback --apply` : revient à l'image 1.2.0. Flyway de l'ancien code accepte une base qui a V63 (les tables `act_*` restent, inertes).
3. `tools/activations/rollback-V63.sql` seulement pour retirer les 23 tables et la ligne Flyway 63 (lu au tag avec `git --git-dir=/home/ubuntu/castbridge/castbridge.git show server-1.2.1:tools/activations/rollback-V63.sql`, car le déploiement n'extrait que `backend/`). **Aucune donnée n'y est écrite tant que le module est éteint.**

## 7. Perte de données

Aucune : migration additive, sauvegarde automatique avant migration (`/var/backups/castbridge/db/castbridge-AAAAMMJJ-HHMMSS.sql.gz`, à noter), retour arrière prouvé.

## 8. Écarts restants avec la production (à lever avant 2027)

- Suivi des activations éteint : à allumer avec ses clés `act-*` (sauvegarde hors serveur) et la TV qui rapporte (cahier w23-04).
- `OrderService.release` : interblocage sous concurrence sur MySQL.
- Le service de jeu en production (clé de ticket, route `/play/`, révocations réelles) est un chantier à part.

## APPROBATIONS À OBTENIR DU PROPRIÉTAIRE (oui / non)

1. Créneau : deux coupures d'API de 30 à 90 secondes au plus (une seule ici).
2. Lancer les commandes du § 4 avec `!` (ou ajouter une règle de permission pour `deploy-server.sh`).
