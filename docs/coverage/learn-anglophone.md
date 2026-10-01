# Couverture « Apprendre » — sous-système anglophone (contenu en anglais)

> Généré le 2026-10-01 (branche `claude/content-learn-anglophone`, depuis `integration/agents`). **Tout ce contenu est un brouillon
> rédigé avec une IA** : statut `draft` sur toutes les fiches, notes de relecture (`reviewNotes`) sur chacune, jamais `reviewed`/`validated`.
> Programmes officiels (MINEDUB, MINESEC, Cameroon GCE Board) **non consultés** : les sujets ci-dessous viennent de la connaissance générale
> des programmes ; chaque `programRef` dit « to be checked ». Les pourcentages de couverture sont des **estimations** à faire confirmer.
> Reproduire / régénérer : `tools/learn-authoring/anglophone/<pack>.py` (voir `tools/learn-authoring/README.md`) ; compter : `tools/learn-authoring/coverage.py`.

## 1. Chiffres (par pack, puis par classe/niveau)

« Exercises » = exercices notés (hors auto-évaluations, y compris questions des épreuves blanches et sous-questions des problèmes) ; « Self-checks » =
QCM d'auto-évaluation (5 par fiche, exportés vers le quiz). Chaque fiche : 2 exemples résolus, 3 exercices d'application + 2 d'approfondissement + 1 type examen,
au moins une illustration. « Subject » = clé **souhaitée** du catalogue (voir § 4).

| Level | Pack | Subject | Exam | Lessons | Exercises | Self-checks | Illustrations | Mocks | KB |
|---|---|---|---|---|---|---|---|---|---|
| Class 1 | class1-english | english | - | 15 | 90 | 75 | 30 | 0 | 167 |
| Class 1 | class1-maths | maths | - | 16 | 96 | 80 | 31 | 0 | 177 |
| Class 1 | class1-science | sciences | - | 13 | 78 | 65 | 26 | 0 | 146 |
| Class 1 | class1-social-studies | social-studies | - | 10 | 60 | 50 | 20 | 0 | 117 |
| Class 2 | class2-english | english | - | 12 | 72 | 60 | 24 | 0 | 133 |
| Class 2 | class2-maths | maths | - | 17 | 102 | 85 | 29 | 0 | 206 |
| Class 2 | class2-science | sciences | - | 13 | 78 | 65 | 27 | 0 | 148 |
| Class 2 | class2-social-studies | social-studies | - | 10 | 60 | 50 | 20 | 0 | 123 |
| Class 3 | class3-english | english | - | 14 | 84 | 70 | 28 | 0 | 166 |
| Class 3 | class3-maths | maths | - | 16 | 96 | 80 | 25 | 0 | 192 |
| Class 3 | class3-science | sciences | - | 15 | 90 | 75 | 30 | 0 | 174 |
| Class 3 | class3-social-studies | social-studies | - | 11 | 66 | 55 | 22 | 0 | 138 |
| Class 4 | class4-english | english | - | 15 | 90 | 75 | 15 | 0 | 191 |
| Class 4 | class4-maths | maths | - | 17 | 102 | 85 | 29 | 0 | 242 |
| Class 4 | class4-science | elementary-science | - | 12 | 72 | 60 | 17 | 0 | 180 |
| Class 4 | class4-social-studies | social-studies | - | 9 | 54 | 45 | 15 | 0 | 146 |
| Class 5 | class5-english | english | - | 17 | 102 | 85 | 17 | 0 | 232 |
| Class 5 | class5-maths | maths | - | 15 | 90 | 75 | 22 | 0 | 184 |
| Class 5 | class5-science | elementary-science | - | 15 | 90 | 75 | 24 | 0 | 235 |
| Class 5 | class5-social-studies | social-studies | - | 10 | 60 | 50 | 17 | 0 | 173 |
| Class 6 | fslc-english | english | FSLC | 18 | 122 | 90 | 19 | 2 | 274 |
| Class 6 | fslc-maths | maths | FSLC | 20 | 138 | 100 | 24 | 2 | 255 |
| Class 6 | fslc-science | sciences | FSLC | 16 | 115 | 80 | 19 | 2 | 251 |
| Class 6 | fslc-social-studies | social-studies | FSLC | 11 | 78 | 55 | 12 | 1 | 160 |
| Form 1 | form1-citizenship | citizenship | - | 6 | 36 | 30 | 7 | 0 | 84 |
| Form 1 | form1-computer | computer-science | - | 5 | 30 | 25 | 5 | 0 | 60 |
| Form 1 | form1-english | english | - | 12 | 72 | 60 | 12 | 0 | 161 |
| Form 1 | form1-geography | geography | - | 6 | 36 | 30 | 6 | 0 | 89 |
| Form 1 | form1-history | history | - | 6 | 36 | 30 | 6 | 0 | 82 |
| Form 1 | form1-literature | literature | - | 7 | 42 | 35 | 7 | 0 | 92 |
| Form 1 | form1-maths | maths | - | 14 | 84 | 70 | 25 | 0 | 181 |
| Form 1 | form1-science | sciences | - | 12 | 72 | 60 | 12 | 0 | 160 |
| Form 2 | form2-citizenship | citizenship | - | 6 | 36 | 30 | 6 | 0 | 86 |
| Form 2 | form2-computer | computer-science | - | 5 | 30 | 25 | 5 | 0 | 60 |
| Form 2 | form2-english | english | - | 11 | 66 | 55 | 11 | 0 | 148 |
| Form 2 | form2-geography | geography | - | 6 | 36 | 30 | 6 | 0 | 82 |
| Form 2 | form2-history | history | - | 6 | 36 | 30 | 6 | 0 | 83 |
| Form 2 | form2-literature | literature | - | 8 | 48 | 40 | 8 | 0 | 110 |
| Form 2 | form2-maths | maths | - | 13 | 78 | 65 | 21 | 0 | 174 |
| Form 2 | form2-science | sciences | - | 12 | 72 | 60 | 13 | 0 | 163 |
| Form 3 | form3-biology | biology | - | 12 | 72 | 60 | 15 | 0 | 181 |
| Form 3 | form3-chemistry | chemistry | - | 10 | 60 | 50 | 12 | 0 | 160 |
| Form 3 | form3-citizenship | citizenship | - | 6 | 36 | 30 | 6 | 0 | 87 |
| Form 3 | form3-computer | computer-science | - | 5 | 30 | 25 | 5 | 0 | 63 |
| Form 3 | form3-english | english | - | 11 | 66 | 55 | 11 | 0 | 148 |
| Form 3 | form3-geography | geography | - | 6 | 36 | 30 | 6 | 0 | 84 |
| Form 3 | form3-history | history | - | 6 | 36 | 30 | 6 | 0 | 81 |
| Form 3 | form3-literature | literature | - | 8 | 48 | 40 | 8 | 0 | 114 |
| Form 3 | form3-maths | maths | - | 12 | 72 | 60 | 26 | 0 | 212 |
| Form 3 | form3-physics | physics | - | 11 | 66 | 55 | 11 | 0 | 141 |
| Form 4 | form4-biology | biology | - | 10 | 60 | 50 | 11 | 0 | 131 |
| Form 4 | form4-chemistry | chemistry | - | 11 | 66 | 55 | 14 | 0 | 165 |
| Form 4 | form4-citizenship | citizenship | - | 7 | 42 | 35 | 7 | 0 | 99 |
| Form 4 | form4-computer | computer-science | - | 5 | 30 | 25 | 5 | 0 | 59 |
| Form 4 | form4-economics | economie | - | 11 | 66 | 55 | 11 | 0 | 118 |
| Form 4 | form4-english | english | - | 11 | 66 | 55 | 11 | 0 | 149 |
| Form 4 | form4-food-science | food-science | - | 6 | 36 | 30 | 6 | 0 | 85 |
| Form 4 | form4-geography | geography | - | 6 | 36 | 30 | 6 | 0 | 89 |
| Form 4 | form4-history | history | - | 7 | 42 | 35 | 7 | 0 | 92 |
| Form 4 | form4-literature | literature | - | 8 | 48 | 40 | 8 | 0 | 114 |
| Form 4 | form4-maths | maths | - | 12 | 72 | 60 | 24 | 0 | 191 |
| Form 4 | form4-physics | physics | - | 11 | 66 | 55 | 15 | 0 | 165 |
| Form 5 | gceol-biology | biology | GCE-OL | 37 | 230 | 185 | 56 | 2 | 607 |
| Form 5 | gceol-chemistry | chemistry | GCE-OL | 31 | 198 | 155 | 31 | 1 | 453 |
| Form 5 | gceol-citizenship | citizenship | GCE-OL | 12 | 76 | 60 | 13 | 1 | 179 |
| Form 5 | gceol-computer | computer-science | GCE-OL | 13 | 87 | 65 | 13 | 1 | 183 |
| Form 5 | gceol-economics | economie | GCE-OL | 21 | 130 | 105 | 21 | 1 | 252 |
| Form 5 | gceol-english | english | GCE-OL | 22 | 148 | 110 | 24 | 2 | 369 |
| Form 5 | gceol-food-science | food-science | GCE-OL | 11 | 75 | 55 | 11 | 1 | 151 |
| Form 5 | gceol-further-maths | further-maths | GCE-OL | 21 | 130 | 105 | 21 | 1 | 192 |
| Form 5 | gceol-geography | geography | GCE-OL | 16 | 100 | 80 | 17 | 1 | 234 |
| Form 5 | gceol-history | history | GCE-OL | 10 | 64 | 50 | 10 | 1 | 135 |
| Form 5 | gceol-literature | literature | GCE-OL | 11 | 71 | 55 | 11 | 1 | 173 |
| Form 5 | gceol-maths | maths | GCE-OL | 39 | 242 | 195 | 45 | 2 | 371 |
| Form 5 | gceol-physics | physics | GCE-OL | 28 | 179 | 140 | 28 | 1 | 354 |
| Lower Sixth | lower6-biology | biology | - | 18 | 108 | 90 | 18 | 0 | 211 |
| Lower Sixth | lower6-chemistry | chemistry | - | 17 | 102 | 85 | 17 | 0 | 270 |
| Lower Sixth | lower6-computer | computer-science | - | 11 | 66 | 55 | 11 | 0 | 161 |
| Lower Sixth | lower6-economics | economie | - | 12 | 72 | 60 | 12 | 0 | 135 |
| Lower Sixth | lower6-food-science | food-science | - | 10 | 60 | 50 | 10 | 0 | 146 |
| Lower Sixth | lower6-further-maths | further-maths | - | 9 | 54 | 45 | 9 | 0 | 113 |
| Lower Sixth | lower6-geography | geography | - | 11 | 66 | 55 | 11 | 0 | 140 |
| Lower Sixth | lower6-history | history | - | 11 | 66 | 55 | 11 | 0 | 140 |
| Lower Sixth | lower6-literature | literature | - | 9 | 54 | 45 | 9 | 0 | 124 |
| Lower Sixth | lower6-maths | maths | - | 22 | 132 | 110 | 23 | 0 | 257 |
| Lower Sixth | lower6-physics | physics | - | 20 | 120 | 100 | 20 | 0 | 270 |
| Upper Sixth | gceal-biology | biology | GCE-AL | 21 | 130 | 105 | 21 | 1 | 246 |
| Upper Sixth | gceal-chemistry | chemistry | GCE-AL | 16 | 103 | 80 | 16 | 1 | 274 |
| Upper Sixth | gceal-computer | computer-science | GCE-AL | 11 | 75 | 55 | 11 | 1 | 168 |
| Upper Sixth | gceal-economics | economie | GCE-AL | 13 | 82 | 65 | 13 | 1 | 162 |
| Upper Sixth | gceal-food-science | food-science | GCE-AL | 10 | 69 | 50 | 10 | 1 | 152 |
| Upper Sixth | gceal-further-maths | further-maths | GCE-AL | 10 | 67 | 50 | 11 | 1 | 155 |
| Upper Sixth | gceal-geography | geography | GCE-AL | 13 | 91 | 65 | 13 | 1 | 178 |
| Upper Sixth | gceal-history | history | GCE-AL | 10 | 73 | 50 | 10 | 1 | 136 |
| Upper Sixth | gceal-literature | literature | GCE-AL | 10 | 70 | 50 | 10 | 1 | 148 |
| Upper Sixth | gceal-maths | maths | GCE-AL | 20 | 127 | 100 | 20 | 1 | 236 |
| Upper Sixth | gceal-physics | physics | GCE-AL | 16 | 103 | 80 | 16 | 1 | 237 |

| Level | Packs | Lessons | Exercises | Self-checks | Illustrations | Mocks | JSON KB (uncompressed) |
|---|---|---|---|---|---|---|---|
| Class 1 | 4 | 54 | 324 | 270 | 107 | 0 | 607 |
| Class 2 | 4 | 52 | 312 | 260 | 100 | 0 | 610 |
| Class 3 | 4 | 56 | 336 | 280 | 105 | 0 | 670 |
| Class 4 | 4 | 53 | 318 | 265 | 76 | 0 | 759 |
| Class 5 | 4 | 57 | 342 | 285 | 80 | 0 | 824 |
| Class 6 | 4 | 65 | 453 | 325 | 74 | 7 | 940 |
| Form 1 | 8 | 68 | 408 | 340 | 80 | 0 | 909 |
| Form 2 | 8 | 67 | 402 | 335 | 76 | 0 | 906 |
| Form 3 | 10 | 87 | 522 | 435 | 106 | 0 | 1271 |
| Form 4 | 12 | 105 | 630 | 525 | 125 | 0 | 1457 |
| Form 5 | 13 | 272 | 1730 | 1360 | 301 | 16 | 3653 |
| Lower Sixth | 11 | 150 | 900 | 750 | 151 | 0 | 1967 |
| Upper Sixth | 11 | 150 | 990 | 750 | 151 | 11 | 2092 |
| **Total** | **97** | **1236** | **7667** | **6180** | **1532** | **34** | **16665** |


## 2. Couverture par classe / niveau et matière (sujets traités, absents, estimation)

Légende : ✔ traité, ◐ partiel, ✘ absent. Estimation = part des grands chapitres du programme probable couverte par au moins une fiche avec exercices.

### Primaire — Class 1 à 5 (MINEDUB, niveaux I-III), lecture à voix haute activée
| Matière | Sujets traités | Manques connus | Couverture estimée |
|---|---|---|---|
| Mathematics | Class 1 : nombres à 100, + et −, formes, mesures, FCFA, heures ; Class 2 : nombres à 1000, valeur de position, ×/÷ intro, moitiés/quarts ; Class 3 : 4 opérations, tables, fractions, mesures, périmètre, pictogrammes ; Class 4 : nombres à 100 000, fractions/décimaux, aire, angles, formes ; Class 5 : grands nombres, PGCD/PPCM, fractions/décimaux/%, rapport, volume, moyennes, intérêt simple | chiffres romains, nombres négatifs, symétrie, cercles/π (avant Class 6), aire en Class 1-3, division longue à plusieurs chiffres | ~75-85 % |
| English Language | alphabet/phonétique, mots, groupes de mots, temps, ponctuation, lecture (textes originaux), écriture guidée, comptines, anglais oral | écriture manuscrite/cursive, dictée audio, littérature de base, apostrophes/guillemets (Class 1-3) | ~70-80 % |
| Elementary Science | êtres vivants, corps, sens, plantes, animaux, aliments, hygiène, eau, météo, matériaux, sécurité, environnement ; Class 4-5 : photosynthèse, chaînes alimentaires, respiration/circulation, états de la matière, lumière/chaleur/électricité/aimants, machines simples | son, électricité (Class 1-3), système solaire détaillé, symboles chimiques | ~70-80 % |
| Social Studies | famille, école, communauté, symboles, régions, cartes, civisme ; Class 4-5 : géographie du Cameroun, histoire en grandes lignes (faits établis seulement), Afrique, organisations | histoire détaillée, personnages, chiffres de population/production (volontairement absents), vraies cartes du Cameroun (figures schématiques) | ~65-75 % |

### Class 6 — FSLC (packs existants étendus + Social Studies)
| Matière (pack) | Sujets traités | Manques connus | Couverture estimée |
|---|---|---|---|
| Mathematics (`fslc-maths`) | + valeur de position, facteurs/PGCD/PPCM, rapports, monnaie FCFA, temps, aire/volume, angles, cercles (π = 3,14), intérêt/profit, statistiques/probabilité, algèbre simple, motifs, conversions, vitesse | tests de divisibilité, constructions au rapporteur, arbres de probabilité | ~85 % |
| English (`fslc-english`) | + classes de mots, vocabulaire, orthographe, ponctuation, types de phrases, actif/passif, discours rapporté, conditionnel, compréhension, lettres, rédaction, poésie, oral | 3e conditionnel, temps rares, œuvres littéraires | ~80-85 % |
| Science (`fslc-science`) | + êtres vivants, plantes, vertébrés, sens, dents/alimentation, sol, air/eau/météo, lumière/chaleur/son, forces/machines, électricité, matériaux, pollution/déchets | points d'ébullition/solidification, densité, circuits série/parallèle | ~80-85 % |
| Social Studies (`fslc-social-studies`, nouveau) | régions, relief, climat, économie, histoire coloniale et indépendance (faits sûrs), droits/devoirs, symboles, routes, Afrique, cartes | traite négrière, politique après 1990, statistiques | ~70-80 % |

### Secondaire Forms 1-4 (MINESEC / feeder du GCE O Level)
| Matière | Sujets traités | Manques connus | Couverture estimée |
|---|---|---|---|
| Mathematics F1-F4 | nombres, fractions/%, rapports, ensembles, algèbre, équations, géométrie/constructions, aires/volumes, statistiques, indices, vitesse, commerce, graphes, quadratiques, variation, trigonométrie (sinus/cosinus), théorèmes du cercle, vecteurs 2D, suites AP/GP, histogrammes, bases | surds, logarithmes, fractions algébriques, inéquations quadratiques, programmation linéaire, inverses de matrices, trigonométrie 3D | ~75-85 % |
| English Language F1-F4 | grammaire progressive, temps, concordance, ponctuation, vocabulaire, compréhension (textes originaux), lettres/rédactions/rapports/discours/essais, oral, résumé (F3-F4) | transcription phonétique (API), enregistrements audio, procès-verbaux | ~75-80 % |
| Literature in English F1-F4 | éléments du récit, poésie, théâtre, figures de style, littérature orale (général), technique de commentaire — **sans œuvres au programme** (à ajouter par un enseignant) | œuvres officielles, histoire littéraire, auteurs précis | compétences ~70 % ; œuvres 0 % |
| Basic Science F1-F2 ; Physics F3-F4 ; Chemistry F3-F4 ; Biology F3 (+ F4 existant) | méthode scientifique, matière, mélanges, énergie ; mouvement, forces, énergie, chaleur, lumière, ondes, électricité, magnétisme ; atomes, liaisons, formules, acides/bases, sels, métaux, mole, énergie/vitesse des réactions, pétrole ; cellules, nutrition, transport, respiration, reproduction, écologie | quantité de mouvement, lois des gaz, radioactivité (en Form 5), génétique/excrétion/système nerveux (en Form 5), électrolyse (en Form 5) | ~75-80 % |
| Geography F1-F4 | Terre et cartes, météo/climat, roches, eau, Cameroun, population, habitat, agriculture, industrie/énergie, transports, commerce, tourisme, Afrique, environnement | cartes météo, glaciation, tectonique détaillée, études de cas hors Cameroun | ~65-75 % |
| History F1-F4 | sources, premières sociétés, royaumes africains, Cameroun précolonial, traite et abolition, Kamerun allemand, Première Guerre mondiale, mandats/tutelle, nationalisme, indépendance et réunification (neutre), organisations internationales | révolution industrielle, Mésopotamie, histoire européenne, UPC et années 1990 | ~60-70 % |
| Citizenship F1-F4 | famille, école, valeurs, symboles, droits de l'enfant, collectivités, nationalité, démocratie, justice, droits de l'homme, intégration nationale, bonne gouvernance, genre, VIH/drogues, ODD, citoyenneté numérique | pouvoirs du Président, hiérarchie judiciaire, textes de loi précis | ~65-75 % |
| Computer Studies F1-F4 | composants, usage sûr, fichiers, traitement de texte, tableur, internet/e-mail, présentation, binaire, bases de données, réseaux, algorithmes, programmation (pseudo-code), sécurité/éthique | pas de logiciel précis ; pas d'exécution de code | ~65-75 % |
| Economics F4 (Form 4) ; Food Science F4 | concepts, production, offre/demande, entreprises, monnaie/banque (njangi, microfinance), revenus/budget, population, impôts ; nutriments, régime équilibré, hygiène, cuisson, conservation | inflation/chômage/commerce (en Form 5) ; allergies, aliments spéciaux | ~65-75 % |

### Form 5 — GCE O Level (packs existants étendus + nouveaux), épreuves blanches /20
| Matière (pack) | Sujets traités | Manques connus | Couverture estimée |
|---|---|---|---|
| Mathematics (`gceol-maths`, 39 fiches) | ensembles, algèbre, trigonométrie, statistiques + nombres, arithmétique commerciale (FCFA, TVA), algèbre, graphes, géométrie, aires/volumes, vecteurs/matrices/transformations, trigonométrie (sinus/cosinus, 3D), statistiques (cumulées, histogrammes) | matrices de transformation, relèvements + sinus, dessin/constructions sur papier | ~85-90 % |
| Further Mathematics (`gceol-further-maths`) | logique, indices/surds, logarithmes, polynômes, fractions partielles, quadratiques, binôme, AP/GP, trigonométrie, radians, droites, cercles, dérivation, intégration, vecteurs, matrices, dénombrement, probabilités, variance | ln/e^x, dérivées produit/quotient, volumes de révolution, loi normale ; **programme exact du GCE Board inconnu** | ~60-70 % |
| English Language (`gceol-english`, 22 fiches) | compréhension, grammaire, discours rapporté, rédaction, résumé + vocabulaire, structure des phrases, ponctuation, types d'écrits, inférence, oral, prise de notes | enregistrements audio, mémos | ~85 % |
| Literature in English (`gceol-literature`) | compétences : prose, poésie, théâtre, littérature orale, termes, questions de contexte, essai PEEL, texte inédit — **sans œuvres au programme** | œuvres officielles, comparaison de deux textes, métrique | compétences ~70 % ; œuvres 0 % |
| Physics (`gceol-physics`) | mesures, mouvement, forces, moments, densité/pression, travail/énergie, thermique (5 fiches), ondes, lumière (3), électricité, magnétisme/induction, atome/radioactivité | résistivité, quantité de mouvement/chocs, oscilloscope, diodes/portes logiques, fission/fusion | ~80-85 % |
| Chemistry (`gceol-chemistry`) | matière, séparation, atome, tableau périodique, liaisons, formules/équations, mole (2), acides/bases/sels, redox/électrolyse, énergie/vitesse, réversibilité (Haber/Contact), air/eau, métaux, non-métaux, chimie organique, chimie au Cameroun | nombres d'oxydation, lois de Faraday, isomérie, constantes d'équilibre | ~80-85 % |
| Biology (`gceol-biology`, 37 fiches) | cellules, nutrition, génétique, écologie + classification, transport, respiration, excrétion/homéostasie, coordination, soutien/mouvement, reproduction, VIH/IST, croissance, sélection naturelle, ADN, maladies, tests alimentaires, sol, impact humain, compétences pratiques | peau (leçon dédiée), boucle de Henlé, ARN/synthèse protéique | ~85-90 % |
| Geography / History / Citizenship (`gceol-geography`, `-history`, `-citizenship`) | géo physique + humaine + Cameroun + Afrique + cartes ; histoire africaine précoloniale, Kamerun, mandats, indépendance (neutre), organisations ; civisme complet | glaciation, cartes météo, personnages historiques, UPC, pouvoirs détaillés | ~65-75 % |
| Economics (`gceol-economics`) | concepts, production, offre/demande, entreprises, coûts, monnaie/banques/BEAC-CEMAC, njangi, finances publiques, revenu national, population, chômage/inflation, commerce, développement, Cameroun, syndicats, consommateurs | multiplicateur, élasticité détaillée, taux de change, bourse/assurance | ~75-80 % |
| Computer Studies (`gceol-computer`), Food Science and Nutrition (`gceol-food-science`) | matériel/logiciel, binaire/hexa, représentation, OS, tableur/traitement de texte/BD, réseaux, sécurité, algorithmes, pseudo-code ; nutriments, régime, énergie, cuisson, hygiène, conservation, carences, additifs, étiquettes, budget | octal (survol), sports/végétarisme/allergies | ~75 % |

### Lower Sixth (non-examen) et Upper Sixth — GCE A Level (épreuves blanches /20)
| Matière | Lower Sixth | Upper Sixth (exam) | Manques connus | Couverture estimée |
|---|---|---|---|---|
| Mathematics | algèbre, polynômes, suites, binôme, trigonométrie, géométrie analytique, dérivation, intégration, vecteurs, e^x/ln, cinématique, forces, probabilités, lois binomiale/normale (22 fiches) | dérivation avancée, trig composée, intégration (substitution/parties/fractions), EDO, méthodes numériques, complexes (à confirmer), Maclaurin, vecteurs 3D, preuve, projectiles, moments, travail/énergie, Poisson, tests d'hypothèse, régression (20 fiches) | transformations de graphes, Simpson, ressorts, restitution, χ²/t, Spearman | ~70-75 % |
| Further Mathematics | matrices 2×2, transformations, complexes, polynômes, induction, séries, hyperboliques, produit vectoriel (9) | déterminants 3×3, valeurs propres, lieux, polaires, coniques, EDO 2e ordre, volumes de révolution, série binomiale, MHS, v.a. continues (10) | méthodes numériques, groupes, maths de décision ; **placement exact au programme non confirmé** | ~55-65 % |
| Physics | mesures/incertitudes, mécanique, matériaux, fluides, thermique, MHS, ondes, électricité (20) | champs, condensateurs/RC, magnétisme, induction, c.a., optique, quantique, nucléaire, thermodynamique, astronomie (à confirmer), semi-conducteurs, compétences (16) | RLC, effet Hall, instruments optiques, lasers, rayons X | ~70-75 % |
| Chemistry | atome, périodicité, liaisons/VSEPR, mole, gaz, énergétique, cinétique, équilibre, acides/tampons, redox/électrolyse, organique (17) | potentiels d'électrode, Kp/Ksp, titrages, énergie réticulaire/entropie, groupes 2/7, période 3, métaux de transition, carbonyles, acides, benzène, amines, polymères, MS/IR/RMN, Haber/Contact, pratique (16) | RMN 13C, SN1/SN2 en flèches, chiralité R/S, Nernst, piles | ~70-75 % |
| Biology | molécules, ultrastructure, membranes, mitose/méiose, ADN, synthèse protéique, classification, échanges/transport, immunité (18) | respiration, photosynthèse, homéostasie, nerveux, rein, génétique, Hardy-Weinberg, évolution, écologie, biotechnologie, reproduction, hormones végétales, statistiques (21) | comportement animal, contraception/santé sexuelle (volontairement omis), sous-types de lymphocytes T | ~75-80 % |
| Economics | micro complète + marchés + imperfections (12) | macro, banque/BEAC-CEMAC, finances publiques, commerce, balance des paiements, change, développement, économie du Cameroun (13) | théorie des jeux, courbes d'indifférence, données chiffrées du Cameroun | ~70 % |
| Geography | géomorphologie, climat, sols, population, migration, urbanisation, agriculture, cartes, SIG (11) | côtes, glaciation (intro), hydrologie, climat/environnement, désertification (lac Tchad), ville, industrie/énergie, commerce, tourisme, Cameroun/Afrique, enquête (13) | karst, déserts, risques, études de cas chiffrées, χ² | ~65-70 % |
| History | compétences, États précoloniaux, traite, exploration, partage de l'Afrique, Kamerun, WWI, mandats, administration, ONU (11) | nationalismes, indépendance/réunification (neutre), panafricanisme, OUA/UA, Afrique post-indépendance, WWII, guerre froide, Commonwealth, Francophonie (10) | histoire européenne, politique camerounaise détaillée, personnages | ~55-65 % |
| Literature in English | prose, poésie, théâtre, tragédie/comédie, oralité, contexte, planification (9) | techniques narratives, poésie comparée, théâtre, littérature africaine (général), critique postcoloniale/féministe/formaliste, comparaison (10) | **œuvres officielles (0 %)**, auteurs précis | compétences ~65 % |
| Computer Science | architecture, données, logique, structures, tri/recherche, paradigmes, SQL, réseaux, OS, éthique (11) | assembleur (jeu d'instructions inventé), virgule flottante, listes/arbres/hachage, récursivité/Big-O, POO, normalisation, crypto (RSA jouet), ordonnancement, cycle de vie, logique (K-maps), compilateurs (11) | sous-réseaux, OSI 7 couches, graphes ; **pseudo-code du GCE Board à aligner** | ~65-70 % |
| Food Science and Nutrition | chimie des nutriments, vitamines/minéraux, énergie/BMR/IMC, digestion, microbiologie/HACCP, analyses, transformation, plans de repas (10) | chimie des aliments (gélatinisation, Maillard), enzymes, microbiologie, technologie, qualité, cycle de vie, nutrition publique, analyses, maladies non transmissibles (10) | valeurs D/z, OGM, droit alimentaire | ~65-70 % |

### Matières du périmètre demandé et ce qui reste à faire
- **Religious studies** : **non traité** (le format n'a pas de matière « religion » ; sujet sensible, à confier à des enseignants du programme).
- **Lower Sixth / Upper Sixth English Language** (A Level) : non écrit (seuls Literature et le O Level English existent).
- **Citizenship / Civics au niveau A Level** : non demandé par le GCE Board à notre connaissance ; non écrit.
- **Épreuves blanches** : une à deux par pack d'examen (O Level, A Level, FSLC), /20 ; format officiel non vérifié.
- **Œuvres officielles de Literature** (GCE Board) : à ajouter par un expert (droits d'auteur).

## 3. Contrôles exécutés
- `tools/learn-authoring/check.sh` (vrai `LessonValidator`) : **OK, 0 erreur** sur les 108 packs de `content/learn`.
- Harnais « core seul » (le plugin Android `com.android.application` est introuvable dans le cloud ; `gradle :core:test --tests 'castbridge.core.*Learn*'` n'a pas pu tourner) :
  Kotlin 2.1.0 + sources de `:core` compilés directement, `LearnContentTest` et `LearnLogicTest` exécutés par réflexion. **29/32 verts**
  (dont `answersAreConsistent`, `everyPackIsValid`, `examCoverage`, `figureTextsDoNotOverlap`, `selfChecksPlayInTheQuiz`,
  `everyPackBuildsReproduciblyAndReadsBack`, `prerequisitesExistAndHaveNoCycle`). Les 3 échecs (`embeddedBudget`, `apiRoutes`,
  `libraryPrefersHigherVersionsAndSkipsBadPacks`) viennent uniquement de l'absence des zips embarqués générés par `embedLearnPacks` dans ce harnais ;
  même résultat avant ajout de contenu. Les packs embarqués (`embedded.txt`) restent à ~235 ko compressés (< 5 Mo).
- Chaque réponse numérique a été calculée en Python (assertions dans les générateurs) ; chaque bonne réponse est notée juste par le moteur de notation.
- **Non vérifié** : rendu sur TV/téléphone (aucune figure regardée à l'écran), exactitude des programmes et des faits (tout est à relire), `:receiver`/`:sender` non compilés.

## 4. Points à traiter par les autres agents / le propriétaire
1. **Matières absentes du catalogue** (`LearnCatalog.subjects`) : les packs utilisent la clé la plus proche et portent le champ additif `subjectWanted`
   (ignoré par le lecteur) : `literature` → `english` ; `further-maths` → `maths` ; `geography`/`history`/`citizenship`/`social-studies` → `histoire-geo` ;
   `computer-science`/`food-science`/`elementary-science` → `sciences`. Une fois les clés ajoutées au catalogue, remplacer `subject` par `subjectWanted` dans les `pack.json`.
   En attendant, la tuile de matière affiche le libellé du catalogue (pas le titre du pack) : plusieurs packs d'un même niveau affichent le même libellé.
2. **Lots < 3 Mo** : zippés, chaque niveau tient largement (le plus gros, Form 5 : ~0,83 Mo pour 13 packs ; Upper Sixth ~0,46 Mo). En JSON brut Form 5 = 3,65 Mo : si le lot compte le brut, découper par matière (un lot par pack suffit).
3. `EXAM_SUBJECTS` (LearnActivity) ne liste que maths/english/biology/physics/chemistry pour le GCE O Level et maths/physics/chemistry/biology pour le GCE A Level : les autres matières n'apparaissent pas comme « à télécharger » mais s'affichent si le pack est installé.
4. Les packs FSLC/GCE O Level existants (`fslc-*`, `gceol-maths/english/biology`) ont été **étendus** (chapitres ajoutés, fiches d'origine et épreuves blanches intactes) ; leur `pack.json` a été réécrit (diff bruyant, contenu d'origine conservé).
