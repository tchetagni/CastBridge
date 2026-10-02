# w9-03 — Synthèse vocale `tts.py` à backends enfichables (locaux par défaut), cache par empreinte, Opus + secours AAC

**Vague 9b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 1.2, § 1.4, § 3.1 ; contrats C1-C3 de `SONNET-WAVE9-INDEX.md`. Branche `claude/sonnet-w9-03`. Rapport : `docs/agent-reports/sonnet-w9-03.md`.

## Objectif
Remplacer `tools/langues/tts_synth.py` (gardé intact) par `tools/content-gen/tts.py` : lit `work/requests*.jsonl`, synthétise chaque demande audio avec le backend choisi (`--backend kokoro|piper|espeak|say|google`, défaut `kokoro` puis repli `piper` puis `espeak` **si** `--fallback`), met en cache par `fingerprint`, encode en Opus (mot 16 kbit/s, phrase 24 kbit/s, 16 kHz mono, `loudnorm` deux passes, `silenceremove` tête/queue avec 80 ms conservés), produit aussi un secours `.m4a` AAC-LC 32 kbit/s si `--aac-fallback`, écrit l'enregistrement de provenance C2 (via `provenance.py write` si présent, sinon JSON direct conforme) ; `--dry-run` liste les demandes, voix, tailles estimées (3 Ko/s) et commandes `ffmpeg` sans rien écrire.

## Pourquoi (preuves)
- `tools/langues/tts_synth.py:11-20` (liste `FREE`, voix de départ Kokoro/Piper/espeak **à vérifier**), `:59` (ligne `ffmpeg` loudnorm → Opus) ; `tools/langues/gen_a0_pilot.py:26-27` (espeak `cmn`/`ja`) ; 66 `.opus` pilotes déjà produits ainsi.
- `docs/LANGUES.md:234-239` (politique d'encodage), `:289` (voix libres, voix NC exclues, jamais une voix clonée), `:359` (débit par niveau, 2 voix par langue).
- `tools/media-pipeline/templates/request-policy.json` « audio » (16/24 kbit/s, −16 LUFS, durées max) ; `docs/MEDIA-PIPELINE.md:38` (registre des voix : refus de clonage, voix personnalisée, audio de référence).
- TV : `android/receiver/.../LanguesActivity.kt:152` (`MediaPlayer`, fichier local) ⇒ secours `.m4a`.
- Mac du propriétaire : `espeak-ng` présent, `piper`/`kokoro` absents (à installer par lui), `say` présent mais **voix non redistribuables** (guide § 3.1).

## Fichiers possédés
`tools/content-gen/tts.py`, `tools/content-gen/backends/__init__.py`, `backends/tts_espeak.py`, `backends/tts_piper.py`, `backends/tts_kokoro.py`, `backends/tts_say.py`, `backends/tts_google.py`, `tools/content-gen/tests/test_tts.py`.

## Étapes
1. `backends/__init__.py` : découverte des modules `tts_*`, interface C3, exception `NetworkRefused`, `Unavailable(reason_fr)`.
2. `tts_espeak.py` : `espeak-ng -v <voix> -s <110 si speed<1 sinon 150> -w out.wav` ; voix par langue `cmn, ja, en-gb, de, fr, it, es` ; licence moteur `GPL-3.0`, `voiceLicense: "GPL-3.0 (moteur)"`.
3. `tts_piper.py` : `python3 -m piper -m <modèle.onnx> -f out.wav` (stdin = texte) ; modèles sous `~/.cache/castbridge-models/piper/<voice>/` avec `MODEL_CARD` ; **refus** si le dossier n'a pas de fichier de licence (`LICENSE*`/`MODEL_CARD*`) ; `available()` faux si le module manque.
4. `tts_kokoro.py` : import gardé de `kokoro.KPipeline` (+ `soundfile`) ; codes de langue `a/f/i/e/j/z` (en-US est `a`, en-GB `b` : utiliser **`b` si variety = en-GB**, D5) ; `speed` ; 24 kHz → rééchantillonné par `ffmpeg` ; **pas d'allemand** (`Unavailable("Kokoro n'a pas de voix allemande : utiliser piper")`).
5. `tts_say.py` : `say -v <voix> -r <mots/min> -o out.aiff` ; `REDISTRIBUTABLE = False` ; `tts.py` **refuse** `--backend say` sans `--prototype` et écrit alors les fichiers sous `work/proto/` seulement, provenance `redistributable: false`, jamais dans le cache des lots ; message : « voix Apple : prototypage interne, jamais redistribuée ».
6. `tts_google.py` : squelette qui lit `tools/media-pipeline/templates/registry/*.json` (voix `approved` seulement) et lève `NetworkRefused` sauf `allow_network=True` passé par `gen.py` (w9-11) ; **aucun appel réseau implémenté en W9** (le corps lève `NotImplementedError("chaîne Google : voir tools/media-pipeline")`).
7. `tts.py` : cache `work/cache/<fp>.wav|opus|m4a` ; sauter si `state.json` connaît l'empreinte ; `--voices` affiche les voix disponibles par backend et langue avec leur licence (lues dans `tools/content-gen/licenses.json` si présent, sinon table locale **marquée « à vérifier »**) ; genre `A→f`, `B→m` d'après `voiceClass` ; `speed` 0,85 pour `register: lent`, 1,0 sinon ; mesure `durationMs` sur le WAV ; `--limit N` ; `--only-lang zh`.
8. Tests (sans modèle) : dry-run sur un `requests.jsonl` de 3 lignes (sortie attendue, aucun fichier) ; backend `espeak` **si** `espeak-ng` et `ffmpeg` présents (sinon sauté avec raison) produit un `.opus` lisible par `ffprobe` (codec `opus`, 1 canal) et un enregistrement C2 valide (`provenance.py check` si présent) ; `say` refusé sans `--prototype` ; `google` lève `NetworkRefused` ; cache : deuxième exécution ne relance pas `ffmpeg` (compter les appels via un `ffmpeg` factice dans `PATH`).

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_tts.py'
python3 tools/content-gen/tts.py --requests tools/content-gen/tests/fixtures/requests-3.jsonl --backend kokoro --dry-run   # 0 ; imprime « kokoro non disponible » si absent et le plan
python3 tools/content-gen/tts.py --requests … --backend say --out /tmp/w ; echo $?   # 2, message « prototypage »
grep -rn "urllib\|requests\.\|http" tools/content-gen/backends/tts_*.py | grep -v "NetworkRefused\|#"   # 0
```

## Cas limites
Texte vide ou > 400 caractères ⇒ `blocked` ; texte CJK avec ponctuation pleine chasse : passer tel quel (jamais NFKC avant TTS, la lecture pinyin n'est pas dite) ; Kokoro et chiffres (« 10 » en zh) : convertir via le champ `reading` **si** la demande le porte, sinon `blocked` ; `ffmpeg` sans `libopus` ⇒ message clair et code 2.

## À ne pas faire
Aucun téléchargement de modèle ; aucun clonage ni audio de référence (option refusée si présente dans la demande) ; ne pas modifier `tools/langues/tts_synth.py` ; aucun nom de voix dans les messages d'erreur publiés (ids seulement).

## Rapport
`STATUT`, backends testés réellement (espeak seul en cloud), format exact de la ligne `ffmpeg`, temps mesurés sur 10 pistes espeak, ce qui reste à mesurer sur le Mac (Kokoro, Piper).
