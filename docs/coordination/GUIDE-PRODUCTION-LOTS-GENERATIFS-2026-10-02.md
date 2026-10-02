# Guide de production de lots multimédias automatisés et génératifs (Langues et Apprendre) — phase de teasing, 2026-10-02

> **Demande du propriétaire (2026-10-02)** : « indique-moi comment produire des lots multimédias automatisés et génératifs (shorts, voix synthétiques, images, …) pour le module Langues et Apprendre, dans cette phase de teasing, pour expérimenter les contenus locatifs et enrichir les contenus en général ».
> **Statut : guide de conception, à valider par le propriétaire (décisions § 9).** Rien n'est produit ici ; aucun appel d'API payant ; aucune dépense réseau. Les cahiers d'exécution sont dans `docs/agent-briefs/SONNET-WAVE9-INDEX.md` (w9-01 … w9-16).
> **Rien de ce document n'est un avis juridique** : les licences citées sont celles connues au 2026-10-02, à revérifier au téléchargement (« date de vérification » dans le registre w9-13).
> Noms d'applications : **CastBridge** (téléphone) et **CastBridge-TV**.

## 0. En bref (25 lignes)

1. **Deux produits, deux familles** : **Langues** = contenu **libre CC BY-SA 4.0**, jamais chiffré, jamais loué (`tools/free-content/license-tags.json:3`, `android/core/.../lots/RentalPolicy.kt:25`) ; **Apprendre** = contenu **réservé**, chiffré par TV, **seul louable**. L'expérience de location se fait donc sur **Apprendre** ; Langues sert à enrichir (voix, cartes, shorts) et à alimenter l'archive libre.
2. **Pile recommandée (Mac M2 Max, 32 Go, vérifié)** : voix **Kokoro-82M** (Apache-2.0 ; en, fr, es, it, ja, zh) + **Piper** (MIT ; allemand `thorsten`, secours des autres) ; `espeak-ng` seulement comme secours de dernier recours (robotique) ; images **cartes vectorielles/Pillow** (déterministes, zéro IA) et, en option, **FLUX.1 [schnell]** local (Apache-2.0) via `mflux` ; vidéo **ffmpeg seul** (diaporama Ken Burns, sous-titres incrustés, affiche WebP) ; vérification **mlx-whisper** (déjà installé) ; **zéro API payante par défaut**.
3. **Chaîne** : données structurées (`langue.json`, `lessons/*.json`) → demandes déterministes avec empreinte (`tools/media-pipeline/mp.py requests`, existant) → scripts/storyboards → TTS → images → assemblage ffmpeg → normalisation −16 LUFS → contrôles automatiques → `media.json` / `MEDIA-MANIFEST.json` → lots (`gradle :core:buildLangLots`, `tools/content-lots/build_lots.py`) → catalogue signé hors dépôt.
4. **Provenance par fichier** : générateur, modèle et version, empreinte du prompt, graine, date, licence du moteur **et** de la voix, `synthetic: true`, `humanReplacement: pending|done` ; c'est ce qui permet le marquage « voix de synthèse » sur la TV et le remplacement ultérieur par des voix humaines sans toucher au texte.
5. **Pièges de licence** (§ 3) : **XTTS/CPML, F5-TTS, Fish-Speech, MMS-TTS (CC-BY-NC)** = non commerciaux, exclus ; **voix macOS `say`** = prototypage interne seulement, jamais redistribuées ; **FLUX.1 [dev]** non commercial, **[schnell]** oui ; **Remotion** payant au-delà de 3 personnes ; **Edge-TTS** non officiel ; Google/Azure/ElevenLabs : conditions à archiver, ElevenLabs commercial seulement en plan payant ; clonage de voix **interdit** ; aucun visage réel ; pas de texte de manuel.
6. **Première expérience (2 semaines)** : Langues **zh, ja, en** × **A0, A1** × {audio de vocabulaire, cartes illustrées, shorts de dialogue} = 18 cellules, un lot média par (langue, niveau) ; Apprendre **cm2, 3e, tle-cd** (classes d'examen, déjà dans le socle) × 1 mini-lot média de 5 shorts (≤ 8 Mo, louable 30 j, fenêtre d'essai 3 j / 720 min existante).
7. **Mesure sans nouveau serveur** : événements existants `learn` (`lesson_view`, `lesson_complete`, `exercise_result`), `content_stat` (par item : `shown`, `correct`, `ms`), `screen_time`, `library_stats` — tous de catégorie **« statistiques d'usage » (consentement)** ; A/B **intra-lot** (moitié des fiches avec short, moitié sans) ; lecture locale `GET /api/learn/events` sur les TV bêta ; conversion de location lue dans le registre des commandes (W5) ou le carnet du point focal.
8. **Ce que la TV ne sait pas encore faire** : recevoir le lot média jumeau (`LanguesHub.kt:15`) ni afficher le badge « voix de synthèse » (recommandation CO-5 de l'audit) ; cahier w9-14 (Android, à compiler par le propriétaire). Sans lui, l'expérience des médias se mesure **sur le téléphone** et sur la TV **via clé USB** (copie manuelle dans `filesDir` impossible sans ADB) : voir § 5.6.

## 1. Quoi produire, par produit, et à quels budgets

### 1.1 Contraintes vérifiées (source de vérité)

| Contrainte | Valeur | Source |
|---|---|---|
| Lot texte compatible TV | ≤ 3 Mo (figures et animations comprises) | `tools/content-lib/lotlib.py:11`, `content/langues/budget.json:6` |
| Lot média Langues | ≤ 100 Mo (téléphone) ; **jamais requis** | `content/langues/budget.json:7`, `docs/LANGUES.md:199-201` |
| Lot accepté par le serveur | ≤ 10 Mo (doit tenir sur la TV) ; relèvement à 100 Mo pour `langues-media` **non implémenté** | `docs/LOTS.md:58`, `docs/LANGUES.md:272-275` |
| TV : tous les lots Apprendre + Quiz | 10 Mo (`TV_MAX_BYTES`), socle compris | `android/core/.../lots/LotApi.kt:31` |
| Téléphone : tous les lots | 100 Mo (`PHONE_MAX_BYTES`) ; quota Langues décidé à **500 Mo** réglable (non implémenté) | `LotApi.kt:32`, `docs/LANGUES.md:439` |
| Enveloppe Langues | 6 144 Mo ; 7 langues (zh 17, ja 17, en 13, de 12, es 11, it 10, fr 11 %) ; média = audio 55 / vidéo 25 / image 12 / autre 8 % | `content/langues/budget.json:4-9,26` |
| Essai (100 Mo, toutes sous-catégories) | audio d'essai ≤ 60 s et ≤ 256 Kio ; vidéo d'essai ≤ 30 s et ≤ 1,5 Mo, une par langue et par niveau | `tools/trial-edition/config.json:4,17-20`, `docs/TRIAL-EDITION.md:65` |
| Politique médias (Apprendre) | figure ≤ 8 Ko, animation ≤ 40 Ko, image WebP ≤ 120 Ko et ≤ 1 280 px, audio Opus ≤ 200 Ko / 30 s, clip ≤ 15 Mo et ≤ 30 s, 480p, affiche obligatoire, `alt` fr+en, `caption`, `transcript` | `docs/MEDIA-POLICY.md:7-13,44-46` |
| Médias dans un pack Apprendre | `media/` accepte de petits webp/svg/ogg ; **« Videos are never inside a pack »** | `android/core/.../learn/Packs.kt:20` |
| Lecture audio sur la TV (Langues) | `android.media.MediaPlayer` sur un fichier `filesDir/lots/langues-media/<scope>/<file>` ; minSdk 26 | `LanguesActivity.kt:152`, `LanguesHub.kt:56`, `android/receiver/build.gradle.kts:14` |
| Location | lots **libres jamais louables** ; durée exacte fixée par bouquet, **30 j par défaut** ; fenêtre d'essai des lots **3 j / 720 min, une fois** ; plafond en ligne ≤ 60 j (W5) | `docs/RENTAL-LOTS.md:11,129,123`, `content/bundles-rental.json`, `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md:11` |
| Marquage synthétique | tout audio de moteur porte `engine` + `synthetic: true` + `voiceLicense` ; l'interface doit afficher « voix de synthèse » (**pas encore fait**) | `docs/LANGUES.md:247`, `RECOMMANDATIONS-FABLE-2026-10-02.md:81` |
| Médias lourds | hors du dépôt de code (`content/langues-media/` ignoré), vers `castbridge-content` | `.gitignore:31-32` |

**Points d'attention vérifiés** : (a) `MediaPlayer` lit l'Opus en conteneur Ogg à partir d'Android 5.0 et minSdk = 26, mais la TV de référence (GaiaOS 32 bits, 720p) **n'a pas été testée avec un `.opus`** : prévoir le secours **AAC-LC `.m4a` 32 kbit/s** et un test de 30 s sur la TV avant toute production de masse (w9-14, critère). (b) Pour la **vidéo**, le conteneur MP4 avec piste **AAC** est le seul choix sûr pour `MediaPlayer` (Opus-dans-MP4 n'est garanti qu'à partir d'Android 10). (c) Le lecteur de bibliothèque (libVLC 3.6.5, `android/receiver/build.gradle.kts:75`) lit tout, mais ce n'est pas lui qui joue les médias de leçon.

### 1.2 Langues (famille libre, CC BY-SA 4.0)

| Média | Pour quoi | Format cible | Budget unitaire | Nom de fichier (manifeste `media.json`) |
|---|---|---|---|---|
| **Audio de vocabulaire** (un mot, une expression) | carte SRS, dictée, `speak` | Opus mono 16 kHz **16 kbit/s**, −16 LUFS, silence ≤ 150 ms en tête/queue, 0,6–4 s | ≈ 1–8 Ko | `audio/<cible>-<slug>.opus` (ex. `audio/zh-nihao.opus`, id `m:zh-nihao`, comme aujourd'hui) |
| **Dialogues** (6–12 répliques, 2 voix) | compréhension orale | une piste par réplique (`audio/<cible>-<unité>-d1-l03.opus`, 24 kbit/s) **et** une piste d'ensemble (`…-d1.opus`) avec 400 ms de silence entre répliques | 10–30 s ≈ 30–90 Ko | id `m:<cible>-<unité>-d1` |
| **Exercices d'écoute** (dictée, vrai/faux audio, QCM audio) | `dictation`, `mcq` avec `audio` | mot ou phrase, 24 kbit/s | ≤ 20 s | `audio/<cible>-<unité>-x<nn>.opus` |
| **Prononciation** (paires minimales, tons) | § 2.2 de LANGUES | deux pistes de 1–2 s + une piste « lent » (vitesse 0,8) ; pour le chinois, une piste par ton | ≈ 2 Ko chacune | `audio/<cible>-mp-<slug>-{a,b,slow}.opus` |
| **Cartes illustrées** (vocabulaire) | `cards`, `image` du vocabulaire | **WebP q75 ≤ 80 Ko**, 960 × 540 (16:9, lisible à 3 m), **sans texte dans l'image** (le mot est rendu par l'app) ; style dessin plat, aucune personne réelle | 25–80 Ko | `img/<cible>-<slug>.webp` (id `m:img-<cible>-<slug>`) |
| **Micro-vidéos / shorts** (dialogue illustré, 15–30 s) | teasing, boutique « aperçu », unité | **MP4 H.264 profil main, 854 × 480, 25 i/s, CRF 28, AAC-LC 48 kbit/s mono**, sous-titres **incrustés** (cible + départ) ; affiche `poster` WebP ≤ 60 Ko | ≤ 30 s, cible **≤ 1,5 Mo** (plancher de l'essai), plafond 15 Mo | `video/<cible>-<unité>-d1.mp4` + `video/<cible>-<unité>-d1.webp` |
| **Animations de traits / articulation** | déjà vectorielles (≤ 40 Ko, TV) | inchangé (`tools/anim`) ; rendu MP4 **facultatif** pour les shorts | — | inchangé |

Un **lot média Langues** `<cible>-<niveau>-<thème>` (partagé fr/en) pour A0-A1 pèse typiquement : 12 mots × 2 Ko + 1 dialogue (90 Ko) + 10 exercices (60 Ko) + 12 cartes (600 Ko) + 1 short (1,5 Mo) ≈ **2,3 Mo**, soit, tant que le serveur plafonne à 10 Mo, **jusqu'à 3 shorts par lot** sans attendre le relèvement à 100 Mo. **Recommandation** : viser ≤ 8 Mo par lot média pendant le teasing (passe partout : serveur, TV, essai), et scinder (`salut1`, `salut2`) au-delà.

### 1.3 Apprendre (famille réservée, louable)

| Média | Pour quoi | Format | Budget | Où il vit |
|---|---|---|---|---|
| **Shorts explicatifs de fiche** (20–30 s : objectif, 1 exemple résolu, 1 piège) | aperçu de location, teasing | comme les shorts Langues (MP4 480p AAC, sous-titres incrustés, affiche) | ≤ 1,5 Mo | lot média `learn:<classe>-media` (scope `kind: media` de `content/graph/scopes.json`, ex. `mat-media`) ; **jamais dans le pack** |
| **Schémas** (figures vectorielles) | fiches | JSON `Figure` ≤ 8 Ko (existant) | — | pack (TV) |
| **Cartes mémo illustrées** (définition, formule, piège) | révision, quiz | WebP ≤ 120 Ko, 960 × 540, **texte rendu par Pillow** (pas par un modèle d'image) | 30–100 Ko | lot média |
| **Médias de quiz** (lecture d'énoncé, image d'énoncé) | Quiz / auto-évaluation | audio Opus 24 kbit/s ≤ 20 s ; image WebP | ≤ 60 Ko | lot média (le format Quiz n'a pas de champ média aujourd'hui : **ne pas** en produire en W9) |
| **Lecture à voix haute** d'une fiche (`readAloud`) | accessibilité | la TV lit déjà par la synthèse de l'appareil (`docs/LEARN.md:167`) : **ne pas** préproduire | — | — |

**Limite honnête** : il n'existe **aucun consommateur** de lot média Apprendre sur la TV ni sur le téléphone (le bloc `video` des fiches pointe vers un flux serveur `GET /api/v1/learn/videos`, `docs/LEARN.md:168,491`, incompatible avec une TV hors ligne). Le cahier **w9-14** apporte un consommateur TV minimal (lots `langmedia` **et** `learn:<classe>-media` dans le volume de la bibliothèque) ; sans lui, les shorts Apprendre ne sont visibles qu'en les copiant à la main (clé USB → lecteur de la bibliothèque), ce qui suffit pour un **test de teasing en magasin** mais pas pour une mesure fine.

### 1.4 Formats techniques retenus (résumé à copier dans les outils)

```
AUDIO (mot)      ffmpeg -i in.wav -ac 1 -ar 16000 -af "silenceremove=…,loudnorm=I=-16:TP=-1.5:LRA=11" -c:a libopus -b:a 16k -application voip out.opus
AUDIO (phrase)   idem, -b:a 24k ; secours TV : -c:a aac -b:a 32k out.m4a
VIDÉO (short)    ffmpeg … -vf "scale=854:480:force_original_aspect_ratio=decrease,pad=854:480:(ow-iw)/2:(oh-ih)/2,subtitles=sub.ass" \
                 -r 25 -c:v libx264 -profile:v main -level 3.1 -pix_fmt yuv420p -crf 28 -preset slow -g 50 -movflags +faststart \
                 -c:a aac -b:a 48k -ac 1 -ar 44100 out.mp4
AFFICHE          ffmpeg -ss 1 -i out.mp4 -frames:v 1 -vf scale=640:-2 -c:v libwebp -quality 70 poster.webp
IMAGE            cwebp -q 75 -resize 960 0 in.png -o out.webp   (ou ffmpeg -c:v libwebp -quality 75)
SOUS-TITRES      ASS (incrustés, police système, 2 lignes max, ≥ 28 px sur 480p) ; un .srt jumeau (sidecar) conservé pour le téléphone et la transcription
MESURE           ffmpeg -i f -af ebur128=peak=true -f null -   → I (LUFS), LRA, true peak ; ffprobe -show_streams (codec, height, fps, durée)
```

Durées : mot 0,6–4 s ; phrase ≤ 20 s ; dialogue ≤ 120 s ; short 15–30 s (plancher essai ≤ 30 s). Débit de parole : A0-A1 **lent** (vitesse 0,85), A2 intermédiaire, ≥ B1 naturel (`tools/media-pipeline/templates/request-policy.json` « register »).

## 2. Architecture de la chaîne (scriptable, reprenable, sur le Mac)

```
content/langues/<scope>/langue.json ─┐
content/learn/<pack>/lessons/*.json ─┼─► (1) mp.py requests  ─► media-requests.jsonl (empreinte SHA-256 par demande, déterministe ; EXISTANT)
                                      │                                   │
                                      │   (2) gen.py plan  ◄──────────────┘   plan.json : demandes × backend × priorité (A0 → A2 d'abord, audio > image > vidéo)
                                      │          │
                                      │   (3) scripts.py : storyboards des shorts (scènes, texte à dire, sous-titres, cartes) — brouillon LLM ou gabarit déterministe ; RELECTURE HUMAINE
                                      │          │
                                      │   (4) tts.py  ─────────────── backends : kokoro | piper | espeak | say(dev) | google(mp registry, réseau explicite)
                                      │   (5) images.py ───────────── backends : card (Pillow, déterministe) | anim (animlib → PNG) | flux-schnell (mflux, local) | none
                                      │   (6) assemble.py ─────────── ffmpeg : diaporama Ken Burns + audio + ASS incrusté + affiche
                                      │   (7) qc_audio.py · qc_align.py · qc_visual.py ── contrôles automatiques (échec = rejet, jamais « conforme » sans mesure)
                                      │   (8) build_media_lots.py ─── media.json (Langues) / MEDIA-MANIFEST.json (Apprendre) + dossiers de lot
                                      │   (9) gradle :core:buildLangLots · tools/content-lots/build_lots.py ─► lots .lot ; catalogue signé HORS dépôt (clé du propriétaire)
                                      └── manifeste de provenance par asset (w9-01) + grand livre des coûts (w9-10) + rapport de provenance / crédits
```

### 2.1 Principes non négociables

1. **Source de vérité = les JSON de contenu.** Aucun texte n'est tapé dans un outil de génération : tout vient de `langue.json` (`term`, `reading`, `lines[].text`, `tr`) ou des fiches (`objectives`, `blocks[].md`, `example.steps`). Un texte modifié = nouvelle empreinte = nouvel asset (règle de `mp.py requests`, `docs/MEDIA-PIPELINE.md:24-26`).
2. **Déterminisme et reprise.** Chaque étape écrit `state.json` par empreinte ; relancer ne refait que ce qui manque ; `--force` pour refaire. Un asset = `<empreinte12>.<ext>` dans `work/cache/`, renommé seulement à l'étape 8 vers le nom du manifeste.
3. **Zéro réseau par défaut.** `gen.py` refuse tout backend réseau sans `--allow-network` **et** un plafond (`--max-cost`, `--max-chars`) **et** un `pricing.json` rempli (reprend la règle « aucune dépense sans tarif » de `mp.py plan`). Les modèles locaux sont téléchargés **une fois, à la main**, dans `~/.cache/castbridge-models/` avec leur `LICENSE` copiée à côté (le registre w9-13 refuse un modèle sans fichier de licence).
4. **Dry-run partout** : `--dry-run` imprime les commandes ffmpeg et les demandes qui seraient faites, les tailles estimées, et **sort 0** sans écrire d'asset ; c'est ce que la CI exécute.
5. **Marquage** : `synthetic: true` sur tout audio de moteur ; `origin: "generated"` + `generator` (script versionné) + `model` sur toute image ; la TV affichera « voix de synthèse » (w9-14). **Remplacement humain** : un asset humain remplace un asset synthétique **à id égal** (même `m:<id>`), le manifeste garde `replacedSyntheticSha256` ; le texte du lot ne change pas, seule la version du lot média monte.
6. **Rien ne contourne les contrôles** : un asset sans rapport QC `ok` n'entre jamais dans `media.json`.

### 2.2 Manifeste de provenance (w9-01) — champs par asset

Fichier `work/provenance/<empreinte>.json`, puis fusionné dans `media.json` (Langues : champs existants `license`, `engine`, `synthetic`, `voiceLicense`, `lang`, `source` inchangés, `docs/LANGUES.md:243-249`) et dans `content/MEDIA-MANIFEST.json` (Apprendre : `origin`, `generator`, `licence`, `author`, `sha256`, `bytes`, `lot`, `lessons`, `alt`, `caption`, `transcript`, `durationS`, `poster`, `docs/MEDIA-POLICY.md:34-49`). Champs **additifs** (ignorés par les lecteurs) :

| Champ | Exemple | Rôle |
|---|---|---|
| `requestFingerprint` | `"3f9a…"` | lie l'asset à la demande (`mp.py`) |
| `generator` | `"tools/content-gen/tts.py@<commit>"` | script et commit |
| `backend`, `model`, `modelVersion`, `modelSha256` | `"kokoro"`, `"hexgrad/Kokoro-82M"`, `"v1.0"`, `"…"` | reproductibilité |
| `voiceId`, `voiceLicense`, `voiceLicenseUrl`, `licenseVerifiedAt` | `"zf_xiaobei"`, `"Apache-2.0"`, url, `"2026-10-05"` | registre w9-13 |
| `promptSha256`, `seed`, `steps`, `guidance` | images et vidéos | reproductibilité sans stocker le prompt en clair (le prompt est dans `work/prompts/`, hors dépôt) |
| `producedAt`, `host` | `"2026-10-06T10:12:00+01:00"`, `"mac-m2max"` | traçabilité |
| `synthetic`, `humanReplacement` | `true`, `"pending"` / `"done"` / `"n/a"` | marquage et suivi du remplacement |
| `qc` | `{"loudnessLufs": -16.1, "truePeakDb": -1.6, "leadSilenceMs": 90, "asrCer": 0.02, "status": "ok"}` | preuve de contrôle |
| `family`, `licence` | `"free"` / `"reserved"`, `"CC-BY-SA-4.0"` / `"CASTBRIDGE-ORIGINAL"` | **ne se mélangent jamais** (règle de `mp.py receive`) |
| `clonage_vocal`, `imitation_personne_reelle`, `depictsPerson` | `false` | gabarits juridiques existants |

### 2.3 Scripts et storyboards des shorts (w9-02)

Un short = `storyboard.json` déterministe : `{"id","lot","source":{"pack","lesson"|"unit","dialogue"},"scenes":[{"t":0,"dur":4,"card":"m:img-…","say":{"lang":"zh","text":"你好！","voice":"A"},"sub":{"zh":"你好！","fr":"Bonjour !"}}],"outro":{"text":"CastBridge · Chinois A0","dur":2}}`. Deux générateurs : **gabarit** (déterministe : un dialogue → une scène par réplique, carte = image du mot clé ou figure de la fiche) et **brouillon LLM** (Apprendre : objectif → exemple → piège, en ≤ 60 mots parlés), **toujours** relu par un humain avant TTS (`state: review` → `validated` dans le storyboard ; `gen.py` refuse de synthétiser un storyboard non validé sauf `--draft` qui marque l'asset `draft: true` et l'exclut du lot).

### 2.4 Où tout cela s'exécute

Sur le **Mac du propriétaire** (Apple M2 Max, 32 Go, macOS 27, `ffmpeg` 9.0.2, `espeak-ng`, Python 3.14, `mlx` 0.31, `mlx-whisper` 0.4.3, `torch` 2.11, Pillow ; **absents** : Piper, Kokoro, `soundfile`, sox, ImageMagick). Les sessions cloud n'ont ni modèles ni réseau vers Hugging Face (`docs/LANGUES.md:371-372`) : elles écrivent et testent les outils en dry-run et avec `espeak-ng` ; **la production réelle est locale**. Vérification à faire avant le premier lot : `ffmpeg -hide_banner -encoders | grep -E 'libopus|libx264|libwebp|aac'` et `ffmpeg -filters | grep -E 'subtitles|loudnorm|ebur128|zoompan'` (le filtre `subtitles` exige libass : sinon repli `drawtext`).

## 3. Outils : comparatif et recommandation

### 3.1 Voix (TTS)

| Outil | Hors ligne ? | Langues parmi les 7 | Qualité (ordre de grandeur) | Licence moteur / voix | Redistribution commerciale de l'audio | Verdict |
|---|---|---|---|---|---|---|
| **Kokoro-82M** (hexgrad) | oui, CPU/MLX, 82 M params | en, fr, es, it, **ja, zh** (pas de **de**) | très bonne pour un modèle léger ; zh/ja **à faire écouter par un natif** (tons, accent de hauteur) | Apache-2.0 (poids et voix) ; G2P `misaki` (Apache) + `misaki[ja]`/`[zh]` | oui | **retenu** (6 langues) |
| **Piper** (rhasspy) | oui, très rapide (ONNX) | de (`thorsten`), fr, es, it, en, zh (`huayan`) ; pas de ja | correcte à bonne selon la voix | MIT ; **une licence par voix** (CC0 Thorsten, CC-BY-4.0 `siwis`, etc. : lire le `MODEL_CARD`) | oui si la voix le permet ; attribution si CC BY | **retenu** pour l'allemand et comme secours |
| **MeloTTS** (MyShell) | oui | en, es, fr, zh, ja (pas de de/it) | bonne, zh mixte zh/en | MIT | oui | option (lourde à installer) |
| **CosyVoice 2** (Alibaba) | oui (GPU conseillé) | zh, en, ja, (ko) | excellente en zh | Apache-2.0 | oui | option **pour le chinois** si Kokoro déçoit à l'écoute ; plus lourd |
| **espeak-ng** | oui | 7 | robotique | GPL-3.0 (moteur) ; sortie non contaminée (hypothèse à valider, `docs/FREE-CONTENT.md:69`) | oui | secours seulement ; déjà utilisé pour les 66 `.opus` pilotes (`gen_a0_pilot.py:9-11`) |
| **macOS `say`** (voix Apple) | oui | 7 (Eddy/Flo…, Anna, Alice, Daniel, Amélie, Kyoko, Ting-Ting…) | bonne | **SLA macOS : voix pour usage personnel/non commercial** | **non** : ne jamais redistribuer | prototypage interne (écouter un storyboard) uniquement ; backend `say` marqué `redistributable: false` |
| **XTTS v2 (Coqui)**, **F5-TTS**, **Fish-Speech**, **MMS-TTS (Meta)** | oui | larges | bonnes à très bonnes | CPML non commercial ; CC-BY-NC ; CC-BY-NC-SA ; **CC-BY-NC-4.0** | **non** | **exclus** (déjà exclus par `tts_synth.py:11`) |
| **Chatterbox (Resemble)**, **Orpheus** | oui | surtout en | bonnes | MIT / Apache-2.0 | oui | hors besoin (anglais seul) ; **clonage interdit** même si la licence le permet |
| **Google Cloud TTS** (Neural2 / Chirp 3 HD) | non (API) | 7 | excellente | conditions Google Cloud ; sortie utilisable commercialement, pas d'attribution exigée, interdiction d'entraîner des modèles | oui (archiver les conditions datées) | **option payante** déjà cadrée par `tools/media-pipeline` (décision « outils Google », `docs/MEDIA-PIPELINE.md:3`) : réserver aux lots **réservés** après échantillon |
| **Azure TTS**, **Amazon Polly** | non | 7 | excellente | conditions respectives ; commercial OK | oui | équivalents ; pas de second fournisseur en W9 |
| **ElevenLabs** | non | 7 | excellente | **plan gratuit : non commercial + attribution** ; commercial en plan payant ; clonage soumis à consentement | selon plan | non retenu en teasing (coût, clonage tentant) |
| **Edge-TTS** (bibliothèque non officielle) | non | 7 | très bonne | usage hors conditions de Microsoft Edge | **non** | **exclu** |

**Recommandation** : Kokoro (en, fr, es, it, ja, zh) + Piper (de ; secours fr/es/it/en/zh) ; deux voix par langue (A féminine, B masculine) **fixées dans le registre** ; vitesse 0,85 en A0-A1 ; marquage `synthetic` ; échantillon de **10 pistes par langue écouté par un natif avant la masse** (règle de `docs/LANGUES.md:359`). Google TTS seulement si le propriétaire le décide (D1), via `mp.py` et un plafond, pour les shorts Apprendre réservés.

### 3.2 Images

| Outil | Local (Apple Silicon) ? | Licence | Usage commercial | Remarques | Verdict |
|---|---|---|---|---|---|
| **Cartes Pillow / figures `animlib`** (dessin programmé) | oui, instantané | code du dépôt | oui ; `origin: generated` + `generator` (règle `MEDIA-POLICY.md:39`) | déterministe, texte parfait, style charte ; polices **Noto Sans / Noto Sans CJK (OFL-1.1)** à télécharger une fois | **retenu par défaut** |
| **FLUX.1 [schnell]** via `mflux` (MLX) | oui, ≈ 20–40 s / image 768² en 4 pas sur M2 Max (4 bits) | **Apache-2.0** | oui | pas de texte lisible dans l'image ; pas de personnes réelles ; sortie à passer au filtre de sécurité | **retenu en option** pour scènes et objets |
| **FLUX.1 [dev]**, **FLUX Kontext [dev]** | oui | **non commercial** | **non** | — | exclus |
| **Stable Diffusion 3.5 (Stability Community License)** | oui | gratuit sous 1 M $ de revenus, conditions | oui sous conditions | à archiver | option, pas en W9 |
| **SDXL 1.0** (CreativeML Open RAIL++-M) | oui | RAIL (restrictions d'usage) | oui | plus ancien, moins bon sur les mains | option |
| **APIs commerciales** (Imagen, DALL·E, Ideogram) | non | conditions propres | généralement oui | coût, et droit d'auteur des sorties IA incertain selon pays | non en W9 |
| **Sources CC0 / domaine public** (Openverse, Wikimedia Commons, Unsplash sous licence Unsplash) | — | CC0/PD vérifiés fichier par fichier ; **Unsplash ≠ CC0** | oui | aucune personne identifiable ; `sourceUrl` + date | option pour photos d'objets |

**Décision de style** : dessin plat, personnages **stylisés jamais réalistes** (enfants compris), contextes camerounais quand c'est pertinent (`docs/PEDAGOGY-RUBRIC.md` « pertinence culturelle »), **aucun texte dans l'image générée** (les modèles d'image écrivent mal, surtout en CJK) : le texte est rendu par Pillow par-dessus.

### 3.3 Vidéo et animation

| Outil | Licence | Verdict |
|---|---|---|
| **ffmpeg** (zoompan Ken Burns, concat, `subtitles`/`drawtext`, affiche) | LGPL/GPL (outil, pas la sortie) | **retenu** : tout l'assemblage |
| **MoviePy** | MIT | inutile si ffmpeg suffit ; option de confort |
| **Remotion** | gratuit ≤ 3 personnes, licence d'entreprise au-delà | non (dépendance Node, licence à surveiller) |
| **Rendu MP4 des animations `animlib`** (JSON → images PNG → ffmpeg) | code du dépôt | **retenu** pour traits et gestes (w9-05, option `anim`) |
| **Avatars / têtes parlantes** (SadTalker, Wav2Lip, LivePortrait, HeyGen, D-ID, Synthesia) | Wav2Lip : recherche non commerciale ; SadTalker Apache mais modèles tiers ; services : licence d'avatar, visages réels | **exclus en teasing** : risque de visage réel, coût, et la politique interdit toute personne identifiable |
| **Rhubarb Lip Sync** (MIT) | MIT | option future pour un personnage dessiné qui parle |

### 3.4 Rédaction, traduction, vérification

| Besoin | Outil | Règle |
|---|---|---|
| Brouillon de storyboard / script (Apprendre) | LLM (modèle le moins cher suffisant : Haiku pour le gabarit, Sonnet pour la rédaction ; cf. rôles des modèles) | **relecture humaine obligatoire** ; `state: review` jusqu'à validation ; aucun texte de manuel ni d'annale (`CONTENT-ARCHITECTURE.md` règle C) |
| Traduction / glose | LLM + **rétro-traduction** automatique comparée (w9-07) | écart sémantique signalé, jamais corrigé en silence |
| Vérification texte ↔ audio | **mlx-whisper** (MIT ; modèles `large-v3-turbo`) : transcription, CER par piste | CER > 10 % (latin) / > 15 % (zh, ja) = rejet ; **ne remplace pas l'écoute native** |
| Tons et lectures | `pypinyin` (MIT) : pinyin avec tons du `term` vs `reading` ; `pykakasi` (Apache-2.0) ou `fugashi` + `unidic-lite` (BSD) : kana du `term` vs `reading` | tout écart = `needs-fix` (une faute de ton en pinyin est bloquante, `docs/LANGUES.md:351`) |
| Alignement forcé (sous-titres au mot) | **whisperX** ou **stable-ts** (MIT) sur CPU ; sinon alignement par réplique (durée de chaque piste) | W9 : alignement **par réplique** (suffisant pour des shorts de 30 s) |

## 4. Contrôle qualité

### 4.1 Linguistique (humain + automatique)

1. **Échantillonnage natif** : avant la masse, 10 pistes par langue (5 mots, 1 dialogue, 4 exercices), grille d'écoute : intelligibilité (0-2), tons/accent (0-2), débit (0-2), naturel (0-2) ; **seuil : aucune piste < 1 sur tons/accent, moyenne ≥ 6/8**. Après la masse : 5 % des pistes tirées par hachage d'id (déterministe, comme l'essai).
2. **Rétro-traduction** (w9-07) : `tr` → langue cible par LLM → comparaison avec `text` (similarité lexicale ≥ 0,6 ou revue).
3. **Lectures** : pinyin et kana vérifiés par outil (§ 3.4) ; chiffres et noms propres toujours relus.
4. **Niveau** : vocabulaire d'une unité A0 ⊂ liste A0 (compteur de mots hors liste, `docs/LANGUES.md:388`).

### 4.2 Audio (automatique, w9-06)

| Contrôle | Seuil | Mesure |
|---|---|---|
| Intensité intégrée | **−16 LUFS ± 1** | `ebur128` |
| Crête vraie | ≤ −1,5 dBTP | `ebur128=peak=true` |
| Écrêtage | 0 échantillon à ±1,0 sur le WAV avant encodage | `astats` (`Peak level`, `Flat factor`) |
| Silence en tête / queue | ≤ 150 ms / ≤ 250 ms ; jamais de coupure de mot (≥ 60 ms de silence avant la fin) | `silencedetect` |
| Bruit de fond | < −50 dBFS sur les silences | `astats` sur les segments silencieux |
| Format | Ogg/Opus mono, 16 kHz d'entrée (`OpusHead`), 16 ou 24 kbit/s ± 20 % | lecture de l'en-tête (comme `mp.py receive`) |
| Durée | mot ≤ 4 s, phrase ≤ 20 s, dialogue ≤ 120 s ; essai : ≤ 60 s | `ffprobe` |
| Taille | ≤ 200 Ko / 30 s ; essai ≤ 256 Kio | — |
| Métadonnées | aucune (pas de `artist`, de date, de chemin) | `ffprobe -show_format` |

### 4.3 Images et vidéos (automatique, w9-08)

| Contrôle | Seuil |
|---|---|
| WebP valide, ≤ 120 Ko (carte ≤ 80 Ko), ≤ 1 280 px, pas d'EXIF/XMP | `MEDIA-POLICY.md:11` |
| Texte dans l'image générée | OCR interdit en W9 (pas de dépendance) : règle de production « aucun texte » + revue visuelle de 100 % des images FLUX (planche contact `tools/anim`-style) |
| Visages réalistes / personnes réelles | revue visuelle 100 % (FLUX) ; cartes Pillow : impossible par construction |
| Vidéo | H.264, hauteur ≤ 480, ≤ 30 s, 25 i/s, AAC, `faststart`, affiche présente, sous-titres présents (`.ass` et `.srt` jumeaux) |
| Clignotement | **≤ 3 flashs/s** : delta de luminance moyenne entre images consécutives ; compte des pics par seconde (`flashHz` écrit dans le manifeste) |
| Lisibilité TV | sous-titres ≥ 28 px sur 480p, 2 lignes max, 42 caractères/ligne latin, 20 CJK ; marge de sécurité 5 % |
| Pertinence culturelle, stéréotypes | revue humaine (grille `PEDAGOGY-RUBRIC` `culturalRelevance`) |

### 4.4 Validateurs à écrire (tous hors ligne, bibliothèque standard + ffmpeg)

`qc_audio.py` (w9-06), `qc_align.py` (w9-07 : whisper optionnel ; sans modèle, « non vérifiable » bloquant), `qc_visual.py` (w9-08), `provenance.py --check` (w9-01 : champs, familles, licences du registre, `synthetic`), `licenses.py --check` (w9-13 : refus NC/ND, `say` non redistribuable, fichier `LICENSE` présent à côté du modèle), et le `check_media.py` / `LangValidator` existants en bout de chaîne.

## 5. L'expérience de la phase de teasing

### 5.1 Matrice minimale

| Produit | Cellules | Médias | Lots |
|---|---|---|---|
| **Langues** (libre) | **zh, ja, en** × **A0, A1** | audio de vocabulaire + dialogue (Kokoro), 12 cartes Pillow, **1 short** de dialogue | 6 lots média `<cible>-<niveau>-<thème>` (thème `salut` en A0 : packs existants ; A1 : à écrire par la vague de contenu ; si A1 n'existe pas à temps, prendre `nombres` et `famille` d'A0) ; **≤ 8 Mo chacun** |
| **Apprendre** (réservé, louable) | **cm2, 3e, tle-cd** (classes d'examen, déjà dans le socle de l'APK, `AUDIT-CONTENU-APPRENDRE:12`) | **5 shorts** par classe (maths : 1 fiche d'intro, 1 fiche difficile, 3 fiches d'examen), 5 cartes mémo | 3 mini-lots `learn:<classe>-media` ≤ 8 Mo ; bouquet `classe-<scope>` existant étendu ou bouquet `media-<scope>` **à créer** (décision D6) |

Pourquoi zh, ja, en : zh et ja ont déjà des packs A0 et le plus gros budget (17 %), en est la demande la plus large (`docs/LANGUES.md:207,418`). Pourquoi cm2, 3e, tle-cd : examens nationaux (CEP, BEPC, Bac), priorité de l'essai (`trial-edition/config.json:22-30`).

### 5.2 Tester la location (sans rien inventer)

- **Produit** : `loc-<bouquet>` à **durée exacte 30 j** (`content/bundles-rental.json`), fenêtre d'essai des lots **3 j / 720 min** (unique, existante), contrat scellé par TV ; les lots Langues restent gratuits et visibles comme « contenu libre » à côté (comparaison **gratuit vs loué**).
- **Points de prix** (XAF) : trois cellules **P-bas / P-moyen / P-haut** affectées **par TV** (le téléphone-vitrine montre la grille signée ; une grille par cellule, signée hors ligne : W4-C). Les valeurs sont une **décision du propriétaire (D4, BLOQUÉ)** ; ordre de grandeur à discuter : prix d'une recharge téléphonique courante, ×2, ×4.
- **Durées** : 30 j par défaut ; une cellule à **7 j** (bouquet `media-<scope>-7j`, `bundles` dans `bundles-rental.json`) pour tester la durée courte ; jamais > 60 j (plafond en ligne W5).
- **Conversion** : TV ayant ouvert la fenêtre d'essai → TV ayant une commande `loc-*` confirmée (registre des commandes W5 si déployé ; sinon carnet du point focal, une ligne par TV : code d'appareil, cellule, date, résultat).
- **Rien à coder côté serveur** pour la version « point focal » : clés et locations émises par les outils existants (`emettre --location`, `tools/rental-test`).

### 5.3 Ce qui se mesure, avec quoi, sous quel consentement

| Mesure | Source existante | Niveau de consentement | Limite |
|---|---|---|---|
| Fiches/unités ouvertes et terminées, temps | événement `learn` (`lesson_view`, `lesson_complete`, `exercise_result`, `ms`) ; `content_stat` (`kind=lesson|exercise`, `shown`, `correct`, `ms`) | **statistiques d'usage** (opt-in ; `docs/TELEMETRY.md:24,70-87`) | aucun événement « média lu » : **la lecture d'un short n'est pas comptée à distance** |
| Lectures de shorts (complétion, rejeux) | **aucun** événement aujourd'hui (`playback_*` ne concerne que le lecteur de bibliothèque) | — | **D3** : (a) ajouter `kind=media` à `content_stat` (client + `EventCatalog.java`, serveur déployé avant les apps) ou (b) **lecture locale** sur les TV bêta : `GET /api/learn/events?since=` (500 derniers événements, `docs/LEARN.md:512`) étendue aux médias par w9-14 |
| Stockage, taille installée | `library_stats` (`files`, `bytes`), manifeste `GET /api/lots` (`usedBytes`) | usage / local | lecture locale suffisante |
| Temps de chargement d'un short sur la TV | **chronomètre manuel** (campagne de test) + journal local w9-14 (`openMs`) | local | TV de référence seulement |
| Location : essais ouverts, commandes, renouvellements | registre W5 (`orders`) ou carnet du point focal | contractuel | hors télémétrie |
| Qualité perçue | questionnaire de 5 questions au point focal (voix compréhensible ? vitesse ? images utiles ? achèteriez-vous ? prix ?) | consentement oral, aucune donnée personnelle | n ≈ 10-20 foyers |

**Règle** : aucun nouvel événement, aucun identifiant de personne, aucun nom de fichier (`FORBIDDEN`, `Telemetry.kt:57-59`). Si D3(a) est retenu, le texte de consentement (`ConsentText.VERSION = "2026-11"`) **mentionne déjà « les lectures »** : pas de nouvelle version nécessaire, mais le serveur doit être déployé avant les apps (`docs/TELEMETRY.md:116-117`).

### 5.4 A/B sans serveur

1. **Intra-lot (recommandé)** : dans chaque mini-lot Apprendre, **la moitié des fiches** reçoit un short, l'autre non (tirage par hachage SHA-256 de l'id de fiche, déterministe). Comparer `lesson_complete / lesson_view` et `ms` entre les deux moitiés **sur le même élève** : pas d'affectation d'appareil, pas de serveur.
2. **Inter-TV par version de lot** : lot `v1` (cartes seules) sur les TV paires, `v2` (cartes + shorts) sur les impaires, livré par le téléphone du point focal ; la TV déclare sa version dans son manifeste.
3. **Prix** : une grille signée par cellule, affectée à la TV à l'activation (carnet).
4. **Voix** : lot `zh-a0-salut` avec Kokoro vs Piper `huayan` sur deux groupes de foyers ; question « voix compréhensible ? ».

### 5.5 Seuils de réussite (à confirmer par le propriétaire)

| Indicateur | Seuil « on continue » | Seuil « on arrête / on corrige » |
|---|---|---|
| Écoute native (échantillon) | moyenne ≥ 6/8, aucune piste < 1 en tons | une langue < 5/8 → changer de moteur pour cette langue |
| Rejet automatique QC | < 5 % des assets | > 15 % → corriger la chaîne avant la masse |
| Taux de fiches terminées avec short vs sans | ≥ +15 % relatif | < 0 → les shorts n'aident pas : garder audio + cartes |
| Rejeux audio de vocabulaire (local) | ≥ 20 % des mots rejoués au moins une fois | — |
| Chargement d'un short sur la TV de référence | < 2 s jusqu'à la première image | > 4 s → baisser à 360p ou CRF 30 |
| Stockage par lot média | ≤ 8 Mo | > 10 Mo → refus serveur |
| Conversion essai → location (point focal, n ≥ 10) | ≥ 10 % | < 3 % sur toutes les cellules → revoir prix/durée avant d'enrichir |
| Réclamations « voix bizarre » | ≤ 1 sur 10 foyers | > 3 → voix humaines prioritaires |

### 5.6 Livraison aux TV pendant l'expérience

- **Langues** : tant que w9-14 n'est pas compilé, la TV ne reçoit pas le lot média (`LanguesHub.kt:15`) ; le **téléphone** (CastBridge) les affiche s'il a un écran Langues avec audio (à vérifier dans `:sender` : non confirmé ici). Chemin de secours **pour un teasing en magasin** : copier les MP4 dans `Download/CastBridge/Bibliotheque/Teasing/` de la clé USB et les lire dans la bibliothèque de la TV (libVLC) — aucune mesure fine, mais une démonstration réelle.
- **Apprendre** : idem ; les mini-lots ne sont **louables** (scellés, balayés à l'échéance) que si un consommateur TV existe (w9-14). Sinon, l'expérience de location porte sur les **lots texte** existants (ils sont déjà réservés et louables) et les shorts servent d'**aperçu** dans la boutique (téléphone) et à la démonstration.

## 6. Coûts, temps, automatisation, plan de 2 semaines

### 6.1 Ordres de grandeur (Mac M2 Max, mesures à confirmer lors de w9-11 ; aucune dépense réseau)

| Tâche | Temps machine | Temps humain | Coût monétaire |
|---|---|---|---|
| 100 mots/phrases TTS (Kokoro, CPU/MLX) ≈ 6 min d'audio | 2–5 min | écoute de 10 % : 10 min ; natif 100 % en A0 : ≈ 1,5 h | 0 |
| 100 mots/phrases TTS (Piper) | < 1 min | idem | 0 |
| 100 cartes Pillow | < 1 min | revue de 100 % : 15 min | 0 |
| 100 images FLUX schnell (768², 4 pas, 4 bits) | 40–70 min | revue 100 % : 30 min | 0 (téléchargement unique ≈ 12 Go) |
| 1 minute de short assemblé (480p, CRF 28, preset slow) | 30–90 s | storyboard + relecture : 15–30 min par short de 30 s | 0 |
| Vérification whisper de 100 pistes (large-v3-turbo, MLX) | 3–6 min | tri des rejets : 10 min | 0 |
| Un lot média Langues A0 (12 mots, 1 dialogue, 10 exercices, 12 cartes, 1 short) | ≈ 10 min | ≈ 3 h (relecture native comprise) | 0 |
| Un mini-lot Apprendre (5 shorts, 5 cartes) | ≈ 15 min | ≈ 4 h (5 storyboards relus par un enseignant) | 0 |
| Option Google TTS (D1) | — | — | **inconnu tant que `pricing.json` est vide** (règle `mp.py estimate`) ; la grille publique de Google est à relever et à dater par le propriétaire |

**Niveau d'automatisation visé** : 100 % de la génération, de l'encodage, des contrôles, des manifestes et des lots ; **0 % de la validation linguistique et pédagogique** (humaine, par échantillon).

### 6.2 Plan de deux semaines

| Jour | Quoi | Qui |
|---|---|---|
| J1–J2 | Décisions D1–D9 ; w9-01, w9-13 (schéma, registre) ; téléchargement des modèles sur le Mac (Kokoro, Piper `thorsten`, Noto, whisper turbo, FLUX schnell en option) avec leurs `LICENSE` | propriétaire + sonnet |
| J3–J5 | w9-02, w9-03, w9-04, w9-06, w9-07, w9-08 en parallèle (fichiers disjoints) ; dry-run en CI | sonnet / haiku |
| J5 | **Échantillon** : 10 pistes × 3 langues écoutées par un natif (ou, à défaut, par le propriétaire et un locuteur de confiance) → choix de voix définitif | propriétaire |
| J6–J8 | w9-05, w9-09, w9-10, w9-11 ; premier lot `zh-a0-salut` complet ; test `.opus` et `.mp4` sur la TV de référence (clé USB) | sonnet |
| J8–J10 | w9-14 (TV : lot média jumeau + badge « voix de synthèse ») compilé par le propriétaire ; w9-12 (fiche d'expérience, carnet) | sonnet + propriétaire |
| J10–J12 | production des 6 lots Langues et 3 mini-lots Apprendre ; QC ; relecture ; signature hors dépôt ; w9-15, w9-16 | propriétaire + haiku |
| J12–J14 | déploiement sur 10–20 TV bêta par le point focal ; lecture des premiers événements ; ajustement | propriétaire |

## 7. Liste de contrôle juridique et éthique

| # | Point | Règle de production | Statut |
|---|---|---|---|
| 1 | **Licence des sorties IA** (audio, images) | audio Kokoro/Piper : sortie non grevée par la licence du moteur (Apache/MIT) ; images FLUX schnell (Apache) ; **le droit d'auteur sur une sortie purement générée est incertain** selon les pays : Langues → publiée **CC BY-SA 4.0** par décision ; Apprendre → `CASTBRIDGE-ORIGINAL` (réservé) | à valider par un juriste (déjà noté `docs/MEDIA-PIPELINE.md:42`) |
| 2 | **CC BY-SA des packs Langues libres** vs médias | un média d'un pack libre doit être redistribuable sous SA : jamais un média Google/Apple/NC dans un lot libre ; familles non mélangées (`mp.py receive`) ; archive libre reconstruite (`build_free_archive.py`) | règle outillée (w9-13) |
| 3 | **Textes sources** | uniquement `langue.json` / fiches originales ; Tatoeba (CC BY : attribution par phrase), domaine public vérifié ; **aucun manuel, aucune annale, aucun script de film/chanson** | règle |
| 4 | **Voix** | voix de catalogue libres ou commerciales approuvées ; **aucun clonage**, aucune imitation d'une personne (gabarits `legal/INTERDICTION-IMITATION.md`) ; voix macOS jamais redistribuées | registre w9-13 |
| 5 | **Mineurs** | aucune photo ni visage réaliste d'enfant ; personnages dessinés ; aucune donnée d'enfant dans les manifestes ; pas de sollicitation commerciale dans un short (pas de « achète maintenant ») | règle + revue visuelle |
| 6 | **Divulgation IA** | `synthetic: true`, badge « voix de synthèse » (w9-14), mention « images générées par programme » dans l'écran Crédits généré depuis `media.json` ; conforme à l'esprit des obligations de transparence (UE AI Act art. 50 pour un éventuel public européen) | w9-10, w9-14 |
| 7 | **Conditions des API** (si D1 = Google) | archiver les conditions datées sous `legal/`, interdiction d'entraînement, pas d'attribution exigée, enregistrer `engine` | `mp.py registries` |
| 8 | **Données personnelles** | aucune dans les médias, manifestes, journaux ; mesure sous consentement « statistiques d'usage » ; loi n° 2024/017 (Cameroun) | `docs/TELEMETRY.md` § 7 |
| 9 | **Verrou logiciel vs contenu SA** | les lots libres ne sont ni chiffrés ni loués ; l'application reste soumise à autorisation (décision du propriétaire) | à valider par un juriste (`docs/LANGUES.md:444`) |
| 10 | **Polices** | Noto (OFL-1.1) incrustées dans des images/vidéos : autorisé ; mention dans les crédits | règle |
| 11 | **Marques et logos** | aucun logo d'examen ni d'organisme (`docs/LANGUES.md:74`) ; pas de marque tierce dans les scènes | revue |

## 8. Risques

| Risque | Effet | Parade |
|---|---|---|
| Tons chinois / accent japonais faux dans la voix de synthèse | apprentissage erroné | échantillon natif avant la masse ; CosyVoice 2 en secours pour zh ; pistes `needs-fix` exclues |
| `.opus` ou `.mp4` illisible par `MediaPlayer` sur la TV de référence | silence sur la TV | test J6 ; secours AAC `.m4a` ; vidéo toujours AAC/MP4 |
| Serveur limité à 10 Mo par lot | lots média refusés | lots ≤ 8 Mo en W9 ; relèvement à 100 Mo pour `langmedia` = chantier serveur séparé |
| Aucun consommateur TV de lots média | expérience non mesurable sur TV | w9-14 ; sinon mesure téléphone + démonstration USB |
| Dérive de licence d'un modèle (changement de conditions) | redistribution interdite | `licenseVerifiedAt` + copie du `LICENSE` ; regénération possible (tout est reproductible) |
| Médias lourds dans le dépôt de code | dépôt alourdi, règle violée | `.gitignore` existant ; sorties dans `castbridge-content` ou `work/` |
| Le propriétaire est seul | goulot humain | 100 % automatisé sauf validation ; échantillonnage à 5 % après la masse |
| Décision « outils Google » antérieure vs chaîne locale | deux chaînes divergentes | **même manifeste, mêmes contrôles** (`mp.py receive` reste la porte d'entrée des assets Google) : D1 tranche la part de chacune |

## 9. Décisions du propriétaire (recommandation en gras ; « BLOQUÉ » = fait manquant)

| # | Question | Options | Recommandation |
|---|---|---|---|
| **D1** | Chaîne de voix par défaut en W9 | (a) locale libre (Kokoro + Piper) ; (b) Google TTS via `tools/media-pipeline` ; (c) les deux : locale pour Langues (libre), Google pour Apprendre réservé après échantillon | **(c)**, avec **(a) seule pendant les 2 semaines** (0 dépense) ; (b) seulement si l'écoute native rejette une langue |
| **D2** | Images génératives (FLUX schnell) ou cartes programmées seules | cartes seules / FLUX en option / APIs | **cartes Pillow par défaut, FLUX schnell en option** pour 20 % des scènes ; aucune API |
| **D3** | Mesure des lectures de shorts | (a) `kind=media` dans `content_stat` (serveur + apps) ; (b) lecture locale seulement en W9 | **(b) en W9, (a) préparée pour W10** (serveur déployé avant les apps) |
| **D4** | Points de prix et durée courte de l'expérience de location | 3 prix XAF + cellule 7 j | **BLOQUÉ** (fait : grille de prix) ; proposition : recharge courante ×1, ×2, ×4 |
| **D5** | Variétés | anglais : en-GB / en-US ; espagnol : es-ES / es-419 | **en-GB** (système camerounais anglophone), **es-ES** (voix libres disponibles) ; compréhension des variétés au niveau natif plus tard |
| **D6** | Lots média Apprendre louables sur la TV | (a) w9-14 compilé en W9 ; (b) shorts = aperçus boutique + démonstration USB seulement | **(a)** si le propriétaire peut compiler en J8–J10 ; sinon (b) et l'expérience de location porte sur les lots texte existants |
| **D7** | Budget monétaire W9 | 0 / plafond pour Google | **0 XAF** en W9 ; **BLOQUÉ** pour un plafond éventuel (fait : budget) |
| **D8** | Style visuel | dessin plat stylisé / semi-réaliste | **dessin plat**, personnages stylisés, jamais réalistes (mineurs) |
| **D9** | Voix par langue | 1 voix / 2 voix (A, B) ; vitesse A0 | **2 voix** (A féminine, B masculine) fixées dans le registre ; **0,85×** en A0-A1 |
| **D10** | Où vivent les sorties | `castbridge-content` (dépôt privé, LFS) / disque local `work/` | **`castbridge-content`** pour les assets acceptés ; `work/` local pour le cache |
| **D11** | Format des shorts | 16:9 854 × 480 seul / vertical aussi | **16:9 seul** (la TV est 16:9 ; le téléphone affiche en bande) |
| **D12** | Relecteurs natifs zh / ja / en | personne de confiance / service payant / propriétaire seul | **BLOQUÉ** (fait : qui ?) ; sans natif, les pistes restent `review` et marquées « non validées » |

## 10. Ce que je n'ai pas pu vérifier

- La lecture effective d'un `.opus` (Ogg) et d'un `.mp4` H.264/AAC par `MediaPlayer` sur la TV GaiaOS de référence (aucun `adb`, aucune TV ici).
- Les encodeurs réellement compilés dans le `ffmpeg` 9.0.2 du Mac (`libopus`, `libx264`, `libwebp`, `libass`).
- Les licences **à la date du téléchargement** des voix Piper et des poids Kokoro/FLUX (valeurs citées de mémoire, à confronter aux `LICENSE`/`MODEL_CARD`).
- L'existence d'un écran Langues avec audio dans l'application téléphone (`:sender`) : non inspecté.
- La performance réelle de Kokoro / FLUX sur le M2 Max (ordres de grandeur, non mesurés).
- Le déploiement effectif de la boutique W5 (commandes `loc-*`) sur le serveur de production : hors de ma zone.
- La compatibilité CC BY-SA d'une sortie IA et la qualification juridique des médias synthétiques dans un lot loué : avis de juriste.
