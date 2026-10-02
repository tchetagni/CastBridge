# w9-01 — Schéma de provenance des assets générés et outil `provenance.py`

**Vague 9a · Effort S (≈ 1 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/GUIDE-PRODUCTION-LOTS-GENERATIFS-2026-10-02.md` § 2.1-2.2 ; contrat C2 de `SONNET-WAVE9-INDEX.md`. Branche `claude/sonnet-w9-01`. Rapport : `docs/agent-reports/sonnet-w9-01.md`.

## Objectif
Un enregistrement de provenance **par asset** (audio, image, vidéo) qui porte générateur, modèle, version, empreinte du prompt, graine, date, licences (moteur **et** voix), `synthetic`, `humanReplacement`, famille et contrôles ; un outil qui l'écrit, le valide et le **fusionne** dans les manifestes existants sans casser leurs lecteurs : `media.json` d'un pack Langues (champs existants `id, file, kind, bytes, durationMs, license, engine, synthetic, voiceLicense, lang, source`, lus par `LangValidator`) et `content/MEDIA-MANIFEST.json` (champs de `docs/MEDIA-POLICY.md` § 2).

## Pourquoi (preuves)
- `docs/LANGUES.md:243-249` (champs de `media.json` et règle « moteur ⇒ `synthetic: true` ») ; `content/langues/ja-a0-salut-fr/media.json` (forme réelle produite par `gen_a0_pilot.py`).
- `docs/MEDIA-POLICY.md:23-49` (manifeste global : `origin`, `generator`, `licence`, `sha256`, `bytes`, `lot`, `lessons`, `alt`, `caption`, `transcript`, `durationS`, `poster`, `flashHz`, `depictsPerson`).
- `docs/MEDIA-PIPELINE.md:42` (manifeste de provenance de la chaîne Google : `requestFingerprint`, `clonage_vocal`, `imitation_personne_reelle`, familles non mélangées) : **mêmes noms de champs** pour que les deux chaînes convergent.
- `tools/media-pipeline/mp/manifest.py` : lire sa structure avant d'écrire la vôtre ; ne pas la dupliquer, l'étendre (import permis en lecture seule).

## Fichiers possédés
`tools/content-gen/provenance.py`, `tools/content-gen/schema/asset-manifest.schema.json`, `tools/content-gen/tests/test_provenance.py`. **Hors zone** : tout `tools/media-pipeline/`, les `media.json` réels de `content/langues/`, `content/MEDIA-MANIFEST.json` (les tests travaillent sur des copies temporaires).

## Étapes
1. `schema/asset-manifest.schema.json` : JSON Schema draft-07 écrit à la main (aucune bibliothèque) décrivant C2 ; `required` = `id, kind, lang, family, licence, requestFingerprint, generator, backend, producedAt, synthetic, humanReplacement, redistributable, sha256, bytes` ; énumérations fermées pour `kind`, `family`, `licence`, `humanReplacement`.
2. `provenance.py` (bibliothèque standard) : sous-commandes
   - `write --out work/provenance/<fp>.json --from <json ou options>` : complète `sha256`, `bytes` en lisant le fichier, `producedAt` (fuseau local, ISO 8601), `host` (`platform.node()`), `generator` (`<script>@<git rev-parse --short HEAD>` ou `@worktree`) ; refuse un `family`/`licence` incohérent (`free` ⇒ `CC-BY-SA-4.0|CC0|PD|CC-BY-4.0|CC-BY-SA-3.0` ; `reserved` ⇒ `CASTBRIDGE-ORIGINAL|CC0|PD`) ; refuse `redistributable: false` avec `family` quelconque si `--for-lot` (un asset non redistribuable n'entre jamais dans un lot).
   - `check <fichiers…>` : validation **sans bibliothèque de schéma** (vérificateur minimal maison : types, required, enum, pattern) ; code 0/2 ; messages en français avec le chemin du champ.
   - `merge-langues --provenance DIR --media media.json --out media.json` : pour chaque enregistrement, écrit/actualise l'entrée `media.json` (`id, file, kind, bytes, durationMs, license, engine = "<backend> (<voiceId>)", synthetic, voiceLicense, lang, source`) **plus** un sous-objet additif `provenance` (C2 sans `transcript` ni `qc`, pour rester léger) ; conserve les entrées non touchées ; ordre par `id` ; refuse un `id` présent avec un `sha256` différent sans `--replace` (c'est le cas « remplacement par voix humaine » : exige alors `humanReplacement: done` et écrit `replacedSyntheticSha256`).
   - `merge-manifest --provenance DIR --manifest MEDIA-MANIFEST.json --lot learn:cm2-media --lessons …` : entrée `assets[]` conforme à MEDIA-POLICY (`origin: "generated"`, `generator`, `licence: "CastBridge-original"` pour `reserved`, `author: "content-gen"`, `alt`, `caption`, `transcript`, `durationS`, `poster`, `flashHz`), + `provenance` additif.
   - `list --provenance DIR [--synthetic-pending]` : tableau (id, kind, backend, voix, licence, humanReplacement).
3. Tests (`unittest`, fichiers temporaires) : écriture complète, `check` rejette chaque champ requis manquant et chaque enum invalide, familles incohérentes refusées, fusion Langues idempotente (deux fusions = mêmes octets), remplacement humain exige `--replace`, fusion manifeste produit une entrée acceptée par `python3 tools/content-media/check_media.py` sur un contenu de test (fichier réel créé par `ffmpeg` si présent, sinon test sauté avec raison).

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_provenance.py'          # vert
python3 tools/content-gen/provenance.py check tools/content-gen/tests/fixtures/*.json   # 0 ; un fixture volontairement faux → 2 avec message en français
python3 tools/content-gen/provenance.py merge-langues --provenance <tmp> --media <copie de content/langues/ja-a0-salut-fr/media.json> --out <tmp>/media.json && diff <(python3 -c "import json;print(sorted(json.load(open('<tmp>/media.json'))['media'][0].keys()))") <(echo "['bytes', 'durationMs', 'engine', 'file', 'id', 'kind', 'lang', 'license', 'provenance', 'source', 'synthetic', 'voiceLicense']")
grep -c "import requests\|urllib" tools/content-gen/provenance.py   # 0
```

## Cas limites
`git` absent ⇒ `@worktree` ; fichier d'asset absent ⇒ refus (jamais de `sha256` inventé) ; `media.json` sans `format` ⇒ créé avec `format: 1` ; un enregistrement `kind: video` sans `poster` ni `subtitles` ⇒ refus ; `producedAt` sans fuseau ⇒ refus ; champs inconnus ⇒ conservés (additifs) mais signalés en avertissement.

## À ne pas faire
Pas de dépendance (`jsonschema`, `pydantic`) ; ne pas modifier `tools/media-pipeline` ; ne pas écrire dans `content/` ; pas de réseau ; pas de prompt en clair dans l'enregistrement (seulement `promptSha256`).

## Rapport
`STATUT`, liste des champs du schéma, commandes exécutées, nombre de tests, points laissés ouverts (champ manquant demandé par w9-09/w9-10).
