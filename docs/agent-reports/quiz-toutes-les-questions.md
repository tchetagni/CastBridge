# Quiz embarqué : TOUTES les questions, réservables par question (CastBridge-TV) — rapport

Décisions du propriétaire (2026-10-03) : « phase d'essai : toutes les questions disponibles » ; « ce sont les questions qui sont réservables, pas les niveaux ».
Branche `claude/cloud-quiz-all` (base `integration/agents`), aucune Pull Request. Rien dans `android/sender`, ni dans `QuizLevelAvailability`, `QuizActivity`, `QuizHub`. Aucune action sur un appareil ou un serveur.

## Résultat
- **217 494 questions** embarquées (93 lots du catalogue `content/quiz/lots/catalog-lots.json`, **28 niveaux** : CP, CE1, CE2, CM1, CM2, 6e–3e, 2nde, 1re, Tle, Form 1/2/3/5, Lower/Upper Sixth, Class 1–6, L1, L2, L3, Culture générale). Chaque id une seule fois ; statut « review » conservé (0 approuvée), texte non modifié. « Form 4 » reste un alias de Form 3 + Form 5 (inchangé).
- **Aucun niveau réservé en bloc** : Tle, L1, L2, L3 ont des questions libres et réservables. Plus de `reservedNotEmbedded` / `reservedLevels` dans l'index. `content/quiz/families.json` n'est plus lu par le script (la réservation est par question).
- Fichiers : `android/core/src/main/resources/castbridge/quiz/embedded/<niveau>/<lot>[.N].json` (libres), `.../embedded-reserved/<niveau>/<lot>[.N].json` (réservables), `embedded/index.json`. **542 fichiers** (la taille maximale d'un fichier est de 210 ko ; un fichier contient au plus 500 questions, un lot plus gros est découpé en parties égales `.2`, `.3`…). Format compact identique à l'ancien (12 colonnes) ; en-tête de fichier : `lot`, `family`, `part`/`parts`.
- `index.json` (v2, 65 ko) : par niveau `count` (total), `freeCount`, `reservedCount`, `files` / `reservedFiles` (nom, lot, filière, nombre, octets), `lots` ; plus `seed`, `reservedSeed`, `reservedRatio`.

## Règle de marquage (`tools/quiz-bank/build_embedded.py`, graine `castbridge-quiz-reserved-v1`)
Par lot : strates = difficulté (région × difficulté pour la culture générale) ; dans chaque strate, tri par SHA-256(graine + `|` + id) ; les K premières sont réservables, K = round(0,30 × effectif cumulé) − round(0,30 × cumul précédent) (reste reporté : total du lot exactement round(0,30 N), chaque strate à ±1 question).
- Mesuré : **30,00 % ± 0,02 point par niveau** (tableau ci-dessous) ; par lot ±1 point, sauf un lot de 16 questions (5/16 = 31,25 %, soit ±1 question : le test accepte « ±1 point OU ±1 question »).
- **Stabilité — limite honnête** : le marquage est une fonction pure (graine, contenu du lot) : deux générations identiques sont identiques octet pour octet. Si un lot GRANDIT, les questions loin du seuil gardent leur marque ; seules celles dont le rang de hachage est voisin de 30 % peuvent basculer (simulation du test : lot à 85 % puis complet, ≤ 3 % des ids marqués changent). Une stabilité STRICTE n'existe pas avec un quota exact sur contenu variable : **avant toute publication, geler la liste des ids réservables** (fichier versionné) — voir « Conséquence pour le serveur ».

## Totaux par niveau
| niveau | total | libres | réservables | % rés. | fichiers libres / rés. | octets libres | octets rés. |
|---|---|---|---|---|---|---|---|
| CP | 5 335 | 3 734 | 1 601 | 30,01 | 8 / 4 | 739 687 | 316 860 |
| CE1 | 5 394 | 3 776 | 1 618 | 30,00 | 8 / 4 | 799 735 | 344 801 |
| CE2 | 5 370 | 3 759 | 1 611 | 30,00 | 8 / 4 | 842 049 | 361 875 |
| CM1 | 5 353 | 3 747 | 1 606 | 30,00 | 8 / 4 | 860 103 | 369 200 |
| CM2 | 6 528 | 4 570 | 1 958 | 29,99 | 10 / 4 | 1 027 644 | 440 062 |
| 6e | 5 352 | 3 746 | 1 606 | 30,01 | 8 / 4 | 867 093 | 370 962 |
| 5e | 5 374 | 3 762 | 1 612 | 30,00 | 8 / 4 | 845 803 | 363 150 |
| 4e | 5 385 | 3 769 | 1 616 | 30,01 | 8 / 4 | 865 498 | 370 920 |
| 3e | 5 571 | 3 900 | 1 671 | 29,99 | 8 / 4 | 990 944 | 423 889 |
| 2nde | 8 307 | 5 814 | 2 493 | 30,01 | 17 / 12 | 1 766 089 | 761 931 |
| 1re | 9 527 | 6 669 | 2 858 | 30,00 | 18 / 12 | 1 910 813 | 819 731 |
| Tle | 12 998 | 9 098 | 3 900 | 30,00 | 25 / 16 | 2 563 479 | 1 101 391 |
| Form 1 | 5 390 | 3 773 | 1 617 | 30,00 | 8 / 4 | 790 167 | 337 706 |
| Form 2 | 5 358 | 3 751 | 1 607 | 29,99 | 8 / 4 | 757 887 | 324 552 |
| Form 3 | 5 369 | 3 758 | 1 611 | 30,01 | 8 / 4 | 780 851 | 335 781 |
| Form 5 | 8 533 | 5 972 | 2 561 | 30,01 | 16 / 12 | 1 561 612 | 668 196 |
| Lower Sixth | 11 322 | 7 926 | 3 396 | 29,99 | 21 / 12 | 2 079 651 | 891 692 |
| Upper Sixth | 11 724 | 8 205 | 3 519 | 30,02 | 21 / 13 | 2 151 981 | 926 640 |
| Class 1 | 5 294 | 3 706 | 1 588 | 30,00 | 8 / 4 | 687 671 | 296 011 |
| Class 2 | 5 372 | 3 760 | 1 612 | 30,01 | 8 / 4 | 747 938 | 319 003 |
| Class 3 | 5 359 | 3 751 | 1 608 | 30,01 | 8 / 4 | 767 843 | 328 744 |
| Class 4 | 5 379 | 3 765 | 1 614 | 30,01 | 8 / 4 | 775 771 | 328 441 |
| Class 5 | 5 397 | 3 778 | 1 619 | 30,00 | 8 / 4 | 791 599 | 340 811 |
| Class 6 | 5 369 | 3 758 | 1 611 | 30,01 | 8 / 4 | 779 308 | 334 479 |
| L1 | 21 879 | 15 316 | 6 563 | 30,00 | 33 / 16 | 5 098 638 | 2 187 825 |
| L2 | 20 326 | 14 228 | 6 098 | 30,00 | 30 / 14 | 5 312 517 | 2 286 135 |
| L3 | 8 414 | 5 890 | 2 524 | 30,00 | 14 / 7 | 2 377 171 | 1 019 235 |
| Culture générale | 6 515 | 4 561 | 1 954 | 29,99 | 10 / 5 | 1 285 116 | 555 456 |
| **Total** | **217 494** | **152 242** | **65 252** | 30,00 | 542 fichiers | 40 824 658 | 17 525 479 |

## Tailles
| | octets |
|---|---|
| Embarqué libre (`embedded/`, JSON non compressé, index compris) | 40 889 419 |
| Réservable (`embedded-reserved/`) | 17 525 479 |
| Total non compressé | 58 414 898 |
| zip -9 de `embedded/` + `embedded-reserved/` | **11 753 172** (≈ 11,2 Mio) |
| zip -9 de `embedded/` seul (production, `-PquizReserved=exclude`) | **8 163 953** (≈ 7,8 Mio) |
| Ancien ajout (24 niveaux × 2000) | ≈ 2,2 Mo compressé → **ajout net à l'APK ≈ +9,5 Mo** (essai) / ≈ +6 Mo (production) |
Seuil d'arrêt (ajout > 20 Mo compressé) : **non atteint**. Budget des lots (10 Mo, `StarterBudget`) : ne compte toujours que la petite banque (test `bundledLevelsDoNotCountInTheStarterBudget` vert).

## Chargement à la demande (core)
- `EmbeddedLevels` : l'index donne les totaux sans lire aucun fichier de questions (test : seule la lecture de `embedded/index.json`). Par niveau, les fichiers sont rangés dans un ordre fixe (graine de l'index, **lots entrelacés** : une partie mélange plusieurs matières). Une partie lit des fichiers **consécutifs** dans cet ordre, au plus **2000 questions** et **3 000 000 octets** (comptés dans l'index, avant lecture), au moins un fichier.
- **Rotation** : curseur (graine + salage de départ + nombre de fichiers déjà consommés), avancé de ce qu'une partie a utilisé : toutes les questions d'un niveau sont atteintes en au plus (nombre de fichiers) parties — testé pour L2 (49 fichiers max par niveau), culture générale et CP. Magasin du curseur : interface `Rotation` ; défaut `Rotation.Memory` (perdu à la fermeture ; départ différent à chaque lancement, car salé par l'horloge). **Non persistant entre lancements** tant que l'hôte n'en donne pas un (voir « À faire »).
- Une partie d'un filtre avec filière (L1 droit…) ne lit que les lots de cette filière.
- `EmbeddedQuestionSource.bankFor` : une tranche à la fois (la précédente est relâchée) ; `newGame(filter)` (ajouté à `QuestionSource`, relayé par `CachedQuestionSource` et `PackedQuestionSource`) : la 1re partie joue la tranche déjà chargée, chaque suivante les fichiers suivants ; sans signal, une source inactive plus de 10 min passe à la tranche suivante. `QuizRoom` : paramètre optionnel **en dernier** `onNewGame`, appelé au début de `startGame`.
- L'anti-répétition par identifiant ne change pas : les ids sont ceux des lots, stables d'une partie à l'autre ; les tranches de deux parties consécutives sont disjointes (test).
- Lots installés / cache serveur (mêmes ids) : remplacent, jamais dupliqués (test conservé). 123 questions embarquées ont le même **texte** qu'une question de l'ancienne banque de base (ids différents) : conservées (le total exigé est 217 494 ; l'ancien filtre de doublons de texte a disparu).
- `TRIAL_OPEN = true` : réservables jouables ; `false` : les fichiers réservables ne sont même pas dans l'ordre de rotation ni ouverts (test avec lecteur espion). Un fichier absent (build de production) est ignoré.
- Ancien index (un `file` par niveau) : toujours lu (test).
- Production : `-PquizReserved=exclude` (défaut `include`) retire `castbridge/quiz/embedded-reserved/**` de `processResources` de `:core` ; valeur autre que include/exclude = échec de build. Vérifié : `build/resources/main/castbridge/quiz` contient `embedded-reserved` avec include et pas avec exclude (test Python lançant gradle, et vérifié à la main). Pour une version de production : `-PquizReserved=exclude` ET `QuizEdition.TRIAL_OPEN = false`.

## Mémoire sur TV 32 bits
- Une partie : ≤ 2000 questions, en pratique ≈ 0,4–0,5 Mo de JSON lu (chaque fichier de 500 questions ≈ 0,1–0,2 Mo, lu en `String` puis en arbre : pic temporaire ≈ 3 × le fichier traité, soit < 1 Mo) ; objets `Question` ≈ quelques Mo (estimation, **non mesurée**) ; plus l'ancienne banque (320). Jamais un niveau entier (L2 = 20 326 questions ≈ 7,4 Mo de JSON) : le plus grand coût est borné par 3 Mo de JSON.
- SIL et Form 4 lisent les tranches de leurs niveaux sources l'une après l'autre (≤ 2 × 2000 questions transitoires), puis ne gardent que le résultat.

## Tests réellement lancés
- Gradle fonctionne ici **sans `--offline`** (avec `--offline` le plugin Kotlin n'est pas résolu) : `tools/core-harness/run.sh :core:test …`.
- Tests écrits d'abord : `QuizAllQuestionsTest` (18 tests) — rouges avant implémentation (15 échecs sur 18 sur une ébauche de l'API), puis verts. Python : `test_quiz_embedded.py` d'abord rouge (ancien script plafonné à 2000, propriété Gradle absente), puis vert. Réserve : les rouges Python et la 1re exécution Kotlin sont des erreurs/échecs d'une ébauche, pas tous des échecs d'assertion pure.
- `python3 -m unittest tools/tests/test_quiz_embedded.py` → **VERT** (16 tests : total 217 494 et ids uniques, 93 lots présents, 28 niveaux, 30 % ± 1 par lot / niveau / difficulté / région, marquage déterministe et stable (≤ 3 % de bascules à la croissance), aucun fichier > 1,2 Mo et ≤ 500 questions, schéma de l'index, statut « review » et questions non modifiées, résultat commité à jour, exclusion Gradle).
- `:core:test --tests '*Quiz*' --tests '*Embedded*' --tests '*Starter*'` → tous les tests Quiz verts ; seuls échecs `LearnLotsTest` (hors périmètre, voir ci-dessous).
- Suite complète `:core:test` → 3128 tests, **11 échecs, tous sans lien avec le Quiz** : `LearnLotsTest` ×10 (message : « le contenu du lot maternelle a changé… relancer avec --update ») et `BaseContentTest.aFullLotOverridesTheBaseContentWithoutDuplicates` : ce sont les 11 échecs déjà listés dans `quiz-embarque.md` comme préexistants sur `integration/agents` ; non revérifiés ici sur la base (pas de re-exécution sans mes changements).
- Tests existants adaptés (minimalement) : `QuizEmbeddedLevelsTest` (24 → 28 niveaux, plus de niveau réservé, lecture par tranche au lieu d'un fichier par niveau, test de « filtre sans niveau » sur `L9`) ; `QuizLevelAvailabilityTest.silAndForm4AreDrawnFromRealQuestionsOneLevelAtATime` : chemins des fichiers lus (`embedded/cp/…`, `form-3`/`form-5`) et SIL ≥ 1500 au lieu de ≥ 2000 (une partie charge au plus 2000 questions). Rien ne lit plus `reservedNotEmbedded` côté Kotlin, sauf `QuizLevelAvailability.Index` (autre agent, inchangé : il tolère l'absence de cette clé).
- **Non lancé** : compilation de `android/receiver` et `android/sender` (le plugin Android n'est pas résoluble ici) ; aucun test sur la vraie TV.

## À faire / à confirmer
- **Coordinateur** : dans `QuizHub.open`, passer `onNewGame = { source.newGame(it) }` à `QuizRoom` (je n'ai pas le droit de toucher `QuizHub`) ; sans cela, la rotation se fait par inactivité (10 min) et au lancement.
- **Autre agent (grille des niveaux)** : `QuizLevelAvailability` lit `count` ; l'index donne maintenant aussi `freeCount` / `reservedCount` ; avec `TRIAL_OPEN=false` afficher `freeCount` (`EmbeddedLevels.openCount(level)` fait ce choix). `QuizRoom.playableCount` compte la tranche chargée (≤ 2000), pas le total : le total d'un niveau vient de l'index.
- Persistance de la rotation entre lancements : fournir une implémentation de `Rotation` (fichier d'un entier par niveau) dans l'hôte.
- Version de production : `-PquizReserved=exclude` + `TRIAL_OPEN=false`.
- **Seule la vraie TV confirme** : temps de chargement d'une partie (4 à 5 fichiers), mémoire après plusieurs changements de niveau, fluidité, taille réelle de l'APK, mélange de matières ressenti.

## Conséquence pour le serveur
Les **68 lots publiés** (famille libre de `content/quiz/families.json` : 153 877 questions) sont aujourd'hui **100 % en accès libre**, y compris **46 167 questions (30,0 %)** que la règle rendrait réservables. Les 25 lots de la famille réservée (63 617 questions) ne sont pas publiés ; la règle en rendrait 70 % (≈ 44 530) libres. Plan de republication (**rien n'a été publié ni modifié sur le serveur**) :
1. **Geler la liste des ids réservables** : exécuter la règle une fois sur chaque lot, écrire `content/quiz/reserved-ids.json` (ids, version de graine) et le versionner ; ensuite la règle ne sert plus qu'aux NOUVEAUX ids (les nouveaux sont marqués par le même hachage, sans toucher aux anciens) : cela donne la stabilité stricte.
2. **Lots libres, version suivante (v+1)** : republier chacun des 68 lots avec 70 % de ses questions (les ids libres) ; les 25 lots de la famille réservée donnent aussi un lot libre (70 %), à publier comme nouveaux lots. Les ids réservables du lot v actuel sont à **retirer** de la liste servie (`retired-ids.json` existe : y ajouter ces ids, ou un champ `reservedIds` dédié) ; les TV déjà installées gardent leur v actuel jusqu'à la mise à jour, qui retire les questions.
3. **Paquets réservables, NON publiés** : un paquet par lot (30 % : 65 252 questions au total, toutes familles), construit comme les lots (`quiz-<lot>-reserved-pN-vN.quiz.zip`), signé, placé hors catalogue public, livré uniquement aux TV titulaires d'un droit (location/essai) ; réutiliser le mécanisme de location des lots (`LOTS.md`) plutôt que d'en créer un.
4. **Ordre** : (a) geler les ids ; (b) construire en local les 68 lots v+1 et les paquets réservables (empreintes) ; (c) tests de non-chevauchement (aucun id dans les deux familles ; union = ensemble d'origine) ; (d) publier les lots libres v+1 ; (e) seulement ensuite retirer les ids réservables de l'ancienne version. Tant que (d) n'est pas fait, les questions réservables restent accessibles à tous : c'est le risque à accepter ou à fermer d'abord (retrait immédiat), décision du propriétaire.
5. **Embarqué** : la version de production de CastBridge-TV exclut `embedded-reserved/` ; les paquets réservables restent la seule voie d'accès hors essai.
6. Le texte de l'APK d'essai contient les 65 252 questions réservables en clair (dossier `embedded-reserved/`) : tant que l'essai dure, elles sont extractibles de l'APK ; ne pas distribuer l'APK d'essai au-delà du cercle d'essai.
