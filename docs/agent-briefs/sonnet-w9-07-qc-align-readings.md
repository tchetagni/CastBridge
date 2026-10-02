# w9-07 — Cohérence texte ↔ audio (`qc_align.py`, whisper optionnel) et lectures pinyin/kana (`readings.py`)

**Vague 9b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 3.4, § 4.1 ; contrat C4. Branche `claude/sonnet-w9-07`. Rapport : `docs/agent-reports/sonnet-w9-07.md`.

## Objectif
(1) `qc_align.py` : pour chaque asset audio/vidéo, transcrit avec **mlx-whisper** (import gardé ; modèle `mlx-community/whisper-large-v3-turbo` attendu dans le cache local, **jamais téléchargé par l'outil**) ou lit un `asr-results.jsonl` fourni (même format que `tools/media-pipeline/mp/asr.py`), puis compare au `transcript` de la provenance : taux d'erreur caractère (CER) après normalisation (casse, ponctuation latine et CJK, espaces, NFKC pour la comparaison **seulement**) ; seuils : CER ≤ 10 % latin, ≤ 15 % zh/ja ; rapport C4 ; sans ASR disponible ⇒ `unverifiable` (bloquant, jamais `ok`). (2) `readings.py` : vérifie, pour chaque mot et réplique d'un `langue.json`, que `reading` correspond au `term`/`text` : chinois via `pypinyin` (style TONE, mode `heteronym` : accepter si une des lectures correspond ; tons **obligatoires**, `ma` pour `mǎ` = écart), japonais via `pykakasi` ou `fugashi`+`unidic-lite` (kana du terme vs `reading`, en tolérant la longueur des voyelles notée différemment seulement avec `--lenient`) ; outils absents ⇒ `unverifiable`. (3) Entrée pour la rétro-traduction : `readings.py backtranslate-prompts` écrit, **sans appel réseau**, un fichier de paires (`tr` → à retraduire) que le propriétaire soumet à un modèle ; `readings.py backtranslate-check <réponses.jsonl>` calcule une similarité lexicale (Jaccard sur lemmes approximés par troncature à 5 lettres ; CJK : bigrammes) et signale < 0,6.

## Pourquoi (preuves)
- `docs/MEDIA-PIPELINE.md:47` (ASR : un écart = rejet ; pas de résultat = non vérifiable, bloquant) ; `tools/media-pipeline/mp/asr.py` (format `asr-results.jsonl` : réutiliser).
- `docs/LANGUES.md:154,170,351` (lecture obligatoire zh/ja, validée ; aucune erreur de ton), `:162` (normalisation de `LangMarking` : NFKC, casse, ponctuation, espaces CJK ignorés ; **les tons comptent**).
- Mac : `mlx-whisper 0.4.3` installé ; `pypinyin`, `pykakasi` absents (à installer par le propriétaire : MIT / Apache-2.0).

## Fichiers possédés
`tools/content-gen/qc_align.py`, `tools/content-gen/readings.py`, `tools/content-gen/tests/test_qc_align.py`.

## Étapes
1. Normalisation partagée (module interne) reproduisant les règles de `LangMarking` décrites dans `docs/LANGUES.md:162` (pas d'import Kotlin : réécrire, documenter l'écart éventuel).
2. CER par distance de Levenshtein (implémentation maison, O(n·m), textes ≤ 2 000 caractères).
3. `qc_align.py --provenance work/provenance --audio work/cache [--asr work/asr-results.jsonl] [--model-dir ~/.cache/castbridge-models/whisper] --out work/qc` ; option `--write-asr` pour conserver la transcription dans `work/asr-results.jsonl` (format `mp/asr.py`).
4. `readings.py check content/langues/<scope>/langue.json` : code 0/2, liste des écarts (id, `term`, `reading`, lecture calculée) ; `--lenient` ; `--fix-suggest` imprime un patch JSON **sans l'appliquer**.
5. Tests : CER sur des paires connues (identique = 0 ; une faute sur 10 caractères = 10 %) ; normalisation (『你好！』 vs `你好` ⇒ 0 %) ; ASR absent ⇒ `unverifiable` ; `asr-results.jsonl` fourni ⇒ rapport sans whisper ; `readings.py` avec `pypinyin` **simulé** (module factice injecté dans `sys.modules` dans le test) : `你好`/`nǐ hǎo` ok, `nǐ hao` écart de ton ; absence réelle du module ⇒ `unverifiable` ; `backtranslate-check` sur 3 paires.

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_qc_align.py'
python3 tools/content-gen/readings.py check content/langues/zh-a0-salut-fr/langue.json ; echo $?   # 0 ou 2 avec liste ; « non vérifiable » si pypinyin absent (code 2)
python3 tools/content-gen/qc_align.py --provenance /tmp/w/provenance --audio /tmp/w/cache --out /tmp/qc ; echo $?   # 2 « ASR non disponible » en cloud, jamais 0 sans mesure
grep -n "snapshot_download\|hf_hub\|urllib" tools/content-gen/qc_align.py   # 0
```

## Cas limites
Chiffres (« 10 » dit « shí ») : comparer aussi avec `reading` converti ; whisper qui traduit au lieu de transcrire : forcer `language=<lang>` et `task=transcribe` ; pistes < 1 s : whisper peu fiable ⇒ seuil CER relevé à 30 % **et** marqué `lowConfidence` ; japonais : `reading` en kana vs sortie whisper en kanji ⇒ comparer après conversion kana si `pykakasi` présent, sinon `lowConfidence`.

## À ne pas faire
Aucun téléchargement ; aucune correction automatique du contenu ; aucun appel LLM dans l'outil (les prompts sont écrits pour le propriétaire) ; ne pas modifier `tools/media-pipeline/mp/asr.py`.

## Rapport
`STATUT`, règles de normalisation et écarts avec `LangMarking`, seuils, résultats sur les fixtures, ce qui exige le Mac (whisper, pypinyin, pykakasi).
