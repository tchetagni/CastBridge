# Apprendre — formation des élèves, de la maternelle à la licence (POC, branche `feat/learn`)

Leçons (« fiches »), illustrations dessinées, exemples résolus pas à pas, exercices corrigés, **préparation des examens
camerounais** (CEP, FSLC, BEPC, GCE O Level, Probatoire, Bac) avec épreuves blanches chronométrées, progression des
élèves et tableau de bord des parents. Sous-systèmes **francophone et anglophone**. Sur la TV (télécommande, 720p à 4K,
~1 Go de RAM, 32 bits) et sur le téléphone.

> **Statut du contenu : brouillon.** Tout le contenu livré a été rédigé avec une IA à partir des programmes connus, puis
> vérifié automatiquement (calculs, index des réponses, barèmes, formules, figures). Il **n'a pas été relu par des
> enseignants** : chaque fiche porte le statut `draft` (« Brouillon — à relire par un enseignant » à l'écran). Les
> exercices sont **originaux, « dans le style de »** l'examen : aucun n'est un sujet officiel ni une annale.

- Ouvrir sur la TV : tuile **Apprendre** de l'accueil. Sur le téléphone : onglet **Apprendre**.
- Télécommande : flèches = se déplacer / changer de page, **OK** = valider / révéler l'étape suivante, **MENU** = lire
  la page à voix haute (épreuve blanche : terminer), **RETOUR** = revenir. Les touches chiffres saisissent les réponses
  numériques.

## 1. Parcours sur la TV

**Qui apprend ?** → profil (jusqu'à **6** élèves : prénom ou surnom, couleur d'avatar, classe ; rien d'autre) →
**accueil de l'élève** :

| Tuile | Rôle |
|---|---|
| **Préparer mon examen** (en tête) | examen de la classe (ou au choix) → matières → pour chacune : fiches « l'essentiel », **exercices type examen** corrigés, **épreuve blanche** ; **compte à rebours** « J-42 » jusqu'à la date saisie par l'élève (facultatif) ; matière absente → « Télécharger ce contenu » (écran Contenus) |
| Reprendre | la fiche et la page où l'élève s'est arrêté |
| Révisions | exercices ratés reproposés (révision espacée, § 6) |
| Mes récompenses | étoiles par fiche, badges, série de jours, notes des épreuves blanches |
| Ma classe | les matières installées pour la classe de l'élève |
| Tout le programme | cursus (maternelle, nursery, primaire fr/en, secondaire fr/en, licence) → niveau → matière → fiches |
| Contenus | packs installés (où, taille, version), packs refusés, installation depuis la clé USB ou le téléphone, suppression |

- **Fiche** : une page par bloc. Page 1 = titre, durée, statut de relecture, objectifs, prérequis, référence au
  programme. Un **exemple résolu** reste sur une page : **OK** révèle l'étape suivante, puis la réponse. Dernière page :
  étoiles, exercices de la fiche, auto-évaluation, fiche suivante.
- **Exercices** (un par écran) : QCM (choix mélangés, lettres A-F), vrai/faux, réponse numérique (pavé à l'écran ou
  touches chiffres ; virgule ou point acceptés, fractions « 3/4 »), appariement (gauche puis droite), réponse rédigée
  (corrigé modèle + critères, puis l'élève se note 0 / ½ / tout), problème en plusieurs questions. Correction immédiate :
  ✓/✗ (jamais la couleur seule), bonne réponse, corrigé commenté, méthode, erreurs fréquentes.
- **Épreuve blanche** : page de consignes (durée, parties, barème, mention « sujet d'entraînement original »), puis
  chronomètre, pas de correction avant la fin, ◀ ▶ pour circuler, MENU pour terminer (ou fin du temps). Résultat :
  **note /20** (au quart de point), points perdus par exercice, **fiches à revoir**, correction détaillée de chaque
  exercice, auto-notation des réponses rédigées (la note est recalculée).
- **Lecture à voix haute** (TextToSpeech fr-FR / en-GB) : automatique pour la maternelle et le primaire (pages et
  questions), sur demande (MENU) ailleurs ; les formules sont lues (« x au carré », « a sur b »).
- **Mode classe** : tuile sur l'écran des profils ; texte agrandi (+15 %), la leçon est pilotée depuis le téléphone
  (pages, révéler, réponses de la classe).
- Maternelle : pas de tuile « Préparer mon examen », tout est lu à voix haute, figures « combien ? » à compter.

### Lisibilité et focus (testé sur émulateurs)
Toutes les tailles sont exprimées sur une grille **1280 × 720** mise à l'échelle de l'écran (la densité est ignorée) :
une TV 1280×720 @160 dpi et une TV 1920×1080 @320 dpi affichent exactement la même page (vérifié par captures). Textes
≥ 18 px de la grille (corps 26-30), mise en page **uniquement en LinearLayout** (aucune vue posée sur une autre), zones
de défilement en `clipChildren` (le correctif de `HomeScreen.kt`), texte long réduit jusqu'à tenir dans la page. Pas de
toast système sur les pages (il masquait la ligne d'aide) : les messages remplacent la ligne d'aide quelques secondes.
Figures : chaque étiquette est placée en évitant les autres (graphiques, frises), halo clair sous les textes ; un test
vérifie qu'**aucune étiquette ne recouvre une autre** dans toutes les figures du contenu. Focus D-pad : premier élément
focalisé à chaque écran, ◀ ▶ se déplacent entre boutons tant qu'il y en a, sinon changent de page.

## 2. Couverture réelle du contenu livré (octobre 2026)

`tools/build-learn-packs --check` imprime ce tableau (les packs `bepc-svt`, `bepc-histoire-geo`, `bepc-english`, `bac-philosophie`, `gceal-maths`, `licence-droit` sont **nouveaux** : 3 fiches chacun, brouillon, à relire ; voir § 4b) (colonne « À vérifier » = notes de relecture + éléments `review`) :

| Pack | Examen | Niveau | Matière | Fiches | Exercices (dont auto-éval.) | Épreuves blanches | Illustrations | À vérifier |
|---|---|---|---|---|---|---|---|---|
| bac-maths | Baccalauréat | Tle | Mathématiques | 5 | 58 (25) | 1 | 5 | 9 |
| bac-philosophie | Baccalauréat | Tle | Philosophie | 3 | 36 (15) | 1 | 3 | 10 |
| bac-physique-chimie | Baccalauréat | Tle | Physique-Chimie | 5 | 59 (25) | 1 | 7 | 8 |
| bepc-english | BEPC | 3e | Anglais | 3 | 36 (15) | 1 | 3 | 2 |
| bepc-francais | BEPC | 3e | Français | 4 | 47 (20) | 1 | 4 | 8 |
| bepc-histoire-geo | BEPC | 3e | Histoire-Géographie-ECM | 3 | 36 (15) | 1 | 3 | 20 |
| bepc-maths | BEPC | 3e | Mathématiques | 5 | 59 (25) | 1 | 8 | 4 |
| bepc-pct | BEPC | 3e | Physique-chimie (PCT) | 4 | 48 (20) | 1 | 6 | 8 |
| bepc-svt | BEPC | 3e | SVT | 3 | 36 (15) | 1 | 3 | 14 |
| cep-francais | CEP | CM2 | Français | 5 | 59 (25) | 1 | 5 | 6 |
| cep-maths | CEP | CM2 | Mathématiques | 5 | 61 (25) | 1 | 10 | 5 |
| cep-sciences | CEP | CM2 | Sciences | 4 | 47 (20) | 1 | 6 | 9 |
| fslc-english | FSLC | Class 6 | English Language | 4 | 48 (20) | 1 | 5 | 5 |
| fslc-maths | FSLC | Class 6 | Mathematics | 4 | 52 (20) | 1 | 8 | 8 |
| fslc-science | FSLC | Class 6 | Science | 4 | 52 (20) | 1 | 6 | 8 |
| gceal-maths | GCE A Level | Upper Sixth | Mathematics | 3 | 36 (15) | 1 | 5 | 4 |
| gceol-biology | GCE O Level | Form 5 | Biology | 5 | 59 (25) | 1 | 12 | 6 |
| gceol-english | GCE O Level | Form 5 | English Language | 5 | 59 (25) | 1 | 6 | 6 |
| gceol-maths | GCE O Level | Form 5 | Mathematics | 4 | 48 (20) | 1 | 10 | 6 |
| licence-droit | — | L1 | Droit | 3 | 38 (15) | 1 | 2 | 11 |
| maternelle-decouverte | — | MS | Découverte du monde | 3 | 18 (0) | 0 | 30 | 5 |
| probatoire-francais | Probatoire | 1re | Français | 4 | 46 (20) | 1 | 4 | 13 |
| **Total (22 packs)** | | | | **88** | **1038 (425)** | **21** | **151** | **175** |

Chapitres traités — CEP maths : numération et 4 opérations, fractions, proportionnalité/pourcentages, mesures (FCFA,
durées) et géométrie, problèmes (partages, prix, vitesse moyenne, intérêts simples). CEP français : accords,
homophones, conjugaison, grammaire de la phrase, rédaction. CEP sciences : corps humain, hygiène et santé (paludisme,
eau, vaccination), plantes et environnement, matière et énergie. FSLC : mêmes thèmes en anglais (whole numbers,
fractions/decimals/percentages, measurement, word problems ; reading, tenses, grammar, composition ; human body,
health, environment, matter). BEPC maths : calcul littéral et identités remarquables, équations/inéquations/systèmes,
Pythagore, Thalès, fonctions linéaires et affines. BEPC français : subordonnées, voix passive, discours rapporté,
compréhension et expression écrite. BEPC PCT : circuits et loi d'Ohm, poids/masse/forces, réflexion et lentilles,
atomes/ions/pH. GCE O Level maths : sets and Venn diagrams, quadratic and simultaneous equations, trigonometry and
bearings, statistics and probability. English Language : comprehension, tenses and concord, reported speech,
composition (letters, argumentative essay), summary. Biology : cells, photosynthesis, digestion and enzymes, genetics,
ecology. Probatoire français : résumé, discussion, commentaire composé, dissertation. Bac maths (C/D) : étude de
fonction, ln/exp, intégrales, suites, complexes. Bac physique-chimie : mécanique newtonienne, oscillateur,
radioactivité, acides-bases et dosage, cinétique.

**Non couvert** (à produire) : histoire-géographie-ECM (aucun pack), SVT (BEPC et Bac D), anglais du BEPC, philosophie
et français du Bac A, GCE A Level, Physics/Chemistry O Level, probabilités (loi binomiale) et programme spécifique C
(similitudes, coniques, arithmétique) du Bac, statistiques et trigonométrie du BEPC, primaire hors CM2/Class 6,
secondaire hors classes d'examen, licence (le catalogue prévoit L1-L3 par filière : droit, économie…), vidéos.

### Points marqués « à vérifier » (principaux)
Chaque fiche liste ses doutes dans `reviewNotes` (visibles des relecteurs, jamais présentés comme des faits aux élèves ;
les blocs `review` s'affichent avec « ⚑ à vérifier »). En résumé :
- **Partout** : références exactes des programmes (MINEDUB, MINESEC, Cameroon GCE Board, Office du Bac) et **formats
  des épreuves** (durée, parties, barème) — toutes les épreuves blanches sont des propositions d'entraînement.
- Maths : conventions (π ≈ 3,14 ou 22/7, séparateur des milliers, hachurer ou colorier les solutions), vocabulaire
  (« applications affines »), place de certaines notions selon la série (intégration par parties en Tle D, suites
  adjacentes, similitudes et racines n-ièmes en C, papillon de Thalès en 3e, completing the square, sine/cosine rules).
- Sciences et santé : vocabulaire (gaz carbonique, liquéfaction…), vaccins du PEV et traitement de l'eau **à faire
  relire par un agent de santé**, valeurs arrondies (demi-vies, pKa, fréquence cardiaque), g = 10 ou 9,8.
- Langues : terminologie (COI/COS, impérative/injonctive), mise en page des lettres, comptage des mots et tolérance du
  résumé, règles de concord britanniques, backshift.
- Maternelle : quantité visée en MS (5-6 plutôt que 10), appariements qui demandent de lire les mots.
- Liste complète : `tools/build-learn-packs --check` puis les `reviewNotes` de chaque fiche (voir aussi le rapport de la
  branche).

## 3. Modèle de contenu (`android/core/src/main/kotlin/castbridge/core/learn/`)

**Catalogue** (`LearnCatalog`, toujours embarqué) : cursus `maternelle` (PS, MS, GS), `nursery` (Nursery 1-2),
`primaire` (SIL…CM2), `primary` (Class 1-6), `secondaire` (6e…Tle), `secondary` (Form 1-5, Lower/Upper Sixth),
`superieur` (L1-L3) ; examens `CEP`, `FSLC`, `BEPC`, `GCE-OL`, `PROBATOIRE`, `BAC`, `GCE-AL` (séries A, C, D) ; matières
(clé, libellé fr/en, couleur).

**Pack** (examen × matière, ou niveau × matière) → **chapitres** → **fiches** (`Lesson`) → **blocs**, plus les
**exercices** du pack et ses **épreuves blanches**.

### Format JSON (version `format: 1`)
Sources dans le dépôt : `content/learn/<pack id>/pack.json` + `lessons/<chapitre>.json` (+ `media/` facultatif).

```json
{"format": 1, "id": "bepc-maths", "version": 1, "title": "Mathématiques — BEPC", "lang": "fr",
 "cursus": "secondaire", "level": "3e", "subject": "maths", "exam": "BEPC", "series": [],
 "programRef": "Programme officiel de 3e (MINESEC) — à vérifier", "authors": ["…"], "status": "draft",
 "updatedAt": "2026-09-30",
 "chapters": [{"id": "bepc-maths-geo", "title": "Géométrie du triangle rectangle", "order": 3, "programRef": "…"}],
 "mockExams": [{"id": "bepc-maths-epreuve-1", "title": "Épreuve blanche…", "minutes": 120, "outOf": 20,
   "instructions": "…", "sections": [{"title": "Partie A — Activités numériques (8 points)", "exercises": ["…"]}]}]}
```

Fichier de leçons : `{"chapter": "<id>", "lessons": [ … ], "exercises": [ … ]}`.

| Élément | Champs |
|---|---|
| Fiche | `id`, `chapter`, `title`, `minutes`, `objectives`, `prerequisites` (fiches du même pack), `programRef`, `status` (`draft` / `reviewed` / `validated` — seul `validated` affiche « Contenu certifié »), `author`, `source`, `lang`, `readAloud`, `exercises` (fiche type : 3 application + 2 approfondissement + 1 examen), `selfCheck` (5 QCM à 4 choix), `reviewNotes`, `blocks` |
| Blocs | `heading` · `text` (md) · `key` (style `definition` `propriete` `formule` `retenir` `methode` `attention` `pieges` `objectifs`, `title`, `md`, `tex`) · `formula` (`tex`, `caption`) · `example` (`title`, `statement`, `steps` [{`md`, `tex`}] ≥ 2, `answer`, `figure`) · `illustration` (`figure`, `caption`, `alt` obligatoire) · `video` (`title`, `src`, `credit`, `license`) · `audio` (`text`, `lang`) · `exercise` (`ref`) · `more` (« Pour aller plus loin », `items`). Tout bloc peut porter `review: true`. |
| Exercice | `id`, `chapter`, `kind` (`mcq` `truefalse` `numeric` `matching` `open` `problem`), `tier` (`application` `approfondissement` `examen` `autoeval`), `difficulty` 1-3, `points` (barème indicatif), `prompt`, `tex`, `figure`, `choices`/`answer` (index), `answer` (booléen, nombre), `tolerance`, `unit`, `pairs`, `model` + `rubric` (réponse rédigée), `parts` (problème ; points = somme), `explanation` (corrigé commenté, obligatoire), `steps`, `method`, `mistakes`, `lesson` (fiche de renvoi), `source`, `review`, `reviewNote` |

Format des exercices = celui du quiz (choix, index de la bonne réponse, explication, source, `review`), étendu. Les
auto-évaluations s'exportent telles quelles dans la banque du quiz (`LearnQuiz`, niveaux connus du quiz, `review` exclu).

- **Markdown restreint** : paragraphes, `- ` puces, `1. ` listes, `**gras**`, `*italique*`, `$formule$` en ligne
  (affichée en Unicode : x², uₙ₊₁, √(x+1)). Pas de titres, liens, images ni HTML.
- **LaTeX minimal** (dessiné sans bibliothèque, `TexLayout`) : `\frac`, `^`, `_`, `\sqrt[n]{}`, `\vec`, `\overline`,
  `\widehat`, `\text`, `\mathbb{R}`, `\left( \right)`, symboles (× ÷ · ± ≤ ≥ ≠ ≈ ∞ → ⇒ ⇔ ∈ ∪ ∩, lettres grecques,
  `\lim \ln \sin…`, `\sum \int`). Toute autre commande est une erreur de validation.
- **Illustrations** (`Figure`, dessinées en Canvas sur TV et téléphone à partir des mêmes primitives `Scene`) :
  `shapes` (lignes, flèches, cercles, rectangles, polygones, textes, marques d'angle, chemins SVG), `plot` (courbes
  y = f(x) avec un petit évaluateur d'expressions, points, segments, grille, axes), `timeline` (frise, étiquettes
  sans chevauchement), `bars`, `count` (objets à compter), `svg` (chemins M L H V C S Q T A Z). Quelques centaines
  d'octets par figure, aucune image.
- **Vidéo** : `src` = `library:<nom du fichier>` (bibliothèque de la TV / clé USB) ou URL fournie par un
  administrateur, lue par le lecteur libVLC existant. **Aucune vidéo n'est livrée** (champs vides) ; jamais de
  téléchargement depuis YouTube ou une autre plateforme sans droit.

### Validation (`LessonValidator`, lancé par les tests, l'outil de build et la TV avant d'activer un pack)
ids uniques préfixés par l'id du pack ; cursus, niveau, matière, examen et série connus et cohérents ; langue du
cursus ; prérequis existants et sans cycle ; Markdown et formules analysables ; textes ≤ 650 caractères par bloc
(une page TV), étapes ≤ 320 ; QCM : 2-6 choix distincts, index valide, pas de choix dépendant de l'ordre ; numérique :
valeur finie, tolérance ≥ 0 ; appariement 2-6 paires sans doublon ; réponse rédigée avec corrigé ; problème ≥ 2
questions ; corrigé obligatoire ; figures dessinables, couleurs connues, textes dans le cadre ; épreuve blanche : total
des points = 20, aucun exercice `review` ; **fiche type** des packs d'examen (objectifs, « l'essentiel », 2 exemples
résolus, 3/2/1 exercices, 5 auto-évaluations). Tests du contenu : chaque bonne réponse est notée juste par le moteur de
notation, chaque mauvaise fausse ; pas d'étiquettes superposées ; aucune vidéo embarquée.

## 4. Packs de contenu et stockage

Le contenu embarqué **dans l'app** reste minimal ; le reste vient d'un **stockage** (clé USB, mémoire de la TV) ou,
plus tard, **en ligne**.

- **Socle embarqué** : packs listés dans `content/learn/embedded.txt` (aujourd'hui : maternelle + mathématiques de
  chaque examen), zippés dans les ressources de `:core` à la compilation (`gradle :core:embedLearnPacks`, automatique).
  **~130 ko** ; budget testé : **5 Mo compressés** au maximum (`LearnContentTest.embeddedBudget`) et, depuis les lots, **3 Mo pour Apprendre** (§ 4b). C'est le **socle de démonstration** : le reste du catalogue arrive par lots.
- **Pack** = zip `<id>-v<version>.learn.zip` :
  `manifest.json` (id, version, titre, langue, cursus, niveau, matière, examen, statut, taille, nombre de fiches /
  exercices / épreuves, date, **sha256 et taille de chaque fichier**, `signature` Ed25519 facultative) + `pack.json` +
  `lessons/*.json` + `media/` (images webp/svg compressées, audio facultatif). **Jamais de vidéo dans un pack.**
  Construction reproductible (entrées triées, dates fixes). Les 22 packs pèsent de 11 à 33 ko chacun (~500 ko au total) ; ils sont regroupés en lots (§ 4b).
- **Vérification avant activation** (`PackReader`) : chemins sûrs, limites de taille (64 Mo décompressés, 2000
  fichiers), chaque fichier du manifest présent avec la bonne taille et le bon **sha256**, aucun fichier non déclaré,
  **signature Ed25519** exigée dès qu'une clé de confiance est configurée (`PackSignatures` ; clé brute 32 octets en
  base64, signature du manifest sans son champ `signature`), puis contenu lu et validé. Un pack altéré est **refusé**
  (testé : octet modifié, fichier ajouté, supprimé, chemin `..`, signature d'une autre clé, pack non signé exigé) et
  signalé dans l'écran Contenus.
- **Où la TV cherche** (`LessonSource` multi-sources, `LearnLibrary`) : 1) `CastBridge/Packs/` de chaque volume, **clé
  USB en premier** : dossier de l'app sur la clé (`Android/data/castbridge.receiver/files/CastBridge/Packs/`, qu'un PC
  peut remplir) et, si la TV le permet, `CastBridge/Packs/` à la **racine** de la clé ; puis la mémoire de la TV ;
  2) le socle embarqué ; 3) le futur serveur (`RemoteLessonApi`). Même pack à plusieurs endroits : la **version la plus
  haute** gagne. Un pack sur la clé s'utilise sans copie ; « Copier les packs de la clé sur la TV » le rend disponible
  sans la clé.
- **Installer** (`PackInstaller`) : pack vérifié, écrit dans un fichier temporaire puis renommé, anciennes versions du
  même pack supprimées, destination = premier volume (clé d'abord) qui garde **1 Go libre après l'installation** (règle
  des transferts téléphone ↔ TV, `TransferRule`). Sources : téléphone (`POST /api/learn/packs/install`, corps = zip ≤ 4
  Mo), fichier `.learn.zip` envoyé dans la bibliothèque par l'échange de fichiers (« Installer les packs reçus »), clé
  USB, serveur (à venir, téléchargement reprenable par la connexion de la TV ou la passerelle Bluetooth du téléphone).
  **Supprimer** un pack libère la place ; les progrès des élèves sont gardés.
- **Outil** : `tools/build-learn-packs [dossier]` (ou `gradle :core:buildLearnPacks`) valide tout puis écrit les zips et
  `catalog.json` ; `tools/build-learn-packs --check` valide et imprime la couverture.

## 4b. Lots : une classe = un lot (hors ligne d'abord)

**Règle du propriétaire.** La TV ne porte que **~10 Mo au maximum** de données Apprendre + Quiz. Le téléphone en gère jusqu'à
**100 Mo** : il les télécharge du serveur à la première synchronisation (il a plus de place et une connexion plus fiable) puis
les livre à la TV. Les mises à jour se font **par lots homogènes** (un lot = toutes les données d'une classe). **Le mode différé
(hors ligne) est prioritaire : la TV n'a le plus souvent pas d'Internet.** Conséquences, vérifiées par les tests :

- tout écran et tout exercice d'Apprendre fonctionne avec les seuls lots que la TV détient : **aucun appel réseau, aucun
  indicateur d'attente du réseau** (le code `learn/` et `lots/` n'utilise ni `java.net` ni socket : test) ;
- le téléphone télécharge dès qu'il a Internet et livre à la TV dès que les deux peuvent se parler (c'est le rôle du cadre
  commun des lots : `LotSync`, `LotPlanner`, `LotPush`… ; Apprendre n'en refait rien et ne dépend que du contrat
  `castbridge.core.lots.LotApi`) ;
- les deux appareils affichent la **fraîcheur** (« données du 12 sept. ») et ne disent jamais qu'Internet est requis ;
- la progression des élèves est locale à chaque appareil, survit aux mises à jour de lots et se **fusionne** (ci-dessous).

### Tableau des lots (`content/learn/scopes.txt` : l'UNIQUE source)
Les identifiants de packs (dossiers de `content/learn/`) restent la source de vérité ; `scopes.txt` les rattache à un lot
(`scope | titre | packs`). Un pack est dans un seul lot ; un lot est autonome (prérequis internes : vérifié à la construction).

| Scope | Lot | Packs | Fiches | Taille (v1) |
|---|---|---|---|---|
| `maternelle` | Maternelle | maternelle-decouverte | 3 | 11,4 Ko |
| `cm2` | CM2 – CEP | cep-francais, cep-maths, cep-sciences | 14 | 76,8 Ko |
| `class6` | Class 6 – FSLC | fslc-english, fslc-maths, fslc-science | 12 | 67,5 Ko |
| `3e` | 3e – BEPC | bepc-english, bepc-francais, bepc-histoire-geo, bepc-maths, bepc-pct, bepc-svt | 22 | 118,3 Ko |
| `form5` | Form 5 – GCE O Level | gceol-biology, gceol-english, gceol-maths | 14 | 83,8 Ko |
| `1ere` | Première – Probatoire | probatoire-francais | 4 | 30,4 Ko |
| `tle-cd` | Terminale C/D – Bac | bac-maths, bac-physique-chimie | 10 | 57,6 Ko |
| `tle-commun` | Terminale (toutes séries) – Philosophie | bac-philosophie | 3 | 15,3 Ko |
| `upper-sixth` | Upper Sixth – GCE A Level | gceal-maths | 3 | 12,5 Ko |
| `droit-l1` | Licence 1 – Droit | licence-droit | 3 | 14,7 Ko |

Total **488 Ko** pour 10 lots (le plus gros : 118 Ko ; plafond par lot : **3 Mo**). Les autres classes (cp…5e, 4e, 2nde,
Form 1-4, Class 1-5, Lower Sixth, tle-a, L2/L3, économie) n'ont pas encore de contenu : leurs scopes existent déjà dans
l'échelle `LearnScopes` pour que le planificateur de la TV sache les ordonner le jour venu.

### Format d'un lot et construction
- Un lot = **un zip** `learn-<scope>-v<version>.lot.zip` : `lot.json` (index : scope, titre, **version**, **date** des données,
  empreinte du contenu, pour chaque pack son sha256 et pour **chaque fiche une empreinte de contenu**) + `packs/<id>-v<n>.learn.zip`
  (les packs existants, stockés tels quels : chacun garde son manifest, ses sha256 et sa signature). Reproductible.
- **La version ne change que si le contenu change** : `content/learn/lots.json` enregistre, par lot, version + empreinte + date.
  Le build **échoue** si un lot a changé sans `--update` (qui incrémente la version des seuls lots modifiés et réécrit
  `lots.json`) : le registre versionné est toujours cohérent avec le contenu (test).
- Outil : `tools/build-learn-lots [dossier]` (ou `gradle :core:buildLearnLots`, `-Pupdate` pour incrémenter) écrit les lots,
  `lots-catalog.json` (liste `LotMeta`, feature `learn`, prête pour la publication serveur) et imprime la **taille de chaque lot et le
  total**. Il **échoue** si un lot dépasse **3 Mo** (la TV doit toujours pouvoir en garder plusieurs), si un pack n'a pas de lot ou si
  un prérequis sort du lot. `tools/build-learn-lots --review` régénère `docs/LEARN-REVIEW.md` (ci-dessous).
- Empreinte d'une fiche : fiche + exercices et auto-évaluations qu'elle liste, **sans** statut de relecture, auteur ni notes : un
  enseignant qui approuve une fiche ne la fait pas passer en « mise à jour » chez les élèves.

### Côté appareil : `LearnLotConsumer` (feature `learn`, cœur pur, `LotConsumer` du contrat)
- `install(meta, fichier)` : vérifie la taille, le **sha256**, `minAppVersion`, puis tout le lot (`LotReader` : entrées sûres, limites,
  chaque pack vérifié par `PackReader` + `LessonValidator`, empreintes des fiches = celles de l'index) ; écrit dans un dossier de
  travail puis le **renomme** (atomique) en `<racine>/<scope>/v<version>/{lot.zip, meta.json}` ; **l'ancienne version n'est supprimée
  qu'après** : un lot corrompu, tronqué, interrompu ou falsifié ne fait rien perdre (retour arrière = ne rien changer).
  Une version est immuable : même version avec un autre contenu, ou version plus ancienne que l'installée = refus.
- `remove`, `installed()` (liste de `LotMeta`), `installedAll()` (avec la date des données), `diskBytes()`, `lessonHash()`.
- Racine par défaut : `filesDir/lots/learn` sur la TV comme sur le téléphone (le cadre commun installe les lots reçus avec ce consommateur).
- **Lecture** : `LearnLotSource` expose les lots installés comme une `LessonSource`, placée **avant** le socle embarqué dans
  `LearnLibrary` (même pack : la version la plus haute, puis la première source). `LearnLotCatalog` = vue « classes disponibles,
  fiches par classe » (lots installés + packs de démarrage).

### Budget de la TV et priorité
- **Socle (starter) de l'APK TV** = les packs de `content/learn/embedded.txt` : maternelle + mathématiques de CM2, Class 6, 3e, Form 5
  et Tle C/D, soit **un pack de démonstration par cycle, 131 795 octets (0,13 Mo)** pour un plafond de 3 Mo (test :
  `starterStaysWithinItsShareOfTheTvBudget`) ; avec le socle du Quiz (≈ 2 Mo) on reste sous 5 Mo, donc **au moins 5 Mo restent pour les lots
  poussés** sur les 10 Mo (`LotBudget.TV_MAX_BYTES`). Tous les autres packs sont **hors de l'APK** et du jar de `:core`
  (`embedLearnPacks` ne zippe que `embedded.txt`) ; le téléphone reçoit le catalogue complet par lots.
- `learnPriority(classe)` / `learnPriority(profils)` (pures) : les scopes classés pour le planificateur de la TV : **la classe de l'élève
  d'abord**, puis l'année au-dessus, celle en dessous, deux au-dessus… dans son sous-système (fr / en / licence) ; plusieurs élèves =
  fusion alternée (la classe de chacun avant le deuxième choix de n'importe qui). Classe inconnue = liste vide.

### Progression : survit aux mises à jour, fusion téléphone ↔ TV
- `LessonState.hash` garde l'empreinte de la version de la fiche que l'élève a lue ; `LearnProgress.lessonUpdated()` : si l'empreinte du lot a changé,
  **le score reste** et la fiche est signalée « ↻ Fiche mise à jour depuis ta dernière lecture » (le signalement disparaît quand l'élève
  a relu la nouvelle version jusqu'à la fin). Un fichier de progression d'avant les lots se charge sans signalement.
- **Règle de fusion** (`LearnMerge`, répétable et indépendante de l'ordre, ne perd rien) : même élève = même prénom ; **dernière écriture
  gagne par exercice** pour l'état de révision (dernier résultat, boîte de Leitner, échéance = ceux de la tentative la plus récente) et le
  **meilleur score est gardé** (`ExerciseState.best`, nombre de réussites et d'essais = le plus grand des deux) ; fiche : vue / terminée si
  l'une des deux, étoiles et temps = le plus grand, page et empreinte = celles de la visite la plus récente ; épreuves blanches et badges :
  union ; série de jours : celle du jour le plus récent ; événements de télémétrie : restent locaux. Identifiants d'élèves en collision :
  renumérotés. **Pas encore branché** : le téléphone ne stocke pas encore de progression (lecture individuelle sans suivi) ; la fusion est prête
  et testée dans `:core` pour le jour où les deux appareils se rencontrent.

### Écrans
- **CastBridge-TV** : écran Contenus : « Mes classes sur cette TV » (version, taille, date des données ; « contenu de démarrage de l'app » pour
  le socle), plus de « en ligne : bientôt » ni de « Télécharger ce contenu » : une matière absente dit « à envoyer depuis le téléphone ».
- **CastBridge** (téléphone), onglet Apprendre : **Mes classes** : par classe, version installée, taille, « données du … », « mise à jour disponible »,
  « sur la TV », et l'appel à l'action « Télécharger 3e – BEPC : 4,2 Mo » quand la classe n'est pas encore sur le téléphone ; une classe
  sélectionnée filtre la liste des packs ; tout se lit hors ligne. Le bouton appelle `LearnLotsHooks.download` (à brancher par le cadre
  commun / l'écran Données) ; tant qu'il ne l'est pas, le texte renvoie à « Données ». `LearnLotsHooks.catalog` / `onTv` : le catalogue du
  serveur gardé au dernier lien (affiché hors ligne) et les classes que la TV a déclarées.

### Relecture par lot (`docs/LEARN-REVIEW.md`, généré)
`LearnTool review` (ou `tools/build-learn-lots --review`) écrit, **lot par lot et pack par pack**, les fiches non certifiées et chaque point
signalé « à vérifier » (notes de relecture, blocs et exercices `review`) : le propriétaire fait relire par lot. Un test vérifie que le
fichier est à jour. **Rien ne devient « relu » ni « certifié » sans un humain** : le statut de chaque pack/fiche reste `draft`.

### Contenu ajouté (octobre 2026, tout `draft`, programmes officiels du Cameroun comme référence, texte original)
6 packs de 3 fiches, ~21 exercices notés + 15 auto-évaluations, 1 épreuve blanche /20 chacun : **SVT 3e** (reproduction et planification
familiale, IST/VIH/paludisme, roches et volcanisme), **HG-ECM 3e** (symboles et institutions, géographie du Cameroun, de la colonisation à la
réunification), **anglais 3e** (present perfect / simple past, conditionnels, compréhension), **philosophie Tle** (méthode de la dissertation,
liberté, conscience), **GCE A Level Pure Mathematics** (dérivation, intégration, suites), **Licence 1 Droit** (règle de droit et hiérarchie des normes,
droit objectif/subjectif, personnalité juridique). Les faits douteux sont en `review` / `reviewNotes` (dates, attributions d'auteurs, institutions,
âge de la majorité…) ; aucun texte de manuel n'est repris. **Manques par classe** : voir § 9 ; encore sans contenu : SIL/CP-CM1, 6e-4e, 2nde, Form 1-4,
Class 1-5, Lower Sixth, Terminale A, L2-L3 (et économie), le français/anglais de plusieurs examens, programme spécifique C du Bac.

## 5. Serveur bridge.sti-cm.com — routes à prévoir

La TV fonctionne sans serveur. Le serveur hébergera, éditera et fera relire le contenu :

| Route | Rôle |
|---|---|
| `GET /api/v1/learn/catalog?lang=&level=&exam=&since=` | liste des packs publiés au format `catalog.json` (id, version, titre, examen, niveau, matière, taille, compteurs, `url`), incrémental par `since` |
| `GET /api/v1/learn/packs/{id}/{version}` | le zip, avec **`Range`** (reprise) et `ETag` = sha256 du zip |
| `GET /api/v1/learn/packs/{id}/{version}/manifest` | le manifest seul (contrôle avant téléchargement : taille, place libre) |
| `GET /api/v1/learn/keys` | clés publiques Ed25519 de signature (id → clé) ; la TV les épingle à la mise à jour de l'app |
| `GET/POST/PUT /api/v1/learn/drafts/{pack}/…` | édition par les enseignants (fiches, exercices, figures) au format JSON ci-dessus, validation serveur = `LessonValidator` |
| `POST /api/v1/learn/drafts/{pack}/review` | circuit de relecture : `draft` → `reviewed` (relecteur) → `validated` (inspecteur/référent) ; commentaires par bloc ; seul `validated` = « contenu certifié » |
| `POST /api/v1/learn/packs/{id}/publish` | construit le zip (même code que `PackBuilder`), le **signe** (clé hors ligne ou HSM), incrémente la version |
| `POST /api/v1/learn/events` | réception facultative de la télémétrie (§ 6), anonymisée, avec accord des parents |
| `GET /api/v1/learn/videos?lesson=` | vidéos **dont les droits sont acquis**, en streaming (jamais dans les packs) |

## 6. Progression, révisions, télémétrie

Stockées sur la TV dans `files/learn/progress.json` (écriture atomique ; fichier abîmé = départ à zéro, jamais de
plantage). Données : profils (id local `p1`…, prénom ≤ 20 caractères — pas de numéro, avatar, classe, examen, date
d'examen), par fiche (page, vue, terminée, étoiles, temps), par exercice (essais, réussites, dernière réponse, boîte de
révision, échéance), épreuves blanches (note, durée), badges, série de jours, temps par matière, dernière position.

- **Étoiles** : 1 = fiche lue jusqu'au bout ; 2 = ≥ 60 % des exercices de la fiche (et de l'auto-évaluation) justes au
  dernier essai ; 3 = ≥ 90 % avec au moins la moitié tentés.
- **Révisions espacées** (Leitner) : un exercice raté entre en boîte 1 (revient le lendemain) ; réussi en révision il
  monte (2, 4, 8, 16 jours) et sort après la boîte 4 ; réussi du premier coup, il n'entre jamais.
- **Badges** : première fiche, 5 et 20 fiches, sans faute, 3 et 7 jours de suite, épreuve blanche ≥ 10 et ≥ 15/20,
  10 révisions réussies.
- **Temps** : compté page par page, 5 min au plus par page (TV restée allumée).
- **Tableau de bord des parents** (téléphone, `GET /api/learn/dashboard`) : par élève, temps passé, fiches terminées,
  étoiles, série, révisions en attente, badges, compte à rebours, **par matière** temps / fiches / taux de réussite,
  dernières épreuves blanches.

### Événements de télémétrie (noms stables, `LearnProgress.EVENTS`)
Gardés sur la TV (500 derniers), lisibles par `GET /api/learn/events?since=` pour un envoi futur au serveur (pas de
`docs/TELEMETRY.md` sur `origin` au moment de l'écriture : ils sont documentés ici).

| Nom | Données |
|---|---|
| `profile_created` | `level` |
| `lesson_view` | `pack`, `lesson`, `subject` (première ouverture) |
| `lesson_complete` | `pack`, `lesson`, `timeMs` |
| `exercise_result` | `pack`, `exercise`, `lesson`, `subject`, `correct`, `points`, `max`, `attempt` |
| `review_result` | `pack`, `exercise`, `correct`, `box` |
| `mock_exam_result` | `pack`, `mock`, `score`, `outOf`, `durationMs` |
| `badge_earned` | `badge` |
| `pack_installed` | `pack`, `version` |

Chaque événement : `name`, `at` (ms), `profile` (id local, jamais le prénom), `data`.

## 7. API de la TV (PIN, pour l'app du téléphone)

| Route | Rôle |
|---|---|
| `GET /api/learn` | écran affiché (fiche, page, étape, exercice en cours avec ses choix dans l'ordre affiché) + profils |
| `POST /api/learn/open[?profile=]` | ouvre « Apprendre » (l'écran de la TV doit être visible, comme le quiz) |
| `POST /api/learn/cmd?action=` | `next` `prev` `ok` `back` `speak` · `choice&value=<n° affiché>` `bool&value=` `number&value=` `self&value=0|0.5|1` · `teacher&value=on|off` · `lesson&pack=&lesson=[&page=]` |
| `GET /api/learn/dashboard[?profile=]` | tableau de bord |
| `GET /api/learn/packs` | packs trouvés (où, version, taille) et refusés (raison) |
| `POST /api/learn/packs/install` | corps = zip ; `POST /api/learn/packs/import?name=` (fichier reçu dans la bibliothèque) ; `POST /api/learn/packs/remove?id=` |
| `GET /api/learn/events?since=` | télémétrie |

Téléphone (onglet **Apprendre**) : **Leçons** (packs embarqués lus sur le téléphone : fiches, étapes, figures et
formules dessinées comme sur la TV, exercices corrigés ; pas de progression enregistrée sur le téléphone), **Piloter la
TV** (ouvrir, pages, révéler, lire à voix haute, mode classe, répondre aux QCM / vrai-faux / nombres / auto-notation,
ouvrir une fiche précise), **Parents** (tableau de bord, contenus de la TV).

## 8. Technique, tests, tailles

- `:core` (JVM, testé) : `LearnCatalog`, `LessonModel`, `LessonJson`, `LessonValidator`, `Markdown`, `Formula`
  (`Tex`, `TexLayout`), `Figure`, `Scene` (+ `SvgPath`), `Expr`, `Packs` (manifest, `PackReader`, `PackBuilder`,
  `PackSignatures`), `LessonSource` (`EmbeddedLessonSource`, `DirectoryLessonSource`, `LearnLibrary`, `PackInstaller`,
  `RemoteLessonApi`), lots (`LearnScopes`, `LearnLots`, `LearnLotConsumer`, `LearnMerge`, `LearnReview`, `lots/LotApi`), `Marking` (`Answer`, `Mark`, `Shuffle`, `LessonDeck`, `MockExamSession`), `Progress`
  (`LearnProgress`, `LearnStore`), `LearnApi`, `LearnQuiz`, `LearnTool` (CLI check/build/embed).
- `:receiver` : `LearnActivity` (profils, accueil, examens, programme, packs, récompenses, contenus), `LearnReader`
  (lecteur, séries, épreuve blanche, correction), `LearnExercise`, `LearnViews` (`LearnStyle`, `FigureView`,
  `FormulaView`), `LearnHub` (sources, progression, API) ; accroches : tuile de l'accueil, entrée du manifest,
  extension d'API dans `TvService`. Vues Android classiques, aucune image ni bibliothèque ajoutée.
- `:sender` : `LearnScreen` (Compose) + un onglet dans `MainActivity`.
- Tests : `LearnLogicTest` (formules, Markdown, expressions, frises sans chevauchement, graphiques, SVG, packs :
  aller-retour, altération, signature, bibliothèque et versions, installateur et place libre, notation, pages,
  épreuve blanche, profils, étoiles, révisions, séries, tableau de bord, persistance, télémétrie, API) et
  `LearnContentTest` (tout le contenu : validation, couverture par examen, réponses cohérentes, formules et figures,
  prérequis sans cycle, pas de vidéo, pas d'étiquette superposée, export vers le quiz, packs reproductibles, budget
  embarqué) et `LearnLotsTest` (lots : table, construction reproductible, plafond 3 Mo, versions, installation atomique, retour arrière, lot corrompu/falsifié, usage sans réseau, priorité, budget du socle, progression et fusion, rapport de relecture). `gradle :core:test` vert (931 tests).
- Essais sur émulateurs Android TV (API 34) **1280×720 @160 dpi** et **1920×1080 @320 dpi** : création de profil à la
  télécommande, accueil, examen BEPC, fiches (définitions, formules, exemples révélés pas à pas), figures (graphiques,
  circuit, optique, Punnett, relèvements, barres, comptage), séries d'exercices (QCM, pavé numérique), épreuve blanche
  (consignes, chrono, fin, résultat /20, correction), maternelle, installation de packs et refus d'un pack altéré par
  l'API, pilotage à distance par l'API (`/api/learn/cmd`).
- **Tailles d'APK TV** mesurées (origin/feat/ssh → feat/learn, mêmes outils) : release non signé armeabi-v7a
  24 044 469 → 24 288 229 octets (**+238 ko**), arm64-v8a 27 637 593 → 27 881 353 (+238 ko) ; debug armeabi-v7a
  28 279 314 → 29 100 757 (+802 ko, code non réduit). Le socle embarqué compte pour ~130 ko (zips de packs,
  déjà compressés). Si le contenu grossit, il part en **packs par niveau/examen** (clé USB, téléphone, serveur),
  pas dans l'APK : budget testé 5 Mo, contrainte de l'app < 50 Mo de contenu.

## 9. Reste à faire

- **Relecture pédagogique** de tout le contenu par des enseignants des deux sous-systèmes (statut `reviewed` puis
  `validated`), vérification des programmes et des formats d'épreuves officiels, relecture santé par un agent de santé.
- **Production à grande échelle** : les autres matières (HG-ECM, SVT, anglais/français, philosophie), les autres
  classes (du SIL/Class 1 à la Tle/Upper Sixth), la licence ; outil d'édition sur le serveur (§ 5) plutôt que du JSON à
  la main ; illustrations plus riches (cartes du Cameroun simplifiées, frises d'histoire) avec le format existant.
- **Vidéos** : acquisition des droits (producteurs locaux, enseignants), hébergement serveur ou clé USB ; le lecteur est
  prêt (`library:` ou URL).
- Serveur : catalogue, téléchargement reprenable, signature des packs (clé à épingler dans l'app), télémétrie avec
  accord parental.
- À valider sur la vraie TV (GaiaOS / Amlogic 32 bits) : moteur TextToSpeech présent et voix française/anglaise,
  lecture de `CastBridge/Packs` à la racine de la clé, fluidité, clavier de la TV pour le prénom, mémoire.
- Téléphone : progression locale pour la lecture individuelle (la fusion avec celle de la TV est prête : § 4b), branchement des boutons « Télécharger » sur le cadre commun des lots.
- Lots : vérifier sur la vraie TV (32 bits, peu de RAM) la lecture depuis `lot.zip` et l'ordre de rangement quand le budget de 10 Mo est atteint ; signature Ed25519 des packs d'un lot (clé à épingler).
