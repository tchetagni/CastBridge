#!/usr/bin/env bash
# tv-tunnel.sh : joindre la TV depuis ce Mac par le téléphone (USB ADB) et la « Passerelle Bluetooth » de CastBridge.
# Voir docs/REMOTE-TUNNEL-BT.md. Ne lit, n'affiche et ne stocke jamais le code (PIN) de la TV.
set -u

SSH_PORT=2222
API_PORT=18765
ADB="${ADB:-adb}"

usage() {
  cat <<'EOF'
Usage : tv-tunnel.sh up|status|down [numéro-de-série]
        tv-tunnel.sh --help

Relie ce Mac à la TV par le téléphone branché en USB (débogage USB autorisé), quand la
« Passerelle Bluetooth » de CastBridge tourne sur le téléphone (onglet « CastBridge TV »,
bouton « Passerelle Bluetooth »).

  up      vérifie que la passerelle écoute sur le téléphone, puis redirige les ports
          2222 (SSH de la TV) et 18765 (API de la TV) de ce Mac vers le téléphone ;
          affiche les commandes ssh et curl à taper.
  status  montre les redirections, ce qui écoute sur le téléphone, et si la TV répond.
  down    retire les deux redirections (sans effet si elles n'existent pas).

Numéro de série : facultatif s'il n'y a qu'un seul téléphone branché (les émulateurs
sont ignorés) ; obligatoire sinon (voir « adb devices »).

Sécurité : le SSH exige une clé autorisée sur la TV ; l'API exige le code de la TV ou
un jeton (remplacez <code> vous-même, ce script ne le connaît pas). Les ports ne sont
ouverts que sur 127.0.0.1 de ce Mac.
EOF
}

die() { echo "tv-tunnel : $1" >&2; exit "${2:-1}"; }

pick_serial() {
  local given="${1:-}"
  local out phones n
  out="$("$ADB" devices 2>/dev/null)" || die "adb introuvable ou en échec (Android SDK platform-tools installé ?)" 4
  if [ -n "$given" ]; then
    printf '%s\n' "$out" | awk -v s="$given" '$1==s && $2=="device"{f=1} END{exit f?0:1}' \
      || die "téléphone « $given » absent ou non autorisé (adb devices)" 3
    echo "$given"; return
  fi
  phones="$(printf '%s\n' "$out" | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1}')"
  n="$(printf '%s' "$phones" | grep -c . || true)"
  [ "$n" -eq 0 ] && die "aucun téléphone branché et autorisé (adb devices)" 3
  [ "$n" -gt 1 ] && die "plusieurs téléphones branchés : précisez le numéro de série (tv-tunnel.sh up <série>)" 2
  echo "$phones"
}

# Prints "loopback", "network" or "none" for a port, from the phone's /proc/net/tcp{,6} (read only).
listening() {
  local serial="$1" port="$2" hex rows
  hex="$(printf '%04X' "$port")"
  rows="$("$ADB" -s "$serial" shell cat /proc/net/tcp6 /proc/net/tcp 2>/dev/null | tr -d '\r' \
    | awk -v h=":$hex" '{split($2,a,":"); if (":" a[2]==h && $4=="0A") print a[1]}')"
  if [ -z "$rows" ]; then echo none
  elif printf '%s\n' "$rows" | grep -Eq '^(0+|0+FFFF0+)$'; then echo network
  else echo loopback
  fi
}

print_commands() {
  echo "SSH (clé autorisée sur la TV) :  ssh -p $SSH_PORT tv@127.0.0.1"
  echo "  état en lecture seule :         ssh -p $SSH_PORT tv@127.0.0.1 'cbdev status'"
  echo "API (code de la TV) :             curl -H 'X-CB-Pin: <code>' http://127.0.0.1:$API_PORT/api/hello"
}

cmd="${1:-}"
case "$cmd" in
  -h|--help|help) usage; exit 0 ;;
  up|status|down) ;;
  "") usage >&2; exit 2 ;;
  *) echo "tv-tunnel : commande inconnue « $cmd »" >&2; usage >&2; exit 2 ;;
esac
[ $# -gt 2 ] && die "trop d'arguments (tv-tunnel.sh --help)" 2
serial="$(pick_serial "${2:-}")" || exit $?

case "$cmd" in
  up)
    ok=0
    for p in "$SSH_PORT" "$API_PORT"; do
      where="$(listening "$serial" "$p")"
      case "$where" in
        none) echo "Port $p : la passerelle n'écoute pas sur le téléphone." ;;
        network) echo "Port $p : écoute sur le téléphone, EXPOSÉ aussi sur ses réseaux (choix fait dans l'app)."; "$ADB" -s "$serial" forward "tcp:$p" "tcp:$p" >/dev/null && ok=$((ok+1)) ;;
        loopback) echo "Port $p : écoute sur le téléphone (boucle locale seulement)."; "$ADB" -s "$serial" forward "tcp:$p" "tcp:$p" >/dev/null && ok=$((ok+1)) ;;
      esac
    done
    if [ "$ok" -eq 0 ]; then
      echo "Démarrez-la sur le téléphone : CastBridge > onglet « CastBridge TV » > « Passerelle Bluetooth »." >&2
      exit 5
    fi
    echo "Redirections en place sur 127.0.0.1 de ce Mac (téléphone $serial)."
    print_commands
    ;;
  status)
    echo "Téléphone : $serial"
    "$ADB" forward --list 2>/dev/null | awk -v s="$serial" -v a="tcp:$SSH_PORT" -v b="tcp:$API_PORT" '$1==s && ($2==a || $2==b) {print "Redirection : " $2 " -> " $3; f=1} END{if(!f) print "Redirection : aucune"}'
    for p in "$SSH_PORT" "$API_PORT"; do echo "Port $p sur le téléphone : $(listening "$serial" "$p")"; done
    if command -v curl >/dev/null 2>&1 && curl -s -m 8 "http://127.0.0.1:$API_PORT/api/hello" 2>/dev/null | grep -q castbridge-tv; then
      echo "TV : répond par l'API (hello, sans code)"
    else
      echo "TV : pas de réponse de l'API sur 127.0.0.1:$API_PORT"
    fi
    ;;
  down)
    for p in "$SSH_PORT" "$API_PORT"; do "$ADB" -s "$serial" forward --remove "tcp:$p" >/dev/null 2>&1 || true; done
    echo "Redirections 2222 et 18765 retirées (téléphone $serial). La passerelle continue sur le téléphone : arrêtez-la dans l'app si besoin."
    ;;
esac
