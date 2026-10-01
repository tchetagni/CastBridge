# Couverture du Quiz : culture générale (famille et école)

Branche `claude/content-quiz-culture` (depuis `integration/agents`), build `v1` du 2026-10-01. Pipeline `tools/quiz-bank/`
(lancer avec **python3.12**), modules `qb/facts/cult_*.py`, packs `content/quiz/dist/quiz-general-p1…p5-v1.quiz.zip`.

**Statut de tout ce qui est ajouté : `review`, jamais `approved`.** Les faits ont été écrits par l'assistant depuis ses
connaissances générales, puis relus de façon adverse par un second passage (≈ 200 questions douteuses, ambiguës ou dont la
réponse figurait dans l'énoncé ont été retirées ou corrigées) ; aucune source externe n'a été consultée. La relecture humaine
(prévue 3 mois après la distribution aux testeurs bêta) reste nécessaire : chaque fiche source (`sources.json`, ids `cult-*`)
dit où comparer. Seuls des faits intemporels ou datés sont posés (pas de titulaire « actuel », pas de record ni de chiffre qui change).

## Résultat global (culture générale)

| | Avant | Après |
|---|---:|---:|
| Questions | 3 205 | **6 515** (+ 3 310 nettes, 3 718 écrites par les modules `cult_*`) |
| Cameroun (CM) | 1 639 | **3 204** (besoin 3 150) |
| Afrique (AF) | 591 | **1 374** (besoin 900) |
| Monde (WORLD) | 975 | **1 937** (besoin 450) |
| Parties sans répétition (par effectif et quotas 70/20/10) | 156 | **305** (objectif 300) |
| Parties sans répétition (montée stricte 1→5, 3 par niveau) | 84 | **174** |

Les chiffres avant/après des questions « calculées » (calendrier, fuseaux) diminuent un peu : `balance.py` plafonne un parcours
à 6 500 questions en éclaircissant d'abord les questions calculées, jamais les faits.

Difficulté (culture générale entière) : CM [243, 512, 1 106, 1 021, 322] · AF [84, 351, 492, 272, 175] · WORLD [196, 387, 667, 466, 221]
(niveaux 1 à 5). Bonnes réponses en A/B/C/D : 1 666 / 1 571 / 1 592 / 1 686 (équilibré).

## Par thème (nouveaux modules)

| Module (fiche) | Région | Questions | Difficulté 1 à 5 | Contenu |
|---|---|---:|---|---|
| `cult_cm_geo` (cm-geo2) | CM | 264 | 13 · 46 · 57 · 82 · 66 | relief, climats, fleuves, côte, frontières, localités, ports, routes |
| `cult_cm_hist` (cm-hist2) | CM | 259 | 15 · 36 · 94 · 71 · 43 | royaumes, colonisation, mandat/tutelle, indépendance, institutions, symboles, diplomatie |
| `cult_cm_pers` (cm-pers) | CM | 214 | 8 · 35 · 63 · 66 · 42 | personnalités (lettres, musique, arts), football (Mondiaux, CAN), autres sports, dates datées |
| `cult_cm_culture` (cm-culture2) | CM (+1 AF) | 222 | 13 · 45 · 58 · 64 · 42 | peuples, langues, gastronomie, musique, fêtes, artisanat, patrimoine |
| `cult_cm_nature_eco` (cm-nat-eco) | CM | 248 | 31 · 39 · 82 · 70 · 26 | faune, flore, parcs, agriculture, mines, énergie, entreprises, FCFA |
| `cult_cm_civic_sante` (civic-sante) | CM 250 · WORLD 134 | 384 | 22 · 83 · 143 · 104 · 32 | éducation civique, sécurité routière, santé publique, hygiène, premiers secours, vaccination |
| `cult_cm_ecole` (cm-ecole) | CM | 153 | 13 · 39 · 53 · 42 · 6 | programme scolaire : climats, éducation civique, système éducatif, communes |
| `cult_cm_base` (cm-base) | CM | 121 | 9 · 31 · 29 · 40 · 12 | faits de base complémentaires (voisins, pidgin, lac Nyos, clubs, quartiers) |
| `cult_af_geo_hist` (af-geo-hist) | AF | 458 | 35 · 93 · 142 · 111 · 77 | fleuves, lacs, reliefs, empires, traite, décolonisation, organisations |
| `cult_af_culture_sport` (af-culture) | AF | 391 | 17 · 65 · 110 · 127 · 72 | peuples et langues, musiques, littérature, cinéma, cuisines, patrimoine, football |
| `cult_world_sci` (world-sci) | WORLD | 387 | 15 · 58 · 118 · 128 · 68 | astronomie, corps humain, physique, chimie, inventions, numérique, environnement |
| `cult_world_hist_arts` (world-hist) | WORLD | 617 | 40 · 95 · 202 · 212 · 68 | géographie, histoire, littérature, peinture, musique, JO, Coupes du monde |

`cult_zz_dedupe.py` retire 43 questions qui reprenaient un fait déjà posé par un module plus ancien (mêmes réponse et mots).

## Lacunes et limites

- **Niveaux 1 et 5** : le niveau 1 compte 523 questions (cible 900 pour 300 parties à montée stricte) et le niveau 5, 718 : le jeu
  retombe donc sur la difficulté voisine après ≈ 174 parties à montée parfaite. Il manque surtout des questions **faciles** sur
  le Cameroun (CM niveau 1 : 243, soit ≈ 80 parties) : les faits très faciles nouveaux sont rares une fois les bases couvertes.
- **Monde et Afrique sont au-dessus des quotas** (1 937 pour 450 ; 1 374 pour 900) : seul le Cameroun limite les parties (305 ≈ 3 204 / 10,5).
  Un surplus ne donne pas de partie en plus tant que le Cameroun n'augmente pas.
- Faits écartés par prudence (pas écrits) : numéros d'urgence, âge de la majorité civile, dates de mise en service des barrages et
  du port de Kribi, titulaires de poste, classements, chiffres de population, résultats récents.
- Points signalés « à relire en priorité » par les relecteurs : `cm-pers` (prix littéraires, dates de parution, scores de 1990/1994/2017),
  `cm-culture2` (attributions ethno-régionales des plats), `cm-nat-eco` (poivre de Penja, BEAC 1972, COMIFAC 2005),
  `civic-sante` (premiers secours, âge de vote, dates Elecam/CONAC), `af-culture` (palmarès CAN, « premiers » africains),
  `world-sci` (insuline, Ada Lovelace, citation de Lavoisier), `cm-geo2` (tableaux de localités et de quartiers).
- 35 avertissements du contrôle qualité restent (formulation identique entre deux modèles pour les années des Jeux olympiques ;
  quasi-doublons préexistants entre modules plus anciens) : sans effet sur le jeu.
- Aucun changement du format des packs, de la signature ou du code Kotlin. Les packs d'autres parcours sont identiques (octet pour octet).
  La version de pack reste `v1` : un appareil qui a déjà téléchargé l'ancien `quiz-general-p1…p3-v1` ne se met pas à jour tout seul
  (les lots 4 et 5 sont nouveaux) ; prévoir `pack-version.txt` + 1 au prochain empaquetage des lots.
- Rappel (docs/QUIZ.md § 6 ter) : une question `review` + `fact` n'est jouable que si l'appli active le mode bêta « contenu non validé » ;
  sinon elle est exclue des parties jusqu'à approbation (`quizbank.py approve`).

## Refaire le build
```sh
python3.12 tools/quiz-bank/quizbank.py check     # 0 rejet
python3.12 tools/quiz-bank/quizbank.py build     # réécrit content/quiz/dist (déterministe)
python3.12 tools/quiz-bank/lintfacts.py tools/quiz-bank/qb/facts/cult_xxx.py   # contrôle rapide d'un module
```
