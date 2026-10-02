#!/bin/sh
# AuthorizedKeysCommand de sshd (installé en /usr/local/sbin/cb-authkeys.sh, propriétaire root, mode 0755, jamais modifiable par un autre).
# Usage : cb-authkeys.sh <compte>. Écrit sur la sortie standard les clés autorisées du compte, lues dans les fichiers produits par le backend CastBridge.
# Ne lit QUE ces deux fichiers ; tout autre compte reçoit une sortie vide (donc aucun accès).
CB_TUNNEL_DIR="${CB_TUNNEL_DIR:-/var/lib/castbridge/tunnel}"
case "$1" in
  cbtunnel) f="$CB_TUNNEL_DIR/authorized_keys" ;;
  cbexpert) f="$CB_TUNNEL_DIR/experts_authorized_keys" ;;
  *) exit 0 ;;
esac
[ -f "$f" ] && [ -r "$f" ] && exec /bin/cat -- "$f"
exit 0
