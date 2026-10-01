# Couverture — Quiz du supérieur et des thèmes professionnels

Généré le 2026-10-01 par le pipeline `tools/quiz-bank` (branche `claude/content-quiz-superieur`). **Toutes** les questions sont `review`
(0 `approved`) : l'application les joue avec la marque « bêta » ; le propriétaire valide la qualité 3 mois après la distribution.
Packs : `content/quiz/dist/quiz-<parcours>-p<n>-v2.quiz.zip` (13 parcours, 40 lots, 4,5 Mo au total, le plus gros lot ≈ 190 Ko, très loin des 3 Mo). Total de ce périmètre : **50,619** questions.

## 1. Chiffres par parcours

| Parcours | Questions | calculées | factuelles | modèles | Parties sans répétition (par effectif) | par difficulté | Questions par difficulté 1/2/3/4/5 |
|---|---:|---:|---:|---:|---:|---:|---|
| Droit L1 (`l1-droit`) | 2 486 | 0 | 2 486 | 0 | 165 | 17 | 53 / 649 / 975 / 584 / 225 |
| Droit L2 (`l2-droit`) | 3 000 | 0 | 3 000 | 0 | 200 | 18 | 54 / 519 / 1045 / 978 / 404 |
| Droit L3 (+ science politique et RI en L2/L3) (`l3-droit`) | 2 325 | 0 | 2 325 | 0 | 155 | 18 | 55 / 381 / 818 / 699 / 372 |
| Économie / gestion L1 (`l1-eco`) | 6 490 | 5 947 | 543 | 55 | 432 | 393 | 1361 / 1525 / 1225 / 1200 / 1179 |
| Économie / gestion L2 (`l2-eco`) | 6 294 | 5 429 | 865 | 69 | 419 | 361 | 1125 / 1323 / 1592 / 1169 / 1085 |
| Économie / gestion L3 (`l3-eco`) | 861 | 0 | 861 | 0 | 57 | 0 | 0 / 37 / 174 / 431 / 219 |
| Maths et statistiques L1 (`l1-maths`) | 6 393 | 6 367 | 26 | 56 | 426 | 407 | 1327 / 1300 / 1281 / 1263 / 1222 |
| Maths et statistiques L2 (`l2-maths`) | 5 537 | 5 537 | 0 | 82 | 369 | 329 | 1157 / 1163 / 1149 / 1079 / 989 |
| Informatique L1 (`l1-info`) | 5 351 | 4 960 | 391 | 36 | 356 | 342 | 1026 / 1116 / 1101 / 1072 / 1036 |
| Informatique L2 (`l2-info`) | 5 495 | 5 073 | 422 | 42 | 366 | 342 | 1027 / 1077 / 1179 / 1156 / 1056 |
| Informatique L3 (`l3-info`) | 5 228 | 4 842 | 386 | 58 | 348 | 325 | 975 / 1041 / 1100 / 1094 / 1018 |
| Biologie L1 : santé publique de base (`l1-bio`) | 598 | 0 | 598 | 0 | 39 | 11 | 45 / 194 / 202 / 122 / 35 |
| Sociologie L1 : méthodologie (`l1-socio`) | 561 | 0 | 561 | 0 | 37 | 13 | 41 / 180 / 164 / 126 / 50 |

*Parties sans répétition* = questions ÷ 15 (règle `QuizBank.remainingFresh`). *Par difficulté* = 3 questions par niveau 1 à 5 par partie : c'est la vraie limite des
parcours de droit, parce que les questions de niveau 1 sont rares (≈ 50 par niveau de droit). Objectif du propriétaire : **300**.

## 2. Résumé honnête de l'objectif « 300 parties sans répétition »

| | Avant | Après (par effectif) | Atteint 300 ? |
|---|---:|---:|---|
| Droit L1 | 7 | **165** | non (manque ≈ 2 000 questions) |
| Droit L2 | — (parcours nouveau) | **200** | non (manque ≈ 1 500) |
| Droit L3 | — (parcours nouveau) | **155** | non (manque ≈ 2 200) |
| Économie L1 / L2 | 325 / — | 432 / 419 | **oui** |
| Économie L3 | — | 57 | non (≈ 3 600) |
| Maths L1 / L2 | 349 / — | 426 / 369 | **oui** |
| Informatique L1 / L2 / L3 | — | 356 / 366 / 348 | **oui** |
| Santé publique (biologie L1) | — | 39 | non |
| Méthodologie (sociologie L1) | — | 37 | non |

Le droit passe de 106 à **7 811 questions** en trois niveaux (≈ 7 000 de plus), soit environ la moitié des ≈ 13 500 que demanderait 300 parties à chacun des trois niveaux.
Le jeu de l'objectif en un seul parcours (L1 droit) a été sacrifié au profit d'une répartition réaliste par niveau : les matières sont affectées au niveau où on les enseigne habituellement,
et un niveau n'a donc que ses propres matières.

Ce qui tient les 300 parties en maths, informatique et économie : ≈ 95 % de questions **calculées** (variantes numériques de 36 à 82 modèles par parcours, aucun modèle > 8 % d'un parcours,
chaque réponse recoupée dans le code par une 2ᵉ méthode). **Ce n'est pas du contenu éditorial** : on change les nombres, pas les notions (voir l'avertissement du § 6 ter de `docs/QUIZ.md`).
Les parcours de droit, santé publique et méthodologie sont, eux, 100 % éditoriaux (faits, définitions, cas pratiques rédigés).

## 3. Contenu par thème

**Droit** (7 811 questions, 100 % factuelles, 0 calculées)
- *L1* : introduction au droit (notion, sources, hiérarchie des normes, branches, preuve, personnes, famille, successions, biens, organisation judiciaire et acteurs), droit constitutionnel et institutions (théorie de l'État, régimes, scrutins avec petits calculs de sièges, contrôle de constitutionnalité, libertés publiques, décentralisation), cas pratiques et qualifications (≈ 1 000).
- *L2* : droit des obligations (contrat, inexécution, responsabilité civile, quasi-contrats, extinction), biens et sûretés (principes), droit administratif général, droit pénal général et procédure pénale, science politique ; cas pratiques (≈ 1 000).
- *L3* : droit du travail (principes + cas), droit commercial et OHADA (organisation, commerçant, fonds de commerce, sociétés, effets de commerce, sûretés, procédures collectives, voies d'exécution, arbitrage), droit international public, organisations internationales, relations internationales ; cas pratiques (≈ 1 000).
- Formes : tables de définitions (sens direct/inverse), classements, QCM rédigés, cas pratiques ; aucune question ne cite de numéro d'article, de date de loi, de montant ni de seuil ; les règles propres à un pays sont formulées « en droit français » ou « en principe ».
- Cameroun : seulement l'organisation générale (État unitaire décentralisé, Parlement bicaméral, Conseil constitutionnel, Cour suprême, bijuridisme…) ; aucun droit camerounais détaillé n'a été osé.

**Économie, gestion et comptabilité** : micro et macro, monnaie et banque, commerce international, CEMAC / zone franc (institutions seulement), développement, gestion (management, marketing, RH, stratégie), comptabilité générale (principes, bilan, résultat, régularisations, SIG, ratios, classes 1 à 7 SYSCOHADA), finance d'entreprise ; générateurs : élasticités, surplus, Cournot, indices, multiplicateurs, IS-LM, change, parité, intérêts, annuités, VAN/TRI, CMPC, DuPont, Gini, régression, stocks CMUP/FIFO, amortissements, SIG, FRNG/BFR, seuil de rentabilité, rapprochement bancaire, TVA à partir d'un taux **donné dans l'énoncé**.

**Mathématiques et statistiques** : L1 (+ 23 modèles de statistiques et probabilités : moyennes, quartiles, variance, covariance, régression, Bayes, binomiale, géométrique, Poisson) ; L2 (82 modèles : intégrales généralisées et doubles, séries entières et de Fourier, dérivées partielles, extrema, équations différentielles, diagonalisation, espaces euclidiens, lois continues, intervalles de confiance, tests, groupes, optimisation linéaire, graphes).

**Informatique et culture numérique** : L1 (bases 2/8/10/16, complément à deux, logique, unités, débits, ASCII, images, algorithmique de base, culture numérique), L2 (adressage IPv4/CIDR, RAID, ordonnancement, pagination, cache, SQL recoupé avec sqlite3, récursivité), L3 (complexité, tris, arbres, graphes, hachage, RSA jouet, Huffman, automates, piles/files).

**Santé publique de base** (≈ 600) et **méthodologie du travail universitaire** (≈ 560) : notions de cours uniquement ; aucune statistique, posologie ni conseil individuel.

## 4. Écarts et lacunes (honnêtes)

1. **Droit : 300 parties non atteintes** (155 à 200 selon le niveau). Manquent surtout les questions de **niveau 1 et 2** : un partie de 15 questions en réclame 3 de niveau 1, il n'y en a qu'≈ 50 par niveau ; en pratique le tirage reprend alors le niveau voisin (la règle « parties par difficulté » donne ≈ 17-18 parties).
2. **Économie L3, santé publique, méthodologie** : volumes très inférieurs à 4 500 (861, 598, 561).
3. **Thèmes sans champ dans l'application** : *code de la route de base* n'est pas traité (aucune filière correspondante dans `QuizCatalog`, que ce périmètre ne peut pas modifier). *Gestion et comptabilité* est classée dans la filière `economie` (catégorie « Gestion et comptabilité »), *sciences politiques et relations internationales* dans `droit` (L2 / L3), *santé publique* dans `biologie` (L1), *méthodologie* dans `sociologie` (L1).
4. **Variété des parcours calculés** : en maths, informatique et économie, ≈ 95 % des questions sont des variantes de modèles ; la variété des notions est celle de 36 à 82 modèles par parcours.
5. **Pas de L3 mathématiques** (le cahier des charges s'arrêtait à L2), pas de L2 / L3 d'autres filières.
6. Les entiers de 4 chiffres ne sont pas groupés (« 8160 »), selon la règle `fr()` du pipeline existant (usage français).
7. **Gradle** : voir le compte rendu (le plugin Android ne se résout pas dans cet environnement, ni un harnais `:core` seul : erreur 429 du dépôt Maven). Les tests Kotlin n'ont donc pas pu être relancés ici ; ce périmètre ne modifie aucun fichier Kotlin ni la banque embarquée de l'APK.

## 5. Relecteurs nécessaires (ordre de priorité)

**Juriste / enseignant de droit (indispensable avant toute validation `approved`)** — tout le droit est `review` ; points les plus sensibles :
- Toutes les formulations « en droit français » (réforme du droit des contrats : imprévision, dol du tiers, clause réputée non écrite ; sûretés : pacte commissoire, garantie autonome, réserve de propriété ; droit du travail : prise d'acte, droit de retrait, faute inexcusable, nullité du licenciement d'un gréviste ; droit pénal : classement sans suite, dispense de peine, opportunité des poursuites) : à confirmer ou à adapter au droit **camerounais / OHADA**.
- Les questions marquées Cameroun ou OHADA (Conseil constitutionnel, Cour suprême, titre foncier, CCJA, ERSUMA, effets de commerce, aval, protêt, procédures collectives, voies d'exécution, arbitrage).
- Les niveaux 5 (nuances doctrinales : effet dévolutif, concours idéal, emprunt de criminalité, acte de gouvernement, imprévision, compétence-compétence, immunités des chefs d'État).
- Le droit international (Conventions de Vienne, immunités, CIJ / CPI, chiffres stables comme les 12 milles ou la composition des organes), les dates et sièges d'organisations (UA, BDEAC, CEMAC) et les attributions d'auteurs en science politique et relations internationales.
- Les définitions volontairement raccourcies (limite de 100 caractères par choix) et les mauvaises réponses **rallongées** pour équilibrer les longueurs : certaines sont verbeuses.

**Enseignant d'économie / de comptabilité** : formules de ratios et de SIG qui varient selon les manuels, taux de marge et de marque, résultat exceptionnel (« hors activités ordinaires » SYSCOHADA), classes 1 à 7 du plan comptable, écarts, consolidation, attributions d'auteurs peu courantes en management, conventions de calcul (escompte exclu du coût d'achat, arrondis).

**Enseignant de mathématiques / statistiques** : conventions d'énoncés (quartiles, a₀ des séries de Fourier), valeurs critiques 1,645 / 1,96 / 2,576 écrites dans les énoncés, formulations des justifications de diagonalisabilité, intégrales vérifiées numériquement.

**Enseignant d'informatique** : conventions d'énoncés (hauteur d'arbre en arêtes, milieu de dichotomie, passe de tri, ordre alphabétique BFS/DFS, tous les processus arrivent à t = 0, formule du cache), unités Ko / Mo, mauvaises réponses « erreurs d'étudiant ».

**Professionnel de santé** : sensibilité / spécificité, immunité active et passive, formulation « indétectable = non transmissible », gestes de premiers secours (à comparer au référentiel local), vecteurs et agents des maladies tropicales.

**Enseignant de méthodologie** : validité interne / externe, échantillonnage raisonné, biais cognitifs, citations de Durkheim, Weber et Popper.

## 6. Reproduire

```sh
python3.12 tools/quiz-bank/quizbank.py check
python3.12 tools/quiz-bank/quizbank.py build --courses superieur --version 2   # réécrit seulement ces 13 parcours
python3.12 -m unittest discover -s tools/quiz-bank
python3.12 tools/quiz-bank/sup_check.py qb.facts.sup_droit_oblig          # un module seul
```
(Python 3.12 : `qb/facts/africa.py`, d'un autre périmètre, utilise une syntaxe f-string refusée par 3.11.)
Fichiers : `qb/gen/sup_register.py` (parcours), `qb/facts/sup_kit.py` (outils), `qb/facts/sup_*.py` (faits), `qb/gen/sup_*.py` (générateurs). Les anciens lots `-v1` de L1 droit, L1 éco et L1 maths sont remplacés par les `-v2` ; les packs des autres périmètres n'ont pas été touchés.
