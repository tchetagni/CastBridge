#!/bin/sh
# Écrit la liste des ports TCP à l'écoute sur la boucle locale de l'hôte (un par ligne) pour le backend, quand celui-ci tourne dans un conteneur
# qui ne voit pas la boucle locale de l'hôte (propriété CASTBRIDGE_TUNNEL_LISTENING_PORTS_FILE). À lancer chaque minute (cron ou timer systemd).
# Usage : listening.sh [fichier]   (défaut : /var/lib/castbridge/tunnel/listening-ports)
OUT="${1:-/var/lib/castbridge/tunnel/listening-ports}"
tmp="$OUT.tmp.$$"
ss -Hltn 2>/dev/null | awk '$4 ~ /^127\.0\.0\.1:/ { sub(/^.*:/, "", $4); print $4 }' | sort -un > "$tmp" && chmod 644 "$tmp" && mv -f "$tmp" "$OUT"
