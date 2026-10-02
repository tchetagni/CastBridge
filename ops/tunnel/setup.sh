#!/bin/sh
# CastBridge : installation côté serveur des tunnels SSH inverses des TV (docs/REMOTE-TUNNEL.md).
# IDEMPOTENT. Sans --apply : AFFICHE seulement ce qui serait fait (aucune modification). Avec --apply : le fait (à lancer en root, par le propriétaire,
# après avoir lu le README et gardé une session ssh root ouverte). Ne touche ni à l'application CastBridge, ni à ses secrets, ni au pare-feu (il affiche les règles).
set -eu

APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1
DIR="$(cd "$(dirname "$0")" && pwd)"
TUNNEL_DIR="${CB_TUNNEL_DIR:-/var/lib/castbridge/tunnel}"
SSH_PORT="${CB_TUNNEL_SSH_PORT:-2200}"

run() { # run <description> <commande...>
  desc="$1"; shift
  if [ "$APPLY" = 1 ]; then echo "[fait]  $desc"; "$@"; else echo "[prévu] $desc"; echo "          \$ $*"; fi
}

[ "$APPLY" = 1 ] && [ "$(id -u)" != 0 ] && { echo "--apply demande root" >&2; exit 1; }
echo "== CastBridge tunnels : $( [ "$APPLY" = 1 ] && echo APPLICATION || echo "SIMULATION (rien n'est modifié ; ajouter --apply)" ) =="

# 1. comptes (sans shell, sans mot de passe)
for u in cbtunnel cbexpert; do
  if id "$u" >/dev/null 2>&1; then echo "[déjà]  compte $u existe"; else
    run "créer le compte $u (sans shell, verrouillé)" useradd --system --create-home --home-dir "/var/lib/$u" --shell /usr/sbin/nologin "$u"
  fi
done

# 2. dossier lu par sshd (écrit par le backend dans son volume : à faire pointer vers le même dossier, voir README)
run "créer $TUNNEL_DIR (lisible par sshd, écrit par le backend)" install -d -m 0755 "$TUNNEL_DIR"

# 3. l'AuthorizedKeysCommand (root, non modifiable)
run "installer /usr/local/sbin/cb-authkeys.sh (root:root, 0755)" install -o root -g root -m 0755 "$DIR/cb-authkeys.sh" /usr/local/sbin/cb-authkeys.sh
run "installer le script de sondage /usr/local/sbin/cb-listening.sh" install -o root -g root -m 0755 "$DIR/listening.sh" /usr/local/sbin/cb-listening.sh

# 4. configuration sshd
run "installer /etc/ssh/sshd_config.d/castbridge-tunnel.conf" install -o root -g root -m 0644 "$DIR/sshd_config.d/castbridge-tunnel.conf" /etc/ssh/sshd_config.d/castbridge-tunnel.conf
if grep -Eqs "^[[:space:]]*Port[[:space:]]+$SSH_PORT([[:space:]]|$)" /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf 2>/dev/null; then
  echo "[déjà]  sshd écoute sur $SSH_PORT"
else
  echo "[A FAIRE À LA MAIN] sshd n'écoute pas encore sur $SSH_PORT : ajouter dans /etc/ssh/sshd_config les DEUX lignes « Port 22 » et « Port $SSH_PORT » (ce script ne modifie jamais sshd_config)."
fi
run "vérifier la configuration sshd" sshd -t
echo "[A FAIRE À LA MAIN] puis : systemctl reload ssh   (garder une session root ouverte et tester une seconde connexion avant de la fermer)"

# 5. journaux
run "installer la rotation des journaux" install -o root -g root -m 0644 "$DIR/logrotate-castbridge-tunnel" /etc/logrotate.d/castbridge-tunnel
run "tâche cron du sondage (chaque minute) pour un backend en conteneur" sh -c "echo '* * * * * root /usr/local/sbin/cb-listening.sh $TUNNEL_DIR/listening-ports' > /etc/cron.d/castbridge-tunnel"

# 6. pare-feu : jamais appliqué par ce script
cat <<NOTE

[PARE-FEU, à appliquer à la main]
  ufw allow $SSH_PORT/tcp comment 'CastBridge tunnels (TV et experts)'
  # NE PAS ouvrir 22100-22999 : ces ports n'écoutent que sur 127.0.0.1 (GatewayPorts no) et doivent rester fermés à Internet.
  ufw status numbered
[FAIL2BAN] voir ops/tunnel/fail2ban-castbridge-tunnel.local et le README.
NOTE
if [ "$APPLY" = 1 ]; then echo "Terminé. Reste à faire à la main : sshd_config (Port), reload de sshd, pare-feu, puis CASTBRIDGE_TUNNEL_ENABLED=true."; else echo "Simulation terminée : rien n'a été modifié."; fi
