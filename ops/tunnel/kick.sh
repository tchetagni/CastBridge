#!/bin/sh
# Coupe la connexion SSH ouverte d'une TV dont le tunnel vient d'être révoqué (sshd ne relit pas authorized_keys pour une session déjà ouverte).
# Usage : kick.sh <port du tunnel, 22100-22999> [--apply]      Sans --apply : montre seulement ce qui serait fait.
set -eu
PORT="${1:-}"; APPLY="${2:-}"
case "$PORT" in ''|*[!0-9]*) echo "Usage : $0 <port 22100-22999> [--apply]" >&2; exit 2;; esac
[ "$PORT" -ge 22100 ] && [ "$PORT" -le 22999 ] || { echo "Port hors de la plage des tunnels (22100-22999)" >&2; exit 2; }
pid="$(ss -Htlnp "sport = :$PORT" 2>/dev/null | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | head -n1)"
[ -n "$pid" ] || { echo "Rien n'écoute sur 127.0.0.1:$PORT : la TV n'est pas connectée."; exit 0; }
echo "Processus sshd de la session qui tient le port $PORT : $pid ($(ps -o user=,args= -p "$pid" 2>/dev/null))"
if [ "$APPLY" = "--apply" ]; then kill "$pid" && echo "Session coupée."; else echo "Rien n'a été fait (ajouter --apply)."; fi
