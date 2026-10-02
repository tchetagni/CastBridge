# w3-04 — Boucle de relecture enseignants : exports par pack, rapports QA par lot, import des décisions

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w2-12 ; D16 relecteurs)
> **Groupe : W3-A** (vague W3) · prérequis : w2-12 · porte : `python3 -m unittest discover -s tools/content-validation -p 'test_*.py'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 3 · Effort M (≈ 2 j d'agent, puis le temps des enseignants) · Statut PRÊT** (après w2-12 ; D16 pour recruter les relecteurs). Branche `claude/sonnet-w3-04`. Rapport : `docs/agent-reports/sonnet-w3-04.md`.

## Objectif
1. Un export CSV **par pack** (`id;state;reviewer;date;note` + colonnes de lecture : classe, matière, titre, extrait) pour les **488 fiches embarquées dans l'APK** (2 premières fiches de chaque pack, `BaseContent.LESSONS_PER_PACK = 2`) et leurs exercices, en priorité les classes d'examen (cm2, 3e, tle-cd, class6, form5).
2. Un rapport QA (`content/qa/learn-<scope>-v<n>.json`, gabarit `tools/pedagogy-report/sample-report.json`) pour chacun des 32 lots, produit par l'agent avec la grille `docs/PEDAGOGY-RUBRIC.md` (relecture adverse : erreurs factuelles, consignes ambiguës, « Bravo » sur mauvaise réponse, etc.), **sans modifier les packs**.
3. Le chemin d'import est prouvé sur un CSV d'exemple : `import-csv` → `check` → `apply --dry-run`.

## Pourquoi (preuves)
- `content/validation/` et `content/qa/` **n'existent pas** ; `cbvalidate.py check` → « 0 décisions, 247 830 éléments » ; `docs/LEARN-REVIEW.md` (8 135 lignes) n'est pas actionnable par un enseignant.
- 259 packs / 2 537 fiches `draft`, auteur « CastBridge (brouillon IA) » ; 488 fiches sont **dans l'APK** via `BaseContent` : irrétractables sans mise à jour.
- `tools/content-validation/cbvalidate.py` : `index`, `import-csv FILE (id;state;reviewer;date;note)`, `check`, `apply`, `rebuild` existent et sont testés (10 tests) ; `index --lot` arrive avec w2-12.
- `tools/pedagogy-report/validate_report.py` + `sample-report.json` : format défini, 0 rapport produit.
- Audit : CO-1, calendrier § 11 de l'audit contenu.

## Fichiers possédés
Nouveau `tools/content-validation/review_export.py`, nouveaux `content/validation/EXEMPLE-relecture.csv` et `content/validation/README.md`, nouveaux `content/qa/learn-<scope>-v<n>.json` (32), `docs/LEARN-REVIEW.md` (**en-tête** : mode d'emploi, lien vers les CSV), `tools/pedagogy-report/**` (corrections d'outil seulement). **Hors zone** : `content/learn/**` (aucune modification de pack), `cbvalidate.py` (w2-12), `LessonValidator`.

## Étapes
1. `review_export.py` : pour un pack, lit `pack.json` + `lessons/*.json`, sélectionne les N premières fiches (option `--embedded` = celles de `BaseContent`, 2 par pack) et leurs exercices, écrit `build/review/<pack>.csv` (séparateur `;`, UTF-8 BOM pour LibreOffice/Excel, colonnes : `id;type;classe;matière;titre;extrait(200 c);state;reviewer;date;note`) ; option `--all-embedded` produit les 244 CSV + un `INDEX.csv` ; **ne lit que** le contenu, n'écrit que sous `build/`.
2. `content/validation/README.md` (français, 1 page) : qui relit quoi, comment remplir `state` (`validated` / `needs-fix` / `rejected`), où déposer le CSV, commande d'import, règle « ne jamais éditer une fiche directement ».
3. `EXEMPLE-relecture.csv` : 10 lignes réelles de `cm2-maths` avec `state` rempli ; prouver `python3 tools/content-validation/cbvalidate.py import-csv content/validation/EXEMPLE-relecture.csv --into /tmp/ex.jsonl && cbvalidate.py check --decisions /tmp/ex.jsonl && cbvalidate.py apply --dry-run` (adapter aux options réelles de l'outil).
4. Rapports QA : pour chaque lot, remplir le gabarit (10 critères, décision, 3-10 points précis avec `id` de fiche) ; `python3 tools/pedagogy-report/validate_report.py content/qa/*.json` doit passer ; prioriser les erreurs qui bloqueraient une vente (faits faux, réponses fausses, consignes ambiguës).
5. Tableau de synthèse dans le rapport : par lot, nombre de points, décision, les 20 défauts les plus graves (id, phrase, correction proposée) — entrée de w3-07/w3-08.

## Critères d'acceptation
```sh
python3 tools/content-validation/review_export.py --pack cm2-maths --embedded --out build/review && head -3 build/review/cm2-maths.csv
python3 tools/content-validation/review_export.py --all-embedded --out build/review && ls build/review/*.csv | wc -l   # 244
python3 tools/pedagogy-report/validate_report.py content/qa/*.json      # 32 rapports valides
python3 tools/content-validation/cbvalidate.py import-csv content/validation/EXEMPLE-relecture.csv --into /tmp/ex.jsonl   # OK
git diff --stat content/learn/                                            # vide
```

## Cas limites
- Les packs de base sont dérivés : les ids des CSV sont ceux des **packs complets** (même ids, `BaseContent` les réutilise).
- Les packs anglophones : CSV en anglais pour le relecteur anglophone (colonnes identiques).

## À ne pas faire
Pas de commit sur les branches partagées, pas de modification de contenu, pas de secret ; ne pas prétendre qu'un contenu est « validé » : les décisions sont celles des enseignants, l'agent ne remplit que l'exemple et les rapports QA.

## Rapport
`STATUT`, nombre de CSV, synthèse QA (tableau), top 20 des défauts, instructions pour le propriétaire (remettre les CSV aux enseignants).
