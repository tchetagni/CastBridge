#!/usr/bin/env bash
# Génère la paire Ed25519 DÉDIÉE des tickets de jeu en ligne (w20-04) avec openssl, dans le dossier que l'exploitant nomme.
#
#   tools/play/gen-ticket-keypair.sh <dossier>
#
# - clé PRIVÉE  <dossier>/play-ticket.key      (chmod 600) : le secret de l'API principale (play-ticket.key), JAMAIS dans le dépôt, JAMAIS affichée ;
# - clé PUBLIQUE <dossier>/play-ticket.pub.b64 (une ligne : SPKI X.509 en Base64) : la valeur de CASTBRIDGE_PLAY_TICKET_PUBKEY du service castbridge-play ;
#   et <dossier>/play-ticket.pub (PEM) pour archive.
# - n'écrase JAMAIS : si l'un des trois fichiers existe, le script s'arrête sans rien modifier.
# - n'affiche que le CHEMIN de la clé publique et son empreinte (SHA-256 du SPKI DER) : jamais la clé privée, ni son chemin dans un message d'erreur de contenu.
# Ne se connecte à rien : aucun réseau, aucun serveur.
set -euo pipefail

usage() { echo "Usage : $0 <dossier>   (le dossier est créé en 0700 s'il n'existe pas)" >&2; exit 2; }
[ $# -eq 1 ] || usage
dir="$1"
[ -n "$dir" ] || usage
case "$dir" in -*) dir="./$dir";; esac   # un nom commençant par « - » n'est jamais lu comme une option

command -v openssl >/dev/null 2>&1 || { echo "openssl est introuvable : installez-le (Ed25519 requis : OpenSSL 1.1.1 ou plus récent, ou LibreSSL 3.7+)." >&2; exit 1; }

priv="$dir/play-ticket.key"
pubpem="$dir/play-ticket.pub"
pubb64="$dir/play-ticket.pub.b64"

for f in "$priv" "$pubpem" "$pubb64"; do
  if [ -e "$f" ]; then
    echo "Refus : $f existe déjà. Rien n'a été écrit. Choisissez un autre dossier (ou archivez l'ancienne paire à la main : changer de clé invalide les tickets en cours)." >&2
    exit 1
  fi
done

umask 077
# le dossier n'est durci (0700) que s'il est CRÉÉ ici : un dossier existant garde ses droits (le script n'a pas à les changer)
if [ ! -d "$dir" ]; then mkdir -p -- "$dir"; chmod 700 "$dir"; fi

tmp="$(mktemp -d "$dir/.gen.XXXXXX")"
trap 'rm -rf "$tmp"' EXIT

openssl genpkey -algorithm ed25519 -out "$tmp/k.pem" >/dev/null 2>&1 || { echo "openssl ne sait pas générer une clé Ed25519 (version trop ancienne ?)." >&2; exit 1; }
openssl pkey -in "$tmp/k.pem" -pubout -out "$tmp/p.pem" >/dev/null 2>&1
openssl pkey -in "$tmp/k.pem" -pubout -outform DER -out "$tmp/p.der" >/dev/null 2>&1
openssl base64 -A -in "$tmp/p.der" > "$tmp/p.b64"
printf '\n' >> "$tmp/p.b64"

# fingerprint = SHA-256 of the SPKI DER, in hex
fp="$(openssl dgst -sha256 -r "$tmp/p.der" | cut -d' ' -f1)"

# move into place without overwriting: `ln` fails if the target appeared meanwhile ; on a race only the links THIS script created are removed
made=()
cleanup() { for f in "${made[@]:-}"; do [ -n "$f" ] && rm -f -- "$f"; done; }
place() { if ln -- "$1" "$2" 2>/dev/null; then made+=("$2"); else echo "Refus : $2 est apparu pendant la génération. Rien n'a été écrasé." >&2; cleanup; exit 1; fi; }
place "$tmp/k.pem" "$priv"
place "$tmp/p.pem" "$pubpem"
place "$tmp/p.b64" "$pubb64"
chmod 600 "$priv"
chmod 644 "$pubpem" "$pubb64"

echo "Clé publique (valeur de CASTBRIDGE_PLAY_TICKET_PUBKEY) : $pubb64"
echo "Empreinte (SHA-256 du SPKI) : $fp"
echo "La clé privée est dans le dossier indiqué (mode 600). Ne la copiez nulle part, ne la mettez jamais dans le dépôt : voir docs/PLAY-OPS-REQUIREMENTS.md, étape « Clé des tickets »."
