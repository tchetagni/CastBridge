#!/usr/bin/env bash
# Vérification EN LECTURE SEULE du serveur CastBridge pour la première expérience (docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md, § 6).
#
#   bash ops/first-run/check-server.sh [adresse]          # défaut : https://bridge.sti-cm.com
#   CB_ADMIN_TOKEN=... bash ops/first-run/check-server.sh # ajoute les contrôles qui demandent le jeton d'administration
#
# - Uniquement des requêtes GET : rien n'est écrit sur le serveur, aucune base n'est touchée.
# - Le jeton n'est lu que dans la variable d'environnement CB_ADMIN_TOKEN, jamais en argument, jamais affiché
#   (il est passé à curl par son entrée standard, donc absent de la liste des processus).
# - Code de sortie : 0 si tous les contrôles OBLIGATOIRES sont bons, 1 sinon (les contrôles « facultatif » ne comptent pas).
set -u

BASE="${1:-${CB_SERVER_URL:-https://bridge.sti-cm.com}}"
BASE="${BASE%/}"
TOKEN="${CB_ADMIN_TOKEN:-}"
fails=0      # contrôles obligatoires en échec
skipped=0    # contrôles ignorés (pas de jeton)
optional_ko=0

command -v curl >/dev/null 2>&1 || { echo "curl est introuvable : installez-le puis relancez."; exit 2; }

ok()    { printf '  OK        %s\n' "$*"; }
ko()    { printf '  KO        %s\n' "$*"; fails=$((fails + 1)); }
okopt() { printf '  OK        %s (facultatif)\n' "$*"; }
koopt() { printf '  KO        %s (facultatif, sans effet sur le résultat)\n' "$*"; optional_ko=$((optional_ko + 1)); }
skip()  { printf '  ignoré (pas de jeton)  %s\n' "$*"; skipped=$((skipped + 1)); }
info()  { printf '  INFO      %s\n' "$*"; }

BODY_FILE="$(mktemp "${TMPDIR:-/tmp}/cb-check.XXXXXX")" || exit 2
trap 'rm -f "$BODY_FILE"' EXIT

# get <chemin> [avec-jeton] : écrit le corps dans $BODY_FILE, renvoie le code HTTP (000 = injoignable).
get() {
    local path="$1" auth="${2:-}"
    : > "$BODY_FILE"
    if [ "$auth" = "token" ]; then
        printf 'header = "Authorization: Bearer %s"\n' "$TOKEN" \
            | curl -sS -K - -o "$BODY_FILE" -w '%{http_code}' --max-time 20 "$BASE$path" 2>/dev/null || printf '000'
    else
        curl -sS -o "$BODY_FILE" -w '%{http_code}' --max-time 20 "$BASE$path" 2>/dev/null || printf '000'
    fi
}

# json_field <champ> : valeur d'un champ texte simple du dernier corps (jq si présent, sinon sed).
json_field() {
    if command -v jq >/dev/null 2>&1; then
        jq -r --arg k "$1" '.[$k] // empty' "$BODY_FILE" 2>/dev/null
    else
        sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p" "$BODY_FILE" | head -n 1
    fi
}

echo "Serveur contrôlé : $BASE"
if [ -n "$TOKEN" ]; then echo "Jeton d'administration : présent (jamais affiché)"; else echo "Jeton d'administration : absent (CB_ADMIN_TOKEN non défini) : contrôles protégés ignorés"; fi
echo

echo "1. Le serveur répond"
code="$(get /api/v1/updates/public-key)"
if [ "$code" = "200" ] && grep -q '"publicKey"' "$BODY_FILE"; then
    ok "clé publique des mises à jour (200) : le serveur et son HTTPS répondent"
elif [ "$code" = "000" ]; then
    ko "serveur injoignable (réseau, DNS ou certificat) : $BASE"
else
    ko "clé publique des mises à jour : code $code au lieu de 200"
fi
code="$(get /actuator/health)"
[ "$code" = "404" ] && ok "actuator non exposé à l'extérieur (404)" || ko "actuator exposé ou réponse inattendue : $code au lieu de 404"

echo
echo "2. Module licences (obligatoire pour émettre depuis le serveur)"
code="$(get /api/v1/admin/licenses/signing)"
case "$code" in
    401) ok "route de signature protégée : 401 sans jeton (la route existe et refuse les inconnus)" ;;
    404) ko "route /api/v1/admin/licenses/signing : 404 sans jeton (version trop ancienne ou module licences éteint, voir § 4 et § 3 du guide)" ;;
    *)   ko "route /api/v1/admin/licenses/signing sans jeton : code $code au lieu de 401" ;;
esac
if [ -n "$TOKEN" ]; then
    code="$(get /api/v1/admin/licenses/signing token)"
    if [ "$code" = "200" ]; then
        kid="$(json_field kid)"; pub="$(json_field publicKey)"
        if grep -q '"keyLoaded":true' "$BODY_FILE" && [ -n "$kid" ] && [ -n "$pub" ]; then
            ok "clé de signature des licences chargée sur le serveur"
            echo "            kid=$kid"
            echo "            publicKey=$pub"
            echo "            Ligne à ajouter aux clés de confiance de la TV (§ 2.4 du guide) :"
            echo "            kid=$kid pub=$pub scopes=ISSUE_TRIAL,ISSUE_PRODUCTION,REVOKE,REGISTRY,REACTIVATE,POLICY"
        else
            ko "module licences allumé mais aucune clé de signature chargée (fichier license-signing.key absent ou illisible)"
        fi
    elif [ "$code" = "401" ] || [ "$code" = "403" ]; then
        ko "jeton refusé par le serveur (code $code) : vérifiez CB_ADMIN_TOKEN"
    else
        ko "signature des licences avec jeton : code $code au lieu de 200"
    fi
    code="$(get /api/v1/admin/licenses/audit/verify token)"
    if [ "$code" = "200" ]; then ok "journal d'audit des licences vérifiable (200)"; else koopt "vérification du journal d'audit : code $code"; fi
else
    skip "clé de signature des licences chargée (kid, publicKey)"
    skip "vérification du journal d'audit des licences"
fi
code="$(get /api/v1/revocations)"
if [ "$code" = "200" ] && grep -q '^cbx1\.' "$BODY_FILE"; then
    ok "liste de révocation signée publiée (200, format cbx1)"
elif [ "$code" = "404" ]; then
    ko "GET /api/v1/revocations : 404 (version ancienne, ou CASTBRIDGE_LICENSES_PUBLIC_ROUTES=false, voir § 3 du guide)"
else
    ko "GET /api/v1/revocations : code $code au lieu de 200"
fi

echo
echo "3. Contenus libres (CC BY-SA)"
code="$(get /api/v1/free-content/info)"
if [ "$code" = "200" ] && grep -q '"available":true' "$BODY_FILE"; then
    ok "archive des contenus libres publiée : $(json_field sha256 | cut -c1-16)… (empreinte tronquée)"
elif [ "$code" = "404" ]; then
    ko "GET /api/v1/free-content/info : 404 (version ancienne, ou archive non déposée au chemin prévu, voir § 5 du guide)"
else
    ko "GET /api/v1/free-content/info : code $code au lieu de 200"
fi
if [ "$code" = "200" ]; then
    hcode="$(curl -sS -I -o /dev/null -w '%{http_code}' --max-time 20 "$BASE/api/v1/free-content" 2>/dev/null || printf '000')"
    [ "$hcode" = "200" ] && ok "téléchargement de l'archive possible (HEAD 200)" || ko "téléchargement de l'archive : HEAD code $hcode au lieu de 200"
fi

echo
echo "4. Catalogue des bouquets signé"
code="$(get /api/v1/catalog/bundles)"
if [ "$code" = "200" ]; then okopt "catalogue des bouquets publié (200)"
elif [ "$code" = "404" ]; then koopt "GET /api/v1/catalog/bundles : 404 (non publié, ou version ancienne : voir § 5.2 du guide)"
else koopt "GET /api/v1/catalog/bundles : code $code"; fi

echo
echo "5. Administration à distance (doit rester ÉTEINTE pour la première expérience)"
code="$(get /api/v1/tunnel/experts)"
if [ "$code" = "404" ]; then
    info "tunnel/experts : 404, attendu (module éteint ou liste non publiée ; le script ne peut pas distinguer les deux, voir § 4.4 du guide pour la vérification dans le conteneur)"
else
    info "tunnel/experts : code $code : le module d'assistance à distance semble ALLUMÉ ; il doit rester éteint pour la première expérience (§ 3)"
fi

echo
if [ "$fails" -eq 0 ]; then
    echo "Résultat : tous les contrôles obligatoires sont bons ($skipped ignoré(s) faute de jeton, $optional_ko facultatif(s) en échec)."
    exit 0
else
    echo "Résultat : $fails contrôle(s) obligatoire(s) en échec ($skipped ignoré(s) faute de jeton, $optional_ko facultatif(s) en échec)."
    exit 1
fi
