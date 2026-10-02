# w9-15 — CI : tests de `tools/content-gen`, dry-run de `gen.py`, contrôle « zéro réseau »

**Vague 9d · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT (après w9-11).** Conception : règles 1-2 de `SONNET-WAVE9-INDEX.md` ; `docs/COORDINATION.md` § « Flux CI ». Branche `claude/sonnet-w9-15`. Rapport : `docs/agent-reports/sonnet-w9-15.md`.

## Objectif
Dans `.github/workflows/tools.yml`, job `python-tools` (Python 3.12 + FFmpeg déjà installés) : ajouter (1) `python3 -m unittest discover -s tools/content-gen/tests` ; (2) `python3 tools/content-gen/gen.py plan --langues content/langues --work /tmp/cg && python3 tools/content-gen/gen.py run --work /tmp/cg --dry-run --allow-missing` ; (3) un contrôle « zéro réseau » : `! grep -rnE "urllib\.request|requests\.(get|post)|http\.client|socket\.create_connection|huggingface_hub|snapshot_download" tools/content-gen --include='*.py' | grep -v "NetworkRefused\|# réseau interdit"` ; (4) `python3 tools/content-gen/licenses.py check` ; (5) un contrôle « aucun média lourd commis » : `! git ls-files tools/content-gen content | grep -E "\.(opus|m4a|mp4|wav)$" | grep -v "tests/fixtures/.*\.(opus|m4a|mp4)$"` (les fixtures de test de quelques Ko sont tolérées, plafond 50 Ko par fichier vérifié par un petit script inline). Mettre à jour `docs/COORDINATION.md` § CI (une ligne par contrôle ajouté).

## Pourquoi (preuves)
- `.github/workflows/tools.yml:26-30` (jobs existants : `tools/tests`, `trial-edition`, `anim`, `content-validation`, `media-pipeline/tests`) ; `docs/COORDINATION.md` § « Flux CI » (liste à tenir à jour).
- Règle de la vague : la CI n'exécute que des dry-run et des tests sur fichiers synthétiques ; aucun modèle, aucune API.

## Fichiers possédés
`.github/workflows/tools.yml` (lignes ajoutées au job `python-tools` et, si besoin, un filtre de chemins `tools/content-gen/**`), `docs/COORDINATION.md` (§ CI).

## Étapes
1. Ajouter les cinq étapes ; garder `timeout` 20 min ; `concurrency` inchangée.
2. Vérifier localement avec `act` si disponible, sinon exécuter chaque commande à la main dans le dépôt et coller les sorties dans le rapport.
3. Si w9-11 n'est pas fusionné, l'étape (2) doit être conditionnée : `if [ -f tools/content-gen/gen.py ]; then …; fi` (jamais un job rouge pour un script absent).

## Critères d'acceptation (hors ligne)
```sh
python3 -c "import yaml" 2>/dev/null && python3 -c "import yaml,sys;yaml.safe_load(open('.github/workflows/tools.yml'))" || echo "yaml non disponible : vérifier l'indentation à la main"
grep -n "content-gen" .github/workflows/tools.yml | wc -l   # ≥ 4
grep -n "content-gen" docs/COORDINATION.md | wc -l   # ≥ 1
```

## Cas limites
`ffmpeg` absent sur le runner ⇒ les tests concernés sont sautés avec raison (règle des cahiers 05-08), le job reste vert ; fixture de test > 50 Ko ⇒ échec explicite avec le nom du fichier.

## À ne pas faire
Ne pas toucher `android.yml`, `release.yml`, `backend.yml` ; aucun secret, aucun cache de modèles dans la CI ; ne pas lancer de téléchargement.

## Rapport
`STATUT`, extrait du YAML ajouté, sorties des cinq commandes exécutées localement.
