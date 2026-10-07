#!/usr/bin/env bash
# Génère la paire Ed25519 DÉDIÉE aux résultats de parties misées (games-G2, W22 option B) avec openssl, dans le dossier que l'exploitant nomme.
#
#   tools/play/gen-result-keypair.sh <dossier>
#
# - clé PRIVÉE  <dossier>/play-result.key      (PKCS#8, PEM, chmod 600) : le SEUL secret du service castbridge-play (CASTBRIDGE_PLAY_RESULT_KEY_FILE) ; il ne signe que les résultats
#   de partie `cbr1`. JAMAIS dans le dépôt, JAMAIS affichée, JAMAIS dans .env.play (seulement le CHEMIN du fichier monté en secret Docker) ;
# - clé PUBLIQUE <dossier>/play-result.pub.b64 (une ligne : 32 octets bruts en Base64, 44 caractères) : la valeur de CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS de l'API (castbridge-api),
#   qui vérifie ainsi les résultats avant de régler ; et <dossier>/play-result.pub (PEM) pour archive.
# - n'écrase JAMAIS : si l'un des trois fichiers existe, le script s'arrête sans rien modifier (changer de clé = les résultats déjà signés ne sont plus acceptés par l'API tant que
#   l'ancienne clé publique n'est pas gardée en deuxième position : CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS accepte deux clés séparées par une virgule, pour une rotation sans coupure).
# - n'affiche que le CHEMIN de la clé publique et son empreinte (SHA-256 de ses 32 octets) : jamais la clé privée.
# Ne se connecte à rien : aucun réseau, aucun serveur. Exploitation : docs/PLAY-OPS.md § 4.2 ter.
set -euo pipefail

usage() { echo "Usage : $0 <dossier>   (le dossier est créé en 0700 s'il n'existe pas)" >&2; exit 2; }
[ $# -eq 1 ] || usage
dir="$1"
[ -n "$dir" ] || usage
case "$dir" in -*) dir="./$dir";; esac   # un nom commençant par « - » n'est jamais lu comme une option

command -v openssl >/dev/null 2>&1 || { echo "openssl est introuvable : installez-le (Ed25519 requis : OpenSSL 1.1.1 ou plus récent, ou LibreSSL 3.7+)." >&2; exit 1; }

priv="$dir/play-result.key"
pubpem="$dir/play-result.pub"
pubb64="$dir/play-result.pub.b64"

for f in "$priv" "$pubpem" "$pubb64"; do
  if [ -e "$f" ]; then
    echo "Refus : $f existe déjà. Rien n'a été écrit. Choisissez un autre dossier (ou archivez l'ancienne paire à la main : changer de clé rend inacceptables les résultats déjà signés si l'API n'a pas gardé l'ancienne clé publique)." >&2
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

# la clé publique BRUTE : les 32 derniers octets du SPKI (44 octets) ; c'est le format que lisent l'API (play-result-pubkeys) et les clés de confiance
der_size="$(wc -c < "$tmp/p.der" | tr -d ' ')"
[ "$der_size" -eq 44 ] || { echo "Clé publique inattendue (SPKI de $der_size octets au lieu de 44) : arrêt, rien n'a été écrit." >&2; exit 1; }
tail -c 32 "$tmp/p.der" > "$tmp/p.raw"
openssl base64 -A -in "$tmp/p.raw" > "$tmp/p.b64"
printf '\n' >> "$tmp/p.b64"

# fingerprint = SHA-256 of the 32 raw public bytes, in hex
fp="$(openssl dgst -sha256 -r "$tmp/p.raw" | cut -d' ' -f1)"

# move into place without overwriting: `ln` fails if the target appeared meanwhile ; on a race only the links THIS script created are removed
made=()
cleanup() { for f in "${made[@]:-}"; do [ -n "$f" ] && rm -f -- "$f"; done; }
place() { if ln -- "$1" "$2" 2>/dev/null; then made+=("$2"); else echo "Refus : $2 est apparu pendant la génération. Rien n'a été écrasé." >&2; cleanup; exit 1; fi; }
place "$tmp/k.pem" "$priv"
place "$tmp/p.pem" "$pubpem"
place "$tmp/p.b64" "$pubb64"
chmod 600 "$priv"
chmod 644 "$pubpem" "$pubb64"

echo "Clé publique (valeur de CASTBRIDGE_WALLET_PLAY_RESULT_PUBKEYS de l'API) : $pubb64"
echo "Empreinte (SHA-256 des 32 octets) : $fp"
echo "La clé privée est dans le dossier indiqué (mode 600) : elle se monte dans le conteneur castbridge-play en secret Docker (docs/PLAY-OPS.md § 4.2 ter). Ne la copiez nulle part, ne la mettez jamais dans le dépôt ni dans .env.play."
