#!/usr/bin/env bash
# Test de tools/release/deploy-server.sh --service play, SANS aucune connexion : un faux « ssh » qui échoue et un faux « docker » dans un bac à sable.
#   bash tools/tests/test_deploy_play.sh
# Vérifie : (1) le plan DRY-RUN du service play ; (2) les refus (main, commit nu, étiquette play sans --service play, --service inconnu) ;
# (3) le comportement par défaut (api) IDENTIQUE à celui de la révision 294e2acc (cas témoin, diff vide) ; (4) le script distant du service play joué
# en local avec de faux outils : succès, refus si des salles sont ouvertes, retour arrière si le port n'est pas en 127.0.0.1 ou si la santé échoue.
set -Eeuo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
DEPLOY="$REPO/tools/release/deploy-server.sh"
WITNESS_REV="294e2acc"
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
FAILS=0; PASSES=0
ok() { PASSES=$((PASSES + 1)); printf '  ok   %s\n' "$1"; }
ko() { FAILS=$((FAILS + 1)); printf '  FAIL %s\n' "$1"; }
has() { # has "<description>" "<texte>" "<motif fixe>"
    if printf '%s' "$2" | grep -qF -- "$3"; then ok "$1"; else ko "$1 (absent : $3)"; fi
}
hasnt() {
    if printf '%s' "$2" | grep -qF -- "$3"; then ko "$1 (présent : $3)"; else ok "$1"; fi
}

echo "== syntaxe"
bash -n "$DEPLOY" && ok "bash -n deploy-server.sh" || ko "bash -n deploy-server.sh"

# ------------------------------------------------------------------------------------------ faux arbre git
R="$T/repo"; BIN="$T/bin"; mkdir -p "$R" "$BIN"
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.org GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.org
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null
for f in backend/docker-compose.play.yml backend/.env.play.example backend/sql/play-schema.sql backend/Dockerfile backend/backup.sh \
         server-play/Dockerfile android/gradle.properties android/core/a.txt content/learn/a.txt content/langues/a.txt; do
    mkdir -p "$R/$(dirname "$f")"; printf 'fake %s\n' "$f" > "$R/$f"
done
git -C "$R" init -q -b integration
git -C "$R" add -A && git -C "$R" commit -q -m "arbre factice"
git -C "$R" tag -a server-play-0.1.0 -m t
git -C "$R" tag -a server-1.0.1 -m t
git -C "$R" branch main
git init -q --bare "$T/origin.git"; git init -q --bare "$T/bridge.git"
git -C "$R" remote add origin "$T/origin.git"; git -C "$R" remote add bridge "$T/bridge.git"
git -C "$R" push -q origin integration && git -C "$R" fetch -q origin
SHA="$(git -C "$R" rev-parse HEAD)"

printf '#!/bin/sh\necho appel > "%s/ssh-appele"\necho "SSH INTERDIT EN TEST" >&2\nexit 99\n' "$T" > "$BIN/ssh"; chmod +x "$BIN/ssh"
cp "$BIN/ssh" "$BIN/scp"
dep() { # lance le script sous test (ou $SCRIPT) en dry-run contre le faux arbre ; sortie normalisée (horodatage UTC)
    ( cd "$T" && env PATH="$BIN:$PATH" CB_REPO="$R" CB_BRIDGE_HOST_CHECK=0 bash "${SCRIPT:-$DEPLOY}" "$@" 2>&1; echo "exit=$?" ) | sed -E 's/[0-9]{8}T[0-9]{6}Z/<UTC>/g'
}

# ------------------------------------------------------------------------------------------ 1. plan DRY-RUN du service play
echo "== --service play : plan DRY-RUN"
OUT="$(dep server-play-0.1.0 --service play)"
has "sortie 0" "$OUT" "exit=0"
has "version 0.1.0 déduite du tag server-play-0.1.0" "$OUT" "server-play-0.1.0 (tag) -> $SHA (0.1.0)"
has "image candidate castbridge-play:candidate" "$OUT" "castbridge-play:candidate"
has "santé /play/health" "$OUT" "/play/health"
has "projet compose castbridge-play" "$OUT" "compose --project-name castbridge-play"
has "fichier compose play" "$OUT" "backend/docker-compose.play.yml"
has "construction avec RUNTIME_IMAGE (digest)" "$OUT" "RUNTIME_IMAGE=eclipse-temurin@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636"
has "contexte = racine de la release (Dockerfile du service)" "$OUT" "server-play/Dockerfile"
has "port local seulement" "$OUT" "127.0.0.1:7091"
has "refus si des salles sont ouvertes" "$OUT" "des salles sont ouvertes"
has "release datée sous releases/server-play-0.1.0-" "$OUT" "/home/ubuntu/castbridge/releases/server-play-0.1.0-<UTC>"
has "push du tag play vers bridge (affiché)" "$OUT" "git push bridge refs/tags/server-play-0.1.0:refs/tags/server-play-0.1.0"
has "DRY-RUN annoncé" "$OUT" "DRY-RUN : rien n'a été envoyé"
hasnt "pas de BLOQUANT" "$OUT" "BLOQUANT"
hasnt "castbridge-api:candidate absent du plan play" "$OUT" "castbridge-api:candidate"
hasnt "castbridge-db jamais manipulée (pas de sauvegarde)" "$OUT" "backup.sh"
hasnt "nginx jamais manipulé par le script distant" "$(printf '%s' "$OUT" | sed -n '/--- script distant/,/--- fin ---/p')" "nginx -s"
hasnt "ssh jamais appelé" "$OUT" "SSH INTERDIT"
[ ! -e "$T/ssh-appele" ] && ok "faux ssh jamais lancé" || ko "faux ssh lancé"
[ -z "$(git -C "$T/bridge.git" for-each-ref)" ] && ok "dry-run : rien poussé vers bridge" || ko "dry-run a poussé"
( cd "$T" && env PATH="$BIN:$PATH" CB_REPO="$R" CB_BRIDGE_HOST_CHECK=0 bash "$DEPLOY" server-play-0.1.0 --service play ) | sed -n '/--- script distant/,/--- fin ---/p' | sed '1d;$d' > "$T/remote-play.sh"
bash -n "$T/remote-play.sh" && ok "script distant play : bash -n" || ko "script distant play : bash -n"
for fn in --status --rollback; do
    O2="$(dep "$fn" --service play)"
    has "$fn --service play : DRY-RUN, exit 0" "$O2" "exit=0"
    has "$fn --service play : service play ciblé" "$O2" "castbridge-play"
    has "$fn --service play : liens current-play" "$O2" "current-play"
done

# ------------------------------------------------------------------------------------------ 2. refus
echo "== refus"
OUT="$(dep server-play-0.1.0)"
has "étiquette play sans --service play : BLOQUANT" "$OUT" "BLOQUANT"
has "...avec le message --service play" "$OUT" "utiliser --service play"
OUT="$(dep server-play-0.1.0 --apply)"
has "...et --apply : exit 3" "$OUT" "exit=3"
OUT="$(dep main --service play --apply)"
has "main refusé (--service play --apply) : exit 3" "$OUT" "exit=3"
has "main : message de refus" "$OUT" "refus de déployer/pousser"
OUT="$(dep "$SHA" --service play --apply)"
has "commit nu refusé : exit 3" "$OUT" "exit=3"
has "commit nu : message" "$OUT" "n'est ni un tag ni une branche"
[ ! -e "$T/ssh-appele" ] && ok "refus : ssh jamais lancé" || ko "ssh lancé pendant un refus"
OUT="$(dep server-play-0.1.0 --service inconnu)"
has "--service inconnu : exit 2" "$OUT" "exit=2"
OUT="$(dep server-play-0.1.0 --service)"
has "--service sans valeur : exit 2" "$OUT" "exit=2"

# ------------------------------------------------------------------------------------------ 3. cas témoin : comportement api inchangé
echo "== cas témoin : comportement par défaut (api) identique à $WITNESS_REV"
if git -C "$REPO" cat-file -e "$WITNESS_REV:tools/release/deploy-server.sh" 2>/dev/null; then
    git -C "$REPO" show "$WITNESS_REV:tools/release/deploy-server.sh" > "$T/baseline.sh"
    for args in "server-1.0.1" "server-1.0.1 --no-push" "main --apply" "--status" "--rollback" "server-1.0.1 --push-only"; do
        # shellcheck disable=SC2086
        A="$(SCRIPT="$T/baseline.sh" dep $args)"; B="$(SCRIPT="$DEPLOY" dep $args)"
        if [ "$A" = "$B" ]; then ok "api « $args » : sortie identique"; else ko "api « $args » : sortie différente"; diff <(printf '%s' "$A") <(printf '%s' "$B") | head -n 10 || true; fi
    done
    has "api : toujours castbridge-api:candidate" "$(dep server-1.0.1)" "castbridge-api:candidate"
else
    echo "  (révision témoin $WITNESS_REV absente de ce dépôt : cas témoin ignoré)"
fi

# ------------------------------------------------------------------------------------------ 4. script distant joué en local avec de faux outils
echo "== script distant (bac à sable, faux docker)"
git -C "$R" push -q bridge refs/tags/server-play-0.1.0:refs/tags/server-play-0.1.0
SB="$T/sb"; SH="$SB/shims"; ROOT="$SB/castbridge"; PLIVE="$ROOT/services/play"; LOG="$SB/docker.log"
mkdir -p "$SH" "$PLIVE"
printf 'CASTBRIDGE_PLAY_DIRECT=1\n' > "$PLIVE/.env.play"
cat > "$SH/docker" <<'EOF'
#!/bin/bash
echo "docker $*" >> "$FAKE_LOG"
case "$*" in
  "port castbridge-play") printf '%s\n' "${FAKE_PORT:-8080/tcp -> 127.0.0.1:7091}";;
  *"image inspect -f"*image.revision*) echo "$FAKE_SHA";;
  *"image inspect -f"*) echo "sha256:fake";;
  *State.Running*) echo "${FAKE_RUNNING:-false}";;
  *State.Health*) echo "${FAKE_HEALTH:-healthy}";;
  exec*) echo "{\"status\":\"ok\",\"rooms\":${FAKE_ROOMS:-0},\"maxRooms\":400}";;
esac
exit 0
EOF
printf '#!/bin/sh\nexit 0\n' > "$SH/flock"; printf '#!/bin/sh\nexit 0\n' > "$SH/sleep"
printf '#!/bin/bash\nargs=(); for a in "$@"; do [ "$a" = -T ] || args+=("$a"); done\nexec /bin/mv "${args[@]}"\n' > "$SH/mv"
chmod +x "$SH"/*
play_remote() { # génère le script distant avec le bac à sable puis le joue ; variables FAKE_* héritées
    : > "$LOG"; rm -rf "$ROOT/releases" "$ROOT/current-play" "$ROOT/previous-play" 2>/dev/null || true
    local dry script
    dry="$( cd "$T" && env PATH="$BIN:$PATH" CB_REPO="$R" CB_BRIDGE_HOST_CHECK=0 CB_DEPLOY_ROOT="$ROOT" CB_BARE_REPO="$T/bridge.git" CB_PLAY_LIVE_DIR="$PLIVE" \
            CB_DOCKER=docker CB_HEALTH_TIMEOUT=30 bash "$DEPLOY" server-play-0.1.0 --service play )"
    script="$(printf '%s\n' "$dry" | sed -n '/--- script distant/,/--- fin ---/p' | sed '1d;$d')"
    printf '%s\n' "$script" | env PATH="$SH:$PATH" FAKE_LOG="$LOG" FAKE_SHA="$SHA" bash -s > "$SB/out.txt" 2>&1
}
set +e
FAKE_RUNNING=false play_remote; RC=$?
set -e
OUTR="$(cat "$SB/out.txt")"; LOGS="$(cat "$LOG")"
[ "$RC" = 0 ] && ok "premier déploiement : exit 0" || { ko "premier déploiement : exit $RC"; printf '%s\n' "$OUTR" | tail -n 15; }
[ -L "$ROOT/current-play" ] && ok "lien current-play posé" || ko "lien current-play absent"
[ -f "$(readlink "$ROOT/current-play" 2>/dev/null)/backend/.env.play" ] && ok ".env.play copié dans la release" || ko ".env.play non copié"
[ "$(stat -f '%Lp' "$(readlink "$ROOT/current-play")/backend/.env.play" 2>/dev/null || stat -c '%a' "$(readlink "$ROOT/current-play")/backend/.env.play")" = 600 ] && ok ".env.play en 0600" || ko ".env.play pas en 0600"
for p in server-play/Dockerfile android/core/a.txt content/learn/a.txt content/langues/a.txt android/gradle.properties backend/docker-compose.play.yml; do
    [ -f "$(readlink "$ROOT/current-play")/$p" ] && ok "release contient $p" || ko "release sans $p"
done
[ ! -e "$(readlink "$ROOT/current-play")/backend/Dockerfile" ] && ok "release sans backend/Dockerfile (api non extraite)" || ko "api extraite"
has "build avec RUNTIME_IMAGE par digest" "$LOGS" "RUNTIME_IMAGE=eclipse-temurin@sha256:fcd7fd7b"
has "bascule candidate -> current" "$LOGS" "tag castbridge-play:candidate castbridge-play:current"
has "up du seul service castbridge-play" "$LOGS" "up -d --no-build --no-deps castbridge-play"
hasnt "castbridge-api jamais touché" "$LOGS" "castbridge-api"
hasnt "castbridge-db jamais touchée" "$LOGS" "castbridge-db"
hasnt "aucun « down »" "$LOGS" " down"

FAKE_RUNNING=true FAKE_ROOMS=3 play_remote && RC=0 || RC=$?
LOGS="$(cat "$LOG")"; OUTR="$(cat "$SB/out.txt")"
[ "$RC" = 1 ] && ok "salles ouvertes : refus (exit 1)" || ko "salles ouvertes : exit $RC"
has "message « déployer hors partie »" "$OUTR" "déployer hors partie"
hasnt "aucun up pendant une partie" "$LOGS" "up -d"
[ ! -L "$ROOT/current-play" ] && ok "lien non posé après refus" || ko "lien posé après refus"

FAKE_RUNNING=true FAKE_ROOMS=0 play_remote && RC=0 || RC=$?
[ "$RC" = 0 ] && ok "aucune salle : déploiement autorisé" || ko "aucune salle : exit $RC"

FAKE_RUNNING=true FAKE_ROOMS=3 CB_PLAY_FORCE=1 play_remote && RC=0 || RC=$?
[ "$RC" = 0 ] && ok "CB_PLAY_FORCE=1 : déploiement malgré les salles" || ko "CB_PLAY_FORCE=1 : exit $RC"

FAKE_PORT='8080/tcp -> 0.0.0.0:7091' play_remote && RC=0 || RC=$?
OUTR="$(cat "$SB/out.txt")"
[ "$RC" = 1 ] && ok "port publié sur 0.0.0.0 : échec + retour arrière (exit 1)" || ko "port 0.0.0.0 : exit $RC"
has "message de retour automatique" "$OUTR" "retour automatique"
[ ! -L "$ROOT/current-play" ] && ok "lien non posé quand le port est public" || ko "lien posé malgré le port public"

FAKE_HEALTH=unhealthy play_remote && RC=0 || RC=$?
[ "$RC" = 1 ] && ok "santé en échec : retour arrière (exit 1)" || ko "santé en échec : exit $RC"

echo
printf '== %d ok, %d en échec\n' "$PASSES" "$FAILS"
[ "$FAILS" = 0 ]
