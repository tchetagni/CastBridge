# Rapport : Quiz Supérieur, Philosophie L1 / L2 / L3

Branche `claude/quiz-higher-philosophie`. 12 fichiers `content/quiz/batches/higher-<l1|l2|l3>-philosophie-0<1..4>.json`, 125 questions chacun : **1 500 questions** (500 par niveau). Clés `course` : `l1-philo`, `l2-philo`, `l3-philo`. Aucune modification de `content/quiz/dist`, `android/` ni `backend/`. Aucune Pull Request.

## Validation
`quizbank.py import --dry-run` sur les 12 fichiers : 125 questions valides chacun, 0 rejet, 0 avertissement. Il faut utiliser `python3.12` : sous 3.11, `tools/quiz-bank/qb/gen/sup_compta_l1.py` (autre rédacteur, f-string imbriqué) empêche de charger l'outil. Fichier non modifié.

## Comptes par sous-thème
- **L1** : 25 sous-thèmes du plan × 20 questions (4 % chacun).
- **L2** : 26 sous-thèmes, de 15 (Hobbes/Locke/Rousseau) à 25 (philosophie de la religion) ; maximum 5 %.
- **L3** : 25 sous-thèmes × 20 questions.
Intitulés exacts dans le champ `category` des fichiers.

## Difficultés (1/2/3/4/5)
- L1 : 150 / 150 / 100 / 50 / 50 (30/30/20/10/10 %, un peu plus de 4-5 que visé).
- L2 : 150 / 150 / 100 / 60 / 40.
- L3 : 150 / 150 / 100 / 60 / 40.

## Bonnes réponses
A/B/C/D : 125 / 125 / 125 / 125 par niveau (25 % exactement), positions mélangées par script.

## Rejets corrigés
Premier passage L1 : 6 choix « dépend de l'ordre » (« Tous les… », « Les deux… ») ; L2 : 14 rejets (11 mêmes choix, 3 trop longs, 3 sans « ? »). Tous réécrits. L3 : 0 rejet automatique ; corrections de relecture (deux distracteurs en réalité défendables, énoncés ambigus, deux explications trop affirmatives).

## Limites
- Contenu en statut `review` : relecture humaine requise, surtout sur la qualité des distracteurs (L2 sous-thème 4) et les sous-thèmes d'auteurs africains moins connus (L3).
- Pas de citation exacte donnée de mémoire ; sources = familles de cours/manuels génériques.
- Dans L3, la bonne réponse est la plus longue dans 41 % des cas : léger biais de longueur.
- Les niveaux de difficulté ont été ajustés après coup pour atteindre la répartition, ils restent indicatifs.
- Les lots ne sont pas intégrés (`quizbank.py lots` et paquets embarqués : étape de l'intégrateur).
