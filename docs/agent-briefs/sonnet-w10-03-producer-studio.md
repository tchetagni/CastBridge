# w10-03 — Studio producteur : `tools/producer-studio/studio.py` (inspecter, transcoder, aperçu, empreinte, emballer, vérifier, soumettre)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : PRÊT
> **Groupe : W10a-3** (vague W10a) · prérequis : aucun (format w10-01 par la conception § 3.2) · porte : `python3 -m unittest discover -s tools/tests -p 'test_producer_studio.py'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 10a · Effort L (≈ 3 j) · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W10 § 3.2, § 3.3, § 3.4, § 4.5. Branche `claude/sonnet-w10-03`. Rapport : `docs/agent-reports/sonnet-w10-03.md`. Dépend du format `work.json` de w10-01 (§ 3.2 : coder contre la spécification ; à la fusion, les lots d'exemple doivent passer `Work.validate`).

## Objectif
Un outil de bureau (Python 3.8+, bibliothèque standard + `ffmpeg`/`ffprobe` ; `fpcalc` optionnel) qui transforme le fichier brut d'un producteur en **deux lots** (`castbridge-lot-oeuvre-<id>-v<n>.lot` et `…-<id>-trial-v<n>.lot`) conformes au cadre des lots, avec `work.json`, couverture WebP et empreintes, puis prépare un **dossier de soumission** ; aucune clé, aucun réseau.

## Pourquoi (preuves)
- `tools/content-lots/build_lots.py` (en-tête) : format d'un lot (`lot.json`, ZIP déterministe) ; `tools/content-lib/lotlib.py` (`read_json`, `deflated`, `media_kind`) : à réutiliser.
- `tools/media-pipeline/mp.py` (`receive` : LUFS, silence, codecs, EXIF, « non vérifiable sans outil ») : réutiliser les fonctions de mesure (import ou copie minimale documentée).
- `docs/MEDIA-POLICY.md` § 1 : la règle « jamais d'audio/clip dans un lot TV ≤ 3 Mo » vise Apprendre ; les œuvres sont une autre fonction (DESIGN § 1).
- `C/transcode/Transcode.kt:4-9` : profils de diffusion (ne pas réutiliser pour le stockage).

## Fichiers possédés
Nouveaux `tools/producer-studio/studio.py`, `tools/producer-studio/presets.json`, `tools/producer-studio/README.md`, `tools/tests/test_producer_studio.py`. **Hors zone** : `tools/content-lots/**`, `tools/trial-edition/**` (w10-04), `tools/media-pipeline/**` (lecture seule), `content/**` (w10-04), tout code Android/serveur.

## Étapes
1. `presets.json` (DESIGN § 3.3) : `audio` (Opus 48 k mono voix / 64 k stéréo musique, 48 kHz, −16 LUFS, pic −1,5 dBTP, `silenceremove` tête/queue), `video360` (H.264 **Main 3.0**, 640×360, ≤ 400 kbit/s, 25 i/s, GOP 2 s, AAC-LC 64 k, MP4 `+faststart`), `video480`, `video720` (marqué « hors pilote : > 25 Mo probable »), `apercu` (hérite ; vidéo : `drawtext` « Aperçu CastBridge » bas centré ; audio : jingle généré ≤ 1,5 s en tête, `sine` 2 notes, −20 LUFS). Chaque preset = liste d'arguments ffmpeg **explicites** (aucune option devinée).
2. `inspecter <fichier>` : ffprobe JSON → codec, durée, résolution, canaux ; LUFS mesuré (`loudnorm` passe 1, comme `mp.py`) ; verdict « conforme / à transcoder (raisons) » ; sans ffprobe : « non vérifiable », code 4.
3. `transcoder <fichier> --preset P --out DIR [--mono|--stereo]` : deux passes `loudnorm` pour l'audio ; vidéo : `-profile:v main -level 3.0 -pix_fmt yuv420p` ; refuse une sortie > `MAX_WORK_BYTES` (25 Mo) avec conseil (« réduire à 360p / raccourcir »).
4. `apercu <fichier> --debut S --duree D(≤60) --marque TEXTE --out DIR` : extrait + marque ; refuse > 60 s ou > 2 Mo.
5. `empreinte <fichier>` : SHA-256 ; chromaprint via `fpcalc -json` si présent, sinon `null` + avertissement.
6. `emballer <dossier>` : lit `oeuvre.json` (saisie du producteur : titre, genre, langues, synopsis, classification proposée, crédits, licence, prix proposé, déclaration de droits) + `media.*` + `cover.*` (convertit en WebP ≤ 60 Ko, 480×480 max) → `work.json` (clés triées, `durationS` depuis ffprobe, empreintes) ; lot complet et lot d'aperçu (ZIP `lotlib`-style : horodatage fixe 2026-01-01, entrées triées : `work.json`, `media/<nom>`, `cover.webp`) ; `lot.json` au format de `build_lots.py` (feature `oeuvre`, scope, version, title, files sha256) ; version lue dans `--version N` (défaut 1). Déterministe : deux exécutions = mêmes octets (test).
7. `verifier <lot>` : relit tout (ZIP sain, un seul `work.json`, empreintes, tailles, classification ≠ adulte, aperçu ≤ 60 s ∧ ≤ 2 Mo, libre ⇒ crédits + pas d'aperçu, réservé ⇒ aperçu présent dans le dossier), sortie JSON + texte ; code 0/2.
8. `soumettre <dossier> --vers DEPOT` : copie les deux lots, `work.json`, `declaration-droits.*` (scan), `SOUMISSION.json` (empreintes, date, version de l'outil, `fpcalc` présent ?) ; refuse si `verifier` échoue ; **aucun envoi réseau**.
9. `README.md` : parcours « producteur apporte un fichier WhatsApp » en 6 commandes, limites (ffmpeg requis, qualité d'enregistrement, pas de musique tierce), rappel « aucune clé ici ».
10. Tests (`ffmpeg` requis pour ~6 tests, sautés sinon avec message) : sons/vidéos générés (`sine`, `testsrc`), presets appliqués, aperçu > 60 s refusé, déterminisme, `verifier` sur un lot altéré (code 2), adulte refusé, libre sans crédits refusé.

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/tests -p 'test_producer_studio.py'      # vert (tests ffmpeg sautés proprement sans ffmpeg)
python3 tools/producer-studio/studio.py --help                                 # 7 sous-commandes
grep -n 'key\|secret\|private' tools/producer-studio/studio.py | grep -v '#'    # aucun secret manipulé
```

## Cas limites
- Fichier avec métadonnées personnelles (GPS, auteur) : retirées (`-map_metadata -1`), signalé.
- Vidéo verticale (téléphone) : conservée en 360 de hauteur **ou** largeur (le plus petit côté = 360), barres noires jamais ajoutées.
- Audio déjà Opus conforme : `inspecter` dit « conforme » ; `transcoder` recopie en normalisant seulement si LUFS hors ±1 LU.

## À ne pas faire
Pas de commit sur les branches partagées ; aucun appel réseau ; aucune clé ; ne pas modifier `build_lots.py`, `lotlib.py`, `mp.py` ; pas de dépendance hors bibliothèque standard (ffmpeg/fpcalc = exécutables externes détectés) ; messages en français.

## Rapport
`STATUT`, presets exacts (arguments ffmpeg), tailles obtenues sur les médias de test, ce qui n'a pas pu être vérifié (voix réelles, TV), questions.
