# Audit du contenu « Apprendre » (CastBridge-TV) — 2026-10-02

Exigence du propriétaire : « Toutes les classes dans le mode Apprendre doivent avoir du contenu ». Audit en lecture seule, branche `integration/agents`.

Sources mesurées : `content/learn/scopes.txt` (244 dossiers de packs, tous rattachés à un lot, aucun orphelin), `content/learn/*/lessons/*.json` (fiches et exercices comptés dans les fichiers), `android/core/build/learn-lots/lots-catalog.json` et les `*.lot.zip` (build du 2026-10-02 01:30), `content/learn/embedded.txt`, `LearnScopes.kt`, `LearnCatalog.kt`, `LearnLotConsumer.kt` (`classes()`), `LearnActivity.kt` (écran « Contenus »).

## 1. Constat global

- Échelle `LearnScopes.ladders` : 13 scopes francophones (maternelle … tle-commun/tle-cd/tle-a), 14 anglophones (nursery, class1-6, form1-5, lower-sixth, upper-sixth), 3 licence (droit-l1/l2/l3) = **30 scopes**.
- Lots construits : **29** (2 375 fiches, 7,40 Mo de zip). **Trois scopes de l'échelle n'ont AUCUN lot ni AUCUN pack : `nursery`, `tle-a`, `droit-l3`.** Ce sont les seules classes à 0 fiche.
- Les 241 packs sont tous `status: draft` (brouillon IA, relecture enseignant pas faite) ; aucun pack non brouillon.
- **Socle embarqué (APK)** : 6 packs seulement (`maternelle-decouverte`, `cep-maths`, `fslc-maths`, `bepc-maths`, `gceol-maths`, `bac-maths`), soit 6 scopes ayant du contenu sans rien recevoir : maternelle, cm2, class6, 3e, form5, tle-cd. Les 24 autres scopes ne montrent du contenu sur la TV que si leur lot est reçu (clé USB, téléphone).
- Écran « Contenus » (`ContentsScreen` → `LearnLotCatalog.classes()`) : liste les lots **installés** puis les scopes du socle ; un scope sans lot installé ni socle est **absent de la liste** (pas de ligne « vide »). L'écran d'une classe sans pack affiche « Aucun contenu installé pour cette classe » (`LearnActivity.kt` l.352).
- Budget : les 29 lots totalisent 7,40 Mo de zip alors que la TV est plafonnée à 10 Mo (Apprendre + Quiz + socle, `LotBudget.TV_MAX_BYTES`). Tout ne tient donc pas à la fois avec le Quiz : le planificateur (`learnPriority`) garde la classe de l'élève, puis N+1, N-1, N+2, N-2. Le plafond par lot est de 3 Mo (le plus gros : form5, 0,84 Mo).
- Docs périmées : `docs/LEARN.md` § 4 (« 488 Ko pour 10 lots ») et `docs/coverage/learn-*.md` décrivent un état antérieur ; les chiffres ci-dessous font foi.

## 2. Tableau par scope

Fiches (F), exercices (Ex) et octets comptés dans `content/learn` ; zip = octets du lot construit (v = version). « Socle » = pack dans l'APK. Fiches par matière entre parenthèses (la clé matière est celle du `pack.json`).

### Francophone

| Scope | Packs | F | Ex | Zip (v) | Socle | Matières (fiches) | Verdict |
|---|---|---|---|---|---|---|---|
| maternelle | 10 | 73 | 345 | 108 Ko (v6) | oui (1 pack) | découverte 25, français 20, maths 22, english 6 | OK (PS/MS/GS) |
| cp | 10 | 78 | 858 | 209 Ko (v4) | non | français 20, maths 22, english 12, hist-géo 12, sciences 12 | OK |
| ce1 | 5 | 51 | 561 | 161 Ko (v4) | non | français 16, maths 15, english 6, hist-géo 7, sciences 7 | OK, anglais mince (6) |
| ce2 | 5 | 53 | 583 | 156 Ko (v5) | non | français 19, maths 14, english 6, hist-géo 7, sciences 7 | OK, anglais mince |
| cm1 | 5 | 45 | 495 | 157 Ko (v4) | non | français 12, maths 12, english 6, hist-géo 7, sciences 8 | OK mais le plus léger du primaire (45) |
| cm2 (CEP) | 10 | 90 | 1 052 | 362 Ko (v6) | oui (cep-maths) | français 23, maths 23, english 13, hist-géo 14, sciences 17 | OK |
| 6e | 6 | 102 | 1 122 | 266 Ko (v4) | non | français 29, english 19, hist-géo 16, maths 16, svt 13, informatique 9 | OK |
| 5e | 8 | 113 | 1 243 | 298 Ko (v4) | non | français 24, maths 20, english 18, hist-géo 16, pct 10, svt 10, informatique 9, vie-hygiène 6 | OK |
| 4e | 10 | 107 | 1 177 | 279 Ko (v4) | non | hist-géo 20, maths 19, english 15, pct 14, français 13, svt 12, informatique 11, ETP/vie 3 | OK, français plus mince que 5e |
| 3e (BEPC) | 8 | 108 | 1 249 | 365 Ko (v6) | oui (bepc-maths) | français 19, hist-géo 20, english 16, maths 15, pct 15, svt 13, informatique 10 | OK |
| 2nde | 11 | **54** | 622 | 283 Ko (v4) | non | maths 10 (A+C), hist-géo-ECM 6, français 5, english 5, svt 5, physique 5, chimie 4, économie 5, droit 4 | **MINCE** : 4-6 fiches par matière, la moitié du collège |
| 1re (Probatoire) | 21 | 98 | 1 114 | 487 Ko (v6) | non | maths 24, physique 18, économie 17, svt 14, français 10, hist-géo 6, english 5, chimie 4 (+ ETP) | Inégal : english 5, chimie 4, hist-géo 6 |
| tle-cd (Bac) | 15 | 75 | 875 | 436 Ko (v6) | oui (bac-maths) | maths 30, physique 9, physique-chimie 5, chimie 5, svt 6, hist-géo 6, english 5, français 5, économie 4 | Inégal : english/français/chimie à 5 |
| tle-commun (philo) | 2 | **10** | 115 | 58 Ko (v6) | non | philosophie 10 | Mince mais complet en un seul sujet |
| **tle-a** | **0** | **0** | **0** | **aucun lot** | non | — (français-A est rangé dans tle-cd, ni philo-A, ni lettres) | **VIDE** |

### Anglophone

| Scope | Packs | F | Ex | Zip (v) | Socle | Matières (fiches) | Verdict |
|---|---|---|---|---|---|---|---|
| **nursery** | **0** | **0** | **0** | **aucun lot** | non | — (catalogue : Nursery 1-2 existe, `maternelle` fr ne le couvre pas) | **VIDE** |
| class1 | 4 | 54 | 594 | 85 Ko (v2) | non | maths 16, english 15, science 13, social studies 10 | OK, pas de French/Citizenship |
| class2 | 4 | 52 | 572 | 85 Ko (v2) | non | maths 17, science 13, english 12, social studies 10 | idem |
| class3 | 4 | 56 | 616 | 98 Ko (v2) | non | maths 16, science 15, english 14, social studies 11 | idem |
| class4 | 4 | 53 | 583 | 125 Ko (v2) | non | maths 17, english 15, science 12, social studies 9 | idem |
| class5 | 4 | 57 | 627 | 133 Ko (v2) | non | maths 15, english 17, science 15, social studies 10 | idem |
| class6 (FSLC) | 4 | 65 | 778 | 205 Ko (v6) | oui (fslc-maths) | english 18, maths 20, science 16, social studies 11 | OK |
| form1 | 8 | 68 | 748 | 195 Ko (v5) | non | english 19, histoire-géo 18, sciences 17, maths 14 | Correct ; maths 14 fiches seulement |
| form2 | 8 | 67 | 737 | 192 Ko (v5) | non | english 19, hist-géo 18, sciences 17, maths 13 | idem |
| form3 | 10 | 87 | 957 | 269 Ko (v6) | non | english 19, hist-géo 18, maths 12, biologie 12, physique 11, chimie 10, sciences 5 | OK |
| form4 | 12 | 105 | 1 155 | 314 Ko (v5) | non | english 19, hist-géo 20, maths 12, chimie 11, physique 11, éco 11, sciences 11, biologie 10 | OK |
| form5 (GCE OL) | 13 | 272 | 3 090 | 838 Ko (v7) | oui (gceol-maths) | maths 60, hist-géo 38, biologie 37, english 33, chimie 31, physique 28, sciences 24, éco 21 | Le plus riche |
| lower-sixth | 11 | 150 | 1 650 | 439 Ko (v5) | non | maths 31, hist-géo 22, sciences 21, physique 20, biologie 18, chimie 17, éco 12, english 9 | OK ; english 9 |
| upper-sixth (GCE AL) | 11 | 153 | 1 776 | 479 Ko (v7) | non | maths 33, hist-géo 23, biologie 21, sciences 21, chimie 16, physique 16, éco 13, english 10 | OK ; english 10 |

### Licence

| Scope | Packs | F | Ex | Zip (v) | Socle | Matières (fiches) | Verdict |
|---|---|---|---|---|---|---|---|
| droit-l1 | 11 | 50 | 570 | 204 Ko (v6) | non | économie 14, maths 14, sciences/info 9, droit 8, français (méthodo) 5 | Pas vide mais le droit pur n'a que 8 fiches |
| droit-l2 | 7 | 29 | 333 | 113 Ko (v4) | non | maths 13, droit 8, économie 8 (`sup-l1-droit-des-obligations` est rangé ici) | **MINCE** (29) |
| **droit-l3** | **0** | **0** | **0** | **aucun lot** | non | — (L3 existe dans le catalogue et l'échelle) | **VIDE** |

## 3. Trous, par gravité

1. **Classes à 0 contenu (3)** : `nursery` (Nursery 1-2, sous-système anglophone), `tle-a` (Terminale A, série littéraire), `droit-l3` (Licence 3). Ni pack, ni lot, ni ligne dans « Contenus ».
2. **Classes visibles mais minces (comparées à 100-113 fiches pour 6e-3e)** : 2nde (54, 4-6 fiches par matière) ; tle-commun (10, philosophie seule) ; droit-l2 (29) ; droit-l1 (50, dont seulement 8 de droit).
3. **Matières à 4-6 fiches au lycée francophone** : chimie 2nde/1re (4), english 1re/Tle/2nde (5), français 2nde/Tle (5), hist-géo 1re/Tle (6) ; english Lower/Upper Sixth (9-10) ; anglais du primaire francophone CE1-CM1 (6).
4. **Matières absentes** : Class 1-5 sans French ni Citizenship/ICT ; Terminale A sans philosophie dédiée, ni LV2, ni lettres ; EPS, éducation artistique et langues nationales absentes du primaire (aucune matière au catalogue, `docs/coverage/learn-primaire.md`) ; licence réduite au droit/éco de base, pas d'autres filières ni de version anglophone.
5. **Visibilité TV** : sur une TV neuve, seuls 6 scopes sur 30 s'affichent (socle). Pour que « toutes les classes aient du contenu » à l'écran, il faut soit élargir `embedded.txt` (budget socle 5 Mo compressés, `LearnContentTest.embeddedBudget`), soit garantir la livraison du lot de la classe de l'élève par le téléphone/la clé (`learnPriority`).

## 4. Outillage et estimation

Outillage existant : `tools/content-lots/build_lots.py` + `make_release.py` (construit lots, `lots-catalog.json`, `LOT-VERSIONS.json`) ; `gradle :core:checkLearnContent` (validateur de fiches) et `:core:checkStarterBudget` ; `tools/content-validation/cbvalidate.py` ; `tools/content-budget/content_budget.py` (aucun lot TV > 3 Mo, total < 3 Go) ; `tools/content-map/map_existing.py` et `tools/content-graph/gen_graph.py` (graphe de compétences) ; briefs d'agents `docs/agent-briefs/content-phase1.md`, `content-phase2.md`, `content-integration.md`, `content-langues-w1.md` ; programmes de référence `docs/curriculum/*.md` (maths, français, english, svt, physique, chimie, histoire-géo-ECM, informatique, droit-éco-gestion, vie pratique). Chaque pack porte un `programRef` (MINESEC/MINEDUB « à vérifier »).
Réserve : **je n'ai trouvé nulle part la « porte de 70 % de correspondance au programme »** (ni dans `docs/CONTENT-*.md`, ni dans les scripts, ni dans les briefs). Les seuls 70 % du dépôt sont des seuils de maîtrise/quiz (`CONTENT-ARCHITECTURE.md` § 8) ; la porte réellement appliquée est le validateur `cbvalidate`/`checkLearnContent` plus les budgets. Si cette porte existe ailleurs (autre branche, ordre oral), elle est à brancher avant la production.

Ordre de grandeur mesuré : une fiche avec ses exercices pèse 13 Ko en JSON et ≈ 2,5-3,5 Ko en zip (7,40 Mo / 2 375 fiches ≈ 3,1 Ko).

| Trou | Contenu à produire (programme) | Fiches | Zip estimé | Effort estimé |
|---|---|---|---|---|
| nursery | Nursery 1-2 : langage (English), pre-maths (counting, shapes), discovery of the world, phonics ; cf. maternelle fr (73 fiches) comme modèle | ≈ 40 | ≈ 0,12 Mo | 1 vague d'agent (≈ 6 packs) + relecture ; créer le scope dans `scopes.txt` |
| tle-a | Série A (lettres) : français-littérature A, philosophie A, histoire-géo-ECM A, english A, LV2, maths A ; réutiliser `tle-francais-a` | ≈ 50 | ≈ 0,15 Mo | 1 vague ; scinder `tle-francais-a` hors de `tle-cd` |
| droit-l3 | Licence 3 droit : droit civil/pénal/administratif/OHADA approfondi, procédure, droit des affaires | ≈ 30 | ≈ 0,10 Mo | 1 vague ; programmes universitaires à sourcer (non cités dans `docs/curriculum`) |
| 2nde (étoffer) | Programme MINESEC 2nde : porter chaque matière à 10-14 fiches (+50 à +60 fiches : chimie, physique, svt, français, english, hist-géo, éco) | +55 | +0,17 Mo | 1-2 vagues |
| droit-l2 / droit-l1 droit | Compléter obligations, contrats spéciaux, droit pénal, institutions (+20 à +30 fiches) | +25 | +0,08 Mo | 1 vague |
| tle-commun, 1re/Tle english-français-chimie, Sixth english | porter à 10-12 fiches par matière (+60 fiches environ) | +60 | +0,19 Mo | 1-2 vagues |
| Class 1-5 | French, Citizenship (+8 à +10 fiches par classe) | +45 | +0,14 Mo | 1 vague |

Incidence sur les budgets :
- **TV 10 Mo** : l'ensemble du contenu actuel + les trous ci-dessus fait ≈ 8,2 Mo de zip pour Apprendre seul ; avec le Quiz, tout n'est pas résident simultanément, ce qui est déjà géré par le planificateur (classe de l'élève ± 2). Les ajouts sont négligeables par lot (aucun lot près de 3 Mo). Le risque n'est pas la taille mais l'**ordre de livraison** : un lot manquant sur la TV = classe vide à l'écran. Les 3 scopes vides figurent déjà dans `LearnScopes` (échelle et libellés) : il reste à créer les packs et l'entrée de `scopes.txt`.
- **Essai 100 Mo** : `content/learn` pèse 35,7 Mo non compressé ; l'édition d'essai exige que **chaque sous-catégorie ait au moins un élément** (`tools/trial-edition` échoue si une sous-catégorie est vide). Les 3 scopes vides feraient donc échouer l'outil dès qu'ils sont déclarés au catalogue d'essai ; les ajouter coûte ≈ 2 Mo non compressés, soit largement sous le plafond (marge : 100 Mo moins 29,9 Mo d'essai mesuré, hors Langues ≈ 70 Mo réservés).
- Tout nouveau contenu est `status: draft` : le passer en `stable` demande la relecture enseignant (`docs/LEARN-REVIEW.md`, 7 000+ lignes de points à confirmer).

## 5. Actions recommandées (par priorité)

1. Produire `nursery`, `tle-a`, `droit-l3` (3 vagues, ≈ 120 fiches, ≈ 0,4 Mo) : seuls scopes à 0.
2. Étoffer la 2nde (54 → ≈ 110 fiches), puis les matières à 4-6 fiches (chimie, english, français, hist-géo).
3. Décider si les classes sans lot doivent apparaître dans « Contenus » avec un état « à recevoir » (aujourd'hui elles sont invisibles).
4. Décider d'élargir `embedded.txt` (un pack de maths par scope ≈ 25 packs, < 1 Mo zip, dans le budget socle de 5 Mo) pour qu'aucune classe ne soit vide sur une TV neuve.
5. Retrouver ou définir la « porte de 70 % de correspondance au programme » et l'appliquer aux nouveaux packs avant intégration.
6. Mettre à jour `docs/LEARN.md` § 4 et `docs/coverage/learn-*.md` (chiffres périmés).
