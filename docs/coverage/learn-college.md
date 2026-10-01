# Couverture « Apprendre » — premier cycle francophone (6e → 3e, BEPC)

Branche `claude/content-learn-college` (base `integration/agents`), généré le 2026-10-01. **Tout ce contenu est une bêta rédigée avec une IA : statut `draft`, notes de relecture (`reviewNotes`) sur chaque fiche, à valider par des enseignants dans ~3 mois.** Les programmes MINESEC (APC) n'ont pas pu être consultés dans leur texte officiel : les thèmes ci-dessous sont reconstitués de mémoire et **doivent être confrontés au programme officiel** ; les doutes de classe/de découpage sont écrits dans les  de chaque fiche. Contenu original, sans extraits de manuels ni d'annales.

## Tableau (compté dans les sources `content/learn/`)

| Classe | Matière | Pack | Examen | Chapitres | Fiches | Exercices (dont auto-éval.) | Épreuves blanches | Illustrations | Notes de relecture |
|---|---|---|---|---|---|---|---|---|---|
| 6e | Mathématiques | 6e-maths | — | 7 | 16 | 176 (80) | 0 | 19 | 21 |
| 6e | Français | 6e-francais | — | 6 | 29 | 319 (145) | 0 | 31 | 35 |
| 6e | Anglais | 6e-english | — | 6 | 19 | 209 (95) | 0 | 19 | 19 |
| 6e | SVT | 6e-svt | — | 11 | 13 | 143 (65) | 0 | 15 | 17 |
| 6e | Histoire-Géographie-ECM | 6e-histoire-geo | — | 9 | 16 | 176 (80) | 0 | 16 | 29 |
| 6e | Informatique | 6e-informatique | — | 4 | 9 | 99 (45) | 0 | 7 | 18 |
| 5e | Mathématiques | 5e-maths | — | 7 | 20 | 220 (100) | 0 | 21 | 26 |
| 5e | Français | 5e-francais | — | 6 | 24 | 264 (120) | 0 | 24 | 30 |
| 5e | Anglais | 5e-english | — | 8 | 18 | 198 (90) | 0 | 18 | 19 |
| 5e | SVT | 5e-svt | — | 6 | 10 | 110 (50) | 0 | 10 | 16 |
| 5e | PCT | 5e-pct | — | 5 | 10 | 110 (50) | 0 | 13 | 20 |
| 5e | Histoire-Géographie-ECM | 5e-histoire-geo | — | 7 | 16 | 176 (80) | 0 | 16 | 34 |
| 5e | Informatique | 5e-informatique | — | 5 | 9 | 99 (45) | 0 | 8 | 18 |
| 4e | Mathématiques | 4e-maths | — | 6 | 15 | 165 (75) | 0 | 15 | 18 |
| 4e | Français | 4e-francais | — | 5 | 13 | 143 (65) | 0 | 11 | 18 |
| 4e | Anglais | 4e-english | — | 5 | 15 | 165 (75) | 0 | 15 | 16 |
| 4e | SVT | 4e-svt | — | 6 | 12 | 132 (60) | 0 | 13 | 25 |
| 4e | PCT | 4e-pct | — | 5 | 14 | 154 (70) | 0 | 16 | 27 |
| 4e | Histoire-Géographie-ECM | 4e-histoire-geo | — | 3 | 16 | 176 (80) | 0 | 15 | 27 |
| 4e | Informatique | 4e-informatique | — | 6 | 11 | 121 (55) | 0 | 12 | 17 |
| 3e | Mathématiques | bepc-maths | BEPC | 14 | 15 | 173 (75) | 2 | 18 | 21 |
| 3e | Français | bepc-francais | BEPC | 11 | 15 | 171 (75) | 2 | 15 | 25 |
| 3e | Anglais | bepc-english | BEPC | 5 | 14 | 159 (70) | 1 | 14 | 16 |
| 3e | SVT | bepc-svt | BEPC | 7 | 10 | 115 (50) | 1 | 11 | 25 |
| 3e | PCT | bepc-pct | BEPC | 10 | 15 | 173 (75) | 2 | 17 | 33 |
| 3e | Histoire-Géographie-ECM | bepc-histoire-geo | BEPC | 4 | 19 | 220 (95) | 1 | 19 | 22 |
| 3e | Informatique | 3e-informatique | — | 5 | 10 | 110 (50) | 0 | 9 | 17 |
| | **Total** | 27 packs | | | **403** | **4476** | **9** | **417** | |

Notes : `bepc-maths`, `bepc-francais`, `bepc-pct` existaient (5, 4 et 4 fiches) et ont été **étendus** (version 2) ; les autres packs sont nouveaux. Chaque fiche suit la « fiche type » (objectifs, l'essentiel, 2 exemples résolus, 3 exercices d'application + 2 d'approfondissement + 1 type examen, 5 auto-évaluations, erreurs fréquentes). Pas d'épreuve blanche en 6e/5e/4e (pas d'examen) ; les packs BEPC en ont une ou deux. Un nouveau sujet `informatique` a été enregistré dans `LearnCatalog` (une ligne). Il n'y a pas de matière « ECM » séparée dans le catalogue : l'ECM est dans « Histoire-Géographie-ECM » (chapitres préfixés « ECM — »).

## Taille et lots

Chaque pack zippé pèse 27 à 68 ko (total des 40 packs du dépôt ≈ 1,3 Mo ; ≈ 0,23-0,29 Mo par classe pour les 7 matières). Un lot « une classe » reste très inférieur à 3 Mo.

## Estimation de couverture

Estimation prudente, par rapport au programme officiel **tel que reconstitué** (à confirmer) : mathématiques ≈ 80 % (probabilités absentes, PGCD/PPCM, relatifs ×÷), français ≈ 80 % (lecture suivie d'œuvres, dissertation), anglais ≈ 75 % (vocabulaire thématique, phonétique), SVT ≈ 75 %, PCT ≈ 70 %, histoire-géographie-ECM ≈ 75 %, informatique ≈ 60 % (programme officiel très incertain). Ces pourcentages sont des estimations de l'auteur, non mesurées.

## Écarts généraux

- Le texte des programmes MINESEC n'a pas été consulté ; ordre, répartition 5e/4e/3e et vocabulaire officiels à vérifier.
- Pas de vidéo, pas d'audio (hors quelques blocs « dictée » lus par la synthèse vocale), pas de cartes détaillées du Cameroun (frises et schémas uniquement).
- Format des épreuves du BEPC (durée, barème, parties) : épreuves blanches proposées à titre d'entraînement.
- Santé (SVT) : formulations prudentes, relecture par un agent de santé nécessaire.

## Détail par matière (notes des rédacteurs : programme, couvert, manques)

### Mathématiques 6e/5e

Programme officiel reconstitué de mémoire : à confronter au texte officiel (aucune source consultée). Tout est `draft`.

#### 6e-maths — 16 fiches, 176 exercices (80 auto-évaluations), 19 figures
Thèmes du programme tels que connus : numération et décimaux ; opérations (division euclidienne, priorités) ; multiples/diviseurs, critères de divisibilité ; fractions ; proportionnalité/pourcentages ; droites, angles, triangles, quadrilatères, cercle, symétrie axiale ; périmètres, aires, volumes ; unités (longueur, masse, capacité, durée, FCFA) ; organisation de données.
Couvert :
- Nombres : Lire, écrire et comparer les décimaux ; Grands nombres, puissances de 10 et arrondis
- Opérations : Division euclidienne et priorités ; Multiples, diviseurs, critères de divisibilité
- Fractions : lecture, représentation, comparaison ; additions, soustractions, fraction d'une quantité
- Proportionnalité : tableaux, règle de trois, pourcentages simples
- Géométrie : droites/demi-droites/segments/parallèles/perpendiculaires/milieu ; angles ; triangles et quadrilatères ; cercle ; symétrie axiale
- Mesures : périmètres et aires (carré, rectangle, triangle, cercle) ; unités de mesure ; volumes (cube, pavé)
- Données : tableaux d'effectifs et diagrammes en barres
Incertain / manque : opérations posées sur décimaux (addition/soustraction/multiplication/division de décimaux, traitées seulement via exemples) ; PGCD/PPCM et nombres premiers (non traités) ; angles complémentaires/supplémentaires (placés ici, parfois 5e) ; construction géométrique détaillée (rapporteur/compas : texte seulement) ; aire du disque (reportée en 5e) ; diagramme circulaire/bâtons ; repérage sur droite graduée en 6e ; conversion des unités d'aire.

#### 5e-maths — 20 fiches, 220 exercices (100 auto-évaluations), 21 figures
Thèmes : nombres relatifs ; fractions ; calcul littéral ; proportionnalité/échelles/pourcentages ; angles, triangles, parallélogramme, symétrie centrale ; aires et volumes ; statistiques.
Couvert :
- Relatifs : droite graduée, opposé, comparaison ; addition et soustraction ; repérage dans le plan
- Fractions et puissances : simplifier/additionner/soustraire ; produit ; puissances (carré, cube, 10^n)
- Calcul littéral : expressions littérales (valeur, réduction, distributivité simple) ; équations simples
- Proportionnalité : tableaux, vitesse, échelles ; pourcentages
- Géométrie : angles opposés/parallèles ; somme des angles du triangle ; inégalité triangulaire et constructions ; parallélogramme ; symétrie centrale ; médiatrice
- Mesures : aires (parallélogramme, trapèze, disque) ; prisme droit et cylindre
- Statistiques : fréquences et diagrammes ; moyenne, médiane, étendue
Incertain / manque : multiplication et division de relatifs (probablement 4e) ; quotient de fractions ; règles de calcul sur les puissances ; distributivité à deux termes / identités ; équations à inconnue des deux côtés ; configurations détaillées de constructions (bissectrice, hauteurs, médianes) ; pavé/cube vus en 6e ; diagramme circulaire dessiné (angles seulement) ; vocabulaire exact des manuels officiels.

### Mathématiques 4e/3e

Sources : `src/4e_maths.py` (+ m4_a..m4_e), `src/bepc_maths_ext.py` (+ b3_a..b3_c). Programme officiel MINESEC connu de mémoire : à vérifier.

#### 4e-maths (nouveau pack, 15 fiches, 165 exercices dont 75 d'auto-évaluation, 15 illustrations)
Thèmes du programme (de mémoire, doutes signalés) : nombres relatifs, fractions, puissances, racines carrées (initiation), calcul littéral, équations et inéquations, proportionnalité/pourcentages, statistiques, Pythagore, droite des milieux/Thalès (initiation), cosinus, translations/vecteurs, cercle/tangente, angle inscrit, problèmes.
Couvert : relatifs ; fractions ; puissances et écriture scientifique ; racines carrées (initiation) ; calcul littéral (réduire, développer, factoriser simple) ; équations et inéquations du 1er degré ; proportionnalité, pourcentages, échelles ; statistiques (effectifs, fréquences, moyenne, diagrammes) ; problèmes (méthode, vitesse) ; Pythagore et réciproque ; droite des milieux et Thalès (initiation) ; cosinus ; cercle (position relative, tangente, corde) ; angle inscrit / au centre ; translations et vecteurs (Chasles).
Doutes : cosinus, angle inscrit, vecteurs et exposants négatifs peuvent relever de la 3e selon les manuels ; la médiane et les classes sont reportées en 3e.
Manque : arithmétique (PGCD, nombres premiers), angles et parallèles, symétrie centrale, solides (prisme, cylindre), repérage dans le plan, aires et volumes usuels, constructions de triangles, systèmes d'équations.

#### bepc-maths (étendu : v1 -> v2, +10 fiches nouvelles, +2e épreuve blanche)
Existant non modifié : calcul littéral/identités, équations-inéquations-systèmes, Pythagore, Thalès, fonctions linéaires et affines, épreuve blanche 1.
Ajouté (fiches) : radicaux ; ordre, encadrement, valeurs approchées, intervalles ; statistiques (classes, moyenne, médiane, étendue) ; trigonométrie du triangle rectangle ; angles inscrits et polygones réguliers ; vecteurs et coordonnées (norme, parallélogramme, translation, milieu) ; homothéties ; pyramide/cône/sphère (aires et volumes) ; droites dans un repère (distance, milieu, équation, parallèles/perpendiculaires) ; problème type BEPC (Pythagore + Thalès + trigonométrie).
Épreuve blanche 2 : 4 exercices, total 20 points (8 + 6 + 6), aucun exercice `review`.
Doutes : probabilités volontairement absentes (programme 3e MINESEC incertain) ; colinéarité non traitée ; perpendicularité par produit des pentes et rationalisation à confirmer ; valeurs exactes sin/cos/tan de 30-45-60 à confirmer.
Manque : fonctions (compléments), systèmes d'inéquations, théorème de la médiane, cylindre/prisme en détail, section de solides, probabilités, problèmes de dénombrement, constructions.

### Français 6e/5e

Sources : fichiers `src/6e_francais.py` (+ `f6_*.py`) et `src/5e_francais.py` (+ `f5_*.py`), helpers de schémas dans `src/fr_common.py`.
Tous les textes (récits, fable, poème, dictées, textes documentaires) sont originaux. Statut `draft` partout.

#### 6e-francais : 29 fiches, 319 exercices (dont 145 auto-évaluations), 31 illustrations

Thèmes du programme officiel tels que je les connais (non vérifié sur le texte MINESEC/APC) : types et formes de phrases, nature et fonction des mots, groupe nominal, sujet-verbe, COD/COI/CC/attribut, présent, imparfait, futur, passé composé, passé simple (3es personnes), impératif, accords, homophones, vocabulaire (familles, préfixes/suffixes, synonymes/antonymes, sens propre/figuré, dictionnaire, champ lexical), récit/description/dialogue/conte-fable/texte informatif, rédaction (récit, portrait, lettre), dictée, poésie.

Couvert :
- Grammaire : La phrase (types et formes) ; La nature des mots ; Le groupe nominal et ses expansions ; Le verbe et son sujet ; COD, COI, attribut et compléments circonstanciels.
- Conjugaison : Présent 1er/2e groupes ; Présent être, avoir et 3e groupe ; Imparfait ; Futur simple ; Passé composé ; Passé simple (3e personne) ; Impératif.
- Orthographe : Féminin et pluriel noms/adjectifs ; Accord sujet-verbe ; Homophones (a/à, et/est, on/ont, son/sont, ou/où, ce/se, ces/ses) ; é/er/ez.
- Vocabulaire : Familles de mots, préfixes, suffixes ; Synonymes, antonymes, sens propre/figuré ; Dictionnaire et champ lexical.
- Lecture : Récit (schéma narratif) ; Description ; Dialogue ; Conte et fable ; Texte informatif.
- Expression et divers : Rédiger un récit ; Décrire (lieu, portrait) ; Lettre personnelle ; Dictée préparée ; Poésie (vers, rimes, comparaison, métaphore, personnification).

Manque / incertain :
- Pas de fiche sur : les déterminants en détail (possessifs/démonstratifs), les pronoms personnels, les degrés de l'adjectif, la ponctuation (virgule, deux-points), le conditionnel (renvoyé en 5e), le futur antérieur, l'accord du participe avec avoir (renvoyé en 5e), le compte rendu et le résumé (5e).
- Pas d'épreuve blanche (pas d'examen en 6e).
- Ordre et contenu exacts du programme officiel (APC) non vérifiés ; chaque fiche a des `notes` de relecture.
- Exemples camerounais présents (Nkeng, Mballa, Amina, Boris, marchés, manguiers, ndolé) ; aucune expression locale du français camerounais n'est traitée.

#### 5e-francais : 24 fiches, 264 exercices (dont 120 auto-évaluations), 24 illustrations

Thèmes du programme officiel tels que je les connais (à confirmer) : propositions (indépendantes, principale, subordonnées), compléments circonstanciels, voix passive, pronoms, discours rapporté (initiation), passé simple, plus-que-parfait, conditionnel, subjonctif (peut être en 4e), accord du participe passé, homophones, registres de langue, formation des mots, récit au passé, portrait/description, texte argumentatif simple, lettre, résumé/compte rendu, poésie, dictée.

Couvert :
- Grammaire : Phrase simple/complexe et propositions ; Subordonnée relative ; Compléments circonstanciels ; Voix active/passive ; Pronoms personnels, possessifs, démonstratifs ; Discours direct/indirect (initiation) ; Expansions du nom (épithète, apposition, complément du nom, relative).
- Conjugaison : Passé simple (toutes personnes) ; Plus-que-parfait ; Conditionnel présent ; Subjonctif présent (initiation).
- Orthographe : Accord du participe passé ; Homophones 2 (c'est/s'est, sa/ça, la/là/l'a, leur(s), quel(le)/qu'elle) ; Accords particuliers (couleurs, vingt et cent).
- Vocabulaire : Registres de langue ; Mots composés, abréviations, sigles, emprunts.
- Lecture : Récit au passé ; Portrait et description ; Texte argumentatif simple.
- Expression et divers : Rédiger un récit au passé avec dialogue ; Lettre (amicale et de demande) ; Résumé et compte rendu ; Poésie (mètre, rimes, figures) ; Dictée préparée.

Manque / incertain :
- Pas de fiche sur : subordonnées conjonctives circonstanciel détaillées (cause, but, conséquence, condition), complétives, futur antérieur, concordance des temps complète (3e), accord du participe suivi d'un infinitif, pluriel des noms composés, texte théâtral, texte de presse, conte/fable en 5e.
- Le conditionnel et le subjonctif peuvent relever de la 4e selon le programme : note de relecture dans les fiches.
- Emprunts (football, pizza, sucre, safari) : origines à confirmer dans un dictionnaire étymologique ; rectifications orthographiques (nombres) à confirmer.
- Pas d'épreuve blanche (pas d'examen en 5e).

#### Validation
`check.sh 6e-francais 5e-francais` : OK, crosscheck OK. `build.sh` (tous packs) : PASS sur tous les tests, OK final.

### Français 4e/3e

#### 4e-francais (13 fiches, 143 exercices dont 65 d'auto-évaluation)
1. Thèmes du programme MINESEC tels que je les connais (non vérifiés sur le texte officiel) : grammaire de la phrase (propositions, subordination), conjugaison (temps du récit, modes), accords (participe passé), vocabulaire (champs lexicaux, formation des mots), figures simples, étude de textes (narratif, descriptif, argumentatif), expression écrite (récit, dialogue, lettre, portrait).
2. Couvert : propositions ; relatives ; circonstancielles ; passé simple/imparfait ; subjonctif et conditionnel présent ; participe passé ; champs lexicaux, famille de mots, préfixes/suffixes ; figures simples ; texte narratif ; texte descriptif ; texte argumentatif (initiation) ; récit et dialogue ; lettre familière/formelle.
3. Manque : fonctions grammaticales (sujet, COD, COI, attribut, compléments), types de phrases, pronoms et déterminants, adverbes, discours rapporté en 4e, étude d'œuvre intégrale, texte injonctif/explicatif, orthographe lexicale, poésie, théâtre. Doute : la place du texte argumentatif et des figures en 4e.

#### bepc-francais (extension : 11 nouvelles fiches, 124 nouveaux exercices ; pack total 15 fiches, 171 exercices, 2 épreuves blanches)
1. Programme de 3e : phrase complexe, voix, discours rapporté (déjà présents), figures de style, registres, argumentation, résumé / contraction / discussion, compréhension de texte, dictée/orthographe, conjugaison, vocabulaire, poésie, rédaction. Doute : dissertation, contraction/discussion au BEPC.
2. Ajouté : figures de style ; registres ; argumentation (thèse adverse, concession, réfutation) ; compréhension et questions types ; résumé ; contraction et discussion (initiation) ; conjugaison de révision ; orthographe et dictée ; vocabulaire (sens, niveaux de langue, racines) ; lecture de poème (versification) ; rédaction (imagination / réflexion) ; épreuve blanche n° 2 (20 points, aucun exercice review).
3. Manque : dissertation, commentaire composé, étude d'œuvres, théâtre, texte injonctif, grammaire de la phrase (types, fonctions) de révision, fiches de méthode BEPC officielles (barèmes réels non vérifiés).

### Anglais 6e/5e

Statut : brouillon (draft), consignes en français, contenu cible en anglais. Programme officiel MINESEC (APC) non consulté directement : thèmes reconstitués de mémoire, à vérifier.

#### 6e-english (19 fiches, 209 exercices dont 95 d'auto-évaluation)
1. Thèmes du programme tels que connus : salutations et présentations, alphabet et épellation, nombres/dates/heure, famille et école, pronoms sujets, to be / to have, présent simple, present continuous, articles et pluriels, possessifs, there is/are, prépositions de lieu, impératif, questions en wh-, courts dialogues, lecture et écriture de messages courts. Doutes : ordre exact, volume (nombres jusqu'à 100 ?), présence de can/like/love + -ing, d'adverbes de fréquence, de la prononciation phonétique.
2. Couvert : Greetings and introductions ; The alphabet and spelling ; Numbers 0-100 ; Days, months and time ; Family and school vocabulary ; Pronouns and to be ; to have ; Articles ; Plurals ; Possessives and 's ; There is/are ; Prepositions of place ; Present simple ; Present continuous ; Imperatives and classroom language ; Wh-questions ; Short dialogues (marché, école) ; Reading short texts ; Writing short messages.
3. Manque : vocabulaire thématique plus large (corps, vêtements, couleurs, aliments, métiers, météo), phonétique, this/that/these/those, adjectifs qualificatifs, can (traité en 5e), textes plus longs, épreuves blanches.

#### 5e-english (18 fiches, 198 exercices dont 90 d'auto-évaluation)
1. Thèmes du programme : past simple (réguliers/irréguliers, négation, questions), can/must/should, comparatifs et superlatifs, dénombrables/indénombrables et quantifieurs, futur (going to, will), routines et adverbes de fréquence, directions, description de personnes et de lieux, compréhension écrite, écriture guidée (lettre, e-mail, description). Doutes : le niveau exact (present perfect ? conditionnel type 0/1 ? passif ?), la place de can/must en 6e ou 5e, la longueur des productions écrites attendues.
2. Couvert : Past simple regular ; Past simple irregular ; Past simple negatives/questions ; Can/could ; Must/have to ; Should ; Comparatives ; Superlatives ; Countable/uncountable ; Quantifiers ; going to ; will ; Daily routines and frequency ; Directions ; Describing people ; Describing places ; Reading comprehension ; Writing a letter/email.
3. Manque : present perfect, past continuous, conjonctions/relatives, vocabulaire thématique (santé, voyage, métiers), paragraphes de description plus longs, épreuves blanches, textes d'écoute (audio).

### Anglais 4e/3e

#### 4e-english (15 fiches, 165 exercices dont 75 auto-évaluations ; check.sh OK)
Programme tel que je le connais (non vérifié sur le texte officiel) : present perfect (initiation), past simple / continuous, futurs, modaux,
conditionnel type 1, passif (initiation), discours rapporté (initiation), relatifs, connecteurs, phrasal verbs, compréhension de texte,
expression écrite guidée (lettre, récit, description), vocabulaire santé / environnement / ville / métiers.
Couvert : present-perfect, past-simple-continuous, future-forms, modals, conditional-1, passive-voice, reported-speech, relative-pronouns,
connectors, phrasal-verbs, reading-comprehension, writing-letter, writing-narrative-description, health-environment, town-jobs.
Manque / incertain : liste officielle des verbes irréguliers et des phrasal verbs ; used to, there is/are, quantifiers, articles, prépositions,
adverbes de fréquence, degrés de comparaison (non traités en 4e) ; compréhension orale ; phonétique ; thèmes culturels du programme.

#### bepc-english (14 fiches, 159 exercices dont 70 auto-évaluations ; épreuve blanche 20 pts ; check.sh OK)
Programme tel que je le connais : révision des temps, conditionnels 0-1-2, passif, discours rapporté (déclaratif, questions, ordres), relatives,
question tags, comparaison, mots de liaison, compréhension, composition guidée (lettre informelle, essai, dialogue), vocabulaire, Use of English.
Couvert : tenses-review, futures-review, conditionals, passive-voice, reported-speech, relative-clauses, question-tags, comparison, linking-words,
reading-comprehension, composition-letter-dialogue, composition-essay, vocabulary-themes, use-of-english + épreuve blanche (reading 5, cloze 5,
transformations 4, vocabulary 2, composition 4).
Manque / incertain : format officiel du BEPC anglais (durée, barème, parties, coefficient) — la maquette est indicative ; conditionnel type 3 et
wish / I wish (non traités) ; gerund/infinitive, prépositions, articles, quantifiers, modals de déduction ; compréhension orale ; phonétique.
Remarque technique : les exercices de l'épreuve blanche (ids bepc-english-eb-NN) renvoient chacun à une fiche existante.

### SVT 6e/5e

Sources : connaissance générale du programme MINESEC (APC) ; texte officiel non consulté, tout est à vérifier.

#### 6e-svt (13 fiches, 143 exercices dont 65 d'auto-évaluation, 15 figures)
Thèmes du programme tels que je les connais : démarche scientifique, vivant/non-vivant, cellule (initiation), classification, nutrition des plantes vertes, milieu et chaînes alimentaires, sol, eau, air, hygiène et santé, alimentation, corps humain. Doute : intitulés exacts, distribution entre 6e et 5e (cellule, corps humain), niveau de détail (biotope/biocénose, classes de vertébrés).
Couvert : La démarche scientifique ; Êtres vivants et non vivants ; La cellule ; Classer les animaux et les plantes ; Comment se nourrit une plante verte ; Milieu de vie et écosystème ; Chaînes alimentaires ; Le sol ; L'eau (cycle, pollution) ; L'air ; Hygiène, microbes, paludisme ; Alimentation équilibrée ; Corps humain et grandes fonctions.
Manque : respiration/germination des graines en détail, champignons et bactéries, microscope (mode d'emploi), météo, travaux pratiques détaillés, épreuve blanche, relief/géologie de 6e éventuelle.

#### 5e-svt (10 fiches, 110 exercices dont 50 d'auto-évaluation, 10 figures)
Thèmes : nutrition (aliments, digestion), respiration, circulation, reproduction humaine et des plantes à fleurs, géologie (roches, volcans, séismes, érosion), ressources et environnement du Cameroun, santé (IST/VIH).
Couvert : Aliments, nutriments, besoins ; Digestion et absorption ; Respiration ; Sang et circulation ; Reproduction humaine ; Reproduction des plantes à fleurs ; Roches et érosion ; Volcans et séismes ; Milieux du Cameroun et déforestation ; IST, VIH/sida, prévention.
Manque : excrétion (reins, peau), hygiène bucco-dentaire, système nerveux/organes des sens approfondis, sol (formation détaillée), météorologie/climat, épreuve blanche, contraception et autres IST en détail (laissé hors programme).

#### Points à relire
Santé (paludisme, hygiène, alimentation, premiers soins, IST/VIH, reproduction humaine) : à faire relire par un agent de santé (notes= et review= posés). Séisme (consignes) : protection civile. Composition de l'air (78 % / 21 %), Mont Cameroun (volcan actif), lac Nyos 1986, aires protégées citées : à confirmer.

### SVT 4e/3e

#### 4e-svt (12 fiches, 132 exercices dont 60 auto-évaluations)
Programme (tel que connu, à vérifier sur le texte officiel) : nutrition et digestion, respiration, circulation et immunité (initiation), excrétion, reproduction des végétaux et animaux, géologie (roches, fossiles, volcans, séismes), écosystèmes, éducation à la santé.
Couvert : Aliments, digestion et absorption ; Alimentation équilibrée et maladies de la nutrition ; La respiration ; Le sang, le cœur et la circulation ; Microbes et défense (initiation) ; L'excrétion ; La fleur, la fécondation et la graine ; Multiplication végétative et reproduction des animaux ; Les roches et les fossiles ; Structure du globe, volcans et séismes ; Écosystèmes et chaînes alimentaires ; Éducation à la santé.
Manque / doutes : photosynthèse et nutrition des végétaux chlorophylliens (non traitée) ; musculo-squelettique ; tectonique des plaques peu détaillée ; sols ; exemples locaux de roches camerounaises ; niveau exact du détail (oreillettes, enzymes) ; VIH/IST à traiter selon le programme.
#### bepc-svt (10 fiches, 115 exercices dont 50 auto-évaluations ; 65 hors auto-évaluations ; 1 épreuve blanche de 20 points, 5 exercices)
Programme (3e, à vérifier) : reproduction humaine, hérédité, système nerveux et réflexes, organes des sens, hormones, immunité, évolution/biodiversité, ressources et environnement, histoire de la Terre.
Couvert : La reproduction humaine ; Chromosomes, gènes et transmission ; Croisements simples et lois de Mendel ; Système nerveux et réflexes ; Organes des sens ; Hormones et glycémie ; Immunité et vaccination ; Biodiversité, classification et évolution ; Ressources, déforestation et pollution ; Histoire de la Terre et fossiles ; Épreuve blanche.
Manque / doutes : dihybridisme, groupes sanguins, hérédité humaine (volontairement omis) ; mitose/méiose en détail ; cycle menstruel détaillé et contraception ; photosynthèse ; sols ; roches/tectonique en 3e ; lois et institutions camerounaises de l'environnement.
Santé et génétique : notes « à faire relire par un agent de santé » dans les fiches concernées ; exemples génétiques sur pois, maïs, souris.

### PCT 5e/4e

Programme officiel MINESEC (APC) non consulté : thèmes reconstitués de mémoire. Répartition exacte 5e/4e INCERTAINE.

#### 5e-pct (10 fiches, 110 exercices, 13 illustrations)
Thèmes attendus : mesures (longueur, masse, volume, température, instruments), états et changements d'état, eau (propriétés, traitement), mélanges/corps purs/solutions, séparations, air et combustion, électricité (circuit simple, sécurité), lumière, technologie (outils, objets techniques, dessin).
Couvert : longueur-masse ; volume ; états-changements (thermomètre inclus) ; melanges-corps-purs ; separation (filtration, décantation, évaporation, distillation) ; eau-potable ; air-combustion ; circuit-simple ; lumiere-ombre ; objets-techniques (outils, échelle, cotation).
Manque : incertitude de mesure approfondie, température (fiche dédiée : thermomètres, graduation), solubilité chiffrée/concentration, électricité (aimants, magnétisme ?), éclipses et phases de la Lune, matériaux (propriétés détaillées), énergie en technologie, chaîne de fabrication, schémas normalisés plus poussés.
Doutes : masse volumique (placée en 4e, peut être en 5e) ; modèle particulaire en 5e ; sens conventionnel du courant ; tension secteur 220 V ; Thalès pour l'ombre.

#### 4e-pct (14 fiches, 154 exercices, 16 illustrations)
Thèmes attendus : masse volumique, atome/molécule, corps simples/composés et réactions, acides/bases (indicateurs, pH), électricité (intensité, tension, série/dérivation, résistance, loi d'Ohm), poids et masse, forces et équilibre, lumière (réflexion, réfraction), énergie, technologie (leviers, engrenages).
Couvert : masse-volumique ; atomes-molecules ; corps-reactions (conservation de la masse, tests de gaz) ; acides-bases ; intensite ; tension ; serie-derivation ; resistance-ohm ; poids-masse (g = 10 N/kg, note g ≈ 9,8) ; forces-equilibre ; reflexion-refraction ; energie ; leviers ; engrenages.
Manque : combustions détaillées (bilans équilibrés avec formules), concentration massique, conductivité des solutions, associations de résistances en dérivation, puissance/énergie électrique (3e probable), poussée d'Archimède, pression, machines simples (poulies, plan incliné), dessin technique, Dilution.
Doutes : loi d'Ohm et pH peut-être 3e ; lois d'additivité série/dérivation ; loi du levier ; relation N×Z ; valeurs ρ arrondies ; Lune g ≈ 1,6 N/kg.

Validation : check.sh 5e-pct 4e-pct = OK, crosscheck OK. build.sh : aucun chevauchement de texte dans mes packs (seul échec : figureTextsDoNotOverlap sur 4e-histoire-geo-cameroun-population et 6e-svt-air, hors périmètre).
Bibliothèque : patch de `poly()` dans learn_author.py pour accepter des points en tuples (aplatis automatiquement).

### PCT 3e

#### 1. Thèmes du programme MINESEC de 3e tels que connus (incertitudes signalées)
- Électricité : circuits, loi d'Ohm, puissance et énergie (pack initial) ; effet Joule, courant alternatif, sécurité, consommation (incertain : formule Q = R I² t exigée ou non ; tension 220 ou 230 V).
- Mécanique : poids/masse/forces (initial) ; travail, énergies potentielle et cinétique, pression, poussée d'Archimède, leviers/moments (incertain : Ec = ½mv² et théorème des moments peut-être hors 3e).
- Optique : réflexion, lentilles convergentes (initial) ; dispersion de la lumière blanche.
- Chimie : atomes/ions/pH/acide-base (initial) ; réactions et équations-bilans, mole et masse molaire (très incertain en 3e), métaux et corrosion, hydrocarbures et combustions.
- Technologie / environnement : énergies renouvelables, rendement.

#### 2. Couvert (fiches ajoutées, v2 du pack)
- elec2 : Effet Joule et protection des circuits ; Courant alternatif, sécurité domestique et facture d'électricité
- meca2 : Travail, puissance mécanique, énergies cinétique et potentielle ; Pression et poussée d'Archimède ; Moment d'une force et leviers
- optique2 : Dispersion de la lumière blanche et couleurs
- chimie2 : Réactions chimiques et équations-bilans ; Quantité de matière, mole, masse molaire ; Métaux, alliages et corrosion ; Hydrocarbures et combustions
- env : Sources d'énergie, rendement et énergies renouvelables
- epreuve-blanche : Épreuve blanche n° 2 (20 points exactement, 4 problèmes, aucun exercice review)
Total : 11 nouvelles fiches (125 exercices dont 55 d'auto-évaluation) ; pack : 15 fiches, 2 épreuves blanches.

#### 3. Manque
- Électrolyse (initiation), solutions/dilution détaillées (la concentration massique est déjà dans la fiche initiale ; la concentration molaire est dans « mole »).
- Technologie : transmission du mouvement (poulies, engrenages, rapports), matériaux, schémas techniques.
- Optique : images par une lentille (formule de conjugaison, grandissement), œil et instruments, réfraction quantitative.
- Électricité : transformateur, montage mixte, résistances équivalentes.
- Prix du kWh : aucun tarif officiel utilisé (valeurs fictives indiquées dans les énoncés).

### Informatique 6e/5e

Programme officiel MINESEC : NON vérifié (aucun accès au texte). Les thèmes ci-dessous sont reconstitués de mémoire et à confronter au programme (titres, ordre, volume, vocabulaire, logiciels imposés).

#### 6e-informatique (9 fiches, 99 exercices dont 45 d'auto-évaluation, 7 figures)
1. Thèmes supposés : découverte de l'ordinateur (matériel/logiciel, périphériques), prise en main (allumer/éteindre, bureau, souris), clavier et saisie, fichiers/dossiers, unités de mesure de l'information, sauvegarde et supports, traitement de texte (premiers pas), dessin, ergonomie et sécurité.
2. Couvert : Matériel et logiciel ; Allumer, souris, éteindre ; Clavier et saisie ; Fichiers et dossiers (arborescence) ; Octet, ko, Mo, Go ; Enregistrer, sauvegarder, supports ; Traitement de texte ; Dessin ; Ergonomie, sécurité et bon usage.
3. Manque / doutes : histoire de l'informatique (générations, évolution), notion de réseau, bits et codage binaire au-delà de la mention 8 bits = 1 octet, système de fichiers détaillé (chemins), raccourcis clavier, logiciels précis imposés. Convention 1 ko = 1000 octets (certains manuels : 1024), à confirmer.

#### 5e-informatique (9 fiches, 99 exercices dont 45 d'auto-évaluation, 8 figures)
1. Thèmes supposés : traitement de texte avancé, tableur, Internet, courrier électronique, sécurité/vie privée, notions d'algorithme.
2. Couvert : Tableaux, images, mise en page ; Tableur : cellules ; Tableur : formules, somme, moyenne, graphique ; Internet et recherche ; Courrier électronique ; Mots de passe, arnaques, vie privée ; Respect en ligne, harcèlement, droit d'auteur ; Algorithme et ordinogramme ; Conditions et boucles.
3. Manque / doutes : publipostage, styles/sommaire, formules avec références absolues, fonctions MAX/MIN/SI, tri et filtre dans le tableur, présentation (diaporama), réseaux locaux, programmation visuelle (blocs) ou langage précis, texte de loi camerounais (volontairement non cité), syntaxe officielle du pseudo-code. Syntaxe du tableur (=SOMME, séparateurs) à vérifier selon la langue du logiciel.

### Informatique 4e/3e

Doute général : le programme officiel MINESEC d'informatique du premier cycle n'a pas pu être consulté ; les thèmes ci-dessous suivent la progression classique et la liste demandée. À faire valider par un enseignant. Pas d'examen (aucun BEPC d'informatique connu ; à confirmer).

#### 4e-informatique (11 fiches, 121 exercices)
1. Thèmes connus (incertains) : système d'exploitation, fichiers, numération, représentation de l'information, tableur, présentation, réseaux/Internet, algorithmique, initiation à la programmation.
2. Couvert : os-fichiers ; binaire ; hexadécimal (peut relever d'un niveau supérieur) ; représentation (texte, image, son, unités) ; tableur-formules ; tableur-fonctions (SOMME, MOYENNE, MIN, MAX, NB, SI, graphiques) ; diaporama ; reseau-internet (LAN, IP, URL, navigateur, courriel) ; algo-bases (variables, séquence, SI) ; algo-boucles (Pour, Tant que) ; algo-programme (test, bogue, cas limites).
3. Manque : traitement de texte (mise en forme) non traité ; périphériques et matériel (architecture) ; histoire de l'informatique ; programmation dans un langage ou outil de blocs précis ; exercices pratiques sur machine.
Doutes : Ko = 1000 octets (certains manuels : 1024) ; séparateurs d'arguments et noms de fonctions du tableur ; valeurs conseillées pour les diaporamas.

#### 3e-informatique (10 fiches, 110 exercices)
1. Thèmes connus (incertains) : algorithmique et programmation, bases de données, tableur avancé, réseaux et sécurité, citoyenneté numérique, métiers, projet.
2. Couvert : structures-controle (ET/OU/NON, compteur, cumul) ; tableaux (indices depuis 1) ; fonctions ; bases-de-donnees (table, champ, enregistrement, clé, sélection/tri) ; tableur-avance (référence absolue, SOMME.SI, NB.SI, tri/filtre) ; securite-informatique (menaces, protections, sauvegarde 3-2-1, combinaisons de mots de passe) ; droit-vie-privee ; cyber-desinformation ; metiers-numerique ; projet-synthese.
3. Manque : relations entre tables et langage de requête ; protocoles réseau détaillés ; chiffrement ; langage de programmation précis ; épreuve blanche (aucun examen).
Doutes : aucune référence légale précise (principes généraux seulement) ; indices de tableau à partir de 1 ; contenus de formation locaux pour les métiers.

### Histoire-Géographie-ECM 6e/5e

Les deux packs sont générés par `src/6e_histoire_geo.py` et `src/5e_histoire_geo.py` (modules `hg6_a/b/c`, `hg5_a/b/c`, `hg_common`).
Chaque pack compte 16 fiches (5 histoire, 6-7 géographie, 4-5 ECM) et 176 exercices dont 80 auto-évaluations.

#### 6e-histoire-geo (16 fiches)
Programme officiel MINESEC tel que je le connais : mal connu dans le détail. Je ne suis pas sûr de l'ordre des leçons ni du choix exact des civilisations (Égypte ? Koush, Aksoum ? Carthage ?).
- Histoire : sources et frise ; préhistoire ; Égypte ancienne ; Koush et Aksoum (programme incertain) ; premiers peuplements du Cameroun (faits prudents).
- Géographie : points cardinaux et plan ; carte et échelle ; latitude, longitude, hémisphères ; relief ; climats et végétation ; eau ; population (densité, nombres fictifs).
- ECM : école et règlement ; droits et devoirs ; famille et respect ; symboles de la nation et civisme.
- Manque : Carthage et autres civilisations (Nok, Sao), repères sur les Grecs et les Romains si le programme les prévoit ; vie économique de base ; la commune et le quartier ; santé et hygiène ; les langues du Cameroun ; les peuples (Sao...).

#### 5e-histoire-geo (16 fiches)
Programme officiel : mal connu ; le Moyen Âge européen, la civilisation arabo-musulmane et la Renaissance font peut-être partie du programme.
- Histoire : Ghana ; Mali ; Songhaï ; Cameroun précolonial (chefferies, lamidats, Douala, Njoya) ; traite négrière transatlantique ; débuts de la présence européenne (Rio dos Camarões, comptoirs, 1884).
- Géographie : régions et villes du Cameroun ; ensembles de relief ; agriculture ; ressources ; transports.
- ECM : citoyenneté et vivre ensemble ; égalité et discriminations ; paix et résolution des conflits ; environnement ; institutions (principes).
- Manque : Moyen Âge européen, monde arabo-musulman, Kanem-Bornou, civilisations d'Asie ; climats et hydrographie du Cameroun en détail (traités en 6e en survol) ; démographie du Cameroun ; santé ; décentralisation.

#### Doutes factuels signalés dans notes= / review=
Les dates sont arrondies (« environ »). Aucune statistique. Les fiches Ghana, Mali, Songhaï, traite, Rio dos Camarões, Njoya, symboles (sens des couleurs non donné), ressources, agriculture et transports ont des notes de relecture. Deux exercices portent review= (liste simplifiée des zones, café à l'Ouest).

### Histoire-Géographie-ECM 4e/3e

Avertissement : le découpage officiel MINESEC (APC) exact n'est pas connu avec certitude ; les thèmes ci-dessous sont ceux que l'auteur connaît. Tout est brouillon (bêta) à faire relire par un enseignant d'histoire-géographie. Aucun chiffre précis (superficies, populations, statistiques) n'est donné, seulement des ordres de grandeur marqués « environ ».

#### 4e-histoire-geo (4e, sans examen) — 16 fiches, 176 exercices (dont 80 auto-évaluations), 15 illustrations
Thèmes du programme (connus, découpage incertain) : Afrique et monde XVe-XIXe (traite, explorations, abolitions) ; Cameroun précolonial ; colonisation (protectorat allemand, mandat/tutelle) ; géographie du Cameroun, de l'Afrique, de la planète ; ECM (droits, institutions, genre, civisme fiscal, environnement).
Couvert :
- Histoire : repères de temps/siècles/frises ; traite négrière atlantique ; explorations et abolitions ; Cameroun précolonial (royaumes, chefferies, lamidats) ; protectorat allemand 1884-1916 ; mandat et tutelle 1916-1945.
- Géographie : la planète (continents, océans, équateur, Greenwich) ; l'Afrique (grands ensembles) ; Cameroun milieu ; Cameroun population et activités ; régions et chefs-lieux.
- ECM : droits de l'homme et de l'enfant ; symboles et institutions ; égalité des genres ; civisme fiscal ; environnement.
Manque : explorateurs du XIXe siècle (Livingstone, Stanley, Brazza), royaumes africains hors Cameroun (Ashanti, Mali, Songhaï), royaumes précoloniaux détaillés (Tikar, Bali, Fulbé), cartes, démographie détaillée, villes secondaires, ECM (famille, vie scolaire, santé/VIH, sécurité routière, etc.). Doutes : programme officiel de 4e (peut inclure l'Europe, l'Amérique, la Révolution industrielle).

#### bepc-histoire-geo (3e, BEPC) — 19 fiches + 1 épreuve blanche (20 points), 220 exercices (dont 95 auto-évaluations), 19 illustrations
Couvert :
- Histoire : Cameroun sous administration franco-britannique ; mouvements nationalistes ; indépendance 1960 et réunification 1961 ; construction de l'État (1961-1996) ; deux guerres mondiales ; décolonisation ; ONU et guerre froide ; OUA et UA.
- Géographie : agriculture/élevage/pêche ; forêt, mines, énergie ; industries, transports, commerce ; intégration régionale (CEMAC, CEEAC, CEDEAO) ; mondialisation ; environnement et développement durable.
- ECM : institutions de la République ; démocratie et élections ; droits et devoirs ; corruption ; paix et vivre-ensemble.
- Épreuve blanche (11 exercices, 20 points, aucun exercice review).
Manque : crises du XXe siècle (Moyen-Orient, Asie), apartheid détaillé, relations internationales du Cameroun, géographie des États-Unis/Europe/Asie si au programme, cartes, démographie, ECM (santé, VIH, sécurité routière). Doutes : détail du programme de 3e (peut inclure d'autres thèmes), format réel de l'épreuve du BEPC.

#### Points de vigilance factuels (voir aussi notes= de chaque fiche)
- Dates : traité juillet 1884 ; 1916 ; 1946 ; 1er janv. 1960 ; plébiscite 11 fév. 1961 ; 1er oct. 1961 ; 20 mai 1972 ; nov. 1982 ; 1984 ; fin 1990 ; 18 janv. 1996 ; OUA 25 mai 1963 ; UA 2002 ; ONU 1945 ; UPC 1948, interdite 1955, Um Nyobè 1958. À recouper.
- Citations de personnes : Nachtigal, Duala Manga Bell, Um Nyobè, Ahidjo, Biya, Foncha, Endeley, Nkrumah, Mandela, Njoya : faits minimaux, sans jugement.
- Conférence de Foumban (juillet 1961), CONAC (2006), âge de vote (omis), organe électoral (ELECAM) : à vérifier.
- Géographie : chefs-lieux des 10 régions, CEMAC (6 États, siège Bangui), ressources minières « potentielles » non affirmées comme exploitées.
