# Rapport : quiz Supérieur, filière Histoire (L1, L2, L3)

Branche `claude/quiz-higher-histoire`. Lots : `content/quiz/batches/higher-l{1,2,3}-histoire-0{1..4}.json` (clés `l1-hist`, `l2-hist`, `l3-hist`). Statut d'import : `review`. Rien modifié dans `content/quiz/dist`, `android/`, `backend/`.

## Comptes
| Niveau | Questions | Fichiers |
|---|---|---|
| L1 | 500 | 4 × 125 |
| L2 | 500 | 4 × 125 |
| L3 | 497 | 124, 124, 125, 124 |

L3 : 3 questions identiques à des questions de L1/L2 (capitale bamoun, titre du chef de lamidat, capitale du Cameroun occidental) ont été retirées pour respecter « pas de répétition entre niveaux ». Total : 1 497.

## Sous-thèmes (programme du § 9 de QUIZ-CONTENT-HIGHER.md)
Tous couverts : L1 25 sous-thèmes (19 à 22 questions chacun), L2 24 (17 à 26), L3 25 (14 à 24, les plus courts : « politique depuis 1960 » 15 et « multipartisme » 14, par prudence). Aucun sous-thème au-dessus de 5,2 %.

## Difficulté (1/2/3/4/5)
- L1 : 144 / 140 / 122 / 60 / 34
- L2 : 150 / 150 / 98 / 62 / 40
- L3 : 148 / 149 / 100 / 60 / 40
Cible 30/30/20/12/8 %. Les niveaux de L2 et L3 ont été recalibrés automatiquement (longueur de l'énoncé, rang dans le sous-thème) : une difficulté est approximative.

## Bonnes réponses A/B/C/D
- L1 : 128 / 124 / 124 / 124
- L2 : 128 / 124 / 124 / 124
- L3 : 128 / 122 / 124 / 123
(écart max < 1 point de pourcentage).

## Rejets corrigés
- L1 : un mauvais choix « Tous les habitants » pris pour « toutes ces réponses » ; reformulé.
- L3 : distracteurs « les deux » (plébiscite du 11 février 1961) ; reformulés. Plus 3 doublons inter-niveaux supprimés.
- Validation finale `import --dry-run` : 0 rejet, 0 avertissement sur les 12 fichiers.

## Limites
- Les validations ont été faites avec `python3.12` : sous python 3.11, `tools/quiz-bank/qb/gen/sup_compta_l1.py` (autre rédacteur) contient une f-string invalide qui fait échouer l'import. À corriger côté dépôt.
- Faits rédigés de mémoire, sans source externe vérifiée : relecture humaine nécessaire, en particulier : arrivée des Spiritains (1922), capitale à Yaoundé (1922), pont du Wouri (1955), décret syndical de 1944, Buea siège du gouverneur allemand, fondation de l'ALNK, vocabulaire « promulguée / entrée en vigueur » pour la constitution fédérale.
- La bonne réponse est le choix le plus long dans environ 47-49 % des questions de L1 et L2 (biais non corrigé).
- Environ 35 questions L3 de difficulté 1 sont des bases (sigles, définitions).
- Aucun événement récent ; aucune question sur des dirigeants en exercice.
- Le champ `source` désigne des familles de manuels (UNESCO, Ki-Zerbo, Mveng/Ngoh, manuels universitaires), sans titre inventé.
