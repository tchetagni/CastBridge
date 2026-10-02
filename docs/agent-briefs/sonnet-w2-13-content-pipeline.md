# w2-13 — Pipeline de publication du contenu : un seul registre de versions, manifeste d'essai commité, échec au lieu d'avertissement

**Vague 2 · Effort M (≈ 1,5 j) · Statut PRÊT.** Branche `claude/sonnet-w2-13`. Rapport : `docs/agent-reports/sonnet-w2-13.md`.

## Objectif
1. `content/learn/lots.json` est la **seule** source des versions d'Apprendre ; `content/LOT-VERSIONS.json` (serveur) en est dérivé ou vérifié égal ; les clés de scope sont identiques.
2. `content/TRIAL-MANIFEST.json` existe, produit par `trial_edition.py select --only learn,quiz`, et `check` passe (option `--only` acceptée par `check`).
3. `tools/publish-content.sh` **échoue** (au lieu d'avertir) quand une étape est sautée (python < 3.12, catalogue non signé, registres divergents, rapport QA manquant) dès que `--go` est demandé.

## Pourquoi (preuves)
- Deux registres : `content/learn/lots.json` (clé `maternelle`, versions 1-7, écrit par `LearnTool lots --update`) vs `content/LOT-VERSIONS.json` (clé `learn:mat`, tout v1, écrit par `tools/content-lots/build_lots.py --bump`) : un pack peut être v6 côté TV et v1 côté serveur.
- `content/TRIAL-MANIFEST.json` absent ; `python3 tools/trial-edition/trial_edition.py check` sort 1 avec **55 cellules Langues vides** (7 langues × 8 niveaux moins zh/A0) ; la sélection Apprendre + Quiz seule donne 32,56 Mo / 100 (127 lots).
- `tools/publish-content.sh:31-37` : validation du quiz sautée « bruyamment » si python < 3.12 (cas normal sur macOS).
- `tools/content-lots/build_lots.py:124` protège le bump oublié ; `langue.json.version` reste manuel (LANGUES § 14).
- Audit : CO-3, D1-D7 de l'audit contenu.

## Fichiers possédés
`tools/trial-edition/**`, `tools/publish-content.sh`, `tools/content-lots/build_lots.py`, `tools/content-lots/make_release.py`, `tools/tests/test_content_tools.py`, `content/TRIAL-MANIFEST.json` (nouveau, généré), `content/LOT-VERSIONS.json`, `docs/CONTENT-PUBLISH.md`. **Hors zone** : `content/learn/**` (y compris `lots.json` : lecture seule), `LearnTool.kt` (w2-12), `cbvalidate.py` (w2-12), workflows (w1-07).

## Étapes
1. `build_lots.py` : lire les versions depuis `content/learn/lots.json` (mapper `learn:<scope>` ↔ `<scope>` avec une table d'alias explicite si `mat` ≠ `maternelle` ; préférer **renommer** les clés de `LOT-VERSIONS.json` vers les noms de `scopes.txt` et documenter) ; option `--check-registries` qui échoue si une version diverge ; `--bump` n'écrit plus que `LOT-VERSIONS.json` pour les fonctions sans registre propre (quiz, langues).
2. `trial_edition.py` : `check --only learn,quiz` (même filtre que `select`) ; `select --only learn,quiz` produit `content/TRIAL-MANIFEST.json` ; le manifeste porte le filtre utilisé pour que `check` sans `--only` explique l'écart au lieu d'échouer sèchement sur Langues (message : « Langues absentes : relancer avec --only learn,quiz ou livrer les lots Langues »).
3. `publish-content.sh` : variable `STRICT=1` implicite avec `--go` ; python < 3.12 ⇒ échec ; `--check-registries` ; présence d'un rapport `content/qa/<lot>.json` par lot avec `decision: pass` ⇒ **avertissement** v1 (erreur quand w3-04 aura produit les rapports) ; catalogue `"signed": true` exigé (déjà).
4. `make_release.py` : inclure l'empreinte de `TRIAL-MANIFEST.json` et des deux registres dans `CONTENT-RELEASE.json`.
5. Tests `tools/tests/test_content_tools.py` : divergence de registres détectée ; `check --only` ; `publish-content.sh --dry-run` sur un faux arbre (si le script le permet ; sinon tester les fonctions Python).
6. `docs/CONTENT-PUBLISH.md` : étapes mises à jour, « une seule source de versions », commande pour les bouquets (`sign-catalog` + dépôt) regroupée.

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/tests                                      # vert
python3 -m unittest discover -s tools/trial-edition                              # vert
python3 tools/trial-edition/trial_edition.py check --only learn,quiz             # 0, manifeste à jour
python3 tools/content-lots/build_lots.py --check-registries                      # 0
bash tools/publish-content.sh --dry-run 2>&1 | tail -5                           # aucune étape « sautée »
git diff --stat content/learn/                                                    # vide
```

## Cas limites
- Le manifeste est déterministe : deux `select` successifs donnent le même fichier (test existant).
- Ne pas committer de lots d'essai construits (`build --out`) : seulement le manifeste.
- `python3.12` absent sur la machine de l'agent : les tests du quiz ne sont pas concernés ici ; `publish-content.sh --dry-run` doit alors **échouer** proprement avec le message, et le rapport le dit.

## À ne pas faire
Pas de commit sur les branches partagées, pas de déploiement, pas de secret (la clé de signature des catalogues n'est jamais lue), pas de modification des packs.

## Rapport
`STATUT`, table d'alias des scopes, taille du manifeste (Mo, lots), sorties des commandes.
