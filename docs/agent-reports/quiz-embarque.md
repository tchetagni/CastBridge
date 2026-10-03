# Quiz embarqué par niveau (CastBridge-TV) — rapport

Décision du propriétaire (2026-10-03) : au moins 2000 questions par niveau atomique dans le Quiz embarqué.
Branche `claude/cloud-quiz-embedded` (base `integration/agents`), aucune Pull Request.

## Résultat
- **24 niveaux libres × 2000 questions = 48 000 questions** embarquées, un fichier par niveau :
  `android/core/src/main/resources/castbridge/quiz/embedded/<niveau>.json` + `index.json` (liste des niveaux et nombres).
- Niveaux (catalogue `catalog-lots.json`, champ `level`) : CP, CE1, CE2, CM1, CM2 · 6e, 5e, 4e, 3e · 2nde, 1re · Form 1, Form 2, Form 3, Form 5 · Lower Sixth, Upper Sixth · Class 1 à Class 6 · Culture générale (CM + AF + Monde regroupés). Clés : `cp ce1 ce2 cm1 cm2 6e 5e 4e 3e 2nde 1re form-1 form-2 form-3 form-5 lower-sixth upper-sixth class-1 … class-6 culture-generale`.
- **Form 4 n'existe pas dans le catalogue** (ni lot, ni question) : rien à embarquer ; à signaler si le niveau doit exister.
- L'ancienne banque (`questions.json` 200 + `questions-school.json` 120 = 320 questions) est inchangée et reste chargée telle quelle.

## Niveaux RÉSERVÉS non couverts (à décider par le propriétaire)
**Tle** (Terminale, 12 lots), **L1** (6 lots), **L2** (4 lots), **L3** (3 lots) : contenus loués, non embarqués (règle produit). Seuls les 320 existants restent. Ils sont listés dans `index.json` (`reservedNotEmbedded`). Le script refuse (sortie en erreur) tout lot absent de `content/quiz/families.json` ou à la fois libre et réservé.

## Qualité
Toutes les questions sont au statut « review » (0 approuvée) : statut conservé, **rien n'est marqué approuvé, aucune validation humaine n'a eu lieu**. Texte, choix, réponse, explication, difficulté, catégorie, région et source ne sont pas modifiés (test Python). Seuls les champs inutiles à l'exécution (piste/niveau répétés par ligne, `lang`, `lot`, `skill`…) sont retirés du format compact ; la source est conservée via une table par fichier. Comme pour les lots, une réponse « calculée » (`verif = computed`) reste jouable avant relecture selon `PlayPolicy` (inchangé).

## Sélection (`tools/quiz-bank/build_embedded.py`)
Déterministe (graine `castbridge-embedded-quiz-v1`, tris explicites ; deux exécutions identiques octet pour octet), stratifiée par lot (matière / région pour la culture générale : CM, AF, Monde en proportion des lots) puis par difficulté, répartition au plus fort reste. Identifiants = ceux des lots (aucun doublon, doublons de texte écartés, y compris contre l'ancienne banque). Empreinte sha256 de chaque lot vérifiée. Régénérer : `python3 tools/quiz-bank/build_embedded.py` (`--check` compare au commité). Test : `python3 -m unittest tools/tests/test_quiz_embedded.py`.

## Tailles
| | octets |
|---|---|
| Ajout non compressé (24 fichiers + index) | 11 003 504 (plus gros fichier : 2nde, 607 071) |
| Ajout compressé (zip -9 du dossier) | 2 196 554 (≈ 2,1 Mo ; seuil d'arrêt 6 Mo non atteint) |

## Budget des lots (10 Mo)
`StarterBudget.measure()` ne compte que `questions.json`, `questions-school.json`, le catalogue Apprendre embarqué et Langues : les nouveaux fichiers ne sont **pas** comptés (ressources de l'APK, pas des lots) ; budget inchangé (test `bundledLevelsDoNotCountInTheStarterBudget`). Aucune modification de `StarterBudget.kt`.

## Chargement à la demande (core)
- `EmbeddedLevels` : index léger ; `load(niveau)` lit un seul fichier.
- `QuestionSource.bankFor(filtre)` (défaut = `bank()`) : `EmbeddedQuestionSource` charge **un seul niveau à la fois** (le précédent est libéré dès qu'un autre est demandé) ; `CachedQuestionSource` et `PackedQuestionSource` le relaient (cache serveur, packs, lots installés fusionnés par identifiant : un lot du même niveau **remplace** les questions embarquées de même id, aucun doublon).
- `QuizRoom` : paramètre optionnel `bankFor` (en dernier, compatible) ; `QuizHub.open` ne change que d'une ligne (le passer). Aucun écran ni fonction nouvelle ; `android/sender` non touché.

## Tests réellement lancés (via `tools/core-harness/run.sh`, JVM, sans plugin Android)
- `:core:test --tests '*QuizEmbeddedLevels*' --tests '*QuizLotsTest*' --tests '*StarterBudget*'` → **VERT** (30 tests, dont 8 nouveaux + 1 dans QuizLotsTest : ≥ 2000 par niveau, aucun niveau réservé, pas de doublon, statut « review », chargement à la demande sans toucher aux autres niveaux, lot installé sans doublon, cache serveur sans doublon, budget inchangé).
- `python3 -m unittest tools/tests/test_quiz_embedded.py` → **VERT** (11 tests : quotas, réservés, déterminisme, résultat commité à jour, questions non modifiées, fail closed).
- Suite complète `:core:test` : 2956 tests, **11 échecs, tous préexistants** (`LearnLotsTest` ×10, `BaseContentTest.aFullLotOverridesTheBaseContentWithoutDuplicates`, `LearnLotBuilder$Failure`) : identiques sur la base `integration/agents` sans mes changements (vérifié par `git stash`). Aucun échec Quiz.
- Méthode « rouge d'abord » : non respectée à la lettre (tests écrits avec le code) ; seul rouge observé : 2 tests mal paramétrés (préfixe d'id `p2-`), corrigés.
- **Non lancé** : compilation de `android/receiver` (le plugin Android n'est pas résoluble ici) ; la ligne modifiée de `QuizHub.kt` est à compiler par le coordinateur (`assembleDebug` / fumée `tools/smoke/smoke.py --tv fake`). Aucun test sur matériel.

## Risques mémoire sur TV 32 bits
- Un niveau chargé ≈ 0,3 à 0,6 Mo de JSON, parsé en ≈ 2000 objets (estimation : quelques Mo de tas) + l'ancienne banque (320) ; jamais les 24 niveaux ensemble. Le parseur JSON maison lit le fichier en `String` puis en arbre : pic temporaire ≈ 3× la taille du fichier (< 2 Mo). À mesurer sur la TV réelle (non fait).
- Changer de niveau recharge un fichier (quelques centaines de ms probables sur TV lente, non mesuré). `available()`/`freshness()` utilisent un cache d'un niveau : pas de relecture à chaque appel.
- Les lots installés et packs restent en mémoire comme avant (inchangé) ; un lot du même niveau s'ajoute au niveau chargé.
- La taille de l'APK augmente d'environ 2,2 Mo (compressé).
