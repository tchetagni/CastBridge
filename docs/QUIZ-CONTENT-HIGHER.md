# Quiz Supérieur : écrire les questions des 26 cellules vides

Cahier pour les rédacteurs de lots de questions (agents ou humains) de la voie « Supérieur » de CastBridge-TV. Sur la TV, la voie
Supérieur propose un niveau (L1, L2, L3) puis une filière parmi 13 : 39 cellules. 26 n'avaient aucune question et affichaient
« bientôt ». Leurs parcours sont maintenant enregistrés (`tools/quiz-bank/qb/courses_sup.py`) : une question qui porte la clé
`course` de sa cellule est acceptée. Vue d'ensemble de la banque : `docs/QUIZ.md`, section 6 ter.

Les questions entrent TOUJOURS en statut `review` (jamais `approved` à l'import) : elles seront relues par un humain.

## 1. Les 26 cellules et leurs clés

La clé `course` est obligatoire dans chaque question. Ne l'inventez pas : copiez-la de la première colonne.

| `course` | Niveau | Filière | `field` (catalogue) | Préfixe d'`id` | Portée du lot |
|---|---|---|---|---|---|
| `l1-phys` | L1 | Physique | `physique` | `hph1-` | `physique-l1` |
| `l1-psy` | L1 | Psychologie | `psychologie` | `hps1-` | `psychologie-l1` |
| `l1-geo` | L1 | Géographie | `geographie` | `hge1-` | `geographie-l1` |
| `l1-lit` | L1 | Littérature | `litterature` | `hli1-` | `litterature-l1` |
| `l1-hist` | L1 | Histoire | `histoire` | `hhi1-` | `histoire-l1` |
| `l1-chim` | L1 | Chimie | `chimie` | `hch1-` | `chimie-l1` |
| `l1-philo` | L1 | Philosophie | `philosophie` | `hfi1-` | `philosophie-l1` |
| `l2-phys` | L2 | Physique | `physique` | `hph2-` | `physique-l2` |
| `l2-psy` | L2 | Psychologie | `psychologie` | `hps2-` | `psychologie-l2` |
| `l2-geo` | L2 | Géographie | `geographie` | `hge2-` | `geographie-l2` |
| `l2-lit` | L2 | Littérature | `litterature` | `hli2-` | `litterature-l2` |
| `l2-hist` | L2 | Histoire | `histoire` | `hhi2-` | `histoire-l2` |
| `l2-chim` | L2 | Chimie | `chimie` | `hch2-` | `chimie-l2` |
| `l2-bio` | L2 | Biologie | `biologie` | `hb2-` | `biologie-l2` |
| `l2-philo` | L2 | Philosophie | `philosophie` | `hfi2-` | `philosophie-l2` |
| `l2-socio` | L2 | Sociologie | `sociologie` | `hs2-` | `sociologie-l2` |
| `l3-maths` | L3 | Mathématiques | `mathematiques` | `hm3-` | `maths-l3` |
| `l3-phys` | L3 | Physique | `physique` | `hph3-` | `physique-l3` |
| `l3-psy` | L3 | Psychologie | `psychologie` | `hps3-` | `psychologie-l3` |
| `l3-geo` | L3 | Géographie | `geographie` | `hge3-` | `geographie-l3` |
| `l3-lit` | L3 | Littérature | `litterature` | `hli3-` | `litterature-l3` |
| `l3-hist` | L3 | Histoire | `histoire` | `hhi3-` | `histoire-l3` |
| `l3-chim` | L3 | Chimie | `chimie` | `hch3-` | `chimie-l3` |
| `l3-bio` | L3 | Biologie | `biologie` | `hb3-` | `biologie-l3` |
| `l3-philo` | L3 | Philosophie | `philosophie` | `hfi3-` | `philosophie-l3` |
| `l3-socio` | L3 | Sociologie | `sociologie` | `hs3-` | `sociologie-l3` |

Les 13 autres cellules existent déjà (droit L1-L3, économie L1-L3, mathématiques L1-L2, informatique L1-L3, biologie L1, sociologie L1) : ne les complétez pas sans ordre explicite.

## 2. Nom et taille des fichiers

- Un fichier = UNE cellule = UN niveau et UNE filière. Nom : `content/quiz/batches/higher-<niveau>-<filière>-<nn>.json`, par exemple `higher-l1-geographie-01.json`, `higher-l3-maths-02.json` (`<filière>` = la clé de la colonne « Portée du lot » sans le niveau final, ex. `geographie`, `maths`, `philosophie`).
- `<nn>` = 01, 02, 03... À chaque fichier suivant d'une même cellule, on incrémente `<nn>`.
- Au plus **125 questions par fichier**. Visez 100 à 125. Plusieurs rédacteurs travaillent en parallèle : ne touchez qu'à vos propres fichiers, ne modifiez jamais ceux des autres, ni `content/quiz/dist`, ni `content/quiz/lots`, ni `content/quiz/families.json`.
- Le nom du fichier (sans `.json`) est le nom du lot (`batch`) : il doit être unique dans le dépôt.

## 3. Format exact du fichier

JSON UTF-8. Soit une liste de questions, soit un objet (recommandé) :

```json
{
  "batch": "higher-l1-geographie-01",
  "author": "nom ou identifiant du rédacteur",
  "date": "2026-10-04",
  "questions": [
    {
      "course": "l1-geo",
      "category": "Coordonnées géographiques",
      "difficulty": 1,
      "question": "Comment s'appelle la ligne imaginaire de latitude 0° ?",
      "choices": ["L'équateur", "Le tropique du Cancer", "Le tropique du Capricorne", "Le méridien de Greenwich"],
      "answer": "A",
      "explanation": "L'équateur est le parallèle de latitude 0° : il partage la Terre en deux hémisphères.",
      "source": "Cours de licence de géographie générale (cartographie et repérage), famille des manuels d'introduction à la géographie",
      "verif": "import"
    }
  ]
}
```

| Champ | Règle |
|---|---|
| `course` | clé du tableau du § 1 (sinon la question est refusée : « parcours inconnu »). Elle fixe `track`, `level`, `field` : ne les écrivez pas |
| `category` | obligatoire : le sous-thème du programme du § 9 (texte court, ex. « Coordonnées géographiques ») |
| `difficulty` | entier de 1 à 5 (voir § 4) |
| `question` | obligatoire, **finit par « ? »**, au plus 260 caractères, pas de double espace, pas d'espace avant une virgule ou un point |
| `choices` | exactement 4 textes, distincts (casse, accents et ponctuation ignorés), non vides, au plus 100 caractères chacun. Jamais « Toutes ces réponses », « Aucune de ces réponses », « A et B », « Les deux » |
| `answer` | la bonne réponse : lettre `"A"` à `"D"` (ou entier 0 à 3). Répartition voir § 5 |
| `explanation` | obligatoire, au moins 8 caractères : une ou deux phrases qui justifient la réponse |
| `source` | obligatoire : voir § 7 |
| `verif` | toujours `"import"` |
| `region` | facultatif ; par défaut `WORLD`. Valeurs permises : `CM`, `AF`, `WORLD`. Mettez `AF` ou `CM` seulement pour une question propre à l'Afrique ou au Cameroun |
| `lang` | facultatif ; par défaut `fr`. Ne pas modifier |
| `id` | à NE PAS écrire : il est calculé (préfixe de la cellule + empreinte du texte) |

Parenthèses et guillemets français « » doivent être fermés. Écrivez les nombres avec des espaces normaux, la virgule comme séparateur décimal si vous écrivez en français.

## 4. Difficulté : répartition visée par fichier

| Difficulté | Part visée | Pour 125 questions | Sens |
|---|---|---|---|
| 1 | 30 % | 37 à 38 | définition ou fait de première année, qu'un étudiant attentif connaît |
| 2 | 30 % | 37 à 38 | notion du cours, application directe |
| 3 | 20 % | 25 | compréhension, comparaison, petit calcul |
| 4 | 12 % | 15 | raisonnement en plusieurs étapes, cas particulier |
| 5 | 8 % | 10 | point fin du programme, mais sans ambiguïté et stable |

Chaque difficulté 1 à 5 doit exister dans le fichier. Les mauvais choix (« distracteurs ») sont plausibles, du même genre que la bonne réponse et de longueur comparable : la bonne réponse n'est jamais la plus longue par habitude.

## 5. Équilibre des réponses A, B, C, D

Le contrôle de la banque exige ±5 points autour de 25 % pour chaque lettre sur l'ensemble d'une cellule. Dans votre fichier, viser 25 % ± 3 points par lettre (pour 125 questions : de 28 à 35 de chacune). Mélangez : ne faites pas de séquence régulière (A, B, C, D, A, B...), ni deux fois de suite plus de trois fois la même lettre.

## 6. Contrôle qualité (résumé de `qb/qc.py`)

Une question est exclue si : champ manquant ; pas 4 choix distincts ; réponse hors A-D ; difficulté hors 1 à 5 ; question qui ne finit pas par « ? » ; texte trop long ; choix dépendant de l'ordre ; espaces mal placés ; parenthèses ou guillemets non fermés ; explication trop courte ; **doublon** (même texte, accents et ponctuation ignorés) dans le fichier ou dans la cellule ; quasi-doublon (même réponse et mots proches). Au niveau de la cellule : équilibre A-D et répartition des difficultés. Les avertissements (mot répété, orthographe douteuse) sont à corriger aussi.

## 7. Règle d'honnêteté : des faits établis, jamais des inventions

- N'écrivez que des **faits bien établis et stables** : définitions, lois, théorèmes, dates et faits historiques consensuels, œuvres et auteurs canoniques, classifications standard. Si vous avez un doute, **ne posez pas la question**.
- **Aucun événement récent** (rien qui dépende d'une actualité, d'un classement, d'une statistique datée, d'un chiffre qui change : population, PIB, records, présidents en exercice, prix récents). Pour un chiffre historique ou physique stable (constante, date), il doit être exact et sans ambiguïté.
- **Aucune ambiguïté** : une seule bonne réponse sans discussion possible ; pas de question d'opinion ; pas de « le plus ... » sans critère ; un seul mauvais choix ne peut pas être défendable.
- Pas d'invention de noms, de théorèmes, de références ou de dates pour remplir. Pas de questions identiques reformulées.
- `source` nomme une **référence standard ou un cours** : une famille de manuel (ex. « Cours de licence de mécanique du point, famille Halliday-Resnick », « Manuel d'histoire de l'Afrique, famille UNESCO Histoire générale de l'Afrique », « Cours de sociologie générale de licence (Durkheim, Weber) »), un texte canonique, une norme (SI). **Ne fabriquez pas de titres, d'ISBN, d'URL, de numéros de page ni d'éditions.** Si vous ne pouvez pas citer une famille de référence réelle et connue, la question ne doit pas être écrite.
- Les distracteurs peuvent être faux mais crédibles ; ils ne doivent jamais être eux-mêmes vrais.
- Contexte : programmes plausibles de licence en Afrique francophone (Cameroun) ; les exemples africains sont bienvenus quand ils sont sûrs.

## 8. Vérifier sans rien modifier, puis livrer

Depuis la racine du dépôt :

```sh
# 1. Validation d'un fichier SANS rien écrire (ni content/quiz/batches, ni dist, ni lots) :
python3 tools/quiz-bank/quizbank.py import --dry-run content/quiz/batches/higher-l1-geographie-01.json
# Objectif : « 125 questions valides importées » et AUCUNE ligne de problème ; code de sortie 0.

# 2. Matrice des 39 cellules (nombre de questions par niveau × filière) et liste des cellules vides :
python3 tools/quiz-bank/cells_report.py --batches-only      # rapide : seuls les fichiers d'écriture
python3 tools/quiz-bank/cells_report.py --strict            # tout le pipeline ; code 1 s'il reste un « bientôt »

# 3. Contrôle de toute la banque (génère et contrôle, écrit seulement des affichages) :
python3 tools/quiz-bank/quizbank.py check
```

Le fichier brut que vous écrivez à son chemin final reste le livrable. La commande sans `--dry-run` (`quizbank.py import FICHIER`) réécrit `content/quiz/batches/<batch>.json` avec les seules questions valides, normalisées : ne la lancez que si votre cahier le demande ; elle ne touche jamais `content/quiz/dist`. La construction des lots (`quizbank.py lots`) et les paquets embarqués sont faits plus tard par l'intégrateur, jamais par un rédacteur. Une cellule restée à zéro question ne produit ni lot ni paquet (ligne « sans question » dans `coverage.json`).

Liste de contrôle avant de rendre un fichier : 4 choix distincts ; une seule bonne réponse ; `course` correct pour le niveau et la filière du nom de fichier ; ≤ 125 questions ; difficultés 30/30/20/12/8 ; lettres équilibrées ; chaque `source` nomme une référence réelle ; aucune question datée ; aucun doublon dans votre fichier ; sous-thèmes variés (voir ci-dessous).

## 9. Programmes indicatifs par cellule

Les listes ci-dessous sont des plans plausibles de licences d'Afrique francophone (L1, L2, L3). Elles servent à répartir les questions et à éviter les quasi-doublons : une question par angle, pas dix questions sur la même définition. Ne posez que ce que vous savez avec certitude.

## Programme indicatif L1

Chaque cellule : au moins 20 sous-thèmes. Répartissez vos questions sur TOUS les sous-thèmes de la cellule (environ 5 questions par sous-thème pour 125 questions) ; un sous-thème ne dépasse pas 8 % du fichier.

### L1 Physique (`l1-phys`)

1. mécanique du point : cinématique, repères et vecteurs
2. mouvements rectiligne uniforme et uniformément varié
3. mouvement circulaire et accélération centripète
4. lois de Newton
5. forces usuelles (poids, tension, réaction, frottement)
6. chute libre et projectile
7. travail et puissance d'une force
8. énergie cinétique et théorème de l'énergie cinétique
9. énergie potentielle et conservation de l'énergie mécanique
10. oscillateur harmonique (ressort, pendule simple)
11. quantité de mouvement et chocs
12. gravitation universelle et lois de Kepler
13. statique des solides et moment d'une force
14. analyse dimensionnelle et unités SI
15. incertitudes et chiffres significatifs
16. thermodynamique de base : température, chaleur, calorimétrie
17. gaz parfaits et loi des gaz
18. premier principe (énergie interne)
19. ondes mécaniques : onde progressive, célérité, périodicité
20. son, intensité sonore et niveau en décibels
21. optique géométrique : réflexion, réfraction, loi de Snell-Descartes
22. lentilles minces et formation des images
23. électrostatique : charge, loi de Coulomb, champ et potentiel
24. circuits en courant continu : loi d'Ohm, lois de Kirchhoff

### L1 Psychologie (`l1-psy`)

1. naissance de la psychologie scientifique (Wundt, James)
2. grands courants : psychanalyse, béhaviorisme, cognitivisme, humanisme
3. méthodes : observation, enquête, expérimentation, étude de cas
4. éthique de la recherche en psychologie
5. bases biologiques du comportement : neurone et synapse
6. organisation du système nerveux
7. sensation et perception : seuils, illusions, lois de la Gestalt
8. attention et conscience
9. mémoire : mémoire sensorielle, à court terme, à long terme (modèle d'Atkinson et Shiffrin)
10. oubli et courbe d'Ebbinghaus
11. apprentissage : conditionnement classique (Pavlov)
12. conditionnement opérant (Skinner) et apprentissage social (Bandura)
13. motivation et besoins (pyramide de Maslow)
14. émotions : théories de James-Lange, Cannon-Bard
15. développement de l'enfant : stades de Piaget
16. développement affectif : attachement (Bowlby, Ainsworth)
17. développement psychosocial d'Erikson
18. Vygotski et la zone proximale de développement
19. adolescence et vieillesse
20. intelligence : mesure, Binet-Simon et QI
21. personnalité : traits, Big Five
22. notions de psychologie sociale : conformité (Asch), obéissance (Milgram)
23. statistiques descriptives de base en psychologie

### L1 Géographie (`l1-geo`)

1. objet et méthodes de la géographie
2. la Terre : forme, dimensions, mouvements
3. coordonnées géographiques : latitude, longitude, fuseaux horaires
4. lecture de cartes : échelle, légende, courbes de niveau
5. projections cartographiques
6. photographies aériennes et télédétection (notions)
7. systèmes d'information géographique (notions)
8. le relief : montagnes, plateaux, plaines
9. tectonique des plaques, séismes et volcans
10. érosion et modelé du relief
11. climats du monde : facteurs et éléments
12. zones climatiques et classification de Köppen
13. circulation atmosphérique et saisons
14. hydrologie : cycle de l'eau, bassins versants
15. grands fleuves et lacs d'Afrique
16. océans, courants marins et littoraux
17. biomes et grandes formations végétales
18. sols et ressources
19. les milieux naturels d'Afrique centrale
20. démographie : natalité, mortalité, croissance naturelle
21. transition démographique
22. migrations et réfugiés
23. peuplement et densités
24. urbanisation et villes du monde
25. Cameroun : relief, climats, régions naturelles

### L1 Littérature (`l1-lit`)

1. introduction à l'étude littéraire : texte, auteur, lecteur
2. genres littéraires : poésie, théâtre, roman, essai
3. figures de style : métaphore, comparaison, métonymie, antithèse, hyperbole
4. versification française : alexandrin, rime, strophe
5. registres : lyrique, épique, tragique, comique, satirique
6. narratologie : narrateur, point de vue, focalisation
7. temps du récit
8. le Moyen Âge : chanson de geste, roman courtois
9. la Renaissance : Rabelais, Ronsard, Du Bellay
10. le classicisme : Corneille, Racine, Molière
11. règles du théâtre classique : trois unités, bienséance, vraisemblance
12. La Fontaine et les fables
13. les Lumières : Montesquieu, Voltaire, Diderot, Rousseau
14. le romantisme : Hugo, Lamartine, Musset
15. le réalisme et le naturalisme : Balzac, Flaubert, Zola
16. la poésie moderne : Baudelaire, Rimbaud, Verlaine
17. le symbolisme
18. le surréalisme : Breton, Éluard
19. Camus, Sartre et l'existentialisme
20. la négritude : Senghor, Césaire, Damas
21. littérature africaine francophone : Kourouma, Mongo Beti, Ferdinand Oyono
22. commentaire composé et dissertation (méthode)

### L1 Histoire (`l1-hist`)

1. la méthode historique : sources, critique, chronologie
2. périodisation et grandes périodes
3. la préhistoire : paléolithique, néolithique
4. berceaux de l'humanité et peuplement de l'Afrique
5. l'Égypte pharaonique : société, écriture, religion
6. la Mésopotamie : cités-États et écriture cunéiforme
7. la Grèce antique : cité, démocratie athénienne
8. Rome : République, Empire, institutions
9. le christianisme et l'Empire romain
10. l'Islam : naissance et expansion
11. le Moyen Âge européen : féodalité, Église
12. royaumes et empires africains : Ghana, Mali, Songhaï
13. Kanem-Bornou et royaumes du bassin du Tchad
14. États de la forêt et de la côte : Bénin, Ashanti, Kongo
15. Éthiopie et Aksoum
16. la Renaissance et les grandes découvertes
17. la traite négrière atlantique et ses conséquences
18. la Réforme et les guerres de religion
19. le siècle des Lumières
20. la Révolution française
21. la Révolution industrielle
22. l'abolition de l'esclavage
23. nationalismes du XIXe siècle
24. la conférence de Berlin (1884-1885) et le partage de l'Afrique
25. peuples du Cameroun avant la colonisation

### L1 Chimie (`l1-chim`)

1. structure de la matière : atome, modèle de Bohr, orbitales
2. classification périodique et propriétés des éléments
3. configuration électronique et règles de remplissage
4. liaison chimique : covalente, ionique, métallique
5. modèle de Lewis et géométrie des molécules (VSEPR)
6. polarité et électronégativité
7. forces intermoléculaires et liaison hydrogène
8. mole, masse molaire et concentration
9. stœchiométrie et réactif limitant
10. solutions : dilution et préparation
11. réactions acido-basiques : définitions de Brønsted
12. pH et produit ionique de l'eau
13. acides et bases forts et faibles
14. dosages acido-basiques
15. oxydoréduction : nombre d'oxydation et couples
16. piles et électrolyse (principe)
17. thermochimie : enthalpie et loi de Hess
18. cinétique chimique : vitesse et facteurs
19. équilibre chimique et loi d'action de masse
20. principe de Le Chatelier
21. solubilité et produit de solubilité
22. chimie organique : alcanes, alcènes, alcynes
23. nomenclature organique
24. fonctions oxygénées : alcools, aldéhydes, cétones, acides
25. sécurité au laboratoire et pictogrammes

### L1 Philosophie (`l1-philo`)

1. qu'est-ce que la philosophie : étymologie et spécificité
2. la naissance de la philosophie en Grèce : les présocratiques
3. Socrate : ironie et maïeutique
4. Platon : théorie des Idées et allégorie de la caverne
5. Aristote : logique, métaphysique, éthique
6. épicurisme et stoïcisme
7. la philosophie médiévale : Augustin et Thomas d'Aquin
8. la Renaissance et l'humanisme
9. Descartes : doute méthodique et cogito
10. empirisme : Locke, Hume
11. rationalisme : Spinoza, Leibniz
12. Kant : la révolution copernicienne
13. la raison et la croyance
14. la vérité : correspondance et cohérence
15. la connaissance et ses sources : raison et expérience
16. la conscience et l'inconscient
17. la liberté et le déterminisme
18. le bonheur et le plaisir
19. le langage et la pensée
20. l'art et le beau
21. la technique et le progrès
22. le travail et la société
23. la méthode de la dissertation philosophique
24. le commentaire de texte philosophique
25. logique de base : raisonnement, syllogisme, sophismes

## Programme indicatif L2

Chaque cellule : au moins 20 sous-thèmes. Répartissez vos questions sur TOUS les sous-thèmes de la cellule (environ 5 questions par sous-thème pour 125 questions) ; un sous-thème ne dépasse pas 8 % du fichier.

### L2 Physique (`l2-phys`)

1. mécanique analytique d'introduction : coordonnées généralisées
2. référentiels non galiléens et forces d'inertie
3. forces centrales et moment cinétique
4. oscillateurs amortis et forcés, résonance
5. systèmes de points et centre de masse
6. mécanique du solide : moment d'inertie, rotation autour d'un axe
7. ondes : superposition, interférences, ondes stationnaires
8. diffraction et réseaux
9. polarisation de la lumière
10. thermodynamique : deuxième principe et entropie
11. cycles thermodynamiques et machines thermiques (Carnot)
12. changements d'état et diagramme de phases
13. transferts thermiques : conduction, convection, rayonnement
14. électrostatique : théorème de Gauss, dipôle, condensateurs
15. magnétostatique : champ magnétique, loi de Biot et Savart, théorème d'Ampère
16. induction électromagnétique : loi de Faraday, loi de Lenz
17. circuits RLC en régime sinusoïdal et impédances
18. équations de Maxwell (forme usuelle)
19. ondes électromagnétiques dans le vide
20. relativité restreinte : postulats, dilatation du temps, contraction des longueurs
21. introduction à la physique quantique : effet photoélectrique, dualité onde-corpuscule
22. atome de Bohr et spectres
23. méthodes mathématiques : équations différentielles, séries de Fourier
24. physique statistique d'introduction : distribution de Maxwell-Boltzmann

### L2 Psychologie (`l2-psy`)

1. psychologie cognitive : traitement de l'information
2. langage : acquisition et troubles (aphasies de Broca et Wernicke)
3. raisonnement, jugement et biais cognitifs
4. résolution de problèmes et prise de décision
5. psychologie du développement : théories du développement cognitif et critiques
6. théorie de l'esprit
7. psychologie sociale : attitudes et dissonance cognitive (Festinger)
8. stéréotypes, préjugés et discrimination
9. attribution causale et erreur fondamentale
10. influence sociale et groupes
11. relations interpersonnelles et agressivité
12. psychologie de la personnalité : psychanalyse freudienne (instances, mécanismes de défense)
13. neurosciences : cortex cérébral et localisations
14. neuropsychologie et imagerie cérébrale (principes)
15. psychopathologie générale : normal et pathologique
16. classifications DSM et CIM
17. troubles anxieux et troubles de l'humeur
18. psychose et schizophrénie (notions)
19. méthodologie : plans expérimentaux et variables
20. statistiques inférentielles : tests t, khi-deux, corrélation
21. psychométrie : fidélité, validité, étalonnage
22. psychologie de la santé et du stress (Selye, Lazarus)
23. psychologie interculturelle et ethnopsychologie en Afrique
24. psychologie de l'éducation : motivation scolaire

### L2 Géographie (`l2-geo`)

1. géographie humaine : cadre conceptuel
2. géographie de la population : structures par âge et par sexe
3. pyramides des âges et politiques de population
4. géographie rurale : systèmes agraires et paysages agraires
5. agriculture tropicale : cultures vivrières et de rente
6. élevage et pastoralisme
7. pêche et aquaculture
8. géographie urbaine : morphologie, fonctions et hiérarchie urbaine
9. croissance urbaine et mégapoles africaines
10. habitat spontané et politiques de la ville
11. réseaux de transport : routes, rail, ports, aéroports
12. corridors et intégration régionale
13. géographie industrielle : localisation et facteurs
14. énergie : sources, production et consommation
15. ressources minières et pétrolières
16. commerce international et mondialisation
17. tourisme et géographie des loisirs
18. géographie de la santé : grandes endémies
19. organisation de l'espace et aménagement du territoire
20. décentralisation et collectivités territoriales
21. géographie régionale de l'Afrique : Afrique centrale, de l'Ouest, de l'Est, australe
22. organisations régionales : CEMAC, CEEAC, CEDEAO, Union africaine
23. cartographie thématique et statistiques de base
24. enquête de terrain et traitement des données

### L2 Littérature (`l2-lit`)

1. théories littéraires : structuralisme et poétique
2. sémiotique et analyse du discours
3. stylistique et rhétorique
4. histoire littéraire du XVIIe siècle : moralistes et Pascal
5. théâtre classique : la tragédie racinienne
6. la comédie de Molière : structure et enjeux
7. littérature des Lumières : le conte philosophique
8. le roman du XVIIIe siècle : Marivaux, Prévost, Laclos
9. poésie romantique et préface de Cromwell
10. roman du XIXe siècle : Stendhal et le roman d'analyse
11. Balzac et La Comédie humaine
12. Flaubert et le travail du style
13. Zola et le roman expérimental
14. poésie du XIXe : Les Fleurs du mal
15. avant-gardes du XXe siècle : dada et surréalisme
16. le Nouveau Roman : Robbe-Grillet, Sarraute
17. théâtre de l'absurde : Beckett, Ionesco
18. littérature francophone d'Afrique : roman de la colonisation et de l'indépendance
19. théâtre africain : Guillaume Oyono-Mbia, Sony Labou Tansi
20. poésie africaine : Senghor et la poétique de la négritude
21. littérature orale africaine : conte, épopée, proverbe
22. littérature camerounaise : Mongo Beti, Ferdinand Oyono, Calixthe Beyala
23. littérature comparée : notions et méthodes
24. traduction et littérature mondiale
25. intertextualité et réécriture
26. méthodologie : explication de texte

### L2 Histoire (`l2-hist`)

1. la colonisation européenne en Afrique : méthodes et administrations
2. résistances africaines à la conquête
3. l'Allemagne au Kamerun (1884-1916)
4. le Cameroun sous mandat puis tutelle (France et Royaume-Uni)
5. la Première Guerre mondiale et l'Afrique
6. l'entre-deux-guerres : crise de 1929, fascismes
7. la Seconde Guerre mondiale
8. la Charte de l'ONU et la décolonisation
9. conférence de Brazzaville (1944)
10. mouvements nationalistes : l'UPC et Ruben Um Nyobè
11. les indépendances africaines (1960)
12. la guerre froide : blocs, crises, conflit de Corée
13. la conférence de Bandung et le non-alignement
14. la construction européenne
15. l'État post-colonial : partis uniques et régimes militaires
16. la réunification du Cameroun (1961) et l'État fédéral
17. la fin de l'apartheid en Afrique du Sud
18. les grands conflits africains (Biafra, Congo, Rwanda)
19. l'Organisation de l'unité africaine puis l'Union africaine
20. le panafricanisme : Nkrumah, Sékou Touré
21. la chute du mur de Berlin et la fin du bloc soviétique
22. le monde arabe au XXe siècle
23. l'Asie : Chine, Inde, Japon
24. méthodes : étude de documents et chronologie

### L2 Chimie (`l2-chim`)

1. thermodynamique chimique : premier et deuxième principes
2. enthalpie libre et spontanéité
3. équilibres chimiques et constante K
4. équilibres en solution : acides, bases, tampons
5. courbes de dosage et indicateurs colorés
6. diagrammes potentiel-pH
7. potentiels d'électrode et équation de Nernst
8. piles, accumulateurs et corrosion
9. cinétique : ordres de réaction et loi d'Arrhenius
10. catalyse homogène et enzymatique
11. chimie organique : stéréochimie, isomérie, chiralité
12. effets électroniques : inductif et mésomère
13. substitution nucléophile SN1 et SN2
14. élimination E1 et E2
15. additions sur les alcènes
16. chimie des composés aromatiques : benzène, substitution électrophile
17. dérivés carbonylés : addition nucléophile
18. acides carboxyliques et dérivés : esters et amides
19. amines et composés azotés
20. polymères : polymérisation et propriétés
21. glucides, lipides, acides aminés (biochimie de base)
22. spectroscopies : IR, RMN, masse (principes)
23. chimie des éléments : halogènes, métaux alcalins
24. chimie de coordination (introduction)
25. méthodes de séparation : distillation, extraction, chromatographie

### L2 Biologie (`l2-bio`)

1. la cellule : théorie cellulaire et organites
2. membrane plasmique : structure et transports
3. métabolisme énergétique : respiration cellulaire et ATP
4. photosynthèse : phases et rendement
5. enzymes : cinétique et régulation
6. cycle cellulaire : mitose et contrôle
7. méiose et brassage génétique
8. génétique mendélienne et croisements
9. hérédité liée au sexe
10. ADN : structure, réplication
11. transcription et traduction : synthèse des protéines
12. mutations et maladies génétiques
13. régulation de l'expression génétique
14. biotechnologies : PCR, clonage, OGM
15. évolution : sélection naturelle et spéciation
16. classification du vivant : bactéries, archées, eucaryotes
17. virus : structure et cycle de réplication
18. microbiologie : bactéries, culture et antibiotiques
19. parasitologie : paludisme et autres parasitoses tropicales
20. immunologie : immunité innée et adaptative
21. vaccination et sérologie
22. physiologie animale : système nerveux et hormonal
23. physiologie de la circulation et de la respiration
24. digestion et nutrition
25. physiologie de la reproduction
26. biologie végétale : organisation et croissance de la plante
27. écologie : écosystèmes et chaînes alimentaires
28. biostatistiques et méthode expérimentale

### L2 Philosophie (`l2-philo`)

1. histoire de la philosophie moderne : de Descartes à Kant
2. critique kantienne : Critique de la raison pure
3. éthique kantienne : impératif catégorique
4. philosophie politique : Hobbes, Locke, Rousseau
5. le contrat social et la souveraineté
6. Hegel : dialectique et philosophie de l'histoire
7. Marx : matérialisme historique, aliénation
8. Nietzsche : généalogie de la morale et nihilisme
9. Schopenhauer et Kierkegaard
10. phénoménologie : Husserl et l'intentionnalité
11. Heidegger : l'être et le temps
12. existentialisme : Sartre et Beauvoir
13. philosophie de la connaissance : scepticisme et connaissance
14. épistémologie : Popper et la réfutabilité
15. Bachelard et l'obstacle épistémologique
16. Kuhn et les révolutions scientifiques
17. philosophie du langage : Frege, Wittgenstein
18. philosophie de l'esprit : dualisme et matérialisme
19. éthique : utilitarisme, déontologie, éthique des vertus
20. philosophie politique : justice et égalité (Rawls)
21. philosophie du droit : droit naturel et positivisme
22. philosophie de l'art et esthétique
23. philosophie de la religion : Dieu et la foi
24. philosophie de l'histoire et du progrès
25. Bergson : durée et intuition
26. logique : propositions et quantificateurs

### L2 Sociologie (`l2-socio`)

1. histoire de la sociologie : Comte, Marx, Durkheim, Weber
2. le fait social et les règles de la méthode sociologique
3. l'action sociale chez Weber et les types d'autorité
4. la sociologie de Pierre Bourdieu : habitus, capital, champ
5. interactionnisme et Goffman
6. la socialisation primaire et secondaire
7. normes, déviance et contrôle social
8. stratification sociale : classes, castes, statuts
9. mobilité sociale
10. inégalités sociales et pauvreté
11. sociologie de la famille et du mariage
12. parenté et systèmes de filiation en Afrique
13. sociologie de l'éducation et reproduction sociale
14. sociologie du travail et des organisations
15. sociologie urbaine et rurale
16. sociologie des religions : Durkheim et Weber
17. sociologie du genre
18. sociologie des médias et de la communication
19. sociologie de la santé
20. sociologie politique : pouvoir, État, participation
21. méthodes quantitatives : enquête par questionnaire, échantillonnage
22. méthodes qualitatives : entretien, observation, monographie
23. statistiques descriptives en sciences sociales
24. démographie sociale et changements des structures familiales
25. sociologie du développement
26. sociologie africaine : ethnicité, chefferie, urbanisation

## Programme indicatif L3

Chaque cellule : au moins 20 sous-thèmes. Répartissez vos questions sur TOUS les sous-thèmes de la cellule (environ 5 questions par sous-thème pour 125 questions) ; un sous-thème ne dépasse pas 8 % du fichier.

### L3 Mathématiques (`l3-maths`)

1. espaces vectoriels normés et topologie des espaces métriques
2. compacité et connexité
3. complétude et théorème du point fixe de Banach
4. suites et séries de fonctions : convergences simple et uniforme
5. séries entières et rayon de convergence
6. séries de Fourier
7. intégrale de Lebesgue : mesure, théorèmes de convergence
8. espaces Lp (introduction)
9. calcul différentiel dans R^n : différentielle, matrice jacobienne
10. théorème d'inversion locale et des fonctions implicites
11. intégrales multiples et changement de variables
12. équations différentielles : existence et unicité (Cauchy-Lipschitz)
13. systèmes linéaires et stabilité
14. analyse complexe : fonctions holomorphes, formule de Cauchy
15. théorème des résidus
16. algèbre : groupes, sous-groupes, théorème de Lagrange
17. anneaux, idéaux et corps
18. polynômes et extensions de corps (introduction)
19. réduction des endomorphismes : diagonalisation, trigonalisation
20. formes bilinéaires et espaces euclidiens
21. théorème spectral
22. probabilités : espérance, variance, lois usuelles
23. convergences de variables aléatoires, loi des grands nombres, théorème central limite
24. statistiques : estimation et tests
25. analyse numérique : interpolation, résolution d'équations non linéaires
26. méthodes itératives pour les systèmes linéaires

### L3 Physique (`l3-phys`)

1. mécanique quantique : postulats, fonction d'onde, équation de Schrödinger
2. puits de potentiel et effet tunnel
3. oscillateur harmonique quantique
4. moment cinétique et spin
5. atome d'hydrogène
6. méthodes d'approximation : perturbations
7. physique statistique : ensembles microcanonique, canonique, grand-canonique
8. statistiques de Fermi-Dirac et de Bose-Einstein
9. gaz de photons et rayonnement du corps noir
10. thermodynamique des phénomènes irréversibles
11. mécanique analytique : équations de Lagrange, principe de moindre action
12. formalisme hamiltonien
13. électromagnétisme dans la matière : diélectriques, milieux magnétiques
14. rayonnement d'une charge accélérée et antennes
15. guides d'ondes et optique ondulatoire
16. relativité restreinte : quadrivecteurs, énergie-impulsion
17. physique du solide : réseaux cristallins, diffraction des rayons X
18. modèle des bandes et semi-conducteurs
19. supraconductivité (phénoménologie)
20. physique nucléaire : radioactivité, énergie de liaison, fission et fusion
21. physique des particules : particules élémentaires et interactions
22. astrophysique d'introduction : étoiles, cosmologie de base
23. optique quantique et laser (principe)
24. physique de l'énergie : photovoltaïque, énergies renouvelables en Afrique
25. méthodes expérimentales : traitement du signal, électronique analogique et numérique

### L3 Psychologie (`l3-psy`)

1. psychologie clinique : entretien clinique et observation
2. bilan psychologique et tests projectifs (Rorschach, TAT)
3. tests d'intelligence de Wechsler
4. psychopathologie de l'enfant et de l'adolescent
5. troubles du neurodéveloppement : autisme, TDAH
6. troubles de la personnalité
7. addictions et conduites à risque
8. traumatisme psychique et état de stress post-traumatique
9. deuil, résilience et soutien psychosocial
10. psychothérapies : approche psychanalytique
11. thérapies cognitivo-comportementales
12. approche systémique et thérapies familiales
13. thérapie centrée sur la personne (Rogers)
14. déontologie et code du psychologue
15. psychologie du travail et des organisations : motivation, leadership
16. psychologie sociale appliquée : communication et changement d'attitude
17. psychologie communautaire et santé mentale en contexte africain
18. psychologie de la santé : observance, éducation thérapeutique
19. psychologie scolaire et orientation
20. psychologie de la petite enfance et parentalité
21. neuropsychologie clinique : démences et vieillissement
22. psychopharmacologie (notions)
23. méthodologie de la recherche : élaboration d'un mémoire
24. analyse de variance et régression
25. épistémologie de la psychologie et statut des sciences humaines

### L3 Géographie (`l3-geo`)

1. épistémologie de la géographie : grandes écoles
2. géographie physique appliquée : géomorphologie tropicale
3. risques naturels : inondations, glissements de terrain, sécheresses
4. changement climatique : causes, effets, accords internationaux
5. gestion de l'eau : bassins transfrontaliers (lac Tchad, Congo, Niger)
6. biodiversité, aires protégées et parcs nationaux
7. déforestation et gestion des forêts du bassin du Congo
8. environnement et développement durable
9. géopolitique : États, frontières et conflits
10. frontières africaines héritées de la colonisation
11. géographie du développement : indicateurs (IDH, PIB, indice de pauvreté)
12. économie spatiale et modèles de localisation (Von Thünen, Christaller)
13. métropolisation et réseaux de villes
14. foncier et gestion des terres
15. urbanisme et planification urbaine
16. systèmes d'information géographique : couches, bases de données
17. télédétection : images satellitaires et indices
18. statistiques spatiales et analyse des données
19. géographie des mobilités et migrations internationales
20. géographie de l'énergie et transition énergétique
21. géographie du Cameroun : régions, économie et aménagement
22. espaces transfrontaliers et intégration régionale
23. méthodologie du mémoire de géographie
24. cartographie numérique et cartes participatives

### L3 Littérature (`l3-lit`)

1. théorie du roman : Lukács, Bakhtine, Barthes
2. esthétique de la réception
3. psychocritique et sociocritique
4. postcolonialisme et littératures du Sud
5. littérature africaine anglophone : Achebe, Soyinka, Ngugi
6. roman africain après les indépendances : désenchantement
7. écriture de la violence et de la mémoire : Kourouma, Mudimbe
8. la littérature féminine africaine : Mariama Bâ, Aminata Sow Fall
9. la littérature camerounaise contemporaine : Mongo Beti, Patrice Nganang
10. théâtre contemporain d'expression française
11. poésie contemporaine d'Afrique et des Antilles
12. littératures antillaise et créole : Césaire, Glissant, Chamoiseau
13. la créolité et la théorie du Tout-monde
14. littérature maghrébine francophone : Kateb Yacine, Assia Djebar
15. littérature du XXe siècle : Proust, Gide, Céline, Malraux
16. autobiographie et autofiction
17. poétique du roman policier et de la littérature de genre
18. littérature et histoire : roman historique
19. littérature et politique : engagement et responsabilité
20. analyse du discours littéraire
21. poétique de la traduction
22. oralité et écriture dans les littératures africaines
23. édition et diffusion du livre en Afrique
24. littérature de jeunesse
25. littérature numérique (notions)
26. méthodologie du mémoire de lettres : problématique et bibliographie

### L3 Histoire (`l3-hist`)

1. historiographie : écoles historiques, École des Annales
2. histoire orale et sources orales africaines
3. archéologie et histoire de l'Afrique centrale
4. l'historiographie africaine : Cheikh Anta Diop, Joseph Ki-Zerbo
5. le Cameroun précolonial : chefferies, lamidats, sultanats (Bamoun, Bamiléké, Fulbé)
6. l'islam et les jihads du XIXe siècle en Afrique
7. commerce transsaharien et commerce à longue distance
8. économies coloniales : cultures de rente, travail forcé
9. l'indigénat et le code du travail
10. l'éducation et les missions chrétiennes
11. urbanisation coloniale : Douala, Yaoundé
12. mouvements syndicaux et associatifs
13. luttes pour l'indépendance au Cameroun : de l'UPC au maquis
14. histoire politique du Cameroun depuis 1960 : Ahidjo, Biya
15. le retour du multipartisme (1990)
16. histoire des institutions camerounaises
17. l'intégration régionale : CEMAC, CEEAC
18. l'Afrique dans les relations internationales
19. les dettes, les ajustements structurels et le développement
20. la mondialisation et l'Afrique contemporaine
21. histoire du genre et des femmes en Afrique
22. mémoire, patrimoine et restitution des biens culturels
23. histoire de la santé et des épidémies
24. histoire des frontières et des conflits frontaliers (Bakassi)
25. méthodologie du mémoire d'histoire : critique des sources et bibliographie

### L3 Chimie (`l3-chim`)

1. chimie quantique : orbitales moléculaires et théorie de Hückel
2. symétrie moléculaire et théorie des groupes (introduction)
3. spectroscopies moléculaires : UV-visible, IR, RMN du proton et du carbone
4. spectrométrie de masse
5. chimie organique avancée : réactions péricycliques
6. synthèse organique : rétrosynthèse et groupes protecteurs
7. organométalliques : réactifs de Grignard, couplages catalysés par le palladium
8. chimie des hétérocycles
9. stéréosélectivité et synthèse asymétrique
10. chimie des polymères : masses molaires et propriétés
11. chimie inorganique : complexes, théorie du champ cristallin
12. chimie des solides : réseaux cristallins et structures types
13. électrochimie avancée : cinétique électrochimique, batteries
14. thermodynamique statistique de base
15. cinétique complexe : mécanismes et approximation de l'état quasi stationnaire
16. photochimie et réactions radicalaires
17. chimie analytique : méthodes spectroscopiques, chromatographiques et électrochimiques
18. incertitudes et validation des méthodes
19. chimie de l'environnement : pollution de l'eau et de l'air
20. traitement des eaux et potabilisation
21. chimie des substances naturelles et plantes médicinales
22. chimie agroalimentaire et conservation
23. chimie industrielle : pétrole, engrais, ciment
24. chimie verte et développement durable
25. sécurité chimique et gestion des déchets

### L3 Biologie (`l3-bio`)

1. biologie moléculaire : régulation de la transcription, opéron lactose
2. épissage et maturation des ARN
3. biologie cellulaire avancée : trafic membranaire
4. signalisation cellulaire et récepteurs
5. cytosquelette et motilité cellulaire
6. cancer : oncogènes et gènes suppresseurs
7. apoptose et différenciation
8. génétique des populations : Hardy-Weinberg
9. génomique et séquençage
10. bio-informatique : alignement de séquences
11. génie génétique : plasmides, enzymes de restriction
12. protéomique et techniques d'analyse (électrophorèse, chromatographie)
13. biochimie métabolique : glycolyse, cycle de Krebs, chaîne respiratoire
14. métabolisme des lipides et des acides aminés
15. régulation hormonale du métabolisme
16. immunologie avancée : complexe majeur d'histocompatibilité, anticorps
17. virologie : VIH, hépatites, virus émergents
18. microbiologie médicale : résistance aux antibiotiques
19. parasitologie et maladies tropicales négligées
20. physiologie comparée
21. écologie des populations et des communautés
22. biodiversité et conservation en Afrique centrale
23. biologie du développement : embryogenèse et gènes Hox
24. physiologie végétale : hormones et stress
25. biotechnologie agricole et sécurité alimentaire
26. méthodologie de la recherche et mémoire de biologie

### L3 Philosophie (`l3-philo`)

1. philosophie africaine : ethnophilosophie, Tempels et ses critiques
2. Hountondji, Eboussi Boulaga, Towa
3. la négritude de Senghor et la critique de Césaire
4. Cheikh Anta Diop et l'héritage égyptien
5. l'Ubuntu et la conception africaine de la personne
6. Mudimbe et l'invention de l'Afrique
7. Achille Mbembe et la critique postcoloniale
8. Fanon : la violence et la décolonisation
9. philosophie contemporaine : Foucault, pouvoir et savoir
10. Derrida et la déconstruction
11. Deleuze et la différence
12. École de Francfort : Adorno, Horkheimer, Habermas
13. Arendt : totalitarisme et condition humaine
14. Levinas : l'éthique comme philosophie première
15. Ricœur : herméneutique et récit
16. Merleau-Ponty et le corps
17. pragmatisme américain : Peirce, James, Dewey
18. philosophie analytique : Russell, Wittgenstein tardif, Austin
19. philosophie des sciences contemporaine
20. bioéthique : vie, mort, consentement
21. éthique de l'environnement
22. philosophie de la technique et du numérique
23. philosophie politique de la démocratie en Afrique
24. philosophie de l'éducation
25. dissertation et méthodologie du mémoire de philosophie

### L3 Sociologie (`l3-socio`)

1. théories sociologiques contemporaines : Giddens, Habermas, Beck
2. sociologie du changement social et de la modernité
3. globalisation et inégalités mondiales
4. sociologie du développement et critiques de la modernisation
5. sociologie politique de l'Afrique : État, clientélisme, ethnicité
6. décentralisation et pouvoirs locaux
7. sociologie de l'économie informelle
8. sociologie du travail et du chômage des jeunes
9. migrations et diasporas
10. sociologie de la ville africaine : quartiers, mobilités
11. sociologie de la famille contemporaine
12. sociologie du genre et des violences
13. sociologie de la santé : systèmes de santé et médecine traditionnelle
14. sociologie de l'éducation : accès, échec, orientation
15. sociologie des religions et du pluralisme religieux en Afrique
16. sociologie des conflits et de la paix
17. sociologie de l'environnement et des risques
18. sociologie du numérique et des réseaux sociaux
19. sociologie des organisations : bureaucratie, pouvoir
20. sociologie de la jeunesse
21. méthodes d'enquête : conception d'un protocole
22. analyse statistique : tableaux croisés, khi-deux, régression
23. analyse qualitative : codage et analyse thématique
24. éthique de la recherche sociale
25. méthodologie du mémoire de sociologie
