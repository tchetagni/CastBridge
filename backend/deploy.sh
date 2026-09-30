#!/usr/bin/env bash
# Deploys the CastBridge server from a git revision, idempotently, with automatic rollback.
#
#   ./deploy.sh                 # deploys $DEPLOY_REF from .env (default origin/main)
#   ./deploy.sh origin/feat/backend
#   ./deploy.sh v1.0.0          # a tag, or a commit hash
#
# Steps: lock, git fetch + checkout (detached) of the revision, keep the running image as :previous, build,
# database backup, up -d, wait for the healthcheck. If the new version does not become healthy: the previous image
# and revision are put back. Running it twice on the same revision just rebuilds (cached) and re-checks.
set -Eeuo pipefail

cd "$(dirname "$(readlink -f "$0")")"
BACKEND_DIR="$(pwd)"
log()  { printf '[deploy %s] %s\n' "$(date '+%F %T')" "$*"; }
die()  { log "ERREUR : $*"; exit 1; }

[ -f .env ] || die ".env manquant (cp .env.example .env, puis remplir)"
set -a; . ./.env; set +a
REF="${1:-${DEPLOY_REF:-origin/main}}"
KEY_PATH="${CASTBRIDGE_SIGNING_KEY_PATH:-./secrets/castbridge-signing.pem}"
[ -f "$KEY_PATH" ] || die "clé de signature introuvable : $KEY_PATH (voir README, « Clés Ed25519 »)"
TIMEOUT="${DEPLOY_HEALTH_TIMEOUT:-240}"
COMPOSE=(docker compose --project-name castbridge -f "$BACKEND_DIR/docker-compose.yml" --env-file "$BACKEND_DIR/.env")

exec 9>"${TMPDIR:-/tmp}/castbridge-deploy.lock"
flock -n 9 || die "un autre déploiement est en cours"

command -v docker >/dev/null || die "docker introuvable"
"${COMPOSE[@]}" config -q || die "docker-compose.yml ou .env invalide"

# ---- 1. revision -------------------------------------------------------------------------------
git diff --quiet && git diff --cached --quiet || die "modifications locales non commitées dans le dépôt : abandon"
PREV_REV="$(git rev-parse HEAD)"
git fetch --prune --tags origin
git checkout --quiet --detach "$REF"
NEW_REV="$(git rev-parse HEAD)"
export CASTBRIDGE_VCS_REF="$NEW_REV"
log "révision : ${PREV_REV:0:10} -> ${NEW_REV:0:10} ($REF)"

rollback() {
    log "ÉCHEC : retour à la version précédente"
    "${COMPOSE[@]}" logs --no-color --tail=80 castbridge-api || true
    git checkout --quiet --detach "$PREV_REV" || true
    if docker image inspect castbridge-api:previous >/dev/null 2>&1; then
        docker tag castbridge-api:previous castbridge-api:current
        "${COMPOSE[@]}" up -d --no-build castbridge-api || true
        if wait_healthy; then log "version précédente rétablie (${PREV_REV:0:10})"; else log "la version précédente ne répond pas non plus : intervention manuelle"; fi
    else
        log "pas d'image précédente (premier déploiement) : service arrêté"
        "${COMPOSE[@]}" stop castbridge-api || true
    fi
    exit 1
}

wait_healthy() {
    local waited=0 state
    while [ "$waited" -lt "$TIMEOUT" ]; do
        state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' castbridge-api 2>/dev/null || echo absent)"
        case "$state" in
            healthy) return 0 ;;
            unhealthy|exited|dead|absent) [ "$waited" -gt 20 ] && return 1 ;;
        esac
        sleep 5; waited=$((waited + 5))
    done
    return 1
}

# ---- 2. image ----------------------------------------------------------------------------------
if docker image inspect castbridge-api:current >/dev/null 2>&1; then
    docker tag castbridge-api:current castbridge-api:previous
    log "image en service conservée sous castbridge-api:previous"
fi
log "construction de l'image"
"${COMPOSE[@]}" build castbridge-api || { git checkout --quiet --detach "$PREV_REV"; die "échec de la construction (rien n'a été modifié en service)"; }

# ---- 3. backup before migrations -----------------------------------------------------------------
if [ "$(docker inspect -f '{{.State.Running}}' castbridge-db 2>/dev/null || echo false)" = "true" ]; then
    log "sauvegarde de la base avant migration"
    "$BACKEND_DIR/backup.sh" --db-only || die "sauvegarde impossible : déploiement annulé"
fi

# ---- 4. start and check --------------------------------------------------------------------------
log "démarrage"
"${COMPOSE[@]}" up -d --remove-orphans || rollback
if wait_healthy; then
    log "OK : castbridge-api en bonne santé (${NEW_REV:0:10})"
    docker image prune -f --filter "dangling=true" >/dev/null || true
else
    rollback
fi
