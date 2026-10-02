#!/usr/bin/env bash
# Sonde interne : disque, conteneurs, RAM, TLS, âge de sauvegarde.
# Mode simulation par défaut (--dry-run) ; pas de vraie exécution sans --apply.
#
# Usage:
#   ./host-check.sh --dry-run       # affiche les contrôles sans rien faire (défaut)
#   ./host-check.sh --apply         # exécute vraiment et signale à healthchecks.io
#
# Variables d'environnement :
#   HC_URL      : URL de la sonde healthchecks.io (ex: https://hc-ping.com/xxxxxxxx-xxxx-xxxx)
#

set -Eeuo pipefail

DRY_RUN=true
APPLY=false

# Analyser les arguments
for arg in "$@"; do
    case "$arg" in
        --dry-run)  DRY_RUN=true;  APPLY=false ;;
        --apply)    DRY_RUN=false; APPLY=true  ;;
    esac
done

# Variables
BACKUP_DIR="${CASTBRIDGE_BACKUP_DIR:-/var/backups/castbridge}"
HC_URL="${HC_URL:-}"
STAMP=$(date '+%Y-%m-%d %H:%M:%S')
ERRORS=""
CHECKS_LIST=()

# Fonctions utilitaires
log_check() {
    local name="$1" status="$2"
    CHECKS_LIST+=("$name: $status")
    [ "$DRY_RUN" = true ] && echo "[check] $name: $status (simulation)"
}

log_error() {
    local msg="$1"
    ERRORS="${ERRORS}${msg}"$'\n'
    [ "$DRY_RUN" = true ] && echo "[error] $msg (simulation)"
}

report_to_hc() {
    local exit_code="$1"
    [ -z "$HC_URL" ] && return
    if [ "$exit_code" -ne 0 ]; then
        [ "$DRY_RUN" = true ] && echo "[hc] would ping: $HC_URL/fail with errors (simulation)" || \
            curl -fsS "$HC_URL/fail" --data-raw "$ERRORS" 2>/dev/null || true
    else
        [ "$DRY_RUN" = true ] && echo "[hc] would ping: $HC_URL (simulation)" || \
            curl -fsS "$HC_URL" 2>/dev/null || true
    fi
}

# 1. Vérifier l'espace disque de /
if [ -d / ]; then
    df_root=$(df / 2>/dev/null | tail -1 | awk '{print $5}' | sed 's/%//' || echo "N/A")
    if [ "$df_root" != "N/A" ] && [ "$df_root" -gt 85 ] 2>/dev/null; then
        log_error "/ disque > 85% ($df_root%)"
    else
        log_check "/ disque" "$df_root%"
    fi
else
    log_check "/ disque" "absent (simulation)"
fi

# 2. Vérifier l'espace disque de /var/lib/docker
if [ -d /var/lib/docker ]; then
    df_docker=$(df /var/lib/docker 2>/dev/null | tail -1 | awk '{print $5}' | sed 's/%//' || echo "N/A")
    if [ "$df_docker" != "N/A" ] && [ "$df_docker" -gt 85 ] 2>/dev/null; then
        log_error "/var/lib/docker disque > 85% ($df_docker%)"
    else
        log_check "/var/lib/docker disque" "${df_docker}%"
    fi
else
    log_check "/var/lib/docker disque" "absent (simulation)"
fi

# 3. Vérifier la santé des conteneurs Docker
if command -v docker >/dev/null 2>&1; then
    for container in castbridge-api castbridge-db; do
        status=$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null | xargs || echo "absent")
        if [ "$status" = "unhealthy" ]; then
            log_error "$container est malsain (status: $status)"
        elif [ "$status" = "absent" ]; then
            log_check "$container santé" "conteneur absent (simulation)"
        else
            log_check "$container santé" "$status"
        fi

        # Vérifier le compteur de redémarrages
        restart_count=$(docker inspect -f '{{.RestartCount}}' "$container" 2>/dev/null | xargs || echo "N/A")
        if [ "$restart_count" != "N/A" ] && [ "$restart_count" -gt 0 ] 2>/dev/null; then
            log_check "$container redémarrages" "$restart_count"
        fi
    done
else
    log_check "docker" "absent (simulation)"
fi

# 4. Vérifier la RAM libre
if command -v free >/dev/null 2>&1; then
    free_kb=$(free 2>/dev/null | grep Mem | awk '{print $7}' || echo "0")
    free_mb=$((free_kb / 1024))
    if [ "$free_mb" -lt 100 ]; then
        log_error "RAM libre < 100 Mo ($free_mb Mo)"
    else
        log_check "RAM libre" "${free_mb} Mo"
    fi
elif [ -f /proc/meminfo ]; then
    free_kb=$(grep MemAvailable /proc/meminfo | awk '{print $2}' || echo "0")
    free_mb=$((free_kb / 1024))
    if [ "$free_mb" -lt 100 ]; then
        log_error "RAM libre < 100 Mo ($free_mb Mo)"
    else
        log_check "RAM libre" "${free_mb} Mo"
    fi
else
    log_check "RAM libre" "indisponible (simulation)"
fi

# 5. Vérifier l'expiration du certificat TLS
if command -v openssl >/dev/null 2>&1; then
    tls_domain="bridge.sti-cm.com"
    exp_date=$(echo "" 2>/dev/null | openssl s_client -servername "$tls_domain" -connect "$tls_domain:443" 2>/dev/null | \
        openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2 || echo "")

    if [ -n "$exp_date" ]; then
        # Essayer les formats de date pour Linux et macOS
        exp_epoch=$(date -d "$exp_date" +%s 2>/dev/null || date -jf "%b %d %T %Y %Z" "$exp_date" +%s 2>/dev/null || echo "0")

        if [ "$exp_epoch" -gt 0 ]; then
            now_epoch=$(date +%s)
            days_left=$(((exp_epoch - now_epoch) / 86400))

            if [ "$days_left" -lt 14 ]; then
                log_error "TLS $tls_domain expire dans $days_left jours"
            else
                log_check "TLS $tls_domain" "expire dans $days_left jours"
            fi
        else
            log_check "TLS $tls_domain" "indisponible (simulation)"
        fi
    else
        log_check "TLS $tls_domain" "indisponible (simulation)"
    fi
else
    log_check "TLS" "openssl indisponible (simulation)"
fi

# 6. Vérifier l'âge du dernier dump
if [ -d "$BACKUP_DIR/db" ]; then
    latest_dump=$(find "$BACKUP_DIR/db" -name 'castbridge-*.sql.gz' -type f -printf '%T@ %p\n' 2>/dev/null | \
        sort -rn | head -1 | cut -d' ' -f2- || echo "")

    if [ -z "$latest_dump" ] || [ ! -f "$latest_dump" ]; then
        log_error "Pas de dump trouvé dans $BACKUP_DIR/db"
    else
        now=$(date +%s)
        age_seconds=$((now - $(date -r "$latest_dump" +%s 2>/dev/null || echo "$now")))
        age_hours=$((age_seconds / 3600))

        if [ "$age_hours" -gt 26 ]; then
            log_error "Dernier dump âgé de $age_hours h (> 26 h)"
        else
            log_check "Dernier dump" "${age_hours} h"
        fi
    fi
else
    log_check "Dumps" "répertoire absent (simulation)"
fi

# Déterminer le code de sortie
EXIT_CODE=0
if [ -n "$ERRORS" ]; then
    EXIT_CODE=1
fi

# Afficher le résumé
if [ "$DRY_RUN" = true ]; then
    echo ""
    echo "[résumé]"
    for check in "${CHECKS_LIST[@]}"; do
        echo "$check"
    done
    if [ -n "$ERRORS" ]; then
        echo ""
        echo "[erreurs]"
        echo "$ERRORS"
    fi
    echo ""
    echo "(simulation)"
fi

# Signaler à healthchecks
if [ "$APPLY" = true ]; then
    report_to_hc "$EXIT_CODE"
fi

exit "$EXIT_CODE"
