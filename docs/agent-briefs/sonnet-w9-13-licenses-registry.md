# w9-13 — Registre des licences des moteurs, modèles, voix et polices (`licenses.json`), vérificateur `licenses.py`, brouillon juridique

**Vague 9a · Effort S (≈ 1 j) · Modèle : sonnet · Statut PRÊT.** Conception : guide § 3 (tableaux), § 7 ; contrats C2, C3. Branche `claude/sonnet-w9-13`. Rapport : `docs/agent-reports/sonnet-w9-13.md`.

## Objectif
(1) `tools/content-gen/licenses.json` : un enregistrement par **moteur**, **modèle**, **voix** et **police** : `id`, `kind` (`engine|model|voice|font|service`), `name`, `version`, `licence` (SPDX quand il existe : `Apache-2.0`, `MIT`, `CC0-1.0`, `CC-BY-4.0`, `OFL-1.1`, `GPL-3.0-only`, `CPML`, `CC-BY-NC-4.0`, `Apple-SLA`, `Google-Cloud-Terms`, …), `licenceUrl`, `commercialRedistribution` (`yes|no|conditions`), `attributionRequired`, `attributionText`, `cloningAllowedButForbiddenHere` (bool, info), `verifiedAt` (**`null` à la livraison** : rien n'est « vérifié » par un agent ; le propriétaire date au téléchargement), `evidencePath` (chemin attendu du `LICENSE`/`MODEL_CARD` sous `~/.cache/castbridge-models/…`), `status` (`candidate|approved|excluded`), `reason`. Contenu initial = tableaux du guide § 3.1-3.3 avec `status: candidate` (Kokoro, Piper + voix `de_DE-thorsten`, `fr_FR-siwis`, `en_GB-alba`, `es_ES-davefx`, `it_IT-paola`, `zh_CN-huayan`, MeloTTS, CosyVoice 2, espeak-ng, Noto Sans, Noto Sans CJK, FLUX.1 schnell, whisper, pypinyin, pykakasi, mlx-whisper) et `status: excluded` avec `reason` (XTTS/CPML, F5-TTS, Fish-Speech, MMS-TTS, FLUX dev/Kontext dev, Wav2Lip, Edge-TTS, voix macOS `say` pour redistribution, Remotion au-delà de 3 personnes) ; services (Google TTS, Azure, Polly, ElevenLabs) en `candidate` avec `commercialRedistribution: conditions`. (2) `licenses.py check [--for-lot] [--models-dir DIR]` : refuse tout `licence` NC/ND ou `commercialRedistribution: no` pour un asset destiné à un lot ; refuse un `status != approved` quand `--strict` (production) ; avec `--models-dir`, vérifie que `evidencePath` existe pour chaque entrée `approved` (sinon « preuve de licence absente ») ; `licenses.py voices --lang zh` : voix utilisables ; `licenses.py attribution --ids …` : texte d'attribution agrégé (utilisé par w9-10). (3) `docs/content-production/LICENCES-GENERATIF.md` : brouillon **« à faire valider par un juriste »** : droit d'auteur des sorties IA (incertitude), compatibilité CC BY-SA 4.0 des packs Langues avec des médias générés, licence du moteur ≠ licence de la sortie (hypothèse GPL/espeak déjà notée `docs/FREE-CONTENT.md:69`), voix Apple non redistribuables, clonage interdit, mineurs, divulgation IA (badge), conditions des API à archiver datées, polices OFL incrustées, questions ouvertes numérotées.

## Pourquoi (preuves)
- `tools/langues/tts_synth.py:11-20` (liste `FREE` et voix « à vérifier au téléchargement ») ; `docs/LANGUES.md:289-292` (politique : maximum de licences libres, NC/ND exclues, licence de la voix inscrite, jamais de voix clonée) ; `tools/media-pipeline/templates/registry/engines-registry.json` et `voices-registry.json` (registres Google vides : **même esprit**, ne pas les modifier ; `licenses.py` doit pouvoir les lire en plus) ; `tools/media-pipeline/templates/legal/*.md` (gabarits existants à référencer, pas à dupliquer).
- Guide § 3 (tableaux comparatifs : source des entrées) et § 7 (liste de contrôle).

## Fichiers possédés
`tools/content-gen/licenses.json`, `tools/content-gen/licenses.py`, `tools/content-gen/tests/test_licenses.py`, `docs/content-production/LICENCES-GENERATIF.md`.

## Étapes
1. Rédiger `licenses.json` (clés triées) ; chaque entrée `excluded` porte une `reason` en français d'une phrase.
2. `licenses.py` : chargement, validation du schéma minimal, règles ci-dessus, lecture optionnelle des registres `media-pipeline` (voix `approved` Google ⇒ `service` conditions), codes 0/2, messages en français.
3. Document juridique : questions, pas de réponses ; renvois aux gabarits `tools/media-pipeline/templates/legal/`.
4. Tests : NC refusé pour lot ; `approved` sans preuve refusé en `--models-dir` ; `say` refusé `--for-lot` même `approved` ; `voices --lang de` ne propose pas Kokoro ; attribution agrégée stable (octets).

## Critères d'acceptation (hors ligne)
```sh
python3 -m unittest discover -s tools/content-gen/tests -p 'test_licenses.py'
python3 tools/content-gen/licenses.py check ; echo $?   # 0 (registre cohérent, rien d'approuvé encore)
python3 tools/content-gen/licenses.py check --strict ; echo $?   # 2 « aucun moteur approuvé : le propriétaire date et approuve au téléchargement »
python3 -c "import json;d=json.load(open('tools/content-gen/licenses.json'));assert all(e['verifiedAt'] is None for e in d['entries'])"
```

## Cas limites
Licence double (code MIT, poids CC-BY-NC) ⇒ deux entrées liées (`model` et `weights`) et c'est la plus restrictive qui compte ; licence d'une voix Piper « inconnue » ⇒ `candidate` avec `reason` et refus `--strict` ; entrée sans `licenceUrl` ⇒ avertissement.

## À ne pas faire
Ne jamais mettre `verifiedAt` ni `approved` (décision et date du propriétaire) ; aucun téléchargement ; aucun avis juridique affirmatif ; ne pas modifier `tools/media-pipeline`.

## Rapport
`STATUT`, nombre d'entrées par statut, les licences dont vous n'êtes pas sûr (liste explicite « de mémoire, à confronter »), questions juridiques numérotées.
