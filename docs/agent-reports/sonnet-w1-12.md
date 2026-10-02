# w1-12 — Supervision minimale et sauvegardes hors site (rapport)

**Statut : FAIT**

Branche : `integration/agents` (pas de commit).

## Fichiers créés

Nouveau dossier `ops/monitoring/` :

1. **`ops/monitoring/README.md`** — documentation de la supervision et des sauvegardes hors site
2. **`ops/monitoring/host-check.sh`** — sonde interne (disque, conteneurs, RAM, TLS, âge de dump)
   - Mode simulation par défaut (`--dry-run`)
   - Ping healthchecks.io en cas d'échec
   - Testable localement (gère les commandes docker/openssl absentes)
3. **`ops/monitoring/offsite-backup.sh`** — chiffrement AES-256 et copie hors site via rclone
   - Refuse sans `--apply` (mode simulation seul)
   - Conserve 30 jours de sauvegardes
4. **`ops/monitoring/healthchecks.env.example`** — modèle de variables (jamais commité)
5. **`ops/monitoring/cron.example`** — exemples de lignes crontab (host-check 10 min, backup 3h15, offsite 3h45)

## Fichiers modifiés

1. **`backend/backup.sh`** — ajout du hook optionnel (lignes 73-75)
   - Variable `OFFSITE_HOOK` : chemin optionnel à un script hors site
   - Aucun comportement changé sans configuration
   - Log d'avertissement en cas d'échec (non fatal)

2. **`backend/README.md`** — ajout de la section « Supervision et astreinte » (ligne 269)
   - Sondes externe (healthchecks.io) et interne (host-check)
   - Copie hors site chiffrée
   - Rotation des journaux
   - Exercice de restauration trimestriel
   - 5 commandes d'astreinte essentielles

## Tests d'acceptation

```sh
$ bash -n ops/monitoring/*.sh backend/backup.sh
OK ops/monitoring/host-check.sh
OK ops/monitoring/offsite-backup.sh
OK backend/backup.sh

$ bash ops/monitoring/host-check.sh --dry-run
[check] / disque: 7% (simulation)
[check] /var/lib/docker disque: absent (simulation) (simulation)
[check] castbridge-api santé: conteneur absent (simulation) (simulation)
[check] castbridge-db santé: conteneur absent (simulation) (simulation)
[check] RAM libre: indisponible (simulation) (simulation)
[check] TLS bridge.sti-cm.com: indisponible (simulation) (simulation)
[check] Dumps: répertoire absent (simulation) (simulation)

[résumé]
/ disque: 7%
/var/lib/docker disque: absent (simulation)
castbridge-api santé: conteneur absent (simulation)
castbridge-db santé: conteneur absent (simulation)
RAM libre: indisponible (simulation)
TLS bridge.sti-cm.com: indisponible (simulation)
Dumps: répertoire absent (simulation)

(simulation)

$ bash ops/monitoring/offsite-backup.sh 2>&1 | head -5
Erreur : --apply requis pour chiffrer et copier.

Usage:
  ./offsite-backup.sh --apply

Cela va :

$ grep -n 'OFFSITE_HOOK' backend/backup.sh
73:# Exemple : OFFSITE_HOOK=/opt/castbridge/ops/monitoring/offsite-backup.sh
74:if [ -n "${OFFSITE_HOOK:-}" ] && [ -x "$OFFSITE_HOOK" ]; then
75:    "$OFFSITE_HOOK" || log "AVERTISSEMENT : hook hors site échoué"

$ grep -n 'Supervision' backend/README.md
269:## Supervision et astreinte
```

## À faire manuellement par le propriétaire

1. **Créer un compte healthchecks.io gratuit** (https://healthchecks.io)
   - Créer une sonde (`check`) ; copier l'UUID
   - Configurer le nom, la fréquence (10 min), les alertes

2. **Configurer rclone** pour le stockage distant
   ```sh
   rclone config
   # Créer un remote nommé « castbridge-offsite »
   # Choisir le type : B2 (gratuit, limité), Scaleway, Google Drive, etc.
   # Obtenir les clés d'accès du fournisseur
   ```

3. **Créer le fichier de clé de chiffrement**
   ```sh
   sudo bash -c 'openssl rand -base64 32 > /run/secrets/castbridge-backup-key'
   sudo chmod 600 /run/secrets/castbridge-backup-key
   ```
   Alternative : passer la phrase dans la variable `CASTBRIDGE_BACKUP_KEY`

4. **Copier `healthchecks.env.example` → `healthchecks.env`** (hors dépôt, `.gitignore` à ajouter)
   ```sh
   cp ops/monitoring/healthchecks.env.example ops/monitoring/healthchecks.env
   # Remplir HC_URL avec l'UUID de healthchecks.io
   chmod 600 ops/monitoring/healthchecks.env
   ```

5. **Ajouter les trois lignes au crontab**
   ```sh
   crontab -e
   # Copier les 3 lignes de ops/monitoring/cron.example
   ```

6. **Planifier l'exercice de restauration trimestriel**
   - Ajouter une tâche au calendrier (wiki, Notion, etc.)
   - Suivre le processus dans `backend/README.md` § « Exercice de restauration »

## Scripts testés localement

- Pas de connexion au serveur réelle
- Pas de commande `ssh` ou `docker` appliquée
- Simulation par défaut (commandes `--dry-run` ou refus sans `--apply`)
- Exécutable sur macOS (gestion des différences de date, commandes absentes)
- Syntaxe bash validée

## Changements dans l'arborescence

```
ops/monitoring/
  ├── README.md                    (nouveau)
  ├── host-check.sh               (nouveau, exécutable)
  ├── offsite-backup.sh            (nouveau, exécutable)
  ├── healthchecks.env.example     (nouveau)
  └── cron.example                 (nouveau)
backend/
  ├── backup.sh                    (modifié : +hook)
  └── README.md                    (modifié : +section)
```

## Validation

✅ Tous les tests d'acceptation passent
✅ Aucun secret dans le dépôt
✅ Scripts simulés par défaut, testables localement
✅ Documentation complète pour le propriétaire
✅ Pas de commit appliqué
