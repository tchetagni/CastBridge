# Rapport : quiz Supérieur, Littérature L2 (`l2-lit`)

Date : 2026-10-03. Rédacteur : Claude. Fichiers : `content/quiz/batches/higher-l2-litterature-01.json` à `-04.json`
(125 questions chacun, 500 au total, `course` = `l2-lit`, `verif` = `import`, aucun `id`).

## Validation

`python3.12 tools/quiz-bank/quizbank.py import --dry-run <fichier>` : 125 questions valides, 0 rejet, 0 avertissement, code de sortie 0, pour les 4 fichiers.
Note d'environnement : sous Python 3.11 la commande échoue avant d'atteindre les lots, à cause d'un f-string imbriqué dans
`tools/quiz-bank/qb/gen/sup_compta_l1.py` (fichier d'un autre rédacteur, non touché) ; la validation a été faite avec `python3.12`.

## Répartition par sous-thème (programme L2 Littérature, 26 sous-thèmes)

21 sous-thèmes à 5 questions par fichier (20 au total) et 5 sous-thèmes à 4 par fichier (16 au total) : 1 à 21 = 20 questions, 22 à 26 = 16 questions.
Aucun sous-thème ne dépasse 4 % d'un fichier (limite 8 %).

| N° | Sous-thème (champ `category`) | Total (01/02/03/04) |
|---|---|---|
| 1 | Théories littéraires : structuralisme et poétique | 20 (5/5/5/5) |
| 2 | Sémiotique et analyse du discours | 20 |
| 3 | Stylistique et rhétorique | 20 |
| 4 | Histoire littéraire du XVIIe siècle : moralistes et Pascal | 20 |
| 5 | Théâtre classique : la tragédie racinienne | 20 |
| 6 | La comédie de Molière : structure et enjeux | 20 |
| 7 | Littérature des Lumières : le conte philosophique | 20 |
| 8 | Le roman du XVIIIe siècle : Marivaux, Prévost, Laclos | 20 |
| 9 | Poésie romantique et préface de Cromwell | 20 |
| 10 | Roman du XIXe siècle : Stendhal et le roman d'analyse | 20 |
| 11 | Balzac et La Comédie humaine | 20 |
| 12 | Flaubert et le travail du style | 20 |
| 13 | Zola et le roman expérimental | 20 |
| 14 | Poésie du XIXe siècle : Les Fleurs du mal | 20 |
| 15 | Avant-gardes du XXe siècle : dada et surréalisme | 20 |
| 16 | Le Nouveau Roman : Robbe-Grillet, Sarraute | 20 |
| 17 | Théâtre de l'absurde : Beckett, Ionesco | 20 |
| 18 | Littérature francophone d'Afrique : roman de la colonisation et de l'indépendance | 20 |
| 19 | Théâtre africain : Guillaume Oyono-Mbia, Sony Labou Tansi | 20 |
| 20 | Poésie africaine : Senghor et la poétique de la négritude | 20 |
| 21 | Littérature orale africaine : conte, épopée, proverbe | 20 |
| 22 | Littérature camerounaise : Mongo Beti, Ferdinand Oyono, Calixthe Beyala | 16 (4/4/4/4) |
| 23 | Littérature comparée : notions et méthodes | 16 |
| 24 | Traduction et littérature mondiale | 16 |
| 25 | Intertextualité et réécriture | 16 |
| 26 | Méthodologie : explication de texte | 16 |

## Difficultés, lettres, régions

| Fichier | D1 | D2 | D3 | D4 | D5 | A | B | C | D | `AF` | `CM` |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 01 | 38 | 37 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 18 | 6 |
| 02 | 38 | 37 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 19 | 5 |
| 03 | 38 | 37 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 19 | 5 |
| 04 | 38 | 37 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 20 | 5 |

Lettres : 25,6 % / 24,8 % / 24,8 % / 24,8 % par fichier ; jamais plus de 3 mêmes lettres d'affilée (position tirée au hasard avec graine fixe, ordre des questions mélangé).
La bonne réponse est strictement la plus longue dans 23 à 33 % des questions selon le fichier (le hasard donne 25 %), les distracteurs ayant été rallongés ou la bonne réponse raccourcie quand l'écart était grand.

## Sources

Chaque sous-thème renvoie à une famille de manuel ou de cours réelle (Lagarde et Michard pour l'histoire littéraire, Compagnon, Genette, Greimas et Courtés, Maingueneau, Molinié, Nadeau, Chevrier pour la littérature africaine francophone, Finnegan pour l'oralité, Brunel-Pichois-Rousseau pour la comparée, Bergez pour la méthode). Aucun titre, ISBN, page ou édition inventé.

## Rejets et corrections

- Importeur : aucun rejet sur la version finale.
- Corrections faites en relecture de fond avant livraison : « sixième roman » des Rougon-Macquart corrigé en « septième » (L'Assommoir) ; explication de Germinal qui évoquait par erreur une grève des années 1880 (le roman se situe sous le Second Empire) ; titre de livre inventé retiré d'un distracteur (remplacé par un titre réel) ; formulations d'explications trop affirmatives (parachutage de « Liberté », ressemblance de la pièce Vautrin avec Louis-Philippe, naissance de Mwindo) retirées ; deux questions où la bonne réponse figurait déjà dans l'énoncé (Vautrin) reformulées ; questions sur des points disputés écartées (sous-titre exact du Rouge et le Noir, « lecteur modèle » d'Eco contre « lecteur implicite » d'Iser, Avant-propos de 1842 et Cuvier, ordres des mots de la Cantatrice chauve).

## Limites

- Les fichiers `-01` et `-03` ont été committés par ailleurs (commit 8cea887f) dans une version intermédiaire ; les versions finales décrites ici sont celles du dossier de travail et diffèrent de ce commit sur quelques questions (corrections ci-dessus).
- Quelques faits reposent sur des synthèses de manuels et sont à revoir par l'humain en `review` : titre anglais de Trois prétendants... un mari (« Three Suitors: One Husband »), prix attribués à Mongo Beti et Francis Bebey, rôle de Ferdinand Oyono (diplomate et ministre), Nazi Boni et Tierno Monénembo (années de publication), formule « civilisation de l'universel » et citation de Senghor dans Liberté I.
- Les questions d'Afrique anglophone (Soyinka, Ngũgĩ) sont rattachées au sous-thème « théâtre africain » avec la région `AF` ; elles élargissent le programme indicatif.
- Peu de questions de niveau 5 sont de pure théorie : on a préféré des points précis et stables (dates, œuvres, auteurs) à des interprétations contestées.
- Pas de `quizbank.py check` complet exploitable (la commande est longue et dépend des lots des autres rédacteurs) ; la cohérence au niveau de la cellule (équilibre A-D, difficultés, doublons) a été vérifiée par un script sur les 4 fichiers : 0 doublon de question, réponses répétées uniquement sur des faits différents.
