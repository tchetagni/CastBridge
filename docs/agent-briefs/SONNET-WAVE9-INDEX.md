# Vague 9 pour agents Sonnet/Haiku — index (2026-10-02) : chaîne de production de lots multimédias génératifs (`tools/content-gen/`)

Source : `docs/coordination/GUIDE-PRODUCTION-LOTS-GENERATIFS-2026-10-02.md` (lire en entier avant tout cahier ; les budgets et formats du § 1.4 et les champs de provenance du § 2.2 sont **le contrat**). Protocole commun : `docs/COORDINATION.md` (branche `claude/<id>` depuis `origin/integration/agents`, rapport `docs/agent-reports/<id>.md`, jamais `main`, le serveur ni un secret, français, « CastBridge » / « CastBridge-TV »).

**Demande du propriétaire (2026-10-02)** : produire des lots multimédias automatisés et génératifs (shorts, voix synthétiques, images) pour Langues et Apprendre, en phase de teasing, pour expérimenter les contenus locatifs et enrichir les contenus.

**Règles de la vague (s'appliquent à tous les cahiers)**
1. **Zéro réseau, zéro dépense par défaut** : aucun appel d'API, aucun téléchargement de modèle dans un cahier ; tout backend réseau refuse de s'exécuter sans `--allow-network` **et** un plafond **et** un tarif connu. La CI n'exécute que des `--dry-run` et des tests sur des fichiers synthétiques (`ffmpeg` fabrique les sons et images de test, comme `tools/media-pipeline/tests`).
2. **Python 3.8+, bibliothèque standard** ; `ffmpeg`/`ffprobe` par `subprocess` ; tout import optionnel (`kokoro`, `piper`, `mlx_whisper`, `pypinyin`, `PIL`) est **gardé** : absent ⇒ le backend répond « non disponible dans cet environnement », jamais une exception brute, jamais « conforme ».
3. **Déterminisme** : même entrée ⇒ mêmes octets des fichiers JSON (clés triées, `ensure_ascii=False`, `\n` final), horodatages uniquement dans les champs prévus (`producedAt`).
4. **Aucun média lourd dans le dépôt** : les sorties vont dans `work/` (ignoré) ou `castbridge-content` ; les tests écrivent dans un `tempfile.TemporaryDirectory()`.
5. **Marquage** : tout audio de moteur ⇒ `synthetic: true` ; toute image générée ⇒ `origin: "generated"` + `generator` ; familles `free` / `reserved` jamais mélangées ; messages d'erreur en français.
6. **Un cahier = ses fichiers** (matrice ci-dessous) ; les contrats partagés sont **dans cet index** et ne se modifient que par le coordinateur.

**Modèle d'exécution** : `haiku` = mécanique (validateurs à seuils fixes, rapports, CI, docs) ; `sonnet` = tout le reste ; `w9-14` (Android) est compilé par le propriétaire. Efforts en agent·jours (S ≈ 0,5-1, M ≈ 2, L ≈ 3-4).

## Contrats partagés

### C1. Disposition du dossier de travail (`work/`, ignoré par git)
```
work/requests.jsonl            sortie de `python3 tools/media-pipeline/mp.py requests content/langues work/requests.jsonl` (EXISTANT : une ligne par média, champs id, kind, lang, payload, voiceClass, constraints, outputPath, source, priority, fingerprint)
work/requests-learn.jsonl      idem pour Apprendre (w9-02 : shorts et cartes mémo), même schéma de ligne
work/storyboards/<id>.json     storyboards des shorts (w9-02)
work/plan.json                 plan d'exécution (w9-11)
work/state.json                reprise : {"done": {"<fingerprint>": {"path", "sha256", "stage"}}, "spent": {...}}
work/cache/<fingerprint>.<ext> assets produits (wav intermédiaires, opus, webp, mp4, ass, srt)
work/provenance/<fingerprint>.json   enregistrement de provenance (C2)
work/qc/<fingerprint>.json     rapport de contrôle (C4)
work/out/<lot>/...             dossiers de lots prêts (w9-09) : Langues `langues-media/<scope>/{audio,img,video}/` ; Apprendre `learn/<pack>/media/` + fragment de MEDIA-MANIFEST
work/ledger.csv                grand livre des coûts et temps (w9-10)
```

### C2. Enregistrement de provenance (`work/provenance/<fingerprint>.json`, schéma w9-01)
Objet JSON, clés triées : `id` (`m:<id>` sans préfixe), `kind` (`audio|image|video`), `lang`, `family` (`free|reserved`), `licence` (`CC-BY-SA-4.0|CASTBRIDGE-ORIGINAL|CC0|PD|CC-BY-4.0|CC-BY-SA-3.0`), `requestFingerprint`, `generator` (`tools/content-gen/<script>.py@<commit ou "worktree">`), `backend`, `model`, `modelVersion`, `modelSha256` (ou `null`), `voiceId`, `voiceLicense`, `voiceLicenseUrl`, `licenseVerifiedAt` (`AAAA-MM-JJ` ou `null`), `promptSha256` (ou `null`), `seed`, `steps`, `guidance` (ou `null`), `producedAt` (ISO 8601 avec fuseau), `host`, `synthetic` (bool), `humanReplacement` (`pending|done|n/a`), `replacedSyntheticSha256` (ou `null`), `redistributable` (bool : `false` pour `say`), `sha256`, `bytes`, `durationMs` (audio/vidéo) ou `width`/`height` (image), `transcript` (audio/vidéo : texte exact dit), `subtitles` (vidéo : chemins `.ass`/`.srt`), `poster` (vidéo), `alt` (`{fr,en}` image/vidéo), `qc` (copie de `status` et des valeurs clés de C4), `clonage_vocal: false`, `imitation_personne_reelle: false`, `depictsPerson: false`.

### C3. Interfaces des backends (modules `tools/content-gen/backends/`)
```python
# TTS : chaque module expose
NAME = "kokoro"                      # kokoro | piper | espeak | say | google
REDISTRIBUTABLE = True               # False pour say
def available() -> tuple[bool, str]  # (dispo ?, raison en français)
def voices(lang: str) -> list[dict]  # [{"voiceId","gender","licence","licenseUrl"}] lus dans tools/content-gen/licenses.json (w9-13)
def synthesize(text: str, lang: str, voice_id: str, speed: float, out_wav: str, *, dry_run: bool) -> dict  # {"model","modelVersion","sampleRate","ms"}
# Images :
NAME = "card"                        # card | anim | flux
def render(spec: dict, out_png: str, *, dry_run: bool) -> dict  # spec = {"kind":"vocab-card"|"memo-card"|"scene", ...} ; retour {"model","seed","promptSha256",...}
```
Un backend réseau (`google`) implémente la même interface et lève `NetworkRefused("réseau désactivé : --allow-network, --max-cost et pricing.json requis")` tant que `gen.py` ne l'a pas explicitement autorisé.

### C4. Rapport de contrôle (`work/qc/<fingerprint>.json`)
`{"asset": "<chemin>", "kind": "audio|image|video|text", "status": "ok|reject|unverifiable", "tool": "qc_audio.py", "checks": [{"name": "loudnessLufs", "value": -16.1, "limit": "[-17,-15]", "ok": true}, …], "messages": ["…en français…"]}`. Un asset entre dans un lot **seulement** si tous ses rapports sont `ok`. `unverifiable` est bloquant (outil absent), jamais silencieux.

### C5. Storyboard d'un short (`work/storyboards/<id>.json`, w9-02)
`{"format":1,"id":"zh-a0-salut-d1","family":"free","lot":"zh-a0-salut","source":{"pack":"zh-a0-salut-fr","unit":"zh-a0-salut-fr-u1","dialogue":"zh-a0-salut-fr-d1"},"state":"review|validated","aspect":"16:9","scenes":[{"t":0.0,"dur":4.0,"card":{"kind":"vocab-card","term":"你好","reading":"nǐ hǎo","gloss":{"fr":"bonjour","en":"hello"}},"say":{"lang":"zh","text":"你好！","voice":"A","speed":0.85},"sub":{"zh":"你好！","fr":"Bonjour !","en":"Hello!"}}],"outro":{"text":{"fr":"CastBridge · Chinois A0","en":"CastBridge · Chinese A0"},"dur":2.0},"draft":false}`

### C6. Paramètres techniques (`tools/content-gen/config.json`, w9-11 ; valeurs du guide § 1.4)
`audio.word` 16 kbit/s, `audio.phrase` 24 kbit/s, 16 kHz mono, `loudnorm I=-16 TP=-1.5 LRA=11`, silences ≤ 150/250 ms ; `video` 854×480, 25 i/s, H.264 main 3.1, CRF 28, AAC 48 kbit/s mono 44,1 kHz, ≤ 30 s, cible ≤ 1,5 Mo, plafond 15 Mo ; `image` WebP q75, 960×540, carte ≤ 80 Ko, image ≤ 120 Ko ; `lot.maxBytes` 8 Mio (W9) ; `trial` audio ≤ 60 s/256 Kio, vidéo ≤ 30 s/1,5 Mo.

## Les 16 cahiers

| id | Cahier | Objet | Effort | Modèle | Statut | Dépend de |
|---|---|---|---|---|---|---|
| w9-01 | `sonnet-w9-01-provenance-schema.md` | Schéma C2 + `provenance.py` (écrire, valider, fusionner vers `media.json` / `MEDIA-MANIFEST.json`) | S | sonnet | PRÊT | — |
| w9-02 | `sonnet-w9-02-scripts-storyboards.md` | `scripts.py` : storyboards C5 déterministes depuis `langue.json` et les fiches ; demandes Apprendre (`requests-learn.jsonl`) ; état `review` | M | sonnet | PRÊT | — |
| w9-03 | `sonnet-w9-03-tts-wrapper.md` | `tts.py` + backends `espeak`, `piper`, `kokoro`, `say` (non redistribuable), `google` (refus hors ligne) ; cache par empreinte ; Opus + secours `.m4a` | M | sonnet | PRÊT | w9-01 (schéma), w9-13 (voix) : sinon fichiers locaux de repli |
| w9-04 | `sonnet-w9-04-images-cards.md` | `images.py` + backends `card` (Pillow), `anim` (animlib → PNG), `flux` (stub local, hors ligne) ; WebP | M | sonnet | PRÊT | w9-01 |
| w9-05 | `sonnet-w9-05-ffmpeg-assembler.md` | `assemble.py` + `subs.py` : short MP4 480p depuis un storyboard, Ken Burns, ASS incrusté + SRT, affiche | M | sonnet | PRÊT | w9-02 (C5), w9-03, w9-04 (assets) ; testable seul avec des assets ffmpeg |
| w9-06 | `sonnet-w9-06-qc-audio.md` | `qc_audio.py` : LUFS, crête, écrêtage, silences, bruit, en-tête Opus, durée, taille, métadonnées | S | haiku | PRÊT | — |
| w9-07 | `sonnet-w9-07-qc-align-readings.md` | `qc_align.py` (whisper optionnel, CER) + `readings.py` (pinyin/kana vs `reading`, rétro-traduction en entrée) | M | sonnet | PRÊT | — |
| w9-08 | `sonnet-w9-08-qc-visual.md` | `qc_visual.py` : WebP/MP4/affiche/sous-titres/flashHz/lisibilité | S | haiku | PRÊT | — |
| w9-09 | `sonnet-w9-09-lot-builder-glue.md` | `build_media_lots.py` : assets `ok` → `work/out/<lot>/`, `media.json` (Langues) et fragment `MEDIA-MANIFEST.json` (Apprendre), tailles ≤ 8 Mio, appels documentés aux constructeurs existants | M | sonnet | PRÊT | w9-01, C4 |
| w9-10 | `sonnet-w9-10-ledger-report.md` | `ledger.py` (temps, coûts = 0 par défaut) + `report.py` (provenance par lot, crédits, synthétiques à remplacer) | S | haiku | PRÊT | w9-01 |
| w9-11 | `sonnet-w9-11-orchestrator-gen.md` | `gen.py` : `plan`/`run`/`status`, reprise, `--dry-run`, garde réseau, `config.json`, `README.md` | M | sonnet | PRÊT | tous les scripts (interfaces C1-C6 ; stubs acceptés) |
| w9-12 | `sonnet-w9-12-experiment-design.md` | `experiment.py` (matrice, affectation A/B intra-lot par hachage, carnet) + `docs/content-production/EXPERIENCE-TEASING-W9.md` + `CARNET-POINT-FOCAL-W9.md` | S | sonnet | PRÊT | — |
| w9-13 | `sonnet-w9-13-licenses-registry.md` | `licenses.json` (moteurs, modèles, voix, polices : licence, commercial, attribution, `verifiedAt`) + `licenses.py --check` + `docs/content-production/LICENCES-GENERATIF.md` | S | sonnet | PRÊT | — |
| w9-14 | `sonnet-w9-14-tv-media-twin-badge.md` | CastBridge-TV : consommateur des lots média (`langmedia`, `learn:<classe>-media`) vers le volume de la bibliothèque, lecture `.opus`/`.m4a`/`.mp4`, badge « voix de synthèse », journal local des lectures | L | sonnet | PRÊT (compilation : propriétaire) | w9-01 (champs lus) |
| w9-15 | `sonnet-w9-15-ci-tests.md` | `tools.yml` : tests `tools/content-gen/tests`, dry-run de `gen.py` ; `docs/COORDINATION.md` § CI | S | haiku | PRÊT | w9-11 |
| w9-16 | `sonnet-w9-16-docs-content-gen.md` | `docs/CONTENT-GEN.md`, entrées HANDOFF, renvois dans MEDIA-PIPELINE, LANGUES § 12, MEDIA-POLICY | S | haiku | PRÊT | w9-11, w9-13 |

## Ordre et dépendances

```
9a (parallèle, J1-J2) : w9-01  w9-13  w9-12
9b (parallèle, J3-J5) : w9-02  w9-03  w9-04  w9-06  w9-07  w9-08
9c (J6-J8)            : w9-05  w9-09  w9-10  w9-11
9d (J8-J10)           : w9-14 (Android, propriétaire)  w9-15  w9-16
```
Chaque script doit **fonctionner seul** (ligne de commande, `--dry-run`, tests) : w9-11 ne fait que les enchaîner.

## Matrice de propriété (fichiers disjoints)

Préfixe `G/` = `tools/content-gen/`, `GT/` = `tools/content-gen/tests/`, `GB/` = `tools/content-gen/backends/`, `DP/` = `docs/content-production/`, `C/` = `android/core/src/main/kotlin/castbridge/core/`, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`.

| id | Fichiers possédés |
|---|---|
| w9-01 | `G/provenance.py`, `G/schema/asset-manifest.schema.json`, `GT/test_provenance.py` |
| w9-02 | `G/scripts.py`, `G/templates/storyboard-dialogue.json`, `G/templates/storyboard-lesson.json`, `GT/test_scripts.py` |
| w9-03 | `G/tts.py`, `GB/__init__.py`, `GB/tts_espeak.py`, `GB/tts_piper.py`, `GB/tts_kokoro.py`, `GB/tts_say.py`, `GB/tts_google.py`, `GT/test_tts.py` |
| w9-04 | `G/images.py`, `GB/img_card.py`, `GB/img_anim.py`, `GB/img_flux.py`, `G/templates/card-theme.json`, `GT/test_images.py` |
| w9-05 | `G/assemble.py`, `G/subs.py`, `GT/test_assemble.py` |
| w9-06 | `G/qc_audio.py`, `GT/test_qc_audio.py` |
| w9-07 | `G/qc_align.py`, `G/readings.py`, `GT/test_qc_align.py` |
| w9-08 | `G/qc_visual.py`, `GT/test_qc_visual.py` |
| w9-09 | `G/build_media_lots.py`, `GT/test_build_media_lots.py` |
| w9-10 | `G/ledger.py`, `G/report.py`, `GT/test_report.py` |
| w9-11 | `G/gen.py`, `G/config.json`, `G/README.md`, `GT/test_gen.py`, `GT/__init__.py`, `.gitignore` (ligne `work/`) |
| w9-12 | `G/experiment.py`, `GT/test_experiment.py`, `DP/EXPERIENCE-TEASING-W9.md`, `DP/CARNET-POINT-FOCAL-W9.md` |
| w9-13 | `G/licenses.py`, `G/licenses.json`, `GT/test_licenses.py`, `DP/LICENCES-GENERATIF.md` |
| w9-14 | nouveaux `C/langues/LangMediaLotConsumer.kt`, `C/learn/LearnMediaLotConsumer.kt`, `CT` tests associés ; `R/LanguesHub.kt`, `R/LanguesActivity.kt` (badge, secours `.m4a`, journal) ; `R/LearnHub.kt` (enregistrement du consommateur média) ; **hors zone** : `C/lots/*`, `R/LotsHub.kt` (utiliser `LotsHub.register`), `R/PlayerActivity.kt` |
| w9-15 | `.github/workflows/tools.yml`, `docs/COORDINATION.md` (§ CI, lignes du job `python-tools`) |
| w9-16 | `docs/CONTENT-GEN.md`, `docs/HANDOFF.md` (entrée datée), `docs/MEDIA-PIPELINE.md` (un renvoi), `docs/LANGUES.md` § 12 (une ligne), `docs/MEDIA-POLICY.md` § 5 (une phrase) |

## Décisions du propriétaire attendues (guide § 9)
D1 chaîne de voix (recommandé : locale en W9), D2 images (cartes Pillow, FLUX en option), D3 mesure des shorts (locale en W9), **D4 prix (BLOQUÉ)**, D5 variétés (en-GB, es-ES), D6 w9-14 en W9, **D7 budget (0 en W9 ; plafond BLOQUÉ)**, D8 style (dessin plat), D9 voix (2 par langue, 0,85×), D10 sorties dans `castbridge-content`, D11 16:9 seul, **D12 relecteurs natifs (BLOQUÉ)**. Les cahiers sont écrits avec les recommandations ; une décision contraire change une constante de `config.json` ou de `licenses.json`.

## Routage des modèles (Fable, 2026-10-02) — vague 9

Aucun cahier `sonnet-w9-*.md` n'existait au moment du routage (index seul). Règle : `docs/coordination/ROUTAGE-AGENTS-EXECUTION-2026-10-02.md` § 7 — chaque cahier reçoit ses 8 critères, son modèle (la colonne « Modèle » de cet index est une proposition : `haiku` seulement si le cahier est écrit en forme mécanique, § 4.7), une entrée dans `docs/agent-briefs/routing.json` et l'en-tête de 3 lignes, avant tout lancement. Les scripts Python (`tools/content-gen/`) avec `unittest` sont de bons candidats Haiku dès lors que chaque fonction est spécifiée entrée/sortie.
