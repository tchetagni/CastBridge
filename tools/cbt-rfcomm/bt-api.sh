#!/bin/sh
# Everything the phone does over Wi-Fi, from a computer, over Bluetooth only: curl against the local end of the TV's API tunnel.
#
# 1. Start the bridge (Mac):  ./cbt-rfcomm proxy <adresse-TV> 18765 api        (Linux: python3 tools/bt-ssh-bridge.py <adresse-TV> --service api --listen 18765)
# 2. Use it:                  CB_PIN=<code de la TV> ./bt-api.sh <commande> ...
#
# The code (PIN) is read from the environment and sent in the X-CB-Pin header only: never in a URL, never printed.
# The TV still checks it; after 5 wrong codes this computer (not your other devices) is locked for 60 s.
#
#   hello                       GET  /api/hello          (no code needed: is the TV there?)
#   info | library | apk        GET  /api/info | /api/library | /api/apk
#   upload <fichier> [nom]      resumable PUT /upload/<nom>?offset=&total=  (relance = reprise à l'octet déjà reçu)
#   install <nom.apk>           POST /api/apk/install    (the APK must be on the TV: see upload)
#   ssh-key <fichier.pub>       POST /api/ssh/enable puis /api/ssh/key (inscrit votre clé publique, sans réseau)
#   screenshot <sortie.png>     GET  /api/screenshot
#   raw <GET|POST> <chemin>     any other route, e.g.  raw GET /api/bluetooth/tunnel
set -eu
PORT="${CB_PORT:-18765}"
BASE="http://127.0.0.1:$PORT"
need_pin() { [ -n "${CB_PIN:-}" ] || { echo "définissez CB_PIN (code de la TV)" >&2; exit 2; }; }
api() { need_pin; curl -sS --max-time "${CB_TIMEOUT:-120}" -H "X-CB-Pin: $CB_PIN" "$@"; }
enc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }

cmd="${1:-}"; [ $# -gt 0 ] && shift || true
case "$cmd" in
  hello) curl -sS --max-time 60 "$BASE/api/hello"; echo ;;
  info|library|apk) api "$BASE/api/$cmd"; echo ;;
  upload)
    f="$1"; name="${2:-$(basename "$f")}"; total=$(wc -c < "$f" | tr -d ' ')
    qn=$(enc "$name")
    # where did a previous try stop? (the TV keeps <nom>.part)
    off=$(api "$BASE/api/part?name=$qn" | python3 -c 'import sys,json;print(json.load(sys.stdin).get("length",0))' 2>/dev/null || echo 0)
    echo "envoi de $name : reprise à l'octet $off / $total"
    tail -c +"$((off + 1))" "$f" | api -X PUT --data-binary @- -H "Content-Type: application/octet-stream" --max-time 0 \
        "$BASE/upload/$qn?offset=$off&total=$total"; echo ;;
  install) api -X POST "$BASE/api/apk/install?names=$(enc "$1")"; echo ;;
  ssh-key)
    api -X POST "$BASE/api/ssh/enable" >/dev/null
    api -X POST --data-urlencode "key@$1" "$BASE/api/ssh/key"; echo
    echo "ensuite : ssh -p 2222 tv@127.0.0.1 après  ./cbt-rfcomm proxy <adresse-TV> 2222 ssh" ;;
  screenshot) api "$BASE/api/screenshot" -o "$1"; echo "écrit $1" ;;
  raw) m="$1"; p="$2"; shift 2; api -X "$m" "$BASE$p" "$@"; echo ;;
  *) sed -n '2,19p' "$0"; exit 2 ;;
esac
