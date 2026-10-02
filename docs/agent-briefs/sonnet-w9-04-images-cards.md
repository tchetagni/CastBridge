# w9-04 — Images : cartes programmées (Pillow), rendu des animations vectorielles, stub diffusion locale ; WebP

**Vague 9b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 1.2-1.3, § 3.2, § 4.3 ; contrats C1-C3. Branche `claude/sonnet-w9-04`. Rapport : `docs/agent-reports/sonnet-w9-04.md`.

## Objectif
`images.py` produit, pour chaque demande `kind: image` ou chaque `card` d'un storyboard, un WebP 960 × 540 (q75, ≤ 80 Ko carte, ≤ 120 Ko image) avec trois backends : **`card`** (Pillow : carte de vocabulaire — grand terme, lecture, glose fr/en, pictogramme vectoriel simple ; carte mémo Apprendre — titre, définition/formule, piège ; thème couleurs de la charte dans `templates/card-theme.json` ; polices Noto Sans / Noto Sans CJK OFL cherchées dans `~/.cache/castbridge-models/fonts/` puis `/System/Library/Fonts`, **jamais embarquées dans le dépôt**), **`anim`** (un bloc `illustration` JSON de `tools/anim/animlib` → PNG via un rasteriseur minimal maison des formes `rect, circle, line, path, text` ; **sans** navigateur ni cairo), **`flux`** (stub : interface C3, `available()` vrai seulement si `mflux` est importable **et** `~/.cache/castbridge-models/flux-schnell/LICENSE` existe ; dry-run imprime prompt haché, graine, pas ; aucun téléchargement). Provenance C2 par image (`origin: generated`, `generator`, `promptSha256`, `seed`).

## Pourquoi (preuves)
- `docs/MEDIA-POLICY.md:11` (WebP ≤ 120 Ko, ≤ 1 280 px), `:39` (`generated` ⇒ `generator`), `:78,84` (alt, pas de texte incrusté dans les images pour la dyslexie ⇒ **les cartes portent du texte rendu nettement, grand, sans empattement ; les scènes `flux` n'en portent aucun**), `:88` (personnages dessinés, jamais une personne identifiable).
- `tools/anim/animlib.py`, `tools/anim/templates.py` (formes des figures, taille ≤ 40 Ko), `content/langues/ja-a0-salut-fr/figures/*.json` (traits).
- Guide § 3.2 : FLUX.1 [schnell] Apache-2.0 retenu en option ; [dev] exclu ; D2 cartes par défaut, D8 dessin plat.
- Mac : Pillow 12.3 présent ; pas d'ImageMagick ; `ffmpeg -c:v libwebp` possible en secours de `PIL` WebP.

## Fichiers possédés
`tools/content-gen/images.py`, `tools/content-gen/backends/img_card.py`, `backends/img_anim.py`, `backends/img_flux.py`, `tools/content-gen/templates/card-theme.json`, `tools/content-gen/tests/test_images.py`.

## Étapes
1. `card-theme.json` : palette (fond clair, encre, accent ; contraste ≥ 4,5:1), marges 5 %, tailles : terme 120 px (CJK) / 96 px (latin), lecture 48 px, glose 40 px ; gabarits `vocab-card`, `memo-card`, `phrase-card`.
2. `img_card.py` : rendu Pillow déterministe (même spec ⇒ mêmes octets : désactiver l'anticrénelage aléatoire, fixer la police et sa version dans la provenance `model: "noto-sans-cjk@<version du fichier>"`) ; repli si police CJK absente : refus avec message « police Noto Sans CJK absente : télécharger (OFL) dans ~/.cache/castbridge-models/fonts/ » (jamais de carrés vides silencieux) ; pictogrammes : 12 formes vectorielles simples dessinées en code (maison, personne stylisée, soleil, main, nombre, flèche…) choisies par `spec.icon`.
3. `img_anim.py` : rasteriseur des formes `Figure.Shapes` (sous-ensemble : `rect`, `circle`, `line`, `path` M/L/Q/C/Z, `text`) à l'échelle 960/w ; dernière image (`reducedMotion: last-frame`) ou image à `t` ; suffisant pour les cartes de traits ; formes non gérées ⇒ `Unavailable` avec la liste.
4. `img_flux.py` : stub C3 ; `render(spec, out_png, dry_run)` : prompt construit depuis `spec.scene` + suffixe de style fixe (« flat illustration, no text, no watermark, no real person ») ; `promptSha256`, `seed = int(sha256(id)[:8],16)`, `steps 4`, `guidance 0` ; si `mflux` présent : appel local ; sinon `Unavailable`. **Ne pas** implémenter de filtre NSFW maison : exiger la **revue visuelle 100 %** (planche contact produite par `images.py --contact-sheet`).
5. `images.py` : lit storyboards et `requests*.jsonl`, écrit `work/cache/<fp>.png` puis `.webp` (PIL `quality=75, method=6` ; au-dessus du budget ⇒ réessayer q70, q65, puis refus) ; provenance ; `--contact-sheet work/contact.png` (grille 4 × N, légendes id) ; `--dry-run`.
6. Tests : carte latine rendue (si une police TTF quelconque est trouvable, sinon police bitmap de PIL **marquée non conforme** et test sauté), déterminisme (deux rendus identiques), budget (q décroissant), `anim` rend un exemple de `tools/anim/examples/*.json` sans erreur, `flux` indisponible ⇒ message, planche contact produite.

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_images.py'
python3 tools/content-gen/images.py --storyboards /tmp/sb --backend card --out /tmp/w --dry-run   # 0 ; liste des cartes et tailles estimées
python3 tools/content-gen/images.py --anim tools/anim/examples/<un>.json --out /tmp/w && python3 -c "from PIL import Image;im=Image.open('/tmp/w/cache/'+__import__('os').listdir('/tmp/w/cache')[0]);assert im.size==(960,540)"
grep -rn "requests\.\|urllib\|huggingface" tools/content-gen/backends/img_*.py   # 0
```

## Cas limites
Terme très long (phrase) ⇒ gabarit `phrase-card` avec retour à la ligne (≤ 2 lignes, taille réduite jusqu'à 64 px) ; furigana/pinyin avec diacritiques (ǎ ǚ) : vérifier que la police les couvre, sinon refus ; image > 120 Ko après q65 ⇒ refus ; `alt` fr+en obligatoire dans la provenance (copié de la glose ou de `spec.alt`).

## À ne pas faire
Aucune police dans le dépôt ; aucun modèle téléchargé ; aucun visage réaliste ni photo ; aucun texte dans les scènes `flux` ; ne pas toucher `tools/anim`.

## Rapport
`STATUT`, polices trouvées/manquantes, tailles moyennes des cartes, formes `animlib` non gérées, temps par carte.
