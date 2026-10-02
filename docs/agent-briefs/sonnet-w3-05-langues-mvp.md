# w3-05 — « Langues » : tranche minimale 1 langue × A0-A1 (texte), générateur et validateur

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : BLOQUÉ (D14 : première langue)
> **Groupe : W3-C** (vague W3) · prérequis : aucun · porte : `python3 -m unittest discover -s tools/langues -p 'test_languelib.py'`
> **Jauge : ≈ 800 k jetons entrée / 40 k sortie** (effort L) · audit Opus : non

**Vague 3 · Effort L (≈ 3 j) · Statut BLOQUÉ** — **D14** : quelle première langue ? (recommandation : anglais, départ français ; sinon chinois). Sans réponse : construire `languelib.py` et le validateur, produire **une** unité d'exemple par langue pour prouver la chaîne, et poser `QUESTION:`. Branche `claude/sonnet-w3-05`. Rapport : `docs/agent-reports/sonnet-w3-05.md`.

## Objectif
1. `tools/langues/languelib.py` : assistant de rédaction (comme `tools/learn-authoring/lessonlib.py` pour Apprendre) : création d'unité, vocabulaire, dialogue, exercices (QCM, appariement, ordre des mots, dictée → modèle), cartes, références `m:<id>` vers `media.json` avec `synthetic: true` par défaut ; `check.sh`.
2. ≈ 18 unités (6 A0 + 12 A1) pour la langue choisie, famille `free` (CC BY-SA 4.0, contenu original), **sans audio** (les lignes à synthétiser sont listées dans `media.json` comme demandes, fichiers absents : c'est au propriétaire d'exécuter `tts_synth.py` hors cloud).
3. Lots construits (`:core:buildLangLots` ou équivalent), budget texte ≤ 3 Mo, `embedded.txt` inchangé sauf décision.
4. Le manifeste d'essai couvre ces cellules (`trial_edition.py check` : 2 cellules de moins vides).

## Pourquoi (preuves)
- `content/langues/zh-a0-salut-fr` : 1 unité, 4 exercices, `media.json` = exemple sans fichiers ; `docs/LANGUES.md` § 11-13 : décisions prises (CC BY-SA, voix neuronales libres, quota 500 Mo) ; `budget.json` : A0 3-6 %, A1 9-12 % de ≈ 1 Go par langue ⇒ texte minuscule.
- Aucun générateur de leçons Langues (`tools/langues/gen-graph.py` ne fait que les graphes) ; `C/langues/` : modèle, analyseur, validateur de licences, correction, répétition espacée, positionnement (21 tests).
- `tools/trial-edition/trial_edition.py check` échoue sur 55 cellules Langues vides.
- Audit : CO-6, M4, M6.

## Fichiers possédés
`content/langues/<lang>-a0-*`, `content/langues/<lang>-a1-*` (nouveaux), nouveau `tools/langues/languelib.py`, nouveau `tools/langues/test_languelib.py`, nouveau `tools/langues/check.sh`, `C/langues/LangValidator.kt` (**règles de validation seulement**, additives : longueur ≤ 650 caractères par bloc, lecture obligatoire zh/ja), `content/langues/lots.json`, `content/langues/embedded.txt`, `docs/LANGUES.md` (§ 14 journal). **Hors zone** : `LanguesActivity.kt` (w2-11), `tts_synth.py`, `trial_edition.py` (w2-13), `budget.json`.

## Étapes
1. Lire `docs/LANGUES.md` § 4-6 (modèle de contenu, critères par niveau), `C/langues/LangPack.kt` (format), le pack d'exemple.
2. `languelib.py` (stdlib) : API Python pour produire `langue.json` + `media.json` conformes ; tests sur le pack d'exemple (relecture identique) ; `check.sh` lance `gradle :core:test --tests 'castbridge.core.LanguesTest'` + validation du pack.
3. Rédiger les unités (contenu **original**, pas de recopie de manuel ; vocabulaire de fréquence libre ; dialogues ≥ 2 répliques ; 8 exercices par unité ; `state: review` ; `author` « CastBridge (brouillon IA) » ; licence `CASTBRIDGE-ORIGINAL` publiée CC BY-SA 4.0 ; crédits).
4. `media.json` : une entrée par ligne audio attendue (`engine: kokoro`, `synthetic: true`, `file` absent ⇒ l'app affiche « Audio : à recevoir » via w2-11).
5. Lots : `lots.json` v1 pour chaque pack ; vérifier ≤ 3 Mo ; `trial_edition.py check` (si w2-13 fusionné, avec `--only learn,quiz,langues`).

## Critères d'acceptation
```sh
python3 -m unittest discover -s tools/langues                                  # vert
cd android && gradle --offline :core:test --tests 'castbridge.core.LanguesTest' --tests 'castbridge.core.langues.*'   # vert
cd android && gradle --offline :core:checkLangContent 2>/dev/null || python3 tools/langues/languelib.py check content/langues   # 0 erreur
ls -d content/langues/<lang>-a0-* content/langues/<lang>-a1-* | wc -l           # ≥ 18
python3 tools/content-budget/langues_budget.py                                   # dans le budget
```

## Cas limites
- Chinois/japonais : lecture (pinyin/kana) obligatoire ; données de traits (KanjiVG CC BY-SA 3.0 accepté ; Make Me a Hanzi **non** : licence à examiner) — si chinois choisi, pas de traits dans cette tranche.
- Ne pas mélanger `free` et `reserved` dans un lot.

## À ne pas faire
Pas de commit sur les branches partagées, pas d'audio synthétisé dans le dépôt (le cloud n'a pas les modèles ; le propriétaire exécute `tts_synth.py`), pas de recopie de sources non libres, pas de secret.

## Rapport
`STATUT: BLOQUÉ` (D14) ou `TERMINÉ`, nombre d'unités/exercices/lignes audio attendues, tailles, commandes.
