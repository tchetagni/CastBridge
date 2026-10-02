# w5-24 — CI et outillage : tests Python des bons et de la boutique dans `tools.yml`, vecteurs boutique/jetons, script de contrôle « clé publique du serveur dans les clés de confiance de la TV »

**Vague 5e · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT (après w5-05, w5-14, w5-22).** Conception : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 12 (risque n° 1), `docs/COORDINATION.md` § CI. Branche `claude/sonnet-w5-24`. Rapport : `docs/agent-reports/sonnet-w5-24.md`.

## Objectif
(1) `.github/workflows/tools.yml` : le job `python-tools` découvre aussi `tools/tests/test_make_vouchers.py`, `tools/tests/test_sign_prices.py`, `tools/shop-test` ; le job `vectors` rejoue `shop-vectors.json` et `tokens-vectors.json` (déjà fait par `verify_vectors.py` si w5-05 les a ajoutés : vérifier la sortie et **faire échouer** le job si une section est absente) ; un pas « aucun fichier `*-CODES-SECRET.csv` dans le dépôt » (`git ls-files | grep -c CODES-SECRET` = 0). (2) `tools/requirements-dev.txt` : rien de nouveau attendu (bibliothèque standard + `cryptography`) : vérifier. (3) `tools/release/check_server_key.sh` : lit `~/.castbridge-signing/activation-trusted-keys.txt` (ou `-f FICHIER`) et une clé publique serveur donnée (`-k <base64>` ou `GET <serveur>/api/v1/admin/licenses/signing` avec jeton : **optionnel**, pas en CI) ; imprime « présente / ABSENTE : les locations et bons de jetons émis par le serveur seront refusés par la TV (`UNKNOWN_KEY`) » ; code de sortie 0/1 ; documenté dans `docs/COORDINATION.md` § CI et référencé par `docs/RELEASES.md` (**lecture seule ici** : demander au coordinateur d'ajouter la ligne si w1-08/w3-10 possèdent le fichier).

## Fichiers possédés
`.github/workflows/tools.yml`, `tools/requirements-dev.txt`, nouveau `tools/release/check_server_key.sh`, `docs/COORDINATION.md` (§ CI seulement). **Hors zone** : `android.yml`, `release.yml`, `docs/RELEASES.md`, tout code.

## Critères d'acceptation
```sh
bash -n tools/release/check_server_key.sh && tools/release/check_server_key.sh -f /dev/null -k AAAA; echo "code=$? (attendu 1 : absente)"
python3 - <<'EOF'
import yaml,sys; d=yaml.safe_load(open('.github/workflows/tools.yml')); s=str(d); sys.exit(0 if 'make_vouchers' in s and 'shop-test' in s and 'CODES-SECRET' in s else 1)
EOF
grep -n 'check_server_key' docs/COORDINATION.md   # ≥ 1
```

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas appeler la production depuis la CI ; ne pas ajouter de secret ; ne pas toucher `release.yml`.

## Rapport
`STATUT`, jobs modifiés, ligne à ajouter dans `docs/RELEASES.md` (texte proposé).
