#!/usr/bin/env bash
# Smoke test of a running CastBridge server (after deploy.sh, or locally): public routes, admin API, signed manifest,
# resumable download, device registration, quiz, admin web, and that the internal ports are not exposed.
#
#   ./smoke-test.sh [base-url]        # default http://127.0.0.1:${CASTBRIDGE_PORT:-7090}
#   SMOKE_APK=path/to/app.apk SMOKE_APP=tv SMOKE_VERSION_CODE=42 ./smoke-test.sh   # also publish + download that APK
#
# Reads CASTBRIDGE_ADMIN_TOKEN from .env. Writes nothing unless SMOKE_APK is given (then a release is published:
# use a test versionCode, and delete it afterwards from /admin/releases).
set -Eeuo pipefail
cd "$(dirname "$(readlink -f "$0")")"
[ -f .env ] && { set -a; . ./.env; set +a; }
BASE="${1:-http://127.0.0.1:${CASTBRIDGE_PORT:-7090}}"
TOKEN="${CASTBRIDGE_ADMIN_TOKEN:?CASTBRIDGE_ADMIN_TOKEN manquant}"
fails=0
ok()   { printf '  OK   %s\n' "$*"; }
ko()   { printf '  ÉCHEC %s\n' "$*"; fails=$((fails + 1)); }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
expect() { local want="$1" label="$2"; shift 2; local got; got="$(code "$@")"; [ "$got" = "$want" ] && ok "$label ($got)" || ko "$label : $got au lieu de $want"; }

echo "Serveur : $BASE"
expect 200 "clé publique" "$BASE/api/v1/updates/public-key"
curl -s -D - -o /dev/null "$BASE/api/v1/updates/public-key" | grep -qi '^x-content-type-options: nosniff' && ok "en-têtes de sécurité" || ko "en-têtes de sécurité"
expect 401 "admin sans jeton" "$BASE/api/v1/admin/releases"
expect 200 "admin avec jeton" -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/admin/releases"
expect 401 "OpenAPI sans jeton" "$BASE/v3/api-docs"
expect 200 "OpenAPI avec jeton" -H "Authorization: Bearer $TOKEN" "$BASE/v3/api-docs"
expect 404 "actuator non exposé" "$BASE/actuator/health"
expect 403 "CORS fermé" -X OPTIONS -H "Origin: https://evil.example" -H "Access-Control-Request-Method: GET" "$BASE/api/v1/quiz/draw"
expect 200 "questions publiées" "$BASE/api/v1/quiz/questions?size=5"
expect 200 "tirage de 15 questions" "$BASE/api/v1/quiz/draw?track=general&count=15"
expect 302 "admin web → connexion" "$BASE/admin"
expect 200 "page de connexion" "$BASE/admin/login"

reg="$(curl -s -X POST -H 'Content-Type: application/json' "$BASE/api/v1/devices/register" \
    -d "{\"installId\":\"$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)\",\"app\":\"tv\",\"versionCode\":1,\"platform\":\"other\",\"model\":\"smoke-test\"}")"
dtoken="$(printf '%s' "$reg" | sed -n 's/.*"deviceToken":"\([^"]*\)".*/\1/p')"
[ -n "$dtoken" ] && ok "enregistrement d'appareil" || ko "enregistrement d'appareil : $reg"
expect 200 "heartbeat" -X POST -H "Authorization: Bearer $dtoken" -H 'Content-Type: application/json' -d '{"versionCode":1}' "$BASE/api/v1/devices/heartbeat"
expect 401 "heartbeat sans jeton" -X POST -H 'Content-Type: application/json' -d '{}' "$BASE/api/v1/devices/heartbeat"

if [ -n "${SMOKE_APK:-}" ]; then
    app="${SMOKE_APP:-tv}"; vc="${SMOKE_VERSION_CODE:?SMOKE_VERSION_CODE}"
    pub="$(curl -s -H "Authorization: Bearer $TOKEN" -F app="$app" -F abi="${SMOKE_ABI:-universal}" -F versionCode="$vc" \
        -F versionName="smoke-$vc" -F channel=beta -F notes="Test de fumée" -F file=@"$SMOKE_APK" "$BASE/api/v1/admin/releases")"
    printf '%s' "$pub" | grep -q '"sha256"' && ok "publication de l'APK" || ko "publication : $pub"
    man="$(curl -s "$BASE/api/v1/updates/$app/latest?channel=beta&versionCode=0&abis=${SMOKE_ABI:-universal}")"
    url="$(printf '%s' "$man" | sed -n 's/.*"url":"\([^"]*\)".*/\1/p')"
    printf '%s' "$man" | grep -q '"signature"' && ok "manifeste signé" || ko "manifeste : $man"
    path="/dl/${url#*/dl/}"
    expect 206 "reprise de téléchargement (Range)" -H 'Range: bytes=10-' "$BASE$path"
    size="$(curl -s -o /dev/null -w '%{size_download}' "$BASE$path")"
    [ "$size" = "$(wc -c < "$SMOKE_APK" | tr -d ' ')" ] && ok "téléchargement complet ($size octets)" || ko "taille téléchargée $size"
fi

[ "$fails" -eq 0 ] && echo "Tout est bon." || { echo "$fails échec(s)."; exit 1; }
