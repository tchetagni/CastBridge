# Architecture du contenu : de zéro à l'expert (Apprendre + Quiz)

> Cadre que **tout le réseau d'agents** suit pour produire la base de formation de **CastBridge** (téléphone) et **CastBridge-TV** (TV).
> Code : `android/core/src/main/kotlin/castbridge/core/curriculum/` · graphe : `content/graph/` · outils : `tools/content-*`.
> Documents liés : [MEDIA-POLICY](MEDIA-POLICY.md) · [CONTENT-PUBLISH](CONTENT-PUBLISH.md) · [PEDAGOGY-RUBRIC](PEDAGOGY-RUBRIC.md) · [LOTS](LOTS.md) (cadre des lots, branche `claude/lots-framework`) · [LEARN](LEARN.md) · [QUIZ](QUIZ.md) · [curriculum/](curriculum/README.md) (descripteurs par domaine).

**Objectif du propriétaire.** Un réseau d'agents experts-pédagogues produit la base de formation de **tous les niveaux requis**, avec illustrations et courtes animations multimédia, pour que tout apprenant — enfant, adolescent ou adulte repartant de zéro, au Cameroun (sous-systèmes francophone et anglophone) — acquière les bases puis progresse jusqu'au niveau **expert**. La base vit sur GitHub et est **copiée** sur le serveur de production. Plafond : **3 Go**. Tout contenu est marqué « à relire » et validé par des humains **3 mois après la distribution bêta**.

## 0. Les trois règles du propriétaire (obligatoires, contrôlées par les outils)

**RÈGLE A — Rien n'est produit sur le serveur.** Le réseau travaille **uniquement dans le dépôt**. Les résultats finis sont **copiés** sur le serveur de production ensuite (pipeline de publication, [CONTENT-PUBLISH](CONTENT-PUBLISH.md)). Aucun agent ne se connecte au serveur, n'y écrit ni n'y déploie. *Outillage :* `tools/publish-content.sh` ne fait que construire ; la copie (`rsync`) est en simulation par défaut et exige `--go` + `CASTBRIDGE_CONFIRM_PRODUCTION=yes` + un catalogue signé.

**RÈGLE B — Tout est organisé en LOTS, pour Quiz comme pour Apprendre.** Un lot est un paquet homogène, versionné et signé des données d'**une** fonction pour **un** périmètre (contrat `castbridge.core.lots.LotApi` : `LotId(feature "learn"|"quiz", scope)`, `LotMeta(id, version, bytes, sha256, title, minAppVersion)`, `LotSource`, `LotConsumer`, `LotBudget` TV 10 Mo / téléphone 100 Mo, livré par `claude/lots-framework`). Conséquences :
1. chaque leçon, exercice, question et média appartient à **exactement un** lot ;
2. un lot est **autonome** (une TV qui a raté des mises à jour reçoit directement la dernière version de chaque lot) ;
3. un lot **compatible TV** pèse **moins de 3 Mo**, figures vectorielles et animations comprises ; les médias lourds optionnels vivent dans des **lots média** séparés, réservés au téléphone, qu'un lot peut référencer mais dont il ne dépend jamais ;
4. le téléphone garde au plus **100 Mo** de lots ;
5. un lot ne change de **version** que si son contenu change (`content/LOT-VERSIONS.json`, empreinte du contenu) ;
6. les **nœuds du graphe de compétences** correspondent à des portées de lot (classe / niveau / thème) ;
7. les **parcours de progression sont des ensembles ordonnés de lots** (`content/graph/paths.json`).
*Outillage :* `tools/content-budget` échoue au-delà de 3 Mo par lot TV, 100 Mo par parcours téléphone, 50 Mo par fichier, 3 Go au total ; le validateur de graphe refuse une compétence hors lot ; le vérificateur de médias refuse un média qui n'appartient pas à un lot ou un média lourd dans un lot TV.

**RÈGLE C — Ce que veulent dire les niveaux.** Pour une classe donnée :

| Niveau | Nom | Sens (définition du propriétaire) |
|---|---|---|
| **N0** | Découverte | on part de zéro, aucun prérequis |
| **N1** | Fondations | le **programme officiel camerounais maîtrisé** (réussite solide à l'examen) |
| **N2** | Intermédiaire | **excellence à l'échelle nationale camerounaise** pour cette classe : le niveau des meilleurs élèves du pays (concours, olympiades nationales, meilleures notes aux examens officiels et **bien au-delà** du programme) |
| **N3** | Avancé | le **pont** entre l'excellence nationale et l'excellence internationale (problèmes et programmes de standard panafricain / international) |
| **N4** | Expert | **excellence mondiale** : le niveau des meilleurs élèves / étudiants de ce niveau dans le monde (olympiades internationales, programmes internationaux de tête, exigences d'entrée des meilleures universités) |

Le contenu N2-N4 doit **vraiment** atteindre ces standards : plus difficile, plus profond, plus rigoureux, plus perspicace (démonstrations, modélisation, raisonnement en plusieurs étapes, transfert à des situations inédites) — **pas** « plus du même ». Les descripteurs et tâches exemplaires sont **originaux** : aucun sujet de concours, d'olympiade ou d'examen n'est reproduit ; on décrit le standard et on écrit de nouveaux problèmes de ce standard. La fidélité au niveau est évaluée lot par lot ([PEDAGOGY-RUBRIC](PEDAGOGY-RUBRIC.md), critère `levelFidelity`).

## 1. Le modèle de progression

```
Domaine  ──▶  Compétence (atomique, évaluable)  ──▶  leçons · exercices · questions · médias  ──▶  LOT (feature, scope)
(10)           id « math.pythagoras », niveau N0-N4     chacun porte skill / level / lot               ≤ 3 Mo (TV) · version · signature
                prérequis = graphe orienté acyclique      paliers de difficulté 1-5                    parcours = ensembles ordonnés de lots
```

**Domaines** (graine de graphe livrée pour les sept premiers ; les trois autres sont à ouvrir, § 4) :
`mathematiques` · `francais` (langue française) · `english` (langue anglaise) · `physique-chimie` (sciences physiques **et** chimie, une seule arborescence, deux étiquettes `physique` / `chimie`) · `svt` · `histoire-geo-ecm` · `informatique` (informatique et numérique) · `droit-eco-gestion` *(à ouvrir)* · `vie-pratique` *(à ouvrir ; portée thématique `vie-pratique`)*.

**Compétence** : ce qu'un élève sait faire, formulable en une phrase et évaluable en 5-30 minutes (`estimated minutes`), avec ≥ 1 leçon et ≥ 3 exercices répartis sur plusieurs paliers une fois le contenu rattaché.

**Quiz et Apprendre partagent le même graphe et la même échelle** : une question de quiz porte `skill`, `nlevel`, `lot` comme un exercice ; le quiz sert à **évaluer et consolider** ce que la leçon enseigne.

## 2. L'échelle des niveaux de bout en bout

| | N0 Découverte | N1 Fondations | N2 Intermédiaire | N3 Avancé | N4 Expert |
|---|---|---|---|---|---|
| Standard | zéro → amorce | programme CM · BEPC · GCE · Bac | **excellence nationale** | **pont** national → international | **excellence mondiale** |
| Paliers d'exercice (`tier`) | `application`, `autoeval` | `application`, `approfondissement`, `examen`, `autoeval` | `excellence-cm` | `excellence-monde` | `excellence-monde` |
| Difficulté absolue (`difficulty`) | 1-2 | 1-3 | 3-4 | 4-5 | 5 |
| Succès attendu de l'apprenant **de ce niveau** | ≈ 0,71 | ≈ 0,71 | ≈ 0,71 | ≈ 0,71 | ≈ 0,71 |
| Un apprenant **un niveau en dessous** réussit | — | ≈ 0,44 | ≈ 0,44 | ≈ 0,44 | ≈ 0,44 |
| Critère de maîtrise (§ 8) | ≥ 85 % | ≥ 80 % | ≥ 70 % | ≥ 65 % | ≥ 60 % |
| Forme dominante | manipuler, nommer, imiter | appliquer une méthode connue | combiner plusieurs méthodes, piège, justification | démontrer, généraliser, modéliser, situation inédite | problème ouvert, preuve complète, synthèse originale |

## 3. Classes, examens et points d'entrée → niveaux et portées de lots

L'**année de scolarité** `y` (0-15) ordonne le graphe ; chaque année a une portée francophone et une portée anglophone (les sous-systèmes enseignent les mêmes compétences dans leur langue, donc **deux lots distincts**, jamais une leçon dans deux lots). Les compétences sont rattachées à la portée de leur **première** année.

| Année | Francophone (portée) | Anglophone (portée) | Examen / jalon | Bande |
|---|---|---|---|---|
| y0 | Maternelle (`mat`) | Nursery (`nursery`) | — | B0 |
| y1 | SIL (`sil`) | Class 1 (`class1`) | — | B1 |
| y2 | CP (`cp`) | Class 2 (`class2`) | — | B1 |
| y3 | CE1 (`ce1`) | Class 3 (`class3`) | — | B1 |
| y4 | CE2 (`ce2`) | Class 4 (`class4`) | — | B2 |
| y5 | CM1 (`cm1`) | Class 5 (`class5`) | — | B2 |
| y6 | CM2 (`cm2`) | Class 6 (`class6`) | **CEP** / **FSLC** | B2 |
| y7 | 6e (`6e`) | Form 1 (`form1`) | — | B3 |
| y8 | 5e (`5e`) | Form 2 (`form2`) | — | B3 |
| y9 | 4e (`4e`) | Form 3 (`form3`) | — | B3 |
| y10 | 3e (`3e`) | Form 4 (`form4`) | **BEPC** | B3 |
| y11 | 2nde (`2nde`) | Form 5 (`form5`) | **GCE O Level** (anglophone) | B4 |
| y12 | 1re (`1ere`) | Lower Sixth (`lower-sixth`) | **Probatoire** | B4 |
| y13 | Terminale (`tle`) | Upper Sixth (`upper-sixth`) | **Baccalauréat** · **GCE A Level** | B4 |
| y14 | Licence 1 / BTS 1 (`l1`) | Level 1 (`l1-en`) | — | B5 |
| y15 | Licence 2 / BTS 2 (`l2`) | Level 2 (`l2-en`) | **BTS** · Licence | B5 |

*À valider par des enseignants :* l'équivalence des années anglophones (Form 1-5 ≈ 6e-2nde) et la place du GCE O Level (fin de Form 5, à peu près BEPC+) sont une convention de travail du graphe (ordre des prérequis), pas une équivalence officielle.

**Tables de portées de lots** (`content/graph/scopes.json`, 142 portées, générées par `tools/content-graph/gen_graph.py`) — noms de lots `castbridge-lot-<feature>-<scope>-v<n>.lot` :

| Type | Portées | Contenu | Niveaux | Compatible TV |
|---|---|---|---|---|
| Classe (base) | `<classe>` : `mat`, `sil`…, `cm2`, `3e`, `form5`, `tle`, `l1`, … (32) | leçons, exercices, questions du programme | N0, N1 | oui (< 3 Mo) |
| Classe (excellence) | `<classe>-exc` (32) | contenu N2, N3, N4 de la classe | N2-N4 | oui (< 3 Mo ; à scinder en `-exc-n2`/`-exc-n3` si dépassement, en accord avec `claude/lots-framework`) |
| Média (téléphone) | `<classe>-media` (32) | images WebP, audio Opus, clips ; **jamais requis** | N0-N4 | **non** (téléphone seulement) |
| Thème adulte | `alpha-adultes`, `vie-pratique` (+ `-exc`) (4) | alphabétisation et calcul des adultes ; vie pratique (argent, santé, démarches, numérique) | N0-N1 / N2-N4 | oui |
| Thème quiz | `culture-cm`, `culture-af`, `culture-monde` ; `<filière>-l1/l2/l3` (13 filières × 3) | culture générale 70 % Cameroun / 20 % Afrique / 10 % Monde ; quiz du supérieur | N1 | oui |

Les paquets existants se rangent ainsi : `learn:cm2` (cep-maths, cep-francais, cep-sciences), `learn:class6` (fslc-*), `learn:3e` (bepc-*), `learn:form5` (gceol-*), `learn:1ere` (probatoire-francais), `learn:tle` (bac-*), `learn:mat` (maternelle-decouverte), `quiz:cm2`, `quiz:3e`, `quiz:tle`, `quiz:culture-cm`, `quiz:droit-l1`, `quiz:economie-l1`, `quiz:mathematiques-l1` (`tools/content-lib/lotlib.py`). Les 14 lots actuels pèsent 2,3 Mo en tout (`tools/content-budget`).

**Parcours** (`content/graph/paths.json`, ensembles ordonnés de lots, classe par classe : `learn:X`, `quiz:X`, `learn:X-exc`, `quiz:X-exc`, `learn:X-media`) : `primaire-fr`, `college-fr`, `lycee-fr`, `primaire-en`, `secondaire-en`, `superieur`, `scolarite-fr`, `scolarite-en`, `adulte-debutant`. Chaque parcours doit tenir en **100 Mo** sur le téléphone.

**Points d'entrée des adultes autodidactes** (aucune classe déclarée) :

| Profil | Entrée | Chemin | Test de positionnement |
|---|---|---|---|
| Adulte ne sachant ni lire ni compter | `alpha-adultes` N0 | lecture-écriture et calcul de base en contexte adulte (monnaie FCFA, marché, santé, téléphone), puis CP-CE1 | pas de test écrit : tâches orales et images, § 9 |
| Adulte ayant quitté l'école au primaire | y3-y5 N1 | reprise des bases du primaire, en accéléré (test de validation de compétences) | test de positionnement par domaine |
| Jeune déscolarisé (collège) | y6-y9 | parcours `college-*` avec remédiation ciblée | idem |
| Candidat libre à un examen (BEPC, Probatoire, Bac, GCE) | classe de l'examen, N1 | « Préparer mon examen » puis N2 si objectif d'excellence | idem |
| Professionnel visant le supérieur / BTS / L1 | y11-y14 | mise à niveau puis `l1` | idem |
| Apprenant exceptionnel, quel que soit l'âge | N2-N4 directement | voie d'excellence (§ 8.3) | test adaptatif qui détecte l'exceptionnel (§ 9) |

## 4. Le graphe de compétences (`content/graph/<domaine>.json`)

Format (un fichier par domaine + `scopes.json` + `paths.json`) :

```json
{"format":1,"domain":"mathematiques","prefix":"math","title":{"fr":"Mathématiques","en":"Mathematics"},
 "skills":[{"id":"math.pythagoras","title":{"fr":"Théorème de Pythagore","en":"Pythagoras' theorem"},"level":"N1",
   "prereq":["math.roots","math.perimeter"],"years":[9],"classes":["4e","form3"],"exams":[],"lots":["4e","form3"],
   "minutes":30,"tags":["geometrie","probe"]}]}
```

`id` = `<préfixe>.<slug>` ; `level` N0-N4 ; `prereq` : ids (même domaine ou d'un autre : `math.eq-1` depuis `phc.…`) ; `years` : années où la compétence se travaille ; `classes`/`exams` : correspondances ; `lots` : portées (une par sous-système) ; `tags` : thèmes, `concours`, `olympiade`, `probe` (sonde de placement, calculée).

**Sources et génération.** Les graines lisibles sont `tools/content-graph/seed/<domaine>.txt` (une ligne par compétence) ; `python3 tools/content-graph/gen_graph.py` produit les JSON (`--check` vérifie qu'ils sont à jour). On ajoute une compétence en ajoutant une ligne, jamais en éditant le JSON à la main.

**Taille actuelle : 378 compétences, 264 h estimées.**

| Domaine | N0 | N1 | N2 | N3 | N4 | Total |
|---|---|---|---|---|---|---|
| mathematiques | 7 | 71 | 20 | 11 | 8 | 117 |
| francais | 7 | 34 | 7 | 3 | — | 51 |
| english | 5 | 22 | 6 | 3 | — | 36 |
| physique-chimie | 4 | 33 | 10 | 6 | 5 | 58 |
| svt | 4 | 28 | 6 | 4 | — | 42 |
| histoire-geo-ecm | 5 | 25 | 6 | 3 | — | 39 |
| informatique | 4 | 21 | 6 | 4 | — | 35 |
| **Total** | 36 | 234 | 61 | 34 | 13 | **378** |

N4 est défini là où le standard mondial est crédible et mesurable aujourd'hui (mathématiques et physique-chimie : olympiades internationales, premier cycle des meilleures universités). Pour les autres domaines, N4 reste à définir (`warning` du validateur) ; droit-économie-gestion et vie pratique restent à ouvrir.

**Validation** (`ContentGraphValidator`, `gradle :core:checkContentGraph`, `tools/content-graph-check`) : graphe **acyclique** ; prérequis existants ; pas de compétence **orpheline** ; chaque compétence dans ≥ 1 portée de lot existante qui porte son niveau ; un prérequis n'est jamais d'un niveau ni d'une année supérieurs ; **échelle continue** (une compétence N2+ a un prérequis du même domaine d'un niveau au plus inférieur) ; **couverture** N0-N3 dans chaque domaine (N4 obligatoire en mathématiques et physique-chimie) ; **une fois le contenu rattaché** : ≥ 1 leçon, ≥ 3 exercices sur plusieurs paliers, et pour N2-N4 au moins 60 % d'exercices de palier d'excellence dans la fenêtre de difficulté du niveau (N3-N4 : au moins un `excellence-monde`). Avec `-PrequireContent` (porte de publication) toute compétence sans contenu est une erreur.

**Rattacher l'existant.** `python3 tools/content-map/map_existing.py` propose, **sans rien modifier**, `skill`, `level` et `lot` pour chaque leçon et paquet de quiz existants → `content/graph/mapping/existing.json` (56 des 70 leçons ont une proposition ; confiance high/medium/low). Les agents de contenu relisent puis ajoutent les champs.

## 5. Champs additifs des leçons, exercices et questions (optionnels, ignorés par les anciens lecteurs)

| Champ | Où | Sens |
|---|---|---|
| `skill` | leçon, exercice, question | id de la compétence du graphe |
| `level` | leçon, exercice | `N0`..`N4` (**quiz : `nlevel`**, car `level` y désigne déjà la classe `CM2`, `3e`…) |
| `prereq` | leçon | ids de compétences prérequises (en plus de `prerequisites` = ids de leçons) |
| `lot` | leçon, exercice, question | portée du lot (`cm2`, `cm2-exc`…), cohérente avec le pack |
| `media` | leçon, exercice | liste d'ids de `content/MEDIA-MANIFEST.json` |
| `calibration` / `calib` | exercice / question | taux de réussite visé par niveau d'apprenant : `{"N1":0.35,"N2":0.7,"N3":0.9}` |

Lecture : `LessonJson` et `QuizBank.parse` les lisent s'ils existent ; absents, rien ne change. Les validateurs vérifient niveaux (`N0..N4`), taux (0..1) et cohérence tier/difficulté.

## 6. Échelle de difficulté (additive, rétro-compatible)

- **Paliers** : `application`, `approfondissement`, `examen`, `autoeval` (existants) + **`excellence-cm`** (N2) et **`excellence-monde`** (N3-N4). Rang : application 0 · approfondissement 1 · examen 2 · excellence-cm 3 · excellence-monde 4 (`ExerciseTier.rank`).
- **Lecteurs** : un `tier` inconnu n'est **jamais** une erreur : il est lu comme le **palier connu le plus difficile** (`ExerciseTier.ofOrHardest`) — les lecteurs d'aujourd'hui traiteront de même les paliers de demain.
- **Difficulté 1-5** (les exercices passent de 1-3 à 1-5 ; le quiz était déjà 1-5) : fenêtres par niveau N0 1-2 · N1 1-3 · N2 3-4 · N3 4-5 · N4 5. Un exercice `excellence-*` a une difficulté ≥ 3.
- **Contenu ancien** (sans niveau déclaré) : il est lu comme **N1** et sa difficulté reste **relative à la classe** (une question de CM2 de difficulté 5 est la plus dure du programme de CM2, pas de l'excellence). Seul le contenu qui déclare `level`/`nlevel` utilise l'échelle absolue.
- **Calibration d'item** : `calibration` donne le taux de réussite **visé** par niveau d'apprenant. À défaut, modèle par défaut `LevelScale.successRate` : un apprenant du niveau de l'item réussit ≈ 0,71 ; un niveau au-dessus de lui ≈ 0,44 ; deux niveaux ≈ 0,25 ; un niveau en dessous ≈ 0,87. Les taux observés après la bêta recalibrent ces valeurs.

## 7. Le quiz choisit selon le niveau de l'apprenant

Pour que l'excellent ne s'ennuie pas et que le débutant ne soit pas écrasé : `LevelPick.pick(pool, niveauApprenant, n, seed)`.
1. Pour chaque question, taux de réussite attendu `p` = `calib[niveau]` si fourni, sinon modèle par défaut (position = niveau déclaré + effet de la difficulté dans la fenêtre).
2. À la position *i* sur *n*, cible `p* = 0,90 − 0,45·i/(n−1)` : échauffement facile (≈ 90 %), dernière question à ≈ 45 % (zone productive 0,45-0,90).
3. On prend, parmi les questions non utilisées, celle dont `p` est la plus proche de `p*` (aléatoire reproductible à graine donnée). Les questions `review` sont exclues comme aujourd'hui ; l'historique « sans répétition » du quiz reste prioritaire (`exclude`).
4. Le Millionnaire (15 questions croissantes) garde ses paliers : la montée de difficulté se fait **dans la zone du niveau de l'apprenant**. Un apprenant N4 n'a presque que du N3-N4 ; un N0 presque que du N0-N1 ; test : `CurriculumTest.beginnerIsNotCrushedAndExpertNotBored`.
5. Le niveau d'apprenant vient du test de positionnement (§ 9) puis se met à jour après chaque partie (réussite sur ≥ 70 % des items du niveau → monter ; < 45 % → descendre).

## 8. Maîtrise, remédiation, accélération

### 8.1 Critères de maîtrise (par compétence, § 2)
Une compétence est **maîtrisée** à un niveau quand l'apprenant atteint le seuil (N0 85 % · N1 80 % · N2 70 % · N3 65 % · N4 60 % de réussite sur les items de ce niveau) sur **deux séances espacées d'au moins 24 h**, dont ≥ 1 item de **transfert** (situation inédite). N2-N4 exigent en plus une **solution rédigée** notée ≥ 3/4 sur la grille (justification, rigueur, généralité). Un **domaine/classe** est maîtrisé à un niveau quand ≥ 80 % de ses compétences (hors `tags: option`) le sont.

### 8.2 « Reprendre les bases » (remédiation)
Déclenchement : deux séances consécutives sous 50 % sur une compétence, ou échec au positionnement. Procédure : (1) parcourir les **prérequis directs** dans le graphe, du plus élémentaire au plus proche, avec 3 items chacun ; (2) s'arrêter au premier prérequis réussi 3/3 : la **frontière** ; (3) proposer le chemin frontière → compétence visée, en leçons courtes N0/N1 ; (4) revenir à la compétence ; (5) re-tester 24 h puis 7 jours plus tard (révision espacée existante). Aucun jugement : formulation « on reprend ensemble depuis… ». Le point le plus bas est N0 : personne n'est « en dessous du début ».

### 8.3 Accélération
- **Validation de compétence** : 3 items du palier visé, 3/3 réussis → compétence validée sans leçon (et ses prérequis non encore vus comptent « inférés », avec un contrôle ponctuel).
- **Chaîne** : enchaînement de validations le long d'un chemin du graphe (parcours accéléré).
- **Voie d'excellence** : proposée quand un apprenant réussit ≥ 90 % sur ≥ 10 compétences N1 d'un domaine : « défi d'excellence » N2, puis N3 s'il réussit ≥ 70 % du N2 ; N4 sur invitation (§ 9).

## 9. Tests de positionnement (« où en suis-je ? »)

**Un test court (8-14 items, ≤ 10 min) par domaine**, à tout âge, qui place l'apprenant au bon niveau et à la bonne compétence, et détecte l'exceptionnel.
- **Items sondes** : les compétences taguées `probe` (une par domaine × année × niveau, calculée) ; ≥ 6 items par sonde (2 par difficulté de la fenêtre), balisés `placement`, **jamais utilisés comme exercices** (pas de fuite), en fr et en en.
- **Algorithme** (`curriculum.Placement`, testé) : escalier N0→N4 à partir de la classe déclarée (N1 de la classe ; adulte : y4-y5). Chaque niveau reçoit jusqu'à 3 items, **réussi à 2/3**. **Voie rapide** : deux bonnes réponses d'entrée → saut de deux niveaux (un apprenant exceptionnel atteint N3/N4 en ≤ 8 items, `exceptionalLearnerIsFastTracked`). Arrêt quand le plus haut niveau réussi et le plus bas échoué sont adjacents. Résultat : `level`, nombre d'items, `fastTracked`, action (`start-n0` | `standard-path` | `excellence-path`).
- **Deux dimensions** : d'abord la **frontière de classe** (recherche dichotomique sur la colonne vertébrale du domaine : sondes N1 des années y0..y13), puis le **niveau** (escalier ci-dessus) dans la classe frontière.
- **Ce que le test écrit** : les compétences sondées réussies = maîtrisées ; leurs prérequis = « inférés » (contrôle ponctuel plus tard) ; les compétences échouées ouvrent la remédiation (§ 8.2).
- **Enfants et adultes peu lecteurs** : consignes lues à voix haute, items à images, réponses par choix ; jamais de note, jamais de comparaison ; message : « voici où on commence ».
- **Équité** : items sans contexte culturel exclusif, calibrés après la bêta (taux observés par région, langue et âge).

## 10. Ce que doit faire un agent de contenu (liste de contrôle)

1. Choisir ses compétences dans `content/graph/` (ou en proposer par une ligne de graine) ; fixer **un lot** par élément, portée de la classe (`-exc` pour N2-N4).
2. Écrire leçons/exercices/questions avec `skill`, `level` (`nlevel` pour le quiz), `lot`, `tier`/`difficulty` dans la fenêtre du niveau ; N2-N4 : problèmes **originaux** du standard visé.
3. Médias : [MEDIA-POLICY](MEDIA-POLICY.md), déclarés dans `content/MEDIA-MANIFEST.json`, lots média pour tout ce qui est lourd.
4. Vérifier : `gradle :core:checkContentGraph`, `tools/build-learn-packs --check`, `python3 tools/content-budget/content_budget.py`, `python3 tools/content-media/check_media.py`.
5. Faire passer la **grille pédagogique** par lot ([PEDAGOGY-RUBRIC](PEDAGOGY-RUBRIC.md)) ; statut `draft` / `review` jusqu'à la validation humaine à 3 mois de la bêta.

## 11. Descripteurs de niveaux et étalons d'excellence par domaine

Pour **chaque domaine** et **chaque bande de classes** (B0 maternelle · B1 SIL-CE1 · B2 CE2-CM2 · B3 6e-3e / Form 1-4 · B4 2nde-Tle / Form 5-Upper Sixth · B5 L1-L2 / BTS) : un tableau N1-N4 (ce que sait faire l'apprenant, **3 tâches exemplaires originales** par niveau) et les **étalons** qui définissent « excellence camerounaise » et « excellence mondiale » (en termes généraux, jamais leurs sujets). Un fichier par domaine :

| Domaine | Fichier |
|---|---|
| Mathématiques | [curriculum/mathematiques.md](curriculum/mathematiques.md) |
| Langue française | [curriculum/francais.md](curriculum/francais.md) |
| English language | [curriculum/english.md](curriculum/english.md) |
| Sciences physiques (physique) | [curriculum/physique.md](curriculum/physique.md) |
| Chimie | [curriculum/chimie.md](curriculum/chimie.md) |
| SVT | [curriculum/svt.md](curriculum/svt.md) |
| Histoire-géographie-ECM | [curriculum/histoire-geo-ecm.md](curriculum/histoire-geo-ecm.md) |
| Informatique et numérique | [curriculum/informatique.md](curriculum/informatique.md) |
| Droit-économie-gestion | [curriculum/droit-eco-gestion.md](curriculum/droit-eco-gestion.md) |
| Vie pratique | [curriculum/vie-pratique.md](curriculum/vie-pratique.md) |

## 12. Outils et commandes

| Besoin | Commande |
|---|---|
| Tests du core sans plugin Android | `tools/core-harness/run.sh :core:test` (le module `:core` est du JVM pur) |
| Valider le graphe | `tools/core-harness/run.sh :core:checkContentGraph` (ou `gradle :core:checkContentGraph` dans `android/`) |
| Régénérer le graphe | `python3 tools/content-graph/gen_graph.py` |
| Budgets | `python3 tools/content-budget/content_budget.py` |
| Médias | `python3 tools/content-media/check_media.py` |
| Proposer le rattachement de l'existant | `python3 tools/content-map/map_existing.py` |
| Rapport pédagogique | `python3 tools/pedagogy-report/validate_report.py rapport.json` |
| Tout publier (construction seulement) | `tools/publish-content.sh` |
| Exporter vers le dépôt de contenu | `tools/content-split-repo/split_repo.sh <dossier>` |
| Tests des outils Python | `python3 -m unittest discover -s tools/tests` |
