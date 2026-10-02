# w9-09 — Colle vers les lots : `build_media_lots.py` (assets `ok` → dossiers de lots, `media.json`, fragment `MEDIA-MANIFEST.json`, budgets)

**Vague 9c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 1.1-1.3, § 2 (étape 8-9) ; contrats C1, C2, C4, C6. Branche `claude/sonnet-w9-09`. Rapport : `docs/agent-reports/sonnet-w9-09.md`.

## Objectif
Prendre les assets dont **tous** les rapports C4 sont `ok` et dont la provenance est valide, et les déposer sous `work/out/` dans la forme attendue par les constructeurs existants : **Langues** : `langues-media/<cible>-<niveau>-<thème>/{audio,img,video}/<fichier>` + `media.json` fusionné (`provenance.py merge-langues`) **dans une copie** du pack texte `langues/<scope>-<départ>/media.json` (les deux départs fr/en reçoivent la même liste : lot média partagé) ; **Apprendre** : `learn/<pack>/media/<fichier>` pour les petits WebP (cartes mémo) et `learn-media/<classe>-media/<fichier>` pour les MP4, plus un fragment `MEDIA-MANIFEST.fragment.json` (`provenance.py merge-manifest`, `lot: "learn:<classe>-media"`, `lessons` renseignés depuis le storyboard). Contrôles : taille d'un lot média ≤ `lot.maxBytes` (8 Mio en W9 ; refus avec la liste des plus gros fichiers et une suggestion de scission `<thème>1`/`<thème>2`), familles non mélangées (un asset `reserved` dans un lot Langues libre ⇒ refus), aucun asset `redistributable: false` ni `draft`, noms conformes (`[a-z0-9][a-z0-9._-]*`), ids `m:<id>` du pack **tous** couverts ou listés comme manquants. Puis **imprime** (sans exécuter) les commandes de construction : `cd android && gradle --offline :core:buildLangLots`, `python3 tools/content-lots/build_lots.py --out DIR`, `python3 tools/content-media/check_media.py`, `python3 tools/content-budget/langues_budget.py --check DIR`, `python3 tools/trial-edition/trial_edition.py select`. `--apply` copie `work/out/` vers un dépôt `castbridge-content` donné (`--content-repo PATH`), jamais vers `content/` du dépôt de code pour les médias lourds.

## Pourquoi (preuves)
- `docs/LANGUES.md:194-203,449-452` (lot texte vs média, scope partagé, `buildLangLots`) ; `content/langues/ja-a0-salut-fr/media.json` + `content/langues-media/ja-a0-salut/audio/` (disposition réelle) ; `.gitignore:31-32` (médias hors dépôt).
- `docs/MEDIA-POLICY.md:7-15,20-22` (lot média `<classe>-media`, un média = un lot, `media: [ids]` sur la fiche) ; `content/graph/scopes.json` (scopes `kind: media`, ex. `mat-media`) ; `android/core/.../learn/Packs.kt:20` (« Videos are never inside a pack »).
- `tools/content-lots/build_lots.py` (lots depuis `MEDIA-MANIFEST.json` ; **ne pas modifier**) ; `tools/content-lib/lotlib.py:11-14` (budgets) ; `docs/LOTS.md:58` (serveur ≤ 10 Mo).

## Fichiers possédés
`tools/content-gen/build_media_lots.py`, `tools/content-gen/tests/test_build_media_lots.py`.

## Étapes
1. Inventaire : `work/provenance/*.json` × `work/qc/*.json` ⇒ assets admissibles ; refus motivés listés dans `work/out/REJETS.md`.
2. Routage : `family` + `source` ⇒ lot cible ; nom de fichier final depuis `id` (`audio/<id>.opus`, `img/<id>.webp`, `video/<id>.mp4` + `.webp` affiche + `.srt`) ; copie **avec vérification SHA-256** après copie.
3. Fusions de manifestes via `provenance.py` (import en module) ; `media.json` des deux départs mis à jour à l'identique ; `bytes`/`durationMs` recalculés.
4. Budgets et contrôles ci-dessus ; `--max-lot-bytes` surchargeable ; résumé par lot (fichiers, Mo, synthétiques, à remplacer).
5. `--dry-run` : tout sauf la copie ; `--apply --content-repo PATH` : copie puis `git status --porcelain` du dépôt cible imprimé (jamais de commit).
6. Tests : arbre temporaire avec 2 assets `ok`, 1 `reject`, 1 `draft`, 1 `reserved` mal routé ⇒ sorties attendues ; dépassement de 8 Mio avec des fichiers factices (`truncate`) ⇒ refus et suggestion ; `media.json` fr et en identiques ; fragment manifeste accepté par `check_media.py` sur une arborescence de test (si ffmpeg présent pour fabriquer un WebP, sinon sauté).

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_build_media_lots.py'
python3 tools/content-gen/build_media_lots.py --work /tmp/w --out /tmp/w/out --dry-run   # 0 ; résumé par lot ; commandes de construction imprimées, non exécutées
git -C "$(git rev-parse --show-toplevel)" status --porcelain content/ | wc -l   # 0 après exécution (rien n'écrit dans content/)
```

## Cas limites
Un pack texte référence `m:<id>` sans asset produit ⇒ listé « manquant », lot construit quand même (le texte reste utilisable sans média, `docs/LANGUES.md:201`) ; asset humain remplaçant un synthétique ⇒ exige `humanReplacement: done` et relève la version du lot média (`content/langues/lots.json` n'est **pas** modifié par cet outil : message « relancer buildLangLots -Pupdate ») ; deux assets avec le même `id` ⇒ refus.

## À ne pas faire
Ne pas exécuter gradle ni `build_lots.py` ; ne pas signer ; ne pas écrire dans `content/` ni `content/langues-media/` du dépôt de code ; ne pas modifier les constructeurs existants.

## Rapport
`STATUT`, disposition exacte produite, taille d'un lot A0 complet mesurée sur les fixtures, questions pour le coordinateur (versionnement des lots média, scope `learn-media` à ajouter dans `scopes.json` si absent).
