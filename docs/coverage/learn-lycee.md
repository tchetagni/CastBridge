# Couverture — Apprendre, second cycle francophone (seconde, première, terminale)

Branche `claude/content-learn-lycee` (depuis `integration/agents`), 2026-10-01. Tout le contenu est un **brouillon rédigé avec une IA**, original, `status: draft`, avec `reviewNotes` par fiche ; **aucune** validation par un enseignant (relecture prévue 3 mois après la distribution aux testeurs bêta). L'application l'affiche « bêta ».

Les programmes officiels MINESEC n'étant pas disponibles dans l'environnement, les « thèmes attendus » ci-dessous sont les contenus **couramment attendus** au second cycle camerounais, **à vérifier sur les textes officiels**. Les pourcentages sont des estimations de couverture de ces thèmes, pas des mesures.

## 1. Tableau récapitulatif (nouveaux packs de cette branche)

| Classe | Pack | Matière | Série(s) | Examen | Fiches | Exercices (hors auto-éval.) | Auto-éval. | Épreuves blanches | Taille (ko) |
|---|---|---|---|---|---|---|---|---|---|
| 2nde | 2nde-chimie-c | chemistry | C | — | 4 | 27 | 20 | 1 | 75 |
| 2nde | 2nde-english | english | général | — | 5 | 33 | 25 | 1 | 80 |
| 2nde | 2nde-francais | francais | général | — | 5 | 33 | 25 | 1 | 111 |
| 2nde | 2nde-histoire-geo-ecm | histoire-geo | général | — | 6 | 40 | 30 | 1 | 120 |
| 2nde | 2nde-maths-a | maths | A | — | 4 | 28 | 20 | 1 | 70 |
| 2nde | 2nde-maths-c | maths | C | — | 6 | 40 | 30 | 1 | 119 |
| 2nde | 2nde-physique-c | physics | C | — | 5 | 34 | 25 | 1 | 97 |
| 2nde | 2nde-svt | svt | A,C,D | — | 5 | 33 | 25 | 1 | 115 |
| 1re | 1re-chimie-cd | chemistry | C,D | PROBATOIRE | 4 | 27 | 20 | 1 | 85 |
| 1re | 1re-economie | economie | général | — | 4 | 28 | 20 | 1 | 79 |
| 1re | 1re-english | english | A,C,D | PROBATOIRE | 5 | 33 | 25 | 1 | 93 |
| 1re | 1re-francais-litterature | francais | A,C,D | PROBATOIRE | 6 | 39 | 30 | 1 | 131 |
| 1re | 1re-histoire-geo-ecm | histoire-geo | A,C,D | PROBATOIRE | 6 | 39 | 30 | 1 | 119 |
| 1re | 1re-maths-a | maths | A | PROBATOIRE | 4 | 27 | 20 | 1 | 66 |
| 1re | 1re-maths-cd | maths | C,D | PROBATOIRE | 6 | 39 | 30 | 1 | 104 |
| 1re | 1re-maths-e-ti | maths | E,TI | — | 4 | 28 | 20 | 1 | 72 |
| 1re | 1re-physique-cd | physics | C,D | PROBATOIRE | 5 | 33 | 25 | 1 | 98 |
| 1re | 1re-svt-d | svt | D | PROBATOIRE | 5 | 34 | 25 | 1 | 115 |
| Tle | bac-chimie-cd-organique | chemistry | C,D | BAC | 5 | 35 | 25 | 1 | 109 |
| Tle | tle-economie | economie | général | — | 4 | 27 | 20 | 1 | 72 |
| Tle | tle-english | english | A,C,D | BAC | 5 | 33 | 25 | 1 | 105 |
| Tle | tle-francais-a | francais | A | BAC | 5 | 32 | 25 | 1 | 119 |
| Tle | tle-histoire-geo-ecm | histoire-geo | A,C,D | BAC | 6 | 39 | 30 | 1 | 122 |
| Tle | bac-maths-a | maths | A | BAC | 5 | 33 | 25 | 1 | 76 |
| Tle | bac-maths-c-specifique | maths | C | BAC | 4 | 28 | 20 | 1 | 77 |
| Tle | bac-maths-cd-complements | maths | C,D | BAC | 6 | 39 | 30 | 1 | 112 |
| Tle | bac-maths-e-ti | maths | E,TI | — | 5 | 33 | 25 | 1 | 78 |
| Tle | tle-philosophie | philosophie | A,C,D | BAC | 7 | 44 | 35 | 1 | 126 |
| Tle | bac-physique-cd-complements | physics | C,D | BAC | 5 | 35 | 25 | 1 | 101 |
| Tle | tle-physique-e-ti | physics | E,TI | — | 4 | 28 | 20 | 1 | 78 |
| Tle | tle-svt-d | svt | D | BAC | 6 | 41 | 30 | 1 | 126 |
| **Total** | 31 packs | | | | **156** | **1042** | **780** | **31** | **3050** |

Packs préexistants du même périmètre (non modifiés) : `probatoire-francais` (4 fiches), `bac-maths` (5), `bac-physique-chimie` (5). Les nouveaux packs `bac-maths-cd-complements`, `bac-maths-c-specifique`, `bac-physique-cd-complements`, `bac-chimie-cd-organique`, `1re-francais-litterature` les complètent sans les dupliquer.

## 2. Lots (< 3 Mo chacun)
Un pack JSON pèse 70-135 ko non compressé (≈ 25-40 ko en zip) : une classe entière (tous packs confondus) reste très en dessous de 3 Mo. Découpage naturel pour les agents « lots » : un lot par classe (2nde, 1re, Tle) puis par série/matière (voir colonnes Série et Matière). Total des 31 nouveaux packs : ≈ 3 Mo de JSON non compressé (≈ 1 Mo en zip), soit < 1,3 Mo par classe : un lot par classe tient largement sous 3 Mo.

## 3. Matières du programme absentes du catalogue ou non écrites
- **Informatique** : aucune clé de matière dans `LearnCatalog` (je n'ai pas le droit de modifier le Kotlin) → **non écrite**. À faire quand la clé `informatique` existera.
- **Histoire, géographie et ECM** sont regroupées sous la clé existante `histoire-geo` (une fiche par thème, ECM incluse).
- **Séries E et TI** : le catalogue n'admet que les séries A, C, D pour PROBATOIRE/BAC ; les packs E/TI sont donc publiés **sans examen** (champ `series` seulement), ils n'ont pas de « Préparer mon examen » dédié.
- **Économie** : présence au programme et série exacte inconnues : packs sans examen, signalés en `reviewNotes`.
- Séries **A** : physique, chimie, SVT non écrites (hors programme habituel de la série A) ; **philosophie en 1re** : non écrite (programme de Tle).
- Hors périmètre ou non couvert par manque de certitude : programmes officiels précis par série, œuvres littéraires imposées, annales (jamais copiées).

## 4. Vérifications
- Validateur Kotlin (`gradle :core:checkLearnContent`, même `LessonValidator` que l'app) : **OK sur les 47 packs** ; contrôles du test `LearnContentTest` rejoués avec un petit programme jetable (bonne réponse notée juste et mauvaises fausses par `Marking`, nombres saisis acceptés, aucune étiquette de figure superposée, ids uniques, prérequis) : 0 problème après correction de 5 cas.
- `gradle :core:test` n'a pas pu s'exécuter complètement dans le cloud (plugin Android et `kotlin-test` non téléchargeables : proxy 403/429) ; harnais « core seul » + miroir Python du validateur (`lv.py`) utilisés.

## 5. Détail par pack (thèmes attendus, couvert / manquant, lacunes)

### Chimie — Seconde C (pack `2nde-chimie-c`)

Niveau 2nde, matière chemistry, série C, sans examen. Statut draft partout. **Volume molaire retenu : 24 L/mol à 20 °C sous 1 atm** (indiqué dans les fiches et énoncés ; 22,4 L/mol à 0 °C mentionné ; à signaler en review).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture | Fiche |
|---|---|---|
| Corps purs et mélanges, séparations | couvert | melanges |
| Solutions, concentrations massique et molaire, dilution | couvert | melanges |
| Constitution de l'atome (Z, A, N), structure électronique Z ≤ 18 | couvert | atome |
| Classification périodique, familles | couvert (partiel : tableau non dessiné) | atome |
| Ions, règles du duet et de l'octet | couvert | atome |
| Liaison covalente, ionique, formules de Lewis simples | couvert (Lewis en texte seulement) | atome |
| Mole, constante d'Avogadro, masse molaire | couvert | matiere |
| Volume molaire d'un gaz | couvert | matiere |
| Équation chimique équilibrée, conservation | couvert | reaction |
| Avancement, réactif limitant, bilans de matière | couvert | reaction |
| Tests d'identification d'ions, pH, acides et bases | manquant | - |
| Chimie organique (alcanes, alcools) | manquant | - |
| Géométrie des molécules (VSEPR), électronégativité | manquant | - |

#### Compteurs
Fiches : 4 ; exercices hors auto-évaluation : 24 fiche + 3 épreuve blanche = 27 ; auto-évaluations : 20 ; épreuves blanches : 1 (/20, 1 h 30) ; illustrations : 4. Taille : environ 78 ko.

#### Lacunes et doutes
- Programme exact MINESEC de Seconde C inconnu (avancement, dilution, liaisons ioniques parfois en Première).
- Volume molaire (24 L/mol à 20 °C) ; masses molaires arrondies (Cl = 35,5).
- Valeur « bouteille de 12,5 kg de butane » fictive.
- Aucune notion de pH, tests d'ions ni chimie organique.
- Épreuve blanche : format, durée et barème indicatifs.

### Anglais — Seconde (2nde-english)
Niveau 2nde, série(s) : aucune (pas d'examen). Statut : brouillon IA, tout en `draft`.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Present simple / continuous / perfect | Couvert (fiche « present ») |
| Past simple, past continuous, verbes irréguliers | Couvert (fiche « past ») ; liste irrégulière courte |
| Futur (will, going to, present continuous), modaux can/must/should/may | Couvert (fiche « future ») |
| Comparatifs, superlatifs | Couvert (fiche « compare ») |
| Quantifieurs, articles | Couvert, règles simplifiées |
| Compréhension de texte | Couvert (fiche « reading-writing ») ; textes courts |
| Lettre informelle, paragraphe | Couvert |
| Vocabulaire école, santé, environnement | Partiel (listes courtes) |
| Past perfect, voix passive, discours indirect, conditionnels, relatives | Manquant (probablement hors Seconde ou à vérifier) |
| Phonétique, compréhension orale | Manquant |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 25 (dont 3 de l'épreuve blanche) ; auto-évaluations : 25 QCM ; épreuves blanches : 1 (/20, 120 min) ; illustrations : 5.

#### Lacunes et doutes
- Programme MINESEC non disponible : ordre et contenus à vérifier (les unités du manuel officiel peuvent différer).
- Pas de compréhension orale ni de phonétique.
- Les consignes sont en français, les exemples en anglais britannique standard.
- Estimation de couverture : environ 70 %.

### Français — Seconde (pack `2nde-francais`)

Niveau : 2nde, matière francais, sans examen. Séries : aucune. Statut : brouillon (draft). Tous les textes à étudier sont originaux (écrits pour l'exercice).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Lecture méthodique : axes, champs lexicaux, registres | Couvert (lecture) |
| Figures de style | Couvert (figures) |
| Versification : alexandrin, rimes, strophes | Couvert (figures), sans diérèse ni synérèse |
| Phrase complexe, subordonnées | Couvert (grammaire) |
| Valeurs des temps et des modes | Couvert (grammaire) ; temps littéraires rares : partiel |
| Texte narratif : schéma, narrateur, focalisation | Couvert (textes) |
| Texte argumentatif : thèse, arguments, connecteurs, procédés | Couvert (textes) |
| Rédiger un récit, une lettre, un paragraphe argumenté | Couvert (expression) |
| Contraction de texte | Couvert (expression), méthode simple |
| Étude d'œuvres intégrales du programme, discours rapporté, théâtre | Manquant (aucune œuvre citée, par prudence) |
| Dissertation / discussion, résumé long | Manquant |
| Orthographe et conjugaison systématiques | Partiel |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 25 (+ 3 de l'épreuve blanche) ; auto-évaluations : 25 ; épreuves blanches : 1 (/20, 120 min) ; illustrations : 3.
- Volume : environ 114 ko ; validation lv.py : 0 erreur.

#### Lacunes et doutes
- Décompte syllabique du vers et du quatrain originaux à re-vérifier.
- Rapport de contraction, tolérance de 10 % et règle de comptage des mots : conventions à confirmer.
- Terminologie (focalisation, registres, subordonnées) à harmoniser avec les manuels camerounais.
- Aucune œuvre au programme n'est étudiée : un enseignant devra ajouter des extraits.

### Histoire-Géographie-ECM — Seconde (2nde-histoire-geo-ecm)
Niveau 2nde, série(s) : aucune (pas d'examen). Statut : brouillon IA, tout en `draft`.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Méthodes : sources, frise, lecture de carte, croquis, graphique | Couvert (fiche « méthodes ») ; croquis : méthode seulement, pas d'exercice de réalisation |
| Grandes civilisations africaines (Égypte, Aksoum, Ghana/Mali/Songhaï, Kanem-Bornou, Bamoun) | Partiel (fiche « civilisations ») : repères très solides uniquement |
| Traites négrières | Couvert en grandes lignes (fiche « traite-colonisation ») |
| Partage et colonisation de l'Afrique | Couvert en grandes lignes (Berlin, résistances) |
| Cameroun colonial et indépendance (Kamerun, mandats, tutelles, 1960, 1961, 1972) | Partiel : repères daté et flagués ; peu de détails sur résistances camerounaises et personnalités |
| Relief, climat, végétation du Cameroun | Couvert (fiche « geo-cameroun »), sans chiffres |
| Afrique centrale (bassin du Congo, CEMAC) | Partiel |
| Hydrographie (Wouri, Sanaga, Bénoué, Logone, lac Tchad) | Couvert |
| ECM : droits et devoirs, symboles, institutions | Couvert en grands principes ; Constitution non détaillée |
| Population : croissance, densité, urbanisation (Yaoundé, Douala) | Couvert, données fictives |
| Agriculture et ressources | Partiel (cultures vivrières/de rente) ; mines, pétrole, forêt, pêche non traités |
| Mondialisation / autres espaces mondiaux | Manquant |

#### Compteurs
- Fiches : 6 ; exercices hors auto-évaluation : 30 (dont 4 problèmes d'épreuve blanche) ; auto-évaluations : 30 QCM ; épreuves blanches : 1 (/20, 120 min) ; illustrations/figures dans les fiches : 8 (frises, barres FICTIVES, schémas, drapeau).

#### Lacunes et doutes
- Dates à confirmer : 1884, 1916, 1922, 1946, 1948 (non utilisée), 1960, 1961, 1972, 1235, 1324, 1493, 1591.
- Chiffres : aucun chiffre réel ; altitude du mont Cameroun non donnée précisément.
- Programme officiel non disponible : choix des civilisations, ordre des fiches, ECM (signification des couleurs, devise, hymne) à vérifier.
- Pas de croquis à compléter, pas de carte détaillée (limite des figures).
- Estimation de couverture : environ 65 %.

### Mathématiques — Seconde A (pack `2nde-maths-a`)

Niveau 2nde, matière maths, série A, sans examen. Brouillon IA (status draft), à relire par un enseignant. Contenu volontairement plus accessible que la série C.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche |
|---|---|---|
| Fractions, priorités, puissances de 10, écriture scientifique | couvert | calcul |
| Proportionnalité, pourcentages, coefficient multiplicateur, évolutions successives (FCFA) | couvert | calcul |
| Racines carrées, identités remarquables | manquant | - |
| Équations et inéquations du 1er degré | couvert | equations |
| Systèmes de deux équations à deux inconnues, mise en équation | couvert | equations |
| Équation du 2nd degré | manquant | - |
| Fonctions : image, antécédent, fonctions affines et linéaires, variations | couvert | fonctions |
| Représentation graphique et lecture de graphiques, comparaison de tarifs | couvert | fonctions |
| Statistiques : effectifs, fréquences, moyenne, mode, médiane, étendue | couvert | stats |
| Quartiles, diagramme circulaire, diagrammes en barres | couvert (quartiles basiques) | stats |
| Écart-type, classes | manquant | - |
| Géométrie plane (Thalès, Pythagore, vecteurs) | manquant | - |
| Ensembles et logique | manquant | - |

#### Compteurs

- Fiches : 4
- Exercices hors auto-évaluation : 28 (24 exercices de fiche + 4 exercices de l'épreuve blanche)
- Auto-évaluations : 20 (4 x 5 QCM)
- Épreuves blanches : 1 (`2nde-maths-a-blanc-1`, 1 h 30, 20 points)
- Illustrations / exemples avec figure : 9
- Taille : environ 72 ko

#### Lacunes et doutes

- Programme officiel MINESEC non disponible, notamment le contenu réel de la seconde A.
- La géométrie (Pythagore, Thalès, repérage), les racines et les identités remarquables, souvent vues en seconde A, ne sont pas traitées.
- Quartiles et durée de l'épreuve blanche (1 h 30) à confirmer.
- Tous les prix et données sont fictifs ; aucun taux officiel n'est utilisé.
- Les valeurs numériques ont été recalculées ; une relecture humaine reste nécessaire.

### Mathématiques — Seconde C (pack `2nde-maths-c`)

Niveau 2nde, matière maths, série C, sans examen. Brouillon IA (status draft), à relire par un enseignant.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche |
|---|---|---|
| Ensembles, appartenance, inclusion, réunion, intersection, complémentaire, cardinal | couvert | ensembles |
| Intervalles de R, ensembles de nombres | couvert | ensembles |
| Logique : connecteurs, implication, réciproque, contraposée, quantificateurs, négation | couvert | ensembles |
| Raisonnements (contre-exemple, contraposée, absurde, disjonction de cas) | partiel (absurde seulement cité, pas d'exemple complet) | ensembles |
| Puissances, racines carrées | couvert | calcul |
| Identités remarquables, factorisation | couvert | calcul |
| Polynômes (degré, racine, factorisation par racine évidente) | partiel (pas de division euclidienne) | calcul |
| Équations et inéquations du 1er degré, tableau de signes | couvert (tableau de signes cité, peu d'exemples) | calcul |
| Équation et inéquation du 2nd degré (discriminant, signe du trinôme) | couvert | calcul |
| Généralités sur les fonctions : domaine, image, antécédent | couvert | fonctions |
| Variations, extremums, parité, représentation graphique | couvert (tableau de variation décrit en texte) | fonctions |
| Fonctions affines | couvert | fonctions |
| Polynômes du second degré : forme canonique, sommet, variations | couvert | fonctions |
| Vecteurs, coordonnées, Chasles, parallélogramme | couvert | vecteurs |
| Milieu, distance en repère orthonormé | couvert | vecteurs |
| Colinéarité, alignement, parallélisme (déterminant) | couvert | vecteurs |
| Barycentre de deux points pondérés | couvert | vecteurs |
| Barycentre de 3 points, ligne de niveau, réduction de MA·a+MB·b | partiel (3 points en exercice seulement) | vecteurs |
| Équation de droite, produit scalaire, orthogonalité | manquant | - |
| Cercle trigonométrique, radian, cos, sin, tan, valeurs remarquables, angles associés | couvert | trigo |
| Angles orientés de base, mesure principale | partiel | trigo |
| Équations/inéquations trigonométriques | manquant | - |
| Translations, homothéties | couvert | trigo |
| Rotations, symétries, composées de transformations, configurations (Thalès) | manquant | - |
| Statistiques : effectifs, fréquences, moyenne, médiane, quartiles, écart-type, classes | couvert | stats |
| Histogramme, polygone des effectifs cumulés | partiel (cités, non construits) | stats |

#### Compteurs

- Fiches : 6
- Exercices hors auto-évaluation : 40 (36 exercices de fiche + 4 exercices de l'épreuve blanche)
- Auto-évaluations : 30 (6 x 5 QCM)
- Épreuves blanches : 1 (`2nde-maths-c-blanc-1`, 2 h, 20 points)
- Illustrations / exemples avec figure : 13
- Taille : environ 122 ko

#### Lacunes et doutes

- Programme officiel MINESEC non disponible : les contenus reposent sur l'usage courant ; chaque fiche porte des `reviewNotes`.
- Le discriminant et la forme canonique sont-ils au programme de 2nde C ou de 1re ? À confirmer.
- Convention des quartiles (rang N/4 arrondi par excès) à aligner sur celle de l'enseignant.
- Angles orientés, homothéties et barycentre : étendue exacte à vérifier.
- Pas de géométrie plane classique (Thalès, cercles, géométrie dans l'espace) ni d'équation de droite.
- Les figures sont simples (plots et formes), sans tableaux de variation dessinés.
- Les valeurs numériques des exercices ont été recalculées (Python et à la main) ; une relecture humaine reste nécessaire.

### Physique — Seconde C (pack `2nde-physique-c`)

Niveau 2nde, matière physics, série C, sans examen. Statut draft partout. Constante retenue : **g = 10 N/kg** (annoncé dans la fiche forces et l'épreuve blanche).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture | Fiche |
|---|---|---|
| Grandeurs et unités SI, préfixes, conversions | couvert | grandeurs |
| Mesures, incertitude simple, chiffres significatifs | couvert (version simplifiée) | grandeurs |
| Analyse dimensionnelle | couvert | grandeurs |
| Référentiel, trajectoire, relativité du mouvement | couvert | mouvement |
| Vitesse moyenne, MRU | couvert | mouvement |
| MRUV (équations horaires, distance d'arrêt) | couvert | mouvement |
| Vecteurs vitesse / accélération, mouvement circulaire | manquant | - |
| Force, poids, masse, réaction | couvert | forces |
| Principe d'inertie, équilibre à 2 et 3 forces | couvert | forces |
| Gravitation universelle, loi de Hooke, plan incliné | manquant | - |
| Courant, tension, lois des nœuds et des mailles, loi d'Ohm | couvert | electricite |
| Associations de résistors, puissance, énergie, effet Joule | couvert | electricite |
| Générateur réel, caractéristiques, diodes | manquant | - |
| Réflexion, réfraction (Snell-Descartes), réflexion totale | couvert | optique |
| Lentilles minces, image, conjugaison, vergence | couvert | optique |
| Instruments (œil, loupe, lunette), dispersion | partiel (loupe en exercice) | optique |

#### Compteurs
Fiches : 5 ; exercices hors auto-évaluation : 25 fiche + 4 épreuve blanche = 29 (30 comptés par le validateur avec l'exercice de chaque fiche) ; auto-évaluations : 25 ; épreuves blanches : 1 (/20, 2 h) ; illustrations : 6. Taille : environ 100 ko.

#### Lacunes et doutes
- Programme exact MINESEC de Seconde C inconnu : MRUV, relation de conjugaison, réflexion totale et incertitudes peuvent relever d'un autre niveau.
- g = 10 N/kg (certains enseignants imposent 9,8) ; g lunaire 1,6 N/kg, indices (1,33 ; 1,5) à vérifier.
- Convention d'incertitude de lecture et règles de chiffres significatifs simplifiées.
- Prix du kWh fictif (100 FCFA), distances et durées fictives.
- Épreuve blanche : format, durée et barème indicatifs.

### SVT — Seconde (pack `2nde-svt`)

Niveau : 2nde, matière svt, sans examen. Séries : A, C, D. Statut : brouillon (draft), à faire valider par un enseignant.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Structure de la cellule, organites, cellule animale/végétale | Couvert (fiche cellule) |
| Calcul de grossissement et taille réelle | Couvert (cellule) |
| Division cellulaire : mitose en grandes lignes | Couvert (cellule) ; méiose citée seulement dans la fiche reproduction |
| Digestion et absorption | Couvert (nutrition) ; enzymes en détail partiel |
| Besoins alimentaires, alimentation équilibrée, IMC | Couvert (nutrition) ; malnutrition : partiel |
| Échanges gazeux, ventilation | Couvert (respcirc) |
| Cœur, double circulation, sang | Couvert (respcirc) ; pression artérielle, valvules en détail : manquant |
| Reproduction humaine, fécondation, grossesse, cycle | Couvert (repro) ; hormones en détail : partiel |
| Santé reproductive : IST, VIH/sida, contraception | Couvert prudemment (repro), à relire |
| Structure du globe, roches, volcanisme (mont Cameroun) | Couvert (geologie) |
| Tectonique des plaques, séismes | Manquant |
| Immunité, génétique, écologie / environnement | Manquant (hors périmètre demandé) |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 25 (+ 3 exercices de l'épreuve blanche) ; auto-évaluations : 25 ; épreuves blanches : 1 (/20, 120 min) ; illustrations : 6.
- Volume : environ 118 ko ; validation lv.py : 0 erreur.

#### Lacunes et doutes
- Valeurs : 4/4/9 kcal/g, seuils d'IMC, gaz de l'air (21 %, 16 %, 4 %), cycle de 28 jours, grossesse de 9 mois : à confirmer.
- Fiche reproduction/IST/contraception : relecture attentive indispensable (ton, niveau de détail).
- Mont Cameroun : aucun chiffre ni date ; nature des laves non affirmée.
- Hors périmètre : tectonique, séismes, immunité, génétique, écologie.
- Le programme exact de 2nde (MINESEC) n'a pas été consulté.

### Chimie — Probatoire C et D (pack `1re-chimie-cd`)

Niveau : 1re ; séries : C, D ; examen : PROBATOIRE ; statut : brouillon (draft), à faire valider par des enseignants.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche |
|---|---|---|
| Quantité de matière, masse molaire, volume molaire | couvert | matiere |
| Concentrations molaire et massique, dissolution | couvert | matiere |
| Dilution | couvert | matiere |
| Dosage simple à l'équivalence (acide/base) | couvert | matiere, acides |
| Tableau d'avancement, réactif limitant, rendement | manquant (seuls des bilans de matière simples sont faits) | |
| Oxydoréduction : couples, demi-équations, équation bilan | couvert | redox |
| Potentiels standard, prévision de réaction (règle du gamma) | couvert (valeurs usuelles à re-vérifier) | redox |
| Pile Daniell, f.é.m., quantité d'électricité | couvert | redox |
| Électrolyse, corrosion, protection des métaux | partiel (corrosion en « pour aller plus loin ») | redox |
| Nombres d'oxydation, formule de Nernst | manquant | |
| Alcanes : formule, nomenclature, isomérie, combustion, substitution | couvert | organique |
| Alcènes : formule, nomenclature, addition, test au dibrome | couvert (aperçu) | organique |
| Alcools : nomenclature, classes | couvert | organique |
| Réactions des alcools (oxydation ménagée, estérification), polymères | manquant | |
| Acides et bases (Brønsted), couples, eau amphotère | couvert | acides |
| pH, produit ionique, acide fort et base forte | couvert | acides |
| Acide faible, taux d'avancement, pKa | partiel (introductif) | acides |
| Courbe de dosage pH = f(V), solutions tampons | manquant | |
| Cinétique chimique, équilibres | manquant | |

#### Compteurs
- Fiches : 4
- Exercices hors auto-évaluation : 27 (24 dans les fiches, dont 4 problèmes à parties, + 3 exercices de l'épreuve blanche)
- Auto-évaluations (QCM) : 20
- Épreuves blanches : 1 (20 points, 2 heures, 3 exercices)
- Illustrations : 4
- Taille : environ 87 ko

#### Lacunes et doutes
- Estimation de couverture du programme habituel de 1re C/D : environ 65 %.
- Alcènes et pKa peuvent relever de la Tle selon le programme ; à confirmer.
- Potentiels standard et pKa : valeurs usuelles, annoncées dans les énoncés, à re-vérifier.
- Masses molaires arrondies (Cl = 35,5 ; Cu = 64 ; Zn = 65 ; Ag = 108 g/mol), volume molaire 24 L/mol (annoncé).
- Pas de tableau d'avancement, d'électrolyse détaillée, de courbe de dosage, ni de cinétique.
- Durée et barème de l'épreuve blanche indicatifs.

### Économie — Première (pack `1re-economie`)

Série : aucune précisée. Niveau 1re, sans examen. Statut : brouillon (draft), validé par lv.py (0 erreur).
ATTENTION : le programme général d'économie de Première est PROBABLE, non confirmé (matière et contenu selon la série à vérifier).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Besoins, biens et services, rareté | couvert (fiche agents) |
| Agents économiques, opérations économiques | couvert (fiche agents) |
| Circuit économique simple, épargne et investissement | couvert (fiche agents) |
| Circuit élargi (État, reste du monde) | partiel (mention) |
| Facteurs de production, valeur ajoutée | couvert (fiche production) |
| Productivité du travail | couvert (fiche production) |
| Coûts fixes/variables, recette, profit, seuil de rentabilité | couvert (fiche production) |
| Coût marginal, rendements, formes d'entreprises | manquant / partiel |
| Marché, offre, demande, équilibre | couvert (fiche marche) |
| Déplacements de courbes, excédent/pénurie | couvert (fiche marche) |
| Élasticité, formes de marché | partiel (mention) |
| Monnaie : fonctions, formes, FCFA/BEAC | couvert (fiche monnaie) |
| Banques, épargne, intérêts simples et composés | couvert (fiche monnaie) |
| Inflation, indice des prix, pouvoir d'achat | couvert (fiche monnaie) |
| Tontines et microfinance (contexte camerounais) | couvert (fiche monnaie) |
| Commerce extérieur, croissance, chômage, fiscalité, comptabilité nationale | manquant |

#### Compteurs
- Fiches : 4 ; exercices hors auto-évaluation : 28 (24 de fiche + 4 exercices de l'épreuve blanche) ; auto-évaluations : 20 ; épreuves blanches : 1 (/20, 2 h indicatif) ; illustrations/figures : 4.
- Taille : environ 81 ko de JSON.

#### Lacunes et doutes
- Estimation de couverture : environ 60 % d'un cours d'introduction ; moins si le programme réel est plus large.
- Existence, intitulé et contenu du programme d'économie en 1re (séries B, G, ESF...) à confirmer.
- Toutes les données chiffrées (FCFA, prix, indices, cotisations) sont fictives.
- BEAC, CEMAC, tontines (njangi), microfinance : formulations générales à faire valider.
- Taux réel ≈ nominal − inflation (approximation) ; classification des besoins et nombre d'agents selon les manuels.

### Anglais — Probatoire (pack `1re-english`)
Séries : A, C, D. Consignes en français, exemples et exercices en anglais.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | État | Fiche |
|---|---|---|
| Present perfect vs simple past, for/since/ago | couvert | tenses-review |
| Past perfect | couvert | tenses-review |
| Present perfect continuous, past continuous, future forms | manquant / à peine effleuré | |
| Voix passive (plusieurs temps, impersonnel, deux objets) | couvert | passive-conditionals |
| Conditionnels 0 à 3, unless | couvert | passive-conditionals |
| Mixed conditionals, « in case », « provided that » | manquant | |
| Wish / if only | couvert | passive-conditionals |
| Discours rapporté (affirmations, questions, ordres, repères) | couvert | reported-relative |
| Propositions relatives (defining / non-defining, whose, omission) | couvert | reported-relative |
| Modaux (must, should, may, ought to…), gérondif / infinitif | manquant | |
| Compréhension : skimming, scanning, inférence, vocabulaire en contexte | couvert | reading-techniques |
| Expression écrite : lettre formelle, essai argumentatif, résumé | couvert | writing |
| Autres rédactions (récit, lettre informelle, dialogue), phonétique | manquant | |
| Épreuve blanche /20 (compréhension, langue, écriture) | couvert | eb1 (3 exercices « blanc ») |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 33 (30 + 3 exercices d'épreuve blanche) ; auto-évaluations : 25 ; épreuves blanches : 1 (20 points, 120 min) ; illustrations : 5 (lignes du temps, tableau des conditionnels, tableau du backshift, techniques de lecture, mise en page d'une lettre).
- Taille : ~96 ko.

#### Estimation de couverture : ~55 % de la grammaire et de l'expression attendues en 1re A/C/D.

#### Lacunes et doutes
- Programme officiel MINESEC non disponible (manuel de référence, structures exigibles, présence de True/False/Not Given).
- Formes continues, modaux, mixed conditionals, phonétique et vocabulaire thématique non traités.
- « If I were / If I was », convention britannique de la lettre (faithfully/sincerely) : à confirmer.
- Textes de lecture originaux et fictifs (noms, chiffres imaginaires) ; « njangi », FCFA utilisés comme repères locaux.
- Format et barème réels de l'épreuve d'anglais au Probatoire non vérifiés.
- Contenu entièrement en brouillon (`draft`), à valider par des enseignants.

### Français et littérature — Probatoire (pack `1re-francais-litterature`)
Séries : A, C, D. Pack complémentaire de `probatoire-francais` (résumé, discussion, commentaire composé, dissertation non refaits).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | État | Fiche |
|---|---|---|
| Lecture analytique, énonciation, temps du récit | couvert | lecture-analytique |
| Registres / tonalités littéraires, registre de langue | couvert | lecture-analytique |
| Figures de style (compléments : métonymie, synecdoque, chiasme, antiphrase, allitération…) | couvert (les figures de base sont dans le pack de méthode) | figures-versification |
| Versification : syllabes, e muet, alexandrin, césure, rimes, strophes, sonnet, enjambement | couvert | figures-versification |
| Genre romanesque : narrateur, focalisation, temps du récit, personnages, schéma narratif | couvert | genre-romanesque |
| Genre théâtral : didascalies, double énonciation, tragédie / comédie / drame | couvert | genre-theatral |
| Mouvements : classicisme, Lumières, romantisme, réalisme, surréalisme, négritude | couvert (repères) | mouvements-litterature-africaine |
| Littérature africaine et camerounaise d'expression française | partiel (repères, quelques auteurs/œuvres) | mouvements-litterature-africaine |
| Phrase complexe, subordonnées, modes | couvert | etude-langue |
| Accord du participe passé | couvert (cas courants) | etude-langue |
| Concordance des temps | couvert | etude-langue |
| Orthographe d'usage (homophones, pluriels, adverbes en -ment) | partiel | etude-langue |
| Épreuve blanche /20 (lecture, langue, expression écrite) | couvert | eb1 (3 exercices « blanc ») |
| Poésie africaine / textes au programme précis, œuvres intégrales imposées | manquant | |
| Symbolisme/Parnasse, Moyen Âge/Renaissance, XXe siècle (Nouveau Roman…) | manquant | |
| Genres : essai, lettre, autobiographie, fable (étude détaillée) | manquant | |
| Expression écrite : rédaction narrative/descriptive, lettre | partiel (une rédaction dans l'épreuve blanche) | etude-langue (blanc) |

#### Compteurs
- Fiches : 6 ; exercices hors auto-évaluation : 39 (36 + 3 exercices d'épreuve blanche) ; auto-évaluations : 30 ; épreuves blanches : 1 (20 points, 150 min) ; illustrations : 6 (énonciation, scansion de l'alexandrin, schéma narratif, double énonciation, frise des œuvres, concordance des temps).
- Taille : ~134 ko.

#### Estimation de couverture : ~60 % des notions de littérature et langue d'une 1re A/C/D, en complément des méthodes.

#### Lacunes et doutes
- Programme officiel MINESEC non disponible : œuvres imposées, textes d'étude et poids de chaque partie à confirmer.
- Auteurs/œuvres camerounais cités (Mongo Beti, Ferdinand Oyono, Francis Bebey) : titres et dates à re-vérifier ; aucun autre auteur ajouté faute de certitude (théâtre camerounais, poésie camerounaise absents).
- Poèmes et textes originaux : scansions vérifiées à la main, à relire par un enseignant (richesse des rimes dépend de la prononciation).
- Nombre d'étapes du schéma narratif, vocabulaire de narratologie, théorie du drame : usages variables.
- Orthographe : règles standard seulement ; rectifications de 1990 et cas particuliers du participe passé non traités.
- Format et barème réels de l'épreuve de français au Probatoire non vérifiés.
- Contenu entièrement en brouillon (`draft`), à valider par des enseignants.

### Histoire-Géographie-ECM — Probatoire (1re-histoire-geo-ecm)

Niveau : Première. Examen : Probatoire. Séries : A, C, D. Statut : brouillon (draft), à valider par des enseignants.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche / remarque |
|---|---|---|
| Révolutions industrielles (1re et 2e), énergies, innovations | couvert | `revolutions` |
| Capitalisme, bourgeoisie et prolétariat, concentration | couvert | `revolutions` |
| Libéralisme, socialisme, impérialisme (analyses détaillées) | partiel | seulement mentionnés |
| Causes, déroulement, bilan de la Première Guerre mondiale | couvert (repères) | `guerre-mondiale-1` |
| Afrique dans la Première Guerre mondiale | couvert (repères) | `guerre-mondiale-1` |
| Kamerun allemand, campagne 1914-1916, partage, mandats SDN | partiel (faits prudents, bloc review) | `guerre-mondiale-1` |
| Révolution russe, génocide arménien, traités autres que Versailles | manquant | |
| Crise de 1929, New Deal | couvert | `entre-deux-guerres` |
| Régimes totalitaires (fascisme, nazisme, stalinisme) | couvert (grandes lignes) | `entre-deux-guerres` |
| Seconde Guerre mondiale (étapes, Shoah, ONU) | couvert (grandes lignes) | `entre-deux-guerres` |
| Cameroun et Afrique pendant la Seconde Guerre mondiale | partiel | ralliement 1940, Brazzaville 1944 |
| Espaces productifs, Triade, pays émergents | couvert (schéma) | `geographie-mondiale` |
| Échanges mondiaux, mondialisation, FTN, inégalités | couvert | `geographie-mondiale` |
| Étude d'espaces précis (États-Unis, UE, Japon, Chine, Afrique) | manquant | |
| Géographie du Cameroun : agriculture, élevage, pêche | couvert | `geographie-cameroun` |
| Industrie, énergie (hydroélectricité), transports du Cameroun | couvert (sans chiffres) | `geographie-cameroun` |
| Ressources minières et pétrolières, tourisme, commerce | partiel | pétrole/raffinerie seulement |
| ECM : Constitution, séparation des pouvoirs, institutions | couvert (principes) | `ecm-institutions` |
| Démocratie, citoyenneté (droits et devoirs), droits de l'homme | couvert | `ecm-institutions` |
| Intégration nationale, bilinguisme, vivre-ensemble | couvert | `ecm-institutions` |
| Corruption, éducation à la paix, environnement civique | manquant / partiel | |

#### Compteurs

- Fiches : 6
- Exercices hors auto-évaluation : 39 (36 d'entraînement + 3 exercices « blanc »)
- Auto-évaluations (QCM) : 30
- Épreuves blanches : 1 (20 points : histoire 8, géographie 6, ECM 6)
- Illustrations (frises, schémas, graphique à barres fictif) : 7 ; frises `timeline` : 3

#### Lacunes et doutes

- Programme exact non disponible : l'ordre et le détail des chapitres sont à vérifier.
- Le partage du Kamerun (1916, mandats de 1922, statut du Cameroun britannique) est limité aux faits sûrs et signalé en revue.
- Ralliement du Cameroun à la France libre (août 1940), Brazzaville 1944 : à confirmer.
- Aucune statistique réelle ; données de géographie fictives (annoncées comme telles).
- Noms d'entreprises et d'ouvrages (Alucam, Sonara, Eneo, Lom Pangar, Song Loulou, Lagdo) à vérifier et à actualiser.
- ECM : principes uniquement ; institutions, durée des mandats et dates à vérifier sur la Constitution.
- Croquis : schémas simplifiés, pas de cartes ; les fonds de carte de l'enseignant sont nécessaires pour les croquis d'examen.
- Estimation de couverture du programme : environ 55 %.

### Mathématiques — Probatoire A (pack `1re-maths-a`)

Série : A (niveau littéraire). Niveau : Première. Statut : brouillon (`draft`), validé par `lv.py` (0 erreur).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche |
|---|---|---|
| Fonctions numériques : ensemble de définition, image, antécédent | couvert | fonctions |
| Second degré : équation, signe du trinôme | couvert | fonctions |
| Dérivation simple (polynômes) et variations, tableau | couvert | fonctions |
| Lecture graphique de variations | partiel (une illustration, pas d'exercice de lecture dédié) | fonctions |
| Étude de fonctions rationnelles, limites | manquant (probablement hors programme A) | |
| Suites arithmétiques et géométriques, sommes | couvert | suites |
| Pourcentages, intérêts simples et composés, FCFA | couvert | suites |
| Statistiques à une variable : moyenne, variance, écart-type, médiane | couvert | statistiques |
| Données groupées en classes | couvert | statistiques |
| Quartiles, statistiques à deux variables | partiel (quartiles mentionnés) / manquant | |
| Dénombrement élémentaire, arrangements, combinaisons | couvert | probabilites |
| Probabilités : équiprobabilité, contraire, réunion, urnes | couvert | probabilites |
| Probabilité conditionnelle, indépendance | manquant (mentionné) | |

#### Compteurs
- Fiches : 4
- Exercices hors auto-évaluation : 27 (24 de fiches + 3 de l'épreuve blanche)
- Auto-évaluations : 20 (4 × 5 QCM)
- Épreuves blanches : 1 (/20, 2 h : 7 + 6 + 7 points)
- Illustrations : 4 (courbe, diagrammes en barres)
- Taille : environ 68 ko

#### Lacunes et doutes
- Niveau exact de la série A (dérivation, suites géométriques, combinaisons) à confirmer sur le programme officiel.
- Pas d'exercice de lecture graphique pure ni de tableau de variations à compléter.
- Durée de l'épreuve blanche (2 h) et barème indicatifs.
- Données et taux entièrement fictifs.

### Mathématiques — Probatoire C et D (pack `1re-maths-cd`)

Série(s) : C, D. Niveau : Première. Statut : brouillon (`draft`), validé par `lv.py` (0 erreur).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche |
|---|---|---|
| Second degré : équations, inéquations, signe, forme canonique | couvert | second-degre |
| Équations et inéquations paramétriques simples (signe des racines, S et P) | couvert | second-degre |
| Polynômes : racine, factorisation, division | partiel (racine évidente + identification) | second-degre |
| Équations bicarrées, systèmes | partiel (mention seulement) | second-degre |
| Limites usuelles, formes indéterminées, limites à l'infini | couvert | limites |
| Asymptotes (verticale, horizontale, oblique), position relative | couvert | limites |
| Continuité en un point, prolongement par continuité, TVI | couvert | limites |
| Limite de composées, théorème des gendarmes | manquant | |
| Nombre dérivé, tangente | couvert | derivation |
| Dérivées usuelles, somme, produit, quotient, composée simple | couvert | derivation |
| Variations, extremums, optimisation | couvert | derivation |
| Dérivée seconde, convexité, points d'inflexion | manquant | |
| Suites arithmétiques et géométriques, sommes | couvert | suites |
| Sens de variation, suite auxiliaire, intérêts composés | couvert | suites |
| Récurrence, limites de suites | manquant (probablement Terminale) | |
| Trigonométrie : valeurs remarquables, addition, duplication, équations | couvert | trigo-geometrie |
| Inéquations trigonométriques, linéarisation, a cos x + b sin x | manquant | |
| Produit scalaire, normes, orthogonalité | couvert | trigo-geometrie |
| Géométrie analytique : droite, distance point-droite, cercle | couvert | trigo-geometrie |
| Al-Kashi, lignes de niveau, transformations du plan | partiel (Al-Kashi mentionnée) / manquant | |
| Dénombrement : arrangements, combinaisons, permutations | couvert | denombrement |
| Probabilités : équiprobabilité, conditionnelle, arbres, indépendance | couvert | denombrement |
| Variable aléatoire, espérance, loi binomiale | manquant | |
| Statistiques à une variable : moyenne, variance, écart-type, médiane | couvert | denombrement |
| Statistiques à deux variables, ajustement | manquant | |

#### Compteurs
- Fiches : 6
- Exercices hors auto-évaluation : 39 (36 de fiches + 3 de l'épreuve blanche)
- Auto-évaluations : 30 (6 × 5 QCM)
- Épreuves blanches : 1 (/20, 3 h : exercice 1 = 5 pts, exercice 2 = 4 pts, problème = 11 pts)
- Illustrations / exemples avec figure : 6 (courbes plot et diagrammes en barres)
- Taille : environ 107 ko

#### Lacunes et doutes
- Programme officiel exact non disponible : répartition entre Première et Terminale (limites de suites, récurrence, convexité) à confirmer.
- Chapitre trigonométrie / géométrie : deux thèmes regroupés en une seule fiche (dense).
- Convention de la variance (division par N) à confirmer.
- Aucune vidéo ; données statistiques et financières toutes fictives.
- Les figures plot utilisent des courbes séparées (from/to) pour les asymptotes verticales : à vérifier visuellement dans l'app.

### Mathématiques — Première E et TI (1re-maths-e-ti)

Niveau : Première. Pas d'examen. Séries : E, TI. Statut : brouillon (draft), à valider par des enseignants.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche / remarque |
|---|---|---|
| Fonctions numériques, ensemble de définition, variations | couvert (polynômes) | `fonctions` |
| Trinôme du second degré (racines, signe, sommet) | couvert | `fonctions` |
| Dérivation (polynômes, produit, quotient), tangente, extrema | couvert | `fonctions` |
| Limites, asymptotes, fonctions rationnelles | manquant | |
| Suites arithmétiques et géométriques, sommes | couvert | `suites-interets` |
| Intérêts simples et composés, dépréciation | couvert | `suites-interets` |
| Trigonométrie (radian, sin, cos, équations simples) | couvert | `trigonometrie` |
| Grandeurs sinusoïdales (courant alternatif) | couvert | `trigonometrie` |
| Formules d'addition, tan, nombres complexes d'introduction | manquant | choix signalé en review |
| Géométrie vectorielle, produit scalaire, barycentre | manquant | |
| Statistiques : moyenne, médiane, variance, écart-type | couvert | `statistiques-probabilites` |
| Statistiques en classes, quartiles, ajustement affine | manquant | |
| Probabilités : équiprobabilité, complémentaire, réunion, sans remise | couvert | `statistiques-probabilites` |
| Probabilité conditionnelle, loi binomiale, dénombrement | manquant | |

#### Compteurs

- Fiches : 4
- Exercices hors auto-évaluation : 28 (24 d'entraînement + 4 exercices « blanc »)
- Auto-évaluations (QCM) : 20
- Épreuves blanches : 1 (20 points : 6 + 5 + 5 + 4)
- Illustrations : 5 (courbes, histogramme, triangle, graphique en bâtons)

#### Lacunes et doutes

- Programme exact des séries E et TI non disponible : trigonométrie choisie plutôt que complexes ou vecteurs ; à confirmer.
- Pas de limites ni d'étude de fonctions rationnelles ; dérivation limitée aux polynômes et formules usuelles.
- Convention g = 10 m/s² en exemple simplifié ; applications électricité et mécanique à valider.
- Variance par division par N (population) ; certaines classes utilisent une autre convention.
- Toutes les données (FCFA, mesures, lots) sont fictives.
- Estimation de couverture du programme : environ 50 %.

### Physique — Probatoire C et D (pack `1re-physique-cd`)

Niveau : 1re ; séries : C, D ; examen : PROBATOIRE ; statut : brouillon (draft), à faire valider par des enseignants.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture | Fiche |
|---|---|---|
| Cinématique : repère, vitesse, accélération, MRU | couvert | cinematique |
| Mouvement rectiligne uniformément varié, chute libre | couvert | cinematique |
| Mouvement circulaire uniforme (ω, T, accélération centripète) | couvert | cinematique |
| Repère de Frenet, accélération tangentielle/normale, mouvement curviligne général | manquant | |
| Lois de Newton, référentiel galiléen, poids | couvert | dynamique |
| Forces de contact, frottements de glissement (μ) | couvert | dynamique |
| Plan incliné avec et sans frottement | couvert | dynamique |
| Mouvement dans un champ de pesanteur (projectile, parabole) | manquant | |
| Mouvement dans un champ électrique ou magnétique uniforme (particule chargée) | manquant | |
| Travail d'une force constante, travail du poids, travail d'un frottement | couvert | energie |
| Puissance, énergie électrique (kWh) | couvert | energie |
| Énergie cinétique, théorème de l'énergie cinétique | couvert | energie |
| Énergie potentielle de pesanteur, conservation et variation de l'énergie mécanique | couvert | energie |
| Énergie potentielle élastique, oscillateur mécanique (ressort) | manquant | |
| Dipôles passifs, lois d'Ohm, associations de résistors, lois des nœuds et des mailles | couvert | electricite |
| Générateur, récepteur, loi de Pouillet, puissance, rendement | couvert | electricite |
| Condensateur, charge/décharge RC (constante de temps, énergie) | couvert (sans résolution de l'équation différentielle) | electricite |
| Champ magnétique (aimants, fil, solénoïde), lignes de champ | partiel (niveau introductif) | magnetisme |
| Force de Laplace | couvert | magnetisme |
| Induction électromagnétique, force de Lorentz | manquant | |
| Optique : lentilles minces, instruments | manquant (non traité : fiche magnétisme choisie) | |
| Gravitation universelle, mouvement des satellites | manquant | |

#### Compteurs
- Fiches : 5
- Exercices hors auto-évaluation : 33 (30 dans les fiches, dont 5 problèmes à parties, + 3 exercices de l'épreuve blanche)
- Auto-évaluations (QCM) : 25
- Épreuves blanches : 1 (20 points, 3 heures, 3 exercices)
- Illustrations : 6 (figures shapes et plot)
- Taille : environ 100 ko

#### Lacunes et doutes
- Estimation de couverture du programme habituel de 1re C/D : environ 60 %.
- Magnétisme : placé en 1re C/D par défaut ; à confirmer (peut relever de la Tle). Voir reviewNotes de la fiche. Alternative non rédigée : optique des lentilles.
- Pas de fiche sur les champs de forces (gravitation, projectile), le ressort, ni l'induction.
- Dérivation des vecteurs (vitesse, accélération) et RC avec exponentielles : à vérifier si ces notions sont au programme de la classe.
- Convention du frottement (μ ou angle φ), règle des trois doigts, notation f.é.m. : à aligner avec l'enseignant.
- g = 10 N/kg annoncé dans les énoncés ; le tarif d'électricité est fictif.
- Durée et barème de l'épreuve blanche indicatifs.

### SVT — Probatoire D (pack `1re-svt-d`)

Série : D. Niveau 1re, examen PROBATOIRE. Statut : brouillon (draft), validé par lv.py (0 erreur).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Cellule : structure, organites, cellule végétale | couvert (fiche adn) |
| ADN, gène, chromosome | couvert (fiche adn) |
| Réplication, transcription, traduction, code génétique | couvert (fiche adn) |
| Mutations | partiel (fiche adn : substitution, drépanocytose) |
| Cycle cellulaire, mitose | couvert (fiche mitose) |
| Méiose, brassage inter/intrachromosomique, fécondation | couvert (fiche mitose) |
| Anomalies chromosomiques (trisomie 21) | partiel (mention) |
| Génétique mendélienne : monohybridisme, dihybridisme, croisement test | couvert (fiche mendel) |
| Hérédité humaine : arbres généalogiques, maladies autosomiques | partiel (fiche mendel) |
| Hérédité liée au sexe, gènes liés, codominance | manquant (mentions) |
| Respiration cellulaire, fermentations, photosynthèse | couvert (fiche energie) |
| Phases claire/sombre de la photosynthèse, détail Krebs | partiel (mention) |
| Nutrition de l'animal (digestion, absorption) | manquant |
| Structure du globe, lithosphère, tectonique des plaques | couvert (fiche geologie) |
| Séismes (ondes, magnitude), volcans | couvert (fiche geologie) |
| Roches (sédimentaires, magmatiques, métamorphiques), minéralogie | manquant |
| Immunologie, système nerveux, reproduction humaine, écologie | manquant |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 34 (30 de fiche + 4 exercices de l'épreuve blanche) ; auto-évaluations : 25 ; épreuves blanches : 1 (/20, 2 h 30 indicatif) ; illustrations/figures : 8.
- Taille : environ 118 ko de JSON.

#### Lacunes et doutes
- Estimation de couverture : environ 50 % du programme probable de 1re D.
- Programme exact (MINESEC) inconnu ; thèmes manquants listés ci-dessus.
- Rendement ATP de la respiration (36 indicatif), niveau de détail enzymes/ARNt/orientation 5'-3' : à vérifier.
- Géologie sans chiffres réels ; valeurs d'âge/vitesse et vitesses P/S fictives ou simplifiées ; ligne volcanique du Cameroun, mont Cameroun et lac Nyos (1986) formulés prudemment.
- Format de l'épreuve blanche (durée, parties A/B, barème) indicatif.
- Brassage intrachromosomique et gènes liés : à placer selon la classe (1re ou Tle).

### Chimie organique — Bac C et D — pack `bac-chimie-cd-organique`

Série(s) : C, D (Terminale, BAC). Complément du pack `bac-physique-chimie` (acides-bases, cinétique). Brouillon IA, status draft.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture |
|---|---|
| Alcools : nomenclature, classes, isomérie | couvert (alcools) |
| Oxydation ménagée des alcools | couvert (alcools) |
| Déshydratation (alcène, éther-oxyde) | couvert (alcools) |
| Aldéhydes et cétones, tests (DNPH, Tollens, Fehling) | couvert (carbonyles) |
| Réduction des carbonylés | partiel (évoquée) |
| Acides carboxyliques, propriétés, dérivés (chlorure d'acyle, anhydride) | couvert (carbonyles) |
| Esters, estérification, hydrolyse, K, rendement | couvert (esters) |
| Saponification, savons | couvert (esters) |
| Amines, basicité, amides | couvert (amines) |
| Acides α-aminés, zwitterion, pKa | couvert (amines) ; représentation de Fischer, pH isoélectrique : partiel |
| Liaison peptidique, peptides | couvert (amines) |
| Polymères : addition, condensation | couvert (polymeres) |
| Piles électrochimiques (Daniell), Faraday | couvert (polymeres) |
| Électrolyse | manquant (choix : piles) |
| Équilibres chimiques généraux | partiel (K et rendement dans la fiche esters) |
| Acides-bases, cinétique | hors pack (voir bac-physique-chimie) |
| Hydrocarbures (alcanes, alcènes, benzène) | manquant (supposé acquis de la Première) |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 35 (30 + 5 de l'épreuve blanche) ; auto-évaluations : 25 ; épreuves blanches : 1 (/20) ; illustrations : 5.
- Taille : ~112 ko.

#### Lacunes et doutes
- Électrolyse et chimie des hydrocarbures non traitées ; piles regroupées avec les polymères dans une fiche (choix éditorial à valider).
- Valeurs usuelles : K = 4, rendements 67 %/60 %/5 %, pKa (éthanoïque 4,8 ; méthylamine 10,6 ; alanine 2,3/9,7), E° (Zn −0,76 V ; Cu +0,34 V), F = 96 500 C/mol, V_m = 24 L/mol.
- Toutes les équations-bilans ont été vérifiées atome par atome et charge par charge en Python ; masses molaires recalculées.
- Le symbole ⇌ est écrit en Unicode dans des formules (\text{ ⇌ }) : vérifier le rendu sur appareil.

### Économie — Terminale (pack `tle-economie`)

Niveau Tle, matière économie, sans examen ni série affectés (series []). Statut : brouillon, à relire par un enseignant.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | État | Fiche |
|---|---|---|
| Comptabilité nationale : valeur ajoutée, PIB (3 optiques), croissance, PIB réel/nominal, PIB par habitant | couvert | comptabilite-nationale |
| Revenu, RNB, épargne, limites du PIB | couvert | comptabilite-nationale |
| Monnaie : fonctions, formes, banques, crédit, intérêts | couvert | monnaie-credit-inflation |
| Politique monétaire, inflation (calcul, causes, effets) | couvert (général) | monnaie-credit-inflation |
| Commerce international : avantage comparatif, libre-échange/protectionnisme | couvert | commerce-international |
| Balance des paiements, taux de change, FCFA/euro | partiel (review) | commerce-international |
| Mondialisation économique | partiel | commerce-international |
| Développement, IDH, sous-développement | couvert (général) | developpement-finances-emploi |
| Finances publiques (budget, déficit, dette) | couvert | developpement-finances-emploi |
| Marché de l'emploi (activité, chômage, informel) | couvert | developpement-finances-emploi |
| Entreprise, production, marchés et prix, concurrence, fluctuations/cycles, intégration régionale détaillée | manquant | - |

#### Compteurs
- Fiches : 4 ; exercices hors auto-évaluation : 27 (dont 3 exercices d'épreuve blanche) ; auto-évaluations : 20 ; épreuves blanches : 1 (/20, 150 min) ; illustrations/figures : 4.
- Taille : environ 74 ko.

#### Lacunes et doutes
- Présence de l'économie au programme de Terminale et séries concernées : non vérifiées (aucune série n'est affirmée ; l'instruction de l'épreuve le précise).
- Parité 655,957 FCFA/euro : blocs et exercice marqués `review` ; mécanismes de garantie non décrits.
- Formules et intitulés (PIB, balance des paiements, IDH, taux réel approximé) simplifiés. Toutes les données sont fictives.
- Estimation de couverture : environ 50 %.

### Anglais — Bac (tle-english)

Séries : A, C, D. Niveau Tle, examen BAC. Statut : brouillon (draft), à valider par un enseignant.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | État | Fiche |
|---|---|---|
| Temps et aspects (present, past, perfect, futur), exceptions | couvert | tenses-aspect |
| Conditionnels (0 à 3, mixtes), wishes, if only | couvert | conditionals-wishes |
| Subjonctif (mandative), inversion | couvert | conditionals-wishes |
| Modaux de déduction et modaux au passé | couvert | modals-passive-causative |
| Voix passive (y compris impersonnelle) | couvert | modals-passive-causative |
| Causatif (have/get/make/let) | couvert | modals-passive-causative |
| Compréhension de l'écrit (méthode, 4 thèmes locaux) | couvert (textes originaux) | reading-comprehension |
| Essay argumentatif | couvert | writing-skills |
| Lettre formelle / report | couvert | writing-skills |
| Summary | couvert | writing-skills |
| Reported speech, relative clauses, gerund/infinitive, phrasal verbs, articles | manquant | — |
| Vocabulaire thématique étendu, phonétique (stress, accent) | manquant ou partiel | — |
| Épreuve blanche /20 (lecture 8, langue 6, écriture 6) | couvert | eb1 |

#### Compteurs

- Fiches : 5
- Exercices hors auto-évaluation : 33 (dont 3 exercices d'épreuve blanche)
- Auto-évaluations : 25 (5 par fiche)
- Épreuves blanches : 1 (20 points, 180 min)
- Illustrations : 5

#### Lacunes et doutes

- Reported speech, relatives, gérondif/infinitif, phrasal verbs non traités : probables dans le programme.
- Format de l'épreuve du Bac d'anglais (séries A, C, D) : à vérifier ; la série A peut comporter une partie littéraire non couverte.
- Textes de compréhension fictifs, personnages inventés ; lieux réels sans fait chiffré.
- Anglais britannique privilégié (faithfully/sincerely, present perfect) ; distinctions was/were et needn't have : à confirmer.

Couverture estimée : environ 65 %.

### Français et littérature — Bac A (tle-francais-a)

Série : A. Niveau Tle, examen BAC. Statut : brouillon (draft), à valider par un enseignant.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | État | Fiche |
|---|---|---|
| Dissertation littéraire : méthode (analyse du sujet, problématique, plan, introduction, conclusion) | couvert | dissertation-bac |
| Sujets sur le roman, le théâtre, la poésie | couvert (par types de sujets, sans œuvres imposées) | dissertation-bac |
| Littérature engagée | couvert | dissertation-bac |
| Commentaire composé : repérage, axes, projet de lecture, rédaction | couvert (textes originaux) | commentaire-bac |
| Littérature francophone et africaine : colonisation, négritude, identité, désillusion, condition de la femme | couvert (auteurs/œuvres sûrs) | francophone-africaine |
| Œuvres exactes au programme de la série A | manquant (liste officielle inconnue) | — |
| Histoire littéraire XVIe-XXe : courants et genres | couvert (repères) | courants-genres |
| Théâtre : tragédie, comédie, drame ; poésie : formes | partiel | courants-genres |
| Langue : syntaxe de la phrase complexe, modes, concordance, accord du participe passé | couvert | langue-expression |
| Expression écrite, argumentation, connecteurs, niveaux de langue | couvert | langue-expression |
| Résumé / contraction de texte | partiel (hors Tle A, couvert en Probatoire) | — |
| Études d'œuvres intégrales (analyse détaillée d'une œuvre au programme) | manquant | — |
| Épreuve blanche /20 (commentaire 8 + dissertation 12) | couvert | eb1 |

#### Compteurs

- Fiches : 5
- Exercices hors auto-évaluation : 32 (dont 2 exercices d'épreuve blanche)
- Auto-évaluations : 25 (5 par fiche)
- Épreuves blanches : 1 (20 points, 240 min)
- Illustrations : 5

#### Lacunes et doutes

- Liste d'œuvres imposées pour la Tle A inconnue : la fiche francophone cite des œuvres/auteurs sûrs (Oyono, Beti, Sembène, Camara Laye, Kane, Kourouma, Bâ, Césaire, Senghor, Damas) ; dates à vérifier (1934, 1947, 1956, 1968, 1979).
- Œuvres étrangères du programme (théâtre, roman) non traitées individuellement.
- Format, durée et barème de l'épreuve de français du Bac A : à vérifier.
- Préface de Tartuffe, affaire Calas, périodisation des courants : formulations prudentes.
- Textes de commentaire entièrement originaux (pastiches), donc sans auteur réel.

Couverture estimée : environ 65 %.

### Histoire-Géographie-ECM — Bac (pack `tle-histoire-geo-ecm`)

Niveau Tle, examen BAC, séries A, C, D. Statut : brouillon, à relire par un enseignant.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | État | Fiche |
|---|---|---|
| Guerre froide : bipolarité, crises, détente, fin | couvert | monde-1945-1991 |
| Décolonisation, Bandung, tiers monde, non-alignement | couvert (repères) | monde-1945-1991 |
| Méthode : commentaire de document | couvert | monde-1945-1991, épreuve blanche |
| Afrique : chemins de l'indépendance, OUA/UA | partiel (repères) | afrique-cameroun |
| Cameroun : colonisation, tutelle, indépendance 1960, réunification 1961, État unitaire 1972 | couvert (repères, review) | afrique-cameroun |
| Construction de l'État camerounais (institutions, multipartisme, Constitution 1996) | partiel | afrique-cameroun |
| Méthode : composition (dissertation) d'histoire | couvert | afrique-cameroun |
| Monde depuis 1991, mondialisation, acteurs | couvert | monde-depuis-1991 |
| ONU, UA, CEMAC (rôle général) | couvert / partiel (CEMAC) | monde-depuis-1991 |
| Espace mondial : centres/périphéries, Triade, puissances, flux | couvert | espace-mondial |
| Méthode : composition de géographie, croquis | couvert | espace-mondial |
| Afrique centrale et Cameroun : milieux, ressources, développement | couvert (sans chiffres) | afrique-centrale-cameroun |
| Urbanisation, enjeux environnementaux | couvert | afrique-centrale-cameroun |
| ECM : démocratie, élections, droits humains, Constitution, éthique, corruption | couvert (principes) | ecm-citoyennete |
| Conflits au Moyen-Orient, intégration européenne détaillée, guerres du Vietnam/Corée en détail, grandes figures nationalistes | manquant | - |
| Études de cas géographiques détaillées (cartes du Cameroun, chiffres) | manquant | - |

#### Compteurs
- Fiches : 6 ; exercices hors auto-évaluation : 30 (dont 3 exercices d'épreuve blanche) ; auto-évaluations : 30 ; épreuves blanches : 1 (/20, 180 min indicatives) ; illustrations/figures : 8.
- Taille : environ 125 ko.

#### Lacunes et doutes
- Dates (1884, 1916, 1922, 1948, 1960, 1961, 1972, 1990, 1996) et noms propres à re-vérifier (blocs `review`, reviewNotes).
- Périodisation de la frise 1945-1991 (crises/détente/fin) : proposition.
- Aucune statistique ; données chiffrées fictives annoncées. Textes de documents : originaux, signalés.
- Programme exact, séries, format de l'épreuve du Bac (histoire, géographie, ECM) : à vérifier. Pas de figure cartographique du Cameroun.
- Estimation de couverture : environ 55 %.

### Mathématiques — Bac A (pack `bac-maths-a`)
Série : A (Tle, BAC). Brouillon IA, tout en `draft`.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Fonctions numériques, domaine, limites (polynôme, rationnelle simple), asymptotes horizontale et verticale | couvert (fiche fonctions) |
| Dérivation, tangente, sens de variation, étude de fonction polynôme/rationnelle | couvert (fiche fonctions) |
| Asymptote oblique, continuité, théorème des valeurs intermédiaires | manquant |
| Logarithme népérien et exponentielle : propriétés, équations, dérivées, étude simple | couvert (fiche ln-exp) |
| Croissances comparées, primitives, calcul intégral | manquant (hors demande) |
| Suites arithmétiques et géométriques, sommes | couvert (fiche suites-finance) |
| Mathématiques financières : intérêts simples/composés, annuités, emprunt | couvert (fiche suites-finance), conventions à valider |
| Statistiques à une variable (moyenne, variance, écart-type, médiane) | couvert (fiche stats) ; quartiles, diagrammes : manquant |
| Statistiques à deux variables : nuage, covariance, droite des moindres carrés, r | couvert (fiche stats) ; méthode de Mayer : manquant |
| Dénombrement (arrangements, combinaisons, factorielle) | couvert (fiche probas) |
| Probabilités (équiprobabilité, contraire, réunion), variable aléatoire, espérance | couvert (fiche probas) ; probabilité conditionnelle, indépendance, loi binomiale, variance : manquant |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 25 (dont 3 de l'épreuve blanche) ; auto-évaluations : 25 QCM (5 par fiche) ; épreuves blanches : 1 (/20, 3 h) ; illustrations/figures : 7 (5 illustrations + 2 figures d'exemple).
- Taille : environ 78 ko.

#### Lacunes et doutes
- Programme officiel de Tle A non disponible : périmètre déduit de l'usage courant ; probabilités conditionnelles et loi binomiale, méthode de Mayer, intégrales à confirmer.
- Annuités : convention versement en fin de période ; formules à valider par un enseignant.
- Médiane/quartiles : convention non détaillée.
- Toutes les données (FCFA, ventes, notes) sont fictives. Réponses recalculées par Python (sympy, statistics, math).

### Mathématiques — Bac C (spécifique)
Série : C (Tle, BAC).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Statut | Fiche |
|---|---|---|
| Divisibilité, division euclidienne, PGCD, PPCM | couvert | arith |
| Bézout, Gauss, équation ax + by = c | couvert | arith |
| Congruences, restes de puissances | couvert | arith |
| Nombres premiers, nombre de diviseurs | partiel | arith |
| Petit théorème de Fermat, numération en base b | manquant (mentionné) | |
| Similitudes planes directes (z' = az + b, éléments caractéristiques) | couvert | simil |
| Translations, rotations, homothéties | couvert (cas particuliers) | simil |
| Similitudes indirectes, composition détaillée, isométries | partiel / manquant | simil |
| Coniques : équations réduites, foyers, excentricité, asymptotes, directrice | couvert | coniques |
| Coniques : tangentes, équation polaire, centre déplacé détaillé | manquant | |
| Racines n-ièmes de l'unité, z^n = Z | couvert | racines |
| Barycentres, lieux géométriques | manquant (choix éditorial) | |

#### Compteurs
- Fiches : 4 ; exercices hors auto-évaluation : 28 (24 de fiche + 4 de l'épreuve blanche) ; auto-évaluations : 20 ; épreuves blanches : 1 (/20, 4 h) ; illustrations : 4. Taille ≈ 79 ko.

#### Lacunes et doutes
- 4e fiche : racines n-ièmes de l'unité choisies à la place de barycentres/isométries/lieux (calculables et vérifiables par code, cohérent avec les complexes) ; à arbitrer avec le programme officiel.
- Étendue exacte du programme de Tle C (Fermat, base b, similitudes indirectes, coniques générales) non confirmée.
- Épreuve blanche en 4 exercices ; format d'examen réel à vérifier.

### Mathématiques — Bac C et D (compléments)
Série(s) : C et D (Tle, BAC). Pack complémentaire de `bac-maths` (non modifié).

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Statut | Fiche |
|---|---|---|
| Dénombrement (arrangements, combinaisons) | couvert | proba |
| Probabilités conditionnelles, totales, indépendance | couvert | proba |
| Variable aléatoire, espérance, variance | couvert | proba |
| Loi binomiale (P(X=k), np, npq, effectif minimal) | couvert | binomiale |
| Équations différentielles y' = ay + b | couvert | diff |
| y'' + ay' + by = 0 (3 cas de Δ, conditions initiales) | couvert (à confirmer au programme) | diff |
| Géométrie dans l'espace : produit scalaire, plans, droites | couvert | espace |
| Distance point-plan, projection orthogonale | partiel (formule, sans démonstration) | espace |
| Produit vectoriel, sphères, sections de sphère | manquant | |
| Statistiques à deux variables, moindres carrés, corrélation | couvert | stats |
| Méthode de Mayer, ajustements non affines | manquant | |
| Limites : comparaison, gendarmes, croissances comparées, branches infinies | couvert | limites |
| Loi de probabilité continue / loi normale | manquant (hors périmètre demandé) | |
| Étude de fonction, ln/exp, intégrales, suites, complexes | dans `bac-maths` | |

#### Compteurs
- Fiches : 6 ; exercices hors auto-évaluation : 39 (36 de fiche + 3 exercices de l'épreuve blanche) ; auto-évaluations : 30 ; épreuves blanches : 1 (/20, 4 h) ; illustrations : 6. Taille ≈ 115 ko.

#### Lacunes et doutes
- Le second ordre (y'' + ay' + by = 0) et la loi binomiale peuvent ne pas figurer dans tous les programmes de Tle C/D : à confirmer.
- Distance point-plan et notion de branche parabolique : exigibilité à vérifier.
- Les statistiques utilisent les moindres carrés (diviseur n) ; le programme officiel peut préférer Mayer.
- Chevauchement partiel avec la fiche limites de `bac-maths`.
- Données fictives partout ; barèmes de l'épreuve blanche indicatifs.

### Mathématiques — Terminale E et TI (pack `bac-maths-e-ti`)
Séries : E, TI (Tle, sans examen déclaré). Brouillon IA, tout en `draft`.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Limites, asymptotes (dont oblique), dérivées (dont sin/cos, u^n, racine), étude de fonction | couvert (fiche fonctions) |
| Primitives, conditions initiales, application à la mécanique (x, v, a) | couvert (fiche fonctions) |
| Exponentielle et logarithme, équations, croissances comparées, applications électriques | couvert (fiche exp-ln) ; log décimal : manquant |
| Intégrale, Chasles, aires, valeur moyenne, travail/distance/charge | couvert (fiche integrales) ; intégration par parties, volumes : manquant ; valeur efficace : seulement mentionnée |
| Nombres complexes : algébrique, module, argument, trigonométrique, division | couvert (fiche complexes) ; forme exponentielle, racines n-ièmes, équations : manquant |
| Complexes appliqués : impédances R, L, C, circuit série, loi d'Ohm, Fresnel | couvert (fiche complexes) — à faire valider (conventions) |
| Équations différentielles y'=ay, y'=ay+b ; circuits RC et RL | couvert (fiche diff-proba) ; y''+ω²y=0 : manquant |
| Probabilités : loi binomiale, espérance | partiel (fiche diff-proba) |
| Statistiques : moyenne, écart-type | partiel (rappel seulement) ; séries à deux variables, loi normale : manquant |
| Suites, dénombrement | manquant (non demandé) |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 25 (dont 3 de l'épreuve blanche) ; auto-évaluations : 25 QCM ; épreuves blanches : 1 (/20, 3 h) ; illustrations/figures : 6 (5 illustrations + 1 figure d'exemple, dont 1 figure de Fresnel).
- Taille : environ 81 ko.

#### Lacunes et doutes
- Séries E et TI : validateur sans examen, séries non vérifiées par lv.py (série "TI" à confirmer côté catalogue Kotlin).
- Fiche 5 regroupe deux thèmes (équations différentielles + probabilités/statistiques) : probabilités/statistiques traitées au minimum.
- Complexes appliqués : notation j/i, signe du déphasage, valeurs efficaces/maximales à faire valider par un enseignant de physique appliquée.
- Croissances comparées, asymptote oblique : dépendent du programme exact. Valeurs de composants fictives. Réponses recalculées par Python.

### Philosophie — Bac A, C, D (tle-philosophie)

Niveau Tle, matière philosophie, examen BAC, séries A, C, D. Brouillon IA (status draft), à faire valider.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture |
|---|---|
| Qu'est-ce que la philosophie (étonnement, doute, méthode) | couvert (intro-philosophie) |
| Méthode de la dissertation | couvert (dissertation-philosophique) |
| Méthode du commentaire de texte | couvert (commentaire-philosophique) |
| Conscience, inconscient | couvert (conscience-inconscient-autrui) |
| Autrui | couvert (même fiche) |
| Désir, passions | partiel (évoqué via liberté-caprice et bonheur) |
| Liberté, devoir, morale, bonheur | couvert (liberte-devoir-bonheur) |
| Vérité, raison, science, technique | couvert (verite-raison-science-technique) |
| Société, État, justice, droit | couvert (etat-justice-droit-travail) |
| Travail | couvert (même fiche) |
| Art, beau | manquant |
| Langage | partiel (effleuré dans le commentaire d'exemple) |
| Religion, mythe | manquant |
| Histoire, culture, nature | manquant |
| Philosophie africaine (ubuntu, palabre) | partiel |

#### Compteurs
- Fiches : 7 (la fiche « méthodes » est scindée en dissertation et commentaire)
- Exercices hors auto-évaluation : 44 (42 de fiches + 2 exercices de l'épreuve blanche)
- Auto-évaluations : 35 QCM
- Épreuves blanches : 1 (dissertation 12 pts + commentaire 8 pts = 20, 240 min)
- Illustrations : 7
- Taille : environ 130 ko

#### Lacunes et doutes
- Programme officiel exact non disponible : art, langage, religion, histoire et désir absents ou partiels.
- Format réel de l'épreuve (choix du sujet, durée, barème, séries) à vérifier.
- Références (Platon, Aristote, Descartes, Kant, Rousseau, Hobbes, Locke, Montesquieu, Marx, Freud, Sartre, Pascal, Popper) en paraphrase, sans citation exacte : à valider.
- Textes à commenter : pastiches originaux, signalés comme tels.
- Peu d'exemples camerounais (marché, cacao, mobile money, palabre, droit coutumier) : à enrichir.

### Physique — Bac C et D (compléments) — pack `bac-physique-cd-complements`

Série(s) : C, D (Terminale, BAC). Pack complémentaire du pack existant `bac-physique-chimie` (mécanique newtonienne, oscillateur mécanique, radioactivité, acides-bases, cinétique), sans doublon. Brouillon IA, status draft.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)

| Thème | Couverture |
|---|---|
| Champ magnétique, solénoïde | couvert (fiche champ-magnetique) |
| Force de Laplace | couvert (fiche champ-magnetique) ; rails de Laplace seulement en exercice d'induction |
| Force de Lorentz, mouvement circulaire dans B, spectromètre de masse | couvert (champ-magnetique) ; hélice / cyclotron : évoqués |
| Induction, loi de Faraday-Lenz, flux | couvert (induction) |
| Auto-induction, dipôle RL, énergie de la bobine | couvert (induction) |
| Oscillations libres LC, énergie | couvert (rlc) |
| Oscillations amorties RLC (régimes) | partiel (qualitatif) |
| Régime sinusoïdal forcé, impédance, phase | couvert (rlc) ; construction de Fresnel : manquant |
| Résonance, facteur de qualité, bande passante, puissance | couvert (rlc) |
| Ondes mécaniques progressives, célérité, retard | couvert (ondes) |
| Diffraction, interférences (Young), lumière | couvert (ondes) |
| Effet photoélectrique, photon (introduction) | couvert (ondes) ; tension d'arrêt : manquant |
| Ondes stationnaires, effet Doppler | manquant |
| Gravitation, satellites, géostationnaire, Kepler | couvert (gravitation) ; énergie mécanique : brièvement |
| Champ électrique (condensateur plan, déviation) | manquant (choix : gravitation) |
| Mécanique, oscillateur mécanique, radioactivité | hors pack (voir bac-physique-chimie) |
| Dipôle RC | manquant (supposé acquis) |

#### Compteurs
- Fiches : 5 ; exercices hors auto-évaluation : 35 (30 dans les fiches + 5 de l'épreuve blanche) ; auto-évaluations : 25 ; épreuves blanches : 1 (/20, 5 exercices de 4 points) ; illustrations : 5 (courbes de résonance, schéma de trajectoire, courbes RL, onde, orbite).
- Taille : ~104 ko.

#### Lacunes et doutes
- Champ électrique non traité (choix éditorial).
- Constantes arrondies annoncées (e, m, h, c, G, M_T, R_T, µ₀) à confirmer.
- Convention de déphasage RLC, convention de trièdre pour les forces : à harmoniser.
- Formules RLC vérifiées par balayage numérique en Python (maximum à f₀, bande passante f₀/Q, période LC).

### Physique appliquée — Terminale E et TI (pack `tle-physique-e-ti`)

Séries : E, TI. Niveau Tle, sans examen (matière physics). Statut : brouillon, 80 ko. Programme exact incertain.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture | Fiche |
|---|---|---|
| Grandeurs sinusoïdales, valeur efficace, période, pulsation | couvert | electricite |
| Impédance R, L, C, circuit RLC série, déphasage | couvert | electricite |
| Puissances active/réactive/apparente, facteur de puissance | couvert | electricite |
| Résonance | partiel (formule en « pour aller plus loin ») | electricite |
| Nombres complexes / construction de Fresnel, triphasé | manquant | - |
| Transformateur monophasé : rapport, pertes, rendement | couvert (cas simples) | machines |
| Moteur à courant continu, bilan de puissance | couvert (modèle simple) | machines |
| Moteur asynchrone : vitesse de synchronisme, glissement | partiel | machines |
| Machine synchrone, essais d'un transformateur, redressement, électronique de puissance | manquant | - |
| Cinématique MRU/MRUA, dynamique (Newton) | couvert | mecanique |
| Moment d'une force, équilibre | couvert | mecanique |
| Travail, énergie cinétique/potentielle, puissance, rendement | couvert | mecanique |
| Rotation du solide (moment d'inertie), frottements, mouvement circulaire | manquant | - |
| Chaleur sensible, calorimétrie, changements d'état | couvert | thermo |
| Premier principe, gaz parfaits, machines thermiques, transferts thermiques | manquant / partiel | thermo |
| Hydraulique, optique, électronique analogique | manquant (hors liste demandée) | - |

#### Compteurs
Fiches : 4. Exercices hors auto-évaluation : 28 (dont 4 de l'épreuve blanche). Auto-évaluations : 20. Épreuves blanches : 1 (/20, 150 min). Illustrations / figures : 7.

#### Lacunes et doutes
- Programme exact des séries E et TI inconnu : niveau, notations et outils (complexes, triphasé) à confirmer.
- Constantes usuelles (g = 9,8 N/kg, c de l'eau 4 180 J/(kg.K), L de fusion et de vaporisation, 230 V / 50 Hz) à confirmer avec le manuel.
- Formules et exemples recalculés par Python ; données fictives.

### SVT — Bac D (pack `tle-svt-d`)

Série : D. Niveau Tle, examen BAC. Statut : brouillon (draft), 130 ko.

#### Thèmes du programme habituellement attendus (à vérifier sur le texte officiel)
| Thème | Couverture | Fiche |
|---|---|---|
| Soi/non-soi, antigène, barrières, inflammation, phagocytose | couvert | immuno |
| Immunité spécifique : humorale, cellulaire, mémoire, sélection clonale | couvert | immuno |
| Vaccins et sérums | couvert | immuno |
| VIH/sida : transmission, prévention, dépistage, traitement | couvert (faits solides, relecture santé) | immuno |
| Allergies, greffes, CMH, auto-immunité | manquant | - |
| Neurone, potentiel de repos et d'action, codage | couvert | neuro |
| Synapse, neuromédiateurs, sommation | couvert (partiel sur sommation) | neuro |
| Arc réflexe, centres nerveux (moelle, encéphale) | couvert | neuro |
| Voies motrices et sensitives centrales, aires cérébrales, physiologie musculaire | manquant / partiel | neuro |
| Vocabulaire génétique, chromosomes, méiose, arbres généalogiques | couvert | genetique |
| Drépanocytose, groupes ABO et Rhésus, probabilités | couvert | genetique |
| Maladie liée à X | partiel (principe + 1 exercice) | genetique |
| Mutations, ADN/synthèse des protéines, brassages (dihybridisme) | manquant | - |
| Cycles ovarien/utérin, régulation hormonale, homme | couvert | repro |
| Fécondation, nidation, grossesse, contraception | couvert | repro |
| Ovogenèse, spermatogenèse détaillées, PMA, IST hors VIH | partiel / manquant | - |
| Glycémie, insuline, glucagon, diabète | couvert | glycemie |
| Autres régulations du milieu intérieur (température, eau) | partiel | glycemie |
| Datation relative et absolue | couvert | geologie |
| Tectonique des plaques, roches, séismes/volcans | partiel | geologie |
| Évolution biologique, hominisation, fossiles | partiel (mentionné seulement) | geologie |
| Ressources du Cameroun | partiel (sans chiffres) | geologie |
| Écologie, nutrition/digestion, photosynthèse, respiration | manquant (hors liste demandée) | - |

#### Compteurs
Fiches : 6. Exercices hors auto-évaluation : 41 (dont 5 exercices de l'épreuve blanche). Auto-évaluations : 30. Épreuves blanches : 1 (/20, 180 min). Illustrations / figures : 8.

#### Lacunes et doutes
- Programme officiel non disponible : contenu limité aux 6 thèmes demandés, plusieurs thèmes habituels (ADN, mutations, dihybridisme, écologie) manquent.
- Valeurs neurophysiologiques (-70 mV, vitesses) et seuils de glycémie (1 g/L, 1,26 g/L) indicatifs.
- Santé (VIH, vaccins, diabète, contraception) : à relire par un enseignant/professionnel de santé ; pas de calendrier vaccinal ni de protocole.
- Ressources du Cameroun : localisations à vérifier ; aucune statistique.
- Les expériences (grenouille, souris, guenon) sont fictives et annoncées comme telles.
