#!/usr/bin/env bash
# Daily backup of the CastBridge server: compressed mysqldump (kept 14 days by default) + a mirror of the APK files.
#
#   ./backup.sh             # database + APK files
#   ./backup.sh --db-only   # database only (used by deploy.sh before a migration)
#
# cron (root or the deploy user, in the docker group):
#   15 3 * * * /opt/castbridge/backend/backup.sh >> /var/log/castbridge-backup.log 2>&1
#
# Restore: see backend/README.md, "Sauvegardes".
set -Eeuo pipefail
read -r -a DOCKER <<< "${DOCKER_CMD:-docker}"   # e.g. DOCKER_CMD="sudo docker"

cd "$(dirname "$(readlink -f "$0")")"
[ -f .env ] && { set -a; . ./.env; set +a; }
BACKUP_DIR="${CASTBRIDGE_BACKUP_DIR:-/var/backups/castbridge}"
RETENTION_DAYS="${CASTBRIDGE_BACKUP_RETENTION_DAYS:-14}"
log() { printf '[backup %s] %s\n' "$(date '+%F %T')" "$*"; }

umask 077
mkdir -p "$BACKUP_DIR/db" "$BACKUP_DIR/apk"

stamp="$(date +%Y%m%d-%H%M%S)"
out="$BACKUP_DIR/db/castbridge-$stamp.sql.gz"
tmp="$out.part"
# credentials stay inside the container (MYSQL_PWD, not on a command line)
"${DOCKER[@]}" exec castbridge-db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --quick --routines --triggers \
    --no-tablespaces --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"' | gzip -9 > "$tmp"
if ! gzip -dc "$tmp" | tail -n 1 | grep -q "Dump completed"; then
    rm -f "$tmp"
    log "ERREUR : dump incomplet"
    exit 1
fi
mv "$tmp" "$out"
log "base : $out ($(du -h "$out" | cut -f1))"

# retention: dumps older than RETENTION_DAYS days
find "$BACKUP_DIR/db" -name 'castbridge-*.sql.gz' -type f -mtime +"$((RETENTION_DAYS - 1))" -delete
find "$BACKUP_DIR/db" -name '*.part' -type f -mmin +120 -delete

if [ "${1:-}" != "--db-only" ]; then
    # APK files never change once published (the name carries version and hash): a mirror is enough
    "${DOCKER[@]}" cp castbridge-api:/data/apk/. "$BACKUP_DIR/apk/"
    rm -rf "$BACKUP_DIR/apk/.multipart" "$BACKUP_DIR/apk/.incoming"
    log "APK : $BACKUP_DIR/apk ($(du -sh "$BACKUP_DIR/apk" | cut -f1))"
fi
