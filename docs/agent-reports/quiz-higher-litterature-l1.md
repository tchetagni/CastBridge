# Rapport : quiz Supérieur, L1 Littérature (`l1-lit`)

Rédacteur : Claude (rédacteur). Date : 2026-10-03. Branche : `claude/quiz-higher-litterature` (aucun commit, aucun push).

## Livrables

| Fichier | Lot (`batch`) | Questions |
|---|---|---|
| `content/quiz/batches/higher-l1-litterature-01.json` | `higher-l1-litterature-01` | 125 |
| `content/quiz/batches/higher-l1-litterature-02.json` | `higher-l1-litterature-02` | 125 |
| `content/quiz/batches/higher-l1-litterature-03.json` | `higher-l1-litterature-03` | 125 |
| `content/quiz/batches/higher-l1-litterature-04.json` | `higher-l1-litterature-04` | 125 |
| Total | | **500** |

Format : objet `{batch, author, date, questions}` ; chaque question porte `course: "l1-lit"`, `category` (sous-thème du § 9), `difficulty`, `question`, `choices` (4), `answer` (lettre), `explanation`, `source`, `verif: "import"` ; `region: "AF"` pour 43 questions propres à l'Afrique (négritude et littérature africaine francophone). Pas d'`id`.

## Validation

- `python3 tools/quiz-bank/quizbank.py import --dry-run <fichier>` : 125 questions valides, 0 rejet, 0 avertissement pour chacun des 4 fichiers (code de sortie 0). Seul `--dry-run` a été utilisé.
- Contrôle croisé des 4 fichiers réunis avec `qb.qc.check_bank` : 0 erreur, 0 avertissement, aucun quasi-doublon (même réponse et mots proches), aucune question en double entre fichiers, aucun message d'équilibre de banque.
- Note d'environnement : sous Python 3.11 (`python3` par défaut ici), `quizbank.py` plante à l'import du générateur `qb/gen/sup_compta_l1.py` (f-string avec guillemets imbriqués, valable seulement dès Python 3.12). Ce fichier appartient à un autre rédacteur et n'a pas été modifié ; les validations ont été faites avec `python3.12`.

## Répartition des difficultés (par fichier)

| Fichier | D1 | D2 | D3 | D4 | D5 |
|---|---|---|---|---|---|
| 01 | 38 | 37 | 25 | 15 | 10 |
| 02 | 38 | 37 | 25 | 15 | 10 |
| 03 | 38 | 37 | 25 | 15 | 10 |
| 04 | 38 | 37 | 25 | 15 | 10 |

Cible 30/30/20/12/8 % atteinte (30,4/29,6/20/12/8 %). Chaque difficulté de 1 à 5 est présente dans chaque fichier.

## Répartition des réponses A, B, C, D

| Fichier | A | B | C | D | Plus longue série de la même lettre |
|---|---|---|---|---|---|
| 01 | 32 | 31 | 31 | 31 | 3 |
| 02 | 32 | 31 | 31 | 31 | 3 |
| 03 | 32 | 31 | 31 | 31 | 3 |
| 04 | 32 | 31 | 31 | 31 | 3 |
| Total | 128 (25,6 %) | 124 (24,8 %) | 124 (24,8 %) | 124 (24,8 %) | |

Les positions sont tirées au hasard (graine fixe par fichier) avec effectifs équilibrés et sans série de plus de trois lettres identiques. La bonne réponse est strictement la plus longue dans environ 30 à 34 % des questions selon le fichier (les réponses-noms propres pèsent là-dedans) ; les définitions longues ont été rééquilibrées en allongeant les mauvais choix.

## Comptes par sous-thème

| # | Sous-thème (champ `category`) | 01 | 02 | 03 | 04 | Total |
|---|---|---|---|---|---|---|
| 1 | Introduction à l'étude littéraire : texte, auteur, lecteur | 6 | 5 | 6 | 6 | 23 |
| 2 | Genres littéraires : poésie, théâtre, roman, essai | 6 | 5 | 6 | 5 | 22 |
| 3 | Figures de style | 6 | 5 | 6 | 5 | 22 |
| 4 | Versification française | 6 | 5 | 6 | 5 | 22 |
| 5 | Registres littéraires | 6 | 5 | 5 | 6 | 22 |
| 6 | Narratologie : narrateur, point de vue, focalisation | 6 | 5 | 5 | 6 | 22 |
| 7 | Temps du récit | 6 | 5 | 5 | 6 | 22 |
| 8 | Le Moyen Âge : chanson de geste, roman courtois | 6 | 6 | 5 | 6 | 23 |
| 9 | La Renaissance : Rabelais, Ronsard, Du Bellay | 6 | 6 | 6 | 6 | 24 |
| 10 | Le classicisme : Corneille, Racine, Molière | 6 | 6 | 6 | 6 | 24 |
| 11 | Règles du théâtre classique | 6 | 6 | 6 | 6 | 24 |
| 12 | La Fontaine et les fables | 6 | 6 | 6 | 5 | 23 |
| 13 | Les Lumières | 6 | 6 | 6 | 5 | 23 |
| 14 | Le romantisme : Hugo, Lamartine, Musset | 6 | 6 | 6 | 5 | 23 |
| 15 | Le réalisme et le naturalisme | 6 | 6 | 6 | 6 | 24 |
| 16 | La poésie moderne : Baudelaire, Rimbaud, Verlaine | 5 | 6 | 6 | 6 | 23 |
| 17 | Le symbolisme | 5 | 6 | 6 | 6 | 23 |
| 18 | Le surréalisme : Breton, Éluard | 5 | 6 | 6 | 6 | 23 |
| 19 | Camus, Sartre et l'existentialisme | 5 | 6 | 6 | 5 | 22 |
| 20 | La négritude : Senghor, Césaire, Damas | 5 | 6 | 5 | 6 | 22 |
| 21 | Littérature africaine francophone | 5 | 6 | 5 | 6 | 22 |
| 22 | Commentaire composé et dissertation (méthode) | 5 | 6 | 5 | 6 | 22 |

Les 22 sous-thèmes du programme L1 Littérature sont couverts dans chaque fichier ; aucun ne dépasse 6 questions sur 125 (4,8 %, plafond 8 %). Les sous-thèmes 3 « Figures de style » et 4 « Versification » pourraient contenir davantage de questions (fonds très riche) mais ont été limités à 22 pour respecter la répartition demandée.

## Sources

Chaque question cite une famille de manuel ou de cours réelle, jamais un titre ou un auteur inventé :
- histoire littéraire française : « Cours de licence de lettres modernes, histoire de la littérature française (famille Lagarde et Michard) », avec le siècle ;
- stylistique et versification : « Cours de stylistique et de versification, famille des manuels de licence de lettres » ; figures de style : « Cours de stylistique et de rhétorique de licence de lettres, famille des manuels de figures de style » ;
- narratologie et temps du récit : « Cours de narratologie de licence de lettres (famille Genette, Figures III) » ;
- théorie littéraire et genres : cours d'introduction à l'analyse littéraire (famille des manuels de théorie littéraire, Genette et Barthes) ;
- négritude et littérature africaine : « Cours de littérature africaine francophone (famille Chevrier, Littérature nègre) » ;
- méthode : « Cours de méthodologie du commentaire composé et de la dissertation en licence de lettres ».

## Rejets et corrections apportés pendant la rédaction

- Rejet « choix qui dépend de l'ordre » (choix commençant par « Les deux… ») : un mauvais choix « Les deux genres mettent… » (fichier 03) et la bonne réponse « Les Deux Pigeons » (fichier 04) ont été reformulés (« Chacun de ces genres… », question sur « Le Savetier et le Financier »).
- Rejet « la question doit finir par ? » : une question terminée par un guillemet fermant (fichier 04, vers de Racine sur les serpents) a été réécrite pour finir par « ? ».
- Correction avant validation (fichier 01) : une question sur le schéma de rimes ABAB sans point d'interrogation a été reformulée.
- Rééquilibrage des difficultés et des longueurs de choix : plusieurs réponses nettement plus longues que les distracteurs ont été corrigées ; les étiquettes de difficulté ont été ajustées pour tenir la répartition 38/37/25/15/10.
- Après correction : 0 rejet, 0 avertissement sur les quatre fichiers.

## Limites et points à relire

- Niveau volontairement limité aux bases de L1 (définitions, grands auteurs, figures, versification, narratologie, méthode). Aucune question de niveau L2/L3 (théories avancées, intertextualité fine, postcolonialisme).
- Aucune citation n'a été écrite de mémoire hors vers et formules d'une notoriété certaine (par exemple « Va, je ne te hais point », « Je est un autre », « Qu'en un lieu, qu'en un jour… », « un long, immense et raisonné dérèglement de tous les sens », « donner un sens plus pur aux mots de la tribu »). La relecture humaine peut vérifier ces formules en priorité.
- Dates et faits stables retenus (Fleurs du mal 1857 avec six pièces condamnées, Avant-propos de la Comédie humaine 1842, Serments de Strasbourg 842, élection de La Fontaine à l'Académie 1684, élection de Senghor en 1983, etc.) : à contrôler par le relecteur si une édition de référence est disponible.
- Questions d'analyse (registres, focalisations, plans de dissertation) : les définitions suivent l'usage scolaire français courant ; une relecture peut vérifier qu'aucun distracteur n'est défendable dans une autre terminologie (par exemple la distinction entre « métonymie » et « synecdoque », volontairement évitée dans les choix).
- La difficulté est une estimation du rédacteur ; la relecture humaine peut la réétalonner.
- Les questions restent en statut `review` à l'import ; aucune approbation n'a été donnée.
- Les autres cellules (L2, L3 Littérature) sont écrites par d'autres rédacteurs ; les sujets ont été choisis pour éviter les recoupements (pas de question de type L2 comme la théorie structuraliste ou la Comédie humaine en détail).
