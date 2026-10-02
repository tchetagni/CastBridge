# Rapport : pilot « Langues » multilingue + adaptation TV sans micro (2026-10-02)

Commit `a052464` sur `integration/agents` (contenu + cœur). Aucune action serveur/TV/adb, aucun réseau, aucun secret lu ni affiché.

## Ce qui a été fait

**Cœur** (`core/langues/LangLogic.kt`)
- `LangCaps(mic, keyboard)` + `LangAdapt` : l'écoute (`co`) ne requiert jamais de micro ; seul `speak` enregistre l'apprenant. Sans micro → `listenAndRepeat` (écoute-répétition, sans enregistrement) ; sans clavier → la dictée passe en QCM (choix de l'écran). 3 tests dans `LanguesTest`.

**Contenu** (`content/langues/`, 46 packs texte, tous « free » CC BY-SA 4.0)
- A0 des 7 langues cibles (zh, ja, en, de, es, it, fr) × 3 thèmes (salut, nombres, famille) + phonétique zh (2 unités : tons, sons) ;
- Français (Québec) B1/B2 « préparation TCF Canada » (source en) ;
- Anglais C1/C2 « préparation IELTS / TOEFL / Cambridge » (source fr).
- `lots.json` (46 lots, v1/v2, hashes recalculés) + `embedded.txt` (starter CJK zh/ja uniquement).

**Multimédia** (généré, NON commité : `content/langues-media/` est gitignoré → à verser dans castbridge-content avec le manifeste de licences)
- audio Opus espeak-ng (242 pistes, marquées `synthetic`, licence voix GPL-3.0) ;
- 7 vidéos courtes H.264 480p (cartes de salutation, audio de synthèse inclus) ;
- 44 figures vectorielles `illustration` (traits, courbes des 4 tons, carte des sons, gestes, comptage).

**Générateurs reproductibles** : `tools/langues/gen_{a0_pilot,zh_prononciation,latin_langs,videos,fr_ca_tcf,en_cert}.py`.

## Tests
- `gradle :core:test --tests 'castbridge.core.LanguesTest'` : VERT (26 tests, dont `LangAdapt` ×3 et `allLanguagePacksParseAndValidate`).
- `gradle :core:buildLangLots -Pupdate` : 46 lots `.lot` + `lots-catalog.json` NON SIGNÉ (la signature est le travail de l'éditeur, hors dépôt).
- Suite `:core:test` complète : 1 échec flaky hors zone (`BtMuxTunnelTest.slowOpenIsWaitedForNotRepeated`, timing Bluetooth — passe seul en isolation).

## Écarts avec la vague 1 formelle (docs/agent-briefs/content-langues-w1.md)
1. Travail fait dans le dépôt **CastBridge** (`content/langues/`), pas dans `castbridge-content`.
2. Audio produit avec **espeak-ng**, ce qui contredit la décision du coordinateur du 2026-10-01 (« ne plus générer d'audio avec espeak-ng pour la production ; multimédia via outils Google + `media-requests.jsonl` »). → À traiter comme **PILOT / démonstration**, pas comme production.
3. Périmètre ≠ vague 1 (A0-A2) : c'est A0 (3 thèmes) + phonétique zh + 2 niveaux de certification (fr TCF, en CAE/CPE) ; aucun A1/A2 produit.
4. Médias lourds absents du worktree (gitignorés) → à déposer dans `castbridge-content` avec le manifeste de licences.

## Points pour le coordinateur / auditeur
- Chaque pack est validé par le vrai `LangValidator` (Kotlin) ; `LangLotBuilder` produit des lots déterministes, installables par `LangLotConsumer` (staging + renommage atomique, pas de recul de version).
- Famille « free » choisie pour tout (contenu original CC BY-SA 4.0) : à confirmer vs « reserved » (décision § 13 de `LANGUES.md`).
- Rendu des polices CJK sur TV 32 bits **non vérifié** (aucune TV dans cette session).
- Ordres de traits des caractères/kana : **indicatifs**, à faire valider par un locuteur natif.

## Risques et limites
- Voix synthétique robotique (tons du chinois approximatifs) : relecture native obligatoire avant production.
- Pas de voix naturelles ni de vidéo filmée ici (cf. `docs/LANGUES.md` § 8).
