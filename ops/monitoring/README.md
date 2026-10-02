# Supervision minimale et sauvegardes hors site

Scripts POSIX pour une surveillance gratuite et une copie de secours chiffrée des sauvegardes de la base de données.

## Vue d'ensemble

- **`host-check.sh`** : sonde interne vérifiant chaque 10 min le disque, la santé des conteneurs, la RAM, l'expiration TLS et l'âge des sauvegardes. Signale à healthchecks.io en cas d'échec.
- **`offsite-backup.sh`** : chiffre le dernier dump et le copie vers un stockage distant (B2, Scaleway, Google Drive…) via `rclone`. Conserve 30 jours.
- **`healthchecks.env.example`** : modèle pour les variables d'environnement (jamais commité).
- **`cron.example`** : exemple d'ajout au crontab.

Tous les scripts fonctionnent en mode **simulation par défaut** (affichent ce qu'ils feraient) ; le drapeau `--apply` active la vraie exécution.

## Installation

1. Créer un compte gratuit sur [healthchecks.io](https://healthchecks.io) ou [Better Stack](https://betterstack.com/uptime-robot).
2. Copier `healthchecks.env.example` vers `healthchecks.env` (hors dépôt) et y coller l'URL de la sonde.
3. Configurer `rclone` pour la copie hors site (B2, Scaleway, Drive…) avec un remote nommé `castbridge-offsite`.
4. Ajouter les trois lignes de `cron.example` au crontab.

## Vérifier localement

```sh
bash -n ops/monitoring/host-check.sh
bash -n ops/monitoring/offsite-backup.sh
bash ops/monitoring/host-check.sh --dry-run        # simule et affiche le résultat
```

## Exercice de restauration (trimestriel)

1. Télécharger le dernier dump depuis le stockage hors site.
2. Le déchiffrer : `gpg --decrypt backup.sql.gz.gpg > backup.sql.gz` (phrase lue depuis le fichier clé).
3. Lancer `docker run --rm -i -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=castbridge mysql:8.4 sh -c 'MYSQL_PWD="test" mysql -u root castbridge' < backup.sql.gz`
4. Vérifier la cohérence : `curl http://127.0.0.1:3306/api/v1/admin/licenses/audit/verify` (si le module des licences est actif).
5. Nettoyer : `docker rm -f test-restore`.

Planifier cet exercice chaque trimestre.
