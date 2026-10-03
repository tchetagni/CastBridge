#!/usr/bin/env bash
# Active le SSH de CastBridge-TV par Bluetooth, depuis ce Mac, via la passerelle du téléphone.
#
#   tools/remote/tv-ssh-deploy.sh [--minutes N] [--key FICHIER.pub] [--api URL] [--dry-run]
#
# Prérequis : le téléphone relié en USB (adb), la « Passerelle Bluetooth » démarrée sur le téléphone (API + SSH, boucle locale),
# `tools/remote/tv-tunnel.sh up` (redirections adb 18765 et 2222), la TV allumée et appairée en Bluetooth.
# Le code PIN de la TV est lu dans la variable CB_PIN ou, à défaut, demandé SANS ÉCHO. Il n'est jamais affiché, jamais mis en
# argument de commande (il passe par l'entrée standard de curl) et jamais écrit sur le disque.
# Étapes : 1) /api/hello  2) état du SSH  3) activation  4) autorisation de la clé publique de ce Mac  5) essai de connexion.
set -u
API="http://127.0.0.1:18765"; KEY="$HOME/.ssh/id_ed25519.pub"; MINUTES=""; DRY=0
usage() { sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; }
while [ $# -gt 0 ]; do
  case "$1" in
    --minutes) MINUTES="${2:-}"; shift 2 ;;
    --key) KEY="${2:-}"; shift 2 ;;
    --api) API="${2:-}"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Option inconnue : $1" >&2; usage >&2; exit 2 ;;
  esac
done
case "$API" in http://127.0.0.1:*|http://localhost:*) ;; *) echo "Refusé : l'adresse de l'API doit être locale (127.0.0.1) : le code PIN ne doit jamais partir sur un réseau." >&2; exit 2 ;; esac
if [ -n "$MINUTES" ] && ! [[ "$MINUTES" =~ ^[0-9]{1,5}$ ]]; then echo "--minutes attend un nombre entier de minutes." >&2; exit 2; fi
[ -f "$KEY" ] || { echo "Clé publique introuvable : $KEY (utilisez --key)." >&2; exit 2; }
case "$(cut -d' ' -f1 "$KEY")" in ssh-ed25519|ssh-rsa|ecdsa-sha2-*) ;; *) echo "Ce n'est pas une clé publique SSH : $KEY" >&2; exit 2 ;; esac
if [ "$DRY" = 1 ]; then
  echo "Simulation : rien n'est envoyé."
  echo "1. GET  $API/api/hello"
  echo "2. GET  $API/api/ssh                       (en-tête X-CB-Pin: ••••••)"
  echo "3. POST $API/api/ssh/enable${MINUTES:+?minutes=$MINUTES}   (en-tête X-CB-Pin: ••••••)"
  echo "4. POST $API/api/ssh/key?key=<$(cut -d' ' -f1 "$KEY") … $(awk '{print $3}' "$KEY")>   (en-tête X-CB-Pin: ••••••)"
  echo "5. ssh -p 2222 tv@127.0.0.1 'echo ok'"
  exit 0
fi
command -v curl >/dev/null || { echo "curl est requis." >&2; exit 1; }

PIN="${CB_PIN:-}"
if [ -z "$PIN" ]; then
  [ -t 0 ] || { echo "Aucun terminal pour saisir le code : exportez CB_PIN ou lancez ce script dans un terminal." >&2; exit 2; }
  printf "Code PIN de la TV (saisie masquée) : " >&2; IFS= read -r -s PIN; echo >&2
fi
[[ "$PIN" =~ ^[0-9]{4,8}$ ]] || { echo "Le code PIN doit être composé de 4 à 8 chiffres." >&2; exit 2; }

# curl lit l'en-tête secret sur son entrée standard (--config -) : le PIN n'apparaît jamais dans `ps`.
call() {  # call METHOD CHEMIN [données...]  -> corps sur stdout, code HTTP sur la dernière ligne
  local method="$1" path="$2"; shift 2
  printf 'header = "X-CB-Pin: %s"\n' "$PIN" | curl -sS -m 25 --config - -X "$method" "$@" -w '\n%{http_code}' "$API$path" 2>&1
}
show() {  # affiche le corps sans jamais laisser passer le PIN
  local body="${1%$'\n'*}"; printf '%s\n' "${body//$PIN/••••••}"
}
step() { echo; echo "== $1"; }

step "1. La TV répond-elle par Bluetooth ?"
H="$(curl -sS -m 15 "$API/api/hello" 2>&1)" || { echo "Pas de réponse : $H" >&2; echo "Vérifiez : passerelle démarrée sur le téléphone, tv-tunnel.sh up, TV allumée à portée." >&2; exit 1; }
echo "$H"

step "2. État actuel du SSH"
R="$(call GET /api/ssh)"; CODE="${R##*$'\n'}"; show "$R"
case "$CODE" in 200) ;; 401|403) echo "Code PIN refusé par la TV (HTTP $CODE). Ne réessayez pas en boucle : la TV se verrouille après quelques essais." >&2; exit 3 ;; *) echo "Réponse inattendue (HTTP $CODE)." >&2; exit 1 ;; esac

step "3. Activation du SSH"
if [ -n "$MINUTES" ]; then R="$(call POST /api/ssh/enable -G --data-urlencode "minutes=$MINUTES")"; else R="$(call POST /api/ssh/enable)"; fi
CODE="${R##*$'\n'}"; show "$R"; [ "$CODE" = 200 ] || { echo "Activation refusée (HTTP $CODE)." >&2; exit 1; }

step "4. Autorisation de la clé publique de ce Mac"
R="$(call POST /api/ssh/key -G --data-urlencode "key@$KEY")"
CODE="${R##*$'\n'}"; show "$R"; [ "$CODE" = 200 ] || { echo "Clé refusée (HTTP $CODE)." >&2; exit 1; }

step "5. Essai de connexion SSH par Bluetooth"
sleep 2
if ssh -p 2222 -o BatchMode=yes -o ConnectTimeout=20 -o StrictHostKeyChecking=accept-new tv@127.0.0.1 'echo ssh-ok; cbdev status 2>/dev/null | head -4'; then
  echo; echo "SSH actif. Connexion : ssh -p 2222 tv@127.0.0.1"
else
  echo; echo "Le SSH est activé mais la connexion a échoué : relancez la passerelle sur le téléphone puis réessayez : ssh -p 2222 tv@127.0.0.1" >&2; exit 1
fi
