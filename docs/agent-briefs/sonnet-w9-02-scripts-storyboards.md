# w9-02 — Scripts et storyboards déterministes des shorts (Langues et Apprendre) et demandes Apprendre

**Vague 9b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 1.2-1.3, § 2.3 ; contrats C1 et C5 de `SONNET-WAVE9-INDEX.md`. Branche `claude/sonnet-w9-02`. Rapport : `docs/agent-reports/sonnet-w9-02.md`.

## Objectif
(1) `scripts.py storyboards` produit un storyboard C5 **déterministe** par dialogue de Langues (`langue.json` → une scène par réplique, carte = mot clé de la réplique s'il est dans `vocab`, sinon carte « phrase ») et par fiche Apprendre choisie (objectif → exemple résolu → piège, **gabarit sans LLM** : les textes dits sont **copiés** des champs `objectives[0]`, `blocks[type=example].statement/answer`, `blocks[type=key,style=pieges].md` nettoyés du Markdown restreint, ≤ 60 mots) ; (2) `scripts.py requests-learn` écrit `work/requests-learn.jsonl` au **même format de ligne** que `mp.py requests` (id, kind, lang, payload, voiceClass, constraints, outputPath, source, priority, fingerprint = SHA-256 de la demande sans chemin ni priorité) pour les pistes des shorts et les cartes mémo ; (3) un mode `--draft-llm` qui **n'appelle rien** : il écrit un fichier `work/storyboards/<id>.prompt.md` (consigne + matière) que le propriétaire soumet lui-même à un modèle, puis `scripts.py import-draft <id> <réponse.json>` valide la réponse contre C5 et la marque `state: review`.

## Pourquoi (preuves)
- `content/langues/ja-a0-salut-fr/langue.json` (`units[].vocab`, `dialogues[].lines[] {who,text,reading,tr}`) ; `tools/langues/gen_a0_pilot.py:53-64` (forme des dialogues).
- `content/learn/4e-maths/lessons/nombres.json` (`lessons[] {objectives, blocks[]}`, blocs `key`, `example`, `illustration`) ; `docs/LEARN.md:168` (types de blocs).
- `docs/MEDIA-PIPELINE.md:24-26` : format des demandes et règle « rien n'est deviné » (texte impossible à déterminer ⇒ `blocked`).
- Relecture humaine obligatoire : guide § 2.3 ; `docs/CONTENT-ARCHITECTURE.md` règle C (aucune reproduction d'annales).

## Fichiers possédés
`tools/content-gen/scripts.py`, `tools/content-gen/templates/storyboard-dialogue.json`, `tools/content-gen/templates/storyboard-lesson.json`, `tools/content-gen/tests/test_scripts.py`.

## Étapes
1. Gabarits : `storyboard-dialogue.json` (durée par scène = max(2,5 s, 0,35 s × nombre de caractères latins ou 0,6 s × caractères CJK) + 0,8 s), `storyboard-lesson.json` (3 scènes : objectif 6 s, exemple 12 s, piège 6 s, outro 2 s ; total ≤ 30 s sinon `blocked: "trop long : raccourcir la fiche ou choisir 2 scènes"`).
2. `storyboards --langues content/langues --learn content/learn --select <liste d'ids de packs/fiches ou @fichier> --out work/storyboards` : un fichier par short, `state: "review"`, `draft: false`, `family` lu dans `content/langues/lots.json` (`free`) ou **`reserved`** pour Apprendre ; sous-titres `sub` = `text` + `tr` (Langues) ou texte dit (Apprendre, fr ou en selon `pack.lang`) ; voix `A`/`B` par `who`.
3. `requests-learn` : lignes pour chaque `say` (kind `audio`, `voiceClass` = `{"lang","register": "naturel","gender": A→f, B→m,"role":…}`) et chaque carte (kind `image`, payload = spec de carte) ; déterministe (tri par id ; deux exécutions = mêmes octets) ; `blocked` si un `say.text` contient une formule `$…$` non lisible (fiche de maths : on dit alors « voir l'écran », décision prise dans le gabarit, **jamais** d'invention).
4. `import-draft` : vérifie C5 strictement (longueurs, langues, ≤ 30 s, aucune scène sans `sub`), refuse tout texte qui ne vient pas de la fiche sauf les connecteurs d'une liste blanche (`Aujourd'hui`, `Exemple`, `Attention`, `Retenons`, et équivalents en anglais).
5. Tests : déterminisme (octets), un dialogue de 7 répliques → 7 scènes + outro, fiche trop longue → `blocked`, `import-draft` refuse un texte étranger, `requests-learn` conforme au schéma de ligne de `mp.py` (comparer les clés avec une ligne produite par `python3 tools/media-pipeline/mp.py requests` sur `content/langues` dans le test, sans modifier `mp.py`).

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_scripts.py'
python3 tools/content-gen/scripts.py storyboards --langues content/langues --select zh-a0-salut-fr --out /tmp/sb && python3 -c "import json;d=json.load(open('/tmp/sb/zh-a0-salut-d1.json'));assert d['state']=='review' and len(d['scenes'])==7 and d['family']=='free'"
python3 tools/content-gen/scripts.py storyboards --learn content/learn --select 4e-maths-relatifs --out /tmp/sb && python3 -c "import json;d=json.load(open('/tmp/sb/4e-maths-relatifs.json'));assert d['family']=='reserved' and sum(s['dur'] for s in d['scenes'])+d['outro']['dur']<=30"
python3 tools/content-gen/scripts.py storyboards … --out /tmp/sb2 && diff -r /tmp/sb /tmp/sb2   # vide
```

## Cas limites
Dialogue avec 3 locuteurs ⇒ `blocked` (deux voix seulement en W9) ; réplique sans `tr` ⇒ `blocked` ; pack `en`/`es` sans variété décidée ⇒ `voiceClass.variety = null` et avertissement (D5) ; fiche sans bloc `example` ⇒ storyboard à 2 scènes ; caractères CJK et accents : jamais de normalisation du texte source (NFC seulement si déjà NFC).

## À ne pas faire
Aucun appel LLM ni réseau ; aucune réécriture pédagogique ; aucune scène « achetez » ; ne pas toucher `tools/media-pipeline`, `content/`.

## Rapport
`STATUT`, nombre de storyboards générables sur le contenu actuel (Langues : 12 packs ; Apprendre : cm2, 3e, tle-cd maths), cas `blocked` rencontrés, durée moyenne des shorts.
