# w9-05 — Assembleur de shorts `assemble.py` (ffmpeg seul) : diaporama Ken Burns, audio, sous-titres incrustés, affiche

**Vague 9c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 1.2, § 1.4, § 3.3 ; contrats C1, C2, C5, C6. Branche `claude/sonnet-w9-05`. Rapport : `docs/agent-reports/sonnet-w9-05.md`.

## Objectif
À partir d'un storyboard C5 **validé** (`state: validated`, ou `--draft` ⇒ asset marqué `draft`) et des assets déjà en cache (cartes WebP/PNG de w9-04, pistes WAV/Opus de w9-03), produire `work/cache/<fp>.mp4` : 854 × 480, 25 i/s, H.264 profil main niveau 3.1, `yuv420p`, CRF 28, `-g 50`, `faststart`, piste **AAC-LC 48 kbit/s mono 44,1 kHz** (jamais Opus dans le MP4), sous-titres **incrustés** depuis un `.ass` généré (cible + départ, 2 lignes, ≥ 28 px sur 480p, marge 5 %), un `.srt` jumeau conservé (sidecar, transcription), une affiche WebP (image à t = 1 s, 640 px, ≤ 60 Ko), durée ≤ 30 s, cible ≤ 1,5 Mo (sinon réencodage CRF 30, puis 360p, puis refus), silence de 0,4 s entre scènes, fondu 0,3 s. Provenance C2 (`kind: video`, `subtitles`, `poster`, `transcript` = concaténation des `say.text`, `flashHz` estimé = 0 pour un diaporama). `--dry-run` imprime toutes les commandes ffmpeg.

## Pourquoi (preuves)
- Formats : guide § 1.4 ; `docs/MEDIA-POLICY.md:13,46` (clip 480p ≤ 15 Mo ≤ 30 s, affiche obligatoire, `caption`) ; `tools/trial-edition/config.json:19-20` (vidéo d'essai ≤ 30 s, ≤ 1,5 Mo).
- TV : `MediaPlayer` (`LanguesActivity.kt:152`), minSdk 26 ⇒ MP4/H.264/AAC ; 1 Go de RAM ⇒ niveau 3.1, pas de B-frames exotiques (`-tune stillimage` acceptable, `-x264-params ref=2`).
- `tools/media-pipeline/mp/checks.py` (contrôles vidéo existants : H.264, ≤ 480p, ≤ 30 s, métadonnées) : la sortie doit les passer.
- Remotion/MoviePy écartés (guide § 3.3) : **ffmpeg uniquement**.

## Fichiers possédés
`tools/content-gen/assemble.py`, `tools/content-gen/subs.py`, `tools/content-gen/tests/test_assemble.py`.

## Étapes
1. `subs.py` : génère `.ass` (style `Default` : police `Noto Sans`/`Noto Sans CJK` si disponible sinon `sans-serif`, taille 30, contour 2, ombre 0, `Alignment 2`, `MarginV 24`, deux événements par scène : ligne cible puis ligne départ en style `Secondary` 26 px, couleur distincte sans dépendre de la couleur seule : préfixe « › » sur la traduction) et `.srt` (mêmes temps, texte brut) ; découpe à 42 caractères latins / 20 CJK par ligne ; **jamais** plus de 2 lignes par événement (sinon scinder l'événement dans le temps).
2. `assemble.py` : par scène, `zoompan` (zoom 1,00 → 1,06 sur la durée, `fps=25`, `s=854x480`) sur la carte ; concat des scènes et de l'outro (carte texte rendue par w9-04 ou fond uni avec `drawtext` en secours) ; piste audio = concat des `say` (WAV du cache, `adelay`/`apad` pour caler chaque piste au `t` de sa scène, silence sinon) + `loudnorm` globale −16 LUFS ; filtre `subtitles=<ass>` si libass, sinon `drawtext` par événement (**à signaler dans la provenance : `subtitleRenderer`**) ; sortie puis `ffprobe` (codec, hauteur, durée, taille) ; réencodage dégressif ; affiche.
3. Tests (ffmpeg requis, sinon sautés avec raison) : construire un storyboard de 2 scènes avec des cartes générées par `ffmpeg -f lavfi color` et un ton `sine` ; vérifier `ffprobe` : `h264`, `aac`, 480, 25 i/s, durée = somme attendue ± 0,2 s, `faststart` (atome `moov` avant `mdat` : lire les 64 premiers Ko), affiche présente ≤ 60 Ko, `.ass` et `.srt` écrits, provenance C2 valide ; storyboard `review` refusé sans `--draft` ; dépassement de 30 s refusé ; dry-run n'écrit rien.

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_assemble.py'
python3 tools/content-gen/assemble.py --storyboard tools/content-gen/tests/fixtures/sb-2scenes.json --work /tmp/w --dry-run   # 0, commandes imprimées, aucun fichier
python3 tools/content-gen/assemble.py --storyboard … --work /tmp/w && ffprobe -v error -select_streams v -show_entries stream=codec_name,height,r_frame_rate -of csv=p=0 /tmp/w/cache/*.mp4   # h264,480,25/1
python3 tools/media-pipeline/mp.py --help >/dev/null   # inchangé (aucune modification de mp.py)
```

## Cas limites
Piste audio plus longue que sa scène ⇒ allonger la scène (jamais couper un mot) et recalculer ; sans aucune piste audio (storyboard muet) ⇒ piste de silence AAC pour garder un MP4 uniforme ; carte absente du cache ⇒ refus avec l'id manquant (jamais de carte noire) ; police CJK absente pour l'ASS ⇒ refus pour zh/ja (carrés vides interdits) ; `libass` absent ⇒ `drawtext` et avertissement.

## À ne pas faire
Pas de MoviePy/Remotion ; pas d'Opus dans le MP4 ; pas de sous-titres **seulement** sidecar (le TV ne lit pas de sidecar) ; aucune image ni musique tierce ; pas de mention commerciale dans l'outro.

## Rapport
`STATUT`, ligne ffmpeg finale, tailles obtenues pour 15 s et 30 s, présence de libass sur l'environnement de test, temps d'encodage.
