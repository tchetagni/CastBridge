# w2-12 — Porte qualité v1 du contenu « Apprendre » et vocabulaire d'état unique

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (D16 : vocabulaire de CONTENT-VALIDATION.md)
> **Groupe : W2-A** (vague W2) · prérequis : aucun · porte : `python3 -m unittest discover -s tools/content-validation -p 'test_cbvalidate.py' && cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*LearnContent*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 2 · Effort M (≈ 2 j) · Statut PRÊT** (D16 : si le propriétaire ne tranche pas le vocabulaire, retenir celui de `docs/CONTENT-VALIDATION.md` : `draft → review → validated | needs-fix | rejected`). Branche `claude/sonnet-w2-12`. Rapport : `docs/agent-reports/sonnet-w2-12.md`.

## Objectif
Dix contrôles automatisables, répartis entre `LessonValidator` (Kotlin, lancé par `:core:checkLearnContent`, les tests et la TV) et `cbvalidate.py` (Python, base entière), avec un **seul** vocabulaire d'état ; `cbvalidate.py index --lot` exporte un pack à la fois.

## Pourquoi (preuves)
- `C/learn/LessonValidator.kt:77` : `alt` exigé seulement sur `illustration` → **273 figures d'exercices sans alt** ; `:92` règles de « fiche type » seulement si `pack.exam != null` (67/259 packs) ; `:104-112` barème sans `outOf == 20` ; 8 fichiers `epreuve-blanche*.json` sont des chapitres ordinaires (`bepc-maths/lessons/epreuve-blanche-2.json` somme 0).
- 259 packs sans `license`, `credits`, `disclaimer` (24 packs de droit, fiches santé) ; aucun contrôle de doublons, d'orthographe, de longueur de fiche (139 fiches < 600 caractères), de niveau de lecture.
- Trois vocabulaires : `C/learn/LessonModel.kt:11` (`DRAFT/REVIEWED/VALIDATED`), `docs/CONTENT-VALIDATION.md` § 1 (`review/validated/needs-fix/rejected`), `tools/content-validation/cbvalidate.py:39` (`reviewed → review`).
- `cbvalidate.py index` exporte 247 830 lignes d'un coup (`cmd_index`, `:411`).
- Audit : CO-1, CO-2, CO-7.

## Fichiers possédés
`C/learn/LessonValidator.kt`, `C/learn/LessonModel.kt`, `C/learn/LearnTool.kt`, `tools/content-validation/cbvalidate.py`, `tools/content-validation/test_cbvalidate.py`, `android/core/src/test/kotlin/castbridge/core/LearnContentTest.kt`, `docs/CONTENT-VALIDATION.md`, `docs/LEARN.md` (§ validation). **Hors zone** : `content/learn/**` (**ne corriger aucun pack** : les contrôles nouveaux sont en mode **avertissement** tant que la base n'est pas conforme, sauf ceux déjà respectés), `tools/trial-edition` (w2-13), `BaseContent.kt`.

## Étapes
1. Vocabulaire : `LessonModel` accepte `draft|review|validated|needs-fix|rejected` (+ alias `reviewed → review` à la lecture, avec avertissement) ; `cbvalidate.py` même table ; `docs/CONTENT-VALIDATION.md` et `docs/LEARN.md` alignés ; **aucune réécriture massive des packs** (ils sont tous `draft`, valide).
2. `LessonValidator` (erreur/avertissement comme indiqué) : (a) `alt` sur **toute** figure (`figure()` gagne un paramètre `alt`) — **avertissement** v1, erreur quand 0 manque ; (b) règles de fiche type pour tous les packs — avertissement ; (c) longueur totale de fiche 600-6 000 caractères — avertissement ; (d) `mockExams.outOf == 20` pour les packs francophones (`lang == fr`) / `== 100` ou déclaré sinon — erreur ; (e) un fichier de leçon nommé `epreuve-blanche*` qui n'est pas un `mockExam` — erreur ; (f) méta de pack `license`, `credits`, `disclaimer` requis pour `subject ∈ {droit, santé, vie-pratique}` — avertissement.
3. `cbvalidate.py` : nouvelle commande `quality` : doublons quasi exacts (SHA de l'énoncé normalisé ; identique ⇒ erreur, Jaccard > 0,9 ⇒ avertissement), niveau de lecture (longueur moyenne de phrase ≤ 20 mots B0-B2, ≤ 25 ensuite : avertissement), orthographe via `hunspell` **si installé** (liste blanche `tools/content-validation/dict-castbridge.txt`, avertissement) ; `index --lot learn/<pack>` ; `check` refuse un état hors vocabulaire ; sortie : tableau par pack + code 0/1 selon `--strict`.
4. `LearnTool check` : pack `validated` seulement si 100 % de ses fiches le sont (erreur sinon).
5. Tests : Python (`test_cbvalidate.py` : doublons, lecture, `--lot`, vocabulaire) ; Kotlin (`LearnContentTest` : cas synthétiques pour chaque nouvelle règle, et vérification que la base actuelle ne produit **que des avertissements** là où annoncé).
6. Rapport chiffré : nombre d'avertissements par règle sur la base actuelle (c'est l'entrée de w3-04, w3-07, w3-08).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.LearnContentTest' --tests 'castbridge.core.*Learn*'   # vert
cd android && gradle --offline :core:checkLearnContent        # 0 erreur (avertissements autorisés), sortie chiffrée
cd tools/content-validation && python3 -m unittest -q test_cbvalidate   # vert
python3 tools/content-validation/cbvalidate.py quality --lot learn/cm2-maths   # tableau, code 0 sans --strict
python3 tools/content-validation/cbvalidate.py index --lot learn/cm2-maths --out /tmp/cm2.jsonl && wc -l /tmp/cm2.jsonl   # lignes du seul pack
```

## Cas limites
- Packs anglophones : règles de longueur en mots, pas en caractères CJK ; `outOf` déclaré.
- Les packs de base (`BaseContent`) sont dérivés à la compilation : ne pas les valider deux fois.
- `hunspell` absent : la règle est sautée avec un message, jamais un échec.

## À ne pas faire
Pas de commit sur les branches partagées ; ne modifier aucun pack ; ne pas transformer un avertissement en erreur sur une règle que la base actuelle ne respecte pas ; pas de secret.

## Rapport
`STATUT`, tableau des 10 règles (où, niveau), chiffres sur la base actuelle, décision D16 appliquée.
