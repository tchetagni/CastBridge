#!/usr/bin/env bash
# Déploiement TRAÇABLE du serveur CastBridge sans dépôt git DANS le répertoire vivant du VPS (disposition « copie de fichiers »).
# La traçabilité vient du dépôt bare du serveur (remote git « bridge » = <root>/castbridge.git) : on y pousse le tag ou la branche
# à déployer, puis le serveur en extrait la release (git archive). backend/deploy.sh reste valable dans un VRAI clone (nouvelle installation,
# /opt/castbridge) mais ne peut pas tourner dans le répertoire vivant actuel, qui n'est pas un clone.
#
#   bash tools/release/deploy-server.sh <ref>              # DRY-RUN : affiche le plan et les commandes exactes (aucune connexion)
#   bash tools/release/deploy-server.sh <ref> --apply      # pousse la référence vers « bridge » puis déploie (ref = tag server-<v> ou branche)
#   bash tools/release/deploy-server.sh <ref> --push-only --apply   # pousse seulement la référence vers « bridge »
#   bash tools/release/deploy-server.sh --status --apply   # révision en service + références du dépôt bare (lecture seule, se connecte)
#   bash tools/release/deploy-server.sh --rollback --apply # revient à la release précédente
#   bash tools/release/deploy-server.sh server-play-<v> --service play            # DRY-RUN du service de jeu en ligne castbridge-play
#   bash tools/release/deploy-server.sh server-play-<v> --service play --apply    # le déploie (hors partie ; voir docs/PLAY-OPS.md)
#   bash tools/release/deploy-server.sh --status --service play --apply | --rollback --service play --apply
#
# Options : --apply  exécute (sans lui : aucune connexion, aucune écriture, aucun effet)
#           --host U@H   cible (défaut : $CB_DEPLOY_HOST, sinon ubuntu@bridge.sti-cm.com)
#           --no-push    ne pousse rien : la révision doit déjà être dans le dépôt bare (accepte alors un commit nu)
#           --push-only  s'arrête après le push
#           --strict-clean   refuse aussi s'il existe des fichiers non suivis (par défaut : seulement les fichiers suivis modifiés)
#           --service api|play   service visé (défaut api : comportement inchangé). « play » = castbridge-play (tag server-play-<v>), projet
#                                compose « castbridge-play », image castbridge-play:candidate -> :current -> :previous, santé GET /play/health,
#                                liens <root>/current-play et previous-play ; castbridge-api, la base et nginx ne sont JAMAIS touchés.
#           -h, --help
# Variables : CB_DEPLOY_HOST, CB_DEPLOY_ROOT (/home/ubuntu/castbridge), CB_LIVE_DIR (<root>/services/castbridge/backend, disposition
#             d'origine), CB_BARE_REPO (<root>/castbridge.git), CB_BRIDGE_REMOTE (bridge), CB_DOCKER ("sudo docker"),
#             CB_HEALTH_TIMEOUT (240 s), CB_SSH_OPTS ; service play : CB_PLAY_LIVE_DIR (<root>/services/play, contient .env.play 0600),
#             CB_PLAY_RUNTIME_IMAGE (image d'exécution par digest, relevée sur l'hôte le 2026-10-03), CB_PLAY_FORCE=1 (déployer malgré des salles ouvertes). Aucun secret n'est lu ni écrit par ce script : .env, secrets/ et geoip/ sont copiés
#             SUR LE SERVEUR depuis la release en service.
#
# Ce que fait --apply, dans l'ordre (rien n'est modifié en service avant l'étape 6) :
#   1 local   : refuse si arbre suivi modifié, si la révision n'est atteignable d'aucune branche origin/* (faire « git fetch »), si la
#               référence est « main » ou un commit nu, ou si le remote « bridge » ne pointe pas sur l'hôte cible.
#   2 local   : « git push bridge » du tag/de la branche (jamais main, jamais --force ; refus si le tag distant existe avec un autre objet).
#   3 serveur : « git --git-dir=<root>/castbridge.git archive <sha> backend | tar -x » dans une NOUVELLE release <root>/releases/<tag>-<UTC>
#               (jamais le répertoire vivant) ; copie .env (0600), secrets/, docker-compose.override.yml depuis la release en service
#               (symlink pour geoip/, possédé par root) ; écrit REVISION et RELEASE.
#   4 serveur : construit castbridge-api:candidate avec org.opencontainers.image.revision=<sha> et .version=<tag> (+ --build-arg VCS_REF).
#   5 serveur : sauvegarde la base avec backup.sh --db-only ; refuse de continuer si elle échoue.
#   6 serveur : castbridge-api:current -> :previous, :candidate -> :current, « sudo docker compose --project-name castbridge up -d --no-build
#               --no-deps castbridge-api » (la base et les autres conteneurs de l'hôte ne sont pas touchés, nginx n'est pas redémarré).
#   7 serveur : attend le healthcheck ; si OK, bascule le lien <root>/current (et <root>/previous) ; sinon ROLLBACK AUTOMATIQUE (image
#               précédente + fichiers de la release précédente) et code de sortie 1.
# Service play (--service play) : mêmes garde-fous locaux ; étapes distantes : release extraite (backend/docker-compose.play.yml, server-play/, android/core,
# content/learn, content/langues), .env.play copié (0600) depuis <root>/services/play, image construite depuis la RACINE de la release, refus si des salles
# sont ouvertes, bascule, vérification que le port n'est publié que sur 127.0.0.1, santé, rollback automatique.
# Première exécution (disposition d'origine, pas encore de lien <root>/current) : la release en service est <root>/services/castbridge/backend,
# COPIÉE (jamais déplacée) ; sa révision n'étant pas connue (image étiquetée « unknown »), elle est notée « révision initiale inconnue ».
set -Eeuo pipefail

HOST="${CB_DEPLOY_HOST:-ubuntu@bridge.sti-cm.com}"
ROOT="${CB_DEPLOY_ROOT:-/home/ubuntu/castbridge}"
LIVE_DIR="${CB_LIVE_DIR:-$ROOT/services/castbridge/backend}"
BARE="${CB_BARE_REPO:-$ROOT/castbridge.git}"
BRIDGE="${CB_BRIDGE_REMOTE:-bridge}"
DOCKER_CMD="${CB_DOCKER:-sudo docker}"
HEALTH_TIMEOUT="${CB_HEALTH_TIMEOUT:-240}"
SSH_OPTS="${CB_SSH_OPTS:--o BatchMode=yes -o ConnectTimeout=15}"
PLAY_LIVE_DIR="${CB_PLAY_LIVE_DIR:-$ROOT/services/play}"
PLAY_RUNTIME_IMAGE="${CB_PLAY_RUNTIME_IMAGE:-eclipse-temurin@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636}"
PLAY_FORCE="${CB_PLAY_FORCE:-0}"
APPLY=0; MODE=deploy; REF=""; STRICT=0; NOPUSH=0; PUSHONLY=0; SERVICE=api

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }
while [ $# -gt 0 ]; do
    case "$1" in
        --apply) APPLY=1 ;;
        --status) MODE=status ;;
        --rollback) MODE=rollback ;;
        --strict-clean) STRICT=1 ;;
        --no-push) NOPUSH=1 ;;
        --push-only) PUSHONLY=1 ;;
        --service) shift; [ $# -gt 0 ] || { echo "--service attend api ou play" >&2; exit 2; }
            case "$1" in api|play) SERVICE="$1" ;; *) echo "--service : api ou play seulement (reçu : $1)" >&2; exit 2 ;; esac ;;
        --host) shift; [ $# -gt 0 ] || { echo "--host attend user@hôte" >&2; exit 2; }; HOST="$1" ;;
        -h|--help) usage; exit 0 ;;
        -*) echo "option inconnue : $1 (voir --help)" >&2; exit 2 ;;
        *) [ -z "$REF" ] || { echo "une seule révision attendue" >&2; exit 2; }; REF="$1" ;;
    esac
    shift
done
if [ "$MODE" = deploy ] && [ -z "$REF" ]; then echo "ERREUR : indiquer la révision (tag server-<v>, branche ou commit). Voir --help." >&2; exit 2; fi

REPO="${CB_REPO:-$(cd "$(dirname "$0")/../.." && pwd)}"
log() { printf '[deploy-server] %s\n' "$*"; }
die() { printf '[deploy-server] ERREUR : %s\n' "$*" >&2; exit 1; }
# shellcheck disable=SC2086
ssh_run() { ssh $SSH_OPTS "$HOST" "$@"; }

# Écrit « VAR=valeur » (échappé) pour chaque nom donné : en-tête du script distant.
prelude() { local v; for v in "$@"; do printf '%s=%q\n' "$v" "${!v}"; done; }

# --------------------------------------------------------------------------- scripts distants
remote_common() { cat <<'REMOTE'
set -Eeuo pipefail
log() { printf '[distant %s] %s\n' "$(date -u +%FT%TZ)" "$*"; }
die() { log "ERREUR : $*"; exit 1; }
read -r -a DOCKER <<< "$DOCKER_CMD"
case "$DOCKER_CMD" in sudo*) SUDO_CP="sudo -n";; *) SUDO_CP="";; esac   # même privilège que docker : sans sudo (tests, hôte déjà root), copie simple
LABEL_REV=org.opencontainers.image.revision
LABEL_VER=org.opencontainers.image.version
img_label() { "${DOCKER[@]}" image inspect -f "{{ index .Config.Labels \"$2\" }}" "$1" 2>/dev/null || echo "?"; }
cname_label() { "${DOCKER[@]}" inspect -f "{{ index .Config.Labels \"$1\" }}" castbridge-api 2>/dev/null || echo "?"; }
compose_for() { # $1 = répertoire de release : le projet reste « castbridge », seuls les fichiers changent
    COMPOSE=("${DOCKER[@]}" compose --project-name castbridge -f "$1/docker-compose.yml" --env-file "$1/.env")
    [ -f "$1/docker-compose.override.yml" ] && COMPOSE+=(-f "$1/docker-compose.override.yml")
    return 0
}
wait_healthy() {
    local waited=0 state
    while [ "$waited" -lt "$HEALTH_TIMEOUT" ]; do
        state="$("${DOCKER[@]}" inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' castbridge-api 2>/dev/null || echo absent)"
        case "$state" in
            healthy) return 0 ;;
            unhealthy|exited|dead|absent) [ "$waited" -gt 20 ] && return 1 ;;
        esac
        sleep 5; waited=$((waited + 5))
    done
    return 1
}
live_dir() { if [ -L "$ROOT/current" ]; then readlink -f "$ROOT/current"; else echo "$LIVE_DIR"; fi; }
REMOTE
}

remote_deploy() { cat <<'REMOTE'
mkdir -p "$ROOT/releases"
exec 9>"$ROOT/releases/.deploy.lock"; flock -n 9 || die "un autre déploiement est en cours"
case "$REL" in "$ROOT"/releases/?*) ;; *) die "répertoire de release hors de $ROOT/releases : $REL" ;; esac
[ ! -e "$REL" ] || die "la release existe déjà : $REL"
[ -d "$BARE" ] || die "dépôt bare introuvable : $BARE (le push vers « bridge » a-t-il eu lieu ?)"
git --git-dir="$BARE" cat-file -e "$SHA^{commit}" 2>/dev/null || die "la révision $SHA est absente du dépôt bare : pousser la référence vers « bridge » d'abord"
if [ -n "$GITREF" ]; then
    [ "$(git --git-dir="$BARE" rev-parse -q --verify "$GITREF^{commit}" 2>/dev/null || echo none)" = "$SHA" ] \
        || die "$GITREF du dépôt bare ne désigne pas $SHA : abandon"
fi
if [ -L "$ROOT/current" ]; then INITIAL=0; else INITIAL=1; fi
SRC="$(live_dir)"
[ -d "$SRC" ] || die "release en service introuvable : $SRC"
[ -f "$SRC/.env" ] && [ -d "$SRC/secrets" ] || die ".env ou secrets/ absents de $SRC : abandon, rien n'a été modifié en service"
"${DOCKER[@]}" inspect castbridge-api >/dev/null 2>&1 || log "avertissement : conteneur castbridge-api absent (premier démarrage ?)"

# ---- 3. release extraite du dépôt bare (jamais le répertoire vivant) puis fichiers d'exploitation (copiés, jamais déplacés)
mkdir -p "$ROOT/releases" && mkdir "$REL"
if ! git --git-dir="$BARE" archive --format=tar "$SHA" backend | tar -x -C "$REL" --strip-components=1 -f -; then
    rm -rf "$REL"; die "extraction impossible : rien n'a été modifié en service"
fi
install -m 600 "$SRC/.env" "$REL/.env"
# les secrets appartiennent à l'utilisateur du conteneur (uid 10001, droits 0400) : sudo cp -a garde propriétaire et droits ; ubuntu ne peut pas les lire
$SUDO_CP cp -a "$SRC/secrets" "$REL/secrets" || die "copie de secrets/ impossible (sudo sans mot de passe requis) : abandon, rien n'a été modifié en service"
[ -f "$SRC/docker-compose.override.yml" ] && cp -a "$SRC/docker-compose.override.yml" "$REL/docker-compose.override.yml"
[ -e "$SRC/geoip" ] && ln -sfn "$(readlink -f "$SRC/geoip")" "$REL/geoip"
if [ "$INITIAL" = 1 ]; then
    PREV_REV="inconnue (révision initiale inconnue : image $(img_label castbridge-api:current $LABEL_REV), copie du $(date -u +%F))"
else
    PREV_REV="$(cat "$SRC/REVISION" 2>/dev/null || echo inconnue)"
fi
printf '%s\n' "$SHA" > "$REL/REVISION"
{
    echo "ref=$REF"; echo "version=$VERSION"; echo "sha=$SHA"; echo "deployed_utc=$(date -u +%FT%TZ)"
    echo "previous_release=$SRC"; echo "previous_revision=$PREV_REV"
} > "$REL/RELEASE"
compose_for "$REL"
"${COMPOSE[@]}" config -q || die "docker-compose.yml ou .env invalide dans $REL"
NEW_COMPOSE=("${COMPOSE[@]}")
compose_for "$SRC"
OLD_COMPOSE=("${COMPOSE[@]}")

# ---- 4. image candidate (le service en cours n'est pas touché)
log "construction castbridge-api:candidate ($SHA, $VERSION)"
"${DOCKER[@]}" build -t castbridge-api:candidate --build-arg "VCS_REF=$SHA" \
    --label "$LABEL_REV=$SHA" --label "$LABEL_VER=$VERSION" --label "org.opencontainers.image.created=$(date -u +%FT%TZ)" "$REL" \
    || die "échec de la construction : rien n'a été modifié en service"
[ "$(img_label castbridge-api:candidate $LABEL_REV)" = "$SHA" ] || die "l'étiquette de révision de l'image ne correspond pas"

# ---- 5. sauvegarde de la base
if [ "$("${DOCKER[@]}" inspect -f '{{.State.Running}}' castbridge-db 2>/dev/null || echo false)" = "true" ]; then
    log "sauvegarde de la base avant migration"
    ( cd "$REL" && DOCKER_CMD="$DOCKER_CMD" ./backup.sh --db-only ) || die "sauvegarde impossible : déploiement annulé, rien n'a été modifié en service"
else
    log "avertissement : castbridge-db arrêtée, pas de sauvegarde"
fi

# ---- 6. bascule de l'image
rollback() {
    log "ÉCHEC : retour automatique à la version précédente"
    "${DOCKER[@]}" logs --tail 80 castbridge-api 2>&1 || true
    if "${DOCKER[@]}" image inspect castbridge-api:previous >/dev/null 2>&1; then
        "${DOCKER[@]}" tag castbridge-api:previous castbridge-api:current
        "${OLD_COMPOSE[@]}" up -d --no-build --no-deps castbridge-api || true
        if wait_healthy; then log "version précédente rétablie ($PREV_REV)"; else log "la version précédente ne répond pas non plus : INTERVENTION MANUELLE"; fi
    else
        log "pas d'image précédente : service laissé arrêté"
        "${OLD_COMPOSE[@]}" stop castbridge-api || true
    fi
    log "le lien $ROOT/current n'a pas été modifié ; release en échec conservée : $REL"
    exit 1
}
if "${DOCKER[@]}" image inspect castbridge-api:current >/dev/null 2>&1; then
    "${DOCKER[@]}" tag castbridge-api:current castbridge-api:previous
    log "image en service conservée sous castbridge-api:previous"
fi
"${DOCKER[@]}" tag castbridge-api:candidate castbridge-api:current
log "démarrage de castbridge-api depuis $REL (base et autres conteneurs non touchés)"
"${NEW_COMPOSE[@]}" up -d --no-build --no-deps castbridge-api || rollback

# ---- 7. santé puis bascule des liens
if wait_healthy; then
    log "OK : castbridge-api en bonne santé"
    ln -sfn "$SRC" "$ROOT/previous"
    ln -sfn "$REL" "$ROOT/current.new" && mv -T "$ROOT/current.new" "$ROOT/current"
    "${DOCKER[@]}" rmi castbridge-api:candidate >/dev/null 2>&1 || true
    if [ "$INITIAL" = 1 ]; then
        printf 'Révision initiale inconnue : %s copié le %s ; image %s\n' "$SRC" "$(date -u +%FT%TZ)" "$("${DOCKER[@]}" image inspect -f '{{.Id}}' castbridge-api:previous 2>/dev/null || echo ?)" \
            > "$ROOT/releases/INITIAL-REVISION-INCONNUE.txt"
    fi
    printf '%s deploy %s %s %s <- %s\n' "$(date -u +%FT%TZ)" "$VERSION" "$SHA" "$REL" "$SRC" >> "$ROOT/releases/HISTORY.log"
    log "en service : $REL ($SHA, $VERSION) ; précédente : $SRC"
else
    rollback
fi
REMOTE
}

remote_rollback() { cat <<'REMOTE'
mkdir -p "$ROOT/releases"
exec 9>"$ROOT/releases/.deploy.lock"; flock -n 9 || die "un autre déploiement est en cours"
[ -L "$ROOT/current" ] && [ -L "$ROOT/previous" ] || die "pas de release précédente enregistrée (aucun déploiement tracé n'a encore eu lieu)"
CUR="$(readlink -f "$ROOT/current")"; PRV="$(readlink -f "$ROOT/previous")"
[ -d "$PRV" ] || die "release précédente introuvable : $PRV"
"${DOCKER[@]}" image inspect castbridge-api:previous >/dev/null 2>&1 || die "image castbridge-api:previous absente"
log "attention : les migrations de base ne reviennent pas en arrière (restauration de sauvegarde : backend/README.md, « Sauvegardes »)"
CUR_ID="$("${DOCKER[@]}" image inspect -f '{{.Id}}' castbridge-api:current)"
compose_for "$PRV"; PRV_COMPOSE=("${COMPOSE[@]}")
compose_for "$CUR"; CUR_COMPOSE=("${COMPOSE[@]}")
"${DOCKER[@]}" tag castbridge-api:previous castbridge-api:current
"${DOCKER[@]}" tag "$CUR_ID" castbridge-api:previous
"${PRV_COMPOSE[@]}" up -d --no-build --no-deps castbridge-api || true
if wait_healthy; then
    ln -sfn "$CUR" "$ROOT/previous.new" && mv -T "$ROOT/previous.new" "$ROOT/previous"
    ln -sfn "$PRV" "$ROOT/current.new" && mv -T "$ROOT/current.new" "$ROOT/current"
    printf '%s rollback %s <- %s\n' "$(date -u +%FT%TZ)" "$PRV" "$CUR" >> "$ROOT/releases/HISTORY.log"
    log "OK : retour à $PRV ($(cat "$PRV/REVISION" 2>/dev/null || echo 'révision inconnue')) ; la release quittée reste disponible sous $ROOT/previous"
else
    log "ÉCHEC du retour : remise de la version quittée"
    "${DOCKER[@]}" tag "$CUR_ID" castbridge-api:current
    "${CUR_COMPOSE[@]}" up -d --no-build --no-deps castbridge-api || true
    wait_healthy && log "version quittée rétablie ($CUR)" || log "INTERVENTION MANUELLE"
    exit 1
fi
REMOTE
}

remote_status() { cat <<'REMOTE'
SRC="$(live_dir)"
echo "== Release en service (disposition : $([ -L "$ROOT/current" ] && echo 'releases tracées' || echo 'initiale, non git'))"
echo "répertoire     : $SRC"
echo "REVISION       : $(cat "$SRC/REVISION" 2>/dev/null || echo 'absent (révision initiale inconnue)')"
[ -f "$SRC/RELEASE" ] && sed 's/^/RELEASE        : /' "$SRC/RELEASE"
echo "précédente     : $([ -L "$ROOT/previous" ] && readlink -f "$ROOT/previous" || echo aucune)"
echo "== Conteneur castbridge-api"
echo "image          : $("${DOCKER[@]}" inspect -f '{{.Config.Image}} ({{.Image}})' castbridge-api 2>/dev/null || echo absent)"
echo "revision       : $(cname_label $LABEL_REV)"
echo "version        : $(cname_label $LABEL_VER)"
echo "santé          : $("${DOCKER[@]}" inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' castbridge-api 2>/dev/null || echo absent)"
echo "== Images"
echo "current        : revision $(img_label castbridge-api:current $LABEL_REV), version $(img_label castbridge-api:current $LABEL_VER)"
echo "previous       : revision $(img_label castbridge-api:previous $LABEL_REV), version $(img_label castbridge-api:previous $LABEL_VER)"
echo "== Dépôt bare $BARE (références, lecture seule)"
if [ -d "$BARE" ]; then
    git --git-dir="$BARE" for-each-ref --format='%(objectname:short) %(objecttype) %(refname)' | sed 's/^/  /'
    LIVE_SHA="$(cat "$SRC/REVISION" 2>/dev/null || true)"
    if [ -n "$LIVE_SHA" ]; then
        git --git-dir="$BARE" cat-file -e "$LIVE_SHA^{commit}" 2>/dev/null && echo "  la révision en service ($LIVE_SHA) est dans le dépôt bare" || echo "  la révision en service ($LIVE_SHA) est ABSENTE du dépôt bare"
        git --git-dir="$BARE" for-each-ref --points-at "$LIVE_SHA" --format='  référence sur la révision en service : %(refname:short)' 2>/dev/null || true
    fi
else
    echo "  absent"
fi
[ -f "$ROOT/releases/HISTORY.log" ] && { echo "== Historique (10 dernières lignes)"; tail -n 10 "$ROOT/releases/HISTORY.log"; }
[ -f "$ROOT/releases/INITIAL-REVISION-INCONNUE.txt" ] && cat "$ROOT/releases/INITIAL-REVISION-INCONNUE.txt"
exit 0
REMOTE
}

# --------------------------------------------------------------------------- service play (ajout : n'altère aucune fonction ci-dessus)
remote_play_common() { cat <<'REMOTE'
PLAY_CT=castbridge-play
play_compose() { # $1 = répertoire de release : projet compose DISTINCT de « castbridge » (l'API et la base ne sont pas dans ce projet)
    PCOMPOSE=("${DOCKER[@]}" compose --project-name castbridge-play -f "$1/backend/docker-compose.play.yml")
}
wait_healthy_play() {
    local waited=0 state
    while [ "$waited" -lt "$HEALTH_TIMEOUT" ]; do
        state="$("${DOCKER[@]}" inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$PLAY_CT" 2>/dev/null || echo absent)"
        case "$state" in
            healthy) return 0 ;;
            unhealthy|exited|dead|absent) [ "$waited" -gt 20 ] && return 1 ;;
        esac
        sleep 5; waited=$((waited + 5))
    done
    return 1
}
# le port ne doit être publié QUE sur 127.0.0.1 (exigence 1 de docs/PLAY-OPS-REQUIREMENTS.md)
port_loopback_only() {
    local ports; ports="$("${DOCKER[@]}" port "$PLAY_CT" 2>/dev/null || true)"
    [ -n "$ports" ] || return 1
    ! printf '%s\n' "$ports" | grep -v -- '-> 127\.0\.0\.1:' | grep -q .
}
play_rooms() { "${DOCKER[@]}" exec "$PLAY_CT" curl -fsS http://127.0.0.1:8080/play/health 2>/dev/null | sed -n 's/.*"rooms":\([0-9][0-9]*\).*/\1/p'; }
REMOTE
}

remote_deploy_play() { remote_play_common; cat <<'REMOTE'
mkdir -p "$ROOT/releases"
exec 9>"$ROOT/releases/.deploy.lock"; flock -n 9 || die "un autre déploiement est en cours"
case "$REL" in "$ROOT"/releases/?*) ;; *) die "répertoire de release hors de $ROOT/releases : $REL" ;; esac
[ ! -e "$REL" ] || die "la release existe déjà : $REL"
[ -d "$BARE" ] || die "dépôt bare introuvable : $BARE (le push vers « bridge » a-t-il eu lieu ?)"
git --git-dir="$BARE" cat-file -e "$SHA^{commit}" 2>/dev/null || die "la révision $SHA est absente du dépôt bare : pousser la référence vers « bridge » d'abord"
if [ -n "$GITREF" ]; then
    [ "$(git --git-dir="$BARE" rev-parse -q --verify "$GITREF^{commit}" 2>/dev/null || echo none)" = "$SHA" ] \
        || die "$GITREF du dépôt bare ne désigne pas $SHA : abandon"
fi
[ -f "$PLAY_LIVE_DIR/.env.play" ] || die "$PLAY_LIVE_DIR/.env.play absent (copier backend/.env.play.example, le remplir, chmod 600) : rien n'a été modifié en service"
if [ -L "$ROOT/current-play" ]; then OLD="$(readlink -f "$ROOT/current-play")"; else OLD=""; fi

# ---- 3. release extraite du dépôt bare (le contexte de construction du Dockerfile est la RACINE de la release)
mkdir -p "$ROOT/releases" && mkdir "$REL"
if ! git --git-dir="$BARE" archive --format=tar "$SHA" backend/docker-compose.play.yml backend/.env.play.example backend/sql \
        android/core android/gradle.properties server-play content/learn content/langues | tar -x -C "$REL" -f -; then
    rm -rf "$REL"; die "extraction impossible (révision sans server-play/ ni backend/docker-compose.play.yml ?) : rien n'a été modifié en service"
fi
install -m 600 "$PLAY_LIVE_DIR/.env.play" "$REL/backend/.env.play"
if grep -Eq '^[[:space:]]*CASTBRIDGE_PLAY_DIRECT=1' "$REL/backend/.env.play"; then
    log "avertissement : CASTBRIDGE_PLAY_DIRECT=1 dans .env.play (profil STAGING) : derrière nginx, tous les clients partageraient l'adresse de nginx ; ne pas exposer /play/ avec ce profil"
fi
printf '%s\n' "$SHA" > "$REL/REVISION"
{ echo "service=play"; echo "ref=$REF"; echo "version=$VERSION"; echo "sha=$SHA"; echo "deployed_utc=$(date -u +%FT%TZ)"; echo "previous_release=${OLD:-aucune}"; echo "runtime_image=$PLAY_RUNTIME_IMAGE"; } > "$REL/RELEASE"
play_compose "$REL"; NEW_COMPOSE=("${PCOMPOSE[@]}")
"${NEW_COMPOSE[@]}" config -q || die "backend/docker-compose.play.yml ou .env.play invalide dans $REL"
OLD_COMPOSE=()
if [ -n "$OLD" ] && [ -f "$OLD/backend/docker-compose.play.yml" ]; then play_compose "$OLD"; OLD_COMPOSE=("${PCOMPOSE[@]}"); fi

# ---- 4. image candidate (le service en cours n'est pas touché)
log "mémoire disponible avant la construction (Gradle est gourmand) : $(free -m 2>/dev/null | awk '/^Mem:/ {print $7 " Mo"}')"
log "construction castbridge-play:candidate ($SHA, $VERSION) sur $PLAY_RUNTIME_IMAGE"
"${DOCKER[@]}" build -f "$REL/server-play/Dockerfile" -t castbridge-play:candidate \
    --build-arg "RUNTIME_IMAGE=$PLAY_RUNTIME_IMAGE" --build-arg "VCS_REF=$SHA" \
    --label "$LABEL_REV=$SHA" --label "$LABEL_VER=$VERSION" --label "org.opencontainers.image.created=$(date -u +%FT%TZ)" "$REL" \
    || die "échec de la construction : rien n'a été modifié en service"
[ "$(img_label castbridge-play:candidate $LABEL_REV)" = "$SHA" ] || die "l'étiquette de révision de l'image ne correspond pas"

# ---- 5. jamais pendant une partie : le redémarrage perd les salles (le service annonce la maintenance puis attend 25 s)
if [ "$("${DOCKER[@]}" inspect -f '{{.State.Running}}' "$PLAY_CT" 2>/dev/null || echo false)" = "true" ]; then
    ROOMS="$(play_rooms || true)"
    log "salles ouvertes : ${ROOMS:-inconnu}"
    if [ "${ROOMS:-inconnu}" != "0" ] && [ "$PLAY_FORCE" != "1" ]; then
        "${DOCKER[@]}" rmi castbridge-play:candidate >/dev/null 2>&1 || true
        die "des salles sont ouvertes (${ROOMS:-inconnu}) : déployer hors partie (heure creuse), ou CB_PLAY_FORCE=1 pour passer outre. Rien n'a été modifié en service"
    fi
fi

# ---- 6. bascule de l'image
rollback() {
    log "ÉCHEC : retour automatique à la version précédente"
    "${DOCKER[@]}" logs --tail 80 "$PLAY_CT" 2>&1 || true
    if "${DOCKER[@]}" image inspect castbridge-play:previous >/dev/null 2>&1; then
        # fichiers de la release précédente si elle est tracée ; sinon (première bascule depuis un essai manuel) ceux de la release en échec
        [ "${#OLD_COMPOSE[@]}" -gt 0 ] || OLD_COMPOSE=("${NEW_COMPOSE[@]}")
        "${DOCKER[@]}" tag castbridge-play:previous castbridge-play:current
        "${OLD_COMPOSE[@]}" up -d --no-build --no-deps castbridge-play || true
        if wait_healthy_play && port_loopback_only; then log "version précédente rétablie ($OLD)"; else log "la version précédente ne répond pas non plus : INTERVENTION MANUELLE"; fi
    else
        log "pas de version précédente : service castbridge-play arrêté (le reste de l'hôte n'est pas touché)"
        "${NEW_COMPOSE[@]}" stop castbridge-play || true
    fi
    log "le lien $ROOT/current-play n'a pas été modifié ; release en échec conservée : $REL"
    exit 1
}
if "${DOCKER[@]}" image inspect castbridge-play:current >/dev/null 2>&1; then
    "${DOCKER[@]}" tag castbridge-play:current castbridge-play:previous
    log "image en service conservée sous castbridge-play:previous"
fi
"${DOCKER[@]}" tag castbridge-play:candidate castbridge-play:current
log "démarrage de castbridge-play depuis $REL (castbridge-api, castbridge-db, nginx non touchés)"
"${NEW_COMPOSE[@]}" up -d --no-build --no-deps castbridge-play || rollback

# ---- 7. santé, port local seulement, puis bascule des liens
if wait_healthy_play && port_loopback_only; then
    log "OK : castbridge-play en bonne santé, port publié sur 127.0.0.1 seulement"
    if [ -n "$OLD" ]; then ln -sfn "$OLD" "$ROOT/previous-play.new" && mv -T "$ROOT/previous-play.new" "$ROOT/previous-play"; fi
    ln -sfn "$REL" "$ROOT/current-play.new" && mv -T "$ROOT/current-play.new" "$ROOT/current-play"
    "${DOCKER[@]}" rmi castbridge-play:candidate >/dev/null 2>&1 || true
    printf '%s deploy-play %s %s %s <- %s\n' "$(date -u +%FT%TZ)" "$VERSION" "$SHA" "$REL" "${OLD:-aucune}" >> "$ROOT/releases/HISTORY.log"
    log "en service : $REL ($SHA, $VERSION) ; précédente : ${OLD:-aucune}"
else
    rollback
fi
REMOTE
}

remote_rollback_play() { remote_play_common; cat <<'REMOTE'
mkdir -p "$ROOT/releases"
exec 9>"$ROOT/releases/.deploy.lock"; flock -n 9 || die "un autre déploiement est en cours"
[ -L "$ROOT/current-play" ] && [ -L "$ROOT/previous-play" ] || die "pas de release play précédente enregistrée"
CUR="$(readlink -f "$ROOT/current-play")"; PRV="$(readlink -f "$ROOT/previous-play")"
[ -d "$PRV" ] || die "release précédente introuvable : $PRV"
"${DOCKER[@]}" image inspect castbridge-play:previous >/dev/null 2>&1 || die "image castbridge-play:previous absente"
ROOMS="$(play_rooms || true)"
if [ "${ROOMS:-0}" != "0" ] && [ "$PLAY_FORCE" != "1" ]; then die "des salles sont ouvertes (${ROOMS}) : revenir en arrière hors partie, ou CB_PLAY_FORCE=1"; fi
CUR_ID="$("${DOCKER[@]}" image inspect -f '{{.Id}}' castbridge-play:current)"
play_compose "$PRV"; PRV_COMPOSE=("${PCOMPOSE[@]}")
play_compose "$CUR"; CUR_COMPOSE=("${PCOMPOSE[@]}")
"${DOCKER[@]}" tag castbridge-play:previous castbridge-play:current
"${DOCKER[@]}" tag "$CUR_ID" castbridge-play:previous
"${PRV_COMPOSE[@]}" up -d --no-build --no-deps castbridge-play || true
if wait_healthy_play && port_loopback_only; then
    ln -sfn "$CUR" "$ROOT/previous-play.new" && mv -T "$ROOT/previous-play.new" "$ROOT/previous-play"
    ln -sfn "$PRV" "$ROOT/current-play.new" && mv -T "$ROOT/current-play.new" "$ROOT/current-play"
    printf '%s rollback-play %s <- %s\n' "$(date -u +%FT%TZ)" "$PRV" "$CUR" >> "$ROOT/releases/HISTORY.log"
    log "OK : retour à $PRV ($(cat "$PRV/REVISION" 2>/dev/null || echo 'révision inconnue'))"
else
    log "ÉCHEC du retour : remise de la version quittée"
    "${DOCKER[@]}" tag "$CUR_ID" castbridge-play:current
    "${CUR_COMPOSE[@]}" up -d --no-build --no-deps castbridge-play || true
    wait_healthy_play && log "version quittée rétablie ($CUR)" || log "INTERVENTION MANUELLE"
    exit 1
fi
REMOTE
}

remote_status_play() { remote_play_common; cat <<'REMOTE'
echo "== Service play (disposition : $([ -L "$ROOT/current-play" ] && echo 'releases tracées' || echo 'aucun déploiement tracé'))"
if [ -L "$ROOT/current-play" ]; then
    SRC="$(readlink -f "$ROOT/current-play")"
    echo "répertoire     : $SRC"
    echo "REVISION       : $(cat "$SRC/REVISION" 2>/dev/null || echo absent)"
    [ -f "$SRC/RELEASE" ] && sed 's/^/RELEASE        : /' "$SRC/RELEASE"
    echo "précédente     : $([ -L "$ROOT/previous-play" ] && readlink -f "$ROOT/previous-play" || echo aucune)"
fi
echo "== Conteneur $PLAY_CT"
echo "image          : $("${DOCKER[@]}" inspect -f '{{.Config.Image}} ({{.Image}})' "$PLAY_CT" 2>/dev/null || echo absent)"
echo "santé          : $("${DOCKER[@]}" inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$PLAY_CT" 2>/dev/null || echo absent)"
echo "ports publiés  : $("${DOCKER[@]}" port "$PLAY_CT" 2>/dev/null | tr '\n' ' ' || true)"
echo "salles ouvertes: $(play_rooms || echo inconnu)"
echo "== Images"
echo "current        : revision $(img_label castbridge-play:current $LABEL_REV), version $(img_label castbridge-play:current $LABEL_VER)"
echo "previous       : revision $(img_label castbridge-play:previous $LABEL_REV), version $(img_label castbridge-play:previous $LABEL_VER)"
[ -f "$ROOT/releases/HISTORY.log" ] && { echo "== Historique (lignes play, 10 dernières)"; grep -E 'deploy-play|rollback-play' "$ROOT/releases/HISTORY.log" | tail -n 10 || true; }
exit 0
REMOTE
}

run_remote() { # $1 = nom de la fonction du script ; variables d'en-tête : le reste
    local fn="$1"; shift
    local script
    script="$(prelude ROOT LIVE_DIR BARE DOCKER_CMD HEALTH_TIMEOUT "$@"; remote_common; "$fn")"
    if [ "$APPLY" = 1 ]; then
        printf '%s\n' "$script" | ssh_run bash -s
    else
        printf -- '--- script distant (exécuté par « ssh %s bash -s » avec --apply) ---\n%s\n--- fin ---\n' "$HOST" "$script"
    fi
}

# --------------------------------------------------------------------------- modes status / rollback
if [ "$MODE" != deploy ]; then
    echo "== Mode $MODE sur $HOST ($( [ "$APPLY" = 1 ] && echo APPLY || echo DRY-RUN ))"
    [ "$APPLY" = 1 ] || echo "DRY-RUN : aucune connexion. Ajouter --apply pour exécuter."
    if [ "$SERVICE" = play ]; then
        if [ "$MODE" = status ]; then run_remote remote_status_play PLAY_FORCE; else run_remote remote_rollback_play PLAY_FORCE; fi
    elif [ "$MODE" = status ]; then run_remote remote_status; else run_remote remote_rollback; fi
    exit 0
fi

# --------------------------------------------------------------------------- mode deploy : contrôles locaux
cd "$REPO"
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERREUR : $REPO n'est pas un dépôt git" >&2; exit 2; }
ZERO="0000000000000000000000000000000000000000"
PROBLEMS=""
problem() { PROBLEMS="$PROBLEMS
  - $1"; }
KIND=commit; GITREF=""
if git rev-parse -q --verify "refs/tags/$REF" >/dev/null 2>&1; then KIND=tag; GITREF="refs/tags/$REF"
elif git rev-parse -q --verify "refs/heads/$REF" >/dev/null 2>&1; then KIND=branch; GITREF="refs/heads/$REF"; fi
if SHA="$(git rev-parse --verify -q "$REF^{commit}" 2>/dev/null)"; then :; else
    SHA="$ZERO"; problem "révision introuvable dans le dépôt local : $REF"
fi
SHA7="$(printf '%.7s' "$SHA")"
if [ "$KIND" = commit ] && [ "$SHA" != "$ZERO" ] && [ "$NOPUSH" = 0 ]; then
    problem "« $REF » n'est ni un tag ni une branche : créer un tag server-<version> (ou utiliser --no-push si le commit est déjà dans le dépôt bare)"
fi
if [ "$KIND" = branch ] && { [ "$REF" = main ] || [ "$REF" = master ]; }; then
    problem "refus de déployer/pousser « $REF » vers le serveur de production (utiliser un tag server-<version>)"
fi
tracked="$(git status --porcelain --untracked-files=no | wc -l | tr -d ' ')"
untracked="$(git status --porcelain | grep -c '^??' || true)"
[ "$tracked" = 0 ] || problem "arbre de travail modifié : $tracked fichier(s) suivi(s) modifié(s) ou indexé(s) (committer ou annuler d'abord)"
[ "$STRICT" = 0 ] || [ "$untracked" = 0 ] || problem "--strict-clean : $untracked fichier(s) non suivi(s)"
if [ "$SHA" != "$ZERO" ]; then
    if [ -z "$(git branch -r --contains "$SHA" 2>/dev/null | grep -v 'HEAD ->' | head -n 1)" ]; then
        problem "la révision $SHA7 n'est atteignable depuis aucune branche origin/* (pousser la branche vers GitHub, puis « git fetch »)"
    fi
fi
if [ "$NOPUSH" = 0 ]; then
    if BRIDGE_URL="$(git remote get-url "$BRIDGE" 2>/dev/null)"; then
        if [ "${CB_BRIDGE_HOST_CHECK:-1}" = 1 ]; then
            case "$BRIDGE_URL" in *"${HOST#*@}"*) ;; *) problem "le remote « $BRIDGE » ($BRIDGE_URL) ne pointe pas sur ${HOST#*@}" ;; esac
        fi
    else
        BRIDGE_URL="?"; problem "remote git « $BRIDGE » absent (git remote add $BRIDGE ${HOST}:castbridge/castbridge.git)"
    fi
fi
VERSION=""
TAGGLOB='server-*'; TAGPFX='server-'
if [ "$SERVICE" = play ]; then TAGGLOB='server-play-*'; TAGPFX='server-play-'; fi
case "$REF" in $TAGGLOB) VERSION="${REF#$TAGPFX}" ;; esac
if [ -z "$VERSION" ] && [ "$SHA" != "$ZERO" ]; then
    t="$(git tag --points-at "$SHA" "$TAGGLOB" 2>/dev/null | sed -n 1p)"
    if [ -n "$t" ]; then VERSION="${t#$TAGPFX}"; fi
fi
case "$REF" in server-play-*) [ "$SERVICE" = play ] || problem "« $REF » est une étiquette du service de jeu : utiliser --service play (castbridge-api n'est pas concerné)" ;; esac
[ -n "$VERSION" ] || VERSION="untagged-$SHA7"
case "$VERSION" in untagged-*) log "avertissement : révision sans tag $TAGGLOB (version étiquetée « $VERSION ») ; préférer un tag ${TAGPFX}<version>" ;; esac
UTC="$(date -u +%Y%m%dT%H%M%SZ)"
case "$KIND" in
    tag) PART="$REF" ;;
    branch) PART="$(printf '%s' "$REF" | tr -c 'A-Za-z0-9._\n-' '-')-$SHA7" ;;
    *) PART="$SHA7" ;;
esac
NAME="$PART-$UTC"
REL="$ROOT/releases/$NAME"

echo "== Déploiement serveur CastBridge$( [ "$SERVICE" = play ] && echo ' : service de jeu castbridge-play') ($( [ "$APPLY" = 1 ] && echo APPLY || echo DRY-RUN ))"
echo "cible            : $HOST"
echo "référence        : $REF ($KIND) -> $SHA ($VERSION)"
echo "dépôt bare       : $BARE (remote local « $BRIDGE » : ${BRIDGE_URL:-non utilisé})"
echo "nouvelle release : $REL"
if [ "$SERVICE" = play ]; then
    echo "release en service (liens $ROOT/current-play, previous-play) ; .env.play (0600) copié depuis $PLAY_LIVE_DIR ; image d'exécution : $PLAY_RUNTIME_IMAGE"
    echo "projet compose   : castbridge-play (conteneur castbridge-play seulement ; castbridge-api, castbridge-db, sti-*, infra-nginx, infra-certbot non touchés)"
    echo "image            : castbridge-play:candidate -> :current (ancienne : :previous) ; santé GET /play/health ; port publié 127.0.0.1:7091 seulement"
else
echo "release en service (source de .env, secrets/, geoip/, override) : lien $ROOT/current s'il existe, sinon $LIVE_DIR (copie, jamais déplacée)"
echo "projet compose   : castbridge (conteneurs castbridge-api / castbridge-db uniquement ; sti-*, infra-nginx, infra-certbot non touchés)"
fi
if [ -n "$PROBLEMS" ]; then
    echo "BLOQUANT en --apply :$PROBLEMS"
    if [ "$APPLY" = 1 ]; then exit 3; fi
    echo "(dry-run : le plan est tout de même affiché ci-dessous)"
fi
echo
if [ "$NOPUSH" = 1 ]; then
    echo "== Étape 2 (locale) : ignorée (--no-push) ; la révision doit déjà être dans $BARE"
else
    echo "== Étape 2 (locale) : publier la référence dans le dépôt bare (jamais main, jamais --force, jamais déplacer une référence existante)"
    if [ -n "$GITREF" ]; then
        echo "git ls-remote $BRIDGE $GITREF        # attendu : vide, ou le même objet (sinon refus)"
        echo "git push $BRIDGE $GITREF:$GITREF"
    else
        echo "(aucune référence à pousser : voir BLOQUANT)"
    fi
fi
echo
if [ "$APPLY" = 1 ] && [ "$NOPUSH" = 0 ]; then
    local_obj="$(git rev-parse "$GITREF")"
    remote_obj="$(git ls-remote "$BRIDGE" "$GITREF" | awk '{print $1}')" || die "git ls-remote $BRIDGE impossible"
    if [ -z "$remote_obj" ]; then
        log "push de $GITREF vers $BRIDGE"
        git push "$BRIDGE" "$GITREF:$GITREF" || die "push refusé : rien n'a été modifié en service"
    elif [ "$remote_obj" = "$local_obj" ]; then
        log "$GITREF déjà présent sur $BRIDGE avec le même objet : rien à pousser"
    else
        die "$GITREF existe sur $BRIDGE avec un autre objet ($remote_obj) : refus de le déplacer (créer un nouveau tag server-<version>)"
    fi
fi
if [ "$PUSHONLY" = 1 ]; then
    [ "$APPLY" = 1 ] || echo "DRY-RUN : rien n'a été poussé. Relancer avec --apply."
    exit 0
fi
if [ "$SERVICE" = play ]; then run_remote remote_deploy_play REL SHA REF VERSION GITREF PLAY_LIVE_DIR PLAY_RUNTIME_IMAGE PLAY_FORCE
else run_remote remote_deploy REL SHA REF VERSION GITREF; fi
[ "$APPLY" = 1 ] || { echo; echo "DRY-RUN : rien n'a été envoyé ni exécuté. Relancer avec --apply."; }
