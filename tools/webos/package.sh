#!/usr/bin/env bash
# CastBridge-TV Web (LG webOS) : lance les tests, puis construit le paquet .ipk avec ares-package si le CLI webOS est installé ;
# sinon imprime la procédure d'installation du CLI et du déploiement sur la TV. Ne touche à aucune TV, ne publie rien.
#
#   bash tools/webos/package.sh
#
# Sortie : build/webos/com.sticm.castbridge.tv_<version>_all.ipk (build/ n'est pas suivi par git).
# Le paquet contient l'app (appinfo.json, index.html, app.css, app.js, icônes) et le service (service/), sans les tests.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP="$ROOT/webos/castbridge-tv-web"
OUT="$ROOT/build/webos"
ID="com.sticm.castbridge.tv"

echo "== Tests (node $(node -v)) =="
node "$APP/tests/run.js"

VERSION="$(node -e 'console.log(require(process.argv[1]).version)' "$APP/appinfo.json")"
IPK="$OUT/${ID}_${VERSION}_all.ipk"
REL="${IPK#"$ROOT"/}"        # chemin relatif à la racine du dépôt, pour les commandes imprimées

if ! command -v ares-package >/dev/null 2>&1; then
  cat <<EOF

== ares-package (CLI webOS) n'est pas installé : le paquet n'a pas été construit ==
Installation du CLI, puis déploiement sur une TV LG en mode développeur (docs/TV-WEB-WEBOS.md) :

  npm install -g @webos-tools/cli                # installe ares-package, ares-setup-device, ares-install, ares-launch, ares-inspect…

  # Sur la TV : installer l'application « Developer Mode » (LG Content Store), se connecter avec un compte LG développeur,
  # activer « Dev Mode Status » et « Key Server », noter l'adresse IP de la TV et la phrase secrète affichées.
  ares-setup-device                              # ajouter la TV : nom « tv », adresse IP, port 9922, utilisateur « prisoner »
  ares-novacom --device tv --getkey              # récupérer la clé de la TV (phrase secrète de Developer Mode)

  bash tools/webos/package.sh                    # construit $REL
  ares-install --device tv $REL
  ares-launch --device tv $ID

  # Déboguer (console Chrome) : app, puis service
  ares-inspect --device tv --app $ID --open
  ares-inspect --device tv --service $ID.service --open

Le mode développeur expire (session d'environ 50 heures à prolonger dans l'application Developer Mode) : voir docs/TV-WEB-WEBOS.md.
EOF
  exit 0
fi

echo
echo "== Paquet =="
STAGE="$OUT/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE/app" "$STAGE/service"
cp "$APP/appinfo.json" "$APP/index.html" "$APP/app.css" "$APP/app.js" "$APP/icon.png" "$APP/largeIcon.png" "$STAGE/app/"
cp -R "$APP/service/lib" "$STAGE/service/lib"
cp "$APP/service/service.js" "$APP/service/services.json" "$APP/service/package.json" "$STAGE/service/"
# --no-minify : le minifieur du CLI ne lit pas toujours le JavaScript récent, et le code reste lisible dans le journal de la TV
ares-package --no-minify -o "$OUT" "$STAGE/app" "$STAGE/service"
ls -l "$IPK"

cat <<EOF

Paquet prêt : $REL
  ares-install --device tv $REL
  ares-launch --device tv $ID
EOF
