# w1-12 — Supervision minimale et sauvegardes hors site (scripts et runbook, rien d'appliqué)

**Vague 1 · Effort S/M (≈ 1 j) · Statut PRÊT.** Branche `claude/sonnet-w1-12`. Rapport : `docs/agent-reports/sonnet-w1-12.md`. **Interdit** : toute connexion au serveur, toute commande `ssh`/`docker` réelle. Livrer des scripts **simulés par défaut** (`--apply` requis), testés localement.

## Objectif
Le propriétaire peut, en 30 minutes et sans budget, mettre en place : une sonde externe gratuite, un ping de cron pour la sauvegarde, un script hôte (disque, conteneurs, RAM) toutes les 10 min, une copie hors site chiffrée des sauvegardes, un runbook d'astreinte et un exercice de restauration trimestriel.

## Pourquoi (preuves)
- Aucun mot `uptime`, `healthchecks`, `alert`, `prometheus` dans `backend/README.md`, `docs/ADMIN.md`, `docs/HANDOFF.md` (hors « alertes d'abus » du module licences) : un disque plein, un certificat expiré, un cron de sauvegarde en échec sont invisibles.
- `backend/backup.sh:25-62` : dump quotidien, 14 j, **sur le même disque** ; `backend/README.md:259-266` dit seulement « copiez aussi… hors du VPS ».
- `backend/deploy.sh:52-66` : retour arrière d'image, pas de base ; exercice de restauration documenté, jamais planifié.
- Journaux : `json-file` 10 Mo × 3 (`docker-compose.yml:6-10`) ≈ 30 Mo d'historique.
- Serveur partagé avec d'autres projets (`docs/HANDOFF.md` § 4) : périmètre `~/castbridge/` et `castbridge.conf` seulement.
- Audit : OP-1, B4, B7, B9.

## Fichiers possédés
Nouveau dossier `ops/monitoring/` (`README.md`, `host-check.sh`, `offsite-backup.sh`, `healthchecks.env.example`, `cron.example`), `backend/backup.sh` (ajout d'un **crochet** optionnel), `backend/README.md` (§ « Supervision et astreinte »). **Hors zone** : `ops/tunnel/**`, `deploy.sh`, `docker-compose*.yml`, tout ce qui touche nginx.

## Étapes
1. `ops/monitoring/host-check.sh` (bash, POSIX-friendly) : vérifie `df` de `/` et `/var/lib/docker` (> 85 % = alerte), `docker inspect -f '{{.State.Health.Status}}' castbridge-api castbridge-db`, compteur de redémarrages (`RestartCount`), RAM libre < 100 Mo, expiration TLS de `bridge.sti-cm.com` < 14 j (`openssl s_client`), âge du dernier dump dans `~/castbridge/backups` > 26 h ; sur échec, `curl -fsS "$HC_URL/fail" --data-raw "<message>"`, sinon `curl -fsS "$HC_URL"`. Lire `HC_URL` depuis `ops/monitoring/healthchecks.env` (jamais commité : fournir `.example`). Mode `--dry-run` par défaut qui imprime ce qu'il ferait.
2. `ops/monitoring/offsite-backup.sh` : chiffre le dernier dump (`age` ou `gpg --symmetric` avec une phrase dans un fichier hors dépôt) puis `rclone copy` vers un remote nommé `castbridge-offsite` (B2/Scaleway/Drive : choix du propriétaire) ; conserve 30 jours ; ping healthchecks. `--apply` requis.
3. `backend/backup.sh` : à la fin, `[ -x "$OFFSITE_HOOK" ] && "$OFFSITE_HOOK"` (variable d'environnement, vide par défaut) : aucun comportement changé sans configuration.
4. `cron.example` : lignes pour `host-check.sh` (10 min), `backup.sh` existant (3 h 15) et `offsite-backup.sh` (3 h 45).
5. `backend/README.md` § « Supervision et astreinte » : sonde externe gratuite (UptimeRobot/Better Stack) sur `GET /api/v1/updates/public-key` (200 attendu) + contrôle TLS ; healthchecks.io ; les 5 commandes d'astreinte (`docker compose ps`, `logs --tail=200`, `./deploy.sh <rev précédente>`, commande de restauration déjà en README, `df -h`) ; rotation hebdomadaire des journaux `docker logs --since 24h > /var/log/castbridge/api-$(date +%F).log` avec `logrotate` 14 j ; **exercice de restauration trimestriel** (restaurer le dernier dump dans `docker run mysql:8.4` jetable, lancer `GET /api/v1/admin/licenses/audit/verify`, comparer l'empreinte de tête d'audit avec le journal de sauvegarde).
6. Tests locaux : `bash -n` sur chaque script ; `shellcheck` si disponible ; exécuter `host-check.sh --dry-run` sur le Mac (les commandes docker absentes doivent être signalées, pas planter).

## Critères d'acceptation
```sh
for f in ops/monitoring/*.sh backend/backup.sh; do bash -n "$f" && echo "OK $f"; done
bash ops/monitoring/host-check.sh --dry-run        # imprime chaque contrôle et « (simulation) », code 0
bash ops/monitoring/offsite-backup.sh              # refuse sans --apply et explique
grep -n 'OFFSITE_HOOK' backend/backup.sh           # crochet présent, vide par défaut
grep -n 'Supervision' backend/README.md
```

## Cas limites
- Le VPS est partagé : les scripts ne touchent que les conteneurs `castbridge-*` et `~/castbridge/`.
- Aucune URL de healthchecks, aucun remote rclone, aucune phrase de chiffrement dans le dépôt.

## À ne pas faire
Pas de connexion au serveur, pas d'installation, pas de commit sur les branches partagées, pas de secret ; ne pas modifier `deploy.sh` ni nginx.

## Rapport
`STATUT`, liste des scripts, ce que le propriétaire doit faire à la main (comptes gratuits à créer, remote à configurer), sortie des commandes.
