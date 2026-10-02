#!/usr/bin/env bash
# Copie chiffrée des sauvegardes hors site (B2, Scaleway, Google Drive…).
# Mode simulation par défaut ; --apply requis pour vraie exécution.
#
# Usage:
#   ./offsite-backup.sh              # refuse et explique
#   ./offsite-backup.sh --apply      # chiffre et copie vraiment
#
# Prérequis :
#   - rclone configuré avec un remote nommé 'castbridge-offsite'
#   - fichier de clé GPG (phrase de chiffrement) à /run/secrets/castbridge-backup-key
#     ou dans la variable CASTBRIDGE_BACKUP_KEY
#   - backend/backup.sh a produit des dumps dans $BACKUP_DIR
#
# Variables d'environnement :
#   CASTBRIDGE_BACKUP_DIR    : répertoire des dumps (défaut /var/backups/castbridge)
#   CASTBRIDGE_BACKUP_KEY    : phrase de chiffrement (alternative au fichier)
#   HC_URL                   : URL de la sonde healthchecks.io
#

set -Eeuo pipefail

APPLY=false
BACKUP_DIR="${CASTBRIDGE_BACKUP_DIR:-/var/backups/castbridge}"
BACKUP_KEY_FILE="${CASTBRIDGE_BACKUP_KEY_FILE:-/run/secrets/castbridge-backup-key}"
BACKUP_KEY="${CASTBRIDGE_BACKUP_KEY:-}"
HC_URL="${HC_URL:-}"
RETENTION_DAYS=30

# Analyser les arguments
for arg in "$@"; do
    case "$arg" in
        --apply) APPLY=true ;;
    esac
done

# Vérifier --apply
if [ "$APPLY" != "true" ]; then
    cat >&2 << 'EOF'
Erreur : --apply requis pour chiffrer et copier.

Usage:
  ./offsite-backup.sh --apply

Cela va :
  1. Chiffrer le dernier dump avec GPG (phrase depuis un fichier ou variable)
  2. Copier le fichier chiffré vers 'castbridge-offsite' (rclone)
  3. Conserver 30 jours de sauvegardes
  4. Signaler à healthchecks.io

Avant d'utiliser --apply :
  - Configurer rclone avec : rclone config
    remote: castbridge-offsite
    type: (B2, Scaleway, Google Drive, etc.)
  - Créer le fichier /run/secrets/castbridge-backup-key avec la phrase de chiffrement
    (ou utiliser la variable CASTBRIDGE_BACKUP_KEY)
EOF
    exit 1
fi

# Vérifier les prérequis
if ! command -v gpg >/dev/null 2>&1; then
    echo "Erreur : gpg introuvable" >&2
    exit 1
fi

if ! command -v rclone >/dev/null 2>&1; then
    echo "Erreur : rclone introuvable" >&2
    exit 1
fi

if [ -z "$BACKUP_KEY" ] && [ ! -f "$BACKUP_KEY_FILE" ]; then
    echo "Erreur : fichier clé $BACKUP_KEY_FILE introuvable et CASTBRIDGE_BACKUP_KEY vide" >&2
    exit 1
fi

# Lire la clé depuis le fichier ou la variable
if [ -z "$BACKUP_KEY" ]; then
    BACKUP_KEY=$(cat "$BACKUP_KEY_FILE")
fi

# Trouver le dernier dump
latest_dump=$(find "$BACKUP_DIR/db" -name 'castbridge-*.sql.gz' -type f -printf '%T@ %p\n' 2>/dev/null | \
    sort -rn | head -1 | cut -d' ' -f2- || echo "")

if [ -z "$latest_dump" ] || [ ! -f "$latest_dump" ]; then
    echo "Erreur : aucun dump trouvé dans $BACKUP_DIR/db" >&2
    exit 1
fi

# Chiffrer
enc_name="${latest_dump##*/}.gpg"
enc_path="$BACKUP_DIR/db/$enc_name"

echo "Chiffrement de $(basename "$latest_dump")…"
echo "$BACKUP_KEY" | gpg --symmetric --cipher-algo AES256 --batch --passphrase-fd 0 \
    --output "$enc_path" "$latest_dump"

if [ ! -f "$enc_path" ]; then
    echo "Erreur : échec du chiffrement" >&2
    exit 1
fi

echo "Copie vers castbridge-offsite…"
rclone copy "$enc_path" "castbridge-offsite:backups/"

# Nettoyer les anciennes copies
echo "Nettoyage (conservation 30 jours)…"
cutoff=$(date -d "$RETENTION_DAYS days ago" '+%Y-%m-%d' 2>/dev/null || \
         date -v-${RETENTION_DAYS}d '+%Y-%m-%d' 2>/dev/null || echo "")

if [ -n "$cutoff" ]; then
    rclone delete "castbridge-offsite:backups/" --min-age "${RETENTION_DAYS}d" || true
fi

# Signaler à healthchecks
if [ -n "$HC_URL" ]; then
    curl -fsS "$HC_URL" 2>/dev/null || true
fi

echo "OK"
exit 0
