# w1-07 — CI utile et sans danger (Python, vecteurs, contenu, sshd ; release neutralisée)

**Vague 1 · Effort S (≈ 4 h) · Statut PRÊT.** Branche `claude/sonnet-w1-07`. Rapport : `docs/agent-reports/sonnet-w1-07.md`.

## Objectif
1. Un flux `tools.yml` exécute les 119 tests Python, le vérificateur de vecteurs et les contrôles de contenu.
2. `android.yml` exécute aussi `:sshd:test`.
3. `release.yml` ne **signe plus** et ne **publie plus** : build seule, manuelle, verrouillée.

## Pourquoi (preuves)
- `.github/workflows/release.yml:57` construit `:receiver:assembleRelease` **sans** `-PrequireActivation=true` et `:59-80` signe avec `KEYSTORE_BASE64` puis publie avec `CASTBRIDGE_ADMIN_TOKEN` : une exécution par tag publierait une TV **déverrouillée** ; contraire à `docs/HANDOFF.md:10` (« un agent cloud ne peut ni déployer, ni installer, ni signer »).
- `android.yml:12` : seulement `:core:test assembleDebug` ; `:sshd:test` (où vit `TvSshServerTest`) absent.
- Aucun flux ne lance : `tools/activation/verify_vectors.py` (dépend de `cryptography`, pas de `requirements`), `tools/tests`, `tools/trial-edition`, `tools/anim`, `tools/content-validation`, `tools/media-pipeline/tests`, `tools/quiz-bank` (python 3.12), `tools/content-budget`, `StarterBudget` (`gradle :core:checkStarterBudget`).
- Audit : OP-2, CI1-CI4, TE-2.

## Fichiers possédés
`.github/workflows/*.yml`, nouveau `tools/requirements-dev.txt`, `docs/COORDINATION.md` (ajouter un § « CI »). **Hors zone** : tout le code et les scripts testés.

## Étapes
1. `tools/requirements-dev.txt` : `cryptography>=42`.
2. `.github/workflows/tools.yml` (`on: push, pull_request, workflow_dispatch` ; `concurrency` par ref ; `timeout-minutes: 20` ; `fail-fast: false`) avec les jobs :
   - `python-tools` (ubuntu-24.04, python 3.12, `apt-get install -y ffmpeg`) : `python3 -m unittest discover -s tools/tests` ; `python3 -m unittest discover -s tools/trial-edition` ; `python3 -m unittest discover tools/anim` ; `cd tools/content-validation && python3 -m unittest -q test_cbvalidate` ; `cd tools/media-pipeline/tests && python3 -m unittest test_pipeline`.
   - `quiz-bank` (python 3.12, `paths: tools/quiz-bank/**, content/quiz/**`) : `python3 -m unittest discover -s tools/quiz-bank` ; `python3 tools/quiz-bank/quizbank.py check`.
   - `vectors` : `pip install -r tools/requirements-dev.txt` ; `python3 tools/activation/verify_vectors.py`.
   - `content-checks` (`paths: content/**, tools/content-budget/**, tools/trial-edition/**`) : `python3 tools/content-budget/content_budget.py --quiet` ; `python3 tools/content-budget/langues_budget.py` ; `python3 tools/trial-edition/trial_edition.py check --only learn,quiz` **si** l'option existe (vérifier `--help` ; sinon mettre `continue-on-error: true` et le noter : w2-13 corrige).
   Ne jamais utiliser `-s tools` (le script `tools/rental-test/rental_test.py` matche `*_test.py` sans être un test).
3. `android.yml` : ajouter `:sshd:test` et `:core:checkStarterBudget` ; publier `android/core/build/reports/tests` en artefact ; `timeout-minutes: 30`.
4. `release.yml` : `on: workflow_dispatch` seulement ; supprimer les étapes `apksigner` et de publication et toute référence aux secrets `KEYSTORE_*`, `CASTBRIDGE_ADMIN_TOKEN` ; construire avec `-PrequireActivation=true -PtrustedKeysFile=ci/trusted-keys.example.txt` **uniquement si** un tel fichier d'exemple (clé publique de test, pas de secret) existe — sinon construire `assembleRelease` non verrouillée mais nommer l'artefact `NON-VERROUILLEE-NE-PAS-DISTRIBUER`. Commentaire en tête : « La signature et la publication se font sur le Mac du propriétaire (docs/RELEASES.md) ».
5. `docs/COORDINATION.md` § CI : quels jobs, quels filtres de chemins, comment relancer.

## Critères d'acceptation
```sh
python3 -c "import yaml,glob;[yaml.safe_load(open(f)) for f in glob.glob('.github/workflows/*.yml')]"   # YAML valide (ou `yamllint` si installé)
grep -n 'apksigner\|KEYSTORE\|ADMIN_TOKEN' .github/workflows/release.yml   # 0 hit
grep -n 'sshd:test' .github/workflows/android.yml                           # 1 hit
# Localement, rejouer les commandes du job python-tools : toutes vertes
python3 -m unittest discover -s tools/tests && python3 -m unittest discover -s tools/trial-edition && python3 -m unittest discover tools/anim
pip install -r tools/requirements-dev.txt && python3 tools/activation/verify_vectors.py
```

## Cas limites
- `quizbank` prend ≈ 3 min : filtre de chemins obligatoire.
- `test_pipeline` saute 8 tests sans ffmpeg : installer ffmpeg dans le job.
- Si `verify_vectors.py` échoue localement sur les vecteurs **non commités** (`tools/activation/test-vectors.json` est modifié dans l'arbre) : le signaler dans le rapport, ne pas modifier les vecteurs (w1-10).

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, aucun secret dans les flux, pas de signature en CI ; ne pas modifier les scripts testés.

## Rapport
`STATUT`, liste des jobs et durées estimées, commandes rejouées localement, échecs rencontrés.
