# Rapport — Quiz Supérieur, Mathématiques L3 (`l3-maths`)

Branche : `claude/quiz-higher-mathematiques` (depuis `origin/integration/agents`). Aucune Pull Request. `content/quiz/dist`, `android/` et `backend/` ne sont pas modifiés.

## Livrable

4 fichiers `content/quiz/batches/higher-l3-mathematiques-01.json` à `-04.json`, 125 questions chacun, soit **500 questions** (statut `review` à l'import, relecture humaine nécessaire). Chaque fichier passe `python3 tools/quiz-bank/quizbank.py import --dry-run` avec « 125 questions valides importées » et aucune ligne de problème (code de sortie 0). Le dépôt exige Python 3.12 ou plus (f-strings imbriquées dans `qb/gen/`) : j'ai utilisé `python3.12`.

## Comptes par sous-thème (plan du § 9 du cahier, 26 sous-thèmes)

| Sous-thème | Questions |
|---|---|
| Espaces normés et topologie des espaces métriques | 20 |
| Compacité et connexité | 21 |
| Complétude et point fixe de Banach | 19 |
| Suites et séries de fonctions | 19 |
| Séries entières | 19 |
| Séries de Fourier | 21 |
| Intégrale de Lebesgue | 20 |
| Espaces Lp | 19 |
| Calcul différentiel dans Rn | 19 |
| Inversion locale et fonctions implicites | 19 |
| Intégrales multiples | 18 |
| Équations différentielles : Cauchy-Lipschitz | 22 |
| Systèmes linéaires et stabilité | 19 |
| Analyse complexe : fonctions holomorphes | 22 |
| Théorème des résidus | 20 |
| Groupes et théorème de Lagrange | 22 |
| Anneaux, idéaux et corps | 21 |
| Polynômes et extensions de corps | 19 |
| Réduction des endomorphismes | 20 |
| Formes bilinéaires et espaces euclidiens | 19 |
| Théorème spectral | 17 |
| Probabilités : espérance, variance, lois usuelles | 20 |
| Convergences, loi des grands nombres, théorème central limite | 15 |
| Statistique : estimation et tests | 17 |
| Analyse numérique : interpolation et équations non linéaires | 17 |
| Méthodes itératives pour les systèmes linéaires | 16 |

Chaque sous-thème compte de 15 à 21 questions (de 3 à 4,2 pour cent du total, sous le plafond de 8 pour cent), réparties en 4 parts par fichier (environ 5 par sous-thème et par fichier). Le plan du cahier ne contient pas de rubrique « géométrie » : aucune question n'y est consacrée, hors les questions de géométrie euclidienne du sous-thème 20.

## Difficultés et bonnes réponses

Total : difficulté 1 : 150, 2 : 150, 3 : 100, 4 : 60, 5 : 40 (soit 30/30/20/12/8 pour cent). Bonnes réponses : A 124, B 124, C 124, D 128 (25 pour cent, à 0,2 point près).

| Fichier | D1 | D2 | D3 | D4 | D5 | A | B | C | D |
|---|---|---|---|---|---|---|---|---|---|
| 01 | 40 | 37 | 26 | 13 | 9 | 31 | 31 | 31 | 32 |
| 02 | 37 | 36 | 28 | 12 | 12 | 31 | 31 | 31 | 32 |
| 03 | 33 | 40 | 22 | 19 | 11 | 31 | 31 | 31 | 32 |
| 04 | 40 | 37 | 24 | 16 | 8 | 31 | 31 | 31 | 32 |

Les lettres ont été tirées au hasard avec une graine fixe, avec 31/31/31/32 par fichier et aucune séquence de plus de 3 fois la même lettre. Les niveaux de difficulté sont mon estimation de rédacteur, ajustée pour atteindre la répartition visée : ils n'ont pas été calibrés sur des réponses d'étudiants.

## Méthode de vérification (honnêteté)

- Les énoncés conceptuels ont été rédigés à partir de résultats standards (théorèmes de cours de licence).
- Tout calcul numérique ou symbolique a été contrôlé par script (`sympy`, `numpy`, calcul exact en `fractions` quand c'était possible) : 107 contrôles (intégrales, sommes de séries, coefficients de Fourier, résidus, déterminants jacobiens, valeurs propres, polynômes minimaux, ordres de permutation, indicatrice d'Euler, itérations de Jacobi et de Gauss-Seidel, rayons spectraux, formule de Bayes, etc.). Les 5 contrôles en échec au premier passage étaient des défauts du contrôle lui-même (hypothèses de signe sur les symboles, quadrature numérique lente) et non des questions ; ils ont été re-vérifiés par un second calcul.
- Les distracteurs sont des erreurs typiques, relues une à une pour s'assurer qu'elles sont fausses (par exemple, la constante racine de 2 dans la majoration de la norme 1 par la norme infinie est insuffisante car (1, 1) donne 2).
- 11 questions douteuses ou redondantes ont été retirées au tri (511 rédigées, 500 gardées).

## Rejets corrigés

Premier passage de `--dry-run` : 69 questions avec « choix identiques ou presque », 35 énoncés ne finissant pas par « ? », 5 choix de plus de 100 caractères et 4 espaces mal placés.

- **Choix « presque identiques »** : la normalisation du banc (`norm`) supprime la ponctuation et les symboles (+, −, ≤, ≥, =, √, |, ′, !, …) : des distracteurs mathématiquement distincts comme « x + y » et « x − y » devenaient identiques. Correction : pour les questions concernées, les symboles opératoires sont écrits en toutes lettres dans les quatre choix (« moins », « plus », « inférieur ou égal à », « racine de », « factorielle », …), et quelques choix ont été réécrits à la main (formule intégrale de Cauchy, formule de Newton, distance d'un point à une droite, homogénéité d'une norme, inégalité de Jensen, résidu, intervalles compacts).
- **Énoncés finissant par « : »** : réécrits sous la forme « Quelle proposition complète correctement l'énoncé : « … » ? ».
- **Choix trop longs et espaces mal placés** : raccourcis ou corrigés.

Résultat final : 0 rejet et aucune ligne de problème sur les 4 fichiers.

## Limites

1. **Longueur de la bonne réponse.** Pour les réponses de 30 caractères ou plus (201 questions), la bonne réponse est strictement la plus longue dans 128 cas (64 pour cent). Sur l'ensemble des 500 questions, elle l'est dans 176 cas (35 pour cent). Le cahier demande que la bonne réponse ne soit pas systématiquement la plus longue : ce biais n'est pas corrigé et devrait être traité en relecture (allonger ou raccourcir des distracteurs).
2. **Écriture des symboles.** Les choix concernés par la correction ci-dessus mélangent symboles et mots (« ‖x‖ moins ‖y‖ ») : lisibles à l'écran mais moins élégants que la notation mathématique.
3. **Relecture humaine requise** : toutes les questions sont en statut `review`. Les sources citent des familles de manuels (Rudin, Dieudonné, Cartan, Gourdon, Perrin, Lang, Ciarlet, Quarteroni, Saporta, etc.) sans titre, édition ni page : ce sont des références de cours-type, pas des renvois précis.
4. **Couverture.** Le plan suit le programme indicatif du cahier ; la géométrie (affine, projective, différentielle) n'y figure pas et n'est pas traitée.
5. Les valeurs de probabilité (par exemple P(X ≥ 60) ≈ 2,3 %) utilisent l'approximation normale sans correction de continuité, ce que l'énoncé précise.
