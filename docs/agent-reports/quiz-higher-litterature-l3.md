# Quiz Supérieur L3 Littérature (`l3-lit`) : rapport du rédacteur

Date : 2026-10-03. Fichiers : `content/quiz/batches/higher-l3-litterature-01.json` à `-04.json` (4 x 125 = 500 questions, auteur « Claude (rédacteur) », `course` = `l3-lit`, `verif` = `import`, aucun `id`). Rien n'a été commité ni importé (`--dry-run` seulement) ; `content/quiz/dist`, `android/` et `backend/` n'ont pas été touchés.

## Validation

`python3 tools/quiz-bank/quizbank.py import --dry-run <fichier>` : 125 questions valides, 0 rejet, 0 avertissement pour chacun des 4 fichiers. Note : le chargeur de générateurs de `quizbank.py` importe `qb/gen/sup_compta_l1.py` (fichier d'un autre rédacteur) qui contient une f-string imbriquée valide seulement à partir de Python 3.12 ; la validation a donc été lancée avec `python3.12` (`python3` = 3.11 plante avant toute lecture du lot).

## Comptes par sous-thème et par fichier

| Sous-thème (`category`) | 01 | 02 | 03 | 04 | Total |
|---|---|---|---|---|---|
| théorie du roman : Lukács, Bakhtine, Barthes | 5 | 5 | 5 | 5 | 20 |
| esthétique de la réception | 5 | 5 | 5 | 5 | 20 |
| psychocritique et sociocritique | 5 | 5 | 5 | 5 | 20 |
| postcolonialisme et littératures du Sud | 5 | 4 | 5 | 5 | 19 |
| littérature africaine anglophone : Achebe, Soyinka, Ngugi | 5 | 5 | 5 | 5 | 20 |
| roman africain après les indépendances : désenchantement | 5 | 5 | 5 | 5 | 20 |
| écriture de la violence et de la mémoire : Kourouma, Mudimbe | 5 | 5 | 5 | 4 | 19 |
| littérature féminine africaine : Mariama Bâ, Aminata Sow Fall | 5 | 5 | 5 | 5 | 20 |
| littérature camerounaise contemporaine : Mongo Beti, Patrice Nganang | 4 | 5 | 5 | 5 | 19 |
| théâtre contemporain d'expression française | 5 | 5 | 5 | 5 | 20 |
| poésie contemporaine d'Afrique et des Antilles | 5 | 5 | 5 | 5 | 20 |
| littératures antillaise et créole : Césaire, Glissant, Chamoiseau | 5 | 5 | 4 | 5 | 19 |
| la créolité et la théorie du Tout-monde | 5 | 5 | 5 | 5 | 20 |
| littérature maghrébine francophone : Kateb Yacine, Assia Djebar | 5 | 5 | 5 | 5 | 20 |
| littérature du XXe siècle : Proust, Gide, Céline, Malraux | 5 | 5 | 5 | 5 | 20 |
| autobiographie et autofiction | 5 | 5 | 5 | 5 | 20 |
| poétique du roman policier et de la littérature de genre | 5 | 4 | 5 | 4 | 18 |
| littérature et histoire : roman historique | 5 | 5 | 5 | 5 | 20 |
| littérature et politique : engagement et responsabilité | 5 | 5 | 5 | 5 | 20 |
| analyse du discours littéraire | 5 | 5 | 5 | 5 | 20 |
| poétique de la traduction | 5 | 5 | 5 | 5 | 20 |
| oralité et écriture dans les littératures africaines | 4 | 5 | 4 | 5 | 18 |
| édition et diffusion du livre en Afrique | 4 | 4 | 4 | 4 | 16 |
| littérature de jeunesse | 4 | 4 | 4 | 4 | 16 |
| littérature numérique (notions) | 4 | 4 | 4 | 4 | 16 |
| méthodologie du mémoire de lettres : problématique et bibliographie | 5 | 5 | 5 | 5 | 20 |
| **Total** | 125 | 125 | 125 | 125 | 500 |

Aucun sous-thème ne dépasse 5 questions par fichier (4 % ; plafond 8 %). Les 26 sous-thèmes du § 9 sont présents dans chaque fichier. Les trois sous-thèmes les plus étroits (édition en Afrique, littérature de jeunesse, littérature numérique) sont limités à 4 questions par fichier (16 au total) et restent sur des notions générales.

## Difficultés et lettres A à D

| Fichier | D1 | D2 | D3 | D4 | D5 | A | B | C | D | plus longue série de même lettre |
|---|---|---|---|---|---|---|---|---|---|---|
| 01 | 38 | 37 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 3 |
| 02 | 37 | 38 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 3 |
| 03 | 37 | 38 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 3 |
| 04 | 37 | 38 | 25 | 15 | 10 | 32 | 31 | 31 | 31 | 3 |

Répartition difficulté : 37-38 / 37-38 / 25 / 15 / 10 par fichier (30/30/20/12/8 %). Lettres : 31 ou 32 par lettre (24,8 % à 25,6 %), jamais plus de 3 identiques d'affilée. La bonne réponse est placée par un tirage pseudo-aléatoire contrôlé (graines fixes), les distracteurs gardent leur ordre d'écriture.

## Régions

- Fichier 01 : AF 28, CM 4, WORLD 93
- Fichier 02 : AF 28, CM 10, WORLD 87
- Fichier 03 : AF 29, CM 6, WORLD 90
- Fichier 04 : AF 22, CM 7, WORLD 96

## Rejets et corrections en cours de route

- Un énoncé du lot 02 ne finissait pas par « ? » (point de suspension dans la phrase) : reformulé.
- Deux questions identiques (Kourouma, *Monnè*) étaient présentes dans le lot 02 sous deux sous-thèmes : la seconde a été remplacée par une question sur *Quatre-vingt-treize* (Hugo).
- Passage de relecture sur la longueur des choix : environ 60 questions dont la bonne réponse dépassait nettement la longueur des distracteurs ont eu leurs distracteurs réécrits à longueur comparable ; il reste quelques titres d'œuvres ou noms propres plus longs que leurs alternatives (inévitable), mais la bonne réponse est la plus courte aussi souvent que la plus longue.
- Retrait de détails dont la certitude n'était pas totale (année de parution d'une pièce de Bole Butake, lieu exact d'un polar de Mongo Beti, durée précise de parution d'une revue, titre français d'un journal de prison de Ngugi, premier prix Goncourt maghrébin).

## Limites et points à relire par un humain

- Tous les faits sont des attributions d'œuvres, dates de publication, prix et notions théoriques classiques ; aucune citation textuelle n'est donnée comme telle (les rares formules reconnues, par exemple « butin de guerre » de Kateb Yacine, sont posées sous forme de question d'attribution).
- Points un peu plus fins à vérifier en priorité : attribution du néologisme « orature » à Pio Zirimu ; remarque sur la « tigritude » attribuée à Soyinka ; premier roman d'une Africaine publié en anglais (Flora Nwapa, formulé « souvent considéré ») ; Marie-Claire Matip (*Ngonda*, 1958) formulé « l'un des premiers romans d'une Africaine francophone » ; typologie des personnages de Marthe Robert (Enfant trouvé / Bâtard) ; Yodi Karone, *Le Bal des Caïmans* (1980) ; Jean Amrouche, *Cendres* (1934).
- Les sous-thèmes « édition et diffusion du livre en Afrique » et « littérature numérique » sont volontairement traités par des notions générales (chaîne du livre, droit d'auteur, ISBN, hypertexte, Oulipo, Gutenberg) ; aucun fait récent sur l'édition africaine ou le numérique contemporain n'a été posé.
- Le sous-thème « édition et diffusion du livre en Afrique » ne comporte aucune question étiquetée AF ou CM : toutes portent sur des notions générales du livre (chaîne du livre, droit d'auteur, ISBN, dépôt légal, piratage) valables en contexte africain.
- Les `source` sont des familles de cours ou de manuels (Compagnon, Jauss, Chevrier, Lagarde et Michard, Lejeune, Todorov, Berman, Meschonnic, Finnegan, Ong, Hazard...) et jamais de titre, d'ISBN ni de page ; elles sont identiques pour toutes les questions d'un même sous-thème.
- Statut à l'import : `review` (relecture humaine requise avant approbation).
