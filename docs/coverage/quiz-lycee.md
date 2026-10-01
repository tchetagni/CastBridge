# Quiz — second cycle francophone et GCE (Ordinary / Advanced Level) : couverture

> Généré le 2026-10-01 par `tools/quiz-bank/lycee_report.py` (mêmes générateurs et mêmes contrôles que `quizbank.py build`). **Toutes les questions sont `review`, aucune n'est `approved`** : la relecture humaine est prévue environ 3 mois après la diffusion aux bêta-testeurs ; l'application jouera ce contenu avec la marque « bêta » visible. Les questions de calcul ont leur réponse prouvée par le calcul ; les questions factuelles restent exclues des parties tant qu'un humain ne les a pas approuvées (règle du pipeline, `docs/QUIZ.md` § 6 ter).

**Total : 59 106 questions, 64 parcours** (un parcours = une classe + une matière ; la série A / C / D / E / TI est indiquée dans la catégorie de chaque question, le format de pack ne portant ni série ni matière : la matière est le champ `field`).

## 1. Comment c'est fabriqué
- **Modèles de calcul** (`qb/gen/lycee_*.py`) : chaque modèle tire des nombres au hasard (graine fixe, résultat reproductible), calcule la bonne réponse **et la vérifie** (substitution, fractions exactes, dérivée ou intégrale numérique, énumération complète pour la génétique et les tables de vérité, **exécution Python** pour les algorithmes, équations chimiques équilibrées atome par atome). Les mauvaises réponses sont des erreurs typiques (signe, oubli d'un terme, mauvaise unité). Une vérification qui échoue arrête la génération.
- **Faits** (`qb/facts/lycee_*.py`) : tables écrites par l'assistant avec une fiche source par thème (`dist/sources.json`) ; la fiche dit où comparer, jamais que c'est vérifié.
- **Plafonds** (`qb/gen/lycee_caps.json`, calculés par `lycee_caps.py`) : aucun modèle ne dépasse 8 % d'un parcours ; un parcours qui a trop peu de modèles est limité à environ 500 questions (voir § 4). Les positions A/B/C/D de la bonne réponse sont rééquilibrées (sel par parcours).
- **Difficulté** 1 à 5 relative à la classe : fixée par modèle (`diffs=`), pas tirée au hasard.

## 2. Questions par classe et par matière

Parties sans répétition = nombre de parties de 15 questions que la banque seule permet sans reposer une question (règle de la TV : 300 parties demandent 4 500 questions).

### Second cycle francophone

| Matière | Classe | Questions | calculées | factuelles | modèles | difficulté 1·2·3·4·5 | parties sans répétition |
|---|---|---:|---:|---:|---:|---|---:|
| Mathématiques | 2nde | 4439 | 4439 | 0 | 22 | 611·1859·1584·385·0 | 295 |
| Mathématiques | 1re | 1987 | 1987 | 0 | 18 | 73·239·672·767·236 | 132 |
| Mathématiques | Tle | 936 | 936 | 0 | 21 | 0·0·293·440·203 | 62 |
| Physique | 2nde | 641 | 641 | 0 | 17 | 108·312·150·59·12 | 42 |
| Physique | 1re | 3227 | 3227 | 0 | 34 | 175·527·1269·1245·11 | 215 |
| Physique | Tle | 2508 | 2508 | 0 | 31 | 0·218·993·1225·72 | 167 |
| Chimie | 2nde | 496 | 496 | 0 | 12 | 32·96·249·119·0 | 33 |
| Chimie | 1re | 684 | 684 | 0 | 21 | 5·50·316·240·73 | 45 |
| Chimie | Tle | 500 | 500 | 0 | 18 | 7·7·208·212·66 | 33 |
| SVT | 2nde | 496 | 474 | 22 | 18 | 9·69·327·86·5 | 33 |
| SVT | 1re | 497 | 474 | 23 | 18 | 0·67·342·83·5 | 33 |
| SVT | Tle | 499 | 481 | 18 | 18 | 0·61·352·81·5 | 33 |
| Philosophie | Tle | 93 | 0 | 93 | 0 | 1·19·63·10·0 | 6 |
| Français / littérature | 2nde | 42 | 0 | 42 | 0 | 10·26·6·0·0 | 2 |
| Français / littérature | 1re | 37 | 0 | 37 | 0 | 0·21·16·0·0 | 2 |
| Français / littérature | Tle | 38 | 0 | 38 | 0 | 0·2·36·0·0 | 2 |
| Histoire | 2nde | 36 | 0 | 36 | 0 | 3·19·14·0·0 | 2 |
| Histoire | 1re | 42 | 0 | 42 | 0 | 0·20·21·1·0 | 2 |
| Histoire | Tle | 100 | 0 | 100 | 0 | 4·49·40·7·0 | 6 |
| Géographie | 2nde | 496 | 377 | 119 | 12 | 21·277·162·36·0 | 33 |
| Géographie | 1re | 498 | 395 | 103 | 12 | 10·193·258·37·0 | 33 |
| Géographie | Tle | 499 | 479 | 20 | 12 | 0·234·223·42·0 | 33 |
| ECM | 2nde | 16 | 0 | 16 | 0 | 11·4·1·0·0 | 1 |
| ECM | 1re | 16 | 0 | 16 | 0 | 7·8·1·0·0 | 1 |
| ECM | Tle | 15 | 0 | 15 | 0 | 0·3·9·3·0 | 1 |
| Anglais | 2nde | 500 | 485 | 15 | 10 | 101·155·143·99·2 | 33 |
| Anglais | 1re | 499 | 491 | 8 | 10 | 94·148·157·100·0 | 33 |
| Anglais | Tle | 500 | 495 | 5 | 10 | 98·152·146·101·3 | 33 |
| Économie | 2nde | 1039 | 1023 | 16 | 20 | 15·459·472·93·0 | 69 |
| Économie | 1re | 1039 | 1024 | 15 | 20 | 9·461·472·97·0 | 69 |
| Économie | Tle | 1039 | 1024 | 15 | 20 | 9·454·480·96·0 | 69 |
| Informatique | 2nde | 500 | 488 | 12 | 10 | 85·168·110·77·60 | 33 |
| Informatique | 1re | 1401 | 1389 | 12 | 18 | 107·233·699·261·101 | 93 |
| Informatique | Tle | 974 | 961 | 13 | 18 | 0·66·557·280·71 | 64 |

### GCE Ordinary Level (Form 5) et Advanced Level (Lower / Upper Sixth), en anglais

| Matière | Classe | Questions | calculées | factuelles | modèles | difficulté 1·2·3·4·5 | parties sans répétition |
|---|---|---:|---:|---:|---:|---|---:|
| English Language | Form 5 | 500 | 485 | 15 | 10 | 100·157·140·100·3 | 33 |
| English Language | Lower Sixth | 499 | 491 | 8 | 10 | 96·150·155·96·2 | 33 |
| English Language | Upper Sixth | 500 | 495 | 5 | 10 | 99·155·143·101·2 | 33 |
| Literature | Form 5 | 38 | 0 | 38 | 0 | 10·21·7·0·0 | 2 |
| Literature | Lower Sixth | 39 | 0 | 39 | 0 | 0·2·21·16·0 | 2 |
| Literature | Upper Sixth | 30 | 0 | 30 | 0 | 0·0·14·16·0 | 2 |
| Mathematics | Form 5 | 3390 | 3390 | 0 | 25 | 604·1459·1281·46·0 | 226 |
| Mathematics | Lower Sixth | 3082 | 3082 | 0 | 26 | 0·382·1932·768·0 | 205 |
| Mathematics | Upper Sixth | 3459 | 3459 | 0 | 28 | 0·407·2287·765·0 | 230 |
| Physics | Form 5 | 1937 | 1937 | 0 | 23 | 190·671·880·196·0 | 129 |
| Physics | Lower Sixth | 3214 | 3214 | 0 | 41 | 9·378·1328·1422·77 | 214 |
| Physics | Upper Sixth | 3213 | 3213 | 0 | 41 | 8·380·1336·1403·86 | 214 |
| Chemistry | Form 5 | 495 | 495 | 0 | 19 | 29·87·264·115·0 | 33 |
| Chemistry | Lower Sixth | 1094 | 1094 | 0 | 26 | 4·27·557·413·93 | 72 |
| Chemistry | Upper Sixth | 1095 | 1095 | 0 | 26 | 6·24·546·435·84 | 73 |
| Biology | Form 5 | 496 | 474 | 22 | 18 | 9·70·331·81·5 | 33 |
| Biology | Lower Sixth | 497 | 474 | 23 | 18 | 0·70·345·77·5 | 33 |
| Biology | Upper Sixth | 499 | 481 | 18 | 18 | 0·62·350·82·5 | 33 |
| Geography | Form 5 | 496 | 377 | 119 | 12 | 21·275·163·37·0 | 33 |
| Geography | Lower Sixth | 500 | 407 | 93 | 12 | 0·198·265·37·0 | 33 |
| Geography | Upper Sixth | 499 | 479 | 20 | 12 | 0·230·224·45·0 | 33 |
| History | Form 5 | 42 | 0 | 42 | 0 | 6·18·16·2·0 | 2 |
| History | Lower Sixth | 37 | 0 | 37 | 0 | 0·18·18·1·0 | 2 |
| History | Upper Sixth | 72 | 0 | 72 | 0 | 0·36·34·2·0 | 4 |
| Economics | Form 5 | 1041 | 1025 | 16 | 20 | 15·469·464·93·0 | 69 |
| Economics | Lower Sixth | 1039 | 1024 | 15 | 20 | 9·453·482·95·0 | 69 |
| Economics | Upper Sixth | 1040 | 1025 | 15 | 20 | 9·460·473·98·0 | 69 |
| Computer Science | Form 5 | 492 | 480 | 12 | 14 | 63·133·205·52·39 | 32 |
| Computer Science | Lower Sixth | 1721 | 1709 | 12 | 20 | 0·120·1069·395·137 | 114 |
| Computer Science | Upper Sixth | 1721 | 1708 | 13 | 20 | 0·113·1074·403·131 | 114 |

## 3. Thèmes couverts (catégories) et modèles, par parcours

### Francophone

**Mathématiques — 2nde** (`2nde-maths`) : Géométrie analytique · séries A, C, D, E, TI (770) ; Algèbre · séries A, C, D, E, TI (752) ; Ensembles et intervalles · séries A, C, D, E, TI (666) ; Droites · séries A, C, D, E, TI (666) ; Calcul numérique · séries A, C, D, E, TI (381) ; Fonctions · séries A, C, D, E, TI (340) ; Systèmes · séries A, C, D, E, TI (333) ; Statistiques · séries A, C, D, E, TI (333) ; Calcul dans R · séries A, C, D, E, TI (81) ; Pourcentages · séries A, C, D, E, TI (62) ; Trigonométrie · séries C, D, E, TI (35) ; Géométrie · séries A, C, D, E, TI (20).
  - modèles : `m2-abs-eq` 81, `m2-circle-area` 20, `m2-degrees-radians` 15, `m2-discriminant` 333, `m2-distance` 333, `m2-domain` 7, `m2-eq2-factored` 70, `m2-fraction-add` 333, `m2-function-value` 333, `m2-interval-inter` 333, `m2-linear-system` 333, `m2-midpoint` 333, `m2-parallel-line` 333, `m2-percent-chain` 62, `m2-roots-count` 333, `m2-set-card` 333, `m2-slope` 333, `m2-sqrt-simplify` 48, `m2-square-poly-factor` 16, `m2-trig-sin-from-cos` 20, `m2-vector-collinear` 104, `m2-weighted-mean` 333

**Mathématiques — 1re** (`1re-maths`) : Dérivation · séries C, D, E, TI (424) ; Suites · séries C, D, E, TI (346) ; Second degré · séries C, D, E, TI (298) ; Géométrie analytique · séries C, D, E, TI (298) ; Produit scalaire · séries C, D, E, TI (149) ; Statistiques · séries C, D, E, TI (149) ; Polynômes · séries C, D, E, TI (149) ; Probabilités · séries C, D, E, TI (75) ; Dénombrement · séries C, D, E, TI (58) ; Trigonométrie · séries C, D, E, TI (21) ; Limites · séries C, D, E, TI (20).
  - modèles : `m1-circle-center` 149, `m1-combinations` 58, `m1-critical-point` 126, `m1-deriv-product` 149, `m1-dot-value` 149, `m1-limit-removable` 20, `m1-parabola-extremum` 149, `m1-point-line-distance` 149, `m1-polynomial-root` 149, `m1-proba-union` 75, `m1-seq-arith-find-r` 149, `m1-seq-arith-sum` 149, `m1-seq-geo-term` 48, `m1-stat-variance` 149, `m1-sum-product` 149, `m1-tangent` 149, `m1-trig-equation` 6, `m1-trig-exact` 15

**Mathématiques — Tle** (`tle-maths`) : Géométrie dans l'espace · séries C, D, E (210) ; Probabilités · séries C, D, E (169) ; Arithmétique · séries C, E (140) ; Nombres complexes · séries C, D, E (126) ; Logarithme · séries C, D, E (114) ; Intégration · séries C, D, E (83) ; Suites · séries C, D, E (70) ; Équations différentielles · séries C, D, E (20) ; Limites · séries C, D, E (4).
  - modèles : `mt-area-between` 8, `mt-binomial-half` 56, `mt-complex-arg` 9, `mt-complex-division` 70, `mt-complex-roots` 43, `mt-conditional-probability` 43, `mt-diff-eq` 20, `mt-limit-ref` 4, `mt-ln-derivative` 9, `mt-ln-equation` 35, `mt-ln-express` 70, `mt-modular-inverse` 70, `mt-modular-power` 70, `mt-moivre` 4, `mt-plane-equation` 70, `mt-point-plane-distance` 70, `mt-primitive-uu` 48, `mt-sequence-fixed-point` 70, `mt-space-distance` 70, `mt-total-probability` 70, `mt-volume-revolution` 27

**Physique — 2nde** (`2nde-phys`) : Électricité (138) ; Mécanique · 2nde (110) ; Électricité · 2nde (96) ; Mécanique (96) ; Ondes (81) ; Optique (72) ; Mécanique · 2nde, 1re (48).
  - modèles : `ph-charge` 42, `ph-density` 48, `ph-echo` 9, `ph-electric-power` 48, `ph-energy-cost` 48, `ph-kmh-ms` 14, `ph-lens` 45, `ph-lens-magnification` 21, `ph-lever` 48, `ph-newton2` 48, `ph-ohm` 48, `ph-period-frequency` 24, `ph-refraction` 6, `ph-resistors` 48, `ph-speed` 48, `ph-wave-speed` 48, `ph-weight` 48

**Physique — 1re** (`1re-phys`) : Électricité (783) ; Thermique (630) ; Cinématique · 1re (514) ; Mécanique (251) ; Énergie (241) ; Mécanique · 1re (224) ; Électromagnétisme (200) ; Énergie · 1re (175) ; Optique (66) ; Électricité · 1re (60) ; Mécanique · 2nde, 1re (50) ; Ondes (33).
  - modèles : `ph-archimedes` 18, `ph-boyle` 171, `ph-capacitor` 84, `ph-charge` 42, `ph-circular` 55, `ph-echo` 9, `ph-electric-power` 96, `ph-emf` 60, `ph-free-fall-speed` 7, `ph-heat` 36, `ph-heat-mixing` 242, `ph-hooke` 84, `ph-ideal-gas` 80, `ph-incline` 12, `ph-joule` 145, `ph-kinetic` 60, `ph-laplace` 200, `ph-latent` 5, `ph-lens` 45, `ph-lens-magnification` 21, `ph-momentum` 120, `ph-newton2` 178, `ph-ohm` 216, `ph-period-frequency` 24, `ph-potential-energy` 56, `ph-power-work` 125, `ph-specific-heat` 96, `ph-spring-period` 8, `ph-suvat-brake` 30, `ph-suvat-s` 242, `ph-suvat-v` 242, `ph-transformer` 200, `ph-weight` 50, `ph-work` 168

**Physique — Tle** (`tle-phys`) : Thermique (571) ; Électricité (417) ; Cinématique · 1re (376) ; Énergie (241) ; Électromagnétisme (188) ; Énergie · 1re (175) ; Mécanique · 1re (120) ; Optique ondulatoire · Tle (100) ; Mécanique (73) ; Optique (72) ; Électricité · 1re (60) ; Ondes (48) ; Physique nucléaire · Tle (31) ; Mécanique · Tle (16).
  - modèles : `ph-archimedes` 18, `ph-boyle` 171, `ph-capacitor` 84, `ph-circular` 55, `ph-emf` 60, `ph-free-fall-speed` 7, `ph-heat` 36, `ph-heat-mixing` 188, `ph-ideal-gas` 80, `ph-joule` 145, `ph-kinetic` 60, `ph-laplace` 188, `ph-lc-frequency` 7, `ph-lens` 45, `ph-lens-magnification` 21, `ph-mass-defect` 7, `ph-momentum` 120, `ph-nuclear-decay` 24, `ph-photon` 7, `ph-photon-frequency` 6, `ph-potential-energy` 56, `ph-power-work` 125, `ph-projectile` 16, `ph-refraction` 6, `ph-specific-heat` 96, `ph-suvat-s` 188, `ph-suvat-v` 188, `ph-transformer` 188, `ph-wave-speed` 48, `ph-work` 168, `ph-young` 100

**Chimie — 2nde** (`2nde-chim`) : Solutions (183) ; Quantité de matière (162) ; Atome · 2nde (100) ; Équations chimiques (51).
  - modèles : `ch-balance` 51, `ch-concentration` 61, `ch-dilution` 61, `ch-electron-shells` 15, `ch-gas-volume` 7, `ch-ion-electrons` 12, `ch-mass-percent` 61, `ch-molar-mass` 33, `ch-mole-from-mass` 61, `ch-neutrons` 57, `ch-solution-mass` 61, `ch-valence` 16

**Chimie — 1re** (`1re-chim`) : Solutions (153) ; Quantité de matière (142) ; Stœchiométrie (140) ; Équations chimiques (51) ; Dosages (51) ; Thermochimie (51) ; Chimie organique (45) ; Atome (28) ; Acides et bases (17) ; Cinétique (6).
  - modèles : `ch-alkane-formula` 10, `ch-alkane-mass` 10, `ch-alkane-name` 10, `ch-average-mass` 28, `ch-balance` 51, `ch-combustion` 15, `ch-concentration` 51, `ch-dilution` 51, `ch-gas-volume` 7, `ch-hess` 51, `ch-limiting-reagent` 40, `ch-mass-percent` 51, `ch-molar-mass` 33, `ch-mole-from-mass` 51, `ch-percent-yield` 49, `ch-ph-strong-acid` 12, `ch-ph-strong-base` 5, `ch-reaction-rate` 6, `ch-solution-mass` 51, `ch-stoichiometry` 51, `ch-titration-simple` 51

**Chimie — Tle** (`tle-chim`) : Stœchiométrie (130) ; Électrochimie · Tle (90) ; Équilibres · Tle (45) ; Chimie organique (45) ; Dosages (45) ; Thermochimie (45) ; Acides et bases · Tle (35) ; Atome (28) ; Acides et bases (17) ; Oxydoréduction · Tle (14) ; Cinétique (6).
  - modèles : `ch-alkane-formula` 10, `ch-alkane-mass` 10, `ch-alkane-name` 10, `ch-average-mass` 28, `ch-cell-emf` 45, `ch-combustion` 15, `ch-equilibrium-kc` 45, `ch-faraday` 45, `ch-henderson` 35, `ch-hess` 45, `ch-limiting-reagent` 40, `ch-oxidation-number` 14, `ch-percent-yield` 45, `ch-ph-strong-acid` 12, `ch-ph-strong-base` 5, `ch-reaction-rate` 6, `ch-stoichiometry` 45, `ch-titration-simple` 45

**SVT — 2nde** (`2nde-svt`) : Écologie (154) ; Cellule (99) ; Physiologie (87) ; Génétique moléculaire (62) ; Croissance (46) ; SVT (22) ; Génétique (21) ; Génétique des populations (5).
  - modèles : `bio-bmi` 35, `bio-cardiac-output` 46, `bio-codons` 8, `bio-dna-chargaff` 8, `bio-doubling` 46, `bio-genetic-probability` 6, `bio-hardy-weinberg` 5, `bio-heart-rate` 6, `bio-hydrogen-bonds` 46, `bio-lincoln` 46, `bio-magnification` 46, `bio-mendel-dihybrid` 10, `bio-mendel-monohybrid` 5, `bio-osmosis` 46, `bio-pyramid-biomass` 16, `bio-quadrat` 46, `bio-surface-volume` 7, `bio-trophic` 46

**SVT — 1re** (`1re-svt`) : Écologie (154) ; Cellule (99) ; Physiologie (87) ; Génétique moléculaire (62) ; Croissance (46) ; SVT (23) ; Génétique (21) ; Génétique des populations (5).
  - modèles : `bio-bmi` 35, `bio-cardiac-output` 46, `bio-codons` 8, `bio-dna-chargaff` 8, `bio-doubling` 46, `bio-genetic-probability` 6, `bio-hardy-weinberg` 5, `bio-heart-rate` 6, `bio-hydrogen-bonds` 46, `bio-lincoln` 46, `bio-magnification` 46, `bio-mendel-dihybrid` 10, `bio-mendel-monohybrid` 5, `bio-osmosis` 46, `bio-pyramid-biomass` 16, `bio-quadrat` 46, `bio-surface-volume` 7, `bio-trophic` 46

**SVT — Tle** (`tle-svt`) : Écologie (157) ; Cellule (100) ; Physiologie (88) ; Génétique moléculaire (63) ; Croissance (47) ; Génétique (21) ; SVT (18) ; Génétique des populations (5).
  - modèles : `bio-bmi` 35, `bio-cardiac-output` 47, `bio-codons` 8, `bio-dna-chargaff` 8, `bio-doubling` 47, `bio-genetic-probability` 6, `bio-hardy-weinberg` 5, `bio-heart-rate` 6, `bio-hydrogen-bonds` 47, `bio-lincoln` 47, `bio-magnification` 46, `bio-mendel-dihybrid` 10, `bio-mendel-monohybrid` 5, `bio-osmosis` 47, `bio-pyramid-biomass` 16, `bio-quadrat` 47, `bio-surface-volume` 7, `bio-trophic` 47

**Philosophie — Tle** (`tle-philo`) : Philosophie (93).

**Français / littérature — 2nde** (`2nde-fran`) : Français et littérature (25) ; Littérature (17).

**Français / littérature — 1re** (`1re-fran`) : Littérature (22) ; Français et littérature (15).

**Français / littérature — Tle** (`tle-fran`) : Littérature (26) ; Français et littérature (12).

**Histoire — 2nde** (`2nde-hist`) : Histoire (36).

**Histoire — 1re** (`1re-hist`) : Histoire (42).

**Histoire — Tle** (`tle-hist`) : Histoire (100).

**Géographie — 2nde** (`2nde-geo`) : Population (183) ; Cartographie (89) ; Capitales (75) ; Climats (70) ; Relief (35) ; Géographie (34) ; Cameroun (10).
  - modèles : `geo-climate-mean` 41, `geo-density` 41, `geo-dependency` 20, `geo-lapse-rate` 29, `geo-latitude-distance` 7, `geo-literacy` 5, `geo-map-scale` 41, `geo-natural-increase` 41, `geo-population-change` 41, `geo-slope` 35, `geo-time-longitude` 41, `geo-urbanisation` 35

**Géographie — 1re** (`1re-geo`) : Population (192) ; Cartographie (94) ; Capitales (75) ; Climats (74) ; Relief (35) ; Géographie (18) ; Cameroun (10).
  - modèles : `geo-climate-mean` 45, `geo-density` 42, `geo-dependency` 20, `geo-lapse-rate` 29, `geo-latitude-distance` 7, `geo-literacy` 5, `geo-map-scale` 42, `geo-natural-increase` 45, `geo-population-change` 45, `geo-slope` 35, `geo-time-longitude` 45, `geo-urbanisation` 35

**Géographie — Tle** (`tle-geo`) : Population (220) ; Cartographie (122) ; Climats (102) ; Relief (35) ; Géographie (20).
  - modèles : `geo-climate-mean` 73, `geo-density` 42, `geo-dependency` 20, `geo-lapse-rate` 29, `geo-latitude-distance` 7, `geo-literacy` 5, `geo-map-scale` 42, `geo-natural-increase` 45, `geo-population-change` 73, `geo-slope` 35, `geo-time-longitude` 73, `geo-urbanisation` 35

**ECM — 2nde** (`2nde-ecm`) : Citoyenneté et ECM (16).

**ECM — 1re** (`1re-ecm`) : Citoyenneté et ECM (16).

**ECM — Tle** (`tle-ecm`) : Citoyenneté et ECM (15).

**Anglais — 2nde** (`2nde-angl`) : Tenses (294) ; Verbs (91) ; Adjectives (30) ; Nouns (27) ; Prepositions (22) ; Anglais (15) ; Articles (10) ; Conditionals (6) ; Reported speech (5).
  - modèles : `en-articles` 10, `en-comparative` 30, `en-conditionals` 6, `en-irregular-participle` 31, `en-irregular-past` 60, `en-past-simple-sentence` 147, `en-plural` 27, `en-prepositions` 22, `en-present-perfect` 147, `en-reported-speech` 5

**Anglais — 1re** (`1re-angl`) : Tenses (300) ; Verbs (91) ; Adjectives (30) ; Nouns (27) ; Prepositions (22) ; Articles (10) ; Anglais (8) ; Conditionals (6) ; Reported speech (5).
  - modèles : `en-articles` 10, `en-comparative` 30, `en-conditionals` 6, `en-irregular-participle` 31, `en-irregular-past` 60, `en-past-simple-sentence` 150, `en-plural` 27, `en-prepositions` 22, `en-present-perfect` 150, `en-reported-speech` 5

**Anglais — Tle** (`tle-angl`) : Tenses (304) ; Verbs (91) ; Adjectives (30) ; Nouns (27) ; Prepositions (22) ; Articles (10) ; Conditionals (6) ; Reported speech (5) ; Anglais (5).
  - modèles : `en-articles` 10, `en-comparative` 30, `en-conditionals` 6, `en-irregular-participle` 31, `en-irregular-past` 60, `en-past-simple-sentence` 152, `en-plural` 27, `en-prepositions` 22, `en-present-perfect` 152, `en-reported-speech` 5

**Économie — 2nde** (`2nde-eco`) : Entreprise (296) ; Prix et inflation (142) ; Macroéconomie (139) ; Microéconomie (110) ; Croissance et production (90) ; Monnaie (87) ; Échanges internationaux (84) ; Emploi (66) ; Économie (16) ; Fiscalité (9).
  - modèles : `eco-average-cost` 78, `eco-breakeven` 62, `eco-equilibrium` 78, `eco-exchange` 9, `eco-gdp-expenditure` 78, `eco-gdp-per-capita` 36, `eco-growth` 54, `eco-index-number` 46, `eco-inflation` 54, `eco-marginal-cost` 78, `eco-multiplier` 25, `eco-opportunity-cost` 6, `eco-price-elasticity` 32, `eco-profit` 78, `eco-real-value` 42, `eco-savings-rate` 36, `eco-simple-interest` 78, `eco-trade-balance` 78, `eco-unemployment` 66, `eco-vat` 9

**Économie — 1re** (`1re-eco`) : Entreprise (297) ; Prix et inflation (142) ; Macroéconomie (139) ; Microéconomie (110) ; Croissance et production (90) ; Monnaie (87) ; Échanges internationaux (84) ; Emploi (66) ; Économie (15) ; Fiscalité (9).
  - modèles : `eco-average-cost` 78, `eco-breakeven` 63, `eco-equilibrium` 78, `eco-exchange` 9, `eco-gdp-expenditure` 78, `eco-gdp-per-capita` 36, `eco-growth` 54, `eco-index-number` 46, `eco-inflation` 54, `eco-marginal-cost` 78, `eco-multiplier` 25, `eco-opportunity-cost` 6, `eco-price-elasticity` 32, `eco-profit` 78, `eco-real-value` 42, `eco-savings-rate` 36, `eco-simple-interest` 78, `eco-trade-balance` 78, `eco-unemployment` 66, `eco-vat` 9

**Économie — Tle** (`tle-eco`) : Entreprise (297) ; Prix et inflation (142) ; Macroéconomie (139) ; Microéconomie (110) ; Croissance et production (90) ; Monnaie (87) ; Échanges internationaux (84) ; Emploi (66) ; Économie (15) ; Fiscalité (9).
  - modèles : `eco-average-cost` 78, `eco-breakeven` 63, `eco-equilibrium` 78, `eco-exchange` 9, `eco-gdp-expenditure` 78, `eco-gdp-per-capita` 36, `eco-growth` 54, `eco-index-number` 46, `eco-inflation` 54, `eco-marginal-cost` 78, `eco-multiplier` 25, `eco-opportunity-cost` 6, `eco-price-elasticity` 32, `eco-profit` 78, `eco-real-value` 42, `eco-savings-rate` 36, `eco-simple-interest` 78, `eco-trade-balance` 78, `eco-unemployment` 66, `eco-vat` 9

**Informatique — 2nde** (`2nde-info`) : Représentation des nombres (236) ; Réseaux (115) ; Programmation (59) ; Représentation des données (42) ; Unités (36) ; Informatique (12).
  - modèles : `inf-ascii` 34, `inf-bin-to-dec` 59, `inf-binary-logic-ops` 59, `inf-dec-to-bin` 59, `inf-download` 56, `inf-list-index` 59, `inf-storage-count` 8, `inf-subnet-address` 59, `inf-two-complement-value` 59, `inf-units` 36

**Informatique — 1re** (`1re-info`) : Représentation des nombres (735) ; Algorithmique (302) ; Réseaux (161) ; Programmation (105) ; Représentation des données (74) ; Logique (12) ; Informatique (12).
  - modèles : `inf-ascii` 34, `inf-bin-add` 105, `inf-bin-to-dec` 105, `inf-binary-logic-ops` 105, `inf-binary-search` 12, `inf-caesar` 105, `inf-dec-to-bin` 105, `inf-download` 56, `inf-hex` 105, `inf-image-size` 32, `inf-list-index` 105, `inf-logic-eval` 12, `inf-nested-loops` 80, `inf-storage-count` 8, `inf-subnet-address` 105, `inf-trace-loop` 105, `inf-two-complement-value` 105, `inf-twos-complement` 105

**Informatique — Tle** (`tle-info`) : Représentation des nombres (292) ; Algorithmique (231) ; Programmation (146) ; Représentation des données (113) ; Bases de données · Tle (73) ; Réseaux (73) ; Réseaux · Tle (21) ; Informatique (13) ; Logique (12).
  - modèles : `inf-binary-logic-ops` 73, `inf-binary-search` 12, `inf-caesar` 73, `inf-hex` 73, `inf-image-size` 32, `inf-ipv4-hosts` 11, `inf-list-index` 73, `inf-logic-eval` 12, `inf-mask-dotted` 10, `inf-nested-loops` 73, `inf-python-expr` 73, `inf-sound-size` 73, `inf-sql-aggregate` 73, `inf-storage-count` 8, `inf-subnet-address` 73, `inf-trace-loop` 73, `inf-two-complement-value` 73, `inf-twos-complement` 73

### GCE (anglais)

**English Language — Form 5** (`f5-lang`) : Tenses (294) ; Verbs (91) ; Adjectives (30) ; Nouns (27) ; Prepositions (22) ; English Language (15) ; Articles (10) ; Conditionals (6) ; Reported speech (5).
  - modèles : `en-articles` 10, `en-comparative` 30, `en-conditionals` 6, `en-irregular-participle` 31, `en-irregular-past` 60, `en-past-simple-sentence` 147, `en-plural` 27, `en-prepositions` 22, `en-present-perfect` 147, `en-reported-speech` 5

**English Language — Lower Sixth** (`l6-lang`) : Tenses (300) ; Verbs (91) ; Adjectives (30) ; Nouns (27) ; Prepositions (22) ; Articles (10) ; English Language (8) ; Conditionals (6) ; Reported speech (5).
  - modèles : `en-articles` 10, `en-comparative` 30, `en-conditionals` 6, `en-irregular-participle` 31, `en-irregular-past` 60, `en-past-simple-sentence` 150, `en-plural` 27, `en-prepositions` 22, `en-present-perfect` 150, `en-reported-speech` 5

**English Language — Upper Sixth** (`u6-lang`) : Tenses (304) ; Verbs (91) ; Adjectives (30) ; Nouns (27) ; Prepositions (22) ; Articles (10) ; Conditionals (6) ; Reported speech (5) ; English Language (5).
  - modèles : `en-articles` 10, `en-comparative` 30, `en-conditionals` 6, `en-irregular-participle` 31, `en-irregular-past` 60, `en-past-simple-sentence` 152, `en-plural` 27, `en-prepositions` 22, `en-present-perfect` 152, `en-reported-speech` 5

**Literature — Form 5** (`f5-lit`) : Literature (38).

**Literature — Lower Sixth** (`l6-lit`) : Literature (39).

**Literature — Upper Sixth** (`u6-lit`) : Literature (30).

**Mathematics — Form 5** (`f5-math`) : Algebra (804) ; Indices and standard form (322) ; Mensuration (315) ; Statistics (250) ; Probability (250) ; Sets (250) ; Coordinate geometry (250) ; Matrices (250) ; Number (250) ; Ratio and proportion (156) ; Variation (120) ; Arithmetic (78) ; Percentages (30) ; Geometry (25).
  - modèles : `en-m-arc-length` 35, `en-m-average-speed` 5, `en-m-bearing-back` 13, `en-m-bearings-angles` 12, `en-m-compound-interest` 30, `en-m-cylinder` 30, `en-m-direct-variation` 120, `en-m-expand-brackets` 250, `en-m-fractions` 250, `en-m-indices` 250, `en-m-linear-equation` 250, `en-m-linear-gradient` 250, `en-m-log-basic` 20, `en-m-matrix-det` 250, `en-m-mean-median-mode` 250, `en-m-percent-profit` 48, `en-m-probability-bag` 250, `en-m-pythag-trig` 15, `en-m-quadratic-factor` 54, `en-m-ratio-share` 156, `en-m-reverse-percentage` 30, `en-m-sets` 250, `en-m-simultaneous` 250, `en-m-standard-form` 72, `en-m-trapezium-area` 250

**Mathematics — Lower Sixth** (`l6-math`) : Differentiation (818) ; Probability and statistics (524) ; Sequences and series (385) ; Integration (261) ; Logarithms (259) ; Polynomials (231) ; Vectors (231) ; Trigonometry (172) ; Binomial expansion (93) ; Quadratics (60) ; Functions (48).
  - modèles : `en-m-ap-sum` 231, `en-m-binomial-coefficient` 93, `en-m-binomial-prob` 112, `en-m-chain-rule` 150, `en-m-complete-square` 52, `en-m-cosine-rule` 6, `en-m-differentiate` 231, `en-m-discriminant-k` 8, `en-m-expectation` 231, `en-m-exponential-equation` 46, `en-m-geometric-term` 100, `en-m-gp-infinity` 54, `en-m-gradient-at-point` 231, `en-m-integrate-definite` 231, `en-m-integrate-indefinite` 30, `en-m-log-equation` 135, `en-m-log-laws` 78, `en-m-modulus-equation` 48, `en-m-poisson-zero` 6, `en-m-remainder-theorem` 231, `en-m-sector-radians` 138, `en-m-sine-rule` 12, `en-m-stationary` 206, `en-m-trig-identity` 16, `en-m-vector-dot` 231, `en-m-z-score` 175

**Mathematics — Upper Sixth** (`u6-math`) : Differentiation (856) ; Probability and statistics (532) ; Sequences and series (404) ; Integration (280) ; Logarithms (259) ; Complex numbers (255) ; Polynomials (250) ; Vectors (250) ; Trigonometry (172) ; Binomial expansion (93) ; Quadratics (60) ; Functions (48).
  - modèles : `en-m-ap-sum` 250, `en-m-binomial-coefficient` 93, `en-m-binomial-prob` 112, `en-m-chain-rule` 150, `en-m-complete-square` 52, `en-m-complex-modulus` 5, `en-m-complex-multiply` 250, `en-m-cosine-rule` 6, `en-m-differentiate` 250, `en-m-discriminant-k` 8, `en-m-expectation` 239, `en-m-exponential-equation` 46, `en-m-geometric-term` 100, `en-m-gp-infinity` 54, `en-m-gradient-at-point` 250, `en-m-integrate-definite` 250, `en-m-integrate-indefinite` 30, `en-m-log-equation` 135, `en-m-log-laws` 78, `en-m-modulus-equation` 48, `en-m-poisson-zero` 6, `en-m-remainder-theorem` 250, `en-m-sector-radians` 138, `en-m-sine-rule` 12, `en-m-stationary` 206, `en-m-trig-identity` 16, `en-m-vector-dot` 250, `en-m-z-score` 175

**Physics — Form 5** (`f5-phys`) : Electricity (701) ; Mechanics (596) ; Energy (282) ; Thermal physics (277) ; Waves (81).
  - modèles : `ph-archimedes` 18, `ph-charge` 42, `ph-echo` 9, `ph-efficiency` 41, `ph-electric-power` 96, `ph-energy-cost` 145, `ph-heat` 36, `ph-heat-mixing` 145, `ph-joule` 145, `ph-kinetic` 60, `ph-kmh-ms` 14, `ph-lever` 80, `ph-moments` 145, `ph-newton2` 145, `ph-ohm` 145, `ph-period-frequency` 24, `ph-potential-energy` 56, `ph-power-work` 125, `ph-pressure` 49, `ph-resistors` 128, `ph-specific-heat` 96, `ph-speed` 145, `ph-wave-speed` 48

**Physics — Lower Sixth** (`l6-phys`) : Thermal physics (629) ; Electricity (538) ; Kinematics (512) ; Mechanics (479) ; Energy (457) ; Electromagnetism (200) ; Nuclear physics (151) ; Wave optics (100) ; Optics (72) ; Modern physics (43) ; Waves (33).
  - modèles : `ph-archimedes` 18, `ph-boyle` 171, `ph-capacitor` 84, `ph-charge` 42, `ph-circular` 55, `ph-decay-halflife` 120, `ph-echo` 9, `ph-efficiency` 41, `ph-emf` 60, `ph-free-fall-speed` 7, `ph-heat` 36, `ph-heat-mixing` 241, `ph-hooke` 84, `ph-ideal-gas` 80, `ph-joule` 145, `ph-kinetic` 60, `ph-laplace` 200, `ph-latent` 5, `ph-lc-frequency` 7, `ph-lens` 45, `ph-lens-magnification` 21, `ph-mass-defect` 7, `ph-momentum` 120, `ph-newton2` 178, `ph-nuclear-decay` 24, `ph-period-frequency` 24, `ph-photoelectric` 30, `ph-photon` 7, `ph-photon-frequency` 6, `ph-potential-energy` 56, `ph-power-work` 125, `ph-projectile` 16, `ph-refraction` 6, `ph-specific-heat` 96, `ph-spring-period` 8, `ph-suvat-brake` 30, `ph-suvat-s` 241, `ph-suvat-v` 241, `ph-transformer` 200, `ph-work` 168, `ph-young` 100

**Physics — Upper Sixth** (`u6-phys`) : Thermal physics (629) ; Electricity (538) ; Kinematics (512) ; Mechanics (479) ; Energy (456) ; Electromagnetism (200) ; Nuclear physics (151) ; Wave optics (100) ; Optics (72) ; Modern physics (43) ; Waves (33).
  - modèles : `ph-archimedes` 18, `ph-boyle` 171, `ph-capacitor` 84, `ph-charge` 42, `ph-circular` 55, `ph-decay-halflife` 120, `ph-echo` 9, `ph-efficiency` 41, `ph-emf` 60, `ph-free-fall-speed` 7, `ph-heat` 36, `ph-heat-mixing` 241, `ph-hooke` 84, `ph-ideal-gas` 80, `ph-joule` 145, `ph-kinetic` 60, `ph-laplace` 200, `ph-latent` 5, `ph-lc-frequency` 7, `ph-lens` 45, `ph-lens-magnification` 21, `ph-mass-defect` 7, `ph-momentum` 120, `ph-newton2` 178, `ph-nuclear-decay` 24, `ph-period-frequency` 24, `ph-photoelectric` 30, `ph-photon` 7, `ph-photon-frequency` 6, `ph-potential-energy` 56, `ph-power-work` 125, `ph-projectile` 16, `ph-refraction` 6, `ph-specific-heat` 96, `ph-spring-period` 8, `ph-suvat-brake` 30, `ph-suvat-s` 241, `ph-suvat-v` 241, `ph-transformer` 200, `ph-work` 167, `ph-young` 100

**Chemistry — Form 5** (`f5-chem`) : Mole calculations (148) ; Solutions (126) ; Atomic structure (85) ; Chemical equations (42) ; Titration (42) ; Organic chemistry (35) ; Acids and bases (17).
  - modèles : `ch-alkane-formula` 10, `ch-alkane-name` 10, `ch-balance` 42, `ch-combustion` 15, `ch-concentration` 42, `ch-dilution` 42, `ch-electron-shells` 15, `ch-empirical` 24, `ch-gas-volume` 7, `ch-ion-electrons` 12, `ch-molar-mass` 33, `ch-mole-from-mass` 42, `ch-neutrons` 42, `ch-percent-mass` 42, `ch-ph-strong-acid` 12, `ch-ph-strong-base` 5, `ch-solution-mass` 42, `ch-titration-simple` 42, `ch-valence` 16

**Chemistry — Lower Sixth** (`l6-chem`) : Mole calculations (228) ; Solutions (164) ; Reacting masses (155) ; Electrochemistry (116) ; Equilibria (82) ; Energetics (82) ; Titration (72) ; Acids and bases (52) ; Chemical equations (50) ; Organic chemistry (45) ; Atomic structure (28) ; Redox (14) ; Kinetics (6).
  - modèles : `ch-alkane-formula` 10, `ch-alkane-mass` 10, `ch-alkane-name` 10, `ch-average-mass` 28, `ch-balance` 50, `ch-cell-emf` 56, `ch-combustion` 15, `ch-concentration` 82, `ch-dilution` 82, `ch-empirical` 24, `ch-equilibrium-kc` 82, `ch-faraday` 60, `ch-gas-volume` 7, `ch-henderson` 35, `ch-hess` 82, `ch-limiting-reagent` 40, `ch-molar-mass` 33, `ch-mole-from-mass` 82, `ch-oxidation-number` 14, `ch-percent-mass` 82, `ch-percent-yield` 49, `ch-ph-strong-acid` 12, `ch-ph-strong-base` 5, `ch-reaction-rate` 6, `ch-stoichiometry` 66, `ch-titration-simple` 72

**Chemistry — Upper Sixth** (`u6-chem`) : Mole calculations (228) ; Solutions (164) ; Reacting masses (155) ; Electrochemistry (116) ; Equilibria (82) ; Energetics (82) ; Titration (72) ; Acids and bases (52) ; Chemical equations (51) ; Organic chemistry (45) ; Atomic structure (28) ; Redox (14) ; Kinetics (6).
  - modèles : `ch-alkane-formula` 10, `ch-alkane-mass` 10, `ch-alkane-name` 10, `ch-average-mass` 28, `ch-balance` 51, `ch-cell-emf` 56, `ch-combustion` 15, `ch-concentration` 82, `ch-dilution` 82, `ch-empirical` 24, `ch-equilibrium-kc` 82, `ch-faraday` 60, `ch-gas-volume` 7, `ch-henderson` 35, `ch-hess` 82, `ch-limiting-reagent` 40, `ch-molar-mass` 33, `ch-mole-from-mass` 82, `ch-oxidation-number` 14, `ch-percent-mass` 82, `ch-percent-yield` 49, `ch-ph-strong-acid` 12, `ch-ph-strong-base` 5, `ch-reaction-rate` 6, `ch-stoichiometry` 66, `ch-titration-simple` 72

**Biology — Form 5** (`f5-bio`) : Ecology (154) ; Cell biology (99) ; Physiology (87) ; Molecular genetics (62) ; Growth (46) ; Biology (22) ; Genetics (21) ; Population genetics (5).
  - modèles : `bio-bmi` 35, `bio-cardiac-output` 46, `bio-codons` 8, `bio-dna-chargaff` 8, `bio-doubling` 46, `bio-genetic-probability` 6, `bio-hardy-weinberg` 5, `bio-heart-rate` 6, `bio-hydrogen-bonds` 46, `bio-lincoln` 46, `bio-magnification` 46, `bio-mendel-dihybrid` 10, `bio-mendel-monohybrid` 5, `bio-osmosis` 46, `bio-pyramid-biomass` 16, `bio-quadrat` 46, `bio-surface-volume` 7, `bio-trophic` 46

**Biology — Lower Sixth** (`l6-bio`) : Ecology (154) ; Cell biology (99) ; Physiology (87) ; Molecular genetics (62) ; Growth (46) ; Biology (23) ; Genetics (21) ; Population genetics (5).
  - modèles : `bio-bmi` 35, `bio-cardiac-output` 46, `bio-codons` 8, `bio-dna-chargaff` 8, `bio-doubling` 46, `bio-genetic-probability` 6, `bio-hardy-weinberg` 5, `bio-heart-rate` 6, `bio-hydrogen-bonds` 46, `bio-lincoln` 46, `bio-magnification` 46, `bio-mendel-dihybrid` 10, `bio-mendel-monohybrid` 5, `bio-osmosis` 46, `bio-pyramid-biomass` 16, `bio-quadrat` 46, `bio-surface-volume` 7, `bio-trophic` 46

**Biology — Upper Sixth** (`u6-bio`) : Ecology (157) ; Cell biology (100) ; Physiology (88) ; Molecular genetics (63) ; Growth (47) ; Genetics (21) ; Biology (18) ; Population genetics (5).
  - modèles : `bio-bmi` 35, `bio-cardiac-output` 47, `bio-codons` 8, `bio-dna-chargaff` 8, `bio-doubling` 47, `bio-genetic-probability` 6, `bio-hardy-weinberg` 5, `bio-heart-rate` 6, `bio-hydrogen-bonds` 47, `bio-lincoln` 47, `bio-magnification` 46, `bio-mendel-dihybrid` 10, `bio-mendel-monohybrid` 5, `bio-osmosis` 47, `bio-pyramid-biomass` 16, `bio-quadrat` 47, `bio-surface-volume` 7, `bio-trophic` 47

**Geography — Form 5** (`f5-geo`) : Population (183) ; Map skills (89) ; Capitals (75) ; Climate (70) ; Relief (35) ; Geography (34) ; Cameroon (10).
  - modèles : `geo-climate-mean` 41, `geo-density` 41, `geo-dependency` 20, `geo-lapse-rate` 29, `geo-latitude-distance` 7, `geo-literacy` 5, `geo-map-scale` 41, `geo-natural-increase` 41, `geo-population-change` 41, `geo-slope` 35, `geo-time-longitude` 41, `geo-urbanisation` 35

**Geography — Lower Sixth** (`l6-geo`) : Population (196) ; Map skills (98) ; Climate (78) ; Capitals (75) ; Relief (35) ; Geography (18).
  - modèles : `geo-climate-mean` 49, `geo-density` 42, `geo-dependency` 20, `geo-lapse-rate` 29, `geo-latitude-distance` 7, `geo-literacy` 5, `geo-map-scale` 42, `geo-natural-increase` 45, `geo-population-change` 49, `geo-slope` 35, `geo-time-longitude` 49, `geo-urbanisation` 35

**Geography — Upper Sixth** (`u6-geo`) : Population (220) ; Map skills (122) ; Climate (102) ; Relief (35) ; Geography (20).
  - modèles : `geo-climate-mean` 73, `geo-density` 42, `geo-dependency` 20, `geo-lapse-rate` 29, `geo-latitude-distance` 7, `geo-literacy` 5, `geo-map-scale` 42, `geo-natural-increase` 45, `geo-population-change` 73, `geo-slope` 35, `geo-time-longitude` 73, `geo-urbanisation` 35

**History — Form 5** (`f5-hist`) : History (42).

**History — Lower Sixth** (`l6-hist`) : History (37).

**History — Upper Sixth** (`u6-hist`) : History (72).

**Economics — Form 5** (`f5-econ`) : The firm (298) ; Inflation (142) ; Macroeconomics (139) ; Microeconomics (110) ; Growth and output (90) ; Money (87) ; International trade (84) ; Employment (66) ; Economics (16) ; Taxation (9).
  - modèles : `eco-average-cost` 78, `eco-breakeven` 64, `eco-equilibrium` 78, `eco-exchange` 9, `eco-gdp-expenditure` 78, `eco-gdp-per-capita` 36, `eco-growth` 54, `eco-index-number` 46, `eco-inflation` 54, `eco-marginal-cost` 78, `eco-multiplier` 25, `eco-opportunity-cost` 6, `eco-price-elasticity` 32, `eco-profit` 78, `eco-real-value` 42, `eco-savings-rate` 36, `eco-simple-interest` 78, `eco-trade-balance` 78, `eco-unemployment` 66, `eco-vat` 9

**Economics — Lower Sixth** (`l6-econ`) : The firm (297) ; Inflation (142) ; Macroeconomics (139) ; Microeconomics (110) ; Growth and output (90) ; Money (87) ; International trade (84) ; Employment (66) ; Economics (15) ; Taxation (9).
  - modèles : `eco-average-cost` 78, `eco-breakeven` 63, `eco-equilibrium` 78, `eco-exchange` 9, `eco-gdp-expenditure` 78, `eco-gdp-per-capita` 36, `eco-growth` 54, `eco-index-number` 46, `eco-inflation` 54, `eco-marginal-cost` 78, `eco-multiplier` 25, `eco-opportunity-cost` 6, `eco-price-elasticity` 32, `eco-profit` 78, `eco-real-value` 42, `eco-savings-rate` 36, `eco-simple-interest` 78, `eco-trade-balance` 78, `eco-unemployment` 66, `eco-vat` 9

**Economics — Upper Sixth** (`u6-econ`) : The firm (298) ; Inflation (142) ; Macroeconomics (139) ; Microeconomics (110) ; Growth and output (90) ; Money (87) ; International trade (84) ; Employment (66) ; Economics (15) ; Taxation (9).
  - modèles : `eco-average-cost` 78, `eco-breakeven` 64, `eco-equilibrium` 78, `eco-exchange` 9, `eco-gdp-expenditure` 78, `eco-gdp-per-capita` 36, `eco-growth` 54, `eco-index-number` 46, `eco-inflation` 54, `eco-marginal-cost` 78, `eco-multiplier` 25, `eco-opportunity-cost` 6, `eco-price-elasticity` 32, `eco-profit` 78, `eco-real-value` 42, `eco-savings-rate` 36, `eco-simple-interest` 78, `eco-trade-balance` 78, `eco-unemployment` 66, `eco-vat` 9

**Computer Science — Form 5** (`f5-cs`) : Data representation (237) ; Networks (78) ; Algorithms (78) ; Programming (39) ; Units (36) ; Logic (12) ; Computer Science (12).
  - modèles : `inf-ascii` 34, `inf-bin-add` 39, `inf-bin-to-dec` 39, `inf-binary-logic-ops` 39, `inf-caesar` 39, `inf-dec-to-bin` 39, `inf-download` 39, `inf-list-index` 39, `inf-logic-eval` 12, `inf-storage-count` 8, `inf-subnet-address` 39, `inf-trace-loop` 39, `inf-two-complement-value` 39, `inf-units` 36

**Computer Science — Lower Sixth** (`l6-cs`) : Data representation (754) ; Algorithms (350) ; Programming (258) ; Networks (206) ; Databases (129) ; Logic (12) ; Computer Science (12).
  - modèles : `inf-bin-add` 129, `inf-binary-logic-ops` 129, `inf-binary-search` 12, `inf-caesar` 129, `inf-download` 56, `inf-hex` 129, `inf-image-size` 32, `inf-ipv4-hosts` 11, `inf-list-index` 129, `inf-logic-eval` 12, `inf-mask-dotted` 10, `inf-nested-loops` 80, `inf-python-expr` 129, `inf-sound-size` 80, `inf-sql-aggregate` 129, `inf-storage-count` 8, `inf-subnet-address` 129, `inf-trace-loop` 129, `inf-two-complement-value` 127, `inf-twos-complement` 120

**Computer Science — Upper Sixth** (`u6-cs`) : Data representation (754) ; Algorithms (350) ; Programming (258) ; Networks (206) ; Databases (129) ; Computer Science (13) ; Logic (11).
  - modèles : `inf-bin-add` 129, `inf-binary-logic-ops` 129, `inf-binary-search` 12, `inf-caesar` 129, `inf-download` 56, `inf-hex` 129, `inf-image-size` 32, `inf-ipv4-hosts` 11, `inf-list-index` 129, `inf-logic-eval` 11, `inf-mask-dotted` 10, `inf-nested-loops` 80, `inf-python-expr` 129, `inf-sound-size` 80, `inf-sql-aggregate` 129, `inf-storage-count` 8, `inf-subnet-address` 129, `inf-trace-loop` 129, `inf-two-complement-value` 127, `inf-twos-complement` 120

## 4. Lacunes connues (à lire avant de se fier aux chiffres)

- **Volume** : le tirage « 300 parties sans répétition » (4 500 questions par parcours) n'est atteint par **aucun** parcours de ce périmètre (maximum : 4439 questions pour `2nde-maths`, soit 295 parties). Les parcours de calcul comptent de 492 à 4439 questions. La limite n'est pas la place (quelques dizaines de Ko par paquet) mais la qualité : une règle du pipeline interdit qu'un modèle dépasse 8 % d'un parcours, donc un parcours n'est grand que s'il a au moins une quinzaine de modèles différents ; sinon il est limité à environ 500 questions. **Pour grossir un parcours il faut ajouter des modèles** (pas des variantes) puis relancer `lycee_caps.py`. Les parcours plafonnés à ~500 : chimie 2nde/Tle, SVT/Biology, anglais, géographie, informatique 2nde, GCE Chemistry Form 5.
- **Séries** : A, C, D, E, TI ne sont pas des champs du format ; elles figurent dans la catégorie. Les thèmes de maths et de physique de Tle séries C / D / E sont couverts, pas les spécialités de la série TI (électronique, génie).
- **Matières fortement factuelles** (histoire, philosophie, littérature, ECM, géographie de Tle) : de 10 à 90 questions par parcours, **toutes à relire** ; elles ne sont **pas jouables** tant qu'elles ne sont pas approuvées (règle `review` + `fact`). Les œuvres au programme changent chaque année : seuls des auteurs et ouvrages très enseignés sont utilisés.
- **Répartition par classe indicative** : la correspondance thème → classe (par exemple l'histoire du Cameroun en Tle) a été faite sans le texte officiel sous les yeux ; à confronter aux programmes MINESEC et au syllabus du GCE Board par un enseignant.
- **GCE** : pas de couverture des épreuves pratiques ni des cartes (Geography Paper 2), ni des textes imposés de Literature d'une année donnée ; pas de questions à réponse longue.
- **Anglais / English Language** : grammaire et vocabulaire (formes verbales, comparatifs, pluriels, prépositions, conditionnels, discours rapporté, articles, notions de rédaction) ; pas de compréhension de texte (pas de passage fourni, donc pas de question).
- **ECM, informatique, SVT, économie** : peu de faits par classe ; la partie calculée (génétique, écologie, comptabilité nationale, représentation des données, algorithmes) est plus riche que la partie « cours ».
- **Sources** : `dist/sources.json` donne une fiche par thème de faits ; aucune n'a été vérifiée par un humain.

## 5. Où sont les paquets, et comment les reconstruire

```sh
python3.12 tools/quiz-bank/quizbank.py check     # génère + contrôle (code 0 = aucune erreur, aucun plafond dépassé)
python3.12 tools/quiz-bank/lycee_caps.py         # recalcule les plafonds et les sels (après avoir ajouté ou modifié un modèle)
python3.12 tools/quiz-bank/quizbank.py build     # écrit content/quiz/dist (un paquet par parcours et par tranche de 1 500 questions)
python3.12 tools/quiz-bank/lycee_report.py       # régénère ce document
```

Python 3.12 est nécessaire (les générateurs plus anciens du dépôt utilisent des f-strings de 3.12). Un paquet d'un parcours pèse de 20 à 80 Ko : bien en dessous des 3 Mo d'un lot.
