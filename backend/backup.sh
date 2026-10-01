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

# Licence module (docs/LICENSE-ADMIN.md). The full dump above already contains every lic_* table and the admin accounts (roles,
# encrypted TOTP secrets). This extra, small dump of ONLY those tables allows restoring the module alone; the head hash of the
# audit chain is logged as an outside anchor (note it somewhere else too). Optional and NEVER fatal: a problem here is a warning.
# NOT included, by design: the secret FILES (license-signing.key, license-totp.key, license-audit.key): save them yourself,
# encrypted, away from this server (see "Sauvegarde" in docs/LICENSE-ADMIN.md).
lic_dump() {
    local tables out2 head
    tables="$("${DOCKER[@]}" exec castbridge-db sh -c "MYSQL_PWD=\"\$MYSQL_PASSWORD\" mysql -N -u\"\$MYSQL_USER\" \"\$MYSQL_DATABASE\" -e \"SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'lic!_%' ESCAPE '!'\"" 2>/dev/null)" || return 0
    [ -n "$tables" ] || return 0
    tables="${tables//$'\n'/ }"
    out2="$BACKUP_DIR/db/castbridge-licenses-$stamp.sql.gz"
    "${DOCKER[@]}" exec castbridge-db sh -c "MYSQL_PWD=\"\$MYSQL_PASSWORD\" exec mysqldump --single-transaction --no-tablespaces --default-character-set=utf8mb4 -u\"\$MYSQL_USER\" \"\$MYSQL_DATABASE\" admin_user $tables" | gzip -9 > "$out2.part" || { rm -f "$out2.part"; return 1; }
    if gzip -dc "$out2.part" | tail -n 1 | grep -q "Dump completed"; then
        mv "$out2.part" "$out2"
        log "licences : $out2 ($(du -h "$out2" | cut -f1))"
    else
        rm -f "$out2.part"
        return 1
    fi
    head="$("${DOCKER[@]}" exec castbridge-db sh -c "MYSQL_PWD=\"\$MYSQL_PASSWORD\" mysql -N -u\"\$MYSQL_USER\" \"\$MYSQL_DATABASE\" -e \"SELECT last_id, last_hash FROM lic_audit_head WHERE id = 1\"" 2>/dev/null)" || return 0
    log "licences : tête du journal d'audit (à noter ailleurs) : $head"
}
lic_dump || log "AVERTISSEMENT : sauvegarde séparée du module licences incomplète (la sauvegarde complète ci-dessus est bonne)"

# retention: dumps older than RETENTION_DAYS days
find "$BACKUP_DIR/db" -name 'castbridge-*.sql.gz' -type f -mtime +"$((RETENTION_DAYS - 1))" -delete
find "$BACKUP_DIR/db" -name '*.part' -type f -mmin +120 -delete

if [ "${1:-}" != "--db-only" ]; then
    # APK files never change once published (the name carries version and hash): a mirror is enough
    "${DOCKER[@]}" cp castbridge-api:/data/apk/. "$BACKUP_DIR/apk/"
    rm -rf "$BACKUP_DIR/apk/.multipart" "$BACKUP_DIR/apk/.incoming"
    log "APK : $BACKUP_DIR/apk ($(du -sh "$BACKUP_DIR/apk" | cut -f1))"
fi
