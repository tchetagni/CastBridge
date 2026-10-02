# w9-08 — Validateur image et vidéo `qc_visual.py` (WebP, MP4, affiche, sous-titres, clignotement, lisibilité)

**Vague 9b · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT.** Conception : guide § 4.3 ; contrat C4. Branche `claude/sonnet-w9-08`. Rapport : `docs/agent-reports/sonnet-w9-08.md`.

## Objectif
`qc_visual.py <fichiers…> --out work/qc [--card] [--trial]` : **images** : WebP valide (en-tête `RIFF…WEBP`), ≤ 120 Ko (`--card` : ≤ 80 Ko), ≤ 1 280 px grand côté, 960 × 540 attendu pour une carte, aucun bloc EXIF/XMP (chunks `EXIF`, `XMP ` absents), `alt` fr+en présent dans la provenance jumelle si `--provenance DIR` ; **vidéos** : H.264 (`profile` Main ou Baseline, `level` ≤ 31), hauteur ≤ 480, ≤ 30 s, 25 i/s, piste audio AAC mono, `faststart` (`moov` avant `mdat`), taille ≤ 15 Mo (`--trial` : ≤ 1,5 Mo), affiche `.webp` présente ≤ 60 Ko, fichiers `.ass` et `.srt` jumeaux présents et non vides, aucune métadonnée (`title`, `comment`, `encoder` toléré) ; **clignotement** : extraire 1 image sur 5 (`ffmpeg -vf fps=5,scale=32:18,format=gray`), luminance moyenne par image, compter les inversions de sens d'amplitude > 20/255 par seconde ⇒ `flashHz` ; ≤ 3 ; **lisibilité** : hauteur de police lue dans le `.ass` (≥ 28 px pour 480p), ≤ 2 lignes par événement, ≤ 42 caractères latins / 20 CJK par ligne. Rapport C4 ; code 0/2 ; outil absent ⇒ `unverifiable`.

## Pourquoi (preuves)
- `docs/MEDIA-POLICY.md:11-13,46-48` (budgets, affiche, `flashHz` ≤ 3, hauteur ≤ 480) ; `docs/MEDIA-POLICY.md:81-83` (≤ 3 flashs/s, lisibilité à 3 m, textes ≥ 18 px de la grille 1280×720 ⇒ 28 px sur 480p) ; `tools/trial-edition/config.json:19-20`.
- `tools/media-pipeline/mp/checks.py` (image WebP ≤ 120 Ko sans EXIF/XMP, vidéo H.264 ≤ 480p ≤ 30 s) : mêmes seuils, à réutiliser ou réécrire sans le modifier.

## Fichiers possédés
`tools/content-gen/qc_visual.py`, `tools/content-gen/tests/test_qc_visual.py`.

## Étapes
1. Lecture binaire WebP (RIFF, chunks `VP8 `/`VP8L`/`VP8X`, dimensions depuis `VP8X` ou `VP8 `/`VP8L`) sans PIL ; PIL utilisé seulement s'il est présent pour confirmer.
2. `ffprobe -show_streams -show_format -of json` pour la vidéo ; `moov` : scanner les atomes de premier niveau.
3. Clignotement : `ffmpeg -i f -vf "fps=5,scale=32:18,format=gray" -f rawvideo -` ⇒ octets ; moyenne par image ; détection d'inversions.
4. Sous-titres : parser minimal `.ass` (`Style:` ⇒ `Fontsize`, `Dialogue:` ⇒ texte, `\N`) et `.srt`.
5. Tests (ffmpeg requis pour la vidéo, sinon sautés) : WebP conforme (généré par ffmpeg `-c:v libwebp`) ⇒ `ok` ; WebP 1 400 px ⇒ `reject` ; MP4 conforme de 3 s (lavfi `color` + `sine` AAC, `-movflags +faststart`) avec affiche et `.ass`/`.srt` ⇒ `ok` ; sans affiche ⇒ `reject` ; vidéo 720p ⇒ `reject` ; clip clignotant (`lavfi` alternant noir/blanc à 6 Hz) ⇒ `reject` (`flashHz` > 3) ; `.ass` à 20 px ⇒ `reject`.

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_qc_visual.py'
python3 tools/content-gen/qc_visual.py /tmp/w/cache/*.webp --card --out /tmp/qc ; echo $?
python3 tools/content-gen/qc_visual.py /tmp/w/cache/*.mp4 --trial --out /tmp/qc --summary
```

## Cas limites
WebP animé (`ANIM` chunk) ⇒ `reject` (jamais d'animation WebP) ; vidéo sans piste audio ⇒ `reject` (uniformité, w9-05 ajoute un silence) ; vidéo à 24 ou 30 i/s ⇒ `reject` (25 attendu) ; `.ass` avec police non précisée ⇒ avertissement, pas de rejet ; fichier > 2 Go ⇒ refus immédiat sans lecture.

## À ne pas faire
Aucune dépendance obligatoire ; pas d'OCR ; aucune modification des fichiers contrôlés ; ne pas toucher `tools/media-pipeline`.

## Rapport
`STATUT`, algorithme de `flashHz` (et sa limite : diaporama = 0), fixtures, temps par vidéo.
