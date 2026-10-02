# w9-11 — Orchestrateur `gen.py` : plan, exécution reprenable, dry-run, garde réseau, `config.json`, README

**Vague 9c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w9-01 à w9-10 ; stubs acceptés pour ceux qui manquent).** Conception : guide § 2 ; contrats C1-C6. Branche `claude/sonnet-w9-11`. Rapport : `docs/agent-reports/sonnet-w9-11.md`.

## Objectif
Un point d'entrée unique, **entièrement hors ligne par défaut** :
```
python3 tools/content-gen/gen.py plan   --langues content/langues --learn content/learn --select @liste --work work        # requests (mp.py) + storyboards + plan.json
python3 tools/content-gen/gen.py run    --work work --stages tts,images,assemble,qc,lots [--dry-run] [--limit N] [--only-lot zh-a0-salut]
python3 tools/content-gen/gen.py status --work work                                                                     # fait / à faire / rejeté / non vérifiable, par lot et par étape
python3 tools/content-gen/gen.py allow-network --backend google --max-cost 0 --pricing pricing.json                      # refuse tant que max-cost = 0 ou pricing vide
```
`plan` appelle `python3 tools/media-pipeline/mp.py requests` (existant) puis `scripts.py` ; ordonne par priorité (A0 → A2 d'abord, audio > image > vidéo, mots > phrases > exercices > dialogues > histoires, comme `mp.py`) ; `run` enchaîne les scripts de la vague **par `subprocess`** (chaque script reste autonome), reprend depuis `state.json` (empreinte faite = sautée), journalise dans `ledger.py`, s'arrête à la première erreur d'un script avec le message de celui-ci, et imprime en fin un résumé `status`. `--dry-run` propage `--dry-run` à chaque script et **ne crée que `work/plan.json` et `work/state.json`**. Garde réseau : `run` passe `allow_network=False` à tous les backends ; `allow-network` écrit `work/network.json` (`backend`, `maxCost`, `pricingSha256`, `grantedAt`) **seulement** si `max-cost > 0`, `pricing.json` existe et contient le tarif du backend (relecture via `tools/media-pipeline/mp/estimate.py` si importable, sinon vérification minimale), et ce fichier expire après 24 h.

## Pourquoi (preuves)
- `docs/MEDIA-PIPELINE.md:28,31-33` (priorités, plafonds, reprise par empreinte, codes de sortie 0/2/3) : mêmes conventions.
- Guide § 2.1 (principes), § 2.4 (exécution sur le Mac), § 6.1 (temps).
- `tools/trial-edition/trial_edition.py` (style de CLI Python du dépôt : `select`/`check`/`build`, messages en français, sans dépendance).

## Fichiers possédés
`tools/content-gen/gen.py`, `tools/content-gen/config.json`, `tools/content-gen/README.md`, `tools/content-gen/tests/test_gen.py`, `tools/content-gen/tests/__init__.py`, `.gitignore` (ajout d'une ligne `work/`).

## Étapes
1. `config.json` : valeurs C6 (audio, vidéo, image, lot, essai), chemins par défaut (`modelsDir: "~/.cache/castbridge-models"`, `fontsDir`), backends par défaut (`tts: ["kokoro","piper","espeak"]`, `images: "card"`, `fallback: false`), `network: {"allowed": false}`.
2. `gen.py` : sous-commandes ci-dessus ; `--stages` ordonnées et validées ; chaque étape = liste de commandes construite à partir du plan ; `state.json` mis à jour **après** chaque script réussi (lecture des provenance/qc produits) ; codes : 0 tout fait, 2 erreur, 3 plafond/limite atteint.
3. Stubs : si un script de la vague est absent (branche non fusionnée), l'étape est marquée `absent` et `run` continue avec `--allow-missing`, sinon s'arrête : message « script w9-0N manquant ».
4. `README.md` : installation sur le Mac (brew/pip des outils **optionnels** avec leurs licences, dossier des modèles, polices Noto), les 5 commandes, le flux complet d'un lot A0, la règle « zéro réseau », comment remplacer une voix synthétique par une humaine (`humanReplacement`), comment lire `status`.
5. Tests : `plan` sur `content/langues` (12 packs) produit un `plan.json` déterministe ; `run --dry-run` avec des scripts factices (créés dans un répertoire temporaire et injectés par `--scripts-dir`) n'écrit que les deux fichiers ; reprise : un `state.json` pré-rempli saute l'empreinte ; `allow-network` refuse `--max-cost 0`, refuse sans pricing, accepte avec pricing et expire après 24 h (horloge injectée) ; `run` sans `network.json` passe `--no-network` à chaque sous-commande (vérifier les arguments reçus par le script factice).

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests
python3 tools/content-gen/gen.py plan --langues content/langues --work /tmp/w && python3 tools/content-gen/gen.py run --work /tmp/w --dry-run --allow-missing ; echo $?   # 0 ; seuls plan.json et state.json existent sous /tmp/w
python3 tools/content-gen/gen.py allow-network --backend google --max-cost 0 --work /tmp/w ; echo $?   # 2 « aucune dépense sans plafond ni tarif »
grep -n "work/" .gitignore
```

## Cas limites
Plan vide (aucune sélection) ⇒ code 0 et message ; script qui sort 3 (plafond) ⇒ `run` s'arrête proprement, `status` montre le reste ; `work/` sur un disque plein ⇒ message clair ; exécution interrompue (Ctrl-C) ⇒ `state.json` cohérent (écriture atomique : fichier temporaire + `os.replace`).

## À ne pas faire
Ne jamais importer un backend réseau directement ; ne pas réimplémenter une étape (tout passe par les scripts) ; aucune valeur de prix dans `config.json` ; ne pas modifier `tools/media-pipeline`.

## Rapport
`STATUT`, arbre de `work/` après un `run --dry-run` complet, commandes exactes générées pour un lot, temps d'un `plan` sur le contenu actuel, scripts absents rencontrés.
