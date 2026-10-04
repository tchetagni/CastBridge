#!/usr/bin/env bash
# Collecteur d'hôte des résultats de parties misées (W22, w22-05). À lancer par cron toutes les 5 minutes SUR L'HÔTE du serveur, jamais dans un conteneur :
#   */5 * * * * /opt/castbridge/tools/wallet/collect-results.sh >> /var/log/castbridge-collect-results.log 2>&1
#
# Il copie les résultats signés (cbr1) gardés par le service de jeu (play-state/results, 7 jours) puis poste chacun, tel quel, à l'API en local :
#   docker cp castbridge-play:/data/play-state/results/.  ->  <travail>/incoming/
#   curl -sf -X POST http://127.0.0.1:7090/api/v1/wallet/settle  (un fichier = un cbr1 en texte brut)
# La route de règlement n'exige AUCUN secret : le résultat est signé par la clé du service de jeu, et l'API vérifie la signature, les blocages et la conservation. Ce script ne détient donc
# aucun secret, ne parle qu'à 127.0.0.1, et le service de jeu n'appelle jamais l'API (c'est l'hôte qui poste).
#
# Classement par fichier selon la réponse :
#   2xx                         -> done/      (réglé, ou rejeu : l'API est idempotente)
#   4xx sauf 403 et 429 (définitif) -> rejected/  (RESULT_AFTER_REFUND, illisible... : inutile de réessayer ; la raison est journalisée)
#   403 (signature fausse OU rotation de clés : le service de jeu signe déjà avec la clé n° 2, l'API pas encore) -> réessayé pendant 6 h, puis rejected/
#   429, 5xx, réseau            -> reste dans incoming/ : réessayé au prochain passage
# Un fichier déjà classé (même nom dans done/ ou rejected/) n'est pas reposté. Les fichiers classés de plus de 14 jours sont supprimés.
# Sécurité (audit w22-05, F3) : la copie va dans un dossier NEUF ; seuls les fichiers ordinaires de moins de 9 Ko sont gardés, TOUT lien symbolique est refusé et journalisé (un conteneur de jeu
# compromis ne peut pas faire lire un fichier de l'hôte au cron) ; un verrou (flock, sinon un dossier-verrou avec le pid) empêche deux passages simultanés.
# Exigence pour w22-04 : nommer chaque fichier par son identifiant de résultat (rid) : un nom réutilisé serait ignoré pour toujours (dédoublonnage par nom).
#
# Réglages (variables d'environnement, aucune valeur secrète) :
#   COLLECT_CONTAINER   conteneur du service de jeu            (castbridge-play)
#   COLLECT_REMOTE_DIR  dossier des résultats dans le conteneur (/data/play-state/results)
#   COLLECT_API         adresse locale de l'API                 (http://127.0.0.1:7090)
#   COLLECT_WORK        dossier de travail de l'hôte            (/var/lib/castbridge/collect-results)
#   COLLECT_SOURCE_DIR  si défini : copie ce dossier au lieu de « docker cp » (essais)
# Essai sans réseau ni Docker :  tools/wallet/collect-results.sh --self-test
set -u

log() { printf '%s collect-results: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"; }

# Verrou : flock si disponible (Linux), sinon un dossier-verrou portant le pid (un pid mort laisse le verrou périmé : il est repris).
acquire_lock() {
  local lock="$1/.lock"
  if [ -z "${COLLECT_NO_FLOCK:-}" ] && command -v flock >/dev/null 2>&1; then
    exec 9>"$lock.flock"
    flock -n 9 || return 1
    return 0
  fi
  if mkdir "$lock" 2>/dev/null; then printf '%s\n' "$$" > "$lock/pid"; return 0; fi
  local pid
  pid="$(cat "$lock/pid" 2>/dev/null || true)"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then return 1; fi
  rm -rf "$lock"
  mkdir "$lock" 2>/dev/null || return 1
  printf '%s\n' "$$" > "$lock/pid"
}

release_lock() {
  exec 9>&- 2>/dev/null || true
  rm -rf "$1/.lock"
}

collect() {
  local WORK="${COLLECT_WORK:-/var/lib/castbridge/collect-results}" rc
  mkdir -p "$WORK" || { log "dossier de travail inaccessible : $WORK"; return 1; }
  if ! acquire_lock "$WORK"; then log "un autre passage est en cours : celui-ci s'arrête"; return 0; fi
  collect_locked
  rc=$?
  release_lock "$WORK"
  return "$rc"
}

collect_locked() {
  # réglages lus à chaque passage (l'auto-test les change entre deux passages)
  local CONTAINER="${COLLECT_CONTAINER:-castbridge-play}" REMOTE_DIR="${COLLECT_REMOTE_DIR:-/data/play-state/results}" API="${COLLECT_API:-http://127.0.0.1:7090}"
  local WORK="${COLLECT_WORK:-/var/lib/castbridge/collect-results}" SOURCE_DIR="${COLLECT_SOURCE_DIR:-}"
  mkdir -p "$WORK/incoming" "$WORK/done" "$WORK/rejected" || { log "dossier de travail inaccessible : $WORK"; return 1; }
  local fresh links=0 g gname
  fresh="$(mktemp -d "$WORK/copy.XXXXXX")" || { log "dossier de copie impossible"; return 1; }
  if [ -n "$SOURCE_DIR" ]; then
    cp -R "$SOURCE_DIR"/. "$fresh/" 2>/dev/null || true
  else
    docker cp "$CONTAINER:$REMOTE_DIR/." "$fresh/" 2>/dev/null || { log "copie impossible depuis $CONTAINER (service arrêté ou aucun résultat)"; rm -rf "$fresh"; return 0; }
  fi
  # seuls les fichiers ordinaires (jamais un lien, jamais un dossier), de moins de 9 Ko, entrent dans incoming/ ; un fichier déjà connu n'est ni remplacé ni retouché (son âge compte pour les 403)
  for g in "$fresh"/* "$fresh"/.[!.]*; do
    [ -e "$g" ] || [ -L "$g" ] || continue
    gname="$(basename "$g")"
    if [ -L "$g" ]; then links=$((links + 1)); log "lien symbolique refusé : $gname"; continue; fi
    [ -f "$g" ] || continue
    [ -n "$(find "$g" -maxdepth 0 -type f ! -type l -size -9k 2>/dev/null)" ] || { log "fichier trop gros ignoré : $gname"; continue; }
    [ -e "$WORK/incoming/$gname" ] || [ -e "$WORK/done/$gname" ] || [ -e "$WORK/rejected/$gname" ] || mv "$g" "$WORK/incoming/$gname"
  done
  rm -rf "$fresh"
  local posted=0 done_n=0 rejected_n=0 retry_n=0 skipped=0 f name code body
  for f in "$WORK"/incoming/*; do
    [ -f "$f" ] && [ ! -L "$f" ] || continue
    name="$(basename "$f")"
    if [ -e "$WORK/done/$name" ] || [ -e "$WORK/rejected/$name" ]; then rm -f "$f"; skipped=$((skipped + 1)); continue; fi
    body="$(mktemp)"
    code="$(curl -s -o "$body" -w '%{http_code}' -X POST -H 'Content-Type: text/plain' --data-binary "@$f" --max-time 20 "$API/api/v1/wallet/settle" 2>/dev/null || true)"
    code="${code: -3}"
    [ -n "$code" ] || code=000
    posted=$((posted + 1))
    case "$code" in
      2??) mv "$f" "$WORK/done/$name"; done_n=$((done_n + 1)) ;;
      403)
        if [ -n "$(find "$f" -maxdepth 0 -mmin +360 2>/dev/null)" ]; then
          mv "$f" "$WORK/rejected/$name"; touch "$WORK/rejected/$name"; rejected_n=$((rejected_n + 1)); log "refusé définitivement après 6 h : $name (HTTP 403, signature jamais acceptée) $(tr -d '\n' < "$body" | cut -c1-200)"
        else
          retry_n=$((retry_n + 1)); log "à réessayer pendant 6 h : $name (HTTP 403 : rotation de clés possible)"
        fi ;;
      429|5??|000) retry_n=$((retry_n + 1)); log "à réessayer : $name (HTTP $code)" ;;
      4??) mv "$f" "$WORK/rejected/$name"; rejected_n=$((rejected_n + 1)); log "refusé définitivement : $name (HTTP $code) $(tr -d '\n' < "$body" | cut -c1-200)" ;;
      *) retry_n=$((retry_n + 1)); log "réponse inattendue : $name (HTTP $code)" ;;
    esac
    rm -f "$body"
  done
  find "$WORK/done" "$WORK/rejected" -type f -mtime +14 -delete 2>/dev/null || true
  log "postés=$posted réglés=$done_n refusés=$rejected_n à_réessayer=$retry_n déjà_vus=$skipped liens_refusés=$links"
}

self_test() {
  local tmp port srv_pid fail=0
  tmp="$(mktemp -d)"
  mkdir -p "$tmp/source"
  # un dossier de « résultats » : l'extension décide de la réponse du serveur factice (aucun vrai cbr1, aucun secret)
  printf 'cbr1.AAAA.ok-1\n' > "$tmp/source/r1.ok"
  printf 'cbr1.AAAA.ok-2\n' > "$tmp/source/r2.ok"
  printf 'cbr1.AAAA.refused\n' > "$tmp/source/r3.refused"
  printf 'cbr1.AAAA.later\n' > "$tmp/source/r4.later"
  printf 'cbr1.AAAA.forged\n' > "$tmp/source/r5.forged"
  printf 'SECRET-HOST-FILE-CONTENT\n' > "$tmp/host-secret.txt"
  ln -s "$tmp/host-secret.txt" "$tmp/source/evil.cbr1"
  cat > "$tmp/server.py" <<'PY'
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
LOG = sys.argv[2]
class H(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get('Content-Length', '0'))
        body = self.rfile.read(n).decode()
        with open(LOG, 'a') as f:
            f.write(self.path + ' ' + body.strip() + '\n')
        code = 200 if body.strip().endswith(('ok-1', 'ok-2')) else 409 if body.strip().endswith('refused') else 403 if body.strip().endswith('forged') else 503
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(b'{"message":"essai"}')
    def log_message(self, *a):
        pass
srv = HTTPServer(('127.0.0.1', 0), H)
open(sys.argv[1], 'w').write(str(srv.server_address[1]))
srv.serve_forever()
PY
  python3 "$tmp/server.py" "$tmp/port" "$tmp/requests.log" &
  srv_pid=$!
  for _ in $(seq 1 50); do [ -s "$tmp/port" ] && break; sleep 0.1; done
  port="$(cat "$tmp/port" 2>/dev/null || true)"
  if [ -z "$port" ]; then log "AUTO-TEST ÉCHEC : serveur factice non démarré"; kill "$srv_pid" 2>/dev/null; rm -rf "$tmp"; return 1; fi
  COLLECT_API="http://127.0.0.1:$port" COLLECT_WORK="$tmp/work" COLLECT_SOURCE_DIR="$tmp/source" collect > "$tmp/run1.log" 2>&1
  [ -f "$tmp/work/done/r1.ok" ] && [ -f "$tmp/work/done/r2.ok" ] || { log "AUTO-TEST ÉCHEC : les résultats acceptés ne sont pas dans done/"; fail=1; }
  [ -f "$tmp/work/rejected/r3.refused" ] || { log "AUTO-TEST ÉCHEC : le refus définitif n'est pas dans rejected/"; fail=1; }
  [ -f "$tmp/work/incoming/r4.later" ] || { log "AUTO-TEST ÉCHEC : l'erreur serveur doit rester à réessayer"; fail=1; }
  [ "$(grep -c . "$tmp/requests.log")" = "5" ] || { log "AUTO-TEST ÉCHEC : 5 requêtes attendues au premier passage (le lien symbolique n'est jamais posté)"; fail=1; }
  if grep -q 'SECRET-HOST-FILE-CONTENT' "$tmp/requests.log"; then log "AUTO-TEST ÉCHEC : un lien symbolique a fait lire un fichier de l'hôte"; fail=1; fi
  [ ! -e "$tmp/work/done/evil.cbr1" ] && [ ! -e "$tmp/work/incoming/evil.cbr1" ] || { log "AUTO-TEST ÉCHEC : le lien symbolique doit être refusé (ni posté ni gardé)"; fail=1; }
  [ -f "$tmp/work/incoming/r5.forged" ] || { log "AUTO-TEST ÉCHEC : un 403 (rotation de clés) doit rester à réessayer"; fail=1; }
  grep -q '^/api/v1/wallet/settle cbr1.AAAA.ok-1$' "$tmp/requests.log" || { log "AUTO-TEST ÉCHEC : le cbr1 doit être posté tel quel à /api/v1/wallet/settle"; fail=1; }
  # deuxième passage : seuls les fichiers à réessayer sont repostés (idempotence côté collecteur)
  COLLECT_API="http://127.0.0.1:$port" COLLECT_WORK="$tmp/work" COLLECT_SOURCE_DIR="$tmp/source" collect > "$tmp/run2.log" 2>&1
  [ "$(grep -c . "$tmp/requests.log")" = "7" ] || { log "AUTO-TEST ÉCHEC : au second passage seuls r4.later et r5.forged doivent être repostés"; fail=1; }
  # un 403 vieux de plus de 6 h devient définitif
  touch -t 200001010000 "$tmp/work/incoming/r5.forged"
  COLLECT_API="http://127.0.0.1:$port" COLLECT_WORK="$tmp/work" COLLECT_SOURCE_DIR="$tmp/source" collect > "$tmp/run2b.log" 2>&1
  [ -f "$tmp/work/rejected/r5.forged" ] || { log "AUTO-TEST ÉCHEC : un 403 de plus de 6 h doit finir dans rejected/"; fail=1; }
  # un passage déjà en cours : le second ne poste rien (verrou)
  before="$(grep -c . "$tmp/requests.log")"
  mkdir -p "$tmp/work/.lock"
  sleep 30 &
  holder=$!
  printf '%s\n' "$holder" > "$tmp/work/.lock/pid"
  COLLECT_NO_FLOCK=1 COLLECT_API="http://127.0.0.1:$port" COLLECT_WORK="$tmp/work" COLLECT_SOURCE_DIR="$tmp/source" collect > "$tmp/run2c.log" 2>&1
  [ "$(grep -c . "$tmp/requests.log")" = "$before" ] || { log "AUTO-TEST ÉCHEC : un passage en cours doit bloquer le suivant"; fail=1; }
  kill "$holder" 2>/dev/null; wait "$holder" 2>/dev/null
  rm -rf "$tmp/work/.lock"
  # API injoignable : rien n'est perdu
  COLLECT_API="http://127.0.0.1:1" COLLECT_WORK="$tmp/work" COLLECT_SOURCE_DIR="$tmp/source" collect > "$tmp/run3.log" 2>&1
  [ -f "$tmp/work/incoming/r4.later" ] && [ -f "$tmp/work/done/r1.ok" ] || { log "AUTO-TEST ÉCHEC : un réseau coupé ne doit rien déplacer"; fail=1; }
  kill "$srv_pid" 2>/dev/null; wait "$srv_pid" 2>/dev/null
  if [ "$fail" = 0 ]; then log "AUTO-TEST OK"; else cat "$tmp/run1.log" "$tmp/run2.log" "$tmp/run2b.log" "$tmp/run2c.log" "$tmp/run3.log"; fi
  rm -rf "$tmp"
  return "$fail"
}

if [ "${1:-}" = "--self-test" ]; then self_test; exit $?; fi
collect
