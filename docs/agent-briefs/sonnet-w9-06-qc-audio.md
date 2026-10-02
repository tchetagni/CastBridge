# w9-06 — Validateur audio `qc_audio.py` (LUFS, crête, écrêtage, silences, bruit, en-tête Opus, durée, taille, métadonnées)

**Vague 9b · Effort S (≈ 0,5-1 j) · Modèle : haiku · Statut PRÊT.** Conception : guide § 4.2 ; contrat C4 de `SONNET-WAVE9-INDEX.md`. Branche `claude/sonnet-w9-06`. Rapport : `docs/agent-reports/sonnet-w9-06.md`.

## Objectif
`qc_audio.py <fichiers…> --out work/qc [--kind word|phrase|dialogue] [--trial]` écrit un rapport C4 par fichier et sort 0 si tout est `ok`, 2 sinon. Seuils fixes (tableau du guide § 4.2) : intensité intégrée −16 LUFS ± 1 ; crête vraie ≤ −1,5 dBTP ; écrêtage : aucun échantillon à ±1,0 (mesuré sur le WAV décodé) ; silence en tête ≤ 150 ms, en queue ≤ 250 ms, et ≥ 60 ms de silence avant la fin (pas de coupure de mot) ; bruit de fond sur les silences < −50 dBFS ; conteneur Ogg/Opus mono, fréquence d'entrée 16 kHz lue dans `OpusHead` (ffprobe annonce toujours 48 kHz pour Opus, voir `tools/media-pipeline/mp/checks.py`), débit 16 (mot) ou 24 (phrase) kbit/s ± 20 % ; durée mot ≤ 4 s, phrase ≤ 20 s, dialogue ≤ 120 s ; `--trial` : ≤ 60 s et ≤ 256 Kio ; taille ≤ 200 Ko / 30 s ; aucune métadonnée (`artist`, `date`, `comment`, `encoder` toléré) ; `.m4a` : AAC-LC mono, mêmes seuils d'intensité et de durée.

## Pourquoi (preuves)
- `docs/LANGUES.md:358` (bruit < −50 dBFS, pas de coupure de mot, −16 LUFS) ; `docs/MEDIA-POLICY.md:12` (≤ 200 Ko / 30 s) ; `tools/trial-edition/config.json:17-18` (audio d'essai).
- `tools/media-pipeline/mp/checks.py` : contrôles Opus existants (lire ; **réutiliser** la lecture d'`OpusHead` en l'important si l'interface le permet, sinon la réécrire en 20 lignes sans modifier ce fichier).
- Mesures : `ffmpeg -i f -af ebur128=peak=true -f null -` (I, LRA, true peak), `-af astats` (`Peak level`, `Flat factor`, `Noise floor`), `-af silencedetect=n=-50dB:d=0.05`.

## Fichiers possédés
`tools/content-gen/qc_audio.py`, `tools/content-gen/tests/test_qc_audio.py`.

## Étapes
1. Analyse `ffprobe -show_streams -show_format -of json` ; lecture binaire d'`OpusHead` (page Ogg 1 : `OpusHead`, canaux à l'octet 9, fréquence d'entrée aux octets 12-15 LE).
2. Décodage en WAV temporaire 16 bits puis `ebur128`, `astats`, `silencedetect` ; parsing des sorties stderr avec des expressions régulières tolérantes ; chaque mesure devient un `check` C4 avec `value`, `limit`, `ok`.
3. Outil absent (`ffmpeg`/`ffprobe`) ⇒ `status: unverifiable`, code 2, message « non vérifiable dans cet environnement » ; jamais `ok` par défaut.
4. `--summary` imprime un tableau (fichier, LUFS, TP, durée, Ko, statut).
5. Tests (ffmpeg requis, sinon sautés avec raison) : fabriquer avec `ffmpeg -f lavfi` (a) un `sine` normalisé à −16 LUFS 16 kHz mono Opus 16k 1,5 s ⇒ `ok` ; (b) le même à −6 LUFS ⇒ `reject` (loudness) ; (c) 2 s de silence en tête ⇒ `reject` (leadSilence) ; (d) 48 kHz stéréo ⇒ `reject` (OpusHead) ; (e) 6 s avec `--kind word` ⇒ `reject` (durée) ; (f) fichier avec `-metadata artist=x` ⇒ `reject` ; (g) `.m4a` conforme ⇒ `ok` ; ffmpeg factice absent du PATH ⇒ `unverifiable`.

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_qc_audio.py'
python3 tools/content-gen/qc_audio.py content/langues-media/zh-a0-salut/audio/*.opus --kind word --out /tmp/qc --summary   # sur le Mac : rapport (les pistes espeak peuvent être rejetées : c'est attendu, le rapport doit le dire)
python3 -c "import json,glob;r=[json.load(open(p)) for p in glob.glob('/tmp/qc/*.json')];assert all(x['status'] in ('ok','reject','unverifiable') for x in r)"
```

## Cas limites
Piste de 0,3 s (particule) : seuil de silence final adapté (≥ 30 ms) ; `silencedetect` sans aucun silence détecté ⇒ `leadSilence = 0` (ok) ; Opus à 48 kHz d'entrée (pistes humaines futures) ⇒ accepté si `--allow-48k` ; fichier vide ou illisible ⇒ `reject` avec raison.

## À ne pas faire
Aucune correction automatique (ne pas renormaliser : c'est le rôle de `tts.py`) ; aucune dépendance Python ; ne pas modifier `tools/media-pipeline`.

## Rapport
`STATUT`, expressions régulières utilisées pour `ebur128`/`astats` (versions de ffmpeg testées), résultats sur les fixtures, temps par fichier.
