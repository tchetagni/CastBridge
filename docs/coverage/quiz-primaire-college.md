# Couverture du quiz — primaire et premier cycle (2026-10-01)

Périmètre : primaire francophone CP, CE1, CE2, CM1, CM2 (CEP) ; collège 6e, 5e, 4e, 3e (BEPC) ; équivalents anglophones **Class 1-6 (FSLC)** et
**Form 1-3** (questions en anglais, `lang: "en"`). Tout est **`review`** (jamais `approved`) : l'app les joue avec la marque « beta » ;
la validation humaine est prévue 3 mois après la distribution. Rien n'a été copié d'annales ou de manuels.

**Comment c'est fabriqué** (pipeline existant, format, signature et code Kotlin inchangés) :
- `tools/quiz-bank/qb/courses_pc.py` : les 16 nouveaux parcours (CP-4e, Class 1-6, Form 1-3) ; `core.py` n'a que 2 lignes de plus (inscription + `lang` du parcours).
- Modèles calculés (réponse prouvée par le calcul, erreurs types en mauvaises réponses) : `qb/gen/pc_maths_prim.py`, `pc_maths_sec.py`, `pc_maths_more.py` (maths, bilingues fr/en),
  `pc_lang_fr.py` (conjugaison de 6 temps, groupes, pluriels, accords, homophones, grammaire, orthographe), `pc_lang_en.py` (English : pluriels, passé, comparatifs, prépositions, homophones…).
  Plafonds par modèle dans `qb/gen/pc_caps.json` (calculés par `pc_tune.py` : aucun modèle au-delà de 7,5 % d'un parcours, ~5 400 questions par parcours).
- Faits (tous relus une seconde fois par un relecteur sceptique, doutes retirés) : `qb/facts/pc_prim_fr.py`, `pc_col_fr.py`, `pc_anglais_info.py`, `pc_en.py` ; fiches sources `pcp-`, `pcc-`, `pca-`, `pce-`.
- `tools/quiz-bank/build_scope.py` reconstruit **seulement** les packs de ce périmètre (CM2 et 3e inclus, car ils reçoivent des faits d'anglais, d'informatique et d'ECM) et fusionne `catalog.json`, `coverage.json`, `sources.json`.
- Contrôles : `pc_check.py` (faits, quelques secondes), `test_pc_scope.py` (parcours, QC, vérification arithmétique indépendante, faits).

**Parties sans répétition** (15 questions par partie ; cible 300) : tous les parcours ont **352 à 359** parties par effectif (CM2 435, 3e 371). Par difficulté (3 questions par niveau 1→5) :
264 à 345 : **sous 300 pour Class 1 (264) et CP (288)** ; tous les autres parcours ≥ 300 (CM2 385, 3e 322 inclus).

## Lecture honnête des chiffres
- La grande majorité des questions sont des **variantes numériques ou lexicales** de 27 à 78 modèles par parcours (« Modèles calculés » ci-dessous) : elles tiennent les 300 parties, pas la variété éditoriale.
  Le contenu rédigé (faits) représente 70 à 350 questions par parcours : **c'est le point faible**.
- CP et Class 1 : l'espace de nombres (≤ 100) est petit ; le volume est atteint avec beaucoup de modèles (comptage, suites, calendrier, lettres…) mais les variantes se ressemblent.
- Les numéros 6e/5e/4e et Form 1-3 sont alignés (Form 1 = 6e, etc.) pour les modèles de maths ; le programme anglophone réel peut s'ordonner autrement.

## Thèmes du programme couverts (résumé)
- **Maths** : numération, quatre opérations, tables, division euclidienne, problèmes (FCFA, partages, proportionnalité), fractions, décimaux, mesures, périmètres/aires/volumes, heure et calendrier,
  suites, relatifs, équations et calcul littéral, pourcentages, PGCD/PPCM, premiers, puissances, Pythagore, Thalès, angles, cercle, statistiques, fonctions affines, probabilités simples.
- **Français** : conjugaison (présent, futur, imparfait, passé composé, plus-que-parfait, conditionnel), groupes, accords, pluriels, féminins, homophones, nature et fonction des mots, types de phrases, vocabulaire, orthographe.
- **English** : plurals, articles, past tense, third person, comparison, prepositions, homophones, question words, parts of speech, vocabulary, spelling.
- **Sciences / SVT / PCT**, **histoire-géographie** (Cameroun, Afrique, monde), **ECM / Citizenship**, **informatique / ICT**, **anglais langue étrangère** : faits sourcés par classe (voir tableaux).

## Lacunes connues
- Pas de questions « lecture de document / texte » (le format n'a pas de support) ni de figures (schémas, cartes) : géométrie sans image.
- Faits : histoire-géographie et sciences sont minces au CP-CE2 et en Class 1-2 (70-260) ; l'anglophone est écrit directement en anglais (pas une traduction du francophone) et couvre moins de thèmes.
- Informatique : seulement à partir du CE2 (Class 3). Pas de questions sur la littérature, le dessin, l'EPS, les langues nationales.
- Difficulté : le niveau 1-5 est relatif à la classe ; les faits sont surtout de niveaux 1-3 au primaire.
- Chiffres d'ECM les moins sûrs à confirmer par un enseignant : âge minimal d'un candidat à la présidentielle (35 ans), âge de vote (20 ans), composition du Sénat.

## Détail par parcours et matière (généré par `build_scope.py`)

<!-- BEGIN AUTO -->
### Primaire · CM2 (`cm2`) — 6528 questions, 435 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 4669 | 36 | 4669 | 0 | 850-903-926-972-1018 |
| Nombres | 777 | 7 | 777 | 0 | 128-146-147-169-187 |
| Français | 661 | 8 | 661 | 0 | 80-142-173-143-123 |
| Logique | 159 | 1 | 159 | 0 | 34-47-26-23-29 |
| Anglais | 109 | 0 | 0 | 109 | 0-45-62-2-0 |
| ECM | 48 | 0 | 0 | 48 | 17-21-9-1-0 |
| Informatique | 37 | 0 | 0 | 37 | 1-20-12-4-0 |
| Histoire-géographie | 35 | 0 | 0 | 35 | 27-8-0-0-0 |
| Sciences | 33 | 0 | 0 | 33 | 19-9-5-0-0 |

### Secondaire · 3e (`3e`) — 5571 questions, 371 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 3003 | 21 | 3003 | 0 | 497-541-615-650-700 |
| Physique-Chimie | 1044 | 16 | 1044 | 0 | 201-231-195-204-213 |
| Fonctions | 640 | 3 | 640 | 0 | 128-130-123-132-127 |
| Nombres | 389 | 3 | 389 | 0 | 84-75-77-85-68 |
| Probabilités | 229 | 2 | 229 | 0 | 49-47-44-40-49 |
| Anglais | 108 | 0 | 0 | 108 | 0-0-2-95-11 |
| ECM | 66 | 0 | 0 | 66 | 0-2-25-27-12 |
| Informatique | 36 | 0 | 0 | 36 | 0-0-4-12-20 |
| SVT | 21 | 0 | 0 | 21 | 1-9-11-0-0 |
| Histoire-géographie | 19 | 0 | 0 | 19 | 4-8-6-1-0 |
| Français et anglais | 16 | 0 | 0 | 16 | 4-8-4-0-0 |

### Primaire · CP (`cp`) — 5335 questions, 355 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 4047 | 19 | 4047 | 0 | 546-650-828-956-1067 |
| Français | 1141 | 8 | 1141 | 0 | 216-205-250-234-236 |
| Anglais | 73 | 0 | 0 | 73 | 71-2-0-0-0 |
| Sciences | 44 | 0 | 0 | 44 | 40-4-0-0-0 |
| Histoire-géographie | 15 | 0 | 0 | 15 | 11-2-2-0-0 |
| ECM | 15 | 0 | 0 | 15 | 14-1-0-0-0 |

### Primaire · CE1 (`ce1`) — 5394 questions, 359 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 3801 | 28 | 3801 | 0 | 699-709-751-815-827 |
| Français | 1395 | 15 | 1395 | 0 | 291-270-267-289-278 |
| Anglais | 105 | 0 | 0 | 105 | 23-82-0-0-0 |
| Sciences | 41 | 0 | 0 | 41 | 21-17-3-0-0 |
| Histoire-géographie | 36 | 0 | 0 | 36 | 4-17-13-2-0 |
| ECM | 16 | 0 | 0 | 16 | 9-6-1-0-0 |

### Primaire · CE2 (`ce2`) — 5370 questions, 358 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 3600 | 37 | 3600 | 0 | 693-694-730-740-743 |
| Français | 1508 | 19 | 1508 | 0 | 304-298-294-306-306 |
| Anglais | 119 | 0 | 0 | 119 | 0-70-49-0-0 |
| Sciences | 46 | 0 | 0 | 46 | 5-18-19-4-0 |
| Histoire-géographie | 44 | 0 | 0 | 44 | 0-4-17-20-3 |
| Informatique | 34 | 0 | 0 | 34 | 29-5-0-0-0 |
| ECM | 19 | 0 | 0 | 19 | 1-10-5-3-0 |

### Primaire · CM1 (`cm1`) — 5353 questions, 356 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 3747 | 48 | 3747 | 0 | 718-729-779-763-758 |
| Français | 1274 | 19 | 1274 | 0 | 264-249-258-264-239 |
| Anglais | 124 | 0 | 0 | 124 | 1-103-20-0-0 |
| Sciences | 74 | 0 | 0 | 74 | 1-4-22-35-12 |
| Histoire-géographie | 65 | 0 | 0 | 65 | 0-1-11-28-25 |
| Informatique | 38 | 0 | 0 | 38 | 6-22-9-1-0 |
| ECM | 31 | 0 | 0 | 31 | 0-3-9-10-9 |

### Secondaire · 6e (`6e`) — 5352 questions, 356 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 3676 | 42 | 3676 | 0 | 704-727-748-747-750 |
| Français | 1366 | 18 | 1366 | 0 | 276-274-280-273-263 |
| Anglais | 120 | 0 | 0 | 120 | 2-83-35-0-0 |
| Informatique | 54 | 0 | 0 | 54 | 12-17-21-4-0 |
| SVT | 44 | 0 | 0 | 44 | 11-18-14-1-0 |
| Histoire-géographie | 38 | 0 | 0 | 38 | 6-18-13-1-0 |
| PCT | 37 | 0 | 0 | 37 | 7-21-9-0-0 |
| ECM | 17 | 0 | 0 | 17 | 9-4-2-2-0 |

### Secondaire · 5e (`5e`) — 5374 questions, 358 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 3882 | 52 | 3882 | 0 | 742-775-777-793-795 |
| Français | 1139 | 17 | 1139 | 0 | 237-233-222-227-220 |
| Anglais | 122 | 0 | 0 | 122 | 0-2-118-2-0 |
| SVT | 57 | 0 | 0 | 57 | 0-10-24-22-1 |
| Informatique | 55 | 0 | 0 | 55 | 0-8-18-23-6 |
| Histoire-géographie | 50 | 0 | 0 | 50 | 0-7-19-19-5 |
| PCT | 49 | 0 | 0 | 49 | 1-3-20-24-1 |
| ECM | 20 | 0 | 0 | 20 | 0-6-9-3-2 |

### Secondaire · 4e (`4e`) — 5385 questions, 359 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathématiques | 4013 | 61 | 4013 | 0 | 813-787-799-800-814 |
| Français | 1019 | 17 | 1019 | 0 | 214-212-203-196-194 |
| Anglais | 124 | 0 | 0 | 124 | 0-0-68-50-6 |
| Informatique | 61 | 0 | 0 | 61 | 0-1-13-39-8 |
| SVT | 52 | 0 | 0 | 52 | 0-2-13-23-14 |
| Histoire-géographie | 48 | 0 | 0 | 48 | 1-1-23-17-6 |
| PCT | 46 | 0 | 0 | 46 | 0-2-13-20-11 |
| ECM | 22 | 0 | 0 | 22 | 0-0-12-6-4 |

### Primary (EN) · Class 1 (`class1`) — 5294 questions, 352 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4387 | 19 | 4379 | 8 | 591-720-886-1034-1156 |
| English language | 845 | 8 | 845 | 0 | 145-136-190-190-184 |
| Science | 40 | 0 | 0 | 40 | 38-2-0-0-0 |
| Social Studies | 14 | 0 | 0 | 14 | 12-2-0-0-0 |
| Citizenship | 8 | 0 | 0 | 8 | 8-0-0-0-0 |

### Primary (EN) · Class 2 (`class2`) — 5372 questions, 358 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4361 | 28 | 4354 | 7 | 785-839-859-926-952 |
| English language | 938 | 12 | 938 | 0 | 160-164-186-232-196 |
| Science | 45 | 0 | 0 | 45 | 16-25-4-0-0 |
| Social Studies | 18 | 0 | 0 | 18 | 3-12-3-0-0 |
| Citizenship | 10 | 0 | 0 | 10 | 7-3-0-0-0 |

### Primary (EN) · Class 3 (`class3`) — 5359 questions, 357 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4280 | 37 | 4272 | 8 | 792-817-856-904-911 |
| English language | 976 | 17 | 976 | 0 | 177-187-188-210-214 |
| Science | 45 | 0 | 0 | 45 | 3-8-26-8-0 |
| Social Studies | 38 | 0 | 0 | 38 | 0-22-8-8-0 |
| Citizenship | 10 | 0 | 0 | 10 | 0-6-1-3-0 |
| ICT | 10 | 0 | 0 | 10 | 0-5-4-1-0 |

### Primary (EN) · Class 4 (`class4`) — 5379 questions, 358 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4384 | 48 | 4376 | 8 | 833-849-904-913-885 |
| English language | 907 | 18 | 907 | 0 | 187-180-177-169-194 |
| Science | 46 | 0 | 0 | 46 | 0-4-14-23-5 |
| Social Studies | 21 | 0 | 0 | 21 | 0-0-4-10-7 |
| ICT | 12 | 0 | 0 | 12 | 0-0-9-3-0 |
| Citizenship | 9 | 0 | 0 | 9 | 0-2-3-3-1 |

### Primary (EN) · Class 5 (`class5`) — 5397 questions, 359 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4362 | 46 | 4354 | 8 | 848-867-874-886-887 |
| English language | 916 | 18 | 916 | 0 | 189-190-185-181-171 |
| Science | 52 | 0 | 0 | 52 | 0-0-4-23-25 |
| Social Studies | 40 | 0 | 0 | 40 | 0-0-21-9-10 |
| ICT | 14 | 0 | 0 | 14 | 0-0-2-8-4 |
| Citizenship | 13 | 0 | 0 | 13 | 0-0-2-5-6 |

### Primary (EN) · Class 6 (`class6`) — 5369 questions, 357 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4549 | 44 | 4541 | 8 | 870-882-918-918-961 |
| English language | 688 | 15 | 688 | 0 | 134-134-145-140-135 |
| Science | 54 | 0 | 0 | 54 | 0-0-1-10-43 |
| Social Studies | 51 | 0 | 0 | 51 | 0-0-0-29-22 |
| ICT | 14 | 0 | 0 | 14 | 0-0-0-5-9 |
| Citizenship | 13 | 0 | 0 | 13 | 0-0-2-2-9 |

### Secondary (EN) · Form 1 (`form1`) — 5390 questions, 359 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4554 | 41 | 4547 | 7 | 844-873-923-936-978 |
| English language | 677 | 14 | 677 | 0 | 144-138-133-134-128 |
| Science | 82 | 0 | 0 | 82 | 0-7-36-31-8 |
| Social Studies | 44 | 0 | 0 | 44 | 0-22-12-9-1 |
| ICT | 18 | 0 | 0 | 18 | 0-0-14-3-1 |
| Citizenship | 15 | 0 | 0 | 15 | 0-2-11-2-0 |

### Secondary (EN) · Form 2 (`form2`) — 5358 questions, 357 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4567 | 51 | 4561 | 6 | 885-898-894-932-958 |
| English language | 631 | 14 | 631 | 0 | 132-131-112-132-124 |
| Social Studies | 65 | 0 | 0 | 65 | 0-1-49-4-11 |
| Science | 65 | 0 | 0 | 65 | 0-0-4-28-33 |
| ICT | 16 | 0 | 0 | 16 | 0-0-4-8-4 |
| Citizenship | 14 | 0 | 0 | 14 | 0-1-3-4-6 |

### Secondary (EN) · Form 3 (`form3`) — 5369 questions, 357 parties sans répétition (objectif 300)

| Matière | Questions | Modèles calculés | Variantes calculées | Faits | Difficulté 1-2-3-4-5 |
|---|---:|---:|---:|---:|---|
| Mathematics | 4594 | 60 | 4589 | 5 | 893-913-932-901-955 |
| English language | 594 | 14 | 594 | 0 | 126-126-121-109-112 |
| Science | 88 | 0 | 0 | 88 | 0-0-3-21-64 |
| Social Studies | 54 | 0 | 0 | 54 | 0-0-0-35-19 |
| Citizenship | 20 | 0 | 0 | 20 | 0-0-3-6-11 |
| ICT | 19 | 0 | 0 | 19 | 0-0-0-9-10 |

<!-- END AUTO -->
