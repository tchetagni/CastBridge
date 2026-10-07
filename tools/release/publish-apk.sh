#!/usr/bin/env bash
# publish-apk.sh : publie un APK signé sur le serveur (route admin /api/v1/admin/releases) et affiche son URL publique /dl/<app>/<fichier>.
# Le jeton d'administration est lu DANS le conteneur, sur le serveur, et n'est jamais affiché ni copié sur ce Mac.
#
# Usage :
#   tools/release/publish-apk.sh tv    ~/CastBridge-release/SIGNED/CastBridge-TV-0.14.44-beta-verrouillee-armeabi-v7a.apk 115 0.14.44-beta-verrouillee "notes…"
#   tools/release/publish-apk.sh phone ~/CastBridge-release/SIGNED/CastBridge-phone-1.2.52-beta.apk                      82  1.2.52-beta              "notes…"
# Variables : ABI (défaut : armeabi-v7a pour tv, universal pour phone), CHANNEL (stable), SERVER (ubuntu@bridge.sti-cm.com),
#   API (http://127.0.0.1:7090 vu du serveur), PUBLIC (https://bridge.sti-cm.com), MANDATORY (false), ROLLOUT (100).
# Règles (docs/RELEASES.md § 3) : seulement des builds signés avec la clé de release ; JAMAIS un build « -test » ni la variante superadmin du téléphone.
set -euo pipefail

app="${1:?app : tv ou phone}"; apk="${2:?chemin de l’APK}"; code="${3:?versionCode}"; name="${4:?versionName}"; notes="${5:-}"
SERVER="${SERVER:-ubuntu@bridge.sti-cm.com}"; API="${API:-http://127.0.0.1:7090}"; PUBLIC="${PUBLIC:-https://bridge.sti-cm.com}"
CHANNEL="${CHANNEL:-stable}"; MANDATORY="${MANDATORY:-false}"; ROLLOUT="${ROLLOUT:-100}"
case "$app" in
  tv) ABI="${ABI:-armeabi-v7a}" ;;
  phone) ABI="${ABI:-universal}" ;;
  *) echo "app : tv ou phone" >&2; exit 2 ;;
esac
[ -f "$apk" ] || { echo "APK introuvable : $apk" >&2; exit 2; }
base="$(basename "$apk")"
case "$base" in
  *superadmin*|*-test*|*debug*) echo "Refusé : $base n’est pas publiable (superadmin, test ou debug)." >&2; exit 2 ;;
esac
sha_local="$(shasum -a 256 "$apk" | cut -c1-64)"
echo "Fichier : $base ($(du -h "$apk" | cut -f1)), sha256 $sha_local"

ssh -o ConnectTimeout=10 -o BatchMode=yes "$SERVER" 'mkdir -p ~/castbridge/incoming'
scp -q "$apk" "$SERVER:~/castbridge/incoming/$base"
echo "Déposé sur le serveur ; publication…"

# Le script distant reçoit ses paramètres en arguments positionnels ($1…${10}) : aucune interpolation locale dans le code distant
# (écrit dans un fichier temporaire : le bash 3.2 de macOS lit mal un heredoc à l'intérieur d'une substitution de commande).
remote="$(mktemp)"; trap 'rm -f "$remote"' EXIT
cat > "$remote" <<'REMOTE'
set -e
cd ~/castbridge/incoming
T="$(sudo docker exec castbridge-api printenv CASTBRIDGE_ADMIN_TOKEN 2>/dev/null || true)"
if [ -z "$T" ]; then echo JETON_ABSENT; exit 3; fi
curl -sS -m 300 -H "Authorization: Bearer $T" \
  -F "app=$2" -F "abi=$3" -F "versionCode=$4" -F "versionName=$5" -F "channel=$6" -F "mandatory=$7" -F "rollout=$8" -F "notes=${10}" \
  -F "file=@$1" "$9/api/v1/admin/releases"
rm -f -- "$1"
REMOTE
out="$(ssh -o ConnectTimeout=10 -o BatchMode=yes "$SERVER" bash -s -- "$base" "$app" "$ABI" "$code" "$name" "$CHANNEL" "$MANDATORY" "$ROLLOUT" "$API" "$notes" < "$remote")"

OUT="$out" SHA_LOCAL="$sha_local" PUBLIC="$PUBLIC" python3 -I - <<'PY'
import json, os, sys
raw = os.environ["OUT"]
if raw.strip() == "JETON_ABSENT":
    sys.exit("Le conteneur castbridge-api n’a pas de CASTBRIDGE_ADMIN_TOKEN : publiez par la page /admin/releases.")
try:
    j = json.loads(raw)
except Exception:
    sys.exit("Réponse illisible du serveur : " + raw[:400])
if "erreur" in j:
    sys.exit("Refus du serveur %s : %s %s" % (j.get("status"), j.get("message"), j.get("details")))
sha, path = j.get("sha256", ""), j.get("downloadPath", "")
print("Publié : %s %s (%s, code %s, canal %s) ; sha256 serveur %s" % (j.get("app"), j.get("versionName"), j.get("abi"), j.get("versionCode"), j.get("channel"), sha))
if sha != os.environ["SHA_LOCAL"]:
    sys.exit("ALERTE : le sha256 du serveur ne correspond pas au fichier local")
pub = os.environ["PUBLIC"]
print("URL publique : " + pub + path)
print("Vérification : curl -sI " + pub + path + " | grep -i x-content-sha256")
PY
