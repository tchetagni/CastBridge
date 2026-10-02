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
#
# Options : --apply  exécute (sans lui : aucune connexion, aucune écriture, aucun effet)
#           --host U@H   cible (défaut : $CB_DEPLOY_HOST, sinon ubuntu@bridge.sti-cm.com)
#           --no-push    ne pousse rien : la révision doit déjà être dans le dépôt bare (accepte alors un commit nu)
#           --push-only  s'arrête après le push
#           --strict-clean   refuse aussi s'il existe des fichiers non suivis (par défaut : seulement les fichiers suivis modifiés)
#           -h, --help
# Variables : CB_DEPLOY_HOST, CB_DEPLOY_ROOT (/home/ubuntu/castbridge), CB_LIVE_DIR (<root>/services/castbridge/backend, disposition
#             d'origine), CB_BARE_REPO (<root>/castbridge.git), CB_BRIDGE_REMOTE (bridge), CB_DOCKER ("sudo docker"),
#             CB_HEALTH_TIMEOUT (240 s), CB_SSH_OPTS. Aucun secret n'est lu ni écrit par ce script : .env, secrets/ et geoip/ sont copiés
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
APPLY=0; MODE=deploy; REF=""; STRICT=0; NOPUSH=0; PUSHONLY=0

usage() { awk 'NR>1 && /^#/ {sub(/^# ?/, ""); print; next} NR>1 {exit}' "$0"; }
while [ $# -gt 0 ]; do
    case "$1" in
        --apply) APPLY=1 ;;
        --status) MODE=status ;;
        --rollback) MODE=rollback ;;
        --strict-clean) STRICT=1 ;;
        --no-push) NOPUSH=1 ;;
        --push-only) PUSHONLY=1 ;;
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
    if [ "$MODE" = status ]; then run_remote remote_status; else run_remote remote_rollback; fi
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
case "$REF" in server-*) VERSION="${REF#server-}" ;; esac
if [ -z "$VERSION" ] && [ "$SHA" != "$ZERO" ]; then
    t="$(git tag --points-at "$SHA" 'server-*' 2>/dev/null | sed -n 1p)"
    if [ -n "$t" ]; then VERSION="${t#server-}"; fi
fi
[ -n "$VERSION" ] || VERSION="untagged-$SHA7"
case "$VERSION" in untagged-*) log "avertissement : révision sans tag server-* (version étiquetée « $VERSION ») ; préférer un tag server-<version>" ;; esac
UTC="$(date -u +%Y%m%dT%H%M%SZ)"
case "$KIND" in
    tag) PART="$REF" ;;
    branch) PART="$(printf '%s' "$REF" | tr -c 'A-Za-z0-9._\n-' '-')-$SHA7" ;;
    *) PART="$SHA7" ;;
esac
NAME="$PART-$UTC"
REL="$ROOT/releases/$NAME"

echo "== Déploiement serveur CastBridge ($( [ "$APPLY" = 1 ] && echo APPLY || echo DRY-RUN ))"
echo "cible            : $HOST"
echo "référence        : $REF ($KIND) -> $SHA ($VERSION)"
echo "dépôt bare       : $BARE (remote local « $BRIDGE » : ${BRIDGE_URL:-non utilisé})"
echo "nouvelle release : $REL"
echo "release en service (source de .env, secrets/, geoip/, override) : lien $ROOT/current s'il existe, sinon $LIVE_DIR (copie, jamais déplacée)"
echo "projet compose   : castbridge (conteneurs castbridge-api / castbridge-db uniquement ; sti-*, infra-nginx, infra-certbot non touchés)"
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
run_remote remote_deploy REL SHA REF VERSION GITREF
[ "$APPLY" = 1 ] || { echo; echo "DRY-RUN : rien n'a été envoyé ni exécuté. Relancer avec --apply."; }
