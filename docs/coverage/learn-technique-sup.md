# Couverture « Apprendre » — enseignement technique, premières années d'université, compétences de vie

> Généré le 2026-10-01 (branche `claude/content-learn-technique-sup`). **Statut : tout est brouillon** (`draft`, « Brouillon — à relire par un enseignant »), rédigé avec une IA, **non relu** par des enseignants, juristes, comptables ou agents de santé. Aucun contenu n'est `reviewed` ni `validated`. Le propriétaire valide la qualité 3 mois après la distribution aux bêta-testeurs ; l'app affiche « bêta ». Exercices et cours **originaux** (jamais copiés d'un manuel ou d'un sujet).

## Total livré
**36 packs, 161 fiches, 996 exercices corrigés + 805 questions d'auto-évaluation, 181 illustrations** ; sources JSON ≈ 2 Mo au total (≤ 110 ko par pack ; un pack zippé reste très en dessous de 1 Mo, donc de la limite de 3 Mo par lot). Colonne « Points à vérifier » = notes de relecture + blocs/exercices marqués `review` (les auto-évaluations des niveaux L1/L2 sont `review`, donc **exclues de l'export vers le quiz** : le quiz exige une « filière » pour le supérieur que le format ne porte pas encore).

## Contraintes du catalogue (le code n'a pas été modifié)
Le catalogue (`LearnCatalog`) n'a ni niveaux CAP/BEP ni séries techniques (F1-F4, G1-G3, STT…), ni matières *comptabilité*, *informatique*, *électricité*, *agricole*, *ESF*, *vie pratique*. Les packs sont donc rattachés au **niveau et à la matière les plus proches** (noté dans les `reviewNotes` de chaque fiche), sans examen (`exam` vide : les séries du Probatoire/Bac du catalogue sont A, C, D seulement) et sans épreuve blanche. À étendre côté code : niveaux/filières techniques, matières (`compta`, `informatique`, `electricite`…), niveau « tous publics » pour la vie pratique, filières du supérieur dans le quiz.

## A. Enseignement technique et professionnel (MINESEC)
| Pack | Niveau | Matière (catalogue) | Fiches | Exercices (+ auto-éval.) | Illustr. | Points à vérifier |
|---|---|---|---|---|---|---|
| `etp-agricole-1re` | 1re | SVT | 5 | 30 (+25) | 5 | 31 |
| `etp-cap-bep-communication` | 3e | Français | 4 | 24 (+20) | 3 | 13 |
| `etp-cap-bep-maths-pro` | 4e | Mathématiques | 4 | 24 (+20) | 4 | 14 |
| `etp-cap-bep-securite-atelier` | 4e | Sciences | 3 | 18 (+15) | 3 | 32 |
| `etp-compta-generale-1re` | 1re | Économie | 5 | 30 (+25) | 5 | 22 |
| `etp-economie-generale-1re` | 1re | Économie | 4 | 24 (+20) | 4 | 9 |
| `etp-electricite-1re` | 1re | Physique | 5 | 30 (+25) | 6 | 18 |
| `etp-esf-1re` | 1re | SVT | 4 | 24 (+20) | 4 | 40 |
| `etp-genie-civil-materiaux-1re` | 1re | Physique | 3 | 18 (+15) | 5 | 15 |
| `etp-gestion-entreprise-1re` | 1re | Économie | 4 | 25 (+20) | 4 | 19 |
| `etp-informatique-bureautique` | 2nde | Sciences | 5 | 30 (+25) | 5 | 16 |
| `etp-maths-commerciales-1re` | 1re | Mathématiques | 5 | 30 (+25) | 5 | 19 |
| `etp-maths-techniques-1re` | 1re | Mathématiques | 5 | 30 (+25) | 6 | 5 |
| `etp-maths-techniques-tle` | Tle | Mathématiques | 5 | 30 (+25) | 5 | 7 |
| `etp-mecanique-dessin-1re` | 1re | Physique | 5 | 30 (+25) | 7 | 21 |

**Ce qui existe (fiches)**

- **Sciences et techniques agricoles — séries agricoles (1re)** (`etp-agricole-1re`) : Calculs agricoles : superficie, rendement, densité, marge brute ; Le sol : composition, texture, fertilité, pH ; La plante : germination, photosynthèse, nutrition minérale ; Techniques culturales et protection des cultures ; Élevage : besoins, hygiène et prophylaxie
- **Communication professionnelle — CAP/BEP** (`etp-cap-bep-communication`) : La lettre professionnelle et le CV ; Le rapport de stage et le compte rendu ; Note de service, message court, orthographe et ponctuation ; Vocabulaire de l'atelier et expression orale en entretien
- **Mathématiques professionnelles — CAP/BEP** (`etp-cap-bep-maths-pro`) : Aires et volumes des solides usuels ; Devis, factures, tableaux et graphiques ; Nombres décimaux et conversions d'unités ; Proportionnalité, règle de trois et pourcentages
- **Sécurité, hygiène et premiers gestes à l'atelier — CAP/BEP** (`etp-cap-bep-securite-atelier`) : Équipements de protection et risques de l'atelier ; Signalisation, rangement, ergonomie et organisation du poste ; Premiers secours : principes généraux
- **Comptabilité générale — séries G (1re)** (`etp-compta-generale-1re`) : Journal, grand livre et balance ; Opérations courantes et TVA ; Amortissement linéaire et résultat ; Le patrimoine et le bilan ; Les comptes et la partie double
- **Économie générale — séries G/STT (1re)** (`etp-economie-generale-1re`) : Besoins, biens, agents économiques et circuit ; Production, valeur ajoutée, revenus, consommation et épargne ; Monnaie, marché, prix et inflation ; Le commerce extérieur de base
- **Électricité et électrotechnique de base — séries F (1re)** (`etp-electricite-1re`) : Le courant alternatif sinusoïdal monophasé ; Sécurité électrique : principes généraux ; Grandeurs électriques, loi d'Ohm et code des couleurs ; Associations de résistances et diviseur de tension ; Lois de Kirchhoff, puissance et énergie
- **Économie sociale et familiale (1re)** (`etp-esf-1re`) : Nutriments, groupes d'aliments et équilibre alimentaire ; Budget familial et consommation ; Puériculture générale : hygiène et alimentation du nourrisson ; Hygiène et conservation des aliments, entretien du linge et du logement
- **Technologie du bâtiment et des matériaux — série F4 (1re)** (`etp-genie-civil-materiaux-1re`) : Aires, volumes et masse volumique en bâtiment ; Le béton : composition, proportions et quantités ; Lecture de plans, devis de matériaux et sécurité de chantier
- **Organisation et gestion de l'entreprise, techniques administratives (1re)** (`etp-gestion-entreprise-1re`) : Les documents commerciaux ; La gestion simple des stocks ; Types d'entreprises, fonctions et organigramme ; Courrier, classement, archivage et bases du secrétariat
- **Informatique et bureautique — séries tertiaires** (`etp-informatique-bureautique`) : Les composants d'un ordinateur ; Fichiers, dossiers et traitement de texte ; Sécurité de base : mots de passe, hameçonnage, sauvegarde ; Internet et courrier électronique ; Le tableur : cellules, formules et références
- **Mathématiques commerciales et financières — séries G (1re)** (`etp-maths-commerciales-1re`) : Intérêts simples et escompte commercial ; Partages proportionnels et moyennes pondérées ; Pourcentages, hausses et baisses ; Prix d'achat, prix de revient, prix de vente et marge ; Suites arithmétiques et géométriques appliquées
- **Mathématiques pour les séries techniques (1re)** (`etp-maths-techniques-1re`) : Équations et trinômes du second degré ; Fonctions et variations simples ; Trigonométrie : triangle rectangle et cercle trigonométrique ; Vecteurs et géométrie analytique de base ; Statistiques descriptives : moyenne, médiane, écart-type
- **Mathématiques pour les séries techniques (Tle)** (`etp-maths-techniques-tle`) : Dérivées et applications : variations et optimisation ; Fonctions exponentielle et logarithme népérien ; Primitives et intégrale ; Suites arithmétiques et géométriques ; Probabilités élémentaires et dénombrement
- **Dessin technique et mécanique de base — séries F (1re)** (`etp-mecanique-dessin-1re`) : Normalisation du dessin : formats, échelles, traits, cotation ; Projection orthogonale, coupes et sections ; Forces, équilibre et moment d'une force ; Travail, puissance mécanique et rendement ; Vitesse et mouvement rectiligne uniforme

**Ce qui manque**
- **CAP/BEP** : seulement un socle transversal (maths pro, communication, sécurité d'atelier) ; aucune matière de spécialité (menuiserie, maçonnerie, couture, mécanique auto, électricité du bâtiment, soudure, coiffure…), ni technologie de spécialité, ni dessin de métier. Niveaux CAP/BEP non représentables (rattachés à 4e/3e).
- **Séries industrielles** : F1 (construction mécanique : usinage, métrologie, RDM, fabrication), F2 (électronique : composants, semi-conducteurs, numérique), F3 (électrotechnique : triphasé, machines, transformateurs, puissance en alternatif cos φ), F4 (génie civil : béton armé, topographie, RDM, mécanique des sols), F5-F8 (froid, chimie, biologie, santé) : **non couverts**, seuls l'électricité de base, le dessin/mécanique, les maths et une introduction au bâtiment existent. Pas de Tle technique en dehors des maths.
- **Séries tertiaires** : G1 (techniques administratives : sténographie, droit du travail, correspondance), G2 (comptabilité approfondie : comptes de tiers, régularisations, comptabilité analytique, fiscalité), G3 (techniques commerciales : marketing, vente, négociation, droit commercial), STT : seule la 1re est abordée, et partiellement ; **Tle non couverte** (comptabilité des sociétés, analyse financière, gestion budgétaire…).
- **Agricole / ESF** : une seule fiche-pack chacun en 1re ; pas de zootechnie détaillée, d'agronomie par culture, de machinisme, d'économie rurale ; ESF sans cuisine/couture/gestion du foyer approfondies.
- **Anglophone** (Technical and Commercial Education, GCE technique) : **aucun contenu** (sous-système anglophone non traité dans ce périmètre).
- Aucune épreuve blanche (pas de format d'examen technique documenté de façon sûre).

## B. Supérieur : L1 et L2
| Pack | Niveau | Matière (catalogue) | Fiches | Exercices (+ auto-éval.) | Illustr. | Points à vérifier |
|---|---|---|---|---|---|---|
| `sup-l1-algebre` | L1 | Mathématiques | 5 | 30 (+25) | 6 | 36 |
| `sup-l1-analyse` | L1 | Mathématiques | 5 | 30 (+25) | 5 | 35 |
| `sup-l1-comptabilite-generale` | L1 | Économie | 4 | 28 (+20) | 4 | 34 |
| `sup-l1-droit-des-obligations` | L2 | Droit | 4 | 24 (+20) | 4 | 77 |
| `sup-l1-informatique-algorithmique` | L1 | Sciences | 5 | 31 (+25) | 5 | 35 |
| `sup-l1-informatique-systemes` | L1 | Sciences | 4 | 24 (+20) | 7 | 31 |
| `sup-l1-introduction-droit` | L1 | Droit | 5 | 30 (+25) | 4 | 73 |
| `sup-l1-macroeconomie` | L1 | Économie | 5 | 35 (+25) | 6 | 36 |
| `sup-l1-methodologie-travail-universitaire` | L1 | Français | 5 | 30 (+25) | 5 | 39 |
| `sup-l1-microeconomie` | L1 | Économie | 5 | 35 (+25) | 6 | 34 |
| `sup-l1-statistiques-descriptives` | L1 | Mathématiques | 4 | 24 (+20) | 4 | 27 |
| `sup-l2-comptabilite-generale` | L2 | Économie | 4 | 28 (+20) | 4 | 37 |
| `sup-l2-droit-des-affaires-ohada` | L2 | Droit | 4 | 24 (+20) | 4 | 80 |
| `sup-l2-macroeconomie` | L2 | Économie | 4 | 28 (+20) | 4 | 31 |
| `sup-l2-mathematiques-financieres` | L2 | Mathématiques | 4 | 29 (+20) | 4 | 31 |
| `sup-l2-probabilites` | L2 | Mathématiques | 5 | 31 (+25) | 5 | 36 |
| `sup-l2-statistiques-inferentielles` | L2 | Mathématiques | 4 | 24 (+20) | 4 | 28 |

**Ce qui existe (fiches)**

- **Algèbre linéaire et matrices — L1** (`sup-l1-algebre`) : Application : le modèle de Leontief simplifié ; Espaces vectoriels : introduction ; Systèmes linéaires et méthode du pivot ; Matrices : opérations et transposée ; Déterminants et matrice inverse
- **Analyse — L1 (mathématiques et économie)** (`sup-l1-analyse`) : Dérivation et étude de fonctions ; Développements limités d'ordre 2 ; Primitives, intégrales et équations différentielles ; Fonctions et ensembles de définition ; Limites et continuité
- **Comptabilité générale 1 — L1 gestion** (`sup-l1-comptabilite-generale`) : Cadre comptable, bilan et compte de résultat ; Les comptes et la partie double ; Journal, grand livre et balance ; Opérations courantes et TVA
- **Droit des obligations — notions (L1/L2)** (`sup-l1-droit-des-obligations`) : Effets du contrat et inexécution ; Responsabilité délictuelle et extinction des obligations ; Notion d'obligation et sources ; Formation du contrat et vices du consentement
- **Informatique : algorithmique et programmation — L1** (`sup-l1-informatique-algorithmique`) : Algorithmes : variables, conditions et boucles ; Tableaux : parcours, somme, maximum et recherche ; Complexité et tris simples ; Numération : binaire, octal et hexadécimal ; Logique booléenne et tables de vérité
- **Informatique : ordinateur, systèmes et réseaux — L1** (`sup-l1-informatique-systemes`) : Bases de données : tables, clés et requêtes SELECT ; Sécurité informatique : principes et bonnes pratiques ; Architecture d'un ordinateur et système d'exploitation ; Réseaux : modèle en couches, adresses IP, DNS et HTTP
- **Introduction générale au droit — L1** (`sup-l1-introduction-droit`) : Droit public, droit privé et droits subjectifs ; Les personnes juridiques et la preuve ; L'organisation de la justice et le pluralisme juridique ; La règle de droit ; Les sources du droit et leur hiérarchie
- **Macroéconomie — L1 économie-gestion** (`sup-l1-macroeconomie`) : Consommation, épargne et investissement ; Inflation et chômage ; Circuit économique, valeur ajoutée et PIB ; PIB nominal, PIB réel, déflateur et croissance ; Monnaie, banques et création monétaire
- **Méthodologie du travail universitaire — L1** (`sup-l1-methodologie-travail-universitaire`) : Exposé oral, travail en groupe, numérique et IA ; Réussir la transition et organiser son temps ; Prendre des notes et lire efficacement ; Rechercher, évaluer ses sources, citer ; Préparer et réussir un examen
- **Microéconomie — L1 économie-gestion** (`sup-l1-microeconomie`) : Rareté, agents économiques et coût d'opportunité ; Utilité et choix du consommateur ; Demande, offre et équilibre de marché ; Élasticités-prix et élasticité-revenu ; Coûts de production, concurrence parfaite et monopole
- **Statistiques descriptives — L1** (`sup-l1-statistiques-descriptives`) : Séries doubles : covariance, corrélation et régression ; Séries statistiques, tableaux et indices simples ; Moyenne, médiane, mode et quantiles ; Dispersion : variance et écart-type
- **Comptabilité générale 2 — L2 gestion** (`sup-l2-comptabilite-generale`) : Résultat, bilan après répartition et états financiers ; Amortissements : linéaire et dégressif ; Dépréciations et provisions ; Stocks (CUMP, FIFO) et régularisations
- **Droit des affaires et OHADA — introduction (L2)** (`sup-l2-droit-des-affaires-ohada`) : L'OHADA : but et cadre général ; Commerçant, acte de commerce et fonds de commerce ; Les sociétés commerciales : SNC, SARL, SA ; Procédures collectives et arbitrage
- **Macroéconomie — L2 (modèles de base)** (`sup-l2-macroeconomie`) : Le modèle IS-LM ; Le modèle keynésien en économie fermée ; Modèle keynésien : État et économie ouverte ; Offre et demande agrégées, balance des paiements et taux de change
- **Mathématiques financières — L2** (`sup-l2-mathematiques-financieres`) : Annuités constantes : placement et emprunt ; Choix d'investissement : VAN et TRI ; Intérêts simples et escompte ; Intérêts composés, taux équivalents et valeur actuelle
- **Probabilités — L2** (`sup-l2-probabilites`) : Dénombrement et probabilités ; Probabilités conditionnelles, indépendance et formule de Bayes ; Variables aléatoires discrètes : espérance et variance ; Lois usuelles discrètes : binomiale et de Poisson ; Variables continues et loi normale
- **Statistiques inférentielles — L2** (`sup-l2-statistiques-inferentielles`) : Échantillonnage et estimation ponctuelle ; Intervalles de confiance ; Tests d'hypothèses sur une moyenne et une proportion ; Test du khi-deux (introduction) et régression linéaire simple

**Ce qui manque**
- **Économie** : microéconomie ordinale (courbes d'indifférence, TMS), effets de substitution/revenu, structures de marché avancées (oligopole, jeux), croissance et développement, économie internationale, monnaie-banque approfondie, histoire de la pensée, économétrie ; macro L2 : Mundell-Fleming, courbe de Phillips.
- **Gestion / comptabilité** : comptabilité analytique et des sociétés, paie, fiscalité, gestion financière/diagnostic, marketing, management, GRH, contrôle de gestion, gestion de production ; L3 non couvert.
- **Maths/stats** : analyse L2 complète (séries, intégrales généralisées, fonctions de plusieurs variables), algèbre L2 (rang, diagonalisation), suites/séries, optimisation, statistiques : comparaison de deux échantillons, ANOVA, séries chronologiques, indices composés.
- **Informatique** : programmation dans un langage réel, structures de données, POO, bases de données SQL poussées, génie logiciel, réseaux avancés, systèmes (Linux), algorithmique avancée (récursivité).
- **Droit** : droit constitutionnel, administratif, pénal, des personnes et de la famille, des biens, du travail, fiscal, droit international, procédure ; organisation judiciaire exacte du Cameroun (à relire par un juriste). Rien sur le droit anglo-saxon (common law) en dépit du bijuridisme.
- **Autres filières** (lettres, sciences de l'éducation, sciences et techniques, médecine…) : non traitées. Pas d'épreuves blanches de L1/L2.

## C. Compétences de vie pratique
| Pack | Niveau | Matière (catalogue) | Fiches | Exercices (+ auto-éval.) | Illustr. | Points à vérifier |
|---|---|---|---|---|---|---|
| `vie-citoyennete` | 4e | Histoire-Géo-ECM | 4 | 24 (+20) | 7 | 77 |
| `vie-code-route` | 2nde | Droit | 4 | 24 (+20) | 7 | 82 |
| `vie-education-financiere` | 2nde | Économie | 5 | 30 (+25) | 9 | 46 |
| `vie-hygiene-sante` | 5e | Sciences | 6 | 36 (+30) | 6 | 122 |

**Ce qui existe (fiches)**

- **Citoyenneté et vivre ensemble au Cameroun** (`vie-citoyennete`) : Droits, devoirs et droits de l'enfant ; Civisme : corruption, vie scolaire et environnement ; L'État, la nation, les symboles et la Constitution ; La séparation des pouvoirs et les institutions
- **Code de la route — notions de base** (`vie-code-route`) : Alcool, téléphone et distance d'arrêt ; Documents du conducteur et gestes en cas d'accident ; Le piéton, les panneaux, les marquages et les feux ; Priorités, moto-taxi, vélo et sécurité des passagers
- **Éducation financière de base** (`vie-education-financiere`) : Monnaie mobile, prix et pourcentages ; Petit commerce et impôts ; Besoins, envies et budget ; Épargner : tontine, banque, microfinance, assurance ; Crédit, intérêts et surendettement
- **Hygiène et santé au quotidien** (`vie-hygiene-sante`) : Hygiène du corps, eau potable et aliments ; Bien manger, bien dormir, bouger ; Le paludisme : comprendre et prévenir ; Diarrhée, choléra et vaccination ; VIH/sida et IST : savoir, se protéger, respecter ; Premiers secours, santé mentale et adolescence

**Ce qui manque**
- **Santé** : hygiène menstruelle, nutrition détaillée (aucune quantité), calendrier vaccinal (renvoi au centre de santé), premiers secours techniques, santé mentale approfondie ; tout est `review` : **relecture par un agent de santé obligatoire**.
- **Finance** : intérêt composé (cité seulement), taxes et taux officiels, fonctionnement légal banques/microfinance, entrepreneuriat.
- **Code de la route** : textes et sanctions précis, catégories de permis, numéros d'urgence, panneaux détaillés (aucun chiffre réglementaire n'est affirmé ; distance d'arrêt avec données d'énoncé).
- **Citoyenneté** : histoire du Cameroun, détail des institutions et des collectivités, hymne, droits de l'enfant (textes), éducation à la paix ; aucune date, aucun nom de responsable politique, aucun chiffre démographique.
- Rattachement à un niveau du catalogue (5e, 2nde, 4e) faute de niveau « tous publics ».

## Estimation de couverture (honnête)
Rapportée aux programmes officiels complets de ce périmètre (qu'on n'a pas pu consulter : les `programRef` sont génériques « à vérifier ») : **ETP ≈ 5-10 %** (surtout socle commun et tertiaire de 1re), **supérieur L1/L2 économie-gestion et maths-stats ≈ 20-30 %** des cours fondamentaux, **droit L1/L2 ≈ 10-15 %**, **vie pratique ≈ 60-70 %** des thèmes de base. Ce sont des estimations d'ordre de grandeur, à recalibrer sur les textes officiels.

## Priorités de relecture
1. Santé et premiers secours (`vie-hygiene-sante`, `etp-esf-1re` puériculture, `etp-cap-bep-securite-atelier`) : agent de santé.
2. Droit (`sup-l1-introduction-droit`, `sup-l1-droit-des-obligations`, `sup-l2-droit-des-affaires-ohada`, `vie-code-route`, `vie-citoyennete`) : juriste ; organisation judiciaire, hiérarchie des normes, institutions.
3. Comptabilité (`etp-compta-generale-1re`, `sup-l1/l2-comptabilite-generale`) : comptable ; cadre SYSCOHADA révisé, intitulés de comptes, taux de TVA (toujours donnés comme hypothèses d'énoncé).
4. Conventions mathématiques/statistiques (quartiles, variance ÷N, valeurs critiques fournies, convention 360 jours, définitions marge/taux de marque).
5. Données techniques (génie civil, électricité, dessin normalisé) : toutes les valeurs sont des données d'énoncé ; comparer aux normes enseignées.
